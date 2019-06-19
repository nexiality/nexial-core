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

/**
 * image comparison metadata
 */
data class ImageComparisonMeta(val expected: String, val actual: String, val matchPercent: Float,
                               val tolerance: Float, val count: Int, val differences: List<Difference>,
                               val smallest: Difference?, val largest: Difference?) {

    constructor(expected: String, actual: String, differences: List<Difference>,
                matchPercent: Float, tolerance: Float) :
        this(expected, actual, matchPercent, tolerance, differences.size,
             differences, sortByArea(differences).first, sortByArea(differences).second)

    override fun toString() = "{\n" +
                              "    expected=$expected,\n" +
                              "    actual=$actual,\n" +
                              "    matchPercent=$matchPercent,\n" +
                              "    tolerance=$tolerance,\n" +
                              "    count=$count,\n" +
                              "    smallest=$smallest,\n" +
                              "    largest=$largest,\n" +
                              "    differences=$differences\n" +
                              "}"

    companion object {
        // find smallest and largest difference in pair after sorting by area
        fun sortByArea(differences: List<Difference>): Pair<Difference?, Difference?> {
            val sorted = differences.sortedBy { it.diffArea() }
            // return Pair(smallest, largest)
            return if (sorted.isNotEmpty()) Pair(sorted[0], sorted[sorted.size - 1]) else Pair(null, null)
        }
    }
}

data class Difference(val x: Int, val y: Int, val width: Int, val height: Int) {
    override fun toString() = "\n{\n" +
                              "    x=$x,\n" +
                              "    y=$y,\n" +
                              "    width=$width,\n" +
                              "    height=$height,\n" +
                              "}"

    fun diffArea() = width * height

    fun getDimension() = "$x,$y,$width,$height"
}