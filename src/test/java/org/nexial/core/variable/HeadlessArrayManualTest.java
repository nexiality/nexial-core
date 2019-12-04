package org.nexial.core.variable;

import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;

public class HeadlessArrayManualTest extends ExcelBasedTests {
    @Test
    public void arrayTestViaExcel() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_array_function.xlsx");
        assertPassFail(executionSummary, "Scenario", TestOutcomeStats.allPassed());
    }
}
