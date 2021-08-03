package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.EMPTY
import org.nexial.core.NexialConst.NAMESPACE
import org.nexial.core.NexialConst.Web.WEB_PAGE_LOAD_WAIT_MS
import org.nexial.core.NexialConst.WebMail.WEBMAIL_TEMPORARYMAIL
import org.nexial.core.NexialConst.WebMail.WEBMAIL_TEMPORARYMAIL_DOWNLOAD_URL_BASE
import org.nexial.core.SystemVariables.getDefaultInt
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.CheckUtils.requiresNotNull
import org.nexial.core.utils.ConsoleUtils
import java.io.File.separator
import java.util.*

/**
 * Abstract Class which performs all the operations related to [WebMailCommand].
 *
 * @author Dhanapathi.Marepalli
 */
abstract class WebMailer {
    /**
     * Extract the [EmailDetails]'s matching the search criteria and duration. The value is set
     * into the var passed in.
     *
     * @param web            the [WebCommand] object passed in.
     * @param profile        the [WebMailProfile] passed in.
     * @param searchCriteria the search criteria for the subject.
     * @param duration       the duration before which the email is created.
     * @return [StepResult.success] or [StepResult.fail] based on whether the emails
     * related information are extracted or not.
     */
    abstract fun search(web: WebCommand, profile: WebMailProfile, searchCriteria: String, duration: Long): Set<String?>?

    /**
     * Extracts the value of the [EmailDetails] matching the search criteria associated with a specific
     * mail id passed in against the profile
     * value passed. The value of the [EmailDetails] will be assigned to the id passed in.
     * However, if the id does not exist in the profile then the method returns
     * a [StepResult.fail] with the appropriate failure message.
     *
     * @param web     [WebCommand] passed in.
     * @param profile the profile passed in.
     * @param id      the email id.
     * @return [StepResult.success] or [StepResult.fail]
     * based on whether the id exists or not.
     */
    open fun read(web: WebCommand, profile: WebMailProfile, id: String): EmailDetails? {
        requiresNotNull(profile, "profile name cannot be empty.")
        requiresNotBlank(id, "mail id cannot be empty.")
        val emailDetails = web.context.getObjectData(deriveEmailContentVar(profile, id), EmailDetails::class.java)
        return if (Objects.isNull(emailDetails)) null else emailDetails
    }

    /**
     * Delete an email with the id specified and the profile against it which results in
     * [StepResult.success]. If the email id does not exist then it will
     * result in [StepResult.fail] with appropriate message.
     *
     * @param profile the [WebMailProfile] profile passed in.
     * @param id             the Email id passed in.
     * @return [StepResult.success] or [StepResult.fail] based on mail is deleted or not.
     */
    abstract fun delete(web: WebCommand, profile: WebMailProfile, id: String): Boolean

    /**
     * Download an email attachment related to the email with the id and the attachment name
     * specified against the profile which results in [StepResult.success]. If the email id/attachment
     * does not exist then it will result in [StepResult.fail] with appropriate message.
     *
     * @param web the [WebCommand] passed in.
     * @param profile the [WebMailProfile] profile passed in.
     * @param id             the Email id passed in.
     * @param attachment     the Email attachment file name  passed in.
     * @param saveTo     the full path of the file download location.
     *
     * @return [StepResult.success] or [StepResult.fail] based on mail is deleted or not.
     *
     * @throws IllegalArgumentException if there is no attachment with the file name specified.
     * @throws RuntimeException if the file download fails.
     */
    open fun attachment(web: WebCommand, profile: WebMailProfile, id: String, attachment: String, saveTo: String) {
        val email = web.context.getObjectData(deriveEmailContentVar(profile, id), EmailDetails::class.java)
        val index = email?.getAttachments()?.indexOf(attachment)
                    ?: throw IllegalArgumentException("There is no attachment named $attachment in email $id.")
        val attachmentName = email.attachmentMap[attachment]

        val url = "${deriveBaseUrl(profile)}/${profile.inbox}/${id}/${index}/${attachmentName}"
        val response = WebServiceClient(web.context).download(url, EMPTY, saveTo)
        if (response.returnCode < 200 || response.returnCode > 299) {
            throw RuntimeException("Unable to download mail $id from ${profile.inbox}: ${response.statusText}")
        }
    }

    /**
     * Download attachment(s) related to the email with the id and the attachment name
     * specified against the profile which results in [StepResult.success]. If the email id or attachments
     * does not exist then it will result in [StepResult.fail] with appropriate message.
     *
     * @param web the [WebCommand] passed in.
     * @param profile the [WebMailProfile] profile passed in.
     * @param id             the Email id passed in.
     * @param saveDir the location where the files should be downloaded.
     * @return no. of files that failed to download.
     *
     * @throws IllegalArgumentException in case there are no attachments to the email.
     */
    open fun attachments(web: WebCommand, profile: WebMailProfile, id: String, saveDir: String): Int {
        val email = web.context.getObjectData(deriveEmailContentVar(profile, id), EmailDetails::class.java)
        if (email?.attachmentMap?.isEmpty != false) return 0

        var failedDownloads = 0

        email.getAttachments().forEachIndexed { index, attachmentName ->
            val attachment = email.attachmentMap[attachmentName]
            val url = "${deriveBaseUrl(profile)}/${profile.inbox}/${id}/${index}/${attachment}"
            val file = "${StringUtils.appendIfMissing(saveDir, separator)}${attachmentName}"

            val response = WebServiceClient(web.context).download(url, EMPTY, file)
            if (response.returnCode < 200 || response.returnCode > 299) {
                ConsoleUtils.error("Unable to download attachment $attachment. Response is ${response.statusText}")
                failedDownloads++
            }
        }

        return failedDownloads
    }

    private fun deriveBaseUrl(profile: WebMailProfile) =
        if (profile.provider == WEBMAIL_TEMPORARYMAIL) WEBMAIL_TEMPORARYMAIL_DOWNLOAD_URL_BASE else EMPTY

    /**
     * Retrieves the maxLoadTime for a page or a component to load. The value set in the
     *
     * @param context [ExecutionContext] passed in.
     * @return maxLoadTime computed.
     */
    fun getMaxLoadTime(context: ExecutionContext) =
        context.getIntData(WEB_PAGE_LOAD_WAIT_MS, getDefaultInt(WEB_PAGE_LOAD_WAIT_MS)).toString()

    protected fun deriveEmailContentVar(profile: WebMailProfile, emailId: String) =
        "${NAMESPACE}${profile.profileName}.$emailId"

    protected fun cleanMailContent(text: String?) =
        if (StringUtils.isEmpty(text))
            text
        else
            StringUtils.remove(text, "\r")
                .split("\n")
                .map { line -> StringUtils.trim(line) }
                .filter { line -> StringUtils.isNotEmpty(line) }
                .joinToString("\n")
}
