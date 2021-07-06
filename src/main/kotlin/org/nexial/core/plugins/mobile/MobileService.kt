package org.nexial.core.plugins.mobile

import io.appium.java_client.AppiumDriver
import io.appium.java_client.MobileElement
import io.appium.java_client.remote.MobileCapabilityType.*
import io.appium.java_client.service.local.AppiumDriverLocalService
import io.appium.java_client.service.local.AppiumServiceBuilder
import io.appium.java_client.service.local.flags.GeneralServerFlag.*
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Mobile.FILE_CONSOLE_LOG_LEVEL
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.remote.DesiredCapabilities
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit.MILLISECONDS

class MobileService(val profile: MobileProfile, val remoteUrl: String?) {

    val driver: AppiumDriver<MobileElement>
    var appiumService: AppiumDriverLocalService? = null

    constructor(profile: MobileProfile) : this(profile, null)

    init {
        val appiumUrl = when {
            remoteUrl != null                         -> URL(remoteUrl)
            StringUtils.isNotBlank(profile.serverUrl) -> URL(profile.serverUrl)
            else                                      -> {
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
                url
            }
        }

        driver = AppiumDriver(appiumUrl, newCapabilities())
        driver.manage().timeouts().implicitlyWait(profile.implicitWaitMs, MILLISECONDS)
    }

    fun isAndroid() = profile.mobileType == ANDROID

    fun isIOS() = profile.mobileType == IOS

    fun shutdown() {
        driver.closeApp()
        if (appiumService != null) appiumService!!.stop()
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