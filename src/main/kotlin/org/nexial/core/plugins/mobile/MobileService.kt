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

package org.nexial.core.plugins.mobile

import io.appium.java_client.AppiumDriver
import io.appium.java_client.MobileElement
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.ios.IOSDriver
import io.appium.java_client.remote.AndroidMobileCapabilityType.RESET_KEYBOARD
import io.appium.java_client.remote.AndroidMobileCapabilityType.UNICODE_KEYBOARD
import io.appium.java_client.remote.MobileCapabilityType.*
import io.appium.java_client.service.local.AppiumDriverLocalService
import io.appium.java_client.service.local.AppiumServiceBuilder
import io.appium.java_client.service.local.flags.GeneralServerFlag.*
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_HOME
import org.apache.commons.lang3.math.NumberUtils
import org.apache.tika.utils.SystemUtils.IS_OS_WINDOWS
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.Data.WIN32_CMD
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.NexialConst.RB
import org.nexial.core.plugins.browserstack.BrowserStackHelper
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS
import org.nexial.core.plugins.web.WebDriverExceptionHelper
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.remote.SessionId
import java.io.File
import java.io.File.separator
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit.MILLISECONDS

class MobileService(val profile: MobileProfile, val remoteUrl: String?) {
    private val envAppiumBinaryPath = "APPIUM_BINARY_PATH"
    private val appiumBinaryRelPath = "appium${separator}build${separator}lib${separator}main.js"
    private val possibleAppiumBinaryPaths = if (IS_OS_WINDOWS) {
        listOf(
            Paths.get(USER_HOME, "node_modules", appiumBinaryRelPath),
            Paths.get(System.getenv("ProgramFiles"), "Appium", "node_modules", appiumBinaryRelPath),
            Paths.get(System.getenv("ProgramFiles(x86)"), "Appium", "node_modules", appiumBinaryRelPath),
            Paths.get(System.getenv("LOCALAPPDATA"), "Programs", "Appium", "resources", "app", "node_modules",
                      appiumBinaryRelPath),
            Paths.get(System.getenv("LOCALAPPDATA"), "Programs", "appium-desktop", "resources", "app", "node_modules",
                      appiumBinaryRelPath),
            Paths.get(System.getenv("HOMEDRIVE"), "tools", "appium-desktop", "resources", "app", "node_modules",
                      appiumBinaryRelPath)
        )
    } else {
        listOf(
            Paths.get(USER_HOME, "node_modules", appiumBinaryRelPath),
            Paths.get("usr", "local", "lib", "node_modules", appiumBinaryRelPath),
            Paths.get("Applications", "Appium.app", "Contents", "Resources", "app", "node_modules",
                      appiumBinaryRelPath),
            Paths.get(USER_HOME, "Applications", "Appium.app", "Contents", "Resources", "app", "node_modules",
                      appiumBinaryRelPath),
            Paths.get(USER_HOME, "tools", "Appium.app", "Contents", "Resources", "app", "node_modules",
                      appiumBinaryRelPath)
        )
    }

    private val envNodeBinaryPath = "NODE_BINARY_PATH"
    private val possibleNodeBinaryPaths = if (IS_OS_WINDOWS) {
        listOf(Paths.get(System.getenv("ProgramFiles"), "nodejs", "node.exe"),
               Paths.get(System.getenv("ProgramFiles(x86)"), "nodejs", "node.exe"),
               Paths.get(System.getenv("HOMEDRIVE"), "tools", "nodejs", "node.exe")
        )
    } else {
        listOf(Paths.get("usr", "local", "bin", "node"),
               Paths.get(USER_HOME, "tools", "nodejs", "bin", "node")
        )
    }
    private val adbLocation = Paths.get(
        StringUtils.trim(StringUtils.defaultIfEmpty(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))),
        "platform-tools",
        if (IS_OS_WINDOWS) "adb.exe" else "adb"
    ).toFile().absolutePath

    private val numericCapNames = listOf("remoteAppsCacheLimit", "interKeyDelay", "webviewConnectRetries",
                                         "appium:maxInstances")
    private val booleanCapNames = listOf("ensureWebviewsHavePages", "allowTestPackages", "useKeystore",
                                         "enableWebviewDetailsCollection", "appium:enableWebviewDetailsCollection",
                                         "dontStopAppOnReset", "unicodeKeyboard", "resetKeyboard", "noSign",
                                         "ignoreUnimportantViews", "disableAndroidWatchers",
                                         "recreateChromeDriverSessions", "nativeWebScreenshot", "autoGrantPermissions",
                                         "gpsEnabled", "skipDeviceInitialization", "chromedriverDisableBuildCheck",
                                         "skipUnlock", "autoLaunch", "skipLogcatCapture", "disableWindowAnimation",
                                         "androidNaturalOrientation", "enforceAppInstall", "ignoreHiddenApiPolicyError",
                                         "allowDelayAdb", "locationServicesEnabled", "locationServicesAuthorized",
                                         "autoAcceptAlerts", "autoDismissAlerts", "nativeInstrumentsLib",
                                         "nativeWebTap", "safariAllowPopups", "safariIgnoreFraudWarning",
                                         "safariOpenLinksInBackground", "keepKeyChains", "showIOSLog",
                                         "enableAsyncExecuteFromHttps", "skipLogCapture", "skipLogCapture",
                                         "noReset", "restart", "useNewWDA")

    private var appiumUrl: URL
    internal val driver: AppiumDriver<MobileElement>
    internal var appiumService: AppiumDriverLocalService? = null
    internal val locatorHelper: MobileLocatorHelper
    internal var sessionId: SessionId?

    constructor(profile: MobileProfile) : this(profile, null)

    init {
        appiumUrl = when {
            remoteUrl != null                         -> URL(remoteUrl)
            StringUtils.isNotBlank(profile.serverUrl) -> URL(profile.serverUrl)
            else                                      -> startAppiumLocalService()
        }

        driver = when {
            profile.mobileType.isAndroid() -> AndroidDriver(appiumUrl, newCapabilities())
            profile.mobileType.isIOS()     -> IOSDriver(appiumUrl, newCapabilities())
            else                           -> AppiumDriver(appiumUrl, newCapabilities())
        }
        if (profile.implicitWaitMs > MIN_WAIT_MS)
            driver.manage().timeouts().implicitlyWait(profile.implicitWaitMs, MILLISECONDS)
        sessionId = driver.sessionId

        locatorHelper = MobileLocatorHelper(this)
    }

    fun isAndroid() = profile.mobileType == ANDROID

    fun isIOS() = profile.mobileType == IOS

    fun shutdown() {
        if (driver.sessionId != null) {
            try {
                driver.closeApp()
            } catch (e: WebDriverException) {
                ConsoleUtils.error(RB.Mobile.text("error.closeApp", WebDriverExceptionHelper.resolveErrorMessage(e)))
            } catch (e: Exception) {
                ConsoleUtils.error(RB.Mobile.text("error.closeApp", e.message))
            } finally {
                sessionId = null
            }
        }

        try {
            driver.quit()
        } catch (e: WebDriverException) {
            ConsoleUtils.error(RB.Mobile.text("error.shutdown", WebDriverExceptionHelper.resolveErrorMessage(e)))
        } catch (e: Exception) {
            ConsoleUtils.error(RB.Mobile.text("error.shutdown", e.message))
        }

        if (appiumService != null) appiumService!!.stop()

        if (profile.config.containsKey("avd") && profile.config.containsKey("udid")) {
            // we've got a profile that targets emulator with auto start
            // let's kill the emu process now since we are shutting down appium
            val cmdParams = listOf("-s", profile.config["udid"], "emu", "kill")
            val outcome = if (IS_OS_WINDOWS)
                ProcessInvoker.invoke(WIN32_CMD, listOf("/C", "start", adbLocation).plus(cmdParams), null)
            else
                ProcessInvoker.invoke(adbLocation, cmdParams, null)

            val msgPrefix = "Emulator (${profile.config["deviceName"]}, ${profile.config["avd"]})"
            var output = StringUtils.trim(StringUtils.trim(outcome.stdout) + "\n" + StringUtils.trim(outcome.stderr))
            if (StringUtils.isNotBlank(output)) output = ": $output"

            ConsoleUtils.log(msgPrefix + (
                if (outcome.exitStatus == 0) " successfully terminated"
                else "did not terminate successfully") + output)
        }
    }

    fun getAppiumUrl() = appiumUrl

    fun manifest() =
        mapOf("type" to profile.mobileType.platformName,
              "device name" to profile.config["deviceName"],
              "avd" to profile.config["avd"],
              "platform version" to profile.config["platformVersion"],
              "appium url" to appiumUrl.toString(),
              "implicit wait" to "${profile.implicitWaitMs}ms",
              "explicit wait" to "${profile.explicitWaitMs}ms",
              "session timeout" to "${profile.sessionTimeoutMs}ms",
              "post action" to "${profile.postActionWaitMs}ms")
            .filterValues { StringUtils.isNotBlank(it) && it != "0ms" }
            .mapKeys { "mobile:${it.key}" }

    private fun startAppiumLocalService(): URL {
        resolveEnv()

        val builder = AppiumServiceBuilder()
        builder.withArgument(SESSION_OVERRIDE)
        builder.withArgument(RELAXED_SECURITY)
        // builder.withArgument { "autoGrantPermissions" }

        if (profile.logFile != null) {
            builder.withLogFile(File(profile.logFile))
            builder.withArgument(LOG_LEVEL, FILE_CONSOLE_LOG_LEVEL)
            builder.withArgument(DEBUG_LOG_SPACING)
        }

        val service = AppiumDriverLocalService.buildService(builder)
        service.start()
        val url = service.url
        ConsoleUtils.log(RB.Mobile.text("appium.started", url))

        appiumService = service
        return url
    }

    private fun resolveEnv() {
        // check/resolve APPIUM_BINARY_PATH
        var appiumBinaryPath = StringUtils.defaultIfEmpty(System.getenv(envAppiumBinaryPath),
                                                          System.getProperty(envAppiumBinaryPath))
        if (StringUtils.isBlank(appiumBinaryPath)) {
            val binary = possibleAppiumBinaryPaths.firstOrNull { path -> FileUtil.isFileExecutable(path.toFile()) }
                         ?: throw RuntimeException(RB.Mobile.text("missing.appium.path", envAppiumBinaryPath))
            appiumBinaryPath = binary.toFile().absolutePath
        } else if (!FileUtil.isFileExecutable(appiumBinaryPath))
            throw RuntimeException(RB.Mobile.text("invalid.appium.path", appiumBinaryPath))

        System.setProperty(envAppiumBinaryPath, appiumBinaryPath)

        // check/resolve NODE_BINARY_PATH
        var nodeBinaryPath = StringUtils.defaultIfBlank(System.getenv(envNodeBinaryPath),
                                                        System.getProperty(envNodeBinaryPath))
        if (StringUtils.isBlank(nodeBinaryPath)) {
            val binary = possibleNodeBinaryPaths.firstOrNull { path -> FileUtil.isFileExecutable(path.toFile()) }
                         ?: throw RuntimeException(RB.Mobile.text("missing.node.path", envNodeBinaryPath))
            nodeBinaryPath = binary.toFile().absolutePath
        } else if (!FileUtil.isFileExecutable(nodeBinaryPath))
            throw RuntimeException(RB.Mobile.text("invalid.node.path", nodeBinaryPath))

        System.setProperty(envNodeBinaryPath, nodeBinaryPath)
    }

    private fun newCapabilities(): DesiredCapabilities {
        val config = profile.config
        val caps = DesiredCapabilities()
        val mobileType = profile.mobileType

        safeSetCap(caps,
                   NEW_COMMAND_TIMEOUT,
                   if (config.containsKey(NEW_COMMAND_TIMEOUT)) config[NEW_COMMAND_TIMEOUT]
                   else profile.sessionTimeoutMs / 1000)

        safeSetCap(caps,
                   AUTOMATION_NAME,
                   if (config.containsKey(AUTOMATION_NAME)) config[AUTOMATION_NAME]
                   else mobileType.automationName)

        safeSetCap(caps,
                   PRINT_PAGE_SOURCE_ON_FIND_FAILURE,
                   if (config.containsKey(PRINT_PAGE_SOURCE_ON_FIND_FAILURE))
                       BooleanUtils.toBoolean(config[PRINT_PAGE_SOURCE_ON_FIND_FAILURE])
                   else false)
        caps.setCapability("realMobile", true)

        if (profile.hideKeyboard) {
            caps.setCapability(UNICODE_KEYBOARD, true)
            caps.setCapability(RESET_KEYBOARD, true)
        }

        if (mobileType.isAndroid()) handleOptionalIntentArguments(caps)
        if (mobileType.isIOS()) caps.setCapability("nativeWebTap", true)
        if (mobileType.isBrowserStack()) BrowserStackHelper.addMobileCapabilities(profile, caps, config)

        config.map { safeSetCap(caps, it.key, it.value) }

        return caps
    }

    /** "optionalIntentArguments": Additional intent arguments that will be used to start activity */
    private fun handleOptionalIntentArguments(caps: DesiredCapabilities) {
        val buffer = StringBuilder()
        if (profile.geoLocation != null) buffer.append("-d geo:${profile.geoLocation} ")
        buffer.append(StringUtils.trim(StringUtils.defaultIfBlank(profile.config.remove(CONF_INTENT_ARGS), "")))
            .append(" ")

        val optIntentArgs = StringUtils.trim(buffer.toString())
        if (StringUtils.isNotEmpty(optIntentArgs)) caps.setCapability(CONF_INTENT_ARGS, optIntentArgs)
    }

    private fun safeSetCap(caps: DesiredCapabilities, cap: String, value: Any?): DesiredCapabilities {
        if (value == null) return caps

        // number
        if (value is Number ||
            StringUtils.endsWithIgnoreCase(cap, "timeout") ||
            StringUtils.endsWithIgnoreCase(cap, "duration") ||
            StringUtils.endsWithIgnoreCase(cap, "port") ||
            numericCapNames.contains(cap)) {
            caps.setCapability(cap, NumberUtils.toInt(Objects.toString(value)))
            return caps
        }

        // boolean
        if (value is Boolean || booleanCapNames.contains(cap)) {
            caps.setCapability(cap, BooleanUtils.toBoolean(Objects.toString(value)))
            return caps
        }

        // all else as string
        caps.setCapability(cap, value)
        return caps
    }
}