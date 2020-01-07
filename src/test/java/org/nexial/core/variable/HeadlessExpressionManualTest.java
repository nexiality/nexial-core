package org.nexial.core.variable;

import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;

public class HeadlessExpressionManualTest extends ExcelBasedTests {

    @Test
    public void testViaExcel() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_expressions_manualTest.xlsx");
        assertPassFail(executionSummary, "TEXT", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "CSV", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "LIST", TestOutcomeStats.allPassed());
    }
}