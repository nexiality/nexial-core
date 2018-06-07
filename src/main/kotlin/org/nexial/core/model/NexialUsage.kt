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
import org.nexial.core.NexialConst.GSON_COMPRESSED
import org.nexial.core.NexialConst.Project.NEXIAL_HOME
import org.nexial.core.model.ExecutionEvent.Utils
import org.nexial.core.utils.CheckUtils
import org.nexial.core.utils.ExecUtil
import java.io.Serializable

class NexialEnv(val id: String, @Transient val commandline: CommandLine) : Serializable {
    data class OperatingSystem(val name: String = OS_NAME, val version: String = OS_VERSION, val arch: String = OS_ARCH)

    data class JVM(val home: String = JAVA_HOME, val vendor: String = JAVA_VENDOR, val version: String = JAVA_VERSION)

    data class Nexial(val manifest: String = ExecUtil.deriveJarManifest(),
                      val home: String = System.getProperty(NEXIAL_HOME, "UNKNOWN"))

    data class Env(val host: String = EnvUtils.getHostName(),
                   val user: String = USER_NAME,
                   val tmp: String = JAVA_IO_TMPDIR,
                   val country: String = USER_COUNTRY,
                   val timezone: String = USER_TIMEZONE,
                   val language: String = USER_LANGUAGE,
                   val isRunningInJUnit: Boolean = CheckUtils.isRunningInJUnit(),
                   val isRunningInCI: Boolean = CheckUtils.isRunningInCi())

    // what sort of physical environment was it?
    val env = Env()

    // what system is used to run this execution?
    val os = OperatingSystem()

    // which Java was used for this?
    val java = JVM()

    // which version of Nexial was used?
    val nexial = Nexial()

    // what was entered on command line to start this execution
    var cmd: String? = null

    var runPlan: Boolean?

    init {
        cmd = commandline.options
                .joinToString(separator = "", transform = { option -> "-${option.opt} ${option.value} " }).trim()
        runPlan = StringUtils.contains(cmd, "-plan")
    }

    fun json() = GSON_COMPRESSED.toJson(this)
}

open class NexialEvent(val id: String, val timestamp: Long = System.currentTimeMillis(), val eventName: String) {
    fun json(): String = GSON_COMPRESSED.toJson(this)
}

class NexialExecutionStartEvent :
        NexialEvent(id = Utils.newEventId(), eventName = ExecutionEvent.ExecutionStart.eventName)

class NexialExecutionCompleteEvent(id: String) : NexialEvent(id, eventName = ExecutionEvent.ExecutionComplete.eventName)

class NexialScriptStartEvent(id: String, val script: String) :
        NexialEvent(id, eventName = ExecutionEvent.ScriptStart.eventName)

class NexialScriptCompleteEvent(id: String, val script: String) :
        NexialEvent(id, eventName = ExecutionEvent.ScriptComplete.eventName)

/**
 * one-and-done event. after this, the execution will stop
 * @property args List<String>
 * @property error String
 * @constructor
 */
class NexialCmdErrorEvent(val args: List<String>, val error: String) :
        NexialEvent(id = Utils.newEventId(), eventName = ExecutionEvent.ErrorOccurred.eventName)

/**
 * when a failure occurred, regardless of whether the execution will continue
 * @property error String
 * @property exception Throwable?
 * @constructor
 */
class NexialFailEvent(id: String, val error: String) :
        NexialEvent(id, eventName = ExecutionEvent.ErrorOccurred.eventName) {
    var exception: Throwable? = null
    var commandType: String? = null
    var command: String? = null
}

class NexialUrlInvokedEvent(id: String, val browser: String, val url: String) :
        NexialEvent(id, eventName = ExecutionEvent.WebOpen.eventName)

