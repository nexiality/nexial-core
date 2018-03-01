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

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.StepResult;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.plugins.desktop.DesktopConst.LOCATOR_TAB_ITEMS;
import static org.nexial.core.plugins.desktop.ElementType.TAB_ITEM;
import static org.nexial.core.plugins.desktop.ElementType.TabItem;

public class DesktopTabGroup extends DesktopElement {
	public static DesktopTabGroup toInstance(DesktopElement component) {
		DesktopTabGroup instance = new DesktopTabGroup();
		copyTo(component, instance);
		if (MapUtils.isNotEmpty(instance.extra)) {
			if (instance.extra.containsKey("tabs")) {
				List<String> tabNames = TextUtils.toList(instance.extra.get("tabs"), ",", true);
				if (CollectionUtils.isNotEmpty(tabNames)) {
					String parentXpath = instance.getXpath();
					tabNames.forEach(tabName -> {
						DesktopElement tab = new DesktopElement();
						tab.setName(tabName);
						tab.setControlType(TAB_ITEM);
						tab.setElementType(TabItem);
						tab.setXpath(resolveTabXpath(parentXpath, tabName));
						tab.setLabel(tabName);
						tab.setContainer(instance);
						instance.getComponents().put(tabName, tab);
					});
				}
			}
		}

		if (MapUtils.isEmpty(instance.components)) { instance.scanTabs(); }

		return instance;
	}

	public StepResult clickTab(String tabName) {
		if (StringUtils.isBlank(tabName)) { return StepResult.fail("Empty/blank tab name"); }

		if (MapUtils.isEmpty(components)) {
			scanTabs();
			if (MapUtils.isEmpty(components)) { return StepResult.fail("No tab(s) found via " + getXpath()); }
		}

		String msgPrefix = "Tab '" + tabName + "' of Tab Group '" + getLabel() + "'";
		if (!components.containsKey(tabName)) { return StepResult.fail(msgPrefix + " NOT found"); }

		DesktopElement tab = components.get(tabName);
		if (tab == null) {
			tab = resolveTabElement(tabName);
			if (tab == null) { return StepResult.fail("Unable to resolve " + msgPrefix); }
		}

		if (tab.getElement() == null) { tab.refreshElement(); }
		tab.getElement().click();
		return StepResult.success(msgPrefix + " clicked");
	}

	protected static String resolveTabXpath(String parentXpath, String tabName) {
		return parentXpath + StringUtils.replace(DesktopConst.LOCATOR_TAB_ITEM, "{tabName}", tabName);
	}

	protected DesktopElement resolveTabElement(String tabName) {
		if (StringUtils.isBlank(getXpath())) { throw new IllegalArgumentException("XPATH is null"); }

		String xpath = resolveTabXpath(getXpath(), tabName);
		String msgDetail = "Tab '" + tabName + "' of Tab Group '" + getLabel() + "' via " + xpath;

		try {
			WebElement tabItem = driver.findElement(By.xpath(xpath));
			if (tabItem == null) {
				DesktopConst.debug(msgDetail + " cannot be found.");
				return null;
			}

			DesktopElement tab = new DesktopElement(tabItem, this);
			components.put(tab.getName(), tab);
			return tab;
		} catch (NoSuchElementException e) {
			ConsoleUtils.error("Error when resolving " + msgDetail + ": " + e.getMessage());
			return null;
		}
	}

	protected void scanTabs() {
		if (StringUtils.isBlank(getXpath())) { throw new IllegalArgumentException("XPATH is null"); }

		try {
			WebElement tabGroupElement = driver.findElement(By.xpath(getXpath()));

			String msgDetail = "Tab Group '" + getLabel() + "' via " + getXpath();
			if (tabGroupElement == null) {
				ConsoleUtils.error("Unable to resolve " + msgDetail + ": NULL");
				return;
			}

			List<WebElement> tabItems = tabGroupElement.findElements(By.xpath(LOCATOR_TAB_ITEMS));
			if (CollectionUtils.isEmpty(tabItems)) {
				ConsoleUtils.error("No tab items found in " + msgDetail);
				return;
			}

			tabItems.forEach(tabItem -> {
				DesktopElement tab = new DesktopElement(tabItem, this);
				components.put(tab.getName(), tab);
			});
		} catch (NoSuchElementException e) {
			ConsoleUtils.error("Unable to resolve Tab Group element via '" + getXpath() + "': " + e.getMessage());
		}
	}
}
