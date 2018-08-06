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

import org.apache.commons.collections4.MapUtils
import org.apache.commons.collections4.map.HashedMap
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.SystemUtils.*
import org.apache.commons.lang3.math.NumberUtils
import org.json.JSONArray
import org.json.JSONObject
import org.nexial.commons.proc.ProcessInvoker
import org.nexial.commons.utils.CollectionUtil
import org.nexial.commons.utils.EnvUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.FileUtil.isFileReadable
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.BrowserType.*
import org.nexial.core.NexialConst.Data.WIN32_CMD
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.plugins.xml.XmlCommand
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtil
import org.nexial.core.utils.JSONPath
import java.io.File
import java.io.File.separator
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.util.*
import java.util.zip.GZIPInputStream

abstract class WebDriverHelper protected constructor(protected var context: ExecutionContext) {
    protected lateinit var browserType: BrowserType
    protected lateinit var config: WebDriverConfig
    protected lateinit var driverLocation: String
    protected lateinit var driverManifest: File

    @Throws(IOException::class)
    fun resolveDriver(): File {
        // check if local copy of driver exists
        // if no local driver, poll online for driver
        // if local driver exists, check metadata for need to check for driver update
        downloadDriver(FileUtil.isFileReadable(driverLocation, DRIVER_MIN_SIZE))
        return File(driverLocation)
    }

    @Throws(IOException::class)
    protected fun downloadDriver(pollForUpdates: Boolean) {
        val manifest = resolveDriverManifest(pollForUpdates)

        // no url to download or no need to download... so we are done
        val driverUrl = manifest.driverUrl
        if (StringUtils.isNotBlank(driverUrl)) {
            // download driver to driver home (local)
            val wsClient = WebServiceClient(context)

            // download url might not be the actual driver, but zip or gzip
            val downloadTo = when {
                driverUrl!!.endsWith(".tar.gz") -> "$driverLocation.tar.gz"
                driverUrl.endsWith(".gz")       -> "$driverLocation.gz"
                driverUrl.endsWith(".zip")      -> "$driverLocation.zip"
                else                            -> driverLocation
            }

            val response = wsClient.download(driverUrl, null, downloadTo)
            if (response.returnCode >= 400) {
                throw IOException("Unable to download driver for $browserType from $driverUrl: ${response.statusText}")
            }

            if (!FileUtil.isFileReadable(downloadTo, DRIVER_MIN_SIZE)) {
                // download fail? disk out of space?
                throw IOException("Unable to download/save driver for $browserType from $driverUrl")
            }

            when {
                StringUtils.endsWith(downloadTo, ".tar.gz") -> ungzipThenUntar(downloadTo, driverLocation)
                StringUtils.endsWith(downloadTo, ".gz")     -> ungzip(downloadTo, driverLocation)
                StringUtils.endsWith(downloadTo, ".zip")    -> unzip(downloadTo, driverLocation)
            }

            if (!FileUtil.isFileReadable(driverLocation, DRIVER_MIN_SIZE)) {
                // download fail? disk out of space?
                throw IOException("Unable to download/save driver for $browserType from $driverUrl")
            }

            File(driverLocation).setExecutable(true)

            ConsoleUtils.log("[WebDriverHelper] webdriver for $browserType downloaded to $driverLocation")
            manifest.downloadAgent = ExecUtil.deriveJarManifest()
        }

        // update metadata
        manifest.lastChecked = System.currentTimeMillis()
        FileUtils.writeStringToFile(driverManifest, GSON.toJson(manifest, WebDriverManifest::class.java), DEF_CHARSET)
    }

    /**
     * default implementation for github or github-like releases
     *
     * @param pollForUpdates if true, then compare previously downloaded driver against the latest available
     * @return [WebDriverManifest] instance with transient information about driver download url and target version
     */
    @Throws(IOException::class)
    protected open fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        val manifest: WebDriverManifest = if (driverManifest.canRead() && driverManifest.length() > 10) {
            GSON.fromJson(FileUtils.readFileToString(driverManifest, DEF_CHARSET), WebDriverManifest::class.java)
        } else {
            // first time
            WebDriverManifest()
        }
        manifest.init()

        val hasDriver = isFileReadable(driverLocation, DRIVER_MIN_SIZE)

        // never check is turned on and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        if (pollForUpdates && manifest.lastChecked + config.checkFrequency > System.currentTimeMillis()) {
            // we still have time.. no need to check now
            return manifest
        }
        // else, need to check online, poll online for newer driver

        // first ws call to check existing/available versions of this driver
        val wsClient = WebServiceClient(context)
        val response = wsClient.get(config.checkUrlBase, null)
        val availableDriverContent = response.body

        val driverContentJon = JSONArray(availableDriverContent)
        val tags = JSONPath.find(driverContentJon, "tag_name")
        val tagNumbers = toReleaseNumberMap(StringUtils.substringBetween(tags, "[", "]"))

        val tagNumbersSorted = CollectionUtil.toList(tagNumbers.keys)
        tagNumbersSorted.sortWith(comparator = Comparator.reverseOrder())

        // persist the date/time when we last checked online
        manifest.lastChecked = System.currentTimeMillis()

        if (!pollForUpdates || manifest.driverVersionExpanded < tagNumbersSorted[0]) {
            val targetVersion = tagNumbers[tagNumbersSorted[0]]
            manifest.driverVersion = targetVersion

            // get latest
            val assetRegex = when {
                IS_OS_WINDOWS -> "win64"
                IS_OS_MAC_OSX -> "macos"
                IS_OS_LINUX   -> "linux64"
                else          -> ""
            }
            var driverUrl = JSONPath.find(driverContentJon, "[tag_name=" + targetVersion + "]" +
                                                            ".assets[name=REGEX:.+" + assetRegex + ".+]" +
                                                            ".browser_download_url")
            if (StringUtils.isBlank(driverUrl) && IS_OS_WINDOWS) {
                // try again with Win32
                driverUrl = JSONPath.find(driverContentJon, "[tag_name=" + targetVersion + "]" +
                                                            ".assets[name=REGEX:.+win32.+]" +
                                                            ".browser_download_url")
            }

            manifest.driverUrl = driverUrl
        }

        return manifest
    }

    protected fun initConfig(): WebDriverConfig {
        val configs = context.webdriverHelperConfig

        if (MapUtils.isEmpty(configs)) {
            val error = "No WebDriver configurations found"
            ConsoleUtils.log(error)
            throw IllegalArgumentException(error)
        }

        var configString = configs[browserType]
        if (StringUtils.isBlank(configString)) {
            val error = "Configuration not supported for browser $browserType"
            ConsoleUtils.log(error)
            throw IllegalArgumentException(error)
        }

        configString = context.replaceTokens(configString)
        configString = StringUtils.replace(configString, "\\", "/")
        val config = GSON.fromJson(configString, WebDriverConfig::class.java)
        config.init()
        return config
    }

    protected fun toReleaseNumberMap(tags: String): Map<Double, String> {
        val map = HashedMap<Double, String>()
        val tagStrings = StringUtils.split(tags, ",")
        Arrays.stream(tagStrings).forEach { tag -> map[expandVersion(tag)] = StringUtils.unwrap(tag, "\"") }
        return map
    }

    protected abstract fun resolveLocalDriverPath(): String

    companion object {
        const val DRIVER_MIN_SIZE: Long = 1024 * 50
        protected const val MANIFEST = ".manifest"

        @Throws(IOException::class)
        fun newInstance(browserType: BrowserType, context: ExecutionContext): WebDriverHelper {
            // sanity check
//            if (context == null) throw IllegalArgumentException("browserType cannot be null")
//            if (browserType == null) throw IllegalArgumentException("browserType cannot be null")
            if (browserType == safari || browserType == browserstack || browserType == iphone) {
                throw IllegalArgumentException("No WebDriverHelper implementation needed/available for $browserType")
            }

            val helper: WebDriverHelper = when (browserType) {
                edge                                   -> EdgeDriverHelper(context)
                firefox, firefoxheadless               -> FirefoxDriverHelper(context)
                electron                               -> ElectronDriverHelper(context)
                chrome, chromeheadless, chromeembedded -> ChromeDriverHelper(context)
                ie                                     -> IEDriverHelper(context)

            // todo: add more browserType-specific helper instantiation
                else                                   -> throw RuntimeException("No WebDriverHelper implemented for $browserType")
            }

            helper.browserType = browserType

            // 0. fetch config based on browser type
            val config = helper.initConfig()
            helper.config = config

            val driverHome = File(context.replaceTokens(config.home))
            FileUtils.forceMkdir(driverHome)

            helper.driverLocation = helper.resolveLocalDriverPath()
            helper.driverManifest = File(StringUtils.appendIfMissing(driverHome.absolutePath, separator) + MANIFEST)

            return helper
        }

        @Throws(IOException::class)
        protected fun ungzipThenUntar(gzipFile: String, uncompressedFile: String) {
            if (StringUtils.isBlank(gzipFile)) return
            if (StringUtils.isBlank(uncompressedFile)) return

            val tarFile = "$uncompressedFile.tar"
            ungzip(gzipFile, tarFile)

            val tarFileObject = File(tarFile)
            val dir = tarFileObject.parentFile
            if (!dir.exists()) dir.mkdirs()

            TarArchiveInputStream(FileInputStream(tarFile)).use { fin ->
                var entry: TarArchiveEntry? = fin.nextTarEntry
                while (entry != null) {
                    if (!entry.isDirectory) IOUtils.copy(fin, FileOutputStream(File(dir, entry.name)))

                    entry = fin.nextTarEntry
                    if (entry == null) break
                }
            }

            FileUtils.deleteQuietly(tarFileObject)
        }

        @Throws(IOException::class)
        protected fun ungzip(gzipFile: String, uncompressedFile: String) {
            if (StringUtils.isBlank(gzipFile)) return
            if (StringUtils.isBlank(uncompressedFile)) return

            val buffer = ByteArray(1024)

            GZIPInputStream(FileInputStream(gzipFile)).use { gzis ->
                FileOutputStream(uncompressedFile).use { out ->
                    var len: Int = gzis.read(buffer)
                    while (true) {
                        if (len < 1) break

                        out.write(buffer, 0, len)
                        len = gzis.read(buffer)
                    }
                }
            }

            FileUtils.deleteQuietly(File(gzipFile))
        }

        @Throws(IOException::class)
        protected fun unzip(zipFile: String, uncompressedFile: String) {
            if (StringUtils.isBlank(zipFile)) return
            if (StringUtils.isBlank(uncompressedFile)) return

            val expectedFile = File(uncompressedFile)
            FileUtil.unzip(File(zipFile), expectedFile.parentFile, listOf(expectedFile))

            FileUtils.deleteQuietly(File(zipFile))
        }

        fun expandVersion(version: String?): Double {
            if (StringUtils.isBlank(version)) return 0.0

            val parts = ArrayList(Arrays.asList(*StringUtils.split(version, ".")))

            var fractionPart = "0"
            if (parts.size > 2) {
                fractionPart = StringUtils.leftPad(RegexUtils.retainMatches(parts.removeAt(parts.size - 1), "[0-9]"),
                                                   4, "0")
            }

            var wholePart = ""
            for (part in parts) wholePart += StringUtils.leftPad(RegexUtils.retainMatches(part, "[0-9]"), 4, "0")

            return NumberUtils.toDouble("$wholePart.$fractionPart")
        }
    }
}

/**
 * webdriver helper for Edge browser
 */
class EdgeDriverHelper(context: ExecutionContext) : WebDriverHelper(context) {

    override fun resolveLocalDriverPath(): String {
        if (!IS_OS_WINDOWS_10 || !EnvUtils.isRunningWindows64bit()) {
            throw RuntimeException("current operating system does not support Microsoft Edge browser")
        }

        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               config.baseName
    }

    /**
     * for edge driver, the driver version and download url are based on Windows 10 OS version.
     *
     * Also, it doesn't make sense to consider `checkOnline` argument since we would only download new driver
     * when there's a mismatch of Windows 10 build number (from current driver).
     *
     * @param pollForUpdates IGNORED
     */
    @Throws(IOException::class)
    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        val manifest: WebDriverManifest = if (FileUtil.isFileReadable(driverManifest, 10)) {
            GSON.fromJson(FileUtils.readFileToString(driverManifest, DEF_CHARSET), WebDriverManifest::class.java)
        } else {
            // first time
            WebDriverManifest()
        }
        manifest.init()

        val hasDriver = FileUtil.isFileReadable(driverLocation, WebDriverHelper.DRIVER_MIN_SIZE)

        // find current OS build of Windows 10
        var currentOsVer = deriveWin10BuildNumber()

        if (!hasDriver || !StringUtils.equals(manifest.driverVersion, currentOsVer)) {
            // gotta download; doesn't matter if current is greater or lesser than manifest's
            val wsClient = WebServiceClient(context)
            var lookupResponse = wsClient.get("${config.checkUrlBase}$currentOsVer.txt", "")
            if (lookupResponse.returnCode != 200) {
                // something's wrong... maybe we don't have any driver for current OS build
                ConsoleUtils.log("[EDGE] unable to resolve $browserType driver download URL for Windows 10 build " +
                                 "$currentOsVer. Use min. version instead...")
                currentOsVer = minOsVersion
                lookupResponse = wsClient.get("${config.checkUrlBase}$currentOsVer.txt", "")
            }

            val downloadUrl = StringUtils.trim(lookupResponse.body)
            ConsoleUtils.log("[EDGE] derived download URL as $downloadUrl")

            manifest.driverUrl = downloadUrl
            manifest.driverVersion = currentOsVer
            manifest.lastChecked = System.currentTimeMillis()
        }

        return manifest
    }

    companion object {
        // first version of edge browser as min/failback version
        private val minOsVersionNum = 10240
        private val minOsVersion = "" + minOsVersionNum

        private val winVer = mutableListOf("/C", "ver")
        private val winVerRegex1 = "10\\.0\\.(\\d+)\\.(\\d+)"
        private val winVerRegex2 = "10\\.0\\.(\\d+)"
        private val winVerRegex3 = "([\\d\\.]+)"

        fun deriveWin10BuildNumber(): String {
            try {
                val outcome = ProcessInvoker.invoke(WIN32_CMD, winVer, HashedMap())
                // e.g. Microsoft Windows [Version 10.0.10240]

                val currentOsVer = outcome.stdout
                ConsoleUtils.log("[EDGE] current Windows 10 version = $currentOsVer")

                return deriveWin10BuildNumber(StringUtils.substringBetween(currentOsVer, "[Version", "]"),
                                              minOsVersionNum)
//            ConsoleUtils.log("[EDGE] current Windows 10 OS Build number resolved to $currentOsVer")
//            return currentOsVer
            } catch (e: Exception) {
                throw RuntimeException("Unable to determine OS Build number for current Windows 10: ${e.message}")
            }
        }

        fun deriveWin10BuildNumber(currentOsVer: String, minVersion: Int): String {
            val osVer = StringUtils.trim(
                when {
                    RegexUtils.isExact(currentOsVer, winVerRegex1) -> RegexUtils.replace(currentOsVer,
                                                                                         winVerRegex1,
                                                                                         "$1")
                    RegexUtils.isExact(currentOsVer, winVerRegex2) -> RegexUtils.replace(currentOsVer,
                                                                                         winVerRegex2,
                                                                                         "$1")
                    RegexUtils.isExact(currentOsVer, winVerRegex3) -> StringUtils.substringAfterLast(currentOsVer, ".")
                    else                                           -> currentOsVer
                })

            if (!NumberUtils.isDigits(osVer)) return minVersion.toString()

            val currentOsVerNum = NumberUtils.toInt(osVer)
            if (currentOsVerNum >= minVersion) return osVer

            ConsoleUtils.log("[EDGE] current Windows 10 OS Build number not supported: $currentOsVer")
            return minVersion.toString()
        }
    }
}

/**
 * webdriver helper for firefox and firefox headless browser
 */
class FirefoxDriverHelper(context: ExecutionContext) : WebDriverHelper(context) {

    override fun resolveLocalDriverPath(): String {
        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               config.baseName + if (IS_OS_WINDOWS) ".exe" else ""
    }
}

/**
 * webdriver helper for Chrome-Electron (chromium)
 * @constructor
 */
class ElectronDriverHelper(context: ExecutionContext) : WebDriverHelper(context) {

    override fun resolveLocalDriverPath(): String {
        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               config.baseName + if (IS_OS_WINDOWS) ".exe" else ""
    }

    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        val manifest: WebDriverManifest = if (driverManifest.canRead() && driverManifest.length() > 10) {
            GSON.fromJson(FileUtils.readFileToString(driverManifest, DEF_CHARSET), WebDriverManifest::class.java)
        } else {
            // first time
            WebDriverManifest()
        }
        manifest.init()

        val hasDriver = isFileReadable(driverLocation, DRIVER_MIN_SIZE)

        // never check is turned on and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        if (pollForUpdates && manifest.lastChecked + config.checkFrequency > System.currentTimeMillis()) {
            // we still have time.. no need to check now
            return manifest
        }
        // else, need to check online, poll online for newer driver

        // first ws call to check existing/available versions of this driver
        val wsClient = WebServiceClient(context)
        val response = wsClient.get(config.checkUrlBase, null)

        val driverContentJson = JSONObject(response.body)
        val tag = JSONPath.find(driverContentJson, "tag_name")
        if (!pollForUpdates || manifest.driverVersionExpanded < WebDriverHelper.expandVersion(tag)) {

            // persist the date/time when we last checked online
            manifest.lastChecked = System.currentTimeMillis()
            manifest.driverVersion = tag

            val env = when {
                IS_OS_WINDOWS -> "win32"
                IS_OS_LINUX   -> "linux"
                IS_OS_MAC     -> "darwin"
                else          -> throw IllegalArgumentException("OS ${SystemUtils.OS_NAME} not supported for $browserType")
            }
            val arch = when (EnvUtils.getOsArchBit()) {
                32   -> "ia32"
                64   -> "x64"
                else -> "ia32"
            }

            manifest.driverUrl = JSONPath.find(driverContentJson,
                                               "assets[name=chromedriver-$tag-$env-$arch.zip].browser_download_url")
        }

        return manifest
    }
}

/**
 * webdriver helper for Chrome browser
 */
class ChromeDriverHelper(context: ExecutionContext) : WebDriverHelper(context) {

    private val minDriverVersion = "2.35"

    override fun resolveLocalDriverPath(): String {
        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               config.baseName + if (IS_OS_WINDOWS) ".exe" else ""
    }

    @Throws(IOException::class)
    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        val manifest: WebDriverManifest = if (FileUtil.isFileReadable(driverManifest, 10)) {
            GSON.fromJson(FileUtils.readFileToString(driverManifest, DEF_CHARSET), WebDriverManifest::class.java)
        } else {
            // first time
            WebDriverManifest()
        }
        manifest.init()

        val hasDriver = FileUtil.isFileReadable(driverLocation, WebDriverHelper.DRIVER_MIN_SIZE)

        if (!hasDriver) {
            var driverVersion = minDriverVersion

            val wsClient = WebServiceClient(context)
            val lookupResponse = wsClient.get("${config.checkUrlBase}/LATEST_RELEASE", "")
            if (lookupResponse.returnCode != 200) {
                // something's wrong...
                ConsoleUtils.log("[Chrome] unable to resolve latest version for $browserType. " +
                                 "Use min. driver version instead...")
            } else {
                driverVersion = lookupResponse.body
            }

            val env = when {
                IS_OS_WINDOWS -> "win32"
                IS_OS_LINUX   -> "linux64"
                IS_OS_MAC     -> "mac64"
                else          -> throw IllegalArgumentException("OS ${SystemUtils.OS_NAME} not supported for $browserType")
            }

            val downloadUrl = "${config.checkUrlBase}/$driverVersion/${config.baseName}_$env.zip"
            ConsoleUtils.log("[Chrome] derived download URL as $downloadUrl")

            manifest.driverUrl = downloadUrl
            manifest.driverVersion = driverVersion
            manifest.lastChecked = System.currentTimeMillis()
        }

        return manifest
    }
}

/**
 * webdriver helper for IE browser
 */
class IEDriverHelper(context: ExecutionContext) : WebDriverHelper(context) {

    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        val manifest: WebDriverManifest = if (driverManifest.canRead() && driverManifest.length() > 10) {
            GSON.fromJson(FileUtils.readFileToString(driverManifest, DEF_CHARSET), WebDriverManifest::class.java)
        } else {
            // first time
            WebDriverManifest()
        }
        manifest.init()

        val hasDriver = isFileReadable(driverLocation, DRIVER_MIN_SIZE)

        // never check is turned on and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        if (pollForUpdates && manifest.lastChecked + config.checkFrequency > System.currentTimeMillis()) {
            // we still have time.. no need to check now
            return manifest
        }
        // else, need to check online, poll online for newer driver

        // first ws call to check existing/available versions of this driver
        val wsClient = WebServiceClient(context)
        val response = wsClient.get(config.checkUrlBase, null)
        // todo: handle to set min version if failed to fetch latest version
        val xmlPayload = response.body
        val archKey = when (EnvUtils.getOsArchBit()) {
            32   -> "Win32"
            64   -> "x64"
            else -> "Win32"
        }

        val generationsIE = "//*[local-name()='Key'][contains(text(),'IEDriverServer_$archKey')]//following-sibling::" +
                            "*[local-name()='Generation']/text()"

        val xmlCommand = XmlCommand()
        xmlCommand.init(context)
        val generationsList = xmlCommand.getValuesListByXPath(xmlPayload, generationsIE)
        val generations = mutableListOf<BigInteger>()
        generationsList.forEach { key -> generations.add(key.toBigInteger()) }
        val latestGenKey = generations.max().toString()
        val xpathKey = "//*[local-name()='Generation'][text()='$latestGenKey']//preceding-sibling::" +
                       "*[local-name()='Key']/text()"
        val key = xmlCommand.getValueByXPath(xmlPayload, xpathKey)
        val tag = key.split("/")[0]

        if (!pollForUpdates || manifest.driverVersionExpanded < WebDriverHelper.expandVersion(tag)) {
            // persist the date/time when we last checked online
            manifest.lastChecked = System.currentTimeMillis()
            manifest.driverVersion = tag
            manifest.driverUrl = "${config.checkUrlBase}/$key"
        }
        return manifest
    }

    override fun resolveLocalDriverPath(): String {

        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               config.baseName + ".exe"
    }
}