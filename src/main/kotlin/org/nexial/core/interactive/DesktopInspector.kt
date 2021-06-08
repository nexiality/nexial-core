package org.nexial.core.interactive

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.reflect.MethodUtils
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.NexialCommand
import org.nexial.core.plugins.desktop.DesktopCommand
import org.nexial.core.plugins.desktop.ElementType
import org.nexial.core.plugins.desktop.WiniumUtils
import org.openqa.selenium.By
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.WebElement
import org.openqa.selenium.winium.WiniumDriver
import org.springframework.util.CollectionUtils

class DesktopInspector(val context: ExecutionContext) {

    fun inspect(locator: String, action: String, input: String) {
        val command = context.findPlugin("desktop")
                      ?: throw Exception("Looks like desktop command has not yet run; driver not found")
        val desktop = command as DesktopCommand
        val driver = resolveDesktopDriver(command)

        val useXpath = StringUtils.startsWith(locator, "/*") || StringUtils.startsWith(locator, "//*")

        val elements = if (useXpath) {
            val matches = driver.findElements(By.xpath(locator))
            // val matches = desktop.findElements(locator)
            if (CollectionUtils.isEmpty(matches)) {
                println("No element found via locator $locator")
                return
            }

            matches
        } else {
            // special cases: app
            if (locator == "app") {
                if (action == "menu")
                    if (StringUtils.isBlank(input)) println("No menu item specified") else desktop.clickMenu(input)
                else
                    println("Unsupported action $action")
                return
            } else {
                // autoscan components
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
                showElementDetails(element)
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
                    StringUtils.equalsIgnoreCase(action, "menu")        -> menu(desktop, element, input)
                    StringUtils.equalsIgnoreCase(action, "context")     -> contextMenu(desktop, element, input)
                    else                                                -> println("Unknown action ${action}; ignored")
                }
            }
        }
    }

    private fun resolveDesktopDriver(command: NexialCommand?) =
        if (command != null) {
            val driver = MethodUtils.invokeMethod(command, true, "getDriver")
            if (driver != null) driver as WiniumDriver else WiniumUtils.joinRunningApp()
        } else
            WiniumUtils.joinRunningApp()

    /**
     * right click, and then specified menu item(s)
     */
    private fun contextMenu(desktop: DesktopCommand, element: WebElement, input: String) {
        if (StringUtils.isEmpty(input)) return
        resolveDesktopDriver(desktop)
        desktop.contextMenu(element, input)
    }

    private fun menu(desktop: DesktopCommand, element: WebElement, input: String) {
        if (StringUtils.isEmpty(input)) return
        resolveDesktopDriver(desktop)
        desktop.clickMenu(element, input)
    }

    private fun showElementDetails(element: WebElement) {
        println("[ IDENTIFICATION ]")
        println("  ${showWebElementAttribute(element, "ClassName")}")
        println("  ${showWebElementAttribute(element, "ControlType")}")
        println("  ${showWebElementAttribute(element, "AutomationId")}")
        println("  ${showWebElementAttribute(element, "LocalizedControlType")}")
        println("  ${showWebElementAttribute(element, "Name")}")

        println("[ DISPLAY ]")
        println("  ${showWebElementProperty("Displayed?", element) { "" + it.isDisplayed }}")
        println("  ${showWebElementProperty("Enabled?", element) { "" + it.isEnabled }}")
        println("  ${showWebElementProperty("Selected?", element) { "" + it.isSelected }}")
        println("  ${showWebElementProperty("Text", element) { it.text }}")

        println("[ VISIBILITY ]")
        println("  ${
            showWebElementProperty("Size (width,height)", element) {
                try {
                    it.size.toString()
                } catch (e: Exception) {
                    // no worries...
                    "N/A"
                }
            }
        }")
        println("  ${
            showWebElementProperty("Dimension (width,height,x,y)", element) {
                val rect = it.getAttribute("BoundingRectangle")
                if (StringUtils.isBlank(rect)) "N/A" else "(${StringUtils.replace(rect, ",", ", ")})"
            }
        }")
        println("  ${showWebElementAttribute(element, "ClickablePoint")}")

        println("[ ATTRIBUTE/PATTERN ]")
        printValidWebElementAttribute(element, "IsContentElement", "  ")
        printValidWebElementAttribute(element, "IsControlElement", "  ")
        printValidWebElementAttribute(element, "IsExpandCollapsePatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsGridItemPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsGridPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsInvokePatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsKeyboardFocusable", "  ")
        printValidWebElementAttribute(element, "IsMultipleViewPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsRangeValuePatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsScrollItemPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsScrollPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsSelectionItemPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsSelectionPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsTableItemPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsTablePatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsTextPatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsTogglePatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsValuePatternAvailable", "  ")
        printValidWebElementAttribute(element, "IsWindowPatternAvailable", "  ")

        println("[ WINDOW ]")
        printValidWebElementAttribute(element, "CanMaximize", "  ")
        printValidWebElementAttribute(element, "CanMinimize", "  ")
        printValidWebElementAttribute(element, "CanMove", "  ")
        printValidWebElementAttribute(element, "CanResize", "  ")
        printValidWebElementAttribute(element, "IsModal", "  ")

        println("")
    }

    private fun showWebElementAttribute(element: WebElement, attr: String) =
        StringUtils.rightPad(attr, 35) + ": " + try {
            StringUtils.defaultString(element.getAttribute(attr), "NONE")
        } catch (e: WebDriverException) {
            "N/A"
        }

    private fun showWebElementProperty(label: String, element: WebElement, prop: (WebElement) -> String) =
        StringUtils.rightPad(label, 35) + ": " + try {
            prop(element)
        } catch (e: WebDriverException) {
            "N/A"
        }

    private fun printValidWebElementAttribute(element: WebElement, attr: String, prefix: String) {
        try {
            val attrValue = element.getAttribute(attr)
            if (StringUtils.isNotEmpty(attrValue)) println("$prefix${StringUtils.rightPad(attr, 35) + ": "}$attrValue")
        } catch (e: WebDriverException) {
        }
    }

}