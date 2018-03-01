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
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.MDC;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.model.TestStep;
import org.nexial.core.tools.ScriptMetadata.Commands;
import org.nexial.core.tools.ScriptMetadata.NamedRange;
import org.nexial.core.utils.InputFileUtils;
import com.google.gson.GsonBuilder;

import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.plugins.base.BaseCommand.PARAM_AUTO_FILL_COMMANDS;
import static java.io.File.separator;

/**
 * utility to update one or more test scripts with the latest command listing.  The command listing is sync'd from S3.
 *
 * @see ScriptMetadataUpdater
 */
public class TestScriptUpdater extends S3BoundCLI {
    private static final List<String> NON_MACRO_COMMANDS = Arrays.asList("macro(file,sheet,name)");

    private String systemSheetName;
    private String metadataFilename;
    private List<File> targetFiles;

    public void setSystemSheetName(String systemSheetName) { this.systemSheetName = systemSheetName; }

    public void setMetadataFilename(String metadataFilename) { this.metadataFilename = metadataFilename; }

    public void setTargetFiles(List<File> targetFiles) { this.targetFiles = targetFiles; }

    public static void main(String[] args) throws Exception {
        TestScriptUpdater updater = newInstance(args);
        if (updater == null) { System.exit(-1); }
        updater.update(updater.retrieveMetadata());
    }

    @Override
    protected void initOptions() {
        super.initOptions();
        cmdOptions.addOption("t", "target", true, "[REQUIRED] Location of a single Excel test script or a " +
                                                  "directory to update.");
    }

    @Override
    protected void parseCLIOptions(CommandLine cmd) {
        if (!cmd.hasOption("t")) { throw new RuntimeException("[target] is a required argument and is missing"); }

        String target = cmd.getOptionValue("t");
        File targetFile = new File(target);
        if (!targetFile.exists() || !targetFile.canRead()) {
            throw new RuntimeException("specified target - " + target + " is not accessible");
        }

        targetFiles = new ArrayList<>();
        if (targetFile.isFile()) {
            if (verbose && logger.isInfoEnabled()) {
                logger.info("resolved target as a single Excel file " + targetFile);
            }
            targetFiles.add(targetFile);
        } else {
            targetFiles.addAll(
                FileUtils.listFiles(targetFile, new String[]{"xlsx"}, true).stream()
                         .filter(file -> !file.getName().startsWith("~") &&
                                         !file.getAbsolutePath().contains(separator + "output" + separator))
                         .collect(Collectors.toList()));

            if (verbose && logger.isInfoEnabled()) {
                logger.info("resolved target as a set of " + targetFiles.size() + " Excel files");
            }
        }

        setMetadataFilename(cmd.getOptionValue("n", defaultMetadata));
        setSystemSheetName(cmd.getOptionValue("s", defaultSheetName));
    }

    private static TestScriptUpdater newInstance(String[] args) {
        TestScriptUpdater updater = new TestScriptUpdater();
        try {
            updater.parseCLIOptions(args);
            return updater;
        } catch (Exception e) {
            System.err.println("\nERROR: " + e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(TestScriptUpdater.class.getName(), updater.cmdOptions, true);
            return null;
        }
    }

    private ScriptMetadata retrieveMetadata() throws IOException {
        return new GsonBuilder().setPrettyPrinting()
                                .create()
                                .fromJson(new String(copyFromS3(defaultDestination, metadataFilename)),
                                          ScriptMetadata.class);
    }

    private void update(final ScriptMetadata metadata) {
        targetFiles.forEach(file -> {
            try {
                Excel excel = new Excel(file);
                if (InputFileUtils.isValidScript(file.getAbsolutePath())) {
                    if (verbose && logger.isInfoEnabled()) {
                        logger.info("processing " + excel.getFile().getAbsolutePath());
                    }

                    handleSystemSheet(excel, metadata);
                    if (verbose && logger.isInfoEnabled()) { logger.info("\tupdated commands"); }

                    scanInvalidCommands(excel, metadata);
                    if (verbose && logger.isInfoEnabled()) { logger.info("\tcompleted script inspection"); }

                    if (updateTemplate(excel) && verbose && logger.isInfoEnabled()) {
                        logger.info("script updated to match latest template");
                    }
                } else if (InputFileUtils.isValidMacro(file.getAbsolutePath())) {
                    if (verbose && logger.isInfoEnabled()) {
                        logger.info("processing " + excel.getFile().getAbsolutePath());
                    }

                    handleMacroSystemSheet(excel, metadata);
                    if (verbose && logger.isInfoEnabled()) { logger.info("\tupdated commands"); }

                    scanInvalidMacroCommands(excel, metadata);
                    if (verbose && logger.isInfoEnabled()) { logger.info("\tcompleted macro inspection"); }

                    if (updateTemplate(excel) && verbose && logger.isInfoEnabled()) {
                        logger.info("macro library updated to match latest template");
                    }
                } else {
                    // remove system sheet, if found..
                    Worksheet worksheet = excel.worksheet(systemSheetName);
                    if (worksheet != null) {
                        if (verbose && logger.isInfoEnabled()) {
                            logger.info("removing 'system' sheet for non-script file: " + excel.getFile().getName());
                        }

                        XSSFWorkbook workbook = excel.getWorkbook();
                        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                            if (StringUtils.equals(workbook.getSheetAt(i).getSheetName(), systemSheetName)) {
                                System.out.println("deleting sheet #" + i + " for " + file.getAbsolutePath());
                                workbook.removeSheetAt(i);
                                Excel.save(file, workbook);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Unable to parse Excel file " + file + ": " + e, e);
            }
        });
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

        Worksheet worksheet = excel.worksheet(systemSheetName, true);
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

        // find all existing worksheet (minus system sheet)
        XSSFWorkbook workbook = excel.getWorkbook();
        int sheetCount = workbook.getNumberOfSheets();
        for (int i = 0; i < sheetCount; i++) {
            XSSFSheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            if (StringUtils.equals(sheetName, systemSheetName)) { continue; }

            MDC.put("script.file", excel.getFile().getName());
            MDC.put("script.scenario", sheetName);

            // start from row 5, scan for each command
            // if (verbose && logger.isInfoEnabled()) { logger.info("\tinspecting " + sheetName); }

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
                String target = cellTarget == null || StringUtils.isBlank(cellTarget.getRawValue()) ?
                                "" : cellTarget.getStringCellValue();
                if (!targets.contains(target)) {
                    logger.error("\tInvalid command target - " + target);
                    continue;
                }

                XSSFCell cellCommand = row.get(COL_IDX_COMMAND);
                String command = cellCommand == null || StringUtils.isBlank(cellCommand.getRawValue()) ?
                                 "" : cellCommand.getStringCellValue();
                if (StringUtils.isBlank(command)) {
                    logger.error("\tInvalid command: " + command);
                    continue;
                }

                String targetCommand = target + "." + command;
                Optional<String> matchedCommand = targetCommands.stream()
                                                                .filter(s -> s.startsWith(targetCommand))
                                                                .findFirst();
                if (!matchedCommand.isPresent()) {
                    // for every unknown command, spit out an error
                    logger.error("\tInvalid command: " + targetCommand);
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
                    logger.error("\tWrong number of parameters for command " + targetCommand +
                                 ": expected " + paramCount + " parameter(s) but found " + paramValuesCount);
                }

                for (int k = 0; k < paramCount; k++) {
                    if (StringUtils.isBlank(Excel.getCellValue(row.get(COL_IDX_PARAMS_START + k)))) {
                        logger.error("\tWrong number of parameters for command " + targetCommand +
                                     ": no data/value found for parameter '" + IterableUtils.get(paramList, k) + "'");
                    }
                }

                // todo: correct scripts with outdated commands
            }
        }
    }

    private boolean updateTemplate(Excel excel) throws IOException {
        boolean[] updated = new boolean[]{false};

        // find all existing worksheet (minus system sheet)
        XSSFWorkbook workbook = excel.getWorkbook();
        int sheetCount = workbook.getNumberOfSheets();
        for (int i = 0; i < sheetCount; i++) {
            XSSFSheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            if (StringUtils.equals(sheetName, systemSheetName)) { continue; }

            MDC.put("script.file", excel.getFile().getName());
            MDC.put("script.scenario", sheetName);

            // start from row 5, scan for each command
            if (verbose && logger.isInfoEnabled()) { logger.info("\tinspecting " + sheetName); }

            Worksheet worksheet = excel.worksheet(sheetName);

            if (InputFileUtils.isV1Script(worksheet)) {
                worksheet.firstCell(ADDR_HEADER_TEST_STEP).setCellValue(HEADER_ACTIVITY);
                worksheet.firstCell(new ExcelAddress("" + COL_TARGET +
                                                     (ADDR_HEADER_TEST_STEP.getRowStartIndex() + COL_IDX_TARGET - 1)))
                         .setCellValue(HEADER_COMMAND_TYPE);
                updated[0] = true;
            }
        }

        excel.save();

        return updated[0];
    }
}
