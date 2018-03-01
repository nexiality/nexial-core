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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.junit.Assert;
import org.junit.Test;

import org.nexial.core.NexialTestUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.model.FlowControl.Condition;
import org.nexial.core.model.FlowControl.Directive;

import static org.nexial.core.model.FlowControl.Directive.EndIf;
import static org.nexial.core.model.FlowControl.Directive.SkipIf;

public class TestScenarioTest {
	@Test
	public void parse() throws Exception {
		File file = NexialTestUtils.getResourceFile(this.getClass(), "TestScenarioTest1.xlsx");
		System.out.println("file.getAbsolutePath() = " + file.getAbsolutePath());
		Assert.assertTrue(file.isFile() && file.canRead());

		String testScenarioName = "Test Scenario";
		ExecutionContext context = new MockExecutionContext();
		Excel excel = new Excel(file);
		Worksheet worksheet = excel.worksheet(testScenarioName);
		Assert.assertNotNull(worksheet);
		((MockExecutionContext) context).cleanProject();

		TestScenario scenario = new TestScenario(context, worksheet);
		Assert.assertNotNull(scenario);
		Assert.assertEquals(testScenarioName, scenario.getName());

		TestScenarioMeta meta = scenario.getMeta();
		Assert.assertNotNull(meta);
		Assert.assertEquals("Need to verify that user is only able to add Deduction Override Groups "
		                    + "to a Batch for those they have permissions for edit/insert/delete\nNeed to "
		                    + "verify that those Deduction Override Groups they do not have permissions for "
		                    + "become read only\nShould be similar to test NG-1296",
		                    meta.getDescription());
		Assert.assertEquals("NGP", meta.getProject());
		Assert.assertEquals("2016Q3", meta.getRelease());
		Assert.assertEquals("NG-614", meta.getJira());
		Assert.assertEquals("NG-1597", meta.getZephyr());
		Assert.assertEquals("John Smith", meta.getAuthor());

		List<TestCase> testCases = scenario.getTestCases();
		System.out.println("testCases = " + testCases);
		Assert.assertEquals(3, CollectionUtils.size(testCases));
		Assert.assertEquals("Open User Management", testCases.get(0).getName());
		Assert.assertEquals("Open User Role Setup", testCases.get(1).getName());
		Assert.assertEquals("Open Deduction Override", testCases.get(2).getName());

		TestCase testCase;
		List<TestStep> testSteps;
		TestStep testStep;
		Map<Directive, FlowControl> flowControls;

		testCase = scenario.getTestCase("Open User Management");
		testSteps = testCase.getTestSteps();
		Assert.assertEquals(2, CollectionUtils.size(testSteps));

		// test case 1
		testStep = testSteps.get(0);
		Assert.assertEquals("Login to NextGen payroll application with Admin User.",
		                    testStep.getDescription());
		Assert.assertEquals("base", testStep.getTarget());
		Assert.assertEquals("assertArrayEqual(array1,array2,exactOrder)", testStep.getCommand());
		Assert.assertEquals(Arrays.asList("1,2,3,4", "1,2,3,4", "true"), testStep.getParams());
		Assert.assertFalse(testStep.isCaptureScreen());

		flowControls = testStep.getFlowControls();
		Assert.assertNotNull(flowControls);
		Assert.assertEquals(1, flowControls.size());
		Assert.assertEquals("= yes", flowControls.get(SkipIf).getConditions().get("var1").toString());

		testStep = testSteps.get(1);
		Assert.assertEquals("Open User Setup form under User Management.", testStep.getDescription());
		Assert.assertEquals("math", testStep.getTarget());
		Assert.assertEquals("assertEqual(num1,num2)", testStep.getCommand());
		Assert.assertEquals(Arrays.asList("123.0", "123"), testStep.getParams());
		Assert.assertTrue(testStep.isCaptureScreen());
		Assert.assertNull(testStep.getFlowControls());

		// test case 2
		testCase = scenario.getTestCase("Open User Role Setup");
		testSteps = testCase.getTestSteps();
		Assert.assertEquals(6, CollectionUtils.size(testSteps));

		testStep = testSteps.get(0);
		Assert.assertEquals("Search records for Admin user and verify System Admin role has been assigned.  "
		                    + "If not add System Admin role to user and save.", testStep.getDescription());
		Assert.assertEquals("io", testStep.getTarget());
		Assert.assertEquals("assertSame(baseline,subject,failFast)", testStep.getCommand());
		Assert.assertEquals(Arrays.asList("${path1}", "${path2}"), testStep.getParams());
		Assert.assertFalse(testStep.isCaptureScreen());
		Assert.assertNull(testStep.getFlowControls());

		testStep = testSteps.get(1);
		Assert.assertEquals("Ensure System Admin role assigned", testStep.getDescription());
		Assert.assertEquals("base", testStep.getTarget());
		Assert.assertEquals("increment(var,amount)", testStep.getCommand());
		Assert.assertEquals(Arrays.asList("${path3}"), testStep.getParams());
		Assert.assertFalse(testStep.isCaptureScreen());

		flowControls = testStep.getFlowControls();
		Assert.assertNotNull(flowControls);
		Assert.assertEquals(1, flowControls.size());
		Map<String, Condition> conditions = flowControls.get(EndIf).getConditions();
		Condition var1Condition = conditions.get("var1");
		Assert.assertEquals("is [yes, no, maybe]", var1Condition.toString());

		testStep = testSteps.get(2);
		Assert.assertEquals("Open form User Role Setup under User Management", testStep.getDescription());
		Assert.assertEquals("io", testStep.getTarget());
		Assert.assertEquals("copyFiles(source,target)", testStep.getCommand());
		Assert.assertEquals(Arrays.asList("C:\\asdjfaskdhf", "D:\\asdgasd\\ga\\sdg\\s\\dg"), testStep.getParams());
		Assert.assertTrue(testStep.isCaptureScreen());
		Assert.assertTrue(testStep.isCaptureScreen());

		// more tests for test case 2

		// test case 3
		String testCaseName = "Open Deduction Override";
		testCase = scenario.getTestCase(testCaseName);
		Assert.assertEquals(testScenarioName, testCase.getTestScenario().getName());

		testSteps = testCase.getTestSteps();
		Assert.assertEquals(3, CollectionUtils.size(testSteps));

		// more tests for test case 3
		testStep = testSteps.get(0);
		Assert.assertEquals(testCaseName, testStep.getTestCase().getName());
		Assert.assertEquals("Open form Deduction Override Group Category under Master Data", testStep.getDescription());
		Assert.assertEquals("date", testStep.getTarget());
		Assert.assertEquals("addDay(date,day,var)", testStep.getCommand());
		Assert.assertEquals(Arrays.asList("2014-09-22", "7", "date1"), testStep.getParams());
		Assert.assertTrue(MapUtils.isEmpty(testStep.getFlowControls()));

		testStep = testSteps.get(1);
		Assert.assertEquals(testCaseName, testStep.getTestCase().getName());
		Assert.assertEquals("Click New and add a unique record with data then Save.", testStep.getDescription());
		Assert.assertEquals("base", testStep.getTarget());
		Assert.assertEquals("appendToVar(var,text)", testStep.getCommand());
		Assert.assertEquals(Arrays.asList("date1", " is 7 days later"), testStep.getParams());
		Assert.assertTrue(MapUtils.isEmpty(testStep.getFlowControls()));

		testStep = testSteps.get(2);
		Assert.assertEquals(testCaseName, testStep.getTestCase().getName());
		Assert.assertEquals("Click New and add a unique record with data then Save", testStep.getDescription());
		Assert.assertEquals("base", testStep.getTarget());
		Assert.assertEquals("assertEqual(value1,value2)", testStep.getCommand());
		Assert.assertEquals(Arrays.asList("${date1}", "2014-09-29 is 7 days later"), testStep.getParams());
		Assert.assertTrue(MapUtils.isEmpty(testStep.getFlowControls()));

		((MockExecutionContext) context).cleanProject();
	}

}