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

import java.util.List;

import org.nexial.core.plugins.filevalidation.FieldBean;
import org.nexial.core.plugins.filevalidation.config.ValidationConfig;
import org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.Severity;

import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.ValidationType;
import static org.nexial.core.plugins.filevalidation.validators.ValidationsExecutor.buildError;

public class RegexValidator implements FieldValidator {

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
            if (validationConfig.getType().equals(ValidationType.REGEX.toString())) {
                if (validationConfig.getParams().isJsonPrimitive()) {
                    String expected = validationConfig.getParams().getAsString();
                    String actual = field.getFieldValue();
                    if (!(actual.matches(expected))) {
                        String errorMessage = ErrorMessage.compareEqualsError(field, ValidationType.REGEX, expected,
                                                                              actual);
                        field.getErrors().add(buildError(field,
                                                         Severity.ERROR,
                                                         errorMessage,
                                                         ValidationType.REGEX.toString()));
                    }
                }
            }
        }

        nextValidator.validateField(field);

    }


}
