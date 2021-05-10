package org.nexial.core.plugins.image

import com.github.romankh3.image.comparison.ImageComparison
import com.github.romankh3.image.comparison.model.ImageComparisonResult
import com.github.romankh3.image.comparison.model.ImageComparisonState.MATCH
import com.github.romankh3.image.comparison.model.ImageComparisonState.SIZE_MISMATCH
import com.github.romankh3.image.comparison.model.Rectangle
import org.nexial.core.plugins.image.ImageCommand.IMAGE_PERCENT_FORMAT
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.stream.Collectors
import javax.imageio.ImageIO

class ImageComparison(expected: BufferedImage, actual: BufferedImage) {
    private var ic: ImageComparison
    private val diffFillingOpacity = 5.0
    private val excludeFillingOpacity = 5.0
    private val rectangleLineWidth = 2
    private val threshold = 6
    private val pixelTolerance = 0.12
    private val minimalRectangleSize = 144
    private var result: ImageComparisonResult? = null

    constructor(image1: File, image2: File) : this(ImageIO.read(image1), ImageIO.read(image2))

    fun compareImages(color: Color?): Float {
        ic.differenceRectangleColor = color
        ic.excludedRectangleColor = color
        result = ic.compareImages()

        val matchedPercent = if (result!!.imageComparisonState == MATCH) 100f else 100 - result!!.differencePercent
        matchPercent = IMAGE_PERCENT_FORMAT.format(matchedPercent).toFloat()
        return matchPercent
    }

    var matchPercent = 0f
        private set

    val diffImage: BufferedImage
        get() = result!!.result

    // return tools.getDifferences();
    val differences: List<Difference>
        get() = if (result == null || result!!.imageComparisonState == MATCH)
            ArrayList()
        else if (result!!.imageComparisonState == SIZE_MISMATCH)
            ArrayList()
        else
            result!!.rectangles.stream()
                    .map { r: Rectangle -> Difference(r.minPoint.x, r.minPoint.y, r.width, r.height) }
                    .collect(Collectors.toList())

    init {
        ic = ImageComparison(expected, actual)
                .setDifferenceRectangleFilling(true, diffFillingOpacity)
                .setExcludedRectangleFilling(true, excludeFillingOpacity)
                .setRectangleLineWidth(rectangleLineWidth)
                .setMinimalRectangleSize(minimalRectangleSize)
                .setThreshold(threshold)
                .setPixelToleranceLevel(pixelTolerance)
    }
}