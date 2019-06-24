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

package org.nexial.core.model;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecutionLogger;
import org.nexial.core.utils.FlowControlUtils;
import org.nexial.core.utils.TrackTimeLogs;

import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;
import static org.nexial.core.CommandConst.CMD_SECTION;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.FlowControls.OPT_PAUSE_ON_ERROR;
import static org.nexial.core.NexialConst.MSG_FAIL;
import static org.nexial.core.NexialConst.MSG_PASS;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.excel.ExcelStyleHelper.*;

public class CommandRepeater {
    private TestStep initialTestStep;
    private List<TestStep> steps = new ArrayList<>();
    private long maxWaitMs;

    public CommandRepeater(TestStep testStep, long maxWait) {
        this.initialTestStep = testStep;
        this.maxWaitMs = maxWait;
    }

    public TestStep getInitialTestStep() { return initialTestStep; }

    public void addStep(TestStep nextStep) { steps.add(nextStep);}

    public int getStepCount() { return steps.size(); }

    public void formatSteps() {
        initialTestStep = formatRepeatUntilDescription(initialTestStep, "");

        if (CollectionUtils.isEmpty(steps)) { return; }

        Worksheet worksheet = initialTestStep.getWorksheet();

        // one loop through to fix all the styles for loop steps
        for (int i = 0; i < steps.size(); i++) {
            TestStep step = steps.get(i);
            formatRepeatUntilDescription(step, i == 0 ? REPEAT_CHECK_DESCRIPTION_PREFIX : REPEAT_DESCRIPTION_PREFIX);
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

                TestStep testStep = steps.get(i);
                ExecutionContext context = testStep.context;
                ExecutionLogger logger = context.getLogger();

                TrackTimeLogs trackTimeLogs = context.getTrackTimeLogs();
                trackTimeLogs.checkStartTracking(context, testStep);

                StepResult result = null;
                try {
                    context.setCurrentTestStep(testStep);

                    if (i == 0) {
                        loopCount++;
                        logRepeatUntilStart(logger, testStep, loopCount);
                    }

                    result = testStep.invokeCommand();
                    boolean succeed = result.isSuccess();
                    context.setData(OPT_LAST_OUTCOME, succeed);

                    if (context.isBreakCurrentIteration()) {
                        logger.log(testStep, MSG_REPEAT_UNTIL_BREAK);
                        return result;
                    }

                    if (i == 0) {
                        // first command is always an assertion.
                        // if this command PASS, then we've reached the condition to exit the loop
                        if (succeed) {
                            result = StepResult.success("repeat-until execution completed");
                            return result;
                        } else {
                            // else failure means continue... no sweat
                            logger.log(testStep,
                                       MSG_REPEAT_UNTIL + "condition not met (" + result.getMessage() + "); " +
                                       "loop proceeds");
                        }
                    } else {
                        if (result.isSuccess()) {
                            logger.log(testStep, MSG_REPEAT_UNTIL + MSG_PASS + result.getMessage());
                        } else if (result.isSkipped()) {
                            logger.log(testStep, MSG_REPEAT_UNTIL + result.getMessage());
                        } else {
                            // fail or warn
                            logger.log(testStep,
                                       MSG_REPEAT_UNTIL + MSG_FAIL + result.getMessage() + "; " +
                                       FAIL_FAST + "=" + context.isFailFast() + "; " +
                                       OPT_LAST_OUTCOME + "=false",
                                       true);
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
                        i += Integer.parseInt(testStep.getParams().get(0));
                    }

                } catch (Throwable e) {
                    result = handleException(testStep, i, e);
                    if (result != null) { return result; }
                } finally {
                    // time tracking
                    trackTimeLogs.checkEndTracking(context, testStep);

                    // expand substitution in description column
                    XSSFCell cellDescription = testStep.getRow().get(COL_IDX_DESCRIPTION);
                    String description = Excel.getCellValue(cellDescription);
                    if (StringUtils.isNotEmpty(description)) {
                        cellDescription.setCellValue(context.replaceTokens(description, true));
                    }

                    // check onError event
                    if (result != null && result.isError()) {
                        errorCount++;
                        context.getExecutionEventListener().onError();

                        if (context.getBooleanData(OPT_PAUSE_ON_ERROR, getDefaultBool(OPT_PAUSE_ON_ERROR))) {
                            ConsoleUtils.doPause(context,
                                                 "[ERROR] " + errorCount + " in repeat-until, " +
                                                 Math.max(context.getIntData(EXECUTION_FAIL_COUNT), 0) +
                                                 " in execution. Error found in " +
                                                 ExecutionLogger.toHeader(testStep) + ": " + result.getMessage());
                        }
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
        String message = "<" + MSG_REPEAT_UNTIL + "entering loop #" + loopCount + " ";
        message += StringUtils.repeat("-", 78 - message.length()) + ">";
        logger.log(testStep, "\n" + message, true);
    }

    protected boolean shouldFailFast(ExecutionContext context, TestStep testStep) {
        boolean shouldFailFast = context.isFailFast() || context.isFailFastCommand(testStep);
        if (shouldFailFast) { context.logCurrentStep(MSG_CRITICAL_COMMAND_FAIL + testStep.getCommandFQN()); }
        return shouldFailFast;
    }

    protected String resolveRootCause(Throwable e) {
        if (e == null) { return "UNKNOWN ERROR"; }

        if (e instanceof InvocationTargetException) {
            Throwable rootCause = ((InvocationTargetException) e).getTargetException();
            if (rootCause != null) { return rootCause.getMessage(); }
        }

        String message = StringUtils.defaultString(e.getMessage(), e.toString());
        Throwable rootCause = e.getCause();
        if (rootCause != e) {
            while (rootCause != null) {
                message = StringUtils.defaultString(rootCause.getMessage(), rootCause.toString());
                rootCause = rootCause.getCause();
            }
        }

        return message;
    }

    private StepResult handleException(TestStep testStep, int stepIndex, Throwable e) {
        if (e == null) { return null; }

        // first command is assertion.. failure means we need to keep going..
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
        if (shouldFailFast(context, testStep)) { return StepResult.fail(resolveRootCause(e)); }

        // else, fail-fast not in effect, so we push on
        return null;
    }
}
