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

import java.util.List;

public class RecordConfig {

    private List<FieldConfig> fieldConfigList;
    private List<MapFunctionConfig> mapFunctionConfigs;
    private String fieldSeparator;
    private String recordIdFiled;
    private String recordId;

    private RecordConfig() {}

    public List<FieldConfig> getFieldConfigList() {
        return fieldConfigList;
    }

    public List<MapFunctionConfig> getMapFunctionConfigs() {
        return mapFunctionConfigs;
    }

    public String getFieldSeparator() {
        return fieldSeparator;
    }

    public String getRecordIdFiled() {
        return recordIdFiled;
    }

    public String getRecordId() {
        return recordId;
    }

    public static class RecordConfigBuilder {
        private List<FieldConfig> fieldConfigList;
        private List<MapFunctionConfig> mapFunctionConfigs;
        private String fieldSeparator;
        private String recordIdField;
        private String recordId;

        public RecordConfigBuilder fieldConfigList(List<FieldConfig> fieldConfigs) {
            this.fieldConfigList = fieldConfigs;
            return this;
        }

        public RecordConfigBuilder mapFunctionConfigs(List<MapFunctionConfig> mapFunctionConfigs) {
            this.mapFunctionConfigs = mapFunctionConfigs;
            return this;
        }

        public RecordConfigBuilder fieldSeparator(String fieldSeparator) {
            this.fieldSeparator = fieldSeparator;
            return this;
        }

        public RecordConfigBuilder recordIdField(String recordIdField) {
            this.recordIdField = recordIdField;
            return this;
        }

        public RecordConfigBuilder recordId(String recordId) {
            this.recordId = recordId;
            return this;
        }

        public RecordConfig build() {
            RecordConfig recordConfig = new RecordConfig();
            recordConfig.fieldConfigList = this.fieldConfigList;
            recordConfig.mapFunctionConfigs = this.mapFunctionConfigs;
            recordConfig.fieldSeparator = this.fieldSeparator;
            recordConfig.recordIdFiled = this.recordIdField;
            recordConfig.recordId = this.recordId;

            return recordConfig;
        }


    }

    @Override
    public String toString() {
        return "RecordConfig{fieldConfigList=" + fieldConfigList +
               ", mapFunctionConfigs=" + mapFunctionConfigs +
               ", fieldSeparator='" + fieldSeparator + '\'' +
               ", recordIdFiled='" + recordIdFiled + '\'' +
               ", recordId='" + recordId + '\'' +
               '}';
    }
}
