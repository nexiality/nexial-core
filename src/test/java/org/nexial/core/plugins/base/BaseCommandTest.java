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

package org.nexial.core.plugins.base;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;

public class BaseCommandTest {
    private MockExecutionContext context = new MockExecutionContext(true);

    @Before
    public void init() {
        context.setData("var1", "a string separated by space");
        context.setData("var2", "every 12 minute there are 12 60 seconds");
        context.setData("clear_var1", 152);
        context.setData("clear_var2", "This is a test");
    }

    @After
    public void tearDown() {
        if (context != null) { context.cleanProject(); }
    }

    @Test
    public void testAssertArrayEquals() throws Exception {
        // technically testing base command. But we are doing the test this way to verify that WebCommand can
        // invoke BaseCommand methods without issues.
        BaseCommand subject = new BaseCommand();
        subject.init(context);

        try {
            StepResult result = subject.assertArrayEqual("1,2,3", "3,2,1", "false");
            Assert.assertNotNull(result);
            Assert.assertTrue(result.isSuccess());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        try {
            StepResult result = subject.assertArrayEqual("1,2,3", "3,2,1,0", "false");
            Assert.fail("expect failure");
        } catch (AssertionError e) {
            // it's ok
        }
    }

    @Test
    public void testAssertArrayEquals_long_list() throws Exception {
        BaseCommand subject = new BaseCommand();
        subject.init(context);

        try {
            subject.assertArrayEqual("1,2,301", "301,2,301", "false");
            Assert.fail("expect failure");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }

        try {
            subject.assertArrayEqual("1,2,301", "301,2,301", "true");
            Assert.fail("expect failure");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }

        try {
            subject.assertArrayEqual("519,23,0.913,587239,42739.187,2364,918.27,36591,82.736492,873,628,374.6",
                                     "519,23,0.913,58723.9,42739.187001,2364,918.27,36591,82.736492,873,628,374.60",
                                     "true");
            Assert.fail("expect failure");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }

        try {
            subject.assertArrayEqual("now is the,time for,all good men, to, ,come to the aid of his,country",
                                     "now is the time for,all good men to,come to the,aid of his,country",
                                     "true");
            Assert.fail("expect failure");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testAssertEquals_map() throws Exception {
        BaseCommand subject = new BaseCommand();
        subject.init(context);

        Map<String,Object> map1 = new LinkedHashMap<>();
        map1.put("amount", 1523.23d);
        map1.put("purchase order", "238934SDF23D");
        map1.put("order date", new Date(12735287394l));
        map1.put("contact person", "job bbblow");

        Map<String,Object> map2 = new LinkedHashMap<>();
        map2.put("amount", 1523.23d);
        map2.put("purchase order", "238934SDF23D");
        map2.put("contact person", "Joe Brlow");
        map2.put("currency", "Indian Rupee");
        map2.put("ship date", "07/19/2019");

        try {
            subject.assertEquals(map1, map2);
            Assert.fail("expect failure");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        } catch (AssertionError e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testNotContain() throws Exception {
        BaseCommand subject = new BaseCommand();
        subject.init(context);

        Assert.assertTrue(subject.assertNotContains("Gopi,Ashwin,Nagesh", "Mike").isSuccess());
        Assert.assertTrue(subject.assertNotContains("", " ").isSuccess());
        Assert.assertFalse(subject.assertNotContains(" ", " ").isSuccess());
        Assert.assertFalse(subject.assertNotContains(" ", "").isSuccess());
        Assert.assertFalse(subject.assertNotContains("Gopi,Ashwin,Nagesh", "").isSuccess());
        Assert.assertFalse(subject.assertNotContains("Gopi,Ashwin,Nagesh", ",").isSuccess());
        Assert.assertFalse(subject.assertNotContains("Gopi,Ashwin,Nagesh", "Ashwin").isSuccess());

    }

    @Test
    public void clear() throws Exception {
        BaseCommand subject = new BaseCommand();
        subject.init(context);

        // first, all clear_varX variables should be available
        Assert.assertEquals("152", subject.getContextValueAsString("clear_var1"));
        // context var is overshadowed by system property
        Assert.assertEquals("I repeat, this is a test.", subject.getContextValueAsString("clear_var2"));
        Assert.assertEquals("I repeat, this is a test.", System.getProperty("clear_var2"));
        Assert.assertEquals("System is a go", subject.getContextValueAsString("clear_var3"));
        Assert.assertNotNull(subject.getContextValueAsString("os.name"));

        // let's remove them
        StepResult result = subject.clear("clear_var1,clear_var2,os.name,clear_var3");
        Assert.assertTrue(result.isSuccess());

        Assert.assertNull(subject.getContextValueAsString("clear_var1"));
        Assert.assertNull(subject.getContextValueAsString("clear_var2"));
        Assert.assertNull(subject.getContextValueAsString("clear_var3"));
        Assert.assertNull(System.getProperty("clear_var3"));
        Assert.assertNotNull(subject.getContextValueAsString("os.name"));
        Assert.assertNotNull(System.getProperty("os.name"));

    }

    @Test
    public void saveCount() throws Exception {
        BaseCommand subject = new BaseCommand();
        subject.init(context);

        try {
            subject.saveCount("", "", "");
            Assert.fail("expected assertion error NOT thrown");
        } catch (AssertionError e) {
            // expected
        }

        // count just the letters
        StepResult result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "[a-z]", "count");
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(26, context.getIntData("count"));

        // count just the numbers
        result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "[0-9]", "count");
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(26, context.getIntData("count"));

        // count the sequence of letter-number-letter
        result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "[a-z][0-9][a-z]", "count");
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(13, context.getIntData("count"));

        // same, but number should be 0, 1, 2, 3, 4, or 5
        result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "[a-z][0-5][a-z]", "count");
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(9, context.getIntData("count"));

        // count just the 5's
        result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "5", "count");
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(3, context.getIntData("count"));

        // count all the spaces
        result = subject.saveCount("Now is the time for all good men to come to the aid of his country",
                                   "\\s", "count");
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(15, context.getIntData("count"));

        // count all the, a, to, of, for, is, are
        result = subject.saveCount("Now is the time for all good men to come to the aid of his country",
                                   "the | a | to | of | for | is | are ", "count");
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(7, context.getIntData("count"));

    }

    @Test
    public void assertTextOrder() throws Exception {
        BaseCommand subject = new BaseCommand();
        subject.init(context);

        String var = "var";
        context.setData(var, "April,August,February,December,January,July,June,November,October,September");

        try {
            StepResult result = subject.assertTextOrder(var, "true");
            Assert.assertFalse(result.isSuccess());
        } catch (AssertionError e) {
            // expected
        }
    }

    @Test
    public void assertArrayContain() throws Exception {
        BaseCommand subject = new BaseCommand();
        subject.init(context);

        String expected = "John,Peter,Soma,James";
        String actual = "Soma";
        StepResult result = subject.assertArrayContain(expected, actual);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        context.setData("nexial.textDelim", "|");
        expected = "John|Peter|Soma|James";
        actual = "Soma";
        result = subject.assertArrayContain(expected, actual);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

    }

    static {
        System.setProperty("clear_var2", "I repeat, this is a test.");
        System.setProperty("clear_var3", "System is a go");
    }
}