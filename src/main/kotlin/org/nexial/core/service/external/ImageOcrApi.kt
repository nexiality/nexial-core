/*
 * Copyright 2012-2018 the original author or authors.
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

package org.nexial.core.service.external

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import org.nexial.commons.ServiceException
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.Data.APIKEYS_OCRSPACE
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.JSONPath
import java.io.File
import java.net.URLEncoder
import java.util.*
import kotlin.random.Random

class ImageOcrApi(lang: String = "eng",
                  detectOrientation: Boolean = false,
                  isTable: Boolean = true,
                  autoscale: Boolean = false,
                  detectCheckbox: Boolean = true) {

    private val apiKeys = APIKEYS_OCRSPACE.toMutableList()
    private val url = "https://api.ocr.space/parse/image"
    private val payloadPattern = "language=${lang}&" +
                                 "isOverlayRequired=true&" +
                                 "FileType=.Auto&" +
                                 "detectOrientation=${detectOrientation}&" +
                                 "isTable=${isTable}&" +
                                 "scale=${autoscale}&" +
                                 "OCREngine=1&" +
                                 "detectCheckbox=${detectCheckbox}&" +
                                 "checkboxTemplate=0&" +
                                 "base64Image=data:%s;base64,%s"
    private val headers: Map<String, Any> = mutableMapOf("Content-Type" to "application/x-www-form-urlencoded")

    @Throws(ServiceException::class)
    fun ocr(srcFile: File): String {
        // step 1: retrieve image content
        val content = FileUtils.readFileToByteArray(srcFile)

        // step 2: convert image to base64
        val base64 = URLEncoder.encode(Base64.getEncoder().encodeToString(content), DEF_CHARSET)

        // step 3: randomize api key
        val apiKey = apiKeys.random(Random(System.currentTimeMillis()))

        // step 4: invoke ocr api
        val response = WebServiceClient(null).configureAsQuiet().disableContextConfiguration().post(
            url,
            String.format(payloadPattern, toMimeType(srcFile), base64),
            headers.plus("apikey" to apiKey))

        // step 5: check response
        val errorPrefix = "Unable to parse '$srcFile': "
        if (response.returnCode != 200) {
            throw ServiceException(errorPrefix + response.statusText +
                                   if (StringUtils.isNotBlank(response.body)) "; ${response.body}" else "")
        }

        // step 6: fetch response text
        val json = JSONObject(response.body)
        val exitCode = JSONPath.find(json, "OCRExitCode")
        if (!StringUtils.equals(exitCode, "1")) {
            var error = errorPrefix
            var errorMessage = JSONPath.find(json, "ErrorMessage")
            if (TextUtils.isBetween(errorMessage, "[", "]")) {
                errorMessage = JSONPath.find(json, "ErrorMessage", JSONArray::class.java).toList()
                    .joinToString(separator = ". ")
            }
            if (StringUtils.isNotBlank(errorMessage)) error += errorMessage

            val errorDetails = JSONPath.find(json, "ErrorDetails")
            if (StringUtils.isNotBlank(errorDetails)) error += ". $errorDetails"

            throw ServiceException(error)
        }

        // parsed successfully, let's fetch text
        return JSONPath.find(json, "ParsedResults.ParsedText")
    }

    private fun toMimeType(imageFile: File) = when (imageFile.extension.toLowerCase()) {
        "tif", "tiff" -> "image/tiff"
        "png"         -> "image/png"
        "jpeg", "jpg" -> "image/jpeg"
        "gif"         -> "image/gif"
        "bmp"         -> "image/bmp"
        else          -> "image/ief"    /* probably won't work */
    }
}