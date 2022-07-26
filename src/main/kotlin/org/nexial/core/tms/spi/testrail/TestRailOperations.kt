package org.nexial.core.tms.spi.testrail

import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import org.json.simple.JSONValue
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.OPT_OUT_DIR
import org.nexial.core.NexialConst.Ws.*
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.tms.TmsConst.CASE_IDS
import org.nexial.core.tms.TmsConst.DESCRIPTION
import org.nexial.core.tms.TmsConst.EXECUTION_OUTPUT_HTML
import org.nexial.core.tms.TmsConst.FAILED_CODE
import org.nexial.core.tms.TmsConst.ID
import org.nexial.core.tms.TmsConst.NAME
import org.nexial.core.tms.TmsConst.RUN
import org.nexial.core.tms.TmsConst.STATUS_ID
import org.nexial.core.tms.TmsConst.SUCCESS_CODE
import org.nexial.core.tms.TmsConst.SUITE_ID
import org.nexial.core.tms.TmsConst.TESTRAIL_API_VERSION
import org.nexial.core.tms.model.*
import org.nexial.core.tms.spi.*
import org.nexial.core.utils.ConsoleUtils
import kotlin.collections.LinkedHashMap


class TestRailOperations(val formatter: TmsFormatter): TMSOperation {
    val projectId: String
    val client: WebServiceClient
    val url: String

    init {
        val data = TmsFactory.loadTmsData()
        projectId = TmsProcessor.projectId
        client = WebServiceClient(null).withBasicAuth(data.user, data.password)
        url = "${StringUtils.appendIfMissing(data.url, "/")}index.php?$TESTRAIL_API_VERSION"
    }

    @Throws(TmsException::class)
    override fun createSuite(suiteName: String, testPath: String) = updateSuite(suiteName, testPath, true)

    @Throws(TmsException::class)
    override fun updateSuite(parentId: String, testPath: String) = updateSuite(parentId, testPath, false)

    @Throws(TmsException::class)
    override fun addSection(parentId: String, sectionName: String): String {
        return try {
            val section = mutableMapOf<String, Any>()
            section[DESCRIPTION] = "Section corresponding to script: $sectionName"
            section[NAME] = sectionName
            section[SUITE_ID] = parentId
            ConsoleUtils.log("Creating section for suite: $sectionName")
            val jsonObject = ResponseHandler.handleResponse(
                client.post("${url}add_section/$projectId", JSONValue.toJSONString(section))) as JSONObject
            jsonObject.getInt(ID).toString()
        } catch (e: Exception) {
            throw TmsException("Section creation failed due to ${e.message}")
        }
    }

    @Throws(TmsException::class)
    override fun updateCase(parentId: String, testCase: TmsTestCase, isNewTestCase: Boolean): Map<String, String> {
        if (StringUtils.isEmpty(parentId)) throw TmsException("Unable to add or update the test case")

        val testCaseToTestName = mutableMapOf<String, String>()
        val response: JSONObject
        val testCaseName = testCase.testCaseName
        val testCaseJsonReq = formatter.formatTestSteps(testCase)
        if (isNewTestCase) {
            try {
                ConsoleUtils.log("Adding test case: '$testCaseName'")
                response = ResponseHandler.handleResponse(
                    client.post("${url}add_case/$parentId", testCaseJsonReq)) as JSONObject
                if (ObjectUtils.isEmpty(response)) {
                    throw TmsException("Could not add test case: $testCaseName")
                }
                ConsoleUtils.log("Added test case: '$testCaseName' with ID : ${response[ID]}")
            } catch (e: Exception) {
                throw TmsException("Could not update test case '$testCaseName' due to ${e.message}")
            }
        } else {
            try {
                ConsoleUtils.log("Updating test case: $testCaseName")
                response = ResponseHandler.handleResponse(
                    client.post("${url}update_case/$parentId", testCaseJsonReq)) as JSONObject
                ConsoleUtils.log("Updated test case '$testCaseName' with id $parentId")
                if (ObjectUtils.isEmpty(response)) {
                    throw TmsException("Could not update test case '$testCaseName'")
                }
            } catch (e: Exception) {
                throw TmsException("Could not update test case: '$testCaseName' due to ${e.message}")
            }
        }
        testCaseToTestName[testCaseName] = response[ID].toString()
        return testCaseToTestName
    }

    override fun delete(parentId: String, testcaseId: String): Boolean {
        var isDeleted = false
        try {
            ConsoleUtils.log("Deleting test case with id: $testcaseId")
            val response = ResponseHandler.handleResponse(
                client.post("${url}delete_case/$testcaseId", JSONValue.toJSONString(0))) as JSONObject
            if (response.has("error")) {
                ConsoleUtils.error(response.getString("error"))
            } else {
                isDeleted = true
                ConsoleUtils.log("Deleted test case with id: '$testcaseId'")
            }
        } catch (e: Exception) {
            ConsoleUtils.error("Could not delete test case: '$testcaseId' due to ${e.message}")
        }
        return isDeleted
    }

    override fun updateCaseOrder(rootId: String, parentId: String?, order: List<TestcaseOrder>) {
        try {
            val id = if (StringUtils.isEmpty(parentId)) getSectionId(rootId) else parentId
            val scenarios = mutableMapOf<String, String>()

            val testIds = order.joinToString { it.id }
            scenarios[CASE_IDS] = testIds
            ConsoleUtils.log("Reordering test cases")
            client.post("${url}move_cases_to_section/$id", JSONValue.toJSONString(scenarios))
        } catch (e: Exception) {
            throw TmsException("Error occurred during re-ordering of test cases due to ${e.message}")
        }
    }

    @Throws(TmsException::class)
    override fun getSectionId(parentId: String): String {
        try {
            val sectionResponse = ResponseHandler.handleResponse(
                client.get("${url}get_sections/$projectId&suite_id=$parentId", "")) as JSONObject
            return sectionResponse.getJSONArray("sections").getJSONObject(0).getInt(ID).toString()
        } catch (e: Exception) {
            throw TmsException("Unable to retrieve sections for project: $projectId " +
                    "and suite id: $parentId due to ${e.message}")
        }
    }

    @Throws(TmsException::class)
    override fun closeRun(runId: String) {
        try {
            val run = ResponseHandler.handleResponse(
                client.post("${url}close_run/$runId", JSONValue.toJSONString(mapOf(Pair(RUN, runId))))) as JSONObject
            ConsoleUtils.log("Closed test run: ${run[ID]} | ${run[NAME]}")
        } catch (e: Exception) {
            throw TmsException("Error occurred during closing of active test run: '$runId' due to ${e.message}")
        }
    }

    override fun addResults(summary: ExecutionSummary, file: TestFile) {
        val suiteId = file.suiteId!!

        val testResults = getScenarios(summary, file)
        if(testResults.isEmpty()) {
            throw TmsException("No Test results found for ${summary.name} imported in the test run. Exiting...")
        }
        val existingActiveRuns = getExistingActiveRuns(suiteId)
        // create new test run if no active runs
        val runId = if(existingActiveRuns.isEmpty()) {
            val caseIds = testResults.map { (testcaseId, _) -> testcaseId.toInt() }.toMutableSet()
            // add test run name same as suite name
            val testRun = TestRailTestRun(suiteId, file.suiteName!!, caseIds)
            val testRunResponse = ResponseHandler.handleResponse(
                client.post("${url}add_run/$projectId", GSON.toJson(testRun))) as JSONObject

           testRunResponse.getInt(ID)
        } else { existingActiveRuns[0].id }

        uploadAttachmentsForTestRun(runId)
        client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
        addTestResults(runId, testResults, file.custom)
        ConsoleUtils.log("Test results uploaded for run id '$runId'")

        ConsoleUtils.log("You have not closed test run with id $runId. ")
        if(shouldCloseTestRun()) {
            closeRun(runId.toString())
        } else {
            ConsoleUtils.log("Test Run with id '$runId is not closed. You can close manually.")
        }
    }

    override fun getExistingActiveRuns(parentId: String): List<TestRun> {
        try {
            val activeRuns = mutableListOf<TestRun>()
            val activeRunsResponse = ResponseHandler.handleResponse(
                client.get("${url}get_runs/$projectId&suite_id=$parentId&is_completed=0", "")) as JSONObject
            val activeRunsArray = activeRunsResponse["runs"] as JSONArray
            if (activeRunsArray.length() == 0) {
                ConsoleUtils.error("There are no active runs for the specified file. All test runs are already closed.")
            } else {
                for (i in 0 until activeRunsArray.length()) {
                    val jsonObj = activeRunsArray.getJSONObject(i)
                    activeRuns.add(TestRun(jsonObj.getInt(ID), jsonObj.getString(NAME)))
                }
            }
            return activeRuns
        } catch (e: Exception) {
            throw TmsException("Could not retrieve active test runs due to ${e.message}")
        }
    }

    @Throws(TmsException::class)
    private fun updateSuite(suiteIdOrName: String, testPath: String, isNew: Boolean): TmsSuite {
        return try {
            val suite = mutableMapOf<String, String>()
            suite[DESCRIPTION] = TmsSuiteDesc(testPath).format(TmsSource.TESTRAIL)
            if (isNew) {
                suite[NAME] = suiteIdOrName
                client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
                ConsoleUtils.log("Creating suite for project id '$projectId' with name '$suiteIdOrName'")

                val jsonObject = ResponseHandler.handleResponse(
                    client.post("${url}add_suite/$projectId", JSONValue.toJSONString(suite))) as JSONObject
                val suiteId = jsonObject.getInt(ID).toString()
                TmsSuite(suiteId, suiteIdOrName, getSuiteURL(suiteId), null)
            } else {
                ConsoleUtils.log("Updating suite with id: $suiteIdOrName")
                val jsonObject = ResponseHandler.handleResponse(
                    client.post("${url}update_suite/$suiteIdOrName", JSONValue.toJSONString(suite))) as JSONObject
                TmsSuite(suiteIdOrName, jsonObject.getString(NAME), getSuiteURL(suiteIdOrName), null)
            }
        } catch (e: Exception) {
            throw TmsException("Suite creation or updating failed due to ${e.message}")
        }
    }

    private fun addTestResults(runId: Int, scenarios: LinkedHashMap<String, TestResult>,
                               customField: CustomField?) {
        scenarios.forEach { (testcaseId, testResult) ->
            val statusId = if(testResult.outcome) SUCCESS_CODE else FAILED_CODE
            val resultStatusJson = JSONObject()
            resultStatusJson.put(STATUS_ID, statusId)
            val stats = customField?.stats
            if(stats != null) {
                resultStatusJson.put("custom_$stats", "PASS: ${testResult.passCount} | " +
                        "FAIL: ${testResult.failCount} | SKIPPED: ${testResult.skipCount} ")
            }
            val testResultResponse = ResponseHandler.handleResponse(
                client.post("${url}add_result_for_case/$runId/$testcaseId", resultStatusJson.toString())) as JSONObject
            if (testResultResponse.has("error")) {
                throw TmsException("Unable to update result for the test case $testcaseId ")
            }
        }
    }

    private fun uploadAttachmentsForTestRun(runId: Int) {
        client.addHeader(WS_CONTENT_TYPE, WS_MULTIPART_CONTENT_TYPE)

        val outputDir = System.getProperty(OPT_OUT_DIR)
        val files = getFilesToUpload(outputDir)
        files.add(outputDir + EXECUTION_OUTPUT_HTML)

        files.forEach { file ->
            try {
                ResponseHandler.handleResponse(client.postMultipart("${url}add_attachment_to_run/$runId",
                    "attachment=$file", "attachment"))
            } catch (e: Exception) {
                ConsoleUtils.error("Unable to attach $file due to ${e.message}")
            }
        }
    }

    @Throws(TmsException::class)
    private fun getSuiteURL(suiteId: String) =
        StringUtils.substringBefore(url, TESTRAIL_API_VERSION) + "/suites/view/$suiteId"
}

/**
 * DO NOT RENAME fields as it is used as json payload to create test
 */
data class TestRailTestRun(val suite_id: String?, val name: String, val case_ids: MutableSet<Int>?,
                           val include_all: Boolean = false)

