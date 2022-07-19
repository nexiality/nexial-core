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

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.jetbrains.annotations.NotNull;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionInputPrep;
import org.nexial.core.NexialConst.RB;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.model.TestProject;
import org.nexial.core.tms.model.TmsCustomStep;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.tms.model.TmsTestStep;
import org.nexial.core.utils.InputFileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.tms.TmsConst.NAT;

/**
 * Parse a script file and return the List of Test Cases by reading the scenarios.
 */
public class ReadScript {
    /**
     * Parse the script file specified by the filepath and return the {@link List} of Test Cases for it.
     *
     * @param filepath the path of the script file
     * @return List of test cases mapped to scenarios
     */
    public static List<TmsTestCase> loadScript(String filepath) throws TmsException {
        List<TmsTestCase> testCases = new ArrayList<>();
        try {
            Excel script = getScript(filepath);
            List<String> scenarioNames = InputFileUtils.retrieveValidTestScenarioNames(script);
            // parse the script file for every scenario and create TmsTestCase objects
            for (String scenarioName : scenarioNames) {
                Worksheet worksheet = script.worksheet(scenarioName);
                TmsTestCase testCase = new TmsTestCase(worksheet);
                if (StringUtils.startsWith(testCase.getName().toLowerCase(), NAT)) { continue; }
                List<TmsTestStep> activities = parseScenario(testCase, worksheet);
                testCase.setTestSteps(activities);
                testCase.setCache();
                testCases.add(testCase);
            }
        } catch (Exception e) {
            throw new TmsException("Error occurred while parsing the excel file: " + filepath + " : " + e.getMessage());
        }
        return testCases;
    }

    /**
     * Perform validations on the path provided and return and {@link Excel} instance of the Script file
     *
     * @param testScriptPath path of the script file
     * @return Excel instance of script file
     */
    @NotNull
    protected static Excel getScript(String testScriptPath) throws TmsException {
        File testScriptFile = new File(testScriptPath);
        if (!testScriptFile.exists()) {
            throw new TmsException("The path specified does not exist");
        }

        Excel script = InputFileUtils.resolveValidScript(testScriptPath);
        if (script == null) {
            throw new TmsException("Invalid test script - " + testScriptPath);
        }

        TestProject project = TestProject.newInstance(testScriptFile);
        if (!project.isStandardStructure()) {
            throw new TmsException("specified test script (" + testScriptFile + ") " +
                                   "not following standard project structure, related directories " +
                                   "would not be resolved from commandline arguments.");

        }

        return script;
    }

    /**
     * Parse each scenario present in the script and return a list of the test steps(activities) inside the scenario.
     *
     * @param scenario  A scenario in the Nexial script, mapped to a single Test Case
     * @param worksheet the current {@link Worksheet}
     * @return List of {@link TmsTestStep} belonging to the scenario
     */
    private static List<TmsTestStep> parseScenario(TmsTestCase scenario, Worksheet worksheet) throws TmsException {
        int lastCommandRow = worksheet.findLastDataRow(ADDR_COMMAND_START);
        ExcelArea area = new ExcelArea(worksheet,
                                       new ExcelAddress(FIRST_STEP_ROW + ":" + COL_REASON + lastCommandRow),
                                       false);
        List<TmsTestStep> testSteps = new ArrayList<>();
        List<String> testCases = new ArrayList<>();

        String scenarioRef = "Error found in [" + worksheet.getFile().getName() + "][" + worksheet.getName() + "]";

        // 3. parse for test steps->test case grouping
        TmsTestStep currentActivity = null;
        for (int i = 0; i < area.getWholeArea().size(); i++) {
            List<XSSFCell> row = area.getWholeArea().get(i);

            XSSFCell cellActivity = row.get(COL_IDX_TESTCASE);
            String errorPrefix = scenarioRef + "[" + cellActivity.getReference() + "]: ";
            String activity = Excel.getCellValue(cellActivity);

            // detect space only activity name
            if (StringUtils.isNotEmpty(activity) && StringUtils.isAllBlank(activity)) {
                throw new TmsException(errorPrefix + "Found invalid, space-only activity name");
            }

            // detect leading/trailing non-printable characters
            if (!StringUtils.equals(activity, StringUtils.trim(activity))) {
                throw new TmsException(errorPrefix + RB.Fatal.text("problematicName") + "activity " + activity);
            }

            boolean hasActivity = StringUtils.isNotBlank(activity);
            if (currentActivity == null && !hasActivity) {
                // first row must define test case (hence at least 1 test case is required)
                throw new TmsException(errorPrefix + "Invalid format; First row must contain valid activity name");
            }
            if (hasActivity) {
                if (testCases.contains(activity)) {
                    // found duplicate activity name!
                    throw new TmsException(errorPrefix + "Found duplicate activity name '" + activity + "'");

                }
                testCases.add(activity);
                currentActivity = new TmsTestStep(scenario, TextUtils.toOneLine(activity, true));
                testSteps.add(currentActivity);
            }
            if(ExecutionInputPrep.isTestStepDisabled(row)) { continue; }
            TmsCustomStep testStep = new TmsCustomStep(row, currentActivity);
            currentActivity.addTmsCustomTestStep(testStep);
        }

        return testSteps;
    }
}
