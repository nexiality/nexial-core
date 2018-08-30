/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.integration.jira

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.json.JSONArray
import org.json.JSONObject
import org.nexial.core.integration.*
import org.nexial.core.integration.connection.ConnectionFactory
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.AsyncWebServiceClient
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.Syspath
import java.io.File
import java.io.FileOutputStream
import java.util.*


class JiraHelper(val context: ExecutionContext, private val httpClient: AsyncWebServiceClient) : IntegrationHelper() {

    fun process(profile: String, scenario: ScenarioOutput) {
        performActions(profile, scenario)
    }


    private fun performActions(profile: String, scenario: ScenarioOutput) {

        val actions = getActions(context, profile)
        actions.forEach { action ->
            when (action) {
                "comment" -> processComment(profile, scenario)
                "label"   -> processLabel(profile, scenario, AUTOMATION_COMPLETE)
                "defect"  -> processDefect(profile, scenario)
            }
        }
    }

    private fun processComment(profile: String, scenario: ScenarioOutput) {

        val features = mutableSetOf<String>()
        scenario.projects.forEach { projectInfo ->
            if (profile == projectInfo.profile) {
                features.addAll(projectInfo.features)
            }
        }
        context.setData(JIRA_COMMENT_BODY, parseResultToMdFormat(scenario))

        val commentBody = StringUtils.replace(context.replaceTokens(getTemplate(COMMENT_ENDPOINT)),
                                              "\n", "")
        features.forEach { feature ->
            val url = jiraPostCommentUrl(feature, profile)
            addComment(url!!, commentBody)
        }

    }

    private fun processDefect(profile: String, scenarioOutput: ScenarioOutput) {
        for (projectInfo in scenarioOutput.projects) {
            // assume that one project for one profile
            if (profile != projectInfo.profile) {
                continue
            }
            val projectKey = projectInfo.projectName
            val sb = StringBuilder()
            val failedSteps = scenarioOutput.testSteps
            sb.append("h2. *Nexial Defect Summary*\\n----\\n")
            sb.append("h3. *Relates to Feature IDs:* ${projectInfo.features.joinToString(separator = ",")} \\n ")
            sb.append("h3. *Relates to Test IDs:* ${projectInfo.testIds.joinToString(separator = ",")} \\n ")
            sb.append("h3. *Test Script*: ${scenarioOutput.iterationOutput!!.fileName} \\n ")
            sb.append("h3. *Test Scenario*: ${scenarioOutput.scenarioName} \\n ")
            sb.append("h3. *Test Steps*:\\n")
            sb.append("|| Row || Activity || Description || Command || Result || \\n")

            val defectlist = mutableListOf<String>()
            failedSteps.forEach { index, value ->
                val activity = value[0]
                val description = value[1]
                val command = "${value.get(2)}&raquo;${value.get(3)}"
                var params = ""
                (4..8).forEach { i ->
                    val param = value.get(i)
                    if (param.isNotBlank()) {
                        params = "$params$param, "
                    }
                }
                params = "[${StringUtils.removeEnd(params, ", ")}]"
                val msg = StringUtils.replaceAll(value.get(13), "\"", "'")
                sb.append(
                    "| $index | $activity | $description | $command $params | {panel:bgColor=#ff4d4d} $msg {panel} | \\n ")

                val defecthash = "${scenarioOutput.scenarioName}_${activity}_$index"
                defectlist.add(StringUtils.replacePattern(defecthash, "\\s+", ""))
            }

            sb.append("\\n\\n")

            val summary = "A defect is created based on test script failures."
            val description = sb.toString()
            context.setData(JIRA_PROJECT_KEY, projectKey)
            context.setData(JIRA_DEFECT_SUMMARY, summary)
            context.setData(JIRA_DEFECT_DESCRIPTION, description)
            val defectKey = createDefect(projectInfo.server!!)

            //todo: design defect history record
            val defectMeta = Properties()
            if (CollectionUtils.isNotEmpty(defectlist) && defectKey != null) {
                defectlist.forEach { hash -> defectMeta.setProperty(hash, defectKey) }
                defectMeta.store(FileOutputStream(File(
                    "${Syspath().out("fullpath")}${File.separator}defectmeta.properties")), "Nexial Defect History")

            }

            if (defectKey != null) {
                // add defect labels to defect card
                addLabels(defectKey!!, projectInfo.server!!, JSONArray("[\"$DEFECT_LABEL\"]"))

                // create links to features and test ref with defect card
                val inwardLinks = mutableSetOf<String>()
                inwardLinks.addAll(projectInfo.features)
                inwardLinks.addAll(projectInfo.testIds)
                inwardLinks.forEach { link -> addLink(projectInfo.server!!, link, defectKey!!) }
            }
        }
    }

    private fun processLabel(profile: String, scenario: ScenarioOutput, label: String) {

        val features = mutableSetOf<String>()
        scenario.projects.forEach { projectInfo ->
            if (profile == projectInfo.profile) {
                features.addAll(projectInfo.features)
            }
        }
        for (feature in features) {
            val labels = appendToCurrentLabels(feature, profile, label)
            addLabels(feature, profile, labels)
        }

    }

    private fun addLabels(feature: String, profile: String, labels: JSONArray) {
        val putLabelsUrl = jiraPutLabelsUrl(feature, profile)
        context.setData(JIRA_LABELS, labels)
        val labelsBody = StringUtils.replace(context.replaceTokens(getTemplate(LABEL_ENDPOINT)), "\n", "")
        addLabels(putLabelsUrl!!, labelsBody)

    }

    private fun addLink(profile: String, inwardIssue: String, outwardIssue: String) {
        context.setData(JIRA_INWARD_ISSUE, inwardIssue)
        context.setData(JIRA_OUTWARD_ISSUE, outwardIssue)
        val linkUrl = context.getStringData("$INTEGRATION.$profile.$JIRA_ISSUE_LINK_URL")
        var issueLinkBody = context.replaceTokens(getTemplate(LINK_ENDPOINT))
        issueLinkBody = context.replaceTokens(issueLinkBody)
        issueLinkBody = StringUtils.replaceAll(issueLinkBody, "\n", "")
        addLink(linkUrl, issueLinkBody)

    }

    override fun addLink(url: String, linkBody: String) {
        if (UrlValidator.getInstance().isValid(url) && StringUtils.isNotBlank(linkBody)) {
            httpClient.post(url, linkBody)
        } else {
            ConsoleUtils.log("Unable to add link with URL: $url and link payload: $linkBody")
        }
    }

    override fun addLabels(url: String, labelsBody: String) {
        if (UrlValidator.getInstance().isValid(url) && StringUtils.isNotBlank(labelsBody)) {
            httpClient.put(url, labelsBody)
        } else {
            ConsoleUtils.log("Unable to add labels with URL: $url and lables payload: $labelsBody")
        }
    }

    override fun addComment(url: String, commentBody: String) {
        if (UrlValidator.getInstance().isValid(url) && StringUtils.isNotBlank(commentBody)) {
            httpClient.post(url, commentBody)
        } else {
            ConsoleUtils.log("Unable to add comment with URL: $url and ")
        }
    }

    override fun createDefect(profile: String): String? {
        val url = context.getStringData("$INTEGRATION.$profile.createIssueUrl")
        val defectBody = context.replaceTokens(getTemplate(DEFECT_ENDPOINT))
        val requestBody = StringUtils.replaceAll(defectBody, "\n", "")
        val response = ConnectionFactory.getWebServiceClient(profile).post(url, requestBody)
        // todo: check status
        val responseJson = JSONObject(response.body)
        return if (responseJson.has("key")) {
            responseJson.getString("key")
        } else {
            ConsoleUtils.log("Unable to create defect.")
            null
        }
    }

    private fun jiraPostCommentUrl(ref: String, profile: String): String? {
        context.setData("$profile.issueId", ref)
        return context.getStringData("$INTEGRATION.$profile.commentUrl")
    }

    private fun jiraGetLabelsUrl(ref: String, profile: String): String? {
        context.setData("$profile.issueId", ref)
        return context.getStringData("$INTEGRATION.$profile.getLabelsUrl")

    }

    private fun jiraPutLabelsUrl(ref: String, profile: String): String? {
        context.setData("$profile.issueId", ref)
        return context.getStringData("$INTEGRATION.$profile.putLabelsUrl")
    }

    private fun appendToCurrentLabels(feature: String, profile: String, label: String): JSONArray {
        val getLabelsUrl = jiraGetLabelsUrl(feature, profile)
        val wsClient = ConnectionFactory.getWebServiceClient(profile)
        val response = wsClient.get(getLabelsUrl, "")
        var currentLabels = JSONArray()
        if (response != null && response.returnCode == 200) {
            currentLabels = JSONObject(response.body).getJSONObject("fields").getJSONArray("labels")
            if (!currentLabels.contains(label)) {
                currentLabels.put(label)
            }
        }
        return currentLabels
    }
}