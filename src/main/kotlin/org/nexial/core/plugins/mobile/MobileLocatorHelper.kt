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

import io.appium.java_client.MobileBy
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.plugins.web.LocatorHelper.normalizeXpathText
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
    private val prefixText = "text"
    private val prefixNearby = "nearby"

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
            prefixText                             -> By.xpath("//*[@text=${normalizeXpathText(loc)}]")
            prefixNearby                           -> handleNearbyLocator(mobileService.profile.mobileType, loc)

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

    companion object {
        private const val leftOf = "left-of"
        private const val rightOf = "right-of"
        private const val regexNearbyNameValueSpec = "\\s*.+\\s*=\\s*.+\\s*"

        /**
         * Expected format: `nearby={left-of|right-of=text}{attribute_with_value_as_true,attribute=value,...}`
         *
         * Only looking for element that are visible and usable, hence implied:
         * - android: @displayed='true' and @enabled='true'
         * - ios: @visible='true' and @enabled='true'
         *
         * Example:
         *  `nearby={left-of=None of these}{clickable,enabled}`
         *  `nearby={right-of=Yes}{clickable,class=android.widget.GroupView}`
         */
        internal fun handleNearbyLocator(mobileType: MobileType, specs: String): By {
            val errorPrefix = "Invalid NEARBY locator '$specs'"
            if (StringUtils.isBlank(specs)) throw IllegalArgumentException(errorPrefix)
            //
            if (!TextUtils.isBetween(specs.trim(), "{", "}"))
                throw IllegalArgumentException("$errorPrefix - Invalid format")

            val parts = mutableListOf("@enabled='true'")
            if (mobileType.isAndroid()) parts.add("@displayed='true'")
            else if (mobileType.isIOS()) parts.add("@visible='true'")

            // aggressively trim off extraneous leading/trailing spaces
            TextUtils.groups(specs.trim(), "{", "}", false).forEach { group ->
                group.split(",").forEach { part ->
                    if (part.matches(Regex(regexNearbyNameValueSpec))) {
                        val name = part.substringBefore("=").trim()
                        val value = normalizeXpathText(part.substringAfter("=").trim())
                        when (name) {
                            leftOf  -> parts.add("following-sibling::*[1][@text=$value]")
                            rightOf -> parts.add("preceding-sibling::*[1][@text=$value]")
                            else    -> parts.add("@$name=$value")
                        }
                    } else
                        parts.add("@${part.trim()}='true'")
                }
            }

            return By.xpath(parts.joinToString(prefix = "//*[", separator = " and ", postfix = "]"))
        }
    }
}