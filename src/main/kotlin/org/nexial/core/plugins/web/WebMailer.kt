package org.nexial.core.plugins.web

import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.NAMESPACE
import org.nexial.core.NexialConst.Web.*
import org.nexial.core.SystemVariables.getDefaultInt
import org.nexial.core.model.ExecutionContext
import org.nexial.core.utils.CheckUtils.*
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
     * @param var            the variable to which emails satisfying the criteria are to be added.
     * @param profile        the [WebMailProfile] passed in.
     * @param searchCriteria the search criteria for the subject.
     * @param duration       the duration before which the email is created.
     * @return [StepResult.success] or [StepResult.fail] based on whether the emails
     * related information are extracted or not.
     */
    abstract fun search(web: WebCommand, profile: WebMailProfile, searchCriteria: String, duration: Long): Set<String?>?

    /**
     * Extracts the value of the [EmailDetails] matching the search criteria associated with a specific
     * mail Id passed in against the profile
     * value passed. The value of the [EmailDetails] will be assigned to the id passed in.
     * However if the id does not exist in the profile then the method returns
     * a [StepResult.fail] with the appropriate failure message.
     *
     * @param web     [WebCommand] passed in.
     * @param var     the name associated to the [EmailDetails] retrieved.
     * @param profile the profile passed in.
     * @param id      the email Id.
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
     * Retrieves the maxLoadTime for a page or a component to load. The value set in the
     *
     * @param context [ExecutionContext] passed in.
     * @return maxLoadTime computed.
     */
    fun getMaxLoadTime(context: ExecutionContext) =
            context.getIntData(WEB_PAGE_LOAD_WAIT_MS, getDefaultInt(WEB_PAGE_LOAD_WAIT_MS)).toString()

    protected fun deriveEmailContentVar(profile: WebMailProfile,
                                        emailId: String) = "${NAMESPACE}${profile.profileName}.$emailId"

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