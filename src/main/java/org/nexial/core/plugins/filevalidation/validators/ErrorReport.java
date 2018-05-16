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

package org.nexial.core.plugins.filevalidation.validators;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.filevalidation.RecordData;
import org.nexial.core.utils.OutputFileUtils;
import org.nexial.core.variable.Syspath;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.GSON;

public class ErrorReport {
    private static ExecutionContext context = ExecutionThread.get();

    public static File create(String type, RecordData recordData) {

        if (StringUtils.equalsIgnoreCase(type, "JSON")) {
            return createJSON(recordData);
        } else if (StringUtils.equalsIgnoreCase(type, "Excel")) {
            return createExcel(recordData);
        }
        return null;
    }

    private static String createCSV(RecordData recordData) {
        List<Error> errors = recordData.getErrors();
        StringBuilder sb = new StringBuilder();

        // todo: v2 implement IO Stream to write line by line
        // handle ambiguity of comma in the field value, field name and error message
        sb.append("Record Line").append(",");
        sb.append("Field Name").append(",");
        sb.append("Severity").append(",");
        sb.append("Validation Type").append(",");
        sb.append("Error Message");
        sb.append(System.getProperty("line.separator"));

        for (Error error : errors) {
            sb.append(error.getRecordLine()).append(",");
            sb.append(error.getFieldName()).append(",");
            sb.append(error.getSeverity()).append(",");
            sb.append(error.getValidationType()).append(",");
            sb.append(error.getErrorMessage());
            sb.append(System.getProperty("line.separator"));
        }
        return sb.toString();
    }

    private static String getJsonString(RecordData recordData) {
        return GSON.toJson(recordData);
    }

    private static File createExcel(RecordData recordData) {

        String outFile = new Syspath().out("fullpath") + separator +
                         OutputFileUtils.generateOutputFilename(context.getCurrentTestStep(), "xlsx");

        File outputFile = new File(outFile);
        FileOutputStream fileOutputStream = null;
        try {
            Workbook workbook = new XSSFWorkbook();
            createSummarySheet(recordData, workbook);
            createSkippedReportSheet(recordData, workbook);
            createErrorReportSheet(recordData, workbook);

            fileOutputStream = new FileOutputStream(outputFile);
            workbook.write(fileOutputStream);
            fileOutputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create Excel output file");
        } finally {
            try {
                if (fileOutputStream != null) { fileOutputStream.close(); }
            } catch (IOException e) {
                throw new IllegalStateException(" Unable to close output file");
            }
        }

        return outputFile;
    }

    private static File createJSON(RecordData recordData) {
        String outFile = new Syspath().out("fullpath") + separator +
                         OutputFileUtils.generateOutputFilename(context.getCurrentTestStep(), "json");

        File outputFile = new File(outFile);
        try {
            FileUtils.writeStringToFile(outputFile, getJsonString(recordData), DEF_FILE_ENCODING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create JSON output file");
        }
        return outputFile;
    }

    private static void createSummarySheet(RecordData recordData, Workbook workbook) {
        Sheet summarySheet = workbook.createSheet("Summary");

        summarySheet = setSummaryData(summarySheet, 0, "Run User", System.getProperty("user.name"));
        summarySheet = setSummaryData(summarySheet, 1, "Start Time", recordData.getStartTime());
        summarySheet = setSummaryData(summarySheet, 2, "Process Time", recordData.getProcessTime());
        summarySheet = setSummaryData(summarySheet, 3, "Total Processed",
                                      String.valueOf(recordData.getTotalRecordsProcessed()));
        summarySheet = setSummaryData(summarySheet, 4, "Total Skipped",
                                      String.valueOf(recordData.getSkippedRecords().keySet().size()));
        summarySheet = setSummaryData(summarySheet, 5, "Total Passed", String.valueOf(recordData.totalRecordsPassed()));
        summarySheet = setSummaryData(summarySheet, 6, "Total Errors", String.valueOf(recordData.totalRecordsFailed()));
        summarySheet = setSummaryData(summarySheet, 7, "Total Warnings", String.valueOf(recordData.totalWarnings()));
        summarySheet = setSummaryData(summarySheet, 8, "Input File", recordData.getInputFile());
        summarySheet = setSummaryData(summarySheet, 9, "Excel File", recordData.getExcelFile());
        summarySheet = setSummaryData(summarySheet, 10, "Has Error", String.valueOf(recordData.isHasError()));

        summarySheet.createRow(12).createCell(0).setCellValue("Map Values");
        Map<String, Object> mapValues = recordData.getMapValues();
        int n = 14;
        for (String key : mapValues.keySet()) {
            summarySheet.createRow(n).createCell(0).setCellValue(key);
            summarySheet.getRow(n).createCell(1).setCellValue(mapValues.get(key).toString());
            n++;
        }

    }

    private static void createSkippedReportSheet(RecordData recordData, Workbook workbook) {

        Sheet summarySheet = workbook.createSheet("Skipped_Records_Report");
        summarySheet.createRow(0).createCell(0).setCellValue("Record Line");
        summarySheet.getRow(0).createCell(1).setCellValue("Skipped Record(s) details");
        int n = 1;
        for (Entry<Integer, String> record : recordData.getSkippedRecords().entrySet()) {
            summarySheet.createRow(n).createCell(0).setCellValue(record.getKey());
            summarySheet.getRow(n).createCell(1).setCellValue(record.getValue());
            n++;
        }
        if (recordData.getSkippedRecords().keySet().size() > 0) {

            summarySheet.createRow(n + 1).createCell(0).setCellValue("Note: ");
            String msg1 =
                "Skipped records may cause evaluating incorrect map functions. Review target file and config for these differences.";
            summarySheet.getRow(n + 1).createCell(1).setCellValue(msg1);
        }

    }

    private static Sheet setSummaryData(Sheet summarySheet, int row, String key, String value) {
        Cell keyCell = summarySheet.createRow(row).createCell(0);
        keyCell.setCellValue(key);
        summarySheet.getRow(row).createCell(1).setCellValue(value);
        return summarySheet;
    }

    private static void createErrorReportSheet(RecordData recordData, Workbook workbook) {
        String errorsCSV = createCSV(recordData);
        Sheet sheet = workbook.createSheet("Error_Report");
        String[] lines = errorsCSV.split("\n");
        int rowNum = 0;
        for (String line : lines) {
            String[] fields = line.split(",");
            Row row = sheet.createRow(rowNum);

            for (int i = 0; i < fields.length; i++) {

                Cell cell = row.createCell(i);
                cell.setCellType(CellType.STRING);
                cell.setCellValue(fields[i]);
            }
            rowNum++;
        }
    }
}
