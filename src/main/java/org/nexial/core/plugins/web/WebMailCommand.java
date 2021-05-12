package org.nexial.core.plugins.web;

import java.util.HashMap;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.NexialConst.WebMail;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.CollectionUtils;

import static org.nexial.core.NexialConst.DEF_SPRING_XML;
import static org.nexial.core.NexialConst.OPT_SPRING_XML;
import static org.nexial.core.NexialConst.WebMail.WEBMAIL_TEMPORARYMAIL;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

/**
 * This command is used to check the emails sent to fake email (webmail) sites like
 * <a href="https://www.mailinator.com">Mailinator</a>, <a href="https://www.temporary-mail.net">temporary-mail.net</a>
 * etc.
 *
 * @author Dhanapathi Marepalli
 */
public class WebMailCommand extends BaseCommand {
    protected static final String MAIL_PROVIDER_MAILINATOR = "mailinator";
    protected transient WebCommand web;

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
        web = (WebCommand) context.findPlugin("web");
    }

    @Override
    public String getTarget() {
        return "webmail";
    }

    /**
     * Retrieves the profile information of the Mail Command.
     *
     * @param profile name of the profile.
     * @return {@link WebMailProfile}.
     */
    private WebMailProfile getMailProviderProfile(String profile) {
        return WebMailProfile.newInstance(profile);
    }

    /**
     * Search for the emails with the subject containing the content mentioned in the `searchCriteria` and the
     * duration of since the email is received is less than the mentioned `duration`.
     * The email id's(here id's are generally the HTML id's) are stored inside the variable `var`.
     *
     * @param var            variable containing the emailId's.
     * @param profile        specifies the properties of the FAKE EMAIL reader.
     * @param searchCriteria search string that the subject of the email should contain.
     * @param duration       time since the email is received.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on whether the emailId's are
     *         retrieved or not. In case there is no search criteria matching still the return value is
     *         {@link StepResult#success(String)}.
     */
    public StepResult search(@NotNull final String var, @NotNull final String profile,
                             @NotNull final String searchCriteria, @NotNull final String duration) {
        requiresNotBlank(var, "`var` cannot be empty.");
        requiresNotBlank(profile, "`profile` cannot be empty.");
        requiresNotBlank(searchCriteria.trim(), "`searchCriteria` cannot be empty.");
        requiresNotBlank(duration, "`duration` cannot be empty");

        long time = Long.parseLong(duration);
        if (time < 0) {
            return StepResult.fail("duration cannot be a negative value.");
        }

        if (!NumberUtils.isDigits(duration) || duration.length() > 4) {
            return StepResult.fail(String.format("Invalid time duration %s.", duration));
        }

        WebMailProfile mailProviderProfile = getMailProviderProfile(profile);
//        WebMailProfile webMailProfile = mailProviderProfile;
        if (mailProviderProfile == null) {
            return StepResult.fail(String.format("There is no profile with the name %s.", profile));
        }

        String inbox = mailProviderProfile.getInbox();
        if (StringUtils.isEmpty(inbox)) {
            return StepResult.fail(String.format("There is no inbox specified for the given profile %s.", profile));
        }

        if (mailProviderProfile.getMailProvider().equals(WEBMAIL_TEMPORARYMAIL)
            && StringUtils.isEmpty(mailProviderProfile.getDomain())) {
            return StepResult.fail(String.format("There is no domain specified for the given profile %s.", profile));
        }

        if (time > WebMail.MAX_DURATION) {
            return StepResult.fail(String.format("Mails older than %s minutes cannot be retrieved.",
                                                 WebMail.MAX_DURATION));
        }

        String mailProvider = mailProviderProfile.getMailProvider();
        WebMailer webMailer = getWebMailer(mailProvider);

        if (webMailer == null) {
            return StepResult.fail("Invalid mail provider.");
        }

        Set<String> emails = webMailer.search(web, var, mailProviderProfile, searchCriteria, Long.parseLong(duration));
        return CollectionUtils.isEmpty(emails) ?
               StepResult.success("There are no emails matching the criteria.") :
               StepResult.success(String.format("The mails matching the criteria are %s", emails));
    }

    /**
     * Retrieves the {@link WebMailer} based on the mailProvider passed in.
     *
     * @param mailProvider the name of the mailProvider.
     * @return {@link WebMailer}.
     */
    private WebMailer getWebMailer(String mailProvider) {
        ClassPathXmlApplicationContext springContext = new ClassPathXmlApplicationContext(
                "classpath:" + System.getProperty(OPT_SPRING_XML, DEF_SPRING_XML));
        if (StringUtils.isEmpty(mailProvider)) {
            mailProvider = MAIL_PROVIDER_MAILINATOR;
        }
        return (WebMailer) springContext.getBean("webMails", HashMap.class).get(mailProvider);
    }

    /**
     * Extracts the value of the {@link EmailDetails} matching the search criteria associated with a specific
     * mail Id passed in against the profile
     * value passed. The value of the {@link EmailDetails} will be assigned to the id passed in. However if the id
     * does not exist in the profile then the method returns a {@link StepResult#fail(String)} with the appropriate
     * failure message.
     *
     * @param var     the name associated to the {@link EmailDetails} retrieved.
     * @param profile the profile passed in.
     * @param id      the email Id.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)}
     *         based on whether the id exists or not.
     */
    public StepResult read(@NotNull final String var, @NotNull final String profile, @NotNull final String id) {
        requiresNotBlank(var, "`var` cannot be empty.");
        requiresNotBlank(profile, "`profile name` cannot be empty.");
        requiresNotBlank(id, "mail `id` cannot be empty.");

        WebMailProfile webMailProfile = getMailProviderProfile(profile);
        if (webMailProfile == null) {
            return StepResult.fail(String.format("There is no profile with the name %s.", profile));
        }

        WebMailer webMailer = getWebMailer(webMailProfile.getMailProvider());
        EmailDetails emailDetails = webMailer.read(web, var, webMailProfile.getProfileName(), id);
        return (emailDetails != null) ?
               StepResult.success(String.format("Retrieved Email Details are %s.", emailDetails)) :
               StepResult.fail(String.format("There is no email with id %s against the profile %s.",
                                             id, profile));
    }

    /**
     * Delete an email with the id specified and the profile against it which results in
     * {@link StepResult#success(String)}. If the email id does not exist then it will
     * result in {@link StepResult#fail(String)} with appropriate message.
     *
     * @param profile the email profile.
     * @param id      Email id.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on mail is deleted or not.
     */
    public StepResult delete(@NotNull final String profile, @NotNull final String id) {
        requiresNotBlank(id, "`id` cannot be empty.");
        requiresNotBlank(profile, "`profile` cannot be empty.");

        WebMailProfile webMailProfile = getMailProviderProfile(profile);
        if (webMailProfile == null) {
            return StepResult.fail(String.format("There is no profile with the name %s.", profile));
        }

        WebMailer webMailer = getWebMailer(webMailProfile.getMailProvider());
        return webMailer.delete(web, webMailProfile, id) ?
               StepResult.success(String.format("Email with id %s is deleted.", id)) :
               StepResult.fail("Email deletion failed.");
    }
}
