/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nexial.core.plugins.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.JRegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.CheckUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.OutputFileUtils.CASE_INSENSIVE_SORT;

class LocatorHelper {
    // handle cases:
    // //a/b/c
    // .//a/b/c
    // ./a/b/c
    // /a/b/c
    // (/a/b/c)[2]
    // (//a/b/c)[2]
    // (.//a/b/c)[2]
    private static final List<String> PATH_STARTS_WITH = Arrays.asList("/", "./", "(/", "( /", "(./", "( ./");
    private WebCommand delegator;

    private enum InnerValueType {TEXT, VALUE}

    LocatorHelper(WebCommand delegator) { this.delegator = delegator; }

    // todo: use reflection to simplify code; nested if's are ugly, man
    @NotNull
    protected By findBy(String locator) {
        return findBy(locator, false);
    }

    @NotNull
    protected By findBy(String locator, boolean allowRelative) {
        if (StringUtils.isBlank(locator)) {
            CheckUtils.fail("invalid locator:" + locator);
            throw new IllegalArgumentException("null/blank locator is not allowed!");
        }

        locator = validateLocator(locator);
        if (StringUtils.startsWith(locator, "id=")) { return By.id(StringUtils.substring(locator, "id=".length())); }

        if (StringUtils.startsWith(locator, "class=")) {
            return By.className(StringUtils.substring(locator, "class=".length()));
        }

        if (StringUtils.startsWith(locator, "name=")) {
            return By.name(StringUtils.substring(locator, "name=".length()));
        }

        if (StringUtils.startsWith(locator, "css=")) {
            return By.cssSelector(StringUtils.substring(locator, "css=".length()));
        }

        if (StringUtils.startsWith(locator, "link=")) {
            return By.linkText(StringUtils.substring(locator, "link=".length()));
        }

        if (StringUtils.startsWith(locator, "partial=")) {
            return By.partialLinkText(StringUtils.substring(locator, "partial=".length()));
        }

        if (StringUtils.startsWith(locator, "partialLinkText=")) {
            return By.partialLinkText(StringUtils.substring(locator, "partialLinkText=".length()));
        }

        if (StringUtils.startsWith(locator, "xpath=")) {
            String xpath = StringUtils.substring(locator, "xpath=".length());
            if (allowRelative) { return By.xpath(xpath); }
            return By.xpath(fixBadXpath(xpath));
        }

        for (String startsWith : PATH_STARTS_WITH) {
            if (allowRelative) { return By.xpath(locator); }
            if (StringUtils.startsWith(locator, startsWith)) { return By.xpath(fixBadXpath(locator)); }
        }

        if (StringUtils.startsWith(locator, "tag=")) {
            return By.tagName(StringUtils.substring(locator, "tag=".length()));
        }

        return By.tagName(locator);
    }

    protected String resolveLabelXpath(String label) {
        return "//*[normalize-space(text())=normalize-space(" + normalizeXpathText(label) + ")]";
    }

    protected String resolveContainLabelXpath(String label) {
        return "//*[contains(normalize-space(string(.)), normalize-space(" + normalizeXpathText(label) + "))]";
    }

    protected String normalizeXpathText(String label) {
        if (StringUtils.isEmpty(label)) { return "''"; }

        List<String> sections = JRegexUtils.collectGroups(label, "[^'\"]+|['\"]");
        if (CollectionUtils.isEmpty(sections)) { return "'" + label + "'"; }
        if (sections.size() == 1) { return "'" + label + "'"; }

        List<String> treated = new ArrayList<>();
        for (String section : sections) {
            if (StringUtils.equals("'", section)) {
                treated.add("\"'\"");
            } else if (StringUtils.equals("\"", section)) {
                treated.add("'\"'");
            } else {
                treated.add("'" + section + "'");
            }
        }

        return "concat(" + StringUtils.join(treated, ",") + ")";
    }

    /**
     * Formulate a single-level XPATH based on a set of attributes.  The attributes are
     * expressed by a list of name-value pairs that represent the xpath filtering criteria, with the following rules:
     * <ol>
     * <li>Order is important! xpath is constructed in the order specified via the {@code nameValues} list</li>
     * <li>The pairs in the {@code nameValues} list are separated by pipe character ({@code | }) or newline</li>
     * <li>The name/value within each pair is separated by equals character ({@code = })</li>
     * <li>Use * in value to signify inexact search.  For example, {@code @class=button*} => filter by class
     * attribute where the class name starts with '{@code button}'</li>
     * </ol>
     * <p/>
     * For example {@code @class=blah*|text()=Save|@id=*save*} yields the following XPATH:
     * //*[ends-with(@class,'blah') and text()='Save' and contains(@id,'save')]
     * </pre>
     */
    protected String resolveFilteringXPath(String nameValues) {
        String nameValuePairs = StringUtils.replace(nameValues, "\r", "|");
        nameValuePairs = StringUtils.trim(StringUtils.replace(nameValuePairs, "\n", "|"));

        // cannot use TextUtils.toMap() since we need to insist on filtering order
        //Map<String, String> pairSet = TextUtils.toMap(nameValuePairs, "=", "|");

        String[] pairs = StringUtils.split(nameValuePairs, "|");
        // strange corner case.. let's just treat input as single name/value pair
        if (pairs.length == 0) { pairs = new String[]{nameValuePairs}; }
        StringBuilder xpath = new StringBuilder("//*[");
        for (String pair : pairs) {
            String[] nameValue = StringUtils.split(pair, "=");
            if (ArrayUtils.getLength(nameValue) != 2) {
                throw new IllegalArgumentException("invalid name=value pair: " + nameValues + ". " +
                                                   "Pairs are separated by newline or pipe character");
            }

            String name = nameValue[0];
            if (!pair.contains("(")) {name = StringUtils.prependIfMissing(nameValue[0], "@");}
            String value = nameValue[1];
            if (StringUtils.startsWith(value, "*")) {
                // filterValue without starting asterisk
                value = StringUtils.substring(value, 1);

                if (StringUtils.endsWith(value, "*")) {
                    // filterValue without ending asterisk
                    value = StringUtils.substring(value, 0, value.length() - 1);
                    xpath.append("contains(").append(name).append(",'").append(value).append("')");
                } else {
                    xpath.append("starts-with(").append(name).append(",'").append(value).append("')");
                }
            } else {
                if (StringUtils.endsWith(value, "*")) {
                    // filterValue without ending asterisk
                    value = StringUtils.substring(value, 0, value.length() - 1);
                    xpath.append("ends-with(").append(name).append(",'").append(value).append("')");
                } else {
                    xpath.append(name).append("='").append(value).append("'");
                }
            }

            xpath.append(" and ");
        }
        return StringUtils.removeEnd(xpath.toString(), " and ") + "]";
    }

    protected String validateLocator(String locator) {
        if (StringUtils.isBlank(locator)) { CheckUtils.fail("invalid locator"); }

        if (StringUtils.startsWithIgnoreCase(locator, "id=") && delegator.browser.isRunSafari()) {
            locator = "//*[@id='" + StringUtils.substring(locator, "id=".length()) + "']";
        }
        return locator;
    }

    protected StepResult assertTextList(String locator, String textList, String ignoreOrder) {
        List<String> matchText = collectTextList(locator);
        if (CollectionUtils.isEmpty(matchText)) {
            if (StringUtils.isEmpty(textList)) {
                return StepResult.success("The expected text list is empty and specified locator resolved to nothing");
            } else {
                return StepResult.fail("No matching element found by '" + locator + "'");
            }
        }

        List<String> expectedTextList = TextUtils.toList(textList, delegator.getContext().getTextDelim(), true);
        if (CollectionUtils.isEmpty(expectedTextList)) {
            return StepResult.fail("expected text list cannot be parsed: " + textList);
        }

        // remove empty items since we can't compare them..
        matchText = ListUtils.removeAll(matchText, Collections.singletonList(""));

        if (BooleanUtils.toBoolean(ignoreOrder)) {
            Collections.sort(matchText);
            Collections.sort(expectedTextList);
        }

        return delegator.assertEqual(expectedTextList.toString(), matchText.toString());
    }

    @NotNull
    protected List<String> collectTextList(String locator) {
        List<WebElement> matches = delegator.findElements(locator);
        return CollectionUtils.isNotEmpty(matches) ?
               matches.stream().map(element -> StringUtils.trim(element.getText())).collect(Collectors.toList()) :
               new LinkedList<>();
    }

    @NotNull
    protected StepResult assertElementNotPresent(String locator) {
        return new StepResult(!delegator.isElementPresent(locator));
    }

    @NotNull
    protected StepResult assertContainCount(String locator, String text, String count) {
        return assertCount(locator, text, count, false);
    }

    @NotNull
    protected StepResult assertTextCount(String locator, String text, String count) {
        return assertCount(locator, text, count, true);
    }

    protected StepResult assertElementCount(String locator, String count) {
        locator = validateLocator(locator);
        int expected = delegator.toPositiveInt(count, "count");
        int actual = delegator.getElementCount(locator);
        if (expected == actual) {
            return StepResult.success("EXPECTED element count found");
        } else {
            return StepResult.fail("element count (" + actual + ") DID NOT match expected count (" + expected + ")");
        }
    }

    protected StepResult assertTextOrder(String locator, String descending) {
        return assertOrder(locator, InnerValueType.TEXT, descending);
    }

    protected StepResult assertValueOrder(String locator, String descending) {
        return assertOrder(locator, InnerValueType.VALUE, descending);
    }

    protected String fixBadXpath(String locator) {
        if (StringUtils.isBlank(locator)) { return locator; }

        locator = StringUtils.trim(locator);
        if (StringUtils.startsWith(locator, ".//")) { return StringUtils.substring(locator, 1); }
        if (StringUtils.startsWith(locator, "(.//")) { return "(" + StringUtils.substring(locator, 2); }
        if (StringUtils.startsWith(locator, "( .//")) { return "(" + StringUtils.substring(locator, 3); }
        return locator;
    }

    protected StepResult assertOrder(String locator, InnerValueType valueType, String descending) {
        requiresNotBlank(locator, "invalid locator", locator);

        List<WebElement> matches = delegator.findElements(locator);
        if (CollectionUtils.isEmpty(matches)) {
            return StepResult.fail("No matches found, hence no order can be asserted");
        }

        List<String> expected = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        for (WebElement elem : matches) {
            String text = null;
            try {
                text = valueType == InnerValueType.TEXT ? elem.getText() :
                       valueType == InnerValueType.VALUE ? elem.getAttribute("value") : null;
            } catch (Exception e) {
                // unable to getText() or getAttribute(value)... assume null
            }

            if (text != null) {
                expected.add(text);
                actual.add(text);
            }
        }

        if (CollectionUtils.isEmpty(expected) && CollectionUtils.isEmpty(actual)) {
            return StepResult.fail("No data for cmparison, hence no order can be asserted");
        }

        // now make 'expected' organized as expected
        expected.sort(CASE_INSENSIVE_SORT);
        if (BooleanUtils.toBoolean(descending)) { Collections.reverse(expected); }

        // string comparison to determine if both the actual (displayed) list and sorted list is the same
        return delegator.assertEqual(expected.toString(), actual.toString());
    }

    @NotNull
    private StepResult assertCount(String locator, String text, String count, boolean exact) {
        requiresNotBlank(text, "invalid text", text);
        int countInt = delegator.toPositiveInt(count, "count");
        List<WebElement> matches = delegator.findElements(locator);
        if (CollectionUtils.isEmpty(matches)) {
            if (countInt != 0) {
                return StepResult.fail("No matching elements found by '" + locator + "'");
            } else {
                return StepResult.success("EXPECTS zero matches; found zero matches");
            }
        }

        List<WebElement> matches2 = new ArrayList<>();
        for (WebElement element : matches) {
            String elemText = element.getText();
            if (exact) {
                if (StringUtils.equals(elemText, text)) { matches2.add(element); }
            } else {
                if (StringUtils.contains(elemText, text)) { matches2.add(element); }
            }
        }

        return new StepResult(matches2.size() == countInt,
                              "EXPECTS " + countInt + " matches; found " + matches2.size() + " matches",
                              null);
    }

}
