/*
 * Copyright 2012-2021 the original author or authors.
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

@file:Suppress("invisible_reference", "invisible_member")
package org.nexial.core.tools

import org.apache.commons.io.FileUtils
import org.junit.Assert
import org.junit.Test
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst
import org.nexial.core.tools.NexialLibDownloader.LIB_VERSION_FILENAME
import org.nexial.core.tools.NexialLibDownloader.nexialHome
import org.nexial.core.tools.NexialLibDownloader.nexialLibLoc
import java.io.File

class NexialLibDownloaderManualTest {

    @Test
    fun updateLibrary() {
        nexialHome = File("").absolutePath
        NexialLibDownloader.updateLibrary()
        val requiredVersion = NexialLibDownloader.getRequiredNexialLibVersion()
        val userHomeNexialLibFilePath = "$nexialLibLoc$LIB_VERSION_FILENAME"
        var existingVersion = ""
        if (FileUtil.isFileReadable(File(userHomeNexialLibFilePath))) {
            existingVersion = FileUtils
                .readFileToString(File(userHomeNexialLibFilePath), NexialConst.DEF_CHARSET)
                .trim()
        }
        Assert.assertEquals(requiredVersion, existingVersion)

    }
}