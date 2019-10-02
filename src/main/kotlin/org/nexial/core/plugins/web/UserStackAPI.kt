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

package org.nexial.core.plugins.web

import com.google.gson.JsonObject
import org.nexial.core.NexialConst.GSON
import org.nexial.core.plugins.ws.WebServiceClient

class UserStackAPI(apiKey: String = "5b71975a107de30d26f3878fa9adbb5e") {
    private val apiURL = "http://api.userstack.com/detect?access_key=${apiKey}&ua="

    fun detect(ua: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val response = WebServiceClient(null).get(apiURL + ua, null)
        val returnCode = response.returnCode

        if (returnCode in 200..299) {
            val json = GSON.fromJson(response.body, JsonObject::class.java)
            map["os"] = json.getAsJsonObject("os").getAsJsonPrimitive("name").asString
            val browser = json.getAsJsonObject("browser")
            map["browser"] = "${browser.getAsJsonPrimitive("name").asString} ${browser.getAsJsonPrimitive("version").asString}"
        } else {
            map["error"] = response.statusText
        }

        return map
    }
}