package org.nexial.core.tms.spi.testrail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.nexial.core.tms.TMSOperation;
import org.nexial.core.tms.model.BDDKeywords;
import org.nexial.core.tms.model.TmsCustomStep;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.tms.model.TmsTestStep;
import org.nexial.core.tms.spi.UpdateTestFiles;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.nexial.core.NexialConst.TMSSettings.*;
import static org.nexial.core.tms.TmsConst.*;

/**
 * Class containing methods relating to various test rail operations
 */
public class TestRailOperations implements TMSOperation {
    private static APIClient client;
    private final String projectId;

    public String getProjectId() {
        return projectId;
    }

    public TestRailOperations(String projectId) {
        client         = getClient();
        this.projectId = projectId;
    }

    /**
     * Returns an instance of the {@link APIClient} based on the username, password and url set up inside setuo.jar. The spring context is
     * required to read the values from the nexial.jar file
     *
     * @return the API client
     */
    public APIClient getClient() {
        // need the spring config here to read the TMS properties from setup.jar
        ClassPathXmlApplicationContext springContext = new ClassPathXmlApplicationContext(CLASSPATH_NEXIAL_INIT_XML);
        String username = System.getProperty(TMS_USERNAME);
        String password = System.getProperty(TMS_PASSWORD);
        String url = System.getProperty(TMS_URL);

        if (StringUtils.isAnyEmpty(username, password, url)) {
            System.out.println("TMS credentials not available");
            System.exit(-1);
        }
        client = new APIClient(url);
        client.setUser(username);
        client.setPassword(password);
        return client;
    }

    /**
     * Create a new test suite with the provided suite name
     *
     * @param suiteName the name of suite
     * @return the json response of the API after suite creation
     */
    public JSONObject createSuite(String suiteName) {
        Map<String, Object> suite = new HashMap<>();
        JSONObject jsonObject = null;
        suite.put(NAME, suiteName);
        suite.put(DESCRIPTION, "Test suite corresponding to script " + suiteName);
        try {
            System.out.println("Creating suite for project id: " + projectId + " with name: " + suiteName);
            jsonObject = (JSONObject) client.sendPost("add_suite/" + projectId, suite);
        } catch (Exception e) {
            System.err.println("Suite creation unsuccessful: " + e.getMessage());
            System.exit(-1);
        }
        return jsonObject;
    }

    /**
     * Create a new section inside the suite associated with the specified suite id
     *
     * @param sectionName the name of the section
     * @param suiteId     the suite id in which we the section is to be added
     * @return the json response of the API after section creation
     */
    public JSONObject addSection(String sectionName, String suiteId) {
        JSONObject jsonObject = null;
        Map<String, Object> section = new HashMap<>();
        section.put(DESCRIPTION, "Section corresponding to script: " + sectionName);
        section.put(NAME, sectionName);
        section.put(SUITE_ID, suiteId);
        try {
            System.out.println("Creating section for suite: " + sectionName);
            jsonObject = (JSONObject) client.sendPost("add_section/" + projectId, section);
        } catch (Exception e) {
            System.err.println("Section creation unsuccessful: " + e.getMessage());
            System.exit(-1);
        }
        return jsonObject;
    }

    /**
     * Add new test cases inside the section specified
     *
     * @param section   the json response received after section creation
     * @param testCases {@link List} of {@link TmsTestCase} containing test cases and test steps
     * @return a {@link Map} of test case id to the test case name
     */
    public Map<String, String> addCases(JSONObject section, List<? extends TmsTestCase> testCases) {
        Map<String, String> testCaseIdToTestName = new HashMap<>();
        for (TmsTestCase tmsTestCase : testCases) {
            testCaseIdToTestName.putAll(updateCase(section.get("id").toString(), tmsTestCase, true));
        }
        return testCaseIdToTestName;
    }

    /**
     * Adds or update a single test case based on the input parameters
     *
     * @param id            in case of new test case, this will represent the section id in which new test case is to be added,
     *                      otherwise it represents the test case id of the test case to update
     * @param tmsTestCase   an instance of {@link TmsTestCase} containing Test Case and Test Steps
     * @param isNewTestCase true if a new test case is to be added, false if an existing test case is to be updated
     * @return a {@link Map} of the test case id to the test case name
     */
    public Map<String, String> updateCase(String id, TmsTestCase tmsTestCase, boolean isNewTestCase) {
        if (tmsTestCase == null || StringUtils.isEmpty(id)) {
            System.err.println("Unable to add or update the test case");
            System.exit(-1);
        }
        Map<String, String> testCaseToTestName = new HashMap<>();
        JSONObject response = null;
        String testCaseName = getTestCaseName(tmsTestCase);
        Map<String, Object> testCase = getTestCase(tmsTestCase, testCaseName);
        if (isNewTestCase) {
            try {
                System.out.println("Adding test case: " + testCase.get(TITLE));
                response = (JSONObject) client.sendPost("add_case/" + id, testCase);
            } catch (Exception e) {
                System.err.println(
                        "Could not add test case: " + testCase.get("title") + " : " + e.getMessage());
                System.exit(-1);
            }
            if (ObjectUtils.isEmpty(response)) {
                System.err.println("Could not add test case: " + testCase.get(TITLE).toString());
            }
        } else {
            try {
                System.out.println("Updating test case: " + testCase.get(TITLE));
                response = (JSONObject) client.sendPost("update_case/" + id, testCase);
            } catch (Exception e) {
                System.err.println("Could not update test case: " + testCaseName + " : " + e.getMessage());
                System.exit(-1);
            }
            if (ObjectUtils.isEmpty(response)) {
                System.err.println("Could not update test case: " + testCaseName);
                System.exit(-1);
            }
        }
        testCaseToTestName.put(testCaseName, response.get(ID).toString());
        UpdateTestFiles.testCaseMap.put(testCaseName, response);
        return testCaseToTestName;
    }

    /**
     * Returns a Test Rail Test Case to be sent as an input to the Test Case creation API
     *
     * @param tmsTestCase  {@link TmsTestCase} instance containing the test case name and the test steps
     * @param testCaseName the name of the test case
     * @return a {@link Map} representing the input to the test case creation API
     */
    @Nullable
    private Map<String, Object> getTestCase(TmsTestCase tmsTestCase, String testCaseName) {
        List<Map<String, String>> testSteps = getTestSteps(tmsTestCase.getTestSteps());
        if (CollectionUtils.isEmpty(testSteps)) { return null; }
        Map<String, Object> testCase = new HashMap<>();
        testCase.put(TITLE, testCaseName);
        testCase.put(TEMPLATE_ID, 2);
        testCase.put(CUSTOM_PRECONDS, "See the Steps section for details");
        testCase.put(CUSTOM_STEPS_SEPARATED, testSteps);
        return testCase;
    }

    /**
     * Returns the test case name based on the scenario name from the {@link TmsTestCase} instance passed in
     *
     * @param scenario a {@link TmsTestCase} instance representing a scenario in Nexial
     * @return the name of the test case
     */
    private String getTestCaseName(TmsTestCase scenario) {
        String scriptName = scenario.getScriptName();
        String row = scenario.getRow();
        String scenarioName = scenario.getName();
        if (!StringUtils.isEmpty(scriptName) && !StringUtils.isEmpty(row)) {
            return scriptName + "/" + scenarioName + "/" + row;
        }
        return scenarioName;
    }

    /**
     * Returns a {@link List} of the test steps along with the custom separated steps.
     *
     * @param testSteps a {@link List} of test steps for a Test Case. Represented by activities in Nexial
     * @return a {@link List} of {@link Map} containing the test steps and their custom steps
     */
    private List<Map<String, String>> getTestSteps(List<TmsTestStep> testSteps) {
        List<Map<String, String>> stepsSeparated = new ArrayList<>();

        for (TmsTestStep step : testSteps) {
            Map<String, String> testStep = new HashMap<>();
            testStep.put("content", step.getName());
            StringBuilder builder = new StringBuilder();
            builder.append("||| " + ROW).append(" | ").append(TEST_STEP).append("\n");
            boolean isEmpty = true;
            for (TmsCustomStep customStep : step.getTmsCustomSteps()) {
                if (!StringUtils.isEmpty(customStep.getDescription())) { isEmpty = false; }
                builder.append("|| ");
                builder.append(customStep.getRowIndex() + 1);
                builder.append(" | ");
                builder.append(formatStepDescription(customStep.getDescription()));
                builder.append("\n");
            }
            if (isEmpty) { continue; }
            builder.append(StringUtils.substringBefore(step.getMessageId(), "[STEP"));
            testStep.put(EXPECTED, builder.toString());
            stepsSeparated.add(testStep);
        }
        return stepsSeparated;
    }

    /**
     * Format the custom step description on the basis of the {@link BDDKeywords}
     *
     * @param step a single custom test step
     * @return a formatted custom step
     */
    private String formatStepDescription(String step) {
        String[] stepWords = step.split(" ");
        String firstWord = stepWords[0];

        if (StringUtils.equals(BDDKeywords.AND.getKeyword(), firstWord) ||
            !EnumUtils.isValidEnum(BDDKeywords.class, firstWord)) {
            step = NBSP + step;
        }
        return step;
    }

    /**
     * Delete the specified test cases from the suite
     *
     * @param testCasesToDelete {@link List} of test cases to delete
     */
    public void delete(List<String> testCasesToDelete) {
        for (String id : testCasesToDelete) {
            try {
                System.out.println("Deleting test case: " + id);
                client.sendPost("delete_case/" + id, 0);
            } catch (Exception e) {
                System.err.println("Could not delete test case: " + id + " : " + e.getMessage());
                System.exit(-1);
            }
        }
    }

    /**
     * Return the existing runs for the suite associated with the specified suite id
     *
     * @param suiteId suite id
     * @return a {@link JSONArray} of all the Test runs associated with the suite
     */
    public JSONArray getExistingRuns(@NotNull String suiteId) {
        JSONArray jsonArray = null;
        try {
            System.out.println("Retrieving existing runs for suiteId: " + suiteId);
            jsonArray = (JSONArray) client.sendGet("get_runs/" + projectId + "&suite_id=" + suiteId);
        } catch (Exception e) {
            System.err.println("Could not retrieve existing runs for suiteId: " + suiteId + ": " + e.getMessage());
            System.exit(-1);
        }
        return jsonArray;
    }

    /**
     * Return all the Test Cases belonging to the suite associated with the specified suite id
     *
     * @param suiteId the suite id
     * @return a {@link JSONArray} of the test cases inside the suite
     */
    public JSONArray getCasesForSuite(String suiteId) {
        JSONArray jsonArray = null;
        try {
            System.out.println("Retrieving test cases for suiteId: " + suiteId);
            jsonArray = (JSONArray) client.sendGet("get_cases/" + projectId + "&suite_id=" + suiteId);
        } catch (Exception e) {
            System.err.println("Could not retrieve test cases: " + e.getMessage());
            System.exit(-1);
        }
        return jsonArray;
    }

    /**
     * Reorder the Test Cases inside the suite id specified according to their order in the String passed in
     *
     * @param suiteId          the suite id
     * @param sectionId        the section id
     * @param scenariosInOrder {@link StringBuilder} containing the test case ids in order
     */
    public void updateCaseOrder(String suiteId, String sectionId, String scenariosInOrder) {
        try {
            if (StringUtils.isEmpty(sectionId)) {
                JSONObject section = (JSONObject) getSections(suiteId).get(0);
                sectionId = section.get(ID).toString();
            }
            Map<String, String> scenarios = new HashMap<>();
            scenarios.put(CASE_IDS, scenariosInOrder);
            System.out.println("Re-ordering test cases for suite id: " + suiteId);
            client.sendPost("move_cases_to_section/" + sectionId, scenarios);
        } catch (Exception e) {
            System.err.println("Error occurred during re-ordering of test cases: " + e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Get all the existing active runs for the suite id passed in
     *
     * @param suiteId the suite id
     * @return a {@link JSONArray} containing the active runs
     */
    public JSONArray getExistingActiveRuns(String suiteId) {
        JSONArray activeRuns = new JSONArray();
        try {
            activeRuns =
                    (JSONArray) client.sendGet("get_runs/" + projectId + "&suite_id=" + suiteId + "&is_completed=0");
            if (isEmpty(activeRuns)) {
                System.out.println("There are no active runs for the specified file. All runs are already closed");
            }
        } catch (APIException | IOException e) {
            System.err.println("Could not retrieve active test runs: " + e.getMessage());
            System.exit(-1);
        }
        return activeRuns;
    }

    /**
     * Close the Test Run associated with the Test Run id passed in
     *
     * @param runId test run id
     */
    public void closeRun(String runId) {
        try {
            Map<String, String> runMap = new HashMap<>();
            runMap.put(RUN, runId);
            JSONObject run = (JSONObject) client.sendPost("close_run/" + runId, runMap);
            System.out.println("Closed test run: " + run.get(ID) + " | " + run.get(NAME));
        } catch (APIException | IOException e) {
            System.err.println("Error occurred during closing of active test run:" + runId + ": " + e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Retrieve all the sections associated with the suite id passed in
     *
     * @param suiteId the suite id
     * @return JSONArray containing the sections belonging to the suite
     */
    public JSONArray getSections(String suiteId) {
        JSONArray sections = new JSONArray();
        try {
            sections = (JSONArray) client.sendGet("get_sections/" + projectId + "&suite_id=" + suiteId);
        } catch (Exception e) {
            System.err.println(
                    "Unable to retrieve sections for project: " + projectId + "and suite id: " + suiteId + " : " +
                    e.getMessage());
            System.exit(-1);
        }
        return sections;
    }

    @Override
    public void setProjectId(@NotNull String projectId) {

    }

    @Override
    public void setClient(@NotNull APIClient client) {

    }
}
