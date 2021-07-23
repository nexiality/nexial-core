package org.nexial.core.plugins.mobile

import io.appium.java_client.MobileBy
import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.By

/**
 * References:
 * https://developer.android.com/intl/ru/training/accessibility/accessible-app.html
 * https://developer.apple.com/library/ios/documentation/UIKit/Reference/UIAccessibilityIdentification_Protocol/index.html
 */
class MobileLocatorHelper(private val mobileService: MobileService) {
    private val prefixId = "id"
    private val prefixAccessibility = "a11y"
    private val prefixClass = "class"
    private val prefixXPath = "xpath"
    private val prefixResourceId = "res"
    private val prefixPredicate = "predicate"
    private val prefixClassChain = "cc"
    private val prefixName = "name"

    // todo
    private val prefixImage = "image"

    private val xpathStartsWith = listOf("/", "./", "(/", "( /", "(./", "( ./")

    internal fun resolve(locator: String, allowRelative: Boolean): By {
        if (StringUtils.isBlank(locator)) throw IllegalArgumentException("Invalid locator: $locator")

        // default
        for (startsWith in xpathStartsWith)
            if (StringUtils.startsWith(locator, startsWith))
                return By.xpath(if (allowRelative) locator else fixBadXpath(locator))
        if (StringUtils.containsNone(locator, "=")) return By.id(locator)

        val strategy = StringUtils.trim(StringUtils.lowerCase(StringUtils.substringBefore(locator, "=")))
        val loc = StringUtils.trim(StringUtils.substringAfter(locator, "="))
        val isIOS = mobileService.profile.mobileType.isIOS()

        return when (strategy) {
            // standard ones
            prefixId, prefixName, prefixResourceId -> By.id(loc)
            prefixAccessibility                    -> MobileBy.AccessibilityId(loc)
            prefixClass                            -> By.className(loc)
            prefixXPath                            -> By.xpath(if (allowRelative) loc else fixBadXpath(loc))

            // ios specific
            prefixPredicate                        ->
                if (isIOS) MobileBy.iOSNsPredicateString(loc)
                else throw IllegalArgumentException("This locator is only supported on iOS device: $locator")
            prefixClassChain                       ->
                if (isIOS) MobileBy.iOSClassChain(loc)
                else throw IllegalArgumentException("This locator is only supported on iOS device: $locator")

            // catch all
            else                                   -> return By.id(loc)
        }
    }

    internal fun resolve(locator: String) = resolve(locator, false)

    internal fun fixBadXpath(locator: String?): String? {
        val loc = StringUtils.trim(locator)
        return when {
            StringUtils.isEmpty(loc)             -> locator
            StringUtils.startsWith(loc, ".//")   -> StringUtils.substring(loc, 1)
            StringUtils.startsWith(loc, "(.//")  -> "(" + StringUtils.substring(loc, 2)
            StringUtils.startsWith(loc, "( .//") -> "(" + StringUtils.substring(loc, 3)
            else                                 -> loc
        }
    }
}