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

package org.nexial.core.model

import org.apache.commons.lang3.StringUtils
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.nexial.core.NexialConst.Data.OPT_NOTIFY_AS_HTML

class EmailNotificationTest {
    val context: MockExecutionContext = MockExecutionContext()

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun includeExecMetadata_Plain() {
        context.setData(OPT_NOTIFY_AS_HTML, false)

        val subject = EmailNotification(context,
                                        ExecutionEvent.ExecutionComplete,
                                        "email:my_email@my_company.com|Execution complete")
        subject.includeExecMeta(false)

        val fixture = subject.handleExecMetadata("Execution Complete")
        println("fixture = $fixture")

        Assert.assertTrue(StringUtils.startsWith(fixture, "Execution Complete\nFrom "))
        Assert.assertFalse(StringUtils.contains(fixture, "<html>"))
    }

    @Test
    fun includeExecMetadata_HTML() {
        context.setData(OPT_NOTIFY_AS_HTML, true)

        val subject = EmailNotification(context,
                                        ExecutionEvent.ExecutionComplete,
                                        "email:my_email@my_company.com|Execution complete")
        subject.includeExecMeta(false)

        val fixture = subject.handleExecMetadata("Execution Complete")
        println("fixture = $fixture")

        Assert.assertTrue(StringUtils.startsWith(fixture,
                                                 "<html><body>Execution Complete<br/><div style=\"font-weight:10pt\">From "))
        Assert.assertTrue(StringUtils.endsWith(fixture, "</div><br/></body></html>"))
    }
}