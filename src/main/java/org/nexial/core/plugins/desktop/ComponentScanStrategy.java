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

package org.nexial.core.plugins.desktop;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.desktop.ig.IgExplorerBar;
import org.nexial.core.plugins.desktop.ig.IgRibbon;

public final class ComponentScanStrategy {
    private static final Map<String, Class> STRATEGY_MAPPING = initStrategies();
    private static final List<String> ONLY_ALLOW_ONE = initOnlyAllowOne();

    public static Class findMatchingClass(String hint) {
        if (StringUtils.isBlank(hint)) { return null; }
        return STRATEGY_MAPPING.get(hint);
    }

    public static boolean isOnlySingleInstanceAllowed(String hint) {
        return StringUtils.isNotBlank(hint) && ONLY_ALLOW_ONE.contains(hint);
    }

    private static Map<String, Class> initStrategies() {
        Map<String, Class> strategies = new HashMap<>();

        strategies.put("IgExplorerBar", IgExplorerBar.class);
        strategies.put("ExplorerBar", IgExplorerBar.class);

        strategies.put("IgRibbon", IgRibbon.class);
        strategies.put("Ribbon", IgRibbon.class);

        strategies.put("Table", DesktopTable.class);

        strategies.put("NextGenLoginForm", DesktopLoginForm.class);
        strategies.put("DesktopLoginForm", DesktopLoginForm.class);
        strategies.put("LoginForm", DesktopLoginForm.class);

        strategies.put("DEFAULT", DesktopElement.class);
        return strategies;
    }

    private static List<String> initOnlyAllowOne() {
        return Arrays.asList("IgExplorerBar", "ExplorerBar",
                             "IgRibbon", "Ribbon",
                             "NextGenLoginForm", "DesktopLoginForm", "LoginForm");
    }
}
