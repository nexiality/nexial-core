/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexial.core.plugins.web

import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Web.BROWSER_ACCEPT_INVALID_CERTS
import org.nexial.core.NexialConst.Web.OPT_ALERT_IGNORE_FLAG
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.ExecutionContext
import org.openqa.selenium.MutableCapabilities
import org.openqa.selenium.UnexpectedAlertBehaviour.ACCEPT
import org.openqa.selenium.UnexpectedAlertBehaviour.IGNORE
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.CapabilityType.*

internal object WebDriverCapabilityUtils {
    internal fun setCapability(capabilities: MutableCapabilities, key: String, config: String?) {
        if (StringUtils.isNotBlank(config)) capabilities.setCapability(key, config)
    }

    internal fun setCapability(capabilities: MutableCapabilities, key: String, config: Boolean) {
        capabilities.setCapability(key, config)
    }

    @JvmStatic
    fun initCapabilities(context: ExecutionContext?, capabilities: MutableCapabilities) {
        val ignoreAlertFlag = context?.getBooleanData(OPT_ALERT_IGNORE_FLAG) ?: false
        val acceptInvalidCert =
            context?.getBooleanData(BROWSER_ACCEPT_INVALID_CERTS, getDefaultBool(BROWSER_ACCEPT_INVALID_CERTS)) ?: false

        // if true then we tell browser not to auto-close js alert dialog
        capabilities.setCapability(UNEXPECTED_ALERT_BEHAVIOUR,
                                   if (BooleanUtils.toBoolean(ignoreAlertFlag)) IGNORE else ACCEPT)
        capabilities.setCapability(SUPPORTS_ALERTS, true)
        capabilities.setCapability(SUPPORTS_WEB_STORAGE, true)
        capabilities.setCapability(HAS_NATIVE_EVENTS, true)
        capabilities.setCapability(SUPPORTS_LOCATION_CONTEXT, false)
        capabilities.setCapability(ACCEPT_SSL_CERTS, true)
        if (acceptInvalidCert) capabilities.setCapability(ACCEPT_INSECURE_CERTS, true)

        // --------------------------------------------------------------------
        // Proxy
        // --------------------------------------------------------------------
        // When a proxy is specified using the proxy capability, this capability sets the proxy settings on
        // a per-process basis when set to true. The default is false, which means the proxy capability will
        // set the system proxy, which IE will use.
        //capabilities.setCapability("ie.setProxyByServer", true);
        capabilities.setCapability("honorSystemProxy", false)

        // todo: not ready for prime time
        // if (context.getBooleanData(OPT_PROXY_REQUIRED, false) && proxy == null) {
        //     ConsoleUtils.log("setting proxy server for webdriver");
        //     capabilities.setCapability(PROXY, WebProxy.getSeleniumProxy());
        // }

        // if (context.getBooleanData(OPT_PROXY_DIRECT, false)) {
        //     ConsoleUtils.log("setting direct connection for webdriver");
        //     capabilities.setCapability(PROXY, WebProxy.getDirect());
        // }
    }


    @JvmStatic
    fun configureForHeadless(options: ChromeOptions) {
        options.setHeadless(true)
        options.addArguments("--no-sandbox")
        options.addArguments("--disable-dev-shm-usage")
        options.addArguments("--use-fake-device-for-media-stream")
        options.addArguments("--autoplay-policy=user-gesture-required")
        options.addArguments("--disable-gpu") // applicable to Windows os and Linux
        options.addArguments("--disable-software-rasterizer")
    }

    @JvmStatic
    fun addChromeExperimentalOptions(
        options: ChromeOptions,
        withGeoLocation: Boolean,
        alwaysSavePdf: Boolean,
        downloadTo: String?,
        lang: String?
    ) {
        // time to experiment...
        val prefs: MutableMap<String, Any> = HashMap()

        // disable save password "bubble", in case we are running in non-incognito mode
        prefs["credentials_enable_service"] = false
        prefs["profile.password_manager_enabled"] = false
        if (downloadTo != null && StringUtils.isNotBlank(downloadTo)) {
            // allow PDF to be downloaded instead of displayed (pretty much useless)
            prefs["plugins.always_open_pdf_externally"] = alwaysSavePdf
            prefs["download.default_directory"] = downloadTo
            // prefs.put("download.extensions_to_open", "application/pdf");
        }

        // https://stackoverflow.com/questions/40244670/disable-geolocation-in-selenium-chromedriver-with-python
        prefs["geolocation"] = withGeoLocation
        prefs["profile.default_content_setting_values.geolocation"] = if (withGeoLocation) 1 else 2
        prefs["profile.managed_default_content_settings.geolocation"] = if (withGeoLocation) 1 else 2

        // To Turns off multiple download warning
        prefs["profile.default_content_settings.popups"] = 0
        prefs["profile.default_content_setting_values.automatic_downloads"] = 1
        // Turns off download prompt
        prefs["download.prompt_for_download"] = false

        if (StringUtils.isNotBlank(lang)) prefs["intl.accept_languages"] = lang!!

        options.setExperimentalOption("prefs", prefs)

        // starting from Chrome 80, "samesite" is enforced by default... we need to workaround it
        val experimentalFlags: MutableList<String> = ArrayList()
        experimentalFlags.add("same-site-by-default-cookies@1")
        experimentalFlags.add("enable-removing-all-third-party-cookies@2")
        experimentalFlags.add("cookies-without-same-site-must-be-secure@1")
        val localState: MutableMap<String, Any> = HashMap()
        localState["browser.enabled_labs_experiments"] = experimentalFlags
        options.setExperimentalOption("localState", localState)

        // get rid of the infobar on top of the browser window
        //  Disable a few things considered not appropriate for automation.
        //     - disables the password saving UI (which covers the usecase of the removed `disable-save-password-bubble` flag)
        //     - disables infobar animations
        //     - disables dev mode extension bubbles (?), and doesn't show some other info bars
        //     - disables auto-reloading on network errors (source)
        //     - means the default browser check prompt isn't shown
        //     - avoids showing these 3 infobars: ShowBadFlagsPrompt, GoogleApiKeysInfoBarDelegate, ObsoleteSystemInfoBarDelegate
        options.setExperimentalOption("excludeSwitches", arrayOf("enable-automation", "load-extension"))
    }
}