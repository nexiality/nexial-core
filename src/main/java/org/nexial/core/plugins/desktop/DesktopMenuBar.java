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
 *
 */

package org.nexial.core.plugins.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import static org.nexial.core.plugins.desktop.DesktopConst.FORM_LAYOUT_LEFT_TO_RIGHT;
import static org.nexial.core.plugins.desktop.DesktopConst.NESTED_CONTAINER_SEP;
import static org.nexial.core.plugins.desktop.DesktopUtils.isExpandCollapsePatternAvailable;
import static org.nexial.core.plugins.desktop.ElementType.*;

/**
 * represents an UI element of ControlType.MenuBar.
 */
public class DesktopMenuBar extends DesktopElement {

    protected DesktopMenuBar() { }

    public static DesktopMenuBar toInstance(DesktopElement component) {
        DesktopMenuBar instance = new DesktopMenuBar();
        copyTo(component, instance);
        instance.setElementType(MenuBar);
        return instance;
    }

    @Override
    public void setElement(WebElement element) {
        super.setElement(element);
        if (!StringUtils.equals(controlType, ElementType.MENU_BAR)) {
            throw new IllegalArgumentException("ControlType must be " + ElementType.MENU_BAR + " not " + controlType);
        }

        setElementType(MenuBar);
        if (getLayout() == null) {
            setLayout(StringUtils.isBlank(layoutHint) ? FORM_LAYOUT_LEFT_TO_RIGHT : FormLayout.toLayout(layoutHint));
        }
        if (getLabel() == null) { setLabel(getAutomationId()); }
    }

    public StepResult click(String... menuItems) {
        return click(this, TextUtils.toString(menuItems, NESTED_CONTAINER_SEP, "", ""));
    }

    public StepResult click(String menu) { return click(this, menu); }

    public boolean isEnabled(String... menuItems) {
        return isEnabled(this, TextUtils.toString(menuItems, NESTED_CONTAINER_SEP, "", ""));
    }

    protected boolean isEnabled(DesktopElement container, String menu) {
        if (StringUtils.isBlank(menu)) {
            ConsoleUtils.error("FAILED to navigate menu due to BLANK menu item");
            return false;
        }

        String current;
        String next;
        if (StringUtils.contains(menu, NESTED_CONTAINER_SEP)) {
            current = StringUtils.substringBefore(menu, NESTED_CONTAINER_SEP);
            next = StringUtils.substringAfter(menu, NESTED_CONTAINER_SEP);
        } else {
            current = menu;
            next = null;
        }

        DesktopElement menuItem = container.getComponent(current);
        if (menuItem == null) {
            ConsoleUtils.error("FAILED to navigate menu: item '" + menu + "' does not exist");
            return false;
        }

        menuItem.refreshElement();
        if (!menuItem.getElement().isEnabled()) {
            ConsoleUtils.error("FAILED to navigate menu: item '" + menu + "' not enabled");
            return false;
        }

        return next == null || isEnabled(menuItem, next);
    }

    protected StepResult click(DesktopElement container, String menu) {
        if (StringUtils.isBlank(menu)) { return StepResult.fail("FAILED to click menu due to BLANK menu item"); }

        String current;
        String next;
        if (StringUtils.contains(menu, NESTED_CONTAINER_SEP)) {
            current = StringUtils.substringBefore(menu, NESTED_CONTAINER_SEP);
            next = StringUtils.substringAfter(menu, NESTED_CONTAINER_SEP);
        } else {
            current = menu;
            next = null;
        }

        DesktopElement menuItem = container.getComponent(current);
        if (menuItem == null) { return StepResult.fail("FAILED to click menu: item '" + menu + "' does not exist"); }

        menuItem.refreshElement();
        if (!menuItem.getElement().isEnabled()) {
            return StepResult.fail("FAILED to click menu: item '" + menu + "' not enabled");
        }

        menuItem.getElement().click();
        return next == null ? StepResult.success("Clicked on menu path " + menu) : click(menuItem, next);
    }

    @Override
    protected void inspect() {
        // only expects MenuItem and Menu
        List<DesktopElement> menus = inspectChildMenuItems(this);
        if (CollectionUtils.isEmpty(menus)) { return; }
        addMenus(menus, this.components);
    }

    protected void addMenus(List<DesktopElement> menus, Map<String, DesktopElement> components) {
        menus.forEach(menu -> {
            overrideLabel(menu);
            if (!useAutomationIdForXpathGeneration(menu.getAutomationId())) { menu.setAutomationId(null); }
            if (!useNameForXpathGeneration(menu.getName(), menu.getAutomationId())) { menu.setName(null); }
            components.put(menu.getLabel(), menu);
        });
    }

    protected List<DesktopElement> inspectChildMenuItems(DesktopElement menuItem) {
        List<DesktopElement> children = new ArrayList<>();

        if (menuItem == null) { return children; }

        String msgPrefix = menuItem.getLabel() + " -> ";

        WebElement element = menuItem.getElement();

        List<WebElement> childElements = element.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(childElements)) { return children; }

        for (WebElement childElement : childElements) {
            String childControlType = childElement.getAttribute("ControlType");
            if (StringUtils.isBlank(childControlType)) { continue; }

            if (StringUtils.equals(childControlType, SEPARATOR)) { continue; }

            if (StringUtils.equals(childControlType, MENU)) {
                // go down one level deeper
                List<WebElement> innerChildElements = childElement.findElements(By.xpath("*"));
                if (CollectionUtils.isEmpty(innerChildElements)) { continue; }

                innerChildElements.forEach(innerChildElement -> {
                    String innerChildControlType = innerChildElement.getAttribute("ControlType");
                    if (!StringUtils.equals(innerChildControlType, SEPARATOR)) {
                        if (StringUtils.equals(innerChildControlType, MENU_ITEM)) {
                            addChildMenuItems(menuItem, innerChildElement, children, true);
                        } else {
                            DesktopConst.debug(msgPrefix + "found UNSUPPORTED child menu inside " + MENU,
                                               innerChildElement);
                        }
                    }
                });

                continue;
            }

            if (StringUtils.equals(childControlType, MENU_ITEM)) {
                addChildMenuItems(menuItem, childElement, children, false);
            }
        }

        return children;
    }

    protected void addChildMenuItems(DesktopElement container,
                                     WebElement menuItem,
                                     List<DesktopElement> children,
                                     boolean fromMenu) {
        DesktopElement childMenuItem = new DesktopElement();
        childMenuItem.inheritXPathGenerationStrategy(container);
        childMenuItem.inheritLayout(container);
        childMenuItem.setElement(menuItem);
        childMenuItem.setContainer(container);

        if (fromMenu) {
            // one-off xpath manipulation.. ugly ahead
            // we need to inject "ControlType.Menu" into the xpath
            String xpath = childMenuItem.getXpath();
            xpath = StringUtils.substringBeforeLast(xpath, "/*") +
                    "/*[@ControlType='" + MENU + "']/*" +
                    StringUtils.substringAfterLast(xpath, "/*");
            childMenuItem.setXpath(xpath);
        }
        children.add(childMenuItem);

        if (hasSubMenu(menuItem)) {
            new Actions(container.getDriver()).moveToElement(menuItem).click().perform();
            // click to activate
            // menuItem.click();

            List<DesktopElement> subMenuItems = inspectChildMenuItems(childMenuItem);
            if (CollectionUtils.isNotEmpty(subMenuItems)) {
                Map<String, DesktopElement> components = childMenuItem.getComponents();
                addMenus(subMenuItems, components);
            }

            // click to deactive/release
            menuItem.click();
        }
    }

    protected boolean hasSubMenu(WebElement menu) { return isExpandCollapsePatternAvailable(menu); }
}
