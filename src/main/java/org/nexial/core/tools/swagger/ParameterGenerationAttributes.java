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
