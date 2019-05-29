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

package org.nexial.core.plugins.base;

import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;

public class HeadlessBaseTests extends ExcelBasedTests {
    @Test
    public void baseCommandTests_part1() throws Exception {
        // ExecutionSummary executionSummary = testViaExcel("unitTest_base_part1.xlsx", "actual_in_output2");
        ExecutionSummary executionSummary = testViaExcel("unitTest_base_part1.xlsx");
        assertPassFail(executionSummary, "base_showcase", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "function_projectfile", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "function_array", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "function_count", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "function_date", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "actual_in_output", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "actual_in_output2", TestOutcomeStats.allPassed());
    }

    @Test
    public void baseCommandTests_part2() throws Exception {
        // ExecutionSummary executionSummary = testViaExcel("unitTest_base_part2.xlsx", "flow_controls");
        ExecutionSummary executionSummary = testViaExcel("unitTest_base_part2.xlsx");
        assertPassFail(executionSummary, "crypto", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "macro-test", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "repeat-test", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "expression-test", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "multi-scenario2", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "flow_controls", new TestOutcomeStats(2, 14));
    }

    @Test
    public void baseCommandTests_part3() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_base_part3.xlsx");
        // ExecutionSummary executionSummary = testViaExcel("unitTest_base_part3.xlsx", "function_format");
        assertPassFail(executionSummary, "function_format", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "execution_count", new TestOutcomeStats(2, 9));
    }

    @Test
    public void baseCommandTests_macro3() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_base_macro3.xlsx");
        assertPassFail(executionSummary, "start", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "macro-test", TestOutcomeStats.allPassed());
    }

    @Test
    public void numberCommandTests() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_numberCommand.xlsx");
        assertPassFail(executionSummary, "Number_Command_Validation", TestOutcomeStats.allPassed());
    }

    @Test
    public void repeatUntilTests() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_repeatUntil.xlsx");
        assertPassFail(executionSummary, "repeatUntil_take1", TestOutcomeStats.allPassed());
    }
}
