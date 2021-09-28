package org.nexial.core.tools.swagger;

import java.util.List;

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
    private List<String> statusTextVariables;
    private String scenarioName;

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

    public List<String> getStatusTextVariables() {
        return statusTextVariables;
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
               ", statusTextVariables=" + statusTextVariables +
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
        this.responseVariable =
        responseVariable;
        return this;
    }

    public ResponseStepAttributes withStatusTextVariables(List<String> statusTextVariables) {
        this.statusTextVariables =
        statusTextVariables;
        return this;
    }

    public ResponseStepAttributes withScenarioName(String scenarioName) {
        this.scenarioName =
        scenarioName;
        return this;
    }
}