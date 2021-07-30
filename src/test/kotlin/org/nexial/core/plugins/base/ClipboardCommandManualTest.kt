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

package org.nexial.core.plugins.base

import org.apache.commons.lang3.StringUtils
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.nexial.core.model.MockExecutionContext

class ClipboardCommandManualTest {
    private val context = MockExecutionContext(true)

    @After
    fun tearDown() {
        context.cleanProject()
    }

    @Test
    fun clipboardCopyTest() {
        val subject = BaseCommand()
        subject.init(context)

        //verify clipboard copy paste
        val clipboardData = "assign this to clipboard"
        subject.copyIntoClipboard(clipboardData)
        subject.copyFromClipboard("c-data")
        val cData = context.getStringData("c-data")
        Assert.assertEquals(clipboardData, cData)
    }

    @Test
    fun clipboardClearTest() {
        val subject = BaseCommand()
        subject.init(context)

        subject.clearClipboard()
        subject.copyFromClipboard("var1")
        val var1 = context.getStringData("var1")
        Assert.assertTrue(StringUtils.isEmpty(var1))
    }
}