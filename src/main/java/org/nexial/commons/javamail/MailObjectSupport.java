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

import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.aws.AwsSesSettings;
import org.nexial.core.reports.ExecutionMailConfig;
import org.nexial.core.utils.ConsoleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.mail.smtp.SMTPTransport;

import static javax.naming.Context.INITIAL_CONTEXT_FACTORY;
import static org.nexial.core.NexialConst.*;

/**
 * @author Mike Liu
 */
public final class MailObjectSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailObjectSupport.class);

    private ExecutionMailConfig mailConfig;
    private Properties mailProps;
    private Hashtable jndiEnv;
    private AwsSesSettings sesSettings;

    private Session session;
    private SMTPTransport transport;

    public void setMailProps(Properties mailProps) { this.mailProps = mailProps; }

    public void setJndiEnv(Hashtable jndiEnv) { this.jndiEnv = jndiEnv; }

    /** configure this instance with currently configured {@link ExecutionMailConfig}.  Also create mail session */
    public static MailObjectSupport configure(ExecutionMailConfig mailConfig) {
        MailObjectSupport instance = new MailObjectSupport();
        instance.mailConfig = mailConfig;

        Properties mailProps = mailConfig.toSmtpConfigs();
        if (MapUtils.isNotEmpty(mailProps)) {
            instance.mailProps = mailProps;
            if (instance.hasSmtpConfigs()) {
                instance.createSession();
                return instance;
            }
        }

        Hashtable jndiEnv = mailConfig.toJndiEnv();
        if (MapUtils.isNotEmpty(jndiEnv)) {
            instance.jndiEnv = jndiEnv;
            if (instance.hasJndiConfigs()) {
                instance.createSession();
                return instance;
            }
        }

        AwsSesSettings sesSettings = mailConfig.toSesConfigs();
        if (sesSettings != null) {
            instance.sesSettings = sesSettings;
            if (instance.hasSesSettings()) { return instance; }
        }

        ConsoleUtils.error("Nexial mailer not properly configured for use");
        return null;
    }

    public SMTPTransport getTransport() {
        if (transport == null || !transport.isConnected()) { createTransport(); }
        return transport;
    }

    public Session getSession() {
        if (session == null) { createSession(); }
        return session;
    }

    public AwsSesSettings getSesSettings() { return sesSettings; }

    public String getConfiguredProperty(String property) { return mailProps.getProperty(property); }

    public SMTPTransport createTransport(Session session) throws MessagingException {
        if (session == null) { throw new IllegalArgumentException("session is null"); }
        return newTransport(session);
    }

    public Session createJndiSession(String jndiName) throws NamingException {
        if (StringUtils.isBlank(jndiName)) { throw new IllegalArgumentException("jndiName is null or blank."); }
        InitialContext ctx = new InitialContext();
        return (Session) ctx.lookup(jndiName);
    }

    public boolean hasSmtpConfigs() {
        return MapUtils.isNotEmpty(mailProps) &&
               StringUtils.isNotBlank(mailProps.getProperty(MAIL_KEY_MAIL_HOST)) &&
               StringUtils.isNotBlank(mailProps.getProperty(MAIL_KEY_PROTOCOL)) &&
               StringUtils.isNotBlank(mailProps.getProperty(MAIL_KEY_MAIL_PORT));
    }

    public boolean hasJndiConfigs() {
        return MapUtils.isNotEmpty(jndiEnv) &&
               jndiEnv.get(MAIL_KEY_MAIL_JNDI_URL) != null &&
               jndiEnv.get(INITIAL_CONTEXT_FACTORY) != null;
    }

    public boolean hasSesSettings() { return sesSettings != null; }

    public List<String> getRecipients() { return mailConfig == null ? null : mailConfig.getRecipients(); }

    private void createSession() {
        // is javamail session configured in JNDI?
        String jndiName = mailProps.getProperty(MAIL_KEY_MAIL_JNDI_URL);
        if (StringUtils.isNotBlank(jndiName)) {
            try {
                InitialContext ctx = MapUtils.isEmpty(jndiEnv) ? new InitialContext() : new InitialContext(jndiEnv);
                session = (Session) ctx.lookup(jndiName);
                if (LOGGER.isInfoEnabled()) { LOGGER.info("JNDI resource '" + jndiName + "' for sending email..."); }
            } catch (NamingException e) {
                // javamail session IS configured in JNDI, but unable to get session resource...
                String error = "Unable to fetch JNDI resource '" + jndiName + "': " + e.getMessage();
                LOGGER.error(error, e);
                throw new RuntimeException(error, e);
            }
        } else {
            // javamail session is NOT configured in JNDI... continue on to use std mail config.
            if (LOGGER.isInfoEnabled()) { LOGGER.info("Standalone config. for sending email..."); }
            createStandAloneSession();
        }
    }

    private void createStandAloneSession() {
        Authenticator auth = null;

        String userName = mailProps.getProperty(MAIL_KEY_USERNAME);
        if (BooleanUtils.toBoolean(mailProps.getProperty(MAIL_KEY_AUTH)) && StringUtils.isNotBlank(userName)) {
            final String smtpUsername = userName;
            final String smtpPassword = mailProps.getProperty(MAIL_KEY_PASSWORD);
            auth = new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            };
        }

        session = Session.getInstance(mailProps, auth);
        if (BooleanUtils.toBoolean(mailProps.getProperty(MAIL_KEY_DEBUG))) { session.setDebug(true); }
    }

    private void createTransport() {
        String mailHost = session.getProperty(MAIL_KEY_MAIL_HOST);
        try {
            transport = newTransport(session);
        } catch (NoSuchProviderException e) {
            LOGGER.error("Mail provider not found for mail host " + mailHost, e);
        } catch (MessagingException e) {
            LOGGER.error("Error occurred while setting transport on mail host " + mailHost, e);
        }
    }

    private SMTPTransport newTransport(Session session) throws MessagingException {
        SMTPTransport transport = (SMTPTransport) session.getTransport(session.getProperty(MAIL_KEY_PROTOCOL));

        if (BooleanUtils.toBoolean(session.getProperty(MAIL_KEY_AUTH))) {
            String mailHost = session.getProperty(MAIL_KEY_MAIL_HOST);
            String mailPort = session.getProperty(MAIL_KEY_MAIL_PORT);
            String smtpUsername = session.getProperty(MAIL_KEY_USERNAME);
            String smtpPassword = session.getProperty(MAIL_KEY_PASSWORD);
            transport.connect(mailHost, Integer.parseInt(mailPort), smtpUsername, smtpPassword);
        } else {
            transport.connect();
        }

        return transport;
    }
}
