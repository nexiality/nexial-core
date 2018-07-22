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
 */

package org.nexial.core.utils;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.utils.JsonEditor.JsonEditorConfig;

public class JSONPathTest3 {

    // consider these jsonpath:
    // 1. a.b.c.d
    // 2. a[1].b                                                                    (JSONArray a contains at least 2 items)
    // 3. US[California].rivers[Napa]                                               (JSONArray "US" contains JSONArray rivers)
    // 4. department[finance].employee[id=1234].name                                (JSONArray contains JSONArray contains node id with value 1234)
    // 5. employee[birthdate=REGEX:1985-\.+].full_name                              (filter with regex on node value - ERROR OUT IF NO MATCH FOUND; WE NEED EXACT NODE NAME)
    // 6. work_history[REGEX:(zipcode|postal)=REGEX:900\.{2,}].[REGEX:(H|h)ours]    (filter with regex on node name and node value - ERROR OUT IF NO MATCH FOUND; WE NEED EXACT NODE NAME)

    @Test
    public void exceptions() {
        JsonEditorConfig config = new JsonEditorConfig();
        config.setRemoveNull(true);
        JsonEditor editor = JsonEditor.newInstance(config);

        try {
            editor.add("{}", "style[my]profile", "\"color\": \"yellow\"");
            Assert.fail("expect failure when JSONPath is malformed: '.' required after [...] filter");
        } catch (MalformedJsonPathException e) { /* it's ok, exception expected */ }

        try {
            editor.add("{}", "style[my]pro.file", "\"color\": \"yellow\"");
            Assert.fail("expect failure when JSONPath is malformed: '.' required immediately after [...] filter");
        } catch (MalformedJsonPathException e) { /* it's ok, exception expected */ }

        try {
            editor.add("{}", "style[]", "\"color\": \"yellow\"");
            Assert.fail("expect failure when JSONPath is malformed: empty filter [] not allowed");
        } catch (MalformedJsonPathException e) { /* it's ok, exception expected */ }

        try {
            editor.add("{}", "style].color", "\"color\": \"yellow\"");
            Assert.fail("expect failure when JSONPath is malformed: unbalanced brackets [] not allowed");
        } catch (MalformedJsonPathException e) { /* it's ok, exception expected */ }

        try {
            editor.add("{}", "style[mine.color", "\"color\": \"yellow\"");
            Assert.fail("expect failure when JSONPath is malformed: unbalanced brackets [] not allowed");
        } catch (MalformedJsonPathException e) { /* it's ok, exception expected */ }

        try {
            editor.add("{}", "style[1].color.", "\"color\": \"yellow\"");
            Assert.fail("expect failure when JSONPath is malformed: ending '.' not allowed");
        } catch (MalformedJsonPathException e) { /* it's ok, exception expected */ }
    }

    @Test
    public void add_one_level() {
        JsonEditorConfig config = new JsonEditorConfig();
        config.setRemoveNull(true);
        JsonEditor editor = JsonEditor.newInstance(config);

        assertAsExpected("start with nothing; should end up with new JSON",
                         "{\"color\":\"yellow\"}",
                         editor.add("{}", "", "\"color\": \"yellow\""));

        assertAsExpected("start with nothing; should end up with new JSON, and quoted string is optional",
                         "{\"color\":\"yellow\"}",
                         editor.add("{}", "", "color:yellow"));

        assertAsExpected("add new node to JSON object; assert that quoted string is ok",
                         "{\"shape\":\"square\",\"color\":\"yellow\"}",
                         editor.add("{ \"shape\": \"square\" }", "", "color:\"yellow\""));

        assertAsExpected("add new node to JSON object; we don't replace case-insensitively",
                         "{\"color\":\"yellow\",\"COLOR\":\"YELLOW\"}",
                         editor.add("{ \"COLOR\": \"YELLOW\" }", "", "\"color\":\"yellow\""));

        assertAsExpected("add new node (string) to array",
                         "{\"color\":[\"YELLOW\",\"pink\"]}",
                         editor.add("{\"color\":[\"YELLOW\"]}", "color[1]", "pink"));

        assertAsExpected("add new node (quoted string) to array",
                         "{\"color\":[\"YELLOW\",\"pink\"]}",
                         editor.add("{\"color\":[\"YELLOW\"]}", "color[1]", "\"pink\""));

        assertAsExpected("add new nodes (boolean, string, double) to array",
                         "{\"color\":[\"YELLOW\",true,\"chicken\",59.2]}",
                         editor.add("{\"color\":[\"YELLOW\"]}", "color", "true,\"chicken\",59.2"));

        assertAsExpected("add new node (one string object to 2nd item) to array",
                         "{\"color\":[\"YELLOW\",[true,\"chicken\",59.2]]}",
                         editor.add("{\"color\":[\"YELLOW\"]}", "color[1]", "true,\"chicken\",59.2"));

        assertAsExpected("add new node to array",
                         "{\"color\":[\"YELLOW\",{\"favorite\":\"pink\"}]}",
                         editor.add("{\"color\":[\"YELLOW\"]}", "color[1]", "{\"favorite\":\"pink\"}"));

        assertAsExpected("add JSON to existing JSON node",
                         "{\"red\":250,\"green\":250,\"color\":\"YELLOW\",\"blue\":230}",
                         editor.add("{ \"color\":\"YELLOW\" }", "", "{\"red\":250,\"green\":250,\"blue\":230}"));

        assertAsExpected("replace existing JSON node",
                         "{\"color\":{\"red\":250,\"green\":250,\"blue\":230}}",
                         editor.add("{\"color\":\"YELLOW\"}", "color", "{\"red\":250,\"green\":250,\"blue\":230}"));
    }

    @Test
    public void add_multi_level() {
        JsonEditorConfig config = new JsonEditorConfig();
        config.setRemoveNull(true);
        JsonEditor editor = JsonEditor.newInstance(config);

        assertAsExpected("add JSON to existing JSON node, with nested JSON Path",
                         "{\"color\":{\"name\":\"YELLOW\",\"hue\":{\"red\":255,\"green\":255,\"blue\":0}}}",
                         editor.add("{\"color\":{\"hue\":{\"red\":255},\"name\":\"YELLOW\"}}",
                                    "color.hue",
                                    "{\"green\":255,\"blue\":0}"));

        assertAsExpected("replace existing nested JSON array item with another array",
                         "{\"color\":{\"brightness\":0.254,\"name\":\"YELLOW\",\"hue\":[255,[255,0],0]}}",
                         editor.add("{\"color\":{\"hue\":[255,0,0],\"brightness\":0.254,\"name\":\"YELLOW\"}}",
                                    "color.hue[1]",
                                    "[255,0]"));

        assertAsExpected("append existing nested JSON array with another array",
                         "{\"color\":{\"brightness\":0.254,\"name\":\"YELLOW\",\"hue\":[255,0,0,255,255,0]}}",
                         editor.add("{\"color\":{\"hue\":[255,0,0],\"brightness\":0.254,\"name\":\"YELLOW\"}}",
                                    "color.hue",
                                    "[255,255,0]"));

        assertAsExpected("replace existing nested JSON array with another array",
                         "{\"color\":{\"brightness\":0.254,\"name\":\"YELLOW\",\"hue\":[255,255,0]}}",
                         editor.add("{\"color\":{\"hue\":[255,0,0],\"brightness\":0.254,\"name\":\"YELLOW\"}}",
                                    "color",
                                    "{\"hue\":[255,255,0]}"));
    }

    @Test
    public void add_array() {
        JsonEditorConfig config = new JsonEditorConfig();
        config.setRemoveNull(true);
        JsonEditor editor = JsonEditor.newInstance(config);

        assertAsExpected("add items to existing JSON array",
                         "[255,2,3,254,54]",
                         editor.add("[255,2,3]", "", "[254,54]"));

        assertAsExpected("can't add to non-existing node", "[255,2,3]", editor.add("[255,2,3]", "a", "[213,94]"));

        assertAsExpected("add items to JSON array",
                         "[255,2,3,{\"extra\":[213,94]}]",
                         editor.add("[255,2,3]", "", "{\"extra\":[213,94]}"));

        assertAsExpected("add items to object nested in array",
                         "[255,2,3,{\"extra\":[213,94]}]",
                         editor.add("[255,2,3,{\"extra\":null}]", "extra", "[213,94]"));

        String fixture1 = "{" +
                          " \"departments\":[" +
                          "   { \"group\":\"Technology\", \"employees\":[\"John\",\"James\",\"Jack\"] }," +
                          "   { \"group\":\"Accounting\", \"employees\":[\"Adam\",\"Abbey\",\"Apollo\"] }" +
                          " ]" +
                          "}";

        // adding new item to departments array
        assertAsExpected("add object to existing JSON array",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}," +
                         "{\"employees\":[\"Peter\",\"Paul\",\"Pippy\"],\"group\":\"Support\"}" +
                         "]}",
                         editor.add(fixture1,
                                    "departments",
                                    "{\"group\":\"Support\",\"employees\":[\"Peter\",\"Paul\",\"Pippy\"]}"));

        // adding new item to departments array; doesn't matter that the new item is not like existing items.
        Object fixture2 = editor.add(fixture1, "departments", "{\"favorites\":[]}");
        assertAsExpected("add new object to existing JSON array",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}," +
                         "{\"favorites\":[]}" +
                         "]}",
                         fixture2);

        // replacing existing empty array with new object structure (don't specify `favorite` to activate "replacement")
        assertAsExpected("add new items to existing empty JSON array",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}," +
                         "{\"favorites\":{\"color\":\"black\",\"numbers\":[42,39,7.14]}}" +
                         "]}",
                         editor.add(fixture2.toString(),
                                    "departments[2]",
                                    "{\"favorites\":{\"color\":\"black\",\"numbers\":[42,39,7.14]}}"));

        // add to existing empty array with new object structure (specify `favorite` to activate "add")
        assertAsExpected("add new items to existing empty JSON array",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}," +
                         "{\"favorites\":[{\"color\":\"black\",\"numbers\":[42,39,7.14]}]}" +
                         "]}",
                         editor.add(fixture2.toString(),
                                    "departments[2].favorites",
                                    "{\"color\":\"black\",\"numbers\":[42,39,7.14]}"));

        assertAsExpected("append to existing nested JSON array with more items",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\",\"Peter\",\"Paul\",\"Pippy\"],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture1,
                                    "departments[1].employees",
                                    "[\"Peter\",\"Paul\",\"Pippy\"]"));

        assertAsExpected("add array as new item to existing nested JSON array item",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\",[\"Peter\",\"Paul\",\"Pippy\"]],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture1, "departments[1].employees[3]", "[\"Peter\",\"Paul\",\"Pippy\"]"));

        assertAsExpected("add primitive as new item to existing nested JSON array item",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\",1353.234],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture1, "departments[1].employees[3]", "1353.234"));

        assertAsExpected("add primitive as new item to existing nested JSON array item (first level)",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}," +
                         "1353.234" +
                         "]}",
                         editor.add(fixture1, "departments[2]", "1353.234"));

        assertAsExpected("add object to non-existing nested JSON array",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"years\":[16,5,2.7],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture1, "departments[1]", "{\"years\":[16,5,2.7]}"));

        assertAsExpected("CANNOT items to non-existing nested JSON array item",
                         "{\"departments\":[" +
                         "{\"employees\":[\"John\",\"James\",\"Jack\"],\"group\":\"Technology\"}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture1, "departments[1].years", "[16,5,2.7]"));
    }

    @Test
    public void add_null() {
        JsonEditorConfig config = new JsonEditorConfig();
        config.setRemoveNull(true);
        JsonEditor editor = JsonEditor.newInstance(config);

        String fixture1 = "{" +
                          " \"departments\":[" +
                          "   { \"group\":\"Technology\", \"employees\":[\"John\",\"James\",\"Jack\"] }," +
                          "   { \"group\":\"Accounting\", \"employees\":[\"Adam\",\"Abbey\",\"Apollo\"] }" +
                          " ]" +
                          "}";

        assertAsExpected("replace entire object with null", "null", editor.add(fixture1, "", "null"));

        assertAsExpected("replace entire object with null",
                         "{\"departments\":null}",
                         editor.add(fixture1, "", "{\"departments\":null}"));

        assertAsExpected("replace nested array with key->null structure",
                         "{\"departments\":[" +
                         "{\"employees\":null,\"group\":null}," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture1, "departments[0]", "{\"employees\":null,\"group\":null}"));

        assertAsExpected("replace nested array with empty string",
                         "{\"departments\":[" +
                         "\"\"," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture1, "departments[0]", ""));

        Object fixture2 = editor.add(fixture1, "departments[0]", "     ");
        assertAsExpected("replace nested array with blanks",
                         "{\"departments\":[" +
                         "\"     \"," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}" +
                         "]}",
                         fixture2);

        assertAsExpected("replace blanks with array",
                         "{\"departments\":[" +
                         "[\"   \",\"\",null]," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture2.toString(), "departments[0]", "[\"   \",\"\",null]"));

        assertAsExpected("replace array item with null",
                         "{\"departments\":[" +
                         "null," +
                         "{\"employees\":[\"Adam\",\"Abbey\",\"Apollo\"],\"group\":\"Accounting\"}" +
                         "]}",
                         editor.add(fixture1, "departments[0]", "null"));

        String fixture3 = "{" +
                          "\"quiz\": {" +
                          "  \"sport\": {" +
                          "      \"q1\": {" +
                          "          \"question\": \"Which one is correct team name in NBA?\"," +
                          "          \"options\": null," +
                          "          \"answer\": \"Huston Rocket\"" +
                          "      }" +
                          "  }," +
                          "  \"maths\": {" +
                          "      \"q1\": {" +
                          "          \"question\": \"5 + 7 = ?\"," +
                          "          \"options\": [\"10\",\"11\",\"12\",\"13\"]," +
                          "          \"answer\": \"12\"" +
                          "      }," +
                          "      \"q2\": {" +
                          "          \"question\": \"12 - 8 = ?\"," +
                          "          \"options\": [\"1\",\"2\",\"3\",\"4\"]," +
                          "          \"answer\": \"4\"" +
                          "      }" +
                          "  }" +
                          "}" +
                          "}";
        assertAsExpected("replace null with null",
                         "{" +
                         "\"quiz\":{" +
                         "\"maths\":{" +
                         "\"q1\":{\"question\":\"5 + 7 = ?\",\"answer\":\"12\",\"options\":[\"10\",\"11\",\"12\",\"13\"]}," +
                         "\"q2\":{\"question\":\"12 - 8 = ?\",\"answer\":\"4\",\"options\":[\"1\",\"2\",\"3\",\"4\"]}" +
                         "}," +
                         "\"sport\":{" +
                         "\"q1\":{" +
                         "\"question\":\"Which one is correct team name in NBA?\"," +
                         "\"answer\":\"Huston Rocket\"," +
                         "\"options\":null" +
                         "" +
                         "}" +
                         "}" +
                         "}}",
                         editor.add(fixture3, "quiz.sport.q1.options", "null"));
        assertAsExpected("replace null with primitive",
                         "{" +
                         "\"quiz\":{" +
                         "\"maths\":{" +
                         "\"q1\":{\"question\":\"5 + 7 = ?\",\"answer\":\"12\",\"options\":[\"10\",\"11\",\"12\",\"13\"]}," +
                         "\"q2\":{\"question\":\"12 - 8 = ?\",\"answer\":\"4\",\"options\":[\"1\",\"2\",\"3\",\"4\"]}" +
                         "}," +
                         "\"sport\":{" +
                         "\"q1\":{" +
                         "\"question\":\"Which one is correct team name in NBA?\"," +
                         "\"answer\":\"Huston Rocket\"," +
                         "\"options\":true" +
                         "" +
                         "}" +
                         "}" +
                         "}}",
                         editor.add(fixture3, "quiz.sport.q1.options", "true"));
    }

    @Test
    public void replace() {
        JsonEditorConfig config = new JsonEditorConfig();
        config.setRemoveNull(true);
        JsonEditor editor = JsonEditor.newInstance(config);

        assertAsExpected("replace one JSON object with another",
                         "{\"color\":\"yellow\"}",
                         editor.add("{ \"color\": \"red\" }", "", "{\"color\":\"yellow\"}"));

        assertAsExpected("replace one JSON object with another",
                         "{\"color\":\"yellow\"}",
                         editor.add("{ \"color\": true}", "", "{\"color\":\"yellow\"}"));

        assertAsExpected("replace one JSON object with another",
                         "{\"color\":\"yellow\"}",
                         editor.add("{ \"color\": [1,2,3]}", "", "{\"color\":\"yellow\"}"));

        assertAsExpected("replace one JSON object with another, but leave the rest unchanged",
                         "{\"color\":\"yellow\",\"shape\":\"square\"}",
                         editor.add("{\"color\":null,\"shape\":\"square\"}", "", "{\"color\":\"yellow\"}"));

        assertAsExpected("replace one JSON object with another, but leave the rest unchanged",
                         "{\"color\":[\"yellow\",\"red\"],\"shape\":\"square\"}",
                         editor.add("{\"color\":null,\"shape\":\"square\"}",
                                    "",
                                    "{\"color\":[\"yellow\",\"red\"]}"));

        assertAsExpected("replace primitive with JSON object, but leave the rest unchanged",
                         "{\"color\":null,\"shape\":{\"name\":\"square\",\"type\":{\"name\":\"geometric\",\"symmetric\":true}}}",
                         editor.add("{\"color\":null,\"shape\":{\"type\":\"geometric\",\"name\":\"square\"}}",
                                    "shape.type",
                                    "{\"name\":\"geometric\",\"symmetric\":true}"));

        assertAsExpected("replace one JSON object with another, but leave the rest unchanged",
                         "{\"color\":[\"yellow\",\"red\"],\"shape\":\"square\"}",
                         editor.add("{\"color\":123.45,\"shape\":\"square\"}",
                                    "",
                                    "{\"color\":[\"yellow\",\"red\"]}"));

        assertAsExpected("replace one JSON array with another, but leave the rest unchanged",
                         "{\"color\":[\"yellow\",\"red\"],\"shape\":\"square\"}",
                         editor.add("{\"color\":[\"blue\"],\"shape\":\"square\"}",
                                    "",
                                    "{\"color\":[\"yellow\",\"red\"]}"));

        assertAsExpected("replace one JSON object with array, but leave the rest unchanged",
                         "{\"color\":[\"yellow\",\"red\"],\"shape\":\"square\"}",
                         editor.add("{\"color\":{\"hue\":[20,50,250],\"name\":\"blue\"},\"shape\":\"square\"}",
                                    "",
                                    "{\"color\":[\"yellow\",\"red\"]}"));

        assertAsExpected("replace one JSON object with array, but leave the rest unchanged",
                         "{\"color\":{\"red\":[255,0,0],\"yellow\":[255,255,0]},\"shape\":\"square\",\"name\":\"blue\"}",
                         editor.add("{\"color\":[20,50,250],\"name\":\"blue\",\"shape\":\"square\"}",
                                    "",
                                    "{\"color\":{\"yellow\":[255,255,0],\"red\":[255,0,0]}}"));

        assertAsExpected("replace one JSON primitive with array, but leave the rest unchanged",
                         "{\"color\":[20,50,250],\"shape\":\"square\",\"name\":[\"red\",\"rojo\",\"hong\"]}",
                         editor.add("{\"color\":[20,50,250],\"name\":\"blue\",\"shape\":\"square\"}",
                                    "name",
                                    "[\"red\",\"rojo\",\"hong\"]"));

        // replacing array with array (must specify with name)
        assertAsExpected("replace one JSON array with array, but leave the rest unchanged",
                         "{\"menu\":{\"popup\":{\"menuitem\":[\"New\",\"Open\",\"Close\"]},\"id\":\"file\",\"value\":\"File\"}}",
                         editor.add("{\"menu\": {" +
                                    "  \"id\": \"file\"," +
                                    "  \"value\": \"File\"," +
                                    "  \"popup\": {" +
                                    "    \"menuitem\": [" +
                                    "      {\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}," +
                                    "      {\"value\": \"Open\", \"onclick\": \"OpenDoc()\"}," +
                                    "      {\"value\": \"Close\", \"onclick\": \"CloseDoc()\"}" +
                                    "    ]" +
                                    "  }" +
                                    "}}",
                                    "menu.popup",
                                    "\"menuitem\":[\"New\",\"Open\",\"Close\"]"));
        assertAsExpected("replace one JSON array with array (wrapped as object), but leave the rest unchanged",
                         "{\"menu\":{\"popup\":{\"menuitem\":[\"New\",\"Open\",\"Close\"]},\"id\":\"file\",\"value\":\"File\"}}",
                         editor.add("{\"menu\": {" +
                                    "  \"id\": \"file\"," +
                                    "  \"value\": \"File\"," +
                                    "  \"popup\": {" +
                                    "    \"menuitem\": [" +
                                    "      {\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}," +
                                    "      {\"value\": \"Open\", \"onclick\": \"OpenDoc()\"}," +
                                    "      {\"value\": \"Close\", \"onclick\": \"CloseDoc()\"}" +
                                    "    ]" +
                                    "  }" +
                                    "}}",
                                    "menu.popup",
                                    "{\"menuitem\":[\"New\",\"Open\",\"Close\"]}"));

        // replacing array with null (must specify with name)
        assertAsExpected("replace one JSON array with null, but leave the rest unchanged",
                         "{\"menu\":{\"popup\":{\"menuitem\":null},\"id\":\"file\",\"value\":\"File\"}}",
                         editor.add("{\"menu\": {" +
                                    "  \"id\": \"file\"," +
                                    "  \"value\": \"File\"," +
                                    "  \"popup\": {" +
                                    "    \"menuitem\": [" +
                                    "      {\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}," +
                                    "      {\"value\": \"Open\", \"onclick\": \"OpenDoc()\"}," +
                                    "      {\"value\": \"Close\", \"onclick\": \"CloseDoc()\"}" +
                                    "    ]" +
                                    "  }" +
                                    "}}",
                                    "menu.popup",
                                    "\"menuitem\":null"));

        // appending to nested array with null
        assertAsExpected("append nested array with null, but leave the rest unchanged",
                         "{\"menu\":" +
                         "{\"popup\":{" +
                         "\"menuitem\":[" +
                         "{\"onclick\":\"CreateNewDoc()\",\"value\":\"New\"}," +
                         "{\"onclick\":\"OpenDoc()\",\"value\":\"Open\"}," +
                         "{\"onclick\":\"CloseDoc()\",\"value\":\"Close\"}," +
                         "null" +
                         "]}," +
                         "\"id\":\"file\"," +
                         "\"value\":\"File\"" +
                         "}}",
                         editor.add("{\"menu\": {" +
                                    "  \"id\": \"file\"," +
                                    "  \"value\": \"File\"," +
                                    "  \"popup\": {" +
                                    "    \"menuitem\": [" +
                                    "      {\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}," +
                                    "      {\"value\": \"Open\", \"onclick\": \"OpenDoc()\"}," +
                                    "      {\"value\": \"Close\", \"onclick\": \"CloseDoc()\"}" +
                                    "    ]" +
                                    "  }" +
                                    "}}",
                                    "menu.popup.menuitem",
                                    "null"));

        assertAsExpected("replace existing array with null, but leave the rest unchanged",
                         "{\"menu\":" +
                         "{\"popup\":{" +
                         "\"menuitem\":[" +
                         "{\"onclick\":\"CreateNewDoc()\",\"value\":\"New\"}," +
                         "null," +
                         "{\"onclick\":\"CloseDoc()\",\"value\":\"Close\"}" +
                         "]}," +
                         "\"id\":\"file\"," +
                         "\"value\":\"File\"" +
                         "}}",
                         editor.add("{\"menu\": {" +
                                    "  \"id\": \"file\"," +
                                    "  \"value\": \"File\"," +
                                    "  \"popup\": {" +
                                    "    \"menuitem\": [" +
                                    "      {\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}," +
                                    "      {\"value\": \"Open\", \"onclick\": \"OpenDoc()\"}," +
                                    "      {\"value\": \"Close\", \"onclick\": \"CloseDoc()\"}" +
                                    "    ]" +
                                    "  }" +
                                    "}}",
                                    "menu.popup.menuitem[1]",
                                    "null"));

        assertAsExpected("replace existing array with primitive, but leave the rest unchanged",
                         "{\"menu\":" +
                         "{\"popup\":{" +
                         "\"menuitem\":[" +
                         "{\"onclick\":\"CreateNewDoc()\",\"value\":\"New\"}," +
                         "19.045," +
                         "{\"onclick\":\"CloseDoc()\",\"value\":\"Close\"}" +
                         "]}," +
                         "\"id\":\"file\"," +
                         "\"value\":\"File\"" +
                         "}}",
                         editor.add("{\"menu\": {" +
                                    "  \"id\": \"file\"," +
                                    "  \"value\": \"File\"," +
                                    "  \"popup\": {" +
                                    "    \"menuitem\": [" +
                                    "      {\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}," +
                                    "      {\"value\": \"Open\", \"onclick\": \"OpenDoc()\"}," +
                                    "      {\"value\": \"Close\", \"onclick\": \"CloseDoc()\"}" +
                                    "    ]" +
                                    "  }" +
                                    "}}",
                                    "menu.popup.menuitem[1]",
                                    "19.045"));

        // replacing nested object with another nested object
        assertAsExpected("replace nested primitive with another primitive, but leave the rest unchanged",
                         "{\"menu\":" +
                         "{\"popup\":{" +
                         "\"menuitem\":[" +
                         "{\"onclick\":\"CreateNewDoc()\",\"value\":\"New\"}," +
                         "{\"onclick\":\"OpenDocument()\",\"value\":\"Open\"}," +
                         "{\"onclick\":\"CloseDoc()\",\"value\":\"Close\"}" +
                         "]}," +
                         "\"id\":\"file\"," +
                         "\"value\":\"File\"" +
                         "}}",
                         editor.add("{\"menu\": {" +
                                    "  \"id\": \"file\"," +
                                    "  \"value\": \"File\"," +
                                    "  \"popup\": {" +
                                    "    \"menuitem\": [" +
                                    "      {\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}," +
                                    "      {\"value\": \"Open\", \"onclick\": \"OpenDoc()\"}," +
                                    "      {\"value\": \"Close\", \"onclick\": \"CloseDoc()\"}" +
                                    "    ]" +
                                    "  }" +
                                    "}}",
                                    "menu.popup.menuitem[1].onclick",
                                    "OpenDocument()"));

        // todo: null should be removed
        assertAsExpected("replace nested primitive with another primitive, but leave the rest unchanged",
                         "{\"menu\":" +
                         "{\"popup\":{" +
                         "\"menuitem\":[" +
                         "{\"onclick\":\"CreateNewDoc()\",\"value\":\"New\"}," +
                         "{\"onclick\":null,\"description\":\"open a document\",\"value\":\"Open\"}," +
                         "{\"onclick\":\"CloseDoc()\",\"value\":\"Close\"}" +
                         "]}," +
                         "\"id\":\"file\"," +
                         "\"value\":\"File\"" +
                         "}}",
                         editor.add("{\"menu\": {" +
                                    "  \"id\": \"file\"," +
                                    "  \"value\": \"File\"," +
                                    "  \"popup\": {" +
                                    "    \"menuitem\": [" +
                                    "      {\"value\": \"New\", \"onclick\": \"CreateNewDoc()\"}," +
                                    "      {\"value\": \"Open\", \"onclick\": \"OpenDoc()\"}," +
                                    "      {\"value\": \"Close\", \"onclick\": \"CloseDoc()\"}" +
                                    "    ]" +
                                    "  }" +
                                    "}}",
                                    "menu.popup.menuitem[1]",
                                    "{\"description\":\"open a document\",\"value\":\"Open\",\"onclick\":null}"));

        // replacing array item with another nested object
    }

    protected void assertAsExpected(String message, String expected, Object actual) {
        String jsonString = actual.toString();
        System.out.println();
        System.out.println("[TEST CASE] " + message);
        System.out.println("json = " + jsonString);
        Assert.assertEquals(message, expected, jsonString);
    }

    // @NotNull
    // protected JSONArray add(JSONArray array, String jsonPath, String data, int indexToAdd) {
    //     if (array == null) { array = new JSONArray(); }
    //
    //     if (StringUtils.isBlank(data)) { return array.put(indexToAdd, data); }
    //
    //     String dataTrimmed = StringUtils.trim(data);
    //     if (TextUtils.isBetween(dataTrimmed, "[", "]")) { return array.put(new JSONArray(dataTrimmed)); }
    //     if (TextUtils.isBetween(dataTrimmed, "{", "}")) { return array.put(indexToAdd, new JSONObject(dataTrimmed)); }
    //     if (RegexUtils.isExact(data, "(true|false)")) { return array.put(indexToAdd, BooleanUtils.toBoolean(data)); }
    //     if (NumberUtils.isCreatable(data)) { return array.put(indexToAdd, NumberUtils.createNumber(data)); }
    //     if (TextUtils.isBetween(data, "\"", "\"")) { return array.put(indexToAdd, StringUtils.unwrap(data, "\"")); }
    //
    //     // last resort...
    //     return array.put(indexToAdd, data);
    // }
    //
    // private List<Object> expandToList(String listItems) {
    //     List<Object> list = new ArrayList<>();
    //
    //     if (StringUtils.isEmpty(listItems)) { return list; }
    //
    //     if (StringUtils.isBlank(listItems)) {
    //         list.add(listItems);
    //         return list;
    //     }
    //
    //     String[] items = StringUtils.split(listItems, ",");
    //     for (String item : items) {
    //         if (TextUtils.isBetween(item, "[", "]")) {
    //             list.add(expandToList(TextUtils.unwrap(item, "[", "]")));
    //             continue;
    //         }
    //         if (TextUtils.isBetween(item, "{", "}")) {
    //             list.add(expandToList(TextUtils.unwrap(item, "[", "]")));
    //             continue;
    //         }
    //     }
    //
    //     return list;
    // }
    //
    // private Map<String, Object> addToList(String nodeName, String jsonPath, String data) {
    //     Map<String, Object> newMap = new HashedMap<>();
    //     JSONObject newData = add(new JSONObject(), jsonPath, data);
    //     if (StringUtils.isBlank(jsonPath)) {
    //         newMap = newData;
    //     } else {
    //         // only use nodeName (effectively creating another level) if we've reached the end of `jsonPath`
    //         newMap.put(nodeName, newData);
    //     }
    //     return newMap;
    // }
    //
    // private String cleanData(String data) {
    //     return StringUtils.isBlank(data) ? data : TextUtils.replaceStrings(data, ESCAPED);
    // }
    //
    // private String unescape(String data) {
    //     return StringUtils.isBlank(data) ? data : TextUtils.replaceStrings(data, UNESCAPED);
    // }
    //
    // private void printMap(Map map, String outputPrefix) {
    //     Set keys = map.keySet();
    //     for (Object key : keys) {
    //         System.out.print("\n" + outputPrefix + key + "\t=\t");
    //         Object value = map.get(key);
    //         if (value instanceof Map) {
    //             printMap((Map) value, outputPrefix + "\t");
    //         } else {
    //             System.out.print(value == null ? "null" : "(" + value.getClass() + ")\t" + value);
    //         }
    //     }
    // }
}
