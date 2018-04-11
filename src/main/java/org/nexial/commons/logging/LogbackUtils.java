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

package org.nexial.commons.logging;

import java.io.File;

import org.apache.commons.lang3.StringUtils;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import static org.nexial.core.NexialConst.Data.TEST_LOG_PATH;
import static org.nexial.core.NexialConst.Data.THIRD_PARTY_LOG_PATH;
import static org.slf4j.LoggerFactory.getILoggerFactory;

public final class LogbackUtils {
    private static final String[] EXEC_LOG_PATH = new String[]{""};

    public LogbackUtils() { }

    public static boolean isExecLoggingReady() { return StringUtils.isNotBlank(EXEC_LOG_PATH[0]); }

    /**
     * setup env variable for logback configuration so that logs are going to the right file/path.
     */
    public static void registerLogDirectory(String outputPath) {
        File path = new File(outputPath);
        if (!path.isDirectory() || !path.exists()) { path.mkdirs(); }

        String newLogDirectory = path.getAbsolutePath();
        String currentLogDirectory = System.getProperty(TEST_LOG_PATH);
        EXEC_LOG_PATH[0] = newLogDirectory;
        if (!StringUtils.equals(currentLogDirectory, newLogDirectory)) {
            System.setProperty(TEST_LOG_PATH, newLogDirectory);
            if (StringUtils.isBlank(System.getProperty(THIRD_PARTY_LOG_PATH))) {
                System.setProperty(THIRD_PARTY_LOG_PATH, newLogDirectory);
            }
            reloadDefaultConfig();
        }
    }

    public static void reloadDefaultConfig() {
        LoggerContext context = (LoggerContext) getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        try {
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(LogbackUtils.class.getResourceAsStream("/logback.xml"));
        } catch (JoranException e) {
            e.printStackTrace();
        }

        StatusPrinter.printIfErrorsOccured(context);
    }
}
