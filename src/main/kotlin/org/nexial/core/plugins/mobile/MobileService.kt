package org.nexial.core.plugins.mobile

import io.appium.java_client.AppiumDriver
import io.appium.java_client.MobileElement
import io.appium.java_client.remote.MobileCapabilityType.*
import io.appium.java_client.service.local.AppiumDriverLocalService
import io.appium.java_client.service.local.AppiumServiceBuilder
import io.appium.java_client.service.local.flags.GeneralServerFlag.*
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_HOME
import org.apache.tika.utils.SystemUtils.IS_OS_WINDOWS
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.Mobile.FILE_CONSOLE_LOG_LEVEL
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.remote.DesiredCapabilities
import java.io.File
import java.io.File.separator
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeUnit.MILLISECONDS

class MobileService(val profile: MobileProfile, val remoteUrl: String?) {
    private val envAppiumBinaryPath = "APPIUM_BINARY_PATH"
    private val appiumBinaryRelPath = "appium${separator}build${separator}lib${separator}main.js"
    private val possibleAppiumBinaryPaths = if (IS_OS_WINDOWS) {
        listOf(
            Path.of(USER_HOME, "node_modules", appiumBinaryRelPath),
            Path.of(System.getenv("ProgramFiles"), "Appium", "node_modules", appiumBinaryRelPath),
            Path.of(System.getenv("ProgramFiles(x86)"), "Appium", "node_modules", appiumBinaryRelPath),
            Path.of(System.getenv("LOCALAPPDATA"), "Programs", "Appium", "resources", "app", "node_modules",
                    appiumBinaryRelPath),
            Path.of(System.getenv("LOCALAPPDATA"), "Programs", "appium-desktop", "resources", "app", "node_modules",
                    appiumBinaryRelPath),
            Path.of(System.getenv("HOMEDRIVE"), "tools", "appium-desktop", "resources", "app", "node_modules",
                    appiumBinaryRelPath)
        )
    } else {
        listOf(
            Path.of(USER_HOME, "node_modules", appiumBinaryRelPath),
            Path.of("usr", "local", "lib", "node_modules", appiumBinaryRelPath),
            Path.of("Applications", "Appium.app", "Contents", "Resources", "app", "node_modules", appiumBinaryRelPath),
            Path.of(USER_HOME, "Applications", "Appium.app", "Contents", "Resources", "app", "node_modules",
                    appiumBinaryRelPath),
            Path.of(USER_HOME, "tools", "Appium.app", "Contents", "Resources", "app", "node_modules",
                    appiumBinaryRelPath)
        )
    }

    private val envNodeBinaryPath = "NODE_BINARY_PATH"
    private val possibleNodeBinaryPaths = if (IS_OS_WINDOWS) {
        listOf(Path.of(System.getenv("ProgramFiles"), "nodejs", "node.exe"),
               Path.of(System.getenv("ProgramFiles(x86)"), "nodejs", "node.exe"),
               Path.of(System.getenv("HOMEDRIVE"), "tools", "nodejs", "node.exe")
        )
    } else {
        listOf(Path.of("usr", "local", "bin", "node"),
               Path.of(USER_HOME, "tools", "nodejs", "bin", "node")
        )
    }

    val driver: AppiumDriver<MobileElement>
    var appiumService: AppiumDriverLocalService? = null

    constructor(profile: MobileProfile) : this(profile, null)

    init {
        val appiumUrl = when {
            remoteUrl != null                         -> URL(remoteUrl)
            StringUtils.isNotBlank(profile.serverUrl) -> URL(profile.serverUrl)
            else                                      -> startAppiumLocalService()
        }

        driver = AppiumDriver(appiumUrl, newCapabilities())
        driver.manage().timeouts().implicitlyWait(profile.implicitWaitMs, MILLISECONDS)
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
        driver.closeApp()
        if (appiumService != null) appiumService!!.stop()
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
        caps.setCapability(NEW_COMMAND_TIMEOUT, profile.sessionTimeoutMs)
        caps.setCapability(AUTOMATION_NAME, profile.mobileType.automationName)
        caps.setCapability(PRINT_PAGE_SOURCE_ON_FIND_FAILURE, true)

        handleOptionalIntentArguments(caps)

        profile.config.map { caps.setCapability(it.key, it.value) }
        return caps
    }

    /** "optionalIntentArguments": Additional intent arguments that will be used to start activity */
    private fun handleOptionalIntentArguments(caps: DesiredCapabilities) {
        val buffer = StringBuilder()

        if (profile.geoLocation != null) buffer.append("-d geo:${profile.geoLocation} ")
        buffer.append(StringUtils.trim(profile.config.remove("optionalIntentArguments"))).append(" ")
        val optionalIntentArguments = buffer.toString()

        if (StringUtils.isNotBlank(optionalIntentArguments))
            caps.setCapability("optionalIntentArguments", StringUtils.trim(optionalIntentArguments))
    }
}