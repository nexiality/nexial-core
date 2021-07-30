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

package org.nexial.core.variable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class JsonDataTypeTest {
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void toJSONObject() throws Exception {
		JsonDataType jsonData = new JsonDataType("{ \"name\": \"John Smith\" }");

		Assert.assertTrue(jsonData.getValue() instanceof JsonObject);

		JSONObject json = jsonData.toJSONObject();
		Assert.assertNotNull(json);
		Assert.assertEquals("John Smith", json.optString("name"));

		// negative test
		try {
			jsonData.toJSONArray();
			Assert.fail("failure expected due to mismatched data type");
		} catch (ClassCastException e) {
			// expected
		}
	}

	@Test
	public void toJSONArray() throws Exception {
		JsonDataType jsonData = new JsonDataType("[ {\"name\":\"John Smith\"}, {\"name\":\"Jane Doe\"} ]");

		Assert.assertTrue(jsonData.getValue() instanceof JsonArray);

		JSONArray json = jsonData.toJSONArray();
		Assert.assertNotNull(json);
		Assert.assertEquals(2, json.length());
		Assert.assertEquals("John Smith", json.optJSONObject(0).optString("name"));
		Assert.assertEquals("Jane Doe", json.optJSONObject(1).optString("name"));

		// negative test
		try {
			jsonData.toJSONObject();
			Assert.fail("failure expected due to mismatched data type");
		} catch (ClassCastException e) {
			// expected
		}
	}

}