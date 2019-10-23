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

import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.excel.ext.CipherHelper.CRYPT_IND
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.TestStep
import org.openqa.selenium.*
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.interactions.MoveTargetOutOfBoundsException
import org.openqa.selenium.remote.ScreenshotException
import java.util.*
import java.util.stream.Collectors

/**
 * specialized "interpreter" class to decipher and simplify the more cryptic error messages of Selenium/WebDriver
 * so that Nexial users can better understand the underlying root cause and take appropriate actions.
 */
object WebDriverExceptionHelper {
    private const val noException = "No error or exception found"

    @JvmStatic
    fun analyzeError(context: ExecutionContext, step: TestStep, e: WebDriverException?): String? {
        return if (e == null) noException else resolveCommandDetail(context, step) + "\n" + resolveErrorMessage(e)
    }

    @JvmStatic
    fun resolveErrorMessage(e: WebDriverException): String {
        val error = e.message
        val messageLines = StringUtils.split(error, "\n")
        val errorSummary = if (ArrayUtils.getLength(messageLines) > 2)
            "${messageLines[0]} ${messageLines[1]}..."
        else
            error

        val heading = when (e) {
            is ElementNotInteractableException -> "Specified element disable or not ready: "
            is ElementNotSelectableException   -> "Specified element cannot be selected: "
            is ElementNotVisibleException      -> "Specified element is not visible: "
            is JavascriptException             -> "JavaScript error: "
            is MoveTargetOutOfBoundsException  -> "Target element to move is outside of browser window dimension: "
            is NoAlertPresentException         -> "Specified alert dialog not found: "
            is NoSuchCookieException           -> "Specified cookie not found: "
            is NoSuchElementException          -> "Specified element not found: "
            is NoSuchFrameException            -> "Specified frame invalid or not found: "
            is NoSuchWindowException           -> "Specified window invalid or not found: "
            is ScreenshotException             -> "Unable to capture screenshot: "
            is UnhandledAlertException         -> "JavaScript alert dialog not properly handled: "
            is StaleElementReferenceException  -> "Referenced element is either not longer available or attached to the specified locator"
            else                               -> "UNKNOWN ERROR: "
        }

        return heading + errorSummary
    }

    private fun resolveCommandDetail(context: ExecutionContext?, step: TestStep?): String {
        if (step == null) return "UNKNOWN COMMAND OR PARAMETER"

        val ctx = context ?: ExecutionThread.get()
        val parameters: List<String> = if (ctx == null) ArrayList(step.params) else {
            step.params
                .stream()
                .map { p -> if (StringUtils.startsWith(p, CRYPT_IND)) p else ctx.replaceTokens(p, true) }
                .collect(Collectors.toList())
        }

        return "EXECUTING COMMAND: ${step.commandFQN}(${TextUtils.toString(parameters, ", ", "'", "'")})"
    }
}