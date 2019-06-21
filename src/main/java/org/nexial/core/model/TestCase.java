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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.utils.ExecutionLogger;
import org.nexial.core.utils.TrackTimeLogs;

import static org.nexial.core.CommandConst.CMD_REPEAT_UNTIL;
import static org.nexial.core.CommandConst.CMD_SECTION;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.excel.ExcelStyleHelper.formatRepeatUntilDescription;
import static org.nexial.core.excel.ExcelStyleHelper.formatSectionDescription;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.ACTIVITY;

/**
 * aka test activity
 */
public class TestCase {
    private static final String NESTED_SECTION_STEP_SKIPPED =
        "current step skipped due to the enclosing section command being skipped";

    private String name;
    private TestScenario testScenario;
    private List<TestStep> testSteps = new ArrayList<>();
    private ExecutionSummary executionSummary = new ExecutionSummary();

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public TestScenario getTestScenario() { return testScenario; }

    public void setTestScenario(TestScenario testScenario) { this.testScenario = testScenario; }

    public List<TestStep> getTestSteps() { return testSteps; }

    public void setTestSteps(List<TestStep> testSteps) { this.testSteps = testSteps; }

    public void addTestStep(TestStep testStep) { this.testSteps.add(testStep);}

    public ExecutionSummary getExecutionSummary() { return executionSummary; }

    public void setExecutionSummary(ExecutionSummary executionSummary) { this.executionSummary = executionSummary; }

    @Override
    public String toString() {
        Worksheet worksheet = testScenario.getWorksheet();
        return new ToStringBuilder(this)
                   .append("file", worksheet.getFile())
                   .append("test scenario", worksheet.getName())
                   .append("name", name)
                   .append("testSteps", testSteps)
                   .toString();
    }

    public boolean execute() {
        ExecutionContext context = testScenario.getContext();
        TrackTimeLogs trackTimeLogs = context.getTrackTimeLogs();
        ExecutionLogger logger = context.getLogger();

        boolean allPassed = true;

        executionSummary.setName(name);
        executionSummary.setExecutionLevel(ACTIVITY);
        executionSummary.setStartTime(System.currentTimeMillis());
        executionSummary.setTotalSteps(CollectionUtils.size(testSteps));

        logger.log(this, "executing activity");

        for (int i = 0; i < testSteps.size(); i++) {
            TestStep testStep = testSteps.get(i);
            StepResult result = testStep.execute();

            if (context.isEndImmediate()) {
                executionSummary.adjustTotalSteps(-1);
                trackTimeLogs.trackingDetails("Execution Interrupted");
                break;
            }

            if (result.isSkipped()) {
                executionSummary.adjustTotalSteps(-1);
                if (StringUtils.equals(testStep.getCommandFQN(), CMD_SECTION)) {
                    formatSectionDescription(testStep, false);

                    // `testStep.getParams().get(0)` represents the number of steps of this `section`
                    int steps = Integer.parseInt(testStep.getParams().get(0));
                    for (int j = 0; j < steps; j++) {
                        int sectionStepIndex = i + j + 1;
                        if (testSteps.size() > sectionStepIndex) {
                            testSteps.get(sectionStepIndex)
                                     .postExecCommand(StepResult.skipped(NESTED_SECTION_STEP_SKIPPED), 0);

                            // reduce the number of steps for repeatUntil command
                            TestStep sectionTestStep = testSteps.get(sectionStepIndex);
                            if (sectionTestStep.isCommandRepeater()) {
                                steps -= Integer.parseInt(sectionTestStep.getParams().get(0));
                            }
                        } else {
                            steps = j - 1;
                            break;
                        }
                    }

                    i += steps;
                    executionSummary.adjustTotalSteps(-steps);
                }

                if (context.isBreakCurrentIteration()) {
                    // reset it so that we are only performing loop-break one level at a time
                    if (StringUtils.equals(testStep.getCommandFQN(), CMD_REPEAT_UNTIL)) {
                        context.setBreakCurrentIteration(false);
                    }

                    break;
                } else {
                    continue;
                }
            }

            executionSummary.incrementExecuted();

            if (result.isSuccess()) {
                executionSummary.incrementPass();
                continue;
            }

            // SKIP condition handle earlier, so this is real FAIL condition
            executionSummary.incrementFail();
            allPassed = false;

            // by default, only fail fast if we are not in interactive mode
            // this line is added here instead of outside the loop so that we can consider any changes to nexial.failFast
            // whilst executing the activity
            if (context.isFailFastCommand(testStep)) {
                logger.log(this, MSG_CRITICAL_COMMAND_FAIL + testStep.getCommandFQN());
                trackTimeLogs.trackingDetails("Execution Failed");
                context.setFailImmediate(true);
                break;
            }

            if (context.isFailImmediate()) {
                logger.log(this, MSG_EXEC_FAIL_IMMEDIATE);
                trackTimeLogs.trackingDetails("Execution Failed");
                break;
            }

            boolean shouldFailFast = context.isFailFast();
            if (shouldFailFast) {
                logger.log(this, MSG_STEP_FAIL_FAST);
                trackTimeLogs.trackingDetails("Execution Failed");
                break;
            }
        }

        formatTestCase(testSteps);

        executionSummary.setEndTime(System.currentTimeMillis());
        executionSummary.setFailedFast(context.isFailFast());
        executionSummary.aggregatedNestedExecutions(context);
        return allPassed;
    }

    public void close() {
        if (CollectionUtils.isNotEmpty(testSteps)) {
            testSteps.forEach(TestStep::close);
            testSteps.clear();
            testSteps = null;
        }

        testScenario = null;
    }

    private void formatTestCase(List<TestStep> testSteps) {
        if (CollectionUtils.isEmpty(testSteps)) { return; }

        // compensate for macro/section
        int totalSteps = testSteps.size();
        for (int i = 0; i < totalSteps; i++) {
            TestStep testStep = testSteps.get(i);

            if (testStep.isCommandRepeater() && testStep.getCommandRepeater() != null) {
                testStep = formatRepeatUntilDescription(testStep, "");
                testStep.getCommandRepeater().formatSteps();
            }

            if (StringUtils.equals(testStep.getCommandFQN(), CMD_SECTION)) {
                formatSectionDescription(testStep, false);

                int adjustedStepCount = i + Integer.parseInt(testStep.getParams().get(0));
                for (int j = i + 1; j <= adjustedStepCount; j++) {
                    if (totalSteps > j) {
                        TestStep sectionStep = testSteps.get(j);
                        formatSectionDescription(sectionStep, true);

                        if (sectionStep.isCommandRepeater()) {
                            formatRepeatUntilDescription(sectionStep, "");
                            sectionStep.getCommandRepeater().formatSteps();
                            adjustedStepCount -= sectionStep.getCommandRepeater().getStepCount();
                        }
                    } else {
                        break;
                    }
                }

                i += adjustedStepCount - 1;
            }
        }
    }

}
