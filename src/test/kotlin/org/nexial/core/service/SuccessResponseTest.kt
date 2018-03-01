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

package org.nexial.core.service

import com.google.gson.GsonBuilder
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class SuccessResponseTest {
    private val gson = GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .disableInnerClassSerialization()
        .setLenient()
        .create()

    @Test
    fun testSuccessResponse() {
        val fixture = SuccessResponse(message = "Anything")
        println("fixture = $fixture")
        val jsonString = gson.toJson(fixture)
        println("fixture json = $jsonString")

        val json = JSONObject(jsonString)
        println("json = $json")
        Assert.assertTrue(json.has("timestamp"))
        Assert.assertTrue(json.has("message"))
    }
}