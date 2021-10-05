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

package org.nexial.core.interactive

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.*
import org.apache.cxf.helpers.FileUtils
import org.aspectj.weaver.tools.cache.SimpleCacheFactory.path
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.Data.WIN32_CMD
import org.nexial.core.NexialConst.OPT_LAST_OUTPUT_LINK
import org.nexial.core.NexialConst.RB
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.ConsoleUtils
import java.io.File

class ExecutionRecorder(private val baseCommand: BaseCommand) {
    private var recordingInSession: Boolean = false

    fun toggleRecording() = if (recordingInSession) stopRecording() else startRecording()

    private fun startRecording() {
        // just in case
        val context = baseCommand.context
        if (ExecutionThread.get() == null && context != null) ExecutionThread.set(context)

        val outcome = baseCommand.startRecording()
        if (outcome.isSuccess) {
            ConsoleUtils.log(outcome.message)
            recordingInSession = true
        } else
            ConsoleUtils.error("FAILED!!! ${outcome.message}")
    }

    private fun stopRecording() {
        // just in case
        val context = baseCommand.context
        if (ExecutionThread.get() == null && context != null) ExecutionThread.set(context)

        val outcome = baseCommand.stopRecording()
        recordingInSession = false

        if (outcome.isSuccess) {
            val link = context.getStringData(OPT_LAST_OUTPUT_LINK)
            if (link != null) {
                val selection = ConsoleUtils.pauseForInput(null, RB.Recorder.text("stop.prompt", link), "INPUT REQUEST")
                if (StringUtils.isBlank(selection)) return

                when (selection.uppercase()) {
                    // play it
                    "P" -> play(link)

                    // show it
                    "S" -> show(link)

                    // delete it (local only)
                    "D" -> delete(link)
                }
            }
        } else {
            ConsoleUtils.error("FAILED!!! ${outcome.message}")
        }
    }

    private fun delete(videoLink: String) {
        if (ResourceUtils.isWebResource(videoLink)) {
            ConsoleUtils.error(RB.Recorder.text("delete.notLocal"))
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
                ConsoleUtils.error(RB.Recorder.text("open.notSupported", path))
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
                ConsoleUtils.error(RB.Recorder.text("open.notSupported", path))
            }
        }
    }
}