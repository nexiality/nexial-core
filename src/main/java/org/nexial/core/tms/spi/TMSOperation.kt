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