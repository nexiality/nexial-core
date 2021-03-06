package org.nexial.core.plugins.mobile

import io.appium.java_client.remote.MobileCapabilityType.PLATFORM_NAME
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.model.ExecutionContext
import java.io.File.separator

class MobileProfile(context: ExecutionContext, val profile: String) {

    private val commandName = "mobile"

    internal val mobileType: MobileType
    internal val config = mutableMapOf<String, String>()
    internal val serverConfig = mutableMapOf<String, String>()
    internal val serverUrl: String?
    internal var implicitWaitMs: Long
    internal var explicitWaitMs: Long
    internal var sessionTimeoutMs: Long
    internal var postActionWaitMs: Long
    internal val geoLocation: String?
    internal val logFile: String?

    /*
    todo: investigate
        ~~caps.setCapability("platformName", "Android");
        ~~caps.setCapability("deviceName", "Android Emulator");
        ~~caps.setCapability("automationName", "UiAutomator2");
        ~~caps.setCapability("appPackage", "com.google.android.apps.maps");
        ~~caps.setCapability("appActivity", "com.google.android.maps.MapsActivity");
        caps.setCapability("intentAction", "android.intent.action.VIEW");
        ~~caps.setCapability("optionalIntentArguments", "-d geo:46.457398,-119.407305");

        https://appium.io/docs/en/writing-running-appium/caps/
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

        // appium driver only accepts orientation as UPPERCASE
        if (config.containsKey("orientation")) {
            config["orientation"] = StringUtils.upperCase(config["orientation"])
            // if (mobileType.isAndroid()) config["androidNaturalOrientation"] = "true"
        }

        val serverConfig = context.getDataByPrefix(StringUtils.appendIfMissing(profile, ".") + "server.")
        serverConfig.forEach {
            config.remove("server." + it.key)
            this.serverConfig[it.key] = it.value
        }

        logFile = if (BooleanUtils.toBoolean(this.serverConfig.remove("logging")))
            StringUtils.appendIfMissing(context.project.logsDir, separator) + "appium.log"
        else null

        // in case user set profile-specific url for appium service
        serverUrl = serverConfig.remove("url")

        // find profile-specific Nexial behavior (such as wait time)
        implicitWaitMs = context.getIntConfig(commandName, profile, IMPLICIT_WAIT_MS).toLong()
        explicitWaitMs = context.getIntConfig(commandName, profile, EXPLICIT_WAIT_MS).toLong()
        sessionTimeoutMs = context.getIntConfig(commandName, profile, SESSION_TIMEOUT_MS).toLong()
        postActionWaitMs = context.getIntConfig(commandName, profile, POST_ACTION_WAIT_MS).toLong()

        this.geoLocation = config.remove("geoLocation")

        this.config[PLATFORM_NAME] = mobileType.platformName
        this.config["autoGrantPermissions"] = "true"
        // all config without `nexial.mobile.` will be used by appium's DesiredCapability
        this.config.putAll(config.filterKeys { !StringUtils.contains(it, NS_MOBILE) })
    }
}