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

package org.nexial.core.plugins.web

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_HOME
import org.junit.Assert
import org.junit.Test
import org.nexial.core.NexialConst.BrowserType
import org.nexial.core.model.MockExecutionContext
import org.nexial.core.model.TestStep
import java.io.File
import java.io.File.separator

class WebDriverHelperTest {
    private val className = WebDriverHelperTest::class.java.simpleName

    @Test
    fun deriveWind10BuildNumber() {
        val minVersion = 10240
        Assert.assertEquals("10240", EdgeDriverHelper.deriveWin10BuildNumber("10.0.10240", minVersion))
        Assert.assertEquals("17711", EdgeDriverHelper.deriveWin10BuildNumber("10.0.17711", minVersion))
        Assert.assertEquals("17134", EdgeDriverHelper.deriveWin10BuildNumber("10.0.17134.112", minVersion))
        Assert.assertEquals("17134", EdgeDriverHelper.deriveWin10BuildNumber("10.0.17134", minVersion))
        Assert.assertEquals("16299", EdgeDriverHelper.deriveWin10BuildNumber("10.0.16299.19", minVersion))
        Assert.assertEquals("10240", EdgeDriverHelper.deriveWin10BuildNumber("Windows 10 version 1607", minVersion))
        Assert.assertEquals("10240", EdgeDriverHelper.deriveWin10BuildNumber("6.8.9841", minVersion))
    }

    @Test
    fun expandVersion() {
        Assert.assertEquals(10002.0, WebDriverHelper.expandVersion("1.2"), 0.0)
        Assert.assertEquals(70024.0001, WebDriverHelper.expandVersion("7.24.1"), 0.0)
        Assert.assertEquals(70024.0001, WebDriverHelper.expandVersion("7.24.1b"), 0.0)
        Assert.assertEquals(70024.0001, WebDriverHelper.expandVersion("7.24.1-beta"), 0.0)
        Assert.assertEquals(70024.11829, WebDriverHelper.expandVersion("7.24.1-1829"), 0.0)
        Assert.assertEquals(70002.11829, WebDriverHelper.expandVersion("7b.2.1-1829"), 0.0)
        Assert.assertEquals(7.0, WebDriverHelper.expandVersion("7b"), 0.0)
        Assert.assertEquals(19.0001, WebDriverHelper.expandVersion("0.19.1"), 0.0)
    }

    @Test
    @Throws(Exception::class)
    fun resolveDriver() {
        val driverHome = File(USER_HOME + separator + ".nexial" + separator + "firefox")
        FileUtils.deleteDirectory(driverHome)

        val context = object : MockExecutionContext(true) {
            override fun getCurrentTestStep(): TestStep {
                return object : TestStep() {
                    override fun generateFilename(ext: String): String {
                        return className + StringUtils.prependIfMissing(StringUtils.trim(ext), ".")
                    }
                }
            }
        }

        val helper = WebDriverHelper.newInstance(BrowserType.firefox, context)
        val driverFile = helper.resolveDriver()
        Assert.assertNotNull(driverFile)
        Assert.assertTrue(driverFile.exists())
        Assert.assertTrue(driverFile.canRead())
        Assert.assertTrue(driverFile.length() > 1572864)
    }

    @Test
    @Throws(Exception::class)
    fun resolveElectronDriver() {
        val driverHome = File(USER_HOME + separator + ".nexial" + separator + "electron")
        FileUtils.deleteDirectory(driverHome)

        val context = object : MockExecutionContext(true) {
            override fun getCurrentTestStep(): TestStep {
                return object : TestStep() {
                    override fun generateFilename(ext: String): String {
                        return className + StringUtils.prependIfMissing(StringUtils.trim(ext), ".")
                    }
                }
            }
        }

        val helper = WebDriverHelper.newInstance(BrowserType.electron, context)
        val driverFile = helper.resolveDriver()
        Assert.assertNotNull(driverFile)
        Assert.assertTrue(driverFile.exists())
        Assert.assertTrue(driverFile.canRead())
        Assert.assertTrue(driverFile.length() > 1572864)
    }
}