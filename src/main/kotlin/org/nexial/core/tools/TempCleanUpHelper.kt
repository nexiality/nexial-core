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

package org.nexial.core.tools

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.SystemUtils.USER_HOME
import org.json.JSONObject
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.GSON
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.JsonUtils
import java.io.File
import java.io.File.separator

data class TempCleanUpManifest(var lastChecked: Long = 0, val checkFrequencyDay: Int = 6)

object TempCleanUpHelper {
    private val cleanUpManifest = File("$USER_HOME$separator.nexial${separator}config.json")

    // 1 day(24 hrs) => 86400000L milliseconds
    private const val dayInMilliseconds = 86400000L

    @JvmStatic
    fun cleanUpTemp() {
        var json = JSONObject()
        val manifest = if (cleanUpManifest.exists() && cleanUpManifest.canRead()) {
            json = JSONObject(FileUtils.readFileToString(cleanUpManifest, DEF_CHARSET))
            if (json.has("cleanUp")) {
                GSON.fromJson(json.getJSONObject("cleanUp").toString(), TempCleanUpManifest::class.java)
            } else {
                TempCleanUpManifest()
            }
        } else {
            // first time
            TempCleanUpManifest()
        }

        if (isCleanUpNeeded(manifest)) {
            ConsoleUtils.log("Cleaning up the temp files")
            TempCleanUp.cleanTempFiles(false)

            manifest.lastChecked = System.currentTimeMillis()
            updateConfig(json, manifest)
        }
    }

    private fun isCleanUpNeeded(manifest: TempCleanUpManifest): Boolean {
        return manifest.lastChecked == 0L ||
                ((System.currentTimeMillis() - manifest.lastChecked) > (dayInMilliseconds * manifest.checkFrequencyDay))
    }

    private fun updateConfig(json: JSONObject, manifest: TempCleanUpManifest) {
        json.put("cleanUp", JSONObject(manifest))
        FileUtils.writeStringToFile(cleanUpManifest, JsonUtils.beautify(json.toString()), DEF_CHARSET)
    }
}

