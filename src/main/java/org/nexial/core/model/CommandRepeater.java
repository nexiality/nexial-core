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

package org.nexial.core.model;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.logs.ExecutionLogger;
import org.nexial.core.logs.TrackTimeLogs;
import org.nexial.core.plugins.web.WebDriverExceptionHelper;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.FlowControlUtils;
import org.openqa.selenium.WebDriverException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;
import static org.nexial.core.CommandConst.CMD_SECTION;
import static org.nexial.core.NexialConst.Data.FAIL_FAST;
import static org.nexial.core.NexialConst.Data.REPEAT_UNTIL_LOOP_INDEX;
import static org.nexial.core.NexialConst.Exec.FAIL_COUNT;
import static org.nexial.core.NexialConst.MSG_FAIL;
import static org.nexial.core.NexialConst.MSG_PASS;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.excel.ExcelStyleHelper.*;

public class CommandRepeater {
    private TestStep initialTestStep;
    private List<TestStep> steps = new ArrayList<>();
    private final long maxWaitMs;

    public CommandRepeater(TestStep testStep, long maxWait) {
        this.initialTestStep = testStep;
        this.maxWaitMs = maxWait;
    }

    public TestStep getInitialTestStep() { return initialTestStep; }

    public void addStep(TestStep nextStep) { steps.add(nextStep); }

    public int getStepCount() { return steps.size(); }

    public List<TestStep> getSteps() { return steps; }

    public void formatSteps() {
        initialTestStep = formatRepeatUntilDescription(initialTestStep);

        if (CollectionUtils.isEmpty(steps)) { return; }

        Worksheet worksheet = initialTestStep.getWorksheet();

        // one loop through to fix all the styles for loop steps
        for (TestStep step : steps) {
            formatRepeatUntilDescription(step);
            formatTargetCell(worksheet, step.getRow().get(COL_IDX_TARGET));
            formatCommandCell(worksheet, step.getRow().get(COL_IDX_COMMAND));
            formatParams(step);
        }
    }

    public StepResult start() {
        if (CollectionUtils.isEmpty(steps)) { return StepResult.fail("No steps to repeat/execute"); }

        long startTime = System.currentTimeMillis();
        long maxEndTime = maxWaitMs == -1 ? -1 : startTime + maxWaitMs;
        long rightNow = startTime;
        int errorCount = 0;
        int loopCount = 0;

        while (maxEndTime == -1 || rightNow < maxEndTime) {

            for (int i = 0; i < steps.size(); i++) {
                if (maxEndTime != -1 && rightNow >= maxEndTime) { break; }

                boolean evalContinuation = i == 0;

                TestStep testStep = steps.get(i);
                ExecutionContext context = testStep.context;
                ExecutionLogger logger = context.getLogger();

                TrackTimeLogs trackTimeLogs = context.getTrackTimeLogs();
                trackTimeLogs.checkStartTracking(context, testStep);

                StepResult result = null;
                try {
                    context.setCurrentTestStep(testStep);

                    if (evalContinuation) {
                        loopCount++;
                        context.setData(REPEAT_UNTIL_LOOP_INDEX, loopCount);
                        logRepeatUntilStart(logger, testStep, loopCount);
                    }

                    result = testStep.invokeCommand();
                    boolean succeed = result.isSuccess();
                    // skip should not be considered as failure, but "inconclusive"
                    if (!result.isSkipped()) { context.setData(OPT_LAST_OUTCOME, succeed); }

                    if (context.isBreakCurrentIteration()) {
                        logger.log(testStep, RB.RepeatUntil.text("break"));
                        return result;
                    }

                    if (evalContinuation) {
                        // first command is always an assertion.
                        // if this command PASS, then we've reached the condition to exit the loop
                        if (succeed) {
                            result = StepResult.success("repeat-until execution completed");
                            return result;
                        } else {
                            // else failure means continue... no sweat
                            logger.log(testStep, RB.RepeatUntil.text("conditionNotMet", result.getMessage()));
                        }
                    } else {
                        if (result.isSuccess()) {
                            logger.log(testStep, MSG_REPEAT_UNTIL + MSG_PASS + result.getMessage());
                        } else if (result.isSkipped()) {
                            logger.log(testStep, MSG_REPEAT_UNTIL + result.getMessage());
                        } else {
                            // fail or warn
                            logError(context, testStep, result.getMessage());
                        }

                        // evaluate if this is TRULY a failure, using result.failed() is not accurate
                        // if (result.failed()) {
                        if (result.isError()) {
                            // we are done, can't pretend everything's fine
                            if (shouldFailFast(context, testStep)) { return result; }
                            // else , fail-fast not in effect, so we push on
                        }
                        // else, continues on
                    }

                    // special case for base.section()
                    if (result.isSkipped() && StringUtils.equals(testStep.getCommandFQN(), CMD_SECTION)) {
                        // add the steps specified for the section command
                        i += testStep.formatSkippedSections(steps, i, false);
                    }

                } catch (Throwable e) {
                    result = handleException(testStep, i, e);
                    if (result != null) { return result; }
                } finally {
                    boolean isSkipped = result != null && result.isSkipped();

                    // time tracking
                    if (!isSkipped) { trackTimeLogs.checkEndTracking(context, testStep); }

                    // expand substitution in description column
                    if (!isSkipped) {
                        List<XSSFCell> row = testStep.getRow();
                        XSSFCell cellDescription = row.get(COL_IDX_DESCRIPTION);
                        String description = Excel.getCellValue(cellDescription);
                        if (StringUtils.isNotEmpty(description)) {
                            cellDescription.setCellValue(context.replaceTokens(description, true));
                        }
                    }

                    // check onError event
                    if (result != null && result.isError()) {
                        if (!evalContinuation) {
                            errorCount++;
                            context.getExecutionEventListener().onError();
                            if (context.isPauseOnError()) {
                                // pause-on-error would work on 2nd step in the loop onwards
                                ConsoleUtils.doPause(context,
                                                     "[ERROR] " + errorCount + " in repeat-until, " +
                                                     Math.max(context.getIntData(FAIL_COUNT), 0) +
                                                     " in execution. Error found in " +
                                                     ExecutionLogger.toHeader(testStep) + ": " + result.getMessage());
                            }
                        }
                    }

                    try {
                        // [NEX023] first command is always an assertion. So FAIL shouldn't trigger screenshot as error
                        if (!evalContinuation || testStep.isCaptureScreen()) { testStep.handleScreenshot(result); }
                    } catch (Throwable e) {
                        ConsoleUtils.error(testStep.messageId, e.getMessage(), e);
                    }

                    // flow control
                    FlowControlUtils.checkPauseAfter(context, testStep);
                }

                rightNow = System.currentTimeMillis();
            }
        }

        // if (maxEndTime != -1 && rightNow >= maxEndTime) {
        return StepResult.fail("Unable to complete repeat-until execution within " + maxWaitMs + "ms.");
        // } else {
        //     return StepResult.success("repeat-until execution completed SUCCESSFULLY");
        // }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SIMPLE_STYLE)
            .append("initialTestStep", initialTestStep)
            .append("steps", steps)
            .append("maxWaitMs", maxWaitMs)
            .toString();
    }

    public void close() {
        if (CollectionUtils.isNotEmpty(steps)) {
            steps.clear();
            steps = null;
        }
    }

    protected void logRepeatUntilStart(ExecutionLogger logger, TestStep testStep, int loopCount) {
        String message = RB.RepeatUntil.text("loop", loopCount);
        logger.log(testStep, NL + message + StringUtils.repeat("-", 78 - message.length()) + ">", true);
    }

    protected boolean shouldFailFast(ExecutionContext context, TestStep testStep) {
        boolean shouldFailFast = context.isFailFast() || context.isFailFastCommand(testStep);
        if (shouldFailFast) { context.logCurrentStep(RB.Abort.text("criticalCommand.fail", testStep.getCommandFQN())); }
        return shouldFailFast;
    }

    private StepResult handleException(TestStep testStep, int stepIndex, Throwable e) {
        if (e == null) { return null; }

        // first command is assertion... failure means we need to keep going...
        if (stepIndex == 0) {
            if (e instanceof AssertionError) { return null; }

            if (e instanceof InvocationTargetException) {
                InvocationTargetException e1 = (InvocationTargetException) e;
                if (e1.getCause() != null && e1.getCause() instanceof AssertionError ||
                    e1.getTargetException() != null && e1.getTargetException() instanceof AssertionError) {
                    return null;
                }
            }
        }

        ExecutionContext context = testStep.context;
        context.setData(OPT_LAST_OUTCOME, false);

        String error = resolveRootCause(e);
        logError(context, testStep, error);

        if (shouldFailFast(context, testStep)) { return StepResult.fail(error); }

        // else, fail-fast not in effect, so we push on
        return null;
    }

    private void logError(ExecutionContext context, TestStep testStep, String error) {
        context.getLogger().log(testStep,
                                MSG_REPEAT_UNTIL + MSG_FAIL + error + "; " +
                                FAIL_FAST + "=" + context.isFailFast() + "; " +
                                OPT_LAST_OUTCOME + "=" + context.getBooleanData(OPT_LAST_OUTCOME),
                                true);
    }

    private String resolveRootCause(Throwable e) {
        if (e == null) { return "UNKNOWN ERROR"; }

        Throwable exception = e;
        if (e instanceof InvocationTargetException) {
            Throwable rootCause = ((InvocationTargetException) e).getTargetException();
            exception = rootCause != null ? rootCause : e;
        }

        String message;
        if (exception instanceof WebDriverException) {
            message = WebDriverExceptionHelper.resolveErrorMessage((WebDriverException) exception);
        } else {
            message = StringUtils.defaultString(e.getMessage(), e.toString());
            Throwable rootCause = e.getCause();
            if (rootCause != e) {
                while (rootCause != null) {
                    message = StringUtils.defaultString(rootCause.getMessage(), rootCause.toString());
                    rootCause = rootCause.getCause();
                }
            }
        }

        return message;
    }
}
