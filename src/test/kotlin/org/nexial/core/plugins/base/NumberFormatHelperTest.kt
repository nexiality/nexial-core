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

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormatHelperTest {

    @Test
    fun testNormalization() {
        // negative + comma + decimal places
        assertEquals(NumberFormatHelper.newInstance("(5)").normalFormat, "-5")
        assertEquals(NumberFormatHelper.newInstance("(5.0)").normalFormat, "-5.0")
        assertEquals(NumberFormatHelper.newInstance("(5.000)").normalFormat, "-5.000")
        assertEquals(NumberFormatHelper.newInstance("(5.0001)").normalFormat, "-5.0001")
        assertEquals(NumberFormatHelper.newInstance("(125.0001)").normalFormat, "-125.0001")
        assertEquals(NumberFormatHelper.newInstance("(1005.0001)").normalFormat, "-1005.0001")
        assertEquals(NumberFormatHelper.newInstance("(1,005.0001)").normalFormat, "-1005.0001")
        assertEquals(NumberFormatHelper.newInstance("(010,005.0001)").normalFormat, "-10005.0001")

        // same as above
        assertEquals(NumberFormatHelper.newInstance("-5").normalFormat, "-5")
        assertEquals(NumberFormatHelper.newInstance("-5.0").normalFormat, "-5.0")
        assertEquals(NumberFormatHelper.newInstance("-5.000").normalFormat, "-5.000")
        assertEquals(NumberFormatHelper.newInstance("-5.0001").normalFormat, "-5.0001")
        assertEquals(NumberFormatHelper.newInstance("-125.0001").normalFormat, "-125.0001")
        assertEquals(NumberFormatHelper.newInstance("-1005.0001").normalFormat, "-1005.0001")
        assertEquals(NumberFormatHelper.newInstance("-1,005.0001").normalFormat, "-1005.0001")
        assertEquals(NumberFormatHelper.newInstance("-010,005.0001").normalFormat, "-10005.0001")

        // leading zero test
        assertEquals(NumberFormatHelper.newInstance("0.0001").normalFormat, "0.0001")
        assertEquals(NumberFormatHelper.newInstance(".0001").normalFormat, "0.0001")
        assertEquals(NumberFormatHelper.newInstance(".0").normalFormat, "0.0")
        assertEquals(NumberFormatHelper.newInstance(".00000").normalFormat, "0.00000")
        assertEquals(NumberFormatHelper.newInstance("(.075)").normalFormat, "-0.075")
        assertEquals(NumberFormatHelper.newInstance("-.075").normalFormat, "-0.075")

        // comma + large number
        assertEquals(NumberFormatHelper.newInstance("35,283,948,235,983").normalFormat, "35283948235983")

        // right-appended negative sign
        assertEquals(NumberFormatHelper.newInstance("35,283,948,235-").normalFormat, "-35283948235")
        assertEquals(NumberFormatHelper.newInstance("35,283,948.303-").normalFormat, "-35283948.303")
        assertEquals(NumberFormatHelper.newInstance(".303-").normalFormat, "-0.303")
    }

    @Test
    fun testValuation() {
        assertEquals(NumberFormatHelper.newInstance("0.0001").value.toDouble(), 0.0001, 0.0)
        assertEquals(NumberFormatHelper.newInstance("4,123.5502901").value.toDouble(), 4123.5502901, 0.0)
        assertEquals(NumberFormatHelper.newInstance("(8,888,880,000.0000000000001)").value.toDouble(),
                     -8888880000.0000000000001,
                     0.0)
        assertEquals(NumberFormatHelper.newInstance("123,456,789.0").value.toDouble(), 123456789.0, 0.0)

        // right-appended negative sign
        assertEquals(NumberFormatHelper.newInstance("3,948,235-").value.toDouble(), -3948235.0, 0.0)
        assertEquals(NumberFormatHelper.newInstance("948.303-").value.toDouble(), -948.303, 0.0)
        assertEquals(NumberFormatHelper.newInstance(".303-").value.toDouble(), -0.303, 0.0)
    }

    @Test
    fun testIncrement() {
        assertEquals(NumberFormatHelper.newInstance("123,456,789.0").addValue("4321.5").normalFormat, "123461110.5")
        assertEquals(NumberFormatHelper.newInstance("(1.0)").addValue("000001.00000").normalFormat, "0.0")
        assertEquals(NumberFormatHelper.newInstance("(1.0)").addValue("(14.0001)").normalFormat, "-15.0001")
        assertEquals(NumberFormatHelper.newInstance("(1,004.0000004)").addValue("(14.0001)").normalFormat,
                     "-1018.0001004")

        // right-appended negative sign
        assertEquals(NumberFormatHelper.newInstance("3,948,235-").normalFormat, "-3948235")
        assertEquals(NumberFormatHelper.newInstance("3,948,235-").addValue("-320.22").normalFormat, "-3948555.22")
        assertEquals(NumberFormatHelper.newInstance("048.303-").addValue("001.001").normalFormat, "-47.302")
    }
}