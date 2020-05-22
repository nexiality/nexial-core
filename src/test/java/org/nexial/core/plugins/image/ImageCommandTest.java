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
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.TestStep;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;
import static org.nexial.core.NexialConst.Image.OPT_IMAGE_TRIM_COLOR;
import static org.nexial.core.NexialConst.Image.OPT_TRIM_BEFORE_DIFF;
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

        String test1 = testDir + "/test1.png";
        String test2 = testDir + "/test2.png";
        String test3 = testDir + "/test3.png";
        String test4 = testDir + "/test4.png";

        ImageCommand command = new ImageCommand();
        command.init(context);
        assertSuccess(command.crop(imageFile, "80,185,892,350", test1));

        String baselineImage = ResourceUtils.getResourceFilePath(resourceBasePath + "/overall-cropped-baseline.png");
        assertSuccess(command.compare(baselineImage, test1));

        assertSuccess(command.crop(imageFile, "80,18,*,*", test2));
        assertSuccess(command.crop(imageFile, "80,18,1190,*", test3));
        assertSuccess(command.crop(imageFile, "80,18,*,800", test4));
        assertSuccess(command.compare(test2, test3));
        assertSuccess(command.compare(test3, test4));
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

        String test1 = testDir + "/test1.png";
        String test2 = testDir + "/test2.png";
        String test3 = testDir + "/test3.png";

        ImageCommand command = new ImageCommand();
        command.init(context);

        assertSuccess(command.resize(imageFile, "20", "10", test1));
        BufferedImage img = ImageIO.read(new File(test1));
        Assert.assertEquals(20, img.getWidth());
        Assert.assertEquals(10, img.getHeight());

        assertSuccess(command.resize(imageFile, "20", "*", test2));
        BufferedImage img2 = ImageIO.read(new File(test2));
        Assert.assertEquals(20, img2.getWidth());
        Assert.assertEquals(818, img2.getHeight());

        assertSuccess(command.resize(imageFile, "*", "10", test3));
        BufferedImage img3 = ImageIO.read(new File(test3));
        Assert.assertEquals(1270, img3.getWidth());
        Assert.assertEquals(10, img3.getHeight());
    }

    @Test
    public void testImageCompare() throws Exception {
        String imageFile1 = ResourceUtils.getResourceFilePath(resourceBasePath + "/overall.png");
        String imageFile2 = ResourceUtils.getResourceFilePath(resourceBasePath + "/quality.png");
        String imageFile3 = ResourceUtils.getResourceFilePath(resourceBasePath + "/spider4.png");

        ImageCommand command = new ImageCommand();
        command.init(context);

        Assert.assertTrue(command.compare(imageFile1, imageFile2).failed());
        Assert.assertTrue(command.compare(imageFile2, imageFile3).failed());
    }

    @Test
    public void testImageSaveDiff() throws Exception {
        String imageFile1 = ResourceUtils.getResourceFilePath(resourceBasePath + "/overall.png");
        String imageFile2 = ResourceUtils.getResourceFilePath(resourceBasePath + "/quality.png");
        String imageFile3 = ResourceUtils.getResourceFilePath(resourceBasePath + "/spider4.png");
        String imageFile4 = ResourceUtils.getResourceFilePath(resourceBasePath + "/saveDiff1.png");
        String imageFile5 = ResourceUtils.getResourceFilePath(resourceBasePath + "/saveDiff2.png");

        ImageCommand command = new ImageCommand();
        command.init(context);

        Assert.assertTrue(command.saveDiff("compareMeta1", imageFile1, imageFile2).failed());
        ImageComparisonMeta compareMeta = (ImageComparisonMeta) context.getObjectData("compareMeta1");
        Assert.assertNotNull(compareMeta);
        Assert.assertEquals(58, compareMeta.getCount());

        Assert.assertTrue(command.saveDiff("compareMeta2", imageFile2, imageFile3).failed());
        compareMeta = (ImageComparisonMeta) context.getObjectData("compareMeta2");
        Assert.assertNotNull(compareMeta);
        Assert.assertEquals(46, compareMeta.getCount());

        Assert.assertTrue(command.saveDiff("compareMeta2", imageFile4, imageFile5).failed());
        context.setData(OPT_TRIM_BEFORE_DIFF, true);
        context.setData(OPT_IMAGE_TRIM_COLOR, "201,201,148");
        Assert.assertFalse(command.saveDiff("compareMeta2", imageFile4, imageFile5).failed());
    }

    protected void assertSuccess(StepResult result) {
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
    }

    protected void checkConvertedFile(String imageFilePath) {
        Assert.assertTrue(FileUtil.isFileReadable(imageFilePath));
    }

}