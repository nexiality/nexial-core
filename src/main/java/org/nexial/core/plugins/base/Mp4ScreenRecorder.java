/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.base;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.NexialConst.Project;
import org.nexial.core.ShutdownAdvisor;
import org.nexial.core.utils.ConsoleUtils;

import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.MediaToolAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.IMetaData;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStreamCoder;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.xuggle.xuggler.ICodec.ID.CODEC_ID_H264;
import static java.awt.image.BufferedImage.*;
import static java.io.File.separator;
import static java.util.concurrent.TimeUnit.*;
import static org.apache.commons.lang3.SystemUtils.OS_ARCH;
import static org.apache.commons.lang3.SystemUtils.OS_NAME;
import static org.nexial.core.NexialConst.OPT_OUT_DIR;
import static org.nexial.core.NexialConst.Recording.Types.mp4;
import static org.nexial.core.NexialConst.TEMP;
import static org.nexial.core.plugins.base.Mp4ScreenRecorder.Quality.HIGH;

public class Mp4ScreenRecorder extends MediaToolAdapter implements Runnable, ScreenRecorder {
    private static final String TITLE = "title";
    private static final double DEF_FRAME_RATE = 30.0;

    private final Dimension screenBounds;
    private final Robot robot;
    private IMediaWriter writer;
    private final ScheduledExecutorService pool;
    private long startTime;
    // private int frameCount = 0;
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
        GraphicsDevice graphicsDevice = AwtUtils.getGraphicsDevice();

        Dimension d;
        try {
            Rectangle defaultBounds = graphicsDevice.getDefaultConfiguration().getBounds();
            d = new Dimension();
            d.width = defaultBounds.width;
            d.height = defaultBounds.height;
        } catch (Throwable e) {
            // try the `screen` property of this graphics device. If user is using Oracle/OpenJDK, then we'll likely get
            // a value, if not just use 0 (default).
            int screenId;
            try {
                String screenIdString = BeanUtils.getProperty(graphicsDevice, "screen");
                screenId = NumberUtils.toInt(screenIdString, 0);
            } catch (Throwable e1) {
                screenId = 0;
            }

            d = AwtUtils.getScreenDimension(screenId);
        }

        screenBounds = d;
        robot = new Robot();
        pool = Executors.newScheduledThreadPool(1);
        ShutdownAdvisor.addAdvisor(this);
    }

    @Override
    public String getVideoFile() { return targetVideoFile; }

    @Override
    public void start() {
        String outputDir = System.getProperty(OPT_OUT_DIR, TEMP);
        start(Project.appendCapture(outputDir) + separator + RandomStringUtils.randomAlphabetic(10) + "." + mp4.name());
    }

    @Override
    public void start(String fullpath) {
        targetVideoFile = fullpath;
        new File(targetVideoFile).getParentFile().mkdirs();
        startCapture();
    }

    public void stop() {
        ConsoleUtils.log("stopping screen recording service...");
        try {
            pool.shutdown();
            pool.awaitTermination(1, SECONDS);

            if (writer != null && writer.isOpen()) {
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
        // frameCount++;
    }

    @Override
    public boolean mustForcefullyTerminate() { return targetVideoFile != null && writer != null && writer.isOpen(); }

    @Override
    public void forcefulTerminate() { stop(); }

    public static BufferedImage convertToType(BufferedImage sourceImage, int targetType) {
        if (sourceImage.getType() == targetType) { return sourceImage; }

        BufferedImage image = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), targetType);
        image.getGraphics().drawImage(sourceImage, 0, 0, null);
        return image;
    }

    protected void startCapture() {
        try {
            writer = ToolFactory.makeWriter(targetVideoFile);
            writer.addVideoStream(0,
                                  0,
                                  CODEC_ID_H264,
                                  screenBounds.width / HIGH.getDivisor(),
                                  screenBounds.height / HIGH.getDivisor());
            IStreamCoder streamCoder = writer.getContainer().getStream(0).getStreamCoder();
            streamCoder.setFrameRate(IRational.make(DEF_FRAME_RATE));

            if (StringUtils.isNotEmpty(title)) {
                IMetaData metaData = writer.getContainer().getMetaData();
                metaData.setValue(TITLE, title);
            }

            startTime = System.nanoTime();
            pool.scheduleAtFixedRate(this, 0L, (long) (1000.0 / DEF_FRAME_RATE), MILLISECONDS);
        } catch (UnsatisfiedLinkError e) {
            ConsoleUtils.error("Screen Recording is not available for " + OS_NAME + " " + OS_ARCH);
            writer = null;
            throw e;
        } catch (Exception e) {
            ConsoleUtils.error("Screen Recording cannot start successfully: " + e.getMessage());
            writer = null;
            throw e;
        }
    }
}
