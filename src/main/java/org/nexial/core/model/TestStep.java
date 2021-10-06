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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionInputPrep;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelStyleHelper;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.logs.ExecutionLogger;
import org.nexial.core.logs.TrackTimeLogs;
import org.nexial.core.plugins.CanTakeScreenshot;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.javaui.JavaUICommand;
import org.nexial.core.plugins.javaui.JavaUIProfile;
import org.nexial.core.plugins.web.WebDriverExceptionHelper;
import org.nexial.core.utils.*;
import org.nexial.core.variable.Syspath;
import org.openqa.selenium.WebDriverException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;
import static org.apache.poi.ss.usermodel.CellType.BLANK;
import static org.apache.tika.utils.SystemUtils.*;
import static org.nexial.commons.utils.EnvUtils.platformSpecificEOL;
import static org.nexial.core.CommandConst.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.FlowControls.CONDITION_DISABLE;
import static org.nexial.core.NexialConst.MSG_FAIL;
import static org.nexial.core.NexialConst.LogMessage.ERROR_LOG;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.excel.ExcelConfig.MSG_PASS;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.model.OnDemandInspectionDetector.getInstance;
import static org.nexial.core.utils.ExecUtils.isRunningInZeroTouchEnv;

public class TestStep extends TestStepManifest {
    protected ExecutionContext context;
    protected Worksheet worksheet;
    protected List<XSSFCell> row;
    protected TestCase testCase;
    protected List<NestedMessage> nestedTestResults;
    protected boolean isCommandRepeater;
    protected CommandRepeater commandRepeater;
    protected boolean hasErrorScreenshot;
    protected boolean isMacroExpander;
    protected MacroExecutor macroExecutor;
    protected boolean macroPartOfRepeatUntil;

    // added if macro file/sheet needed for particular testStep in future
    protected Macro macro;

    // support testing
    protected TestStep() { }

    public TestStep(TestCase testCase, List<XSSFCell> row, Worksheet worksheet) {
        assert testCase != null;
        assert CollectionUtils.isNotEmpty(row);

        this.testCase = testCase;
        this.row = row;
        this.worksheet = worksheet;
        this.context = testCase.getTestScenario().getContext();

        assert context != null && StringUtils.isNotBlank(context.getId());
        assert worksheet != null && worksheet.getFile() != null;

        int rowIndex = row.get(0).getRowIndex();
        setRowIndex(rowIndex);
        scriptRowIndex = rowIndex;
        readDescriptionCell(row);
        readTargetCell(row);
        readCommandCell(row);
        readParamCells(row);
        readFlowControlsCell(row);
        readCaptureScreenCell(row);

        setMessageId(String.format("[%s][%s][%s][STEP %s][%s][%s]",
                                   worksheet.getFile().getName(),
                                   worksheet.getName(),
                                   testCase.getName() + (macro == null ? "" : " (" + macro.getMacroName() + ")"),
                                   StringUtils.leftPad((getRowIndex() + 1) + "", 3),
                                   target,
                                   command));

        nestedTestResults = new ArrayList<>();
        setExternalProgram(StringUtils.containsAny(target, "external", "junit"));
        setLogToTestScript(isExternalProgram);
        isCommandRepeater = StringUtils.equals(target + "." + command, CMD_REPEAT_UNTIL);
        isMacroExpander = StringUtils.equalsAny(target + "." + command, CMD_MACRO, CMD_MACRO_FLEX);
        macroPartOfRepeatUntil = false;
    }

    public Worksheet getWorksheet() { return worksheet; }

    public List<XSSFCell> getRow() { return row; }

    public TestCase getTestCase() { return testCase; }

    public boolean isCommandRepeater() { return isCommandRepeater;}

    public List<NestedMessage> getNestedTestResults() { return nestedTestResults; }

    public String showPosition() { return messageId; }

    public ExecutionContext getContext() { return context; }

    public boolean isMacroExpander() { return isMacroExpander; }

    public MacroExecutor getMacroExecutor() { return macroExecutor; }

    public void setMacroExecutor(MacroExecutor macroExecutor) { this.macroExecutor = macroExecutor;}

    public Macro getMacro() { return macro; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SIMPLE_STYLE)
            .appendSuper(super.toString())
            .append("commandRepeater", commandRepeater)
            .toString();
    }

    public void addNestedMessage(String message) {
        if (MessageUtils.isPass(message) ||
            MessageUtils.isFail(message) ||
            MessageUtils.isWarn(message) ||
            MessageUtils.isSkipped(message)) { return; }
        if (worksheet == null) { return; }
        nestedTestResults.add(new NestedMessage(message));
    }

    public boolean hasErrorScreenshot() { return hasErrorScreenshot; }

    public void addErrorScreenCapture(String link, String message) {
        addNestedScreenCapture(link, message);
        hasErrorScreenshot = true;
    }

    public void addNestedScreenCapture(String link, String message) {
        nestedTestResults.add(new NestedScreenCapture(message, link));
    }

    public void addNestedScreenCapture(String link, String message, String label) {
        nestedTestResults.add(new NestedScreenCapture(message, link, label));
    }

    public void addStepOutput(String link, String label) { nestedTestResults.add(new StepOutput(label, link)); }

    /**
     * added semaphore-like marking as a strategy to avoid duplicating excel-logging when executing runClass().  When
     * commands are executed in a custom test class, some of the Nexial command will provide detailed logging where
     * the auto-discovery (via nexial.runClassAutoResult) excel logging is no longer necessary for the same command
     * executed. In such case, we want to skip the auto-discovery excel logging.  By marking a message as "logged",
     * the immediate subsequent message can then be skipped.  However, whether a message is skipped or not, the "logged"
     * flag will always be reset.
     */
    public void addNestedTestResult(String message) { nestedTestResults.add(new NestedTestResult(message)); }

    public StepResult execute() {
        TrackTimeLogs trackTimeLogs = context.getTrackTimeLogs();
        trackTimeLogs.checkStartTracking(context, this);

        // clock's ticking
        StopWatch tickTock = new StopWatch();
        tickTock.start();

        context.setCurrentTestStep(this);

        StepResult result = null;
        try {
            result = invokeCommand();
        } catch (Throwable e) {
            result = toFailedResult(e);
        } finally {
            tickTock.stop();
            trackTimeLogs.checkEndTracking(context, this);
            if (this.isCommandRepeater()) { context.setCurrentTestStep(this); }
            postExecCommand(result, tickTock.getTime());
            FlowControlUtils.checkPauseAfter(context, this);

            if (!isRunningInZeroTouchEnv() && getInstance(context).detectedPause()) {
                ConsoleUtils.pauseAndInspect(context, ">>>>> On-Demand Inspection detected...", true);
            }

            context.clearCurrentTestStep();
        }

        return result;
    }

    public CommandRepeater getCommandRepeater() { return commandRepeater;}

    public void setCommandRepeater(CommandRepeater commandRepeater) { this.commandRepeater = commandRepeater;}

    public String generateFilename(String ext) {
        String filename = generateFileName();

        List<NestedMessage> nestedTestResults = getNestedTestResults();
        if (CollectionUtils.isNotEmpty(nestedTestResults)) {
            int screenCount = 0;
            for (NestedMessage nested : nestedTestResults) {
                if (nested instanceof NestedScreenCapture) { screenCount++; }
            }
            if (screenCount > 0) { filename += "_" + screenCount; }
        }

        // make sure we don't have funky characters in the file name
        return filename + StringUtils.prependIfMissing(ext, ".");
    }

    private String generateFileName() {
        String repeatUntilIndex = context != null && context.hasData(REPEAT_UNTIL_LOOP_IN) ?
                                  "_" + StringUtils.leftPad(context.getStringData(REPEAT_UNTIL_LOOP_INDEX), 3, '0') :
                                  "";
        String stepIndex = row.get(0).getReference();
        Worksheet worksheet = testCase.getTestScenario().getWorksheet();
        if (context != null && context.hasData(MACRO_INVOKED_FROM)) {
            stepIndex = context.getStringData(MACRO_INVOKED_FROM) + "." + stepIndex;
        }

        return worksheet.getFile().getName() + "_" + worksheet.getName() + "_" + stepIndex + repeatUntilIndex +
               (isExternalProgram() ? "_" + getParams().get(0) : "");
    }

    public static List<String> readParamValues(List<XSSFCell> row) {
        XSSFCell cell;
        List<String> params = new ArrayList<>();

        // need to do a double pass to figure out the last cell with data so that we can accurately handle
        // the empty cell condition

        // FIRST PASS: find out the last cell with data
        int idxLastCell = COL_IDX_PARAMS_START;
        for (int i = COL_IDX_PARAMS_START; i <= COL_IDX_PARAMS_END; i++) {
            cell = row.get(i);
            if (cell != null && StringUtils.isNotBlank(cell.toString())) { idxLastCell = i + 1; }
        }

        // SECOND PASS: push data to params
        for (int i = COL_IDX_PARAMS_START; i < idxLastCell; i++) {
            params.add(treatCommonValueShorthand(Excel.getCellValue(row.get(i))));
        }
        return params;
    }

    public void close() {
        worksheet = null;

        if (CollectionUtils.isNotEmpty(row)) {
            row.clear();
            row = null;
        }

        if (CollectionUtils.isNotEmpty(nestedTestResults)) {
            nestedTestResults.clear();
            nestedTestResults = null;
        }

        if (commandRepeater != null) {
            commandRepeater.close();
            commandRepeater = null;
        }
    }

    protected StepResult toFailedResult(Throwable e) {
        context.setData(OPT_LAST_OUTCOME, false);

        // step 1: get truth! InvocationTargetException is masking real exception
        if (e instanceof InvocationTargetException && e.getCause() != null) { e = e.getCause(); }

        // step 2: print error to console
        String error;
        if (e instanceof WebDriverException) {
            error = WebDriverExceptionHelper.analyzeError(context, this, (WebDriverException) e);
        } else if (e instanceof ArrayIndexOutOfBoundsException) {
            error = "position/index not found: " + StringUtils.defaultString(e.getMessage(), e.toString());
        } else if (e instanceof NullPointerException) {
            error = "null pointer exception";
        } else {
            error = StringUtils.defaultString(e.getMessage(), e.toString());
            // minor formatting adjustment for better error output
            List<String> errorParts = RegexUtils.collectGroups(error, "^.+Exception\\:\\s*(.+)$");
            if (CollectionUtils.isNotEmpty(errorParts)) { error = errorParts.get(0); }
        }

        StepResult result = StepResult.fail(error);

        // print a lot or a little
        if (context.getBooleanData(OPT_PRINT_ERROR_DETAIL, getDefaultBool(OPT_PRINT_ERROR_DETAIL))) {
            ConsoleUtils.error(ExecutionLogger.toHeader(this), error, e);
        }

        // step 3: write error to file for RCA
        // determine the file to send log
        String logFqn = OutputFileUtils.generateErrorLog(this, e);
        if (StringUtils.isNotBlank(logFqn)) { result.setDetailedLogLink(logFqn); }

        // step 4: return back the error message for FAIL result instance
        return result;
    }

    protected void waitFor(long waitMs) {
        if (waitMs < 100) { return; }

        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            ConsoleUtils.log("sleep interrupted: " + e.getMessage());
        }
    }

    protected StepResult invokeCommand() throws InvocationTargetException, IllegalAccessException {
        String[] args = params.toArray(new String[0]);

        logCommand(args);

        FlowControlUtils.checkPauseBefore(context, this);

        boolean shouldExecute = true;

        // in case we want to skip this command
        StepResult result = FlowControlUtils.checkFailIf(context, this);
        if (result != null) { return result; }

        result = FlowControlUtils.checkEndLoopIf(context, this);
        if (result != null) { return result; }

        result = FlowControlUtils.checkEndIf(context, this);
        if (result != null) { return result; }

        result = FlowControlUtils.checkSkipIf(context, this);
        if (result != null) { shouldExecute = false; }

        StepResult result1 = FlowControlUtils.checkProceedIf(context, this);
        if (result1 != null) {
            shouldExecute = result1.isSuccess();
            result = result1;
        }

        if (shouldExecute) {
            NexialCommand plugin = context.findPlugin(target);
            if (plugin == null) { return StepResult.fail("Unknown/unsupported command target " + target); }
            if (plugin instanceof CanTakeScreenshot) { context.registerScreenshotAgent((CanTakeScreenshot) plugin); }

            // delay is carried out here so that timespan is captured as part of execution
            if (plugin instanceof JavaUICommand) {
                if (StringUtils.isNotBlank(plugin.getProfile()) &&
                    !StringUtils.equals(plugin.getProfile(), CMD_PROFILE_DEFAULT)) {
                    JavaUIProfile config = ((JavaUICommand) plugin).resolveConfig(plugin.getProfile());
                    waitFor(config.getDelayBetweenStepsMs());
                }
            } else {
                waitFor(context.getDelayBetweenStep());
            }

            try {
                result = plugin.execute(command, args);
            } finally {
                // support post-execution flow control
                if (result != null) {
                    context.setData(OPT_LAST_OUTCOME, result.isSuccess());
                    // for End... flow control, don't update result since it should not alter the execution result.
                    // result.update(FlowControlUtils.checkEndAfterIf(context, this));
                    // result.update(FlowControlUtils.checkEndLoopAfterIf(context, this));
                    FlowControlUtils.checkEndAfterIf(context, this);
                    FlowControlUtils.checkEndLoopAfterIf(context, this);
                    result.update(FlowControlUtils.checkFailAfterIf(context, this));
                }
            }
        }

        return result;
    }

    protected void logCommand(String[] args) {
        // log before pause (DO NOT use log() since that might trigger logToTestScript())
        ExecutionLogger logger = context.getLogger();

        String argText = Arrays.stream(args)
              .map(arg -> StringUtils.startsWith(arg, "$(execution") ? context.replaceTokens(arg, true) : arg)
              .collect(Collectors.joining(", "));

        // force console logging
        logger.log(this, "executing " + command + "(" + argText + ")", true);
    }

    public int formatSkippedSections(List<TestStep> testSteps, int i, boolean updateResult) {
        // `params.get(0)` represents the number of steps of this `section`
        int steps = Integer.parseInt(params.get(0));

        for (int j = 0; j < steps; j++) {
            int sectionStepIndex = i + j + 1;
            if (testSteps.size() > sectionStepIndex) {
                TestStep step = testSteps.get(sectionStepIndex);
                if (updateResult) { step.postExecCommand(StepResult.skipped(RB.Skipped.text("nestedSectionStep")), 0); }

                // reduce the number of steps for repeatUntil command
                if (step.isCommandRepeater()) {
                    int stepCount = Integer.parseInt(step.getParams().get(0));
                    if (step.macro != null) {
                        for (int k = 1; k <= stepCount; k++) {
                            TestStep innerStep = testSteps.get(sectionStepIndex + k);
                            if (StringUtils.equals(innerStep.getCommandFQN(), CMD_SECTION)) {
                                stepCount += Integer.parseInt(innerStep.getParams().get(0));
                            }
                        }

                        steps += stepCount;
                        j += stepCount;
                    } else {
                        int stepsToSkip = stepCount;
                        List<TestStep> innerSteps = step.commandRepeater.getSteps();
                        for (int k = 0; k < stepCount; k++) {
                            TestStep innerStep = innerSteps.get(k);
                            if (StringUtils.equals(innerStep.getCommandFQN(), CMD_SECTION)) {
                                stepsToSkip += Integer.parseInt(innerStep.getParams().get(0));
                            }
                        }

                        steps += stepsToSkip;
                        j += stepsToSkip;
                    }
                }

                // add the number of steps for section commands
                if (StringUtils.equals(step.getCommandFQN(), CMD_SECTION)) {
                    int stepCount = step.formatSkippedSections(testSteps, sectionStepIndex, updateResult);
                    steps += stepCount;
                    j += stepCount;
                }
            } else {
                steps = j - 1;
                break;
            }
        }
        return steps;
    }

    protected void trackExecutionError(StepResult result) {
        context.setData(OPT_LAST_ERROR, result.getMessage());
        if (context.getBooleanData(OPT_ERROR_TRACKER, getDefaultBool(OPT_ERROR_TRACKER))) {
            // 1. derive log file path
            String fullpath = StringUtils.appendIfMissing(new Syspath().log("fullpath"), separator) + ERROR_TRACKER;
            File logFile = new File(fullpath);

            // 2. append to log
            String log = "[" + DateUtility.formatLogDate(System.currentTimeMillis()) + "]" +
                         getMessageId() +
                         ("[" + (hasErrorScreenshot ? context.getStringData(OPT_LAST_SCREENSHOT_NAME) : "-") + "]") +
                         ("[" + StringUtils.defaultIfBlank(result.getDetailedLogLink(), "-") + "]") + ": " +
                         result.getMessage() + NL;
            try {
                FileUtils.writeStringToFile(logFile, log, DEF_FILE_ENCODING, true);
            } catch (IOException e) {
                ConsoleUtils.log("Unable to track errors to " + ERROR_TRACKER + ": " + e.getMessage());
            }

            // otc integration will occur at the end of execution
        }
    }

    protected void readDescriptionCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_DESCRIPTION);
        setDescription(cell != null ? StringUtils.defaultIfEmpty(cell.toString(), "") : "");
    }

    protected void readTargetCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_TARGET);
        if (cell == null || StringUtils.isBlank(cell.toString())) {
            throw new IllegalArgumentException(StringUtils.defaultString(messageId) + " no target specified" +
                                               (cell != null ? " on ROW " + (cell.getRowIndex() + 1) : ""));
        }
        setTarget(cell.toString());
    }

    protected void readCommandCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_COMMAND);
        if (cell == null || StringUtils.isBlank(cell.toString())) {
            throw new IllegalArgumentException(StringUtils.defaultString(messageId) + " no command specified" +
                                               (cell != null ? " on ROW " + (cell.getRowIndex() + 1) : ""));
        }
        setCommand(cell.toString());
    }

    protected void readParamCells(List<XSSFCell> row) {
        this.params = readParamValues(row);
        linkableParams = new ArrayList<>(this.params.size());
        for (int i = 0; i < this.params.size(); i++) { linkableParams.add(i, null); }
    }

    protected void postExecCommand(StepResult result, long elapsedMs) {
        // also include screenshot-on-error handling
        updateResult(result, elapsedMs);

        ExecutionSummary summary = testCase.getTestScenario().getExecutionSummary();
        if (result.isSkipped()) {
            summary.adjustTotalSteps(-1);
            log(MessageUtils.renderAsSkipped(result.getMessage()));
        } else if (!result.isEnded()) {
            summary.incrementExecuted();

            boolean lastOutcome = result.isSuccess();

            // if macro expanded, then consider last outcome from macro steps
            // "skipped" result already check in the above if-else block
            if (!isMacroExpander || macroExecutor == null) { context.setData(OPT_LAST_OUTCOME, lastOutcome); }

            if (lastOutcome) {
                summary.incrementPass();
                // avoid printing verbose() message to avoid leaking of sensitive information on log
                log(MessageUtils.renderAsPass(StringUtils.equals(getCommandFQN(), CMD_VERBOSE) ?
                                              "" : result.getMessage()));
            } else {
                summary.incrementFail();
                error(MessageUtils.renderAsFail(result.getMessage()));
                if (StringUtils.isNotBlank(result.getDetailedLogLink())) {
                    context.getLogger().error(this, ERROR_LOG + result.getDetailedLogLink());
                }
                trackExecutionError(result);
            }
        }

        context.evaluateResult(result);
    }

    protected void readCaptureScreenCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_CAPTURE_SCREEN);
        setCaptureScreen(StringUtils.startsWithIgnoreCase(Excel.getCellValue(cell), "X"));
    }

    protected String handleScreenshot(StepResult result) {
        if (result == null || result.isSkipped()) { return null; }

        // no screenshot specified and no error found (success or skipped)
        if (!captureScreen && result.isSuccess()) { return null; }

        // no screenshot specified and screenshot-on-error is turned off
        if (!captureScreen && !context.isScreenshotOnError()) { return null; }

        // screenshot agent is registered at the start of a test step (only web or desktop command would qualify)
        CanTakeScreenshot agent = context.findCurrentScreenshotAgent();
        if (agent == null) { return null; }

        // screenshot failure shouldn't cause exception to offset execution pass/fail percentage
        try {
            String screenshotPath = agent.takeScreenshot(this);
            if (StringUtils.isBlank(screenshotPath)) {
                error("Unable to capture screenshot - " + result.getMessage());
                return null;
            }

            if (result.failed()) {
                addErrorScreenCapture(screenshotPath, result.getMessage());
            } else {
                addStepOutput(screenshotPath, MSG_SCREENCAPTURE);
            }

            return screenshotPath;
        } catch (Exception e) {
            error("Unable to capture screenshot: " + e.getMessage());
            return null;
        }
    }

    protected void updateResult(StepResult result, long elapsedMs) {
        boolean isSkipped = result.isSkipped();
        boolean isEnded = result.isEnded();

        // screenshot
        // take care of screenshot requirement first...
        // in interactive mode, we won't proceed if any of the Excel-related operations
        if (!isSkipped || !isEnded) {
            // don't capture screenshot if step skipped or ended by endIf
            handleScreenshot(result);
        }

        if (!context.isInteractiveMode()) {
            ExcelStyleHelper.formatActivityCell(worksheet, row.get(COL_IDX_TESTCASE));

            // description
            XSSFCell cellDescription = row.get(COL_IDX_DESCRIPTION);
            String description = Excel.getCellValue(cellDescription);
            if (StringUtils.startsWith(description, SECTION_DESCRIPTION_PREFIX)) {
                ExcelStyleHelper.formatSectionDescription(worksheet, cellDescription);
            } else if (StringUtils.contains(description, REPEAT_DESCRIPTION_PREFIX)) {
                ExcelStyleHelper.formatRepeatUntilDescription(worksheet, cellDescription);
            } else {
                ExcelStyleHelper.formatDescription(worksheet, cellDescription);
            }

            ExcelStyleHelper.formatTargetCell(worksheet, row.get(COL_IDX_TARGET));
            ExcelStyleHelper.formatCommandCell(worksheet, row.get(COL_IDX_COMMAND));

            String commandName = row.get(COL_IDX_TARGET).toString() + "." + row.get(COL_IDX_COMMAND).toString();
            String message = result.getMessage();

            if (isSkipped) {
                XSSFCellStyle styleSkipped = worksheet.getStyle(STYLE_PARAM_SKIPPED);
                for (int i = COL_IDX_PARAMS_START; i <= COL_IDX_PARAMS_END; i++) {
                    row.get(i).setCellStyle(styleSkipped);
                }
            } else if (!isEnded) {
                cellDescription.setCellValue(context.containsCrypt(description) ?
                                             CellTextReader.readValue(description) :
                                             context.replaceTokens(description, true));

                XSSFCellStyle styleParam = worksheet.getStyle(STYLE_PARAM);
                XSSFCellStyle styleTaintedParam = worksheet.getStyle(STYLE_TAINTED_PARAM);

                // merging resolved parameter value (what's evaluated) and parameter template (what's written)
                List<String> mergedParams = params == null ? new ArrayList<>() : new ArrayList<>(params);
                Object[] paramValues = result.getParamValues();
                if (paramValues != null) {
                    for (int i = 0; i < mergedParams.size(); i++) {
                        if (paramValues.length > i) { mergedParams.set(i, Objects.toString(paramValues[i], "")); }
                    }

                    if (paramValues.length > mergedParams.size()) {
                        int startFrom = mergedParams.size();
                        for (int i = startFrom; i < paramValues.length; i++) {
                            mergedParams.add(Objects.toString(paramValues[i], ""));
                        }
                    }
                }

                if (linkableParams == null) {
                    linkableParams = new ArrayList<>(mergedParams.size());
                    for (int i = 0; i < mergedParams.size(); i++) { linkableParams.add(i, null); }
                } else if (linkableParams.size() < mergedParams.size()) {
                    int startFrom = linkableParams.size();
                    for (int i = startFrom; i < mergedParams.size(); i++) { linkableParams.add(i, null); }
                }

                // update the params that can be expressed as links (file or url)
                for (int i = COL_IDX_PARAMS_START; i <= COL_IDX_PARAMS_END; i++) {
                    int paramIdx = i - COL_IDX_PARAMS_START;
                    if (mergedParams.size() <= paramIdx) { break; }
                    XSSFCell paramCell = row.get(i);

                    String param = mergedParams.get(paramIdx);
                    if (StringUtils.isBlank(param)) { continue; }

                    String link = resolveParamAsLink(param);
                    if (link != null) {
                        // create hyperlink where path is referenced
                        linkableParams.set(paramIdx, link);

                        if (isURL(link)) {
                            worksheet.setHyperlink(paramCell, link, param);
                            continue;
                        }

                        // support output to cloud:
                        // - if link is local resource but output-to-cloud is enabled, then copy resource to cloud and update link
                        if (context.isOutputToCloud()) {
                            // create new local resource with name matching to current row, so that duplicate use of the
                            // same name will not result in overriding cloud resource

                            if (FileUtil.isFileReadable(link, 1)) {
                                // target file should exist with at least 1 byte
                                File tmpFile = new File(StringUtils.substringBeforeLast(link, separator) + separator +
                                                        row.get(COL_IDX_TESTCASE).getReference() + "_" +
                                                        StringUtils.substringAfterLast(link, separator));

                                try {
                                    // ConsoleUtils.log("copy local resource " + link + " to " + tmpFile);
                                    FileUtils.copyFile(new File(link), tmpFile);

                                    ConsoleUtils.log("output-to-cloud enabled; copying " + link + " cloud...");
                                    String cloudUrl = context.getOtc().importFile(tmpFile, true);
                                    context.setData(OPT_LAST_OUTPUT_LINK, cloudUrl);
                                    context.setData(OPT_LAST_OUTPUT_PATH, StringUtils.substringBeforeLast(cloudUrl, "/"));
                                    ConsoleUtils.log("output-to-cloud enabled; copied  " + link + " to " + cloudUrl);

                                    worksheet.setHyperlink(paramCell, cloudUrl, "(cloud) " + param);
                                    continue;
                                } catch (IOException e) {
                                    ConsoleUtils.log("Unable to copy resource to cloud: " + e.getMessage());
                                    log(toCloudIntegrationNotReadyMessage(link + ": " + e.getMessage()));
                                }
                            } else {
                                ConsoleUtils.log(
                                    "output-to-cloud enabled; but file " + link + " is empty or unreadable");
                            }
                        }

                        // if `link` contains double quote, it's likely not a link..
                        if (!StringUtils.containsAny(link, "\"")) { worksheet.setHyperlink(paramCell, link, param); }
                        continue;
                    } else {
                        paramCell.setCellValue(context.truncateForDisplay(param));
                        paramCell.setCellStyle(styleParam);
                    }

                    String origParamValue = Excel.getCellValue(paramCell);
                    if (StringUtils.isBlank(origParamValue)) {
                        paramCell.setCellType(BLANK);
                        continue;
                    }

                    if (i == COL_IDX_PARAMS_START && StringUtils.equals(getCommandFQN(), CMD_VERBOSE)) {
                        if (context.containsCrypt(origParamValue)) {
                            paramCell.setCellComment(toSystemComment(paramCell, "detected crypto"));
                        } else {
                            message = StringUtils.trim(platformSpecificEOL(message));
                            if (StringUtils.length(message) > MAX_VERBOSE_CHAR) {
                                message = StringUtils.abbreviate(message, MAX_VERBOSE_CHAR);
                            }
                            paramCell.setCellValue(message);
                            paramCell.setCellComment(toSystemComment(paramCell, origParamValue));
                        }
                        continue;
                    }

                    // respect the crypts... if value has crypt:, then keep it as is
                    if (context.containsCrypt(origParamValue)) {
                        paramCell.setCellComment(toSystemComment(paramCell, "detected crypto"));
                        continue;
                    }

                    String taintedValue = CellTextReader.getOriginal(origParamValue, param);
                    boolean tainted = !StringUtils.equals(origParamValue, taintedValue);
                    if (tainted) {
                        paramCell.setCellValue(context.truncateForDisplay(taintedValue));
                        if (StringUtils.isNotEmpty(origParamValue)) {
                            paramCell.setCellComment(toSystemComment(paramCell, origParamValue));
                        }
                        paramCell.setCellStyle(styleTaintedParam);
                    } else {
                        paramCell.setCellStyle(styleParam);
                    }

                    ExcelStyleHelper.handleTextWrap(paramCell);
                }
            }

            // flow control
            ExcelStyleHelper.formatFlowControlCell(worksheet, row.get(COL_IDX_FLOW_CONTROLS));

            // elapsed time
            if (!isSkipped || !isEnded) {
                row.get(COL_IDX_ELAPSED_MS).setCellStyle(worksheet.getStyle(STYLE_ELAPSED_MS));
            }

            boolean pass = result.isSuccess();

            if ((!isSkipped && !isEnded) &&
                !SLA_EXEMPT_COMMANDS.contains(commandName) &&
                !StringUtils.contains(commandName, ".wait")) {
                // SLA not applicable to composite commands and all wait* commands
                long elapsedTimeSLA = context.getSLAElapsedTimeMs();
                if (!updateElapsedTime(elapsedMs, elapsedTimeSLA > 0 && elapsedTimeSLA < elapsedMs)) {
                    result.markElapsedTimeSlaNotMet();
                    pass = false;
                    message = result.getMessage();
                }
            }

            createCommentForMacro(cellDescription, pass);

            // result
            XSSFCell cellResult = row.get(COL_IDX_RESULT);
            String resultMsg =
                MESSAGE_REQUIRED_COMMANDS.contains(commandName) && (result.isSuccess() || result.isError()) ?
                (result.isSuccess() ? MSG_PASS : MSG_FAIL) + message :
                MessageUtils.markResult(message, pass, true);
            cellResult.setCellValue(StringUtils.left(resultMsg, MAX_VERBOSE_CHAR));

            if (result.isError()) {
                ExcelStyleHelper.formatFailedStepDescription(this);
                Excel.createComment(cellDescription, cellResult.getStringCellValue(), COMMENT_AUTHOR);
            }

            if (isSkipped) {
                // paint both result and description the same style to improve readability
                cellResult.setCellStyle(worksheet.getStyle(STYLE_SKIPPED_RESULT));
                cellDescription.setCellStyle(worksheet.getStyle(STYLE_SKIPPED_RESULT));
                Excel.createComment(cellDescription, message, COMMENT_AUTHOR);
            } else if (isEnded) {
                // paint both result and description the same style to improve readability
                cellResult.setCellStyle(ExcelStyleHelper.generate(worksheet, TERMINATED));
                cellDescription.setCellStyle(ExcelStyleHelper.generate(worksheet, TERMINATED));
                Excel.createComment(cellDescription, message, COMMENT_AUTHOR);
            } else {
                cellResult.setCellStyle(worksheet.getStyle(pass ? STYLE_SUCCESS_RESULT : STYLE_FAILED_RESULT));
            }

            // reason
            XSSFCell cellReason = row.get(COL_IDX_REASON);
            if (cellReason != null && !pass) {
                if (StringUtils.isNotBlank(result.getDetailedLogLink())) {
                    // currently support just 1 log link
                    String logLink = result.getDetailedLogLink();
                    if (StringUtils.isNotBlank(logLink)) {
                        Excel.setHyperlink(cellReason, logLink, "details");
                        ConsoleUtils.error("Check corresponding error log for details: " + logLink);
                    }
                } else {
                    Throwable exception = result.getException();
                    if (exception != null) {
                        Throwable rootCause = ExceptionUtils.getRootCause(exception);
                        cellReason.setCellValue(context.truncateForDisplay(rootCause == null ?
                                                                           exception.getMessage() :
                                                                           rootCause.getMessage()));
                        cellReason.setCellStyle(worksheet.getStyle(STYLE_MESSAGE));
                    }
                }
            }
        }

        if (CollectionUtils.isNotEmpty(nestedTestResults)) {
            TestStepManifest testStep = toTestStepManifest();
            testCase.getTestScenario().getExecutionSummary().addNestedMessages(testStep, nestedTestResults);
        }
    }

    @Nullable
    private String resolveParamAsLink(String param) {
        // could be literal syspath - e.g. $(syspath|out|fullpath)/...
        // could be data variable that reference syspath function
        // OR
        // param is a data variable... so it might be referencing a syspath function
        String link = param;
        if (isFileLink(param) || TextUtils.isBetween(param, TOKEN_START, TOKEN_END)) {
            link = context.replaceTokens(param);
        }

        if (!FileUtil.isSuitableAsPath(link)) { return null; }

        if (isURL(link)) { return link; }

        if (IS_OS_WINDOWS) {
            link = StringUtils.replace(link, "/", "\\");
            if (RegexUtils.isExact(link, "^[A-Za-z]\\:\\\\.+$") ||
                RegexUtils.isExact(link, "^\\\\\\\\[0-9A-Za-z_\\.\\-]+\\\\.+$")) {
                return link;
            } else {
                return null;
            }
        }

        if (IS_OS_MAC || IS_OS_LINUX) { return RegexUtils.isExact(link, "/.+") ? link : null; }

        return OutputResolver.isContentReferencedAsFile(link) ? link : null;
    }

    protected boolean updateElapsedTime(long elapsedTime, boolean violateSLA) {
        XSSFCell cellElapsedTime = row.get(COL_IDX_ELAPSED_MS);
        cellElapsedTime.setCellValue(elapsedTime);
        context.setData(OPT_LAST_ELAPSED_TIME, elapsedTime);
        if (violateSLA) {
            cellElapsedTime.setCellStyle(worksheet.getStyle(STYLE_ELAPSED_MS_BAD_SLA));
            return false;
        }

        return true;
    }

    protected void log(String message) {
        if (StringUtils.isBlank(message)) { return; }
        context.getLogger().log(this, message);
        logToTestScript(message);
    }

    protected void error(String message) {
        if (StringUtils.isBlank(message)) { return; }
        context.getLogger().error(this, message);
        logToTestScript(message);
    }

    protected void logToTestScript(String message) {
        if (!isLogToTestScript()) { return; }

        if (!MessageUtils.isTestResult(message)) {
            addNestedMessage(message);
            return;
        }

        if (!context.isScreenshotOnError() || !MessageUtils.isFail(message)) { addNestedMessage(message); }

        // screenshot-on-error already handled in `postExecCommand()` method; no need to capture error screenshot here
    }

    protected TestStepManifest toTestStepManifest() {
        TestStepManifest lwTestStep = new TestStepManifest();
        lwTestStep.setMessageId(getMessageId());
        lwTestStep.setDescription(getDescription());
        lwTestStep.setTarget(getTarget());
        lwTestStep.setCommand(getCommand());
        lwTestStep.setParams(getParams());
        lwTestStep.setLinkableParams(getLinkableParams());
        lwTestStep.setFlowControls(getFlowControls());
        lwTestStep.setCaptureScreen(isCaptureScreen());
        lwTestStep.setLogToTestScript(isLogToTestScript());
        lwTestStep.setRowIndex(row.get(0).getRowIndex());
        if (scriptRowIndex != 0) { lwTestStep.scriptRowIndex = scriptRowIndex; }
        return lwTestStep;
    }

    private boolean isFileLink(String param) {
        return !StringUtils.startsWith(param, "[") &&
               StringUtils.countMatches(param, TOKEN_FUNCTION_START + "syspath|") == 1 &&
               StringUtils.countMatches(param, "|name" + TOKEN_FUNCTION_END) == 0;
    }

    private boolean isURL(String link) {
        return StringUtils.startsWithIgnoreCase(link, "http") ||
               StringUtils.startsWithIgnoreCase(link, "file:");
    }

    private Comment toSystemComment(XSSFCell paramCell, String message) {
        return Excel.createComment(paramCell, toSystemComment(message), COMMENT_AUTHOR);
    }

    private String toSystemComment(String message) { return "test script:" + lineSeparator() + message; }

    // add macro name as a comment for description of section command
    protected void createCommentForMacro(XSSFCell cellDescription, boolean pass) {
        if (pass && isMacroExpander && macroExecutor != null) {
            Macro macro = macroExecutor.getMacro();
            String comment = "imported from: " + lineSeparator() +
                             "[FROM]: ROW #" + (rowIndex + 1) + lineSeparator() +
                             "[FILE]: " + macro.getFile() + lineSeparator() +
                             "[SHEET] :" + macro.getSheet() + lineSeparator() +
                             "[NAME] :" + macro.getMacroName();
            Excel.createComment(cellDescription, comment, COMMENT_AUTHOR);
        }
    }

    protected void readFlowControlsCell(List<XSSFCell> row) {
        addSkipFlowControl();
        XSSFCell cell = row.get(COL_IDX_FLOW_CONTROLS);
        setFlowControls(FlowControl.parse(cell != null ? StringUtils.defaultString(Excel.getCellValue(cell), "") : ""));
    }

    private void addSkipFlowControl() {
        if (!ExecutionInputPrep.isTestStepDisabled(row)) { return; }

        setFlowControls(FlowControl.parse("SkipIf(true)"));

        // add SkipIf(true) condition to flow control cell
        XSSFCell cellFlowControls = row.get(COL_IDX_FLOW_CONTROLS);
        cellFlowControls.setCellValue(CONDITION_DISABLE);

        // (2018/12/28,automike): we MUST NOT prepend or append since other flow control may supersede the flow
        // control we are adding here to "disable" a command
        // String currentFlowControls = Excel.getCellValue(cellFlowControls);
        // currentFlowControls = StringUtils.prependIfMissing(currentFlowControls, CONDITION_DISABLE);
        // cellFlowControls.setCellValue(currentFlowControls);
    }
}