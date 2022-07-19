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

import com.google.gson.Gson
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONObject
import org.json.simple.JSONValue
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.NL
import org.nexial.core.NexialConst.OPT_OUT_DIR
import org.nexial.core.NexialConst.Ws.*
import org.nexial.core.excel.ExcelConfig.PLAN_ROW_START_INDEX
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.tms.TmsConst.API_VERSION_5_0_PREVIEW
import org.nexial.core.tms.TmsConst.API_VERSION_6_0
import org.nexial.core.tms.TmsConst.API_VERSION_6_0_PREVIEW_1
import org.nexial.core.tms.TmsConst.API_VERSION_6_0_PREVIEW_2
import org.nexial.core.tms.TmsConst.DESCRIPTION
import org.nexial.core.tms.TmsConst.EXECUTION_OUTPUT_HTML
import org.nexial.core.tms.TmsConst.ID
import org.nexial.core.tms.TmsConst.JUNIT_XML
import org.nexial.core.tms.TmsConst.NAME
import org.nexial.core.tms.TmsConst.OUTPUT_EXCEL_PATTERN
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
class AzureDevOpsOperations : TMSOperation {
    lateinit var formatter: TmsFormatter

    var projectId: String
    var client: WebServiceClient
    var url: String

    init {
        val data = TmsFactory.loadTmsData()
        client = WebServiceClient(null).withBasicAuth(data.user ?: "", data.password)
        projectId = TmsProcessor.projectId
        url = StringUtils.appendIfMissing(data.url, "/") + "$projectId/_apis/"
    }

    override fun createSuite(suiteName: String, testPath: String) = updateSuite(suiteName, testPath, true)

    override fun updateSuite(parentId: String, testPath: String) = updateSuite(parentId, testPath, false)

    @Throws(TmsException::class)
    private fun updateSuite(suiteIdOrName: String, testPath: String, isNew: Boolean): TmsSuite {
        val jsonObject =  try {
            val suite: MutableMap<String, Any> = mutableMapOf()
            suite[DESCRIPTION] = TmsSuiteDesc(testPath).format(TmsSource.TESTRAIL)
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
            throw TmsException("Suite creation or updating failed: " + e.message)
        }
        val suiteId = jsonObject.getInt(ID).toString()
        return TmsSuite(suiteId, jsonObject.getString(NAME), getSuitUrl(suiteId), null)
    }

    fun getSuitUrl(suiteId: String) =
        url.substringBeforeLast("_apis") + "_testPlans/define?planId=$suiteId&suiteId=${suiteId + 1}"

    override fun addSection(parentId: String, sectionName: String): String {
        val response = handleResponse(client.get("${url}testplan/plans/$parentId/suites?" +
                        API_VERSION_6_0_PREVIEW_1, "")) as JSONObject
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
                client.post("${url}testplan/plans/${parentId.toInt() - 1}/suites/$parentId/TestCase?$API_VERSION_6_0_PREVIEW_2",
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
                client.delete("${url}test/TestCases/$testcaseId?$API_VERSION_5_0_PREVIEW", "")) as JSONObject

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

    override fun getSectionId(parentId: String) = parentId + 1

    override fun getExistingActiveRuns(parentId: String): List<TestRun> {
        val existingRunResponse = handleResponse(client.get("${url}test/runs?" +
                "includeRunDetails=true&api-version=7.1-preview.3", "")) as JSONObject

        val existingRuns = existingRunResponse.getJSONArray("value")
        val existingActiveRuns = mutableListOf<TestRun>()
        existingRuns.forEach { js ->
            val jsonObject1 = js as JSONObject
            if(jsonObject1.has("plan") && jsonObject1.getJSONObject("plan").getInt(ID).toString() == parentId) {
                existingActiveRuns.add(Gson().fromJson(jsonObject1.toString(), TestRun::class.java))
            }
        }
        // Get all InProgress Test Runs
        return existingActiveRuns.filter { it.state == "InProgress" }
    }

    override fun closeRun(runId: String) {
        client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
        val response = handleResponse(client.patch("${url}test/runs/$runId?$API_VERSION_6_0",
            Gson().toJson(TestRun(state = "Completed")))) as JSONObject
        if (response.has("error")) {
            ConsoleUtils.error("Unable to close in progress test run due to ${response["error"]}")
        }
    }

    override fun addResults(summary: ExecutionSummary, file: TestFile) {
        val testRunPayload = Gson().toJson(createTestRun(summary, file.suiteId!!))
        val response = handleResponse(client.post("${url}test/runs?$API_VERSION_6_0", testRunPayload)) as JSONObject

        val testRunResponse = Gson().fromJson(response.toString(), TestRun::class.java)
        val runId = testRunResponse.id
        attachFilesToRun(runId)

        val resultResponse = handleResponse(client.post("${url}test/runs/$runId/results?$API_VERSION_6_0",
            getResults(summary, file))) as JSONObject
        if(!resultResponse.has("count")) {
            ConsoleUtils.error("Unable to upload results to test run")
        }

        // update test run state to completed
        closeRun(runId.toString())
    }

    private fun attachFilesToRun(runId: Int) {
        val files = mutableListOf<String>()
        files.add(System.getProperty(OPT_OUT_DIR) + JUNIT_XML)
        files.add(System.getProperty(OPT_OUT_DIR) + EXECUTION_OUTPUT_HTML)
        FileUtils
            .listFiles(File(System.getProperty(OPT_OUT_DIR)), arrayOf("xlsx"), false).stream()
            .filter { !it.name.startsWith("~") && RegexUtils.match(it.name, OUTPUT_EXCEL_PATTERN) }
            .forEach { files.add(it.absolutePath) }

        files.forEach {
            handleResponse(client.post("${url}test/runs/$runId/attachments?$API_VERSION_6_0_PREVIEW_1",
                getAttachmentPayload(it))) as JSONObject
        }
    }

    private fun createTestRun(summary: ExecutionSummary, suiteId: String) : TestRun {
        val startDate = dataFormat.format(Date(summary.startTime))
        val completeDate = dataFormat.format(Date(summary.endTime))

        val outputSummary =
            "|Execution Result|Started On|Execution Time|$NL" +
            "|-----------|:-----------|:-----------:|$NL" +
            "|${summary.name}|$startDate|$completeDate|$NL" +
            "|**Passed Tests**|**Failed Tests**|**Success Rate**|$NL" +
            "|${summary.passCount}|${summary.failCount}|${summary.successRateString}|"

        return TestRun(name = summary.nestedExecutions[0].name.substringBefore("(").trim(),
            startDate = startDate, completeDate = completeDate, comment = outputSummary, plan = Plan(suiteId))
    }

    private fun getResults(summary: ExecutionSummary, file: TestFile): String {
        var id = 100000
        val planSteps = file.planSteps
        val results = mutableListOf<AzureTestResult>()
        // todo check for mapping test point
        summary.nestedExecutions.forEach { scriptExec ->
            scriptExec.nestedExecutions.forEach { iterExec ->
                iterExec.nestedExecutions.forEach { scenarioExec ->
                    val automationStorageName = scriptExec.name + iterExec.name
                    val result = if (planSteps == null || planSteps.isEmpty()) {
                        getTestResult(file, id, scenarioExec, automationStorageName)
                    } else {
                        val scriptImportedFilter = planSteps.filter { testFile ->
                            TmsMetaJson.getRelativePath(scriptExec.scriptFile).equals(testFile.path!!) }
                            .filter { it.stepId!!.toInt() == scriptExec.planSequence + PLAN_ROW_START_INDEX }

                        if(scriptImportedFilter.isNotEmpty() && file.subplan == scriptExec.planName) {
                            getTestResult(scriptImportedFilter.first(), id, scenarioExec, automationStorageName)
                        } else null
                    }
                    if(result != null) {
                        results.add(result)
                        id++
                    }
                }
            }
        }
        return Gson().toJson(results)
    }

    private fun getTestResult(file: TestFile, id: Int, scenarioExec: ExecutionSummary,
                              automationStorageName: String): AzureTestResult? {
        val testcaseId = getTestPointId(file.scenarios!!, scenarioExec) ?: return null
        val startDate = dataFormat.format(Date(scenarioExec.startTime))
        val completeDate = dataFormat.format(Date(scenarioExec.endTime))
        val outcome = if (scenarioExec.failCount > 0) "Failed" else "Passed"
        return AzureTestResult(id, scenarioExec.name, "Completed", scenarioExec.name, automationStorageName,
                "junit", startDate, completeDate, Id(testcaseId), outcome, "None")
    }

    private fun getTestPointId(scenarios: List<Scenario>, scenarioExec: ExecutionSummary): String? {
        val matchedTests = scenarios.filter { it.name == scenarioExec.name }
        return if(matchedTests.isNotEmpty()) {
            return matchedTests.first().testCaseId
        } else {
            null
        }
    }

    private fun getAttachmentPayload(path: String): String? {
        val file = File(path)
        if (!FileUtil.isFileReadable(file)) {
            throw TmsException("File ${file.absolutePath} doesn't exist or is not readable")
        }
        val encodedStream = String(Base64.getEncoder().encode(FileUtils.readFileToByteArray(file)))
        val testResultAttachment = TestResultAttachment("GeneralAttachment", "Testcase result attachment", file.name, encodedStream)
        return Gson().toJson(testResultAttachment)
    }
}
