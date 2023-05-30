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

package org.nexial.core.model;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.ExecutionInputPrep;
import org.nexial.core.ExecutionThread;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.excel.ExcelStyleHelper;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.InputFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

import static java.io.File.separator;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.core.CommandConst.*;
import static org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP;
import static org.nexial.core.NexialConst.Data.SECTION_DESCRIPTION_PREFIX;
import static org.nexial.core.NexialConst.Project.SCRIPT_FILE_EXT;
import static org.nexial.core.NexialConst.Project.SCRIPT_FILE_SUFFIX;
import static org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE;
import static org.nexial.core.excel.ExcelConfig.*;

public class MacroMerger {
    private static final String TEST_STEPS_PREFIX = FIRST_STEP_ROW + ":" + COL_REASON;
    private static final String CONDITION_DISABLE = "SkipIf(true) ";
    // (2018/12/9,automike): remove to support dynamic macro changes during execution and interactive mode
    // private static final Map<String, List<List<String>>> MACRO_CACHE = new HashMap<>();

    private Excel excel;
    private ExecutionDefinition execDef;
    private TestProject project;
    private int currentIteration;

    public void setExecDef(ExecutionDefinition execDef) { this.execDef = execDef; }

    public void setProject(TestProject project) { this.project = project; }

    public void setCurrentIteration(int currentIteration) { this.currentIteration = currentIteration; }

    public void setExcel(Excel excel) { this.excel = excel; }

    public void mergeMacro() throws IOException {
        excel.getWorkbook().setMissingCellPolicy(CREATE_NULL_AS_BLANK);

        // find all scenario sheets
        List<String> expectedScenarios = new ArrayList<>(execDef.getScenarios());
        assert CollectionUtils.isNotEmpty(expectedScenarios) : "No scenario specified for " + execDef.getTestScript();

        List<Worksheet> testSheets = InputFileUtils.retrieveValidTestScenarios(excel);
        assert CollectionUtils.isNotEmpty(testSheets) :
            "Specified scenario(s) of " + execDef.getTestScript() + " not found: " + expectedScenarios;

        // ensure that all the sheets found in script file are the same as initially scanned and determined as scenario
        testSheets.forEach(worksheet -> expectedScenarios.remove(worksheet.getName()));
        assert CollectionUtils.isEmpty(expectedScenarios) :
            "Invalid scenario(s) found in " + execDef.getTestScript() + ": " + expectedScenarios;

        boolean fileModified = false;

        // scan all test steps
        for (Worksheet sheet : testSheets) {
            // collect all test steps (also clear existing test steps in sheet)
            List<List<String>> allTestSteps = harvestAllTestSteps(sheet);

            // scan through all test steps
            if (expandTestSteps(allTestSteps)) { fileModified = true; }
            refillExpandedTestSteps(sheet, allTestSteps);
        }

        // save excel, rinse and repeat.
        // (2018/10/18,automike): skip saving now because this file will be saved later anyways
        if (fileModified) {
            ConsoleUtils.log("macro(s) found and merged; " + excel.getFile() + " will need to be saved");
            //     ConsoleUtils.log("macro(s) merged, saving excel " + excel.getFile());
            //     excel.save();
        }
    }

    /**
     * A macro library file can be specified as full path or relative path. Relative path may be pivoted at project
     * home or artifact/script dir
     */
    @NotNull
    public static File resolveMacroFile(TestProject project, String macroFile) throws IOException {
        // first, try it as is
        if (FileUtil.isFileReadable(macroFile, MIN_EXCEL_FILE_SIZE)) { return new File(macroFile); }

        // next, try pivoting from artifact/script
        macroFile = StringUtils.appendIfMissing(macroFile, "." + SCRIPT_FILE_SUFFIX);
        String macroFilePath = StringUtils.appendIfMissing(project.getScriptPath(), separator) + macroFile;
        if (!FileUtil.isFileReadable(macroFilePath, MIN_EXCEL_FILE_SIZE)) {
            // last, try again pivoting now from project home
            macroFilePath = StringUtils.appendIfMissing(project.getProjectHome(), separator) + macroFile;
            if (!FileUtil.isFileReadable(macroFilePath, MIN_EXCEL_FILE_SIZE)) {
                throw new IOException("Unable to read macro file '" + macroFile + "'");
            }
        }

        return new File(macroFilePath);
    }

    protected boolean expandTestSteps(List<List<String>> allTestSteps) throws IOException {
        boolean macroExpanded = false;

        // int sectionSteps = 0;
        int repeatUntilStepIndex = -1;
        int repeatUntilSteps = -1;
        int repeatUntilLastStepIndex = -1;

        for (int i = 0; i < allTestSteps.size(); i++) {
            if (i > repeatUntilLastStepIndex) {
                // we are out of the repeat-until loop
                repeatUntilStepIndex = -1;
                repeatUntilSteps = -1;
                repeatUntilLastStepIndex = -1;
            }

            List<String> row = allTestSteps.get(i);
            String cellTarget = row.get(COL_IDX_TARGET);
            String cellCommand = row.get(COL_IDX_COMMAND);
            String testCommand = cellTarget + "." + cellCommand;

            // look for base.repeatUntil(step,maxWaitMs) so that we can adjust step count to compensate for embedded macro
            if (StringUtils.equals(testCommand, CMD_REPEAT_UNTIL)) {
                // scan for presence of macro command within its boundary
                repeatUntilStepIndex = i;
                repeatUntilSteps = NumberUtils.toInt(row.get(COL_IDX_PARAMS_START));
                repeatUntilLastStepIndex = repeatUntilStepIndex + repeatUntilSteps;

                boolean repeatUntilContainsMacro = false;
                for (int j = i + 1; j < repeatUntilLastStepIndex; j++) {
                    if (allTestSteps.size() <= j) { break; }

                    List<String> loopStep = allTestSteps.get(j);
                    String loopCommand = loopStep.get(COL_IDX_TARGET) + "." + loopStep.get(COL_IDX_COMMAND);
                    if (StringUtils.equals(loopCommand, CMD_MACRO)) {
                        // found macro in repeat until. Now we mark `repeatUntilSteps` and `repeatUntilStepIndex`
                        // so that we can further process them when handling macro expansion (below)
                        repeatUntilContainsMacro = true;
                        break;
                    }
                }

                if (!repeatUntilContainsMacro) {
                    repeatUntilStepIndex = -1;
                    repeatUntilSteps = -1;
                    repeatUntilLastStepIndex = -1;
                }

                continue;
            }

            // look for base.macro(file,sheet,name) - open macro library as excel
            if (StringUtils.equals(testCommand, CMD_MACRO)) {
                String paramFile = row.get(COL_IDX_PARAMS_START);
                String paramSheet = row.get(COL_IDX_PARAMS_START + 1);
                String paramMacro = row.get(COL_IDX_PARAMS_START + 2);

                ExecutionContext context = ExecutionThread.get();
                if (context != null) {
                    Map<String, String> iterationData = execDef.getTestData().getAllValue(currentIteration);
                    iterationData.forEach(context::setData);
                    paramFile = context.replaceTokens(paramFile);
                    paramSheet = context.replaceTokens(paramSheet);
                    paramMacro = context.replaceTokens(paramMacro);
                }

                // use macro cache if possible
                // expand macro test steps, add them to cache
                List<List<String>> macroSteps = harvestMacroSteps(paramFile, paramSheet, paramMacro);
                if (CollectionUtils.isNotEmpty(macroSteps)) {
                    int stepSize = macroSteps.size();

                    // replace existing macro() command to section() command
                    String[] commandParts = StringUtils.split(CMD_SECTION, ".");
                    row.set(COL_IDX_TARGET, commandParts[0]);
                    row.set(COL_IDX_COMMAND, commandParts[1]);
                    row.set(COL_IDX_PARAMS_START, stepSize + "");
                    row.set(COL_IDX_PARAMS_START + 1, "");
                    row.set(COL_IDX_PARAMS_START + 2, "");

                    ConsoleUtils.log("Total step count for macro '" + paramFile + ":" + paramSheet + ":" + paramMacro +
                                     "' :: " + stepSize);

                    int repeatUntilEndCount = -1;
                    int newStepSize = stepSize;

                    // replace macro invocation step with expanded macro test steps
                    for (int j = 0; j < stepSize; j++) {
                        List<String> macroStep = new ArrayList<>(macroSteps.get(j));

                        // since section command will be preceding all macro steps, we no longer need to be concerned
                        // with the activity information from macro
                        // macroStep.add(COL_IDX_TESTCASE, j == 0 ? activityName : "");
                        macroStep.add(COL_IDX_TESTCASE, "");

                        // add special character in macro steps description
                        String description = macroStep.get(COL_IDX_DESCRIPTION);
                        if (!StringUtils.startsWith(description, SECTION_DESCRIPTION_PREFIX)) {
                            macroStep.set(COL_IDX_DESCRIPTION, SECTION_DESCRIPTION_PREFIX + description);
                        }

                        String commandFQn = macroStep.get(COL_IDX_TARGET) + "." + macroStep.get(COL_IDX_COMMAND);
                        if(StringUtils.equals(commandFQn, CMD_REPEAT_UNTIL)){
                            repeatUntilEndCount = Integer.parseInt(row.get(COL_IDX_PARAMS_START));
                        }

                        // to check section is not part of repeatUntil
                        boolean notPartOfRepeatUntil = repeatUntilEndCount == -1 || i > repeatUntilEndCount;
                        if(StringUtils.equals(commandFQn, CMD_SECTION)){
                            int sectionSteps = Integer.parseInt(macroStep.get(COL_IDX_PARAMS_START));
                            // checking all section commands count in a macro
                            if(sectionSteps + j > stepSize) {
                                throw new IllegalArgumentException("Wrong section step count " + sectionSteps);
                            }

                            if (notPartOfRepeatUntil)  {
                                newStepSize -= sectionSteps;
                                row.set(COL_IDX_PARAMS_START, newStepSize + "");
                                ConsoleUtils.log("Macro Step count has been changed from " + stepSize +
                                                 " to " + newStepSize);
                            }
                        }

                        allTestSteps.add(i + j + 1, macroStep);
                    }

                    i += macroSteps.size();
                    macroExpanded = true;
                    if (repeatUntilStepIndex > 0) { // && repeatUntilSteps > 0) {
                        // change repeat-until step count since we have a macro inside a repeat-until block
                        int newLoopSize = repeatUntilSteps + macroSteps.size();
                        allTestSteps.get(repeatUntilStepIndex).set(COL_IDX_PARAMS_START, newLoopSize + "");
                        repeatUntilSteps = newLoopSize;
                        repeatUntilLastStepIndex = repeatUntilStepIndex + repeatUntilSteps;
                    }
                }
            }
        }

        if (macroExpanded) {
            // we need to add one extra blank row so that the macro expansion won't accidentally merge with other
            // inactivated test steps.
            allTestSteps.add(new ArrayList<>(COL_IDX_CAPTURE_SCREEN));
        }

        return macroExpanded;
    }

    protected void refillExpandedTestSteps(Worksheet sheet, List<List<String>> allTestSteps) {
        XSSFSheet excelSheet = sheet.getSheet();

        // remove existing rows
        int lastCommandRow = sheet.findLastDataRow(ADDR_COMMAND_START);
        ExcelArea area = new ExcelArea(sheet, new ExcelAddress(TEST_STEPS_PREFIX + lastCommandRow), false);
        List<List<XSSFCell>> testStepArea = area.getWholeArea();
        if (CollectionUtils.isNotEmpty(testStepArea)) {
            testStepArea.forEach(row -> excelSheet.removeRow(excelSheet.getRow(row.get(0).getRowIndex())));
        }

        XSSFCellStyle styleTarget = sheet.getStyle(STYLE_TARGET);
        XSSFCellStyle styleCommand = sheet.getStyle(STYLE_COMMAND);
        XSSFCellStyle styleParam = sheet.getStyle(STYLE_PARAM);
        XSSFCellStyle styleParamSkipped = sheet.getStyle(STYLE_PARAM_SKIPPED);

        // push expanded test steps back to scenario sheet
        for (int i = 0; i < allTestSteps.size(); i++) {
            List<String> row = allTestSteps.get(i);
            int targetRowIdx = ADDR_COMMAND_START.getRowStartIndex() + i;

            XSSFRow excelRow = excelSheet.createRow(targetRowIdx);
            boolean isSkipped = ExcelStyleHelper.isStepSkipped(excelRow);

            for (int j = 0; j < row.size(); j++) {
                String cellValue = row.get(j);
                if (j == COL_IDX_REASON && StringUtils.isEmpty(cellValue)) { break; }

                XSSFCell cell = excelRow.createCell(j, STRING);
                cell.setCellValue(cellValue);
                boolean hasCellValue = StringUtils.isNotBlank(cellValue);

                // set style for all known cells
                if (j >= COL_IDX_PARAMS_START && j <= COL_IDX_PARAMS_END) {
                    if (isSkipped) {
                        cell.setCellStyle(styleParamSkipped);
                    } else if (hasCellValue) {
                        cell.setCellStyle(styleParam);
                    }
                    continue;
                }

                switch (j) {
                    case COL_IDX_TESTCASE -> {
                        if (hasCellValue) { ExcelStyleHelper.formatActivityCell(sheet, cell); }
                    }
                    case COL_IDX_DESCRIPTION -> {
                        if (hasCellValue) {
                            if (StringUtils.startsWith(cellValue, SECTION_DESCRIPTION_PREFIX)) {
                                ExcelStyleHelper.formatSectionDescription(sheet, cell);
                            } else {
                                ExcelStyleHelper.formatDescription(sheet, cell);
                            }
                        }
                    }
                    case COL_IDX_TARGET -> cell.setCellStyle(styleTarget);
                    case COL_IDX_COMMAND -> cell.setCellStyle(styleCommand);
                    case COL_IDX_FLOW_CONTROLS -> ExcelStyleHelper.formatFlowControlCell(sheet, cell);
                }
            }
        }
    }

    protected List<List<String>> harvestAllTestSteps(Worksheet sheet) {
        int lastCommandRow = sheet.findLastDataRow(ADDR_COMMAND_START);
        ExcelArea area = new ExcelArea(sheet, new ExcelAddress(TEST_STEPS_PREFIX + lastCommandRow), false);
        List<List<XSSFCell>> testStepArea = area.getWholeArea();
        List<List<String>> testStepData = new ArrayList<>();
        if (CollectionUtils.isEmpty(testStepArea)) { return testStepData; }

        ExecutionContext context = ExecutionThread.get();

        testStepArea.forEach(row -> {
            List<String> testStepRow = new ArrayList<>();

            // check for strikethrough (which means skip)
            if (ExecutionInputPrep.isTestStepDisabled(row)) { addSkipFlowControl(row); }

            row.forEach(cell -> {
                String cellValue = Excel.getCellValue(cell);
                testStepRow.add(context != null && context.containsCrypt(cellValue) ?
                                Excel.getCellRawValue(cell) : cellValue);
            });
            testStepData.add(testStepRow);
        });

        return testStepData;
    }

    protected List<List<String>> harvestMacroSteps(String paramFile, String paramSheet, String paramMacro)
        throws IOException {

        File macroFile = resolveMacroFile(project, StringUtils.appendIfMissing(paramFile, SCRIPT_FILE_EXT));

        // (2018/12/9,automike): remove to support dynamic macro changes during execution and interactive mode
        // String macroKey = macroFile + ":" + paramSheet + ":" + paramMacro;
        // if (MACRO_CACHE.containsKey(macroKey)) {
        //     shortcut: in case the same macro is referenced
        // ConsoleUtils.log("reading macro from cache: " + macroKey);
        // return MACRO_CACHE.get(macroKey);
        // }

        // open specified sheet
        Excel macroExcel = new Excel(macroFile, DEF_OPEN_EXCEL_AS_DUP, false);
        Worksheet macroSheet = macroExcel.worksheet(paramSheet);
        int lastMacroRow = macroSheet.findLastDataRow(ADDR_MACRO_COMMAND_START);
        ExcelArea macroArea = new ExcelArea(macroSheet, new ExcelAddress("A2:L" + lastMacroRow), false);
        List<List<XSSFCell>> macroStepArea = macroArea.getWholeArea();
        List<List<String>> macroSteps = new ArrayList<>();
        boolean macroFound = false;

        // 6. read test steps based on macro name
        for (List<XSSFCell> macroRow : macroStepArea) {
            String currentMacroName = Excel.getCellValue(macroRow.get(COL_IDX_TESTCASE));
            if (StringUtils.equals(currentMacroName, paramMacro)) {
                macroFound = true;
                macroSteps.add(collectMacroStep(macroRow));
                continue;
            }

            if (macroFound) {
                if (StringUtils.isBlank(currentMacroName)) {
                    macroSteps.add(collectMacroStep(macroRow));
                } else {
                    return macroSteps;
                    // (2018/12/9,automike): remove to support dynamic macro changes during execution and interactive mode
                    // MACRO_CACHE.put(macroKey, macroSteps);
                    // macroSteps = new ArrayList<>();
                    // macroFound = false;
                    // break;
                }
            }
        }

        // (2018/12/16,automike): memory consumption precaution
        macroExcel.close();

        return macroSteps;

        // (2018/12/9,automike): remove to support dynamic macro changes during execution and interactive mode
        // capture last one
        // if (macroFound && !macroSteps.isEmpty()) { MACRO_CACHE.put(macroKey, macroSteps); }
        // if (!MACRO_CACHE.containsKey(macroKey)) { ConsoleUtils.error("Unable to resolve macro via " + macroKey);}
        // return MACRO_CACHE.get(macroKey);
    }

    protected List<String> collectMacroStep(List<XSSFCell> macroRow) {
        if (ExecutionInputPrep.isMacroStepDisabled(macroRow)) { addSkipFlowControl(macroRow); }

        List<String> oneStep = new ArrayList<>();
        for (int i = COL_IDX_DESCRIPTION; i <= COL_IDX_CAPTURE_SCREEN; i++) {
            oneStep.add(Excel.getCellValue(macroRow.get(i)));
        }
        return oneStep;
    }

    private void addSkipFlowControl(List<XSSFCell> row) {
        XSSFCell cellFlowControls = row.get(COL_IDX_FLOW_CONTROLS);
        cellFlowControls.setCellValue(CONDITION_DISABLE);

        // (2018/12/28,automike): we MUST NOT prepend or append since other flow control may supersede the flow
        // control we are adding here to "disable" a command
        // String currentFlowControls = Excel.getCellValue(cellFlowControls);
        // currentFlowControls = StringUtils.prependIfMissing(currentFlowControls, CONDITION_DISABLE);
        // cellFlowControls.setCellValue(currentFlowControls);
    }
}
