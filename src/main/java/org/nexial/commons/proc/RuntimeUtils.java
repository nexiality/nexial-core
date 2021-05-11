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

package org.nexial.commons.proc;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.utils.ConsoleUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.commons.proc.ProcessInvoker.PROC_REDIRECT_OUT;
import static org.nexial.core.NexialConst.Data.WIN32_CMD;

public final class RuntimeUtils {

    public static final String NIX_SHELL = "/bin/sh";
    private static final String TEMP_ESCAPED_DOUBLE_QUOTES = "@!#DQ#!@";
    private static final String TEMP_SPACE = "@!#SP#!@";
    private static final String TEMP_DOUBLE_QUOTES = "@!#QU#!@";

    private RuntimeUtils() { }

    public static long memUsed() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }

    public static void gc() { Runtime.getRuntime().gc(); }

    /** find running instances, as PID, on current OS based on {@code exeName}. */
    public static List<Integer> findRunningInstances(String exeName) {
        if (IS_OS_WINDOWS) { return findRunningInstancesOnWIN(exeName); }
        if (IS_OS_MAC_OSX || IS_OS_UNIX) { return findRunningInstancesOnNIX(exeName);}

        ConsoleUtils.error("UNSUPPORTED OS: " + OS_NAME + ". No running instances found for " + exeName);
        return null;
    }

    public static boolean terminateInstance(String exeName) {
        try {
            ConsoleUtils.log("terminating any leftover instance of " + exeName);
            ProcessOutcome outcome;
            if (IS_OS_WINDOWS) {
                outcome = ProcessInvoker.invoke(
                    WIN32_CMD, Arrays.asList("/C", "taskkill", "/IM", exeName + "*", "/T", "/F"), null);
            } else if (IS_OS_MAC_OSX || IS_OS_UNIX) {
                outcome = ProcessInvoker.invoke("pkill", Collections.singletonList(exeName), null);
            } else {
                ConsoleUtils.error("UNSUPPORTED OS: " + OS_NAME + ". No termination for " + exeName);
                return false;
            }
            ConsoleUtils.log(outcome.getStdout());

            // exit code 128 means process not found
            // (https://docs.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-)
            if (IS_OS_WINDOWS && (outcome.getExitStatus() == 127 || outcome.getExitStatus() == 128)) { return true; }

            try { Thread.sleep(3000); } catch (InterruptedException e) { }
            return true;
        } catch (IOException | InterruptedException e) {
            ConsoleUtils.error("Unable to terminate any running " + exeName + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean terminateInstance(int processId) {
        ConsoleUtils.log("terminating process with process id " + processId);
        try {
            if (IS_OS_WINDOWS) {
                ProcessInvoker.invokeNoWait(WIN32_CMD,
                                            Arrays.asList("/C", "start", "\"\"",
                                                          "taskkill", "/pid", processId + "", "/T", "/F"),
                                            null);
            } else if (IS_OS_MAC_OSX || IS_OS_UNIX) {
                ProcessInvoker.invokeNoWait("kill", Arrays.asList("-s", "QUIT", processId + ""), null);
            } else {
                ConsoleUtils.error("UNSUPPORTED OS: " + OS_NAME + ". No termination for " + processId);
                return false;
            }
        } catch (IOException e) {
            ConsoleUtils.error("Unable to terminate process with process id " + processId + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    public static void runAppNoWait(String exePath, String exeName, List<String> args) throws IOException {
        runAppNoWait(exePath, exeName, args, new HashMap<>());
    }

    public static void runAppNoWait(String exePath, String exeName, List<String> args, Map<String, String> env)
        throws IOException {
        if (StringUtils.isBlank(exePath)) { return; }
        if (StringUtils.isBlank(exeName)) { return; }

        String outFile = StringUtils.substringBeforeLast(exeName, ".");
        env.put(PROC_REDIRECT_OUT, StringUtils.appendIfMissing(JAVA_IO_TMPDIR, separator) + outFile + ".out");
        ProcessInvoker.invokeNoWait(exePath + separator + exeName, args, env);
    }

    public static void runApp(String exePath, String exeName, List<String> args) throws IOException {
        runApp(exePath, exeName, args, new HashMap<>());
    }

    public static void runApp(String exePath, String exeName, List<String> args, Map<String, String> env)
        throws IOException {
        if (StringUtils.isBlank(exePath)) { return; }
        if (StringUtils.isBlank(exeName)) { return; }

        String fullpath = exePath + separator + exeName;
        try {
            ProcessOutcome outcome = ProcessInvoker.invoke(fullpath, args, env);
            // System.out.println("outcome = " + outcome);
            // System.out.println(outcome.getExitStatus());
        } catch (InterruptedException e) {
            throw new IOException("Unable to start " + fullpath + ": " + e.getMessage(), e);
        }
    }

    public static String[] formatCommandLine(String programAndParams) {
        if (StringUtils.isBlank(programAndParams)) {
            return new String[]{StringUtils.defaultString(programAndParams)};
        }

        // double quote treatments
        String escaped = StringUtils.replace(programAndParams, "\\\"", TEMP_ESCAPED_DOUBLE_QUOTES);

        int doubleQuoteCount = StringUtils.countMatches(escaped, "\"");
        if (doubleQuoteCount % 2 != 0) {
            // uneven number of quotes... not good idea.
            ConsoleUtils.error("Uneven double quotes found in [" + programAndParams + "]... abort reformatting");
            return new String[]{programAndParams};
        }

        while (StringUtils.contains(escaped, "\"")) {
            String textInQuotes = RegexUtils.firstMatches(escaped, "(\".*?\")");
            String textInQuotesTreated =
                StringUtils.replace(StringUtils.replace(textInQuotes, " ", TEMP_SPACE), "\"", TEMP_DOUBLE_QUOTES);
            escaped = StringUtils.replace(escaped, textInQuotes, textInQuotesTreated);
        }

        // if *NIX and balanced single quote
        int singleQuoteCount = StringUtils.countMatches(escaped, "'");
        if (!SystemUtils.IS_OS_WINDOWS && singleQuoteCount % 2 == 0) {
            while (StringUtils.contains(escaped, "'")) {
                String textInQuotes = RegexUtils.firstMatches(escaped, "('.*?')");
                String textInQuotes2 = StringUtils.remove(StringUtils.replace(textInQuotes, " ", TEMP_SPACE), "'");
                escaped = StringUtils.replace(escaped, textInQuotes, textInQuotes2);
            }
        }

        // now split by space
        String[] splits = StringUtils.splitByWholeSeparator(escaped, " ");
        if (ArrayUtils.isEmpty(splits)) {
            // uneven number of quotes... not good idea.
            ConsoleUtils.error("Fail to parse [" + programAndParams + "]... abort reformatting");
            return new String[]{programAndParams};
        }

        List<String> cmdline = new ArrayList<>();
        Arrays.stream(splits).forEach(text -> {
            if (StringUtils.isNotBlank(text)) {
                // reverse all the previous replacements
                text = StringUtils.replace(text, TEMP_DOUBLE_QUOTES, "\"");
                text = StringUtils.replace(text, TEMP_SPACE, " ");
                text = StringUtils.replace(text, TEMP_ESCAPED_DOUBLE_QUOTES, "\\\"");
                cmdline.add(StringUtils.trim(text));
            }
        });

        return cmdline.toArray(new String[0]);
    }

    protected static List<Integer> findRunningInstancesOnNIX(String prog) {
        try {
            // ProcessOutcome outcome = ProcessInvoker.invoke(NIX_SHELL, Arrays.asList("-c", "\"pgrep " + prog + "\""), null);
            ProcessOutcome outcome = ProcessInvoker.invoke("pgrep", Collections.singletonList(prog), null);
            String output = outcome.getStdout();
            if (StringUtils.isBlank(output)) {
                ConsoleUtils.log("No running process found for " + prog);
                return null;
            }

            String[] lines = StringUtils.split(StringUtils.trim(output), lineSeparator());
            if (ArrayUtils.isEmpty(lines)) {
                ConsoleUtils.log("No running process found for " + prog + " (can't split lines)");
                return null;
            }

            List<Integer> runningProcs = Arrays.stream(lines)
                                               .map(line -> NumberUtils.toInt(StringUtils.trim(line)))
                                               .collect(Collectors.toList());
            ConsoleUtils.log("Found the following running instances of " + prog + ": " + runningProcs);

            return runningProcs;
        } catch (IOException | InterruptedException e) {
            ConsoleUtils.error("Error when finding running instances of " + prog + ": " + e.getMessage());
            return null;
        }
    }

    protected static List<Integer> findRunningInstancesOnWIN(String exeName) {
        try {
            // ConsoleUtils.log("finding existing instances of " + exeName);
            ProcessOutcome outcome = ProcessInvoker.invoke(
                WIN32_CMD,
                Arrays.asList("/C", "tasklist", "/FO", "CSV", "/FI", "\"imagename eq " + exeName + "\"", "/NH"),
                null);
            String output = outcome.getStdout();
            if (StringUtils.isBlank(output)) {
                ConsoleUtils.log("No running process found for " + exeName);
                return null;
            }

            String[] lines = StringUtils.split(StringUtils.trim(output), lineSeparator());
            if (ArrayUtils.isEmpty(lines)) {
                ConsoleUtils.log("No running process found for " + exeName + " (can't split lines)");
                return null;
            }

            // proc id is ALWAYS the second field of this CSV output
            List<Integer> runningProcs = new ArrayList<>();
            for (String line : lines) {
                String[] fields = StringUtils.split(line, ",");
                if (ArrayUtils.isEmpty(fields)) {
                    ConsoleUtils.log("No running process found for " + exeName + " (likely invalid output)");
                    return null;
                }

                if (fields.length < 2) {
                    ConsoleUtils.log("No running process found for " + exeName + " (output without process id)");
                    return null;
                }

                runningProcs.add(NumberUtils.toInt(StringUtils.substringBetween(fields[1], "\"", "\"")));
            }

            ConsoleUtils.log("Found the following running instances of " + exeName + ": " + runningProcs);
            return runningProcs;
        } catch (IOException | InterruptedException e) {
            ConsoleUtils.error("Error when finding running instances of " + exeName + ": " + e.getMessage());
            return null;
        }
    }

}
