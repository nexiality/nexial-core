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

import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.Tailer
import org.apache.commons.io.input.TailerListenerAdapter
import org.apache.commons.lang3.StringUtils
import org.junit.runner.JUnitCore
import org.nexial.commons.proc.ProcessInvoker.*
import org.nexial.commons.proc.RuntimeUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.External.*
import org.nexial.core.ShutdownAdvisor
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.ForcefulTerminate
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.requires
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
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

    fun runProgram(programPathAndParams: String): StepResult {
        requires(StringUtils.isNotBlank(programPathAndParams), "empty/null programPathAndParams")

        try {
            val programAndParams = RuntimeUtils.formatCommandLine(programPathAndParams)
            if (programAndParams.isEmpty()) {
                throw IllegalArgumentException("Unable to parse programPathAndParams: $programAndParams")
            }

            //attach link to results
            val currentRow = context.currentTestStep.row[0].reference
            val outputFileName = "runProgram_$currentRow.log"
            context.setData(OPT_RUN_PROGRAM_OUTPUT, outputFileName)
            val fileName = Syspath().out("fullpath") + separator + outputFileName

            val env = prepEnv(fileName, currentRow)

            invoke(programAndParams[0], programAndParams.filterIndexed { index, _ -> index > 0 }, env)

            //attach link to results
            addLinkRef(null, "output", fileName)

            return StepResult.success()
        } catch (e: Exception) {
            return StepResult.fail(e.message)
        }
    }

    fun runProgramNoWait(programPathAndParams: String): StepResult {
        requires(StringUtils.isNotBlank(programPathAndParams), "empty/null programPathAndParams")

        try {
            val programAndParams = RuntimeUtils.formatCommandLine(programPathAndParams)
            if (programAndParams.isEmpty()) {
                throw IllegalArgumentException("Unable to parse programPathAndParams: $programAndParams")
            }

            val currentRow = context.currentTestStep.row[0].reference
            val outputFileName = "runProgramNoWait_$currentRow.log"
            context.setData(OPT_RUN_PROGRAM_OUTPUT, outputFileName)
            val fileName = Syspath().out("fullpath") + separator + outputFileName

            val env = prepEnv(fileName, currentRow)

            invokeNoWait(programAndParams[0], programAndParams.filterIndexed { index, _ -> index > 0 }, env)

            //attach link to results
            addLinkRef("Follow the link to view the output", "output", fileName)

            return StepResult.success()
        } catch (e: Exception) {
            return StepResult.fail(e.message)
        }
    }

    fun terminate(programName: String): StepResult {
        requires(StringUtils.isNotBlank(programName), "empty/null programName")
        return if (RuntimeUtils.terminateInstance(programName))
            StepResult.success("Program $programName successfully terminated")
        else
            StepResult.fail("Program $programName NOT terminated successfully, check log for detail")
    }

    /**
     * tail a reachable (local or network via shared folder or SMB) file. File does not have to exists when this command
     * is executed. However, background thread will be issued to watch/display the content of such file.
     * @param file String
     * @return StepResult
     */
    fun tail(id: String, file: String): StepResult {
        requiresNotBlank(id, "invalid id", id)
        requiresNotBlank(file, "invalid file", file)

        if (FileUtil.isFileReadable(file, 1)) {
            ConsoleUtils.log("File $file not readable at this time. Nexial will display its content when available")
        }

        val listener = ExternalTailer(id)
        val tailer = Tailer.create(File(file), listener, 250, true, true)

        val tailThread = Thread(tailer)
        tailThread.isDaemon = true
        tailThread.start()

        ShutdownAdvisor.addAdvisor(ExternalTailShutdownHelper(tailThread, tailer))
        return StepResult.success("tail watch on $file began...")
    }

    private fun prepEnv(outputFile: String, currentRow: String): MutableMap<String, String> {
        val env = mutableMapOf<String, String>()

        env[PROC_REDIRECT_OUT] = outputFile

        val consoleOut = context.getBooleanData(OPT_RUN_PROGRAM_CONSOLE, getDefaultBool(OPT_RUN_PROGRAM_CONSOLE))
        if (consoleOut) {
            env[PROC_CONSOLE_OUT] = "true"
            env[PROC_CONSOLE_ID] = "${context.runId}][$currentRow]"
        }

        if (context.hasData(OPT_RUN_FROM)) env[WORKING_DIRECTORY] = context.getStringData(OPT_RUN_FROM)

        return env
    }

    fun openFile(filePath: String): StepResult {
        requires(StringUtils.isNotBlank(filePath), "empty/null programName")
        val outcome = ExecUtils.openFileWaitForStatus(filePath)

        return if (outcome.exitStatus == 0)
            StepResult.success("Successfully opened $filePath")
        else if (StringUtils.isNotBlank(outcome.stderr))
            StepResult.fail(outcome.stderr)
        else
            StepResult.fail("Unable to open $filePath")
    }

    companion object {

        @Throws(IOException::class)
        fun exec(programPathAndParams: String): String {
            // could be xyz.cmd "long parameter with spaces" "and another one"
            // could be "weird batch file with spaces.bat" "blah blah blah" 1 2 3
            val proc = Runtime.getRuntime().exec(RuntimeUtils.formatCommandLine(programPathAndParams))
            return TextUtils.toString(IOUtils.readLines(proc.inputStream, DEF_CHARSET), lineSeparator())
        }
    }
}

class ExternalTailer(val id: String) : TailerListenerAdapter() {

    override fun handle(line: String?) = ConsoleUtils.log(id, line)

    override fun handle(ex: java.lang.Exception?) {
        if (ex !is InterruptedException) ConsoleUtils.log(id, "ERROR FOUND:\n$ex")
    }
}

class ExternalTailShutdownHelper(private var tailThread: Thread?, var tailer: Tailer?) : ForcefulTerminate {

    override fun mustForcefullyTerminate() = (tailThread != null && tailThread!!.isAlive) || (tailer != null)

    override fun forcefulTerminate() {
        if (tailer != null) {
            tailer!!.stop()
            tailer = null
        }

        if (tailThread != null) {
            tailThread!!.interrupt()
            tailThread = null
        }
    }
}