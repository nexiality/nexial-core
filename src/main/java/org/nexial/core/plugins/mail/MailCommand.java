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

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.mail.MessagingException;
import javax.validation.constraints.NotNull;

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
import org.nexial.core.utils.OutputResolver;

import static org.nexial.core.NexialConst.Iteration.CURR_ITERATION_ID;
import static org.nexial.core.NexialConst.Project.SCRIPT_FILE_EXT;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

/**
 * Command for email composition and dispatching it.
 *
 * @author Dhanapathi Marepalli
 */
public class MailCommand extends BaseCommand {
    private static final String VAR_NAMESPACE = "nexial.email";
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";
    private static final String ATTACH = "attachment";
    private static final String TO = "to";
    private static final String CC = "cc";
    private static final String BCC = "bcc";
    private static final Set<String> CONFIGS = Stream.of(SUBJECT, TO, CC, BCC, BODY, ATTACH)
                                                     .collect(Collectors.toCollection(HashSet::new));
    private static final String ATTACHMENT_DELIM = "=";
    private static final String EMAIL_DELIM = ",";
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L; // 10MB.

    // todo: support reply-to
    private enum Recipients {TO, CC, BCC}

    @Override
    public String getTarget() { return "mail"; }

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
    }

    /**
     * Sends an email based on the properties set.
     *
     * @param profile name of the email profile.
     * @param to      the TO recipient.
     * @param subject the subject of the email.
     * @param body    email body.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)}
     */
    public StepResult send(String profile, String to, String subject, String body) {
        requiresNotBlank(profile, "Invalid profile", profile);
        requiresNotBlank(to, "Invalid recipient", to);
        requiresNotBlank(subject, "Invalid subject", subject);
        requiresNotBlank(body, "Invalid email body", body);

        MailProfile settings = MailProfile.newInstance(profile);
        requiresNotNull(settings, "Unable to derive email connectivity from profile '" + profile + "'");

        MailObjectSupport mailer = new MailObjectSupport();
        mailer.setMailProps(settings.toProperties());
        MailSender sender = MailSender.newInstance(mailer);
        String from = settings.getFrom();
        String[] recipients = StringUtils.split(StringUtils.replace(to, ";", ","), ",");

        try {
            // body = OutputFileUtils.resolveContent(body, context, false, true);
            body = new OutputResolver(body, context).getContent();
            sender.sendMail(recipients, from, subject, body);
            return StepResult.success("email successfully sent");
        } catch (Throwable e) {
            return StepResult.fail("email unsuccessful to be sent: " + e.getMessage());
        }
    }

    /**
     * Check if the email address is valid or not.
     *
     * @param address email address passed in.
     * @return true/false based on whether the email address is valid or not.
     */
    protected boolean isValidEmail(String address) {
        return StringUtils.isNotBlank(address) && EmailValidator.getInstance().isValid(address.trim());
    }

    /**
     * This command refers to various steps involved in composing the email like adding the Subject of email,
     * adding the TO recipients, CC recipients, BCC recipients, add Attachments to the email, add the Body to
     * the email etc. In this command the var refers to the variable containing the name associated to the email.
     * The config specifies what to perform. For example attach some file to email, attach the screen shot to the email,
     * add the subject to the email etc. The value is associated to the config associated for example the path of the
     * file attachment, subject of the email etc. based on the config to be performed. Once the basic implementation
     * is done features like adding the email priority will be added to the email. The command is flexible such that we
     * can update any property of the email. Also we can reuse the same var set as part of the command to configure
     * various emails.
     *
     * @param var    the variable name containing the email information.
     * @param config the config associated as part of email composition.
     *               This is one among the {@link MailCommand#CONFIGS}.
     * @param value  the value to be used in regard to the config associated.
     * @return {@link StepResult#success(String)} displaying the email composition details set so far.
     */
    public StepResult compose(final String var, final String config, final String value) {
        if (!CONFIGS.contains(config)) { return StepResult.fail("Config '" + config + "' is not valid."); }

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

        context.setData(variableName, emailSettings);
        return setConfiguration(config, value.trim(), emailSettings);
    }

    /**
     * Create the variable name.
     *
     * @param var name used in the script.
     * @return Generated variable name.
     */
    private String getContextVariable(@NotNull final String var) {
        ExecutionDefinition execDef = context.getExecDef();
        return StringUtils.joinWith(
            ".",
            VAR_NAMESPACE,
            String.valueOf(execDef.getPlanSequence()),
            StringUtils.removeEndIgnoreCase(new File(execDef.getTestScript()).getName(), SCRIPT_FILE_EXT),
            context.getCurrentScenario(),
            context.getStringData(CURR_ITERATION_ID),
            var);
    }

    /**
     * This command is used to send the email based on the profile settings as well as the configurations set as part of
     * {@link MailCommand#compose(String, String, String)} related var.
     *
     * @param profile mail profile set as part of data file.
     * @param var     name of the mail configuration variable.
     * @return {@link StepResult} stating whether the email is sent successfully or failed due to some reason.
     */
    public StepResult sendComposed(final String profile, final String var) {
        requiresNotBlank(profile, "Invalid profile", profile);

        Object objectData = context.getObjectData(getContextVariable(var));

        if (!(objectData instanceof EmailSettings)) {
            return StepResult.fail("Invalid configuration via '" + var + "'");
        }

        EmailSettings emailSettings = (EmailSettings) objectData;

        if (MapUtils.isNotEmpty(emailSettings.getFailures())) {
            return StepResult.fail("Unable to send email; " + emailSettings.displayFailures());
        }

        String validationErrorMessage = validateEmailSettings(emailSettings);
        if (StringUtils.isNotEmpty(validationErrorMessage)) { return StepResult.fail(validationErrorMessage); }

        MailProfile mailProfile = MailProfile.newInstance(profile);
        if (mailProfile == null) { return StepResult.fail("Invalid profile '" + profile + "'"); }

        return sendEmail(profile, emailSettings, mailProfile, new MailObjectSupport());
    }

    /**
     * Command to delete the email configuration variable passed in as `var`. The variable will be deleted in case if it exists.
     * If not it will give a failure message.
     *
     * @param var the name of the mail configuration variable.
     * @return {@link StepResult} based on whether the variable exists or not.
     */
    public StepResult clearComposed(final String var) {
        String variable = getContextVariable(var);
        Object object = context.getObjectData(variable);

        if (object instanceof EmailSettings) {
            context.removeData(variable);
            return StepResult.success("Email configuration with the variable name: `" + var + "` is removed.");
        }
        return StepResult.fail("There is no email configuration variable with the name: `" + var + "`.");
    }

    /**
     * Perform the appropriate email config.
     *
     * @param config        the email config set i.e. one of the configs in {@link MailCommand#CONFIGS}.
     * @param value         the value passed to the config.
     * @param emailSettings {@link EmailSettings}.
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on if the config successful
     *     or any of the validations failed.
     */
    private StepResult setConfiguration(@NotNull final String config,
                                        @NotNull final String value,
                                        @NotNull final EmailSettings emailSettings) {
        // todo: support content type
        // todo: support reply-to

        boolean valueReset = context.isNullOrEmptyValue(value);
        switch (config) {
            case SUBJECT:
                emailSettings.clearFailure(SUBJECT);
                if (valueReset) {
                    emailSettings.setSubject(null);
                    return StepResult.success(emailSettings.toString());
                }

                emailSettings.setSubject(value);
                return StepResult.success(emailSettings.toString());

            case BODY:
                emailSettings.clearFailure(BODY);
                if (valueReset) {
                    emailSettings.setBody(null);
                    return StepResult.success(emailSettings.toString());
                }

                emailSettings.setBody(new OutputResolver(value, context).getContent());
                return StepResult.success(emailSettings.toString());

            case TO:
                emailSettings.clearFailure(TO);
                if (valueReset) {
                    emailSettings.setToRecipients(null);
                    return StepResult.success(emailSettings.toString());
                }

                configureMailRecipients(value, emailSettings, Recipients.TO);
                return StepResult.success(emailSettings.toString());

            case CC:
                emailSettings.clearFailure(CC);
                if (valueReset) {
                    emailSettings.setCcRecipients(null);
                    return StepResult.success(emailSettings.toString());
                }

                configureMailRecipients(value, emailSettings, Recipients.CC);
                return StepResult.success(emailSettings.toString());

            case BCC:
                emailSettings.clearFailure(BCC);
                if (valueReset) {
                    emailSettings.setBccRecipients(null);
                    return StepResult.success(emailSettings.toString());
                }

                configureMailRecipients(value, emailSettings, Recipients.BCC);
                return StepResult.success(emailSettings.toString());

            case ATTACH:
                emailSettings.clearFailure(ATTACH);
                if (valueReset) {
                    emailSettings.setAttachments(null);
                    return StepResult.success(emailSettings.toString());
                }

                addAttachments(value.trim(), emailSettings);
                return StepResult.success(emailSettings.toString());

            default:
                String message = "Invalid config " + config + ".";
                emailSettings.setFailure(config, message);
                return StepResult.fail(message);
        }

    }

    /**
     * Add attachments to the email.
     *
     * @param value         list of attachments separated by {@link #ATTACHMENT_DELIM}
     * @param emailSettings {@link EmailSettings} passed in.
     */
    private void addAttachments(@NotNull final String value, @NotNull final EmailSettings emailSettings) {
        emailSettings.setAttachments(null);
        String[] filePaths = StringUtils.split(value, context.getTextDelim());
        if (filePaths.length == 0) { return; }

        List<String> invalidFilePaths = new ArrayList<>();
        Map<String, File> fileAttachments = new HashMap<>();

        for (String filePath : filePaths) {
            filePath = filePath.trim();
            File file;
            if (filePath.contains(ATTACHMENT_DELIM)) {
                String[] attachmentPartitions = filePath.split(ATTACHMENT_DELIM);
                file = new File(attachmentPartitions[1]);
                fileAttachments.put(attachmentPartitions[0], file);
            } else {
                file = new File(filePath);
                fileAttachments.put(file.getName(), file);
            }

            if (!file.exists()) { invalidFilePaths.add(filePath); }
        }

        if (CollectionUtils.isNotEmpty(invalidFilePaths)) {
            String message = "The following file(s) are not valid " + invalidFilePaths + ".";
            emailSettings.setFailure(ATTACH, message);
        }

        emailSettings.setAttachments(fileAttachments);

        long filesSize = emailSettings.getAttachments().values().stream().map(File::length).reduce(0L, Long::sum);
        if (filesSize > MAX_FILE_SIZE) {
            emailSettings.setFailure(ATTACH, "Files size attached is greater than max size of 10MB.");
        }

    }

    /**
     * Configures the email recipients based on the Recipient types. Checks if there are any failures already in the
     * mail composition. Also checks if there are any emails which are not passing the
     * {@link MailCommand#isValidEmail(String)}. If all the validations are passed then the emails will be configured
     * accordingly.
     *
     * @param value         the recipients list passed in a String separated by a {@link #EMAIL_DELIM}
     * @param emailSettings {@link EmailSettings} passed in.
     * @param recipientType recipient type passed in.
     */
    private void configureMailRecipients(@NotNull final String value,
                                         @NotNull final EmailSettings emailSettings,
                                         @NotNull final Recipients recipientType) {
        // ignore empty email
        if (StringUtils.isEmpty(value)) { return; }

        List<String> emails = Arrays.stream(StringUtils.split(value, EMAIL_DELIM))
                                    .map(String::trim)
                                    .collect(Collectors.toList());

        String config = recipientType.name().toLowerCase();
        if (CollectionUtils.isEmpty(emails)) {
            emailSettings.setFailure(config, "Invalid email address(es) for " + recipientType.name() + " recipients.");
        }

        List<String> invalidEmails = emails.stream()
                                           .filter(address -> !isValidEmail(address)).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(invalidEmails)) {
            emailSettings.setFailure(config, "The invalid " + recipientType.name() + " recipients: " + invalidEmails);
        }

        switch (recipientType) {
            case TO:
                emailSettings.setToRecipients(emails);
                break;
            case CC:
                emailSettings.setCcRecipients(emails);
                break;
            case BCC:
                emailSettings.setBccRecipients(emails);
                break;
        }
    }

    /**
     * Send the email based on the mailProfile passed.
     *
     * @param profile       the profile variable name.
     * @param emailSettings {@link EmailSettings} set as part of the email composition.
     * @param mailProfile   the {@link MailProfile} configurations passed in.
     * @param mailer        the {@link MailObjectSupport}
     * @return {@link StepResult#success(String)} or {@link StepResult#fail(String)} based on whether email
     *     got dispatched or not.
     */
    private StepResult sendEmail(@NotNull final String profile,
                                 @NotNull final EmailSettings emailSettings,
                                 @NotNull final MailProfile mailProfile,
                                 @NotNull final MailObjectSupport mailer) {
        mailer.setMailProps(mailProfile.toProperties());
        MailSender sender = MailSender.newInstance(mailer);
        String from = mailProfile.getFrom();

        if (StringUtils.isEmpty(from)) {
            return StepResult.fail("The profile '" + profile + "' does not contain the 'from' address.");
        }

        try {
            sender.sendMail(emailSettings.getToRecipients(),
                            emailSettings.getCcRecipients(),
                            emailSettings.getBccRecipients(),
                            from, emailSettings.getSubject(),
                            emailSettings.getBody(),
                            emailSettings.getAttachments());
        } catch (MessagingException e) {
            return StepResult.fail("Email failed to get delivered. Error is " + e.getMessage());
        }

        return StepResult.success("Email dispatched successfully.");
    }

    /**
     * Checks if there are any validation errors in terms of the email settings that are set during the email
     * composition.
     *
     * @param emailSettings the email settings set as part of mail composition.
     * @return Failure message in case of validation failure and if not {@link StringUtils#EMPTY}.
     */
    private String validateEmailSettings(@NotNull final EmailSettings emailSettings) {
        if (StringUtils.isEmpty(emailSettings.getBody())) { return "No email body found."; }
        if (StringUtils.isEmpty(emailSettings.getSubject())) { return "No email subject found."; }
        if (CollectionUtils.isEmpty(emailSettings.getToRecipients())) { return "No email recipients configured."; }
        if (MapUtils.isNotEmpty(emailSettings.getFailures())) { return emailSettings.getFailures().toString(); }
        return StringUtils.EMPTY;
    }
}
