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

package org.nexial.core;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.nexial.core.excel.Excel;
import org.nexial.core.excel.ExcelConfig.StyleDecorator;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.TestData;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;

import static org.nexial.core.NexialConst.Data.SHEET_MERGED_DATA;
import static org.nexial.core.NexialConst.Data.SHEET_SYSTEM;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Project.appendCapture;
import static org.nexial.core.NexialConst.Project.appendLog;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.*;
import static java.io.File.separator;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;

/**
 * This class serves 2 purposes:
 * <ol>
 * <li>Create the necessary output directory structure. The output directory will be named according to the start
 * time of the test execution.</li>
 * <li>Merge the test data with the resolved test script into one or more output files.  These output are further
 * decorated where its filename indicates its associated iteration.</li>
 * </ol>
 */
class ExecutionInputPrep {
    /** called from {@link ExecutionThread} for each iteration. */
    static File prep(String runId, ExecutionDefinition execDef, int iteration, int counter) throws IOException {
        assert StringUtils.isNotBlank(runId);
        assert execDef != null;

        // 1. create output directory structure
        String outBase = StringUtils.appendIfMissing(execDef.getOutPath(), separator) + runId;
        createSubdirs(runId, outBase);

        // 2. copy output file to tmp directory for data merge
        File testScript = new File(execDef.getTestScript());
        String filename = testScript.getName();

        // since tmp file is local, the merge work with test data would be faster
        // add random alphanum to avoid collision in parallel processing
        String tmpFilePath = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator +
                             RandomStringUtils.randomAlphabetic(5) + separator +
                             filename;
        File tmpFile = new File(tmpFilePath);
        FileUtils.copyFile(testScript, tmpFile);

        // 3. remove unused sheets
        List<String> scenarios = execDef.getScenarios();
        List<Integer> unusedWorksheetIndices = new ArrayList<>();
        Excel tmpExcelFile = new Excel(tmpFile);
        // collect the index of all unused worksheets
        tmpExcelFile.getWorksheetsStartWith("").forEach(worksheet -> {
            if (!StringUtils.equals(worksheet.getName(), SHEET_SYSTEM) &&
                !scenarios.contains(worksheet.getName())) {
                unusedWorksheetIndices.add(tmpExcelFile.getWorkbook().getSheetIndex(worksheet.getSheet()));
            }
        });
        // remove the latter ones first so that we don't need to deal with shift in positions
        Collections.reverse(unusedWorksheetIndices);

        unusedWorksheetIndices.forEach(index -> tmpExcelFile.getWorkbook().removeSheetAt(index));
        tmpExcelFile.save();

        // merge macros
        // todo: better instantiation so that we can reuse in-class cache (inside MacroMerger)
        MacroMerger macroMerger = new MacroMerger();
        macroMerger.mergeMacro(new Excel(tmpFile), execDef.getProject());

        // 4. merge expanded test data to output file
        // this is necessary since the output directory (and final output file) could be remote
        // merging test data to remote output file could be time-consuming
        ConsoleUtils.log(runId, "merging test data to tmp file " + tmpFilePath);
        mergeTestData(tmpFile, execDef.getTestData(true), iteration);

        // 5. decorate file name based on runtime information
        String outputFileName = StringUtils.appendIfMissing(outBase, separator) + filename;
        outputFileName = OutputFileUtils.addStartDateTime(outputFileName, new Date());
        //if (testData.has(iteration, BROWSER)) {
        //	outputFileName = OutputFileUtils.addBrowser(outputFileName, Browser testData.getValue(iteration, BROWSER));
        //}
        outputFileName = OutputFileUtils.addIteration(outputFileName, StringUtils.leftPad(counter + "", 3, "0"));

        // if script are executed as part of test plan, then the naming convention is:
        // [test plan file name w/o ext][SEP][test plan sheet name][SEP][sequence#][SEP][test script name w/o ext][SEP][start date yyyyMMdd_HHmmss][SEP][iteration#].xlsx
        outputFileName = OutputFileUtils.addTestPlan(outputFileName, execDef);

        File outputFile = new File(outputFileName);
        ConsoleUtils.log(runId, "test script and test data merged to " + outputFile);

        // 6. now copy tmp to final location
        ConsoleUtils.log(runId, "copying tmp file to output file " + outputFile);
        FileUtils.copyFile(tmpFile, outputFile);
        FileUtils.deleteQuietly(tmpFile.getParentFile());

        return outputFile;
    }

    private static Excel mergeTestData(File outputFile, TestData testData, int iteration) throws IOException {
        SortedMap<String, String> settings = new TreeMap<>(testData.getAllSettings());
        SortedMap<String, String> data = new TreeMap<>(testData.getAllValue(iteration));

        Excel excel = new Excel(outputFile);
        XSSFSheet dataSheet = excel.getWorkbook().createSheet(SHEET_MERGED_DATA);

        XSSFWorkbook workbook = dataSheet.getWorkbook();
        XSSFCellStyle styleSettingName = StyleDecorator.generate(workbook, SETTING_NAME);
        XSSFCellStyle styleSettingValue = StyleDecorator.generate(workbook, SETTING_VALUE);
        XSSFCellStyle stylePredefTestDataName = StyleDecorator.generate(workbook, PREDEF_TEST_DATA_NAME);
        XSSFCellStyle styleTestDataName = StyleDecorator.generate(workbook, TEST_DATA_NAME);
        XSSFCellStyle styleTestDataValue = StyleDecorator.generate(workbook, TEST_DATA_VALUE);

        final int[] currentRowIndex = {0};
        settings.forEach((name, value) -> {
            XSSFRow row = dataSheet.getRow(currentRowIndex[0]++);
            if (row == null) { row = dataSheet.createRow(currentRowIndex[0] - 1); }

            XSSFCell cell = row.getCell(0, CREATE_NULL_AS_BLANK);
            cell.setCellValue(name);
            cell.setCellStyle(styleSettingName);
            cell = row.getCell(1, CREATE_NULL_AS_BLANK);
            cell.setCellValue(CellTextReader.readValue(value));
            cell.setCellStyle(styleSettingValue);
        });

        data.forEach((name, value) -> {
            XSSFRow row = dataSheet.getRow(currentRowIndex[0]++);
            if (row == null) { row = dataSheet.createRow(currentRowIndex[0] - 1); }

            XSSFCell cellName = row.getCell(0, CREATE_NULL_AS_BLANK);
            cellName.setCellValue(name);
            cellName.setCellStyle(StringUtils.startsWith(name, NAMESPACE) ?
                                  stylePredefTestDataName : styleTestDataName);

            XSSFCell cellValue = row.getCell(1, CREATE_NULL_AS_BLANK);
            cellValue.setCellValue(CellTextReader.readValue(testData.getValue(iteration, name)));
            cellValue.setCellStyle(styleTestDataValue);
        });

        // save output file with expanded data
        excel.save();

        return excel;
    }

    private static void createSubdirs(String runId, String base) {
        File baseDir = new File(base);
        if (!baseDir.exists()) { baseDir.mkdirs(); }
        mkdirs(runId, appendCapture(base));
        mkdirs(runId, appendLog(base));
    }

    private static void mkdirs(String runId, String pathname) {
        ConsoleUtils.log(runId, "create directory " + pathname);
        new File(pathname).mkdirs();
    }
}
