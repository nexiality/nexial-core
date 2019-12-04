package org.nexial.core.variable;

import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;

public class HeadlessExpressionsManualTest extends ExcelBasedTests {
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
