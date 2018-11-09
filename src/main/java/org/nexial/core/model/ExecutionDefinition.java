/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.model;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.nexial.core.excel.Excel;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP;

public class ExecutionDefinition {
    private String description;
    private String testScript;
    private List<String> scenarios;
    private Excel dataFile;
    private List<String> dataSheets;
    private boolean failFast = true;
    private boolean serialMode = true;
    private boolean loadTestMode = false;
    private int minimumLoad;
    private int maximumLoad;
    private int rampUpSec;
    private int holdForSec;

    private TestProject project;
    private String runId;
    private TestData testData;

    private String planFilename;
    private String planName;
    private int planSequence;

    public ExecutionDefinition() { }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getTestScript() { return testScript; }

    public void setTestScript(String testScript) { this.testScript = testScript; }

    public List<String> getScenarios() { return scenarios; }

    public void setScenarios(List<String> scenarios) { this.scenarios = scenarios; }

    public Excel getDataFile() { return dataFile; }

    public void setDataFile(Excel dataFile) { this.dataFile = dataFile; }

    public List<String> getDataSheets() { return dataSheets; }

    public void setDataSheets(List<String> dataSheets) { this.dataSheets = dataSheets; }

    public boolean isFailFast() { return failFast; }

    public void setFailFast(boolean failFast) { this.failFast = failFast; }

    public boolean isSerialMode() { return serialMode; }

    public void setSerialMode(boolean serialMode) { this.serialMode = serialMode; }

    public boolean isLoadTestMode() { return loadTestMode; }

    public void setLoadTestMode(boolean loadTestMode) { this.loadTestMode = loadTestMode; }

    public int getMinimumLoad() { return minimumLoad; }

    public void setMinimumLoad(int minimumLoad) { this.minimumLoad = minimumLoad; }

    public int getMaximumLoad() { return maximumLoad; }

    public void setMaximumLoad(int maximumLoad) { this.maximumLoad = maximumLoad; }

    public int getRampUpSec() { return rampUpSec; }

    public void setRampUpSec(int rampUpSec) { this.rampUpSec = rampUpSec; }

    public int getHoldForSec() { return holdForSec; }

    public void setHoldForSec(int holdForSec) { this.holdForSec = holdForSec; }

    public TestProject getProject() { return project; }

    public void setProject(TestProject project) { this.project = project; }

    public String getOutPath() { return project.getOutPath(); }

    public TestData getTestData(boolean refetch) {
        if (refetch) {
            try {
                dataFile = new Excel(dataFile.getFile(), DEF_OPEN_EXCEL_AS_DUP, false);
                parse();
            } catch (IOException e) {
                String error = "Unable to successfully read/parse data file " + dataFile + ": " + e.getMessage();
                ConsoleUtils.error(error);
                throw new RuntimeException(error, e);
            }
        }

        return testData;
    }

    public TestData getTestData() { return testData; }

    public String getRunId() { return runId; }

    public void setRunId(String runId) { this.runId = runId; }

    public String getPlanFilename() { return planFilename; }

    public void setPlanFilename(String planFilename) { this.planFilename = planFilename; }

    public String getPlanName() { return planName; }

    public void setPlanName(String planName) { this.planName = planName; }

    public int getPlanSequence() { return planSequence; }

    public void setPlanSequence(int planSequence) { this.planSequence = planSequence; }

    public void parse() {
        // parse and collect all relevant test data so we can merge then into iteration-bound test script
        testData = new TestData(dataFile, dataSheets);
    }

    public void infuseIntraExecutionData(Map<String, Object> intraExecutionData) {
        testData.infuseIntraExecutionData(intraExecutionData);
    }

}
