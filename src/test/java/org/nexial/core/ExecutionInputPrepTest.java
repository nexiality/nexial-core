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

package org.nexial.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.NexialConst.Project;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.TestProject;
import org.springframework.util.ResourceUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.Data.SHEET_MERGED_DATA;
import static org.nexial.core.NexialConst.Project.DEF_REL_LOC_OUTPUT;
import static org.nexial.core.NexialConst.Project.DEF_REL_LOC_TEST_SCRIPT;

public class ExecutionInputPrepTest {
    private String projectHome;
    private String outBase;
    private File dirScript;
    private File dirData;

    private Map<Integer, Map<String, String>> expectedDataMap = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        System.out.println("creating test artifacts");
        projectHome = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator
                      + "_nexial_" + RandomStringUtils.randomAlphabetic(5) + separator;

        dirScript = new File(Project.appendScript(projectHome));
        FileUtils.forceMkdir(dirScript);

        dirData = new File(Project.appendData(projectHome));
        FileUtils.forceMkdir(dirData);

        outBase = Project.appendOutput(projectHome);
        FileUtils.forceMkdir(new File(outBase));

        // setup expected data mapping
        Map<String, String> iteration1DataMap = new HashMap<>();
        iteration1DataMap.put("nexial.scope.executionMode", "local");
        iteration1DataMap.put("nexial.scope.fallbackToPrevious", "true");
        iteration1DataMap.put("nexial.scope.iteration", "3");
        iteration1DataMap.put("nexial.scope.mailTo", "jumbotron@tiny.corp");
        iteration1DataMap.put("nexial.delayBetweenStepsMs", "600");
        iteration1DataMap.put("nexial.failFast", "false");
        iteration1DataMap.put("nexial.pollWaitMs", "800");
        iteration1DataMap.put("nexial.textDelim", ",");
        iteration1DataMap.put("nexial.verbose", "true");
        iteration1DataMap.put("var1", "");
        iteration1DataMap.put("var2", "");
        expectedDataMap.put(1, iteration1DataMap);

        Map<String, String> iteration2DataMap = new HashMap<>();
        iteration2DataMap.put("nexial.scope.executionMode", "local");
        iteration2DataMap.put("nexial.scope.fallbackToPrevious", "true");
        iteration2DataMap.put("nexial.scope.iteration", "3");
        iteration2DataMap.put("nexial.scope.mailTo", "jumbotron@tiny.corp");
        iteration2DataMap.put("nexial.delayBetweenStepsMs", "600");
        iteration2DataMap.put("nexial.failFast", "false");
        iteration2DataMap.put("nexial.pollWaitMs", "800");
        iteration2DataMap.put("nexial.textDelim", ",");
        iteration2DataMap.put("nexial.verbose", "true");
        iteration2DataMap.put("var1", "Value1a");
        iteration2DataMap.put("var2", "Value2a");
        expectedDataMap.put(2, iteration2DataMap);

        Map<String, String> iteration3DataMap = new HashMap<>();
        iteration3DataMap.put("nexial.scope.executionMode", "local");
        iteration3DataMap.put("nexial.scope.fallbackToPrevious", "true");
        iteration3DataMap.put("nexial.scope.iteration", "3");
        iteration3DataMap.put("nexial.scope.mailTo", "jumbotron@tiny.corp");
        iteration3DataMap.put("nexial.delayBetweenStepsMs", "600");
        iteration3DataMap.put("nexial.failFast", "false");
        iteration3DataMap.put("nexial.pollWaitMs", "800");
        iteration3DataMap.put("nexial.textDelim", ",");
        iteration3DataMap.put("nexial.verbose", "true");
        iteration3DataMap.put("var1", "Value1b");
        iteration3DataMap.put("var2", "Value2a");
        expectedDataMap.put(3, iteration3DataMap);

    }

    @After
    public void tearDown() throws Exception {
        System.out.println("destroying test artifacts");
        FileUtils.deleteDirectory(new File(projectHome));
    }

    @Test
    public void prep() throws Exception {
        Assert.assertTrue(FileUtil.isDirectoryReadable(outBase));
        Assert.assertTrue(FileUtil.isDirectoryReadable(dirScript.getAbsolutePath()));
        Assert.assertTrue(FileUtil.isDirectoryReadable(dirData.getAbsolutePath()));

        // setup test script
        File fileTestScript = new File(getPath(ExecutionInputPrepTest.class.getSimpleName() + "_test1.xlsx"));
        FileUtils.copyFileToDirectory(fileTestScript, dirScript);
        String testScript = dirScript.getAbsolutePath() + separator + fileTestScript.getName();

        // setup test data
        File fileTestData = new File(getPath(ExecutionInputPrepTest.class.getSimpleName() + "_test1.data.xlsx"));
        FileUtils.copyFileToDirectory(fileTestData, dirData);
        String testData = dirData.getAbsolutePath() + separator + fileTestData.getName();

        List<String> scenarios = Arrays.asList("scenario1", "scenario2");

        ExecutionDefinition execDef = new ExecutionDefinition();
        execDef.setTestScript(testScript);
        execDef.setScenarios(scenarios);
        execDef.setDataFile(testData);
        execDef.setDataSheets(scenarios);
        execDef.setProject(TestProject.newInstance(new File(testScript), DEF_REL_LOC_TEST_SCRIPT));
        execDef.parse();

        String baseScriptName = StringUtils.substringBefore(fileTestScript.getName(), ".xlsx");
        // ExecutionInputPrep prep = new ExecutionInputPrep();
        String runId = DateUtility.createTimestampString(null);

        for (int iterationIndex = 1; iterationIndex <= 3; iterationIndex++) {
            File targetOutputFile = ExecutionInputPrep.prep(runId, execDef, iterationIndex, iterationIndex);

            System.out.println("asserting project directories are generated ");

            // check output directories
            String executionOutBase = outBase + separator + runId + separator;
            Assert.assertTrue(FileUtil.isDirectoryReadable(Project.appendCapture(executionOutBase)));
            Assert.assertTrue(FileUtil.isDirectoryReadable(Project.appendLog(executionOutBase)));
            //Assert.assertTrue(FileUtil.isDirectoryReadable(executionOutBase + "summary"));

            String expectedTargetFilePath = separator + DEF_REL_LOC_OUTPUT + runId + separator + baseScriptName;

            Assert.assertNotNull(targetOutputFile);
            String targetFileName = targetOutputFile.getName();
            Assert.assertTrue(StringUtils.contains(targetOutputFile.getAbsolutePath(), expectedTargetFilePath));
            Assert.assertTrue(StringUtils.startsWith(targetFileName, baseScriptName));
            Assert.assertTrue(StringUtils.endsWith(targetFileName, ".00" + iterationIndex + ".xlsx"));

            Map<String, String> expectedData = expectedDataMap.get(iterationIndex);

            Excel excel = new Excel(targetOutputFile);
            Worksheet worksheet = excel.worksheet(SHEET_MERGED_DATA);
            int lastRow = worksheet.findLastDataRow(new ExcelAddress("A1"));

            // 1 extra due to the addition of `nexial.home` in #data
            Assert.assertTrue(lastRow >= expectedData.size());

            List<List<XSSFCell>> dataCells = worksheet.cells(new ExcelAddress("A1:B" + lastRow));
            dataCells.forEach(row -> {
                // skip over those defined via -D
                String name = row.get(0).getStringCellValue();
                if (!StringUtils.startsWith(name, "idea.test") && 
                    !StringUtils.startsWith(name, "nexial.home") &&
                    !StringUtils.startsWith(name, "java.")) {
                    String value = row.get(1).getStringCellValue();
                    System.out.print("asserting that data name " + name + " has value " + value + "... ");
                    Assert.assertEquals(expectedData.get(name), value);
                    System.out.println("PASSED");
                }
            });

            System.out.println();
        }
    }

    public String getPath(String filename) throws FileNotFoundException {
        return ResourceUtils.getFile("classpath:" +
                                     StringUtils.replace(this.getClass().getPackage().getName(), ".", "/") +
                                     "/" + filename).getAbsolutePath();
    }

}