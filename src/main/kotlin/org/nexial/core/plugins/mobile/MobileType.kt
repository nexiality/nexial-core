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
import org.nexial.core.NexialConst.Mobile.CONF_APP_ACTIVITY
import org.nexial.core.NexialConst.Mobile.CONF_APP_PACKAGE

enum class MobileType(val platformName: String, val automationName: String, val requiredConfig: List<String>) {
    IOS("iOS", "XCUITest", listOf(DEVICE_NAME, PLATFORM_VERSION, UDID)),
    ANDROID("Android", "UiAutomator2", listOf(DEVICE_NAME, PLATFORM_VERSION, UDID, CONF_APP_PACKAGE, CONF_APP_ACTIVITY));

    fun isAndroid() = platformName == "Android"
    fun isIOS() = platformName == "iOS"
}