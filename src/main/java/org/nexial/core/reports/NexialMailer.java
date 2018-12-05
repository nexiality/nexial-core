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

package org.nexial.core.reports;

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
import org.apache.commons.lang3.ArrayUtils;
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
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.sun.mail.smtp.SMTPTransport;

import static javax.mail.Message.RecipientType.TO;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Mailer.MAIL_KEY_CONTENT_TYPE;
import static org.nexial.core.NexialConst.Mailer.MAIL_KEY_FROM;

public class NexialMailer implements ExecutionNotifier {
    private ExecutionContext context;
    private MailObjectSupport mailSupport;
    private TemplateEngine mailTemplateEngine;
    private String mailTemplate;

    public void setContext(ExecutionContext context) { this.context = context; }

    public void setMailTemplateEngine(TemplateEngine mailTemplateEngine) {this.mailTemplateEngine = mailTemplateEngine;}

    public void setMailTemplate(String mailTemplate) { this.mailTemplate = mailTemplate; }

    public void sendPlainText(List<String> recipients, String subject, String content) throws MessagingException {
        if (CollectionUtils.isEmpty(recipients)) {
            ConsoleUtils.log("No email to send since no recipient address provided.");
            return;
        }

        if (StringUtils.isBlank(content)) {
            ConsoleUtils.log("No email to send since content provided.");
            return;
        }

        if (!readyToSend()) {
            ConsoleUtils.log("nexial mailer is not properly configured; SKIPPING");
            return;
        }

        // default subject
        if (StringUtils.isBlank(subject)) { subject = "[nexial] You've got mail"; }

        // off we go
        ConsoleUtils.log("Sending email to " + recipients);

        if (mailSupport.hasSmtpConfigs() || mailSupport.hasJndiConfigs()) {
            smtpSendPlainText(recipients, subject, content);
            return;
        }

        if (mailSupport.hasSesSettings()) { sesSendPlainText(recipients, subject, content); }
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
                                                     context.getStringData(ENABLE_EMAIL, DEF_ENABLE_EMAIL) :
                                                     System.getProperty(ENABLE_EMAIL, DEF_ENABLE_EMAIL));
        if (!enableEmail) {
            ConsoleUtils.log("email notification is currently not enabled; SKIPPING...");
            return;
        }

        if (!readyToSend()) {
            ConsoleUtils.log("nexial mailer is not properly configured to send execution result");
            return;
        }

        List<String> recipients = resolveRecipients();
        if (CollectionUtils.isEmpty(recipients)) {
            ConsoleUtils.log("No email to send since no recipient is specified.");
            return;
        }

        // print to console - last SOS attempt
        if (summary.getError() != null) { summary.getError().printStackTrace(); }

        StringBuilder subject = new StringBuilder("Execution Result for ");
        Set<String> nestedNames = new HashSet<>();
        summary.getNestedExecutions().forEach(nested -> nestedNames.add(nested.getName()));
        nestedNames.forEach(name -> subject.append(name).append(", "));

        // off we go
        ConsoleUtils.log("Preparing email for " + ArrayUtils.toString(recipients));
        Context engineContext = new Context();
        engineContext.setVariable("summary", summary);
        String content = mailTemplateEngine.process(mailTemplate, engineContext);

        try {
            sendResult(recipients, content, StringUtils.removeEnd(subject.toString(), ", "));
        } catch (MessagingException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public void sendResult(List<String> recipients, String content, String testCase) throws MessagingException {
        if (!readyToSend()) {
            ConsoleUtils.log("nexial mailer not properly configured to send execution result");
            return;
        }

        String subject = "Test Result for '" + testCase + "'";

        if (mailSupport.hasSmtpConfigs() || mailSupport.hasJndiConfigs()) {
            smtpSendResult(recipients, subject, content, null);
            return;
        }

        if (mailSupport.hasSesSettings()) { sesSendResult(recipients, subject, content); }
    }

    public void sendResult(List<String> recipients, String content, List<File> attachments) throws MessagingException {
        if (!readyToSend()) {
            ConsoleUtils.log("nexial mailer not properly configured to send execution result");
            return;
        }

        String subject = "Test Result for '" + deriveTestName(attachments) + "'";

        if (mailSupport.hasSmtpConfigs() || mailSupport.hasJndiConfigs()) {
            smtpSendResult(recipients, subject, content, attachments);
            return;
        }

        if (mailSupport.hasSesSettings()) {
            if (CollectionUtils.isNotEmpty(attachments)) {
                ConsoleUtils.error("AWS SES mailer currently does not support file attachments");
            }
            sesSendResult(recipients, subject, content);
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

    protected List<String> resolveRecipients() {
        String recipients = context != null ? context.getStringData(MAIL_TO) : System.getProperty(MAIL_TO);

        if (StringUtils.isBlank(recipients)) {
            recipients = context != null ? context.getStringData(MAIL_TO2) : System.getProperty(MAIL_TO2);
        }

        if (StringUtils.isBlank(recipients)) { return mailSupport.getRecipients(); }

        // if (StringUtils.isBlank(recipients)) { return null; }

        return TextUtils.toList(StringUtils.replace(recipients, ";", ","), ",", true);
    }

    protected void smtpSendPlainText(List<String> recipients, String subject, String content)
        throws MessagingException {

        Session session = mailSupport.getSession();
        SMTPTransport transport = mailSupport.createTransport(session);

        Message msg = prepMessage(session, subject);

        try {
            // add to, cc, bcc, from
            for (String recipient : recipients) { msg.addRecipient(TO, new InternetAddress(recipient)); }

            // content
            MimeBodyPart part = new MimeBodyPart();
            part.setContent(content, "text/plain");

            Multipart mp = new MimeMultipart();
            mp.addBodyPart(part);

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

    protected void smtpSendResult(List<String> recipients, String subject, String content, List<File> attachments)
        throws MessagingException {

        Session session = mailSupport.getSession();
        SMTPTransport transport = mailSupport.createTransport(session);

        Message msg = prepMessage(session, subject);

        try {
            Multipart mp = new MimeMultipart();

            // content
            MimeBodyPart part = new MimeBodyPart();
            part.setContent(content, mailSupport.getConfiguredProperty(MAIL_KEY_CONTENT_TYPE));
            mp.addBodyPart(part);

            // attachments
            if (CollectionUtils.isNotEmpty(attachments)) { addAttachment(mp, attachments); }

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

    protected void sesSendResult(List<String> recipients, String subject, String content) {
        sesSendEmail(recipients, subject, content, false);
    }

    protected void sesSendPlainText(List<String> recipients, String subject, String content) {
        sesSendEmail(recipients, subject, content, true);
    }

    protected void sesSendEmail(List<String> recipients, String subject, String content, boolean plaintext) {
        AwsSesSettings sesSettings = mailSupport.getSesSettings();

        SesConfig sesConfig = SesSupport.Companion.newConfig();
        sesConfig.setSubject(subject);

        if (plaintext) {
            sesConfig.setPlainText(content);
        } else {
            sesConfig.setHtml(content);
        }

        sesConfig.setTo(recipients);
        sesConfig.setFrom(sesSettings.getFrom());

        String replyTo = sesSettings.getReplyTo();
        if (StringUtils.isNotBlank(replyTo)) { sesConfig.setReplyTo(TextUtils.toList(replyTo, ",", true)); }

        String cc = sesSettings.getCc();
        if (StringUtils.isNotBlank(cc)) { sesConfig.setCc(TextUtils.toList(cc, ",", true)); }

        String bcc = sesSettings.getBcc();
        if (StringUtils.isNotBlank(bcc)) { sesConfig.setBcc(TextUtils.toList(bcc, ",", true)); }

        String configSet = sesSettings.getConfigurationSetName();
        if (StringUtils.isNotBlank(configSet)) { sesConfig.setConfigurationSetName(configSet); }

        String xmailer = sesSettings.getXmailer();
        if (StringUtils.isNotBlank(xmailer)) { sesConfig.setXmailer(xmailer); }

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
}
