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
package org.nexial.core.model

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.apache.poi.ss.usermodel.CellType.STRING
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.*
import org.nexial.core.CommandConst.CMD_REPEAT_UNTIL
import org.nexial.core.CommandConst.CMD_SECTION
import org.nexial.core.CommandConst.CMD_VERBOSE
import org.nexial.core.CommandConst.shouldMergeCommandParams
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.Data.*
import org.nexial.core.NexialConst.LogMessage.SAVING_TEST_SCENARIO
import org.nexial.core.NexialConst.MAX_VERBOSE_CHAR
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.excel.ExcelStyleHelper
import org.nexial.core.excel.ext.CellTextReader
import org.nexial.core.logs.ExecutionLogger
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.MessageUtils
import java.io.IOException
import java.util.function.Consumer

class ExecutionResultHelper(private val allSteps: List<TestStep>, val worksheet: Worksheet,
                            val executionSummary: ExecutionSummary) {

    fun updateScenarioResults() {
        worksheet.sheet.workbook.missingCellPolicy = CREATE_NULL_AS_BLANK
        var currentRow = ADDR_COMMAND_START.rowStartIndex
        var lastRow = worksheet.findLastDataRow(ADDR_COMMAND_START)

        for (index in allSteps.indices) {
            val testStep: TestStep = allSteps[index]

            var pair = writeTestResults(testStep, false, currentRow, lastRow)
            currentRow = pair.first
            lastRow = pair.second
            currentRow++

            if (testStep.commandFQN == CMD_REPEAT_UNTIL) {
                val commandRepeater = testStep.getCommandRepeater()
                val repeatUntilSteps = commandRepeater.steps
                val stepCount = commandRepeater.stepCount
                for (j in 0 until stepCount) {
                    pair = writeTestResults(repeatUntilSteps[j], true, currentRow, lastRow)
                    currentRow = pair.first + 1
                    lastRow = pair.second
                }
            }
        }

        formatDescriptionCell(ADDR_COMMAND_START.rowStartIndex, lastRow)
        // update execution summary in the scenario sheet
        writeTestScenarioResult(worksheet, executionSummary)
    }

    private fun formatDescriptionCell(startRow: Int, lastRow: Int): Int {
        val excelSheet = worksheet.sheet
        var i = startRow
        while (true) {
            if (i > lastRow) break
            i += formatDescriptionCell(excelSheet, i, "", lastRow)
            i++
        }
        return lastRow
    }

    private fun formatDescriptionCell(sheet: XSSFSheet, startRow: Int, prefix: String, lastRow: Int): Int {
        if (lastRow < startRow) return 0
        val row = sheet.getRow(startRow) ?: return 0

        var prefix1 = prefix

        val targetCell = row.getCell(COL_IDX_TARGET)
        val commandCell = row.getCell(COL_IDX_COMMAND)
        if (targetCell == null || commandCell == null) return 0

        val target = targetCell.stringCellValue
        val command = commandCell.stringCellValue

        if (target == "" || command == "") return 0

        when ("$target.$command") {
            CMD_REPEAT_UNTIL -> {
                val indent = if (StringUtils.containsAny(prefix1, REPEAT_DESCRIPTION_PREFIX,
                                                         SECTION_DESCRIPTION_PREFIX)) "  " else ""
                prefix1 += if (StringUtils.contains(prefix1,
                                                    REPEAT_DESCRIPTION_PREFIX)) "" else REPEAT_DESCRIPTION_PREFIX
                ExcelStyleHelper.formatDescriptionCell(row.getCell(COL_IDX_DESCRIPTION), prefix1)
                prefix1 = indent + prefix1
                var numOfSteps = Integer.parseInt(row.getCell(COL_IDX_PARAMS_START).stringCellValue)
                var j = 0
                while (true) {
                    if (j == numOfSteps) break
                    prefix1 = if (j == 0) {
                        StringUtils.replace(prefix1, REPEAT_DESCRIPTION_PREFIX, REPEAT_CHECK_DESCRIPTION_PREFIX)
                    } else {
                        StringUtils.replace(prefix1, REPEAT_CHECK_DESCRIPTION_PREFIX, REPEAT_DESCRIPTION_PREFIX)
                    }
                    val num = formatDescriptionCell(sheet, startRow + j + 1, prefix1, lastRow)
                    numOfSteps += num
                    j += num
                    j++
                }
                return numOfSteps
            }

            CMD_SECTION      -> {
                val indent = if (StringUtils.containsAny(prefix1, REPEAT_DESCRIPTION_PREFIX,
                                                         SECTION_DESCRIPTION_PREFIX)) "  " else ""
                prefix1 += if (StringUtils.contains(prefix1,
                                                    SECTION_DESCRIPTION_PREFIX)) "" else SECTION_DESCRIPTION_PREFIX
                ExcelStyleHelper.formatDescriptionCell(row.getCell(COL_IDX_DESCRIPTION), prefix1)
                prefix1 = indent + prefix1
                var numOfSteps = Integer.parseInt(row.getCell(COL_IDX_PARAMS_START).stringCellValue)
                var j = 0
                while (true) {
                    if (j == numOfSteps) break
                    val num = formatDescriptionCell(sheet, startRow + j + 1, prefix1, lastRow)
                    numOfSteps += num
                    j += num
                    j++
                }

                return numOfSteps
            }

            else             -> {
                ExcelStyleHelper.formatDescriptionCell(row.getCell(COL_IDX_DESCRIPTION), prefix1)
                return 0
            }
        }
    }

    /**
     * This method refills macro expanded and transform to section commands.
     * Also, merge verbose output and add nested messaged to output excel.
     *
     * @param testStep current teststep from test scenario
     * @param isRepeatUntil is it part of repeat until loop command
     * @param currentRowIdx current row index of worksheet to write teststep
     * @param lastRow last row number of the worksheet
     * @constructor Creates an empty group.
     */
    private fun writeTestResults(testStep: TestStep, isRepeatUntil: Boolean, currentRowIdx: Int,
                                 lastRow: Int): Pair<Int, Int> {
        // look for base.macro(file,sheet,name) OR base.macroEnhanced(macro,input,output) - expand into output excel
        var lastDataRow: Int = lastRow
        var currentRow = currentRowIdx
        mergeVerboseOutput(testStep, currentRow)

        if (CollectionUtils.isNotEmpty(testStep.nestedTestResults)) {
            // return current row and lastRow...
            val pair = handleNestedMessages(worksheet, testStep, currentRow, lastDataRow)
            currentRow = pair.first
            lastDataRow = pair.second
        }

        // go ahead if command base.macro() or base.macroFlex()
        if (!testStep.isMacroExpander()) return Pair(currentRow, lastDataRow)

        val macroExecutor = testStep.getMacroExecutor() ?: return Pair(currentRow, lastDataRow)
        val macroSteps = macroExecutor.testSteps
        if (CollectionUtils.isNotEmpty(macroSteps)) {
            val stepSize = macroSteps.size
            if (lastDataRow == currentRow) lastDataRow++

            // replace macro invocation step with expanded macro test steps
            // replace existing macro() command to section() command
            val macroCommandRow = currentRow
            var nestedSteps = 0
            // added to check if any repeat until contains section
            // might think about different approach
            var repeatUntilStartIndex = -1
            var repeatUntilEndIndex = -1

            for (j in 0 until stepSize) {
                val macroStep = macroSteps[j]
                if (macroStep.commandFQN == CMD_REPEAT_UNTIL && macroStep.commandRepeater != null) {
                    nestedSteps = macroStep.commandRepeater.stepCount
                    repeatUntilStartIndex = j
                    repeatUntilEndIndex = j + nestedSteps
                }

                // ignore sections inside repeatuntil
                if (macroStep.commandFQN == CMD_SECTION && j !in repeatUntilStartIndex..repeatUntilEndIndex) {
                    nestedSteps += macroStep.formatSkippedSections(macroSteps, j, false)
                }

                // check repeatuntil inside macro ended or not
                if (repeatUntilStartIndex != -1 && j > repeatUntilEndIndex) {
                    repeatUntilStartIndex = -1
                    repeatUntilEndIndex = -1
                }
                copyMacroSteps(macroStep, currentRow++ + 1, lastDataRow, isRepeatUntil)
                mergeVerboseOutput(macroStep, currentRow)

                lastDataRow++

                val pair = handleNestedMessages(worksheet, macroStep, currentRow, lastDataRow)
                currentRow = pair.first
                lastDataRow = pair.second
            }
            // refactor macro to section command
            refactorMacroCommand(testStep, stepSize - nestedSteps, macroCommandRow, isRepeatUntil)
        }
        // close excel here
        macroExecutor.macroExcel?.close()
        return Pair(currentRow, lastDataRow)
    }

    private fun refactorMacroCommand(testStep: TestStep, stepSize: Int, commandRow: Int, partOfRepeatUntil: Boolean) {
        val commandParts = StringUtils.split(CMD_SECTION, ".")
        testStep.setTarget(commandParts[0])
        testStep.setCommand(commandParts[1])
        testStep.setParams(listOf("$stepSize", "", ""))

        val row: XSSFRow = worksheet.sheet.getRow(commandRow)
        val cell = row.getCell(COL_IDX_DESCRIPTION)
        if (!partOfRepeatUntil) ExcelStyleHelper.formatSectionDescription(worksheet, cell)

        row.getCell(COL_IDX_TARGET).setCellValue(commandParts[0])
        row.getCell(COL_IDX_COMMAND).setCellValue(commandParts[1])
        row.getCell(COL_IDX_PARAMS_START).setCellValue("$stepSize")
        row.getCell(COL_IDX_PARAMS_START + 1).setCellValue("")
        row.getCell(COL_IDX_PARAMS_START + 2).setCellValue("")
    }

    private fun copyMacroSteps(macroStep: TestStep?, startRow: Int, endRowIndex: Int, isRepeatUntil: Boolean) {
        worksheet.shiftRows(startRow, endRowIndex, 1)
        val sheet = worksheet.sheet
        val workbook = sheet.workbook
        val oldRow = macroStep!!.getRow() ?: return
        val newRow = sheet.getRow(startRow) ?: sheet.createRow(startRow)

        for (i in 0 until COL_IDX_REASON) {
            var newCell = newRow.getCell(i)
            val oldCell = oldRow[i] ?: continue

            // macro name changed to empty for activity column
            if (i == COL_IDX_TESTCASE) oldCell.setCellValue("")

            if (newCell == null) newCell = newRow.createCell(i)

            newCell.cellStyle = ExcelStyleHelper.cloneCellStyle(oldCell.cellStyle, workbook)

            // add comment to cells if any
            val cellComment = oldCell.cellComment
            if (cellComment != null) Excel.createComment(newCell, cellComment.string.string, cellComment.author)

            /*// cell copy policy to copy value and formula
            val cellCopyPolicy = CellCopyPolicy().createBuilder().copyHyperlink(false).cellStyle(false).build()
            newCell.copyCellFrom(oldCell, cellCopyPolicy)*/

            // to avoid copying hyperlink
            Excel.copyCellValue(oldCell, newCell)

            if (i == COL_IDX_DESCRIPTION) formatDescriptionCell(newCell, isRepeatUntil)
        }
    }

    private fun formatDescriptionCell(cell: XSSFCell?, isRepeatUntil: Boolean) {
        if (isRepeatUntil) {
            ExcelStyleHelper.formatRepeatUntilDescription(worksheet, cell)
        } else {
            ExcelStyleHelper.formatSectionDescription(worksheet, cell)
        }
    }

    private fun handleNestedMessages(worksheet: Worksheet, testStep: TestStep, currentRow: Int,
                                     lastRow: Int): Pair<Int, Int> {
        val nestedTestResults = testStep.nestedTestResults

        if (testStep.commandFQN == CMD_VERBOSE || CollectionUtils.isEmpty(nestedTestResults)) return Pair(currentRow,
                                                                                                          lastRow)

        var lastDataRow = lastRow
        val excelSheet = worksheet.sheet

        // prepare to print nested messages
        val style = worksheet.getStyle(STYLE_MESSAGE)
        val resultStyle = ExcelStyleHelper.generate(worksheet, RESULT)
        val linkStyle = ExcelStyleHelper.generate(worksheet, SCREENSHOT)

        // add to test result
        // if (StringUtils.equals(step!!.commandFQN, CMD_VERBOSE)) continue
        var currentRowIdx = currentRow
        setMinHeight(worksheet, excelSheet.getRow(currentRow))

        val messageCount = nestedTestResults.size
        for (i in 0 until messageCount) {
            val nestedMessage = nestedTestResults[i]

            if (nestedMessage is StepOutput) {
                // step output will output to same row as test step
                addScreenshotLink(excelSheet.getRow(currentRow), linkStyle, nestedMessage)
            } else {
                currentRowIdx++
                // nested screen capture will add to new row (after test step)
                // shift rows only once by messageCount
                // if (i == 0) {
                // +1 if lastRow is the same as currentRow. Otherwise shiftRow on a single row block
                // causes problem for createRow (later on).
                worksheet.shiftRows(currentRowIdx, lastDataRow + if (currentRowIdx == lastDataRow) 1 else 0, 1)

                val rowIndex = currentRowIdx
                val row = excelSheet.createRow(rowIndex)
                var cell = row.createCell(COL_IDX_MERGE_RESULT_START)
                cell.setCellValue(nestedMessage.message)
                cell.cellStyle = style
                // Merge param1 to parma5 cell with some background color
                mergeOutput(worksheet, rowIndex)
                val resultMessage = nestedMessage.resultMessage
                if (StringUtils.isNotBlank(resultMessage)) {
                    cell = row.createCell(COL_IDX_RESULT)
                    cell.setCellValue(resultMessage)
                    cell.cellStyle = resultStyle
                }
                if (nestedMessage is NestedScreenCapture) addScreenshotLink(row, linkStyle, nestedMessage)
            }

            lastDataRow++
        }

        return Pair(currentRowIdx, lastDataRow)
    }

    private fun mergeVerboseOutput(testStep: TestStep, currentRow: Int) {
        if (shouldMergeCommandParams(testStep.commandFQN)) mergeOutput(worksheet, currentRow)
        setMinHeight(worksheet, worksheet.sheet.getRow(currentRow))
    }

    private fun mergeOutput(worksheet: Worksheet, rowIndex: Int) {
        val excelSheet = worksheet.sheet
        val row = excelSheet.getRow(rowIndex)
        val cellMerge = row.getCell(COL_IDX_MERGE_RESULT_START) ?: return

        val cellResult = row.getCell(COL_IDX_RESULT)
        // skip the SKIPPED steps
        if (cellResult != null && MessageUtils.isSkipped(cellResult.stringCellValue)) return

        // make sure we aren't create merged region on existing merged region
        var alreadyMerged = false
        val mergedRegions = excelSheet.mergedRegions
        if (CollectionUtils.isNotEmpty(mergedRegions)) {
            for (rangeAddress in mergedRegions) {
                val firstRow = rangeAddress!!.firstRow
                val lastRow = rangeAddress.lastRow
                val firstColumn = rangeAddress.firstColumn
                val lastColumn = rangeAddress.lastColumn
                if (rowIndex in firstRow..lastRow &&
                    firstColumn <= COL_IDX_MERGE_RESULT_START &&
                    lastColumn >= COL_IDX_MERGE_RESULT_END) {
                    alreadyMerged = true
                    break
                }
            }
        }
        if (!alreadyMerged) {
            excelSheet.addMergedRegion(
                CellRangeAddress(rowIndex, rowIndex, COL_IDX_MERGE_RESULT_START, COL_IDX_MERGE_RESULT_END))
        }
        if (cellMerge.cellTypeEnum == STRING) cellMerge.cellStyle = worksheet.getStyle(STYLE_MESSAGE)
        cellMerge.setCellValue(Excel.getCellValue(cellMerge))
        Excel.adjustCellHeight(worksheet, cellMerge)
    }

    private fun addScreenshotLink(row: XSSFRow, linkStyle: XSSFCellStyle?, screenCapture: NestedScreenCapture) {
        val link = screenCapture.link
        if (StringUtils.isNotBlank(link)) {
            Excel.setHyperlink(row.createCell(COL_IDX_CAPTURE_SCREEN), link, screenCapture.label).cellStyle = linkStyle
        }
    }

    private fun setMinHeight(worksheet: Worksheet, row: XSSFRow?) {
        if (row == null) return

        if (row.lastCellNum < COL_IDX_RESULT) {
            worksheet.setMinHeight(row.getCell(0), 1)
            return
        }

        var maxParamLine = 0
        for (i in COL_IDX_PARAMS_START until COL_IDX_PARAMS_END) {
            maxParamLine = NumberUtils.max(maxParamLine,
                                           StringUtils.countMatches(Excel.getCellValue(row.getCell(i)), '\n'))
        }

        val cellDescription = row.getCell(COL_IDX_DESCRIPTION)
        val numOfLines = NumberUtils.max(
            StringUtils.countMatches(Excel.getCellValue(row.getCell(COL_IDX_TESTCASE)), '\n'),
            StringUtils.countMatches(Excel.getCellValue(cellDescription), '\n'), maxParamLine,
            StringUtils.countMatches(Excel.getCellValue(row.getCell(COL_IDX_FLOW_CONTROLS)), '\n')) + 1
        worksheet.setMinHeight(cellDescription, numOfLines)
    }

    @Throws(IOException::class)
    internal fun writeTestScenarioResult(worksheet: Worksheet, executionSummary: ExecutionSummary) {
        // adjust width to fit column content
        val excelSheet = worksheet.sheet
        excelSheet.autoSizeColumn(COL_IDX_TESTCASE)
        excelSheet.autoSizeColumn(COL_IDX_DESCRIPTION)
        excelSheet.autoSizeColumn(COL_IDX_TARGET)
        excelSheet.autoSizeColumn(COL_IDX_COMMAND)
        excelSheet.autoSizeColumn(COL_IDX_FLOW_CONTROLS)
        excelSheet.autoSizeColumn(COL_IDX_ELAPSED_MS)
        excelSheet.autoSizeColumn(COL_IDX_RESULT)

        val logId: String = ExecutionLogger.justFileName(worksheet.file) + "|" + worksheet.name
        ConsoleUtils.log(logId, SAVING_TEST_SCENARIO)
        save(worksheet, executionSummary)
    }

    @Throws(IOException::class)
    private fun save(worksheet: Worksheet, executionSummary: ExecutionSummary) {
        val summaryCell = worksheet.cell(ADDR_SCENARIO_EXEC_SUMMARY)
        if (summaryCell != null) {
            if (executionSummary.endTime == 0L) executionSummary.endTime = System.currentTimeMillis()

            val summary = executionSummary.toString()
            summaryCell.setCellValue(summary)

            // XSSFCellStyle summaryStyle = worksheet.newCellStyle();
            // ExcelStyleHelper.setCellBorder(summaryStyle, BorderStyle.MEDIUM, new XSSFColor(new Color(117, 113, 113)));
            // summaryCell.setCellStyle(summaryStyle);
            worksheet.setMinHeight(summaryCell, StringUtils.countMatches(summary, '\n'))
        }

        val descriptionCell = worksheet.cell(ADDR_SCENARIO_DESCRIPTION)
        if (descriptionCell != null) {
            val context = ExecutionThread.get()
            if (context != null) {
                descriptionCell.setCellValue(context.replaceTokens(Excel.getCellRawValue(descriptionCell), true))
            }
        }
        worksheet.sheet.setZoom(100)
        worksheet.save()
    }

    companion object {
        @JvmStatic
        fun updateOutputDataSheet(context: ExecutionContext, outputFile: Excel): Excel? {
            val dataSheet = outputFile.workbook.getSheet(SHEET_MERGED_DATA)
            val currentRowIndex = intArrayOf(0)

            dataSheet.forEach(Consumer forEach@{
                val row = dataSheet.getRow(currentRowIndex[0]++) ?: return@forEach
                val cellName = row.getCell(0, CREATE_NULL_AS_BLANK)
                val name = Excel.getCellValue(cellName)
                val cellValue = row.getCell(1, CREATE_NULL_AS_BLANK)

                if (context.hasData(name)) {
                    // use try-catch here in case we run into NPE or RTE when substituting `name`
                    val value = try {
                        CellTextReader.readValue(context.getStringData(name))
                    } catch (e: RuntimeException) {
                        val error = if (e.cause != null) e.cause!!.message else e.message
                        ConsoleUtils.error("Error while evaluating '$name': $error")
                        name
                    }

                    // this might not be fully expanded/substituted as the above try-catch block is prevent RTE to surface
                    cellValue.setCellValue(StringUtils.abbreviate(value, MAX_VERBOSE_CHAR))
                }
            })

            // adjust width to fit column content
            dataSheet.autoSizeColumn(0)
            dataSheet.autoSizeColumn(1)

            // (2018/10/18,automike): omit saving here because this file will be saved later anyways
            // outputFile.save();
            return outputFile
        }
    }
}