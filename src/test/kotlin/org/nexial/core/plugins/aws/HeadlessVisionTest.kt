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

package org.nexial.core.plugins.aws

import org.junit.Assert
import org.junit.Test
import org.nexial.core.ExcelBasedTests

class HeadlessVisionTest : ExcelBasedTests() {

    private val SYSPROP_ACCESS_KEY = "HeadlessVisionTest.ACCESS_KEY"
    private val SYSPROP_SECRET_KEY = "HeadlessVisionTest.SECRET_KEY"

    @Test
    @Throws(Exception::class)
    fun allTests() {
        // expects some system variable to be predefined... otherwise we won't proceed
        if (!System.getProperties().containsKey(SYSPROP_ACCESS_KEY)) {
            println("REQUIRED SYSTEM PROPERTIES '$SYSPROP_ACCESS_KEY' NOT FOUND; TEST SKIPPED")
            return
        }

        if (!System.getProperties().containsKey(SYSPROP_SECRET_KEY)) {
            println("REQUIRED SYSTEM PROPERTIES '$SYSPROP_SECRET_KEY' NOT FOUND; TEST SKIPPED")
            return
        }

        // both system variables are referenced directly in data sheet
        val executionSummary = testViaExcel("unitTest_vision.xlsx")
        Assert.assertEquals(0, executionSummary.failCount.toLong())
    }
}
