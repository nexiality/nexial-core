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

package org.nexial.core.variable;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.MockExecutionContext;

import static org.nexial.core.NexialConst.Data.TEXT_DELIM;

public class ArrayTest {
    private static MockExecutionContext context;

    @BeforeClass
    public static void beforeClass() {
        MockExecutionContext context = new MockExecutionContext();
        context.setData(TEXT_DELIM, ",");
        ExecutionThread.set(context);
    }

    @AfterClass
    public static void afterClass() {
        if (context != null) { context.cleanProject(); }
    }

    @Test
    public void pack() {
        Array subject = new Array();

        Assert.assertEquals("", subject.pack((String) null));
        Assert.assertEquals("", subject.pack(""));
        Assert.assertEquals("", subject.pack(",,"));
        Assert.assertEquals(" ", subject.pack(", ,"));
        Assert.assertEquals(" ", subject.pack(", ,,"));
        Assert.assertEquals("a,b,c", subject.pack("a,b,c,,,,,,,"));
        Assert.assertEquals("a,b,c", subject.pack("a,b,,,,,c,,,"));
        Assert.assertEquals("a,b,c", subject.pack("a,b,,,,,,,,c"));
    }

    @Test
    public void replica() {
        Array subject = new Array();

        Assert.assertEquals("", subject.replica("", "5"));
        Assert.assertEquals(" , , , , ", subject.replica(" ", "5"));
        Assert.assertEquals("a,a,a,a,a", subject.replica("a", "5"));
        Assert.assertEquals("a,b,c,a,b,c,a,b,c,a,b,c,a,b,c", subject.replica("a,b,c", "5"));
        Assert.assertEquals("a, ,,b,c,a, ,,b,c,a, ,,b,c,a, ,,b,c", subject.replica("a, ,,b,c", "4"));
    }

    @Test
    public void replicaUntil() {
        Array subject = new Array();

        Assert.assertEquals("", subject.replicaUntil((String) null, "5"));
        Assert.assertEquals("", subject.replicaUntil("", "5"));
        Assert.assertEquals("a,a,a,a,a", subject.replicaUntil("a", "5"));
        Assert.assertEquals("a,b,a,b,a", subject.replicaUntil("a,b", "5"));
        Assert.assertEquals("a,bc,d,a,bc", subject.replicaUntil("a,bc,d", "5"));
        Assert.assertEquals("a,bc,defg,a,bc", subject.replicaUntil("a,bc,defg", "5"));
    }
}