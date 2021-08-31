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

import ch.qos.logback.core.joran.spi.ActionException
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.bouncycastle.asn1.x500.style.RFC4519Style.name
import org.eclipse.jubula.autagent.Embedded
import org.eclipse.jubula.client.*
import org.eclipse.jubula.client.exceptions.*
import org.eclipse.jubula.client.internal.BaseConnection.AlreadyConnectedException
import org.eclipse.jubula.client.launch.AUTConfiguration
import org.eclipse.jubula.toolkit.ToolkitInfo
import org.eclipse.jubula.toolkit.base.AbstractComponents
import org.eclipse.jubula.toolkit.base.components.GraphicsComponent
import org.eclipse.jubula.toolkit.base.components.handler.GraphicsComponentActionHandler
import org.eclipse.jubula.toolkit.concrete.components.ButtonComponent
import org.eclipse.jubula.toolkit.concrete.components.TextComponent
import org.eclipse.jubula.toolkit.concrete.components.TextInputComponent
import org.eclipse.jubula.toolkit.concrete.components.handler.ButtonComponentActionHandler
import org.eclipse.jubula.toolkit.concrete.components.handler.TextComponentActionHandler
import org.eclipse.jubula.toolkit.concrete.components.handler.TextInputComponentActionHandler
import org.eclipse.jubula.toolkit.concrete.internal.impl.handler.MenuBarComponentActionHandler
import org.eclipse.jubula.toolkit.enums.ValueSets.InteractionMode.primary
import org.eclipse.jubula.toolkit.enums.ValueSets.Operator.matches
import org.eclipse.jubula.toolkit.enums.ValueSets.Operator.simpleMatch
import org.eclipse.jubula.toolkit.rcp.config.RCPAUTConfiguration
import org.eclipse.jubula.toolkit.swing.SwingComponents
import org.eclipse.jubula.toolkit.swing.config.SwingAUTConfiguration
import org.eclipse.jubula.toolkit.swing.internal.impl.handler.JMenuBarActionHandler
import org.eclipse.jubula.toolkit.swt.SwtComponents
import org.eclipse.jubula.toolkit.swt.config.SWTAUTConfiguration
import org.eclipse.jubula.toolkit.swt.internal.impl.handler.ApplicationActionHandler
import org.eclipse.jubula.toolkit.swt.internal.impl.handler.MenuActionHandler
import org.eclipse.jubula.tools.AUTIdentifier
import org.eclipse.jubula.tools.internal.exception.JBVersionException
import org.nexial.core.NexialConst.PolyMatcher.REGEX
import org.nexial.core.SystemVariables.getDefaultLong
import org.nexial.core.plugins.javaui.JavaUIConst.SystemVariable
import org.nexial.core.plugins.javaui.JavaUIConst.embedded
import org.nexial.core.plugins.javaui.JavaUIConst.postAutAgentDelayMs
import org.nexial.core.plugins.javaui.JavaUIConst.screenshotExtension
import org.nexial.core.plugins.javaui.JubulaHelper.Companion.rcp
import org.nexial.core.plugins.javaui.JubulaHelper.Companion.swt
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.util.Locale.US
import javax.imageio.ImageIO
import org.eclipse.jubula.toolkit.base.components.TextComponent as BaseTextComponent

/**
 * each instance of `JubulaHelper` is designated for 1 app (1 AUT)
 */
abstract class JubulaHelper {
    protected var currentAutId: String? = null
    protected var binder: AutBinder? = null
    protected val objectMapping = mutableMapOf<String, String>()
    protected var agent: AUTAgent? = null
    internal var appStartupWaitMs = getDefaultLong(SystemVariable.appStartupWaitMs)
    internal var delayBetweenStepsMs = getDefaultLong(SystemVariable.delayBetweenStepsMs)

    companion object {
        @JvmStatic
        fun swt(): JubulaHelper = JubulaSWTHelper()

        @JvmStatic
        fun swing(): JubulaHelper = JubulaSwingHelper()

        @JvmStatic
        fun rcp(): JubulaHelper = JubulaRCPHelper()
    }

    abstract fun registerAut(name: String, appLocation: String, exe: String, vararg args: String?)

    open fun isConnected(): Boolean = agent!!.isConnected

    fun startEmbeddedAgent(): AUTAgent {
        agent = Embedded.INSTANCE.agent() ?: throw RuntimeException("Unable to connect to embedded agent")
        return latch(agent!!)
    }

    fun startAgent(host: String, port: Int): AUTAgent {
        agent = MakeR.createAUTAgent(host, port)
        return latch(agent!!)
    }

    protected fun latch(agent: AUTAgent): AUTAgent {
        try {
            agent.connect()
        } catch (e: CommunicationException) {
            when (e.cause) {
                is AlreadyConnectedException -> return agent
                is JBVersionException        -> throw e
                else                         -> {
                    Thread.interrupted()
                    Thread.sleep(1500L)
                    agent.connect()
                    ConsoleUtils.log("poll for connection to AUT agent within $postAutAgentDelayMs ms")
                }
            }
        }

        val timeoutAt = System.currentTimeMillis() + postAutAgentDelayMs
        while (System.currentTimeMillis() < timeoutAt) {
            try {
                if (agent.isConnected) break
                Thread.interrupted()
                Thread.sleep(200L)
            } catch (e: Exception) {
            }
        }

        if (agent.isConnected)
            return agent
        else
            throw RuntimeException("Unable to connect to AUT agent within $postAutAgentDelayMs ms; " +
                                   "try increasing wait time")
    }

    open fun startAgent(): AUTAgent {
        if (agent != null) return agent as AUTAgent

        val jubulaAgentConfig = getAgentConfig()
        return if (StringUtils.equals(jubulaAgentConfig, embedded))
            startEmbeddedAgent()
        else {
            val config = StringUtils.split(jubulaAgentConfig, ":")
            startAgent(config[0], NumberUtils.toInt(config[1]))
        }
    }

    open fun startAut(name: String) {
        if (agent == null) agent = startAgent()

        val autBinder = this.binder!!
        val configuration = autBinder.configuration
                            ?: throw IllegalArgumentException("No configuration for AUT '$name', registered yet?")

        if (agent != null && !autBinder.isStarted()) {
            currentAutId = name

            // give time for AUT to start/initialize
            try {
                Thread.interrupted()
            } catch (e: Exception) {
            }

            val autId = agent!!.startAUT(configuration)
                        ?: throw IllegalArgumentException("Unable to start AUT '$name'. Check logs for details")

            val aut = agent!!.getAUT(autId, getToolkit())
            aut.connect(appStartupWaitMs.toInt())
            aut.setHandler(ContinueExecutionHandler(suppressCheckFail = false,
                                                    suppressActionError = false,
                                                    suppressNotFound = false,
                                                    suppressConfigurationError = true))
            AUTRegistry.INSTANCE.register(aut)
            autBinder.aut = aut
            autBinder.id = autId

            postAutStart(autBinder)
        }
    }

    open fun disconnect() {
        stopAut()
        agent!!.disconnect()
    }

    open fun stopAut() {
        val binder = resolveBinder()
        if (!binder.isStarted())
            ConsoleUtils.log("AUT '$name' is already stopped or it was never started")
        else if (agent != null) {
            agent!!.stopAUT(binder.id)
            binder.stop()
        }
    }

    open fun takeScreenshot(saveTo: File) =
        try {
            saveTo.parentFile.mkdirs()
            val aut = resolveAut()
            val screenshot = aut.screenshot
            if (screenshot != null) {
                ImageIO.write(screenshot, screenshotExtension, saveTo)
                saveTo
            } else {
                ConsoleUtils.error("Unable to capture screenshot for AUT '$currentAutId'")
                null
            }
        } catch (e: Exception) {
            ConsoleUtils.error("Error during creation of screenshot" + e.message)
            null
        }

    fun addMapping(name: String, identifier: String) {
        objectMapping[name] = identifier
    }

    fun addMappings(vararg mappings: Pair<String, String>) {
        mappings.map { objectMapping[it.first] = it.second }
    }

    fun findTextBox(name: String): TextInputComponentActionHandler {
        checkComponentName(name)
        val component = MakeR.createCI<TextInputComponent>(objectMapping[name]!!)
                        ?: throw java.lang.RuntimeException("Unable to reference a text input component via '$name'")
        return when (this) {
            is JubulaSwingHelper -> SwingComponents.createJTextComponentActionHandler(component)
            is JubulaSWTHelper,
            is JubulaRCPHelper   -> SwtComponents.createTextInputComponentActionHandler(component)
            else                 -> throw IllegalArgumentException("Not supported: ${this::class.java}")
        }
    }

    fun findButton(name: String): ButtonComponentActionHandler {
        checkComponentName(name)
        val component = MakeR.createCI<ButtonComponent>(objectMapping[name]!!)
                        ?: throw java.lang.RuntimeException("Unable to reference a button component via '$name'")
        return when (this) {
            is JubulaSwingHelper -> SwingComponents.createAbstractButtonActionHandler(component)
            is JubulaSWTHelper,
            is JubulaRCPHelper   -> SwtComponents.createButtonComponentActionHandler(component)
            else                 -> throw IllegalArgumentException("Not supported: ${this::class.java}")
        }
    }

    @Throws(IllegalArgumentException::class)
    fun findLabel(name: String): TextComponentActionHandler {
        checkComponentName(name)
        val component = MakeR.createCI<TextComponent>(objectMapping[name]!!)
                        ?: throw java.lang.RuntimeException("Unable to reference a label component via '$name'")
        return when (this) {
            is JubulaSwingHelper -> SwingComponents.createJLabelActionHandler(component)
            is JubulaSWTHelper,
            is JubulaRCPHelper   -> SwtComponents.createLabelActionHandler(component)
            else                 -> throw IllegalArgumentException("Not supported: ${this::class.java}")
        }
    }

    fun clickMenu(vararg menus: String): Result<*> {
        require(menus.isNotEmpty()) { "invalid menu items: $menus" }
        return newMenuBarActionHandler().selectMenuEntryByTextpath("/" + menus.joinToString(separator = "/"),
                                                                   simpleMatch)
    }

    fun waitForTitleBar(text: String): Result<Any> {
        require(StringUtils.isNotBlank(text)) { "invalid text for title bar: $text" }
        val operator = if (StringUtils.startsWith(text, REGEX)) matches else simpleMatch
        val title = if (StringUtils.startsWith(text, REGEX)) StringUtils.substringAfter(text, REGEX) else text
        return newApplicationActionHandler().waitForWindow(title, operator, delayBetweenStepsMs.toInt())
    }

    fun assertText(name: String, text: String): Result<Any> {
        requiresNotBlank(text, "Invalid text to assert", text)

        checkComponentName(name)
        val component = MakeR.createCI<BaseTextComponent>(objectMapping[name]!!)
                        ?: throw java.lang.RuntimeException("Unable to reference a 'text' component via '$name'")

        val operator = if (StringUtils.startsWith(text, REGEX)) matches else simpleMatch
        val value = if (StringUtils.startsWith(text, REGEX)) StringUtils.substringAfter(text, REGEX) else text

        val cap = AbstractComponents.createTextComponent(component).checkText(value, operator)
        return resolveAut().execute(cap, null)
    }

    protected fun resolveBinder(): AutBinder {
        if (currentAutId == null) throw RuntimeException("current AUT id is unknown; unable to execute this command")
        return this.binder ?: throw RuntimeException("Missing AUT binder; AUT registered for profile '$currentAutId'?")
    }

    protected fun resolveAut(): AUT {
        val binder = resolveBinder()
        val aut = binder.aut ?: throw RuntimeException("AUT cannot be resolved via '$currentAutId'")
        if (!aut.isConnected) throw RuntimeException("AUT for '$currentAutId' is currently not connected")
        return aut
    }

    fun assertEditable(name: String): Result<Any> = findTextBox(name).checkEditability(true)

    fun assertPresence(name: String): Result<Any> = deriveComponentHandler(name).checkExistence(true)

    fun assertEnabled(name: String): Result<Any> = deriveComponentHandler(name).checkEnablement(true)

    fun assertDisabled(name: String): Result<Any> = deriveComponentHandler(name).checkEnablement(false)

    fun typeText(name: String, text: String): Result<Any> = findTextBox(name).replaceText(text)

    fun clickButton(name: String): Result<Any> = findButton(name).click(1, primary)

    protected abstract fun newMenuBarActionHandler(): MenuBarComponentActionHandler

    protected open fun newApplicationActionHandler() =
        org.eclipse.jubula.toolkit.concrete.internal.impl.handler.ApplicationActionHandler()

    protected fun checkComponentName(name: String) {
        requiresNotBlank(name, "Invalid component name", name)
        require(objectMapping.containsKey(name)) { "No component via name '$name'" }
    }

    protected fun deriveComponentHandler(name: String): GraphicsComponentActionHandler {
        checkComponentName(name)
        val component = MakeR.createCI<GraphicsComponent>(objectMapping[name]!!)
                        ?: throw java.lang.RuntimeException("Unable to reference a 'text' component via '$name'")
        return AbstractComponents.createGraphicsComponentActionHandler(component)
    }

    protected open fun getAgentConfig(): String = System.getProperty(SystemVariable.agent)

    protected abstract fun postAutStart(binder: AutBinder)

    protected abstract fun getToolkit(): ToolkitInfo
}

class JubulaSwingHelper : JubulaHelper() {
    override fun getToolkit() = SwingComponents.getToolkitInformation()

    override fun registerAut(name: String, appLocation: String, exe: String, vararg args: String?) {
        val binder = AutBinder()
        binder.configuration = SwingAUTConfiguration(name, name, exe, appLocation, args)
        this.binder = binder
    }

    override fun postAutStart(binder: AutBinder) {
        try {
            SwingComponents.createApplicationActionHandler().waitForWindow(".*", matches, 500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun newMenuBarActionHandler() = JMenuBarActionHandler()
    override fun newApplicationActionHandler() =
        org.eclipse.jubula.toolkit.swing.internal.impl.handler.ApplicationActionHandler()
}

open class JubulaSWTHelper : JubulaHelper() {
    override fun getToolkit() = SwtComponents.getToolkitInformation()

    override fun registerAut(name: String, appLocation: String, exe: String, vararg args: String?) {
        val binder = AutBinder()
        binder.configuration = SWTAUTConfiguration(name, name, exe, appLocation, args, US)
        this.binder = binder
    }

    override fun postAutStart(binder: AutBinder) {
        try {
            SwtComponents.createApplicationActionHandler().waitForWindow(".*", matches, 500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun newMenuBarActionHandler() = MenuActionHandler()

    override fun newApplicationActionHandler() = ApplicationActionHandler()
}

class JubulaRCPHelper : JubulaSWTHelper() {
    override fun registerAut(name: String, appLocation: String, exe: String, vararg args: String?) {
        val binder = AutBinder()
        binder.configuration = RCPAUTConfiguration(name, name, exe, appLocation, args, US)
        this.binder = binder
    }
}

class ContinueExecutionHandler(val suppressCheckFail: Boolean,
                               val suppressActionError: Boolean,
                               val suppressNotFound: Boolean,
                               val suppressConfigurationError: Boolean) : ExecutionExceptionHandler {

    override fun handle(exception: ExecutionException) {
        when (exception) {
            is CheckFailedException       -> if (!suppressCheckFail) throw exception else return
            is ActionException            -> if (!suppressActionError) throw exception else return
            is ComponentNotFoundException -> if (!suppressNotFound) throw exception else return
            is ConfigurationException     -> if (!suppressConfigurationError) throw exception else return
            else                          -> throw exception
        }
    }
}

class AutBinder {
    var aut: AUT? = null
    var id: AUTIdentifier? = null
    var configuration: AUTConfiguration? = null

    fun isStarted() = aut != null && id != null

    fun isRegistered() = configuration != null

    fun stop() {
        aut = null
        id = null
    }
}

class ObjectMapping {

    companion object {
        @Throws(Throwable::class)
        @JvmStatic
        fun main(args: Array<String>) {
            // System.setProperty(JavaUIConst.SystemVariable.agent, "embedded");
            System.setProperty(SystemVariable.agent, "localhost:60000")

            /* This class can be used to start the AUT Agent and the AUT. This is needed */
            // inspectSimpleAdder()
            inspectEclipse()
        }

        private fun inspectSimpleAdder() {
            val helper = swt()
            helper.registerAut("SimpleAdder",
                               "C:\\tools\\jubula_8.8.0.034\\examples\\AUTs\\SimpleAdder\\swt",
                               "SimpleAdderSWT.bat")
            helper.startAut("SimpleAdder")
            // helper.disconnect()
        }

        private fun inspectEclipse() {
            val helper = rcp()
            helper.registerAut("eclipse", "C:\\tools\\eclipse-2021-06\\eclipse", "eclipsec.exe", "-noSplash")
            helper.startAut("eclipse")
            // helper.disconnect()
        }
    }
}