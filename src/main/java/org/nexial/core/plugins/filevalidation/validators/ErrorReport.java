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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.nexial.core.ExecutionThread;
import org.nexial.core.NexialConst;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.filevalidation.RecordData;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.JsonHelper;
import org.nexial.core.utils.OutputFileUtils;
import org.nexial.core.variable.Syspath;

import static org.nexial.core.NexialConst.GSON;

public class ErrorReport {
    private static ExecutionContext context = ExecutionThread.get();

    public static String createCSV(List<Error> errors) {
        if (errors == null || errors.isEmpty()) { return ""; }
        StringBuilder sb = new StringBuilder();

        for (Error error : errors) {
            sb.append(error.getRecordLine()).append(",");
            sb.append(error.getFieldName()).append(",");
            sb.append(error.getSeverity()).append(",");
            sb.append(error.getValidationType()).append(",");
            sb.append(error.getErrorMessage().replace(",", "\\,"));
            sb.append(System.getProperty("line.separator"));
        }
        return sb.toString();
    }

    private static String getJsonString(RecordData recordData) {
        return GSON.toJson(recordData);
    }

    public static File createExcel(RecordData recordData) {

        String outFile = new Syspath().out("fullpath") + File.separator +
                         OutputFileUtils.generateOutputFilename(context.getCurrentTestStep(), "xlsx");
        File outputFile = new File(outFile);
        String csvFile = new Syspath().out("fullpath") + File.separator +
                         OutputFileUtils.generateOutputFilename(context.getCurrentTestStep(), "csv");

        try {
            Excel excel = Excel.createExcel(outputFile);
            createSummarySheet(recordData, excel.getWorkbook());
            createReportSheets(csvFile, excel);
            excel.save();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to write error report to excel file: "+e.getMessage());
        }
        return outputFile;
    }

    public static File createJSON(RecordData recordData) {
        String outFile = new Syspath().out("fullpath") + File.separator +
                         OutputFileUtils.generateOutputFilename(context.getCurrentTestStep(), "json");
        File outputFile = new File(outFile);
        String csvFile = new Syspath().out("fullpath") + File.separator +
                         OutputFileUtils.generateOutputFilename(context.getCurrentTestStep(), "csv");
        String summary = getJsonString(recordData);

        String errorHeaders = "Record Line, Field Name, Severity, Validation Type, Error Message \n";
        String skipHeaders = "Record Line, Record ID, Message \n";

        List<List<String>> errors = createListFromCsv(csvFile, false, errorHeaders);
        List<List<String>> skipped = createListFromCsv(csvFile, true, skipHeaders);

        try (Writer fileWriter = new FileWriter(outputFile)) {

            if (CollectionUtils.isEmpty(errors) && CollectionUtils.isEmpty(skipped)) {
                fileWriter.write(summary);
                return outputFile;
            }
            if (CollectionUtils.isNotEmpty(errors)) {
                String appendErrors = StringUtils.removeEnd(summary, "}") + ",\n\"errors\":";
                createErrorsFromCsv(errors, skipped, appendErrors, fileWriter);
            } else if (CollectionUtils.isNotEmpty(skipped)) {

                String appendSkipped = StringUtils.removeEnd(summary, "}") + ",\n\"skipped\":";
                createSkippedFromCsv(skipped, appendSkipped, fileWriter);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create JSON output file: " + e.getMessage());
        }
        return outputFile;
    }

    private static void createErrorsFromCsv(List<List<String>> errors,
                                            List<List<String>> skipped,
                                            String appendErrors,
                                            Writer fileWriter
                                           ) throws IOException {
        String appendSkipped = ",\n\"skipped\":";
        JsonHelper.fromCsv(errors,
                           true, beforeErrors -> beforeErrors.write(appendErrors),
                           fileWriter, (CollectionUtils.isNotEmpty(skipped)) ?
                                       afterErrors -> createSkippedFromCsv(skipped, appendSkipped, fileWriter) :
                                       closeJson -> closeJson.append("}"));
    }

    private static void createSkippedFromCsv(List<List<String>> skipped, String appendSkipped, Writer fileWriter)
        throws IOException {
        JsonHelper.fromCsv(skipped, true, beforeSkipped -> beforeSkipped.write(appendSkipped),
                           fileWriter, closeJson -> closeJson.write("\n}"));
    }

    private static void createSummarySheet(RecordData recordData, Workbook workbook) {
        Sheet summarySheet = workbook.createSheet("Summary");

        summarySheet = setSummaryData(summarySheet, 0, "Run User", System.getProperty("user.name"));
        summarySheet = setSummaryData(summarySheet, 1, "Start Time", recordData.getStartTime());
        summarySheet = setSummaryData(summarySheet, 2, "Process Time", recordData.getProcessTime());
        summarySheet = setSummaryData(summarySheet, 3, "Total Processed",
                                      String.valueOf(recordData.getTotalRecordsProcessed()));
        summarySheet = setSummaryData(summarySheet, 4, "Total Skipped",
                                      String.valueOf(recordData.getTotalRecordsSkipped()));
        summarySheet = setSummaryData(summarySheet, 5, "Total Errors",
                                      String.valueOf(recordData.getTotalRecordsFailed()));
        summarySheet = setSummaryData(summarySheet, 6, "Total Passed",
                                      String.valueOf(recordData.getTotalRecordsPassed()));
        summarySheet = setSummaryData(summarySheet, 7, "Total Warnings",
                                      String.valueOf(recordData.getTotalRecordsWarning()));
        summarySheet = setSummaryData(summarySheet, 8, "Input File", recordData.getInputFile());
        summarySheet = setSummaryData(summarySheet, 9, "Excel File", recordData.getExcelFile());
        summarySheet = setSummaryData(summarySheet, 10, "Has Error", String.valueOf(recordData.isHasError()));

        summarySheet.createRow(12).createCell(0).setCellValue("Map Values");
        Map<String, Number> mapValues = recordData.getMapValues();
        int n = 14;
        if (mapValues != null && !mapValues.isEmpty()) {
            for (String key : mapValues.keySet()) {
                summarySheet.createRow(n).createCell(0).setCellValue(key);
                summarySheet.getRow(n).createCell(1).setCellValue(mapValues.get(key).toString());
                n++;
            }
        }
    }

    private static Sheet setSummaryData(Sheet summarySheet, int row, String key, String value) {
        Cell keyCell = summarySheet.createRow(row).createCell(0);
        keyCell.setCellValue(key);
        summarySheet.getRow(row).createCell(1).setCellValue(value);
        return summarySheet;
    }

    private static void createReportSheets(String errorsCSV, Excel excel) {
        CheckUtils.requiresReadableFile(errorsCSV);
        String errorHeaders = "Record Line, Field Name, Severity, Validation Type, Error Message \n";
        String skipHeaders = "Record Line, Record ID, Message \n";
        try {
            excel.worksheet("Errors_Report", true)
                 .writeAcross(new ExcelAddress("A1"), createListFromCsv(errorsCSV, false, errorHeaders));
            excel.worksheet("Skipped_Records", true)
                 .writeAcross(new ExcelAddress("A1"), createListFromCsv(errorsCSV, true, skipHeaders));
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to create error reports in excel sheets: " + e.getMessage());
        }

    }

    private static List<List<String>> createListFromCsv(String errorsCsv, boolean isSkipped, String headers) {
        String csv;
        try {
            List<String> lines = FileUtils.readLines(new File(errorsCsv), NexialConst.DEF_FILE_ENCODING);
            csv = lines.stream().filter(line -> (isSkipped) == line.startsWith("Skipped"))
                       .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to create excel report file.");
        }
        if (isSkipped) { csv = StringUtils.replace(csv, "Skipped:", ""); }
        return StringUtils.isNotBlank(csv) ?
               createCsvRows(StringUtils.prependIfMissing(csv, headers)) :
               new ArrayList<>();
    }

    private static List<List<String>> createCsvRows(String csvString) {
        CheckUtils.requiresNotBlank(csvString, "csv data can't be blank");
        List<List<String>> rows = new ArrayList<>();
        String[] lines = StringUtils.split(csvString, "\n");
        for (String line : lines) {
            // prevent writing field values with comma into multiple cells in excel sheet
            String[] fields = line.split("(?<!\\\\),");
            List<String> cols = new ArrayList<>();
            for (String field : fields) {
                cols.add(StringUtils.trim(field.replace("\\,", ",")));
            }
            rows.add(cols);
        }
        return rows;
    }
}