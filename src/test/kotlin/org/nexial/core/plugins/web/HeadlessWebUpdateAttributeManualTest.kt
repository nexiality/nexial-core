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

import org.junit.Test
import org.nexial.core.ExcelBasedTests
import org.nexial.core.NexialConst.Web.BROWSER

class HeadlessWebUpdateAttributeManualTest : ExcelBasedTests() {

    @Test
    @Throws(Exception::class)
    fun updateAttribute() {
        System.setProperty(BROWSER, "chrome.headless")
        val executionSummary = testViaExcel("unitTest_web-updateAttributeCommand.xlsx")
        val testIterations = executionSummary.nestedExecutions[0].nestedExecutions
        for (summary in testIterations) {
            assertPassFail(summary, "UpdateAttribute", TestOutcomeStats(1, 18))
        }
    }
}