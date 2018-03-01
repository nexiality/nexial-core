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

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.config.ValidationConfig;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.ValidationType;

import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.buildError;

public class EqualsValidator implements FieldValidator {

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
            if (validationConfig.getType().equals(ValidationType.EQUALS.toString())) {
                String expected = validationConfig.getParams().getAsString();
                String actual = field.getFieldValue();

                if (StringUtils.startsWith(expected, "${")) {
                    Map<String, Object> mapValues = field.getRecord().getRecordData().getMapValues();
                    DecimalFormat decimalFormat = new DecimalFormat("#.##");
                    actual = decimalFormat.format(Double.valueOf(actual));
                    expected = String.valueOf(mapValues.get(StringUtils.substringBetween(expected, "${", "}")));
                    expected = decimalFormat.format(Double.valueOf(expected));
                }

                if (!StringUtils.equals(expected, actual)) {
                    String errorMessage = ErrorMessage.compareEqualsError(field,
                                                                          ValidationType.EQUALS,
                                                                          expected,
                                                                          actual);
                    field.getErrors().add(buildError(field,
                                                     Severity.ERROR,
                                                     errorMessage,
                                                     ValidationType.EQUALS.toString()));
                }
            }
        }
        nextValidator.validateField(field);


    }

}

