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

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.core.model.StepResult
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.CheckUtils.requiresPositiveNumber
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.NoSuchFrameException
import org.openqa.selenium.WebDriver

internal class FrameHelper(private val webCommand: WebCommand, private var driver: WebDriver) {

    fun assertFramePresent(frameName: String): StepResult {
        requiresNotBlank(frameName, "invalid frame name", frameName)

        return if (execScript("return window.frames['$frameName']!=null", Boolean::class.java))
            StepResult.success("frame '$frameName' found")
        else
            StepResult.fail("frame '$frameName' DOES NOT exists.")
    }

    fun assertFrameCount(count: String): StepResult {
        // ensuring that count can be converted to an integer
        val countInt = webCommand.toInt(count, "count")
        val frameCount = execScript("return window.frames.length", Long::class.java)
        webCommand.assertEquals(countInt.toString(), frameCount.toString())
        return StepResult.success("EXPECTS $count frame(s); found $frameCount")
    }

    fun selectFrame(locator: String): StepResult {
        val locator1 = webCommand.locatorHelper.validateLocator(locator)

        try {
            if (StringUtils.equals(locator1, "relative=top")) {
                driver = driver.switchTo().defaultContent()
                return StepResult.success("selected frame '$locator1'")
            }

            if (StringUtils.startsWith(locator1, "index=")) {
                val frameIndex = StringUtils.substringAfter(locator1, "index=")
                requiresPositiveNumber(frameIndex, "invalid frame index", frameIndex)
                driver = driver.switchTo().frame(NumberUtils.toInt(frameIndex))
                return StepResult.success("selected frame '$locator1'")
            }

            if (StringUtils.startsWith(locator1, "id=")) {
                driver = driver.switchTo().frame(StringUtils.substringAfter(locator1, "id="))
                return StepResult.success("selected frame '$locator1'")
            }

            if (StringUtils.startsWith(locator1, "name=")) {
                driver = driver.switchTo().frame(StringUtils.substringAfter(locator1, "name="))
                return StepResult.success("selected frame '$locator1'")
            }

            driver = driver.switchTo().frame(webCommand.findElement(locator1))
            return StepResult.success("selected frame '$locator1'")
        } catch (e: NoSuchFrameException) {
            return StepResult.fail("Cannot switch to target frame '" + locator1 + "': " + e.message)
        }
    }

    private fun <T> execScript(script: String, returnType: Class<T>): T {
        return returnType.cast((driver as JavascriptExecutor).executeScript(script))
    }
}
