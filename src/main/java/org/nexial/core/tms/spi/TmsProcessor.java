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
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.tms.model.*;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE;
import static org.nexial.core.tms.TmsConst.*;
import static org.nexial.core.tms.spi.ReadPlan.scriptToStep;
import static org.nexial.core.tms.spi.TmsMetaJson.*;
import static org.nexial.core.utils.ExecUtils.RUNTIME_ARGS;

/**
 * Perform processing on script excel, plan excel and project.tms.json to retrieve testcases meta data
 */
public class TmsProcessor {
    public static String projectId;

    /**
     * Receive the arguments passed by the user and determines what operations to perform based on the input. If suite
     * for the file pointed to by the file path is existing, update the suite, otherwise create a new suite
     *
     * @param filepath the path of the nexial test file
     * @param subplan  the subplan name in case the input path points to plan
     */
    public void importToTms(String filepath, String subplan) throws TmsException {
        TmsTestFile tmsFile = getJsonEntryForFile(filepath, subplan);
        if (!FileUtil.isFileReadWritable(filepath, MIN_EXCEL_FILE_SIZE)) {
            throw new TmsException("File '" + filepath + "' is not readable or writable");
        }
        projectId = tmsFile.getProjectId();
        TestFile file = tmsFile.getFile();
        TMSOperation tms = TmsFactory.INSTANCE.getTmsInstance(projectId);
        TestFile updatedTestFile = StringUtils.isEmpty(subplan) ? processScript(filepath, file, tms) :
                                           processPlan(filepath, file, subplan, tms);
        updateMeta(filepath, updatedTestFile);
    }

    /**
     * Import execution results to a respective test script/plan suite if present
     *
     * @param summary {@link ExecutionSummary} of the nexial script/plan execution
     */
    public void importResultsToTms(ExecutionSummary summary) throws TmsException {

        Map<String, String> referenceData = summary.getReferenceData();
        if(referenceData == null || !referenceData.containsKey(RUNTIME_ARGS)) {
            throw new TmsException("Unable to find runtime args. Exiting...");
        }
        List<String> runtimeArgs = TextUtils.toList(referenceData.get(RUNTIME_ARGS), " ", true);

        if(runtimeArgs.contains(SCRIPT_ARG)) {
            String filepath = runtimeArgs.get(runtimeArgs.indexOf(SCRIPT_ARG) + 1);
            uploadResults(filepath, "", summary);
        } else if(runtimeArgs.contains(PLAN_ARG)){
            String filepath = runtimeArgs.get(runtimeArgs.indexOf(PLAN_ARG) + 1);
            for (String path : TextUtils.toList(filepath, ",", true)) {
                List<String> subplans = summary.getNestedExecutions().stream()
                        .filter(exec -> exec.getPlanFile() != null &&
                                                exec.getPlanFile().equals(path))
                        .map(ExecutionSummary::getPlanName).distinct()
                        .collect(Collectors.toList());
                if (CollectionUtils.isEmpty(subplans)) {
                    throw new TmsException("No subplan results found. Exiting...");
                }
                for (String subplan : subplans) { uploadResults(path, subplan, summary); }
            }
        } else {
            throw new TmsException("Unable to find runtime args to upload result. Exiting....");
        }
    }


    /**
     * Return the {@link Scenario} instances for each test case from the project.tms.json for the testFile specified
     *
     * @param testFile the json entry for the input nexial testFile path
     * @return List of scenarios from the project.tms.json testFile
     */
    static List<Scenario> getTestCaseFromJsonPlan(TestFile testFile) {
        List<Scenario> scenarios = new ArrayList<>();
        if (testFile == null || testFile.getPlanSteps() == null) { return scenarios; }
        testFile.getPlanSteps().forEach(file -> scenarios.addAll(Objects.requireNonNull(file.getScenarios())));
        return scenarios;
    }

    /**
     * Process a nexial script testFile and create or update a suite depending upon whether a suite corresponding
     * to the script is existing or not
     * @param testPath the path of the script testFile
     * @param testFile     the {@link TestFile} instance representing the entry for the script testFile in the project.tms.json
     * @param tms instance of {@link TMSOperation} user want to perform
     * @return the updated TestFile instance for the script testFile
     */
    private TestFile processScript(String testPath, TestFile testFile, TMSOperation tms) throws TmsException {
        TmsSuite suite;
        boolean isUpdated;
        List<TmsTestCase> testCases = ReadScript.loadScript(testPath);
        String suiteName = FilenameUtils.removeExtension(new File(testPath).getName());
        if (testFile == null) {
            SuiteUpload upload = new SuiteUpload(suiteName, testPath, tms);
            ConsoleUtils.log("Initiated suite upload.");
            suite = upload.uploadScript(testCases);
            isUpdated = suite != null;
            if(!isUpdated) {
                throw new TmsException("Unable to upload the suite. Exiting...");
            }
            ConsoleUtils.log("Completed suite upload with id: " + suite.getId());
        } else {
            String suiteId = testFile.getSuiteId();
            SuiteUpdate update = new SuiteUpdate(suiteName, testFile, testPath, tms);
            ConsoleUtils.log("Initiated suite update for suite: " + suiteId);
            if (!update.shouldUpdateSuite()) { System.exit(0); }
            suite = update.scriptSuiteUpdate(testCases);
            isUpdated = update.isUpdated() && suite != null;
            ConsoleUtils.log("Completed suite update for suite: " + suiteId);
        }
        if(isUpdated) UpdateTestFiles.updateScriptFile(testPath, suite, true);
        return getScriptJson(testPath, suite, testCases, testFile);
    }

    /**
     * Retrieve a new json entry from the project.tms.json testFile for the script
     *
     * @param filepath     path of the script testFile
     * @param suite        existing suite details
     * @param tmsTestCases the {@link TmsTestCase} instances for the scenarios in the script testFile
     * @param testFile         the existing json entry in the project.tms.json corresponding to the Script testFile
     * @return updated json entry for the script
     */
    private TestFile getScriptJson(String filepath, TmsSuite suite, List<TmsTestCase> tmsTestCases, TestFile testFile) {
        List<Scenario> scenarios = new ArrayList<>();
        Map<String, String> cache = new LinkedHashMap<>();
        for (TmsTestCase tmsTestCase : tmsTestCases) {
            String testCaseName = tmsTestCase.getName();
            String testCaseId = Objects.requireNonNull(suite.getTestCases()).get(testCaseName);
            if (testCaseId == null) { continue; }
            scenarios.add(new Scenario(testCaseName, testCaseName, testCaseId));
            cache.put(testCaseName, tmsTestCase.getCache());
        }
        if (testFile == null) {
            testFile = new TestFile(getRelativePath(filepath), SCRIPT, suite.getId(),
                                null, null, scenarios, suite.getSuiteUrl(), cache);
        } else {
            testFile.setScenarios(scenarios);
            testFile.setCache(cache);
        }
        return testFile;

    }

    /**
     * Process a nexial script testFile and create of update a suite depending upon whether a suite corresponding
     * to the script is existing or not
     * @param testPath the path of the plan testFile
     * @param testFile     the existing json entry in the project.tms.json corresponding to the plan testFile
     * @param subplan  the subplan name
     * @param tms instance of {@link TMSOperation} user want to perform
     * @return the updated TestFile instance for the script testFile
     */
    private TestFile processPlan(String testPath, TestFile testFile, String subplan, TMSOperation tms)
            throws TmsException {
        Map<Integer, Map<String, String>> testCaseMap;
        TmsSuite suite;
        boolean isUpdated;

        LinkedHashMap<Integer, List<TmsTestCase>> testCasesToPlanStep = ReadPlan.loadPlan(testPath, subplan);
        String suiteName = FilenameUtils.removeExtension(new File(testPath).getName()) + "/" + subplan;

        if (testFile == null) {
           ConsoleUtils.log("Initiated suite upload");
            SuiteUpload upload = new SuiteUpload(suiteName, testPath, tms);
            suite = upload.uploadPlan(testCasesToPlanStep);
            testCaseMap = upload.getTestCaseToStep();
            isUpdated = true;
            ConsoleUtils.log("Suite upload completed for suite: " + suite.getId());
        } else {
           ConsoleUtils.log("Initiating suite update for suite: " + testFile.getSuiteId());
            SuiteUpdate update = new SuiteUpdate(suiteName, testFile, testPath, tms);
            if (!update.shouldUpdateSuite()) {
                throw new TmsException("Unable to upload the suite. Exiting...");
            }
            suite = update.planSuiteUpdate(testCasesToPlanStep);
            testCaseMap = update.getTestCaseToStep();
            isUpdated = update.isUpdated();
            ConsoleUtils.log("Suite update completed for suite: " + suite.getId());
        }
        // update the plan testFile with test case ids
        if(isUpdated) UpdateTestFiles.updatePlanFile(testPath, suite, subplan);
        ConsoleUtils.log("Test file update completed.");
        return getPlanJson(testPath, suite, getScriptFiles(testCaseMap), testFile,
                           subplan, retrieveCache(testCasesToPlanStep));
    }

    /**
     * Retrieve a new json entry from the project.tms.json testFile for the plan testFile
     *
     * @param filepath    path of the script testFile
     * @param suite       existing suite details
     * @param scriptFiles the List of {@link TestFile} instances for the scenarios in the subplan testFile to each plan step
     * @param testFile    the existing json entry in the project.tms.json corresponding to the plan testFile and subplan
     * @param subplan     the subplan name
     * @param cache       {@link Map} of scenario to the cache
     * @return updated json entry for the script
     */
    private static TestFile getPlanJson(String filepath, TmsSuite suite, List<TestFile> scriptFiles,
                                        TestFile testFile, String subplan, Map<String, String> cache) {

        if (testFile == null) {
            testFile = new TestFile(getRelativePath(filepath), PLAN, suite.getId(),
                    subplan, scriptFiles, null, suite.getSuiteUrl(), cache);
        } else {
            testFile.setPlanSteps(scriptFiles);
            testFile.setCache(cache);
        }
        return testFile;
    }

    /**
     * Retrieve list of script files as a {@link TestFile} from subplan of plan file
     *
     * @param testCaseToStep map of plan step to testcases with testcase name and testcase id
     * @return {@link List} of {@link TestFile} with script data
     */
    @NotNull
    private static List<TestFile> getScriptFiles(Map<Integer, Map<String, String>> testCaseToStep) {
        List<TestFile> scriptFiles = new ArrayList<>();
        testCaseToStep.forEach((step, testCases) -> {
            TestFile script = new TestFile();
            script.setPath(getRelativePath(scriptToStep.get(step)));
            script.setFileType(SCRIPT);
            script.setStepId(String.valueOf(step));
            List<Scenario> scenarios = new ArrayList<>();

            testCases.forEach((testCase, testCaseId) -> scenarios.add(
                new Scenario(testCase, StringUtils.substringBetween(testCase, "/"), testCaseId)));
            script.setScenarios(scenarios);
            scriptFiles.add(script);
        });
        return scriptFiles;
    }

    /**
     * Upload execution result to already imported testcases to provided tms tool
     *
     * @param testPath path of script file/plan file for which result to be uploaded
     * @param subplan of the plan file only, empty in case of script file
     * @param summary {@link ExecutionSummary} from the execution output file
     */
    private void uploadResults(String testPath, String subplan, ExecutionSummary summary) throws TmsException {
        if (!FileUtil.isFileReadWritable(testPath, MIN_EXCEL_FILE_SIZE)) {
            throw new TmsException("File " + testPath + " is not readable or writable");
        }
        TmsTestFile tmsFile = getJsonEntryForFile(testPath, subplan);
        projectId = tmsFile.getProjectId();
        TestFile file = tmsFile.getFile();
        if (file == null) {
            throw new TmsException("Script is not imported to tms. So can't import Test results");
        }
        String suiteName = FilenameUtils.removeExtension(new File(testPath).getName());
        if (StringUtils.isNotEmpty(subplan)) { suiteName += "/" + subplan; }
        String suiteId = file.getSuiteId();
        TMSOperation tms = TmsFactory.INSTANCE.getTmsInstance(projectId);
        SuiteUpdate update = new SuiteUpdate(suiteName, file, testPath, tms);
        ConsoleUtils.log("Initiating Test Result upload for suiteId: " + suiteId);
        update.addResults(summary, StringUtils.isEmpty(subplan));
        ConsoleUtils.log("Test result upload completed for suiteId: " + suiteId);
    }
}
