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

package org.nexial.core.plugins.web

import org.junit.Test
import org.nexial.core.ExcelBasedTests
import org.nexial.core.NexialConst.Web.BROWSER

class WebManualTest : ExcelBasedTests() {

    @Test
    @Throws(Exception::class)
    fun webTests() {
        System.setProperty(BROWSER, "chrome.headless")

        val executionSummary = testViaExcel("unitTest_web.xlsx",
                                            "AssertElement",
                                            "WindowDialog",
                                            "AlertWindowPopUp",
                                            "LocalStorage",
                                            "Scroll",
                                            "SelectMultiOption",
                                            "SaveTable",
                                            "SaveTable2")

        val testIterations = executionSummary.nestedExecutions[0].nestedExecutions
        for (summary in testIterations) {
            assertPassFail(summary, "AssertElement", TestOutcomeStats.allPassed())
            assertPassFail(summary, "WindowDialog", TestOutcomeStats.allPassed())
            assertPassFail(summary, "AlertWindowPopUp", TestOutcomeStats.allPassed())
            assertPassFail(summary, "LocalStorage", TestOutcomeStats.allPassed())
            assertPassFail(summary, "Scroll", TestOutcomeStats.allPassed())
            assertPassFail(summary, "SelectMultiOption", TestOutcomeStats.allPassed())
            assertPassFail(summary, "SaveTable", TestOutcomeStats.allPassed())
            assertPassFail(summary, "SaveTable2", TestOutcomeStats.allPassed())

            // assertPassFail(summary, "AssertingAttributes", TestOutcomeStats.allPassed());
            // assertPassFail(summary, "SaveText", TestOutcomeStats.allPassed());
            // assertPassFail(summary, "Frame", TestOutcomeStats.allPassed());
            // assertPassFail(summary, "CheckBox", TestOutcomeStats.allPassed());
            // assertPassFail(summary, "SavePartialText", TestOutcomeStats.allPassed());
            // assertPassFail(summary, "DoubleClick", TestOutcomeStats.allPassed());
            // assertPassFail(summary, "MouseOver", TestOutcomeStats.allPassed());
        }
    }

    @Test
    @Throws(Exception::class)
    fun collectPerformanceMetrics() {
        // System.getProperty(BROWSER, "chrome.headless")

        val executionSummary = testViaExcel("unitTest_web_metrics.xlsx")
        assertPassFail(executionSummary, "GoogleSearch", TestOutcomeStats.allFailed())
    }
}
