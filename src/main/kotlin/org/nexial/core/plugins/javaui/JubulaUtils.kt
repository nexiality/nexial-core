/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.javaui

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR
import org.nexial.commons.proc.ProcessInvoker.*
import org.nexial.commons.utils.FileUtil
import org.nexial.core.plugins.external.ExternalCommand
import org.nexial.core.plugins.javaui.JavaUIConst.Message.noJubulaHome
import org.nexial.core.plugins.javaui.JavaUIConst.envJubulaHome
import org.nexial.core.plugins.javaui.JavaUIConst.postAutAgentDelayMs
import org.nexial.core.plugins.javaui.JavaUIConst.postStopAutAgentDelayMs
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator

object JubulaUtils {

    private val logPath = StringUtils.appendIfMissing(JAVA_IO_TMPDIR, separator)

    private fun prepExecEnv(procName: String, log: File, workingDirectory: File): MutableMap<String, String> {
        return mutableMapOf<String, String>(
            PROC_CONSOLE_ID to procName,
            WORKING_DIRECTORY to workingDirectory.absolutePath,
            PROC_CONSOLE_OUT to "true",
            PROC_REDIRECT_OUT to log.absolutePath
        )
    }

    internal fun startLocalAgent(portNumber: Int) {
        val autAgentLocation = resolveAutAgentBinary(resolveJubulaHome())
        val procName = "javaui-start-local-agent"
        val env = prepExecEnv(procName,
                              File("$logPath$procName.log"),
                              File(StringUtils.substringBeforeLast(autAgentLocation, separator)))
        ExternalCommand.runProgram("$autAgentLocation -l -q -p $portNumber", env, false)
        postStartLocalAgent()
    }

    internal fun stopLocalAgent(portNumber: Int) {
        val stopautagent = resolveStopAutAgentBinary(resolveJubulaHome())
        val procName = "javaui-stop-local-agent"
        val env = prepExecEnv(procName,
                              File("$logPath$procName.log"),
                              File(StringUtils.substringBeforeLast(stopautagent, separator)))
        ExternalCommand.runProgram("$stopautagent -p $portNumber", env, false)
        postStopLocalAgent(portNumber)
    }

    internal fun postStartLocalAgent() {
        try {
            Thread.interrupted()
            Thread.sleep(postAutAgentDelayMs.toLong())
        } catch (e: InterruptedException) {
        }
    }

    internal fun postStopLocalAgent(portNumber: Int) {
        val waitUntil = System.currentTimeMillis() + postStopAutAgentDelayMs
        while (System.currentTimeMillis() < waitUntil) {
            Thread.yield()
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt()
                try {
                    Thread.sleep(500L)
                } catch (e: Exception) {
                }
            }
        }

        if (Thread.interrupted())
            ConsoleUtils.error("Unable to stop local agent on port $portNumber within $postStopAutAgentDelayMs ms")

        // clear off current thread's interrupted status
        Thread.interrupted()
    }

    internal fun resolveAutAgentBinary(jubulaHome: String): String {
        val autagent = "$jubulaHome${separator}ite${separator}autagent${if (IS_OS_WINDOWS) ".exe" else ""}"
        if (!FileUtil.isFileExecutable(autagent))
            throw RuntimeException("Unable to resolve to an executable binary for 'AUT Agent' at $autagent")
        return if (IS_OS_WINDOWS) StringUtils.wrapIfMissing(autagent, "\"") else autagent
    }

    internal fun resolveStopAutAgentBinary(jubulaHome: String): String {
        val stopAutAgent = "$jubulaHome${separator}ite${separator}stopautagent${if (IS_OS_WINDOWS) ".exe" else ""}"
        if (!FileUtil.isFileExecutable(stopAutAgent))
            throw RuntimeException("Unable to resolve to an executable binary for 'Stop AUT Agent' at $stopAutAgent")
        return if (IS_OS_WINDOWS) StringUtils.wrapIfMissing(stopAutAgent, "\"") else stopAutAgent
    }

    internal fun resolveJubulaHome(): String {
        val jubulaHome = System.getenv(envJubulaHome) ?: throw IllegalArgumentException(noJubulaHome)
        return StringUtils.unwrap(jubulaHome, "\"")
    }

}