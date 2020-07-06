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

package org.nexial.core.variable

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.nexial.core.NexialConst.*
import java.text.SimpleDateFormat

class DateTest {

    @Test
    fun testFormat() {
        val d = Date()
        assertEquals("04/10/2011 00:00:00", d.format("2011/10/04", "yyyy/dd/MM", "MM/dd/yyyy HH:mm:ss"))
        assertEquals("4/10/2011 00:00:00", d.format("2011/10/04", "yyyy/dd/MM", "M/dd/yyyy HH:mm:ss"))
        assertEquals("11/10/2011 00:00:00", d.format("2011/10/11", "yyyy/dd/MM", "M/dd/yyyy HH:mm:ss"))
        assertEquals("11/10/2011 22:14:19",
                     d.format("2011/10/11 14:22:19", "yyyy/dd/MM mm:HH:ss", "M/dd/yyyy HH:mm:ss"))
    }

    private val logTimeFormat = "HH:mm:ss.SSS"
    private val logDateFormat = "yyyy/MM/dd $logTimeFormat"

    @Test
    fun testFormat2() {
        println("System.currentTimeMillis() = " + System.currentTimeMillis())

        val d = Date()
        assertEquals("0000/00/00 00:01:12.000", d.format("72000", EPOCH, logDateFormat))
        assertEquals("2018/05/24 18:46:36.917", d.format("1527212796917", EPOCH, logDateFormat))
        assertEquals("00:00:07.461", d.format("7461", EPOCH, logTimeFormat))
        assertEquals("00:00:07", d.format("7461", EPOCH, "HH:mm:ss"))
        assertEquals("0:0:7", d.format("7461", EPOCH, "h:m:s"))
        assertEquals("0", d.format("7461", EPOCH, "y"))
        assertEquals("720:0:0", d.format(THIRTYDAYS.toString() + "", EPOCH, "h:m:s"))
        assertEquals("30 0:0:0", d.format(THIRTYDAYS.toString() + "", EPOCH, "dd h:m:s"))
        assertEquals("00/30 0:0:0", d.format(THIRTYDAYS.toString() + "", EPOCH, "MM/dd h:m:s"))
        assertEquals("12/31 4:0:0", d.format(ONEYEAR.toString() + "", EPOCH, "MM/dd h:m:s"))
        assertEquals("1970/12/31 16:0:0", d.format(ONEYEAR.toString() + "", EPOCH, "yyyy/MM/dd H:m:s"))
    }

    @Test
    fun testStdFormat() {
        val d = Date()
        assertEquals("04/01/2011 00:00:00", d.stdFormat("2011/1/4", "yyyy/dd/MM"))
        assertEquals("04/10/2011 00:00:00", d.stdFormat("2011/10/04", "yyyy/dd/MM"))
        assertEquals("12/11/2012 00:00:00", d.stdFormat("2012/11/12", "yyyy/dd/MM"))
        assertEquals("12/11/2012 15:12:34", d.stdFormat("2012/11/12 12:15:34", "yyyy/dd/MM mm:HH:ss"))
    }

    @Test
    fun testAddYear() {
        val d = Date()
        assertEquals("04/14/2011 11:15:22", d.addYear("04/14/2010 11:15:22", "1"))
        assertEquals("04/14/2009 11:15:22", d.addYear("04/14/2010 11:15:22", "-1"))
        assertEquals("04/14/2021 11:15:22", d.addYear("04/14/2010 11:15:22", "11"))
    }

    @Test
    fun testAddMonth() {
        val d = Date()
        assertEquals("05/14/2010 11:15:22", d.addMonth("04/14/2010 11:15:22", "1"))
        assertEquals("03/14/2010 11:15:22", d.addMonth("04/14/2010 11:15:22", "-1"))
        assertEquals("03/14/2011 11:15:22", d.addMonth("04/14/2010 11:15:22", "11"))
    }

    @Test
    fun testAddDay() {
        val d = Date()
        assertEquals("04/15/2010 11:15:22", d.addDay("04/14/2010 11:15:22", "1"))
        assertEquals("04/13/2010 11:15:22", d.addDay("04/14/2010 11:15:22", "-1"))
        assertEquals("04/25/2010 11:15:22", d.addDay("04/14/2010 11:15:22", "11"))
        assertEquals("06/13/2010 11:15:22", d.addDay("04/14/2010 11:15:22", "60"))
    }

    @Test
    fun testAddHour() {
        val d = Date()
        assertEquals("04/14/2010 12:15:22", d.addHour("04/14/2010 11:15:22", "1"))
        assertEquals("04/14/2010 10:15:22", d.addHour("04/14/2010 11:15:22", "-1"))
        assertEquals("04/14/2010 22:15:22", d.addHour("04/14/2010 11:15:22", "11"))
        assertEquals("04/16/2010 23:15:22", d.addHour("04/14/2010 11:15:22", "60"))
    }

    @Test
    fun testAddMinute() {
        val d = Date()
        assertEquals("04/14/2010 11:16:22", d.addMinute("04/14/2010 11:15:22", "1"))
        assertEquals("04/14/2010 11:14:22", d.addMinute("04/14/2010 11:15:22", "-1"))
        assertEquals("04/14/2010 11:26:22", d.addMinute("04/14/2010 11:15:22", "11"))
        assertEquals("04/14/2010 12:15:22", d.addMinute("04/14/2010 11:15:22", "60"))
        assertEquals("04/14/2010 12:00:22", d.addMinute("04/14/2010 11:15:22", "45"))
    }

    @Test
    fun testAddSecond() {
        val d = Date()
        assertEquals("04/14/2010 11:15:23", d.addSecond("04/14/2010 11:15:22", "1"))
        assertEquals("04/14/2010 11:15:21", d.addSecond("04/14/2010 11:15:22", "-1"))
        assertEquals("04/14/2010 11:15:33", d.addSecond("04/14/2010 11:15:22", "11"))
        assertEquals("04/14/2010 11:16:22", d.addSecond("04/14/2010 11:15:22", "60"))
        assertEquals("04/14/2010 11:16:07", d.addSecond("04/14/2010 11:15:22", "45"))
    }

    @Test
    fun testSetYear() {
        val d = Date()
        assertEquals("04/14/2013 11:15:22", d.setYear("04/14/2010 11:15:22", "2013"))
        assertEquals("04/14/2000 11:15:22", d.setYear("04/14/2010 11:15:22", "2000"))
        assertEquals("04/14/1972 11:15:22", d.setYear("04/14/2010 11:15:22", "1972"))
        assertEquals("04/14/1970 11:15:22", d.setYear("04/14/2010 11:15:22", "1970"))

        try {
            assertEquals("04/14/1972 11:15:22", d.setYear("04/14/2010 11:15:22", "1960"))
            fail("Expected exception not thrown")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun testSetMonth() {
        val d = Date()
        assertEquals("10/14/2010 11:15:22", d.setMonth("04/14/2010 11:15:22", "10"))
        assertEquals("04/14/2010 11:15:22", d.setMonth("04/14/2010 11:15:22", "4"))
        assertEquals("09/14/2010 11:15:22", d.setMonth("04/14/2010 11:15:22", "9"))
    }

    @Test
    fun testSetDay() {
        val d = Date()
        assertEquals("04/10/2010 11:15:22", d.setDay("04/14/2010 11:15:22", "10"))
        assertEquals("04/24/2010 11:15:22", d.setDay("04/14/2010 11:15:22", "24"))
        assertEquals("04/09/2010 11:15:22", d.setDay("04/14/2010 11:15:22", "9"))
    }

    @Test
    fun testSetHour() {
        val d = Date()
        assertEquals("04/14/2010 10:15:22", d.setHour("04/14/2010 11:15:22", "10"))
        assertEquals("04/14/2010 14:15:22", d.setHour("04/14/2010 11:15:22", "14"))
        assertEquals("04/14/2010 00:15:22", d.setHour("04/14/2010 11:15:22", "0"))
    }

    @Test
    fun testSetMinute() {
        val d = Date()
        assertEquals("04/14/2010 11:10:22", d.setMinute("04/14/2010 11:15:22", "10"))
        assertEquals("04/14/2010 11:14:22", d.setMinute("04/14/2010 11:15:22", "14"))
        assertEquals("04/14/2010 11:00:22", d.setMinute("04/14/2010 11:15:22", "0"))
    }

    @Test
    fun setSecond() {
        val d = Date()
        assertEquals("04/14/2010 11:15:10", d.setSecond("04/14/2010 11:15:22", "10"))
        assertEquals("04/14/2010 11:16:04", d.setSecond("04/14/2010 11:15:22", "64"))
        assertEquals("04/14/2010 11:15:00", d.setSecond("04/14/2010 11:15:22", "0"))
        assertEquals("04/14/2010 11:14:15", d.setSecond("04/14/2010 11:15:22", "-45"))
    }

    @Test
    fun setDOW() {
        val d = Date()
        assertEquals("09/22/2013", d.setDOW("09/22/2013", "0"))
        assertEquals("09/23/2013", d.setDOW("09/22/2013", "1"))
        assertEquals("09/24/2013", d.setDOW("09/22/2013", "2"))
        assertEquals("09/25/2013", d.setDOW("09/22/2013", "3"))
        assertEquals("09/26/2013", d.setDOW("09/22/2013", "4"))
        assertEquals("09/27/2013", d.setDOW("09/22/2013", "5"))
        assertEquals("09/28/2013", d.setDOW("09/22/2013", "6"))
        assertEquals("09/22/2013", d.setDOW("09/22/2013", "7"))

        try {
            d.setDOW("09/22/2013", "8")
            fail("Expected exception not thrown")
        } catch (e: Exception) {
            // expected
        }

        try {
            d.setDOW("09/22/2013", "-1")
            fail("Expected exception not thrown")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    @Throws(Exception::class)
    fun fromBase10Time() {
        val dateFormat = SimpleDateFormat("HH:mm:ss.S")
        assertEquals("00:00:00.0", dateFormat.format(Date.fromBase10Time("0")))
        assertEquals("00:00:00.0", dateFormat.format(Date.fromBase10Time("0.00")))
        assertEquals("00:30:00.0", dateFormat.format(Date.fromBase10Time("0.50")))
        assertEquals("01:00:00.0", dateFormat.format(Date.fromBase10Time("1")))
        assertEquals("02:27:00.0", dateFormat.format(Date.fromBase10Time("2.45")))
        assertEquals("14:27:00.0", dateFormat.format(Date.fromBase10Time("14.45")))
        assertEquals("14:19:45.48", dateFormat.format(Date.fromBase10Time("14.32918")))
        assertEquals("23:59:59.964", dateFormat.format(Date.fromBase10Time("23.99999")))
        // lost in fidelity... need a different date format to see the overflown time information
        assertEquals("00:00:00.360", dateFormat.format(Date.fromBase10Time("24.0001")))
        assertEquals("01:06:03.640", dateFormat.format(Date.fromBase10Time("25.101011")))
        assertEquals("01:06:03.639", dateFormat.format(Date.fromBase10Time("25.1010108333")))

        val dateFormat2 = SimpleDateFormat("dd HH:mm:ss.S")
        assertEquals("01 00:00:00.360", dateFormat2.format(Date.fromBase10Time("24.0001")))
        assertEquals("01 01:06:03.640", dateFormat2.format(Date.fromBase10Time("25.101011")))
        assertEquals("01 01:06:03.639", dateFormat2.format(Date.fromBase10Time("25.1010108333")))
    }

    @Test
    @Throws(Exception::class)
    fun toBase10Time() {
        val dateFormat = SimpleDateFormat("HH:mm:ss.S")
        assertEquals(0.0, Date.toBase10Time(dateFormat.parse("00:00:00.0")), 0.0)
        assertEquals(0.00, Date.toBase10Time(dateFormat.parse("00:00:00.0")), 0.0)
        assertEquals(0.50, Date.toBase10Time(dateFormat.parse("00:30:00.0")), 0.0)
        assertEquals(1.0, Date.toBase10Time(dateFormat.parse("01:00:00.0")), 0.0)
        assertEquals(2.45, Date.toBase10Time(dateFormat.parse("02:27:00.0")), 0.0)
        assertEquals(14.45, Date.toBase10Time(dateFormat.parse("14:27:00.0")), 0.0)
        assertEquals(14.32918, Date.toBase10Time(dateFormat.parse("14:19:45.48")), 0.0)
        assertEquals(23.99999, Date.toBase10Time(dateFormat.parse("23:59:59.964")), 0.0)
        assertEquals(24.0001, Date.toBase10Time(dateFormat.parse("24:00:00.360")), 0.0)
        assertEquals(25.1010108333, Date.toBase10Time(dateFormat.parse("25:06:03.639")), 0.0)
        assertEquals(10.425, Date.toBase10Time(dateFormat.parse("10:25:30.0")), 0.0)
    }
}
