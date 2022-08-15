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
package org.nexial.core.plugins.tn5250

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class Tn5250CommandTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun compare_fully() {
        val subject = Tn5250Command()

        assertEquals("Title equals to 'Sign On'", subject.compare("Sign On", "Sign On\n\n", true, "Title").message)
        assertEquals("Title contains 'Sign On'",
                     subject.compare("CONTAIN:Sign On", "Sign On\n\n", true, "Title").message)
        assertEquals("Title contains 'Sign On'",
                     subject.compare("CONTAIN_ANY_CASE:Sign On", "Sign On\n\n", true, "Title").message)
        assertEquals("Title starts with 'Sign On'",
                     subject.compare("START:Sign On", "Sign On\n\n", true, "Title").message)
        assertEquals("Title starts with 'Sign On'",
                     subject.compare("START_ANY_CASE:Sign On", "Sign On\n\n", true, "Title").message)
        assertEquals("Title ends with 'Sign On'", subject.compare("END:Sign On", "Sign On\n\n", true, "Title").message)
        assertEquals("Title ends with 'Sign On'",
                     subject.compare("END_ANY_CASE:Sign On", "Sign On\n\n", true, "Title").message)
        assertEquals("Title matches the regular expression 'Sign On'",
                     subject.compare("REGEX:Sign On", "Sign On\n\n", true, "Title").message)
    }

    @Test
    fun compare_regex() {
        val subject = Tn5250Command()

        assertEquals("Field matches the regular expression '(Customer Name|customer name)'",
                     subject.compare("REGEX:(Customer Name|customer name)", "Customer Name", true, "Field").message)
        assertEquals("Field matches the regular expression '.+ Name'",
                     subject.compare("REGEX:.+ Name", "Customer Name", true, "Field").message)
        assertEquals("Field matches the regular expression '[C|c]ustomer [N|n]ame'",
                     subject.compare("REGEX:[C|c]ustomer [N|n]ame", "Customer Name", true, "Field").message)
    }


}