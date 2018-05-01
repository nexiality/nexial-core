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

import java.util.HashSet;
import java.util.Set;

import javax.mail.MessagingException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.utils.ConsoleUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

public class MailNotifier implements ExecutionNotifier {
	private Mailer mailer = new Mailer();
	private TemplateEngine mailTemplateEngine;
	private String mailTemplate;

	public void setMailer(Mailer mailer) { this.mailer = mailer; }

	public void setMailTemplateEngine(TemplateEngine mailTemplateEngine) {
		this.mailTemplateEngine = mailTemplateEngine;
	}

	public void setMailTemplate(String mailTemplate) { this.mailTemplate = mailTemplate; }

	@Override
	public void notify(String[] recipients, ExecutionSummary summary) throws MessagingException {

		if (ArrayUtils.isEmpty(recipients)) {
			ConsoleUtils.log("No email to send since no address provided.");
			return;
		}

		if (summary == null) {
			ConsoleUtils.log("No execution summary found.. skipping mail notification");
			return;
		}

		if (CollectionUtils.isEmpty(summary.getNestedExecutions())) {
			ConsoleUtils.log("No result files found... skipping mail notification");
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
		mailer.sendResult(recipients, content, StringUtils.removeEnd(subject.toString(), ", "));
	}
}
