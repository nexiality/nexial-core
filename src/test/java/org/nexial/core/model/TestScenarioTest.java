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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.NexialTestUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
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
        MockExecutionContext context = new MockExecutionContext();
        Excel excel = new Excel(file);
        Worksheet worksheet = excel.worksheet(testScenarioName);
        Assert.assertNotNull(worksheet);

        TestScenario scenario = new TestScenario(context, worksheet);
        Assert.assertNotNull(scenario);
        Assert.assertEquals(testScenarioName, scenario.getName());

        TestScenarioMeta meta = scenario.getMeta();
        Assert.assertNotNull(meta);
        Assert.assertEquals("test fixture - here's a description, a very, very, very long descriptiont, to detail " +
                            "the purpose of this test script, which, incidentally, amounts to nothing more than " +
                            "just a small sample of what can be accomplished via Nexial, inasmuch as one's " +
                            "dedication and investment of time upon it.  Having said such, this script contains no " +
                            "significant relevance in the real world, just as this very description similarly so " +
                            "stated.  Good day. ",
                            meta.getDescription());
        Assert.assertEquals("PROJECT1", meta.getProject());
        Assert.assertEquals("Release1", meta.getRelease());
        Assert.assertEquals("STORY-1234", meta.getFeatureRef());
        Assert.assertEquals("TEST-2345", meta.getTestRef());
        Assert.assertEquals("John Smith", meta.getAuthor());

        List<TestCase> testCases = scenario.getTestCases();
        System.out.println("testCases = " + testCases);
        Assert.assertEquals(3, CollectionUtils.size(testCases));
        Assert.assertEquals("Perform Activity 1", testCases.get(0).getName());
        Assert.assertEquals("Perform Activity 2", testCases.get(1).getName());
        Assert.assertEquals("Perform Activity 3", testCases.get(2).getName());

        TestCase testCase;
        List<TestStep> testSteps;
        TestStep testStep;
        Map<Directive, FlowControl> flowControls;

        testCase = scenario.getTestCase("Perform Activity 1");
        testSteps = testCase.getTestSteps();
        Assert.assertEquals(2, CollectionUtils.size(testSteps));

        // test case 1
        testStep = testSteps.get(0);
        Assert.assertEquals("I'm testing stuff… leave me alone!",
                            testStep.getDescription());
        Assert.assertEquals("base", testStep.getTarget());
        Assert.assertEquals("assertArrayEqual(array1,array2,exactOrder)", testStep.getCommand());
        Assert.assertEquals(Arrays.asList("1,2,3,4", "1,2,3,4", "true"), testStep.getParams());
        Assert.assertFalse(testStep.isCaptureScreen());

        flowControls = testStep.getFlowControls();
        Assert.assertNotNull(flowControls);
        Assert.assertEquals(1, flowControls.size());
        Assert.assertEquals("var1 = yes", flowControls.get(SkipIf).getConditions().get(0).toString());

        testStep = testSteps.get(1);
        Assert.assertEquals("I say, I'm working! No more description!", testStep.getDescription());
        Assert.assertEquals("math", testStep.getTarget());
        Assert.assertEquals("assertEqual(num1,num2)", testStep.getCommand());
        Assert.assertEquals(Arrays.asList("123.0", "123"), testStep.getParams());
        Assert.assertTrue(testStep.isCaptureScreen());
        Assert.assertNull(testStep.getFlowControls());

        // test case 2
        testCase = scenario.getTestCase("Perform Activity 2");
        testSteps = testCase.getTestSteps();
        Assert.assertEquals(6, CollectionUtils.size(testSteps));

        testStep = testSteps.get(0);
        Assert.assertEquals("ok.. Sorry, my bad. Got a little carried away", testStep.getDescription());
        Assert.assertEquals("io", testStep.getTarget());
        Assert.assertEquals("assertSame(baseline,subject,failFast)", testStep.getCommand());
        Assert.assertEquals(Arrays.asList("${path1}", "${path2}"), testStep.getParams());
        Assert.assertFalse(testStep.isCaptureScreen());
        Assert.assertNull(testStep.getFlowControls());

        testStep = testSteps.get(1);
        Assert.assertEquals("Here's what I'm doing: I'm running a test!", testStep.getDescription());
        Assert.assertEquals("base", testStep.getTarget());
        Assert.assertEquals("increment(var,amount)", testStep.getCommand());
        Assert.assertEquals(Collections.singletonList("${path3}"), testStep.getParams());
        Assert.assertFalse(testStep.isCaptureScreen());

        flowControls = testStep.getFlowControls();
        Assert.assertNotNull(flowControls);
        Assert.assertEquals(1, flowControls.size());
        NexialFilterList conditions = flowControls.get(EndIf).getConditions();
        NexialFilter var1Condition = conditions.get(0);
        Assert.assertEquals("var1 is [yes|no|maybe]", var1Condition.toString());

        testStep = testSteps.get(2);
        Assert.assertEquals("And now, guess what: I'm running another test!", testStep.getDescription());
        Assert.assertEquals("io", testStep.getTarget());
        Assert.assertEquals("copyFiles(source,target)", testStep.getCommand());
        Assert.assertEquals(Arrays.asList("C:\\asdjfaskdhf", "D:\\asdgasd\\ga\\sdg\\s\\dg"), testStep.getParams());
        Assert.assertTrue(testStep.isCaptureScreen());
        Assert.assertTrue(testStep.isCaptureScreen());

        // more tests for test case 2

        // test case 3
        String testCaseName = "Perform Activity 3";
        testCase = scenario.getTestCase(testCaseName);
        Assert.assertEquals(testScenarioName, testCase.getTestScenario().getName());

        testSteps = testCase.getTestSteps();
        Assert.assertEquals(3, CollectionUtils.size(testSteps));

        // more tests for test case 3
        testStep = testSteps.get(0);
        Assert.assertEquals(testCaseName, testStep.getTestCase().getName());
        Assert.assertEquals("What might I be doing now: surprise - it's a test!", testStep.getDescription());
        Assert.assertEquals("date", testStep.getTarget());
        Assert.assertEquals("addDay(date,day,var)", testStep.getCommand());
        Assert.assertEquals(Arrays.asList("2014-09-22", "7", "date1"), testStep.getParams());
        Assert.assertTrue(MapUtils.isEmpty(testStep.getFlowControls()));

        testStep = testSteps.get(1);
        Assert.assertEquals(testCaseName, testStep.getTestCase().getName());
        Assert.assertEquals("you know what the definition of insanity is?", testStep.getDescription());
        Assert.assertEquals("base", testStep.getTarget());
        Assert.assertEquals("appendToVar(var,text)", testStep.getCommand());
        Assert.assertEquals(Arrays.asList("date1", " is 7 days later"), testStep.getParams());
        Assert.assertTrue(MapUtils.isEmpty(testStep.getFlowControls()));

        testStep = testSteps.get(2);
        Assert.assertEquals(testCaseName, testStep.getTestCase().getName());
        Assert.assertEquals("Do you _REALLY_ know what the definition of insanity is?", testStep.getDescription());
        Assert.assertEquals("base", testStep.getTarget());
        Assert.assertEquals("assertEqual(value1,value2)", testStep.getCommand());
        Assert.assertEquals(Arrays.asList("${date1}", "2014-09-29 is 7 days later"), testStep.getParams());
        Assert.assertTrue(MapUtils.isEmpty(testStep.getFlowControls()));

        context.cleanProject();
    }

}