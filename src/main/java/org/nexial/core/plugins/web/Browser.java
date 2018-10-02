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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.WordUtils;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.NexialConst.*;
import org.nexial.core.ShutdownAdvisor;
import org.nexial.core.WebProxy;
import org.nexial.core.browsermob.ProxyHandler;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.plugins.external.ExternalCommand;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService.Builder;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static java.io.File.separator;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.core.NexialConst.BrowserStack.*;
import static org.nexial.core.NexialConst.BrowserType.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.utils.CheckUtils.requiresExecutableFile;
import static org.openqa.selenium.PageLoadStrategy.EAGER;
import static org.openqa.selenium.UnexpectedAlertBehaviour.ACCEPT;
import static org.openqa.selenium.UnexpectedAlertBehaviour.IGNORE;
import static org.openqa.selenium.firefox.FirefoxDriver.MARIONETTE;
import static org.openqa.selenium.firefox.FirefoxDriverLogLevel.ERROR;
import static org.openqa.selenium.ie.InternetExplorerDriver.*;
import static org.openqa.selenium.remote.CapabilityType.*;

public class Browser implements ForcefulTerminate {
    private static final Logger LOGGER = LoggerFactory.getLogger(Browser.class);
    private static final int MAX_DISP_LENGTH = 25;
    private static final String REGEX_WINDOW_SIZE = "^[0-9]{2,4}x[0-9]{2,4}$";
    private static final int MIN_WIDTH_OR_HEIGHT = 100;
    private static final Point INITIAL_POSITION = new Point(0, 0);

    private static final String USERHOME = StringUtils.appendIfMissing(USER_HOME, separator);
    private static final List<String> POSSIBLE_NIX_CHROME_BIN_LOCATIONS = Arrays.asList(
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable",
        "/usr/bin/chrome",
        "/bin/google-chrome",
        "/bin/google-chrome-stable",
        "/bin/chrome",
        "/etc/alternatives/google-chrome",
        "/etc/alternatives/google-chrome-stable",
        "/etc/alternatives/chrome",
        "/usr/bin/chromium-browser");
    private static final List<String> POSSIBLE_OSX_CHROME_BIN_LOCATIONS = Arrays.asList(
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        USERHOME + "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        USERHOME + "/tools/Google Chrome.app/Contents/MacOS/Google Chrome");
    private static final List<String> POSSIBLE_WIN_CHROME_BIN_LOCATIONS = Arrays.asList(
        "C:\\Program Files (x86)\\Google\\Application\\chrome.exe",
        "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
        "C:\\Program Files\\Google\\Application\\chrome.exe",
        "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
        USERHOME + "AppData\\Local\\Google\\Chrome\\Application\\chrome.exe",
        USERHOME + "AppDataLocal\\Google\\Chrome\\chrome.exe",
        USERHOME + "Local Settings\\Application Data\\Google\\Chrome\\chrome.exe");

    protected ExecutionContext context;
    protected WebDriver driver;
    // todo: not ready for prime time
    protected ProxyHandler proxy;
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

    public void setContext(ExecutionContext context) { this.context = context; }

    public void setChromeOptions(List<String> chromeOptions) { this.chromeOptions = chromeOptions; }

    public void setFirefoxIntPrefs(Map<String, Integer> firefoxIntPrefs) { this.firefoxIntPrefs = firefoxIntPrefs;}

    public void setFirefoxBooleanPrefs(Map<String, Boolean> firefoxBooleanPrefs) {
        this.firefoxBooleanPrefs = firefoxBooleanPrefs;
    }

    public void setFirefoxStringPrefs(Map<String, String> firefoxStringPrefs) {
        this.firefoxStringPrefs = firefoxStringPrefs;
    }

    public void setFirefoxBinArgs(List<String> firefoxBinArgs) { this.firefoxBinArgs = firefoxBinArgs; }

    public void setProxy(ProxyHandler proxy) { this.proxy = proxy; }

    public ProxyHandler getProxyHandler() { return proxy; }

    public WebDriver getDriver() { return driver; }

    public boolean isRunFireFox() { return browserType == firefox; }

    public boolean isRunFirefoxHeadless() { return browserType == firefoxheadless; }

    public boolean isRunIE() { return browserType == ie; }

    public boolean isRunEdge() { return browserType == edge; }

    public boolean isRunChrome() { return browserType == chrome; }

    public boolean isRunChromeHeadless() { return browserType == chromeheadless; }

    public boolean isRunChromeEmbedded() { return browserType == chromeembedded; }

    public boolean isRunElectron() { return browserType == electron; }

    public boolean isRunSafari() { return browserType == safari; }

    public boolean isRunBrowserStack() { return browserType == browserstack; }

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

    @Override
    public String toString() {
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

    public String manifest() { return browserType.name() + " " + browserVersion; }

    @Override
    public boolean mustForcefullyTerminate() { return driver != null; }

    @Override
    public void forcefulTerminate() { if (driver != null) { shutdown(); } }

    public boolean favorJSClick() { return browserType.isJsEventFavored(); }

    public WebDriver ensureWebDriverReady() {
        boolean shouldInitialize = false;
        String browser = context.getBrowserType();

        if (driver != null) {
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("current browser type - " + browser); }

            if (browserType != BrowserType.valueOf(browser)) {
                // browser changed... reinitialize..
                LOGGER.warn("current browser type (" + browser + ") " +
                            "not compatible with target browser type (" + browserType.name() + "). Re-init webdriver");
                shutdown();
                shouldInitialize = true;
            } else {
                // double check that browser hasn't close down
                try {
                    String winHandle = driver.getWindowHandle();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("webdriver readiness check: current window handle=" + winHandle);
                    }
                    // everything's fine, moving on
                } catch (Throwable e) {
                    String error = e.getMessage();
                    if (StringUtils.contains(error, "unexpected end of stream on Connection") ||
                        StringUtils.contains(error, "caused connection abort: recv failed")) {
                        LOGGER.error("webdriver readiness check: " + error);
                    } else {
                        // something's wrong with current browser window or session, need to re-init
                        shouldInitialize = true;
                        LOGGER.error("webdriver readiness check: " + error + "\n" +
                                     "BROWSER MIGHT BE TERMINATED; RESTARTING...");
                    }
                }
            }
        } else {
            shouldInitialize = true;
            if (LOGGER.isInfoEnabled()) { LOGGER.info("No valid instance of webdriver, initializing..."); }
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
            long pollWaitMs = context.getPollWaitMs();
            boolean timeoutChangesEnabled = browserType.isTimeoutChangesEnabled();
            boolean shouldWaitImplicitly = timeoutChangesEnabled &&
                                           pollWaitMs > 0 &&
                                           !context.getBooleanData(WEB_ALWAYS_WAIT, DEF_WEB_ALWAYS_WAIT);
            if (shouldWaitImplicitly) {
                timeouts.implicitlyWait(pollWaitMs, MILLISECONDS);
                ConsoleUtils.log("setting browser polling wait time to " + pollWaitMs + " ms");
            }

            if (timeoutChangesEnabled) {
                int pageLoadTimeout = context.getIntData(OPT_WEB_PAGE_LOAD_WAIT_MS, DEF_WEB_PAGE_LOAD_WAIT_MS);
                timeouts.pageLoadTimeout(pageLoadTimeout, MILLISECONDS);
                ConsoleUtils.log("setting browser page load timeout to " + pageLoadTimeout + " ms");
            }
        }

        if (StringUtils.isBlank(initialWinHandle)) {
            initialWinHandle = driver.getWindowHandle();
            lastWinHandles.clear();
            lastWinHandles.push(initialWinHandle);
        }

        if (LOGGER.isDebugEnabled()) { LOGGER.debug("webdriver ready for " + browser); }
        return driver;
    }

    public void init() {
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
        if (LOGGER.isInfoEnabled()) { LOGGER.info("init " + browserType); }

        // now we need to "remember" the browser type (even if it's default) so that the #data tab of output file will
        // display the browser type used during execution
        if (!context.hasData(BROWSER)) { context.setData(BROWSER, System.getProperty(BROWSER, DEF_BROWSER)); }

        try {
            if (isRunSafari()) { driver = initSafari(); }
            if (isRunChrome()) { driver = initChrome(false); }
            if (isRunChromeHeadless()) { driver = initChrome(true); }
            if (isRunChromeEmbedded()) { driver = initChromeEmbedded(); }
            if (isRunElectron()) { driver = initElectron(); }
            if (isRunIE()) { driver = initIE(); }
            if (isRunEdge()) { driver = initEdge(); }
            if (isRunFireFox()) { driver = initFirefox(false); }
            if (isRunFirefoxHeadless()) { driver = initFirefox(true); }
            if (isRunBrowserStack()) { driver = initBrowserStack(); }

            // NO LONGER SUPPORTED! ALL HAIL CHROME HEADLESS!!!
            // runPhantomJS = browserType == BrowserType.phantomjs;
            // if (runPhantomJS) { driver = initPhantomJS(); }

            if (driver != null) {
                if (LOGGER.isInfoEnabled()) { LOGGER.info("browser initialization completed for '" + browser + "'"); }
            } else {
                LOGGER.error("browser '" + browser + "' is not supported.");
            }
        } catch (Throwable e) {
            String msg = "Error initializing browser '" + browser + "': " + e.getMessage();
            ConsoleUtils.error(msg);
            throw new RuntimeException(msg, e);
        }
    }

    protected void shutdown() {
        // todo: not ready for prime time
        if (proxy != null) {
            proxy.stopProxy();
            proxy = null;
        }

        if (driver == null) {
            clearWinHandles();
            return;
        }

        // if (isRunFireFox() || isRunFirefoxHeadless() || driver instanceof FirefoxDriver) {
        //     // try { driver.close(); } catch (Throwable e) { }
        //     try { driver.quit(); } catch (Throwable e) { }
        // } else if (isRunSafari() || driver instanceof SafariDriver) {
        //     try { driver.close(); } catch (Throwable e) { }
        //     try { driver.quit(); } catch (Throwable e) { }
        // } else {
        //     try { driver.close(); } catch (Throwable e) { }
        //     try { driver.quit(); } catch (Throwable e) { }
        // }

        ConsoleUtils.log("Shutting down '" + browserType.name() + "' webdriver...");

        try { Thread.sleep(2000);} catch (InterruptedException e) { }

        if (!isRunFireFox() && !isRunFirefoxHeadless() && !(driver instanceof FirefoxDriver)) {
            // close before quite doesn't seem to work for firefox driver
            try { driver.close(); } catch (Throwable e) { }
        }

        try { driver.quit(); } catch (Throwable e) { }

        try { Thread.sleep(4000);} catch (InterruptedException e) { }

        clearWinHandles();
        driver = null;
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
            ConsoleUtils.error("Unable to resync window handles: " + e.getMessage());
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

    private void postInit(WebDriver driver) {
        String browserVersion = getBrowserVersion();
        if (LOGGER.isDebugEnabled()) { LOGGER.debug("post-init for " + browserType + " " + browserVersion); }
        if (StringUtils.isNotBlank(browserVersion)) {
            String temp = StringUtils.substringBefore(StringUtils.substringBefore(browserVersion, "."), " ");
            majorVersion = NumberUtils.toInt(StringUtils.trim(temp));
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("determined browser major version as " + majorVersion); }
        }
        setWindowSize(driver);
    }

    private String resolveConfig(String propName, String defaultValue) {
        return System.getProperty(propName, StringUtils.defaultString(context.getStringData(propName), defaultValue));
    }

    private boolean resolveConfig(String propName, boolean defaultValue) {
        return BooleanUtils.toBoolean(System.getProperty(propName,
                                                         StringUtils.defaultString(context.getStringData(propName),
                                                                                   defaultValue + "")));
    }

    private String resolveConfig(String propName) { return resolveConfig(propName, null); }

    private void resolveBrowserProfile() {
        if (browserType == firefox) { browserProfile = resolveConfig(SELENIUM_FIREFOX_PROFILE); }
        if (browserType == chrome) { browserProfile = resolveConfig(OPT_CHROME_PROFILE); }
    }

    private WebDriver initSafari() {
        // change location of safari's download location to "out" directory
        if (!IS_OS_MAC_OSX) {
            throw new RuntimeException("Browser automation for Safari is only supported on Mac OSX. Sorry...");
        }

        String out = context.getProject().getOutPath();
        try {
            ConsoleUtils.log(
                "modifying safari's download path: \n" +
                ExternalCommand.Companion.exec("defaults write com.apple.Safari DownloadsPath \"" + out + "\""));
        } catch (IOException e) {
            ConsoleUtils.error("Unable to modify safari's download path to " + out + ": " + e);
        }

        SafariOptions options = new SafariOptions();

        // Whether to make sure the session has no cookies, cache entries, local storage, or databases.
        options.setUseTechnologyPreview(context.getBooleanData(SAFARI_USE_TECH_PREVIEW, DEF_SAFARI_USE_TECH_PREVIEW));

        // todo: Create a SafariDriverService to specify what Safari flavour should be used and pass the service instance to a SafariDriver constructor.  When SafariDriver API updates to better code.. can't do this now
        SafariDriver safari = new SafariDriver(options);
        initCapabilities((MutableCapabilities) safari.getCapabilities());

        browserVersion = safari.getCapabilities().getVersion();
        browserPlatform = safari.getCapabilities().getPlatform();
        postInit(safari);
        return safari;
    }

    private WebDriver initChromeEmbedded() throws IOException {
        // ensure path specified for AUT app (where chromium is embedded)
        String clientLocation = context.getStringData(CEF_CLIENT_LOCATION);
        requiresExecutableFile(clientLocation);

        resolveChromeDriverLocation();

        ChromeOptions options = new ChromeOptions()
                                    .addArguments(this.chromeOptions)
                                    .setBinary(clientLocation);

        ChromeDriver chrome = new ChromeDriver(options);
        Capabilities capabilities = chrome.getCapabilities();
        initCapabilities((MutableCapabilities) capabilities);

        postInit(chrome);
        return chrome;
    }

    private WebDriver initElectron() throws IOException {
        // ensure path specified for AUT app (where electron app is)
        String clientLocation = context.getStringData(ELECTRON_CLIENT_LOCATION);
        requiresExecutableFile(clientLocation);

        resolveChromeDriverLocation();

        ChromeOptions options = new ChromeOptions().setBinary(clientLocation).setAcceptInsecureCerts(true);
        // options.addArguments("--disable-extensions"); // disabling extensions
        // options.addArguments("--disable-gpu"); // applicable to windows os only
        options.addArguments("--disable-dev-shm-usage"); // overcome limited resource problems
        options.addArguments("--no-sandbox"); // Bypass OS security model
        // options.addArguments("--headless");

        Builder cdsBuilder = new Builder();
        if (context.getBooleanData(LOG_ELECTRON_DRIVER, DEF_LOG_ELECTRON_DRIVER)) {
            // determine chrome log file
            String appName = clientLocation;
            if (StringUtils.contains(appName, "/")) { appName = StringUtils.substringAfterLast(appName, "/"); }
            if (StringUtils.contains(appName, "\\")) { appName = StringUtils.substringAfterLast(appName, "\\"); }
            if (StringUtils.contains(appName, ".")) { appName = StringUtils.substringBeforeLast(appName, "."); }
            cdsBuilder = cdsBuilder.withLogFile(resolveBrowserLogFile("chrome-" + appName + ".log"));
        }

        return new ChromeDriver(cdsBuilder.build(), options);
    }

    private WebDriver initChrome(boolean headless) throws IOException {
        // check https://github.com/SeleniumHQ/selenium/wiki/ChromeDriver for details

        resolveChromeDriverLocation();

        if (context.getBooleanData(LOG_CHROME_DRIVER, DEF_LOG_CHROME_DRIVER)) {
            String chromeLog = resolveBrowserLogFile("chrome-browser.log").getAbsolutePath();
            System.setProperty("webdriver.chrome.logfile", chromeLog);
            System.setProperty("webdriver.chrome.verboseLogging", "true");
        } else {
            System.setProperty("webdriver.chrome.verboseLogging", "false");
        }

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.setHeadless(true);
            options.addArguments("--no-sandbox");
        }

        options.addArguments(this.chromeOptions);
        if (context.getBooleanData(BROWER_INCOGNITO, DEF_BROWSER_INCOGNITO)) { options.addArguments("incognito"); }

        String binaryLocation = resolveChromeBinLocation();
        if (StringUtils.isNotBlank(binaryLocation)) { options.setBinary(binaryLocation); }

        if (context.getBooleanData(OPT_PROXY_REQUIRED)) {
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                options.addArguments("--proxy-server=http://" + localHost.getCanonicalHostName() + ":" + PROXY_PORT);
                options.addArguments("--proxy-bypass-list=127.0.0.1,localhost");
            } catch (UnknownHostException e) {
                throw new RuntimeException("Unable to determine localhost hostname: " + e.getMessage());
            }
        }

        ChromeDriver chrome = new ChromeDriver(options);
        Capabilities capabilities = chrome.getCapabilities();
        initCapabilities((MutableCapabilities) capabilities);

        Map<String, Object> prefs = new HashMap<>();
        //prefs.put("download.prompt_for_download", "true");
        prefs.put("profile.content_settings.pattern_pairs.*.multiple-automatic-downloads", "1");
        prefs.put("profile.content_settings.pattern_pairs.,.multiple-automatic-downloads", 1);
        prefs.put("multiple-automatic-downloads", "1");
        ((MutableCapabilities) capabilities).setCapability("chrome.prefs", prefs);

        browserVersion = capabilities.getVersion();
        browserPlatform = capabilities.getPlatform();
        postInit(chrome);
        // pageSourceSupported = false;

        return chrome;
    }

    @NotNull
    private File resolveBrowserLogFile(String logFileName) {
        return new File(StringUtils.appendIfMissing(System.getProperty(TEST_LOG_PATH, JAVA_IO_TMPDIR), separator) +
                        logFileName);
    }

    private void resolveChromeDriverLocation() throws IOException {
        WebDriverHelper helper = WebDriverHelper.Companion.newInstance(browserType, context);
        File driver = helper.resolveDriver();
        if (!driver.exists()) { throw new IOException("Can't resolve/download driver for " + browserType); }

        String driverPath = driver.getAbsolutePath();
        context.setData(SELENIUM_CHROME_DRIVER, driverPath);
        System.setProperty(SELENIUM_CHROME_DRIVER, driverPath);
    }

    private String resolveChromeBinLocation() {
        String configuredPath = resolveConfig(SELENIUM_CHROME_BIN);
        if (StringUtils.isNotBlank(configuredPath)) {
            if (FileUtil.isFileExecutable(configuredPath)) { return configuredPath; }
            ConsoleUtils.error("Configured Chrome binary '" + configuredPath + "' is not executable; " +
                               "search for alternative...");
        }

        List<String> possibleLocations = IS_OS_WINDOWS ? POSSIBLE_WIN_CHROME_BIN_LOCATIONS :
                                         IS_OS_MAC ? POSSIBLE_OSX_CHROME_BIN_LOCATIONS :
                                         IS_OS_LINUX ? POSSIBLE_NIX_CHROME_BIN_LOCATIONS : null;
        if (CollectionUtils.isEmpty(possibleLocations)) {
            ConsoleUtils.error("Unable to derive alternative Chrome binary... ");
            return null;
        }

        for (String location : possibleLocations) {
            if (FileUtil.isFileExecutable(location)) { return new File(location).getAbsolutePath(); }
        }

        ConsoleUtils.error("Unable to derive alternative Chrome binary... ");
        return null;
    }

    private WebDriver initIE() throws IOException {

        resolveIEDriverLocation();

        // check https://github.com/SeleniumHQ/selenium/wiki/InternetExplorerDriver for details
        System.setProperty(SELENIUM_IE_LOG_LEVEL, "WARN");
        syncContextPropToSystem(SELENIUM_IE_LOG_LEVEL);
        syncContextPropToSystem(SELENIUM_IE_DRIVER);
        syncContextPropToSystem(SELENIUM_IE_LOG_LOGFILE);
        syncContextPropToSystem(SELENIUM_IE_SILENT);
        boolean runWin64 = EnvUtils.isRunningWindows64bit();

        if (System.getProperty(SELENIUM_IE_DRIVER) == null) {
            runWin64 = EnvUtils.isRunningWindows64bit() && !resolveConfig(OPT_FORCE_IE_32, DEFAULT_FORCE_IE_32);
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

        if (context.getBooleanData(BROWSER_IE_REQUIRE_WINDOW_FOCUS, DEF_BROWSER_IE_REQUIRE_WINDOW_FOCUS)) {
            capabilities.requireWindowFocus();
        }

        // Capability that defines to use whether to use native or javascript events during operations.
        capabilities.setCapability(HAS_NATIVE_EVENTS, false);
        capabilities.setCapability(SUPPORTS_WEB_STORAGE, true);
        capabilities.setCapability(SUPPORTS_ALERTS, true);
        capabilities.setCapability(ACCEPT_SSL_CERTS, true);

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
        if (context.getBooleanData(BROWER_INCOGNITO, DEF_BROWSER_INCOGNITO)) {
            // capabilities.setCapability(FORCE_CREATE_PROCESS, true);
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
        browserVersion = ie.getCapabilities().getVersion();
        browserPlatform = ie.getCapabilities().getPlatform();
        postInit(ie);

        StringBuilder log = new StringBuilder("IEDriverServer capabilities:\n");
        ie.getCapabilities().asMap().forEach((key, val) -> log.append("\t")
                                                              .append(key).append("\t= ").append(val)
                                                              .append("\n"));
        log.append("\n");
        ConsoleUtils.log(log.toString());

        return ie;
    }

    private void resolveIEDriverLocation() throws IOException {
        WebDriverHelper helper = WebDriverHelper.Companion.newInstance(ie, context);
        File driver = helper.resolveDriver();
        if (!driver.exists()) { throw new IOException("Unable to resolve/ download driver for IE browser."); }
        String ieDriverPath = driver.getAbsolutePath();
        context.setData(SELENIUM_IE_DRIVER, ieDriverPath);
        System.setProperty(SELENIUM_IE_DRIVER, ieDriverPath);
    }

    private WebDriver initEdge() throws IOException {
        WebDriverHelper helper = WebDriverHelper.Companion.newInstance(edge, context);
        File driver = helper.resolveDriver();
        if (!driver.exists()) { throw new IOException("Can't resolve/download driver for " + edge); }

        String driverPath = driver.getAbsolutePath();
        context.setData(SELENIUM_EDGE_DRIVER, driverPath);
        System.setProperty(SELENIUM_EDGE_DRIVER, driverPath);

        EdgeDriver edge = new EdgeDriver();
        postInit(edge);

        Capabilities capabilities = edge.getCapabilities();
        browserVersion = capabilities.getVersion();
        browserPlatform = capabilities.getPlatform();
        pageSourceSupported = false;

        StringBuilder log = new StringBuilder("Edge WebDriver capabilities:\n");
        capabilities.asMap().forEach((key, val) -> log.append("\t").append(key).append("\t=").append(val).append("\n"));
        ConsoleUtils.log(log.toString() + "\n");

        return edge;
    }

    private void syncContextPropToSystem(String prop) {
        if (System.getProperty(prop) == null) {
            if (context.hasData(prop)) { System.setProperty(prop, context.getStringData(prop)); }
        }
    }

    private WebDriver initFirefox(boolean headless) throws IOException {
        BrowserType browserType = headless ? firefoxheadless : firefox;
        WebDriverHelper helper = WebDriverHelper.Companion.newInstance(browserType, context);
        File driver = helper.resolveDriver();
        if (!driver.exists()) { throw new IOException("Can't resolve/download driver for " + browserType); }

        String driverPath = driver.getAbsolutePath();
        context.setData(SELENIUM_GECKO_DRIVER, driverPath);
        System.setProperty(SELENIUM_GECKO_DRIVER, driverPath);
        context.setData(MARIONETTE, true);
        System.setProperty(MARIONETTE, "true");

        FirefoxOptions options;

        // todo: introduce auto-download feature
        //firefoxProfile.setPreference("browser.download.dir", SystemUtils.getJavaIoTmpDir().getAbsolutePath());

        // unrelated, added only to improve firefox-pdf perf.
        //firefoxProfile.setPreference("pdfjs.disabled", true);

        // options.setLogLevel(FirefoxDriverLogLevel.FATAL);

        try {
            DesiredCapabilities capabilities;

            // todo: not ready for prime time
            if (proxy != null) {
                Proxy localProxy = proxy.getServer().seleniumProxy();

                String localHost = InetAddress.getLocalHost().getHostName();
                localProxy.setHttpProxy(localHost);
                localProxy.setSslProxy(localHost);

                capabilities = new DesiredCapabilities();
                initCapabilities(capabilities);
                capabilities.setCapability(PROXY, localProxy);

                Properties browsermobProps = PropertiesLoaderUtils.loadAllProperties(
                    "org/nexial/core/plugins/har/browsermob.properties");
                int proxyPort = Integer.parseInt(browsermobProps.getProperty("browsermob.port"));

                options = new FirefoxOptions(capabilities);
                options.addPreference("network.proxy.type", 1);
                options.addPreference("network.proxy.http_port", proxyPort);
                options.addPreference("network.proxy.ssl_port", proxyPort);
                options.addPreference("network.proxy.no_proxies_on", "");

            } else {
                options = new FirefoxOptions();
                capabilities = new DesiredCapabilities();
                // capabilities = DesiredCapabilities.firefox();
                initCapabilities(capabilities);
                options.merge(capabilities);

                // options = new FirefoxOptions(capabilities);

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
            }

            if (headless) { options.setHeadless(true); }

            // merge configured prefs (spring) to `options` instance
            if (MapUtils.isNotEmpty(firefoxBooleanPrefs)) { firefoxBooleanPrefs.forEach(options::addPreference); }
            if (MapUtils.isNotEmpty(firefoxIntPrefs)) { firefoxIntPrefs.forEach(options::addPreference); }
            if (MapUtils.isNotEmpty(firefoxStringPrefs)) { firefoxStringPrefs.forEach(options::addPreference); }
            if (CollectionUtils.isNotEmpty(firefoxBinArgs)) { firefoxBinArgs.forEach(options::addArguments); }

            boolean ignoreAlert = BooleanUtils.toBoolean(context.getBooleanData(OPT_ALERT_IGNORE_FLAG));
            options.setUnhandledPromptBehaviour(ignoreAlert ? IGNORE : ACCEPT);
            options.setAcceptInsecureCerts(true);
            options.setPageLoadStrategy(EAGER);
            options.setLogLevel(ERROR);

            FirefoxDriver firefox = new FirefoxDriver(options);

            browserVersion = capabilities.getVersion();
            browserPlatform = capabilities.getPlatform();

            postInit(firefox);
            return firefox;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private WebDriver initBrowserStack() {
        String username = context.getStringData(KEY_USERNAME);
        String automateKey = context.getStringData(KEY_AUTOMATEKEY);

        if (StringUtils.isBlank(username) || StringUtils.isBlank(automateKey)) {
            throw new RuntimeException("Both " + KEY_USERNAME + " and " + KEY_AUTOMATEKEY +
                                       " are required to use BrowserStack");
        }

        MutableCapabilities capabilities = new MutableCapabilities();
        initCapabilities(capabilities);

        capabilities.setCapability("browserstack.local", context.getBooleanData(KEY_ENABLE_LOCAL, DEF_ENABLE_LOCAL));

        String browserName = context.getStringData(KEY_BROWSER);
        String bsBrowser = StringUtils.length(browserName) < 3 ?
                           StringUtils.upperCase(browserName) : WordUtils.capitalize(browserName);

        setCapability(capabilities, "browserName", StringUtils.lowerCase(browserName));
        setCapability(capabilities, "browser", bsBrowser);
        setCapability(capabilities, "browser_version", context.getStringData(KEY_BROWSER_VER));
        setCapability(capabilities, "browserstack.debug", context.getBooleanData(KEY_DEBUG, DEF_DEBUG));
        setCapability(capabilities, "resolution", context.getStringData(KEY_RESOLUTION));
        setCapability(capabilities, "build", context.getStringData(KEY_BUILD_NUM));
        if (context.hasData(KEY_CAPTURE_CRASH)) {
            setCapability(capabilities, "browserstack.captureCrash", context.getBooleanData(KEY_CAPTURE_CRASH));
        }

        ExecutionDefinition execDef = context.getExecDef();
        if (execDef != null) {
            if (execDef.getProject() != null && StringUtils.isNotBlank(execDef.getProject().getName())) {
                setCapability(capabilities, "project", execDef.getProject().getName());
            }

            if (StringUtils.isNotBlank(execDef.getTestScript())) {
                String scriptName = StringUtils.substringAfterLast(
                    StringUtils.replace(execDef.getTestScript(), "\\", "/"), "/");
                setCapability(capabilities, "name", scriptName);
            }
        }

        // Timezone tz = Timezone.byName(TimeZone.getDefault().toZoneId().getId());
        // setCapability(capabilities, "browserstack.timezone", tz.name());

        String targetOs = context.getStringData(KEY_OS);
        String targetOsVer = context.getStringData(KEY_OS_VER);
        if (StringUtils.isNotBlank(targetOs) && StringUtils.isNotBlank(targetOsVer)) {
            setCapability(capabilities, "os", StringUtils.upperCase(targetOs));
            setCapability(capabilities, "os_version", targetOsVer);
        } else {
            // if no target OS specified, then we'll just stick to automation host's OS
            if (IS_OS_WINDOWS) {
                setCapability(capabilities, "os", "WINDOWS");
                if (IS_OS_WINDOWS_7) {
                    setCapability(capabilities, "platform", "WIN7");
                    setCapability(capabilities, "os_version", "7");
                }
                if (IS_OS_WINDOWS_8) {
                    setCapability(capabilities, "platform", "WIN8");
                    setCapability(capabilities, "os_version", "8");
                }
                if (IS_OS_WINDOWS_10) {
                    setCapability(capabilities, "platform", "WIN10");
                    setCapability(capabilities, "os_version", "10");
                }
                if (IS_OS_WINDOWS_2008) {
                    setCapability(capabilities, "platform", "WWINOWS");
                    setCapability(capabilities, "os_version", "2008");
                }
            }
            if (IS_OS_MAC_OSX) {
                setCapability(capabilities, "os", "OS X");
                setCapability(capabilities, "platform", "MAC");
                if (IS_OS_MAC_OSX_SNOW_LEOPARD) { setCapability(capabilities, "os_version", "Snow Leopard"); }
                if (IS_OS_MAC_OSX_LION) { setCapability(capabilities, "os_version", "Lion"); }
                if (IS_OS_MAC_OSX_MOUNTAIN_LION) { setCapability(capabilities, "os_version", "Mountain Lion"); }
                if (IS_OS_MAC_OSX_MAVERICKS) { setCapability(capabilities, "os_version", "Mavericks"); }
                if (IS_OS_MAC_OSX_YOSEMITE) { setCapability(capabilities, "os_version", "Yosemite"); }
                if (IS_OS_MAC_OSX_EL_CAPITAN) { setCapability(capabilities, "os_version", "El Capitan"); }
            }
        }

        // support any existing or new browserstack.* configs
        Map<String, String> browserstackConfig = context.getDataByPrefix("browserstack.");
        if (MapUtils.isNotEmpty(browserstackConfig)) {
            browserstackConfig.forEach((key, value) -> setCapability(capabilities, "browserstack." + key, value));
        }

        try {
            RemoteWebDriver driver =
                new RemoteWebDriver(new URL(BASE_PROTOCOL + username + ":" + automateKey + BASE_URL), capabilities);
            postInit(driver);
            return driver;
        } catch (MalformedURLException | WebDriverException e) {
            throw new RuntimeException("Unable to initialize BrowserStack session: " + e.getMessage(), e);
        }
    }

    private void setCapability(MutableCapabilities capabilities, String key, String config) {
        if (StringUtils.isNotBlank(config)) { capabilities.setCapability(key, config); }
    }

    private void setCapability(MutableCapabilities capabilities, String key, boolean config) {
        capabilities.setCapability(key, config);
    }

    private void initCapabilities(MutableCapabilities capabilities) {
        // if true then we tell firefox not to auto-close js alert diaglog
        boolean ignoreAlert = BooleanUtils.toBoolean(context.getBooleanData(OPT_ALERT_IGNORE_FLAG));
        capabilities.setCapability(UNEXPECTED_ALERT_BEHAVIOUR, ignoreAlert ? IGNORE : ACCEPT);
        capabilities.setCapability(SUPPORTS_ALERTS, true);
        capabilities.setCapability(SUPPORTS_WEB_STORAGE, true);
        capabilities.setCapability(HAS_NATIVE_EVENTS, true);
        capabilities.setCapability(SUPPORTS_LOCATION_CONTEXT, false);
        capabilities.setCapability(ACCEPT_SSL_CERTS, true);

        // --------------------------------------------------------------------
        // Proxy
        // --------------------------------------------------------------------
        // When a proxy is specified using the proxy capability, this capability sets the proxy settings on
        // a per-process basis when set to true. The default is false, which means the proxy capability will
        // set the system proxy, which IE will use.
        //capabilities.setCapability("ie.setProxyByServer", true);
        capabilities.setCapability("honorSystemProxy", false);

        // todo: not ready for prime time
        if (context.getBooleanData(OPT_PROXY_REQUIRED, false) && proxy == null) {
            ConsoleUtils.log("setting proxy server for webdriver");
            capabilities.setCapability(PROXY, WebProxy.getSeleniumProxy());
        }

        if (context.getBooleanData(OPT_PROXY_DIRECT, false)) {
            ConsoleUtils.log("setting direct connection for webdriver");
            capabilities.setCapability(PROXY, WebProxy.getDirect());
        }
    }

    private void setWindowSize(WebDriver driver) {
        // not suitable for cef
        if (isRunChromeEmbedded() || isRunElectron()) { return; }

        String windowSize = context.getStringData(BROWSER_WINDOW_SIZE);
        if ((isRunChromeHeadless() || isRunFirefoxHeadless()) && StringUtils.isBlank(windowSize)) {
            // window size required for headless browser
            windowSize = context.getStringData(BROWSER_DEFAULT_WINDOW_SIZE);
            ConsoleUtils.log("No '" + BROWSER_WINDOW_SIZE + "' defined for headless browser; default to " + windowSize);
        }

        if (StringUtils.isNotEmpty(windowSize)) {
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
            window.setPosition(INITIAL_POSITION);
        }
    }
}
