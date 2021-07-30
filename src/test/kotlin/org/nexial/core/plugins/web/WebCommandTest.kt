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

package org.nexial.core.plugins.web

import io.jsonwebtoken.lang.Assert.isTrue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.nexial.core.NexialConst.Web.RGBA_TRANSPARENT
import org.nexial.core.NexialConst.Web.RGBA_TRANSPARENT2

class WebCommandTest {
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
        isTrue(WebCommand.isRGBA(RGBA_TRANSPARENT))
        isTrue(WebCommand.isRGBA("rgba(0,0,0,0)"))
        isTrue(WebCommand.isRGBA("rgba(255,231,0,1)"))
        isTrue(WebCommand.isRGBA("rgba(1, 1, 1, 1)"))
        // unlikely rgba
        isTrue(WebCommand.isRGBA("rgba(300, 3000, 30000, 300000)"))
    }

    @Test
    fun testIsHexColor() {
        isTrue(WebCommand.isHexColor("#000000"))
        isTrue(WebCommand.isHexColor("#111111"))
        isTrue(WebCommand.isHexColor("#000"))
        isTrue(WebCommand.isHexColor("#111"))
        isTrue(WebCommand.isHexColor("#fff"))
        isTrue(WebCommand.isHexColor("#ffffff"))
        isTrue(WebCommand.isHexColor("#123456"))
        isTrue(WebCommand.isHexColor("#abcdef"))
        isTrue(WebCommand.isHexColor("#7a8b9c"))
    }

    @Test
    fun convertToRGBA() {
        assertEquals(RGBA_TRANSPARENT, WebCommand.convertToRGBA(RGBA_TRANSPARENT2))
        assertEquals(RGBA_TRANSPARENT, WebCommand.convertToRGBA("transparent"))
        assertEquals("rgba(0, 0, 0, 1)", WebCommand.convertToRGBA("#000000"))
        assertEquals("rgba(255, 255, 255, 1)", WebCommand.convertToRGBA("#fff"))
        assertEquals("rgba(240, 145, 183, 1)", WebCommand.convertToRGBA("#f091b7"))
        assertEquals("rgba(136, 204, 170, 1)", WebCommand.convertToRGBA("#8ca"))
        assertEquals("rgba(140, 161, 196, 1)", WebCommand.convertToRGBA("#8ca1c4"))
    }
}