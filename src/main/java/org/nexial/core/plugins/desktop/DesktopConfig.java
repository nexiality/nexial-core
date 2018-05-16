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

import java.io.*;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.utils.ConsoleUtils;

import static java.io.File.separator;
import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;
import static org.nexial.core.NexialConst.OPT_PROJECT_BASE;
import static org.nexial.core.NexialConst.Project.DEF_REL_LOC_TEST_DATA;
import static org.nexial.core.plugins.desktop.DesktopConst.*;

/**
 * corresponds to application.json, this class captures the metadata of navigating a desktop app.
 */
public class DesktopConfig {

    /** identifier of this application; also used to formulate the resource location of the corresponding application.json */
    protected String appId;

    /** application under test; exe location */
    protected Aut aut;

    /** default wait time (ms) between commands */
    protected int defaultWaitMs;

    /** wait time (ms) for application to start up and ready for us */
    protected int appStartupWaitMs;

    /**
     * determine the most appropriate strategy to generate xpaths for a particular application
     */
    protected String xpathGenerationStrategy = XPATH_GEN_DEFAULT;

    /** base location of config JSON file(s) */
    protected String resourceBaseLocation;

    /** true if the config JSON was loaded as classpath resource */
    protected boolean fileBasedResource;

    protected DesktopElement app;

    public int getDefaultWaitMs() { return defaultWaitMs; }

    public void setDefaultWaitMs(int defaultWaitMs) { this.defaultWaitMs = defaultWaitMs; }

    public int getAppStartupWaitMs() { return appStartupWaitMs == 0 ? defaultWaitMs : appStartupWaitMs; }

    public void setAppStartupWaitMs(int appStartupWaitMs) { this.appStartupWaitMs = appStartupWaitMs; }

    public String getXpathGenerationStrategy() { return xpathGenerationStrategy; }

    public void setXpathGenerationStrategy(String xpathGenerationStrategy) {
        this.xpathGenerationStrategy = xpathGenerationStrategy;
    }

    public Aut getAut() { return aut; }

    public void setAut(Aut aut) { this.aut = aut; }

    public String getResourceBaseLocation() { return resourceBaseLocation; }

    public void setResourceBaseLocation(String resourceBaseLocation) {
        this.resourceBaseLocation = resourceBaseLocation;
    }

    public boolean isFileBasedResource() { return fileBasedResource; }

    public void setFileBasedResource(boolean fileBasedResource) { this.fileBasedResource = fileBasedResource; }

    public String getAppId() { return appId; }

    public void setAppId(String appId) { this.appId = appId; }

    public DesktopElement getApp() { return app; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, MULTI_LINE_STYLE)
                   .append("appId", appId)
                   .append("appStartupWaitMs", appStartupWaitMs)
                   .append("defaultWaitMs", defaultWaitMs)
                   .append("aut", aut)
                   .append("resourceBaseLocation", resourceBaseLocation)
                   .append("app", app)
                   .toString();
    }

    protected DesktopElement findComponentByXPath(String xpath) {
        return findComponentByXPath(getApp(), xpath);
    }

    protected DesktopElement findComponentByXPath(DesktopElement component, String xpath) {
        if (component == null) { return null; }

        if (StringUtils.equals(component.getXpath(), xpath)) { return component; }

        Map<String, DesktopElement> components = component.getComponents();
        if (MapUtils.isEmpty(components)) { return null; }

        for (String label : components.keySet()) {
            DesktopElement childComponent = components.get(label);
            if (findComponentByXPath(childComponent, xpath) != null) { return childComponent; }
        }

        return null;
    }

    protected static DesktopConfig parseJsonByAppId(String appId) throws IOException {
        if (StringUtils.isBlank(appId)) { return null; }

        // todo: move to using ${project}/artifact/data/desktop/${appId}/application.json
        // todo: need to incorporate ExecutionContent so that we can retrieve context data here
        String projectBase = System.getProperty(OPT_PROJECT_BASE);
        if (StringUtils.isNotBlank(projectBase)) {
            DesktopConfig config = parseJson(StringUtils.appendIfMissing(projectBase, separator) +
                                             DEF_REL_LOC_TEST_DATA + "desktop" + separator + appId + separator +
                                             DEF_CONFIG_FILENAME);
            config.setAppId(appId);
            return config;
        }

        String jsonResourcePath = DEF_CONFIG_HOME + appId + "/" + DEF_CONFIG_FILENAME;
        String resourcePath = ResourceUtils.getResourceFilePath(jsonResourcePath);
        if (StringUtils.isBlank(resourcePath)) {
            throw new RuntimeException("Unable to reference config JSON file for " + appId + ": " + jsonResourcePath);
        }

        File resource = new File(resourcePath);
        InputStream jsonInputStream = null;
        InputStreamReader jsonReader = null;
        try {
            jsonInputStream = new FileInputStream(resource);
            jsonReader = new InputStreamReader(jsonInputStream);
            DesktopConfig instance = DesktopConfig.parseJson(jsonReader);
            instance.appId = appId;
            instance.fileBasedResource = false;
            instance.resourceBaseLocation = resource.getParentFile().getAbsolutePath();
            return instance;
        } finally {
            if (jsonReader != null) { try { jsonReader.close();} catch (IOException e) {} }
            if (jsonInputStream != null) { try { jsonInputStream.close();} catch (IOException e) {} }
        }
    }

    /**
     * parse the JSON file as specified via {@code jsonFilePath}.  Note that {@code jsonFilePath} usually points to a
     * file named {@code application.json}.
     */
    protected static DesktopConfig parseJson(String jsonFilePath) throws IOException {
        if (!FileUtil.isFileReadable(jsonFilePath)) {
            throw new IOException("Unable to parse to object since file is not readable: " + jsonFilePath);
        }

        File resource = new File(jsonFilePath);
        FileReader reader = null;
        try {
            ConsoleUtils.log("loading desktopconfig via " + resource);
            reader = new FileReader(resource);
            DesktopConfig instance = parseJson(reader);
            instance.fileBasedResource = true;
            instance.resourceBaseLocation = resource.getParentFile().getAbsolutePath();
            return instance;
        } finally {
            if (reader != null) { try { reader.close();} catch (IOException e) {} }
        }
    }

    protected static DesktopConfig parseJson(Reader reader) {
        DesktopConfig config = GSON.fromJson(reader, DesktopConfig.class);

        DesktopElement app = config.getApp();
        if (app != null) {
            app.setLabel(COMPONENT_APP);
            postConfigParsed(app);
        }

        return config;
    }

    protected static void postConfigParsed(DesktopElement app) {
        Map<String, DesktopElement> components = app.getComponents();
        if (MapUtils.isEmpty(components)) { return; }

        components.forEach((label, component) -> {
            component.setLabel(label);
            component.layout = StringUtils.isBlank(component.layoutHint) ?
                               FORM_LAYOUT_DEFAULT : FormLayout.toLayout(component.layoutHint);
            postConfigParsed(component);
        });
    }
}
