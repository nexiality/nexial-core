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

package org.nexial.core.plugins.desktop.ig;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.desktop.ThirdPartyComponent;
import org.nexial.core.utils.ConsoleUtils;

import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.builder.ToStringStyle.JSON_STYLE;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.NexialConst.NL;

public class IgRibbon extends ThirdPartyComponent {
    protected transient String currentModule;
    protected Map<String, IgIconGroup> modules = new HashMap<>();

    public static class IgIconGroup {
        protected transient String name;
        protected Map<String, IgIcon> icons = new HashMap<>();

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public Map<String, IgIcon> getIcons() { return icons; }

        public void setIcons(Map<String, IgIcon> icons) { this.icons = icons; }

        public IgIcon findIcon(String name) { return icons.get(name); }

        @Override
        public String toString() {
            return new ToStringBuilder(this, JSON_STYLE)
                       .append("name", name)
                       .append("icons", icons)
                       .toString();
        }
    }

    public static class IgIcon {
        protected transient String name;
        protected String shortcut;
        protected int xOffset;
        protected int yOffset;

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String getShortcut() { return shortcut; }

        public void setShortcut(String shortcut) { this.shortcut = shortcut; }

        public int getXOffset() { return xOffset; }

        public void setXOffset(int xOffset) { this.xOffset = xOffset; }

        public int getYOffset() { return yOffset; }

        public void setYOffset(int yOffset) { this.yOffset = yOffset; }

        @Override
        public String toString() {
            return new ToStringBuilder(this, JSON_STYLE)
                       .append("name", name)
                       .append("shortcut", shortcut)
                       .append("xOffset", xOffset)
                       .append("yOffset", yOffset)
                       .toString();
        }
    }

    public void updateCurrentModule(String module) {
        this.currentModule = module;
        ConsoleUtils.log("Current module of this " + this.getClass().getSimpleName() + " instance: " + currentModule);
    }

    public IgIcon findIcon(String name) {
        return modules.get(currentModule) == null ? null : modules.get(currentModule).findIcon(name);
    }

    public StepResult click(String name) {
        String msgPrefix = "Icon '" + name + "' ";

        IgIcon icon = findIcon(name);
        if (icon == null) { return StepResult.fail(msgPrefix + "cannot be found"); }

        String shortcut = icon.getShortcut();
        if (StringUtils.isNotBlank(shortcut)) {
            component.getDriver().executeScript("shortcut: <[" + shortcut + "]>", component.getElement());
            return StepResult.success(msgPrefix + "invoked via shortcut " + shortcut);
        }

        int xOffset = icon.getXOffset();
        int yOffset = icon.getYOffset();
        if (xOffset < 1 || yOffset < 1) {
            return StepResult.fail(msgPrefix + "cannot be clicked via offset (" + xOffset + "," + yOffset + ")");
        }

        clickOffset(component.getElement(), xOffset + "", yOffset + "");
        return StepResult.success("Clicked offset (" + xOffset + "," + yOffset + ") from icon '" + name + "'");
    }

    @Override
    public String toString() { return "currentModule=" + currentModule + NL + GSON.toJson(this); }
}
