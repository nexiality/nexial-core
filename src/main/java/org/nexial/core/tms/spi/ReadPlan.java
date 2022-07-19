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
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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

import java.util.stream.Collectors;
import static org.nexial.core.NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP;
import static org.nexial.core.excel.ExcelConfig.ADDR_PLAN_EXECUTION_START;

/**
 * Parse the Nexial Plan file and return the test cases accordingly
 */
public class ReadPlan extends Nexial {
    protected static Map<Integer, String> scriptToStep;

    /**
     * Parse the specified nexial plan file for the specified sub plan and return the test cases associated with each plan
     * step
     *
     * @param testPlanPath the path of the plan file
     * @param subplan      the subplan name
     * @return an {@link Map} of the test cases associated with the plan steps in the plan
     */
    public static LinkedHashMap<Integer, List<TmsTestCase>> loadPlan(String testPlanPath, String subplan)
            throws TmsException {
        try {
            File testPlanFile = new File(testPlanPath);
            Excel excel = new Excel(testPlanFile, DEF_OPEN_EXCEL_AS_DUP, false);

            List<Worksheet> subplanSheets = InputFileUtils.retrieveValidPlanSequence(excel).stream()
                    .filter(splan -> StringUtils.equals(splan.getName(), subplan))
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(subplanSheets)) {
                throw new TmsException("Unable to find provide subplan '" + subplan +
                                               "' from file '" + testPlanPath + "'");
            }
            return getSubPlanMap(testPlanFile, subplanSheets.get(0));
        } catch (Exception e) {
            throw new TmsException("Unable to read excel file: " + e.getMessage());
        }
    }

    /**
     * Derive the {@link TestProject} for the plan file
     *
     * @param testPlanPath the path of the plan file
     * @param testPlanFile the name of the plan file
     * @return TestProject instance for the specified plan
     */
    @NotNull
    private static TestProject getTestProject(String testPlanPath, File testPlanFile) throws TmsException {
        if (!InputFileUtils.isValidPlanFile(testPlanPath)) {
            throw new TmsException("specified test plan (" + testPlanPath + ") is not readable or does not contain valid format.");
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
     * @param testPlanFile the path of the plan file
     * @param subplan      the sub plan name
     * @return List of {@link ExecutionDefinition}s
     */
    private static List<ExecutionDefinition> getExecutions(File testPlanFile, Worksheet subplan) throws TmsException {
        Nexial nexial = new Nexial();

        int rowStartIndex = ADDR_PLAN_EXECUTION_START.getRowStartIndex();
        int lastExecutionRow = subplan.findLastDataRow(ADDR_PLAN_EXECUTION_START);
        TestProject project = getTestProject(testPlanFile.getAbsolutePath(), testPlanFile);

        // map to store the script association with plan step (row number)
        scriptToStep = new HashMap<>();
        List<ExecutionDefinition> executions = new ArrayList<>();

        //parsing the subplan sheet
        for (int i = rowStartIndex; i < lastExecutionRow; i++) {
            XSSFRow row = subplan.getSheet().getRow(i);
            int rowNum = row.getRowNum() + 1;
            String msgSuffix = " specified in ROW " + rowNum + " of " + subplan.getName() + " in " + testPlanFile;

            if (ExecutionInputPrep.isPlanStepDisabled(row)) { continue; }

            File testScript = nexial.deriveScriptFromPlan(row, project, testPlanFile.getAbsolutePath());

            if (!InputFileUtils.isValidScript(testScript.getAbsolutePath())) {
                throw new RuntimeException("Invalid/unreadable test script" + msgSuffix);
            }
            // storing the script to step association
            scriptToStep.put(rowNum, testScript.getAbsolutePath());
            List<String> scenarios = nexial.deriveScenarioFromPlan(row, nexial.deriveScenarios(testScript));

            ExecutionDefinition exec = new ExecutionDefinition();
            exec.setPlanFile(subplan.getFile().getAbsolutePath());
            exec.setTestScript(testScript.getAbsolutePath());
            exec.setScenarios(scenarios);
            exec.setPlanSequence(rowNum);
            executions.add(exec);
        }
        return executions;
    }

    /**
     * Loop over the executions present for the plan and retrieve the test cases belonging to the scripts specified in the
     * plan steps
     *
     * @param testPlanFile {@link File} representing the plan file
     * @param worksheet    {@link Worksheet} representing the subplan worksheet
     * @return a {@link Map} of the test cases associated with each plan step
     */
    private static LinkedHashMap<Integer, List<TmsTestCase>> getSubPlanMap(File testPlanFile, Worksheet worksheet)
        throws TmsException {
        List<ExecutionDefinition> executions = getExecutions(testPlanFile, worksheet);

        LinkedHashMap<Integer, List<TmsTestCase>> testCasesToPlanStep = new LinkedHashMap<>();
        for (ExecutionDefinition exec : executions) {
            testCasesToPlanStep.put(exec.getPlanSequence(), resolveTestCases(exec));
        }
        return testCasesToPlanStep;
    }

    /**
     * Filter out the scenarios not specified in the plan step for each execution
     *
     * @param exec {@link ExecutionDefinition} instance representing single plan step
     * @return a {@link List} of {@link TmsTestCase}
     */
    private static List<TmsTestCase> resolveTestCases(ExecutionDefinition exec) throws TmsException {
        List<TmsTestCase> testCases = ReadScript.loadScript(exec.getTestScript());
        List<String> execScenarios = exec.getScenarios();
        List<TmsTestCase> testCases1 = new ArrayList<>();

        testCases.stream()
                .filter(testCase -> execScenarios.contains(testCase.getName()))
                .forEach(testCase -> {
                    testCase.setPlanTestCaseName(exec.getPlanSequence());
                    testCases1.add(testCase);
                });
        return testCases1;
    }
}
