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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.winium.WiniumDriver;

import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.plugins.desktop.DesktopConst.*;

final class DesktopUtils {
    private DesktopUtils() { }

    protected static String printDetails(WebElement element) {
        if (element == null) { return null; }

        return StringUtils.rightPad("@AutomationId=", DEF_OUTPUT_LABEL_WIDTH) +
               element.getAttribute("AutomationId") + NL +
               StringUtils.rightPad("@Name=", DEF_OUTPUT_LABEL_WIDTH) + element.getAttribute("Name") + NL +
               StringUtils.rightPad("@ControlType=", DEF_OUTPUT_LABEL_WIDTH) +
               element.getAttribute("ControlType") + NL +
               StringUtils.rightPad("@BoundingRectangle=", DEF_OUTPUT_LABEL_WIDTH) +
               element.getAttribute("BoundingRectangle") + NL;
    }

    protected static String printDetails(DesktopElement element) {
        if (element == null) { return null; }

        return StringUtils.rightPad("element type=", DEF_OUTPUT_LABEL_WIDTH) + element.elementType + NL +
               StringUtils.rightPad("editable=", DEF_OUTPUT_LABEL_WIDTH) + element.editable + NL +
               StringUtils.rightPad("label=", DEF_OUTPUT_LABEL_WIDTH) + element.label + NL +
               StringUtils.rightPad("name=", DEF_OUTPUT_LABEL_WIDTH) + element.name + NL +
               StringUtils.rightPad("control type=", DEF_OUTPUT_LABEL_WIDTH) + element.controlType + NL +
               StringUtils.rightPad("automation id=", DEF_OUTPUT_LABEL_WIDTH) + element.automationId + NL +
               StringUtils.rightPad("bound=", DEF_OUTPUT_LABEL_WIDTH) +
               (element.element == null ? "UNKNOWN" : element.element.getAttribute("BoundingRectangle") + NL);
    }

    protected static String toShortcutText(String text) {
        if (StringUtils.isEmpty(text)) { return ""; }
        return SCRIPT_PREFIX_SHORTCUT + TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX;
    }

    protected static String appendShortcutText(String shortcut, String text) {
        if (StringUtils.isEmpty(text)) { return shortcut; }
        return StringUtils.defaultIfEmpty(shortcut, SCRIPT_PREFIX_SHORTCUT) +
               TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX;
    }

    protected static String toShortcuts(String... shortcuts) {
        if (ArrayUtils.isEmpty(shortcuts)) { return ""; }

        StringBuilder script = new StringBuilder(SCRIPT_PREFIX_SHORTCUT);
        Arrays.stream(shortcuts).forEach(s -> script.append(SHORTCUT_PREFIX).append(s).append(SHORTCUT_POSTFIX));
        return script.toString();
    }

    protected static String appendShortcuts(String shortcut, String... shortcuts) {
        if (ArrayUtils.isEmpty(shortcuts)) { return shortcut; }

        StringBuilder script = new StringBuilder(StringUtils.defaultIfEmpty(shortcut, SCRIPT_PREFIX_SHORTCUT));
        Arrays.stream(shortcuts).forEach(s -> script.append(SHORTCUT_PREFIX).append(s).append(SHORTCUT_POSTFIX));
        return script.toString();
    }

    /**
     * {@code shortcuts} could be a mix of function keys and 'normal' keys
     */
    protected static String joinShortcuts(String... shortcuts) {
        String shortcut = "";
        for (String thisText : shortcuts) {
            if (StringUtils.isNotEmpty(thisText)) {
                shortcut = StringUtils.isEmpty(shortcut) ?
                           forceShortcutSyntax(thisText) : addShortcut(shortcut, thisText);
            }
        }
        return shortcut;
    }

    protected static String treatShortcutSyntax(String text) {
        if (!TextUtils.isBetween(text, "[", "]")) { return text; }
        return StringUtils.replace(StringUtils.replace(text, "[", SHORTCUT_PREFIX), "]", SHORTCUT_POSTFIX);
    }

    protected static String forceShortcutSyntax(String text) {
        if (StringUtils.isEmpty(text)) { return text; }

        // <[{ ... }]>
        if (TextUtils.isBetween(text, TEXT_INPUT_PREFIX, TEXT_INPUT_POSTFIX)) { return text; }

        // <[ ... ]>
        if (TextUtils.isBetween(text, SHORTCUT_PREFIX, SHORTCUT_POSTFIX)) { return text; }

        // [...]
        if (TextUtils.isBetween(text, "[", "]")) {
            return SHORTCUT_PREFIX + StringUtils.removeEnd(StringUtils.removeStart(text, "["), "]") + SHORTCUT_POSTFIX;
        }

        return TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX;
    }

    protected static String addShortcut(String shortcuts, String newShortcut) {
        if (StringUtils.isEmpty(newShortcut)) { return shortcuts; }

        if (TextUtils.isBetween(newShortcut, "[", "]")) {
            return shortcuts + SHORTCUT_PREFIX + StringUtils.substringBetween(newShortcut, "[", "]") + SHORTCUT_POSTFIX;
        }

        // append text
        if (StringUtils.endsWith(shortcuts, TEXT_INPUT_POSTFIX)) {
            return StringUtils.substringBeforeLast(shortcuts, TEXT_INPUT_POSTFIX) + newShortcut + TEXT_INPUT_POSTFIX;
        } else if (StringUtils.endsWith(shortcuts, SHORTCUT_POSTFIX)) {
            return shortcuts +
                   SHORTCUT_PREFIX + "CTRL-END" + SHORTCUT_POSTFIX +
                   TEXT_INPUT_PREFIX + newShortcut + TEXT_INPUT_POSTFIX;
        } else {
            return shortcuts + TEXT_INPUT_PREFIX + newShortcut + TEXT_INPUT_POSTFIX;
        }
    }

    protected static boolean hasText(WebElement element) { return isValuePatternAvailable(element); }

    protected static String toKeystrokes(String text) {
        // clean up input
        StringBuilder keystrokes = new StringBuilder();
        while (StringUtils.isNotEmpty(text)) {
            String input = StringUtils.substringBetween(text, SHORTCUT_PREFIX, SHORTCUT_POSTFIX);
            if (StringUtils.isNotEmpty(input)) {
                // got a pair
                input = SHORTCUT_PREFIX + input + SHORTCUT_POSTFIX;
                if (!StringUtils.startsWith(text, input)) {
                    String textInput = StringUtils.substringBefore(text, SHORTCUT_PREFIX);
                    text = StringUtils.substringAfter(text, textInput);
                    keystrokes.append(TEXT_INPUT_PREFIX).append(textInput).append(TEXT_INPUT_POSTFIX);
                }
                text = StringUtils.substringAfter(text, input);
                keystrokes.append(input);
            } else {
                keystrokes.append(TEXT_INPUT_PREFIX).append(text).append(TEXT_INPUT_POSTFIX);
                break;
            }
        }

        return keystrokes.toString();
    }

    protected static boolean isValuePatternAvailable(WebElement element) {
        return element != null && BooleanUtils.toBoolean(element.getAttribute(ATTR_IS_VALUE_PATTERN_AVAILABLE));
    }

    protected static boolean isSelectionPatternAvailable(WebElement target) {
        return target != null && BooleanUtils.toBoolean(target.getAttribute("IsSelectionPatternAvailable"));
    }

    protected static boolean isTextPatternAvailable(WebElement target) {
        return target != null && BooleanUtils.toBoolean(target.getAttribute("IsTextPatternAvailable"));
    }

    protected static boolean isExpandCollapsePatternAvailable(WebElement target) {
        return target != null && BooleanUtils.toBoolean(target.getAttribute("IsExpandCollapsePatternAvailable"));
    }

    protected static boolean isInvokePatternAvailable(WebElement target) {
        return target != null && BooleanUtils.toBoolean(target.getAttribute("IsInvokePatternAvailable"));
    }

    protected static boolean isEnabled(WebElement target) {
        return target != null && BooleanUtils.toBoolean(target.getAttribute("IsEnabled"));
    }

    protected static boolean isKeyboardFocusable(WebElement target) {
        return target != null && BooleanUtils.toBoolean(target.getAttribute("IsKeyboardFocusable"));
    }

    protected static String formatLabel(String label) {
        return StringUtils.trim(StringUtils.removeEnd(StringUtils.trim(label), ":"));
    }

    protected static String xpathSafeValue(String text) {
        if (StringUtils.contains(text, "'")) { return "\"" + text + "\""; }
        return "'" + text + "'";
    }

    protected static String treatQuoteString(String arg) {
        if (StringUtils.startsWith(arg, "\"") && StringUtils.endsWith(arg, "\"")) {
            return StringUtils.substringBetween(arg, "\"", "\"");
        }
        return arg;
    }

    protected static void clearModalDialog(WiniumDriver driver, String baseXpath) {
        WebElement button = findModalDialogCloseButton(driver, baseXpath);
        if (button == null) { return; }

        ConsoleUtils.log("found modal dialog with close button, clearing out modal dialog...");
        button.click();
    }

    protected static WebElement findModalDialogCloseButton(WiniumDriver driver, String baseXpath) {
        if (driver == null) { throw new IllegalArgumentException("driver is null"); }
        if (StringUtils.isBlank(baseXpath)) { throw new IllegalArgumentException("baseXpath is empty/blank"); }

        String xpathCloseDialog = baseXpath + LOCATOR_DIALOG_CLOSE_BUTTON;
        List<WebElement> elements = driver.findElements(By.xpath(xpathCloseDialog));
        if (CollectionUtils.isEmpty(elements)) { return null; }
        return elements.get(0);
    }

    protected static WebElement findModalDialog(WiniumDriver driver, String baseXpath) {
        if (driver == null) { throw new IllegalArgumentException("driver is null"); }
        if (StringUtils.isBlank(baseXpath)) { throw new IllegalArgumentException("baseXpath is empty/blank"); }

        // try { Thread.sleep(1500);} catch (InterruptedException e) {}

        String xpathModalDialog = StringUtils.appendIfMissing(baseXpath, "/") + LOCATOR_MODAL_DIALOG;
        List<WebElement> elements = driver.findElements(By.xpath(xpathModalDialog));
        if (CollectionUtils.isEmpty(elements)) { return null; }

        return elements.get(0);
    }

}
