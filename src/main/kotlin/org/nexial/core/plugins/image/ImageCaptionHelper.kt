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

import java.awt.Rectangle;
import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.NexialConst.CommonColor;
import org.nexial.core.utils.ConsoleUtils;

import static java.awt.AlphaComposite.*;
import static java.awt.Font.*;
import static java.awt.font.TextAttribute.*;
import static org.nexial.core.NexialConst.ImageCaption.*;
import static org.nexial.core.NexialConst.ImageCaption.CaptionPositions.BOTTOM_RIGHT;

public class ImageCaptionHelper {

    public static class CaptionModel {
        private List<String> captions;
        private String captionColorText = "yellow";
        private Color captionColor = CommonColor.toColor(captionColorText);
        private CaptionPositions position = BOTTOM_RIGHT;
        private float alpha = DEF_ALPHA;
        private String fontFace = DEF_FONT_FACE;
        private int fontSize = DEF_FONT_SIZE;
        private boolean wrap;

        public CaptionModel() {}

        public Color getCaptionColor() {
            if (captionColor != null) { return captionColor; }

            if (StringUtils.isEmpty(captionColorText)) {
                ConsoleUtils.log("No caption color specified; resorting to default");
                captionColorText = "yellow";
            }

            if (alpha < 0) {
                ConsoleUtils.log("No caption alpha setting found; resorting to default of 100%");
                alpha = 1;
            }

            captionColor = CommonColor.toColor(captionColorText);
            return captionColor;
        }

        public void setCaptionColor(String captionColor) {
            this.captionColorText = captionColor;
            this.captionColor = CommonColor.toColor(captionColorText);
        }

        public CaptionPositions getPosition() { return position; }

        public void setPosition(CaptionPositions position) {this.position = position; }

        public String getFontFace() {return fontFace; }

        public void setFontFace(String fontFace) {this.fontFace = fontFace; }

        public int getFontSize() {return fontSize; }

        public void setFontSize(int fontSize) {this.fontSize = fontSize; }

        public float getAlpha() {return alpha; }

        public void setAlpha(float alpha) {this.alpha = alpha; }

        public List<String> getCaptions() { return captions; }

        public void setCaptions(List<String> captions) { this.captions = captions; }

        public void addCaption(Object... captions) {
            if (ArrayUtils.isEmpty(captions)) { return; }
            if (this.captions == null) { this.captions = new ArrayList<>(); }
            Arrays.stream(captions).forEach(caption -> this.captions.add(StringUtils.trim(caption.toString())));
        }

        public boolean isWrap() { return wrap; }

        public void setWrap(boolean wrap) { this.wrap = wrap; }
    }

    public static void addCaptionToImage(File file, CaptionModel caption) {
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

        Graphics2D graphics = prepareGraphicObject(img, caption);
        try {
            renderText(graphics, resolveTextBounds(graphics, img, caption), caption);
            ImageIO.write(img, StringUtils.lowerCase(StringUtils.substringAfterLast(file.getName(), ".")), file);
        } catch (Exception e) {
            ConsoleUtils.error("Unable to add caption to the image", e.getMessage());
        } finally {
            // ConsoleUtils.log("Caption added to image " + file.getAbsolutePath());
            graphics.dispose();
        }
    }

    @NotNull
    protected static Rectangle resolveTextBounds(Graphics2D graphics, BufferedImage img, CaptionModel caption) {
        int imageWidth = img.getWidth();
        int imageHeight = img.getHeight();

        double maxTextBoundWidth = (double) imageWidth / 4;
        double maxTextBoundHeight = (double) imageHeight / 4;

        String fullText = TextUtils.toString(caption.getCaptions(), caption.wrap ? " " : "\n");
        Rectangle2D textBound = graphics.getFontMetrics().getStringBounds(fullText, graphics);
        double oneLineHeight = textBound.getHeight();
        double oneLineWidth = textBound.getWidth();

        int width = (int) Math.min(maxTextBoundWidth, oneLineWidth);
        int height = oneLineWidth > maxTextBoundWidth ?
                     (int) Math.min(maxTextBoundHeight, oneLineHeight * (oneLineWidth / maxTextBoundWidth + 1)) :
                     (int) oneLineHeight;

        CaptionPositions position = caption.getPosition();

        int x = 0;
        if (position.isLeft()) { x = LEFT_PADDING; }
        if (position.isCenter()) { x = (imageWidth - width) / 2; }
        if (position.isRight()) { x = imageWidth - width - RIGHT_PADDING; }

        int y = 0;
        if (position.isTop()) { y = TOP_PADDING; }
        if (position.isMiddle()) { y = (imageHeight - height) / 2; }
        if (position.isBottom()) { y = imageHeight - height - BOTTOM_PADDING; }

        return new Rectangle(x, y, width, height);
    }

    /**
     * render string on the specified {@link Graphics2D} instance. The {@code bounds} parameter specified the x, y,
     * width, height of the rendering space. The {@link CaptionModel} parameter represents the text and font setting
     * to render.
     */
    protected static Rectangle renderText(Graphics2D g, Rectangle bounds, CaptionModel caption) {
        if (g == null) { throw new NullPointerException("The image graphics handle is null!"); }
        if (bounds == null) { throw new NullPointerException("No text bounds found!"); }

        String text = TextUtils.toString(caption.getCaptions(), caption.wrap ? " " : "\n");
        if (StringUtils.isEmpty(text)) { return new Rectangle(bounds.x, bounds.y, 0, 0); }

        LineBreakMeasurer lineMeasurer = new LineBreakMeasurer(createAttributedText(caption),
                                                               new FontRenderContext(null, true, false));

        Point targetLocation = new Point(bounds.x, bounds.y);
        int nextOffset;

        CaptionPositions align = caption.getPosition();
        if (align.isMiddle() || align.isBottom()) {
            if (align.isMiddle()) { targetLocation.y = bounds.y + (bounds.height / 2); }
            if (align.isBottom()) { targetLocation.y = bounds.y + bounds.height; }

            while (lineMeasurer.getPosition() < text.length()) {
                nextOffset = nextTextIndex(text, lineMeasurer.getPosition(), lineMeasurer.nextOffset(bounds.width));

                TextLayout textLayout = lineMeasurer.nextLayout(bounds.width, nextOffset, false);
                float totalTextHeight = textLayout.getAscent() + textLayout.getLeading() + textLayout.getDescent();
                if (align.isMiddle()) { targetLocation.y -= totalTextHeight / 2; }
                if (align.isBottom()) { targetLocation.y -= totalTextHeight; }
            }

            targetLocation.y = Math.max(0, targetLocation.y);
            lineMeasurer.setPosition(0);
        }

        if (align.isRight() || align.isCenter()) { targetLocation.x = bounds.x + bounds.width; }

        Rectangle consumedBounds = new Rectangle(targetLocation.x, targetLocation.y, 0, 0);

        while (lineMeasurer.getPosition() < text.length()) {
            nextOffset = nextTextIndex(text, lineMeasurer.getPosition(), lineMeasurer.nextOffset(bounds.width));

            TextLayout textLayout = lineMeasurer.nextLayout(bounds.width, nextOffset, false);
            Rectangle2D textBounds = textLayout.getBounds();

            targetLocation.y += textLayout.getAscent();
            consumedBounds.width = Math.max(consumedBounds.width, (int) textBounds.getWidth());

            switch (align) {
                case TOP_LEFT:
                case MIDDLE_LEFT:
                case BOTTOM_LEFT:
                    textLayout.draw(g, targetLocation.x, targetLocation.y);
                    break;

                case TOP_CENTER:
                case MIDDLE_CENTER:
                case BOTTOM_CENTER:
                    targetLocation.x = bounds.x + (bounds.width / 2) - (int) (textBounds.getWidth() / 2);
                    consumedBounds.x = Math.min(consumedBounds.x, targetLocation.x);
                    textLayout.draw(g, targetLocation.x, targetLocation.y);
                    break;

                case TOP_RIGHT:
                case MIDDLE_RIGHT:
                case BOTTOM_RIGHT:
                    targetLocation.x = bounds.x + bounds.width - (int) textBounds.getWidth();
                    textLayout.draw(g, targetLocation.x, targetLocation.y);
                    consumedBounds.x = Math.min(consumedBounds.x, targetLocation.x);
                    break;
            }

            targetLocation.y += textLayout.getLeading() + textLayout.getDescent();
        }

        consumedBounds.height = targetLocation.y - consumedBounds.y;

        return consumedBounds;
    }

    private static Graphics2D prepareGraphicObject(BufferedImage img, CaptionModel caption) {
        Graphics2D graphics = (Graphics2D) img.getGraphics();
        graphics.setComposite(AlphaComposite.getInstance(SRC_OVER, caption.getAlpha()));
        graphics.setColor(caption.getCaptionColor());
        graphics.setFont(new Font(caption.getFontFace(), PLAIN, caption.getFontSize()));
        return graphics;
    }

    private static AttributedCharacterIterator createAttributedText(CaptionModel caption) {
        AttributedString attributedString =
            new AttributedString(TextUtils.toString(caption.getCaptions(), caption.wrap ? " " : "\n"));
        attributedString.addAttribute(FOREGROUND, caption.getCaptionColor());
        attributedString.addAttribute(FONT, new Font(caption.getFontFace(), PLAIN, caption.getFontSize()));
        return attributedString.getIterator();
    }

    /** Calculates the next maximum index of the string that will be displayed. */
    private static int nextTextIndex(String text, int from, int until) {
        for (int i = from + 1; i < until; ++i) {
            if (text.charAt(i) == '\n') { return i; }
        }
        return until;
    }

    // private static String wrapCaptionName(int imageWidth, String caption, Graphics2D graphics) {
    //     Rectangle2D rect = resolveBounds(graphics, caption);
    //
    //     // wrap the caption until caption rectangle width less than image width
    //     if (imageWidth > rect.getWidth()) { return caption; }
    //
    //     while (rect.getWidth() > imageWidth) {
    //         caption = StringUtils.right(caption, caption.length() - trimIndex);
    //         rect = resolveBounds(graphics, caption);
    //     }
    //
    //     return "…" + caption;
    // }
    //
    // private static Rectangle2D resolveBounds(Graphics2D graphics, String text) {
    //     return graphics.getFontMetrics().getStringBounds(text, graphics);
    // }
}
