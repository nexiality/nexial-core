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

import org.json.JSONObject;

public class StepAttributes {
    private String path;
    private String method;
    private JSONObject responseParameters;
    private JSONObject params;
    private String requestJSONBodyFile;
    private String varName;
    private String scenarioName;
    private String warningMessage;
    private String contentType;

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public JSONObject getResponseParameters() {
        return responseParameters;
    }

    public JSONObject getParams() {
        return params;
    }

    public String getRequestJSONBodyFile() {
        return requestJSONBodyFile;
    }

    public String getVarName() {
        return varName;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public String getContentType() {
        return contentType;
    }

    public StepAttributes withPath(String path) {
        this.path = path;
        return this;
    }

    public StepAttributes withMethod(String method) {
        this.method = method;
        return this;
    }

    public StepAttributes withResponseParameters(JSONObject responseParameters) {
        this.responseParameters =
        responseParameters;
        return this;
    }

    public StepAttributes withParams(JSONObject params) {
        this.params = params;
        return this;
    }

    public StepAttributes withRequestJSONBodyFile(String requestJSONBodyFile) {
        this.requestJSONBodyFile =
        requestJSONBodyFile;
        return this;
    }

    public StepAttributes withVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public StepAttributes withScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
        return this;
    }

    public StepAttributes withWarningMessage(String warningMessage) {
        this.warningMessage =
        warningMessage;
        return this;
    }

    public StepAttributes withContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }
}
