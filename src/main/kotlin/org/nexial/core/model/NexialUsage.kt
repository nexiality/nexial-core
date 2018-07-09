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

package org.nexial.core.model

import org.apache.commons.cli.CommandLine
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.*
import org.nexial.commons.utils.EnvUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.GSON_COMPRESSED
import org.nexial.core.NexialConst.Project.NEXIAL_HOME
import org.nexial.core.model.ExecutionEvent.*
import org.nexial.core.utils.CheckUtils
import org.nexial.core.utils.ExecUtil
import org.nexial.core.utils.TrackTimeLogs
import java.io.Serializable

class NexialEnv(@Transient val commandline: CommandLine) : Serializable {

    data class OperatingSystem(val name: String = OS_NAME, val version: String = OS_VERSION, val arch: String = OS_ARCH)

    data class JVM(val vendor: String = JAVA_VENDOR,
                   val version: String = JAVA_VERSION,
                   val bits: String = System.getProperty("sun.arch.data.model"))

    data class Env(val host: String = EnvUtils.getHostName(),
                   val user: String = USER_NAME,
                   val country: String = USER_COUNTRY,
                   val timezone: String = USER_TIMEZONE,
                   val language: String = USER_LANGUAGE,
                   val isRunningInCI: Boolean = CheckUtils.isRunningInCi())

    data class Execution(val manifest: String = ExecUtil.deriveJarManifest(),
                         val home: String = System.getProperty(NEXIAL_HOME, "UNKNOWN"),
                         val cmdOption: String,
                         val runPlan: Boolean)

    val id = Utils.eventId
    val timestamp = System.currentTimeMillis()

    // what sort of physical environment was it?
    val env = Env()

    // what system is used to run this execution?
    val os = OperatingSystem()

    // which Java was used for this?
    val java = JVM()

    // which version of Nexial was used?
    // what was entered on command line to start this execution
    val nexial = Execution(
        cmdOption = commandline.options.joinToString(
            separator = "",
            transform = { option -> "-${option.opt} ${option.value} " }).trim(),
        runPlan = commandline.options.any { opt -> StringUtils.equals(opt.opt, "plan") })

    fun json(): String = GSON_COMPRESSED.toJson(this)
}

open class NexialEvent(val eventName: String, val startTime: Long) {
    val id: String = Utils.eventId
    val endTime: Long = System.currentTimeMillis()
    val timetracking: TrackTimeLogs? = ExecutionThread.getTrackTimeLogs()
    fun json(): String = GSON_COMPRESSED.toJson(this)
}

open class NexialCompleteEvent(eventName: String,
                               startTime: Long,
                               val steps: Int,
                               val executed: Int,
                               val pass: Int,
                               val fail: Int) :
    NexialEvent(eventName = eventName, startTime = startTime)

class NexialExecutionCompleteEvent(startTime: Long,
                                   steps: Int,
                                   executed: Int,
                                   pass: Int,
                                   fail: Int) :
    NexialCompleteEvent(eventName = ExecutionComplete.eventName,
                        startTime = startTime,
                        steps = steps,
                        executed = executed,
                        pass = pass,
                        fail = fail) {
    constructor(summary: ExecutionSummary) : this(summary.startTime,
                                                  summary.totalSteps,
                                                  summary.executed,
                                                  summary.passCount,
                                                  summary.failCount)

    fun trackExecution(label: String) = TrackTimeLogs().trackExecutionLevels(label, startTime, endTime, "Execution")
}

class NexialScriptCompleteEvent(val script: String,
                                startTime: Long,
                                steps: Int,
                                executed: Int,
                                pass: Int,
                                fail: Int) :
    NexialCompleteEvent(eventName = ScriptComplete.eventName,
                        startTime = startTime,
                        steps = steps,
                        executed = executed,
                        pass = pass,
                        fail = fail) {
    constructor(script: String, summary: ExecutionSummary) :
        this(script, summary.startTime, summary.totalSteps, summary.executed, summary.passCount, summary.failCount)

    fun trackScript() {
        val label: String = StringUtils.substringAfterLast(StringUtils.substringBeforeLast(script, "."), "\\")
        timetracking?.trackExecutionLevels(label, startTime, endTime, "Script")
    }

}

class NexialIterationCompleteEvent(val script: String,
                                   val iteration: Int,
                                   startTime: Long,
                                   steps: Int,
                                   executed: Int,
                                   pass: Int,
                                   fail: Int) :
    NexialCompleteEvent(eventName = IterationComplete.eventName,
                        startTime = startTime,
                        steps = steps,
                        executed = executed,
                        pass = pass,
                        fail = fail) {
    constructor(script: String, iteration: Int, summary: ExecutionSummary) :
        this(script,
             iteration,
             summary.startTime,
             summary.totalSteps,
             summary.executed,
             summary.passCount,
             summary.failCount)

    fun endTracking() = timetracking?.forcefullyEndTracking()
    fun trackIteration() {
        val label: String = StringUtils.substringAfterLast(StringUtils.substringBeforeLast(script, "."),
                                                           "\\") + "-00" + iteration
        timetracking?.trackExecutionLevels(label, startTime, endTime, "Iteration")
    }
}

class NexialScenarioCompleteEvent(val script: String,
                                  val scenario: String,
                                  startTime: Long,
                                  steps: Int,
                                  executed: Int,
                                  pass: Int,
                                  fail: Int) :
    NexialCompleteEvent(eventName = ScenarioComplete.eventName,
                        startTime = startTime,
                        steps = steps,
                        executed = executed,
                        pass = pass,
                        fail = fail) {
    constructor(summary: ExecutionSummary) : this(summary.testScript.absolutePath,
                                                  summary.name,
                                                  summary.startTime,
                                                  summary.totalSteps,
                                                  summary.executed,
                                                  summary.passCount,
                                                  summary.failCount)

    fun trackScenario() = timetracking?.trackExecutionLevels(scenario, startTime, endTime, "Scenario")
}

/**
 * one-and-done event. after this, the execution will stop
 * @property args List<String>
 * @property error String
 * @constructor
 */
class NexialCmdErrorEvent(val args: List<String>, val error: String) :
    NexialEvent(eventName = ErrorOccurred.eventName, startTime = System.currentTimeMillis())

/**
 * when a failure occurred, regardless of whether the execution will continue
 * @property error String
 * @property exception Throwable?
 * @constructor
 */
class NexialFailEvent(val error: String) :
    NexialEvent(eventName = ErrorOccurred.eventName, startTime = System.currentTimeMillis()) {
    var exception: Throwable? = null
    var commandType: String? = null
    var command: String? = null
}

class NexialUrlInvokedEvent(val browser: String, val url: String) :
    NexialEvent(eventName = WebOpen.eventName, startTime = System.currentTimeMillis())
