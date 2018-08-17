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

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class JsonHelperTest {

    @Before
    public void setUp() { }

    @After
    public void tearDown() { }

    @Test
    public void testRetrieveJSONObject() throws Exception {
        String fixture1 = "";
        Assert.assertNull(JsonHelper.retrieveJsonObject(JsonHelper.class, fixture1));

        Assert.assertNull(JsonHelper.retrieveJsonObject(JsonHelper.class, null));
        Assert.assertNull(JsonHelper.retrieveJsonObject(null, null));

        String fixture3 = "does_not_exists";
        try {
            JsonHelper.retrieveJsonObject(JsonHelper.class, fixture3);
            Assert.fail("Should have failed since " + fixture3 + " does not exists");
        } catch (IOException e) {
            System.out.println("e.getMessage() = " + e.getMessage());
        } catch (JSONException e) {
            Assert.fail("Should have failed with IOException " + fixture3 + " does not exists");
        }

        String fixture4 = "/org/nexial/core/utils/supportconfig.json";
        try {
            JSONObject json = JsonHelper.retrieveJsonObject(JsonHelper.class, fixture4);
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
}
