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

package org.nexial.core.plugins.image;

import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.NexialConst.ImageDiffColor;
import org.nexial.core.NexialConst.ImageType;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;

import static java.awt.image.BufferedImage.*;
import static java.io.File.separator;
import static org.nexial.core.NexialConst.ImageDiffColor.DEF_IMAGE_DIFF_COLOR;
import static org.nexial.core.NexialConst.ImageType.png;
import static org.nexial.core.NexialConst.OPT_IMAGE_DIFF_COLOR;
import static org.nexial.core.NexialConst.OPT_IMAGE_TOLERANCE;
import static org.nexial.core.utils.CheckUtils.*;

public class ImageCommand extends BaseCommand implements ForcefulTerminate {
    protected static final DecimalFormat IMAGE_PERCENT_FORMAT = new DecimalFormat("##.00");

    protected boolean mustForcefullyTerminate;

    @Override
    public String getTarget() { return "image"; }

    public StepResult convert(String source, String format, String saveTo) throws IOException {
        requiresReadableFile(source);

        requires(StringUtils.isNotBlank(format), "missing format", format);
        ImageType type;
        try {
            type = ImageType.valueOf(StringUtils.lowerCase(format));
        } catch (IllegalArgumentException e) {
            fail("Invalid/unsupported image format: " + format + ".  Supported formats are png, jpg, gif, bmp");
            return null;
        }

        mustForcefullyTerminate = true;

        // create a blank, RGB, same width and height, and a white background
        File srcFile = new File(source);
        String sourceExt = StringUtils.substringAfterLast(srcFile.getAbsolutePath(), ".");
        String targetExt = type.name();
        if (StringUtils.equalsIgnoreCase(sourceExt, targetExt)) {
            return StepResult.success("source and target format are the same; no conversion needed");
        }

        BufferedImage srcImage = ImageIO.read(srcFile);
        BufferedImage saveImage = new BufferedImage(srcImage.getWidth(), srcImage.getHeight(), type.getImageType());
        saveImage.createGraphics().drawImage(srcImage, 0, 0, null);
        // saveImage.createGraphics().drawImage(srcImage, 0, 0, WHITE, null);

        // write to new file
        File saveFile = resolveSaveTo(saveTo, StringUtils.substringAfterLast(srcFile.getAbsolutePath(), separator));
        ImageIO.write(saveImage, targetExt, saveFile);

        String message = "image '" + source + "' converted to '" + saveFile.getAbsolutePath() + "'";
        log(message);
        return StepResult.success(message);
    }

    public StepResult crop(String image, String dimension, String saveTo) throws IOException {
        requiresReadableFile(image);
        File imageFile = new File(image);

        requiresNotBlank(dimension, "Invalid dimension", dimension);

        String delim = context.getTextDelim();
        String reDigits = "\\s*\\d+\\s*";
        String reDimension = "^" + reDigits + delim + reDigits + delim + reDigits + delim + reDigits + "$";
        requires(RegexUtils.isExact(dimension, reDimension), "Invalid dimension", dimension);

        String[] split = StringUtils.splitByWholeSeparator(dimension, delim);
        int x = NumberUtils.toInt(StringUtils.trim(split[0]));
        int y = NumberUtils.toInt(StringUtils.trim(split[1]));
        int w = NumberUtils.toInt(StringUtils.trim(split[2]));
        int h = NumberUtils.toInt(StringUtils.trim(split[3]));

        mustForcefullyTerminate = true;

        //fill in the corners of the desired crop location here
        BufferedImage existing = ImageIO.read(imageFile);
        BufferedImage newImage = existing.getSubimage(x, y, w, h);
        BufferedImage cropped = new BufferedImage(newImage.getWidth(), newImage.getHeight(), TYPE_INT_RGB);
        Graphics g = cropped.createGraphics();
        g.drawImage(newImage, 0, 0, null);

        String ext = StringUtils.lowerCase(StringUtils.substringAfterLast(image, "."));
        File saveFile = resolveSaveTo(saveTo, StringUtils.substringAfterLast(imageFile.getAbsolutePath(), separator));
        ImageIO.write(cropped, ext, saveFile);

        String message = "image cropped and saved to '" + saveTo + "'";
        log(message);
        return StepResult.success(message);
    }

    public StepResult resize(String image, String width, String height, String saveTo) throws IOException {
        requiresReadableFile(image);
        File imageFile = new File(image);

        requires(NumberUtils.isDigits(width), "invalid width", width);
        int w = NumberUtils.toInt(width);

        requires(NumberUtils.isDigits(height), "invalid height", height);
        int h = NumberUtils.toInt(height);

        mustForcefullyTerminate = true;

        BufferedImage img = ImageIO.read(imageFile);
        BufferedImage resizedImage = new BufferedImage(w, h, TYPE_INT_RGB);

        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        String ext = StringUtils.lowerCase(StringUtils.substringAfterLast(image, "."));
        File saveFile = resolveSaveTo(saveTo, StringUtils.substringAfterLast(imageFile.getAbsolutePath(), separator));
        ImageIO.write(resizedImage, ext, saveFile);

        String message = "image resized and save to '" + saveTo + "'";
        log(message);
        return StepResult.success(message);
    }

    public StepResult compare(String baseline, String actual) {
        requiresNotBlank(baseline, "null/invalid baseline file", baseline);
        requiresNotBlank(actual, "null/invalid actual file", actual);

        float imageTol = context.hasData(OPT_IMAGE_TOLERANCE) ?
                         Float.parseFloat(context.getStringData(OPT_IMAGE_TOLERANCE)) : 0;

        // get baseline image
        File baselineFile = new File(baseline);
        requires(baselineFile.canRead() && baselineFile.length() > 0, "empty or unreadable baseline file", baseline);
        String baselineFullpath = baselineFile.getAbsolutePath();

        BufferedImage baseImage;
        try {
            baseImage = ImageIO.read(baselineFile);
            if (baseImage == null) {
                return StepResult.fail("Baseline '" + baselineFullpath + "' is unlikely a proper image file.");
            }
        } catch (IOException e) {
            return StepResult.fail("Unable to read baseline '" + baselineFullpath + "': " + e.getMessage());
        }

        // get test image
        File testFile = new File(actual);
        requires(testFile.canRead() && testFile.length() > 0, "empty or unreadable test file", testFile);
        String testFullpath = testFile.getAbsolutePath();

        BufferedImage testImage;
        try {
            testImage = ImageIO.read(testFile);
            if (testImage == null) {
                return StepResult.fail("Test file '" + testFullpath + "' is unlikely a proper image file.");
            }
        } catch (IOException e) {
            return StepResult.fail("Unable to read test '" + testFullpath + "': " + e.getMessage());
        }

        int baseImageWidth = baseImage.getWidth(null);
        int baseImageHeight = baseImage.getHeight(null);

        int actualImageWidth = testImage.getWidth(null);
        int actualHeight = testImage.getHeight(null);

        int width = baseImageWidth > actualImageWidth ? actualImageWidth : baseImageWidth;
        int height = baseImageHeight > actualHeight ? actualHeight : baseImageHeight;

        int matchCount = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (baseImage.getRGB(x, y) == testImage.getRGB(x, y)) { matchCount++; }
                // converting to grayscale image then comparing
                //if (ImageComparison.isMatched(baseImage.getRGB(x, y), testImage.getRGB(x, y))) { matchCount++; }
            }
        }

        float dimensionBase = width * height;

        //Percentage Difference Calculation
        float matchPercent = Float.parseFloat(IMAGE_PERCENT_FORMAT.format(matchCount / dimensionBase * 100));
        String message = formatToleranceMessage(matchPercent, imageTol);
        // if (diff < 100) {
        // 	if (diff + imageTol >= 100 && imageTol > 0) { log("Images match using tolerance " + message); }
        // }

        if ((matchPercent + imageTol) < 100) {
            return StepResult.fail("baseline and test images are different " + message);
        } else {
            return StepResult.success("baseline and test images match " + message);
        }
    }

    protected StepResult saveDiff(String baseline, String actual, String saveTo) {
        requiresReadableFile(baseline);
        requiresReadableFile(actual);
        requiresNotBlank(saveTo, "Invalid 'saveTo' location", saveTo);

        // get baseline image
        File baselineFile = new File(baseline);

        // get test image
        File testFile = new File(actual);

        String colorName = context.getStringData(OPT_IMAGE_DIFF_COLOR, DEF_IMAGE_DIFF_COLOR);
        Color color = ImageDiffColor.toColor(colorName);

        try {
            StopWatch watch = new StopWatch();
            watch.start();

            ImageComparison imageComparison = new ImageComparison(baselineFile, testFile);
            BufferedImage outImage = imageComparison.compareImages(color);
            ImageIO.write(outImage, png.toString(), new File(saveTo));

            watch.stop();

            ConsoleUtils.log("time elapsed to save image difference is " + watch.getTime());
            return StepResult.success("file is saved to " + saveTo);
        } catch (IOException e) {
            return StepResult.fail("can not read/write file " + e.getMessage());
        }
    }

    protected StepResult colorbit(String image, String bit, String saveTo) throws IOException {
        requiresReadableFile(image);
        requiresPositiveNumber(bit, "invalid value for bit", bit);
        requiresNotBlank(saveTo, "invalid 'saveTo' location", saveTo);

        int targetImageBit = NumberUtils.toInt(bit);

        // get image image
        File imageFile = new File(image);
        BufferedImage img;
        try {
            img = ImageIO.read(imageFile);
            if (img == null) { return StepResult.fail("File '" + image + "' CANNOT be read as an image"); }
        } catch (IOException e) {
            return StepResult.fail("Unable to read image '" + imageFile.getAbsolutePath() + "': " + e.getMessage());
        }

        int imgBit = img.getColorModel().getPixelSize();
        if (imgBit == targetImageBit) {
            String message = "color bit of source image and target bit are the same; no conversion needed";
            log(message);
            return StepResult.success(message);
        }

        String defaultFileName = StringUtils.substringAfterLast(imageFile.getAbsolutePath(), separator);
        File saveFile = resolveSaveTo(saveTo, defaultFileName);

        String targetExt = StringUtils.lowerCase(StringUtils.substringAfterLast(imageFile.getAbsolutePath(), "."));
        ImageIO.write(BitDepthConversion.changeColorBit(img, targetImageBit), targetExt, saveFile);

        String message = "Image color bit is converted from " + imgBit + " to " + targetImageBit;
        log(message);
        return StepResult.success(message);
    }

    @Override
    public boolean mustForcefullyTerminate() { return mustForcefullyTerminate; }

    @Override
    public void forcefulTerminate() { }

    protected static File resolveSaveTo(String saveTo, String defaultFileName) {
        File saveFile = new File(saveTo);
        if (StringUtils.endsWithAny(saveTo, "/", "\\") || saveFile.isDirectory()) {
            saveFile.mkdirs();
            return new File(StringUtils.appendIfMissing(saveTo, separator) + defaultFileName);
        } else {
            saveFile.getParentFile().mkdirs();
            return saveFile;
        }
    }

    protected String formatToleranceMessage(float match, float tolerance) {
        boolean hasTol = context.hasData(OPT_IMAGE_TOLERANCE);

        StringBuilder str = new StringBuilder();
        str.append("(");
        if (hasTol) { str.append("Tolerance: ").append(IMAGE_PERCENT_FORMAT.format(tolerance)).append(", "); }
        str.append("Match: ").append(IMAGE_PERCENT_FORMAT.format(match)).append("%");
        // if (hasTol) { str.append("Total: ").append(IMAGE_PERCENT_FORMAT.format(match + tolerance)); }
        str.append(")");
        return str.toString();
    }
}
