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

package org.nexial.core.tools;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class DateTest {

	@Test
	public void testISODateFormat() throws Exception {
		DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z '('zzzz')'");
		String dateString = "Fri May 02 2014 12:57:51 GMT-0700 (Pacific Daylight Time)";
		Assert.assertEquals("Fri May 02 12:57:51 PDT 2014", dateFormat.parse(dateString).toString());
	}
}
