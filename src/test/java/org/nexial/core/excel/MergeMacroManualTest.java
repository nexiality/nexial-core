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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.NexialTestUtils;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.utils.InputFileUtils;

import static java.io.File.separator;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.core.excel.ExcelConfig.*;

public class MergeMacroManualTest {
    private static final String TEST_STEPS_PREFIX =
        "" + COL_TEST_CASE + (ADDR_COMMAND_START.getRowStartIndex() + 1) + ":" + COL_REASON;
    private static final String TEST_COMMAND_MACRO = "base.macro(file,sheet,name)";

    private File testSheetFile = new File(ResourceUtils.getResourceFilePath("/org/nexial/core/excel/MacroTest.xlsx"));

    class MacroMerger {
        private Map<String, List<List<String>>> parsedMacroSteps = new HashMap<>();

        protected void mergeMacro(Excel excel) throws IOException {
            excel.getWorkbook().setMissingCellPolicy(CREATE_NULL_AS_BLANK);

            // find all scenario sheets
            List<Worksheet> testSheets = InputFileUtils.retrieveValidTestScenarios(excel);
            assert testSheets != null;

            // scan all test steps
            for (Worksheet sheet : testSheets) {
                // collect all test steps (also clear existing test steps in sheet)
                List<List<String>> allTestSteps = harvestAllTestSteps(sheet);

                System.out.println("sheet " + sheet.getName() +
                                   " - original test steps:\n\t\t" + TextUtils.toString(allTestSteps, "\n\t\t"));

                // scan through all test steps
                scanTestSteps(sheet, allTestSteps);
                refillExpandedTestSteps(sheet, allTestSteps);
            }

            // save excel, rinse and repeat.
            System.out.println("\tsaving excel " + excel.getFile());
            excel.save();
        }

        protected void scanTestSteps(Worksheet sheet, List<List<String>> allTestSteps) throws IOException {
            for (int i = 0; i < allTestSteps.size(); i++) {
                List<String> row = allTestSteps.get(i);
                String activityName = row.get(COL_IDX_TESTCASE);
                String cellTarget = row.get(COL_IDX_TARGET);
                String cellCommand = row.get(COL_IDX_COMMAND);
                String testCommand = cellTarget + "." + cellCommand;

                // look for base.macro(file,sheet,name) - open macro library as excel
                if (StringUtils.equals(testCommand, TEST_COMMAND_MACRO)) {
                    System.out.println(sheet.getName() + ": " + cellTarget + " - " + testCommand);

                    String paramFile = row.get(COL_IDX_PARAMS_START);
                    String paramSheet = row.get(COL_IDX_PARAMS_START + 1);
                    String paramMacro = row.get(COL_IDX_PARAMS_START + 2);
                    System.out.println("\tparamFile = " + paramFile);
                    System.out.println("\tparamSheet = " + paramSheet);
                    System.out.println("\tparamMacro = " + paramMacro);

                    // use macro cache if possible
                    // expand macro test steps, add them to cache
                    List<List<String>> macroSteps = harvestMacroSteps(paramFile, paramSheet, paramMacro);
                    if (CollectionUtils.isNotEmpty(macroSteps)) {
                        System.out.println("\tfound macro steps:");
                        System.out.println("\t\t" + TextUtils.toString(macroSteps, "\n\t\t"));

                        // 9. replace macro invocation step with expanded macro test steps
                        allTestSteps.remove(i);
                        for (int j = 0; j < macroSteps.size(); j++) {
                            List<String> macroStep = new ArrayList<>(macroSteps.get(j));
                            macroStep.add(0, j == 0 ? activityName : "");
                            allTestSteps.add(i + j, macroStep);
                        }
                        i += macroSteps.size();
                    }
                }
            }
        }

        protected void refillExpandedTestSteps(Worksheet sheet, List<List<String>> allTestSteps) {
            System.out.println("sheet " + sheet.getName() +
                               " - expanded test steps:\n\t\t" + TextUtils.toString(allTestSteps, "\n\t\t"));

            int rowStartIndex = ADDR_COMMAND_START.getRowStartIndex();

            // push expaneded test steps back to scenario sheet
            XSSFSheet excelSheet = sheet.getSheet();
            for (int i = 0; i < allTestSteps.size(); i++) {
                List<String> testStepRow = allTestSteps.get(i);
                XSSFRow excelRow = excelSheet.createRow(rowStartIndex + i);
                for (int j = 0; j < testStepRow.size(); j++) {
                    String cellValue = testStepRow.get(j);
                    excelRow.createCell(j, STRING).setCellValue(cellValue);
                }
            }
        }

        protected List<List<String>> harvestAllTestSteps(Worksheet sheet) {
            int lastCommandRow = sheet.findLastDataRow(ADDR_COMMAND_START);
            ExcelArea area = new ExcelArea(sheet, new ExcelAddress(TEST_STEPS_PREFIX + lastCommandRow), false);
            List<List<XSSFCell>> testStepArea = area.getWholeArea();
            List<List<String>> testStepData = new ArrayList<>();
            if (CollectionUtils.isEmpty(testStepArea)) { return testStepData; }

            XSSFSheet excelSheet = sheet.getSheet();
            testStepArea.forEach(row -> {
                List<String> testStepRow = new ArrayList<>();
                row.forEach(cell -> testStepRow.add(cell.getStringCellValue()));
                excelSheet.removeRow(excelSheet.getRow(row.get(0).getRowIndex()));
                testStepData.add(testStepRow);
            });

            return testStepData;
        }

        protected List<List<String>> harvestMacroSteps(String paramFile, String paramSheet, String paramMacro)
            throws IOException {

            File macroFile;
            if (FileUtil.isFileReadable(paramFile, 5000)) {
                macroFile = new File(paramFile);
            } else {
                String macroFilePath = StringUtils.appendIfMissing(testSheetFile.getParent(), separator) + paramFile;
                macroFile = new File(macroFilePath);
            }

            String macroKey = macroFile + ":" + paramSheet + ":" + paramMacro;
            System.out.println("macroKey = " + macroKey);

            if (parsedMacroSteps.containsKey(macroKey)) {
                // shortcircut: in case the same macro is referenced
                System.out.println("\t... found same macro " + macroKey);
                return parsedMacroSteps.get(macroKey);
            }

            // open specified sheet
            Excel macroExcel = new Excel(macroFile);
            Worksheet macroSheet = macroExcel.worksheet(paramSheet);
            int lastMacroRow = macroSheet.findLastDataRow(ADDR_MACRO_COMMAND_START);
            ExcelArea macroArea = new ExcelArea(macroSheet, new ExcelAddress("A2:L" + lastMacroRow), false);
            List<List<XSSFCell>> macroStepArea = macroArea.getWholeArea();
            List<List<String>> macroSteps = new ArrayList<>();
            boolean macroFound = false;

            // 6. read test steps based on macro name
            for (List<XSSFCell> macroRow : macroStepArea) {
                String currentMacroName = macroRow.get(0).getStringCellValue();
                if (StringUtils.equals(currentMacroName, paramMacro)) {
                    macroFound = true;
                    macroSteps.add(collectMacroStep(macroRow));
                    continue;
                }

                if (macroFound) {
                    if (StringUtils.isBlank(currentMacroName)) {
                        macroSteps.add(collectMacroStep(macroRow));
                    } else {
                        System.out.println("\t... save macros for " + macroKey);
                        parsedMacroSteps.put(macroKey, macroSteps);
                        macroSteps = new ArrayList<>();
                        macroFound = false;
                        break;
                    }
                }
            }

            if (macroFound && !macroSteps.isEmpty()) {
                System.out.println("\t... save macros for " + macroKey);
                parsedMacroSteps.put(macroKey, macroSteps);
            }

            System.out.println(TextUtils.toString(parsedMacroSteps, "\n\t\t", "="));
            return parsedMacroSteps.get(macroKey);
        }

        protected List<String> collectMacroStep(List<XSSFCell> macroRow) {
            List<String> oneStep = new ArrayList<>();
            for (int i = 1; i <= 11; i++) { oneStep.add(macroRow.get(i).getStringCellValue()); }
            return oneStep;
        }
    }

    @Before
    public void init() throws Exception {
        //NexialTestUtils.cleanupPastRemant(MergeMacroManualTest.class, );
    }

    @Test
    public void testMergeMacro() throws Exception {
        NexialTestUtils.setupCommonProps(testSheetFile, testSheetFile.getParent());

        MacroMerger merger = new MacroMerger();
        merger.mergeMacro(new Excel(testSheetFile));

        Map<String, List<List<String>>> teststeps = readTestSteps(new Excel(testSheetFile));

        teststeps.forEach((scenario, testStepData) -> {
            System.out.println("file:" + testSheetFile + ", scenario:" + scenario + ", testStepData:" + testStepData);

            if (StringUtils.equals(scenario, "scenario1")) {
                Assert.assertEquals(testStepData.get(0).toString(),
                                    "[hello, greeting, base, verbose(text), Hello World! It's now $(sysdate|now|yyyy-MM-dd HH:mm:ss.S), , , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(1).toString(),
                                    "[login, login as a username, desktop, login(username,password), ${username}, ${password}, , , , , , x, , , ]");
                Assert.assertEquals(testStepData.get(2).toString(),
                                    "[search office, search for office location, desktop, openTab(section,item), Section1, Item1, , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(3).toString(),
                                    "[, , desktop, search(gridVar,params), officeSearchResult, Search for Office Location=${location code}, , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(4).toString(),
                                    "[, , desktop, assertSearchResultColumn(column,expected), Location Code, ${location code}, , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(5).toString(),
                                    "[, , desktop, assertSearchResultRow(rowNum,expected), 1, ${location address}, , , , , , , , , ]");
            }

            if (StringUtils.equals(scenario, "scenario2")) {
                Assert.assertEquals(testStepData.get(0).toString(),
                                    "[login, login as a username, desktop, login(username,password), ${username}, ${password}, , , , , , x, , , ]");
                Assert.assertEquals(testStepData.get(1).toString(),
                                    "[login 2, let's try again, base, verbose(text), logging in again.. Make sure no issue, , , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(2).toString(),
                                    "[, login as a username, desktop, login(username,password), ${username}, ${password}, , , , , , x, , , ]");
                Assert.assertEquals(testStepData.get(3).toString(),
                                    "[search office, next, let's search AZ office, base, verbose(text), search office, , , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(4).toString(),
                                    "[, but first, add a new dynamic macro, excel, write(file,worksheet,startCell,var), MacroLibrary.xlsx, macros, A7, tell_time,tell the time,base,verbose(text),The time now is $(sysdate|now|MM/dd/yyyy HH:mm:ss.S)., , , , , , , ]");
                Assert.assertEquals(testStepData.get(5).toString(),
                                    "[, search for office location, desktop, openTab(section,item), Section1, Item1, , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(6).toString(),
                                    "[, , desktop, search(gridVar,params), officeSearchResult, Search for Office Location=${location code}, , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(7).toString(),
                                    "[, , desktop, assertSearchResultColumn(column,expected), Location Code, ${location code}, , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(8).toString(),
                                    "[, , desktop, assertSearchResultRow(rowNum,expected), 1, ${location address}, , , , , , , , , ]");
                Assert.assertEquals(testStepData.get(9).toString(),
                                    "[, call the dynamic macro, base, macro(file,sheet,name), MacroLibrary.xlsx, macros, tell_time, , , , , , , , ]");
            }
        });

        Excel.openExcel(testSheetFile);
    }

    protected Map<String, List<List<String>>> readTestSteps(Excel excel) {
        List<Worksheet> testSheets = InputFileUtils.retrieveValidTestScenarios(excel);
        Assert.assertEquals(2, CollectionUtils.size(testSheets));

        Map<String, List<List<String>>> teststeps = new HashMap<>();
        for (Worksheet sheet : testSheets) {
            // collect all test steps (also clear existing test steps in sheet)

            int lastCommandRow = sheet.findLastDataRow(ADDR_COMMAND_START);
            ExcelArea area = new ExcelArea(sheet, new ExcelAddress(TEST_STEPS_PREFIX + lastCommandRow), false);
            List<List<XSSFCell>> testStepArea = area.getWholeArea();
            Assert.assertTrue(CollectionUtils.isNotEmpty(testStepArea));

            String sheetName = sheet.getName();
            List<List<String>> testStepData = new ArrayList<>();
            testStepArea.forEach(row -> {
                List<String> testStepRow = new ArrayList<>();
                row.forEach(cell -> testStepRow.add(cell.getStringCellValue()));
                testStepData.add(testStepRow);
            });

            teststeps.put(sheetName, testStepData);
        }
        return teststeps;
    }

}
