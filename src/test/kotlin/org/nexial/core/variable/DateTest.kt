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

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.junit.Assert;
import org.junit.Test;

import static org.nexial.core.NexialConst.EPOCH;
import static org.nexial.core.NexialConst.ONEYEAR;
import static org.nexial.core.NexialConst.THIRTYDAYS;

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
    public void testFormat2() {
        System.out.println("System.currentTimeMillis() = " + System.currentTimeMillis());

        Date d = new Date();
        Assert.assertEquals("0000/00/00 00:01:12,000", d.format("72000", EPOCH, "yyyy/MM/dd HH:mm:ss,SSS"));
        Assert.assertEquals("2018/05/24 18:46:36,917", d.format("1527212796917", EPOCH, "yyyy/MM/dd HH:mm:ss,SSS"));
        Assert.assertEquals("00:00:07,461", d.format("7461", EPOCH, "HH:mm:ss,SSS"));
        Assert.assertEquals("00:00:07", d.format("7461", EPOCH, "HH:mm:ss"));
        Assert.assertEquals("0:0:7", d.format("7461", EPOCH, "h:m:s"));
        Assert.assertEquals("0", d.format("7461", EPOCH, "y"));
        Assert.assertEquals("720:0:0", d.format(THIRTYDAYS + "", EPOCH, "h:m:s"));
        Assert.assertEquals("30 0:0:0", d.format(THIRTYDAYS + "", EPOCH, "dd h:m:s"));
        Assert.assertEquals("00/30 0:0:0", d.format(THIRTYDAYS + "", EPOCH, "MM/dd h:m:s"));
        Assert.assertEquals("12/31 4:0:0", d.format(ONEYEAR + "", EPOCH, "MM/dd h:m:s"));
        Assert.assertEquals("1970/12/31 16:0:0", d.format(ONEYEAR + "", EPOCH, "yyyy/MM/dd H:m:s"));
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

    @Test
    public void fromBase10Time() throws Exception {
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.S");
        Assert.assertEquals("00:00:00.0", dateFormat.format(Date.fromBase10Time("0")));
        Assert.assertEquals("00:00:00.0", dateFormat.format(Date.fromBase10Time("0.00")));
        Assert.assertEquals("00:30:00.0", dateFormat.format(Date.fromBase10Time("0.50")));
        Assert.assertEquals("01:00:00.0", dateFormat.format(Date.fromBase10Time("1")));
        Assert.assertEquals("02:27:00.0", dateFormat.format(Date.fromBase10Time("2.45")));
        Assert.assertEquals("14:27:00.0", dateFormat.format(Date.fromBase10Time("14.45")));
        Assert.assertEquals("14:19:45.48", dateFormat.format(Date.fromBase10Time("14.32918")));
        Assert.assertEquals("23:59:59.964", dateFormat.format(Date.fromBase10Time("23.99999")));
        // lost in fidelity... need a different date format to see the overflown time information
        Assert.assertEquals("00:00:00.360", dateFormat.format(Date.fromBase10Time("24.0001")));
        Assert.assertEquals("01:06:03.640", dateFormat.format(Date.fromBase10Time("25.101011")));
        Assert.assertEquals("01:06:03.639", dateFormat.format(Date.fromBase10Time("25.1010108333")));

        DateFormat dateFormat2 = new SimpleDateFormat("dd HH:mm:ss.S");
        Assert.assertEquals("01 00:00:00.360", dateFormat2.format(Date.fromBase10Time("24.0001")));
        Assert.assertEquals("01 01:06:03.640", dateFormat2.format(Date.fromBase10Time("25.101011")));
        Assert.assertEquals("01 01:06:03.639", dateFormat2.format(Date.fromBase10Time("25.1010108333")));
    }

    @Test
    public void toBase10Time() throws Exception {
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.S");
        Assert.assertEquals(0, Date.toBase10Time(dateFormat.parse("00:00:00.0")), 0);
        Assert.assertEquals(0.00, Date.toBase10Time(dateFormat.parse("00:00:00.0")), 0);
        Assert.assertEquals(0.50, Date.toBase10Time(dateFormat.parse("00:30:00.0")), 0);
        Assert.assertEquals(1, Date.toBase10Time(dateFormat.parse("01:00:00.0")), 0);
        Assert.assertEquals(2.45, Date.toBase10Time(dateFormat.parse("02:27:00.0")), 0);
        Assert.assertEquals(14.45, Date.toBase10Time(dateFormat.parse("14:27:00.0")), 0);
        Assert.assertEquals(14.32918, Date.toBase10Time(dateFormat.parse("14:19:45.48")), 0);
        Assert.assertEquals(23.99999, Date.toBase10Time(dateFormat.parse("23:59:59.964")), 0);
        Assert.assertEquals(24.0001, Date.toBase10Time(dateFormat.parse("24:00:00.360")), 0);
        Assert.assertEquals(25.1010108333, Date.toBase10Time(dateFormat.parse("25:06:03.639")), 0);
        Assert.assertEquals(10.425, Date.toBase10Time(dateFormat.parse("10:25:30.0")), 0);
    }
}
