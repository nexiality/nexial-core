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

package org.nexial.core;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.ExecutionSummary;

import static org.nexial.core.NexialConst.OPT_RUN_ID;

public class ExecutionInterruptTests extends ExcelBasedTests {

    @Override
    @Before
    public void init() throws Exception {
        super.init();

        // System.setProperty(OUTPUT_TO_CLOUD, "true");
        // System.setProperty(OUTPUT_TO_CLOUD, "false");
        // System.setProperty(OPT_RUN_ID_PREFIX, "unitTest_ExecIntrpt");
        // System.setProperty(OPT_OPEN_RESULT, "off");

        System.out.println();
    }

    @Test
    public void failfast_in_scenario() throws Exception {
        ExecutionSummary executionSummary =
            new ExcelBasedTestBuilder().setScript("unitTest_ExecInterrupt.xlsx").execute();
        assertPassFail(executionSummary, "failfast_in_scenario", new TestOutcomeStats(1, 1));
        assertScenarioNotExecuted(executionSummary, "dummy1");
        assertScenarioNotExecuted(executionSummary, "dummy2");

        System.out.println();

        executionSummary =
            new ExcelBasedTestBuilder().setScript("unitTest_ExecInterrupt.xlsx")
                                       .setScenarios(Arrays.asList("dummy1", "failfast_in_scenario2", "dummy2"))
                                       .execute();
        assertPassFail(executionSummary, "dummy1", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "failfast_in_scenario2", new TestOutcomeStats(1, 1));
        assertScenarioNotExecuted(executionSummary, "dummy2");
    }

    @Test
    public void failfast_in_iteration() throws Exception {
        // scenario1,scenario2 => nexial.failFast=true
        // hence all 6 iterations will run
        ExecutionSummary executionSummary =
            new ExcelBasedTestBuilder().setScript("unitTest_ExecInterrupt_iter.xlsx").execute();
        List<ExecutionSummary> iterations = executionSummary.getNestedExecutions().get(0).getNestedExecutions();

        Assert.assertEquals(3, iterations.size());

        ExecutionSummary iterationSummary = iterations.get(0);
        assertPassFail(iterationSummary, "scenario1", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "scenario2", TestOutcomeStats.allPassed());

        iterationSummary = iterations.get(1);
        assertPassFail(iterationSummary, "scenario1", new TestOutcomeStats(1, 2));
        assertPassFail(iterationSummary, "scenario2", TestOutcomeStats.allPassed());

        iterationSummary = iterations.get(2);
        assertPassFail(iterationSummary, "scenario1", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "scenario2", new TestOutcomeStats(1, 2));

        System.out.println();

        // scenario2,scenario1 => nexial.failFast=false
        // hence execution will fail when first error occurred
        executionSummary =
            new ExcelBasedTestBuilder().setScript("unitTest_ExecInterrupt_iter.xlsx")
                                       .setScenarios(Arrays.asList("scenario2", "scenario1"))
                                       .execute();
        iterations = executionSummary.getNestedExecutions().get(0).getNestedExecutions();

        Assert.assertEquals(2, iterations.size());

        iterationSummary = iterations.get(0);
        assertPassFail(iterationSummary, "scenario2", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "scenario1", TestOutcomeStats.allPassed());

        iterationSummary = iterations.get(1);
        assertPassFail(iterationSummary, "scenario2", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "scenario1", new TestOutcomeStats(1, 1));
    }

    @Test
    public void failfast_in_plan() throws Exception {
        ExecutionSummary executionSummary =
            new ExcelBasedTestBuilder().setPlan("unitTest_ExecInterrupt_plan.xlsx").execute();

        List<ExecutionSummary> planExecutions = executionSummary.getNestedExecutions();
        Assert.assertEquals(3, planExecutions.size());

        // sequence 1: iteration with failure
        List<ExecutionSummary> iterations = planExecutions.get(0).getNestedExecutions();
        Assert.assertEquals(3, iterations.size());

        ExecutionSummary iterationSummary = iterations.get(0);
        assertPassFail(iterationSummary, "scenario1", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "scenario2", TestOutcomeStats.allPassed());

        iterationSummary = iterations.get(1);
        assertPassFail(iterationSummary, "scenario1", new TestOutcomeStats(1, 2));
        assertPassFail(iterationSummary, "scenario2", TestOutcomeStats.allPassed());

        iterationSummary = iterations.get(2);
        assertPassFail(iterationSummary, "scenario1", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "scenario2", new TestOutcomeStats(1, 2));

        // sequence 2: expects failure
        iterations = planExecutions.get(1).getNestedExecutions();
        Assert.assertEquals(1, iterations.size());
        iterationSummary = iterations.get(0);
        assertPassFail(iterationSummary, "dummy2", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "failfast_in_scenario2", new TestOutcomeStats(1, 1));
        assertScenarioNotExecuted(iterationSummary, "dummy1");

        // sequence 4: expects not to run
        iterations = planExecutions.get(2).getNestedExecutions();
        Assert.assertTrue(CollectionUtils.isEmpty(iterations));
    }

    @Test
    public void failfast_in_plan2() throws Exception {
        ExecutionSummary executionSummary =
            new ExcelBasedTestBuilder().setPlan("unitTest_ExecInterrupt_plan2.xlsx").execute();

        List<ExecutionSummary> planExecutions = executionSummary.getNestedExecutions();
        Assert.assertEquals(3, planExecutions.size());

        // sequence 1: iteration with failure
        List<ExecutionSummary> iterations = planExecutions.get(0).getNestedExecutions();
        Assert.assertEquals(3, iterations.size());

        ExecutionSummary iterationSummary = iterations.get(0);
        assertPassFail(iterationSummary, "scenario1", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "scenario2", TestOutcomeStats.allPassed());

        iterationSummary = iterations.get(1);
        assertPassFail(iterationSummary, "scenario1", new TestOutcomeStats(1, 2));
        assertPassFail(iterationSummary, "scenario2", TestOutcomeStats.allPassed());

        iterationSummary = iterations.get(2);
        assertPassFail(iterationSummary, "scenario1", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "scenario2", new TestOutcomeStats(1, 2));

        // sequence 2: expects failure
        iterations = planExecutions.get(1).getNestedExecutions();
        Assert.assertEquals(1, iterations.size());
        iterationSummary = iterations.get(0);
        assertPassFail(iterationSummary, "dummy2", TestOutcomeStats.allPassed());
        assertPassFail(iterationSummary, "failfast_in_scenario2", new TestOutcomeStats(1, 1));
        assertScenarioNotExecuted(iterationSummary, "dummy1");

        // sequence 3: expects not to run
        iterations = planExecutions.get(2).getNestedExecutions();
        Assert.assertEquals(1, iterations.size());
        iterationSummary = iterations.get(0);
        assertPassFail(iterationSummary, "failfast_in_scenario", new TestOutcomeStats(1, 1));
    }

    static {
        System.clearProperty(OPT_RUN_ID);
    }
}
