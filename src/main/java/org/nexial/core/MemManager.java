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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.proc.RuntimeUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.TestCase;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.DEF_MANAGE_MEM;
import static org.nexial.core.NexialConst.OPT_MANAGE_MEM;

public class MemManager {
    private static final boolean MEM_MGMT_ENABLED =
        BooleanUtils.toBoolean(System.getProperty(OPT_MANAGE_MEM, DEF_MANAGE_MEM));

    private static final NumberFormat MEM_FORMAT = new DecimalFormat("###,###");
    private static final int MEM_LENGTH = 15;
    private static final int LOG_LENGTH = 90;
    private static final Map<String, String> MEM_CHANGES = new LinkedHashMap<>();
    private static final List<Class> GC_SCOPES = Arrays.asList(Nexial.class,
                                                               ExecutionContext.class,
                                                               ExecutionThread.class,
                                                               ExecutionDefinition.class,
                                                               TestCase.class);

    private static long lastMemUsed = -1;

    public static void recordMemoryChanges(String title) {
        if (!MEM_MGMT_ENABLED) { return; }

        long memUsed = RuntimeUtils.memUsed();

        title = StringUtils.rightPad(title, LOG_LENGTH);
        String descriptive = "mem use: " + StringUtils.leftPad(MEM_FORMAT.format(memUsed), MEM_LENGTH);
        if (lastMemUsed != -1) {
            descriptive += ", changes: " + (StringUtils.leftPad(MEM_FORMAT.format(memUsed - lastMemUsed), MEM_LENGTH));
        }

        ConsoleUtils.log("[MEM] " + title + " " + descriptive);
        lastMemUsed = memUsed;
        MEM_CHANGES.put(DateUtility.getCurrentTimestampForLogging() + " " + title, descriptive);
    }

    public static void gc(Object requestor) {
        if (!MEM_MGMT_ENABLED) { return; }
        if (requestor == null) { return; }
        gc(requestor.getClass());
    }

    public static void gc(Class requestorClass) {
        if (!MEM_MGMT_ENABLED) { return; }
        if (GC_SCOPES.contains(requestorClass)) {
            RuntimeUtils.gc();
            recordMemoryChanges(requestorClass.getSimpleName() + ", after gc");
        }
    }

    public static String showUsage() { return showUsage(""); }

    public static String showUsage(String logPrefix) {
        if (!MEM_MGMT_ENABLED) { return null; }

        StringBuilder buffer = new StringBuilder();
        MEM_CHANGES.forEach((title, log) -> buffer.append(logPrefix).append(title).append(" ").append(log)
                                                  .append("\n"));
        return buffer.toString();
    }
}
