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

package org.nexial.core.plugins.web;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.RequireBrowser;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.Alert;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;

import static org.nexial.core.NexialConst.Data.OPT_PREEMPTIVE_ALERT_CHECK;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.utils.CheckUtils.requires;
import static org.nexial.core.utils.CheckUtils.requiresValidVariableName;

public class AlertCommand extends BaseCommand implements RequireBrowser {
    protected Browser browser;
    protected WebDriver driver;

    @Override
    public Browser getBrowser() { return browser; }

    @Override
    public void setBrowser(Browser browser) { this.browser = browser; }

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        driver = null;
        ensureWebDriver();
    }

    @Override
    public String getTarget() { return "webalert"; }

    public StepResult dismiss() { return handleDialog(false, false, null); }

    public StepResult accept() { return handleDialog(true, false, null); }

    public StepResult replyOK(String text) { return handleDialog(true, true, StringUtils.defaultIfEmpty(text, "")); }

    public StepResult replyCancel(String text) {
        return handleDialog(false, true, StringUtils.defaultIfEmpty(text, ""));
    }

    public StepResult assertPresent() {
        return isDialogPresent() ?
               StepResult.success("EXPECTED dialog found") :
               StepResult.fail("expected dialog NOT present");
    }

    public StepResult storeText(String var) {
        requiresValidVariableName(var);

        // get current alert text (if any)
        String alertText = harvestDialogText();
        if (StringUtils.isEmpty(alertText)) { return StepResult.fail("No dialog found"); }

        context.setData(var, alertText);
        return StepResult.success("stored dialog text '" + alertText + "' to ${" + var + "}");
    }

    @NotNull
    public StepResult assertText(String text, String matchBy) {
        requires(StringUtils.isNotBlank(text), "invalid text", text);

        if (StringUtils.isBlank(matchBy)) { matchBy = MATCH_BY_EXACT; }
        requires(MATCH_BY_RULES.contains(matchBy), "invalid 'matchBy' value", matchBy);

        // get current alert text (if any)
        String lastHarvested = context.getStringData(OPT_LAST_ALERT_TEXT);
        String alertText = StringUtils.defaultIfEmpty(harvestDialogText(), lastHarvested);
        if (StringUtils.isEmpty(alertText)) { return StepResult.fail("No dialog found; no dialog text captured"); }

        if (matchBy.equals(MATCH_BY_EXACT)) { return assertEqual(text, alertText); }
        if (matchBy.equals(MATCH_BY_CONTAINS)) { return assertContains(alertText, text); }
        if (matchBy.equals(MATCH_BY_STARTS_WITH)) { return assertStartsWith(alertText, text); }
        if (matchBy.equals(MATCH_BY_ENDS_WITH)) { return assertEndsWith(alertText, text); }
        return StepResult.fail("UNSUPPORTED matchBy rule: " + matchBy);
    }

    @NotNull
    protected StepResult handleDialog(boolean accept, boolean reply, String text) {
        ensureWebDriver();

        try {
            Alert alert = driver.switchTo().alert();
            if (alert == null) {
                log("No dialog found");
                return StepResult.fail("No dialog found");
            }

            String msg = harvestDialogText(alert);

            if (reply) { alert.sendKeys(text); }

            if (accept) {
                alert.accept();
                return StepResult.success("accepted dialog '" + msg + "'");
            } else {
                alert.dismiss();
                return StepResult.success("dismissed dialog '" + msg + "'");
            }
        } catch (NoAlertPresentException e) {
            return StepResult.fail("No dialog was present");
        } catch (UnreachableBrowserException e) {
            return StepResult.fail("browser already closed");
        }
    }

    protected void preemptiveDismissAlert() { if (preemptiveCheckAlert()) { accept(); } }

    protected boolean preemptiveCheckAlert() {
        if (browser == null || browser.isRunElectron()) { return false; }
        return context.getBooleanData(OPT_PREEMPTIVE_ALERT_CHECK, getDefaultBool(OPT_PREEMPTIVE_ALERT_CHECK)) &&
               isDialogPresent();
    }

    protected String harvestDialogText(Alert alert) {
        String msg = alert.getText();
        if (StringUtils.isNotEmpty(msg)) {
            ConsoleUtils.log("found dialog text - " + msg);
            context.setData(OPT_LAST_ALERT_TEXT, msg);
        }
        return msg;
    }

    protected String harvestDialogText() {
        ensureWebDriver();

        try {
            Alert alert = driver.switchTo().alert();
            if (alert == null) { return null; }
            return harvestDialogText(alert);
        } catch (NoAlertPresentException e) {
            ConsoleUtils.log("No dialog was present");
        } catch (UnreachableBrowserException e) {
            ConsoleUtils.log("browser already closed: " + e);
        }

        return null;
    }

    protected boolean isDialogPresent() {
        if (browser == null || browser.isRunElectron()) { return false; }

        ensureWebDriver();

        try {
            driver.switchTo().alert();
            return true;
        } catch (NoAlertPresentException | UnreachableBrowserException e) {
            return false;
        }
    }

    private void ensureWebDriver() { if (driver == null && browser != null) { driver = browser.ensureWebDriverReady();}}
}
