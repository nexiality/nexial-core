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

package org.nexial.core.services.external

import com.google.gson.JsonObject
import org.apache.commons.lang3.SystemUtils.OS_ARCH
import org.apache.commons.lang3.SystemUtils.OS_NAME
import org.nexial.commons.utils.CollectionUtil
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.GSON
import org.nexial.core.model.BrowserDevice
import org.nexial.core.model.BrowserDeviceType
import org.nexial.core.model.BrowserDeviceType.unknown
import org.nexial.core.model.BrowserMeta
import org.nexial.core.model.BrowserOS
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils

class UserStackAPI(val url: String, val apiKeys: MutableList<String>) {

    fun detectAsBrowserMeta(ua: String): BrowserMeta {
        val dummy = newDummyBrowserMeta(ua)
        // could fail -> if all the available keys are invalid.. or network issue
        // but we mustn't hold up the execution
        if (apiKeys.isEmpty()) return dummy

        val apiKey = CollectionUtil.randomSelectOne(apiKeys)
        return try {
            val response = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
                .get(TextUtils.replaceTokens(url, "{", "}", mapOf("accessKey" to apiKey, "ua" to ua)), null)
            if (response.returnCode == 200) {
                val json = GSON.fromJson(response.body, JsonObject::class.java)
                if (json.has("error")) {
                    val error = json.getAsJsonObject("error")
                    ConsoleUtils.log("Error when invoking UserStack: " +
                                     if (error.has("info")) error.get("info").asString else error.toString())
                    apiKeys.remove(apiKey)
                    detectAsBrowserMeta(ua)
                } else {
                    parseBrowserMeta(json, ua)
                }
            } else {
                // could fail.. if all the available keys are invalid.. or network issue
                // but we mustn't hold up the execution
                dummy
            }
        } catch (e: Exception) {
            // could fail -> if all the available keys are invalid.. or network issue
            // but we mustn't hold up the execution
            ConsoleUtils.error("Unable to determine browser metadata: ${e.message}")
            dummy
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
        val browser = json.getAsJsonObject("browser")
        val device = json.getAsJsonObject("device")
        return BrowserMeta(name = browser.get("name").asString,
                           version = getOrDefault(browser, "version"),
                           userAgent = ua,
                           os = BrowserOS(name = os.get("name").asString,
                                          code = os.get("code").asString,
                                          family = os.get("family").asString),
                           device = BrowserDevice(type = BrowserDeviceType.valueOf(device.get("type").asString),
                                                  brand = getOrDefault(device, "brand"),
                                                  name = getOrDefault(device, "name")))
    }

    private fun getOrDefault(json: JsonObject, key: String) =
        if (json.get(key).isJsonNull) "" else json.getAsJsonPrimitive(key).asString
}