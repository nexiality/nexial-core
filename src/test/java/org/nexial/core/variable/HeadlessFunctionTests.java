package org.nexial.core.variable;

import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;

public class HeadlessFunctionTests extends ExcelBasedTests {

    @Test
    public void testFormat() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_function.xlsx");
        assertPassFail(executionSummary, "format", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "date", TestOutcomeStats.allPassed());
    }
}
