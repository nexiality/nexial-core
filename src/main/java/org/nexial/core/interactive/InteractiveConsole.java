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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.model.TestScenario;

import com.diogonunes.jcdp.bw.Printer;
import com.diogonunes.jcdp.bw.Printer.Types;
import com.diogonunes.jcdp.color.ColoredPrinter;
import com.diogonunes.jcdp.color.api.Ansi.Attribute;

import static com.diogonunes.jcdp.color.api.Ansi.Attribute.BOLD;
import static com.diogonunes.jcdp.color.api.Ansi.BColor.NONE;
import static com.diogonunes.jcdp.color.api.Ansi.FColor.*;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.core.interactive.InteractiveConsole.Commands.*;
import static org.nexial.core.model.ExecutionSummary.ExecutionLevel.STEP;
import static org.nexial.core.utils.ConsoleUtils.*;

public class InteractiveConsole {

    private static final String HEADER_EXECUTED = META_START + "Executed" + META_END;
    private static final String HEADER_SESSION = META_START + "Session " + META_END;
    private static final String HEADER_SCRIPT = META_START + "Script  " + META_END;
    private static final String HEADER_SCENARIO = META_START + "Scenario" + META_END;
    private static final String HEADER_ACTIVITY = META_START + "Activity" + META_END;
    private static final String HEADER_STEPS = META_START + "Step    " + META_END;
    private static final String HEADER_SUMMARY = META_START + "Summary " + META_END;
    private static final String HEADER_EXCEPTION = META_START + "ERROR   " + META_END;

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
    private static final String HELP_TEMPLATE_RESOURCE =
        StringUtils.replace(InteractiveConsole.class.getPackage().getName(), ".", "/") +
        "/nexial-interactive-help.properties";
    private static final Properties HELP_TEMPLATE;

    class Commands {
        static final String CMD_SET_SCRIPT = "1";
        static final String CMD_SET_DATA = "2";
        static final String CMD_SET_SCENARIO = "3";
        static final String CMD_SET_ITER = "4";
        static final String CMD_SET_ACTIVITY = "5";
        static final String CMD_SET_STEPS = "6";
        static final String CMD_RELOAD_SCRIPT = "7";
        static final String CMD_RELOAD_DATA = "8";
        // static final String CMD_RELOAD_MENU = "9";
        static final String CMD_RELOAD_MENU = "R";
        static final String CMD_HELP = "H";
        static final String CMD_RUN = "X";
        static final String CMD_INSPECT = "I";
        static final String CMD_EXIT = "Q";

        private Commands() {}
    }

    public static void showMenu(InteractiveSession session) {
        if (session == null) {
            System.err.println("ERROR: No interactive session found");
            return;
        }

        printConsoleHeaderTop(System.out, "NEXIAL INTERACTIVE", FILLER_MENU);
        printHeaderLine(System.out, HEADER_SESSION, formatExecutionMeta(session.getStartTime()));
        printHeaderLine(System.out, HEADER_SCRIPT, formatTestScript(session.getScript()));
        printHeaderLine(System.out, HEADER_SCENARIO, session.getScenario());
        printHeaderLine(System.out, HEADER_ACTIVITY, session.getActivities());
        printHeaderLine(System.out, HEADER_STEPS, TextUtils.toString(session.getSteps(), ","));
        // printConsoleSectionSeparator(System.out, FILLER_MENU);

        // printHeaderLine(System.out, "Available commands:", " ");
        printConsoleSectionSeparator(System.out, "~~options", FILLER_MENU);
        printHeaderLine(System.out, CMD_START + CMD_SET_SCRIPT + " <script>   " + CMD_END, "assign test script");
        printHeaderLine(System.out, CMD_START + CMD_SET_DATA + " <data file>" + CMD_END, "assign data file");
        printHeaderLine(System.out, CMD_START + CMD_SET_SCENARIO + " <scenario> " + CMD_END, "assign scenario");
        printHeaderLine(System.out, CMD_START + CMD_SET_ITER + " <iteration>" + CMD_END, "assign iteration");
        printHeaderLine(System.out, CMD_START + CMD_SET_ACTIVITY + " <activity> " + CMD_END,
                        "assign activities; clears assigned test steps");
        printHeaderLine(System.out, CMD_START + CMD_SET_STEPS + " <step>     " + CMD_END,
                        "assign steps; clears assigned activities");
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_SCRIPT + "            " + CMD_END,
                        "reload assigned test script");
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_DATA + "            " + CMD_END,
                        "reload assigned data file");
        // printHeaderLine(System.out, CMD_START + CMD_RELOAD_MENU +   "            " + CMD_END, "reload this menu");
        // printHeaderLine(System.out, CMD_START + CMD_HELP +          "            " + CMD_END, "get help on Nexial Interactive");
        // printHeaderLine(System.out, CMD_START + CMD_RUN +           "            " + CMD_END, "execute current configuration");
        // printHeaderLine(System.out, CMD_START + CMD_INSPECT +       "            " + CMD_END, "inspect data");
        // printHeaderLine(System.out, CMD_START + CMD_EXIT +          "            " + CMD_END, "quit Nexial Interactive");
        printHeaderLine(System.out, CMD_START + "action       " + CMD_END, "(" + CMD_RELOAD_MENU + ")eload menu  " +
                                                                           "(" + CMD_HELP + ")elp  " +
                                                                           "e(" + CMD_RUN + ")ecute  " +
                                                                           "(" + CMD_INSPECT + ")nspect  " +
                                                                           "(" + CMD_EXIT + ")uit");
        printConsoleHeaderBottom(System.out, FILLER_MENU);
    }

    public static void showRun(InteractiveSession session) {
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

        testScenarios.forEach(scenario -> showRun(scenario.getExecutionSummary(), session));
        System.out.println();
        System.out.println();
    }

    public static void showRun(ExecutionSummary scenarioSummary, InteractiveSession session) {
        // scenarioSummary.aggregatedNestedExecutions(session.getContext());

        printConsoleHeaderTop(System.out, "NEXIAL INTERACTIVE", FILLER);
        printHeaderLine(System.out, HEADER_EXECUTED, formatExecutionMeta(scenarioSummary.getStartTime()));
        printHeaderLine(System.out, HEADER_SCRIPT, formatTestScript(session.getScript()));
        printHeaderLine(System.out, HEADER_SCENARIO, scenarioSummary.getName());

        Throwable error = session.getException();
        if (error != null) { printHeaderLine(System.out, HEADER_EXCEPTION, error.getMessage()); }

        printConsoleSectionSeparator(System.out, FILLER_MENU);

        List<ExecutionSummary> activitySummaries = scenarioSummary.getNestedExecutions();
        activitySummaries.forEach(activity -> {
            long endTime = activity.getEndTime();
            long startTime = activity.getStartTime();
            String timeSpan = DateUtility.formatLongDate(startTime) + " - " + DateUtility.formatLongDate(endTime);
            String duration = DateUtility.formatStopWatchTime(endTime - startTime);

            String header = activity.getExecutionLevel() == STEP ? HEADER_STEPS : HEADER_ACTIVITY;
            printHeaderLine(System.out, header, activity.getName());
            printHeaderLine(System.out, SUB1_HEADER_TIMESPAN, timeSpan);
            printHeaderLine(System.out, SUB1_HEADER_DURATION, duration);
            printStats(activity.getTotalSteps(), activity.getPassCount(), activity.getFailCount());

            Map<String, String> refs = activity.getReferenceData();
            if (MapUtils.isNotEmpty(refs)) {
                refs.forEach((key, value) -> {
                    String refKey = SUB1_START + StringUtils.rightPad("(" + key + ")", REF_MAX_LENGTH, " ") + SUB2_END;
                    printHeaderLine(System.out, refKey, value);
                });
            }
        });

        printConsoleSectionSeparator(System.out, FILLER_MENU);

        long endTime = scenarioSummary.getEndTime();
        long startTime = scenarioSummary.getStartTime();
        String timeSpan = DateUtility.formatLongDate(startTime) + " - " + DateUtility.formatLongDate(endTime);
        String duration = DateUtility.formatStopWatchTime(endTime - startTime);
        printHeaderLine(System.out, HEADER_SUMMARY, scenarioSummary.getName());
        printHeaderLine(System.out, SUB1_HEADER_TIMESPAN, timeSpan);
        printHeaderLine(System.out, SUB1_HEADER_DURATION, duration);
        printHeaderLine(System.out, SUB1_HEADER_ITERATION, session.getIteration() + "");
        printStats(scenarioSummary.getTotalSteps(), scenarioSummary.getPassCount(), scenarioSummary.getFailCount());

        printConsoleHeaderBottom(System.out, FILLER);
    }

    protected static void printStats(int total, int pass, int fail) {
        String headerLine = MARGIN_LEFT + SUB1_HEADER_STATS;

        int leftMargin = PROMPT_LINE_WIDTH - SUB1_HEADER_STATS.length() - MARGIN_LEFT.length() - MARGIN_RIGHT.length();

        Printer printer = new Printer.Builder(Types.TERM).timestamping(false).build();
        printer.print(headerLine);

        ColoredPrinter cprinter = new ColoredPrinter.Builder(1, false).foreground(WHITE)
                                                                      .background(NONE)
                                                                      .timestamping(false)
                                                                      .build();
        cprinter.print(total, BOLD, WHITE, NONE);
        cprinter.print(MULTI_SEP, Attribute.NONE, WHITE, NONE);
        cprinter.print(pass, BOLD, GREEN, NONE);
        cprinter.print(MULTI_SEP, Attribute.NONE, WHITE, NONE);
        cprinter.print(fail, BOLD, fail < 1 ? WHITE : RED, NONE);
        cprinter.clear();

        printer.print(StringUtils.repeat(" ", leftMargin - (total + MULTI_SEP + pass + MULTI_SEP + fail).length()));
        printer.println(MARGIN_RIGHT);
    }

    protected static void showHelp(InteractiveSession session) {
        // tokens for template search-n-replace
        Map<String, String> tokens = new HashMap<>();
        tokens.put("username", USER_NAME);
        tokens.put("host", EnvUtils.getHostName() + " (" + OS_NAME + " " + OS_VERSION + ")");
        tokens.put("sessionStartDT", DateUtility.formatLongDate(session.getStartTime()));
        tokens.put("cmd.script", CMD_SET_SCRIPT);
        tokens.put("cmd.data", CMD_SET_DATA);
        tokens.put("cmd.scenario", CMD_SET_SCENARIO);
        tokens.put("cmd.iteration", CMD_SET_ITER);
        tokens.put("cmd.activity", CMD_SET_ACTIVITY);
        tokens.put("cmd.steps", CMD_SET_STEPS);
        tokens.put("cmd.reloadscript", CMD_RELOAD_SCRIPT);
        tokens.put("cmd.reloaddata", CMD_RELOAD_DATA);
        tokens.put("cmd.reloadmenu", CMD_RELOAD_MENU);
        tokens.put("cmd.inspect", CMD_INSPECT);
        tokens.put("cmd.execute", CMD_RUN);
        tokens.put("cmd.help", CMD_HELP);
        tokens.put("cmd.quit", CMD_EXIT);
        tokens.put("script", session.getScript());
        tokens.put("scenario", session.getScenario());
        tokens.put("activities", TextUtils.toString(session.getActivities(), ", "));
        tokens.put("steps", TextUtils.toString(session.getSteps(), ", "));

        printConsoleHeaderTop(System.out, "NEXIAL INTERACTIVE HELP", FILLER_MENU);
        printHeaderLine(System.out, "INTRO ", resolveContent("intro", tokens));
        printHeaderLine(System.out, "NOTE- ", resolveContent("notes.1", tokens));
        printHeaderLine(System.out, "    - ", resolveContent("notes.2", tokens));
        printHeaderLine(System.out, "    - ", resolveContent("notes.3", tokens));
        printHeaderLine(System.out, "    - ", resolveContent("notes.4", tokens));

        printConsoleSectionSeparator(System.out, "~~informational", FILLER_MENU);
        printHeaderLine(System.out, HEADER_SESSION, resolveContent("session", tokens));
        printHeaderLine(System.out, HEADER_SCRIPT, resolveContent("script", tokens));
        printHeaderLine(System.out, HEADER_SCENARIO, resolveContent("scenario", tokens));
        printHeaderLine(System.out, HEADER_ACTIVITY, resolveContent("activity", tokens));
        printHeaderLine(System.out, HEADER_STEPS, resolveContent("steps", tokens));

        printConsoleSectionSeparator(System.out, "~~options", FILLER_MENU);
        printHeaderLine(System.out, CMD_START + CMD_SET_SCRIPT + " <script>   " + CMD_END,
                        resolveContent("command.script", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_DATA + " <data file>" + CMD_END,
                        resolveContent("command.data", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_SCENARIO + " <scenario> " + CMD_END,
                        resolveContent("command.scenario", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_ITER + " <iteration>" + CMD_END,
                        resolveContent("command.iteration", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_ACTIVITY + " <activity> " + CMD_END,
                        resolveContent("command.activity", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_STEPS + " <step>     " + CMD_END,
                        resolveContent("command.steps", tokens));
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_SCRIPT + "            " + CMD_END,
                        resolveContent("command.reloadscript", tokens));
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_DATA + "            " + CMD_END,
                        resolveContent("command.reloaddata", tokens));
        printHeaderLine(System.out, " (" + CMD_RELOAD_MENU + ")eload      " + CMD_END,
                        resolveContent("command.reloadmenu", tokens));
        printHeaderLine(System.out, " (" + CMD_HELP + ")elp        " + CMD_END, resolveContent("command.help", tokens));
        printHeaderLine(System.out, "e(" + CMD_RUN + ")ecute      " + CMD_END, resolveContent("command.run", tokens));
        printHeaderLine(System.out, " (" + CMD_INSPECT + ")nspect     " + CMD_END,
                        resolveContent("command.inspect", tokens));
        printHeaderLine(System.out, " (" + CMD_EXIT + ")uit        " + CMD_END, resolveContent("command.exit", tokens));

        printConsoleHeaderBottom(System.out, FILLER_MENU);
        System.out.println();
        System.out.println();
    }

    protected static String resolveContent(String templateKey, Map<String, String> tokens) {
        if (StringUtils.isBlank(templateKey)) { return " "; }

        String template = HELP_TEMPLATE.getProperty(templateKey);
        if (MapUtils.isEmpty(tokens)) { return template; }

        Set<String> keys = tokens.keySet();
        for (String token : keys) { template = StringUtils.replace(template, "${" + token + "}", tokens.get(token)); }
        return template;
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

    static {
        try {
            HELP_TEMPLATE = ResourceUtils.loadProperties(HELP_TEMPLATE_RESOURCE);
        } catch (IOException e) {
            throw new RuntimeException("Help resource cannot be loaded via '" + HELP_TEMPLATE_RESOURCE +
                                       "': " + e.getMessage(),
                                       e);
        }
    }
}
