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

package org.nexial.core.plugins.filevalidation.config;

public class MapFunctionConfig {

    private String fieldName;
    private String signField;
    private String function;
    private String mapTo;

    private MapFunctionConfig(String fieldName, String signField, String function, String mapTo) {
        this.fieldName = fieldName;
        this.signField = signField;
        this.function = function;
        this.mapTo = mapTo;
    }

    public static MapFunctionConfig newInstance(MapfunctionsBean function) {
        return new MapFunctionConfig(function.getFieldname(),
                                     function.getSignfield(),
                                     function.getFunction(),
                                     function.getMapTo());
    }

    public String getSignField() {
        return signField;
    }

    public void setSignField(String signField) {
        this.signField = signField;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getMapTo() {
        return mapTo;
    }

    public void setMapTo(String mapTo) {
        this.mapTo = mapTo;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String toString() {
        return "MapFunctionConfig{fieldName='" + fieldName + '\'' +
               ", signField='" + signField + '\'' +
               ", function='" + function + '\'' +
               ", mapTo='" + mapTo + '\'' +
               '}';
    }
}
