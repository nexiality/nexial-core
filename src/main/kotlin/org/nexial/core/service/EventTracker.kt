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

package org.nexial.core.service

import org.apache.commons.codec.binary.Hex
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.model.NexialEnv
import org.nexial.core.model.NexialEvent
import org.nexial.core.service.EventUtils.postfix
import org.nexial.core.service.EventUtils.storageLocation
import java.io.File
import java.io.File.separator
import java.text.SimpleDateFormat
import java.util.*

object EventTracker {
    private val eventFileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss.SSS.")

    fun track(event: NexialEvent) = write(event.eventName, event.json())

    fun track(env: NexialEnv) = write("env", env.json())

    private fun write(type: String, content: String) {
        val file = File(storageLocation +
                        RandomStringUtils.randomAlphabetic(5) + "." +
                        eventFileDateFormat.format(Date()) + "." +
                        type + postfix)
        FileUtils.forceMkdirParent(file)
        FileUtils.write(file, content, DEF_FILE_ENCODING)
    }
}

object EventUtils {
    internal val storageLocation = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().absolutePath, separator) +
                                   Hex.encodeHexString("Nexial_Event".toByteArray()) + separator
    internal const val postfix = ".json"

//    init {
//        println("storageLocation = $storageLocation")
//    }
}