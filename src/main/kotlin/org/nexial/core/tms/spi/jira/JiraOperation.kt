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

package org.nexial.core.tms.spi.jira

import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import org.json.simple.JSONValue
import org.nexial.commons.utils.DateUtility
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.TMSSettings.*
import org.nexial.core.NexialConst.Ws.*
import org.nexial.core.excel.ExcelConfig
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.tms.TmsConst.ACCOUNT_ID
import org.nexial.core.tms.TmsConst.ACTIVE_USER
import org.nexial.core.tms.TmsConst.COLOR1
import org.nexial.core.tms.TmsConst.COLOR_END_TEXT
import org.nexial.core.tms.TmsConst.DISPLAY_NAME
import org.nexial.core.tms.TmsConst.EMAIL_ADDRESS
import org.nexial.core.tms.TmsConst.EXECUTION_OUTPUT_HTML
import org.nexial.core.tms.TmsConst.FAILED_COLOR
import org.nexial.core.tms.TmsConst.FIELDS
import org.nexial.core.tms.TmsConst.FOCUSSED_COMMENT_ID
import org.nexial.core.tms.TmsConst.ID
import org.nexial.core.tms.TmsConst.KEY
import org.nexial.core.tms.TmsConst.OUTPUT_EXCEL_PATTERN
import org.nexial.core.tms.TmsConst.PASSED_COLOR
import org.nexial.core.tms.TmsConst.RESULT_LINK_TEXT
import org.nexial.core.tms.TmsConst.STORY
import org.nexial.core.tms.TmsConst.SUBTASK
import org.nexial.core.tms.TmsConst.simpleDateFormat
import org.nexial.core.tms.model.*

import org.nexial.core.tms.spi.*
import org.nexial.core.tms.spi.TmsSource.JIRA
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JSONPath
import java.io.File
import java.util.*

class JiraOperations : TMSOperation {
    lateinit var formatter: TmsFormatter
    var projectId: String
    var client: WebServiceClient
    var url: String

    init {
        val data = TmsFactory.loadTmsData()
        projectId = TmsProcessor.projectId
        client = WebServiceClient(null).withBasicAuth(data.user ?: "", data.password)
        url = StringUtils.appendIfMissing(data.url, "/") + "rest/api/2/"
    }

    private var suiteIssueTypeId: String? = null
    private var caseIssueTypeId: String? = null

    @Throws(Exception::class)
    override fun createSuite(suiteName: String, testPath: String): TmsSuite {
        try {
            setSuiteIssueType()
            val fields =
                Fields(summary = suiteName, issuetype = Id(suiteIssueTypeId), project = Id(key = projectId),
                    description = TmsSuiteDesc(testPath).format(JIRA), assignee = Id(retrieveAccountId()))
            val issueData = IssueData(fields)
            val payload = GSON.fromJson(GSON.toJson(issueData), MutableMap::class.java)
            val jsonObject = ResponseHandler.handleResponse(
                client.post("${url}issue", JSONValue.toJSONString(payload))) as JSONObject
            return TmsSuite(jsonObject.getString(ID), suiteName, getSuiteURL(jsonObject), null)
        } catch (e: Exception) {
            throw TmsException("Suite creation failed due to ${e.message}")
        }

    }

    override fun updateSuite(parentId: String, testPath: String): TmsSuite {
        try {
            val issueData = IssueData(Fields(description = TmsSuiteDesc(testPath).format(JIRA)))
            val payload = GSON.fromJson(GSON.toJson(issueData), MutableMap::class.java)
            val jsonObject = ResponseHandler.handleResponse(
                client.put("${url}issue/$parentId", JSONValue.toJSONString(payload))) as JSONObject
            return TmsSuite(Files.getNameWithoutExtension(testPath), parentId, null)
        }  catch (e: Exception) {
            throw TmsException("Suite updating failed: " + e.message)
        }
    }

    // returning suiteId as it is because there is no section for JIRA
    override fun addSection(parentId: String, sectionName: String) = parentId

    override fun updateCase(parentId: String, testCase: TmsTestCase, isNewTestCase: Boolean): Map<String, String> {
        val testCaseToScenario = mutableMapOf<String, String>()
        val response: JSONObject
        val responseId: String
        val scenarioName = testCase.testCaseName
        val data = formatter.formatTestSteps(testCase)
        setSuiteIssueType()
        val reqJsonObject = JSONObject(data)
        if (isNewTestCase) {
            ConsoleUtils.log("Adding test case '$scenarioName'")
            val fieldsJson = reqJsonObject.getJSONObject(FIELDS)
            val fieldsObj = Fields(
                issuetype = Id(id = caseIssueTypeId),
                project = Id(key = projectId), parent = Id(id = parentId),
                assignee = Id(id = retrieveAccountId()),
                summary = fieldsJson.getString("summary"),
                description = fieldsJson.getString("description"))

            reqJsonObject.put(FIELDS, JSONObject(GSON.toJson(fieldsObj)))
            response = ResponseHandler.handleResponse(
                client.post("${url}issue", reqJsonObject.toString())) as JSONObject
            responseId = response[ID] as String
            ConsoleUtils.log("Added test case '$scenarioName' with id $responseId")
        } else {
            try {
                ConsoleUtils.log("Updating test case '$scenarioName'")

                ResponseHandler.handleResponse(client.put("${url}issue/$parentId", reqJsonObject.toString())) as JSONObject
                responseId = parentId
                ConsoleUtils.log("Updated test case '$scenarioName' with id: $parentId.")
            } catch (e: Exception) {
                throw TmsException("Could not update test case '$scenarioName' due to ${e.message}")
            }
        }
        testCaseToScenario[scenarioName] = responseId
        return testCaseToScenario
    }

    override fun delete(parentId: String, testcaseId: String): Boolean {
        var isDeleted = false
        try {
            ConsoleUtils.log("Deleting test case: $testcaseId")
            val response = ResponseHandler.handleResponse(client.delete("${url}issue/$testcaseId", "")) as JSONObject
            if (response.has("error")) {
                ConsoleUtils.error("Unable to delete testcase with id: $testcaseId")
            } else {
                ConsoleUtils.log("Deleted testcase with id: $testcaseId")
                isDeleted = true
            }
        } catch (e: Exception) {
            ConsoleUtils.error("Unable to delete testcase with id '$testcaseId' due to ${e.message}")
        }
        return isDeleted
    }

    override fun updateCaseOrder(rootId: String, parentId: String?, order: List<TestcaseOrder>) {
        ConsoleUtils.error("Reordering is not supported for JIRA.")
    }

    // todo add executionSummary for loop here and remove scenarioIds
    override fun addResults(summary: ExecutionSummary, file: TestFile) {
        val bodyPrefix = getBodyPrefix(summary)
        val suiteId = file.suiteId!!
        val suiteCommentBody = bodyPrefix + updateResult(suiteId, summary)
        val suiteResult = addResult(suiteId, suiteCommentBody)
        val testCaseCommentBody = bodyPrefix + NL + RESULT_LINK_TEXT + file.suiteUrl +
                FOCUSSED_COMMENT_ID + suiteResult.getString("id") + "]"
        getScenarios(summary, file).forEach { addResult(it, testCaseCommentBody) }
    }

    fun getScenarios(summary: ExecutionSummary, file: TestFile):  MutableList<String> {
        val scenarioIds = mutableListOf<String>()
        val planSteps = file.planSteps
        if (planSteps == null || planSteps.isEmpty()) {
            // For script file
            val scenarios = file.scenarios!!
            summary.nestedExecutions[0].nestedExecutions[0].nestedExecutions
                .forEach { addScenarioIds(it.name, scenarios, scenarioIds) }
        } else {
            // for plan file
            summary.nestedExecutions.forEach { script ->
                val scriptImportedFilter = planSteps.filter {
                    TmsMetaJson.getRelativePath(script.scriptFile).equals(it.path!!) }
                    .filter { it.stepId!!.toInt() == script.planSequence + ExcelConfig.PLAN_ROW_START_INDEX }
                if(scriptImportedFilter.isNotEmpty() && file.subplan == script.planName) {
                    val scenarios = scriptImportedFilter.first().scenarios!!
                    script.nestedExecutions.forEach { iter ->
                        iter.nestedExecutions.forEach { addScenarioIds(it.name, scenarios, scenarioIds) }
                    }
                }
            }
        }
        return scenarioIds
    }

    override fun getSectionId(parentId: String) = parentId

    override fun closeRun(runId: String) = ConsoleUtils.error("NOT Supported to close test run by JIRA")

    override fun getExistingActiveRuns(parentId: String): List<TestRun> {
        ConsoleUtils.error("NOT Supported to get existing active runs for JIRA")
        return mutableListOf()
    }

    /** Add execution results to mentioned issue as a comment
     *
     * @param issueId id of the issue
     * @param body  result body to upload in the issue comment
     * @return [JSONObject] of the response after uploadig result
     */
    private fun addResult(issueId: String, body: String): JSONObject {
        client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
        return ResponseHandler.handleResponse(client.post("${url}issue/$issueId/comment",
                JSONValue.toJSONString(mutableMapOf(Pair("body", body))))) as JSONObject
    }

    /** Upload execution output files
     *
     * @param files [MutableList] of execution files to be uploaded
     * @param issueId id of the issue to which files to be uploaded
     */
    private fun uploadFiles(files: MutableList<String>, issueId: String) {
        // 1. attach file
        client.addHeader(WS_CONTENT_TYPE, WS_IGNORE_CONTENT_TYPE)
        client.addHeader("X-Atlassian-Token", "no-check")
        files.forEach {
            try {
                ResponseHandler.handleResponse(client.postMultipart("${url}issue/$issueId/attachments", "file=$it", "file"))
            } catch (e: Exception) {
                ConsoleUtils.error("Unable to upload file '$it' due to ${e.message}")
            }
        }
    }

    /** Upload execution files and create and return execution report information
     *
     * @param issueId id of the issue to which execution output to be uploaded
     * @param summary [ExecutionSummary] from which result data to be uploaded
     */
    private fun updateResult(issueId: String, summary: ExecutionSummary): String {
        // 1. attach file
        val excelReports = StringBuilder()
        val files: MutableList<String> = ArrayList()
        FileUtils
            .listFiles(File(System.getProperty(OPT_OUT_DIR)), arrayOf("xlsx"), false).stream()
            .filter { !it.name.startsWith("~") && RegexUtils.match(it.name, OUTPUT_EXCEL_PATTERN) }
            .forEach {
                files.add(it.absolutePath)
                excelReports.append("Click below icon to download output excel *${it.name}*!${it.name}" +
                        "|width=30,height=30!$NL")
            }
        // upload files before adding link
        uploadFiles(files, issueId)
        val htmlReport = System.getProperty(OPT_OUT_DIR) + EXECUTION_OUTPUT_HTML
        var htmlReportInfo = ""
        if (System.getProperty(OPT_OUT_DIR) != null && File(htmlReport).exists()) {
            htmlReportInfo = "Go to below location to view HTML Report" + NL + File(htmlReport).absolutePath + NL
        }

        // 2. add comment
        return "|${PASSED_COLOR}Passed Tests$NL*${summary.passCount}*$COLOR_END_TEXT" +
                "|${FAILED_COLOR}Failed Tests$NL*${summary.failCount}*$COLOR_END_TEXT" +
                "|${COLOR1}Success Rate$COLOR_END_TEXT$NL*${summary.successRateString}*$NL$NL" +
                "|$htmlReportInfo|$excelReports"
    }

    /** Get common body prefix for the execution result
     *
     * @param summary [ExecutionSummary] of the exection output to be uploaded
     * @return basic execution result e.g. Start time, elapsed time
     *
     */
    private fun getBodyPrefix(summary: ExecutionSummary): String {
        val startTime = simpleDateFormat.format(Date(summary.startTime))
        val elapsedTime = DateUtility.formatStopWatchTime(summary.elapsedTime)
        val link = linkedMapOf("Execution Result" to summary.name, "Started On" to startTime, "Execution Time" to elapsedTime)
        var pref = ""

        link.forEach{ (key, value) -> pref += "|${COLOR1}$key$COLOR_END_TEXT$NL*$value*" }
        return "$pref$NL"
    }


    /** Retrieve supported JIRA issue types information for provided project
     *
     * @param typeName name of the issue type e.g. Epic, Task, Subtask
     * @param projectId id of the project to retrieve issue type information from
     * @return [Map] of issue type information
     */
    @Throws(TmsException::class)
    private fun retrieveIssueInfo(typeName: String, projectId: String): Map<*, *> {
        val jsonArray = ResponseHandler.handleResponse(client.get("${url}issuetype", ""))
        val issueTypesResponse = JSONPath.find(jsonArray as JSONArray, "[name=$typeName]")
        if (issueTypesResponse != null) {
            if (issueTypesResponse.startsWith("{") && issueTypesResponse.endsWith("}")) {
                val issueTypeData = GSON.fromJson(issueTypesResponse, MutableMap::class.java) as MutableMap<String, Any>
                if (getValidIssueInfo(issueTypeData, projectId)) return issueTypeData

            } else if (issueTypesResponse.startsWith("[") && issueTypesResponse.endsWith("]")) {
                val issueTypesList = GSON.fromJson(issueTypesResponse, MutableList::class.java)
                for (i in 0 until issueTypesList.size) {
                    val issueTypeData = issueTypesList[i] as MutableMap<String, Any>
                    if (getValidIssueInfo(issueTypeData, projectId)) return issueTypeData
                }
            }
        }
        ConsoleUtils.error("Provided issue type $typeName not supported for your JIRA.")
        val json1 = JSONPath.find(jsonArray, "name => distinct")
        throw TmsException("Supported JIRA issue types list:: $json1")
    }

    /** Check if given jira issue type is valid for given project
     *
     * @param map [MutableMap] of the issueType information
     * @param expectedProjectId [String] expected project id to validate available project id from issue type information
     * @return [Boolean] if issue type is valid for expected project id
     */
    private fun getValidIssueInfo(map: MutableMap<String, Any>, expectedProjectId: String): Boolean {
        if (map.containsKey("scope")) {
            val scope = map["scope"] as Map<*, *>
            val project = scope["project"] as Map<*, *>
            val projectId = project["id"] as String
            if (projectId == expectedProjectId) return true
        }
        return false
    }

    /** set suite issue type for suite and testcases from TMS_SUITE_ISSUE_TYPE and TMS_CASE_ISSUE_TYPE */
    fun setSuiteIssueType() {
        if (!StringUtils.isAnyEmpty(suiteIssueTypeId, caseIssueTypeId)) return

        val suiteType = System.getProperty(TMS_SUITE_ISSUE_TYPE, STORY)
        val caseType = System.getProperty(TMS_CASE_ISSUE_TYPE, SUBTASK)

        val projectInfo = ResponseHandler.handleResponse(client.get("${url}project/$projectId", "")) as JSONObject
        if (!projectInfo.has(ID)) {
            throw TmsException("Unable to retrieve project id")
        }
        val suiteIssueInfo = retrieveIssueInfo(suiteType, projectInfo[ID] as String)
        val caseIssueInfo = retrieveIssueInfo(caseType, projectInfo[ID] as String)
        if ((suiteIssueInfo["hierarchyLevel"] as Double) != (caseIssueInfo["hierarchyLevel"] as Double + 1)) {
            throw TmsException("Please do check hierarchy level for issue types under JIRA settings. " +
                    "$suiteType issue does not support child issue of type $caseType.")

        }
        suiteIssueTypeId = suiteIssueInfo[ID] as String
        caseIssueTypeId = caseIssueInfo[ID] as String
    }

    /** Retrieve account id of the user importing Nexial script/plan to assign issue
     *
     * @return account id of the user
     */
    private fun retrieveAccountId(): String? {
        val userName = System.getProperty(TMS_USERNAME) ?: return ""

        val jsonArray = ResponseHandler.handleResponse(client.get("${url}users/search", "")) as JSONArray
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray[i] as JSONObject
            if (jsonObject.has(ACCOUNT_ID) &&
                    jsonObject.has(ACTIVE_USER) && jsonObject[ACTIVE_USER] == true && 
                    (jsonObject.has(EMAIL_ADDRESS) && jsonObject[EMAIL_ADDRESS] == userName ||
                    jsonObject.has(DISPLAY_NAME) && jsonObject[DISPLAY_NAME] == userName)) {
                return jsonObject.getString(ACCOUNT_ID)
            }
        }
        return ""
    }

    fun getSuiteURL(createSuiteResponse: JSONObject) = "${System.getProperty(TMS_URL)}browse/${createSuiteResponse[KEY]}"

    private fun addScenarioIds(scenarioName: String, scenarios: List<Scenario>, scenarioIds: MutableList<String>) {
        val matchedTests = scenarios.filter { it.name == scenarioName }
        if (matchedTests.isNotEmpty()) { scenarioIds.add(matchedTests.first().testCaseId) }
    }
}


/** POJO to create issue/suite. */
data class Fields(val summary: String? = null, val description: String? = null, val issuetype: Id? = null,
                  val project: Id? = null, val parent: Id? = null, val assignee: Id? = null)

/** POJO to create issue id object. */
data class Id(val id: String? = null, val key: String? = null)

/** POJO to create issue data object. */
data class IssueData(val fields: Fields?)
