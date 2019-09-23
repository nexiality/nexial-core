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

package org.nexial.core.variable;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class FormatTest {

	@Test
	public void testToNumeric() {
		Assert.assertNull(Format.toNumeric(null));
		Assert.assertEquals("", Format.toNumeric(""));
		Assert.assertEquals("1", Format.toNumeric("1"));
		Assert.assertEquals("123", Format.toNumeric("123"));
		Assert.assertEquals("0000123", Format.toNumeric("0000123"));
		Assert.assertEquals("123", Format.toNumeric(" 123 "));
		Assert.assertEquals("1230", Format.toNumeric(" 123 0 "));
		Assert.assertEquals("1230", Format.toNumeric(" 123a 0 "));
		Assert.assertEquals("1230", Format.toNumeric(" a123a 0 "));
		Assert.assertEquals("1230", Format.toNumeric(" a-123a 0 "));
		Assert.assertEquals("1230", Format.toNumeric(" a-b123a 0 "));
		Assert.assertEquals("1230", Format.toNumeric(" a-b1rt2a3a 0 "));
		Assert.assertEquals("1230", Format.toNumeric(" a-b1r 2 3a 0 "));
		Assert.assertEquals("-1230", Format.toNumeric(" -12 3a 0 "));
		Assert.assertEquals("-1230", Format.toNumeric(" -123data here0 "));
		Assert.assertEquals("-1230", Format.toNumeric(" -1230 "));
		Assert.assertEquals("-1230.0", Format.toNumeric(" -1230.0 "));
		Assert.assertEquals("-1230.0144", Format.toNumeric(" -1230.01a44 "));
		Assert.assertEquals("-1230.0144", Format.toNumeric(" --1230.01a44 "));
		Assert.assertEquals("1230.0144", Format.toNumeric(" +1230.01a44 "));
	}

	@Test
	public void testInteger() {
		Format f = new Format();
		Assert.assertNull(f.integer(null));
		Assert.assertEquals("", f.integer(""));
		Assert.assertEquals("1", f.integer("1"));
		Assert.assertEquals("1234", f.integer("1234"));
		Assert.assertEquals("1234", f.integer("1234.0"));
		Assert.assertEquals("1234", f.integer("1234.01"));
		Assert.assertEquals("1234", f.integer("1234.444"));
		Assert.assertEquals("-1234", f.integer("-1234.444"));
		Assert.assertEquals("-1234", f.integer("-1a2b3c4d.4e4f4g"));
		Assert.assertEquals("-1234", f.integer("-1a2     b3c4d.4e4f4g   "));
		Assert.assertEquals("1234", f.integer("+1a2     b3c4d.4e4f4g   "));
	}

	@Test
	public void testNumber() {
		Format f = new Format();
		Assert.assertNull(f.number(null, null));
		Assert.assertEquals("", f.number("", null));
		Assert.assertEquals("", f.number("", ""));
		Assert.assertEquals("1", f.number("1", ""));
		Assert.assertEquals("1.0", f.number("1.0", ""));
		Assert.assertEquals("1234", f.number("1234", ""));
		Assert.assertEquals("1234.0", f.number("1234.0", ""));
		Assert.assertEquals("1234.0", f.number("1234a.b0c", ""));
		Assert.assertEquals("1234.0", f.number("1-234a.   b0c", ""));
		Assert.assertEquals("1,234", f.number("1-234a.   b0c", "###,###.##"));
		Assert.assertEquals("1,234.01", f.number("1-234a.   b01c", "###,###.##"));
		Assert.assertEquals("1,234.00", f.number("1-234a.   b0c", "###,###.00"));
		Assert.assertEquals("1,494.23", f.number("The total amount due is $1494.23", "###,###.00"));
		Assert.assertEquals("1,494.00", f.number("The total amount due is $1494.00", "###,###.00"));
		Assert.assertEquals("1,494", f.number("The total amount due is $1494.00", "###,###.##"));
	}

	@Test
	public void testPercent() {
		Format f = new Format();
		Assert.assertNull(f.percent(null));
		Assert.assertEquals("", f.percent(""));
		Assert.assertEquals("", f.percent(""));
		Assert.assertEquals("100%", f.percent("1"));
		Assert.assertEquals("100%", f.percent("1.0"));
		Assert.assertEquals("123,400%", f.percent("1234"));
		Assert.assertEquals("123,400%", f.percent("1234.0"));
		Assert.assertEquals("123,400%", f.percent("1234a.b0c"));
		Assert.assertEquals("123,400%", f.percent("1-234a.   b0c"));
		Assert.assertEquals("123,400%", f.percent("1-234a.   b0c"));
		Assert.assertEquals("123,401%", f.percent("1-234a.   b01c"));
		Assert.assertEquals("123,400%", f.percent("1-234a.   b0c"));
		Assert.assertEquals("149,423%", f.percent("The total amount due is $1494.23"));
		Assert.assertEquals("149,400%", f.percent("The total amount due is $1494.00"));
		Assert.assertEquals("149,400%", f.percent("The total amount due is $1494.00"));

		Assert.assertEquals("51%", f.percent("The difference is 0.51a"));
		Assert.assertEquals("0.51%", f.percent("The difference is 0.0051a"));
		Assert.assertEquals("0.05%", f.percent("The difference is 0.000511 or so"));
		Assert.assertEquals("0.06%", f.percent("The difference is 0.000551 or so"));
	}

	@Test
	public void testDollar() {
		Format f = new Format();
		Assert.assertNull(f.dollar(null));
		Assert.assertEquals("", f.dollar(""));
		Assert.assertEquals("$1.00", f.dollar("1"));
		Assert.assertEquals("$1.00", f.dollar("1.0"));
		Assert.assertEquals("$1.02", f.dollar("1.02"));
		Assert.assertEquals("$2,121.02", f.dollar("2121.02"));
		Assert.assertEquals("$2,121.02", f.dollar("2d1a-2b1.-02"));
		Assert.assertEquals("$2,121.02", f.dollar("2d1a-2b1.-02424"));
		Assert.assertEquals("$2,121.03", f.dollar("2d1a-2b1.-02532"));
	}

	@Test
	public void testSsn() {
		Format f = new Format();
		Assert.assertNull(f.ssn(null));
		Assert.assertEquals("", f.ssn(""));
		Assert.assertEquals("123-45-67890", f.ssn("1234567890"));
		Assert.assertEquals("123-45-67890ABC", f.ssn("1234567890ABC"));
		Assert.assertEquals("123-45-678", f.ssn("12345678"));
		Assert.assertEquals("123-45-6", f.ssn("123456"));
		Assert.assertEquals("123", f.ssn("123"));
		Assert.assertEquals("13", f.ssn("13"));
	}

	@Test
	public void testMask() {
		Format f = new Format();
		Assert.assertNull(f.mask(null, "0", "0", ""));
		Assert.assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "0", "0", ""));
		Assert.assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "A", "0", ""));
		Assert.assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "0", "B", ""));
		Assert.assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "0", "-2", ""));
		Assert.assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "-1", "5", ""));
		Assert.assertEquals("Jolly Rancher", f.mask("Jolly Rancher", "8", "5", ""));
		Assert.assertEquals("##lly Rancher", f.mask("Jolly Rancher", "0", "2", ""));
		Assert.assertEquals("Jolly Rooooor", f.mask("Jolly Rancher", "7", "12", "o"));
		Assert.assertEquals("Jolly Roooooo", f.mask("Jolly Rancher", "7", "15", "o"));
	}

	@Test
	public void testPhone() {
		Format f = new Format();
		Assert.assertNull(f.phone(null));
		Assert.assertEquals("", f.phone(""));
		Assert.assertEquals("123-4567", f.phone("1234567"));
		Assert.assertEquals("2-123-4567", f.phone("21234567"));
		Assert.assertEquals("(213)123-4567", f.phone("2131234567"));
		Assert.assertEquals("9(213)123-4567", f.phone("92131234567"));
		Assert.assertEquals("12345", f.phone("12345"));
		Assert.assertEquals("125", f.phone("125"));
		Assert.assertEquals("123(456)789-0ABC", f.phone("1234567890ABC"));
	}

	@Test
	public void testCustom() {
		Format f = new Format();
		Assert.assertNull(f.custom(null, ""));
		Assert.assertEquals("", f.custom("", ""));
		Assert.assertEquals("ABC", f.custom("ABC", ""));
		Assert.assertEquals("A-B-C", f.custom("ABC", "`-`-`"));
		Assert.assertEquals("(123) 456-7890", f.custom("1234567890", "(```) ```-````"));
		Assert.assertEquals("123-4567", f.custom("1234567890", "```-````"));
		Assert.assertEquals("123-456-7890", f.custom("1234567890", "```-```-````"));
		Assert.assertEquals("123-45-6789", f.custom("123456789", "```-``-````"));
	}

	@Test
	public void testStrip() {
		Format f = new Format();
		Assert.assertNull(f.strip(null, null));
		Assert.assertNull(f.strip(null, ""));
		Assert.assertNull(f.strip(null, " "));
		Assert.assertNull(f.strip(null, "a"));
		Assert.assertEquals("Hnbrown cow?", f.strip("How now brown cow?", "ow "));
	}
}
