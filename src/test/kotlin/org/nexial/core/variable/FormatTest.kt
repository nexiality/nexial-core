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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun testToNumeric() {
        assertNull(Format.toNumeric(null))
        assertEquals("", Format.toNumeric(""))
        assertEquals("1", Format.toNumeric("1"))
        assertEquals("123", Format.toNumeric("123"))
        assertEquals("0000123", Format.toNumeric("0000123"))
        assertEquals("123", Format.toNumeric(" 123 "))
        assertEquals("1230", Format.toNumeric(" 123 0 "))
        assertEquals("1230", Format.toNumeric(" 123a 0 "))
        assertEquals("1230", Format.toNumeric(" a123a 0 "))
        assertEquals("1230", Format.toNumeric(" a-123a 0 "))
        assertEquals("1230", Format.toNumeric(" a-b123a 0 "))
        assertEquals("1230", Format.toNumeric(" a-b1rt2a3a 0 "))
        assertEquals("1230", Format.toNumeric(" a-b1r 2 3a 0 "))
        assertEquals("-1230", Format.toNumeric(" -12 3a 0 "))
        assertEquals("-1230", Format.toNumeric(" -123data here0 "))
        assertEquals("-1230", Format.toNumeric(" -1230 "))
        assertEquals("-1230.0", Format.toNumeric(" -1230.0 "))
        assertEquals("-1230.0144", Format.toNumeric(" -1230.01a44 "))
        assertEquals("-1230.0144", Format.toNumeric(" --1230.01a44 "))
        assertEquals("1230.0144", Format.toNumeric(" +1230.01a44 "))
    }

    @Test
    fun testInteger() {
        val f = Format()
        assertNull(f.integer(null))
        assertEquals("", f.integer(""))
        assertEquals("1", f.integer("1"))
        assertEquals("1234", f.integer("1234"))
        assertEquals("1234", f.integer("1234.0"))
        assertEquals("1234", f.integer("1234.01"))
        assertEquals("1234", f.integer("1234.444"))
        assertEquals("-1234", f.integer("-1234.444"))
        assertEquals("-1234", f.integer("-1a2b3c4d.4e4f4g"))
        assertEquals("-1234", f.integer("-1a2     b3c4d.4e4f4g   "))
        assertEquals("1234", f.integer("+1a2     b3c4d.4e4f4g   "))
    }

    @Test
    fun testNumber() {
        val f = Format()
        assertNull(f.number(null, null))
        assertEquals("", f.number("", null))
        assertEquals("", f.number("", ""))
        assertEquals("1", f.number("1", ""))
        assertEquals("1.0", f.number("1.0", ""))
        assertEquals("1234", f.number("1234", ""))
        assertEquals("1234.0", f.number("1234.0", ""))
        assertEquals("1234.0", f.number("1234a.b0c", ""))
        assertEquals("1234.0", f.number("1-234a.   b0c", ""))
        assertEquals("1,234", f.number("1-234a.   b0c", "###,###.##"))
        assertEquals("1,234.01", f.number("1-234a.   b01c", "###,###.##"))
        assertEquals("1,234.00", f.number("1-234a.   b0c", "###,###.00"))
        assertEquals("1,494.23", f.number("The total amount due is $1494.23", "###,###.00"))
        assertEquals("1,494.00", f.number("The total amount due is $1494.00", "###,###.00"))
        assertEquals("1,494", f.number("The total amount due is $1494.00", "###,###.##"))
    }

    @Test
    fun testPercent() {
        val f = Format()
        assertNull(f.percent(null))
        assertEquals("", f.percent(""))
        assertEquals("", f.percent(""))
        assertEquals("100%", f.percent("1"))
        assertEquals("100%", f.percent("1.0"))
        assertEquals("123,400%", f.percent("1234"))
        assertEquals("123,400%", f.percent("1234.0"))
        assertEquals("123,400%", f.percent("1234a.b0c"))
        assertEquals("123,400%", f.percent("1-234a.   b0c"))
        assertEquals("123,400%", f.percent("1-234a.   b0c"))
        assertEquals("123,401%", f.percent("1-234a.   b01c"))
        assertEquals("123,400%", f.percent("1-234a.   b0c"))
        assertEquals("149,423%", f.percent("The total amount due is $1494.23"))
        assertEquals("149,400%", f.percent("The total amount due is $1494.00"))
        assertEquals("149,400%", f.percent("The total amount due is $1494.00"))

        assertEquals("51%", f.percent("The difference is 0.51a"))
        assertEquals("0.51%", f.percent("The difference is 0.0051a"))
        assertEquals("0.05%", f.percent("The difference is 0.000511 or so"))
        assertEquals("0.06%", f.percent("The difference is 0.000551 or so"))
    }

    @Test
    fun testDollar() {
        val f = Format()
        assertNull(f.dollar(null))
        assertEquals("", f.dollar(""))
        assertEquals("$1.00", f.dollar("1"))
        assertEquals("$1.00", f.dollar("1.0"))
        assertEquals("$1.02", f.dollar("1.02"))
        assertEquals("$2,121.02", f.dollar("2121.02"))
        assertEquals("$2,121.02", f.dollar("2d1a-2b1.-02"))
        assertEquals("$2,121.02", f.dollar("2d1a-2b1.-02424"))
        assertEquals("$2,121.03", f.dollar("2d1a-2b1.-02532"))
    }

    @Test
    fun testSsn() {
        val f = Format()
        assertNull(f.ssn(null))
        assertEquals("", f.ssn(""))
        assertEquals("123-45-67890", f.ssn("1234567890"))
        assertEquals("123-45-67890ABC", f.ssn("1234567890ABC"))
        assertEquals("123-45-678", f.ssn("12345678"))
        assertEquals("123-45-6", f.ssn("123456"))
        assertEquals("123", f.ssn("123"))
        assertEquals("13", f.ssn("13"))
    }

    @Test
    fun testMask() {
        val f = Format()
        assertNull(f.mask(null, "0", "0", ""))
        assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "0", "0", ""))
        assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "A", "0", ""))
        assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "0", "B", ""))
        assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "0", "-2", ""))
        assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "-1", "5", ""))
        assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "8", "5", ""))
        assertEquals("##lly Rancher", f.mask("Jolly Rancher", "0", "2", ""))
        assertEquals("Jolly Rooooor", f.mask("Jolly Rancher", "7", "12", "o"))
        assertEquals("Jolly Roooooo", f.mask("Jolly Rancher", "7", "15", "o"))
    }

    @Test
    fun testPhone() {
        val f = Format()
        assertNull(f.phone(null))
        assertEquals("", f.phone(""))
        assertEquals("123-4567", f.phone("1234567"))
        assertEquals("2-123-4567", f.phone("21234567"))
        assertEquals("(213)123-4567", f.phone("2131234567"))
        assertEquals("9(213)123-4567", f.phone("92131234567"))
        assertEquals("12345", f.phone("12345"))
        assertEquals("125", f.phone("125"))
        assertEquals("123(456)789-0ABC", f.phone("1234567890ABC"))
    }

    @Test
    fun testPhone2() {
        val f = Format()
        assertEquals("(714)541-9901", f.phone("714-541-9901"))
        assertEquals("1(714)541-9901", f.phone("1-714-541-9901"))
        assertEquals("1(714)541-9901", f.phone("1.714.541.9901"))
        assertEquals("1(714)541-9901", f.phone("1(714)5419901"))
    }

    @Test
    fun testCustom() {
        val f = Format()
        assertNull(f.custom(null, ""))
        assertEquals("", f.custom("", ""))
        assertEquals("ABC", f.custom("ABC", ""))
        assertEquals("A-B-C", f.custom("ABC", "`-`-`"))
        assertEquals("(123) 456-7890", f.custom("1234567890", "(```) ```-````"))
        assertEquals("123-4567", f.custom("1234567890", "```-````"))
        assertEquals("123-456-7890", f.custom("1234567890", "```-```-````"))
        assertEquals("123-45-6789", f.custom("123456789", "```-``-````"))
    }

    @Test
    fun testStrip() {
        val f = Format()
        assertNull(f.strip(null, null))
        assertNull(f.strip(null, ""))
        assertNull(f.strip(null, " "))
        assertNull(f.strip(null, "a"))
        assertEquals("Hnbrown cow?", f.strip("How now brown cow?", "ow "))
    }
}
