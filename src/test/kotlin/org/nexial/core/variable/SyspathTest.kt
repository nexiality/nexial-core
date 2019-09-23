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

package org.nexial.core.variable;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;

public class SyspathTest {
    private static final String PROJ_BASE =
        StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator) +
        "projects" + separator + "MyProject";
    private static final String PROJ_REL_BASE = PROJ_BASE + separator + "Release1";
    private static final String ARTIFACT_BASE = PROJ_REL_BASE + separator + "artifact";
    private static final String OUT_BASE = PROJ_REL_BASE + separator + "sandbox" + separator + "out";

    private Syspath syspath;

    @Before
    public void setUp() throws Exception {
        System.setProperty(OPT_OUT_DIR, OUT_BASE + separator + "20170106_230142");
        System.setProperty(OPT_EXCEL_FILE, ARTIFACT_BASE + separator + "MyScript.xlsx");
        System.setProperty(OPT_INPUT_EXCEL_FILE, ARTIFACT_BASE + separator + "MyScript.xlsx");
        System.setProperty(OPT_PROJECT_BASE, PROJ_REL_BASE);
        System.out.println("setting project base as " + PROJ_REL_BASE);
        syspath = new Syspath();
        syspath.init();
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteQuietly(new File(PROJ_BASE));
    }

    @Test
    public void testOut() throws Exception {
        Assert.assertEquals("20170106_230142", syspath.out("name"));
        Assert.assertEquals(OUT_BASE, syspath.out("base"));
        Assert.assertEquals(OUT_BASE + separator + "20170106_230142", syspath.out("fullpath"));
    }

    @Test
    public void testScript() throws Exception {
        Assert.assertEquals("MyScript.xlsx", syspath.script("name"));
        Assert.assertEquals(ARTIFACT_BASE, syspath.script("base"));
        Assert.assertEquals(ARTIFACT_BASE + separator + "MyScript.xlsx", syspath.script("fullpath"));
    }

    @Test
    public void testScreenshot() throws Exception {
        Assert.assertEquals("captures", syspath.screenshot("name"));
        Assert.assertEquals(OUT_BASE + separator + "20170106_230142", syspath.screenshot("base"));
        Assert.assertEquals(OUT_BASE + separator + "20170106_230142" + separator + "captures",
                            syspath.screenshot("fullpath"));
    }

    @Test
    public void testLog() throws Exception {
        Assert.assertEquals("logs", syspath.log("name"));
        Assert.assertEquals(OUT_BASE + separator + "20170106_230142", syspath.log("base"));
        Assert.assertEquals(OUT_BASE + separator + "20170106_230142" + separator + "logs",
                            syspath.log("fullpath"));
    }

    @Test
    public void testTemp() throws Exception {
        Assert.assertNotNull(syspath.temp("name"));
        Assert.assertNotNull(syspath.temp("base"));
        Assert.assertNotNull(syspath.temp("fullpath"));
    }

    @Test
    public void testProject() throws Exception {
        Assert.assertEquals(syspath.project("name"), "Release1");
    }
}
