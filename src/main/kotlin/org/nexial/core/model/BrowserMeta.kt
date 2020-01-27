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

package org.nexial.core.model

import com.google.gson.JsonObject
import org.nexial.core.NexialConst.GSON

/**
 * encapsulation of what userstack returns back based on user-agent string. We are cherry-picking only the meaningful
 * ones, which will be stored in ExecutionContent
 */
data class BrowserMeta(val name: String,
                       val version: String,
                       val userAgent: String,
                       val os: BrowserOS,
                       val device: BrowserDevice) {

    fun browser() = ("$name $version").trim()

    companion object {
        @JvmStatic
        fun toBrowserMeta(json: JsonObject) = GSON.fromJson(json, BrowserMeta::class.java)

        @JvmStatic
        fun fromBrowserMeta(browserMeta: BrowserMeta) = GSON.fromJson(GSON.toJson(browserMeta), JsonObject::class.java)
    }
}

data class BrowserOS(val name: String, val code: String, val family: String)

data class BrowserDevice(val type: BrowserDeviceType, val brand: String?, val name: String?)

enum class BrowserDeviceType {
    desktop {
        override fun isMobileDevice() = false
    },
    tablet {
        override fun isMobileDevice() = true
    },
    smartphone {
        override fun isMobileDevice() = true
    },
    console {
        override fun isMobileDevice() = false
    },
    smarttv {
        override fun isMobileDevice() = false
    },
    wearable {
        override fun isMobileDevice() = true
    },
    unknown {
        override fun isMobileDevice() = false
    };

    abstract fun isMobileDevice(): Boolean
}