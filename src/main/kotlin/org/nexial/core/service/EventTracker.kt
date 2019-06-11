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

import org.apache.commons.codec.binary.Hex
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.Data.*
import org.nexial.core.NexialConst.OPT_RUN_ID
import org.nexial.core.model.*
import org.nexial.core.model.ExecutionEvent.*
import org.nexial.core.service.EventUtils.postfix
import org.nexial.core.service.EventUtils.storageLocation
import org.nexial.core.utils.TrackTimeLogs
import java.io.File
import java.io.File.separator
import java.text.SimpleDateFormat
import java.util.*

object EventTracker {
    private val eventFileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS")
    private val enableUniversalTracking =
        BooleanUtils.toBoolean(System.getProperty("nexial.universalTracking", "false"))

    fun getStorageLocation() = storageLocation

    fun getExtension() = postfix

    @JvmStatic
    fun track(event: NexialEvent) {
        write(event.eventName, event.json())
        trackEvents(event)
    }

    @JvmStatic
    fun track(env: NexialEnv) = write("env", env.json())

    private fun write(type: String, content: String) {
        if (enableUniversalTracking) {
            val file = File(storageLocation +
                            RandomStringUtils.randomAlphabetic(10) + "." +
                            eventFileDateFormat.format(Date()) + "." +
                            type + postfix)
            FileUtils.forceMkdirParent(file)
            FileUtils.write(file, content, DEF_FILE_ENCODING)
        }
    }

    private fun trackEvents(event: NexialEvent) {
        val context = ExecutionThread.get()
        val eventName = event.eventName
        val startTime = event.startTime
        val endTime = event.endTime
        val trackTimeLogs = context?.trackTimeLogs ?: TrackTimeLogs()

        when {
            eventName == ExecutionComplete.eventName && trackEvent(TRACK_EXECUTION)        -> {
                trackTimeLogs.trackExecutionLevels(System.getProperty(OPT_RUN_ID), startTime, endTime, "Execution")
            }

            eventName == ScriptComplete.eventName && trackEvent(context, TRACK_SCRIPT)     -> {
                val label: String = asLabel((event as NexialScriptCompleteEvent).script)
                trackTimeLogs.trackExecutionLevels(label, startTime, endTime, "Script")
            }

            eventName == IterationComplete.eventName                                       -> {
                trackTimeLogs.forcefullyEndTracking()

                if (trackEvent(context, TRACK_ITERATION)) {
                    val iterEvent = event as NexialIterationCompleteEvent
                    val iteration = "" + iterEvent.iteration
                    val label: String = asLabel(iterEvent.script) + "-" +
                                        StringUtils.rightPad("0", 3 - iteration.length) + iteration
                    trackTimeLogs.trackExecutionLevels(label, startTime, endTime, "Iteration")
                }
            }

            eventName == ScenarioComplete.eventName && trackEvent(context, TRACK_SCENARIO) -> {
                trackTimeLogs.trackExecutionLevels((event as NexialScenarioCompleteEvent).scenario,
                                                   startTime, endTime, "Scenario")
            }
        }
    }

    private fun asLabel(script: String) =
        StringUtils.substringBeforeLast(StringUtils.substringAfterLast(StringUtils.replace(script, "\\", "/"), "/"),
                                        ".")

    private fun trackEvent(context: ExecutionContext, trackId: String) =
        BooleanUtils.toBoolean(context.getStringData(trackId))

    private fun trackEvent(trackId: String) = BooleanUtils.toBoolean(System.getProperty(trackId))
}

object EventUtils {
    internal val storageLocation = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().absolutePath, separator) +
                                   Hex.encodeHexString("Nexial_Event".toByteArray()) + separator
    internal const val postfix = ".json"

    init {
    }
}