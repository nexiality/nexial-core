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

package org.nexial.commons.utils;

import org.apache.http.client.utils.DateUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class DateUtilityTest {

	@Test
	public void testFormatTo() {
		try {
			System.out.println(DateUtility.formatTo("06/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}

		try {
			System.out.println(DateUtility.formatTo("2017/04/01 05:15:24", "MM/dd/yyyy HH:mm:ss"));
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}

		try {
			System.out.println(DateUtility.formatTo("2017/04/01 09:4:3", "MM/dd/yyyy HH:mm:ss"));
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}

		try {
			assertEquals(DateUtility.formatTo("11/19/2021 12:00:00 PM", "M/dd/yyyy hh:mm:ss a", "MM/dd/yyyy"), "11/19/2021");
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}

	}

	@Test
	public void testFormatFullMonth() {
		Date fixture = new Date(DateUtility.formatTo("01/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		String test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("January", test);

		fixture = new Date(DateUtility.formatTo("02/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("February", test);

		fixture = new Date(DateUtility.formatTo("03/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("March", test);

		fixture = new Date(DateUtility.formatTo("04/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("April", test);

		fixture = new Date(DateUtility.formatTo("05/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("May", test);

		fixture = new Date(DateUtility.formatTo("06/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("June", test);

		fixture = new Date(DateUtility.formatTo("07/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("July", test);

		fixture = new Date(DateUtility.formatTo("08/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("August", test);

		fixture = new Date(DateUtility.formatTo("09/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("September", test);

		fixture = new Date(DateUtility.formatTo("10/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("October", test);

		fixture = new Date(DateUtility.formatTo("11/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("November", test);

		fixture = new Date(DateUtility.formatTo("12/06/2012 04:34:12 AM", "MM/dd/yyyy hh:mm:ss a"));
		test = DateUtils.formatDate(fixture, "MMMM");
		assertEquals("December", test);

	}

	@Test
	public void formatTime() {
		assertEquals("00:00:00", DateUtility.formatTime(4));
	}

	@Test
	public void formatCookieDate() {

		Date date = new Date(DateUtility.formatTo("Wed, 09 Nov 2016 16:16:21 GMT", "EEE, dd MMM yyyy HH:mm:ss ZZZ"));
		System.out.println("date = " + date);
		String dateString = DateUtility.format(date.getTime(), "yyyy/MM/dd HH:mm:ss");
		assertEquals("2016/11/09 08:16:21", dateString);

	}
}
