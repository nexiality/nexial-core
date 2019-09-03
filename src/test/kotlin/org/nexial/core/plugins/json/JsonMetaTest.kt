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

package org.nexial.core.plugins.json

import com.google.gson.JsonParser
import org.apache.commons.lang3.StringUtils
import org.junit.Assert
import org.junit.Test
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.plugins.json.JsonComparisonResult.Difference
import java.io.BufferedReader
import java.io.FileReader

class JsonMetaTest {

    private var jsonMetaParser = JsonMetaParser()
    private val jsonParser = JsonParser()

    @Test
    @Throws(Throwable::class)
    fun parse_and_validate_json_document() {
        val resource = StringUtils.replace(JsonMeta::class.java.name, ".", "/") + "-compare1.json"
        val resourcePath = ResourceUtils.getResourceFilePath(resource)
        Assert.assertNotNull(resourcePath)

        val reader = BufferedReader(FileReader(resourcePath!!))
        val subject = jsonMetaParser.parse(jsonParser.parse(reader))
        println(subject)

        Assert.assertNotNull(subject)
        Assert.assertTrue(subject.isObject)
        val children = subject.children
        Assert.assertEquals(1, children.size.toLong())

        val meta = children[0]
        Assert.assertEquals("quiz", meta.name)

        Assert.assertTrue(meta.isObject)
        Assert.assertEquals(3, meta.children.size.toLong())

        val mathsMeta = meta.children[0]
        assertObject(mathsMeta, "maths", 2)

        val mathsQ1Meta = mathsMeta.children[0]
        assertObject(mathsQ1Meta, "q1", 3)

        val mathsQ1AnswerMeta = mathsQ1Meta.children[0]
        assertLeaf(mathsQ1AnswerMeta, "answer", "12")

        val mathsQ1OptionsMeta = mathsQ1Meta.children[1]
        assertArray(mathsQ1OptionsMeta, "options", 4)
        assertPrimitive(mathsQ1OptionsMeta.children[0], "10")
        assertPrimitive(mathsQ1OptionsMeta.children[1], "11")
        assertPrimitive(mathsQ1OptionsMeta.children[2], "12")
        assertPrimitive(mathsQ1OptionsMeta.children[3], "13")

        val mathsQ1QuestionMeta = mathsQ1Meta.children[2]
        assertLeaf(mathsQ1QuestionMeta, "question", "5 + 7 = ?")

        val mathsQ2Meta = mathsMeta.children[1]
        assertObject(mathsQ2Meta, "q2", 3)

        val mathsQ2AnswerMeta = mathsQ2Meta.children[0]
        assertLeaf(mathsQ2AnswerMeta, "answer", "4")

        val mathsQ2OptionsMeta = mathsQ2Meta.children[1]
        assertArray(mathsQ2OptionsMeta, "options", 4)
        assertPrimitive(mathsQ2OptionsMeta.children[0], "1")
        assertPrimitive(mathsQ2OptionsMeta.children[1], "2")
        assertPrimitive(mathsQ2OptionsMeta.children[2], "3")
        assertPrimitive(mathsQ2OptionsMeta.children[3], "4")

        val mathsQ2QuestionMeta = mathsQ2Meta.children[2]
        assertLeaf(mathsQ2QuestionMeta, "question", "12 - 8 = ?")

        val sportMeta = meta.children[1]
        assertObject(sportMeta, "sport", 1)

        val sportQ1Meta = sportMeta.children[0]
        assertObject(sportQ1Meta, "q1", 3)

        val sportQ1AnswerMeta = sportQ1Meta.children[0]
        assertLeaf(sportQ1AnswerMeta, "answer", "Huston Rocket")

        val sportQ1OptionsMeta = sportQ1Meta.children[1]
        assertArray(sportQ1OptionsMeta, "options", 4)
        assertPrimitive(sportQ1OptionsMeta.children[0], "New York Bulls")
        assertPrimitive(sportQ1OptionsMeta.children[1], "Los Angeles Kings")
        assertPrimitive(sportQ1OptionsMeta.children[2], "Golden State Warriros")
        assertPrimitive(sportQ1OptionsMeta.children[3], "Huston Rocket")

        val sportQ1QuestionMeta = sportQ1Meta.children[2]
        assertLeaf(sportQ1QuestionMeta, "question", "Which one is correct team name in NBA?")

        val statsMeta = meta.children[2]
        assertArray(statsMeta, "stats", 2)

        val statsMathsGroupMeta = statsMeta.children[0]
        assertObject(statsMathsGroupMeta, null, 1)

        val statsMathsMeta = statsMathsGroupMeta.children[0]
        assertObject(statsMathsMeta, "maths", 2)

        val statsMathCategoriesMeta = statsMathsMeta.children[0]
        assertArray(statsMathCategoriesMeta, "categories", 2)
        val statsMathCategories1Meta = statsMathCategoriesMeta.children[0]
        assertObject(statsMathCategories1Meta, null, 1)
        assertLeaf(statsMathCategories1Meta.children[0], "name", "science")
        val statsMathCategories2Meta = statsMathCategoriesMeta.children[1]
        assertObject(statsMathCategories2Meta, null, 1)
        assertLeaf(statsMathCategories2Meta.children[0], "name", "general")

        val statsMathLevelMeta = statsMathsMeta.children[1]
        assertLeaf(statsMathLevelMeta, "level", "5")

        val statsSportGroupMeta = statsMeta.children[1]
        assertObject(statsSportGroupMeta, null, 1)

        val statsSportMeta = statsSportGroupMeta.children[0]
        assertObject(statsSportMeta, "sport", 2)

        val statsSportCategoriesMeta = statsSportMeta.children[0]
        assertArray(statsSportCategoriesMeta, "categories", 2)
        val statsSportCategories1Meta = statsSportCategoriesMeta.children[0]
        assertObject(statsSportCategories1Meta, null, 1)
        assertLeaf(statsSportCategories1Meta.children[0], "name", "general")
        val statsSportCategories2Meta = statsSportCategoriesMeta.children[1]
        assertObject(statsSportCategories2Meta, null, 1)
        assertLeaf(statsSportCategories2Meta.children[0], "name", "sport")

        val statsSportLevelMeta = statsSportMeta.children[1]
        assertLeaf(statsSportLevelMeta, "level", "2")
    }

    @Test
    @Throws(Exception::class)
    fun parse_and_validate_json_array() {
        // test json doc that starts with array
        val resource2 = StringUtils.replace(JsonMeta::class.java.name, ".", "/") + "-compare2.json"
        val resourcePath = ResourceUtils.getResourceFilePath(resource2)
        Assert.assertNotNull(resourcePath)

        val reader2 = BufferedReader(FileReader(resourcePath!!))
        val subject2 = jsonMetaParser.parse(jsonParser.parse(reader2))
        println(subject2)

        Assert.assertTrue(subject2.isArray)
        Assert.assertNull(subject2.name)
        Assert.assertEquals(3, subject2.children.size.toLong())

        val q1MetaParent = subject2.children[0]
        assertObject(q1MetaParent, null, 1)

        val q1Meta = q1MetaParent.children[0]
        assertLeaf(q1Meta.children[0], "answer", "Huston Rocket")
        val q1OptionsMeta = q1Meta.children[1]
        assertArray(q1OptionsMeta, "options", 4)
        assertPrimitive(q1OptionsMeta.children[0], "New York Bulls")
        assertPrimitive(q1OptionsMeta.children[1], "Los Angeles Kings")
        assertPrimitive(q1OptionsMeta.children[2], "Golden State Warriors")
        assertPrimitive(q1OptionsMeta.children[3], "Huston Rocket")
        assertLeaf(q1Meta.children[2], "question", "Which one is correct team name in NBA?")

        val q2MetaParent = subject2.children[1]
        assertObject(q2MetaParent, null, 1)

        val q2Meta = q2MetaParent.children[0]

        assertLeaf(q2Meta.children[0], "answer", "12")

        val q2OptionsMeta = q2Meta.children[1]
        assertArray(q2OptionsMeta, "options", 4)
        assertPrimitive(q2OptionsMeta.children[0], "10")
        assertPrimitive(q2OptionsMeta.children[1], "11")
        assertPrimitive(q2OptionsMeta.children[2], "12")
        assertPrimitive(q2OptionsMeta.children[3], "13")

        assertLeaf(q2Meta.children[2], "question", "5 + 7 = ?")

        val q3MetaParent = subject2.children[2]
        assertObject(q3MetaParent, null, 1)

        val q3Meta = q3MetaParent.children[0]

        assertLeaf(q3Meta.children[0], "answer", "4")

        val q3OptionsMeta = q3Meta.children[1]
        assertArray(q3OptionsMeta, "options", 4)
        assertPrimitive(q3OptionsMeta.children[0], "1")
        assertPrimitive(q3OptionsMeta.children[1], "2")
        assertPrimitive(q3OptionsMeta.children[2], "3")
        assertPrimitive(q3OptionsMeta.children[3], "4")

        assertLeaf(q3Meta.children[2], "question", "12 - 8 = ?")
    }

    @Test
    fun compare_no_diff() {
        val fixture1 = """
            {
                "question": "Which one is correct team name in NBA?",
                "options": [
                    "New York Bulls",
                    "Los Angeles Kings",
                    "Golden State Warriros",
                    "Huston Rocket"
                ],
                "answer": "Huston Rocket"
            }"""
        val fixture2 = """
            {
                "question": "Which one is correct team name in NBA?",
                "options": [
                    "New York Bulls",
                    "Los Angeles Kings",
                    "Golden State Warriros",
                    "Huston Rocket"
                ],
                "answer": "Huston Rocket"
            }"""

        val expected = jsonMetaParser.parse(jsonParser.parse(fixture1))
        val actual = jsonMetaParser.parse(jsonParser.parse(fixture2))
        val results = expected.compare(actual)
        println("results = $results")
        Assert.assertNotNull(results)
        Assert.assertFalse(results.hasDifferences())
    }

    @Test
    fun compare_no_diff_with_nonarray_order_diffs() {
        val fixture1 = """
            {
                "question": "Which one is correct team name in NBA?",
                "options": ["New York Bulls","Los Angeles Kings","Golden State Warriros","Huston Rocket"],
                "answer": "Huston Rocket"
            }"""
        val fixture2 = """
            {
                "answer": "Huston Rocket",
                "options": [ "New York Bulls", "Los Angeles Kings", "Golden State Warriros", "Huston Rocket"],
                "question": "Which one is correct team name in NBA?"
            }"""

        val expected = jsonMetaParser.parse(jsonParser.parse(fixture1))
        val actual = jsonMetaParser.parse(jsonParser.parse(fixture2))
        val results = expected.compare(actual)
        println("results = $results")
        Assert.assertNotNull(results)
        Assert.assertFalse(results.hasDifferences())
    }

    @Test
    fun compare_diff_leaf() {
        val fixture1 = """
             {
                "question": "Which one is correct team name in NBA?",
                "options": [
                    "New York Bulls",
                    "Los Angeles Kings",
                    "Golden State Warriros",
                    "Huston Rocket"
                ],
                "answer": "Houston Rocket"
            }"""
        val fixture2 = """
             {
                "question": "Which one is correct team name in NBA?",
                "options": [
                    "New York Bulls",
                    "Los Angeles Kings",
                    "Golden State Warriros",
                    "Huston Rocket"
                ],
                "answer": "Huston Rocket"
            }"""

        val expected = jsonMetaParser.parse(jsonParser.parse(fixture1))
        val actual = jsonMetaParser.parse(jsonParser.parse(fixture2))
        val results = expected.compare(actual)
        println("results = $results")

        assertResultSize(results, 1)
        assertDiffNodes(results.differences[0], "$.answer", "$.answer")
    }

    @Test
    fun compare_diff_leaves() {
        val fixture1 = """
            {
                "question": "Which one is correct NBA team name?",
                 "options": [
                   "New York Bulls",
                   "Los Angeles Kings",
                   "Golden State Warriros",
                   "Huston Rocket"
                 ],
                 "answer": "Houston Rocket"
           }"""
        val fixture2 = """
            {
                "question": "Which one is correct team name in NBA?",
                "options": [ "New York Bulls", "Los Angeles Kings", "Golden State Warriros", "Huston Rocket" ],
                "answer": "Huston Rocket"
            }"""

        val expected = jsonMetaParser.parse(jsonParser.parse(fixture1))
        val actual = jsonMetaParser.parse(jsonParser.parse(fixture2))
        val results = expected.compare(actual)
        println("results = $results")

        assertResultSize(results, 2)
        assertDiffNodes(results.differences[0], "$.answer", "$.answer")
        assertDiffNodes(results.differences[1], "$.question", "$.question")
    }

    @Test
    fun compare_diff_leaves_and_array() {
        val fixture1 = """
            {
                "question": "Which one is correct NBA team name?",
                "options": [  "New York Bulls",  "Los Angeles Kings",  "Golden State Warriors",  "Huston Rocket" ],
                "answer": "Golden State Warriors"
            }"""
        val fixture2 = """
            {
                "question": "Which one is correct team name in NBA?",
                "answer": "Huston Rocket",
                "options": [  "New York Bulls",  "Los Angeles Kings",  "Chicago Fools",  "Golden State Warriros",  "Huston Rocket" ],
                "for real": false
            }"""

        val expected = jsonMetaParser.parse(jsonParser.parse(fixture1))
        val actual = jsonMetaParser.parse(jsonParser.parse(fixture2))
        val results = expected.compare(actual)
        println("results = $results")

        assertResultSize(results, 8)

        val diff = results.differences[0]
        val message = diff.message
        assertDiffNodes(diff, "$", "$")
        Assert.assertTrue(message.contains("EXPECTED has 3 nodes"))
        Assert.assertTrue(message.contains("ACTUAL has 4 nodes"))

        assertDiffNodes(results.differences[1], "$.answer", "$.answer")
        assertDiffNodes(results.differences[2], "$.options", "$.options")
        assertDiffNodes(results.differences[3], "$.options[2]", "$.options[2]")

        val diff4 = results.differences[4]
        Assert.assertEquals("$.options[3]", diff4.expectedNode)
        Assert.assertEquals("$.options[3]", diff4.actualNode)

        val diff5 = results.differences[5]
        Assert.assertNull(diff5.expectedNode)
        Assert.assertEquals("$.options[4]", diff5.actualNode)

        val diff6 = results.differences[6]
        Assert.assertEquals("$.question", diff6.expectedNode)
        Assert.assertEquals("$.question", diff6.actualNode)

        val diff7 = results.differences[7]
        Assert.assertNull(diff7.expectedNode)
        Assert.assertEquals("$.question", diff7.actualNode)
    }

    @Test
    fun compare_no_diff_array() {
        val fixture1 = """
            [
                {
                    "q1": {
                        "question": "Which one is correct team name in NBA?",
                        "options": [ "New York Bulls", "Los Angeles Kings", "Golden State Warriors", "Huston Rocket" ],
                        "answer": "Huston Rocket"
                    }
                },
                {
                    "q2": {
                        "question": "5 + 7 = ?",
                        "options": [ "10", "11", "12", "13" ],
                        "answer": "12"
                    }
                },
                {
                    "q3": {
                        "question": "12 - 8 = ?",
                        "options": [  "1",  "2", "3", "4"  ],
                        "answer": "4"
                    }
                }
            ]"""
        val fixture2 = """
            [
                {
                    "q1": {
                        "question": "Which one is correct team name in NBA?",
                        "options": [ "New York Bulls","Los Angeles Kings",   "Golden State Warriors", "Huston Rocket" ],
                        "answer": "Huston Rocket"
                    }
                },
                {
                    "q2": {
                        "question": "5 + 7 = ?",
                        "options": [   "10",  "11",  "12",  "13"   ],
                        "answer": "12"
                    }
                },
                {
                    "q3": {
                        "question": "12 - 8 = ?",
                        "options": ["1","2","3","4"],
                        "answer": "4"
                    }
                }]"""

        val expected = jsonMetaParser.parse(jsonParser.parse(fixture1))
        val actual = jsonMetaParser.parse(jsonParser.parse(fixture2))
        val results = expected.compare(actual)
        println("results = $results")

        Assert.assertNotNull(results)
        Assert.assertFalse(results.hasDifferences())
    }

    @Test
    fun compare_diff_array() {
        val fixture1 = """
            [
                {
                    "q1": {
                        "question": "Which one is correct team name in NBA?",
                        "options": ["New York Bulls", "Los Angeles Kings", "Golden State Warriors", "Houston Rocket"],
                        "answer": "Huston Rocket"
                    }
                },
                {
                    "q2": {
                        "question": "5 - 7 = ?",
                        "options": ["10","11","12","13"],
                        "answer": null
                    }
                },
                { "q3": { "question": "12 - 8 = ?", "answer": 4 } }
            ]"""
        val fixture2 = """
            [
                {
                    "q1": {
                        "question": "Which one is correct team name in NBA?",
                        "options": [
                            "New York Bulls","Los Angeles Kings",
                            "Golden State Warriors",  "Huston Rocket"
                        ],
                        "answer": "Huston Rocket"
                    }
                },
                {
                    "q2": {
                        "question": "5 + 7 = ?", "options": [10,11,12,13], "answer": "12"
                    }
                },
                {
                    "q3": {
                        "question": "12 - 8 = ?", "options": [ "1", "2", "3", "4" ], "answer": "4"
                    }
                }
            ]"""

        val results = jsonMetaParser.parse(jsonParser.parse(fixture1))
            .compare(jsonMetaParser.parse(jsonParser.parse(fixture2)))
        println("results = $results")

        assertResultSize(results, 10)
        assertDiffNodes(results.differences[0], "$[0].q1.options[3]", "$[0].q1.options[3]")
        assertDiffNodes(results.differences[1], "$[1].q2.answer", "$[1].q2.answer")
        assertDiffNodes(results.differences[2], "$[1].q2.options[0]", "$[1].q2.options[0]")
        assertDiffNodes(results.differences[3], "$[1].q2.options[1]", "$[1].q2.options[1]")
        assertDiffNodes(results.differences[4], "$[1].q2.options[2]", "$[1].q2.options[2]")
        assertDiffNodes(results.differences[5], "$[1].q2.options[3]", "$[1].q2.options[3]")
        assertDiffNodes(results.differences[6], "$[1].q2.question", "$[1].q2.question")
        assertDiffNodes(results.differences[7], "$[2].q3", "$[2].q3")
        assertDiffNodes(results.differences[8], "$[2].q3.answer", "$[2].q3.answer")
        assertDiffNodes(results.differences[9], null, "$[2].q3.question")

        val diffJson = results.toJson()
        println("results = $diffJson")

        Assert.assertNotNull(diffJson)
        Assert.assertEquals(10, diffJson.size())
    }

    private fun assertResultSize(results: JsonComparisonResult, count: Int) {
        Assert.assertNotNull(results)
        if (count == 0) Assert.assertFalse(results.hasDifferences())
        Assert.assertEquals(count, results.differenceCount())
    }

    private fun assertDiffNodes(diff: Difference, expectedNode: String?, actualNode: String) {
        Assert.assertEquals(expectedNode, diff.expectedNode)
        Assert.assertEquals(actualNode, diff.actualNode)
    }

    private fun assertArray(jsonMeta: JsonMeta, expectedName: String, expectedSize: Int) {
        if (StringUtils.isNotEmpty(jsonMeta.node)) println("inspecting ${jsonMeta.node}")
        Assert.assertTrue(jsonMeta.isArray)
        Assert.assertNotNull(jsonMeta.parent)
        Assert.assertEquals(expectedName, jsonMeta.name)
        Assert.assertEquals(expectedSize, jsonMeta.children.size)
    }

    private fun assertObject(jsonMeta: JsonMeta, expectedName: String?, expectedChildNodes: Int) {
        if (StringUtils.isNotEmpty(jsonMeta.node)) println("inspecting ${jsonMeta.node}")
        Assert.assertTrue(jsonMeta.isObject)
        Assert.assertNotNull(jsonMeta.parent)
        Assert.assertEquals(expectedName, jsonMeta.name)
        Assert.assertEquals(expectedChildNodes, jsonMeta.children.size)
    }

    private fun assertLeaf(jsonMeta: JsonMeta, expectedName: String, expectedValue: String) {
        if (StringUtils.isNotEmpty(jsonMeta.node)) println("inspecting ${jsonMeta.node}")
        Assert.assertTrue(jsonMeta.isLeaf)
        Assert.assertNotNull(jsonMeta.parent)
        Assert.assertEquals(expectedName, jsonMeta.name)
        Assert.assertEquals(expectedValue, jsonMeta.getValueString())
    }

    private fun assertPrimitive(jsonMeta: JsonMeta, expectedValue: String) {
        Assert.assertTrue(jsonMeta.isLeaf)
        Assert.assertNotNull(jsonMeta.parent)
        Assert.assertEquals(expectedValue, jsonMeta.getValueString())
    }
}
