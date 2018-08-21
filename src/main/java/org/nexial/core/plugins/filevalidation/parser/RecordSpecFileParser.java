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

package org.nexial.core.plugins.filevalidation.parser;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.nexial.core.plugins.filevalidation.config.*;

public abstract class RecordSpecFileParser {

    public abstract MasterConfig parseMappingFile();

    public FieldConfig parseValidationConfigs(FieldConfig fieldConfig, List<ValidationsBean> validations) {
        if (CollectionUtils.isEmpty(validations)) { return fieldConfig; }
        // todo check validity of field name given in validation methods
        List<ValidationConfig> validationConfigs = new ArrayList<>();

        for (ValidationsBean validationsBean : validations) {
            if (fieldConfig.getFieldname().equals(validationsBean.getFieldname())) {
                validationsBean.getValidationmethods()
                               .forEach(validation -> validationConfigs.add(ValidationConfig.newInstance(validation)));
            }
        }

        fieldConfig.setValidationConfigs(validationConfigs);
        return fieldConfig;
    }

    public FieldConfig mapFunctionsToField(FieldConfig fieldConfig,
                                           List<MapfunctionsBean> mapFunctions) {
        List<MapFunctionConfig> mapFunctionConfigs = new ArrayList<>();
        // todo check validity of field name given in map functions
        if (mapFunctions != null) {
            for (MapfunctionsBean function : mapFunctions) {
                if (fieldConfig.getFieldname().equals(function.getFieldname())) {
                    mapFunctionConfigs.add(MapFunctionConfig.newInstance(function));
                }
            }
            fieldConfig.setMapFunctionConfigs(mapFunctionConfigs);
        }
        return fieldConfig;
    }

    public List<MapFunctionConfig> mapFunctionsToRecord(List<MapfunctionsBean> mapFunctions) {
        List<MapFunctionConfig> mapFunctionConfigs = new ArrayList<>();
        if (mapFunctions != null) {
            for (MapfunctionsBean function : mapFunctions) {
                mapFunctionConfigs.add(MapFunctionConfig.newInstance(function));
            }
        }
        return mapFunctionConfigs;
    }

}
