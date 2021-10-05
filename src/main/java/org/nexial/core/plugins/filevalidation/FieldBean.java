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

package org.nexial.core.plugins.filevalidation;

import java.util.List;

import org.nexial.core.plugins.filevalidation.config.FieldConfig;
import org.nexial.core.plugins.filevalidation.validators.Error;

public class FieldBean {

    private FieldConfig config;
    private String fieldValue;
    private transient List<Error> errors;
    private transient boolean dataTypeError;
    private transient boolean textAlignmentError;

    private RecordBean record;

    public FieldBean(FieldConfig fieldConfig, String fieldValue) {
        this.config = fieldConfig;
        this.fieldValue = fieldValue;
    }

    public RecordBean getRecord() {
        return record;
    }

    public void setRecord(RecordBean record) {
        this.record = record;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public List<Error> getErrors() {
        return errors;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public boolean isDataTypeError() {
        return dataTypeError;
    }

    public void setDataTypeError(boolean dataTypeError) {
        this.dataTypeError = dataTypeError;
    }

    public boolean isTextAlignmentError() {
        return textAlignmentError;
    }

    public void setTextAlignmentError(boolean textAlignmentError) {
        this.textAlignmentError = textAlignmentError;
    }

    public FieldConfig getConfig() {
        return config;
    }

    public void setConfig(FieldConfig config) {
        this.config = config;
    }

    @Override
    public String toString() {
        return "FieldBean{, config=" + config + ", fieldValue='" + fieldValue + "', errors=" + errors + '}';
    }
}
