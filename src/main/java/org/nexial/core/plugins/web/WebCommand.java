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

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.client.methods.HttpRequestBase;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.web.URLEncodingUtils;
import org.nexial.core.WebProxy;
import org.nexial.core.browsermob.ProxyHandler;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.NexialUrlInvokedEvent;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.TestStep;
import org.nexial.core.plugins.CanLogExternally;
import org.nexial.core.plugins.CanTakeScreenshot;
import org.nexial.core.plugins.RequireBrowser;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.plugins.base.ScreenshotUtils;
import org.nexial.core.plugins.ws.Response;
import org.nexial.core.plugins.ws.WsCommand;
import org.nexial.core.service.EventTracker;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.NativeInputHelper;
import org.nexial.core.utils.OutputFileUtils;
import org.nexial.core.utils.WebDriverUtils;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.*;
import org.openqa.selenium.WebDriver.Window;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.Coordinates;
import org.openqa.selenium.interactions.Locatable;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import net.lightbody.bmp.proxy.ProxyServer;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.jetty.http.HttpMessage;
import net.lightbody.bmp.proxy.jetty.http.HttpRequest;

import static java.io.File.separator;
import static java.lang.Thread.sleep;
import static org.apache.commons.lang3.SystemUtils.IS_OS_MAC;
import static org.nexial.core.NexialConst.BrowserType.safari;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.SystemVariables.*;
import static org.nexial.core.plugins.ws.WebServiceClient.hideAuthDetails;
import static org.nexial.core.utils.CheckUtils.*;
import static org.openqa.selenium.Keys.BACK_SPACE;
import static org.openqa.selenium.Keys.TAB;

public class WebCommand extends BaseCommand implements CanTakeScreenshot, CanLogExternally, RequireBrowser {
    private static final String SUFFIX_LOCATOR = ".locator";
    protected Browser browser;
    protected WebDriver driver;
    protected JavascriptExecutor jsExecutor;
    protected TakesScreenshot screenshot;

    // helper
    protected LocatorHelper locatorHelper;
    protected FrameHelper frameHelper;
    protected AlertCommand alert;
    protected CookieCommand cookie;
    protected WsCommand ws;
    protected TableHelper tableHelper;
    protected boolean logToBrowser;

    @Override
    public Browser getBrowser() { return browser; }

    @Override
    public void setBrowser(Browser browser) { this.browser = browser; }

    @Override
    public void init(ExecutionContext context) {
        super.init(context);

        // todo: revisit to handle proxy
        if (context.getBooleanData(OPT_PROXY_ENABLE, false)) {
            ProxyHandler proxy = new ProxyHandler();
            proxy.setContext(context);
            proxy.startProxy();
            browser.setProxy(proxy);
        }

        if (!context.isDelayBrowser()) { initWebDriver(); }

        ws = (WsCommand) context.findPlugin("ws");
        ws.init(context);

        locatorHelper = new LocatorHelper(this);

        long browserStabilityWaitMs = deriveBrowserStabilityWaitMs(context);
        if (context.isVerbose()) { log("default browser stability wait time is " + browserStabilityWaitMs + " ms"); }

        // todo: consider this http://seleniumhq.github.io/selenium/docs/api/javascript/module/selenium-webdriver/lib/logging.html
        logToBrowser = !browser.isRunChrome() &&
                       context.getBooleanData(OPT_BROWSER_CONSOLE_LOG, getDefaultBool(OPT_BROWSER_CONSOLE_LOG));
        tableHelper = new TableHelper(this);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (browser != null && browser.getDriver() != null) {
            try { browser.getDriver().quit(); } catch (Exception e) { }
        }
    }

    @Override
    public String getTarget() { return "web"; }

    public StepResult assertTextOrder(String locator, String descending) {
        return locatorHelper.assertTextOrder(locator, descending);
    }

    public StepResult assertFocus(String locator) {
        String expected = getAttributeValue(locator, "id");
        if (StringUtils.isBlank(expected)) {
            return StepResult.fail("element found via " + locator + " does not contain ID attribute");
        }

        try {
            String actual = findActiveElementId();
            if (StringUtils.isBlank(actual)) {
                // since our asserted element has id, this must not be our element..
                return StepResult.fail("active element does not contain ID attribute");
            }

            if (!StringUtils.equals(expected, actual)) {
                return StepResult.fail("EXPECTED element '" + expected + "' not focused; " +
                                       "focused element ID is '" + actual + "'");
            }
        } catch (NoSuchElementException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        }

        return StepResult.success("validated EXPECTED focus on locator '" + locator + "'");
    }

    public StepResult assertNotFocus(String locator) {
        String expected = getAttributeValue(locator, "id");
        if (StringUtils.isBlank(expected)) {
            return StepResult.fail("EXPECTS element does not contain ID attribute");
        }

        try {
            String actual = findActiveElementId();
            if (StringUtils.isBlank(actual) || !StringUtils.equals(expected, actual)) {
                return StepResult.success("validated no-focus on locator '" + locator + "' as EXPECTED");
            }
        } catch (NoSuchElementException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        }

        return StepResult.fail("element '" + expected + "' EXPECTS not to be focused but it is.");
    }

    // todo need to test
    public StepResult focus(String locator) {
        requiresNotBlank(locator, "Invalid locator", locator);

        try {
            focus(toElement(locator));
            return StepResult.success("SUCCESSFULLY focused on element '" + locator + "'");
        } catch (NullPointerException e) {
            return StepResult.fail("No element found via '" + locator + "'");
        } catch (IllegalArgumentException e) {
            return StepResult.fail("FAIL to focus because " + e.getMessage() + ": " + locator);
        }
    }

    public StepResult assertLinkByLabel(String label) {
        return assertElementPresent("//a[text()=" + locatorHelper.normalizeXpathText(label) + "]");
    }

    public StepResult assertChecked(String locator) { return new StepResult(isChecked(locator)); }

    public StepResult assertNotChecked(String locator) { return new StepResult(!isChecked(locator)); }

    public StepResult checkAll(String locator) {
        String script = "if (arguments[0].hasAttribute('type','checkbox') && !arguments[0].hasAttribute('checked')) {" +
                        "   arguments[0].click(); " +
                        "}";
        return execJsOverFreshElements(locator, script, (int) context.getPollWaitMs()) ?
               StepResult.success("CheckBox elements (" + locator + ") are checked") :
               StepResult.fail("Check FAILED on element(s) '" + locator + "'");
    }

    public StepResult uncheckAll(String locator) {
        String script = "if (arguments[0].hasAttribute('type','checkbox') && arguments[0].hasAttribute('checked')) {" +
                        "   arguments[0].click();" +
                        "}";
        return execJsOverFreshElements(locator, script, (int) context.getPollWaitMs()) ?
               StepResult.success("CheckBox elements (" + locator + ") are unchecked") :
               StepResult.fail("Uncheck FAILED on element(s) '" + locator + "'");
    }

    /** treated all matching elements as checkbox, radio or select-option and toggle their current 'selected' status */
    public StepResult toggleSelections(String locator) {
        List<WebElement> elements = findElements(locator);
        if (CollectionUtils.isEmpty(elements)) {
            return StepResult.fail("No elements matching locator '" + locator + "'");
        }

        for (WebElement element : elements) {
            if (element == null) { continue; }
            element.click();
        }

        return StepResult.success("Successfully toggled " + elements.size() +
                                  " elements matching locator '" + locator + "'");
    }

    @NotNull
    public StepResult select(String locator, String text) {
        Select select = getSelectElement(locator);
        if (StringUtils.isBlank(text)) {
            select.deselectAll();
            return StepResult.success("deselected ALL from '" + locator + "' since no text was provided");
        } else {
            select.selectByVisibleText(text);
            return StepResult.success("selected '" + text + "' from '" + locator + "'");
        }
    }

    @NotNull
    public StepResult deselect(String locator, String text) {
        Select select = getSelectElement(locator);
        if (StringUtils.isNotBlank(text)) {
            select.deselectByVisibleText(text);
            return StepResult.success("deselected '" + text + "' from '" + locator + "'");
        } else {
            return StepResult.success("NO action performed on '" + locator + "' since no text was provided");
        }
    }

    @NotNull
    public StepResult selectMulti(String locator, String array) {
        requires(StringUtils.isNotBlank(array), "invalid text array", array);

        Select select = getSelectElement(locator);

        List<String> labels = TextUtils.toList(array, context.getTextDelim(), true);
        for (String label : labels) { select.selectByVisibleText(label); }

        return StepResult.success("selected '" + array + "' on widgets matching '" + locator + "'");
    }

    @NotNull
    public StepResult selectMultiOptions(String locator) { return clickAll(locator); }

    @NotNull
    public StepResult deselectMulti(String locator, String array) {
        requires(StringUtils.isNotBlank(array), "invalid text array", array);

        Select select = new Select(toElement(locator));

        List<String> labels = TextUtils.toList(array, context.getTextDelim(), true);
        for (String label : labels) { select.deselectByVisibleText(label); }

        return StepResult.success("deselected '" + array + "' on widgets matching '" + locator + "'");
    }

    /**
     * assert that an element can be found by {@code locator} and it contains {@code text}.  The textual content is
     * the visible (i.e. not hidden by CSS) innerText of this element, including sub-elements, without any leading
     * or trailing whitespace.
     * <p/>
     * <b>Note: it is possible to match more than 1 element by using {@code locator} and {@code text} alone</b>
     */
    @NotNull
    public StepResult assertElementByText(String locator, String text) {
        requires(StringUtils.isNotBlank(locator), "invalid locator", locator);
        requires(StringUtils.isNotBlank(text), "invalid text to search by", text);

        List<WebElement> matches = findElements(locator);
        if (CollectionUtils.isEmpty(matches)) { return StepResult.fail("No element found via xpath " + locator); }

        String expected = StringUtils.trim(text);
        for (WebElement elem : matches) {
            String actual = StringUtils.trim(elem.getText());
            if (StringUtils.equals(expected, actual)) {
                return StepResult.success("validated locator '" + locator + "' by text '" + text + "' as EXPECTED");
            }
        }

        return StepResult.fail("No element with text '" + text + "' can be found.");
    }

    @NotNull
    public StepResult assertElementNotPresent(String locator) {
        try {
            return locatorHelper.assertElementNotPresent(locator);
        } catch (NoSuchElementException | TimeoutException e) {
            // that's fine.  we expected that..
            return StepResult.success("Element as represented by " + locator + " is not available");
        }
    }

    @NotNull
    public StepResult assertElementCount(String locator, String count) {
        return locatorHelper.assertElementCount(locator, count);
    }

    @NotNull
    public StepResult assertElementPresent(String locator) {
        if (isElementPresent(locator)) {
            return StepResult.success("EXPECTED element '" + locator + "' found");
        } else {
            ConsoleUtils.log("Expected element not found at " + locator);
            return StepResult.fail("Expected element not found at " + locator);
        }
    }

    /**
     * assert multiple "element presence" via prefix. So that we can perform multiple validations in one go. For example,
     * <pre>
     * LoginForm.username.locator   css=#username
     * LoginForm.password.locator   css=#password
     * LoginForm.submit.locator     css=button.loginSubmit
     * </pre>
     *
     * One can invoke validation across all "LoginForm" elements, which in this case would be 3.
     *
     * "LoginForm" is custom prefix. ".locator" is the required suffix.
     */
    @NotNull
    public StepResult assertElementsPresent(String prefix) {
        requiresNotBlank(prefix, "Invalid prefix", prefix);

        Map<String, String> locators = context.getDataByPrefix(prefix);
        if (MapUtils.isEmpty(locators)) {
            return StepResult.fail("No data variables found via prefix '" + prefix + "'");
        }

        String runId = context.getRunId();
        boolean allPassed = true;
        StringBuilder logs = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        int locatorsFound = 0;

        Set<String> keys = locators.keySet();
        for (String key : keys) {
            if (!key.endsWith(SUFFIX_LOCATOR)) { continue; }

            String name = StringUtils.substringBefore(key, SUFFIX_LOCATOR);
            String locator = locators.get(key);
            locatorsFound++;

            boolean found = true;
            String message = "[" + name + "] ";

            try {
                StepResult isPresent = assertElementPresent(locator);
                if (isPresent.isSuccess()) {
                    message += "found via '" + locator + "'";
                    ConsoleUtils.log(runId, message);
                } else {
                    found = false;
                    message += "NOT FOUND via '" + locator + "'";
                    ConsoleUtils.error(runId, message);
                }
            } catch (WebDriverException e) {
                found = false;
                message += "NOT FOUND via '" + locator + "': " + WebDriverExceptionHelper.resolveErrorMessage(e);
                ConsoleUtils.error(runId, message);
            } catch (Throwable e) {
                found = false;
                message += "NOT FOUND via '" + locator + "': " + e.getMessage();
                ConsoleUtils.error(runId, message);
            }

            logs.append(message).append("\n");
            if (!found) {
                allPassed = false;
                errors.append(message).append("\n");
            }
        }

        if (locatorsFound < 1) {
            return StepResult.fail("No data variables found via prefix '" + prefix + "' contains the required " +
                                   "'" + SUFFIX_LOCATOR + "' suffix");
        }

        String message = logs.toString();
        String errorsFound = errors.toString();

        // at least print errors.. unless verbose is true
        if (context.isVerbose()) {
            log(message);
            ConsoleUtils.log(message);
        } else if (!allPassed) {
            log(errorsFound);
        }

        return allPassed ? StepResult.success(message) : StepResult.fail(errorsFound);
    }

    public StepResult saveTextSubstringAfter(String var, String locator, String delim) {
        return saveTextSubstring(var, locator, delim, null);
    }

    public StepResult saveTextSubstringBefore(String var, String locator, String delim) {
        return saveTextSubstring(var, locator, null, delim);
    }

    public StepResult saveTextSubstringBetween(String var, String locator, String start, String end) {
        return saveTextSubstring(var, locator, start, end);
    }

    /**
     * validate the presence of an element (possibly elements) based on a set of attributes.  The attributes are
     * expressed by a list of name-value pairs that represent the xpath filtering criteria, with the following rules:
     * <ol>
     * <li>Order is important! xpath is constructed in the order specified via the {@code nameValues} list</li>
     * <li>The pairs in the {@code nameValues} list are separated by pipe character ({@code | }) or newline</li>
     * <li>The name/value within each pair is separated by equals character ({@code = })</li>
     * <li>Use * in value to signify inexact search.  For example, {@code @class=button*} => filter by class
     * attribute where the class name starts with '{@code button}'</li>
     * </ol>
     * <p/>
     * For example (Java code),
     * <pre>
     * String nameValues = "@class=digit*|text()=Save|@id=*save*";
     * StepResult result = assertElementPresentByAttribs(nameValues);
     * </pre>
     * Same example (spreadsheet),<br/>
     * <pre>
     * assertElementByAttributes(nameValues) |  @class=jumbo*|text()=Save|@id=*save*
     *      - OR -
     * assertElementByAttributes(nameValues) |  @class=jumbo*
     *                                          text()=Save
     *                                          &#0064;id=*save*
     * </pre>
     * <p/>
     * The above code will construct an XPATH as follows:
     * <pre>
     * //*[ends-with(@class,'digit') and text()='Save' and contains(@id,'save')]
     * </pre>
     * <p/>
     * This XPATH is then used to check if the current page contains an element that would satisfy the
     * specified filtering.
     * <br/>
     * <p/>
     * <b>NOTE: It is possible for the resulting XPATH to return more than 1 element!</b>
     *
     * @see LocatorHelper#resolveFilteringXPath(String)
     * @see #assertElementPresent(String)
     */
    public StepResult assertElementByAttributes(String nameValues) {
        requires(StringUtils.isNotBlank(nameValues), "empty name/value pair", nameValues);
        requires(StringUtils.contains(nameValues, "="), "invalid name/value pair", nameValues);
        return assertElementPresent(locatorHelper.resolveFilteringXPath(nameValues));
    }

    public StepResult waitForElementPresent(final String locator) {
        return new StepResult(waitForCondition(context.getPollWaitMs(), object -> isElementPresent(locator)));
    }

    public StepResult saveValue(String var, String locator) {
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);
        updateDataVariable(var, getValue(locator));
        return StepResult.success("stored value of '" + locator + "' as ${" + var + "}");
    }

    public StepResult saveValues(String var, String locator) {
        requiresValidVariableName(var);
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);

        String[] values = collectValueList(findElements(locator));
        if (ArrayUtils.isNotEmpty(values)) {
            context.setData(var, values);
        } else {
            context.removeData(var);
        }

        return StepResult.success("stored values of '" + locator + "' as ${" + var + "}");
    }

    /**
     * save the value of {@code attrName} of the element matching {@code locator} to a variable named {@code var}
     */
    public StepResult saveAttribute(String var, String locator, String attrName) {
        requiresValidVariableName(var);
        requiresNotBlank(locator, "invalid locator", locator);
        requiresNotBlank(attrName, "invalid attribute name", attrName);

        WebElement element = findElement(locator);
        if (element == null) { return StepResult.fail("Element NOT found via '" + locator + "'"); }

        String actual = element.getAttribute(attrName);
        if (actual == null) {
            context.removeData(var);
            return StepResult.success("the matching element does not contain attribute '" + attrName + "'");
        } else {
            updateDataVariable(var, actual);
            return StepResult.success("attribute '" + attrName + "' for '" + locator + "' saved to '" + var + "'");
        }
    }

    /**
     * save the value of {@code attrName} of all elements matching {@code locator} to a variable named {@code var}
     */
    public StepResult saveAttributeList(String var, String locator, String attrName) {
        requiresValidVariableName(var);
        requiresNotBlank(locator, "invalid locator", locator);
        requiresNotBlank(attrName, "invalid attribute name", attrName);

        List<WebElement> elements = findElements(locator);
        if (CollectionUtils.isEmpty(elements)) { return StepResult.fail("No element matched to '" + locator + "'"); }

        String[] attrValues = elements.stream().map(element -> element.getAttribute(attrName)).toArray(String[]::new);

        if (ArrayUtils.isEmpty(attrValues)) {
            context.removeData(var);
            return StepResult.success("matching elements do not contain attribute '" + attrName + "'");
        } else {
            context.setData(var, attrValues);
            return StepResult.success("attribute '" + attrName + "' for elements that matched '" + locator +
                                      "' saved to '" + var + "'");
        }
    }

    public StepResult saveCount(String var, String locator) {
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);
        context.setData(var, getElementCount(locator));
        return StepResult.success("stored matched count of '" + locator + "' as ${" + var + "}");
    }

    public StepResult saveTextArray(String var, String locator) {
        requiresValidVariableName(var);

        String[] textArray = collectTextList(locator);
        if (ArrayUtils.isNotEmpty(textArray)) {
            context.setData(var, textArray);
        } else {
            context.removeData(var);
        }

        return StepResult.success("stored content of '" + locator + "' as '" + var + "'");
    }

    public StepResult saveText(String var, String locator) {
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);
        String text = getElementText(locator);
        updateDataVariable(var, text);
        return StepResult.success(StringUtils.isEmpty(text) ?
                                  "no content found via locator '" + locator + "'" :
                                  "stored content of '" + locator + "' as ${" + var + "}");
    }

    public StepResult saveElement(String var, String locator) {
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);
        context.setData(var, findElement(locator));
        return StepResult.success("element '" + locator + "' found and stored as ${" + var + "}");
    }

    public StepResult saveElements(String var, String locator) {
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid property", var);
        context.setData(var, findElements(locator));
        return StepResult.success("elements '" + locator + "' found and stored as ${" + var + "}");
    }

    public StepResult assertIECompatMode() {
        //ie only functionality; not for chrome,ff,safari
        if (!browser.isRunIE()) { return StepResult.success("not applicable to non-IE browser"); }
        if (isIENativeMode()) {
            return StepResult.fail("EXPECTS IE Compatibility Mode, but browser runs at native mode found");
        } else {
            return StepResult.success("browser runs in IE Compatibility Mode");
        }
    }

    public StepResult assertIENativeMode() {
        //ie only functionality; not for chrome,ff,safari
        if (!browser.isRunIE()) { return StepResult.success("not applicable to non-IE browser"); }
        if (isIENativeMode()) {
            return StepResult.success("browser runs in native Mode");
        } else {
            return StepResult.fail("EXPECTS native mode, but browser runs at Compatibility Mode found");
        }
    }

    public StepResult assertContainCount(String locator, String text, String count) {
        return locatorHelper.assertContainCount(locator, text, count);
    }

    public StepResult assertTextCount(String locator, String text, String count) {
        return locatorHelper.assertTextCount(locator, text, count);
    }

    public StepResult assertTextList(String locator, String list, String ignoreOrder) {
        return locatorHelper.assertTextList(locator, list, ignoreOrder);
    }

    public StepResult assertTextContains(String locator, String text) {
        requires(StringUtils.isNotBlank(text), "empty text is not allowed", text);
        String elementText = getElementText(locator);
        return elementText == null ?
               StepResult.fail("Invalid locator '" + locator + "'; no text found") :
               assertContains(elementText, text);
        // if (lenientContains(elementText, text, false)) {
        //     return StepResult.success("validated text '" + elementText + "' contains '" + text + "'");
        // } else {
        //     return StepResult.fail("Expects \"" + text + "\" be contained in \"" + elementText + "\"");
        // }
    }

    public StepResult assertTextNotContains(String locator, String text) {
        requires(StringUtils.isNotBlank(text), "empty text is not allowed", text);
        String elementText = getElementText(locator);
        return elementText == null ?
               StepResult.fail("Invalid locator '" + locator + "'; no text found") :
               assertNotContains(elementText, text);
    }

    public StepResult assertNotText(String locator, String text) {
        assertNotEquals(text, getElementText(locator));
        return StepResult.success("validated text '" + text + "' not found in '" + locator + "'");
    }

    public StepResult waitForTextPresent(final String text) {
        requires(StringUtils.isNotBlank(text), "invalid text", text);
        return new StepResult(waitForCondition(context.getPollWaitMs(), object -> isTextPresent(text)));
    }

    public StepResult assertText(String locator, String text) {
        assertEquals(text, getElementText(locator));
        return StepResult.success();
    }

    public StepResult assertTextPresent(String text) {
        requires(StringUtils.isNotBlank(text), "invalid text", text);
        String msgPrefix = "EXPECTED text '" + text + "' ";

        // selenium.isTextPresent() isn't cutting it when text contains spaces or line breaks.
        //if (selenium.isTextPresent(text)) { }

        // text might contain single quote; hence use resolveContainLabelXpath()
        StepResult result = assertElementPresent(locatorHelper.resolveContainLabelXpath(text));
        if (result.failed()) {
            return StepResult.fail(msgPrefix + "not found");
        } else {
            return StepResult.success(msgPrefix + "found");
        }
    }

    public StepResult assertTextNotPresent(String text) {
        requiresNotBlank(text, "invalid text", text);
        String msgPrefix = "unexpected text '" + text + "' ";

        // text might contain single quote; hence use resolveContainLabelXpath()

        if (isTextPresent(text)) {
            return StepResult.fail(msgPrefix + "FOUND");
        } else {
            return StepResult.success(msgPrefix + "not found");
        }
    }

    public StepResult assertTextMatches(String text, String minMatch, String scrollTo) {
        requiresNotBlank(text, "Invalid text to search", text);
        requiresPositiveNumber(minMatch, "index must be a positive number", minMatch);

        String locator = locatorHelper.resolveContainLabelXpath(text);
        int atLeast = NumberUtils.toInt(minMatch);
        boolean alsoScrollTo = BooleanUtils.toBoolean(scrollTo);

        List<WebElement> matches = findElements(locator);
        if (CollectionUtils.isEmpty(matches) && atLeast > 0) {
            return StepResult.fail("'" + text + "' not found but at least " + minMatch + " matches required");
        }

        int matchCount = matches.size();

        if (atLeast == 0) {
            return StepResult.fail(matchCount + " matches of '" + text + "' found but no matches is expected");
        }

        if (matchCount < atLeast) {
            return StepResult.fail(minMatch + " matches of '" + text + "' required but only " + matchCount + " found");
        }

        if (alsoScrollTo) {
            WebElement scrollTarget = matches.get(atLeast - 1);
            if (scrollTarget instanceof Locatable) {
                Locatable target = (Locatable) scrollTarget;
                if (!scrollTo(target)) {
                    log("At least " + minMatch + " matches of text '" + text + "' was found, but scrolling to the " +
                        "last expected element was not possible due to its lack of page coordinates");
                } else {
                    highlight(scrollTarget);
                }
            }
        }

        return StepResult.success("At least " + minMatch + " matches of text '" + text + "' was found");
    }

    public StepResult assertOneMatch(String locator) {
        try {
            WebElement matched = findExactlyOneElement(locator);
            if (matched != null) {
                return StepResult.success("Found 1 element via locator '" + locator + "'");
            } else {
                return StepResult.fail("Unable to find matching element via locator '" + locator + "'");
            }
        } catch (IllegalArgumentException e) {
            return StepResult.fail(e.getMessage());
        }
    }

    public StepResult assertTable(String locator, String row, String column, String text) {
        return tableHelper.assertTable(locator, row, column, text);
    }

    public StepResult saveTableAsCsv(String locator, String nextPageLocator, String file) {
        return tableHelper.saveTableAsCsv(locator, nextPageLocator, file);
    }

    /**
     * This one is different from {@link #saveTableAsCsv(String, String, String)} in that it does NOT rely on
     * conventional HTML table structure. Instead, this method uses {@code headers} to represent the "header"
     * cells, the {@code rows} to represent the pattern of a "data" row and the {@code cells} as the
     * relative path of a "data" cell (hierarchically contained within a row).
     *
     * Optionally, the {@code nextPage} is used to forward to the "page" of the table data. If provided, this
     * method will forward to the next page of data AFTER the current page of table data is collected. Furthermore, this
     * method will keep forward to the next page of table data until the element represented by the
     * {@code nextPage} is either disabled or no longer visible.
     *
     * Collected table data will be saved as CSV into {@code file}. {@code headers} is optional; if it is not
     * specified, then the target {@code file} will not contain header either.
     */
    public StepResult saveDivsAsCsv(String headers, String rows, String cells, String nextPage, String file) {
        return tableHelper.saveDivsAsCsv(headers, rows, cells, nextPage, file);
    }

    public StepResult assertValue(String locator, String value) {
        assertEquals(value, getValue(locator));
        return StepResult.success();
    }

    public StepResult assertValueOrder(String locator, String descending) {
        return locatorHelper.assertValueOrder(locator, descending);
    }

    public StepResult assertAttribute(String locator, String attrName, String value) {
        try {
            String actual = getAttributeValue(locator, attrName);
            ConsoleUtils.log("Element '" + locator + "' attribute '" + attrName + "' yielded '" + actual + "'");
            if (actual == null) {
                boolean expectsNull = context.isNullValue(value);
                return new StepResult(expectsNull,
                                      "Attribute '" + attrName + "' of element '" + locator + "' is null/missing " +
                                      (expectsNull ? "as EXPECTED" : "but EXPECTS " + value), null);
            }

            return assertEqual(value, actual);
        } catch (NoSuchElementException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        }
    }

    public StepResult assertAttributePresent(String locator, String attrName) {
        return assertAttributePresentInternal(locator, attrName, true);
    }

    public StepResult assertAttributeNotPresent(String locator, String attrName) {
        return assertAttributePresentInternal(locator, attrName, false);
    }

    public StepResult assertAttributeContains(String locator, String attrName, String contains) {
        return assertAttributeContainsInternal(locator, attrName, contains, true);
    }

    public StepResult assertAttributeNotContains(String locator, String attrName, String contains) {
        return assertAttributeContainsInternal(locator, attrName, contains, false);
    }

    public StepResult assertCssNotPresent(String locator, String property) {
        requiresNotBlank(property, "invalid css property", property);

        String actual = getCssValue(locator, property);
        return StringUtils.isEmpty(actual) ?
               StepResult.success("No CSS property '" + property + "' found, as EXPECTED") :
               StepResult.fail("CSS property '" + property + "' (" + actual + ") found with UNEXPECTED value '" +
                               actual + "'");
    }

    public StepResult assertCssPresent(String locator, String property, String value) {
        requiresNotBlank(property, "invalid css property", property);

        String actual = getCssValue(locator, property);
        if (context.isVerbose()) { log("CSS property '" + property + "' for locator '" + locator + "' is " + actual); }

        if (StringUtils.isEmpty(actual) && StringUtils.isEmpty(value)) {
            return StepResult.success("no value found for CSS property '" + property + "' as EXPECTED");
        }

        value = StringUtils.lowerCase(StringUtils.trim(value));

        return StringUtils.equals(actual, value) ?
               StepResult.success("CSS property '" + property + "' contains EXPECTED value") :
               StepResult.fail("CSS property '" + property + "' (" + actual + ") DOES NOT contain expected value: '" +
                               value + "'");
    }

    public StepResult assertVisible(String locator) {
        String prefix = "EXPECTED visible element '" + locator + "'";
        return isVisible(locator) ? StepResult.success(prefix + " found") : StepResult.fail(prefix + " not found");
    }

    public StepResult assertNotVisible(String locator) {
        return !isVisible(locator) ?
               StepResult.success("element '" + locator + " NOT visible as EXPECTED.") :
               StepResult.fail("EXPECTS element as NOT visible but it is.");
    }

    public StepResult assertAndClick(String locator, String label) {
        requires(StringUtils.isNotBlank(label), "invalid label");

        StepResult result = assertElementByText(locator, label);
        if (result.failed()) {
            log("text/label for '" + label + "' not found at " + locator);
            return result;
        }

        // new locator should either add "text() = ..." or concatenate the "text() = ..." clause to existing filter
        String locatorWithText;
        if (StringUtils.endsWith(locator, "]")) {
            locatorWithText = StringUtils.substringBeforeLast(locator, "]") + " and ";
        } else {
            locatorWithText = locator + "[";
        }
        locatorWithText += "text() = " + locatorHelper.normalizeXpathText(label) + "]";

        result = click(locatorWithText);
        if (result.failed()) { log("text/label for '" + label + "' not clickable at " + locator); }
        return result;
    }

    public StepResult click(String locator) { return clickInternal(locator); }

    /**
     * click on all matching elements. Useful to check/uncheck "fake" options disguised as {@literal <div>} tags.
     *
     * Internally used by {@link #selectMultiOptions(String)} since both are functionally equivalent.
     */
    public StepResult clickAll(String locator) {
        List<WebElement> elements;
        try {
            elements = findElements(locator);
            if (CollectionUtils.isEmpty(elements)) {
                return StepResult.success("No matching element via '" + locator + "'");
            }
        } catch (TimeoutException e) {
            return StepResult.fail("Unable to find element via '" + locator + "' within allotted time");
        }

        for (WebElement elem : elements) { clickInternal(elem); }

        return StepResult.success("clicked " + elements.size() + " element(s) via '" + locator + "'");
    }

    public StepResult clickWithKeys(String locator, String keys) {
        requiresNotBlank(locator, "locator must not be empty", locator);
        WebElement element = toElement(locator);
        if (element == null) {
            return StepResult.fail("Unable to find element via locator '" + locator);
        }

        // if keys not specified, it is Equivalent to : web.click()
        if (StringUtils.isBlank(keys)) { return clickInternal(element); }

        Actions actions = WebDriverUtils.sendKeysActions(driver, element, keys);
        if (actions != null) { actions.build().perform(); }

        return StepResult.success("clicked element '" + locator + "'");
    }

    public StepResult clickAndWait(String locator, String waitMs) {
        boolean isNumber = NumberUtils.isDigits(waitMs);
        long waitMs1 = isNumber ? (long) NumberUtils.toDouble(waitMs) : context.getPollWaitMs();

        StepResult result = clickInternal(locator);
        if (result.failed()) { return result; }

        waitForBrowserStability(waitMs1);
        if (!isNumber) {
            return StepResult.warn("invalid waitMs: " + waitMs + ", default to " + waitMs1);
        } else {
            return StepResult.success("clicked-and-waited '" + locator + "'");
        }
    }

    public StepResult clickByLabel(String label) { return clickByLabelAndWait(label, context.getPollWaitMs() + ""); }

    public StepResult clickByLabelAndWait(String label, String waitMs) {
        String xpath = locatorHelper.resolveLabelXpath(label);
        StepResult result = assertOneMatch(xpath);
        if (result.failed()) { return result; }

        return clickAndWait(xpath, waitMs);
    }

    public StepResult clickOffset(String locator, String x, String y) {
        requiresPositiveNumber(x, "Invalid value for x", x);
        requiresPositiveNumber(y, "Invalid value for y", y);

        WebElement element;
        try {
            List<WebElement> matches = findElements(locator);
            element = CollectionUtils.isEmpty(matches) ? null : matches.get(0);
            if (element == null) { return StepResult.fail("No element via locator '" + locator + "'"); }

            ConsoleUtils.log("clicking '" + locator + "'...");
            highlight(element);

            int offsetX = NumberUtils.toInt(x);
            int offsetY = NumberUtils.toInt(y);
            new Actions(driver).moveToElement(element, offsetX, offsetY).click().build().perform();
            return StepResult.success("clicked on web element at offset (" + x + "," + y + ")");
        } catch (TimeoutException e) {
            return StepResult.fail("Unable to find element via locator '" + locator + "' within allotted time");
        } catch (WebDriverException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        } catch (Exception e) {
            return StepResult.fail(e.getMessage(), e);
        } finally {
            // could have alert text...
            alert.preemptiveDismissAlert();
        }
    }

    public StepResult doubleClickByLabel(String label) {
        return doubleClickByLabelAndWait(label, context.getPollWaitMs() + "");
    }

    public StepResult doubleClickByLabelAndWait(String label, String waitMs) {
        if (browser.isRunSafari()) { return StepResult.fail("double-click not supported by Safari"); }

        String xpath = locatorHelper.resolveLabelXpath(label);
        StepResult result = assertOneMatch(xpath);
        if (result.failed()) { return result; }

        return doubleClickAndWait(xpath, waitMs);
    }

    public StepResult doubleClick(String locator) {
        StepResult result = doubleClickAndWait(locator, context.getPollWaitMs() + "");
        if (result.failed()) { return result; }
        return StepResult.success("double-clicked '" + locator + "'");
    }

    public StepResult rightClick(String locator) {
        WebElement element = toElement(locator);
        scrollIntoView(element);
        highlight(element);
        new Actions(driver).moveToElement(element).contextClick(element).build().perform();
        return StepResult.success("right-clicked on '" + locator + "'");
    }

    public StepResult dismissInvalidCert() {
        wait("2000");
        if (!browser.isRunIE() && !browser.isRunFireFox()) {
            return StepResult.success("dismissInvalidCert(): Not applicable to " + browser);
        }

        ensureReady();
        String title = driver.getTitle();
        if (!StringUtils.contains(title, "Certificate Error: Navigation Blocked") &&
            !StringUtils.contains(title, "Untrusted Connection")) {
            return StepResult.success("dismissInvalidCert(): Invalid certificate message not found " +
                                      browser);
        }

        try {
            driver.get("javascript:document.getElementById('overridelink').click();");
        } catch (WebDriverException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        } catch (Exception e) {
            return StepResult.fail(e.getMessage());
        }
        return StepResult.success("dismissInvalidCert(): Invalid certificate message in " + browser);

    }

    public StepResult dismissInvalidCertPopup() {
        wait("2000");

        ensureReady();
        String parentTitle = driver.getTitle();
        String initialWinHandle = browser.getInitialWinHandle();

        try {
            //get all window handles available
            for (String popUpHandle : driver.getWindowHandles()) {
                wait("750");
                if (!popUpHandle.equals(initialWinHandle)) {
                    //switch driver to popup handle
                    driver.switchTo().window(popUpHandle);
                }
            }

            //take care of invalid cert error message; call method
            dismissInvalidCert();
            //switch back to parent window
            driver.switchTo().window(initialWinHandle);
            waitForBrowserStability(1000);
            waitForTitle(parentTitle);
        } catch (WebDriverException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        } catch (Exception e) {
            return StepResult.fail(e.getMessage());
        }

        return StepResult.success("Dismiss invalid certification popup message in " + browser);
    }

    public StepResult goBack() { return goBack(false); }

    public StepResult goBackAndWait() { return goBack(true); }

    public StepResult selectText(String locator) {
        WebElement elem = toElement(locator);
        if (StringUtils.isBlank(elem.getText())) { return StepResult.fail("Element found without text to select."); }

        String id = elem.getAttribute("id");
        if (StringUtils.isBlank(id)) { return StepResult.fail("Element found without 'id';REQUIRED"); }

        jsExecutor.executeScript("window.getSelection().selectAllChildren(document.getElementById('" + id + "'));");
        return StepResult.success("selected text at '" + locator + "'");
    }

    public StepResult unselectAllText() {
        ensureReady();
        jsExecutor.executeScript("window.getSelection().removeAllRanges();");
        return StepResult.success("unselected all text");
    }

    public StepResult maximizeWindow() {
        ensureReady();

        StepResult failed = notSupportedForElectron();
        if (failed != null) { return failed; }

        if (browser.isMobile()) { return StepResult.skipped("maximizeWindow not supported for mobile device"); }

        String winHandle = browser.getCurrentWinHandle();
        Window window;
        if (StringUtils.isNotBlank(winHandle) && browser.getBrowserType().isSwitchWindowSupported()) {
            window = driver.switchTo().window(winHandle).manage().window();
        } else {
            log("Unable to recognize current window, this command will likely fail..");
            window = driver.manage().window();
        }

        if (window == null) { return StepResult.fail("No current window found"); }

        try {
            if (browser.isRunChrome() && SystemUtils.IS_OS_MAC_OSX) {
                nativeMaximizeScreen(window);
            } else {
                window.maximize();
            }
            return StepResult.success("browser window maximized");
        } catch (WebDriverException e) {
            // fail safe..
            // Toolkit toolkit = Toolkit.getDefaultToolkit();
            // int screenWidth = (int) toolkit.getScreenSize().getWidth();
            // int screenHeight = (int) toolkit.getScreenSize().getHeight();
            // driver.manage().window().setSize(new Dimension(screenWidth, screenHeight));
            return StepResult.fail("Unable to maximize window: " + e.getMessage() +
                                   ".  Consider running browser in non-incognito mode");
        }
    }

    public StepResult resizeWindow(String width, String height) {
        requiresPositiveNumber(width, "invalid value for width", width);
        requiresPositiveNumber(height, "invalid value for height", height);

        ensureReady();
        int numWidth = NumberUtils.toInt(width);
        int numHeight = NumberUtils.toInt(height);
        Window window = driver.manage().window();
        // dimension unit is point (or px)
        window.setSize(new Dimension(numWidth, numHeight));

        return StepResult.success("browser window resize to " + width + "x" + height);
    }

    public StepResult assertScrollbarVPresent(String locator) { return checkScrollbarV(locator, false); }

    public StepResult assertScrollbarVNotPresent(String locator) { return checkScrollbarV(locator, true); }

    public StepResult assertScrollbarHPresent(String locator) { return checkScrollbarH(locator, false); }

    public StepResult assertScrollbarHNotPresent(String locator) { return checkScrollbarH(locator, true); }

    public StepResult open(String url) { return openAndWait(url, "100"); }

    public StepResult openAndWait(String url, String waitMs) {
        requiresNotBlank(url, "invalid URL", url);

        ensureReady();

        url = validateUrl(url);
        driver.get(url);
        waitForBrowserStability(toPositiveLong(waitMs, "waitMs"));
        updateWinHandle();
        resizeSafariAfterOpen();

        EventTracker.track(new NexialUrlInvokedEvent(browser.getBrowserType().name(), url));

        return StepResult.success("opened URL " + hideAuthDetails(url));
    }

    public StepResult openHttpBasic(String url, String username, String password) {
        requiresNotBlank(url, "invalid URL", url);
        requiresNotBlank(username, "invalid username", url);
        requiresNotBlank(password, "invalid password", url);

        ensureReady();

        String urlBasic = StringUtils.substringBefore(url, "://") + "://" +
                          URLEncodingUtils.encodeAuth(username) + ":" +
                          URLEncodingUtils.encodeAuth(password) + "@" +
                          StringUtils.substringAfter(url, "://");
        driver.get(urlBasic);
        waitForBrowserStability(context.getPollWaitMs());

        updateWinHandle();
        resizeSafariAfterOpen();

        EventTracker.track(new NexialUrlInvokedEvent(browser.getBrowserType().name(), url));

        return StepResult.success("opened URL " + hideAuthDetails(urlBasic));
    }

    public StepResult openIgnoreTimeout(String url) {
        requiresNotBlank(url, "invalid URL", url);

        ensureReady();

        driver.get("about:blank");

        StopWatch stopWatch = StopWatch.createStarted();
        long maxLoadTime = context.getIntData(WEB_WEB_PAGE_LOAD_WAIT_MS, getDefaultInt(WEB_WEB_PAGE_LOAD_WAIT_MS));

        url = validateUrl(url);
        String linkToUrl = "var a = document.createElement(\"a\");" +
                           "var linkText = document.createTextNode(\"" + url + "\");" +
                           "a.appendChild(linkText);" +
                           "a.title = \"" + url + "\";" +
                           "a.href = \"" + url + "\";" +
                           "document.body.appendChild(a);";
        jsExecutor.executeScript(linkToUrl);

        WebElement elemA = findElement("css=a");
        elemA.click();

        String checkReadyState = "return document.readyState";
        Object readyState = jsExecutor.executeScript(checkReadyState);
        while (readyState == null || !StringUtils.equals(readyState.toString(), "complete")) {
            waitFor(100);
            if (stopWatch.getTime() >= maxLoadTime) { break; }
            readyState = jsExecutor.executeScript(checkReadyState);
        }

        stopWatch.stop();

        resizeSafariAfterOpen();

        EventTracker.track(new NexialUrlInvokedEvent(browser.getBrowserType().name(), url));

        return StepResult.success("opened URL " + hideAuthDetails(url));
    }

    public StepResult refresh() {
        ensureReady();
        driver.navigate().refresh();
        return StepResult.success("active window refreshed");
    }

    public StepResult refreshAndWait() {
        ensureReady();
        driver.navigate().refresh();
        waitForBrowserStability(context.getPollWaitMs());
        return StepResult.success("active window refreshed");
    }

    public StepResult assertTitle(String text) {
        ensureReady();
        assertEquals(text, driver.getTitle());
        return StepResult.success();
    }

    public StepResult waitForTitle(final String text) {
        requiresNotBlank(text, "invalid title text", text);

        ensureReady();
        Boolean expectedTitleFound = newFluentWait().until(ExpectedConditions.titleIs(text));
        return new StepResult(expectedTitleFound, (expectedTitleFound ? "EXPECTED title " : "NOT ") + "found", null);
    }

    public StepResult selectWindow(String winId) { return selectWindowAndWait(winId, "2000"); }

    public StepResult selectWindowByIndex(String index) { return selectWindowByIndexAndWait(index, "2000"); }

    public StepResult selectWindowByIndexAndWait(String index, String waitMs) {
        requiresPositiveNumber(index, "window index must be a positive integer (zero-based");
        requiresPositiveNumber(waitMs, "waitMs must be a positive integer", waitMs);

        if (driver != null) {
            Set<String> handles = driver.getWindowHandles();
            ConsoleUtils.log("found " + CollectionUtils.size(handles) + " window handle(s); recalibrating again...");
        }

        ensureReady();

        // electron app or electron chromedriver tends to be slower... so we need to give some time
        if (browser.isRunElectron()) { try { Thread.sleep(2000);} catch (InterruptedException e) { } }

        // double check
        Set<String> handles = driver.getWindowHandles();
        if (CollectionUtils.isEmpty(handles)) { return StepResult.fail("No window or windows handle found"); }

        int handleCount = handles.size();
        ConsoleUtils.log("found " + handleCount + " window handle(s)...");

        int idx = NumberUtils.toInt(index);
        if (idx >= handleCount) { return StepResult.fail("Window index " + index + " not found"); }

        String handle = IterableUtils.get(handles, idx);
        ConsoleUtils.log("selecting window based on handle " + handle);
        return selectWindowAndWait(handle, waitMs);
    }

    public StepResult selectWindowAndWait(String winId, String waitMs) {
        requiresNotNull(winId, "Invalid window handle/id", winId);
        requiresPositiveNumber(waitMs, "waitMs must be a positive integer", waitMs);

        // wait time removed since in a multi-window scenario, the last (main) window might no yet selected.
        //waitForBrowserStability(context.getPollWaitMs());

        return trySelectWindow(winId, waitMs);
    }

    public StepResult waitForPopUp(String winId, String waitMs) {
        requires(StringUtils.isNotBlank(winId), "invalid window ID ", winId);
        long waitMsLong = toPositiveLong(waitMs, "wait millisecond");

        ensureReady();

        if (browser.isRunIE()) {
            return StepResult.warnUnsupportedFeature("waitForPopUp", driver);
            // todo IE fix? need more testing
        }

        // todo need to test
        WebDriverWait wait = new WebDriverWait(driver, waitMsLong);
        wait.until((Function<WebDriver, Object>) webDriver -> {
            driver = driver.switchTo().window(winId);
            return true;
        });

        return StepResult.success("waited for popup window '" + winId + "'");
    }

    public StepResult saveAllWindowNames(String var) {
        String names = TextUtils.toString(getAllWindowNames().toArray(new String[]{}), ",", "", "");
        updateDataVariable(var, names);
        return StepResult.success("stored existing window names '" + names + "' as ${" + var + "}");
    }

    public StepResult saveAllWindowIds(String var) {
        String names = TextUtils.toString(getAllWindowIds().toArray(new String[]{}), ",", "", "");
        updateDataVariable(var, names);
        return StepResult.success("stored existing window ID '" + names + "' as ${" + var + "}");
    }

    public StepResult assertFramePresent(String frameName) { return frameHelper.assertFramePresent(frameName); }

    public StepResult assertFrameCount(String count) { return frameHelper.assertFrameCount(count); }

    public StepResult selectFrame(String locator) { return frameHelper.selectFrame(locator); }

    public StepResult wait(String waitMs) {
        if (StringUtils.isBlank(waitMs)) { return StepResult.success("waited " + waitMs + "ms"); }

        int waitMsInt = toPositiveInt(waitMs, "waitMs");
        try {
            sleep(waitMsInt);
            return StepResult.success("waited " + waitMs + "ms");
        } catch (InterruptedException e) {
            return StepResult.fail(e.getMessage());
        }
    }

    public StepResult scrollTo(String locator) { return scrollTo(locator, (Locatable) toElement(locator)); }

    public StepResult scrollLeft(String locator, String pixel) {
        requiresInteger(pixel, "invalid number", pixel);

        logDeprecated(getTarget() + " » scrollLeft(locator,pixel)",
                      getTarget() + " » scrollElement(locator,xOffset,yOffset)");

        WebElement element = toElement(locator);
        jsExecutor.executeScript("arguments[0].scrollBy(" + pixel + ",0)", element);

        return scrollTo(locator, (Locatable) element);
    }

    public StepResult scrollRight(String locator, String pixel) {
        requiresInteger(pixel, "invalid number", pixel);

        logDeprecated(getTarget() + " » scrollRight(locator,pixel)",
                      getTarget() + " » scrollElement(locator,xOffset,yOffset)");

        WebElement element = toElement(locator);
        jsExecutor.executeScript("arguments[0].scrollBy(" + (NumberUtils.toInt(pixel) * -1) + ",0)", element);

        return scrollTo(locator, (Locatable) element);
    }

    public StepResult scrollPage(String xOffset, String yOffset) {
        requiresInteger(xOffset, "invalid xOffset", xOffset);
        requiresInteger(yOffset, "invalid yOffset", yOffset);

        jsExecutor.executeScript("window.scrollBy(" + xOffset + "," + yOffset + ")");

        return StepResult.success("current window/page scrolled by (" + xOffset + "," + yOffset + ")");
    }

    public StepResult scrollElement(String locator, String xOffset, String yOffset) {
        requiresInteger(xOffset, "invalid xOffset", xOffset);
        requiresInteger(yOffset, "invalid yOffset", yOffset);

        WebElement element = toElement(locator);
        jsExecutor.executeScript("arguments[0].scrollBy(" + xOffset + "," + yOffset + ")", element);

        // return StepResult.success("current window/page scrolled by (" + xOffset + "," + yOffset + ")");
        return scrollTo(locator, (Locatable) element);
    }

    public StepResult type(String locator, String value) {
        WebElement element = findElement(locator);
        if (element == null) {
            String msg = "unable to complete type() since locator (" + locator + ") cannot be found.";
            error(msg);
            return StepResult.fail(msg);
        }

        clearValue(element);

        if (StringUtils.isNotEmpty(value)) {
            // was needed for Safari/Windows. We don't need this anymore
            // if (browser.isRunSafari()) { focus("//body"); }

            // onchange event will not fire until a different element is selected
            if (context.getBooleanData(WEB_UNFOCUS_AFTER_TYPE, getDefaultBool(WEB_UNFOCUS_AFTER_TYPE))) {
                // element.sendKeys();
                new Actions(driver).moveToElement(element)
                                   .sendKeys(element, value)
                                   .sendKeys(TAB)
                                   .build()
                                   .perform();
            } else {
                scrollIntoView(element);
                element.sendKeys(value);
            }
        }

        return StepResult.success("typed text at '" + locator + "'");
    }

    /** support no locator */
    public StepResult typeKeys(String locator, String value) {
        if (StringUtils.isNotBlank(locator)) {
            WebElement element = toElement(locator);
            scrollIntoView(element);

            if (StringUtils.isEmpty(value)) {
                clearValue(element);
                return StepResult.success("cleared out value at '" + locator + "'");
            }

            element.click();
            waitFor(MIN_STABILITY_WAIT_MS);
            Actions actions = WebDriverUtils.toSendKeyAction(driver, element, value);
            if (actions != null) {
                if (context.getBooleanData(WEB_UNFOCUS_AFTER_TYPE, getDefaultBool(WEB_UNFOCUS_AFTER_TYPE))) {
                    actions.sendKeys(TAB);
                }
                actions.build().perform();
            }
        } else {
            // no locator
            WebDriverUtils.toSendKeyAction(driver, null, value).build().perform();
        }

        // could have alert text...
        alert.preemptiveDismissAlert();

        return StepResult.success("typed text at '" + locator + "'");
    }

    public StepResult upload(String fieldLocator, String file) {
        requires(StringUtils.isNotBlank(fieldLocator), "invalid field locator", fieldLocator);
        requires(StringUtils.isNotBlank(file), "invalid file to upload", file);

        File f = new File(file);
        if (!f.isFile() || !f.canRead()) { return StepResult.fail("specified file '" + file + "' is not readable"); }

        //driver.setFileDetector(new LocalFileDetector());
        WebElement upload = findElement(fieldLocator);
        if (upload == null) { return StepResult.fail("expected locator '" + fieldLocator + "' NOT FOUND"); }

        upload.sendKeys(f.getAbsolutePath());
        return StepResult.success("adding file '" + file + "' to '" + fieldLocator + "'");
    }

    public StepResult verifyContainText(String locator, String text) { return assertTextContains(locator, text); }

    public StepResult verifyText(String locator, String text) { return assertText(locator, text); }

    public StepResult savePageAs(String var, String sessionIdName, String url) {
        requiresNotBlank(var, "invalid variable", var);
        String safeUrl = hideAuthDetails(url);

        try {
            updateDataVariable(var, new String(downloadLink(sessionIdName, url)));
            return StepResult.success("saved '" + safeUrl + "' as ${" + var + "}");
        } catch (Exception e) {
            String message = "Unable to save link '" + safeUrl + "' as property '" + var + "': " + e.getMessage();
            return StepResult.fail(message);
        }
    }

    public StepResult savePageAsFile(String sessionIdName, String url, String file) {
        requiresNotBlank(file, "invalid filename", file);

        File f = new File(file);
        requires(!f.isDirectory(), "filename cannot be a directory", file);

        String safeUrl = hideAuthDetails(url);

        try {
            // download
            byte[] payload = downloadLink(sessionIdName, url);

            // just in case
            f.getParentFile().mkdirs();

            FileUtils.writeByteArrayToFile(f, payload);
            return StepResult.success("saved '" + safeUrl + "' as '" + file + "'");
        } catch (IOException e) {
            return StepResult.fail("Unable to save '" + safeUrl + "' as '" + file + "': " + e.getMessage());
        }
    }

    public StepResult saveLocation(String var) {
        requires(StringUtils.isNotBlank(var) && !StringUtils.startsWith(var, "${"), "invalid variable", var);

        ensureReady();
        updateDataVariable(var, driver.getCurrentUrl());
        return StepResult.success("stored current URL as ${" + var + "}");
    }

    public StepResult clearLocalStorage() {
        ensureReady();
        jsExecutor.executeScript("window.localStorage.clear();");
        return StepResult.success("browser's local storage cleared");
    }

    public StepResult editLocalStorage(String key, String value) {
        requiresNotBlank(key, "local storage key must not be null");

        ensureReady();
        if (StringUtils.isBlank(value)) {
            jsExecutor.executeScript("window.localStorage.removeItem('" + key + "');");
        } else {
            jsExecutor.executeScript("window.localStorage.setItem('" + key + "','" +
                                     StringUtils.replace(value, "'", "\\'") +
                                     "');");
        }
        return StepResult.success("browser's local storage updated");
    }

    public StepResult saveLocalStorage(String var, String key) {
        requiresValidVariableName(var);
        requiresNotBlank(key, "local storage key must not be null");

        ensureReady();
        Object response = jsExecutor.executeScript("return window.localStorage.getItem('" + key + "')");
        context.setData(var, response);
        return StepResult.success("browser's local storage (" + key + ") stored to " + var + " as " + response);
    }

    /**
     * designed for JS injection / web security testing
     */
    public StepResult executeScript(String var, String script) {
        requiresValidVariableName(var);
        requiresNotBlank(script, "Invalid script", script);

        String javascript;
        try {
            javascript = OutputFileUtils.resolveContent(script, context, false, true);
        } catch (IOException e) {
            // can't resolve content.. then we'll leave it be
            ConsoleUtils.log("Unable to resolve JavaScript '" + script + "': " + e.getMessage() + ". Use as is...");
            javascript = script;
        }

        ensureReady();

        Object retVal = null;
        try {
            retVal = jsExecutor.executeScript(javascript);
        } catch (UnhandledAlertException e) {
            if (browser.isRunSafari()) {
                // it's ok.. safari's been known to barf at JS... let's try to move on...
                ConsoleUtils.error("UnhandledAlertException when executing JavaScript with Safari, might be ok...");
            } else {
                throw e;
            }
        }

        if (retVal != null) { context.setData(var, retVal); }

        return StepResult.success("script executed");
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

        boolean useNativeCapture = false;
        if (browser == null || driver == null) {
            useNativeCapture = true;
        } else {
            // proceed... with caution (or not!)
            waitForBrowserStability(deriveBrowserStabilityWaitMs(context));
            if (alert.isDialogPresent()) {
                if (browser.isRunBrowserStack() || browser.isRunCrossBrowserTesting()) {
                    alert.dismiss();
                } else {
                    useNativeCapture = true;
                }
            }
        }

        File screenshotFile;
        if (useNativeCapture) {
            log("using native screen capturing approach...");
            screenshotFile = new File(filename);
            if (!NativeInputHelper.captureScreen(0, 0, -1, -1, screenshotFile)) {
                error("Unable to capture screenshot via native screen capturing approach");
                return null;
            }

            context.setData(OPT_LAST_SCREENSHOT_NAME, screenshotFile.getName());
        } else {
            screenshotFile = ScreenshotUtils.saveScreenshot(screenshot, filename);
        }

        if (screenshotFile == null) {
            error("Unable to save screenshot for " + testStep);
            return null;
        }

        if (context.isOutputToCloud()) {
            try {
                return context.getOtc().importMedia(screenshotFile);
            } catch (IOException e) {
                log(toCloudIntegrationNotReadyMessage(screenshotFile.toString()) + ": " + e.getMessage());
            }
        }

        return screenshotFile.getAbsolutePath();
    }

    @Override
    public String generateScreenshotFilename(TestStep testStep) {
        return OutputFileUtils.generateScreenCaptureFilename(testStep);
    }

    @Override
    public void logExternally(TestStep testStep, String message) {
        if (testStep == null || StringUtils.isBlank(message)) { return; }
        logToBrowserConsole(testStep.showPosition() + " " + message);
    }

    // todo need to test
    public StepResult doubleClickAndWait(String locator, String waitMs) {
        boolean isNumber = NumberUtils.isDigits(waitMs);
        String waitMsStr = isNumber ? (long) NumberUtils.toDouble(waitMs) + "" : context.getPollWaitMs() + "";

        WebElement element = toElement(locator);
        scrollIntoView(element);
        highlight(element);
        new Actions(driver).moveToElement(element).doubleClick(element).build().perform();

        // could have alert text...
        alert.preemptiveDismissAlert();
        waitForBrowserStability(Long.parseLong(waitMsStr));

        if (!isNumber) {
            return StepResult.warn("invalid waitMs: " + waitMs + ", default to " + waitMsStr);
        } else {
            return StepResult.success("double-clicked-and-waited '" + locator + "'");
        }
    }

    public StepResult close() {
        ensureReady();

        boolean lastWindow = browser.isOnlyOneWindowRemaining();
        String activeWindowHandle = driver.getWindowHandle();

        // warning for non-OSX safari instances or IE
        if ((browser.isRunSafari() && !IS_OS_MAC) || browser.isRunIE()) {
            ConsoleUtils.log("close() might not work on IE or Safari/Win32; likely to close the wrong tab/window");
            jsExecutor.executeScript("window.close()");
        } else {
            driver.switchTo().window(activeWindowHandle).close();
        }

        // give it time to settle down
        wait(context.getIntData(BROWSER_POST_CLOSE_WAIT, getDefaultInt(BROWSER_POST_CLOSE_WAIT)) + "");

        if (lastWindow) { return closeAll(); }

        browser.removeWinHandle(activeWindowHandle);

        Stack<String> lastWinHandles = browser.getLastWinHandles();
        if (CollectionUtils.isNotEmpty(lastWinHandles)) {
            try {
                //do not pop if there's only 1 win handle left (that's the initial win handle).
                String handle = lastWinHandles.size() == 1 ? lastWinHandles.peek() : lastWinHandles.pop();
                ConsoleUtils.log("focus returns to previous window '" + handle + "'");
                driver = driver.switchTo().window(handle);
            } catch (NotFoundException e) {
                ConsoleUtils.error("Unable to focus on windows due to invalid handler; default to main window");
                selectWindow("null");
            }
        } else {
            ConsoleUtils.log("focus returns to initial browser window");
            selectWindow("null");
        }

        return StepResult.success("closed active window");
    }

    public StepResult closeAll() {
        // ensureReady();
        if (browser != null) { browser.shutdown(); }
        driver = null;
        return StepResult.success("closed last tab/window");
    }

    // todo need to test
    public StepResult mouseOver(String locator) {
        new Actions(driver).moveToElement(toElement(locator)).build().perform();

        //selenium.fireEvent(locator, "focus");
        // work as of 2.26.0
        //selenium.mouseOver(locator);

        return StepResult.success("mouse-over on '" + locator + "'");
    }

    public StepResult dragAndDrop(String fromLocator, String toLocator) {
        requiresNotBlank(fromLocator, "invalid fromLocator", fromLocator);
        requiresNotBlank(toLocator, "invalid toLocator", toLocator);

        WebElement source = findElement(fromLocator);
        WebElement target = findElement(toLocator);
        new Actions(driver).clickAndHold(source).pause(500).dragAndDrop(source, target).build().perform();

        return StepResult.success("Drag-and-drop element '" + fromLocator + "' to '" + toLocator + "'");
    }

    public StepResult dragTo(String fromLocator, String xOffset, String yOffset) {
        requiresNotBlank(fromLocator, "invalid fromLocator", fromLocator);
        requiresInteger(xOffset, "invalid x-offset", xOffset);
        requiresInteger(yOffset, "invalid y-offset", yOffset);

        int moveX = NumberUtils.toInt(xOffset);
        int moveY = NumberUtils.toInt(yOffset);

        WebElement source = findElement(fromLocator);

        Point xy = deriveDragFrom(source);
        ConsoleUtils.log("start dragging from target element (" + xy.getX() + "," + xy.getY() + ")");

        new Actions(driver).moveToElement(source, xy.getX(), xy.getY())
                           .dragAndDropBy(source, moveX, moveY)
                           .build().perform();

        return StepResult.success("Drag-and-move element '" + fromLocator + "' by (" + moveX + "," + moveY + ")");
    }

    protected int deriveBrowserStabilityWaitMs(ExecutionContext context) {
        return context.getIntData(OPT_UI_RENDER_WAIT_MS, getDefaultInt(OPT_UI_RENDER_WAIT_MS));
    }

    protected void clearValue(WebElement element) {
        if (element == null) { return; }

        // prior to clearing
        String before = element.getAttribute("value");

        if (browser.isRunElectron() ||
            context.getBooleanData(WEB_CLEAR_WITH_BACKSPACE, getDefaultBool(WEB_CLEAR_WITH_BACKSPACE))) {
            if (StringUtils.isNotEmpty(before)) {
                // persistently delete character.. but if app insist on "autocompleting" then we'll give up
                String beforeBackspace;
                String afterBackspace;
                do {
                    beforeBackspace = element.getAttribute("value");
                    element.sendKeys(Keys.END);
                    before.chars().forEach(value -> element.sendKeys(BACK_SPACE));
                    afterBackspace = element.getAttribute("value");
                } while (!StringUtils.equals(afterBackspace, beforeBackspace));
            }
        } else {
            // try thrice to cover all bases
            element.clear();
            jsExecutor.executeScript("arguments[0].setAttribute(arguments[1],arguments[2]);", element, "value", "");
            jsExecutor.executeScript("arguments[0].value = '';", element);
        }

        // after clearing
        String after = null;
        try {
            after = element.getAttribute("value");
        } catch (WebDriverException e) {
            // hmm... something's afoot.. but we shouldn't alarm the populous...
            ConsoleUtils.log("Unable to retrieve value from the 'value' attribute after clearing the target element");
        }

        if (StringUtils.isNotEmpty(after)) {
            error("Unable to clear out the value of the target element. [before] " + before + ", [after] " + after);
        }
    }

    protected Point deriveDragFrom(WebElement source) {
        String defaultDragFrom = getDefault(OPT_DRAG_FROM);
        String dragFrom = context.getStringData(OPT_DRAG_FROM, defaultDragFrom);
        if (!OPT_DRAG_FROMS.contains(dragFrom)) {
            ConsoleUtils.error("Invalid drag-from value: " + dragFrom + ", use default instead");
            dragFrom = defaultDragFrom;
        }

        Dimension dimension = source.getSize();

        if (StringUtils.equals(dragFrom, OPT_DRAG_FROM_LEFT_CORNER)) {
            return new Point(0, dimension.getHeight() / 2);
        }

        if (StringUtils.equals(dragFrom, OPT_DRAG_FROM_RIGHT_CORNER)) {
            return new Point(dimension.getWidth(), dimension.getHeight() / 2);
        }

        if (StringUtils.equals(dragFrom, OPT_DRAG_FROM_TOP_CORNER)) {
            return new Point(dimension.getWidth() / 2, 0);
        }

        if (StringUtils.equals(dragFrom, OPT_DRAG_FROM_BOTTOM_CORNER)) {
            return new Point(dimension.getWidth() / 2, dimension.getHeight());
        }

        // default - drag from middle
        return new Point(dimension.getWidth() / 2, dimension.getHeight() / 2);
    }

    protected void resizeSafariAfterOpen() {
        if ((browser.isRunBrowserStack() && browser.getBrowserstackHelper().getBrowser() == safari) ||
            (browser.isRunCrossBrowserTesting() && browser.getCbtHelper().getBrowser() == safari)) {
            if (!context.getBooleanData(SAFARI_RESIZED, getDefaultBool(SAFARI_RESIZED))) {
                // time to resize it now
                browser.setWindowSizeForcefully(driver);
                context.setData(SAFARI_RESIZED, true);
            }
        }
    }

    // bring browser to foreground
    protected void updateWinHandle() {
        String initialHandle = browser.updateWinHandle();
        if (!StringUtils.isNotBlank(initialHandle)) { return; }
        if (!browser.getBrowserType().isSwitchWindowSupported()) { return; }
        if (browser.isRunCrossBrowserTesting() && browser.getCbtHelper().isMobile()) { return; }

        ConsoleUtils.log("current browser window handle:" + initialHandle);
        driver = driver.switchTo().window(initialHandle).switchTo().defaultContent();
    }

    protected void nativeMaximizeScreen(Window window) {
        java.awt.Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        window.setPosition(new Point(0, 0));
        window.setSize(new Dimension((int) screenSize.getWidth(), (int) screenSize.getHeight()));
    }

    @NotNull
    protected String[] collectTextList(String locator) { return collectTextList(findElements(locator)); }

    @NotNull
    protected String[] collectTextList(List<WebElement> matches) {
        if (CollectionUtils.isEmpty(matches)) { return new String[0]; }
        return matches.stream().map(WebElement::getText).toArray(String[]::new);
    }

    @NotNull
    protected String[] collectValueList(List<WebElement> matches) {
        if (CollectionUtils.isEmpty(matches)) { return new String[0]; }
        return matches.stream().map(elem -> elem.getAttribute("value")).toArray(String[]::new);
    }

    @NotNull
    protected StepResult goBack(boolean wait) {
        StepResult failed = notSupportedForElectron();
        if (failed != null) { return failed; }

        ensureReady();
        driver.navigate().back();
        if (wait) { waitForBrowserStability(context.getPollWaitMs()); }
        return StepResult.success("went back previous page");
    }

    protected StepResult notSupportedForElectron() {
        if (browser.isRunElectron()) { return StepResult.fail("This command is NOT supported on Electron apps."); }
        return null;
    }

    protected boolean execJsOverFreshElements(String locator, String script, int inBetweenWaitMs) {
        if (StringUtils.isBlank(script)) { throw new IllegalArgumentException("script is blank/empty"); }

        requiresNotBlank(locator, "invalid locator", locator);

        List<WebElement> elements = findElements(locator);
        if (CollectionUtils.isEmpty(elements)) { return false; }

        // since we are activating click event, there's a chance that the page would be updated and the element(s) might
        // no longer be valid. As such we need to refresh the element list each time one element is clicked
        int count = elements.size();
        ConsoleUtils.log("executing JavaScript over " + count + " elements (" + locator + ")...");

        for (int i = 0; i < count; i++) {
            elements = findElements(locator);
            if (CollectionUtils.size(elements) > i) {
                ConsoleUtils.log("\t...executing JavaScript over element " + (i + 1));
                jsExecutor.executeScript(script, elements.get(i));
                waitFor(inBetweenWaitMs);
            }
        }

        return true;
    }

    protected boolean isCheckbox(WebElement element, String locator, boolean checkstate) {
        if (!StringUtils.equalsIgnoreCase(element.getAttribute("type"), "checkbox")) {
            ConsoleUtils.log("A web element matching '" + locator + "' is NOT a CheckBox");
            return false;
        }

        if (!element.isEnabled()) {
            ConsoleUtils.log("A CheckBox element matching '" + locator + "' is NOT enabled");
            return false;
        }

        if (!element.isDisplayed()) {
            ConsoleUtils.log("A CheckBox element matching '" + locator + "' is NOT visible");
            return false;
        }

        if (checkstate && element.isSelected()) {
            ConsoleUtils.log("A CheckBox element matching '" + locator + "' is already CHECKED");
            return false;
        }

        if (!checkstate && !element.isSelected()) {
            ConsoleUtils.log("A CheckBox element matching '" + locator + "' is already UNCHECKED");
            return false;
        }

        return true;
    }

    protected StepResult scrollTo(String locator, Locatable element) {
        try {
            boolean success = scrollTo(element);
            if (success) {
                return StepResult.success("scrolled to '" + locator + "'");
            } else {
                return StepResult.fail("Unable to scroll to '" + locator + "' - failed to obtain coordinates");
            }
        } catch (ElementNotVisibleException e) {
            return StepResult.fail("Unable to scroll to '" + locator + "': " + e.getMessage());
        }
    }

    protected StepResult mouseOut(String locator) {
        ensureReady();
        jsExecutor.executeScript("arguments[0].mouseout();", toElement(locator));

        //selenium.fireEvent(locator, "blur");
        // work as of 2.26.0
        //selenium.mouseOut(locator);
        return StepResult.success("mouse-out on '" + locator + "'");
    }

    protected StepResult checkScrollbarV(String locator, boolean failIfExists) {
        try {
            WebElement element = findElement(locator);

            boolean exists;
            // special treatment for body tag
            if (StringUtils.equalsIgnoreCase(element.getTagName(), "body")) {
                exists = BooleanUtils.toBoolean(Objects.toString(jsExecutor.executeScript(
                    "document.documentElement.clientHeight < document.documentElement.scrollHeight"))
                                               );
            } else {
                exists = NumberUtils.toInt(element.getAttribute("clientHeight")) <
                         NumberUtils.toInt(element.getAttribute("scrollHeight"));
            }

            boolean result = failIfExists != exists;
            String msg = "vertical scrollbar " + (exists ? "exists" : "does not exists") + " at '" + locator + "'";
            return new StepResult(result, msg, null);
        } catch (Throwable e) {
            return StepResult.fail("Error determining vertical scrollbar at '" + locator + "': " + e.getMessage());
        }
    }

    protected StepResult checkScrollbarH(String locator, boolean failIfExists) {
        try {
            WebElement element = findElement(locator);

            boolean exists;
            // special treatment for body tag
            if (StringUtils.equalsIgnoreCase(element.getTagName(), "body")) {
                exists = BooleanUtils.toBoolean(Objects.toString(jsExecutor.executeScript(
                    "return document.documentElement.clientWidth < document.documentElement.scrollWidth")));
            } else {
                exists = NumberUtils.toInt(element.getAttribute("clientWidth")) <
                         NumberUtils.toInt(element.getAttribute("scrollWidth"));
            }

            boolean result = failIfExists != exists;
            String msg = "horizontal scrollbar " + (exists ? "exists" : "does not exists") + " at '" + locator + "'";
            return new StepResult(result, msg, null);
        } catch (Throwable e) {
            return StepResult.fail("Error determining horizontal scrollbar at '" + locator + "': " + e.getMessage());
        }
    }

    protected String getCssValue(String locator, String property) {
        WebElement element = findElement(locator);
        if (element == null) { throw new NoSuchElementException("Element NOT found via '" + locator + "'"); }
        return StringUtils.lowerCase(StringUtils.trim(element.getCssValue(property)));
    }

    /**
     * todo: need to evaluate whether we need this still... for now, move it to protected
     */
    protected StepResult uploadAndSubmit(String fieldLocator, String file, String submitLocator) {
        requires(StringUtils.isNotBlank(submitLocator), "invalid submit locator", file);
        StepResult result = upload(fieldLocator, file);
        if (result.failed()) { return result; }
        return click(submitLocator);
    }

    /** INTERNAL METHOD; not for public consumption */
    protected StepResult assertAttributePresentInternal(String locator, String attrName, boolean expectsFound) {
        try {
            String actual = getAttributeValue(locator, attrName);
            boolean success = expectsFound ? StringUtils.isNotEmpty(actual) : StringUtils.isEmpty(actual);
            return new StepResult(success,
                                  "Attribute '" + attrName + "' of element '" + locator + "' is " +
                                  (success ? "found as EXPECTED" : " NOT FOUND"), null);
        } catch (NoSuchElementException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        }
    }

    protected StepResult assertAttributeContainsInternal(String locator,
                                                         String attrName,
                                                         String contains,
                                                         boolean expectsContains) {

        String msg = "Attribute '" + attrName + "' of element '" + locator + "' ";

        boolean success;
        try {
            String actual = getAttributeValue(locator, attrName);
            if (StringUtils.isBlank(contains) && StringUtils.isBlank(actual)) {
                // got a match
                if (expectsContains) {
                    success = true;
                    msg += "CONTAINS blank as EXPECTED";
                } else {
                    success = false;
                    msg += "CONTAINS blank, which is NOT expected";
                }
            } else {
                if (StringUtils.contains(actual, contains)) {
                    if (expectsContains) {
                        success = true;
                        msg += "CONTAINS '" + contains + "' as EXPECTED";
                    } else {
                        success = false;
                        msg += "CONTAINS '" + contains + "', which is NOT as expected";
                    }
                } else {
                    if (expectsContains) {
                        success = false;
                        msg += "DOES NOT contains '" + contains + "', which is NOT as expected";
                    } else {
                        success = true;
                        msg += "DOES NOT contains '" + contains + "' as EXPECTED";
                    }
                }
            }

            return new StepResult(success, msg, null);
        } catch (WebDriverException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        }
    }

    protected StepResult trySelectWindow(String winId, String waitMs) {
        long waitMsLong = toPositiveLong(waitMs, "waitMs");
        long endTime = System.currentTimeMillis() + waitMsLong;
        boolean rc = false;

        ensureReady();

        // track current window handle as the last focused window object (used in close() cmd)
        try {
            String windowHandle = driver.getWindowHandle();
            Stack<String> lastWinHandles = browser.getLastWinHandles();
            if (CollectionUtils.isNotEmpty(lastWinHandles)) {
                if (!StringUtils.equals(lastWinHandles.peek(), windowHandle)) { lastWinHandles.push(windowHandle); }
            }
        } catch (NotFoundException e) {
            // failsafe.. in case we can't select window by selenium api
            driver = driver.switchTo().defaultContent();
        }

        String dummyId = "#DEF#";

        if (StringUtils.equals(winId, "null")) { winId = ""; }
        if (StringUtils.isEmpty(winId)) { winId = StringUtils.defaultIfBlank(browser.getInitialWinHandle(), dummyId); }

        do {
            try {
                if (StringUtils.equals(winId, dummyId)) {
                    driver = driver.switchTo().defaultContent();
                } else {
                    driver = driver.switchTo().window(winId);
                }
                rc = true;
                break;
            } catch (Exception e) {
                try { sleep(250); } catch (InterruptedException e1) { }
            }
        } while (System.currentTimeMillis() < endTime);

        String targetWinId = (StringUtils.equals(winId, dummyId) ? "default" : winId) + " window";
        if (rc) {
            waitForBrowserStability(context.getPollWaitMs());
            return StepResult.success("selected " + targetWinId);
        } else {
            return StepResult.fail("could not select " + targetWinId);
        }
    }

    protected StepResult clickInternal(String locator) {
        WebElement element;
        try {
            List<WebElement> matches = findElements(locator);
            element = CollectionUtils.isEmpty(matches) ? null : matches.get(0);
        } catch (TimeoutException e) {
            return StepResult.fail("Unable to find element via locator '" + locator + "' within allotted time");
        }

        if (element == null) { return StepResult.fail("No element via locator '" + locator + "'"); }

        ConsoleUtils.log("clicking '" + locator + "'...");
        return clickInternal(element);
    }

    /** internal impl. to handle browser-specific behavior regarding click */
    protected StepResult clickInternal(WebElement element) {
        if (element == null) { return StepResult.fail("Unable to obtain element"); }

        highlight(element);

        // Nexial configure "preference" for each browser to use JS click on not. However, we need to honor user's
        // wish NOT to use JS click if they had configured their test as such
        boolean systemFavorJsClick = jsExecutor != null && browser.favorJSClick();
        boolean forceJSClick = systemFavorJsClick;
        if (context.hasData(FORCE_JS_CLICK)) { forceJSClick = context.getBooleanData(FORCE_JS_CLICK); }

        try {
            // @id doesn't matter anymore...
            // if (forceJSClick && StringUtils.isNotBlank(element.getAttribute("id"))) {
            if (forceJSClick) {
                jsClick(element);
                return StepResult.success("click via JS event");
            } else {
                // better impl. for CI
                new Actions(driver).moveToElement(element).click(element).build().perform();
                return StepResult.success("clicked on web element");
            }
        } catch (WebDriverException e) {
            return StepResult.fail(WebDriverExceptionHelper.resolveErrorMessage(e));
        } catch (Exception e) {
            // try again..
            if (systemFavorJsClick) {
                jsClick(element);
                return StepResult.success("second attempt click via JS event");
            }

            return StepResult.fail(e.getMessage(), e);
        } finally {
            // could have alert text...
            alert.preemptiveDismissAlert();
        }
    }

    protected void jsClick(WebElement element) {
        ConsoleUtils.log("click target via JS, @id=" + element.getAttribute("id"));
        scrollIntoView(element);
        ConsoleUtils.log("clicked -> " + jsExecutor.executeScript("arguments[0].click(); return true;", element));
    }

    protected void initWebDriver() {
        // todo: revisit to handle proxy
        //if (context.getBooleanData(OPT_PROXY_ENABLE, false)) {
        //	ProxyHandler proxy = new ProxyHandler();
        //	proxy.setContext(this);
        //	proxy.startProxy();
        //	browser.setProxy(proxy);
        //}

        WebDriver currentDriver = browser.ensureWebDriverReady();
        if (currentDriver == null) {
            // oops... we are in trouble...
            throw new RuntimeException("Unable to initialize WebDriver for " + context.getBrowserType());
        }

        if (driver == null || currentDriver != driver) {
            driver = currentDriver;
            alert = null;
            cookie = null;
        }

        jsExecutor = (JavascriptExecutor) this.driver;
        screenshot = (TakesScreenshot) this.driver;
        frameHelper = new FrameHelper(this, this.driver);

        if (alert == null) {
            alert = (AlertCommand) context.findPlugin("webalert");
            alert.init(context);
        } else {
            alert.driver = this.driver;
        }
        alert.setBrowser(browser);

        if (cookie == null) {
            cookie = (CookieCommand) context.findPlugin("webcookie");
            cookie.init(context);
        } else {
            cookie.driver = this.driver;
        }
        cookie.setBrowser(browser);
    }

    protected void ensureReady() { initWebDriver(); }

    // todo: need to enable proxy capability across nexial
    protected void initProxy() {
        // todo: need to decide key
        ProxyServer proxyServer = WebProxy.getProxyServer();
        if (context.hasData(BROWSER_LANG) && proxyServer != null) {
            String browserLang = context.getStringData(BROWSER_LANG);
            proxyServer.addHeader("Accept-Language", browserLang);
            proxyServer.addRequestInterceptor((RequestInterceptor) (request, har) -> {
                //Accept-Language: en-US,en;q=0.5
                //request.addRequestHeader("Accept-Language", getProp(OPT_BROWSER_LANG));
                HttpRequestBase method = request.getMethod();
                method.removeHeaders("Accept-Language");
                method.setHeader("Accept-Language", browserLang);

                HttpRequest proxyRequest = request.getProxyRequest();
                int oldState = proxyRequest.setState(HttpMessage.__MSG_EDITABLE);
                proxyRequest.removeField("Accept-Language");
                proxyRequest.setField("Accept-Language", browserLang);
                proxyRequest.setState(oldState);
            });
        }
    }

    @NotNull
    protected FluentWait<WebDriver> newFluentWait() {
        return new FluentWait<>(driver).withTimeout(Duration.ofMillis(context.getPollWaitMs()))
                                       .pollingEvery(Duration.ofMillis(10))
                                       .ignoring(NoSuchElementException.class);
    }

    @NotNull
    protected Select getSelectElement(String locator) {
        WebElement element = findElement(locator);
        if (element == null) { throw new NoSuchElementException("element '" + locator + "' not found."); }
        return new RegexAwareSelect(element);
    }

    protected int getElementCount(String locator) {
        List<WebElement> elements = findElements(locator);
        return CollectionUtils.isEmpty(elements) ? 0 : elements.size();
    }

    protected boolean isElementPresent(String locator) { return findElement(locator) != null; }

    protected List<WebElement> findElements(String locator) {
        ensureReady();

        By by = locatorHelper.findBy(locator);
        try {
            return shouldWait() ? newFluentWait().until(driver -> driver.findElements(by)) : driver.findElements(by);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    protected WebElement findElement(String locator) {
        ensureReady();

        By by = locatorHelper.findBy(locator);
        WebElement target = shouldWait() ?
                            newFluentWait().until(driver -> driver.findElement(by)) : driver.findElement(by);
        if (isHighlightEnabled() && target != null && target.isDisplayed()) { highlight(target); }
        return target;
    }

    protected WebElement toElement(String locator) {
        WebElement element = findElement(locator);
        if (element == null) { throw new NoSuchElementException("element not found via '" + locator + "'."); }
        return element;
    }

    protected boolean isIENativeMode() {
        ensureReady();
        Object ret = jsExecutor.executeScript("javascript:document.documentMode");
        if (ret == null) { throw new NotFoundException("Unable to determine IE native mode"); }

        String ieDocMode = (String) ret;
        String ieDocModeCompare = ieDocMode.substring(0, ieDocMode.indexOf("."));
        double docModeVersion = NumberUtils.toDouble(ieDocModeCompare);
        return browser.getBrowserVersionNum() == docModeVersion;
    }

    protected String getElementText(String locator) {
        try {
            return toElement(locator).getText();
        } catch (NoSuchElementException e) {
            String error = StringUtils.substringBefore(e.getMessage(), "\n");
            TestStep testStep = context.getCurrentTestStep();
            if (testStep != null) {
                context.getLogger().error(this, error);
            } else {
                context.getLogger().error(context, error);
            }
            return null;
        }
    }

    protected boolean isTextPresent(String text) {
        ensureReady();
        return StringUtils.contains(driver.findElement(By.tagName("body")).getText(), text);
    }

    protected String getValue(String locator) {
        WebElement element = toElement(locator);
        if (element == null) {
            String msg = "unable to complete command since locator (" + locator + ") cannot be found.";
            log(msg);
            throw new NoSuchElementException(msg);
        }

        return element.getAttribute("value");
    }

    protected String getAttributeValue(String locator, String attrName) throws NoSuchElementException {
        requiresNotBlank(locator, "invalid locator", locator);
        requiresNotBlank(attrName, "invalid attribute name", attrName);
        WebElement element = findElement(locator);
        if (element == null) { throw new NoSuchElementException("Element NOT found via '" + locator + "'"); }
        return element.getAttribute(attrName);
    }

    protected List<String> getAllWindowNames() {
        if (driver == null) { return new ArrayList<>(); }

        browser.resyncWinHandles();

        List<String> windowNames = new ArrayList<>();
        String current;
        try {
            current = driver.getWindowHandle();
        } catch (WebDriverException e) {
            ConsoleUtils.error("Error when obtaining available window names: " + e.getMessage());
            current = browser.initialWinHandle;
        }

        for (String handle : browser.getLastWinHandles()) {
            try {
                driver = driver.switchTo().window(handle);
                windowNames.add(jsExecutor.executeScript("return window.name").toString());
            } catch (WebDriverException e) {
                ConsoleUtils.error("Error when obtaining window name for " + handle + ": " + e.getMessage());
            }
        }

        driver = driver.switchTo().window(current);

        return windowNames;
    }

    protected List<String> getAllWindowIds() {
        if (driver == null) { return new ArrayList<>(); }
        return CollectionUtil.toList(driver.getWindowHandles());
    }

    protected boolean waitForBrowserStability(long maxWait) {
        if (browser == null || browser.isRunElectron()) { return false; }

        // for firefox we can't be calling driver.getPageSource() or driver.findElement() when alert dialog is present
        if (alert.preemptiveCheckAlert()) { return true; }
        if (alert.isDialogPresent()) { return false; }
        if (!context.isPageSourceStabilityEnforced()) { return false; }

        // force at least 1 compare
        if (maxWait < MIN_STABILITY_WAIT_MS) { maxWait = MIN_STABILITY_WAIT_MS + 1; }

        // some browser might not support 'view-source'...
        boolean hasSource = browser.isPageSourceSupported();
        if (!hasSource) {
            try { sleep(context.getPollWaitMs()); } catch (InterruptedException e) {}
            return isBrowserLoadComplete();
        }

        int successCount = 0;
        int speed = context.getIntData(OPT_WAIT_SPEED, getDefaultInt(OPT_WAIT_SPEED));
        long endTime = System.currentTimeMillis() + maxWait;

        try {
            String oldSource = driver.getPageSource();

            do {
                sleep(MIN_STABILITY_WAIT_MS);

                String newSource = driver.getPageSource();

                if (isBrowserLoadComplete() && StringUtils.equals(oldSource, newSource)) {
                    successCount += 1;
                    // compare is successful, but we'll keep trying until COMPARE_TOLERANCE is reached
                    if (successCount >= speed) { return true; }
                } else {
                    successCount = 0;
                    // compare didn't work.. but let's wait until maxWait is reached before declaring failure
                    oldSource = newSource;
                }
            } while (System.currentTimeMillis() < endTime);
        } catch (Throwable e) {
            // exception thrown because a JS alert is "blocking" the browser.. in this case we consider the page as "loaded"
            if (StringUtils.containsAny(e.getMessage(), "Modal", "modal dialog") ||
                e instanceof UnhandledAlertException) {
                return true;
            }

            if (successCount == 0) {
                // failed at first try
                if (System.currentTimeMillis() > endTime) {
                    // times up
                    ConsoleUtils.log("browser stability unknown; exceeded allotted page load timeout");
                    return isBrowserLoadComplete();
                } else {
                    WebDriverWait waiter = new WebDriverWait(driver, maxWait);
                    return waiter.until(driver -> isBrowserLoadComplete());
                }
            }

            log("Unable to determine browser's stability: " + e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }

        return false;
    }

    protected boolean isBrowserLoadComplete() {
        ensureReady();

        JavascriptExecutor jsExecutor = (JavascriptExecutor) this.driver;

        String readyState;
        try {
            readyState = (String) jsExecutor.executeScript("return document.readyState");
        } catch (Exception e) {
            log("Unable to execute [document.readyState] due to " + e.getMessage() + ". Trying another approach...");
            try {
                // fail-safe retry
                readyState = (String) jsExecutor.executeScript(
                    "return selenium.browserbot.getCurrentWindow().document.readyState");
            } catch (Exception e1) {
                log("Unable to evaluate browser readyState: " + e1.getMessage());
                return false;
            }
        }

        return StringUtils.equals("complete", StringUtils.trim(readyState));
    }

    protected boolean waitForCondition(long maxWaitMs, Function<WebDriver, Object> condition) {
        ensureReady();

        FluentWait<WebDriver> waiter = newFluentWait();

        try {
            Object target = waiter.until(condition);
            if (target instanceof WebElement) { highlight((WebElement) target); }
            return target != null;
        } catch (TimeoutException e) {
            log("Condition not be met on the current browser within specified wait time of " + maxWaitMs + " ms");
        } catch (WebDriverException e) {
            log("Error while waiting for a condition to be met on the current browser: " +
                WebDriverExceptionHelper.resolveErrorMessage(e));
        } catch (Exception e) {
            log("Error while waiting for browser to stabilize: " + e.getMessage());
        }

        return false;
    }

    protected void scrollIntoView(WebElement element) {
        if (element != null && context.getBooleanData(SCROLL_INTO_VIEW, getDefaultBool(SCROLL_INTO_VIEW))) {
            jsExecutor.executeScript(JS_SCROLL_INTO_VIEW, element);
        }
    }

    protected boolean scrollTo(Locatable element) throws ElementNotVisibleException {
        Coordinates coordinates = element.getCoordinates();
        if (coordinates == null) { return false; }

        if (browser.isRunSafari()) {
            jsExecutor.executeScript("arguments[0].scrollIntoViewIfNeeded();", element);
            return true;
        }

        Point pagePosition = coordinates.onPage();
        // either we are already in view, or this is not suitable for scrolling (i.e. Window, Document, Html object)
        if (pagePosition.getX() < 10 && pagePosition.getY() < 10) { return true; }

        // according to Coordinates' Javadoc: "... This method automatically scrolls the page and/or
        // frames to make element visible in viewport before calculating its coordinates"
        Point topLeft = coordinates.inViewPort();
        if (context.isVerbose()) { log("scrolled to " + topLeft.getX() + "/" + topLeft.getY()); }
        return true;
    }

    protected byte[] downloadLink(String sessionName, String url) {
        // sanity check
        if (ws == null) { fail("command type 'ws' is not available. " + MSG_CHECK_SUPPORT); }
        if (cookie == null) { fail("command type 'wscookie' is not available. " + MSG_CHECK_SUPPORT); }
        requiresNotBlank(url, "valid/full URL or property reference required", url);

        String cookieVar = NAMESPACE + "downloadCookies";
        String wsRespVar = NAMESPACE + "downloadResponse";
        String safeUrl = hideAuthDetails(url);

        try {
            // in case we need to use specific cookie(s) for the HTTP GET
            if (StringUtils.isBlank(sessionName) || context.isNullValue(sessionName)) {
                if (driver != null) { cookie.saveAll(cookieVar); }
            } else {
                // since session name is specified, we need to get cookie value from running browser
                if (driver == null) {
                    fail("sessionIdName='" + sessionName + "' is NOT COMPATIBLE with " + OPT_DELAY_BROWSER + "=true");
                    return null;
                }

                Cookie sessionCookie = cookie.getCookie(sessionName);
                if (sessionCookie == null || StringUtils.isBlank(sessionCookie.getValue())) {
                    fail("Unable to download - unable to switch to session-bound window");
                    return null;
                }

                cookie.save(cookieVar, sessionName);
            }

            if (context.hasData(cookieVar)) {
                StepResult result = ws.headerByVar("Cookie", cookieVar);
                if (result.failed()) {
                    fail("Failed to download from '" + safeUrl + "': " + result.getMessage());
                    return null;
                }
            }

            StepResult result = ws.get(url, null, wsRespVar);
            if (result.failed()) {
                fail("Failed to download from '" + safeUrl + "': " + result.getMessage());
                return null;
            }

            Object response = context.getObjectData(wsRespVar);
            if (!(response instanceof Response)) {
                fail("Failed to download from '" + safeUrl + "': valid HTTP response; check log for details");
                return null;
            }

            return ((Response) response).getRawBody();
        } finally {
            context.removeData(cookieVar);
            context.removeData(wsRespVar);
        }
    }

    protected String validateUrl(String url) {
        if (FileUtil.isFileReadable(url)) { url = "file://" + StringUtils.replace(url, "\\", "/"); }
        requires(RegexUtils.isExact(StringUtils.lowerCase(url), REGEX_VALID_WEB_PROTOCOL), "invalid URL", url);
        return fixURL(url);
    }

    /** IE8-specific fix by adding randomized number sequence to URL to prevent overly-intelligent-caching by IE. */
    protected String fixURL(String url) {
        if (browser.isRunIE() && browser.getMajorVersion() == 8) { url = addNoCacheRandom(url); }
        return url;
    }

    protected String addNoCacheRandom(String url) {
        int indexAfterHttp = StringUtils.indexOf(url, "//") + 2;
        if (StringUtils.indexOf(url, "/", indexAfterHttp) == -1) { url += "/"; }

        // add random 'prevent-cache' tail request param
        url += (StringUtils.contains(url, "&") ? "&" : "?") + "random=" + RandomStringUtils.random(16, false, true);
        return url;
    }

    /** add logging to browser's console (via console.log call) */
    protected void logToBrowserConsole(String message) {
        if (!logToBrowser) { return; }
        if (driver == null) { return; }
        if (browser == null) { return; }
        if (!browser.getBrowserType().isConsoleLoggingEnabled()) { return; }

        jsExecutor.executeScript("console.log(\"[nexial]>> " + StringUtils.replace(message, "\"", "\\\"") + "\");");
    }

    // todo need to test
    protected WebElement findExactlyOneElement(String xpath) throws IllegalArgumentException {
        requiresNotBlank(xpath, "xpath is empty");

        ensureReady();

        List<WebElement> elements = findElements(xpath);
        if (CollectionUtils.isEmpty(elements)) {
            throw new IllegalArgumentException("No element found via locator '" + xpath + "'");
        }

        // exactly one
        if (elements.size() == 1) { return elements.get(0); }

        // more than one? could be browser DOM cache; check it out
        WebElement matched = null;
        for (WebElement element : elements) {
            Point location = element.getLocation();

            // impossible position... likely not the one we want
            if (location.getX() == 0 && location.getY() == 0) { continue; }

            // text not reflect our filter... likely not the one we want
            String elementText = element.getText();
            if (StringUtils.isBlank(elementText)) { continue; }
            if (!StringUtils.contains(xpath, elementText)) { continue; }

            if (matched == null) {
                // found one, and this is the only one so far..
                matched = element;
            } else {
                // found another one, hence failure is in order
                throw new IllegalArgumentException("More than 1 element found locator '" + xpath + "'");
            }
        }

        if (matched == null) { throw new IllegalArgumentException("No element matched via locator '" + xpath + "'"); }

        return matched;
    }

    protected void highlight(String locator) {
        if (!isHighlightEnabled()) { return; }
        highlight(toElement(context.replaceTokens(locator)));
    }

    protected void highlight(WebElement element) {
        if (!isHighlightEnabled()) { return; }
        if (element == null) { return; }
        // if (!element.isDisplayed()) { return; }

        // if we can't scroll to it, then we won't highlight it
        if (scrollTo((Locatable) element)) {
            int waitMs = context.hasData(HIGHLIGHT_WAIT_MS) ?
                         context.getIntData(HIGHLIGHT_WAIT_MS) :
                         context.getIntData(HIGHLIGHT_WAIT_MS_OLD, getDefaultInt(HIGHLIGHT_WAIT_MS));
            String highlight = context.getStringData(HIGHLIGHT_STYLE, getDefault(HIGHLIGHT_STYLE));
            jsExecutor.executeScript("var ws = arguments[0];" +
                                     "var oldStyle = arguments[0].getAttribute('style');" +
                                     "ws.setAttribute('style', arguments[1]);" +
                                     "setTimeout(function () { ws.setAttribute('style', oldStyle); }, " + waitMs + ");",
                                     element, highlight);
        }
    }

    // todo incomplete... need more testing
    protected void highlight(TestStep teststep) {
        if (!isHighlightEnabled()) { return; }

        String cmd = teststep.getCommand();
        if (StringUtils.contains(cmd, "()") || !StringUtils.contains(cmd, "(")) { return; }

        String parameters = StringUtils.substringBefore(StringUtils.substringAfter(cmd, "("), ")");
        List<String> paramList = TextUtils.toList(parameters, ",", true);

        for (int i = 0; i < paramList.size(); i++) {
            String param = paramList.get(i);
            if (StringUtils.equals(param, "locator")) {
                try {
                    highlight(teststep.getParams().get(i));
                } catch (Throwable e) {
                    // don't complain... this is debugging freebie..
                }
                break;
            }
        }
    }

    // todo need to test
    protected String findActiveElementId() throws NoSuchElementException {
        ensureReady();
        Object ret = jsExecutor.executeScript("return document.activeElement == null ? '' : document.activeElement.id");
        if (ret == null) { throw new NoSuchElementException("No active element or element has no ID"); }
        return StringUtils.trim((String) ret);
    }

    // todo need to test
    protected boolean isChecked(String locator) { return toElement(locator).isSelected(); }

    // todo need to test
    protected boolean isVisible(String locator) {
        WebElement element = findElement(locator);
        if (element == null) {
            log("element '" + locator + "' not found, hence considered as 'NOT VISIBLE'");
            return false;
        }
        return element.isDisplayed();
    }

    protected void focus(WebElement element) {
        if (element == null) { throw new NullPointerException("element is null"); }
        if (!element.isDisplayed()) { throw new IllegalArgumentException("element is not displayed"); }
        if (!element.isEnabled()) { throw new IllegalArgumentException("element is not enabled"); }

        new Actions(driver).moveToElement(element).build().perform();

        String tagName = element.getTagName();
        if (StringUtils.equalsIgnoreCase(tagName, "input") &&
            StringUtils.equalsIgnoreCase(element.getAttribute("type"), "text")) {
            element.sendKeys("");
            return;
        }

        if (StringUtils.equalsIgnoreCase(tagName, "textarea")) { element.sendKeys(""); }
    }

    protected void tryFocus(WebElement element) {
        try {
            focus(element);
            wait("250");
        } catch (Exception e) {
            log("unable to execute focus on target element: " + e.getMessage());
        }
    }

    protected boolean isHighlightEnabled() {
        return !browser.getBrowserType().isHeadless() &&
               context.hasData(OPT_DEBUG_HIGHLIGHT) ?
               context.getBooleanData(OPT_DEBUG_HIGHLIGHT) :
               context.getBooleanData(OPT_DEBUG_HIGHLIGHT_OLD, getDefaultBool(OPT_DEBUG_HIGHLIGHT));
    }

    protected boolean shouldWait() { return context.getBooleanData(WEB_ALWAYS_WAIT, getDefaultBool(WEB_ALWAYS_WAIT)); }

    protected StepResult saveTextSubstring(String var, String locator, String delimStart, String delimEnd) {
        List<WebElement> matches = findElements(locator);
        if (CollectionUtils.isEmpty(matches)) {
            context.removeData(var);
            return StepResult.success("No matches found; ${" + var + "} removed from context");
        }

        if (matches.size() == 1) { return saveSubstring(matches.get(0).getText(), delimStart, delimEnd, var); }

        List<String> textArray = matches.stream().map(WebElement::getText).collect(Collectors.toList());

        return saveSubstring(textArray, delimStart, delimEnd, var);
    }
}
