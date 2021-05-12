package org.nexial.core.plugins.web;

import java.util.Objects;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;

import static org.nexial.core.NexialConst.Web.WEB_PAGE_LOAD_WAIT_MS;
import static org.nexial.core.SystemVariables.getDefaultInt;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

/**
 * Abstract Class which performs all the operations related to {@link WebMailCommand}.
 *
 * @author Dhanapathi.Marepalli
 */
public abstract class WebMailer {
    protected static final String VARIABLE_SEPARATOR = ".";
    protected static final String VARIABLE_PREFIX = "nexial";

    /**
     * Extract the {@link EmailDetails}'s matching the search criteria and duration. The value is set
     * into the var passed in.
     *
     * @param web            the {@link WebCommand} object passed in.
     * @param var            the variable to which emails satisfying the criteria are to be added.
     * @param profile        the {@link WebMailProfile} passed in.
     * @param searchCriteria the search criteria for the subject.
     * @param duration       the duration before which the email is created.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on whether the emails
     *         related information are extracted or not.
     */
    protected abstract Set<String> search(@NotNull final WebCommand web, @NotNull final String var,
                                          @NotNull final WebMailProfile profile,
                                          @NotNull final String searchCriteria, final @NotNull long duration);

    /**
     * Extracts the value of the {@link EmailDetails} matching the search criteria associated with a specific
     * mail Id passed in against the profile
     * value passed. The value of the {@link EmailDetails} will be assigned to the id passed in.
     * However if the id does not exist in the profile then the method returns
     * a {@link StepResult#fail(String)} with the appropriate failure message.
     *
     * @param web     {@link WebCommand} passed in.
     * @param var     the name associated to the {@link EmailDetails} retrieved.
     * @param profile the profile passed in.
     * @param id      the email Id.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)}
     *         based on whether the id exists or not.
     */
    protected EmailDetails read(@NotNull final WebCommand web, @NotNull final String var, @NotNull final String profile,
                                @NotNull final String id) {
        requiresNotBlank(var, "`var` cannot be empty.");
        requiresNotBlank(profile, "`profile name` cannot be empty.");
        requiresNotBlank(id, "mail `id` cannot be empty.");

        EmailDetails emailDetails =
                web.getContext().getObjectData(StringUtils.joinWith(VARIABLE_SEPARATOR, VARIABLE_PREFIX, profile, id),
                                               EmailDetails.class);

        if (Objects.isNull(emailDetails)) {
            return null;
        }

        web.getContext().setData(var, emailDetails);
        return emailDetails;
    }

    /**
     * Delete an email with the id specified and the profile against it which results in
     * {@link StepResult#success(String)}. If the email id does not exist then it will
     * result in {@link StepResult#fail(String)} with appropriate message.
     *
     * @param webMailProfile the {@link WebMailProfile} profile passed in.
     * @param id             the Email id passed in.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on mail is deleted or not.
     */
    protected abstract boolean delete(@NotNull final WebCommand web, @NotNull final WebMailProfile webMailProfile,
                                      @NotNull final String id);

    /**
     * Retrieves the maxLoadTime for a page or a component to load. The value set in the
     *
     * @param context {@link ExecutionContext} passed in.
     * @return maxLoadTime computed.
     */
    protected String getMaxLoadTime(@NotNull ExecutionContext context) {
        return String.valueOf(context.getIntData(WEB_PAGE_LOAD_WAIT_MS, getDefaultInt(WEB_PAGE_LOAD_WAIT_MS)));
    }

}
