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

public class MethodInvocationStepAttributes {
    private String method;
    private String requestBody;
    private boolean newActivity;
    private String responseVariable;
    private String responseDescription;
    private String activityName;
    private String pathString;
    private String queryParamString;
    private String response;

    public String getMethod() {
        return method;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public boolean isNewActivity() {
        return newActivity;
    }

    public String getResponseVariable() {
        return responseVariable;
    }

    public String getResponseDescription() {
        return responseDescription;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getPathString() {
        return pathString;
    }

    public String getQueryParamString() {
        return queryParamString;
    }

    public String getResponse() {
        return response;
    }


    public MethodInvocationStepAttributes withMethod(String method) {
        this.method = method;
        return this;
    }

    public MethodInvocationStepAttributes withRequestBody(String requestBody) {
        this.requestBody =
        requestBody;
        return this;
    }

    public MethodInvocationStepAttributes withNewActivity(boolean newActivity) {
        this.newActivity =
        newActivity;
        return this;
    }

    public MethodInvocationStepAttributes withResponseVariable(String responseVariable) {
        this.responseVariable =
        responseVariable;
        return this;
    }

    public MethodInvocationStepAttributes withResponseDescription(String responseDescription) {
        this.responseDescription =
        responseDescription;
        return this;
    }

    public MethodInvocationStepAttributes withActivityName(String activityName) {
        this.activityName =
        activityName;
        return this;
    }

    public MethodInvocationStepAttributes withPathString(String pathString) {
        this.pathString =
        pathString;
        return this;
    }

    public MethodInvocationStepAttributes withQueryParamString(String queryParamString) {
        this.queryParamString =
        queryParamString;
        return this;
    }

    public MethodInvocationStepAttributes withResponse(String response) {
        this.response = response;
        return this;
    }
}
