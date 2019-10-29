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
 */

package org.nexial.core.plugins.image;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.plugins.image.ImageCaptionHelper.CaptionModel;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.ImageCaption.CaptionPositions.*;

public class ImageCaptionHelperTest {
    private String fixture1 = null;
    private String expected1 = null;
    private String expected2 = null;
    private String expected3 = null;
    private String expected4 = null;
    private String expected5 = null;
    private String expected6 = null;

    @Before
    public void setUp() throws Exception {
        fixture1 = ResourceUtils.getResourceFilePath("/unittesting/artifact/data/unitTest_image.test1.jpg");

        String expectedBasePath = "/unittesting/artifact/data/image/";
        expected1 = ResourceUtils.getResourceFilePath(expectedBasePath + "expected1.bottom_right.yellow.jpg");
        expected2 = ResourceUtils.getResourceFilePath(expectedBasePath + "expected2.bottom_left.orange.jpg");
        expected3 = ResourceUtils.getResourceFilePath(expectedBasePath + "expected3.bottom_center_green.jpg");
        expected4 = ResourceUtils.getResourceFilePath(expectedBasePath + "expected4.top_center_pink.jpg");
        expected5 = ResourceUtils.getResourceFilePath(expectedBasePath + "expected5.middle_center_white.jpg");
        expected6 = ResourceUtils.getResourceFilePath(expectedBasePath + "expected6.top_right_gray.jpg");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void addCaptionToImage() throws Exception {
        File fixtureFile1 = new File(fixture1);
        Object[] lines = {
            "0123456789ABCDEFGIJKLMOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!@#$%^&*()_+~`{}|\\][",
            this.getClass().getName(),
            "supercalifragilisticexpelidocious",
            "good night, moon"
        };

        {
            System.out.print("adding caption text - TEST 1... ");

            // set up
            CaptionModel model = new CaptionModel();
            model.addCaption(lines);
            model.setPosition(BOTTOM_RIGHT);

            File actual = newBaseFixture(fixtureFile1, "actual1.jpg");

            // add caption
            ImageCaptionHelper.addCaptionToImage(actual, model);

            // compare against expected
            assertMatchAgainstExpected(new File(expected1), actual);
            System.out.println("PASSED");
        }

        {
            System.out.print("adding caption text - TEST 2... ");

            // set up
            CaptionModel model = new CaptionModel();
            model.addCaption(lines);
            model.setPosition(BOTTOM_LEFT);
            model.setCaptionColor("orange");
            model.setAlpha(1);
            model.setFontSize(20);

            File actual = newBaseFixture(fixtureFile1, "actual2.jpg");

            // add caption
            ImageCaptionHelper.addCaptionToImage(actual, model);

            // compare against expected
            assertMatchAgainstExpected(new File(expected2), actual);
            System.out.println("PASSED");
        }

        {
            System.out.print("adding caption text - TEST 3... ");

            // set up
            CaptionModel model = new CaptionModel();
            model.addCaption(lines);
            model.setPosition(BOTTOM_CENTER);
            model.setCaptionColor("green");

            File actual = newBaseFixture(fixtureFile1, "actual3.jpg");

            // add caption
            ImageCaptionHelper.addCaptionToImage(actual, model);

            // compare against expected
            assertMatchAgainstExpected(new File(expected3), actual);
            System.out.println("PASSED");
        }

        {
            System.out.print("adding caption text - TEST 4... ");

            // set up
            CaptionModel model = new CaptionModel();
            model.addCaption(lines);
            model.setPosition(TOP_CENTER);
            model.setCaptionColor("pink");

            File actual = newBaseFixture(fixtureFile1, "actual4.jpg");

            // add caption
            ImageCaptionHelper.addCaptionToImage(actual, model);

            // compare against expected
            assertMatchAgainstExpected(new File(expected4), actual);
            System.out.println("PASSED");
        }

        {
            System.out.print("adding caption text - TEST 5... ");

            // set up
            CaptionModel model = new CaptionModel();
            model.addCaption(lines);
            model.setPosition(MIDDLE_CENTER);
            model.setCaptionColor("white");

            File actual = newBaseFixture(fixtureFile1, "actual5.jpg");

            // add caption
            ImageCaptionHelper.addCaptionToImage(actual, model);

            // compare against expected
            assertMatchAgainstExpected(new File(expected5), actual);
            System.out.println("PASSED");
        }

        {
            System.out.print("adding caption text - TEST 6... ");

            // set up
            CaptionModel model = new CaptionModel();
            model.addCaption(lines);
            model.setPosition(TOP_RIGHT);
            model.setCaptionColor("gray");
            model.setAlpha(0.8f);
            model.setWrap(true);

            File actual = newBaseFixture(fixtureFile1, "actual6.jpg");

            // add caption
            ImageCaptionHelper.addCaptionToImage(actual, model);

            // compare against expected
            assertMatchAgainstExpected(new File(expected6), actual);
            System.out.println("PASSED");
        }

        // ProcessInvoker.invoke("open", Collections.singletonList(img.getAbsolutePath()), null);
    }

    protected ImageComparison assertMatchAgainstExpected(File expected, File actual) throws IOException {
        ImageComparison imageComparison = new ImageComparison(ImageIO.read(expected), ImageIO.read(actual));
        float matchPercent = imageComparison.getMatchPercent();
        Assert.assertEquals(100, matchPercent, 0);
        return imageComparison;
    }

    @NotNull
    protected File newBaseFixture(File baseFixture, String newFileName) throws IOException {
        File img = new File(SystemUtils.getJavaIoTmpDir() + separator + newFileName);
        FileUtils.copyFile(baseFixture, img);
        return img;
    }
}