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

import org.apache.commons.collections4.CollectionUtils
import org.junit.Assert
import org.junit.Test
import org.nexial.core.ExcelBasedTests
import org.nexial.core.NexialConst.OUTPUT_TO_CLOUD
import org.nexial.core.excel.Excel
import org.nexial.core.excel.ExcelAddress
import kotlin.test.assertEquals

class ScriptRefTest : ExcelBasedTests() {

    @Test
    @Throws(Exception::class)
    fun allTests() {
        System.setProperty(OUTPUT_TO_CLOUD, "false")

        val executionSummary = testViaExcel("unitTest_ScenarioRef.xlsx")
        Assert.assertEquals(0, executionSummary.failCount.toLong())
        Assert.assertTrue(CollectionUtils.isNotEmpty(executionSummary.nestedExecutions))
        Assert.assertTrue(CollectionUtils.isNotEmpty(executionSummary.nestedExecutions[0].nestedExecutions))

        // plan -> script -> iteration (this one has the output
        val output = executionSummary.nestedExecutions[0].nestedExecutions[0].testScript
        val outputExcel = Excel(output, false, false)
        val scenarioData = outputExcel.worksheet("#summary").readRange(ExcelAddress("A20:C42"))
        scenarioData.removeIf { it.isEmpty() || it[0] + it[1] + it[2] == "" }

        val scenario1 = "${scenarioData[0][1]}=${scenarioData[0][2]};" +
                        "${scenarioData[1][1]}=${scenarioData[1][2]};" +
                        "${scenarioData[2][1]}=${scenarioData[2][2]};"
        val scenario2 = "${scenarioData[3][1]}=${scenarioData[3][2]};" +
                        "${scenarioData[4][1]}=${scenarioData[4][2]};" +
                        "${scenarioData[5][1]}=${scenarioData[5][2]};"
        val scenario3 = "${scenarioData[6][1]}=${scenarioData[6][2]};" +
                        "${scenarioData[7][1]}=${scenarioData[7][2]};" +
                        "${scenarioData[8][1]}=${scenarioData[8][2]};"
        val scenario4 = "${scenarioData[9][1]}=${scenarioData[9][2]};" +
                        "${scenarioData[10][1]}=${scenarioData[10][2]};" +
                        "${scenarioData[11][1]}=${scenarioData[11][2]};"
        println(scenario1)
        println(scenario2)
        println(scenario3)
        println(scenario4)

        assertEquals("Color=Yellow;Number=142;Scenario=Scenario-1;", scenario1)
        assertEquals("Color=Pink;Number=147;Scenario=Scenario-2;", scenario2)
        assertEquals("Color=Blue;Number=152;Scenario=Scenario-3;", scenario3)
        assertEquals("Color=Orange;Number=157;Scenario=Scenario-4;", scenario4)
    }
}
