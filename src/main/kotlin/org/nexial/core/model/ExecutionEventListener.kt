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

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.Data.*
import org.nexial.core.model.ExecutionEvent.*
import org.nexial.core.service.EventTracker
import org.nexial.core.utils.ConsoleUtils

class ExecutionEventListener {
    lateinit var context: ExecutionContext
    lateinit var mailTagLine: String
    var mailIncludeMeta: Boolean = false
    var smsIncludeMeta: Boolean = false

    fun onExecutionStart() = handleEvent(ExecutionStart)
    fun onExecutionComplete() = handleEvent(ExecutionComplete)
    fun onScriptStart() = handleEvent(ScriptStart)
    fun onScriptComplete() = handleEvent(ScriptComplete)
    fun onIterationStart() = handleEvent(IterationStart)
    fun onIterationComplete() = handleEvent(IterationComplete)
    fun onScenarioStart() = handleEvent(ScenarioStart)

    fun onScenarioComplete(executionSummary: ExecutionSummary) {
        handleEvent(ScenarioComplete)
        // summary and context are null in Interactive Mode
        if (ExecutionThread.get() != null) EventTracker.track(NexialScenarioCompleteEvent(executionSummary))
    }

    fun onError() = handleEvent(ErrorOccurred)
    fun onPause() = handleEvent(ExecutionPause)

    // nothing for now
    fun afterPause() {}

    private fun handleEvent(event: ExecutionEvent) {
        val notifyConfig = context.getStringData(event.variable)
        if (StringUtils.isBlank(notifyConfig) || !isConditionMatched(event)) return

        if (!StringUtils.contains(notifyConfig, ":")) {
            ConsoleUtils.error("Unknown notification for [" + event.eventName + "]: " + notifyConfig)
            return
        }

        val notifyPrefix = StringUtils.substringBefore(notifyConfig, ":") + ":"
        val notifyText = StringUtils.substringAfter(notifyConfig, ":")

        when (notifyPrefix) {
            TTS_PREFIX     -> TtsNotification(context, event, notifyText).perform()
            SMS_PREFIX     -> SmsNotification(context, event, notifyText).includeExecMeta(smsIncludeMeta).invoke().perform()
            AUDIO_PREFIX   -> AudioNotification(context, event, notifyText).perform()
            EMAIL_PREFIX   -> EmailNotification(context, event, notifyText).mailTagLine(mailTagLine).invoke().includeExecMeta(mailIncludeMeta).invoke()
                .perform()
            CONSOLE_PREFIX -> ConsoleNotification(context, event, notifyText).perform()
            else           -> ConsoleUtils.error(context.runId, "Unknown event notification: " + notifyConfig!!)
        }
    }

    private fun isConditionMatched(event: ExecutionEvent): Boolean {
        val condition = context.getStringData(event.conditionalVar)
        if (StringUtils.isBlank(condition)) return true

        val filters = NexialFilterList(condition)
        return if (CollectionUtils.isEmpty(filters)) true
        else filters.isMatched(context, "handling event " + event.eventName)
    }

//    fun setContext(context: ExecutionContext) {
//        this.context = context
//    }
//    fun setMailTagLine(mailTagLine: String) {
//        this.mailTagLine = mailTagLine
//    }
//
//    fun setMailIncludeMeta(mailIncludeMeta: Boolean) {
//        this.mailIncludeMeta = mailIncludeMeta
//    }
//
//    fun setSmsIncludeMeta(smsIncludeMeta: Boolean) {
//        this.smsIncludeMeta = smsIncludeMeta
//    }

    // private String gatherMeta() {
    //     String runHost = StringUtils.upperCase(EnvUtils.getHostName());
    //     return "From " + StringUtils.defaultString(USER_NAME, "unknown") + "@" + runHost +
    //            " using " + ExecUtils.deriveJarManifest();
    // }
    //
    // private void doEmail(ExecutionEvent event, String config) {
    //     String msgId = context.getRunId();
    //
    //     NexialMailer nexialMailer = context.getNexialMailer();
    //     if (nexialMailer == null) {
    //         ConsoleUtils.log(msgId, event + " - nexial mailer not configured for notification; SKIPPING...");
    //         ConsoleUtils.log(msgId, event + " - " + config);
    //         return;
    //     }
    //
    //     List<String> recipientList = TextUtils.toList(StringUtils.substringBefore(config, EVENT_CONFIG_SEP),
    //                                                   context.getTextDelim(),
    //                                                   true);
    //     if (CollectionUtils.isEmpty(recipientList)) {
    //         ConsoleUtils.log(msgId, event + " - invalid or no recipient specified; SKIPPING...");
    //         ConsoleUtils.log(msgId, event + " - " + config);
    //         return;
    //     }
    //
    //     String text = StringUtils.trim(StringUtils.substringAfter(config, EVENT_CONFIG_SEP)) +
    //                   (mailIncludeMeta ? "\n" + gatherMeta() : "") + "\n" + mailTagLine + "\n";
    //
    //     // todo: need const, doc.
    //     boolean useHTML = context.getBooleanData("nexial.notifyUsingHTML");
    //     MailData mailData = MailData.newInstance(useHTML ? "text/html" : "text/plain")
    //                                 .setToAddr(recipientList)
    //                                 .setContent(text)
    //                                 .setSubject(MAIL_NOTIF_SUBJECT_PREFIX + event.getDescription());
    //
    //     try {
    //         nexialMailer.sendMail(mailData);
    //     } catch (MessagingException e) {
    //         ConsoleUtils.log(msgId, event + " - nexial mailer not configured properly: " + e.getMessage());
    //         ConsoleUtils.log(msgId, event + " - " + config);
    //     }
    // }
    //
    // private void doSms(ExecutionEvent event, String config) {
    //     if (StringUtils.isBlank(config) || !StringUtils.contains(config, EVENT_CONFIG_SEP)) {
    //         ConsoleUtils.log(context.getRunId(), event + " - notification not properly configured: " + config);
    //     } else {
    //         try {
    //             String phone = StringUtils.substringBefore(config, EVENT_CONFIG_SEP);
    //             List<String> phones = TextUtils.toList(phone, context.getTextDelim(), true);
    //
    //             String text = StringUtils.substringAfter(config, EVENT_CONFIG_SEP);
    //             if (smsIncludeMeta) { text += "\n\n" + gatherMeta(); }
    //
    //             context.getSmsHelper().send(phones, text);
    //         } catch (IntegrationConfigException e) {
    //             ConsoleUtils.log(context.getRunId(), event + " - sms not configured: " + e.getMessage());
    //             ConsoleUtils.log(context.getRunId(), event + " - " + config);
    //         }
    //     }
    // }
    //
    // private void doTts(ExecutionEvent event, String text) {
    //     try {
    //         SoundMachine dj = context.getDj();
    //         if (dj == null) { return; }
    //         dj.speak(text, true);
    //     } catch (JavaLayerException | IntegrationConfigException e) {
    //         ConsoleUtils.log(context.getRunId(), event + " - tts not configured: " + e.getMessage());
    //         ConsoleUtils.log(context.getRunId(), event + " - " + text);
    //     }
    // }
    //
    // private void doConsole(ExecutionEvent event, String text) {
    //     ConsoleUtils.doPause(context, event.getDescription() + " - " + text);
    // }
    //
    // private void doAudio(ExecutionEvent event, String text) {
    //     try {
    //         SoundMachine dj = context.getDj();
    //         if (dj == null) { return; }
    //         dj.playAudio(text);
    //     } catch (JavaLayerException | LineUnavailableException | IOException | UnsupportedAudioFileException e) {
    //         ConsoleUtils.log(context.getRunId(), event + " - audio playback error: " + e.getMessage());
    //         ConsoleUtils.log(context.getRunId(), event + " - " + text);
    //     }
    // }
}
