package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.core.NexialConst.WebMail.BROWSER_CONFIG
import org.nexial.core.NexialConst.WebMail.MAILINATOR_BROWSER_PROFILE
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JSONPath
import org.nexial.core.utils.JsonUtils
import java.time.Instant
import java.time.ZoneId


/**
 * [WebMailCommand] implementation of [Mailinator](https://www.mailinator.com).
 *
 * @author Dhanapathi Marepalli
 */
class Mailinator : WebMailer() {
    private val url = "https://www.mailinator.com/v4/public/inboxes.jsp?to=%s"
    private val urlGetMail = "https://www.mailinator.com/fetch_public?msgid=%s"
    private val urlDeleteMail = "https://www.mailinator.com/delete_public?msgid=%s"
    private var isV4 = url.contains("/v4/")
    private val labelSubject = "Subject"

    private val timeFormat = "EEE MMM dd yyyy HH:mm:ss zZ"
    private val locators = Locators(url)

    override fun search(web: WebCommand, profile: WebMailProfile, searchCriteria: String, duration: Long): Set<String> {
        web.switchBrowser(MAILINATOR_BROWSER_PROFILE, BROWSER_CONFIG)

        openEmailListingPage(web, profile)

        // find index of "Subject"
        val indexSubject = web.collectTextList(locators.listingHeaders).indexOfFirst { it.contains(labelSubject, true) }
        val search = web.locatorHelper.normalizeXpathText(searchCriteria.trim())
        val emailRowLocator = String.format(locators.matchedEmail, indexSubject + 2, search)

        val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()

        val matchingEmails = web.getAttributeValues(emailRowLocator, "id").map { it.trim() }.toSet()
        web.closeAll()
        web.switchBrowser("", "")

        if (matchingEmails.isEmpty()) return emptySet()

        return matchingEmails.map { emailId ->
            val id = emailId.substringAfter("row_")
            val url = String.format(urlGetMail, id)
            val response = wsClient.get(url, "")
            if (response.returnCode < 200 || response.returnCode > 299 || StringUtils.isBlank(response.body)) {
                ConsoleUtils.log("Unexpected error when fetching mail $id: " +
                                 "return code=${response.returnCode}, body=${response.body}")
                ""
            } else {
                val jsonObject = JsonUtils.toJSONObject(response.body)

                val minutesAgo = NumberUtils.toLong(JSONPath.find(jsonObject, "data.seconds_ago")) / 60
                if (minutesAgo > duration)
                    ""
                else {
                    val email = EmailDetails(
                            id = JSONPath.find(jsonObject, "data.id"),
                            subject = JSONPath.find(jsonObject, "data.subject"),
                            to = JSONPath.find(jsonObject, "data.headers.to"),
                            from = JSONPath.find(jsonObject, "data.from"),
                            time = Instant.ofEpochMilli(NumberUtils.toLong(JSONPath.find(jsonObject, "data.time")))
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                    )

                    val parts = JsonUtils.toJSONArray(JSONPath.find(jsonObject, "data.parts"))
                    if (parts.length() == 1) {
                        email.content = parts.getJSONObject(0).getString("body")
                        email.html = email.content
                    } else {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val headers = part.getJSONObject("headers") ?: continue
                            if (headers.getString("content-type").contains("html"))
                                email.html = part.getString("body")
                            if (headers.getString("content-type").contains("plain"))
                                email.content = part.getString("body")
                        }
                    }
                    web.context.setData(deriveEmailContentVar(profile, email.id), email)
                    email.id
                }
            }
        }.filter { StringUtils.isNotEmpty(it) }.toSet()
    }

    private fun openEmailListingPage(web: WebCommand, profile: WebMailProfile) {
        val maxLoadTime = getMaxLoadTime(web.context)
        web.open(String.format(url, profile.inbox))
        web.waitUntilVisible(String.format(locators.emailListing, profile.inbox), maxLoadTime)
        web.waitForCondition(NumberUtils.toLong(maxLoadTime)) { web.isBrowserLoadComplete }
    }

    override fun delete(web: WebCommand, profile: WebMailProfile, id: String): Boolean {
        val email = web.context.getObjectData(deriveEmailContentVar(profile, id), EmailDetails::class.java)
        if (email == null) {
            ConsoleUtils.error("There is no email with this id.")
            return false
        }

        val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
        wsClient.get(String.format(urlDeleteMail, id), "")
        return true
    }

}

private class Locators(targetUrl: String) {
    var isV4 = targetUrl.contains("/v4/")

    var emailListing: String = "css=table tr[id^='row_%s']"
    var listingHeaders: String
    var matchedEmail: String
    var emailLink: String = "//tr[@id='%s']/td[%s]"
    var emailHeader: String
    var nonEmptyReceivedOn: String
    var contentFrame: String
    var content: String = "//body"
    var links: String = "//*[@href]"
    var deleteCheckbox: String = "css=input[type='checkbox'][id='%s']"
    var deleteButton: String

    init {
        if (isV4) {
            listingHeaders = "css=.wrapper-table > table td"
            matchedEmail = "//*[@id='inbox_pane']//tr[./td[%s][contains(string(.),%s)]]"
            emailHeader = "css=#email_pane .sender-info > div"
            nonEmptyReceivedOn = "//*[@id='email_pane']//div[./div[contains(normalize-space(string(.)),'Received')]]" +
                                 "/div[contains(@class,'from ') and normalize-space(string(.))!='']"
            contentFrame = "css=#html_msg_body"
            deleteButton = "css=button[aria-label='Delete Button']"
        } else {
            listingHeaders = "css=table thead th"
            matchedEmail = "//*[@id='inboxpane']//tr[./td[%s][contains(string(.),%s)]]"
            emailHeader = "css=#msgpane .x_panel tr"
            nonEmptyReceivedOn = "//*[@id='msgpane']//tr[./td[contains(string(.),'Received')]]" +
                                 "/td[2][normalize-space(string(.))!='']"
            contentFrame = "css=#msg_body"
            deleteButton = "css=button[id='trash_but']"
        }
    }
}