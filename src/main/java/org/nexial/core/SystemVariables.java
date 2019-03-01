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

package org.nexial.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.TextUtils;

import static org.nexial.core.NexialConst.Data.*;

/**
 * constants repo. to manage/track system variables
 */
public class SystemVariables {
    // global defaults, to be registered from the definition of each default values
    private static final Map<String, Object> SYSVARS = new HashMap<>();
    private static final List<String> SYSVARGROUPS = new ArrayList<>();
    private static final Map<String, String> OLD_NEW_SYSTEM_VARIABLE_NAMES =
        TextUtils.toMap("=",
                        OPT_DEBUG_HIGHLIGHT_OLD + "=" + OPT_DEBUG_HIGHLIGHT,
                        HIGHLIGHT_WAIT_MS_OLD + "=" + HIGHLIGHT_WAIT_MS,
                        ASSISTANT_MODE + "=" + OPT_OPEN_RESULT,
                        MAIL_TO + "=" + MAIL_TO2
                       );

    private SystemVariables() { }

    public static String registerSystemVariable(String name) {
        if (StringUtils.isNotBlank(name)) { SYSVARS.put(name, null); }
        return name;
    }

    public static <T> String registerSystemVariable(String name, T value) {
        if (StringUtils.isNotBlank(name)) { SYSVARS.put(name, value); }
        return name;
    }

    public static String registerSystemVariableGroup(String group) {
        if (StringUtils.isNotBlank(group) && !SYSVARGROUPS.contains(group)) { SYSVARGROUPS.add(group); }
        return group;
    }

    public static String getDefault(String name) {
        return SYSVARS.containsKey(name) ? String.valueOf(SYSVARS.get(name)) : null;
    }

    public static boolean getDefaultBool(String name) {
        if (!SYSVARS.containsKey(name)) {
            throw new IllegalArgumentException("No default configured for '" + name + "'");
        }
        return BooleanUtils.toBoolean(String.valueOf(SYSVARS.get(name)));
    }

    public static int getDefaultInt(String name) {
        if (!SYSVARS.containsKey(name)) {
            throw new IllegalArgumentException("No default value configured for '" + name + "'");
        }
        return NumberUtils.toInt(String.valueOf(SYSVARS.get(name)));
    }

    public static long getDefaultLong(String name) {
        if (!SYSVARS.containsKey(name)) {
            throw new IllegalArgumentException("No default configured for '" + name + "'");
        }
        return NumberUtils.toLong(String.valueOf(SYSVARS.get(name)));
    }

    public static double getDefaultFloat(String name) {
        if (!SYSVARS.containsKey(name)) {
            throw new IllegalArgumentException("No default configured for '" + name + "'");
        }
        return NumberUtils.toFloat(String.valueOf(SYSVARS.get(name)));
    }

    public static double getDefaultDouble(String name) {
        if (!SYSVARS.containsKey(name)) {
            throw new IllegalArgumentException("No default configured for '" + name + "'");
        }
        return NumberUtils.toDouble(String.valueOf(SYSVARS.get(name)));
    }

    public static String getPreferredSystemVariableName(String name) {
        return OLD_NEW_SYSTEM_VARIABLE_NAMES.getOrDefault(name, name);
    }

    public static boolean isRegisteredSystemVariable(String name) {
        if (SYSVARS.containsKey(name)) { return true; }
        for (String group : SYSVARGROUPS) { if (StringUtils.startsWith(name, group)) { return true; } }
        return false;
    }

    public static List<String> listSystemVariables() {
        return SYSVARS.keySet().stream().sorted().collect(Collectors.toList());
    }
}
