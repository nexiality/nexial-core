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

import java.text.ParseException;
import java.util.Date;

import org.junit.Test;

import static org.apache.commons.lang3.time.DateUtils.parseDate;
import static org.junit.Assert.assertEquals;
import static org.nexial.commons.utils.SocialDateText.since;

public class SocialDateTextTest {
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Test
    public void testSince() throws Exception {
        assertEquals("just now", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:05:01")));
        assertEquals("just now", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:05:30")));
        assertEquals("just a minute ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:05:31")));
        assertEquals("just a minute ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:06:15")));
        assertEquals("a few minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:06:16")));

        assertEquals("about 5 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:10:00")));
        assertEquals("about 8 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:13:00")));
        assertEquals("about 9 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:14:58")));
        assertEquals("10 minutes and 20 seconds ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:15:20")));
        assertEquals("10 minutes and 40 seconds ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:15:40")));
        assertEquals("10 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:15:00")));
        assertEquals("13 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:18:00")));
        assertEquals("15 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:20:00")));
        assertEquals("17 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:22:00")));
        assertEquals("30 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:35:00")));
        assertEquals("32 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 00:37:00")));
        assertEquals("58 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 01:03:00")));

        assertEquals("about 1 hour ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 01:08:00")));
        assertEquals("about 1 hour and 5 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 01:10:00")));
        assertEquals("about 2 hours and 5 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 02:10:00")));
        assertEquals("about 2 hours and 5 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 02:10:16")));
        assertEquals("about 2 hours and 5 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 02:10:24")));
        assertEquals("about 2 and a half hours ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 02:34:00")));
        assertEquals("about 2 and a half hours ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 02:35:00")));
        assertEquals("about 2 hours and 36 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 02:41:00")));
        assertEquals("about 3 hours and 16 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 03:21:00")));
        assertEquals("about 9 hours and 8 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 09:13:00")));
        assertEquals("about 12 and a half hours ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 12:39:00")));
        assertEquals("about 12 hours and 35 minutes ago", since(d("2013-08-03 00:05:00"), d("2013-08-03 12:40:00")));

        assertEquals("about 1 and a half day later", since(d("2013-08-03 00:05:00"), d("2013-08-01 12:41:00")));
        assertEquals("about 1 and a half day ago", since(d("2013-08-03 00:05:00"), d("2013-08-04 12:41:00")));
        assertEquals("about 11 and a half days ago", since(d("2013-08-03 00:05:00"), d("2013-08-14 12:41:00")));
        assertEquals("about 29 and a half days ago", since(d("2013-08-03 00:05:00"), d("2013-09-01 12:58:00")));

        assertEquals("about 5 months later", since(d("2013-08-03 00:05:00"), d("2013-03-01 12:41:00")));
        assertEquals("about 4 months and 25 days later", since(d("2013-08-03 00:05:00"), d("2013-03-10 12:41:00")));
        assertEquals("about 2 and a half months later", since(d("2013-08-03 00:05:00"), d("2013-05-18 12:41:00")));

        assertEquals("1 year ago", since(d("2013-08-03 00:05:00"), d("2014-08-01 12:41:00")));
        assertEquals("about 1 year ago", since(d("2013-08-03 00:05:00"), d("2014-08-21 12:41:00")));
        assertEquals("about 1 year and 7 months ago", since(d("2013-08-03 00:05:00"), d("2015-03-01 12:41:00")));
        assertEquals("about 1 year and 10 months ago", since(d("2013-08-03 00:05:00"), d("2015-05-29 12:41:00")));
        assertEquals("about 5 years ago", since(d("2013-08-03 00:05:00"), d("2018-08-29 23:24:05")));
    }

    private static Date d(String date) throws ParseException { return parseDate(date, DATE_FORMAT); }
}
