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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelStyleHelper;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.plugins.CanTakeScreenshot;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.web.WebDriverExceptionHelper;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecutionLogger;
import org.nexial.core.utils.FlowControlUtils;
import org.nexial.core.utils.MessageUtils;
import org.nexial.core.utils.TrackTimeLogs;
import org.openqa.selenium.WebDriverException;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE;
import static org.nexial.commons.utils.EnvUtils.platformSpecificEOL;
import static org.nexial.core.CommandConst.CMD_REPEAT_UNTIL;
import static org.nexial.core.CommandConst.CMD_VERBOSE;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.excel.ExcelConfig.*;

public class TestStep extends TestStepManifest {
    protected ExecutionContext context;
    protected Worksheet worksheet;
    protected List<XSSFCell> row;
    protected TestCase testCase;
    protected List<NestedMessage> nestedTestResults;
    protected boolean isCommandRepeater;
    protected CommandRepeater commandRepeater;

    // support testing
    protected TestStep() { }

    public TestStep(TestCase testCase, List<XSSFCell> row) {
        assert testCase != null;
        assert CollectionUtils.isNotEmpty(row);

        this.testCase = testCase;
        this.row = row;
        this.worksheet = testCase.getTestScenario().getWorksheet();
        this.context = testCase.getTestScenario().getContext();

        assert context != null && StringUtils.isNotBlank(context.getId());
        assert worksheet != null && worksheet.getFile() != null;

        setRowIndex(row.get(0).getRowIndex());
        readDescriptionCell(row);
        readTargetCell(row);
        readCommandCell(row);
        readParamCells(row);
        readFlowControlsCell(row);
        readCaptureScreenCell(row);

        setMessageId("[" + worksheet.getFile().getName() + "]" +
                     "[" + worksheet.getName() + "][" + testCase.getName() + "]" +
                     "[ROW " + StringUtils.leftPad((getRowIndex() + 1) + "", 3) + "]" +
                     "[" + target + "][" + command + "]");

        nestedTestResults = new ArrayList<>();
        setExternalProgram(StringUtils.containsAny(target, "external", "junit"));
        setLogToTestScript(isExternalProgram);
        isCommandRepeater = StringUtils.equals(target + "." + command, CMD_REPEAT_UNTIL);
    }

    public Worksheet getWorksheet() { return worksheet; }

    public List<XSSFCell> getRow() { return row; }

    public TestCase getTestCase() { return testCase; }

    public boolean isCommandRepeater() { return isCommandRepeater;}

    public List<NestedMessage> getNestedTestResults() { return nestedTestResults; }

    public String showPosition() { return messageId; }

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

    public void addNestedScreenCapture(String link, String message) {
        nestedTestResults.add(new NestedScreenCapture(message, link));
    }

    public void addNestedScreenCapture(String link, String message, String label) {
        nestedTestResults.add(new NestedScreenCapture(message, link, label));
    }

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
        boolean printStackTrace = context.getBooleanData(OPT_PRINT_ERROR_DETAIL,
                                                         getDefaultBool(OPT_PRINT_ERROR_DETAIL));

        // delay is carried out here so that timespan is captured as part of execution
        waitFor(context.getDelayBetweenStep());

        StepResult result = null;
        try {
            result = invokeCommand();
        } catch (InvocationTargetException e) {
            String error = e.getMessage();
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof WebDriverException) {
                    error = WebDriverExceptionHelper.analyzeError(context, this, (WebDriverException) cause);
                    result = StepResult.fail(error);
                    return result;
                } else if (cause instanceof ArrayIndexOutOfBoundsException) {
                    error = "position/index not found: " +
                            StringUtils.defaultString(cause.getMessage(), cause.toString());
                } else {
                    // assertion error are already account for.. so no need to increment fail test count
                    // if (!(cause instanceof AssertionError)) { ConsoleUtils.error(cause.getMessage()); }
                    error = StringUtils.defaultString(cause.getMessage(), cause.toString());
                }
            }

            if (printStackTrace) { ConsoleUtils.error(ExecutionLogger.toHeader(this), error, e); }

            result = StepResult.fail(error);
        } catch (WebDriverException e) {
            String error = WebDriverExceptionHelper.analyzeError(context, this, e);
            // logger.error(this, error);
            result = StepResult.fail(error);
        } catch (Throwable e) {
            String error = e.getMessage();
            if (printStackTrace) {
                ConsoleUtils.error(ExecutionLogger.toHeader(this), error, e);
            } else {
                ConsoleUtils.error(error);
            }
            result = StepResult.fail(error);
        } finally {
            tickTock.stop();
            trackTimeLogs.checkEndTracking(context, this);
            if (this.isCommandRepeater()) { context.setCurrentTestStep(this); }
            postExecCommand(result, tickTock.getTime());
            FlowControlUtils.checkPauseAfter(context, this);
            context.clearCurrentTestStep();
        }

        return result;
    }

    public CommandRepeater getCommandRepeater() { return commandRepeater;}

    public void setCommandRepeater(CommandRepeater commandRepeater) { this.commandRepeater = commandRepeater;}

    public String generateFilename(String ext) {
        String filename = worksheet.getFile().getName() + "_"
                          + worksheet.getName() + "_"
                          // + getTestCase().getName() + "_"
                          + getRow().get(0).getReference()
                          + (isExternalProgram() ? "_" + getParams().get(0) : "");

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
            cell = row.get(i);
            String cellValue = treatCommonValueShorthand(cell.toString());
            params.add(cellValue);
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

            // todo: resolve soon. we need to log to excel when running external programs
            // if (isExternalProgram) {
            //	 start excel logging
            //context.setCurrentCommand(testStep);
            //context.setLogToExcel(true);
            // }

            if (plugin instanceof CanTakeScreenshot) { context.registerScreenshotAgent((CanTakeScreenshot) plugin); }
            result = plugin.execute(command, args);

            // todo: resolve soon. we need to stop logging to excel when not running external program
            // if (isExternalProgram) {
            //	// stop excel logging
            //	context.setCurrentCommand(null);
            //	context.setLogToExcel(false);
            //	MDC.put(NexialConst.TEST_SUITE_NAME, context.name);
            //	MDC.put(NexialConst.TEST_NAME, context.name);
            // }
        }

        return result;
    }

    protected void logCommand(String[] args) {
        // log before pause (DO NOT use log() since that might trigger logToTestScript())
        ExecutionLogger logger = context.getLogger();
        StringBuilder argText = new StringBuilder();
        for (String arg : args) {
            argText.append(StringUtils.startsWith(arg, "$(execution") ? context.replaceTokens(arg, true) : arg)
                   .append(", ");
        }

        // force console logging
        logger.log(this, "executing " + command + "(" + StringUtils.removeEnd(argText.toString(), ", ") + ")", true);
    }

    protected void postExecCommand(StepResult result, long elapsedMs) {
        updateResult(result, elapsedMs);

        ExecutionSummary summary = testCase.getTestScenario().getExecutionSummary();
        if (result.isSkipped()) {
            summary.adjustTotalSteps(-1);
            log(MessageUtils.renderAsSkipped(result.getMessage()));
        } else {
            summary.incrementExecuted();

            boolean lastOutcome = result.isSuccess();
            context.setData(OPT_LAST_OUTCOME, lastOutcome);

            if (lastOutcome) {
                summary.incrementPass();
                // avoid printing verbose() message to avoid leaking of sensitive information on log
                log(MessageUtils.renderAsPass(StringUtils.equals(getCommandFQN(), CMD_VERBOSE) ?
                                              "" : result.getMessage()));
            } else {
                summary.incrementFail();
                error(MessageUtils.renderAsFail(result.getMessage()));
            }
        }

        context.evaluateResult(result);
    }

    protected void readDescriptionCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_DESCRIPTION);
        setDescription(cell != null ? StringUtils.defaultIfEmpty(cell.toString(), "") : "");
    }

    protected void readTargetCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_TARGET);
        if (cell == null || StringUtils.isBlank(cell.toString())) {
            throw new IllegalArgumentException(StringUtils.defaultString(messageId) + " no target specified" +
                                               (cell != null ? " on ROW " + cell.getRowIndex() : ""));
        }
        setTarget(cell.toString());
    }

    protected void readCommandCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_COMMAND);
        if (cell == null || StringUtils.isBlank(cell.toString())) {
            throw new IllegalArgumentException(StringUtils.defaultString(messageId) + " no command specified" +
                                               (cell != null ? " on ROW " + cell.getRowIndex() : ""));
        }
        setCommand(cell.toString());
    }

    protected void readParamCells(List<XSSFCell> row) {
        this.params = readParamValues(row);
        linkableParams = new ArrayList<>(this.params.size());
        for (int i = 0; i < this.params.size(); i++) { linkableParams.add(i, null); }
    }

    protected void readFlowControlsCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_FLOW_CONTROLS);
        setFlowControls(FlowControl.parse(cell != null ? StringUtils.defaultString(cell.toString(), "") : ""));
    }

    protected void readCaptureScreenCell(List<XSSFCell> row) {
        XSSFCell cell = row.get(COL_IDX_CAPTURE_SCREEN);
        setCaptureScreen(StringUtils.startsWithIgnoreCase(Excel.getCellValue(cell), "X"));
    }

    protected String handleScreenshot(StepResult result) {
        if (result == null) { return null; }

        // no screenshot specified and no error found (success or skipped)
        if (!captureScreen && (result.isSuccess() || result.isSkipped())) { return null; }

        // no screenshot specified and screenshot-on-error is turned off
        if (!captureScreen && !context.isScreenshotOnError()) { return null; }

        CanTakeScreenshot agent = context.findCurrentScreenshotAgent();
        if (agent == null) {
            log("No screenshot capability available for command " + getCommandFQN() + "; no screenshot taken");
            return null;
        }

        // screenshot failure shouldn't cause exception to offset execution pass/fail percentage
        try {
            String screenshotPath = agent.takeScreenshot(this);
            if (StringUtils.isBlank(screenshotPath)) {
                log("Unable to capture screenshot - " + result.getMessage());
                return null;
            }

            return screenshotPath;
        } catch (Exception e) {
            ConsoleUtils.error("Unable to capture screenshot: " + e.getMessage());
            return null;
        }
    }

    protected void updateResult(StepResult result, long elapsedMs) {
        String message = result.getMessage();

        ExcelStyleHelper.formatActivityCell(worksheet, row.get(COL_IDX_TESTCASE));
        ExcelStyleHelper.formatTargetCell(worksheet, row.get(COL_IDX_TARGET));
        ExcelStyleHelper.formatCommandCell(worksheet, row.get(COL_IDX_COMMAND));

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
        cellDescription.setCellValue(context.containsCrypt(description) ?
                                     CellTextReader.readValue(description) : context.replaceTokens(description, true));

        XSSFCellStyle styleTaintedParam = worksheet.getStyle(STYLE_TAINTED_PARAM);
        XSSFCellStyle styleParam = worksheet.getStyle(STYLE_PARAM);

        // update the params that can be expressed as links (file or url)
        for (int i = 0; i < params.size(); i++) {
            String param = params.get(i);
            if (StringUtils.isBlank(param)) { continue; }

            // could be literal syspath - e.g. $(syspath|out|fullpath)/...
            // could be data variable that reference syspath function
            boolean hasPath = isFileLink(param);
            if (!hasPath && TextUtils.isBetween(param, TOKEN_START, TOKEN_END)) {
                // param is a data variable... so it might be referencing a syspath function
                Object pathObj = context.getObjectData(StringUtils.substringBetween(param, TOKEN_START, TOKEN_END));
                if (pathObj != null && isFileLink(pathObj.toString())) { hasPath = true; }
            }

            // create hyperlink for syspath when path is referenced
            if (hasPath) {
                String value = context.replaceTokens(param);
                // gotta make sure it's a file/path
                if (FileUtil.isSuitableAsPath(value) && StringUtils.containsAny(value, "\\/")) {
                    linkableParams.set(i, value);
                }
            }
        }

        Object[] paramValues = result.getParamValues();

        // handle linkable params (first priority), verbose (second priority) and params (last)
        for (int i = COL_IDX_PARAMS_START; i < COL_IDX_PARAMS_END; i++) {
            int paramIdx = i - COL_IDX_PARAMS_START;

            String link = CollectionUtils.size(linkableParams) > paramIdx ? linkableParams.get(paramIdx) : null;
            XSSFCell paramCell = row.get(i);
            if (StringUtils.isNotBlank(link)) {
                // support output to cloud:
                // - if link is local resource but output-to-cloud is enabled, then copy resource to cloud and update link
                boolean linkToCloud = false;
                if (context.isOutputToCloud() &&
                    !StringUtils.startsWithIgnoreCase(link, "http") &&
                    !StringUtils.startsWithIgnoreCase(link, "file:")) {
                    // create new local resource with name matching to current row, so that duplicate use of the
                    // same name will not result in overriding cloud resource

                    if (FileUtil.isFileReadable(link, 1)) {
                        // target file should exist with at least 1 byte
                        File tmpFile = new File(StringUtils.substringBeforeLast(link, separator) + separator +
                                                row.get(COL_IDX_TESTCASE).getReference() + "_" +
                                                StringUtils.substringAfterLast(link, separator));

                        try {
                            ConsoleUtils.log("copy local resource " + link + " to " + tmpFile);
                            FileUtils.copyFile(new File(link), tmpFile);
                            String cloudUrl = context.getOtc().importFile(tmpFile, true);
                            ConsoleUtils.log("output-to-cloud enabled; " + link + " copied to cloud " + cloudUrl);
                            worksheet.setHyperlink(paramCell, cloudUrl, "(cloud) " + params.get(paramIdx));
                            linkToCloud = true;
                        } catch (IOException e) {
                            ConsoleUtils.log("Unable to copy resource to cloud: " + e.getMessage());
                            log(toCloudIntegrationNotReadyMessage(link + ": " + e.getMessage()));
                        }
                    } else {
                        ConsoleUtils.log("output-to-cloud enabled; but file " + link + " is empty or unreadable");
                    }
                }

                // if `link` contains double quote, it's likely not a link..
                if (!linkToCloud && !StringUtils.containsAny(link, "\"")) {
                    worksheet.setHyperlink(paramCell, link, context.replaceTokens(params.get(paramIdx)));
                }
                continue;
            }

            String origParamValue = Excel.getCellValue(paramCell);
            if (StringUtils.isBlank(origParamValue)) { continue; }

            if (i == COL_IDX_PARAMS_START && StringUtils.equals(getCommandFQN(), CMD_VERBOSE)) {
                if (context.containsCrypt(origParamValue)) {
                    paramCell.setCellComment(toSystemComment(paramCell, "detected crypto"));
                } else {
                    if (StringUtils.length(message) > MAX_VERBOSE_CHAR) {
                        message = StringUtils.truncate(message, MAX_VERBOSE_CHAR) + "…";
                    }
                    paramCell.setCellValue(platformSpecificEOL(message));
                    paramCell.setCellComment(toSystemComment(paramCell, origParamValue));
                }

                ExcelStyleHelper.handleTextWrap(paramCell);
                continue;
            }

            Object value = ArrayUtils.getLength(paramValues) > paramIdx ? paramValues[paramIdx] : null;
            if (value != null) {
                // respect the crypts... if value has crypt:, then keep it as is
                if (context.containsCrypt(origParamValue)) {
                    paramCell.setCellComment(toSystemComment(paramCell, "detected crypto"));
                    continue;
                }

                String taintedValue = CellTextReader.getOriginal(origParamValue, Objects.toString(value));
                boolean tainted = !StringUtils.equals(origParamValue, taintedValue);
                if (tainted) {
                    paramCell.setCellValue(StringUtils.abbreviate(taintedValue, MAX_VERBOSE_CHAR));
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

        // screenshot
        String screenshotLink = handleScreenshot(result);
        if (StringUtils.isNotBlank(screenshotLink)) {
            worksheet.setScreenCaptureStyle(row.get(COL_IDX_CAPTURE_SCREEN), screenshotLink);
        }

        boolean pass = result.isSuccess();

        // elapsed time
        row.get(COL_IDX_ELAPSED_MS).setCellStyle(worksheet.getStyle(STYLE_ELAPSED_MS));
        long elapsedTimeSLA = context.getSLAElapsedTimeMs();
        if (!updateElapsedTime(elapsedMs, elapsedTimeSLA > 0 && elapsedTimeSLA < elapsedMs)) {
            result.markElapsedTimeSlaNotMet();
            pass = false;
            message = result.getMessage();
            // exception = result.getException();
        }

        // result
        XSSFCell cellResult = row.get(COL_IDX_RESULT);
        cellResult.setCellValue(StringUtils.left(MessageUtils.markResult(message, pass, true), 32767));

        if (result.isError()) {
            ExcelStyleHelper.formatFailedStepDescription(this);
            Excel.createComment(cellDescription, cellResult.getStringCellValue(), COMMENT_AUTHOR);
        }

        boolean skipped = MessageUtils.isSkipped(message);
        if (skipped) {
            // paint both result and description the same style to improve readability
            cellResult.setCellStyle(worksheet.getStyle(STYLE_SKIPPED_RESULT));
            cellDescription.setCellStyle(worksheet.getStyle(STYLE_SKIPPED_RESULT));
            Excel.createComment(cellDescription, cellResult.getStringCellValue(), COMMENT_AUTHOR);
        } else {
            cellResult.setCellStyle(worksheet.getStyle(pass ? STYLE_SUCCESS_RESULT : STYLE_FAILED_RESULT));
        }
        ExcelStyleHelper.handleTextWrap(cellResult);

        // reason
        Throwable exception = result.getException();
        XSSFCell cellReason = row.get(COL_IDX_REASON);
        if (cellReason != null && !pass && exception != null) {
            String error = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
            cellReason.setCellValue(error);
            cellReason.setCellStyle(worksheet.getStyle(STYLE_MESSAGE));
        }

        if (CollectionUtils.isNotEmpty(nestedTestResults)) {
            TestStepManifest testStep = toTestStepManifest();
            testStep.setRowIndex(row.get(0).getRowIndex());
            testCase.getTestScenario().getExecutionSummary().addNestedMessages(testStep, nestedTestResults);
        }
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

        if (MessageUtils.isTestResult(message)) {
            if (context.isScreenshotOnError() && MessageUtils.isFail(message)) {
                NexialCommand plugin = context.findPlugin(target);
                if (plugin instanceof CanTakeScreenshot) {
                    String screenshotPath = ((CanTakeScreenshot) plugin).takeScreenshot(this);
                    if (StringUtils.isNotBlank(screenshotPath)) {
                        addNestedScreenCapture(screenshotPath, message);
                    } else {
                        context.getLogger().error(this, "Unable to capture screenshot - " + message);
                    }
                } else {
                    context.getLogger().error(this, message);
                }
            } else {
                addNestedMessage(message);
            }
        } else {
            addNestedMessage(message);
        }
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
        return lwTestStep;
    }

    private boolean isFileLink(String param) {
        return !StringUtils.startsWith(param, "[") &&
               StringUtils.countMatches(param, TOKEN_FUNCTION_START + "syspath|") == 1 &&
               StringUtils.countMatches(param, "|name" + TOKEN_FUNCTION_END) == 0;
    }

    private Comment toSystemComment(XSSFCell paramCell, String message) {
        return Excel.createComment(paramCell, toSystemComment(message), COMMENT_AUTHOR);
    }

    private String toSystemComment(String message) { return "test script:" + lineSeparator() + message; }
}