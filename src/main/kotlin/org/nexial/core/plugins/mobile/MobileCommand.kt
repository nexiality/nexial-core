package org.nexial.core.plugins.mobile

import io.appium.java_client.AppiumDriver
import io.appium.java_client.MobileElement
import io.appium.java_client.MultiTouchAction
import io.appium.java_client.TouchAction
import io.appium.java_client.android.AndroidTouchAction
import io.appium.java_client.ios.IOSTouchAction
import io.appium.java_client.touch.WaitOptions
import io.appium.java_client.touch.offset.PointOption
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.core.NexialConst.Mobile.EDGE_WIDTH
import org.nexial.core.NexialConst.Mobile.ERR_NO_SERVICE
import org.nexial.core.ShutdownAdvisor
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.model.TestStep
import org.nexial.core.plugins.CanTakeScreenshot
import org.nexial.core.plugins.ForcefulTerminate
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.plugins.base.ScreenshotUtils
import org.nexial.core.plugins.mobile.Direction.*
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.OutputFileUtils
import org.openqa.selenium.*
import org.openqa.selenium.ScreenOrientation.LANDSCAPE
import org.openqa.selenium.ScreenOrientation.PORTRAIT
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.FluentWait
import java.io.File.separator
import java.time.Duration


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

    fun click(locator: String): StepResult {
        val element = findElement(locator)
        val mobileService = getMobileService()
        Actions(mobileService.driver).click(element).pause(mobileService.profile.postActionWaitMs).perform()
        return StepResult.success("Clicked on '$locator'")
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

    fun clickUntilNotFound(locator: String, waitMs: String, max: String): StepResult {
        val mobileService = getMobileService()

        var waitMillis = if (StringUtils.isBlank(waitMs))
            mobileService.profile.postActionWaitMs
        else
            NumberUtils.toLong(waitMs)
        if (waitMillis < 250) {
            ConsoleUtils.log("Invalid 'waitMs' ($waitMs); default to ${mobileService.profile.postActionWaitMs}")
            waitMillis = mobileService.profile.postActionWaitMs
        }

        if (StringUtils.isNotEmpty(max)) requiresPositiveNumber(max, "Invalid max try number", max)
        val maxTries = if (StringUtils.isEmpty(max)) -1 else NumberUtils.toInt(max)

        var attempt = 0

        var element = findFirstMatch(locator)
        while (element != null) {
            Actions(mobileService.driver).click(element).pause(waitMillis).perform()
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

    fun type(locator: String, text: String): StepResult {
        requiresNotEmpty(text, "Invalid text", text)
        val element = findElement(locator)
        val mobileService = getMobileService()

        // too fast?
        // Actions(mobileService.driver).sendKeys(element, text).pause(mobileService.profile.postActionWaitMs).perform()
        // try with additional waits to let the onscreen keyboard renders/settles
        Actions(mobileService.driver)
            .click(element)
            .pause(mobileService.profile.postActionWaitMs)
            .sendKeys(element, text)
            .pause(mobileService.profile.postActionWaitMs)
            .perform()

        return StepResult.success("Text '$text' typed on '$locator'")
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
        val ANIMATION_TIME = 200L
        val PRESS_TIME = 200L

        val mobileService = getMobileService()
        val driver = mobileService.driver
        val currentScreen = driver.manage().window().size

        val startFrom = toPointOption(start, currentScreen)
        val endAt = toPointOption(end, currentScreen)

        // execute swipe using TouchAction
        return try {
            newTouchAction(mobileService)
                .press(startFrom).waitAction(waitMs(PRESS_TIME))
                .moveTo(endAt).waitAction(waitMs(ANIMATION_TIME))
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

        return StepResult.success("Screenshot for '$locator' created on $file")
    }

    fun saveCount(`var`: String, locator: String): StepResult {
        requiresValidVariableName(`var`)
        val matches = findElements(locator)
        val count = CollectionUtils.size(matches)
        context.setData(`var`, count)
        return StepResult.success("$count match(es) of '$locator' found and its count is stored to data variable 'var'")
    }

    // fun rotate(start: String, end: String): StepResult
    // fun assertTextPresent(locator: String, text: String): StepResult
    // fun back(locator: String, text: String): StepResult
    // fun main(locator: String, text: String): StepResult
    // fun apps(locator: String, text: String): StepResult
    // fun airplaneMode(): StepResult

    fun closeApp(): StepResult {
        val mobileService = getMobileService()
        mobileService.shutdown()
        context.mobileServices.remove(mobileService.profile.profile)
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
            newWaiter().until { it.findElement(resolveFindBy(locator)) } != null
        } catch (e: TimeoutException) {
            false
        }
    }

    private fun waitMs(ms: Long) = WaitOptions.waitOptions(Duration.ofMillis(ms))

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