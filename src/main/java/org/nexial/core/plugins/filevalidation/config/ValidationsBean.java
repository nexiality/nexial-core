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

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

public class ValidationsBean {

    @SerializedName("field-name")
    private String fieldname;
    @SerializedName("validation-methods")
    private List<ValidationmethodsBean> validationmethods;

    public static class ValidationmethodsBean {

        @SerializedName("type")
        private String type;
        @SerializedName("params")
        private JsonElement params;
        @SerializedName("conditions")
        private List<ConditionBean> conditionBeans;

        public static class ConditionBean {

            @SerializedName("field-name")
            private String fieldname;
            @SerializedName("field-value")
            private String fieldvalue;

            public String getFieldname() { return fieldname;}

            public void setFieldname(String fieldname) {
                System.out.println("setting field name " + fieldname);
                this.fieldname = fieldname;
            }

            public String getFieldvalue() { return fieldvalue;}

            public void setFieldvalue(String fieldvalue) { this.fieldvalue = fieldvalue;}


        }


        public List<ConditionBean> getConditionBeans() {
            return conditionBeans;
        }

        public void setConditionBeans(List<ConditionBean> conditionBeans) {
            this.conditionBeans = conditionBeans;
        }

        public String getType() { return type;}

        public void setType(String type) { this.type = type;}

        public JsonElement getParams() {
            return params;
        }

        public void setParams(JsonElement params) {
            this.params = params;
        }
    }

    public String getFieldname() { return fieldname;}

    public void setFieldname(String fieldname) { this.fieldname = fieldname;}

    public List<ValidationmethodsBean> getValidationmethods() { return validationmethods;}

    public void setValidationmethods(List<ValidationmethodsBean> validationmethods) {
        this.validationmethods = validationmethods;
    }
}
