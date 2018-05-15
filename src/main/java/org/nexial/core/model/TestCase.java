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

import static org.nexial.core.NexialConst.Data.CMD_COMMAND_SECTION;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.ACTIVITY;

/**
 * aka test activity
 */
public class TestCase {
    private static final String NESTED_SECTION_STEP_SKIPPED =
        "current step skipped due to the enclosed section command being skipped";

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
        ExecutionLogger logger = context.getLogger();

        boolean allPassed = true;

        executionSummary.setName(name);
        executionSummary.setExecutionLevel(ACTIVITY);
        executionSummary.setStartTime(System.currentTimeMillis());
        executionSummary.setTotalSteps(CollectionUtils.size(testSteps));

        logger.log(this, "executing test case");

        for (int i = 0; i < testSteps.size(); i++) {
            TestStep testStep = testSteps.get(i);
            StepResult result = testStep.execute();

            if (context.isEndImmediate()) {
                executionSummary.adjustTotalSteps(-1);
                break;
            }

            if (result.isSkipped()) {
                executionSummary.adjustTotalSteps(-1);
                if (StringUtils.equals(testStep.getCommandFQN(), CMD_COMMAND_SECTION)) {
                    int steps = Integer.parseInt(testStep.getParams().get(0));
                    for (int j = 0; j < steps; j++) {
                        testSteps.get(i + j + 1).postExecCommand(StepResult.skipped(NESTED_SECTION_STEP_SKIPPED), 0);
                    }
                    i += steps;
                    executionSummary.adjustTotalSteps(-steps);
                }

                if (context.isBreakCurrentIteration()) {
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

            executionSummary.incrementFail();
            allPassed = false;

            // by default, only fail fast if we are not in interactive mode
            // this line is added instead of outside the loop so that we can consider any changes to nexial.failFast
            // whilst executing the activity
            boolean shouldFailFast = !context.isInterativeMode() && context.isFailFast();
            if (shouldFailFast) {
                logger.log(testStep, "test stopping due to execution failure and fail-fast in effect");
                break;
            }
            if (context.isFailFastCommand(testStep)) {
                logger.log(testStep, "test stopping due to failure on fail-fast command: " + testStep.getCommandFQN());
                break;
            }
            if (context.isFailImmediate()) {
                logger.log(testStep, "test stopping due fail-immediate in effect");
                break;
            }
        }

        executionSummary.setEndTime(System.currentTimeMillis());
        executionSummary.setFailedFast(!context.isInterativeMode() && context.isFailFast());
        executionSummary.aggregatedNestedExecutions(context);
        return allPassed;
    }

}
