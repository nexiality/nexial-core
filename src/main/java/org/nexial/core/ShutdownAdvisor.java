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

package org.nexial.core;

import java.util.ArrayList;
import java.util.List;

import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.utils.ConsoleUtils;

public final class ShutdownAdvisor {
    private static final List<ForcefulTerminate> ADVISORS = new ArrayList<>();

    private ShutdownAdvisor() {}

    public static void addAdvisor(ForcefulTerminate advisor) {
        if (advisor == null || ADVISORS.contains(advisor)) { return; }
        ADVISORS.add(advisor);
    }

    public static boolean mustForcefullyTerminate() {
        if (ADVISORS.isEmpty()) { return false; }
        for (ForcefulTerminate advisor : ADVISORS) {
            if (advisor != null && advisor.mustForcefullyTerminate()) { return true; }
        }
        return false;
    }

    public static void forcefullyTerminate() {
        if (ADVISORS.isEmpty()) { return; }
        ConsoleUtils.log("shutdown starts...");
        while (!ADVISORS.isEmpty()) { synchronized (ADVISORS) { ADVISORS.remove(0).forcefulTerminate(); } }
        ConsoleUtils.log("shutdown ends...");
    }
}
