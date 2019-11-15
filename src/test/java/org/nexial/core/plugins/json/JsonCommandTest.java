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

package org.nexial.core.plugins.json;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;

import com.google.gson.JsonArray;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.Data.LAST_JSON_COMPARE_RESULT;
import static org.nexial.core.NexialConst.Data.TREAT_JSON_AS_IS;
import static org.nexial.core.NexialConst.GSON_COMPRESSED;

public class JsonCommandTest {
    private MockExecutionContext context = new MockExecutionContext();
    private String destinationBase;

    @Before
    public void init() {
        if (context == null) { context = new MockExecutionContext(); }
        destinationBase = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator + "JsonCommandTest" + separator;
    }

    @After
    public void tearDown() {
        if (context != null) { context.cleanProject(); }
        if (destinationBase != null) { FileUtils.deleteQuietly(new File(destinationBase)); }
    }

    @Test
    public void assertElementPresent() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{" +
                      "   \"results\" : [" +
                      "      {" +
                      "         \"address_components\" : [" +
                      "            {" +
                      "               \"long_name\" : \"1600\"," +
                      "               \"short_name\" : \"1600\"," +
                      "               \"types\" : [ \"street_number\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"Amphitheatre Parkway\"," +
                      "               \"short_name\" : \"Amphitheatre Pkwy\"," +
                      "               \"types\" : [ \"route\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"Mountain View\"," +
                      "               \"short_name\" : \"Mountain View\"," +
                      "               \"types\" : [ \"locality\", \"political\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"Santa Clara\"," +
                      "               \"short_name\" : \"Santa Clara\"," +
                      "               \"types\" : [ \"administrative_area_level_2\", \"political\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"California\"," +
                      "               \"short_name\" : \"CA\"," +
                      "               \"types\" : [ \"administrative_area_level_1\", \"political\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"United States\"," +
                      "               \"short_name\" : \"US\"," +
                      "               \"types\" : [ \"country\", \"political\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"94043\"," +
                      "               \"short_name\" : \"94043\"," +
                      "               \"types\" : [ \"postal_code\" ]" +
                      "            }" +
                      "         ]," +
                      "         \"formatted_address\" : \"1600 Amphitheatre Parkway, Mountain View, CA 94043, USA\"," +
                      "         \"geometry\" : {" +
                      "            \"location\" : {" +
                      "               \"lat\" : 37.42151070," +
                      "               \"lng\" : -122.08400970" +
                      "            }," +
                      "            \"location_type\" : \"ROOFTOP\"," +
                      "            \"viewport\" : {" +
                      "               \"northeast\" : {" +
                      "                  \"lat\" : 37.42285968029150," +
                      "                  \"lng\" : -122.0826607197085" +
                      "               }," +
                      "               \"southwest\" : {" +
                      "                  \"lat\" : 37.42016171970850," +
                      "                  \"lng\" : -122.0853586802915" +
                      "               }" +
                      "            }" +
                      "         }," +
                      "         \"types\" : [ \"street_address\" ]" +
                      "      }" +
                      "   ]," +
                      "   \"status\" : \"OK\"" +
                      "}";
        Assert.assertTrue(fixture.assertElementPresent(json, "results.formatted_address").isSuccess());
        Assert.assertTrue(fixture.assertElementPresent(json, "status[OK]").isSuccess());
        Assert.assertTrue(fixture.assertElementPresent(json, "results.geometry.location.lng").isSuccess());
        Assert.assertTrue(fixture.assertElementPresent(json, "results[0].address_components[4].types[1]").isSuccess());
        Assert.assertTrue(fixture.assertElementPresent(json, "results[0].address_components[5].types[country]")
                                 .isSuccess());
    }

    @Test
    public void find() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{" +
                      "   \"results\" : [" +
                      "      {" +
                      "         \"address_components\" : [" +
                      "            {" +
                      "               \"long_name\" : \"1600\"," +
                      "               \"short_name\" : \"1600\"," +
                      "               \"types\" : [ \"street_number\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"Amphitheatre Parkway\"," +
                      "               \"short_name\" : \"Amphitheatre Pkwy\"," +
                      "               \"types\" : [ \"route\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"Mountain View\"," +
                      "               \"short_name\" : \"Mountain View\"," +
                      "               \"types\" : [ \"locality\", \"political\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"Santa Clara\"," +
                      "               \"short_name\" : \"Santa Clara\"," +
                      "               \"types\" : [ \"administrative_area_level_2\", \"political\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"California\"," +
                      "               \"short_name\" : \"CA\"," +
                      "               \"types\" : [ \"administrative_area_level_1\", \"political\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"United States\"," +
                      "               \"short_name\" : \"US\"," +
                      "               \"types\" : [ \"country\", \"political\" ]" +
                      "            }," +
                      "            {" +
                      "               \"long_name\" : \"94043\"," +
                      "               \"short_name\" : \"94043\"," +
                      "               \"types\" : [ \"postal_code\" ]" +
                      "            }" +
                      "         ]," +
                      "         \"formatted_address\" : \"1600 Amphitheatre Parkway, Mountain View, CA 94043, USA\"," +
                      "         \"geometry\" : {" +
                      "            \"location\" : {" +
                      "               \"lat\" : 37.42151070," +
                      "               \"lng\" : -122.08400970" +
                      "            }," +
                      "            \"location_type\" : \"ROOFTOP\"," +
                      "            \"viewport\" : {" +
                      "               \"northeast\" : {" +
                      "                  \"lat\" : 37.42285968," +
                      "                  \"lng\" : -122.0826607197" +
                      "               }," +
                      "               \"southwest\" : {" +
                      "                  \"lat\" : 37.4201617197," +
                      "                  \"lng\" : -122.085358680" +
                      "               }" +
                      "            }" +
                      "         }," +
                      "         \"types\" : [ \"street_address\" ]" +
                      "      }" +
                      "   ]," +
                      "   \"status\" : \"OK\"" +
                      "}";

        Assert.assertEquals("OK", fixture.find(json, "status"));
        Assert.assertEquals("1600 Amphitheatre Parkway, Mountain View, CA 94043, USA",
                            fixture.find(json, "results.formatted_address"));
        Assert.assertEquals("37.4201617197", fixture.find(json, "results.geometry.viewport.southwest.lat"));
        Assert.assertEquals("1600", fixture.find(json, "results.address_components.short_name[0]"));
        Assert.assertEquals("CA", fixture.find(json, "results.address_components[4].short_name"));
        Assert.assertEquals("[\"administrative_area_level_1\",\"political\"]",
                            fixture.find(json, "results.address_components[4].types"));
        Assert.assertEquals("{\"lng\":-122.0826607197,\"lat\":37.42285968}",
                            fixture.find(json, "results.geometry.viewport.northeast"));
    }

    @Test
    public void count() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{ " +
                      "  \"a\": \"b\", " +
                      "  \"c\": [\"d\", \"e\", \"f\", \"g\"], " +
                      "  \"h\": { " +
                      "    \"i\": [ " +
                      "      { " +
                      "        \"j\": \"k\" " +
                      "      }, " +
                      "      { " +
                      "        \"j\": \"m\" " +
                      "      }, " +
                      "      { " +
                      "        \"n\": \"o\" " +
                      "      } " +
                      "    ], " +
                      "    \"p\": \"q\" " +
                      "  } " +
                      "}";

        Assert.assertEquals(fixture.count(json, "a"), 1);
        Assert.assertEquals(fixture.count(json, "c"), 4);
        Assert.assertEquals(fixture.count(json, "h.i.j"), 2);
        Assert.assertEquals(fixture.count(json, "h.i[j]"), 2);
        Assert.assertEquals(fixture.count(json, "h.p"), 1);
        Assert.assertEquals(fixture.count(json, "h.p.q"), 1);
        Assert.assertEquals(fixture.count(json, "junk"), 0);
        try {
            Assert.assertEquals(fixture.count(json, ""), 0);
            Assert.fail("expected failure not thrown");
        } catch (Throwable e) {
            // expected
        }
    }

    @Test
    public void count_Array() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "[" +
                      " { \"name\": \"John\" }, " +
                      " { \"name\": \"Peter\" }, " +
                      " { \"name\": \"Johnny\" }, " +
                      " { \"name\": \"Johnathan\" }, " +
                      " { \"name\": [\"Johnson\", \"John-John\", \"Bob\" ] } " +
                      "]";

        Assert.assertEquals("[{\"name\":\"John\"},{\"name\":\"Johnny\"},{\"name\":\"Johnathan\"}]",
                            fixture.find(json, "[name=REGEX:John.*]"));
        Assert.assertEquals(3, fixture.count(json, "[name=REGEX:John.*]"));

    }

    @Test
    public void wellform() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{ " +
                      "  \"a\": \"b\", " +
                      "  \"c\": [\"d\", \"e\", \"f\", \"g\"], " +
                      "  \"h\": { " +
                      "    \"i\": [ " +
                      "      { " +
                      "        \"j\": \"k\" " +
                      "      }, " +
                      "      { " +
                      "        \"j\": \"m\" " +
                      "      }, " +
                      "      { " +
                      "        \"n\": \"o\" " +
                      "      } " +
                      "    ], " +
                      "    \"p\": \"q\" " +
                      "  } " +
                      "}";
        Assert.assertTrue(fixture.assertWellformed(json).isSuccess());

        // ignore whitespace
        Assert.assertTrue(fixture.assertWellformed("\t\t\n\n   \r\t  \n" + json + "\r\n \t \t \t  \r").isSuccess());

        // json array friendly
        Assert.assertTrue(fixture.assertWellformed("[ " + json + ", { } ]").isSuccess());
    }

    @Test
    public void notWellform() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{ " +
                      "  \"a\": \"b\", " +
                      "  \"c\": [\"d\", \"e\", \"f\", \"g\"], " +
                      "  \"h\": { " +
                      "    \"i\": [ " +
                      "      { " +
                      "        \"j\": \"k\" " +
                      "      }, " +
                      "      { " +
                      "        \"j\": \"m\" " +
                      "      }, " +
                      "      { " +
                      "        \"n\": \"o\" " +
                      "      } " +
                      "    ], " +
                      "    \"p\": \"q\" " +
                      "  } " +
                      "}";
        try {
            Assert.assertFalse(fixture.assertWellformed(StringUtils.replace(json, ",", "")).isSuccess());
        } catch (AssertionError e) {
            // expected
        }

        try {
            Assert.assertFalse(fixture.assertWellformed(json + ",{}").isSuccess());
        } catch (AssertionError e) {
            // expected
        }

        try {
            Assert.assertTrue(fixture.assertWellformed("[" + json + " ").isSuccess());
        } catch (AssertionError e) {
            // expected
        }
    }

    @Test
    public void correctness() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String schemaLocation = StringUtils.replace(this.getClass().getPackage().getName(), ".", "/") +
                                "/JsonCommandTest-schema.json";
        Assert.assertTrue(StringUtils.isNotBlank(schemaLocation));
        System.out.println("schemaLocation = " + schemaLocation);

        String json = "{\n" +
                      "  \"address\": {\n" +
                      "    \"streetAddress\": \"21 2nd Street\",\n" +
                      "    \"city\": \"New York\"\n" +
                      "  },\n" +
                      "  \"phoneNumber\": [\n" +
                      "    {\n" +
                      "      \"location\": \"home\",\n" +
                      "      \"number\": \"212 555-1234\"\n" +
                      "    }\n" +
                      "  ]\n" +
                      "}";
        Assert.assertTrue(fixture.assertCorrectness(json, schemaLocation).isSuccess());

        json = "{\n" +
               "  \"address\": {\n" +
               "    \"streetAddress\": \"21 2nd Street\",\n" +
               "    \"city\": \"New York\"\n" +
               "  },\n" +
               "  \"phoneNumber\": [\n" +
               "    {\n" +
               "      \"location\": \"home\",\n" +
               "      \"number\": \"212 555-1234\"\n" +
               "    }\n" +
               "  ]\n" +
               "}";
        try {
            Assert.assertTrue(fixture.assertCorrectness(json, schemaLocation).failed());
        } catch (Throwable e) {
            // expected
        }

    }

    @Test
    public void correctness2() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String schemaLocation = this.getClass().getResource("JsonCommandTest-schema.json").getFile();
        Assert.assertTrue(StringUtils.isNotBlank(schemaLocation));
        System.out.println("schemaLocation = " + schemaLocation);

        String json = "{\n" +
                      "  \"address\": {\n" +
                      "    \"streetAddress\": \"21 2nd Street\",\n" +
                      "    \"city\": \"New York\"\n" +
                      "  },\n" +
                      "  \"phoneNumber\": [\n" +
                      "    {\n" +
                      "      \"location\": \"home\",\n" +
                      "      \"number\": \"212 555-1234\"\n" +
                      "    }\n" +
                      "  ]\n" +
                      "}";
        Assert.assertTrue(fixture.assertCorrectness(json, schemaLocation).isSuccess());

        json = "{\n" +
               "  \"address\": {\n" +
               "    \"streetAddress\": \"21 2nd Street\",\n" +
               "    \"city\": \"New York\"\n" +
               "  },\n" +
               "  \"phoneNumber\": [\n" +
               "    {\n" +
               "      \"location\": \"home\",\n" +
               "      \"number\": \"212 555-1234\"\n" +
               "    }\n" +
               "  ]\n" +
               "}";
        try {
            Assert.assertTrue(fixture.assertCorrectness(json, schemaLocation).failed());
        } catch (Throwable e) {
            // expected
        }

    }

    @Test
    public void correctness3() {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String schemaLocation = this.getClass().getResource("ConfirmMessage_v1_0_revision007_schema.json").getFile();
        Assert.assertTrue(StringUtils.isNotBlank(schemaLocation));
        System.out.println("schemaLocation = " + schemaLocation);

        String json = "{\n" +
                      "   \"confirmMessageID\": {\"idValue\": \"e39a9113-a7f8-4d72-9fb6-df7380ed0ff4\"},\n" +
                      "   \"createDateTime\": \"Dec 31, 1969 3:59:59 PM\",\n" +
                      "   \"protocolStatusCode\": {\"codeValue\": \"500\"},\n" +
                      "   \"protocolCode\": {\"codeValue\": \"HTTP/1.1\"},\n" +
                      "   \"requestID\": {\"idValue\": null},\n" +
                      "   \"requestStatusCode\": {\"codeValue\": null},\n" +
                      "   \"requestMethodCode\": {\"codeValue\": null},\n" +
                      "   \"requestLink\":    {\n" +
                      "      \"href\": \"/onboardingros/api/rest/core/v1/accessgroups/17\",\n" +
                      "      \"rel\": \"related\",\n" +
                      "      \"method\": \"GET\"\n" +
                      "   },\n" +
                      "   \"sessionID\":    {\n" +
                      "      \"idValue\": \"QkZB56j4uM7F9zu_YaH0HQ5\",\n" +
                      "      \"schemeName\": \"ADPFACS\",\n" +
                      "      \"schemeAgencyName\": \"sessionID\"\n" +
                      "   },\n" +
                      "   \"resourceMessages\": [   {\n" +
                      "      \"resourceMessageID\": {\"idValue\": null},\n" +
                      "      \"resourceStatusCode\": {\"codeValue\": null},\n" +
                      "      \"processMessages\": [      {\n" +
                      "         \"processMessageID\": {\"idValue\": null},\n" +
                      "         \"messageTypeCode\": {\"codeValue\": null},\n" +
                      "         \"developerMessage\":          {\n" +
                      "            \"code\": null,\n" +
                      "            \"title\": \"ApplicationException\",\n" +
                      "            \"value\": null\n" +
                      "         },\n" +
                      "         \"userMessage\":          {\n" +
                      "            \"code\": \"7001\",\n" +
                      "            \"title\": \"ApplicationException\",\n" +
                      "            \"value\": \"OOID is missing in the header to access this web service.\"\n" +
                      "         }\n" +
                      "      }]\n" +
                      "   }]\n" +
                      "}";
        Assert.assertTrue(fixture.assertCorrectness(json, schemaLocation).isSuccess());

    }

    @Test
    public void toJsonObject() {
        String fixture =
            "{" +
            "   \"timestamp\":\"2017-09-25T19:23:24.419-07:00\"," +
            "   \"status\":405," +
            "   \"error\":\"Method Not Allowed\"," +
            "   \"exception\":\"org.springframework.web.HttpRequestMethodNotSupportedException\"," +
            "   \"message\":\"Request method \\u0027PATCH\\u0027 not supported, doesn\\u0027t work anyways\"," +
            "   \"path\":\"/notification/client/rP58k9yEFflmwGGngGdigkPRxkmzFnlC/deletePhone\"" +
            "}";

        JsonCommand subject = new JsonCommand();
        subject.init(context);
        Object jsonObject = subject.toJSONObject(fixture);
        Assert.assertNotNull(jsonObject);
        Assert.assertTrue(jsonObject instanceof JSONObject);

        JSONObject json = (JSONObject) jsonObject;
        Assert.assertEquals("Request method 'PATCH' not supported, doesn't work anyways", json.optString("message"));
    }

    @Test
    public void toJsonObject2() {
        String fixture =
            "{" +
            "   \"timestamp\":\"2017-09-25T19:23:24.419-07:00\"," +
            "   \"status\":405," +
            "   \"error\":\"Method Not Allowed\"," +
            "   \"exception\":\"org.springframework.web.HttpRequestMethodNotSupportedException\"," +
            "   \"message\":\"Request method \\u005BPATCH\\u005D not supported\\u003A just not my style\"," +
            "   \"path\":\"/notification/client/rP58k9yEFflmwGGngGdigkPRxkmzFnlC/deletePhone\"" +
            "}";

        JsonCommand subject = new JsonCommand();
        subject.init(context);
        Object jsonObject = subject.toJSONObject(fixture);
        Assert.assertNotNull(jsonObject);
        Assert.assertTrue(jsonObject instanceof JSONObject);

        JSONObject json = (JSONObject) jsonObject;
        Assert.assertEquals("Request method [PATCH] not supported: just not my style", json.optString("message"));
    }

    @Test
    public void parseFloatingAmounts() {
        String fixture = "{" +
                         "  \"deductionList\": [" +
                         "      {\"deductionType\":\"PRE\",\"deductionAmount\":100.00}," +
                         "      {\"deductionType\":\"POST\",\"deductionAmount\":0.00}" +
                         "  ]" +
                         "}";

        JsonCommand subject = new JsonCommand();
        subject.init(context);
        Assert.assertEquals("0.00", subject.find(fixture, "deductionList[1].deductionAmount"));
        Assert.assertEquals("[100.00,0.00]", subject.find(fixture, "deductionList.deductionAmount"));

        Assert.assertTrue(subject.assertValue(fixture, "deductionList[1].deductionAmount", "0.00").isSuccess());
        Assert.assertTrue(subject.assertValues(fixture, "deductionList.deductionAmount", "100.00,0.00", "true")
                                 .isSuccess());

    }

    @Test
    public void fromCsv_withHeader() throws Exception {
        context.setData("nexial.textDelim", "|");
        String fixture = "ID|NAME|AGE|ADDRESS|TITLE\r\n" +
                         "001|John Doe|17|154 Merlin Ave., Party Town, CA 90235|Senior Intern\n" +
                         "002|Cassine LaCoprale|25|2054 Sourcano Avenida, Mi Casa|El Jefe\n" +
                         "003|Chow Mi Toue|23|Apt 12-B, 9209 Ching Tong Rd, Konmandon, Mi Gouk|Big Boss Man\n";

        String destination = destinationBase + "fromCsv_withHeader.json";

        JsonCommand subject = new JsonCommand();
        subject.init(context);
        StepResult result = subject.fromCsv(fixture, "true", destination);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        String output = FileUtils.readFileToString(new File(destination), DEF_FILE_ENCODING);
        System.out.println(output);
        System.out.println();

        JsonArray array = GSON_COMPRESSED.fromJson(output, JsonArray.class);
        Assert.assertNotNull(array);
        Assert.assertEquals(3, array.size());
        String template = "{\"ID\":\"%s\",\"NAME\":\"%s\",\"AGE\":\"%s\",\"ADDRESS\":\"%s\",\"TITLE\":\"%s\"}";
        Assert.assertEquals(String.format(template,
                                          "001",
                                          "John Doe",
                                          "17",
                                          "154 Merlin Ave., Party Town, CA 90235",
                                          "Senior Intern"),
                            array.get(0).getAsJsonObject().toString());
        Assert.assertEquals(String.format(template,
                                          "002",
                                          "Cassine LaCoprale",
                                          "25",
                                          "2054 Sourcano Avenida, Mi Casa",
                                          "El Jefe"),
                            array.get(1).getAsJsonObject().toString());
        Assert.assertEquals(String.format(template,
                                          "003",
                                          "Chow Mi Toue",
                                          "23",
                                          "Apt 12-B, 9209 Ching Tong Rd, Konmandon, Mi Gouk",
                                          "Big Boss Man"),
                            array.get(2).getAsJsonObject().toString());
    }

    @Test
    public void fromCsv_noHeader() throws Exception {
        context.setData("nexial.textDelim", "|");
        String fixture = "ID|NAME|AGE|ADDRESS|TITLE\n" +
                         "001|John Doe|17|154 Merlin Ave., Party Town, CA 90235|Senior Intern\n" +
                         "002|Cassine LaCoprale|25|2054 Sourcano Avenida, Mi Casa|El Jefe\n\r" +
                         "003|Chow Mi Toue|23|Apt 12-B, 9209 Ching Tong Rd, Konmandon, Mi Gouk|Big Boss Man\n";

        String destination = destinationBase + "fromCsv_noHeader.json";

        JsonCommand subject = new JsonCommand();
        subject.init(context);
        StepResult result = subject.fromCsv(fixture, "false", destination);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        String output = FileUtils.readFileToString(new File(destination), DEF_FILE_ENCODING);
        System.out.println(output);
        System.out.println();

        JsonArray array = GSON_COMPRESSED.fromJson(output, JsonArray.class);
        Assert.assertNotNull(array);
        Assert.assertEquals(4, array.size());
        String template = "[\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"]";
        Assert.assertEquals(String.format(template, "ID", "NAME", "AGE", "ADDRESS", "TITLE"),
                            array.get(0).getAsJsonArray().toString());
        Assert.assertEquals(String.format(template,
                                          "001",
                                          "John Doe",
                                          "17",
                                          "154 Merlin Ave., Party Town, CA 90235",
                                          "Senior Intern"),
                            array.get(1).getAsJsonArray().toString());
        Assert.assertEquals(String.format(template,
                                          "002",
                                          "Cassine LaCoprale",
                                          "25",
                                          "2054 Sourcano Avenida, Mi Casa",
                                          "El Jefe"),
                            array.get(2).getAsJsonArray().toString());
        Assert.assertEquals(String.format(template,
                                          "003",
                                          "Chow Mi Toue",
                                          "23",
                                          "Apt 12-B, 9209 Ching Tong Rd, Konmandon, Mi Gouk",
                                          "Big Boss Man"),
                            array.get(3).getAsJsonArray().toString());
    }

    @Test
    public void assertValue_or_Values() throws Exception {
        context.setData("jobParameters", "[]");
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{ " +
                      " \"response\": { " +
                      "     \"jobParameters\": [    \t \t \n \n \t   ] " +
                      " } " +
                      "}";

        System.out.println("data = " + fixture.find(json, "response.jobParameters"));
        System.out.println("data = " + fixture.find(json, "response.jobParameters[]"));

        Assert.assertTrue(fixture.assertValue(json, "response.jobParameters", "[]").isSuccess());

        Assert.assertTrue(fixture.assertValues(json, "response.jobParameters", "[]", "false").isSuccess());
        Assert.assertTrue(fixture.assertValues(json,
                                               "response.jobParameters",
                                               context.replaceTokens("(null)"), "false").isSuccess());
        Assert.assertTrue(fixture.assertValues(json, "response.jobParameters", "", "false").isSuccess());
        Assert.assertTrue(fixture.assertValues(json, "response.jobParameters", null, "false").isSuccess());
        Assert.assertTrue(fixture.assertValues(json,
                                               "response.jobParameters",
                                               context.replaceTokens("[TEXT(${jobParameters}) => removeRegex(\\[\\])]"),
                                               "false").isSuccess());

        Assert.assertTrue(fixture.assertElementCount(json, "response.jobParameters", "0").isSuccess());
        Assert.assertTrue(fixture.assertElementCount(json, "response.jobParameters[]", "0").isSuccess());
    }

    @Test
    public void assertValues() throws Exception {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        Assert.assertTrue(fixture.assertValues("{\"a\":[{\"name\":\"John\"},{\"name\":\"Jim\"},{\"name\":\"James\"}]}",
                                               "a.name", "John,Jim,James", "true").isSuccess());

        Assert.assertTrue(fixture.assertValues("{\"b\":[{\"id\":2},{\"id\":23},{\"id\":235}]}",
                                               "b.id", "2,23,235", "true").isSuccess());

        Assert.assertTrue(fixture.assertValues("{\"c\":[{\"id\":2},{\"id\":23},{\"id\":235}]}",
                                               "c.id", "23,235,2", "false").isSuccess());

        Assert.assertTrue(fixture.assertValues("{\"a\":[" +
                                               "    {\"name\":\"John A.\"}," +
                                               "    {\"name\":\"Jim C.\"}," +
                                               "    {\"name\":\"James W.\"}" +
                                               "]}",
                                               "a.name", "John A.,Jim C.,James W.", "true").isSuccess());

        Assert.assertTrue(fixture.assertValues("{\"a\":[" +
                                               "    {\"name\":\"John A.\"}," +
                                               "    {\"name\":\"Jim C.\"}," +
                                               "    {\"name\":\"James W.\"}" +
                                               "]}",
                                               "a",
                                               "{\"name\":\"John A.\"}, {\"name\":\"Jim C.\"}, {\"name\":\"James W.\"}",
                                               "true").isSuccess());

        Assert.assertTrue(fixture.assertValues("{\"a\":[" +
                                               "    {\"name\":\"John A.\"}," +
                                               "    {\"name\":\"Jim C.\"}," +
                                               "    {\"name\":\"James W.\"}" +
                                               "]}",
                                               "a",
                                               "{\"name\":\"Jim C.\"}, {\"name\":\"John A.\"}, {\"name\":\"James W.\"}",
                                               "false").isSuccess());

        Assert.assertFalse(fixture.assertValues("{\"c\":[{\"id\":2},{\"id\":23},{\"id\":235}]}",
                                                "c.id", "23,2,235,2", "false").isSuccess());
    }

    @Test
    public void toJsonArray_simple() throws Exception {
        String delim = ",";

        JsonArray array = JsonCommand.toJsonArray("a,b,c,d,e", delim);
        Assert.assertNotNull(array);
        Assert.assertEquals(5, array.size());

        array = JsonCommand.toJsonArray("a, b, c, d,e", delim);
        Assert.assertNotNull(array);
        Assert.assertEquals(5, array.size());

        array = JsonCommand.toJsonArray("a, b,true,27,0.09283", delim);
        Assert.assertNotNull(array);
        Assert.assertEquals(5, array.size());
        Assert.assertEquals("a", array.get(0).getAsString());
        Assert.assertEquals("b", array.get(1).getAsString());
        Assert.assertEquals("true", array.get(2).getAsString());
        Assert.assertEquals("27", array.get(3).getAsString());
        Assert.assertEquals("0.09283", array.get(4).getAsString());

        array = JsonCommand.toJsonArray("a, b,true,27, this is a test", delim);
        Assert.assertNotNull(array);
        Assert.assertEquals(5, array.size());
        Assert.assertEquals("a", array.get(0).getAsString());
        Assert.assertEquals(" b", array.get(1).getAsString());
        Assert.assertEquals("true", array.get(2).getAsString());
        Assert.assertEquals("27", array.get(3).getAsString());
        Assert.assertEquals(" this is a test", array.get(4).getAsString());

        // this one FAILS to parse!!!
        // array = JsonCommand.toJsonArray("a, b,true,{\"key\":\"value\"}, this is a test", delim);
        // Assert.assertNotNull(array);
        // Assert.assertEquals(5, array.size());
        // Assert.assertEquals("a", array.get(0).getAsString());
        // Assert.assertEquals(" b", array.get(1).getAsString());
        // Assert.assertEquals("true", array.get(2).getAsString());
        // Assert.assertEquals("{\"key\":\"value\"}", array.get(3).getAsJsonObject().toString());
        // Assert.assertEquals(" this is a test", array.get(4).getAsString());
    }

    @Test
    public void extractJsonValue() throws Exception {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{\n" +
                      "    \"shipping_address\": [\n" +
                      "        {\n" +
                      "            \"street_address\": \"1600 Pennsylvania Avenue NW\",\n" +
                      "            \"city\": \"Washington\",\n" +
                      "            \"state\": \"DC\"\n" +
                      "        },\n" +
                      "        {\n" +
                      "            \"street_address\": \"1733 Washington Avenue\",\n" +
                      "            \"city\": \"Glenoak\",\n" +
                      "            \"state\": \"CA\"\n" +
                      "        }\n" +
                      "    ],\n" +
                      "    \"billing_address\": {\n" +
                      "        \"street_address\": \"1st Street SE\",\n" +
                      "        \"city\": \"Washington\",\n" +
                      "        \"state\": \"DC\"\n" +
                      "    }\n" +
                      "}";

        Assert.assertTrue(fixture.extractJsonValue(json, "shipping_address[0].city", "dummy").isSuccess());
        Assert.assertEquals("Washington", context.getStringData("dummy"));

        Assert.assertTrue(fixture.extractJsonValue(json,
                                                   "[REGEX:shipping_address|billing_address].city => distinct ascending",
                                                   "dummy")
                                 .isSuccess());
        Assert.assertEquals("[\"Glenoak\",\"Washington\"]", context.getStringData("dummy"));

        context.setData(TREAT_JSON_AS_IS, "false");
        Assert.assertTrue(fixture.extractJsonValue(json,
                                                   "[REGEX:shipping_address|billing_address].city => distinct ascending",
                                                   "dummy")
                                 .isSuccess());
        Assert.assertEquals("[Glenoak, Washington]", context.getStringData("dummy"));

    }

    @Test
    public void compact() throws Exception {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{\n" +
                      "    \"billing_address\": {\n" +
                      "        \"street_address\": \"1st Street SE\",\n" +
                      "        \"city\": \"Washington\",\n" +
                      "        \"state\": \"DC\"\n" +
                      "    },\n" +
                      "     \"glconfig\": []," +
                      "     \"account\": { \"id\": \"12345\", \"name\": \"\" }" +
                      "}";

        Assert.assertTrue(fixture.compact("var", json, "true").isSuccess());
        String compacted = context.getStringData("var");
        System.out.println(compacted);
        Assert.assertFalse(compacted.contains("glconfig"));
        Assert.assertFalse(compacted.contains("name"));

        json = "{\n" +
               "  \"config\": {\n" +
               "    \"mainOptions\": [\n" +
               "      {\n" +
               "        \"regionCode\": \"CA\",\n" +
               "        \"state\": \"California\"\n" +
               "      }\n" +
               "    ],\n" +
               "    \"mainLocation\": {\n" +
               "    },\n" +
               "    \"shootLocations\": [\n" +
               "      {\n" +
               "        \"regionCode\": \"CA\",\n" +
               "        \"state\": \"California\"\n" +
               "      }\n" +
               "    ],\n" +
               "    \"payrollConfig\": {\n" +
               "      \"clientId\": 12345,\n" +
               "      \"isNonUnion\": true\n" +
               "    },\n" +
               "    \"glconfig\": [],\n" +
               "    \"dataListing\": [\"\", \"\", null, \"\", { } ],\n" +
               "    \"productionInfo\": {\n" +
               "      \"productionCompanyName\": \"Entertainment Productions\",\n" +
               "      \"productionTitle\": \"Growing Painelous\",\n" +
               "      \"productionAddress1\": \"123B Elms Blvd.\",\n" +
               "      \"productionAddress2\": \"\",\n" +
               "      \"productionCity\": null,\n" +
               "      \"productionState\": \"CA\",\n" +
               "      \"productionZipCode\": \"90010\",\n" +
               "      \"productionPhoneNumber\": \"323-939-5555\",\n" +
               "      \"productionEIN\": \"45-4098722\",\n" +
               "      \"array1\": [ " +
               "            { " +
               "                \"inner1\": { }, " +
               "                \"inner2\": [ " +
               "                    null," +
               "                    null, " +
               "                    { " +
               "                        \"inner3\": null, " +
               "                        \"inner4\": { " +
               "                            \"inner5\":\"\" " +
               "                        } " +
               "                    } " +
               "                ] " +
               "            } " +
               "       ]" +
               "    }\n" +
               "  }\n" +
               "}";
        Assert.assertTrue(fixture.compact("var", json, "true").isSuccess());
        compacted = context.getStringData("var");
        System.out.println(compacted);
        Assert.assertFalse(compacted.contains("glconfig"));
        Assert.assertFalse(compacted.contains("mainLocation"));
        Assert.assertFalse(compacted.contains("dataListing"));
        Assert.assertFalse(compacted.contains("productionAddress2"));
        Assert.assertFalse(compacted.contains("productionCity"));
        Assert.assertFalse(compacted.contains("array1"));

    }

    @Test
    public void compact2() throws Exception {
        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        String json = "{\n" +
                      "  \"config\": {\n" +
                      "    \"location1\": [\n" +
                      "      { \"code\": \"CA\", \"state\": \"California\" }\n" +
                      "    ],\n" +
                      "    \"location2\": { },\n" +
                      "    \"config1\": {\n" +
                      "      \"client\": 12345,\n" +
                      "      \"active\": true\n" +
                      "    },\n" +
                      "    \"config\": [],\n" +
                      "    \"dataListing\": [\"\", \"\", null, \"\", { } ]\n" +
                      "  }\n" +
                      "}";

        Assert.assertTrue(fixture.compact("var", json, "false").isSuccess());
        String compacted = context.getStringData("var");
        System.out.println("JSONCommand.compact(var,false):");
        System.out.println(compacted);
        System.out.println();
        System.out.println();
        Assert.assertEquals("{\"config\":{\"location1\":[{\"code\":\"CA\",\"state\":\"California\"}]," +
                            "\"config1\":{\"client\":12345,\"active\":true},\"dataListing\":[\"\",\"\",\"\"]}}",
                            compacted);

        Assert.assertTrue(fixture.compact("var", json, "true").isSuccess());
        compacted = context.getStringData("var");
        System.out.println("JSONCommand.compact(var,true):");
        System.out.println(compacted);
        Assert.assertEquals("{\"config\":{\"location1\":[{\"code\":\"CA\",\"state\":\"California\"}]," +
                            "\"config1\":{\"client\":12345,\"active\":true}}}",
                            compacted);
    }

    @Test
    public void assertEqual_different_order() throws Exception {
        String testJson1 = "[ { \"firstName\": \"John Mark\", \"id\": 12345, \"lastName\": \"Magillon\" }, {} ]";
        String testJson2 = "[ { \"id\": 12345, \"lastName\": \"Magillon\", \"firstName\": \"John Mark\" }, {  } ]";

        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        StepResult result = fixture.assertEqual(testJson1, testJson2);
        Assert.assertNotNull(result);

        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void assertEqual_different_order_2() throws Exception {
        String testJson1 = "[ {}, { \"firstName\": \"John Mark\", \"id\": 12345, \"lastName\": \"Magillon\" } ]";
        String testJson2 = "[ { \"id\": 12345, \"lastName\": \"Magillon\", \"firstName\": \"John Mark\" }, {  } ]";

        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        StepResult result = fixture.assertEqual(testJson1, testJson2);
        Assert.assertNotNull(result);

        System.out.println("result = " + result);
        Assert.assertFalse(result.isSuccess());

        String compareResult = context.getStringData(LAST_JSON_COMPARE_RESULT);
        Assert.assertTrue(compareResult.contains("\"expected\": \"0 element\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"3 elements (firstName, id, lastName)\""));
        Assert.assertTrue(compareResult.contains("\"expected\": \"3 elements (firstName, id, lastName)\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"0 element\""));
    }

    @Test
    public void assertEqual_uneven_nodes() throws Exception {
        String testJson1 = "[ { \"firstName\": \"John Mark\", \"id\": 12345, \"lastName\": \"Magillon\" }, {} ]";
        String testJson2 = "[ { \"id\": 54321, \"lastName\": \"Killian\", \"middleName\": \"Mark\", " +
                           "\"firstName\": \"John\" }, {  } ]";

        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        StepResult result = fixture.assertEqual(testJson1, testJson2);
        Assert.assertNotNull(result);

        System.out.println("result = " + result);
        Assert.assertFalse(result.isSuccess());

        String compareResult = context.getStringData(LAST_JSON_COMPARE_RESULT);
        Assert.assertTrue(compareResult.contains("\"expected\": \"3 elements (firstName, id, lastName)\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"4 elements (firstName, id, lastName, middleName)\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"value \\\"John Mark\\\" of type text\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"John\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"value 12345 of type number\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value 54321 of type number\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"value \\\"Magillon\\\" of type text\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"Killian\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"NOT FOUND\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"Mark\\\" of type text\""));
    }

    @Test
    public void assertEqual_out_of_order() throws Exception {
        String testJson1 = "[" +
                           "  {" +
                           "    \"id\": \"1491302\"," +
                           "    \"employeeid\": \"9268gIgEI\"," +
                           "    \"employeetype\": \"REG\"," +
                           "    \"employeetypedescription\": \"PWE Employee\"," +
                           "    \"firstName\": \"MICHAEL\"," +
                           "    \"middleName\": \"\"," +
                           "    \"lastname\": \"FRXXX\"," +
                           "    \"corpName\": \"\"," +
                           "    \"corpFEIN\": \"\"," +
                           "    \"address1\": \"C/O EMA TELSTAR PRODUCTIONS AB\"," +
                           "    \"address2\": \"BOX 1018 CARL MILLES VAG 7\"," +
                           "    \"city\": \"S-18121 LIDINGO\"," +
                           "    \"state\": \"FO\"," +
                           "    \"postalcode\": \"000000000\"," +
                           "    \"country\": \"UNK\"" +
                           "  }" +
                           "]";
        String testJson2 = "[" +
                           "  {" +
                           "    \"lastName\": \"HIRST\"," +
                           "    \"country\": \"USA\"," +
                           "    \"address2\": \"\"," +
                           "    \"city\": \"BEVERLY HILLS\"," +
                           "    \"address1\": \"% ENDEAVOR              9601 WILSHIRE BL 3RD FL   \"," +
                           "    \"postalCode\": \"90210\"," +
                           "    \"employeeId\": \"+876Hy!5s\"," +
                           "    \"corpName\": \"\"," +
                           "    \"firstName\": \"MICHAEL\"," +
                           "    \"employeeType\": \"REG\"," +
                           "    \"pweTypeDescription\": \"PWE Employee \"," +
                           "    \"corpFEIN\": \"\"," +
                           "    \"middleName\": \"\"," +
                           "    \"id\": 2740263," +
                           "    \"state\": \"CA\"" +
                           "  }" +
                           "]";

        context.setData("nexial.json.compareResultsAsJSON", true);
        context.setData("nexial.json.compareResultsAsCSV", true);
        context.setData("nexial.json.compareResultsAsHTML", true);

        JsonCommand fixture = new JsonCommand();
        fixture.init(context);

        StepResult result = fixture.assertEqual(testJson1, testJson2);
        Assert.assertNotNull(result);

        System.out.println("result = " + result);
        Assert.assertFalse(result.isSuccess());

        String compareResult = context.getStringData(LAST_JSON_COMPARE_RESULT);
        Assert.assertTrue(
            compareResult.contains("\"expected\": \"value \\\"C/O EMA TELSTAR PRODUCTIONS AB\\\" of type text\""));
        Assert.assertTrue(
            compareResult.contains(
                "\"actual\": \"value \\\"% ENDEAVOR              9601 WILSHIRE BL 3RD FL   \\\" of type text\""));

        Assert.assertTrue(compareResult
                              .contains("\"expected\": \"value \\\"BOX 1018 CARL MILLES VAG 7\\\" of type text\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"value \\\"S-18121 LIDINGO\\\" of type text\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"BEVERLY HILLS\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"value \\\"UNK\\\" of type text\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"USA\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"value \\\"9268gIgEI\\\" of type text\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"NOT FOUND\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"value \\\"REG\\\" of type text\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"NOT FOUND\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"value \\\"PWE Employee\\\" of type text\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"NOT FOUND\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"NOT FOUND\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"+876Hy!5s\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"NOT FOUND\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"REG\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"NOT FOUND\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"HIRST\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"NOT FOUND\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"90210\\\" of type text\""));

        Assert.assertTrue(compareResult.contains("\"expected\": \"NOT FOUND\""));
        Assert.assertTrue(compareResult.contains("\"actual\": \"value \\\"PWE Employee \\\" of type text\""));
    }

}
