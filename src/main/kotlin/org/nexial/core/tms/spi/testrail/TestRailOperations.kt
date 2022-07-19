package org.nexial.core.tms.spi.testrail

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import org.json.simple.JSONValue
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.OPT_OUT_DIR
import org.nexial.core.NexialConst.Ws.*
import org.nexial.core.excel.ExcelConfig.PLAN_ROW_START_INDEX
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.tms.TmsConst.CASE_IDS
import org.nexial.core.tms.TmsConst.DESCRIPTION
import org.nexial.core.tms.TmsConst.EXECUTION_OUTPUT_HTML
import org.nexial.core.tms.TmsConst.FAILED_CODE
import org.nexial.core.tms.TmsConst.ID
import org.nexial.core.tms.TmsConst.JUNIT_XML
import org.nexial.core.tms.TmsConst.NAME
import org.nexial.core.tms.TmsConst.OUTPUT_EXCEL_PATTERN
import org.nexial.core.tms.TmsConst.RUN
import org.nexial.core.tms.TmsConst.SUCCESS_CODE
import org.nexial.core.tms.TmsConst.SUITE_ID
import org.nexial.core.tms.TmsConst.TESTRAIL_API_VERSION
import org.nexial.core.tms.model.*
import org.nexial.core.tms.spi.*
import org.nexial.core.tms.spi.TmsMetaJson.getRelativePath
import org.nexial.core.utils.ConsoleUtils
import java.io.File

class TestRailOperations: TMSOperation {
    lateinit var formatter: TmsFormatter
    var projectId: String
    var client: WebServiceClient
    var url: String

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

    /**
     * Create a new section inside the suite associated with the specified suite id
     *
     * @param parentId     the suite id in which the section is to be added
     * @param sectionName the name of the section
     * @return the json response of the API after section creation
     */
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

    /**
     * Adds or update a single test case based on the input parameters
     *
     * @param parentId            in case of new test case, this will represent the section id in which new test case is to be added,
     * otherwise it represents the test case id of the test case to update
     * @param testCase   an instance of [TmsTestCase] containing Test Case and Test Steps
     * @param isNewTestCase true if a new test case is to be added, false if an existing test case is to be updated
     * @return a [Map] of the test case id to the test case name
     */
    @Throws(TmsException::class)
    override fun updateCase(parentId: String, testCase: TmsTestCase, isNewTestCase: Boolean): Map<String, String> {
        if (StringUtils.isEmpty(parentId)) {
            throw TmsException("Unable to add or update the test case")
        }
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
            } catch (e: Exception) {
                throw TmsException("Could not update test case: '$testCaseName' due to ${e.message}")
            }
            if (ObjectUtils.isEmpty(response)) {
                throw TmsException("Could not update test case '$testCaseName'")
            }
        }
        testCaseToTestName[testCaseName] = response[ID].toString()
        return testCaseToTestName
    }

    /**
     * Delete the specified test cases from the suite
     * @param parentId    [List] sectionId of test cases to delete
     * @param testcaseId [String] test case id to delete
     */
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

    /**
     * Retrieve all the sections associated with the suite id passed in
     *
     * @param parentId the suite id
     * @return JSONArray containing the sections belonging to the suite
     */
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

    /**
     * Close the Test Run associated with the Test Run id passed in
     * @param runId test run id
     */
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
        val scenarios = getScenarios(summary, file)

        val caseIds = scenarios.map { it.key.toInt() }.toMutableSet()
        val testResult = TestRailTestRun(file.suiteId, summary.name, false, caseIds)

        val testRunResponse = ResponseHandler.handleResponse(
            client.post("${url}add_run/$projectId", GSON.toJson(testResult))) as JSONObject

        val runId = testRunResponse.getInt(ID)
        attachFilesToRun(runId)

        client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
        addTestResults(scenarios, runId)

        // close test run for modification
        closeRun(runId.toString())
    }

    /**
     * Get all the existing active runs for the suite id passed in
     *
     * @param parentId the suite id
     * @return a [JSONArray] containing the active runs
     */
    override fun getExistingActiveRuns(parentId: String): List<TestRun> {
        return try {
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
            activeRuns
        } catch (e: Exception) {
            ConsoleUtils.error("Could not retrieve active test runs due to ${e.message}")
            mutableListOf()
        }
    }

    @Throws(TmsException::class)
    private fun updateSuite(suiteIdOrName: String, testPath: String, isNew: Boolean): TmsSuite {
        return try {
            val suite = mutableMapOf<String, Any>()
            suite[DESCRIPTION] = TmsSuiteDesc(testPath).format(TmsSource.TESTRAIL)
            if (isNew) {
                suite[NAME] = suiteIdOrName
                client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
                ConsoleUtils.log("Creating suite for project id: $projectId with name: $suiteIdOrName")
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

    private fun addTestResults(scenarios: LinkedHashMap<String, Int>, runId: Int) {
        scenarios.forEach { (key, value) ->
            val resultStatusJson = JSONObject()
            resultStatusJson.put("status_id", value)
            val testResultResponse = ResponseHandler.handleResponse(
                client.post("${url}add_result_for_case/$runId/$key", resultStatusJson.toString())) as JSONObject
            if (testResultResponse.has("error")) {
                ConsoleUtils.error("Unable to update result for the test case $key ")
            }
        }
    }

    private fun attachFilesToRun(runId: Int) {
        client.addHeader(WS_CONTENT_TYPE, WS_MULTIPART_CONTENT_TYPE)

        val outputDir = System.getProperty(OPT_OUT_DIR)
        val excelFiles = FileUtils
            .listFiles(File(outputDir), arrayOf("xlsx"), false)
            .filter { !it.name.startsWith("~") }.filter { RegexUtils.match(it.name, OUTPUT_EXCEL_PATTERN) }
            .map { it.absolutePath }

        val files = mutableListOf<String>()
        files.add(outputDir + JUNIT_XML)
        files.add(outputDir + EXECUTION_OUTPUT_HTML)
        files.addAll(excelFiles)

        files.forEach {
            ResponseHandler.handleResponse(client.postMultipart("${url}add_attachment_to_run/$runId",
                "attachment=$it", "attachment"))
        }
    }

    private fun getScenarios(summary: ExecutionSummary, file: TestFile): LinkedHashMap<String, Int> {
        val scenarioIdsToStatus = linkedMapOf<String, Int>()
        val planSteps = file.planSteps
        if (planSteps == null || planSteps.isEmpty()) {
            val scenarios = file.scenarios!!
            summary.nestedExecutions[0].nestedExecutions.forEach { iterExec ->
                iterExec.nestedExecutions.forEach { addResultStatus(scenarioIdsToStatus, scenarios, it) }
            }
        } else {
            summary.nestedExecutions.forEach { scriptExec ->
                val scriptImportedFilter = planSteps.filter {
                    getRelativePath(scriptExec.scriptFile).equals(it.path!!) }
                    .filter { it.stepId!!.toInt() == scriptExec.planSequence + PLAN_ROW_START_INDEX }
                if(scriptImportedFilter.isNotEmpty() && file.subplan == scriptExec.planName) {
                    val scenarios = scriptImportedFilter.first().scenarios!!
                    scriptExec.nestedExecutions.forEach { iterExec ->
                        iterExec.nestedExecutions.forEach { addResultStatus(scenarioIdsToStatus, scenarios, it) }
                    }
                }
            }
        }
        return scenarioIdsToStatus
    }

    private fun addResultStatus(scenarioIdsToStatus: LinkedHashMap<String, Int>,
                                scenarios: List<Scenario>, scenarioExec: ExecutionSummary) {
        val matchingTestCases = scenarios.filter { it.name == scenarioExec.name }
        if(matchingTestCases.isEmpty()) return

        var isPassed = scenarioExec.failCount == 0
        val testCaseId = matchingTestCases.first().testCaseId
        if (scenarioIdsToStatus.containsKey(testCaseId)) {
            isPassed = isPassed && scenarioIdsToStatus[testCaseId] == SUCCESS_CODE
        }
        scenarioIdsToStatus[testCaseId] = if (isPassed) SUCCESS_CODE else FAILED_CODE
    }

    @Throws(TmsException::class)
    private fun getSuiteURL(suiteId: String) = StringUtils.substringBefore(url, TESTRAIL_API_VERSION) +
            "/suites/view/$suiteId"
}