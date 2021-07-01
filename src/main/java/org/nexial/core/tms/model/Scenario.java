package org.nexial.core.tms.model;

/**
 * Represents a single test case entry in the TMS Meta Json file. Each script can have multiple scenarios
 */
public class Scenario {
    private final String testCase;
    private final String scenarioName;
    private final String testCaseId;

    public Scenario(String testCase, String scenarioName, String testCaseId) {
        this.testCase = testCase;
        this.scenarioName    = scenarioName;
        this.testCaseId = testCaseId;
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public String getTestCase() {
        return testCase;
    }

    public String getName() {
        return scenarioName;
    }
}
