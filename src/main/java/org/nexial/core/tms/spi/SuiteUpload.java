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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.nexial.core.tms.model.TmsSuite;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.tms.TmsConst.ID;

/**
 * Create a new suite (fresh import) for the nexial test file passed in as an argument.
 */
public class SuiteUpload {

    private final Map<Integer, Map<String, String>> testCaseToStep = new HashMap<>();
    private final String suiteName;
    private final String testPath;
    private final TMSOperation tms;

    public SuiteUpload(String suiteName, String testPath, TMSOperation tms) {
//        tms = new TmsFactory().getTmsInstance(projectId);
        this.tms = tms;
        this.suiteName = suiteName;
        this.testPath = testPath;
    }

    public Map<Integer, Map<String, String>> getTestCaseToStep() { return testCaseToStep; }

    /**
     * Create a new suite for the Nexial script file passed in as an argument.
     *
     * @param testCases List of valid {@link TmsTestCase} instances for each scenario inside the script
     * @return TmsSuite object containing details of the newly created suite
     */
    protected TmsSuite uploadScript(List<TmsTestCase> testCases) throws TmsException {
        TmsSuite tmsSuite = tms.createSuite(suiteName, testPath);
//        JSONObject tmsSuite = new JSONObject();
        String suiteId = tmsSuite.getId();
//        String suiteId = "3";
        ConsoleUtils.log("Test suite created with id: " + suiteId);
        String sectionId = tms.addSection(suiteId, suiteName);
        Map<String, String> testCaseToScenario = tms.addCases(sectionId, testCases);
//        Map<String, String> testCaseToScenario = tms.addCases("4", testCases);
        tmsSuite.setTestCases(testCaseToScenario);
        return tmsSuite;
    }

    /**
     * Create a new suite for the plan file passed in
     *
     * @param testCasesToPlanStep {@link Map} of {@link TmsTestCase} instance representing the scenarios
     *        specified in each plan step
     * @return {@link TmsSuite} object containing details of the newly created suite
     */
    protected TmsSuite uploadPlan(LinkedHashMap<Integer, List<TmsTestCase>> testCasesToPlanStep) throws TmsException {
        try {
            TmsSuite tmsSuite = tms.createSuite(suiteName, testPath);
            String suiteId = tmsSuite.getId();
            String sectionId = tms.addSection(suiteId, suiteName);

            Map<String, String> newTestCaseResponse = new HashMap<>();
            for (Map.Entry<Integer, List<TmsTestCase>> entry : testCasesToPlanStep.entrySet()) {
                Integer row = entry.getKey();
                List<TmsTestCase> testCases = entry.getValue();
                Map<String, String> caseToScenario = tms.addCases(sectionId, testCases);
                newTestCaseResponse.putAll(caseToScenario);
                testCaseToStep.put(row, caseToScenario);
            }
            tmsSuite.setTestCases(newTestCaseResponse);
            return tmsSuite;
        } catch (Exception e) {
            throw new TmsException("Unable to upload complete suite due to " + e.getMessage());
        }
    }
}
