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

import org.apache.commons.io.FileUtils
import org.json.JSONArray
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.excel.ExcelConfig.PLAN_ROW_START_INDEX
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.tms.TmsConst.OUTPUT_EXCEL_PATTERN
import org.nexial.core.tms.model.*
import org.nexial.core.tms.spi.TmsMetaJson.getRelativePath
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashMap

interface TMSOperation {
    /**
     * Create a new test suite with the provided suite name
     *
     * @param suiteName the name of suite
     * @return the [TmsSuite] as a response of the API after suite creation
     */
    @Throws(TmsException::class)
    fun createSuite(suiteName: String, testPath: String): TmsSuite

    /**
     * Update test suite with the provided runtime config like description, Nexial Version and modified time
     *
     * @param parentId the name of suite
     * @return the [TmsSuite] as a response of the API after updating suite

     */
    @Throws(TmsException::class)
    fun updateSuite(parentId: String, testPath: String): TmsSuite

    /**
     * Create a new section inside the suite associated with the specified suite id
     *
     * @param parentId     the suite id in which the section is to be added
     * @param sectionName the name of the section
     * @return the json response of the API after section creation
     */
    @Throws(TmsException::class)
    fun addSection(parentId: String, sectionName: String): String

    /**
     * Add test cases (mainly for TestRail and for others suite create response)
     *
     * @param parentId the id of section
     * @param testCases List of the TmsTestcase
     * @return the map of the added testcase name to testId
     */
    @Throws(TmsException::class)
    fun addCases(parentId: String, testCases: List<TmsTestCase>): Map<String, String> { val testCaseToScenario = mutableMapOf<String, String>()
        testCases.forEach { testCaseToScenario.putAll(updateCase(parentId, it, true)) }
        return testCaseToScenario
    }

    /**
     * Adds or update a single test case based on the input parameters
     *
     * @param parentId  in case of new test case, this will represent the section id in which new test case
     *                  is to be added, otherwise it represents the test case id of the test case to update
     * @param testCase   an instance of [TmsTestCase] containing Test Case and Test Steps
     * @param isNewTestCase true if a new test case is to be added, false if an existing test case is to be updated
     * @return a [Map] of the test case id to the test case name
     */
    @Throws(TmsException::class)
    fun updateCase(parentId: String, testCase: TmsTestCase, isNewTestCase: Boolean): Map<String, String>

    /**
     * Delete the specified test cases from the suite
     * @param parentId    [List] sectionId of test cases to delete
     * @param testcaseId [String] test case id to delete
     * @return [Boolean] true if testcase deleted otherwise false
     */
    @Throws(TmsException::class)
    fun delete(parentId: String, testcaseId: String) : Boolean

    /**
     * Reorder test cases
     *
     * @param rootId the id of section
     * @param parentId id of section if any otherwise suiteId only
     * @param order list of TestCaseOrder
     */
    @Throws(TmsException::class)
    fun updateCaseOrder(rootId: String, parentId: String?, order: List<TestcaseOrder>)

    /**
     * Retrieve all the sections associated with the suite id passed in
     *
     * @param parentId the suite id
     * @return JSONArray containing the sections belonging to the suite
     */
    @Throws(TmsException::class)
    fun getSectionId(parentId: String): String

    /**
     * Get all the existing active runs for the suite id passed in
     *
     * @param parentId the suite id
     * @return a [JSONArray] containing the active runs
     */
    @Throws(TmsException::class)
    fun getExistingActiveRuns(parentId: String): List<TestRun>

    /**
     * Close the Test Run associated with the Test Run id passed in
     * @param runId test run id
     */
    @Throws(TmsException::class)
    fun closeRun(runId: String)

    /**
     * Add test results for testcases
     * @param summary [ExecutionSummary] to add results from
     * @param file [TestFile] url of the suite
     */
    @Throws(TmsException::class)
    fun addResults(summary: ExecutionSummary, file: TestFile)

    fun getFilesToUpload(outputDir: String) = FileUtils
        .listFiles(File(outputDir), arrayOf("xlsx"), false)
        .filter { !it.name.startsWith("~") }.filter { RegexUtils.match(it.name, OUTPUT_EXCEL_PATTERN) }
        .map { it.absolutePath }.toMutableList()

    fun getScenarios(summary: ExecutionSummary, file: TestFile): LinkedHashMap<String, TestResult> {
        val scenarioIdsToStatus = linkedMapOf<String, TestResult>()
        val planSteps = file.planSteps

        if (planSteps == null || planSteps.isEmpty()) {
            // script run
            val scenarios = file.scenarios!!
            gatherScenarioResult(summary.nestedExecutions[0], scenarioIdsToStatus, scenarios, file)
        } else {
            // plan test
            summary.nestedExecutions.forEach { scriptExec ->
                val scriptImportedFilter = planSteps.filter { getRelativePath(scriptExec.scriptFile) == it.path!! }
                    .filter { it.stepId!!.toInt() == scriptExec.planSequence + PLAN_ROW_START_INDEX }
                if(scriptImportedFilter.isNotEmpty() && file.subplan == scriptExec.planName) {
                    val scenarios = scriptImportedFilter.first().scenarios!!
                    gatherScenarioResult(scriptExec, scenarioIdsToStatus, scenarios, file)
                }
            }
        }
        return scenarioIdsToStatus
    }

    fun gatherScenarioResult(scriptExec: ExecutionSummary, scenarioIdsToStatus: LinkedHashMap<String, TestResult>,
                             scenarios: List<Scenario>, file: TestFile) {
        scriptExec.nestedExecutions.forEach { iterExec ->
            iterExec.nestedExecutions.forEach { gatherTestStats(scenarioIdsToStatus, scenarios, it, file) }
        }
    }

    private fun gatherTestStats(scenarioIdsToStatus: LinkedHashMap<String, TestResult>, scenarios: List<Scenario>,
                                scenarioExec: ExecutionSummary, file: TestFile) {
        val scenarioName = scenarioExec.name
        val testCaseId = getTestcaseId(scenarios, scenarioName) ?: return

        var isPassed = scenarioExec.failCount == 0
        var passCount = scenarioExec.passCount
        var failCount = scenarioExec.failCount
        var skipCount = scenarioExec.skippedCount
        var elapsedTime = scenarioExec.elapsedTime

        val statMap: TestResult
        if (scenarioIdsToStatus.containsKey(testCaseId)) {
            statMap = scenarioIdsToStatus[testCaseId]!!
            isPassed = isPassed && statMap.outcome
            passCount += statMap.passCount
            failCount += statMap.failCount
            skipCount += statMap.skipCount
            elapsedTime += statMap.durationInMs
        }

        scenarioIdsToStatus[testCaseId] = TestResult(scenarioName, file.suiteId!!,
            testCaseId, elapsedTime, isPassed, passCount, failCount, skipCount)
    }

    /**
     * Returns  testcase id of testcase if imported else null
     *
     * @param scenarios [List] of [Scenario] imported
     * @param testcaseName the name of scenario whose testcase id to get
     * @return testcase id whose test results to be updated
     */
    private fun getTestcaseId(scenarios: List<Scenario>, testcaseName: String): String? {
        val scenario = scenarios.filter { it.name == testcaseName }
        return if(scenario.isNotEmpty()) scenario.first().testCaseId else null
    }

    /**
     * To check if user want to close test run during result upload
     * @return [Boolean] true if user select option to close run or false if don't want to close
     */
    fun shouldCloseTestRun(): Boolean {
        if(ExecUtils.isRunningInZeroTouchEnv()) { return false }

        /* prompt the user if the user chooses to close existing runs, call close runs method */
        println("Please select an option to close.")
        println("""
            1.Close existing runs.
            2.Exit the process without closing test run.
            """.trimIndent())
        print("Input your choice >> ")
        val scan = Scanner(System.`in`)
        return scan.nextInt() == 1
    }
}