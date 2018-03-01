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
import org.apache.commons.lang3.builder.ToStringBuilder;

import org.nexial.core.utils.FlowControlUtils;

import static org.nexial.core.NexialConst.OPT_LAST_OUTCOME;
import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;

public class CommandRepeater {
    private TestStep initialTestStep;
    private List<TestStep> steps = new ArrayList<>();
    private long maxWaitMs = -1;

    public CommandRepeater(TestStep testStep, long maxWait) {
        this.initialTestStep = testStep;
        this.maxWaitMs = maxWait;
    }

    public TestStep getInitialTestStep() { return initialTestStep; }

    public void addStep(TestStep nextStep) { steps.add(nextStep);}

    public StepResult start() {
        if (CollectionUtils.isEmpty(steps)) { return StepResult.fail("No steps to repeat/execute"); }

        long startTime = System.currentTimeMillis();
        long maxEndTime = maxWaitMs == -1 ? -1 : startTime + maxWaitMs;

        long rightNow = startTime;
        while (maxEndTime == -1 || rightNow < maxEndTime) {

            for (int i = 0; i < steps.size(); i++) {
                TestStep testStep = steps.get(i);
                ExecutionContext context = testStep.context;

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
                        if (result.isSuccess()) {
                            // first command is always an assertion.
                            // if this command PASS, then we've reached the condition to exit the loop
                            return StepResult.success("repeat-until execution completed");
                        }
                        // else failure means continue... no sweat
                    } else {
                        // evaluate if this is TRULY a failure, using result.failed() is not accurate
                        // if (result.failed()) {
                        if (!result.isSuccess() && !result.isSkipped() && !result.isWarn()) {
                            if (context.isFailFast() || context.isFailFastCommand(testStep)) {
                                context.logCurrentStep("test stopping due to failure on fail-fast command: " +
                                                       testStep.getCommandFQN());
                                // we are done, can't pretend everything's fine
                                return result;
                            }
                            // else , fail-fast not in effect, so we push on
                        }
                        // else, continues on
                    }
                } catch (InvocationTargetException e) {
                    // first command is assertion.. failure means we need to keep going..
                    if (e.getCause() != null && e.getCause() instanceof AssertionError && i == 0) { continue; }

                    return StepResult.fail(resolveRootCause(e));
                } catch (AssertionError e) {
                    // first command is assertion.. failure means we need to keep going..
                    if (i == 0) { continue; }

                    return StepResult.fail(resolveRootCause(e));
                } catch (Throwable e) {
                    return StepResult.fail(resolveRootCause(e));
                } finally {
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
