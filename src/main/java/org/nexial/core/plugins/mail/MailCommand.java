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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.nexial.commons.javamail.MailObjectSupport;
import org.nexial.commons.javamail.MailSender;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.OutputResolver;

import static org.nexial.core.utils.CheckUtils.requiresNotBlank;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

public class MailCommand extends BaseCommand {

    @Override
    public String getTarget() { return "mail"; }

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

    protected boolean isValidEmail(String address) {
        return StringUtils.isNotBlank(address) && EmailValidator.getInstance().isValid(address.trim());
    }
}
