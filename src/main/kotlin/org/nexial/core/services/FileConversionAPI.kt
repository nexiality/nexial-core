package org.nexial.core.services

import com.google.gson.JsonObject
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.CollectionUtil
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.Data.APIKEYS_ZZ
import org.nexial.core.NexialConst.Data.HOSTS_ZZ
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.Ws
import org.nexial.core.NexialConst.Ws.WS_BASIC_USER
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator

class FileConversionAPI {
    private val jobUrl = "https://{host}/v1/jobs"
    private val fileUrl = "https://{host}/v1/files/{fileId}/content"
    private val statusInit = "initialising"
    private val statusSuccess = "successful"
    private val callsign = "[CONVERSION]"

    fun xls2xlsx(source: File, target: String): File? {
        if (!FileUtil.isFileReadable(source, 1024))
            throw IllegalArgumentException("File '$source' not found or invalid")

        if (StringUtils.isBlank(target)) throw IllegalArgumentException("Invalid target path: '$target'")

        val xlsx = resolveTargetFile(source, target, ".xlsx")

        val apiKey = CollectionUtil.randomSelectOne(APIKEYS_ZZ)
        val host = CollectionUtil.randomSelectOne(HOSTS_ZZ)
        val wsClient = WebServiceClient(null)
                .configureAsQuiet()
                .disableContextConfiguration()
                .setPriorityConfiguration(WS_BASIC_USER, apiKey)
                .setPriorityConfiguration(Ws.WS_BASIC_PWD, "")

        // step 1: issue conversion request
        val startJobResp = wsClient.postMultipart(StringUtils.replace(jobUrl, "{host}", host),
                                                  "source_file=${source.absolutePath}\ntarget_format=xlsx",
                                                  "source_file")
        return if (startJobResp.returnCode in 200..299) {
            val json = GSON.fromJson(startJobResp.body, JsonObject::class.java)
            if (json.get("status").asString == statusInit) {
                // step 2: check/wait for conversion job to finish
                val fileId = pollJobCompletion(wsClient, host, json.get("id").asString, 30000)
                if (fileId != null) {
                    val url = StringUtils.replace(StringUtils.replace(fileUrl, "{fileId}", fileId), "{host}", host)
                    // step 3: download converted file
                    val fileResp = wsClient.download(url, null, xlsx.absolutePath)
                    if (fileResp.returnCode in 200..299) {
                        ConsoleUtils.log("$callsign conversion complete; file saved to $xlsx")
                        // step 4: delete converted files (fire and forget)
                        wsClient.delete(url, null)
                        xlsx
                    } else {
                        ConsoleUtils.error("$callsign Unable to complete conversion")
                        null
                    }
                } else {
                    ConsoleUtils.error("$callsign Unable to initialize file conversion; no file id found")
                    null
                }
            } else {
                ConsoleUtils.error("$callsign Unable to initialize file conversion")
                null
            }
        } else {
            ConsoleUtils.error("$callsign Error initialize file conversion: ${startJobResp.statusText}")
            null
        }
    }

    private fun pollJobCompletion(wsClient: WebServiceClient, host: String, callbackId: String, maxWaitMs: Int):
            String? {
        val url = StringUtils.replace("$jobUrl/$callbackId", "{host}", host)
        val waitUntil = System.currentTimeMillis() + maxWaitMs

        while (System.currentTimeMillis() < waitUntil) {
            val jobStatusResp = wsClient.get(url, null)
            if (jobStatusResp.returnCode in 200..299) {
                val json = GSON.fromJson(jobStatusResp.body, JsonObject::class.java)
                if (json.get("status").asString == statusSuccess) {
                    val targets = json.get("target_files").asJsonArray
                    if (targets != null && targets.size() > 0) return targets[0].asJsonObject.get("id").asString

                    ConsoleUtils.log("$callsign Unable to obtain valid conversion target; retrying...")
                } else {
                    ConsoleUtils.log("$callsign In progress; retrying...")
                    try {
                        Thread.sleep(1000)
                    } catch (e: Exception) {
                    }
                }
            } else
                ConsoleUtils.log("$callsign Error when requesting conversion: ${jobStatusResp.statusText}; retrying...")
        }

        ConsoleUtils.log("$callsign Unable to obtain complete conversion; retrying...")
        return null
    }

    private fun resolveTargetFile(source: File, target: String, extension: String): File {
        val xlsx = File(target)
        return if (!xlsx.exists()) {
            // assuming we can create such a file...
            xlsx.parentFile.mkdirs()
            ConsoleUtils.log("$callsign creating target file as $target")
            xlsx
        } else if (xlsx.isFile) {
            ConsoleUtils.log("$callsign target file '$target' will be overridden")
            xlsx
        } else {
            val newTarget = StringUtils.appendIfMissing(xlsx.absolutePath, separator) +
                            StringUtils.substringBeforeLast(source.name, ".") + extension
            ConsoleUtils.log("$callsign target is a directory; deriving file path via source file as $newTarget...")
            File(newTarget)
        }
    }
}