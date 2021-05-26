package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import org.jsoup.Jsoup
import org.nexial.core.NexialConst.WebMail.BROWSER_CONFIG
import org.nexial.core.NexialConst.WebMail.MAILINATOR_BROWSER_PROFILE
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JSONPath
import org.nexial.core.utils.JsonUtils
import java.net.URI
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

    // private val timeFormat = "EEE MMM dd yyyy HH:mm:ss zZ"
    private val locators = Locators(url)

    override fun search(web: WebCommand, profile: WebMailProfile, searchCriteria: String, duration: Long): Set<String> {
        val matchingEmails = mutableSetOf<String>()

        val client = MailinatorClient(profile.inbox, searchCriteria, duration)
        if (client.connectBlocking()) {
            while (client.isOpen) Thread.sleep(250)
            if (client.disconnectedNormally()) matchingEmails.addAll(client.messageIds)
        }

        // fallback to headless browser
        if (matchingEmails.isEmpty()) matchingEmails.addAll(searchViaWebCommand(web, profile, searchCriteria))

        // if neither WS or headless browser works, then we give up
        if (matchingEmails.isEmpty()) return emptySet()

        val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
        return matchingEmails.map { emailId ->
            val id = emailId.substringAfter("row_")
            val url = String.format(urlGetMail, id)
            val response = wsClient.get(url, "")
            if (response.returnCode < 200 || response.returnCode > 299 || StringUtils.isBlank(response.body)) {
                ConsoleUtils.log("$logPrefix Unexpected error when fetching mail $id: " +
                                 "return code=${response.returnCode}, body=${response.body}")
                ""
            } else {
                val jsonObject = JsonUtils.toJSONObject(response.body)
                val minutesAgo = NumberUtils.toLong(JSONPath.find(jsonObject, "data.seconds_ago")) / 60
                if (minutesAgo > duration)
                    ""
                else {
                    val email = toEmailDetails(jsonObject)
                    web.context.setData(deriveEmailContentVar(profile, email.id), email)
                    email.id
                }
            }
        }.filter { StringUtils.isNotEmpty(it) }.toSet()
    }

    private fun searchViaWebCommand(web: WebCommand,
                                    profile: WebMailProfile,
                                    searchCriteria: String): List<String> {
        web.switchBrowser(MAILINATOR_BROWSER_PROFILE, BROWSER_CONFIG)
        openEmailListingPage(web, profile)

        // find index of "Subject"
        val indexSubject =
            web.collectTextList(locators.listingHeaders).indexOfFirst { it.contains(labelSubject, true) }
        val search = web.locatorHelper.normalizeXpathText(searchCriteria.trim())
        val emailRowLocator = String.format(locators.matchedEmail, indexSubject + 2, search)
        val emailIds = web.getAttributeValues(emailRowLocator, "id").map { it.trim() }.toList()
        web.closeAll()
        web.switchBrowser("", "")
        return emailIds
    }

    private fun toEmailDetails(jsonObject: JSONObject?): EmailDetails {
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
            val contentBody = parts.getJSONObject(0).getString("body")

            val headers = parts.getJSONObject(0).getJSONObject("headers")
            val isHtmlContent = StringUtils.contains(headers?.getString("content-type") ?: "", "html")

            if (isHtmlContent) {
                email.content = StringUtils.trim(Jsoup.parse(contentBody).wholeText())
                email.html = contentBody
            } else {
                email.content = contentBody
                email.html = email.content
            }
        } else {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val headers = part.getJSONObject("headers") ?: continue
                if (headers.getString("content-type").contains("html"))
                    email.html = part.getString("body")
                if (headers.getString("content-type").contains("plain"))
                    email.content = StringUtils.trim(part.getString("body"))
            }
        }

        val clickableLinks = JSONPath.find(jsonObject, "data.clickablelinks")
        if (clickableLinks != null) {
            val links = JsonUtils.toJSONArray(clickableLinks)
            if (links != null && links.length() > 0) email.links = links.map { it.toString() }.toSet()
        }

        return email
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
            ConsoleUtils.error("$logPrefix There is no email with this id.")
            return false
        }

        val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
        wsClient.get(String.format(urlDeleteMail, id), "")
        return true
    }

}

private class MailinatorClient(val inbox: String, val subjectSearch: String, val noOlderThanMinutes: Long) :
    WebSocketClient(toWSUri(inbox), Draft_6455(PerMessageDeflateExtension()), toWsRequestHeaders(inbox)) {

    private val reasonOK = "OK"
    private val reasonTimeOut = "TIME_OUT"
    private val reasonUnknown = "UNKNOWN"

    private var timeout = initTimeout()
    private var wsAccept: String? = null
    private var disconnectReason: String? = null
    val messageIds = mutableSetOf<String>()

    fun disconnectedNormally() = StringUtils.equals(disconnectReason, reasonOK)

    fun disconnectedDueToTimeout() = StringUtils.equals(disconnectReason, reasonTimeOut)

    override fun onOpen(handshake: ServerHandshake?) {
        wsAccept = handshake?.getFieldValue("Sec-WebSocket-Accept")
        timeout = initTimeout()
    }

    override fun onMessage(message: String?) {
        if (isTimedOut()) {
            disconnectReason = reasonTimeOut
            close()
        }

        if (StringUtils.isBlank(message)) return

        val json = JsonUtils.toJSONObject(message)

        // not json
        if (json.has("errorCode")) {
            ConsoleUtils.log("$logPrefix ignoring non-conforming incoming message: $message")
            return
        }

        if (!json.has("msgs")) return

        val messages = json.getJSONArray("msgs")
        if (messages == null || messages.length() < 1) return

        messageIds.addAll(
            messages.asSequence()
                .filterIsInstance<JSONObject>()
                .filter { it.has("subject") && StringUtils.contains(it.getString("subject"), subjectSearch) }
                .filter { it.has("seconds_ago") && it.getInt("seconds_ago") < (noOlderThanMinutes * 60) }
                .map { it.getString("id") }.toList())
        disconnectReason = reasonOK
        close()
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        when {
            code == 1000 || StringUtils.isEmpty(reason) -> {
                disconnectReason = reasonOK
            }
            code == 1006                                -> {
                disconnectReason = reason?.substringBefore(".") ?: reasonUnknown
                ConsoleUtils.log("$logPrefix [$code] $disconnectReason")
            }
            else                                        -> {
                disconnectReason = reason
                ConsoleUtils.log("$logPrefix [$code] $reason")
            }
        }

        close()
    }

    override fun onError(e: Exception?) {
        disconnectReason = e?.message ?: reasonUnknown
        ConsoleUtils.log("$logPrefix ERROR: $e")
        close()
    }

    private fun initTimeout() = System.currentTimeMillis() + (1000 * 20)

    private fun isTimedOut() = System.currentTimeMillis() - timeout > 0

    companion object {
        fun toWSUri(inbox: String) = URI("wss://www.mailinator.com/ws/fetchpublic?to=$inbox")

        fun toWsRequestHeaders(inbox: String) = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Encoding" to "gzip, deflate, br",
            "Accept-Language" to "en-US,en;q=0.9",
            "Cache-Control" to "no-cache",
            "Connection" to "Upgrade",
            "Cookie" to "SERVERID=s2; last_public_inbox=$inbox",
            "Host" to "www.mailinator.com",
            "Origin" to "https://www.mailinator.com",
            "Pragma" to "no-cache",
            "Sec-WebSocket-Extensions" to "permessage-deflate; client_max_window_bits",
            "Sec-WebSocket-Version" to "13",
            "Upgrade" to "websocket",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
        )
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

private const val logPrefix = "[mailinator]"
