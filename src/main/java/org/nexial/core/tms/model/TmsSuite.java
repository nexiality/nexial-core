package org.nexial.core.tms.model;

import java.util.Map;

/**
 * Represents a TMS Test Suite, contains the name, id and the url of the test suite as well as the test cases present inside it
 */
public class TmsSuite {
    private final String name;
    private final String id;
    private String suiteUrl;
    private final Map<String, String> testCases;

    public TmsSuite(String name, String id, Map<String, String> testCases) {
        this.name      = name;
        this.id        = id;
        this.testCases = testCases;
    }

    public TmsSuite(String name, String id, Map<String, String> testCases, String suiteUrl) {
        this.name      = name;
        this.id        = id;
        this.testCases = testCases;
        this.suiteUrl  = suiteUrl;
    }

    public String getSuiteUrl() {
        return suiteUrl;
    }

    public Map<String, String> getTestCases() {
        return testCases;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }
}
