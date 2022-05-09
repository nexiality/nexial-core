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
import org.nexial.core.NexialConst.BrowserType
import org.nexial.core.NexialConst.Web.BROWSER
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JsonUtils
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.FluentWait
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


/**
 * [WebMailCommand] implementation of [temporary-mail.net](https://www.temporary-mail.net).
 *
 * @author Dhanapathi.Marepalli
 */
class TemporaryMail : WebMailer() {
    private val waitBetweenFetchMs = 5000L
    private val maxFetchTimeMs = (1 * 60 * 1000)
    private val waitBeforeDriverCloseMs = 2500L
    private val maxElementWaitMs = 15 * 1000L

    private val website = "temporary-mail.net"
    private val apiBase = "https://www.$website/api/v1/mailbox/"
    private val startUrl = "https://$website/change"
    private val dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ssz"
    private val mailSuffix = "@$website"

    var browser: Browser? = null

    override fun search(context: ExecutionContext, profile: WebMailProfile, searchCriteria: String, duration: Long):
        Set<String?> {
        if (duration < 1) {
            ConsoleUtils.log("Scan for incoming email from ${profile.inbox}$mailSuffix...")
            val driver: WebDriver = initDriver(context)
            initInbox(driver, profile)
            Thread.sleep(waitBeforeDriverCloseMs)
            driver.close()
            return emptySet()
        }

        val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
        val timesUp = (Date().time + maxFetchTimeMs)
        while ((Date().time < timesUp)) {
            val emails = fetchEmails(wsClient, profile, searchCriteria, duration)
            if (emails.isNotEmpty()) {
                return emails.map { email ->
                    context.setData(deriveEmailContentVar(profile, email.id), email)
                    email.id
                }.toSet()
            }
            // if (duration < 1) break
            Thread.sleep(waitBetweenFetchMs)
        }

        return emptySet()
    }

    private fun fetchEmails(wsClient: WebServiceClient,
                            profile: WebMailProfile,
                            searchCriteria: String,
                            duration: Long): Set<EmailDetails> {
        val inboxUrl = apiBase + profile.inbox
        val response = wsClient.get(inboxUrl, "")
        if (response.returnCode < 200 || response.returnCode > 299) {
            ConsoleUtils.log("Unable to retrieve emails ${profile.inbox}: ${response.statusText}")
            return emptySet()
        }

        if (StringUtils.isBlank(response.body)) {
            ConsoleUtils.log("Unable to retrieve emails ${profile.inbox}: empty response body")
            return emptySet()
        }

        return JsonUtils.toJSONArray(response.body).filterIsInstance<JSONObject>().mapNotNull { mail ->
            val receivedDate = LocalDateTime.from(
                DateTimeFormatter.ofPattern(dateTimeFormat).parse(mail.getString("date").trim())
            )
            val receivedMillis = receivedDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val minutesAgo = (System.currentTimeMillis() - receivedMillis) / 1000 / 60
            if (minutesAgo > duration)
                null
            else {
                val subject = mail.getString("subject")
                if (!StringUtils.containsAnyIgnoreCase(subject, searchCriteria))
                    null
                else {
                    val id = mail.getString("id")
                    val contentResponse = wsClient.get("$inboxUrl/$id", "")
                    if (contentResponse.returnCode < 200 || contentResponse.returnCode > 299) {
                        ConsoleUtils.log("Unable to retrieve email $id from ${profile.inbox}: " +
                                         contentResponse.statusText)
                        null
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
                        email
                    }
                }
            }
        }.toSet()
    }

    private fun initInbox(driver: WebDriver, profile: WebMailProfile) {
        driver.get(startUrl)
        Thread.sleep(750)

        val waiter = newWaiter(driver)

        val bodyElem = driver.findElement<WebElement>(By.cssSelector("body"))
                       ?: throw WebDriverException("Unable to navigate to $startUrl")
        if (StringUtils.containsIgnoreCase(bodyElem.text, "Checking your browser")) Thread.sleep(6500)

        val inboxSelector = By.cssSelector("#user_mailbox")
        val submitSelector = By.cssSelector("#user_set")
        waiter.until { driver.findElement<WebElement>(inboxSelector) }
        waiter.until { driver.findElement<WebElement>(submitSelector) }

        val inboxElem = driver.findElement<WebElement>(inboxSelector)
        Actions(driver).moveToElement(inboxElem).click(inboxElem).sendKeys(inboxElem, profile.inbox).perform()

        val submitElem = driver.findElement<WebElement>(submitSelector)
        Actions(driver).click(submitElem).perform()

        Thread.sleep(2750)

        val messageSelector = By.cssSelector("#message-list")
        waiter.until { driver.findElement<WebElement>(messageSelector) }
    }

    private fun newWaiter(chrome: WebDriver): FluentWait<WebDriver> =
        FluentWait<WebDriver>(chrome)
            .withTimeout(Duration.ofMillis(maxElementWaitMs))
            .pollingEvery(Duration.ofMillis(25))
            .ignoring(WebDriverException::class.java)

    private fun initDriver(context: ExecutionContext): WebDriver {
        val existingBrowserType = context.browserType

        val targetBrowserType = BrowserType.chromeheadless.toString()
        System.setProperty(BROWSER, targetBrowserType)
        context.setData(BROWSER, targetBrowserType)

        browser!!.setContext(context)
        browser!!.init()
        val driver = browser!!.ensureWebDriverReady()

        // switch back
        System.setProperty(BROWSER, existingBrowserType)
        context.setData(BROWSER, existingBrowserType)

        return driver
    }

    private fun retrieveFrom(json: JSONObject) =
        if (!json.has("header"))
            ""
        else {
            val header = json.getJSONObject("header")
            if (header == null || !header.has("From"))
                ""
            else {
                val fromArray = header.getJSONArray("From")
                if (fromArray == null || fromArray.length() < 1 || fromArray.isNull(0))
                    ""
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
        } else
            true
    }
}