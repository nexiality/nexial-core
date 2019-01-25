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

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.ResourceUtils;

/**
 *
 */
public class JSONPathTest {

    @Before
    public void setUp() { }

    @After
    public void tearDown() { }

    @Test
    public void testParsing() {
        JSONObject fixture1 = new JSONObject("{ " +
                                             " \"a\": { \"b\": \"1234\" }," +
                                             " \"c\": 5," +
                                             " \"d\": \"hello\"," +
                                             " \"e\": null," +
                                             " \"f\": []," +
                                             " \"g\": [ \"cat\", \"dog\", \"mouse\" ]," +
                                             " \"h\": { \"i.j\": \"yoyoma\" }, " +
                                             " \"k\": { \"l\": { \"m\": \"n\" } }, " +
                                             " \"o\": { \"p\": \"q\" }, " +
                                             " \"r\": [ " +
                                             "      { \"s\": [ \"t\", \"u\" ] }, " +
                                             "      { \"v.w\": [ \"x\", \"yx\", 1234 ] }, " +
                                             " ]," +
                                             " \"items\": [ " +
                                             "      { \"name\": \"SIT\", \"id\": \"5\" }, " +
                                             "      { \"name\": \"SUI\", \"id\": \"6\" }, " +
                                             "      { \"name\": \"SDI\", \"id\": \"D\" } " +
                                             " ] " +
                                             "}");

        testPathValue(fixture1, "c", "5");
        //testPathValue(fixture1, , );
        testPathValue(fixture1, "a.b", "1234");
        testPathValue(fixture1, "d", "hello");
        testPathValue(fixture1, "e", null);
        testPathValue(fixture1, "f", null);
        testPathValue(fixture1, "g", "[\"cat\",\"dog\",\"mouse\"]");
        testPathValue(fixture1, "g[2]", "mouse");
        testPathValue(fixture1, "g[0]", "cat");
        testPathValue(fixture1, "g[3]", null);
        testPathValue(fixture1, "g[dog]", "dog");
        testPathValue(fixture1, "g[chicken]", null);
        testPathValue(fixture1, "h", "{\"i.j\":\"yoyoma\"}");
        testPathValue(fixture1, "h[i.j]", "yoyoma");
        testPathValue(fixture1, "h[1]", null);
        testPathValue(fixture1, "h[i]", null);
        testPathValue(fixture1, "h[i.]", null);
        testPathValue(fixture1, "k", "{\"l\":{\"m\":\"n\"}}");
        testPathValue(fixture1, "k.l", "{\"m\":\"n\"}");
        testPathValue(fixture1, "k[l]", "{\"m\":\"n\"}");
        testPathValue(fixture1, "k['l']", "{\"m\":\"n\"}");
        testPathValue(fixture1, "k[\"l\"]", "{\"m\":\"n\"}");
        testPathValue(fixture1, "k.l.m", "n");
        testPathValue(fixture1, "k[l.m]", null);
        testPathValue(fixture1, "o.p", "q");
        testPathValue(fixture1, "o[p]", "q");

        // { "r":[
        //      { "s":   [ "t", "u" ] },
        //      { "v.w": [ "x", "yx", 1234 ] }
        // ] }
        testPathValue(fixture1, "r", "[{\"s\":[\"t\",\"u\"]},{\"v.w\":[\"x\",\"yx\",1234]}]");
        testPathValue(fixture1, "r.s", "[\"t\",\"u\"]");
        testPathValue(fixture1, "r.s.t", "t");
        testPathValue(fixture1, "r[s][0]", "t");
        testPathValue(fixture1, "r[s][1]", "u");
        testPathValue(fixture1, "r[s][t]", "t");
        testPathValue(fixture1, "r[v.w]", "[\"x\",\"yx\",1234]");
        testPathValue(fixture1, "r[v.w][2]", "1234");
        testPathValue(fixture1, "r[v.w].yx", "yx");

        //"items":      [
        //	{ "name": "SIT", "id":   "5" },
        //	{ "name": "SUI", "id":   "6" },
        //	{ "name": "SDI", "id":   "D" }
        //]
        testPathValue(fixture1,
                      "items",
                      "[{\"name\":\"SIT\",\"id\":\"5\"},{\"name\":\"SUI\",\"id\":\"6\"},{\"name\":\"SDI\",\"id\":\"D\"}]");
        testPathValue(fixture1, "items[id=6]", "{\"name\":\"SUI\",\"id\":\"6\"}");
        testPathValue(fixture1, "items[id=6].name", "SUI");
    }

    @Test
    public void testParseArray() {
        JSONObject fixture1 = new JSONObject("{ " +
                                             " \"items\": [ " +
                                             "      { \"name\": \"SIT\", \"id\": \"5\" }, " +
                                             "      { \"name\": \"SUI\", \"id\": \"6\" }, " +
                                             "      { \"name\": \"SDI\", \"id\": \"D\" } " +
                                             " ] " +
                                             "}");

        testPathValue(fixture1, "items.name", "[\"SIT\",\"SUI\",\"SDI\"]");
    }

    @Test
    public void testArray() {
        JSONObject fixture1 =
            new JSONObject("{\"response\":[" +
                           "{\"erFein\":\"391127174\",\"updateResult\":\"Update Succesfull\"}," +
                           "{\"erFein\":\"363871028\",\"updateResult\":\"Update Succesfull\"}" +
                           "]}");

        testPathValue(fixture1, "response[0].erFein", "391127174");
        testPathValue(fixture1, "response[1].erFein", "363871028");
    }

    @Test
    public void testArray2() throws Exception {
        JSONArray fixture = new JSONArray(ResourceUtils.loadResource("org/nexial/core/utils/JSONPathTest1.json"));

        testPathValue(fixture,
                      "tag_name",
                      "[\"v0.21.0\",\"v0.20.1\",\"v0.20.0\",\"v0.19.1\",\"v0.19.0\",\"v0.18.0\",\"v0.17.0\"," +
                      "\"v0.16.1\",\"v0.16.0\",\"v0.15.0\",\"v0.14.0\",\"v0.13.0\",\"v0.12.0\",\"v0.11.1\"," +
                      "\"v0.11.0\",\"v0.10.0\",\"v0.9.0\",\"v0.8.0\",\"v0.7.1\",\"v0.6.2\",\"v0.6.0\",\"v0.5.0\"," +
                      "\"v0.4.2\",\"v0.4.1\",\"v0.4.0\",\"0.3.0\",\"v0.2.0\",\"v0.1.0\"]");
        testPathValue(fixture,
                      "[tag_name=v0.21.0].assets[name=REGEX:.+win64.+].browser_download_url",
                      "https://github.com/mozilla/geckodriver/releases/download/v0.21.0/geckodriver-v0.21.0-win64.zip");
    }

    @Test
    public void testPrependString() {
        String fixture = "{ " +
                         " \"a\": { \"b\": \"1234\" }," +
                         " \"c\": 5," +
                         " \"d\": \"hello\"," +
                         " \"e\": null," +
                         " \"f\": []," +
                         " \"g\": [ \"cat\", \"dog\", \"mouse\" ]," +
                         " \"h\": { \"i.j\": \"yoyoma\" }, " +
                         " \"k\": { \"l\": { \"m\": \"n\" } }, " +
                         " \"o\": { \"p\": \"q\" }, " +
                         " \"r\": [ " +
                         "      { \"s\": [ \"t\", \"u\" ] }, " +
                         "      { \"v.w\": [ \"x\", \"yx\", 1234 ] }, " +
                         " ] " +
                         "}";

        testPrepend(new JSONObject(fixture), "a.b", "432", "4321234");
        testPrepend(new JSONObject(fixture), "d", "yo yo ", "yo yo hello");
        testPrepend(new JSONObject(fixture), "g[2]", "BIG ", "BIG mouse");
        testPrepend(new JSONObject(fixture), "h[i.j]", "Yo yo ", "Yo yo yoyoma");
        testPrepend(new JSONObject(fixture), "k.l.m", "Natio", "Nation");
        testPrepend(new JSONObject(fixture), "o.p", "op", "opq");
        testPrepend(new JSONObject(fixture), "r.s[1]", "Yo", "You");
        testPrepend(new JSONObject(fixture), "r[v.w][0]", "E", "Ex");
        //testPrepend(new JSONObject(fixture), "e", "data", "data");
    }

    @Test
    public void testAppendString() {
        String fixture = "{ " +
                         " \"a\": { \"b\": \"1234\" }," +
                         " \"c\": 5," +
                         " \"d\": \"hello\"," +
                         " \"e\": null," +
                         " \"f\": []," +
                         " \"g\": [ \"cat\", \"dog\", \"mouse\" ]," +
                         " \"h\": { \"i.j\": \"yoyoma\" }, " +
                         " \"k\": { \"l\": { \"m\": \"n\" } }, " +
                         " \"o\": { \"p\": \"q\" }, " +
                         " \"r\": [ " +
                         "      { \"s\": [ \"t\", \"u\" ] }, " +
                         "      { \"v.w\": [ \"x\", \"yx\", 1234 ] }, " +
                         " ] " +
                         "}";

        testAppend(new JSONObject(fixture), "a.b", "432", "1234432");
        testAppend(new JSONObject(fixture), "d", "yo yo ", "helloyo yo ");
        testAppend(new JSONObject(fixture), "g[2]", "BIG ", "mouseBIG ");
        testAppend(new JSONObject(fixture), "h[i.j]", "Yo yo ", "yoyomaYo yo ");
        testAppend(new JSONObject(fixture), "k.l.m", "Natio", "nNatio");
        testAppend(new JSONObject(fixture), "o.p", "op", "qop");
        testAppend(new JSONObject(fixture), "r.s[1]", "Yo", "uYo");
        testAppend(new JSONObject(fixture), "r[v.w][0]", "E", "xE");
    }

    @Test
    public void testOverwriteString() {
        String fixture = "{ " +
                         " \"a\": { \"b\": \"1234\" }," +
                         " \"c\": 5," +
                         " \"d\": \"hello\"," +
                         " \"e\": null," +
                         " \"f\": []," +
                         " \"g\": [ \"cat\", \"dog\", \"mouse\" ]," +
                         " \"h\": { \"i.j\": \"yoyoma\" }, " +
                         " \"k\": { \"l\": { \"m\": \"n\" } }, " +
                         " \"o\": { \"p\": \"q\" }, " +
                         " \"r\": [ " +
                         "      { \"s\": [ \"t\", \"u\" ] }, " +
                         "      { \"v.w\": [ \"x\", \"yx\", 1234 ] }, " +
                         " ] " +
                         "}";

        testOverwrite(new JSONObject(fixture), "a.b", "432", "432");
        testOverwrite(new JSONObject(fixture), "d", "yo yo ", "yo yo ");
        testOverwrite(new JSONObject(fixture), "g[2]", "BIG ", "BIG ");
        testOverwrite(new JSONObject(fixture), "h[i.j]", "Yo yo ", "Yo yo ");
        testOverwrite(new JSONObject(fixture), "k.l.m", "Natio", "Natio");
        testOverwrite(new JSONObject(fixture), "o.p", "op", "op");
        testOverwrite(new JSONObject(fixture), "r.s[1]", "Yo", "Yo");
        testOverwrite(new JSONObject(fixture), "r[v.w][0]", "E", "E");
    }

    @Test
    public void testDeleteString() {
        String fixture = "{ " +
                         " \"a\": { \"b\": \"1234\" }," +
                         " \"c\": 5," +
                         " \"d\": \"hello\"," +
                         " \"e\": null," +
                         " \"f\": []," +
                         " \"g\": [ \"cat\", \"dog\", \"mouse\" ]," +
                         " \"h\": { \"i.j\": \"yoyoma\" }, " +
                         " \"k\": { \"l\": { \"m\": \"n\" } }, " +
                         " \"o\": { \"p\": \"q\" }, " +
                         " \"r\": [ " +
                         "      { \"s\": [ \"t\", \"u\" ] }, " +
                         "      { \"v.w\": [ \"x\", \"yx\", 1234 ] }, " +
                         " ] " +
                         "}";

        JSONObject json = JSONPath.delete(new JSONObject(fixture), "c");
        Assert.assertTrue(json.opt("c") == null);

        json = JSONPath.delete(new JSONObject(fixture), "a");
        Assert.assertTrue(json.opt("a") == null);

        json = JSONPath.delete(new JSONObject(fixture), "a.b");
        Assert.assertTrue(json.optJSONObject("a").toString().equals("{}"));

        json = JSONPath.delete(new JSONObject(fixture), "d");
        Assert.assertTrue(json.opt("d") == null);

        json = JSONPath.delete(new JSONObject(fixture), "f");
        Assert.assertTrue(json.opt("f") == null);

        json = JSONPath.delete(new JSONObject(fixture), "g");
        Assert.assertTrue(json.opt("g") == null);

        json = JSONPath.delete(new JSONObject(fixture), "g.cat");
        Assert.assertEquals("[\"dog\",\"mouse\"]", json.optJSONArray("g").toString());

        json = JSONPath.delete(new JSONObject(fixture), "k");
        Assert.assertTrue(json.opt("k") == null);

        json = JSONPath.delete(new JSONObject(fixture), "k.l");
        Assert.assertTrue(json.optJSONObject("k").toString().equals("{}"));

        json = JSONPath.delete(new JSONObject(fixture), "k.l.m");
        Assert.assertTrue(json.optJSONObject("k").optJSONObject("l").opt("m") == null);

        json = JSONPath.delete(new JSONObject(fixture), "o.p.q");
        Assert.assertTrue(json.optJSONObject("o").opt("p").toString().equals("q"));

        json = JSONPath.delete(new JSONObject(fixture), "r.s.t");
        Assert.assertEquals("[{\"s\":[\"u\"]},{\"v.w\":[\"x\",\"yx\",1234]}]", json.optJSONArray("r").toString());

        json = JSONPath.delete(new JSONObject(fixture), "r[v.w].1234");
        Assert.assertEquals("[{\"s\":[\"t\",\"u\"]},{\"v.w\":[\"x\",\"yx\"]}]", json.optJSONArray("r").toString());
    }

    @Test
    public void testDelete2() {
        String fixture = "{" +
                         " \"id\":       \"Employee Management\"," +
                         " \"group\": [" +
                         "    {" +
                         "      \"label\":       \"Hire Date:\"," +
                         "      \"label-id\":    \"hiredatelabel\"," +
                         "      \"object-id\":   \"hiredate\"," +
                         "      \"object-type\": \"date\"" +
                         "    }," +
                         "    {" +
                         "      \"label\":    \"Timecard:\"," +
                         "      \"group\": [" +
                         "        {" +
                         "          \"label\":       \"|<\"," +
                         "          \"object-id\":   \"timecard_first\"," +
                         "          \"object-type\": \"button\"" +
                         "        }," +
                         "        {" +
                         "          \"label\":       \"<<\"," +
                         "          \"object-id\":   \"timecard_previous\"," +
                         "          \"object-type\": \"button\"" +
                         "        }," +
                         "        {" +
                         "          \"label\":       \"Record\"," +
                         "          \"object-id\":   \"timecard_record_id\"," +
                         "          \"object-type\": \"textbox\"" +
                         "        }" +
                         "      ]" +
                         "    }," +
                         "    {" +
                         "      \"label\":    \"Employee:\"," +
                         "      \"group\": [" +
                         "        {" +
                         "          \"label\":       \"|<\"," +
                         "          \"object-id\":   \"employee_first\"," +
                         "          \"object-type\": \"button\"" +
                         "        }," +
                         "        {" +
                         "          \"label\":       \"<<\"," +
                         "          \"object-id\":   \"employee_previous\"," +
                         "          \"object-type\": \"button\"" +
                         "        }," +
                         "        {" +
                         "          \"label\":       \"Record\"," +
                         "          \"object-id\":   \"employee_record_id\"," +
                         "          \"object-type\": \"textbox\"" +
                         "        }" +
                         "      ]" +
                         "    }," +
                         "    {" +
                         "      \"label\":       \"Weekly Time Sheet\"," +
                         "      \"alias\":       [ \"timesheet\" ]," +
                         "      \"object-id\":   \"timesheet1\"," +
                         "      \"object-type\": \"grid\"" +
                         "    }" +
                         " ]" +
                         "}";

        // delete single match
        JSONObject result = JSONPath.delete(new JSONObject(fixture), "group");
        Assert.assertNotNull(result);
        Assert.assertEquals("Employee Management", JSONPath.find(result, "id"));
        Assert.assertNull(JSONPath.find(result, "group"));
        Assert.assertNull(JSONPath.find(result, "group[0]"));
        Assert.assertNull(JSONPath.find(result, "group.label"));

        // delete single matches
        JSONObject json = new JSONObject(fixture);
        String innerGroupLabel = JSONPath.find(json, "group.group[0].label");
        Assert.assertNotNull(innerGroupLabel);

        result = JSONPath.delete(json, "group.group[0].label");
        Assert.assertNotNull(result);
        Assert.assertEquals("Employee Management", JSONPath.find(result, "id"));
        Assert.assertNotNull(JSONPath.find(result, "group"));
        Assert.assertNotNull(JSONPath.find(result, "group[0]"));
        Assert.assertNotNull(JSONPath.find(result, "group.label"));

        innerGroupLabel = JSONPath.find(result, "group.group[0].label");
        Assert.assertNull(innerGroupLabel);

        // delete multiple matches
        json = new JSONObject(fixture);
        String label = JSONPath.find(json, "group.label");
        Assert.assertNotNull(label);

        result = JSONPath.delete(json, "group.label");
        Assert.assertNotNull(result);
        Assert.assertEquals("Employee Management", JSONPath.find(result, "id"));
        Assert.assertNotNull(JSONPath.find(result, "group"));
        Assert.assertNotNull(JSONPath.find(result, "group[0]"));
        Assert.assertNull(JSONPath.find(result, "group.label"));

        label = JSONPath.find(result, "group.label");
        Assert.assertNull(label);

    }

    @Test
    public void testRegexMatch() {
        JSONObject fixture = new JSONObject("{" +
                                            "    \"name\": \"John Smith\"," +
                                            "    \"work_history\": [" +
                                            "        { \"date\": \"2016-12-19\", \"location\": \"California\", \"hours\": 7.5 }," +
                                            "        { \"date\": \"2016-12-20\", \"location\": \"Washington\", \"hours\": 5.9 }," +
                                            "        { \"date\": \"2016-12-21\", \"location\": \"Florida\",    \"hours\": 8.0 }," +
                                            "        { \"date\": \"2016-12-23\", \"location\": \"Florida\",    \"hours\": 8.3 }," +
                                            "        { \"date\": \"2016-12-26\", \"location\": \"Florida\",    \"hours\": 8.1 }," +
                                            "        { \"date\": \"2016-12-27\", \"location\": \"California\", \"hours\": 7.0 }," +
                                            "        { \"date\": \"2016-12-28\", \"location\": \"California\", \"hours\": 7.0 }," +
                                            "        { \"date\": \"2016-12-29\", \"location\": \"Arizona\",    \"hours\": 6.5 }," +
                                            "        { \"date\": \"2016-12-30\", \"location\": \"Arizona\",    \"hours\": 6.5 }," +
                                            "        { \"date\": \"2017-01-02\", \"location\": \"Arizona\",    \"hours\": 6.5 }," +
                                            "        { \"date\": \"2017-01-03\", \"location\": \"Arizona\",    \"hours\": 6.5 }," +
                                            "        { \"date\": \"2017-01-04\", \"location\": \"Naveda\",     \"hours\": 8.0 }," +
                                            "        { \"date\": \"2017-01-05\", \"location\": \"Naveda\",     \"hours\": 8.1 }" +
                                            "    ]" +
                                            "}");

        testPathValue(fixture,
                      "work_history[date=REGEX:2016-\\.+].location",
                      "[\"California\",\"Washington\",\"Florida\",\"Florida\",\"Florida\",\"California\",\"California\",\"Arizona\",\"Arizona\"]");
        testPathValue(fixture,
                      "work_history[location=REGEX:\\.+o\\.*n\\.*a\\.*].hours",
                      "[7.5,7.0,7.0,6.5,6.5,6.5,6.5]");
        testPathValue(fixture,
                      "work_history[hours=REGEX:8\\.+].date",
                      "[\"2016-12-21\",\"2016-12-23\",\"2016-12-26\",\"2017-01-04\",\"2017-01-05\"]");
    }

    @Test
    public void testRegexMatch2() {
        JSONObject fixture =
            new JSONObject("{" +
                           "    \"name\": \"John Smith\"," +
                           "    \"work_history\": [" +
                           "        { \"date\": \"2016-12-19\", \"location\": \"California\",     \"hours\": 7.5 }," +
                           "        { \"date\": \"2016-12-20\", \"location\": \"Washington\",     \"hours\": 5.9 }," +
                           "        { \"date\": \"2016-12-21\", \"location\": \"Florida\",        \"hours\": 8.0 }," +
                           "        { \"date\": \"2016-12-22\", \"Location\": \"North Carolina\", \"hours\": 4.1 }," +
                           "        { \"date\": \"2016-12-23\", \"location\": \"Florida\",        \"hours\": 8.3 }," +
                           "        { \"date\": \"2016-12-26\", \"location\": \"Florida\",        \"hours\": 8.1 }," +
                           "        { \"date\": \"2016-12-27\", \"location\": \"California\",     \"hours\": 7.0 }," +
                           "        { \"date\": \"2016-12-28\", \"location\": \"California\",     \"hours\": 7.0 }," +
                           "        { \"date\": \"2016-12-29\", \"location\": \"Arizona\",        \"hours\": 6.5 }," +
                           "        { \"date\": \"2016-12-30\", \"location\": \"Arizona\",        \"hours\": 6.5 }," +
                           "        { \"date\": \"2017-01-02\", \"location\": \"Arizona\",        \"hours\": 6.5 }," +
                           "        { \"date\": \"2017-01-03\", \"location\": \"Arizona\",        \"hours\": 6.5 }," +
                           "        { \"date\": \"2017-01-04\", \"location\": \"Naveda\",         \"hours\": 8.0 }," +
                           "        { \"date\": \"2017-01-05\", \"location\": \"Naveda\",         \"hours\": 8.1 }," +
                           "        { \"date\": \"2017-01-06\", \"location\": \"New York\",       \"hours\": 8.1 }," +
                           "        { \"date\": \"2017-01-10\", \"location\": \"South Wales\",    \"Hours\": 8.1 }" +
                           "    ]" +
                           "}");

        // list where I worked in 2016 where the state ends in '...a'
        testPathValue(fixture,
                      "work_history[date=REGEX:2016-\\.+ AND location=REGEX:\\.+a].location",
                      "[\"California\",\"Florida\",\"Florida\",\"Florida\",\"California\",\"California\",\"Arizona\",\"Arizona\"]");

        // list the hours that were either 8 or more hours in a state that ends in '...a'
        testPathValue(fixture,
                      "work_history[location=REGEX:\\.*a].hours[REGEX:(8|9)\\.*]", "[8.0,8.3,8.1,8.0,8.1]");

        // list the hours that were worked in 2016 and in states that ends with '...a'
        testPathValue(fixture,
                      "work_history[REGEX:(L|l)ocation=REGEX:\\.+a AND date=REGEX:2016-\\.+].hours",
                      "[7.5,8.0,4.1,8.3,8.1,7.0,7.0,6.5,6.5]");

        // list the hours that were worked in 2017 and in New Year
        testPathValue(fixture, "work_history[location=New York].hours", "8.1");
        testPathValue(fixture, "work_history[REGEX:(L|l)ocation=New York AND date=REGEX:2017-\\.+].hours", "8.1");

        // list all the dates worked in Nevada
        testPathValue(fixture, "work_history[location=Naveda].date", "[\"2017-01-04\",\"2017-01-05\"]");

        // list all the hours or Hours worked in South Wales
        testPathValue(fixture, "work_history[location=South Wales][REGEX:(H|h)ours]", "8.1");
        testPathValue(fixture, "work_history[location=California].hours[0]", "7.5");
        testPathValue(fixture, "work_history[location=California].hours[REGEX:(6|7|8|9)\\.*]", "[7.5,7.0,7.0]");
        testPathValue(fixture, "work_history[location=California].hours[REGEX:(8|9)\\.*]", null);
    }

    @Test
    public void testJsonArray() {
        JSONArray fixture =
            new JSONArray("[" +
                          "     { \"date\": \"2016-12-19\", \"location\": \"California\", \"hours\": 7.5 }," +
                          "     { \"date\": \"2016-12-20\", \"location\": \"Washington\", \"hours\": 5.9 }," +
                          "     { \"date\": \"2016-12-21\", \"location\": \"Florida\",    \"hours\": 8.0 }," +
                          "     { \"date\": \"2016-12-23\", \"location\": \"Florida\",    \"hours\": 8.3 }," +
                          "     { \"date\": \"2016-12-26\", \"location\": \"Florida\",    \"hours\": 8.1 }," +
                          "     { \"date\": \"2016-12-27\", \"location\": \"California\", \"hours\": 7.0 }," +
                          "     { \"date\": \"2016-12-28\", \"location\": \"California\", \"hours\": 7.0 }," +
                          "     { \"date\": \"2016-12-29\", \"location\": \"Arizona\",    \"hours\": 6.5 }," +
                          "     { \"date\": \"2016-12-30\", \"location\": \"Arizona\",    \"hours\": 6.5 }," +
                          "     { \"date\": \"2017-01-02\", \"location\": \"Arizona\",    \"hours\": 6.5 }," +
                          "     { \"date\": \"2017-01-03\", \"location\": \"Arizona\",    \"hours\": 6.5 }," +
                          "     { \"date\": \"2017-01-04\", \"location\": \"Naveda\",     \"hours\": 8.0 }," +
                          "     { \"date\": \"2017-01-05\", \"location\": \"Naveda\",     \"hours\": 8.1 }" +
                          "]");

        testPathValue(fixture,
                      "[date=REGEX:2016-\\.+].location",
                      "[\"California\",\"Washington\",\"Florida\",\"Florida\",\"Florida\",\"California\",\"California\",\"Arizona\",\"Arizona\"]");
        testPathValue(fixture, "[location=REGEX:\\.+o\\.*n\\.*a\\.*].hours", "[7.5,7.0,7.0,6.5,6.5,6.5,6.5]");
        testPathValue(fixture,
                      "[hours=REGEX:8\\.+].date",
                      "[\"2016-12-21\",\"2016-12-23\",\"2016-12-26\",\"2017-01-04\",\"2017-01-05\"]");
        testPathValue(fixture, "[location=Arizona].hours", "[6.5,6.5,6.5,6.5]");
    }

    @Test
    public void testJsonArray2() {
        JSONArray fixture = new JSONArray("[ \"Apple\", \"Orange\", \"Banana\" ]");

        testPathValue(fixture, "[Apple]", "Apple");
        testPathValue(fixture, "[REGEX:\\.+a\\.+]", "[\"Orange\",\"Banana\"]");
        testPathValue(fixture, "[REGEX:\\.{6}]", "[\"Orange\",\"Banana\"]");
        testPathValue(fixture, "[REGEX:\\.+]", "[\"Apple\",\"Orange\",\"Banana\"]");
    }

    @Test
    public void testModifyJsonArray() {
        String fixture = "[" +
                         "    { \"colorName\": \"red\",    \"hexValue\": \"#f00\" }," +
                         "    { \"colorName\": \"green\",  \"hexValue\": \"#0f0\" }," +
                         "    { \"colorName\": \"blue\",   \"hexValue\": \"#00f\" }," +
                         "    { \"colorName\": \"cyan\",   \"hexValue\": \"#0ff\" }," +
                         "    { \"colorName\": \"magenta\",\"hexValue\": \"#f0f\" }," +
                         "    { \"colorName\": \"yellow\", \"hexValue\": \"#ff0\" }," +
                         "    { \"colorName\": \"black\",  \"hexValue\": \"#000\" }" +
                         "]";

        // ---------------------------------------------------------------------------------------
        // test deletes
        // ---------------------------------------------------------------------------------------
        JSONArray result = JSONPath.delete(new JSONArray(fixture), "[hexValue=REGEX:(\\.*f\\.*f\\.*)]");
        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.length());

        result = JSONPath.delete(new JSONArray(fixture), "[colorName=yellow]");
        Assert.assertNotNull(result);
        Assert.assertEquals(6, result.length());
        Assert.assertNull(JSONPath.find(result, "[colorName=yellow]"));

        // filter deletes the parent of the matching node
        result = JSONPath.delete(new JSONArray(fixture), "[hexValue]");
        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length());

        // named node deletes the matching node, not parent
        result = JSONPath.delete(new JSONArray(fixture), "hexValue");
        Assert.assertNotNull(result);
        Assert.assertEquals(7, result.length());
        Assert.assertNull(JSONPath.find(result, "hexValue"));

        // ---------------------------------------------------------------------------------------
        // test append
        // ---------------------------------------------------------------------------------------
        // wholesale change
        result = JSONPath.append(new JSONArray(fixture), "colorName", " color");
        Assert.assertNotNull(result);
        Assert.assertEquals(
            "[\"red color\",\"green color\",\"blue color\",\"cyan color\",\"magenta color\",\"yellow color\",\"black color\"]",
            JSONPath.find(result, "colorName"));

        // append the filtered
        result = JSONPath.append(new JSONArray(fixture), "[colorName=red].colorName", " color");
        Assert.assertNotNull(result);
        Assert.assertNull("old color value is gone so this 'find' should return nothing",
                          JSONPath.find(result, "[colorName=red]"));
        Assert.assertEquals("new color value should work now",
                            "{\"colorName\":\"red color\",\"hexValue\":\"#f00\"}",
                            JSONPath.find(result, "[colorName=red color]"));
        Assert.assertEquals("[\"red color\",\"green\",\"blue\",\"cyan\",\"magenta\",\"yellow\",\"black\"]",
                            JSONPath.find(result, "colorName"));

        // regex not supported outside of filter, so this should not work
        result = JSONPath.append(new JSONArray(fixture), "REGEX:(colorName|hexValue)", " color");
        Assert.assertNotNull(result);
        Assert.assertEquals(result.toString(), new JSONArray(fixture).toString());
        Assert.assertNull(JSONPath.find(result, "REGEX:(colorName|hexValue)"));

        // can't append what's not already there
        result = JSONPath.append(new JSONArray(fixture), "zippo", "toe-wear");
        Assert.assertNotNull(result);
        Assert.assertEquals(result.toString(), new JSONArray(fixture).toString());

        // ---------------------------------------------------------------------------------------
        // test prepend
        // ---------------------------------------------------------------------------------------
        // wholesale change
        result = JSONPath.prepend(new JSONArray(fixture), "colorName", "The ");
        Assert.assertNotNull(result);
        Assert.assertEquals(
            "[\"The red\",\"The green\",\"The blue\",\"The cyan\",\"The magenta\",\"The yellow\",\"The black\"]",
            JSONPath.find(result, "colorName"));

        // prepend the filtered
        result = JSONPath.prepend(new JSONArray(fixture), "[colorName=green].colorName", "The ");
        Assert.assertNotNull(result);
        Assert.assertNull("old color value is gone so this 'find' should return nothing",
                          JSONPath.find(result, "[colorName=green]"));
        Assert.assertEquals("new color value should work now",
                            "{\"colorName\":\"The green\",\"hexValue\":\"#0f0\"}",
                            JSONPath.find(result, "[colorName=The green]"));
        Assert.assertEquals("[\"red\",\"The green\",\"blue\",\"cyan\",\"magenta\",\"yellow\",\"black\"]",
                            JSONPath.find(result, "colorName"));

        // regex not supported outside of filter, so this should not work
        result = JSONPath.prepend(new JSONArray(fixture), "REGEX:(colorName|hexValue)", "The ");
        Assert.assertNotNull(result);
        Assert.assertEquals(result.toString(), new JSONArray(fixture).toString());
        Assert.assertNull(JSONPath.find(result, "REGEX:(colorName|hexValue)"));

        // can't prepend what's not already there
        result = JSONPath.prepend(new JSONArray(fixture), "huervo", "food thing");
        Assert.assertNotNull(result);
        Assert.assertEquals(result.toString(), new JSONArray(fixture).toString());

        // ---------------------------------------------------------------------------------------
        // test overwrite
        // ---------------------------------------------------------------------------------------
        // wholesale change
        result = JSONPath.overwrite(new JSONArray(fixture), "colorName", "NONE");
        Assert.assertNotNull(result);
        Assert.assertEquals("[\"NONE\",\"NONE\",\"NONE\",\"NONE\",\"NONE\",\"NONE\",\"NONE\"]",
                            JSONPath.find(result, "colorName"));

        result = JSONPath.overwrite(new JSONArray(fixture), "colorName", "");
        Assert.assertNotNull(result);
        Assert.assertEquals("[\"\",\"\",\"\",\"\",\"\",\"\",\"\"]", JSONPath.find(result, "colorName"));

        result = JSONPath.overwrite(new JSONArray(fixture), "colorName", null);
        Assert.assertNotNull(result);
        // Assert.assertEquals("[\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\"]", JSONPath.find(result, "colorName"));
        Assert.assertNull(JSONPath.find(result, "colorName"));

        // overwrite the filtered
        result = JSONPath.overwrite(new JSONArray(fixture), "[colorName=yellow].colorName", "AMARILLOS");
        Assert.assertNotNull(result);
        Assert.assertNull("old color value is gone so this 'find' should return nothing",
                          JSONPath.find(result, "[colorName=yellow]"));
        Assert.assertEquals("new color value should work now",
                            "{\"colorName\":\"AMARILLOS\",\"hexValue\":\"#ff0\"}",
                            JSONPath.find(result, "[colorName=AMARILLOS]"));
        Assert.assertEquals("[\"red\",\"green\",\"blue\",\"cyan\",\"magenta\",\"AMARILLOS\",\"black\"]",
                            JSONPath.find(result, "colorName"));

        // regex NOW supported outside of filter, so this should work
        result = JSONPath.overwrite(new JSONArray(fixture), "[REGEX:(colorName|hexValue)]", "COLOR");
        Assert.assertNotNull(result);
        Assert.assertEquals(new JSONArray("[" +
                                          " { \"colorName\": \"COLOR\", \"hexValue\": \"COLOR\" }," +
                                          " { \"colorName\": \"COLOR\", \"hexValue\": \"COLOR\" }," +
                                          " { \"colorName\": \"COLOR\", \"hexValue\": \"COLOR\" }," +
                                          " { \"colorName\": \"COLOR\", \"hexValue\": \"COLOR\" }," +
                                          " { \"colorName\": \"COLOR\", \"hexValue\": \"COLOR\" }," +
                                          " { \"colorName\": \"COLOR\", \"hexValue\": \"COLOR\" }," +
                                          " { \"colorName\": \"COLOR\", \"hexValue\": \"COLOR\" }" +
                                          "]").toString(),
                            result.toString());
        Assert.assertNull(JSONPath.find(result, "REGEX:(colorName|hexValue)"));

        // can't overwrite what's not already there
        result = JSONPath.overwrite(new JSONArray(fixture), "nationales", "place");
        Assert.assertNotNull(result);
        Assert.assertEquals(result.toString(), new JSONArray(fixture).toString());

        // ---------------------------------------------------------------------------------------
        // test overwrite or add
        // ---------------------------------------------------------------------------------------
        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "colorName", "NONE", false);
        Assert.assertNotNull(result);
        Assert.assertEquals("[\"NONE\",\"NONE\",\"NONE\",\"NONE\",\"NONE\",\"NONE\",\"NONE\"]",
                            JSONPath.find(result, "colorName"));

        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "colorName", "", false);
        Assert.assertNotNull(result);
        Assert.assertEquals("[\"\",\"\",\"\",\"\",\"\",\"\",\"\"]", JSONPath.find(result, "colorName"));

        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "colorName", null, false);
        Assert.assertNotNull(result);
        // Assert.assertEquals("[\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\"]", JSONPath.find(result, "colorName"));
        Assert.assertNull(JSONPath.find(result, "colorName"));

        // overwrite-or-add the filtered
        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "[colorName=yellow].colorName", "AMARILLOS", false);
        Assert.assertNotNull(result);
        Assert.assertNull("old color value is gone; MUST return nothing", JSONPath.find(result, "[colorName=yellow]"));
        Assert.assertEquals("new color value should work now",
                            "{\"colorName\":\"AMARILLOS\",\"hexValue\":\"#ff0\"}",
                            JSONPath.find(result, "[colorName=AMARILLOS]"));
        Assert.assertEquals("[\"red\",\"green\",\"blue\",\"cyan\",\"magenta\",\"AMARILLOS\",\"black\"]",
                            JSONPath.find(result, "colorName"));

        // bad JSONPATH means adding "bad" key as a new element
        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "REGEX:(colorName|hexValue)", "COLOR", false);
        Assert.assertNotNull(result);
        boolean found = false;
        for (int i = 0; i < result.length(); i++) {
            JSONObject elem = result.optJSONObject(i);
            if (StringUtils.equals(elem.optString("REGEX:(colorName|hexValue)"), "COLOR")) {
                found = true;
            }
        }
        Assert.assertTrue("expects a {REGEX:(colorName|hexValue): COLOR} node in the resulting array", found);

        Assert.assertEquals("COLOR", JSONPath.find(result, "REGEX:(colorName|hexValue)"));

        // can't append what's not already there,
        // but for OVERWRITE-OR-ADD, this will be added
        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "nationales", "place", false);
        Assert.assertNotNull(result);
        Assert.assertEquals("place", JSONPath.find(result, "nationales"));

        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "[colorName=blue].nationales", "baseball team", false);
        Assert.assertNotNull(result);
        Assert.assertEquals("baseball team", JSONPath.find(result, "[colorName=blue].nationales"));
    }

    @Test
    public void testModifyJsonArray2() {
        String fixture = "[ \"Johnny\", \"Sammy\", \"Tracy\", \"Alex\" ]";

        // ---------------------------------------------------------------------------------------
        // test deletes
        // ---------------------------------------------------------------------------------------
        JSONArray result = JSONPath.delete(new JSONArray(fixture), "Alex");
        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.length());

        result = JSONPath.delete(new JSONArray(fixture), "[Alex]");
        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.length());

        result = JSONPath.delete(new JSONArray(fixture), "[REGEX:\\.+\\[^y\\]]");
        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.length());
        Assert.assertNull(JSONPath.find(result, "Alex"));

        // ---------------------------------------------------------------------------------------
        // test append
        // ---------------------------------------------------------------------------------------
        // wholesale change
        result = JSONPath.append(new JSONArray(fixture), "Sammy", " Sousa");
        Assert.assertNotNull(result);
        Assert.assertEquals("Sammy Sousa", JSONPath.find(result, "[1]"));

        // append the filtered
        JSONArray array = new JSONArray(fixture);
        JSONPath.append(array, "[REGEX:\\.+\\[^y\\]]", " Cracker");
        Assert.assertEquals("[\"Johnny\",\"Sammy\",\"Tracy\",\"Alex Cracker\"]", array.toString());

        // ---------------------------------------------------------------------------------------
        // test prepend
        // ---------------------------------------------------------------------------------------
        // wholesale change
        result = JSONPath.prepend(new JSONArray(fixture), "Johnny", "Walker, ");
        Assert.assertNotNull(result);
        Assert.assertEquals("Walker, Johnny", JSONPath.find(result, "[0]"));

        // ---------------------------------------------------------------------------------------
        // test overwrite
        // ---------------------------------------------------------------------------------------
        // wholesale change
        result = JSONPath.overwrite(new JSONArray(fixture), "Tracy", "Stacy");
        Assert.assertNotNull(result);
        Assert.assertEquals("[\"Johnny\",\"Sammy\",\"Stacy\",\"Alex\"]", JSONPath.find(result, "[REGEX:\\.+]"));

        // can't overwrite what's not already there
        result = JSONPath.overwrite(new JSONArray(fixture), "Kerry", "Stacy");
        Assert.assertNotNull(result);
        Assert.assertEquals("[\"Johnny\",\"Sammy\",\"Tracy\",\"Alex\"]", JSONPath.find(result, "[REGEX:\\.+]"));

        // ---------------------------------------------------------------------------------------
        // test overwrite or add
        // ---------------------------------------------------------------------------------------
        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "Kerry", "Kerry", false);
        Assert.assertNotNull(result);
        Assert.assertEquals("[\"Johnny\",\"Sammy\",\"Tracy\",\"Alex\",{\"Kerry\":\"Kerry\"}]",
                            JSONPath.find(result, "[REGEX:\\.+]"));

        result = JSONPath.overwriteOrAdd(new JSONArray(fixture), "Tracy", "Kerry", false);
        Assert.assertNotNull(result);
        Assert.assertEquals("[\"Johnny\",\"Sammy\",\"Kerry\",\"Alex\"]", JSONPath.find(result, "[REGEX:\\.+]"));
    }

    @Test
    public void jsonPathFunctions() {
        String fixture = "{\n" +
                         "    \"books\": [\n" +
                         "        { \"title\": \"Introduction to Programming\", \"price\": 13.95, \"category\": \"Technology\" },\n" +
                         "        { \"title\": \"How to Cook Good Food Cheap\", \"price\": 15.60, \"category\": \"Home Improvement\" },\n" +
                         "        { \"title\": \"Furniture for Harmony\", \"price\": 32.50, \"category\": \"Home Improvement\" }\n" +
                         "    ],\n" +
                         "    \"published date\": \"2019-01-14\"\n" +
                         "}";
        Assert.assertEquals("1", JSONPath.find(new JSONObject(fixture), "published date => count"));
        Assert.assertEquals("2", JSONPath.find(new JSONObject(fixture), "books[category=Home Improvement] => count"));
        Assert.assertEquals("3", JSONPath.find(new JSONObject(fixture), "books.title => count"));
        Assert.assertEquals("3", JSONPath.find(new JSONObject(fixture), "books => count"));
        Assert.assertEquals(
            "[\"Furniture for Harmony\",\"How to Cook Good Food Cheap\",\"Introduction to Programming\"]",
            JSONPath.find(new JSONObject(fixture), "books.title => ascending"));
        Assert.assertEquals("[\"Technology\",\"Home Improvement\",\"Home Improvement\"]",
                            JSONPath.find(new JSONObject(fixture), "books.category => descending"));
        Assert.assertEquals("62.05", JSONPath.find(new JSONObject(fixture), "books.price => sum"));
        Assert.assertEquals("20.683333333333334", JSONPath.find(new JSONObject(fixture), "books.price => average"));
        Assert.assertEquals("13.95", JSONPath.find(new JSONObject(fixture), "books.price => min"));
        Assert.assertEquals("32.50", JSONPath.find(new JSONObject(fixture), "books.price => max"));
        Assert.assertEquals("13.95", JSONPath.find(new JSONObject(fixture), "books.price => first"));
        Assert.assertEquals("32.50", JSONPath.find(new JSONObject(fixture), "books.price => last"));
        Assert.assertEquals("[\"Home Improvement\",\"Technology\"]",
                            JSONPath.find(new JSONObject(fixture), "books.category => distinct"));

    }

    private void testPathValue(JSONObject fixture, String path, String expected) {
        String testVal = JSONPath.find(fixture, path);
        Assert.assertEquals(expected, testVal);
        System.out.println(StringUtils.rightPad("test passed - path:" + path + " =", 55) + testVal);
    }

    private void testPathValue(JSONArray fixture, String path, String expected) {
        String testVal = JSONPath.find(fixture, path);
        Assert.assertEquals(expected, testVal);
        System.out.println(StringUtils.rightPad("test passed - path:" + path + " =", 55) + testVal);
    }

    private void testPrepend(JSONObject fixture, String path, String prependWith, String expected)
        throws JSONException {
        JSONObject json = JSONPath.prepend(fixture, path, prependWith);
        Assert.assertNotNull(json);
        Assert.assertEquals(expected, JSONPath.find(json, path));
    }

    private void testAppend(JSONObject fixture, String path, String appendWith, String expected)
        throws JSONException {
        JSONObject json = JSONPath.append(fixture, path, appendWith);
        Assert.assertNotNull(json);
        Assert.assertEquals(expected, JSONPath.find(json, path));
    }

    private void testOverwrite(JSONObject fixture, String path, String overwriteWith, String expected)
        throws JSONException {
        JSONObject json = JSONPath.overwrite(fixture, path, overwriteWith);
        Assert.assertNotNull(json);
        Assert.assertEquals(expected, JSONPath.find(json, path));
    }
}
