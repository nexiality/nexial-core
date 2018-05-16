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

package org.nexial.core.plugins.external;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.TestStep;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.variable.Syspath;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.OPT_RUN_PROGRAM_OUTPUT;
import static org.nexial.core.utils.CheckUtils.requires;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

public class ExternalCommand extends BaseCommand {
    protected Properties prop;

    @Override
    public String getTarget() { return "external"; }

    public StepResult runJUnit(String className) {
        requiresNotBlank(className, "invalid class", className);

        ExecutionSummary executionSummary =
            context.getCurrentTestStep().getTestCase().getTestScenario().getExecutionSummary();

        try {
            Class testClass = Class.forName(className);
            Object testObject = testClass.newInstance();
            log("running external class '" + className + "'");

            // save the current tests ran count, tests pass count, tests failed count... so that we can tally the
            // difference caused by this junit run
            int currentTestsRan = executionSummary.getExecuted();
            int currentTestsPassed = executionSummary.getPassCount();
            int currentTestsFailed = executionSummary.getFailCount();
            int currentTestsWarned = executionSummary.getWarnCount();

            Result result = JUnitCore.runClasses(testClass);
            if (result.getFailureCount() > 0) {
                log("runClass() on " + className + " - " + result.getFailureCount() + " failure(s)");
                for (Failure f : result.getFailures()) { log(f.getTrace()); }
            }

            int testRan = executionSummary.getExecuted() - currentTestsRan;
            int testPassed = executionSummary.getPassCount() - currentTestsPassed;
            int testFailed = executionSummary.getFailCount() - currentTestsFailed;
            int testWarned = executionSummary.getWarnCount() - currentTestsWarned;

            String msg = testRan + " test(s) ran - " +
                         testPassed + " passed, " + testFailed + " failed and " + testWarned + " warned.";
            return testFailed > 0 ? StepResult.fail(msg) : new StepResult(true, msg, null);
        } catch (ClassNotFoundException e) {
            executionSummary.incrementExecuted();
            executionSummary.incrementFail();
            return StepResult.fail("Test '" + className + "' cannot be found: " + e.getMessage());
        } catch (InstantiationException e) {
            executionSummary.incrementExecuted();
            executionSummary.incrementFail();
            return StepResult.fail("Test '" + className + "' cannot be instantiated: " + e.getMessage());
        } catch (IllegalAccessException e) {
            executionSummary.incrementExecuted();
            executionSummary.incrementFail();
            return StepResult.fail("Test '" + className + "' cannot be accessed: " + e.getMessage());
        } catch (Throwable e) {
            // last catch-all
            executionSummary.incrementExecuted();
            executionSummary.incrementFail();
            return StepResult.fail(e.getMessage());
        }
    }

    public StepResult runProgram(String programPathAndParms) {
        requires(StringUtils.isNotBlank(programPathAndParms), "empty/null programPathAndParms");

        try {
            String output = exec(programPathAndParms);

            //attach link to results
            TestStep currentTestStep = context.getCurrentTestStep();
            String outputFileName = "runProgram_" + currentTestStep.getRow().get(0).getReference() + ".log";
            context.setData(OPT_RUN_PROGRAM_OUTPUT, outputFileName);
            String fileName = new Syspath().out("fullpath") + separator + outputFileName;
            FileUtils.write(new File(fileName), output, DEF_CHARSET, false);

            addLinkRef("Follow the link to view the output", "output", fileName);
            return StepResult.success();
        } catch (Exception e) {
            return StepResult.fail(e.getMessage());
        }
    }

    public static String exec(String programPathAndParms) throws IOException {
        Process proc = Runtime.getRuntime().exec(programPathAndParms);
        List<String> buffer = IOUtils.readLines(proc.getInputStream(), DEF_CHARSET);
        return TextUtils.toString(buffer, lineSeparator());
    }
}
