package org.nexial.core.plugins.mobile

import io.appium.java_client.remote.MobileCapabilityType.*

enum class MobileType(val platformName:String, val requiredConfig: List<String>) {
    IOS("iOS", listOf(DEVICE_NAME, PLATFORM_VERSION, APP, AUTOMATION_NAME)),
    ANDROID("Android", listOf(DEVICE_NAME, PLATFORM_VERSION, UDID, "appPackage", "appActivity"))

}