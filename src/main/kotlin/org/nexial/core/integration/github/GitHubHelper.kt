package org.nexial.core.integration.github

import org.nexial.core.integration.IntegrationHelper
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.AsyncWebServiceClient

class GitHubHelper(val context: ExecutionContext, private val httpClient: AsyncWebServiceClient) : IntegrationHelper() {
    override fun createDefect(profile: String): String {
        //todo implement
        return ""
    }

    override fun addLink(url: String, linkBody: String) {
        // todo
    }

    override fun addComment(url: String, commentBody: String) {
        // todo
    }

    override fun addLabels(url: String, labelsBody: String) {
        // todo
    }
}