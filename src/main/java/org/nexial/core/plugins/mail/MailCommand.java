/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.mail;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.nexial.commons.javamail.MailObjectSupport;
import org.nexial.commons.javamail.MailSender;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;

import javax.mail.MessagingException;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.nexial.core.NexialConst.Project.SCRIPT_FILE_EXT;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

/**
 * Command for email composition and dispatching it.
 *
 * @author Dhanapathi Marepalli
 */
public class MailCommand extends BaseCommand {
    public static final String SUBJECT = "set-subject";
    public static final String BODY = "add-body";
    public static final String ATTACH = "add-attachment";
    public static final String TO = "add-to";
    public static final String CC = "add-cc";
    public static final String BCC = "add-bcc";
    public static final String EMAIL_VARIABLE_PREFIX = "nexial.email";
    private static final String ATTACHMENT_SEPARATOR = "=";
    private static final String EMAIL_SEPARATOR = ",";
    private static final Set<String> EMAIL_ACTIONS = Stream.of(SUBJECT, TO, CC, BCC, BODY, ATTACH)
            .collect(Collectors.toCollection(HashSet::new));
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L; // 10MB.

    @Override
    public String getTarget() {
        return "mail";
    }

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
    }

    /**
     * This command refers to various steps involved in composing the email like adding the Subject of email,
     * adding the TO recipients, CC recipients, BCC recipients, add Attachments to the email, add the Body to
     * the email etc. In this command the var refers to the variable containing the name associated to the email.
     * The action specifies what to perform. For example attach some file to email, attach the screen shot to the email,
     * add the subject to the email etc. The value is associated to the action associated for example the path of the
     * file attachment, subject of the email etc. based on the action to be performed. Once the basic implementation
     * is done features like adding the email priority will be added to the email. The command is flexible such that we
     * can update any property of the email. Also we can reuse the same var set as part of the command to configure
     * various emails.
     *
     * @param var    the variable name containing the email information.
     * @param action the action associated as part of email composition.
     *               This is one among the {@link MailCommand#EMAIL_ACTIONS}.
     * @param value  the value to be used in regard to the action associated.
     * @return {@link StepResult#success(String)} displaying the email composition details set so far.
     */
    public StepResult composeMail(final String var, final String action, final String value) {
        requiresNotBlank(value, "Value cannot be empty", value);
        if (!EMAIL_ACTIONS.contains(action)) {
            return StepResult.fail("Action `" + action + "` is not a valid email action.");
        }

        String variableName = getContextVariable(var);

        Object mailContext = context.getObjectData(variableName);
        EmailSettings emailSettings;

        if (mailContext == null) {
            emailSettings = new EmailSettings();
        } else if (mailContext instanceof EmailSettings) {
            emailSettings = (EmailSettings) mailContext;
        } else {
            return StepResult.fail("Variable with the name `" + var + "` is not a valid email configuration.");
        }

        if (StringUtils.isNotEmpty(emailSettings.getFailure())) {
            return StepResult.fail("Ignoring this step as the Email composition failed earlier.");
        }

        context.setData(variableName, emailSettings);
        return performAction(action, value.trim(), emailSettings);
    }

    /**
     * Create the variable name.
     *
     * @param var name used in the script.
     * @return Generated variable name.
     */
    private String getContextVariable(@NotNull final String var) {
        ExecutionDefinition execDef = context.getExecDef();
        int planSequence = execDef.getPlanSequence();
        String scriptName = StringUtils.removeEndIgnoreCase(new File(execDef.getTestScript()).getName(),
                SCRIPT_FILE_EXT);
        String scenarioName = context.getCurrentScenario();
        String iteration = execDef.getTestData().getIteration();
        return StringUtils.joinWith(".",
                EMAIL_VARIABLE_PREFIX, String.valueOf(planSequence),
                scriptName, scenarioName, iteration, var);
    }

    /**
     * This command is used to send the email based on the profile settings as well as the configurations set as part of
     * {@link MailCommand#composeMail(String, String, String)} related var.
     *
     * @param profile mail profile set as part of data file.
     * @param var     name of the mail configuration variable.
     * @return {@link StepResult} stating whether the email is sent successfully or failed due to some reason.
     */
    public StepResult send(final String profile, final String var) {
        requiresNotBlank(profile, "Invalid profile", profile);

        EmailSettings emailSettings;
        Object objectData = context.getObjectData(getContextVariable(var));

        if (objectData instanceof EmailSettings) {
            emailSettings = (EmailSettings) objectData;
        } else {
            return StepResult.fail("Invalid email configuration variable " + var + ".");
        }

        if (StringUtils.isNotEmpty(emailSettings.getFailure())) {
            return StepResult.fail("Ignoring this step as the Email composition failed earlier.");
        }

        String validationErrorMessage = validateEmailSettings(emailSettings);
        if (StringUtils.isNotEmpty(validationErrorMessage)) {
            return StepResult.fail(validationErrorMessage);
        }

        MailProfile mailProfile = MailProfile.newInstance(profile);
        ConsoleUtils.log("Profile is " + profile);

        if (mailProfile != null) {
            MailObjectSupport mailer = new MailObjectSupport();
            return sendEmail(profile, emailSettings, mailProfile, mailer);
        }

        return StepResult.fail("Emails profile cannot be empty.");
    }

    /**
     * Command to delete the email configuration variable passed in as `var`. The variable will be deleted in case if it exists.
     * If not it will give a failure message.
     *
     * @param var the name of the mail configuration variable.
     * @return {@link StepResult} based on whether the variable
     * exists or not.
     */
    public StepResult clearMail(final String var) {
        String variable = getContextVariable(var);
        Object object = context.getObjectData(variable);

        if (object instanceof EmailSettings) {
            context.removeData(variable);
            return StepResult.success("Email configuration with the variable name: `" + var + "` is removed.");
        }
        return StepResult.fail("There is no email configuration variable with the name: `" + var + "`.");
    }

    /**
     * Perform the appropriate email action.
     *
     * @param action        the email action performed i.e. one of the actions in {@link MailCommand#EMAIL_ACTIONS}.
     * @param value         the value passed to the action.
     * @param emailSettings {@link EmailSettings}.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on if the action successful
     * or any of the validations failed.
     */
    private StepResult performAction(@NotNull final String action,
                                     @NotNull final String value,
                                     @NotNull final EmailSettings emailSettings) {
        switch (action) {
            case SUBJECT:
                emailSettings.setSubject(value);
                return StepResult.success(emailSettings.toString());

            case BODY:
                String body = StringUtils.defaultString(emailSettings.getBody()).concat(value);
                emailSettings.setBody(body);
                return StepResult.success(emailSettings.toString());

            case TO:
                return configureMailRecipients(value, emailSettings, RECIPIENT_TYPE.TO) ?
                        StepResult.success(emailSettings.toString()) : StepResult.fail(emailSettings.getFailure());

            case CC:
                return configureMailRecipients(value, emailSettings, RECIPIENT_TYPE.CC) ?
                        StepResult.success(emailSettings.toString()) : StepResult.fail(emailSettings.getFailure());

            case BCC:
                return configureMailRecipients(value, emailSettings, RECIPIENT_TYPE.BCC) ?
                        StepResult.success(emailSettings.toString()) : StepResult.fail(emailSettings.getFailure());

            case ATTACH:
                return addAttachments(value.trim(), emailSettings) ?
                        StepResult.success(emailSettings.toString()) : StepResult.fail(emailSettings.getFailure());

            default:
                String message = "Invalid action " + action + ".";
                emailSettings.setFailure(message);
                return StepResult.fail(message);
        }

    }

    /**
     * Add attachments to the email.
     *
     * @param value         list of attachments separated by DELIMETER.
     * @param emailSettings {@link EmailSettings} passed in.
     * @return true/false based on whether the attachments added successfully or not.
     */
    private boolean addAttachments(@NotNull final String value, @NotNull final EmailSettings emailSettings) {
        String[] filePaths = StringUtils.split(value, context.getTextDelim());
        List<String> invalidFilePaths = new ArrayList<>();
        Map<String, File> fileAttachments = new HashMap<>();

        for (String filePath : filePaths) {
            filePath = filePath.trim();
            File file;
            if (filePath.contains(ATTACHMENT_SEPARATOR)) {
                String[] attachmentPartitions = filePath.split(ATTACHMENT_SEPARATOR);
                file = new File(attachmentPartitions[1]);
                fileAttachments.put(attachmentPartitions[0], file);
            } else {
                file = new File(filePath);
                fileAttachments.put(file.getName(), file);
            }

            if (!file.exists()) {
                invalidFilePaths.add(filePath);
            }
        }

        if (CollectionUtils.isNotEmpty(invalidFilePaths)) {
            String message = "The following file(s) are not valid " + invalidFilePaths + ".";
            emailSettings.setFailure(message);
            return false;
        }

        if (MapUtils.isNotEmpty(emailSettings.getAttachments())) {
            emailSettings.getAttachments().putAll(fileAttachments);
        } else {
            emailSettings.setAttachments(fileAttachments);
        }

        long filesSize = emailSettings.getAttachments().values()
                .stream().map(File::length).reduce(0L, Long::sum);
        if (filesSize > MAX_FILE_SIZE) {
            String message = "Files size attached is greater than max size of 10MB.";
            emailSettings.setFailure(message);
            return false;
        }

        return true;
    }

    /**
     * Configures the email recipients based on the Recipient types. Checks if there are any failures already in the
     * mail composition. Also checks if there are any emails which are not passing the
     * {@link MailCommand#isNotValidEmail(String)}. If all the validations are passed then the emails will be configured
     * accordingly.
     *
     * @param value         the recipients list passed in a String separated by a delimeter.
     * @param emailSettings {@link EmailSettings} passed in.
     * @param recipientType recipient type passed in.
     * @return true/false based on whether the validations passed and configurations set properly or not.
     */
    private boolean configureMailRecipients(@NotNull final String value, @NotNull final EmailSettings emailSettings,
                                            @NotNull final RECIPIENT_TYPE recipientType) {
        List<String> emails = Arrays.stream(StringUtils.split(value, EMAIL_SEPARATOR))
                .map(String::trim).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(emails)) {
            String message = "The " + recipientType.name() + " recipients cannot be empty.";
            emailSettings.setFailure(message);
            return false;
        }

        List<String> invalidEmails = emails.stream()
                .filter(this::isNotValidEmail).collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(invalidEmails)) {
            String message = "The following " + recipientType.name() + " recipients are invalid: "
                    + invalidEmails + ".";
            emailSettings.setFailure(message);
            return false;
        }

        switch (recipientType) {
            case TO:
                emailSettings.setToRecipients(appendPreviousRecipients(emails, emailSettings.getToRecipients()));
                break;
            case CC:
                emailSettings.setCcRecipients(appendPreviousRecipients(emails, emailSettings.getCcRecipients()));
                break;
            case BCC:
                emailSettings.setBccRecipients(appendPreviousRecipients(emails, emailSettings.getBccRecipients()));
                break;
        }

        return true;
    }

    /**
     * Add the existing recipients to the current recipients.
     *
     * @param emails     existing list of recipients
     * @param recipients new list of recipients
     * @return {@link List<String>} containing all the list of recipients.
     */
    private List<String> appendPreviousRecipients(final List<String> emails, final List<String> recipients) {
        if (CollectionUtils.isNotEmpty(recipients)) {
            emails.addAll(recipients);
        }
        return emails;
    }

    /**
     * Send the email based on the mailProfile passed.
     *
     * @param profile       the profile variable name.
     * @param emailSettings {@link EmailSettings} set as part of the email composition.
     * @param mailProfile   the {@link MailProfile} configurations passed in.
     * @param mailer        the {@link MailObjectSupport}
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on whether email
     * got dispatched or not.
     */
    private StepResult sendEmail(@NotNull final String profile, @NotNull final EmailSettings emailSettings,
                                 @NotNull final MailProfile mailProfile,
                                 @NotNull final MailObjectSupport mailer) {
        mailer.setMailProps(mailProfile.toProperties());
        MailSender sender = MailSender.newInstance(mailer);
        String from = mailProfile.getFrom();

        if (StringUtils.isEmpty(from)) {
            return StepResult.fail("The email profile `" + profile + "` does not contain the `from`" +
                    " address of the email.");
        }

        try {
            sender.sendMail(emailSettings.getToRecipients(),
                    emailSettings.getCcRecipients(),
                    emailSettings.getBccRecipients(), from, emailSettings.getSubject(),
                    emailSettings.getBody(), emailSettings.getAttachments());
        } catch (MessagingException e) {
            return StepResult.fail("Email failed to get delivered. Error is " + e.getMessage());
        }
        return StepResult.success("Email dispatched successfully.");
    }

    /**
     * Checks whether the email is valid or not.
     *
     * @param address email address passed in.
     * @return true/false based on whether the email is valid or not.
     */
    private boolean isNotValidEmail(final String address) {
        return !StringUtils.isNotBlank(address) || !EmailValidator.getInstance().isValid(address.trim());
    }

    /**
     * Checks if there are any validation errors in terms of the email settings that are set during the email
     * composition.
     *
     * @param emailSettings the email settings set as part of mail composition.
     * @return Failure message in case of validation failure and if not {@link StringUtils#EMPTY}.
     */
    private String validateEmailSettings(@NotNull final EmailSettings emailSettings) {
        if (StringUtils.isEmpty(emailSettings.getBody())) {
            return "Email should have a body.";
        }

        if (StringUtils.isEmpty(emailSettings.getSubject())) {
            return "Email should have a Subject.";
        }

        if (CollectionUtils.isEmpty(emailSettings.getToRecipients())) {
            return "There should be at least one recipient to send the email.";
        }

        if (StringUtils.isNotEmpty(emailSettings.getFailure())) {
            return "Email cannot be dispatched as there were errors while composing email.";
        }

        return StringUtils.EMPTY;
    }

    private enum RECIPIENT_TYPE {
        TO, CC, BCC
    }
}
