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

/**
 * Class containing the attributes needed to generate the Response Validaton steps.
 */
public class ResponseStepAttributes {
    private String path;
    private JSONObject responseParameters;
    private String response;
    private String activityName;
    private String responseVariable;
    private String scenarioName;
    private String statusTextVar;

    public String getPath() {
        return path;
    }

    public JSONObject getResponseParameters() {
        return responseParameters;
    }

    public String getResponse() {
        return response;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getResponseVariable() {
        return responseVariable;
    }

    public String getStatusTextVar() {
        return statusTextVar;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    @Override
    public String toString() {
        return "ResponseStepAttributes{" +
               "path='" + path + '\'' +
               ", responseParameters=" + responseParameters +
               ", response='" + response + '\'' +
               ", activityName='" + activityName + '\'' +
               ", responseVariable='" + responseVariable + '\'' +
               ", statusTextVar=" + statusTextVar +
               ", scenarioName='" + scenarioName + '\'' +
               '}';
    }

    public ResponseStepAttributes withPath(String path) {
        this.path = path;
        return this;
    }

    public ResponseStepAttributes withResponseParameters(JSONObject responseParameters) {
        this.responseParameters =
        responseParameters;
        return this;
    }

    public ResponseStepAttributes withResponse(String response) {
        this.response =
        response;
        return this;
    }

    public ResponseStepAttributes withActivityName(String activityName) {
        this.activityName =
        activityName;
        return this;
    }

    public ResponseStepAttributes withResponseVariable(String responseVariable) {
        this.responseVariable = responseVariable;
        return this;
    }

    public ResponseStepAttributes withStatusTextVar(String statusTextVar) {
        this.statusTextVar = statusTextVar;
        return this;
    }

    public ResponseStepAttributes withScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
        return this;
    }
}