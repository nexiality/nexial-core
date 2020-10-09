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

package org.nexial.commons.javamail;

import java.io.File;
import java.util.*;
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

import com.sun.mail.smtp.SMTPTransport;

import static javax.mail.Message.RecipientType.*;
import static org.nexial.core.NexialConst.Mailer.MAIL_KEY_CONTENT_TYPE;

/**
 * @author Mike Liu
 */
public final class MailSender {
    private MailObjectSupport mail;

    private MailSender() {}

    public static MailSender newInstance(MailObjectSupport mail) {
        MailSender _this = new MailSender();
        _this.mail = mail;
        return _this;
    }

    public void sendMail(String to, String from, String subject, String content) throws MessagingException {
        sendMail(Collections.singletonList(to), null, null, from, subject, content);
    }

    public void sendMail(String[] to, String from, String subject, String content) throws MessagingException {
        sendMail(Arrays.asList(to), null, null, from, subject, content);
    }

    public void sendMail(String[] to, String from, String subject, String content, File... files)
        throws MessagingException {
        sendMail(Arrays.asList(to), null, null, from, subject, content, files);
    }

    public void sendMail(String to, String cc, String from, String subject, String content) throws MessagingException {
        sendMail(Collections.singletonList(to), Collections.singletonList(cc), null, from, subject, content);
    }

    public void sendMail(String[] to, String[] cc, String from, String subject, String content)
        throws MessagingException {
        sendMail(Arrays.asList(to), Arrays.asList(cc), null, from, subject, content);
    }

    public void sendMail(String[] to, String[] cc, String from, String subject, String content, File... files)
        throws MessagingException {
        sendMail(Arrays.asList(to), Arrays.asList(cc), null, from, subject, content, files);
    }

    public void sendMail(List<String> to,
                         List<String> cc,
                         List<String> bcc,
                         String from,
                         String subject,
                         String content,
                         File... attachments) throws MessagingException {

        if (mail == null) { throw new RuntimeException("Mail instance not set/found"); }
        Session session = mail.getSession();

        String contentType = mail.getConfiguredProperty(MAIL_KEY_CONTENT_TYPE);
        Message msg = new MimeMessage(session);

        try (SMTPTransport transport = mail.createTransport(session)) {
            // to, cc, bcc, from
            Multipart mp = getMultipart(to, cc, bcc, from, subject, content, contentType, msg);

            // attachments
            if (attachments != null) {
                for (File attachment : attachments) {
                    MimeBodyPart messageBodyPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(attachment);
                    messageBodyPart.setDataHandler(new DataHandler(source));
                    messageBodyPart.setFileName(attachment.getName());
                    mp.addBodyPart(messageBodyPart);
                }
            }

            // final steps
            sendMessage(transport, msg, mp);
        }
    }

    public void sendMail(List<String> to,
                         List<String> cc,
                         List<String> bcc,
                         String from,
                         String subject,
                         String content,
                         Map<String, File> attachments) throws MessagingException {

        if (mail == null) {
            throw new RuntimeException("Mail instance not set/found");
        }

        Session session = mail.getSession();
        String contentType = mail.getConfiguredProperty(MAIL_KEY_CONTENT_TYPE);
        Message msg = new MimeMessage(session);

        try (SMTPTransport transport = mail.createTransport(session)) {
            // to, cc, bcc, from
            Multipart mp = getMultipart(to, cc, bcc, from, subject, content, contentType, msg);

            // attachments
            if (attachments != null) {
                for (String attachment : attachments.keySet()) {
                    MimeBodyPart messageBodyPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(attachments.get(attachment));
                    messageBodyPart.setDataHandler(new DataHandler(source));
                    messageBodyPart.setFileName(attachment);
                    mp.addBodyPart(messageBodyPart);
                }
            }
            sendMessage(transport, msg, mp);

        }
    }

    private Multipart getMultipart(List<String> to, List<String> cc, List<String> bcc, String from, String subject, String content, String contentType, Message msg) throws MessagingException {
        for (String addr : to) {
            msg.addRecipient(TO, new InternetAddress(addr));
        }

        if (cc != null) {
            for (String addr : cc) {
                msg.addRecipient(CC, new InternetAddress(addr));
            }
        }

        if (bcc != null) {
            for (String addr : bcc) {
                msg.addRecipient(BCC, new InternetAddress(addr));
            }
        }

        msg.setFrom(new InternetAddress(from));

        // subject
        msg.setSubject(subject);

        Multipart mp = new MimeMultipart();

        // content
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(content, contentType);
        mp.addBodyPart(part);
        return mp;
    }

    private void sendMessage(SMTPTransport transport, Message msg, Multipart mp) throws MessagingException {
        // final steps
        msg.setContent(mp);
        msg.setSentDate(new Date());
        msg.saveChanges();

        // send it!
        transport.sendMessage(msg, msg.getAllRecipients());
    }

}
