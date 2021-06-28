package org.nexial.core.plugins.mobile

import io.appium.java_client.remote.MobileCapabilityType.PLATFORM_NAME
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.model.ExecutionContext

class MobileProfile(context: ExecutionContext, val profile: String) {

    private val commandName = "mobile"

    internal val mobileType: MobileType
    internal val config = mutableMapOf<String, String>()
    internal val url: String?
    internal var implicitWaitMs: Long
    internal var explicitWaitMs: Long
    internal var sessionTimeoutMs: Long
    internal var postActionWaitMs: Long

    /*
    todo: investigate

        caps.setCapability("platformName", "Android");
        caps.setCapability("deviceName", "Android Emulator");
        caps.setCapability("automationName", "UiAutomator2");
        caps.setCapability("appPackage", "com.google.android.apps.maps");
        caps.setCapability("appActivity", "com.google.android.maps.MapsActivity");
        caps.setCapability("intentAction", "android.intent.action.VIEW");
        caps.setCapability("optionalIntentArguments", "-d geo:46.457398,-119.407305");
    */

    init {
        val config = context.getDataByPrefix(StringUtils.appendIfMissing(profile, "."))

        // check required config
        if (MapUtils.isEmpty(config)) throw IllegalArgumentException("No configuration found for profile $profile")
        if (!config.containsKey("type")) throw IllegalArgumentException("Missing configuration: ${profile}.type")

        context.updateProfileConfig(commandName, profile, config)

        mobileType = MobileType.valueOf(config.remove("type")!!.toUpperCase())
        for (key in mobileType.requiredConfig) {
            if (!config.containsKey(key)) throw IllegalArgumentException("Missing configuration: $profile.$key")
        }

        // in case user set profile-specific url for appium service
        url = config.remove("url")

        // find profile-specific Nexial behavior (such as wait time)
        implicitWaitMs = context.getIntConfig(commandName, profile, IMPLICIT_WAIT_MS).toLong()
        explicitWaitMs = context.getIntConfig(commandName, profile, EXPLICIT_WAIT_MS).toLong()
        sessionTimeoutMs = context.getIntConfig(commandName, profile, SESSION_TIMEOUT_MS).toLong()
        postActionWaitMs = context.getIntConfig(commandName, profile, POST_ACTION_WAIT_MS).toLong()

        this.config[PLATFORM_NAME] = mobileType.platformName
        // all config without `nexial.mobile.` will be used by appium's DesiredCapability
        this.config.putAll(config.filterKeys { !StringUtils.contains(it, NS_MOBILE) })
    }
}