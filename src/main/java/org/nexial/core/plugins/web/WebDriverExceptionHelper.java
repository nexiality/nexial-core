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

package org.nexial.core.plugins.web;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestStep;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.MoveTargetOutOfBoundsException;
import org.openqa.selenium.remote.ScreenshotException;

import static org.nexial.core.excel.ext.CipherHelper.CRYPT_IND;

/**
 * specialized "interpreter" class to decipher and simplify the more cryptic error messages of Selenium/WebDriver
 * so that Nexial users can better understand the underlying root cause and take appropriate actions.
 */
public class WebDriverExceptionHelper {
    private String noException;

    public void setNoException(String noException) { this.noException = noException; }

    public String analyzeError(ExecutionContext context, TestStep step, WebDriverException e) {
        if (e == null) { return noException; }
        return resolveCommandDetail(context, step) + "\n" + resolveErrorMessage(e);
    }

    @NotNull
    public static String resolveErrorMessage(WebDriverException e) {
        String error = e.getMessage();
        String[] messageLines = StringUtils.split(error, "\n");
        String errorSummary = ArrayUtils.getLength(messageLines) > 2 ?
                              messageLines[0] + " " + messageLines[1] + "..." : error;

        String errorHeading = "UNKNOWN ERROR: ";
        if (e instanceof ElementNotInteractableException) { errorHeading = "Specified element disable or not ready: "; }
        if (e instanceof ElementNotSelectableException) { errorHeading = "Specified element cannot be selected: "; }
        if (e instanceof ElementNotVisibleException) { errorHeading = "Specified element is not visible: "; }
        if (e instanceof JavascriptException) { errorHeading = "JavaScript error: "; }
        if (e instanceof MoveTargetOutOfBoundsException) {
            errorHeading = "Target element to move is outside of browser window dimension: ";
        }
        if (e instanceof NoAlertPresentException) { errorHeading = "Specified alert dialog not found: "; }
        if (e instanceof NoSuchCookieException) { errorHeading = "Specified cookie not found: "; }
        if (e instanceof NoSuchElementException) { errorHeading = "Specified element not found: "; }
        if (e instanceof NoSuchFrameException) { errorHeading = "Specified frame invalid or not found: "; }
        if (e instanceof NoSuchWindowException) { errorHeading = "Specified window invalid or not found: "; }
        if (e instanceof ScreenshotException) {errorHeading = "Unable to capture screenshot: "; }
        if (e instanceof UnhandledAlertException) { errorHeading = "JavaScript alert dialog not properly handled: "; }

        return errorHeading + errorSummary;
    }

    private String resolveCommandDetail(ExecutionContext context, TestStep step) {
        if (step == null) { return "UNKNOWN COMMAND OR PARAMETER"; }

        if (context == null) {
            // try another way
            ExecutionContext contextInThread = ExecutionThread.get();
            if (contextInThread != null) { context = contextInThread; }
        }

        String command = step.getCommandFQN();

        List<String> parameters;
        if (context == null) {
            parameters = new ArrayList<>(step.getParams());
        } else {
            final ExecutionContext context1 = context;
            parameters = step.getParams()
                             .stream()
                             .map(param -> StringUtils.startsWith(param, CRYPT_IND) ?
                                           param : context1.replaceTokens(param, true))
                             .collect(Collectors.toList());
        }

        return "EXECUTING COMMAND: " + command + "(" + TextUtils.toString(parameters, ", ", "'", "'") + ")";
    }
}
