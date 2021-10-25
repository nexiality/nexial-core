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

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.proc.RuntimeUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.web.URLEncodingUtils
import org.nexial.core.NexialConst.BrowserType.crossbrowsertesting
import org.nexial.core.NexialConst.CrossBrowserTesting.*
import org.nexial.core.NexialConst.Data.BUILD_NO
import org.nexial.core.NexialConst.Data.SCRIPT_REF_PREFIX
import org.nexial.core.NexialConst.Web
import org.nexial.core.NexialConst.Ws.WS_BASIC_PWD
import org.nexial.core.NexialConst.Ws.WS_BASIC_USER
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.web.WebDriverHelper.Companion.newInstance
import org.nexial.core.plugins.ws.WsCommand
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.WebDriverUtils
import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

/**
 * extension to [Browser] in support of all things CrossBrowserTesting.
 */
class CrossBrowserTestingHelper(context: ExecutionContext) : CloudWebTestingPlatform(context) {
    private val docUrl = "Check $REFERENCE_URL for more details"

    override fun initWebDriver(): WebDriver {
        // support any existing or new cbt.* configs
        val config = context.getDataByPrefix(NS)
        val username = config.remove(KEY_USERNAME)
        val authKey = config.remove(KEY_AUTHKEY)
        if (StringUtils.isBlank(username) || StringUtils.isBlank(authKey))
            throw RuntimeException("Both $KEY_USERNAME and $KEY_AUTHKEY are required to use CrossBrowserTesting")

        val capabilities = MutableCapabilities()
        WebDriverCapabilityUtils.initCapabilities(context, capabilities)
        handleLocal(username, authKey, config)
        handlePlatform(capabilities, config)
        handleProjectMeta(capabilities, config)

        // remaining configs specific to cbt
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_ENABLE_VIDEO,
                                               config.getOrDefault(KEY_ENABLE_VIDEO, DEF_ENABLE_VIDEO))
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_RECORD_NETWORK,
                                               config.getOrDefault(KEY_RECORD_NETWORK, DEF_RECORD_NETWORK))
        config.forEach { (key: String?, value: String?) ->
            WebDriverCapabilityUtils.setCapability(capabilities, key, value)
        }
        isPageSourceSupported = false

        return try {
            val url = "$BASE_PROTOCOL${URLEncodingUtils.encodeAuth(username)}:$authKey$BASE_URL"
            val driver = RemoteWebDriver(URL(url), capabilities)
            WebDriverUtils.saveSessionId(context, driver)
            driver
        } catch (e: MalformedURLException) {
            throw RuntimeException("Unable to initialize CrossBrowserTesting session: ${e.message}", e)
        } catch (e: WebDriverException) {
            throw RuntimeException("Unable to initialize CrossBrowserTesting session: ${e.message}", e)
        }
    }

    fun reportExecutionStatus(summary: ExecutionSummary) = reportExecutionStatus(context, summary)

    protected fun handleLocal(username: String?, authkey: String?, config: MutableMap<String, String>) {
        val enableLocal =
                config.containsKey(KEY_ENABLE_LOCAL) && BooleanUtils.toBoolean(config.remove(KEY_ENABLE_LOCAL))
        if (!enableLocal) return
        // default is true for backward compatibility
        isTerminateLocal = if (!config.containsKey(KEY_TERMINATE_LOCAL))
            true
        else
            BooleanUtils.toBoolean(config.remove(KEY_TERMINATE_LOCAL))

        try {
            val helper = newInstance(crossbrowsertesting, context)
            val driver = helper.resolveDriver()
            val cbtLocal = helper.config.baseName
            if (isTerminateLocal) RuntimeUtils.terminateInstance(cbtLocal)

            val cmdlineArgs = mutableListOf("--username", username, "--authkey", authkey, "--acceptAllCerts")

            val localStartWaitMs = StringUtils.trim(config.remove(KEY_LOCAL_START_WAITMS))
            val useReadyFile = StringUtils.isEmpty(localStartWaitMs) ||
                               StringUtils.equals(localStartWaitMs, AUTO_LOCAL_START_WAIT)
            ConsoleUtils.log("starting new instance of $cbtLocal...")
            if (useReadyFile) {
                val waitMs = MAX_LOCAL_START_WAITMS
                FileUtils.deleteQuietly(File(LOCAL_READY_FILE))
                cmdlineArgs.add("--ready")
                cmdlineArgs.add(LOCAL_READY_FILE)
                RuntimeUtils.runAppNoWait(driver.parent, driver.name, cmdlineArgs)
                ConsoleUtils.log("waiting for $cbtLocal to start/stabilize, up to ${waitMs}ms...")
                val maxWaitTime = System.currentTimeMillis() + waitMs
                while (!FileUtil.isFileReadable(LOCAL_READY_FILE)) {
                    Thread.sleep(500)
                    if (System.currentTimeMillis() > maxWaitTime)
                        throw IOException("CrossBrowserTesting Local executable NOT ready within max wait time")
                }
            } else {
                RuntimeUtils.runAppNoWait(driver.parent, driver.name, cmdlineArgs)
                val waitMs = NumberUtils.toLong(localStartWaitMs, DEF_LOCAL_START_WAITMS)
                ConsoleUtils.log("waiting for $cbtLocal to start/stabilize: ${waitMs}ms")
                Thread.sleep(waitMs)
            }
            isRunningLocal = true
            localExeName = driver.name
        } catch (e: IOException) {
            throw RuntimeException("unable to start CrossBrowserTesting Local: ${e.message}", e)
        } catch (e: InterruptedException) {
            throw RuntimeException("unable to start CrossBrowserTesting Local: ${e.message}", e)
        }
    }

    /**
     * os specific setting, including mobile devices
     */
    protected fun handlePlatform(capabilities: MutableCapabilities, config: MutableMap<String, String>) {
        val browserName = config.remove(KEY_BROWSER)
        if (StringUtils.isBlank(browserName))
            throw RuntimeException("'$NS$KEY_BROWSER' is required to use CrossBrowserTesting. $docUrl")
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_BROWSER, browserName)
        val targetOS = config.remove(KEY_PLATFORM)
        if (StringUtils.isNotBlank(targetOS)) {
            // not mobile for sure
            WebDriverCapabilityUtils.setCapability(capabilities, KEY_PLATFORM, targetOS)
            WebDriverCapabilityUtils.setCapability(
                    capabilities, KEY_RESOLUTION,
                    StringUtils.defaultIfBlank(config.remove(KEY_RESOLUTION),
                                               context.getStringData(Web.BROWSER_WINDOW_SIZE)))
            WebDriverCapabilityUtils.setCapability(capabilities, KEY_BROWSER_VER, config.remove(KEY_BROWSER_VER))
            isMobile = false
            ConsoleUtils.log("[CBT] setting up $browserName on $targetOS")
            return
        }

        val targetMobileOS = config.remove(KEY_MOBILE_PLATFORM)
        // we gotta have either `KEY_PLATFORM` or `KEY_MOBILE_PLATFORM`
        if (StringUtils.isBlank(targetMobileOS))
            throw RuntimeException("Either '$NS$KEY_PLATFORM' or '$NS$KEY_MOBILE_PLATFORM' is required. $docUrl")

        // mobile for sure, at this point
        val deviceName = config.remove(KEY_DEVICE)
        if (StringUtils.isBlank(deviceName))
            throw RuntimeException("'$NS$KEY_DEVICE' is required for mobile web testing. $docUrl")
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_MOBILE_PLATFORM, targetMobileOS)
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_MOBILE_PLATFORM_VER,
                                               config.remove(KEY_MOBILE_PLATFORM_VER))
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_DEVICE, deviceName)
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_DEVICE_ORIENTATION,
                                               config.remove(KEY_DEVICE_ORIENTATION))
        isMobile = true
        ConsoleUtils.log("[CBT] setting up $browserName on $deviceName/$targetMobileOS")
    }

    protected fun handleProjectMeta(capabilities: MutableCapabilities, config: MutableMap<String, String>) {
        val buildNum = StringUtils.defaultIfBlank(config.remove(KEY_BUILD),
                                                  context.getStringData(SCRIPT_REF_PREFIX + BUILD_NO))
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_BUILD, buildNum)
        val execDef = context.execDef ?: return
        var testName: String? = null
        if (execDef.project != null && StringUtils.isNotBlank(execDef.project.name)) testName = execDef.project.name
        if (StringUtils.isNotBlank(execDef.testScript)) {
            val scriptName = StringUtils.substringAfterLast(StringUtils.replace(execDef.testScript, "\\", "/"), "/")
            testName += (if (StringUtils.isNotBlank(testName)) " / " else "") + scriptName
        }
        WebDriverCapabilityUtils.setCapability(capabilities, KEY_NAME, testName)
    }

    public override fun terminateLocal() {
        if (isRunningLocal && isTerminateLocal) RuntimeUtils.terminateInstance(localExeName)
    }

    companion object {
        fun reportExecutionStatus(context: ExecutionContext, summary: ExecutionSummary) {
            ConsoleUtils.log("reporting execution status to CrossBrowserTesting...")

            val oldUsername = context.getStringData(WS_BASIC_USER)
            val oldPassword = context.getStringData(WS_BASIC_PWD)
            context.setData(WS_BASIC_USER, context.getStringData(NS + KEY_USERNAME))
            context.setData(WS_BASIC_PWD, context.getStringData(NS + KEY_AUTHKEY))

            val sessionId = WebDriverUtils.getSessionId(context) ?: return
            val statusApi = StringUtils.replace(SESSION_URL, "{session}", sessionId)

            val wsCommand = context.findPlugin("ws") as? WsCommand ?: return
            try {
                wsCommand.put(statusApi,
                              """{"action":"set_score", "score":"${if (summary.failCount > 0) "fail" else "pass"}"}""",
                              RandomStringUtils.randomAlphabetic(5))
                wsCommand.put(statusApi,
                              """{"action":"set_description", "description":"${formatStatusDescription(summary)}"}""",
                              RandomStringUtils.randomAlphabetic(5))
            } finally {
                // put BASIC AUTH back
                context.setData(WS_BASIC_USER, oldUsername)
                context.setData(WS_BASIC_PWD, oldPassword)
            }
        }
    }
}