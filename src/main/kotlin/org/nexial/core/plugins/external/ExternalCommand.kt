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

package org.nexial.core.plugins.external

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.junit.runner.JUnitCore
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.OPT_RUN_PROGRAM_OUTPUT
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.requires
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.variable.Syspath
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.lang.System.lineSeparator

class ExternalCommand : BaseCommand() {
    override fun getTarget() = "external"

    fun runJUnit(className: String): StepResult {
        requiresNotBlank(className, "invalid class", className)

        val executionSummary = context.currentTestStep.testCase.testScenario.executionSummary

        try {
            val testClass = Class.forName(className)
            val testObject = testClass.newInstance()
            log("running external class '$className'")

            // save the current tests ran count, tests pass count, tests failed count... so that we can tally the
            // difference caused by this junit run
            val currentTestsRan = executionSummary.executed
            val currentTestsPassed = executionSummary.passCount
            val currentTestsFailed = executionSummary.failCount
            val currentTestsWarned = executionSummary.warnCount

            val result = JUnitCore.runClasses(testClass)
            if (result.failureCount > 0) {
                log("runClass() on $className - ${result.failureCount} failure(s)")
                for (f in result.failures) log(f.trace)
            }

            // test stats would have been modified since we just ran the unit test class
            val testRan = executionSummary.executed - currentTestsRan
            val testPassed = executionSummary.passCount - currentTestsPassed
            val testFailed = executionSummary.failCount - currentTestsFailed
            val testWarned = executionSummary.warnCount - currentTestsWarned
            val msg = "$testRan test(s) ran - $testPassed passed, $testFailed failed and $testWarned warned."
            return if (testFailed > 0) StepResult.fail(msg) else StepResult(true, msg, null)
        } catch (e: ClassNotFoundException) {
            executionSummary.incrementExecuted()
            executionSummary.incrementFail()
            return StepResult.fail("Test '$className' cannot be found: ${e.message}")
        } catch (e: InstantiationException) {
            executionSummary.incrementExecuted()
            executionSummary.incrementFail()
            return StepResult.fail("Test '$className' cannot be instantiated: ${e.message}")
        } catch (e: IllegalAccessException) {
            executionSummary.incrementExecuted()
            executionSummary.incrementFail()
            return StepResult.fail("Test '$className' cannot be accessed: ${e.message}")
        } catch (e: Throwable) {
            // last catch-all
            executionSummary.incrementExecuted()
            executionSummary.incrementFail()
            return StepResult.fail(e.message)
        }
    }

    fun runProgram(programPathAndParms: String): StepResult {
        requires(StringUtils.isNotBlank(programPathAndParms), "empty/null programPathAndParms")

        try {
            val output = exec(programPathAndParms)

            //attach link to results
            val outputFileName = "runProgram_${context.currentTestStep.row[0].reference}.log"
            context.setData(OPT_RUN_PROGRAM_OUTPUT, outputFileName)
            val fileName = Syspath().out("fullpath") + separator + outputFileName
            FileUtils.write(File(fileName), output, DEF_CHARSET, false)
            addLinkRef("Follow the link to view the output", "output", fileName)

            return StepResult.success()
        } catch (e: Exception) {
            return StepResult.fail(e.message)
        }
    }

    companion object {
        @Throws(IOException::class)
        fun exec(programPathAndParms: String): String {
            val proc = Runtime.getRuntime().exec(programPathAndParms)
            val buffer = IOUtils.readLines(proc.inputStream, DEF_CHARSET)
            return TextUtils.toString(buffer, lineSeparator())
        }
    }
}
