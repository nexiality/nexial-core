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

package org.nexial.core;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import javax.mail.MessagingException;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.javamail.MailObjectSupport;
import org.nexial.commons.utils.EnvUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.sound.SoundMachine;
import org.nexial.core.reports.ExecutionMailConfig;
import org.nexial.core.reports.MailNotifier;
import org.nexial.core.reports.Mailer;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtil;

import javazoom.jl.decoder.JavaLayerException;

import static org.apache.commons.lang3.SystemUtils.USER_NAME;
import static org.nexial.core.ExecutionEventListener.Events.ExecutionPause;
import static org.nexial.core.NexialConst.Data.*;

public class ExecutionEventListener {
    private static final String EVENT_CONFIG_SEP = "|";

    private ExecutionContext context;
    private String mailTagLine;
    private boolean mailIncludeMeta;
    private boolean smsIncludeMeta;

    enum Events {
        ScriptStart("onScriptStart", NOTIFY_ON_START, "Script Execution Started"),
        ScriptComplete("onScriptComplete", NOTIFY_ON_COMPLETE, "Script Execution Completed"),
        ErrorOccurred("onError", NOTIFY_ON_ERROR, "Error Occurred"),
        ExecutionPause("onPause", NOTIFY_ON_PAUSE, "Execution Paused");

        private String name;
        private String variable;
        private String description;

        Events(String name, String variable, String description) {
            this.name = name;
            this.variable = variable;
            this.description = description;
        }

        public String getName() { return name; }

        public String getVariable() { return variable; }

        public String getDescription() { return description; }
    }

    public ExecutionEventListener() { }

    public void setContext(ExecutionContext context) { this.context = context; }

    public void setMailTagLine(String mailTagLine) { this.mailTagLine = mailTagLine; }

    public void setMailIncludeMeta(boolean mailIncludeMeta) { this.mailIncludeMeta = mailIncludeMeta;}

    public void setSmsIncludeMeta(boolean smsIncludeMeta) { this.smsIncludeMeta = smsIncludeMeta; }

    public void onScriptStart() { handleEvent(Events.ScriptStart); }

    public void onScriptComplete() { handleEvent(Events.ScriptComplete); }

    public void onError() { handleEvent(Events.ErrorOccurred); }

    public void onPause() { handleEvent(ExecutionPause); }

    public void afterPause() {
        // nothing for now
    }

    public void handleEvent(Events event) {
        String notifyConfig = context.getStringData(event.variable);
        if (StringUtils.isBlank(notifyConfig)) {
            // fallback
            // todo: to be removed by v1.3
            doSound(event);
            return;
        }

        String notifyPrefix = StringUtils.substringBefore(notifyConfig, ":") + ":";
        if (StringUtils.isBlank(notifyPrefix)) {
            ConsoleUtils.error("Unknown notification for [" + event.name + "]: " + notifyConfig);
            return;
        }

        String notifyText = StringUtils.substringAfter(notifyConfig, ":");

        switch (notifyPrefix) {
            case TTS_PREFIX:
                doTts(event.name, notifyText);
                break;
            case SMS_PREFIX:
                doSms(event.name, notifyText);
                break;
            case AUDIO_PREFIX:
                doAudio(event.name, notifyText);
                break;
            case EMAIL_PREFIX:
                doEmail(event.name, notifyText);
                break;
            case CONSOLE_PREFIX: {
                if (event == ExecutionPause) {
                    ConsoleUtils.error(context.getRunId(),
                                       event.name + " with [console] notification doesn't make sense. SKIPPING...");
                } else {
                    doConsole(event.name, notifyText);
                }
                break;
            }
            default:
                ConsoleUtils.error(context.getRunId(), "Unknown event notification: " + notifyConfig);
                break;
        }
    }

    private void doEmail(String event, String config) {
        ExecutionMailConfig mailConfig = ExecutionMailConfig.get();
        if (mailConfig == null) { mailConfig = ExecutionMailConfig.configure(context); }

        if (!mailConfig.isReadyForNotification() || context.getMailNotifier() == null) {
            ConsoleUtils.log(context.getRunId(), event + " - sms not configured for notification; SKIPPING...");
            ConsoleUtils.log(context.getRunId(), event + " - " + config);
            return;
        }

        String recipients = StringUtils.substringBefore(config, EVENT_CONFIG_SEP);
        List<String> recipientList = TextUtils.toList(recipients, context.getTextDelim(), true);

        String text = StringUtils.trim(StringUtils.substringAfter(config, EVENT_CONFIG_SEP));
        if (mailIncludeMeta) { text += "\n" + gatherMeta(); }
        text += "\n" + mailTagLine + "\n";

        try {
            // setup
            MailObjectSupport mailObjectSupport = new MailObjectSupport();
            mailObjectSupport.configure();

            Mailer mailer = new Mailer();
            mailer.setMailer(mailObjectSupport);

            MailNotifier mailNotifier = context.getMailNotifier();
            mailNotifier.setMailer(mailer);

            mailNotifier.sendPlainText(recipientList, "[nexial-notification] " + event, text);
        } catch (MessagingException e) {
            ConsoleUtils.log(context.getRunId(), event + " - sms not configured properly: " + e.getMessage());
            ConsoleUtils.log(context.getRunId(), event + " - " + config);
        }
    }

    private String gatherMeta() {
        String runHost;
        try {
            runHost = StringUtils.upperCase(EnvUtils.getHostName());
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unable to determine host name of current host: " + e.getMessage());
        }

        return "From " + USER_NAME + "@" + runHost + " using " + ExecUtil.deriveJarManifest();
    }

    private void doSms(String event, String config) {
        if (StringUtils.isBlank(config) || !StringUtils.contains(config, EVENT_CONFIG_SEP)) {
            ConsoleUtils.log(context.getRunId(), event + " - notification not properly configured: " + config);
        } else {
            try {
                String phone = StringUtils.substringBefore(config, EVENT_CONFIG_SEP);
                List<String> phones = TextUtils.toList(phone, context.getTextDelim(), true);

                String text = StringUtils.substringAfter(config, EVENT_CONFIG_SEP);
                if (smsIncludeMeta) { text += "\n\n" + gatherMeta(); }

                context.getSmsHelper().send(phones, text);
            } catch (IntegrationConfigException e) {
                ConsoleUtils.log(context.getRunId(), event + " - sms not configured: " + e.getMessage());
                ConsoleUtils.log(context.getRunId(), event + " - " + config);
            }
        }
    }

    private void doTts(String event, String text) {
        try {
            SoundMachine dj = context.getDj();
            if (dj == null) { return; }
            dj.speak(text, false);
        } catch (JavaLayerException | IntegrationConfigException e) {
            ConsoleUtils.log(context.getRunId(), event + " - tts not configured: " + e.getMessage());
            ConsoleUtils.log(context.getRunId(), event + " - " + text);
        }
    }

    private void doAudio(String event, String text) {
        try {
            SoundMachine dj = context.getDj();
            if (dj == null) { return; }
            dj.playAudio(text);
        } catch (JavaLayerException | LineUnavailableException | IOException | UnsupportedAudioFileException e) {
            ConsoleUtils.log(context.getRunId(), event + " - audio playback error: " + e.getMessage());
            ConsoleUtils.log(context.getRunId(), event + " - " + text);
        }
    }

    private void doConsole(String event, String text) { ConsoleUtils.pause(context, event + " - " + text); }

    private void doSound(Events event) { doSound(event, false); }

    private void doSound(Events event, boolean wait) {
        String sound = context.getStringData(event.variable);
        if (StringUtils.isBlank(sound)) { return; }

        SoundMachine dj = context.getDj();
        if (dj == null) { return; }

        dj.playOrSpeak(context, sound, event.name, wait);
    }
}
