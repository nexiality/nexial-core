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
import org.nexial.commons.utils.CollectionUtil
import org.nexial.core.NexialConst.GSON
import org.nexial.core.model.BrowserDevice
import org.nexial.core.model.BrowserDeviceType
import org.nexial.core.model.BrowserMeta
import org.nexial.core.model.BrowserOS
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils

class UserStackAPI(apiKeys: MutableList<String> = mutableListOf("5b71975a107de30d26f3878fa9adbb5e")) {
    private val keys = apiKeys

    @Throws(IllegalArgumentException::class)
    fun detectAsBrowserMeta(ua: String): BrowserMeta {
        if (keys.isEmpty())
            throw IllegalArgumentException("Unable to integrate with UserStack. Check previous logs for details")

        val apiKey = CollectionUtil.randomSelectOne(keys)
        val response = WebServiceClient(null).get("http://api.userstack.com/detect?access_key=${apiKey}&ua=${ua}", null)
        return if (response.returnCode == 200) {
            val json = GSON.fromJson(response.body, JsonObject::class.java)
            if (json.has("error")) {
                val error = json.getAsJsonObject("error")
                ConsoleUtils.log("Error when invoking UserStack: " +
                                 if (error.has("info")) error.get("info").asString else error.toString())
                keys.remove(apiKey)
                detectAsBrowserMeta(ua)
            } else {
                parseBrowserMeta(json, ua)
            }
        } else {
            throw IllegalArgumentException(response.statusText)
        }
    }

    internal fun parseBrowserMeta(json: JsonObject, ua: String): BrowserMeta {
        val os = json.getAsJsonObject("os")
        val device = json.getAsJsonObject("device")
        val browser = json.getAsJsonObject("browser")

        val version = if (browser.get("version").isJsonNull) "" else browser.getAsJsonPrimitive("version").asString
        val divBrand = if (device.get("brand").isJsonNull) "" else device.getAsJsonPrimitive("brand").asString
        val devName = if (device.get("name").isJsonNull) "" else device.getAsJsonPrimitive("name").asString

        return BrowserMeta(browser.get("name").asString, version, ua,
                           BrowserOS(os.get("name").asString, os.get("code").asString, os.get("family").asString),
                           BrowserDevice(BrowserDeviceType.valueOf(device.get("type").asString), divBrand, devName))
    }
}