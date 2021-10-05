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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.tms.model.Scenario;
import org.nexial.core.tms.model.TestFile;
import org.nexial.core.tms.model.TmsSuite;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.tms.model.TmsTestFile;

import static org.nexial.core.tms.TmsConst.PLAN;
import static org.nexial.core.tms.TmsConst.SCRIPT;
import static org.nexial.core.tms.spi.ReadPlan.scriptToStep;
import static org.nexial.core.tms.spi.TmsMetaJson.*;

public class TmsProcessor {
    private String projectId;
    /**
     * Receive the arguments passed by the user and determines what operations to perform based on the input. If suite
     * for the file pointed to by the file path is existing, update the suite, otherwise create a new suite
     *
     * @param filepath the path of the nexial test file
     * @param subplan the subplan name in case the input path points to plan
     * @param scenarios list of scenarios that the user wants to update in the suite
     */
    public void importToTms(String filepath, String subplan, List<String> scenarios) {
        TmsTestFile tmsFile = getJsonEntryForFile(filepath, subplan);
        projectId = tmsFile.getProjectId();
        TestFile file = tmsFile.getFile();
        TestFile updatedTestFile;
        if (StringUtils.isEmpty(subplan)) {
            updatedTestFile = processScript(filepath, scenarios, file);
        } else {
            updatedTestFile = processPlan(filepath, file, subplan);
        }
        updateMeta(filepath, updatedTestFile);
    }

    /**
     * Process a nexial script file and create of update a suite depending upon whether a suite corresponding to the script
     * is existing or not
     *
     * @param filepath the path of the script file
     * @param scenarios {@link List} of scenario names that the user wants to update specifically
     * @param file the {@link TestFile} instance representing the entry for the script file in the project.tms.json
     * @return the updated TestFile instance for the script file
     */
     private TestFile processScript(String filepath, List<String> scenarios, TestFile file) {
        TmsSuite suite = null;
        List<TmsTestCase> testCases = ReadScript.loadScript(filepath);
        if (file == null) {
            System.out.println("Initiating suite creation");
            SuiteUpload upload = new SuiteUpload(projectId);
            suite = upload.uploadScript(testCases, FilenameUtils.removeExtension(new File(filepath).getName()));
            System.out.println("Suite creation complete. New suite id is " + suite.getId());

        } else {
            String suiteId = file.getSuiteId();
            SuiteUpdate update = new SuiteUpdate(filepath, file, projectId);
            System.out.println("Initiating suite update for suiteId: " + suiteId);
            if (update.shouldUpdateSuite()) {
                suite = update.scriptSuiteUpdate(testCases, scenarios);
            } else {
                System.exit(0);
            }
        }
        System.out.println("Updating test file: " + filepath);
        UpdateTestFiles.updateScriptFile(filepath, suite, true);
        return getScriptJson(filepath, suite, testCases, file);
    }


    /**
     * Retrieve a new json entry from the project.tms.json file for the script
     *
     * @param filepath path of the script file
     * @param suite existing suite details
     * @param tmsTestCases the {@link TmsTestCase} instances for the scenarios in the script file
     * @param file the existing json entry in the project.tms.json corresponding to the Script file
     * @return updated json entry for the script
     */
    private TestFile getScriptJson(String filepath, TmsSuite suite,
                                          List<TmsTestCase> tmsTestCases, TestFile file) {
        List<Scenario> scenarios = new ArrayList<>();
        for (TmsTestCase tmsTestCase : tmsTestCases) {
            String scenarioName = tmsTestCase.getName();
            String testCase = suite.getTestCases().get(scenarioName);
            scenarios.add(new Scenario(scenarioName, scenarioName, testCase));
        }
        if (file == null) {
            return new TestFile(getRelativePath(filepath), SCRIPT, suite.getId(), null, null, scenarios,
                                suite.getSuiteUrl(), null);
        }
        file.setScenarios(scenarios);
        return file;
    }

    /**
     * Process a nexial script file and create of update a suite depending upon whether a suite corresponding to the script
     * is existing or not
     *
     * @param testPath the path of the plan file
     * @param file the existing json entry in the project.tms.json corresponding to the plan file
     * @param subplan the subplan name
     * @return the updated TestFile instance for the script file
     */
    private TestFile processPlan(String testPath, TestFile file, String subplan) {
        LinkedHashMap<String, List<TmsTestCase>> testCasesToPlanStep = ReadPlan.loadPlan(testPath, subplan);
        Map<String, Map<String, String>> testCaseMap = null;
        TmsSuite suite = null;
        if (ObjectUtils.isEmpty(file)) {
            System.out.println("Initiating suite upload");
            SuiteUpload upload = new SuiteUpload(testPath, subplan, projectId);
            suite = upload.uploadPlan(testCasesToPlanStep);
            System.out.println("Suite uploading complete");
            testCaseMap = upload.getTestCaseToStep();
        } else {
            SuiteUpdate update = new SuiteUpdate(testPath, file, projectId);
            if (update.shouldUpdateSuite()) {
                suite = update.planSuiteUpdate(testCasesToPlanStep);
                testCaseMap = update.getTestCaseToStep();
            }
            else {
                System.exit(-1);
            }
        }
        // update the plan file with test case ids
        System.out.println("Updating test file: " + testPath);
        UpdateTestFiles.updatePlanFile(testPath, suite, subplan);
        System.out.println("Test File update complete");
        return getPlanJson(testPath, suite, testCaseMap, file, subplan);
    }

    /**
     * Get the scenario names based on the {@link TmsTestCase} instances retrieved from the plan file
     *
     * @param testCases the {@link TmsTestCase} instances retrieved from the plan file
     * @return the scenario names
     */
    static List<String> getScenarioNames(List<TmsTestCase> testCases) {
        List<String> scenarioNames = new ArrayList<>();
        for (TmsTestCase testCase : testCases) {
            scenarioNames.add(testCase.getName());
        }
        return scenarioNames;
    }

    /**
     * Retrieve a new json entry from the project.tms.json file for the plan file
     *
     * @param filepath path of the script file
     * @param suite existing suite details
     * @param testCaseToStep the {@link TmsTestCase} instances for the scenarios in the subplan file to each plan step
     * @param file the existing json entry in the project.tms.json corresponding to the plan file and subplan
     * @param subplan the subplan name
     * @return updated json entry for the script
     */
    private static TestFile getPlanJson(String filepath, TmsSuite suite,
                                        Map<String, Map<String, String>> testCaseToStep,
                                        TestFile file, String subplan) {
        List<TestFile> scriptFiles = new ArrayList<>();
        for (String step : testCaseToStep.keySet()) {
            TestFile script = new TestFile();
            script.setPath(getRelativePath(scriptToStep.get(step)));
            script.setFileType(SCRIPT);
            script.setStepId(step);
            Map<String, String> map = testCaseToStep.get(step);
            List<Scenario> scenarios = new ArrayList<>();
            for (String testCase : map.keySet()) {
                scenarios.add(new Scenario(testCase, StringUtils.substringBetween(testCase, "/"), map.get(testCase)));
            }
            script.setScenarios(scenarios);
            scriptFiles.add(script);
        }
        if (ObjectUtils.isNotEmpty(file)) {
            file.setPlanSteps(scriptFiles);
            return file;
        }
        return new TestFile(getRelativePath(filepath), PLAN, suite.getId(), subplan, scriptFiles, null,
                            suite.getSuiteUrl(), null);
    }

    /**
     * Return the {@link Scenario} instances for each test case from the project.tms.json for the file specified
     * @param file the json entry for the input nexial file path
     * @return List of scenarios from the project.tms.json file
     */
    static List<Scenario> getTestCaseFromJsonPlan(TestFile file) {
        List<Scenario> scenarios = new ArrayList<>();
        if (ObjectUtils.isEmpty(file)) { return scenarios; }
        for (TestFile script : file.getPlanSteps()) {
            scenarios.addAll(script.getScenarios());
        }
        return scenarios;
    }
}
