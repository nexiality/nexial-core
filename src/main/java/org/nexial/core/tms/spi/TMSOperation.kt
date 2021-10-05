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

package org.nexial.core.tms

import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.tms.model.TmsTestStep
import org.nexial.core.tms.spi.testrail.APIClient

interface TMSOperation {
    var projectId: String
    var client: APIClient


//    fun upload(testCaseMap: LinkedHashMap<String, List<TestCase>>, scriptName: String): TmsSuite?

    fun createSuite(suiteName: String): JSONObject

    fun addSection(sectionName: String, suiteId: String): JSONObject

    fun addCases(section: JSONObject, testCases: List<TmsTestCase>): Map<String, String>

    fun updateCase(id: String, testCase: TmsTestCase, isNewTestCase: Boolean): Map<String, String>

    fun getExistingRuns(suiteId: String): JSONArray?

    fun closeRun(id: String)

    fun delete(testCasesToDelete: List<String>)

    fun updateCaseOrder(suiteId: String, sectionId: String, scenariosInOrder: String)

    fun getSections(suiteId: String): JSONArray?

    fun getExistingActiveRuns(suiteId: String): JSONArray?
}