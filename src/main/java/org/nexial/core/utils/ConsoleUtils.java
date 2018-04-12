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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.nexial.commons.logging.LogbackUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.model.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import static org.nexial.core.NexialConst.FlowControls.*;
import static org.nexial.core.NexialConst.Jenkins.*;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;

/**
 * helper class to log to console.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class ConsoleUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleUtils.class);
    private static final List<Pair<Level, String>> PRE_EXEC_READY_BUFFER = new ArrayList<>();

    private ConsoleUtils() { }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String msg) {
        if (System.out == null) { throw new RuntimeException("System.out is null!"); }
        System.out.println(DateUtility.getCurrentTimestampForLogging() + " >> " + msg);
        logAs(INFO, msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String msg) {
        if (System.err == null) { throw new RuntimeException("System.err is null!"); }
        System.err.println(DateUtility.getCurrentTimestampForLogging() + " >> " + msg);
        logAs(ERROR, msg);
    }

    public static void pause(ExecutionContext context, String msg) {
        if (!isPauseReady()) { return; }

        if (context != null && context.getBooleanData(OPT_INSPECT_ON_PAUSE, DEF_INSPECT_ON_PAUSE)) {
            // inspect mode
            System.out.println(msg + "\n");
            System.out.println("/------------------------------------------------------------------------------\\");
            System.out.println("|" + centerPrompt("INSPECT ON PAUSE", 78) + "|");
            System.out.println("\\------------------------------------------------------------------------------/");
            System.out.println("> Enter statement to inspect.  Press ENTER or " + RESUME_FROM_PAUSE + " to resume " +
                               "execution\n");
            System.out.print("inspect-> ");
            Scanner in = new Scanner(System.in);
            String input = in.nextLine();

            while (StringUtils.isNotBlank(input) && !StringUtils.equals(StringUtils.trim(input), RESUME_FROM_PAUSE)) {
                System.out.println(context.replaceTokens(input));
                System.out.println();
                System.out.print("inspect-> ");
                input = in.nextLine();
            }
        } else {
            System.out.println(msg + "\n\t>>> Press ENTER to continue... ");
            Scanner in = new Scanner(System.in);
            in.nextLine();
        }
    }

    public static void pauseForStep(ExecutionContext context, String instructions) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return; }

        System.out.println("\n");
        System.out.println("/------------------------------------------------------------------------------\\");
        System.out.println(centerPrompt("PERFORM THE FOLLOWING STEPS", 80));
        System.out.println("\\------------------------------------------------------------------------------/");
        System.out.println("> Instruction(s) from " + context.getCurrentTestStep().getMessageId());

        Arrays.stream(StringUtils.split(instructions, "\n"))
              .forEach(step -> System.out.println(" | " + StringUtils.removeEnd(step, "\r")));

        System.out.println("> When complete, press ENTER to continue ");

        Scanner in = new Scanner(System.in);
        in.nextLine();
    }

    public static String pauseToValidate(ExecutionContext context, String instructions, String possibleResponses) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return null; }

        System.out.println("\n");
        System.out.println("/------------------------------------------------------------------------------\\");
        System.out.println(centerPrompt("VALIDATE THE FOLLOWING", 80));
        System.out.println("\\------------------------------------------------------------------------------/");
        System.out.println("> Validation(s) from " + context.getCurrentTestStep().getMessageId());

        Arrays.stream(StringUtils.split(instructions, "\n"))
              .forEach(step -> System.out.println(" | " + StringUtils.removeEnd(step, "\r")));

        List<String> responses = new ArrayList<>();
        if (StringUtils.isBlank(possibleResponses)) {
            responses.add("Y");
            responses.add("N");
        } else {
            responses.addAll(Arrays.asList(StringUtils.split(possibleResponses, context.getTextDelim())));
        }

        System.out.printf(" > %s: ", responses);

        Scanner in = new Scanner(System.in);
        return in.nextLine();
    }

    public static String pauseForInput(ExecutionContext context, String prompt) {
        // not applicable when running in Jenkins environment
        if (!isPauseReady()) { return null; }

        System.out.println("\n");
        System.out.println("/------------------------------------------------------------------------------\\");
        System.out.println(centerPrompt("NOTE YOUR OBSERVATION", 80));
        System.out.println("\\------------------------------------------------------------------------------/");
        System.out.println("> Prompt from " + context.getCurrentTestStep().getMessageId());

        Arrays.stream(StringUtils.split(prompt, "\n"))
              .forEach(step -> System.out.println(" | " + StringUtils.removeEnd(step, "\r")));

        System.out.print("> ");

        Scanner in = new Scanner(System.in);
        return in.nextLine();
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String id, String msg) {
        assert StringUtils.isNotBlank(id);
        if (System.out == null) { throw new RuntimeException("System.out is null!"); }
        System.out.println(DateUtility.getCurrentTimestampForLogging() + " >> [" + id + "] " + msg);
        logAs(INFO, "[" + id + "] " + msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg) {
        assert StringUtils.isNotBlank(id);
        if (System.err == null) { throw new RuntimeException("System.err is null!"); }
        System.err.println(DateUtility.getCurrentTimestampForLogging() + " >> [" + id + "] " + msg);
        logAs(ERROR, "[" + id + "] " + msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg, Throwable e) {
        error(id, msg);
        e.printStackTrace(System.err);
        System.err.print("\n\n");
        logAs(ERROR, "[" + id + "] " + msg + e.getMessage());
    }

    public static void showMissingLibraryError(String message) {
        System.out.println("\n");
        System.out.println("\n");
        System.out.println("/!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\\");
        System.out.println(" ! ERROR:");
        System.out.println(" !   " + StringUtils.replace(message, "\n", "\n !   "));
        System.out.println("\\!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!/");
        System.out.println("\n");
        System.out.println("\n");
    }

    protected static boolean isPauseReady() {
        if (isRunningInCi()) {
            log("SKIPPING pause-for-step since we are running in CI");
            return false;
        }

        if (isRunningInJUnit()) {
            log("SKIPPING pause-for-step since we are running in JUnit");
            return false;
        }

        return true;
    }

    protected static boolean isRunningInCi() {
        Map<String, String> environments = System.getenv();
        return StringUtils.isNotBlank(environments.get(OPT_JENKINS_URL)) &&
               StringUtils.isNotBlank(environments.get(OPT_JENKINS_HOME)) &&
               StringUtils.isNotBlank(environments.get(OPT_BUILD_ID)) &&
               StringUtils.isNotBlank(environments.get(OPT_BUILD_URL));
    }

    protected static boolean isRunningInJUnit() {
        // am i running via junit?
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        try {
            Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            m.setAccessible(true);
            Object loaded = m.invoke(cl, "org.junit.runner.JUnitCore");
            if (loaded != null) { return true; }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // probably not loaded... ignore error; it's probably not critical...
        }

        return false;
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

    private static String centerPrompt(String prompt, int width) {
        if (StringUtils.isBlank(prompt)) { return StringUtils.repeat(" ", width); }

        String paddingSpaces = StringUtils.repeat(" ", (width - prompt.length()) / 2);
        String newPrompt = paddingSpaces + prompt + paddingSpaces;
        if (newPrompt.length() > width) { newPrompt = StringUtils.removeEnd(newPrompt, " "); }
        if (newPrompt.length() < width) { newPrompt += " "; }
        return newPrompt;
    }
}
