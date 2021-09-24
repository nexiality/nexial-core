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

        return if (execScript("return window.frames['$frameName']!=null", java.lang.Boolean::class.java).booleanValue())
            StepResult.success("frame '$frameName' found")
        else
            StepResult.fail("frame '$frameName' DOES NOT exists.")
    }

    fun assertFrameCount(count: String): StepResult {
        // ensuring that count can be converted to an integer
        val countInt = webCommand.toInt(count, "count")
        val frameCount = execScript("return window.frames.length", java.lang.Long::class.java)
        return if (countInt == frameCount.toInt())
            StepResult.success("EXPECTED $count frame(s) found")
        else
            StepResult.fail("EXPECTS $count frame(s) but found $frameCount")
    }

    fun selectFrame(locator: String): StepResult {
        try {
            if (StringUtils.equals(locator, "relative=top")) {
                driver = driver.switchTo().defaultContent()
                return StepResult.success("selected frame '$locator'")
            }

            if (StringUtils.startsWith(locator, "index=")) {
                val frameIndex = StringUtils.substringAfter(locator, "index=")
                requiresPositiveNumber(frameIndex, "invalid frame index", frameIndex)
                driver = driver.switchTo().frame(NumberUtils.toInt(frameIndex))
                return StepResult.success("selected frame '$locator'")
            }

            if (StringUtils.startsWith(locator, "id=")) {
                driver = driver.switchTo().frame(StringUtils.substringAfter(locator, "id="))
                return StepResult.success("selected frame '$locator'")
            }

            if (StringUtils.startsWith(locator, "name=")) {
                driver = driver.switchTo().frame(StringUtils.substringAfter(locator, "name="))
                return StepResult.success("selected frame '$locator'")
            }

            driver = driver.switchTo().frame(webCommand.findElement(locator))
            return StepResult.success("selected frame '$locator'")
        } catch (e: NoSuchFrameException) {
            return StepResult.fail("Cannot switch to target frame '" + locator + "': " + e.message)
        }
    }

    private fun <T> execScript(script: String, returnType: Class<T>): T {
        return returnType.cast((driver as JavascriptExecutor).executeScript(script))
    }
}
