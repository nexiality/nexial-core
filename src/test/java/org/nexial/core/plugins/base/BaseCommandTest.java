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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;

public class BaseCommandTest {
    private ExecutionContext context = new MockExecutionContext(true);

    static {
        System.setProperty("clear_var2", "I repeat, this is a test.");
        System.setProperty("clear_var3", "System is a go");
    }

    @Before
    public void init() {
        context.setData("var1", "a string separated by space");
        context.setData("var2", "every 12 minute there are 12 60 seconds");
        context.setData("clear_var1", 152);
        context.setData("clear_var2", "This is a test");
    }

    @After
    public void tearDown() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
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
}