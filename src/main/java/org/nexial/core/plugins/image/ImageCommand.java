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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.nexial.commons.ServiceException;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.NexialConst.ImageDiffColor;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.ForcefulTerminate;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.services.external.ImageOcrApi;
import org.nexial.core.utils.ConsoleUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import static org.nexial.core.NexialConst.Image.*;
import static org.nexial.core.NexialConst.Image.ImageType.png;
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

        mustForcefullyTerminate = true;

        // create a blank, RGB, same width and height, and a white background
        File srcFile = resolveFileResource(source);
        try {
            BufferedImage img = ImageIO.read(srcFile);
            if (img == null) { return StepResult.fail("File '" + source + "' CANNOT be read as an image"); }

            String srcFileName = srcFile.getName();
            BufferedImage dest = ImageUtils.convert(img, StringUtils.substringAfterLast(srcFileName, "."), format);
            File saveFile = writeProcessedImage(dest, saveTo, srcFileName);
            String message = "image '" + source + "' converted to '" + saveFile.getAbsolutePath() + "'";
            log(message);
            return StepResult.success(message);
        } catch (IllegalArgumentException e) {
            return StepResult.fail("Invalid/unsupported image format: " + format + ". " +
                                   "Supported formats are png, jpg, gif, bmp");
        }
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
        int w = StringUtils.equals(width, "*") ? 0 : NumberUtils.toInt(width);
        String height = StringUtils.trim(split[3]);
        int h = StringUtils.equals(height, "*") ? 0 : NumberUtils.toInt(height);

        mustForcefullyTerminate = true;

        File imageFile = resolveFileResource(image);
        BufferedImage img = ImageIO.read(imageFile);
        if (img == null) { return StepResult.fail("File '" + image + "' CANNOT be read as an image"); }

        File saveFile = writeProcessedImage(ImageUtils.crop(img, x, y, w, h), saveTo, imageFile.getName());
        String message = "image cropped and saved to '" + saveFile.getAbsolutePath() + "'";
        log(message);
        return StepResult.success(message);
    }

    public StepResult resize(String image, String width, String height, String saveTo) throws IOException {
        requiresReadableFileOrValidUrl(image);

        width = StringUtils.equals(width, "*") ? "0" : width;
        requires(NumberUtils.isDigits(width), "invalid width", width);

        height = StringUtils.equals(height, "*") ? "0" : height;
        requires(NumberUtils.isDigits(height), "invalid height", height);

        mustForcefullyTerminate = true;

        File imageFile = resolveFileResource(image);
        BufferedImage img = ImageIO.read(imageFile);
        if (img == null) { return StepResult.fail("File '" + image + "' CANNOT be read as an image"); }

        BufferedImage resized = ImageUtils.resize(img, NumberUtils.toInt(width), NumberUtils.toInt(height));
        File saveFile = writeProcessedImage(resized, saveTo, imageFile.getName());
        String message = "image resized and saved to '" + saveFile.getAbsolutePath() + "'";
        log(message);
        return StepResult.success(message);
    }

    public StepResult compare(String baseline, String actual) throws IOException {
        logDeprecated(getTarget() + " » compare(baseline,actual)", getTarget() + " » saveDiff(baseline,actual,var)");
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
            BufferedImage imgBaseline = ImageIO.read(baselineFile);
            if (imgBaseline == null) { return StepResult.fail("File '" + baseline + "' CANNOT be read as an image"); }

            BufferedImage imgActual = ImageIO.read(testFile);
            if (imgActual == null) { return StepResult.fail("File '" + actual + "' CANNOT be read as an image"); }

            Color trimColor = resolveTrimColor();
            if (trimColor != null) {
                imgBaseline = ImageUtils.trimOffOuterColor(imgBaseline, trimColor);
                imgActual = ImageUtils.trimOffOuterColor(imgActual, trimColor);
            }

            ImageComparison imageComparison = new ImageComparison(imgBaseline, imgActual);
            float matchPercent = imageComparison.compareImages(color);
            String stats = formatToleranceMessage(matchPercent, imageTol);

            // show all differences (if any) in images
            context.setData(var, new ImageComparisonMeta(baselineFile.getAbsolutePath(),
                                                         testFile.getAbsolutePath(),
                                                         imageComparison.getDifferences(),
                                                         matchPercent,
                                                         imageTol,
                                                         trimColor != null));
            log("Image comparison meta is saved to variable '" + var + "'");

            if ((matchPercent + imageTol) < 100) {
                String msg = "Difference between baseline and actual BEYOND tolerance " + stats;
                addOutputAsLink(msg, imageComparison.getDiffImage(), png.toString());
                return StepResult.fail(msg);
            } else {
                String msg = "Difference between baseline and actual within tolerance " + stats;
                log(msg);
                return StepResult.success(msg);
            }
        } catch (IOException e) {
            return StepResult.fail("ERROR reading image file: " + e.getMessage());
        } finally {
            watch.stop();
            ConsoleUtils.log("Image comparison completed in " + watch.getTime() + "ms");
        }
    }

    public StepResult colorbit(String image, String bit, String saveTo) throws IOException {
        requiresReadableFileOrValidUrl(image);
        requiresPositiveNumber(bit, "invalid value for bit", bit);
        requiresNotBlank(saveTo, "invalid 'saveTo' location", saveTo);

        int targetImageBit = NumberUtils.toInt(bit);

        // get image
        File imageFile = resolveFileResource(image);
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) { return StepResult.fail("File '" + image + "' CANNOT be read as an image"); }

            File saveFile = writeProcessedImage(ImageUtils.convertColorBit(img, targetImageBit),
                                                saveTo,
                                                imageFile.getName());
            String message = "File '" + image + "' color bit converted to " + targetImageBit + " and saved to " +
                             saveFile.getAbsolutePath();
            log(message);
            return StepResult.success(message);
        } catch (IOException e) {
            return StepResult.fail("Unable to read image '" + imageFile.getAbsolutePath() + "': " + e.getMessage());
        }
    }

    public StepResult ocr(String image, String saveVar) throws IOException, ServiceException {
        requiresReadableFileOrValidUrl(image);
        context.removeData(saveVar);
        log("performing OCR on '" + image + "'");
        context.setData(saveVar, new ImageOcrApi().ocr(resolveFileResource(image)));
        return StepResult.success("OCR completed on '" + image + "' and saved to data variable '" + saveVar + "'");
    }

    @Override
    public boolean mustForcefullyTerminate() { return mustForcefullyTerminate; }

    @Override
    public void forcefulTerminate() { }

    protected File writeProcessedImage(BufferedImage img, String saveTo, String filename) throws IOException {
        File saveFile = resolveSaveTo(saveTo, filename);
        ImageType imageType = ImageUtils.toSupportedFormat(StringUtils.substringAfterLast(filename, "."));
        ImageIO.write(img, imageType.name(), saveFile);
        return saveFile;
    }

    protected String formatToleranceMessage(float match, float tolerance) {
        StringBuilder str = new StringBuilder("(");
        if (context.hasData(OPT_IMAGE_TOLERANCE)) {
            str.append("Tolerance: ").append(IMAGE_PERCENT_FORMAT.format(tolerance)).append(", ");
        }
        str.append("Match: ").append(IMAGE_PERCENT_FORMAT.format(match)).append("%)");
        return str.toString();
    }

    // Supported RGB color space as integers only.
    private Color resolveTrimColor() {
        boolean trim = context.getBooleanData(OPT_TRIM_BEFORE_DIFF, getDefaultBool(OPT_TRIM_BEFORE_DIFF));
        if (!trim) { return null; }

        String rgbComponent = context.getStringData(OPT_IMAGE_TRIM_COLOR, getDefault(OPT_IMAGE_TRIM_COLOR));
        List<Integer> rgb = TextUtils.toList(rgbComponent, ",", true)
                                     .stream().map(Integer::parseInt).collect(Collectors.toList());
        if (rgb.size() != 3) {
            throw new ArrayIndexOutOfBoundsException("RGB color for trimming is not specified correctly");
        }
        return new Color(rgb.get(0), rgb.get(1), rgb.get(2));
    }
}
