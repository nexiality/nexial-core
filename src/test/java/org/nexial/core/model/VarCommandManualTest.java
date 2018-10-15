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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.plugins.base.BaseCommand;

import static java.lang.Boolean.TRUE;

public class VarCommandManualTest {
    MockExecutionContext context = new MockExecutionContext();

    public class MyVarCommand extends BaseCommand { }

    @Before
    public void init() {
        if (context == null) { context = new MockExecutionContext(); }

        context.setData("var1", "a string separated by space");
        context.setData("var2", "every 12 minute there are 12 60 seconds");
        context.setData("varArray", Arrays.asList("    EI101067",
                                                  "EI101071",
                                                  "EI101077",
                                                  "  EI101085",
                                                  "EI101101  ",
                                                  "EI101116  ",
                                                  "  EI101122  ",
                                                  "EI101138  ",
                                                  "EI101156",
                                                  "EI101160"));
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.cleanProject();
            context = null;
        }
    }

    @Test
    public void testSplit() {
        MyVarCommand var = new MyVarCommand();
        var.init(context);

        StepResult result = var.split("var1", " ", "var1Array");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertArrayEquals(((List) context.getObjectData("var1Array")).toArray(new String[0]),
                                 new String[]{"a", "string", "separated", "by", "space"});
    }

    @Test
    public void testSplit2() {
        MyVarCommand var = new MyVarCommand();
        var.init(context);

        StepResult result = var.split("var2", "12", "var2Array");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertArrayEquals(((List) context.getObjectData("var2Array")).toArray(new String[0]),
                                 new String[]{"every ", " minute there are ", " 60 seconds"});
    }

    @Test
    public void testSetNull() {
        String varName = "nothingToSeeHere";
        Assert.assertNull(context.getStringData(varName));

        context.setData(varName, "(null)");
        Assert.assertNull(context.getStringData(varName));

        try {
            MyVarCommand var = new MyVarCommand();
            var.init(context);

            System.out.println(var.incrementChar(varName, "5", null));
            Assert.fail("Expects AssertionError since varName points to null value");
        } catch (AssertionError e) {
            // expected
        }
    }

    @Test
    public void testTextOrderOnArray() {
        String varName = "myArray";

        String[] array = new String[]{"Jimmy", "James", "Cindy", "Adam", "Abraham"};
        context.setData(varName, array);

        MyVarCommand var = new MyVarCommand();
        var.init(context);

        StepResult result = var.assertTextOrder(varName, "true");
        Assert.assertTrue(result.isSuccess());

        List<String> list = Arrays.asList("Jimmy", "James", "Cindy", "Adam", "Abraham");
        context.setData(varName, list);

        var = new MyVarCommand();
        var.init(context);

        result = var.assertTextOrder(varName, "true");
        Assert.assertTrue(result.isSuccess());

        List list2 = new ArrayList();
        list2.add(12345);
        list2.add(4321);
        list2.add(4321.23);
        list2.add("John");
        list2.add('K');
        list2.add(TRUE);
        context.setData(varName, list2);

        var = new MyVarCommand();
        var.init(context);

        result = var.assertTextOrder(varName, "false");
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void testFindMatchesOnArray() {
        String varName = "myArray";
        String varName2 = "myArray2";

        String[] array = new String[]{"Jimmy", "James", "Cindy", "Adam", "Abraham"};
        context.setData(varName, array);

        MyVarCommand var = new MyVarCommand();
        var.init(context);

        StepResult result = var.saveReplace(context.replaceTokens("${" + varName + "}"), "a", "", varName2);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("Jimmy,Jmes,Cindy,Adm,Abrhm", context.getStringData(varName2));

        List<String> list = Arrays.asList("Jimmy", "James", "Cindy", "Adam", "Abraham");
        context.setData(varName, list);

        var = new MyVarCommand();
        var.init(context);

        result = var.saveReplace(context.replaceTokens("${" + varName + "}"), "a|A", "", varName2);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("Jimmy,Jmes,Cindy,dm,brhm", context.getStringData(varName2));

        List list2 = new ArrayList();
        list2.add(12345);
        list2.add(4321);
        list2.add(4321.23);
        list2.add("John");
        list2.add('K');
        list2.add(TRUE);
        context.setData(varName, list2);

        var = new MyVarCommand();
        var.init(context);

        result = var.saveReplace(context.replaceTokens("${" + varName + "}"), "1|e", "X", varName2);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("X2345,432X,432X.23,John,K,truX", context.getStringData(varName2));

    }

    @Test
    public void testSaveReplace() {
        MyVarCommand var = new MyVarCommand();
        var.init(context);

        context.setData("batchguid", "a,b,c");
        StepResult result = var.saveReplace(context.replaceTokens("${batchguid}"), ",", "','", "bgarray");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("a','b','c", context.getStringData("bgarray"));
    }
}