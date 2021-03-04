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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.NexialConst.PolyMatcher;
import org.nexial.core.ShutdownAdvisor;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.TestStep;
import org.nexial.core.plugins.CanLogExternally;
import org.nexial.core.plugins.CanTakeScreenshot;
import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.plugins.base.NumberCommand;
import org.nexial.core.plugins.base.ScreenshotUtils;
import org.nexial.core.plugins.desktop.DesktopTable.TableMetaData;
import org.nexial.core.plugins.desktop.ig.IgExplorerBar;
import org.nexial.core.plugins.desktop.ig.IgRibbon;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.NativeInputHelper;
import org.nexial.core.utils.OutputFileUtils;
import org.nexial.seeknow.SeeknowData;
import org.openqa.selenium.*;
import org.openqa.selenium.By.ByClassName;
import org.openqa.selenium.By.ById;
import org.openqa.selenium.By.ByName;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.By.ByXPath;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.winium.WiniumDriver;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static java.io.File.separator;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.core.NexialConst.Data.BUILD_NO;
import static org.nexial.core.NexialConst.Data.SCRIPT_REF_PREFIX;
import static org.nexial.core.NexialConst.Desktop.*;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.NexialConst.OS;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.SystemVariables.getDefaultInt;
import static org.nexial.core.plugins.desktop.DesktopConst.*;
import static org.nexial.core.plugins.desktop.DesktopNotification.NotificationLevel.info;
import static org.nexial.core.plugins.desktop.DesktopUtils.toShortcuts;
import static org.nexial.core.plugins.desktop.DesktopUtils.treatQuoteString;
import static org.nexial.core.plugins.desktop.ElementType.*;
import static org.nexial.core.plugins.web.WebDriverExceptionHelper.resolveErrorMessage;
import static org.nexial.core.utils.CheckUtils.*;

public class DesktopCommand extends BaseCommand implements ForcefulTerminate, CanTakeScreenshot, CanLogExternally {
    protected static final Map<String, Class<? extends By>> SUPPORTED_FIND_BY = initSupportedFindBys();
    protected transient WiniumDriver winiumDriver;
    protected transient NumberCommand numberCommand;

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        ShutdownAdvisor.addAdvisor(this);
        numberCommand = (NumberCommand) context.findPlugin("number");
        // only set once during init; no support for per-scanning flexibility.
        AUTOSCAN_DEBUG = context.getBooleanData(OPT_AUTOSCAN_VERBOSE, getDefaultBool(OPT_AUTOSCAN_VERBOSE));
    }

    @Override
    public String getTarget() { return "desktop"; }

    @Override
    public boolean mustForcefullyTerminate() {
        return IS_OS_WINDOWS &&
               winiumDriver != null &&
               getDriver() != null &&
               context.getBooleanData(WINIUM_SERVICE_RUNNING);
    }

    @Override
    public void forcefulTerminate() {
        DesktopSession session = getCurrentSession();
        if (session != null && session.getConfig() != null) {
            Aut aut = session.getConfig().getAut();
            if (aut != null && aut.isTerminateExisting()) { closeApplication(); }
        }

        if (mustForcefullyTerminate()) {
            WiniumUtils.shutdownWinium(null, winiumDriver);
            winiumDriver = null;
        }
    }

    @Override
    public String takeScreenshot(TestStep testStep) {
        if (testStep == null) { return null; }

        String filename = generateScreenshotFilename(testStep);
        if (StringUtils.isBlank(filename)) {
            error("Unable to generate screen capture filename!");
            return null;
        }
        filename = context.getProject().getScreenCaptureDir() + separator + filename;

        File file;
        if (!IS_OS_WINDOWS || winiumDriver == null) {
            log("using native screen capturing approach...");
            file = new File(filename);
            if (!NativeInputHelper.captureScreen(0, 0, -1, -1, file)) {
                error("Unable to capture screenshot via native screen capturing approach");
                return null;
            }
        } else {
            WebElement application = getApp();
            if (application == null) {
                error("target application not available or not yet initialized");
                return null;
            }

            List<String> dimension = null;
            if (!context.getBooleanData(DESKTOP_SCREENSHOT_FULLSCREEN, DEF_DESKTOP_SCREENSHOT_FULLSCREEN)) {
                dimension = TextUtils.toList(application.getAttribute(ATTR_BOUNDING_RECTANGLE), ",", true);
            }

            file = screenshot(filename, dimension);
        }

        return postScreenshot(testStep, file);
    }

    protected File screenshot(String targetFile, List<String> dimension) {
        Rectangle rect = null;
        if (CollectionUtils.size(dimension) == 4) {
            rect = new Rectangle(NumberUtils.toInt(dimension.get(0)),
                                 NumberUtils.toInt(dimension.get(1)),
                                 NumberUtils.toInt(dimension.get(3)),
                                 NumberUtils.toInt(dimension.get(2)));
        }

        // also generate `OPT_LAST_SCREENSHOT_NAME` var in context
        File imageFile = ScreenshotUtils.saveScreenshot(getDriver(), targetFile, rect);
        if (imageFile == null) {
            error("Unable to capture screenshot via Winium driver");
            return null;
        }

        return imageFile;
    }

    @Override
    public String generateScreenshotFilename(TestStep testStep) {
        return OutputFileUtils.generateScreenCaptureFilename(testStep);
    }

    @Override
    public void logExternally(TestStep testStep, String message) {
        // favor DESKTOP_NOTIFY_WAITMS2 over DESKTOP_NOTIFY_WAITMS (deprecated)
        int waitMs = context.getIntData(NOTIFY_WAITMS,
                                        context.getIntData(DESKTOP_NOTIFY_WAITMS,
                                                           getDefaultInt(DESKTOP_NOTIFY_WAITMS)));

        Worksheet worksheet = testStep.getWorksheet();
        String msg = "[" + worksheet.getName() + "][ROW " + (testStep.getRow().get(0).getRowIndex() + 1) + "]" + NL +
                     message;
        DesktopNotification.notify(info, msg, waitMs);
    }

    public StepResult useApp(String appId) {
        requiresNotBlank(appId, "Invalid app id", appId);

        // in case the current session matched
        DesktopSession session = getCurrentSession();
        if (session != null && StringUtils.equals(session.getAppId(), appId)) {
            syncSessionDataToContext(session);
            return StepResult.success("Reusing existing sessions for application " + appId);
        }

        // in case this appId is mapped to a location override
        String locationOverride = context.getStringData(OPT_CONFIG_LOCATION_PREFIX + appId);
        String configLocation =
            StringUtils.isNotBlank(locationOverride) ?
            locationOverride :
            context.getProject().getDataPath() + DEF_CONFIG_HOME + appId + separator + DEF_CONFIG_FILENAME;

        try {
            session = DesktopSession.newInstance(appId, configLocation);
            syncSessionDataToContext(session);
            winiumDriver = session.getDriver();
        } catch (IOException e) {
            return StepResult.fail("Error loading application " + appId + ": " + e.getMessage(), e);
        }

        return StepResult.success("Application '" + appId + "' loaded and inspected");
    }

    public StepResult useForm(String formName) {
        requiresNotBlank(formName, "Invalid form name", formName);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        // ** This section is commented out to eliminated Reusing form during the session.
        // ** New instance of the form container will be called from the current session
        // ** Modified on Dt: 07/07/2017

        //		DesktopElement container = getCurrentContainer();
        //		if (container != null && StringUtils.equals(container.getLabel(), formName)) {
        //			syncContainerDataToContext(container, formName);
        //			return StepResult.success("Reusing existing form '" + formName + "' for '" + session.getAppId() + "'");
        //		}

        DesktopElement container = session.findContainer(formName, session.getApp());
        syncContainerDataToContext(container, formName);
        return StepResult.success("Form '" + formName + "' of '" + session.getAppId() + "' loaded and inspected");
    }

    public StepResult login(String form, String username, String password) {
        requiresNotBlank(form, "Invalid container name for login form", form);
        requiresNotBlank(username, "Invalid username", username);
        requiresNotBlank(password, "Invalid password", password);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        LoginForm login = (LoginForm) session.findThirdPartyComponentByName(form);
        requiresNotNull(login, "No login component defined for application '" + session.getAppId() + "'");

        StepResult result = login.login(username, password);
        autoClearModalDialog(session);
        return result;
    }

    public StepResult clickExplorerBar(String group, String item) {
        requiresNotBlank(group, "Invalid group", group);
        requiresNotBlank(item, "Invalid item", item);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        IgExplorerBar explorerBar = session.findThirdPartyComponent(IgExplorerBar.class);
        requiresNotNull(explorerBar, "EXPECTED explorer bar component NOT specified/loaded");

        StepResult result = explorerBar.click(group, item);
        autoClearModalDialog(session);

        if (result.failed()) { return result; }

        // todo: best implement as event/observable pattern so that ribbon class can handle the event of explorer bar being clicked
        IgRibbon ribbon = session.findThirdPartyComponent(IgRibbon.class);
        if (ribbon != null) { ribbon.updateCurrentModule(group + NESTED_CONTAINER_SEP + item); }

        return result;
    }

    public StepResult toggleExplorerBar() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        IgExplorerBar explorerBar = session.findThirdPartyComponent(IgExplorerBar.class);
        requiresNotNull(explorerBar, "EXPECTED explorer bar component NOT specified/loaded");

        return explorerBar.isCollapsed() ? explorerBar.expand() : explorerBar.collapse();
    }

    public StepResult showExplorerBar() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        IgExplorerBar explorerBar = session.findThirdPartyComponent(IgExplorerBar.class);
        requiresNotNull(explorerBar, "EXPECTED explorer bar component NOT specified/loaded");

        return explorerBar.expand();
    }

    public StepResult hideExplorerBar() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        IgExplorerBar explorerBar = session.findThirdPartyComponent(IgExplorerBar.class);
        requiresNotNull(explorerBar, "EXPECTED explorer bar component NOT specified/loaded");

        return explorerBar.collapse();
    }

    public StepResult clickMenu(String menu) {
        requiresNotBlank(menu, "Invalid menu", menu);
        return resolveMenuBar().click(StringUtils.splitByWholeSeparator(menu, context.getTextDelim()));
    }

    public StepResult clickMenuByLocator(String locator, String menu) {
        requiresNotBlank(locator, "Invalid locator", locator);
        try {
            clickMenu(findElement(locator), menu);
            return StepResult.success("Successfully clicked on Menu " + menu + " on locator " + locator);
        } catch (IllegalArgumentException e) {
            return StepResult.fail(e.getMessage());
        }
    }

    public void clickMenu(WebElement elem, String menu) throws IllegalArgumentException {
        if (elem == null) { throw new IllegalArgumentException("element NOT found"); }
        requiresNotBlank(menu, "Invalid menu", menu);

        String warningSuffix = "likely unable to invoke application menu " + menu;

        String controlType = elem.getAttribute("ControlType");
        if (!StringUtils.equals(controlType, "ControlType.Window")) {
            log("[WARNING] target element does not resolve to a Window component; " + warningSuffix);
        }

        List<WebElement> menuBarObject = findElements(elem, "*[@ControlType=\"ControlType.MenuBar\"]");
        if (CollectionUtils.isEmpty(menuBarObject)) {
            log("[WARNING] No menu bar cannot be found under specified locator; " + warningSuffix);
            // try with just menu
            menuBarObject = findElements(elem, "*[@ControlType=\"ControlType.Menu\"]");
            // give up
            if (CollectionUtils.isEmpty(menuBarObject)) {
                throw new IllegalArgumentException("Unable to derive any Menu element under the target element");
            }
        }

        WebElement menuParent = menuBarObject.get(0);

        // move into position
        new Actions(winiumDriver).moveToElement(menuParent, 10, 10).perform();

        // split menu into its individual levels
        String[] menuItems = StringUtils.splitByWholeSeparator(menu, context.getTextDelim());
        for (int i = 0; i < menuItems.length; i++) {
            String item = menuItems[i];
            boolean useIndex = StringUtils.startsWith(item, CONTEXT_MENU_VIA_INDEX);
            if (useIndex) { item = StringUtils.trim(StringUtils.substringAfter(item, CONTEXT_MENU_VIA_INDEX)); }

            String xpath = i == 0 ? "" : "*[@ControlType='ControlType.Menu']/";
            if (useIndex) {
                if (!NumberUtils.isDigits(item)) {
                    throw new IllegalArgumentException("Invalid context menu index: " + item);
                }
                if (StringUtils.equals(item, "0")) {
                    throw new IllegalArgumentException("Invalid context menu index: " + item + ". Must be 1-based.");
                }

                xpath += "*[@ControlType='ControlType.MenuItem'][" + item + "]";
            } else {
                xpath += "*[@ControlType='ControlType.MenuItem' and @Name='" + item + "']";
            }

            WebElement menuItem = menuParent.findElement(By.xpath(xpath));
            if (menuItem == null) {
                throw new IllegalArgumentException("Unable to derive menu item " + item + " from target item");
            }

            new Actions(winiumDriver).moveToElement(menuItem, 10, 10).click(menuItem).perform();
            menuParent = menuItem;
        }
    }

    public StepResult contextMenu(String name, String menu) {
        requiresNotBlank(name, "Invalid name/label", name);
        DesktopElement component = getRequiredElement(name, Any);
        if (component == null) { return StepResult.fail("Element '" + name + "' NOT found"); }

        try {
            contextMenu(component.getElement(), menu);
            return StepResult.success("Successfully invoke context menu " + menu + " on '" + name + "'");
        } catch (IllegalArgumentException e) {
            return StepResult.fail(e.getMessage());
        }
    }

    public StepResult contextMenuByLocator(String locator, String menu) {
        requiresNotBlank(locator, "Invalid locator", locator);
        try {
            contextMenu(findElement(locator), menu);
            return StepResult.success("Successfully invoke context menu " + menu + " on locator " + locator);
        } catch (IllegalArgumentException e) {
            return StepResult.fail(e.getMessage());
        }
    }

    public void contextMenu(WebElement elem, String menu) throws IllegalArgumentException {
        if (elem == null) { throw new IllegalArgumentException("element NOT found"); }
        requiresNotBlank(menu, "Invalid menu", menu);

        // invoke context menu
        new Actions(winiumDriver).contextClick(elem).perform();

        String xpath = "";
        String previousMenuContainer = "Menu";
        String[] menuItems = StringUtils.split(menu, context.getTextDelim());
        for (String item : menuItems) {
            xpath += "/*[@ControlType='ControlType.Menu'";
            if (StringUtils.isNotBlank(previousMenuContainer)) { xpath += " and @Name='" + previousMenuContainer + "'";}
            xpath += "]";

            boolean useIndex = StringUtils.startsWith(item, CONTEXT_MENU_VIA_INDEX);
            if (useIndex) { item = StringUtils.trim(StringUtils.substringAfter(item, CONTEXT_MENU_VIA_INDEX)); }

            if (useIndex) {
                if (!NumberUtils.isDigits(item)) {
                    throw new IllegalArgumentException("Invalid context menu index: " + item);
                }
                if (StringUtils.equals(item, "0")) {
                    throw new IllegalArgumentException("Invalid context menu index: " + item + ". Must be 1-based.");
                }
                xpath += "/*[@ControlType='ControlType.MenuItem'][" + item + "]";
                previousMenuContainer = "";
            } else {
                xpath += "/*[@ControlType='ControlType.MenuItem' and @Name='" + item + "']";
                previousMenuContainer = item;
            }

            WebElement menuElement = findFirstElement(xpath);
            if (menuElement == null) {
                throw new IllegalArgumentException("Unable to derive menu item " + item + " from target element");
            }

            new Actions(winiumDriver).moveToElement(menuElement, 10, 10).click(menuElement).pause(750L).perform();
        }
    }

    public StepResult assertMenuEnabled(String menu) {
        requiresNotBlank(menu, "Invalid menu", menu);
        String[] menuItems = StringUtils.splitByWholeSeparator(menu, context.getTextDelim());

        DesktopMenuBar menuBar = resolveMenuBar();
        return menuBar.isEnabled(menuItems) ?
               StepResult.success("Menu '" + menu + "' is enabled as EXPECTED") :
               StepResult.fail("Menu '" + menu + "' is NOT enabled as EXPECTED");
    }

    public StepResult assertModalDialogPresent() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");
        return session.isModalDialogExists() ?
               StepResult.success("Modal dialog found") :
               StepResult.fail("No modal dialog found");
    }

    public StepResult assertModalDialogNotPresent() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        return session.isModalDialogExists() ?
               StepResult.fail("Modal dialog found") :
               StepResult.success("No modal dialog found");
    }

    public StepResult assertElementPresent(String name) {
        requiresNotBlank(name, "Invalid name/label", name);
        DesktopElement component = getRequiredElement(name, Any);
        boolean found = component != null;
        return new StepResult(found, "Element '" + name + "' " + (found ? "" : "NOT ") + "found", null);
    }

    public StepResult assertElementNotPresent(String name) {
        requiresNotBlank(name, "Invalid name/label", name);

        boolean found;
        try {
            found = getRequiredElement(name, Any) != null;
        } catch (AssertionError | Exception e) {
            found = false;
        }

        return new StepResult(!found,
                              found ?
                              "UNEXPECTED element '" + name + "' found" :
                              "element '" + name + "' not found, as expected",
                              null);
    }

    public StepResult assertEnabled(String name) {
        requiresNotBlank(name, "Invalid name/label", name);
        DesktopElement component = getRequiredElement(name, Any);
        if (component == null) { return StepResult.fail("Element '" + name + "' NOT found"); }

        boolean enabled = DesktopUtils.isEnabled(component.getElement());
        return new StepResult(enabled, "Element '" + name + "' is" + (enabled ? "" : " NOT") + " enabled", null);
    }

    public StepResult assertDisabled(String name) {
        requiresNotBlank(name, "Invalid name/label", name);
        DesktopElement component = getRequiredElement(name, Any);
        if (component == null) { return StepResult.fail("Element '" + name + "' NOT found"); }

        boolean disabled = !DesktopUtils.isEnabled(component.getElement());
        return new StepResult(disabled, "Element '" + name + "' " + (disabled ? "is" : "IS NOT") + " disabled", null);
    }

    public StepResult assertModalDialogTitle(String title) {
        requiresNotBlank(title, "Invalid title", title);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        WebElement dialog = session.getModalDialog();
        if (dialog == null) { return StepResult.fail("No modal dialog found"); }

        try {
            WebElement titleBar = dialog.findElement(By.xpath(LOCATOR_DIALOG_TITLE));
            if (titleBar == null) { return StepResult.fail("No title bar found for current modal dialog"); }

            String actualTitle = titleBar.getAttribute("Name");
            return assertEqual(StringUtils.trim(title), StringUtils.trim(actualTitle));
        } catch (WebDriverException e) {
            return StepResult.fail("Error when accessing current modal dialog title bar: " + e.getMessage());
        }
    }

    public StepResult clearModalDialog(String var, String button) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(button, "invalid button", button);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        String resultMsg;

        String dialogText = session.clearModalDialog(button);
        if (StringUtils.isNotEmpty(dialogText)) {
            ConsoleUtils.log("saving to var '" + var + "' the text '" + dialogText + "'");
            updateDataVariable(var, dialogText);
            resultMsg = "Modal dialog text harvested and saved to '" + var + "'.";
        } else {
            ConsoleUtils.error("Unable to save var '" + var + "' since no modal dialog text is found");
            resultMsg = "No modal dialog text found; likely the modal dialog is not cleared";
        }

        return StepResult.success(resultMsg);
    }

    public StepResult saveModalDialogText(String var) {
        requiresValidAndNotReadOnlyVariableName(var);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        String dialogText = session.collectModalDialogText();
        if (StringUtils.isNotEmpty(dialogText)) {
            updateDataVariable(var, dialogText);
            return StepResult.success("Modal dialog text saved to '" + var + "'");
        }

        return StepResult.success("No modal dialog found");
    }

    /** @deprecated use {@link #assertModalDialogTitle(String)} */
    public StepResult assertModalDialogTitleByLocator(String locator, String title) {
        WebElement modalDialog = findElement(locator);
        if (modalDialog == null) { return StepResult.fail("EXPECTED modal dialog NOT found."); }

        WebElement titleBar = findElement(modalDialog, "*[@ControlType='ControlType.TitleBar']");
        if (titleBar == null) { return StepResult.fail("EXPECTED modal dialog DOES NOT have a title bar"); }

        String actual = titleBar.getAttribute("Name");
        if (StringUtils.containsIgnoreCase(actual, title)) {
            return StepResult.success();
        } else {
            return StepResult.fail("Modal dialog found, but has an UNEXPECTED title: " + actual);
        }
    }

    public StepResult saveModalDialogTextByLocator(String var, String locator) {
        // find dialog
        WebElement modalDialog = findElement(locator);
        if (modalDialog == null) {
            context.removeData(var);
            return StepResult.success("No modal dialog found under application via " + locator + ".  No text saved");
        } else {
            String text = saveModalDialogText(modalDialog, var);
            return StepResult.success("Modal text found and saved to '" + var + "': " + text);
        }
    }

    public StepResult sendKeysToTextBox(String name, String text1, String text2, String text3, String text4) {
        DesktopElement component = getRequiredElement(name, Textbox);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }
        return component.typeTextComponent(true, false, text1, text2, text3, text4);
    }

    /**
     * send keys via {@link java.awt.Robot}. As such, this is not dependent on Winium driver, and the {@code keystrokes}
     * can be OS-specific. We use {@code os} to limit some undesired impact, albeit not completely. We use {@code os}
     * as a condition whether the specified {@code keystrokes} should be exercised.
     */
    public StepResult typeKeys(String os, String keystrokes) {
        requiresNotBlank(os, "invalid os", os);
        requiresNotBlank(keystrokes, "invalid keystrokes", keystrokes);

        List<String> oses = TextUtils.toList(os, context.getTextDelim(), true);
        if (CollectionUtils.isEmpty(oses)) { return StepResult.fail("Invalid os specified: '" + os + "'"); }

        oses = oses.stream().map(s -> s.trim().toUpperCase().replace(" ", "")).collect(Collectors.toList());

        boolean supported = false;
        for (String sys : oses) {
            if (!OS.isValid(sys)) { return StepResult.fail("Invalid os '" + sys + "'"); }
            if (IS_OS_WINDOWS && OS.isWindows(sys) || IS_OS_MAC && OS.isMac(sys) || IS_OS_LINUX && OS.isLinux(sys)) {
                supported = true;
                break;
            }
        }

        if (!supported) { return StepResult.skipped("current operating system not supported: '" + os + "'"); }

        ConsoleUtils.log("simulating keystrokes: " + keystrokes);
        NativeInputHelper.typeKeys(TextUtils.toList(StringUtils.remove(keystrokes, "\r"), "\n", false));
        return StepResult.success("type keys completed for " + keystrokes);
    }

    public StepResult typeTextBox(String name, String text1, String text2, String text3, String text4) {
        DesktopElement component = getRequiredElement(name, Textbox);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }
        return component.typeTextComponent(false, false, text1, text2, text3, text4);
    }

    public StepResult typeTextArea(String name, String text1, String text2, String text3, String text4) {
        DesktopElement component = getRequiredElement(name, TextArea);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }
        return component.typeTextComponent(false, false, text1, text2, text3, text4);
    }

    public StepResult typeAppendTextBox(String name, String text1, String text2, String text3, String text4) {
        DesktopElement component = getRequiredElement(name, Textbox);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }
        return component.typeTextComponent(false, true, text1, text2, text3, text4);
    }

    public StepResult typeAppendTextArea(String name, String text1, String text2, String text3, String text4) {
        DesktopElement component = getRequiredElement(name, TextArea);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }
        return component.typeTextComponent(false, true, text1, text2, text3, text4);
    }

    public StepResult typeByLocator(String locator, String text) {
        requires(StringUtils.isNotEmpty(text), "empty text", text);

        WebElement elem = findElement(locator);
        if (elem == null) { return StepResult.fail("element NOT found via " + locator); }

        type(elem, text);
        autoClearModalDialog(locator);
        return StepResult.success();
    }

    /**
     * auto-redirect to use {@link #typeTextBox(String, String, String, String, String)} if the specified element
     * resolves to a  {@link ElementType#TypeAheadCombo}
     */
    public StepResult selectCombo(String name, String text) {
        requiresNotBlank(text, "Invalid text", text);

        DesktopElement component = getRequiredElement(name, Any);
        if (component == null) { return StepResult.fail("Unable to find any component via '" + name + "'"); }
        if (!component.getElementType().isCombo()) {
            return StepResult.fail("Unable to resolve a combo component via '" + name + "'");
        }
        return component.select(text);
    }

    public StepResult clickIcon(String label) {
        requiresNotBlank(label, "Invalid icon label", label);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        IgRibbon ribbon = session.findThirdPartyComponent(IgRibbon.class);
        if (ribbon == null) { return StepResult.fail("EXPECTED ribbon/icon component NOT specified/loaded"); }

        new Actions(session.getDriver()).moveToElement(session.getApp().getElement()).perform();
        return ribbon.click(label);
    }

    public StepResult clickButton(String name) { return click(name, Button); }

    public StepResult clickCheckBox(String name) { return click(name, Checkbox); }

    public StepResult clickRadio(String name) { return click(name, Radio); }

    public StepResult clickByLocator(String locator) {
        WebElement elem = findElement(locator);
        if (elem == null) { return StepResult.fail("element NOT found via " + locator);}

        elem.click();
        autoClearModalDialog(locator);
        return StepResult.success();
    }

    public StepResult clickOffset(String locator, String xOffset, String yOffset) {
        requiresNotBlank(locator, "Invalid locator", locator);

        WebElement elem = findElement(locator);
        if (elem == null) { return StepResult.fail("element NOT found via " + locator);}

        clickOffset(elem, xOffset, yOffset);
        autoClearModalDialog(locator);
        return StepResult.success("Clicked offset (" + xOffset + "," + yOffset + ") from element " + locator);
    }

    public StepResult clickElementOffset(String name, String xOffset, String yOffset) {
        DesktopElement component = getRequiredElement(name, null);

        WebElement element = component.getElement();
        if (element == null) { return StepResult.fail("element NOT found via '" + name + "'");}

        clickOffset(element, xOffset, yOffset);
        autoClearModalDialog(component);
        return StepResult.success("Clicked offset (" + xOffset + "," + yOffset + ") from element '" + name + "'");
    }

    public StepResult clickScreen(String button, String modifiers, String x, String y) {
        requiresNotBlank(button, "Invalid mouse button", button);

        List<String> mods = new ArrayList<>();
        if (!context.isNullValue(modifiers) && !context.isEmptyValue(modifiers)) {
            while (StringUtils.isNotBlank(modifiers)) {
                String mod = TextUtils.substringBetweenFirstPair(modifiers, "{", "}", true);
                if (StringUtils.isEmpty(mod)) {
                    if (StringUtils.isNotBlank(modifiers)) {
                        ConsoleUtils.log("ignoring unsupported modifiers: " + modifiers);
                    }
                    break;
                }
                mods.add(mod);

                String beforeMod = StringUtils.substringBefore(modifiers, mod);
                if (StringUtils.isNotBlank(beforeMod)) {
                    ConsoleUtils.log("ignoring unsupported modifiers: " + beforeMod);
                }
                modifiers = StringUtils.substringAfter(modifiers, mod);
            }
        }

        Point screenPoint = NativeInputHelper.resolveScreenPosition(x, y);
        int posX = screenPoint.x;
        int posY = screenPoint.y;
        if (StringUtils.equalsIgnoreCase(button, "left")) {
            NativeInputHelper.click(mods, posX, posY);
        } else if (StringUtils.equalsIgnoreCase(button, "middle")) {
            NativeInputHelper.middleClick(mods, posX, posY);
        } else if (StringUtils.equalsIgnoreCase(button, "right")) {
            NativeInputHelper.rightClick(mods, posX, posY);
        } else {
            return StepResult.fail("Unknown/unsupported mouse button: " + button);
        }

        return StepResult.success(button + " mouse button clicked on screen (" + x + "," + y + ")");
    }

    public StepResult screenshotByLocator(String locator, String file) {
        requiresNotNull(locator, "Invalid locator", locator);
        requiresNotNull(file, "Invalid screenshot file", file);
        if (FileUtil.isDirectoryReadable(file)) {
            return StepResult.fail("Invalid file specified: " + file + " points to a directory");
        }

        WebElement elem = findFirstElement(locator);
        if (elem == null) { return StepResult.fail("No component can be resolved via locator " + locator); }

        List<String> dimension = TextUtils.toList(elem.getAttribute("BoundingRectangle"), ",", true);
        File imageFile = screenshot(file, dimension);

        return StepResult.success("Screenshot captured for " + locator + ": " + imageFile);
    }

    public StepResult screenshot(String name, String file) {
        requiresNotBlank(name, "Invalid component name", name);

        requiresNotNull(file, "Invalid screenshot file", file);
        if (FileUtil.isDirectoryReadable(file)) {
            return StepResult.fail("Invalid file specified: " + file + " points to a directory");
        }

        DesktopElement container = getCurrentContainer();
        WebElement elem;
        if (StringUtils.equals(container.getLabel(), name)) {
            elem = container.getElement();
            if (elem == null) {
                container.refreshElement();
                elem = container.getElement();
            }
        } else {
            elem = getRequiredElement(name, Any).getElement();
        }

        if (elem == null) { return StepResult.fail("No component can be resolved via its name " + name); }

        List<String> dimension = TextUtils.toList(elem.getAttribute("BoundingRectangle"), ",", true);
        File imageFile = screenshot(file, dimension);

        return StepResult.success("Screenshot captured for " + name + ": " + imageFile);
    }

    public StepResult doubleClick(String name) { return doubleClick(name, Any); }

    public StepResult doubleClickByLocator(String locator) {
        WebElement elem = findElement(locator);
        if (elem == null) { return StepResult.fail("element NOT found via " + locator);}
        new Actions(winiumDriver).doubleClick(elem).perform();
        autoClearModalDialog(locator);
        return StepResult.success();
    }

    public StepResult mouseWheel(String amount, String modifiers, String x, String y) {
        if (!IS_OS_WINDOWS) { return StepResult.skipped("mouseWheel() currently only works on Windows"); }

        requiresInteger(amount, "Invalid scroll amount", amount);

        List<String> mods = new ArrayList<>();
        if (!context.isNullValue(modifiers) && !context.isEmptyValue(modifiers)) {
            while (StringUtils.isNotBlank(modifiers)) {
                String mod = TextUtils.substringBetweenFirstPair(modifiers, "{", "}", true);
                if (StringUtils.isEmpty(mod)) {
                    if (StringUtils.isNotBlank(modifiers)) {
                        ConsoleUtils.log("ignoring unsupported modifiers: " + modifiers);
                    }
                    break;
                }
                mods.add(mod);

                String beforeMod = StringUtils.substringBefore(modifiers, mod);
                if (StringUtils.isNotBlank(beforeMod)) {
                    ConsoleUtils.log("ignoring unsupported modifiers: " + beforeMod);
                }
                modifiers = StringUtils.substringAfter(modifiers, mod);
            }
        }

        Point screenPoint = NativeInputHelper.resolveScreenPosition(x, y);
        int posX = screenPoint.x;
        int posY = screenPoint.y;
        NativeInputHelper.mouseWheel(NumberUtils.toInt(amount), mods, posX, posY);
        return StepResult.success("Mouse wheel moved by " + amount + " notches on screen (" + x + "," + y + ")");
    }

    public StepResult saveText(String var, String name) {
        requiresValidAndNotReadOnlyVariableName(var);

        String text = getText(name);
        if (StringUtils.isNotEmpty(text)) {
            updateDataVariable(var, text);
            return StepResult.success("Element '" + name + "' with text '" + text + "' saved to '" + var + "'");
        } else {
            context.removeData(var);
            return StepResult.success("Element '" + name + "' found with no text; '" + var + "' removed");
        }
    }

    public StepResult saveTextByLocator(String var, String locator) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(locator, "Invalid locator", locator);

        WebElement element = findElement(locator);
        if (element == null) { return StepResult.fail("element NOT found via " + locator);}

        try {
            String text = null;
            String controlType = element.getAttribute("ControlType");
            if (StringUtils.equals(controlType, CHECK_BOX) || StringUtils.equals(controlType, RADIO)) {
                if (BooleanUtils.toBoolean(element.getAttribute("IsTogglePatternAvailable"))) {
                    text = element.isSelected() ? "True" : "False";
                }
            }

            if (text == null) { text = StringUtils.defaultIfEmpty(element.getText(), element.getAttribute("Name")); }

            if (StringUtils.isNotEmpty(text)) {
                updateDataVariable(var, text);
                return StepResult.success("text content saved to '" + var + "'");
            } else {
                context.removeData(var);
                return StepResult.success("No text found for element " + locator + "; '" + var + "' removed");
            }
        } catch (WebDriverException e) {
            String msg = "Cannot resolve content for '" + locator + "': " + resolveErrorMessage(e);
            ConsoleUtils.error(context.getRunId(), msg);
            return StepResult.fail(msg);
        }
    }

    public StepResult saveWindowTitle(String var) {
        requiresValidAndNotReadOnlyVariableName(var);
        DesktopElement currentAppWindow = resolveCurrentTopMostWindow();
        String title = getCurrentTopMostWindowTitle(currentAppWindow);
        if (StringUtils.isNotEmpty(title)) {
            updateDataVariable(var, title);
            return StepResult.success("window title saved to '" + var + "': " + title);
        } else {
            return StepResult.success("No window title found");
        }
    }

    public StepResult saveLocatorCount(String var, String locator) {
        requires(StringUtils.isNotBlank(locator), "invalid locator", locator);
        requires(StringUtils.isNotBlank(var), "invalid variable", var);

        By findBy = findBy(locator);
        if (findBy == null) { return StepResult.fail("Unsupported/unknown locator " + locator); }

        List<WebElement> matched = getDriver().findElements(findBy);
        int count = CollectionUtils.size(matched);
        context.setData(var, count);
        return StepResult.success("Matched count of " + count + " saved to data " + var);
    }

    public StepResult saveAttributeByLocator(String var, String locator, String attribute) {
        requires(StringUtils.isNotBlank(var), "invalid variable", var);

        String value = getAttribute(locator, attribute);
        if (value != null) {
            updateDataVariable(var, value);
            return StepResult.success("Attribute " + attribute + "=" + value + "; saved to data " + var);
        }

        return StepResult.success("Either element not found via " + locator +
                                  " or it does not contain attribute " + attribute + ". No data saved to " + var);
    }

    public StepResult waitFor(String name, String maxWaitMs) {
        DesktopElement component = getRequiredElement(name, Any);
        return waitForLocator(component.getXpath(), maxWaitMs);
    }

    public StepResult waitForLocator(String locator, String maxWaitMs) {
        requiresPositiveNumber(maxWaitMs, "invalid maxWaitMs; only digits are expected", maxWaitMs);
        WebElement waitFor = waitForElement(null, locator, NumberUtils.toLong(maxWaitMs));
        return waitFor != null ?
               StepResult.success() :
               StepResult.fail("EXPECTED element NOT found via " + locator + " within " + maxWaitMs + " ms");
    }

    public StepResult assertText(String name, String expected) {
        String actual = getText(name);
        if (StringUtils.isEmpty(actual)) {
            if (StringUtils.isEmpty(expected)) {
                return StepResult.success("No text was expected, and no text was found for '" + name + "'");
            }

            return StepResult.fail("EXPECTED '" + expected + "' NOT found for '" + name + "'");
        }

        if (StringUtils.isBlank(actual) && StringUtils.isNotBlank(actual)) {
            return StepResult.fail("EXPECTED no text for element '" + name + "'; instead found '" + actual + "'");
        }

        if (StringUtils.isNotBlank(actual) && StringUtils.isBlank(actual)) {
            return StepResult.fail("EXPECTED text '" + actual + "' for element '" + name + "'; but found empty text");
        }

        return assertEqual(StringUtils.trim(expected), StringUtils.trim(actual));
    }

    public StepResult assertSelected(String name, String text) { return assertText(name, text); }

    public StepResult assertWindowTitleContains(String contains) {
        DesktopElement currentAppWindow = resolveCurrentTopMostWindow();
        String title = getCurrentTopMostWindowTitle(currentAppWindow);
        return title == null ?
               StepResult.fail("No title bar found for current window: " + currentAppWindow.getLabel()) :
               assertContains(title, contains);
    }

    public StepResult assertLocatorPresent(String locator) {
        WebElement elem = findElement(locator);
        return elem == null ?
               StepResult.fail("element NOT found via " + locator) :
               StepResult.success("element found via " + locator);
    }

    public StepResult assertLocatorNotPresent(String locator) {
        WebElement elem = findElement(locator);
        return elem == null ?
               StepResult.success("element NOT found via " + locator) :
               StepResult.fail("element found via " + locator);
    }

    public StepResult assertAttribute(String locator, String attribute, String expected) {
        requires(StringUtils.isNotEmpty(expected), "Expected value must be specified.", expected);

        String value = getAttribute(locator, attribute);
        if (value != null) { return assertEqual(expected, value); }

        return StepResult.fail("Either element not found via " + locator +
                               " or it does not contain attribute " + attribute);
    }

    public StepResult assertNotChecked(String name) {
        boolean checked = isChecked(name);
        return new StepResult(!checked, "Checkbox '" + name + "' is " + (checked ? "CHECKED" : "not checked"), null);
    }

    public StepResult assertChecked(String name) {
        boolean checked = isChecked(name);
        return new StepResult(checked, "Checkbox '" + name + "' is " + (checked ? "checked" : "NOT checked"), null);
    }

    public StepResult clearTextBox(String name) { return clear(name, Textbox); }

    public StepResult clearTextArea(String name) { return clear(name, TextArea); }

    public StepResult clearCombo(String name) {
        DesktopElement component = getRequiredElement(name, Any);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }
        if (!component.getElementType().isCombo()) { return StepResult.fail("component '" + name + "' is NOT a Combo");}
        return component.clearCombo();
    }

    public StepResult clear(String locator) {
        WebElement elem = findElement(locator);
        if (elem == null) { return StepResult.fail("element NOT found via " + locator); }

        elem.clear();
        autoClearModalDialog(locator);
        return StepResult.success();
    }

    public StepResult useList(String var, String name) { return saveListMetaData(var, name); }

    public StepResult assertListCount(String count) {
        requiresPositiveNumber(count, "Positive number required", count);

        DesktopList list = getCurrentList();
        requiresNotNull(list, "No List element referenced or scanned");

        return numberCommand.assertEqual(count, list.getRowCount() + "");
    }

    public StepResult saveFirstMatchedListIndex(String var, String contains) {
        requiresValidAndNotReadOnlyVariableName(var);

        DesktopList list = getCurrentList();
        requiresNotNull(list, "No List element referenced or scanned");

        int rowIndex = resolveFirstMatchedListIndex(list, contains);
        if (rowIndex != UNDEFINED) {
            context.setData(var, rowIndex);
            ConsoleUtils.log("saved matched list row to variable '" + var + "' as " + rowIndex);
        } else {
            context.removeData(var);
            ConsoleUtils.log("No matched row found.");
        }

        return StepResult.success();
    }

    public StepResult saveFirstListData(String var, String contains) {
        requiresValidAndNotReadOnlyVariableName(var);

        DesktopList list = getCurrentList();
        requiresNotNull(list, "No List element referenced or scanned");

        int rowIndex = resolveFirstMatchedListIndex(list, contains);
        if (rowIndex != UNDEFINED) {
            context.setData(var, list.getData(rowIndex));
            ConsoleUtils.log("saved matched list row to variable '" + var + "'");
        } else {
            context.removeData(var);
            ConsoleUtils.log("No matched row found.");
        }

        return StepResult.success();
    }

    public StepResult saveListData(String var, String contains) {
        requiresValidAndNotReadOnlyVariableName(var);

        DesktopList list = getCurrentList();
        requiresNotNull(list, "No List element referenced or scanned");

        ArrayList<List<String>> matches = new ArrayList<>();
        list.getData().forEach(item -> {
            for (String text : item) {
                if (StringUtils.contains(text, contains)) {
                    matches.add(item);
                    break;
                }
            }
        });

        if (matches.isEmpty()) {
            context.removeData(var);
            ConsoleUtils.log("No matched row found.");
        } else {
            context.setData(var, matches);
            ConsoleUtils.log("saved matched list (" + matches.size() + ") to variable '" + var + "'" + NL + matches);
        }

        return StepResult.success();
    }

    public StepResult clickFirstMatchedList(String contains) {
        DesktopList list = getCurrentList();
        requiresNotNull(list, "No List element referenced or scanned");

        int rowIndex = resolveFirstMatchedListIndex(list, contains);
        if (rowIndex == UNDEFINED) { return StepResult.fail("No matched row found.  No click performed"); }

        WebElement row = list.getDataElement(rowIndex);
        if (row == null) { return StepResult.fail("Unable to click list: Null row element"); }

        boolean listContainsScrollbar = list.getVerticalScrollBar() != null;
        if (!listContainsScrollbar) {
            ConsoleUtils.log("no vertical scroll bar found, directly clicking on the row");
            row.click();
        } else {
            ConsoleUtils.log("found vertical scroll bar, hence we need to use keyboard/shortcuts");

            WiniumDriver driver = list.getDriver();
            WebElement listElement = list.getElement();

            // click first shown option
            // listElement.click();
            driver.executeScript(SCRIPT_CLICK, listElement);
            new Actions(driver).moveToElement(listElement, 5, 5).click().perform();

            List<String> shortcuts = new ArrayList<>();
            // move to first open via 'HOME'
            shortcuts.add("HOME");

            // we should always found match, since we got a matching 'rowIndex'
            boolean foundMatch = false;
            for (int i = 0; i < list.getRowCount(); i++) {
                WebElement elem = list.getDataElement(i);
                if (elem == row) {
                    foundMatch = true;
                    break;
                }
                shortcuts.add("DOWN");
            }

            if (!foundMatch) { return StepResult.fail("Unable to found list item matching '" + contains + "'"); }

            // run the shortcut sequence to select the target list item
            driver.executeScript(toShortcuts(shortcuts.toArray(new String[shortcuts.size()])), listElement);
        }

        autoClearModalDialog(list);
        return StepResult.success("List row " + rowIndex + " matched to '" + contains + "' clicked");
    }

    public StepResult clickList(String row) {
        requiresPositiveNumber(row, "Invalid row index", row);
        int rowIndex = NumberUtils.toInt(row);

        DesktopList list = getCurrentList();
        requiresNotNull(list, "No List element referenced or scanned");

        WebElement rowElement = list.getDataElement(rowIndex);
        if (rowElement == null) { return StepResult.fail("Unable to retrieve list row " + rowIndex); }

        rowElement.click();
        autoClearModalDialog(list);
        return StepResult.success("List row " + rowIndex + " clicked");
    }

    /**
     * {@code criteria} is a mpa of various matching criteria, as follows:
     * <ol>
     * <li>stopOnMatch - if true, we will stop scanning upon the first match</li>
     * <li>contains - specifies the text to match by, using "contains" match. DISABLED WHEN 'regex' IS SPECIFIED</li>
     * <li>limitMatch - if true, then only match the characters specified in contains. APPLICABLE ONLY WHEN USING 'contains'.</li>
     * <li>regex - specify the regex to match by; takes precedence over "contains" match</li>
     * <li>stopOnEmptyText - if true, seeknow stops upon finding a "blank" row (rows with only white color)</li>
     * <li>limitRows - specified the rows (zero-based) to scan.  When used, all unspecified rows will be skipped</li>
     * <li>color - specified the color as a criteria to match.  Used in the form of <code>red:[0-255];green:[0-255];blue:[0-255]</code></li>
     * </ol>
     */
    public StepResult clickTextPane(String name, String criteria) {
        requiresNotBlank(criteria, "Invalid criteria", criteria);

        DesktopElement component = getRequiredElement(name, TextPane);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }

        Map<String, String> criteriaMap = nameValuesToMap(criteria);
        if (MapUtils.isEmpty(criteriaMap)) { return StepResult.fail("Invalid criteria specified: " + criteria); }

        try {
            return component.clickMatchedTextPane(criteriaMap);
        } catch (IOException e) {
            return StepResult.fail("Unable to match text pane due to " + e.getMessage(), e);
        }
    }

    /**
     * @see #clickTextPane(String, String) for details regarding {@code criteria}
     */
    public StepResult saveTextPane(String var, String name, String criteria) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(criteria, "Invalid criteria", criteria);

        DesktopElement component = getRequiredElement(name, TextPane);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }

        Map<String, String> criteriaMap = nameValuesToMap(criteria);
        if (MapUtils.isEmpty(criteriaMap)) { return StepResult.fail("Invalid criteria specified: " + criteria); }

        try {
            List<SeeknowData> matches = component.findTextPaneMatches(criteriaMap);
            if (CollectionUtils.isEmpty(matches)) {
                ConsoleUtils.log("No matches found");
                context.removeData(var);
                context.removeData(DESKTOP_CURRENT_TEXTPANE);
                return StepResult.success("No matches found");
            } else {
                ConsoleUtils.log(matches.size() + " match(es) found");
                context.setData(var, matches);
                ConsoleUtils.log("saving current textpane as '" + DESKTOP_CURRENT_TEXTPANE + "'");
                context.setData(DESKTOP_CURRENT_TEXTPANE, component);
                return StepResult.success(matches.size() + " match(es) found");
            }
        } catch (IOException e) {
            return StepResult.fail("Unable to match text in " + name + ": " + e.getMessage(), e);
        }
    }

    public StepResult clickTextPaneRow(String var, String index) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresPositiveNumber(index, "Invalid row index (zero-based)", index);

        if (!context.hasData(var)) { return StepResult.fail("No variable '" + var + "' found"); }
        if (!context.hasData(DESKTOP_CURRENT_TEXTPANE)) { return StepResult.fail("No TextPane previously used"); }

        Object textPaneObject = context.getObjectData(DESKTOP_CURRENT_TEXTPANE);
        if (textPaneObject == null) { return StepResult.fail("No TextPane previously used"); }
        if (!(textPaneObject instanceof DesktopElement)) {
            return StepResult.fail("Invalid object type: " + textPaneObject.getClass().getSimpleName());
        }

        DesktopElement textPane = (DesktopElement) textPaneObject;
        if (textPane.getElementType() != TextPane) {
            return StepResult.fail("Invalid component type: " + textPane.getElementType());
        }
        if (textPane.getElement() == null) { textPane.refreshElement(); }

        Object result = context.getObjectData(var);
        if (result instanceof SeeknowData) {
            return DesktopElement.clickMatchedTextPaneRow(getDriver(), textPane.getElement(), (SeeknowData) result);
        }

        int row = NumberUtils.toInt(index);
        try {
            if (result instanceof Collection) { return clickTextPaneRow(textPane, CollectionUtils.get(result, row)); }
            if (result.getClass().isArray()) { return clickTextPaneRow(textPane, Array.get(result, row)); }
        } catch (IllegalArgumentException e) {
            fail("Variable '" + var + "' DOES NOT contains the EXPECTED TextPane row");
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            fail("Variable '" + var + "' DOES NOT has index " + index);
        }

        return StepResult.fail("Unable to resolve TextPane row from variable '" + var + "', index " + index);
    }

    public StepResult useTable(String var, String name) {
        StepResult result = saveTableMetaData(var, name, true);
        // remove any stale data
        context.removeData(CURRENT_DESKTOP_TABLE_ROW);
        context.removeData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_FOUND);
        context.removeData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_NAME);
        return result;
    }

    // DONE: deprecated; removal candidate for v1.1
    //    public StepResult scanTable(String var, String name) {
    //        logDeprecated(getTarget() + " » scanTable(var,name)", getTarget() + " » useTable(var,name)");
    //        return useTable(var, name);
    //    }

    /** support PolyMatcher, as of v3.7 */
    public StepResult assertTableRowContains(String row, String contains) {
        requiresPositiveNumber(row, "Invalid row index (zero-based)", row);
        requiresNotBlank(contains, "Invalid 'contains' specified", contains);

        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");

        String delim = context.getTextDelim();
        String[] criterion = StringUtils.contains(contains, delim) ?
                             StringUtils.splitByWholeSeparator(contains, delim) :
                             new String[]{contains};

        setTableFocus(table);
        boolean found = table.containsRowData(NumberUtils.toInt(row), criterion);
        return new StepResult(found,
                              "Table row '" + row + "'" + (found ? "" : " DOES NOT ") + "contains '" + contains + "'",
                              null);
    }

    /** support PolyMatcher, as of v3.7 */
    public StepResult assertTableColumnContains(String column, String contains) {
        requiresNotBlank(column, "Invalid column name", column);
        requiresNotBlank(contains, "Invalid 'contains' specified", contains);
        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");
        String delim = context.getTextDelim();
        String[] criterion = StringUtils.contains(contains, delim) ?
                             StringUtils.splitByWholeSeparator(contains, delim) :
                             new String[]{contains};

        setTableFocus(table);
        boolean found = table.containsColumnData(column, criterion);
        return new StepResult(found,
                              "Table column '" + column + "'" + (found ? "" : " DOES NOT ") + "contains '" +
                              contains + "'",
                              null);
    }

    /** support PolyMatcher, as of v3.7 */
    public StepResult assertTableContains(String contains) {
        requiresNotBlank(contains, "Invalid 'contains' specified", contains);

        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");

        setTableFocus(table);
        String delim = context.getTextDelim();
        boolean found = table.containsAnywhere(table.fetchAll().getRows(), TextUtils.toList(contains, delim, false));
        return new StepResult(found, "Table " + (found ? "" : " DOES NOT ") + "contains '" + contains + "'", null);
    }

    /** support PolyMatcher and TreeView grid, as of v3.7 */
    public StepResult assertTableCell(String row, String column, String contains) {
        requiresPositiveNumber(row, "Invalid row index (zero-based)", row);
        requiresNotBlank(column, "Invalid column name", column);
        // allow blank value assertions
        requiresNotNull(contains, "Invalid 'contains' specified", contains);

        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");
        requires(table.containsColumnHeader(column), "Invalid column", column);

        setTableFocus(table);
        TableData tableData = table.fetch(NumberUtils.toInt(row), NumberUtils.toInt(row));
        String actual = tableData.getCell(column);

        if (!PolyMatcher.isPolyMatcher(contains)) { return assertContains(actual, contains); }

        String textForDisplay = context.truncateForDisplay(actual);
        if (TextUtils.polyMatch(actual, contains)) {
            return StepResult.success("validated text '%s' poly-matched by '%s'", textForDisplay, contains);
        } else {
            return StepResult.fail("'%s' NOT poly-matched by '%s'", textForDisplay, contains);
        }
    }

    /** support PolyMatcher, as of v3.7 */
    public StepResult saveTableRows(String var, String contains, String csv) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(contains, "Invalid 'contains' specified", contains);

        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");

        setTableFocus(table);
        String delim = context.getTextDelim();
        List<List<String>> matched = table.findMatchedRows(TextUtils.toList(contains, delim, false));
        if (BooleanUtils.toBoolean(csv)) {
            context.setData(var, TextUtils.toCsvLine(table.headers, ",", lineSeparator()) +
                                 TextUtils.toCsvContent(matched, ",", lineSeparator()));
            return StepResult.success(matched.size() + " row(s) of Table saved as CSV to '" + var + "'");
        } else {
            context.setData(var, matched);
            return StepResult.success(matched.size() + " row(s) of Table saved to '" + var + "'");
        }
    }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) and converting to CSV structure (text) */
    public StepResult saveAllTableRows(String var, String csv) {
        requiresValidAndNotReadOnlyVariableName(var);
        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");

        setTableFocus(table);
        List<List<String>> allRows = table.fetchAll().getRows();
        if (BooleanUtils.toBoolean(csv)) {
            context.setData(var, TextUtils.toCsvLine(table.headers, ",", lineSeparator()) +
                                 TextUtils.toCsvContent(allRows, ",", lineSeparator()));
            return StepResult.success(allRows.size() + " row(s) of Table saved as CSV to '" + var + "'");
        } else {
            context.setData(var, allRows);
            return StepResult.success(allRows.size() + " row(s) of Table saved to '" + var + "'");
        }
    }

    public StepResult saveElementCount(String var, String name) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(name, "Invalid name", name);

        DesktopElement component = getRequiredElement(name, Any);
        if (component == null) {
            context.setData(var, 0);
            return StepResult.success("0 element found by name '" + name + "'");
        }

        List<WebElement> matches = getDriver().findElements(By.xpath(component.getXpath()));
        if (CollectionUtils.isEmpty(matches)) {
            context.setData(var, 0);
            return StepResult.success("0 element found by name '" + name + "'");
        }

        context.setData(var, matches.size());
        return StepResult.success(matches.size() + " element(s) found by name '" + name + "'");
    }

    public StepResult clickFirstMatchRow(String nameValues) {
        requiresNotBlank(nameValues, "Invalid name-value pairs specified", nameValues);
        requires(StringUtils.contains(nameValues, "="), "Name-values MUST be in the form of name=value", nameValues);

        Map<String, String> nameValuePairs = nameValuesToMap(nameValues);

        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");

        try {
            boolean clicked = table.clickFirstMatchedRow(nameValuePairs);
            if (clicked) {
                return StepResult.success("Click table row that matched '" + nameValues + "'");
            } else {
                return StepResult.fail("Unable to click on a table row that matched '" + nameValues + "'");
            }
        } catch (Throwable e) {
            return StepResult.fail("Unable to click table row with matching '" + nameValues + "': " + e.getMessage());
        }
    }

    /** support TreeView grid, as of v3.7 */
    public StepResult clickTableRow(String row) {
        requiresPositiveNumber(row, "Invalid row index (zero-based)", row);
        int rowIndex = NumberUtils.toInt(row);

        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");

        table.clickRow(rowIndex);
        return StepResult.success("Table row '" + row + "' cell 0 is clicked");
    }

    /** support TreeView grid, as of v3.7 */
    public StepResult clickTableCell(String row, String column) {
        requiresPositiveNumber(row, "Invalid row index (zero-based)", row);
        int rowIndex = NumberUtils.toInt(row);

        requiresNotBlank(column, "Invalid column name", column);

        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");

        int columnIndex = table.findColumnIndex(column);
        if (columnIndex < 0) { return StepResult.fail("Table DOES NOT contain column '" + column + "'"); }

        table.clickCell(rowIndex, column);
        autoClearModalDialog(table);
        return StepResult.success("Table row '" + row + "' column '" + column + "' is clicked");
    }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) */
    public StepResult editTableCells(String row, String nameValues) {
        requiresNotBlank(row, "Invalid row index (zero-based)", row);
        int rowIndex = StringUtils.equals(row, "*") ? MAX_VALUE : NumberUtils.toInt(row);
        requiresPositiveNumber(rowIndex + "", "Invalid row index (zero-based)", row);

        requiresNotBlank(nameValues, "Invalid name-values", nameValues);
        requires(StringUtils.contains(nameValues, "="), "Name-values MUST be in the form of name=value", nameValues);

        DesktopTable table = getCurrentTable();
        requiresNotNull(table, "No Table element referenced or scanned");
        setTableFocus(table);
        return table.editCells(rowIndex, nameValuesToMap(nameValues));
    }

    public StepResult useTableRow(String var, String row) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(row, "Invalid row index (zero-based)", row);
        int rowIndex = StringUtils.equals(row, "*") ? MAX_VALUE : NumberUtils.toInt(row);
        requiresPositiveNumber(rowIndex + "", "Invalid row index (zero-based)", row);

        DesktopTable table = getCurrentTable();
        if (table == null) {
            context.removeData(var);
            return StepResult.fail("Unable to fetch/create row '" + row + "' since no desktop table is in " +
                                   "reference.  Check that desktop.useTable() is executed before this command.");
        }

        setTableFocus(table);
        String tableName = context.getStringData(CURRENT_DESKTOP_TABLE_NAME);
        DesktopTableRow tableRow = table.fetchOrCreateRow(rowIndex);
        syncTableRowDataToContext(var, tableName, rowIndex, tableRow);
        return StepResult.success("Table '" + tableName + "' Row '" + tableRow.getRow() + "' fetched and " +
                                  "ready for use");
    }

    public StepResult editCurrentRow(String nameValues) {
        requiresNotBlank(nameValues, "Invalid name-values", nameValues);
        requires(StringUtils.contains(nameValues, "="), "Name-values MUST be in the form of name=value", nameValues);

        logDeprecated(getTarget() + " » editCurrentRow(nameValues)", getTarget() + " » editTableCells(row,nameValues)");

        if (!context.hasData(CURRENT_DESKTOP_TABLE_ROW)) {
            fail("Unable to proceed since no table row in reference.  Check that desktop.useTable() is executed " +
                 "before this command");
        }

        DesktopTableRow tableRow = (DesktopTableRow) context.getObjectData(CURRENT_DESKTOP_TABLE_ROW);
        if (tableRow == null || MapUtils.isEmpty(tableRow.getColumns()) || tableRow.getTable() == null) {
            return StepResult.fail("Unable to proceed since current table row in reference is invalid");
        }

        return tableRow.getTable().editCells(tableRow, nameValuesToMap(nameValues));
    }

    public StepResult saveTableRowsRange(String var, String beginRow, String endRow, String csv) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresPositiveNumber(beginRow, "Invalid beginRow", beginRow);
        requiresPositiveNumber(endRow, "Invalid beginRow", endRow);

        int beginRowNum = NumberUtils.toInt(beginRow);
        int endRowNum = NumberUtils.toInt(endRow);
        tableRowsRangeCheck(beginRowNum, endRowNum);

        DesktopTable table = getCurrentTable();
        if (table == null) {
            throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
        }
        setTableFocus(table);

        final TableData rows = table.fetch(beginRowNum, endRowNum);
        if (BooleanUtils.toBoolean(csv)) {
            context.setData(var, TextUtils.toCsvLine(table.headers, ",", lineSeparator()) +
                                 TextUtils.toCsvContent(rows.getRows(), ",", lineSeparator()));
            return StepResult.success(rows.getRowCount() + " row(s) of Table saved as CSV to '" + var + "'");
        } else {
            context.setData(var, rows);
            return StepResult.success("Table row data is saved to var " + var);
        }

    }

    public StepResult saveRowCount(String var) {
        requiresValidAndNotReadOnlyVariableName(var);
        DesktopTable table = getCurrentTable();
        if (table == null) {
            throw new IllegalArgumentException("ERROR: no Table object found. Make sure to run useTable() first");
        }
        setTableFocus(table);
        context.setData(var, table.getTableRowCount());
        return StepResult.success("Table row count is saved to var " + var);
    }

    public StepResult useHierTable(String var, String name) { return saveHierTableMetaData(var, name); }

    public StepResult collapseHierTable() {
        DesktopHierTable hiertable = getCurrentHierTable();
        requiresNotNull(hiertable, "No Hierarchical Table element referenced or scanned");
        hiertable.collapseAll();
        return StepResult.success();
    }

    public StepResult saveHierRow(String var, String matchBy) {
        requiresValidAndNotReadOnlyVariableName(var);
        Map<String, String> rowData = getHierRow(matchBy);
        context.setData(var, rowData);
        return StepResult.success("Hierarchical table row saved to '" + var + "'");
    }

    public StepResult saveHierCells(String var, String matchBy, String column, String nestedOnly) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(matchBy, "Invalid 'matchBy' specified", matchBy);
        requiresNotBlank(column, "Invalid 'column' specified", column);
        if (CheckUtils.toBoolean(nestedOnly)) {
            return saveHierCellChildData(var, matchBy, column);
        }

        String cellData = getHierCell(matchBy, column);
        if (cellData == null) {
            context.removeData(var);
            return StepResult.success("No data found in column '" + column + "'.  Variable '" + var + "' removed.");
        } else {
            updateDataVariable(var, cellData);
            return StepResult.success("Cell data for '" + column + "' saved to variable '" + var + "'");
        }
    }

    public StepResult assertHierRow(String matchBy, String expected) {
        requiresNotBlank(matchBy, "Invalid 'matchBy' specified", matchBy);
        Map<String, String> rowData = getHierRow(matchBy);
        if (StringUtils.isBlank(expected)) {
            if (!rowData.isEmpty()) {
                return StepResult.fail("EXPECTS empty but row data found via " + matchBy);
            }

            return StepResult.success("EXPECTS empty and either empty row or no match was found");
        }
        if (rowData.isEmpty()) {
            return StepResult.fail("EXPECTS '" + expected + "' but empty row or no match was found");
        }
        String delim = context.getTextDelim();
        String[] criterion = StringUtils.contains(expected, delim) ?
                             StringUtils.splitByWholeSeparator(expected, delim) :
                             new String[]{expected};

        String[] actualValues = rowData.values().toArray(new String[0]);

        return assertArrayEqual(Arrays.toString(criterion), Arrays.toString(actualValues), "true");

    }

    public StepResult assertHierCells(String matchBy, String column, String expected, String nestedOnly) {
        requiresNotBlank(matchBy, "Invalid 'matchBy' specified", matchBy);
        requiresNotBlank(column, "Invalid 'column' specified", column);
        if (CheckUtils.toBoolean(nestedOnly)) {
            return assertHierCellChildData(matchBy, column, expected);
        }
        String cellData = getHierCell(matchBy, column);
        if (cellData == null) {
            if (StringUtils.isEmpty(expected)) { return StepResult.success("EXPECTED empty cell found"); }
            return StepResult.fail("EXPECTED '" + expected + "' but found empty cell instead");
        }

        if (StringUtils.isEmpty(expected)) {
            return StepResult.success("EXPECTED empty but cell data contains '" + cellData + "'");
        }

        return assertEqual(StringUtils.trim(expected), StringUtils.trim(cellData));
    }

    public StepResult editHierCells(String var, String matchBy, String nameValues) {
        requiresNotBlank(matchBy, "Invalid 'matchBy' specified", matchBy);
        requiresNotBlank(nameValues, "Invalid name-value pairs specified", nameValues);
        requires(StringUtils.contains(nameValues, "="), "Name-values MUST be in the form of name=value", nameValues);

        DesktopHierTable hiertable = getCurrentHierTable();
        requiresNotNull(hiertable, "No Hierarchical Table element referenced or scanned");

        String delim = context.getTextDelim();
        List<String> matchData = TextUtils.toList(matchBy, delim, true);
        Map<String, String> result = hiertable.editHierCell(matchData, nameValuesToMap(nameValues));
        context.setData(var, result);
        return StepResult.success("Edited " + nameValues + " on the row where matchBy  =" + matchBy);
    }

    public StepResult clickTab(String group, String name) {
        requiresNotBlank(group, "Invalid tab group", group);
        requiresNotBlank(name, "Invalid tab name", name);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");
        DesktopElement component = session.findContainer(group, session.getApp());
        requiresNotNull(component, "No Tab '" + group + "' defined");
        requires((component instanceof DesktopTabGroup),
                 "Requested element '" + group + "' is not a Tab, but a " + component.getClass());

        DesktopTabGroup tabGroup = (DesktopTabGroup) component;
        return tabGroup.clickTab(name);
    }

    /** maximize current window (based current app) */
    public StepResult maximize() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        WebElement window = getApp();
        requiresNotNull(window, "Unable to resolve the window object '" + session.getAppId() + "'");

        session.getDriver().executeScript("maximize", window);
        return StepResult.success("window maximized");
    }

    /** minimize current window (based current app) */
    public StepResult minimize() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        WebElement window = getApp();
        requiresNotNull(window, "Unable to resolve the window object '" + session.getAppId() + "'");

        session.getDriver().executeScript("minimize", window);
        return StepResult.success("window minimized");
    }

    /** resize current window (based current app) */
    public StepResult resize(String width, String height) {
        requiresPositiveNumber(width, "Invalid width", width);
        requiresPositiveNumber(height, "Invalid height", height);

        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        WebElement window = getApp();
        requiresNotNull(window, "Unable to resolve the window object '" + session.getAppId() + "'");

        String newDimension = width + "X" + height;
        session.getDriver().executeScript("resize", window, newDimension);
        return StepResult.success("window resized to " + newDimension);
    }

    public StepResult closeApplication() {
        if (context != null) {
            CanTakeScreenshot agent = context.findCurrentScreenshotAgent();
            if (agent instanceof DesktopCommand) { context.clearScreenshotAgent(); }
        }

        DesktopSession session = getCurrentSession();
        if (session == null) {
            WiniumUtils.shutdownWinium(null, winiumDriver);
            winiumDriver = null;
            return StepResult.success();
        }

        if (!WiniumUtils.isSoloMode()) {
            String msg = "not closing application due solo mode currently in effect";
            ConsoleUtils.log(msg);
            return StepResult.skipped(msg);
        }

        Aut aut = session.getConfig().getAut();
        if (aut == null || winiumDriver == null || getDriver() == null) {
            return StepResult.success("No desktop application running");
        }

        // doing it old school...
        boolean closed = WiniumUtils.terminateRunningInstance(aut.getExe());
        String message = "application " + (closed ? "terminated via process termination" : "DID NOT terminate");

        if (closed) {
            session.terminateSession();
            session = null;
            context.removeData(CURRENT_DESKTOP_SESSION);
            context.removeData(DESKTOP_APPLICATION);
            context.removeData(DESKTOP_PROCESS_ID);
            context.removeData(CURRENT_DESKTOP_CONTAINER);
            context.removeData(CURRENT_DESKTOP_CONTAINER_NAME);
            context.removeData(CURRENT_DESKTOP_HIER_TABLE);
            context.removeData(CURRENT_DESKTOP_HIER_TABLE_NAME);
            context.removeData(CURRENT_DESKTOP_LIST);
            context.removeData(CURRENT_DESKTOP_LIST_NAME);
            context.removeData(CURRENT_DESKTOP_TABLE);
            context.removeData(CURRENT_DESKTOP_TABLE_NAME);
            context.removeData(CURRENT_DESKTOP_TABLE_ROW);
            context.removeData(CURRENT_DESKTOP_TABLE_ROW_NAME);
        }

        return new StepResult(closed, message, null);
    }

    /**
     * @deprecated use {@link DesktopSession#saveProcessId()}
     */
    public StepResult saveProcessId(String var, String locator) {
        requires(StringUtils.isNotBlank(var), "invalid variable", var);

        String processId = findProcessId(locator);
        if (StringUtils.isBlank(processId)) {
            return StepResult.fail("element NOT found via " + locator);
        } else {
            context.setData(var, processId);
            return StepResult.success("process id " + processId + " saved to variable " + var);
        }
    }

    protected StepResult saveHierCellChildData(String var, String matchBy, String fetchColumn) {
        requiresValidAndNotReadOnlyVariableName(var);
        requiresNotBlank(matchBy, "Invalid 'matchBy' specified", matchBy);
        requiresNotBlank(fetchColumn, "Invalid 'column' specified", fetchColumn);

        List<String> cellData = getHierCellChildData(matchBy, fetchColumn);
        if (cellData != null) {
            context.setData(var, cellData);
            return StepResult.success("Column data for '" + fetchColumn + "' saved to variable '" + var + "'");
        }

        context.removeData(var);
        return StepResult.success("No data found in column '" + fetchColumn + "'.  Variable '" + var + "' removed.");
    }

    protected StepResult assertHierCellChildData(String matchBy, String fetchColumn, String expected) {
        requiresNotBlank(matchBy, "Invalid 'matchBy' specified", matchBy);
        requiresNotBlank(fetchColumn, "Invalid 'column' specified", fetchColumn);

        List<String> cellData = getHierCellChildData(matchBy, fetchColumn);
        if (StringUtils.isBlank(expected)) {
            if (!cellData.isEmpty()) {
                return StepResult.fail("EXPECTS empty but cell data found via " + matchBy);
            }

            return StepResult.success("EXPECTS empty and either empty row or no match was found");
        }
        if (cellData.isEmpty()) {
            return StepResult.fail("EXPECTS '" + expected + "' but empty row or no match was found");
        }

        String delim = context.getTextDelim();
        String[] criterion = StringUtils.contains(expected, delim) ?
                             StringUtils.splitByWholeSeparator(expected, delim) :
                             new String[]{expected};

        String[] actualValues = cellData.toArray(new String[0]);

        return assertArrayEqual(Arrays.toString(criterion), Arrays.toString(actualValues), "true");

    }

    protected StepResult clickTextPaneRow(DesktopElement textPane, Object rowObject) {
        if (rowObject == null) { throw new NullPointerException(); }
        if (rowObject instanceof SeeknowData) {
            return DesktopElement.clickMatchedTextPaneRow(getDriver(), textPane.getElement(), (SeeknowData) rowObject);
        }

        ConsoleUtils.log("EXPECTS type " + SeeknowData.class + ", but got " + rowObject.getClass() + " instead");
        return StepResult.fail("Specified variable DOES NOT represent a TextPane row");
    }

    protected String getHierCell(String matchBy, String column) {
        DesktopHierTable hiertable = getCurrentHierTable();
        requiresNotNull(hiertable, "No Hierarchical Table element referenced or scanned");

        if (!hiertable.getHeaders().contains(column)) {CheckUtils.fail("Specified column '" + column + "' not found.");}
        String delim = context.getTextDelim();
        List<String> matchData = TextUtils.toList(matchBy, delim, true);

        Map<String, String> row = hiertable.getHierRow(matchData);
        if (row == null) { CheckUtils.fail("Unable to fetch data from hiertable"); }

        return String.valueOf(row.get(column));
    }

    protected List<String> getHierCellChildData(String matchBy, String fetchColumn) {
        DesktopHierTable hiertable = getCurrentHierTable();
        requiresNotNull(hiertable, "No Hierarchical Table element referenced or scanned");
        requires(hiertable.containsHeader(fetchColumn), "Unmatched column specified", fetchColumn);
        String delim = context.getTextDelim();
        List<String> matchData = TextUtils.toList(matchBy, delim, true);

        List<String> cellData = hiertable.getHierCellChildData(matchData, fetchColumn);
        if (cellData == null) { CheckUtils.fail("Unable to fetch data from hiertable"); }

        return cellData;
    }

    protected Map<String, String> nameValuesToMap(String nameValues) {
        if (nameValues.isEmpty()) { return new HashMap<>(); }
        String pairDelim = "\n";
        if (!StringUtils.contains(nameValues, pairDelim) && !StringUtils.contains(nameValues, "\r")) {
            String delim = context.getTextDelim();
            nameValues = StringUtils.replace(nameValues, delim, pairDelim);
        } else {
            nameValues = StringUtils.replace(nameValues, "\r", pairDelim);
        }

        Map<String, String> nameValuePairs = TextUtils.toMap(nameValues, pairDelim, "=");
        requires(MapUtils.isNotEmpty(nameValuePairs), "Invalid name-values", nameValues);
        return nameValuePairs;
    }

    protected Map<String, String> getHierRow(String matchBy) {
        requiresNotBlank(matchBy, "Invalid 'matchBy' specified", matchBy);

        DesktopHierTable hierTable = getCurrentHierTable();
        requiresNotNull(hierTable, "No Hierarchical Table element referenced or scanned");

        String delim = context.getTextDelim();
        List<String> matchData = TextUtils.toList(matchBy, delim, true);
        return hierTable.getHierRow(matchData);
    }

    protected WiniumDriver getDriver() {
        if (!IS_OS_WINDOWS) {
            log("Winium requires Windows OS");
            return null;
        }

        DesktopSession session = getCurrentSession();
        if (session != null) { return session.getDriver(); }

        if (winiumDriver != null) { return winiumDriver; }

        winiumDriver = WiniumUtils.joinRunningApp();
        return winiumDriver;
    }

    protected DesktopSession getCurrentSession() {
        if (!context.hasData(CURRENT_DESKTOP_SESSION)) { return null; }

        Object sessionObj = context.getObjectData(CURRENT_DESKTOP_SESSION);
        if (!(sessionObj instanceof DesktopSession)) {
            context.removeData(CURRENT_DESKTOP_SESSION);
            return null;
        }

        DesktopSession session = (DesktopSession) sessionObj;
        if (session.getApp() == null) {
            ConsoleUtils.error("desktop session found without 'app'; return null!");
            return null;
        }

        if (StringUtils.isBlank(session.getAppId())) {
            ConsoleUtils.error("desktop session found without 'App ID'; return null!");
            return null;
        }

        return session;
    }

    protected DesktopElement getCurrentContainer() {
        Object obj = context.getObjectData(CURRENT_DESKTOP_CONTAINER);
        return obj instanceof DesktopElement ? (DesktopElement) obj : null;
    }

    public DesktopElement getRequiredElement(String name, ElementType expectedType) {
        requiresNotBlank(name, "Invalid name", name);

        DesktopElement container = getCurrentContainer();
        requiresNotNull(container, "No active form found");

        String delim = context.getTextDelim();
        if (StringUtils.contains(name, delim)) { name = StringUtils.replace(name, delim, NESTED_CONTAINER_SEP); }

        DesktopElement component = container.getComponent(name);
        requiresNotNull(component, "No element '" + name + "' in current form '" + container.getLabel() + "'");
        requiresNotBlank(component.getXpath(), "Element '" + name + "' has no XPATH; Unable to proceed");

        if (component.getElement() == null) { component.refetchComponents(); }

        if (expectedType == null || expectedType == Any) { return component; }

        if (expectedType.isCombo()) {
            if (!component.getElementType().isCombo()) { warnTypeMismatch(name, component, "ComboBox"); }
            return component;
        }

        if (expectedType.isTextbox()) {
            if (!component.getElementType().isTextbox()) { warnTypeMismatch(name, component, "Textbox"); }
            return component;
        }

        if (component.getElementType() != expectedType) { warnTypeMismatch(name, component, expectedType.name()); }

        return component;
    }

    protected DesktopMenuBar resolveMenuBar() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        DesktopElement app = session.getApp();
        DesktopElement menuBarElement = app.getComponent(APP_MENUBAR);
        requiresNotNull(menuBarElement, "No menu component found for current desktop session");
        if (menuBarElement instanceof DesktopMenuBar) {return (DesktopMenuBar) menuBarElement; }

        fail("Invalid component type for '" + APP_MENUBAR + "': " + menuBarElement.getClass());
        return null;
    }

    protected DesktopList resolveListElement(String name) {
        DesktopElement component = getRequiredElement(name, ListGrouping);
        DesktopList list = (DesktopList) component;
        list.inspect();
        return list;
    }

    protected int resolveFirstMatchedListIndex(DesktopList list, String contains) {
        requiresNotNull(list, "Invalid list object", list);
        requiresNotBlank(contains, "Invalid match-by criteria", contains);

        String delim = context.getTextDelim();
        return (StringUtils.contains(contains, delim)) ?
               list.findFirstMatchedRow(StringUtils.splitByWholeSeparator(contains, delim)) :
               list.findFirstMatchedRow(contains);
    }

    protected DesktopElement resolveCurrentTopMostWindow() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        // is current container a window?
        boolean isContainerTopMostWindow = false;

        DesktopElement container = getCurrentContainer();
        if (container != null && container.getElementType() == Window) {
            // topmost window test
            String xpath = StringUtils.substringBeforeLast(container.getXpath(), "]") + LOCATOR_ISTOPMOST + "]";
            try {
                WebElement topMostWindow = container.getDriver().findElement(By.xpath(xpath));
                if (topMostWindow != null) { isContainerTopMostWindow = true; }
            } catch (NoSuchElementException e) {
                // nope, this one is not topmost
                isContainerTopMostWindow = false;
            }
        }

        DesktopElement currentAppWindow = isContainerTopMostWindow ? container : session.getApp();
        if (currentAppWindow.getElement() == null) { currentAppWindow.refreshElement(); }
        return currentAppWindow;
    }

    protected String getCurrentTopMostWindowTitle(DesktopElement currentAppWindow) {
        try {
            WebElement windowElement = currentAppWindow.getElement();
            WebElement titleBar = windowElement.findElement(By.xpath(LOCATOR_DIALOG_TITLE));
            return titleBar.getAttribute("Name");
        } catch (NoSuchElementException e) {
            ConsoleUtils.error("No element in current window matched " + LOCATOR_DIALOG_TITLE + ": " + e.getMessage());
            return null;
        }
    }

    protected WebElement getApp() {
        DesktopSession session = getCurrentSession();
        if (session != null && session.getDriver() != null) {
            DesktopElement app = session.getApp();
            if (app != null) {
                if (app.getElement() == null) { app.refreshElement(); }
                return app.getElement();
            }
        }

        WebElement application = (WebElement) context.getObjectData(DESKTOP_APPLICATION);
        if (application == null) { application = findElement(DEFAULT_APP_LOCATOR); }
        return application;
    }

    protected WebElement findElement(String locator) {
        requires(StringUtils.isNotBlank(locator), "invalid locator", locator);

        By findBy = findBy(locator);
        if (findBy == null) { return null; }

        List<WebElement> matched = getDriver().findElements(findBy);
        return CollectionUtils.isEmpty(matched) ? null : matched.get(0);
    }

    protected WebElement findElement(WebElement container, String locator) {
        requiresNotNull(container, "Invalid container", container);
        requiresNotBlank(locator, "invalid locator", locator);

        By findBy = findBy(locator);
        requires(findBy != null, "Unknown/unsupported locator", locator);

        try {
            return container.findElement(findBy);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public List<WebElement> findElements(WebElement container, String locator) {
        requiresNotNull(container, "Invalid container", container);
        requiresNotBlank(locator, "invalid locator", locator);

        By findBy = findBy(locator);
        requires(findBy != null, "Unknown/unsupported locator", locator);

        try {
            return container.findElements(findBy);
        } catch (NoSuchElementException e) {
            return null;
        }

    }

    public List<WebElement> findElements(String locator) {
        requiresNotBlank(locator, "invalid locator", locator);

        By findBy = findBy(locator);
        try {
            return findBy != null ? getDriver().findElements(findBy) : null;
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public WebElement findFirstElement(String locator) {
        List<WebElement> elements = findElements(locator);
        return CollectionUtils.isEmpty(elements) ? null : elements.get(0);
    }

    protected static By findBy(String locator) {
        if (StringUtils.isBlank(locator)) { return null; }

        if (StringUtils.equals(locator, "*") ||
            StringUtils.startsWith(locator, "/") ||
            StringUtils.startsWith(locator, "./") ||
            StringUtils.startsWith(locator, "*[")) { return By.xpath(locator); }

        for (String key : SUPPORTED_FIND_BY.keySet()) {
            String findByKey = key + "=";
            if (StringUtils.startsWith(locator, findByKey)) {
                Class<By> byClass = (Class<By>) SUPPORTED_FIND_BY.get(key);
                try {
                    return byClass.getConstructor(String.class)
                                  .newInstance(treatQuoteString(StringUtils.substringAfter(locator, findByKey)));
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new RuntimeException("Unable to instantiate " + byClass + ": " + e.getMessage(), e);
                }
            }
        }

        return null;
    }

    protected WebElement waitForElement(WebElement parent, String locator, long maxWaitMs) {
        requires(maxWaitMs > 0, "invalid maxWaitMs", maxWaitMs);
        requiresNotBlank(locator, "invalid locator", locator);

        By by = findBy(locator);
        requiresNotNull(by, "Unsupported/unknown locator", locator);

        WebDriverWait wait = new WebDriverWait(getDriver(), maxWaitMs / 1000);
        WebElement waitFor = wait.until(driver -> {
            if (parent != null) {
                try {
                    return parent.findElement(by);
                } catch (Exception e) {
                    log(String.format("Error performing %s findElement(%s), retrying...", parent, by));
                    // keeps trying
                    return null;
                }
            } else {
                try {
                    return driver.findElements(by)
                                 .stream()
                                 .findFirst()
                                 .orElseThrow(() -> new NoSuchElementException("Cannot locate an element using " + by));
                } catch (Exception e) {
                    log(String.format("Error performing findElement(%s), retrying...", by));
                    // keeps trying
                    return null;
                }
            }
        });

        return waitFor != null && waitFor.isDisplayed() ? waitFor : null;
    }

    protected static WebElement getModalDialog(WebElement app) {
        if (app == null) {
            ConsoleUtils.log("Unable to reference application");
            return null;
        }

        // find dialog
        try {
            return app.findElement(findBy(LOCATOR_MODAL_DIALOG));
        } catch (WebDriverException e) {
            return null;
        }
    }

    protected String saveModalDialogText(WebElement modalDialog, String var) {
        List<WebElement> dialogs = modalDialog.findElements(findBy(LOCATOR_DIALOG_TEXT));
        if (CollectionUtils.isEmpty(dialogs)) { return null; }

        String processId = context.getStringData(DESKTOP_PROCESS_ID);

        final StringBuilder buffer = new StringBuilder();
        dialogs.forEach(dialog -> {
            // collect text from all dialog box, but only if they are from the same process id as current AUT
            if (StringUtils.isBlank(processId) ||
                StringUtils.equals(processId, findProcessId(dialog))) {
                buffer.append(StringUtils.trim(dialog.getAttribute("Name"))).append(lineSeparator());
            }
        });

        String text = StringUtils.trim(buffer.toString());
        context.setData(var, text);
        return text;
    }

    protected boolean isChecked(String name) {
        DesktopElement component = getRequiredElement(name, Any);
        if (component.getElementType() == Checkbox || component.getElementType() == Radio) {
            return component.isSelected();
        }
        fail("Specified element not a Checkbox or Radio: " + name);
        return false;
    }

    protected StepResult click(String name, ElementType expectedType) {
        DesktopElement component = getRequiredElement(name, expectedType);
        component.getElement().click();
        autoClearModalDialog(component);
        return StepResult.success("Element '" + name + "' clicked");
    }

    protected StepResult doubleClick(String name, ElementType expectedType) {
        DesktopElement component = getRequiredElement(name, expectedType);
        WebElement target = component.getElement();
        new Actions(winiumDriver).doubleClick(target).perform();
        autoClearModalDialog(component);
        return StepResult.success("Element '" + name + "' double-clicked");
    }

    protected StepResult clickOffset(WebElement elem, String xOffset, String yOffset) {
        requiresNotNull(elem, "Unable to reference target element", elem);
        requiresPositiveNumber(xOffset, "Invalid xOffset", xOffset);
        requiresPositiveNumber(yOffset, "Invalid yOffset", yOffset);

        int x = NumberUtils.toInt(xOffset);
        requires(x > 0, "Invalid xOffset", xOffset);

        int y = NumberUtils.toInt(yOffset);
        requires(y > 0, "Invalid yOffset", yOffset);

        Actions actions = new Actions(getDriver()).moveToElement(elem, x, y).click();
        actions.perform();
        return StepResult.success("Clicked offset (" + x + "," + y + ") from element " + elem);
    }

    // todo: not used?
    protected StepResult type(String name, ElementType expectedType, String... text) {
        requires(ArrayUtils.isNotEmpty(text), "at least one text parameter is required");

        DesktopElement component = getRequiredElement(name, expectedType);
        // combine shortcut sequences into 1 string for performance.
        String shortcut = TextUtils.toString(
            DesktopElement.parseTextInputWithShortcuts(TextUtils.toString(text, "", "", ""), true), "");

        try {
            component.getElement().click();
        } catch (WebDriverException e) {
            ConsoleUtils.error("Unable to click on component %s; might be ok...: %s", name, resolveErrorMessage(e));
        }

        if (StringUtils.isNotEmpty(shortcut)) { component.type(shortcut, false); }
        autoClearModalDialog(component.getXpath());
        return StepResult.success("text entered to element '" + name + "'");
    }

    public void
    type(WebElement elem, String text) {
        if (elem == null) { return; }

        // improve on speed.  when not using control key, we should just use winiumDriver.sendKey()
        boolean useAscii = context.getBooleanData(USE_ASCII_KEY_MAPPING, getDefaultBool(USE_ASCII_KEY_MAPPING));
        WiniumUtils.sendKey(getDriver(), elem, text, useAscii);
    }

    protected StepResult clear(String name, ElementType expectedType) {
        DesktopElement component = getRequiredElement(name, expectedType);
        if (component == null) { return StepResult.fail("Unable to derive component via '" + name + "'"); }
        return component.clear();
    }

    protected String getText(String name) { return StringUtils.trim(getRequiredElement(name, Any).getText()); }

    protected String getAttribute(String locator, String attribute) {
        requires(StringUtils.isNotBlank(attribute), "invalid attribute", attribute);

        WebElement element = findElement(locator);
        return element != null ? element.getAttribute(attribute) : null;
    }

    protected StepResult assertChildElementPresent(WebElement container, String locator) {
        requires(container != null, "Invalid container", container);
        requires(StringUtils.isNotBlank(locator), "Invalid locator", locator);

        WebElement elemUsername = container.findElement(findBy(locator));
        if (elemUsername == null) {
            return StepResult.fail("EXPECTED child element not found under " + container + " via " + locator);
        }
        return StepResult.success();
    }

    protected String findProcessId(String locator) {
        requires(StringUtils.isNotBlank(locator), "Invalid locator", locator);

        WebElement elem = findElement(locator);
        if (elem == null) { return null; }

        return findProcessId(elem);
    }

    protected String findProcessId(WebElement elem) {
        String processId = elem.getAttribute("ProcessId");
        if (StringUtils.isBlank(processId)) { return processId; }
        if (StringUtils.contains(processId, " (")) { processId = StringUtils.substringBefore(processId, " (");}
        return processId;
    }

    protected void syncSessionDataToContext(DesktopSession session) {
        if (session == null) {
            fail("desktop session object is null!");
            return;
        }

        context.setData(CURRENT_DESKTOP_SESSION, session);
        context.setData(DESKTOP_APPLICATION, session.getApp());
        context.setData(DESKTOP_PROCESS_ID, session.getProcessId());
        context.addScriptReferenceData("process id", session.getProcessId());

        String appVersion = StringUtils.defaultString(session.getApplicationVersion());
        String buildnum = context.getStringData(SCRIPT_REF_PREFIX + BUILD_NO, appVersion);

        if (StringUtils.isNotBlank(appVersion)) { context.addScriptReferenceData("app version", appVersion); }
        if (StringUtils.isNotBlank(buildnum)) { context.addScriptReferenceData(BUILD_NO, buildnum); }

        context.addScriptReferenceData("app", session.getAppId());
    }

    protected void syncContainerDataToContext(DesktopElement container, String label) {
        if (container == null) { fail("desktop container/form object is null!"); }
        if (StringUtils.isBlank(label)) { fail("desktop container/form name is missing!"); }

        context.setData(CURRENT_DESKTOP_CONTAINER, container);
        context.setData(CURRENT_DESKTOP_CONTAINER_NAME, label);
        context.addScenarioReferenceData("form", label);
    }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) as well */
    protected DesktopTable resolveTableElement(String name) {
        DesktopElement tableObject = getRequiredElement(name, Any);
        DesktopTable table = null;
        if (tableObject.getElementType() == Table) { table = (DesktopTable) tableObject; }
        if (tableObject.getElementType() == HierTable) { table = DesktopTable.toInstance(tableObject); }
        if (table == null) {
            fail("requested element '" + name + "' is not a " + Table + " but a " + tableObject.getElementType() + ".");
            return null;
        }

        if (CollectionUtils.isEmpty(table.headers) && table.columnCount == UNDEFINED) { table.scanStructure(); }
        return table;
    }

    protected void syncListDataToContext(DesktopElement list, String label) {
        if (list == null) { fail("desktop list object is null!"); }
        if (StringUtils.isBlank(label)) { fail("desktop list name is missing!"); }

        context.setData(CURRENT_DESKTOP_LIST, list);
        context.setData(CURRENT_DESKTOP_LIST_NAME, label);
    }

    protected void syncTableDataToContext(DesktopElement table, String label) {
        if (table == null) { fail("desktop table object is null!"); }
        if (StringUtils.isBlank(label)) { fail("desktop table name is missing!"); }

        context.setData(CURRENT_DESKTOP_TABLE, table);
        context.setData(CURRENT_DESKTOP_TABLE_NAME, label);
    }

    protected void syncTableRowDataToContext(String var, String tableName, int row, DesktopTableRow tableRow) {
        if (StringUtils.isBlank(tableName)) { fail("desktop table name is missing!"); }
        if (row < 0) { fail("desktop table row index (zero-based) is invalid: " + row); }
        if (tableRow == null || MapUtils.isEmpty(tableRow.getColumns())) {
            fail("desktop table row index (zero-based) is invalid: " + row);
            return;
        }

        context.setData(CURRENT_DESKTOP_TABLE_ROW, tableRow);
        context.setData(CURRENT_DESKTOP_TABLE_ROW_NAME, tableName + "." + row);
        context.setData(var, tableRow.toMetadata());
    }

    protected DesktopTable getCurrentTable() {
        Object obj = context.getObjectData(CURRENT_DESKTOP_TABLE);
        return obj instanceof DesktopTable ? (DesktopTable) obj : null;
    }

    protected DesktopList getCurrentList() {
        Object obj = context.getObjectData(CURRENT_DESKTOP_LIST);
        return obj instanceof DesktopList ? (DesktopList) obj : null;
    }

    protected StepResult saveListMetaData(String var, String name) {
        requiresValidAndNotReadOnlyVariableName(var);

        DesktopList list = resolveListElement(name);
        if (list == null) { return StepResult.fail("Unable to reference '" + name + "' as a list element."); }

        syncListDataToContext(list, name);
        context.setData(var, list.toMetaData());

        return StepResult.success("list '" + name + "' rescanned and metadata stored to '" + var + "'");
    }

    /**
     * (re)scan table structure and store table metadata to {@code var}.  This method will force rescanning to get the
     * latest row count.  Consequently it will clear out any previously harvested rows.  The latest table metadata will
     * be stored as {@code var}.
     * <p>
     * As of v3.7, we will support HierTable (aka ControlType.Tree) as well
     */
    protected StepResult saveTableMetaData(String var, String name, boolean rescan) {
        requiresValidAndNotReadOnlyVariableName(var);

        DesktopTable table = resolveTableElement(name);
        if (table == null) { return StepResult.fail("Unable to reference '" + name + "' as a table element."); }

        TableMetaData metaData = new TableMetaData();
        if (rescan) { metaData = table.scanStructure(); }
        syncTableDataToContext(table, name);
        context.setData(var, metaData);

        return StepResult.success("table " + (rescan ? "rescanned and " : "") + "metadata stored to '" + var + "'");
    }

    protected DesktopHierTable resolveHierTableElement(String name) {
        DesktopElement object = getRequiredElement(name, HierTable);
        if (object instanceof DesktopHierTable) {
            DesktopHierTable table = (DesktopHierTable) object;
            if (CollectionUtils.isEmpty(table.headers) && table.columnCount == UNDEFINED) { table.scanStructure(); }
            return table;
        } else {
            fail("requested element '" + name + "' is not a " + HierTable + " but a " + object.getElementType() + ".");
            return null;
        }
    }

    protected void syncHierTableDataToContext(DesktopElement table, String label) {
        if (table == null) { fail("desktop hierarchical table object is null!"); }
        if (StringUtils.isBlank(label)) { fail("desktop hierarchical table name is missing!"); }

        context.setData(CURRENT_DESKTOP_HIER_TABLE, table);
        context.setData(CURRENT_DESKTOP_HIER_TABLE_NAME, label);
    }

    protected DesktopHierTable getCurrentHierTable() {
        Object obj = context.getObjectData(CURRENT_DESKTOP_HIER_TABLE);
        return obj instanceof DesktopHierTable ? (DesktopHierTable) obj : null;
    }

    /**
     * (re)scan table structure and store table metadata to {@code var}.  This method will force rescanning to get the
     * latest row count.  Consequently it will clear out any previously harvested rows.  The latest table metadata will
     * be stored as {@code var}.
     */
    protected StepResult saveHierTableMetaData(String var, String name) {
        requiresValidAndNotReadOnlyVariableName(var);

        DesktopHierTable table = resolveHierTableElement(name);
        if (table == null) {
            return StepResult.fail("Unable to reference '" + name + "' as a hierarchical table element.");
        }

        syncHierTableDataToContext(table, name);
        context.setData(var, table.toMetaData());
        table.collapseAll();

        return StepResult.success("hierarchical table metadata stored to '" + var + "'");
    }

    protected void autoClearModalDialog(DesktopSession session) {
        if (session == null) { return; }

        DesktopElement app = session.getApp();
        if (app == null) { return; }

        autoClearModalDialog(app.getXpath());
    }

    protected void autoClearModalDialog(DesktopElement component) {
        if (component == null) { return; }
        autoClearModalDialog(component.getXpath());
    }

    public void autoClearModalDialog(String xpath) {
        if (!context.getBooleanData(DESKTOP_AUTO_CLEAR_MODAL_DIALOG, DEF_AUTO_CLEAR_MODAL_DIALOG)) { return; }
        if (StringUtils.isBlank(xpath)) { return; }

        String baseXpath = "/" + StringUtils.substringBefore(xpath.substring(1), "/");
        DesktopUtils.clearModalDialog(getDriver(), baseXpath);
    }

    private void setTableFocus(DesktopTable table) {
        boolean isCurrentTable = table == context.getObjectData(CURRENT_DESKTOP_TABLE);
        if (!isCurrentTable || context.getObjectData(CURRENT_DESKTOP_TABLE_ROW) == null) { return; }

        DesktopTableRow tableRow = (DesktopTableRow) context.getObjectData(CURRENT_DESKTOP_TABLE_ROW);
        if (tableRow == null) { return; }

        String currentEditColumn = context.getStringData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_NAME);
        if (StringUtils.isNotEmpty(currentEditColumn)) {
            WebElement editColumn = tableRow.getColumns().get(currentEditColumn);
            if (editColumn == null) { return; }

            ConsoleUtils.log("shortcut key was pressed.. now setting focus back to table at " + currentEditColumn);
            if (table.isTreeView) {
                winiumDriver.executeScript(toShortcuts("ESC"), editColumn);
            } else {
                editColumn.click();
            }
        }
        context.removeData(CURRENT_DESKTOP_TABLE_ROW);
    }

    private void warnTypeMismatch(String name, DesktopElement component, String expectedTypeDesc) {
        ConsoleUtils.log("WARNING: requested element '" + name + "' is not a " + expectedTypeDesc + " but a " +
                         component.getElementType() + ". This command might work anyways...");
    }

    private static Map<String, Class<? extends By>> initSupportedFindBys() {
        // reference: https://github.com/2gis/Winium.Desktop/wiki/Finding-Elements
        Map<String, Class<? extends By>> map = new HashMap<>();
        map.put("id", ById.class);
        map.put("class", ByClassName.class);
        map.put("name", ByName.class);
        map.put("xpath", ByXPath.class);
        return map;
    }

    private void tableRowsRangeCheck(int beginRow, int endRow) {
        if (beginRow > endRow) {
            throw new IllegalArgumentException("begin row (" + beginRow + " ) > end row (" + endRow + " )");
        }
        if (beginRow < 0) {
            throw new IllegalArgumentException(" begin row (" + beginRow + ") cannot be less than 0");
        }
    }
}
