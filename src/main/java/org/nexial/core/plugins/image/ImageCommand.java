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
import java.util.ArrayList;
import javax.imageio.ImageIO;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.NexialConst.*;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;

import static java.awt.RenderingHints.*;
import static java.awt.image.BufferedImage.*;
import static java.io.File.separator;
import static org.nexial.core.NexialConst.ImageDiffColor.getColorNames;
import static org.nexial.core.NexialConst.ImageType.png;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.utils.CheckUtils.*;

public class ImageCommand extends BaseCommand implements ForcefulTerminate {
    protected static final DecimalFormat IMAGE_PERCENT_FORMAT = new DecimalFormat("##.00");

    protected boolean mustForcefullyTerminate;

    @Override
    public String getTarget() { return "image"; }

    public StepResult convert(String source, String format, String saveTo) throws IOException {
        requiresReadableFileOrValidUrl(source);

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
        File srcFile = resolveFileResource(source);
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
        requiresReadableFileOrValidUrl(image);
        requiresNotBlank(dimension, "Invalid dimension", dimension);

        String delim = context.getTextDelim();
        String reDigits = "\\s*\\d+\\s*";
        String reDimension = "^" + reDigits + delim + reDigits + delim +
                             "(" + reDigits + "|\\*)" + delim + "(" + reDigits + "|\\*)" + "$";
        requires(RegexUtils.isExact(dimension, reDimension), "Invalid dimension", dimension);

        String[] split = StringUtils.splitByWholeSeparator(dimension, delim);
        int x = NumberUtils.toInt(StringUtils.trim(split[0]));
        int y = NumberUtils.toInt(StringUtils.trim(split[1]));
        String width = StringUtils.trim(split[2]);
        String height = StringUtils.trim(split[3]);

        mustForcefullyTerminate = true;

        //fill in the corners of the desired crop location here
        File imageFile = resolveFileResource(image);
        BufferedImage existing = ImageIO.read(imageFile);
        int w = StringUtils.equals(width, "*") ? existing.getWidth() - x : NumberUtils.toInt(width);
        int h = StringUtils.equals(height, "*") ? existing.getHeight() - y : NumberUtils.toInt(height);

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
        requiresReadableFileOrValidUrl(image);

        mustForcefullyTerminate = true;

        File imageFile = resolveFileResource(image);
        BufferedImage img = ImageIO.read(imageFile);
        width = StringUtils.equals(width, "*") ? "" + img.getWidth() : width;
        requires(NumberUtils.isDigits(width), "invalid width", width);
        int w = NumberUtils.toInt(width);

        height = StringUtils.equals(height, "*") ? "" + img.getHeight() : height;
        requires(NumberUtils.isDigits(height), "invalid height", height);
        int h = NumberUtils.toInt(height);

        BufferedImage resizedImage = new BufferedImage(w, h, TYPE_INT_RGB);

        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

        String ext = StringUtils.lowerCase(StringUtils.substringAfterLast(image, "."));
        File saveFile = resolveSaveTo(saveTo, StringUtils.substringAfterLast(imageFile.getAbsolutePath(), separator));
        ImageIO.write(resizedImage, ext, saveFile);

        String message = "image resized and save to '" + saveTo + "'";
        log(message);
        return StepResult.success(message);
    }

    public StepResult compare(String baseline, String actual) throws IOException {
        logDeprecated(getTarget() + " » compare(baseline,actual)",
                      getTarget() + " » saveDiff(baseline,actual,var)");
        return saveDiff(OPT_LAST_IMAGES_DIFF, baseline, actual);
    }

    public StepResult saveDiff(String var, String baseline, String actual) throws IOException {
        requiresReadableFileOrValidUrl(baseline);
        requiresReadableFileOrValidUrl(actual);

        float imageTol = Float.parseFloat(context.getStringData(OPT_IMAGE_TOLERANCE, getDefault(OPT_IMAGE_TOLERANCE)));

        // get baseline image
        File baselineFile = resolveFileResource(baseline);

        // get test image
        File testFile = resolveFileResource(actual);

        String colorName = context.getStringData(OPT_IMAGE_DIFF_COLOR, getDefault(OPT_IMAGE_DIFF_COLOR));
        Color color = ImageDiffColor.toColor(colorName);

        StopWatch watch = new StopWatch();
        watch.start();
        try {
            BufferedImage image1 = ImageIO.read(baselineFile);
            BufferedImage image2 = ImageIO.read(testFile);

            boolean trim = context.getBooleanData(OPT_TRIM_BEFORE_DIFF,
                                                  getDefaultBool(OPT_TRIM_BEFORE_DIFF));
            if (trim) {
                image1 = trimImage(image1);
                image2 = trimImage(image2);
            }

            ImageComparison imageComparison = new ImageComparison(image1, image2);
            float matchPercent = imageComparison.getMatchPercent();
            String stats = formatToleranceMessage(matchPercent, imageTol);
            ImageComparisonMeta imageComparisonMeta;
            if ((matchPercent + imageTol) < 100) {
                BufferedImage outImage = imageComparison.compareImages(color);
                // find all differences in images
                imageComparisonMeta = new ImageComparisonMeta(baselineFile.getAbsolutePath(),
                                                              testFile.getAbsolutePath(),
                                                              imageComparison.getDifferences(),
                                                              matchPercent,
                                                              imageTol, trim);
                context.setData(var, imageComparisonMeta);
                log("Image comparison meta is saved to variable '" + var + "'");

                String msg = "Difference between baseline and actual BEYOND tolerance " + stats;
                addOutputAsLink(msg, outImage, png.toString());
                return StepResult.fail(msg);
            } else {
                String msg = "Difference between baseline and actual within tolerance " + stats;
                log(msg);
                imageComparisonMeta = new ImageComparisonMeta(baselineFile.getAbsolutePath(),
                                                              testFile.getAbsolutePath(),
                                                              new ArrayList<>(),
                                                              matchPercent,
                                                              imageTol, trim);
                context.setData(var, imageComparisonMeta);
                log("Image comparison meta is saved to variable '" + var + "'");
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
        requiresReadableFileOrValidUrl(source);
        requiresPositiveNumber(bit, "invalid value for bit", bit);
        requiresNotBlank(saveTo, "invalid 'saveTo' location", saveTo);

        int targetImageBit = NumberUtils.toInt(bit);

        // get image
        File imageFile = resolveFileResource(source);
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

    private BufferedImage trimImage(BufferedImage image) {
        String colorName = context.getStringData(OPT_IMAGE_TRIM_COLOR, getDefault(OPT_IMAGE_TRIM_COLOR));
        Color color = MapUtils.getObject(getColorNames(), colorName,
                                         getColorNames().get(getDefault(OPT_IMAGE_TRIM_COLOR)));
        int num = 0;
        // Trimming left side white spaces
        int height = image.getHeight();
        int width = image.getWidth();
        for (int i = 0; i < width; i++) {
            if (!isMatched1(image, i, color)) { break; }
            num++;
        }
        int x = num < 3 ? 0 : num;

        num = 0;
        // Trimming top side white spaces
        for (int j = 0; j < height; j++) {
            if (!isMatched2(image, j, color)) { break; }
            num++;
        }
        int y = num < 3 ? 0 : num;
        if (x == width && y == height) {
            System.out.println("No trimming: There is only empty spaces of provided color " + colorName);
            return image;
        }

        num = 0;
        // Trimming right side white spaces
        for (int i = width - 1; i >= 0; i--) {
            if (!isMatched1(image, i, color)) { break; }
            num++;
        }
        width = width - (num < 3 ? 0 : num) - x;

        num = 0;
        // Trimming bottom side white spaces
        for (int j = height - 1; j >= 0; j--) {
            if (!isMatched2(image, j, color)) { break; }
            num++;
        }
        height = height - (num < 3 ? 0 : num) - y;
        // return cropped image using dimensions
        return image.getSubimage(x, y, width, height);
    }

    private boolean isMatched1(BufferedImage image, int i, Color color) {
        boolean isMatched = true;
        for (int j = 0; j < image.getHeight(); j++) {
            if (image.getRGB(i, j) != color.getRGB()) {
                isMatched = false;
                break;
            }
        }
        return isMatched;
    }

    private boolean isMatched2(BufferedImage image, int j, Color color) {
        boolean isMatched = true;
        for (int i = 0; i < image.getWidth(); i++) {
            if (image.getRGB(i, j) != color.getRGB()) {
                isMatched = false;
                break;
            }
        }
        return isMatched;
    }

    @Override
    public boolean mustForcefullyTerminate() { return mustForcefullyTerminate; }

    @Override
    public void forcefulTerminate() { }

    protected String formatToleranceMessage(float match, float tolerance) {
        StringBuilder str = new StringBuilder("(");
        if (context.hasData(OPT_IMAGE_TOLERANCE)) {
            str.append("Tolerance: ").append(IMAGE_PERCENT_FORMAT.format(tolerance)).append(", ");
        }
        str.append("Match: ").append(IMAGE_PERCENT_FORMAT.format(match)).append("%)");
        return str.toString();
    }
}
