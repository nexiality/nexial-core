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
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.model.TestStep
import org.nexial.core.plugins.CanTakeScreenshot
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.plugins.external.ExternalCommand
import org.nexial.core.plugins.javaui.JavaUIConst.Message.noJubulaHome
import org.nexial.core.plugins.javaui.JavaUIConst.Message.startAppNotExecuted
import org.nexial.core.plugins.javaui.JavaUIConst.Message.tooLowAppStartupWaitMs
import org.nexial.core.plugins.javaui.JavaUIConst.SystemVariable.appStartupWaitMs
import org.nexial.core.plugins.javaui.JavaUIConst.SystemVariable.delayBetweenStepsMs
import org.nexial.core.plugins.javaui.JavaUIConst.SystemVariable.profileVarPrefix
import org.nexial.core.plugins.javaui.JavaUIConst.defaultAutAgentPort
import org.nexial.core.plugins.javaui.JavaUIConst.embedded
import org.nexial.core.plugins.javaui.JavaUIConst.envJubulaHome
import org.nexial.core.plugins.javaui.JavaUIConst.mappingConfigurationPrefix
import org.nexial.core.plugins.javaui.JavaUIConst.minAppStartupWaitMs
import org.nexial.core.plugins.javaui.JavaUIConst.postAutAgentDelayMs
import org.nexial.core.plugins.javaui.JavaUIConst.postStopAutAgentDelayMs
import org.nexial.core.plugins.javaui.JavaUIConst.requiredConfigs
import org.nexial.core.plugins.javaui.JavaUIType.*
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.OutputFileUtils
import java.io.File
import java.io.File.separator

class JavaUICommand : BaseCommand(), CanTakeScreenshot {
    private lateinit var externalCommand: ExternalCommand

    override fun getTarget() = "javaui"

    override fun init(context: ExecutionContext) {
        super.init(context)
        externalCommand = context.findPlugin("external") as ExternalCommand
    }

    override fun takeScreenshot(testStep: TestStep): String = OutputFileUtils.generateScreenCaptureFilename(testStep)

    override fun generateScreenshotFilename(testStep: TestStep?): String? {
        if (testStep == null || !isScreenshotEnabled) return null

        val filename = generateScreenshotFilename(testStep)
        if (StringUtils.isBlank(filename)) {
            error("Unable to generate screen capture filename!")
            return null
        }
        val screenshotFile = File(context.project.screenCaptureDir + separator + filename)

        val jubula = resolveJubula(getProfile())
        val saveTo = jubula.takeScreenshot(screenshotFile)
        if (saveTo == null) {
            error("Unable to capture screenshot for the current AUT")
            return null
        }

        return postScreenshot(testStep, screenshotFile)
    }

    fun startLocalAgent(port: String): StepResult {
        val portNumber = toPortNumber(port)
        val autAgentLocation = resolveAutAgentBinary(resolveJubulaHome())
        val result = externalCommand.runProgramNoWait("$autAgentLocation -l -q -p $portNumber")
        return if (result.failed())
            result
        else {
            try {
                Thread.interrupted()
                Thread.sleep(postAutAgentDelayMs.toLong())
            } catch (e: InterruptedException) {
            }

            StepResult.success("AUT Agent successfully started on port $port")
        }
    }

    fun stopLocalAgent(port: String): StepResult {
        val portNumber = toPortNumber(port)
        val stopautagent = resolveStopAutAgentBinary(resolveJubulaHome())
        val result = externalCommand.runProgramNoWait("$stopautagent -p $portNumber")
        return if (result.failed())
            result
        else {
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
                ConsoleUtils.error("Unable to stop local agent on port $port within $postStopAutAgentDelayMs ms")
            Thread.interrupted()
            StepResult.success("AUT Agent on port $port successfully stopped")
        }
    }

    fun startApp(profile: String): StepResult {
        requiresNotBlank(profile, "invalid profile", profile)

        val profileKey = StringUtils.appendIfMissing(profile, ".")
        val profileData = context.getDataByPrefix(profileKey)
        val requiredMissing = requiredConfigs.filter { !profileData.contains(it) }.joinToString { ", " }
        if (StringUtils.isNotBlank(requiredMissing))
            fail("Missing required configuration for profile '$profile': $requiredMissing")

        val config = JavaUIProfile(
            name = profileData["name"]!!,
            agent = profileData["agent"] ?: "",
            appLocation = profileData["location"]!!,
            appFileName = profileData["app"]!!,
            type = JavaUIType.valueOf(
                StringUtils.upperCase(profileData["type"]
                                      ?: throw IllegalArgumentException("Invalid type for profile '$profile'"))),
            mappings = context.getDataByPrefix(profileKey + mappingConfigurationPrefix),
            appStartupWaitMs = NumberUtils.toLong(profileData["appStartupWaitMs"]
                                                  ?: context.getStringData(appStartupWaitMs)),
            delayBetweenStepsMs = NumberUtils.toLong(profileData["delayBetweenStepsMs"]
                                                     ?: context.getStringData(delayBetweenStepsMs)),
        )

        if (config.appStartupWaitMs < minAppStartupWaitMs) throw IllegalArgumentException(tooLowAppStartupWaitMs)

        val jubula = when (config.type) {
            SWING -> JubulaHelper.swing()
            SWT   -> JubulaHelper.swt()
            RCP   -> JubulaHelper.rcp()
            // GEF -> throw IllegalArgumentException("profile type '${profile.type}' not yet supported")
            // JAVAFX -> throw IllegalArgumentException("profile type '${profile.type}' not yet supported")
            else  -> throw IllegalArgumentException("profile type '${config.type}' not yet supported")
        }

        jubula.appStartupWaitMs = config.appStartupWaitMs
        jubula.delayBetweenStepsMs = config.delayBetweenStepsMs
        jubula.addMappings(*config.mappings.map { Pair(it.key, it.value) }.toTypedArray())
        jubula.registerAut(name = config.name, appLocation = config.appLocation, exe = config.appFileName)
        when (config.agent) {
            ""       -> jubula.startAgent()
            embedded -> jubula.startEmbeddedAgent()
            else     -> {
                val agentConfig = StringUtils.split(config.agent, ":")
                jubula.startAgent(agentConfig[0], NumberUtils.toInt(agentConfig[1]))
            }
        }
        jubula.startAut(config.name)

        config.jubula = jubula
        context.setData(profileVarPrefix + profile, config)
        setProfile(profile)

        return StepResult.success("JavaUI profile '$profile' initialized and started")
    }

    fun stopApp(profile: String): StepResult {
        val config = resolveConfig(profile)
        val jubula = config.jubula
                     ?: throw IllegalArgumentException(
                         "Unable to resolve AUT for profile '$profile'; $startAppNotExecuted")
        jubula.stopAut()
        return StepResult.success("AUT for profile '$profile' is stopped")
    }

    fun clickMenu(menus: String): StepResult {
        val result = resolveJubula(getProfile()).clickMenu(*StringUtils.split(menus, context.textDelim))
        return if (result.isOK)
            StepResult.success("Menu on AUT triggered successfully: $menus")
        else
            StepResult.fail("Menu on AUT was not triggered successfully", result.exception)
    }

    /**
     * supports REGEX of simpleMatch
     */
    fun waitForWindowTitle(title: String): StepResult {
        val result = resolveJubula(getProfile()).waitForTitleBar(title)
        return if (result.isOK)
            StepResult.success("Window with title '$title' FOUND")
        else
            StepResult.fail("Window with title '$title' NOT FOUND")
    }

    /**
     * supports PolyMatcher
     */
    fun assertText(name: String, text: String): StepResult {
        val result = resolveJubula(getProfile()).assertText(name, text)
        return if (result.isOK)
            StepResult.success("Component '$name' contains text '$text'")
        else
            StepResult.fail("Component '$name' DOES NOT contain '$text': ${result.exception}")
    }

    fun assertEditable(name: String): StepResult {
        val result = resolveJubula(getProfile()).assertEditable(name)
        return if (result.isOK)
            StepResult.success("Component '$name' is editable AS EXPECTED")
        else
            StepResult.fail("Component '$name' is NOT editable as expected: ${result.exception}")
    }

    fun assertPresence(name: String): StepResult {
        val result = resolveJubula(getProfile()).assertPresence(name)
        return if (result.isOK)
            StepResult.success("Component '$name' exists AS EXPECTED")
        else
            StepResult.fail("Component '$name' DOES NOT exists as expected: ${result.exception}")
    }

    fun assertEnabled(name: String): StepResult {
        val result = resolveJubula(getProfile()).assertEnabled(name)
        return if (result.isOK)
            StepResult.success("Component '$name' is enabled AS EXPECTED")
        else
            StepResult.fail("Component '$name' is NOT enabled as expected: ${result.exception}")
    }

    fun assertDisabled(name: String): StepResult {
        val result = resolveJubula(getProfile()).assertDisabled(name)
        return if (result.isOK)
            StepResult.success("Component '$name' is NOT enabled AS EXPECTED")
        else
            StepResult.fail("Component '$name' IS ENABLED, not as expected: ${result.exception}")
    }

    fun typeText(name: String, text: String): StepResult {
        val result = resolveJubula(getProfile()).typeText(name, text)
        return if (result.isOK)
            StepResult.success("Text '$text' successfully typed on component '$name'")
        else
            StepResult.fail("ERROR occurred when typing '$text' on component '$name' : ${result.exception}")
    }

    fun clickButton(name: String): StepResult {
        val result = resolveJubula(getProfile()).clickButton(name)
        return if (result.isOK)
            StepResult.success("Mouse click successfully on component '$name")
        else
            StepResult.fail("ERROR occurred when clicking on component '$name' : ${result.exception}")
    }

    fun resolveConfig(profile: String?) =
        if (StringUtils.isNotBlank(profile)) {
            context.getObjectData(profileVarPrefix + profile, JavaUIProfile::class.java)
            ?: throw IllegalArgumentException("Unable to resolve configuration for profile '$profile'; " +
                                              startAppNotExecuted)
        } else
            throw IllegalArgumentException("Invalid profile: '$profile'")

    internal fun resolveJubula(profile: String?) =
        resolveConfig(profile).jubula
        ?: throw IllegalArgumentException("Unable to reference AUT for profile '$profile'; $startAppNotExecuted")

    private fun resolveAutAgentBinary(jubulaHome: String): String {
        val autagent = "$jubulaHome${separator}ite${separator}autagent${if (IS_OS_WINDOWS) ".exe" else ""}"
        if (!FileUtil.isFileExecutable(autagent))
            throw RuntimeException("Unable to resolve to an executable binary for AUT Agent at $autagent")
        return if (IS_OS_WINDOWS) StringUtils.wrapIfMissing(autagent, "\"") else autagent
    }

    private fun resolveStopAutAgentBinary(jubulaHome: String): String {
        val stopAutAgent = "$jubulaHome${separator}ite${separator}stopautagent${if (IS_OS_WINDOWS) ".exe" else ""}"
        if (!FileUtil.isFileExecutable(stopAutAgent))
            throw RuntimeException("Unable to resolve to an executable binary for 'Stop AUT Agent' at $stopAutAgent")
        return if (IS_OS_WINDOWS) StringUtils.wrapIfMissing(stopAutAgent, "\"") else stopAutAgent
    }

    private fun resolveJubulaHome(): String {
        val jubulaHome = System.getenv(envJubulaHome) ?: throw IllegalArgumentException(noJubulaHome)
        return StringUtils.unwrap(jubulaHome, "\"")
    }

    private fun toPortNumber(port: String): Int {
        val portNumber = if (StringUtils.isBlank(port)) {
            ConsoleUtils.log("port number not defined, use default $defaultAutAgentPort")
            defaultAutAgentPort
        } else {
            requiresPositiveNumber(port, "invalid port number", port)
            NumberUtils.toInt(port)
        }
        return portNumber
    }
}