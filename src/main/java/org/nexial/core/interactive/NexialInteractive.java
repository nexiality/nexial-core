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

import java.util.List;
import java.util.Scanner;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.logging.LogbackUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionInputPrep;
import org.nexial.core.excel.Excel;
import org.nexial.core.model.*;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtil;
import org.nexial.core.utils.ExecutionLogger;

import static org.nexial.core.NexialConst.Data.BREAK_CURRENT_ITERATION;
import static org.nexial.core.NexialConst.Data.CURR_ITERATION;
import static org.nexial.core.NexialConst.OPT_LAST_OUTCOME;
import static org.nexial.core.NexialConst.Project.appendLog;
import static org.nexial.core.interactive.InteractiveConsole.Commands.*;

public class NexialInteractive {
    private ExecutionDefinition executionDefinition;

    public ExecutionDefinition getExecutionDefinition() { return executionDefinition; }

    public void setExecutionDefinition(ExecutionDefinition execDef) { this.executionDefinition = execDef; }

    public void startSession() {
        // start of test suite (one per test plan in execution)
        String runId = ExecUtil.deriveRunId();

        executionDefinition.setRunId(runId);
        LogbackUtils.registerLogDirectory(appendLog(executionDefinition));

        TestData testData = executionDefinition.getTestData();
        String scriptLocation = executionDefinition.getTestScript();

        ConsoleUtils.log(runId, ("[" + scriptLocation + "] ") + "resolve RUN ID as " + runId);

        // track iteration setting for current script
        IterationManager iterationManager = testData.getIterationManager();

        ExecutionContext context = new ExecutionContext(executionDefinition);
        ExecutionLogger logger = context.getLogger();

        InteractiveSession session = new InteractiveSession(context);
        session.setIteration(1);
        session.setScript(executionDefinition.getTestScript());
        if (executionDefinition.getDataFile() != null) {
            session.setDataFile(executionDefinition.getDataFile().getFile().getAbsolutePath());
        }

        InteractiveConsole.showInteractiveMenu(session);
        processMenu(session);
    }

    protected void processMenu(InteractiveSession session) {
        String runId = session.getContext().getRunId();

        System.out.print("> command: ");
        String input = new Scanner(System.in).nextLine();
        String command = StringUtils.upperCase(StringUtils.trim(StringUtils.substringBefore(input, " ")));
        String argument = StringUtils.trim(StringUtils.substringAfter(input, " "));

        switch (command) {
            case CMD_SET_SCRIPT: {
                session.setScript(argument);
                processMenu(session);
                break;
            }
            case CMD_SET_DATA: {
                session.setDataFile(argument);
                processMenu(session);
                break;
            }
            case CMD_SET_SCENARIO: {
                session.setScript(argument);
                processMenu(session);
                break;

            }
            case CMD_SET_ITER: {
                session.setIteration(NumberUtils.createInteger(argument));
                processMenu(session);
                break;
            }
            case CMD_SET_ACTIVITY: {
                session.setActivities(TextUtils.toList(argument, ",", true));
                session.clearSteps();
                processMenu(session);
                break;
            }
            case CMD_SET_STEPS: {
                session.setSteps(TextUtils.toList(argument, ",", true));
                session.clearActivities();
                processMenu(session);
                break;
            }
            case CMD_RELOAD_SCRIPT: {
                // todo: reload script
                processMenu(session);
                break;
            }
            case CMD_RELOAD_DATA: {
                // todo: reload data
                processMenu(session);
                break;
            }
            case CMD_RELOAD_MENU: {
                InteractiveConsole.showInteractiveMenu(session);
                processMenu(session);
                break;
            }
            case CMD_RUN: {
                execute(session);
                InteractiveConsole.showInteractiveMenu(session);
                processMenu(session);
                break;
            }
            case CMD_EXIT: {
                ConsoleUtils.log("Ending Nexial Interactive session...");
                break;
            }
            default: {
                ConsoleUtils.error(runId, "Unknown command " + input + ". Try again...");
                processMenu(session);
            }
        }
    }

    protected void execute(InteractiveSession session) {
        ExecutionContext context = session.getContext();
        String runId = context.getRunId();

        // at the start of each interactive run, always reset to simulate nothing was run and nothing was wrong
        context.setCurrentActivity(null);
        context.setData(OPT_LAST_OUTCOME, true);
        context.setData(CURR_ITERATION, session.getIteration());
        // todo: reset pass/fail/executed count

        try {
            Excel testScript =
                ExecutionInputPrep.prep(runId, executionDefinition, session.getIteration(), session.getIteration());
            context.useTestScript(testScript);
            context.adjustForInteractive(session);
            boolean executionResult = context.execute();
            storeResult(executionResult, session);
        } catch (Throwable e) {
            e.printStackTrace();
            session.setException(e);
        }

        // todo redisplay interactive menu
        // ConsoleUtils.log(runId, session.toString());
    }

    protected void storeResult(boolean allPass, InteractiveSession session) {
        if (session == null) { return; }

        ExecutionContext context = session.getContext();

        List<TestScenario> testScenarios = context.getTestScenarios();
        if (CollectionUtils.isEmpty(testScenarios)) { return; }

        context.removeData(BREAK_CURRENT_ITERATION);

        // augment the execution summaries
        String scriptPath = session.getScript();
        testScenarios.forEach(testScenario -> {
            ExecutionSummary executionSummary = testScenario.getExecutionSummary();
            if (executionSummary != null) { executionSummary.setSourceScript(scriptPath); }
        });
        InteractiveConsole.showInteractiveRun(session);
    }
}
