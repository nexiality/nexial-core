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

package org.nexial.core.excel;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelConfig.StyleConfig;
import org.nexial.core.excel.ExcelConfig.StyleDecorator;

import static java.io.File.separator;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.core.excel.ExcelConfig.DEF_CHAR_WIDTH;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.*;

public class MergingNewExecutionSummarySheet {
    private Map<String, String> testExecutionData = TextUtils.toMap(
        "=",
        "run from=MACHINE1.LOCAL (x86_64 Mac OS X 10.12.3)",
        "run by=me_and_myself",
        "time span=02/16/2017 00:35:52 - 02/16/2017 00:35:58",
        "duration=00:00:34.2",
        "total steps=84",
        "passed=83 (98.81%)",
        "failed=1 (1.19%)",
        "nexial version=1.02.5041",
        "java version=1.8.2012",
        "log=Click here");
    private Map<String, String> userData = TextUtils.toMap(
        "=",
        "environment=QA",
        "release=2017 Q4 YTD",
        "sprint=Sprint 17",
        "User Type=beta1",
        "Browser=chrome",
        "NGP version=1.16.2049");

    @Test
    public void testMerging() throws Exception {
        String originalFixture = "/org/nexial/core/excel/MergingNewExecutionSummarySheet.xlsx";
        String originalFixtureLocation = ResourceUtils.getResourceFilePath(originalFixture);
        System.out.println("originalFixtureLocation = " + originalFixtureLocation);
        File originalFixtureFile = new File(originalFixtureLocation);

        File fixtureFile = new File(originalFixtureFile.getParentFile().getAbsolutePath() + separator + "test.xlsx");
        System.out.println("fixtureFile = " + fixtureFile);

        FileUtils.copyFile(originalFixtureFile, fixtureFile);

        Excel fixture = new Excel(fixtureFile, false);
        XSSFWorkbook workbook = fixture.getWorkbook();
        Worksheet summary = fixture.worksheet("#summary", true);
        workbook.setSheetOrder(summary.getName(), 0);
        workbook.setSelectedTab(0);
        workbook.setActiveSheet(0);

        // title
        XSSFCell mergedCell = mergeCell(new ExcelArea(summary, new ExcelAddress("A1:J1"), false),
                                        "Execution Summary",
                                        EXEC_SUMM_TITLE);
        mergedCell.getRow().setHeightInPoints(21f);

        int startRow = 2;
        createTestExecutionSection(summary, startRow, "Test Execution", testExecutionData);

        startRow += testExecutionData.size();
        createTestExecutionSection(summary, startRow, "User Data", userData);

        startRow += userData.size();
        createExceptionSection(summary, startRow, "Fatal Error", new Exception("Hello World"));

        startRow += 2;
        createCell(summary, new ExcelAddress("A" + startRow), "scenario", EXEC_SUMM_HEADER);
        mergeCell(new ExcelArea(summary, new ExcelAddress("B" + startRow + ":C" + startRow), false),
                  "user data",
                  EXEC_SUMM_HEADER);
        createCell(summary, new ExcelAddress("D" + startRow), "activity", EXEC_SUMM_HEADER);
        createCell(summary, new ExcelAddress("E" + startRow), "start date/time", EXEC_SUMM_HEADER);
        createCell(summary, new ExcelAddress("F" + startRow), "duration (ms)", EXEC_SUMM_HEADER);
        createCell(summary, new ExcelAddress("G" + startRow), "total", EXEC_SUMM_HEADER);
        createCell(summary, new ExcelAddress("H" + startRow), "pass", EXEC_SUMM_HEADER);
        createCell(summary, new ExcelAddress("I" + startRow), "fail", EXEC_SUMM_HEADER);
        createCell(summary, new ExcelAddress("J" + startRow), "success %", EXEC_SUMM_HEADER);

        startRow += 1;
        createCell(summary, new ExcelAddress("A" + startRow), "base_showcase", EXEC_SUMM_SCENARIO);
        createCell(summary, new ExcelAddress("B" + startRow), "browser", EXEC_SUMM_DATA_NAME);
        createCell(summary, new ExcelAddress("C" + startRow), "firefox", EXEC_SUMM_DATA_VALUE);

        startRow += 1;
        createCell(summary, new ExcelAddress("B" + startRow), "user", EXEC_SUMM_DATA_NAME);
        createCell(summary, new ExcelAddress("C" + startRow), "nobody@nowhere.com", EXEC_SUMM_DATA_VALUE);

        startRow += 1;
        createCell(summary, new ExcelAddress("D" + startRow), "activity #1", EXEC_SUMM_ACTIVITY);
        createCell(summary, new ExcelAddress("E" + startRow), "2016/11/16 14:13:19", EXEC_SUMM_TIMESPAN);
        createCell(summary, new ExcelAddress("F" + startRow), "00:00:14", EXEC_SUMM_DURATION);
        createCell(summary, new ExcelAddress("G" + startRow), "50", EXEC_SUMM_TOTAL);
        createCell(summary, new ExcelAddress("H" + startRow), "32", EXEC_SUMM_PASS);
        createCell(summary, new ExcelAddress("I" + startRow), "18", EXEC_SUMM_FAIL);
        createCell(summary, new ExcelAddress("J" + startRow), "64.00%", EXEC_SUMM_NOT_SUCCESS);

        // 56 = 200*12/(11*3.8961)
        // 9000 = 211
        // 8800 = 206
        // 8600 = 203
        // 8580 = 201
        // 8565 = 201 / 32.67
        // 8565/32.67 *32.50
        // 262.1671
        double cellWidthMultiplier = 262.1671;
        XSSFSheet summarySheet = summary.getSheet();
        summarySheet.setColumnWidth(0, (int) (cellWidthMultiplier * 32.50));
        summarySheet.setColumnWidth(1, (int) (cellWidthMultiplier * 15.33));
        summarySheet.setColumnWidth(2, (int) (cellWidthMultiplier * 15.33));
        summarySheet.setColumnWidth(3, (int) (cellWidthMultiplier * 21.83));
        summarySheet.setColumnWidth(4, (int) (cellWidthMultiplier * 21.83));
        summarySheet.setColumnWidth(5, (int) (cellWidthMultiplier * 21.83));
        summarySheet.setColumnWidth(6, (int) (cellWidthMultiplier * 11.17));
        summarySheet.setColumnWidth(7, (int) (cellWidthMultiplier * 11.17));
        summarySheet.setColumnWidth(8, (int) (cellWidthMultiplier * 11.17));
        summarySheet.setColumnWidth(9, (int) (cellWidthMultiplier * 11.17));

        fixture.save();

        //System.out.println("opening " + fixtureFile.getAbsolutePath());
        //Excel.openExcel(fixtureFile);
    }

    protected void createExceptionSection(Worksheet sheet, int rowNum, String title, Throwable exception) {
        createCell(sheet, new ExcelAddress("A" + rowNum), title, EXEC_SUMM_DATA_HEADER);

        String errorMessage = ExceptionUtils.getMessage(exception);
        String rootCauseMessage = ExceptionUtils.getRootCauseMessage(exception);
        String[] stackTrace = ExceptionUtils.getStackFrames(exception);
        StringBuilder error = new StringBuilder();
        if (StringUtils.isNotBlank(errorMessage)) { error.append("ERROR: ").append(errorMessage).append("\n"); }
        if (StringUtils.isNotBlank(rootCauseMessage)) {
            error.append("ROOT CAUSE: ").append(rootCauseMessage).append("\n");
        }
        for (String errorDetail : stackTrace) {
            if (StringUtils.contains(errorDetail, "nexial")) {
                error.append(errorDetail).append("\n");
            } else {
                break;
            }
        }

        errorMessage = StringUtils.trim(error.toString());

        mergeCell(new ExcelArea(sheet, new ExcelAddress("B" + rowNum + ":J" + rowNum), false),
                  errorMessage,
                  EXEC_SUMM_EXCEPTION);
    }

    protected void createTestExecutionSection(Worksheet sheet,
                                              int startRowNum,
                                              String title,
                                              Map<String, String> data) {
        createCell(sheet, new ExcelAddress("A" + startRowNum), title, EXEC_SUMM_DATA_HEADER);

        List<String> names = CollectionUtil.toList(data.keySet());
        for (int i = 0; i < names.size(); i++) {
            createCell(sheet, new ExcelAddress("B" + (i + startRowNum)), names.get(i), EXEC_SUMM_DATA_NAME);
            createCell(sheet, new ExcelAddress("C" + (i + startRowNum)), data.get(names.get(i)), EXEC_SUMM_DATA_VALUE);
        }
    }

    private XSSFCell createCell(Worksheet worksheet, ExcelAddress addr, String content, StyleConfig styleConfig) {
        if (worksheet == null) { return null; }
        if (addr == null || StringUtils.isBlank(addr.getAddr())) { return null; }

        worksheet.setRowValues(addr, Collections.singletonList(content));
        XSSFCell cell = worksheet.cell(addr);
        if (cell == null) { return null; }

        cell.setCellStyle(StyleDecorator.generate(worksheet, styleConfig));
        cell.getRow().setHeightInPoints(21f);

        return cell;
    }

    private XSSFCell mergeCell(ExcelArea mergeArea, String mergedContent, StyleConfig styleConfig) {
        if (mergeArea == null || mergeArea.getAddr().getColumnCount() < 1 || mergeArea.getAddr().getRowCount() < 1) {
            return null;
        }

        Worksheet worksheet = mergeArea.getWorksheet();
        if (worksheet == null) { return null; }

        XSSFSheet sheet = worksheet.getSheet();
        if (sheet == null) { return null; }

        ExcelAddress addr = mergeArea.getAddr();
        int starRowIdx = addr.getRowStartIndex();
        int endRowIdx = addr.getRowEndIndex();
        int startColumnIdx = addr.getColumnStartIndex();
        int endColumnIndex = addr.getColumnEndIndex();

        sheet.addMergedRegion(new CellRangeAddress(starRowIdx, endRowIdx, startColumnIdx, endColumnIndex));

        XSSFRow row = sheet.getRow(addr.getRowStartIndex());
        if (row == null) { row = sheet.createRow(addr.getRowStartIndex()); }

        XSSFCell cellMerge = row.getCell(addr.getColumnStartIndex(), CREATE_NULL_AS_BLANK);
        if (cellMerge == null) { return null; }

        if (StringUtils.isNotEmpty(mergedContent)) { cellMerge.setCellValue(mergedContent); }
        if (styleConfig != null) { cellMerge.setCellStyle(StyleDecorator.generate(worksheet, styleConfig)); }
        if (StringUtils.isNotEmpty(mergedContent)) {
            int mergedWidth = 0;
            for (int j = startColumnIdx; j < endColumnIndex + 1; j++) { mergedWidth += sheet.getColumnWidth(j); }
            int charPerLine = (int) ((mergedWidth - DEF_CHAR_WIDTH) * addr.getRowCount() /
                                     (DEF_CHAR_WIDTH * FONT_HEIGHT_DEFAULT));

            int lineCount = StringUtils.countMatches(mergedContent, "\n") + 1;
            String[] lines = StringUtils.split(mergedContent, "\n");
            if (ArrayUtils.isEmpty(lines)) { lines = new String[]{mergedContent}; }
            for (String line : lines) { lineCount += Math.ceil((double) StringUtils.length(line) / charPerLine) - 1; }

            // lineCount should always be at least 1. otherwise this row will not be rendered with height 0
            if (lineCount < 1) { lineCount = 1; }

            worksheet.setHeight(cellMerge, lineCount);
        }

        return cellMerge;
    }
}
