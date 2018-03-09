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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.model.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.nexial.core.NexialConst.FlowControls.*;
import static org.nexial.core.NexialConst.Jenkins.*;

/**
 * helper class to log to console.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class ConsoleUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleUtils.class);

    private ConsoleUtils() {
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String msg) {
        if (System.out == null) {
            throw new RuntimeException("System.out is null!");
        }
        System.out.println(DateUtility.getCurrentTimestampForLogging() + " >> " + msg);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(msg);
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String msg) {
        if (System.err == null) {
            throw new RuntimeException("System.err is null!");
        }
        System.err.println(DateUtility.getCurrentTimestampForLogging() + " >> " + msg);
        LOGGER.error(msg);
    }

    public static void pause(ExecutionContext context, String msg) {
        // not applicable when running in Jenkins environment
        if (isRunningInCi()) {
            LOGGER.info("SKIPPING pause since we are running in CI");
            return;
        }

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
        if (isRunningInCi()) {
            LOGGER.info("SKIPPING pause-for-step since we are running in CI");
            return;
        }

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
        if (isRunningInCi()) {
            LOGGER.info("SKIPPING pause-to-validate since we are running in CI");
            return null;
        }

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
        if (isRunningInCi()) {
            LOGGER.info("SKIPPING pause-to-input since we are running in CI");
            return null;
        }

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
        if (System.out == null) {
            throw new RuntimeException("System.out is null!");
        }
        System.out.println(DateUtility.getCurrentTimestampForLogging() + " >> [" + id + "] " + msg);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[" + id + "] " + msg);
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg) {
        assert StringUtils.isNotBlank(id);
        if (System.err == null) {
            throw new RuntimeException("System.err is null!");
        }
        System.err.println(DateUtility.getCurrentTimestampForLogging() + " >> [" + id + "] " + msg);
        LOGGER.error("[" + id + "] " + msg);
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg, Throwable e) {
        error(id, msg);
        e.printStackTrace(System.err);
        System.err.print("\n\n");
        LOGGER.error("[" + id + "] " + msg, e);
    }

    protected static boolean isRunningInCi() {
        Map<String, String> environments = System.getenv();
        return StringUtils.isNotBlank(environments.get(OPT_JENKINS_URL)) &&
                StringUtils.isNotBlank(environments.get(OPT_JENKINS_HOME)) &&
                StringUtils.isNotBlank(environments.get(OPT_BUILD_ID)) &&
                StringUtils.isNotBlank(environments.get(OPT_BUILD_URL));
    }

    private static String centerPrompt(String prompt, int width) {
        if (StringUtils.isBlank(prompt)) {
            return StringUtils.repeat(" ", width);
        }

        String paddingSpaces = StringUtils.repeat(" ", (width - prompt.length()) / 2);
        String newPrompt = paddingSpaces + prompt + paddingSpaces;
        if (newPrompt.length() > width) {
            newPrompt = StringUtils.removeEnd(newPrompt, " ");
        }
        if (newPrompt.length() < width) {
            newPrompt += " ";
        }
        return newPrompt;
    }
}
