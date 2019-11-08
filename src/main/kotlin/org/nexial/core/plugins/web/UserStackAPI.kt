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

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.nexial.core.NexialConst.GSON
import org.nexial.core.plugins.ws.WebServiceClient

class UserStackAPI(apiKey: String = "5b71975a107de30d26f3878fa9adbb5e") {
    private val apiURL = "http://api.userstack.com/detect?access_key=${apiKey}&ua="

    fun detect(ua: String): Map<String, String> {
        val response = WebServiceClient(null).get(apiURL + ua, null)
        return if (response.returnCode in 200..299) {
            parseBrowserMeta(response.body)
        } else {
            val map = mutableMapOf<String, String>()
            map["error"] = response.statusText
            map
        }
    }

    companion object {
        @JvmStatic
        fun parseBrowserMeta(jsonString: String): Map<String, String> {
            val map = mutableMapOf<String, String>()

            val json = GSON.fromJson(jsonString, JsonObject::class.java)
            map["os"] = json.getAsJsonObject("os").getAsJsonPrimitive("name").asString

            val browser = json.getAsJsonObject("browser")
            val name = if (browser.has("name")) {
                val browserName = browser.get("name")
                if (browserName == null || browserName is JsonNull) {
                    "UNKNOWN BROWSER"
                } else {
                    browser.getAsJsonPrimitive("name").asString
                }
            } else "UNKNOWN BROWSER"

            val version = if (browser.has("version")) {
                val versionElem = browser.get("version")
                if (versionElem == null || versionElem is JsonNull) {
                    ""
                } else {
                    " " + browser.getAsJsonPrimitive("version").asString
                }
            } else ""

            map["browser"] = "$name$version"

            return map
        }
    }
}