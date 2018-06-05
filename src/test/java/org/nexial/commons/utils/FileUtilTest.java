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

package org.nexial.commons.utils;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.NexialTestUtils;

public class FileUtilTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void filterAndTransform() throws Exception {
        File fixture = NexialTestUtils.getResourceFile(this.getClass(), this.getClass().getSimpleName() + ".01.txt");

        Assert.assertTrue(FileUtil.isFileReadable(fixture, 2000));
        List<String> lincolns = FileUtil.filterAndTransform(fixture,
                                                            line -> StringUtils.contains(line, "Lincoln"),
                                                            line -> StringUtils.replace(line, "Lincoln", "[LINCOLN]"));

        Assert.assertNotNull(lincolns);

        System.out.println("lincolns:");
        lincolns.forEach(System.out::println);

        Assert.assertEquals(144, lincolns.size());
    }
}