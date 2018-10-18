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

import javax.validation.constraints.NotNull;

import org.nexial.core.NexialConst.BrowserType;
import org.nexial.core.model.ExecutionContext;
import org.openqa.selenium.WebDriver;

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
    protected String localExeName;

    protected CloudWebTestingPlatform(ExecutionContext context) { this.context = context; }

    public boolean isRunningLocal() { return isRunningLocal; }

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

    protected abstract void terminateLocal();
}
