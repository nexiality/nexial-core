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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecutionLogger;

import static org.nexial.core.NexialConst.Data.CMD_REPEAT_UNTIL;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.SCENARIO;

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

    public boolean execute() throws IOException {
        ExecutionLogger logger = context.getLogger();
        logger.log(this, "executing test scenario");

        // by default, only fail fast if we are not in interactive mode
        boolean shouldFailFast = context.isFailFast();
        boolean skipDueToFailFast = false;
        boolean skipDueToEndFast = false;
        boolean skipDueToEndLoop = false;
        boolean allPass = true;

        executionSummary.setName(name);
        executionSummary.setExecutionLevel(SCENARIO);
        executionSummary.setTestScript(worksheet.excel().getOriginalFile());
        executionSummary.setStartTime(System.currentTimeMillis());
        executionSummary.setTotalSteps(CollectionUtils.size(allSteps));

        context.setCurrentScenario(this);
        ExecutionEventListener executionEventListener = context.getExecutionEventListener();
        executionEventListener.onScenarioStart();

        for (TestCase testCase : testCases) {
            context.setCurrentActivity(testCase);

            if (skipDueToFailFast) {
                logger.log(this, "skipping test activity due to previous failure");
                continue;
            }

            if (skipDueToEndFast) {
                logger.log(this, "skipping test activity due to previous end");
                continue;
            }

            if (skipDueToEndLoop) {
                logger.log(this, "skipping test activity due to break-loop in effect");
                continue;
            }

            boolean pass = testCase.execute();
            if (!pass) {
                allPass = false;
                if (shouldFailFast || context.isFailImmediate()) {
                    skipDueToFailFast = true;
                    logger.log(this, "test activity execution failed. " +
                                     "Because of this, all subsequent test case will be skipped");
                }
            }

            if (context.isEndImmediate()) {
                skipDueToEndFast = true;
                logger.log(this, "test activity execution ending due to EndIf() flow control activated.");
            }

            if (context.isBreakCurrentIteration()) {
                skipDueToEndLoop = true;
                logger.log(this, "test activity execution ending due to EndLoopIf() flow control activated " +
                                 "or unrecoverable execution failure.");
            }

            executionSummary.addNestSummary(testCase.getExecutionSummary());
        }

        context.setCurrentActivity(null);

        executionSummary.setEndTime(System.currentTimeMillis());
        executionSummary.setFailedFast(shouldFailFast);
        executionSummary.aggregatedNestedExecutions(context);

        ExecutionResultHelper.writeTestScenarioResult(worksheet, executionSummary);

        executionEventListener.onScenarioComplete(executionSummary);

        // clear off any scenario-ref incurred during the execution of this scenario.. we don't want current
        // scenario-ref's to taint subsequent scenario
        context.clearScenarioRefData();

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

    public void close() {
        if (CollectionUtils.isNotEmpty(testCases)) {
            testCases.forEach(TestCase::close);
            testCases.clear();
            testCases = null;
        }

        if (worksheet != null) {
            XSSFSheet sheet = worksheet.getSheet();
            if (sheet != null) {
                XSSFWorkbook workbook = sheet.getWorkbook();
                if (workbook != null) {
                    try {
                        workbook.close();
                    } catch (IOException e) {
                        ConsoleUtils.error("Unable to close scenario (" + name + "): " + e.getMessage());
                    }
                }
            }
            worksheet = null;
        }

        if (MapUtils.isNotEmpty(testCaseMap)) {
            testCaseMap.clear();
            testCaseMap = null;
        }

        if (CollectionUtils.isNotEmpty(allSteps)) {
            allSteps.clear();
            allSteps = null;
        }

        if (MapUtils.isNotEmpty(testStepsByRow)) {
            testStepsByRow.clear();
            testStepsByRow = null;
        }
    }

    protected void parse() {
        // 1. parse meta
        meta = TestScenarioMeta.newInstance(worksheet);

        // 2. find last command
        int lastCommandRow = worksheet.findLastDataRow(ADDR_COMMAND_START);
        area = new ExcelArea(worksheet, new ExcelAddress(FIRST_STEP_ROW + ":" + COL_REASON + lastCommandRow), false);
        testCases = new ArrayList<>();
        testCaseMap = new HashMap<>();
        allSteps = new ArrayList<>();
        testStepsByRow = new HashMap<>();

        // 3. parse for test steps->test case grouping
        TestCase currentTestCase = null;
        for (int i = 0; i < area.getWholeArea().size(); i++) {
            List<XSSFCell> row = area.getWholeArea().get(i);

            XSSFCell cellTestCase = row.get(COL_IDX_TESTCASE);
            String testCase = Excel.getCellValue(cellTestCase);
            boolean hasTestCase = StringUtils.isNotBlank(testCase);
            if (currentTestCase == null && !hasTestCase) {
                // first row must define test case (hence at least 1 test case is required)
                throw new RuntimeException("Invalid format found in " + worksheet.getFile() + " (" + name +
                                           "): Test case not found.");
            }

            if (hasTestCase) {
                currentTestCase = new TestCase();
                currentTestCase.setName(testCase);
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
                        " wrong parameters specified for " + CMD_REPEAT_UNTIL + ": " + testStep.getParams();
        if (CollectionUtils.size(testStep.getParams()) != 2) {
            ConsoleUtils.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        String steps = context.replaceTokens(testStep.getParams().get(0));
        int numOfStepsIncluded = NumberUtils.toInt(steps);
        if (numOfStepsIncluded < 1) {
            ConsoleUtils.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        if ((startFrom + numOfStepsIncluded) > allSteps.size()) {
            String errMsg1 = errMsg + " - number of steps specified greater than available in this test scenario";
            ConsoleUtils.error(errMsg1);
            throw new RuntimeException(errMsg1);
        }

        String maxWaitMs = context.replaceTokens(testStep.getParams().get(1));
        long maxWait = NumberUtils.toLong(maxWaitMs);
        if (maxWait != -1 && maxWait < 1000) {
            String errMsg1 = errMsg + " - minimum wait time is 1000ms: " + maxWait;
            ConsoleUtils.error(errMsg1);
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
