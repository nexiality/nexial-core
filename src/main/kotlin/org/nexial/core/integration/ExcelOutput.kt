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
 *
 */

package org.nexial.core.integration

import org.apache.commons.lang3.StringUtils
import org.apache.poi.xssf.usermodel.XSSFCell
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.Excel.getCellValue
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelArea
import org.nexial.core.excel.ExcelConfig
import org.nexial.core.model.TestScenarioMeta
import java.io.File

private const val scenarioStartAddress = "A20"

class ExcelOutput(file: File) : IterationOutput() {

    init {
        val excel = Excel(file, false, false)
        parse(excel)
    }


    fun parse(excel: Excel) {
        fileName = excel.file.name
        val summarySheet: Worksheet = excel.worksheet("#summary")
        // summarySheet.findLastDataRow(ExcelAddress(scenarioStartAddress))
        val scenarioEndRow = summarySheet.sheet.lastRowNum
        val scenarioCells = summarySheet.getCellData("$scenarioStartAddress:A$scenarioEndRow")

        summary = parseSummaryOutput(summarySheet)
        scenarioCells.values.forEach { name ->
            if (StringUtils.isNotBlank(name)) {
                val scenarioSheet = excel.worksheet(name)
                val scenarioOutput = readScenarioOutput(scenarioSheet)
                scenarios.add(scenarioOutput!!)
                scenarioOutput.iterationOutput = this
            }
        }
        // parse data sheet if needed
        // data = parseDataSheet(excel.worksheet("#data"))

    }

    /*private fun parseDataSheet(worksheet: Worksheet): Map<String, String>? {

        return null
    }*/


    private fun parseSummaryOutput(worksheet: Worksheet): SummaryOutput {
        val addrTestCaseName = ExcelAddress("A1")
        val cell: XSSFCell = worksheet.cell(addrTestCaseName)
        val title = Excel.getCellValue(cell)

        val execSummary = readExecutionSummary(worksheet)
        val summaryOutput = SummaryOutput()
        summaryOutput.title = title
        summaryOutput.execSummary = execSummary
        return summaryOutput
    }

    private fun readExecutionSummary(worksheet: Worksheet): Map<String, String> {
        val cols: List<Char> = listOf('B', 'C')
        val map: MutableMap<String, String> = mutableMapOf()
        // summary rows range (2..12)
        (2..12).forEach { row ->
            val key = Excel
                .getCellValue(worksheet.cell(ExcelAddress("${cols[0]}$row")))
            val value = Excel
                .getCellValue(worksheet.cell(ExcelAddress("${cols[1]}$row")))
            map[key] = value
        }
        return map
    }

    private fun readScenarioOutput(worksheet: Worksheet): ScenarioOutput? {
        val scenarioOutput = parseStepResults(worksheet)
        val scenarioName = worksheet.name
        scenarioOutput.scenarioName = scenarioName
        val meta = TestScenarioMeta.newInstance(worksheet)
        val projects = getProjectList(meta)
        scenarioOutput.projects = projects
        val cell = worksheet.cell(ExcelAddress("L2"))
        if (cell != null) {
            val scenarioResult = Excel.getCellValue(cell)
            scenarioOutput.scenarioSummaryMap = parseScenarioResult(scenarioResult)
        }
        return scenarioOutput
    }

    private fun getProjectList(meta: TestScenarioMeta): MutableList<ProjectInfo> {
        val projectList = mutableListOf<ProjectInfo>()
        val projects = StringUtils.split(meta.project, "\n") ?: return projectList
        projects.forEach { projectKey ->

            val key = StringUtils.substringBetween(projectKey, "[", "]")
            val projectProfile = StringUtils.substringBefore(key, ":")
            val project = StringUtils.substringAfter(key, ":")
            val projectInfo = ProjectInfo(project)
            if (IntegrationManager.isValidServer(projectProfile)) {
                projectInfo.server = projectProfile
                projectInfo.profile = projectProfile
            }
            projectInfo.features = parseRefValues(meta.featureRef, projectProfile)
            projectInfo.testIds = parseRefValues(meta.testRef, projectProfile)
            projectInfo.description = meta.description
            projectInfo.author = meta.author
            projectList.add(projectInfo)
        }
        return projectList
    }

    private fun parseRefValues(metaCell: String, projectProfile: String?): MutableList<String> {
        val refValues = mutableListOf<String>()
        val cellValues = StringUtils.split(metaCell, "\n")
        cellValues.forEach { value ->
            val ref = StringUtils.substringBetween(value, "[", "]")
            if (StringUtils.substringBefore(ref, ":") == projectProfile) {
                val refValue = StringUtils.substringAfter(ref, ":")
                refValues.add(refValue)
            }
        }
        return refValues
    }

    private fun parseStepResults(worksheet: Worksheet): ScenarioOutput {

        val scenarioOutput = ScenarioOutput()
        val lastCommandRow = worksheet.findLastDataRow(ExcelConfig.ADDR_COMMAND_START)
        val startRowIndex = ExcelConfig.ADDR_COMMAND_START.rowStartIndex!! + 1
        val area = ExcelArea(worksheet,
                             ExcelAddress("" + ExcelConfig.COL_TEST_CASE + startRowIndex + ":" +
                                          ExcelConfig.COL_REASON + lastCommandRow), false)

        val steps = mutableMapOf<Int, List<String>>()

        var i = 0
        var rowNumber = startRowIndex
        var description = ""
        var activity = ""
        while (i < area.wholeArea.size) {
            val step = mutableListOf<String>()
            val row = area.wholeArea[i]

            if (getCellValue(row[0]).isNotBlank()) {
                activity = getCellValue(row[0])
            }
            if (getCellValue(row[1]).isNotBlank()) {
                description = getCellValue(row[1])
            }
            if (getCellValue(row[13]).contains("FAIL")) {
                scenarioOutput.isFailed = true
                row.forEach { cell -> step.add(getCellValue(cell)) }
                steps[rowNumber] = step
            }
            if (step.isNotEmpty()) {
                step.set(0, activity)
                step.set(1, description)
            }
            rowNumber++
            i++
        }
        scenarioOutput.testSteps = steps
        return scenarioOutput
    }

    private fun parseScenarioResult(scenarioResult: String?): MutableMap<String, String> {
        val scenarioSummaryMap = mutableMapOf<String, String>()
        val lines: List<String> = scenarioResult!!.split("\n")
        var n: String
        for (line in lines) {
            n = StringUtils.removeEnd(line, "\r")
            if (StringUtils.isNotEmpty(n)) {

                val attrs = n.split(":")
                val key = StringUtils.trim(attrs[0])
                val value = StringUtils.trim(attrs[1])
                when (key) {
                    "Run From"  -> scenarioSummaryMap["runFrom"] = value
                    "Run User"  -> scenarioSummaryMap["runUser"] = value
                    "Time Span" -> scenarioSummaryMap["timeSpan"] = value
                    "Duration"  -> scenarioSummaryMap["duration"] = value
                    "Steps"     -> scenarioSummaryMap["steps"] = value
                    "Executed"  -> scenarioSummaryMap["executed"] = value
                    "PASS"      -> scenarioSummaryMap["passed"] = value
                    "FAIL"      -> scenarioSummaryMap["failed"] = value
                }
            }
        }
        return scenarioSummaryMap
    }

    override fun toString(): String {
        return "ExcelOutput(summary=$summary, scenarios=$scenarios)"
    }
}