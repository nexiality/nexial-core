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

import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.ExecutionInputPrep
import org.nexial.core.NexialConst
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelArea
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.model.TestProject
import org.nexial.core.tms.TmsConst.NAT
import org.nexial.core.tms.model.TmsCustomStep
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.tms.model.TmsTestStep
import org.nexial.core.utils.InputFileUtils
import java.io.File

/**
 * Parse a script file and return the List of Test Cases by reading the scenarios.
 */
object ReadScript {
    /**
     * Parse the script file specified by the filepath and return the [List] of Test Cases for it.
     *
     * @param filepath the path of the script file
     * @return List of test cases mapped to scenarios
     */
    @Throws(TmsException::class)
    fun loadScript(filepath: String): List<TmsTestCase> {
        val testCases = mutableListOf<TmsTestCase>()
        try {
            val script = getScript(filepath)
            val scenarioNames = InputFileUtils.retrieveValidTestScenarioNames(script)
            // parse the script file for every scenario and create TmsTestCase objects
            scenarioNames.forEach { scenarioName ->
                val worksheet = script.worksheet(scenarioName)
                val testCase = TmsTestCase(worksheet)
                if (!StringUtils.startsWith(testCase.name.lowercase(), NAT)) {
                    val activities = parseScenario(testCase, worksheet)
                    testCase.testSteps = activities
                    testCase.setCache()
                    testCases.add(testCase)
                }
            }
        } catch (e: Exception) {
            throw TmsException("Error occurred while parsing the excel file $filepath due to ${e.message}")
        }
        return testCases
    }

    /**
     * Perform validations on the path provided and return and [Excel] instance of the Script file
     *
     * @param testScriptPath path of the script file
     * @return Excel instance of script file
     */
    @Throws(TmsException::class)
    internal fun getScript(testScriptPath: String): Excel {
        val testScriptFile = File(testScriptPath)
        if (!testScriptFile.exists()) throw TmsException("The path specified does not exist")

        val script = InputFileUtils.resolveValidScript(testScriptPath)
            ?: throw TmsException("Invalid test script - $testScriptPath")
        val project = TestProject.newInstance(testScriptFile)
        if (!project.isStandardStructure) {
            throw TmsException("specified test script ($testScriptFile) not following standard project structure, " +
                    "related directories would not be resolved from commandline arguments.")
        }
        return script
    }

    /**
     * Parse each scenario present in the script and return a list of the test steps(activities) inside the scenario.
     *
     * @param scenario  A scenario in the Nexial script, mapped to a single Test Case
     * @param worksheet the current [Worksheet]
     * @return List of [TmsTestStep] belonging to the scenario
     */
    @Throws(TmsException::class)
    private fun parseScenario(scenario: TmsTestCase, worksheet: Excel.Worksheet): List<TmsTestStep> {
        val lastCommandRow = worksheet.findLastDataRow(ADDR_COMMAND_START)
        val area = ExcelArea(worksheet, ExcelAddress("$FIRST_STEP_ROW:$COL_REASON$lastCommandRow"), false)
        val testSteps = mutableListOf<TmsTestStep>()
        val testCases = mutableListOf<String>()
        val scenarioRef = "Error found in [${worksheet.file.name}][${worksheet.name}]"

        // 3. parse for test steps->test case grouping
        var currentActivity: TmsTestStep? = null
        area.wholeArea.forEach { row ->
            val cellActivity = row[COL_IDX_TESTCASE]
            val errorPrefix = "$scenarioRef[${cellActivity!!.reference}]: "
            val activity = Excel.getCellValue(cellActivity)

            // detect space only activity name
            if (StringUtils.isNotEmpty(activity) && StringUtils.isAllBlank(activity)) {
                throw TmsException("${errorPrefix}Found invalid, space-only activity name")
            }

            // detect leading/trailing non-printable characters
            if (!StringUtils.equals(activity, StringUtils.trim(activity))) {
                throw TmsException(errorPrefix + NexialConst.RB.Fatal.text("problematicName") + "activity " + activity)
            }
            val hasActivity = StringUtils.isNotBlank(activity)
            if (currentActivity == null && !hasActivity) {
                // first row must define test case (hence at least 1 test case is required)
                throw TmsException("${errorPrefix}Invalid format; First row must contain valid activity name")
            }
            if (hasActivity) {
                if (testCases.contains(activity)) {
                    // found duplicate activity name!
                    throw TmsException("${errorPrefix}Found duplicate activity name '$activity'")
                }
                testCases.add(activity)
                currentActivity = TmsTestStep(scenario, TextUtils.toOneLine(activity, true))
                testSteps.add(currentActivity!!)
            }
            if (ExecutionInputPrep.isTestStepDisabled(row)) return@forEach

            val testStep = TmsCustomStep(row, currentActivity)
            currentActivity!!.addTmsCustomTestStep(testStep)
        }
        return testSteps
    }
}