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
 */

package org.nexial.core.model;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.nexial.core.model.NexialFilterComparator.*;

public class NexialFilterListTest {

    @Test
    public void testParsing() throws Exception {
        NexialFilterList list;
        NexialFilter filter;

        list = new NexialFilterList("");
        Assert.assertNotNull(list);
        Assert.assertEquals(0, list.size());

        list = new NexialFilterList(" ");
        Assert.assertNotNull(list);
        Assert.assertEquals(0, list.size());

        // single filter test
        list = new NexialFilterList("a = 5");
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        filter = list.get(0);
        Assert.assertEquals("a", filter.getSubject());
        Assert.assertEquals(Equal.toString(), filter.getComparator().toString());
        Assert.assertEquals("5", filter.getControls());

        list = new NexialFilterList("jambalaya contain shrimp");
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        filter = list.get(0);
        Assert.assertEquals("jambalaya", filter.getSubject());
        Assert.assertEquals(Contain, filter.getComparator());
        Assert.assertEquals("shrimp", filter.getControls());

        list = new NexialFilterList(" pizza is [food,art,happiness]");
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        filter = list.get(0);
        Assert.assertEquals("pizza", filter.getSubject());
        Assert.assertEquals(Is, filter.getComparator());
        Assert.assertEquals("[food,art,happiness]", filter.getControls());

        // chained filter test
        list = new NexialFilterList("${color} is not [red,yellow,blue] & ${fruits} start with \"ban\"");
        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());
        filter = list.get(0);
        Assert.assertEquals("${color}", filter.getSubject());
        Assert.assertEquals(IsNot, filter.getComparator());
        Assert.assertEquals("[red,yellow,blue]", filter.getControls());
        filter = list.get(1);
        Assert.assertEquals("${fruits}", filter.getSubject());
        Assert.assertEquals(StartsWith, filter.getComparator());
        Assert.assertEquals("ban", filter.getControls());
    }

    @Test
    public void parsingIOFilter() throws Exception {
        NexialFilterList list;
        NexialFilter filter;

        list = new NexialFilterList("file1 has file-size 5");
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        filter = list.get(0);
        Assert.assertEquals("file1", filter.getSubject());
        Assert.assertEquals(ReadableFileWithSize.toString(), filter.getComparator().toString());
        Assert.assertEquals("5", filter.getControls());

        assertFilter(new NexialFilterList("file1 is not readable-file"), NotReadableFile);
        assertFilter(new NexialFilterList("file1 is readable-file"), ReadableFile);
        assertFilter(new NexialFilterList("file1 is not readable-path"), NotReadablePath);
        assertFilter(new NexialFilterList("file1 is readable-path"), ReadablePath);
        assertFilter(new NexialFilterList("file1 is not empty-path"), NotEmptyPath);
        assertFilter(new NexialFilterList("file1 is empty-path"), EmptyPath);
    }

    protected void assertFilter(NexialFilterList list, NexialFilterComparator expectedComparator) {
        NexialFilter filter;
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        filter = list.get(0);
        Assert.assertEquals("file1", filter.getSubject());
        Assert.assertEquals(expectedComparator.toString(), filter.getComparator().toString());
        Assert.assertTrue(StringUtils.isEmpty(filter.getControls()));
    }
}