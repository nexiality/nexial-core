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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.config.FieldConfig;
import org.nexial.core.plugins.filevalidation.validators.Error.ErrorBuilder;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Alignment;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.DataType;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.DataType.*;
import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity.ERROR;
import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity.WARNING;

public class BasicValidator {

    void validateField(FieldBean field) {
        field.setErrors(new ArrayList<>());
        validateDataType(field);
        validateTextAlignment(field);
    }

    private void validateDataType(FieldBean field) {
        FieldConfig config = field.getConfig();
        String fieldValue = field.getFieldValue();

        CheckUtils.requiresNotNull(config, "Requires valid config for validation");
        CheckUtils.requiresNotNull(fieldValue, "Invalid field value", fieldValue);

        String dataType = StringUtils.trim(config.getDatatype());
        if (StringUtils.startsWith(dataType, "REGEX:")) {
            String regex = StringUtils.substringAfter(dataType, "REGEX:");
            try {
                final Pattern pattern = Pattern.compile(regex);
                if (!pattern.matcher(fieldValue).matches()) {
                    addDataTypeError(field, REGEX);
                }
            } catch (PatternSyntaxException e) {
                ConsoleUtils.error("Invalid REGEX: " + regex);
                addDataTypeError(field, REGEX);
            }
            return;
        }
        DataType enumType = DataType.toEnum(dataType);

        if (enumType == null) {
            addUndefinedValidationError(field, dataType);
            return;
        }

        switch (enumType) {
            case ANY: {
                if (!StringUtils.isAsciiPrintable(fieldValue)) {
                    addDataTypeError(field, DataType.ANY);
                }
                break;
            }
            case ALPHA: {
                if (!StringUtils.isAlphaSpace(fieldValue)) {
                    addDataTypeError(field, DataType.ALPHA);
                }
                break;
            }
            case BLANK: {
                if (StringUtils.isNotBlank(fieldValue)) {
                    addDataTypeError(field, DataType.BLANK);
                }
                break;
            }

            case NUMERIC: {
                if (!validateNumberDataType(fieldValue)) {
                    addDataTypeError(field, NUMERIC);
                }
                break;
            }
            case ALPHALOWER: {
                if (!StringUtils.isAllLowerCase(fieldValue.trim())) {
                    addDataTypeError(field, DataType.ALPHALOWER);
                }
                break;
            }
            case ALPHAUPPER: {
                if (!StringUtils.isAllUpperCase(fieldValue.trim())) {
                    addDataTypeError(field, DataType.ALPHAUPPER);
                }
                break;
            }
            case PERSONNAME: {
                final Pattern pattern = Pattern.compile("^[a-zA-Z]+[ ,.'\\-(a-zA-Z)]*$");
                if (!pattern.matcher(fieldValue).matches()) {
                    addDataTypeError(field, PERSONNAME);
                }
                break;
            }
            case ALPHANUMERIC: {
                if (!StringUtils.isAlphanumericSpace(fieldValue)) {
                    addDataTypeError(field, ALPHANUMERIC);
                }
                break;
            }
        }
    }

    private boolean validateNumberDataType(String fieldValue) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        ParsePosition parsePosition = new ParsePosition(0);
        numberFormat.parse(fieldValue.trim(), parsePosition);
        return fieldValue.trim().length() == parsePosition.getIndex();
    }

    private void validateTextAlignment(FieldBean field) {
        FieldConfig config = field.getConfig();
        String alignmentType = config.getAlignment();
        String fieldValue = field.getFieldValue();

        if (StringUtils.isBlank(fieldValue) || StringUtils.isBlank(alignmentType)) { return; }

        Alignment alignment = Alignment.toEnum(alignmentType);

        if (alignment == null) {
            addUndefinedValidationError(field, alignmentType);
            return;
        }

        switch (alignment) {
            case LEFT: {
                if (fieldValue.length() >= 1 && fieldValue.charAt(0) == ' ') {
                    addAlignmentError(field, Alignment.LEFT);
                }
                break;
            }
            case RIGHT: {
                if (fieldValue.length() >= 1 && fieldValue.charAt(fieldValue.length() - 1) == ' ') {
                    addAlignmentError(field, Alignment.RIGHT);
                }
                break;
            }
        }
    }

    private void addDataTypeError(FieldBean field, DataType dataType) {
        field.setDataTypeError(true);
        FieldConfig config = field.getConfig();
        String errorMessage = ErrorMessage.dataTypeError(field.getFieldValue(), config.getDatatype(), config);
        Error error = new ErrorBuilder().fieldName(config.getFieldname())
                                        .severity(ERROR.toString())
                                        .validationType(dataType.toString())
                                        .errorMessage(errorMessage)
                                        .build();

        field.getErrors().add(error);
    }

    private void addAlignmentError(FieldBean field, Alignment alignmentType) {
        field.setTextAlignmentError(true);
        FieldConfig config = field.getConfig();
        String errorMessage = ErrorMessage.alignmentError(field.getFieldValue(), config.getAlignment(), config);
        Error error = new ErrorBuilder().fieldName(config.getFieldname())
                                        .severity(ERROR.toString())
                                        .validationType(alignmentType.toString())
                                        .errorMessage(errorMessage)
                                        .build();

        field.getErrors().add(error);
    }

    private void addUndefinedValidationError(FieldBean field, String validationType) {
        FieldConfig config = field.getConfig();
        Error error = new ErrorBuilder().fieldName(config.getFieldname())
                                        .severity(WARNING.toString())
                                        .validationType(validationType)
                                        .errorMessage(ErrorMessage.undefinedValidationError(field.getFieldValue(),
                                                                                            validationType,
                                                                                            config))
                                        .build();
        field.getErrors().add(error);

    }

}
