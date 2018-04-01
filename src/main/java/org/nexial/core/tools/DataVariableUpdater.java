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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.validation.constraints.NotNull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.utils.InputFileUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS;
import static org.nexial.core.NexialConst.ExitStatus.RC_EXCEL_IN_USE;
import static org.nexial.core.NexialConst.Project.DEF_PROJECT_PROPS;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.tools.CliUtils.getCommandLine;
import static org.nexial.core.tools.CliUtils.newArgOption;

/**
 * Utility to rename the variables in the data files, scripts, properties files and sql files within a project.
 */
final public class DataVariableUpdater {
    private static final String OPT_PROJECT_PATH = "t";
    private static final String OPT_VARIABLES_LIST = "d";
    private static final String OPT_VERBOSE = "v";

    private static final String DATA_FILE_SUFFIX = "data.xlsx";
    private static final String SCRIPT_FILE_SUFFIX = "xlsx";

    private static final String KEY_VALUE_SEPARATOR = "=";
    private static final String VARIABLE_SEPARATOR = ";";

    private static final List<String> KEYWORDS_VAR_PARAM = Arrays.asList("var", "saveVar", "profile", "config", "db");
    private static final List<String> VAR_WRAPPERS = Arrays.asList("store", "CONFIG", "CSV", "DATE", "EXCEL", "INI",
                                                                   "JSON", "LIST", "NUMBER", "SQL", "TEXT", "XML");
    // private static final List<String> VAR_PARTIAL_WRAPPERS = Arrays.asList("SkipIf", "PauseBefore", "PauseAfter",
    //                                                                        "EndIf", "FailIf", "EndLoopIf", "ProceedIf");

    protected String searchFrom;
    protected File searchPath;
    protected Map<String, String> variableMap;
    protected List<UpdateLog> updated = new ArrayList<>();

    final class UpdateLog {
        private String file;
        private String position;
        private String before;
        private String after;

        public UpdateLog(String file) { this.file = file; }

        public UpdateLog(File file) { this.file = file.getAbsolutePath(); }

        public String getFile() { return file;}

        public UpdateLog setFile(String file) {
            this.file = file;
            return this;
        }

        public String getPosition() { return position;}

        public UpdateLog setPosition(String position) {
            this.position = position;
            return this;
        }

        public String getBefore() { return before;}

        public UpdateLog setBefore(String before) {
            this.before = before;
            return this;
        }

        public String getAfter() { return after;}

        public UpdateLog setAfter(String after) {
            this.after = after;
            return this;
        }

        public UpdateLog copy() {
            UpdateLog copy = new UpdateLog(this.file);
            copy.setPosition(this.position);
            copy.setBefore(this.before);
            copy.setAfter(this.after);
            return copy;
        }

        public UpdateLog setChange(String before, String after) {
            this.before = before;
            this.after = after;
            return this;
        }

        @Override
        public String toString() {
            return StringUtils.leftPad(StringUtils.right(file, 70) + " [" + position + "]: ", 75) +
                   before + " => " + after;
        }
    }

    /**
     * This is a utility written to rename an existing variable name to some other name. The list of all the
     * variable names to be renamed are sent as key value pairs where key is variable and value is the new name with
     * which it should be renamed.
     * <p>
     * The renaming happens across data files, scripts, properties file and other sql files.
     * <p>
     * This class accepts two command line {@link Options}:-
     * <ol><li>{@link DataVariableUpdater#OPT_PROJECT_PATH} project path</li>
     * <li>{@link DataVariableUpdater#OPT_VARIABLES_LIST} variables list passed as key value pairs.</li></ol>
     *
     * @param args command line arguments as {@link Options}.
     */
    public static void main(String[] args) {
        Options cmdOptions = new Options();
        cmdOptions.addOption(OPT_VERBOSE, "verbose", false, "Turn on verbose logging.");
        cmdOptions.addOption(newArgOption(OPT_PROJECT_PATH, "target", "Starting location of update data variable."));
        cmdOptions.addOption(newArgOption(OPT_VARIABLES_LIST, "data", "Data variables to replace, in the form " +
                                                                      "old_var=new_var;old_var2=new_var2"));

        final CommandLine cmd = getCommandLine(DataVariableUpdater.class.getName(), args, cmdOptions);
        if (cmd == null) {
            System.err.println("Unable to proceed, exiting...");
            System.exit(RC_BAD_CLI_ARGS);
        }

        try {
            DataVariableUpdater updater = new DataVariableUpdater();
            updater.setSearchFrom(cmd.getOptionValue(OPT_PROJECT_PATH));
            updater.setVariableMap(TextUtils.toMap(cmd.getOptionValue(OPT_VARIABLES_LIST),
                                                   VARIABLE_SEPARATOR,
                                                   KEY_VALUE_SEPARATOR));
            updater.updateAll();
        } catch (IllegalArgumentException e) {
            System.err.println("Error processing command line arguments: " + e.getMessage());
            System.exit(RC_BAD_CLI_ARGS);
        }
    }

    public String getSearchFrom() { return searchFrom; }

    public void setSearchFrom(String searchFrom) {
        if (!FileUtil.isDirectoryReadable(searchFrom)) {
            if (FileUtil.isFileReadable(searchFrom)) {
                searchFrom = new File(searchFrom).getParentFile().getAbsolutePath();
            } else {
                throw new IllegalArgumentException("'" + searchFrom + "' is not a readable directory");
            }
        }

        log("project artifacts from " + searchFrom);

        this.searchPath = new File(StringUtils.appendIfMissing(searchFrom, separator));
        this.searchFrom = searchPath.getAbsolutePath();
    }

    public Map<String, String> getVariableMap() { return variableMap; }

    public void setVariableMap(Map<String, String> variableMap) {
        this.variableMap = variableMap;

        // remove any empty/invalid ones
        this.variableMap.remove("");
        this.variableMap.remove(null);
        List<String> removalCandidates = new ArrayList<>();
        this.variableMap.forEach((key, value) -> {
            if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) { removalCandidates.add(key); }
        });

        removalCandidates.forEach(removeKey -> this.variableMap.remove(removeKey));
    }

    public void updateAll() {
        replaceProperties();
        replaceTextFiles();
        replaceDataFiles();
        replaceScripts();

        System.out.println();
        System.out.println();
        System.out.println("/-------------------------------------------------------------------------------");
        System.out.println(" data variable update summary");
        System.out.println("\\-------------------------------------------------------------------------------");
        updated.forEach(System.out::println);
        System.out.println();
    }

    /**
     * This method replaces all the variables specified in the variable list inside the data files for the specific
     * project.
     */
    protected void replaceDataFiles() {
        FileUtils.listFiles(searchPath, new String[]{DATA_FILE_SUFFIX}, true).forEach(this::handleDataFile);
    }

    protected void handleDataFile(File file) {
        // sanity check
        if (file == null || StringUtils.startsWith(file.getName(), "~")) { return; }

        log("processing", file);

        try {
            Excel dataFile = new Excel(file);
            List<Worksheet> dataSheets = InputFileUtils.filterValidDataSheets(dataFile);
            if (CollectionUtils.isEmpty(dataSheets)) {
                log("processed (no data sheet)", file);
                return;
            }

            boolean hasUpdated = false;
            for (Worksheet dataSheet : dataSheets) { if (handleDataSheet(file, dataSheet)) { hasUpdated = true; } }

            if (!hasUpdated) {
                log("processed (no change)", file);
                return;
            }

            dataFile.save();
        } catch (IOException e) {
            System.err.println("FATAIL ERROR: " + e.getMessage());
            // System.exit(RC_BAD_CLI_ARGS);
        }

        log("processed", file);
    }

    protected boolean handleDataSheet(File file, Worksheet sheet) {
        String dataSheet = file + " [" + sheet.getName() + "]";
        log("processing data sheet ", dataSheet);

        int lastCommandRow = sheet.findLastDataRow(new ExcelAddress("A1"));
        ExcelAddress addr = new ExcelAddress("A1:ZZ" + lastCommandRow);
        ExcelArea area = new ExcelArea(sheet, addr, false);

        AtomicBoolean hasUpdated = new AtomicBoolean(false);

        // parse entire area
        List<List<XSSFCell>> wholeArea = area.getWholeArea();
        wholeArea.forEach(row -> row.forEach(cell -> {
            if (cell != null && StringUtils.isNotBlank(Excel.getCellValue(cell))) {
                UpdateLog updateLog = new UpdateLog(dataSheet).setPosition(cell.getAddress().toString());

                int columnIndex = cell.getColumnIndex();
                if (columnIndex == 0) {
                    // first cell must be variable name only (no expression, function or token)
                    if (updateDataVariableName(cell, updateLog)) { hasUpdated.set(true); }
                } else {
                    if (updateDataToken(cell, updateLog)) { hasUpdated.set(true); }
                }
            }
        }));

        log("processed data sheet", dataSheet);
        return hasUpdated.get();
    }

    protected boolean updateDataVariableName(XSSFCell cell, UpdateLog updateLog) {
        String replaced = replaceVarName(Excel.getCellValue(cell), updateLog);
        if (replaced != null) {
            cell.setCellValue(replaced);
            return true;
        } else {
            return false;
        }
    }

    /**
     * replace old variable with new, for those expressed in the form of ${...} or KEYWORD(...)
     */
    protected boolean updateDataToken(XSSFCell cell, UpdateLog updateLog) {
        boolean hasUpdate = false;

        // DO NOT TRIM! we should not change the data as found in artifact
        String cellValue = Excel.getCellValue(cell);

        String cellValueModified = replaceVarTokens(cellValue, updateLog);
        if (cellValueModified != null) {
            cellValue = cellValueModified;
            hasUpdate = true;
        }

        cellValueModified = replaceVarsInKeywordWrapper(cellValue, updateLog);
        if (cellValueModified != null) {
            cellValue = cellValueModified;
            hasUpdate = true;
        }

        cellValueModified = replaceSqlVars(cellValue, updateLog);
        if (!StringUtils.equals(cellValue, cellValueModified)) {
            cellValue = cellValueModified;
            hasUpdate = true;
        }

        if (hasUpdate) { cell.setCellValue(cellValue); }

        return hasUpdate;
    }

    /**
     * This method replaces all the variables specified in the variable list inside the script files.
     */
    protected void replaceScripts() {
        FileUtils.listFiles(searchPath, new String[]{SCRIPT_FILE_SUFFIX}, true).forEach(this::handleScriptFile);
    }

    protected void handleScriptFile(File file) {
        // sanity check
        if (file == null ||
            StringUtils.startsWith(file.getName(), "~") ||
            StringUtils.endsWith(file.getName(), DATA_FILE_SUFFIX)) {
            return;
        }

        log("processing", file);

        try {
            Excel script = new Excel(file, false, false);
            List<Worksheet> worksheets = InputFileUtils.retrieveValidTestScenarios(script);
            if (CollectionUtils.isEmpty(worksheets)) {
                log("processed (no valid sheet)", file);
                return;
            }

            boolean hasUpdated = false;
            for (Worksheet worksheet : worksheets) { if (updateScenario(file, worksheet)) { hasUpdated = true; } }

            if (!hasUpdated) {
                log("processed (no change)", file);
                return;
            }

            saveExcel(file, script.getWorkbook());
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("FATAIL ERROR: " + e.getMessage());
            // System.exit(RC_BAD_CLI_ARGS);
        }

        log("processed", file);
    }

    protected boolean updateScenario(File file, Worksheet worksheet) {
        String scenario = file + " [" + worksheet.getName() + "]";
        log("processing scenario", scenario);

        int lastCommandRow = worksheet.findLastDataRow(ADDR_COMMAND_START);
        ExcelAddress addr = new ExcelAddress("" + COL_TEST_CASE + (ADDR_COMMAND_START.getRowStartIndex() + 1) +
                                             ":" + COL_REASON + lastCommandRow);
        ExcelArea area = new ExcelArea(worksheet, addr, false);

        boolean hasUpdate = false;

        // parse entire area
        List<List<XSSFCell>> wholeArea = area.getWholeArea();
        for (List<XSSFCell> row : wholeArea) {
            List<Integer> varIndices = gatherVarIndices(row);

            for (int j = 0; j < row.size(); j++) {
                // between COL_IDX_TESTCASE and COL_IDX_CAPTURE_SCREEN, we would search/replace for ${...},
                // except COL_IDX_TARGET and COL_IDX_COMMAND
                if (j > COL_IDX_CAPTURE_SCREEN) { continue; }
                if (j == COL_IDX_TARGET) { continue; }
                if (j == COL_IDX_COMMAND) { continue; }

                if (updateScriptCell(scenario, row.get(j), varIndices)) { hasUpdate = true; }
            }
        }

        log("processed scenario", scenario);

        return hasUpdate;
    }

    protected boolean updateScriptCell(String file, XSSFCell cell, List<Integer> varIndices) {
        if (cell == null || StringUtils.isBlank(cell.getRawValue())) { return false; }

        String cellValue = Excel.getCellValue(cell);
        if (StringUtils.isBlank(cellValue)) { return false; }

        boolean hasUpdate = false;
        UpdateLog updateLog = new UpdateLog(file).setPosition(cell.getAddress().formatAsString());

        String cellValueModified = replaceVarTokens(cellValue, updateLog);
        if (cellValueModified != null) {
            cellValue = cellValueModified;
            hasUpdate = true;
        }

        // search for var name
        if (varIndices.contains(cell.getColumnIndex())) {
            cellValueModified = replaceVarName(cellValue, updateLog);
            if (cellValueModified != null) {
                cellValue = cellValueModified;
                hasUpdate = true;
            }
        }

        cellValueModified = replaceVarsInKeywordWrapper(cellValue, updateLog);
        if (cellValueModified != null) {
            cellValue = cellValueModified;
            hasUpdate = true;
        }

        cellValueModified = replaceSqlVars(cellValue, updateLog);
        if (!StringUtils.equals(cellValue, cellValueModified)) {
            cellValue = cellValueModified;
            hasUpdate = true;
        }

        if (hasUpdate) { cell.setCellValue(cellValue); }

        return hasUpdate;
    }

    /**
     * Replaces all the keys in the properties file with the values specified in the variables. It also changes the
     * expressions in the values accordingly.
     */
    protected void replaceProperties() {
        List<File> props = FileUtil.listFiles(searchFrom, DEF_PROJECT_PROPS, true);

        // there should only be 1 artifact/project.properties
        if (CollectionUtils.isEmpty(props)) { return; }

        File file = props.get(0);
        log("processing", file);

        try {
            String content = FileUtils.readFileToString(file, DEF_CHARSET);
            String sep = StringUtils.contains(content, "\r\n") ? "\r\n" : "\n";

            StringBuilder replaced = new StringBuilder();

            String[] lines = StringUtils.splitPreserveAllTokens(content, sep);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                UpdateLog updateLog = new UpdateLog(file).setPosition(StringUtils.leftPad(i + "", 3));

                for (String oldVar : variableMap.keySet()) {
                    String newVar = variableMap.get(oldVar);

                    if (StringUtils.contains(oldVar, "*")) {
                        line = replaceWildcardVar(line, oldVar, newVar, updateLog);
                        continue;
                    }

                    // check var name first
                    String regexVarName = "^(" + oldVar + ")(\\s*=\\s*.*)";
                    if (RegexUtils.isExact(line, regexVarName)) {
                        line = RegexUtils.replace(line, regexVarName, newVar + "$2");
                        updated.add(updateLog.setChange(oldVar, newVar));
                    }

                    String oldToken = TOKEN_START + oldVar + TOKEN_END;
                    if (StringUtils.contains(line, oldToken)) {
                        line = StringUtils.replace(line, oldToken, TOKEN_START + newVar + TOKEN_END);
                        updated.add(updateLog.setChange(oldToken, newVar));
                    }
                }

                replaced.append(line).append(sep);
            }

            FileUtils.writeStringToFile(file, StringUtils.removeEnd(replaced.toString(), sep), DEF_CHARSET);
            log("processed", file);
        } catch (IOException e) {
            System.err.println("Unable to process " + file + " successfully: " + e.getMessage());
        }
    }

    /**
     * Replaces all variables in the text file with the form of ${...}, or KEYWORD(...) or `-- sentry:*` (SQL file).
     */
    protected void replaceTextFiles() {
        List<File> props = FileUtil.listFiles(searchFrom, "(?i).+\\.(txt|json|xml|sql|csv)", true);

        // there should only be 1 artifact/project.properties
        if (CollectionUtils.isEmpty(props)) { return; }

        for (File file : props) {
            log("processing", file);

            try {
                String content = FileUtils.readFileToString(file, DEF_CHARSET);
                String sep = StringUtils.contains(content, "\r\n") ? "\r\n" : "\n";

                StringBuilder replaced = new StringBuilder();

                String[] lines = StringUtils.splitPreserveAllTokens(content, sep);
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    UpdateLog updateLog = new UpdateLog(file).setPosition(StringUtils.leftPad(i + "", 3));

                    for (String oldVar : variableMap.keySet()) {
                        String newVar = variableMap.get(oldVar);

                        // since we are supporting multiple file format, let's just handle all the variable
                        // replacement as is (without prefixes or enclosure)

                        if (StringUtils.contains(oldVar, "*")) {
                            line = replaceWildcardVar(line, oldVar, newVar, updateLog);
                            continue;
                        }

                        if (StringUtils.contains(line, oldVar)) {
                            line = StringUtils.replace(line, oldVar, newVar);
                            updated.add(updateLog.copy().setChange(oldVar, newVar));
                        }

                        line = replaceSqlVars(line, updateLog);
                    }

                    replaced.append(line).append(sep);
                }

                FileUtils.writeStringToFile(file, StringUtils.removeEnd(replaced.toString(), sep), DEF_CHARSET);
                log("processed", file);
            } catch (IOException e) {
                System.err.println("Unable to process " + file + " successfully: " + e.getMessage());
            }
        }
    }

    @NotNull
    protected String replaceSqlVars(String line, UpdateLog updateLog) {
        // special case for sql statements
        if (StringUtils.contains(line, "-- sentry:")) {
            line = StringUtils.replace(line, "-- sentry:", "-- nexial:");
            updated.add(updateLog.copy().setChange("-- sentry:", "-- nexial:"));
        }
        return line;
    }

    @NotNull
    protected List<Integer> gatherVarIndices(List<XSSFCell> row) {
        List<Integer> varIndices = new ArrayList<>();

        String command = Excel.getCellValue(row.get(COL_IDX_COMMAND));
        if (command == null) { return varIndices; }

        List<String> paramList = TextUtils.toList(StringUtils.substringBetween(command, "(", ")"), ",", false);
        if (CollectionUtils.isEmpty(paramList)) { return varIndices; }

        KEYWORDS_VAR_PARAM.forEach(varName -> {
            if (paramList.contains(varName)) { varIndices.add(paramList.indexOf(varName) + COL_IDX_PARAMS_START); }
        });

        return varIndices;
    }

    /** search-n-replace by exact match */
    protected String replaceVarName(String cellValue, @NotNull UpdateLog updateLog) {
        boolean cellHasUpdate = false;

        for (String varName : variableMap.keySet()) {
            // search for var name
            String newValue = variableMap.get(varName);

            if (StringUtils.contains(varName, "*")) {
                Pair<String, String> regexes = varNameToRegex(varName, newValue);
                if (regexes == null) {
                    System.err.println("Invalid or erroneous wildcard references in either '" + varName +
                                       "' or '" + newValue + "', skipping");
                } else {
                    String newCellValue = RegexUtils.replace(cellValue, regexes.getKey(), regexes.getValue());
                    if (!StringUtils.equals(newCellValue, cellValue)) {
                        updated.add(updateLog.copy().setChange(varName, newValue));
                        cellValue = newCellValue;
                        cellHasUpdate = true;
                    }
                }

                continue;
            }

            if (StringUtils.equals(cellValue, varName)) {
                updated.add(updateLog.copy().setChange(varName, newValue));
                cellValue = newValue;
                cellHasUpdate = true;
            }
        }

        return cellHasUpdate ? cellValue : null;
    }

    @NotNull
    protected static Pair<String, String> varNameToRegex(String varName, String replaceWith) {
        int wildcardCount = StringUtils.countMatches(varName, "*");
        if (wildcardCount < 1) { return null; }

        int wildcardCount2 = StringUtils.countMatches(replaceWith, "*");
        if (wildcardCount != wildcardCount2) {
            System.err.println("Uneven number of wildcards found between '" + varName + "' and  '" + replaceWith + "'");
            return null;
        }

        String regex = varName;
        regex = StringUtils.replace(regex, ".", "\\.");
        regex = StringUtils.replace(regex, "*", "(.+)");
        // regex = "^" + regex + "$";

        String replace = replaceWith;
        int idx = 1;
        while (StringUtils.contains(replace, "*")) { replace = StringUtils.replaceOnce(replace, "*", "$" + idx++); }

        return new ImmutablePair<>(regex, replace);
    }

    /** search-n-replace via the standard ${...} pattern. */
    protected String replaceVarTokens(@NotNull String cellValue, @NotNull UpdateLog updateLog) {
        boolean cellHasUpdate = false;

        for (String varName : variableMap.keySet()) {
            // search for ${...}
            String newValue = variableMap.get(varName);

            if (StringUtils.contains(varName, "*")) {
                Pair<String, String> regexes = varNameToRegex(varName, newValue);
                if (regexes == null) {
                    System.err.println("Invalid or erroneous wildcard references in either '" + varName +
                                       "' or '" + newValue + "', skipping");
                } else {
                    String newCellValue = RegexUtils.replace(cellValue, regexes.getKey(), regexes.getValue());
                    if (!StringUtils.equals(newCellValue, cellValue)) {
                        updated.add(updateLog.copy().setChange(varName, newValue));
                        cellValue = newCellValue;
                        cellHasUpdate = true;
                    }
                }

                continue;
            }

            String searchFor = TOKEN_START + varName + TOKEN_END;
            if (StringUtils.contains(cellValue, searchFor)) {
                String replaceBy = TOKEN_START + newValue + TOKEN_END;
                String newCellValue = StringUtils.replace(cellValue, searchFor, replaceBy);
                if (!StringUtils.equals(newCellValue, cellValue)) {
                    updated.add(updateLog.copy().setChange(searchFor, replaceBy));
                    cellValue = newCellValue;
                    cellHasUpdate = true;
                }
            }
        }

        return cellHasUpdate ? cellValue : null;
    }

    /** search-n-replace via the KEYWORD(...) pattern */
    protected String replaceVarsInKeywordWrapper(@NotNull String cellValue, @NotNull UpdateLog updateLog) {
        boolean cellHasUpdate = false;

        // search for var name in KEYWORD(...) form
        for (String keyword : VAR_WRAPPERS) {
            for (String varName : variableMap.keySet()) {
                // search for KEYWORD(var)
                String newValue = variableMap.get(varName);
                String searchFor = keyword + "(" + varName + ")";
                if (StringUtils.contains(cellValue, searchFor)) {
                    String newCellValue = StringUtils.replace(cellValue, searchFor, keyword + "(" + newValue + ")");
                    if (!StringUtils.equals(newCellValue, cellValue)) {
                        updated.add(updateLog.copy().setChange(varName, newValue));
                        cellValue = newCellValue;
                        cellHasUpdate = true;
                    }
                }
            }
        }

        return cellHasUpdate ? cellValue : null;
    }

    protected void saveExcel(@NotNull File file, @NotNull XSSFWorkbook workBook) {
        try {
            Excel.save(file, workBook);
        } catch (IOException e) {
            System.err.println(String.format("\n\nFile %s is either is in use by other process or got deleted. " +
                                             "Please close the file if it is open and re run the program\n\n", file));
            System.exit(RC_EXCEL_IN_USE);
        } finally {
            try {
                workBook.close();
            } catch (IOException e) {
                System.err.println("Unable to properly close Excel file " + file + ": " + e.getMessage());
            }
        }
    }

    private String replaceWildcardVar(String line, String oldVar, String newVar, UpdateLog updateLog) {
        Pair<String, String> regexes = varNameToRegex(oldVar, newVar);
        if (regexes == null) {
            System.err.println("Invalid or erroneous wildcard references in either '" + oldVar +
                               "' or '" + newVar + "', skipping");
        } else {
            String newLine = RegexUtils.replace(line, regexes.getKey(), regexes.getValue());
            if (!StringUtils.equals(newLine, line)) {
                updated.add(updateLog.setChange(oldVar, newVar));
                line = newLine;
            }
        }
        return line;
    }

    private static void log(String message) { System.out.println(" >> " + message); }

    private static void log(String action, Object subject) {
        System.out.println(" >> " + StringUtils.rightPad(action, 26) + " " + subject);
    }
}
