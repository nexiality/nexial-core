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

import org.nexial.core.plugins.filevalidation.config.ValidationsBean.ValidationmethodsBean;
import org.nexial.core.plugins.filevalidation.config.ValidationsBean.ValidationmethodsBean.ConditionBean;

import com.google.gson.JsonElement;

public class ValidationConfig {

    private String type;
    private JsonElement params;
    private List<ConditionBean> conditionBeans;

    private ValidationConfig(String type, JsonElement params, List<ConditionBean> conditionBeans) {
        this.type = type;
        this.params = params;
        this.conditionBeans = conditionBeans;
    }

    public static ValidationConfig newInstance(ValidationmethodsBean validation) {
        return new ValidationConfig(validation.getType(), validation.getParams(), validation.getConditionBeans());
    }

    public List<ConditionBean> getConditionBeans() {
        return conditionBeans;
    }

    public void setConditionBeans(List<ConditionBean> conditionBeans) {
        this.conditionBeans = conditionBeans;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JsonElement getParams() {
        return params;
    }

    public void setParams(JsonElement params) {
        this.params = params;
    }

    @Override
    public String toString() {
        return "ValidationConfig{type='" + type + '\'' +
               ", params=" + params +
               ", conditionBeans=" + conditionBeans +
               '}';
    }
}
