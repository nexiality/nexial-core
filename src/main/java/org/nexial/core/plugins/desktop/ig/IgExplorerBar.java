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

package org.nexial.core.plugins.desktop.ig;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.desktop.BoundingRectangle;
import org.nexial.core.plugins.desktop.ThirdPartyComponent;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.plugins.desktop.DesktopConst.*;
import static org.nexial.core.utils.CheckUtils.requires;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

public class IgExplorerBar extends ThirdPartyComponent {
    protected int offsetX;
    protected int offsetY;
    protected int categoryHeight;
    protected int itemHeight;
    protected boolean singleTabMode = true;
    protected Map<String, String> xpaths = new HashMap<>();
    protected List<IgExplorerBarGroup> groups = new ArrayList<>();
    protected transient Map<String, IgExplorerBarGroup> mapping = new HashMap<>();

    public static class IgExplorerBarItem {
        protected String name;
        protected String shortcut;

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String getShortcut() { return shortcut;}

        public void setShortcut(String shortcut) { this.shortcut = shortcut;}

        @Override
        public String toString() { return name + "," + shortcut; }
    }

    public static class IgExplorerBarGroup {
        protected String name;
        protected String shortcut;
        protected List<IgExplorerBarItem> items;

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public List<IgExplorerBarItem> getItems() { return items; }

        public void setItems(List<IgExplorerBarItem> items) { this.items = items; }

        public String getShortcut() { return shortcut;}

        public void setShortcut(String shortcut) { this.shortcut = shortcut;}

        @Override
        public String toString() {
            return new ToStringBuilder(this).append("name", name)
                                            .append("shortcut", shortcut)
                                            .append("items", items)
                                            .toString();
        }
    }

    public int getOffsetX() { return offsetX; }

    public void setOffsetX(int offsetX) { this.offsetX = offsetX; }

    public int getOffsetY() { return offsetY; }

    public void setOffsetY(int offsetY) { this.offsetY = offsetY; }

    public int getCategoryHeight() { return categoryHeight; }

    public void setCategoryHeight(int categoryHeight) { this.categoryHeight = categoryHeight; }

    public int getItemHeight() { return itemHeight; }

    public void setItemHeight(int itemHeight) { this.itemHeight = itemHeight; }

    public boolean isSingleTabMode() { return singleTabMode; }

    public void setSingleTabMode(boolean singleTabMode) { this.singleTabMode = singleTabMode; }

    public List<IgExplorerBarGroup> getGroups() { return groups; }

    public void setGroups(List<IgExplorerBarGroup> groups) { this.groups = groups; }

    public Map<String, IgExplorerBarGroup> getMapping() { return mapping; }

    public void setMapping(Map<String, IgExplorerBarGroup> mapping) { this.mapping = mapping; }

    public void clickFirstSectionHeader() {

        expand();
        WebElement element = component.getElement();
        Pair<Integer, Integer> xyOffset = findRightMostOffset(element);
        int width = xyOffset.getLeft();

        new Actions(component.getDriver()).moveToElement(element, width / 2, xyOffset.getRight())
                                          .click()
                                          .build()
                                          .perform();

    }

    public StepResult click(String group, String item) {
        // find current explorer bar's dimension (bounding rectangle) so that we can calculate the
        // correct y-offset of the target section

        String groupShortcut = null;
        String itemShortcut = null;

        int groupIndex = -1;
        IgExplorerBarGroup targetGroup = null;
        for (int i = 0; i < groups.size(); i++) {
            IgExplorerBarGroup ebGroup = groups.get(i);
            if (StringUtils.equalsIgnoreCase(ebGroup.getName(), group)) {
                targetGroup = ebGroup;
                groupShortcut = targetGroup.getShortcut();
                groupIndex = i;
                break;
            }
        }
        if (groupIndex == -1) { return StepResult.fail("Group '" + group + "' is not found in Explorer Bar"); }

        List<IgExplorerBarItem> items = targetGroup.getItems();
        int itemIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            IgExplorerBarItem ebItem = items.get(i);
            if (StringUtils.equalsIgnoreCase(ebItem.getName(), item)) {
                itemShortcut = ebItem.getShortcut();
                itemIndex = i;
                break;
            }
        }
        if (itemIndex == -1) {
            return StepResult.fail("Item '" + item + "' not found in Explorer Bar group '" + group + "'");
        }

        if (singleTabMode) { closeOpenTabs(); }

        expand();

        if (StringUtils.isNotBlank(groupShortcut) && StringUtils.isNotBlank(itemShortcut)) {
            getDriver().executeScript(SCRIPT_PREFIX_SHORTCUT +
                                      TEXT_INPUT_PREFIX + groupShortcut + itemShortcut + TEXT_INPUT_POSTFIX,
                                      getComponent().getElement());
            waitFor(config.getDefaultWaitMs());
            return StepResult.success("Explorer Bar (" + group + "," + item + ") clicked via shortcut");
        }

        // the y-offset = [
        //  explorerBar.height - (
        //      "more buttons".height +
        //      sectionButton.height / 2 +
        //      sectionButton.height * sectionButton.reverseIndex
        //  )
        // ]
        // - note that section are always gathered at the bottom of the explorerBar
        int groupCount = CollectionUtils.size(groups);
        BoundingRectangle bound = BoundingRectangle.newInstance(component);
        int explorerBarHeight = bound != null ? bound.getHeight() : 0;
        int reverseGroupPosition = groupCount - groupIndex;
        int yOffset = explorerBarHeight -
                      (categoryHeight * reverseGroupPosition + (7 - reverseGroupPosition) + (categoryHeight / 2));

        ConsoleUtils.log("Click on Explorer Bar group " + group);
        WebElement element = component.getElement();
        clickOffset(element, offsetX + "", yOffset + "");
        ConsoleUtils.log("Clicked offset (" + offsetX + "," + yOffset + ") from '" + group + "'");

        // click on item via its resolved offsets
        ConsoleUtils.log("Opening new tab from Explorer Bar " + item);
        yOffset = offsetY + itemHeight / 2 + itemHeight * itemIndex;
        clickOffset(element, offsetX + "", yOffset + "");
        ConsoleUtils.log("Clicked offset (" + offsetX + "," + yOffset + ") from item '" + item + "'");

        waitFor(config.getDefaultWaitMs());
        return StepResult.success("Explorer Bar (" + group + "," + item + ") clicked");
    }

    public StepResult collapse() { return toggleExplorerBar(false); }

    public StepResult expand() { return toggleExplorerBar(true); }

    @Override
    public String toString() { return GSON.toJson(this); }

    public boolean isCollapsed() {
        WebElement element = component.getElement();
        Pair<Integer, Integer> xyOffset = findRightMostOffset(element);
        return xyOffset.getLeft() <= 40;
    }

    protected void closeOpenTabs() {
        WebElement tabContainer = findElement(xpaths.get(TAB_CONTAINER));
        if (tabContainer == null) { return; }

        By findBy = By.xpath(xpaths.get(TAB_WITH_CLOSE_BUTTON));
        List<WebElement> closableTabs;

        do {
            closableTabs = tabContainer.findElements(findBy);
            if (CollectionUtils.isEmpty(closableTabs)) { break; }

            closableTabs.forEach(tab -> {
                if (tab != null) {
                    ConsoleUtils.log("\tclosing tab via '" + tab.getAttribute("Name") + "'");
                    BoundingRectangle rect = BoundingRectangle.newInstance(tab);
                    if (rect != null) {
                        new Actions(getDriver()).moveToElement(tab, rect.getWidth() - 15, rect.getHeight() / 2)
                                                .click()
                                                .build()
                                                .perform();
                    } else {
                        getDriver().executeScript(SCRIPT_PREFIX_SHORTCUT +
                                                  SHORTCUT_PREFIX + "CTRL-F4" + SHORTCUT_POSTFIX,
                                                  tab);
                    }
                }
            });

            closableTabs = tabContainer.findElements(findBy);
        } while (CollectionUtils.isNotEmpty(closableTabs));
    }

    protected StepResult toggleExplorerBar(boolean expand) {
        String msgPrefix = "Explorer Bar ";

        WebElement element = component.getElement();
        Pair<Integer, Integer> xyOffset = findRightMostOffset(element);
        int width = xyOffset.getLeft();
        if (expand) {
            if (width > 40) { return StepResult.success(msgPrefix + "already expanded"); }
        } else {
            if (width < 40) { return StepResult.success(msgPrefix + "already collapsed"); }
        }

        new Actions(component.getDriver()).moveToElement(element, width, xyOffset.getRight())
                                          .click()
                                          .build()
                                          .perform();
        return StepResult.success(msgPrefix + (expand ? "expanded" : "collapsed"));
    }

    protected Pair<Integer, Integer> findRightMostOffset(WebElement element) {
        requiresNotNull(element, "Null element found");

        BoundingRectangle boundingRectangle = BoundingRectangle.newInstance(element);
        int width = boundingRectangle.getWidth();
        requires(width > 11, "Invalid width found via 'BoundingRectangle'", width);

        return new ImmutablePair<>(width - 10, 13);
    }
}
