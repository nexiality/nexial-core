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
package org.nexial.core.tms.spi

import org.nexial.core.tms.model.TmsSuite
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.utils.ConsoleUtils

/**
 * Create a new suite (fresh import) for the nexial test file passed in as an argument.
 */
class SuiteUpload(private val suiteName: String, private val testPath: String, private val tms: TMSOperation) {
    val testCaseToStep = LinkedHashMap<Int, Map<String, String>>()

    /**
     * Create a new suite for the Nexial script file passed in as an argument.
     *
     * @param testCases List of valid [TmsTestCase] instances for each scenario inside the script
     * @return TmsSuite object containing details of the newly created suite
     */
    @Throws(TmsException::class)
    fun uploadScript(testCases: List<TmsTestCase>): TmsSuite {
        val tmsSuite = tms.createSuite(suiteName, testPath)
        val suiteId = tmsSuite.id
        ConsoleUtils.log("Test suite created with id: $suiteId")
        val sectionId = tms.addSection(suiteId, suiteName)
        val testCaseToScenario = tms.addCases(sectionId, testCases)
        tmsSuite.testCases = testCaseToScenario
        return tmsSuite
    }

    /**
     * Create a new suite for the plan file passed in
     *
     * @param testCasesToPlanStep [Map] of [TmsTestCase] instance representing the scenarios
     * specified in each plan step
     * @return [TmsSuite] object containing details of the newly created suite
     */
    @Throws(TmsException::class)
    fun uploadPlan(testCasesToPlanStep: LinkedHashMap<Int, List<TmsTestCase>>): TmsSuite {
        return try {
            val tmsSuite = tms.createSuite(suiteName, testPath)
            val suiteId = tmsSuite.id
            val sectionId = tms.addSection(suiteId, suiteName)
            val newTestCaseResponse = linkedMapOf<String, String>()
            testCasesToPlanStep.forEach { (row, testCases) ->
                val caseToScenario = tms.addCases(sectionId, testCases)
                newTestCaseResponse.putAll(caseToScenario)
                testCaseToStep[row] = caseToScenario
            }
            tmsSuite.testCases = newTestCaseResponse
            tmsSuite
        } catch (e: Exception) {
            throw TmsException("Unable to upload complete suite due to " + e.message)
        }
    }
}