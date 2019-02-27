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

package org.nexial.core.tools.inspector

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.nexial.core.tools.inspector.ProjectInspector.generateMD5
import java.io.File
import java.io.File.separator
import java.nio.charset.Charset

class MD5Test {
    val subject = File(JAVA_IO_TMPDIR + separator + "dummy.txt")
    private val utf8 = Charset.forName("UTF-8")
    // wait at least this much time before generating MD5 on same file
    private val minWaitTime: Long = 650

    @After
    fun tearDown() {
        FileUtils.deleteQuietly(subject)
    }

    @Test
    fun md5() {
        // create file
        val content = RandomStringUtils.randomAlphanumeric(1024)
        FileUtils.write(subject, content, utf8)

        val md5Hex = generateMD5(subject)
        println("md5Hex  = $md5Hex")

        // hang on...
        Thread.sleep(1000)

        FileUtils.touch(subject)
        val md5Hex2 = generateMD5(subject)
        println("md5Hex2 = $md5Hex2")

        Assert.assertNotEquals(md5Hex, md5Hex2)
        Assert.assertEquals(content, FileUtils.readFileToString(subject, utf8))

        // update content, keep length
        Thread.sleep(minWaitTime)
        FileUtils.write(subject, RandomStringUtils.randomAlphabetic(1024), utf8)

        val md5Hex3 = generateMD5(subject)
        println("md5Hex3 = $md5Hex3")

        Assert.assertNotEquals(md5Hex2, md5Hex3)

        // update content, diff length
        FileUtils.write(subject, RandomStringUtils.randomAlphabetic(1023), utf8)

        val md5Hex4 = generateMD5(subject)
        println("md5Hex4 = $md5Hex4")

        Assert.assertNotEquals(md5Hex3, md5Hex4)
    }
}