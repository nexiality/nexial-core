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

import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.CheckUtils;

import javazoom.jl.decoder.JavaLayerException;

import static org.nexial.core.utils.CheckUtils.*;

public class SoundCommand extends BaseCommand {
    private static final float SAMPLE_RATE = 8000f;

    private AudioFormat af = null;
    private SourceDataLine sdl = null;

    @Override
    public String getTarget() { return "sound"; }

    @Override
    public void init(ExecutionContext context) {
        super.init(context);

        try {
            af = new AudioFormat(
                SAMPLE_RATE, // sampleRate
                8,           // sampleSizeInBits
                1,           // channels
                true,        // signed
                false);      // bigEndian
            sdl = AudioSystem.getSourceDataLine(af);
        } catch (LineUnavailableException e) {
            throw new RuntimeException("Unable to set up sounds: " + e.getMessage(), e);
        }
    }

    public StepResult laser(String repeats) throws LineUnavailableException {
        StepResult skip = skipIfRunningInHandsfreeEnv();
        if (skip != null) { return skip; }

        requiresPositiveNumber(repeats, "Must be a positive number", repeats);

        int repeat = NumberUtils.toInt(repeats);
        requires(repeat > 0, "repeats must be 1 or greater", repeats);

        sdl.open(af);
        sdl.start();

        byte[] buf = new byte[1];
        int step;

        for (int j = 0; j < repeat; j++) {
            step = 10;
            for (int i = 0; i < 2000; i++) {
                buf[0] = ((i % step > 0) ? 32 : (byte) 0);
                if (i % 250 == 0) { step += 2; }
                sdl.write(buf, 0, 1);
            }

            try { Thread.sleep(200); } catch (InterruptedException e) { }
        }

        sdl.drain();
        sdl.stop();
        sdl.close();

        return StepResult.success();
    }

    // public StepResult warp(String repeats) throws LineUnavailableException {
    //     StepResult skip = skipIfRunningInHandsfreeEnv();
    //     if (skip != null) { return skip; }
    //
    //     requiresPositiveNumber(repeats, "Must be a positive number", repeats);
    //
    //     int repeat = NumberUtils.toInt(repeats);
    //     requires(repeat > 0, "repeats must be 1 or greater", repeats);
    //
    //     sdl.open(af);
    //     sdl.start();
    //
    //     byte[] buf = new byte[1];
    //     int step;
    //
    //     for (int j = 0; j < repeat; j++) {
    //         step = 25;
    //         for (int i = 0; i < 2000; i++) {
    //             if (i < 500) {
    //                 buf[0] = ((i % step > 0) ? 32 : (byte) 0);
    //                 if (i % 25 == 0) { step--; }
    //             } else {
    //                 buf[0] = ((i % step > 0) ? 32 : (byte) 0);
    //                 if (i % 50 == 0) { step++; }
    //             }
    //             sdl.write(buf, 0, 1);
    //         }
    //     }
    //
    //     sdl.drain();
    //     sdl.stop();
    //     sdl.close();
    //
    //     return StepResult.success();
    // }

    public StepResult play(String audio)
        throws IOException, UnsupportedAudioFileException, LineUnavailableException, JavaLayerException {
        SoundMachine dj = context.getDj();
        if (dj == null) { return StepResult.skipped("sound is not properly configured"); }
        return dj.playAudio(audio) ? StepResult.success() : StepResult.skipped("Current running in CI environment");
    }

    public StepResult speak(String text) throws IOException, JavaLayerException {
        requiresNotBlank(text, "Invalid text", text);

        SoundMachine dj = context.getDj();
        if (dj == null || !dj.isReadyFroTTS()) { return StepResult.skipped("tts is not configured"); }

        dj.speak(text, true);
        return StepResult.success();
    }

    public StepResult speakNoWait(String text) throws IOException, JavaLayerException {
        requiresNotBlank(text, "Invalid text", text);

        SoundMachine dj = context.getDj();
        if (dj == null || !dj.isReadyFroTTS()) { return StepResult.skipped("tts is not configured"); }

        dj.speak(text, false);
        return StepResult.success();
    }

    @Nullable
    private StepResult skipIfRunningInHandsfreeEnv() {
        if (CheckUtils.isRunningInCi()) { return StepResult.skipped("Current running in CI environment"); }
        if (CheckUtils.isRunningInJUnit()) { return StepResult.skipped("Current running in JUnit framework"); }
        return null;
    }
}
