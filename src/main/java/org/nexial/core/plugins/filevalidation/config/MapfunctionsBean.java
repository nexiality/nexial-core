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

import com.google.gson.annotations.SerializedName;

public class MapfunctionsBean {

    @SerializedName("field-name")
    private String fieldname;
    @SerializedName("sign-field")
    private String signfield;
    @SerializedName("function")
    private String function;
    @SerializedName("mapTo")
    private String mapTo;
    @SerializedName("condition")
    private String condition;

    public String getSignfield() {
        return signfield;
    }

    public void setSignfield(String signfield) {
        this.signfield = signfield;
    }

    public String getFieldname() { return fieldname;}

    public void setFieldname(String fieldname) { this.fieldname = fieldname;}

    public String getFunction() { return function;}

    public void setFunction(String function) { this.function = function;}

    public String getMapTo() { return mapTo;}

    public void setMapTo(String mapTo) { this.mapTo = mapTo;}

    public String getCondition() {
        return condition;
    }
}
