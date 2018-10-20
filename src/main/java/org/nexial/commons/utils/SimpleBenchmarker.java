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

package org.nexial.commons.utils;

import java.io.PrintStream;
import java.util.logging.Logger;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import static java.util.logging.Level.FINE;

/**
 * log timespan between a block of code.
 */
public class SimpleBenchmarker {
    public static final String ENABLE_BENCHMARK = "nexial.enableBenchmark";
    public static final String DEF_PREFIX = "TIME >> ";

    private static final ThreadLocal<StopWatch> tickTock = new ThreadLocal<>();
    private static final SimpleBenchmarker SELF = new SimpleBenchmarker();

    private boolean enableBenchmark;

    private SimpleBenchmarker() {
        enableBenchmark = BooleanUtils.toBoolean(System.getProperty(ENABLE_BENCHMARK, "false"));
        if (enableBenchmark && tickTock.get() == null) { tickTock.set(new StopWatch()); }
    }

    public static SimpleBenchmarker getInstance() { return SELF; }

    public static void start() { getInstance().startCountdown(); }

    public static String end(String activity) {
        SimpleBenchmarker instance = getInstance();
        return instance.enableBenchmark ?
               StringUtils.leftPad(instance.stopCountdown() + "", 6) + "ms to complete " + activity : "";
    }

    public static void logEnd(PrintStream out, String prefix, String activity) {
        String message = end(activity);
        if (StringUtils.isNotBlank(message)) { out.println(prefix + message); }
    }

    public static void logEnd(Logger logger, String prefix, String activity) {
        String message = end(activity);
        if (StringUtils.isNotBlank(message)) { logger.log(FINE, prefix + message); }
    }

    public static void logEnd(org.slf4j.Logger logger, String prefix, String activity) {
        String message = end(activity);
        if (StringUtils.isNotBlank(message)) { logger.debug(prefix + message); }
    }

    public static void logEnd(org.slf4j.Logger logger, String activity) {
        String message = end(activity);
        if (StringUtils.isNotBlank(message)) { logger.debug(DEF_PREFIX + message); }
    }

    private long stopCountdown() {
        if (!enableBenchmark) { return -1; }

        StopWatch watch = initWatch();

        try {
            if (watch.isStopped()) { return watch.getTime(); }

            watch.stop();
            return watch.getTime();
        } finally {
            watch.reset();
        }
    }

    private void startCountdown() {
        if (enableBenchmark) {
            StopWatch watch = initWatch();
            if (!watch.isStarted()) { watch.start(); }
        }
    }

    private StopWatch initWatch() {
        StopWatch watch = tickTock.get();
        if (watch == null) {
            tickTock.set(new StopWatch());
            watch = tickTock.get();
        }
        return watch;
    }
}
