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
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.aws.TtsHelper;
import org.nexial.core.plugins.ws.Response;
import org.nexial.core.plugins.ws.WebServiceClient;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.ExecUtils;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import static javax.sound.sampled.LineEvent.Type.START;
import static javax.sound.sampled.LineEvent.Type.STOP;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

/**
 * general utility to play audio files or TTS
 */
public class SoundMachine {
    private TtsHelper tts;
    private String notReadMessage;
    private Map<String, String> soundFileInfo;


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

        public AudioPlayer(Clip audioClip) { this.audioClip = audioClip; }

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

    public void setSoundFileInfo(Map<String, String> soundFileInfo) {
        this.soundFileInfo = soundFileInfo;
    }

    public boolean isReadyFroTTS() { return tts != null && tts.isReadyForUse(); }

    public void speak(String text, boolean wait) throws JavaLayerException, IntegrationConfigException {
        if (ExecUtils.isRunningInZeroTouchEnv()) { return; }

        if (tts == null || !tts.isReadyForUse()) { throw new IntegrationConfigException(notReadMessage); }

        tts.init();
        tts.speak(text, wait);
    }

    public boolean playAudio(String audio)
        throws IOException, JavaLayerException, UnsupportedAudioFileException, LineUnavailableException {

        requiresNotBlank(audio, "Invalid audio preset or file", audio);

        String audioFile = StringUtils.trim(audio);
        if (!FileUtil.isFileReadable(new File(audioFile))) {
            audioFile = downloadFile(audioFile);
        }
        if (StringUtils.endsWithIgnoreCase(audioFile, ".mp3")) {
            playMp3(audioFile);
        } else {
            playWav(audioFile);
        }

        return true;
    }

    public void setNotReadMessage(String message) { this.notReadMessage = message; }

    protected void init() { if (tts != null && tts.isReadyForUse()) { tts.init(); } }

    private void playMp3(String audioFile) throws FileNotFoundException, JavaLayerException {
        if (ExecUtils.isRunningInZeroTouchEnv()) { return; }

        String audioResource = StringUtils.trim(audioFile);

        BufferedInputStream bis;
        if (FileUtil.isFileReadable(audioResource, 5)) {
            bis = new BufferedInputStream(new FileInputStream(audioResource));
        } else {
            InputStream audioStream = ResourceUtils.getInputStream(audioResource);
            if (audioStream == null) { throw new FileNotFoundException("No audio resource '" + audioResource + "' found"); }
            bis = new BufferedInputStream(audioStream);
        }

        Player player = new Player(bis);

        // run in background
        new Thread(() -> {
            try {
                player.play();
            } catch (JavaLayerException e) {
                ConsoleUtils.log("Error while playing " + audioResource + ": " + e.getMessage());
            }
        }).start();
    }

    private void playWav(String audioFile) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        if (ExecUtils.isRunningInZeroTouchEnv()) { return; }

        audioFile = StringUtils.trim(audioFile);

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

    private String downloadFile(String audioFile) throws IOException {
        if (ExecUtils.isRunningInZeroTouchEnv()) { return ""; }

        String audioResource = StringUtils.trim(audioFile);
        String extn = soundFileInfo.get("fileExtension");
        String fileName = StringUtils.appendIfMissing(audioResource, extn);
        String downloadTo = soundFileInfo.get("downloadTo") + fileName;
        String url = soundFileInfo.get("downloadFrom") + fileName;
        if (!new File(downloadTo).exists()) {
            WebServiceClient wsClient = new WebServiceClient(null).configureAsQuiet().disableContextConfiguration();
            Response response = wsClient.download(url, null, downloadTo);
            if(response.getReturnCode() >= 400){
                throw new IOException("Unable to download audio file: "+audioFile);
            }
        }
        return downloadTo;
    }
}
