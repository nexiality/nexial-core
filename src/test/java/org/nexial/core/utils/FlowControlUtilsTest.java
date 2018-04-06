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

package org.nexial.core.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.nexial.core.model.*;
import org.nexial.core.model.FlowControl.Condition;
import org.nexial.core.model.FlowControl.Directive;

import static org.nexial.core.model.FlowControl.Directive.*;
import static org.nexial.core.model.NexialFilterComparator.Equal;
import static org.nexial.core.model.NexialFilterComparator.NotEqual;

public class FlowControlUtilsTest {
    ExecutionContext context = new MockExecutionContext();

    public static class DummyTestStep extends TestStep {
        public DummyTestStep(Directive directive, FlowControl flowControl) {
            super();
            flowControls = new LinkedHashMap<>();
            flowControls.put(directive, flowControl);
        }
    }

    @Before
    public void init() {
        context.setData("condition1", "banana");
        context.setData("condition2", "red");
        context.setData("condition3", "coffee");
    }

    @After
    public void tearDown() { if (context != null) { ((MockExecutionContext) context).cleanProject(); } }

    @Test
    public void checkPauseBefore() {
    }

    @Test
    public void checkPauseAfter() {
    }

    // @Test
    // public void checkSkipIf_NexialFilter() {
    //     Map<String, Condition> conditions = new HashMap<>();
    //     conditions.put("${condition1}", NexialFilter.newInstance() new Condition(Equal, Collections.singletonList("banana")));
    //     FlowControl flowControl = new FlowControl(SkipIf, conditions);
    //     TestStep fixture = new DummyTestStep(SkipIf, flowControl);
    //
    //     StepResult result = FlowControlUtils.checkSkipIf(context, fixture);
    //     Assert.assertNotNull(result);
    //     Assert.assertTrue(result.isSkipped());
    //
    //     conditions = new HashMap<>();
    //     conditions.put("${condition1}", new Condition(Equal, Collections.singletonList("apple")));
    //     flowControl = new FlowControl(SkipIf, conditions);
    //     fixture = new DummyTestStep(SkipIf, flowControl);
    //
    //     result = FlowControlUtils.checkSkipIf(context, fixture);
    //     Assert.assertNull(result);
    // }

    @Test
    public void checkSkipIf() {
        Map<String, Condition> conditions = new HashMap<>();
        conditions.put("${condition1}", new Condition(Equal, Collections.singletonList("banana")));
        FlowControl flowControl = new FlowControl(SkipIf, conditions);
        TestStep fixture = new DummyTestStep(SkipIf, flowControl);

        StepResult result = FlowControlUtils.checkSkipIf(context, fixture);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSkipped());

        conditions = new HashMap<>();
        conditions.put("${condition1}", new Condition(Equal, Collections.singletonList("apple")));
        flowControl = new FlowControl(SkipIf, conditions);
        fixture = new DummyTestStep(SkipIf, flowControl);

        result = FlowControlUtils.checkSkipIf(context, fixture);
        Assert.assertNull(result);
    }

    @Test
    public void checkProceedIf() {
        Map<Directive, FlowControl> flowControls = FlowControl.parse("ProceedIf(${condition1} != \"red\")");
        Assert.assertNotNull(flowControls);
        Assert.assertEquals(1, flowControls.size());

        FlowControl flowControl = flowControls.get(ProceedIf);
        Assert.assertNotNull(flowControl);

        Map<String, Condition> conditions = flowControl.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());

        System.out.println("conditions = " + conditions);
        Condition condition = conditions.get("${condition1}");
        Assert.assertNotNull(condition);
        Assert.assertEquals(NotEqual, condition.getOperator());

        TestStep fixture = new DummyTestStep(ProceedIf, flowControl);

        StepResult result = FlowControlUtils.checkProceedIf(context, fixture);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        conditions = new HashMap<>();
        conditions.put("${condition1}", new Condition(Equal, Collections.singletonList("apple")));
        flowControl = new FlowControl(ProceedIf, conditions);
        fixture = new DummyTestStep(ProceedIf, flowControl);

        result = FlowControlUtils.checkProceedIf(context, fixture);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void checkFailIf() {
        Map<String, Condition> conditions = new HashMap<>();
        conditions.put("${condition2}",
                       new Condition(Equal, Collections.singletonList("black")));
        FlowControl flowControl = new FlowControl(FailIf, conditions);
        TestStep fixture = new DummyTestStep(FailIf, flowControl);

        StepResult result = FlowControlUtils.checkFailIf(context, fixture);
        Assert.assertNull(result);

        conditions = new HashMap<>();
        conditions.put("${condition2}", new Condition(Equal, Collections.singletonList("red")));
        flowControl = new FlowControl(FailIf, conditions);
        fixture = new DummyTestStep(FailIf, flowControl);

        result = FlowControlUtils.checkFailIf(context, fixture);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void checkEndIf() {
        Map<String, Condition> conditions = new HashMap<>();
        conditions.put("${condition3}", new Condition(NexialFilterComparator.Is, Arrays.asList("black", "coffee")));
        FlowControl flowControl = new FlowControl(EndIf, conditions);
        TestStep fixture = new DummyTestStep(EndIf, flowControl);

        StepResult result = FlowControlUtils.checkEndIf(context, fixture);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        conditions = new HashMap<>();
        conditions.put("${condition2}", new Condition(Equal, Collections.singletonList("red")));
        conditions.put("${condition3}", new Condition(Equal, Collections.singletonList("coffee")));
        flowControl = new FlowControl(EndIf, conditions);
        fixture = new DummyTestStep(EndIf, flowControl);

        result = FlowControlUtils.checkEndIf(context, fixture);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
    }

}