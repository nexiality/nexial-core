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

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.ListUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.JRegexUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.PolyMatcher.*
import org.nexial.core.NexialConst.PolyMatcher.Message.*
import org.nexial.core.model.NexialFilterList
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.web.LocatorHelper.InnerValueType.TEXT
import org.nexial.core.plugins.web.LocatorHelper.InnerValueType.VALUE
import org.nexial.core.utils.CheckUtils
import org.nexial.core.utils.OutputFileUtils.CASE_INSENSIVE_SORT
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import java.util.*
import java.util.stream.Collectors

class LocatorHelper internal constructor(private val delegator: WebCommand) {
    private enum class InnerValueType {
        TEXT, VALUE
    }

    internal enum class LocatorType(val prefix: String) {
        // --------------------------------------------------------------------
        // standard selenium locators
        // --------------------------------------------------------------------
        ID("id") {
            override fun build(locator: String, relative: Boolean): By = By.id(extract(locator))
        },

        CLASS("class") {
            override fun build(locator: String, relative: Boolean): By = By.className(extract(locator))
        },

        CSS("css") {
            override fun build(locator: String, relative: Boolean): By = By.cssSelector(extract(locator))
        },

        LINK_TEXT("linkText") {
            override fun build(locator: String, relative: Boolean): By = By.linkText(extract(locator))
        },

        NAME("name") {
            override fun build(locator: String, relative: Boolean): By = By.name(extract(locator))
        },

        PARTIAL_LINK_TEXT("partialLinkText") {
            override fun build(locator: String, relative: Boolean): By = By.partialLinkText(extract(locator))
        },

        TAG("tag") {
            override fun build(locator: String, relative: Boolean): By = By.tagName(extract(locator))
        },

        XPATH("xpath") {
            override fun build(locator: String, relative: Boolean): By {
                val xpath = extract(locator)
                return By.xpath(if (relative) xpath else fixBadXpath(xpath))
            }
        },

        // --------------------------------------------------------------------
        // Nexial custom
        // --------------------------------------------------------------------
        // same as `linkText`, just making it easier to type
        LINK("link") {
            override fun build(locator: String, relative: Boolean): By = By.linkText(extract(locator))
        },

        // same as `partialLinkText`, just making it easier to type
        PARTIAL("partial") {
            override fun build(locator: String, relative: Boolean): By = By.partialLinkText(extract(locator))
        },

        // turns text into XPATH, supports some PolyMatcher
        TEXT("text") {
            override fun build(locator: String, relative: Boolean): By {
                val text = StringUtils.trim(extract(locator))
                if (StringUtils.isBlank(text)) throw IllegalArgumentException("Invalid text locator: $locator")

                val elemText = "normalize-space(text())"
                return By.xpath(
                    (if (relative) "." else "") +
                    if (isPolyMatcher(text)) {
                        when {
                            StringUtils.startsWith(text, REGEX)            ->
                                throw IllegalArgumentException("$REGEX_NOT_SUPPORTED $locator")
                            StringUtils.startsWith(text, NUMERIC)          ->
                                throw IllegalArgumentException("$NUMERIC_NOT_SUPPORTED $locator")
                            StringUtils.startsWith(text, END)              ->
                                throw IllegalArgumentException("$END_NOT_SUPPORTED $locator")
                            StringUtils.startsWith(text, END_ANY_CASE)     ->
                                throw IllegalArgumentException("$END_ANY_CASE_NOT_SUPPORTED $locator")

                            StringUtils.startsWith(text, CONTAIN)          ->
                                "//*[contains($elemText,${normalizeXpathText(text.substringAfter(CONTAIN))})]"

                            StringUtils.startsWith(text, CONTAIN_ANY_CASE) ->
                                resolveContainLabelXpath(text.substringAfter(CONTAIN_ANY_CASE))

                            StringUtils.startsWith(text, START)            ->
                                "//*[starts-with($elemText,${normalizeXpathText(text.substringAfter(START))})]"

                            StringUtils.startsWith(text, START_ANY_CASE)   ->
                                "//*[starts-with(${applyLowercaseNormalize("text()")}," +
                                "${lowerCaseNormalize(text.substringAfter(START_ANY_CASE))})]"

                            StringUtils.startsWith(text, LENGTH)           ->
                                "//*[string-length(text()=${text.substringAfter(LENGTH)})]"

                            StringUtils.startsWith(text, EMPTY)            ->
                                "//*[$elemText ${
                                    if (BooleanUtils.toBoolean(StringUtils.substringAfter(text, EMPTY))) "=" else "!="
                                } '']"

                            StringUtils.startsWith(text, BLANK)            ->
                                "//*[text()!='' and $elemText ${
                                    if (BooleanUtils.toBoolean(StringUtils.substringAfter(text, BLANK))) "=" else "!="
                                } '']"

                            StringUtils.startsWith(text, EXACT)            ->
                                "//*[$elemText=${normalizeXpathText(text.substringAfter(EXACT))}]"

                            else                                           ->
                                "//*[$elemText=${normalizeXpathText(text)}]"
                        }
                    } else
                        "//*[$elemText=${normalizeXpathText(text)}]"
                )
            }
        },

        // compound locators to find 1 or more elements described via multiple hierarchical locators
        LAYER("layer") {
            override fun build(locator: String, relative: Boolean): By {
                return LayeredFindBy(TextUtils.groups(
                    StringUtils.trim(StringUtils.substringAfter(locator, "$prefix=")), "{", "}", false))
            }
        };


        abstract fun build(locator: String, relative: Boolean): By

        internal fun extract(locator: String) = StringUtils.substringAfter(locator, "$prefix=")

        companion object {
            fun build(locator: String, relative: Boolean): By {
                if (StringUtils.isBlank(locator)) {
                    CheckUtils.fail("invalid locator: $locator")
                    throw IllegalArgumentException("null/blank locator is not allowed!")
                }

                for (startsWith in PATH_STARTS_WITH) {
                    if (StringUtils.startsWith(locator, startsWith)) {
                        return By.xpath(if (relative) locator else fixBadXpath(locator))
                    }
                }

                return resolvePrefix(locator).build(locator, relative)
            }

            fun resolvePrefix(locator: String): LocatorType {
                val prefix = StringUtils.substringBefore(locator, "=")
                if (StringUtils.isBlank(prefix)) throw IllegalArgumentException("No prefix (prefix=...): $locator")
                return values().first { type -> type.prefix == prefix }
            }
        }
    }

    fun findBy(locator: String) = findBy(locator, false)

    internal fun findBy(locator: String, relative: Boolean) = LocatorType.build(locator, relative)

    /**
     * Formulate a single-level XPATH based on a set of attributes.  The attributes are
     * expressed by a list of name-value pairs that represent the xpath filtering criteria, with the following rules:
     *
     *  1. Order is important! xpath is constructed in the order specified via the `nameValues` list
     *  1. The pairs in the `nameValues` list are separated by pipe character (`| `) or newline
     *  1. The name/value within each pair is separated by equals character (`= `)
     *  1. Use * in value to signify inexact search.  For example, `@class=button*` => filter by class
     * attribute where the class name starts with '`button`'
     *
     * For example `@class=blah*|text()=Save|@id=*save*` yields the following XPATH:
     * // *[ends-with(@class,'blah') and text()='Save' and contains(@id,'save')]
     *
     */
    fun resolveFilteringXPath(nameValues: String): String {
        var nameValuePairs = StringUtils.replace(nameValues, "\r", "|")
        nameValuePairs = StringUtils.trim(StringUtils.replace(nameValuePairs, "\n", "|"))

        // cannot use TextUtils.toMap() since we need to insist on filtering order
        //Map<String, String> pairSet = TextUtils.toMap(nameValuePairs, "=", "|");
        var pairs = StringUtils.split(nameValuePairs, "|")
        // strange corner case; let's just treat input as single name/value pair
        if (pairs.isEmpty()) pairs = arrayOf(nameValuePairs)

        val xpath = StringBuilder("//*[")
        for (pair in pairs) {
            val nameValue = StringUtils.split(pair, "=")
            require(ArrayUtils.getLength(nameValue) == 2) {
                "invalid name=value pair: " + nameValues + ". " +
                "Pairs are separated by newline or pipe character"
            }

            var name = nameValue[0]
            if (!pair.contains("(")) name = StringUtils.prependIfMissing(nameValue[0], "@")

            var value = nameValue[1]
            if (StringUtils.startsWith(value, "*")) {
                // filterValue without starting asterisk
                value = StringUtils.substring(value, 1)
                if (StringUtils.endsWith(value, "*")) {
                    // filterValue without ending asterisk
                    value = StringUtils.substring(value, 0, value.length - 1)
                    xpath.append("contains($name,'$value')")
                } else {
                    xpath.append("starts-with($name,'$value')")
                }
            } else {
                if (StringUtils.endsWith(value, "*")) {
                    // filterValue without ending asterisk
                    value = StringUtils.substring(value, 0, value.length - 1)
                    xpath.append("ends-with($name,'$value')")
                } else {
                    xpath.append("$name='$value'")
                }
            }
            xpath.append(" and ")
        }

        return StringUtils.removeEnd(xpath.toString(), " and ") + "]"
    }

    // internal fun validateLocator(locator: String): String {
    //     if (StringUtils.isBlank(locator)) CheckUtils.fail("invalid locator")
    //     return if (StringUtils.startsWithIgnoreCase(locator, id) && delegator.browser.isRunSafari)
    //         "//*[@id='" + StringUtils.substring(locator, id.length) + "']"
    //     else locator
    // }

    fun assertTextList(locator: String, textList: String, ignoreOrder: String?): StepResult {
        // remove empty items since we can't compare them...
        val matchText = ListUtils.removeAll(collectTextList(locator), listOf(""))
        val expectedTextList = TextUtils.toList(textList, delegator.context.textDelim, true)

        if (CollectionUtils.isEmpty(matchText)) {
            return if (CollectionUtils.isEmpty(expectedTextList))
                StepResult.success("The expected text list is empty and specified locator resolved to nothing")
            else
                StepResult.fail("No matching element found by '$locator'")
        }

        if (BooleanUtils.toBoolean(ignoreOrder)) {
            matchText.sort()
            expectedTextList.sort()
        }

        return delegator.assertEqual(expectedTextList.toString(), matchText.toString())
    }

    private fun collectTextList(locator: String): MutableList<String> {
        val matches = delegator.findElements(locator)
        return if (CollectionUtils.isNotEmpty(matches))
            matches
                .stream()
                .map { element: WebElement -> StringUtils.defaultString(StringUtils.trim(element.text), "") }
                .collect(Collectors.toList())
        else
            LinkedList()
    }

    fun assertContainCount(locator: String, text: String, count: String) = assertCount(locator, text, count, false)

    fun assertTextCount(locator: String, text: String, count: String) = assertCount(locator, text, count, true)

    fun assertElementCount(locator: String, count: String): StepResult {
        val actual = delegator.getElementCount(locator)
        return if (NexialFilterList.isMatchCount(actual, count)) {
            StepResult.success("EXPECTED element count ($actual) found")
        } else {
            StepResult.fail("element count ($actual) DID NOT match expected count ($count)")
        }
    }

    fun assertTextOrder(locator: String?, descending: String?) = assertOrder(locator, TEXT, descending)

    fun assertValueOrder(locator: String?, descending: String?) = assertOrder(locator, VALUE, descending)

    private fun assertOrder(locator: String?, valueType: InnerValueType, descending: String?): StepResult {
        CheckUtils.requiresNotBlank(locator, "invalid locator", locator)

        val matches = delegator.findElements(locator)
        if (CollectionUtils.isEmpty(matches)) return StepResult.fail("No matches found, hence no order can be asserted")

        val expected = mutableListOf<String>()
        val actual = mutableListOf<String>()

        for (elem in matches) {
            val text = try {
                when (valueType) {
                    TEXT  -> elem!!.text
                    VALUE -> elem!!.getAttribute("value")
                }
            } catch (e: Exception) {
                // unable to getText() or getAttribute(value)... assume null
                null
            }

            if (text != null) {
                expected.add(text)
                actual.add(text)
            }
        }

        if (CollectionUtils.isEmpty(expected) && CollectionUtils.isEmpty(actual))
            return StepResult.fail("No data for comparison, hence no order can be asserted")

        // now make 'expected' organized as expected
        expected.sortWith(CASE_INSENSIVE_SORT)
        if (BooleanUtils.toBoolean(descending)) expected.reverse()

        // string comparison to determine if both the actual (displayed) list and sorted list is the same
        return delegator.assertEqual(expected.toString(), actual.toString())
    }

    private fun assertCount(locator: String, text: String, count: String, exact: Boolean): StepResult {
        CheckUtils.requiresNotBlank(text, "invalid text", text)

        val countInt = delegator.toPositiveInt(count, "count")
        val matches = delegator.findElements(locator)
        return if (CollectionUtils.isEmpty(matches)) {
            if (countInt != 0) {
                StepResult.fail("No matching elements found by '$locator'")
            } else {
                StepResult.success("EXPECTS zero matches; found zero matches")
            }
        } else {
            val matches2: MutableList<WebElement?> = ArrayList()
            for (element in matches) {
                val elemText = element!!.text
                if (exact) {
                    if (StringUtils.equals(elemText, text)) matches2.add(element)
                } else {
                    if (StringUtils.contains(elemText, text)) matches2.add(element)
                }
            }

            StepResult(matches2.size == countInt,
                       "EXPECTS " + countInt + " matches; found " + matches2.size + " matches",
                       null)
        }
    }

    companion object {
        // handle cases:
        // //a/b/c
        // .//a/b/c
        // ./a/b/c
        // /a/b/c
        // (/a/b/c)[2]
        // (//a/b/c)[2]
        // (.//a/b/c)[2]
        private val PATH_STARTS_WITH = listOf("/", "./", "(/", "( /", "(./", "( ./")

        @JvmStatic
        fun normalizeXpathText(label: String?): String {
            if (StringUtils.isEmpty(label)) return "''"

            val sections = JRegexUtils.collectGroups(label, "[^'\"]+|['\"]")
            if (CollectionUtils.size(sections) <= 1) return "'$label'"

            return "concat(" +
                   sections.joinToString(separator = ",") { section ->
                       when {
                           StringUtils.equals("'", section)  -> "\"'\""
                           StringUtils.equals("\"", section) -> "'\"'"
                           else                              -> "'$section'"
                       }
                   } +
                   ")"
        }

        @JvmStatic
        private fun lowerCaseNormalize(label: String) =
            "normalize-space(" + normalizeXpathText(StringUtils.trim(StringUtils.lowerCase(label))) + ")"

        @JvmStatic
        fun applyLowercaseNormalize(data: String) =
            "translate(normalize-space($data),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"

        @JvmStatic
        fun resolveLabelXpath(label: String) =
            "//*[" + applyLowercaseNormalize("text()") + "=" + lowerCaseNormalize(label) + "]"

        @JvmStatic
        fun resolveContainLabelXpath(label: String) =
            "//*[contains(" + applyLowercaseNormalize("string(.)") + "," + lowerCaseNormalize(label) + ")]"

        internal fun fixBadXpath(locator: String?): String? {
            if (StringUtils.isBlank(locator)) return locator

            val locatorTrimmed = StringUtils.trim(locator)
            return when {
                StringUtils.startsWith(locatorTrimmed, ".//")   -> StringUtils.substring(locatorTrimmed, 1)
                StringUtils.startsWith(locatorTrimmed, "(.//")  -> "(" + StringUtils.substring(locatorTrimmed, 2)
                StringUtils.startsWith(locatorTrimmed, "( .//") -> "(" + StringUtils.substring(locatorTrimmed, 3)
                else                                            -> locatorTrimmed
            }
        }
    }
}