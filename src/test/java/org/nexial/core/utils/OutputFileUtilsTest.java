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

package org.nexial.core.utils;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.NexialConst.BrowserType;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.plugins.web.Browser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.utils.OutputFileUtils.*;

public class OutputFileUtilsTest {
    private static final SimpleDateFormat DF_TIMESTAMP = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private final String timestamp = DF_TIMESTAMP.format(new Date());
    private final String browser = "firefox20.0.1";
    private final String stepName = "7";
    private final int iteration = 3;
    private final String ext = ".xlsx";
    private String fixture = "MyTest.xlsx";

    @Before
    public void setUp() { fixture = "MyTest.xlsx"; }

    @After
    public void tearDown() { }

    @Test
    public void testAdd() {
        // individual test
        Assert.assertEquals(
            "MyTest." + timestamp + ext, OutputFileUtils.updateFilePart(fixture, timestamp, POS_START_TS));
        Assert.assertEquals(
            "MyTest." + "firefox20_0_1" + ext, OutputFileUtils.updateFilePart(fixture, browser, POS_BROWSER));
        Assert.assertEquals(
            "MyTest." + stepName + ext, OutputFileUtils.updateFilePart(fixture, stepName, POS_STEP_N_ITER));
        Assert.assertEquals(
            "MyTest." + iteration + ext, OutputFileUtils.updateFilePart(fixture, iteration + "", POS_STEP_N_ITER));
    }

    @Test
    public void testAdd2() {
        // sequence test
        fixture = OutputFileUtils.updateFilePart(fixture, timestamp, POS_START_TS);
        Assert.assertEquals("MyTest." + timestamp + ext, fixture);

        fixture = OutputFileUtils.updateFilePart(fixture, browser, POS_BROWSER);
        Assert.assertEquals("MyTest." + timestamp + ".firefox20_0_1" + ext,
                            OutputFileUtils.updateFilePart(fixture, browser, POS_BROWSER));

        fixture = OutputFileUtils.updateFilePart(fixture, stepName, POS_STEP_N_ITER);
        Assert.assertEquals("MyTest." + timestamp + ".firefox20_0_1." + stepName + ext, fixture);

        fixture = OutputFileUtils.updateFilePart(fixture, iteration + "", POS_STEP_N_ITER);
        Assert.assertEquals("MyTest." + timestamp + ".firefox20_0_1." + iteration + ext, fixture);
    }

    @Test
    public void testAdd3() {
        // jazz up the sequence
        fixture = OutputFileUtils.updateFilePart(fixture, iteration + "", POS_STEP_N_ITER);
        Assert.assertEquals("MyTest." + iteration + ext, fixture);

        fixture = OutputFileUtils.updateFilePart(fixture, browser, POS_BROWSER);
        Assert.assertEquals("MyTest." + "firefox20_0_1." + iteration + ext,
                            OutputFileUtils.updateFilePart(fixture, browser, POS_BROWSER));

        fixture = OutputFileUtils.updateFilePart(fixture, timestamp, POS_START_TS);
        Assert.assertEquals("MyTest." + timestamp + ".firefox20_0_1." + iteration + ext, fixture);

        fixture = OutputFileUtils.updateFilePart(fixture, stepName, POS_STEP_N_ITER);
        Assert.assertEquals("MyTest." + timestamp + ".firefox20_0_1." + stepName + ext, fixture);
    }

    @Test
    public void testAddBrowser() {
        Browser browser = new Browser() {
            @Override
            public BrowserType getBrowserType() { return BrowserType.firefox; }

            @Override
            public String getBrowserVersion() { return "20.0.1"; }
        };

        Assert.assertEquals("MyTest.firefox_20_0_1" + ext,
                            OutputFileUtils.addBrowser(fixture, browser));

        String fixture1 = "MyTest.20030812_092349.Step1~2" + ext;
        Assert.assertEquals("MyTest.20030812_092349.firefox_20_0_1.Step1~2" + ext,
                            OutputFileUtils.addBrowser(fixture1, browser));

        fixture1 = "MyTest.Step1~2.20030812_092349." + ext;
        Assert.assertEquals("MyTest.20030812_092349.firefox_20_0_1.Step1~2" + ext,
                            OutputFileUtils.addBrowser(fixture1, browser));
    }

    @Test
    public void testAddStepName() {
        Assert.assertEquals("MyTest.Step2a" + ext, OutputFileUtils.addIteration(fixture, "Step2a"));
        Assert.assertEquals("MyTest.2" + ext, OutputFileUtils.addIteration(fixture, "2"));

        String fixture1 = "MyTest.20030812_092349.Step1~2" + ext;
        Assert.assertEquals("MyTest.20030812_092349.Step3~2" + ext,
                            OutputFileUtils.addIteration(fixture1, "Step3"));

        fixture1 = "MyTest.Step1~2.20030812_092349." + ext;
        Assert.assertEquals("MyTest.20030812_092349.3~2" + ext, OutputFileUtils.addIteration(fixture1, "3"));
    }

    @Test
    public void testAddIteration() {
        Assert.assertEquals("MyTest.~3" + ext, OutputFileUtils.addIteration(fixture, 3));
        Assert.assertEquals("MyTest.~20" + ext, OutputFileUtils.addIteration(fixture, 20));

        String fixture1 = "MyTest.20030812_092349.Step1~2" + ext;
        Assert.assertEquals("MyTest.20030812_092349.Step1~4" + ext,
                            OutputFileUtils.addIteration(fixture1, 4));

        fixture1 = "MyTest.Step1~2.20030812_092349." + ext;
        Assert.assertEquals("MyTest.20030812_092349.Step1~145" + ext,
                            OutputFileUtils.addIteration(fixture1, 145));
    }

    @Test
    public void testAddStartDateTime() {
        String date = "20130122_055123";
        Assert.assertEquals("MyTest.20130122_055123" + ext, OutputFileUtils.addStartDateTime(fixture, date));

        String fixture1 = "MyTest.20030812_092349.Step1~2.firefox_20_0_1" + ext;
        Assert.assertEquals("MyTest.20130122_055123.firefox_20_0_1.Step1~2" + ext,
                            OutputFileUtils.addStartDateTime(fixture1,
                                                             date));

        fixture1 = "MyTest.Step1~2.20030812_092349." + ext;
        Assert.assertEquals("MyTest.20130122_055123.Step1~2" + ext,
                            OutputFileUtils.addStartDateTime(fixture1, date));
    }

    @Test
    public void testResolveContent() throws Exception {
        String tmpdir = System.getProperty("java.io.tmpdir");

        File f1 = new File(tmpdir + "/junk1.txt");
        FileUtils.write(f1, "This is a test. Do not be alarmed", DEF_CHARSET);

        File f2 = new File(tmpdir + "/junk file with spaces");
        FileUtils.write(f2, "junk file with spaces", DEF_CHARSET);

        File f3 = new File(tmpdir + "/file3");
        FileUtils.write(f3, "${a} ${b} ${c} ${d}${e}", DEF_CHARSET);

        MockExecutionContext context = new MockExecutionContext();
        context.setData("a", "junk");
        context.setData("b", "file");
        context.setData("c", "with");
        context.setData("d", "space");
        context.setData("e", "s");

        Assert.assertEquals("This is a test. Do not be alarmed",
                            OutputFileUtils.resolveContent(tmpdir + "/junk1.txt", context, false));
        Assert.assertEquals("junk file with spaces",
                            OutputFileUtils.resolveContent("junk file with spaces", context, false));
        Assert.assertEquals("junk file with spaces",
                            OutputFileUtils.resolveContent(tmpdir + "/junk file with spaces", context, false));
        Assert.assertEquals("junk file with spaces",
                            OutputFileUtils.resolveContent(tmpdir + "/junk file with spaces", context, false));
        Assert.assertEquals("junk file with spaces",
                            OutputFileUtils.resolveContent(tmpdir + "/file3", context, false));

        FileUtils.forceDelete(f1);
        FileUtils.forceDelete(f2);
        FileUtils.forceDelete(f3);

        context.cleanProject();
    }
}
