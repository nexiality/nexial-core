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

package org.nexial.core.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonElement;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.GSON_COMPRESSED;

public class JsonHelperTest {
    private String destinationBase;

    @Before
    public void setUp() {
        destinationBase = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator + "JsonHelperTest" + separator;
        new File(destinationBase).mkdirs();
    }

    @After
    public void tearDown() {
        if (destinationBase != null) { FileUtils.deleteQuietly(new File(destinationBase)); }
    }

    @Test
    public void testRetrieveJSONObject() throws Exception {
        Assert.assertNull(JsonHelper.retrieveJsonObject(JsonHelper.class, ""));
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
        Assert.assertEquals("[]", JsonHelper.fetchString(json, "f"));
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
    public void fromCsv_simple() throws Exception {

        List<List<String>> records = new ArrayList<>();
        records.add(Arrays.asList("NAME", "ADDRESS", "AGE"));
        records.add(Arrays.asList("Johnny", "123 Elm Street", "29"));
        records.add(Arrays.asList("Sam", "#14 Sesame Street", "35"));
        records.add(Arrays.asList("Shandra", "49 Mississippi Ave.", "7"));

        String destination = destinationBase + "fromCsv_simple.json";
        FileWriter writer = new FileWriter(destination);

        JsonHelper.fromCsv(records, true, writer);

        Assert.assertEquals("[" +
                            "{\"NAME\":\"Johnny\",\"ADDRESS\":\"123 Elm Street\",\"AGE\":\"29\"}," +
                            "{\"NAME\":\"Sam\",\"ADDRESS\":\"#14 Sesame Street\",\"AGE\":\"35\"}," +
                            "{\"NAME\":\"Shandra\",\"ADDRESS\":\"49 Mississippi Ave.\",\"AGE\":\"7\"}" +
                            "]",
                            readJsonContent(destination));
    }

    @Test
    public void fromCsv_uneven_records() throws Exception {
        List<List<String>> records = new ArrayList<>();
        records.add(Arrays.asList("NAME", "ADDRESS", "AGE"));
        records.add(Arrays.asList("Johnny", "123 Elm Street", "29"));
        records.add(Arrays.asList("Sam", "#14 Sesame Street"));
        records.add(Arrays.asList("Shandra", "49 Mississippi Ave.", "7", "123-ABC"));

        String destination = destinationBase + "fromCsv_uneven_records.json";
        FileWriter writer = new FileWriter(destination);

        JsonHelper.fromCsv(records, true, writer);

        Assert.assertEquals("[" +
                            "{\"NAME\":\"Johnny\",\"ADDRESS\":\"123 Elm Street\",\"AGE\":\"29\"}," +
                            "{\"NAME\":\"Sam\",\"ADDRESS\":\"#14 Sesame Street\"}," +
                            "{\"NAME\":\"Shandra\",\"ADDRESS\":\"49 Mississippi Ave.\",\"AGE\":\"7\"}" +
                            "]",
                            readJsonContent(destination));
    }

    @Test
    public void fromCsv_empty_records() throws Exception {
        List<List<String>> records = new ArrayList<>();
        records.add(Arrays.asList("NAME", "ADDRESS", "AGE"));
        records.add(Arrays.asList("Johnny", "123 Elm Street", "29"));
        records.add(Arrays.asList(""));
        records.add(Arrays.asList("Shandra"));
        records.add(Arrays.asList("     "));

        String destination = destinationBase + "fromCsv_empty_records.json";
        FileWriter writer = new FileWriter(destination);

        JsonHelper.fromCsv(records, true, writer);

        Assert.assertEquals("[" +
                            "{\"NAME\":\"Johnny\",\"ADDRESS\":\"123 Elm Street\",\"AGE\":\"29\"}," +
                            "{\"NAME\":\"\"}," +
                            "{\"NAME\":\"Shandra\"}," +
                            "{\"NAME\":\"     \"}" +
                            "]",
                            readJsonContent(destination));

        records = new ArrayList<>();
        records.add(Arrays.asList("Johnny", "123 Elm Street", "29"));
        records.add(Arrays.asList(""));
        records.add(Arrays.asList("Shandra"));
        records.add(Arrays.asList("     "));

        destination = destinationBase + "fromCsv_empty_records2.json";
        writer = new FileWriter(destination);

        JsonHelper.fromCsv(records, false, writer);

        Assert.assertEquals("[" +
                            "[\"Johnny\",\"123 Elm Street\",\"29\"]," +
                            "[\"\"]," +
                            "[\"Shandra\"]," +
                            "[\"     \"]" +
                            "]",
                            readJsonContent(destination));
    }

    @Test
    public void fromCsv_before_after() throws Exception {
        List<List<String>> records = new ArrayList<>();
        records.add(Arrays.asList("NAME", "ADDRESS", "AGE"));
        records.add(Arrays.asList("Johnny", "123 Elm Street", "29"));
        records.add(Arrays.asList("Sam", "#14 Sesame Street", "35"));
        records.add(Arrays.asList("Shandra", "49 Mississippi Ave.", "7"));

        String destination = destinationBase + "fromCsv_simple.json";
        FileWriter writer = new FileWriter(destination);

        JsonHelper.fromCsv(records,
                           true,
                           before -> before.write("{ \"version\": \"12.3401b\", \"details\": "),
                           writer,
                           after -> after.write("}"));

        Assert.assertEquals("{" +
                            "\"version\":\"12.3401b\"," +
                            "\"details\":" +
                            "[" +
                            "{\"NAME\":\"Johnny\",\"ADDRESS\":\"123 Elm Street\",\"AGE\":\"29\"}," +
                            "{\"NAME\":\"Sam\",\"ADDRESS\":\"#14 Sesame Street\",\"AGE\":\"35\"}," +
                            "{\"NAME\":\"Shandra\",\"ADDRESS\":\"49 Mississippi Ave.\",\"AGE\":\"7\"}" +
                            "]" +
                            "}",
                            readJsonContent(destination));
    }

    @Test
    public void fromCsv_nested_before_after() throws Exception {
        List<List<String>> records = new ArrayList<>();
        records.add(Arrays.asList("NAME", "ADDRESS", "AGE"));
        records.add(Arrays.asList("Johnny", "123 Elm Street", "29"));
        records.add(Arrays.asList("Sam", "#14 Sesame Street", "35"));
        records.add(Arrays.asList("Shandra", "49 Mississippi Ave.", "7"));

        List<List<String>> records2 = new ArrayList<>();
        records2.add(Arrays.asList("94", "Red", "29"));
        records2.add(Arrays.asList("78", "Yellow", "35"));
        records2.add(Arrays.asList("34.2", "Blue", "7"));

        String destination = destinationBase + "fromCsv_simple.json";
        FileWriter writer = new FileWriter(destination);

        JsonHelper.fromCsv(records,
                           true,
                           before -> before.write("{ \"version\": \"12.3401b\", \"details\": "),
                           writer,
                           after -> JsonHelper.fromCsv(records2,
                                                       false,
                                                       before -> before.write(",\"stats\":"),
                                                       after,
                                                       after2 -> after2.write(", \"complete\":true}")));

        Assert.assertEquals("{" +
                            "\"version\":\"12.3401b\"," +
                            "\"details\":[" +
                            "{\"NAME\":\"Johnny\",\"ADDRESS\":\"123 Elm Street\",\"AGE\":\"29\"}," +
                            "{\"NAME\":\"Sam\",\"ADDRESS\":\"#14 Sesame Street\",\"AGE\":\"35\"}," +
                            "{\"NAME\":\"Shandra\",\"ADDRESS\":\"49 Mississippi Ave.\",\"AGE\":\"7\"}" +
                            "]," +
                            "\"stats\":[" +
                            "[\"94\",\"Red\",\"29\"]," +
                            "[\"78\",\"Yellow\",\"35\"]," +
                            "[\"34.2\",\"Blue\",\"7\"]" +
                            "]," +
                            "\"complete\":true" +
                            "}",
                            readJsonContent(destination));
    }

    private String readJsonContent(String destination) throws IOException {
        String content = FileUtils.readFileToString(new File(destination), DEF_FILE_ENCODING);
        System.out.println(content);
        return GSON_COMPRESSED.toJson(GSON_COMPRESSED.fromJson(content, JsonElement.class));
    }

}
