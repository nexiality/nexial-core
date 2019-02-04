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

package org.nexial.core.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP;
import static org.nexial.core.NexialConst.Data.SHEET_SYSTEM;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.utils.ExecUtils.BIN_SCRIPT_EXT;

public final class InputFileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputFileUtils.class);
    private static final ExcelAddress FIRST_TEST_STEP = new ExcelAddress("" + COL_TEST_CASE +
                                                                         ADDR_COMMAND_START.getRowStartIndex());
    private static final String MSG_V1_HEADER_FOUND = "Outdated 'header' format found at " +
                                                      ArrayUtils.toString(ADDR_HEADER_SCENARIO_INFO1) + "," +
                                                      ArrayUtils.toString(ADDR_HEADER_SCENARIO_INFO2) + "," +
                                                      ArrayUtils.toString(ADDR_HEADER_TEST_STEP) + "; please run " +
                                                      "bin/nexial-script-update" + BIN_SCRIPT_EXT +
                                                      " to update your test script";
    private static final String MSG_HEADER_NOT_FOUND = "required script header NOT found at " +
                                                       ArrayUtils.toString(ADDR_HEADER_SCENARIO_INFO1) + "," +
                                                       ArrayUtils.toString(ADDR_HEADER_SCENARIO_INFO2);
    private static final String MSG_V1_SCRIPT_HEADER = "Outdated format found at " +
                                                       ArrayUtils.toString(ADDR_HEADER_TEST_STEP) + "; please run " +
                                                       "bin/nexial-script-update" + BIN_SCRIPT_EXT +
                                                       " to update your test script";
    private static final String MSG_SCRIPT_HEADER_NOT_FOUND = "required script header not found at " +
                                                              ArrayUtils.toString(ADDR_HEADER_TEST_STEP);
    private static final String MSG_MISSING_TEST_ACTIVITY = "First test step must be accompanied by a test activity";

    private InputFileUtils() {}

    public static List<Worksheet> findMatchingSheets(String file, Function<Excel, List<Worksheet>> matcher) {
        Excel excel = null;
        try {
            excel = toExcel(file);
            return excel == null ? new ArrayList<>() : matcher.apply(excel);
        } finally {
            if (DEF_OPEN_EXCEL_AS_DUP && excel != null) { FileUtils.deleteQuietly(excel.getFile().getParentFile()); }
        }
    }

    public static int countMatchingSheets(String file, Function<Excel, Integer> matcher) {
        Excel excel = null;
        try {
            excel = toExcel(file);
            return excel == null ? 0 : matcher.apply(excel);
        } finally {
            if (DEF_OPEN_EXCEL_AS_DUP && excel != null) { FileUtils.deleteQuietly(excel.getFile().getParentFile()); }
        }
    }

    public static boolean hasMatchingSheets(String file, Function<Excel, Boolean> matcher) {
        Excel excel = null;
        try {
            excel = toExcel(file);
            return excel == null ? false : matcher.apply(excel);
        } finally {
            if (DEF_OPEN_EXCEL_AS_DUP && excel != null) { FileUtils.deleteQuietly(excel.getFile().getParentFile()); }

            // (2018/12/16,automike): memory consumption precaution
            if (excel != null) {
                try {
                    excel.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Unable to close Excel file (" + file + "): " + e.getMessage());
                }
            }
        }
    }

    public static Excel toExcel(String file) {
        if (StringUtils.isBlank(file)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info("filename is blank"); }
            return null;
        }

        try {
            // check that file is in Excel 2007 or above format
            return Excel.asXlsxExcel(file, DEF_OPEN_EXCEL_AS_DUP, false);
        } catch (IOException e) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info("File (" + file + ") cannot be loaded: " + e.getMessage()); }
            return null;
        }
    }

    public static Excel asDataFile(String file) {
        final Excel excel = toExcel(file);
        if (excel == null) { return null; }

        List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
        if (CollectionUtils.isEmpty(allSheets)) {
            ConsoleUtils.error("File " + file + " contains no worksheet; NOT A VALID DATA FILE");
            return null;
        }

        if (allSheets.stream().anyMatch(InputFileUtils::isValidDataSheet)) { return excel; }

        ConsoleUtils.error("File " + file + " contains NO valid data sheets");
        return null;
    }

    public static boolean isValidDataFile(String file) {
        return hasMatchingSheets(file, excel -> {
            List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
            if (CollectionUtils.isEmpty(allSheets)) { return false; }
            return allSheets.stream().anyMatch(InputFileUtils::isValidDataSheet);
        });
    }

    public static List<Worksheet> filterValidDataSheets(Excel excel) {
        List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
        if (CollectionUtils.isEmpty(allSheets)) { return new ArrayList<>(); }
        return allSheets.stream().filter(InputFileUtils::isValidDataSheet).collect(Collectors.toList());
    }

    public static boolean isValidDataSheet(Worksheet sheet) {
        if (sheet == null) { return false; }

        Excel excel = sheet.excel();
        String errPrefix = deriveFileErrorPrefix(excel) + "sheet (" + sheet.getName() + ") ";

        // for every data sheet, at least 1 1x2 row
        ExcelAddress addrMinimumData = new ExcelAddress("A1:B1");
        List<List<XSSFCell>> dataCells = sheet.cells(addrMinimumData);
        if (CollectionUtils.isEmpty(dataCells)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "does not contain data or the expected format"); }
            return false;
        }

        for (List<XSSFCell> row : dataCells) {
            if (CollectionUtils.isEmpty(row)) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(errPrefix + "does not contain data or the expected format in " + addrMinimumData);
                }
                return false;
            }

            for (XSSFCell cell : row) {
                if (StringUtils.isBlank(Excel.getCellValue(cell))) {
                    LOGGER.info(errPrefix + "does not contain data or the expected format in " + addrMinimumData);
                    return false;
                }
            }
        }

        // find the end of the data block
        int lastRowIndex = sheet.findLastDataRow(new ExcelAddress("A1:A1"));
        ExcelAddress addrDataBlock = new ExcelAddress("A1:A" + lastRowIndex);
        List<List<XSSFCell>> rows = sheet.cells(addrDataBlock);
        if (CollectionUtils.isEmpty(rows)) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(errPrefix + "expects contiguous set of data at " + addrDataBlock + " but none found");
            }
            return false;
        }

        for (List<XSSFCell> row : rows) {
            if (CollectionUtils.isEmpty(row)) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(errPrefix + "expects non-empty data at " + addrDataBlock + " but none found");
                }
                return false;
            }

            XSSFCell cell = row.get(0);
            String data = cell.getCellTypeEnum() == STRING ? cell.getStringCellValue() : cell.getRawValue();
            if (StringUtils.isBlank(data)) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(errPrefix + "expects contiguous set of data at " + addrDataBlock +
                                " but blank data found");
                }
                return false;
            }
        }

        return true;
    }

    public static Excel resolveValidScript(String file) {
        Excel excel = null;
        try {
            excel = toExcel(file);
            if (excel == null) { return null; }
            if (isValidScript(excel)) {
                return excel;
            } else {
                ConsoleUtils.error("test script (" + file + ") is not readable or does not contain valid format.");
                return null;
            }
        } finally {
            if (DEF_OPEN_EXCEL_AS_DUP && excel != null) { FileUtils.deleteQuietly(excel.getFile().getParentFile()); }
        }
    }

    public static boolean isValidScript(String file) { return hasMatchingSheets(file, InputFileUtils::isValidScript); }

    public static boolean isValidScript(Excel excel) {
        return hasValidSystemSheet(excel) && CollectionUtils.isNotEmpty(retrieveValidTestScenarios(excel));
    }

    public static boolean isValidMacro(String file) { return hasMatchingSheets(file, InputFileUtils::isValidMacro); }

    public static boolean isValidMacro(Excel excel) {
        return hasValidSystemSheet(excel) && CollectionUtils.isNotEmpty(retrieveValidMacros(excel));
    }

    @NotNull
    public static List<String> retrieveValidTestScenarioNames(Excel excel) {
        List<Worksheet> allScenarios = InputFileUtils.retrieveValidTestScenarios(excel);
        ArrayList<String> scenarioNames = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(allScenarios)) {
            allScenarios.forEach(sheet -> scenarioNames.add(sheet.getName()));
        }
        return scenarioNames;
    }

    /**
     * filter all the worksheets in {@code excel} that are considered as valid test script.
     */
    public static List<Worksheet> retrieveValidTestScenarios(Excel excel) {
        String errPrefix = deriveFileErrorPrefix(excel);

        // check for at least 1 test scenario
        List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
        if (CollectionUtils.isEmpty(allSheets)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "is missing test scenarios."); }
            return null;
        }

        // check that every test scenario is of right format (warn only if format is wrong)
        return allSheets.stream().filter(sheet -> {
            String sheetName = sheet.getName();
            if (StringUtils.equals(sheetName, SHEET_SYSTEM)) { return false; }

            String errPrefix1 = errPrefix + "worksheet (" + sheetName + ") ";

            // CHECK 1: check execution summary
            if (!Excel.isRowTextFound(sheet,
                                      Collections.singletonList(HEADER_EXEC_SUMMARY),
                                      ADDR_SCENARIO_EXEC_SUMMARY_HEADER)) {
                LOGGER.info(errPrefix1 + "required script header not found at " +
                            ArrayUtils.toString(ADDR_SCENARIO_EXEC_SUMMARY_HEADER) + "; ignoring this worksheet...");
                return false;
            }

            // CHECK 2: check that all valid scenario sheet has at least 1 command
            int lastRowIndex = sheet.findLastDataRow(ADDR_COMMAND_START);

            // disabled since we don't need actual test step to quality a valid script
            // if (lastRowIndex - ADDR_COMMAND_START.getRowStartIndex() <= 0) {
            //     if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix1 + "does not contain any tests"); }
            //     return false;
            // }

            // CHECK 3: make sure that the first command row also specifies test case
            if (lastRowIndex - ADDR_COMMAND_START.getRowStartIndex() > 0) {
                XSSFCell cellTestCase = sheet.cell(FIRST_TEST_STEP);
                if (StringUtils.isBlank(Excel.getCellValue(cellTestCase))) {
                    if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix1 + MSG_MISSING_TEST_ACTIVITY); }
                }
            }

            // CHECK 4: check summary header is either V1 or V2
            if (Excel.isRowTextFound(sheet,
                                     HEADER_SCENARIO_INFO_V1,
                                     ADDR_HEADER_SCENARIO_INFO1,
                                     ADDR_HEADER_SCENARIO_INFO2)) {
                // found V1 header
                LOGGER.warn(errPrefix1 + MSG_V1_HEADER_FOUND);
                // accepted, but WARN
            } else {
                if (!Excel.isRowTextFound(sheet,
                                          HEADER_SCENARIO_INFO,
                                          ADDR_HEADER_SCENARIO_INFO1,
                                          ADDR_HEADER_SCENARIO_INFO2)) {
                    // found no V1 nor V2 header
                    LOGGER.info(errPrefix1 + MSG_HEADER_NOT_FOUND + "; ignoring this worksheet...");
                    return false;
                }
            }

            // CHECK 5: check test script header
            if (isV1Script(sheet)) {
                // outdated format... need to run update script
                LOGGER.warn(errPrefix1 + MSG_V1_SCRIPT_HEADER);
            } else if (isV15Script(sheet)) {
                // outdated format... need to run update script
                LOGGER.warn(errPrefix1 + MSG_V1_SCRIPT_HEADER);
            } else {
                if (!Excel.isRowTextFound(sheet, HEADER_TEST_STEP_LIST_V2, ADDR_HEADER_TEST_STEP)) {
                    // found no V1, V1.5 nor V2 header
                    LOGGER.info(errPrefix1 + MSG_SCRIPT_HEADER_NOT_FOUND + "; ignoring this worksheet...");
                    return false;
                }
            }

            return true;
        }).collect(Collectors.toList());

        // check that all valid scenario sheet do not have result (output file)
    }

    /**
     * filter all the worksheets in {@code excel} that are considered as valid Nexial test script.
     */
    public static List<Worksheet> retrieveValidMacros(Excel excel) {
        String errPrefix = deriveFileErrorPrefix(excel);

        // check for at least 1 test scenario
        List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
        if (CollectionUtils.isEmpty(allSheets)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "is missing test scenarios."); }
            return null;
        }

        // check that every test scenario is of right format (warn only if format is wrong)
        return allSheets.stream().filter(sheet -> {
            String sheetName = sheet.getName();
            if (StringUtils.equals(sheetName, SHEET_SYSTEM)) { return false; }

            String errPrefix1 = errPrefix + "worksheet (" + sheetName + ") ";

            // check test script header
            if (!Excel.isRowTextFound(sheet, HEADER_MACRO_TEST_STEPS, ADDR_HEADER_MACRO)) {
                LOGGER.info(errPrefix1 + "required macro header not found at " +
                            ArrayUtils.toString(HEADER_MACRO_TEST_STEPS) + "; ignoring this worksheet...");
                return false;
            }

            // check that all valid scenario sheet has at least 1 command
            int lastRowIndex = sheet.findLastDataRow(ADDR_MACRO_COMMAND_START);
            if (lastRowIndex - ADDR_MACRO_COMMAND_START.getRowStartIndex() <= 0) {
                if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix1 + "does not contain any test steps"); }
                return false;
            }

            // make sure that the first command row also specifies test case
            XSSFCell cellMacroName =
                sheet.cell(new ExcelAddress("" + COL_TEST_CASE + ADDR_MACRO_COMMAND_START.getRowStartIndex()));
            return StringUtils.isNotBlank(Excel.getCellValue(cellMacroName));
        }).collect(Collectors.toList());

        // check that all valid scenario sheet do not have result (output file)
    }

    public static boolean isV1Script(Worksheet sheet) {
        return Excel.isRowTextFound(sheet, HEADER_TEST_STEP_LIST_V1, ADDR_HEADER_TEST_STEP);
    }

    public static boolean isV15Script(Worksheet sheet) {
        return Excel.isRowTextFound(sheet, HEADER_TEST_STEP_LIST_V15, ADDR_HEADER_TEST_STEP);
    }

    /**
     * V2 script header looks like "description | project | release | jira | zephyr | author"
     */
    public static boolean isV2Script(Worksheet worksheet) {
        return Excel.isRowTextFound(worksheet,
                                    HEADER_SCENARIO_INFO_V1,
                                    ADDR_HEADER_SCENARIO_INFO1,
                                    ADDR_HEADER_SCENARIO_INFO2) &&
               Excel.isRowTextFound(worksheet, HEADER_TEST_STEP_LIST_V2, ADDR_HEADER_TEST_STEP);
    }

    public static boolean hasValidSystemSheet(Excel excel) {
        // check for #system sheet
        String errPrefix = deriveFileErrorPrefix(excel) + "'system' sheet ";

        Worksheet systemSheet = excel.worksheet(SHEET_SYSTEM);
        if (systemSheet == null) { return false; }

        // check that #system sheet has targets and commands
        int lastColumn = systemSheet.findLastDataColumn(new ExcelAddress("A1"));
        if (lastColumn < 2) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "does not have right format."); }
            return false;
        }

        List<String> targets = new ArrayList<>();
        systemSheet.cells(new ExcelAddress("B1:" + (char) ('A' + lastColumn - 1) + "1"))
                   .get(0)
                   .stream()
                   .filter(cell -> StringUtils.isNotBlank(Excel.getCellValue(cell)))
                   .forEach(cell -> targets.add(Excel.getCellValue(cell)));
        //if (LOGGER.isDebugEnabled()) { LOGGER.debug(errPrefix + "has commands of type: " + targets); }

        List<String> targets2 = new ArrayList<>();
        systemSheet.cells(new ExcelAddress("A2:A" + (lastColumn + 2)))
                   .stream()
                   .filter(row -> row != null && StringUtils.isNotBlank(Excel.getCellValue(row.get(0))))
                   .forEach(row -> targets2.add(Excel.getCellValue(row.get(0))));
        //if (LOGGER.isDebugEnabled()) { LOGGER.debug(errPrefix + "has targets: " + targets2); }

        if (!CollectionUtils.isEqualCollection(targets, targets2)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "missing target(s) or command(s)."); }
            return false;
        }

        return true;
    }

    public static boolean isValidPlanFile(String file) {
        return hasMatchingSheets(file, excel -> {
            // check for at least 1 test scenario
            List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
            if (CollectionUtils.isEmpty(allSheets)) {
                if (LOGGER.isInfoEnabled()) { LOGGER.info(deriveFileErrorPrefix(excel) + "is missing test plans."); }
                return false;
            }

            // check that every test scenario is of right format (warn only if format is wrong)
            return allSheets.stream().allMatch(InputFileUtils::isValidPlan);
        });
    }

    public static List<Worksheet> retrieveValidPlanSequence(Excel excel) {

        // check for at least 1 test scenario
        List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
        if (CollectionUtils.isEmpty(allSheets)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(deriveFileErrorPrefix(excel) + "is missing test plans."); }
            return null;
        }

        // check that every test scenario is of right format (warn only if format is wrong)
        return allSheets.stream().filter(InputFileUtils::isValidPlan).collect(Collectors.toList());

        // check that all valid scenario sheet do not have result (output file)
    }

    /**
     * V2 plan header looks like "summary | author | jira override | zephyr override | notification override"
     */
    public static boolean isV2Plan(Worksheet worksheet) {
        return Excel.isRowTextFound(worksheet,
                                    HEADER_SCENARIO_INFO_V1,
                                    ADDR_HEADER_SCENARIO_INFO1,
                                    ADDR_HEADER_SCENARIO_INFO2) &&
               Excel.isRowTextFound(worksheet, HEADER_TEST_STEP_LIST_V2, ADDR_HEADER_TEST_STEP);
    }

    public static List<Worksheet> retrieveV2Plan(Excel excel) {
        String errPrefix = deriveFileErrorPrefix(excel);

        // check for at least 1 test scenario
        List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
        if (CollectionUtils.isEmpty(allSheets)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "contains no worksheet."); }
            return null;
        }

        // check that every test scenario is of right format (warn only if format is wrong)
        return allSheets.stream().filter(sheet -> {
            // check summary header (v2) and execution summary and execution header
            if (Excel.isRowTextFound(sheet,
                                     PLAN_HEADER_SEQUENCE_V2,
                                     ADDR_PLAN_HEADER_SEQUENCE1,
                                     ADDR_PLAN_HEADER_SEQUENCE2) &&
                Excel.isRowTextFound(sheet,
                                     Collections.singletonList(HEADER_EXEC_SUMMARY),
                                     ADDR_PLAN_HEADER_EXEC_SUMMARY_HEADER) &&
                Excel.isRowTextFound(sheet, PLAN_HEADER_EXECUTION, ADDR_PLAN_HEADER_EXECUTION)) {
                // could be V2

                // check that all valid scenario sheet has at least 1 command
                int lastRowIndex = sheet.findLastDataRow(ADDR_PLAN_EXECUTION_START);
                return lastRowIndex - ADDR_PLAN_EXECUTION_START.getRowStartIndex() > 0;
            } else {
                return false;
            }
        }).collect(Collectors.toList());
    }

    protected static boolean isValidPlan(Worksheet sheet) {
        if (sheet == null) { return false; }

        String sheetName = sheet.getName();
        Excel excel = sheet.excel();
        String errPrefix = deriveFileErrorPrefix(excel);
        String errPrefix1 = errPrefix + "worksheet (" + sheetName + ") ";

        // check summary header
        if (!Excel.isRowTextFound(sheet,
                                  PLAN_HEADER_SEQUENCE,
                                  ADDR_PLAN_HEADER_SEQUENCE1,
                                  ADDR_PLAN_HEADER_SEQUENCE2)) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(errPrefix1 + "required plan header not found at " +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_SEQUENCE1) + "," +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_SEQUENCE2) + "; ignoring this worksheet...");
            }
            return false;
        }

        // check execution summary
        if (!Excel.isRowTextFound(sheet,
                                  Collections.singletonList(HEADER_EXEC_SUMMARY),
                                  ADDR_PLAN_HEADER_EXEC_SUMMARY_HEADER)) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(errPrefix1 + "required plan header not found at " +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_SEQUENCE1) + "," +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_SEQUENCE2) + "; ignoring this worksheet...");
            }
            return false;
        }

        // check execution header
        if (!Excel.isRowTextFound(sheet, PLAN_HEADER_EXECUTION, ADDR_PLAN_HEADER_EXECUTION)) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(errPrefix1 + "required plan header not found at " +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_EXECUTION) + "; ignoring this worksheet...");
            }
            return false;
        }

        // check that all valid scenario sheet has at least 1 command
        int lastRowIndex = sheet.findLastDataRow(ADDR_PLAN_EXECUTION_START);
        if (lastRowIndex - ADDR_PLAN_EXECUTION_START.getRowStartIndex() <= 0) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix1 + "does not contain any test steps"); }
            return false;
        }

        return true;
    }

    @NotNull
    private static String deriveFileErrorPrefix(Excel excel) {
        return "File (" + ObjectUtils.defaultIfNull(excel.getOriginalFile(), excel.getFile()) + ") ";
    }
}
