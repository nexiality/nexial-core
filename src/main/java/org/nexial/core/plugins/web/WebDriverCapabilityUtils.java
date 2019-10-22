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

package org.nexial.core.plugins.web;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.WebProxy;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.MutableCapabilities;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Web.*;
import static org.nexial.core.NexialConst.Web.BROWSER_ACCEPT_INVALID_CERTS;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.openqa.selenium.UnexpectedAlertBehaviour.ACCEPT;
import static org.openqa.selenium.UnexpectedAlertBehaviour.IGNORE;
import static org.openqa.selenium.remote.CapabilityType.*;

class WebDriverCapabilityUtils {
    protected static void setCapability(MutableCapabilities capabilities, String key, String config) {
        if (StringUtils.isNotBlank(config)) { capabilities.setCapability(key, config); }
    }

    protected static void setCapability(MutableCapabilities capabilities, String key, boolean config) {
        capabilities.setCapability(key, config);
    }

    protected static void initCapabilities(ExecutionContext context, MutableCapabilities capabilities) {
        // if true then we tell firefox not to auto-close js alert dialog
        boolean ignoreAlert = BooleanUtils.toBoolean(context.getBooleanData(OPT_ALERT_IGNORE_FLAG));
        capabilities.setCapability(UNEXPECTED_ALERT_BEHAVIOUR, ignoreAlert ? IGNORE : ACCEPT);
        capabilities.setCapability(SUPPORTS_ALERTS, true);
        capabilities.setCapability(SUPPORTS_WEB_STORAGE, true);
        capabilities.setCapability(HAS_NATIVE_EVENTS, true);
        capabilities.setCapability(SUPPORTS_LOCATION_CONTEXT, false);
        capabilities.setCapability(ACCEPT_SSL_CERTS, true);
        if (context.getBooleanData(BROWSER_ACCEPT_INVALID_CERTS, getDefaultBool(BROWSER_ACCEPT_INVALID_CERTS))) {
            capabilities.setCapability(ACCEPT_INSECURE_CERTS, true);
        }

        // --------------------------------------------------------------------
        // Proxy
        // --------------------------------------------------------------------
        // When a proxy is specified using the proxy capability, this capability sets the proxy settings on
        // a per-process basis when set to true. The default is false, which means the proxy capability will
        // set the system proxy, which IE will use.
        //capabilities.setCapability("ie.setProxyByServer", true);
        capabilities.setCapability("honorSystemProxy", false);

        // todo: not ready for prime time
        // if (context.getBooleanData(OPT_PROXY_REQUIRED, false) && proxy == null) {
        //     ConsoleUtils.log("setting proxy server for webdriver");
        //     capabilities.setCapability(PROXY, WebProxy.getSeleniumProxy());
        // }

        if (context.getBooleanData(OPT_PROXY_DIRECT, false)) {
            ConsoleUtils.log("setting direct connection for webdriver");
            capabilities.setCapability(PROXY, WebProxy.getDirect());
        }
    }
}
