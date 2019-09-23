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

package org.nexial.core.variable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.FileUtil;

import static java.io.File.separator;

public class FileTest {
    private String testDir;

    @Before
    public void setUp() {
        testDir = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator +
                  this.getClass().getSimpleName() + separator;
        new java.io.File(testDir).mkdirs();
    }

    @After
    public void tearDown() {
        if (testDir != null) { FileUtils.deleteQuietly(new java.io.File(testDir)); }
    }

    @Test
    public void content() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        File file = new File();
        String content = file.content(target);
        Assert.assertEquals(fixture, content);
    }

    @Test
    public void asList() throws IOException {
        String item1 = RandomStringUtils.randomAlphanumeric(256);
        String item2 = RandomStringUtils.randomAlphanumeric(256);
        String item3 = RandomStringUtils.randomAlphanumeric(256);
        String item4 = RandomStringUtils.randomAlphanumeric(256);
        String item5 = RandomStringUtils.randomAlphanumeric(256);
        String fixture = item1 + "\n" +
                         item2 + "\n" +
                         item3 + "\r\n" +
                         item4 + "\r\n" +
                         item5;
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        String content = new File().asList(target);
        List<String> list = Arrays.asList(StringUtils.split(content, ","));
        Assert.assertEquals(5, list.size());
        Assert.assertEquals(item1, list.get(0));
        Assert.assertEquals(item2, list.get(1));
        Assert.assertEquals(item3, list.get(2));
        Assert.assertEquals(item4, list.get(3));
        Assert.assertEquals(item5, list.get(4));
    }

    @Test
    public void lastmod() throws IOException {
        long rightNow = System.currentTimeMillis();

        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        String lastmod = new File().lastmod(target);

        // assert that the file was created in less than last 2 seconds
        Assert.assertTrue((Long.parseLong(lastmod) - rightNow) < 2000);
    }

    @Test
    public void size() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        Assert.assertEquals(1024, Integer.parseInt(new File().size(target)));
    }

    @Test
    public void copy() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        String target2 = testDir + "dummy" + separator + "test2.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        Assert.assertEquals(target2, new File().copy(target, target2));
        Assert.assertTrue(FileUtil.isFileReadable(target2, 1024));
    }

    @Test
    public void move() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        String target2 = testDir + "dummy" + separator + "test2.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        Assert.assertEquals(target2, new File().move(target, target2));
        Assert.assertTrue(FileUtil.isFileReadable(target2, 1024));
        Assert.assertFalse(FileUtil.isFileReadable(target));
    }

    @Test
    public void delete() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        Assert.assertEquals(target, new File().delete(target));
        Assert.assertFalse(FileUtil.isFileReadable(target));
    }

    @Test
    public void overwrite() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        String fixture2 = RandomStringUtils.randomAlphanumeric(1048);
        Assert.assertEquals(target, new File().overwrite(target, fixture2));
        Assert.assertTrue(FileUtil.isFileReadable(target, 1048));
        Assert.assertEquals(fixture2, FileUtils.readFileToString(new java.io.File(target), Charset.forName("UTF-8")));
    }

    @Test
    public void overwrite_new_file() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";

        Assert.assertEquals(target, new File().overwrite(target, fixture));
        Assert.assertTrue(FileUtil.isFileReadable(target, 1024));
        Assert.assertEquals(fixture, FileUtils.readFileToString(new java.io.File(target), Charset.forName("UTF-8")));
    }

    @Test
    public void append() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        String fixture2 = RandomStringUtils.randomAlphanumeric(1048);
        Assert.assertEquals(target, new File().append(target, fixture2));
        Assert.assertTrue(FileUtil.isFileReadable(target, 1024 + 1048));
        Assert.assertEquals(fixture + fixture2,
                            FileUtils.readFileToString(new java.io.File(target), Charset.forName("UTF-8")));
    }

    @Test
    public void prepend() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        String fixture2 = RandomStringUtils.randomAlphanumeric(1048);
        Assert.assertEquals(target, new File().prepend(target, fixture2));
        Assert.assertTrue(FileUtil.isFileReadable(target, 1024 + 1048));
        Assert.assertEquals(fixture2 + fixture,
                            FileUtils.readFileToString(new java.io.File(target), Charset.forName("UTF-8")));
    }

    @Test
    public void name() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        Assert.assertEquals("test1.txt", new File().name(target));
    }

    @Test
    public void dir() throws IOException {
        String fixture = RandomStringUtils.randomAlphanumeric(1024);
        String target = testDir + "test1.txt";
        FileUtils.writeStringToFile(new java.io.File(target), fixture, Charset.forName("UTF-8"));

        Assert.assertEquals(StringUtils.removeEnd(testDir, separator),
                            StringUtils.removeEnd(new File().dir(target), separator));
    }
}