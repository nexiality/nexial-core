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

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.RequireBrowser;
import org.nexial.core.plugins.base.BaseCommand;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;

import static org.nexial.core.utils.CheckUtils.requires;

public class CookieCommand extends BaseCommand implements RequireBrowser {
    protected Browser browser;
    protected WebDriver driver;

    @Override
    public String getTarget() { return "webcookie"; }

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

    public StepResult assertValue(String name, String value) {
        Cookie cookie = getCookie(name);
        if (StringUtils.isBlank(value) && cookie == null) { return StepResult.success(); }
        return assertEqual(StringUtils.trim(value), StringUtils.trim(cookie.getValue()));
    }

    public StepResult assertNotPresent(String name) {
        requires(StringUtils.isNotBlank(name), "invalid cookie name", name);
        boolean isPresent = deriveCookieStore().getCookieNamed(name) != null;
        return new StepResult(!isPresent,
                              "UNEXPECTED cookie '" + name + "' " + (isPresent ? "FOUND" : "not found"),
                              null);
    }

    public StepResult assertPresent(String name) {
        requires(StringUtils.isNotBlank(name), "invalid cookie name", name);
        boolean isPresent = deriveCookieStore().getCookieNamed(name) != null;
        return new StepResult(isPresent,
                              "EXPECTED cookie '" + name + "' " + (isPresent ? "found" : "NOT found"),
                              null);
    }

    public StepResult save(String var, String name) {
        requires(StringUtils.isNotBlank(var), "invalid variable name", var);
        Cookie cookie = getCookie(name);
        if (cookie != null) { context.setData(var, cookie); }
        return StepResult.success("bound cookie named '" + name + "' saved as '" + var + "'");
    }

    public StepResult saveAll(String var) {
        requires(StringUtils.isNotBlank(var), "invalid variable name", var);
        Set<Cookie> cookies = deriveCookieStore().getCookies();
        context.setData(var, cookies);
        return StepResult.success("all bound cookies saved to " + var);
    }

    public StepResult delete(String name) {
        requires(StringUtils.isNotBlank(name), "invalid cookie name", name);
        deriveCookieStore().deleteCookieNamed(name);
        return StepResult.success("bound cookie named '" + name + "' deleted");
    }

    public StepResult deleteAll() {
        deriveCookieStore().deleteAllCookies();
        return StepResult.success("all bound cookies deleted");
    }

    public Cookie getCookie(String name) {
        requires(StringUtils.isNotBlank(name), "invalid cookie name", name);
        return deriveCookieStore().getCookieNamed(name);
    }

    protected WebDriver.Options deriveCookieStore() {
        //if (context.getBrowser().isRunIE()) {
        //	// IE-specific support... since IE driver can't focus on window by name
        //	TargetLocator targetLocator = driver.switchTo();
        //	Set<String> winHandles = driver.getWindowHandles();
        //	for (String handle : winHandles) {
        //		try {
        //			return targetLocator.window(handle).manage();
        //		} catch (Throwable e) {
        //			// keep trying...
        //			log("Unable to switch to window '" + handle + "': " + e.getMessage());
        //			log("keep trying...");
        //		}
        //	}
        //
        //	throw new RuntimeException("Unable to swtich to an active window to retrieve cookies");
        //}

        ensureWebDriver();
        return driver.manage();
    }

    private void ensureWebDriver() {
        if (driver == null && browser != null) { driver = browser.ensureWebDriverReady(); }
    }
}
