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

import io.appium.java_client.remote.MobileCapabilityType.*
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.plugins.browserstack.BrowserStack.AUTOMATE_KEY
import org.nexial.core.plugins.browserstack.BrowserStack.USERNAME

enum class MobileType(val platformName: String, val automationName: String, val requiredConfig: List<String>) {

    ANDROID(PLATFORM_ANDROID, DRIVER_UIAUTOMATOR2,
            listOf(DEVICE_NAME,
                   PLATFORM_VERSION,
                   UDID,
                   CONF_APP_PACKAGE,
                   CONF_APP_ACTIVITY)),
    IOS(PLATFORM_IOS, DRIVER_XCUITEST,
        listOf(DEVICE_NAME,
               PLATFORM_VERSION,
               UDID)),

    ANDROID_BROWSERSTACK(PLATFORM_ANDROID, DRIVER_UIAUTOMATOR2,
                         listOf(DEVICE_NAME,
                                PLATFORM_VERSION,
                                CONF_APP_PACKAGE,
                                CONF_APP_ACTIVITY,
                                USERNAME,
                                AUTOMATE_KEY)),
    IOS_BROWSERSTACK(PLATFORM_IOS,
                     DRIVER_XCUITEST,
                     listOf(DEVICE_NAME,
                            PLATFORM_VERSION,
                            USERNAME,
                            AUTOMATE_KEY)),
    ;

    fun isAndroid() = platformName == PLATFORM_ANDROID
    fun isIOS() = platformName == PLATFORM_IOS
    fun isBrowserStack() = this.name.contains(PLATFORM_BROWSERSTACK)
}