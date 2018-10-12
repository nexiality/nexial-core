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
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;

import com.google.gson.JsonArray;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.GSON_COMPRESSED;

/**
 *
 */
public class JsonCommandTest {
    private ExecutionContext context = new MockExecutionContext();
    private String destinationBase;

    @Before
    public void init() {
        if (context == null) { context = new MockExecutionContext(); }
        destinationBase = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator + "JsonCommandTest" + separator;
    }

    @After
    public void tearDown() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
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

        Assert.assertTrue(fixture.assertValue(json, "response.jobParameters", null).isSuccess());

        Assert.assertTrue(fixture.assertValues(json, "response.jobParameters", "[]", "false").isSuccess());
        Assert.assertTrue(fixture.assertValues(json, "response.jobParameters", "(null)", "false").isSuccess());
        Assert.assertTrue(fixture.assertValues(json, "response.jobParameters", "", "false").isSuccess());
        Assert.assertTrue(fixture.assertValues(json, "response.jobParameters", null, "false").isSuccess());
        Assert.assertTrue(fixture.assertValues(json,
                                               "response.jobParameters",
                                               context.replaceTokens("[TEXT(${jobParameters}) => removeRegex(\\[\\])]"),
                                               "false")
                                 .isSuccess());

        Assert.assertTrue(fixture.assertElementCount(json, "response.jobParameters", "0").isSuccess());
        Assert.assertTrue(fixture.assertElementCount(json, "response.jobParameters[]", "0").isSuccess());
    }

}
