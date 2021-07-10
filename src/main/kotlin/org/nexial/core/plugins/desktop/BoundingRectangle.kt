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
package org.nexial.core.plugins.desktop

import com.mchange.v2.util.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.utils.ConsoleUtils
import org.openqa.selenium.Rectangle
import org.openqa.selenium.WebElement

/** object representation of the coordinates and dimension of a [DesktopElement]. */
class BoundingRectangle private constructor() {
    var x = 0
        private set
    var y = 0
        private set
    var width = 0
        private set
    var height = 0
        private set
    var element: WebElement? = null
        private set
    var desktopElement: DesktopElement? = null
        private set

    fun adjust(x: Int, y: Int, width: Int, height: Int) {
        this.x += x
        this.y += y
        this.width += width
        this.height += height
    }

    override fun toString() = "($x,$y,$width,$height) of $desktopElement"

    companion object {
        @JvmStatic
        fun newInstance(desktopElement: DesktopElement?): BoundingRectangle? {
            if (desktopElement == null) return null
            val element = desktopElement.element
            val instance = newInstance(element) ?: return null
            instance.desktopElement = desktopElement
            return instance
        }

        @JvmStatic
        fun newInstance(element: WebElement?): BoundingRectangle? {
            if (element == null) return null

            val bounds = element.getAttribute(DesktopConst.ATTR_BOUNDING_RECTANGLE)
            if (StringUtils.isBlank(bounds) || StringUtils.countMatches(bounds, ",") != 3) return null

            val boundArray = StringUtils.split(bounds, ",")

            val instance = BoundingRectangle()
            instance.x = NumberUtils.toInt(boundArray[0])
            instance.y = NumberUtils.toInt(boundArray[1])
            instance.width = NumberUtils.toInt(boundArray[2])
            instance.height = NumberUtils.toInt(boundArray[3])
            instance.element = element
            return instance
        }

        fun asList(element: WebElement?): MutableList<String>? =
            if (element == null) null
            else TextUtils.toList(element.getAttribute(DesktopConst.ATTR_BOUNDING_RECTANGLE), ",", true)

        @JvmStatic
        fun asRectangle(element: WebElement?): Rectangle? {
            val dimension = asList(element)
            if (dimension == null || CollectionUtils.size(dimension) != 4) {
                ConsoleUtils.log(String.format("Unusable BoundingRectangle (%s) found for %s", dimension, element))
                return null
            }
            return Rectangle(NumberUtils.toInt(dimension[0]),
                             NumberUtils.toInt(dimension[1]),
                             NumberUtils.toInt(dimension[3]),
                             NumberUtils.toInt(dimension[2]))
        }
    }
}