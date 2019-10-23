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
import org.apache.commons.text.WordUtils;
import org.nexial.commons.proc.RuntimeUtils;
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

import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.core.NexialConst.BrowserStack.*;
import static org.nexial.core.NexialConst.BrowserType.*;
import static org.nexial.core.NexialConst.Web.BROWSER_WINDOW_SIZE;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.web.WebDriverCapabilityUtils.setCapability;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

/**
 * extension to {@link Browser} in support of all things BrowserStack.
 */
public class BrowserStackHelper extends CloudWebTestingPlatform {

    public BrowserStackHelper(ExecutionContext context) { super(context); }

    @Override
    @NotNull
    public WebDriver initWebDriver() {
        String username = context.getStringData(KEY_USERNAME);
        String automateKey = context.getStringData(AUTOMATEKEY);
        if (StringUtils.isBlank(username) || StringUtils.isBlank(automateKey)) {
            throw new RuntimeException("Both " + KEY_USERNAME + " and " + AUTOMATEKEY +
                                       " are required to use BrowserStack");
        }

        MutableCapabilities capabilities = new MutableCapabilities();
        WebDriverCapabilityUtils.initCapabilities(context, capabilities);

        // support any existing or new browserstack.* configs
        String prefix = "browserstack.";
        Map<String, String> config = context.getDataByPrefix(prefix);

        handleLocal(capabilities, config);
        handleOS(capabilities, config);
        handleTargetBrowser(capabilities, config);
        handleProjectMeta(capabilities, config);
        handleOthers(capabilities, config);

        // remaining configs specific to browserstack
        config.forEach((key, value) -> {
            // might be just `key`, might be browserstack.`key`... can't known for sure so we do both
            setCapability(capabilities, key, value);
            setCapability(capabilities, prefix + key, value);
        });

        pageSourceSupported = false;

        try {
            String url = BASE_PROTOCOL + username + ":" + automateKey + BASE_URL;
            RemoteWebDriver driver = new RemoteWebDriver(new URL(url), capabilities);

            saveSessionId(driver);

            // safari's usually a bit slower to come up...
            if (browser == safari) {
                ConsoleUtils.log("5 second grace period for starting up Safari on BrowserStack...");
                try { Thread.sleep(5000);} catch (InterruptedException e) {}
            }

            return driver;
        } catch (MalformedURLException | WebDriverException e) {
            throw new RuntimeException("Unable to initialize BrowserStack session: " + e.getMessage(), e);
        }
    }

    public void reportExecutionStatus(ExecutionSummary summary) { reportExecutionStatus(context, summary); }

    public static void reportExecutionStatus(ExecutionContext context, ExecutionSummary summary) {
        if (context == null) { return; }

        NexialCommand wsCommand = context.findPlugin("ws");
        if (!(wsCommand instanceof WsCommand)) { return; }

        WsCommand ws = ((WsCommand) wsCommand);

        String sessionId = CloudWebTestingPlatform.getSessionId(context);
        if (sessionId == null) { return; }

        String url = StringUtils.replace(SESSION_URL, "${username}", context.getStringData(KEY_USERNAME));
        url = StringUtils.replace(url, "${automatekey}", context.getStringData(AUTOMATEKEY));
        url = StringUtils.replace(url, "${sessionId}", sessionId);

        String payload = "{\"status\":\"" + (summary.getFailCount() > 0 ? "failed" : "passed") +
                         "\", \"reason\":\"" + formatStatusDescription(summary) + "\"}";

        ConsoleUtils.log("reporting execution status to BrowserStack...");
        ws.put(url, payload, RandomStringUtils.randomAlphabetic(5));
    }

    protected void handleLocal(MutableCapabilities capabilities, Map<String, String> config) {
        boolean enableLocal = config.containsKey("local") ?
                              BooleanUtils.toBoolean(config.remove("local")) :
                              context.getBooleanData(KEY_ENABLE_LOCAL, getDefaultBool(KEY_ENABLE_LOCAL));
        if (!enableLocal) { return; }

        isTerminateLocal = context.getBooleanData(KEY_TERMINATE_LOCAL, getDefaultBool(KEY_TERMINATE_LOCAL));

        String automateKey = context.getStringData(AUTOMATEKEY);
        requiresNotBlank(automateKey, "BrowserStack Access Key not defined via '" + AUTOMATEKEY + "'", automateKey);

        capabilities.setCapability("browserstack.local", true);

        try {
            WebDriverHelper helper = WebDriverHelper.newInstance(browserstack, context);
            File driver = helper.resolveDriver();

            String browserstacklocal = helper.config.getBaseName();

            if (isTerminateLocal) { RuntimeUtils.terminateInstance(browserstacklocal); }

            // start browserstack local, but wait (3s) for it to start up completely.
            ConsoleUtils.log("starting new instance of " + browserstacklocal + "...");
            RuntimeUtils.runAppNoWait(driver.getParent(), driver.getName(), Arrays.asList("--key", automateKey));
            Thread.sleep(3000);
            isRunningLocal = true;
            localExeName = driver.getName();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("unable to start BrowserStackLocal: " + e.getMessage(), e);
        }
    }

    protected void handleOS(MutableCapabilities capabilities, Map<String, String> config) {
        // os specific setting, including mobile devices
        String targetOs = StringUtils.defaultIfBlank(config.remove("os"), context.getStringData(KEY_OS));
        String targetOsVer = StringUtils.defaultIfBlank(config.remove("os_version"), context.getStringData(KEY_OS_VER));
        String bsCapsUrl = "Check https://www.browserstack.com/automate/capabilities for more details";
        String msgRequired = "'browserstack.device' is required for ";

        if (StringUtils.equalsIgnoreCase(targetOs, "ANDROID") || StringUtils.equalsIgnoreCase(targetOs, "IOS")) {
            String device = config.remove("device");
            if (StringUtils.isBlank(device)) { throw new RuntimeException(msgRequired + targetOs + ". " + bsCapsUrl); }

            setCapability(capabilities, "device", device);
            setCapability(capabilities, "os_version", targetOsVer);

            boolean realMobile =
                BooleanUtils.toBoolean(StringUtils.defaultIfBlank(config.remove("real_mobile"), "false"));
            setCapability(capabilities, "real_mobile", realMobile);
            setCapability(capabilities, "realMobile", realMobile);

            this.isRunningAndroid = StringUtils.equalsIgnoreCase(targetOs, "ANDROID");
            this.isRunningIOS = StringUtils.equalsIgnoreCase(targetOs, "IOS");
            this.device = device;
            this.isMobile = realMobile;
            return;
        }

        // resolution only works for non-mobile platforms
        setCapability(capabilities, "resolution",
                      StringUtils.defaultIfBlank(
                          config.remove("resolution"),
                          StringUtils.defaultIfBlank(context.getStringData(KEY_RESOLUTION),
                                                     context.getStringData(BROWSER_WINDOW_SIZE))));

        if (StringUtils.isNotBlank(targetOs) && StringUtils.isNotBlank(targetOsVer)) {
            setCapability(capabilities, "os", StringUtils.upperCase(targetOs));
            setCapability(capabilities, "os_version", targetOsVer);

            this.os = targetOs;
            this.isRunningWindows = StringUtils.startsWithIgnoreCase(targetOs, "WIN");
            this.isRunningOSX = StringUtils.equalsIgnoreCase(targetOs, "OS X");
            return;
        }

        // if no target OS specified, then we'll just stick to automation host's OS
        if (IS_OS_WINDOWS) {
            setCapability(capabilities, "os", "Windows");
            if (IS_OS_WINDOWS_7) { setCapability(capabilities, "os_version", "7"); }
            if (IS_OS_WINDOWS_8) { setCapability(capabilities, "os_version", "8"); }
            if (IS_OS_WINDOWS_10) { setCapability(capabilities, "os_version", "10"); }
            if (IS_OS_WINDOWS_2008) { setCapability(capabilities, "os_version", "2008"); }

            this.isRunningWindows = true;
            this.os = "Windows";
            return;
        }

        if (IS_OS_MAC_OSX) {
            setCapability(capabilities, "os", "OS X");
            if (IS_OS_MAC_OSX_SNOW_LEOPARD) { setCapability(capabilities, "os_version", "Snow Leopard"); }
            if (IS_OS_MAC_OSX_LION) { setCapability(capabilities, "os_version", "Lion"); }
            if (IS_OS_MAC_OSX_MOUNTAIN_LION) { setCapability(capabilities, "os_version", "Mountain Lion"); }
            if (IS_OS_MAC_OSX_MAVERICKS) { setCapability(capabilities, "os_version", "Mavericks"); }
            if (IS_OS_MAC_OSX_YOSEMITE) { setCapability(capabilities, "os_version", "Yosemite"); }
            if (IS_OS_MAC_OSX_EL_CAPITAN) { setCapability(capabilities, "os_version", "El Capitan"); }

            this.isRunningOSX = true;
            this.os = "OS X";
        }
    }

    protected void handleTargetBrowser(MutableCapabilities capabilities, Map<String, String> config) {
        browserName = StringUtils.defaultIfBlank(config.remove("browser"), context.getStringData(KEY_BROWSER));

        if (StringUtils.startsWithIgnoreCase(browserName, "iPad") ||
            StringUtils.startsWithIgnoreCase(browserName, "iPhone") ||
            StringUtils.startsWithIgnoreCase(browserName, "android")) {
            setCapability(capabilities, "browserName", browserName);
            if (StringUtils.startsWithIgnoreCase(browserName, "iPhone")) { this.browser = iphone; }
            return;
        }

        if (StringUtils.isNotBlank(browserName)) {
            setCapability(capabilities, "browser", StringUtils.length(browserName) < 3 ?
                                                   StringUtils.upperCase(browserName) :
                                                   WordUtils.capitalize(browserName));
        }

        browserVersion =
            StringUtils.defaultIfBlank(config.remove("browser_version"), context.getStringData(KEY_BROWSER_VER));
        if (StringUtils.isNotBlank(browserVersion)) { setCapability(capabilities, "browser_version", browserVersion); }

        if (StringUtils.startsWithIgnoreCase(browserName, "Firefox")) { this.browser = firefox; }
        if (StringUtils.startsWithIgnoreCase(browserName, "Chrome")) { this.browser = chrome; }
        if (StringUtils.startsWithIgnoreCase(browserName, "Safari")) { this.browser = safari; }
        if (StringUtils.startsWithIgnoreCase(browserName, "IE")) { this.browser = ie; }
        if (StringUtils.startsWithIgnoreCase(browserName, "Edge")) { this.browser = edge; }
    }

    protected void handleProjectMeta(MutableCapabilities capabilities, Map<String, String> config) {
        setCapability(capabilities,
                      "build",
                      StringUtils.defaultIfBlank(config.remove("build"), context.getStringData(KEY_BUILD_NUM)));

        ExecutionDefinition execDef = context.getExecDef();
        if (execDef != null) {
            if (execDef.getProject() != null && StringUtils.isNotBlank(execDef.getProject().getName())) {
                setCapability(capabilities, "project", execDef.getProject().getName());
            }

            if (StringUtils.isNotBlank(execDef.getTestScript())) {
                setCapability(capabilities, "name", new File(execDef.getTestScript()).getName());
            }
        }
    }

    protected void handleOthers(MutableCapabilities capabilities, Map<String, String> config) {
        boolean debug = config.containsKey("debug") ?
                        BooleanUtils.toBoolean(config.remove("debug")) :
                        context.getBooleanData(KEY_DEBUG, getDefaultBool(KEY_DEBUG));
        setCapability(capabilities, "browserstack.debug", debug);
        if (debug) {
            setCapability(capabilities, "browserstack.console", "verbose");
            setCapability(capabilities, "browserstack.networkLogs", true);
        }
        setCapability(capabilities, "browserstack.use_w3c", BooleanUtils.toBoolean(config.remove("use_w3c")));

        if (context.hasData(KEY_CAPTURE_CRASH)) {
            setCapability(capabilities, "browserstack.captureCrash", context.getBooleanData(KEY_CAPTURE_CRASH));
        }
    }

    @Override
    protected void terminateLocal() {
        if (isRunningLocal && isTerminateLocal) { RuntimeUtils.terminateInstance(localExeName); }
    }
}
