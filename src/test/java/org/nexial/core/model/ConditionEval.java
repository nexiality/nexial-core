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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.compare.Evaluate;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.nexial.core.NexialConst.DEF_SPRING_XML;

public class ConditionEval {
    private static ExecutionContext context = new MockExecutionContext();
    private Evaluate evaluate;

    @AfterClass
    public static void afterClass() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Before
    public void init() {
        System.setProperty("selenium.logDir", "/app/nexial/logs");
        System.setProperty("selenium.host", "localhost");
        System.setProperty("testsuite.name", this.getClass().getSimpleName());
        // NexialTestUtils.setupCommonProps();

        context.setData("myAge", "19");
        context.setData("counter", 0 + "");
        context.setData("lastCounter", 10 + "");
        context.setData("myName", "JimBob");
        context.setData("myFriends", "Sally,Johnny,Obama");
        context.setData("myEgos", "Superman,${myName},JohnnyCash");
        context.setData("I'm loaded", "true");
        context.setData("I'm bluffing", "false");

        ClassPathXmlApplicationContext springContext =
            new ClassPathXmlApplicationContext("classpath:" + DEF_SPRING_XML);
        evaluate = springContext.getBean("evaluate", Evaluate.class);
    }

    @Test
    public void testCompareSingle() throws Exception {
        Assert.assertTrue(evaluate.evaluate("${myAge} = 19"));
        Assert.assertTrue(evaluate.evaluate("${myAge} is 19"));
        Assert.assertTrue(evaluate.evaluate("${myAge} != ${myName}"));
        Assert.assertTrue(evaluate.evaluate("${myAge} is not ${myName}"));

        int counter = 0;
        while (evaluate.evaluate("${counter} < ${lastCounter}")) {
            Assert.assertEquals(context.getStringData("counter"), counter + "");
            counter++;
            context.setData("counter", counter + "");
        }

        Assert.assertTrue(evaluate.evaluate("${counter} == ${lastCounter}"));

        Assert.assertTrue(evaluate.evaluate("${counter} >= 1"));
        Assert.assertTrue(evaluate.evaluate("${counter} <= ${myAge}"));
        Assert.assertTrue(evaluate.evaluate("${myName} = JimBob "));
        Assert.assertTrue(evaluate.evaluate("${myName} = JimBob    "));
        Assert.assertTrue(evaluate.evaluate("${myName} =          JimBob    "));
        Assert.assertTrue(evaluate.evaluate("${myName} is          JimBob    "));
        Assert.assertTrue(evaluate.evaluate("${myName} is not          JimJones    "));
        Assert.assertTrue(evaluate.evaluate("${myName} not in ${myFriends}"));
        Assert.assertTrue(evaluate.evaluate("${myName} in ${myEgos}"));
        Assert.assertTrue(evaluate.evaluate("${I'm loaded} is true"));
        Assert.assertTrue(evaluate.evaluate("${I'm bluffing} is false"));
    }

    @Test
    public void testCompareMultiple() throws Exception {
        Assert.assertTrue(evaluate.evaluate("(${myAge} = 19) and (${myName} = JimBob       )"));
        Assert.assertTrue(evaluate.evaluate(
            "(${myName} is  JimBob ) and (${myName} in ${myEgos}) and (${myAge} is 19)"));
        Assert.assertTrue(evaluate.evaluate(
            "(${myName} not in ${myFriends}) and (${counter} >= 0) and (${lastCounter} == 10) and (17 is 17)"));

        Assert.assertTrue(evaluate.evaluate("(${myAge} is JimBob) or (${myName} is      JimBob  )      "));
        Assert.assertTrue(evaluate.evaluate(
            "(${myFriends} is ${myEgos}) or (${myEgos} is ${myName}) or (${myName} is  JimBob)"));
        Assert.assertTrue(evaluate.evaluate(
            "(${I'm bluffing} is true) or (${I'm loaded} is true) or (${counter} > 0) or (${myName} in ${myFriends})"));

        Assert.assertTrue(evaluate.evaluate("(${myName} is 17) or (${myAge} is 19) and (${I'm loaded} is true)"));
        Assert.assertTrue(evaluate.evaluate("(${counter} >= 0) and (true is true) or (true is not false)"));

        Assert.assertTrue(evaluate.evaluate(
            "(${counter} >= 0) or (${myName} is Sam) or (${myName} is Jim) and (${myName} is JimBob)"));
        Assert.assertTrue(evaluate.evaluate(
            "(${myName} is Johnny) or (${I'm loaded} is true) and (${lastCounter} = 10) or (${lastCounter} = 9)"));
        Assert.assertTrue(evaluate.evaluate(
            "(${myName} in ${myEgos}) and (${myAge} is 19) or (${I'm bluffing} is true) or (${I'm bluffing} is not true)"));

        Assert.assertTrue(evaluate.evaluate(
            "(${myName} is JimBob)     or (${myAge} is 20) and (${I'm loaded} is true) and (15 is 15.00)"));
        Assert.assertTrue(evaluate.evaluate(
            "(${myAge} >= 5) and (${myAge} <= 20) or (${myName} is Mike) and (${myName} is not Jim)"));
        Assert.assertTrue(evaluate.evaluate(
            "(${myAge} is not ${myName}) and (${myName} is not ${I'm loaded}) and (${I'm bluffing} is false) or (${nothing} is nothing)"));

        Assert.assertTrue(evaluate.evaluate(
            "(${myName} is JimBob) and (${myAge} is 19) or (${I'm loaded} is true) or (${I'm bluffing} is true)"));
        Assert.assertTrue(evaluate.evaluate(
            "(${I'm loaded} is true) or (${I'm loaded} is false) and (${I'm bluffing} is false) or (${I'm bluffing} is true)"));
        Assert.assertTrue(evaluate.evaluate(
            "(JimBob is ${myName}) or (19 i ${myAge}) or (${myFriends} is ${myName}) and (${myName} in ${myEgo})"));
    }

    // todo currently doesn't work for nested comparison
    //@Test
    //public void testCompareNested() throws Exception {
    //Assert.assertTrue(evaluate.evaluate(
    //	"((${myAge} is 19) or (${myAge} != 19)) and ((${myName} is JimBob) or (${myName} != JimBob))"));
    //}

}
