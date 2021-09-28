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
