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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.config.ValidationConfig;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.ValidationType;

import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.buildError;

public class DateValidator implements FieldValidator {

    FieldValidator nextValidator;

    @Override
    public FieldValidator setNextValidator(FieldValidator nextValidator) {
        this.nextValidator = nextValidator;
        return this.nextValidator;
    }

    @Override
    public void validateField(FieldBean field) {
        List<ValidationConfig> validationConfigs = field.getConfig().getValidationConfigs();
        if (validationConfigs == null || validationConfigs.isEmpty()) { return; }

        for (ValidationConfig validationConfig : validationConfigs) {
            if (validationConfig.getType().equals(ValidationType.DATE.toString())) {
                String format = validationConfig.getParams().getAsString();
                String actual = field.getFieldValue().trim();

                if (!validateDate(actual, format)) {
                    logErrorMessage(field, format, actual);
                }
            }
        }
        nextValidator.validateField(field);
    }

    protected static boolean validateDate(String value, String format) {
        if (StringUtils.isNotEmpty(value) && StringUtils.isNotEmpty(format)) {
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
                Date date = simpleDateFormat.parse(value);
                if (!value.equals(simpleDateFormat.format(date))) { return false; }
            } catch (ParseException e) { return false; }
        } else { return false; }

        return true;
    }

    private void logErrorMessage(FieldBean field, String format, String actual) {
        String errorMessage = ErrorMessage.dateCheckError(field, format, actual);
        field.getErrors().add(buildError(field,
                                         Severity.ERROR,
                                         errorMessage,
                                         ValidationType.DATE.toString()));
    }
}
