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
import org.junit.Before
import org.junit.Test
import org.springframework.context.support.ClassPathXmlApplicationContext
import java.text.SimpleDateFormat
import java.util.Date

// named as manual test since "sysdate" isn't active/defined by default
class SysdateManualTest {

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
    fun testNow() {
        println("sysdate.now = " + sysdate.now("yyyy-MM-dd HH:mm:ss"))
        assertEquals("2019", sysdate.now("yyyy"))
    }

    @Test
    fun testFirstDOW() {
        // manual update needed
        assertEquals("018", sysdate.firstDOW("ddd"))
    }

    @Test
    fun testLastDOW() {
        // manual update needed
        assertEquals("24", sysdate.lastDOW("dd"))
    }

    @Test
    fun testLastQtr() {
        assertEquals("2019-2", sysdate.lastQtr("yyyy-q"))
        assertEquals("2019-2", sysdate.lastQtr("yyyy-qqq"))
        assertEquals("2", sysdate.lastQtr("qqq"))
    }

    @Test
    fun testCurrentQtr() {
        assertEquals("2019-3", sysdate.currentQtr("yyyy-q"))
        assertEquals("2019-3", sysdate.currentQtr("yyyy-qqq"))
        assertEquals("3", sysdate.currentQtr("qqq"))
    }

    @Test
    fun testNextQtr() {
        assertEquals("2019-4", sysdate.nextQtr("yyyy-q"))
        assertEquals("2019-4", sysdate.nextQtr("yyyy-qqq"))
        assertEquals("4", sysdate.nextQtr("qqq"))
    }
}
