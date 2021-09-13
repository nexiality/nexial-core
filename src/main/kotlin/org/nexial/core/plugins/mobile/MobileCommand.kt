/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.mobile

import io.appium.java_client.AppiumDriver
import io.appium.java_client.MobileCommand.PRESS_KEY_CODE
import io.appium.java_client.MobileElement
import io.appium.java_client.MultiTouchAction
import io.appium.java_client.TouchAction
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.AndroidTouchAction
import io.appium.java_client.android.nativekey.AndroidKey.*
import io.appium.java_client.appmanagement.ApplicationState.NOT_INSTALLED
import io.appium.java_client.appmanagement.ApplicationState.RUNNING_IN_FOREGROUND
import io.appium.java_client.ios.IOSDriver
import io.appium.java_client.ios.IOSTouchAction
import io.appium.java_client.remote.HideKeyboardStrategy.TAP_OUTSIDE
import io.appium.java_client.touch.LongPressOptions
import io.appium.java_client.touch.TapOptions
import io.appium.java_client.touch.WaitOptions
import io.appium.java_client.touch.offset.ElementOption
import io.appium.java_client.touch.offset.PointOption
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.proc.ProcessInvoker.WORKING_DIRECTORY
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.WIN32_CMD
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.NexialConst.Mobile.Android.SCRIPT_COPY_TO_ANDROID
import org.nexial.core.NexialConst.Mobile.Message.*
import org.nexial.core.NexialConst.Mobile.iOS.*
import org.nexial.core.NexialConst.Project.NEXIAL_HOME
import org.nexial.core.NexialConst.Project.NEXIAL_MOBILE_BIN_REL_PATH
import org.nexial.core.NexialConst.Web.GROUP_LOCATOR_SUFFIX
import org.nexial.core.ShutdownAdvisor
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.model.TestStep
import org.nexial.core.plugins.CanTakeScreenshot
import org.nexial.core.plugins.ForcefulTerminate
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.plugins.base.NumberCommand
import org.nexial.core.plugins.base.ScreenshotUtils
import org.nexial.core.plugins.mobile.Direction.*
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.clearAllNotificationsLocators
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.doneLocator
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.iosAlertLocator
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.pickerWheelLocator
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.prefixIndex
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.regexOneOfLocator
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.scriptPressButton
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.scrollableLocator
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.scrollableLocator2
import org.nexial.core.plugins.mobile.MobileLocatorHelper.Companion.toLocatorString
import org.nexial.core.plugins.mobile.MobileType.ANDROID
import org.nexial.core.plugins.mobile.MobileType.IOS
import org.nexial.core.plugins.web.WebDriverExceptionHelper.resolveErrorMessage
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.OutputFileUtils
import org.openqa.selenium.*
import org.openqa.selenium.Keys.BACK_SPACE
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.ScreenOrientation.LANDSCAPE
import org.openqa.selenium.ScreenOrientation.PORTRAIT
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.FluentWait
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.time.Duration
import java.util.*
import java.util.function.Function
import kotlin.math.absoluteValue
import kotlin.math.max

// https://github.com/appium/appium-base-driver/blob/master/lib/protocol/routes.js#L345
class MobileCommand : BaseCommand(), CanTakeScreenshot, ForcefulTerminate {
    private lateinit var numberCommand: NumberCommand
    private var mobileService: MobileService? = null
    private val forcedWaitMs = 1000L

    override fun init(context: ExecutionContext) {
        super.init(context)
        numberCommand = context.findPlugin("number") as NumberCommand
        ShutdownAdvisor.addAdvisor(this)
    }

    override fun getTarget() = COMMAND

    override fun takeScreenshot(testStep: TestStep): String? {
        if (!isScreenshotEnabled) return null

        val mobileService = getMobileService()

        var filename = generateScreenshotFilename(testStep)
        if (StringUtils.isBlank(filename)) {
            error("Unable to generate screen capture filename!")
            return null
        }

        filename = context.project.screenCaptureDir + separator + filename
        return postScreenshot(testStep, ScreenshotUtils.saveScreenshot(mobileService.driver, filename))
    }

    override fun generateScreenshotFilename(testStep: TestStep): String =
        OutputFileUtils.generateScreenCaptureFilename(testStep)

    override fun mustForcefullyTerminate() = mobileService != null || MapUtils.isNotEmpty(context.mobileServices)

    override fun forcefulTerminate() {
        context.mobileServices.forEach { (_, service) ->
            if (service == mobileService) mobileService = null
            service.shutdown()
        }

        context.mobileServices.clear()

        if (mobileService != null) {
            mobileService!!.shutdown()
            mobileService = null
        }
    }

    fun use(profile: String): StepResult {
        requiresNotBlank(profile, "Invalid profile", profile)

        mobileService = context.getMobileService(profile)

        val appId = mobileService!!.profile.appId
        if (StringUtils.isNotBlank(appId)) {
            val driver = mobileService!!.driver
            val appState = driver.queryAppState(appId)
            if (appState == NOT_INSTALLED) throw IllegalArgumentException("$APP_NOT_INSTALLED $appId")
            if (appState != RUNNING_IN_FOREGROUND) launchApp(appId)
        }

        return StepResult.success()
    }

    fun assertElementPresent(locator: String): StepResult =
        if (isElementPresent(locator))
            StepResult.success("EXPECTED element '$locator' found")
        else
            StepResult.fail("Expected element not found at '$locator'")

    fun assertElementNotPresent(locator: String): StepResult =
        if (isElementPresent(locator))
            StepResult.fail("Element matching to '$locator' is found, which is NOT as expected")
        else
            StepResult.success("No element matching to '$locator' can be found, as EXPECTED")

    fun assertElementEnabled(locator: String): StepResult {
        return try {
            val (element, _) = findElement(locator)
            if (Objects.isNull(element)) StepResult.fail("No element found via locator '${locator}'")

            if (BooleanUtils.toBoolean(element.getAttribute("enabled")))
                StepResult.success("Element matching '${locator}' is enabled as EXPECTED")
            else
                StepResult.fail("Element matching '${locator}' is NOT enabled")
        } catch (e: WebDriverException) {
            StepResult.fail("Unable to find an element via '${locator}': ${e.localizedMessage}")
        }
    }

    fun assertElementDisabled(locator: String): StepResult {
        return try {
            val (element, _) = findElement(locator)
            if (Objects.isNull(element)) StepResult.fail("No element found via locator '${locator}'")

            if (BooleanUtils.toBoolean(element.getAttribute("enabled")))
                StepResult.fail("Element matching '${locator}' is NOT disabled")
            else
                StepResult.success("Element matching '${locator}' is disabled as EXPECTED")
        } catch (e: WebDriverException) {
            StepResult.fail("Unable to find an element via '${locator}': ${e.localizedMessage}")
        }
    }

    fun assertElementsPresent(prefix: String): StepResult {
        requiresNotBlank(prefix, "Invalid prefix", prefix)

        val locators = context.getDataByPrefix(prefix)
        if (MapUtils.isEmpty(locators)) return StepResult.fail("No data variables with prefix '$prefix'")

        val runId = context.runId
        var allPassed = true
        val logs = StringBuilder()
        val errors = StringBuilder()
        var locatorsFound = 0

        locators.filterKeys { it.endsWith(GROUP_LOCATOR_SUFFIX) }.forEach { (key, locator) ->
            locatorsFound++
            var message = "[${StringUtils.substringBefore(key, GROUP_LOCATOR_SUFFIX)}] "

            val found = try {
                if (assertElementPresent(locator).isSuccess) {
                    message += "found via '$locator'"
                    ConsoleUtils.log(runId, message)
                    true
                } else
                    false
            } catch (e: WebDriverException) {
                message += "NOT FOUND via '" + locator + "': " + resolveErrorMessage(e)
                ConsoleUtils.error(runId, message)
                false
            } catch (e: Throwable) {
                message += "NOT FOUND via '" + locator + "': " + e.message
                ConsoleUtils.error(runId, message)
                false
            }

            logs.append(message).append(NL)
            if (!found) {
                allPassed = false
                errors.append(message).append(NL)
            }
        }

        if (locatorsFound < 1) return StepResult.fail(
            "No data variables of prefix '$prefix' contains the required '$GROUP_LOCATOR_SUFFIX' suffix")

        val message = logs.toString()
        val errorsFound = errors.toString()
        if (context.isVerbose) log(message) else if (!allPassed) log(errorsFound)
        return if (allPassed) StepResult.success(message) else StepResult.fail(errorsFound)
    }

    fun assertElementVisible(locator: String): StepResult =
        if (isElementVisible(locator))
            StepResult.success("EXPECTED element '$locator' is present and visible")
        else
            StepResult.fail("Expected element '$locator' is either not present or not visible")

    fun assertElementNotVisible(locator: String): StepResult =
        if (!isElementVisible(locator))
            StepResult.success("Element '$locator' is either not present or not visible, as EXPECTED")
        else
            StepResult.fail("element '$locator' is visible, which is NOT as expected")

    fun assertTextPresent(locator: String, text: String): StepResult =
        if (findElements(locator).any { TextUtils.polyMatch(it.text, text) })
            StepResult.success("Element of '${locator}' contains text matching '${text}'")
        else
            StepResult.fail("Element of '${locator}' DOES NOT contains text matching '${text}'")

    fun saveText(`var`: String, locator: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)

        val (element, _) = findElement(locator)
        val elementText = element.text
        if (StringUtils.isEmpty(elementText))
            context.removeData(`var`)
        else
            context.setData(`var`, elementText)

        return StepResult.success("The text of element '${locator}' saved to `${`var`}` data variable")
    }

    fun saveTextArray(`var`: String, locator: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)

        val textArray = collectTextList(locator)
        if (CollectionUtils.isNotEmpty(textArray)) {
            context.setData(`var`, textArray)
        } else {
            context.removeData(`var`)
        }

        return StepResult.success("stored content of '$locator' as '$`var`'")
    }

    fun saveAttributes(`var`: String, locator: String, attribute: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(attribute, "invalid attribute", attribute)

        val values = collectAttributeValues(locator, attribute)
        if (CollectionUtils.isNotEmpty(values)) {
            context.setData(`var`, values)
        } else {
            context.removeData(`var`)
        }

        return StepResult.success("The value of '$attribute' from '$locator' are saved to '$`var`'")
    }

    fun assertAttribute(locator: String, attribute: String, text: String): StepResult {
        requiresNotBlank(attribute, "invalid attribute", attribute)

        val values = collectAttributeValues(locator, attribute)
        return if (CollectionUtils.isNotEmpty(values)) {
            val actual = values[0]
            if (TextUtils.polyMatch(actual, text))
                StepResult.success("The attribute '$attribute' value '$actual' matches '$text' as EXPECTED")
            else
                StepResult.fail("The attribute '$attribute' value '$actual' DOES NOT match '$text'")
        } else
            if (StringUtils.isEmpty(text))
                StepResult.success("No attribute value found, and none was expected")
            else
                StepResult.fail("Either no element matches '$locator' or none contains the attribute '$attribute'")
    }

    fun waitForElementPresent(locator: String, waitMs: String): StepResult {
        val maxWaitMs = deriveMaxWaitMs(waitMs)
        val isPresent = waitForElementPresent(locator, maxWaitMs)
        return if (isPresent) {
            StepResult.success("Element by locator '$locator' is present")
        } else {
            StepResult.fail("Element by locator '$locator' is NOT present within ${maxWaitMs}ms")
        }
    }

    private fun waitForElementPresent(locator: String, waitMs: Long): Boolean {
        if (waitMs < 150) throw IllegalArgumentException("Invalid wait time $waitMs")
        requiresNotBlank(locator, "invalid locator", locator)
        return waitForCondition(waitMs) { isElementPresent(locator) }
    }

    /**
     * assert presence of an alert, optionally also asserting the text on the alert. PolyMatcher supported
     */
    fun assertAlertPresent(text: String): StepResult {
        val assertText = !context.isNullOrEmptyOrBlankValue(text)
        val msgSuffix = if (assertText) "with text $text" else ""
        return if (isAlertPresent(text))
            StepResult.success("EXPECTED alert dialog $msgSuffix found on current window")
        else
            StepResult.fail("EXPECTED alert dialog $msgSuffix NOT found")
    }

    internal fun isAlertPresent(text: String): Boolean {
        val assertText = !context.isNullOrEmptyOrBlankValue(text)
        val mobileService = getMobileService()
        return when (mobileService.profile.mobileType) {
            ANDROID -> throw IllegalArgumentException("This command is not supported on Android device")
            IOS     -> {
                isElementPresent(
                    "$iosAlertLocator//*[@type='XCUIElementTypeStaticText'" +
                    if (assertText) " and ${MobileLocatorHelper.resolveFilter("value", text)}]" else "]"
                )
            }
        }
    }

    fun clearAlert(option: String): StepResult {
        requiresNotBlank(option, "invalid option/button name", option)
        if (!isAlertPresent("")) return StepResult.fail("No alert dialog found")

        val mobileService = getMobileService()
        return when (mobileService.profile.mobileType) {
            ANDROID -> StepResult.fail("This command is not supported on Android device")
            IOS     -> {
                val alertOption = "$iosAlertLocator//*[@type='XCUIElementTypeButton' and " +
                                  "${MobileLocatorHelper.resolveFilter("label", option)}]"
                if (isElementPresent(alertOption)) {
                    val (element, _) = findElement(alertOption)
                    element.click()
                    StepResult.success("Alert dialog dismissed via '$option'")
                } else
                    StepResult.fail("No alert dialog with option '$option' found")
            }
        }
    }

    fun saveAlertText(`var`: String): StepResult {
        requiresValidVariableName(`var`)
        if (!isAlertPresent("")) {
            context.removeData(`var`)
            return StepResult.success("No alert dialog found, no data will be saved to $`var`")
        }

        val mobileService = getMobileService()
        return when (mobileService.profile.mobileType) {
            ANDROID -> StepResult.fail("This command is not supported on Android device")
            IOS     -> {
                val alertMessages = findElements("$iosAlertLocator//*[@type='XCUIElementTypeStaticText']")
                if (CollectionUtils.isEmpty(alertMessages)) StepResult.fail("No alert dialog found")

                context.setData(`var`, alertMessages.joinToString(separator = "\n") { it.getAttribute("value") })
                StepResult.success("Text from current alert dialog saved to $`var`")
            }
        }
    }

    fun clearNotification(): StepResult {
        val mobileService = getMobileService()
        return when (mobileService.profile.mobileType) {
            ANDROID -> {
                val driver = mobileService.driver
                (driver as AndroidDriver).openNotifications()

                // if there aren't any notification, then the `clear all` buttons are likely to exist
                try {
                    val (clearAll, _) = findElement(clearAllNotificationsLocators)
                    clearAll.click()
                } catch (e: NoSuchElementException) {
                    ConsoleUtils.log("The 'clear all' button does not exists; likely there's no notification to " +
                                     "clear at this time")
                }

                try {
                    driver.runAppInBackground(Duration.ofSeconds(1))
                } catch (e: Exception) {
                }

                StepResult.success("All notifications cleared")
            }
            // probably doesn't work...
            IOS     -> StepResult.fail("This command is not supported on iOS device")
        }
    }

    fun click(locator: String): StepResult {
        tap(locator)
        return StepResult.success("Clicked on '$locator'")
    }

    private fun tap(locator: String, postTaWaitMs: String) =
        tap(findElement(locator).first, resolveWaitMs(postTaWaitMs))

    private fun tap(locator: String) = tap(findElement(locator).first)

    private fun tap(target: MobileElement) = tap(target, getMobileService().profile.postActionWaitMs)

    private fun tap(target: MobileElement, postTabWaitMs: Long) {
        val mobileService = getMobileService()
        toTouchAction(mobileService)
            .tap(TapOptions.tapOptions().withElement(ElementOption.element(target)))
            .perform()
        if (postTabWaitMs > MIN_WAIT_MS) waitFor(postTabWaitMs.toInt())
    }

    private fun longTap(target: MobileElement, holdTime: Long) {
        val mobileService = getMobileService()
        toTouchAction(mobileService)
            .longPress(LongPressOptions.longPressOptions()
                           .withElement(ElementOption.element(target))
                           .withPosition(PointOption.point(EDGE_WIDTH, EDGE_WIDTH))
                           .withDuration(Duration.ofMillis(holdTime)))
            .perform()
        val postTabWaitMs = mobileService.profile.postActionWaitMs
        if (postTabWaitMs > MIN_WAIT_MS) waitFor(postTabWaitMs.toInt())
    }

    private fun toTouchAction(mobileService: MobileService) =
        when (mobileService.profile.mobileType) {
            ANDROID -> AndroidTouchAction(mobileService.driver)
            IOS     -> IOSTouchAction(mobileService.driver)
        }

    /** partially supports polymatcher */
    fun clickByDisplayText(text: String): StepResult {
        requiresNotBlank(text, "invalid display text", text)
        val mobileService = getMobileService()
        val xpath = StringBuilder(buildString {
            append("//*[").append(if (mobileService.isAndroid()) "@displayed" else "@visible").append("='true' and ")
            append(MobileLocatorHelper.resolveLinkTextFilter(mobileService.profile.mobileType, text))
            append("]")
        })
        return click(xpath.toString())
    }

    fun clickUntilNotFound(locator: String, waitMs: String, max: String): StepResult {
        val waitMillis = resolveWaitMs(waitMs)

        if (StringUtils.isNotEmpty(max)) requiresPositiveNumber(max, "Invalid max try number", max)
        val maxTries = if (StringUtils.isEmpty(max)) -1 else NumberUtils.toInt(max)

        var attempt = 0

        var element = findFirstMatch(locator)
        while (element != null) {
            // withPostActionWait(Actions(mobileService.driver).click(element), waitMillis)
            tap(element, waitMillis)
            element = findFirstMatch(locator)
            attempt++

            if (element == null) break

            if (maxTries != -1 && attempt >= maxTries) {
                ConsoleUtils.log("Click attempt has reached the specified maximum value; however at least " +
                                 "one more element matching '$locator' is found")
            }
        }

        return StepResult.success("$attempt click(s) performed on '$locator'")
    }

    fun longClick(locator: String, waitMs: String): StepResult {
        requiresPositiveNumber(waitMs, "invalid hold time", waitMs)

        val holdTime = max(NumberUtils.toLong(waitMs), 1500L)
        val (element, _) = findElement(locator)
        longTap(element, holdTime)

        // val mobileService = getMobileService()
        // withPostActionWait(Actions(mobileService.driver)
        //                        .moveToElement(element, EDGE_WIDTH, EDGE_WIDTH)
        //                        .clickAndHold()
        //                        .pause(holdTime)
        //                        .release(),
        //                    mobileService)

        return StepResult.success("successfully long-click on '$locator'")
    }

    fun doubleClick(locator: String, xOffset: String, yOffset: String): StepResult {
        requiresInteger(xOffset, "invalid x-offset", xOffset)
        requiresInteger(yOffset, "invalid y-offset", yOffset)

        val (element, _) = findElement(locator)
        val mobileService = getMobileService()
        withPostActionWait(
            Actions(mobileService.driver)
                .moveToElement(element, NumberUtils.toInt(xOffset), NumberUtils.toInt(yOffset))
                .doubleClick(),
            mobileService)
        return StepResult.success("Double-clicked on '$locator'")
    }

    fun type(locator: String, text: String): StepResult {
        val mobileService = getMobileService()

        // too fast?
        // Actions(mobileService.driver).sendKeys(element, text).pause(mobileService.profile.postActionWaitMs).perform()

        return if (StringUtils.isEmpty(text)) {
            val (_, findBy) = findElement(locator)
            clearText(findBy)
            val currentText = findElement(findBy).text
            if (StringUtils.isEmpty(currentText))
                StepResult.success("Element '$locator' cleared")
            else
                StepResult.fail("Element '$locator' not cleared; current text is '$currentText'")
        } else {
            // val findBy = resolveFindBy(locator)
            val (element, findBy) = findElement(locator)
            val elementText = element.text
            if (StringUtils.equals(elementText, text))
                StepResult.success("Element '$locator' already contain text '$text")
            else {
                clearText(findBy)
                findElement(findBy).sendKeys(text)
                val postWaitMs = mobileService.profile.postActionWaitMs
                if (postWaitMs > MIN_WAIT_MS) waitFor(postWaitMs.toInt())

                // close keyboard if possible
                if (mobileService.isIOS() && isElementPresent("name=Done")) click("name=Done")

                StepResult.success("Text '$text' typed on '$locator'")
            }
        }
    }

    /**
     * Performs swipe from the center of screen
     */
    fun slide(start: String, end: String): StepResult {
        requiresNotBlank(start, "Invalid starting position", start)
        requiresNotBlank(end, "Invalid ending position", end)

        // Animation default time in ms:
        //  - Android: 300 ms
        //  - iOS: 200 ms
        // final value depends on your app and could be greater
        val animationTime = 200L
        val pressTime = 200L

        val mobileService = getMobileService()
        val driver = mobileService.driver
        val currentScreen = driver.manage().window().size

        val startFrom = toPointOption(start, currentScreen)
        val endAt = toPointOption(end, currentScreen)

        // execute swipe using TouchAction
        return try {
            newTouchAction(mobileService)
                .press(startFrom).waitAction(waitMs(pressTime))
                .moveTo(endAt).waitAction(waitMs(animationTime))
                .release()
                .perform()
            StepResult.success("Successfully swiped from $start to $end")
        } catch (e: Exception) {
            val error = "Unable to swipe from $start to $end: ${e.message}"
            ConsoleUtils.error(error)
            StepResult.fail(error, e)
        }
    }

    /**
     * scroll until `searchFor` is found. `searchFor` would be a locator.
     * The `locator` should represent the scrollable container by which the target text can be found
     * `direction` supports UP, DOWN, LEFT, RIGHT
     */
    fun scrollUntilFound(scrollTarget: String, direction: String, searchFor: String, maxWaitMs: String): StepResult {
        requiresNotBlank(searchFor, "Invalid text", searchFor)

        if (StringUtils.isNotBlank(maxWaitMs)) requiresInteger(maxWaitMs, "Invalid maxWaitMs", maxWaitMs)

        requiresNotBlank(direction, "Invalid direction", direction)
        val dir = Direction.values().find { it.detail.equals(direction, true) }
                  ?: throw IllegalArgumentException("Invalid direction $direction")

        // 1. make sure the specified scroll container exists
        val timeoutBy =
            if (StringUtils.isBlank(maxWaitMs) || maxWaitMs.trim() == "-1") -1
            else System.currentTimeMillis() + NumberUtils.toLong(maxWaitMs)
        val (scrollArea, _) = findElement(scrollTarget)
        val scrollAreaHeight = scrollArea.rect.height
        val scrollAreaWidth = scrollArea.rect.width
        val afterHoldWaitMs = 10L
        val afterMoveWaitMs = 10L
        val afterReleaseWaitMs = 250L

        // 2. figure out the offsets to use based on `direction`
        val startX = scrollAreaWidth / 2 * -1 + 10
        val startY = scrollAreaHeight / 2 * -1 + 10
        val factor = 0.5
        val yOffset = (scrollAreaHeight * factor).toInt()
        val xOffset = (scrollAreaWidth * factor).toInt()
        val offsets = when (dir) {
            DOWN  -> Pair(Point(startX, 1), Point(0, yOffset))
            UP    -> Pair(Point(startX, 1), Point(0, yOffset * -1))
            LEFT  -> Pair(Point(1, startY), Point(xOffset * -1, 0))
            RIGHT -> Pair(Point(1, startY), Point(xOffset, 0))
            else  -> throw IllegalArgumentException("Invalid direction; Percentage on direction not supported - $dir")
        }

        // 3. getting ready to scroll and search
        val driver = getMobileService().driver
        val waiter = newWaiter(scrollArea, forcedWaitMs, "scrolling ${dir.name} to find $searchFor")
        val findBys = resolveFindBy(searchFor)
        var matches = waiter.until { findElements(it, findBys) }
        while (matches.isEmpty()) {
            if (timeoutBy != (-1).toLong() && System.currentTimeMillis() >= timeoutBy)
                return StepResult.fail("time out while scrolling ${dir.detail} to find $searchFor")

            Actions(driver)
                .moveToElement(scrollArea, offsets.first.x, offsets.first.y)
                .clickAndHold().pause(afterHoldWaitMs)
                .moveByOffset(offsets.second.x, offsets.second.y).pause(afterMoveWaitMs)
                .release().pause(afterReleaseWaitMs)
                .perform()
            matches = waiter.until { findElements(scrollArea, findBys) }
        }

        // 4. all done
        return if (matches.isNotEmpty())
            StepResult.success("Element '${searchFor}' found after some scrolling on '${scrollTarget}")
        else
            StepResult.fail("Element '${searchFor}' cannot be found after scrolling on '${scrollTarget}")
    }

    fun scroll(locator: String, direction: String): StepResult {
        requiresNotBlank(direction, "Invalid direction", direction)
        val dir = Direction.values().find { it.detail.equals(direction, true) }
                  ?: throw IllegalArgumentException("Invalid direction $direction")

        val (element, _) = findElement(locator)
        val driver = getMobileService().driver
        val screenDimension = driver.manage().window().size

        val offsets = when (dir) {
            DOWN_10P  -> Pair(0, (screenDimension.height * 0.1).toInt())
            DOWN_20P  -> Pair(0, (screenDimension.height * 0.2).toInt())
            DOWN_30P  -> Pair(0, (screenDimension.height * 0.3).toInt())
            DOWN_40P  -> Pair(0, (screenDimension.height * 0.4).toInt())
            DOWN_50P  -> Pair(0, (screenDimension.height * 0.5).toInt())
            DOWN_60P  -> Pair(0, (screenDimension.height * 0.6).toInt())
            DOWN_70P  -> Pair(0, (screenDimension.height * 0.7).toInt())
            DOWN_80P  -> Pair(0, (screenDimension.height * 0.8).toInt())
            DOWN_90P  -> Pair(0, (screenDimension.height * 0.9).toInt())
            DOWN      -> Pair(0, screenDimension.height)

            UP_10P    -> Pair(0, (screenDimension.height * 0.1 * -1).toInt())
            UP_20P    -> Pair(0, (screenDimension.height * 0.2 * -1).toInt())
            UP_30P    -> Pair(0, (screenDimension.height * 0.3 * -1).toInt())
            UP_40P    -> Pair(0, (screenDimension.height * 0.4 * -1).toInt())
            UP_50P    -> Pair(0, (screenDimension.height * 0.5 * -1).toInt())
            UP_60P    -> Pair(0, (screenDimension.height * 0.6 * -1).toInt())
            UP_70P    -> Pair(0, (screenDimension.height * 0.7 * -1).toInt())
            UP_80P    -> Pair(0, (screenDimension.height * 0.8 * -1).toInt())
            UP_90P    -> Pair(0, (screenDimension.height * 0.9 * -1).toInt())
            UP        -> Pair(0, screenDimension.height * -1)

            LEFT_10P  -> Pair((screenDimension.width * 0.1 * -1).toInt(), 0)
            LEFT_20P  -> Pair((screenDimension.width * 0.2 * -1).toInt(), 0)
            LEFT_30P  -> Pair((screenDimension.width * 0.3 * -1).toInt(), 0)
            LEFT_40P  -> Pair((screenDimension.width * 0.4 * -1).toInt(), 0)
            LEFT_50P  -> Pair((screenDimension.width * 0.5 * -1).toInt(), 0)
            LEFT_60P  -> Pair((screenDimension.width * 0.6 * -1).toInt(), 0)
            LEFT_70P  -> Pair((screenDimension.width * 0.7 * -1).toInt(), 0)
            LEFT_80P  -> Pair((screenDimension.width * 0.8 * -1).toInt(), 0)
            LEFT_90P  -> Pair((screenDimension.width * 0.9 * -1).toInt(), 0)
            LEFT      -> Pair(screenDimension.width * -1, 0)

            RIGHT_10P -> Pair((screenDimension.width * 0.1).toInt(), 0)
            RIGHT_20P -> Pair((screenDimension.width * 0.2).toInt(), 0)
            RIGHT_30P -> Pair((screenDimension.width * 0.3).toInt(), 0)
            RIGHT_40P -> Pair((screenDimension.width * 0.4).toInt(), 0)
            RIGHT_50P -> Pair((screenDimension.width * 0.5).toInt(), 0)
            RIGHT_60P -> Pair((screenDimension.width * 0.6).toInt(), 0)
            RIGHT_70P -> Pair((screenDimension.width * 0.7).toInt(), 0)
            RIGHT_80P -> Pair((screenDimension.width * 0.8).toInt(), 0)
            RIGHT_90P -> Pair((screenDimension.width * 0.9).toInt(), 0)
            RIGHT     -> Pair(screenDimension.width, 0)
        }

        withPostActionWait(Actions(driver)
                               .moveToElement(element)
                               .clickAndHold()
                               .moveByOffset(offsets.first, offsets.second)
                               .release()
                               .pause(500L),
                           getMobileService())
        return StepResult.success("Scroll $direction on '$locator'")
    }

    fun shake(): StepResult =
        when (val driver = getMobileService().driver) {
            is AndroidDriver -> StepResult.fail("shake() command not yet implemented for Android device")
            is IOSDriver     -> {
                driver.shake()
                StepResult.success()
            }
            else             -> executeCommand("shake")
        }

    fun lock(): StepResult =
        when (val driver = getMobileService().driver) {
            is AndroidDriver -> {
                driver.lockDevice()
                StepResult.success()
            }
            is IOSDriver     -> {
                driver.lockDevice()
                StepResult.success()
            }
            else             -> executeCommand("lock")
        }

    fun unlock(): StepResult =
        when (val driver = getMobileService().driver) {
            is AndroidDriver -> {
                driver.unlockDevice()
                StepResult.success()
            }
            is IOSDriver     -> {
                driver.unlockDevice()
                StepResult.success()
            }
            else             -> executeCommand("unlock")
        }

    fun assertLocked(): StepResult {
        return if (isDeviceLocked())
            StepResult.success("Device is currently locked, as EXPECTED")
        else
            StepResult.fail("Device is currently NOT locked")
    }

    fun saveLockStatus(`var`: String): StepResult {
        requiresValidVariableName(`var`)
        val status = if (isDeviceLocked()) "LOCKED" else "UNLOCKED"
        context.setData(`var`, status)
        return StepResult.success("Device lock status saved to `var`")
    }

    fun sendSms(phone: String, message: String): StepResult {
        requiresNotBlank(phone, "Invalid phone number", phone)
        requiresNotBlank(message, "Invalid message", message)
        return executeCommand("sendSMS",
                              mutableMapOf("phoneNumber" to TextUtils.sanitizePhoneNumber(phone), "message" to message))
    }

    /**
     * support both zoom in and pinching
     */
    fun zoom(start1: String, end1: String, start2: String, end2: String): StepResult {
        requiresNotBlank(start1, "Invalid value for the first pinch-start position", start1)
        requiresNotBlank(end1, "Invalid value for the first pinch-end position", end1)
        requiresNotBlank(start2, "Invalid value for the second pinch-start position", start2)
        requiresNotBlank(end2, "Invalid value for the first pinch-end position", end2)

        val mobileService = getMobileService()
        val driver = mobileService.driver
        val currentScreen = driver.manage().window().size

        val start1From = toPointOption(start1, currentScreen)
        val start2From = toPointOption(start2, currentScreen)
        val end1At = toPointOption(end1, currentScreen)
        val end2At = toPointOption(end2, currentScreen)

        val wait = waitMs(forcedWaitMs)
        MultiTouchAction(driver)
            .add(newTouchAction(mobileService)
                     .press(start1From).waitAction(wait).moveTo(end1At).waitAction(wait).release())
            .add(newTouchAction(mobileService)
                     .press(start2From).waitAction(wait).moveTo(end2At).waitAction(wait).release())
            .perform()
        return StepResult.success("Successfully pinch from ($start1) and ($start2) to ($end1) and ($end2)")
    }

    fun orientation(mode: String): StepResult {
        val orient = StringUtils.lowerCase(mode)
        requiresOneOf(orient, "landscape", "portrait")

        val mobileService = getMobileService()
        val driver = mobileService.driver
        val currentOrientation = driver.orientation

        return when (orient) {
            "landscape" ->
                if (currentOrientation == LANDSCAPE)
                    StepResult.success("Current screen is already in LANDSCAPE mode")
                else {
                    driver.rotate(LANDSCAPE)
                    StepResult.success("Successfully rotate screen $mode")
                }
            "portrait"  ->
                if (currentOrientation == PORTRAIT)
                    StepResult.success("Current screen is already in PORTRAIT mode")
                else {
                    driver.rotate(PORTRAIT)
                    StepResult.success("Successfully rotate screen $mode")
                }
            else        -> StepResult.fail("Unknown orientation: $mode")
        }
    }

    fun screenshot(file: String, locator: String): StepResult {
        if (!isScreenshotEnabled) return StepResult.skipped("screen capturing disabled")
        requiresNotBlank(file, "invalid file", file)
        val screenshot = ScreenshotUtils.saveScreenshot(findElement(locator).first, file)
        return postScreenshot(screenshot, locator)
    }

    fun assertCount(locator: String, count: String): StepResult {
        requiresPositiveNumber(count, "invalid count", count)
        val actual = CollectionUtils.size(findElements(locator))
        return if (numberCommand.assertEqual(count, actual.toString()).isSuccess) {
            StepResult.success("EXPECTED element count ($actual) found")
        } else {
            StepResult.fail("element count ($actual) DID NOT match expected count ($count)")
        }
    }

    fun saveCount(`var`: String, locator: String): StepResult {
        requiresValidVariableName(`var`)
        return try {
            val matches = findElements(locator)
            val count = CollectionUtils.size(matches)
            context.setData(`var`, count)
            StepResult.success("$count match(es) of '$locator' found and its count is stored to data variable 'var'")
        } catch (e: Exception) {
            // any exception is not good... make sure `var` is not created with some random/irrelevant value
            context.removeData(`var`)
            StepResult.fail("Error occurred while resolving element count on '$locator': ${e.message}")
        }
    }

    fun hideKeyboard(): StepResult {
        return if (hideKeyboard(false))
            StepResult.success("execute hide-keyboard successfully")
        else
            StepResult.success("on-screen keyboard already hidden")
    }

    fun home(): StepResult = keyPress(if (getMobileService().profile.mobileType.isAndroid()) HOME.code else MENU.code)

    fun back() = keyPress(BACK.code)

    fun forward() = executeCommand("forward")

    fun recentApps() = when (getMobileService().profile.mobileType) {
        ANDROID -> keyPress(APP_SWITCH.code)
        IOS     -> {
            val pressHome = mapOf("name" to "home")
            val driver = getMobileService().driver
            driver.executeScript(scriptPressButton, pressHome)
            driver.executeScript(scriptPressButton, pressHome)
            StepResult.success()
        }
    }

    fun launchApp(app: String): StepResult {
        requiresNotBlank(app, "invalid app/bundle id", app)
        val mobileService = getMobileService()
        val driver = mobileService.driver

        // in case app's destroy or terminated...
        try {
            driver.sessionId
        } catch (e: NoSuchSessionException) {
            // re-launch
            driver.launchApp()
        }

        driver.activateApp(app)
        return StepResult.success("activated app '$app")
    }

    fun select(locator: String, item: String): StepResult {
        requiresNotBlank(item, "invalid item specified", item)

        val mobileService = getMobileService()
        val driver = mobileService.driver
        val profileName = mobileService.profile.profile

        return when (mobileService.profile.mobileType) {
            ANDROID -> {
                // 1. click on the dropdown to display the options
                val (select, _) = findElement(locator)
                val currentSelectValue = select.text
                if (StringUtils.equals(currentSelectValue, item))
                    StepResult.success("item $item already selected")
                else {
                    tap(select)
                    // withPostActionWait(Actions(mobileService.driver).click(select), mobileService)
                    waitFor(forcedWaitMs.toInt())

                    // 2. find the most likely scrollable container
                    val scrollTarget = findAndroidScrollTarget(driver, select)
                    if (scrollTarget == null)
                        StepResult.fail("Unable to locate a usable dropdown")
                    else {
                        // 3. determine how many lines to scroll (if needed)
                        val maxScrolls = context.getIntConfig(target, profileName, DROPDOWN_MAX_SCROLL)
                        val scrollAmount = scrollTarget.rect.height / 2
                        val startFrom = Point(0, scrollAmount - 5)
                        val scrollTo = Point(0, (scrollAmount / 2) * -1)

                        // 4. scroll downwards until specified item is found
                        val waiter = newWaiter(forcedWaitMs, "identifying option '${item}'")
                        var scrollCount = 0
                        val findByOption = By.xpath(MobileLocatorHelper.resolveAndroidDropdownItemLocator(item))
                        var itemElement = waiter.until { scrollTarget.findElements(findByOption) }
                        while (CollectionUtils.isEmpty(itemElement)) {
                            if (scrollCount++ >= maxScrolls) break
                            Actions(driver)
                                .moveToElement(scrollTarget, startFrom.x, startFrom.y)
                                .clickAndHold()
                                .moveByOffset(scrollTo.x, scrollTo.y)
                                .release()
                                .pause(500L)
                                .perform()
                            itemElement = waiter.until { scrollTarget.findElements(findByOption) }
                        }

                        waitFor(forcedWaitMs.toInt())

                        if (CollectionUtils.isNotEmpty(itemElement)) {
                            // itemElement[0].click()
                            tap(itemElement[0], forcedWaitMs)
                            // test that the intended item is selected
                            val retry = if (StringUtils.startsWith(item, prefixIndex))
                                StringUtils.equals(select.text, currentSelectValue)
                            else
                                !StringUtils.equals(select.text, item)

                            // 1 last retry...
                            if (retry) tap(itemElement[0], forcedWaitMs)

                            ConsoleUtils.log(
                                "After selecting '$item', the text of the dropdown is now '${select.text}'")
                            StepResult.success("dropdown option '${item}' selected")
                        } else
                            StepResult.fail("Unable to find dropdown option '${item}'")
                    }
                }
            }
            IOS     -> {
                // 1. find the select element / check if intended value is already selected
                val (select, _) = findElement(locator)
                val currentSelection = select.getAttribute("value")
                if (StringUtils.equals(currentSelection, item))
                    StepResult.success("Current selection matched to $item")
                else {
                    // 2. click on the select element (allow time for dropdown options to display)
                    // withPostActionWait(Actions(mobileService.driver).click(select), mobileService)
                    // waitFor(800)
                    tap(select)

                    // 3. 1 or more pickers?
                    val pickers = findElements(pickerWheelLocator)
                    if (pickers.size > 1 && StringUtils.contains(item, MULTI_PICKER_DELIM)) {
                        val parts = StringUtils.split(item, MULTI_PICKER_DELIM)
                        // if the picker wheels do not match the text splits
                        if (parts == null || parts.size != pickers.size)
                            StepResult.fail("Unable to automate '$item' against ${pickers.size} pick lists")
                        else {
                            // 4. designated value to which picker
                            parts.forEachIndexed { index, part ->
                                if (StringUtils.isNotEmpty(part)) pickers[index].sendKeys(part)
                            }

                            // 5. close keyboard if possible
                            if (isElementPresent(doneLocator)) click(doneLocator)
                            StepResult.success("Selected '$item' on ${pickers.size} pick lists")
                        }
                    } else {
                        // 4. check if corresponding Picker Wheel already has the intended value
                        MobileLocatorHelper.selectIOSDropdown(driver, pickers[0], item)

                        // 5. close keyboard if possible
                        if (isElementPresent(doneLocator)) click(doneLocator)

                        StepResult.success("Selected item $item")
                    }
                }
            }
        }
    }

    fun selectLocalFile(device: String, folder: String, filename: String): StepResult {
        requiresNotBlank(device, "Invalid device name specified", device)
        requiresNotBlank(filename, "Invalid file name specified", filename)

        val dir = if (StringUtils.isBlank(folder)) "Documents" else folder

        val baseResId = "com.google.android.documentsui:id"
        val resIdTitle = "@resource-id='android:id/title'"

        val rootMenu = "//*[@resource-id='$baseResId/toolbar']/android.widget.ImageButton[@content-desc='Show roots']"
        val rootFolder = "//*[$resIdTitle and @text='${device}']"
        val targetFolder = "//*[$resIdTitle and @text='$dir']"
        val files = "//*[@resource-id='$baseResId/container_directory']" +
                    "//*[@resource-id='$baseResId/dir_list']" +
                    "/*[@resource-id='$baseResId/item_root']"
        val targetFile = "$files//*[$resIdTitle and @text='$filename']"
        val acceptButton = "id=menu_crop"

        // step 1: make sure we are on the right screen
        if (!waitForElementPresent(rootMenu, 5000)) return StepResult.fail("Fail to verify UI - root menu NOT FOUND")

        tap(rootMenu)

        // step 2. click on the device name to get to the root folder
        if (!waitForElementPresent(rootFolder, 5000)) return StepResult.fail("Fail to find a folder named '$device'")

        tap(rootFolder)

        // step 3. click on the specified folder
        if (!waitForElementPresent(targetFolder, 2000)) return StepResult.fail("Fail to find a folder name '$dir'")

        tap(targetFolder)

        // step 4. make sure we have at least 1 file in the target folder
        if (!waitForElementPresent(files, 2000))
            return StepResult.fail("Fail to find any files under the '$dir' folder")

        // step 5. click on target file to select
        if (!waitForElementPresent(targetFile, 2000))
            return StepResult.fail("Unable to find the specified file '$filename' under the '$dir' folder")

        tap(targetFile)

        // in case we end up in the crop picture UI...
        if (isElementVisible(acceptButton)) tap(acceptButton)

        return StepResult.success("selected '$filename' from '$folder' folder")
    }

    // todo: need to figure out permission issue
    // Original error: Error executing adbExec. Original error: 'Command '... ... \\platform-tools\\adb.exe -P 5037
    //  -s emulator-5554 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true' exited with code
    //  255'; Stderr: 'Exception occurred while executing 'broadcast'
    // fun toggleAirplaneMode() = executeCommand("toggleFlightMode")

    // fun rotate(start: String, end: String): StepResult

    fun closeApp(): StepResult {
        val mobileService = getMobileService()
        return when (val driver = mobileService.driver) {
            is AndroidDriver -> {
                driver.resetApp()
                driver.closeApp()
                StepResult.success()
            }
            is IOSDriver     -> {
                // no need to reset for XCUITestDriver
                // driver.resetApp()
                driver.closeApp()
                StepResult.success()
            }
            else             -> executeCommand("closeApp")
        }
    }

    fun shutdown(profile: String): StepResult {
        val mobileService = getMobileService()
        mobileService.shutdown()
        context.mobileServices.remove(profile)
        this.mobileService = null
        return StepResult.success()
    }

    fun copyToLocal(file: String, folder: String): StepResult {
        requiresReadableFile(file)
        requiresNotBlank(folder, "Invalid device folder", folder)

        val mobileType = getMobileService().profile.mobileType

        // check that env NEXIAL_HOME is set
        requiresNexialHome()

        val mobileScriptPath =
            StringUtils.appendIfMissing(File(System.getProperty(NEXIAL_HOME)).absolutePath, separator) +
            NEXIAL_MOBILE_BIN_REL_PATH
        val extProcEnv = mapOf(WORKING_DIRECTORY to mobileScriptPath)

        val script = mobileScriptPath + when (mobileType) {
            ANDROID -> "${SCRIPT_COPY_TO_ANDROID}${if (IS_OS_WINDOWS) "cmd" else "sh"}"
            IOS     -> SCRIPT_COPY_TO_IOS
        }
        if (!FileUtil.isFileExecutable(script)) throw RuntimeException("$script $SCRIPT_NOT_EXECUTABLE")

        val outcome = when (mobileType) {
            ANDROID -> {
                // adb push *file* /storage/emulated/0/Download/
                if (IS_OS_WINDOWS)
                    ProcessInvoker.invoke(WIN32_CMD, listOf("/C", script, file, folder.removePrefix("/")), extProcEnv)
                else
                    ProcessInvoker.invoke(script, listOf(file, folder.removePrefix("/")), extProcEnv)
            }
            IOS     -> {
                // check file extension (only .png, .jpg, .mp4, .gif, .wbem
                if (!SUPPORTED_COPY_FILE_EXT.contains(StringUtils.lowerCase(StringUtils.substringAfterLast(file, "."))))
                    throw IllegalArgumentException("$NOT_SUPPORT_FILE_EXT $file")
                ProcessInvoker.invoke(script, listOf(file), extProcEnv)
            }
        }

        return if (outcome.exitStatus != 0) {
            ConsoleUtils.log(outcome.stdout)
            ConsoleUtils.log(outcome.stderr)
            StepResult.fail("FAIL to copy '$file' to the mobile device: ${outcome.stdout}")
        } else
            StepResult.success("File '$file' copied to the mobile device")
    }

    internal fun findAndroidScrollTarget(driver: AppiumDriver<MobileElement>, closest: MobileElement): MobileElement? {
        // find all scrollable containers
        val elements = driver.findElements(By.xpath(scrollableLocator))
        val scrollables = if (CollectionUtils.isNotEmpty(elements))
            elements
        else
            driver.findElements(By.xpath(scrollableLocator2))

        // find the most likely scrollable container
        return if (CollectionUtils.isEmpty(scrollables))
            null
        else if (scrollables.size == 1)
            scrollables[0]
        else {
            // lastResort is usually the top level scrollable container (x or y < 10)
            val lastResort = scrollables.find { elem -> elem.rect.x < 10 || elem.rect.y < 10 }
            if (lastResort != null) scrollables.remove(lastResort)

            // find the closest one with the most similar width
            val matchWidth = closest.rect.width
            val matchY = closest.rect.y
            scrollables.sortBy { elem ->
                (elem.rect.y - matchY).absoluteValue * (elem.rect.width - matchWidth).absoluteValue / matchWidth
            }
            scrollables[0]
        }
    }

    internal fun getMobileService(): MobileService {
        if (mobileService == null) throw IllegalStateException(ERR_NO_SERVICE)
        return mobileService as MobileService
    }

    internal fun newWaiter(message: String) = newWaiter(getMobileService().profile.explicitWaitMs, message)

    internal fun newWaiter(waitMs: Long, message: String) = newWaiter(getMobileService().driver, waitMs, message)

    private fun <T> newWaiter(input: T, waitMs: Long, message: String): FluentWait<T> {
        return FluentWait(input)
            .withTimeout(Duration.ofMillis(waitMs))
            .pollingEvery(Duration.ofMillis(10))
            .ignoring(WebDriverException::class.java)
            .withMessage(message)
    }

    /**
     * transform either a single locator into a single-item list of By, or a compound locator (i.e. one-of=...) into
     * a list of By instances
     */
    internal fun resolveFindBy(locator: String): List<By> {
        requiresNotBlank(locator, "Invalid locator", locator)
        val mobileService = getMobileService()
        val locatorHelper = mobileService.locatorHelper

        return if (RegexUtils.isExact(StringUtils.trim(locator), regexOneOfLocator))
            locatorHelper.resolveCompoundLocators(mobileService.profile.mobileType, locator)
        else {
            val findBy = locatorHelper.resolve(locator)
            if (context.isVerbose) ConsoleUtils.log("resolve locator as $findBy")
            listOf(findBy)
        }
    }

    @Throws(NoSuchElementException::class)
    internal fun findElement(locator: String): Pair<MobileElement, By> {
        requiresNotBlank(locator, "Invalid locator", locator)

        val findBys = resolveFindBy(locator)
        if (findBys.isEmpty()) throw NoSuchElementException("Unable to resolve any locator via $locator")

        if (findBys.size == 1) {
            val findBy = findBys[0]
            return findElement(findBy) to findBy
        }

        for (findBy in findBys) {
            try {
                val matched = findElement(findBy)
                ConsoleUtils.log("[one-of]: A match found via locator ${toLocatorString(findBy)}")
                return (matched to findBy)
            } catch (e: NoSuchElementException) {
                continue
            }
        }

        throw NoSuchElementException("$NO_MATCH_COMPOUND_LOCATOR $locator")
    }

    @Throws(NoSuchElementException::class)
    internal fun findElement(findBy: By): MobileElement {
        val mobileService = getMobileService()
        val profile = mobileService.profile
        val locator = toLocatorString(findBy)
        return if (profile.explicitWaitEnabled)
            try {
                newWaiter("find element via locator '${locator}'").until { it.findElement(findBy) }
            } catch (e: TimeoutException) {
                val err = "Timed out after ${profile.explicitWaitMs}ms looking for element that matches '${locator}'"
                log(err)
                throw NoSuchElementException(err)
            }
        else
            mobileService.driver.findElement(findBy)
    }

    // @Throws(NoSuchElementException::class)
    // internal fun findElement(locators: List<ConditionalLocator>): MobileElement {
    //     val mobileType = getMobileService().profile.mobileType.toString()
    //     val filteredLocators = locators.filter { it.condition == mobileType }
    //     for (qualified in filteredLocators) {
    //         try {
    //             val matched = findElement(qualified.locator)
    //             ConsoleUtils.log("[conditional-locators]: A match found via locator ${qualified.locator}")
    //             return matched
    //         } catch (e: NoSuchElementException) {
    //             continue
    //         }
    //     }
    //
    //     throw NoSuchElementException("No element matched to any of these locators: $filteredLocators")
    // }

    internal fun findElements(locator: String): List<MobileElement> {
        requiresNotBlank(locator, "Invalid locator", locator)

        val mobileService = getMobileService()
        val profile = mobileService.profile

        val findBys = resolveFindBy(locator)
        if (findBys.isEmpty()) throw NoSuchElementException("Unable to resolve any locator via $locator")

        if (profile.explicitWaitEnabled) {
            try {
                newWaiter("find elements via locator '$locator'").until { findElements(it, findBys) }
            } catch (e: TimeoutException) {
                log("Timed out after ${profile.explicitWaitMs}ms looking for any element that matches '$locator'")
                listOf()
            }
        }

        return findElements(mobileService.driver, findBys)
    }

    internal fun findElements(driver: AppiumDriver<MobileElement>, findBys: List<By>): List<MobileElement> {
        if (findBys.size == 1) return driver.findElements(findBys[0])

        for (findBy in findBys) {
            try {
                val matched = driver.findElements(findBy)
                if (CollectionUtils.isNotEmpty(matched)) {
                    ConsoleUtils.log("[one-of]: A match found via locator ${toLocatorString(findBy)}")
                    return matched
                }
            } catch (e: NoSuchElementException) {
                continue
            }
        }

        return emptyList()
    }

    internal fun findElements(searchFrom: MobileElement, locator: String): List<MobileElement> {
        requiresNotBlank(locator, "Invalid locator", locator)

        val mobileService = getMobileService()
        val profile = mobileService.profile

        val findBys = resolveFindBy(locator)
        if (findBys.isEmpty()) throw NoSuchElementException("Unable to resolve any locator via $locator")

        if (profile.explicitWaitEnabled) {
            try {
                newWaiter(searchFrom, profile.explicitWaitMs, "find elements via locator '$locator'")
                    .until { findElements(it, findBys) }
            } catch (e: TimeoutException) {
                log("Timed out after ${profile.explicitWaitMs}ms looking for any element that matches '$locator'")
                listOf()
            }
        }

        return findElements(searchFrom, findBys)
    }

    internal fun findElements(searchFrom: MobileElement, findBys: List<By>): List<MobileElement> {
        if (findBys.size == 1) return searchFrom.findElements(findBys[0])

        for (findBy in findBys) {
            try {
                val matched = searchFrom.findElements(findBy)
                if (CollectionUtils.isNotEmpty(matched)) {
                    ConsoleUtils.log("[one-of]: A match found via locator ${toLocatorString(findBy)}")
                    return matched
                }
            } catch (e: NoSuchElementException) {
                continue
            }
        }

        return emptyList()
    }

    internal fun findFirstMatch(locator: String): MobileElement? {
        val matches = findElements(locator)
        return if (CollectionUtils.isEmpty(matches)) null else matches[0]
    }

    internal fun isElementPresent(locator: String) =
        try {
            !Objects.isNull(findElement(locator))
        } catch (e: WebDriverException) {
            false
        }

    internal fun isElementVisible(locator: String) =
        try {
            val (element, _) = findElement(locator)
            if (Objects.isNull(element))
                false
            else {
                when (getMobileService().profile.mobileType) {
                    ANDROID -> BooleanUtils.toBoolean(element.getAttribute("displayed"))
                    IOS     -> {
                        // special case for image: check either itself or parent for visibility
                        if (StringUtils.equals(element.getAttribute("type"), "XCUIElementTypeImage"))
                            BooleanUtils.toBoolean(element.getAttribute("visible")) ||
                            BooleanUtils.toBoolean(element.findElementByXPath("./parent::*").getAttribute("visible"))
                        else
                            BooleanUtils.toBoolean(element.getAttribute("visible"))
                    }
                }
            }
        } catch (e: NoSuchElementException) {
            false
        }

    internal fun waitForCondition(maxWaitMs: Long, condition: Function<WebDriver, Any>) =
        try {
            newWaiter(maxWaitMs, "wait $maxWaitMs ms for specified condition is reached").until(condition) != null
        } catch (e: TimeoutException) {
            log("Condition not be met on within specified wait time of $maxWaitMs ms")
            false
        } catch (e: WebDriverException) {
            log("Error while waiting for a condition to be met: ${resolveErrorMessage(e)}")
            false
        } catch (e: Exception) {
            log("Error while waiting for a condition to be met: ${e.message}")
            false
        }

    internal fun collectTextList(locator: String) = collectTextList(findElements(locator))

    internal fun collectTextList(matches: List<MobileElement>): List<String> =
        if (CollectionUtils.isEmpty(matches)) listOf() else matches.map { it.text }.toList()

    internal fun collectAttributeValues(locator: String, attribute: String) =
        findElements(locator).map { it.getAttribute(attribute) }.toList()

    @Throws(IOException::class)
    internal fun postScreenshot(target: File, locator: String?): StepResult {
        val captured = if (locator == null) "full screen" else "'$locator'"
        return if (context.isOutputToCloud) {
            val cloudUrl = context.otc.importMedia(target, true)
            context.setData(OPT_LAST_OUTPUT_LINK, cloudUrl)
            context.setData(OPT_LAST_OUTPUT_PATH, StringUtils.substringBeforeLast(cloudUrl, "/"))
            StepResult.success("Screenshot captured for $captured to URL $cloudUrl")
        } else {
            val link = target.absolutePath
            context.setData(OPT_LAST_OUTPUT_LINK, link)
            context.setData(OPT_LAST_OUTPUT_PATH,
                            if (StringUtils.contains(link, "\\")) StringUtils.substringBeforeLast(link, "\\")
                            else StringUtils.substringBeforeLast(link, "/"))
            StepResult.success("Screenshot captured for $captured to file '$target'")
        }
    }

    private fun clearText(locator: String) {
        val (element, findBy) = findElement(locator)
        val elementText = element.text
        element.click()
        // blank/spaces must be treated different, at least for android
        if (StringUtils.isBlank(elementText))
            for (i in (0..elementText.length)) findElement(findBy).sendKeys(BACK_SPACE)
        else
            findElement(findBy).clear()
    }

    private fun clearText(findBy: By) {
        val element = findElement(findBy)
        val elementText = element.text
        element.click()
        // blank/spaces must be treated different, at least for android
        if (StringUtils.isBlank(elementText))
            for (i in (0..elementText.length)) findElement(findBy).sendKeys(BACK_SPACE)
        else
            findElement(findBy).clear()
    }

    private fun keyPress(code: Int): StepResult = executeCommand(PRESS_KEY_CODE, mutableMapOf("keycode" to code))

    private fun executeCommand(action: String, params: MutableMap<String, *>? = null): StepResult {
        val mobileService = getMobileService()
        val response = mobileService.driver.execute(action, params)
        if (context.isVerbose) context.logger.log(context.currentTestStep, "invoked $action: $response}", false)
        return if (response.status == 0) StepResult.success() else StepResult.fail(response.toString())
    }

    private fun withPostActionWait(action: Actions, mobileService: MobileService) {
        withPostActionWait(action, mobileService.profile.postActionWaitMs)
    }

    private fun withPostActionWait(action: Actions, postWaitMs: Long) {
        if (postWaitMs > MIN_WAIT_MS) action.pause(postWaitMs).perform() else action.perform()
    }

    /**
     * derive at a sensible wait time for each "wait for element" task
     */
    private fun deriveMaxWaitMs(waitMs: String): Long {
        val defWaitMs = getMobileService().profile.explicitWaitMs
        val maxWait = if (StringUtils.isBlank(waitMs)) defWaitMs else NumberUtils.toDouble(waitMs).toLong()
        return if (maxWait < MIN_WAIT_MS) defWaitMs else maxWait
    }

    private fun waitMs(ms: Long) = WaitOptions.waitOptions(Duration.ofMillis(ms))

    private fun isDeviceLocked() =
        when (val driver = getMobileService().driver) {
            is AndroidDriver -> driver.isDeviceLocked
            is IOSDriver     -> driver.isDeviceLocked
            else             -> {
                val response = driver.execute("isLocked")
                BooleanUtils.toBoolean(Objects.toString(response.value, ""))
            }
        }

    private fun hideKeyboard(tryHarder: Boolean) =
        try {
            when (val driver = getMobileService().driver) {
                is AndroidDriver -> driver.hideKeyboard()
                is IOSDriver     -> if (tryHarder) driver.hideKeyboard(TAP_OUTSIDE) else driver.hideKeyboard()
                else             -> driver.hideKeyboard()
            }
            true
        } catch (e: WebDriverException) {
            if (StringUtils.contains(e.message, "keyboard cannot be closed"))
                ConsoleUtils.log("keyboard already hidden")
            false
        }

    private fun newTouchAction(mobileService: MobileService): TouchAction<*> =
        if (mobileService.isAndroid()) AndroidTouchAction(mobileService.driver)
        else IOSTouchAction(mobileService.driver)

    private fun toPointOption(position: String, currentScreen: Dimension): PointOption<*> {
        val x = StringUtils.substringBefore(position.toLowerCase(), ",")
        requiresPositiveNumber(x, "Invalid x-coordinate", x)

        val y = StringUtils.substringAfter(position.toLowerCase(), ",")
        requiresPositiveNumber(y, "Invalid y-coordinate", y)

        return PointOption.point(withinLimit(EDGE_WIDTH, currentScreen.width - EDGE_WIDTH, NumberUtils.toInt(x)),
                                 withinLimit(EDGE_WIDTH, currentScreen.height - EDGE_WIDTH, NumberUtils.toInt(y)))
    }

    private fun withinLimit(least: Int, most: Int, number: Int) =
        if (number < least) least else if (number > most) most else number

    private fun resolveWaitMs(postTaWaitMs: String): Long {
        val fallbackWaitMs = getMobileService().profile.postActionWaitMs
        val waitMillis = if (StringUtils.isBlank(postTaWaitMs)) fallbackWaitMs else NumberUtils.toLong(postTaWaitMs)
        return if (waitMillis < MIN_WAIT_MS) {
            ConsoleUtils.log("Invalid 'waitMs' ($postTaWaitMs); default to $fallbackWaitMs")
            fallbackWaitMs
        } else
            waitMillis
    }
}