/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.variable

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.junit.Assert.*
import org.junit.Test

class RandomTest {

    @Test
    @Throws(Exception::class)
    fun testInteger() {
        val fixture = Random()
        fixture.init()

        var data = fixture.integer("a")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.integer(null)
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.integer("")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.integer("16")
        assertNotNull(data)
        assertEquals(16, data.length.toLong())
        assertTrue(NumberUtils.isDigits(data))
    }

    @Test
    @Throws(Exception::class)
    fun testDecimal() {
        val fixture = Random()
        fixture.init()

        var data = fixture.decimal("a")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.decimal(null)
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.decimal("")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.decimal("16")
        assertNotNull(data)
        assertTrue(data.length <= 16)
        assertTrue(NumberUtils.isDigits(data))

        data = fixture.decimal("16,")
        assertNotNull(data)
        assertTrue(data.length <= 16)
        assertTrue(NumberUtils.isDigits(data))

        data = fixture.decimal("16,0")
        assertNotNull(data)
        assertTrue(data.length <= 16)
        assertTrue(NumberUtils.isCreatable(data))

        data = fixture.decimal("16,a")
        assertNotNull(data)
        assertTrue(data.length <= 16)
        assertTrue(NumberUtils.isCreatable(data))

        data = fixture.decimal("16,3")
        assertNotNull(data)
        assertTrue(data.length <= 20)
        assertTrue(NumberUtils.isCreatable(data))

        // test that there's no preceding zero
        for (i in 0..99) {
            data = fixture.decimal("30,3")
            assertNotNull(data)
            assertTrue(data.length <= 34)
            assertTrue(NumberUtils.isCreatable(data))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testLetter() {
        val fixture = Random()
        fixture.init()

        var data = fixture.letter("a")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.letter(null)
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.letter("")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.letter("16")
        assertNotNull(data)
        assertEquals(16, data.length.toLong())
        assertTrue(StringUtils.isAlpha(data))
    }

    @Test
    @Throws(Exception::class)
    fun testAlphanumeric() {
        val fixture = Random()
        fixture.init()

        var data = fixture.alphanumeric("a")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.alphanumeric(null)
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.alphanumeric("")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.alphanumeric("16")
        assertNotNull(data)
        assertEquals(16, data.length.toLong())
        assertTrue(StringUtils.isAlphanumeric(data))

        data = fixture.alphanumeric("2")
        assertNotNull(data)
        assertEquals(2, data.length.toLong())
        assertTrue(StringUtils.isAlphanumeric(data))
    }

    @Test
    @Throws(Exception::class)
    fun testAny() {
        val fixture = Random()
        fixture.init()

        var data = fixture.any("a")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.any(null)
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.any("")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.any("16")
        println("data = $data")
        assertNotNull(data)
        assertEquals(16, data.length.toLong())
        assertTrue(StringUtils.isAsciiPrintable(data))

        data = fixture.any("2")
        println("data = $data")
        assertNotNull(data)
        assertEquals(2, data.length.toLong())
        assertTrue(StringUtils.isAsciiPrintable(data))
    }

    @Test
    @Throws(Exception::class)
    fun testCharacters() {
        val fixture = Random()
        fixture.init()

        var data = fixture.characters(null, "a")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.characters("abcde", null)
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.characters("12345", "")
        assertNotNull(data)
        assertEquals("", data)

        data = fixture.characters("abcde", "16")
        println("data = $data")
        assertNotNull(data)
        assertEquals(16, data.length.toLong())
        assertTrue(StringUtils.containsOnly(data, *"abcde".toCharArray()))

        data = fixture.characters("4", "2")
        println("data = $data")
        assertNotNull(data)
        assertEquals(2, data.length.toLong())
        assertTrue(StringUtils.containsOnly(data, *"4".toCharArray()))

        data = fixture.characters("abcdefg", "5")
        println("data = $data")
        assertNotNull(data)
        assertEquals(5, data.length.toLong())
        assertTrue(StringUtils.containsAny(data, *"abcdefg".toCharArray()))
    }
}