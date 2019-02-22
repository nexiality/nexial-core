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

package org.nexial.core.model

import javazoom.jl.decoder.JavaLayerException
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_NAME
import org.nexial.commons.utils.EnvUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.IntegrationConfigException
import org.nexial.core.NexialConst.Data.*
import org.nexial.core.mail.MailData
import org.nexial.core.model.ExecutionEvent.ExecutionPause
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import javax.mail.MessagingException

abstract class EventNotification(val context: ExecutionContext,
                                 val event: ExecutionEvent,
                                 val data: String,
                                 var includeExecMeta: Boolean = false) {

    open fun perform() {
        this.parse()
    }

    fun includeExecMeta(includeExecMeta: Boolean) = {
        this.includeExecMeta = includeExecMeta
        this
    }

    internal open fun parse() {}

    internal fun logSkipping(message: String) {
        val msgId = context.runId
        ConsoleUtils.log(msgId, "$event - $message; SKIPPING...")
        ConsoleUtils.log(msgId, "$event - $data")
    }

    internal fun gatherMeta(): String = "From ${StringUtils.defaultString(USER_NAME, "unknown")}@" +
                                        "${StringUtils.upperCase(EnvUtils.getHostName())} using " +
                                        ExecUtils.deriveJarManifest()

    internal fun resolveMimeType() = Companion.resolveMimeType(context)

    companion object {
        fun resolveMimeType(context: ExecutionContext) =
                if (context.getBooleanData(OPT_NOTIFY_WITH_HTML, false)) MIME_HTML else MIME_PLAIN
    }
}

class EmailNotification(context: ExecutionContext, event: ExecutionEvent, data: String) :
        EventNotification(context, event, data, true) {

    private var mailTagLine = "~ powered by nexial ~"
    private lateinit var mailData: MailData

    fun mailTagLine(mailTagLine: String) = {
        this.mailTagLine = mailTagLine
        this
    }

    override fun parse() {
        val recipientList = TextUtils.toList(StringUtils.substringBefore(data, EVENT_CONFIG_SEP),
                                             context.textDelim,
                                             true)
        if (CollectionUtils.isEmpty(recipientList)) {
            logSkipping("invalid or no recipient specified")
            return
        }

        val mailContent = StringUtils.trim(StringUtils.substringAfter(data, EVENT_CONFIG_SEP)) +
                          (if (includeExecMeta) "\n" + gatherMeta() else "") + "\n" + mailTagLine + "\n"

        mailData = MailData.newInstance(resolveMimeType())
            .setToAddr(recipientList)
            .setSubject(MAIL_NOTIF_SUBJECT_PREFIX + event.description)
            .setContent(mailContent)
            .setMimeType(resolveMimeType())
    }

    override fun perform() {
        super.perform()
        val mailer = context.getNexialMailer()
        if (mailer == null) {
            logSkipping("nexial mailer not configured for notification")
            return
        }

        try {
            mailer.sendMail(mailData)
        } catch (e: MessagingException) {
            logSkipping("nexial mailer not configured properly: ${e.message}")
        }
    }
}

class SmsNotification(context: ExecutionContext, event: ExecutionEvent, data: String) :
        EventNotification(context, event, data) {

    private lateinit var phones: List<String>
    private lateinit var text: String

    override fun parse() {
        val phone = StringUtils.substringBefore(data, EVENT_CONFIG_SEP)
        phones = TextUtils.toList(phone, context.textDelim, true)

        text = StringUtils.substringAfter(data, EVENT_CONFIG_SEP) +
               if (includeExecMeta) "\n\n" + gatherMeta() else ""
    }

    override fun perform() {
        super.perform()
        if (CollectionUtils.isEmpty(phones)) {
            logSkipping("no phone numbers specified")
            return
        }

        if (StringUtils.isBlank(text)) {
            logSkipping("no text specified")
            return
        }

        val smsHelper = context.getSmsHelper()
        if (smsHelper == null) {
            logSkipping("sms not configured")
            return
        }

        try {
            smsHelper.send(phones, text)
        } catch (e: IntegrationConfigException) {
            ConsoleUtils.log(context.runId, "$event - sms not configured: ${e.message}")
            ConsoleUtils.log(context.runId, "$event - $data")
        }
    }
}

class TtsNotification(context: ExecutionContext, event: ExecutionEvent, data: String) :
        EventNotification(context, event, data) {

    override fun perform() {
        super.perform()
        if (StringUtils.isBlank(data)) {
            logSkipping("No text specified")
            return
        }

        val dj = context.getDj()
        if (dj == null) {
            logSkipping("tts not configured")
            return
        }

        try {
            dj.speak(data, true)
        } catch (e: JavaLayerException) {
            ConsoleUtils.log(context.runId, "$event - tts not configured correctly: ${e.message}")
            ConsoleUtils.log(context.runId, "$event - $data")
        } catch (e: IntegrationConfigException) {
            ConsoleUtils.log(context.runId, "$event - tts not configured correctly: ${e.message}")
            ConsoleUtils.log(context.runId, "$event - $data")
        }
    }
}

class AudioNotification(context: ExecutionContext, event: ExecutionEvent, data: String) :
        EventNotification(context, event, data) {

    override fun perform() {
        super.perform()
        if (StringUtils.isBlank(data)) {
            logSkipping("No text specified")
            return
        }

        val dj = context.getDj()
        if (dj == null) {
            logSkipping("tts not configured")
            return
        }

        try {
            dj.playAudio(data)
        } catch (e: Exception) {
            ConsoleUtils.log(context.runId, "$event - audio playback error: ${e.message}")
            ConsoleUtils.log(context.runId, "$event - $data")
        }
    }
}

class ConsoleNotification(context: ExecutionContext, event: ExecutionEvent, data: String) :
        EventNotification(context, event, data) {

    override fun perform() {
        super.perform()
        if (StringUtils.isBlank(data)) {
            logSkipping("No text specified")
            return
        }

        if (event === ExecutionPause) {
            logSkipping("$event with [console] notification doesn't make sense.")
            return
        }

        ConsoleUtils.doPause(context, event.description + " - " + data)
    }
}

