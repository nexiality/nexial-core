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
 *
 */

package org.nexial.core.plugins.base;

import org.apache.commons.lang3.math.NumberUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;

public class NumberCommandTest {
	ExecutionContext context = new MockExecutionContext();

	@After
	public void tearDown() {
		if (context != null) { ((MockExecutionContext) context).cleanProject(); }
	}

	@Test
	public void testAssertGreater() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		Assert.assertTrue(fixture.assertGreater("2", "1").isSuccess());
		Assert.assertTrue(fixture.assertGreater("2.1", "2.00000").isSuccess());
		Assert.assertTrue(fixture.assertGreater("-3", "-3.0001").isSuccess());
		Assert.assertTrue(fixture.assertGreater("2124123124", "2124123123.9999998").isSuccess());
	}

	@Test
	public void testAssertGreaterOrEqual() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		Assert.assertTrue(fixture.assertGreaterOrEqual("1", "1").isSuccess());
		Assert.assertTrue(fixture.assertGreaterOrEqual("2.1", "2.10000").isSuccess());
		Assert.assertTrue(fixture.assertGreaterOrEqual("-3.000", "-03.").isSuccess());
		Assert.assertTrue(fixture.assertGreaterOrEqual("001.0001", "1.00010000").isSuccess());
	}

	@Test
	public void testAssertLesser() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		Assert.assertTrue(fixture.assertLesser("1", "2").isSuccess());
		Assert.assertTrue(fixture.assertLesser("2.00000", "2.1").isSuccess());
		Assert.assertTrue(fixture.assertLesser("-3.0001", "-3").isSuccess());
		Assert.assertTrue(fixture.assertLesser("2124123123.9999998", "2124123124").isSuccess());
	}

	@Test
	public void testAssertLesserOrEqual() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		Assert.assertTrue(fixture.assertLesserOrEqual("1", "2").isSuccess());
		Assert.assertTrue(fixture.assertLesserOrEqual("2.00000", "2.").isSuccess());
		Assert.assertTrue(fixture.assertLesserOrEqual("-3.", "-0000000003").isSuccess());
		Assert.assertTrue(fixture.assertLesserOrEqual("2124123123.9999998", "2124123124").isSuccess());
	}

	@Test
	public void testAverage() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		String var = "var1";

		Assert.assertTrue(fixture.average(var, null).isSuccess());
		Assert.assertEquals(0, NumberUtils.toDouble(context.getStringData(var)), 0);

		Assert.assertTrue(fixture.average(var, "").isSuccess());
		Assert.assertEquals(0, NumberUtils.toDouble(context.getStringData(var)), 0);

		Assert.assertTrue(fixture.average(var, "this,is,not,a,number").isSuccess());
		Assert.assertEquals(0, NumberUtils.toDouble(context.getStringData(var)), 0);

		// integer
		Assert.assertTrue(fixture.average(var, "1,2,3").isSuccess());
		Assert.assertEquals(2, NumberUtils.toDouble(context.getStringData(var)), 0);

		Assert.assertTrue(fixture.average(var, "1,2,3,4,5,6,7,8,9,10").isSuccess());
		Assert.assertEquals(5.5, NumberUtils.toDouble(context.getStringData(var)), 0);

		Assert.assertTrue(fixture.average(var, "1212, 4, 68, 4, 2, 235, 234, 9, 8, 765, 5, 45, 63, 452").isSuccess());
		Assert.assertEquals(221.857142857143, NumberUtils.toDouble(context.getStringData(var)), 0.000001);

		// negative integer
		Assert.assertTrue(fixture.average(var, "7,-235,68,-331,-2903,235,234,-532,-9110,76,23,4,63,10").isSuccess());
		Assert.assertEquals(-885.0714286, NumberUtils.toDouble(context.getStringData(var)), 0.000001);

		// decimals/negative
		Assert.assertTrue(fixture.average(var, "4.241, 4.0001, 2.4, 4, 24.2, -104.11, 23, -46, 66,,,11,").isSuccess());
		Assert.assertEquals(-1.12689, NumberUtils.toDouble(context.getStringData(var)), 00.000001);

		// wild wild west; expects non-number to be ignored
		Assert.assertTrue(fixture.average(var, "1,.2,3.00,004,-105.00,6a,7b,blakhas,,,,").isSuccess());
		Assert.assertEquals(-12.1, NumberUtils.toDouble(context.getStringData(var)), 0.000001);

	}

	@Test
	public void testMax() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		String var = "var1";

		// empty, null, blank
		try {
			fixture.max(var, null);
			Assert.fail("expects failure with null array");
		} catch (AssertionError e) { }
		try {
			fixture.max(var, "");
			Assert.fail("expects failure with empty array");
		} catch (AssertionError e) { }
		try {
			fixture.max(var, " ");
			Assert.fail("expects failure with blank");
		} catch (AssertionError e) { }
		try {
			fixture.max(var, "\t");
			Assert.fail("expects failure with only tab");
		} catch (AssertionError e) { }

		// integer
		Assert.assertTrue(fixture.max(var, "1,1,1").isSuccess());
		Assert.assertEquals(1, NumberUtils.toDouble(context.getStringData(var)), 0);

		// negative
		Assert.assertTrue(fixture.max(var, "1,-16,5,0,3").isSuccess());
		Assert.assertEquals(5, NumberUtils.toDouble(context.getStringData(var)), 0);

		// decimals
		Assert.assertTrue(fixture.max(var, "1,-16,5,0,3.002,-144,5.001").isSuccess());
		Assert.assertEquals(5.001, NumberUtils.toDouble(context.getStringData(var)), 0);
	}

	@Test
	public void testMin() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		String var = "var1";

		// empty, null, blank
		try {
			fixture.min(var, null);
			Assert.fail("expects failure with null array");
		} catch (AssertionError e) { }
		try {
			fixture.min(var, "");
			Assert.fail("expects failure with empty array");
		} catch (AssertionError e) { }
		try {
			fixture.min(var, " ");
			Assert.fail("expects failure with blank");
		} catch (AssertionError e) { }
		try {
			fixture.min(var, "\t");
			Assert.fail("expects failure with only tab");
		} catch (AssertionError e) { }

		// integer
		Assert.assertTrue(fixture.min(var, "1,1,1").isSuccess());
		Assert.assertEquals(1, NumberUtils.toDouble(context.getStringData(var)), 0);

		// negative
		Assert.assertTrue(fixture.min(var, "1,-16,5,0,3").isSuccess());
		Assert.assertEquals(-16, NumberUtils.toDouble(context.getStringData(var)), 0);

		// decimals
		Assert.assertTrue(fixture.min(var, "1,-16,5,0,3.002,-144,5.001").isSuccess());
		Assert.assertEquals(-144, NumberUtils.toDouble(context.getStringData(var)), 0);
	}

	@Test
	public void testCeiling() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		String var = "var1";

		// null/empty/blank
		try {
			context.removeData(var);
			fixture.ceiling(var);
			Assert.fail("expected failure due to null value");
		} catch (AssertionError e) { }
		try {
			context.setData(var, "");
			fixture.ceiling(var);
			Assert.fail("expected failure due to empty value");
		} catch (AssertionError e) { }
		try {
			context.setData(var, " ");
			fixture.ceiling(var);
			Assert.fail("expected failure due to blank value");
		} catch (AssertionError e) { }

		// happy path
		context.setData(var, 1);
		Assert.assertTrue(fixture.ceiling(var).isSuccess());
		Assert.assertEquals("1", context.getStringData(var));

		context.setData(var, 56.00024);
		Assert.assertTrue(fixture.ceiling(var).isSuccess());
		Assert.assertEquals("57", context.getStringData(var));

		context.setData(var, 0.0950);
		Assert.assertTrue(fixture.ceiling(var).isSuccess());
		Assert.assertEquals("1", context.getStringData(var));

		// negative
		context.setData(var, -503.124);
		Assert.assertTrue(fixture.ceiling(var).isSuccess());
		Assert.assertEquals("-503", context.getStringData(var));

		// NaN
		try {
			context.setData(var, "not.a.number");
			fixture.ceiling(var);
			Assert.fail("expected failure due to blank value");
		} catch (AssertionError e) { }
	}

	@Test
	public void testFloor() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		String var = "var1";

		// null/empty/blank
		try {
			context.removeData(var);
			fixture.floor(var);
			Assert.fail("expected failure due to null value");
		} catch (AssertionError e) { }
		try {
			context.setData(var, "");
			fixture.floor(var);
			Assert.fail("expected failure due to empty value");
		} catch (AssertionError e) { }
		try {
			context.setData(var, " ");
			fixture.floor(var);
			Assert.fail("expected failure due to blank value");
		} catch (AssertionError e) { }

		// happy path
		context.setData(var, 1);
		Assert.assertTrue(fixture.floor(var).isSuccess());
		Assert.assertEquals("1", context.getStringData(var));

		context.setData(var, 56.00024);
		Assert.assertTrue(fixture.floor(var).isSuccess());
		Assert.assertEquals("56", context.getStringData(var));

		context.setData(var, 0.0950);
		Assert.assertTrue(fixture.floor(var).isSuccess());
		Assert.assertEquals("0", context.getStringData(var));

		// negative
		context.setData(var, -503.124);
		Assert.assertTrue(fixture.floor(var).isSuccess());
		Assert.assertEquals("-504", context.getStringData(var));

		// NaN
		try {
			context.setData(var, "not.a.number");
			fixture.floor(var);
			Assert.fail("expected failure due to blank value");
		} catch (AssertionError e) { }
	}

	@Test
	public void testRound() throws Exception {
		NumberCommand fixture = new NumberCommand();
		fixture.init(context);

		String var = "var1";

		// null/empty/blank
		try {
			context.removeData(var);
			fixture.round(var, "1");
			Assert.fail("expected failure due to null value");
		} catch (AssertionError e) { }

		try {
			context.setData(var, "");
			fixture.round(var, "1");
			Assert.fail("expected failure due to empty value");
		} catch (AssertionError e) { }

		try {
			context.setData(var, " ");
			fixture.round(var, "1");
			Assert.fail("expected failure due to blank value");
		} catch (AssertionError e) { }

		try {
			context.setData(var, 1);
			fixture.round(var, "");
			Assert.fail("expected failure due to blank value");
		} catch (AssertionError e) { }

		// happy path
		context.setData(var, 1);
		Assert.assertTrue(fixture.round(var, "1").isSuccess());
		Assert.assertEquals(1, NumberUtils.toDouble(context.getStringData(var)), 0);

		context.setData(var, 1);
		Assert.assertTrue(fixture.round(var, "0.1").isSuccess());
		Assert.assertEquals(1, NumberUtils.toDouble(context.getStringData(var)), 0);

		context.setData(var, 1);
		Assert.assertTrue(fixture.round(var, "10").isSuccess());
		Assert.assertEquals(0, NumberUtils.toDouble(context.getStringData(var)), 0);

		context.setData(var, 56.00024);
		Assert.assertTrue(fixture.round(var, "10").isSuccess());
		Assert.assertEquals(60, NumberUtils.toDouble(context.getStringData(var)), 0);

		context.setData(var, 56.00024);
		Assert.assertTrue(fixture.round(var, "1").isSuccess());
		Assert.assertEquals(56, NumberUtils.toDouble(context.getStringData(var)), 0);

		context.setData(var, 0.0960);
		Assert.assertTrue(fixture.round(var, "0.1").isSuccess());
		Assert.assertEquals(0.1, NumberUtils.toDouble(context.getStringData(var)), 0);

		// negative
		context.setData(var, -503.124);
		Assert.assertTrue(fixture.round(var, "0.001").isSuccess());
		Assert.assertEquals(-503.124, NumberUtils.toDouble(context.getStringData(var)), 0);

		// NaN
		try {
			context.setData(var, "not.a.number");
			fixture.round(var, "1.0");
			Assert.fail("expected failure due to blank value");
		} catch (AssertionError e) { }
	}

	// @Test
	// public void testIncrement() throws Exception {
	// 	NumberCommand fixture = new NumberCommand();
	// 	fixture.init(context);
    //
	// 	String var = "var1";
	// }

}
