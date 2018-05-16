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

package org.nexial.core.plugins.filevalidation.validators;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.EnumSet;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.config.FieldConfig;
import org.nexial.core.plugins.filevalidation.validators.Error.ErrorBuilder;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Alignment;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.DataType;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity;
import org.nexial.core.utils.CheckUtils;

public class BasicValidator {

    public void validateField(FieldBean field) {

        field.setErrors(new ArrayList<>());

        validateDataType(field);
        validateTextAlignment(field);
    }

    protected void validateDataType(FieldBean field) {
        FieldConfig config = field.getConfig();
        String fieldValue = field.getFieldValue();

        CheckUtils.requiresNotNull(config, "Requires valid config for validation");
        CheckUtils.requiresNotNull(fieldValue, "Invalid field value", fieldValue);

        String dataType = StringUtils.trim(config.getDatatype());

        if (dataType == null || StringUtils.isBlank(dataType)) { return; }

        if (DataType.NUMERIC.isNumeric(dataType)) {
            if (!validateNumberDataType(fieldValue)) {
                addDataTypeError(field, Severity.ERROR, DataType.NUMERIC);
            }
            return;
        }

        if (DataType.ALPHANUMERIC.isAlphaNumeric(dataType)) {
            if (!validateAlphaNumericDataType(fieldValue)) {
                addDataTypeError(field, Severity.ERROR, DataType.NUMERIC);
            }
            return;
        }

        if (DataType.BLANK.isBlank(dataType)) {
            if (StringUtils.isNotBlank(fieldValue)) {
                addDataTypeError(field, Severity.ERROR, DataType.BLANK);
            }
            return;
        }
        addUndefinedValidationError(field, Severity.WARNING, dataType);

    }

    private boolean isEnumContains(EnumSet enumSet, String value) {
        for (Object obj : enumSet) {

            if (obj.toString().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean validateNumberDataType(String fieldValue) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        ParsePosition parsePosition = new ParsePosition(0);
        numberFormat.parse(fieldValue.trim(), parsePosition);
        return fieldValue.trim().length() == parsePosition.getIndex();
    }

    private boolean validateAlphaNumericDataType(String fieldValue) {
        return StringUtils.isAsciiPrintable(fieldValue.trim());

    }

    private void validateTextAlignment(FieldBean field) {
        FieldConfig config = field.getConfig();
        String alignmentType = config.getAlignment();
        String fieldValue = field.getFieldValue();

        if (alignmentType == null || StringUtils.isBlank(alignmentType)) { return; }

        if (!isEnumContains(EnumSet.allOf(Alignment.class), alignmentType)) {
            field.setTextAlignmentError(true);
            addUndefinedValidationError(field, Severity.WARNING, alignmentType);
            return;
        }

        if (StringUtils.isBlank(fieldValue)) { return; }

        if (isEnumContains(EnumSet.of(Alignment.L, Alignment.LEFT), alignmentType)) {
            if (fieldValue.length() >= 1 && fieldValue.charAt(0) == ' ') {
                addAlignmentError(field, Severity.ERROR, Alignment.LEFT);
            }
        }

        if (isEnumContains(EnumSet.of(Alignment.R, Alignment.RIGHT), alignmentType)) {
            if (fieldValue.length() >= 1 && fieldValue.charAt(fieldValue.length() - 1) == ' ') {
                addAlignmentError(field, Severity.ERROR, Alignment.RIGHT);
            }
        }

    }

    private void addDataTypeError(FieldBean field, Severity severity, DataType dataType) {
        field.setDataTypeError(true);
        FieldConfig config = field.getConfig();
        String errorMessage = ErrorMessage.dataTypeError(field.getFieldValue(), config.getDatatype(), config);
        Error error = new ErrorBuilder().fieldName(config.getFieldname())
                                        .severity(severity.toString())
                                        .validationType(dataType.toString())
                                        .errorMessage(errorMessage)
                                        .build();

        field.getErrors().add(error);
    }

    private void addAlignmentError(FieldBean field, Severity severity, Alignment alignmentType) {
        field.setTextAlignmentError(true);
        FieldConfig config = field.getConfig();
        String errorMessage = ErrorMessage.alignmentError(field.getFieldValue(), config.getAlignment(), config);
        Error error = new ErrorBuilder().fieldName(config.getFieldname())
                                        .severity(severity.toString())
                                        .validationType(alignmentType.toString())
                                        .errorMessage(errorMessage)
                                        .build();

        field.getErrors().add(error);
    }

    private void addUndefinedValidationError(FieldBean field, Severity severity, String validationType) {
        FieldConfig config = field.getConfig();
        Error error = new ErrorBuilder().fieldName(config.getFieldname())
                                        .severity(severity.toString())
                                        .validationType(validationType)
                                        .errorMessage(ErrorMessage.undefinedValidationError(field.getFieldValue(),
                                                                                            validationType,
                                                                                            config))
                                        .build();
        field.getErrors().add(error);

    }

}
