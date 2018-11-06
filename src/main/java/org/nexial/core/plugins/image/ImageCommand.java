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
        requiresReadableFile(baseline);
        requiresReadableFile(actual);

        float imageTol = context.hasData(OPT_IMAGE_TOLERANCE) ?
                         Float.parseFloat(context.getStringData(OPT_IMAGE_TOLERANCE)) : 0;

        // get baseline image
        File baselineFile = new File(baseline);

        // get test image
        File testFile = new File(actual);

        String colorName = context.getStringData(OPT_IMAGE_DIFF_COLOR, DEF_IMAGE_DIFF_COLOR);
        Color color = ImageDiffColor.toColor(colorName);

        StopWatch watch = new StopWatch();
        watch.start();
        try {
            ImageComparison imageComparison = new ImageComparison(baselineFile, testFile);
            float matchPercent = imageComparison.getMatchPercent();
            String stats = formatToleranceMessage(matchPercent, imageTol);

            if ((matchPercent + imageTol) < 100) {
                BufferedImage outImage = imageComparison.compareImages(color);
                String msg = "Difference between baseline and actual BEYOND tolerance " + stats;
                addOutputAsLink(msg, outImage, png.toString());
                return StepResult.fail(msg);
            } else {
                String msg = "Difference between baseline and actual within tolerance " + stats;
                log(msg);
                return StepResult.success("baseline and test images are same " + stats);
            }
        } catch (IOException e) {
            return StepResult.fail("ERROR reading image file: " + e.getMessage());
        } finally {
            watch.stop();
            ConsoleUtils.log("Image comparison completed in " + watch.getTime() + "ms");
        }
    }

    public StepResult colorbit(String source, String bit, String saveTo) throws IOException {
        requiresReadableFile(source);
        requiresPositiveNumber(bit, "invalid value for bit", bit);
        requiresNotBlank(saveTo, "invalid 'saveTo' location", saveTo);

        int targetImageBit = NumberUtils.toInt(bit);

        // get image
        File imageFile = new File(source);
        BufferedImage img;
        try {
            img = ImageIO.read(imageFile);
            if (img == null) { return StepResult.fail("File '" + source + "' CANNOT be read as an image"); }
        } catch (IOException e) {
            return StepResult.fail("Unable to read image '" + imageFile.getAbsolutePath() + "': " + e.getMessage());
        }

        int imgBit = img.getColorModel().getPixelSize();
        if (imgBit <= targetImageBit) {
            String message = "color bit of source image(" + imgBit + ") is equal/less than target bit("
                             + targetImageBit + "); no conversion needed";
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
        StringBuilder str = new StringBuilder("(");
        if (context.hasData(OPT_IMAGE_TOLERANCE)) {
            str.append("Tolerance: ").append(IMAGE_PERCENT_FORMAT.format(tolerance)).append(", ");
        }
        str.append("Match: ").append(IMAGE_PERCENT_FORMAT.format(match)).append("%)");
        return str.toString();
    }
}
