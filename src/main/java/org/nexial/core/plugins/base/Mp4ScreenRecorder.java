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
import java.awt.image.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.nexial.core.NexialConst.Project;
import org.nexial.core.ShutdownAdvisor;
import org.nexial.core.utils.ConsoleUtils;

import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.IMetaData;
import com.xuggle.xuggler.IRational;

import static com.xuggle.xuggler.ICodec.ID.CODEC_ID_H264;
import static java.awt.image.BufferedImage.*;
import static java.io.File.separator;
import static java.util.concurrent.TimeUnit.*;
import static org.nexial.core.NexialConst.Data.RECORDER_TYPE_MP4;
import static org.nexial.core.NexialConst.OPT_OUT_DIR;
import static org.nexial.core.plugins.base.Mp4ScreenRecorder.Quality.HIGH;

public class Mp4ScreenRecorder extends MediaToolAdapter implements Runnable, ScreenRecorder {
    private static final String TITLE = "title";
    private static final double DEF_FRAME_RATE = 30.0;

    private final Dimension screenBounds;
    private final Robot robot;
    private IMediaWriter writer;
    private final ScheduledExecutorService pool;
    private long startTime;
    private int frameCount = 0;
    private String targetVideoFile;
    private String title;

    public enum Quality {
        HIGH(1),
        MEDIUM(2),
        LOW(4);

        private final int divisor;

        Quality(int divisor) { this.divisor = divisor; }

        int getDivisor() { return divisor; }
    }

    public Mp4ScreenRecorder() throws AWTException {
        screenBounds = AwtUtils.getScreenDimension(0);
        robot = new Robot();
        pool = Executors.newScheduledThreadPool(1);
        ShutdownAdvisor.addAdvisor(this);
    }

    @Override
    public void start() {
        targetVideoFile =
            Project.appendCapture(
                StringUtils.appendIfMissing(
                    System.getProperty(OPT_OUT_DIR, SystemUtils.getJavaIoTmpDir().getAbsolutePath()),
                    separator)) +
            separator +
            RandomStringUtils.randomAlphabetic(10) +
            "." + RECORDER_TYPE_MP4;
        startCapture();
    }

    @Override
    public void start(String fullpath) {
        targetVideoFile = fullpath;
        startCapture();
    }

    public void stop() {
        ConsoleUtils.log("stopping screen recording service...");
        try {
            pool.shutdown();
            pool.awaitTermination(1, SECONDS);

            if (writer.isOpen()) {
                writer.flush();
                writer.close();
            }
        } catch (InterruptedException e) {
            ConsoleUtils.log("Not able to stop recording : due to error :   " + e.getMessage());
        }
    }

    @Override
    public void setTitle(String title) { this.title = title; }

    @Override
    public void run() {
        BufferedImage screen = robot.createScreenCapture(new Rectangle(screenBounds));
        BufferedImage bgrScreen = convertToType(screen, TYPE_3BYTE_BGR);
        writer.encodeVideo(0, bgrScreen, System.nanoTime() - startTime, NANOSECONDS);
        frameCount++;
    }

    @Override
    public boolean mustForcefullyTerminate() { return true; }

    @Override
    public void forcefulTerminate() { stop(); }

    public static BufferedImage convertToType(BufferedImage sourceImage, int targetType) {
        BufferedImage image;
        if (sourceImage.getType() == targetType) { image = sourceImage; } else {
            image = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), targetType);
            image.getGraphics().drawImage(sourceImage, 0, 0, null);
        }

        return image;
    }

    protected void startCapture() {
        writer = ToolFactory.makeWriter(targetVideoFile);
        writer.addVideoStream(0,
                              0,
                              CODEC_ID_H264,
                              screenBounds.width / HIGH.getDivisor(),
                              screenBounds.height / HIGH.getDivisor());
        writer.getContainer().getStream(0).getStreamCoder().setFrameRate(IRational.make(DEF_FRAME_RATE));

        if (StringUtils.isNotEmpty(title)) {
            IMetaData metaData = writer.getContainer().getMetaData();
            metaData.setValue(TITLE, title);
        }

        startTime = System.nanoTime();
        pool.scheduleAtFixedRate(this, 0L, (long) (1000.0 / DEF_FRAME_RATE), MILLISECONDS);
    }
}

