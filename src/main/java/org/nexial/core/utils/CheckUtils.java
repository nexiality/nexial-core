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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;

import static java.lang.Integer.MIN_VALUE;
import static org.nexial.core.NexialConst.TOKEN_END;
import static org.nexial.core.NexialConst.TOKEN_START;

public class CheckUtils {

    public static boolean isValidVariable(String var) {
        return !StringUtils.isBlank(var) &&
               !TextUtils.isBetween(var, TOKEN_START, TOKEN_END) &&
               !StringUtils.containsAny(var, "$", "{", "}", "[", "]");
    }

    public static void fail(String message) {
        // use `.log()` to comply with quiet mode when activated
        // no need to log here. the assertion error will force system error print out anyways..
        // ConsoleUtils.log(message);
        throw new AssertionError(message);
    }

    public static boolean requires(boolean condition, String errorMessage, Object... params) {
        if (!condition) {
            fail(errorMessage + ": " + TextUtils.toString(ArrayUtils.toStringArray(params, "<null>"), ",", "", ""));
        }
        return condition;
    }

    public static boolean requiresNotNull(Object notNull, String errorMessage, Object... params) {
        if (notNull == null) { fail(errorMessage + ": " + ArrayUtils.toString(params)); }
        return true;
    }

    public static boolean requiresParamNotNull(final Object parameter, final String parameterName) {
        if (parameter == null) {
            throw new IllegalArgumentException("Parameter '" + parameterName + "' must not be null!");
        }
        return true;
    }

    public static boolean requiresNotBlank(String notBlank, String errorMessage, Object... params) {
        if (StringUtils.isBlank(notBlank)) { fail(errorMessage + ": " + ArrayUtils.toString(params)); }
        return true;
    }

    public static boolean requiresNotEmpty(String notBlank, String errorMessage, Object... params) {
        if (StringUtils.isEmpty(notBlank)) { fail(errorMessage + ": " + ArrayUtils.toString(params)); }
        return true;
    }

    public static boolean requiresNotEmpty(List<?> list, String errorMessage, Object... params) {
        if (CollectionUtils.isEmpty(list)) { fail(errorMessage + ": " + ArrayUtils.toString(params)); }
        return true;
    }

    public static boolean requiresValidVariableName(String var) {
        requires(isValidVariable(var), "Invalid variable name", var);
        return true;
    }

    public static boolean requiresReadableFile(String file) {
        requires(FileUtil.isFileReadable(file), "invalid, unreadable or empty file", file);
        return true;
    }

    public static boolean requiresExecutableFile(String file) {
        requires(FileUtil.isFileExecutable(file), "invalid, unexecutable or empty file", file);
        return true;
    }

    public static boolean requiresReadableDirectory(String path, String message, Object... params) {
        requiresNotBlank(path, message, params);

        String msgParams = Objects.toString(Array.get(params, 0), "");

        File dir = new File(path);
        if (!dir.exists()) {
            try {
                FileUtils.forceMkdir(dir);
            } catch (IOException e) {
                fail("specified path (" + msgParams + ") does not exists and cannot be created: " + e.getMessage());
            }
        }

        if (!dir.isDirectory() || !dir.canRead()) { fail(message + ": " + msgParams); }
        return true;
    }

    public static boolean requiresReadWritableDirectory(String path, String message, Object... params) {
        requiresReadableDirectory(path, message, params);

        File dir = new File(path);
        if (!dir.canWrite()) { fail(message + ": " + ArrayUtils.toString(params)); }

        return true;
    }

    public static boolean requiresReadableFileOrValidUrl(String resource) {
        return ResourceUtils.isWebResource(resource) || requiresReadableFile(resource);
    }

    public static boolean requiresPositiveNumber(String number, String message, Object... params) {
        requiresNotBlank(number, message, params);
        if (!NumberUtils.isDigits(number)) { fail(message + ": " + ArrayUtils.toString(params)); }
        return true;
    }

    public static boolean requiresInteger(String number, String message, Object... params) {
        requiresNotBlank(number, message, params);

        int integer = NumberUtils.toInt(number, MIN_VALUE);
        if (integer == MIN_VALUE) { fail(message + ": " + ArrayUtils.toString(params)); }
        return true;
    }

    public static boolean isInRange(double num, double lowerRange, double upperRange) {
        return lowerRange < upperRange ?
               (num >= lowerRange && num <= upperRange) :
               (num >= upperRange && num <= lowerRange);
    }

    public static boolean isSameType(Class[] types, Class required) {
        if (ArrayUtils.isEmpty(types)) { return false; }

        for (Class type : types) { if (type != required) { return false;} }
        return true;
    }

    public static boolean requireOneOf(String option, String... options) {
        if (requiresNotEmpty(option, "Invalid value", option)) {
            List<String> optionList = Arrays.asList(options);
            if (!optionList.contains(option)) { fail("Invalid value: " + option + ". Must be one of " + optionList); }
            return true;
        } else {
            return false;
        }
    }
}
