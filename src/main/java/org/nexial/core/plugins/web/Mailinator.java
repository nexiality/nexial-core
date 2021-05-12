package org.nexial.core.plugins.web;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.ConsoleUtils;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.NexialConst.WebMail.*;

/**
 * {@link WebMailCommand} implementation of <a href="https://www.mailinator.com">Mailinator</a>.
 *
 * @author Dhanapathi Marepalli
 */
public class Mailinator extends WebMailer {
    private static final String MAILINATOR_URL =
            "https://www.mailinator.com/v3/index.jsp?zone=public&query=%s#/#inboxpane";

    @Override
    protected EmailDetails read(@NotNull WebCommand web, @NotNull String var, @NotNull String profile,
                                @NotNull String id) {
        return super.read(web, var, profile, id);
    }

    private static final String SUBJECT_TEXT = "Subject:";
    private static final String TO_TEXT = "To:";
    private static final String FROM_TEXT = "From:";
    private static final String RECEIVED_TEXT = "Received:";

    private static final String SCREENSHOT_FILE_EXTENSION = ".jpg";

    //XPATH Variables
    private static final String XPATH_EMAIL_SUBJECT_LOCATOR = "//table//tr[./td[contains(string(.), %s)]]";
    private static final String XPATH_EMAIL_LINK = "//tr[@id='%s']/td[3]";
    private static final String XPATH_EMAIL_HTML = "/html";
    private static final String XPATH_TR = "//tr";
    private static final String XPATH_EMAIL_FRAME = "//*[@id='msg_body']";
    private static final String XPATH_EMAIL_FRAME_HTML = "//body";
    private static final String XPATH_EMAIL_FRAME_HREFS = "//*[@href]";
    private static final String XPATH_MAIL_CHECK_BOX_ID = "//*[@id='%s']";
    private static final String XPATH_BUTTON_ID = String.format(XPATH_MAIL_CHECK_BOX_ID, "trash_but");
    private static final String ID_ROW = "row_";
    private static final String ID_CHECK = "check_";
    private static final String HREF_ATTRIBUTE = "href";
    private static final String ATTRIBUTE_ID = "id";

    private static final String TIME_STAMP_FORMAT = "MMM dd yyyy HH:mm:ss";
    private static final String SPACE = " ";

    @Override
    public Set<String> search(@NotNull final WebCommand web, @NotNull final String var,
                              @NotNull final WebMailProfile profile,
                              @NotNull final String searchCriteria, final @NotNull long duration) {
        web.switchBrowser(NEXIAL_WEBMAIL_MAILINATOR_BROWSER_PROFILE, NEXIAL_WEBMAIL_BROWSER_CONFIG);
        String mainURL = String.format(MAILINATOR_URL, profile.getInbox());

        String maxLoadTime = getMaxLoadTime(web.getContext());
        web.openAndWait(mainURL, maxLoadTime);

        String emailSubjectLocator = String.format(XPATH_EMAIL_SUBJECT_LOCATOR,
                                                   web.locatorHelper.normalizeXpathText(searchCriteria.trim()));
        web.saveCount(NEXIAL_WEBMAIL_MAILINATOR_EMAIL_MATCHING_SIZE, emailSubjectLocator);
        int size = web.getContext().getIntData(NEXIAL_WEBMAIL_MAILINATOR_EMAIL_MATCHING_SIZE);
        ConsoleUtils.log("Count of Emails with the Subject matching the searchCriteria is " + size);

        if (size == 0) {
            return new HashSet<>();
        }

        return extractEmailInfo(web, mainURL, emailSubjectLocator, var, profile, duration, maxLoadTime);
    }

    /**
     * Extract the {@link EmailDetails}'s matching the search criteria and duration. The value is set
     * into the var passed in.
     *
     * @param mainURL           Mailinator URL passed in.
     * @param emailSubjectXPath xpath of the email Subject.
     * @param var               the variable into which the emails which satisfy the criteria are to be added.
     * @param profile           the profile passed in.
     * @param duration          the duration before which the email is created.
     * @param maxLoadTime       the value retrieved from the {@link WebMailer#getMaxLoadTime} method.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on whether the emails
     *         related information are extracted or not.
     */
    private Set<String> extractEmailInfo(@NotNull final WebCommand web, @NotNull final String mainURL,
                                         @NotNull final String emailSubjectXPath,
                                         @NotNull final String var, @NotNull WebMailProfile profile,
                                         final long duration, @NotNull final String maxLoadTime) {
        web.saveAttributeList(NEXIAL_WEBMAIL_MAILINATOR_MAIL_IDS, emailSubjectXPath, ATTRIBUTE_ID);
        String[] mailIds = (String[]) web.getContext().getObjectData(NEXIAL_WEBMAIL_MAILINATOR_MAIL_IDS);

        Set<String> matchingEmails =
                Arrays.stream(mailIds).filter(StringUtils::isNotEmpty).map(String::trim).collect(Collectors.toSet());
        ConsoleUtils.log("Email id's are " + Arrays.toString(mailIds));
        Set<String> validEmails = new HashSet<>();

        for (String emailId : matchingEmails) {
            web.click(String.format(XPATH_EMAIL_LINK, emailId));
            ConsoleUtils.log(String.format("Mail with id %s is opened.", emailId));
            web.waitUntilVisible(XPATH_TR, maxLoadTime);

            web.saveTextArray(NEXIAL_WEBMAIL_MAILINATOR_MAIL_ROW_CONTENT, XPATH_TR);
            String[] trValues = (String[]) web.getContext().getObjectData(NEXIAL_WEBMAIL_MAILINATOR_MAIL_ROW_CONTENT);

            String subject = null;
            String to = null;
            String from = null;
            String received = null;

            Set<String> texts = Arrays.stream(trValues).filter(StringUtils::isNotEmpty).map(String::trim)
                                      .collect(Collectors.toSet());
            for (String text : texts) {
                if (text.startsWith(SUBJECT_TEXT)) {
                    subject = StringUtils.substringAfter(text, SUBJECT_TEXT).trim();
                    ConsoleUtils.log("Subject is " + subject);
                } else if (text.startsWith(TO_TEXT)) {
                    to = StringUtils.substringAfter(text, TO_TEXT).trim();
                    ConsoleUtils.log("TO is " + to);
                } else if (text.startsWith(FROM_TEXT)) {
                    from = StringUtils.substringAfter(text, FROM_TEXT).trim();
                    ConsoleUtils.log("From Email is " + from);
                } else if (text.startsWith(RECEIVED_TEXT)) {
                    received = StringUtils.substringAfter(text, RECEIVED_TEXT).trim();
                    ConsoleUtils.log("Received is " + received);
                }
            }

            ConsoleUtils.log("Final text is " + StringUtils.joinWith(NL, subject, to, from, received));
            LocalDateTime emailReceiveTime = getEmailReceivedTime(received);
            long minutes = MINUTES.between(LocalDateTime.now(), emailReceiveTime) * -1;

            if (minutes > duration) {
                web.openAndWait(mainURL, maxLoadTime);
                continue;
            }

            String screenCaptureFile = StringUtils.join(web.getContext().getProject().getScreenCaptureDir(),
                                                        File.separatorChar, emailId, SCREENSHOT_FILE_EXTENSION);
            web.screenshot(screenCaptureFile, XPATH_EMAIL_HTML, "true");

            EmailDetails email = new EmailDetails();
            email.setId(emailId);
            email.setSubject(subject);
            email.setTo(to);
            email.setFrom(from);
            email.setTime(emailReceiveTime);

            web.selectFrame(XPATH_EMAIL_FRAME);
            web.saveText(NEXIAL_WEBMAIL_MAILINATOR_EMAIL_CONTENT, XPATH_EMAIL_FRAME_HTML);
            String content = web.getContext().getStringData(NEXIAL_WEBMAIL_MAILINATOR_EMAIL_CONTENT).trim();

            email.setContent(content);
            ConsoleUtils.log("Email Content is: " + content);

            web.saveAttributeList(NEXIAL_WEBMAIL_MAILINATOR_EMAIL_LINKS, XPATH_EMAIL_FRAME_HREFS, HREF_ATTRIBUTE);
            String[] links = (String[]) web.getContext().getObjectData(NEXIAL_WEBMAIL_MAILINATOR_EMAIL_LINKS);
            if (ArrayUtils.isNotEmpty(links)) {
                Set<String> emailLinks = Arrays.stream(links).map(String::trim).collect(Collectors.toSet());
                ConsoleUtils.log("Email links are " + emailLinks);
                email.setLinks(emailLinks);
            }

            email.setHtml(web.getBrowser().getDriver().getPageSource());
            ConsoleUtils.log("Html content is " + email.getHtml());

            String contextName = StringUtils.joinWith(VARIABLE_SEPARATOR, VARIABLE_PREFIX,
                                                      profile.getProfileName(), emailId);
            web.getContext().setData(contextName, email);
            validEmails.add(emailId);
            web.openAndWait(mainURL, maxLoadTime);
        }

        web.getContext().setData(var, validEmails);
        return validEmails;
    }

    /**
     * Convert the time in the string format as mentioned in the format {@link this#TIME_STAMP_FORMAT}
     * to {@link LocalDateTime}.
     *
     * @param time timestamp mentioned as string.
     * @return the {@link LocalDateTime} value of the string passed in.
     */
    private LocalDateTime getEmailReceivedTime(@NotNull final String time) {
        String[] timeStampComponents = ArrayUtils.subarray(time.split("\\s"), 1, 5);
        String timeStamp =
                StringUtils.joinWith(SPACE, (Object[]) timeStampComponents);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIME_STAMP_FORMAT);
        return LocalDateTime.from(formatter.parse(timeStamp));
    }

    @Override
    protected boolean delete(@NotNull final WebCommand web, @NotNull final WebMailProfile webMailProfile,
                             @NotNull final String id) {
        if (webMailProfile == null) {
            ConsoleUtils.log("Invalid Web Profile.");
            return false;
        }

        EmailDetails email = web.getContext().getObjectData(StringUtils.joinWith(VARIABLE_SEPARATOR, VARIABLE_PREFIX,
                                                                                 webMailProfile.getProfileName(), id),
                                                            EmailDetails.class);
        if (email == null) {
            ConsoleUtils.error("There is no email with this id.");
            return false;
        }

        web.switchBrowser(NEXIAL_WEBMAIL_MAILINATOR_BROWSER_PROFILE, NEXIAL_WEBMAIL_BROWSER_CONFIG);
        String maxLoadTime = getMaxLoadTime(web.getContext());
        web.openAndWait(String.format(MAILINATOR_URL, webMailProfile.getInbox()), maxLoadTime);

        String checkBoxId = id.replace(ID_ROW, ID_CHECK);
        String checkBoxXPathLocator = String.format(XPATH_MAIL_CHECK_BOX_ID, checkBoxId);
        if (web.assertElementPresent(checkBoxXPathLocator).failed()) {
            ConsoleUtils.error(String.format("Email with id %s is not present.", id));
            return false;
        }

        web.clickAndWait(String.format(XPATH_MAIL_CHECK_BOX_ID, checkBoxId), maxLoadTime);
        ConsoleUtils.log(String.format("Mail with Id %s is selected.", id));

        web.clickAndWait(XPATH_BUTTON_ID, maxLoadTime);
        String name = StringUtils.joinWith(VARIABLE_SEPARATOR, VARIABLE_PREFIX, webMailProfile.getProfileName(), id);
        web.getContext().removeData(name);

        return true;
    }
}
