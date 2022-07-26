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
import org.apache.commons.lang3.StringUtils
import org.nexial.core.ExecutionInputPrep
import org.nexial.core.Nexial
import org.nexial.core.NexialConst
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelConfig
import org.nexial.core.model.ExecutionDefinition
import org.nexial.core.model.TestProject
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.InputFileUtils
import java.io.File

/**
 * Parse the Nexial Plan file and return the test cases accordingly
 */
object ReadPlan {
    var scriptToStep: HashMap<Int, String>? = null

    /**
     * Parse the specified nexial plan file for the specified sub plan and return the test cases associated with each plan
     * step
     *
     * @param testPlanPath the path of the plan file
     * @param subplan      the subplan name
     * @return an [Map] of the test cases associated with the plan steps in the plan
     */
    @Throws(TmsException::class)
    fun loadPlan(testPlanPath: String, subplan: String): LinkedHashMap<Int, List<TmsTestCase>> {
        try {
            val testPlanFile = File(testPlanPath)
            val excel = Excel(testPlanFile, NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP, false)
            val subplanSheets = InputFileUtils.retrieveValidPlanSequence(excel)
                .filter { splan -> StringUtils.equals(splan.name, subplan)}
            if (CollectionUtils.isEmpty(subplanSheets)) {
                throw TmsException("Unable to find provide subplan '$subplan' from file '$testPlanPath'")
            }
            return getSubPlanMap(testPlanFile, subplanSheets[0])
        } catch (e: Exception) {
            throw TmsException("Unable to read excel file due to ${e.message}")
        }
    }

    /**
     * Derive the [TestProject] for the plan file
     *
     * @param testPlanPath the path of the plan file
     * @param testPlanFile the name of the plan file
     * @return TestProject instance for the specified plan
     */
    @Throws(TmsException::class)
    private fun getTestProject(testPlanPath: String, testPlanFile: File): TestProject {
        if (!InputFileUtils.isValidPlanFile(testPlanPath)) {
            throw TmsException("specified test plan ($testPlanPath) is not readable or does not contain valid format.")
        }
        val project = TestProject.newInstance(testPlanFile)
        if (!project.isStandardStructure) {
            ConsoleUtils.log("specified plan ($testPlanFile) not following standard project " +
                        "structure, related directories would not be resolved from commandline arguments.")
        }
        return project
    }

    /**
     * Return a [List] of [ExecutionDefinition]s. Each instance representing a single plan step
     *
     * @param testPlanFile the path of the plan file
     * @param subplan      the sub plan name
     * @return List of [ExecutionDefinition]s
     */
    @Throws(TmsException::class)
    private fun getExecutions(testPlanFile: File, subplan: Excel.Worksheet?): List<ExecutionDefinition> {
        val rowStartIndex = ExcelConfig.ADDR_PLAN_EXECUTION_START.rowStartIndex
        val lastExecutionRow = subplan!!.findLastDataRow(ExcelConfig.ADDR_PLAN_EXECUTION_START)
        val project = getTestProject(testPlanFile.absolutePath, testPlanFile)
        val nexial = Nexial()

        // map to store the script association with plan step (row number)
        scriptToStep = HashMap()
        val executions = mutableListOf<ExecutionDefinition>()

        //parsing the subplan sheet
        for (i in rowStartIndex until lastExecutionRow) {
            val row = subplan.sheet.getRow(i)
            val rowNum = row.rowNum + 1
            val msgSuffix = " specified in ROW " + rowNum + " of " + subplan.name + " in " + testPlanFile
            if (ExecutionInputPrep.isPlanStepDisabled(row)) continue

            val testScript = nexial.deriveScriptFromPlan(row, project, testPlanFile.absolutePath)
            if (!InputFileUtils.isValidScript(testScript.absolutePath)) {
                throw RuntimeException("Invalid/unreadable test script$msgSuffix")
            }
            // storing the script to step association
            scriptToStep!![rowNum] = testScript.absolutePath
            val scenarios = nexial.deriveScenarioFromPlan(row, nexial.deriveScenarios(testScript))
            val exec = ExecutionDefinition()
            exec.planFile = subplan.file.absolutePath
            exec.testScript = testScript.absolutePath
            exec.scenarios = scenarios
            exec.planSequence = rowNum
            executions.add(exec)
        }
        return executions
    }

    /**
     * Loop over the executions present for the plan and retrieve the test cases belonging to the scripts specified in the
     * plan steps
     *
     * @param testPlanFile [File] representing the plan file
     * @param worksheet    [Worksheet] representing the subplan worksheet
     * @return a [Map] of the test cases associated with each plan step
     */
    @Throws(TmsException::class)
    private fun getSubPlanMap(testPlanFile: File, worksheet: Worksheet?): LinkedHashMap<Int, List<TmsTestCase>> {
        val executions = getExecutions(testPlanFile, worksheet)
        val testCasesToPlanStep = LinkedHashMap<Int, List<TmsTestCase>>()
        executions.forEach { exec -> testCasesToPlanStep[exec.planSequence] = resolveTestCases(exec) }
        return testCasesToPlanStep
    }

    /**
     * Filter out the scenarios not specified in the plan step for each execution
     *
     * @param exec [ExecutionDefinition] instance representing single plan step
     * @return a [List] of [TmsTestCase]
     */
    @Throws(TmsException::class)
    private fun resolveTestCases(exec: ExecutionDefinition): List<TmsTestCase> {
        val testCases = ReadScript.loadScript(exec.testScript)
        val execScenarios = exec.scenarios
        val testCases1 = mutableListOf<TmsTestCase>()
        testCases.filter{ testCase -> execScenarios.contains(testCase.name) }
            .forEach { testCase ->
                testCase.setPlanTestCaseName(exec.planSequence)
                testCases1.add(testCase)
            }
        return testCases1
    }
}