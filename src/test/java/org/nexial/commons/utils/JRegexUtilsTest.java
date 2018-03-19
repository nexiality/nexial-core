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

package org.nexial.commons.utils;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class JRegexUtilsTest {

	@Test
	public void testReplace() {
		String fixture = "Invalid input for Federal EIN '0000051801'. [2005]";
		String actual = JRegexUtils.replace(fixture, "(.+)\\[([\\d]{1,4})\\]", "$1|$2");
		Assert.assertEquals("Invalid input for Federal EIN '0000051801'. |2005", actual);

		String fixture1 = "Invalid input for Federal EIN '0000051801'.";
		String actual1 = JRegexUtils.replace(fixture1, "(.+)\\[([\\d]{1,4})\\]", "$1|$2");
		Assert.assertEquals("Invalid input for Federal EIN '0000051801'.", actual1);

		String fixture2 = "Invalid input for Federal EIN '0000051801'. [2005";
		String actual2 = JRegexUtils.replace(fixture2, "(.+)\\[([\\d]{1,4})\\]", "$1|$2");
		Assert.assertEquals("Invalid input for Federal EIN '0000051801'. [2005", actual2);
	}

	@Test
	public void testSplits() {
		String key = "@include(dataDriver.xlsx, #data, LANDSCAPE)";
		List<String> splits = JRegexUtils.collectGroups(key,
		                                                "\\@include\\(\\s*(.+)\\s*\\,\\s*(.+)\\s*\\,\\s*(LANDSCAPE|PORTRAIT)\\)");
		Assert.assertTrue(splits.size() == 3);
		Assert.assertEquals(splits.get(0), "dataDriver.xlsx");
		Assert.assertEquals(splits.get(1), "#data");
		Assert.assertEquals(splits.get(2), "LANDSCAPE");

	}
}
