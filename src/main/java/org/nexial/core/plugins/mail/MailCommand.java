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

import javax.mail.MessagingException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

import org.nexial.commons.javamail.MailSender;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;

import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

/**
 *
 */
public class MailCommand extends BaseCommand {

	private MailSender sender;

	@Override
	public String getTarget() { return "mail"; }

	@Override
	public void init(ExecutionContext context) {
		super.init(context);
		sender = MailSender.newInstance(context.getMailer());
	}

	public StepResult send(String to, String subject, String body) {
		requiresNotBlank(to, "Invalid recipient", to);
		requiresNotBlank(subject, "Invalid subject", subject);
		requiresNotBlank(body, "Invalid email body", body);

		String from = System.getProperty("mail.smtp.from");
		String[] recipients = StringUtils.split(StringUtils.replace(to, ";", ","), ",");

		try {
			sender.sendMail(recipients, from, subject, body);
			return StepResult.success("email successfully sent");
		} catch (MessagingException e) {
			return StepResult.fail("email unsuccessful to be sent: " + e.getMessage());
		}
	}

	protected boolean isValidEmail(String address) {
		return StringUtils.isNotBlank(address) && EmailValidator.getInstance().isValid(address.trim());
	}
}
