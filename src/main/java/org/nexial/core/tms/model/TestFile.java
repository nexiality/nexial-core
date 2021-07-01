package org.nexial.core.tms.model;

import java.util.List;

/**
 * Represents a single file entry inside the file {@link List} inside TMS Meta json for the project
 */
public class TestFile {
    private String path;
    private String fileType;
    private String suiteId;
    private String suiteUrl;
    private String stepId;
    private String subStep;
    private List<TestFile> planSteps;
    private List<Scenario> scenarios;
    private transient String projectId;

    public TestFile(String path, String fileType, String suiteId, String subStep,
                    List<TestFile> planSteps, List<Scenario> scenarios, String suiteUrl, String stepId) {
        this.path      = path;
        this.fileType  = fileType;
        this.suiteId   = suiteId;
        this.subStep   = subStep;
        this.planSteps = planSteps;
        this.scenarios = scenarios;
        this.suiteUrl  = suiteUrl;
        this.stepId    = stepId;
    }

    public TestFile() {
    }

    public String getProjectId() {
        return projectId;
    }

    public String getSubStep() {
        return subStep;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public List<TestFile> getPlanSteps() {
        return planSteps;
    }

    public void setPlanSteps(List<TestFile> planSteps) {
        this.planSteps = planSteps;
    }

    public List<Scenario> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<Scenario> scenarios) {
        this.scenarios = scenarios;
    }

    public String getSuiteUrl() {
        return suiteUrl;
    }

    public String getStepId() {
        return stepId;
    }

    public String getSuiteId() {
        return suiteId;
    }

    public String getPath() {
        return path;
    }

    public String getFileType() {
        return fileType;
    }
}
