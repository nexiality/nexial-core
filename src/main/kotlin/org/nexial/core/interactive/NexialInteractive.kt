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
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.RegExUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.apache.tika.utils.SystemUtils.IS_OS_MAC
import org.apache.tika.utils.SystemUtils.IS_OS_WINDOWS
import org.java_websocket.WebSocket
import org.nexial.commons.logging.LogbackUtils
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.proc.ProcessInvoker.WORKING_DIRECTORY
import org.nexial.commons.proc.RuntimeUtils
import org.nexial.commons.utils.EnvUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.CommandConst.CMD_REPEAT_UNTIL
import org.nexial.core.CommandConst.CMD_SECTION
import org.nexial.core.ExecutionInputPrep
import org.nexial.core.ExecutionThread
import org.nexial.core.Nexial.ExecutionMode
import org.nexial.core.Nexial.ExecutionMode.READY
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.*
import org.nexial.core.NexialConst.Iteration.CURR_ITERATION
import org.nexial.core.NexialConst.Iteration.ITERATION
import org.nexial.core.NexialConst.Project.appendLog
import org.nexial.core.excel.Excel
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelStyleHelper
import org.nexial.core.interactive.InteractiveConsole.Commands.AUTORUN
import org.nexial.core.interactive.InteractiveConsole.Commands.CLEAR_TEMP
import org.nexial.core.interactive.InteractiveConsole.Commands.EXIT
import org.nexial.core.interactive.InteractiveConsole.Commands.HELP
import org.nexial.core.interactive.InteractiveConsole.Commands.INSPECT
import org.nexial.core.interactive.InteractiveConsole.Commands.OPEN_DATA
import org.nexial.core.interactive.InteractiveConsole.Commands.OPEN_SCRIPT
import org.nexial.core.interactive.InteractiveConsole.Commands.OUTPUT
import org.nexial.core.interactive.InteractiveConsole.Commands.RELOAD_ALL
import org.nexial.core.interactive.InteractiveConsole.Commands.RELOAD_MENU
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
import org.nexial.core.tools.TempCleanUp
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import java.io.File
import java.util.*

class NexialInteractive : ConnectedEventListener, CloseEventListener {

	private lateinit var runId: String
	lateinit var executionDefinition: ExecutionDefinition
	private val tmpComma = "~~!@!~~"
	private val rangeSeparator = "-"
	private val listSeparator = ","
	private var autoRun = true

	lateinit var executionMode: ExecutionMode
	private lateinit var wsServer: NexialWebSocket
	private lateinit var callbackUrl: String

	override fun onConnected(ws: WebSocket) {
		InteractiveConsole.showReadyMenu(callbackUrl)
		processReadyMenu()
	}

	override fun onClose(ws: WebSocket, code: Int, reason: String, remote: Boolean) {
		ConsoleUtils.log(runId, "terminating FrontDesk...")
		val procName = "neutralino-" +
		               if (IS_OS_WINDOWS) "win_x64.exe" else if (IS_OS_MAC) "mac_x64" else "linux_x64"
		RuntimeUtils.terminateInstance(procName)
	}

	fun startSession() {
		// start of test suite (one per test plan in execution)
		runId = ExecUtils.deriveRunId()

		// todo: determine execution mode
		if (executionMode == READY) {
			ConsoleUtils.log(runId, "starting Ready Mode...")
			val handshake = RandomStringUtils.randomAlphanumeric(15)
			wsServer = NexialWebSocket(handshake = handshake)
			wsServer.addConnectedListener(this)
			wsServer.start()
			ConsoleUtils.log(runId, "waiting on connection...")

			callbackUrl =
				"${if (wsServer.secure) WS_PREFIX_SECURE else WS_PREFIX}${EnvUtils.getHostName()}:${wsServer.port}"
			ProcessInvoker.invokeNoWait(FRONTDESK_LAUNCHER,
			                            listOf("run", "--", "$HANDSHAKE_KEY=$handshake", "$CALLBACK_KEY=$callbackUrl"),
			                            mapOf(Pair(WORKING_DIRECTORY, FRONTDESK_HOME))
			)
		}

		if (executionDefinition.testScript != null) {
			executionDefinition.runId = runId
			LogbackUtils.registerLogDirectory(appendLog(executionDefinition))

			val scriptLocation = executionDefinition.testScript

			ConsoleUtils.log(runId, "[$scriptLocation] resolve RUN ID as $runId")

			val session = InteractiveSession(ExecutionContext(executionDefinition))
			session.executionDefinition = executionDefinition
			session.autoRun = autoRun

			InteractiveConsole.showMenu(session)
			processMenu(session)
		}
	}

	private fun processMenu(session: InteractiveSession) {
		var proceed = true
		while (proceed) {
			print("> command: ")
			val input = Scanner(System.`in`).nextLine()
			val command = StringUtils.upperCase(StringUtils.trim(StringUtils.substringBefore(input, " ")))
			val argument = StringUtils.trim(StringUtils.substringAfter(input, " "))

			try {
				when (command) {
					SET_SCRIPT       -> {
						if (StringUtils.isBlank(argument)) {
							error("No test script assigned")
						} else {
							session.script = argument
						}
					}

					SET_DATA         -> {
						if (StringUtils.isBlank(argument)) {
							error("No data file assigned")
						} else {
							session.dataFile = argument
						}
					}

					SET_SCENARIO     -> {
						if (StringUtils.isBlank(argument)) {
							error("No scenario assigned")
						} else {
							session.scenario = argument
							session.reloadTestScript()
						}
					}

					SET_ITER         -> {
						if (StringUtils.isBlank(argument))
							error("No iteration assigned")
						else if (NumberUtils.isDigits(argument))
							session.iteration = NumberUtils.createInteger(argument)
						else {
							// try converting from letter (i.e. Column M) to number
							if (RegexUtils.isExact(argument, "[A-Za-z]{1,3}")) {
								session.iteration = ExcelAddress.fromColumnLettersToOrdinalNumber(argument) - 1
							} else {
								error("No a valid number or column reference for iteration: $argument")
							}
						}
					}

					SET_ACTIVITY     -> {
						// steps can be range (dash) or comma-separated
						if (StringUtils.isBlank(argument)) {
							error("No activity assigned")
						} else {
							session.assignActivities(TextUtils.replaceItems(toSteps(argument), tmpComma, ","))
							if (autoRun) {
								execute(session)
								InteractiveConsole.showMenu(session)
							}
						}
					}

					SET_STEPS        -> {
						// steps can be range (dash) or comma-separated
						if (StringUtils.isBlank(argument)) {
							error("No test step assigned")
						} else {
							session.steps = toSteps(argument)
							session.clearActivities()
							if (autoRun) {
								execute(session)
								InteractiveConsole.showMenu(session)
							}
						}
					}

					RELOAD_ALL       -> {
						System.setProperty(ITERATION, session.iteration.toString())
						session.reloadDataFile()
						session.reloadProjectProperties()
						session.reloadTestScript()
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

					OPEN_SCRIPT      -> {
						if (StringUtils.isBlank(session.script)) {
							error("No valid test script assigned")
						} else {
							Excel.openExcel(File(session.script!!))
							InteractiveConsole.showMenu(session)
						}
					}

					OPEN_DATA        -> {
						if (StringUtils.isBlank(session.dataFile)) {
							error("No valid data file assigned")
						} else {
							Excel.openExcel(File(session.dataFile!!))
							InteractiveConsole.showMenu(session)
						}
					}

					OUTPUT           -> {
						val output = System.getProperty(OPT_OUT_DIR)
						if (StringUtils.isNotBlank(output)) {
							ExecUtils.openFileNoWait(output)
							InteractiveConsole.showMenu(session)
						} else {
							error("Unable to determine output directory. Perhaps no execution has been performed thus far?")
						}
					}

					TOGGLE_RECORDING -> {
						toggleRecording(session)
						InteractiveConsole.showMenu(session)
					}

					AUTORUN          -> {
						autoRun = !autoRun
						session.autoRun = autoRun
						if (autoRun)
							ConsoleUtils.log(runId,
							                 "Autorun ENABLED: Nexial Interactive will automatically execute after steps" +
							                 " are specified via Option 5 or 6.")
						else
							ConsoleUtils.log(runId, "Autorun DISABLED: Use Option X to execute specified steps.")

						InteractiveConsole.showMenu(session)
					}

					CLEAR_TEMP       -> {
						ConsoleUtils.log(runId, "Scanning fo Nexial-generated temp files to purge...")
						TempCleanUp.cleanTempFiles(verbose = true, 1)
						InteractiveConsole.showMenu(session)
					}

					HELP             -> {
						InteractiveConsole.showHelp(session)
						InteractiveConsole.showMenu(session)
					}

					EXIT             -> {
						proceed = false
						ConsoleUtils.log(runId, "Ending Nexial Interactive session...")
					}

					else             -> {
						error("Unknown command $input. Try again...\n")
					}
				}
			} catch (e: Exception) {
				println("\n")
				println(StringUtils.repeat('!', 13))
				println("!!! ERROR !!!")
				println(StringUtils.repeat('!', 13))
				println(" >>> ${e.message}")
				println("\n\n")
			}
		}
	}

	private fun processReadyMenu() {
		var proceed = true
		while (proceed) {
			print("> command: ")
			val input = Scanner(System.`in`).nextLine()
			val command = StringUtils.upperCase(StringUtils.trim(StringUtils.substringBefore(input, " ")))
			val argument = StringUtils.trim(StringUtils.substringAfter(input, " "))

			when (command) {
				RELOAD_MENU -> {
					InteractiveConsole.showReadyMenu(callbackUrl)
				}

				// TOGGLE_RECORDING -> {
				//     toggleRecording(session)
				//     InteractiveConsole.showMenu(session)
				// }

				// HELP             -> {
				//     InteractiveConsole.showHelp(session)
				//     InteractiveConsole.showMenu(session)
				// }

				EXIT        -> {
					proceed = false
					wsServer.terminateClientsAndClose()
					ConsoleUtils.log(runId, "Ending Nexial Interactive session...")
				}

				else        -> {
					error("Unknown command $input. Try again...\n")
				}
			}
		}
	}

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

	private fun inspect(session: InteractiveSession) = session.executionInspector.inspect()

	private fun toggleRecording(session: InteractiveSession) = session.executionRecorder.toggleRecording()

	private fun execute(session: InteractiveSession) {
		// sanity check
		if (StringUtils.isBlank(session.script)) {
			error("No test script assigned")
			return
		}

		if (StringUtils.isBlank(session.scenario)) {
			error("No test scenario assigned")
			return
		}

		if (session.activities.isEmpty() && session.steps.isEmpty()) {
			error("No activity or test step assigned")
			return
		}

		val context = session.context
		val runId = context.runId

		context.setCurrentActivity(null)
		context.isFailImmediate = false
		context.isEndImmediate = false
		context.isBreakCurrentIteration = false
		context.setData(OPT_LAST_OUTCOME, true)
		context.setData(CURR_ITERATION, 1)

		val scriptLocation = executionDefinition.testScript
		val testData = executionDefinition.testData
		ConsoleUtils.log(runId, "executing $scriptLocation. ${testData.iterationManager}")

		ExecutionThread.set(context)

		var targetScenario: TestScenario?
		var scenarioSummary: ExecutionSummary? = null

		try {
			context.markExecutionStart()

			var testScript = session.inflightScript
			if (testScript == null) {
				System.setProperty(ITERATION, session.iteration.toString())
				// always running only 1 iteration.
				testScript = ExecutionInputPrep.prep(runId, executionDefinition, 1)
				context.useTestScript(testScript)
				session.inflightScript = testScript
			}

			// find target scenario object
			val availableScenarios = context.testScenarios
			targetScenario = session.inflightScenario
			if (targetScenario == null) {
				for (testScenario in availableScenarios) {
					if (StringUtils.equals(testScenario.name, session.scenario)) {
						targetScenario = testScenario
						break
					}
				}

				if (targetScenario == null) {
					error("Invalid test scenario assigned: ${session.script!!}")
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
			availableScenarios.forEach { scenario -> resetScenarioExecutionSummary(session, scenario) }
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

		val sectionStepsToSkip = mutableListOf<Int>()

		var allPass = true
		for (testStepId in steps) {
			val rowIndex = NumberUtils.toInt(testStepId)

			// perhaps the current step is marked as SKIP due to its enclosing "section" being skipped
			if (sectionStepsToSkip.contains(rowIndex)) {
				val skipStep = scenario.getTestStepByRowIndex(rowIndex)
				if (skipStep != null) context.logger.log(skipStep, RB.Skipped.text("nestedSectionStep2"))
				continue
			}

			val testStep = scenario.getTestStepByRowIndex(rowIndex) ?: break

			val stepSummary = ExecutionSummary()
			stepSummary.name = "[ROW " + StringUtils.leftPad(testStepId + "", 3) + "]"
			stepSummary.startTime = System.currentTimeMillis()
			stepSummary.totalSteps = 1
			stepSummary.executionLevel = STEP

			val result = testStep.execute()

			stepSummary.endTime = System.currentTimeMillis()
			parentSummary.addNestSummary(stepSummary)

			if (context.isEndImmediate) {
				stepSummary.adjustTotalSteps(-1)
				break
			}

			val commandFQN = testStep.commandFQN
			if (StringUtils.equals(commandFQN, CMD_REPEAT_UNTIL)) {
				// now, jolt down all the steps we need to skip since this section is now SKIPPED
				// `testStep.getParams().get(0)` represents the number of steps of this `repeat-until` section
				val stepsToSkip = testStep.params[0].toInt()
				for (j in (rowIndex + 1)..(rowIndex + stepsToSkip)) sectionStepsToSkip.add(j)
			}

			if (result.isSkipped) {
				stepSummary.adjustTotalSteps(-1)
				stepSummary.adjustSkipCount(1)

				// special treatment for `base.section()`
				if (StringUtils.equals(commandFQN, CMD_SECTION)) {
					ExcelStyleHelper.formatSectionDescription(testStep)

					// now, jolt down all the steps we need to skip since this section is now SKIPPED
					// `testStep.getParams().get(0)` represents the number of steps of this `section`
					val stepsToSkip = testStep.params[0].toInt()
					for (j in (rowIndex + 1)..(rowIndex + stepsToSkip)) sectionStepsToSkip.add(j)
				}

				if (context.isBreakCurrentIteration) {
					if (StringUtils.equals(commandFQN, CMD_REPEAT_UNTIL)) {
						context.isBreakCurrentIteration = false
					} else {
						break
					}
				} else {
					continue
				}
			}

			stepSummary.incrementExecuted()

			if (result.isSuccess) {
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
				logger.log(testStep, "${MSG_ABORT}failure on fail-fast command: $commandFQN")
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

	private fun resetExecutionSummary(
		session: InteractiveSession,
		es: ExecutionSummary,
		name: String,
		level: ExecutionLevel,
	) {
		es.scriptFile = session.script
		es.startTime = System.currentTimeMillis()
		es.endTime = 0
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
		val context = session?.context ?: return
		val testScenarios = context.testScenarios
		if (CollectionUtils.isEmpty(testScenarios)) return

		InteractiveConsole.showRun(session)
	}

	private fun resetScenarioExecutionSummary(session: InteractiveSession, scenario: TestScenario): ExecutionSummary {
		val scenarioSummary = scenario.executionSummary
		resetExecutionSummary(session, scenarioSummary, scenario.name, SCENARIO)
		return scenarioSummary
	}

	private fun resetActivityExecutionSummary(session: InteractiveSession, activity: TestCase): ExecutionSummary {
		val activitySummary = activity.executionSummary
		resetExecutionSummary(session, activitySummary, activity.name, ACTIVITY)
		return activitySummary
	}

	private fun error(message: Any) = println("\n\b[ERROR] >>\t$message")
}
