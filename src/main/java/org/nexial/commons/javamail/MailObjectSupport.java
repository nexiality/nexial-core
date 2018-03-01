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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.mail.smtp.SMTPTransport;

import static javax.naming.Context.*;

/**
 * @author Mike Liu
 */
public final class MailObjectSupport {
	public static final String[] JNDI_KEYS = new String[]{
		INITIAL_CONTEXT_FACTORY, OBJECT_FACTORIES, STATE_FACTORIES, URL_PKG_PREFIXES, PROVIDER_URL, DNS_URL,
		AUTHORITATIVE, BATCHSIZE, REFERRAL, SECURITY_PROTOCOL, SECURITY_AUTHENTICATION, SECURITY_PRINCIPAL,
		SECURITY_CREDENTIALS, LANGUAGE
	};

	private static final Logger LOGGER = LoggerFactory.getLogger(MailObjectSupport.class);

	private static final String KEY_AUTH = "mail.smtp.auth";
	private static final String KEY_USERNAME = "mail.smtp.username";
	private static final String KEY_PASSWORD = "mail.smtp.password";
	private static final String KEY_DEBUG = "mail.smtp.debug";
	private static final String KEY_MAIL_HOST = "mail.smtp.host";
	private static final String KEY_MAIL_PORT = "mail.smtp.port";
	private static final String KEY_PROTOCOL = "mail.transport.protocol";
	private static final String KEY_SMTP_LOCALHOST = "mail.smtp.localhost";
	private static final String KEY_MAIL_JNDI_URL = "mail.jndi.url";
	private static final String KEY_BUFF_SIZE = "mail.smtp.bufferSize";
	private static final String KEY_TLS_ENABLE = "mail.smtp.starttls.enable";
	private static final String KEY_CONTENT_TYPE = "mail.smtp.contentType";
	public static final String[] MAIL_PROP_KEYS = new String[]{
		KEY_BUFF_SIZE, KEY_PROTOCOL, KEY_MAIL_HOST, KEY_MAIL_PORT, KEY_TLS_ENABLE, KEY_AUTH, KEY_DEBUG,
		KEY_CONTENT_TYPE, KEY_USERNAME, KEY_PASSWORD, KEY_MAIL_JNDI_URL
	};

	private Properties mailProps;
	private Hashtable jndiEnv;
	private Session session;
	private SMTPTransport transport;

	public Properties getMailProps() { return mailProps; }

	public void setMailProps(Properties mailProps) { this.mailProps = mailProps; }

	public void setJndiEnv(Hashtable jndiEnv) { this.jndiEnv = jndiEnv; }

	public void init() {
		try {
			mailProps.setProperty(KEY_SMTP_LOCALHOST, EnvUtils.getHostName());
		} catch (UnknownHostException e) {
			LOGGER.warn("Unable to query localhost's hostname, setting '" + KEY_SMTP_LOCALHOST +
			            "' to 'localhost', but it probably won't work. " + e.getMessage());
			mailProps.setProperty(KEY_SMTP_LOCALHOST, "localhost");
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
		String jndiName = mailProps.getProperty(KEY_MAIL_JNDI_URL);
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

		String userName = mailProps.getProperty(KEY_USERNAME);
		if (BooleanUtils.toBoolean(mailProps.getProperty(KEY_AUTH)) && StringUtils.isNotBlank(userName)) {
			final String smtpUsername = userName;
			final String smtpPassword = mailProps.getProperty(KEY_PASSWORD);
			auth = new Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(smtpUsername, smtpPassword);
				}
			};
		}

		session = Session.getInstance(mailProps, auth);
		if (BooleanUtils.toBoolean(mailProps.getProperty(KEY_DEBUG))) { session.setDebug(true); }
	}

	private void createTransport() {
		String mailHost = session.getProperty(KEY_MAIL_HOST);
		try {
			transport = newTransport(session);
		} catch (NoSuchProviderException e) {
			LOGGER.error("Mail provider not found for mail host " + mailHost, e);
		} catch (MessagingException e) {
			LOGGER.error("Error occured while setting transport on mail host " + mailHost, e);
		}
	}

	private SMTPTransport newTransport(Session session) throws MessagingException {
		SMTPTransport transport = (SMTPTransport) session.getTransport(session.getProperty(KEY_PROTOCOL));

		if (BooleanUtils.toBoolean(session.getProperty(KEY_AUTH))) {
			String mailHost = session.getProperty(KEY_MAIL_HOST);
			String mailPort = session.getProperty(KEY_MAIL_PORT);
			String smtpUsername = session.getProperty(KEY_USERNAME);
			String smtpPassword = session.getProperty(KEY_PASSWORD);
			transport.connect(mailHost, Integer.parseInt(mailPort), smtpUsername, smtpPassword);
		} else {
			transport.connect();
		}

		return transport;
	}
}
