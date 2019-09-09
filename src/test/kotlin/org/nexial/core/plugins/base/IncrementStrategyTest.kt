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

package org.nexial.core.plugins.base

import org.apache.commons.lang3.StringUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.plugins.base.IncrementStrategy.*

class IncrementStrategyTest {
    private val resourcePath = StringUtils.replace(this.javaClass.`package`.name, ".", "/") + "/"

    @Test
    fun testIncrement() {
        val strategy = UPPER

        // single char increment
        assertEquals(strategy.increment("A", 1, 0), "A")
        assertEquals(strategy.increment("A", 1, 1), "B")
        assertEquals(strategy.increment("A", 1, 2), "C")
        assertEquals(strategy.increment("A", 1, 26), "AA")

        // double char increment
        assertEquals(strategy.increment("A", 1, 27), "AB")
        assertEquals(strategy.increment("A", 1, 30), "AE")
        assertEquals(strategy.increment("A", 1, 26 * 2), "BA")
        assertEquals(strategy.increment("A", 1, 26 * 3), "CA")
        assertEquals(strategy.increment("A", 1, 26 * 10), "JA")
        assertEquals(strategy.increment("A", 1, 26 * 26), "ZA")

        // triple char increment
        assertEquals(strategy.increment("A", 1, 26 * 27), "AAA")
        assertEquals(strategy.increment("A", 1, 26 * 27 + 2), "AAC")
        assertEquals(strategy.increment("A", 1, 26 * 27 + 27), "ABB")
        assertEquals(strategy.increment("A", 1, 26 * 27 + 29), "ABD")
        assertEquals(strategy.increment("A", 1, 26 * 27 + 27 + 26), "ACB")
        assertEquals(strategy.increment("A", 1, 26 * 27 + 27 + 27), "ACC")
        assertEquals(strategy.increment("A", 1, 26 * 27 + 26 * 3), "ADA")
        assertEquals(strategy.increment("A", 1, 26 * 27 + 26 * 4), "AEA")
        assertEquals(strategy.increment("A", 1, 26 * 26 + 26 * 25), "AYA")
        assertEquals(strategy.increment("A", 1, 26 * 26 + 26 * 26 - 1), "AYZ")
        assertEquals(strategy.increment("A", 1, 26 * 26 + 26 * 26), "AZA")
        assertEquals(strategy.increment("A", 1, 26 * 26 + 26 * 26 + 26), "BAA")
    }

    @Test
    fun testIncrementSpotCheck() {
        var strategy = UPPER

        assertEquals(strategy.increment("A", 1, 0), "A")
        assertEquals(strategy.increment("A", 1, 1), "B")
        assertEquals(strategy.increment("A", 1, 25), "Z")
        assertEquals(strategy.increment("A", 1, 26), "AA")
        assertEquals(strategy.increment("A", 1, 27), "AB")
        assertEquals(strategy.increment("A", 1, 51), "AZ")
        assertEquals(strategy.increment("A", 1, 52), "BA")
        assertEquals(strategy.increment("A", 1, 53), "BB")
        assertEquals(strategy.increment("A", 1, 77), "BZ")
        assertEquals(strategy.increment("A", 1, 78), "CA")
        assertEquals(strategy.increment("A", 1, 427), "PL")
        assertEquals(strategy.increment("A", 1, 700), "ZY")
        assertEquals(strategy.increment("A", 1, 701), "ZZ")
        assertEquals(strategy.increment("A", 1, 702), "AAA")
        assertEquals(strategy.increment("A", 1, 737), "ABJ")
        assertEquals(strategy.increment("A", 1, 913), "AID")
        assertEquals(strategy.increment("A", 1, 1000), "ALM")
        assertEquals(strategy.increment("A", 1, 1376), "AZY")
        assertEquals(strategy.increment("A", 1, 1377), "AZZ")
        assertEquals(strategy.increment("A", 1, 1378), "BAA")
        assertEquals(strategy.increment("A", 1, 1499), "BER")

        strategy = LOWER
        assertEquals(strategy.increment("A", 1, 0), "a")
        assertEquals(strategy.increment("A", 1, 1), "b")
        assertEquals(strategy.increment("A", 1, 25), "z")
        assertEquals(strategy.increment("A", 1, 26), "aa")
        assertEquals(strategy.increment("A", 1, 27), "ab")
        assertEquals(strategy.increment("A", 1, 51), "az")
        assertEquals(strategy.increment("A", 1, 52), "ba")
        assertEquals(strategy.increment("A", 1, 1486), "bee")

        strategy = ALPHANUM
        assertEquals(strategy.increment("A", 1, 0), "A")
        assertEquals(strategy.increment("A", 1, 1), "B")
        assertEquals(strategy.increment("A", 1, 2), "C")
        assertEquals(strategy.increment("A", 1, 10), "K")
        assertEquals(strategy.increment("A", 1, 34), "i")
        assertEquals(strategy.increment("A", 1, 35), "j")
        assertEquals(strategy.increment("A", 1, 36), "k")
        assertEquals(strategy.increment("A", 1, 61), "09")
        assertEquals(strategy.increment("A", 1, 62), "0A")
        assertEquals(strategy.increment("A", 1, 63), "0B")
        assertEquals(strategy.increment("A", 1, 72), "0K")
        assertEquals(strategy.increment("A", 1, 97), "0j")
        assertEquals(strategy.increment("A", 1, 98), "0k")
        assertEquals(strategy.increment("A", 1, 123), "19")
        assertEquals(strategy.increment("A", 1, 124), "1A")
        assertEquals(strategy.increment("A", 1, 125), "1B")
        assertEquals(strategy.increment("A", 1, 257), "3J")
        assertEquals(strategy.increment("A", 1, 258), "3K")
        assertEquals(strategy.increment("A", 1, 283), "3j")
        assertEquals(strategy.increment("A", 1, 284), "3k")
        assertEquals(strategy.increment("A", 1, 309), "49")
        assertEquals(strategy.increment("A", 1, 310), "4A")
        assertEquals(strategy.increment("A", 1, 1426), "MA")
        assertEquals(strategy.increment("A", 1, 1496), "NI")
    }

    @Test
    fun testIncrement2() {
        assertEquals(retrieveExpectedValue("IncrementStrategyTest-Increment2-expected1.txt"),
                     (0..1499).fold("", { value, i ->
                         value + StringUtils.leftPad("" + i, 5) + " " + UPPER.increment("A", 1, i) + "\n"
                     }))

        assertEquals(retrieveExpectedValue("IncrementStrategyTest-Increment2-expected2.txt"),
                     (0..1499).fold("", { value, i ->
                         value + StringUtils.leftPad("" + i, 5) + " " + LOWER.increment("A", 1, i) + "\n"
                     }))

        assertEquals(retrieveExpectedValue("IncrementStrategyTest-Increment2-expected3.txt"),
                     (0..1499).fold("", { value, i ->
                         value + StringUtils.leftPad("" + i, 5) + " " + ALPHANUM.increment("A", 1, i) + "\n"
                     }))

        assertEquals("18279 aaab", "18279 " + LOWER.increment("P102", 1, 18279))

        assertEquals("35852 pzzy\n" +
                     "35853 pzzz\n" +
                     "35854 qaaa\n",
                     (35852..35854).fold("",
                                         { value, i -> value + "" + i + " " + LOWER.increment("p102", 1, i) + "\n" }))
    }

    private fun retrieveExpectedValue(resource: String) = ResourceUtils.loadResource(resourcePath + resource)
}
