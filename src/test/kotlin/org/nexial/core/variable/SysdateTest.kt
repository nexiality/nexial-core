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

import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.commons.lang3.time.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.springframework.context.support.ClassPathXmlApplicationContext
import java.text.SimpleDateFormat
import java.util.Date

class SysdateTest {
    private var sysdate = Sysdate()

    @Before
    fun init() {
        // EPTestUtils.setupCommonProps();
        System.setProperty("selenium.host", "localhost")
        System.setProperty("testsuite.startTs", SimpleDateFormat("yyyyMMddHHmmssS").format(Date()))

        val springContext = ClassPathXmlApplicationContext("classpath:/nexial.xml")
        sysdate = springContext.getBean("sysdate", Sysdate::class.java)
    }

    @Test
    fun testYesterday() {
        assertEquals(DateFormatUtils.format(DateUtils.addDays(Date(), -1), "yyyy-MM-dd"),
                     sysdate.yesterday("yyyy-MM-dd"))
    }

    @Test
    fun testTomorrow() {
        assertEquals(DateFormatUtils.format(DateUtils.addDays(Date(), 1), "yyyy-MM-dd"), sysdate.tomorrow("yyyy-MM-dd"))
    }

    @Test
    fun testFirstDOM() {
        assertEquals(DateFormatUtils.format(DateUtils.setDays(Date(), 1), "dd"), sysdate.firstDOM("dd"))
    }

    @Test
    fun testLastDOM() {
        assertEquals(
                DateFormatUtils.format(
                        // last day of this month
                        DateUtils.addDays(
                                // first day of next month
                                DateUtils.addMonths(
                                        // first day of this month
                                        DateUtils.setDays(Date(), 1),
                                        1),
                                -1),
                        "dd"),
                sysdate.lastDOM("dd"))
    }

    @Test
    fun testLastQtr2() {
        val sysdate2 = object : Sysdate() {
            public override fun init() {
                currentYear = 2010
                currentQtr = 1
                derivePrevNextQtr()
            }
        }
        sysdate2.init()

        assertEquals("2009-4", sysdate2.lastQtr("yyyy-q"))
        assertEquals("2009-4", sysdate2.lastQtr("yyyy-qqq"))
        assertEquals("4", sysdate2.lastQtr("qqq"))
    }

    @Test
    fun testNextQtr2() {
        val sysdate2 = object : Sysdate() {
            public override fun init() {
                currentYear = 2009
                currentQtr = 4
                derivePrevNextQtr()
            }
        }
        sysdate2.init()

        assertEquals("2010-1", sysdate2.nextQtr("yyyy-q"))
        assertEquals("2010-1", sysdate2.nextQtr("yyyy-qqq"))
        assertEquals("1", sysdate2.nextQtr("qqq"))
    }
}