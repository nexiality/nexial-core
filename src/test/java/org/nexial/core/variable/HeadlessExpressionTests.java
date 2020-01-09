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
 */

package org.nexial.core.variable;

import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;


public class HeadlessExpressionTests extends ExcelBasedTests {
    @Test
    public void testViaExcel() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_expressions.xlsx");
        assertPassFail(executionSummary, "NUMBER", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "CSV", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "EXCEL", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "XML", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "JSON", TestOutcomeStats.allPassed());
    }

    @Test
    public void macro() throws Exception {
        // System.setProperty("nexial.openResult", "true");
        ExecutionSummary executionSummary = testViaExcel("unitTest_base_macro2.xlsx", "ImportMacro");
        assertPassFail(executionSummary, "ImportMacro", TestOutcomeStats.allPassed());
    }

    @Test
    public void testViaExcel1() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_expressions1.xlsx");
        assertPassFail(executionSummary, "TEXT", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "CSV", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "LIST", TestOutcomeStats.allPassed());
    }

    @Test
    public void configTestViaExcel() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_config_expression.xlsx");
        assertPassFail(executionSummary, "CONFIG", TestOutcomeStats.allPassed());
    }

    @Test
    public void jsonTestViaExcel() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_json_expression.xlsx");
        assertPassFail(executionSummary, "Add or Replace-ADD", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "Add or Replace-REPLACE", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "Add or Replace-NONE", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "Other Commands", TestOutcomeStats.allPassed());
    }
}
