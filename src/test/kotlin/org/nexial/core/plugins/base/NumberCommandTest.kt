package org.nexial.core.plugins.base


import org.apache.commons.lang3.math.NumberUtils
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.nexial.commons.utils.TextUtils
import org.nexial.core.model.MockExecutionContext

class NumberCommandTest {

    val context: MockExecutionContext = MockExecutionContext()

    @After
    fun tearDown() = context.cleanProject()

    @Test
    @Throws(Exception::class)
    fun target() {
        val fixture = NumberCommand()

        fixture.init(context)

        Assert.assertEquals("number", fixture.target)
    }

    @Test
    @Throws(Exception::class)
    fun assertEqual() {
        val fixture = NumberCommand()

        fixture.init(context)

        // happy path
        Assert.assertTrue(fixture.assertEqual("1.234", "1.234").isSuccess)
        Assert.assertTrue(fixture.assertEqual("0", "0").isSuccess)
        Assert.assertTrue(fixture.assertEqual("195", "1567").failed())
        Assert.assertTrue(fixture.assertEqual("3451", "152").failed())

    }

    @Test
    @Throws(Exception::class)
    fun assertGreater() {
        val fixture = NumberCommand()

        fixture.init(context)

        Assert.assertTrue(fixture.assertGreater("2", "1").isSuccess)
        Assert.assertTrue(fixture.assertGreater("2.1", "2.00000").isSuccess)
        Assert.assertTrue(fixture.assertGreater("-3", "-3.0001").isSuccess)
        Assert.assertTrue(fixture.assertGreater("2124123124", "2124123123.9999998").isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun assertGreaterOrEqual() {
        val fixture = NumberCommand()

        fixture.init(context)

        Assert.assertTrue(fixture.assertGreaterOrEqual("1", "1").isSuccess)
        Assert.assertTrue(fixture.assertGreaterOrEqual("2.1", "2.10000").isSuccess)
        Assert.assertTrue(fixture.assertGreaterOrEqual("-3.000", "-03.").isSuccess)
        Assert.assertTrue(fixture.assertGreaterOrEqual("001.0001", "1.00010000").isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun assertLesser() {
        val fixture = NumberCommand()

        fixture.init(context)

        Assert.assertTrue(fixture.assertLesser("1", "2").isSuccess)
        Assert.assertTrue(fixture.assertLesser("2.00000", "2.1").isSuccess)
        Assert.assertTrue(fixture.assertLesser("-3.0001", "-3").isSuccess)
        Assert.assertTrue(fixture.assertLesser("2124123123.9999998", "2124123124").isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun assertLesserOrEqual() {
        val fixture = NumberCommand()

        fixture.init(context)

        Assert.assertTrue(fixture.assertLesserOrEqual("1", "2").isSuccess)
        Assert.assertTrue(fixture.assertLesserOrEqual("2.00000", "2.").isSuccess)
        Assert.assertTrue(fixture.assertLesserOrEqual("-3.", "-0000000003").isSuccess)
        Assert.assertTrue(fixture.assertLesserOrEqual("2124123123.9999998", "2124123124").isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun assertBetween() {
        val fixture = NumberCommand()

        fixture.init(context)

        Assert.assertTrue(fixture.assertBetween("5", "1", "10").isSuccess)
        Assert.assertTrue(fixture.assertBetween("0.004", "0", "0.93211").isSuccess)
        Assert.assertTrue(fixture.assertBetween("0", "0", "0.011").isSuccess)
        Assert.assertTrue(fixture.assertBetween("0.1", "0", "0.1").isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun average() {
        val fixture = NumberCommand()

        fixture.init(context)

        val varName = "var1.1"

        Assert.assertTrue(fixture.average(varName, null).isSuccess)
        Assert.assertEquals(0.0, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        Assert.assertTrue(fixture.average(varName, "").isSuccess)
        Assert.assertEquals(0.0, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        Assert.assertTrue(fixture.average(varName, "this,is,not,a,number").isSuccess)
        Assert.assertEquals(0.0, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        // integer
        Assert.assertTrue(fixture.average(varName, "1,2,3").isSuccess)
        Assert.assertEquals("2.0", NumberUtils.toDouble(context.getStringData(varName)).toString())
//        Assert.assertEquals(2.0, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        Assert.assertTrue(fixture.average(varName, "1,2,3,4,5,6,7,8,9,10").isSuccess)
        Assert.assertEquals("5.5", NumberUtils.toDouble(context.getStringData(varName)).toString())
//        Assert.assertEquals(5.5, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        Assert.assertTrue(fixture.average(varName, "1212, 4, 68, 4, 2, 235, 234, 9, 8, 765, 5, 45, 63, 452").isSuccess)
        Assert.assertEquals(221.857142857143, NumberUtils.toDouble(context.getStringData(varName)), 0.000001)

        // negative integer
        Assert.assertTrue(fixture.average(varName, "7,-235,68,-331,-2903,235,234,-532,-9110,76,23,4,63,10").isSuccess)
        Assert.assertEquals(-885.0714286, NumberUtils.toDouble(context.getStringData(varName)), 0.000001)

        // decimals/negative
        Assert.assertTrue(fixture.average(varName, "4.241, 4.0001, 2.4, 4, 24.2, -104.11, 23, -46, 66,,,11,").isSuccess)
        Assert.assertEquals(-1.12689, NumberUtils.toDouble(context.getStringData(varName)), 00.000001)

        // wild wild west; expects non-number to be ignored
        Assert.assertTrue(fixture.average(varName, "1,.2,3.00,004,-105.00,6a,7b,blakhas,,,,").isSuccess)
        Assert.assertEquals(-12.1, NumberUtils.toDouble(context.getStringData(varName)), 0.000001)

        // mixing number types
        Assert.assertTrue(fixture.average(varName, "1,.2,3.0,004,-105.00,,,  ,52.214123,0,00.0,1,1.00,").isSuccess)
        Assert.assertEquals(-3.871443364, context.getDoubleData(varName), 0.00001)
    }

    @Test
    @Throws(Exception::class)
    fun testMax() {

        val fixture = NumberCommand()

        fixture.init(context)

        val varName = "var2"

        // empty, null, blank
        try {
            fixture.max(varName, null)
            Assert.fail("expects failure with null array")
        } catch (e: AssertionError) {
        }

        try {
            fixture.max(varName, "")
            Assert.fail("expects failure with empty array")
        } catch (e: AssertionError) {
        }

        try {
            fixture.max(varName, " ")
            Assert.fail("expects failure with blank")
        } catch (e: AssertionError) {
        }

        try {
            fixture.max(varName, "\t")
            Assert.fail("expects failure with only tab")
        } catch (e: AssertionError) {
        }

        // integer
        Assert.assertTrue(fixture.max(varName, "1,1,1").isSuccess)
        Assert.assertEquals("1.0", NumberUtils.toDouble(context.getStringData(varName)).toString())
        Assert.assertEquals("1.0", context.getStringData(varName))
//        Assert.assertEquals(1.0, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        // negative
        Assert.assertTrue(fixture.max(varName, "1,-16,5,0,3").isSuccess)
        Assert.assertEquals("5.0", NumberUtils.toDouble(context.getStringData(varName)).toString())
        Assert.assertEquals("5.0", context.getStringData(varName))
//        Assert.assertEquals(5.0, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        // decimals
        Assert.assertTrue(fixture.max(varName, "1,-16,5,0,3.002,-144,5.001").isSuccess)
        Assert.assertEquals("5.001", NumberUtils.toDouble(context.getStringData(varName)).toString())
        Assert.assertEquals("5.001", context.getStringData(varName))
//        Assert.assertEquals(5.001, NumberUtils.toDouble(context.getStringData(varName)), 0.0)
    }

    @Test
    @Throws(Exception::class)
    fun testMin() {
        val fixture = NumberCommand()

        fixture.init(context)

        val varName = "var3"

        // empty, null, blank
        try {
            fixture.min(varName, null)
            Assert.fail("expects failure with null array")
        } catch (e: AssertionError) {
        }

        try {
            fixture.min(varName, "")
            Assert.fail("expects failure with empty array")
        } catch (e: AssertionError) {
        }

        try {
            fixture.min(varName, " ")
            Assert.fail("expects failure with blank")
        } catch (e: AssertionError) {
        }

        try {
            fixture.min(varName, "\t")
            Assert.fail("expects failure with only tab")
        } catch (e: AssertionError) {
        }

        // integer
        Assert.assertTrue(fixture.min(varName, "1,1,1").isSuccess)
        Assert.assertEquals("1.0", NumberUtils.toDouble(context.getStringData(varName)).toString())
        Assert.assertEquals("1.0", context.getStringData(varName))
//        Assert.assertEquals(1.0, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        // negative
        Assert.assertTrue(fixture.min(varName, "1,-16,5,0,3").isSuccess)
        Assert.assertEquals("-16.0", NumberUtils.toDouble(context.getStringData(varName)).toString())
        Assert.assertEquals("-16.0", context.getStringData(varName))
//        Assert.assertEquals(-16.0, NumberUtils.toDouble(context.getStringData(varName)), 0.0)

        // decimals
        Assert.assertTrue(fixture.min(varName, "1,-16,5,0,3.002,-144,5.001").isSuccess)
        Assert.assertEquals("-144.0", NumberUtils.toDouble(context.getStringData(varName)).toString())
        Assert.assertEquals("-144.0", context.getStringData(varName))
    }

    @Test
    @Throws(Exception::class)
    fun testCeiling() {
        val fixture = NumberCommand()

        fixture.init(context)

        val varName = "var4"

        // null/empty/blank
        try {
            context.removeData(varName)
            fixture.ceiling(varName)
            Assert.fail("expected failure due to null value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(varName, "")
            fixture.ceiling(varName)
            Assert.fail("expected failure due to empty value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(varName, " ")
            fixture.ceiling(varName)
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

        // happy path
        context.setData(varName, 1)
        Assert.assertTrue(fixture.ceiling(varName).isSuccess)
        Assert.assertEquals("1", context.getStringData(varName))

        context.setData(varName, 56.00024)
        Assert.assertTrue(fixture.ceiling(varName).isSuccess)
        Assert.assertEquals("57", context.getStringData(varName))

        context.setData(varName, 0.0950)
        Assert.assertTrue(fixture.ceiling(varName).isSuccess)
        Assert.assertEquals("1", context.getStringData(varName))

        // negative
        context.setData(varName, -503.124)
        Assert.assertTrue(fixture.ceiling(varName).isSuccess)
        Assert.assertEquals("-503", context.getStringData(varName))

        // NaN
        try {
            context.setData(varName, "not.a.number")
            fixture.ceiling(varName)
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

    }

    @Test
    @Throws(Exception::class)
    fun testFloor() {
        val fixture = NumberCommand()

        fixture.init(context)

        val variableName = "var5"

        // null/empty/blank
        try {
            context.removeData(variableName)
            fixture.floor(variableName)
            Assert.fail("expected failure due to null value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, "")
            fixture.floor(variableName)
            Assert.fail("expected failure due to empty value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, " ")
            fixture.floor(variableName)
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }


        // happy path
        context.setData(variableName, 1)
        Assert.assertTrue(fixture.floor(variableName).isSuccess)
        Assert.assertEquals("1", context.getStringData(variableName))

        context.setData(variableName, 56.00024)
        Assert.assertTrue(fixture.floor(variableName).isSuccess)
        Assert.assertEquals("56", context.getStringData(variableName))

        context.setData(variableName, 0.0950)
        Assert.assertTrue(fixture.floor(variableName).isSuccess)
        Assert.assertEquals("0", context.getStringData(variableName))

        // negative
        context.setData(variableName, -503.124)
        Assert.assertTrue(fixture.floor(variableName).isSuccess)
        Assert.assertEquals("-504", context.getStringData(variableName))

        // NaN
        try {
            context.setData(variableName, "not.a.number")
            fixture.floor(variableName)
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

    }

    @Test
    @Throws(Exception::class)
    fun testRoundTo() {
        val fixture = NumberCommand()

        fixture.init(context)

        val variableName = "var6"

        // null/empty/blank
        try {
            context.removeData(variableName)
            fixture.roundTo(variableName, "1")
            Assert.fail("expected failure due to null value")
        } catch (e: AssertionError) {
        }


        try {
            context.setData(variableName, "")
            fixture.roundTo(variableName, "1")
            Assert.fail("expected failure due to empty value")
        } catch (e: AssertionError) {
        }


        try {
            context.setData(variableName, " ")
            fixture.roundTo(variableName, "1")
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, 1)
            fixture.roundTo(variableName, "")
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

        // happy path
        context.setData(variableName, 1)
        Assert.assertTrue(fixture.roundTo(variableName, "1").isSuccess)
        Assert.assertEquals(1.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 1)
        Assert.assertTrue(fixture.roundTo(variableName, "0.1").isSuccess)
        Assert.assertEquals(1.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 1)
        Assert.assertTrue(fixture.roundTo(variableName, "10").isSuccess)
        Assert.assertEquals(0.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 56.00024)
        Assert.assertTrue(fixture.roundTo(variableName, "10").isSuccess)
        Assert.assertEquals(60.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 353.254)
        Assert.assertTrue(fixture.roundTo(variableName, "90").isSuccess)
        Assert.assertEquals(350.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 353.265)
        Assert.assertTrue(fixture.roundTo(variableName, "0.00").isSuccess)
        Assert.assertEquals(353.27, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 353.254)
        Assert.assertTrue(fixture.roundTo(variableName, "0.01").isSuccess)
        Assert.assertEquals(353.25, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 353.256)
        Assert.assertTrue(fixture.roundTo(variableName, "1.00").isSuccess)
        Assert.assertEquals(353.26, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 56.00024)
        Assert.assertTrue(fixture.roundTo(variableName, "1").isSuccess)
        Assert.assertEquals(56.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, 0.0960)
        Assert.assertTrue(fixture.roundTo(variableName, "0.1").isSuccess)
        Assert.assertEquals(0.1, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        // negative
        context.setData(variableName, -503.124)
        Assert.assertTrue(fixture.roundTo(variableName, "0.001").isSuccess)
        Assert.assertEquals(-503.124, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        // NaN
        try {
            context.setData(variableName, "not.a.number")
            fixture.roundTo(variableName, "1.0")
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

    }

    @Test
    @Throws(Exception::class)
    fun testWhole() {
        val fixture = NumberCommand()

        fixture.init(context)

        val variableName = "var7"

        // null/empty/blank
        try {
            context.removeData(variableName)
            fixture.whole(variableName)
            Assert.fail("expected failure due to null value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, "")
            fixture.whole(variableName)
            Assert.fail("expected failure due to empty value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, " ")
            fixture.whole(variableName)
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

        // happy path
        context.setData(variableName, 1)
        Assert.assertTrue(fixture.whole(variableName).isSuccess)
        Assert.assertEquals(1, NumberUtils.toInt(context.getStringData(variableName)))

        context.setData(variableName, 353.465)
        Assert.assertTrue(fixture.whole(variableName).isSuccess)
        Assert.assertEquals(353, NumberUtils.toInt(context.getStringData(variableName)))

        context.setData(variableName, 353.565)
        Assert.assertTrue(fixture.whole(variableName).isSuccess)
        Assert.assertEquals(354, NumberUtils.toInt(context.getStringData(variableName)))

        context.setData(variableName, 0.0960)
        Assert.assertTrue(fixture.whole(variableName).isSuccess)
        Assert.assertEquals(0, NumberUtils.toInt(context.getStringData(variableName)))

        context.setData(variableName, 0.960)
        Assert.assertTrue(fixture.whole(variableName).isSuccess)
        Assert.assertEquals(1, NumberUtils.toInt(context.getStringData(variableName)))

        // negative
        context.setData(variableName, -503.624)
        Assert.assertTrue(fixture.whole(variableName).isSuccess)
        Assert.assertEquals(-504, NumberUtils.toInt(context.getStringData(variableName)))

        // NaN
        try {
            context.setData(variableName, "not.a.number")
            fixture.whole(variableName)
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

    }

    @Test
    @Throws(Exception::class)
    fun testIncrement() {
        val fixture = NumberCommand()

        fixture.init(context)

        val variableName = "var8"

        // null/empty/blank
        try {
            context.removeData(variableName)
            fixture.increment(variableName, "1")
            Assert.fail("expected failure due to null value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, "")
            fixture.increment(variableName, "1")
            Assert.fail("expected failure due to empty value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, " ")
            fixture.increment(variableName, "1")
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, "1")
            fixture.increment(variableName, "")
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

        // happy path
        context.setData(variableName, "1")
        Assert.assertTrue(fixture.increment(variableName, "1").isSuccess)
        Assert.assertEquals(2.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, "0")
        Assert.assertTrue(fixture.increment(variableName, "5.634").isSuccess)
        Assert.assertEquals(5.634, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, "13.1567")
        Assert.assertTrue(fixture.increment(variableName, "-5.634").isSuccess)
        Assert.assertEquals(7.5227, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, "0")
        Assert.assertTrue(fixture.increment(variableName, "0").isSuccess)
        Assert.assertEquals(0.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

    }

    @Test
    @Throws(Exception::class)
    fun testDecrement() {
        val fixture = NumberCommand()

        fixture.init(context)

        val variableName = "var9"

        // null/empty/blank
        try {
            context.removeData(variableName)
            fixture.decrement(variableName, "1")
            Assert.fail("expected failure due to null value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, "")
            fixture.decrement(variableName, "1")
            Assert.fail("expected failure due to empty value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, " ")
            fixture.decrement(variableName, "1")
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

        try {
            context.setData(variableName, "1")
            fixture.decrement(variableName, "")
            Assert.fail("expected failure due to blank value")
        } catch (e: AssertionError) {
        }

        // happy path
        context.setData(variableName, "1")
        Assert.assertTrue(fixture.decrement(variableName, "1").isSuccess)
        Assert.assertEquals(0.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, "0")
        Assert.assertTrue(fixture.decrement(variableName, "5.634").isSuccess)
        Assert.assertEquals(-5.634, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, "13.1567")
        Assert.assertTrue(fixture.decrement(variableName, "-5.634").isSuccess)
        Assert.assertEquals(18.7907, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

        context.setData(variableName, "0")
        Assert.assertTrue(fixture.decrement(variableName, "0").isSuccess)
        Assert.assertEquals(0.0, NumberUtils.toDouble(context.getStringData(variableName)), 0.0)

    }

    @Test
    fun testAdd() {
        val strings = TextUtils.toList("1,2,3,4,5,6,7,8,9,10", ",", true)
        val result = strings.foldRight(0.0, { s, acc -> NumberUtils.toDouble(s) + acc })
        println("result = ${result}")
    }

}
