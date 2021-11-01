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
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.SIMPLE_STYLE
import org.apache.commons.lang3.time.StopWatch
import org.nexial.commons.utils.FileUtil
import org.nexial.core.CommandConst.CMD_REPEAT_UNTIL
import org.nexial.core.CommandConst.CMD_SECTION
import org.nexial.core.CommandConst.CMD_VERBOSE
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP
import org.nexial.core.NexialConst.Data.MACRO_INVOKED_FROM
import org.nexial.core.NexialConst.LogMessage.ERROR_LOG
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelArea
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.excel.ExcelStyleHelper
import org.nexial.core.model.OnDemandInspectionDetector.Companion.getInstance
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils.isRunningInZeroTouchEnv
import org.nexial.core.utils.FlowControlUtils
import org.nexial.core.utils.MessageUtils
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

        // harvest all macro steps
        testSteps = harvestSteps(macro)
        val size = testSteps.size
        stepCount = size
        if (size == 0) throw Exception("There is no steps detected in $macro ; so can not be extended")

        // pre-process input parameter
        if (MapUtils.isNotEmpty(input)) updateInputToContext()

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
            if (BooleanUtils.toBoolean(System.getProperty(Data.END_SCRIPT_IMMEDIATE, "false"))) {
                trackTimeLogs.trackingDetails("Execution Interrupted")
                break
            }

            val testStep = testSteps[i]

            // check for repeat until
            if (testStep.isCommandRepeater) {
                i += collectRepeatingCommand(testStep, activitySummary, scenarioSummary)
            }

            val result = execute(testStep)

            if (result == null) {
                // just to be safe for upcoming macros
                updateOutputToContext()
                return StepResult.fail("Unable to execute step ${testStep.commandFQN}")
            }

            val succeed = result.isSuccess
            // skip should not be considered as failure, but "inconclusive"
            if (!result.isSkipped)
                context.setData(OPT_LAST_OUTCOME, succeed)
            else {
                // if macro step is part of repeat until and is skipped don't reduce total steps
                if (!initialTestStep.macroPartOfRepeatUntil) activitySummary.adjustTotalSteps(-1)
                if (testStep.commandFQN == CMD_SECTION) {
                    ExcelStyleHelper.formatSectionDescription(testStep)
                    val numOfSteps = testStep.formatSkippedSections(testSteps, i, true)
                    i += numOfSteps
                    // if macro step is part of repeat until and is skipped don't reduce total steps
                    if (!initialTestStep.macroPartOfRepeatUntil) activitySummary.adjustTotalSteps(-numOfSteps)
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

            if (initialTestStep.macroPartOfRepeatUntil) {
                // if repeat until contains repeat until commands
                // if nested repeat until has EndLoopIf() so setting back to normal again
                if (testStep.isCommandRepeater) context.isBreakCurrentIteration = false

                if (context.isBreakCurrentIteration || (result.isError && shouldFailFast(context, testStep))) {
                    // just to be safe for upcoming macro
                    updateOutputToContext()
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
                logger.log(testStep, RB.Abort.text("criticalCommand.fail", testStep.commandFQN))
                trackTimeLogs.trackingDetails("Execution Failed")
                context.isFailImmediate = true
                break
            }

            if (context.isFailImmediate) {
                logger.log(testStep, RB.Abort.text("exec.failImmediate"))
                trackTimeLogs.trackingDetails("Execution Failed")
                break
            }

            if (shouldFailFast(context, testStep)) {
                logger.log(testStep, RB.Abort.text("step.failFast"))
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
        if (macroSheet == null) {
            throw IOException("Unable to read macro sheet '${macro.sheet}' from file '${macroFile.absolutePath}'")
        }
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
                testStep.macro = macro
                // setting macro name to empty for the first cell so that activity cell formatting avoided
                testStep.row[COL_IDX_TESTCASE].setCellValue("")
                testSteps.add(testStep)
                continue
            }
            if (macroFound) {
                if (StringUtils.isBlank(currentMacroName)) {
                    testStep.macro = macro
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
        if (!macroFound) {
            throw IOException(
                "Unable to read macro '${macro.macroName}' in sheet " + "'${macro.sheet}'" + " from file " + "'${macroFile.absolutePath}'")
        }

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
        // remove macro reference index
        context.removeData(MACRO_INVOKED_FROM)
        val outputValueMap = hashMapOf<String, Any>()

        // Adding output data to map before removing Macro Flex data
        output.forEach {
            // don't update if key is empty or has null value
            if (StringUtils.isBlank(it.key) || !context.hasData(it.key) || StringUtils.isBlank(it.value)) return@forEach
            outputValueMap[it.value] = context.getObjectData(it.key) as Any
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

        step.commandRepeater.steps.forEach { it.macro = step.macro }

        stepCount -= numOfStepIncluded

        // subtracting steps nested repeat until steps from total steps
        if (!initialTestStep.macroPartOfRepeatUntil) {
            activitySummary.adjustTotalSteps(-numOfStepIncluded)
            scenarioSummary.adjustTotalSteps(-numOfStepIncluded)
        }
        return numOfStepIncluded
    }

    private fun execute(testStep: TestStep): StepResult? {
        val trackTimeLogs = context.trackTimeLogs
        trackTimeLogs.checkStartTracking(context, testStep)

        // clock's ticking
        val tickTock = StopWatch()
        tickTock.start()

        context.setCurrentTestStep(testStep)

        // delay is carried out here so that timespan is captured as part of execution
        testStep.waitFor(context.delayBetweenStep)

        var result: StepResult? = null
        try {
            result = testStep.invokeCommand()
        } catch (e: Throwable) {
            result = testStep.toFailedResult(e)
        } finally {
            tickTock.stop()
            trackTimeLogs.checkEndTracking(context, testStep)

            if (initialTestStep.macroPartOfRepeatUntil) {
                log(testStep, result!!)
                testStep.handleScreenshot(result)
                return result
            }

            testStep.postExecCommand(result, tickTock.time)
            FlowControlUtils.checkPauseAfter(context, testStep)

            if (!isRunningInZeroTouchEnv() && getInstance(context).detectedPause()) {
                ConsoleUtils.pauseAndInspect(context, ">>>>> On-Demand Inspection detected...", true)
            }

            if (testStep.isCommandRepeater()) context.setCurrentTestStep(testStep)

            context.clearCurrentTestStep()
        }

        return result
    }

    private fun log(testStep: TestStep, result: StepResult) {
        val logger = context.getLogger()
        val message = result.message

        if (result.isSuccess) {
            // avoid printing verbose() message to avoid leaking of sensitive information on log
            logger.log(testStep, MessageUtils.renderAsPass(if (testStep.commandFQN == CMD_VERBOSE) "" else message))
            return
        }

        if (result.isSkipped) {
            logger.log(testStep, MessageUtils.renderAsSkipped(result.message))
            return
        }

        logger.error(testStep, MessageUtils.renderAsFail(message))
        if (StringUtils.isNotBlank(result.detailedLogLink))
            logger.error(testStep, "$ERROR_LOG${result.detailedLogLink}")
        testStep.trackExecutionError(result)
    }

    private fun shouldFailFast(context: ExecutionContext, testStep: TestStep): Boolean {
        return if (context.isFailFast || context.isFailFastCommand(testStep)) {
            context.logCurrentStep(
                RB.Abort.text("criticalCommand.fail", testStep.commandFQN))
            true
        } else false
    }

    override fun toString(): String {
        return ToStringBuilder(this, SIMPLE_STYLE)
            .append("initialTestStep", initialTestStep)
            .append("steps", testSteps)
            .toString()
    }
}