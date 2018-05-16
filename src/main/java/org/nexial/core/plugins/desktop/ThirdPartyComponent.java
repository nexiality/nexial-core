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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.openqa.selenium.winium.WiniumDriver;

import com.google.gson.JsonObject;

import static java.io.File.separator;
import static org.nexial.core.plugins.desktop.DesktopConst.DEF_CONFIG_HOME;
import static org.nexial.core.plugins.desktop.DesktopConst.GSON;

public class ThirdPartyComponent extends DesktopCommand {
    protected transient DesktopElement component;
    protected transient DesktopConfig config;

    public DesktopElement getComponent() { return component; }

    public void setComponent(DesktopElement component) { this.component = component; }

    public DesktopConfig getConfig() { return config; }

    public void setConfig(DesktopConfig config) { this.config = config; }

    public static ThirdPartyComponent loadConfig(String appId,
                                                 String name,
                                                 Class<? extends ThirdPartyComponent> targetClass) {
        if (StringUtils.isBlank(appId)) { return null; }

        String configJsonPath = null;
        ExecutionContext context = ExecutionThread.get();
        if (context != null) {
            configJsonPath = context.getProject().getDataPath() + DEF_CONFIG_HOME + appId + separator + name + ".json";
            // make sure file is readable and is not empty
            if (!FileUtil.isFileReadable(configJsonPath, 50)) { configJsonPath = null; }
        }

        InputStream configStream = null;
        InputStreamReader configReader = null;
        JsonObject jsonConfig;
        try {
            if (configJsonPath != null) {
                configStream = new FileInputStream(new File(configJsonPath));
            } else {
                configJsonPath = StringUtils.replace(targetClass.getName(), ".", "/") + ".json";
                configStream = ResourceUtils.getInputStream(configJsonPath);
            }

            if (configStream == null) { throw new IllegalArgumentException("Unable to read from " + configJsonPath); }

            DesktopConst.debug("loading " + configJsonPath + " into an instance of " + targetClass);
            configReader = new InputStreamReader(configStream);
            jsonConfig = GSON.fromJson(configReader, JsonObject.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error loading " + configJsonPath + ": " + e.getMessage(), e);
        } finally {
            if (configStream != null) { try { configStream.close();} catch (IOException e) { } }
            if (configReader != null) {try { configReader.close();} catch (IOException e) { } }
        }

        if (jsonConfig == null || jsonConfig.size() < 1) { return null; }
        if (!jsonConfig.has(appId)) { return null; }

        JsonObject jsonApp = jsonConfig.get(appId).getAsJsonObject();
        if (jsonApp == null) {
            DesktopConst.debug("No JSON configuration found in " + configJsonPath + " for application " + appId);
            return null;
        }

        if (!jsonApp.has(name)) {
            DesktopConst.debug("No JSON configuration found in " + configJsonPath + " for application " + appId +
                               ", component " + name);
            return null;
        }

        JsonObject jsonComponent = jsonApp.get(name).getAsJsonObject();
        if (jsonComponent == null || jsonComponent.size() < 1) {
            DesktopConst.debug("No JSON configuration found in " + configJsonPath + " for application " + appId +
                               ", component " + name + "; likely invalid JSON structure");
            return null;
        }

        return GSON.fromJson(jsonComponent, targetClass);
    }

    @Override
    public void forcefulTerminate() {
        super.forcefulTerminate();
        if (component != null) { component.driver = null; }
        component = null;
    }

    @Override
    protected WiniumDriver getDriver() { return component != null ? component.getDriver() : null; }
}
