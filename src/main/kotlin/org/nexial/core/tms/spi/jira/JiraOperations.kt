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

import com.google.gson.annotations.SerializedName
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import org.nexial.commons.utils.DateUtility
import org.nexial.commons.utils.EnvUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.TMSSettings.*
import org.nexial.core.NexialConst.Ws.*
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
import kotlin.collections.LinkedHashMap

class JiraOperations(val formatter: TmsFormatter) : TMSOperation {
    val projectId: String
    val client: WebServiceClient
    val url: String
    private var suiteIssueTypeId: String? = null
    private var caseIssueTypeId: String? = null

    init {
        val data = TmsFactory.loadTmsData()
        projectId = TmsProcessor.projectId
        client = WebServiceClient(null).withBasicAuth(data.user, data.password)
        url = StringUtils.appendIfMissing(data.url, "/") + "rest/api/2/"
    }

    @Throws(Exception::class)
    override fun createSuite(suiteName: String, testPath: String): TmsSuite {
        try {
            retrieveIssueType()
            val fields = Fields(summary = suiteName, issueType = Id(suiteIssueTypeId), project = Id(key = projectId),
                    description = TmsSuiteDesc(testPath).format(JIRA), assignee = retrieveAccountId())
            val issueData = IssueData(fields)

            val jsonObject = ResponseHandler.handleResponse(
                client.post("${url}issue", GSON.toJson(issueData))) as JSONObject
            return TmsSuite(jsonObject.getString(ID), suiteName, getSuiteURL(jsonObject[KEY] as String), null)
        } catch (e: Exception) {
            throw TmsException("Suite creation failed due to ${e.message}")
        }
    }

    override fun updateSuite(parentId: String, testPath: String): TmsSuite {
        try {
            val issueData = IssueData(Fields(description = TmsSuiteDesc(testPath).format(JIRA)))
            ResponseHandler.handleResponse(
                client.put("${url}issue/$parentId", GSON.toJson(issueData))) as JSONObject
            return TmsSuite(parentId, File(testPath).name.substringBeforeLast("."))
        }  catch (e: Exception) {
            throw TmsException("Suite updating failed due to ${e.message}")
        }
    }

    // returning suiteId as it is, because there is no section for JIRA
    override fun addSection(parentId: String, sectionName: String) = parentId

    override fun updateCase(parentId: String, testCase: TmsTestCase, isNewTestCase: Boolean): Map<String, String> {
        val testCaseToScenario = mutableMapOf<String, String>()
        val response: JSONObject
        val responseId: String
        val scenarioName = testCase.testCaseName
        val data = formatter.formatTestSteps(testCase)
        retrieveIssueType()
        val testcaseReqObj = JSONObject(data)
        if (isNewTestCase) {
            ConsoleUtils.log("Adding test case '$scenarioName'")
            val fieldsJson = testcaseReqObj.getJSONObject(FIELDS)
            val fieldsObj = Fields(
                issueType = Id(id = caseIssueTypeId),
                project = Id(key = projectId), parent = Id(id = parentId),
                assignee = retrieveAccountId(),
                summary = fieldsJson.getString("summary"),
                description = fieldsJson.getString("description"))

            testcaseReqObj.put(FIELDS, JSONObject(GSON.toJson(fieldsObj)))
            response = ResponseHandler.handleResponse(
                client.post("${url}issue", testcaseReqObj.toString())) as JSONObject
            responseId = response[ID] as String
            ConsoleUtils.log("Added test case '$scenarioName' with id $responseId")
        } else {
            try {
                ConsoleUtils.log("Updating test case '$scenarioName'")

                ResponseHandler.handleResponse(client.put("${url}issue/$parentId", testcaseReqObj.toString())) as JSONObject
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
        try {
            ConsoleUtils.log("Deleting test case: $testcaseId")
            val response = ResponseHandler.handleResponse(client.delete("${url}issue/$testcaseId", "")) as JSONObject
            if (response.has("error")) {
                ConsoleUtils.error("Unable to delete testcase with id: $testcaseId")
            } else {
                ConsoleUtils.log("Deleted testcase with id: $testcaseId")
                return true
            }
        } catch (e: Exception) {
            ConsoleUtils.error("Unable to delete testcase with id '$testcaseId' due to ${e.message}")
        }
        return false
    }

    override fun updateCaseOrder(rootId: String, parentId: String?, order: List<TestcaseOrder>) {
        ConsoleUtils.error("Reordering is not supported for JIRA.")
    }

    override fun addResults(summary: ExecutionSummary, file: TestFile) {
        val scenarios = getScenarios(summary, file)
        if(scenarios.isEmpty()) {
            throw TmsException("No Test results found for ${summary.name} imported in the test run. Exiting...")
        }
        val resultPrefix = getResultPrefix(summary)
        val suiteId = file.suiteId!!
        val suiteCommentResultBody = resultPrefix + NL + updateResult(suiteId, summary)

        client.addHeader(WS_CONTENT_TYPE, WS_JSON_CONTENT_TYPE)
        val suiteResult = ResponseHandler.handleResponse(client.post("${url}issue/$suiteId/comment",
            GSON.toJson(mutableMapOf(Pair("body", suiteCommentResultBody))))) as JSONObject

        val testcaseToResultMap = getTestcaseCommentBody(scenarios, resultPrefix,
            suiteResult.getString("id"), file.suiteUrl!!)
        addResult(testcaseToResultMap)
    }

    override fun getSectionId(parentId: String) = parentId

    override fun closeRun(runId: String) = ConsoleUtils.error("NOT Supported to close test run by JIRA")

    override fun getExistingActiveRuns(parentId: String): List<TestRun> {
        ConsoleUtils.error("NOT Supported to get existing active runs for JIRA")
        return mutableListOf()
    }

    private fun getSuiteURL(key: String) = "${System.getProperty(TMS_URL)}browse/$key"

    /** Add execution results to mentioned issue as a comment
     *
     * @param testResultMap map of testcase id to request body
     * @return [JSONObject] of the response after uploadig result
     */
    private fun addResult(testResultMap: Map<String, String>) {
        testResultMap.forEach { (issueId, body) ->
            try {
                ResponseHandler.handleResponse(client.post("${url}issue/$issueId/comment",
                    GSON.toJson(mutableMapOf("body" to body)))) as JSONObject
            } catch (e: Exception) {
                throw TmsException("Unable to update test result for issue id $issueId")
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
        val outDir = System.getProperty(OPT_OUT_DIR)
        val filesToUpload = getFilesToUpload(outDir)
        // upload files before adding link
        uploadAttachmentsForTestRun(filesToUpload, issueId)

        var excelReports = ""
        filesToUpload.forEach {
            val name = File(it).name
            excelReports += "Click below icon to download output excel *${name}*!${name}|width=200,height=200!$NL"
        }

        var htmlReportInfo = ""
        val file = File(outDir + EXECUTION_OUTPUT_HTML)
        if (file.exists()) {
            // todo support cloud link
            htmlReportInfo = "Go to below location to view HTML Report$NL${file.absolutePath}$NL" +
                    "*${COLOR1}\\[$USERNAME || ${EnvUtils.getHostName()}]$COLOR_END_TEXT*$NL"
        }

        // 2. add comment
        return "|${PASSED_COLOR}Passed Tests$NL*${summary.passCount}*$COLOR_END_TEXT" +
                "|${FAILED_COLOR}Failed Tests$NL*${summary.failCount}*$COLOR_END_TEXT" +
                "|${COLOR1}Success Rate$COLOR_END_TEXT$NL*${summary.successRateString}*$NL$NL" +
                "|$htmlReportInfo|$excelReports"
    }

    /** Upload execution output files
     *
     * @param files [MutableList] of execution files to be uploaded
     * @param issueId id of the issue to which files to be uploaded
     */
    private fun uploadAttachmentsForTestRun(files: MutableList<String>, issueId: String) {
        // 1. attach file
        client.addHeader(WS_CONTENT_TYPE, WS_IGNORE_CONTENT_TYPE)
        client.addHeader("X-Atlassian-Token", "no-check")
        files.forEach {
            try {
                ResponseHandler.handleResponse(
                    client.postMultipart("${url}issue/$issueId/attachments", "file=$it", "file"))
            } catch (e: Exception) {
                ConsoleUtils.error("Unable to upload file '$it' due to ${e.message}")
            }
        }
    }

    /** Get common body prefix for the execution result
     *
     * @param summary [ExecutionSummary] of the exection output to be uploaded
     * @return basic execution result e.g. Start time, elapsed time
     *
     */
    private fun getResultPrefix(summary: ExecutionSummary): String {
        val startTime = simpleDateFormat.format(Date(summary.startTime))
        val elapsedTime = DateUtility.formatStopWatchTime(summary.elapsedTime)
        val executionResult = linkedMapOf("Execution Result" to summary.name,
            "Started On" to startTime, "Execution Time" to elapsedTime)
        return getResultInFormat(executionResult)
    }

    private fun getTestcaseCommentBody(scenarios: Map<String, TestResult>, resultPrefix: String,
                                       resultId: String, suiteUrl: String): LinkedHashMap<String, String> {
        val mapResult = linkedMapOf<String, String>()
        scenarios.forEach { (testcaseId, result) ->
            val executionResult = linkedMapOf("Passed Tests" to result.passCount.toString(),
                "Failed Tests" to result.failCount.toString(), "SkippedTests" to result.skipCount.toString())
            val pref = resultPrefix + NL + getResultInFormat(executionResult) + NL
            val testCaseCommentBody ="$pref$NL$RESULT_LINK_TEXT$suiteUrl$FOCUSSED_COMMENT_ID$resultId]"
            mapResult[testcaseId] = testCaseCommentBody
        }
        return mapResult
    }

    private fun getResultInFormat(executionResult: LinkedHashMap<String, String>): String {
        var pref = ""
        executionResult.forEach { (header, value) -> pref += "|${COLOR1}$header$COLOR_END_TEXT$NL*$value*" }
        return pref
    }

    /** set suite issue type for suite and testcases from TMS_SUITE_ISSUE_TYPE and TMS_CASE_ISSUE_TYPE */
    private fun retrieveIssueType() {
        if (!StringUtils.isAnyEmpty(suiteIssueTypeId, caseIssueTypeId)) return

        val suiteType = System.getProperty(TMS_SUITE_ISSUE_TYPE, STORY)
        val caseType = System.getProperty(TMS_CASE_ISSUE_TYPE, SUBTASK)

        val projectInfo = ResponseHandler.handleResponse(client.get("${url}project/$projectId", "")) as JSONObject
        if (!projectInfo.has(ID)) { throw TmsException("Unable to retrieve project id") }

        val projectId = projectInfo.getString(ID)
        val suiteIssueInfo = retrieveIssueInfo(suiteType, projectId)
        val caseIssueInfo = retrieveIssueInfo(caseType, projectId)
        if ((suiteIssueInfo["hierarchyLevel"] as Double) != (caseIssueInfo["hierarchyLevel"] as Double + 1)) {
            throw TmsException("Please do check hierarchy level for issue types under JIRA settings. " +
                    "$suiteType issue does not support child issue of type $caseType.")

        }
        suiteIssueTypeId = suiteIssueInfo[ID] as String
        caseIssueTypeId = caseIssueInfo[ID] as String
    }

    /** Retrieve supported JIRA issue types information for provided project
     *
     * @param typeName name of the issue type e.g. Epic, Task, Subtask
     * @param projectId id of the project to retrieve issue type information from
     * @return [Map] of issue type information
     */
    @Throws(TmsException::class)
    private fun retrieveIssueInfo(typeName: String, projectId: String): MutableMap<String, Any> {
        val issueTypeResponse = ResponseHandler.handleResponse(client.get("${url}issuetype", ""))
        val issueTypesWithSpecifiedType = JSONPath.find(issueTypeResponse as JSONArray, "[name=$typeName]")
        if (issueTypesWithSpecifiedType != null) {
            if (issueTypesWithSpecifiedType.startsWith("{") && issueTypesWithSpecifiedType.endsWith("}")) {
                val issueTypeData = GSON.fromJson(issueTypesWithSpecifiedType, MutableMap::class.java) as MutableMap<String, Any>
                if (checkForValidIssueType(issueTypeData, projectId)) return issueTypeData

            } else if (issueTypesWithSpecifiedType.startsWith("[") && issueTypesWithSpecifiedType.endsWith("]")) {
                val issueTypesList = GSON.fromJson(issueTypesWithSpecifiedType, MutableList::class.java)
                for (i in 0 until issueTypesList.size) {
                    val issueTypeData = issueTypesList[i] as MutableMap<String, Any>
                    if (checkForValidIssueType(issueTypeData, projectId)) return issueTypeData
                }
            }
        }
        ConsoleUtils.error("Provided issue type $typeName not supported for your JIRA.")
        val supportedIssueType = JSONPath.find(issueTypeResponse, "name => distinct")
        throw TmsException("Supported JIRA issue types list:: $supportedIssueType")
    }

    /** Check if given jira issue type is valid for given project
     *
     * @param issueType [MutableMap] of the issueType information
     * @param expectedProjectId [String] expected project id to validate available project id from issue type information
     * @return [Boolean] if issue type is valid for expected project id
     */
    private fun checkForValidIssueType(issueType: MutableMap<String, Any>, expectedProjectId: String): Boolean {
        if (issueType.containsKey("scope")) {
            val scope = issueType["scope"] as Map<*, *>
            val project = if(scope.containsKey("project")) scope["project"] as Map<*, *> else return false
            val projectId = if(project.containsKey(ID)) project[ID] as String else return false
            return projectId == expectedProjectId
        }
        return false
    }


    /** Retrieve account id of the user importing Nexial script/plan to assign issue
     *
     * @return account id of the user
     */
    private fun retrieveAccountId(): Id? {
        val userName = System.getProperty(TMS_USERNAME) ?: return null

        val jsonArray = ResponseHandler.handleResponse(client.get("${url}users/search", "")) as JSONArray
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray[i] as JSONObject
            if (jsonObject.has(ACCOUNT_ID) &&
                jsonObject.has(ACTIVE_USER) && jsonObject[ACTIVE_USER] == true &&
                (jsonObject.has(EMAIL_ADDRESS) && jsonObject[EMAIL_ADDRESS] == userName ||
                        jsonObject.has(DISPLAY_NAME) && jsonObject[DISPLAY_NAME] == userName)) {
                val accountId = jsonObject.getString(ACCOUNT_ID)
                return if (StringUtils.isEmpty(accountId)) null else Id(accountId)
            }
        }
        return null
    }
}

/**
 * DO NOT RENAME any of following field as it is used as json payload
 */
/** POJO to create issue/suite. */
data class Fields(val summary: String? = null, val description: String? = null,
                  @SerializedName("issuetype") val issueType: Id? = null,
                  val project: Id? = null, val parent: Id? = null, val assignee: Id? = null)


/** POJO to create issue data object. */
data class IssueData(val fields: Fields?)

/** POJO to create issue id object. */
data class Id(val id: String? = null, val key: String? = null)

