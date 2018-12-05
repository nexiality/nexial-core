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

package org.nexial.core.aws;

import java.io.InputStream;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.AmazonPollyClientBuilder;
import com.amazonaws.services.polly.model.*;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.advanced.AdvancedPlayer;

import static com.amazonaws.services.polly.model.TextType.Ssml;
import static org.nexial.core.NexialConst.Data.MAX_TTS_LENGTH;

/**
 * proxy class to interact with AWS Polly (Text-to-Speech)
 */
public class TtsHelper extends AwsSupport {
    protected LanguageCode languageCode = LanguageCode.EnGB;
    protected OutputFormat outputFormat = OutputFormat.Mp3;
    protected String sampleRate = "8000";
    protected Gender voiceGender = Gender.Female;
    protected AmazonPolly tts;
    protected Voice voice;

    static final String SSML_SHORT_PAUSE = "<break time=\"100ms\"/>";
    static final String SSML_PAUSE = "<break time=\"200ms\"/>";

    public void setLanguageCode(LanguageCode languageCode) { this.languageCode = languageCode; }

    public void setLanguage(String languageCode) { this.languageCode = LanguageCode.fromValue(languageCode); }

    public void setOutputFormat(OutputFormat outputFormat) { this.outputFormat = outputFormat; }

    public void setSampleRate(String sampleRate) { this.sampleRate = sampleRate; }

    public void setVoiceGender(Gender voiceGender) { this.voiceGender = voiceGender; }

    public void setVoiceGenderString(String voiceGender) { this.voiceGender = Gender.valueOf(voiceGender); }

    public void init() {
        if (StringUtils.isBlank(accessKey)) { ConsoleUtils.log("accessKey not set; tts DISABLED!"); }
        if (StringUtils.isBlank(secretKey)) { ConsoleUtils.log("secretKey not set; tts DISABLED!"); }

        if (!isReadyForUse()) { throw new RuntimeException("Missing critical configuration; Not ready for use"); }

        try {
            if (tts == null) {
                ClientConfiguration clientConfiguration = new ClientConfiguration();
                tts = AmazonPollyClientBuilder.standard()
                                              .withCredentials(resolveCredentials(region))
                                              .withClientConfiguration(clientConfiguration)
                                              .withRegion(region)
                                              .build();
            }

            if (voice == null) {
                // Create describe voices request.
                DescribeVoicesRequest describeVoicesRequest = new DescribeVoicesRequest();
                describeVoicesRequest.setLanguageCode(languageCode);

                // Synchronously ask Amazon Polly to describe available TTS voices.
                DescribeVoicesResult describeVoicesResult = tts.describeVoices(describeVoicesRequest);

                // for EN-GR, this would be Emma
                // for EU-US, this would be Salli
                voice = describeVoicesResult.getVoices().get(0);
                // todo: not yet implemented... need to convert to voice id
                voice.setGender(voiceGender);
            }
        } catch (AmazonClientException e) {
            throw new RuntimeException("Critical error: " + e.getMessage());
        }
    }

    public void speak(String text, boolean wait) throws JavaLayerException {
        if (StringUtils.isBlank(text)) { throw new IllegalArgumentException("No text is found; tts aborted"); }

        if (ExecUtils.isRunningInZeroTouchEnv()) { return; }

        if (StringUtils.length(text) > MAX_TTS_LENGTH) {
            ConsoleUtils.log("truncating TTS text to " + MAX_TTS_LENGTH + " characters");
            text = StringUtils.truncate(text, MAX_TTS_LENGTH);
        }

        //get the audio stream and create an MP3 player
        AdvancedPlayer player =
            new AdvancedPlayer(synthesize(text), FactoryRegistry.systemRegistry().createAudioDevice());
        // todo: need better strategy
        // player.setPlayBackListener(new PlaybackListener() {
        //     @Override
        //     public void playbackStarted(PlaybackEvent evt) {
        //         System.out.println("Playback started");
        //         System.out.println(text);
        //     }
        //
        //     @Override
        //     public void playbackFinished(PlaybackEvent evt) {
        //         System.out.println("Playback finished");
        //     }
        // });

        if (wait) {
            player.play();
        } else {
            new Thread(() -> {
                // play it!
                try {
                    player.play();
                } catch (JavaLayerException e) {
                    ConsoleUtils.error(e.getMessage());
                }
            }).start();
        }
    }

    protected InputStream synthesize(String text) {
        return tts.synthesizeSpeech(new SynthesizeSpeechRequest().withTextType(Ssml)
                                                                 .withText(buildSsml(text))
                                                                 .withVoiceId(voice.getId())
                                                                 .withOutputFormat(outputFormat)
                                                                 .withSampleRate(sampleRate))
                  .getAudioStream();
    }

    @NotNull
    protected String buildSsml(String text) {
        // treat text into SSML for more natural speeches
        // https://docs.aws.amazon.com/polly/latest/dg/supported-ssml.html
        StringBuilder speechBuffer = new StringBuilder("<speak>");

        text = StringUtils.replace(text, "…", "…" + SSML_PAUSE);
        text = StringUtils.replace(text, "...", "…" + SSML_PAUSE);

        text = StringUtils.replaceAll(text, "\\[([\\w\\d_\\- \\.\\#]+)\\]", "$1" + SSML_SHORT_PAUSE);

        text = StringUtils.replaceAll(text, "^([0-9A-Z_\\-]+)$", "<emphasis>$1</emphasis>");
        text = StringUtils.replaceAll(text, "(\\s)([0-9A-Z_\\-]+)$", " <emphasis>$2</emphasis>");
        text = StringUtils.replaceAll(text, "^([0-9A-Z_\\-]+)([\\s\\!\\?\\.])", "<emphasis>$1</emphasis>$2");
        text = StringUtils.replaceAll(text, "([0-9A-Z_\\-]+)([\\s\\!\\?\\.])", "<emphasis>$1</emphasis>$2");

        speechBuffer.append(text)
                    .append("</speak>");

        return speechBuffer.toString();
    }
}
