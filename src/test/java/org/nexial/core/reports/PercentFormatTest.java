/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.reports;

import java.text.MessageFormat;

import org.junit.Assert;
import org.junit.Test;

public class PercentFormatTest {

	@Test
	public void testFormat() throws Exception {
		String rateFormat = "{0,number,0.00%}";

		Assert.assertEquals("100.00%", MessageFormat.format(rateFormat, 1.00));
		Assert.assertEquals("86.21%", MessageFormat.format(rateFormat, 0.8621));
		Assert.assertEquals("50.00%", MessageFormat.format(rateFormat, 0.5));
		Assert.assertEquals("0.00%", MessageFormat.format(rateFormat, 0));
		Assert.assertEquals("0.44%", MessageFormat.format(rateFormat, 0.00443));
		Assert.assertEquals("0.00%", MessageFormat.format(rateFormat, 0.0000443));
	}
}
