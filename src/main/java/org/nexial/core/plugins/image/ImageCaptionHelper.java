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

package org.nexial.core.plugins.image;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.nexial.core.NexialConst.CommonColor;
import org.nexial.core.NexialConst.Image.ImageType;
import org.nexial.core.utils.ConsoleUtils;

import static java.awt.AlphaComposite.*;
import static java.awt.Font.*;
import static org.nexial.core.NexialConst.ImageCaption.*;
import static org.nexial.core.NexialConst.ImageCaption.CaptionPositions.BOTTOM_RIGHT;
import static org.nexial.core.NexialConst.replaceWindowsChars;

public class ImageCaptionHelper {

    public static class CaptionModel {
        private List<String> captions;
        private Color captionColor;
        private CaptionPositions position = BOTTOM_RIGHT;
        private String fontFace = DEF_FONT_FACE;
        private int fontSize = DEF_FONT_SIZE;
        private float alpha = DEF_ALPHA;

        public CaptionModel() {}

        public Color getCaptionColor() { return captionColor; }

        public void setCaptionColor(Color captionColor) { this.captionColor = captionColor; }

        public void setCaptionColor(String captionColor) { this.captionColor = CommonColor.toColor(captionColor); }

        public CaptionPositions getPosition() { return position; }

        public void setPosition(CaptionPositions position) {this.position = position;}

        public String getFontFace() {return fontFace;}

        public void setFontFace(String fontFace) {this.fontFace = fontFace;}

        public int getFontSize() {return fontSize;}

        public void setFontSize(int fontSize) {this.fontSize = fontSize;}

        public float getAlpha() {return alpha;}

        public void setAlpha(float alpha) {this.alpha = alpha;}

        public List<String> getCaptions() { return captions; }

        public void setCaptions(List<String> captions) { this.captions = captions; }

        public void addCaption(Object... captions) {
            if (ArrayUtils.isEmpty(captions)) { return; }
            if (this.captions == null) { this.captions = new ArrayList<>(); }
            Arrays.stream(captions).forEach(caption -> this.captions.add(StringUtils.trim(caption.toString())));
        }
    }

    public static void addCaptionToImage(File file, CaptionModel model) {

    // }
    //
    // public static void addCaptionToImage(File file, CaptionPositions position, Object... caption) {

        BufferedImage img;
        try {
            img = ImageIO.read(file);
        } catch (IOException e) {
            ConsoleUtils.error("Unable to read image " + file + ": " + e.getMessage());
            return;
        }

        if (img == null) { return; }

        int imageWidth = img.getWidth();
        int imageHeight = img.getHeight();
        if (imageWidth <= MIN_WIDTH || imageHeight <= MIN_HEIGHT) { return; }

        try {
            Graphics2D graphics = prepareGraphicObject(img, model);
            int lineHeight = graphics.getFontMetrics().getHeight();

            List<String> captions = new ArrayList<>();
            // Arrays.stream(caption).forEach(text -> captions.add(wrapCaptionName(imageWidth, (String) text, graphics)));
            model.getCaptions().forEach(text -> captions.add(wrapCaptionName(imageWidth, text, graphics)));

            List<String> captionList = getCaptionTextList(imageWidth, graphics, captions);

            if (captionList != null) {
                for (int line = 0; line < captionList.size(); line++) {
                    String captionString = captionList.get(line);
                    Rectangle2D rectangle;
                    rectangle = resolveBounds(graphics, captionString);
                    int lineCal = line * lineHeight;
                    // Pair<Integer, Integer> dimensions = getDimensions(img, position, rectangle, lineCal);
                    Pair<Integer, Integer> dimensions = getDimensions(img, model.position, rectangle, lineCal);
                    graphics.drawString(captionString, dimensions.getLeft(), dimensions.getRight());
                }
            }
            ImageIO.write(img, ImageType.png.toString(), file);
            graphics.dispose();

        } catch (Exception e) {
            ConsoleUtils.error("Unable to add caption to the image", e.getMessage());
        }
    }

    private static Pair<Integer, Integer> getDimensions(BufferedImage bufferedImage, CaptionPositions position,
                                                        Rectangle2D rectangle, int lineCal) {
        int captionWidth = 0;
        int captionHeight = 0;
        switch (position) {
            case BOTTOM_CENTER:
                captionWidth = (bufferedImage.getWidth() - (int) rectangle.getWidth()) / 2;
                captionHeight = (bufferedImage.getHeight() * paddingBottomHeight) / 100 + lineCal;
                break;
            case BOTTOM_LEFT:
                captionWidth = paddingLeftWidth;
                captionHeight = (bufferedImage.getHeight() * paddingBottomHeight) / 100 + lineCal;
                break;
            case BOTTOM_RIGHT:
                captionWidth = bufferedImage.getWidth() - (int) rectangle.getWidth();
                captionHeight = (bufferedImage.getHeight() * paddingBottomHeight) / 100 + lineCal;
                break;
            case MIDDLE_LEFT:
                captionWidth = paddingLeftWidth;
                captionHeight = (bufferedImage.getHeight() / 2) + lineCal;
                break;
            case MIDDLE_CENTER:
                captionWidth = (bufferedImage.getWidth() - (int) rectangle.getWidth()) / 2;
                captionHeight = (bufferedImage.getHeight() / 2) + lineCal;
                break;
            case MIDDLE_RIGHT:
                captionWidth = bufferedImage.getWidth() - (int) rectangle.getWidth();
                captionHeight = (bufferedImage.getHeight() / 2) + lineCal;
                break;
            case TOP_LEFT:
                captionWidth = paddingLeftWidth;
                captionHeight = paddingTopHeight + lineCal;
                break;
            case TOP_CENTER:
                captionWidth = (bufferedImage.getWidth() - (int) rectangle.getWidth()) / 2;
                captionHeight = paddingTopHeight + lineCal;
                break;
            case TOP_RIGHT:
                captionWidth = bufferedImage.getWidth() - (int) rectangle.getWidth();
                captionHeight = paddingTopHeight + lineCal;
                break;
            default:
                break;
        }
        return Pair.of(captionWidth, captionHeight);
    }

    private static List<String> getCaptionTextList(int imageWidth, Graphics2D graphics, List<String> captions) {

        if (captions.isEmpty()) { return null; }
        List<String> captionList = new ArrayList<>();
        int count = 0;
        String temp = "";

        while (count < captions.size()) {
            String combination;
            if (StringUtils.isNotEmpty(temp)) {
                combination = temp + " " + captions.get(count);
            } else {
                combination = temp + captions.get(count);
            }
            count = count + 1;
            if (checkCaptionFits(combination, imageWidth, graphics)) {
                temp = combination;
            } else {
                combination = captions.get(count - 1);
                if (!StringUtils.isEmpty(temp)) {captionList.add(temp);}
                temp = combination;
            }
        }

        if (!StringUtils.isEmpty(temp)) {captionList.add(temp);}
        return captionList;
    }

    private static boolean checkCaptionFits(String combination, int imageWidth, Graphics2D graphics) {
        Rectangle2D bounds = resolveBounds(graphics, combination);
        return imageWidth > bounds.getWidth();
    }

    private static Graphics2D prepareGraphicObject(BufferedImage img, CaptionModel model) {
        Graphics2D graphics = (Graphics2D) img.getGraphics();
        graphics.setComposite(AlphaComposite.getInstance(SRC_OVER, model.getAlpha()));
        graphics.setColor(model.getCaptionColor());
        graphics.setFont(new Font(model.getFontFace(), PLAIN, model.getFontSize()));
        return graphics;
    }

    private static String wrapCaptionName(int imageWidth, String caption, Graphics2D graphics) {
        Rectangle2D rect = resolveBounds(graphics, caption);

        // wrap the caption until caption rectangle width less than image width
        if (imageWidth > rect.getWidth()) { return caption; }

        while (rect.getWidth() > imageWidth) {
            caption = StringUtils.right(caption, caption.length() - trimIndex);
            rect = resolveBounds(graphics, caption);
        }
        return replaceWindowsChars.get("\\u2026") + caption;
    }

    private static Rectangle2D resolveBounds(Graphics2D graphics, String text) {
        return graphics.getFontMetrics().getStringBounds(text, graphics);
    }
}
