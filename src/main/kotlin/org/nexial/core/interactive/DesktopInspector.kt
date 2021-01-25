package org.nexial.core.interactive

import org.apache.commons.lang3.StringUtils
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.desktop.DesktopCommand
import org.nexial.core.plugins.desktop.ElementType
import org.nexial.core.plugins.desktop.WiniumUtils
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.WebElement
import org.springframework.util.CollectionUtils

class DesktopInspector(val context: ExecutionContext) {

    fun inspect(locator: String, action: String, input: String) {
        val command = context.findPlugin("desktop")
                      ?: throw Exception("Looks like desktop command has not yet run; driver not found")
        val desktop = command as DesktopCommand

        val winiumDriver = WiniumUtils.joinRunningApp()

        val useXpath = StringUtils.startsWith(locator, "/*") || StringUtils.startsWith(locator, "//*")

        val elements = if (useXpath) {
            val matches = desktop.findElements(locator)
            if (CollectionUtils.isEmpty(matches)) {
                println("No element found via locator $locator")
                return
            }

            matches
        } else {
            // special cases: app
            if (locator == "app") {
                if (action == "menu") {
                    if (StringUtils.isBlank(input)) println("No menu item specified") else desktop.clickMenu(input)
                } else
                    println("Unsupported action $action")

                return
            } else {
                val component = desktop.getRequiredElement(locator, ElementType.Any)
                if (component == null) {
                    println("Unable to resolve to a valid component via label '$locator'")
                    return
                }

                arrayListOf<WebElement>(component.element)
            }
        }

        println("${elements.size} element(s) found via $locator")
        elements.forEachIndexed { i, element ->
            println("Element ${i + 1}: (${element::class.java.simpleName})")
            if (StringUtils.isBlank(action)) {
                showElementDetails(elements)
            } else {
                when {
                    StringUtils.equalsIgnoreCase(action, "click")       -> {
                        element.click()
                        desktop.autoClearModalDialog(locator)
                    }
                    StringUtils.equalsIgnoreCase(action, "doubleClick") -> {
                        desktop.doubleClickByLocator(locator)
                        desktop.autoClearModalDialog(locator)
                    }
                    StringUtils.equalsIgnoreCase(action, "type")        ->
                        if (StringUtils.isEmpty(input)) println("No input found for type(); ignored")
                        else desktop.type(element, input)
                    else                                                -> println("Unknown action ${action}; ignored")
                }
            }
        }
    }

    private fun showElementDetails(elements: MutableList<WebElement>) {
        elements.forEachIndexed { i, element ->
            println("Element ${i + 1}: (${element::class.java.simpleName})")

            println("[ IDENTIFICATION ]")
            println("  ${showWebElementAttribute(element, "ClassName")}")
            println("  ${showWebElementAttribute(element, "ControlType")}")
            println("  ${showWebElementAttribute(element, "AutomationId")}")
            println("  ${showWebElementAttribute(element, "LocalizedControlType")}")
            println("  ${showWebElementAttribute(element, "Name")}")

            println("[ DISPLAY ]")
            println("  ${showWebElementProperty("DISPLAYED?", element) { "" + it.isDisplayed }}")
            println("  ${showWebElementProperty("ENABLED", element) { "" + it.isEnabled }}")
            println("  ${showWebElementProperty("SELECTED?", element) { "" + it.isSelected }}")
            println("  ${showWebElementProperty("TEXT", element) { it.text }}")

            println("[ VISIBILITY ]")
            println("  ${showWebElementProperty("SIZE (height,width)", element) { "" + it.size }}")
            println("  ${showWebElementAttribute(element, "BoundingRectangle")}")
            println("  ${showWebElementAttribute(element, "ClickablePoint")}")

            println("[ ATTRIBUTE/PATTERN ]")
            println("  ${showWebElementAttribute(element, "IsContentElement")}")
            println("  ${showWebElementAttribute(element, "IsControlElement")}")
            println("  ${showWebElementAttribute(element, "IsExpandCollapsePatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsGridItemPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsGridPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsInvokePatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsKeyboardFocusable")}")
            println("  ${showWebElementAttribute(element, "IsMultipleViewPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsRangeValuePatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsScrollItemPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsScrollPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsSelectionItemPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsSelectionPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsTableItemPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsTablePatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsTextPatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsTogglePatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsValuePatternAvailable")}")
            println("  ${showWebElementAttribute(element, "IsWindowPatternAvailable")}")

            println("[ WINDOW ]")
            println("  ${showWebElementAttribute(element, "CanMaximize")}")
            println("  ${showWebElementAttribute(element, "CanMinimize")}")
            println("  ${showWebElementAttribute(element, "CanMove")}")
            println("  ${showWebElementAttribute(element, "CanResize")}")
            println("  ${showWebElementAttribute(element, "IsModal")}")

            println("")
        }
    }

    private fun showWebElementAttribute(element: WebElement, attr: String) =
        StringUtils.rightPad(attr, 35) + ": " + try {
            element.getAttribute(attr)
        } catch (e: WebDriverException) {
            "N/A"
        }

    private fun showWebElementProperty(label: String, element: WebElement, prop: (WebElement) -> String) =
        StringUtils.rightPad(label, 35) + ": " + try {
            prop(element)
        } catch (e: WebDriverException) {
            "N/A"
        }


}