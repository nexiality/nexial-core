package org.nexial.core.plugins.web

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.WebMail.WEBMAIL_MAILINATOR
import org.nexial.core.NexialConst.WebMail.WEBMAIL_TEMPORARYMAIL
import org.nexial.core.utils.CheckUtils
import org.nexial.core.utils.CheckUtils.requiresNotBlank

/**
 * @author Dhanapathi.Marepalli
 * Mail Provider profile class which contains various profile related information.
 */
class WebMailProfile(val profileName: String,
                     val provider: String,
                     val inbox: String,
                     val domain: String,
                     val mailer: WebMailer) {

    companion object {
        /**
         * static method for creation of MailChecker object. Returns an instance of [WebMailProfile] if
         * all the mandatory profile attributes are available. Else it will raise an exception as mentioned in the
         * [CheckUtils.fail].
         *
         * @param profile read from the data file.
         * @return the [WebMailProfile] created.
         */
        @JvmStatic
        fun newInstance(profile: String): WebMailProfile {
            requiresNotBlank(profile, "Invalid mail checker profile ", profile)

            val context = ExecutionThread.get() ?: throw IllegalArgumentException("Unable to obtain execution context")

            val config = context.getDataByPrefix("$profile.")
            if (MapUtils.isEmpty(config)) throw IllegalArgumentException("There is no profile with the name $profile")

            // check inbox
            val inbox = config["inbox"] ?: ""
            requiresNotBlank(inbox, "The 'inbox' from which the emails to be read is not specified.")

            // check provider
            val provider = config["provider"] ?: WEBMAIL_MAILINATOR
            val mailer = context.webMails[provider] ?: throw IllegalArgumentException("Invalid mail provider $provider")

            // check domain
            val domain = config["domain"] ?: ""
            if (provider == WEBMAIL_TEMPORARYMAIL && StringUtils.isEmpty(domain))
                throw IllegalArgumentException("There is no domain specified for the given profile $profile")

            return WebMailProfile(profile, provider, inbox, domain, mailer)
        }
    }
}