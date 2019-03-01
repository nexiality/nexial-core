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
 */

package org.nexial.core.mail;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.javamail.MailObjectSupport;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.aws.SesSupport;
import org.nexial.core.aws.SesSupport.SesConfig;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.plugins.aws.AwsSesSettings;
import org.nexial.core.reports.ExecutionMailConfig;
import org.nexial.core.reports.ExecutionNotifier;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.sun.mail.smtp.SMTPTransport;

import static javax.mail.Message.RecipientType.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Mailer.MAIL_KEY_CONTENT_TYPE;
import static org.nexial.core.NexialConst.Mailer.MAIL_KEY_FROM;
import static org.nexial.core.SystemVariables.getDefault;

public class NexialMailer implements ExecutionNotifier {
    private ExecutionContext context;
    private MailObjectSupport mailSupport;
    private TemplateEngine mailTemplateEngine;
    private String mailTemplate;

    public void setContext(ExecutionContext context) { this.context = context; }

    public void setMailTemplateEngine(TemplateEngine mailTemplateEngine) {this.mailTemplateEngine = mailTemplateEngine;}

    public void setMailTemplate(String mailTemplate) { this.mailTemplate = mailTemplate; }

    public void sendMail(MailData data) throws MessagingException {
        // List<String> recipients, String subject, String content, boolean plaintext
        if (!validateMailData(data)) { return; }

        // off we go
        ConsoleUtils.log("Sending email to " + data.getToAddr());
        if (mailSupport.hasSmtpConfigs() || mailSupport.hasJndiConfigs()) { smtpSendEmail(data);}
        if (mailSupport.hasSesSettings()) { sesSendEmail(data); }
    }

    @Override
    public void notify(ExecutionSummary summary) throws IOException {
        if (summary == null) {
            ConsoleUtils.log("No execution summary found.. skipping mail notification");
            return;
        }

        if (CollectionUtils.isEmpty(summary.getNestedExecutions())) {
            ConsoleUtils.log("No result files found... skipping mail notification");
            return;
        }

        boolean enableEmail = BooleanUtils.toBoolean(context != null ?
                                                     context.getStringData(ENABLE_EMAIL, getDefault(ENABLE_EMAIL)) :
                                                     System.getProperty(ENABLE_EMAIL, getDefault(ENABLE_EMAIL)));
        if (!enableEmail) {
            ConsoleUtils.log("email notification is currently not enabled; SKIPPING...");
            return;
        }

        // print to console - last SOS attempt
        if (summary.getError() != null) { summary.getError().printStackTrace(); }

        if (!readyToSend()) {
            ConsoleUtils.log("nexial mailer is not properly configured; SKIPPING...");
            return;
        }

        // unique set of test names
        StringBuilder subject = new StringBuilder("Execution Result for ");
        Set<String> nestedNames = new HashSet<>();
        summary.getNestedExecutions().forEach(nested -> nestedNames.add(nested.getName()));
        nestedNames.forEach(name -> subject.append(name).append(", "));

        Context engineContext = new Context();
        engineContext.setVariable("summary", summary);

        MailData mailData =
            MailData.newInstance(MIME_HTML)
                    .setToAddr(resolveRecipients())
                    .setSubject(MAIL_RESULT_SUBJECT_PREFIX + StringUtils.removeEnd(subject.toString(), ", "))
                    .setContent(mailTemplateEngine.process(mailTemplate, engineContext));

        try {
            sendMail(mailData);
            // sendResult(recipients, content, StringUtils.removeEnd(subject.toString(), ", "));
        } catch (MessagingException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public void sendCidContent(List<String> recipients, String subject, String content, Map<String, File> images)
        throws MessagingException {
        if (!readyToSend()) {
            ConsoleUtils.log("nexial mailer not properly configured to send email");
            return;
        }

        Session session = mailSupport.getSession();
        SMTPTransport transport = mailSupport.createTransport(session);

        Message msg = prepMessage(session, subject);

        try {
            Multipart mp = new MimeMultipart("related");

            // content
            MimeBodyPart part = new MimeBodyPart();
            part.setContent(content, mailSupport.getConfiguredProperty(MAIL_KEY_CONTENT_TYPE));
            mp.addBodyPart(part);

            if (MapUtils.isNotEmpty(images)) {
                for (String cid : images.keySet()) {
                    MimeBodyPart imagePart = new MimeBodyPart();
                    File image = images.get(cid);
                    DataSource fds = new FileDataSource(image);
                    imagePart.setDataHandler(new DataHandler(fds));
                    imagePart.setHeader("Content-ID", "<" + cid + ">");
                    String imageType = StringUtils.lowerCase(StringUtils.substringAfterLast(image.getName(), "."));
                    imagePart.setHeader("Content-type",
                                        "image/" + imageType + "; name=\"" + cid + "." + imageType + "\"");
                    imagePart.setHeader("Content-disposition",
                                        "attachment; filename=\"" + cid + "." + imageType + "\"");
                    mp.addBodyPart(imagePart);
                }
            }

            // add to, cc, bcc, from
            for (String addr : recipients) { msg.addRecipient(TO, new InternetAddress(addr)); }

            // final steps
            msg.setContent(mp);
            msg.setSentDate(new Date());
            msg.saveChanges();

            // send it!
            transport.sendMessage(msg, msg.getAllRecipients());
        } finally {
            transport.close();
        }
    }

    protected void smtpSendEmail(MailData data) throws MessagingException {

        Session session = mailSupport.getSession();
        SMTPTransport transport = mailSupport.createTransport(session);

        Message msg = prepMessage(session, data.getSubject());

        try {
            // add to, cc, bcc, from
            if (CollectionUtils.isNotEmpty(data.getToAddr())) {
                for (String to : data.getToAddr()) { msg.addRecipient(TO, new InternetAddress(to)); }
            }
            if (CollectionUtils.isNotEmpty(data.getCcAddr())) {
                for (String cc : data.getCcAddr()) { msg.addRecipient(CC, new InternetAddress(cc)); }
            }
            if (CollectionUtils.isNotEmpty(data.getBccAddr())) {
                for (String bcc : data.getBccAddr()) { msg.addRecipient(BCC, new InternetAddress(bcc)); }
            }
            if (StringUtils.isNotBlank(data.getFromAddr())) { msg.setFrom(new InternetAddress(data.getFromAddr())); }

            // content
            MimeBodyPart part = new MimeBodyPart();
            String contentType = StringUtils.defaultIfBlank(data.getMimeType(),
                                                            mailSupport.getConfiguredProperty(MAIL_KEY_CONTENT_TYPE));
            part.setContent(data.getContent(), contentType);

            Multipart mp = new MimeMultipart();
            mp.addBodyPart(part);

            // attachments
            if (CollectionUtils.isNotEmpty(data.getAttachments())) { addAttachment(mp, data.getAttachments()); }

            // final steps
            msg.setContent(mp);
            msg.setSentDate(new Date());
            msg.saveChanges();

            // send it!
            transport.sendMessage(msg, msg.getAllRecipients());
        } finally {
            transport.close();
        }
    }

    protected void sesSendEmail(MailData data) {
        AwsSesSettings sesSettings = mailSupport.getSesSettings();

        SesConfig sesConfig = SesSupport.Companion.newConfig();
        sesConfig.setSubject(data.getSubject());

        String mimeType = data.getMimeType();
        if (StringUtils.equals(mimeType, MIME_PLAIN)) {
            sesConfig.setPlainText(data.getContent());
        } else {
            sesConfig.setHtml(data.getContent());
        }

        sesConfig.setTo(data.getToAddr());
        sesConfig.setFrom(StringUtils.defaultIfBlank(data.getFromAddr(), sesSettings.getFrom()));

        if (CollectionUtils.isNotEmpty(data.getReplyToAddr())) {
            sesConfig.setReplyTo(data.getReplyToAddr());
        } else {
            String replyTo = sesSettings.getReplyTo();
            if (StringUtils.isNotBlank(replyTo)) { sesConfig.setReplyTo(TextUtils.toList(replyTo, ",", true)); }
        }

        if (CollectionUtils.isNotEmpty(data.getCcAddr())) {
            sesConfig.setCc(data.getCcAddr());
        } else {
            String cc = sesSettings.getCc();
            if (StringUtils.isNotBlank(cc)) { sesConfig.setCc(TextUtils.toList(cc, ",", true)); }
        }

        if (CollectionUtils.isNotEmpty(data.getBccAddr())) {
            sesConfig.setBcc(data.getBccAddr());
        } else {
            String bcc = sesSettings.getBcc();
            if (StringUtils.isNotBlank(bcc)) { sesConfig.setBcc(TextUtils.toList(bcc, ",", true)); }
        }

        // if (CollectionUtils.isNotEmpty(data.getAttachments())) { sesConfig.setAttachments(data.getAttachments()); }

        String configSet = sesSettings.getConfigurationSetName();
        if (StringUtils.isNotBlank(configSet)) { sesConfig.setConfigurationSetName(configSet); }

        if (data.isFooter()) {
            String xmailer = sesSettings.getXmailer();
            if (StringUtils.isNotBlank(xmailer)) { sesConfig.setXmailer(xmailer); }
        }

        SesSupport ses = new SesSupport();
        ses.setAccessKey(sesSettings.getAccessKey());
        ses.setSecretKey(sesSettings.getSecretKey());
        ses.setRegion(sesSettings.getRegion());
        ses.setAssumeRoleArn(sesSettings.getAssumeRoleArn());
        ses.setAssumeRoleSession(sesSettings.getAssumeRoleSession());
        ses.setAssumeRoleDuration(sesSettings.getAssumeRoleDuration());

        ses.sendMail(sesConfig);
        try { Thread.sleep(2000);} catch (InterruptedException e) { }
    }

    protected List<String> resolveRecipients() {
        String recipients = context != null ? context.getStringData(MAIL_TO) : System.getProperty(MAIL_TO);

        if (StringUtils.isBlank(recipients)) {
            recipients = context != null ? context.getStringData(MAIL_TO2) : System.getProperty(MAIL_TO2);
        }

        if (StringUtils.isBlank(recipients)) {
            return mailSupport != null ? mailSupport.getRecipients() : null;
        } else {
            return TextUtils.toList(StringUtils.replace(recipients, ";", ","), ",", true);
        }
    }

    private boolean validateMailData(MailData data) {
        if (CollectionUtils.isEmpty(data.getToAddr())) {
            ConsoleUtils.log("No email to send since no recipient address provided.");
            return false;
        }

        if (StringUtils.isBlank(data.getContent())) {
            ConsoleUtils.log("No email to send since content provided.");
            return false;
        }

        if (!readyToSend()) {
            ConsoleUtils.log("nexial mailer is not properly configured; SKIPPING");
            return false;
        }

        // default subject
        if (StringUtils.isBlank(data.getSubject())) { data.setSubject(MAIL_RESULT_SUBJECT_PREFIX + "You've got mail"); }

        return true;
    }

    private boolean readyToSend() {
        if (mailSupport == null) {
            ExecutionMailConfig mailConfig = ObjectUtils.defaultIfNull(ExecutionMailConfig.get(),
                                                                       ExecutionMailConfig.configure(context));
            if (!mailConfig.isReadyForNotification()) {
                ConsoleUtils.log("nexial mailer not configured for notification; SKIPPING...");
            } else {
                mailSupport = MailObjectSupport.configure(mailConfig);
            }
        }

        return mailSupport != null &&
               (mailSupport.hasSmtpConfigs() || mailSupport.hasJndiConfigs() || mailSupport.hasSesSettings());
    }

    private static String deriveTestName(List<File> attachments) {
        // since the attachments from the same test session, any of the files should suffice as 'test name'
        if (CollectionUtils.isEmpty(attachments)) { return "Unknown"; }

        File excel = attachments.get(0);
        return StringUtils.substringBeforeLast(excel.getName(), ".");
    }

    private Message prepMessage(Session session, String subject) throws MessagingException {
        String from = mailSupport.getConfiguredProperty(MAIL_KEY_FROM);

        Message msg = new MimeMessage(session);
        msg.addHeader("X-Mailer", ExecUtils.deriveJarManifest());
        msg.addHeader("Disposition-Notification-To", from);
        msg.setFrom(new InternetAddress(from));
        msg.setSubject(subject);
        return msg;
    }

    private void addAttachment(Multipart mp, List<File> attachments)
        throws MessagingException {

        if (CollectionUtils.isNotEmpty(attachments)) {
            for (File file : attachments) {
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.setDataHandler(new DataHandler(new FileDataSource(file)));
                attachment.setFileName(file.getName());
                mp.addBodyPart(attachment);
            }
        }
    }

    // protected void smtpSendEmail(List<String> recipients, String subject, String content, boolean plaintext)
    //     throws MessagingException {
    //
    //     Session session = mailSupport.getSession();
    //     SMTPTransport transport = mailSupport.createTransport(session);
    //
    //     Message msg = prepMessage(session, subject);
    //
    //     try {
    //         // add to, cc, bcc, from
    //         for (String recipient : recipients) { msg.addRecipient(TO, new InternetAddress(recipient)); }
    //
    //         // content
    //         MimeBodyPart part = new MimeBodyPart();
    //         part.setContent(content, plaintext ? "text/plain" : "text/html");
    //
    //         Multipart mp = new MimeMultipart();
    //         mp.addBodyPart(part);
    //
    //         // final steps
    //         msg.setContent(mp);
    //         msg.setSentDate(new Date());
    //         msg.saveChanges();
    //
    //         // send it!
    //         transport.sendMessage(msg, msg.getAllRecipients());
    //     } finally {
    //         transport.close();
    //     }
    // }

    // public void sendResult(List<String> recipients, String content, String testCase) throws MessagingException {
    //     if (!readyToSend()) {
    //         ConsoleUtils.log("nexial mailer not properly configured to send execution result");
    //         return;
    //     }
    //
    //     String subject = MAIL_RESULT_SUBJECT_PREFIX + "Test Result for '" + testCase + "'";
    //
    //     if (mailSupport.hasSmtpConfigs() || mailSupport.hasJndiConfigs()) {
    //         this.smtpSendEmail(recipients, subject, content, null);
    //         return;
    //     }
    //
    //     if (mailSupport.hasSesSettings()) { sesSendResult(recipients, subject, content); }
    // }

    // public void sendResult(List<String> recipients, String content, List<File> attachments) throws MessagingException {
    //     if (!readyToSend()) {
    //         ConsoleUtils.log("nexial mailer not properly configured to send execution result");
    //         return;
    //     }
    //
    //     String subject = MAIL_RESULT_SUBJECT_PREFIX + "Test Result for '" + deriveTestName(attachments) + "'";
    //
    //     if (mailSupport.hasSmtpConfigs() || mailSupport.hasJndiConfigs()) {
    //         this.smtpSendEmail(recipients, subject, content, attachments);
    //         return;
    //     }
    //
    //     if (mailSupport.hasSesSettings()) {
    //         if (CollectionUtils.isNotEmpty(attachments)) {
    //             ConsoleUtils.error("AWS SES mailer currently does not support file attachments");
    //         }
    //         sesSendResult(recipients, subject, content);
    //     }
    // }
    // protected void sesSendResult(List<String> recipients, String subject, String content) {
    //     sesSendEmail(recipients, subject, content, false);
    // }

    // protected void sesSendPlainText(List<String> recipients, String subject, String content) {
    //     sesSendEmail(recipients, subject, content, true);
    // }

    // protected void sesSendEmail(List<String> recipients, String subject, String content, boolean plaintext) {
    //     AwsSesSettings sesSettings = mailSupport.getSesSettings();
    //
    //     SesConfig sesConfig = SesSupport.Companion.newConfig();
    //     sesConfig.setSubject(subject);
    //
    //     if (plaintext) {
    //         sesConfig.setPlainText(content);
    //     } else {
    //         sesConfig.setHtml(content);
    //     }
    //
    //     sesConfig.setTo(recipients);
    //     sesConfig.setFrom(sesSettings.getFrom());
    //
    //     String replyTo = sesSettings.getReplyTo();
    //     if (StringUtils.isNotBlank(replyTo)) { sesConfig.setReplyTo(TextUtils.toList(replyTo, ",", true)); }
    //
    //     String cc = sesSettings.getCc();
    //     if (StringUtils.isNotBlank(cc)) { sesConfig.setCc(TextUtils.toList(cc, ",", true)); }
    //
    //     String bcc = sesSettings.getBcc();
    //     if (StringUtils.isNotBlank(bcc)) { sesConfig.setBcc(TextUtils.toList(bcc, ",", true)); }
    //
    //     String configSet = sesSettings.getConfigurationSetName();
    //     if (StringUtils.isNotBlank(configSet)) { sesConfig.setConfigurationSetName(configSet); }
    //
    //     String xmailer = sesSettings.getXmailer();
    //     if (StringUtils.isNotBlank(xmailer)) { sesConfig.setXmailer(xmailer); }
    //
    //     SesSupport ses = new SesSupport();
    //     ses.setAccessKey(sesSettings.getAccessKey());
    //     ses.setSecretKey(sesSettings.getSecretKey());
    //     ses.setRegion(sesSettings.getRegion());
    //     ses.setAssumeRoleArn(sesSettings.getAssumeRoleArn());
    //     ses.setAssumeRoleSession(sesSettings.getAssumeRoleSession());
    //     ses.setAssumeRoleDuration(sesSettings.getAssumeRoleDuration());
    //
    //     ses.sendMail(sesConfig);
    //     try { Thread.sleep(2000);} catch (InterruptedException e) { }
    // }

}
