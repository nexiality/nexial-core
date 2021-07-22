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
import org.nexial.core.NexialConst.Mobile.FILE_CONSOLE_LOG_LEVEL
import org.nexial.core.NexialConst.Mobile.MIN_WAIT_MS
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.remote.DesiredCapabilities
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
        StringUtils.defaultIfEmpty(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT")),
        "platform-tools",
        if (IS_OS_WINDOWS) "adb.exe" else "adb"
    ).toFile().absolutePath

    private val numericCapNames = listOf("remoteAppsCacheLimit", "interKeyDelay", "webviewConnectRetries")
    private val booleanCapNames = listOf("ensureWebviewsHavePages", "allowTestPackages", "useKeystore",
                                         "enableWebviewDetailsCollection", "dontStopAppOnReset", "unicodeKeyboard",
                                         "resetKeyboard", "noSign", "ignoreUnimportantViews", "disableAndroidWatchers",
                                         "recreateChromeDriverSessions", "nativeWebScreenshot", "autoGrantPermissions",
                                         "gpsEnabled", "skipDeviceInitialization", "chromedriverDisableBuildCheck",
                                         "skipUnlock", "autoLaunch", "skipLogcatCapture", "disableWindowAnimation",
                                         "androidNaturalOrientation", "enforceAppInstall", "ignoreHiddenApiPolicyError",
                                         "allowDelayAdb", "locationServicesEnabled", "locationServicesAuthorized",
                                         "autoAcceptAlerts", "autoDismissAlerts", "nativeInstrumentsLib",
                                         "nativeWebTap", "safariAllowPopups", "safariIgnoreFraudWarning",
                                         "safariOpenLinksInBackground", "keepKeyChains", "showIOSLog",
                                         "enableAsyncExecuteFromHttps", "skipLogCapture", "skipLogCapture")

    val driver: AppiumDriver<MobileElement>
    var appiumService: AppiumDriverLocalService? = null

    constructor(profile: MobileProfile) : this(profile, null)

    init {
        val appiumUrl = when {
            remoteUrl != null                         -> URL(remoteUrl)
            StringUtils.isNotBlank(profile.serverUrl) -> URL(profile.serverUrl)
            else                                      -> startAppiumLocalService()
        }

        driver = when {
            profile.mobileType.isAndroid() -> AndroidDriver(appiumUrl, newCapabilities())
            profile.mobileType.isIOS()     -> IOSDriver(appiumUrl, newCapabilities())
            else                           -> AppiumDriver(appiumUrl, newCapabilities())
        }
        if (profile.implicitWaitMs > MIN_WAIT_MS) {
            driver.manage().timeouts().implicitlyWait(profile.implicitWaitMs, MILLISECONDS)
        }
    }

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
        ConsoleUtils.log("appium server started on $url")

        appiumService = service
        return url
    }

    fun isAndroid() = profile.mobileType == ANDROID

    fun isIOS() = profile.mobileType == IOS

    fun shutdown() {
        try {
            driver.closeApp()
        } catch (e: Exception) {
            ConsoleUtils.error("Unable to shutdown mobile driver: ${e.message}")
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

    private fun resolveEnv() {
        // check/resolve APPIUM_BINARY_PATH
        var appiumBinaryPath = StringUtils.defaultIfEmpty(System.getenv(envAppiumBinaryPath),
                                                          System.getProperty(envAppiumBinaryPath))
        if (StringUtils.isBlank(appiumBinaryPath)) {
            val binary = possibleAppiumBinaryPaths.firstOrNull { path -> FileUtil.isFileExecutable(path.toFile()) }
                         ?: throw RuntimeException("Environment variable $envAppiumBinaryPath is not defined; unable " +
                                                   "to resolve an appropriate path for Appium server (main.js")
            appiumBinaryPath = binary.toFile().absolutePath
        } else if (!FileUtil.isFileExecutable(appiumBinaryPath)) {
            throw RuntimeException("Environment variable $envAppiumBinaryPath does not resolve to a valid " +
                                   "executable for Appium server (main.js): $appiumBinaryPath")
        }

        System.setProperty(envAppiumBinaryPath, appiumBinaryPath)

        // check/resolve NODE_BINARY_PATH
        var nodeBinaryPath = StringUtils.defaultIfEmpty(System.getenv(envNodeBinaryPath),
                                                        System.getProperty(envNodeBinaryPath))
        if (StringUtils.isBlank(nodeBinaryPath)) {
            val binary = possibleNodeBinaryPaths.firstOrNull { path -> FileUtil.isFileExecutable(path.toFile()) }
                         ?: throw RuntimeException("Environment variable $envNodeBinaryPath is not defined; unable " +
                                                   "to resolve an appropriate path for NodeJS (to run Appium server)")
            nodeBinaryPath = binary.toFile().absolutePath
        } else if (!FileUtil.isFileExecutable(nodeBinaryPath)) {
            throw RuntimeException("Environment variable $envNodeBinaryPath does not resolve to a valid executable " +
                                   "NodeJS file: $nodeBinaryPath")
        }

        System.setProperty(envNodeBinaryPath, nodeBinaryPath)
    }

    private fun newCapabilities(): DesiredCapabilities {
        val caps = DesiredCapabilities()

        safeSetCap(caps, NEW_COMMAND_TIMEOUT,
                   if (profile.config.containsKey(NEW_COMMAND_TIMEOUT)) profile.config[NEW_COMMAND_TIMEOUT]
                   else profile.sessionTimeoutMs / 1000)

        safeSetCap(caps, AUTOMATION_NAME,
                   if (profile.config.containsKey(AUTOMATION_NAME)) profile.config[AUTOMATION_NAME]
                   else profile.mobileType.automationName)

        safeSetCap(caps, PRINT_PAGE_SOURCE_ON_FIND_FAILURE,
                   if (profile.config.containsKey(PRINT_PAGE_SOURCE_ON_FIND_FAILURE))
                       BooleanUtils.toBoolean(profile.config[PRINT_PAGE_SOURCE_ON_FIND_FAILURE])
                   else true)

        if (profile.hideKeyboard) {
            caps.setCapability(UNICODE_KEYBOARD, true)
            caps.setCapability(RESET_KEYBOARD, true)
        }

        handleOptionalIntentArguments(caps)

        profile.config.map { safeSetCap(caps, it.key, it.value) }
        return caps
    }

    /** "optionalIntentArguments": Additional intent arguments that will be used to start activity */
    private fun handleOptionalIntentArguments(caps: DesiredCapabilities) {
        val buffer = StringBuilder()

        if (profile.geoLocation != null) buffer.append("-d geo:${profile.geoLocation} ")
        buffer.append(
            StringUtils.trim(StringUtils.defaultIfBlank(profile.config.remove("optionalIntentArguments"), ""))
        ).append(" ")

        val optionalIntentArguments = StringUtils.trim(buffer.toString())
        if (StringUtils.isNotEmpty(optionalIntentArguments))
            caps.setCapability("optionalIntentArguments", optionalIntentArguments)
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