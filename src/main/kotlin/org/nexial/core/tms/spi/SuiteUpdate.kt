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

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.tms.model.Scenario
import org.nexial.core.tms.model.TestFile
import org.nexial.core.tms.model.TmsSuite
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.tms.tools.CloseTestRun
import org.nexial.core.tms.tools.TmsImporter.closePreviousRun
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import java.util.*


/**
 * Perform update operations on an already existing suite
 */
class SuiteUpdate(private val file: TestFile, private val testPath: String,
                  private var suiteId: String,  private val tms: TMSOperation) {

    constructor(file: TestFile, testPath: String, tms: TMSOperation): this(file, testPath, file.suiteId!!, tms)

    internal var testCaseToStep = linkedMapOf<Int, Map<String, String>>()
    internal var isUpdated = false
    private var sectionId: String? = null

     fun shouldUpdateSuite(): Boolean {
        val existingRuns = tms.getExistingActiveRuns(suiteId)
        if (existingRuns.isEmpty()) return true
        val choice = if (closePreviousRun || ExecUtils.isRunningInZeroTouchEnv()) {
            println("Closing test runs for $suiteId")
            2
        } else {
            println("""1. Proceed with the update.
            2. Close existing runs and then continue with the update.
            3. Exit the process.
            """.trimIndent())
            print("Input your choice >> ")
            val scan = Scanner(System.`in`)
            val choice = scan.nextInt()
            scan.close()
            choice
        }

        when(choice) {
            1 -> {
                ConsoleUtils.log("Continue updating testcases without closing corresponding test runs.")
                return true
            }
            2 -> {
                val collect = existingRuns.map { it.id.toString() }.toMutableList()
                CloseTestRun().closeActiveRuns(tms, collect)
                ConsoleUtils.log("Closed corresponding test runs and continue updating testcases.")
                return true
            }
            else -> {
                ConsoleUtils.log("Exiting updating operation on the test suite.")
                return false
            }
        }
    }

    /**
     * Update the suite corresponding to the script.
     *
     * @param testCases the [TmsTestCase] instances for the scenarios in the script file
     * @return TmsSuite object containing suite details
     */
    @Throws(TmsException::class)
    fun scriptSuiteUpdate(testCases: List<TmsTestCase>): TmsSuite {
        // firstly update description of suite
        val tmsSuite = tms.updateSuite(suiteId, testPath)
        ConsoleUtils.log("Updated test suite description for suite: $suiteId")
        val existingScenarios = file.scenarios!!
        val caseToScenario = updateExistingScenarios(testCases, existingScenarios).toMutableMap()

        // check for new or deleted test cases only in case if scenarios not mentioned in command
        val caseToScenario1 = addOrDeleteTestCase(testCases, existingScenarios)
        caseToScenario.putAll(caseToScenario1)
        tmsSuite.testCases = caseToScenario
        reorderSuiteAfterScriptUpdate(tmsSuite, testCases)
        return tmsSuite
    }

    /**
     * Update the suite corresponding to the plan.
     *
     * @param testCasesToPlanStep the [TmsTestCase] instances for the scenarios associated with each plan step
     * @return TmsSuite object containing suite details
     */
    @Throws(TmsException::class)
    fun planSuiteUpdate(testCasesToPlanStep: LinkedHashMap<Int, List<TmsTestCase>>): TmsSuite {
        val tmsSuite = tms.updateSuite(suiteId, testPath)
        tmsSuite.name = file.suiteName!!
        val existingTestCases1 = getTestCaseFromJsonPlan()
        val scenarioNamesToStep = LinkedHashMap<Int, List<String>>()
        testCaseToStep = LinkedHashMap()

        testCasesToPlanStep.forEach { (row, testCases) ->
            val existingTestCases = mutableListOf<Scenario>()
            existingTestCases.addAll(existingTestCases1)
            testCases.forEach { testCase ->
                existingTestCases.removeIf { t -> testCase.row != t.testCase.substringAfterLast("/").toInt() }
            }
                val testCases1 = tobeUpdatedTestCases(testCases, existingTestCases)
                // do not update scripts added just now
                val updatedTestCases = updateExistingScenarios(testCases1, existingTestCases)
                val caseToScenario: LinkedHashMap<String, String> = LinkedHashMap(updatedTestCases)

                // update all scenarios as well
                val addedDeletedTestcases = addOrDeleteTestCase(testCases, existingTestCases)
                caseToScenario.putAll(addedDeletedTestcases)

                testCaseToStep[row] = caseToScenario
                scenarioNamesToStep[row] = testCases.map { tc -> tc.name }
        }

        val caseToScenario = reorderSuiteAfterPlanUpdate(scenarioNamesToStep)
        tmsSuite.testCases = caseToScenario
        return tmsSuite
    }

    /**
     * Return the [Scenario] instances for each test case from the project.tms.json for the testFile specified
     *
     * @return List of scenarios from the project.tms.json testFile
     */
     fun getTestCaseFromJsonPlan(): MutableList<Scenario> {
        val scenarios = mutableListOf<Scenario>()
        val planSteps = file.planSteps ?: return scenarios

        planSteps.forEach { file -> scenarios.addAll(file.scenarios!!) }
        return scenarios
    }

    /**
     * Add results to already imported testcases
     *
     * @param summary the [ExecutionSummary] of execution from detailed json file
     */
    @Throws(TmsException::class)
    fun addResults(summary: ExecutionSummary) {
        if (StringUtils.isEmpty(file.suiteUrl)) {
            throw TmsException("Unable to update the result as test script/plan " +
                    "import data is not available OR suite url is empty. Exiting...")
        }
        tms.addResults(summary, file)
    }

    /**
     * Update the test cases associated with the specified scenario names with the updated data inside the scenarios.
     *
     * @param testCases  List of possible test cases for each scenario inside the script file passed in as argument
     * @param existingTestCases List if test cases already existing inside TMS
     * @return Map of test case ids to test case names after updating
     */
    @Throws(TmsException::class)
    private fun updateExistingScenarios(testCases: List<TmsTestCase>, existingTestCases: List<Scenario>):
            Map<String, String> {
        var cache = file.cache
        if (cache == null) { cache = LinkedHashMap() }
        // retrieving testcases to update by matching present scenarios against user specified scenarios
        val updateCaseResponses = mutableMapOf<String, String>()
        // specified scenarios not found
        if (testCases.isEmpty()) {
            ConsoleUtils.error("There are no scenarios specified exist in the given script.")
            return updateCaseResponses
        }
        testCases.forEach { testCase ->
            for (existingTests in existingTestCases) {
                val testCase1 = existingTests.testCase
                if (testCase1 == testCase.testCaseName) {
                    if (testCase.cache == cache[StringUtils.substringBeforeLast(testCase1, "/")]) {
                        updateCaseResponses[testCase1] = existingTests.testCaseId
                    } else {
                        val response = tms.updateCase(existingTests.testCaseId, testCase, false)
                        updateCaseResponses.putAll(response)
                    }
                    break
                }
            }
        }

        // putting the response back into the map to update the file,
        return updateCaseResponses
    }

    /**
     * Add or delete testcase from the suite associated with the suite id specified.
     *
     * @param testCases         List of TestCases for the file currently under processing
     * @param existingTestCases the test cases already existing inside TMS
     * @return Map of test case id to test case names
     */
    @Throws(TmsException::class)
    private fun addOrDeleteTestCase(testCases: List<TmsTestCase>, existingTestCases: List<Scenario>):
            Map<String, String> {
        // delete any test cases not associated with any scenario in the sheet
        deleteTestCase(testCases, existingTestCases)
        // add any new scenarios not associated with any existing case as new test cases
        return addTestCase(testCases, existingTestCases)
    }

    /**
     * Compare the test cases read from the file against test cases read from project meta json and
     * find if there are any test cases not mapped to a scenario and then delete them.
     *
     * @param testCases         the current test cases, each test case representing a scenario
     * @param existingTestCases the ids of test cases currently existing inside TMS
     */
    @Throws(TmsException::class)
    private fun deleteTestCase(testCases: List<TmsTestCase>, existingTestCases: List<Scenario>) {
        val scenariosInTestFile = testCases.map { it.testCaseName }
        val deletedTestCases = existingTestCases.filter { !scenariosInTestFile.contains(it.testCase) }

        if (deletedTestCases.isEmpty()) return
        deletedTestCases.forEach { testCase ->
            tms.delete(suiteId, testCase.testCaseId)
            existingTestCases.dropWhile { it.testCaseId == testCase.testCaseId }
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
    @Throws(TmsException::class)
    private fun addTestCase(testCases: List<TmsTestCase>, existingTmsCases: List<Scenario>): Map<String, String> {
        // find any new scenario not mapped to a test case and add a new test case for it
        var newTestcaseResponse: Map<String, String> = HashMap()

        val testcaseNames = existingTmsCases.map { it.testCase }
        val testCasesToAdd = testCases.filter { tc -> !testcaseNames.contains(tc.testCaseName) }
        if (CollectionUtils.isNotEmpty(testCasesToAdd)) {
            sectionId = tms.getSectionId(suiteId)
            newTestcaseResponse = tms.addCases(sectionId!!, testCasesToAdd)
            isUpdated = MapUtils.isNotEmpty(newTestcaseResponse)
        }
        return newTestcaseResponse
    }

    /**
     * Reorder the test cases in the suite according to the scenario order in the script file.
     *
     * @param suite     Test suite details
     * @param testCases the [TmsTestCase] instances for the scenarios in the script file
     */
    @Throws(TmsException::class)
    private fun reorderSuiteAfterScriptUpdate(suite: TmsSuite, testCases: List<TmsTestCase>) {
        val order = mutableListOf<TestcaseOrder>()
        var sequenceNum = 0
        // Don't do every time
        testCases.forEach { tmsTestCase ->
            val tmsTestCaseName = tmsTestCase.name
            val testCaseId = suite.testCases?.get(tmsTestCaseName)
            if(testCaseId != null) {
                order.add(TestcaseOrder(testCaseId, tmsTestCaseName, sequenceNum))
                sequenceNum++
            }
        }
        tms.updateCaseOrder(suite.id, sectionId, order)
    }

    /**
     * Reorder the test cases in the suite for a plan file according to the plan step and scenario order
     *
     * @param scenarioNamesToStep the scenario names specified in the subplan for each plan step
     * @return [Map] of testcase name to the testcase id
     */
    @Throws(TmsException::class)
    private fun reorderSuiteAfterPlanUpdate(scenarioNamesToStep: LinkedHashMap<Int, List<String>>): Map<String, String> {
        val order = mutableListOf<TestcaseOrder>()
        val caseToScenario: MutableMap<String, String> = LinkedHashMap()
        val testCaseToPlanStep1: MutableMap<Int, MutableMap<String, String>> = LinkedHashMap()
        var sequenceNum = 0
        scenarioNamesToStep.forEach { (row, scenarioNames) ->
            val map = testCaseToStep[row]
            scenarioNames.forEach { scenario ->
                map!!.keys.forEach { testCase ->
                    val testCaseName = StringUtils.substringBetween(testCase, "/")
                    if (StringUtils.equals(scenario, testCaseName)) {
                        val testCaseId = map[testCase]
                        if(testCaseId != null) {
                            order.add(TestcaseOrder(testCaseId, testCaseName, sequenceNum, "testCase"))
                            // reorder test scenarios to update in json file
                            caseToScenario[testCase] = testCaseId
                            updateTestPlan(testCaseToPlanStep1, row, testCase, testCaseId)
                            sequenceNum++
                        }
                    }
                }
            }
        }
        testCaseToStep = LinkedHashMap(testCaseToPlanStep1)
        tms.updateCaseOrder(suiteId, sectionId, order)
        return caseToScenario
    }

    /**
     * Update test plan testcase ids after updating scenarios from file
     *
     * @param testCaseToStep [Map] of plan step to test case
     * @param row plan step row number
     * @param testCase test case name to be updated
     * @param testId test case id to be updated
     */
    private fun updateTestPlan(testCaseToStep: MutableMap<Int, MutableMap<String, String>>,
                               row: Int, testCase: String, testId: String) {
        if (testCaseToStep.containsKey(row)) {
            val map = testCaseToStep[row]!!
            map[testCase] = testId
        } else {
            val map = LinkedHashMap<String, String>()
            map[testCase] = testId
            testCaseToStep[row] = map
        }
    }

    /**
     * Retrieve list of the testcase to be updated by comparing with existing testcases imported to tms tool
     *
     * @param testCases [List] of the testcases to be updated
     * @param existingTestCases [List] of the existing testcases imported till now
     * @return [List] of [TmsTestCase] to be updated
     */
    private fun tobeUpdatedTestCases(testCases: List<TmsTestCase>, existingTestCases: List<Scenario>): List<TmsTestCase> {
        val cases = mutableListOf<TmsTestCase>()
        existingTestCases.forEach { scenario ->
            testCases.filter { tc -> tc.testCaseName == scenario.testCase }.forEach { cases.add(it) }
        }
        return cases
    }
}