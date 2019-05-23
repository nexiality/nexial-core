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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import static org.nexial.core.NexialConst.Data.*;
import static org.slf4j.LoggerFactory.getILoggerFactory;

public final class LogbackUtils {
    private static final String[] EXEC_LOG_PATH = new String[]{""};

    public LogbackUtils() { }

    public static boolean isExecLoggingReady() { return StringUtils.isNotBlank(EXEC_LOG_PATH[0]); }

    /**
     * setup env variable for logback configuration so that logs are going to the right file/path.
     */
    public static void registerLogDirectory(String outputPath) {
        // output path should contain run id now..
        File path = new File(outputPath);
        if (!path.isDirectory() || !path.exists()) { path.mkdirs(); }

        String newLogDirectory = path.getAbsolutePath();
        String currentLogDirectory = System.getProperty(TEST_LOG_PATH);
        EXEC_LOG_PATH[0] = newLogDirectory;
        if (!StringUtils.equals(currentLogDirectory, newLogDirectory)) {
            System.setProperty(TEST_LOG_PATH, newLogDirectory);
            System.setProperty(THIRD_PARTY_LOG_PATH, newLogDirectory);
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

            // support super-quiet mode
            if (BooleanUtils.toBoolean(System.getProperty(QUIET))) {
                quietLogger("org.nexial.seeknow");
                quietLogger("org.nexial.core");
                quietLogger("org.nexial.core.aws.S3Support");
                quietLogger("org.nexial.core.utils.ExecutionLogger");
                quietLogger("org.nexial.core.model.ExecutionContext");
                quietLogger("org.openqa.selenium");
                quietLogger("org.springframework.web.servlet.mvc.method.annotation");
                quietLogger("org.apache.commons.beanutils");
                quietLogger(Logger.ROOT_LOGGER_NAME);

                // we need to omit ExecutionLogger's logger due to its use by base.verbose()
                // quietLogger("org.nexial.core.utils.ExecutionLogger-priority");
            }

        } catch (JoranException e) {
            e.printStackTrace();
        }

        StatusPrinter.printIfErrorsOccured(context);
    }

    protected static void quietLogger(String loggerName) {
        Logger logger = LoggerFactory.getLogger(loggerName);
        if (logger == null) { return ; }
        if (logger instanceof ch.qos.logback.classic.Logger) {
            // ((ch.qos.logback.classic.Logger) logger).setLevel(WARN);
            ((ch.qos.logback.classic.Logger) logger).detachAppender("console-catchall");
            ((ch.qos.logback.classic.Logger) logger).detachAppender("console-execution");
        }
    }
}
