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

package org.nexial.core.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.model.TestStep;
import org.nexial.core.tools.ScriptMetadata.Commands;
import org.nexial.core.tools.ScriptMetadata.NamedRange;
import org.nexial.core.utils.InputFileUtils;
import org.slf4j.MDC;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.Data.SHEET_SYSTEM;
import static org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS;
import static org.nexial.core.NexialConst.Project.COMMAND_JSON_FILE_NAME;
import static org.nexial.core.NexialConst.MSG_SCRIPT_UPDATE_ERR;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.plugins.base.BaseCommand.PARAM_AUTO_FILL_COMMANDS;
import static org.nexial.core.tools.CliConst.OPT_VERBOSE;
import static org.nexial.core.tools.CliUtils.newArgOption;
import static org.nexial.core.tools.CommandDiscovery.GSON;

/**
 * utility to update one or more test scripts with the latest command listing.  The command listing is sync'd from
 * ${NEXIAL_HOME}/template.
 *
 * @see CommandMetaGenerator
 */
public class TestScriptUpdater {
    private static final List<String> NON_MACRO_COMMANDS = Arrays.asList("macro(file,sheet,name)");
    private static final Map<String, String> REPLACED_COMMANDS = TextUtils.toMap(
        "=",
        "number.assertBetween(num,lower,upper)=number.assertBetween(num,min,max)",
        "desktop.scanTable(var,name)=desktop.useTable(var,name)",
        "desktop.getRowCount(var)=desktop.saveRowCount(var)");
    private static final Map<String, String> COMMAND_SUGGESTIONS = TextUtils.toMap(
        "=",
        "desktop.useTable(var,name)=This command is deprecated and will soon be removed. " +
        "Consider using desktop » editTableCells(row,nameValues) instead",
        "desktop.editCurrentRow(nameValues)=This command is deprecated and will soon be removed. " +
        "Consider using desktop » editTableCells(row,nameValues) instead");
    private static final Options cmdOptions = new Options();

    private boolean verbose;
    private List<File> targetFiles;

    public static void main(String[] args) throws Exception {
        initOptions();

        TestScriptUpdater updater = newInstance(args);
        if (updater == null) { System.exit(RC_BAD_CLI_ARGS); }

        updater.update(updater.retrieveMetadata());
    }

    public void setTargetFiles(List<File> targetFiles) { this.targetFiles = targetFiles; }

    protected static TestScriptUpdater newInstance(String[] args) {
        try {
            TestScriptUpdater updater = new TestScriptUpdater();
            updater.parseCLIOptions(new DefaultParser().parse(cmdOptions, args));
            return updater;
        } catch (Exception e) {
            System.err.println("\nERROR: " + e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(TestScriptUpdater.class.getName(), cmdOptions, true);
            return null;
        }
    }

    protected void parseCLIOptions(CommandLine cmd) {
        if (!cmd.hasOption("t")) { throw new RuntimeException("[target] is a required argument and is missing"); }

        verbose = cmd.hasOption("v");

        String target = cmd.getOptionValue("t");
        File targetFile = new File(target);
        if (!targetFile.exists() || !targetFile.canRead()) {
            throw new RuntimeException("specified target - " + target + " is not accessible");
        }

        targetFiles = new ArrayList<>();
        if (targetFile.isFile()) {
            if (verbose) { System.out.println("resolved target as a single Excel file " + targetFile); }
            targetFiles.add(targetFile);
        } else {
            targetFiles.addAll(
                FileUtils.listFiles(targetFile, new String[]{"xlsx"}, true).stream()
                         .filter(file -> !file.getName().startsWith("~") &&
                                         !file.getAbsolutePath().contains(separator + "output" + separator))
                         .collect(Collectors.toList()));
            if (verbose) { System.out.println("resolved target as a set of " + targetFiles.size() + " Excel files"); }
        }
    }

    protected ScriptMetadata retrieveMetadata() {
        try {
            String commandJson = ResourceUtils.loadResource("/" + COMMAND_JSON_FILE_NAME);
            if (StringUtils.isEmpty(commandJson)) {
                System.err.println(MSG_SCRIPT_UPDATE_ERR);
                System.exit(-1);
            }
            return GSON.fromJson(commandJson, ScriptMetadata.class);
        } catch (Exception e) {
            System.err.println(MSG_SCRIPT_UPDATE_ERR);
            System.exit(-1);
        }
        return null;
    }

    protected void update(final ScriptMetadata metadata) {
        targetFiles.forEach(file -> {
            try {
                Excel excel = new Excel(file);
                String filePath = excel.getFile().getAbsolutePath();

                if (InputFileUtils.isValidScript(excel)) {
                    System.out.println("processing " + filePath);

                    if (updateTemplate(excel) && verbose) { System.out.println("\tscript updated to latest template"); }

                    handleSystemSheet(excel, metadata);
                    if (verbose) { System.out.println("\tupdated commands"); }

                    scanInvalidCommands(excel, metadata);
                    if (verbose) { System.out.println("\tcompleted script inspection"); }

                    // reset zoom and starting position
                    excel.getWorksheetsStartWith("").forEach(worksheet -> {
                        if (!StringUtils.equals(worksheet.getName(), SHEET_SYSTEM)) {
                            XSSFSheet sheet = worksheet.getSheet();
                            System.out.println("\t[" + worksheet.getName() + "] " +
                                               "setting starting position as A5 and reset zoom to 100%");
                            sheet.setActiveCell(new CellAddress("A5"));
                            sheet.setZoom(100);
                        }
                    });
                    excel.getWorkbook().setActiveSheet(1);
                    excel.getWorkbook().setFirstVisibleTab(1);
                    excel.getWorkbook().setSelectedTab(1);
                    excel.save();

                } else if (InputFileUtils.isValidMacro(excel)) {
                    System.out.println("processing " + filePath);

                    handleMacroSystemSheet(excel, metadata);
                    if (verbose) { System.out.println("\tupdated commands"); }

                    scanInvalidMacroCommands(excel, metadata);
                    if (verbose) { System.out.println("\tcompleted macro inspection"); }

                } else {
                    // remove system sheet, if found..
                    Worksheet worksheet = excel.worksheet(SHEET_SYSTEM);
                    if (worksheet != null) {
                        if (verbose) {
                            System.out.println("\tremoving 'system' sheet for non-script file: " +
                                               excel.getFile().getName());
                        }

                        XSSFWorkbook workbook = excel.getWorkbook();
                        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                            if (StringUtils.equals(workbook.getSheetAt(i).getSheetName(), SHEET_SYSTEM)) {
                                System.out.println("\tdeleting sheet #" + i + " for " + file.getAbsolutePath());
                                workbook.removeSheetAt(i);
                                Excel.save(file, workbook);
                                break;
                            }
                        }
                    }

                    // could be a plan of old (v2) format...
                    List<Worksheet> v2Plans = InputFileUtils.retrieveV2Plan(excel);
                    if (CollectionUtils.isEmpty(v2Plans)) {
                        if (verbose) { System.out.println("not recognized as nexial script: " + filePath); }
                    } else {
                        System.out.println("processing " + filePath);

                        v2Plans.forEach(plan -> {
                            if (!updateV2Plan(plan)) {
                                System.err.println("\tUNABLE TO UPDATE TEST PLAN " + plan.getName() +
                                                   " in " + filePath + "; CHECK TEST PLAN FOR ERRORS");
                            }
                        });

                        XSSFWorkbook workbook = excel.getWorkbook();
                        workbook.setActiveSheet(0);
                        workbook.setFirstVisibleTab(0);
                        workbook.setSelectedTab(0);
                        excel.save();
                    }
                }
            } catch (Exception e) {
                System.err.println("Unable to parse Excel file " + file + ": " + e.getMessage());
            }
        });
    }

    protected boolean updateTemplate(Excel excel) throws IOException {
        boolean[] updated = new boolean[]{false};

        // find all existing worksheet (minus system sheet)
        XSSFWorkbook workbook = excel.getWorkbook();
        int sheetCount = workbook.getNumberOfSheets();
        for (int i = 0; i < sheetCount; i++) {
            XSSFSheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            if (StringUtils.equals(sheetName, SHEET_SYSTEM)) { continue; }

            MDC.put("script.file", excel.getFile().getName());
            MDC.put("script.scenario", sheetName);

            // start from row 5, scan for each command
            if (verbose) { System.out.println("\tinspecting " + sheetName); }

            Worksheet worksheet = excel.worksheet(sheetName);

            // probably no one's using V1 anymore... so commented out
            if (InputFileUtils.isV1Script(worksheet) || InputFileUtils.isV15Script(worksheet)) {
                worksheet.firstCell(ADDR_HEADER_TEST_STEP).setCellValue(HEADER_ACTIVITY);
                worksheet.firstCell(new ExcelAddress("" + COL_TARGET +
                                                     (ADDR_HEADER_TEST_STEP.getRowStartIndex() + COL_IDX_TARGET - 1)))
                         .setCellValue(HEADER_COMMAND_TYPE);
                worksheet.firstCell(new ExcelAddress("J4")).setCellValue(HEADER_TEST_STEP_FLOW_CONTROLS);
                updated[0] = true;
            }

            if (InputFileUtils.isV2Script(worksheet)) {
                worksheet.setColumnValues(ADDR_HEADER_SCENARIO_INFO2, Arrays.asList(HEADER_SCENARIO_INFO_PROJECT,
                                                                                    HEADER_SCENARIO_INFO_RELEASE,
                                                                                    HEADER_SCENARIO_INFO_FEATURE,
                                                                                    HEADER_SCENARIO_INFO_TESTREF,
                                                                                    HEADER_SCENARIO_INFO_AUTHOR));
                updated[0] = true;
            }
        }

        if (updated[0]) { excel.save(); }

        return updated[0];
    }

    protected boolean updateV2Plan(Worksheet plan) {
        XSSFSheet sheet = plan.getSheet();
        String sheetName = sheet.getSheetName();
        if (verbose) { System.out.println("\tupdating test plan " + sheetName); }

        plan.setColumnValues(ADD_PLAN_HEADER_FEATURE_AND_TEST,
                             Arrays.asList(PLAN_HEADER_FEATURE_OVERRIDE, PLAN_HEADER_TESTREF_OVERRIDE));

        System.out.println("\t[" + sheetName + "] setting starting position as A5 and reset zoom to 100%");
        sheet.setActiveCell(new CellAddress("A5"));
        sheet.setZoom(100);
        return true;
    }

    private static void initOptions() {
        cmdOptions.addOption(OPT_VERBOSE);
        cmdOptions.addOption(newArgOption("t", "target",
                                          "[REQUIRED] Location of a single Excel test script or a directory to update.",
                                          true));
    }

    private void handleMacroSystemSheet(Excel excel, ScriptMetadata metadata) throws IOException {
        updateSystemSheet(excel, metadata, true);
    }

    private void handleSystemSheet(Excel excel, ScriptMetadata metadata) throws IOException {
        updateSystemSheet(excel, metadata, false);
    }

    private void updateSystemSheet(Excel excel, ScriptMetadata metadata, boolean forMacro) throws IOException {
        List<String> targets = metadata.getTargets();
        List<String> headers = new ArrayList<>(targets);
        headers.add(0, "target");

        List<Commands> commands = metadata.getCommands();
        List<NamedRange> names = metadata.getNames();

        Worksheet worksheet = excel.worksheet(SHEET_SYSTEM, true);
        worksheet.clearAllContent();

        worksheet.setColumnValues(new ExcelAddress("A1"), headers);
        worksheet.setRowValues(new ExcelAddress("A2"), targets);

        for (int i = 0; i < commands.size(); i++) {
            List<String> commandList = commands.get(i).getCommands();
            if (forMacro) {
                List<String> macroCommandList = new ArrayList<>(commandList);
                for (int j = 0; j < macroCommandList.size(); j++) {
                    String command = macroCommandList.get(j);
                    if (NON_MACRO_COMMANDS.contains(command)) {
                        macroCommandList.remove(command);
                        j--;
                    }
                }
                commandList = macroCommandList;
            }

            worksheet.setRowValues(new ExcelAddress(((char) ('B' + i)) + "2"), commandList);
        }

        names.forEach(namedRange -> worksheet.createName(namedRange.getName(), namedRange.getReference()));
        //worksheet.show();
        worksheet.hide();

        excel.save();
    }

    private void scanInvalidMacroCommands(Excel excel, ScriptMetadata metadata) {
        scanInvalidCommands(excel, metadata, ADDR_MACRO_COMMAND_START);
    }

    private void scanInvalidCommands(Excel excel, ScriptMetadata metadata) {
        scanInvalidCommands(excel, metadata, ADDR_COMMAND_START);
    }

    private void scanInvalidCommands(Excel excel, ScriptMetadata metadata, ExcelAddress addrCommandStart) {
        List<String> targetCommands = new ArrayList<>();

        List<String> targets = metadata.getTargets();
        List<Commands> allCommands = metadata.getCommands();
        for (int j = 0; j < allCommands.size(); j++) {
            String target = targets.get(j);
            allCommands.get(j).getCommands().forEach(cmd -> targetCommands.add(target + "." + cmd));
        }

        boolean excelUpdated = false;

        // find all existing worksheet (minus system sheet)
        XSSFWorkbook workbook = excel.getWorkbook();
        int sheetCount = workbook.getNumberOfSheets();
        for (int i = 0; i < sheetCount; i++) {
            XSSFSheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            if (StringUtils.equals(sheetName, SHEET_SYSTEM)) { continue; }

            MDC.put("script.file", excel.getFile().getName());
            MDC.put("script.scenario", sheetName);

            // start from row 5, scan for each command
            // if (verbose && logger.isInfoEnabled()) { System.out.println("\tinspecting " + sheetName); }

            Worksheet worksheet = excel.worksheet(sheetName);
            int lastCommandRow = worksheet.findLastDataRow(addrCommandStart);
            String commandAreaAddr = "" + COL_TEST_CASE + (addrCommandStart.getRowStartIndex() + 1) + ":" +
                                     COL_CAPTURE_SCREEN + lastCommandRow;
            ExcelArea area = new ExcelArea(worksheet, new ExcelAddress(commandAreaAddr), false);

            for (int j = 0; j < area.getWholeArea().size(); j++) {
                List<XSSFCell> row = area.getWholeArea().get(j);
                int rowIndex = row.get(0).getRowIndex() + 1;
                MDC.put("script.position", "row " + rowIndex);

                // form the command signature
                XSSFCell cellTarget = row.get(COL_IDX_TARGET);
                String target = Excel.getCellValue(cellTarget);
                if (!targets.contains(target)) {
                    System.err.println("\tInvalid command target - " + target);
                    continue;
                }

                XSSFCell cellCommand = row.get(COL_IDX_COMMAND);
                String command = Excel.getCellValue(cellCommand);
                if (StringUtils.isBlank(command)) {
                    System.err.println("\tInvalid command: " + command);
                    continue;
                }

                String targetCommand = target + "." + command;

                // check for auto-substitution
                if (REPLACED_COMMANDS.containsKey(targetCommand)) {
                    // found old command, let's replace it with new one
                    String newCommand = REPLACED_COMMANDS.get(targetCommand);
                    if (cellTarget != null) {
                        cellTarget.setCellValue(StringUtils.substringBefore(newCommand, "."));
                    }
                    if (cellCommand != null) {
                        cellCommand.setCellValue(StringUtils.substringAfter(newCommand, "."));
                    }

                    targetCommand = newCommand;
                    excelUpdated = true;
                }

                // todo: correct scripts with outdated commands
                // todo: desktop.scanTable --> desktop.useTable
                // todo: desktop.useTableRow --> MESSAGE NOT NEED, CHANGE TO USE desktop.editTableCell
                // todo: desktop.editCurrentRow --> MESSAGE NOT NEED, CHANGE TO USE desktop.editTableCell
                // todo: desktop.get*** --> desktop.save***

                // check for warning/suggest
                if (COMMAND_SUGGESTIONS.containsKey(targetCommand)) {
                    String suggestion = COMMAND_SUGGESTIONS.get(targetCommand);
                    System.err.println("\t!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    System.err.println("\tRow " + rowIndex + ": " + target + " » " + command);
                    System.err.println("\t" + suggestion);
                    System.err.println("\t!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                }

                String commandSignature = targetCommand;
                Optional<String> matchedCommand = targetCommands.stream()
                                                                .filter(s -> s.startsWith(commandSignature))
                                                                .findFirst();
                if (!matchedCommand.isPresent()) {
                    // for every unknown command, spit out an error
                    System.err.println("\tInvalid command: " + targetCommand);
                    continue;
                }

                // check param count
                if (PARAM_AUTO_FILL_COMMANDS.contains(StringUtils.substringBeforeLast(targetCommand, "("))) {
                    // special case: some commands will automatically fill missing/undefined cells during the call
                    continue;
                }

                List<String> paramList = TextUtils.toList(StringUtils.substringBetween(matchedCommand.get(), "(", ")"),
                                                          ",",
                                                          true);
                int paramCount = CollectionUtils.size(paramList);

                List<String> paramValues = TestStep.readParamValues(row);
                int paramValuesCount = CollectionUtils.size(paramValues);
                if (paramValuesCount != paramCount) {
                    System.err.println("\tWrong number of parameters for command " + targetCommand +
                                       ": expected " + paramCount + " parameter(s) but found " + paramValuesCount);
                }

                for (int k = 0; k < paramCount; k++) {
                    if (StringUtils.isBlank(Excel.getCellValue(row.get(COL_IDX_PARAMS_START + k)))) {
                        System.err.println("\tWrong number of parameters for command " +
                                           targetCommand +
                                           ": no data/value found for parameter '" +
                                           IterableUtils.get(paramList, k) +
                                           "'");
                    }
                }
            }
        }
    }
}
