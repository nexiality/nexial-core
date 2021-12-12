/*
 * Copyright 2012-2022 the original author or authors.
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

package org.nexial.core.variable

import com.google.gson.JsonObject
import org.junit.Test
import org.nexial.core.NexialConst.GSON
import org.nexial.core.utils.JsonUtils
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonTest {

    @Test
    fun basic_sanity_test() {
        val fixture = """
        {
            movie: "Hudson Hawk",
            length: "01:40:39",year:1991,
            actors: [ "Bruce Willis","Andie MacDowell",
            "Danny Aiello","Sandra Berhard","Richard Grant" 
], "genre":[Comedy,Heist,Action]}
        """.trimIndent()

        val sanitized = JsonDataType.sanitize(fixture)
        println("sanitized:\n${sanitized}")
        println("beautified:\n${JsonUtils.beautify(sanitized)}")

        val json = GSON.fromJson(sanitized, JsonObject::class.java)
        assertNotNull(json)
        assertTrue(json.isJsonObject)
        assertEquals(5, json.keySet().size)
        assertEquals(listOf("movie", "length", "year", "actors", "genre"), json.keySet().toList())
        assertEquals("Hudson Hawk", json.get("movie").asString)
        assertEquals("01:40:39", json.get("length").asString)
        assertEquals(1991, json.getAsJsonPrimitive("year").asInt)
        assertEquals(listOf("Bruce Willis", "Andie MacDowell", "Danny Aiello", "Sandra Berhard", "Richard Grant"),
                     json.getAsJsonArray("actors").map { it.asString }.toList())
        assertEquals(listOf("Comedy", "Heist", "Action"), json.getAsJsonArray("genre").map { it.asString }.toList())
    }
}