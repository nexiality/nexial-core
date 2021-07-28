package org.nexial.core.plugins.mobile

import io.appium.java_client.AppiumDriver
import io.appium.java_client.MobileElement
import io.appium.java_client.MultiTouchAction
import io.appium.java_client.TouchAction
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.AndroidTouchAction
import io.appium.java_client.android.nativekey.AndroidKey.*
import io.appium.java_client.ios.IOSDriver
import io.appium.java_client.ios.IOSTouchAction
import io.appium.java_client.remote.HideKeyboardStrategy.TAP_OUTSIDE
import io.appium.java_client.touch.WaitOptions
import io.appium.java_client.touch.offset.PointOption
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.NexialConst.PolyMatcher.*
import org.nexial.core.NexialConst.Web.GROUP_LOCATOR_SUFFIX
import org.nexial.core.ShutdownAdvisor
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.model.TestStep
import org.nexial.core.plugins.CanTakeScreenshot
import org.nexial.core.plugins.ForcefulTerminate
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.plugins.base.ScreenshotUtils
import org.nexial.core.plugins.mobile.Direction.*
import org.nexial.core.plugins.web.LocatorHelper.normalizeXpathText
import org.nexial.core.plugins.web.WebDriverExceptionHelper.resolveErrorMessage
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.OutputFileUtils
import org.openqa.selenium.*
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
import kotlin.math.max

// https://github.com/appium/appium-base-driver/blob/master/lib/protocol/routes.js#L345
class MobileCommand : BaseCommand(), CanTakeScreenshot, ForcefulTerminate {
    private var mobileService: MobileService? = null

    override fun init(context: ExecutionContext?) {
        super.init(context)
        ShutdownAdvisor.addAdvisor(this)
    }

    override fun getTarget() = "mobile"

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
        return StepResult.success()
    }

    fun assertElementPresent(locator: String): StepResult =
        if (isElementPresent(locator))
            StepResult.success("EXPECTED element '$locator' found")
        else
            StepResult.fail("Expected element not found at '$locator'")

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

    fun assertTextPresent(locator: String, text: String): StepResult =
        if (findElements(locator).any { TextUtils.polyMatch(it.text, text) })
            StepResult.success("Element of '${locator}' contains text matching '${text}'")
        else
            StepResult.fail("Element of '${locator}' DOES NOT contains text matching '${text}'")

    fun saveText(`var`: String, locator: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)

        val elementText = findElement(locator).text
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

    fun waitForElementPresent(locator: String, waitMs: String): StepResult {
        requiresNotBlank(locator, "invalid locator", locator)
        val maxWaitMs = deriveMaxWaitMs(waitMs)
        val isPresent = waitForCondition(maxWaitMs) { isElementPresent(locator) }
        return if (isPresent) {
            StepResult.success("Element by locator '$locator' is present")
        } else {
            StepResult.fail("Element by locator '" + locator + "' is NOT present within " + maxWaitMs + "ms")
        }
    }

    fun click(locator: String): StepResult {
        val mobileService = getMobileService()
        withPostActionWait(Actions(mobileService.driver).click(findElement(locator)), mobileService)
        return StepResult.success("Clicked on '$locator'")
    }

    /**
     * partially supports polymatcher
     */
    fun clickByDisplayText(text: String): StepResult {
        requiresNotBlank(text, "invalid display text", text)

        val xpath = StringBuilder("//*[@displayed='true' and ")

        when {
            StringUtils.startsWith(text, EMPTY)            ->
                return StepResult.fail("PolyMatcher $EMPTY is not supported for this command")

            StringUtils.startsWith(text, BLANK)            ->
                return StepResult.fail("PolyMatcher $BLANK is not supported for this command")

            StringUtils.startsWith(text, NUMERIC)          ->
                return StepResult.fail("PolyMatcher $NUMERIC is not supported for this command")

            StringUtils.startsWith(text, REGEX)            ->
                return StepResult.fail("PolyMatcher $REGEX is not supported for this command")

            StringUtils.startsWith(text, CONTAIN)          ->
                xpath.append("contains(@text,${normalizeXpathText(text)})]")

            StringUtils.startsWith(text, CONTAIN_ANY_CASE) ->
                xpath.append("contains(@text,${normalizeXpathText(text.toLowerCase())})]")

            StringUtils.startsWith(text, START)            ->
                xpath.append("starts-with(@text,${normalizeXpathText(text)})]")

            StringUtils.startsWith(text, START_ANY_CASE)   ->
                xpath.append("starts-with(lower-case(@text),${normalizeXpathText(text.toLowerCase())})]")

            StringUtils.startsWith(text, END)              ->
                xpath.append("ends-with(@text,${normalizeXpathText(text)})]")

            StringUtils.startsWith(text, END_ANY_CASE)     ->
                xpath.append("ends-with(lower-case(@text),${normalizeXpathText(text.toLowerCase())})]")

            StringUtils.startsWith(text, LENGTH)           -> {
                requiresPositiveNumber(text, "invalid number specified as length", text)
                xpath.append("string-length(@text)=$text]")
            }

            StringUtils.startsWith(text, EXACT)            ->
                xpath.append("@text=${normalizeXpathText(text)}]")

            else                                           ->
                xpath.append("@text=${normalizeXpathText(text)}]")
        }

        return click(xpath.toString())
    }

    fun clickUntilNotFound(locator: String, waitMs: String, max: String): StepResult {
        val mobileService = getMobileService()

        var waitMillis = if (StringUtils.isBlank(waitMs))
            mobileService.profile.postActionWaitMs
        else
            NumberUtils.toLong(waitMs)
        if (waitMillis < MIN_WAIT_MS) {
            ConsoleUtils.log("Invalid 'waitMs' ($waitMs); default to ${mobileService.profile.postActionWaitMs}")
            waitMillis = mobileService.profile.postActionWaitMs
        }

        if (StringUtils.isNotEmpty(max)) requiresPositiveNumber(max, "Invalid max try number", max)
        val maxTries = if (StringUtils.isEmpty(max)) -1 else NumberUtils.toInt(max)

        var attempt = 0

        var element = findFirstMatch(locator)
        while (element != null) {
            withPostActionWait(Actions(mobileService.driver).click(element), waitMillis)
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
        val element = findElement(locator)
        val mobileService = getMobileService()

        withPostActionWait(Actions(mobileService.driver)
                               .moveToElement(element, EDGE_WIDTH, EDGE_WIDTH)
                               .clickAndHold()
                               .pause(holdTime)
                               .release(),
                           mobileService)
        return StepResult.success("successfully long-click on '$locator'")
    }

    fun doubleClick(locator: String, xOffset: String, yOffset: String): StepResult {
        requiresInteger(xOffset, "invalid x-offset", xOffset)
        requiresInteger(yOffset, "invalid y-offset", yOffset)

        val element = findElement(locator)
        val mobileService = getMobileService()
        withPostActionWait(
            Actions(mobileService.driver).moveToElement(element, NumberUtils.toInt(xOffset), NumberUtils.toInt(yOffset))
                .doubleClick(),
            mobileService)
        return StepResult.success("Double-clicked on '$locator'")
    }

    fun type(locator: String, text: String): StepResult {
        val element = findElement(locator)
        val mobileService = getMobileService()

        // too fast?
        // Actions(mobileService.driver).sendKeys(element, text).pause(mobileService.profile.postActionWaitMs).perform()

        return if (StringUtils.isEmpty(text)) {
            element.click()
            element.clear()
            val currentText = element.text
            if (StringUtils.isEmpty(currentText))
                StepResult.success("Element '${locator}' cleared")
            else
                StepResult.fail("Element '${locator}' not cleared; current text is '${currentText}'")
        } else {
            element.sendKeys(text)
            val postWaitMs = mobileService.profile.postActionWaitMs
            if (postWaitMs > MIN_WAIT_MS) waitFor(postWaitMs.toInt())
            StepResult.success("Text '$text' typed on '$locator'")
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

    fun scroll(locator: String, direction: String): StepResult {
        requiresNotBlank(direction, "Invalid direction", direction)
        val dir = Direction.values().find { it.detail.equals(direction, true) }
                  ?: throw IllegalArgumentException("Invalid direction $direction")

        val elem = findElement(locator)
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
                               .moveToElement(elem)
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
        return executeCommand("sendSMS", mutableMapOf("phoneNumber" to TextUtils.sanitizePhoneNumber(phone),
                                                      "message" to message))
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

        val wait = waitMs(1000)
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
        requireOneOf(orient, "landscape", "portrait")

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
        val screenshot = ScreenshotUtils.saveScreenshot(findElement(locator), file)
        return postScreenshot(screenshot, locator)
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
    fun recentApps() = keyPress(APP_SWITCH.code)

    fun select(locator: String, item: String): StepResult {
        requiresNotBlank(item, "invalid item specified", item)

        val mobileService = getMobileService()
        val driver = mobileService.driver
        val profileName = mobileService.profile.profile

        if (mobileService.profile.mobileType.isAndroid()) {
            val result = click(locator)
            if (result.failed()) return result

            waitFor(800)

            // find the number of scrollables (usually the last one is the right one)
            val scrollables =
                driver.findElements(By.xpath("//*[@scrollable='true' and @displayed='true' and @enabled='true']"))

            // the last one is likely the right one
            val scrollTarget = if (CollectionUtils.isEmpty(scrollables)) {
                // try another way
                val scrollables2 =
                    driver.findElements(By.xpath("//android.widget.ScrollView[@displayed='true' and @enabled='true']"))
                if (CollectionUtils.isEmpty(scrollables2)) return StepResult.fail("Unable to locate a usable dropdown")

                scrollables2[scrollables2.size - 1]
            } else
                scrollables[scrollables.size - 1]

            // determine how many lines to scroll (if needed)
            val scrollAmount = context.getIntConfig(target, profileName, DROPDOWN_TEXT_LINE_HEIGHT) *
                               context.getIntConfig(target, profileName, DROPDOWN_LINES_TO_SCROLL) * -1
            val maxScrolls = context.getIntConfig(target, profileName, DROPDOWN_MAX_SCROLL)

            val findByOption = By.xpath("//*[@text=${normalizeXpathText(item)}]")

            var scrollCount = 0
            var itemElement: WebElement? = null
            while (itemElement == null) {
                itemElement = scrollTarget.findElements(findByOption).getOrNull(0)

                if (scrollCount >= maxScrolls) break

                if (itemElement == null) {
                    Actions(driver).moveToElement(scrollTarget, 50, 5)
                        .clickAndHold()
                        .moveByOffset(0, scrollAmount)
                        .release()
                        .perform()
                    scrollCount++
                } else
                    break
            }

            return if (itemElement != null) {
                itemElement.click()
                StepResult.success("dropdown option '${item}' selected")
            } else
                StepResult.fail("Unable to find dropdown option '${item}'")
        }

        return StepResult.fail("This command does not support iOS device at this time (#patience_is_a_virtue)")
    }

    // todo: need to figure out permission issue
    // Original error: Error executing adbExec. Original error: 'Command '... ... \\platform-tools\\adb.exe -P 5037
    //  -s emulator-5554 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true' exited with code
    //  255'; Stderr: 'Exception occurred while executing 'broadcast'
    // fun toggleAirplaneMode() = executeCommand("toggleFlightMode")

    // fun rotate(start: String, end: String): StepResult

    fun closeApp(): StepResult =
        when (val driver = getMobileService().driver) {
            is AndroidDriver -> {
                driver.closeApp()
                StepResult.success()
            }
            is IOSDriver     -> {
                driver.closeApp()
                StepResult.success()
            }
            else             -> executeCommand("closeApp")
        }

    fun shutdown(profile: String): StepResult {
        val mobileService = getMobileService()
        mobileService.shutdown()
        context.mobileServices.remove(profile)
        this.mobileService = null
        return StepResult.success()
    }

    internal fun getMobileService(): MobileService {
        if (mobileService == null) throw IllegalStateException(ERR_NO_SERVICE)
        return mobileService as MobileService
    }

    internal fun newWaiter(message: String) = newWaiter(getMobileService().profile.explicitWaitMs, message)

    internal fun newWaiter(waitMs: Long, message: String): FluentWait<AppiumDriver<MobileElement>> {
        val mobileService = getMobileService()
        return FluentWait(mobileService.driver)
            .withTimeout(Duration.ofMillis(waitMs))
            .pollingEvery(Duration.ofMillis(10))
            .ignoring(WebDriverException::class.java)
            .withMessage(message)
    }

    internal fun resolveFindBy(locator: String) = getMobileService().locatorHelper.resolve(locator)

    internal fun findElement(locator: String): WebElement {
        requiresNotBlank(locator, "Invalid locator", locator)
        val findBy = resolveFindBy(locator)
        val profile = getMobileService().profile
        return if (profile.explicitWaitEnabled)
            try {
                newWaiter("find element via locator '$locator'").until { it.findElement(findBy) }
            } catch (e: TimeoutException) {
                val err = "Timed out after ${profile.explicitWaitMs}ms looking for element that matches '$locator'"
                log(err)
                throw NoSuchElementException(err)
            }
        else
            getMobileService().driver.findElement(findBy)
    }

    internal fun findElements(locator: String): List<WebElement> {
        requiresNotBlank(locator, "Invalid locator", locator)
        val findBy = resolveFindBy(locator)
        val profile = getMobileService().profile
        return if (profile.explicitWaitEnabled)
            try {
                newWaiter("find elements via locator '$locator'").until { it.findElements(findBy) }
            } catch (e: TimeoutException) {
                log("Timed out after ${profile.explicitWaitMs}ms looking for any element that matches '$locator'")
                listOf()
            }
        else
            getMobileService().driver.findElements(findBy)
    }

    internal fun findFirstMatch(locator: String): WebElement? {
        requiresNotBlank(locator, "Invalid locator", locator)
        val findBy = resolveFindBy(locator)
        val profile = getMobileService().profile
        val matches = if (profile.explicitWaitEnabled)
            try {
                newWaiter("find element via locator '$locator'").until { it.findElements(findBy) }
            } catch (e: TimeoutException) {
                log("Timed out after ${profile.explicitWaitMs}ms looking for any element that matches '$locator'")
                null
            }
        else
            getMobileService().driver.findElements(findBy)

        return if (CollectionUtils.isEmpty(matches)) null else matches?.get(0)
    }

    internal fun collectTextList(locator: String) = collectTextList(findElements(locator))

    internal fun collectTextList(matches: List<WebElement>): List<String> =
        if (CollectionUtils.isEmpty(matches)) listOf() else matches.map { it.text }.toList()

    internal fun isElementPresent(locator: String): Boolean {
        requiresNotBlank(locator, "Invalid locator", locator)
        val findBy = resolveFindBy(locator)
        val profile = getMobileService().profile
        return if (profile.explicitWaitEnabled)
            try {
                newWaiter("check for the present of an element via locator '$locator")
                    .until { it.findElement(findBy) } != null
            } catch (e: TimeoutException) {
                false
            }
        else
            getMobileService().driver.findElement(findBy) != null
    }

    internal fun isElementVisible(locator: String): Boolean {
        requiresNotBlank(locator, "Invalid locator", locator)
        val findBy = resolveFindBy(locator)

        val profile = getMobileService().profile
        val element = if (profile.explicitWaitEnabled)
            try {
                newWaiter("check for the present of an element via locator '$locator").until { it.findElement(findBy) }
            } catch (e: TimeoutException) {
                null
            }
        else
            getMobileService().driver.findElement(findBy)

        return if (element == null)
            false
        else {
            val mobileType = getMobileService().profile.mobileType
            when {
                mobileType.isIOS()     -> {
                    if (StringUtils.equals(element.getAttribute("type"), "XCUIElementTypeImage"))
                        BooleanUtils.toBoolean(element.findElementByXPath("./..").getAttribute("visible"))
                    else
                        BooleanUtils.toBoolean(element.getAttribute("visible"))
                }
                mobileType.isAndroid() -> BooleanUtils.toBoolean(element.getAttribute("displayed"))
                else                   -> true
            }
        }
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

    @Throws(IOException::class)
    internal fun postScreenshot(target: File, locator: String?): StepResult {
        val captured = if (locator == null) "FullPage" else "'$locator'"
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

    private fun keyPress(code: Int): StepResult = executeCommand("pressKeyCode", mutableMapOf("keycode" to code))

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
}