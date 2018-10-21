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

package org.nexial.core.utils;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.nexial.commons.logging.LogbackUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.ExecutionEventListener;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestCase;
import org.nexial.core.model.TestStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import static org.nexial.core.NexialConst.FlowControls.*;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;

/**
 * helper class to log to console.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class ConsoleUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleUtils.class);
    private static final List<Pair<Level, String>> PRE_EXEC_READY_BUFFER = new ArrayList<>();
    private static final int PROMPT_LINE_WIDTH = 80;
    private static final String HDR_START = "";
    private static final String HDR_END = "";
    private static final String META_START = "[";
    private static final String META_END = "]";

    private ConsoleUtils() { }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String msg) {
        if (System.out == null) { throw new RuntimeException("System.out is null!"); }
        System.out.println(DateUtility.getCurrentTimeForLogging() + " >> " + msg);
        logAs(INFO, msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String msg) {
        if (System.err == null) { throw new RuntimeException("System.err is null!"); }
        System.err.println(DateUtility.getCurrentTimeForLogging() + " >> " + msg);
        logAs(ERROR, msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String id, String msg) {
        assert StringUtils.isNotBlank(id);
        if (System.out == null) { throw new RuntimeException("System.out is null!"); }
        System.out.println(DateUtility.getCurrentTimeForLogging() + " >> [" + id + "] " + msg);
        logAs(INFO, "[" + id + "] " + msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg) {
        assert StringUtils.isNotBlank(id);
        if (System.err == null) { throw new RuntimeException("System.err is null!"); }
        System.err.println(DateUtility.getCurrentTimeForLogging() + " >> [" + id + "] " + msg);
        logAs(ERROR, "[" + id + "] " + msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg, Throwable e) {
        error(id, msg);
        e.printStackTrace(System.err);
        System.err.print("\n\n");
        logAs(ERROR, "[" + id + "] " + msg + e.getMessage());
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void showMissingLibraryError(String message) {
        System.out.println();
        System.out.println();
        System.out.println("/" + StringUtils.repeat("!", PROMPT_LINE_WIDTH - 2) + "\\");
        System.out.println(" ! ERROR:");
        System.out.println(" !   " + StringUtils.replace(message, "\n", "\n !   "));
        System.out.println("\\" + StringUtils.repeat("!", PROMPT_LINE_WIDTH - 2) + "/");
        System.out.println();
        System.out.println();
    }

    public static void errorBeforeTerminate(String message) {
        logAs(ERROR, message);

        char filler = '!';
        printConsoleHeaderTop(System.err, " ERROR OCCURRED ", filler);
        printHeaderLine(System.err, "ERROR: ", message);
        printConsoleHeaderBottom(System.err, filler);
        System.err.println();
    }

    public static void pause(ExecutionContext context, String msg) {
        if (!isPauseReady()) { return; }

        ExecutionEventListener listener = context.getExecutionEventListener();
        listener.onPause();
        doPause(context, msg);
        listener.afterPause();
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void doPause(ExecutionContext context, String msg) {
        System.out.println();
        System.out.println(msg);

        if (context != null && context.getBooleanData(OPT_INSPECT_ON_PAUSE, DEF_INSPECT_ON_PAUSE)) {
            // inspect mode
            System.out.println("/------------------------------------------------------------------------------\\");
            System.out.println("|" + centerPrompt("INSPECT ON PAUSE", PROMPT_LINE_WIDTH - 2) + "|");
            System.out.println("\\------------------------------------------------------------------------------/");
            System.out.println("> Enter statement to inspect.  Press ENTER or " + RESUME_FROM_PAUSE + " to resume " +
                               "execution\n");
            System.out.print("inspect-> ");
            Scanner in = new Scanner(System.in);
            String input = in.nextLine();

            while (StringUtils.isNotBlank(input) && !StringUtils.equals(StringUtils.trim(input), RESUME_FROM_PAUSE)) {
                // if (StringUtils.trim(input), INTERACTIVE)
                System.out.println(context.replaceTokens(input));
                System.out.println();
                System.out.print("inspect-> ");
                input = in.nextLine();
            }
        } else {
            System.out.println("\t>>> Press ENTER to continue... ");
            Scanner in = new Scanner(System.in);
            in.nextLine();
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static String pauseForStep(ExecutionContext context, String instructions) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return null; }

        ExecutionEventListener listener = context.getExecutionEventListener();
        listener.onPause();

        printHeader(HDR_START + "PERFORM ACTION" + HDR_END, context);
        printStepPrompt(instructions);

        System.out.println("> When complete, enter your comment or press ENTER to continue ");
        String comment = new Scanner(System.in).nextLine();

        listener.afterPause();

        return comment;
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static List<String> pauseToValidate(ExecutionContext context,
                                               String instructions,
                                               String possibleResponses) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return null; }

        ExecutionEventListener listener = context.getExecutionEventListener();
        listener.onPause();

        printHeader(HDR_START + "VALIDATION" + HDR_END, context);
        printStepPrompt(instructions);

        List<String> responses = new ArrayList<>();
        if (StringUtils.isBlank(possibleResponses)) {
            responses.add("Y");
            responses.add("N");
        } else {
            responses.addAll(Arrays.asList(StringUtils.split(possibleResponses, context.getTextDelim())));
        }

        System.out.printf("> %s: ", responses);
        String input = new Scanner(System.in).nextLine();

        System.out.print("> Comment: ");
        String comment = new Scanner(System.in).nextLine();

        listener.afterPause();

        return Arrays.asList(input, comment);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static String pauseForInput(ExecutionContext context, String prompt) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return null; }

        ExecutionEventListener listener = context.getExecutionEventListener();
        listener.onPause();

        printHeader(HDR_START + "OBSERVATION" + HDR_END, context);
        printStepPrompt(prompt);

        System.out.print("> ");

        String input = new Scanner(System.in).nextLine();

        listener.afterPause();

        return input;
    }

    public static String centerPrompt(String prompt, int width) {
        if (StringUtils.isBlank(prompt)) { return StringUtils.repeat(" ", width); }

        String paddingSpaces = StringUtils.repeat(" ", (width - prompt.length()) / 2);
        String newPrompt = paddingSpaces + prompt + paddingSpaces;
        if (newPrompt.length() > width) { newPrompt = StringUtils.removeEnd(newPrompt, " "); }
        if (newPrompt.length() < width) { newPrompt += " "; }
        return newPrompt;
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static void printStepPrompt(String instructions) {
        Arrays.stream(StringUtils.split(instructions, "\n"))
              .forEach(step -> System.out.println("> " + StringUtils.removeEnd(step, "\r")));
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static void printHeader(String header, ExecutionContext context) {
        TestStep testStep = context.getCurrentTestStep();
        TestCase activity = testStep.getTestCase();
        String activityName = activity.getName();
        String scenarioName = activity.getTestScenario().getName();

        char filler = '-';
        printConsoleHeaderTop(System.out, header, filler);
        printHeaderLine(System.out, META_START + "scenario" + META_END + " ", scenarioName);
        printHeaderLine(System.out, META_START + "activity" + META_END + " ", activityName);
        printHeaderLine(System.out, META_START + "row/step" + META_END + " ", (testStep.getRowIndex() + 1) + "");
        printConsoleHeaderBottom(System.out, filler);
    }

    private static void printConsoleHeaderTop(PrintStream out, String header, char filler) {
        int fillerLength = PROMPT_LINE_WIDTH - 2 - header.length() - 1;
        String filler1 = StringUtils.repeat(filler, fillerLength / 2);
        String filler2 = StringUtils.repeat(filler, fillerLength - filler1.length());

        out.println();
        out.println("/-" + filler1 + header + filler2 + "\\");
    }

    private static void printConsoleHeaderBottom(PrintStream out, char filler) {
        out.println("\\" + StringUtils.repeat(filler, PROMPT_LINE_WIDTH - 2) + "/");
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static void printHeaderLine(PrintStream out, String header1, String header2) {
        // garbage in, garbage out
        if (StringUtils.isBlank(header1) || StringUtils.isBlank(header2)) { return; }

        header2 = StringUtils.trim(header2);

        String headerLine1 = "| " + header1 + header2;
        if (StringUtils.length(headerLine1) < PROMPT_LINE_WIDTH) {
            out.println(headerLine1 + StringUtils.repeat(" ", PROMPT_LINE_WIDTH - headerLine1.length() - 1) + "|");
            return;
        }

        // longer than 1 line
        boolean firstLine = true;
        // `-3` because we have `| ` in the beginning and `|` at the end of each line
        int leftMargin = PROMPT_LINE_WIDTH - header1.length() - 3;

        do {
            String portion =
                (StringUtils.length(header2) <= leftMargin) ?
                header2 :
                StringUtils.trim(StringUtils.substringBeforeLast(StringUtils.substring(header2, 0, leftMargin), " "));
            String headerLine = "| " + ((firstLine ? header1 : StringUtils.repeat(" ", header1.length())) + portion);
            out.println(headerLine + StringUtils.repeat(" ", PROMPT_LINE_WIDTH - headerLine.length() - 1) + "|");
            firstLine = false;

            header2 = StringUtils.trim(StringUtils.substringAfter(header2, portion));
        } while (StringUtils.isNotBlank(header2));
    }

    private static boolean isPauseReady() {
        if (CheckUtils.isRunningInZeroTouchEnv()) {
            log("SKIPPING pause-for-step since Nexial is currently running in non-interactive environment");
            return false;
        } else {
            return true;
        }
    }

    private static void logAs(Level logLevel, String message) {
        if (!LogbackUtils.isExecLoggingReady()) {
            PRE_EXEC_READY_BUFFER.add(new ImmutablePair<>(logLevel, message));
            return;
        }

        flushPreExecReadyBuffer();
        sendToLogger(logLevel, message);
    }

    private static void flushPreExecReadyBuffer() {
        if (CollectionUtils.isEmpty(PRE_EXEC_READY_BUFFER)) { return; }
        synchronized (PRE_EXEC_READY_BUFFER) {
            PRE_EXEC_READY_BUFFER.forEach(logPair -> sendToLogger(logPair.getKey(), "(DELAYED) " + logPair.getValue()));
            PRE_EXEC_READY_BUFFER.clear();
        }
    }

    private static void sendToLogger(Level logLevel, String message) {
        switch (logLevel) {
            case TRACE:
                if (LOGGER.isTraceEnabled()) { LOGGER.trace(message); }
                return;
            case DEBUG:
                if (LOGGER.isDebugEnabled()) { LOGGER.debug(message); }
                return;
            case INFO:
                if (LOGGER.isInfoEnabled()) { LOGGER.info(message); }
                return;
            case WARN:
                LOGGER.warn(message);
                return;
            case ERROR:
                LOGGER.error(message);
                return;
            default:
                if (LOGGER.isDebugEnabled()) { LOGGER.debug(message); }
        }
    }
}
