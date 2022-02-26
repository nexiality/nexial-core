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

package org.nexial.core.variable

import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.nexial.core.NexialConst.*
import java.io.File
import java.io.File.separator

class SyspathTest {
    private val projectBase = "${TEMP}projects${separator}MyProject"
    private val projectReleaseBase = projectBase + separator + "Release1"
    private val artifactBase = projectReleaseBase + separator + "artifact"
    private val outBase = projectReleaseBase + separator + "sandbox" + separator + "out"

    private var syspath: Syspath? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        System.setProperty(OPT_OUT_DIR, outBase + separator + "20170106_230142")
        System.setProperty(OPT_EXCEL_FILE, artifactBase + separator + "MyScript.xlsx")
        System.setProperty(OPT_INPUT_EXCEL_FILE, artifactBase + separator + "MyScript.xlsx")
        System.setProperty(OPT_PROJECT_BASE, projectReleaseBase)
        println("setting project base as $projectReleaseBase")
        syspath = Syspath()
        syspath!!.init()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        FileUtils.deleteQuietly(File(projectBase))
    }

    @Test
    @Throws(Exception::class)
    fun testOut() {
        assertEquals("20170106_230142", syspath!!.out("name"))
        assertEquals(outBase, syspath!!.out("base"))
        assertEquals(outBase + separator + "20170106_230142", syspath!!.out("fullpath"))
    }

    @Test
    @Throws(Exception::class)
    fun testScript() {
        assertEquals("MyScript.xlsx", syspath!!.script("name"))
        assertEquals(artifactBase, syspath!!.script("base"))
        assertEquals(artifactBase + separator + "MyScript.xlsx", syspath!!.script("fullpath"))
    }

    @Test
    @Throws(Exception::class)
    fun testScreenshot() {
        assertEquals("captures", syspath!!.screenshot("name"))
        assertEquals(outBase + separator + "20170106_230142", syspath!!.screenshot("base"))
        assertEquals(outBase + separator + "20170106_230142" + separator + "captures",
                     syspath!!.screenshot("fullpath"))
    }

    @Test
    @Throws(Exception::class)
    fun testLog() {
        assertEquals("logs", syspath!!.log("name"))
        assertEquals(outBase + separator + "20170106_230142", syspath!!.log("base"))
        assertEquals(outBase + separator + "20170106_230142" + separator + "logs", syspath!!.log("fullpath"))
    }

    @Test
    @Throws(Exception::class)
    fun testTemp() {
        Assert.assertNotNull(syspath!!.temp("name"))
        Assert.assertNotNull(syspath!!.temp("base"))
        Assert.assertNotNull(syspath!!.temp("fullpath"))
    }

    @Test
    @Throws(Exception::class)
    fun testProject() {
        assertEquals(syspath!!.project("name"), "Release1")
    }
}
