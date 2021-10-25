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

package org.nexial.core.plugins.browserstack

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.*
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.web.WebDriverHelper
import org.nexial.core.plugins.web.WebDriverManifest
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.IOException

class BrowserStackLocalHelper(context: ExecutionContext) : WebDriverHelper(context) {
    override fun resolveLocalDriverPath(): String {
        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, File.separator) +
               config.baseName + if (IS_OS_WINDOWS) ".exe" else ""
    }

    @Throws(IOException::class)
    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        val (manifest: WebDriverManifest, hasDriver) = initManifestAndCheckDriver()

        // never check is turned on, and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        if (pollForUpdates && manifest.lastChecked + config.checkFrequency > System.currentTimeMillis()) {
            // we still have time... no need to check now
            return manifest
        }
        // else, need to check online, poll online for newer driver

        val env = when {
            IS_OS_WINDOWS -> "win32"
            IS_OS_LINUX   -> "linux-x64"
            IS_OS_MAC     -> "darwin-x64"
            else          -> throw IllegalArgumentException("OS $OS_NAME not supported for $browserType")
        }

        val downloadUrl = "${config.checkUrlBase}/${config.baseName}-$env.zip"
        ConsoleUtils.log("[BrowserStackLocal] derived download URL as $downloadUrl")

        manifest.driverUrl = downloadUrl
        manifest.lastChecked = System.currentTimeMillis()

        return manifest
    }
}