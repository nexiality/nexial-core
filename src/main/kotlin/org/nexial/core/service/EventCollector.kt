/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nexial.core.service

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.service.EventUtils.postfix
import org.nexial.core.service.EventUtils.storageLocation
import org.nexial.core.utils.CheckUtils
import org.nexial.core.utils.ConsoleUtils
import java.io.File

class EventCollector(val url: String, val verbose: Boolean, val enabled: Boolean) : Thread() {
    private val sleepMs = 250L
    private val wsClient = WebServiceClient(null)
    private val shouldProceed = enabled && !CheckUtils.isRunningInJUnit()

    init {
        isDaemon = true
        name = "nexial-event-collector"
        priority = MIN_PRIORITY
    }

    override fun run() {
        while (true) {

            log("scanning for event files in $storageLocation...")
            val files = FileUtil.listFiles(storageLocation, ".+$postfix", false)

            if (CollectionUtils.isNotEmpty(files)) {
                val file = files[0]
                log(file, "found; ready to process...")

                try {
                    val response = wsClient.post(url, FileUtils.readFileToString(file, DEF_FILE_ENCODING))
                    if (response.returnCode in 200..299) {
                        log(file, "event successfully collected")
                        if (FileUtils.deleteQuietly(file)) log(file, "event file deleted")
                    } else {
                        log(file, "event was NOT successfully collected - ${response.statusText}")
                    }
                } catch (e: Exception) {
                    log(file, "event was NOT successfully collected - ${e.message}")
                }
            } else {
                log("no event files found")
            }

            Thread.sleep(sleepMs)
        }
    }

    private fun log(message: String) {
        if (verbose) ConsoleUtils.log(name, message)
    }

    private fun log(file: File, message: String) {
        if (verbose) ConsoleUtils.log(name, "[${file.name}] - $message")
    }

    private fun init() {
        wsClient.setVerbose(verbose)
        if (shouldProceed) start()
    }
}