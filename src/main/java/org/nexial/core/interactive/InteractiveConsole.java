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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.model.TestScenario;

import com.diogonunes.jcdp.bw.Printer;
import com.diogonunes.jcdp.bw.Printer.Builder;
import com.diogonunes.jcdp.bw.Printer.Types;
import com.diogonunes.jcdp.color.ColoredPrinter;
import com.diogonunes.jcdp.color.api.Ansi.Attribute;
import com.diogonunes.jcdp.color.api.Ansi.BColor;
import com.diogonunes.jcdp.color.api.Ansi.FColor;

import static com.diogonunes.jcdp.color.api.Ansi.Attribute.*;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.core.interactive.InteractiveConsole.Commands.*;
import static org.nexial.core.interactive.InteractiveConsole.MenuIdentifier.digit;
import static org.nexial.core.interactive.InteractiveConsole.MenuIdentifier.uppercase;
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
    private static final String CMD_START = "  ";
    private static final String CMD_END = " ";
    private static final char FILLER_MENU = '~';

    private static final String SUB1_HEADER_TIMESPAN = SUB1_START + "timespan       " + SUB2_END;
    private static final String SUB1_HEADER_DURATION = SUB1_START + "duration       " + SUB2_END;
    private static final String SUB1_HEADER_ITERATION = SUB1_START + "iteration      " + SUB2_END;
    private static final String SUB1_HEADER_STATS = SUB1_START + "total/pass/fail" + SUB2_END;

    private static final int MAX_LENGTH_BASE = PROMPT_LINE_WIDTH - MARGIN_LEFT.length() - MARGIN_RIGHT.length();
    private static final int MAX_LENGTH_SCRIPT = MAX_LENGTH_BASE - HEADER_SCRIPT.length();
    private static final int MAX_LENGTH_REF = SUB1_HEADER_STATS.length() - SUB1_START.length() - SUB2_END.length();
    private static final int LEFT_MARGIN_L2_VAL = MAX_LENGTH_BASE - SUB1_HEADER_STATS.length();
    private static final int LEFT_MARGIN_L3_HEADER = MAX_LENGTH_BASE - SUB1_START.length();

    private static final Printer CONSOLE = new Builder(Types.TERM).timestamping(false).build();
    private static final ColoredPrinter CPRINTER = new ColoredPrinter.Builder(1, false).timestamping(false).build();

    private static final String HELP_TEMPLATE_RESOURCE =
        StringUtils.replace(InteractiveConsole.class.getPackage().getName(), ".", "/") +
        "/nexial-interactive-help.properties";
    private static final Properties HELP_TEMPLATE;

    enum MenuIdentifier {uppercase, digit}

    class Commands {
        static final String CMD_SET_SCRIPT = "1";
        static final String CMD_SET_DATA = "2";
        static final String CMD_SET_SCENARIO = "3";
        static final String CMD_SET_ITER = "4";
        static final String CMD_SET_ACTIVITY = "5";
        static final String CMD_SET_STEPS = "6";
        static final String CMD_RELOAD_SCRIPT = "7";
        static final String CMD_RELOAD_DATA = "8";

        static final String CMD_RELOAD_MENU = "R";
        static final String CMD_RUN = "X";
        static final String CMD_INSPECT = "I";
        static final String CMD_ALL_STEP = "A";
        static final String CMD_OPEN_SCRIPT = "S";
        static final String CMD_OPEN_DATA = "D";
        static final String CMD_HELP = "H";
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

        printConsoleSectionSeparator(System.out, "~~options", FILLER_MENU);
        printMenu(CMD_START, digit, CMD_SET_SCRIPT + " <script>   " + CMD_END + "assign test script");
        printMenu(CMD_START, digit, CMD_SET_DATA + " <data file>" + CMD_END + "assign data file");
        printMenu(CMD_START, digit, CMD_SET_SCENARIO + " <scenario> " + CMD_END + "assign scenario");
        printMenu(CMD_START, digit, CMD_SET_ITER + " <iteration>" + CMD_END + "assign iteration");
        printMenu(CMD_START, digit, CMD_SET_ACTIVITY + " <activity> " + CMD_END + "assign activities; clears assigned test steps");
        printMenu(CMD_START, digit, CMD_SET_STEPS + " <step>     " + CMD_END + "assign steps; clears assigned activities");
        printMenu(CMD_START, digit, CMD_RELOAD_SCRIPT + "            " + CMD_END + "reload assigned test script");
        printMenu(CMD_START, digit, CMD_RELOAD_DATA + "            " + CMD_END + "reload assigned data file");
        printMenu(CMD_START + "action       " + CMD_END, uppercase,
                  StringUtils.rightPad(CMD_RELOAD_MENU + "eload menu", 15),
                  StringUtils.rightPad("e" + CMD_RUN + "ecute", 18),
                  StringUtils.rightPad(CMD_INSPECT + "nspect", 12),
                  StringUtils.rightPad(CMD_ALL_STEP + "ll steps", 12));
        printMenu(CMD_START + "             " + CMD_END, uppercase,
                  StringUtils.rightPad(CMD_OPEN_SCRIPT + "cript open", 15),
                  StringUtils.rightPad(CMD_OPEN_DATA + "ata file open", 18),
                  StringUtils.rightPad(CMD_HELP + "elp", 12),
                  StringUtils.rightPad(CMD_EXIT + "uit", 12));
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
            printStats(activity);
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
        printStats(scenarioSummary);

        ExecutionContext context = session.getContext();
        printReferenceData("script reference data", context.gatherScriptReferenceData());
        printReferenceData("scenario reference data", scenarioSummary.getReferenceData());

        printConsoleHeaderBottom(System.out, FILLER);
    }

    protected static void printMenu(String prefix, MenuIdentifier menuIdentifier, String... menus) {
        if (ArrayUtils.isEmpty(menus)) { return; }

        int charPrinted = 0;

        CONSOLE.print(MARGIN_LEFT);
        CONSOLE.print(prefix);
        charPrinted += MARGIN_LEFT.length() + prefix.length();

        String regex = menuIdentifier == uppercase ? "[A-Z]" : "[0-9]";
        for (String menu : menus) {
            charPrinted += menu.length();
            String key = RegexUtils.firstMatches(menu, regex);
            if (StringUtils.isBlank(key)) {
                CONSOLE.print(menu);
            } else {
                String beforeKey = StringUtils.substringBefore(menu, key);
                if (StringUtils.isNotEmpty(beforeKey)) { CONSOLE.print(beforeKey); }

                CPRINTER.print(key, UNDERLINE, FColor.BLACK, BColor.WHITE);
                CPRINTER.clear();

                String afterKey = StringUtils.substringAfter(menu, key);
                if (StringUtils.isNotEmpty(afterKey)) { CONSOLE.print(afterKey); }
            }
        }

        CONSOLE.print(StringUtils.repeat(' ', PROMPT_LINE_WIDTH - charPrinted - 1));
        CONSOLE.println(MARGIN_RIGHT);
    }

    protected static void printReferenceData(String header, Map<String, String> refs) {
        if (MapUtils.isEmpty(refs)) { return; }

        String header1 = "[" + header + "]";

        CONSOLE.print(MARGIN_LEFT);
        CONSOLE.print(SUB1_START);
        CPRINTER.print(header1, UNDERLINE, FColor.CYAN, BColor.NONE);
        CPRINTER.clear();

        int fillerLength = LEFT_MARGIN_L3_HEADER - header1.length();
        CONSOLE.print(StringUtils.repeat(" ", fillerLength));
        CONSOLE.println(MARGIN_RIGHT);

        refs.forEach((key, value) -> {
            String refKey = SUB1_START + StringUtils.rightPad("(" + key + ")", MAX_LENGTH_REF, " ") + SUB2_END;
            printHeaderLine(System.out, refKey, value);
        });
    }

    protected static void printStats(ExecutionSummary executionSummary) {
        int totalCount = executionSummary.getTotalSteps();
        int failCount = executionSummary.getFailCount();
        int skipCount = totalCount - executionSummary.getExecuted();

        String total = StringUtils.leftPad(totalCount + "", 3);
        String pass = StringUtils.leftPad(executionSummary.getPassCount() + "", 3);
        String fail = StringUtils.leftPad(failCount + "", 3);
        String skipped = StringUtils.leftPad(skipCount + "", 3);

        String headerLine = MARGIN_LEFT + SUB1_HEADER_STATS;
        String skippedStat = (skipCount > 0 ? "  (SKIPPED:" + skipped + ")" : "");
        String statDetails = total + MULTI_SEP + pass + MULTI_SEP + fail + skippedStat;

        CONSOLE.print(headerLine);
        CPRINTER.print(total, BOLD, FColor.WHITE, BColor.NONE);
        CPRINTER.print(MULTI_SEP, Attribute.NONE, FColor.WHITE, BColor.NONE);
        CPRINTER.print(pass, BOLD, FColor.GREEN, BColor.NONE);
        CPRINTER.print(MULTI_SEP, Attribute.NONE, FColor.WHITE, BColor.NONE);
        CPRINTER.print(fail, BOLD, (failCount < 1) ? FColor.WHITE: FColor.RED, BColor.NONE);
        if (skipCount > 0) { CPRINTER.print(skippedStat, CLEAR, FColor.YELLOW, BColor.NONE); }
        CPRINTER.clear();

        int fillerLength = LEFT_MARGIN_L2_VAL - statDetails.length();
        CONSOLE.print(StringUtils.repeat(" ", fillerLength));
        CONSOLE.println(MARGIN_RIGHT);
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
        printHeaderLine(System.out, CMD_START + CMD_SET_SCRIPT + " <script>   " + CMD_END, resolveContent("command.script", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_DATA + " <data file>" + CMD_END, resolveContent("command.data", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_SCENARIO + " <scenario> " + CMD_END, resolveContent("command.scenario", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_ITER + " <iteration>" + CMD_END, resolveContent("command.iteration", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_ACTIVITY + " <activity> " + CMD_END, resolveContent("command.activity", tokens));
        printHeaderLine(System.out, CMD_START + CMD_SET_STEPS + " <step>     " + CMD_END, resolveContent("command.steps", tokens));
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_SCRIPT + "            " + CMD_END, resolveContent("command.reloadscript", tokens));
        printHeaderLine(System.out, CMD_START + CMD_RELOAD_DATA + "            " + CMD_END, resolveContent("command.reloaddata", tokens));
        printHeaderLine(System.out, " (" + CMD_RELOAD_MENU + ")eload      " + CMD_END, resolveContent("command.reloadmenu", tokens));
        printHeaderLine(System.out, "e(" + CMD_RUN + ")ecute      " + CMD_END, resolveContent("command.run", tokens));
        printHeaderLine(System.out, " (" + CMD_INSPECT + ")nspect     " + CMD_END, resolveContent("command.inspect", tokens));
        printHeaderLine(System.out, " (" + CMD_ALL_STEP + ")ll steps   " + CMD_END, resolveContent("command.allstep", tokens));
        printHeaderLine(System.out, " (" + CMD_OPEN_SCRIPT + ")cript open " + CMD_END, resolveContent("command.openscript", tokens));
        printHeaderLine(System.out, " (" + CMD_OPEN_DATA + ")ata file..." + CMD_END, resolveContent("command.opendata", tokens));
        printHeaderLine(System.out, " (" + CMD_HELP + ")elp        " + CMD_END, resolveContent("command.help", tokens));
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
        if (StringUtils.length(testScript) > MAX_LENGTH_SCRIPT) {
            testScript = "..." + testScript.substring(testScript.length() - MAX_LENGTH_SCRIPT + 3);
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
            throw new RuntimeException("Help resource cannot be loaded via '" + HELP_TEMPLATE_RESOURCE + "': " +
                                       e.getMessage(), e);
        }
    }
}
