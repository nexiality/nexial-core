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

package org.nexial.core.plugins.filevalidation.config;

import java.util.List;

public class FieldConfig {
    private String fieldname;
    private String datatype;
    private int fieldlength;
    private int positionfrom;
    private int positionto;
    private String alignment;

    private List<ValidationConfig> validationConfigs;
    private List<MapFunctionConfig> mapFunctionConfigs;

    public String getFieldname() {
        return fieldname;
    }

    public void setFieldname(String fieldname) {
        this.fieldname = fieldname;
    }

    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        this.datatype = datatype;
    }

    public int getFieldlength() {
        return fieldlength;
    }

    public void setFieldlength(int fieldlength) {
        this.fieldlength = fieldlength;
    }

    public int getPositionfrom() {
        return positionfrom;
    }

    public void setPositionfrom(int positionfrom) {
        this.positionfrom = positionfrom;
    }

    public int getPositionto() {
        return positionto;
    }

    public void setPositionto(int positionto) {
        this.positionto = positionto;
    }

    public String getAlignment() {
        return alignment;
    }

    public void setAlignment(String alignment) {
        this.alignment = alignment;
    }

    public List<ValidationConfig> getValidationConfigs() {
        return validationConfigs;
    }

    public void setValidationConfigs(List<ValidationConfig> validationConfigs) {
        this.validationConfigs = validationConfigs;
    }

    public List<MapFunctionConfig> getMapFunctionConfigs() {
        return mapFunctionConfigs;
    }

    public void setMapFunctionConfigs(List<MapFunctionConfig> mapFunctionConfigs) {
        this.mapFunctionConfigs = mapFunctionConfigs;
    }

    @Override
    public String toString() {
        return "FieldConfig{fieldname='" + fieldname + '\'' +
               ", datatype='" + datatype + '\'' +
               ", fieldlength=" + fieldlength +
               ", positionfrom=" + positionfrom +
               ", positionto=" + positionto +
               ", alignment='" + alignment + '\'' +
               ", validationConfigs=" + validationConfigs +
               ", mapFunctionConfigs=" + mapFunctionConfigs +
               '}';
    }
}
