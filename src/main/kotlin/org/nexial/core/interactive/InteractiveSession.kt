/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nexial.core.interactive

import org.apache.commons.collections4.BidiMap
import org.apache.commons.collections4.bidimap.DualLinkedHashBidiMap
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_NAME
import org.nexial.commons.utils.EnvUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP
import org.nexial.core.excel.Excel
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelArea
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionDefinition
import org.nexial.core.model.StepResult
import org.nexial.core.model.TestProject
import org.nexial.core.model.TestScenario
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import org.nexial.core.utils.ExecUtils.IGNORED_CLI_OPT
import org.nexial.core.utils.InputFileUtils
import java.io.File
import java.util.*

data class InteractiveSession(val context: ExecutionContext) {

    init {
        // val propKeys = System.getProperties().keys.toTypedArray()
        // for (name in propKeys) {

        // System.getProperties().forEach { (name, value) ->

        System.getProperties().toMap().forEach { (name, _) ->
            val n = name.toString()
            if (IGNORED_CLI_OPT.none { StringUtils.startsWith(n, it) }) context.setData(n, System.getProperty(n))
        }
    }

    // system
    val startTime: Long = System.currentTimeMillis()
    val hostname: String = EnvUtils.getHostName()
    val user: String = USER_NAME
    var excel: Excel? = null

    var executionDefinition: ExecutionDefinition? = null
        set(value) {
            if (value == null) throw IllegalArgumentException("ExecutionDefinition MUST not be null")

            field = value

            // save scenario, in case we can use it
            val execScenario = value.scenarios[0] ?: ""
            val dataSheet = value.dataSheets[0] ?: ""

            // setter will override scenario & data sheet information
            script = value.testScript

            if (dataSheet != "") {
                value.dataSheets.clear()
                value.dataSheets.add(dataSheet)
            }

            if (execScenario != "") {
                value.scenarios.clear()
                value.scenarios.add(execScenario)
            }

            if (value.dataFile != null) dataFile = value.dataFile.absolutePath

            if (execScenario != "") scenario = execScenario
        }

    // inflight / flyweight pattern
    var inflightScript: Excel? = null
    var inflightScenario: TestScenario? = null

    // user input
    var script: String? = null
        set(value) {
            if (FileUtil.isFileReadable(value)) {
                val reloadExcel = !StringUtils.equals(field, value)
                field = value
                loadTestScript(reloadExcel)
            } else {
                ConsoleUtils.error("Invalid script specified: $value")
                excel = null
                allScenarios.clear()
                allActivities.clear()
                allSteps.clear()
            }
        }

    private fun loadTestScript(reloadExcel: Boolean) {
        if (reloadExcel) {
            excel = Excel(File(script), DEF_OPEN_EXCEL_AS_DUP, false)
            inflightScript = null
        }

        if (executionDefinition != null) executionDefinition!!.testScript = script

        // check if current scenario is valid / if not use first valid one
        allScenarios.clear()
        allScenarios.addAll(InputFileUtils.retrieveValidTestScenarioNames(excel))
        if (StringUtils.isBlank(scenario) || !allScenarios.contains(scenario)) {
            scenario = if (allScenarios.isEmpty()) "" else allScenarios[0]
        } else {
            collectScenarioDetails()
            calibrateActivitiesAndSteps()
        }
    }

    fun reloadTestScript() {
        when {
            script == null                     -> ConsoleUtils.error("No test script assigned.")
            !FileUtil.isFileReadable(dataFile) -> ConsoleUtils.error("Assigned test script is not readable: $script")
            else                               -> loadTestScript(true)
        }
    }

    fun reloadProjectProperties() {
        val projectHome = executionDefinition?.project?.projectHome
        if (projectHome != null) {
            executionDefinition?.project?.projectHome = projectHome
            TestProject.listProjectPropertyKeys().forEach { key: String? ->
                System.setProperty(key, TestProject.getProjectProperty(key))
            }
        }
    }

    var dataFile: String? = null
        set(value) {
            if (FileUtil.isFileReadable(value)) {
                field = value
                loadDataFile()
            } else {
                ConsoleUtils.error("Invalid data file specified: $value")
            }
        }

    private fun loadDataFile() {
        if (executionDefinition == null) return

        val execDef = executionDefinition!!

        val dataFileObj = File(dataFile)
        val dataFileOfValue = dataFileObj.absolutePath
        if (execDef.dataFile == null || !StringUtils.equals(execDef.dataFile.absolutePath, dataFileOfValue)) {
            execDef.dataFile = dataFileObj
        }

        execDef.getTestData(true)
        this.iteration = if (this.iteration == 0) execDef.testData.iteration else this.iteration
    }

    fun reloadDataFile() {
        when {
            dataFile == null                   -> ConsoleUtils.error("No data file assigned.")
            !FileUtil.isFileReadable(dataFile) -> ConsoleUtils.error("Assigned data file is not readable: $dataFile")
            else                               -> loadDataFile()
        }
    }

    private val allScenarios: MutableList<String> = mutableListOf()
    var scenario: String? = null
        set(value) {
            if (excel != null && allScenarios.isNotEmpty()) {
                if (!allScenarios.contains(value)) {
                    ConsoleUtils.error("Invalid scenario specified: $value")
                } else {
                    field = value
                    executionDefinition!!.scenarios = mutableListOf(scenario)
                    executionDefinition!!.dataSheets = mutableListOf(scenario)
                    collectScenarioDetails()
                    calibrateActivitiesAndSteps()
                }
            }
        }

    private fun collectScenarioDetails() {
        allActivities.clear()
        allSteps.clear()
        activityStepMap.clear()
        clearActivities()
        clearSteps()

        // collect activities and steps for the specified scenario
        val worksheet = excel!!.worksheet(scenario)
        val lastCommandRow = worksheet.findLastDataRow(ADDR_COMMAND_START)
        val stepArea = ExcelAddress("$FIRST_STEP_ROW:$COL_REASON$lastCommandRow")
        val area = ExcelArea(worksheet, stepArea, false)

        var currentActivity = ""
        var i = 0
        while (i < area.wholeArea.size) {
            val row = area.wholeArea[i]
            val activityName = Excel.getCellValue(row[COL_IDX_TESTCASE])
            if (i == 0 && StringUtils.isBlank(activityName)) {
                ConsoleUtils.error("first row in Scenario '$scenario' must contain a valid activity name!")
                break
            }

            val currentRow = "" + (row[COL_IDX_COMMAND].rowIndex + 1)

            if (StringUtils.isNotBlank(activityName)) {
                allActivities[allActivities.size + 1] = activityName

                if (StringUtils.isEmpty(currentActivity)) {
                    // first time.. probably first row in this cell area
                    currentActivity = activityName
                    activityStepMap[currentActivity] = mutableListOf()
                } else {
                    if (!StringUtils.equals(currentActivity, activityName)) {
                        currentActivity = activityName
                        activityStepMap[currentActivity] = mutableListOf()
                    }
                }
            }

            activityStepMap[currentActivity]!!.add(currentRow)
            allSteps.add(currentRow)
            i++
        }
    }

    var iteration: Int = 0
        set(value) {
            field = value

            if (executionDefinition != null && executionDefinition!!.testData != null) {
                val testData = executionDefinition!!.testData

                val data = TreeMap(testData.getAllValue(iteration))
                testData.allSettings.forEach { (key, testValue) -> data[key] = testValue }
                data.putAll(ExecUtils.deriveJavaOpts())
                data.forEach { (key, dataValue) -> context.setData(key, dataValue) }
            }
        }

    private val allActivities: BidiMap<Int, String> = DualLinkedHashBidiMap()
    var activities: MutableList<String> = mutableListOf()
        set(value) {
            if (excel != null && allActivities.isNotEmpty() && value.isNotEmpty()) {
                field.clear()
                field.addAll(value)
                clearSteps()
            }
        }

    fun assignActivities(inputs: List<String>) {
        if (excel == null) {
            ConsoleUtils.error("Currently no test script assigned!")
            return
        }

        if (allScenarios.isEmpty()) {
            ConsoleUtils.error("No scenarios found in currently assigned test script!")
            return
        }

        if (scenario == null) {
            ConsoleUtils.error("Currently no scenario assigned!")
            return
        }

        if (allActivities.isEmpty) {
            ConsoleUtils.error("No activities found in currently assigned scenario!")
            return
        }

        if (inputs.isEmpty()) {
            ConsoleUtils.error("No activities specified!")
            return
        }

        if (inputs.size == 1 && inputs[0] == "*") {
            // reset to all activities
            useAllActivities()
            return
        }

        val mappedActivities = mutableListOf<String>()
        inputs.forEach { input ->
            run {
                var found = false
                allActivities.forEach { entry ->
                    // user specified by activity index or activity name
                    if (entry.key.toString() == input || entry.value == input) {
                        mappedActivities.add(entry.value)
                        found = true
                    }
                }

                if (!found) {
                    ConsoleUtils.error("Invalid activity index or name specified: $input")
                    return
                }
            }
        }

        // all activities are valid
        activities = mappedActivities
    }

    fun clearActivities() = activities.clear()

    fun useAllActivities() {
        clearSteps()
        clearActivities()
        activities.addAll(allActivities.values)
    }

    //    private fun formatActivity(activity: Entry<Int, String>) = "${activity.key}/${activity.value}"
    //    fun formatActivities(activities: MutableMap<Int, String>) = activities.map { activity -> formatActivity(activity) }
    fun formatActivities(activities: MutableList<String>) =
        activities.map { activity -> "${allActivities.getKey(activity)}/$activity" }

    private val activityStepMap: MutableMap<String, MutableList<String>> = mutableMapOf()

    private val allSteps: MutableList<String> = mutableListOf()
    var steps: MutableList<String> = mutableListOf()
        set(value) {
            if (excel != null && allSteps.isNotEmpty()) {
                if (value.isNotEmpty()) {
                    if (value.size == 1 && value[0] == "*") {
                        // reset to all steps
                        field.clear()
                        field.addAll(allSteps)
                        clearActivities()
                    } else if (value.find { step -> !allSteps.contains(step) } != null) {
                        // found invalid step
                        ConsoleUtils.error("Invalid step specified: $value")
                    } else {
                        // all steps are valid
                        field.clear()
                        field.addAll(value)
                        clearActivities()
                    }
                }
            }
        }

    private fun clearSteps() = steps.clear()

    // execution output
    val results: List<StepResult> = mutableListOf()

    var exception: Throwable? = null

    private fun calibrateActivitiesAndSteps() {
        if (activities.isEmpty() || activities.find { activity -> !allActivities.containsValue(activity) } != null) {
            // currently without activity or one or more activities are invalid against `allActivities`
            clearActivities()

            // check if current steps are valid / if not use the ones from scenario
            if (steps.isEmpty() || steps.find { step -> !allSteps.contains(step) } != null) useAllActivities()
        } else {
            clearSteps()
        }
    }
}
