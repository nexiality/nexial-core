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

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import static org.nexial.core.NexialConst.PREFIX_REGEX;

public class RegexAwareSelect extends Select {
    private WebElement element;

    public RegexAwareSelect(WebElement element) {
        super(element);
        this.element = element;
    }

    @Override
    public void selectByVisibleText(String text) {
        if (StringUtils.startsWith(text, PREFIX_REGEX)) {
            List<WebElement> options = element.findElements(By.tagName("option"));
            if (CollectionUtils.isEmpty(options)) {
                ConsoleUtils.log("target SELECT element contains NO child OPTION elements");
                return;
            }

            String regex = StringUtils.removeStart(text, PREFIX_REGEX);
            if (StringUtils.isNotBlank(regex)) {
                boolean matched = false;
                for (WebElement option : options) {
                    if (RegexUtils.match(option.getText(), regex)) {
                        setSelected(option, true);
                        if (!isMultiple()) { return; }
                        matched = true;
                    }
                }

                if (matched) { return; }

                ConsoleUtils.log("No text from target SELECT element matches to " + text + ", retrying as normal text");
            }
        }

        super.selectByVisibleText(text);
    }

    /**
     * Deselect all options that display text matching the argument. That is, when given "Bar" this
     * would deselect an option like:
     *
     * &lt;option value="foo"&gt;Bar&lt;/option&gt;
     *
     * @param text The visible text to match against
     * @throws NoSuchElementException        If no matching option elements are found
     * @throws UnsupportedOperationException If the SELECT does not support multiple selections
     */
    @Override
    public void deselectByVisibleText(String text) {
        if (!isMultiple()) {throw new UnsupportedOperationException("You may only deselect options of a multi-select");}

        if (StringUtils.startsWith(text, PREFIX_REGEX)) {
            List<WebElement> options = element.findElements(By.tagName("option"));
            if (CollectionUtils.isEmpty(options)) {
                ConsoleUtils.log("target SELECT element contains NO child OPTION elements");
                return;
            }

            String regex = StringUtils.removeStart(text, PREFIX_REGEX);
            if (StringUtils.isNotBlank(regex)) {
                boolean matched = false;
                for (WebElement option : options) {
                    if (RegexUtils.match(option.getText(), regex)) {
                        setSelected(option, false);
                        matched = true;
                    }
                }

                if (matched) { return; }

                ConsoleUtils.log("No text from target SELECT element matches to " + text + ", retrying as normal text");
            }
        }

        super.deselectByVisibleText(text);
    }

    /**
     * Select or deselect specified option
     *
     * @param option The option which state needs to be changed
     * @param select Indicates whether the option needs to be selected (true) or deselected (false)
     */
    private void setSelected(WebElement option, boolean select) {
        boolean isSelected = option.isSelected();
        if ((!isSelected && select) || (isSelected && !select)) { option.click(); }
    }

}
