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
}
