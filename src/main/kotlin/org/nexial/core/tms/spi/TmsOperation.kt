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

import org.nexial.core.model.ExecutionSummary
import org.nexial.core.tms.model.TestFile
import org.nexial.core.tms.model.TestRun
import org.nexial.core.tms.model.TmsSuite
import org.nexial.core.tms.model.TmsTestCase

interface TMSOperation {
    /**
     * Create a new test suite with the provided suite name
     *
     * @param suiteName the name of suite
     * @return the [TmsSuite] as a response of the API after suite creation
     */
    @Throws(TmsException::class)
    fun createSuite(suiteName: String, testPath: String): TmsSuite

    /**
     * Update test suite with the provided runtime config like description, Nexial Version and modified time
     *
     * @param parentId the name of suite
     * @return the [TmsSuite] as a response of the API after updating suite

     */
    @Throws(TmsException::class)
    fun updateSuite(parentId: String, testPath: String): TmsSuite

    /**
     * Add section (mainly for TestRail and for others suite create response)
     *
     * @param sectionName the name of section
     * @param parentId suite id
     * @return the ID from section created for suite
     */
    @Throws(TmsException::class)
    fun addSection(parentId: String, sectionName: String): String

    /**
     * Add test cases (mainly for TestRail and for others suite create response)
     *
     * @param parentId the id of section
     * @param testCases List of the TmsTestcase
     * @return the map of the added testcase name to testId
     */
    @Throws(TmsException::class)
    fun addCases(parentId: String, testCases: List<TmsTestCase>): Map<String, String> {
        val testCaseToScenario = mutableMapOf<String, String>()
        testCases.forEach { testCaseToScenario.putAll(updateCase(parentId, it, true)) }
        return testCaseToScenario
    }

    /**/

    /*val testCaseToScenario = mutableMapOf<String, String>()
    testCases.forEach { testCaseToScenario.putAll(updateCase(parentId, it, true)) }
    return testCaseToScenario*/

    /*val testCaseIdToTestName = mutableMapOf<String, String>()
    testCases.forEach { testCaseIdToTestName.putAll(updateCase(parentId, it, true)) }
    return testCaseIdToTestName*/

    /**
     * Add test cases (mainly for TestRail and for others suite create response)
     *
     * @param parentId testcase id to update
     * @param testCase TmsTestcase to update
     * @param isNewTestCase to check if is new testcase
     * @return the map of the added testcase name to testId
     */
    @Throws(TmsException::class)
    fun updateCase(parentId: String, testCase: TmsTestCase, isNewTestCase: Boolean): Map<String, String>

    /**
     * Delete test case
     *
     * @param parentId the id of section
     * @param testcaseId id of testcase to delete
     * @return pass is testcase deleted else false
     */
    @Throws(TmsException::class)
    fun delete(parentId: String, testcaseId: String) : Boolean

    /**
     * Reorder test cases
     *
     * @param rootId the id of section
     * @param parentId id of section if any otherwise suiteId only
     * @param order list of TestCaseOrder
     */
    @Throws(TmsException::class)
    fun updateCaseOrder(rootId: String, parentId: String?, order: List<TestcaseOrder>)

    /**
     * Retrieve sections if required
     *
     * @param parentId the id of section
     * @return array of sections if any else empty array
     */
    @Throws(TmsException::class)
    fun getSectionId(parentId: String): String

    /**
     * Retrieve existing active runs in case of TestRails
     *
     * @param parentId the id of section
     * @return array of active runs if any else empty array
     */
    @Throws(TmsException::class)
    fun getExistingActiveRuns(parentId: String): List<TestRun>

    /**
     * Close test runs      *
     * @param runId of the test run
     */
    @Throws(TmsException::class)
    fun closeRun(runId: String)

    /**
     * Add test results for testcases
     * @param summary executionSummary to add results from
     * @param parentId the id of section
     * @param scenarioIds list of scenarioIds to update
     * @param suiteUrl url of the suite
     */
    @Throws(TmsException::class)
    fun addResults(summary: ExecutionSummary, file: TestFile)
}