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

package org.nexial.core.plugins.desktop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.nexial.core.plugins.desktop.ElementType.*;

/**
 * represents a dialog box - which means it should have a titlebar, some text and one or more buttons
 */
public class DesktopDialog {
    private WebElement element;
    private Map<String, WebElement> buttons = new LinkedHashMap<>();
    private String text;
    private String title;

    public DesktopDialog(WebElement element) {
        this.element = element;
        inspect();
    }

    public WebElement getElement() { return element; }

    public Map<String, WebElement> getButtons() { return buttons; }

    public WebElement getButton(String name) { return buttons.get(name); }

    public String getText() { return text; }

    public String getTitle() { return title; }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                   .append("title", title)
                   .append("text", text)
                   .append("buttons", buttons)
                   .toString();
    }

    private void inspect() {
        List<WebElement> children = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(children)) {
            throw new IllegalArgumentException("Dialog without child elements: " + element);
        }

        StringBuilder content = new StringBuilder();
        for (WebElement child : children) {
            String controlType = child.getAttribute("ControlType");
            if (StringUtils.isBlank(controlType)) {
                DesktopConst.debug("Null/empty controlType found", child);
                continue;
            }

            if (StringUtils.equals(controlType, TITLE_BAR)) {
                title = child.getAttribute("Name");
                continue;
            }

            if (StringUtils.equals(controlType, BUTTON) || StringUtils.equals(controlType, SPLIT_BUTTON)) {
                buttons.put(child.getAttribute("Name"), child);
                continue;
            }

            if (StringUtils.equals(controlType, ElementType.LABEL)) {
                content.append(child.getAttribute("Name")).append("\n");
                continue;
            }
        }

        text = StringUtils.trim(content.toString());
    }
}
