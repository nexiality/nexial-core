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

package org.nexial.core.aws

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.Regions.DEFAULT_REGION
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClientBuilder
import com.amazonaws.services.simpleemail.model.*
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.Mailer.SES_ENFORCE_NO_CERT
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.utils.ConsoleUtils
import java.util.regex.Pattern
import java.util.regex.Pattern.DOTALL

private const val MSG_UNVERIFIED_DOMAIN = "\tCheck the target email address(es) and ensure the specified mail\n" +
                                          "\tdomain has been verified in the corresponding AWS account. For\n" +
                                          "\tmore details, please check \n" +
                                          "\thttps://docs.aws.amazon.com/ses/latest/DeveloperGuide/verify-domain-procedure.html"
private const val ERR_UNVERIFIED_DOMAIN = "Domain contains illegal character"
private const val HTML_FOOTER_PREFIX = "<br/><br/><div style=\"text-align:right;font-size:9pt;color:#aaa\">"
private const val HTML_FOOTER_POSTFIX = "</div><br/>"
private val REGEX_HAS_TAG: Pattern = Pattern.compile(".*<[^>]+>.*", DOTALL)

class SesSupport : AwsSupport() {

    class SesConfig {
        var to: List<String>? = null
        var from: String? = null
        var cc: List<String>? = null
        var bcc: List<String>? = null
        var replyTo: List<String>? = null
        var subject: String? = null
        var html: String? = null
        var plainText: String? = null
        var configurationSetName: String? = null
        var xmailer: String? = null
    }

    fun sendMail(config: SesConfig) {
        // sanity check
        if (StringUtils.isBlank(accessKey)) throw IllegalArgumentException("AWS accessKey is missing")
        if (StringUtils.isBlank(secretKey)) throw IllegalArgumentException("AWS secretKey is missing")
        if (StringUtils.isBlank(config.from)) throw IllegalArgumentException("from address is required")
        if (CollectionUtils.isEmpty(config.to)) throw IllegalArgumentException("to address is required")
        if (StringUtils.isBlank(config.subject)) throw IllegalArgumentException("subject is required")
        if (StringUtils.isBlank(config.html) && StringUtils.isBlank(config.plainText))
            throw IllegalArgumentException("Either HTML or plain text email content is required")

        // here we go
        val request = SendEmailRequest()
            .withSource(config.from)
            .withDestination(toDestination(config))
            .withMessage(toMessage(config, toSubject(config)))
            .withConfigurationSetName(config.configurationSetName)
        if (CollectionUtils.isNotEmpty(config.replyTo)) request.withReplyToAddresses(config.replyTo)

        sendMail(request)
    }

    private fun sendMail(request: SendEmailRequest) {
        val region = if (this.region == null) DEFAULT_REGION else this.region
        ConsoleUtils.log("invoking AWS SES on ${region.description}")

        if (BooleanUtils.toBoolean(System.getProperty(SES_ENFORCE_NO_CERT, "${getDefaultBool(SES_ENFORCE_NO_CERT)}"))) {
            System.clearProperty("javax.net.ssl.trustStore")
            System.clearProperty("javax.net.ssl.trustStorePassword")
        }

        val client = AmazonSimpleEmailServiceAsyncClientBuilder.standard()
            .withRegion(region)
            .withCredentials(resolveCredentials(region))
            .build()

        ConsoleUtils.log("scheduling sent-mail via AWS SES to ${request.destination.toAddresses}")
        client.sendEmailAsync(request, object : AsyncHandler<SendEmailRequest, SendEmailResult> {
            override fun onError(e: Exception) {
                ConsoleUtils.error("FAILED to send email via AWS SES: ${e.message}" +
                                   if (StringUtils.contains(e.message,
                                                            ERR_UNVERIFIED_DOMAIN)) "\n$MSG_UNVERIFIED_DOMAIN"
                                   else "")
                e.printStackTrace()
            }

            override fun onSuccess(request: SendEmailRequest, sendEmailResult: SendEmailResult) {
                ConsoleUtils.log("Email sent via AWS SES: $sendEmailResult")
            }
        })
    }

    private fun toMessage(config: SesConfig, subject: Content): Message {
        val body = Body()

        val xmailer = StringUtils.defaultIfEmpty(config.xmailer, "")

        if (StringUtils.isNotBlank(config.html)) {
            val footer = "$HTML_FOOTER_PREFIX$xmailer$HTML_FOOTER_POSTFIX"
            val content = if (REGEX_HAS_TAG.matcher(config.html).matches()) {
                val document = Jsoup.parse(config.html!!)
                document.body().append(footer)
                document.html()
            } else {
                "<html><body>${config.html}\n$footer</body></html>"
            }

            body.withHtml(Content().withCharset(DEF_FILE_ENCODING).withData(content))
        }

        if (StringUtils.isNotBlank(config.plainText)) {
            body.withText(Content().withCharset(DEF_FILE_ENCODING).withData("${config.plainText}\n\n\n\n$xmailer"))
        }

        return Message().withSubject(subject).withBody(body)
    }

    private fun toDestination(config: SesConfig): Destination {
        val destination = Destination().withToAddresses(config.to)
        if (CollectionUtils.isNotEmpty(config.cc)) destination.withCcAddresses(config.cc)
        if (CollectionUtils.isNotEmpty(config.bcc)) destination.withBccAddresses(config.bcc)
        return destination
    }

    private fun toSubject(config: SesConfig) = Content().withCharset(DEF_FILE_ENCODING).withData(config.subject)

    companion object {
        fun newConfig() = SesConfig()
    }
}
