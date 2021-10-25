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

package org.nexial.core.plugins.browserstack

import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Mobile.CONF_SERVER
import org.nexial.core.NexialConst.Mobile.CONF_SERVER_URL
import org.nexial.core.SystemVariables.registerSysVar
import org.nexial.core.plugins.browserstack.BrowserStack.Url.HUB

object BrowserStack : CloudWebTesting() {
    object Url {
        const val BASE_URL = "@hub.browserstack.com/wd/hub"
        const val HUB = "https://hub-cloud.browserstack.com/wd/hub"

        // browserstack specific APIs
        private const val APP_AUTOMATE_API_BASE = "https://api-cloud.browserstack.com/app-automate"
        const val UPLOAD = "$APP_AUTOMATE_API_BASE/upload"
        const val LIST_UPLOADED = "$APP_AUTOMATE_API_BASE/recent_apps"
        const val DELETE_UPLOADED = "$APP_AUTOMATE_API_BASE/app/delete/"
        const val LIST_DEVICES = "$APP_AUTOMATE_API_BASE/devices.json"
        const val MOBILE_SESSION_URL = "$APP_AUTOMATE_API_BASE/sessions/{session}.json"

        private const val AUTOMATE_API_BASE = "https://api.browserstack.com/automate"
        const val LIST_BROWSER = "$AUTOMATE_API_BASE/browsers.json"
        const val WEB_SESSION_URL = "$AUTOMATE_API_BASE/sessions/{session}.json"
    }

    // for mobile app
    const val APP_PREFIX = "bs://"

    // an app should be at least 5k in size
    const val MIN_APP_SIZE: Long = 5120

    private const val NS = "browserstack."
    const val USERNAME = "${NS}username"
    const val AUTOMATE_KEY = "${NS}automatekey"

    const val PROJECT = "${NS}project"
    const val BUILD = "${NS}build"
    const val TEST_NAME = "${NS}name"
    const val BROWSER = "browser"

    // WEB
    val KEY_USERNAME = registerSysVar(NAMESPACE + USERNAME)
    val AUTOMATEKEY = registerSysVar(NAMESPACE + AUTOMATE_KEY)

    val KEY_BROWSER = registerSysVar("$NAMESPACE$NS$BROWSER")
    val KEY_BROWSER_VER = registerSysVar("$NAMESPACE${NS}browser.version")
    val KEY_RESOLUTION = registerSysVar("$NAMESPACE${NS}resolution")
    val KEY_BUILD_NUM = registerSysVar("$NAMESPACE${NS}app.buildnumber")
    val KEY_OS = registerSysVar("$NAMESPACE${NS}os")
    val KEY_OS_VER = registerSysVar("$NAMESPACE${NS}os.version")
    val KEY_DEBUG = registerSysVar("$NAMESPACE${NS}debug", true)
    val KEY_ENABLE_LOCAL = registerSysVar("$NAMESPACE${NS}enablelocal", false)
    val KEY_TERMINATE_LOCAL = registerSysVar("$NAMESPACE${NS}terminatelocal", true)
    val KEY_CAPTURE_CRASH = registerSysVar("$NAMESPACE${NS}captureCrash")

    // status report
    val KEY_STATUS_SCOPE = registerSysVar(NAMESPACE + NS + "reportStatus")

    // context name prefix
    const val PREFIX = "$NAMESPACE$NS-profile."

    // special System variable for browserstack integration
    val ENABLE_BROWSERSTACK_INTEGRATION = registerSysVar("$NAMESPACE$NS.enableIntegration", false)
    val UPLOAD_ONLY_NEWER_APP = registerSysVar("$NAMESPACE$NS.uploadOnlyNewer", false)
}

class BrowserStackConfig(profile: String, config: Map<String, String>) {
    val user = config["user"] ?: throw IllegalArgumentException(RB.BrowserStack.text("missing.config", profile, "user"))
    val automateKey = config["key"]
                      ?: throw IllegalArgumentException(RB.BrowserStack.text("missing.config", profile, "key"))
    val hubUrl = config["$CONF_SERVER.$CONF_SERVER_URL"] ?: HUB
    val useBrowser = config["browser"] != null || config["browserName"] != null
    val useMobile = config["deviceName"] != null
}