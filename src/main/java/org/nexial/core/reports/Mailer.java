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
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.javamail.MailObjectSupport;
import org.nexial.core.utils.ExecUtil;

import com.sun.mail.smtp.SMTPTransport;

import static javax.mail.Message.RecipientType.TO;
import static org.nexial.core.NexialConst.MAIL_KEY_CONTENT_TYPE;
import static org.nexial.core.NexialConst.OPT_MAIL_FROM;

public class Mailer {
    private MailObjectSupport mailer;

    public void setMailer(MailObjectSupport mailer) { this.mailer = mailer; }

    public void sendPlainText(List<String> recipients, String subject, String content) throws MessagingException {

        Session session = mailer.getSession();
        SMTPTransport transport = mailer.createTransport(session);

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

    public void sendResult(String[] recipients, String content, String testCase) throws MessagingException {

        Session session = mailer.getSession();
        SMTPTransport transport = mailer.createTransport(session);

        Message msg = prepMessage(session, "Test Result for '" + testCase + "'");

        try {
            Multipart mp = new MimeMultipart();

            // content
            MimeBodyPart part = new MimeBodyPart();
            part.setContent(content, mailer.getConfiguredProperty(MAIL_KEY_CONTENT_TYPE));
            mp.addBodyPart(part);

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

    public void sendResult(String[] recipients, String content, List<File> attachments)
        throws MessagingException {

        Session session = mailer.getSession();
        SMTPTransport transport = mailer.createTransport(session);

        Message msg = prepMessage(session, "Test Result for '" + deriveTestName(attachments) + "'");

        try {
            Multipart mp = new MimeMultipart();

            // content
            MimeBodyPart part = new MimeBodyPart();
            part.setContent(content, mailer.getConfiguredProperty(MAIL_KEY_CONTENT_TYPE));
            mp.addBodyPart(part);

            // attachments
            addAttachment(mp, attachments);

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

    public void sendCidContent(String[] recipients, String subject, String content, Map<String, File> images)
        throws MessagingException {

        Session session = mailer.getSession();
        SMTPTransport transport = mailer.createTransport(session);

        Message msg = prepMessage(session, subject);

        try {
            Multipart mp = new MimeMultipart("related");

            // content
            MimeBodyPart part = new MimeBodyPart();
            part.setContent(content, mailer.getConfiguredProperty(MAIL_KEY_CONTENT_TYPE));
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

    private static String deriveTestName(List<File> attachments) {
        // since the attachments from the same test session, any of the files should suffice as 'test name'
        if (CollectionUtils.isEmpty(attachments)) { return "Unknown"; }

        File excel = attachments.get(0);
        return StringUtils.substringBeforeLast(excel.getName(), ".");
    }

    private Message prepMessage(Session session, String subject) throws MessagingException {
        String from = mailer.getConfiguredProperty(OPT_MAIL_FROM);

        Message msg = new MimeMessage(session);
        msg.addHeader("X-Mailer", ExecUtil.deriveJarManifest());
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

    // private void prepReceipients(Properties config, Message msg) throws MessagingException {
    //     String[] to = StringUtils.split(config.getProperty(OPT_MAILTO), DELIM_EMAIL);
    //     if (ArrayUtils.isNotEmpty(to)) { for (String addr : to) { msg.addRecipient(TO, new InternetAddress(addr)); } }
    //
    //     String[] cc = StringUtils.split(config.getProperty(OPT_MAIL_CC), DELIM_EMAIL);
    //     if (ArrayUtils.isNotEmpty(cc)) { for (String addr : cc) { msg.addRecipient(CC, new InternetAddress(addr)); } }
    //
    //     String[] bcc = StringUtils.split(config.getProperty(OPT_MAIL_BCC), DELIM_EMAIL);
    //     if (ArrayUtils.isNotEmpty(bcc)) {
    //         for (String addr : bcc) { msg.addRecipient(BCC, new InternetAddress(addr)); }
    //     }
    // }

}
