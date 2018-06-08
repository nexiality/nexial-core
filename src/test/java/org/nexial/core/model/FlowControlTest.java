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

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.FlowControl.Directive;
import org.nexial.core.utils.FlowControlUtils;

import static org.nexial.core.model.FlowControl.Directive.*;
import static org.nexial.core.model.NexialFilterComparator.Is;

public class FlowControlTest {
    @Test
    public void parseToMap_simple() {
        Map<Directive, FlowControl> subjects = FlowControl.parse("SkipIf(var1 = \"a\")");
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        FlowControl subject = subjects.get(SkipIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(SkipIf, subject.getDirective());

        NexialFilterList conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals("var1 = a", conditions.get(0).toString());
    }

    @Test
    public void parseToMap_ProceedIf() {
        Map<Directive, FlowControl> subjects = FlowControl.parse("ProceedIf(var1 = \"a\")");
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        FlowControl subject = subjects.get(ProceedIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(ProceedIf, subject.getDirective());

        NexialFilterList conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals("var1 = a", conditions.get(0).toString());

        subjects = FlowControl.parse("ProceedIf(var1 = \"a\" & ${var2} = ${var1})");
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        subject = subjects.get(ProceedIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(ProceedIf, subject.getDirective());

        conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(2, conditions.size());
        Assert.assertEquals("var1 = a", conditions.get(0).toString());
        Assert.assertEquals("${var2} = ${var1}", conditions.get(1).toString());
    }

    @Test
    public void parseToMap_2conditions() {

        Map<Directive, FlowControl> subjects = FlowControl.parse("PauseBefore(var1 = \"a\" & var2 = 95)");
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        FlowControl subject = subjects.get(PauseBefore);
        Assert.assertNotNull(subject);
        Assert.assertEquals(PauseBefore, subject.getDirective());

        NexialFilterList conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(2, conditions.size());
        Assert.assertEquals("var1 = a", conditions.get(0).toString());
        Assert.assertEquals("var2 = 95", conditions.get(1).toString());
    }

    @Test
    public void parseToMap_multi_directives_and_conditions() {

        String fixture = "PauseBefore (var1 = \"a\" & var2  =    95)  \n" +
                         "EndIf \t \t (var3 is [\"chicken\"| 47| true ] )  \n" +
                         "\t\t \t SkipIf (var3 is [\"Nacho\"| var2| stuff] & var4 = \"password\" & var4 != \"PassWord\" )  \n " +
                         "  ProceedIf ( var5 start with \"Hello World\" )";

        Map<Directive, FlowControl> subjects = FlowControl.parse(fixture);
        Assert.assertNotNull(subjects);
        Assert.assertEquals(4, subjects.size());

        subjects.forEach((directive, flowControl) ->
                             System.out.println(StringUtils.rightPad(directive + "", 25) + " = " + flowControl));

        FlowControl subject = subjects.get(PauseBefore);
        Assert.assertNotNull(subject);
        Assert.assertEquals(PauseBefore, subject.getDirective());

        NexialFilterList conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(2, conditions.size());
        Assert.assertEquals("var1 = a", conditions.get(0).toString());
        Assert.assertEquals("var2 = 95", conditions.get(1).toString());

        subject = subjects.get(EndIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(EndIf, subject.getDirective());

        conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals("var3 is [chicken|47|true]", conditions.get(0).toString());

        subject = subjects.get(SkipIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(SkipIf, subject.getDirective());

        conditions = subject.getConditions();
        System.out.println("conditions = " + conditions);
        Assert.assertNotNull(conditions);
        Assert.assertEquals(3, conditions.size());
        Assert.assertEquals("var3 is [Nacho|var2|stuff]", conditions.get(0).toString());
        Assert.assertEquals("var4 = password", conditions.get(1).toString());
        Assert.assertEquals("var4 != PassWord", conditions.get(2).toString());
    }

    @Test
    public void parseToMap_inConditions() {
        String fixture = "EndIf(var1 is [\"yes\"| \"no\"| \"maybe\"])";

        Map<Directive, FlowControl> subjects = FlowControl.parse(fixture);
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        FlowControl subject = subjects.get(EndIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(EndIf, subject.getDirective());

        NexialFilterList conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals(Is, conditions.get(0).getComparator());
        Assert.assertEquals(Arrays.asList("yes", "no", "maybe"), conditions.get(0).getControlList());

    }

    @Test
    public void executeViaTestStep() throws Exception {
        MockExecutionContext context = new MockExecutionContext();
        context.setData("my_color", "blue");
        context.setData("my_name", "Jonathan");
        context.setData("my_age", 29);
        context.setData("my_fruit", "mango");
        context.setData("is_login", true);
        context.setData("min_age", 21);
        context.setData("max_age", 30);

        TestStep fixture = createMockTestStep(context);

        // simple test
        fixture.flowControls = FlowControl.parse("SkipIf ( ${my_age} between [21|30] )   \n  \r\n");
        String expected = "SkipIf -> [${my_age} between [21|30]]";
        assertExpectedFilterEval(fixture, expected);

        fixture.flowControls = FlowControl.parse("SkipIf ( ${my_age} between [${min_age}|${max_age}] )   \n  \r\n");
        assertExpectedFilterEval(fixture, "SkipIf -> [${my_age} between [${min_age}|${max_age}]]");

        fixture.flowControls = FlowControl.parse("SkipIf ( ${my_age} between [2|3] )   \n  \r\n");
        assertFailFilterEval(fixture);

        fixture.flowControls = FlowControl.parse("PauseBefore ( ${my_name} end with nathan ) ");
        assertExpectedFilterEval(fixture, "PauseBefore -> [${my_name} end with nathan]");

        fixture.flowControls = FlowControl.parse("PauseAfter ( ${is_login} is [true]) ");
        assertExpectedFilterEval(fixture, "PauseAfter -> [${is_login} is [true]]");

        // chained filters
        fixture.flowControls = FlowControl.parse("EndLoopIf ( ${is_login} is [true] \t&   ${my_color} contain lu   ) ");
        assertExpectedFilterEval(fixture, "EndLoopIf -> [${is_login} is [true], ${my_color} contain [lu]]");

        fixture.flowControls = FlowControl.parse("ProceedIf ( ${my_fruit} match [A-Za-z]{5} \t&   ${my_age} > 25   ) ");
        assertExpectedFilterEval(fixture, "ProceedIf -> [${my_fruit} match [A-Za-z]{5}, ${my_age} > 25]");

        fixture.flowControls = FlowControl.parse("EndIf ( ${my_color} != red & ${my_name} has length of 8 & " +
                                                 "${min_age} < ${max_age} & " +
                                                 "${my_age} is not [28,27,26,30,49,31,33,45] ) ");
        assertExpectedFilterEval(fixture, "EndIf -> [${my_color} != red, ${my_name} has length of 8, " +
                                          "${min_age} < ${max_age}, " +
                                          "${my_age} is not [28,27,26,30,49,31,33,45]]");

        // compound flow control, test for precedence
        fixture.flowControls = FlowControl.parse("SkipIf ( ${my_age} between [21|30] )   \n" +
                                                 "PauseAfter ( ${is_login} is [true|TRUE|True|yes|Yes|YES] )  \n" +
                                                 "\t EndIf ( \t  ${my_fruit} not in [banana,apple,chicken] )");
        assertExpectedFilterEval(fixture, "EndIf -> [${my_fruit} not in [banana,apple,chicken]]");

        fixture.flowControls = FlowControl.parse("SkipIf ( ${my_age} between [21|30] )   \n" +
                                                 "PauseAfter ( ${is_login} is [true|TRUE|True|yes|Yes|YES] )  \n" +
                                                 "\t FailIf ( \t  ${my_fruit} in [banana,apple,mango,chicken] )");
        assertExpectedFilterEval(fixture, "SkipIf -> [${my_age} between [21|30]]");

        fixture.flowControls = FlowControl.parse("SkipIf ( ${my_age} between [21|30] )   \n" +
                                                 "PauseAfter ( ${is_login} is [true|TRUE|True|yes|Yes|YES] )  \n" +
                                                 "\t FailIf ( \t  ${my_fruit} in [banana|apple|mango|chicken] )");
        assertExpectedFilterEval(fixture, "FailIf -> [${my_fruit} in [banana|apple|mango|chicken]]");

        fixture.flowControls = FlowControl.parse("EndIf ( ${my_age} between [21|30] )   \n" +
                                                 "\t FailIf ( \t  ${my_fruit} in [banana|apple|mango|chicken] )" +
                                                 "EndLoopIf ( ${is_login} is [true|TRUE|True|yes|Yes|YES] )  \n");
        assertExpectedFilterEval(fixture, "FailIf -> [${my_fruit} in [banana|apple|mango|chicken]]");

        fixture.flowControls = FlowControl.parse("EndIf ( ${my_age} between [21|30] )   \n" +
                                                 "\t SkipIf ( \t  ${my_fruit} in [banana|apple|mango|chicken] )" +
                                                 "EndLoopIf ( ${is_login} is [true|TRUE|True|yes|Yes|YES] )  \n");
        assertExpectedFilterEval(fixture, "EndLoopIf -> [${is_login} is [true|TRUE|True|yes|Yes|YES]]");

        fixture.flowControls = FlowControl.parse("PauseAfter ( ${is_login} is [true|TRUE|True|yes|Yes|YES] )  \n" +
                                                 "PauseBefore(${is_login} is [true|TRUE|True|yes|Yes|YES])");
        assertExpectedFilterEval(fixture, "PauseBefore -> [${is_login} is [true|TRUE|True|yes|Yes|YES]]");

        fixture.flowControls = FlowControl.parse("PauseAfter ( ${is_login} is [true|TRUE|True|yes|Yes|YES] )  \n" +
                                                 "PauseBefore(${is_login} = TRUE)");
        assertExpectedFilterEval(fixture, "PauseAfter -> [${is_login} is [true|TRUE|True|yes|Yes|YES]]");

        fixture.flowControls = FlowControl.parse("PauseAfter ( ${is_login} is false)  \n" +
                                                 "PauseBefore( ${my_age} = 28)");
        assertFailFilterEval(fixture);

        // odd ball test
        fixture.flowControls = FlowControl.parse("SkipIf ( not_a_var is undefined )");
        assertExpectedFilterEval(fixture, "SkipIf -> [not_a_var is undefined]");

        fixture.flowControls = FlowControl.parse("SkipIf ( my_age is defined )");
        assertExpectedFilterEval(fixture, "SkipIf -> [my_age is defined]");

        fixture.flowControls = FlowControl.parse("SkipIf ( my_age is defined & " +
                                                 "         ${my_age} is not empty &" +
                                                 "         ${my_age} has length of 2     " +
                                                 "        )                          ");
        assertExpectedFilterEval(fixture, "SkipIf -> [" +
                                          "my_age is defined, ${my_age} is not empty, ${my_age} has length of 2]");

    }

    protected void assertFailFilterEval(TestStep fixture) throws InvocationTargetException, IllegalAccessException {
        StepResult actual = fixture.invokeCommand();
        System.out.println(actual);
        Assert.assertNotNull(actual);
        Assert.assertTrue(actual.failed());
        Assert.assertEquals("NO FLOW CONTROL MATCHED", actual.getMessage());
        System.out.println("--------------------------------------------------\n");
    }

    protected void assertExpectedFilterEval(TestStep fixture, String expected)
        throws InvocationTargetException, IllegalAccessException {
        StepResult actual = fixture.invokeCommand();
        System.out.println(actual);
        Assert.assertNotNull(actual);
        Assert.assertTrue(actual.isSuccess());
        Assert.assertEquals(expected, actual.getMessage());
        System.out.println("--------------------------------------------------\n");
    }

    @NotNull
    protected TestStep createMockTestStep(MockExecutionContext context) {
        TestCase testCase = new TestCase();
        testCase.setName("executeViaTestStep");

        TestStep fixture = new TestStep() {
            @Override
            protected StepResult invokeCommand() {
                String[] args = params.toArray(new String[0]);

                // log before pause (DO NOT use log() since that might trigger logToTestScript())
                System.out.println("executing " + command + "(" + TextUtils.toString(args, ", ", "", "") + ")");

                if (MapUtils.isNotEmpty(flowControls)) {

                    // order is important!
                    if (FlowControlUtils.shouldPause(context, this, PauseBefore)) {
                        return StepResult.success(flowControls.get(PauseBefore).toString());
                    }

                    if (FlowControlUtils.checkFailIf(context, this) != null) {
                        return StepResult.success(flowControls.get(FailIf).toString());
                    }

                    if (FlowControlUtils.checkEndLoopIf(context, this) != null) {
                        return StepResult.success(flowControls.get(EndLoopIf).toString());
                    }

                    if (FlowControlUtils.checkEndIf(context, this) != null) {
                        return StepResult.success(flowControls.get(EndIf).toString());
                    }

                    if (FlowControlUtils.checkSkipIf(context, this) != null) {
                        return StepResult.success(flowControls.get(SkipIf).toString());
                    }

                    if (FlowControlUtils.checkProceedIf(context, this) != null) {
                        return StepResult.success(flowControls.get(ProceedIf).toString());
                    }

                    if (FlowControlUtils.shouldPause(context, this, PauseAfter)) {
                        return StepResult.success(flowControls.get(PauseAfter).toString());
                    }
                }

                return StepResult.fail("NO FLOW CONTROL MATCHED");
            }
        };
        fixture.testCase = testCase;
        fixture.context = context;
        fixture.description = "This is a test.  Do not be alarmed";
        fixture.target = "base";
        fixture.command = "verbose(text)";
        fixture.params = Collections.singletonList("My favorite color is ${my_color}.  Hi, my name is ${my_name}");
        fixture.captureScreen = false;
        fixture.messageId = this.getClass().getSimpleName() + ".executeViaTestStep";
        return fixture;
    }
}