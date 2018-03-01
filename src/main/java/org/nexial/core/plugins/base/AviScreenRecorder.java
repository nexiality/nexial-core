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

package org.nexial.core.plugins.base;

import java.awt.*;
import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.monte.media.Format;
import org.monte.media.math.Rational;

import org.nexial.core.ShutdownAdvisor;

import static org.monte.media.FormatKeys.EncodingKey;
import static org.monte.media.FormatKeys.FrameRateKey;
import static org.monte.media.FormatKeys.MIME_AVI;
import static org.monte.media.FormatKeys.MediaType.FILE;
import static org.monte.media.FormatKeys.MediaType.VIDEO;
import static org.monte.media.FormatKeys.MediaTypeKey;
import static org.monte.media.FormatKeys.MimeTypeKey;
import static org.monte.media.VideoFormatKeys.*;
import static org.monte.screenrecorder.ScreenRecorder.State.RECORDING;

class AviScreenRecorder extends org.monte.screenrecorder.ScreenRecorder implements ScreenRecorder {
    private static final Format FILE_FORMAT_AVI = new Format(MediaTypeKey, FILE, MimeTypeKey, MIME_AVI);
    private static final Format SCREEN_FORMAT_AVI =
        new Format(MediaTypeKey, VIDEO,
                   EncodingKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
                   CompressorNameKey, ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
                   DepthKey, 24,
                   FrameRateKey, Rational.valueOf(15),
                   QualityKey, 1.0f);
    private static final Format MOUSE_FORMAT =
        new Format(MediaTypeKey, VIDEO, EncodingKey, "black", FrameRateKey, Rational.valueOf(30));

    private String targetVideoFile;
    private String title;

    public AviScreenRecorder(GraphicsConfiguration cfg,
                             Format fileFormat,
                             Format screenFormat,
                             Format mouseFormat,
                             Format audioFormat) throws IOException, AWTException {
        super(cfg, fileFormat, screenFormat, mouseFormat, audioFormat);
        // resolveFileExt(fileFormat);
        ShutdownAdvisor.addAdvisor(this);
    }

    public AviScreenRecorder() throws IOException, AWTException {
        // Create an instance of GraphicsConfiguration to get the Graphics configuration
        // of the Screen. This is needed for ScreenRecorder class.
        this(AwtUtils.getGraphicsConfiguration(), FILE_FORMAT_AVI, SCREEN_FORMAT_AVI, MOUSE_FORMAT, null);
    }

    @Override
    public void start(String fullpath) throws IOException {
        targetVideoFile = fullpath;
        super.start();
    }

    @Override
    public void setTitle(String title) { this.title = title; }

    @Override
    public boolean mustForcefullyTerminate() { return getState() != null && getState() == RECORDING; }

    @Override
    public void forcefulTerminate() {
        try {
            stop();
        } catch (IOException e) {
            System.err.println("Unable to forcefully terminate screen recording: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create movie file for recorder screen cast. Screen cast will be stored
     * to file specified within proceeding start() method invocation
     */
    @Override
    protected File createMovieFile(Format fileFormat) throws IOException {
        if (StringUtils.isEmpty(this.targetVideoFile)) { return super.createMovieFile(fileFormat); }

        File targetFile = new File(targetVideoFile);
        if (targetFile.exists()) { targetFile.delete(); }
        return targetFile;
    }

    @Override
    public void start() throws IOException {
        targetVideoFile = null;
        super.start();
    }

    @Override
    public void stop() throws IOException { super.stop(); }

    // private void resolveFileExt(Format fileFormat) { ext = '.' + Registry.getInstance().getExtension(fileFormat);}
}
