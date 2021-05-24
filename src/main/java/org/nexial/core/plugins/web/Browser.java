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
 *
 */

package org.nexial.core.plugins.web;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.proc.RuntimeUtils;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ShutdownAdvisor;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.CanTakeScreenshot;
import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.plugins.external.ExternalCommand;
import org.nexial.core.spi.NexialExecutionEvent;
import org.nexial.core.spi.NexialListenerFactory;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeDriverService.Builder;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.html5.Location;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.io.File.separator;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.BrowserType.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Web.*;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.web.WebDriverCapabilityUtils.initCapabilities;
import static org.nexial.core.utils.CheckUtils.requiresExecutableFile;
import static org.openqa.selenium.PageLoadStrategy.EAGER;
import static org.openqa.selenium.UnexpectedAlertBehaviour.ACCEPT;
import static org.openqa.selenium.UnexpectedAlertBehaviour.IGNORE;
import static org.openqa.selenium.chrome.ChromeDriverService.CHROME_DRIVER_LOG_PROPERTY;
import static org.openqa.selenium.chrome.ChromeDriverService.CHROME_DRIVER_VERBOSE_LOG_PROPERTY;
import static org.openqa.selenium.edge.EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY;
import static org.openqa.selenium.edge.EdgeDriverService.EDGE_DRIVER_VERBOSE_LOG_PROPERTY;
import static org.openqa.selenium.firefox.FirefoxDriver.MARIONETTE;
import static org.openqa.selenium.firefox.FirefoxDriverLogLevel.ERROR;
import static org.openqa.selenium.ie.InternetExplorerDriver.*;
import static org.openqa.selenium.remote.CapabilityType.*;

public class Browser implements ForcefulTerminate {
    private static final boolean DEBUG = false;
    private static final int MAX_DISP_LENGTH = 25;
    private static final String REGEX_WINDOW_SIZE = "^[0-9]{2,4}x[0-9]{2,4}$";
    private static final int MIN_WIDTH_OR_HEIGHT = 100;
    private static final Point INITIAL_POSITION = new Point(0, 0);
    private static final Point INITIAL_POSITION_SAFARI = new Point(2, 2);

    protected ExecutionContext context;
    // not browser profile; this is nexial-specific profile to allow for multiple instances of the same command
    protected String profile;
    protected WebDriver driver;
    // todo: not ready for prime time
    // protected ProxyHandler proxy;
    protected List<String> chromeOptions;
    protected BrowserType browserType;
    protected String browserProfile;
    protected String browserVersion;
    protected Platform browserPlatform;
    protected boolean pageSourceSupported = true;
    protected int majorVersion;
    protected Map<String, Integer> firefoxIntPrefs;
    protected Map<String, Boolean> firefoxBooleanPrefs;
    protected Map<String, String> firefoxStringPrefs;
    protected Map<String, String> firefoxDriverVersionMapping;
    protected List<String> firefoxBinArgs;

    protected String initialWinHandle;
    protected Stack<String> lastWinHandles = new Stack<String>() {
        @Override
        public synchronized void addElement(String obj) { if (!contains(obj)) { super.addElement(obj); } }

        @Override
        public synchronized boolean add(String s) { return !contains(s) && super.add(s); }

        @Override
        public void add(int index, String element) { if (!contains(element)) { super.add(index, element); } }

        @Override
        public synchronized boolean addAll(Collection<? extends String> c) {
            if (CollectionUtils.isEmpty(c)) { return false; }
            c.forEach(this::add);
            return true;
        }
    };

    protected boolean isMobile;
    protected BrowserStackHelper browserstackHelper;
    protected CrossBrowserTestingHelper cbtHelper;

    protected boolean shutdownStarted;

    protected Map<String, List<String>> chromeBinLocations;
    protected Map<String, List<String>> firefoxBinLocations;
    protected Map<String, List<String>> edgeBinLocations;

    public void setContext(ExecutionContext context) { this.context = context; }

    public void setProfile(String profile) { this.profile = profile; }

    public void setChromeOptions(List<String> chromeOptions) { this.chromeOptions = chromeOptions; }

    public void setFirefoxIntPrefs(Map<String, Integer> firefoxIntPrefs) { this.firefoxIntPrefs = firefoxIntPrefs;}

    public void setFirefoxBooleanPrefs(Map<String, Boolean> prefs) { this.firefoxBooleanPrefs = prefs; }

    public void setFirefoxStringPrefs(Map<String, String> prefs) { this.firefoxStringPrefs = prefs; }

    public void setFirefoxBinArgs(List<String> firefoxBinArgs) { this.firefoxBinArgs = firefoxBinArgs; }

    public void setChromeBinLocations(Map<String, List<String>> locations) { this.chromeBinLocations = locations; }

    public void setFirefoxBinLocations(Map<String, List<String>> locations) { this.firefoxBinLocations = locations; }

    public void setEdgeBinLocations(Map<String, List<String>> locations) { this.edgeBinLocations = locations; }

    public void setFirefoxDriverVersionMapping(Map<String, String> firefoxDriverVersionMapping) {
        this.firefoxDriverVersionMapping = firefoxDriverVersionMapping;
    }

    // public void setProxy(ProxyHandler proxy) { this.proxy = proxy; }
    // public ProxyHandler getProxyHandler() { return proxy; }

    public WebDriver getDriver() { return driver; }

    public boolean isRunFireFox() { return browserType == firefox; }

    public boolean isRunFirefoxHeadless() { return browserType == firefoxheadless; }

    public boolean isRunIE() { return browserType == ie; }

    public boolean isRunEdge() { return browserType == edge; }

    public boolean isRunEdgeChrome() { return browserType == edgechrome; }

    public boolean isRunChrome() { return browserType == chrome; }

    public boolean isHeadless() { return isRunChromeHeadless() || isRunFirefoxHeadless(); }

    public boolean isRunChromeHeadless() { return browserType == chromeheadless; }

    public boolean isRunChromeEmbedded() { return browserType == chromeembedded; }

    public boolean isRunElectron() { return browserType == electron; }

    public boolean isRunSafari() { return browserType == safari; }

    public boolean isCloudBrowser() { return isRunBrowserStack() || isRunCrossBrowserTesting(); }

    public boolean isRunBrowserStack() { return browserType == browserstack; }

    public boolean isRunCrossBrowserTesting() { return browserType == crossbrowsertesting; }

    public boolean isMobile() { return isMobile; }

    public BrowserType getBrowserType() { return browserType; }

    public String getBrowserVersion() { return browserVersion; }

    public double getBrowserVersionNum() { return NumberUtils.toDouble(browserVersion); }

    public int getMajorVersion() { return majorVersion; }

    public String getBrowserPlatform() {
        if (StringUtils.equalsIgnoreCase("XP", browserPlatform.toString()) &&
            browserPlatform.getMajorVersion() >= 6 && browserPlatform.getMinorVersion() >= 1) {
            return "WIN7";
        }

        return browserPlatform.toString();
    }

    public boolean isPageSourceSupported() { return pageSourceSupported; }

    public BrowserStackHelper getBrowserstackHelper() { return browserstackHelper; }

    public CrossBrowserTestingHelper getCbtHelper() { return cbtHelper; }

    @Override
    public String toString() {
        if (browserType == null) { return "Not yet initialized"; }
        int excelVer = NumberUtils.toInt(resolveConfig(OPT_EXCEL_VER, "2007"), 2007);
        if (excelVer >= 2012) {
            StringBuilder sb = new StringBuilder(browserType.toString());
            if (browserType.isProfileSupported()) {
                String profile = StringUtils.length(browserProfile) > MAX_DISP_LENGTH ?
                                 "..." + StringUtils.right(browserProfile, MAX_DISP_LENGTH) :
                                 browserProfile;
                sb.append(" (").append(profile).append(")");
            }
            return sb.toString();
        } else {
            return browserType.toString();
        }
    }

    public String manifest() { return browserType + " " + browserVersion; }

    @Override
    public boolean mustForcefullyTerminate() { return driver != null; }

    @Override
    public void forcefulTerminate() {
        shutdownStarted = true;
        if (driver != null) { shutdown(); }
    }

    public boolean favorJSClick() { return browserType.isJsEventFavored(); }

    public WebDriver ensureWebDriverReady() {
        boolean shouldInitialize = false;
        String browser = context.getBrowserType();

        if (driver != null) {
            if (browserType != BrowserType.valueOf(browser)) {
                // browser changed... reinitialize..
                log("current browser type (%s) is not compatible with target browser type (%s). " +
                    "Re-initializing webdriver..", browser, browserType);
                shutdown();
                shouldInitialize = true;
            } else {
                // double check that browser hasn't close down
                try {
                    String winHandle = driver.getWindowHandle();
                    debug("webdriver readiness check: current window handle=" + winHandle);
                    // everything's fine, moving on
                } catch (Throwable e) {
                    if (e instanceof NoSuchWindowException) {
                        // recalibrate window handles
                        shouldInitialize = !resolveActiveWindowHandle();
                    } else {
                        String error = e.getMessage();
                        if (StringUtils.contains(error, "unexpected end of stream on Connection") ||
                            StringUtils.contains(error, "caused connection abort: recv failed")) {
                            error("webdriver readiness check: " + error);
                        } else if (StringUtils.contains(error, "window already closed")) {
                            shouldInitialize = !resolveActiveWindowHandle();
                        } else {
                            // something's wrong with current browser window or session, need to re-init
                            shouldInitialize = true;
                            error("webdriver readiness check: %s \nBROWSER MIGHT BE TERMINATED; RESTARTING...", error);
                        }
                    }
                }
            }
        } else {
            shouldInitialize = true;
            debug("No valid instance of webdriver, initializing...");
        }

        if (shouldInitialize) {
            init();
            if (driver == null) {
                throw new RuntimeException("unable to create browser driver instance, is the browser type '" +
                                           browser + "' the intended one? Or, is firefox not available?");
            }

            // new driver means new browser instance... so we reset any window handle ref. of previous browser window.
            initialWinHandle = null;
            lastWinHandles.clear();

            // if browser supports implicit wait and we are not using explicit wait (`WEB_ALWAYS_WAIT`), then
            // we'll change timeout's implicit wait time
            Timeouts timeouts = driver.manage().timeouts();
            boolean timeoutChangesEnabled = browserType.isTimeoutChangesEnabled();
            if (timeoutChangesEnabled) {
                int loadWaitMs = context.getIntConfig("web", profile, WEB_PAGE_LOAD_WAIT_MS);
                timeouts.pageLoadTimeout(loadWaitMs, MILLISECONDS);
                log("setting browser page load timeout to %s ms", loadWaitMs);
            }

            long pollWaitMs = context.getPollWaitMs();
            boolean alwaysWait = context.getBooleanConfig("web", profile, WEB_ALWAYS_WAIT);
            if (alwaysWait) {
                log("detected %s; use fluent-wait (up to %s ms) during web automation", WEB_ALWAYS_WAIT, pollWaitMs);
            } else {
                boolean shouldWaitImplicitly = timeoutChangesEnabled && pollWaitMs > 0;
                if (shouldWaitImplicitly) {
                    timeouts.implicitlyWait(pollWaitMs, MILLISECONDS);
                    log("setting browser polling wait time to %s ms", pollWaitMs);
                } else {
                    log("implicit-wait might not be supported by the current browser");
                }
            }
        }

        if (StringUtils.isBlank(initialWinHandle)) {
            initialWinHandle = driver.getWindowHandle();
            lastWinHandles.clear();
            if (StringUtils.isNotBlank(initialWinHandle)) { lastWinHandles.push(initialWinHandle); }
        }

        return driver;
    }

    public void init() {
        // if JVM already initiated shutdown sequence, then we need to give up trying as well.
        if (shutdownStarted) { return; }

        ShutdownAdvisor.addAdvisor(this);

        System.setProperty("webdriver.reap_profile", "true");
        System.setProperty("webdriver.accept.untrusted.certs", "true");
        System.setProperty("webdriver.assume.untrusted.issuer", "true");

        // enable native event
        context.setData("webdriver.enable.native.events", 1);
        System.setProperty("webdriver.enable.native.events", "1");

        // setup browser driver
        // system property always get first priority
        // then test data
        // then default (firefox)
        String browser = context.getBrowserType();
        browserType = BrowserType.valueOf(browser);
        if (browserType.isProfileSupported()) { resolveBrowserProfile(); }
        log("initializing " + browserType);

        // now we need to "remember" the browser type (even if it's default) so that the #data tab of output file will
        // display the browser type used during execution
        if (!context.hasData(withProfile(profile, BROWSER))) {context.setData(withProfile(profile, BROWSER), browser); }

        try {
            if (isRunSafari()) { driver = initSafari(); }
            if (isRunChrome()) { driver = initChrome(false); }
            if (isRunChromeHeadless()) { driver = initChrome(true); }
            if (isRunChromeEmbedded()) { driver = initChromeEmbedded(); }
            if (isRunElectron()) { driver = initElectron(); }
            if (isRunIE()) { driver = initIE(); }
            if (isRunEdge()) { driver = initEdge(false); }
            if (isRunEdgeChrome()) { driver = initEdge(true); }
            if (isRunFireFox()) { driver = initFirefox(false); }
            if (isRunFirefoxHeadless()) { driver = initFirefox(true); }
            if (isRunBrowserStack()) { driver = initBrowserStack(); }
            if (isRunCrossBrowserTesting()) { driver = initCrossBrowserTesting(); }

            if (driver != null) {
                log("browser initialization completed for '%s'", browser);
            } else {
                error("browser '%s' is not supported.", browser);
            }

            syncContextPropToSystem(BROWSER);
        } catch (Throwable e) {
            String msg = "Error initializing browser '" + browser + "': " + ExceptionUtils.getRootCauseMessage(e);
            error(msg);
            throw new RuntimeException(msg, e);
        }
    }

    protected void shutdown() {
        // todo: not ready for prime time
        // if (proxy != null) {
        //     proxy.stopProxy();
        //     proxy = null;
        // }

        if (context != null) { context.removeData(withProfile(profile, BROWSER_META)); }

        clearWinHandles();

        if (driver == null) { return; }

        log("Shutting down webdriver...");

        NexialListenerFactory.fireEvent(NexialExecutionEvent.newBrowserEndEvent(browserType.name()));

        if (context != null) {
            CanTakeScreenshot agent = context.findCurrentScreenshotAgent();
            if (agent instanceof WebCommand) { context.clearScreenshotAgent(); }
        }

        if (browserstackHelper != null) {
            log("Terminating local agent...");
            browserstackHelper.terminateLocal();
            browserstackHelper = null;
        }

        if (cbtHelper != null) {
            log("Terminating local agent...");
            cbtHelper.terminateLocal();
            cbtHelper = null;
        }

        if (isRunElectron() &&
            context.getBooleanData(ELECTRON_FORCE_TERMINATE, getDefaultBool(ELECTRON_FORCE_TERMINATE))) {
            String clientLocation = context.getStringData(ELECTRON_CLIENT_LOCATION);
            if (StringUtils.isNotBlank(clientLocation)) {
                String exeName = StringUtils.substringAfterLast(StringUtils.replace(clientLocation, "\\", "/"), "/");
                log("Forcefully terminating '%s'...", exeName);
                RuntimeUtils.terminateInstance(exeName);
            }
        }

        if (!isRunFireFox() && !isRunFirefoxHeadless() && !(driver instanceof FirefoxDriver)) {
            // close before quite doesn't seem to work for firefox driver or electron app
            log("Close the current window...");
            try { driver.close(); } catch (Throwable e) { }
        }

        try {
            log("Quit this driver, closing every associated window.");
            driver.quit();
            Thread.sleep(1000);
        } catch (Throwable e) {
            error("Error occurred while shutting down webdriver - " + ExceptionUtils.getRootCauseMessage(e));
        } finally {
            driver = null;
        }
    }

    protected String updateWinHandle() {
        try { initialWinHandle = driver.getWindowHandle(); } catch (WebDriverException e) { }
        resyncWinHandles();
        return initialWinHandle;
    }

    protected String getInitialWinHandle() {
        resyncWinHandles();
        return initialWinHandle;
    }

    protected String getCurrentWinHandle() {
        if (StringUtils.isNotBlank(initialWinHandle)) { return initialWinHandle; }
        if (CollectionUtils.isNotEmpty(lastWinHandles)) { return lastWinHandles.peek(); }
        return updateWinHandle();
    }

    protected void addWinHandle(String lastWinHandle) { lastWinHandles.push(lastWinHandle); }

    protected String removeLastWinHandle() { return lastWinHandles.pop(); }

    protected Stack<String> getLastWinHandles() { return lastWinHandles; }

    protected Stack<String> removeWinHandle(String handle) {
        if (StringUtils.isBlank(handle)) { return lastWinHandles; }

        lastWinHandles.remove(handle);
        if (StringUtils.equals(handle, initialWinHandle)) { initialWinHandle = null; }
        resyncWinHandles();
        return lastWinHandles;
    }

    protected void resyncWinHandles() {
        try {
            // re-calibrate win handles
            Set<String> handles = driver.getWindowHandles();
            if (CollectionUtils.isNotEmpty(handles)) { lastWinHandles.addAll(handles); }

            // reset initialWinHandle
            if (initialWinHandle == null) {
                try {
                    initialWinHandle = driver.getWindowHandle();
                } catch (WebDriverException e) {
                    initialWinHandle = CollectionUtils.isNotEmpty(lastWinHandles) ? lastWinHandles.get(0) : null;
                }
                lastWinHandles.add(initialWinHandle);
            }
        } catch (NoSuchSessionException e) {
            // this means that the last or only window is closed.
            error("Unable to re-synchronize window handles: %s", e.getMessage());
            lastWinHandles.clear();
            initialWinHandle = null;
        }
    }

    protected void clearWinHandles() {
        lastWinHandles.clear();
        initialWinHandle = null;
    }

    protected boolean isOnlyOneWindowRemaining() {
        // return StringUtils.equals(driver.getWindowHandle(), initialWinHandle) &&
        return CollectionUtils.size(driver.getWindowHandles()) <= 1 &&
               CollectionUtils.size(lastWinHandles) <= 1;
    }

    protected WebDriver initChromeEmbedded() throws IOException {
        // ensure path specified for AUT app (where chromium is embedded)
        String clientLocation = context.getStringData(CEF_CLIENT_LOCATION);
        requiresExecutableFile(clientLocation);

        WebDriverHelper helper = resolveChromeDriverLocation();
        ChromeOptions options = new ChromeOptions().addArguments(this.chromeOptions).setBinary(clientLocation);
        ChromeDriver chrome;
        try {
            chrome = new ChromeDriver(options);
        } catch (Exception e) {
            if (updateChromeDriver(helper)) {
                chrome = new ChromeDriver(options);
            } else {
                throw new RuntimeException("Fail to start webdriver: " + e.getMessage());
            }
        }

        Capabilities capabilities = chrome.getCapabilities();
        initCapabilities(context, (MutableCapabilities) capabilities);

        postInit(chrome);
        return chrome;
    }

    protected WebDriver initElectron() throws IOException {
        // ensure path specified for AUT app (where electron app is)
        String clientLocation = context.getStringData(ELECTRON_CLIENT_LOCATION);
        requiresExecutableFile(clientLocation);

        resolveChromeDriverLocation();

        ChromeOptions options = new ChromeOptions().setBinary(clientLocation)
                                                   .setAcceptInsecureCerts(true)
                                                   .setUnhandledPromptBehaviour(IGNORE);

        options.addArguments("--disable-extensions"); // disabling extensions
        // options.addArguments("--disable-gpu"); // applicable to windows os only
        options.addArguments("--disable-dev-shm-usage"); // overcome limited resource problems
        options.addArguments("--no-sandbox"); // Bypass OS security model
        handleChromeProfile(options);

        int port = 12209;
        if (NumberUtils.isDigits(context.getStringData(CHROME_REMOTE_PORT))) {
            port = NumberUtils.toInt(context.getStringData(CHROME_REMOTE_PORT));
        }
        log("enabling chrome remote port: %s", port);
        options.addArguments("--remote-debugging-port=" + port);

        // options.addArguments("--auto-open-devtools-for-tabs"); // open devtools
        // options.addArguments("--headless");

        Builder cdsBuilder = new Builder();
        if (context.getBooleanData(ELECTRON_LOG_ENABLED, getDefaultBool(ELECTRON_LOG_ENABLED))) {
            // determine chrome log file
            String appName = clientLocation;
            if (StringUtils.contains(appName, "/")) { appName = StringUtils.substringAfterLast(appName, "/"); }
            if (StringUtils.contains(appName, "\\")) { appName = StringUtils.substringAfterLast(appName, "\\"); }
            if (StringUtils.contains(appName, ".")) { appName = StringUtils.substringBeforeLast(appName, "."); }
            File logFile = resolveBrowserLogFile("electron-" + appName + ".log");
            log("enabling logging for Electron app: %s", logFile.getAbsolutePath());

            boolean verbose = context.getBooleanData(ELECTRON_LOG_VERBOSE, getDefaultBool(ELECTRON_LOG_VERBOSE));
            cdsBuilder = cdsBuilder.withVerbose(verbose).withLogFile(logFile);
        }

        pageSourceSupported = false;
        return new ChromeDriver(cdsBuilder.build(), options);
    }

    protected WebDriver initChrome(boolean headless) throws IOException {
        // check https://github.com/SeleniumHQ/selenium/wiki/ChromeDriver for details

        WebDriverHelper helper = resolveChromeDriverLocation();

        boolean activateLogging = context.getBooleanConfig("web", profile, CHROME_LOG_ENABLED);
        if (activateLogging) {
            String chromeLog = resolveBrowserLogFile("chrome-browser." + profile + ".log").getAbsolutePath();
            log("enabling logging for Chrome: %s", chromeLog);
            System.setProperty(CHROME_DRIVER_LOG_PROPERTY, chromeLog);
            System.setProperty(CHROME_DRIVER_VERBOSE_LOG_PROPERTY, "true");
        } else {
            System.setProperty(CHROME_DRIVER_VERBOSE_LOG_PROPERTY, "false");
        }

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.setHeadless(true);
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
        }

        options.addArguments(this.chromeOptions);
        // force the chrome native prompt to go silent
        options.addArguments("enable-strict-powerful-feature-restrictions");

        // time to experiment...
        Map<String, Object> prefs = new HashMap<>();

        // disable save password "bubble", in case we are running in non-incognito mode
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);

        String downloadTo = resolveDownloadTo(context);
        if (StringUtils.isNotBlank(downloadTo)) {
            // allow PDF to be downloaded instead of displayed (pretty much useless)
            if (context.getBooleanConfig("web", profile, OPT_DOWNLOAD_PDF)) {
                prefs.put("plugins.always_open_pdf_externally", true);
            }
            prefs.put("download.default_directory", downloadTo);
            // prefs.put("download.extensions_to_open", "application/pdf");
        }

        // https://stackoverflow.com/questions/40244670/disable-geolocation-in-selenium-chromedriver-with-python
        boolean enableGeoLocation = context.getBooleanConfig("web", profile, GEOLOCATION);
        prefs.put("geolocation", enableGeoLocation);
        prefs.put("profile.default_content_setting_values.geolocation", enableGeoLocation ? 1 : 2);
        prefs.put("profile.managed_default_content_settings.geolocation", enableGeoLocation ? 1 : 2);

        // To Turns off multiple download warning
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);
        // Turns off download prompt
        prefs.put("download.prompt_for_download", false);

        options.setExperimentalOption("prefs", prefs);

        handleChromeProfile(options);

        String chromeRemotePort = context.getStringConfig("web", profile, CHROME_REMOTE_PORT);
        if (NumberUtils.isDigits(chromeRemotePort)) {
            int port = NumberUtils.toInt(chromeRemotePort);
            log("enabling chrome remote port: %s", port);
            options.addArguments("--remote-debugging-port=" + port);
        }

        String binaryLocation = resolveBinLocation();
        if (StringUtils.isNotBlank(binaryLocation)) { options.setBinary(binaryLocation); }

        // proxy code not yet ready for prime time
        // if (context.getBooleanData(OPT_PROXY_REQUIRED)) {
        //     try {
        //         InetAddress localHost = InetAddress.getLocalHost();
        //         options.addArguments("--proxy-server=http://" + localHost.getCanonicalHostName() + ":" + PROXY_PORT);
        //         options.addArguments("--proxy-bypass-list=127.0.0.1,localhost");
        //     } catch (UnknownHostException e) {
        //         throw new RuntimeException("Unable to determine localhost hostname: " + e.getMessage());
        //     }
        // }

        // support mobile emulation on chrome
        // ref: http://chromedriver.chromium.org/mobile-emulation
        // ref: https://developers.google.com/web/tools/chrome-devtools/device-mode/?utm_source=dcc&utm_medium=redirect&utm_campaign=2016q3
        // issue: https://bugs.chromium.org/p/chromedriver/issues/detail?id=2144&desc=2
        // devices: https://codesearch.chromium.org/codesearch/f/chromium/src/third_party/blink/renderer/devtools/front_end/emulated_devices/module.json
        String emuDevice = context.getStringConfig("web", profile, EMU_DEVICE_NAME);
        if (StringUtils.isNotBlank(emuDevice)) {
            Map<String, String> mobileEmulation = new HashMap<>();
            mobileEmulation.put("deviceName", emuDevice);
            options.setExperimentalOption("mobileEmulation", mobileEmulation);
            log("setting mobile emulation on Chrome as %s", emuDevice);
        }

        String emuUserAgent = context.getStringConfig("web", profile, EMU_USER_AGENT);
        if (StringUtils.isNotBlank(emuUserAgent)) {
            Map<String, Object> deviceMetrics = new HashMap<>();
            deviceMetrics.put("width", context.getIntConfig("web", profile, EMU_WIDTH));
            deviceMetrics.put("height", context.getIntConfig("web", profile, EMU_HEIGHT));
            deviceMetrics.put("pixelRatio", context.getDoubleConfig("web", profile, EMU_PIXEL_RATIO));
            deviceMetrics.put("touch", context.getBooleanConfig("web", profile, EMU_TOUCH));

            Map<String, Object> mobileEmulation = new HashMap<>();
            mobileEmulation.put("deviceMetrics", deviceMetrics);
            mobileEmulation.put("userAgent", emuUserAgent);
            options.setExperimentalOption("mobileEmulation", mobileEmulation);
            log("setting mobile emulation on Chrome as %s", emuUserAgent);
        }

        // starting from chrome 80, samesite is enforced by default... we need to workaround it
        List<String> experimentalFlags = new ArrayList<>();
        experimentalFlags.add("same-site-by-default-cookies@1");
        experimentalFlags.add("enable-removing-all-third-party-cookies@2");
        experimentalFlags.add("cookies-without-same-site-must-be-secure@1");
        Map<String, Object> localState = new HashMap<>();
        localState.put("browser.enabled_labs_experiments", experimentalFlags);
        options.setExperimentalOption("localState", localState);

        // get rid of the infobar on top of the browser window
        //  Disable a few things considered not appropriate for automation.
        //     - disables the password saving UI (which covers the usecase of the removed `disable-save-password-bubble` flag)
        //     - disables infobar animations
        //     - disables dev mode extension bubbles (?), and doesn't show some other info bars
        //     - disables auto-reloading on network errors (source)
        //     - means the default browser check prompt isn't shown
        //     - avoids showing these 3 infobars: ShowBadFlagsPrompt, GoogleApiKeysInfoBarDelegate, ObsoleteSystemInfoBarDelegate
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation", "load-extension"});

        // strategy to instruct chromedriver not to wait for full page load
        // https://stackoverflow.com/questions/44770796/how-to-make-selenium-not-wait-till-full-page-load-which-has-a-slow-script/44771628#44771628
        // but this doesn't work for older driver... so we need to catch exception
        try {
            options.setPageLoadStrategy(EAGER);
        } catch (WebDriverException e) {
            // oh well... i tried; never mind...
            log("unable to page load strategy to EAGER; resetting back to default");
        }

        ChromeDriverService driverService = null;
        ChromeDriver chrome;
        try {
            driverService = ChromeDriverService.createDefaultService();
            chrome = new ChromeDriver(driverService, options);
        } catch (Exception e) {
            if (updateChromeDriver(helper)) {
                if (driverService == null) { driverService = ChromeDriverService.createDefaultService(); }
                chrome = new ChromeDriver(driverService, options);
            } else {
                throw new RuntimeException("Fail to start webdriver: " + e.getMessage());
            }
        }

        Capabilities capabilities = chrome.getCapabilities();
        initCapabilities(context, (MutableCapabilities) capabilities);

        browserVersion = capabilities.getVersion();
        browserPlatform = capabilities.getPlatform();
        postInit(chrome);

        if (enableGeoLocation) {
            // https://stackoverflow.com/a/47399337/4615880
            double longitude = context.getDoubleConfig("web", profile, GEO_LONGITUDE);
            double latitude = context.getDoubleConfig("web", profile, GEO_LATITUDE);
            if (longitude != 0 && latitude != 0) { chrome.setLocation(new Location(longitude, latitude, 0)); }
        }

        // pageSourceSupported = false;

        return chrome;
    }

    protected WebDriver initFirefox(boolean headless) throws IOException {
        BrowserType browserType = headless ? firefoxheadless : firefox;
        String binaryLocation = resolveBinLocation();
        WebDriverHelper helper = WebDriverHelper.newInstance(browserType, context);
        helper.setFirefoxDriverVersionMapping(this.firefoxDriverVersionMapping);
        helper.setBrowserBinLocation(binaryLocation);
        File driver = helper.resolveDriver();

        String driverPath = driver.getAbsolutePath();
        context.setData(SELENIUM_GECKO_DRIVER, driverPath);
        System.setProperty(SELENIUM_GECKO_DRIVER, driverPath);
        // todo: need them?
        context.setData(MARIONETTE, true);
        System.setProperty(MARIONETTE, "true");

        FirefoxOptions options;

        try {
            DesiredCapabilities capabilities;

            // todo: not ready for prime time
            // if (proxy != null) {
            //     Proxy localProxy = proxy.getServer().seleniumProxy();
            //
            //     String localHost = InetAddress.getLocalHost().getHostName();
            //     localProxy.setHttpProxy(localHost);
            //     localProxy.setSslProxy(localHost);
            //
            //     capabilities = new DesiredCapabilities();
            //     initCapabilities(context, capabilities);
            //     capabilities.setCapability(PROXY, localProxy);
            //
            //     Properties browsermobProps = PropertiesLoaderUtils.loadAllProperties(
            //         "org/nexial/core/plugins/har/browsermob.properties");
            //     int proxyPort = Integer.parseInt(browsermobProps.getProperty("browsermob.port"));
            //
            //     options = new FirefoxOptions(capabilities);
            //     options.addPreference("network.proxy.type", 1);
            //     options.addPreference("network.proxy.http_port", proxyPort);
            //     options.addPreference("network.proxy.ssl_port", proxyPort);
            //     options.addPreference("network.proxy.no_proxies_on", "");
            // } else {
            options = new FirefoxOptions();
            capabilities = new DesiredCapabilities();
            initCapabilities(context, capabilities);
            options.merge(capabilities);

            Proxy proxy = (Proxy) capabilities.getCapability(PROXY);
            if (proxy != null) {
                options.addPreference("network.proxy.type", 1);

                String proxyHostAndPort = proxy.getHttpProxy();
                String proxyHost = StringUtils.substringBefore(proxyHostAndPort, ":");
                String proxyPort = StringUtils.substringAfter(proxyHostAndPort, ":");
                options.addPreference("network.proxy.http", proxyHost);
                options.addPreference("network.proxy.http_port", proxyPort);
                options.addPreference("network.proxy.ssl", proxyHost);
                options.addPreference("network.proxy.ssl_port", proxyPort);
                options.addPreference("network.proxy.ftp", proxyHost);
                options.addPreference("network.proxy.ftp_port", proxyPort);
                options.addPreference("network.proxy.no_proxies_on", "localhost, 127.0.0.1");
            }
            // }

            if (StringUtils.isNotBlank(binaryLocation)) { options.setBinary(binaryLocation); }

            handleFirefoxProfile(options, capabilities);

            if (headless) { options.setHeadless(true); }

            // merge configured prefs (spring) to `options` instance
            if (MapUtils.isNotEmpty(firefoxBooleanPrefs)) { firefoxBooleanPrefs.forEach(options::addPreference); }
            if (MapUtils.isNotEmpty(firefoxIntPrefs)) { firefoxIntPrefs.forEach(options::addPreference); }
            if (MapUtils.isNotEmpty(firefoxStringPrefs)) {
                boolean downloadPdf = context.getBooleanConfig("web", profile, OPT_DOWNLOAD_PDF);
                firefoxStringPrefs.forEach((key, value) -> {
                    if (StringUtils.equals(key, "browser.helperApps.neverAsk.saveToDisk")) {
                        options.addPreference(key, value + (downloadPdf ? ",application/x-pdf" : ""));
                    } else if (StringUtils.equals(key, "browser.helperApps.neverAsk.openFile")) {
                        options.addPreference(key, value + (downloadPdf ? ",application/x-pdf" : ""));
                    } else {
                        options.addPreference(key, value);
                    }
                });
            }
            if (CollectionUtils.isNotEmpty(firefoxBinArgs)) { firefoxBinArgs.forEach(options::addArguments); }

            boolean ignoreAlert = BooleanUtils.toBoolean(context.getBooleanData(OPT_ALERT_IGNORE_FLAG));
            options.setUnhandledPromptBehaviour(ignoreAlert ? IGNORE : ACCEPT);
            if (context.getBooleanConfig("web", profile, BROWSER_ACCEPT_INVALID_CERTS)) {
                options.setAcceptInsecureCerts(true);
            }
            options.setPageLoadStrategy(EAGER);
            options.setLogLevel(ERROR);

            // set current output directory for download
            String downloadTo = resolveDownloadTo(context);
            if (StringUtils.isNotBlank(downloadTo)) { options.addPreference("browser.download.dir", downloadTo); }

            boolean enableGeoLocation = context.getBooleanConfig("web", profile, GEOLOCATION);
            options.addPreference("geo.enabled", enableGeoLocation);
            options.addPreference("geo.provider.testing", enableGeoLocation);
            options.addPreference("geo.provider.use_corelocation", enableGeoLocation);
            options.addPreference("geo.prompt.testing", enableGeoLocation);
            options.addPreference("geo.prompt.testing.allow", enableGeoLocation);
            options.addPreference("geo.wifi.scan", true);

            if (enableGeoLocation) {
                double longitude = context.getDoubleConfig("web", profile, GEO_LONGITUDE);
                double latitude = context.getDoubleConfig("web", profile, GEO_LATITUDE);
                if (longitude != 0 && latitude != 0) {
                    String geoPosition = String.format("{\"location\": {\"lat\":%s, \"lng\":%s}, \"accuracy\":27000.0}",
                                                       longitude, latitude);
//                    options.addPreference("geo.wifi.uri", "data:application/json," + geoPosition);
                    options.addPreference("geo.provider.network.url", "data:application/json," + geoPosition);
                }
            }

            FirefoxDriver firefox;
            try {
                firefox = new FirefoxDriver(options);
            } catch (Exception e) {
                log("Corresponding browser might have been updated recently. Nexial will stop the webdriver in use to update it...");
                RuntimeUtils.terminateInstance(driver.getName());
                if (helper.updateCompatibleDriverVersion()) {
                    helper.resolveDriver();
                    firefox = new FirefoxDriver(options);
                } else { throw new RuntimeException(e); }
            }
            browserVersion = capabilities.getVersion();
            browserPlatform = capabilities.getPlatform();

            postInit(firefox);
            return firefox;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected WebDriver initEdge(boolean chrome) throws IOException {
        System.setProperty(EDGE_DRIVER_VERBOSE_LOG_PROPERTY, context.getStringConfig("web", profile, EDGE_LOG_ENABLED));

        WebDriverHelper helper = WebDriverHelper.newInstance(chrome ? edgechrome : edge, context);
        helper.setBrowserBinLocation(resolveBinLocation());
        File driver = helper.resolveDriver();
        String driverPath = driver.getAbsolutePath();
        context.setData(EDGE_DRIVER_EXE_PROPERTY, driverPath);
        System.setProperty(EDGE_DRIVER_EXE_PROPERTY, driverPath);

        EdgeOptions options = new EdgeOptions();

        String customBinary = context.getStringConfig("web", profile, CEF_CLIENT_LOCATION);
        if (StringUtils.isNotEmpty(customBinary)) {
            if (!FileUtil.isFileExecutable(customBinary)) {
                throw new FileNotFoundException("Specified file is not an executable binary: " + customBinary);
            }

            options.setCapability(BROWSER_NAME, "webview2");
            Map<String, Object> edgeOptions = new HashMap<>();
            edgeOptions.put("binary", new File(customBinary).getAbsolutePath());
            options.setCapability("ms:edgeOptions", UnmodifiableMap.unmodifiableMap(edgeOptions));
        }

        if (context.getBooleanConfig("web", profile, BROWSER_INCOGNITO)) { options.setCapability("InPrivate", true); }

        EdgeDriver edge = new EdgeDriver(options);
        postInit(edge);

        Capabilities capabilities = edge.getCapabilities();
        browserVersion = capabilities.getVersion();
        browserPlatform = capabilities.getPlatform();
        pageSourceSupported = false;

        StringBuilder log = new StringBuilder("Edge WebDriver capabilities:" + NL);
        capabilities.asMap().forEach((key, val) -> log.append("\t").append(key).append("\t=").append(val).append(NL));
        ConsoleUtils.log(log + NL);

        return edge;
    }

    protected WebDriver initIE() throws IOException {
        resolveIEDriverLocation();

        // check https://github.com/SeleniumHQ/selenium/wiki/InternetExplorerDriver for details
        System.setProperty(SELENIUM_IE_LOG_LEVEL, "WARN");
        syncContextPropToSystem(SELENIUM_IE_LOG_LEVEL);
        syncContextPropToSystem(SELENIUM_IE_DRIVER);
        syncContextPropToSystem(SELENIUM_IE_LOG_LOGFILE);
        syncContextPropToSystem(SELENIUM_IE_SILENT);
        boolean runWin64 = EnvUtils.isRunningWindows64bit();

        if (System.getProperty(SELENIUM_IE_DRIVER) == null) {
            runWin64 = EnvUtils.isRunningWindows64bit() && !context.getBooleanConfig("web", profile, OPT_FORCE_IE_32);
        }

        boolean ignoreAlert = resolveConfig(OPT_ALERT_IGNORE_FLAG, false);
        InternetExplorerOptions capabilities = new InternetExplorerOptions();
        capabilities = capabilities.setUnhandledPromptBehaviour(ignoreAlert ? IGNORE : ACCEPT)
                                   // Indicates whether to skip the check that the browser's zoom level is set to 100%.
                                   // Value is set to false by default.
                                   .ignoreZoomSettings()

                                   // clean session during InternetExplorer starting. This clears the cache for all
                                   // running instances of InternetExplorer, including those started manually.
                                   .destructivelyEnsureCleanSession()

                                   // https://seleniumonlinetrainingexpert.wordpress.com/tag/introduce_flakiness_by_ignoring_security_domains/
                                   .introduceFlakinessByIgnoringSecurityDomains()
        ;

        if (context.getBooleanConfig("web", profile, IE_REQUIRE_WINDOW_FOCUS)) { capabilities.requireWindowFocus(); }

        // Capability that defines to use whether to use native or javascript events during operations.
        capabilities.setCapability(HAS_NATIVE_EVENTS, false);
        capabilities.setCapability(SUPPORTS_WEB_STORAGE, true);
        capabilities.setCapability(SUPPORTS_ALERTS, true);
        capabilities.setCapability(ACCEPT_SSL_CERTS, true);
        if (context.getBooleanConfig("web", profile, BROWSER_ACCEPT_INVALID_CERTS)) {
            capabilities.setCapability(ACCEPT_INSECURE_CERTS, true);
        }

        // Determines whether the driver should attempt to remove obsolete elements from the element cache
        // on page navigation (true by default). This is to help manage the IE driver's memory footprint,
        // removing references to invalid elements.
        capabilities.setCapability(ENABLE_ELEMENT_CACHE_CLEANUP, true);

        if (!runWin64) {
            capabilities = capabilities
                // .useCreateProcessApiToLaunchIe()
                // .useShellWindowsApiToAttachToIe()
                .takeFullPageScreenshot();
        } else {
            capabilities = capabilities
                // https://stackoverflow.com/a/32691070/4615880
                // https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/5116
                //      (search for "After investigation of this issue")
                .requireWindowFocus()
                // .useCreateProcessApiToLaunchIe()
                .useShellWindowsApiToAttachToIe();
        }

        // With the creation of the IEDriverServer.exe, it should be possible to create and use multiple
        // simultaneous instances of the InternetExplorerDriver. However, this functionality is largely
        // untested, and there may be issues with cookies, window focus, and the like. If you attempt to
        // use multiple instances of the IE driver, and run into such issues, consider using the
        // RemoteWebDriver and virtual machines.
        //
        // There are 2 solutions for problem with cookies (and another session items) shared between
        // multiple instances of InternetExplorer.
        //
        // The first is to start your InternetExplorer in private mode. After that InternetExplorer
        // will be started with clean session data and will not save changed session data at quiting.
        // To do so you need to pass 2 specific capabilities to driver: ie.forceCreateProcessApi with true value
        if (context.getBooleanConfig("web", profile, BROWSER_INCOGNITO)) {
            capabilities.setCapability(FORCE_CREATE_PROCESS, true);
            // and ie.browserCommandLineSwitches with -private value.
            capabilities.setCapability(IE_SWITCHES, "-private");
        }

        // Be note that it will work only for InternetExplorer 8 and newer, and Windows Registry
        // HKLM_CURRENT_USER\\Software\\Microsoft\\Internet Explorer\\Main path should contain key TabProcGrowth
        // with 0 value.

        // Capability that defines setting the proxy information for a single IE process
        // without affecting the proxy settings of other instances of IE.
        // capabilities.setCapability(IE_USE_PER_PROCESS_PROXY, true);

        // InternetExplorerDriverService service = InternetExplorerDriverService.createDefaultService();
        // RemoteWebDriver ie = new RemoteWebDriver(new DriverCommandExecutor(service), capabilities);
        // InternetExplorerDriver ie = new InternetExplorerDriver(service, capabilities);
        InternetExplorerDriver ie = new InternetExplorerDriver(capabilities);
        Capabilities caps = ie.getCapabilities();
        browserVersion = caps.getVersion();
        browserPlatform = caps.getPlatform();
        postInit(ie);

        StringBuilder log = new StringBuilder("IEDriverServer capabilities:" + NL);
        caps.asMap().forEach((key, val) -> log.append("\t").append(key).append("\t= ").append(val).append(NL));
        ConsoleUtils.log(log + NL);

        return ie;
    }

    protected WebDriver initSafari() {
        // change location of safari's download location to "out" directory
        if (!IS_OS_MAC_OSX) { throw new RuntimeException("Safari automation is only supported on Mac OSX. Sorry..."); }

        String out = context.getProject().getOutPath();
        try {
            log("modifying safari's download path:" + NL +
                ExternalCommand.exec("defaults write com.apple.Safari DownloadsPath \"" + out + "\""));
        } catch (IOException e) {
            error("Unable to modify safari's download path to %s: %s", out, e);
        }

        SafariOptions options = new SafariOptions();

        // Whether to make sure the session has no cookies, cache entries, local storage, or databases.
        options.setUseTechnologyPreview(context.getBooleanConfig("web", profile, SAFARI_USE_TECH_PREVIEW));

        // todo: Create a SafariDriverService to specify what Safari flavour should be used and pass the service instance to a SafariDriver constructor.  When SafariDriver API updates to better code.. can't do this now
        SafariDriver safari = new SafariDriver(options);
        MutableCapabilities capabilities = (MutableCapabilities) safari.getCapabilities();
        initCapabilities(context, capabilities);
        // setCapability(capabilities, "javascriptEnabled", true);
        // setCapability(capabilities, "databaseEnabled", true);

        browserVersion = capabilities.getVersion();
        browserPlatform = capabilities.getPlatform();
        postInit(safari);
        return safari;
    }

    protected WebDriver initBrowserStack() {
        browserstackHelper = new BrowserStackHelper(context);
        WebDriver webDriver = browserstackHelper.initWebDriver();

        isMobile = browserstackHelper.isMobile();
        pageSourceSupported = browserstackHelper.isPageSourceSupported();
        // browserVersion = browserstack.getBrowserVersion();

        postInit(webDriver);
        return webDriver;
    }

    protected WebDriver initCrossBrowserTesting() {
        cbtHelper = new CrossBrowserTestingHelper(context);
        WebDriver webDriver = cbtHelper.initWebDriver();

        isMobile = cbtHelper.isMobile();
        pageSourceSupported = cbtHelper.isPageSourceSupported();

        postInit(webDriver);
        return webDriver;
    }

    protected String resolveDownloadTo(ExecutionContext context) {
        if (context == null) { return null; }

        String downloadTo = context.getStringConfig("web", profile, OPT_DOWNLOAD_TO);
        if (StringUtils.isNotBlank(downloadTo)) { return downloadTo; }

        return context.getProject() != null &&
               StringUtils.isNotBlank(context.getProject().getOutPath()) ?
               context.getProject().getOutPath() :
               context.getStringData(OPT_OUT_DIR);
    }

    protected void setWindowSize(WebDriver driver) {
        // not suitable for cef, electron or mobile
        // for safari, we can only change position AFTER browser is opened
        if (isRunChromeEmbedded() ||
            isRunElectron() ||
            isMobile() ||
            (isRunBrowserStack() && browserstackHelper.browser == safari) ||
            (isRunCrossBrowserTesting() && cbtHelper.browser == safari)) {
            return;
        }

        setWindowSizeForcefully(driver);
    }

    protected void setWindowSizeForcefully(WebDriver driver) {
        String windowSize = context.getStringConfig("web", profile, BROWSER_WINDOW_SIZE);
        if ((isHeadless()) && StringUtils.isBlank(windowSize)) {
            // window size required for headless browser
            windowSize = context.getStringConfig("web", profile, BROWSER_DEFAULT_WINDOW_SIZE);
            log("No '%s' defined for headless browser; default to %s", BROWSER_WINDOW_SIZE, windowSize);
        }

        if (StringUtils.isNotBlank(windowSize)) {
            Pattern p = Pattern.compile(REGEX_WINDOW_SIZE);
            Matcher m = p.matcher(windowSize);
            if (!m.matches()) {throw new RuntimeException("The valid window size not defined.. Found : " + windowSize);}

            String[] dim = StringUtils.split(windowSize, "x");

            int width = NumberUtils.toInt(dim[0]);
            int height = NumberUtils.toInt(dim[1]);
            if (width < MIN_WIDTH_OR_HEIGHT || height < MIN_WIDTH_OR_HEIGHT) {
                throw new RuntimeException("Window height or width should be greater than " + MIN_WIDTH_OR_HEIGHT +
                                           ": " + windowSize);
            }

            Window window = driver.manage().window();
            window.setSize(new Dimension(width, height));
        }
    }

    protected void setWindowPosition(WebDriver driver) {
        if (driver == null) { return; }

        Window window = driver.manage().window();
        if (window == null) { return; }

        boolean runningSafari = isRunSafari() ||
                                (isRunBrowserStack() && browserstackHelper.browser == safari) ||
                                (isRunCrossBrowserTesting() && cbtHelper.browser == safari);

        Point position = null;
        String windowPosition = context.getStringConfig("web", profile, OPT_POSITION);
        if (StringUtils.isBlank(windowPosition)) {
            position = runningSafari ? INITIAL_POSITION_SAFARI : INITIAL_POSITION;
        } else {
            String[] coordinates = StringUtils.split(windowPosition, context.getTextDelim());
            if (ArrayUtils.getLength(coordinates) != 2 ||
                !NumberUtils.isCreatable(coordinates[0]) ||
                !NumberUtils.isCreatable(coordinates[1])) {
                error("Invalid windows position: %s; default to 0,0", windowPosition);
            } else {
                position = new Point(NumberUtils.toInt(coordinates[0]), NumberUtils.toInt(coordinates[1]));
            }
        }

        window.setPosition(position);
    }

    private void handleFirefoxProfile(FirefoxOptions options, DesiredCapabilities capabilities) {
        String userDataDir = context.getStringConfig("web", profile, BROWSER_USER_DATA);
        if (StringUtils.isNotBlank(userDataDir)) {
            if (FileUtil.isDirectoryReadable(userDataDir)) {
                options.setProfile(new FirefoxProfile(new File(userDataDir)));
                return;
            }

            error("Unable to add user-data on %s because it is NOT accessible", userDataDir);
        }

        if (context.getBooleanConfig("web", profile, BROWSER_INCOGNITO)) {
            capabilities.setCapability("browser.privatebrowsing.autostart", true);
        }
    }

    private void handleChromeProfile(ChromeOptions options) {
        String userDataDir = context.getStringConfig("web", profile, BROWSER_USER_DATA);
        if (StringUtils.isNotBlank(userDataDir)) {
            if (FileUtil.isDirectoryReadable(userDataDir)) {
                options.addArguments("--user-data-dir=" + userDataDir);
                return;
            }

            error("Unable to add user-data on %s because it is NOT accessible", userDataDir);
        }

        if (context.getBooleanConfig("web", profile, BROWSER_INCOGNITO)) { options.addArguments(KEY_INCOGNITO); }
    }

    @NotNull
    private boolean updateChromeDriver(WebDriverHelper helper) throws IOException {
        log("Corresponding browser might have been updated recently. Nexial will stop the webdriver in use to update it...");
        RuntimeUtils.terminateInstance(new File(helper.getDriverLocation()).getName());
        if (helper.updateCompatibleDriverVersion()) {
            helper.resolveDriver();
            return true;
        }

        return false;
    }

    private boolean resolveActiveWindowHandle() {
        // find last window
        if (CollectionUtils.isNotEmpty(lastWinHandles)) {
            List<String> badHandles = new ArrayList<>();
            String winHandle = null;

            for (int i = lastWinHandles.size() - 1; i >= 0; i--) {
                String lastWinHandle = lastWinHandles.get(i);
                try {
                    driver.switchTo().window(lastWinHandle);
                    winHandle = lastWinHandle;
                    break;
                } catch (Throwable e) {
                    // keep going...
                    badHandles.add(lastWinHandle);
                }
            }

            if (CollectionUtils.isNotEmpty(badHandles)) { lastWinHandles.removeAll(badHandles); }

            if (winHandle != null) {
                debug("webdriver readiness check: last window handle appeared invalid; reverting to previous one: %s",
                      winHandle);
                return true;
            } // else, recheck via webdriver for the last set of window handles.
        } else if (initialWinHandle != null) {
            debug("webdriver readiness check: last window handle appeared invalid; reverting to initial one: %s",
                  initialWinHandle);
            return true;
        }

        // recheck via webdriver
        Set<String> currentWinHandles = driver.getWindowHandles();
        if (CollectionUtils.isNotEmpty(currentWinHandles)) {
            // no guaranteed that the last is the active one...
            // but good enough
            initialWinHandle = IterableUtils.get(currentWinHandles, currentWinHandles.size() - 1);
            currentWinHandles.forEach(handle -> lastWinHandles.push(handle));
            debug("webdriver readiness check: last window handle appeared to be invalid; reverting to %s", initialWinHandle);
            return true;
        }

        // exhausted all possibilities..
        error("webdriver readiness check: no windows available\nBROWSER MIGHT BE TERMINATED; RESTARTING...");
        return false;
    }

    private void postInit(WebDriver driver) {
        String browserVersion = getBrowserVersion();

        log("initializing for %s %s", browserType, browserVersion);

        if (StringUtils.isNotBlank(browserVersion)) {
            String temp = StringUtils.substringBefore(StringUtils.substringBefore(browserVersion, "."), " ");
            majorVersion = NumberUtils.toInt(StringUtils.trim(temp));
        }

        setWindowSize(driver);
        setWindowPosition(driver);
    }

    private String resolveConfig(String propName, String defaultValue) {
        Map<String, String> config = context.getProfileConfig("web", profile);
        return config.getOrDefault(
            withProfile(profile, propName),
            System.getProperty(propName, StringUtils.defaultString(context.getStringData(propName), defaultValue)));
    }

    private boolean resolveConfig(String propName, boolean defaultValue) {
        Map<String, String> config = context.getProfileConfig("web", profile);
        return BooleanUtils.toBoolean(
            config.getOrDefault(withProfile(profile, propName),
                                System.getProperty(propName,
                                                   StringUtils.defaultString(context.getStringData(propName),
                                                                             defaultValue + ""))));
    }

    public String resolveConfig(String propName) { return resolveConfig(propName, null); }

    private void resolveBrowserProfile() {
        if (browserType == firefox) { browserProfile = resolveConfig(SELENIUM_FIREFOX_PROFILE); }
        if (browserType == chrome) { browserProfile = resolveConfig(OPT_CHROME_PROFILE); }
    }

    @NotNull
    private File resolveBrowserLogFile(String logFileName) {
        return new File(StringUtils.appendIfMissing(System.getProperty(TEST_LOG_PATH, JAVA_IO_TMPDIR), separator) +
                        logFileName);
    }

    private WebDriverHelper resolveChromeDriverLocation() throws IOException {
        WebDriverHelper helper = WebDriverHelper.newInstance(browserType, context);
        helper.setBrowserBinLocation(resolveBinLocation());
        File driver = helper.resolveDriver();

        String driverPath = driver.getAbsolutePath();
        context.setData(SELENIUM_CHROME_DRIVER, driverPath);
        System.setProperty(SELENIUM_CHROME_DRIVER, driverPath);
        return helper;
    }

    @NotNull
    private String resolveBinLocation() {
        String configuredPath;
        Map<String, List<String>> binaryLocations;
        switch (browserType) {
            case chrome:
            case chromeheadless:
                configuredPath = resolveConfig(SELENIUM_CHROME_BIN);
                binaryLocations = chromeBinLocations;
                break;

            case firefox:
            case firefoxheadless:
                configuredPath = resolveConfig(SELENIUM_FIREFOX_BIN);
                binaryLocations = firefoxBinLocations;
                break;

            case edgechrome:
                configuredPath = resolveConfig(SELENIUM_EDGE_BIN);
                binaryLocations = edgeBinLocations;
                break;
            default:
                return "";
        }

        if (StringUtils.isNotBlank(configuredPath)) {
            if (FileUtil.isFileExecutable(configuredPath)) { return configuredPath; }
            error("%s binary '%s' is not executable; search for alternative...", browserType, configuredPath);
        }

        List<String> possibleLocations = IS_OS_WINDOWS ? binaryLocations.get("windows") :
                                         IS_OS_MAC ? binaryLocations.get("mac") :
                                         IS_OS_LINUX ? binaryLocations.get("linux") :
                                         null;
        if (CollectionUtils.isEmpty(possibleLocations)) {
            throw new IllegalArgumentException("Unable to derive alternative " + browserType + " binary... ");
        }

        for (String location : possibleLocations) {
            if (FileUtil.isFileExecutable(location)) { return new File(location).getAbsolutePath(); }
        }

        throw new IllegalArgumentException("Unable to derive alternative " + browserType + " binary via " +
                                           TextUtils.toString(possibleLocations, ", ", null, null));
    }

    private void resolveIEDriverLocation() throws IOException {
        WebDriverHelper helper = WebDriverHelper.newInstance(ie, context);
        File driver = helper.resolveDriver();

        String ieDriverPath = driver.getAbsolutePath();
        context.setData(SELENIUM_IE_DRIVER, ieDriverPath);
        System.setProperty(SELENIUM_IE_DRIVER, ieDriverPath);
    }

    private void syncContextPropToSystem(String prop) {
        String key = withProfile(profile, prop);
        if (System.getProperty(key) == null) {
            if (context.hasData(key)) { System.setProperty(key, context.getStringConfig("web", profile, key)); }
        }
    }

    /** log prefix will be printed in the beginning of a log statement to aid in readability */
    @Nonnull
    private String resolveLogPrefix() {
        String prefix = browserType == null ? "" : browserType.toString();
        if (!StringUtils.startsWith(profile, NAMESPACE) &&
            StringUtils.isNotBlank(profile) &&
            !StringUtils.equals(profile, CMD_PROFILE_DEFAULT)) {
            prefix += (StringUtils.isEmpty(prefix) ? "" : "@") + profile;
        }
        return prefix;
    }

    private boolean shouldLog() { return !StringUtils.startsWith(profile, NAMESPACE); }

    private void debug(String message, Object... args) {
        if (shouldLog() && DEBUG) {
            String prefix = resolveLogPrefix();
            if (StringUtils.isBlank(prefix)) {
                ConsoleUtils.log(String.format(message, args));
            } else {
                ConsoleUtils.log(prefix, message, args);
            }
        }
    }

    private void log(String message, Object... args) {
        if (shouldLog()) {
            String prefix = resolveLogPrefix();
            if (StringUtils.isBlank(prefix)) {
                ConsoleUtils.log(String.format(message, args));
            } else {
                ConsoleUtils.log(prefix, message, args);
            }
        }
    }

    private void error(String message, Object... args) {
        if (shouldLog()) { ConsoleUtils.error(resolveLogPrefix(), message, args); }
    }
}
