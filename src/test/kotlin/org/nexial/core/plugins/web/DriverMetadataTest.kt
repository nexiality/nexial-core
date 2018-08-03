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

import org.junit.Assert
import org.junit.Test
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.GSON_COMPRESSED

class DriverMetadataTest {

    @Test
    fun testSerialization() {
        val metadata = WebDriverManifest()
        metadata.downloadAgent = "nexial-dev_0107"
        metadata.driverVersion = "3.7.1"
        metadata.lastChecked = 348274786523L

        val json = GSON_COMPRESSED.toJson(metadata)
        println("json = $json")

        // never Check has default of false
        Assert.assertEquals("{" +
                            "\"driverVersion\":\"3.7.1\"," +
                            "\"lastChecked\":348274786523," +
                            "\"downloadAgent\":\"nexial-dev_0107\"," +
                            "\"neverCheck\":false" +
                            "}",
                            json)
    }

    @Test
    fun testDeserialization() {
        val json = "{" +
                   " \"driverVersion\"   :\"3.7.1\"," +
                   " \"lastChecked\"     :348274786523," +
                   " \"downloadAgent\"   :\"nexial-dev_0107\"," +
                   " \"browserVersion\"  :\"68.0\"" +
                   "}"

        val metadata = GSON.fromJson(json, WebDriverManifest::class.java)
        Assert.assertNotNull(metadata)

        metadata.init()

        Assert.assertEquals("nexial-dev_0107", metadata.downloadAgent)
        Assert.assertEquals("3.7.1", metadata.driverVersion)
        Assert.assertEquals(348274786523L, metadata.lastChecked)
        Assert.assertEquals(30007.0001, metadata.driverVersionExpanded, 0.0)
        Assert.assertEquals(false, metadata.neverCheck)
    }
}