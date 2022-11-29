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

package org.nexial.core.model;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.RequireBrowser;
import org.nexial.core.plugins.web.Browser;
import org.nexial.core.utils.ConsoleUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;

import static org.nexial.core.NexialConst.Data.CMD_PROFILE_DEFAULT;
import static org.nexial.core.NexialConst.Data.CMD_PROFILE_SEP;

public class PluginManager implements ApplicationContextAware {
    protected ApplicationContext springContext;
    protected ExecutionContext context;
    protected Map<String, NexialCommand> plugins;
    protected Map<String, NexialCommand> initialized = new HashMap<>();
    protected Map<String, Browser> profileBrowsers = new HashMap<>();

    @Override
    public void setApplicationContext(@NotNull ApplicationContext ctx) throws BeansException { springContext = ctx; }

    public void init() {
        assert context != null;
        assert MapUtils.isNotEmpty(plugins);
    }

    public void setContext(ExecutionContext context) {
        this.context = context;
        // needed when the same context instance is used for another script (in the same test plan)
        if (MapUtils.isNotEmpty(initialized)) { initialized.forEach((name, plugin) -> plugin.init(context)); }
    }

    public void setPlugins(Map<String, NexialCommand> plugins) { this.plugins = plugins; }

    public boolean isPluginLoaded(String target) { return initialized.containsKey(StringUtils.lowerCase(target)); }

    /**
     * as of 2020-02-16/v3, "target" can contain "profile" for "profile-aware" commands. The idea is to allow for
     * multiple instances of the same command so that they can automate specific targets. For example, there could be
     * a WebCommand instance for Chrome browser and another one for Firefox.
     * <p>
     * A "profile named" target has the pattern of "target::profile". The default profile is "DEFAULT"
     * (e.g. web::DEFAULT)
     */
    public NexialCommand getPlugin(String target) {
        String nsTarget;
        String profile = CMD_PROFILE_DEFAULT;
        if (StringUtils.contains(target, CMD_PROFILE_SEP)) {
            // profile-named target
            profile = StringUtils.substringAfter(target, CMD_PROFILE_SEP);
            target = StringUtils.substringBefore(target, CMD_PROFILE_SEP);
        }

        // check if command exist
        if (!plugins.containsKey(target)) {
            ConsoleUtils.error("No command target found: " + target);
            return null;
        }

        nsTarget = target + CMD_PROFILE_SEP + profile;
        boolean isInitialized = false;
        NexialCommand nexialCommand;

        if (StringUtils.equals(profile, CMD_PROFILE_DEFAULT)) {
            if (initialized.containsKey(target)) {
                nexialCommand = initialized.get(target);
                isInitialized = true;
            } else {
                nexialCommand = plugins.get(target);
            }
        } else {
            if (initialized.containsKey(nsTarget)) {
                nexialCommand = initialized.get(nsTarget);
                isInitialized = true;
            } else {
                nexialCommand = plugins.get(target);
                nexialCommand.setProfile(profile);
            }
        }

        if (nexialCommand instanceof RequireBrowser) {
            ((RequireBrowser) nexialCommand).setBrowser(initBrowser(profile));
        }

        if (!isInitialized) {
            nexialCommand.init(context);
            if (StringUtils.equals(profile, CMD_PROFILE_DEFAULT)) {
                initialized.put(target, nexialCommand);
            } else {
                initialized.put(nsTarget, nexialCommand);
            }
        }

        return nexialCommand;
    }

    protected Browser initBrowser(String profile) {
        if (StringUtils.isBlank(profile)) { profile = CMD_PROFILE_DEFAULT; }

        Browser browser = getBrowser(profile);
        if (browser != null) { return browser; }

        // create new Browser instance for "prototype-scoped" bean
        browser = springContext.getBean("browserTemplate", Browser.class);
        browser.setProfile(profile);
        browser.setContext(context);
        if (!context.isDelayBrowser()) { browser.ensureWebDriverReady(); }
        profileBrowsers.put(profile, browser);
        return browser;
    }

    protected Browser getBrowser(String profile) {
        if (StringUtils.isBlank(profile)) { profile = CMD_PROFILE_DEFAULT; }
        return profileBrowsers.get(profile);
    }

    public void clearBrowser(String profile) { profileBrowsers.remove(profile); }
}
