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

package org.nexial.core.tools.swagger;

import org.json.JSONArray;
import org.json.JSONObject;

public class ParameterGenerationAttributes {
    private String path;
    private String method;
    private String response;
    private JSONArray parameters;
    private JSONArray parentParams;
    private JSONObject parameterSchemas;
    private String varName;
    private String scenarioName;

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public String getResponse() {
        return response;
    }

    public JSONArray getParameters() {
        return parameters;
    }

    public JSONArray getParentParams() {
        return parentParams;
    }

    public JSONObject getParameterSchemas() {
        return parameterSchemas;
    }

    public String getVarName() {
        return varName;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public ParameterGenerationAttributes withPath(String path) {
        this.path = path;
        return this;
    }

    public ParameterGenerationAttributes withMethod(String method) {
        this.method = method;
        return this;
    }

    public ParameterGenerationAttributes withResponse(String response) {
        this.response = response;
        return this;
    }

    public ParameterGenerationAttributes withParameters(JSONArray parameters) {
        this.parameters =
        parameters;
        return this;
    }

    public ParameterGenerationAttributes withParentParams(JSONArray parentParams) {
        this.parentParams =
        parentParams;
        return this;
    }

    public ParameterGenerationAttributes withParameterSchemas(JSONObject parameterSchemas) {
        this.parameterSchemas =
        parameterSchemas;
        return this;
    }

    public ParameterGenerationAttributes withVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public ParameterGenerationAttributes withScenarioName(String scenarioName) {
        this.scenarioName =
        scenarioName;
        return this;
    }
}
