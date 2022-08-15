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

@file:Suppress("invisible_reference", "invisible_member")
package org.nexial.core.plugins.web

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nexial.core.NexialConst.Web
import org.nexial.core.NexialConst.Web.RGBA_TRANSPARENT
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.MockExecutionContext
import org.openqa.selenium.support.Color
import javax.validation.constraints.NotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CssHelperTest {
    private val mockContext = MockExecutionContext(true)
    private val css = CssHelper(object : WebCommand() {
        override fun getContext() = mockContext
        override fun init(@NotNull context: ExecutionContext?) {
            this.context = mockContext
        }
    })

    @Before
    @Throws(Exception::class)
    fun setUp() {
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
    }

    @Test
    fun testIsRGBA() {
        listOf(RGBA_TRANSPARENT, "rgba(0,0,0,0)", "rgba(255,231,0,1)", "rgba(1, 1, 1, 1)")
            .forEach { assertTrue(css.isRGBA(it), "assert that '$it' is an RGBA color value") }
        listOf("rgba(300, 3000, 30000, 300000)")
            .forEach { assertFalse(css.isRGBA(it), "assert that '$it' is NOT an RGBA color value") }
    }

    @Test
    fun testIsHexColor() {
        listOf("#000000", "#111111", "#000", "#111", "#fff", "#ffffff", "#123456", "#abcdef", "#7a8b9c")
            .forEach { assertTrue(css.isHexColor(it), "assert that '$it' is a HEX color value") }
        listOf("#00", "#1211", "#0*0", "#13141", "#123 456", "1px solid #abcdef")
            .forEach { assertFalse(css.isHexColor(it), "assert that '$it' is NOT a HEX color value") }
    }

    @Test
    fun hasHexColor() {
        listOf("#000000", "#000", "#fff", "1px solid #123456", "#abcdef no-repeat")
            .forEach { assertTrue(css.hasHexColor(it), "assert that '$it' contains a HEX color value") }
        listOf("#00", "#000a", "#f234b", "1px solid #123456a", "#abcdef(no-repeat)")
            .forEach { assertFalse(css.hasHexColor(it), "assert that '$it' DOES NOT contain a HEX color value") }
    }

    @Test
    fun convertToRGBA() {
        assertEquals(RGBA_TRANSPARENT, css.convertToRGBA(Web.RGBA_TRANSPARENT2))
        assertEquals(RGBA_TRANSPARENT, css.convertToRGBA("transparent"))
        assertEquals("rgba(0, 0, 0, 1)", css.convertToRGBA("#000000"))
        assertEquals("rgba(255, 255, 255, 1)", css.convertToRGBA("#fff"))
        assertEquals("rgba(240, 145, 183, 1)", css.convertToRGBA("#f091b7"))
        assertEquals("rgba(136, 204, 170, 1)", css.convertToRGBA("#8ca"))
        assertEquals("rgba(140, 161, 196, 1)", css.convertToRGBA("#8ca1c4"))
    }

    @Test
    fun assertSameColor() {
        assertTrue(css.assertSameColor(Color(12, 34, 56, 1.0), Color(12, 34, 56, 1.toDouble())))
        assertTrue(css.assertSameColor(Color(112, 134, 156, 0.95), Color(112, 134, 156, 0.9500)))
        assertTrue(css.assertSameColor(Color(0, 0, 0, 0.0), Color(112, 134, 156, 0.0)))
    }

    @Test
    fun extractColor() {
        assertNull(css.extractColor("#xyz"))
        assertNull(css.extractColor("#1134"))
        assertTrue(css.assertSameColor(css.extractColor("#123456")!!, Color(18, 52, 86, 1.0)))
        assertTrue(css.assertSameColor(css.extractColor("#fedcba")!!, Color(254, 220, 186, 1.0)))
        assertTrue(css.assertSameColor(css.extractColor("rgba(145,242,16,1)")!!, Color(145, 242, 16, 1.0)))
        assertTrue(css.assertSameColor(css.extractColor("rgba( 0,\t45 , 019 ,0.61 )")!!, Color(0, 45, 19, 0.61)))
    }
}