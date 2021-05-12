package org.nexial.core.plugins.web;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.NexialConst;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.CheckUtils;

import javax.validation.constraints.NotNull;
import java.util.Map;

import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

/**
 * @author Dhanapathi.Marepalli
 * Mail Provider profile class which contains various profile related information.
 */
public class WebMailProfile {
    private String mailProvider;
    private String inbox;
    private String domain;
    private String profileName;

    public String getMailProvider() {
        return mailProvider;
    }

    public String getInbox() {
        return inbox;
    }

    public String getDomain() {
        return domain;
    }

    public String getProfileName() {
        return profileName;
    }

    /**
     * static method for creation of MailChecker object. Returns an instance of {@link WebMailProfile} if
     * all the mandatory profile attributes are available. Else it will raise an exception as mentioned in the
     * {@link CheckUtils#fail(String)}.
     *
     * @param profile read from the data file.
     * @return the {@link WebMailProfile} created.
     */
    static WebMailProfile newInstance(@NotNull final String profile) {
        requiresNotBlank(profile, "Invalid mail checker profile ", profile);

        ExecutionContext context = ExecutionThread.get();
        requiresNotNull(context, "Unable to obtain execution context");

        Map<String, String> config = context.getDataByPrefix(profile + ".");
        if (MapUtils.isEmpty(config)) {
            context.logCurrentStep("No mail checker configuration found for '" + profile + "'");
            return null;
        }

        WebMailProfile settings = new WebMailProfile();
        settings.mailProvider = settings.getMailProvider();
        if (config.containsKey("mailProvider")) {
            settings.mailProvider = config.get("mailProvider");
        } else {
            settings.mailProvider = NexialConst.WebMail.WEBMAIL_MAILINAOR;
        }

        if (config.containsKey("inbox")) {
            settings.inbox = config.get("inbox");
        }

        if (config.containsKey("domain")) {
            settings.domain = config.get("domain");
        }

        if (StringUtils.isBlank(settings.inbox)) {
            CheckUtils.fail("The 'inbox' from which the emails to be read is not specified.");
        }

        if (StringUtils.isNotBlank(profile)) {
            settings.profileName = profile;
        }

        return settings;
    }
}
