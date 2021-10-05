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

package org.nexial.core.plugins.web;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.RequireBrowser;
import org.nexial.core.plugins.base.BaseCommand;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static org.nexial.core.utils.CheckUtils.*;

public class CookieCommand extends BaseCommand implements RequireBrowser {
    protected Browser browser;
    protected WebDriver driver;

    @Override
    public Browser getBrowser() { return browser; }

    @Override
    public void setBrowser(Browser browser) { this.browser = browser; }

    @Override
    public void init(@NotNull ExecutionContext context) {
        super.init(context);
        driver = null;
        if (!context.isDelayBrowser()) { ensureWebDriver(); }
    }

    @Override
    public String getTarget() { return "webcookie"; }

    public StepResult save(String var, String name) {
        requires(StringUtils.isNotBlank(var), "invalid variable name", var);
        Cookie cookie = getCookie(name);
        if (cookie != null) { context.setData(var, cookie); }
        return StepResult.success("bound cookie named '" + name + "' saved as '" + var + "'");
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

    public StepResult saveAll(String var) {
        requires(StringUtils.isNotBlank(var), "invalid variable name", var);
        Set<Cookie> cookies = deriveCookieStore().getCookies();
        context.setData(var, cookies);
        return StepResult.success("all bound cookies saved to " + var);
    }

    public StepResult saveAllAsText(String var, String exclude) {
        requires(StringUtils.isNotBlank(var), "invalid variable name", var);

        List<String> excludedNames = context.isNullOrEmptyOrBlankValue(exclude) ?
                                     TextUtils.toList(exclude, context.getTextDelim(), true) : new ArrayList<>();
        Set<Cookie> cookies = deriveCookieStore().getCookies()
                                                 .stream()
                                                 .filter(cookie -> !excludedNames.contains(cookie.getName()))
                                                 .collect(Collectors.toSet());
        if (CollectionUtils.isNotEmpty(cookies)) {
            Optional<String> cookieText = cookies.stream()
                                                 .map(Cookie::toString)
                                                 .reduce((cookie, cookie2) -> cookie + "; " + cookie2);
            if (cookieText.isPresent()) {
                context.setData(var, cookieText.get().replace(";;", ";"));
                return StepResult.success("all bound cookies saved as text to %s, excluding %s", var, excludedNames);
            }
        }

        context.removeData(var);
        return StepResult.success("No cookies available to be saved as %s", var);
    }

    /**
     * remove fields from cookie found in `var`
     */
    public StepResult clearCookieFields(String var, String remove) {
        requiresValidVariableName(var);
        requiresNotBlank(remove, "No fields specified to be removed");

        if (!context.hasData(var)) { return StepResult.fail("No data variable named as '%s' is found", var); }

        List<String> removeFields = TextUtils.toList(remove, context.getTextDelim(), true);

        Object object = context.getObjectData(var);
        if (object instanceof String) {
            String cookieText = (String) object;
            if (removeFields.contains("secure")) {
                cookieText = StringUtils.remove(cookieText, "secure;");
                removeFields.remove("secure");
            }

            for (String field : removeFields) {
                cookieText = RegexUtils.removeMatches(cookieText, field + "=[^;]+; ");
                cookieText = RegexUtils.removeMatches(cookieText, field + "=[^;]+;?$");
            }

            context.setData(var, cookieText);
            return StepResult.success("cookie(s) updated to data variable '%s'", var);
        }

        if (object instanceof Set) {
            Set set = (Set) object;
            if (CollectionUtils.isEmpty(set)) { return StepResult.success("No cookies found as '%s'", var); }

            Object[] objects = set.toArray();
            if (!(objects[0] instanceof Cookie)) {
                return StepResult.fail("Data variable '%s' does not contains cookie(s)", var);
            }

            Set<Cookie> cookies = new HashSet<>();
            Arrays.stream(objects).forEach(c -> cookies.add(removeCookieFields((Cookie) c, removeFields)));
            context.setData(var, cookies);
            return StepResult.success("cookies updated to data variable '%s'", var);
        }

        if (object instanceof Cookie) {
            context.setData(var, removeCookieFields(((Cookie) object), removeFields));
            return StepResult.success("cookie updated to data variable '%s'", var);
        }

        return StepResult.fail("Data variable '%s' does not contains cookie(s)", var);
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

    protected String toCookieText(Set<Cookie> cookies) {
        return cookies.stream()
                      .map(Cookie::toString)
                      .reduce((cookie, cookie2) -> cookie + "; " + cookie2)
                      .map(s -> s.replace(";;", ";"))
                      .orElse(null);
    }

    protected String toCookieText(Cookie cookie) { return cookie == null ? null : cookie.toString().replace(";;", ";");}

    @NotNull
    protected Cookie removeCookieFields(Cookie cookie, List<String> removeFields) {
        String name = removeFields.contains("name") ? null : cookie.getName();
        String value = removeFields.contains("name") ? null : cookie.getValue();
        String domain = removeFields.contains("domain") ? null : cookie.getDomain();
        String path = removeFields.contains("path") ? null : cookie.getPath();
        Date expiry = removeFields.contains("expires") ? null : cookie.getExpiry();
        boolean secure = !removeFields.contains("secure") && cookie.isSecure();

        return new Cookie(name, value, domain, path, expiry, secure, cookie.isHttpOnly());
    }

    protected WebDriver.Options deriveCookieStore() {
        ensureWebDriver();
        return driver.manage();
    }

    private void ensureWebDriver() {
        if (driver == null && browser != null) { driver = browser.ensureWebDriverReady(); }
    }
}
