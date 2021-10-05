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
