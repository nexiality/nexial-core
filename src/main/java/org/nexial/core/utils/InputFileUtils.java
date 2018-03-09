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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP;
import static org.nexial.core.NexialConst.Data.SHEET_SYSTEM;
import static org.nexial.core.excel.ExcelConfig.*;

public final class InputFileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputFileUtils.class);

    private InputFileUtils() {}

    public static Excel toExcel(String file) {
        if (StringUtils.isBlank(file)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info("filename is blank"); }
            return null;
        }

        String errPrefix = "File (" + file + ") ";

        if (!FileUtil.isFileReadable(file, 5 * 1024)) {
            if (LOGGER.isInfoEnabled()) {
                File f = new File(file);
                LOGGER.info(errPrefix + "is not readable or accessible. "
                            + " exists? " + f.exists() + ","
                            + " is file? " + f.isFile() + ","
                            + " can read? " + f.canRead() + ","
                            + " file size=" + f.length());
            }
            return null;
        }

        // check that file is in Excel 2007 or above format
        if (!Excel.isXlsxVersion(file)) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("\n\n\n"
                            + StringUtils.repeat("!", 80) + "\n"
                            + errPrefix + "\n"
                            + "is either unreadable, not of version Excel 2007 or above, or is currently open.\n"
                            + "If this file is currently open, please close it before retrying again.\n"
                            + StringUtils.repeat("!", 80) + "\n"
                            + "\n\n");
            }
            return null;
        }

        try {
            return new Excel(new File(file), DEF_OPEN_EXCEL_AS_DUP);
        } catch (IOException e) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "cannot be loaded: " + e.getMessage()); }
            return null;
        }
    }

    public static boolean isValidDataFile(String file) {
        Excel excel = toExcel(file);
        if (excel == null) { return false; }

        // at least 1 data sheet
        List<Worksheet> dataSheets = filterValidDataSheets(excel);
        if (CollectionUtils.isEmpty(dataSheets)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info("File (" + file + ") contains no valid data sheet"); }
            return false;
        }

        return CollectionUtils.size(dataSheets) > 0;
    }

    public static List<Worksheet> filterValidDataSheets(Excel excel) {
        List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
        if (CollectionUtils.isEmpty(allSheets)) { return new ArrayList<>(); }
        return allSheets.stream().filter(InputFileUtils::isValidDataSheet).collect(Collectors.toList());
    }

    public static Worksheet getValidDataSheet(Excel excel, String sheetName) {
        if (excel == null || StringUtils.isBlank(sheetName)) { return null; }
        Worksheet sheet = excel.worksheet(sheetName);
        return sheet != null && isValidDataSheet(sheet) ? sheet : null;
    }

    public static boolean isValidDataSheet(Worksheet sheet) {
        if (sheet == null) { return false; }

        String errPrefix = "File (" + sheet.getFile().getAbsolutePath() + ") sheet (" + sheet.getName() + ") ";

        // for every data sheet, at least 1 1x2 row
        ExcelAddress addrMinimiumData = new ExcelAddress("A1:B1");
        List<List<XSSFCell>> dataCells = sheet.cells(addrMinimiumData);
        if (CollectionUtils.isEmpty(dataCells)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "does not contain data or the expected format"); }
            return false;
        }

        for (List<XSSFCell> row : dataCells) {
            if (CollectionUtils.isEmpty(row)) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(errPrefix + "does not contain data or the expected format in " + addrMinimiumData);
                }
                return false;
            }

            for (XSSFCell cell : row) {
                if (cell == null || StringUtils.isBlank(cell.getStringCellValue())) {
                    LOGGER.info(errPrefix + "does not contain data or the expected format in " + addrMinimiumData);
                    return false;
                }
            }
        }

        // find the end of the dta block
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

    public static boolean isValidScript(String file) {
        Excel excel = toExcel(file);
        if (excel == null) { return false; }

        if (!hasValidSystemSheet(excel)) { return false; }

        List<Worksheet> validScenarios = retrieveValidTestScenarios(excel);
        return !CollectionUtils.isEmpty(validScenarios);
    }

    public static boolean isValidMacro(String file) {
        Excel excel = toExcel(file);
        return excel != null && hasValidSystemSheet(excel) && !CollectionUtils.isEmpty(retrieveValidMacros(excel));
    }

    /**
     * filter all the worksheets in {@code excel} that are considered as valid test script.
     */
    public static List<Worksheet> retrieveValidTestScenarios(Excel excel) {
        String errPrefix = "File (" + excel.getFile() + ") ";

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

            // check summary header
            if (!Excel.isRowTextFound(sheet,
                                      HEADER_SCENARIO_INFO,
                                      ADDR_HEADER_SCENARIO_INFO1,
                                      ADDR_HEADER_SCENARIO_INFO2)) {
                LOGGER.info(errPrefix1 + "required script header not found at " +
                            ArrayUtils.toString(ADDR_HEADER_SCENARIO_INFO1) + "," +
                            ArrayUtils.toString(ADDR_HEADER_SCENARIO_INFO2) + "; ignoring this worksheet...");
                return false;
            }

            // check execution summary
            if (!Excel.isRowTextFound(sheet,
                                      Collections.singletonList(HEADER_EXEC_SUMMARY),
                                      ADDR_SCENARIO_EXEC_SUMMARY_HEADER)) {
                LOGGER.info(errPrefix1 + "required script header not found at " +
                            ArrayUtils.toString(ADDR_SCENARIO_EXEC_SUMMARY_HEADER) + "; ignoring this worksheet...");
                return false;
            }

            // check test script header
            if (isV1Script(sheet)) {
                // outdated format... need to run update script
                LOGGER.warn(errPrefix1 + "Outdated format found at " + ArrayUtils.toString(ADDR_HEADER_TEST_STEP) +
                            "; please run bin/nexial-script-update.cmd to update your test script");
                // todo: can't we just update it silently?
            } else {
                if (!Excel.isRowTextFound(sheet, HEADER_TEST_STEP_LIST_V2, ADDR_HEADER_TEST_STEP)) {
                    LOGGER.info(errPrefix1 + "required script header not found at " +
                                ArrayUtils.toString(ADDR_HEADER_TEST_STEP) + "; ignoring this worksheet...");
                    return false;
                }
            }

            // check that all valid scenario sheet has at least 1 command
            int lastRowIndex = sheet.findLastDataRow(ADDR_COMMAND_START);
            if (lastRowIndex - ADDR_COMMAND_START.getRowStartIndex() <= 0) {
                if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix1 + "does not contain any test steps"); }
                return false;
            }

            // make sure that the first command row also specifies test case
            XSSFCell cellTestCase = sheet.cell(new ExcelAddress("" + COL_TEST_CASE +
                                                                ADDR_COMMAND_START.getRowStartIndex()));
            return !(cellTestCase == null || StringUtils.isBlank(cellTestCase.getStringCellValue()));
        }).collect(Collectors.toList());

        // check that all valid scenario sheet do not have result (output file)
    }

    /**
     * filter all the worksheets in {@code excel} that are considered as valid Nexial test script.
     */
    public static List<Worksheet> retrieveValidMacros(Excel excel) {
        String errPrefix = "File (" + excel.getFile() + ") ";

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
            XSSFCell cellMacroName = sheet.cell(new ExcelAddress("" + COL_TEST_CASE +
                                                                 ADDR_MACRO_COMMAND_START.getRowStartIndex()));
            return !(cellMacroName == null || StringUtils.isBlank(cellMacroName.getStringCellValue()));
        }).collect(Collectors.toList());

        // check that all valid scenario sheet do not have result (output file)
    }

    public static boolean isV1Script(Worksheet sheet) {
        return Excel.isRowTextFound(sheet, HEADER_TEST_STEP_LIST_V1, ADDR_HEADER_TEST_STEP);
    }

    public static boolean isV2Script(Worksheet worksheet) {
        return Excel.isRowTextFound(worksheet,
                                    HEADER_SCENARIO_INFO_V1,
                                    ADDR_HEADER_SCENARIO_INFO1,
                                    ADDR_HEADER_SCENARIO_INFO2);
    }

    public static boolean hasValidSystemSheet(Excel excel) {
        // check for #system sheet
        String errPrefix = "File (" + excel.getFile() + ") 'system' sheet ";

        Worksheet systemSheet = excel.worksheet(SHEET_SYSTEM);
        if (systemSheet == null) {
            // if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "does not exist"); }
            return false;
        }

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
                   .filter(cell -> cell != null && StringUtils.isNotBlank(cell.getStringCellValue()))
                   .forEach(cell -> targets.add(cell.getStringCellValue()));
        //if (LOGGER.isDebugEnabled()) { LOGGER.debug(errPrefix + "has commands of type: " + targets); }

        List<String> targets2 = new ArrayList<>();
        systemSheet.cells(new ExcelAddress("A2:A" + (lastColumn + 2)))
                   .stream()
                   .filter(row -> row != null &&
                                  row.get(0) != null &&
                                  StringUtils.isNotBlank(row.get(0).getStringCellValue()))
                   .forEach(row -> targets2.add(row.get(0).getStringCellValue()));
        //if (LOGGER.isDebugEnabled()) { LOGGER.debug(errPrefix + "has targets: " + targets2); }

        if (!CollectionUtils.isEqualCollection(targets, targets2)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "missing target(s) or command(s)."); }
            return false;
        }

        return true;
    }

    public static boolean isValidPlanFile(String file) {
        Excel excel = toExcel(file);
        if (excel == null) { return false; }

        List<Worksheet> validPlanSheets = retrieveValidPlanSequence(excel);
        return !CollectionUtils.isEmpty(validPlanSheets);
    }

    public static List<Worksheet> retrieveValidPlanSequence(Excel excel) {
        String errPrefix = "File (" + excel.getFile() + ") ";

        // check for at least 1 test scenario
        List<Worksheet> allSheets = excel.getWorksheetsStartWith("");
        if (CollectionUtils.isEmpty(allSheets)) {
            if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix + "is missing test scenarios."); }
            return null;
        }

        // check that every test scenario is of right format (warn only if format is wrong)
        return allSheets.stream().filter(sheet -> {
            String sheetName = sheet.getName();

            String errPrefix1 = errPrefix + "worksheet (" + sheetName + ") ";

            // check summary header
            if (!Excel.isRowTextFound(sheet,
                                      PLAN_HEADER_SEQUENCE,
                                      ADDR_PLAN_HEADER_SEQUENCE1,
                                      ADDR_PLAN_HEADER_SEQUENCE2)) {
                LOGGER.info(errPrefix1 + "required plan header not found at " +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_SEQUENCE1) + "," +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_SEQUENCE2) + "; ignoring this worksheet...");
                return false;
            }

            // check execution summary
            if (!Excel.isRowTextFound(sheet,
                                      Collections.singletonList(HEADER_EXEC_SUMMARY),
                                      ADDR_PLAN_HEADER_EXEC_SUMMARY_HEADER)) {
                LOGGER.info(errPrefix1 + "required plan header not found at " +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_SEQUENCE1) + "," +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_SEQUENCE2) + "; ignoring this worksheet...");
                return false;
            }

            // check execution header
            if (!Excel.isRowTextFound(sheet, PLAN_HEADER_EXECUTION, ADDR_PLAN_HEADER_EXECUTION)) {
                LOGGER.info(errPrefix1 + "required plan header not found at " +
                            ArrayUtils.toString(ADDR_PLAN_HEADER_EXECUTION) + "; ignoring this worksheet...");
                return false;
            }

            // check that all valid scenario sheet has at least 1 command
            int lastRowIndex = sheet.findLastDataRow(ADDR_PLAN_EXECUTION_START);
            if (lastRowIndex - ADDR_PLAN_EXECUTION_START.getRowStartIndex() <= 0) {
                if (LOGGER.isInfoEnabled()) { LOGGER.info(errPrefix1 + "does not contain any test steps"); }
                return false;
            }

            return true;
        }).collect(Collectors.toList());

        // check that all valid scenario sheet do not have result (output file)
    }
}
