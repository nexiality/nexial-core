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
 */

package org.nexial.core.interactive;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.logging.LogbackUtils;
import org.nexial.commons.proc.RuntimeUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionInputPrep;
import org.nexial.core.ExecutionThread;
import org.nexial.core.excel.Excel;
import org.nexial.core.model.*;
import org.nexial.core.model.ExecutionSummary.ExecutionLevel;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtil;
import org.nexial.core.utils.ExecutionLogger;

import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.OPT_LAST_OUTCOME;
import static org.nexial.core.NexialConst.Project.appendLog;
import static org.nexial.core.interactive.InteractiveConsole.Commands.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.*;

public class NexialInteractive {
    private static final String RANGE_SEP = "-";
    private static final String LIST_SEP = ",";
    private ExecutionDefinition executionDefinition;

    // todo: disable during jenkins or junit run
    // todo: enable video recording
    // todo: save session

    public ExecutionDefinition getExecutionDefinition() { return executionDefinition; }

    public void setExecutionDefinition(ExecutionDefinition execDef) { this.executionDefinition = execDef; }

    public void startSession() {
        // start of test suite (one per test plan in execution)
        String runId = ExecUtil.deriveRunId();

        executionDefinition.setRunId(runId);
        LogbackUtils.registerLogDirectory(appendLog(executionDefinition));

        String scriptLocation = executionDefinition.getTestScript();

        ConsoleUtils.log(runId, "[" + scriptLocation + "] resolve RUN ID as " + runId);

        InteractiveSession session = new InteractiveSession(new ExecutionContext(executionDefinition));
        session.setExecutionDefinition(executionDefinition);
        session.setIteration(1);

        InteractiveConsole.showMenu(session);
        processMenu(session);
    }

    protected void processMenu(InteractiveSession session) {
        boolean proceed = true;
        while (proceed) {
            System.out.print("> command: ");
            String input = new Scanner(System.in).nextLine();
            String command = StringUtils.upperCase(StringUtils.trim(StringUtils.substringBefore(input, " ")));
            String argument = StringUtils.trim(StringUtils.substringAfter(input, " "));

            switch (command) {
                case CMD_SET_SCRIPT: {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No test script assigned");
                    } else {
                        session.setScript(argument);
                    }
                    break;
                }

                case CMD_SET_DATA: {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No data file assigned");
                    } else {
                        session.setDataFile(argument);
                    }
                    break;
                }

                case CMD_SET_SCENARIO: {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No scenario assigned");
                    } else {
                        session.setScenario(argument);
                    }
                    break;
                }

                case CMD_SET_ITER: {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No iteration assigned");
                    } else {
                        session.setIteration(NumberUtils.createInteger(argument));
                    }
                    break;
                }

                case CMD_SET_ACTIVITY: {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No activity assigned");
                    } else {
                        session.setActivities(TextUtils.toList(argument, LIST_SEP, true));
                        session.clearSteps();
                    }
                    break;
                }

                case CMD_SET_STEPS: {
                    // steps can be range (dash) or comma-separated
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No test step assigned");
                    } else {
                        session.setSteps(toSteps(argument));
                        session.clearActivities();
                    }
                    break;
                }

                case CMD_RELOAD_SCRIPT: {
                    session.reloadTestScript();
                    InteractiveConsole.showMenu(session);
                    break;
                }

                case CMD_RELOAD_DATA: {
                    session.reloadDataFile();
                    InteractiveConsole.showMenu(session);
                    break;
                }

                case CMD_RELOAD_MENU: {
                    InteractiveConsole.showMenu(session);
                    break;
                }

                case CMD_RUN: {
                    execute(session);
                    InteractiveConsole.showMenu(session);
                    break;
                }

                case CMD_INSPECT: {
                    inspect(session);
                    InteractiveConsole.showMenu(session);
                    break;
                }

                case CMD_ALL_STEP: {
                    session.useAllActivities();
                    InteractiveConsole.showMenu(session);
                    break;
                }

                case CMD_OPEN_SCRIPT: {
                    if (StringUtils.isBlank(session.getScript())) {
                        ConsoleUtils.error("No valid test script assigned");
                    } else {
                        Excel.openExcel(new File(session.getScript()));
                    }
                    break;
                }

                case CMD_OPEN_DATA: {
                    if (StringUtils.isBlank(session.getDataFile())) {
                        ConsoleUtils.error("No valid data file assigned");
                    } else {
                        Excel.openExcel(new File(session.getDataFile()));
                    }
                    break;
                }

                case CMD_HELP: {
                    InteractiveConsole.showHelp(session);
                    InteractiveConsole.showMenu(session);
                    break;
                }

                case CMD_EXIT: {
                    proceed = false;
                    ConsoleUtils.log("Ending Nexial Interactive session...");
                    break;
                }

                default: {
                    ConsoleUtils.error("Unknown command " + input + ". Try again...");
                }
            }
        }
    }

    @NotNull
    protected List<String> toSteps(String argument) {
        String steps = argument;
        while (true) {
            String range = RegexUtils.firstMatches(steps, "(\\d+\\-\\d+)");
            if (StringUtils.isBlank(range)) { break; }

            int startNum = NumberUtils.toInt(StringUtils.substringBefore(range, RANGE_SEP));
            int endNum = NumberUtils.toInt(StringUtils.substringAfter(range, RANGE_SEP));
            StringBuilder numberRange = new StringBuilder();
            for (int i = startNum; i <= endNum; i++) { numberRange.append(i).append(LIST_SEP); }
            steps = StringUtils.replace(steps, range, StringUtils.removeEnd(numberRange + "", LIST_SEP));
        }

        steps = RegExUtils.removeAll(steps, "\\ \\t\\n\\r");
        return TextUtils.toList(steps, LIST_SEP, true);
    }

    protected void inspect(InteractiveSession session) {
        System.out.print("> inspect: ");
        Scanner in = new Scanner(System.in);
        String input = in.nextLine();

        while (StringUtils.isNotBlank(input)) {
            try {
                System.out.println(session.getContext().replaceTokens(input));
            } catch (Throwable e) {
                ConsoleUtils.error("ERROR on '" + input + "' - " + e.getMessage());
            }
            System.out.println();
            System.out.print("> inspect: ");
            input = in.nextLine();
        }
    }

    protected void execute(InteractiveSession session) {
        // sanity check
        if (StringUtils.isBlank(session.getScript())) {
            ConsoleUtils.error("No test script assigned");
            return;
        }

        if (StringUtils.isBlank(session.getScenario())) {
            ConsoleUtils.error("No test scenario assigned");
            return;
        }

        if (CollectionUtils.isEmpty(session.getActivities()) && CollectionUtils.isEmpty(session.getSteps())) {
            ConsoleUtils.error("No activity or test step assigned");
            return;
        }

        ExecutionContext context = session.getContext();

        String runId = context.getRunId();
        int currIteration = session.getIteration();

        context.setCurrentActivity(null);
        context.setFailImmediate(false);
        context.setData(OPT_LAST_OUTCOME, true);
        context.removeData(BREAK_CURRENT_ITERATION);
        context.setData(CURR_ITERATION, currIteration);

        String scriptLocation = executionDefinition.getTestScript();
        TestData testData = executionDefinition.getTestData();
        IterationManager iterationManager = testData.getIterationManager();
        int iteration = iterationManager.getIterationRef(currIteration - 1);

        ConsoleUtils.log(runId, "executing " + scriptLocation + ". " + iterationManager);

        ExecutionThread.set(context);

        boolean allPass = true;
        TestScenario targetScenario;
        ExecutionSummary scenarioSummary = null;

        try {
            context.markExecutionStart();

            Excel testScript = session.getInflightScript();
            if (testScript == null) {
                testScript = ExecutionInputPrep.prep(runId, executionDefinition, iteration, currIteration);
                context.useTestScript(testScript);
                session.setInflightScript(testScript);
            }

            // find target scenario object
            targetScenario = session.getInflightScenario();
            if (targetScenario == null) {
                List<TestScenario> availableScenarios = context.getTestScenarios();
                for (TestScenario testScenario : availableScenarios) {
                    if (StringUtils.equals(testScenario.getName(), session.getScenario())) {
                        targetScenario = testScenario;
                        break;
                    }
                }

                if (targetScenario == null) {
                    ConsoleUtils.error("Invalid test scenario assigned: " + session.getScript());
                    return;
                }

                session.setInflightScenario(targetScenario);
                availableScenarios.clear();
                availableScenarios.add(targetScenario);
            }

            // gather pre-execution reference data here, so that after the execution we can reset the reference data
            // set back to its pre-execution state
            Map<String, String> ref = context.gatherScenarioReferenceData();

            // re-init scenario ref data
            context.clearScenarioRefData();
            ref.forEach((name, value) -> context.setData(SCENARIO_REF_PREFIX + name, context.replaceTokens(value)));

            // reset for this run
            scenarioSummary = resetScenarioExecutionSummary(session, targetScenario);

            allPass = CollectionUtils.isNotEmpty(session.getActivities()) ?
                      executeActivities(session, scenarioSummary) : executeSteps(session, scenarioSummary);

        } catch (Throwable e) {
            e.printStackTrace();
            session.setException(e);
        } finally {
            context.setData(ITERATION_ENDED, true);
            context.removeData(BREAK_CURRENT_ITERATION);

            context.markExecutionEnd();
            if (scenarioSummary != null) { scenarioSummary.setEndTime(context.getEndTimestamp()); }

            postExecution(allPass, session);

            // context.endIteration();
            ExecutionThread.unset();

            RuntimeUtils.gc();
        }
    }

    protected boolean executeActivities(InteractiveSession session, ExecutionSummary parentSummary) {
        ExecutionContext context = session.getContext();
        TestScenario scenario = context.getTestScenarios().get(0);

        boolean allPass = true;
        List<String> activities = session.getActivities();
        for (String activityName : activities) {
            TestCase activity = scenario.getTestCase(activityName);
            if (activity != null) {
                ExecutionSummary activitySummary = resetActivityExecutionSummary(session, activity);
                allPass = activity.execute();
                parentSummary.addNestSummary(activitySummary);
            }
        }

        return allPass;
    }

    protected boolean executeSteps(InteractiveSession session, ExecutionSummary parentSummary) {

        ExecutionContext context = session.getContext();
        ExecutionLogger logger = context.getLogger();
        TestScenario scenario = context.getTestScenarios().get(0);

        List<String> steps = session.getSteps();
        parentSummary.setTotalSteps(steps.size());

        boolean allPass = true;
        for (String testStepId : steps) {
            TestStep testStep = scenario.getTestStepByRowIndex(NumberUtils.toInt(testStepId));

            ExecutionSummary stepSummary = new ExecutionSummary();
            stepSummary.setName("[ROW " + StringUtils.leftPad((testStepId) + "", 3) + "]");
            stepSummary.setStartTime(System.currentTimeMillis());
            stepSummary.setTotalSteps(1);
            stepSummary.setExecutionLevel(STEP);

            StepResult result = testStep.execute();

            stepSummary.setEndTime(System.currentTimeMillis());
            parentSummary.addNestSummary(stepSummary);

            if (context.isEndImmediate()) {
                // parentSummary.adjustTotalSteps(-1);
                stepSummary.adjustTotalSteps(-1);
                break;
            }

            if (result.isSkipped()) {
                // parentSummary.adjustTotalSteps(-1);
                stepSummary.adjustTotalSteps(-1);
                if (context.isBreakCurrentIteration()) { break; } else { continue; }
            }

            // parentSummary.incrementExecuted();
            stepSummary.incrementExecuted();

            if (result.isSuccess()) {
                // parentSummary.incrementPass();
                stepSummary.incrementPass();
                continue;
            }

            // SKIP condition handle earlier, so this is real FAIL condition
            // parentSummary.incrementFail();
            stepSummary.incrementFail();
            allPass = false;

            // by default, only fail fast if we are not in interactive mode
            // this line is added here instead of outside the loop so that we can consider any changes to nexial.failFast
            // whilst executing the activity
            if (context.isFailFast()) {
                logger.log(testStep, "test stopping due to execution failure and fail-fast in effect");
                break;
            }

            if (context.isFailFastCommand(testStep)) {
                logger.log(testStep, "test stopping due to failure on fail-fast command: " + testStep.getCommandFQN());
                context.setFailImmediate(true);
                break;
            }

            if (context.isFailImmediate()) {
                logger.log(testStep, "test stopping due fail-immediate in effect");
                break;
            }

            if (context.isEndImmediate()) {
                logger.log(testStep, "test scenario execution ended due to EndIf() flow control");
                break;
            }

            if (context.isBreakCurrentIteration()) {
                logger.log(testStep, "test scenario execution ended due to EndLoopIf() flow control");
                break;
            }
        }

        parentSummary.aggregatedNestedExecutions(context);
        return allPass;
    }

    protected void resetExecutionSummary(InteractiveSession session,
                                         ExecutionSummary es,
                                         String name,
                                         ExecutionLevel level) {
        es.setSourceScript(session.getScript());
        es.setStartTime(System.currentTimeMillis());
        es.setExecutionLevel(level);
        es.setName(name);
        es.getNestedExecutions().clear();
        es.getNestMessages().clear();
        es.setTotalSteps(0);
        es.setExecuted(0);
        es.setFailCount(0);
        es.setPassCount(0);
        es.setWarnCount(0);
        es.setError(null);
    }

    protected void postExecution(boolean allPass, InteractiveSession session) {
        if (session == null) { return; }

        ExecutionContext context = session.getContext();

        List<TestScenario> testScenarios = context.getTestScenarios();
        if (CollectionUtils.isEmpty(testScenarios)) { return; }

        // augment the execution summaries
        // String scriptPath = session.getScript();
        // testScenarios.forEach(testScenario -> {
        // ExecutionSummary executionSummary = testScenario.getExecutionSummary();
        // if (executionSummary != null) { executionSummary.setSourceScript(scriptPath); }
        // });

        InteractiveConsole.showRun(session);
    }

    @NotNull
    private ExecutionSummary resetScenarioExecutionSummary(InteractiveSession session, TestScenario targetScenario) {
        ExecutionSummary scenarioSummary = targetScenario.getExecutionSummary();
        resetExecutionSummary(session, scenarioSummary, targetScenario.getName(), SCENARIO);
        return scenarioSummary;
    }

    @NotNull
    private ExecutionSummary resetActivityExecutionSummary(InteractiveSession session, TestCase activity) {
        ExecutionSummary activitySummary = activity.getExecutionSummary();
        resetExecutionSummary(session, activitySummary, activity.getName(), ACTIVITY);
        return activitySummary;
    }
}
