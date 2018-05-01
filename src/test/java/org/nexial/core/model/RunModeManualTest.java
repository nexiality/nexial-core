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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import static org.nexial.core.NexialConst.Data.START_URL;
import static org.nexial.core.NexialConst.*;

public class RunModeManualTest {
	private static final String SERVER_URL = "http://localhost/abcde";
	private static final String SERVER_BASEDIR = "yawza";
	private ExecutionContext context;

	@After
	public void tearDown() {
		if (context != null) { ((MockExecutionContext) context).cleanProject(); }
	}

	@Test
	public void testResolveUrl() {
		System.setProperty("log4j.configuration", "log4j.xml");
		System.setProperty(SELENIUM_FIREFOX_BIN, "C:\\Program Files\\Mozilla Firefox\\firefox.exe");

		context = new MockExecutionContext();
		context.setData(START_URL, "http://localhost");
		context.setData(OPT_REPORT_SERVER_URL, SERVER_URL);
		context.setData(OPT_REPORT_SERVER_BASEDIR, SERVER_BASEDIR);

		String testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\changnesia");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals(SERVER_URL + "/" + "changnesia", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\get.it.done");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals(SERVER_URL + "/" + "get.it.done", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\day-to-day");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals(SERVER_URL + "/" + "day-to-day", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\skillz eye got");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals(SERVER_URL + "/" + "skillz_eye_got", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\skillz&talent");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals(SERVER_URL + "/" + "skillz_talent", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\hash#this");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals(SERVER_URL + "/" + "hash_this", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\projects\\teh\\" + SERVER_BASEDIR
		                                                + "\\20130318_2305064\\TS_StateIDFormatsValidateion#1.xlsx");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals(SERVER_URL + "/" + "20130318_2305064/TS_StateIDFormatsValidateion_1.xlsx", testSubject);
	}

	@Test
	public void testResolveUrl_Local() {
		System.setProperty("log4j.configuration", "log4j.xml");
		System.setProperty(SELENIUM_FIREFOX_BIN, "C:\\Program Files\\Mozilla Firefox\\firefox.exe");

		context = new MockExecutionContext();
		context.setData(OPT_REPORT_SERVER_URL, SERVER_URL);
		context.setData(OPT_REPORT_SERVER_BASEDIR, SERVER_BASEDIR);
		context.setData(START_URL, "http://localhost");

		String testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\changnesia");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals("c:\\project\\" + SERVER_BASEDIR + "\\changnesia", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\get.it.done");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals("c:\\project\\" + SERVER_BASEDIR + "\\get.it.done", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\day-to-day");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals("c:\\project\\" + SERVER_BASEDIR + "\\day-to-day", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\skillz eye got");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals("c:\\project\\" + SERVER_BASEDIR + "\\skillz eye got", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\skillz&talent");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals("c:\\project\\" + SERVER_BASEDIR + "\\skillz&talent", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\project\\" + SERVER_BASEDIR + "\\hash#this");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals("c:\\project\\" + SERVER_BASEDIR + "\\hash#this", testSubject);

		testSubject = context.resolveRunModeSpecificUrl("c:\\projects\\teh\\" + SERVER_BASEDIR
		                                                + "\\20130318_2305064\\TS_StateIDFormatsValidateion#1.xlsx");
		Assert.assertNotNull(testSubject);
		Assert.assertEquals("c:\\projects\\teh\\" + SERVER_BASEDIR
		                    + "\\20130318_2305064\\TS_StateIDFormatsValidateion#1.xlsx",
		                    testSubject);
	}
}
