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

import java.util.Map;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.nexial.commons.utils.DateUtility;
import org.nexial.core.model.ExecutionContext;

import static org.nexial.core.NexialConst.FlowControls.*;
import static org.nexial.core.NexialConst.Jenkins.*;

/**
 * helper class to log to console.
 */
@SuppressWarnings("PMD.SystemPrintln")
public final class ConsoleUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleUtils.class);

    private ConsoleUtils() {}

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String msg) {
        if (System.out == null) { throw new RuntimeException("System.out is null!"); }
        System.out.println(DateUtility.getCurrentTimestampForLogging() + " >> " + msg);
        if (LOGGER.isInfoEnabled()) { LOGGER.info(msg); }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String msg) {
        if (System.err == null) { throw new RuntimeException("System.err is null!"); }
        System.err.println(DateUtility.getCurrentTimestampForLogging() + " >> " + msg);
        LOGGER.error(msg);
    }

    public static void pause(ExecutionContext context, String msg) {
        // not applicable when running in Jenkins environment
        Map<String, String> environments = System.getenv();
        if (StringUtils.isNotBlank(environments.get(OPT_JENKINS_URL)) &&
            StringUtils.isNotBlank(environments.get(OPT_JENKINS_HOME)) &&
            StringUtils.isNotBlank(environments.get(OPT_BUILD_ID)) &&
            StringUtils.isNotBlank(environments.get(OPT_BUILD_URL))) {
            LOGGER.info("SKIPPING pause since we are running in Jenkins environment");
            return;
        }

        if (context != null && context.getBooleanData(OPT_INSPECT_ON_PAUSE, DEF_INSPECT_ON_PAUSE)) {
            // inspect mode
            System.out.println(msg + "\n");
            System.out.println("/------------------------------------------------------------------------------\\");
            System.out.println("|                               INSPECT ON PAUSE                               |");
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

    @SuppressWarnings("PMD.SystemPrintln")
    public static void log(String id, String msg) {
        assert StringUtils.isNotBlank(id);
        if (System.out == null) { throw new RuntimeException("System.out is null!"); }
        System.out.println(DateUtility.getCurrentTimestampForLogging() + " >> [" + id + "] " + msg);
        if (LOGGER.isInfoEnabled()) { LOGGER.info("[" + id + "] " + msg); }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void error(String id, String msg) {
        assert StringUtils.isNotBlank(id);
        if (System.err == null) { throw new RuntimeException("System.err is null!"); }
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
}
