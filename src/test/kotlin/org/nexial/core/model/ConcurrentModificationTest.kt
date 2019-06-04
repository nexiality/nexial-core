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

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.nexial.core.interactive.InteractiveSession

class ConcurrentModificationTest {

    val context: MockExecutionContext = MockExecutionContext()

    @After
    fun tearDown() = context.cleanProject()

    @Before
    fun setUp() {
        context.referenceDataForExecution.add("nexial.scriptRef.app")
        context.setData("nexial.scriptRef.app", "nexial unit test", true)
    }

    @Test
    @Throws(Exception::class)
    fun initInteractiveSession() {
        System.setProperty("nexial.scriptRef.app", "nexial tester")
        val session = InteractiveSession(context)
        // no exception here means that we don't have ConcurrentModification issue
        Assert.assertNotNull(session)

        context.removeData("nexial.scriptRef.app")
        Assert.assertNull(System.getProperty("nexial.scriptRef.app"))
    }

    companion object {
        init {
            System.setProperty("nexial.scriptRef.app", "nexial-test")
        }
    }
}
