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

import org.nexial.core.plugins.filevalidation.config.*;

public abstract class RecordSpecFileParser {

    public abstract MasterConfig parseMappingFile();

    public FieldConfig parseValidationConfigs(FieldConfig fieldConfig, List<ValidationsBean> validations) {
        List<ValidationConfig> validationConfigs = new ArrayList<>();
        if (validations != null) {
            for (ValidationsBean validationsBean : validations) {
                if (validationsBean.getFieldname().equals(fieldConfig.getFieldname())) {
                    validationsBean.getValidationmethods()
                                   .forEach(validation -> validationConfigs
                                                              .add(ValidationConfig.newInstance(validation)));
                }
            }
        }

        fieldConfig.setValidationConfigs(validationConfigs);
        return fieldConfig;
    }

    public FieldConfig mapFunctionsToField(FieldConfig fieldConfig,
                                           List<MapfunctionsBean> mapFunctions) {
        List<MapFunctionConfig> mapFunctionConfigs = new ArrayList<>();
        if (mapFunctions != null) {
            for (MapfunctionsBean function : mapFunctions) {
                if (function.getFieldname().equals(fieldConfig.getFieldname())) {
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
