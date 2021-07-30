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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.nexial.commons.utils.ResourceUtils;

import static java.io.File.separator;

public class DesktopConfigTest {

    private String appId = "notepad";

    @Test
    public void parseJsonByAppId() throws Exception {
        DesktopConfig metadata = DesktopConfig.parseJson(System.getProperty("nexial.projectBase") +
                                                         "/artifact/data/desktop/" + appId + "/" +
                                                         appId + ".commons.json");
        // DesktopConfig metadata = DesktopConfig.parseJsonByAppId(appId);
        Assert.assertNotNull(metadata);
        //Assert.assertNotNull(config.resolveLocator("general", "app"));

        System.out.println("metadata = \n" + metadata);
        System.out.println("\n\n");

        Assert.assertEquals(appId, metadata.getAppId());

        Aut aut = metadata.getAut();
        Assert.assertNotNull(aut);
        Assert.assertEquals("C:\\windows\\system32", aut.getPath());
        Assert.assertEquals("notepad.exe", aut.getExe());
        Assert.assertEquals("", aut.getArgs());
        Assert.assertEquals("", aut.getDotnetConfig());

        Assert.assertEquals(800, metadata.getDefaultWaitMs());
        Assert.assertEquals(1000, metadata.getAppStartupWaitMs());

        DesktopElement app = metadata.getApp();
        Assert.assertNotNull(app);
        Assert.assertEquals("/*[@ClassName='Notepad' and @ControlType='ControlType.Window']", app.getXpath());

        //Assert.assertNull(app.getLayout());

        Map<String, DesktopElement> components = app.getComponents();
        Assert.assertNotNull(components);
        Assert.assertNotNull(components.get("TitleBar"));
        Assert.assertNotNull(components.get("MenuBar"));
        Assert.assertNotNull(components.get("Dialog"));

        // title
        DesktopElement title = components.get("TitleBar");
        Assert.assertEquals(
            "/*[@ClassName='Notepad' and @ControlType='ControlType.Window']/*[@ControlType='ControlType.TitleBar' and @AutomationId='TitleBar']",
            title.getXpath());

        // menubar
        DesktopElement menubar = components.get("MenuBar");
        Assert.assertEquals(
            "/*[@ClassName='Notepad' and @ControlType='ControlType.Window']/*[@ControlType='ControlType.MenuBar' and @AutomationId='MenuBar']",
            menubar.getXpath());
        Assert.assertEquals(
            "/*[@ClassName='Notepad' and @ControlType='ControlType.Window']/*[@ControlType='ControlType.MenuBar' and @AutomationId='MenuBar']/*[@Name='File' and @ControlType='ControlType.MenuItem']",
            menubar.getComponents().get("File").getXpath());
        Assert.assertEquals(
            "/*[@ClassName='Notepad' and @ControlType='ControlType.Window']/*[@ControlType='ControlType.MenuBar' and @AutomationId='MenuBar']/*[@Name='Edit' and @ControlType='ControlType.MenuItem']",
            menubar.getComponents().get("Edit").getXpath());
        Assert.assertEquals(
            "/*[@ClassName='Notepad' and @ControlType='ControlType.Window']/*[@ControlType='ControlType.MenuBar' and @AutomationId='MenuBar']/*[@Name='Format' and @ControlType='ControlType.MenuItem']",
            menubar.getComponents().get("Format").getXpath());
        Assert.assertEquals(
            "/*[@ClassName='Notepad' and @ControlType='ControlType.Window']/*[@ControlType='ControlType.MenuBar' and @AutomationId='MenuBar']/*[@Name='View' and @ControlType='ControlType.MenuItem']",
            menubar.getComponents().get("View").getXpath());
        Assert.assertEquals(
            "/*[@ClassName='Notepad' and @ControlType='ControlType.Window']/*[@ControlType='ControlType.MenuBar' and @AutomationId='MenuBar']/*[@Name='Help' and @ControlType='ControlType.MenuItem']",
            menubar.getComponents().get("Help").getXpath());

        // toolbar

        // dialog
        DesktopElement dialog = components.get("Dialog");
        Assert.assertEquals(
            "/*[@ClassName='Notepad' and @ControlType='ControlType.Window']/*[@ControlType='ControlType.Window' and @Name='Open']",
            dialog.getComponents().get("Open").getXpath());

    }

    static {
        String projectBase = StringUtils.removeEnd(ResourceUtils.getResourceFilePath("/unittesting/"), separator);
        System.out.println("projectBase = " + projectBase);
        System.setProperty("nexial.projectBase", projectBase);
    }

    // @Test
    // public void parseJson() throws Exception {
    // 	URL fixture = this.getClass().getResource(fixtureFile);
    // 	if (fixture == null) { Assert.fail("specified resource not found"); }
    //
    // 	System.out.println("resource = " + fixture.getFile());
    //
    // 	DesktopConfig metadata = DesktopConfig.parseJson(fixture.getFile());
    // 	Assert.assertNotNull(metadata);
    // 	//Assert.assertNotNull(config.resolveLocator("general", "app"));
    //
    // 	System.out.println("metadata = \n" + metadata);
    // 	System.out.println("\n\n");
    // }

}