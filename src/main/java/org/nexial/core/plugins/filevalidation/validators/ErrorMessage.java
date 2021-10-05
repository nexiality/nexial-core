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

package org.nexial.core.plugins.filevalidation.validators;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.config.FieldConfig;

import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.ValidationType;
import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.ValidationType.*;

final class ErrorMessage {
    private ErrorMessage() {}

    public static String dataTypeError(String fieldValue, String dataType, FieldConfig config) {
        return initErrorMessagePrefix(config) +
               "Failed Validation: Data Type (" + dataType + "); Field value: '" + fieldValue + "'";
    }

    public static String alignmentError(String fieldValue, String alignmentType, FieldConfig config) {
        return initErrorMessagePrefix(config) +
               "Failed Validation: Alignment (" + alignmentType + "); Field value: '" + fieldValue + "'";
    }

    public static String inCheckError(FieldBean field, List<String> expectedStringList, String actual) {
        return initErrorMessagePrefix(field.getConfig()) +
               "Failed Validation: " + IN + "; Field value: " +
               "Expected '" + expectedStringList.toString() + "' Actual '" + actual + "'";

    }

    public static String compareEqualsError(FieldBean field, ValidationType type, String expected, String actual) {
        return initErrorMessagePrefix(field.getConfig()) +
               "Failed Validation: " + type + "; Field value: " +
               "Expected '" + expected + "' Actual '" + actual + "'";
    }

    public static String undefinedValidationError(String fieldValue, String type, FieldConfig config) {
        return initErrorMessagePrefix(config) + "Failed Validation: " +
               "UNDEFINED (" + type + "); Field value: '" + fieldValue + "'";
    }

    public static String dateCheckError(FieldBean field, String format, String actual) {
        return initErrorMessagePrefix(field.getConfig()) +
               "Failed Validation: " + DATE + "; Expected format '" + format + "' Actual Date'" + actual + "'. " +
               "Format mismatched with actual Date.";
    }

    public static String sqlCheckError(FieldBean field, String msg, String actual) {
        return initErrorMessagePrefix(field.getConfig()) + "Failed Validation: " + SQL + "; " +
               "Actual '" + actual + "' DB Result " + msg;
    }

    private static String initErrorMessagePrefix(FieldConfig config) {
        String errorMessage = "";
        if (!(config.getPositionfrom() == 0 && config.getPositionto() == 0)) {
            String positionFrom = StringUtils.leftPad(config.getPositionfrom() + "", 4, "0");
            String positionTo = StringUtils.leftPad(config.getPositionto() + "", 4, "0");
            errorMessage = "Position " + positionFrom + " - " + positionTo + "; ";
        }
        return errorMessage;
    }
}
