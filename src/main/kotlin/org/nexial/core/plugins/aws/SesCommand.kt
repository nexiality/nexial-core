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

package org.nexial.core.plugins.aws

import org.nexial.commons.utils.TextUtils
import org.nexial.core.IntegrationConfigException
import org.nexial.core.aws.SesSupport
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.CheckUtils.requiresNotNull
import org.nexial.core.utils.OutputFileUtils

class SesCommand : BaseCommand() {

    override fun getTarget(): String = "aws.ses"

    fun sendTextMail(profile: String, to: String, subject: String, body: String) =
            sendHtmlMail(profile, to, subject, body, true)

    fun sendHtmlMail(profile: String, to: String, subject: String, body: String) =
            sendHtmlMail(profile, to, subject, body, false)

    private fun sendHtmlMail(profile: String, to: String, subject: String, body: String, sentAsPlainText: Boolean):
            StepResult {
        requiresNotBlank(profile, "Invalid profile", profile)
        requiresNotBlank(to, "Invalid 'to' address", to)
        requiresNotBlank(subject, "Invalid subject", subject)
        requiresNotBlank(body, "Invalid mail content", body)

        var content = OutputFileUtils.resolveContent(body, context, false)
        val delim = context.textDelim

        try {
            val settings = resolveSesSettings(context, profile)

            val config = SesSupport.newConfig()
            config.from = settings.from
            config.to = TextUtils.toList(to, delim, true)
            config.cc = TextUtils.toList(settings.cc, delim, true)
            config.bcc = TextUtils.toList(settings.bcc, delim, true)
            config.replyTo = TextUtils.toList(settings.replyTo, delim, true)
            config.configurationSetName = settings.configurationSetName
            config.subject = subject
            if (sentAsPlainText) config.plainText = content else config.html = content
            config.xmailer = settings.xmailer

            val support = SesSupport()
            support.setAccessKey(settings.accessKey)
            support.setSecretKey(settings.secretKey)
            support.setRegion(settings.region)
            support.sendMail(config)

            return StepResult.success("Mail sent via AWS SES to $to")
        } catch (e: IntegrationConfigException) {
            return StepResult.fail("Unable to resolve valid mail setting via profile '$profile': ${e.message}", e)
        }
    }

    @Throws(IntegrationConfigException::class)
    private fun resolveSesSettings(context: ExecutionContext, profile: String): AwsSesSettings {
        val settings = AwsUtils.resolveAwsSesSettings(context, profile)
        requiresNotNull(settings, "Unable to resolve AWS credentials and/or AWS SES settings.")
        return settings!!
    }
}
