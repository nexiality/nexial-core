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

package org.nexial.core;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.*;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelStyleHelper;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.ExecutionVariableConsole;
import org.nexial.core.model.IterationManager;
import org.nexial.core.model.TestData;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;
import org.nexial.core.utils.OutputFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.io.File.separator;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.core.NexialConst.Data.SHEET_MERGED_DATA;
import static org.nexial.core.NexialConst.Data.SHEET_SYSTEM;
import static org.nexial.core.NexialConst.ExitStatus.OUTPUT_LOCATION;
import static org.nexial.core.NexialConst.Iteration.*;
import static org.nexial.core.NexialConst.NAMESPACE;
import static org.nexial.core.NexialConst.OPT_INPUT_EXCEL_FILE;
import static org.nexial.core.NexialConst.Project.appendCapture;
import static org.nexial.core.NexialConst.Project.appendLog;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.utils.ExecUtils.IGNORED_CLI_OPT;

/**
 * This class serves 2 purposes:
 * <ol>
 * <li>Create the necessary output directory structure. The output directory will be named according to the start
 * time of the test execution.</li>
 * <li>Merge the test data with the resolved test script into one or more output files.  These output are further
 * decorated where its filename indicates its associated iteration.</li>
 * </ol>
 */
public class ExecutionInputPrep {

    /** called from {@link ExecutionThread} for each iteration. */
    public static Excel prep(String runId, ExecutionDefinition execDef, int iterationIndex) throws IOException {
        assert StringUtils.isNotBlank(runId);
        assert execDef != null;

        // 1. create output directory structure
        String outBaseParent = StringUtils.appendIfMissing(execDef.getOutPath(), separator);
        String outBase = outBaseParent + runId;
        System.setProperty(OUTPUT_LOCATION, outBase);
        createSubdirs(runId, outBase);

        // 2. copy output file to tmp directory for data merge
        File testScript = new File(execDef.getTestScript());
        String filename = testScript.getName();

        // since tmp file is local, the merge work with test data would be faster
        // add random alphanumeric to avoid collision in parallel processing
        // String tmpFilePath = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator +
        //                      RandomStringUtils.randomAlphabetic(5) + separator +
        //                      filename;
        // File tmpFile = new File(tmpFilePath);
        // FileUtils.copyFile(testScript, tmpFile);

        // 2.1. decorate output file name based on runtime information
        String outputFileName = StringUtils.appendIfMissing(outBase, separator) + filename;
        outputFileName = OutputFileUtils.addStartDateTime(outputFileName, new Date());
        outputFileName = OutputFileUtils.addIteration(outputFileName,
                                                      StringUtils.leftPad(String.valueOf(iterationIndex), 3, "0"));

        // if script are executed as part of test plan, then the naming convention is:
        // [test plan file name w/o ext][SEP][test plan sheet name][SEP][sequence#][SEP][test script name w/o ext][SEP][start date yyyyMMdd_HHmmss][SEP][iteration#].xlsx
        outputFileName = OutputFileUtils.addTestPlan(outputFileName, execDef);
        File outputFile = new File(outputFileName);

        FileUtils.copyFile(testScript, outputFile);
        System.setProperty(OPT_INPUT_EXCEL_FILE, outputFileName);

        // 3. remove unused sheets
        List<String> scenarios = execDef.getScenarios();
        List<Integer> unusedWorksheetIndices = new ArrayList<>();
        Excel outputExcel = new Excel(outputFile, false, true);

        // collect the index of all unused worksheets
        for (Worksheet worksheet : outputExcel.getWorksheetsStartWith("")) {
            if (!StringUtils.equals(worksheet.getName(), SHEET_SYSTEM) && !scenarios.contains(worksheet.getName())) {
                unusedWorksheetIndices.add(outputExcel.getWorkbook().getSheetIndex(worksheet.getSheet()));
            }
        }

        // remove the latter ones first so that we don't need to deal with shift in positions
        if (CollectionUtils.isNotEmpty(unusedWorksheetIndices)) {
            Collections.reverse(unusedWorksheetIndices);
            for (Integer index : unusedWorksheetIndices) { outputExcel.getWorkbook().removeSheetAt(index); }
            outputExcel.save();
            // (2018/12/16,automike): memory consumption precaution
            outputExcel.close();
            outputExcel = new Excel(outputFile, false, true);
        }

        // 4. merge expanded test data to output file
        // this is necessary since the output directory (and final output file) could be remote
        // merging test data to remote output file could be time-consuming
        ConsoleUtils.log(runId, "merging test data to tmp file " + outputFile);

        // we are no longer concerned with remote file access. The best practice to follow is NOT to use remote fs
        TestData testData = execDef.getTestData();
        if (testData == null || testData.getSettingAsBoolean(REFETCH_DATA_FILE)) {
            testData = execDef.getTestData(true);
        }
        if (!ExecUtils.isRunningInZeroTouchEnv()) {
            testData = new ExecutionVariableConsole().processRuntimeVariables(testData, iterationIndex);
        }
        mergeTestData(outputExcel, testData, iterationIndex);
        ConsoleUtils.log(runId, "test script and test data merged to " + outputFile);

        // 6. now copy tmp to final location
        // ConsoleUtils.log(runId, "copying tmp file to output file " + outputFile);
        // FileUtils.copyFile(tmpFile, outputFile);
        // FileUtils.deleteQuietly(tmpFile.getParentFile());

        // copy any existing JIT batch  (from nexial.sh only)
        String jitBatchSource = System.getProperty("nexial.script");
        if (StringUtils.isNotBlank(jitBatchSource) && FileUtil.isFileReadable(jitBatchSource, 800)) {
            String jitBatchTarget = StringUtils.appendIfMissing(outBase, separator) + "nexial.sh";
            if (!FileUtil.isFileReadable(jitBatchTarget, 800)) {
                // didn't copy the file yet... time to do so
                ConsoleUtils.log(runId, "copying just-in-time batch script to output");
                FileUtils.copyFile(new File(jitBatchSource), new File(jitBatchTarget));
            }
        }

        // save it before use it
        outputExcel.save();
        // (2018/12/16,automike): memory consumption precaution
        outputExcel.close();

        return new Excel(outputFile, false, true);
    }

    public static boolean isTestStepDisabled(List<XSSFCell> row) {
        return isCellStrikeOut(row, COL_IDX_COMMAND, "test step");
    }

    public static boolean isTestStepDisabled(XSSFCell cell) {
        return cell.getColumnIndex() == COL_IDX_COMMAND && isCellStrikeOut(cell, "test step");
    }

    public static boolean isMacroStepDisabled(List<XSSFCell> row) {
        return isCellStrikeOut(row, COL_IDX_COMMAND, "macro step");
    }

    public static boolean isMacroStepDisabled(XSSFCell cell) {
        return cell.getColumnIndex() == COL_IDX_COMMAND && isCellStrikeOut(cell, "macro step");
    }

    public static boolean isDataStepDisabled(XSSFCell cell) {
        return isCellStrikeOut(cell, "data name");
    }

    public static boolean isPlanStepDisabled(XSSFRow row) {
        return isCellStrikeOut(row.getCell(COL_IDX_PLAN_TEST_SCRIPT), "plan step");
    }

    public static boolean isPlanStepDisabled(XSSFCell cell) {
        return cell.getColumnIndex() == COL_IDX_PLAN_TEST_SCRIPT && isCellStrikeOut(cell, "plan step");
    }

    protected static boolean isCellStrikeOut(List<XSSFCell> row, int cellIndex, String stepName) {
        return row != null && cellIndex >= 0 && cellIndex < row.size() && isCellStrikeOut(row.get(cellIndex), stepName);
    }

    private static boolean isCellStrikeOut(XSSFCell cell, String stepName) {
        if (cell == null) { return false; }

        XSSFCellStyle cellStyle = cell.getCellStyle();
        if (cellStyle == null) { return false; }

        XSSFFont font = cellStyle.getFont();
        if (font == null || !font.getStrikeout()) { return false; }

        int rowNum = cell.getRowIndex() + 1;
        ConsoleUtils.log("skipping " + stepName + " in ROW " + rowNum + " since it's disabled");
        return true;
    }

    private static Excel mergeTestData(Excel excel, TestData testData, int iterationIndex) {
        XSSFSheet dataSheet = excel.getWorkbook().createSheet(SHEET_MERGED_DATA);

        XSSFWorkbook workbook = dataSheet.getWorkbook();
        XSSFCellStyle styleSystemDataName = ExcelStyleHelper.generate(workbook, PREDEF_TEST_DATA_NAME);
        XSSFCellStyle styleTestDataName = ExcelStyleHelper.generate(workbook, TEST_DATA_NAME);
        XSSFCellStyle styleTestDataNameDisabled = ExcelStyleHelper.generate(workbook, TEST_DATA_NAME_DISABLED);
        XSSFCellStyle styleTestDataValue = ExcelStyleHelper.generate(workbook, TEST_DATA_VALUE);

        SortedMap<String, String> data = new TreeMap<>((key1, key2) -> {
            if (StringUtils.startsWith(key1, NAMESPACE)) {
                return StringUtils.startsWith(key2, NAMESPACE) ? key1.compareTo(key2) : -1;
            } else {
                return StringUtils.startsWith(key2, NAMESPACE) ? 1 : key1.compareTo(key2);
            }
        });

        IterationManager iterationManager = testData.getIterationManager();
        int iterationRef = iterationManager.getIterationRef(iterationIndex - 1);

        data.putAll(testData.getAllValue(iterationRef));
        data.putAll(testData.getAllSettings());
        data.putAll(ExecUtils.deriveJavaOpts());

        data.put(CURR_ITERATION, String.valueOf(iterationIndex));
        if (iterationRef != -1) { data.put(CURR_ITERATION_ID, String.valueOf(iterationRef)); }
        if (iterationIndex > 1) {
            int lastIterationRef = iterationManager.getIterationRef(iterationIndex - 2);
            if (lastIterationRef != -1) { data.put(LAST_ITERATION, String.valueOf(lastIterationRef)); }
            data.put(IS_FIRST_ITERATION, "false");
        } else {
            data.put(IS_FIRST_ITERATION, "true");
        }
        data.put(IS_LAST_ITERATION, iterationIndex == iterationManager.getIterationCount() ? "true" : "false");

        Properties sysprops = System.getProperties();
        if (MapUtils.isNotEmpty(sysprops)) {
            sysprops.forEach((name, value) -> data.put(name.toString(), value.toString()));
        }

        // remove all excluded data variables
        String[] dataNames = data.keySet().toArray(new String[0]);
        Arrays.stream(dataNames).forEach(name -> {
            if (StringUtils.isBlank(name) || shouldExcludeDataVariable(name)) { data.remove(name); }
        });

        final int[] currentRowIndex = {0};
        data.forEach((name, value) -> {
            XSSFRow row = dataSheet.getRow(currentRowIndex[0]++);
            if (row == null) { row = dataSheet.createRow(currentRowIndex[0] - 1); }

            XSSFCell cellName = row.getCell(0, CREATE_NULL_AS_BLANK);
            cellName.setCellValue(name);
            cellName.setCellStyle(StringUtils.startsWith(name, NAMESPACE) ?
                                  styleSystemDataName :
                                  StringUtils.startsWith(name, "//") ? styleTestDataNameDisabled : styleTestDataName);

            XSSFCell cellValue = row.getCell(1, CREATE_NULL_AS_BLANK);
            cellValue.setCellValue(CellTextReader.readValue(value));
            cellValue.setCellStyle(styleTestDataValue);
        });

        dataSheet.autoSizeColumn(0);
        dataSheet.autoSizeColumn(1);

        // save output file with expanded data
        // (2018/10/18,automike): skip saving because it'll need to be saved later anyway
        // excel.save();

        return excel;
    }

    public static boolean shouldExcludeDataVariable(String varName) {
        for (String ignored : IGNORED_CLI_OPT) { if (StringUtils.startsWith(varName, ignored)) { return true; } }
        return false;
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
