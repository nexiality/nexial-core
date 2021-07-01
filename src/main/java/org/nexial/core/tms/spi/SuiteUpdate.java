package org.nexial.core.tms.spi;

import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.nexial.core.tms.TMSOperation;
import org.nexial.core.tms.TmsFactory;
import org.nexial.core.tms.model.Scenario;
import org.nexial.core.tms.model.TestFile;
import org.nexial.core.tms.model.TmsSuite;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.tms.tools.CloseTestRun;

import static org.nexial.core.excel.ExcelConfig.ADDR_PLAN_EXECUTION_START;
import static org.nexial.core.tms.TmsConst.*;
import static org.nexial.core.tms.spi.TmsProcessor.*;

/**
 * Perform update operations on an already existing suite
 */
public class SuiteUpdate {

    private String sectionId;
    private final String filepath;
    private final TestFile file;
    private final String suiteId;
    private Map<String, Map<String, String>> testCaseToStep;
    private final TMSOperation tms;

    public Map<String, Map<String, String>> getTestCaseToStep() {
        return testCaseToStep;
    }

    public SuiteUpdate(String filepath, TestFile file, String projectId) {
        this.filepath = filepath;
        this.file     = file;
        suiteId       = file.getSuiteId();
        tms = new TmsFactory().getTmsInstance(projectId);
    }

    /**
     * Checks if suite id specified has any active test runs associated with it and prompts the user with options as to
     * update the suite of not.
     *
     * @return true if the user opts for an update, otherwise return false
     */
    protected boolean shouldUpdateSuite() {
        try {
            JSONArray existingRuns = tms.getExistingActiveRuns(suiteId);
            if (CollectionUtils.isEmpty(existingRuns)) {
                return true;
            } else { /*prompt the user. if the user chooses to close existing runs, call close runs method*/
                System.out.println("You have unclosed test runs for the suite id " + suiteId +
                                   ". Proceeding with the update might affect your existing runs. " +
                                   "Please select an option to proceed");
                System.out.println("1.\tProceed with the update.\n" +
                                   "2.\tClose existing runs and then continue with the update.\n" +
                                   "3.\tExit the process.");
                Scanner scan = new Scanner(System.in);
                int choice = scan.nextInt();
                switch (choice) {
                    case 1:
                        return true;
                    case 2:
                        new CloseTestRun().closeActiveRuns(suiteId, tms.getProjectId());
                        return true;
                    default:
                        return false;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("An error occurred during suite update: " + e.getMessage());
        }
    }

    /**
     * Update the suite corresponding to the script
     *
     * @param testCases the {@link TmsTestCase} instances for the scenarios in the script file
     * @param scenarios the list of scenarios that the user wants to update specifically
     * @return TmsSuite object containing suite details
     */
    protected TmsSuite scriptSuiteUpdate(List<TmsTestCase> testCases, List<String> scenarios) {
        TmsSuite suite;
        Map<String, String> caseToScenario;
        if (CollectionUtils.isEmpty(scenarios)) {
            caseToScenario = update(testCases, suiteId, file.getScenarios(), SCRIPT);
        } else {
            caseToScenario = updateSpecifiedScenario(testCases, file.getScenarios(), scenarios);
        }
        suite = new TmsSuite(filepath, suiteId, caseToScenario);
        reorderSuiteAfterScriptUpdate(suite, testCases);
        System.out.println("Suite update completed");
        return suite;
    }

    /**
     * Update the suite corresponding to the plan
     *
     * @param testCasesToPlanStep the {@link TmsTestCase} instances for the scenarios associated with each plan step
     * @return TmsSuite object containing suite details
     */
    @NotNull
    protected TmsSuite planSuiteUpdate(LinkedHashMap<String, List<TmsTestCase>> testCasesToPlanStep) {
        TmsSuite suite;
        LinkedHashMap<String, List<String>> scenarioNamesToStep = new LinkedHashMap<>();
        Map<String, String> caseToScenario = new HashMap<>();
        int i = ADDR_PLAN_EXECUTION_START.getRowStartIndex() + 1; // row number for execution
        System.out.println("Initiating update for suiteId: " + file.getSuiteId());
        testCaseToStep = new HashMap<>();
        // looping over each plan step
        for (String row : testCasesToPlanStep.keySet()) {
            List<Scenario> existingTestCases = getTestCaseFromJsonPlan(file);
            List<TmsTestCase> testCases = testCasesToPlanStep.get(row);
            for (TmsTestCase testCase : testCases) {
                existingTestCases.removeIf(
                        t -> !testCase.getRow().equals(StringUtils.substringAfterLast(t.getTestCase(), "/")));
            }
            Map<String, String> updateResult = update(testCases, file.getSuiteId(), existingTestCases, PLAN);
            caseToScenario.putAll(updateResult);
            testCaseToStep.put(String.valueOf(i), updateResult);

            List<String> scenarioNames = getScenarioNames(testCases);
            scenarioNamesToStep.put(String.valueOf(i), scenarioNames);
            i++;
        }
        reorderSuiteAfterPlanUpdate(file.getSuiteId(), testCaseToStep, scenarioNamesToStep);

        suite = new TmsSuite(filepath, suiteId, caseToScenario);
        System.out.println("Suite update complete");
        return suite;
    }

    /**
     * Updates the suite associated with the suite id specified
     *
     * @param testCases         List of TestCases for the file currently under processing
     * @param suiteId           the suite id to update
     * @param existingTestCases the test cases already existing inside TMS
     * @param fileType          the type of file being processed, script or plan
     * @return Map of test case id to test case names
     */
    private Map<String, String> update(List<TmsTestCase> testCases,
                                              String suiteId,
                                              List<Scenario> existingTestCases, String fileType) {
        try {
            // delete any test cases not associated with any scenario in the sheet
            List<Scenario> deletedTestCases = deleteTestCase(testCases, existingTestCases, fileType);
            for (Scenario deleted : deletedTestCases) { existingTestCases.remove(deleted); }

            Map<String, String> caseIdToName = new HashMap<>();
            existingTestCases.forEach(scenario -> caseIdToName.put(scenario.getTestCase(), scenario.getTestCaseId()));

            // add any new scenarios not associated with any existing case as new test cases
            Map<String, String> addNewTestCaseResponses = addTestCase(testCases, suiteId, existingTestCases, fileType);

            // map of scenario names to test case names (both will be different in case of plan)
            caseIdToName.putAll(addNewTestCaseResponses);
            return caseIdToName;
        } catch (Exception e) {
            throw new RuntimeException("Test suite update failed: " + e.getMessage());
        }
    }

    /**
     * Compare the test cases read from the file against test cases read from project meta json and
     * Find if there are any test cases not mapped to a scenario and then delete them
     *
     * @param testCases               the current test cases, each test case representing a scenario
     * @param existingTestRailCaseIds the ids of test cases currently existing inside TMS
     * @param fileType                the type of file currently under processing
     * @return List of {@link Scenario} instances for the test cases which were deleted
     */
    private List<Scenario> deleteTestCase(List<TmsTestCase> testCases,
                                                 List<Scenario> existingTestRailCaseIds, String fileType) {
        // find test case in tms is not mapped to a scenario in the script and delete it

        List<String> deletedTestCaseIds = new ArrayList<>();
        List<Scenario> deletedTestCases = new ArrayList<>();
        List<String> scenariosInTestFile = new ArrayList<>();

        for (TmsTestCase testCase : testCases) {
            if (StringUtils.equals(fileType, SCRIPT)) {
                scenariosInTestFile.add(testCase.getName());
            } else {
                String scenarioName = testCase.getScriptName() + "/" + testCase.getName() + "/" + testCase.getRow();
                scenariosInTestFile.add(scenarioName);
            }
        }
        for (Scenario scenarioFromJson : existingTestRailCaseIds) {
            if (!scenariosInTestFile.contains(scenarioFromJson.getTestCase())) {
                deletedTestCases.add(scenarioFromJson);
                deletedTestCaseIds.add(scenarioFromJson.getTestCaseId());
            }
        }
        if (CollectionUtils.isNotEmpty(deletedTestCaseIds)) {
            tms.delete(deletedTestCaseIds);
            for (Scenario testCase : deletedTestCases) { existingTestRailCaseIds.remove(testCase); }
        }
        return deletedTestCases;
    }

    /**
     * Compare the test cases read from the file against test cases read from project meta json and
     * Find if there are any scenarios that are not mapped to a test case and then add them
     *
     * @param testCases             the current test cases, each test case representing a scenario
     * @param existingTestRailCases the test cases currently existing inside TMS
     * @param fileType              the type of file currently under processing
     * @return Map of the test case name to test case ids for tests which were added
     */
    private Map<String, String> addTestCase(List<TmsTestCase> testCases,
                                                   String suiteId,
                                                   List<Scenario> existingTestRailCases, String fileType) {
        // find any new scenario not mapped to a test case and add a new test case for it
        Map<String, String> addNewTestCaseResponses = new HashMap<>();
        List<String> existingTestCaseNames = new ArrayList<>();
        for (Scenario existingTestCase : existingTestRailCases) {
            existingTestCaseNames.add(existingTestCase.getTestCase());
        }
        List<TmsTestCase> testCasesToAdd = new ArrayList<>();
        for (TmsTestCase testCase : testCases) {
            String scenarioName = testCase.getName();
            if (StringUtils.equals(fileType, PLAN)) {
                scenarioName = testCase.getScriptName() + "/" + testCase.getName() + "/" +
                               testCase.getRow();
            }
            if (!existingTestCaseNames.contains(scenarioName)) {
                testCasesToAdd.add(testCase);
            }
        }

        if (CollectionUtils.isNotEmpty(testCasesToAdd)) {
            JSONArray sections = tms.getSections(suiteId);
            if (CollectionUtils.isEmpty(sections) || CollectionUtils.size(sections) != 1) {
                System.err.println("Exactly one section needs to be maintained inside a suite");
                System.exit(-1);
            }
            JSONObject addSectionResponse = (JSONObject) sections.get(0);
            sectionId               = addSectionResponse.get(ID).toString();
            addNewTestCaseResponses = tms.addCases(addSectionResponse, testCasesToAdd);
        }
        return addNewTestCaseResponses;
    }


    /**
     * Update the test cases associated with the specified scenario names with the updated data inside the scenarios
     *
     * @param testCases         List of possible test cases for each scenario inside the script file passed in as argument
     * @param existingTestCases List if test cases already existing inside TMS
     * @param scenariosToUpdate List of scenarios to update
     * @return Map of test case ids to test case names after updation
     */
    private Map<String, String> updateSpecifiedScenario(
            List<TmsTestCase> testCases, List<Scenario> existingTestCases,
            List<String> scenariosToUpdate) {

        List<TmsTestCase> casesToUpdate = new ArrayList<>();
        List<String> caseIdsToUpdate = new ArrayList<>();

        // retrieving testcases to update by matching present scenarios against user specified scenarios
        for (String scenarioToUpdate : scenariosToUpdate) {
            for (TmsTestCase testCase : testCases) {
                if (StringUtils.equals(testCase.getName(), scenarioToUpdate)) {
                    casesToUpdate.add(testCase);
                }
            }
        }
        // specified scenarios not found
        if (CollectionUtils.isEmpty(casesToUpdate)) {
            System.err.println(
                    "The scenarios specified do not exist in the given script. Check the script name and the scenarios specified");
            System.exit(-1);
        }
        // retrieving the current test case ids of specified scenarios
        for (Scenario existingTestCase : existingTestCases) {
            for (String scenarioToUpdate : scenariosToUpdate) {
                if (StringUtils.equals(existingTestCase.getName(), scenarioToUpdate)) {
                    caseIdsToUpdate.add(existingTestCase.getTestCaseId());
                }
            }
        }

        // scenarios specified should already be mapped to a test case for update to take place
        if (CollectionUtils.isEmpty(caseIdsToUpdate)) {
            System.err.println("the scenarios specified are not associate with any test cases");
            System.exit(-1);
        }

        Map<String, String> updateCaseResponses = new HashMap<>();
        Map<String, String> caseToScenario = new HashMap<>();

        // map of test case name to test case id
        existingTestCases.forEach(scenario -> caseToScenario.put(scenario.getTestCase(), scenario.getTestCaseId()));
        for (TmsTestCase testCase : casesToUpdate) {
            for (Scenario existingTestCase : existingTestCases) {
                if (StringUtils.equals(existingTestCase.getName(), testCase.getName())) {
                    Map<String, String> response =
                            tms.updateCase(existingTestCase.getTestCaseId(), testCase, false);
                    updateCaseResponses.putAll(response);
                }
            }
        }

        // putting the response back into the map to update the file,
        // if skipped, this will only update the specified scenario entries in the json
        caseToScenario.putAll(updateCaseResponses);
        return caseToScenario;
    }

    /**
     * Reorder the test cases in the suite according to the scenario order in the script file
     *
     * @param suite     suite details
     * @param testCases the {@link TmsTestCase} instances for the scenarios in the script file
     */
    private void reorderSuiteAfterScriptUpdate(TmsSuite suite,
                                                      List<TmsTestCase> testCases) {
        StringBuilder scenariosInOrder = new StringBuilder();
        for (TmsTestCase tmsTestCase : testCases) {
            String tmsTestCaseName = tmsTestCase.getName();
            String testCase = suite.getTestCases().get(tmsTestCaseName);
            scenariosInOrder.append(testCase).append(",");
        }
        scenariosInOrder.deleteCharAt(scenariosInOrder.length() - 1);
        tms.updateCaseOrder(suite.getId(), sectionId, scenariosInOrder.toString());
    }

    /**
     * Reorder the test cases in the suite according to the scenario order in the script file
     *
     * @param suiteId             the suite id
     * @param testCaseToPlanStep  the {@link TmsTestCase} instances for the scenarios in the plan file to each plan step
     * @param scenarioNamesToStep the scenario names specified in the subplan for each plan step
     */
    private void reorderSuiteAfterPlanUpdate(String suiteId,
                                                    Map<String, Map<String, String>> testCaseToPlanStep,
                                                    LinkedHashMap<String, List<String>> scenarioNamesToStep) {
        StringBuilder scenariosInOrder = new StringBuilder();
        for (String step : testCaseToPlanStep.keySet()) {
            Map<String, String> map = testCaseToPlanStep.get(step);
            List<String> scenarioNames = scenarioNamesToStep.get(step);
            for (String scenario : scenarioNames) {
                for (String testCase : map.keySet()) {
                    String testCaseName = StringUtils.substringBetween(testCase, "/");
                    if (StringUtils.equals(scenario, testCaseName)) {
                        scenariosInOrder.append(map.get(testCase)).append(",");
                    }
                }
            }
        }
        scenariosInOrder.deleteCharAt(scenariosInOrder.length() - 1);
        tms.updateCaseOrder(suiteId, sectionId, scenariosInOrder.toString());
    }
}
