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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecutionLogger;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.CMD_COMMAND_REPEATER;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.MSG;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.SCENARIO;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;

public class TestScenario {
    private ExecutionContext context;
    private String name;
    private Worksheet worksheet;
    private TestScenarioMeta meta;
    private ExecutionSummary executionSummary = new ExecutionSummary();

    /**
     * the section with the corresponding worksheet that has test steps
     */
    private ExcelArea area;

    private List<TestCase> testCases;
    private Map<String, TestCase> testCaseMap;
    private List<TestStep> allSteps;
    private Map<Integer, TestStep> testStepsByRow;

    public TestScenario(ExecutionContext context, Worksheet worksheet) {
        assert context != null && StringUtils.isNotBlank(context.getId());
        assert worksheet != null && worksheet.getSheet() != null;

        this.context = context;
        this.worksheet = worksheet;
        this.name = worksheet.getName();

        parse();
    }

    public String getName() { return name; }

    public TestScenarioMeta getMeta() { return meta; }

    public ExecutionSummary getExecutionSummary() { return executionSummary; }

    public Worksheet getWorksheet() { return worksheet; }

    public ExecutionContext getContext() { return context; }

    public List<TestCase> getTestCases() { return testCases; }

    public Map<String, TestCase> getTestCaseMap() { return testCaseMap; }

    public List<TestStep> getAllSteps() { return allSteps; }

    public TestCase getTestCase(String name) { return testCaseMap.get(name); }

    public void save() throws IOException {
        XSSFCell summaryCell = worksheet.cell(ADDR_SCENARIO_EXEC_SUMMARY);
        if (summaryCell != null) {
            if (executionSummary.getEndTime() == 0) { executionSummary.setEndTime(System.currentTimeMillis()); }
            summaryCell.setCellValue(executionSummary.toString());
        }

        worksheet.getSheet().setZoom(100);
        worksheet.save();
    }

    public boolean execute() throws IOException {
        ExecutionLogger logger = context.getLogger();
        logger.log(this, "executing test scenario");

        boolean interativeMode = context.isInterativeMode();

        // by default, only fail fast if we are not in interactive mode
        boolean shouldFailFast = !interativeMode && context.isFailFast();
        boolean skipDueToFailFast = false;
        boolean skipDueToEndFast = false;
        boolean skipDueToEndLoop = false;
        boolean allPass = true;

        executionSummary.setName(name);
        executionSummary.setExecutionLevel(SCENARIO);
        executionSummary.setTestScript(worksheet.getFile());
        executionSummary.setStartTime(System.currentTimeMillis());
        executionSummary.setTotalSteps(CollectionUtils.size(allSteps));

        for (TestCase testCase : testCases) {
            if (skipDueToFailFast) {
                logger.log(this, "skipping test case due to previous failure");
                continue;
            }

            if (skipDueToEndFast) {
                logger.log(this, "skipping test case due to previous end");
                continue;
            }

            if (skipDueToEndLoop) {
                logger.log(this, "skipping test case due to break-loop in effect");
                continue;
            }

            boolean pass = testCase.execute();
            if (!pass) {
                allPass = false;
                if (shouldFailFast || context.isFailImmediate()) {
                    skipDueToFailFast = true;
                    logger.log(this, "test case execution failed. " +
                                     "Because of this, all subsequent test case will be skipped");
                }
            }

            if (context.isEndImmediate()) {
                skipDueToEndFast = true;
                logger.log(this, "test case execution ending due to EndIf() flow control activated.");
            }

            if (context.isBreakCurrentIteration()) {
                skipDueToEndLoop = true;
                logger.log(this, "test case execution ending due to EndLoopIf() flow control activated.");
            }

            executionSummary.addNestSummary(testCase.getExecutionSummary());

            // todo: forcefully stop video recording (if any) in case we are currently dealing
            // with the last agenda or that fail-fast condition is reached.
            //if (i == testScenarios.size() - 1 || failFast) { forceStopVideoRecording(); }
        }

        // if (interativeMode) {
            // doInteractive(context);
            // return allPass;
        // }

        executionSummary.setEndTime(System.currentTimeMillis());
        executionSummary.setFailedFast(shouldFailFast);

        XSSFSheet excelSheet = worksheet.getSheet();
        excelSheet.getWorkbook().setMissingCellPolicy(CREATE_NULL_AS_BLANK);

        Map<TestStepManifest, List<NestedMessage>> nestMessages = executionSummary.getNestMessages();
        int lastRow = worksheet.findLastDataRow(ADDR_COMMAND_START);
        if (MapUtils.isNotEmpty(nestMessages)) {
            int forwardRowsBy = 0;

            Set<TestStepManifest> testStepsWithNestMessages = nestMessages.keySet();
            for (TestStepManifest step : testStepsWithNestMessages) {
                if (StringUtils.equals(step.getCommandFQN(), CMD_VERBOSE)) { continue; }

                List<NestedMessage> nestedMessages = nestMessages.get(step);
                int messageCount = CollectionUtils.size(nestedMessages);
                if (messageCount < 1) { continue; }

                int currentRow = step.getRowIndex() + 1 + forwardRowsBy;
                // +1 if lastRow is the same as currentRow.  Otherwise shiftRow on a single row block causes problem for createRow (later on).
                worksheet.shiftRows(currentRow, lastRow + (currentRow == lastRow ? 1 : 0), messageCount);

                for (int i = 0; i < messageCount; i++) {
                    nestedMessages.get(i).printTo(excelSheet.createRow(currentRow + i));
                }

                lastRow += messageCount;
                forwardRowsBy += messageCount;
            }
        }

        // scan for verbose() or similar commands where merging should be done
        int startRow = ADDR_PARAMS_START.getRowStartIndex();
        for (int i = startRow; i < lastRow; i++) {
            XSSFRow row = excelSheet.getRow(i);
            if (row == null) { continue; }

            XSSFCell cellTarget = row.getCell(COL_IDX_TARGET);
            XSSFCell cellCommand = row.getCell(COL_IDX_COMMAND);
            String command = (cellTarget == null ? "" : cellTarget.getStringCellValue()) + "." +
                             (cellCommand == null ? "" : cellCommand.getStringCellValue());
            if (MERGE_OUTPUTS.contains(command)) { mergeOutput(excelSheet, row, i); }
        }

        executionSummary.aggregatedNestedExecutions(context);

        logger.log(this, "saving test scenario");
        save();

        return allPass;
    }

    public TestStep getTestStepByRowIndex(int rowIndex) { return testStepsByRow.get(rowIndex); }

    public List<TestStep> getTestStepsByRowRange(int startRow, int endRow) {
        List<TestStep> testSteps = new ArrayList<>();
        for (int i = startRow; i <= endRow; i++) {
            TestStep testStep = getTestStepByRowIndex(i);
            if (testStep != null) {testSteps.add(testStep); }
        }
        return testSteps;
    }

    protected void mergeOutput(XSSFSheet excelSheet, XSSFRow row, int rowIndex) {
        XSSFCell cellMerge = row.getCell(COL_IDX_MERGE_RESULT_START);
        if (cellMerge == null) { return; }

        // determine aggregated column width from 'param 1' to 'flow control'
        int mergedWidth = 0;
        for (int j = COL_IDX_MERGE_RESULT_START; j < COL_IDX_MERGE_RESULT_END + 1; j++) {
            mergedWidth += worksheet.getSheet().getColumnWidth(j);
        }
        int charPerLine = (int) ((mergedWidth - DEF_CHAR_WIDTH) / (DEF_CHAR_WIDTH * MSG.getFontHeight()));

        excelSheet.addMergedRegion(
            new CellRangeAddress(rowIndex, rowIndex, COL_IDX_MERGE_RESULT_START, COL_IDX_MERGE_RESULT_END));
        if (cellMerge.getCellTypeEnum() == STRING) { cellMerge.setCellStyle(worksheet.getStyle(STYLE_MESSAGE)); }

        String mergedContent = cellMerge.getStringCellValue();
        cellMerge.setCellValue(mergedContent);

        int lineCount = StringUtils.countMatches(mergedContent, "\n") + 1;
        String[] lines = StringUtils.split(mergedContent, "\n");
        if (ArrayUtils.isEmpty(lines)) { lines = new String[]{mergedContent}; }
        for (String line : lines) { lineCount += Math.ceil((double) StringUtils.length(line) / charPerLine) - 1; }

        // lineCount should always be at least 1. otherwise this row will not be rendered with height 0
        if (lineCount < 1) { lineCount = 1; }

        worksheet.setHeight(cellMerge, lineCount);
        if (lineCount == 1) { cellMerge.getRow().setHeightInPoints(20); }
    }

    // protected void doInteractive(ExecutionContext context) {
        // todo probably not in the right place.  Interactive mode would negate multiple test scenarios
        // context.setData(OPT_EXCEL_FILE, context.getStringData(OPT_LAST_TEST_SCENARIO));
        // context.setData(OPT_EXCEL_WORKSHEET, context.getStringData(OPT_LAST_TEST_STEP));
        // todo: need to fix soon
        //InteractiveDispatcher dispatcher = execution.newInteractiveDispatcher();
        //dispatcher.setExecutor(this);
        //dispatcher.doPrompt();
    // }

    protected void parse() {
        // 1. parse meta
        meta = TestScenarioMeta.newInstance(worksheet);

        // 2. find last command
        int lastCommandRow = worksheet.findLastDataRow(ADDR_COMMAND_START);
        area = new ExcelArea(worksheet,
                             new ExcelAddress("" + COL_TEST_CASE + (ADDR_COMMAND_START.getRowStartIndex() + 1) +
                                              ":" + COL_REASON + lastCommandRow),
                             false);
        testCases = new ArrayList<>();
        testCaseMap = new HashMap<>();
        allSteps = new ArrayList<>();
        testStepsByRow = new HashMap<>();

        // 3. parse for test steps->test case grouping
        TestCase currentTestCase = null;
        for (int i = 0; i < area.getWholeArea().size(); i++) {
            List<XSSFCell> row = area.getWholeArea().get(i);

            XSSFCell cellTestCase = row.get(COL_IDX_TESTCASE);
            boolean hasTestCase = cellTestCase != null && StringUtils.isNotBlank(cellTestCase.getStringCellValue());
            if (currentTestCase == null && !hasTestCase) {
                // first row must define test case (hence at least 1 test case is required)
                throw new RuntimeException("Invalid format found in " + worksheet.getFile() + " (" + name +
                                           "): Test case not found.");
            }

            if (hasTestCase) {
                currentTestCase = new TestCase();
                currentTestCase.setName(cellTestCase.getStringCellValue());
                currentTestCase.setTestScenario(this);
                testCases.add(currentTestCase);
                testCaseMap.put(currentTestCase.getName(), currentTestCase);
            }

            TestStep testStep = new TestStep(currentTestCase, row);
            if (testStep.isCommandRepeater()) { i += collectRepeatingCommandSet(testStep, area.getWholeArea(), i + 1); }
            currentTestCase.addTestStep(testStep);
            allSteps.add(testStep);
            testStepsByRow.put(row.get(0).getRowIndex() + 1, testStep);
        }
    }

    protected int collectRepeatingCommandSet(TestStep testStep, List<List<XSSFCell>> allSteps, int startFrom) {
        if (testStep == null || !testStep.isCommandRepeater() ||
            CollectionUtils.isEmpty(allSteps) || startFrom < 1) {
            return 0;
        }

        // expectation: first parameter is the number of test steps for the repeats
        // expectation: second parameter is the max. wait time in ms
        String errMsg = "[ROW " + (startFrom + ADDR_COMMAND_START.getRowStartIndex()) + "]" +
                        " wrong parameters specified for " + CMD_COMMAND_REPEATER + ": " + testStep.getParams();
        if (CollectionUtils.size(testStep.getParams()) != 2) {
            ConsoleUtils.log(errMsg);
            throw new RuntimeException(errMsg);
        }

        String steps = context.replaceTokens(testStep.getParams().get(0));
        int numOfStepsIncluded = NumberUtils.toInt(steps);
        if (numOfStepsIncluded < 1) {
            ConsoleUtils.log(errMsg);
            throw new RuntimeException(errMsg);
        }

        if ((startFrom + numOfStepsIncluded) > allSteps.size()) {
            String errMsg1 = errMsg + " - number of steps specified greater than available in this test scenario";
            ConsoleUtils.log(errMsg1);
            throw new RuntimeException(errMsg1);
        }

        String maxWaitMs = context.replaceTokens(testStep.getParams().get(1));
        long maxWait = NumberUtils.toLong(maxWaitMs);
        if (maxWait != -1 && maxWait < 1000) {
            String errMsg1 = errMsg + " - minimum wait time is 1000ms: " + maxWait;
            ConsoleUtils.log(errMsg1);
            throw new RuntimeException(errMsg1);
        }

        CommandRepeater commandRepeater = new CommandRepeater(testStep, maxWait);
        TestCase currentTestCase = testStep.getTestCase();
        for (int i = startFrom; i < (startFrom + numOfStepsIncluded); i++) {
            List<XSSFCell> row = area.getWholeArea().get(i);
            TestStep nextStep = new TestStep(currentTestCase, row);

            // check that first step is an assertion - REQUIRED
            if (i == startFrom && !StringUtils.startsWith(nextStep.getCommand(), "assert")) {
                String errMsg1 = "[ROW " + (startFrom + area.getTopLeft().getRowIndex()) + "] " +
                                 "wrong command for the first step.  First command MUST be an assertion " +
                                 "(assert* command), not " + nextStep.getCommandFQN();
                ConsoleUtils.error(errMsg1);
                throw new RuntimeException(errMsg1);
            }

            commandRepeater.addStep(nextStep);
        }

        testStep.setCommandRepeater(commandRepeater);
        return (numOfStepsIncluded);
    }
}
