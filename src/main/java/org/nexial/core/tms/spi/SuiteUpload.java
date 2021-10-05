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

package org.nexial.core.tms.spi;

import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONObject;
import org.nexial.core.tms.TMSOperation;
import org.nexial.core.tms.model.TmsSuite;
import org.nexial.core.tms.model.TmsTestCase;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.nexial.core.excel.ExcelConfig.ADDR_PLAN_EXECUTION_START;

/**
 * Create a new suite (fresh import) for the nexial test file passed in as argument
 */
public class SuiteUpload {

    private String planPath;
    private String subplan;
    private Map<String, Map<String, String>> testCaseToStep;
    private TMSOperation tms;

    public SuiteUpload(String projectId) {
        tms = new TmsFactory().getTmsInstance(projectId);
    }

    public SuiteUpload(String planPath, String subplan, String projectId) {
        this.planPath = planPath;
        this.subplan = subplan;
        tms = new TmsFactory().getTmsInstance(projectId);
    }

    public Map<String, Map<String, String>> getTestCaseToStep() {
        return testCaseToStep;
    }

    /**
     * Create a new suite for the Nexial script file passed in
     *
     * @param testCases  List of valid {@link TmsTestCase} instances for each scenario inside the script
     * @param scriptName name of the script file
     * @return TmsSuite object containing details of the newly created suite
     */
    protected TmsSuite uploadScript(List<TmsTestCase> testCases,
                                    String scriptName) {
        JSONObject createSuiteResponse = tms.createSuite(scriptName);
        String id = createSuiteResponse.get("id").toString();
        String url = createSuiteResponse.get("url").toString();

        JSONObject addSectionResponse = tms.addSection(scriptName, id);
        Map<String, String> testCaseToScenario = tms.addCases(addSectionResponse, testCases);

        return new TmsSuite(scriptName, id, testCaseToScenario, url);
    }

    /**
     * Create a new suite for the plan file passed in
     *
     * @param testCasesToPlanStep {@link Map} of {@link TmsTestCase} instance representing the scenarios specified in
     *                            each plan step
     * @return TmsSuite object containing details of the newly created suite
     */
    protected TmsSuite uploadPlan(LinkedHashMap<String, List<TmsTestCase>> testCasesToPlanStep) {
        TmsSuite suite = null;
        testCaseToStep = new HashMap<>();
        try {
            int i = ADDR_PLAN_EXECUTION_START.getRowStartIndex() + 1; // row number to plan test cases
            String suiteName = FilenameUtils.removeExtension(new File(planPath).getName());
            suiteName = suiteName + "/" + subplan; // generate the suite name (<plan name>/<subplan name>)
            Map<String, String> addCasesResponseList = new HashMap<>();
            JSONObject createSuiteResponse = tms.createSuite(suiteName);
            JSONObject addSectionResponse =
                tms.addSection(suiteName, createSuiteResponse.get("id").toString());
            for (String row : testCasesToPlanStep.keySet()) {
                Map<String, String> caseToScenario =
                    tms.addCases(addSectionResponse, testCasesToPlanStep.get(row));
                addCasesResponseList.putAll(caseToScenario);
                testCaseToStep.put(String.valueOf(i), caseToScenario);
                i++;
            }
            suite = new TmsSuite(suiteName, createSuiteResponse.get("id").toString(), addCasesResponseList,
                                 createSuiteResponse.get("url").toString());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return suite;
    }
}
