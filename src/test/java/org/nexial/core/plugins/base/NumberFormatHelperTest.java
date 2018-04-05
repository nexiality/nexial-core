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

package org.nexial.core.plugins.base;

import org.junit.Assert;
import org.junit.Test;

public class NumberFormatHelperTest {

	@Test
	public void testNormalization() {
		// negative + comma + decimal places
		Assert.assertEquals(NumberFormatHelper.newInstance("(5)").getNormalFormat(), "-5");
		Assert.assertEquals(NumberFormatHelper.newInstance("(5.0)").getNormalFormat(), "-5.0");
		Assert.assertEquals(NumberFormatHelper.newInstance("(5.000)").getNormalFormat(), "-5.000");
		Assert.assertEquals(NumberFormatHelper.newInstance("(5.0001)").getNormalFormat(), "-5.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("(125.0001)").getNormalFormat(), "-125.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("(1005.0001)").getNormalFormat(), "-1005.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("(1,005.0001)").getNormalFormat(), "-1005.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("(010,005.0001)").getNormalFormat(), "-10005.0001");

		// same as above
		Assert.assertEquals(NumberFormatHelper.newInstance("-5").getNormalFormat(), "-5");
		Assert.assertEquals(NumberFormatHelper.newInstance("-5.0").getNormalFormat(), "-5.0");
		Assert.assertEquals(NumberFormatHelper.newInstance("-5.000").getNormalFormat(), "-5.000");
		Assert.assertEquals(NumberFormatHelper.newInstance("-5.0001").getNormalFormat(), "-5.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("-125.0001").getNormalFormat(), "-125.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("-1005.0001").getNormalFormat(), "-1005.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("-1,005.0001").getNormalFormat(), "-1005.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("-010,005.0001").getNormalFormat(), "-10005.0001");

		// leading zero test
		Assert.assertEquals(NumberFormatHelper.newInstance("0.0001").getNormalFormat(), "0.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance(".0001").getNormalFormat(), "0.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance(".0").getNormalFormat(), "0.0");
		Assert.assertEquals(NumberFormatHelper.newInstance(".00000").getNormalFormat(), "0.00000");
		Assert.assertEquals(NumberFormatHelper.newInstance("(.075)").getNormalFormat(), "-0.075");
		Assert.assertEquals(NumberFormatHelper.newInstance("-.075").getNormalFormat(), "-0.075");

		// comma + large number
		Assert.assertEquals(NumberFormatHelper.newInstance("35,283,948,235,983").getNormalFormat(), "35283948235983");

		// right-appended negative sign
		Assert.assertEquals(NumberFormatHelper.newInstance("35,283,948,235-").getNormalFormat(), "-35283948235");
		Assert.assertEquals(NumberFormatHelper.newInstance("35,283,948.303-").getNormalFormat(), "-35283948.303");
		Assert.assertEquals(NumberFormatHelper.newInstance(".303-").getNormalFormat(), "-0.303");
	}

	@Test
	public void testValuation() {
		Assert.assertEquals(NumberFormatHelper.newInstance("0.0001").getValue().doubleValue(), 0.0001, 0);
		Assert.assertEquals(NumberFormatHelper.newInstance("4,123.5502901").getValue().doubleValue(), 4123.5502901, 0);
		Assert.assertEquals(NumberFormatHelper.newInstance("(8,888,880,000.0000000000001)").getValue().doubleValue(),
		                    -8888880000.0000000000001, 0);
		Assert.assertEquals(NumberFormatHelper.newInstance("123,456,789.0").getValue().doubleValue(), 123456789.0d, 0);

		// right-appended negative sign
		Assert.assertEquals(NumberFormatHelper.newInstance("3,948,235-").getValue().doubleValue(), -3948235d, 0);
		Assert.assertEquals(NumberFormatHelper.newInstance("948.303-").getValue().doubleValue(), -948.303, 0);
		Assert.assertEquals(NumberFormatHelper.newInstance(".303-").getValue().doubleValue(), -0.303, 0);
	}

	@Test
	public void testIncrement() {
		Assert.assertEquals(NumberFormatHelper.newInstance("123,456,789.0").addValue("4321.5").getNormalFormat(),
		                    "123461110.5");
		Assert.assertEquals(NumberFormatHelper.newInstance("(1.0)").addValue("000001.00000").getNormalFormat(), "0.0");
		Assert.assertEquals(NumberFormatHelper.newInstance("(1.0)").addValue("(14.0001)").getNormalFormat(),
		                    "-15.0001");
		Assert.assertEquals(NumberFormatHelper.newInstance("(1,004.0000004)").addValue("(14.0001)").getNormalFormat(),
		                    "-1018.0001004");

		// right-appended negative sign
		Assert.assertEquals(NumberFormatHelper.newInstance("3,948,235-").getNormalFormat(), "-3948235");
		Assert.assertEquals(NumberFormatHelper.newInstance("3,948,235-").addValue("-320.22").getNormalFormat(),
		                    "-3948555.22");
		Assert.assertEquals(NumberFormatHelper.newInstance("048.303-").addValue("001.001").getNormalFormat(),
		                    "-47.302");
	}
}