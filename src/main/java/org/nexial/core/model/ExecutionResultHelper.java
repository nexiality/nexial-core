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

package org.nexial.core.model;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.nexial.core.ExecutionThread;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecutionLogger;

import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.core.NexialConst.Data.CMD_VERBOSE;
import static org.nexial.core.NexialConst.Data.SHEET_MERGED_DATA;
import static org.nexial.core.NexialConst.MERGE_OUTPUTS;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.*;

public final class ExecutionResultHelper {
    private ExecutionResultHelper() { }

    public static Excel updateOutputDataSheet(ExecutionContext context, Excel outputFile) {
        if (context == null) { return null; }

        XSSFSheet dataSheet = outputFile.getWorkbook().getSheet(SHEET_MERGED_DATA);

        final int[] currentRowIndex = {0};

        dataSheet.forEach(dataRow -> {
            XSSFRow row = dataSheet.getRow(currentRowIndex[0]++);
            if (row == null) { return; }

            XSSFCell cellName = row.getCell(0, CREATE_NULL_AS_BLANK);
            String name = Excel.getCellValue(cellName);

            XSSFCell cellValue = row.getCell(1, CREATE_NULL_AS_BLANK);

            if (context.hasData(name)) {
                // use try-catch here in case we run into NPE or RTE when substituting `name`
                String value;
                try {
                    value = CellTextReader.readValue(context.getStringData(name));
                } catch (RuntimeException e) {
                    String error;
                    if (e.getCause() != null) {
                        error = e.getCause().getMessage();
                    } else {
                        error = e.getMessage();
                    }
                    ConsoleUtils.error("Error while evaluating '" + name + "': " + error);

                    value = name;
                }

                // this might not be fully expanded/substituted as the above try-catch block is prevent RTE to surface
                cellValue.setCellValue(value);
                // cellValue.setCellStyle(styleTestDataValue);
            }
        });

        // save output file with updated data
        // (2018/10/18,automike): omit saving here because this file will be saved later anyways
        // outputFile.save();

        return outputFile;
    }

    protected static void writeTestScenarioResult(Worksheet worksheet, ExecutionSummary executionSummary)
        throws IOException {
        XSSFSheet excelSheet = worksheet.getSheet();
        excelSheet.getWorkbook().setMissingCellPolicy(CREATE_NULL_AS_BLANK);

        int lastRow = worksheet.findLastDataRow(ADDR_COMMAND_START);
        lastRow = handleNestedMessages(worksheet, executionSummary, lastRow);
        mergeVerboseOutput(worksheet, lastRow);

        for (int i = ADDR_COMMAND_START.getRowStartIndex(); i < lastRow; i++) {
            ExecutionResultHelper.setMinHeight(worksheet, excelSheet.getRow(i));
        }

        String logId = ExecutionLogger.justFileName(worksheet.getFile()) + "|" + worksheet.getName();
        ConsoleUtils.log(logId, "saving test scenario");
        save(worksheet, executionSummary);
    }

    protected static void setMinHeight(Worksheet worksheet, XSSFRow row) {
        if (row == null) { return; }

        if (row.getLastCellNum() < COL_IDX_RESULT) {
            worksheet.setMinHeight(row.getCell(0), 1);
            return;
        }

        int maxParamLine = 0;
        for (int i = COL_IDX_PARAMS_START; i < COL_IDX_PARAMS_END; i++) {
            maxParamLine = NumberUtils.max(maxParamLine,
                                           StringUtils.countMatches(Excel.getCellValue(row.getCell(i)), '\n'));
        }

        XSSFCell cellDescription = row.getCell(COL_IDX_DESCRIPTION);
        int numOfLines = NumberUtils.max(
            StringUtils.countMatches(Excel.getCellValue(row.getCell(COL_IDX_TESTCASE)), '\n'),
            StringUtils.countMatches(Excel.getCellValue(cellDescription), '\n'),
            maxParamLine,
            StringUtils.countMatches(Excel.getCellValue(row.getCell(COL_IDX_FLOW_CONTROLS)), '\n')) + 1;
        worksheet.setMinHeight(cellDescription, numOfLines);
    }

    protected static int handleNestedMessages(Worksheet worksheet, ExecutionSummary executionSummary, int lastRow) {
        XSSFSheet excelSheet = worksheet.getSheet();

        Map<TestStepManifest, List<NestedMessage>> nestMessages = executionSummary.getNestMessages();
        if (MapUtils.isEmpty(nestMessages)) { return lastRow; }

        // prepare to print nested messages
        int forwardRowsBy = 0;
        XSSFCellStyle style = worksheet.getStyle(STYLE_MESSAGE);
        XSSFCellStyle resultStyle = StyleDecorator.generate(worksheet, RESULT);
        XSSFCellStyle linkStyle = StyleDecorator.generate(worksheet, SCREENSHOT);

        Set<TestStepManifest> testStepsWithNestMessages = nestMessages.keySet();
        for (TestStepManifest step : testStepsWithNestMessages) {
            if (StringUtils.equals(step.getCommandFQN(), CMD_VERBOSE)) { continue; }

            List<NestedMessage> nestedMessages = nestMessages.get(step);
            int messageCount = CollectionUtils.size(nestedMessages);
            if (messageCount < 1) { continue; }

            int currentRow = step.getRowIndex() + 1 + forwardRowsBy;
            // +1 if lastRow is the same as currentRow.  Otherwise shiftRow on a single row block causes problem for createRow (later on).
            worksheet.shiftRows(currentRow, lastRow + (currentRow == lastRow ? 1 : 0), messageCount);

            for (int i = 0; i < messageCount; i++) {
                NestedMessage nestedMessage = nestedMessages.get(i);
                String message = nestedMessage.getMessage();
                String resultMessage = nestedMessage.getResultMessage();

                XSSFRow row = excelSheet.createRow(currentRow + i);
                XSSFCell cell = row.createCell(COL_IDX_MERGE_RESULT_START);
                cell.setCellValue(message);
                cell.setCellStyle(style);

                if (StringUtils.isNotBlank(resultMessage)) {
                    cell = row.createCell(COL_IDX_RESULT);
                    cell.setCellValue(resultMessage);
                    cell.setCellStyle(resultStyle);
                }

                if (nestedMessage instanceof NestedScreenCapture) {
                    NestedScreenCapture screenCapture = (NestedScreenCapture) nestedMessage;
                    String link = screenCapture.getLink();
                    if (StringUtils.isNotBlank(link)) {
                        XSSFCell linkCell = Excel.setHyperlink(row.createCell(COL_IDX_CAPTURE_SCREEN),
                                                               link,
                                                               screenCapture.getLabel());
                        linkCell.setCellStyle(linkStyle);
                    }
                }

            }

            lastRow += messageCount;
            forwardRowsBy += messageCount;
        }

        return lastRow;
    }

    protected static void mergeVerboseOutput(Worksheet worksheet, int lastRow) {
        XSSFSheet excelSheet = worksheet.getSheet();

        // scan for verbose() or similar commands where merging should be done
        int startRow = ADDR_PARAMS_START.getRowStartIndex();
        for (int i = startRow; i < lastRow; i++) {
            XSSFRow row = excelSheet.getRow(i);
            if (row == null) { continue; }

            XSSFCell cellTarget = row.getCell(COL_IDX_TARGET);
            XSSFCell cellCommand = row.getCell(COL_IDX_COMMAND);
            String command = StringUtils.defaultIfBlank(Excel.getCellValue(cellTarget), "") + "." +
                             StringUtils.defaultIfBlank(Excel.getCellValue(cellCommand), "");
            if (MERGE_OUTPUTS.contains(command)) { mergeOutput(worksheet, excelSheet, row, i); }
        }
    }

    protected static void mergeOutput(Worksheet worksheet, XSSFSheet excelSheet, XSSFRow row, int rowIndex) {
        XSSFCell cellMerge = row.getCell(COL_IDX_MERGE_RESULT_START);
        if (cellMerge == null) { return; }

        // determine aggregated column width from 'param 1' to 'flow control'
        int mergedWidth = 0;
        for (int j = COL_IDX_MERGE_RESULT_START; j < COL_IDX_MERGE_RESULT_END + 1; j++) {
            mergedWidth += worksheet.getSheet().getColumnWidth(j);
        }

        int charPerLine = (int) ((mergedWidth - DEF_CHAR_WIDTH) / (DEF_CHAR_WIDTH * MSG.getFontHeight()));

        // make sure we aren't create merged region on existing merged region
        boolean alreadyMerged = false;
        List<CellRangeAddress> mergedRegions = excelSheet.getMergedRegions();
        if (CollectionUtils.isNotEmpty(mergedRegions)) {
            for (CellRangeAddress rangeAddress : mergedRegions) {
                int firstRow = rangeAddress.getFirstRow();
                int lastRow = rangeAddress.getLastRow();
                int firstColumn = rangeAddress.getFirstColumn();
                int lastColumn = rangeAddress.getLastColumn();

                if (firstRow <= rowIndex && lastRow >= rowIndex &&
                    firstColumn <= COL_IDX_MERGE_RESULT_START && lastColumn >= COL_IDX_MERGE_RESULT_END) {
                    alreadyMerged = true;
                    break;
                }
            }
        }

        if (!alreadyMerged) {
            excelSheet.addMergedRegion(
                new CellRangeAddress(rowIndex, rowIndex, COL_IDX_MERGE_RESULT_START, COL_IDX_MERGE_RESULT_END));
        }

        if (cellMerge.getCellTypeEnum() == STRING) { cellMerge.setCellStyle(worksheet.getStyle(STYLE_MESSAGE)); }

        String mergedContent = Excel.getCellValue(cellMerge);
        cellMerge.setCellValue(mergedContent);

        Excel.adjustCellHeight(worksheet, cellMerge, charPerLine);
    }

    protected static void save(Worksheet worksheet, ExecutionSummary executionSummary) throws IOException {
        XSSFCell summaryCell = worksheet.cell(ADDR_SCENARIO_EXEC_SUMMARY);
        if (summaryCell != null) {
            if (executionSummary.getEndTime() == 0) { executionSummary.setEndTime(System.currentTimeMillis()); }
            summaryCell.setCellValue(executionSummary.toString());
        }

        XSSFCell descriptionCell = worksheet.cell(ADDR_SCENARIO_DESCRIPTION);
        if (descriptionCell != null) {
            ExecutionContext context = ExecutionThread.get();
            if (context != null) {
                descriptionCell.setCellValue(context.replaceTokens(Excel.getCellRawValue(descriptionCell), true));
            }
        }

        worksheet.getSheet().setZoom(100);
        worksheet.save();
    }
}
