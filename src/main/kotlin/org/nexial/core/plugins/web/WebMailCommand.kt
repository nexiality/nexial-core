package org.nexial.core.plugins.web

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Web.OPT_DELAY_BROWSER
import org.nexial.core.NexialConst.WebMail.*
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.CheckUtils.requiresPositiveNumber
import org.springframework.util.CollectionUtils

/**
 * This command is used to check the emails sent to fake email (webmail) sites like
 * [Mailinator](https://www.mailinator.com), [temporary-mail.net](https://www.temporary-mail.net)
 * etc.
 *
 * @author Dhanapathi Marepalli
 */
class WebMailCommand : BaseCommand() {
    lateinit var webmailers: Map<String, WebMailer>

    @Transient
    protected var web: WebCommand? = null

    override fun init(context: ExecutionContext) {
        super.init(context)

        if (!context.isPluginLoaded("web")) {
            val currentDelayBrowser = context.getBooleanData(OPT_DELAY_BROWSER)
            context.setData(OPT_DELAY_BROWSER, true)
            web = context.findPlugin("web") as WebCommand
            context.setData(OPT_DELAY_BROWSER, currentDelayBrowser)
        } else
            web = context.findPlugin("web") as WebCommand
    }

    override fun getTarget() = "webmail"

    /**
     * Search for the emails with the subject containing the content mentioned in the `searchCriteria` and the
     * duration of since the email is received is less than the mentioned `duration`.
     * The email id's(here id's are generally the HTML id's) are stored inside the variable `var`.
     *
     * @param var            variable containing the emailId's.
     * @param profile        specifies the properties of the FAKE EMAIL reader.
     * @param searchCriteria search string that the subject of the email should contain.
     * @param duration       time since the email is received.
     * @return [StepResult.success] or [StepResult.fail] based on whether the emailId's are
     * retrieved or not. In case there is no search criteria matching still the return value is
     * [StepResult.success].
     */
    fun search(`var`: String, profile: String, searchCriteria: String, duration: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(profile, "profile cannot be empty.")
        // allow for empty search criteria (meaning, FIND ALL)
        // requiresNotBlank(searchCriteria, "searchCriteria cannot be empty.")

        requiresPositiveNumber(duration, "duration must be a positive number: $duration")
        if (duration.length > 4) return StepResult.fail("Invalid time duration $duration.")

        val time = duration.toLong()
        if (time > MAX_DURATION) return StepResult.fail("Mails older than $MAX_DURATION minutes cannot be retrieved.")

        val mailProfile = resolveProfile(profile)
        val subject = StringUtils.defaultIfBlank(searchCriteria, "").trim()
        val emails = mailProfile.mailer.search(web!!, mailProfile, subject, time)
        return if (CollectionUtils.isEmpty(emails)) {
            context.removeData(`var`)
            StepResult.success("There are no emails matching the criteria.")
        } else {
            context.setData(`var`, emails)
            StepResult.success("The mails matching the criteria are ${emails!!.joinToString(",")}")
        }
    }

    /**
     * Extracts the value of the [EmailDetails] matching the search criteria associated with a specific
     * mail Id passed in against the profile
     * value passed. The value of the [EmailDetails] will be assigned to the id passed in. However if the id
     * does not exist in the profile then the method returns a [StepResult.fail] with the appropriate
     * failure message.
     *
     * @param var     the name associated to the [EmailDetails] retrieved.
     * @param profile the profile passed in.
     * @param id      the email Id.
     * @return [StepResult.success] or [StepResult.fail]
     * based on whether the id exists or not.
     */
    fun read(`var`: String, profile: String, id: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(profile, "profile name cannot be empty.")
        requiresNotBlank(id, "mail id cannot be empty.")

        val mailProfile = resolveProfile(profile)
        val emailDetails = mailProfile.mailer.read(web!!, mailProfile, id)
        return if (emailDetails != null) {
            context.setData(`var`, emailDetails)
            StepResult.success("Retrieved email details for mail id $id")
        } else {
            context.removeData(`var`)
            StepResult.fail("There is no email with id $id against the profile $profile")
        }
    }

    /**
     * Delete an email with the id specified and the profile against it which results in
     * [StepResult.success]. If the email id does not exist then it will
     * result in [StepResult.fail] with appropriate message.
     *
     * @param profile the email profile.
     * @param id      Email id.
     * @return [StepResult.success] or [StepResult.fail] based on mail is deleted or not.
     */
    fun delete(profile: String, id: String): StepResult {
        requiresNotBlank(profile, "profile cannot be empty.")
        requiresNotBlank(id, "id cannot be empty.")

        val mailProfile = resolveProfile(profile)
        return if (mailProfile.mailer.delete(web!!, mailProfile, id))
            StepResult.success("Email with id $id is deleted.")
        else
            StepResult.fail("Email deletion failed.")
    }


    /**
     * static method for creation of MailChecker object. Returns an instance of [WebMailProfile] if
     * all the mandatory profile attributes are available. Else it will raise an exception as mentioned in the
     * [CheckUtils.fail].
     *
     * @param profile read from the data file.
     * @return the [WebMailProfile] created.
     */
    fun resolveProfile(profile: String): WebMailProfile {
        requiresNotBlank(profile, "Invalid mail checker profile ", profile)

        val config = context.getDataByPrefix("$profile.")
        if (MapUtils.isEmpty(config)) throw IllegalArgumentException("There is no profile with the name $profile")

        // check inbox
        val inbox = config["inbox"] ?: ""
        requiresNotBlank(inbox, "The 'inbox' from which the emails to be read is not specified.")

        // check provider
        val provider = config["provider"] ?: WEBMAIL_MAILINATOR
        val mailer = webmailers[provider] ?: throw IllegalArgumentException("Invalid mail provider $provider")

        // check domain
        val domain = config["domain"] ?: ""
        if (provider == WEBMAIL_TEMPORARYMAIL && StringUtils.isEmpty(domain))
            throw IllegalArgumentException("There is no domain specified for the given profile $profile")

        return WebMailProfile(profile, provider, inbox, domain, mailer)
    }
}