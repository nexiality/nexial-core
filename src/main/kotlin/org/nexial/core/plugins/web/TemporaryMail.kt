package org.nexial.core.plugins.web

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.SCREENSHOT_EXT
import org.nexial.core.NexialConst.WebMail.*
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.Syspath
import java.io.File.separator
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.MINUTES
import java.util.*

/**
 * [WebMailCommand] implementation of [temporary-mail.net](https://www.temporary-mail.net).
 *
 * @author Dhanapathi.Marepalli
 */
class TemporaryMail : WebMailer() {
    private val url = "https://www.temporary-mail.net/"
    private val urlChange = url + "change"

    //XPATH Variables
    private val xpathInboxName = "//input[@id='user_mailbox']"
    private val xpathInboxSave = "//button[@id='user_set']"
    private val xpathSubject = "//div[@class='mail-info']/h4"
    private val xpathFrom = "//ul[@class='list-unstyled']/li[1]"
    private val xpathReceiveTime = "//*[@id='message-date']"
    private val xpathEmail = "//div[@class='mail-content']"
    private val xpathEmailHref = "$xpathEmail//*[@href]"
    private val xpathDelete = "//a/i[@class='fa fa-close']"
    private val xpathEmailLink = "//a[contains(string(.), '%s') and @class='title-subject']"
    private val xpathEmailContent = "/html"

    private val cst = "Asia/Shanghai"
    private val cstTimeZone = "CST"
    private val cstTimeFormat = "yyyy-MM-dd HH:mm:ss"
    private val pst = "America/Los_Angeles"
    private val pstTimeFormat = "MMM dd yyyy HH:mm:ss"

    override fun search(web: WebCommand, profile: WebMailProfile, searchCriteria: String,
                        duration: Long): Set<String?> {
        web.switchBrowser(TEMPORARYMAIL_BROWSERE, NEXIAL_WEBMAIL_BROWSER_CONFIG)

        val context = web.context
        val maxLoadTime = getMaxLoadTime(context)

        web.openAndWait(urlChange, maxLoadTime)
        web.type(xpathInboxName, profile.inbox)
        web.click(xpathInboxSave)
        val mailIds = web.getAttributeValues(String.format(xpathEmailLink, StringUtils.trim(searchCriteria)), "href")

        val size = ArrayUtils.getLength(mailIds)
        ConsoleUtils.log("Found $size email(s) with subject containing '${searchCriteria}'")

        return if (size < 1) emptySet() else extractEmailInfo(web, mailIds, profile, duration)
    }

    /**
     * Extract the [EmailDetails]'s matching the search criteria and duration. The value is set
     * into the var passed in.
     *
     * @param urls     urls matching the criteria.
     * @param var      the variable into which the emails which satisfy the criteria are to be added.
     * @param profile  the profile passed in.
     * @param duration the duration before which the email is created.
     * @return [StepResult.success] or [StepResult.fail] based on whether the emails
     * related information are extracted or not.
     */
    private fun extractEmailInfo(web: WebCommand, urls: Array<String>, profile: WebMailProfile, duration: Long):
            Set<String?> {
        ConsoleUtils.log("Email links are ${urls.contentToString()}")

        val matchingEmails = urls.map { it.trim() }.toSet()
        if (CollectionUtils.isEmpty(matchingEmails)) return HashSet()

        val context = web.context
        val maxLoadTime = getMaxLoadTime(context)
        val validEmails: MutableSet<String?> = HashSet()

        for (url in matchingEmails) {
            val id = StringUtils.substringAfter(url, this.url)
            web.openAndWait(url, maxLoadTime)
            ConsoleUtils.log("Mail with id $id is opened.")

            val receivedTimeText = StringUtils.trim(web.getElementText(xpathReceiveTime))
            ConsoleUtils.log("Received time shown in the mail is $receivedTimeText")

            val mailReceivedTime = if (receivedTimeText.endsWith(cstTimeZone)) {
                getMailReceivedDateTime(receivedTimeText, cst, cstTimeFormat)
            } else {
                getMailReceivedDateTime(receivedTimeText, pst, pstTimeFormat)
            }

            val now = ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault())
            val minutes = mailReceivedTime.until(now, MINUTES)
            if (minutes > duration) {
                ConsoleUtils.log("Email with id $id is skipped as it is not with in the duration.")
                continue
            }
            ConsoleUtils.log("Mail received time is ${mailReceivedTime.toLocalDate()}.")

            val subject = StringUtils.trim(web.getElementText(xpathSubject))
            ConsoleUtils.log("Subject is $subject")

            val from = StringUtils.trim(web.getElementText(xpathFrom))
            ConsoleUtils.log("From is $from")

            val to = "${profile.inbox}@${profile.domain}"
            ConsoleUtils.log("To is $to")

            val htmlContent = web.driver.pageSource
            ConsoleUtils.log("HTML content is $htmlContent")

            val content = web.getElementText(xpathEmail)
            ConsoleUtils.log("Content is $content")

            val screenCaptureFile = "${Syspath().screenshot("fullpath")}$separator" +
                                    "${StringUtils.substringAfterLast(url, "/")}$SCREENSHOT_EXT"
            web.screenshot(screenCaptureFile, xpathEmailContent, "true")

            val links = web.getAttributeValues(xpathEmailHref, "href").map { it.trim() }.toSet()
            ConsoleUtils.log("Email links are $links")

            val emailDetails = EmailDetails(
                    id = id,
                    subject = subject,
                    to = to,
                    from = from,
                    time = mailReceivedTime.toLocalDateTime(),
                    content = content,
                    links = links,
                    html = htmlContent
            )
            context.setData(deriveEmailContentVar(profile, id), emailDetails)
            validEmails.add(id)
        }

        return validEmails
    }

    /**
     * Converts the Email Received time in terms of the system specific time zone.
     *
     * @param dateTime the received time information displayed on the web page.
     * @param zone             the time zone mentioned in the page.
     * @param dateTimePattern  the pattern mentioning the date and time as per the email.
     * @return [ZonedDateTime] value of the mail received time as per the system time.
     */
    private fun getMailReceivedDateTime(dateTime: String, zone: String, dateTimePattern: String): ZonedDateTime {
        val timeStampComponents = if (zone == pst)
            ArrayUtils.subarray(dateTime.split("\\s".toRegex()).toTypedArray(), 1, 5)
        else
            ArrayUtils.subarray(dateTime.split("\\s".toRegex()).toTypedArray(), 0, 2)
        val timeStamp = StringUtils.joinWith(" ", *timeStampComponents as Array<Any>)

        val ldt = LocalDateTime.parse(timeStamp, DateTimeFormatter.ofPattern(dateTimePattern))
        val zonedDateTime = ZonedDateTime.of(ldt, ZoneId.of(zone))
        return zonedDateTime.withZoneSameInstant(ZoneId.systemDefault())
    }

    override fun delete(web: WebCommand, profile: WebMailProfile, id: String): Boolean {
        web.switchBrowser(TEMPORARYMAIL_BROWSERE, NEXIAL_WEBMAIL_BROWSER_CONFIG)

        val url = StringUtils.join(url, id)
        val maxLoadTime = getMaxLoadTime(web.context)
        web.openAndWait(url, maxLoadTime)
        if (!web.isElementPresent(xpathDelete)) {
            ConsoleUtils.log("There is no email with Id $id")
            return false
        }

        web.clickAndWait(xpathDelete, maxLoadTime)
        web.context.removeData(deriveEmailContentVar(profile, id))
        return true
    }
}