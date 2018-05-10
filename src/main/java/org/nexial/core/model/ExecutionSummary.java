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

package org.nexial.core.model;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.excel.ExcelConfig.StyleConfig;
import org.nexial.core.excel.ExcelConfig.StyleDecorator;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.excel.ExcelConfig.DEF_CHAR_WIDTH;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.ITERATION;
import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static java.util.Locale.US;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;

/**
 * serves as the summary of a test execution, which can be scoped into:
 * <ul>
 * <li>a test activity - logical grouping of a set of contiguous steps that represents a business function.</li>
 * <li>a scenario - a set of test activities to accomplish a business transaction or goal.</li>
 * <li>an iteration - one run of a series of scenarios.</li>
 * <li>a test script - one or more iteration.</li>
 * <li>a test execution - execution of 1 or more test scripts.</li>
 * </ul>
 *
 * at the end of each 'scope', test summary are aggregated upward to its parent summary information.
 */
public class ExecutionSummary {
    protected static final int LABEL_SIZE = 10;
    protected static final String SUMMARY_TAB_NAME = "#summary";
    protected static final String MERGE_AREA_TITLE = "A1:J1";
    protected static final float EXEC_SUMMARY_HEIGHT = 21f;
    protected static final double CELL_WIDTH_MULTIPLIER = 275.3;
    protected static final List<Integer> COLUMN_WIDTHS = Arrays.asList(
        (int) (CELL_WIDTH_MULTIPLIER * 38), // scenario name
        (int) (CELL_WIDTH_MULTIPLIER * 18), // ref data name
        (int) (CELL_WIDTH_MULTIPLIER * 18), // ref data value
        (int) (CELL_WIDTH_MULTIPLIER * 38), // activity name
        (int) (CELL_WIDTH_MULTIPLIER * 22), // start time
        (int) (CELL_WIDTH_MULTIPLIER * 14), // duration
        (int) (CELL_WIDTH_MULTIPLIER * 10), // total steps
        (int) (CELL_WIDTH_MULTIPLIER * 10), // total pass
        (int) (CELL_WIDTH_MULTIPLIER * 10), // total fail
        (int) (CELL_WIDTH_MULTIPLIER * 10)); // success rate
    protected static final String REGEX_LINKABLE_DATA = "^(http|/[0-9A-Za-z/_]+|[A-Za-z]\\:\\\\|\\\\\\\\.+).+\\|.+$";
    protected static final Gson GSON = new GsonBuilder().setLenient().create();

    private String name;
    private ExecutionLevel executionLevel;
    private transient File testScript;
    private String testScriptLink;

    private String runHost;
    private String runHostOs;
    private String runUser;
    private long startTime;
    private long endTime;
    private int totalSteps;
    private int passCount;
    private int failCount;
    private int warnCount;
    private int executed;

    private boolean failedFast;
    private String errorStackTrace;
    private Throwable error;
    private String executionLog;

    // optional
    private Map<String, String> referenceData = new TreeMap<>();

    private List<ExecutionSummary> nestedExecutions = new ArrayList<>();

    // not persisted to JSON
    private transient Map<TestStepManifest, List<NestedMessage>> nestMessages = new LinkedHashMap<>();

    public enum ExecutionLevel {EXECUTION, SCRIPT, ITERATION, SCENARIO, ACTIVITY}

    public ExecutionSummary() {
        runUser = USER_NAME;
        runHostOs = OS_ARCH + " " + OS_NAME + " " + OS_VERSION;

        try {
            runHost = StringUtils.upperCase(EnvUtils.getHostName());
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unable to determine host name of current host: " + e.getMessage());
        }

        // set up startTime now, just in case this execution didn't complete normally..
        // then at least we'll have the time when this failed test started
        startTime = System.currentTimeMillis();
    }

    public String getErrorStackTrace() { return errorStackTrace; }

    public Throwable getError() { return error; }

    public void setError(Throwable error) {
        this.error = error;
        errorStackTrace = ExceptionUtils.getStackTrace(error);
    }

    public String getRunHost() { return runHost; }

    public String getRunHostOs() { return runHostOs; }

    public String getRunUser() { return runUser; }

    public String getManifest() { return ExecUtil.deriveJarManifest(); }

    public long getStartTime() { return startTime; }

    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }

    public void setEndTime(long endTime) { this.endTime = endTime; }

    public long getElapsedTime() { return this.endTime - this.startTime; }

    public int getTotalSteps() { return totalSteps; }

    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }

    /** when a teststep is skipped, we would need to adjust the total number of execution */
    public void adjustTotalSteps(int changeBy) { this.totalSteps += changeBy; }

    public int getPassCount() { return passCount; }

    public void setPassCount(int passCount) { this.passCount = passCount; }

    public void incrementPass() { this.passCount++; }

    public int getFailCount() { return failCount; }

    public void setFailCount(int failCount) { this.failCount = failCount; }

    public void incrementFail() { this.failCount++; }

    public int getWarnCount() { return warnCount; }

    public void setWarnCount(int warnCount) { this.warnCount = warnCount; }

    public void incrementWarn() { warnCount++; }

    public int getExecuted() { return executed; }

    public void setExecuted(int executed) { this.executed = executed; }

    public void incrementExecuted() { this.executed++; }

    public double getSuccessRate() { return executed < 1 ? 0 : (passCount * 1.0 / executed); }

    public boolean isFailedFast() { return failedFast; }

    public void setFailedFast(boolean failedFast) { this.failedFast = failedFast; }

    public File getTestScript() { return testScript; }

    public void setTestScript(File testScript) { this.testScript = testScript; }

    public String getTestScriptLink() { return testScriptLink; }

    public void setTestScriptLink(String testScriptLink) {
        this.testScriptLink = testScriptLink;
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public Map<String, String> getReferenceData() { return referenceData; }

    public void addNestedMessages(TestStepManifest testStep, List<NestedMessage> nestedTestResults) {
        nestMessages.put(testStep, nestedTestResults);
    }

    public void addNestSummary(ExecutionSummary nested) { this.nestedExecutions.add(nested); }

    public List<ExecutionSummary> getNestedExecutions() { return nestedExecutions; }

    public ExecutionLevel getExecutionLevel() { return executionLevel; }

    public void setExecutionLevel(ExecutionLevel executionLevel) {
        this.executionLevel = executionLevel;
        if (this.executionLevel == EXECUTION) {
            referenceData = ExecutionContext.getSysPropsByPrefix(SCRIPT_REF_PREFIX);
        }
    }

    public void aggregatedNestedExecutions(ExecutionContext context) {
        if (CollectionUtils.isEmpty(nestedExecutions)) { return; }

        //startTime = 0;
        //endTime = 0;
        totalSteps = 0;
        executed = 0;
        passCount = 0;
        failCount = 0;
        warnCount = 0;

        nestedExecutions.forEach(nested -> {
            if (startTime == 0) { startTime = nested.startTime; }
            if (nested.startTime != 0 && nested.startTime < startTime) {startTime = nested.startTime; }

            if (endTime == 0) { endTime = nested.endTime; }
            if (nested.endTime != 0 && nested.endTime > endTime) { endTime = nested.endTime; }

            totalSteps += nested.totalSteps;
            executed += nested.executed;
            passCount += nested.passCount;
            failCount += nested.failCount;
            warnCount += nested.warnCount;
        });

        if (context != null) {
            failedFast = context.isFailFast();
            if (executionLevel == SCENARIO) { referenceData = context.gatherScenarioReferenceData(); }
            if (executionLevel == ITERATION) { referenceData = context.gatherScriptReferenceData(); }

            executionLog = System.getProperty(TEST_LOG_PATH) + separator + "nexial-" + context.getRunId() + ".log";
            if (!FileUtil.isFileReadable(executionLog)) { return; }

            if (executionLevel == SCENARIO || executionLevel == ACTIVITY) { return; }

            if (!context.isOutputToCloud()) { return; }
            try {
                executionLog = context.getOtc().importLog(new File(executionLog), false);
            } catch (IOException e) {
                ConsoleUtils.error("Unable to save " + executionLog + " to cloud storage due to " + e.getMessage());
            }
        }
    }

    public String toString() {
        StringBuilder text = new StringBuilder();

        text.append(formatLabel("Run From")).append(formatValue(runHost, runHostOs));
        text.append(formatLabel("Run User")).append(formatValue(runUser));
        text.append(formatLabel("Time Span")).append(
            formatValue(DateUtility.formatLongDate(startTime) + " - " + DateUtility.formatLongDate(endTime)));
        text.append(formatLabel("Duration")).append(formatValue(DateUtility.formatStopWatchTime(endTime - startTime)));
        text.append(formatLabel("Steps")).append(formatValue(StringUtils.leftPad(totalSteps + "", 4, " ")));
        text.append(formatLabel("Executed")).append(formatStat(executed));
        text.append(formatLabel("PASS")).append(formatStat(passCount));
        text.append(formatLabel("FAIL")).append(formatStat(failCount));

        //if (MapUtils.isNotEmpty(referenceData)) {
        //	StringBuffer refData = new StringBuffer();
        //	referenceData.forEach((key, value) -> refData.append(" [")
        //	                                             .append(key).append("=")
        //	                                             .append(referenceData.get(key))
        //	                                             .append("]"));
        //	text.append(formatLabel("References")).append(formatValue(refData.toString()));
        //}

        return text.toString();
    }

    public Map<TestStepManifest, List<NestedMessage>> getNestMessages() { return nestMessages; }

    public void generateExcelReport(File testScript) {
        if (testScript == null || !FileUtil.isFileReadable(testScript.getAbsolutePath())) {
            ConsoleUtils.error("Unable to generate Excel report because file " + testScript + " is not readable");
            return;
        }

        try {
            Excel excel = new Excel(testScript, false);
            XSSFWorkbook workbook = excel.getWorkbook();

            Worksheet summary = excel.worksheet(SUMMARY_TAB_NAME, true);
            workbook.setSheetOrder(summary.getName(), 0);
            workbook.setSelectedTab(0);
            workbook.setActiveSheet(0);
            XSSFSheet summarySheet = summary.getSheet();

            // title
            createReportTitle(testScript, summary);

            int rowNum = createReferenceDataSection(summary, 2);
            createSummaryHeader(summary, ++rowNum);

            for (ExecutionSummary scenarioSummary : nestedExecutions) {
                if (scenarioSummary == null ||
                    StringUtils.isBlank(scenarioSummary.getName()) ||
                    CollectionUtils.isEmpty(scenarioSummary.getNestedExecutions())) { continue; }

                // nested should be test scenario
                rowNum = createScenarioExecutionSummary(summary, scenarioSummary, ++rowNum);

                // test scenario should be nested with activities
                List<ExecutionSummary> activitySummaries = scenarioSummary.getNestedExecutions();
                for (ExecutionSummary activitySummary : activitySummaries) {
                    if (activitySummary == null || StringUtils.isBlank(activitySummary.getName())) { continue; }
                    createActivityExecutionSummary(summary, activitySummary, rowNum++);
                }
            }

            createIterationExecutionSummary(summary, ++rowNum);

            // set column widths
            for (int i = 0; i < COLUMN_WIDTHS.size(); i++) { summarySheet.setColumnWidth(i, COLUMN_WIDTHS.get(i)); }

            excel.save();
        } catch (Throwable e) {
            ConsoleUtils.error("Unable to generate Excel report to " + testScript + ": " + e.getMessage());
        }
    }

    public void generateDetailedJson(String jsonFile) throws IOException {
        if (StringUtils.isBlank(jsonFile)) { return; }
        FileUtils.writeStringToFile(new File(jsonFile), GSON.toJson(this), DEF_CHARSET);
    }

    public void generateSummaryJson(String jsonFile) throws IOException {
        if (StringUtils.isBlank(jsonFile)) { return; }
        ExecutionSummary report = this.toSummary();
        if (report != null) { FileUtils.writeStringToFile(new File(jsonFile), GSON.toJson(report), DEF_CHARSET); }
    }

    public void generateHtmlReport(String targetHtmlFile) throws IOException {
        // ConsoleUtils.error("\n\n\nNO HTML GENERATION YET!!!\n" + toString() + "\n\n\n");
        FileUtils.writeStringToFile(new File(targetHtmlFile), toString(), DEF_CHARSET);
    }

    protected int createScenarioExecutionSummary(Worksheet sheet, ExecutionSummary summary, int rowNum) {
        createCell(sheet, "A" + rowNum, summary.getName(), EXEC_SUMM_SCENARIO);
        createCell(sheet, "E" + rowNum, DateUtility.formatLongDate(summary.startTime), EXEC_SUMM_TIMESPAN);
        createCell(sheet, "F" + rowNum, formatDuration(summary.getElapsedTime()), EXEC_SUMM_DURATION);
        //createCell(sheet, "F" + rowNum, formatDuration(summary.getElapsedTime()), EXEC_SUMM_DURATION);
        createCell(sheet, "G" + rowNum, summary.getTotalSteps(), EXEC_SUMM_FINAL_TOTAL);
        createCell(sheet, "H" + rowNum, summary.getPassCount(), EXEC_SUMM_FINAL_TOTAL);
        createCell(sheet, "I" + rowNum, summary.getFailCount(), EXEC_SUMM_FINAL_TOTAL);
        double successRate = summary.getSuccessRate();
        createCell(sheet, "J" + rowNum, MessageFormat.format(RATE_FORMAT, successRate),
                   successRate == 1 ? EXEC_SUMM_FINAL_SUCCESS : EXEC_SUMM_FINAL_NOT_SUCCESS);

        Map<String, String> ref = summary.referenceData;
        if (MapUtils.isNotEmpty(ref)) {
            List<String> refNames = CollectionUtil.toList(ref.keySet());
            for (String name : refNames) {
                createCell(sheet, "B" + rowNum, name, EXEC_SUMM_DATA_NAME);
                createCell(sheet, "C" + rowNum, ref.get(name), EXEC_SUMM_DATA_VALUE);
                rowNum++;
            }
        } else {
            rowNum++;
        }

        return rowNum;
    }

    protected void createActivityExecutionSummary(Worksheet sheet, ExecutionSummary summary, int rowNum) {
        createCell(sheet, "D" + rowNum, summary.getName(), EXEC_SUMM_ACTIVITY);
        createCell(sheet, "F" + rowNum, formatDuration(summary.getElapsedTime()), EXEC_SUMM_DURATION);
        //createCell(sheet, "F" + rowNum, formatDuration(summary.getElapsedTime()), EXEC_SUMM_DURATION);
        createCell(sheet, "G" + rowNum, summary.getTotalSteps(), EXEC_SUMM_TOTAL);
        createCell(sheet, "H" + rowNum, summary.getPassCount(), EXEC_SUMM_PASS);
        createCell(sheet, "I" + rowNum, summary.getFailCount(), EXEC_SUMM_FAIL);
        double successRate = summary.getSuccessRate();
        createCell(sheet, "J" + rowNum, MessageFormat.format(RATE_FORMAT, successRate),
                   successRate == 1 ? EXEC_SUMM_SUCCESS : EXEC_SUMM_NOT_SUCCESS);
    }

    protected void createIterationExecutionSummary(Worksheet sheet, int rowNum) {
        // just in case of sudden death
        if (startTime < 1) {
            if (CollectionUtils.isNotEmpty(nestedExecutions)) {
                startTime = nestedExecutions.get(0).startTime;
            } else {
                startTime = NumberUtils.toLong(System.getProperty(TEST_START_TS));
            }
        }

        if (endTime < 1) {
            if (CollectionUtils.isNotEmpty(nestedExecutions)) {
                endTime = nestedExecutions.get(nestedExecutions.size() - 1).endTime;
            } else {
                endTime = System.currentTimeMillis();
            }
        }

        XSSFCell mergedCell = mergeCell(new ExcelArea(sheet, new ExcelAddress("A" + rowNum + ":D" + rowNum), false),
                                        "Totals", EXEC_SUMM_FINAL_TOTAL);
        mergedCell.getRow().setHeightInPoints(EXEC_SUMMARY_HEIGHT);

        createCell(sheet, "E" + rowNum, DateUtility.formatLongDate(startTime), EXEC_SUMM_TIMESPAN);
        createCell(sheet, "F" + rowNum, formatDuration(endTime - startTime), EXEC_SUMM_FINAL_TOTAL);
        //createCell(sheet, "F" + rowNum, formatDuration(endTime - startTime), EXEC_SUMM_FINAL_TOTAL);
        createCell(sheet, "G" + rowNum, totalSteps, EXEC_SUMM_FINAL_TOTAL);
        createCell(sheet, "H" + rowNum, passCount, EXEC_SUMM_FINAL_TOTAL);
        createCell(sheet, "I" + rowNum, failCount, EXEC_SUMM_FINAL_TOTAL);
        double successRate = getSuccessRate();
        createCell(sheet, "J" + rowNum, MessageFormat.format(RATE_FORMAT, successRate),
                   successRate == 1 ? EXEC_SUMM_FINAL_SUCCESS : EXEC_SUMM_FINAL_NOT_SUCCESS);
    }

    protected int createReferenceDataSection(Worksheet summary, int rowNum) {
        Map<String, String> executionData = gatherExecutionData();
        if (MapUtils.isNotEmpty(executionData)) {
            createTestExecutionSection(summary, rowNum, "Test Execution", executionData);
            rowNum += executionData.size();
        }

        if (MapUtils.isNotEmpty(referenceData)) {
            createTestExecutionSection(summary, rowNum, "User Data (" + SCRIPT_REF_PREFIX + "*)", referenceData);
            rowNum += referenceData.size();
        }

        if (error != null) { createExceptionSection(summary, rowNum++, error); }

        return rowNum;
    }

    protected void createReportTitle(File testScript, Worksheet summary) {
        ExcelArea mergeArea = new ExcelArea(summary, new ExcelAddress(MERGE_AREA_TITLE), false);
        String mergedContent = "Execution Summary for " + StringUtils.substringBeforeLast(testScript.getName(), ".");
        XSSFCell mergedCell = mergeCell(mergeArea, mergedContent, EXEC_SUMM_TITLE);
        mergedCell.getRow().setHeightInPoints(26f);
    }

    protected void createSummaryHeader(Worksheet summary, int rowNum) {
        createCell(summary, "A" + rowNum, "scenario", EXEC_SUMM_HEADER);
        mergeCell(new ExcelArea(summary, new ExcelAddress("B" + rowNum + ":C" + rowNum), false),
                  "user data (" + SCENARIO_REF_PREFIX + "*)", EXEC_SUMM_HEADER);
        createCell(summary, "D" + rowNum, "activity", EXEC_SUMM_HEADER);
        createCell(summary, "E" + rowNum, "start date/time", EXEC_SUMM_HEADER);
        createCell(summary, "F" + rowNum, "duration (ms)", EXEC_SUMM_HEADER);
        createCell(summary, "G" + rowNum, "total", EXEC_SUMM_HEADER);
        createCell(summary, "H" + rowNum, "pass", EXEC_SUMM_HEADER);
        createCell(summary, "I" + rowNum, "fail", EXEC_SUMM_HEADER);
        createCell(summary, "J" + rowNum, "success %", EXEC_SUMM_HEADER);
    }

    protected Map<String, String> gatherExecutionData() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("run from", formatValue(runHost, runHostOs));
        map.put("run user", formatValue(runUser));
        map.put("time span",
                formatValue(DateUtility.formatLongDate(startTime) + " - " + DateUtility.formatLongDate(endTime)));
        map.put("duration", formatValue(DateUtility.formatStopWatchTime(endTime - startTime)));
        map.put("total steps", formatValue(StringUtils.leftPad(totalSteps + "", 4, " ")));
        map.put("executed steps", formatStat(executed));
        map.put("passed", formatStat(passCount));
        map.put("failed", formatStat(failCount));
        map.put("fail-fast", failedFast + "");
        map.put("nexial version", ExecUtil.deriveJarManifest());
        map.put("java version", JAVA_VERSION);

        // special case: log file is copied (NOT MOVED) to S3 with a special syntax here (markdown-like)
        // createCell() function will made regard to this format to create appropriate hyperlink-friendly cells
        map.put("log", StringUtils.isBlank(executionLog) ? "" : (executionLog + "|Click here"));

        return map;
    }

    protected static String formatLabel(String label) { return StringUtils.rightPad(label + ":", LABEL_SIZE); }

    protected static String formatValue(String value, String detail) {
        if (StringUtils.isEmpty(value)) { return lineSeparator(); }
        return StringUtils.defaultString(value) + (StringUtils.isNotEmpty(detail) ? " (" + detail + ")" : "") +
               lineSeparator();
    }

    protected static String formatValue(String value) { return StringUtils.defaultString(value) + lineSeparator(); }

    protected String formatStat(int actual) {
        return formatValue(StringUtils.leftPad("" + actual, 4, " "),
                           MessageFormat.format(RATE_FORMAT, totalSteps < 1 ? 0 : ((double) actual / totalSteps)));
    }

    protected static String formatDuration(long elapsedTime) {
        return NumberFormat.getNumberInstance(US).format(elapsedTime);
    }

    protected void createTestExecutionSection(Worksheet sheet, int rowNum, String title, Map<String, String> data) {
        createCell(sheet, "A" + rowNum, title, EXEC_SUMM_DATA_HEADER);

        List<String> names = CollectionUtil.toList(data.keySet());
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            createCell(sheet, "B" + (i + rowNum), name, EXEC_SUMM_DATA_NAME);

            String value = data.get(name);
            XSSFCell valueCell = createCell(sheet, "C" + (i + rowNum), value, EXEC_SUMM_DATA_VALUE);
            if (RegexUtils.isExact(value, REGEX_LINKABLE_DATA)) {
                String link = StringUtils.substringBefore(value, "|");
                String text = StringUtils.substringAfter(value, "|");
                Excel.setHyperlink(valueCell, link, text);
            }
        }
    }

    protected XSSFCell createCell(Worksheet worksheet, String cellAddress, Object content, StyleConfig styleConfig) {
        if (worksheet == null) { return null; }
        if (StringUtils.isBlank(cellAddress)) { return null; }

        ExcelAddress addr = new ExcelAddress(cellAddress);

        worksheet.setRowValues(addr, Collections.singletonList(Objects.toString(content)));
        XSSFCell cell = worksheet.cell(addr);
        if (cell == null) { return null; }

        cell.setCellStyle(StyleDecorator.generate(worksheet, styleConfig));
        cell.getRow().setHeightInPoints(EXEC_SUMMARY_HEIGHT);

        return cell;
    }

    protected void createExceptionSection(Worksheet sheet, int rowNum, Throwable exception) {
        createCell(sheet, "A" + rowNum, "Fatal Error", EXEC_SUMM_DATA_HEADER);

        String eol = lineSeparator();

        String errorMessage = ExceptionUtils.getMessage(exception);
        String rootCauseMessage = ExceptionUtils.getRootCauseMessage(exception);
        String[] stackTrace = ExceptionUtils.getStackFrames(exception);
        StringBuilder error = new StringBuilder();
        if (StringUtils.isNotBlank(errorMessage)) { error.append("ERROR: ").append(errorMessage).append(eol); }
        if (StringUtils.isNotBlank(rootCauseMessage)) {
            error.append("ROOT CAUSE: ").append(EnvUtils.platformSpecificEOL(rootCauseMessage)).append(eol);
        }
        for (String errorDetail : stackTrace) {
            if (StringUtils.contains(errorDetail, "nexial")) {
                error.append(errorDetail).append(eol);
            } else {
                break;
            }
        }

        errorMessage = StringUtils.trim(error.toString());

        mergeCell(new ExcelArea(sheet, new ExcelAddress("B" + rowNum + ":J" + rowNum), false),
                  errorMessage,
                  EXEC_SUMM_EXCEPTION);
    }

    protected XSSFCell mergeCell(ExcelArea mergeArea, String mergedContent, StyleConfig styleConfig) {
        if (mergeArea == null || mergeArea.getAddr().getColumnCount() < 1 || mergeArea.getAddr().getRowCount() < 1) {
            return null;
        }

        Worksheet worksheet = mergeArea.getWorksheet();
        if (worksheet == null) { return null; }

        XSSFSheet sheet = worksheet.getSheet();
        if (sheet == null) { return null; }

        ExcelAddress addr = mergeArea.getAddr();
        int startRowIdx = addr.getRowStartIndex();
        int endRowIdx = addr.getRowEndIndex();
        int startColumnIdx = addr.getColumnStartIndex();
        int endColumnIndex = addr.getColumnEndIndex();

        XSSFCellStyle cellStyle = StyleDecorator.generate(worksheet, styleConfig);

        // force all merge candidate to take on the designated style first so that after merge the style will stick
        for (int i = startRowIdx; i <= endRowIdx; i++) {
            XSSFRow row = sheet.getRow(addr.getRowStartIndex());
            if (row == null) { row = sheet.createRow(addr.getRowStartIndex()); }
            row.setHeightInPoints(EXEC_SUMMARY_HEIGHT);

            for (int j = startColumnIdx; j <= endColumnIndex; j++) {
                XSSFCell cell = row.getCell(j, CREATE_NULL_AS_BLANK);
                cell.setCellStyle(cellStyle);
            }
        }

        sheet.addMergedRegion(new CellRangeAddress(startRowIdx, endRowIdx, startColumnIdx, endColumnIndex));

        XSSFRow row = sheet.getRow(addr.getRowStartIndex());
        if (row == null) { row = sheet.createRow(addr.getRowStartIndex()); }

        XSSFCell cellMerge = row.getCell(addr.getColumnStartIndex(), CREATE_NULL_AS_BLANK);
        if (cellMerge == null) { return null; }

        boolean multiline = StringUtils.contains(mergedContent, "\n");

        if (StringUtils.isNotEmpty(mergedContent)) {
            if (multiline) {
                cellMerge.setCellValue(new XSSFRichTextString(mergedContent));
            } else {
                cellMerge.setCellValue(mergedContent);
            }
        }

        if (styleConfig != null) {
            if (multiline) { cellStyle.setWrapText(true); }
            cellMerge.setCellStyle(cellStyle);
        }

        if (StringUtils.isNotEmpty(mergedContent)) {
            int mergedWidth = 0;
            for (int j = startColumnIdx; j < endColumnIndex + 1; j++) { mergedWidth += sheet.getColumnWidth(j); }
            int charPerLine = (int) ((mergedWidth - DEF_CHAR_WIDTH) * addr.getRowCount() /
                                     (DEF_CHAR_WIDTH * FONT_HEIGHT_DEFAULT));

            int lineCount = StringUtils.countMatches(mergedContent, "\n") + 1;
            String[] lines = StringUtils.split(mergedContent, "\n");
            if (ArrayUtils.isEmpty(lines)) { lines = new String[]{mergedContent}; }
            for (String line : lines) { lineCount += Math.ceil((double) StringUtils.length(line) / charPerLine) - 1; }

            // lineCount should always be at least 1. otherwise this row will not be rendered with height 0
            if (lineCount < 1) { lineCount = 1; }

            worksheet.setHeight(cellMerge, lineCount);
        }

        return cellMerge;
    }

    private ExecutionSummary toSummary() {
        // execution
        ExecutionSummary summary = summarized(this);
        if (summary == null) { return null; }

        // for execution-level, we need to add referenceData back to track execution-level context data
        summary.referenceData = this.getReferenceData();

        summary.runHost = this.runHost;
        summary.runHostOs = this.runHostOs;
        summary.runUser = this.runUser;

        this.getNestedExecutions().forEach(exec -> {
            // script
            ExecutionSummary scriptExec = summarized(exec);
            if (scriptExec != null) {
                // iteration
                exec.getNestedExecutions().forEach(exec2 -> {
                    ExecutionSummary iterationExec = summarized(exec2);
                    if (iterationExec != null) {
                        iterationExec.nestedExecutions = null;
                        scriptExec.addNestSummary(iterationExec);
                    }
                });

                summary.addNestSummary(scriptExec);
            }
        });

        // we stop here
        return summary;
    }

    private ExecutionSummary summarized(ExecutionSummary source) {
        // check for "not started" tests due to preceding error
        if (source.startTime == 0 || source.endTime == 0 || source.totalSteps == 0 || source.executed == 0) {
            // nothing started on unkown time and nothing executed? this is useless
            return null;
        }

        ExecutionSummary summary = new ExecutionSummary();
        summary.setName(source.name);
        summary.setStartTime(source.startTime);
        summary.setEndTime(source.endTime);
        summary.setTotalSteps(source.totalSteps);
        summary.setPassCount(source.passCount);
        summary.setFailCount(source.failCount);
        summary.setWarnCount(source.warnCount);
        summary.setExecuted(source.executed);
        summary.executionLog = source.executionLog;
        summary.setTestScriptLink(source.getTestScriptLink());
        summary.referenceData = null;
        summary.runHost = null;
        summary.runHostOs = null;
        summary.runUser = null;
        return summary;
    }
}
