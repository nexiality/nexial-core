package org.nexial.core.plugins.web;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.WebMail.*;

/**
 * {@link WebMailCommand} implementation of <a href="https://www.temporary-mail.net">temporary-mail.net</a>.
 *
 * @author Dhanapathi.Marepalli
 */
public class TemporaryMail extends WebMailer {
    private static final String TEMPORARY_MAIL_URL = "https://www.temporary-mail.net/";
    private static final String CHANGE_URL = TEMPORARY_MAIL_URL + "change";

    //XPATH Variables
    private static final String XPATH_INBOX_NAME = "//input[@id='user_mailbox']";
    private static final String XPATH_INBOX_SAVE = "//button[@id='user_set']";
    private static final String XPATH_SUBJECT = "//div[@class='mail-info']/h4";
    private static final String XPATH_FROM = "//ul[@class='list-unstyled']/li[1]";
    private static final String XPATH_RECEIVE_TIME = "//*[@id='message-date']";
    private static final String XPATH_EMAIL_FRAME_HREFS = "//div[@class='mail-content']//*[@href]";
    private static final String XPATH_EMAIL_FRAME_HTML = "//div[@class='mail-content']";
    private static final String XPATH_DELETE_EMAIL = "//a/i[@class='fa fa-close']";
    private static final String XPATH_EMAIL_SUBJECT_LOCATOR =
            "//a[contains(string(.), '%s') and @class='title-subject']";

    private static final String CONTEXT_VARIABLE_SEPARATOR = ".";
    private static final String CONTEXT_VARIABLE_PREFIX = "nexial";
    private static final String FROM_TEXT = "From:";
    private static final String EMAIL_AT = "@";
    private static final String IMAGE_FORMAT_EXTENSION = ".jpg";
    private static final String URL_SEPARATOR = "/";

    private static final String XPATH_EMAIL_HTML = "/html";
    private static final String HREF_ATTRIBUTE = "href";

    private static final String PST_TIME_STAMP_FORMAT = "MMM dd yyyy HH:mm:ss";
    private static final String CST_TIME_STAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String SPACE = " ";
    private static final String CST = "Asia/Shanghai";
    private static final String PST = "America/Los_Angeles";
    public static final String CST_TIME_ZONE_SPECIFIER = "CST";

    @Override
    public Set<String> search(@NotNull final WebCommand web, @NotNull final String var,
                              @NotNull final WebMailProfile profile,
                              @NotNull final String searchCriteria, final @NotNull long duration) {
        web.switchBrowser(NEXIAL_WEBMAIL_TEMPORARYMAIL_BROWSER_PROFILE, NEXIAL_WEBMAIL_BROWSER_CONFIG);
        String maxLoadTime = getMaxLoadTime(web.getContext());

        web.openAndWait(CHANGE_URL, maxLoadTime);
        web.type(XPATH_INBOX_NAME, profile.getInbox());

        web.click(XPATH_INBOX_SAVE);

        String emailSubjectLocator = String.format(XPATH_EMAIL_SUBJECT_LOCATOR, searchCriteria.trim());
        web.saveAttributeList(NEXIAL_WEBMAIL_TEMPORARYMAIL_MAIL_IDS, emailSubjectLocator, HREF_ATTRIBUTE);
        String[] mailIds = (String[]) web.getContext().getObjectData(NEXIAL_WEBMAIL_TEMPORARYMAIL_MAIL_IDS);

        int size = (mailIds != null) ? mailIds.length : 0;
        ConsoleUtils.log("Count of Emails with the Subject matching the searchCriteria is " + size);

        if (size == 0) {
            return new HashSet<>();
        }

        return extractEmailInfo(web, mailIds, var, profile, duration, maxLoadTime);
    }

    /**
     * Extract the {@link EmailDetails}'s matching the search criteria and duration. The value is set
     * into the var passed in.
     *
     * @param urls     urls matching the criteria.
     * @param var      the variable into which the emails which satisfy the criteria are to be added.
     * @param profile  the profile passed in.
     * @param duration the duration before which the email is created.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on whether the emails
     *         related information are extracted or not.
     */
    private Set<String> extractEmailInfo(@NotNull final WebCommand web,
                                         @NotNull final String[] urls,
                                         @NotNull final String var, @NotNull WebMailProfile profile,
                                         final long duration, @NotNull final String maxLoadTime) {
        Set<String> matchingEmails =
                Arrays.stream(urls).map(String::trim).collect(Collectors.toSet());

        ConsoleUtils.log("Email links are " + Arrays.toString(urls));
        Set<String> validEmails = new HashSet<>();

        if (CollectionUtils.isEmpty(matchingEmails)) {
            return new HashSet<>();
        }

        for (String url : matchingEmails) {
            String id = StringUtils.replace(url, TEMPORARY_MAIL_URL, StringUtils.EMPTY);
            web.openAndWait(url, maxLoadTime);
            ConsoleUtils.log(String.format("Mail with id %s is opened.", id));

            web.saveText(NEXIAL_TEMPORARY_MAIL_NET_RECEIVED_TIME, XPATH_RECEIVE_TIME);
            String receivedTimeText = web.getContext().getStringData(NEXIAL_TEMPORARY_MAIL_NET_RECEIVED_TIME);
            ConsoleUtils.log("Received time shown in the mail is " + receivedTimeText);

            String zone = receivedTimeText.endsWith(CST_TIME_ZONE_SPECIFIER) ? CST : PST;
            String timeStampPattern = receivedTimeText.endsWith(CST_TIME_ZONE_SPECIFIER) ?
                                      CST_TIME_STAMP_FORMAT : PST_TIME_STAMP_FORMAT;

            ZonedDateTime mailReceivedTime = getMailReceivedDateTime(receivedTimeText, zone, timeStampPattern);
            ZonedDateTime now = ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault());
            long minutes = mailReceivedTime.until(now, ChronoUnit.MINUTES);

            if (minutes > duration) {
                ConsoleUtils.log(String.format("Email with id %s is skipped as it is not with in the duration.", id));
                continue;
            }
            ConsoleUtils.log(String.format("Mail received time is %s.", mailReceivedTime.toLocalDate()));

            web.saveText(NEXIAL_TEMPORARY_MAIL_NET_SUBJECT, XPATH_SUBJECT);
            String subject = web.getContext().getStringData(NEXIAL_TEMPORARY_MAIL_NET_SUBJECT).trim();
            ConsoleUtils.log("Subject is " + subject);

            web.saveText(NEXIAL_TEMPORARY_MAIL_NET_FROM, XPATH_FROM);
            String fromText = web.getContext().getStringData(NEXIAL_TEMPORARY_MAIL_NET_FROM);

            String from = StringUtils.substringAfter(fromText, FROM_TEXT).trim();
            ConsoleUtils.log("From is " + from);

            String to = StringUtils.join(profile.getInbox(), EMAIL_AT, profile.getDomain());
            ConsoleUtils.log("To is " + to);

            String htmlContent = web.getBrowser().getDriver().getPageSource();
            ConsoleUtils.log("HTML content is " + htmlContent);

            web.saveText(NEXIAL_WEBMAIL_TEMPORARYMAIL_EMAIL_CONTENT, XPATH_EMAIL_FRAME_HTML);
            String content = web.getContext().getStringData(NEXIAL_WEBMAIL_TEMPORARYMAIL_EMAIL_CONTENT);
            ConsoleUtils.log("Content is " + content);

            String screenCaptureFile = StringUtils.join(web.getContext().getProject().getScreenCaptureDir(),
                                                        File.separatorChar,
                                                        StringUtils.substringAfterLast(url, URL_SEPARATOR),
                                                        IMAGE_FORMAT_EXTENSION);
            web.screenshot(screenCaptureFile, XPATH_EMAIL_HTML, "true");

            web.saveAttributeList(NEXIAL_WEBMAIL_TEMPORARYMAIL_EMAIL_LINKS, XPATH_EMAIL_FRAME_HREFS, HREF_ATTRIBUTE);
            String[] links = (String[]) web.getContext().getObjectData(NEXIAL_WEBMAIL_TEMPORARYMAIL_EMAIL_LINKS);
            Set<String> emailLinks = new HashSet<>();
            if (ArrayUtils.isNotEmpty(links)) {
                emailLinks = Arrays.stream(links).map(String::trim).collect(Collectors.toSet());
                ConsoleUtils.log("Email links are " + emailLinks);
            }

            EmailDetails emailDetails =
                    getEmailDetails(id, mailReceivedTime, subject, from, to, content, emailLinks, htmlContent);
            String contextName = StringUtils.joinWith(CONTEXT_VARIABLE_SEPARATOR, CONTEXT_VARIABLE_PREFIX,
                                                      profile.getProfileName(), id);
            web.getContext().setData(contextName, emailDetails);
            validEmails.add(id);
        }

        web.getContext().setData(var, validEmails);
        return validEmails;
    }

    /**
     * Gets the {@link EmailDetails} object with the details passed in.
     *
     * @param id               Email Id.
     * @param mailReceivedTime time at which the email is received.
     * @param subject          subject of the email.
     * @param from             from address of the email.
     * @param to               to address of the email.
     * @param content          content of the email.
     * @param emailLinks       various links that the email body contains.
     * @return generated {@link EmailDetails}.
     */
    private EmailDetails getEmailDetails(@NotNull final String id, @NotNull final ZonedDateTime mailReceivedTime,
                                         @NotNull final String subject, @NotNull final String from,
                                         @NotNull final String to, @NotNull final String content,
                                         @NotNull final Set<String> emailLinks, @NotNull final String html) {
        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setId(id);
        emailDetails.setSubject(subject);
        emailDetails.setTo(to);
        emailDetails.setFrom(from);
        emailDetails.setContent(content);
        emailDetails.setLinks(emailLinks);
        emailDetails.setTime(mailReceivedTime.toLocalDateTime());
        emailDetails.setHtml(html);
        return emailDetails;
    }

    /**
     * Converts the Email Received time in terms of the system specific time zone.
     *
     * @param receivedTimeText the received time information displayed on the web page.
     * @param zone             the time zone mentioned in the page.
     * @param dateTimePattern  the pattern mentioning the date and time as per the email.
     * @return {@link ZonedDateTime} value of the mail received time as per the system time.
     */
    private ZonedDateTime getMailReceivedDateTime(String receivedTimeText, String zone, String dateTimePattern) {
        String[] timeStampComponents =
                zone.equals(PST) ?
                ArrayUtils.subarray(receivedTimeText.split("\\s"), 1, 5) :
                ArrayUtils.subarray(receivedTimeText.split("\\s"), 0, 2);

        String timeStamp =
                StringUtils.joinWith(SPACE, (Object[]) timeStampComponents);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateTimePattern);

        LocalDateTime ldt = LocalDateTime.now(ZoneId.of(zone));
        ldt = ldt.parse(timeStamp, formatter);

        ZonedDateTime zonedDateTime = ZonedDateTime.of(ldt, ZoneId.of(zone));
        return zonedDateTime.withZoneSameInstant(ZoneId.systemDefault());
    }

    @Override
    public boolean delete(@NotNull final WebCommand web, @NotNull final WebMailProfile webMailProfile,
                          @NotNull final String id) {
        if (webMailProfile == null) {
            ConsoleUtils.log("Invalid Web Profile.");
            return false;
        }

        web.switchBrowser(NEXIAL_WEBMAIL_TEMPORARYMAIL_BROWSER_PROFILE, NEXIAL_WEBMAIL_BROWSER_CONFIG);
        String contextName = StringUtils.joinWith(CONTEXT_VARIABLE_SEPARATOR, CONTEXT_VARIABLE_PREFIX,
                                                  webMailProfile.getProfileName(), id);

        String url = StringUtils.join(TEMPORARY_MAIL_URL, id);
        String maxLoadTime = getMaxLoadTime(web.getContext());
        web.openAndWait(url, maxLoadTime);

        if (web.assertElementPresent(XPATH_DELETE_EMAIL).failed()) {
            ConsoleUtils.log(String.format("There is no email with Id %s", id));
            return false;
        }
        web.clickAndWait(XPATH_DELETE_EMAIL, maxLoadTime);

        web.getContext().removeData(contextName);
        return true;
    }
}
