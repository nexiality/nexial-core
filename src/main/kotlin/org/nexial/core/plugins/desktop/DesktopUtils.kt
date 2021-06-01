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
package org.nexial.core.plugins.desktop

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.CollectionUtil
import org.nexial.commons.utils.TextUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.Desktop.AUTOSCAN_INFRAGISTICS4_AWARE
import org.nexial.core.NexialConst.NL
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.plugins.desktop.DesktopConst.*
import org.nexial.core.plugins.desktop.ElementType.*
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.By
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.WebElement
import org.openqa.selenium.winium.WiniumDriver
import java.util.*

internal object DesktopUtils {
    @JvmStatic
    fun printDetails(element: WebElement?) =
        if (element == null) null
        else
            StringUtils.rightPad("@AutomationId=", DEF_OUTPUT_LABEL_WIDTH) + element.getAttribute("AutomationId") + NL +
            StringUtils.rightPad("@Name=", DEF_OUTPUT_LABEL_WIDTH) + element.getAttribute("Name") + NL +
            StringUtils.rightPad("@ControlType=", DEF_OUTPUT_LABEL_WIDTH) + element.getAttribute("ControlType") + NL +
            StringUtils.rightPad("@BoundingRectangle=", DEF_OUTPUT_LABEL_WIDTH) +
            element.getAttribute("BoundingRectangle") + NL

    @JvmStatic
    fun printDetails(element: DesktopElement?) =
        if (element == null) null
        else
            StringUtils.rightPad("element type=", DEF_OUTPUT_LABEL_WIDTH) + element.elementType + NL +
            StringUtils.rightPad("editable=", DEF_OUTPUT_LABEL_WIDTH) + element.editable + NL +
            StringUtils.rightPad("label=", DEF_OUTPUT_LABEL_WIDTH) + element.label + NL +
            StringUtils.rightPad("name=", DEF_OUTPUT_LABEL_WIDTH) + element.name + NL +
            StringUtils.rightPad("control type=", DEF_OUTPUT_LABEL_WIDTH) + element.controlType + NL +
            StringUtils.rightPad("automation id=", DEF_OUTPUT_LABEL_WIDTH) + element.automationId + NL +
            StringUtils.rightPad("bound=", DEF_OUTPUT_LABEL_WIDTH) +
            if (element.element == null) "UNKNOWN" else element.element.getAttribute("BoundingRectangle") + NL

    @JvmStatic
    fun toShortcutText(text: String) =
        if (StringUtils.isEmpty(text)) "" else SCRIPT_PREFIX_SHORTCUT + TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX

    internal fun appendShortcutText(shortcut: String, text: String) =
        if (StringUtils.isEmpty(text)) shortcut
        else StringUtils.defaultIfEmpty(shortcut, SCRIPT_PREFIX_SHORTCUT) +
             TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX

    @JvmStatic
    fun toShortcuts(vararg shortcuts: String) =
        if (ArrayUtils.isEmpty(shortcuts)) ""
        else SCRIPT_PREFIX_SHORTCUT + shortcuts.joinToString { SHORTCUT_PREFIX + it + SHORTCUT_POSTFIX }

    /**
     * `shortcuts` could be a mix of function keys and 'normal' keys
     */
    @JvmStatic
    fun joinShortcuts(vararg shortcuts: String) =
        if (ArrayUtils.isEmpty(shortcuts)) ""
        else {
            val shortcutList = mutableListOf<String>()
            var keys = StringUtils.replace(StringUtils.remove(shortcuts.joinToString(""), "\r"), "\n", "[ENTER]")
            while (StringUtils.isNotEmpty(keys)) {
                val shortcut = StringUtils.substringBetween(keys, "[", "]")
                if (StringUtils.isNotBlank(shortcut) && StringUtils.containsNone(shortcut, " ")) {
                    val beforeShortcut = StringUtils.substringBefore(keys, "[$shortcut]")
                    if (StringUtils.isNotEmpty(beforeShortcut))
                        shortcutList.add(TEXT_INPUT_PREFIX + beforeShortcut + TEXT_INPUT_POSTFIX)
                    shortcutList.add("$SHORTCUT_PREFIX$shortcut$SHORTCUT_POSTFIX")
                    keys = StringUtils.substringAfter(keys, "[$shortcut]")
                } else {
                    shortcutList.add(TEXT_INPUT_PREFIX + keys + TEXT_INPUT_POSTFIX)
                    break
                }
            }
            shortcutList.joinToString("")
        }

    @JvmStatic
    fun forceShortcutSyntax(text: String): String {
        if (StringUtils.isEmpty(text)) return text

        // <[{ ... }]>
        if (TextUtils.isBetween(text, TEXT_INPUT_PREFIX, TEXT_INPUT_POSTFIX)) return text

        // <[ ... ]>
        if (TextUtils.isBetween(text, SHORTCUT_PREFIX, SHORTCUT_POSTFIX)) return text

        // [...]
        return if (TextUtils.isBetween(text, "[", "]"))
            SHORTCUT_PREFIX + StringUtils.removeEnd(StringUtils.removeStart(text, "["), "]") + SHORTCUT_POSTFIX
        else
            TEXT_INPUT_PREFIX + text + TEXT_INPUT_POSTFIX
    }

    @JvmStatic
    fun addShortcut(shortcuts: String, newShortcut: String): String {
        if (StringUtils.isEmpty(newShortcut)) return shortcuts
        if (TextUtils.isBetween(newShortcut, "[", "]"))
            return shortcuts + SHORTCUT_PREFIX + StringUtils.substringBetween(newShortcut, "[", "]") + SHORTCUT_POSTFIX

        // append text
        return when {
            StringUtils.endsWith(shortcuts, TEXT_INPUT_POSTFIX) ->
                StringUtils.substringBeforeLast(shortcuts, TEXT_INPUT_POSTFIX) + newShortcut + TEXT_INPUT_POSTFIX
            StringUtils.endsWith(shortcuts, SHORTCUT_POSTFIX)   ->
                shortcuts + SHORTCUT_PREFIX + "CTRL-END" + SHORTCUT_POSTFIX +
                TEXT_INPUT_PREFIX + newShortcut + TEXT_INPUT_POSTFIX
            else                                                ->
                shortcuts + TEXT_INPUT_PREFIX + newShortcut + TEXT_INPUT_POSTFIX
        }
    }

    internal fun toKeystrokes(keys: String?): String {
        // clean up input
        var text = keys
        val keystrokes = StringBuilder()
        while (StringUtils.isNotEmpty(text)) {
            var input = StringUtils.substringBetween(text, SHORTCUT_PREFIX, SHORTCUT_POSTFIX)
            if (StringUtils.isNotEmpty(input)) {
                // got a pair
                input = SHORTCUT_PREFIX + input + SHORTCUT_POSTFIX
                if (!StringUtils.startsWith(text, input)) {
                    val textInput = StringUtils.substringBefore(text, SHORTCUT_PREFIX)
                    text = StringUtils.substringAfter(text, textInput)
                    keystrokes.append(TEXT_INPUT_PREFIX).append(textInput).append(TEXT_INPUT_POSTFIX)
                }
                text = StringUtils.substringAfter(text, input)
                keystrokes.append(input)
            } else {
                keystrokes.append(TEXT_INPUT_PREFIX).append(text).append(TEXT_INPUT_POSTFIX)
                break
            }
        }
        return keystrokes.toString()
    }

    internal fun hasText(element: WebElement?) = isValuePatternAvailable(element)

    @JvmStatic
    fun isValuePatternAvailable(element: WebElement?) = isAttrTrue(element, ATTR_IS_VALUE_PATTERN_AVAILABLE)

    @JvmStatic
    fun isSelectionPatternAvailable(target: WebElement?) = isAttrTrue(target, "IsSelectionPatternAvailable")

    @JvmStatic
    fun isTextPatternAvailable(target: WebElement?) = isAttrTrue(target, "IsTextPatternAvailable")

    @JvmStatic
    fun isExpandCollapsePatternAvailable(target: WebElement?) = isAttrTrue(target, "IsExpandCollapsePatternAvailable")

    @JvmStatic
    fun isInvokePatternAvailable(target: WebElement?) = isAttrTrue(target, "IsInvokePatternAvailable")

    @JvmStatic
    fun isTogglePatternAvailable(target: WebElement?) = isAttrTrue(target, "IsTogglePatternAvailable")

    @JvmStatic
    fun isEnabled(target: WebElement?) = isAttrTrue(target, "IsEnabled")

    @JvmStatic
    fun isKeyboardFocusable(target: WebElement?) = isAttrTrue(target, "IsKeyboardFocusable")

    private fun isAttrTrue(target: WebElement?, attr: String) =
        target != null && BooleanUtils.toBoolean(target.getAttribute(attr))

    @JvmStatic
    fun formatLabel(label: String?): String =
        StringUtils.trim(StringUtils.removeEnd(StringUtils.trim(label), ":"))

    @JvmStatic
    fun xpathSafeValue(text: String) = if (StringUtils.contains(text, "'")) "\"" + text + "\"" else "'$text'"

    @JvmStatic
    fun treatQuoteString(arg: String): String =
        if (StringUtils.startsWith(arg, "\"") && StringUtils.endsWith(arg, "\""))
            StringUtils.substringBetween(arg, "\"", "\"")
        else arg

    @JvmStatic
    fun clearModalDialog(driver: WiniumDriver?, baseXpath: String) {
        val button = findModalDialogCloseButton(driver, baseXpath) ?: return
        ConsoleUtils.log("found modal dialog with close button, clearing out modal dialog...")
        button.click()
    }

    private fun findModalDialogCloseButton(driver: WiniumDriver?, baseXpath: String): WebElement? {
        requireNotNull(driver) { "driver is null" }
        require(!StringUtils.isBlank(baseXpath)) { "baseXpath is empty/blank" }
        val elements = driver.findElements(By.xpath(baseXpath + LOCATOR_DIALOG_CLOSE_BUTTON))
        return if (CollectionUtils.isEmpty(elements)) null else elements[0]
    }

    @JvmStatic
    fun findModalDialog(driver: WiniumDriver?, baseXpath: String?): WebElement? {
        requireNotNull(driver) { "driver is null" }
        requiresNotBlank(baseXpath, "baseXpath is empty/blank")

        // try { Thread.sleep(1500);} catch (InterruptedException e) {}
        val xpathModalDialog = StringUtils.appendIfMissing(baseXpath, "/") + LOCATOR_MODAL_DIALOG
        val elements = driver.findElements(By.xpath(xpathModalDialog))
        return if (CollectionUtils.isEmpty(elements)) null else elements[0]
    }

    @JvmStatic
    fun isAttributeMatched(element: WebElement?, attribute: String?, vararg matchedTo: String): Boolean {
        if (element == null || StringUtils.isBlank(attribute) || ArrayUtils.isEmpty(matchedTo)) return false
        val value = StringUtils.trim(element.getAttribute(attribute))
        return Arrays.stream(matchedTo).anyMatch { match: String? -> StringUtils.equals(match, value) }
    }

    @JvmStatic
    internal fun isRadio(elem: WebElement?) =
        elem != null && StringUtils.equals(elem.getAttribute("ControlType"), RADIO)

    @JvmStatic
    fun isCheckbox(elem: WebElement?) =
        elem != null && StringUtils.equals(elem.getAttribute("ControlType"), CHECK_BOX)

    @JvmStatic
    fun isCheckboxOrRadio(elem: WebElement?) =
        elem != null && StringUtils.equalsAny(elem.getAttribute("ControlType"), CHECK_BOX, RADIO)

    @JvmStatic
    fun isCheckboxOrRadio(elem: DesktopElement?) = elem != null && isCheckboxOrRadio(elem.getElement())

    @JvmStatic
    fun isChecked(elem: WebElement) = isCheckbox(elem) && checkboxStatus(elem)

    @JvmStatic
    fun isChecked(elem: DesktopElement) = isChecked(elem.getElement())

    @JvmStatic
    fun isUnchecked(elem: WebElement) = isCheckbox(elem) && !checkboxStatus(elem)

    @JvmStatic
    fun isUnchecked(elem: DesktopElement) = isUnchecked(elem.getElement())

    @JvmStatic
    fun checkboxStatus(elem: WebElement) =
        if (isTogglePatternAvailable(elem)) elem.isSelected else BooleanUtils.toBoolean(getElementText(elem))

    @JvmStatic
    fun countChildren(elem: WebElement?) =
        if (elem == null) 0 else CollectionUtils.size(elem.findElements(By.xpath("*")))

    @JvmStatic
    fun countChildren(element: WebElement, filter: (child: WebElement) -> Boolean): Int {
        // we don't know if there are any row in this data
        // so we use "*" to all children -- this is faster
        val children = element.findElements(By.xpath("*"))
        if (CollectionUtils.isEmpty(children)) return 0
        return children.sumBy { if (filter(it)) 1 else 0 }
    }

    @JvmStatic
    fun isSelected(elem: WebElement): Boolean {
        val isSelected = elem.getAttribute("IsSelected")
        return if (StringUtils.isBlank(isSelected)) elem.isSelected else StringUtils.equals(isSelected, "True")
    }

    @JvmStatic
    fun isValidDataRow(elem: WebElement): Boolean {
        return StringUtils.equals(elem.getAttribute("ControlType"), TREE_VIEW_ROW) &&
               !StringUtils.equals(elem.getAttribute("AutomationId"), "-1")
    }

    @JvmStatic
    fun getElementText(element: WebElement?, defaultText: String): String =
        if (element == null) defaultText
        else try {
            StringUtils.trim(element.text)
        } catch (e: Exception) {
            defaultText
        }

    @JvmStatic
    fun getElementText(element: WebElement?) =
        if (element == null) null
        else try {
            StringUtils.trim(element.text)
        } catch (e: WebDriverException) {
            null
        }

    @JvmStatic
    fun isInfragistic4Aware(): Boolean {
        val context = ExecutionThread.get() ?: return false
        return context.getBooleanData(AUTOSCAN_INFRAGISTICS4_AWARE, getDefaultBool(AUTOSCAN_INFRAGISTICS4_AWARE))
    }

    @JvmStatic
    fun infragistic4Text(element: WebElement): String? {
        if (!isInfragistic4Aware()) return getElementText(element)

        val itemStatus = element.getAttribute("ItemStatus")
        return if (StringUtils.isBlank(itemStatus)) null
        else StringUtils.substringBetween(itemStatus, INFRAG4_ITEM_STATUS_PREFIX, INFRAG4_ITEM_STATUS_POSTFIX)
    }

    @JvmStatic
    fun findFirstElement(element: WebElement?, xpath: String?) =
        if (element == null || StringUtils.isBlank(xpath)) null
        else CollectionUtil.getOrDefault(element.findElements(By.xpath(xpath)), 0, null)

}