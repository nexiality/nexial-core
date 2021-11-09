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
import org.json.JSONObject
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JsonUtils
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * [WebMailCommand] implementation of [temporary-mail.net](https://www.temporary-mail.net).
 *
 * @author Dhanapathi.Marepalli
 */
class TemporaryMail : WebMailer() {
    private val apiBase = "https://www.temporary-mail.net/api/v1/mailbox/"
    private val dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ssz"
    private val mailSuffix = "@temporary-mail.net"

    override fun search(context: ExecutionContext, profile: WebMailProfile, searchCriteria: String, duration: Long):
        Set<String?> {
        val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()

        val inboxUrl = apiBase + profile.inbox
        val response = wsClient.get(inboxUrl, "")
        if (response.returnCode < 200 || response.returnCode > 299) {
            ConsoleUtils.log("Unable to retrieve emails ${profile.inbox}: ${response.statusText}")
            return emptySet()
        }

        return JsonUtils.toJSONArray(response.body).map { mail ->
            if (mail !is JSONObject) "" else {
                val receivedDate = LocalDateTime.from(
                    DateTimeFormatter.ofPattern(dateTimeFormat).parse(mail.getString("date").trim())
                )
                val receivedMillis = receivedDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val minutesAgo = (System.currentTimeMillis() - receivedMillis) / 1000 / 60
                if (minutesAgo > duration) "" else {
                    val subject = mail.getString("subject")
                    if (!StringUtils.containsAnyIgnoreCase(subject, searchCriteria)) "" else {
                        val id = mail.getString("id")
                        val contentResponse = wsClient.get("$inboxUrl/$id", "")
                        if (contentResponse.returnCode < 200 || contentResponse.returnCode > 299) {
                            ConsoleUtils.log(
                                "Unable to retrieve email $id from ${profile.inbox}: " +
                                    contentResponse.statusText
                            )
                            ""
                        } else {
                            val contentJson = JsonUtils.toJSONObject(contentResponse.body)

                            val email = EmailDetails(
                                id = id,
                                subject = subject,
                                from = retrieveFrom(contentJson),
                                to = "${mail.getString("mailbox")}$mailSuffix",
                                time = receivedDate
                            )
                            email.content = cleanMailContent(contentJson.getJSONObject("body").getString("text"))
                            email.html = contentJson.getJSONObject("body").getString("html")
                            contentJson.getJSONArray("attachments")
                                .map { (it as JSONObject).getString("filename") }
                                .forEach { addAttachment(it, email) }
                            extractLinks(email)

                            context.setData(deriveEmailContentVar(profile, email.id), email)
                            email.id
                        }
                    }
                }
            }
        }.filter { StringUtils.isNotBlank(it) }.toSet()
    }

    private fun retrieveFrom(json: JSONObject) = if (!json.has("header")) "" else {
        val header = json.getJSONObject("header")
        if (header == null || !header.has("From")) "" else {
            val fromArray = header.getJSONArray("From")
            if (fromArray == null || fromArray.length() < 1 || fromArray.isNull(0)) ""
            else {
                val email = fromArray.getString(0)
                if (email.contains(">") && email.contains("<"))
                    StringUtils.trim(StringUtils.substringBetween(email, "<", ">"))
                else
                    email
            }
        }
    }

    override fun delete(context: ExecutionContext, profile: WebMailProfile, id: String): Boolean {
        val url = apiBase + profile.inbox + "/" + id
        val response = WebServiceClient(null).configureAsQuiet().disableContextConfiguration().delete(url, "")
        return if (response.returnCode < 200 || response.returnCode > 299) {
            ConsoleUtils.log("Unable to delete mail $id from ${profile.inbox}: ${response.statusText}")
            false
        } else true
    }
}