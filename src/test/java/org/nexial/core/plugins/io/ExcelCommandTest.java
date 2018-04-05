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

package org.nexial.core.plugins.io;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;

public class ExcelCommandTest {
    private ExecutionContext context = new MockExecutionContext();

    @After
    public void tearDown() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void stringTo2dList() {
        ExcelCommand subject = new ExcelCommand();
        subject.init(context);

        // empty test
        Assert.assertTrue(CollectionUtils.isEmpty(subject.stringTo2dList(null)));
        Assert.assertTrue(CollectionUtils.isEmpty(subject.stringTo2dList("")));

        // happy path
        Assert.assertArrayEquals(new String[]{"a", "b", "c", "d", "e"},
                                 subject.stringTo2dList("a,b,c,d,e").get(0).toArray());

        // multi line test
        List<List<String>> twoDlist = subject.stringTo2dList("a,b,c,d,e\r\n1,2,3,4,5");
        Assert.assertArrayEquals(new String[]{"a", "b", "c", "d", "e"}, twoDlist.get(0).toArray());
        Assert.assertArrayEquals(new String[]{"1", "2", "3", "4", "5"}, twoDlist.get(1).toArray());

        // empty token test
        twoDlist = subject.stringTo2dList("a,b,c,,e\r\n1,,,4,5");
        Assert.assertArrayEquals(new String[]{"a", "b", "c", "", "e"}, twoDlist.get(0).toArray());
        Assert.assertArrayEquals(new String[]{"1", "", "", "4", "5"}, twoDlist.get(1).toArray());

        // empty line test
        twoDlist = subject.stringTo2dList(",,,\r\n,,,,5\n\n\n");
        Assert.assertEquals(2, twoDlist.size());
        Assert.assertArrayEquals(new String[]{"", "", "", ""}, twoDlist.get(0).toArray());
        Assert.assertArrayEquals(new String[]{"", "", "", "", "5"}, twoDlist.get(1).toArray());

        // empty line test 2
        twoDlist = subject.stringTo2dList(",,,\r\n,,,,5\n\n\n,,asdf,,$$\n\n.,l,;,',,");
        Assert.assertEquals(4, twoDlist.size());
        Assert.assertArrayEquals(new String[]{"", "", "", ""}, twoDlist.get(0).toArray());
        Assert.assertArrayEquals(new String[]{"", "", "", "", "5"}, twoDlist.get(1).toArray());
        Assert.assertArrayEquals(new String[]{"", "", "asdf", "", "$$"}, twoDlist.get(2).toArray());
        Assert.assertArrayEquals(new String[]{".", "l", ";", "'", "", ""}, twoDlist.get(3).toArray());
    }
}