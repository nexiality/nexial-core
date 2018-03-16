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
public class DateTest {

    @Test
    public void testFormat() {
        Date d = new Date();
        Assert.assertEquals("04/10/2011 00:00:00", d.format("2011/10/04", "yyyy/dd/MM", "MM/dd/yyyy HH:mm:ss"));
        Assert.assertEquals("4/10/2011 00:00:00", d.format("2011/10/04", "yyyy/dd/MM", "M/dd/yyyy HH:mm:ss"));
        Assert.assertEquals("11/10/2011 00:00:00", d.format("2011/10/11", "yyyy/dd/MM", "M/dd/yyyy HH:mm:ss"));
        Assert.assertEquals("11/10/2011 22:14:19",
                            d.format("2011/10/11 14:22:19", "yyyy/dd/MM mm:HH:ss", "M/dd/yyyy HH:mm:ss"));
    }

    @Test
    public void testStdFormat() {
        Date d = new Date();
        Assert.assertEquals("04/01/2011 00:00:00", d.stdFormat("2011/1/4", "yyyy/dd/MM"));
        Assert.assertEquals("04/10/2011 00:00:00", d.stdFormat("2011/10/04", "yyyy/dd/MM"));
        Assert.assertEquals("12/11/2012 00:00:00", d.stdFormat("2012/11/12", "yyyy/dd/MM"));
        Assert.assertEquals("12/11/2012 15:12:34", d.stdFormat("2012/11/12 12:15:34", "yyyy/dd/MM mm:HH:ss"));
    }

    @Test
    public void testAddYear() {
        Date d = new Date();
        Assert.assertEquals("04/14/2011 11:15:22", d.addYear("04/14/2010 11:15:22", "1"));
        Assert.assertEquals("04/14/2009 11:15:22", d.addYear("04/14/2010 11:15:22", "-1"));
        Assert.assertEquals("04/14/2021 11:15:22", d.addYear("04/14/2010 11:15:22", "11"));
    }

    @Test
    public void testAddMonth() {
        Date d = new Date();
        Assert.assertEquals("05/14/2010 11:15:22", d.addMonth("04/14/2010 11:15:22", "1"));
        Assert.assertEquals("03/14/2010 11:15:22", d.addMonth("04/14/2010 11:15:22", "-1"));
        Assert.assertEquals("03/14/2011 11:15:22", d.addMonth("04/14/2010 11:15:22", "11"));
    }

    @Test
    public void testAddDay() {
        Date d = new Date();
        Assert.assertEquals("04/15/2010 11:15:22", d.addDay("04/14/2010 11:15:22", "1"));
        Assert.assertEquals("04/13/2010 11:15:22", d.addDay("04/14/2010 11:15:22", "-1"));
        Assert.assertEquals("04/25/2010 11:15:22", d.addDay("04/14/2010 11:15:22", "11"));
        Assert.assertEquals("06/13/2010 11:15:22", d.addDay("04/14/2010 11:15:22", "60"));
    }

    @Test
    public void testAddHour() {
        Date d = new Date();
        Assert.assertEquals("04/14/2010 12:15:22", d.addHour("04/14/2010 11:15:22", "1"));
        Assert.assertEquals("04/14/2010 10:15:22", d.addHour("04/14/2010 11:15:22", "-1"));
        Assert.assertEquals("04/14/2010 22:15:22", d.addHour("04/14/2010 11:15:22", "11"));
        Assert.assertEquals("04/16/2010 23:15:22", d.addHour("04/14/2010 11:15:22", "60"));
    }

    @Test
    public void testAddMinute() {
        Date d = new Date();
        Assert.assertEquals("04/14/2010 11:16:22", d.addMinute("04/14/2010 11:15:22", "1"));
        Assert.assertEquals("04/14/2010 11:14:22", d.addMinute("04/14/2010 11:15:22", "-1"));
        Assert.assertEquals("04/14/2010 11:26:22", d.addMinute("04/14/2010 11:15:22", "11"));
        Assert.assertEquals("04/14/2010 12:15:22", d.addMinute("04/14/2010 11:15:22", "60"));
        Assert.assertEquals("04/14/2010 12:00:22", d.addMinute("04/14/2010 11:15:22", "45"));
    }

    @Test
    public void testAddSecond() {
        Date d = new Date();
        Assert.assertEquals("04/14/2010 11:15:23", d.addSecond("04/14/2010 11:15:22", "1"));
        Assert.assertEquals("04/14/2010 11:15:21", d.addSecond("04/14/2010 11:15:22", "-1"));
        Assert.assertEquals("04/14/2010 11:15:33", d.addSecond("04/14/2010 11:15:22", "11"));
        Assert.assertEquals("04/14/2010 11:16:22", d.addSecond("04/14/2010 11:15:22", "60"));
        Assert.assertEquals("04/14/2010 11:16:07", d.addSecond("04/14/2010 11:15:22", "45"));
    }

    @Test
    public void testSetYear() {
        Date d = new Date();
        Assert.assertEquals("04/14/2013 11:15:22", d.setYear("04/14/2010 11:15:22", "2013"));
        Assert.assertEquals("04/14/2000 11:15:22", d.setYear("04/14/2010 11:15:22", "2000"));
        Assert.assertEquals("04/14/1972 11:15:22", d.setYear("04/14/2010 11:15:22", "1972"));
    }

    @Test
    public void testSetMonth() {
        Date d = new Date();
        Assert.assertEquals("10/14/2010 11:15:22", d.setMonth("04/14/2010 11:15:22", "10"));
        Assert.assertEquals("04/14/2010 11:15:22", d.setMonth("04/14/2010 11:15:22", "4"));
        Assert.assertEquals("09/14/2010 11:15:22", d.setMonth("04/14/2010 11:15:22", "9"));
    }

    @Test
    public void testSetDay() {
        Date d = new Date();
        Assert.assertEquals("04/10/2010 11:15:22", d.setDay("04/14/2010 11:15:22", "10"));
        Assert.assertEquals("04/24/2010 11:15:22", d.setDay("04/14/2010 11:15:22", "24"));
        Assert.assertEquals("04/09/2010 11:15:22", d.setDay("04/14/2010 11:15:22", "9"));
    }

    @Test
    public void testSetHour() {
        Date d = new Date();
        Assert.assertEquals("04/14/2010 10:15:22", d.setHour("04/14/2010 11:15:22", "10"));
        Assert.assertEquals("04/14/2010 14:15:22", d.setHour("04/14/2010 11:15:22", "14"));
        Assert.assertEquals("04/14/2010 00:15:22", d.setHour("04/14/2010 11:15:22", "0"));
    }

    @Test
    public void testSetMinute() {
        Date d = new Date();
        Assert.assertEquals("04/14/2010 11:10:22", d.setMinute("04/14/2010 11:15:22", "10"));
        Assert.assertEquals("04/14/2010 11:14:22", d.setMinute("04/14/2010 11:15:22", "14"));
        Assert.assertEquals("04/14/2010 11:00:22", d.setMinute("04/14/2010 11:15:22", "0"));
    }

    @Test
    public void testSetSecond() {
        Date d = new Date();
        Assert.assertEquals("04/14/2010 11:15:10", d.setSecond("04/14/2010 11:15:22", "10"));
        Assert.assertEquals("04/14/2010 11:16:04", d.setSecond("04/14/2010 11:15:22", "64"));
        Assert.assertEquals("04/14/2010 11:15:00", d.setSecond("04/14/2010 11:15:22", "0"));
    }
}
