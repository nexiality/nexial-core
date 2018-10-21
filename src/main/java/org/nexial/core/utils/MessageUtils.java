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

package org.nexial.core.utils;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import static org.nexial.core.NexialConst.MSG_WARN;
import static org.nexial.core.excel.ExcelConfig.*;

public final class MessageUtils {

    private MessageUtils() {}

    public static boolean isWarn(String message) { return StringUtils.startsWith(message, MSG_WARN); }

    public static boolean isFail(String message) { return StringUtils.startsWith(message, MSG_FAIL); }

    public static boolean isPass(String message) { return StringUtils.startsWith(message, MSG_PASS); }

    public static boolean isSkipped(String message) { return StringUtils.startsWith(message, MSG_SKIPPED); }

    public static boolean isDeprecated(String message) { return StringUtils.startsWith(message, MSG_DEPRECATED); }

    public static boolean isTestResult(String message) { return isPass(message) || isFail(message) || isWarn(message); }

    public static void logAsPass(Logger logger, String message) { logger.info(renderAsPass(message)); }

    public static void logAsFail(Logger logger, String message) { logger.error(renderAsFail(message)); }

    public static void logAsWarn(Logger logger, String message) { logger.info(renderAsWarn(message)); }

    public static void logAsSkipped(Logger logger, String message) { logger.info(renderAsSkipped(message)); }

    public static String renderAsPass(String message) {
        // avoid printing null
        return MSG_PASS + StringUtils.defaultIfBlank(StringUtils.substringAfter(message, MSG_PASS),
                                                     StringUtils.defaultString(message));
    }

    public static String renderAsFail(String message) {
        // avoid printing null
        return MSG_FAIL + StringUtils.defaultIfBlank(StringUtils.substringAfter(message, MSG_FAIL),
                                                     StringUtils.defaultString(message));
    }

    public static String renderAsWarn(String message) {
        // avoid printing null
        return MSG_WARN + StringUtils.defaultIfBlank(StringUtils.substringAfter(message, MSG_WARN),
                                                     StringUtils.defaultString(message));
    }

    public static String renderAsSkipped(String message) {
        // avoid printing null
        return MSG_SKIPPED + StringUtils.defaultIfBlank(StringUtils.substringAfter(message, MSG_SKIPPED),
                                                        StringUtils.defaultString(message));
    }

    public static String markResult(String message, boolean pass) {
        String keyword = pass ? MSG_PASS : MSG_FAIL;
        message = StringUtils.startsWith(message, keyword) ? message : keyword + message;
        return markResult(message, pass, false);
    }

    public static String markResult(String message, boolean pass, boolean simplePass) {
        message = StringUtils.defaultString(message, "");

        if (isSkipped(message)) { return message; }

        if (pass) {
            if (simplePass) { return MSG_PASS; }
            return !isPass(message) ? MSG_PASS + message : message;
        }

        // keep warning message as such
        if (isWarn(message)) { return message; }

        // if not, we'll mark it as a 'fail' message
        return !isFail(message) ? MSG_FAIL + message : message;
    }
}
