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
import org.nexial.core.ExecutionThread;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelConfig;
import org.nexial.core.utils.FlowControlUtils;
import org.nexial.core.utils.TrackTimeLogs;

import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.OPT_LAST_OUTCOME;
import static org.nexial.core.excel.ExcelConfig.COL_IDX_COMMAND;
import static org.nexial.core.excel.ExcelConfig.COL_IDX_TARGET;

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
        initialTestStep = ExcelConfig.formatRepeatUntilDescription(initialTestStep, "");

        if (CollectionUtils.isEmpty(steps)) { return; }

        Worksheet worksheet = initialTestStep.getWorksheet();

        // one loop through to fix all the styles for loop steps
        for (int i = 0; i < steps.size(); i++) {
            TestStep step = steps.get(i);
            ExcelConfig.formatRepeatUntilDescription(step,
                                                     i == 0 ?
                                                     REPEAT_CHECK_DESCRIPTION_PREFIX :
                                                     REPEAT_DESCRIPTION_PREFIX);
            ExcelConfig.formatTargetCell(worksheet, step.getRow().get(COL_IDX_TARGET));
            ExcelConfig.formatCommandCell(worksheet, step.getRow().get(COL_IDX_COMMAND));
            ExcelConfig.formatParams(step);
        }
    }

    public StepResult start() {
        if (CollectionUtils.isEmpty(steps)) { return StepResult.fail("No steps to repeat/execute"); }

        long startTime = System.currentTimeMillis();
        long maxEndTime = maxWaitMs == -1 ? -1 : startTime + maxWaitMs;

        long rightNow = startTime;
        while (maxEndTime == -1 || rightNow < maxEndTime) {

            for (int i = 0; i < steps.size(); i++) {
                TestStep testStep = steps.get(i);
                ExecutionContext context = testStep.context;
                TrackTimeLogs trackTimeLogs = ExecutionThread.getTrackTimeLogs();
                trackTimeLogs.checkStartTracking(context, testStep);

                try {
                    context.setCurrentTestStep(testStep);
                    StepResult result = testStep.invokeCommand();
                    context.setData(OPT_LAST_OUTCOME, result.isSuccess());

                    if (context.isBreakCurrentIteration()) {
                        context.logCurrentStep("test stopping due to failure on break-loop condition: " +
                                               testStep.getCommandFQN());
                        return result;
                    }

                    if (i == 0) {
                        // first command is always an assertion.
                        // if this command PASS, then we've reached the condition to exit the loop
                        if (result.isSuccess()) { return StepResult.success("repeat-until execution completed"); }
                        // else failure means continue... no sweat
                    } else {
                        // evaluate if this is TRULY a failure, using result.failed() is not accurate
                        // if (result.failed()) {
                        if (!result.isSuccess() && !result.isSkipped() && !result.isWarn()) {
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

                } catch (InvocationTargetException e) {
                    // first command is assertion.. failure means we need to keep going..
                    if (i == 0) {
                        boolean isAssertError =
                            (e.getCause() != null && e.getCause() instanceof AssertionError) ||
                            (e.getTargetException() != null && e.getTargetException() instanceof AssertionError);
                        if (isAssertError) { continue; }
                    }

                    if (shouldFailFast(context, testStep)) { return StepResult.fail(resolveRootCause(e)); }
                    // else, fail-fast not in effect, so we push on
                } catch (AssertionError e) {
                    // first command is assertion.. failure means we need to keep going..
                    if (i == 0) { continue; }

                    if (shouldFailFast(context, testStep)) { return StepResult.fail(resolveRootCause(e)); }
                    // else, fail-fast not in effect, so we push on
                } catch (Throwable e) {
                    return StepResult.fail(resolveRootCause(e));
                } finally {
                    trackTimeLogs.checkEndTracking(context, testStep);
                    FlowControlUtils.checkPauseAfter(context, testStep);
                }
            }

            rightNow = System.currentTimeMillis();
        }

        if (rightNow >= maxEndTime) {
            return StepResult.fail("Unable to complete repeat-until execution within " + maxWaitMs + "ms.");
        }

        return StepResult.success("repeat-until execution completed SUCCESSFULLY");
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SIMPLE_STYLE)
                   .append("initialTestStep", initialTestStep)
                   .append("steps", steps)
                   .append("maxWaitMs", maxWaitMs)
                   .toString();
    }

    protected boolean shouldFailFast(ExecutionContext context, TestStep testStep) {
        boolean shouldFailFast = context.isFailFast() || context.isFailFastCommand(testStep);
        if (shouldFailFast) {
            context.logCurrentStep("test stopping due to failure on fail-fast command: " + testStep.getCommandFQN());
        }
        return shouldFailFast;
    }

    protected String resolveRootCause(Throwable e) {
        if (e == null) { return "UNKNOWN ERROR"; }

        if (e instanceof InvocationTargetException) {
            Throwable rootCause = ((InvocationTargetException) e).getTargetException();
            if (rootCause != null) { return rootCause.getMessage(); }
        }

        String message = e.getMessage();
        Throwable rootCause = e.getCause();
        while (rootCause != null) {
            message = rootCause.getMessage();
            rootCause = rootCause.getCause();
        }

        return message;
    }
}
