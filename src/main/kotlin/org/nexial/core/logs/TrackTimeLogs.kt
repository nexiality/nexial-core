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
package org.nexial.core.logs

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.DateUtility
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.TimeTrack
import org.nexial.core.NexialConst.TimeTrack.TIMETRACK_FORMAT
import org.nexial.core.SystemVariables.getDefault
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.FlowControl.Directive
import org.nexial.core.model.FlowControl.Directive.TimeTrackEnd
import org.nexial.core.model.FlowControl.Directive.TimeTrackStart
import org.nexial.core.model.TestStep
import org.nexial.core.utils.ConsoleUtils
import org.slf4j.LoggerFactory

class TrackTimeLogs {
    private var trackStartDate: String?
    private var trackEndDate: String
    private var label: String?
    private val context: ExecutionContext?

    // used for forcefully end tracking
    private var trackStartDate1: String? = null
    private var label1: String? = null

    fun setTrackStartDate(trackStartDate: String?) {
        this.trackStartDate = trackStartDate
    }

    fun setTrackEndDate(trackEndDate: String) {
        this.trackEndDate = trackEndDate
    }

    fun setLabel(label: String?) {
        this.label = label
    }

    fun checkStartTracking(context: ExecutionContext, testStep: TestStep) {
        if (!shouldTrack(context, testStep, TimeTrackStart)) return

        if (StringUtils.isNotEmpty(trackStartDate)) {
            ConsoleUtils.log(testStep.messageId,
                             "Ignoring TimeTrackStart() since previous time tracking has not ended yet.")
            return
        }

        trackStartDate = DateUtility.getCurrentTimestampForLogging()
        val conditions = testStep.flowControls[TimeTrackStart]!!.conditions
        label = conditions[0].subject

        // To allow TimeTrackStart with empty condition i.e no label
        if (StringUtils.isBlank(label)) {
            val scriptName = StringUtils.substringBeforeLast(
                StringUtils.substringAfterLast(context.execDef.testScript, "\\"),
                ".")
            val scenario = testStep.worksheet.name
            label = "$scriptName#$scenario"
        }

        trackStartDate1 = trackStartDate
        label1 = label
    }

    fun checkEndTracking(context: ExecutionContext?, testStep: TestStep) {
        if (!shouldTrack(context, testStep, TimeTrackEnd)) return

        if (StringUtils.isEmpty(trackStartDate)) {
            ConsoleUtils.log(testStep.messageId, "Ignoring TimeTrackEnd() since none has started yet")
            return
        }

        trackStartDate1 = EMPTY
        trackingDetails(EMPTY)
    }

    fun trackingDetails(remark: String?) {
        if (StringUtils.isEmpty(trackStartDate)) return
        if (StringUtils.isEmpty(trackEndDate)) trackEndDate = DateUtility.getCurrentTimestampForLogging()

        val startDateTime = StringUtils.split(trackStartDate, " ")
        val endDateTime = StringUtils.split(trackEndDate, " ")
        val timeDiff = DateUtility.toLogTimestamp(trackEndDate) - DateUtility.toLogTimestamp(trackStartDate)
        val elapsedTime = java.lang.Long.toString(timeDiff)
        var format = System.getProperty(TIMETRACK_FORMAT)
        if (format == null && context != null) format = context.getStringData(TIMETRACK_FORMAT,
                                                                              getDefault(TIMETRACK_FORMAT))

        val replaceList = arrayOf(startDateTime[0],
                                  startDateTime[1],
                                  endDateTime[0],
                                  endDateTime[1],
                                  elapsedTime,
                                  Thread.currentThread().name,
                                  label,
                                  remark)
        format = StringUtils.replaceEach(format, TimeTrack.TRACKING_DETAIL_TOKENS, replaceList)
        if (StringUtils.isNotEmpty(format)) LOGGER.info(format)

        // setting variable to default value
        unset()
    }

    fun trackExecutionLevels(label: String?, startTime: Long?, endTime: Long?, executionLevel: String) {
        setTrackStartDate(DateUtility.formatLogDate(startTime!!))
        setTrackEndDate(DateUtility.formatLogDate(endTime!!))
        setLabel(label)
        trackingDetails("$executionLevel ended")
    }

    fun forcefullyEndTracking() {
        if (StringUtils.isEmpty(trackStartDate1)) return
        setTrackStartDate(trackStartDate1)
        setLabel(label1)
        trackingDetails("Execution ended")
    }

    private fun unset() {
        setTrackStartDate(EMPTY)
        setTrackEndDate(EMPTY)
        setLabel(EMPTY)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(TrackTimeLogs::class.java)
        private const val EMPTY = ""
        private fun shouldTrack(context: ExecutionContext?, testStep: TestStep?, directive: Directive): Boolean {
            if (testStep == null || context == null) return false
            val flowControls = testStep.flowControls
            if (MapUtils.isEmpty(flowControls)) return false
            val flowControl = flowControls[directive] ?: return false
            val conditions = flowControl.conditions
            return !CollectionUtils.isEmpty(conditions) &&
                   conditions.isMatched(context, "Evaluating Time Tracking Directive $directive")
        }
    }

    init {
        trackStartDate = EMPTY
        trackEndDate = EMPTY
        label = EMPTY
        context = ExecutionThread.get()
    }
}