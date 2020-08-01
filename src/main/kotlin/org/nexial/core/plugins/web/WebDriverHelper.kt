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
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.BrowserType.*
import org.nexial.core.NexialConst.Data.WIN32_CMD
import org.nexial.core.NexialConst.Web.OPT_FORCE_IE_32
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.plugins.xml.XmlCommand
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils.NEXIAL_MANIFEST
import org.nexial.core.utils.JSONPath
import java.io.*
import java.io.File.separator
import java.math.BigInteger
import java.util.*
import java.util.zip.GZIPInputStream

abstract class WebDriverHelper protected constructor(protected var context: ExecutionContext) {
    protected lateinit var browserType: BrowserType
    protected lateinit var config: WebDriverConfig
    protected lateinit var driverLocation: String
    protected lateinit var driverManifest: File
    lateinit var browserBinLocation: String
    var firefoxDriverVersionMapping: Map<String, String>? = null

    @Throws(IOException::class)
    fun resolveDriver(): File {
        // check if local copy of driver exists
        // if no local driver, poll online for driver
        // if local driver exists, check metadata for need to check for driver update
        try {
            downloadDriver(isFileReadable(driverLocation, DRIVER_MIN_SIZE))
        } catch (e: IOException) {
            ConsoleUtils.log("Unable to resolve or download webdriver: ${e.message}")
        }

        val driver = File(driverLocation)
        return if (!driver.exists())
            throw RuntimeException("Can't resolve/download driver for $browserType")
        else
            driver
    }

    @Throws(IOException::class)
    protected fun downloadDriver(pollForUpdates: Boolean) {
        val manifest = resolveDriverManifest(pollForUpdates)

        // no url to download or no need to download... so we are done
        if (StringUtils.isNotBlank(manifest.driverUrl)) {
            // download driver to driver home (local)
            val wsClient = newIsolatedWsClient()

            // download url might not be the actual driver, but zip or gzip
            val driverUrl = manifest.driverUrl!!
            val downloadTo = when {
                driverUrl.endsWith(".tar.gz") -> "$driverLocation.tar.gz"
                driverUrl.endsWith(".gz")     -> "$driverLocation.gz"
                driverUrl.endsWith(".zip")    -> "$driverLocation.zip"
                else                          -> driverLocation
            }

            val response = wsClient.download(driverUrl, null, downloadTo)
            if (response.returnCode >= 400) {
                throw IOException("Unable to download driver for $browserType from $driverUrl: ${response.statusText}")
            }

            if (!isFileReadable(downloadTo, DRIVER_MIN_SIZE)) {
                // download fail? disk out of space?
                throw IOException("Unable to download/save driver for $browserType from $driverUrl")
            }

            when {
                StringUtils.endsWith(downloadTo, ".tar.gz") -> ungzipThenUntar(downloadTo, driverLocation)
                StringUtils.endsWith(downloadTo, ".gz")     -> ungzip(downloadTo, driverLocation)
                StringUtils.endsWith(downloadTo, ".zip")    -> unzip(downloadTo, driverLocation)
            }

            if (!isFileReadable(driverLocation, DRIVER_MIN_SIZE)) {
                // download fail? disk out of space?
                throw IOException("Unable to download/save driver for $browserType from $driverUrl")
            }

            File(driverLocation).setExecutable(true)

            ConsoleUtils.log("[WebDriverHelper] webdriver for $browserType downloaded to $driverLocation")
            manifest.downloadAgent = NEXIAL_MANIFEST
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
        val (manifest: WebDriverManifest, hasDriver) = initManifestAndCheckDriver()

        // never check is turned on and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        if (pollForUpdates && manifest.lastChecked + config.checkFrequency > System.currentTimeMillis()) {
            // we still have time.. no need to check now
            return manifest
        }
        // else, need to check online, poll online for newer driver

        // first ws call to check existing/available versions of this driver
        val wsClient = newIsolatedWsClient()
        val response = wsClient.get(config.checkUrlBase, null)
        if (response.returnCode >= 400) {
            // error in checking online
            throw IOException("Error when accessing ${config.checkUrlBase}: ${response.statusText}")
        }

        val availableDriverContent = StringUtils.trim(response.body)
        when {
            TextUtils.isBetween(availableDriverContent, "[", "]") ->
                extractDriverInfos(JSONArray(availableDriverContent), manifest, pollForUpdates)
            TextUtils.isBetween(availableDriverContent, "{", "}") ->
                extractDriverInfo(JSONObject(availableDriverContent), manifest, pollForUpdates)
            else                                                  ->
                ConsoleUtils.error("Unknown content downloaded from ${config.checkUrlBase}; IGNORED...")
        }

        return manifest
    }

    protected open fun extractDriverInfos(json: JSONArray, manifest: WebDriverManifest, pollForUpdates: Boolean) {

        val tags = JSONPath.find(json, "tag_name")
        val tagNumbers = toReleaseNumberMap(StringUtils.substringBetween(tags, "[", "]"))

        val tagNumbersSorted = CollectionUtil.toList(tagNumbers.keys)
        tagNumbersSorted.sortWith(comparator = Comparator.reverseOrder())
        val latestVersion = tagNumbersSorted[0]

        if (!pollForUpdates || manifest.driverVersionExpanded < latestVersion) {
            val targetVersion = tagNumbers[latestVersion]
            resolveDownloadUrlFromJSONArray(manifest, targetVersion, json)
        }
    }

    protected fun updateManifestProperties(manifest: WebDriverManifest, driverUrl: String?, targetVersion: String?) {
        manifest.driverUrl = driverUrl
        manifest.lastChecked = System.currentTimeMillis()
        manifest.driverVersion = targetVersion
        manifest.compatibleDriverVersion = null
    }

    protected fun resolveDownloadUrlFromJSONArray(manifest: WebDriverManifest,
                                                  targetVersion: String?,
                                                  json: JSONArray) {
        // get latest
        val driverSearchName = resolveDriverSearchName(targetVersion!!)
        var driverUrl = JSONPath.find(json,
                                      "[tag_name=$targetVersion].assets[name=$driverSearchName].browser_download_url")
        if (StringUtils.isBlank(driverUrl) && IS_OS_WINDOWS) {
            // [corner case] try again with Win32
            driverUrl = JSONPath.find(json,
                                      "[tag_name=$targetVersion].assets[name=REGEX:.+win32.+].browser_download_url")
        }

        if (TextUtils.isBetween(driverUrl, "[", "]")) {
            // [corner case] multiple matches found.. most likely one with ".tar.gz" and the other one ".tar.gz.asc"
            val driverUrlList = JSONArray(driverUrl).map { it.toString() }.filter { !it.endsWith(".asc") }
            driverUrl = driverUrlList[0]
        }

        updateManifestProperties(manifest, driverUrl, targetVersion)
    }

    protected open fun extractDriverInfo(json: JSONObject, manifest: WebDriverManifest, pollForUpdates: Boolean) {
        val tag = JSONPath.find(json, "tag_name")
        if (!pollForUpdates || manifest.driverVersionExpanded < expandVersion(tag)) {
            resolveDownloadUrlFromJSONObject(manifest, tag, json)
        }
    }

    protected fun resolveDownloadUrlFromJSONObject(manifest: WebDriverManifest, tag: String, json: JSONObject) {
        val driverSearchName = resolveDriverSearchName(tag)
        val driverUrl = JSONPath.find(json, "assets[name=$driverSearchName].browser_download_url")
        updateManifestProperties(manifest, driverUrl, tag)
    }

    @Throws(IOException::class)
    open fun updateCompatibleDriverVersion(): Boolean {
        val currentBrowserType = context.browserType

        if (StringUtils.isNotBlank(browserBinLocation)) {
            var driverVersion: String?
            if (currentBrowserType.startsWith("chrome")) {
                driverVersion = execCommandForBrowserVersion(getChromeVerCommand(browserBinLocation))
                driverVersion = driverVersion!!
                    .replace("Version=", "").replace("Google Chrome ", "").trim { it <= ' ' }
            } else if (currentBrowserType.startsWith("firefox")) {
                driverVersion = execCommandForBrowserVersion(getFirefoxVerCommand(browserBinLocation))
                driverVersion = driverVersion!!
                    .replace("Mozilla Firefox ", "")
                    .replace("Firefox ", "")
                    .trim { it <= ' ' }

                var firefoxBrowserVer = 0
                if (StringUtils.isNotBlank(driverVersion) && driverVersion.contains(".")) {
                    firefoxBrowserVer = StringUtils
                        .substringBefore(driverVersion, ".").toInt()
                } else {
                    ConsoleUtils.error("Invalid driver version :$driverVersion")
                    return false
                }
                //find driver mapping configuration in nexial.xml
                for ((minMaxRange, value) in firefoxDriverVersionMapping!!.entries) {
                    val minBrowserVer = StringUtils
                        .substringBefore(minMaxRange, "-")
                        .toInt()
                    val maxBrowserVer = StringUtils
                        .substringAfter(minMaxRange, "-")
                        .toInt()
                    if (firefoxBrowserVer in minBrowserVer..maxBrowserVer) {
                        driverVersion = value
                        break
                    }
                }
            } else {
                ConsoleUtils.error("Invalid browser type for compatible driver mapping")
                return false
            }
            //var manifest: WebDriverManifest = initWebDriverManifest()
            val (manifest: WebDriverManifest, hasDriver) = initManifestAndCheckDriver()

            //if old & new both driver are same than stop execution
            //maybe something went wrong with browser initialization
            val manifestDriverVer = manifest.driverVersion
            if (hasDriver && manifestDriverVer != null
                && (manifestDriverVer.contains(driverVersion!!)
                    || StringUtils.substringBeforeLast(manifestDriverVer, ".")
                        .contains(StringUtils.substringBeforeLast(driverVersion,
                                                                  ".")))) {
                ConsoleUtils.log("Current web driver is compatible with the browser. No need to update.")
                return false
            }
            manifest.compatibleDriverVersion = driverVersion
            ConsoleUtils.log("required driver version : $driverVersion")
            FileUtils.writeStringToFile(driverManifest, GSON.toJson(manifest, WebDriverManifest::class.java),
                                        DEF_CHARSET)
        } else {
            ConsoleUtils.error("Could not find the $currentBrowserType browser.")
        }
        return true
    }

    private fun execCommandForBrowserVersion(
        cmd: Array<String>?): String? {
        var browserVersion: String? = ""
        try {
            val process = Runtime.getRuntime().exec(cmd)
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (StringUtils.isNotBlank(line)) browserVersion = line
                }
            } catch (e: Exception) {
                ConsoleUtils.error("Error while finding browser version: $e")
            }
        } catch (e: Exception) {
            ConsoleUtils.error("Error while executing command to find browser version: $e")
        }
        return browserVersion
    }

    private fun getChromeVerCommand(binaryLocation: String?): Array<String>? {
        var cmd: Array<String>? = null
        if (IS_OS_WINDOWS) {
            cmd = arrayOf("cmd.exe", "/C",
                          "wmic datafile where name=\"" +
                          binaryLocation!!.replace("\\", "\\\\") +
                          "\" get Version /value")
        } else if (IS_OS_LINUX || IS_OS_MAC) {
            cmd = arrayOf("bash", "-c", "google-chrome --version")
        }
        return cmd
    }

    private fun getFirefoxVerCommand(binaryLocation: String?): Array<String>? {
        var cmd: Array<String>? = null
        if (IS_OS_WINDOWS) {
            cmd = arrayOf("cmd.exe", "/C",
                          "wmic datafile where name=\"" +
                          binaryLocation!!.replace("\\", "\\\\") +
                          "\" get version")
        } else if (IS_OS_LINUX || IS_OS_MAC) {
            cmd = arrayOf("bash", "-c", "firefox --version")
        }
        return cmd
    }

    protected open fun resolveDriverSearchName(tag: String): String {
        val assetRegex = when {
            IS_OS_WINDOWS -> "win64"
            IS_OS_MAC_OSX -> "macos"
            IS_OS_LINUX   -> "linux64"
            else          -> throw java.lang.IllegalArgumentException("OS $OS_NAME not supported for $browserType")
        }

        return "REGEX:.+$tag-$assetRegex.+"
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

    protected fun newIsolatedWsClient() = WebServiceClient(context).configureAsQuiet().disableContextConfiguration()

    protected fun initManifestAndCheckDriver(): Pair<WebDriverManifest, Boolean> {
        return Pair(initWebDriverManifest(), isFileReadable(driverLocation, DRIVER_MIN_SIZE))
    }

    protected fun initWebDriverManifest(): WebDriverManifest {
        val manifest: WebDriverManifest = if (driverManifest.canRead() && driverManifest.length() > 10) {
            GSON.fromJson(FileUtils.readFileToString(driverManifest, DEF_CHARSET), WebDriverManifest::class.java)
        } else {
            // first time
            WebDriverManifest()
        }
        manifest.init()
        return manifest
    }

    protected abstract fun resolveLocalDriverPath(): String

    companion object {
        const val DRIVER_MIN_SIZE: Long = 1024 * 50
        protected const val MANIFEST = ".manifest"

        @JvmStatic
        @Throws(IOException::class)
        fun newInstance(browserType: BrowserType, context: ExecutionContext): WebDriverHelper {
            // sanity check
            if (browserType == safari || browserType == iphone) {
                throw IllegalArgumentException("No WebDriverHelper implementation needed/available for $browserType")
            }

            val helper: WebDriverHelper = when (browserType) {
                edge                                   -> EdgeDriverHelper(context)
                firefox, firefoxheadless               -> FirefoxDriverHelper(context)
                electron                               -> ElectronDriverHelper(context)
                chrome, chromeheadless, chromeembedded -> ChromeDriverHelper(context)
                ie                                     -> IEDriverHelper(context)
                // download BrowserStackLocal executable only needed when `browserstack.local` is `true`
                browserstack                           -> BrowserStackLocalHelper(context)
                // download CrossBrowserTesting local executable only needed when `crossbrowsertesting.local` is `true`
                crossbrowsertesting                    -> CrossBrowserTestingLocalHelper(context)
                else                                   -> throw RuntimeException("No WebDriverHelper implemented for $browserType")
            }

            helper.browserType = browserType

            // 0. fetch config based on browser type
            val config = helper.initConfig()
            helper.config = config

            helper.driverLocation = helper.resolveLocalDriverPath()

            val driverHome = File(context.replaceTokens(config.home))
            FileUtils.forceMkdir(driverHome)

            helper.driverManifest = File(StringUtils.appendIfMissing(driverHome.absolutePath, separator) + MANIFEST)

            return helper
        }

        @JvmStatic
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

        @JvmStatic
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

        @JvmStatic
        @Throws(IOException::class)
        protected fun unzip(zipFile: String, uncompressedFile: String) {
            if (StringUtils.isBlank(zipFile)) return
            if (StringUtils.isBlank(uncompressedFile)) return

            val expectedFile = File(uncompressedFile)
            FileUtil.unzip(File(zipFile), expectedFile.parentFile, listOf(expectedFile))

            FileUtils.deleteQuietly(File(zipFile))
        }

        // todo: not perfect - does not account for versioning spaning over 3 dots, like v.0.2.7.1
        @JvmStatic
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

/** webdriver helper for Edge browser */
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
        val (manifest: WebDriverManifest, hasDriver) = initManifestAndCheckDriver()

        // find current OS build of Windows 10
        var currentOsVer = deriveWin10BuildNumber()

        if (!hasDriver || !StringUtils.equals(manifest.driverVersion, currentOsVer)) {
            // gotta download; doesn't matter if current is greater or lesser than manifest's
            val wsClient = newIsolatedWsClient()
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
            updateManifestProperties(manifest, downloadUrl, currentOsVer)
        }

        return manifest
    }

    companion object {
        // first version of edge browser as min/fallback version
        private const val minOsVersionNum = 10240
        private const val minOsVersion = "" + minOsVersionNum

        private val winVer = mutableListOf("/C", "ver")
        private const val winVerRegex1 = "10\\.0\\.(\\d+)\\.(\\d+)"
        private const val winVerRegex2 = "10\\.0\\.(\\d+)"
        private const val winVerRegex3 = "([\\d\\.]+)"

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
                    RegexUtils.isExact(currentOsVer, winVerRegex1) ->
                        RegexUtils.replace(currentOsVer, winVerRegex1, "$1")
                    RegexUtils.isExact(currentOsVer, winVerRegex2) ->
                        RegexUtils.replace(currentOsVer, winVerRegex2, "$1")
                    RegexUtils.isExact(currentOsVer, winVerRegex3) ->
                        StringUtils.substringAfterLast(currentOsVer, ".")
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

/** webdriver helper for firefox and firefox headless browser */
class FirefoxDriverHelper(context: ExecutionContext) : WebDriverHelper(context) {

    override fun resolveLocalDriverPath(): String {
        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               config.baseName + if (IS_OS_WINDOWS) ".exe" else ""
    }

    @Throws(IOException::class)
    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        var (manifest: WebDriverManifest, hasDriver) = initManifestAndCheckDriver()

        // never check is turned on and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        //first time driver download
        if (!hasDriver) {
            updateCompatibleDriverVersion()
            manifest = initWebDriverManifest()
        }

        var driverVersion = manifest.compatibleDriverVersion
        if (StringUtils.isNotBlank(driverVersion)) {
            driverVersion = "v$driverVersion"
        } else if (hasDriver && manifest.lastChecked + config.checkFrequency >= System.currentTimeMillis()) {
            //No need to update still have time
            return manifest
        }

        // first ws call to check existing/available versions of this driver
        val wsClient = newIsolatedWsClient()
        val response = wsClient.get(config.checkUrlBase, null)
        if (response.returnCode >= 400) {
            // error in checking online
            throw IOException("Error when accessing ${config.checkUrlBase}: ${response.statusText}")
        }
        FileUtils.deleteQuietly(File(File(driverLocation).parent))
        val availableDriverContent = StringUtils.trim(response.body)
        when {
            TextUtils.isBetween(availableDriverContent, "[", "]") -> {
                if (StringUtils.isBlank(driverVersion)) {
                    extractDriverInfos(JSONArray(availableDriverContent), manifest,
                                       pollForUpdates)
                } else {
                    resolveDownloadUrlFromJSONArray(
                        manifest, driverVersion, JSONArray(availableDriverContent))
                }
            }

            TextUtils.isBetween(availableDriverContent, "{", "}") -> {
                if (StringUtils.isBlank(driverVersion)) {
                    extractDriverInfo(JSONObject(availableDriverContent), manifest, pollForUpdates)
                } else {
                    resolveDownloadUrlFromJSONObject(manifest, driverVersion!!,
                                                     JSONObject(availableDriverContent))
                }
            }

            else                                                  ->
                ConsoleUtils.error("Unknown content downloaded from ${config.checkUrlBase}; IGNORED...")
        }

        return manifest
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

    override fun resolveDriverSearchName(tag: String): String {
        val env = when {
            IS_OS_WINDOWS -> "win32"
            IS_OS_LINUX   -> "linux"
            IS_OS_MAC     -> "darwin"
            else          -> throw IllegalArgumentException("OS $OS_NAME not supported for $browserType")
        }

        val arch = when (EnvUtils.getOsArchBit()) {
            32   -> "ia32"
            64   -> "x64"
            else -> "ia32"
        }

        return "${config.baseName}-$tag-$env-$arch.zip"
    }
}

/** webdriver helper for Chrome browser */
class ChromeDriverHelper(context: ExecutionContext) : WebDriverHelper(context) {

    private val minDriverVersion = "2.46"

    override fun resolveLocalDriverPath(): String {
        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               config.baseName + if (IS_OS_WINDOWS) ".exe" else ""
    }

    @Throws(IOException::class)
    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {

        var (manifest: WebDriverManifest, hasDriver) = initManifestAndCheckDriver()

        // never check is turned on and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        //first time driver download
        if (!hasDriver) {
            updateCompatibleDriverVersion()
            manifest = initWebDriverManifest()
        }

        //Fetch installed chrome version and prepare url for download
        val (compatibleDriverVersion, isUpdateRequired) = isBrowserUpdated(manifest, hasDriver)

        if (!hasDriver || isUpdateRequired) {
            var driverVersion = minDriverVersion
            val wsClient = newIsolatedWsClient()
            val lookupResponse =
                    if (StringUtils.isBlank(compatibleDriverVersion)) {
                        wsClient.get("${config.checkUrlBase}/LATEST_RELEASE", "")
                    } else wsClient.get("${config.checkUrlBase}/LATEST_RELEASE_$compatibleDriverVersion", "")

            if (lookupResponse.returnCode != 200) {
                // something's wrong...
                ConsoleUtils.log("[Chrome] unable to resolve latest version for $browserType. " +
                                 "Use min. driver version instead...")
            } else {
                driverVersion = lookupResponse.body
                FileUtils.deleteQuietly(File(File(driverLocation).parent))
            }

            val env = when {
                IS_OS_WINDOWS -> "win32"
                IS_OS_LINUX   -> "linux64"
                IS_OS_MAC     -> "mac64"
                else          -> throw IllegalArgumentException("OS $OS_NAME not supported for $browserType")
            }

            val downloadUrl = "${config.checkUrlBase}/$driverVersion/${config.baseName}_$env.zip"
            ConsoleUtils.log("[Chrome] derived download URL as $downloadUrl")
            updateManifestProperties(manifest, downloadUrl, driverVersion)
        }

        return manifest
    }

    private fun isBrowserUpdated(manifest: WebDriverManifest, hasDriver: Boolean): Pair<String?, Boolean> {
        var browserVersion = manifest.compatibleDriverVersion
        var isUpdateRequired = false
        if (StringUtils.isNotBlank(browserVersion)) {
            browserVersion = browserVersion?.substringBeforeLast(".")
            if (hasDriver && (StringUtils.isBlank(manifest.driverVersion) || !manifest.driverVersion?.startsWith(
                    browserVersion!!)!!)) {
                isUpdateRequired = true
            }
        } else if (manifest.lastChecked + config.checkFrequency <= System.currentTimeMillis()) {
            isUpdateRequired = true
        }

        return Pair(browserVersion, isUpdateRequired)
    }
}

/** webdriver helper for IE browser */
class IEDriverHelper(context: ExecutionContext) : WebDriverHelper(context) {

    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        val (manifest: WebDriverManifest, hasDriver) = initManifestAndCheckDriver()

        // never check is turned on and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        if (pollForUpdates && manifest.lastChecked + config.checkFrequency > System.currentTimeMillis()) {
            // we still have time.. no need to check now
            return manifest
        }
        // else, need to check online, poll online for newer driver

        // first ws call to check existing/available versions of this driver
        val wsClient = newIsolatedWsClient()
        val response = wsClient.get(config.checkUrlBase, null)
        val xmlPayload = response.body
        val archKey = if (StringUtils.contains(driverLocation, "win32")) "Win32" else "x64"

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

        if (!pollForUpdates || manifest.driverVersionExpanded < expandVersion(tag)) {
            // persist the date/time when we last checked online
            manifest.lastChecked = System.currentTimeMillis()
            manifest.driverVersion = tag
            manifest.driverUrl = "${config.checkUrlBase}/$key"
        }
        return manifest
    }

    override fun resolveLocalDriverPath(): String {
        if (!IS_OS_WINDOWS) {
            throw RuntimeException("Browser automation for Internet Explorer is only supported on " +
                                   "Windows operating system. Sorry...")
        }

        val newConfigHome = context.replaceTokens(config.home) + separator + (
                if (EnvUtils.isRunningWindows64bit() &&
                    !context.getBooleanData(OPT_FORCE_IE_32, getDefaultBool(OPT_FORCE_IE_32))) "x64"
                else "win32")

        this.driverLocation = newConfigHome
        config.home = newConfigHome
        return StringUtils.appendIfMissing(File(newConfigHome).absolutePath, separator) + config.baseName + ".exe"
    }
}

class BrowserStackLocalHelper(context: ExecutionContext) : WebDriverHelper(context) {
    override fun resolveLocalDriverPath(): String {
        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               config.baseName + if (IS_OS_WINDOWS) ".exe" else ""
    }

    @Throws(IOException::class)
    override fun resolveDriverManifest(pollForUpdates: Boolean): WebDriverManifest {
        val (manifest: WebDriverManifest, hasDriver) = initManifestAndCheckDriver()

        // never check is turned on and we already have a driver, so just keep this one
        if (manifest.neverCheck && hasDriver) return manifest

        if (pollForUpdates && manifest.lastChecked + config.checkFrequency > System.currentTimeMillis()) {
            // we still have time.. no need to check now
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

class CrossBrowserTestingLocalHelper(context: ExecutionContext) : WebDriverHelper(context) {
    override fun resolveLocalDriverPath(): String {
        return StringUtils.appendIfMissing(File(context.replaceTokens(config.home)).absolutePath, separator) +
               resolveBaseName() + if (IS_OS_WINDOWS) ".exe" else ""
    }

    override fun resolveDriverSearchName(tag: String): String {
        val fileType = when {
            IS_OS_WINDOWS -> ".zip"
            IS_OS_LINUX   -> ".zip"
            IS_OS_MAC     -> ".zip"
            else          -> ""
        }

        return "${resolveBaseName()}$fileType"
    }

    private fun resolveBaseName(): String {
        val env = when {
            IS_OS_WINDOWS -> "win"
            IS_OS_LINUX   -> "linux"
            IS_OS_MAC     -> "macos"
            else          -> throw IllegalArgumentException("OS $OS_NAME not supported for $browserType")
        }

        val arch = when (EnvUtils.getOsArchBit()) {
            32   -> "-x86"
            64   -> "-x64"
            else -> ""
        }

        return "${config.baseName}-$env$arch"
    }
}