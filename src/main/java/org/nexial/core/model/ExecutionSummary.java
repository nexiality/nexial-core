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
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.jetbrains.annotations.NotNull;
import org.nexial.commons.utils.*;
import org.nexial.core.NexialConst;
import org.nexial.core.NexialConst.Recording.Types;
import org.nexial.core.aws.NexialS3Helper;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.excel.ExcelStyleHelper;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;
import org.nexial.core.utils.OutputFileUtils;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static java.util.Locale.US;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.commons.utils.FileUtil.SortBy.FILENAME_ASC;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.*;
import static org.nexial.core.utils.ExecUtils.*;

/**
 * serves as the summary of a test execution, which can be scoped into:
 * <ul>
 * <li>a test activity - logical grouping of a set of contiguous steps that represents a business function.</li>
 * <li>a scenario - a set of test activities to accomplish a business transaction or goal.</li>
 * <li>an iteration - one run of a series of scenarios.</li>
 * <li>a test script - one or more iteration.</li>
 * <li>a test execution - execution of 1 or more test scripts.</li>
 * </ul>
 * <p>
 * at the end of each 'scope', test summary are aggregated upward to its parent summary information.
 */
public class ExecutionSummary {
    protected static final int LABEL_SIZE = 10;
    protected static final String MERGE_AREA_TITLE = "A1:J1";
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
    private static final int STEP_LENGTH = 5;
    private String name;
    private ExecutionLevel executionLevel;
    private String scriptFile;
    private String dataFile;
    private transient File testScript;
    private String testScriptLink;

    private String runHost;
    private String runHostOs;
    private String runUser;
    private final String triggerUser;
    private final String triggerUrl;
    private final String triggerId;
    private long startTime;
    private long endTime;
    private int totalLevelPassed;
    private int totalSteps;
    private int passCount;
    private int failCount;
    private int warnCount;
    private int executed;
    private String customHeader;
    private String customFooter;

    private boolean failedFast;
    private String errorStackTrace;
    private Throwable error;
    private String executionLog;
    private final Map<String, String> logs = new TreeMap<>();

    // optional
    private Map<String, String> referenceData = new TreeMap<>();

    private List<ExecutionSummary> nestedExecutions = new ArrayList<>();

    // not persisted to JSON
    private final transient Map<TestStepManifest, List<NestedMessage>> nestMessages = new LinkedHashMap<>();

    // only applicable to script in plan
    private int planSequence;
    private int iterationIndex;
    private int iterationTotal;

    // only application to plan
    private String planName;
    private String planFile;
    private String planDescription;

    // supplemental
    private String outputPath;
    private Map<String, Map<String, String>> screenRecordings;
    private WebServiceLogs wsLogs;

    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }

    public static void gatherSupplementProofs(@NotNull ExecutionSummary summary) {
        if (!FileUtil.isDirectoryReadable(summary.outputPath)) { return; }

        // 1. get capture directory
        String captureDir = NexialConst.Project.appendCapture(summary.outputPath);

        // 2. look for videos
        String recordingFilesRegex = ".+\\.(" +
                                     (Arrays.stream(Types.values()).map(Enum::name).collect(Collectors.joining("|"))) +
                                     ")";

        // 3. organize videos by "readable" name
        summary.screenRecordings = new TreeMap<String, Map<String, String>>(
            FileUtil.listFiles(captureDir, recordingFilesRegex, false, FILENAME_ASC).stream()
                    .collect(Collectors.toMap(file -> SUBDIR_CAPTURES + "/" + file.getName(),
                                              file -> OutputFileUtils.distillOutputFile(file.getAbsolutePath()))));

        // 4. get log directory
        String logDir = NexialConst.Project.appendLog(summary.outputPath);

        // 5. look for ws-summary log
        List<File> wsLogs = FileUtil.listFiles(logDir, "nexial\\-ws\\-.+\\.log", false);
        WebServiceSummaryLog wsSummaryLog = CollectionUtils.isNotEmpty(wsLogs) ?
                                            new WebServiceSummaryLog(wsLogs.get(0)) : null;

        // 6. map api call to ws-detail log
        // 7. organize api calls by "invocation source" to ws details
        List<WebServiceDetailLog> wsDetailLogList =
            FileUtil.listFiles(summary.outputPath, ".+\\.ws\\-detail\\.log", false, FILENAME_ASC).stream()
                    .map(logFile -> {
                        Map<String, String> meta = OutputFileUtils.distillOutputFile(logFile.getAbsolutePath());
                        return new WebServiceDetailLog(meta.get("filename"),
                                                       meta.get("script"),
                                                       meta.get("scenario"),
                                                       meta.get("iteration"),
                                                       meta.get("row"),
                                                       meta.get("repeatUntilLoopIndex"),
                                                       logFile);
                    })
                    .collect(Collectors.toList());
        if (wsSummaryLog != null || CollectionUtils.isNotEmpty(wsDetailLogList)) {
            summary.wsLogs = new WebServiceLogs(wsSummaryLog, wsDetailLogList);
        }

        // 8. create chart
        // todo
    }

    public enum ExecutionLevel { EXECUTION, SCRIPT, ITERATION, SCENARIO, ACTIVITY, STEP }

    public ExecutionSummary() {
        // support "build-user-vars" plugin (Jenkins)
        // https://plugins.jenkins.io/build-user-vars-plugin/
        triggerUser = StringUtils.defaultString(System.getenv("BUILD_USER_ID"));
        // use standard Jenkins vars (other CI/CD may be supported in the future)
        triggerUrl = StringUtils.defaultString(System.getenv("BUILD_URL"));
        triggerId = StringUtils.trim(StringUtils.defaultString(System.getenv("JOB_NAME")) + " " +
                                     StringUtils.defaultString(System.getenv("BUILD_ID")));
        runUser = USER_NAME;
        runHostOs = OS_ARCH + " " + OS_NAME + " " + OS_VERSION;
        runHost = StringUtils.upperCase(EnvUtils.getHostName());

        // set up startTime now, just in case this execution didn't complete normally..
        // then at least we'll have the time when this failed test started
        startTime = System.currentTimeMillis();
    }

    public String getErrorStackTrace() { return errorStackTrace; }

    public Throwable getError() { return error; }

    public void setError(Throwable error) {
        if (error != null) {
            this.error = error;
            errorStackTrace = ExceptionUtils.getStackTrace(error);
        }
    }

    public int getIterationIndex() { return iterationIndex; }

    public void setIterationIndex(int iterationIndex) { this.iterationIndex = iterationIndex; }

    public int getIterationTotal() { return iterationTotal; }

    public void setIterationTotal(int iterationTotal) { this.iterationTotal = iterationTotal; }

    public int getPlanSequence() { return planSequence; }

    public void setPlanSequence(int planSequence) { this.planSequence = planSequence; }

    public String getPlanName() { return planName; }

    public void setPlanName(String planName) { this.planName = planName; }

    public String getPlanFile() { return planFile; }

    public void setPlanFile(String planFile) { this.planFile = planFile; }

    public String getPlanDescription() { return planDescription; }

    public void setPlanDescription(String planDescription) { this.planDescription = planDescription; }

    public String getRunHost() { return runHost; }

    public String getRunHostOs() { return runHostOs; }

    public String getRunUser() { return runUser; }

    public String getTriggerUser() { return triggerUser; }

    public String getTriggerUrl() { return triggerUrl; }

    public String getTriggerId() { return triggerId; }

    public String getManifest() { return NEXIAL_MANIFEST; }

    public long getStartTime() { return startTime; }

    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }

    public void setEndTime(long endTime) { this.endTime = endTime; }

    public long getElapsedTime() { return this.endTime - this.startTime; }

    public int getTotalSteps() { return totalSteps; }

    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }

    public int getTotalLevelPassed() { return totalLevelPassed; }

    /** when a test step is skipped, we would need to adjust the total number of execution */
    public void adjustTotalSteps(int changeBy) { this.totalSteps += changeBy; }

    public void adjustPassCount(int changeBy) { this.passCount += changeBy; }

    public void adjustExecutedSteps(int changeBy) { this.executed += changeBy; }

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

    public String getSuccessRateString() { return MessageFormat.format(RATE_FORMAT, getSuccessRate()); }

    public boolean isFailedFast() { return failedFast; }

    public void setFailedFast(boolean failedFast) { this.failedFast = failedFast; }

    public File getTestScript() { return testScript; }

    public void setTestScript(File testScript) { this.testScript = testScript; }

    public String getTestScriptLink() { return testScriptLink; }

    public void setTestScriptLink(String testScriptLink) { this.testScriptLink = testScriptLink; }

    public String getScriptFile() { return scriptFile; }

    public void setScriptFile(String scriptFile) { this.scriptFile = scriptFile; }

    public String getDataFile() { return dataFile; }

    public void setDataFile(String dataFile) { this.dataFile = dataFile; }

    public String getExecutionLog() { return executionLog; }

    public void updateExecutionLogLocation(String url) { executionLog = url; }

    public Map<String, String> getLogs() { return logs; }

    public String resolveDataFile() {
        if (StringUtils.isNotBlank(dataFile)) { return dataFile; }

        if (referenceData.containsKey(DATA_FILE)) { return referenceData.get(DATA_FILE); }

        if (CollectionUtils.isNotEmpty(nestedExecutions)) {
            ExecutionSummary nested = nestedExecutions.get(0);
            if (nested != null && nested.getReferenceData().containsKey(DATA_FILE)) {
                return nested.getReferenceData().get(DATA_FILE);
            }
        }

        return "";
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = handleWindowsChar(name); }

    public Map<String, String> getReferenceData() { return referenceData; }

    public void addNestedMessages(TestStepManifest testStep, List<NestedMessage> nestedTestResults) {
        nestMessages.put(testStep, nestedTestResults);
    }

    public void addNestSummary(ExecutionSummary nested) { this.nestedExecutions.add(nested); }

    public List<ExecutionSummary> getNestedExecutions() { return nestedExecutions; }

    public ExecutionLevel getExecutionLevel() { return executionLevel; }

    public void setExecutionLevel(ExecutionLevel executionLevel) { this.executionLevel = executionLevel; }

    public String getCustomHeader() { return customHeader; }

    public void setCustomHeader(String customHeader) { this.customHeader = customHeader; }

    public String getCustomFooter() { return customFooter; }

    public void setCustomFooter(String customFooter) { this.customFooter = customFooter; }

    public void aggregatedNestedExecutions(ExecutionContext context) {
        if (CollectionUtils.isEmpty(nestedExecutions)) { return; }

        //startTime = 0;
        //endTime = 0;
        totalSteps = 0;
        executed = 0;
        passCount = 0;
        failCount = 0;
        warnCount = 0;
        totalLevelPassed = 0;

        nestedExecutions.forEach(nested -> {
            if (startTime == 0) { startTime = nested.startTime; }
            if (nested.startTime != 0 && nested.startTime < startTime) { startTime = nested.startTime; }

            if (endTime == 0) { endTime = nested.endTime; }
            if (nested.endTime != 0 && nested.endTime > endTime) { endTime = nested.endTime; }

            totalSteps += nested.totalSteps;
            executed += nested.executed;
            passCount += nested.passCount;
            failCount += nested.failCount;
            warnCount += nested.warnCount;
            if (nested.executed == nested.passCount && nested.executed != 0) { totalLevelPassed++; }

            if (nested.error != null && error == null) { error = nested.error; }
            if (nested.errorStackTrace != null && errorStackTrace == null) { errorStackTrace = nested.errorStackTrace; }
        });

        importSysProps();

        if (context != null) {
            failedFast = context.isFailFast();
            if (executionLevel == SCENARIO) { referenceData = context.gatherScenarioReferenceData(); }
            if (executionLevel == ITERATION) { referenceData = context.gatherScriptReferenceData(); }

            String logPath = System.getProperty(TEST_LOG_PATH);
            executionLog = logPath + separator + NEXIAL_LOG_PREFIX + context.getRunId() + ".log";

            Collection<File> logFiles = FileUtils.listFiles(new File(logPath), new String[]{"log"}, false);
            if (CollectionUtils.isNotEmpty(logFiles) || logFiles.size() > 1) {
                // if only 1 log file found - assume this one is the nexial log
                logFiles.forEach(file -> {
                    if (file.length() > 1) { logs.put(file.getName(), file.getAbsolutePath()); }
                });
            }

            // only transfer log file if
            // - execution level is EXECUTION (so that we transfer towards the end of execution)
            // - output to cloud is on
            if (executionLevel == SCENARIO || executionLevel == ACTIVITY || !context.isOutputToCloud()) { return; }

            try {
                NexialS3Helper otc = context.getOtc();
                if (!otc.isReadyForUse()) {
                    ConsoleUtils.error(toCloudIntegrationNotReadyMessage("logs"));
                    return;
                }

                // export log files to cloud
                if (MapUtils.isNotEmpty(logs)) {
                    List<String> otherLogs = CollectionUtil.toList(logs.keySet());
                    for (String name : otherLogs) { logs.put(name, otc.importLog(new File(logs.get(name)), false)); }
                } else if (FileUtil.isFileReadable(executionLog)) {
                    executionLog = otc.importLog(new File(executionLog), false);
                }
            } catch (IOException e) {
                ConsoleUtils.error(toCloudIntegrationNotReadyMessage(executionLog) + ": " + e.getMessage());
            }
        }
    }

    public Map<String, Map<String, String>> getScreenRecordings() { return screenRecordings; }

    public WebServiceLogs getWsLogs() { return wsLogs; }

    public String toString() {
        StringBuilder text = new StringBuilder();

        text.append(formatLabel("Run From")).append(formatValue(runHost, runHostOs));
        text.append(formatLabel("Run User")).append(formatValue(runUser));
        text.append(formatLabel("Time Span")).append(formatValue(DateUtility.formatLongDate(startTime) + " - " +
                                                                 DateUtility.formatLongDate(endTime)));
        text.append(formatLabel("Duration")).append(formatValue(DateUtility.formatStopWatchTime(endTime - startTime)));
        text.append(formatLabel("Steps")).append(formatValue(StringUtils.leftPad(totalSteps + "", STEP_LENGTH, " ")));
        text.append(formatLabel("Executed")).append(formatStat(executed, totalSteps));
        text.append(formatLabel("PASS")).append(formatStat(passCount, totalSteps));
        text.append(formatLabel("FAIL")).append(formatStat(failCount, totalSteps));

        return text.toString();
    }

    public Map<TestStepManifest, List<NestedMessage>> getNestMessages() { return nestMessages; }

    public void generateExcelReport(File testScript) {
        if (FileUtil.isFileReadable(testScript, MIN_EXCEL_FILE_SIZE)) {
            try {
                generateExcelReport(new Excel(testScript, false, false));
            } catch (IOException e) {
                ConsoleUtils.error("Unable to generate execution output: " + e.getMessage());
            }
        }
    }

    public void generateExcelReport(Excel testScript) {
        if (testScript == null) {
            ConsoleUtils.error("Unable to generate Excel report because test script is null");
            return;
        }
        if (!FileUtil.isFileReadable(testScript.getFile(), MIN_EXCEL_FILE_SIZE)) {
            ConsoleUtils.error("Unable to generate Excel report because " + testScript.getFile() + " is not readable");
            return;
        }

        // initialize result-related styles so that we can reuse them here
        testScript.initResultCommonStyles();
        File scriptFile = testScript.getFile();

        try {
            XSSFWorkbook workbook = testScript.getWorkbook();
            Worksheet summary = testScript.worksheet(SUMMARY_TAB_NAME, true);
            workbook.setSheetOrder(summary.getName(), 0);
            workbook.setSelectedTab(0);
            workbook.setActiveSheet(0);
            XSSFSheet summarySheet = summary.getSheet();

            // title
            createReportTitle(scriptFile, summary);

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

            testScript.save();
        } catch (Throwable e) {
            ConsoleUtils.error("Unable to generate Excel report to " + testScript + ": " + e.getMessage());
        }
    }

    public static Map<String, String> gatherExecutionData(ExecutionSummary summary) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("run from", ExecutionSummary.formatValue(summary.runHost, summary.runHostOs));
        map.put("run user", ExecutionSummary.formatValue(summary.runUser));
        map.put("time span", ExecutionSummary.formatValue(
            DateUtility.formatLongDate(summary.startTime) + " - " + DateUtility.formatLongDate(summary.endTime)));
        map.put("start time", ExecutionSummary.formatValue(DateUtility.formatLongDate(summary.startTime)));
        map.put("end time", ExecutionSummary.formatValue(DateUtility.formatLongDate(summary.endTime)));
        map.put("duration", ExecutionSummary.formatValue(DateUtility.formatStopWatchTime(
            summary.endTime - summary.startTime)));
        map.put("scenario passed", ExecutionSummary.formatValue(summary.resolveTotalScenariosPassed()));
        map.put("total steps", ExecutionSummary.formatValue(StringUtils.leftPad(
            summary.totalSteps + "", STEP_LENGTH, " ")));
        map.put("executed steps", ExecutionSummary.formatStat(summary.executed, summary.totalSteps));
        map.put("passed", ExecutionSummary.formatStat(summary.passCount, summary.totalSteps));
        map.put("failed", ExecutionSummary.formatStat(summary.failCount, summary.totalSteps));
        map.put("fail-fast", summary.failedFast + "");
        map.put("nexial version", NEXIAL_MANIFEST);
        map.put("java version", JAVA_VERSION);

        if (ExecUtils.isRunningInCi()) {
            map.put("JENKINS::build url", ExecUtils.currentCiBuildUrl());
            map.put("JENKINS::build id", ExecUtils.currentCiBuildId());
            map.put("JENKINS::build number", ExecUtils.currentCiBuildNumber());
            map.put("JENKINS::build user", ExecUtils.currentCiBuildUser());
        }

        // special case: log file is copied (NOT MOVED) to S3 with a special syntax here (markdown-like)
        // createCell() function will made regard to this format to create appropriate hyperlink-friendly cells
        StringBuilder allLogs = new StringBuilder();
        if (StringUtils.isNotBlank(summary.executionLog)) {
            allLogs.append(summary.executionLog).append("|nexial log\n");
        }
        if (MapUtils.isNotEmpty(summary.logs)) {
            summary.logs.forEach((name, path) -> {
                if (!StringUtils.equals(path, summary.executionLog)) {
                    allLogs.append(path).append("|").append(name).append("\n");
                }
            });
        }

        if (allLogs.length() != 0) { map.put("log", StringUtils.trim(allLogs.toString())); }

        String runtimeArgs = System.getProperty("execution." + RUNTIME_ARGS);
        if (StringUtils.isNotBlank(runtimeArgs)) { map.put(RUNTIME_ARGS, runtimeArgs); }

        String javaOpt = System.getProperty("execution." + JAVA_OPT);
        if (StringUtils.isNotBlank(javaOpt)) { map.put(JAVA_OPT, javaOpt); }

        return map;
    }

    public ExecutionSummary toSummary() {
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

    public String resolveTotalScenariosPassed() {
        int totalScenarios = 0;
        int totalScenariosPassed = 0;

        if (executionLevel == EXECUTION) {
            for (ExecutionSummary nested : nestedExecutions) {
                //  all scenarios passed in entire execution
                for (ExecutionSummary nested1 : nested.nestedExecutions) {
                    totalScenarios += nested1.nestedExecutions.size();
                    totalScenariosPassed += nested1.totalLevelPassed;
                }
            }
        } else if (executionLevel == SCRIPT) {
            for (ExecutionSummary nested1 : nestedExecutions) {
                totalScenarios += nested1.nestedExecutions.size();
                totalScenariosPassed += nested1.totalLevelPassed;
            }
        } else if (executionLevel == ITERATION) {
            // added for the excel output
            totalScenarios = nestedExecutions.size();
            totalScenariosPassed = totalLevelPassed;
        }
        return totalScenariosPassed + " / " + totalScenarios;
    }

    public boolean showPlan(ExecutionSummary executionSummary, int index) {
        if (StringUtils.isBlank(planFile)) { return false; }
        if (index == 0) { return true; }
        // for concurrent script execution in plan
        ExecutionSummary summary = executionSummary.getNestedExecutions().get(index - 1);
        return !(StringUtils.equals(planFile, summary.planFile) && StringUtils.equals(planName, summary.planName));
    }

    protected int createScenarioExecutionSummary(Worksheet sheet, ExecutionSummary summary, int rowNum) {
        float rowHeight = EXEC_SUMMARY_HEIGHT;
        createCell(sheet, "A" + rowNum, summary.getName(), STYLE_EXEC_SUMM_SCENARIO, rowHeight);
        createCell(sheet, "E" + rowNum, DateUtility.formatLongDate(summary.startTime), STYLE_EXEC_SUMM_TIMESPAN,
                   rowHeight);
        createCell(sheet, "F" + rowNum, formatDuration(summary.getElapsedTime()), STYLE_EXEC_SUMM_DURATION, rowHeight);
        //createCell(sheet, "F" + rowNum, formatDuration(summary.getElapsedTime()), EXEC_SUMM_DURATION);
        createCell(sheet, "G" + rowNum, summary.getTotalSteps(), STYLE_EXEC_SUMM_FINAL_TOTAL, rowHeight);
        createCell(sheet, "H" + rowNum, summary.getPassCount(), STYLE_EXEC_SUMM_FINAL_TOTAL, rowHeight);
        createCell(sheet, "I" + rowNum, summary.getFailCount(), STYLE_EXEC_SUMM_FINAL_TOTAL, rowHeight);
        double successRate = summary.getSuccessRate();
        createCell(sheet, "J" + rowNum, MessageFormat.format(RATE_FORMAT, successRate),
                   successRate == 1 ? STYLE_EXEC_SUMM_FINAL_SUCCESS : STYLE_EXEC_SUMM_FINAL_NOT_SUCCESS,
                   rowHeight);

        Map<String, String> ref = summary.referenceData;
        if (MapUtils.isNotEmpty(ref)) {
            List<String> refNames = CollectionUtil.toList(ref.keySet());
            for (String name : refNames) {
                if (StringUtils.isBlank(name)) { continue; }

                String value = ref.get(name);
                if (StringUtils.isEmpty(value)) { continue; }

                // don't print empty/missing ref data
                createCell(sheet, "B" + rowNum, name, STYLE_EXEC_SUMM_DATA_NAME, rowHeight);
                createCell(sheet, "C" + rowNum, value, STYLE_EXEC_SUMM_DATA_VALUE, rowHeight);
                rowNum++;
            }
        } else {
            rowNum++;
        }

        return rowNum;
    }

    protected void createActivityExecutionSummary(Worksheet sheet, ExecutionSummary summary, int rowNum) {
        float rowHeight = EXEC_SUMMARY_HEIGHT;
        createCell(sheet, "D" + rowNum, summary.getName(), STYLE_EXEC_SUMM_ACTIVITY, rowHeight);
        createCell(sheet, "F" + rowNum, formatDuration(summary.getElapsedTime()), STYLE_EXEC_SUMM_DURATION, rowHeight);
        //createCell(sheet, "F" + rowNum, formatDuration(summary.getElapsedTime()), EXEC_SUMM_DURATION);
        createCell(sheet, "G" + rowNum, summary.getTotalSteps(), STYLE_EXEC_SUMM_TOTAL, rowHeight);
        createCell(sheet, "H" + rowNum, summary.getPassCount(), STYLE_EXEC_SUMM_PASS, rowHeight);
        createCell(sheet, "I" + rowNum, summary.getFailCount(), STYLE_EXEC_SUMM_FAIL, rowHeight);
        double successRate = summary.getSuccessRate();
        createCell(sheet, "J" + rowNum, summary.getSuccessRateString(),
                   successRate == 1 ? STYLE_EXEC_SUMM_SUCCESS : STYLE_EXEC_SUMM_NOT_SUCCESS,
                   rowHeight);
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

        float rowHeight = EXEC_SUMMARY_HEIGHT;
        createCell(sheet, "E" + rowNum, DateUtility.formatLongDate(startTime), STYLE_EXEC_SUMM_TIMESPAN, rowHeight);
        createCell(sheet, "F" + rowNum, formatDuration(endTime - startTime), STYLE_EXEC_SUMM_FINAL_TOTAL, rowHeight);
        //createCell(sheet, "F" + rowNum, formatDuration(endTime - startTime), EXEC_SUMM_FINAL_TOTAL);
        createCell(sheet, "G" + rowNum, totalSteps, STYLE_EXEC_SUMM_FINAL_TOTAL, rowHeight);
        createCell(sheet, "H" + rowNum, passCount, STYLE_EXEC_SUMM_FINAL_TOTAL, rowHeight);
        createCell(sheet, "I" + rowNum, failCount, STYLE_EXEC_SUMM_FINAL_TOTAL, rowHeight);
        double successRate = getSuccessRate();
        createCell(sheet, "J" + rowNum, getSuccessRateString(),
                   successRate == 1 ? STYLE_EXEC_SUMM_FINAL_SUCCESS : STYLE_EXEC_SUMM_FINAL_NOT_SUCCESS,
                   rowHeight);
    }

    protected int createReferenceDataSection(Worksheet summary, int rowNum) {
        Map<String, String> executionData = gatherExecutionData(this);

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
        float rowHeight = EXEC_SUMMARY_HEIGHT;
        createCell(summary, "A" + rowNum, "scenario", STYLE_EXEC_SUMM_HEADER, rowHeight);
        mergeCell(new ExcelArea(summary, new ExcelAddress("B" + rowNum + ":C" + rowNum), false),
                  "user data (" + SCENARIO_REF_PREFIX + "*)",
                  EXEC_SUMM_HEADER);
        createCell(summary, "D" + rowNum, "activity", STYLE_EXEC_SUMM_HEADER, rowHeight);
        createCell(summary, "E" + rowNum, "start date/time", STYLE_EXEC_SUMM_HEADER, rowHeight);
        createCell(summary, "F" + rowNum, "duration (ms)", STYLE_EXEC_SUMM_HEADER, rowHeight);
        createCell(summary, "G" + rowNum, "total", STYLE_EXEC_SUMM_HEADER, rowHeight);
        createCell(summary, "H" + rowNum, "pass", STYLE_EXEC_SUMM_HEADER, rowHeight);
        createCell(summary, "I" + rowNum, "fail", STYLE_EXEC_SUMM_HEADER, rowHeight);
        createCell(summary, "J" + rowNum, "success %", STYLE_EXEC_SUMM_HEADER, rowHeight);
    }

    protected static String formatLabel(String label) { return StringUtils.rightPad(label + ":", LABEL_SIZE); }

    protected static String formatValue(String value, String detail) {
        if (StringUtils.isEmpty(value)) { return lineSeparator(); }
        return StringUtils.defaultString(value) + (StringUtils.isNotEmpty(detail) ? " (" + detail + ")" : "") +
               lineSeparator();
    }

    protected static String formatValue(String value) { return StringUtils.defaultString(value) + lineSeparator(); }

    protected static String formatStat(int actual, int total) {
        return formatValue("" + actual, MessageFormat.format(RATE_FORMAT, total < 1 ? 0 : ((double) actual / total)));
    }

    protected static String formatDuration(long elapsedTime) {
        return NumberFormat.getNumberInstance(US).format(elapsedTime);
    }

    protected void createTestExecutionSection(Worksheet sheet, int rowNum, String title, Map<String, String> data) {
        // gotta make sure we don't print empty/missing ref.
        if (MapUtils.isEmpty(data)) { return; }

        List<String> names = CollectionUtil.toList(data.keySet());
        names.forEach(name -> {
            if (StringUtils.isBlank(name) || StringUtils.isEmpty(data.get(name))) { data.remove(name); }
        });

        if (MapUtils.isEmpty(data)) { return; }

        float rowHeight = EXEC_SUMMARY_HEIGHT;
        createCell(sheet, "A" + rowNum, title, STYLE_EXEC_SUMM_DATA_HEADER, rowHeight);

        names = CollectionUtil.toList(data.keySet());
        for (int i = 0; i < names.size(); i++) {
            int dataRow = i + rowNum;

            String name = names.get(i);
            createCell(sheet, "B" + dataRow, name, STYLE_EXEC_SUMM_DATA_NAME, rowHeight);

            String value = data.get(name);
            String[] values = StringUtils.splitByWholeSeparator(value, "\n");
            for (int j = 0; j < values.length; j++) {
                String dataValue = values[j];
                if (StringUtils.isNotBlank(dataValue)) {
                    createLinkCell(sheet, (char) ('C' + j) + "" + dataRow, dataValue, STYLE_EXEC_SUMM_DATA_VALUE);
                }
            }
        }
    }

    protected void createCell(Worksheet worksheet, String cellAddress, Object content, String style, float rowHeight) {
        if (worksheet == null || StringUtils.isBlank(cellAddress)) { return; }

        worksheet.setRowValues(new ExcelAddress(cellAddress),
                               Collections.singletonList(Objects.toString(content)),
                               style,
                               rowHeight);
    }

    protected void createLinkCell(Worksheet worksheet, String cellAddress, Object content, String styleName) {
        if (worksheet == null || StringUtils.isBlank(cellAddress)) { return; }

        ExcelAddress addr = new ExcelAddress(cellAddress);

        String dataValue = Objects.toString(content);
        if (RegexUtils.isExact(dataValue, REGEX_LINKABLE_DATA)) {
            String link = StringUtils.substringBefore(dataValue, "|");
            String text = StringUtils.substringAfter(dataValue, "|");
            worksheet.setLinkCell(addr, link, text, styleName, EXEC_SUMMARY_HEIGHT);
        } else {
            createCell(worksheet, cellAddress, content, styleName, EXEC_SUMMARY_HEIGHT);
        }
    }

    protected void createExceptionSection(Worksheet sheet, int rowNum, Throwable exception) {
        createCell(sheet, "A" + rowNum, "Fatal Error", STYLE_EXEC_SUMM_DATA_HEADER, EXEC_SUMMARY_HEIGHT);

        String eol = lineSeparator();
        StringBuilder error = new StringBuilder();

        String errorMessage = ExceptionUtils.getMessage(exception);
        String rootCauseMessage = ExceptionUtils.getRootCauseMessage(exception);
        if (StringUtils.isNotBlank(errorMessage)) { error.append(errorMessage).append(eol); }
        if (StringUtils.isNotBlank(rootCauseMessage) && !StringUtils.equals(errorMessage, rootCauseMessage)) {
            error.append("ROOT CAUSE: ").append(EnvUtils.platformSpecificEOL(rootCauseMessage)).append(eol);
        }

        String[] stackTrace = ExceptionUtils.getStackFrames(exception);
        for (String errorDetail : stackTrace) {
            if (StringUtils.contains(errorDetail, "nexial")) {
                error.append(errorDetail).append(eol);
            } else {
                break;
            }
        }

        errorMessage = TextUtils.demarcate(StringUtils.trim(error.toString()), 120, eol);

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

        XSSFCellStyle cellStyle = ExcelStyleHelper.generate(worksheet, styleConfig);

        // force all merge candidate to take on the designated style first so that after merge the style will stick
        for (int i = startRowIdx; i <= endRowIdx; i++) {
            XSSFRow row = sheet.getRow(addr.getRowStartIndex());
            if (row == null) { row = sheet.createRow(addr.getRowStartIndex()); }
            row.setHeightInPoints(EXEC_SUMMARY_HEIGHT);

            for (int j = startColumnIdx; j <= endColumnIndex; j++) {
                row.getCell(j, CREATE_NULL_AS_BLANK).setCellStyle(cellStyle);
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
                if (cellStyle != null) { cellStyle.setWrapText(true); }
            } else {
                cellMerge.setCellValue(mergedContent);
            }

            if (cellStyle != null) { cellMerge.setCellStyle(cellStyle); }

            Excel.adjustCellHeight(worksheet, cellMerge);
        }

        return cellMerge;
    }

    private void importSysProps() {
        if (this.executionLevel == EXECUTION) {
            Map<String, String> execRefs = new HashMap<>();
            collectAllScriptRefs(execRefs);

            // last one wins: sys props override previous script/plan referenceData
            execRefs.putAll(EnvUtils.getSysPropsByPrefix(SCRIPT_REF_PREFIX));

            // also override any execRefs of the same suffix - by design
            execRefs.putAll(EnvUtils.getSysPropsByPrefix(EXEC_REF_PREFIX));

            if (MapUtils.isNotEmpty(execRefs)) { referenceData.putAll(execRefs); }
        }
    }

    private void collectAllScriptRefs(Map<String, String> scriptRefs) {
        if (executionLevel == SCENARIO || executionLevel == ACTIVITY) { return; }

        // last one wins: last scriptRef override previous
        scriptRefs.putAll(referenceData);
        nestedExecutions.forEach(nested -> nested.collectAllScriptRefs(scriptRefs));
    }

    private ExecutionSummary summarized(ExecutionSummary source) {
        // check for "not started" tests due to preceding error
        if (source.startTime == 0 || source.endTime == 0 || source.totalSteps == 0 || source.executed == 0) {
            // nothing started on unknown time and nothing executed? this is useless
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
        summary.scriptFile = source.scriptFile;
        return summary;
    }
}
