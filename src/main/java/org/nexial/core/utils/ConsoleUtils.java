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

package org.nexial.core.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.nexial.commons.logging.LogbackUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.NexialConst.RB;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionEventListener;
import org.nexial.core.model.TestCase;
import org.nexial.core.model.TestStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import javax.annotation.Nonnull;

import static org.nexial.core.NexialConst.Data.QUIET;
import static org.nexial.core.NexialConst.Exec.INSPECT_END;
import static org.nexial.core.NexialConst.Exec.INSPECT_RESUME;
import static org.nexial.core.NexialConst.FlowControls.OPT_INSPECT_ON_PAUSE;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;

/**
 * helper class to log to console.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class ConsoleUtils {
    public static final char FILLER = '-';

    public static final String MARGIN_LEFT = "| ";
    public static final String MARGIN_RIGHT = "|";
    public static final String HDR_START = "";
    public static final String HDR_END = "";
    public static final String META_START = "[";
    public static final String META_END = "] ";
    public static final String MULTI_SEP = " / ";

    public static final int PROMPT_LINE_WIDTH = 100;
    public static final int PRINTABLE_LENGTH = PROMPT_LINE_WIDTH - MARGIN_LEFT.length() - MARGIN_RIGHT.length();

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleUtils.class);
    private static final List<Pair<Level, String>> PRE_EXEC_READY_BUFFER = new ArrayList<>();

    public enum LogType { LOG, ERROR }

    private ConsoleUtils() { }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String msg) { log(null, msg); }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String id, String msg) {
        if (System.out == null) { throw new RuntimeException("System.out is null!"); }

        String label = StringUtils.isNotBlank(id) ? "[" + id + "] " : "";
        if (!BooleanUtils.toBoolean(System.getProperty(QUIET))) {
            System.out.println(DateUtility.getCurrentTimeForLogging() + " >> " + label + msg);
        }
        logAs(INFO, label + msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String id, String format, Object... args) {
        if (System.out == null) { throw new RuntimeException("System.out is null!"); }
        log(id, String.format(format, args));
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String msg) { error(null, msg); }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg) {
        if (System.err == null) { throw new RuntimeException("System.err is null!"); }

        String label = StringUtils.isNotBlank(id) ? "[" + id + "] " : "";
        System.err.println(DateUtility.getCurrentTimeForLogging() + " >> " + label + msg);
        logAs(ERROR, label + msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String format, Object... args) {
        if (System.err == null) { throw new RuntimeException("System.err is null!"); }
        try {
            error(id, String.format(format, args));
        } catch (IllegalArgumentException e) {
            error(id, format + " " + ArrayUtils.toString(args));
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg, Throwable e) {
        error(id, msg);
        if (e != null) {
            e.printStackTrace(System.err);
            System.err.print(NL + NL);
            logAs(ERROR, "[" + id + "] " + msg + e.getMessage());
        }
    }

    public static void log(LogType type, String id, String format, Object... args) {
        switch (type) {
            case LOG:
                log(id, String.format(format, args));
            case ERROR:
                error(id, String.format(format, args));
        }
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
        pauseAndInspect(context,
                        msg,
                        context != null &&
                        context.getBooleanData(OPT_INSPECT_ON_PAUSE, getDefaultBool(OPT_INSPECT_ON_PAUSE)));
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void pauseAndInspect(ExecutionContext context, String msg, boolean inspect) {
        System.out.println();
        System.out.println(msg);

        if (inspect) {
            // inspect mode
            System.out.print(RB.Console.text("inspectPrompt"));
            Scanner in = new Scanner(System.in);
            String input = in.nextLine();

            while (StringUtils.isNotBlank(input) && !StringUtils.equals(StringUtils.trim(input), INSPECT_RESUME)) {
                if (StringUtils.equals(StringUtils.trim(input), INSPECT_END)) {
                    context.setEndImmediate(true);
                    break;
                }

                System.out.println(context.replaceTokens(input, true));
                System.out.println();
                System.out.print("inspect-> ");
                input = in.nextLine();
            }
        } else {
            System.out.print(RB.Console.text("continuePrompt"));
            // System.out.println("\t>>> Press ENTER to continue... ");
            Scanner in = new Scanner(System.in);
            in.nextLine();
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static String pauseForStep(ExecutionContext context, String instructions, String header) {
        return pauseForStep(context, instructions, header, false);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static String pauseForStep(ExecutionContext context,
                                      String instructions,
                                      String header,
                                      boolean interrupted) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return null; }

        ExecutionEventListener listener = context.getExecutionEventListener();
        listener.onPause();

        printStepHeader(HDR_START + header + HDR_END, context);
        printStepPrompt(instructions);

        System.out.println("> When complete, enter your comment or press ENTER to continue ");
        String comment = readInput(interrupted);

        listener.afterPause();

        return comment;
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static List<String> pauseToValidate(ExecutionContext context,
                                               String instructions,
                                               String possibleResponses,
                                               String header) {
        return pauseToValidate(context, instructions, possibleResponses, header, false);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static List<String> pauseToValidate(ExecutionContext context,
                                               String instructions,
                                               String possibleResponses,
                                               String header,
                                               boolean interrupted) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return null; }

        ExecutionEventListener listener = context.getExecutionEventListener();
        listener.onPause();

        printStepHeader(HDR_START + header + HDR_END, context);
        printStepPrompt(instructions);

        List<String> responses = new ArrayList<>();
        if (StringUtils.isBlank(possibleResponses)) {
            responses.add("Y");
            responses.add("N");
        } else {
            responses.addAll(Arrays.asList(StringUtils.split(possibleResponses, context.getTextDelim())));
        }

        System.out.printf("> %s: ", responses);
        String input = readInput(interrupted);

        System.out.print("> Comment: ");
        String comment = readInput(interrupted);

        listener.afterPause();

        return Arrays.asList(input, comment);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static String pauseForInput(ExecutionContext context, String prompt, String header) {
        return pauseForInput(context, prompt, header, false);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static String pauseForInput(ExecutionContext context, String prompt, String header, boolean interrupted) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return null; }

        ExecutionEventListener listener = null;
        if (context != null) {
            listener = context.getExecutionEventListener();
            listener.onPause();
            printStepHeader(HDR_START + header + HDR_END, context);
        }

        printStepPrompt(prompt);
        System.out.print("> ");
        String input = readInput(interrupted);

        if (listener != null) { listener.afterPause(); }

        return input;
    }

    private static String readInput(boolean interrupted) {
        return interrupted ? InterruptableSystemIn.getNextLine() : new Scanner(System.in).nextLine();
    }

    public static String centerPrompt(String prompt, int width) {
        if (StringUtils.isBlank(prompt)) { return StringUtils.repeat(" ", width); }

        String paddingSpaces = StringUtils.repeat(" ", (width - prompt.length()) / 2);
        String newPrompt = paddingSpaces + prompt + paddingSpaces;
        if (newPrompt.length() > width) { newPrompt = StringUtils.removeEnd(newPrompt, " "); }
        if (newPrompt.length() < width) { newPrompt += " "; }
        return newPrompt;
    }

    public static void printConsoleHeaderTop(PrintStream out, String header, char filler) {
        out.println(toConsoleHeaderTop(header, filler));
    }

    public static void printConsoleHeaderBottom(PrintStream out, char filler) {
        out.println(toConsoleHeaderBottom(filler));
    }

    @Nonnull
    public static String toConsoleHeaderTop(String header, char filler) {
        // `-2` because we are adding filler twice later
        int fillerLength = PROMPT_LINE_WIDTH - 2 - header.length() - 1;
        String filler1 = StringUtils.repeat(filler, fillerLength / 2);
        String filler2 = StringUtils.repeat(filler, fillerLength - filler1.length());
        return "\n/" + filler + filler1 + header + filler2 + "\\";
    }

    @Nonnull
    public static String toConsoleHeaderBottom(char filler) {
        return "\\" + StringUtils.repeat(filler, PROMPT_LINE_WIDTH - 2) + "/";
    }

    public static void printConsoleSectionSeparator(PrintStream out, char filler) {
        printConsoleSectionSeparator(out, null, filler);
    }

    public static void printConsoleSectionSeparator(PrintStream out, String prefix, char filler) {
        out.print(MARGIN_LEFT);
        if (StringUtils.isNotEmpty(prefix)) {
            out.print(prefix + StringUtils.repeat(filler, PRINTABLE_LENGTH - prefix.length()));
        } else {
            out.print(StringUtils.repeat(filler, PRINTABLE_LENGTH));
        }
        out.println(MARGIN_RIGHT);
    }

    public static void printHeaderLine(PrintStream out, String header1, List<String> headers2) {
        if (CollectionUtils.isEmpty(headers2)) { return; }
        printHeaderLine(out, header1, headers2.toArray(new String[0]));
    }

    public static void printHeaderLine(PrintStream out, String header1, String... headers2) {
        // garbage in, garbage out
        if (StringUtils.isEmpty(header1) || ArrayUtils.isEmpty(headers2)) { return; }

        String filler = StringUtils.repeat(' ', header1.length());
        for (int i = 0; i < headers2.length; i++) { printHeaderLine(out, i == 0 ? header1 : filler, headers2[i]); }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void printHeaderLine(PrintStream out, String header1, String header2) {
        // garbage in, garbage out
        if (StringUtils.isEmpty(header1) || StringUtils.isEmpty(header2)) { return; }

        header2 = StringUtils.trim(header2);

        String headerLine1 = MARGIN_LEFT + header1 + header2;
        if (StringUtils.length(headerLine1) < PROMPT_LINE_WIDTH) {
            String padding = StringUtils.repeat(" ", PROMPT_LINE_WIDTH - headerLine1.length() - MARGIN_RIGHT.length());
            out.println(headerLine1 + padding + MARGIN_RIGHT);
            return;
        }

        // we have longer than 1 line to display
        boolean firstLine = true;
        // `-3` because we have `| ` in the beginning and `|` at the end of each line
        int leftMargin = PROMPT_LINE_WIDTH - header1.length() - MARGIN_LEFT.length() - MARGIN_RIGHT.length();

        do {
            String portion;
            if (StringUtils.length(header2) <= leftMargin) {
                portion = header2;
            } else {
                portion = StringUtils.substringBeforeLast(StringUtils.substring(header2, 0, leftMargin), " ");
                portion = StringUtils.trim(portion);
            }

            String headerLine = MARGIN_LEFT;
            if (firstLine) {
                headerLine += header1;
            } else {
                headerLine += StringUtils.repeat(" ", header1.length());
            }
            headerLine += portion;

            String padding = StringUtils.repeat(" ", PROMPT_LINE_WIDTH - headerLine.length() - MARGIN_RIGHT.length());
            out.println(headerLine + padding + MARGIN_RIGHT);

            firstLine = false;

            header2 = StringUtils.trim(StringUtils.substringAfter(header2, portion));
        } while (StringUtils.isNotBlank(header2));
    }

    private static void printStepHeader(String header, ExecutionContext context) {
        TestStep testStep = context.getCurrentTestStep();
        TestCase activity = testStep.getTestCase();
        String activityName = activity.getName();
        String scenarioName = activity.getTestScenario().getName();

        printConsoleHeaderTop(System.out, header, FILLER);
        printHeaderLine(System.out, META_START + "scenario" + META_END, scenarioName);
        printHeaderLine(System.out, META_START + "activity" + META_END, activityName);
        printHeaderLine(System.out, META_START + "row/step" + META_END, String.valueOf(testStep.getRowIndex() + 1));
        printConsoleHeaderBottom(System.out, FILLER);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private static void printStepPrompt(String instructions) {
        Arrays.stream(StringUtils.split(instructions, "\n"))
              .forEach(step -> System.out.println("> " + StringUtils.removeEnd(step, "\r")));
    }

    private static boolean isPauseReady() {
        if (!ExecUtils.isRunningInZeroTouchEnv()) { return true; }

        log("SKIPPING pause-for-step since Nexial is currently running in non-interactive environment");
        return false;
    }

    private static void logAs(Level logLevel, String message) {
        if (!LogbackUtils.isExecLoggingReady()) {
            PRE_EXEC_READY_BUFFER.add(new ImmutablePair<>(logLevel, message));
        } else {
            flushPreExecReadyBuffer();
            sendToLogger(logLevel, message);
        }
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
            case TRACE -> { if (LOGGER.isTraceEnabled()) { LOGGER.trace(message); } }
            case INFO -> { if (LOGGER.isInfoEnabled()) { LOGGER.info(message); } }
            case WARN -> LOGGER.warn(message);
            case ERROR -> LOGGER.error(message);
            default -> { if (LOGGER.isDebugEnabled()) { LOGGER.debug(message); } }
        }
    }
}
