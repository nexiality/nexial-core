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

package org.nexial.commons.utils;

import java.text.ParseException;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.commons.lang3.time.DateUtils.parseDate;
import static org.junit.Assert.assertEquals;
import static org.nexial.commons.utils.SocialDateText.since;

public class SocialDateTextTest {
	public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

	@Test
	public void testSince() throws Exception {
		Assert.assertEquals("just now", SocialDateText
                                            .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:05:01")));
		Assert.assertEquals("just now", SocialDateText
                                            .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:05:30")));
		Assert.assertEquals("just a minute ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:05:31")));
		Assert.assertEquals("just a minute ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:06:15")));
		Assert.assertEquals("a few minutes ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:06:16")));
		Assert.assertEquals("about 5 minutes ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:10:00")));
		Assert.assertEquals("about 10 minutes ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:13:00")));
		Assert.assertEquals("about 10 minutes ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:14:58")));
		Assert.assertEquals("about 10 minutes ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:15:00")));
		Assert.assertEquals("13 minutes ago", SocialDateText
                                                  .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:18:00")));
		Assert.assertEquals("15 minutes ago", SocialDateText
                                                  .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:20:00")));
		Assert.assertEquals("17 minutes ago", SocialDateText
                                                  .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:22:00")));
		Assert.assertEquals("30 minutes ago", SocialDateText
                                                  .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:35:00")));
		Assert.assertEquals("32 minutes ago", SocialDateText
                                                  .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 00:37:00")));
		Assert.assertEquals("58 minutes ago", SocialDateText
                                                  .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 01:03:00")));
		Assert.assertEquals("1 hour ago", SocialDateText
                                              .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 01:08:00")));
		Assert.assertEquals("1 hour ago", SocialDateText
                                              .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 01:10:00")));
		Assert.assertEquals("2 hours ago", SocialDateText
                                               .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 02:10:00")));
		Assert.assertEquals("about 2.5 hours ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 02:34:00")));
		Assert.assertEquals("about 2.5 hours ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 02:35:00")));
		Assert.assertEquals("about 2.5 hours ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 02:41:00")));
		Assert.assertEquals("3 hours ago", SocialDateText
                                               .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 03:21:00")));
		Assert.assertEquals("9 hours ago", SocialDateText
                                               .since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 09:13:00")));
		Assert.assertEquals("about 12.5 hours ago", SocialDateText.since(toDate("2013-08-03 00:05:00"), toDate("2013-08-03 12:41:00")));
	}

	private static Date toDate(String date) throws ParseException { return parseDate(date, DATE_FORMAT); }
}
