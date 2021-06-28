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
package org.nexial.core.plugins.web

import org.apache.commons.collections4.CollectionUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.Select
import java.util.function.Consumer
import java.util.stream.Collectors

class RegexAwareSelect(private val element: WebElement) : Select(element) {
    override fun selectByVisibleText(text: String) = handleViaPolyMatch(text, true)

    /**
     * Deselect all options that display text matching the argument. That is, when given "Bar" this
     * would deselect an option like:
     *
     *
     * &lt;option value="foo"&gt;Bar&lt;/option&gt;
     *
     * @param text The visible text to match against
     * @throws NoSuchElementException        If no matching option elements are found
     * @throws UnsupportedOperationException If the SELECT does not support multiple selections
     */
    override fun deselectByVisibleText(text: String) = handleViaPolyMatch(text, false)

    private fun handleViaPolyMatch(text: String, select: Boolean) {
        // deselect only works when we are dealing with multi-enabled <SELECT> element
        if (!select && !isMultiple)
            throw UnsupportedOperationException("You may only deselect options of a multi-select")

        val options = element.findElements<WebElement>(By.tagName("option"))
        if (CollectionUtils.isEmpty(options)) {
            ConsoleUtils.log("target SELECT element contains NO child OPTION elements")
            return
        }

        val matches = options.stream()
            .filter { option: WebElement? -> TextUtils.polyMatch(option!!.text, text) }
            .collect(Collectors.toList())
        if (CollectionUtils.isNotEmpty(matches)) {
            if (isMultiple) {
                matches.forEach(Consumer { option: WebElement? -> setSelected(option, select) })
            } else {
                setSelected(matches[0], select)
            }
            return
        }
        ConsoleUtils.log("No text from target SELECT element matches to $text, retrying as normal text")
        if (select) {
            super.selectByVisibleText(text)
        } else {
            super.deselectByVisibleText(text)
        }
    }

    /**
     * Select or deselect specified option
     *
     * @param option The option which state needs to be changed
     * @param select Indicates whether the option needs to be selected (true) or deselected (false)
     */
    private fun setSelected(option: WebElement?, select: Boolean) {
        val isSelected = option!!.isSelected
        if (!isSelected && select || isSelected && !select) option.click()
    }
}