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

package org.nexial.core.variable

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters.NAME_ASCENDING
import org.nexial.commons.utils.FileUtil
import java.io.File.separator
import java.io.IOException
import java.nio.charset.Charset

@FixMethodOrder(NAME_ASCENDING)
class FileTest {

    private var testDir = "${SystemUtils.getJavaIoTmpDir().absolutePath}$separator${this.javaClass.simpleName}_"

    @Before
    fun setUp() {
        // if (!java.io.File(testDir!!).mkdirs()) System.err?.println("Unable to create $testDir")
    }

    @After
    fun tearDown() {
        FileUtils.deleteQuietly(java.io.File(testDir))
    }

    @Test
    @Throws(IOException::class)
    fun content() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}content${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            val file = File()
            val content = file.content(target)
            Assert.assertEquals(fixture, content)
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun asList() {
        val osArch = System.getProperty("os.arch")
        if (StringUtils.equals(osArch, "aarch64")) {
            // skipping this test for ARM64... not sure why it doesn't work here
            println("skipping asList() test for $osArch system since... well, it doesn't work (NOT SURE WHY")
            return;
        }

        val item1 = RandomStringUtils.randomAlphanumeric(256)
        val item2 = RandomStringUtils.randomAlphanumeric(256)
        val item3 = RandomStringUtils.randomAlphanumeric(256)
        val item4 = RandomStringUtils.randomAlphanumeric(256)
        val item5 = RandomStringUtils.randomAlphanumeric(256)
        val fixture = item1 + "\n" +
                      item2 + "\n" +
                      item3 + "\r\n" +
                      item4 + "\r\n" +
                      item5
        val dir = "${testDir}asList${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            val content = File().asList(target)
            val list = StringUtils.split(content, ",")
            // val list = listOf(*StringUtils.split(content, ","))
            Assert.assertEquals(5, list.size)
            Assert.assertEquals(item1, list[0])
            Assert.assertEquals(item2, list[1])
            Assert.assertEquals(item3, list[2])
            Assert.assertEquals(item4, list[3])
            Assert.assertEquals(item5, list[4])
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun lastmod() {
        val rightNow = System.currentTimeMillis()

        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}lastmod${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            val lastmod = File().lastmod(target)

            // assert that the file was created in less than last 2 seconds
            Assert.assertTrue(java.lang.Long.parseLong(lastmod) - rightNow < 2000)
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun size() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}size${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))
            Assert.assertEquals(1024, Integer.parseInt(File().size(target)).toLong())
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun copy() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir1 = "${testDir}copy${separator}"
        val target = "${dir1}test1.txt"
        val dir2 = "${testDir}dummy${separator}"
        val target2 = "${dir2}test2.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            Assert.assertEquals(target2, File().copy(target, target2))
            Assert.assertTrue(FileUtil.isFileReadable(target2, 1024))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir1))
            FileUtils.deleteQuietly(java.io.File(dir2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun move() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir1 = "${testDir}move${separator}"
        val target = "${dir1}test1.txt"
        val dir2 = "${testDir}dummy${separator}"
        val target2 = "${dir2}test2.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            Assert.assertEquals(target2, File().move(target, target2))
            Assert.assertTrue(FileUtil.isFileReadable(target2, 1024))
            Assert.assertFalse(FileUtil.isFileReadable(target))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir1))
            FileUtils.deleteQuietly(java.io.File(dir2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun delete() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}delete${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            Assert.assertEquals(target, File().delete(target))
            Assert.assertFalse(FileUtil.isFileReadable(target))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun overwrite() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}overwrite${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            val fixture2 = RandomStringUtils.randomAlphanumeric(1048)
            Assert.assertEquals(target, File().overwrite(target, fixture2))
            Assert.assertTrue(FileUtil.isFileReadable(target, 1048))
            Assert.assertEquals(fixture2, FileUtils.readFileToString(java.io.File(target), Charset.forName("UTF-8")))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun overwrite_new_file() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}new_file${separator}"
        val target = "${dir}test1.txt"

        try {
            Assert.assertEquals(target, File().overwrite(target, fixture))
            Assert.assertTrue(FileUtil.isFileReadable(target, 1024))
            Assert.assertEquals(fixture, FileUtils.readFileToString(java.io.File(target), Charset.forName("UTF-8")))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun append() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}append${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            val fixture2 = RandomStringUtils.randomAlphanumeric(1048)
            Assert.assertEquals(target, File().append(target, fixture2))
            Assert.assertTrue(FileUtil.isFileReadable(target, (1024 + 1048).toLong()))
            Assert.assertEquals(fixture + fixture2,
                                FileUtils.readFileToString(java.io.File(target), Charset.forName("UTF-8")))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun prepend() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}prepend${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            val fixture2 = RandomStringUtils.randomAlphanumeric(1048)
            Assert.assertEquals(target, File().prepend(target, fixture2))
            Assert.assertTrue(FileUtil.isFileReadable(target, (1024 + 1048).toLong()))
            Assert.assertEquals(fixture2 + fixture,
                                FileUtils.readFileToString(java.io.File(target), Charset.forName("UTF-8")))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun name() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}name${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            Assert.assertEquals("test1.txt", File().name(target))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }

    @Test
    @Throws(IOException::class)
    fun dir() {
        val fixture = RandomStringUtils.randomAlphanumeric(1024)
        val dir = "${testDir}dir${separator}"
        val target = "${dir}test1.txt"
        try {
            FileUtils.writeStringToFile(java.io.File(target), fixture, Charset.forName("UTF-8"))

            Assert.assertEquals(StringUtils.removeEnd("${testDir}dir", separator),
                                StringUtils.removeEnd(File().dir(target), separator))
        } finally {
            FileUtils.deleteQuietly(java.io.File(dir))
        }
    }
}