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

package org.nexial.core.interactive

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.RegExUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.*
import org.apache.commons.lang3.math.NumberUtils
import org.apache.cxf.helpers.FileUtils
import org.nexial.commons.logging.LogbackUtils
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.proc.RuntimeUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.ResourceUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.ExecutionInputPrep
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.*
import org.nexial.core.NexialConst.Project.appendLog
import org.nexial.core.excel.Excel
import org.nexial.core.interactive.InteractiveConsole.Commands.ALL_STEP
import org.nexial.core.interactive.InteractiveConsole.Commands.EXIT
import org.nexial.core.interactive.InteractiveConsole.Commands.HELP
import org.nexial.core.interactive.InteractiveConsole.Commands.INSPECT
import org.nexial.core.interactive.InteractiveConsole.Commands.OPEN_DATA
import org.nexial.core.interactive.InteractiveConsole.Commands.OPEN_SCRIPT
import org.nexial.core.interactive.InteractiveConsole.Commands.RELOAD_ALL_DATA
import org.nexial.core.interactive.InteractiveConsole.Commands.RELOAD_DATA
import org.nexial.core.interactive.InteractiveConsole.Commands.RELOAD_MENU
import org.nexial.core.interactive.InteractiveConsole.Commands.RELOAD_PROJPROP
import org.nexial.core.interactive.InteractiveConsole.Commands.RELOAD_SCRIPT
import org.nexial.core.interactive.InteractiveConsole.Commands.RUN
import org.nexial.core.interactive.InteractiveConsole.Commands.SET_ACTIVITY
import org.nexial.core.interactive.InteractiveConsole.Commands.SET_DATA
import org.nexial.core.interactive.InteractiveConsole.Commands.SET_ITER
import org.nexial.core.interactive.InteractiveConsole.Commands.SET_SCENARIO
import org.nexial.core.interactive.InteractiveConsole.Commands.SET_SCRIPT
import org.nexial.core.interactive.InteractiveConsole.Commands.SET_STEPS
import org.nexial.core.interactive.InteractiveConsole.Commands.TOGGLE_RECORDING
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionDefinition
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.model.ExecutionSummary.ExecutionLevel
import org.nexial.core.model.ExecutionSummary.ExecutionLevel.*
import org.nexial.core.model.TestCase
import org.nexial.core.model.TestScenario
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import java.io.File
import java.util.*
import javax.validation.constraints.NotNull

class NexialInteractive {
    // todo: save session

    lateinit var executionDefinition: ExecutionDefinition
    private val tmpComma = "~~!@!~~"
    private val rangeSeparator = "-"
    private val listSeparator = ","
    private val regexSaveVar = "^SAVE\\s*\\(([^\\)]+)\\)\\s*\\=\\s*(.+)$"
    private val regexClearVar = "^CLEAR\\s*\\(([^\\)]+)\\)\\s*$"
    private var recordingInSession = false

    fun startSession() {
        // start of test suite (one per test plan in execution)
        val runId = ExecUtils.deriveRunId()

        executionDefinition.runId = runId
        LogbackUtils.registerLogDirectory(appendLog(executionDefinition))

        val scriptLocation = executionDefinition.testScript

        ConsoleUtils.log(runId, "[$scriptLocation] resolve RUN ID as $runId")

        val session = InteractiveSession(ExecutionContext(executionDefinition))
        session.executionDefinition = executionDefinition

        InteractiveConsole.showMenu(session)
        processMenu(session)
    }

    private fun processMenu(session: InteractiveSession) {
        var proceed = true
        while (proceed) {
            print("> command: ")
            val input = Scanner(System.`in`).nextLine()
            val command = StringUtils.upperCase(StringUtils.trim(StringUtils.substringBefore(input, " ")))
            val argument = StringUtils.trim(StringUtils.substringAfter(input, " "))

            when (command) {
                SET_SCRIPT       -> {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No test script assigned")
                    } else {
                        session.script = argument
                    }
                }

                SET_DATA         -> {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No data file assigned")
                    } else {
                        session.dataFile = argument
                    }
                }

                SET_SCENARIO     -> {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No scenario assigned")
                    } else {
                        session.scenario = argument
                    }
                }

                SET_ITER         -> {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No iteration assigned")
                    } else {
                        session.iteration = NumberUtils.createInteger(argument)
                    }
                }

                SET_ACTIVITY     -> {
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No activity assigned")
                    } else {
                        session.assignActivities(TextUtils.replaceItems(toSteps(argument), tmpComma, ","))
                    }
                }

                SET_STEPS        -> {
                    // steps can be range (dash) or comma-separated
                    if (StringUtils.isBlank(argument)) {
                        ConsoleUtils.error("No test step assigned")
                    } else {
                        session.steps = toSteps(argument)
                        session.clearActivities()
                    }
                }

                RELOAD_SCRIPT    -> {
                    session.reloadTestScript()
                    InteractiveConsole.showMenu(session)
                }

                RELOAD_DATA      -> {
                    session.reloadDataFile()
                    InteractiveConsole.showMenu(session)
                }

                RELOAD_PROJPROP  -> {
                    session.reloadProjectProperties()
                    InteractiveConsole.showMenu(session)
                }

                RELOAD_ALL_DATA  -> {
                    session.reloadDataFile()
                    session.reloadProjectProperties()
                    InteractiveConsole.showMenu(session)
                }

                RELOAD_MENU      -> {
                    InteractiveConsole.showMenu(session)
                }

                RUN              -> {
                    execute(session)
                    InteractiveConsole.showMenu(session)
                }

                INSPECT          -> {
                    inspect(session)
                    InteractiveConsole.showMenu(session)
                }

                ALL_STEP         -> {
                    session.useAllActivities()
                    InteractiveConsole.showMenu(session)
                }

                OPEN_SCRIPT      -> {
                    if (StringUtils.isBlank(session.script)) {
                        ConsoleUtils.error("No valid test script assigned")
                    } else {
                        Excel.openExcel(File(session.script!!))
                    }
                }

                OPEN_DATA        -> {
                    if (StringUtils.isBlank(session.dataFile)) {
                        ConsoleUtils.error("No valid data file assigned")
                    } else {
                        Excel.openExcel(File(session.dataFile!!))
                    }
                }

                TOGGLE_RECORDING -> {
                    toggleRecording(session)
                    InteractiveConsole.showMenu(session)
                }

                HELP             -> {
                    InteractiveConsole.showHelp(session)
                    InteractiveConsole.showMenu(session)
                }

                EXIT             -> {
                    proceed = false
                    ConsoleUtils.log("Ending Nexial Interactive session...")
                }

                else             -> {
                    ConsoleUtils.error("Unknown command $input. Try again...")
                }
            }
        }
    }

    @NotNull
    private fun toSteps(argument: String): MutableList<String> {
        if (argument == "*") return mutableListOf("*")

        var steps = argument
        while (true) {
            val range = RegexUtils.firstMatches(steps, "(\\d+\\-\\d+)")
            if (StringUtils.isBlank(range)) break

            val startNum = NumberUtils.toInt(StringUtils.substringBefore(range, rangeSeparator))
            val endNum = NumberUtils.toInt(StringUtils.substringAfter(range, rangeSeparator))
            val numberRange = StringBuilder()
            for (i in startNum..endNum) numberRange.append(i).append(listSeparator)
            steps = StringUtils.replace(steps, range, StringUtils.removeEnd(numberRange.toString() + "", listSeparator))
        }

        steps = RegExUtils.removeAll(steps, "\\ \\t\\n\\r")
        return TextUtils.toList(steps, listSeparator, true)
    }

    private fun inspect(session: InteractiveSession) {
        val context = session.context
        ExecutionThread.set(context)
        val baseCommand = context.findPlugin("base") as BaseCommand

        print("> inspect: ")
        val `in` = Scanner(System.`in`)
        var input = `in`.nextLine()

        while (StringUtils.isNotBlank(input)) {
            try {
                if (RegexUtils.isExact(input, regexSaveVar)) {
                    val groups = RegexUtils.collectGroups(input, regexSaveVar)
                    val dataVariable = groups[0]
                    val dataValue = context.replaceTokens(groups[1], true)
                    if (context.hasData(dataVariable)) {
                        ConsoleUtils.log("updating data variable [$dataVariable] to [$dataValue]")
                    } else {
                        ConsoleUtils.log("creating data variable [$dataVariable] to [$dataValue]")
                    }
                    baseCommand.save(dataVariable, dataValue)
                } else if (RegexUtils.isExact(input, regexClearVar)) {
                    val groups = RegexUtils.collectGroups(input, regexClearVar)
                    val variables = TextUtils.toList(groups[0], ",", true)
                    ConsoleUtils.log("removing data variable $variables")
                    val outcome = baseCommand.clear(variables)

                    // outcome.message.trim().split("\n").forEach { ConsoleUtils.log(it) }
                    // forgo above simple print out for something more elaborate
                    // not sure if this is a good idea.. the message itself doesn't have well-defined demarcation of
                    // data variable list
                    outcome.message.trim().split("\n").forEach {
                        if (it.contains(": ")) {
                            ConsoleUtils.log(it.substringBefore(": ") + ": ")
                            it.substringAfter(": ").split(",").forEach { item -> ConsoleUtils.log("\t$item") }
                        } else {
                            ConsoleUtils.log(it)
                        }
                    }
                } else {
                    println(context.replaceTokens(input, true))
                }
            } catch (e: Throwable) {
                ConsoleUtils.error("ERROR on '" + input + "' - " + e.message)
            }

            println()
            print("> inspect: ")
            input = `in`.nextLine()
        }
    }

    private fun toggleRecording(session: InteractiveSession) {
        val context = session.context
        ExecutionThread.set(context)
        val baseCommand = context.findPlugin("base") as BaseCommand

        if (recordingInSession) {
            val outcome = baseCommand.stopRecording()
            recordingInSession = false

            if (outcome.isSuccess) {
                val videoLink = context.getStringData(OPT_LAST_OUTPUT_LINK)
                if (videoLink != null) {
                    val selection = ConsoleUtils.pauseForInput(null,
                                                               "Previous desktop recording available at $videoLink\n" +
                                                               "Would you like to (P)lay it, (S)how it, (D)elete it? ")
                    if (StringUtils.isBlank(selection)) return

                    when (selection.toUpperCase()) {
                        "P" -> {    // play it
                            if (IS_OS_MAC_OSX) {
                                ProcessInvoker.invokeNoWait("open", Collections.singletonList(videoLink), null)
                            } else if (IS_OS_WINDOWS) {
                                ProcessInvoker.invokeNoWait(WIN32_CMD, listOf("/C", "start", "\"\"", videoLink), null)
                            } else if (IS_OS_LINUX) {
                                ProcessInvoker.invokeNoWait("/bin/sh -c", Collections.singletonList(videoLink), null)
                            } else {
                                ConsoleUtils.error("Unknown O/S; Nexial doesn't know how to open file $videoLink")
                            }
                        }

                        "S" -> {    // show it
                            val path = if (videoLink.contains("\\"))
                                videoLink.substringBeforeLast("\\")
                            else
                                videoLink.substringBeforeLast("/")
                            if (IS_OS_MAC_OSX) {
                                ProcessInvoker.invokeNoWait("open", Collections.singletonList(path), null)
                            } else if (IS_OS_WINDOWS) {
                                ProcessInvoker.invokeNoWait(WIN32_CMD, listOf("/C", "start", "\"\"", path), null)
                            } else if (IS_OS_LINUX) {
                                ProcessInvoker.invokeNoWait("/bin/sh -c", Collections.singletonList(path), null)
                            } else {
                                ConsoleUtils.error("Unknown O/S; Nexial doesn't know how to open path $path")
                            }
                        }

                        "D" -> {    // delete it (local only)
                            if (ResourceUtils.isWebResource(videoLink)) {
                                ConsoleUtils.error("The video file is not currently located in local drive; cancel delete")
                            } else {
                                FileUtils.delete(File(videoLink))
                            }
                        }
                    }
                }
            }
        } else {
            val outcome = baseCommand.startRecording()
            if (outcome.isSuccess) {
                ConsoleUtils.log(outcome.message)
                recordingInSession = true
            } else
                ConsoleUtils.error("FAILED!!! ${outcome.message}")
        }
    }

    private fun execute(session: InteractiveSession) {
        // sanity check
        if (StringUtils.isBlank(session.script)) {
            ConsoleUtils.error("No test script assigned")
            return
        }

        if (StringUtils.isBlank(session.scenario)) {
            ConsoleUtils.error("No test scenario assigned")
            return
        }

        if (session.activities.isEmpty() && session.steps.isEmpty()) {
            ConsoleUtils.error("No activity or test step assigned")
            return
        }

        val context = session.context

        val runId = context.runId
        val iterationIndex = session.iteration

        context.setCurrentActivity(null)
        context.isFailImmediate = false
        context.setData(OPT_LAST_OUTCOME, true)
        context.removeData(BREAK_CURRENT_ITERATION)
        context.setData(CURR_ITERATION, iterationIndex)

        val scriptLocation = executionDefinition.testScript
        val testData = executionDefinition.testData
        val iterationManager = testData.iterationManager
        // val iteration = iterationManager.getIterationRef(iterationIndex - 1)
        iterationManager.getIterationRef(iterationIndex - 1)

        ConsoleUtils.log(runId, "executing $scriptLocation. $iterationManager")

        ExecutionThread.set(context)

        var targetScenario: TestScenario?
        var scenarioSummary: ExecutionSummary? = null

        try {
            context.markExecutionStart()

            var testScript = session.inflightScript
            if (testScript == null) {
                testScript = ExecutionInputPrep.prep(runId, executionDefinition, iterationIndex)
                context.useTestScript(testScript)
                session.inflightScript = testScript
            }

            // find target scenario object
            targetScenario = session.inflightScenario
            if (targetScenario == null) {
                val availableScenarios = context.testScenarios
                for (testScenario in availableScenarios) {
                    if (StringUtils.equals(testScenario.name, session.scenario)) {
                        targetScenario = testScenario
                        break
                    }
                }

                if (targetScenario == null) {
                    ConsoleUtils.error("Invalid test scenario assigned: ${session.script!!}")
                    return
                }

                session.inflightScenario = targetScenario
                availableScenarios.clear()
                availableScenarios.add(targetScenario)
            }

            // gather pre-execution reference data here, so that after the execution we can reset the reference data
            // set back to its pre-execution state
            val ref = context.gatherScenarioReferenceData()

            // re-init scenario ref data
            context.clearScenarioRefData()
            ref.forEach { (name, value) -> context.setData(SCENARIO_REF_PREFIX + name, context.replaceTokens(value)) }

            // reset for this run
            scenarioSummary = resetScenarioExecutionSummary(session, targetScenario)

            if (session.activities.isNotEmpty())
                executeActivities(session, scenarioSummary)
            else
                executeSteps(session, scenarioSummary)
        } catch (e: Throwable) {
            e.printStackTrace()
            session.exception = e
        } finally {
            context.setData(ITERATION_ENDED, true)
            context.removeData(BREAK_CURRENT_ITERATION)

            context.markExecutionEnd()
            if (scenarioSummary != null) scenarioSummary.endTime = context.endTimestamp

            postExecution(session)

            // context.endIteration();
            ExecutionThread.unset()

            RuntimeUtils.gc()
        }
    }

    private fun executeActivities(session: InteractiveSession, parentSummary: ExecutionSummary?): Boolean {
        val context = session.context
        val scenario = context.testScenarios[0]

        var allPass = true
        val activities = session.activities
        for (activityName in activities) {
            val activity = scenario.getTestCase(activityName)
            if (activity != null) {
                val activitySummary = resetActivityExecutionSummary(session, activity)
                allPass = activity.execute()
                parentSummary!!.addNestSummary(activitySummary)
            }
        }

        parentSummary!!.aggregatedNestedExecutions(context)
        return allPass
    }

    private fun executeSteps(session: InteractiveSession, parentSummary: ExecutionSummary?): Boolean {

        val context = session.context
        val logger = context.logger
        val scenario = context.testScenarios[0]

        val steps = session.steps
        parentSummary!!.totalSteps = steps.size

        var allPass = true
        for (testStepId in steps) {
            val testStep = scenario.getTestStepByRowIndex(NumberUtils.toInt(testStepId)) ?: break

            val stepSummary = ExecutionSummary()
            stepSummary.name = "[ROW " + StringUtils.leftPad(testStepId + "", 3) + "]"
            stepSummary.startTime = System.currentTimeMillis()
            stepSummary.totalSteps = 1
            stepSummary.executionLevel = STEP

            val result = testStep.execute()

            stepSummary.endTime = System.currentTimeMillis()
            parentSummary.addNestSummary(stepSummary)

            if (context.isEndImmediate) {
                // parentSummary.adjustTotalSteps(-1);
                stepSummary.adjustTotalSteps(-1)
                break
            }

            if (result.isSkipped) {
                // parentSummary.adjustTotalSteps(-1);
                stepSummary.adjustTotalSteps(-1)
                if (context.isBreakCurrentIteration) break else continue
            }

            // parentSummary.incrementExecuted();
            stepSummary.incrementExecuted()

            if (result.isSuccess) {
                // parentSummary.incrementPass();
                stepSummary.incrementPass()
                continue
            }

            // SKIP condition handle earlier, so this is real FAIL condition
            // parentSummary.incrementFail();
            stepSummary.incrementFail()
            allPass = false

            // by default, only fail fast if we are not in interactive mode
            // this line is added here instead of outside the loop so that we can consider any changes to nexial.failFast
            // whilst executing the activity
            if (context.isFailFast) {
                logger.log(testStep, "${MSG_ABORT}due to execution failure and fail-fast in effect")
                break
            }

            if (context.isFailFastCommand(testStep)) {
                logger.log(testStep, "${MSG_ABORT}failure on fail-fast command: ${testStep.commandFQN}")
                context.isFailImmediate = true
                break
            }

            if (context.isFailImmediate) {
                logger.log(testStep, "${MSG_ABORT}fail-immediate in effect")
                break
            }

            if (context.isEndImmediate) {
                logger.log(testStep, "${MSG_ABORT}test scenario execution ended due to EndIf() flow control")
                break
            }

            if (context.isBreakCurrentIteration) {
                logger.log(testStep, "${MSG_ABORT}test scenario execution ended due to EndLoopIf() flow control")
                break
            }
        }

        parentSummary.aggregatedNestedExecutions(context)
        return allPass
    }

    private fun resetExecutionSummary(session: InteractiveSession,
                                      es: ExecutionSummary,
                                      name: String,
                                      level: ExecutionLevel) {
        es.scriptFile = session.script
        es.startTime = System.currentTimeMillis()
        es.executionLevel = level
        es.name = name
        es.nestedExecutions.clear()
        es.nestMessages.clear()
        es.totalSteps = 0
        es.executed = 0
        es.failCount = 0
        es.passCount = 0
        es.warnCount = 0
        es.error = null
    }

    private fun postExecution(session: InteractiveSession?) {
        if (session == null) return

        val context = session.context
        val testScenarios = context.testScenarios
        if (CollectionUtils.isEmpty(testScenarios)) return

        InteractiveConsole.showRun(session)
    }

    @NotNull
    private fun resetScenarioExecutionSummary(session: InteractiveSession, scenario: TestScenario): ExecutionSummary {
        val scenarioSummary = scenario.executionSummary
        resetExecutionSummary(session, scenarioSummary, scenario.name, SCENARIO)
        return scenarioSummary
    }

    @NotNull
    private fun resetActivityExecutionSummary(session: InteractiveSession, activity: TestCase): ExecutionSummary {
        val activitySummary = activity.executionSummary
        resetExecutionSummary(session, activitySummary, activity.name, ACTIVITY)
        return activitySummary
    }
}
