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
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.model.InteractiveSession;
import org.nexial.core.model.TestScenario;

import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.core.interactive.InteractiveConsole.Commands.*;
import static org.nexial.core.utils.ConsoleUtils.*;

public class InteractiveConsole {

    private static final String HEADER_EXECUTED = META_START + "Executed" + META_END;
    private static final String HEADER_SCRIPT = META_START + "Script  " + META_END;
    private static final String HEADER_SCENARIO = META_START + "Scenario" + META_END;
    private static final String HEADER_EXCEPTION = META_START + "Error(s)" + META_END;

    private static final String HEADER_SESSION = META_START + "Session " + META_END;

    private static final String HEADER_ACTIVITY = META_START + "Activity" + META_END;
    private static final String HEADER_STEPS = META_START + "Step(s) " + META_END;

    private static final String SUB1_START = StringUtils.repeat(" ", HEADER_ACTIVITY.length());
    private static final String SUB2_END = ": ";

    private static final String SUB1_HEADER_TIMESPAN = SUB1_START + "timespan       " + SUB2_END;
    private static final String SUB1_HEADER_DURATION = SUB1_START + "duration       " + SUB2_END;
    private static final String SUB1_HEADER_ITERATION = SUB1_START + "iteration      " + SUB2_END;
    private static final String SUB1_HEADER_STATS = SUB1_START + "total/pass/fail" + SUB2_END;

    private static final int SCRIPT_MAX_LENGTH = PROMPT_LINE_WIDTH -
                                                 MARGIN_LEFT.length() -
                                                 HEADER_SCRIPT.length() -
                                                 MARGIN_RIGHT.length();
    private static final int REF_MAX_LENGTH = SUB1_HEADER_STATS.length() - SUB1_START.length() - SUB2_END.length();

    private static final char FILLER_MENU = '~';
    private static final String CMD_START = "  ";
    private static final String CMD_END = " ";


    class Commands {
        private Commands() {}

        static final String CMD_SET_SCRIPT = "1";
        static final String CMD_SET_DATA = "2";
        static final String CMD_SET_SCENARIO = "3";
        static final String CMD_SET_ITER = "4";
        static final String CMD_SET_ACTIVITY = "5";
        static final String CMD_SET_STEPS = "6";
        static final String CMD_RELOAD_SCRIPT = "7";
        static final String CMD_RELOAD_DATA = "8";
        static final String CMD_RELOAD_MENU = "9";
        static final String CMD_RUN = "X";
        static final String CMD_EXIT = "Q";
    }

    public static void showInteractiveMenu(InteractiveSession session) {
        if (session == null) {
            System.err.println("ERROR: No interactive session found");
            return;
        }

        printConsoleHeaderTop(System.out, "NEXIAL INTERACTIVE", FILLER_MENU);
        printHeaderLine(System.out, HEADER_SESSION, formatExecutionMeta(session.getStartTime()));
        printHeaderLine(System.out, HEADER_SCRIPT, formatTestScript(session.getScript()));
        printHeaderLine(System.out, HEADER_SCENARIO, session.getScenario());
        printHeaderLine(System.out, HEADER_ACTIVITY, session.getActivities());
        printHeaderLine(System.out, HEADER_STEPS, TextUtils.toString(session.getSteps(), ", "));
        printConsoleSectionSeparator(System.out, FILLER_MENU);

        printHeaderLine(System.out, "Available commands:", " ");
        printHeaderLine(System.out, CMD_START + CMD_SET_SCRIPT + " <script>   " + CMD_END, "specify test script");
        printHeaderLine(System.out, CMD_START + CMD_SET_DATA + " <data>     " + CMD_END, "specify data file");
        printHeaderLine(System.out, CMD_START + CMD_SET_SCENARIO + " <scenario> " + CMD_END, "specify scenario");
        printHeaderLine(System.out, CMD_START + CMD_SET_ITER + " <iteration>" + CMD_END, "specify iteration");
        printHeaderLine(System.out, CMD_START + CMD_SET_ACTIVITY + " <activity> " + CMD_END,
                        "specify comma-separated activities. This effectively clears any specified test steps");
        printHeaderLine(System.out, CMD_START + CMD_SET_STEPS + " <step(s)>  " + CMD_END,
                        "specify steps (single step, comma-separated steps or step range). This effectively clears " +
                        "any assigned activities");
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_SCRIPT + "            " + CMD_END,
                        "reload currently assigned test script");
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_DATA + "            " + CMD_END,
                        "reload currently assigned data file");
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_MENU + "            " + CMD_END, "reload this menu");
        printHeaderLine(System.out, CMD_START + CMD_RUN + "            " + CMD_END, "execute the specified steps");
        printHeaderLine(System.out, CMD_START + CMD_EXIT + "            " + CMD_END, "quit this interactive session");
        printConsoleHeaderBottom(System.out, FILLER_MENU);
    }

    public static void showInteractiveRun(InteractiveSession session) {
        if (session == null) {
            System.err.println("ERROR: No interactive session found");
            return;
        }

        ExecutionContext context = session.getContext();
        List<TestScenario> testScenarios = context.getTestScenarios();
        if (CollectionUtils.isEmpty(testScenarios)) {
            System.err.println("ERROR: Test steps executed");
            return;
        }

        testScenarios.forEach(scenario -> showInteractiveRun(scenario.getExecutionSummary(), session.getException()));
        System.out.println();
        System.out.println();
    }

    public static void showInteractiveRun(ExecutionSummary scenarioSummary, Throwable error) {
        printConsoleHeaderTop(System.out, "NEXIAL INTERACTIVE", FILLER);
        printHeaderLine(System.out, HEADER_EXECUTED, formatExecutionMeta(scenarioSummary.getStartTime()));
        printHeaderLine(System.out, HEADER_SCRIPT, formatTestScript(scenarioSummary.getSourceScript()));
        printHeaderLine(System.out, HEADER_SCENARIO, scenarioSummary.getName());
        if (error != null) { printHeaderLine(System.out, HEADER_EXCEPTION, error.getMessage()); }
        printConsoleSectionSeparator(System.out, FILLER);

        List<ExecutionSummary> activitySummaries = scenarioSummary.getNestedExecutions();
        activitySummaries.forEach(activity -> {
            long endTime = activity.getEndTime();
            long startTime = activity.getStartTime();
            String timeSpan = DateUtility.formatLongDate(startTime) + " - " + DateUtility.formatLongDate(endTime);
            String duration = DateUtility.formatStopWatchTime(endTime - startTime);
            String stats = activity.getTotalSteps() + MULTI_SEP +
                           activity.getPassCount() + MULTI_SEP +
                           activity.getFailCount();

            printHeaderLine(System.out, HEADER_ACTIVITY, activity.getName());
            printHeaderLine(System.out, SUB1_HEADER_TIMESPAN, timeSpan);
            printHeaderLine(System.out, SUB1_HEADER_DURATION, duration);
            // todo: fix to use real data
            printHeaderLine(System.out, SUB1_HEADER_ITERATION, "1");
            printHeaderLine(System.out, SUB1_HEADER_STATS, stats);

            Map<String, String> refs = activity.getReferenceData();
            if (MapUtils.isNotEmpty(refs)) {
                refs.forEach((key, value) -> {
                    String refKey = SUB1_START + StringUtils.rightPad("(" + key + ")", REF_MAX_LENGTH, " ") + SUB2_END;
                    printHeaderLine(System.out, refKey, value);
                });
            }
        });

        printConsoleHeaderBottom(System.out, FILLER);
    }

    private static String formatTestScript(String testScript) {
        if (StringUtils.length(testScript) > SCRIPT_MAX_LENGTH) {
            testScript = "..." + testScript.substring(testScript.length() - SCRIPT_MAX_LENGTH + 3);
        }
        return testScript;
    }

    @NotNull
    private static String formatExecutionMeta(long startTime) {
        return USER_NAME + MULTI_SEP +
               EnvUtils.getHostName() + " (" + OS_NAME + " " + OS_VERSION + ")" + MULTI_SEP +
               DateUtility.formatLongDate(startTime);
    }
}
