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

package org.nexial.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.nexial.core.NexialConst.Data.START_URL;
import static org.nexial.core.NexialConst.OPT_DELAY_BROWSER;

public class ExecutionTokenReplacementTest {
	private ExecutionContext context;

	@Before
	public void init() {
		List<Map<String, String>> resultset = new ArrayList<>();

		Map<String, String> rowOne = new HashMap<>();
		rowOne.put("col1", "Johnny");
		rowOne.put("col2", "B.");
		rowOne.put("col3", "Good");
		resultset.add(rowOne);

		Map<String, String> rowTwo = new HashMap<>();
		rowTwo.put("col1", "Samuel");
		rowTwo.put("col2", "L.");
		rowTwo.put("col3", "Jackson");
		resultset.add(rowTwo);

		String varName = "myData";

		context = new MockExecutionContext();
		context.setData(START_URL, "http://www.google.com");
		context.setData(varName, resultset);
	}

	@After
	public void tearDown() {
		if (context != null) { ((MockExecutionContext) context).cleanProject(); }
	}

	@Test
	public void testReplaceTokens4() {

		Assert.assertEquals("R1C2 = Good", context.replaceTokens("R1C2 = ${myData}[0].col3"));
		String actual = context.replaceTokens("${myData}");
		Assert.assertTrue(StringUtils.contains(actual, "col1=Johnny"));
		Assert.assertTrue(StringUtils.contains(actual, "col2=B."));
		Assert.assertTrue(StringUtils.contains(actual, "col3=Good"));
		Assert.assertTrue(StringUtils.contains(actual, "col1=Samuel"));
		Assert.assertTrue(StringUtils.contains(actual, "col2=L."));
		Assert.assertTrue(StringUtils.contains(actual, "col3=Jackson"));

		String actual1 = context.replaceTokens("${myData}[1]");
		Assert.assertTrue(StringUtils.contains(actual, "col1=Samuel"));
		Assert.assertTrue(StringUtils.contains(actual, "col2=L."));
		Assert.assertTrue(StringUtils.contains(actual, "col3=Jackson"));

		Assert.assertEquals("Yo yo Johnny,Samuel yaw!", context.replaceTokens("Yo yo ${myData}.col1 yaw!"));
	}

	static {
		System.setProperty("nexial.runMode", "local");
		System.setProperty("nexial.outBase", ".");
		System.setProperty("app.env", "DEV2");
		System.setProperty(OPT_DELAY_BROWSER, "true");
	}

}
