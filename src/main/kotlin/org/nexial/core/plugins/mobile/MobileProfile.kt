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

import io.appium.java_client.remote.MobileCapabilityType.PLATFORM_NAME
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.NexialConst.Mobile.Message.*
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS
import java.io.File.separator

class MobileProfile(context: ExecutionContext, val profile: String) {
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
    internal val hideKeyboard: Boolean
    internal val explicitWaitEnabled: Boolean

    internal lateinit var appId: String

    /*
    todo: investigate
        caps.setCapability("intentAction", "android.intent.action.VIEW");
        ~~caps.setCapability("optionalIntentArguments", "-d geo:46.457398,-119.407305");
        https://appium.io/docs/en/writing-running-appium/caps/
    */

    init {
        val config = context.getDataByPrefix(StringUtils.appendIfMissing(profile, "."))

        // check required config
        if (MapUtils.isEmpty(config)) throw IllegalArgumentException("$MISSING_PROFILE $profile")
        if (!config.containsKey(CONF_TYPE)) throw IllegalArgumentException("$MISSING_TYPE_CONFIG $profile")

        context.updateProfileConfig(COMMAND, profile, config)

        mobileType = MobileType.valueOf(config.remove(CONF_TYPE)!!.toUpperCase())
        for (key in mobileType.requiredConfig) {
            if (!config.containsKey(key)) throw IllegalArgumentException("$MISSING_CONFIG $profile.$key")
        }

        appId = when (mobileType) {
            ANDROID -> config[CONF_APP_ID] ?: config[CONF_APP_PACKAGE] ?: ""
            IOS -> config[CONF_BUNDLE_ID] ?: ""
        }

        // appium driver only accepts orientation as UPPERCASE
        if (config.containsKey(CONF_ORIENTATION)) {
            config[CONF_ORIENTATION] = StringUtils.upperCase(config[CONF_ORIENTATION])
            // if (mobileType.isAndroid()) config["androidNaturalOrientation"] = "true"
        }

        val serverConfig = context.getDataByPrefix(StringUtils.appendIfMissing(profile, ".") + "${CONF_SERVER}.")
        serverConfig.forEach {
            config.remove("${CONF_SERVER}.${it.key}")
            this.serverConfig[it.key] = it.value
        }

        logFile = if (BooleanUtils.toBoolean(this.serverConfig.remove(CONF_SERVER_LOGGING)))
            StringUtils.appendIfMissing(context.project.logsDir, separator) + APPIUM_LOG
        else null

        // in case user set profile-specific url for appium service
        serverUrl = this.serverConfig.remove(CONF_SERVER_URL)

        // find profile-specific Nexial behavior (such as wait time)
        implicitWaitMs = context.getIntConfig(COMMAND, profile, IMPLICIT_WAIT_MS).toLong()
        explicitWaitMs = context.getIntConfig(COMMAND, profile, EXPLICIT_WAIT_MS).toLong()
        explicitWaitEnabled = explicitWaitMs > MIN_WAIT_MS
        sessionTimeoutMs = context.getIntConfig(COMMAND, profile, SESSION_TIMEOUT_MS).toLong()
        postActionWaitMs = context.getIntConfig(COMMAND, profile, POST_ACTION_WAIT_MS).toLong()
        hideKeyboard = context.getBooleanConfig(COMMAND, profile, HIDE_KEYBOARD)

        // TODO: auto-detect deviceName, appVersion
        /*
$ adb devices -l
List of devices attached
emulator-5554          device product:sdk_gphone_x86_64_arm64 model:sdk_gphone_x86_64_arm64 device:generic_x86_64_arm64 transport_id:2

$ adb shell getprop ro.build.version.release
11

        static String getDeviceName() {
    String deviceName = "";
    String shellCommand = "adb get-serialno"; // For platform use : adb shell getprop ro.build.version.release

    try {
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", shellCommand);
        builder.redirectErrorStream(true);
        Process prc = builder.start();
        BufferedReader output = new BufferedReader(new InputStreamReader(prc.getInputStream()));
        deviceName = output.readLine();
        prc.waitFor();
    } catch (Exception e) {
        e.printStackTrace();
    }

    return deviceName;
}
        */
        this.geoLocation = config.remove(CONF_GEO_LOC)

        this.config[PLATFORM_NAME] = mobileType.platformName
        this.config[CONF_AUTO_GRANT_PERM] = "true"
        // all config without `nexial.mobile.` will be used by appium's DesiredCapability
        this.config.putAll(config.filterKeys { !StringUtils.contains(it, NS_MOBILE) })
    }
}