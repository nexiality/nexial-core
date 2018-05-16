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

package org.nexial.core.plugins.desktop;

import java.util.HashMap;
import java.util.Map;

import org.nexial.core.model.StepResult;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.winium.WiniumDriver;

import static org.nexial.core.plugins.desktop.DesktopConst.*;

public class DesktopLoginForm extends LoginForm {
    protected long loginScreenWaitMs = 30000;
    protected long loginWaitMs = 1000;
    protected Map<String, String> xpaths = new HashMap<>();

    @Override
    public StepResult login(String username, String password) {
        WebElement loginDialog = null;
        try {
            loginDialog = waitForElement(component.getElement(), xpaths.get(FORM), loginScreenWaitMs);
        } catch (TimeoutException e) {
            // am I already logged in?
            ConsoleUtils.log("Login form not found - probably user had already logged in.");
        }

        if (loginDialog == null) { return StepResult.success("Already logged in"); }

        loginDialog.click();

        // prompted to login, let's do it!
        String xpath = xpaths.get(USERNAME);
        WebElement elemUsername = findElement(loginDialog, xpath);
        if (elemUsername == null) { return StepResult.fail("EXPECTED element Username not found via " + xpath); }

        xpath = xpaths.get(PASWORD);
        WebElement elemPassword = findElement(loginDialog, xpath);
        if (elemPassword == null) { return StepResult.fail("EXPECTED element Password not found via " + xpath); }

        xpath = xpaths.get(BTN_LOGIN);
        WebElement elemLogin = findElement(loginDialog, xpath);
        if (elemLogin == null) { return StepResult.fail("EXPECTED element Login button not found via " + xpath); }

        xpath = xpaths.get(BTN_CANCEL);
        WebElement elemCancel = findElement(loginDialog, xpath);
        if (elemCancel == null) { return StepResult.fail("EXPECTED element Cancel button not found via " + xpath); }

        WiniumDriver driver = component.getDriver();
        driver.executeScript("input: brc_click", elemPassword);
        driver.executeScript("automation: ValuePattern.SetValue", elemUsername, username);
        driver.executeScript("automation: ValuePattern.SetValue", elemPassword, password);
        driver.executeScript("input: brc_click", elemLogin);

        // can't do a waitForLocator since all the elements are probably already loaded at this time
        waitFor(loginWaitMs + "");

        return StepResult.success("Successfully logged in as " + username);
    }
}
