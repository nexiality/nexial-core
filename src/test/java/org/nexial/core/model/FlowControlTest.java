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

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.model.FlowControl.Condition;
import org.nexial.core.model.FlowControl.Directive;

import static org.nexial.core.model.FlowControl.Directive.*;

public class FlowControlTest {
    @Test
    public void parseToMap_simple() {
        Map<Directive, FlowControl> subjects = FlowControl.parse("SkipIf(var1=\"a\")");
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        FlowControl subject = subjects.get(SkipIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(SkipIf, subject.getDirective());

        Map<String, Condition> conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals("= a", conditions.get("var1").toString());
    }

    @Test
    public void parseToMap_ProceedIf() {
        Map<Directive, FlowControl> subjects = FlowControl.parse("ProceedIf(var1 =\"a\")");
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        FlowControl subject = subjects.get(ProceedIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(ProceedIf, subject.getDirective());

        Map<String, Condition> conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals("= a", conditions.get("var1").toString());

        subjects = FlowControl.parse("ProceedIf(var1 =\"a\" & ${var2} = ${var1})");
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        subject = subjects.get(ProceedIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(ProceedIf, subject.getDirective());

        conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(2, conditions.size());
        Assert.assertEquals("= a", conditions.get("var1").toString());
        Assert.assertEquals("= ${var1}", conditions.get("${var2}").toString());
    }

    @Test
    public void parseToMap_2conditions() {

        Map<Directive, FlowControl> subjects = FlowControl.parse("PauseBefore(var1=\"a\" & var2=95)");
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        FlowControl subject = subjects.get(PauseBefore);
        Assert.assertNotNull(subject);
        Assert.assertEquals(PauseBefore, subject.getDirective());

        Map<String, Condition> conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(2, conditions.size());
        Assert.assertEquals("= a", conditions.get("var1").toString());
        Assert.assertEquals("= 95", conditions.get("var2").toString());
    }

    @Test
    public void parseToMap_multi_directives_and_conditions() {

        String fixture = "PauseBefore (var1=\"a\" &var2  =    95)  \n" +
                         "EndIf \t \t (var3 is [\"chicken\", 47, true ] )  \n" +
                         "\t\t \t SkipIf (var3 is [\"Nacho\", var2, stuff] & var4=\"password\" & var4 != \"PassWord\" )  \n " +
                         "  ProceedIf ( var5 start with \"Hello World\" )";

        Map<Directive, FlowControl> subjects = FlowControl.parse(fixture);
        Assert.assertNotNull(subjects);
        Assert.assertEquals(4, subjects.size());

        subjects.forEach((directive, flowControl) -> {
            System.out.println(StringUtils.rightPad(directive + "", 25) + " = " + flowControl);
        });

        FlowControl subject = subjects.get(PauseBefore);
        Assert.assertNotNull(subject);
        Assert.assertEquals(PauseBefore, subject.getDirective());

        Map<String, Condition> conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(2, conditions.size());
        Assert.assertEquals("= a", conditions.get("var1").toString());
        Assert.assertEquals("= 95", conditions.get("var2").toString());

        subject = subjects.get(EndIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(EndIf, subject.getDirective());

        conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals("is [chicken, 47, true]", conditions.get("var3").toString());

        subject = subjects.get(SkipIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(SkipIf, subject.getDirective());

        conditions = subject.getConditions();
        System.out.println("conditions = " + conditions);
        Assert.assertNotNull(conditions);
        Assert.assertEquals(2, conditions.size());
        Assert.assertEquals("is [Nacho, var2, stuff]", conditions.get("var3").toString());
        Assert.assertEquals("!= PassWord", conditions.get("var4").toString());

    }

    @Test
    public void parseToMap_inConditions() {
        String fixture = "EndIf(var1 is [\"yes\", \"no\", \"maybe\"])";

        Map<Directive, FlowControl> subjects = FlowControl.parse(fixture);
        Assert.assertNotNull(subjects);
        Assert.assertEquals(1, subjects.size());

        FlowControl subject = subjects.get(EndIf);
        Assert.assertNotNull(subject);
        Assert.assertEquals(EndIf, subject.getDirective());

        Map<String, Condition> conditions = subject.getConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals(NexialFilterComparator.Is, conditions.get("var1").getOperator());
        Assert.assertEquals(Arrays.asList("yes", "no", "maybe"), conditions.get("var1").getValues());

    }
}