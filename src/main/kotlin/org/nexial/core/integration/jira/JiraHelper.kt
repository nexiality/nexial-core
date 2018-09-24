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
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.DATE_FORMAT_NOW
import org.nexial.core.integration.*
import org.nexial.core.integration.connection.ConnectionFactory
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.AsyncWebServiceClient
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JsonUtils
import org.nexial.core.variable.Sysdate
import java.io.File
import java.io.FileInputStream
import java.util.*


class JiraHelper(val context: ExecutionContext, private val httpClient: AsyncWebServiceClient) : IntegrationHelper() {

    val lineSeparator = System.getProperty("line.separator")!!
    private val integrationMeta = IntegrationMeta()

    fun process(profile: String, executionOutput: ExecutionOutput): IntegrationMeta {
        return performActions(profile, executionOutput)
    }

    private fun performActions(profile: String, executionOutput: ExecutionOutput): IntegrationMeta {
        integrationMeta.processedTime = Sysdate().now(DATE_FORMAT_NOW)
        val actions = getActions(context, profile)
        actions.forEach { action ->
            when (action) {
                "comment-summary" -> {
                    executionOutput.iterations.forEach { iteration ->
                        for (scenario in iteration.scenarios) {
                            processComment(profile, scenario)
                        }
                    }
                }
                "label-automation" -> {
                    executionOutput.iterations.forEach { iteration ->
                        for (scenario in iteration.scenarios) {
                            processLabel(profile, scenario, AUTOMATION_COMPLETE)
                        }
                    }
                }
                "defect" -> {
                    val defectData = mutableListOf<Pair<String, String>>()
                    executionOutput.iterations.forEach { iteration ->
                        for (scenario in iteration.scenarios) {
                            defectData.addAll(processDefect(profile, scenario))
                        }
                        if (CollectionUtils.isNotEmpty(defectData)) {
                            //todo: design a centralized defect history record
                            updateJiraDefectHistory(defectData)
                        }
                    }
                }
            }
        }
        return integrationMeta
    }

    private fun processComment(profile: String, scenario: ScenarioOutput) {

        val features = mutableSetOf<String>()
        scenario.projects.forEach { projectInfo ->
            if (profile == projectInfo.profile) {
                features.addAll(projectInfo.features)
            }
        }
        context.setData(JIRA_COMMENT_BODY, TemplateEngine.setJiraCommentSummary(scenario))

        val commentBody = StringUtils.replace(context.replaceTokens(TemplateEngine.getTemplate(profile, COMMENT_ENDPOINT)),
                lineSeparator, "")
        features.forEach { feature ->
            val url = jiraPostCommentUrl(feature, profile)
            addComment(url!!, commentBody)
        }
    }

    private fun processDefect(profile: String, scenarioOutput: ScenarioOutput): MutableList<Pair<String, String>> {
        val defectData = mutableListOf<Pair<String, String>>()
        val defectList = mutableListOf<String>()
        for (projectInfo in scenarioOutput.projects) {
            // assume that one project for one profile
            if (profile != projectInfo.profile) {
                continue
            }
            val newDefects = TemplateEngine.setJiraDefect(projectInfo, scenarioOutput)
            val defectHistory = Properties()
            val file = File(jiraDefectMeta)
            if (file.exists()) {
                defectHistory.load(FileInputStream(file))
            }
            // check if defects are repeated or contains in history
            // create a defect only when this condition is not met
            if (CollectionUtils.isEqualCollection(defectList, newDefects)
                    || CollectionUtils.containsAll(defectHistory.keys, newDefects)) continue
            val defectKey = createDefect(projectInfo.server!!)
            CollectionUtils.addAll(defectList, newDefects)
            integrationMeta.defects.add(defectKey!!)

            defectList.forEach { hash ->
                val pair = Pair(hash, defectKey)
                defectData.add(pair)
            }

            if (StringUtils.isNotBlank(defectKey)) {
                // add defect labels
                addLabels(defectKey, projectInfo.server!!, JSONArray("[\"$DEFECT_LABEL\"]"))

                // add links to features and test ref
                val inwardLinks = mutableSetOf<String>()
                inwardLinks.addAll(projectInfo.features)
                inwardLinks.addAll(projectInfo.testIds)
                inwardLinks.forEach { link -> addLink(projectInfo.server!!, link, defectKey) }
            }
        }
        return defectData

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
        integrationMeta.labels.addAll(JsonUtils.toList(labels) as MutableList<String>)
        val labelsBody = StringUtils.replace(context.replaceTokens(TemplateEngine.getTemplate(profile, LABEL_ENDPOINT)),
                lineSeparator, "")
        addLabels(putLabelsUrl!!, labelsBody)

    }

    private fun addLink(profile: String, inwardIssue: String, outwardIssue: String) {
        context.setData(JIRA_INWARD_ISSUE, inwardIssue)
        context.setData(JIRA_OUTWARD_ISSUE, outwardIssue)
        val linkUrl = context.getStringData("$INTEGRATION.$profile.$JIRA_ISSUE_LINK_URL")
        var issueLinkBody = context.replaceTokens(TemplateEngine.getTemplate(profile, LINK_ENDPOINT))
        issueLinkBody = context.replaceTokens(issueLinkBody)
        issueLinkBody = RegexUtils.replace(issueLinkBody, lineSeparator, "")
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
            ConsoleUtils.log("Unable to add labels with URL: $url and labels payload: $labelsBody")
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
        val defectBody = context.replaceTokens(TemplateEngine.getTemplate(profile, DEFECT_ENDPOINT))
        val requestBody = RegexUtils.replace(defectBody, lineSeparator, "")
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