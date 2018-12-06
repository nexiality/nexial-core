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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelConfig.StyleDecorator;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.MacroMerger;
import org.nexial.core.model.TestData;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;
import org.nexial.core.utils.OutputFileUtils;

import static java.io.File.separator;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.core.NexialConst.Data.SHEET_MERGED_DATA;
import static org.nexial.core.NexialConst.Data.SHEET_SYSTEM;
import static org.nexial.core.NexialConst.NAMESPACE;
import static org.nexial.core.NexialConst.Project.appendCapture;
import static org.nexial.core.NexialConst.Project.appendLog;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.*;
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

    public static Excel updateOutputDataSheet(Excel outputFile) {
        ExecutionContext context = ExecutionThread.get();
        if (context == null) { return null; }

        XSSFSheet dataSheet = outputFile.getWorkbook().getSheet(SHEET_MERGED_DATA);

        // XSSFWorkbook workbook = dataSheet.getWorkbook();
        // XSSFCellStyle styleTestDataValue = StyleDecorator.generate(workbook, TEST_DATA_VALUE);

        final int[] currentRowIndex = {0};

        dataSheet.forEach(datarow -> {
            XSSFRow row = dataSheet.getRow(currentRowIndex[0]++);
            if (row == null) { return; }

            XSSFCell cellName = row.getCell(0, CREATE_NULL_AS_BLANK);
            String name = cellName.getStringCellValue();

            XSSFCell cellValue = row.getCell(1, CREATE_NULL_AS_BLANK);
            if (context.hasData(name)) {
                cellValue.setCellValue(CellTextReader.readValue(context.getStringData(name)));
                // cellValue.setCellStyle(styleTestDataValue);
            }
        });

        // save output file with updated data
        // (2018/10/18,automike): omit saving here because this file will be saved later anyways
        // outputFile.save();

        return outputFile;
    }

    /** called from {@link ExecutionThread} for each iteration. */
    public static Excel prep(String runId, ExecutionDefinition execDef, int iteration, int counter) throws IOException {
        assert StringUtils.isNotBlank(runId);
        assert execDef != null;

        // 1. create output directory structure
        String outBaseParent = StringUtils.appendIfMissing(execDef.getOutPath(), separator);
        String outBase = outBaseParent + runId;
        createSubdirs(runId, outBase);

        // 2. copy output file to tmp directory for data merge
        File testScript = new File(execDef.getTestScript());
        String filename = testScript.getName();

        // since tmp file is local, the merge work with test data would be faster
        // add random alphanum to avoid collision in parallel processing
        // String tmpFilePath = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator +
        //                      RandomStringUtils.randomAlphabetic(5) + separator +
        //                      filename;
        // File tmpFile = new File(tmpFilePath);
        // FileUtils.copyFile(testScript, tmpFile);

        // 2.1. decorate output file name based on runtime information
        String outputFileName = StringUtils.appendIfMissing(outBase, separator) + filename;
        outputFileName = OutputFileUtils.addStartDateTime(outputFileName, new Date());
        outputFileName = OutputFileUtils.addIteration(outputFileName, StringUtils.leftPad(counter + "", 3, "0"));

        // if script are executed as part of test plan, then the naming convention is:
        // [test plan file name w/o ext][SEP][test plan sheet name][SEP][sequence#][SEP][test script name w/o ext][SEP][start date yyyyMMdd_HHmmss][SEP][iteration#].xlsx
        outputFileName = OutputFileUtils.addTestPlan(outputFileName, execDef);
        File outputFile = new File(outputFileName);

        FileUtils.copyFile(testScript, outputFile);

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
            outputExcel = new Excel(outputFile, false, true);
        }

        // merge macros
        // todo: better instantiation so that we can reuse in-class cache (inside MacroMerger)
        MacroMerger macroMerger = new MacroMerger();
        macroMerger.setCurrentIteration(counter);
        macroMerger.setExecDef(execDef);
        macroMerger.setProject(execDef.getProject());
        macroMerger.setExcel(outputExcel);
        macroMerger.mergeMacro();

        // 4. merge expanded test data to output file
        // this is necessary since the output directory (and final output file) could be remote
        // merging test data to remote output file could be time-consuming
        ConsoleUtils.log(runId, "merging test data to tmp file " + outputFile);

        // we are no longer concerned with remote file access. The best practice to follow is NOT to use remote fs
        mergeTestData(outputExcel, execDef.getTestData(true), iteration);
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
                // didn't copy the file yet.. time to do so
                ConsoleUtils.log(runId, "copying just-in-time batch script to output");
                FileUtils.copyFile(new File(jitBatchSource), new File(jitBatchTarget));
            }
        }

        // save it before use it
        outputExcel.save();
        return new Excel(outputExcel.getFile(), false, true);
    }

    private static Excel mergeTestData(Excel excel, TestData testData, int iteration) {
        XSSFSheet dataSheet = excel.getWorkbook().createSheet(SHEET_MERGED_DATA);

        XSSFWorkbook workbook = dataSheet.getWorkbook();
        XSSFCellStyle styleSystemDataName = StyleDecorator.generate(workbook, PREDEF_TEST_DATA_NAME);
        XSSFCellStyle styleTestDataName = StyleDecorator.generate(workbook, TEST_DATA_NAME);
        XSSFCellStyle styleTestDataValue = StyleDecorator.generate(workbook, TEST_DATA_VALUE);

        SortedMap<String, String> data = new TreeMap<>(testData.getAllValue(iteration));
        testData.getAllSettings().forEach(data::put);
        data.putAll(ExecUtils.deriveJavaOpts());

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
            cellName.setCellStyle(StringUtils.startsWith(name, NAMESPACE) ? styleSystemDataName : styleTestDataName);

            XSSFCell cellValue = row.getCell(1, CREATE_NULL_AS_BLANK);
            cellValue.setCellValue(CellTextReader.readValue(value));
            cellValue.setCellStyle(styleTestDataValue);
        });

        // save output file with expanded data
        // (2018/10/18,automike): skip saving because it'll need to be saved later anyways
        // excel.save();

        return excel;
    }

    private static boolean shouldExcludeDataVariable(String varName) {
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
