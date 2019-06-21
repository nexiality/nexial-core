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

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestCase;
import org.nexial.core.model.TestScenario;
import org.nexial.core.model.TestStep;
import org.nexial.core.plugins.CanLogExternally;
import org.nexial.core.plugins.NexialCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionLogger {
    private ExecutionContext context;
    private String runId;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Logger priorityLogger = LoggerFactory.getLogger(this.getClass().getName() + "-priority");

    public ExecutionLogger(ExecutionContext context) {
        this.context = context;
        this.runId = context.getRunId();
    }

    public void log(NexialCommand subject, String message) { log(subject, message, false); }

    public void log(NexialCommand subject, String message, boolean priority) {
        TestStep testStep = context.getCurrentTestStep();
        if (testStep != null) {
            // test step undefined could mean that we are in interactive mode, or we are running unit testing
            log(toHeader(testStep), message, priority);
            testStep.addNestedMessage(message);
            if (subject instanceof CanLogExternally) { ((CanLogExternally) subject).logExternally(testStep, message); }
        } else {
            log(runId, message, priority);
        }
    }

    public void log(TestStep testStep, String message) { log(testStep, message, false);}

    public void log(TestStep testStep, String message, boolean priority) { log(toHeader(testStep), message, priority);}

    // force logging when quiet mode is active
    public void log(TestCase subject, String message) { log(toHeader(subject), message, true); }

    // force logging when quiet mode is active
    public void log(TestScenario subject, String message) { log(toHeader(subject), message, true); }

    // force logging when quiet mode is active
    public void log(ExecutionContext subject, String message) { log(toHeader(subject), message, true); }

    public void error(NexialCommand subject, String message) { error(subject, message, null); }

    public void error(NexialCommand subject, String message, Throwable exception) {
        TestStep testStep = ExecutionThread.get().getCurrentTestStep();
        error(toHeader(testStep), message, exception);
        if (subject instanceof CanLogExternally) { ((CanLogExternally) subject).logExternally(testStep, message); }
    }

    public void errorToOutput(NexialCommand subject, String message, Throwable exception) {
        TestStep testStep = ExecutionThread.get().getCurrentTestStep();
        if (testStep != null) {
            error(toHeader(testStep), message, exception);
            testStep.addNestedMessage(message);
            if (subject instanceof CanLogExternally) { ((CanLogExternally) subject).logExternally(testStep, message); }
        } else {
            error(runId, message, exception);
        }
    }

    public void error(TestStep subject, String message) { error(toHeader(subject), message); }

    public void error(TestCase subject, String message) { error(toHeader(subject), message); }

    public void error(TestScenario subject, String message) { error(toHeader(subject), message); }

    public void error(ExecutionContext subject, String message) { error(toHeader(subject), message); }

    public static String toHeader(TestStep subject) {
        if (subject == null) { return "current step"; }
        return toHeader(subject.getTestCase()) +
               "|#" + StringUtils.leftPad((subject.getRowIndex() + 1) + "", 3) +
               "|" + StringUtils.truncate(subject.getCommandFQN(), 25);
    }

    public static String toHeader(TestCase subject) {
        return subject == null ? "current activity" : (toHeader(subject.getTestScenario()) + "|" + subject.getName());
    }

    public static String toHeader(TestScenario subject) {
        if (subject == null || subject.getWorksheet() == null) { return "current scenario"; }

        Worksheet worksheet = subject.getWorksheet();
        return justFileName(worksheet.getFile()) + "|" + worksheet.getName();
    }

    public static String toHeader(ExecutionContext subject) {
        if (subject != null && subject.getTestScript() != null) {
            return justFileName(subject.getTestScript().getFile());
        } else {
            return "current script";
        }
    }

    public static String justFileName(File file) { return StringUtils.substringBeforeLast(file.getName(), "."); }

    private void log(String header, String message, boolean priority) {
        if (priority) {
            priorityLogger.info(header + " - " + message);
        } else {
            logger.info(header + " - " + message);
        }
    }

    private void error(String header, String message) { error(header, message, null); }

    private void error(String header, String message, Throwable e) {
        if (e == null) {
            priorityLogger.error(header + " - " + message);
        } else {
            priorityLogger.error(header + " - " + message, e);
        }
    }
}