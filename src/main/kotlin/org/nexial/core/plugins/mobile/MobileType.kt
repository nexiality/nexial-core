package org.nexial.core.plugins.mobile

import io.appium.java_client.remote.MobileCapabilityType.*

enum class MobileType(val platformName: String, val automationName: String, val requiredConfig: List<String>) {
    IOS("iOS", "XCUITest", listOf(DEVICE_NAME, PLATFORM_VERSION, UDID)),
    ANDROID("Android", "UiAutomator2", listOf(DEVICE_NAME, PLATFORM_VERSION, UDID, "appPackage", "appActivity"));

    fun isAndroid() = platformName == "Android"
    fun isIOS() = platformName == "iOS"
}