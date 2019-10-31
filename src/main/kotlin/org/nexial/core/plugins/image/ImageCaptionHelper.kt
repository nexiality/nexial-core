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

package org.nexial.core.plugins.image

import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.CommonColor
import org.nexial.core.NexialConst.ImageCaption.*
import org.nexial.core.NexialConst.ImageCaption.CaptionPositions.*
import org.nexial.core.utils.ConsoleUtils
import java.awt.*
import java.awt.AlphaComposite.SRC_OVER
import java.awt.Font.PLAIN
import java.awt.Rectangle
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

object ImageCaptionHelper {
    @JvmStatic
    fun addCaptionToImage(file: File, caption: CaptionModel) {

        val img: BufferedImage?
        try {
            img = ImageIO.read(file)
        } catch (e: IOException) {
            ConsoleUtils.error("Unable to read image " + file.toString() + ": " + e.message)
            return
        }

        if (img == null || img.width <= MIN_WIDTH || img.height <= MIN_HEIGHT) return

        val graphics = prepareGraphicObject(img, caption)
        try {
            renderText(graphics, resolveTextBounds(graphics, img, caption), caption)
            ImageIO.write(img, StringUtils.lowerCase(StringUtils.substringAfterLast(file.name, ".")), file)
        } catch (e: Exception) {
            ConsoleUtils.error("Unable to add caption to the image", e.message)
        } finally {
            // ConsoleUtils.log("Caption added to image " + file.getAbsolutePath());
            graphics.dispose()
        }
    }

    private fun resolveTextBounds(graphics: Graphics2D, img: BufferedImage, caption: CaptionModel): Rectangle {
        val imageWidth = img.width
        val imageHeight = img.height

        val maxTextBoundWidth = imageWidth.toDouble() / 4
        val maxTextBoundHeight = imageHeight.toDouble() / 4

        val textBound = graphics.fontMetrics.getStringBounds(caption.toText(), graphics)
        val oneLineHeight = textBound.height
        val oneLineWidth = textBound.width

        val width = min(maxTextBoundWidth, oneLineWidth).toInt()
        val height = (if (oneLineWidth > maxTextBoundWidth)
            min(maxTextBoundHeight, oneLineHeight * (oneLineWidth / maxTextBoundWidth))
        else
            oneLineHeight).toInt()

        val position = caption.position
        return Rectangle(when {
                             position.isLeft   -> LEFT_PADDING
                             position.isCenter -> (imageWidth - width) / 2
                             position.isRight  -> imageWidth - width - RIGHT_PADDING
                             else              -> 0
                         }, when {
                             position.isTop    -> TOP_PADDING
                             position.isMiddle -> (imageHeight - height) / 2
                             position.isBottom -> imageHeight - height - (LINE_PADDING * 2) - BOTTOM_PADDING
                             else              -> 0
                         }, width, height)
    }

    /**
     * render string on the specified [Graphics2D] instance. The `bounds` parameter specified the x, y,
     * width, height of the rendering space. The [CaptionModel] parameter represents the text and font setting
     * to render.
     */
    private fun renderText(g: Graphics2D, bounds: Rectangle, caption: CaptionModel): Rectangle {
        val text = caption.toText()
        if (StringUtils.isEmpty(text)) return Rectangle(bounds.x, bounds.y, 0, 0)

        val lineMeasurer = LineBreakMeasurer(createAttributedText(caption), FontRenderContext(null, true, true))

        val target = Point(bounds.x, bounds.y)
        var nextOffset: Int
        val align = caption.position

        if (align.isMiddle || align.isBottom) {
            if (align.isMiddle) target.y = bounds.y + bounds.height / 2
            if (align.isBottom) target.y = bounds.y + bounds.height - caption.fontSize

            while (lineMeasurer.position < text.length) {
                nextOffset = nextTextIndex(text, lineMeasurer.position, lineMeasurer.nextOffset(bounds.width.toFloat()))
                val textLayout = lineMeasurer.nextLayout(bounds.width.toFloat(), nextOffset, false)
                val totalTextHeight = textLayout.ascent + textLayout.leading + textLayout.descent
                if (align.isMiddle) target.y -= (totalTextHeight / 2).toInt()
                if (align.isBottom) target.y -= totalTextHeight.toInt()
            }
            target.y = max(TOP_PADDING, target.y)
            lineMeasurer.position = 0
        }

        if (align.isRight || align.isCenter) target.x = bounds.x + bounds.width

        val consumedBounds = Rectangle(target.x, target.y, 0, 0)
        while (lineMeasurer.position < text.length) {
            nextOffset = nextTextIndex(text, lineMeasurer.position, lineMeasurer.nextOffset(bounds.width.toFloat()))
            val textLayout = lineMeasurer.nextLayout(bounds.width.toFloat(), nextOffset, false)
            val textBounds = textLayout.bounds

            consumedBounds.width = max(consumedBounds.width, textBounds.width.toInt())

            target.y += textLayout.ascent.toInt() + LINE_PADDING

            when (align) {
                TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT       -> {
                    textLayout.draw(g, target.x.toFloat(), target.y.toFloat())
                }

                TOP_CENTER, MIDDLE_CENTER, BOTTOM_CENTER -> {
                    target.x = bounds.x + bounds.width / 2 - (textBounds.width / 2).toInt()
                    consumedBounds.x = min(consumedBounds.x, target.x)
                    textLayout.draw(g, target.x.toFloat(), target.y.toFloat())
                }

                TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT    -> {
                    target.x = bounds.x + bounds.width - textBounds.width.toInt()
                    textLayout.draw(g, target.x.toFloat(), target.y.toFloat())
                    consumedBounds.x = max(consumedBounds.x, target.x)
                }
            }

            target.y += (textLayout.leading + textLayout.descent).toInt() + LINE_PADDING
        }

        consumedBounds.height = target.y - consumedBounds.y
        return consumedBounds
    }

    private fun prepareGraphicObject(img: BufferedImage, caption: CaptionModel): Graphics2D {
        val graphics = img.graphics as Graphics2D
        graphics.composite = AlphaComposite.getInstance(SRC_OVER, caption.alpha)
        // graphics.color = caption.getCaptionColor()
        graphics.font = Font(caption.fontFace, PLAIN, caption.fontSize)
        return graphics
    }

    private fun createAttributedText(caption: CaptionModel): AttributedCharacterIterator? {
        val attributedString = AttributedString(caption.toText())
        attributedString.addAttribute(FOREGROUND, caption.getCaptionColor())
        attributedString.addAttribute(FONT, Font(caption.fontFace, PLAIN, caption.fontSize))
        if (caption.withBackground) {
            attributedString.addAttribute(BACKGROUND,
                                          CommonColor.toComplementaryBackgroundColor(caption.getCaptionColor()))
        }
        attributedString.addAttribute(KERNING, LINE_PADDING)
        return attributedString.iterator
    }

    /** Calculates the next maximum index of the string that will be displayed.  */
    private fun nextTextIndex(text: String, from: Int, until: Int): Int {
        for (i in from + 1 until until) if (text[i] == '\n') return i
        return until
    }

    class CaptionModel {
        private var captions: MutableList<String>? = null
        private var captionColorText = "yellow"
        private var captionColor: Color? = CommonColor.toColor(captionColorText)
        var position = BOTTOM_RIGHT
        var alpha = DEF_ALPHA
        var fontFace = DEF_FONT_FACE
        var fontSize = DEF_FONT_SIZE
        var isWrap = false
        var withBackground = true

        fun getCaptionColor(): Color? {
            if (captionColor != null) return captionColor

            if (StringUtils.isEmpty(captionColorText)) {
                ConsoleUtils.log("No caption color specified; resorting to default")
                captionColorText = "yellow"
            }
            if (alpha < 0) {
                ConsoleUtils.log("No caption alpha setting found; resorting to default of 100%")
                alpha = 1f
            }
            captionColor = CommonColor.toColor(captionColorText)
            return captionColor
        }

        fun setCaptionColor(captionColor: String) {
            captionColorText = captionColor
            this.captionColor = CommonColor.toColor(captionColorText)
        }

        fun getCaptions(): List<String>? = captions

        fun setCaptions(captions: MutableList<String>?) {
            this.captions = captions
        }

        fun addCaptions(captions: List<Any>) {
            if (captions.isEmpty()) return
            if (this.captions == null) this.captions = mutableListOf()
            captions.forEach { this.captions!!.add(it.toString().trim()) }
        }

        fun toText(): String = TextUtils.toString(getCaptions(), if (isWrap) " " else "\n")
    }
}