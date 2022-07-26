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
package org.nexial.core.tms.spi

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.TextUtils
import org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.tms.TmsConst.PLAN
import org.nexial.core.tms.TmsConst.PLAN_ARG
import org.nexial.core.tms.TmsConst.SCRIPT
import org.nexial.core.tms.TmsConst.SCRIPT_ARG
import org.nexial.core.tms.model.Scenario
import org.nexial.core.tms.model.TestFile
import org.nexial.core.tms.model.TmsSuite
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.tms.spi.ReadPlan.scriptToStep
import org.nexial.core.tms.spi.TmsFactory.getTmsInstance
import org.nexial.core.tms.spi.TmsMetaJson.getJsonEntryForFile
import org.nexial.core.tms.spi.TmsMetaJson.getRelativePath
import org.nexial.core.tms.spi.TmsMetaJson.updateMeta
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils.RUNTIME_ARGS
import java.io.File

/**
 * Perform processing on script excel, plan excel and project.tms.json to retrieve testcases meta data
 */
object TmsProcessor {
    lateinit var projectId: String

    /**
     * Receive the arguments passed by the user and determines what operations to perform based on the input. If suite
     * for the file pointed to by the file path is existing, update the suite, otherwise create a new suite
     *
     * @param filepath the path of the nexial test file
     * @param subplan  the subplan name in case the input path points to plan
     */
    @Throws(TmsException::class)
    fun importToTms(filepath: String, subplan: String) {
        val tmsFile = getJsonEntryForFile(filepath, subplan)
        if (!FileUtil.isFileReadWritable(filepath, MIN_EXCEL_FILE_SIZE)) {
            throw TmsException("File '$filepath' is not readable or writable")
        }
        projectId = tmsFile.projectId
        val file = tmsFile.file
        val tms = getTmsInstance(projectId)
        val updatedTestFile = if (StringUtils.isEmpty(subplan)) processScript(filepath, file, tms) else
            processPlan(filepath, file, subplan, tms)
        updateMeta(filepath, updatedTestFile)
    }

    /**
     * Import execution results to a respective test script/plan suite if present
     *
     * @param execSummary [ExecutionSummary] of the nexial script/plan execution
     */
    @Throws(TmsException::class)
    fun importResultsToTms(execSummary: ExecutionSummary) {
        val referenceData = execSummary.referenceData
        if (referenceData == null || !referenceData.containsKey(RUNTIME_ARGS)) {
            throw TmsException("Unable to find runtime args. Exiting...")
        }
        val runtimeArgs = TextUtils.toList(referenceData[RUNTIME_ARGS], " ", true)
        if (runtimeArgs.contains(SCRIPT_ARG)) {
            val filepath = runtimeArgs[runtimeArgs.indexOf(SCRIPT_ARG) + 1]
            uploadResults(filepath, "", execSummary)
        } else if (runtimeArgs.contains(PLAN_ARG)) {
            val filepath = runtimeArgs[runtimeArgs.indexOf(PLAN_ARG) + 1]
            for (path in TextUtils.toList(filepath, ",", true)) {
                execSummary.nestedExecutions
                    .filter { scriptExec -> scriptExec.planFile != null && scriptExec.planFile == path }
                    .map { summary1 -> summary1.planName }.distinct().forEach { uploadResults(path, it, execSummary) }
            }
        } else {
            throw TmsException("Unable to find runtime args to upload result. Exiting....")
        }
    }

    /**
     * Process a nexial script testFile and create or update a suite depending upon whether a suite corresponding
     * to the script is existing or not
     * @param testPath the path of the script testFile
     * @param testFile     the [TestFile] instance representing the entry for the script testFile in the project.tms.json
     * @param tms instance of [TMSOperation] user want to perform
     * @return the updated TestFile instance for the script testFile
     */
    @Throws(TmsException::class)
    private fun processScript(testPath: String, testFile: TestFile?, tms: TMSOperation): TestFile {
        val suite: TmsSuite?
        val isUpdated: Boolean
        val testCases = ReadScript.loadScript(testPath)
        val suiteName = FilenameUtils.removeExtension(File(testPath).name)
        if (testFile == null) {
            val upload = SuiteUpload(suiteName, testPath, tms)
            ConsoleUtils.log("Initiated suite upload.")
            suite = upload.uploadScript(testCases)
            isUpdated = true
            ConsoleUtils.log("Completed suite upload with id: " + suite.id)
        } else {
            val suiteId = testFile.suiteId
            val update = SuiteUpdate(testFile, testPath, tms)
            ConsoleUtils.log("Initiated suite update for suite: $suiteId")
            if (!update.shouldUpdateSuite()) {
                throw TmsException("Unable to update the suite due to existing test run or manual intervention. Exiting...")
            }
            suite = update.scriptSuiteUpdate(testCases)
            isUpdated = update.isUpdated
            ConsoleUtils.log("Completed suite update for suite: $suiteId")
        }
        if (isUpdated) UpdateTestFiles.updateScriptFile(testPath, suite, true)
        return getScriptJson(suite, testCases, testFile, testPath)
    }

    /**
     * Retrieve a new json entry from the project.tms.json testFile for the script
     *
     * @param filepath     path of the script testFile
     * @param suite        existing suite details
     * @param tmsTestCases the [TmsTestCase] instances for the scenarios in the script testFile
     * @param testFile         the existing json entry in the project.tms.json corresponding to the Script testFile
     * @return updated json entry for the script
     */
    private fun getScriptJson(suite: TmsSuite, tmsTestCases: List<TmsTestCase>,
                              testFile: TestFile?, filepath: String): TestFile {
        var testFile1 = testFile
        val scenarios = mutableListOf<Scenario>()
        val cache = mutableMapOf<String, String>()
        for (tmsTestCase in tmsTestCases) {
            val testCaseName = tmsTestCase.name
            val testCaseId = suite.testCases!![testCaseName] ?: continue
            scenarios.add(Scenario(testCaseName, testCaseName, testCaseId))
            cache[testCaseName] = tmsTestCase.cache!!
        }
        if (testFile1 == null) {
            testFile1 = TestFile(suite.id, suite.name, suite.suiteUrl, getRelativePath(filepath),
                SCRIPT, null, null, scenarios, cache)
        } else {
            testFile1.scenarios = scenarios
            testFile1.cache = cache
        }
        return testFile1
    }

    /**
     * Process a nexial script testFile and create of update a suite depending upon whether a suite corresponding
     * to the script is existing or not
     * @param testPath the path of the plan testFile
     * @param testFile     the existing json entry in the project.tms.json corresponding to the plan testFile
     * @param subplan  the subplan name
     * @param tms instance of [TMSOperation] user want to perform
     * @return the updated TestFile instance for the script testFile
     */
    @Throws(TmsException::class)
    private fun processPlan(testPath: String, testFile: TestFile?, subplan: String, tms: TMSOperation): TestFile {
        val testCaseMap: Map<Int, Map<String, String>>
        val suite: TmsSuite
        val isUpdated: Boolean
        val testCasesToPlanStep = ReadPlan.loadPlan(testPath, subplan)
        val suiteName = FilenameUtils.removeExtension(File(testPath).name) + "/" + subplan
        if (testFile == null) {
            ConsoleUtils.log("Initiated suite upload")
            val upload = SuiteUpload(suiteName, testPath, tms)
            suite = upload.uploadPlan(testCasesToPlanStep)
            testCaseMap = upload.testCaseToStep
            isUpdated = true
            ConsoleUtils.log("Suite upload completed for suite: ${suite.id}")
        } else {
            ConsoleUtils.log("Initiating suite update for suite: ${testFile.suiteId}")
            val update = SuiteUpdate(testFile, testPath, tms)
            if (!update.shouldUpdateSuite()) {
                throw TmsException("Unable to upload the suite. Exiting...")
            }
            suite = update.planSuiteUpdate(testCasesToPlanStep)
            testCaseMap = update.testCaseToStep
            isUpdated = update.isUpdated
            ConsoleUtils.log("Suite update completed for suite: ${suite.id}")
        }
        // update the plan testFile with test case ids
        if (isUpdated) UpdateTestFiles.updatePlanFile(testPath, suite, subplan)
        ConsoleUtils.log("Test file update completed.")
        return getPlanJson(suite, getScriptFiles(testCaseMap), testFile, testPath,
            subplan, TmsMetaJson.retrieveCache(testCasesToPlanStep))
    }

    /**
     * Upload execution result to already imported testcases to provided tms tool
     *
     * @param testPath path of script file/plan file for which result to be uploaded
     * @param subplan of the plan file only, empty in case of script file
     * @param summary [ExecutionSummary] from the execution output file
     */
    @Throws(TmsException::class)
    private fun uploadResults(testPath: String, subplan: String?, summary: ExecutionSummary) {
        if (!FileUtil.isFileReadWritable(testPath, MIN_EXCEL_FILE_SIZE)) {
            throw TmsException("File $testPath is not readable or writable")
        }
        val tmsFile = getJsonEntryForFile(testPath, subplan)

        val file =
            tmsFile.file ?: throw TmsException("Script/Plan is not imported to tms. So can't import Test results")
        val suiteId = file.suiteId

        projectId = tmsFile.projectId
        val update = SuiteUpdate(file, testPath, getTmsInstance(projectId))
        ConsoleUtils.log("Initiating Test Result upload for suiteId: $suiteId")
        update.addResults(summary)
        ConsoleUtils.log("Test result upload completed for suiteId: $suiteId")
    }


    /**
     * Retrieve a new json entry from the project.tms.json testFile for the plan testFile
     *
     * @param suite       existing suite details
     * @param planSteps the List of [TestFile] instances for the scenarios in the subplan testFile to each plan step
     * @param testFile    the existing json entry in the project.tms.json corresponding to the plan testFile and subplan
     * @param filepath    path of the script testFile
     * @param subplan     the subplan name
     * @param cache       [Map] of scenario to the cache
     * @return updated json entry for the script
     */
    private fun getPlanJson(suite: TmsSuite, planSteps: List<TestFile>, testFile: TestFile?,
                            filepath: String, subplan: String, cache: Map<String, String>?): TestFile {
        var testFile1 = testFile
        if (testFile1 == null) {
            testFile1 = TestFile(suite.id, suite.name, suite.suiteUrl,
                getRelativePath(filepath), PLAN, subplan, planSteps, null, cache)
        } else {
            testFile1.planSteps = planSteps
            testFile1.cache = cache
        }
        return testFile1
    }

    /**
     * Retrieve list of script files as a [TestFile] from subplan of plan file
     *
     * @param testCaseToStep map of plan step to testcases with testcase name and testcase id
     * @return [List] of [TestFile] with script data
     */
    private fun getScriptFiles(testCaseToStep: Map<Int, Map<String, String>>): List<TestFile> {
        val scriptFiles = mutableListOf<TestFile>()
        testCaseToStep.forEach { (step, testCases) ->
            val script = TestFile()
            script.path = getRelativePath(scriptToStep!![step])
            script.fileType = SCRIPT
            script.stepId = step.toString()
            val scenarios = mutableListOf<Scenario>()
            testCases.forEach { (testCase, testCaseId) ->
                scenarios.add(Scenario(testCase, StringUtils.substringBetween(testCase, "/"), testCaseId))
            }
            script.scenarios = scenarios
            scriptFiles.add(script)
        }
        return scriptFiles
    }
}