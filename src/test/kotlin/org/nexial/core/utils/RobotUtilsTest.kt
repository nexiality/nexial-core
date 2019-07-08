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

package org.nexial.core.utils

import org.junit.Assert
import org.junit.Test

class RobotUtilsTest {

    @Test
    @Throws(Exception::class)
    fun toKeystrokes_mod_1_key() {
        val fixture = "{COMMAND}5"
        val keystrokes = KeystrokeParser.toKeystrokes(fixture)
        println(keystrokes)

        Assert.assertNotNull(keystrokes)
        Assert.assertEquals(2, keystrokes.size)
        Assert.assertEquals("{COMMAND}", keystrokes[0])
        Assert.assertEquals("5", keystrokes[1])
    }

    @Test
    @Throws(Exception::class)
    fun toKeystrokes_mod_2_keys() {
        val fixture = "{COMMAND}5A"
        val keystrokes = KeystrokeParser.toKeystrokes(fixture)
        println(keystrokes)

        Assert.assertNotNull(keystrokes)
        Assert.assertEquals(3, keystrokes.size)
        Assert.assertEquals("{COMMAND}", keystrokes[0])
        Assert.assertEquals("5", keystrokes[1])
        Assert.assertEquals("A", keystrokes[2])
    }

    @Test
    @Throws(Exception::class)
    fun toKeystrokes_multiple_keys() {
        val fixture = "this is a TEST"
        val keystrokes = KeystrokeParser.toKeystrokes(fixture)
        println(keystrokes)

        Assert.assertNotNull(keystrokes)
        Assert.assertEquals(14, keystrokes.size)
        Assert.assertEquals("t", keystrokes[0])
        Assert.assertEquals("h", keystrokes[1])
        Assert.assertEquals("i", keystrokes[2])
        Assert.assertEquals("s", keystrokes[3])
        Assert.assertEquals(" ", keystrokes[4])
        Assert.assertEquals("i", keystrokes[5])
        Assert.assertEquals("s", keystrokes[6])
        Assert.assertEquals(" ", keystrokes[7])
        Assert.assertEquals("a", keystrokes[8])
        Assert.assertEquals(" ", keystrokes[9])
        Assert.assertEquals("T", keystrokes[10])
        Assert.assertEquals("E", keystrokes[11])
        Assert.assertEquals("S", keystrokes[12])
        Assert.assertEquals("T", keystrokes[13])
    }

    @Test
    @Throws(Exception::class)
    fun toKeystrokes_combined() {
        val fixture = "{COMMAND}{SHIFT}6{CONTROL}{ALT}F"
        val keystrokes = KeystrokeParser.toKeystrokes(fixture)
        println(keystrokes)

        Assert.assertNotNull(keystrokes)
        Assert.assertEquals(6, keystrokes.size)
        Assert.assertEquals("{COMMAND}", keystrokes[0])
        Assert.assertEquals("{SHIFT}", keystrokes[1])
        Assert.assertEquals("6", keystrokes[2])
        Assert.assertEquals("{CONTROL}", keystrokes[3])
        Assert.assertEquals("{ALT}", keystrokes[4])
        Assert.assertEquals("F", keystrokes[5])
    }

    @Test
    @Throws(Exception::class)
    fun toKeystrokes_combined2() {
        val fixture = "{COMMAND}{F12}F12{ALT}{ESC}!$$%!"
        val keystrokes = KeystrokeParser.toKeystrokes(fixture)
        println(keystrokes)

        Assert.assertNotNull(keystrokes)
        Assert.assertEquals(12, keystrokes.size)
        Assert.assertEquals("{COMMAND}", keystrokes[0])
        Assert.assertEquals("{F12}", keystrokes[1])
        Assert.assertEquals("F", keystrokes[2])
        Assert.assertEquals("1", keystrokes[3])
        Assert.assertEquals("2", keystrokes[4])
        Assert.assertEquals("{ALT}", keystrokes[5])
        Assert.assertEquals("{ESC}", keystrokes[6])
        Assert.assertEquals("!", keystrokes[7])
        Assert.assertEquals("$", keystrokes[8])
        Assert.assertEquals("$", keystrokes[9])
        Assert.assertEquals("%", keystrokes[10])
        Assert.assertEquals("!", keystrokes[11])
    }
}