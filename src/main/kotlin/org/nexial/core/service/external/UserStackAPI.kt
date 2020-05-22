/*
 * Copyright 2012-2018 the original author or authors.
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

package org.nexial.core.service.external

import com.google.gson.JsonObject
import org.apache.commons.lang3.SystemUtils.OS_ARCH
import org.apache.commons.lang3.SystemUtils.OS_NAME
import org.nexial.commons.utils.CollectionUtil
import org.nexial.core.NexialConst.Data.APIKEYS_USERSTACK
import org.nexial.core.NexialConst.GSON
import org.nexial.core.model.BrowserDevice
import org.nexial.core.model.BrowserDeviceType
import org.nexial.core.model.BrowserDeviceType.unknown
import org.nexial.core.model.BrowserMeta
import org.nexial.core.model.BrowserOS
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils

class UserStackAPI() {
    private val keys = APIKEYS_USERSTACK.toMutableList()
    private val urlTemplate = "http://api.userstack.com/detect?access_key=%s&ua=%s"

    fun detectAsBrowserMeta(ua: String): BrowserMeta {
        // could fail.. if all the available keys are invalid.. or network issue
        // but we mustn't hold up the execution
        if (keys.isEmpty()) return newDummyBrowserMeta(ua)

        val apiKey = CollectionUtil.randomSelectOne(keys)
        val response = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
            .get(urlTemplate.format(apiKey, ua), null)
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
            // could fail.. if all the available keys are invalid.. or network issue
            // but we mustn't hold up the execution
            newDummyBrowserMeta(ua)
        }
    }

    private fun newDummyBrowserMeta(ua: String): BrowserMeta =
            BrowserMeta("UNABLE TO DETERMINE",
                        "???",
                        ua,
                        BrowserOS(OS_NAME, OS_NAME, OS_ARCH),
                        BrowserDevice(unknown, "UNKNOWN", "UNKNOWN"))

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