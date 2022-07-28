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


package org.nexial.core.tms.spi.azureDevOps

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONObject
import org.json.simple.JSONValue
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.OPT_OUT_DIR
import org.nexial.core.NexialConst.Ws.*
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.tms.TmsConst.API_VERSION_5_0_PREVIEW_1
import org.nexial.core.tms.TmsConst.API_VERSION_5_0_PREVIEW_5
import org.nexial.core.tms.TmsConst.API_VERSION_6_0
import org.nexial.core.tms.TmsConst.API_VERSION_6_0_PREVIEW_1
import org.nexial.core.tms.TmsConst.API_VERSION_6_0_PREVIEW_2
import org.nexial.core.tms.TmsConst.COMPLETED
import org.nexial.core.tms.TmsConst.DESCRIPTION
import org.nexial.core.tms.TmsConst.EXECUTION_OUTPUT_HTML
import org.nexial.core.tms.TmsConst.ID
import org.nexial.core.tms.TmsConst.INPROGRESS
import org.nexial.core.tms.TmsConst.NAME
import org.nexial.core.tms.TmsConst.WORK_ITEM
import org.nexial.core.tms.TmsConst.dataFormat
import org.nexial.core.tms.model.*
import org.nexial.core.tms.spi.*
import org.nexial.core.tms.spi.ResponseHandler.handleResponse
import org.nexial.core.tms.spi.jira.Id
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.util.*

/**
 * Perform all the AzureDevOps related operations
 */
class AzureDevOpsOperations(val formatter: TmsFormatter) : TMSOperation {
    var projectId: String
    var client: WebServiceClient
    var url: String

    init {
        val data = TmsFactory.loadTmsData()
        client = WebServiceClient(null).withBasicAuth(data.user ?: "", data.password)
        projectId = TmsProcessor.projectId
        url = "${StringUtils.appendIfMissing(data.url, "/")}$projectId/_apis/"
    }

    override fun createSuite(suiteName: String, testPath: String) = updateSuite(suiteName, testPath, true)

    override fun updateSuite(parentId: String, testPath: String) = updateSuite(parentId, testPath, false)

    @Throws(TmsException::class)
    private fun updateSuite(suiteIdOrName: String, testPath: String, isNew: Boolean): TmsSuite {
        val jsonObject =  try {
            val suite: MutableMap<String, Any> = mutableMapOf()
            suite[DESCRIPTION] = TmsSuiteDesc(testPath).format(TmsSource.AZURE)
            client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
            if (isNew) {
                suite[NAME] = suiteIdOrName
                ConsoleUtils.log("Creating suite for project id: $projectId with name: $suiteIdOrName")
                handleResponse(client.post("${url}testplan/plans?$API_VERSION_6_0_PREVIEW_1",
                    JSONValue.toJSONString(suite))) as JSONObject
            } else {
                ConsoleUtils.log("Updating suite with id: $suiteIdOrName")
                handleResponse(client.patch("${url}testplan/plans/$suiteIdOrName?$API_VERSION_6_0_PREVIEW_1",
                    JSONValue.toJSONString(suite))) as JSONObject
            }
        } catch (e: Exception) {
            throw TmsException("Suite creation or updating failed: ${e.message}")
        }
        val suiteId = jsonObject.getInt(ID).toString()
        return TmsSuite(suiteId, jsonObject.getString(NAME), getSuitUrl(suiteId), null)
    }

    fun getSuitUrl(suiteId: String) =
        url.substringBeforeLast("_apis") + "_testPlans/define?planId=$suiteId&suiteId=${suiteId + 1}"

    override fun addSection(parentId: String, sectionName: String): String {
        val response = handleResponse(
            client.get("${url}testplan/plans/$parentId/suites?$API_VERSION_6_0_PREVIEW_1", "")) as JSONObject
        return response.getJSONArray("value").getJSONObject(0).getInt(ID).toString()
    }

    override fun updateCase(parentId: String, testCase: TmsTestCase, isNewTestCase: Boolean): Map<String, String> {
        val testCaseToScenario = mutableMapOf<String, String>()
        val response: JSONObject?
        val scenarioName = testCase.testCaseName
        val jsonRequest = formatter.formatTestSteps(testCase)
        client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE3)

        if (isNewTestCase) {
            response = handleResponse(client.post("${url}wit/workitems/\$Test%20Case?$API_VERSION_6_0",
                jsonRequest)) as JSONObject

            //  attach work item to suite
            val caseId = response[ID].toString().toInt()
            client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)

            ConsoleUtils.log("Adding test case '$scenarioName'")
            val workItemReq = mutableListOf<Map<String, Any>>(mapOf(WORK_ITEM to mapOf(ID to caseId)))

            val response1 = handleResponse(
                client.post("${url}testplan/plans/${parentId.toInt() - 1}" +
                        "/suites/$parentId/TestCase?$API_VERSION_6_0_PREVIEW_2",
                    JSONValue.toJSONString(workItemReq))) as JSONObject

            if (response1.has("count") && (response1.getInt("count")) > 0)
                ConsoleUtils.log("Added test case '$scenarioName' with $caseId")
            else ConsoleUtils.log("Could not add testcase '$scenarioName'")
        } else {
            try {
                ConsoleUtils.log("Updating test case $scenarioName")
                response = handleResponse(client.patch("${url}wit/workitems/$parentId?$API_VERSION_6_0",
                    jsonRequest)) as JSONObject
                ConsoleUtils.log("Updated test case $scenarioName with id $parentId")
                // setting back for other api call
                client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
                if (ObjectUtils.isEmpty(response)) { throw TmsException("Could not update test case '$scenarioName'") }
            } catch (e: Exception) {
                throw TmsException("Could not update test case '$scenarioName' due to ${e.message}")
            }
        }

        testCaseToScenario[scenarioName] = response[ID].toString()
        return testCaseToScenario
    }

    override fun updateCaseOrder(rootId: String, parentId: String?, order: List<TestcaseOrder>) {
        try {
            handleResponse(client.patch("${url}testplan/suiteentry/${rootId.toInt() + 1}" +
                            "?$API_VERSION_6_0_PREVIEW_1", order.toString()))
            ConsoleUtils.log("Updated testcase order")
        } catch (e: Exception) {
            ConsoleUtils.error("Could not update testcase order due to ${e.message}")
        }
    }

    override fun delete(parentId: String, testcaseId: String): Boolean {
        var isDeleted = false
        try {
            ConsoleUtils.log("Deleting test case with id $testcaseId")
            val response = handleResponse(
                client.delete("${url}test/TestCases/$testcaseId?$API_VERSION_5_0_PREVIEW_1", "")) as JSONObject

            if(response.has("error")) {
                ConsoleUtils.error("Could not delete testcase with id: $testcaseId")
            } else {
                ConsoleUtils.log("Deleted testcase with id $testcaseId")
                isDeleted = true
            }
        } catch (e: Exception) {
            ConsoleUtils.error("Could not delete testcase with id '$testcaseId' due to ${e.message}")
        }
        return isDeleted
    }

    override fun getSectionId(parentId: String) = (parentId.toInt() + 1).toString()

    override fun getExistingActiveRuns(parentId: String): List<TestRun> {
        val existingRunResponse = handleResponse(client.get("${url}test/runs?" +
                "includeRunDetails=true&api-version=7.1-preview.3", "")) as JSONObject

        val existingRuns = existingRunResponse.getJSONArray("value")
        val existingActiveRuns = mutableListOf<TestRun>()
        existingRuns.map{ it as JSONObject }.forEach {
            if(it.has("plan") && parentId == it.getJSONObject("plan").getInt(ID).toString()) {
                existingActiveRuns.add(GSON.fromJson(it.toString(), TestRun::class.java))
            }
        }
        // Get all InProgress Test Runs
        return existingActiveRuns.filter { it.state == INPROGRESS }
    }

    override fun closeRun(runId: String) {
        client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
        val response = handleResponse(client.patch("${url}test/runs/$runId?$API_VERSION_6_0",
            GSON.toJson(TestRun(state = COMPLETED)))) as JSONObject
        if (response.has("error")) {
            ConsoleUtils.error("Unable to close in progress test run due to ${response["error"]}")
        }
    }

    override fun addResults(summary: ExecutionSummary, file: TestFile) {
        val suiteId = file.suiteId!!
        val existingActiveRuns = getExistingActiveRuns(suiteId)
        // create new test run if no active runs
        val runId = if(existingActiveRuns.isEmpty()) {
            val testRunPayload = GSON.toJson(createTestRun(suiteId, file.suiteName!!, summary))
            val response = handleResponse(client.post("${url}test/runs?$API_VERSION_6_0", testRunPayload)) as JSONObject
            val testRunResponse = GSON.fromJson(response.toString(), TestRun::class.java)
            testRunResponse.id
        } else {
            existingActiveRuns[0].id
        }

        uploadAttachmentsForTestRun(runId)
        val testResults = getScenarios(summary, file)
        if(testResults.isEmpty()) {
            throw TmsException("No Test results found for ${summary.name} imported in the test run. Exiting...")
        }
        val resultResponse = handleResponse(client.post("${url}test/runs/$runId/results?$API_VERSION_5_0_PREVIEW_5",
            GSON.toJson(createResultPayload(testResults)))) as JSONObject
        if(!resultResponse.has("count")) {
            ConsoleUtils.error("Unable to upload results to test run")
        } else {
            ConsoleUtils.log("Test results uploaded for run id '$runId'")
        }

        ConsoleUtils.log("You have not closed test run with id $runId. ")
        // add prompt before closing
        if(shouldCloseTestRun()) {
            closeRun(runId.toString())
        } else {
            ConsoleUtils.log("Test Run with id '$runId is not closed. You can close manually.")
        }
    }

    private fun createTestRun(suiteId: String, suiteName: String, summary: ExecutionSummary) : TestRun {
        return TestRun(name = suiteName, plan = Plan(suiteId),
            startDate = dataFormat.format(Date(summary.startTime)),
            completeDate = dataFormat.format(Date(summary.endTime)))
    }

    private fun createResultPayload(testResults: Map<String, TestResult>): MutableList<AzureTestResult> {
        val azureTestResults = mutableListOf<AzureTestResult>()
        testResults.forEach { (testCaseId, res) ->
            val pointId = getTestPointId(testCaseId) ?: return@forEach
            val outcome = if(res.outcome) "Passed" else "Failed"
            val comment = "PASS: ${res.passCount} | " + "FAIL: ${res.failCount} | SKIPPED: ${res.skipCount} "
            azureTestResults.add(
                AzureTestResult(res.name, Id(res.suiteId), Id(getSectionId(res.suiteId)),
                Id(testCaseId), Id(pointId), res.name, res.name, res.durationInMs.toString(),
                outcome, comment))
        }
        return azureTestResults
    }
    private fun uploadAttachmentsForTestRun(runId: Int) {
        val outDir = System.getProperty(OPT_OUT_DIR)
        val files = getFilesToUpload(outDir).toMutableList()
        files.add(outDir + EXECUTION_OUTPUT_HTML)

        files.forEach {
            try {
                handleResponse(client.post("${url}test/runs/$runId/attachments?$API_VERSION_6_0_PREVIEW_1",
                        getAttachmentPayload(it)))
            } catch (e: Exception) {
                ConsoleUtils.error("Unable to upload $it due to ${e.message}")
            }
        }
    }

    private fun getAttachmentPayload(path: String): String? {
        val file = File(path)
        if (!FileUtil.isFileReadable(file)) {
            throw TmsException("File ${file.absolutePath} doesn't exist or is not readable")
        }
        val outputFolder = file.parentFile.name
        val comment = if(file.endsWith("xlsx")) "Output Excel for $outputFolder" else "HTML Report for $outputFolder"
        val encodedStream = String(Base64.getEncoder().encode(FileUtils.readFileToByteArray(file)))
        return GSON.toJson(AzureTestResultAttachment(file.name, encodedStream, comment))
    }

    /**
     * Returns test point id for test case imported
     *
     * @param testCaseId [String] of test whose pointId to find out
     * @return point id of testcase [String] to be retrieved for test results
     */
    private fun getTestPointId(testCaseId: String) : String? {
        val pointPayload = PointPayload(PointFilter(mutableListOf(testCaseId)))
        val response = handleResponse(client.post("${url}test/points?api-version=7.1-preview.2",
            GSON.toJson(pointPayload))) as JSONObject
        return if(response.has("points")){
            val pointsArray = response.getJSONArray("points")
            if(pointsArray == null || pointsArray.length() == 0) {
                null
            } else {
                pointsArray.getJSONObject(0).getInt("id").toString()
            }
        } else
            throw TmsException("Unable to find point id for test case. Test case with id $testCaseId might not exist")
    }
}

/**
 * DO NOT RENAME any of following field as it is used as json payload
 */
data class AzureTestResultAttachment(val fileName: String, val stream: String,
                                     val comment: String = "Testcase result attachment",
                                     val attachmentType: String = "GeneralAttachment")

/**
 * DO NOT RENAME any of following field as it is used as json payload
 */
data class AzureTestResult(val testCaseTitle: String, val testPlan: Id, val testSuite: Id,
                           val testCase: Id, val testPoint: Id, val automatedTestName: String,
                           val automatedTestStorage: String, val durationInMs: String,
                           val outcome: String, val comment: String = "", val state: String = COMPLETED)

/**
 * Class to get test point from PointFilter
 */
data class PointFilter(val TestcaseIds: MutableList<String>)
/**
 * Class to have payload to get test point from PointFilter
 */
data class PointPayload(val PointsFilter: PointFilter)
