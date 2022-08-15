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
package org.nexial.core.plugins.desktop

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class DesktopUtilsTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun joinShortcuts() {
        assertEquals("", DesktopUtils.joinShortcuts(""))
        assertEquals("<[{a}]>", DesktopUtils.joinShortcuts("a"))
        assertEquals("<[{abc d}]>", DesktopUtils.joinShortcuts("abc d"))
        assertEquals("<[{abc d}]>", DesktopUtils.joinShortcuts("abc", " ", "d"))
        assertEquals("<[{abc }]><[ENTER]><[{d}]>", DesktopUtils.joinShortcuts("abc", " ", "[ENTER]", "d"))
        assertEquals("<[{abc }]><[ENTER]><[{d}]><[TAB]><[ESCAPE]>",
                     DesktopUtils.joinShortcuts("abc", " ", "[ENTER]", "d", "[TAB][ESCAPE]"))
        assertEquals("<[{abc }]><[ENTER]><[{d}]><[TAB]><[ESCAPE]><[{.}]>",
                     DesktopUtils.joinShortcuts("abc", " ", "[ENTER]", "d", "[TAB][ESCAPE]", "."))
        assertEquals("<[ENTER]>", DesktopUtils.joinShortcuts("[ENTER]"))
        assertEquals("<[ENTER]><[{a}]><[TAB]><[{ }]><[TAB]><[ENTER]><[{ppa}]>",
                     DesktopUtils.joinShortcuts("[ENTER]a[TAB] [TAB][ENTER]ppa"))

        // test newline characters
        assertEquals("<[{This is}]><[ENTER]>" +
                     "<[{a Test}]><[ENTER]>" +
                     "<[{A test, I said!!}]><[ENTER]>" +
                     "<[ENTER]>" +
                     "<[{Bye}]>",
                     DesktopUtils.joinShortcuts("This is\na Test\nA test, I said!!\n\nBye"))
        assertEquals("<[{When I say}]><[ENTER]>" +
                     "<[{ENTER}]><[ENTER]>" +
                     "<[{You press it, like this:}]><[ENTER]>" +
                     "<[ENTER]>",
                     DesktopUtils.joinShortcuts("When I say\nENTER\nYou press it, like this:\n\n"))
    }
}