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
import org.junit.Assert
import org.junit.Test
import org.nexial.core.NexialConst.GSON

class UserStackAPITest {

    @Test
    fun parseBrowserMeta_electron() {
        val ua = """Mozilla\/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit\/537.36 (KHTML, like Gecko) MovieMagicBudgeting\/0.1.7 Chrome\/76.0.3809.131 Electron\/6.0.4 Safari\/537.36"""
        val fixture = """
            {
                "ua":"$ua",
                "type":"browser",
                "brand":"Apple",
                "name":"Mac",
                "url":"https:\/\/github.com\/about",
                "os":{
                    "name":"macOS 10.14 Mojave",
                    "code":"macos_10_14",
                    "url":"https:\/\/en.wikipedia.org\/wiki\/MacOS_Mojave",
                    "family":"macOS",
                    "family_code":"macos",
                    "family_vendor":"Apple Inc.",
                    "icon":"https:\/\/assets.userstack.com\/icon\/os\/macosx.png",
                    "icon_large":"https:\/\/assets.userstack.com\/icon\/os\/macosx_big.png"
                },
                "device":{
                    "is_mobile_device":false,
                    "type":"desktop",
                    "brand":"Apple",
                    "brand_code":"apple",
                    "brand_url":"https:\/\/www.apple.com\/",
                    "name":"Mac"
                },
                "browser":{
                    "name":"Electron App",
                    "version":null,
                    "version_major":null,
                    "engine":"WebKit"
                },
                "crawler":{
                    "is_crawler":false,
                    "category":null,
                    "last_seen":null
                } 
            }
        """.trimIndent()

        val browserMeta = UserStackAPI().parseBrowserMeta(GSON.fromJson(fixture, JsonObject::class.java), ua)
        Assert.assertNotNull(browserMeta)
        Assert.assertEquals("Electron App", browserMeta.browser())
        Assert.assertEquals("macOS 10.14 Mojave", browserMeta.os.name)
    }

    @Test
    fun parseBrowserMeta_chrome() {
        val ua = """Mozilla\/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit\/537.36 (KHTML, like Gecko) Chrome\/78.0.3904.87 Safari\/537.36"""
        val fixture = """
            {
                "ua":"$ua",
                "type":"browser",
                "brand":"Apple",
                "name":"Mac",
                "url":"https:\/\/www.google.com\/about\/company\/",
                "os":{
                    "name":"macOS 10.14 Mojave",
                    "code":"macos_10_14",
                    "url":"https:\/\/en.wikipedia.org\/wiki\/MacOS_Mojave",
                    "family":"macOS",
                    "family_code":"macos",
                    "family_vendor":"Apple Inc.",
                    "icon":"https:\/\/assets.userstack.com\/icon\/os\/macosx.png",
                    "icon_large":"https:\/\/assets.userstack.com\/icon\/os\/macosx_big.png"
                },
                "device":{
                    "is_mobile_device":false,
                    "type":"desktop",
                    "brand":"Apple",
                    "brand_code":"apple",
                    "brand_url":"https:\/\/www.apple.com\/",
                    "name":"Mac"
                },
                "browser":{
                    "name":"Chrome",
                    "version":"78.0.3904.87",
                    "version_major":"78",
                    "engine":"WebKit\/Blink"
                },
                "crawler":{
                    "is_crawler":false,
                    "category":null,
                    "last_seen":null
                }
            }
        """.trimIndent()

        val browserMeta = UserStackAPI().parseBrowserMeta(GSON.fromJson(fixture, JsonObject::class.java), ua)
        Assert.assertNotNull(browserMeta)
        Assert.assertEquals("Chrome 78.0.3904.87", browserMeta.browser())
        Assert.assertEquals("macOS 10.14 Mojave", browserMeta.os.name)
    }
}