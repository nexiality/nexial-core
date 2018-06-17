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

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.RequireBrowser;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.Alert;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.utils.CheckUtils.requires;

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

    public StepResult assertPresent() {
        boolean alertTextHarvested = StringUtils.isNotBlank(context.getStringData(OPT_LAST_ALERT_TEXT));
        if (alertTextHarvested) { return StepResult.success("EXPECTED alert popup found"); }

        String alertText = harvestText();
        if (StringUtils.isNotBlank(alertText)) {
            return StepResult.success("EXPECTED alert popup found with text '" + alertText + "'");
        }

        if (isAlertPresent()) { return StepResult.success("EXPECTED alert popup found"); }
        return StepResult.fail("EXPECTS alert popup present but it is not");
    }

    public StepResult dismiss() {
        ensureWebDriver();
        String error = "No alert found";

        try {
            Alert alert = driver.switchTo().alert();
            if (alert != null) {
                String msg = alert.getText();
                context.setData(OPT_LAST_ALERT_TEXT, msg);
                alert.dismiss();
                return StepResult.success("dismissed alert '" + msg + "'");
            }
        } catch (NoAlertPresentException e) {
            try {
                String msg = getAlertText();
                if (StringUtils.isNotBlank(msg)) {
                    context.setData(OPT_LAST_ALERT_TEXT, msg);
                    return StepResult.success("dismissed alert '" + msg + "'");
                }
            } catch (Exception e1) {
                error = e1.getMessage();
            }
        } catch (UnreachableBrowserException e) {
            return StepResult.fail("browser already closed");
        }

        String msg = context.removeData(OPT_LAST_ALERT_TEXT);
        if (StringUtils.isNotBlank(msg)) {
            // this means that there was a alert text harvested, but the dialog is gone now
            return StepResult.success("dismissed alert '" + msg + "'");
        } else {
            log("alert - exception: " + error);
            return StepResult.fail(error);
        }
    }

    public StepResult accept() {
        ensureWebDriver();

        String error = "No alert found";

        try {
            Alert alert = driver.switchTo().alert();
            if (alert != null) {
                String msg = alert.getText();
                context.setData(OPT_LAST_ALERT_TEXT, msg);
                alert.accept();
                return StepResult.success("accepted alert '" + msg + "'");
            }
        } catch (NoAlertPresentException e) {
            try {
                String msg = getAlertText();
                if (StringUtils.isNotBlank(msg)) {
                    context.setData(OPT_LAST_ALERT_TEXT, msg);
                    return StepResult.success("accepted alert '" + msg + "'");
                }
            } catch (Exception e1) {
                error = e1.getMessage();
            }
        } catch (UnreachableBrowserException e) {
            return StepResult.fail("browser already closed");
        }

        String msg = context.removeData(OPT_LAST_ALERT_TEXT);
        if (StringUtils.isNotBlank(msg)) {
            // this means that there was a alert text harvested, but the dialog is gone now
            return StepResult.success("accepted alert '" + msg + "'");
        } else {
            log("alert - exception: " + error);
            return StepResult.fail(error);
        }
    }

    public StepResult storeText(String var) {
        requires(StringUtils.isNotBlank(var), "invalid var", var);

        // get current alert text (if any)
        String alertText = StringUtils.defaultIfBlank(harvestText(), context.getStringData(OPT_LAST_ALERT_TEXT));
        if (StringUtils.isNotBlank(alertText)) {
            // this means that there was a alert text harvested, but the dialog is gone now
            context.setData(var, alertText);
            return StepResult.success("stored text from alert popup '" + alertText + "'");
        }

        return StepResult.fail("No alert found");
    }

    public StepResult assertText(String text, String matchBy) {
        requires(StringUtils.isNotBlank(text), "invalid text", text);

        if (StringUtils.isBlank(matchBy)) { matchBy = MATCH_BY_EXACT; }
        requires(MATCH_BY_RULES.contains(matchBy), "invalid 'matchBy' value", matchBy);

        // get current alert text (if any)
        //String alertText = StringUtils.defaultIfBlank(harvestText(), context.removeData(OPT_LAST_ALERT_TEXT));
        String alertText = StringUtils.defaultIfBlank(harvestText(), context.getStringData(OPT_LAST_ALERT_TEXT));
        if (StringUtils.isBlank(alertText)) { return StepResult.fail("No alert found"); }

        if (matchBy.equals(MATCH_BY_EXACT)) { return assertEqual(text, alertText); }
        if (matchBy.equals(MATCH_BY_CONTAINS)) { return assertContains(alertText, text); }
        if (matchBy.equals(MATCH_BY_STARTS_WITH)) { return assertStartsWith(alertText, text); }
        if (matchBy.equals(MATCH_BY_ENDS_WITH)) { return assertEndsWith(alertText, text); }
        return StepResult.fail("UNSUPPORTED matchBy rule: " + matchBy);
    }

    protected void preemptiveDismissAlert() {
        if (context.getBooleanData(OPT_PREEMPTIVE_ALERT_CHECK, DEF_PREEMPTIVE_ALERT_CHECK)) { harvestText(); }
    }

    protected boolean preemptiveCheckAlert() {
        return context.getBooleanData(OPT_PREEMPTIVE_ALERT_CHECK, DEF_PREEMPTIVE_ALERT_CHECK) && isAlertPresent();
    }

    protected String harvestText() {
        String alertText = null;

        ensureWebDriver();

        try {
            Alert alert = driver.switchTo().alert();
            if (alert != null) { alertText = alert.getText(); }
            if (StringUtils.isNotBlank(alertText)) {
                ConsoleUtils.log("found alert/confirm text - " + alertText);
                context.setData(OPT_LAST_ALERT_TEXT, alertText);
            }
        } catch (UnreachableBrowserException e) {
            ConsoleUtils.log("browser already closed: " + e);
        } catch (Exception e) {
        }

        return alertText;
    }

    protected String harvestText(UnhandledAlertException e) throws NoAlertPresentException {
        // we expects alert dialog
        String alertText = harvestText();

        if (StringUtils.isBlank(alertText)) {
            if (e == null) {
                throw new NoAlertPresentException("expected alert dialog NOT found");
            } else {
                alertText = e.getAlertText();
                if (StringUtils.isNotBlank(alertText)) { context.setData(OPT_LAST_ALERT_TEXT, alertText); }
            }
        }

        return alertText;
    }

    protected boolean isAlertPresent() {
        ensureWebDriver();
        try {
            driver.switchTo().alert();
            return true;
        } catch (NoAlertPresentException e) {
            return false;
        }
    }

    protected String getAlertText() {
        ensureWebDriver();
        try {
            return driver.switchTo().alert().getText();
        } catch (NoAlertPresentException e) {
            return null;
        }
    }

    private void ensureWebDriver() {
        if (driver == null && browser != null) { driver = browser.ensureWebDriverReady(); }
    }
}
