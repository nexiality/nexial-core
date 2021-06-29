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

package org.nexial.core.plugins.base

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.math.NumberUtils
import org.junit.After
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.model.MockExecutionContext
import java.io.File
import java.util.*
import kotlin.test.assertTrue

class BaseCommandTest {
    private val context = MockExecutionContext(true)

    companion object {
        init {
            System.setProperty("clear_var2", "I repeat, this is a test.")
            System.setProperty("clear_var3", "System is a go")
        }
    }

    @Before
    fun init() {
        context.setData("var1", "a string separated by space")
        context.setData("var2", "every 12 minute there are 12 60 seconds")
        context.setData("clear_var1", 152)
        context.setData("clear_var2", "This is a test")
    }

    @After
    fun tearDown() {
        context.cleanProject()
    }

    @Test
    @Throws(Exception::class)
    fun assertArrayEquals() {
        // technically testing base command. But we are doing the test this way to verify that WebCommand can
        // invoke BaseCommand methods without issues.
        val subject = BaseCommand()
        subject.init(context)

        try {
            val result = subject.assertArrayEqual("1,2,3", "3,2,1", "false")
            assertNotNull(result)
            Assert.assertTrue(result.isSuccess)
        } catch (e: Exception) {
            fail(e.message)
        }

        try {
            subject.assertArrayEqual("1,2,3", "3,2,1,0", "false")
            fail("expect failure")
        } catch (e: AssertionError) {
            // it's ok
        }
    }

    @Test
    @Throws(Exception::class)
    fun assertArrayEquals_long_list() {
        val subject = BaseCommand()
        subject.init(context)

        try {
            subject.assertArrayEqual("1,2,301", "301,2,301", "false")
            fail("expect failure")
        } catch (e: Exception) {
            fail(e.message)
        } catch (e: AssertionError) {
            println(e.message)
        }

        try {
            subject.assertArrayEqual("1,2,301", "301,2,301", "true")
            fail("expect failure")
        } catch (e: Exception) {
            fail(e.message)
        } catch (e: AssertionError) {
            println(e.message)
        }

        try {
            subject.assertArrayEqual("519,23,0.913,587239,42739.187,2364,918.27,36591,82.736492,873,628,374.6",
                                     "519,23,0.913,58723.9,42739.187001,2364,918.27,36591,82.736492,873,628,374.60",
                                     "true")
            fail("expect failure")
        } catch (e: Exception) {
            fail(e.message)
        } catch (e: AssertionError) {
            println(e.message)
        }

        try {
            subject.assertArrayEqual("now is the,time for,all good men, to, ,come to the aid of his,country",
                                     "now is the time for,all good men to,come to the,aid of his,country",
                                     "true")
            fail("expect failure")
        } catch (e: Exception) {
            fail(e.message)
        } catch (e: AssertionError) {
            println(e.message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun assertArrayEqual_test_output() {
        val subject = BaseCommand()
        subject.init(context)

        try {
            subject.assertArrayEqual("This, is, a , test  ,Do,not,be , alarm .",
                                     "This,is,a ,test,Do,not, be,alarmed.",
                                     "true")
            fail("expect failure")
        } catch (e: Exception) {
            fail(e.message)
        } catch (e: AssertionError) {
            println(e.message)
            assertEquals("expected=This,[ is],[ a ],[ test  ],Do,not,[be ],[ alarm .]\n" +
                         "actual  =This,[is] ,[a ] ,[test]   ,Do,not,[ be],[alarmed.]",
                         e.message?.trim()
            )
        }

        try {
            subject.assertArrayEqual("",
                                     "This,is,a ,test,Do,not, be,alarmed.",
                                     "true")
            fail("expect failure")
        } catch (e: Exception) {
            fail(e.message)
        } catch (e: AssertionError) {
            println(e.message)
            assertEquals("EXPECTED array is empty: {}", e.message?.trim()
            )
        }

        try {
            subject.assertArrayEqual("This",
                                     "This,is,a ,test,Do,not, be,alarmed.",
                                     "true")
            fail("expect failure")
        } catch (e: Exception) {
            fail(e.message)
        } catch (e: AssertionError) {
            println(e.message)
            assertEquals("expected=This,<missing>,<missing>,<missing>,<missing>,<missing>,<missing>,<missing> \n" +
                         "actual  =This,[is]     ,[a ]     ,[test]   ,[Do]     ,[not]    ,[ be]    ,[alarmed.]",
                         e.message?.trim()
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun assertEquals_null() {
        val subject = BaseCommand()
        subject.init(context)

        try {
            subject.assertEqual("11.25", "(null)")
            fail("Expected failure did not happened..")
        } catch (e: AssertionError) {
            // expected
        }

        try {
            subject.assertEqual("(null)", "-91.001")
            fail("Expected failure did not happened..")
        } catch (e: AssertionError) {
            // expected
        }

        try {
            subject.assertEqual("(blank)", "(null)")
            fail("Expected failure did not happened..")
        } catch (e: AssertionError) {
            // expected
        }

        try {
            subject.assertEqual("(blank)", "(empty)")
            fail("Expected failure did not happened..")
        } catch (e: AssertionError) {
            // expected
        }

        try {
            subject.assertEqual("null", "(null)")
            fail("Expected failure did not happened..")
        } catch (e: AssertionError) {
            // expected
        }

        try {
            subject.assertEqual("1992", "(empty)")
            fail("Expected failure did not happened..")
        } catch (e: AssertionError) {
            // expected
        }
    }

    @Test
    @Throws(Exception::class)
    fun assertEquals_special_markers() {
        val subject = BaseCommand()
        subject.init(context)

        try {
            val result = subject.assertEqual("(null)", "(null)")
            assertTrue { result.isSuccess }
        } catch (e: AssertionError) {
            fail(e.message)
        }

        try {
            val result = subject.assertEqual("(blank)", "(blank)")
            assertTrue { result.isSuccess }
        } catch (e: AssertionError) {
            fail(e.message)
        }

        try {
            val result = subject.assertEqual("(empty)", "(empty)")
            assertTrue { result.isSuccess }
        } catch (e: AssertionError) {
            fail(e.message)
        }

        try {
            val result = subject.assertEqual("(blank)", " ")
            assertTrue { result.isSuccess }
        } catch (e: AssertionError) {
            fail(e.message)
        }

        try {
            val result = subject.assertEqual("", "(empty)")
            assertTrue { result.isSuccess }
        } catch (e: AssertionError) {
            fail(e.message)
        }

        try {
            val result = subject.assertEqual("19", "19.000")
            assertTrue { result.isSuccess }
        } catch (e: AssertionError) {
            fail(e.message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun assertEquals_asteriks() {
        val subject = BaseCommand()
        subject.init(context)

        // expects no globbin' or regex stuff
        context.setData("actual", "**** **** **** 1111 Exp: 03/2030 Billing Zip: 78758")
        context.setData("expected", "**** **** **** 1111 Exp: 03/2030 Billing Zip: 78758")

        try {
            val result = subject.assertEqual(context.replaceTokens("\${expected}"), context.replaceTokens("\${actual}"))
            assertTrue { result.isSuccess }
        } catch (e: AssertionError) {
            fail(e.message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun assertEquals_number() {
        assertEquals(1.0, NumberUtils.createBigDecimal("1").toDouble(), 0.0)
        assertEquals(1.0, NumberUtils.createBigDecimal("1.").toDouble(), 0.0)
        assertEquals(1.0, NumberUtils.createBigDecimal("01").toDouble(), 0.0)
        assertEquals(1.0, NumberUtils.createBigDecimal("01.").toDouble(), 0.0)
        assertEquals(1.0, NumberUtils.createBigDecimal("01.0").toDouble(), 0.0)
        assertEquals(1.0, NumberUtils.createBigDecimal("01.0000").toDouble(), 0.0)
        assertEquals(1.0, NumberUtils.createBigDecimal("000001.0000").toDouble(), 0.0)

        try {
            NumberUtils.createBigDecimal("  1. ")
            fail("expected NFE here!")
        } catch (e: NumberFormatException) {
            // it's fine/expected
        }

        try {
            NumberUtils.createBigDecimal("1,000.001")
            fail("expected NFE here!")
        } catch (e: NumberFormatException) {
            // it's fine/expected
        }

        try {
            NumberUtils.createBigDecimal("$59")
            fail("expected NFE here!")
        } catch (e: NumberFormatException) {
            // it's fine/expected
        }

        try {
            NumberUtils.createBigDecimal("1..0")
            fail("expected NFE here!")
        } catch (e: NumberFormatException) {
            // it's fine/expected
        }
    }

    @Test
    @Throws(Exception::class)
    fun assertMatch() {
        val subject = BaseCommand()
        subject.init(context)

        assertTrue(subject.assertMatch(" ", ".+").isSuccess)
        assertTrue(subject.assertMatch("abc", ".+").isSuccess)
        assertTrue(subject.assertMatch(".", ".+").isSuccess)
        assertTrue(subject.assertMatch("..", ".+").isSuccess)
        assertTrue(subject.assertMatch("KHJGF", "[A-Z]{5,6}").isSuccess)
        assertTrue(subject.assertMatch("KHJGFS", "[A-Z]{5,6}").isSuccess)
        assertTrue(subject.assertMatch("2KHJGFSa", "\\d[A-Z]{5,6}[a-z]").isSuccess)

        try {
            subject.assertMatch("", "")
            fail("expected exception here!")
        } catch (e: Throwable) {
            // it's fine/expected
        }

        try {
            subject.assertMatch("", null)
            fail("expected exception here!")
        } catch (e: Throwable) {
            // it's fine/expected
        }

        try {
            subject.assertMatch("", null)
            fail("expected exception here!")
        } catch (e: Throwable) {
            // it's fine/expected
        }

        try {
            subject.assertMatch(null, ".*")
            fail("expected exception here!")
        } catch (e: Throwable) {
            // it's fine/expected
        }

    }

    @Test
    @Throws(Exception::class)
    fun assertEquals_map() {
        val subject = BaseCommand()
        subject.init(context)

        val map1 = LinkedHashMap<String, Any>()
        map1["amount"] = 1523.23
        map1["purchase order"] = "238934SDF23D"
        map1["order date"] = Date(12735287394L)
        map1["contact person"] = "job bbblow"

        val map2 = LinkedHashMap<String, Any>()
        map2["amount"] = 1523.23
        map2["purchase order"] = "238934SDF23D"
        map2["contact person"] = "Joe Brlow"
        map2["currency"] = "Indian Rupee"
        map2["ship date"] = "07/19/2019"

        try {
            subject.assertEquals(map1, map2)
            fail("expect failure")
        } catch (e: Exception) {
            fail(e.message)
        } catch (e: AssertionError) {
            println(e.message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun notContain() {
        val subject = BaseCommand()
        subject.init(context)

        Assert.assertTrue(subject.assertNotContain("Gopi,Ashwin,Nagesh", "Mike").isSuccess)
        Assert.assertTrue(subject.assertNotContain("", " ").isSuccess)
        assertFalse(subject.assertNotContain(" ", " ").isSuccess)
        assertFalse(subject.assertNotContain(" ", "").isSuccess)
        assertFalse(subject.assertNotContain("Gopi,Ashwin,Nagesh", "").isSuccess)
        assertFalse(subject.assertNotContain("Gopi,Ashwin,Nagesh", ",").isSuccess)
        assertFalse(subject.assertNotContain("Gopi,Ashwin,Nagesh", "Ashwin").isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun clear() {
        val subject = BaseCommand()
        subject.init(context)

        // first, all clear_varX variables should be available
        assertEquals("152", subject.getContextValueAsString("clear_var1"))
        // context var is overshadowed by system property
        assertEquals("I repeat, this is a test.", subject.getContextValueAsString("clear_var2"))
        assertEquals("I repeat, this is a test.", System.getProperty("clear_var2"))
        assertEquals("System is a go", subject.getContextValueAsString("clear_var3"))
        assertNotNull(subject.getContextValueAsString("os.name"))

        // let's remove them
        val result = subject.clear("clear_var1,clear_var2,os.name,clear_var3")
        Assert.assertTrue(result.isSuccess)

        assertNull(subject.getContextValueAsString("clear_var1"))
        assertNull(subject.getContextValueAsString("clear_var2"))
        assertNull(subject.getContextValueAsString("clear_var3"))
        assertNull(System.getProperty("clear_var3"))
        assertNotNull(subject.getContextValueAsString("os.name"))
        assertNotNull(System.getProperty("os.name"))
    }

    @Test
    @Throws(Exception::class)
    fun saveCount() {
        val subject = BaseCommand()
        subject.init(context)

        try {
            subject.saveCount("", "", "")
            fail("expected assertion error NOT thrown")
        } catch (e: AssertionError) {
            // expected
        }

        // count just the letters
        var result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "[a-z]", "count")
        Assert.assertTrue(result.isSuccess)
        assertEquals(26, context.getIntData("count").toLong())

        // count just the numbers
        result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "[0-9]", "count")
        Assert.assertTrue(result.isSuccess)
        assertEquals(26, context.getIntData("count").toLong())

        // count the sequence of letter-number-letter
        result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "[a-z][0-9][a-z]", "count")
        Assert.assertTrue(result.isSuccess)
        assertEquals(13, context.getIntData("count").toLong())

        // same, but number should be 0, 1, 2, 3, 4, or 5
        result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "[a-z][0-5][a-z]", "count")
        Assert.assertTrue(result.isSuccess)
        assertEquals(9, context.getIntData("count").toLong())

        // count just the 5's
        result = subject.saveCount("a0b1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s8t9u0v1w2x3y4z5", "5", "count")
        Assert.assertTrue(result.isSuccess)
        assertEquals(3, context.getIntData("count").toLong())

        // count all the spaces
        result = subject.saveCount("Now is the time for all good men to come to the aid of his country",
                                   "\\s", "count")
        Assert.assertTrue(result.isSuccess)
        assertEquals(15, context.getIntData("count").toLong())

        // count all the, a, to, of, for, is, are
        result = subject.saveCount("Now is the time for all good men to come to the aid of his country",
                                   "the | a | to | of | for | is | are ", "count")
        Assert.assertTrue(result.isSuccess)
        assertEquals(7, context.getIntData("count").toLong())
    }

    @Test
    @Throws(Exception::class)
    fun assertTextOrder() {
        val subject = BaseCommand()
        subject.init(context)

        val `var` = "var"
        context.setData(`var`, "April,August,February,December,January,July,June,November,October,September")

        try {
            val result = subject.assertTextOrder(`var`, "true")
            assertFalse(result.isSuccess)
        } catch (e: AssertionError) {
            // expected
        }
    }

    @Test
    @Throws(Exception::class)
    fun assertArrayContain() {
        val subject = BaseCommand()
        subject.init(context)

        var expected = "John,Peter,Soma,James"
        var actual = "Soma"
        var result = subject.assertArrayContain(expected, actual)
        println("result = $result")
        Assert.assertTrue(result.isSuccess)

        context.setData("nexial.textDelim", "|")
        expected = "John|Peter|Soma|James"
        actual = "Soma"
        result = subject.assertArrayContain(expected, actual)
        println("result = $result")
        Assert.assertTrue(result.isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun assertArrayContain_withExpression() {
        context.setData("suggestions", "[\"croissant\",\"croissant\",\"croissant\"]")
        context.setData("search_term", "croissant")

        val subject = BaseCommand()
        subject.init(context)

        val array = context.replaceTokens("[TEXT(\${suggestions}) => removeStart([) removeEnd(]) remove(\") text]")
        val expected = context.replaceTokens("\${search_term}")
        var result = subject.assertArrayContain(array, expected)
        println("result = $result")
        Assert.assertTrue(result.isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun assertEqualsNBSP() {
        val expectedFile = "C:\\Users\\mikel\\AppData\\Roaming\\JetBrains\\IntelliJIdea2021.1\\scratches\\scratch_1.txt"
        val expected = FileUtils.readFileToString(File(expectedFile), DEF_FILE_ENCODING)
        println("expected = $expected")

        val actual = "Vendor \"PCVen18903\" not found. Do you wish to create?"
        assertNotEquals("normal assert would fail", expected, actual)
        assertTrue("but BaseCommand's assert will pass because we put special character handling there!",
                   BaseCommand.assertEqualsInternal(expected, actual))
    }
}