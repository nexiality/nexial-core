package org.nexial.core.plugins.web;

import java.util.List;

import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;

import static org.nexial.core.NexialConst.Data.BROWSER;

public class HeadlessWebUpdateAttributeTest extends ExcelBasedTests {

    @Test
    public void WebUpdateAttributeTest() throws Exception {
        System.setProperty(BROWSER, "chrome.headless");
        ExecutionSummary executionSummary = testViaExcel("unitTest_web-updateAttributeCommand.xlsx");
        List<ExecutionSummary> testIterations = executionSummary.getNestedExecutions().get(0).getNestedExecutions();
        for (ExecutionSummary summary : testIterations) {
            assertPassFail(summary, "UpdateAttribute", new TestOutcomeStats(1, 18));
        }
    }
}