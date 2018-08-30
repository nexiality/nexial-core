package org.nexial.core.integration.slack

import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.nexial.core.integration.*
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.AsyncWebServiceClient
import org.nexial.core.utils.ConsoleUtils

class SlackHelper(val context: ExecutionContext, private val httpClient: AsyncWebServiceClient) : IntegrationHelper() {

    val templateEngine = TemplateEngine(context)
    fun process(profile: String, scenarioOutput: ScenarioOutput) {
        val actions = getActions(context, profile)
        actions.forEach { action ->
            when (action) {
                "comment" -> {
                    processComment(profile, scenarioOutput)
                }
            }
        }
    }

    private fun processComment(profile: String, scenario: ScenarioOutput) {

        // todo: create template to post results
        // context.setData(SLACK_COMMENT_BODY, parseResultToMdFormat(scenario))
        val commentBody = StringUtils.replace(context.replaceTokens(getTemplate(COMMENT_ENDPOINT)), "\n", "")
        val url = context.getStringData("$INTEGRATION.$profile.$SLACK_CHAT_URL")
        addComment(url!!, commentBody)

    }

    override fun createDefect(profile: String): String {
        // TODO
        return ""
    }

    override fun addLink(url: String, linkBody: String) {
        // todo
    }

    override fun addComment(url: String, commentBody: String) {
        if (UrlValidator.getInstance().isValid(url) && StringUtils.isNotBlank(commentBody)) {
            httpClient.post(url, commentBody)
        } else {
            ConsoleUtils.log("Unable to add comment with URL: $url and ")
        }
    }

    override fun addLabels(url: String, labelsBody: String) {
        //todo
    }
}