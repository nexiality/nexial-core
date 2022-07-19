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

package org.nexial.core.plugins.browserstack

import io.appium.java_client.remote.MobileCapabilityType.*
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.text.WordUtils
import org.nexial.commons.proc.RuntimeUtils
import org.nexial.core.NexialConst.BrowserType.*
import org.nexial.core.NexialConst.CloudWebTesting.BASE_PROTOCOL
import org.nexial.core.NexialConst.CloudWebTesting.BASE_PROTOCOL2
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.NexialConst.RB
import org.nexial.core.NexialConst.Web.BROWSER_WINDOW_SIZE
import org.nexial.core.NexialConst.Web.OPT_SECURE_BROWSERSTACK
import org.nexial.core.NexialConst.Ws.*
import org.nexial.core.SystemVariables
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.browserstack.BrowserStack.APP_PREFIX
import org.nexial.core.plugins.browserstack.BrowserStack.AUTOMATEKEY
import org.nexial.core.plugins.browserstack.BrowserStack.AUTOMATE_KEY
import org.nexial.core.plugins.browserstack.BrowserStack.BROWSER
import org.nexial.core.plugins.browserstack.BrowserStack.BUILD
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_BROWSER
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_BROWSER_VER
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_BUILD_NUM
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_CAPTURE_CRASH
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_DEBUG
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_ENABLE_LOCAL
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_OS
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_OS_VER
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_RESOLUTION
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_TERMINATE_LOCAL
import org.nexial.core.plugins.browserstack.BrowserStack.KEY_USERNAME
import org.nexial.core.plugins.browserstack.BrowserStack.PREFIX
import org.nexial.core.plugins.browserstack.BrowserStack.PROJECT
import org.nexial.core.plugins.browserstack.BrowserStack.TEST_NAME
import org.nexial.core.plugins.browserstack.BrowserStack.USERNAME
import org.nexial.core.plugins.browserstack.BrowserStack.Url.BASE_URL
import org.nexial.core.plugins.browserstack.BrowserStack.Url.DELETE_UPLOADED
import org.nexial.core.plugins.browserstack.BrowserStack.Url.HUB
import org.nexial.core.plugins.browserstack.BrowserStack.Url.HUB2
import org.nexial.core.plugins.browserstack.BrowserStack.Url.LIST_BROWSER
import org.nexial.core.plugins.browserstack.BrowserStack.Url.LIST_DEVICES
import org.nexial.core.plugins.browserstack.BrowserStack.Url.LIST_UPLOADED
import org.nexial.core.plugins.browserstack.BrowserStack.Url.MOBILE_SESSION_URL
import org.nexial.core.plugins.browserstack.BrowserStack.Url.UPLOAD
import org.nexial.core.plugins.browserstack.BrowserStack.Url.WEB_SESSION_URL
import org.nexial.core.plugins.mobile.MobileProfile
import org.nexial.core.plugins.web.CloudWebTestingPlatform
import org.nexial.core.plugins.web.WebDriverCapabilityUtils
import org.nexial.core.plugins.web.WebDriverHelper
import org.nexial.core.plugins.ws.Response
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JSONPath
import org.nexial.core.utils.JsonUtils
import org.nexial.core.utils.WebDriverUtils
import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.File
import java.io.IOException
import java.lang.System.lineSeparator
import java.net.MalformedURLException
import java.net.URL
import javax.validation.constraints.NotNull

/**
 * extension to [org.nexial.core.plugins.web.Browser] in support of all things BrowserStack, and to
 * [org.nexial.core.plugins.mobile.MobileCommand] in support of mobile testing on BrowserStack.
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
            // might be just `key`, might be browserstack.`key`... can't know for sure, so we do both
            WebDriverCapabilityUtils.setCapability(capabilities, key, value)
            WebDriverCapabilityUtils.setCapability(capabilities, prefix + key, value)
        }

        isPageSourceSupported = false

        val useSecure = context.getBooleanData(OPT_SECURE_BROWSERSTACK, getDefaultBool(OPT_SECURE_BROWSERSTACK))
        val url = "${if (useSecure) BASE_PROTOCOL else BASE_PROTOCOL2}$username:$automateKey${BASE_URL}"

        return try {
            val driver = RemoteWebDriver(URL(url), capabilities)
            WebDriverUtils.saveSessionId(context, driver)

            // safari's usually a bit slower to come up...
            if (browser == safari) {
                ConsoleUtils.log("5-second grace period for starting up Safari on BrowserStack...")
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

    public override fun terminateLocal() {
        if (isRunningLocal && isTerminateLocal) RuntimeUtils.terminateInstance(localExeName)
    }

    fun reportExecutionStatus(summary: ExecutionSummary) = reportExecutionStatus(context, summary)

    fun uploadApp(config: BrowserStackConfig, app: String, customId: String?): Map<String, String> {
        val uploadResponse = newWebClient(config, WS_MULTIPART_CONTENT_TYPE).postMultipart(
            UPLOAD, "${if (StringUtils.isNotBlank(customId)) "custom_id=$customId\n" else ""}file=$app", "file")

        // 5. validate upload outcome
        if (uploadResponse.returnCode != 200)
            throw RuntimeException(RB.BrowserStack.text("upload.app.fail", app, showHttpError(uploadResponse)))

        // 6. extract response payload
        val responseBody = JsonUtils.toJSONObject(uploadResponse.body)
        val appUrl = JSONPath.find(responseBody, "app_url")
        return mapOf("app_url" to appUrl,
                     "app_id" to appUrl.substringAfter(APP_PREFIX),
                     "custom_id" to JSONPath.find(responseBody, "custom_id"),
                     "shareable_id" to JSONPath.find(responseBody, "shareable_id"))
    }

    fun saveUploadApps(config: BrowserStackConfig, customId: String?): String {
        val response = newWebClient(config).get(
            "$LIST_UPLOADED${if (StringUtils.isNotBlank(customId)) "/$customId" else ""}", "")
        if (response.returnCode != 200)
            throw RuntimeException(RB.BrowserStack.text("list.app.fail", config.user, showHttpError(response)))
        return response.body
    }

    fun deleteUploadedApp(config: BrowserStackConfig, appId: String): String {
        val response = newWebClient(config).delete(DELETE_UPLOADED + appId, "")
        if (response.returnCode != 200)
            throw RuntimeException(RB.BrowserStack.text("delete.app.fail", config.user, appId, showHttpError(response)))
        return response.body
    }

    fun listBrowsers(config: BrowserStackConfig): String {
        val response = newWebClient(config).get(LIST_BROWSER, "")
        if (response.returnCode != 200)
            throw RuntimeException(RB.BrowserStack.text("list.browsers.fail", config.user, showHttpError(response)))
        return response.body
    }

    fun listDevices(config: BrowserStackConfig): String {
        val response = newWebClient(config).get(LIST_DEVICES, "")
        if (response.returnCode != 200)
            throw RuntimeException(RB.BrowserStack.text("list.devices.fail", config.user, showHttpError(response)))
        return response.body
    }

    fun getAppAutomateSessionDetail(config: BrowserStackConfig, sessionId: String): String {
        val response = newWebClient(config).get(resolveSessionUrl(MOBILE_SESSION_URL, sessionId), "")
        if (response.returnCode != 200)
            throw RuntimeException(RB.BrowserStack.text("session.not.found", config.user, showHttpError(response)))
        return response.body
    }

    fun getAutomateSessionDetail(config: BrowserStackConfig, sessionId: String): String {
        val response = newWebClient(config).get(resolveSessionUrl(WEB_SESSION_URL, sessionId), "")
        if (response.returnCode != 200)
            throw RuntimeException(RB.BrowserStack.text("session.not.found", config.user, showHttpError(response)))
        return response.body
    }

    fun updateAutomateSessionStatus(config: BrowserStackConfig, sessionId: String, status: String, reason: String) =
        updateSessionStatus(config, resolveSessionUrl(WEB_SESSION_URL, sessionId), status, reason)

    fun updateAppAutomateSessionStatus(config: BrowserStackConfig, sessionId: String, status: String, reason: String) =
        updateSessionStatus(config, resolveSessionUrl(MOBILE_SESSION_URL, sessionId), status, reason)

    private fun handleLocal(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
        val enableLocal = if (config.containsKey("local"))
            BooleanUtils.toBoolean(config.remove("local"))
        else
            context.getBooleanData(KEY_ENABLE_LOCAL, SystemVariables.getDefaultBool(KEY_ENABLE_LOCAL))
        if (!enableLocal) return

        isTerminateLocal = context.getBooleanData(KEY_TERMINATE_LOCAL,
                                                  SystemVariables.getDefaultBool(KEY_TERMINATE_LOCAL))
        val automateKey = context.getStringData(AUTOMATEKEY)
        requiresNotBlank(automateKey, "BrowserStack Access Key not defined via '$AUTOMATEKEY'", automateKey)
        capabilities.setCapability("browserstack.local", true)

        try {
            val helper = WebDriverHelper.newInstance(browserstack, context)
            val driver = helper.resolveDriver()
            val browserStackLocal = helper.config.baseName
            if (isTerminateLocal) RuntimeUtils.terminateInstance(browserStackLocal)

            // start browserstack local, but wait (3s) for it to start up completely.
            ConsoleUtils.log("starting new instance of $browserStackLocal...")
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

    private fun handleOS(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
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
                                                                  context.getStringData(BROWSER_WINDOW_SIZE))))
        if (StringUtils.isNotBlank(targetOs) && StringUtils.isNotBlank(targetOsVer)) {
            WebDriverCapabilityUtils.setCapability(capabilities, "os", StringUtils.upperCase(targetOs))
            WebDriverCapabilityUtils.setCapability(capabilities, "os_version", targetOsVer)
            os = targetOs
            isRunningWindows = StringUtils.startsWithIgnoreCase(targetOs, "WIN")
            isRunningOSX = StringUtils.equalsIgnoreCase(targetOs, "OS X")
            return
        }

        // if no target OS specified, then we'll just stick to automation host's OS
        if (SystemUtils.IS_OS_WINDOWS) {
            WebDriverCapabilityUtils.setCapability(capabilities, "os", "Windows")
            WebDriverCapabilityUtils.setCapability(capabilities, "os_version", when {
                SystemUtils.IS_OS_WINDOWS_7     -> "7"
                SystemUtils.IS_OS_WINDOWS_8     -> "8"
                SystemUtils.IS_OS_WINDOWS_10    -> "10"
                SystemUtils.IS_OS_WINDOWS_2008  -> "2008"
                SystemUtils.IS_OS_WINDOWS_95    -> "95"
                SystemUtils.IS_OS_WINDOWS_98    -> "98"
                SystemUtils.IS_OS_WINDOWS_2000  -> "2000"
                SystemUtils.IS_OS_WINDOWS_2003  -> "2003"
                SystemUtils.IS_OS_WINDOWS_2012  -> "2012"
                SystemUtils.IS_OS_WINDOWS_NT    -> "NT"
                SystemUtils.IS_OS_WINDOWS_VISTA -> "VISTA"
                SystemUtils.IS_OS_WINDOWS_XP    -> "XP"
                else                            -> ""
            })
            isRunningWindows = true
            os = "Windows"
            return
        }

        if (SystemUtils.IS_OS_MAC_OSX) {
            WebDriverCapabilityUtils.setCapability(capabilities, "os", "OS X")
            WebDriverCapabilityUtils.setCapability(capabilities, "os_version", when {
                SystemUtils.IS_OS_MAC_OSX_MAVERICKS     -> "Mavericks"
                SystemUtils.IS_OS_MAC_OSX_BIG_SUR       -> "Big Sur"
                SystemUtils.IS_OS_MAC_OSX_CATALINA      -> "Catalina"
                SystemUtils.IS_OS_MAC_OSX_CHEETAH       -> "Cheetah"
                SystemUtils.IS_OS_MAC_OSX_EL_CAPITAN    -> "El Capitan"
                SystemUtils.IS_OS_MAC_OSX_HIGH_SIERRA   -> "High Sierra"
                SystemUtils.IS_OS_MAC_OSX_JAGUAR        -> "Jaguar"
                SystemUtils.IS_OS_MAC_OSX_LEOPARD       -> "Leopard"
                SystemUtils.IS_OS_MAC_OSX_LION          -> "Lion"
                SystemUtils.IS_OS_MAC_OSX_MOJAVE        -> "Mojave"
                SystemUtils.IS_OS_MAC_OSX_MOUNTAIN_LION -> "Mountain Lion"
                SystemUtils.IS_OS_MAC_OSX_PANTHER       -> "Panther"
                SystemUtils.IS_OS_MAC_OSX_PUMA          -> "Puma"
                SystemUtils.IS_OS_MAC_OSX_SIERRA        -> "Sierra"
                SystemUtils.IS_OS_MAC_OSX_SNOW_LEOPARD  -> "Snow Leopard"
                SystemUtils.IS_OS_MAC_OSX_TIGER         -> "Tiger"
                SystemUtils.IS_OS_MAC_OSX_YOSEMITE      -> "Yosemite"
                else                                    -> ""
            })
            isRunningOSX = true
            os = "OS X"
        }
    }

    private fun handleTargetBrowser(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
        browserName = StringUtils.defaultIfBlank(config.remove(BROWSER), context.getStringData(KEY_BROWSER))
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
                BROWSER,
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

    private fun handleProjectMeta(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
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

    private fun handleOthers(capabilities: MutableCapabilities, config: MutableMap<String, String?>) {
        val debug = if (config.containsKey("debug"))
            BooleanUtils.toBoolean(config.remove("debug"))
        else
            context.getBooleanData(KEY_DEBUG, SystemVariables.getDefaultBool(KEY_DEBUG))

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

    companion object {
        fun newConfig(context: ExecutionContext, profile: String): BrowserStackConfig {
            requiresNotBlank(profile, "Invalid profile", profile)

            val key = PREFIX + profile
            val config = if (context.hasData(key)) context.getObjectData(key, BrowserStackConfig::class.java) else null
            if (config != null) return config

            val config1 = context.getDataByPrefix(StringUtils.appendIfMissing(profile, "."))
            if (config1.isEmpty()) throw IllegalArgumentException(RB.BrowserStack.text("missing.config", profile, "*"))

            if (!config1.containsKey("$CONF_SERVER.$CONF_SERVER_URL")) {
                val useSecure = context.getBooleanData(OPT_SECURE_BROWSERSTACK, getDefaultBool(OPT_SECURE_BROWSERSTACK))
                config1["$CONF_SERVER.$CONF_SERVER_URL"] = if (useSecure) HUB else HUB2
            }

            val newConfig = BrowserStackConfig(profile, config1)
            context.setData(key, newConfig)
            return newConfig
        }

        private fun newWebClient(config: BrowserStackConfig, contentType: String) =
            newWebClient(config).addHeader(WS_CONTENT_TYPE, contentType)

        private fun newWebClient(config: BrowserStackConfig) =
            WebServiceClient(null)
                .configureAsQuiet()
                .disableContextConfiguration()
                .withBasicAuth(config.user, config.automateKey)

        private fun newBrowserStackConfig(context: ExecutionContext): BrowserStackConfig {
            val useSecure = context.getBooleanData(OPT_SECURE_BROWSERSTACK, getDefaultBool(OPT_SECURE_BROWSERSTACK))
            return BrowserStackConfig("context", mapOf(
                USERNAME to context.getStringData(KEY_USERNAME),
                AUTOMATE_KEY to context.getStringData(AUTOMATEKEY),
                "browser" to context.getStringData(KEY_BROWSER),
                "$CONF_SERVER.$CONF_SERVER_URL" to if (useSecure) HUB else HUB2
            ))
        }

        private fun showHttpError(response: Response) =
            "${response.returnCode} ${response.statusText}${lineSeparator()}${response.body}"

        // https://www.browserstack.com/docs/automate/api-reference/selenium/session#set-test-status
        fun reportExecutionStatus(context: ExecutionContext, summary: ExecutionSummary): String {
            val sessionId = WebDriverUtils.getSessionId(context)
                            ?: throw IllegalArgumentException("No BrowserStack session found")
            return updateSessionStatus(newBrowserStackConfig(context),
                                       resolveSessionUrl(WEB_SESSION_URL, sessionId),
                                       if (summary.failCount > 0) "failed" else "passed",
                                       formatStatusDescription(summary))
        }

        internal fun updateSessionStatus(config: BrowserStackConfig, url: String, status: String, reason: String):
            String {
            ConsoleUtils.log("reporting execution status to BrowserStack...")
            val response = newWebClient(config, WS_JSON_CONTENT_TYPE)
                .put(url, """{"status":"$status", "reason":"$reason"}""")
            return if (response.returnCode != 200)
                throw RuntimeException(RB.BrowserStack.text("update.status.fail", url, showHttpError(response)))
            else
                response.body
        }

        private fun resolveSessionUrl(url: String, sessionId: String) = StringUtils.replace(url, "{session}", sessionId)

        fun enrichConfiguration(context: ExecutionContext, config: @NotNull MutableMap<String, String>) {
            // add default URLs if not specified
            val keyHubUrl = "$CONF_SERVER.$CONF_SERVER_URL"
            if (!config.containsKey(keyHubUrl)) config[keyHubUrl] = HUB

            val execDef = context.execDef
            val project = if (execDef != null && execDef.project != null) execDef.project else context.project
            if (!config.containsKey(PROJECT)) config[PROJECT] = project.name

            if (!config.containsKey(TEST_NAME)) config[TEST_NAME] =
                if (execDef != null && execDef.testScript != null)
                    File(execDef.testScript).name.substringBeforeLast(".")
                else
                    context.currentScenario

            if (!config.containsKey(BUILD)) config[BUILD] = context.getStringData(KEY_BUILD_NUM, context.runId)
        }

        fun addMobileCapabilities(profile: MobileProfile,
                                  caps: DesiredCapabilities,
                                  config: MutableMap<String, String>) {

            // appId is required for browserstack integration
            // accept either:
            //      bs://9fg0rt...
            //      MyCustomId
            if (config.containsKey(APP)) {
                caps.setCapability(APP, config.remove(APP)!!)
            } else if (config.containsKey(CONF_APP_ID)) {
                caps.setCapability(APP, config.remove(CONF_APP_ID)!!)
            } else {
                throw IllegalArgumentException(RB.BrowserStack.text("missing.appOrAppId", profile.profile))
            }

            caps.setCapability("browserstack.user", config.remove(BrowserStack.USERNAME))
            caps.setCapability("browserstack.key", config.remove(BrowserStack.AUTOMATE_KEY))

            caps.setCapability("project", config.remove(PROJECT))
            caps.setCapability("build", config.remove(BUILD))
            caps.setCapability("name", config.remove(TEST_NAME))

            val mobileType = profile.mobileType
            if (mobileType.isAndroid()) caps.setCapability("os", "android")
            if (mobileType.isIOS()) caps.setCapability("os", "ios")

            if (config.containsKey(DEVICE_NAME)) caps.setCapability("device", config.remove(DEVICE_NAME))
            if (config.containsKey(PLATFORM_VERSION)) caps.setCapability("os_version", config.remove(PLATFORM_VERSION))
            if (config.containsKey(CONF_ORIENTATION))
                caps.setCapability("deviceOrientation", config[CONF_ORIENTATION]!!.lowercase())

            if (profile.geoLocation != null) caps.setCapability("browserstack.gpsLocation", profile.geoLocation)

            caps.setCapability("browserstack.acceptInsecureCerts", true)
        }
    }
}