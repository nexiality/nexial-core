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

import com.google.gson.JsonObject
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.junit.Assert.*
import org.junit.Test
import org.nexial.core.ExcelBasedTests
import org.nexial.core.NexialConst.GSON
import java.io.File
import java.io.File.separator
import java.io.FileReader

class HeadlessBaseTests : ExcelBasedTests() {
    @Test
    @Throws(Exception::class)
    fun baseCommandTests_part1() {
        // ExecutionSummary executionSummary = testViaExcel("unitTest_base_part1.xlsx", "actual_in_output2");
        val executionSummary = testViaExcel("unitTest_base_part1.xlsx")
        assertPassFail(executionSummary, "base_showcase", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "function_projectfile", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "function_array", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "function_count", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "function_date", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "actual_in_output", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "actual_in_output2", TestOutcomeStats.allPassed())
    }

    @Test
    @Throws(Exception::class)
    fun baseCommandTests_part2() {
        // ExecutionSummary executionSummary = testViaExcel("unitTest_base_part2.xlsx", "flow_controls");
        val executionSummary = testViaExcel("unitTest_base_part2.xlsx")
        assertPassFail(executionSummary, "crypto", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "macro-test", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "repeat-test", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "expression-test", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "multi-scenario2", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "flow_controls", TestOutcomeStats(2, 14))
    }

    @Test
    @Throws(Exception::class)
    fun baseCommandTests_part3() {
        val executionSummary = testViaExcel("unitTest_base_part3.xlsx")
        // ExecutionSummary executionSummary = testViaExcel("unitTest_base_part3.xlsx", "function_format");
        assertPassFail(executionSummary, "function_format", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "execution_count", TestOutcomeStats(2, 9))
        assertPassFail(executionSummary, "waitForCondition", TestOutcomeStats(1, 6))
    }

    @Test
    @Throws(Exception::class)
    fun baseCommandTests_macro3() {
        val executionSummary = testViaExcel("unitTest_base_macro3.xlsx")
        assertPassFail(executionSummary, "start", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "macro-test", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "macro-section-test", TestOutcomeStats.allPassed())
        // to check total steps inside macro-section-test is not reduced
        assertEquals(38, executionSummary.totalSteps)
    }

    @Test
    @Throws(Exception::class)
    fun baseCommandTests_macroFlex() {
        val executionSummary = testViaExcel("unitTest_base_macroFlex.xlsx")
        assertPassFail(executionSummary, "macroFlex", TestOutcomeStats.allPassed())
        assertPassFail(executionSummary, "macroFlexWithoutIO", TestOutcomeStats.allPassed())
    }

    @Test
    @Throws(Exception::class)
    fun numberCommandTests() {
        val executionSummary = testViaExcel("unitTest_numberCommand.xlsx")
        assertPassFail(executionSummary, "Number_Command_Validation", TestOutcomeStats.allPassed())
    }

    @Test
    @Throws(Exception::class)
    fun repeatUntilTests() {
        val executionSummary = testViaExcel("unitTest_repeatUntil.xlsx")
        assertPassFail(executionSummary, "repeatUntil_take1", TestOutcomeStats.allPassed())
    }

    @Test
    @Throws(Exception::class)
    fun repeatUntilAssetTests() {
        val executionSummary = testViaExcel("unitTest_repeatUntil_assert.xlsx", "repeatUntil")
        assertPassFail(executionSummary, "repeatUntil", TestOutcomeStats.allPassed())

        // in this test, we expects no screenshot to be generated
        val outputDir = System.getProperty("nexial.outBase")
        val captureDir = File(StringUtils.appendIfMissing(outputDir, "/") + "captures/")
        val screenshots = FileUtils.listFiles(captureDir, arrayOf("png"), false)
        assertTrue(screenshots == null || screenshots.isEmpty())
    }

    /**
     * test that the combination of section and repeat-until won't throw off the correct number of steps to skip
     */
    @Test
    @Throws(Exception::class)
    fun repeatUntilInsideSectionAssetTests() {
        val executionSummary = testViaExcel("unitTest_repeatUntil_assert.xlsx", "section_with_repeatUntil")
        assertPassFail(executionSummary, "section_with_repeatUntil", TestOutcomeStats.allPassed())
    }

    @Test
    @Throws(Exception::class)
    fun buildNumTest() {
        System.setProperty("nexial.generateReport", "true")

        val executionSummary = testViaExcel("unitTest_buildnum.xlsx", "basic")
        assertPassFail(executionSummary, "basic", TestOutcomeStats.allPassed())

        // check scriptRef
        val executionSummaryJson = StringUtils.appendIfMissing(System.getProperty("nexial.output"), separator) +
                                   "execution-summary.json"
        val json = GSON.fromJson(FileReader(executionSummaryJson), JsonObject::class.java)
        assertTrue(json.has("referenceData"))

        val referenceData = json.get("referenceData").asJsonObject
        assertTrue(referenceData.size() > 0)
        assertEquals("v2.0-1.1419-", referenceData.get("buildnum").asString)
    }

    @Test
    @Throws(Exception::class)
    fun buildNumTest_sysprop() {
        System.setProperty("nexial.generateReport", "true")

        // test that sys prop won't affected the same being modified in execution (script, in this case)
        System.setProperty("nexial.scriptRef.buildnum", "1.2.3-4")

        try {
            val executionSummary = testViaExcel("unitTest_buildnum.xlsx", "basic")
            assertPassFail(executionSummary, "basic", TestOutcomeStats.allPassed())

            // check scriptRef
            val executionSummaryJson = StringUtils.appendIfMissing(System.getProperty("nexial.output"), separator) +
                                       "execution-summary.json"
            val json = GSON.fromJson(FileReader(executionSummaryJson), JsonObject::class.java)
            assertTrue(json.has("referenceData"))

            val referenceData = json.get("referenceData").asJsonObject
            assertTrue(referenceData.size() > 0)
            assertEquals("v2.0-1.1419-", referenceData.get("buildnum").asString)
        } finally {
            System.clearProperty("nexial.scriptRef.buildnum")
            System.clearProperty("nexial.generateReport")
        }
    }

    @Test
    @Throws(Exception::class)
    fun parsingTest() {

        listOf(Pair("duplicate_activity_name", "[A9]: Found duplicate activity name 'Activity1'"),
               Pair("empty_activity_name", "[A7]: Found invalid, space-only activity name"),
               Pair("no_activity_name", "[A5]: Invalid format; First row must contain valid activity name")).forEach {

            val summary = testViaExcel("unitTest_bad_script.xlsx", it.first)
            assertNotNull(summary)
            assertEquals(0, summary.totalSteps)
            assertEquals(0, summary.executed)
            assertEquals(0, summary.passCount)
            assertEquals(0, summary.failCount)
            assertTrue(StringUtils.contains(summary.errorStackTrace, "[${it.first}]${it.second}"))

        }
    }
}
