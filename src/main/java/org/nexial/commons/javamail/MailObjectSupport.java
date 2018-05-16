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

import java.net.UnknownHostException;
import java.util.Hashtable;
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
import org.nexial.commons.utils.EnvUtils;
import org.nexial.core.reports.ExecutionMailConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.mail.smtp.SMTPTransport;

import static javax.naming.Context.*;
import static org.nexial.core.NexialConst.*;

/**
 * @author Mike Liu
 */
public final class MailObjectSupport {
    public static final String[] JNDI_KEYS = new String[]{
        INITIAL_CONTEXT_FACTORY, OBJECT_FACTORIES, STATE_FACTORIES, URL_PKG_PREFIXES, PROVIDER_URL, DNS_URL,
        AUTHORITATIVE, BATCHSIZE, REFERRAL, SECURITY_PROTOCOL, SECURITY_AUTHENTICATION, SECURITY_PRINCIPAL,
        SECURITY_CREDENTIALS, LANGUAGE
    };
    public static final String[] MAIL_PROP_KEYS = new String[]{
        MAIL_KEY_BUFF_SIZE, MAIL_KEY_PROTOCOL, MAIL_KEY_MAIL_HOST, MAIL_KEY_MAIL_PORT, MAIL_KEY_TLS_ENABLE,
        MAIL_KEY_AUTH, MAIL_KEY_DEBUG, MAIL_KEY_CONTENT_TYPE, MAIL_KEY_USERNAME, MAIL_KEY_PASSWORD,
        MAIL_KEY_MAIL_JNDI_URL};

    private static final Logger LOGGER = LoggerFactory.getLogger(MailObjectSupport.class);

    private Properties mailProps;
    private Hashtable jndiEnv;
    private Session session;
    private SMTPTransport transport;

    public void setMailProps(Properties mailProps) { this.mailProps = mailProps; }

    public void setJndiEnv(Hashtable jndiEnv) { this.jndiEnv = jndiEnv; }

    /**
     * configure this instance with currently configured {@link ExecutionMailConfig}.  Also create mail session
     */
    public void configure() {
        ExecutionMailConfig mailConfig = ExecutionMailConfig.get();

        Properties mailProps = mailConfig.toMailProperties();
        if (MapUtils.isNotEmpty(mailProps)) { this.mailProps = mailProps; }

        Hashtable jndiEnv = mailConfig.toJndiEnv();
        if (MapUtils.isNotEmpty(jndiEnv)) { this.jndiEnv = jndiEnv; }

        createSession();
    }

    public void init() {
        try {
            mailProps.setProperty(MAIL_KEY_SMTP_LOCALHOST, EnvUtils.getHostName());
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to query localhost's hostname, setting '" + MAIL_KEY_SMTP_LOCALHOST +
                        "' to 'localhost', but it probably won't work. " + e.getMessage());
            mailProps.setProperty(MAIL_KEY_SMTP_LOCALHOST, "localhost");
        }

        createSession();
        //createTransport();
    }

    public SMTPTransport getTransport() {
        if (transport == null || !transport.isConnected()) { createTransport(); }
        return transport;
    }

    public Session getSession() {
        if (session == null) { createSession(); }
        return session;
    }

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
            LOGGER.error("Error occured while setting transport on mail host " + mailHost, e);
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
