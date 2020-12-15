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

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.NexialConst.BrowserStack;
import org.nexial.core.NexialConst.BrowserType;
import org.nexial.core.NexialConst.CloudWebTesting;
import org.nexial.core.NexialConst.CrossBrowserTesting;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionEvent;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import static org.nexial.core.NexialConst.CloudWebTesting.*;
import static org.nexial.core.NexialConst.CrossBrowserTesting.SESSION_ID;
import static org.nexial.core.model.ExecutionEvent.*;

public abstract class CloudWebTestingPlatform {
    protected ExecutionContext context;

    protected String os;
    protected boolean isRunningWindows;
    protected boolean isRunningOSX;

    protected BrowserType browser;
    protected String browserVersion;
    protected String browserName;
    protected boolean pageSourceSupported;

    protected boolean isMobile;
    protected String device;
    protected boolean isRunningIOS;
    protected boolean isRunningAndroid;

    protected boolean isRunningLocal;
    protected boolean isTerminateLocal;
    protected String localExeName;

    protected CloudWebTestingPlatform(ExecutionContext context) { this.context = context; }

    public boolean isRunningLocal() { return isRunningLocal; }

    public boolean isTerminateLocal() { return isTerminateLocal; }

    public String getLocalExeName() { return localExeName;}

    public boolean isMobile() { return isMobile; }

    public boolean isPageSourceSupported() { return pageSourceSupported; }

    public String getBrowserVersion() { return browserVersion; }

    public String getBrowserName() { return browserName; }

    public String getOs() { return os;}

    public boolean isRunningWindows() { return isRunningWindows; }

    public boolean isRunningOSX() { return isRunningOSX; }

    public BrowserType getBrowser() { return browser; }

    public String getDevice() { return device; }

    public boolean isRunningIOS() { return isRunningIOS; }

    public boolean isRunningAndroid() { return isRunningAndroid; }

    @NotNull
    public abstract WebDriver initWebDriver();

    /**
     * report execution status to the on-demand, cloud-based browser execution service such as BrowserStack or
     * CrossBrowserTesting. The execution status can be scoped to the entire execution or per iteration.
     */
    public static void reportCloudBrowserStatus(ExecutionContext context,
                                                ExecutionSummary summary,
                                                ExecutionEvent targetScope) {

        if (context == null || summary == null || targetScope == null) { return; }

        if (!CloudWebTesting.isValidReportScope(targetScope)) {
            ConsoleUtils.error("Execution Scope '" + targetScope.getEventName() +
                               "' currently not supported for status report on cloud-based browser execution");
            return;
        }

        if (!context.isPluginLoaded("web")) { return; }

        // blank browser type means no browser init yet.
        String browserType = context.getBrowserType();
        if (StringUtils.isBlank(browserType)) { return; }

        // null browser means no web command yet.
        Browser browser = context.getBrowser();
        if (browser == null) { return; }

        // special case for BrowserStack and CrossBrowserTesting
        // https://www.browserstack.com/automate/rest-api

        // this means we were running browser in this script.. now let's report status
        if (browser.isRunBrowserStack() &&
            isReportStatusMatchingScope(targetScope,
                                        context.getStringData(BrowserStack.KEY_STATUS_SCOPE, SCOPE_DEFAULT))) {
            BrowserStackHelper.reportExecutionStatus(context, summary);
        }

        if (browser.isRunCrossBrowserTesting() &&
            isReportStatusMatchingScope(targetScope,
                                        context.getStringData(CrossBrowserTesting.KEY_STATUS_SCOPE, SCOPE_DEFAULT))) {
            CrossBrowserTestingHelper.reportExecutionStatus(context, summary);
        }
    }

    protected void saveSessionId(RemoteWebDriver driver) {
        context.addScriptReferenceData(SESSION_ID, driver.getSessionId().toString());
    }

    @Nullable
    protected String getSessionId() { return getSessionId(context); }

    @Nullable
    protected static String getSessionId(ExecutionContext context) {
        String sessionId = context.gatherScriptReferenceData().get(SESSION_ID);
        if (StringUtils.isBlank(sessionId)) {
            ConsoleUtils.error("Unable to report execution status since session id is blank or cannot be retrieved.");
            return null;
        }
        return sessionId;
    }

    @NotNull
    protected static String formatStatusDescription(ExecutionSummary summary) {
        return "total: " + summary.getTotalSteps() +
               ", pass: " + summary.getPassCount() +
               ", fail: " + summary.getFailCount() +
               ", success%: " + summary.getSuccessRateString();
    }

    protected abstract void terminateLocal();

    private static boolean isReportStatusMatchingScope(ExecutionEvent targetScope, String scope) {
        return (scope.equals(SCOPE_EXECUTION) && targetScope == ExecutionComplete) ||
               (scope.equals(SCOPE_SCRIPT) && targetScope == ScriptComplete) ||
               (scope.equals(SCOPE_ITERATION) && targetScope == IterationComplete);
    }
}
