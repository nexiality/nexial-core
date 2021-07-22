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
import org.nexial.core.NexialConst.Mobile.*
import org.nexial.core.NexialConst.OPT_LAST_OUTPUT_LINK
import org.nexial.core.NexialConst.OPT_LAST_OUTPUT_PATH
import org.nexial.core.NexialConst.PolyMatcher.*
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

    fun assertElementPresent(locator: String): StepResult {
        return if (isElementPresent(locator))
            StepResult.success("EXPECTED element '$locator' found")
        else {
            val msg = "Expected element not found at '$locator'"
            ConsoleUtils.log(msg)
            StepResult.fail(msg)
        }
    }

    fun assertTextPresent(locator: String, text: String): StepResult {
        return if (TextUtils.polyMatch(findElement(locator).text, text))
            StepResult.success("Element of '${locator}' contains text matching '${text}'")
        else
            StepResult.fail("Element of '${locator}' DOES NOT contains text matching '${text}'")
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
        val element = findElement(locator)
        val mobileService = getMobileService()
        withPostActionWait(Actions(mobileService.driver).click(element), mobileService)
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
                xpath.append("starts-with(@text,'${text}')]")

            StringUtils.startsWith(text, START_ANY_CASE)   ->
                xpath.append("starts-with(lower-case(@text),'${text.toLowerCase()}')]")

            StringUtils.startsWith(text, END)              ->
                xpath.append("ends-with(@text,'${text}')]")

            StringUtils.startsWith(text, END_ANY_CASE)     ->
                xpath.append("ends-with(lower-case(@text),'${text.toLowerCase()}')]")

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
            withPostActionWait(Actions(mobileService.driver).click(element).sendKeys(element, text), mobileService)
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

        val dir = Direction.valueOf(direction.toUpperCase())
        val elem = findElement(locator)

        val driver = getMobileService().driver
        val screenDimension = driver.manage().window().size

        val offsets = when (dir) {
            DOWN  -> Pair(0, screenDimension.height)
            UP    -> Pair(0, screenDimension.height * -1)
            LEFT  -> Pair(screenDimension.width * -1, 0)
            RIGHT -> Pair(screenDimension.width, 0)
        }

        Actions(driver)
            .moveToElement(elem)
            .clickAndHold()
            .moveByOffset(offsets.first, offsets.second)
            .release()
            .perform()
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

        val element = findElement(locator)
        val size = element.size
        val location = element.location
        val dimension = Rectangle(location.x, location.y, size.height, size.width)

        val mobileService = getMobileService()
        val driver = mobileService.driver

        // todo: generate `OPT_LAST_SCREENSHOT_NAME` var in context? output to cloud?
        val screenshot = ScreenshotUtils.saveScreenshot(driver, file, dimension)
                         ?: return StepResult.fail("Unable to capture screenshot on '$locator'")
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

    internal fun newWaiter(): FluentWait<AppiumDriver<MobileElement>> {
        val mobileService = getMobileService()
        return FluentWait(mobileService.driver)
            .withTimeout(Duration.ofMillis(mobileService.profile.explicitWaitMs))
            .pollingEvery(Duration.ofMillis(10))
            .ignoring(WebDriverException::class.java)
    }

    internal fun resolveFindBy(locator: String) = if (locator.startsWith("/")) By.xpath(locator) else By.id(locator)

    internal fun findElement(locator: String): WebElement {
        requiresNotBlank(locator, "Invalid locator", locator)
        return try {
            newWaiter().withMessage("find element via locator '$locator'")
                .until { it.findElement(resolveFindBy(locator)) }
        } catch (e: TimeoutException) {
            val err = "Timed out after ${getMobileService().profile.explicitWaitMs}ms " +
                      "looking for element that matches '$locator'"
            log(err)
            throw NoSuchElementException(err)
        }
    }

    internal fun findElements(locator: String): List<WebElement>? {
        requiresNotBlank(locator, "Invalid locator", locator)
        return try {
            newWaiter().withMessage("find elements via locator '$locator'")
                .until { it.findElements(resolveFindBy(locator)) }
        } catch (e: TimeoutException) {
            log("Timed out after ${getMobileService().profile.explicitWaitMs}ms " +
                "looking for any element that matches '$locator'")
            null
        }
    }

    internal fun findFirstMatch(locator: String): WebElement? {
        requiresNotBlank(locator, "Invalid locator", locator)
        return try {
            val matches = newWaiter().withMessage("find element via locator '$locator'")
                .until { it.findElements(resolveFindBy(locator)) }
            if (CollectionUtils.isEmpty(matches)) null else matches[0]
        } catch (e: TimeoutException) {
            log("Timed out after ${getMobileService().profile.explicitWaitMs}ms " +
                "looking for any element that matches '$locator'")
            null
        }
    }

    internal fun isElementPresent(locator: String): Boolean {
        requiresNotBlank(locator, "Invalid locator", locator)
        return try {
            newWaiter()
                .withMessage("check for the present of an element via locator '$locator")
                .until { it.findElement(resolveFindBy(locator)) } != null
        } catch (e: TimeoutException) {
            false
        }
    }

    internal fun waitForCondition(maxWaitMs: Long, condition: Function<WebDriver, Any>) =
        try {
            newWaiter().until(condition) != null
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

    private fun deriveMaxWaitMs(waitMs: String): Long {
        val defWaitMs = getMobileService().profile.explicitWaitMs
        val maxWait = if (StringUtils.isBlank(waitMs)) defWaitMs else NumberUtils.toDouble(waitMs).toLong()
        return if (maxWait < 1) defWaitMs else maxWait
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