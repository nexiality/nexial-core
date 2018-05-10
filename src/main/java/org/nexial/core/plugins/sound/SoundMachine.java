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

package org.nexial.core.plugins.sound;

import java.io.*;
import java.util.Map;

import javax.sound.sampled.*;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.aws.TtsHelper;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import static javax.sound.sampled.LineEvent.Type.START;
import static javax.sound.sampled.LineEvent.Type.STOP;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

/**
 * general utility to play audio files or TTS
 */
public class SoundMachine {
    private static final Map<String, String> SOUND_RESOURCES = TextUtils.toMap("=",
                                                                               "error1=sound/error1.wav",
                                                                               "error2=sound/error2.wav",
                                                                               "error3=sound/error3.wav",
                                                                               "error4=sound/error4.wav",
                                                                               "error5=sound/error5.wav",

                                                                               "alert1=sound/alert1.mp3",
                                                                               "alert2=sound/alert2.mp3",
                                                                               "alert3=sound/alert3.mp3",
                                                                               "alert4=sound/alert4.mp3",
                                                                               "alert5=sound/alert5.mp3",

                                                                               "chime1=sound/chime1.mp3",
                                                                               "chime2=sound/chime2.wav",
                                                                               "chime3=sound/chime3.wav",
                                                                               "chime4=sound/chime4.wav",
                                                                               "chime5=sound/chime5.mp3",
                                                                               "chime6=sound/chime6.mp3",

                                                                               "fail1=sound/fail1.mp3",
                                                                               "fail2=sound/fail2.wav",

                                                                               "success1=sound/success1.wav",
                                                                               "success2=sound/success2.mp3",
                                                                               "success3=sound/success3.wav"
                                                                              );

    private TtsHelper tts;

    class AudioLineListener implements LineListener {
        boolean completed = false;
        boolean started = false;

        @Override
        public void update(LineEvent event) {
            if (event.getType() == START) { started = true; }
            if (event.getType() == STOP) { completed = true; }
        }
    }

    class AudioPlayer implements Runnable {
        private AudioLineListener listener = new AudioLineListener();
        private Clip audioClip;

        public AudioPlayer(Clip audioClip) {
            this.audioClip = audioClip;
        }

        @Override
        public void run() {
            if (!listener.started) {
                audioClip.addLineListener(listener);
                audioClip.start();
            }

            // clip.loop(repeat);

            while (!listener.completed) {
                try { Thread.sleep(200);} catch (InterruptedException e) { }
            }

            audioClip.close();
        }
    }

    public void setTts(TtsHelper tts) { this.tts = tts; }

    public static boolean shouldSkip() { return CheckUtils.isRunningInCi() || CheckUtils.isRunningInJUnit(); }

    public boolean playAudio(String audio)
        throws IOException, JavaLayerException, UnsupportedAudioFileException, LineUnavailableException {

        if (shouldSkip()) { return false; }

        requiresNotBlank(audio, "Invalid audio preset or file", audio);

        String audioFile = SOUND_RESOURCES.getOrDefault(audio, audio);
        if (StringUtils.endsWithIgnoreCase(audioFile, ".mp3")) {
            playMp3(audioFile);
        } else {
            playWav(audioFile);
        }

        return true;
    }

    public void speak(String text, boolean wait) throws IOException, JavaLayerException {
        if (tts == null || !tts.isReadyForUse()) {
            throw new IOException("Nexial speech helper not probably configured. " +
                                  "Please contact support for more details.");
        }

        tts.init();
        tts.speak(text, wait);
    }

    public boolean isReadyFroTTS() { return tts != null && tts.isReadyForUse(); }

    public void onError(ExecutionContext context) {
        if (context == null) { return; }
        String sound = context.getStringData(SOUND_ON_ERROR);
        if (StringUtils.isBlank(sound)) { return; }
        playOrSpeak(context, sound, "onError()");
    }

    public void onPause(ExecutionContext context) {
        if (context == null) { return; }
        String sound = context.getStringData(SOUND_ON_PAUSE);
        if (StringUtils.isBlank(sound)) { return; }
        playOrSpeak(context, sound, "onPause()", true);
    }

    public void playOrSpeak(ExecutionContext context, String sound, String event) {
        playOrSpeak(context, sound, event, false);
    }

    public void playOrSpeak(ExecutionContext context, String sound, String event, boolean wait) {
        try {
            if (StringUtils.startsWith(sound, TTS_PREFIX)) {
                String message = StringUtils.substringAfter(sound, TTS_PREFIX);
                if (tts == null || !tts.isReadyForUse()) {
                    ConsoleUtils.log(context.getRunId(), event + " - tts not configured; SKIPPED");
                    ConsoleUtils.log(context.getRunId(), event + " - " + message);
                } else {
                    speak(message, wait);
                }
            } else {
                playAudio(sound);
            }
        } catch (IOException | JavaLayerException | LineUnavailableException | UnsupportedAudioFileException e) {
            ConsoleUtils.error(context.getRunId(), event + " - Error playing sound: " + e.getMessage());
        }
    }

    private void playMp3(String audioFile) throws FileNotFoundException, JavaLayerException {
        BufferedInputStream bis;
        if (FileUtil.isFileReadable(audioFile, 5)) {
            bis = new BufferedInputStream(new FileInputStream(audioFile));
        } else {
            InputStream audioStream = ResourceUtils.getInputStream(audioFile);
            if (audioStream == null) { throw new FileNotFoundException("No audio resource '" + audioFile + "' found"); }
            bis = new BufferedInputStream(audioStream);
        }

        Player player = new Player(bis);

        // run in background
        new Thread(() -> {
            try {
                player.play();
            } catch (JavaLayerException e) {
                ConsoleUtils.log("Error while playing " + audioFile + ": " + e.getMessage());
            }
        }).start();
    }

    private void playWav(String audioFile) throws IOException, UnsupportedAudioFileException, LineUnavailableException {

        AudioInputStream audioIn;
        if (FileUtil.isFileReadable(audioFile, 5)) {
            audioIn = AudioSystem.getAudioInputStream(new File(audioFile));
        } else {
            InputStream audioStream = ResourceUtils.getInputStream(audioFile);
            if (audioStream == null) { throw new FileNotFoundException("No audio resource '" + audioFile + "' found"); }
            BufferedInputStream input = new BufferedInputStream(audioStream);
            audioIn = AudioSystem.getAudioInputStream(input);
        }

        Clip clip = AudioSystem.getClip();
        clip.open(audioIn);

        AudioPlayer player = new AudioPlayer(clip);
        player.run();

    }
}
