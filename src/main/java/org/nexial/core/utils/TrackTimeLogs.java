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
 *
 */

package org.nexial.core.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.FlowControl;
import org.nexial.core.model.FlowControl.Directive;
import org.nexial.core.model.NexialFilterList;
import org.nexial.core.model.TestStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.model.FlowControl.Directive.TimeTrackEnd;
import static org.nexial.core.model.FlowControl.Directive.TimeTrackStart;

public final class TrackTimeLogs {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackTimeLogs.class);
    private static final String FORMAT_LOG = "yyyy-MM-dd HH:mm:ss,SSS";
    private static final DateFormat DATE_FORMAT_LOG = new SimpleDateFormat(FORMAT_LOG);

    private static final String EMPTY = "";

    private String trackStartDate;
    private String trackEndDate;
    private String label;

    // used for forcefully end tracking
    private String trackStartDate1;
    private String label1;

    public TrackTimeLogs() {
        this.trackStartDate = EMPTY;
        this.trackEndDate = EMPTY;
        this.label = EMPTY;
    }

    public void setTrackStartDate(String trackStartDate) { this.trackStartDate = trackStartDate; }

    public void setTrackEndDate(String trackEndDate) { this.trackEndDate = trackEndDate; }

    public void setLabel(String label) { this.label = label; }

    public void checkStartTracking(ExecutionContext context, TestStep testStep) {
        if (!shouldTrack(context, testStep, TimeTrackStart)) { return; }

        if (StringUtils.isNotEmpty(trackStartDate)) {
            ConsoleUtils.log(testStep.getMessageId(),
                             "Ignoring TimeTrackStart() here since previous time tracking has not ended yet.");
            return;
        }

        trackStartDate = DateUtility.getCurrentTimestampForLogging();
        NexialFilterList conditions = testStep.getFlowControls().get(TimeTrackStart).getConditions();
        label = conditions.get(0).getSubject();

        // To allow TimeTrackStart with empty condition i.e no label
        if (StringUtils.isBlank(label)) {
            String scriptName = StringUtils.substringBeforeLast(
                StringUtils.substringAfterLast(context.getExecDef().getTestScript(), "\\"), ".");
            String scenario = testStep.getWorksheet().getName();
            label = scriptName + "#" + scenario;
        }

        trackStartDate1 = trackStartDate;
        label1 = label;
    }

    public void checkEndTracking(ExecutionContext context, TestStep testStep) {
        if (!shouldTrack(context, testStep, TimeTrackEnd)) { return; }

        if (StringUtils.isEmpty(trackStartDate)) {
            ConsoleUtils.log(testStep.getMessageId(),
                             "Ignoring TimeTrackEnd() here since no time tracking has started yet");
            return;
        }

        trackStartDate1 = EMPTY;
        trackingDetails(EMPTY);
    }

    public void trackingDetails(String remark) {
        if (StringUtils.isEmpty(trackStartDate)) { return; }
        if (StringUtils.isEmpty(trackEndDate)) { trackEndDate = DateUtility.getCurrentTimestampForLogging(); }

        String startDateTime[] = StringUtils.split(trackStartDate, " ");
        String endDateTime[] = StringUtils.split(trackEndDate, " ");
        long timeDiff = DateUtility.formatTo(trackEndDate, FORMAT_LOG) -
                        DateUtility.formatTo(trackStartDate, FORMAT_LOG);
        String elapsedTime = Long.toString(timeDiff);

        ExecutionContext context = ExecutionThread.get();
        String separator = DEF_TRACK_SEP;

        String property = System.getProperty(TRACK_SEP);
        if (property != null) { separator = property; }
        if (property == null && context != null) { separator = context.getStringData(TRACK_SEP, separator); }

        trackStartDate = getBooleanData(context, TRACK_START_DATE) ? startDateTime[0] + separator : EMPTY;
        String startTime = getBooleanData(context, TRACK_START_TIME) ? startDateTime[1] + separator : EMPTY;
        trackEndDate = getBooleanData(context, TRACK_END_DATE) ? endDateTime[0] + separator : EMPTY;
        String endTime = getBooleanData(context, TRACK_END_TIME) ? endDateTime[1] + separator : EMPTY;
        elapsedTime = getBooleanData(context, TRACK_ELAPSED_TIME) ? elapsedTime + separator : EMPTY;
        label = getBooleanData(context, TRACK_LABEL) ? label + separator : EMPTY;
        remark = getBooleanData(context, TRACK_REMARK) && StringUtils.isNotEmpty(remark) ? remark : EMPTY;
        String currentThread = getBooleanData(context, TRACK_THREAD_NAME) ?
                               Thread.currentThread().getName() + separator :
                               EMPTY;

        String message =
            trackStartDate + startTime + trackEndDate + endTime + elapsedTime + currentThread + label + remark;
        if (StringUtils.isNotEmpty(message)) { LOGGER.info(StringUtils.removeEnd(message, separator)); }
        // setting variable to default value
        unset();
    }

    public void trackExecutionLevels(String label, Long startTime, Long endTime, String executionLevel) {
        setTrackStartDate(formatDate(startTime));
        setTrackEndDate(formatDate(endTime));
        setLabel(label);
        trackingDetails(executionLevel + " Ended");
    }

    public void forcefullyEndTracking() {
        if (StringUtils.isEmpty(trackStartDate1)) { return; }
        setTrackStartDate(trackStartDate1);
        setLabel(label1);
        trackingDetails("Execution Ended");
    }

    private String formatDate(Long timestampMillis) { return DATE_FORMAT_LOG.format(new Date(timestampMillis)); }

    private void unset() {
        setTrackStartDate(EMPTY);
        setTrackEndDate(EMPTY);
        setLabel(EMPTY);
    }

    private static Boolean shouldTrack(ExecutionContext context, TestStep testStep, Directive directive) {
        if (testStep == null || context == null) { return false; }

        Map<Directive, FlowControl> flowControls = testStep.getFlowControls();
        if (MapUtils.isEmpty(flowControls)) { return false; }

        FlowControl flowControl = flowControls.get(directive);
        if (flowControl == null) { return false; }

        NexialFilterList conditions = flowControl.getConditions();
        return !CollectionUtils.isEmpty(conditions) &&
               conditions.isMatched(context, "Evaluating Time Tracking Directive " + directive);
    }

    private boolean getBooleanData(ExecutionContext context, String data) {
        String property = System.getProperty(data);
        if (property == null) {
            if (context != null) {
                property = context.getStringData(data, DEF_TRACK_VALUE);
            } else {
                property = DEF_TRACK_VALUE;
            }
        }
        return BooleanUtils.toBoolean(property);
    }


}
