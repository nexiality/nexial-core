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

package org.nexial.core

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ResourceBundleTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun text_simple() {
        val fixture = ResourceBundle("org/nexial/core/test_ResourceBundle")

        assertEquals("This is a test.", fixture.text("message1"))
        assertEquals("Do not be alarmed.", fixture.text("message2"))
        assertEquals("Now is the time for all good men to come to the aid of their country. Again, now is the time.",
                     fixture.text("message3", "time", "good men", "aid", "country"))
        assertEquals("This is a test. Do not be alarmed.", fixture.text("message4"))
    }

    @Test
    fun text_hierarchy() {
        val fixture = ResourceBundle("org/nexial/core/test_ResourceBundle")

        val companyRB = fixture.subset("Company")
        assertEquals("Acme Inc.", companyRB.text("name"))
        assertEquals("123 Brighton Lane, Gumdrop City", companyRB.text("address", "Brighton", "Gumdrop"))

        val departmentRB = companyRB.subset("Department")
        assertEquals("Accounting", departmentRB.text("name"))
        assertEquals("15 people", departmentRB.text("size", "people"))
        assertEquals("second", departmentRB.text("floor"))

        val commons = fixture.subset("Commons")
        assertEquals("Command type 'ws' is not available. Check with Nexial Support Group for details.",
                     commons.text("command.missing", "ws"))
    }

    @Test
    fun text_absoluteRef() {
        val fixture = ResourceBundle("org/nexial/core/test_ResourceBundle")

        val mailer = fixture.subset("Mailer")
        assertEquals("nexial mailer not enabled: missing required JNDI configurations. " +
                     "Please check https://nexiality.github.io/documentation/tipsandtricks/IntegratingNexialWithEmail.html " +
                     "for more details.",
                     mailer.text("notReady.jndi"))
        assertEquals("nexial mailer not enabled: missing required smtp/imap configurations. " +
                     "Please check https://nexiality.github.io/documentation/tipsandtricks/IntegratingNexialWithEmail.html " +
                     "for more details.",
                     mailer.text("notReady.smtp"))
    }
}