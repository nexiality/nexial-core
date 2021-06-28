package org.nexial.core.plugins.mobile

import io.appium.java_client.AppiumDriver
import io.appium.java_client.MobileElement
import io.appium.java_client.remote.MobileCapabilityType.*
import io.appium.java_client.service.local.AppiumDriverLocalService
import org.apache.commons.lang3.StringUtils
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.remote.DesiredCapabilities
import java.net.URL
import java.util.concurrent.TimeUnit.MILLISECONDS

class MobileService(val profile: MobileProfile, val remoteUrl: String?) {

    val driver: AppiumDriver<MobileElement>

    constructor(profile: MobileProfile) : this(profile, null)

    init {
        val appiumUrl = when {
            remoteUrl != null                   -> URL(remoteUrl)
            StringUtils.isNotBlank(profile.url) -> URL(profile.url)
            else                                -> {
                val appiumService = AppiumDriverLocalService.buildDefaultService()
                appiumService.start()
                val url = appiumService.url
                ConsoleUtils.log("appium server started on $url")
                url
            }
        }

        driver = AppiumDriver(appiumUrl, newCapabilities())
        driver.manage().timeouts().implicitlyWait(profile.implicitWaitMs, MILLISECONDS)
    }

    fun isAndroid() = profile.mobileType == ANDROID

    fun isIOS() = profile.mobileType == IOS

    private fun newCapabilities(): DesiredCapabilities {
        val caps = DesiredCapabilities()
        caps.setCapability(NEW_COMMAND_TIMEOUT, profile.sessionTimeoutMs)
        caps.setCapability(ACCEPT_INSECURE_CERTS, true)
        caps.setCapability(HAS_NATIVE_EVENTS, true)
        caps.setCapability(HAS_TOUCHSCREEN, true)
        caps.setCapability(PRINT_PAGE_SOURCE_ON_FIND_FAILURE, true)
        profile.config.map { caps.setCapability(it.key, it.value) }
        return caps
    }
}