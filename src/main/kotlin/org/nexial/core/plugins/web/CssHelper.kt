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

package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.Web.*
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.StepResult
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.openqa.selenium.support.Color

class CssHelper(private val delegator: WebCommand) {
    private val contextLogger = delegator.context.logger
    private val locatorHelper = delegator.locatorHelper

    // fun assertCssValue(locator: String, property: String, expected: String): StepResult {
    //     val verbose = delegator.context.isVerbose
    //
    //     val actual = getComputedCssValue(locator, property)
    //     if (verbose) contextLogger.log(delegator, "derive CSS property '$property' for '$locator': '$actual'")
    //
    //     if (StringUtils.isEmpty(expected)) {
    //         return if (StringUtils.isEmpty(actual))
    //             StepResult.success("no value found for CSS property '$property' as EXPECTED")
    //         else
    //             StepResult.fail("no value expected for CSS property '$property', but '$actual' was found")
    //     }
    //
    //     // special treatment for colors
    //     // is it hex? is it rgb/rgba?
    //     val colorExpected = toColor(expected)
    //     if (colorExpected != null) {
    //         val colorActual = extractColor(actual)
    //         return if (colorActual == null)
    //             StepResult.fail("EXPECTED CSS '$property' to be a color value, but found '$actual' instead")
    //         else if (assertSameColor(colorExpected, colorActual))
    //             StepResult.success("EXPECTED color '$expected' matched by CSS property '$property': '$actual'")
    //         else
    //             StepResult.fail("Color '$expected' NOT found in CSS property '$property': '$actual'")
    //     }
    //
    //     return if (delegator.assertPolyMatcher(expected, actual))
    //         StepResult.success("CSS property '$property' value '$actual' match EXPECTED value '$expected'")
    //     else
    //         StepResult.fail("CSS property '$property' value '$actual' DOES NOT match EXPECTED value '$expected'")
    // }

    fun deriveCssValue(locator: String, property: String): String {
        val context = delegator.context
        val useComputedCss = context.getBooleanData(OPT_COMPUTED_CSS, getDefaultBool(OPT_COMPUTED_CSS))
        // if (useComputedCss) return assertCssValue(locator, property, value)

        // 1. use selenium or js to get css value
        val actual = if (useComputedCss) getCssValue(locator, property) else getComputedCssValue(locator, property)

        if (context.isVerbose) contextLogger.log(delegator, "derive CSS property '$property' for '$locator': '$actual'")

        return actual
    }

    fun assertCssPresent(locator: String, property: String, value: String): StepResult? {
        val actual = deriveCssValue(locator, property)

        // 2. CHECK: don't care about the actual value, just the presence of a value
        if (StringUtils.equals(value, "*") && StringUtils.isEmpty(actual))
            return StepResult.success("Value found for CSS property '$property' as EXPECTED: '$actual")

        // 3. CHECK: expects no value
        if (StringUtils.isEmpty(value)) {
            return if (StringUtils.isEmpty(actual))
                StepResult.success("no value found for CSS property '$property' as EXPECTED")
            else
                StepResult.fail("no value expected for CSS property '$property', but '$actual' was found")
        }

        // 4. color value treatment
        // is it hex? is it rgb/rgba?
        val colorExpected = toColor(value)
        if (colorExpected != null) {
            // yes `value` (expected) is a color... check if `actual` is also a color
            val colorActual = extractColor(actual)
                              ?: return StepResult.fail("EXPECTS CSS '$property' as color value, " +
                                                        "but found '$actual' instead")

            val verbose = delegator.context.isVerbose
            if (verbose) contextLogger.log(delegator,
                                           "Comparing expected color '$colorExpected' against actual '$colorActual'")

            return if (assertSameColor(colorExpected, colorActual))
                StepResult.success("EXPECTED CSS property '$property' '$value' matched actual '$actual'")
            else
                StepResult.fail("CSS property '$property' '$value' NOT matched by  '$actual'")
        }

        return if (delegator.assertPolyMatcher(value, actual))
            StepResult.success("CSS property '$property' value '$actual' match EXPECTED value '$value'")
        else
            StepResult.fail("CSS property '$property' value '$actual' DOES NOT match EXPECTED value '$value'")
    }

    fun assertCssNotPresent(locator: String, property: String): StepResult {
        requiresNotBlank(property, "invalid css property", property)
        val actual: String = getCssValue(locator, property)
        return if (StringUtils.isEmpty(actual))
            StepResult.success("No CSS property '$property' found, as EXPECTED")
        else
            StepResult.fail("CSS property '$property' found with UNEXPECTED value '$actual'")
    }

    fun getComputedCssValue(locator: String, property: String): String {
        requiresNotBlank(property, "invalid css property", property)
        val element = locatorHelper.findElement(locator, false)
        val js = JsLib.getComputedCssValue(property)
        return ((delegator.jsExecutor.executeScript(js, element) ?: "") as String).trim()
    }

    fun getCssValue(locator: String, property: String): String {
        requiresNotBlank(property, "invalid css property", property)
        val element = locatorHelper.findElement(locator, false)
        return if (element == null)
            ""
        else
            (element.getCssValue(property) ?: "").trim().lowercase()
    }

    internal fun toColor(colorValue: String): Color? {
        // JS code will accept named color, `transparent`, hex color, rgb, rgba
        val jsColor = delegator.jsExecutor.executeScript(JsLib.toHexColor(colorValue))?.toString()?.trim()
                      ?: return null
        return when {
            jsColor.startsWith("rgba(") -> rgbaToColor(jsColor)
            jsColor.startsWith("rgb(")  -> rgbToColor(jsColor)
            jsColor.startsWith("#")     -> {
                if (jsColor == "#000000" && !jsColor.equals("black", ignoreCase = true))
                    null
                else
                    hexToColor(jsColor)
            }
            // otherwise, probably not a color value
            else                        -> null
        }
    }

    internal fun extractColor(actual: String) =
        when {
            actual == "transparent" -> newTransparentColor()
            hasHexColor(actual)     -> hexToColor(RegexUtils.firstMatches(actual, REGEX_IS_HEX_COLOR)!!)
            hasRGB(actual)          -> rgbToColor(RegexUtils.firstMatches(actual, REGEX_IS_RGB)!!)
            hasRGBA(actual)         -> rgbaToColor(RegexUtils.firstMatches(actual, REGEX_IS_RGBA)!!)
            else                    -> null
        }

    internal fun assertSameColor(expected: Color, actual: Color) =
        if (expected.color.alpha == 0 && actual.color.alpha == 0) true else expected.asRgba() == actual.asRgba()

    internal fun isRGBA(color: String) = RegexUtils.isExact(color, REGEX_IS_RGBA)

    internal fun isRGB(color: String) = RegexUtils.isExact(color, REGEX_IS_RGB)

    internal fun hasRGBA(color: String) = RegexUtils.match(color, REGEX_IS_RGBA)

    internal fun hasRGB(color: String) = RegexUtils.match(color, REGEX_IS_RGB)

    internal fun isTransparentColor(color: String) = isRGBA(color) && StringUtils.equals(color, RGBA_TRANSPARENT)

    internal fun isHexColor(color: String) = RegexUtils.isExact(StringUtils.lowerCase(color), REGEX_IS_HEX_COLOR)

    internal fun hasHexColor(color: String): Boolean {
        if (!color.contains("#")) return false

        val colorValue = "#" + color.substringAfter("#").substringBefore(" ").substringBefore(";").lowercase().trim()
        return RegexUtils.isExact(colorValue, REGEX_IS_HEX_COLOR)
    }

    internal fun convertToRGBA(value: String): String {
        return if (StringUtils.isEmpty(value) ||
                   StringUtils.equalsIgnoreCase(StringUtils.deleteWhitespace(value), RGBA_TRANSPARENT2) ||
                   StringUtils.equalsIgnoreCase(value, "transparent"))
            RGBA_TRANSPARENT
        else if (isHexColor(value)) hexToRgba(value)
        else value
    }

    internal fun hexToRgba(value: String): String {
        val hexColor = value.removePrefix("#").lowercase()
        return when (hexColor.length) {
            3    -> {
                "rgba(${StringUtils.repeat(hexColor[0], 2).toInt(16)}, ${
                    StringUtils.repeat(hexColor[1], 2).toInt(16)
                }, ${StringUtils.repeat(hexColor[2], 2).toInt(16)}, 1)"
            }
            6    -> {
                "rgba(${StringUtils.substring(hexColor, 0, 2).toInt(16)}, ${
                    StringUtils.substring(hexColor, 2, 4).toInt(16)
                }, ${StringUtils.substring(hexColor, 4, 6).toInt(16)}, 1)"
            }
            else -> value
        }
    }

    internal fun hexToColor(value: String): Color {
        val hexColor = value.removePrefix("#").lowercase()
        return when (hexColor.length) {
            3    -> {
                Color(StringUtils.repeat(hexColor[0], 2).toInt(16),
                      StringUtils.repeat(hexColor[1], 2).toInt(16),
                      StringUtils.repeat(hexColor[2], 2).toInt(16),
                      1.0)
            }
            6    -> {
                Color(StringUtils.substring(hexColor, 0, 2).toInt(16),
                      StringUtils.substring(hexColor, 2, 4).toInt(16),
                      StringUtils.substring(hexColor, 4, 6).toInt(16),
                      1.0)
            }
            else -> throw IllegalArgumentException("Invalid HEX color value '$value'")
        }
    }

    internal fun rgbToColor(value: String): Color {
        val colorNumbers = RegexUtils.collectGroups(value, REGEX_IS_RGB)
        return Color(colorNumbers[0].toInt(), colorNumbers[1].toInt(), colorNumbers[2].toInt(), 1.0)
    }

    internal fun rgbaToColor(value: String): Color {
        val colorNumbers = RegexUtils.collectGroups(value, REGEX_IS_RGBA)
        return Color(colorNumbers[0].toInt(), colorNumbers[1].toInt(), colorNumbers[2].toInt(),
                     colorNumbers[3].toDouble())
    }

    internal fun newTransparentColor() = Color(0, 0, 0, 0.0)
}