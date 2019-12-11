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

package org.nexial.core.interactive

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.*
import org.apache.cxf.helpers.FileUtils
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.Data.WIN32_CMD
import org.nexial.core.NexialConst.OPT_LAST_OUTPUT_LINK
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.ConsoleUtils
import java.io.File

class ExecutionRecorder(private val baseCommand: BaseCommand) {
    private var recordingInSession: Boolean = false

    fun toggleRecording() = if (recordingInSession) stopRecording() else startRecording()

    private fun startRecording() {
        val outcome = baseCommand.startRecording()
        if (outcome.isSuccess) {
            ConsoleUtils.log(outcome.message)
            recordingInSession = true
        } else
            ConsoleUtils.error("FAILED!!! ${outcome.message}")
    }

    private fun stopRecording() {
        val outcome = baseCommand.stopRecording()
        recordingInSession = false

        if (outcome.isSuccess) {
            val context = baseCommand.context
            val videoLink = context.getStringData(OPT_LAST_OUTPUT_LINK)
            if (videoLink != null) {
                val selection = ConsoleUtils.pauseForInput(null,
                                                           "Previous desktop recording available at $videoLink\n" +
                                                           "Would you like to (P)lay it, (S)how it, (D)elete it? ")
                if (StringUtils.isBlank(selection)) return

                when (selection.toUpperCase()) {
                    "P" -> {
                        // play it
                        play(videoLink)
                    }

                    "S" -> {
                        // show it
                        show(videoLink)
                    }

                    "D" -> {
                        // delete it (local only)
                        delete(videoLink)
                    }
                }
            }
        } else {
            ConsoleUtils.error("FAILED!!! ${outcome.message}")
        }
    }

    private fun delete(videoLink: String) {
        if (ResourceUtils.isWebResource(videoLink)) {
            ConsoleUtils.error("The video file is not currently located in local drive; delete CANCELLED")
        } else {
            FileUtils.delete(File(videoLink))
        }
    }

    private fun show(videoLink: String) {
        val path = if (videoLink.contains("\\"))
            videoLink.substringBeforeLast("\\")
        else
            videoLink.substringBeforeLast("/")

        when {
            IS_OS_MAC_OSX -> {
                ProcessInvoker.invokeNoWait("open", listOf(path), null)
            }

            IS_OS_WINDOWS -> {
                ProcessInvoker.invokeNoWait(WIN32_CMD, listOf("/C", "start", "\"\"", path), null)
            }

            IS_OS_LINUX   -> {
                ProcessInvoker.invokeNoWait("/bin/sh -c", listOf(path), null)
            }

            else          -> {
                ConsoleUtils.error("Unknown O/S; Nexial doesn't know how to open path $path")
            }
        }
    }

    private fun play(videoLink: String) {
        when {
            IS_OS_MAC_OSX -> {
                ProcessInvoker.invokeNoWait("open", listOf(videoLink), null)
            }

            IS_OS_WINDOWS -> {
                ProcessInvoker.invokeNoWait(WIN32_CMD, listOf("/C", "start", "\"\"", videoLink), null)
            }

            IS_OS_LINUX   -> {
                ProcessInvoker.invokeNoWait("/bin/sh -c", listOf(videoLink), null)
            }

            else          -> {
                ConsoleUtils.error("Unknown O/S; Nexial doesn't know how to open file $videoLink")
            }
        }
    }
}