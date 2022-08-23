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

package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import org.jsoup.Jsoup
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.Web.OPT_DELAY_BROWSER
import org.nexial.core.NexialConst.WebMail.BROWSER_CONFIG
import org.nexial.core.NexialConst.WebMail.MAILINATOR_BROWSER_PROFILE
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.web.LocatorHelper.Companion.normalizeXpathText
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
	private val labelSubject = "Subject"

	// private val timeFormat = "EEE MMM dd yyyy HH:mm:ss zZ"
	private val locators = Locators(url)

	override fun search(context: ExecutionContext, profile: WebMailProfile, searchCriteria: String, duration: Long):
		Set<String> {

		val matchingEmails = mutableSetOf<String>()

		val client = MailinatorClient(profile.inbox, searchCriteria, duration)
		if (client.connectBlocking()) {
			while (client.isOpen) Thread.sleep(250)
			if (client.disconnectedNormally()) matchingEmails.addAll(client.messageIds)
		}

		// fallback to headless browser
		if (matchingEmails.isEmpty()) matchingEmails.addAll(searchViaWebCommand(context, profile, searchCriteria))

		// if neither WS nor headless browser works, then we give up
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
					context.setData(deriveEmailContentVar(profile, email.id), email)
					email.id
				}
			}
		}.filter { StringUtils.isNotEmpty(it) }.toSet()
	}

	private fun searchViaWebCommand(context: ExecutionContext, profile: WebMailProfile, searchCriteria: String):
		List<String> {

		val web = if (!context.isPluginLoaded("web")) {
			val currentDelayBrowser = context.getBooleanData(OPT_DELAY_BROWSER)
			context.setData(OPT_DELAY_BROWSER, true)
			val newWeb = context.findPlugin("web") as WebCommand
			context.setData(OPT_DELAY_BROWSER, currentDelayBrowser)
			newWeb
		} else
			context.findPlugin("web") as WebCommand

		val currentProfile = web.profile
		web.switchBrowser(MAILINATOR_BROWSER_PROFILE, BROWSER_CONFIG)
		openEmailListingPage(web, profile)

		// find index of "Subject"
		val indexSubject =
			web.collectTextList(locators.listingHeaders).indexOfFirst { it.contains(labelSubject, true) }
		val search = normalizeXpathText(searchCriteria.trim())
		val emailRowLocator = String.format(locators.matchedEmail, indexSubject + 2, search)
		val emailIds = web.getAttributeValues(emailRowLocator, "id").map { it.trim() }.toList()
		// web.close()
		web.switchBrowser(currentProfile, "")
		return emailIds
	}

	internal fun toEmailDetails(jsonObject: JSONObject): EmailDetails {
		val email = EmailDetails(
			id = JSONPath.find(jsonObject, "data.id") ?: throw IllegalArgumentException("email id not found"),
			subject = JSONPath.find(jsonObject, "data.subject")
			          ?: throw IllegalArgumentException("email subject not found"),
			to = JSONPath.find(jsonObject, "data.headers.to")
			     ?: throw IllegalArgumentException("email recipient not found"),
			from = JSONPath.find(jsonObject, "data.from") ?: throw IllegalArgumentException("email sender not found"),
			time = Instant.ofEpochMilli(NumberUtils.toLong(JSONPath.find(jsonObject, "data.time")))
				.atZone(ZoneId.systemDefault())
				.toLocalDateTime())

		val parts = JsonUtils.toJSONArray(JSONPath.find(jsonObject, "data.parts"))
		val body = mutableMapOf<String, String>()
		parts.forEach() { part ->
			val jsonPart = part as JSONObject
			if (jsonPart.has("body") && jsonPart.has("headers")) {
				val header = jsonPart.getJSONObject("headers")
				if (header.has("content-type")) {
					val contentType = header.getString("content-type")
					when {
						contentType.contains("text/html")  -> body["html"] = jsonPart.getString("body")
						contentType.contains("text/plain") -> body["plain"] = jsonPart.getString("body")
					}
				}
			}
		}

		if (parts.length() == 1) {
			val part = parts.getJSONObject(0)
			val contentBody = part.getString("body")
			val headers = part.getJSONObject("headers")
			val isHtmlContent = StringUtils.contains(headers?.getString("content-type") ?: "", "html")

			if (isHtmlContent) {
				email.content = cleanMailContent(Jsoup.parse(contentBody).wholeText())
				email.html = contentBody
			} else {
				email.content = cleanMailContent(contentBody)
				email.html = email.content
			}
		} else {
			for (i in 0 until parts.length()) {
				val part = parts.getJSONObject(i)
				val headers = part.optJSONObject("headers") ?: continue
				val contentBody = part.getString("body")
				if (headers.has("content-type")) {
					if (headers.getString("content-type").contains("html")) {
						email.content = cleanMailContent(Jsoup.parse(contentBody).wholeText())
						email.html = contentBody
					}
					if (headers.getString("content-type").contains("plain"))
						email.content = cleanMailContent(contentBody)
				}
			}
		}

		email.links = harvestLinks(body["html"] ?: "")

		if (StringUtils.isNotBlank(email.html)) {
			val labels = extractLinkLabels(email.html!!)
			var bodyPlain = body["plain"] ?: ""
			for (label in labels) {
				val result = harvestUrlByLabel(bodyPlain, label)
				if (result.isNotEmpty()) {
					val url = "http" + StringUtils.substringBefore(StringUtils.substringAfter(result, "<http"), ">")
					if (!email.link.contains(label)) email.link[label] = url
					bodyPlain = StringUtils.substringAfter(bodyPlain, result)
				}
			}
		}

		return email
	}

	internal fun extractLinkLabels(html: String): List<String> =
		Jsoup.parse(removeConditionalComments(html))
			.select("a[href]")
			.mapNotNull { anchor ->
				val href = anchor.attr("href")
				if (StringUtils.isBlank(href))
					null
				else
					anchor.text().trim()
			}
			.filter { label -> StringUtils.isNotBlank(label) }
			.toList()

	fun harvestLinks(messageBody: String): List<String> {
		return Jsoup.parse(removeConditionalComments(messageBody))
			.select("a[href]")
			.mapNotNull { anchor ->
				val href = anchor.attr("href")
				if (StringUtils.isBlank(href))
					null
				else
					href
			}
			.filter { link -> StringUtils.isNotBlank(link) }
			.toList()
	}

	private fun harvestUrlByLabel(messageBody: String, label: String): String {
		val labelNormalized = label
			.replace("\\", "\\\\")
			.replace("/", "\\/")
			.replace("(", "\\(")
			.replace(")", "\\)")
			.replace("[", "\\[")
			.replace("]", "\\]")
		return RegexUtils.firstMatches(messageBody, "$labelNormalized\\s+<(http|https):.*?>") ?: ""
	}

	private fun openEmailListingPage(web: WebCommand, profile: WebMailProfile) {
		val maxLoadTime = getMaxLoadTime(web.context)
		web.open(String.format(url, profile.inbox))
		web.waitUntilVisible(String.format(locators.emailListing, profile.inbox), maxLoadTime)
		web.waitForCondition(NumberUtils.toLong(maxLoadTime)) { web.isBrowserLoadComplete }
	}

	override fun delete(context: ExecutionContext, profile: WebMailProfile, id: String): Boolean {
		val email = context.getObjectData(deriveEmailContentVar(profile, id), EmailDetails::class.java)
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
				.filter {
					it.has("subject") &&
					(StringUtils.isEmpty(subjectSearch) || StringUtils.contains(it.getString("subject"), subjectSearch))
				}
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
