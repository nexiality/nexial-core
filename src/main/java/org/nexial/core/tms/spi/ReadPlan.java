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

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.jetbrains.annotations.NotNull;
import org.nexial.core.ExecutionInputPrep;
import org.nexial.core.Nexial;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.TestProject;
import org.nexial.core.tms.model.TmsTestCase;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.InputFileUtils;

import static org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP;
import static org.nexial.core.excel.ExcelConfig.ADDR_PLAN_EXECUTION_START;

/**
 * Parse the Nexial Plan file and return the test cases accordingly
 */
public class ReadPlan extends Nexial {

    protected static Map<String, String> scriptToStep;

    /**
     * Parse the specified nexial plan file for the specified sub plan and return the test cases associated with each plan
     *  step
     *
     * @param testPlanPath the path of the plan file
     * @param subplan the subplan name
     * @return an {@link Map} of the test cases associated with the plan steps in the plan
     */
    public static LinkedHashMap<String, List<TmsTestCase>> loadPlan(String testPlanPath, String subplan) {
        Nexial nexial = new Nexial();
        File testPlanFile = new File(testPlanPath);
        TestProject project = getTestProject(testPlanPath, testPlanFile);
        Excel excel = null;
        try {
            excel = new Excel(testPlanFile, DEF_OPEN_EXCEL_AS_DUP, false);
        } catch (IOException e) {
            System.err.println("Unable to read excel file: " + e.getMessage());
            System.exit(-1);
        }
        List<Worksheet> plans = nexial.retrieveValidPlans(null, excel);

        Worksheet subplanWorksheet = plans.stream().filter(plan -> plan.getName().equals(subplan)).findFirst().get();
        if (plans.stream().noneMatch(plan -> plan.getName().equals(subplan))) {
            throw new IllegalArgumentException("The sub plan name provided does not exist in plan " + testPlanPath);
        }

        List<ExecutionDefinition> executions = getExecutions(testPlanPath, nexial, testPlanFile, project, subplanWorksheet);
        return getSubPlanMap(executions);
    }

    /**
     * Derive the {@link TestProject} for the plan file
     *
     * @param testPlanPath the path of the plan file
     * @param testPlanFile the name of the plan file
     * @return TestProject instance for the specified plan
     */
    @NotNull
    private static TestProject getTestProject(String testPlanPath, File testPlanFile) {
        if (!InputFileUtils.isValidPlanFile(testPlanPath)) {
            throw new RuntimeException(
                    "specified test plan (" + testPlanPath + ") is not readable or does not contain valid format.");
        }
        TestProject project = TestProject.newInstance(testPlanFile);
        if (!project.isStandardStructure()) {
            ConsoleUtils.log("specified plan (" + testPlanFile + ") not following standard project " +
                             "structure, related directories would not be resolved from commandline arguments.");
        }
        return project;
    }

    /**
     * Return a {@link List} of {@link ExecutionDefinition}s. Each instance representing a single plan step
     *
     * @param testPlanPath the path of the plan file
     * @param nexial {@link Nexial} instance
     * @param testPlanFile the test plan file
     * @param project the TestProject for the plan file
     * @param subplan the sub plan name
     * @return List of {@link ExecutionDefinition}s
     */
    private static List<ExecutionDefinition> getExecutions(String testPlanPath, Nexial nexial, File testPlanFile,
                                                           TestProject project,
                                                           Worksheet subplan) {
        int rowStartIndex = ADDR_PLAN_EXECUTION_START.getRowStartIndex();
        int lastExecutionRow = subplan.findLastDataRow(ADDR_PLAN_EXECUTION_START);
        // map to store the script association with plan step (row number)
        scriptToStep = new HashMap<>();
        List<ExecutionDefinition> executions = new ArrayList<>();
        //parsing the subplan sheet
        for (int i = rowStartIndex; i < lastExecutionRow; i++) {
            XSSFRow row = subplan.getSheet().getRow(i);
            String msgSuffix = " specified in ROW " + (row.getRowNum() + 1) +
                               " of " + subplan.getName() + " in " + testPlanFile;

            if (ExecutionInputPrep.isPlanStepDisabled(row)) { continue; }

            File testScript = nexial.deriveScriptFromPlan(row, project, testPlanPath);

            if (!InputFileUtils.isValidScript(testScript.getAbsolutePath())) {
                throw new RuntimeException("Invalid/unreadable test script" + msgSuffix);
            }
            String rowNum = String.valueOf(row.getRowNum() + 1);
            // storing the script to step association
            scriptToStep.put(rowNum, testScript.getAbsolutePath());
            List<String> scenarios = nexial.deriveScenarioFromPlan(row, nexial.deriveScenarios(testScript));

            ExecutionDefinition exec = new ExecutionDefinition();
            exec.setPlanFile(subplan.getFile().getAbsolutePath());
            exec.setTestScript(testScript.getAbsolutePath());
            exec.setScenarios(scenarios);
            // using the description field to store the row number for each execution
            exec.setDescription(rowNum);
            executions.add(exec);
        }
        return executions;
    }

    /**
     * Loop over the executions present for the plan and retrieve the test cases belonging to the scripts specified in the
     * plan steps
     *
     * @param executions List of {@link ExecutionDefinition} representing the plan steps
     * @return a {@link Map} of the test cases associated with each plan step
     */
    private static LinkedHashMap<String, List<TmsTestCase>> getSubPlanMap(List<ExecutionDefinition> executions) {
        LinkedHashMap<String, List<TmsTestCase>> testCasesToPlanStep = new LinkedHashMap<>();
        for (ExecutionDefinition exec : executions) {
            List<TmsTestCase> testCases = ReadScript.loadScript(exec.getTestScript());
            resolveTestCases(testCases, exec);
            testCasesToPlanStep.put(exec.getDescription(), testCases);
        }
        return testCasesToPlanStep;
    }

    /**
     * Filter out the scenarios not specified in the plan step for each execution
     *
     * @param testCases {@link List} of test cases. In this case it represents the scenarios present in the script mentioned
     *                              in the plan step
     * @param exec {@link ExecutionDefinition} instance representing single plan step
     */
    private static void resolveTestCases(List<TmsTestCase> testCases, ExecutionDefinition exec) {
        List<String> execScenarios = exec.getScenarios();
        Iterator<TmsTestCase> iterator = testCases.iterator();
        while (iterator.hasNext()) {
            TmsTestCase testCase = iterator.next();
            testCase.setRow(exec.getDescription());
            // removing scenarios not specified in execution
            if (!execScenarios.contains(testCase.getName())) { iterator.remove(); }
        }
    }
}
