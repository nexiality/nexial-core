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

package org.nexial.core.plugins.image;

import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.TestStep;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;
import static org.nexial.core.NexialConst.OPT_OUT_DIR;

public class ImageCommandTest {
    private static final String CLASSNAME = ImageCommandTest.class.getSimpleName();
    private static final String TMP_PATH = SystemUtils.getJavaIoTmpDir().getAbsolutePath();

    private final String testDir = StringUtils.appendIfMissing(JAVA_IO_TMPDIR, separator) +
                                   this.getClass().getSimpleName();
    private final String resourceBasePath = StringUtils.replace(this.getClass().getPackage().getName(), ".", "/");

    private MockExecutionContext context = new MockExecutionContext() {
        @Override
        public TestStep getCurrentTestStep() {
            return new TestStep() {
                @Override
                public String generateFilename(String ext) {
                    return CLASSNAME + StringUtils.prependIfMissing(StringUtils.trim(ext), ".");
                }
            };
        }
    };

    @Before
    public void init() throws IOException {
        System.setProperty(OPT_OUT_DIR, TMP_PATH);
        FileUtils.forceMkdir(new File(testDir));
    }

    @After
    public void cleanup() {
        FileUtils.deleteQuietly(new File(testDir));
        if (context != null) { context.cleanProject(); }
    }

    @Test
    public void testCrop() throws Exception {
        String imageFile = ResourceUtils.getResourceFilePath(resourceBasePath + "/overall.png");
        System.out.println("imageFile = " + imageFile);

        ImageCommand command = new ImageCommand();
        command.init(context);
        assertSuccess(command.crop(imageFile, "80,185,892,350", testDir + "/test1.png"));

        String baselineImage = ResourceUtils.getResourceFilePath(resourceBasePath + "/overall-cropped-baseline.png");
        assertSuccess(command.compare(baselineImage, testDir + "/test1.png"));
    }

    @Test
    public void testConvert() throws Exception {
        String sourceImageFile = ResourceUtils.getResourceFilePath(resourceBasePath + "/overall.png");
        ImageCommand command = new ImageCommand();
        command.init(context);
        command.convert(sourceImageFile, "jpg", testDir + "/converted.jpg");
        checkConvertedFile(testDir + "/converted.jpg");

        command.convert(sourceImageFile, "jpg", testDir + "/converted1.gif");
        checkConvertedFile(testDir + "/converted1.gif");
    }

    @Test
    public void testResize() throws Exception {

        String imageFile = ResourceUtils.getResourceFilePath(resourceBasePath + "/overall.png");
        System.out.println("imageFile = " + imageFile);

        ImageCommand command = new ImageCommand();
        command.init(context);
        assertSuccess(command.resize(imageFile, "20", "10", testDir + "/test1.png"));

        File resizedImgFile = new File(testDir + "/test1.png");
        BufferedImage img = ImageIO.read(resizedImgFile);

        Assert.assertEquals(20, img.getWidth());
        Assert.assertEquals(10, img.getHeight());
    }

    @Test
    public void testImageCompare() {
        String imageFile1 = ResourceUtils.getResourceFilePath(resourceBasePath + "/overall.png");
        String imageFile2 = ResourceUtils.getResourceFilePath(resourceBasePath + "/quality.png");
        String imageFile3 = ResourceUtils.getResourceFilePath(resourceBasePath + "/spider4.png");

        ImageCommand command = new ImageCommand();
        command.init(context);
        command.compare(imageFile1, imageFile2);

        StepResult result = command.compare(imageFile1, imageFile2);
        if (!result.isSuccess()) { Assert.assertTrue(true); }

        result = command.compare(imageFile2, imageFile3);
        if (!result.isSuccess()) { Assert.assertTrue(true); }
    }

    protected void assertSuccess(StepResult result) {
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
    }

    protected void checkConvertedFile(String imageFilePath) {
        File imgFile = new File(imageFilePath);
        Assert.assertTrue(imgFile.isFile());
        Assert.assertTrue(imgFile.canRead());
        Assert.assertTrue(imgFile.length() > 1);
    }

}