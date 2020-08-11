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
package org.nexial.core.model

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE
import org.apache.commons.lang3.time.StopWatch
import org.nexial.commons.utils.FileUtil
import org.nexial.core.CommandConst.CMD_REPEAT_UNTIL
import org.nexial.core.CommandConst.CMD_SECTION
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelArea
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.excel.ExcelStyleHelper
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils.isRunningInZeroTouchEnv
import org.nexial.core.utils.FlowControlUtils
import java.io.File
import java.io.IOException

data class Macro(val file: String, val sheet: String, val macroName: String) {
    override fun toString(): String = "$file::$sheet::$macroName"
}

class MacroExecutor(private val initialTestStep: TestStep, val macro: Macro,
                    val input: Map<String, String> = hashMapOf(), val output: Map<String, String> = hashMapOf()) {

    var macroExcel: Excel? = null
    private var macroSheet: Worksheet? = null
    val context: ExecutionContext = ExecutionThread.get()
    var testSteps: MutableList<TestStep> = mutableListOf()
    var stepCount = 0

    fun start(): StepResult {
        val logger = context.getLogger()
        val trackTimeLogs = context.trackTimeLogs

        // todo check for repeatUntil macro
        val testCase = initialTestStep.testCase

        // pre-process input parameter
        if (MapUtils.isNotEmpty(input)) updateInputToContext()

        // harvest all macro steps
        testSteps = harvestSteps(macro)
        val size = testSteps.size
        stepCount = size
        if (size == 0) throw Exception("There is no steps detected in $macro ; so can not be extended")

        // for macro outside repeatUntil loop
        // add macro steps count into total step count for activity and scenario
        val activitySummary = testCase.executionSummary
        val scenarioSummary = testCase.testScenario.executionSummary
        if (!initialTestStep.macroPartOfRepeatUntil) {
            activitySummary.adjustTotalSteps(size)
            scenarioSummary.adjustTotalSteps(size)
        }

        var i = 0
        while (i < size) {
            val testStep = testSteps[i]

            // check for repeat until
            if (testStep.isCommandRepeater) {
                i += collectRepeatingCommand(testStep, activitySummary, scenarioSummary)
            }

            val result = execute(testStep) ?: return StepResult.fail("Unable to execute step ${testStep.commandFQN}")

            val succeed = result.isSuccess
            context.setData(OPT_LAST_OUTCOME, succeed)

            if (initialTestStep.macroPartOfRepeatUntil) {
                // if repeat until contains repeat until commands
                // if nested repeat until has EndLoopIf() so setting back to normal again
                if (testStep.isCommandRepeater) context.isBreakCurrentIteration = false

                // todo endImmediate working for repeat until
                if (context.isBreakCurrentIteration || (result.isError && shouldFailFast(context, testStep))) {
                    return result
                }
                i++
                continue
            }

            if (context.isEndImmediate) {
                activitySummary.incrementExecuted()
                activitySummary.incrementPass()
                scenarioSummary.adjustPassCount(-1)
                scenarioSummary.adjustExecutedSteps(-1)
                scenarioSummary.adjustTotalSteps(-1)
                break
            }

            if (result.isSkipped) {
                activitySummary.adjustTotalSteps(-1)
                if (testStep.commandFQN == CMD_SECTION) {
                    ExcelStyleHelper.formatSectionDescription(testStep, false)

                    val numOfSteps = testStep.formatSkippedSections(testSteps, i, true)
                    i += numOfSteps

                    activitySummary.adjustTotalSteps(-numOfSteps)
                }

                if (context.isBreakCurrentIteration) {
                    // reset it so that we are only performing loop-break one level at a time
                    if (testStep.commandFQN == CMD_REPEAT_UNTIL) {
                        context.isBreakCurrentIteration = false
                    } else {
                        context.isMacroBreakIteration = true
                        break
                    }
                }
                i++
                continue
            }

            activitySummary.incrementExecuted()

            if (result.isSuccess) {
                activitySummary.incrementPass()
                i++
                continue
            }

            // SKIP condition handle earlier, so this is real FAIL condition
            activitySummary.incrementFail()
            context.isMacroStepFailed = true
            // allPassed = false

            // by default, only fail fast if we are not in interactive mode
            // this line is added here instead of outside the loop so that we can consider any changes to nexial.failFast
            // whilst executing the activity
            if (context.isFailFastCommand(testStep)) {
                logger.log(testStep, MSG_CRITICAL_COMMAND_FAIL + testStep.commandFQN)
                trackTimeLogs.trackingDetails("Execution Failed")
                context.isFailImmediate = true
                break
            }

            if (context.isFailImmediate) {
                logger.log(testStep, MSG_EXEC_FAIL_IMMEDIATE)
                trackTimeLogs.trackingDetails("Execution Failed")
                break
            }

            val shouldFailFast = context.isFailFast
            if (shouldFailFast) {
                logger.log(testStep, MSG_STEP_FAIL_FAST)
                trackTimeLogs.trackingDetails("Execution Failed")
                break
            }
            i++
        }

        // add output variables value to context
        updateOutputToContext()

        return StepResult.success("Macro successfully executed")
    }

    @Throws(IOException::class)
    fun harvestSteps(macro: Macro): MutableList<TestStep> {
        val macroFile = resolveMacroFile(macro.file)

        // (2018/12/9,automike): remove to support dynamic macro changes during execution and interactive mode
        // String macroKey = macroFile + ":" + paramSheet + ":" + paramMacro;
        // if (MACRO_CACHE.containsKey(macroKey)) {
        //     shortcut: in case the same macro is referenced
        // ConsoleUtils.log("reading macro from cache: " + macroKey);
        // return MACRO_CACHE.get(macroKey);
        // }

        // open specified sheet
        // try stylesheet to false
        macroExcel = Excel(macroFile, DEF_OPEN_EXCEL_AS_DUP, true)
        macroSheet = macroExcel!!.worksheet(macro.sheet)
        val lastMacroRow = macroSheet!!.findLastDataRow(ADDR_MACRO_COMMAND_START)
        val macroArea = ExcelArea(macroSheet, ExcelAddress("A2:O$lastMacroRow"), false)
        val macroStepArea = macroArea.wholeArea
        var macroFound = false

        // 6. read test steps based on macro name
        for (macroRow in macroStepArea) {
            val currentMacroName = Excel.getCellValue(macroRow[COL_IDX_TESTCASE])

            val testStep = TestStep(initialTestStep.testCase, macroRow, macroSheet)
            val macroName = macro.macroName
            if (currentMacroName == macroName) {
                macroFound = true
                testStep.macroName = macroName
                testSteps.add(testStep)
                continue
            }
            if (macroFound) {
                if (StringUtils.isBlank(currentMacroName)) {
                    testStep.macroName = macroName
                    testSteps.add(testStep)
                } else {
                    return testSteps
                    // (2018/12/9,automike): remove to support dynamic macro changes during execution and interactive mode
                    // MACRO_CACHE.put(macroKey, steps);
                    // steps = new ArrayList<>();
                    // macroFound = false;
                    // break;
                }
            }
        }

        // (2018/12/16,automike): memory consumption precaution
        return testSteps

        // (2018/12/9,automike): remove to support dynamic macro changes during execution and interactive mode
        // capture last one
        // if (macroFound && !steps.isEmpty()) { MACRO_CACHE.put(macroKey, steps); }
        // if (!MACRO_CACHE.containsKey(macroKey)) { ConsoleUtils.error("Unable to resolve macro via " + macroKey);}
        // return MACRO_CACHE.get(macroKey);
    }

    @Throws(IOException::class)
    fun resolveMacroFile(file: String): File {
        // first, try it as is
        if (FileUtil.isFileReadable(file, MIN_EXCEL_FILE_SIZE.toLong())) return File(file)

        val project = context.execDef.project

        // next, try pivoting from artifact/script
        val macroFile = StringUtils.appendIfMissing(file, ".${Project.SCRIPT_FILE_SUFFIX}")
        var macroFilePath = StringUtils.appendIfMissing(project.scriptPath, File.separator) + macroFile

        if (!FileUtil.isFileReadable(macroFilePath, MIN_EXCEL_FILE_SIZE.toLong())) {
            // last, try again pivoting now from project home
            macroFilePath = StringUtils.appendIfMissing(project.projectHome, File.separator) + macroFile

            if (!FileUtil.isFileReadable(macroFilePath, MIN_EXCEL_FILE_SIZE.toLong())) {
                throw IOException("Unable to read macro file '$macroFile'")
            }
        }
        return File(macroFilePath)
    }

    private fun updateInputToContext() = input.forEach { context.setData(it.key, it.value, false) }

    private fun updateOutputToContext() {
        // Adding output data to map before removing Macro Flex data
        val outputValueMap = hashMapOf<String, Any>()

        output.forEach {
            if (it.key == "") return@forEach
            outputValueMap[it.value] = context.getStringData(it.key)
        }
        // Now going outside this macro
        context.isInMacro = false

        // remove all macro flex variables from context
        context.removeDataByPrefix(MACRO_FLEX_PREFIX)
        // Adding output to context
        outputValueMap.forEach { (key, value) -> context.setData(key, value) }
    }

    private fun collectRepeatingCommand(step: TestStep, activitySummary: ExecutionSummary,
                                        scenarioSummary: ExecutionSummary): Int {
        val excelAddress = ExcelAddress("${COL_TEST_CASE}1:$COL_REASON${step.rowIndex + testSteps.size}")
        val area = ExcelArea(macroSheet, excelAddress, false)
        val numOfStepIncluded = step.testCase.testScenario.collectRepeatingCommandSet(step, area, step.rowIndex + 1)

        step.commandRepeater.steps.forEach { it.macroName = step.macroName }

        stepCount -= numOfStepIncluded

        // subtracting steps nested repeat until steps from total steps
        if (!initialTestStep.macroPartOfRepeatUntil) {
            activitySummary.adjustTotalSteps(-numOfStepIncluded)
            scenarioSummary.adjustTotalSteps(-numOfStepIncluded)
        }
        return numOfStepIncluded
    }

    private fun execute(testStep: TestStep): StepResult? {
        val tickTock = StopWatch()
        val trackTimeLogs = context.trackTimeLogs
        var result: StepResult? = null

        context.setCurrentTestStep(testStep)
        trackTimeLogs.checkStartTracking(context, testStep)

        tickTock.start()
        try {
            result = testStep.invokeCommand()
        } catch (e: Throwable) {
            result = testStep.toFailedResult(e)
        } finally {
            tickTock.stop()

            // time tracking
            trackTimeLogs.checkEndTracking(context, testStep)

            testStep.postExecCommand(result, tickTock.time)
            FlowControlUtils.checkPauseAfter(context, testStep)
            if (!isRunningInZeroTouchEnv() && OnDemandInspectionDetector.getInstance(context).detectedPause()) {
                ConsoleUtils.pauseAndInspect(context, ">>>>> On-Demand Inspection detected...", true)
            }

            if (initialTestStep.macroPartOfRepeatUntil) return result

            if (testStep.isCommandRepeater()) context.setCurrentTestStep(testStep)

            context.clearCurrentTestStep()
        }
        // macroExcel?.close()
        return result
    }

    private fun shouldFailFast(context: ExecutionContext, testStep: TestStep): Boolean {
        val shouldFailFast = context.isFailFast || context.isFailFastCommand(testStep)
        if (shouldFailFast) {
            context.logCurrentStep(MSG_CRITICAL_COMMAND_FAIL + testStep.commandFQN)
        }
        return shouldFailFast
    }

    override fun toString(): String {
        return ToStringBuilder(this, SIMPLE_STYLE)
            .append("initialTestStep", initialTestStep)
            .append("steps", testSteps)
            .toString()
    }
}