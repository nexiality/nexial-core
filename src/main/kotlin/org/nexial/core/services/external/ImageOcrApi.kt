/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.services.external

import com.google.gson.JsonObject
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import org.nexial.commons.ServiceException
import org.nexial.commons.utils.DateUtility
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.Data.APIKEYS_OCRSPACE
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.Project.PROJECT_CACHE_LOCATION
import org.nexial.core.plugins.io.IoCommand
import org.nexial.core.plugins.pdf.PdfCommand
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JSONPath
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.URLEncoder
import java.util.*
import kotlin.random.Random

class ImageOcrApi(
    lang: String = "eng",
    detectOrientation: Boolean = false,
    isTable: Boolean = true,
    autoscale: Boolean = false,
    detectCheckbox: Boolean = true,
    val retry: Int = 3,
) {

    private val apiKeys = APIKEYS_OCRSPACE.toMutableList()
    private val delayBetweenRetries = 3000L

    // https://ocr.space/OCRAPI
    private val url = "https://api.ocr.space/parse/image"
    private val imagePayloadPattern = "language=${lang}&" +
                                      "isOverlayRequired=true&" +
                                      "FileType=.Auto&" +
                                      "detectOrientation=${detectOrientation}&" +
                                      "isTable=${isTable}&" +
                                      "scale=${autoscale}&" +
                                      "OCREngine=1&" +
                                      "detectCheckbox=${detectCheckbox}&" +
                                      "checkboxTemplate=0&" +
                                      "base64Image=data:%s;base64,%s"
    private val pdfPayloadPattern = "language=${lang}&" +
                                    "isOverlayRequired=true&" +
                                    "FileType=.Auto&" +
                                    "detectOrientation=false&" +
                                    "isTable=true&" +
                                    "scale=true&" +
                                    "OCREngine=1&" +
                                    "detectCheckbox=true&" +
                                    "checkboxTemplate=0&" +
                                    "IsCreateSearchablePDF=true&" +
                                    "isSearchablePdfHideTextLayer=true&" +
                                    "base64Image=data:%s;base64,%s"
    private val headers: Map<String, Any> = mutableMapOf("Content-Type" to "application/x-www-form-urlencoded")
    private val ocrCache = PROJECT_CACHE_LOCATION + "ocr.cache"

    @Throws(ServiceException::class)
    fun ocr(srcFile: File): String {
        val errorPrefix = "Unable to parse '${srcFile.name}': "

        // step 1: retrieve image content
        if (!FileUtil.isFileReadable(srcFile)) throw ServiceException(errorPrefix + "file is not readable or is empty")
        val content = FileUtils.readFileToByteArray(srcFile)

        // step 1: check for cached ocr (if any)
        val imageChecksum = IoCommand.checksum(content)
        val cachedOcrText = deriveCachedOcrText(imageChecksum)
        if (cachedOcrText != null) return cachedOcrText

        // step 2: convert image to base64
        val base64 = URLEncoder.encode(Base64.getEncoder().encodeToString(content), DEF_CHARSET)
        val mimeType = toMimeType(srcFile)
        val payloadPattern = if (StringUtils.equals(mimeType, "application/pdf")) {
            val pageCount = PdfCommand.derivePageCount(srcFile.absolutePath)
            if (pageCount < 1) throw ServiceException("No pages found in '%s'")
            if (pageCount > 3) throw ServiceException("At this time, only PDF with 3 or less pages is supported.")
            pdfPayloadPattern
        } else
            imagePayloadPattern
        val payload = String.format(payloadPattern, mimeType, base64)

        val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
        var waitMs = delayBetweenRetries

        // support reties, in case ext api is acting up
        for (i in 1..retry) {
            // step 3: randomize api key
            // step 4: invoke ocr api
            val response = wsClient.post(url,
                                         payload,
                                         headers.plus("apikey" to apiKeys.random(Random(System.currentTimeMillis()))))

            // step 5: check response
            if (response.returnCode == 200) {
                // step 6: fetch response text
                val json = JSONObject(response.body)

                // 1 is good - SUCCESS!
                // parsed successfully, let's fetch text
                if (JSONPath.find(json, "OCRExitCode") == "1")
                    return cacheOcrText(imageChecksum, JSONPath.find(json, "ParsedResults.ParsedText"))

                val error = collectResponseError(errorPrefix, json)
                if (i == retry)
                    throw ServiceException(errorPrefix + error)
                else
                    ConsoleUtils.log("OCR API responded with error: ${error}, wait and retrying...")
            } else {
                // HTTP 5xx... give up
                // last retry... give up
                if (response.returnCode >= 500 || i == retry)
                    throw ServiceException(errorPrefix + response.statusText +
                                           if (StringUtils.isNotBlank(response.body)) "; ${response.body}" else "")
                else
                    ConsoleUtils.log(when (response.returnCode) {
                                         // handle HTTP 403 - Forbidden
                                         // e.g. Forbidden; "For this API KEY only 3 concurrent connections at the same time allowed. Contact support if you need more."
                                         403  -> "Concurrent OCR use detected..."

                                         // handle HTTP 408 - Timeout response status code
                                         // e.g. Timed out waiting for results
                                         408  -> "OCR API timed out..."
                                         else -> "OCR API return unknown error - ${response.returnCode}"
                                     } + " wait and retry...")
            }

            // wait and retry
            Thread.sleep(waitMs)
            waitMs += Random.nextLong(1000L, delayBetweenRetries)
        }

        // give up
        throw ServiceException(errorPrefix + "Unknown error even after retries")
    }

    private fun deriveCachedOcrText(checksum: String): String? {
        return if (!FileUtil.isFileReadable(ocrCache, 5))
            null
        else {
            val json = GSON.fromJson(FileReader(ocrCache), JsonObject::class.java)
            if (json == null || json.isJsonNull || json.size() < 1)
                null
            else {
                val cache = json.getAsJsonObject(checksum)
                if (cache == null || cache.isJsonNull || cache.size() < 1)
                    null
                else {
                    val jsonText = cache.get("text")
                    if (jsonText == null || jsonText.isJsonNull || !jsonText.isJsonPrimitive)
                        null
                    else
                        jsonText.asString
                }
            }
        }
    }

    private fun cacheOcrText(checksum: String, ocrText: String): String {
        val cache = JsonObject()
        cache.addProperty("text", ocrText)
        cache.addProperty("since", DateUtility.formatLogDate(System.currentTimeMillis()))

        val root = if (!FileUtil.isFileReadable(ocrCache, 5)) {
            if (!File(ocrCache).parentFile.mkdirs()) ConsoleUtils.error("Unable to create directory for $ocrCache")
            JsonObject()
        } else
            GSON.fromJson(FileReader(ocrCache), JsonObject::class.java)
        root.add(checksum, cache)

        GSON.newJsonWriter(FileWriter(ocrCache)).use { GSON.toJson(root, it) }

        return ocrText
    }

    private fun collectResponseError(errorPrefix: String, json: JSONObject): String {
        var errorMessage = JSONPath.find(json, "ErrorMessage")
        if (TextUtils.isBetween(errorMessage, "[", "]")) {
            errorMessage = JSONPath.find(json, "ErrorMessage", JSONArray::class.java).joinToString(separator = ". ")
        }

        val errorDetails = JSONPath.find(json, "ErrorDetails")

        return errorPrefix +
               (if (StringUtils.isNotBlank(errorMessage)) errorMessage else "") +
               (if (StringUtils.isNotBlank(errorDetails)) ". $errorDetails" else "")
    }

    private fun toMimeType(imageFile: File) = when (imageFile.extension.lowercase()) {
        "pdf"         -> "application/pdf"
        "tif", "tiff" -> "image/tiff"
        "png"         -> "image/png"
        "jpeg", "jpg" -> "image/jpeg"
        "gif"         -> "image/gif"
        "bmp"         -> "image/bmp"
        else          -> "image/ief"    /* probably won't work */
    }
}