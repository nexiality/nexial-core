/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.tms.spi;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.tms.model.*;
import org.nexial.core.tms.tools.CloseTestRun;
import org.nexial.core.tms.tools.TmsImporter;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.tms.spi.TmsProcessor.getTestCaseFromJsonPlan;

/**
 * Perform update operations on an already existing suite
 */
public class SuiteUpdate {
    private boolean isUpdated = false;
    private final String suiteName;
    private final TestFile file;
    private final String testPath;
    private final String suiteId;
    private String sectionId;
    private Map<Integer, Map<String, String>> testCaseToStep;
    private final TMSOperation tms;

    public SuiteUpdate(String suiteName, TestFile file, String testPath, TMSOperation tms) {
        this.suiteName = suiteName;
        this.file = file;
        this.testPath = testPath;
        this.tms = tms;
        suiteId = file.getSuiteId();
    }

    public Map<Integer, Map<String, String>> getTestCaseToStep() { return testCaseToStep; }

    public boolean isUpdated() { return isUpdated; }

    /**
     * Checks if suite id specified has any active test runs associated with it and prompts the user with options as to
     * close active runs if any and update the suite or not.
     *
     * @return true if the user opts for an update, otherwise return false
     */
    protected boolean shouldUpdateSuite() {
        try {
            List<TestRun> existingRuns = tms.getExistingActiveRuns(suiteId);
            if (existingRuns.size() == 0) { return true; }
            /* prompt the user if the user chooses to close existing runs, call close runs method */
            ConsoleUtils.log("You have unclosed test runs for the suite id " + suiteId +
                               ". Proceeding with the update might affect your existing runs. " +
                               "Please select an option to proceed");
            int choice;
            if (TmsImporter.INSTANCE.getClosePreviousRun()) {
                choice = 2;
                System.out.println("Closing test runs for " + suiteId);
            } else {
                System.out.println("1.\tProceed with the update.\n" +
                                   "2.\tClose existing runs and then continue with the update.\n" +
                                   "3.\tExit the process.");
                System.out.println("Input your choice >> ");
                Scanner scan = new Scanner(System.in);
                choice = scan.nextInt();
            }

            switch (choice) {
                case 1:
                    ConsoleUtils.log("Continue updating testcases without closing corresponding test runs.");
                    return true;
                case 2:
                    List<String> collect = existingRuns.stream().map(TestRun::getId)
                            .map(Object::toString).collect(Collectors.toList());
                    new CloseTestRun().closeActiveRuns(tms, collect);
                    ConsoleUtils.log("Closed corresponding test runs and continue updating testcases.");
                    return true;
                default:
                    ConsoleUtils.log("Exiting updating operation on the test suite.");
                    return false;
            }

        } catch (NumberFormatException e) {
            ConsoleUtils.error("Only choice input supported is between numbers 1 and 3.");
            return false;
        } catch (Exception e) {
            ConsoleUtils.error("An error occurred during suite update: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update the suite corresponding to the script.
     *
     * @param testCases the {@link TmsTestCase} instances for the scenarios in the script file
     * @return TmsSuite object containing suite details
     */
    protected TmsSuite scriptSuiteUpdate(List<TmsTestCase> testCases) throws TmsException {
        // firstly update description of suite
        TmsSuite tmsSuite = tms.updateSuite(suiteId, testPath);
        ConsoleUtils.log("Updated test suite description for suite: " + suiteId);
        List<Scenario> existingScenarios = file.getScenarios();
        Map<String, String> caseToScenario = updateExistingScenarios(testCases, existingScenarios);

        // check for new or deleted test cases only in case if scenarios not mentioned in command
        Map<String, String> caseToScenario1 = addOrDeleteTestCase(testCases, existingScenarios);
        caseToScenario.putAll(caseToScenario1);

        tmsSuite.setTestCases(caseToScenario);
        reorderSuiteAfterScriptUpdate(tmsSuite, testCases);
        return tmsSuite;
    }

    /**
     * Update the suite corresponding to the plan.
     *
     * @param testCasesToPlanStep the {@link TmsTestCase} instances for the scenarios associated with each plan step
     * @return TmsSuite object containing suite details
     */
    @NotNull
    protected TmsSuite planSuiteUpdate(LinkedHashMap<Integer, List<TmsTestCase>> testCasesToPlanStep)
            throws TmsException {
        TmsSuite tmsSuite = tms.updateSuite(suiteId, testPath);
        List<Scenario> existingTestCases1 = getTestCaseFromJsonPlan(file);

        LinkedHashMap<Integer, List<String>> scenarioNamesToStep = new LinkedHashMap<>();
        testCaseToStep = new LinkedHashMap<>();
        // looping over each plan step
        for (Map.Entry<Integer, List<TmsTestCase>> entry : testCasesToPlanStep.entrySet()) {
            Integer row = entry.getKey();
            List<TmsTestCase> testCases = entry.getValue();
            List<Scenario> existingTestCases = new ArrayList<>(existingTestCases1);
            for (TmsTestCase testCase : testCases) {
                existingTestCases.removeIf(t -> testCase.getRow() !=
                                                        Integer.parseInt(StringUtils.substringAfterLast(t.getTestCase(), "/")));
            }

            List<TmsTestCase> testCases1 = tobeUpdatedTestCases(testCases, existingTestCases);
            // do not update scripts added just now
            Map<String, String> updatedTestCases = updateExistingScenarios(testCases1, existingTestCases);
            Map<String, String> caseToScenario = new LinkedHashMap<>(updatedTestCases);

            // update all scenarios as well
            Map<String, String> addedDeletedTestcases = addOrDeleteTestCase(testCases, existingTestCases);
            caseToScenario.putAll(addedDeletedTestcases);

            testCaseToStep.put(row, caseToScenario);
            List<String> scenarioNames = testCases.stream().map(TmsTestCase::getName).collect(Collectors.toList());
            scenarioNamesToStep.put(row, scenarioNames);
        }

        Map<String, String> caseToScenario = reorderSuiteAfterPlanUpdate(scenarioNamesToStep);
        tmsSuite.setTestCases(caseToScenario);
        return tmsSuite;
    }

    /**
     * Add results to already imported testcases
     *
     * @param summary the {@link ExecutionSummary} of execution from detailed json file
     * @param isScript {@link Boolean} to check for script or plan execution
     */
    protected void addResults(ExecutionSummary summary, boolean isScript) throws TmsException {
        if(file == null || StringUtils.isEmpty(file.getSuiteUrl())) {
            throw new TmsException("Unable to update the result as test script/plan import data is " +
                                           "not available OR suite url is empty. Exiting...");
        }
        tms.addResults(summary, file);
    }

    /**
     * Update the test cases associated with the specified scenario names with the updated data inside the scenarios.
     *
     * @param testCases         List of possible test cases for each scenario inside the script file passed in as argument
     * @param existingTestCases List if test cases already existing inside TMS
     * @return Map of test case ids to test case names after updating
     */
    private Map<String, String> updateExistingScenarios(List<TmsTestCase> testCases,
                                                        List<Scenario> existingTestCases) throws TmsException {
        Map<String, String> cache = file.getCache();
        if (cache == null) { cache = new LinkedHashMap<>(); }
        // retrieving testcases to update by matching present scenarios against user specified scenarios
        Map<String, String> updateCaseResponses = new HashMap<>();
        // specified scenarios not found
        if (CollectionUtils.isEmpty(testCases)) {
            ConsoleUtils.error("There are no scenarios specified exist in the given script.");
            return updateCaseResponses;
        }

        for (TmsTestCase testCase : testCases) {
            for (Scenario existingTests : existingTestCases) {
                String testCase1 = existingTests.getTestCase();
                if (StringUtils.equals(testCase1, testCase.getTestCaseName())) {
                    if (StringUtils.equals(testCase.getCache(), cache.get(
                            StringUtils.substringBeforeLast(testCase1, "/")))) {
                        updateCaseResponses.put(testCase1, existingTests.getTestCaseId());
                    } else {
                        Map<String, String> response = tms.updateCase(existingTests.getTestCaseId(), testCase, false);
                        updateCaseResponses.putAll(response);
                    }
                    break;
                }
            }
        }

        // putting the response back into the map to update the file,
        return updateCaseResponses;
    }

    /**
     * Add or delete testcase from the suite associated with the suite id specified.
     *
     * @param testCases         List of TestCases for the file currently under processing
     * @param existingTestCases the test cases already existing inside TMS
     * @return Map of test case id to test case names
     */
    private Map<String, String> addOrDeleteTestCase(List<TmsTestCase> testCases, List<Scenario> existingTestCases)
            throws TmsException {
        // delete any test cases not associated with any scenario in the sheet
        deleteTestCase(testCases, existingTestCases);
        // add any new scenarios not associated with any existing case as new test cases
        return addTestCase(testCases, existingTestCases);
    }

    /**
     * Compare the test cases read from the file against test cases read from project meta json and
     * find if there are any test cases not mapped to a scenario and then delete them.
     *
     * @param testCases         the current test cases, each test case representing a scenario
     * @param existingTestCases the ids of test cases currently existing inside TMS
     */
    private void deleteTestCase(List<TmsTestCase> testCases, List<Scenario> existingTestCases) throws TmsException {
        // find test case in tms is not mapped to a scenario in the script and delete it
        List<String> scenariosInTestFile = testCases.stream()
                .map(TmsTestCase::getTestCaseName).collect(Collectors.toList());

        List<Scenario> deletedTestCases = existingTestCases
                .stream().filter(scenario -> !scenariosInTestFile.contains(scenario.getTestCase()))
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(deletedTestCases)) {
            for (Scenario testCase : deletedTestCases) {
                tms.delete(suiteId, testCase.getTestCaseId());
                existingTestCases.removeIf(tc -> StringUtils.equals(tc.getTestCaseId(), testCase.getTestCaseId()));
            }
        }
    }

    /**
     * Compare the test cases read from the file against test cases read from project meta json and
     * find if there are any scenarios that are not mapped to a test case and then add them.
     *
     * @param testCases        the current test cases, each test case representing a scenario
     * @param existingTmsCases the test cases currently existing inside TMS
     * @return Map of the test case name to test case ids for tests which were added
     */
    private Map<String, String> addTestCase(List<TmsTestCase> testCases,
                                            List<Scenario> existingTmsCases) throws TmsException{
        // find any new scenario not mapped to a test case and add a new test case for it
        Map<String, String> newTestcaseResponse = new HashMap<>();
        List<String> testcaseNames = existingTmsCases.stream().map(Scenario::getTestCase).collect(Collectors.toList());

        List<TmsTestCase> testCasesToAdd = testCases.stream()
                                                    .filter(tc -> !testcaseNames.contains(tc.getTestCaseName()))
                                                    .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(testCasesToAdd)) {
            sectionId  = tms.getSectionId(suiteId);
            newTestcaseResponse = tms.addCases(sectionId, testCasesToAdd);
            isUpdated = MapUtils.isNotEmpty(newTestcaseResponse);
        }
        return newTestcaseResponse;
    }

    /**
     * Reorder the test cases in the suite according to the scenario order in the script file.
     *
     * @param suite     Test suite details
     * @param testCases the {@link TmsTestCase} instances for the scenarios in the script file
     */
    private void reorderSuiteAfterScriptUpdate(TmsSuite suite, List<TmsTestCase> testCases) throws TmsException {
        List<TestcaseOrder> order = new ArrayList<>();
        int sequenceNum = 0;
        // Don't do every time
        for (TmsTestCase tmsTestCase : testCases) {
            String tmsTestCaseName = tmsTestCase.getName();
            String testCaseId = Objects.requireNonNull(suite.getTestCases()).get(tmsTestCaseName);
            if (testCaseId == null) { continue; }
            order.add(new TestcaseOrder(testCaseId, tmsTestCaseName, sequenceNum, "testCase"));
            sequenceNum++;
        }
        tms.updateCaseOrder(suite.getId(), sectionId, order);
    }

    /**
     * Reorder the test cases in the suite for a plan file according to the plan step and scenario order
     *
     * @param scenarioNamesToStep the scenario names specified in the subplan for each plan step
     * @return {@link Map} of testcase name to the testcase id
     */
    private Map<String, String> reorderSuiteAfterPlanUpdate(LinkedHashMap<Integer, List<String>> scenarioNamesToStep)
            throws TmsException {

        List<TestcaseOrder> order = new ArrayList<>();
        Map<String, String> caseToScenario = new LinkedHashMap<>();
        Map<Integer, Map<String, String>> testCaseToPlanStep1 = new LinkedHashMap<>();

        int sequenceNum = 0;
        for (int row : scenarioNamesToStep.keySet()) {
            List<String> scenarioNames = scenarioNamesToStep.get(row);
            Map<String, String> map = testCaseToStep.get(row);
            for (String scenario : scenarioNames) {
                for (String testCase : map.keySet()) {
                    String testCaseName = StringUtils.substringBetween(testCase, "/");
                    if (StringUtils.equals(scenario, testCaseName)) {
                        String testCaseId = map.get(testCase);
                        if (testCaseId == null) { continue; }
                        order.add(new TestcaseOrder(testCaseId, testCaseName, sequenceNum, "testCase"));
                        // reorder test scenarios to update in json file
                        caseToScenario.put(testCase, testCaseId);
                        updateTestPlan(testCaseToPlanStep1, row, testCase, testCaseId);
                        sequenceNum++;
                    }
                }
            }
        }
        testCaseToStep = new LinkedHashMap<>(testCaseToPlanStep1);
        tms.updateCaseOrder(suiteId, sectionId, order);
        return caseToScenario;
    }


    /**
     *  Update test plan testcase ids after updating scenarios from file
     *
     * @param testCaseToStep {@link Map} of plan step to test case
     * @param row plan step row number
     * @param testCase test case name to be updated
     * @param testId test case id to be updated
     */
    private void updateTestPlan(Map<Integer, Map<String, String>> testCaseToStep,
                                int row, String testCase, String testId) {
        if (testCaseToStep.containsKey(row)) {
            Map<String, String> map = testCaseToStep.get(row);
            map.put(testCase, testId);
        } else {
            Map<String, String> map = new LinkedHashMap<>();
            map.put(testCase, testId);
            testCaseToStep.put(row, map);
        }
    }

    /**
     * Retrieve list of the testcase to be updated by comparing with existing testcases imported to tms tool
     *
     * @param testCases {@link List} of the testcases to be updated
     * @param existingTestCases {@link List} of the existing testcases imported till now
     * @return {@link List} of {@link TmsTestCase} to be updated
     */
    private List<TmsTestCase> tobeUpdatedTestCases(List<TmsTestCase> testCases, List<Scenario> existingTestCases) {
        List<TmsTestCase> cases = new ArrayList<>();
        existingTestCases.forEach(scenario ->
                                      testCases.stream()
                                               .filter(tc -> tc.getTestCaseName().equals(scenario.getTestCase()))
                                               .forEach(cases::add));
        return cases;
    }
}
