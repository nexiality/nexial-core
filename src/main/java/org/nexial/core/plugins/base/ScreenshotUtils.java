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
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriverException;

import static org.nexial.core.NexialConst.OPT_LAST_SCREENSHOT_NAME;
import static org.nexial.core.NexialConst.SCREENSHOT_EXT;
import static org.openqa.selenium.OutputType.BASE64;

public class ScreenshotUtils {
    private ScreenshotUtils() { }

    public static File saveScreenshot(TakesScreenshot screenshot, String filename, Rectangle rect) {
        File imageFile = saveScreenshot(screenshot, filename);
        if (imageFile == null) { return null; }
        if (rect == null) { return imageFile; }

        File output = new File(filename);
        String ext = StringUtils.substringAfterLast(filename, ".");

        try {
            BufferedImage image = ImageIO.read(imageFile);
            BufferedImage cropped = image.getSubimage(Math.max(rect.getX(), 0),
                                                      Math.max(rect.getY(), 0),
                                                      Math.min(rect.getWidth(), image.getWidth()),
                                                      Math.min(rect.getHeight(), image.getHeight()));
            if (ImageIO.write(cropped, ext, output)) {
                return output;
            } else {
                ConsoleUtils.error("Unable to save cropped screen capture successfully");
                return imageFile;
            }
        } catch (Exception e) {
            ConsoleUtils.error("failed to capture screen capture to '" + filename + "': " + e.getMessage());
            return imageFile;
        }
    }

    public static File saveDesktopScreenshot(String filename) {
        if (filename == null) { throw new IllegalArgumentException("filename is null"); }
        File output = new File(filename);

        try {
            BufferedImage image =
                new Robot().createScreenCapture(new java.awt.Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            ImageIO.write(image, StringUtils.removeStart(SCREENSHOT_EXT, "."), output);
            return output;
        } catch (HeadlessException | AWTException | IOException e) {
            ConsoleUtils.error("failed to save screen capture to '" + filename + "': " + e.getMessage());
            return null;
        }
    }

    public static File saveScreenshot(TakesScreenshot screenshot, String filename) {
        if (screenshot == null) { throw new IllegalArgumentException("screenshot object is null"); }
        if (filename == null) { throw new IllegalArgumentException("filename is null"); }

        File f = new File(filename);
        File dir = f.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            log("directory creation failed for '" + dir.getAbsolutePath() + "'... this might not work...");
        }

        String screen;
        try {
            // if (screenshot instanceof RemoteWebDriver) { ((RemoteWebDriver) screenshot).manage().window().fullscreen();}
            screen = screenshot.getScreenshotAs(BASE64);
        } catch (WebDriverException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnhandledAlertException) {
                log("screen capture not support when Javascript alert present");
            } else {
                log("Error when performing screen capture: " + e.getMessage());
            }
            return null;
        }

        if (screen == null) {
            log("Failed to capture screenshot - null returned.");
            return null;
        }

        try {
            FileUtils.writeByteArrayToFile(f, Base64.decodeBase64(screen.getBytes()));
            ExecutionContext context = ExecutionThread.get();
            if (context != null) { context.setData(OPT_LAST_SCREENSHOT_NAME, f.getName(), true); }
            //log("screen captured to '" + filename + "'");
            return f;
        } catch (Exception e) {
            ConsoleUtils.error("failed to save screen capture to '" + filename + "': " + e.getMessage());
            return null;
        }
    }

    protected static void log(String message) {
        if (StringUtils.isBlank(message)) { return; }
        ExecutionContext context = ExecutionThread.get();
        if (context != null) {
            context.getLogger().log(context, message);
        } else {
            ConsoleUtils.log(message);
        }
    }
}
