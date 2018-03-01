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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.NexialFilterComparator;
import org.nexial.core.model.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.FlowControl;
import org.nexial.core.model.FlowControl.Condition;
import org.nexial.core.model.FlowControl.Directive;
import org.nexial.core.model.TestStep;
import org.nexial.core.plugins.desktop.DesktopNotification;
import org.nexial.core.reports.JenkinsVariables;

import static org.nexial.core.model.FlowControl.Condition.ANY;
import static org.nexial.core.model.FlowControl.Directive.*;
import static org.nexial.core.plugins.desktop.DesktopNotification.NotificationLevel.warn;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

public final class FlowControlUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowControlUtils.class);

    private FlowControlUtils() { }

    public static void checkPauseBefore(ExecutionContext context, TestStep testStep) {
        if (mustNotPause(context, testStep)) { return; }

        String msgPrefix = "PAUSE BEFORE EXECUTION - row " + (testStep.getRow().get(0).getRowIndex() + 1);

        if (context.isStepByStep()) {
            ConsoleUtils.pause(context, msgPrefix + ", conditions: step-by-step=true");
            return;
        }

        Map<Directive, FlowControl> flowControls = testStep.getFlowControls();
        if (MapUtils.isEmpty(flowControls)) { return; }
        FlowControl flowControl = flowControls.get(PauseBefore);
        if (!isMatched(context, flowControl)) { return; }

        if (IS_OS_WINDOWS) {
            DesktopNotification.notifyNoAutoDismiss(warn,
                                                    "TEST EXECUTION PAUSED DUE TO PauseBefore() CONDITION. "
                                                    + "PRESS ENTER ON CONSOLE TO CONTINUE EXECUTION");
        }

        ConsoleUtils.pause(context, msgPrefix + ", conditions: " + serializeDirectives(flowControl));
    }

    public static void checkPauseAfter(ExecutionContext context, TestStep testStep) {
        if (mustNotPause(context, testStep)) { return; }

        Map<Directive, FlowControl> flowControls = testStep.getFlowControls();
        if (MapUtils.isEmpty(flowControls)) { return; }
        FlowControl flowControl = flowControls.get(PauseAfter);
        if (!isMatched(context, flowControl)) { return; }

        if (IS_OS_WINDOWS) {
            DesktopNotification.notifyNoAutoDismiss(warn,
                                                    "TEST EXECUTION PAUSED DUE TO PauseAfter() CONDITION. "
                                                    + "PRESS ENTER ON CONSOLE TO CONTINUE EXECUTION");
        }

        ConsoleUtils.pause(context, "PAUSE AFTER EXECUTION - row " + (testStep.getRow().get(0).getRowIndex() + 1) +
                                    ", conditions: " + serializeDirectives(flowControl));
    }

    public static StepResult checkSkipIf(ExecutionContext context, TestStep testStep) {
        return checkFlowControl(context, testStep, SkipIf);
    }

    public static StepResult checkProceedIf(ExecutionContext context, TestStep testStep) {
        return checkFlowControl(context, testStep, ProceedIf);
    }

    public static StepResult checkFailIf(ExecutionContext context, TestStep testStep) {
        return checkFlowControl(context, testStep, FailIf);
    }

    public static StepResult checkEndIf(ExecutionContext context, TestStep testStep) {
        return checkFlowControl(context, testStep, EndIf);
    }

    public static StepResult checkEndLoopIf(ExecutionContext context, TestStep testStep) {
        return checkFlowControl(context, testStep, EndLoopIf);
    }

    protected static StepResult checkFlowControl(ExecutionContext context, TestStep testStep, Directive directive) {
        if (testStep == null || context == null) { return null; }

        Map<Directive, FlowControl> flowControls = testStep.getFlowControls();
        if (MapUtils.isEmpty(flowControls)) { return null; }

        FlowControl flowControl = flowControls.get(directive);
        if (directive == ProceedIf) {
            if (flowControl == null || MapUtils.isEmpty(flowControl.getConditions())) { return null; }
            return isMatched(context, flowControl) ?
                   StepResult.success("current step proceeds on") :
                   StepResult.skipped("current step skipped: " + serializeDirectives(flowControl));
        }

        if (!isMatched(context, flowControl)) { return null; }

        String flowText = serializeDirectives(flowControl);

        switch (directive) {
            case FailIf:
                if (LOGGER.isInfoEnabled()) { LOGGER.info("step " + testStep.showPosition() + " failed: " + flowText); }
                context.setFailImmediate(true);
                return StepResult.fail("current step failed: " + flowText);

            case EndIf:
                if (LOGGER.isInfoEnabled()) { LOGGER.info("test ends at " + testStep.showPosition() + ": " + flowText);}
                context.setEndImmediate(true);
                return StepResult.success("test execution ends here: " + flowText);

            case SkipIf:
                if (LOGGER.isInfoEnabled()) { LOGGER.info("step " + testStep.showPosition() + " skipped: " + flowText);}
                return StepResult.skipped("current step skipped: " + flowText);

            case EndLoopIf:
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("current iteration ends at " + testStep.showPosition() + ": " + flowText);
                }
                context.setBreakCurrentIteration(true);
                return StepResult.skipped("current iteration ends here: " + flowText);

            default:
                throw new RuntimeException("Unknown/unsupported directive " + directive);
        }
    }

    private static String serializeDirectives(FlowControl flowControl) {
        StringBuilder buffer = new StringBuilder();
        Map<String, Condition> conditions = flowControl.getConditions();
        for (Entry<String, Condition> entry : conditions.entrySet()) {
            buffer.append(TextUtils.wrapIfMissing(entry.getKey(), "\"", "\""));

            Condition value = entry.getValue();
            if (value == ANY) {
                buffer.append(" MATCH ANY ");
            } else {
                buffer.append(" ").append(value.getOperator().getSymbol()).append(" ");
                List<String> matchTo = value.getValues();
                buffer.append(CollectionUtils.isEmpty(matchTo) ? " <EMPTY> " :
                              CollectionUtils.size(matchTo) == 1 ?
                              TextUtils.wrapIfMissing(matchTo.get(0), "\"", "\"") : matchTo.toString());
            }

            buffer.append(" & ");
        }

        return StringUtils.removeEnd(buffer.toString(), " & ");
    }

    /**
     * no pausing when we run via castle/jenkins
     */
    private static boolean mustNotPause(ExecutionContext context, TestStep testStep) {
        return context == null || testStep == null || JenkinsVariables.getInstance(context).isInvokedFromJenkins();
    }

    private static boolean isMatched(ExecutionContext context, FlowControl flowControl) {
        if (context == null || flowControl == null || MapUtils.isEmpty(flowControl.getConditions())) {
            return false;
        }

        Map<String, Condition> conditions = flowControl.getConditions();

        // * condition means ALWAYS MATCHED --> meaning always pause
        if (conditions.get("*") == ANY) { return true; }

        Set<String> names = conditions.keySet();
        for (String name : names) {
            if (StringUtils.isBlank(name)) { continue; }

            String actual = TextUtils.wrapIfMissing(context.replaceTokens(name), "\"", "\"");

            Condition condition = conditions.get(name);
            if (condition == null) {
                ConsoleUtils.log("Invalid flow control specified: INVALID CONDITION");
                return false;
            }

            NexialFilterComparator operator = condition.getOperator();

            // for Is operator, we will defer the double-quote-wrap until isAtLeastOneMatched()
            String expected = condition.getValues() == null ?
                              "" :
                              TextUtils.wrapIfMissing(context.replaceTokens(condition.getValues().get(0)), "\"", "\"");
            ConsoleUtils.log("evaluating flow control:\t" + flowControl.getDirective().name() +
                             "([" + actual + "] " + operator + "? [" + expected + "])");

            switch (operator) {
                case Any: {
                    // always good
                    return true;
                }
                case Is: {
                    if (!isAtLeastOneMatched(actual, condition, context)) { return false; }
                    break;
                }
                case Equal: {
                    if (!StringUtils.equals(actual, expected)) { return false; }
                    break;
                }
                case NotEqual: {
                    if (StringUtils.equals(actual, expected)) { return false; }
                    break;
                }
                default: {
                    LOGGER.warn("Unsupport operator: " + operator);
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isAtLeastOneMatched(String actual, Condition condition, ExecutionContext context) {
        List<String> matches = condition.getValues();
        List<String> expected = new ArrayList<>();
        matches.forEach(match -> expected.add(TextUtils.wrapIfMissing(context.replaceTokens(match), "\"", "\"")));

        return StringUtils.isEmpty(actual) && expected.contains("\"\"") || expected.contains(actual);
    }
}
