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
package org.nexial.core.plugins.web

import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.*
import org.apache.commons.text.WordUtils
import org.nexial.commons.proc.RuntimeUtils
import org.nexial.core.NexialConst.BrowserStack.*
import org.nexial.core.NexialConst.BrowserType.*
import org.nexial.core.NexialConst.Web
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.web.WebDriverHelper.Companion.newInstance
import org.nexial.core.plugins.ws.WsCommand
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

/**
 * extension to [Browser] in support of all things BrowserStack.
 */
class BrowserStackHelper(context: ExecutionContext) : CloudWebTestingPlatform(context) {

    override fun initWebDriver(): WebDriver {
        val username = context.getStringData(KEY_USERNAME)
        val automateKey = context.getStringData(AUTOMATEKEY)
        if (StringUtils.isBlank(username) || StringUtils.isBlank(automateKey))
            throw RuntimeException("Both $KEY_USERNAME and $AUTOMATEKEY are required to use BrowserStack")
        val capabilities = MutableCapabilities()
        WebDriverCapabilityUtils.initCapabilities(context, capabilities)

        // support any existing or new browserstack.* configs
        val prefix = "browserstack."
        val config = context.getDataByPrefix(prefix)
        handleLocal(capabilities, config)
        handleOS(capabilities, config)
        handleTargetBrowser(capabilities, config)
        handleProjectMeta(capabilities, config)
        handleOthers(capabilities, config)

        // remaining configs specific to browserstack
        config.forEach { (key: String, value: String?) ->
            // might be just `key`, might be browserstack.`key`... can't known for sure so we do both
            WebDriverCapabilityUtils.setCapability(capabilities, key, value)
            WebDriverCapabilityUtils.setCapability(capabilities, prefix + key, value)
        }

        isPageSourceSupported = false

        return try {
            val driver = RemoteWebDriver(URL("$BASE_PROTOCOL$username:$automateKey$BASE_URL"), capabilities)
            saveSessionId(driver)

            // safari's usually a bit slower to come up...
            if (browser == safari) {
                ConsoleUtils.log("5 second grace period for starting up Safari on BrowserStack...")
                try {
                    Thread.sleep(5000)
                } catch (e: InterruptedException) {
                }
            }
            driver
        } catch (e: MalformedURLException) {
            throw RuntimeException("Unable to initialize BrowserStack session: " + e.message, e)
        } catch (e: WebDriverException) {
            throw RuntimeException("Unable to initialize BrowserStack session: " + e.message, e)
        }
    }

    fun reportExecutionStatus(summary: ExecutionSummary) = reportExecutionStatus(context, summary)

    protected fun handleLocal(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
        val enableLocal = if (config.containsKey("local"))
            BooleanUtils.toBoolean(config.remove("local"))
        else
            context.getBooleanData(KEY_ENABLE_LOCAL, getDefaultBool(KEY_ENABLE_LOCAL))
        if (!enableLocal) return

        isTerminateLocal = context.getBooleanData(KEY_TERMINATE_LOCAL, getDefaultBool(KEY_TERMINATE_LOCAL))
        val automateKey = context.getStringData(AUTOMATEKEY)
        requiresNotBlank(automateKey, "BrowserStack Access Key not defined via '$AUTOMATEKEY'", automateKey)
        capabilities.setCapability("browserstack.local", true)

        try {
            val helper = newInstance(browserstack, context)
            val driver = helper.resolveDriver()
            val browserstacklocal = helper.config.baseName
            if (isTerminateLocal) RuntimeUtils.terminateInstance(browserstacklocal)

            // start browserstack local, but wait (3s) for it to start up completely.
            ConsoleUtils.log("starting new instance of $browserstacklocal...")
            RuntimeUtils.runAppNoWait(driver.parent, driver.name, listOf("--key", automateKey))
            Thread.sleep(3000)
            isRunningLocal = true
            localExeName = driver.name
        } catch (e: IOException) {
            throw RuntimeException("unable to start BrowserStackLocal: ${e.message}", e)
        } catch (e: InterruptedException) {
            throw RuntimeException("unable to start BrowserStackLocal: ${e.message}", e)
        }
    }

    protected fun handleOS(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
        // os specific setting, including mobile devices
        val targetOs = StringUtils.defaultIfBlank(config.remove("os"), context.getStringData(KEY_OS))
        val targetOsVer = StringUtils.defaultIfBlank(config.remove("os_version"), context.getStringData(KEY_OS_VER))
        val bsCapsUrl = "Check https://www.browserstack.com/automate/capabilities for more details"
        val msgRequired = "'browserstack.device' is required for "

        if (StringUtils.equalsIgnoreCase(targetOs, "ANDROID") || StringUtils.equalsIgnoreCase(targetOs, "IOS")) {
            val device = config.remove("device")
            if (StringUtils.isBlank(device)) throw RuntimeException("$msgRequired$targetOs. $bsCapsUrl")

            WebDriverCapabilityUtils.setCapability(capabilities, "device", device)
            WebDriverCapabilityUtils.setCapability(capabilities, "os_version", targetOsVer)
            val realMobile = BooleanUtils.toBoolean(StringUtils.defaultIfBlank(config.remove("real_mobile"), "false"))
            WebDriverCapabilityUtils.setCapability(capabilities, "real_mobile", realMobile)
            WebDriverCapabilityUtils.setCapability(capabilities, "realMobile", realMobile)
            isRunningAndroid = StringUtils.equalsIgnoreCase(targetOs, "ANDROID")
            isRunningIOS = StringUtils.equalsIgnoreCase(targetOs, "IOS")
            this.device = device
            isMobile = realMobile
            return
        }

        // resolution only works for non-mobile platforms
        WebDriverCapabilityUtils.setCapability(
                capabilities, "resolution",
                StringUtils.defaultIfBlank(config.remove("resolution"),
                                           StringUtils.defaultIfBlank(context.getStringData(KEY_RESOLUTION),
                                                                      context.getStringData(Web.BROWSER_WINDOW_SIZE))))
        if (StringUtils.isNotBlank(targetOs) && StringUtils.isNotBlank(targetOsVer)) {
            WebDriverCapabilityUtils.setCapability(capabilities, "os", StringUtils.upperCase(targetOs))
            WebDriverCapabilityUtils.setCapability(capabilities, "os_version", targetOsVer)
            os = targetOs
            isRunningWindows = StringUtils.startsWithIgnoreCase(targetOs, "WIN")
            isRunningOSX = StringUtils.equalsIgnoreCase(targetOs, "OS X")
            return
        }

        // if no target OS specified, then we'll just stick to automation host's OS
        if (IS_OS_WINDOWS) {
            WebDriverCapabilityUtils.setCapability(capabilities, "os", "Windows")
            WebDriverCapabilityUtils.setCapability(capabilities, "os_version", when {
                IS_OS_WINDOWS_7     -> "7"
                IS_OS_WINDOWS_8     -> "8"
                IS_OS_WINDOWS_10    -> "10"
                IS_OS_WINDOWS_2008  -> "2008"
                IS_OS_WINDOWS_95    -> "95"
                IS_OS_WINDOWS_98    -> "98"
                IS_OS_WINDOWS_2000  -> "2000"
                IS_OS_WINDOWS_2003  -> "2003"
                IS_OS_WINDOWS_2012  -> "2012"
                IS_OS_WINDOWS_NT    -> "NT"
                IS_OS_WINDOWS_VISTA -> "VISTA"
                IS_OS_WINDOWS_XP    -> "XP"
                else                -> ""
            })
            isRunningWindows = true
            os = "Windows"
            return
        }

        if (IS_OS_MAC_OSX) {
            WebDriverCapabilityUtils.setCapability(capabilities, "os", "OS X")
            WebDriverCapabilityUtils.setCapability(capabilities, "os_version", when {
                IS_OS_MAC_OSX_MAVERICKS     -> "Mavericks"
                IS_OS_MAC_OSX_BIG_SUR       -> "Big Sur"
                IS_OS_MAC_OSX_CATALINA      -> "Catalina"
                IS_OS_MAC_OSX_CHEETAH       -> "Cheetah"
                IS_OS_MAC_OSX_EL_CAPITAN    -> "El Capitan"
                IS_OS_MAC_OSX_HIGH_SIERRA   -> "High Sierra"
                IS_OS_MAC_OSX_JAGUAR        -> "Jaguar"
                IS_OS_MAC_OSX_LEOPARD       -> "Leopard"
                IS_OS_MAC_OSX_LION          -> "Lion"
                IS_OS_MAC_OSX_MOJAVE        -> "Mojave"
                IS_OS_MAC_OSX_MOUNTAIN_LION -> "Mountain Lion"
                IS_OS_MAC_OSX_PANTHER       -> "Panther"
                IS_OS_MAC_OSX_PUMA          -> "Puma"
                IS_OS_MAC_OSX_SIERRA        -> "Sierra"
                IS_OS_MAC_OSX_SNOW_LEOPARD  -> "Snow Leopard"
                IS_OS_MAC_OSX_TIGER         -> "Tiger"
                IS_OS_MAC_OSX_YOSEMITE      -> "Yosemite"
                else                        -> ""
            })
            isRunningOSX = true
            os = "OS X"
        }
    }

    protected fun handleTargetBrowser(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
        browserName = StringUtils.defaultIfBlank(config.remove("browser"), context.getStringData(KEY_BROWSER))
        if (StringUtils.startsWithIgnoreCase(browserName, "iPad") ||
            StringUtils.startsWithIgnoreCase(browserName, "iPhone") ||
            StringUtils.startsWithIgnoreCase(browserName, "android")) {
            WebDriverCapabilityUtils.setCapability(capabilities, "browserName", browserName)
            if (StringUtils.startsWithIgnoreCase(browserName, "iPhone")) browser = iphone
            return
        }

        if (StringUtils.isNotBlank(browserName))
            WebDriverCapabilityUtils.setCapability(
                    capabilities,
                    "browser",
                    if (StringUtils.length(browserName) < 3)
                        StringUtils.upperCase(browserName)
                    else
                        WordUtils.capitalize(browserName))

        browserVersion = StringUtils.defaultIfBlank(config.remove("browser_version"),
                                                    context.getStringData(KEY_BROWSER_VER))
        if (StringUtils.isNotBlank(browserVersion))
            WebDriverCapabilityUtils.setCapability(capabilities, "browser_version", browserVersion)
        if (StringUtils.startsWithIgnoreCase(browserName, "Firefox")) browser = firefox
        if (StringUtils.startsWithIgnoreCase(browserName, "Chrome")) browser = chrome
        if (StringUtils.startsWithIgnoreCase(browserName, "Safari")) browser = safari
        if (StringUtils.startsWithIgnoreCase(browserName, "IE")) browser = ie
        if (StringUtils.startsWithIgnoreCase(browserName, "Edge")) browser = edge
    }

    protected fun handleProjectMeta(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
        WebDriverCapabilityUtils.setCapability(
                capabilities,
                "build",
                StringUtils.defaultIfBlank(config.remove("build"), context.getStringData(KEY_BUILD_NUM)))

        val execDef = context.execDef
        if (execDef != null) {
            if (execDef.project != null && StringUtils.isNotBlank(execDef.project.name))
                WebDriverCapabilityUtils.setCapability(capabilities, "project", execDef.project.name)
            if (StringUtils.isNotBlank(execDef.testScript))
                WebDriverCapabilityUtils.setCapability(capabilities, "name", File(execDef.testScript).name)
        }
    }

    protected fun handleOthers(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
        val debug = if (config.containsKey("debug"))
            BooleanUtils.toBoolean(config.remove("debug"))
        else
            context.getBooleanData(KEY_DEBUG, getDefaultBool(KEY_DEBUG))

        WebDriverCapabilityUtils.setCapability(capabilities, "browserstack.debug", debug)
        if (debug) {
            WebDriverCapabilityUtils.setCapability(capabilities, "browserstack.console", "verbose")
            WebDriverCapabilityUtils.setCapability(capabilities, "browserstack.networkLogs", true)
        }

        WebDriverCapabilityUtils.setCapability(capabilities, "browserstack.use_w3c",
                                               BooleanUtils.toBoolean(config.remove("use_w3c")))
        if (context.hasData(KEY_CAPTURE_CRASH))
            WebDriverCapabilityUtils.setCapability(capabilities, "browserstack.captureCrash",
                                                   context.getBooleanData(KEY_CAPTURE_CRASH))
    }

    public override fun terminateLocal() {
        if (isRunningLocal && isTerminateLocal) RuntimeUtils.terminateInstance(localExeName)
    }

    companion object {
        fun reportExecutionStatus(context: ExecutionContext, summary: ExecutionSummary) {
            val sessionId = getSessionId(context) ?: return
            var url = StringUtils.replace(SESSION_URL, "\${username}", context.getStringData(KEY_USERNAME))
            url = StringUtils.replace(url, "\${automatekey}", context.getStringData(AUTOMATEKEY))
            url = StringUtils.replace(url, "\${sessionId}", sessionId)
            val payload = """{"status":"${if (summary.failCount > 0) "failed" else "passed"}", "reason":"${
                formatStatusDescription(summary)
            }"}"""

            ConsoleUtils.log("reporting execution status to BrowserStack...")
            val wsCommand = context.findPlugin("ws") as? WsCommand ?: return
            wsCommand.put(url, payload, RandomStringUtils.randomAlphabetic(5))
        }
    }
}