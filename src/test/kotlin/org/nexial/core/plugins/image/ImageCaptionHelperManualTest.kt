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

package org.nexial.core.plugins.image

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.SystemUtils
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.ImageCaption.CaptionPositions.*
import org.nexial.core.plugins.image.ImageCaptionHelper.CaptionModel
import org.nexial.core.plugins.image.ImageCaptionHelper.addCaptionToImage
import org.nexial.core.utils.ExecUtils
import java.io.File
import java.io.File.separator
import java.io.IOException
import javax.imageio.ImageIO

class ImageCaptionHelperManualTest {
    private val showActual = SystemUtils.IS_OS_MAC_OSX && !ExecUtils.isRunningInCi()
    // private val showActual = false

    private val fixtureBase = "/unittesting/artifact/data/image/"
    private val fixture1 = ResourceUtils.getResourceFilePath("${fixtureBase}unitTest_image.test1.jpg")
    private val expected1 = ResourceUtils.getResourceFilePath("${fixtureBase}expected1.bottom_right.yellow.jpg")
    private val expected2 = ResourceUtils.getResourceFilePath("${fixtureBase}expected2.bottom_left.orange.jpg")
    private val expected3 = ResourceUtils.getResourceFilePath("${fixtureBase}expected3.bottom_center_green.jpg")
    private val expected4 = ResourceUtils.getResourceFilePath("${fixtureBase}expected4.top_center_pink.jpg")
    private val expected5 = ResourceUtils.getResourceFilePath("${fixtureBase}expected5.middle_center_white.jpg")
    private val expected6 = ResourceUtils.getResourceFilePath("${fixtureBase}expected6.top_right_gray.jpg")

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    @Throws(Exception::class)
    fun addCaptionToImage() {
        val fixtureFile1 = File(fixture1)
        val lines = listOf<Any>("0123456789ABCDEFGIJKLMOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!@#$%^&*()_+~`{}|\\][",
                                 ImageCaptionHelper::class.java.name,
                                 "supercalifragilisticexpelidocious",
                                 "good night, moon")
        run {
            print("adding caption text - TEST 1... ")

            // set up
            val model = CaptionModel()
            model.addCaptions(lines)
            model.position = BOTTOM_RIGHT
            val actual = newBaseFixture(fixtureFile1, "actual1.jpg")

            // add caption
            addCaptionToImage(actual, model)
            if (showActual) ProcessInvoker.invoke("open", listOf(actual.absolutePath), null)

            // compare against expected
            assertMatchAgainstExpected(File(expected1), actual)
            println("PASSED")
        }

        run {
            print("adding caption text - TEST 2... ")

            // set up
            val model = CaptionModel()
            model.addCaptions(lines)
            model.position = BOTTOM_LEFT
            model.setCaptionColor("orange")
            model.alpha = 1f
            model.fontSize = 20
            val actual = newBaseFixture(fixtureFile1, "actual2.jpg")

            // add caption
            addCaptionToImage(actual, model)
            if (showActual) ProcessInvoker.invoke("open", listOf(actual.absolutePath), null)

            // compare against expected
            assertMatchAgainstExpected(File(expected2), actual)
            println("PASSED")
        }

        run {
            print("adding caption text - TEST 3... ")

            // set up
            val model = CaptionModel()
            model.addCaptions(lines)
            model.position = BOTTOM_CENTER
            model.setCaptionColor("green")
            val actual = newBaseFixture(fixtureFile1, "actual3.jpg")

            // add caption
            addCaptionToImage(actual, model)
            if (showActual) ProcessInvoker.invoke("open", listOf(actual.absolutePath), null)

            // compare against expected
            assertMatchAgainstExpected(File(expected3), actual)
            println("PASSED")
        }

        run {
            print("adding caption text - TEST 4... ")

            // set up
            val model = CaptionModel()
            model.addCaptions(lines)
            model.position = TOP_CENTER
            model.setCaptionColor("pink")
            val actual = newBaseFixture(fixtureFile1, "actual4.jpg")

            // add caption
            addCaptionToImage(actual, model)
            if (showActual) ProcessInvoker.invoke("open", listOf(actual.absolutePath), null)

            // compare against expected
            assertMatchAgainstExpected(File(expected4), actual)
            println("PASSED")
        }

        run {
            print("adding caption text - TEST 5... ")

            // set up
            val model = CaptionModel()
            model.addCaptions(lines)
            model.position = MIDDLE_CENTER
            model.setCaptionColor("white")
            val actual = newBaseFixture(fixtureFile1, "actual5.jpg")

            // add caption
            addCaptionToImage(actual, model)
            if (showActual) ProcessInvoker.invoke("open", listOf(actual.absolutePath), null)

            // compare against expected
            assertMatchAgainstExpected(File(expected5), actual)
            println("PASSED")
        }

        run {
            print("adding caption text - TEST 6... ")

            // set up
            val model = CaptionModel()
            model.addCaptions(lines)
            model.position = TOP_RIGHT
            model.setCaptionColor("gray")
            model.alpha = 0.8f
            model.isWrap = true
            val actual = newBaseFixture(fixtureFile1, "actual6.jpg")

            // add caption
            addCaptionToImage(actual, model)
            if (showActual) ProcessInvoker.invoke("open", listOf(actual.absolutePath), null)

            // compare against expected
            assertMatchAgainstExpected(File(expected6), actual)
            println("PASSED")
        }
    }

    @Throws(IOException::class)
    private fun assertMatchAgainstExpected(expected: File, actual: File): ImageComparison {
        val imageComparison = ImageComparison(ImageIO.read(expected), ImageIO.read(actual))
        val matchPercent = imageComparison.matchPercent
        Assert.assertEquals(100f, matchPercent, 0f)
        return imageComparison
    }

    @Throws(IOException::class)
    private fun newBaseFixture(baseFixture: File, newFileName: String): File {
        val img = File(SystemUtils.getJavaIoTmpDir().toString() + separator + newFileName)
        FileUtils.copyFile(baseFixture, img)
        return img
    }
}