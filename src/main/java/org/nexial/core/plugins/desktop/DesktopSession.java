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

package org.nexial.core.plugins.desktop;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.nexial.commons.proc.RuntimeUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.XmlUtils;
import org.nexial.core.plugins.xml.XmlCommand;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.winium.WiniumDriver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.SystemVariables.getDefaultInt;
import static org.nexial.core.plugins.desktop.DesktopConst.*;
import static org.nexial.core.plugins.desktop.ElementType.WINDOW;

/**
 * The ephemeral data structure to track current state of a desktop application, particularly when navigating between
 * different containers within one application.
 * <p>
 * In order to support multiple applications within one execution, the concept of the "active", "declared",
 * "background" applications are introduced:<ul>
 * <li>active - the application currently being in used and referenced.  </li>
 * </ul>
 */
public class DesktopSession {
    // public static final int MAX_APPS = 2;
    // private static final Map<String, DesktopApplication> APPS_IN_SESSION = new HashMap<>();

    private static final Map<String, List<Integer>> RUNNING_INSTANCES = new HashMap<>();
    private static final Map<String, DesktopSession> SESSIONS = new HashMap<>();

    protected String appId;
    protected DesktopConfig config;
    protected String exeFullPath;
    protected WiniumDriver driver;
    protected DesktopElement app;
    protected String processId;
    protected String applicationVersion;
    protected Map<String, ThirdPartyComponent> thirdPartyComponents = new HashMap<>();

    DesktopSession() { }

    /**
     * see {@link #newInstance(String, String)}
     */
    public static DesktopSession newInstance(String appId) throws IOException { return newInstance(appId, null); }

    /**
     * load a {@link DesktopElement} based on {@code appId}.  This {@code appId} references a JSON file, which
     * in turns contains all the necessary details to traverse through an application.  Some of the navigation details
     * will be derived along the way, as the app is being in used - some UI elements are dynamically created.
     * <p>
     * The JSON file can be loaded via classpath resource or via Nexial property,
     * {@link DesktopConst#OPT_CONFIG_LOCATION_PREFIX} (latter given precedence).
     */
    public static DesktopSession newInstance(String appId, String configLocation) throws IOException {
        if (StringUtils.isBlank(appId)) { throw new IllegalArgumentException("appId is missing!"); }

        // load config
        DesktopConfig config = StringUtils.isNotBlank(configLocation) ?
                               DesktopConfig.parseJson(configLocation) : DesktopConfig.parseJsonByAppId(appId);
        if (config == null) {
            throw new IllegalArgumentException("config cannot be derived; appId=" + appId +
                                               ", configLocation=" + configLocation);
        }
        if (StringUtils.isNotBlank(configLocation)) { config.setAppId(appId); }

        DesktopSession session = new DesktopSession();
        session.appId = appId;
        session.config = config;

        // reload cache
        boolean loadFromCache = session.reloadFromCache();

        // derive application instance
        runAut(session);

        WiniumDriver driver = session.getDriver();
        if (loadFromCache) {
            DesktopElement app = session.getApp();
            app.setDriver(driver);
            app.setElement(waitForElement(driver, app.getXpath(), config.getAppStartupWaitMs()));

            // rescan cached components for (1) 3rd party elements, and (2) standard elements like titlebar, menubar..
            reloadCachedComponents(app);
            session.collect3rdPartyComponents();
        } else {
            DesktopElement app = config.getApp();
            // app.setConfig(config);
            app.inheritXPathGenerationStrategy(config);
            app.setDriver(driver);
            app.setElement(waitForElement(driver, app.getXpath(), config.getAppStartupWaitMs()));
            app.resolveInputType();
            app.collectLabelOverrides();

            session.app = app;
            session.inspectCommonComponents();
            session.collect3rdPartyComponents();
            session.saveAppMetadata();
        }

        session.saveProcessId();
        session.saveApplicationVersion();
        SESSIONS.put(session.exeFullPath, session);

        return session;
    }

    public static void terminateRunningInstances(String exeName) {
        if (StringUtils.isBlank(exeName)) { return; }

        List<Integer> remainingProcId = new ArrayList<>();

        // remove known procs
        List<Integer> runningProcessIds = RUNNING_INSTANCES.remove(exeName);
        if (runningProcessIds == null) { runningProcessIds = new ArrayList<>(); }

        // find other procs not registered
        List<Integer> runningProcs = RuntimeUtils.findRunningInstances(exeName);
        if (CollectionUtils.isNotEmpty(runningProcs)) { runningProcessIds.addAll(runningProcs); }

        if (CollectionUtils.isNotEmpty(runningProcessIds)) {
            for (Integer prodId : runningProcessIds) {
                if (RuntimeUtils.terminateInstance(prodId)) {
                    ConsoleUtils.log("terminated " + exeName + " with process id " + prodId);
                } else {
                    remainingProcId.add(prodId);
                }
            }
        }

        if (CollectionUtils.isNotEmpty(remainingProcId)) { RUNNING_INSTANCES.put(exeName, remainingProcId); }

        try { Thread.sleep(2000); } catch (InterruptedException e) { }
    }

    public String getAppId() { return appId; }

    public DesktopConfig getConfig() { return config; }

    public DesktopElement getApp() { return app; }

    public String getExeFullPath() { return exeFullPath; }

    public WiniumDriver getDriver() { return driver; }

    public Map<String, ThirdPartyComponent> getThirdPartyComponents() { return thirdPartyComponents; }

    public String getProcessId() { return processId; }

    public String getApplicationVersion() { return applicationVersion; }

    public DesktopDialog findDialogBox(By by) {
        try {
            WebElement element = driver.findElement(by);
            if (element == null) { return null; }

            String controlType = element.getAttribute("ControlType");
            if (!StringUtils.equals(controlType, WINDOW)) {
                ConsoleUtils.error("Found element with @ControlType as " + controlType +
                                   ", probably not a DialogBox. By=" + by);
                return null;
            }

            return new DesktopDialog(element);
        } catch (Exception e) {
            // no element found means no dialog
            return null;
        }
    }

    public DesktopDialog findDialogByContent(String containing) {
        By xpath = By.xpath("/*[@ControlType='" + WINDOW + "' and " +
                            "count(*[@ControlType='ControlType.Text' and contains(@Name,'" + containing + "')]) > 0]");
        return findDialogBox(xpath);
    }

    public void terminateSession() {
        List<Integer> runningProcIds = RUNNING_INSTANCES.remove(exeFullPath);
        if (CollectionUtils.isNotEmpty(runningProcIds)) { runningProcIds.forEach(RuntimeUtils::terminateInstance); }

        SESSIONS.remove(exeFullPath);

        try { Thread.sleep(2000); } catch (InterruptedException e) { }

        this.appId = null;
        this.exeFullPath = null;
        this.config = null;
        this.app = null;
        this.processId = null;
        this.applicationVersion = null;
        this.driver = null;
    }

    public WebElement getModalDialog() {
        String xpath = resolveModalDialogXpath();
        try {
            List<WebElement> dialogs = driver.findElements(By.xpath(xpath));
            return CollectionUtils.isNotEmpty(dialogs) ? dialogs.get(0) : null;
        } catch (WebDriverException e) {
            ConsoleUtils.error("Error accessing/processing modal dialog: " + e.getMessage());
            return null;
        }
    }

    public boolean isModalDialogExists() { return getModalDialog() != null; }

    public String clearModalDialog(String button) {
        if (StringUtils.isBlank(button)) { return null; }

        WebElement dialog = getModalDialog();
        if (dialog == null) { return null; }

        String text = collectModalDialogText();

        try {
            String buttonXpath = resolveModalDialogXpath() + StringUtils.replace(LOCATOR_BUTTON, "{button}", button);
            List<WebElement> matches = driver.findElements(By.xpath(buttonXpath));
            if (CollectionUtils.isEmpty(matches)) {
                ConsoleUtils.error("Unable to clear dialog since button '" + button + "' cannot be found");
                return null;
            }

            WebElement elemButton = matches.get(0);
            elemButton.click();
            return text;
        } catch (WebDriverException e) {
            ConsoleUtils.error("Error accessing/processing modal dialog: " + e.getMessage());
            return null;
        }
    }

    public String collectModalDialogText() {
        String xpathDialogText = resolveModalDialogXpath() + LOCATOR_DIALOG_TEXT;

        // copied from DesktopCommand.saveModalDialog()
        List<WebElement> textElements = driver.findElements(By.xpath(xpathDialogText));
        if (CollectionUtils.isEmpty(textElements)) { return null; }

        final StringBuilder buffer = new StringBuilder();
        textElements.forEach(textElement -> buffer.append(StringUtils.trim(textElement.getAttribute("Name")))
                                                  .append(lineSeparator()));
        return StringUtils.trim(buffer.toString());
    }

    protected String resolveModalDialogXpath() {
        return StringUtils.appendIfMissing(app.getXpath(), "/") +
               "*[@ControlType='" + WINDOW + "' and @IsModal='True']";
    }

    protected static void reloadCachedComponents(DesktopElement container) {
        if (container == null) { return; }

        Map<String, DesktopElement> components = container.getComponents();
        if (MapUtils.isEmpty(components)) { return; }

        components.forEach((label, component) -> {
            if (component == null || StringUtils.isBlank(component.getXpath())) {
                ConsoleUtils.error("found invalid component from cache: No XPATH found for '" + label + "'");
            } else {
                component.inheritXPathGenerationStrategy(container);
                component.inheritLayout(container);
                component.container = container;
                component.driver = container.driver;

                if (StringUtils.equals(label, APP_MENUBAR)) {
                    component = DesktopMenuBar.toInstance(component);
                    components.put(label, component);
                }
            }

            reloadCachedComponents(component);
        });
    }

    protected static void runAut(DesktopSession session) throws IOException {
        if (session == null) { throw new IllegalArgumentException("session is null"); }

        DesktopConfig config = session.getConfig();
        if (config == null) { throw new IllegalArgumentException("config is null"); }

        Aut aut = config.getAut();
        if (aut == null) { throw new IllegalArgumentException("aut is null"); }

        // fetch or instantiate winium driver
        // if driver is to be instantiated, then new AUT instance will be instantiated as well
        session.exeFullPath = StringUtils.appendIfMissing(aut.getPath(), separator) + aut.getExe();
        session.driver = WiniumUtils.newWiniumInstance(aut);
        updateImplicitWaitMs(config, session.getDriver());

        List<Integer> runningInstances = RuntimeUtils.findRunningInstances(aut.getExe());
        if (CollectionUtils.isNotEmpty(runningInstances)) {
            RUNNING_INSTANCES.computeIfAbsent(session.exeFullPath, list -> new ArrayList<>()).addAll(runningInstances);
        }
    }

    protected boolean reloadFromCache() {
        if (config == null || StringUtils.isBlank(config.getResourceBaseLocation())) { return false; }

        // cache file ALWAYS follows ${directory of application.json}/${appId}.[commons | ${form}].json
        String cachePath = config.getResourceBaseLocation() + separator + config.getAppId() + ".commons.json";

        // assume any cache file should be at least 2k
        if (!FileUtil.isFileReadable(cachePath, MIN_CACHE_FILE_SIZE)) {
            DesktopConst.debug("desktop cache " + cachePath + " is not readable or is too small to be useful.");
            return false;
        }

        FileReader cacheReader = null;
        try {
            cacheReader = new FileReader(cachePath);
            DesktopConfig cache = DesktopConfig.parseJson(cacheReader);
            DesktopElement app = cache.getApp();
            if (app == null || cache.getAut() == null) { return false; }

            cache.setResourceBaseLocation(config.getResourceBaseLocation());
            cache.setFileBasedResource(true);

            // this.config = cache;
            this.appId = cache.getAppId();

            // we want to only inherit the "common" components. The rest should come directly from application.json
            // (which is loaded into this.config)
            app.getComponents().forEach((label, component) -> config.getApp().getComponents().put(label, component));

            config.getApp().collectLabelOverrides();
            this.app = config.getApp();

            return true;
        } catch (FileNotFoundException e) {
            ConsoleUtils.error("Error parsing desktop cache " + cachePath + ": " + e.getMessage());
            return false;
        } finally {
            if (cacheReader != null) { try { cacheReader.close(); } catch (IOException e) { } }
        }
    }

    protected void saveContainerMetadata(DesktopElement container, String containerName) throws IOException {
        if (container == null) {
            ConsoleUtils.log("container is null");
            return;
        }

        // those marked as "unmatched"... these are hopeless now.  Let's re-label them back: i.e. use automationId as label
        containerName = fixUnmatchLabels(containerName, container);

        File cacheFile = writeToCache(GSON2.toJson(container, DesktopElement.class), containerName);
        if (cacheFile == null) {
            ConsoleUtils.error("Unable to save container metadata since derived file is null");
            return;
        }

        ConsoleUtils.log("persisted cache for '" + containerName + "' to " + cacheFile);
    }

    protected String fixUnmatchLabels(String label, DesktopElement container) {
        if (container == null) { return null; }

        String fixedLabel = label;
        if (StringUtils.startsWith(label, UNMATCHED_LABEL_PREFIX)) {
            fixedLabel =
                StringUtils.isNotBlank(container.getAutomationId()) ? container.getAutomationId() :
                StringUtils.isNotBlank(container.getName()) ? container.getName() :
                container.hasContainer() ? container.getContainer().getLabel() : label;
            container.setLabel(fixedLabel);
        }

        Map<String, DesktopElement> components = container.getComponents();
        if (MapUtils.isNotEmpty(components)) {
            String[] labels = components.keySet().toArray(new String[components.size()]);
            for (String componentLabel : labels) {
                DesktopElement component = components.remove(componentLabel);
                components.put(fixUnmatchLabels(componentLabel, component), component);
            }
        }

        return fixedLabel;
    }

    protected void saveAppMetadata() throws IOException {
        // make sure we only save the 'commons' components
        DesktopElement configApp = config.getApp();
        Map<String, DesktopElement> configComponents = configApp.getComponents();
        Map<String, DesktopElement> commons = new LinkedHashMap<>();
        configComponents.forEach((label, component) -> {
            if (COMMON_DESKTOP_COMPONENTS.contains(label)) {
                label = fixUnmatchLabels(label, component);
                commons.put(label, component);
            }
        });

        // switch to 'commons' component just for serialization
        configApp.components = commons;
        File cacheFile = writeToCache(GSON2.toJson(config), "commons");
        if (cacheFile == null) { return; }

        // swich back to the full components map
        configApp.components = configComponents;

        ConsoleUtils.log("persisted cache for config to " + cacheFile);
    }

    protected File writeToCache(String cache, String containerName) throws IOException {
        // cache file ALWAYS follows ${directory of application.json}/${appId}.[commons | ${form}].json
        File cacheFile = getCacheFile(getConfig(), containerName);
        if (cacheFile == null) {
            ConsoleUtils.error("Unable to save container metadata since derived file is null");
            return null;
        }

        FileUtils.writeStringToFile(cacheFile, cache, "UTF-8");
        return cacheFile;
    }

    protected static void updateImplicitWaitMs(DesktopConfig config, WiniumDriver driver) throws IOException {
        if (driver == null) { return; }
        if (config == null) { return; }
        // with explicit wait, we should set the implicit wait value on driver to something low, and let the
        // corresponding waiter do retry-and-wait until max wait ms is reached
        WiniumUtils.updateImplicitWaitMs(
            driver, config.isExplicitWait() ? getDefaultInt(EXPLICIT_WAIT_MS) : config.getDefaultWaitMs());
    }

    protected static WebElement waitForElement(WiniumDriver driver, String xpath, int waitMs) {
        if (driver == null) { return null; }
        if (StringUtils.isBlank(xpath)) { return null; }

        By by = By.xpath(xpath);
        // sleep in .25 second
        return new WebDriverWait(driver, waitMs / 1000, 250).until(webDriver -> {
            try {
                return webDriver.findElements(by).stream()
                                .filter(element -> element != null && element.isDisplayed())
                                .findFirst()
                                .orElseThrow(() -> new NoSuchElementException(
                                    String.format("Cannot locate an element via %s within %s", xpath, waitMs)));
            } catch (WebDriverException e) {
                ConsoleUtils.log(String.format("WebDriverException thrown by findElement(%s)", xpath));
                throw e;
            }
        });
    }

    protected boolean useExplicitWait() { return config.isExplicitWait(); }

    protected DesktopElement findContainer(String containerName, DesktopElement container) {
        if (StringUtils.isBlank(containerName)) { return null; }
        if (container == null) { return null; }

        String cachePath = StringUtils.appendIfMissing(getConfig().getResourceBaseLocation(), separator) +
                           getAppId() + "." + containerName + ".json";

        // assume any cache file should be at least 2k
        if (FileUtil.isFileReadable(cachePath, MIN_CACHE_FILE_SIZE)) {
            FileReader cacheReader = null;
            try {
                cacheReader = new FileReader(new File(cachePath));
                DesktopElement cacheElement = GSON2.fromJson(cacheReader, DesktopElement.class);
                if (cacheElement == null) { return null; }
                if (StringUtils.isBlank(cacheElement.getXpath())) { return null; }
                if (StringUtils.isBlank(cacheElement.getLabel())) { return null; }

                cacheElement.container = container;
                cacheElement.driver = container.driver;
                cacheElement.inheritXPathGenerationStrategy(container);
                cacheElement.inheritLayout(container);
                reloadCachedComponents(cacheElement);

                getApp().components.put(cacheElement.getLabel(), cacheElement);
                return cacheElement;
            } catch (FileNotFoundException e) {
                ConsoleUtils.error("Error parsing desktop cache " + cachePath + ": " + e.getMessage());
            } finally {
                if (cacheReader != null) { try { cacheReader.close(); } catch (IOException e) { } }
            }
        } else {
            DesktopConst.debug("desktop cache " + cachePath + " is not readable or is too small to be useful.");
        }

        DesktopConst.debug("scanning container '" + containerName + "'...");

        DesktopElement component = getApp().inspectComponent(containerName);
        if (component == null) {
            ConsoleUtils.error("No form named '" + containerName + "' for application '" + getAppId() + "'");
            return null;
        }

        try {
            saveContainerMetadata(component, containerName);
        } catch (IOException e) {
            ConsoleUtils.error("Error saving metadata for container '" + containerName + "': " + e.getMessage());
        }

        return component;
    }

    protected <T extends ThirdPartyComponent> T findThirdPartyComponent(Class<T> targetType) {
        if (targetType == null) { return null; }
        if (MapUtils.isEmpty(thirdPartyComponents)) { return null; }

        Set<String> componentLabels = thirdPartyComponents.keySet();
        for (String label : componentLabels) {
            ThirdPartyComponent component = thirdPartyComponents.get(label);
            if (component == null) { continue; }
            if (targetType.isAssignableFrom(component.getClass())) { return (T) component; }
        }

        return null;
    }

    protected ThirdPartyComponent findThirdPartyComponentByName(String name) {
        if (StringUtils.isBlank(name)) { return null; }
        if (MapUtils.isEmpty(thirdPartyComponents)) { return null; }
        return thirdPartyComponents.get(name);
    }

    protected void inspectCommonComponents() {
        if (app == null || MapUtils.isEmpty(app.getComponents())) { return; }
        app.inspectComponent(APP_TITLEBAR);
        app.inspectComponent(APP_TOOLBAR);
        app.inspectComponent(APP_MENUBAR);
        app.inspectComponent(APP_STATUSBAR);
    }

    protected void collect3rdPartyComponents() {
        // search for 3rd party UI component
        Map<String, DesktopElement> components = app.getComponents();
        Set<String> labels = components.keySet();
        for (String label : labels) {
            DesktopElement component = components.get(label);
            if (StringUtils.isBlank(component.componentTypeHint)) { continue; }

            Class matchingClass = ComponentScanStrategy.findMatchingClass(component.componentTypeHint);
            if (matchingClass == null) { continue; }

            component.label = label;
            try {
                ThirdPartyComponent thirdPartyComponent = init3rdPartyComponent(component, matchingClass);
                if (thirdPartyComponent == null) {
                    ConsoleUtils.error("Unable to resolve component " + label + ": null returned;");
                } else {
                    DesktopConst.debug("resolving " + label + " as an instance of " + thirdPartyComponent.getClass());
                    thirdPartyComponents.put(label, thirdPartyComponent);
                }
            } catch (Exception e) {
                ConsoleUtils.error("Error initialize 3rd-party component '" + label + "': " + e.getMessage());
            }
        }
    }

    protected ThirdPartyComponent init3rdPartyComponent(DesktopElement component, Class targetClass) {
        if (component == null || targetClass == null) { return null; }
        if (!ThirdPartyComponent.class.isAssignableFrom(targetClass)) { return null; }

        ThirdPartyComponent targetComponent = ThirdPartyComponent.loadConfig(appId, component.label, targetClass);
        if (targetComponent == null) { return null; }

        WiniumDriver driver = app.getDriver();
        if (driver == null) {
            ConsoleUtils.error("No winium driver found - UNABLE TO CONTINUE!");
            return null;
        }

        List<WebElement> matches = driver.findElements(By.xpath(component.getXpath()));
        WebElement element;
        if (CollectionUtils.isEmpty(matches)) {
            ConsoleUtils.log("Unable to find element matching to " + component.getXpath());
            element = null;
        } else {
            element = matches.get(0);
        }

        component.setDriver(driver);
        component.inheritXPathGenerationStrategy(app);
        component.inheritLayout(app);
        component.setElement(element);
        component.setContainer(app);

        targetComponent.setComponent(component);
        targetComponent.setConfig(config);
        return targetComponent;
    }

    protected void saveProcessId() {
        if (app == null) { return; }

        WebElement elementApp = app.getElement();
        String processId = elementApp.getAttribute("ProcessId");
        if (StringUtils.isBlank(processId)) { return; }

        if (StringUtils.contains(processId, " (")) { processId = StringUtils.substringBefore(processId, " ("); }
        this.processId = processId;
        ConsoleUtils.log("application is running (process id " + processId + ")");
    }

    protected void saveApplicationVersion() {
        if (config == null) { return; }

        Aut aut = config.getAut();
        if (StringUtils.isBlank(aut.getPath())) { return; }
        if (StringUtils.isBlank(aut.getDotnetConfig())) { return; }

        String dotNetConfig = aut.getPath() + separator + aut.getDotnetConfig();
        File dotNetConfigFile = new File(dotNetConfig);

        try {
            String configContent = FileUtils.readFileToString(dotNetConfigFile, DEF_CHARSET);
            if (StringUtils.isBlank(configContent)) {
                ConsoleUtils.error("Unable to read content from " + dotNetConfigFile);
                return;
            }

            configContent = XmlCommand.cleanXmlContent(configContent);

            Document doc;
            try {
                doc = XmlUtils.parse(configContent);
            } catch (JDOMException | IOException e) {
                ConsoleUtils.log("invalid/malformed xml: " + e.getMessage());
                return;
            }

            String appVersion = XmlCommand.getValueByXPath(doc, XPATH_APP_VER);
            this.applicationVersion = appVersion;
            ConsoleUtils.log("application is running as version " + appVersion);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to parse AUT donet config file (" + dotNetConfigFile + "): " + e.getMessage());
        }
    }

    protected static File getCacheFile(DesktopConfig config, String containerName) {
        if (config == null || StringUtils.isBlank(config.getResourceBaseLocation())) {
            DesktopConst.debug("config is null");
            return null;
        }

        if (StringUtils.isEmpty(containerName)) {
            DesktopConst.debug("container name is null/empty");
            return null;
        }

        // todo: we need to remove legacy cache logic
        String cacheFileName = config.isFileBasedResource() ?
                               config.getResourceBaseLocation() + separator + config.getAppId() + "." + containerName :
                               DEF_CONFIG_HOME + separator + config.getAppId() + separator + config.getAppId();
        return new File(cacheFileName + ".json");
    }
}
