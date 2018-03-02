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

package org.nexial.core.utils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.nexial.commons.utils.DateUtility;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 *
 */
public class JsonHelperTest {
	public static class Company {
		String name;
		String address;
		String industry;
		boolean publicallyTraded;

		public String getName() { return name; }

		public void setName(String name) { this.name = name; }

		public String getAddress() { return address; }

		public void setAddress(String address) { this.address = address; }

		public String getIndustry() { return industry; }

		public void setIndustry(String industry) { this.industry = industry; }

		public boolean isPublicallyTraded() { return publicallyTraded; }

		public void setPublicallyTraded(boolean publicallyTraded) { this.publicallyTraded = publicallyTraded; }
	}

	public static class Person {
		String name;
		int age;
		Company company;
		double averageScore;
		Person[] friends;
		Date birthDate;

		public String getName() { return name; }

		public void setName(String name) { this.name = name; }

		public int getAge() { return age; }

		public void setAge(int age) { this.age = age; }

		public Company getCompany() { return company; }

		public void setCompany(Company company) { this.company = company; }

		public double getAverageScore() { return averageScore; }

		public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

		public Person[] getFriends() { return friends; }

		public void setFriends(Person[] friends) { this.friends = friends; }

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "PST")
		public Date getBirthDate() { return birthDate; }

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "PST")
		public void setBirthDate(Date birthDate) { this.birthDate = birthDate; }
	}

	@Before
	public void setUp() { }

	@After
	public void tearDown() { }

	@Test
	public void testRetrieveJSONObject() throws Exception {
		String fixture1 = "";
		Assert.assertNull(JsonHelper.retrieveJSONObject(JsonHelper.class, fixture1));

		String fixture2 = null;
		Assert.assertNull(JsonHelper.retrieveJSONObject(JsonHelper.class, fixture2));
		Assert.assertNull(JsonHelper.retrieveJSONObject(null, fixture2));

		String fixture3 = "does_not_exists";
		try {
			JsonHelper.retrieveJSONObject(JsonHelper.class, fixture3);
			Assert.fail("Should have failed since " + fixture3 + " does not exists");
		} catch (IOException e) {
			System.out.println("e.getMessage() = " + e.getMessage());
		} catch (JSONException e) {
			Assert.fail("Should have failed with IOException " + fixture3 + " does not exists");
		}

		String fixture4 = "/TESTAPP/function1/supportconfig.json";
		try {
			JSONObject json = JsonHelper.retrieveJSONObject(JsonHelper.class, fixture4);
			Assert.assertNotNull(json);
		} catch (IOException | JSONException e) {
			Assert.fail("retrieve " + fixture4 + " failed with " + e);
		}
	}

	@Test
	public void testFetchString() {
		String fixture1 = "{ " +
		                  " \"a\": { \"b\": \"1234\" }," +
		                  " \"c\": 5," +
		                  " \"d\": \"hello\"," +
		                  " \"e\": null," +
		                  " \"f\": []," +
		                  " \"g\": [ \"cat\", \"dog\", \"mouse\" ]," +
		                  " \"h\": { \"i.j\": \"yoyoma\" }, " +
		                  " \"k\": { \"l\": { \"m\": \"n\" } } " +
		                  "}";
		JSONObject json = new JSONObject(fixture1);
		Assert.assertEquals("hello", JsonHelper.fetchString(json, "d"));
		Assert.assertEquals("5", JsonHelper.fetchString(json, "c"));
		Assert.assertEquals("{\"b\":\"1234\"}", JsonHelper.fetchString(json, "a"));
		Assert.assertEquals("1234", JsonHelper.fetchString(json, "a.b"));
		Assert.assertNull(JsonHelper.fetchString(json, "e"));
		Assert.assertNull(JsonHelper.fetchString(json, "f"));
		Assert.assertNull(JsonHelper.fetchString(json, "f[0]"));
		Assert.assertNull(JsonHelper.fetchString(json, "f[1]"));
		Assert.assertEquals("[\"cat\",\"dog\",\"mouse\"]", JsonHelper.fetchString(json, "g"));
		Assert.assertEquals("cat", JsonHelper.fetchString(json, "g[0]"));
		Assert.assertEquals("dog", JsonHelper.fetchString(json, "g[1]"));
		Assert.assertEquals("mouse", JsonHelper.fetchString(json, "g[2]"));
		Assert.assertNull(JsonHelper.fetchString(json, "g[3]"));
		Assert.assertEquals("{\"i.j\":\"yoyoma\"}", JsonHelper.fetchString(json, "h"));
		Assert.assertEquals("yoyoma", JsonHelper.fetchString(json, "h[\"i.j\"]"));
		Assert.assertEquals("yoyoma", JsonHelper.fetchString(json, "h['i.j']"));
		Assert.assertEquals("yoyoma", JsonHelper.fetchString(json, "h[i.j]"));
		Assert.assertEquals("n", JsonHelper.fetchString(json, "k.l.m"));
	}

	@Test
	public void testToCompactString() {
		// test empty
		JSONObject fixture = new JSONObject("{}");
		String testData = JsonHelper.toCompactString(fixture, true);
		System.out.println("testData = " + testData);
		Assert.assertNotNull(testData);
		Assert.assertTrue(!testData.isEmpty());

		// test normal
		fixture = new JSONObject(
			                        "{\n" +
			                        "\t\"requestTimestamp\": 5192309591209,\n" +
			                        "\t\"userId\":           \"SYS\",\n" +
			                        "\t\"fein\":             \"13-1898818\",\n" +
			                        "\t\"stateCode\":        \"31\",\n" +
			                        "\t\"taxTypes\":          [\"SIT\", \"SUI\"]\n" +
			                        "}");
		testData = JsonHelper.toCompactString(fixture, true);
		System.out.println("testData = " + testData);
		Assert.assertNotNull(fixture);
		Assert.assertTrue(!testData.isEmpty());
		Assert.assertTrue(!testData.contains(" "));

		// test normal with false flag for removeNullNameValuePair
		fixture = new JSONObject(
			                        "{\n" +
			                        "\t\"requestTimestamp\": 5192309591209,\n" +
			                        "\t\"userId\":           \"SYS\",\n" +
			                        "\t\"fein\":             \"13-1898818\",\n" +
			                        "\t\"stateCode\":        \"31\",\n" +
			                        "\t\"taxTypes\":          [\"SIT\", \"SUI\"]\n" +
			                        "}");
		testData = JsonHelper.toCompactString(fixture, false);
		System.out.println("testData = " + testData);
		Assert.assertNotNull(fixture);
		Assert.assertTrue(!testData.isEmpty());
		Assert.assertTrue(!testData.contains(" "));

		// test null value
		fixture = new JSONObject(
			                        "{\n" +
			                        "\t\"requestTimestamp\": 5192309591209,\n" +
			                        "\t\"userId\":           \"SYS\",\n" +
			                        "\t\"fein\":             \"13-1898818\",\n" +
			                        "\t\"stateCode\":        null,\n" +
			                        "\t\"taxTypes\":          [\"SIT\", null]\n" +
			                        "}");
		testData = JsonHelper.toCompactString(fixture, true);
		System.out.println("testData = " + testData);
		Assert.assertNotNull(fixture);
		Assert.assertTrue(!testData.isEmpty());
		Assert.assertTrue(testData.contains("\"userId\":\"SYS\""));
		Assert.assertTrue(!testData.contains(" "));
		Assert.assertTrue(!testData.contains("stateCode"));
		// null array index not removed to ensure intended index positioning
		Assert.assertFalse(testData.contains("taxTypes:[\"SIT\"]"));

		// test null
		fixture = new JSONObject("{}");
		testData = JsonHelper.toCompactString(fixture, true);
		System.out.println("testData = " + testData);
		Assert.assertNotNull(testData);
		Assert.assertTrue(!testData.isEmpty());

		// test  null in the middle and at the end of json
		fixture = new JSONObject(
			                        "{\n" +
			                        "\t\"requestTimestamp\": 5192309591209,\n" +
			                        "\t\"userId\":           \"null\",\n" +
			                        "\t\"fein\":             null,\n" +
			                        "\t\"stateCode\":        null,\n" +
			                        "\t\"taxTypes\":         [\"SIT\", \"SUI\"],\n" +
			                        "\t\"taxTypes2\":        [],\n" +
			                        "\t\"taxTypes3\":        null\n" +
			                        "}");
		testData = JsonHelper.toCompactString(fixture, true);
		System.out.println("testData = " + testData);
		Assert.assertNotNull(fixture);
		Assert.assertTrue(!testData.isEmpty());
		Assert.assertTrue(!testData.contains(" "));

		// test (again) null in the middle and at the end of json
		fixture = new JSONObject("{\"key1\":\"myVal\",\"key2\":null,\"key3\":null}");
		testData = JsonHelper.toCompactString(fixture, true);
		System.out.println("testData = " + testData);
		Assert.assertNotNull(fixture);
		Assert.assertTrue(!testData.isEmpty());
		Assert.assertTrue(!testData.contains(" "));
	}

	@Test
	public void testLenientExtractTypedValue() {
		String jsonString = "{ " +
		                    "     \"name\"        : \"John Doe\", " +
		                    "     \"age\"         : 29, " +
		                    "     \"averageScore\": 34.351, " +
		                    "     \"birthDate\"   : \"1985-04-11\", " +
		                    "     \"company\"     : { " +
		                    "          \"name\"            : \"Acme Inc.\", " +
		                    "          \"address\"         : \" 1 Busy Street, Trouble Town\", " +
		                    "          \"industry\"        : \"Import/Export\", " +
		                    "          \"publicallyTraded\": false " +
		                    "     }, " +
		                    "     \"friends\"     : [ " +
		                    "          { " +
		                    "               \"name\"        : \"Jane Smith\", " +
		                    "               \"age\"         : 23, " +
		                    "               \"averageScore\": 19.353, " +
		                    "               \"birthDate\"   : \"1991-11-04\", " +
		                    "               \"company\"     : { " +
		                    "                    \"name\"    : \"BubbleWrap R Us\", " +
		                    "                    \"address\" : \"94 Dead End Drive, Gotham City\", " +
		                    "                    \"industry\": \"Health Supply\" " +
		                    "               } " +
		                    "          }, " +
		                    "          { " +
		                    "               \"name\"        : \"Jimmy Crafty\", " +
		                    "               \"age\"         : 31, " +
		                    "               \"averageScore\": 39.93, " +
		                    "               \"birthDate\"   : \"1983-07-24\" " +
		                    "          } " +
		                    "     ] " +
		                    "} ";
		JSONObject json = new JSONObject(jsonString);
		Person subject = JsonHelper.lenientExtractTypedValue(json, Person.class, new SimpleDateFormat("yyyy-MM-dd"));
		Assert.assertNotNull(subject);
		Assert.assertEquals("John Doe", subject.getName());
		Assert.assertEquals(29, subject.getAge());
		Assert.assertEquals(34.351, subject.getAverageScore(), 0.001);
		Assert.assertEquals(DateUtility.formatTo("1985-04-11", "yyyy-MM-dd"), subject.getBirthDate().getTime());

		Company company = subject.getCompany();
		Assert.assertNotNull(company);
		Assert.assertEquals("Acme Inc.", company.getName());
		Assert.assertEquals(" 1 Busy Street, Trouble Town", company.getAddress());
		Assert.assertEquals("Import/Export", company.getIndustry());
		Assert.assertFalse(company.isPublicallyTraded());

		Assert.assertNotNull(subject.getFriends());
		Assert.assertEquals(2, subject.getFriends().length);

		Person friend = subject.getFriends()[0];
		Assert.assertNotNull(friend);
		Assert.assertEquals("Jane Smith", friend.getName());
		Assert.assertEquals(23, friend.getAge());
		Assert.assertEquals(19.353, friend.getAverageScore(), 0.001);
		Assert.assertEquals(DateUtility.formatTo("1991-11-04", "yyyy-MM-dd"), friend.getBirthDate().getTime());

		company = friend.getCompany();
		Assert.assertNotNull(company);
		Assert.assertEquals("BubbleWrap R Us", company.getName());
		Assert.assertEquals("94 Dead End Drive, Gotham City", company.getAddress());
		Assert.assertEquals("Health Supply", company.getIndustry());

		friend = subject.getFriends()[1];
		Assert.assertNotNull(friend);
		Assert.assertEquals("Jimmy Crafty", friend.getName());
		Assert.assertEquals(31, friend.getAge());
		Assert.assertEquals(39.93, friend.getAverageScore(), 0.001);
		Assert.assertEquals(DateUtility.formatTo("1983-07-24", "yyyy-MM-dd"), friend.getBirthDate().getTime());
	}
}
