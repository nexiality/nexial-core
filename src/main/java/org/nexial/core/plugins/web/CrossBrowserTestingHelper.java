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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.proc.RuntimeUtils;
import org.nexial.commons.utils.web.URLEncodingUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.ws.WsCommand;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.nexial.core.NexialConst.BrowserType.crossbrowsertesting;
import static org.nexial.core.NexialConst.CrossBrowserTesting.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.WS_BASIC_PWD;
import static org.nexial.core.NexialConst.WS_BASIC_USER;
import static org.nexial.core.plugins.web.WebDriverCapabilityUtils.initCapabilities;
import static org.nexial.core.plugins.web.WebDriverCapabilityUtils.setCapability;

/**
 * extension to {@link Browser} in support of all things CrossBrowserTesting.
 */
public class CrossBrowserTestingHelper extends CloudWebTestingPlatform {
    private final String docUrl = "Check " + REFERENCE_URL + " for more details";

    protected CrossBrowserTestingHelper(ExecutionContext context) { super(context); }

    @Override
    @NotNull
    public WebDriver initWebDriver() {
        // support any existing or new cbt.* configs
        Map<String, String> config = context.getDataByPrefix(NS);

        String username = config.remove(KEY_USERNAME);
        String authKey = config.remove(KEY_AUTHKEY);
        if (StringUtils.isBlank(username) || StringUtils.isBlank(authKey)) {
            throw new RuntimeException("Both " + KEY_USERNAME + " and " + KEY_AUTHKEY +
                                       " are required to use CrossBrowserTesting");
        }

        MutableCapabilities capabilities = new MutableCapabilities();
        initCapabilities(context, capabilities);

        handleLocal(username, authKey, config);
        handlePlatform(capabilities, config);
        handleProjectMeta(capabilities, config);

        // remaining configs specific to cbt
        setCapability(capabilities, KEY_ENABLE_VIDEO, config.getOrDefault(KEY_ENABLE_VIDEO, DEF_ENABLE_VIDEO));
        setCapability(capabilities, KEY_RECORD_NETWORK, config.getOrDefault(KEY_RECORD_NETWORK, DEF_RECORD_NETWORK));
        config.forEach((key, value) -> setCapability(capabilities, key, value));

        pageSourceSupported = false;
        try {
            String url = BASE_PROTOCOL + URLEncodingUtils.encodeAuth(username) + ":" + authKey + BASE_URL;
            RemoteWebDriver driver = new RemoteWebDriver(new URL(url), capabilities);

            String sessionId = driver.getSessionId().toString();
            context.addScriptReferenceData(KEY_SESSION_ID, sessionId);

            return driver;
        } catch (MalformedURLException | WebDriverException e) {
            throw new RuntimeException("Unable to initialize CrossBrowserTesting session: " + e.getMessage(), e);
        }
    }

    public void reportExecutionStatus(ExecutionSummary summary) {
        if (context == null) { return; }

        NexialCommand wsCommand = context.findPlugin("ws");
        if (!(wsCommand instanceof WsCommand)) { return; }

        String sessionId = context.gatherScriptReferenceData().get(KEY_SESSION_ID);
        if (StringUtils.isBlank(sessionId)) {
            ConsoleUtils.error("Unable to report execution status since session id is blank or cannot be retrieved.");
            return;
        }

        String oldUsername = context.getStringData(WS_BASIC_USER);
        String oldPassword = context.getStringData(WS_BASIC_PWD);

        ConsoleUtils.log("reporting execution status to CrossBrowserTesting...");

        context.setData(WS_BASIC_USER, context.getStringData(NS + KEY_USERNAME));
        context.setData(WS_BASIC_PWD, context.getStringData(NS + KEY_AUTHKEY));

        try {
            WsCommand ws = ((WsCommand) wsCommand);
            ws.put(StringUtils.replace(SESSION_URL, "${seleniumTestId}", sessionId),
                   "{\"action\":\"set_score\", \"score\":\"" + (summary.getFailCount() > 0 ? "fail" : "pass") + "\"}",
                   RandomStringUtils.randomAlphabetic(5));
        } finally {
            // put BASIC AUTH back
            context.setData(WS_BASIC_USER, oldUsername);
            context.setData(WS_BASIC_PWD, oldPassword);
        }
    }

    protected void handleLocal(String username, String authkey, Map<String, String> config) {
        boolean enableLocal = config.containsKey(KEY_ENABLE_LOCAL) ?
                              BooleanUtils.toBoolean(config.remove(KEY_ENABLE_LOCAL)) : DEF_ENABLE_LOCAL;
        if (!enableLocal) { return; }

        try {
            WebDriverHelper helper = WebDriverHelper.Companion.newInstance(crossbrowsertesting, context);
            File driver = helper.resolveDriver();

            String cbtlocal = helper.config.getBaseName();

            RuntimeUtils.terminateInstance(cbtlocal);

            // start cbt local, but wait (3s) for it to start up completely.
            // Command line: 'cbt_tunnels --username USERNAME --authkey AUTHKEY'
            ConsoleUtils.log("starting new instance of " + cbtlocal + "...");
            RuntimeUtils.runAppNoWait(driver.getParent(),
                                      driver.getName(),
                                      Arrays.asList("--username", username, "--authkey", authkey));
            Thread.sleep(3000);
            isRunningLocal = true;
            localExeName = driver.getName();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("unable to start CrossBrowsingTesting Local: " + e.getMessage(), e);
        }
    }

    /**
     * os specific setting, including mobile devices
     */
    protected void handlePlatform(MutableCapabilities capabilities, Map<String, String> config) {
        String browserName = config.remove(KEY_BROWSER);
        if (StringUtils.isBlank(browserName)) {
            throw new RuntimeException("'" + NS + KEY_BROWSER + "' is required to use CrossBrowserTesting. " + docUrl);
        }

        setCapability(capabilities, KEY_BROWSER, browserName);

        String targetOS = config.remove(KEY_PLATFORM);
        if (StringUtils.isNotBlank(targetOS)) {
            // not mobile for sure
            setCapability(capabilities, KEY_PLATFORM, targetOS);
            setCapability(capabilities, KEY_RESOLUTION,
                          StringUtils.defaultIfBlank(config.remove(KEY_RESOLUTION),
                                                     context.getStringData(BROWSER_WINDOW_SIZE)));
            setCapability(capabilities, KEY_BROWSER_VER, config.remove(KEY_BROWSER_VER));
            isMobile = false;
            ConsoleUtils.log("[CBT] setting up " + browserName + " on " + targetOS);
            return;
        }

        String targetMobileOS = config.remove(KEY_MOBILE_PLATFORM);
        if (StringUtils.isBlank(targetMobileOS)) {
            // we gotta have either `KEY_PLATFORM` or `KEY_MOBILE_PLATFORM`
            throw new RuntimeException("Either '" + NS + KEY_PLATFORM + "' or '" + NS + KEY_MOBILE_PLATFORM + "' " +
                                       "is required. " + docUrl);
        }

        // mobile for sure, at this point
        String deviceName = config.remove(KEY_DEVICE);
        if (StringUtils.isBlank(deviceName)) {
            throw new RuntimeException("'" + NS + KEY_DEVICE + "' is required for mobile web testing. " + docUrl);
        }

        setCapability(capabilities, KEY_MOBILE_PLATFORM, targetMobileOS);
        setCapability(capabilities, KEY_MOBILE_PLATFORM_VER, config.remove(KEY_MOBILE_PLATFORM_VER));
        setCapability(capabilities, KEY_DEVICE, deviceName);
        setCapability(capabilities, KEY_DEVICE_ORIENTATION, config.remove(KEY_DEVICE_ORIENTATION));
        isMobile = true;

        ConsoleUtils.log("[CBT] setting up " + browserName + " on " + deviceName + "/" + targetMobileOS);
    }

    protected void handleProjectMeta(MutableCapabilities capabilities, Map<String, String> config) {
        String buildNum =
            StringUtils.defaultIfBlank(config.remove(KEY_BUILD), context.getStringData(SCRIPT_REF_PREFIX + BUILD_NO));
        setCapability(capabilities, KEY_BUILD, buildNum);

        ExecutionDefinition execDef = context.getExecDef();
        if (execDef == null) { return; }

        String testName = null;
        if (execDef.getProject() != null && StringUtils.isNotBlank(execDef.getProject().getName())) {
            testName = execDef.getProject().getName();
        }

        if (StringUtils.isNotBlank(execDef.getTestScript())) {
            String scriptName = StringUtils.substringAfterLast(
                StringUtils.replace(execDef.getTestScript(), "\\", "/"), "/");
            testName += (StringUtils.isNotBlank(testName) ? " / " : "") + scriptName;
        }

        setCapability(capabilities, KEY_NAME, testName);
    }

    @Override
    protected void terminateLocal() { if (isRunningLocal) { RuntimeUtils.terminateInstance(localExeName); } }
}
