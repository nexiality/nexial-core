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

package org.nexial.core.plugins.db

import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.FileExistsException
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter.TRUE
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_HOME
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.FileUtil.isFileReadable
import org.nexial.core.NexialConst.GSON
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator
import java.io.IOException

abstract class DBDriverHelper protected constructor(protected var context: ExecutionContext) {
    protected lateinit var dbType: String
    protected lateinit var config: DBDriverConfig
    protected lateinit var driverLocation: String
    private lateinit var driverFilePath: String

    @Throws(IOException::class)
    fun resolveDriver(): File {
        // check if local copy of driver exists
        // if no local driver, poll online for driver
        // if local driver exists, check metadata for need to check for driver update
        driverFilePath = StringUtils.appendIfMissing(driverLocation, separator) + config.fileName
        downloadDriver()

        //resolve driver version as per jdk
        resolveDriverVersion()

        val driver = File(driverFilePath)
        return if (!driver.exists())
            throw RuntimeException("Can't resolve/download driver for $dbType")
        else
            driver
    }

    private fun resolveDriverVersion() {
        if (dbType == "mssql") {
            val fileFilter: IOFileFilter = WildcardFileFilter(listOf("mssql-jdbc*.jar"))
            val availableDrivers: Collection<File> = FileUtils.listFiles(File(driverLocation), fileFilter, TRUE)
            val javaVersion = System.getProperty("java.version") //"${JAVA_VERSION}"
            val javaVersionStartsWith = javaVersion.substringBefore(".") + "."
            driverFilePath = if (javaVersion.startsWith("1.")) {
                getDriverFilePathByJreVersion(availableDrivers, ".jre8.jar")
            } else {
                getDriverFilePathByJreVersion(availableDrivers, ".jre" + javaVersionStartsWith + "jar")
            }
            ConsoleUtils.log("Loading driver $driverFilePath for java version: $javaVersion")
        }
    }

    private fun getDriverFilePathByJreVersion(availableDrivers: Collection<File>, jreVersion: String): String {
        var driverFilePathIfExist = ""

        //exact match
        val jreVersionList = mutableListOf<Int>()
        for (file in availableDrivers) {
            val fileName = file.name
            if (fileName.endsWith(jreVersion)) return file.absolutePath
            jreVersionList.add(extractJreVersion(fileName))
        }

        //if exact match not found then find closest match
        if (StringUtils.isBlank(driverFilePathIfExist)) {
            val requiredDriverJreVer = extractJreVersion(jreVersion)
            val bestMatchDriverVersion = jreVersionList.stream()
                .filter { num -> num <= requiredDriverJreVer }
                .max(Comparator.naturalOrder())
                .get()

            // driverFilePathIfExist = availableDrivers.find { it.name.endsWith(".jre$bestMatchDriverVersion.jar") }?.absolutePath ?: ""
            for (file: File in availableDrivers) {
                if (file.name.endsWith(".jre$bestMatchDriverVersion.jar")) driverFilePathIfExist = file.absolutePath
            }
        }
        return driverFilePathIfExist
    }

    private fun extractJreVersion(fileName: String) = fileName.substringAfter(".jre").substringBefore(".jar").toInt()

    @Throws(IOException::class)
    protected fun downloadDriver() {
        val hasDriver = isFileReadable(driverFilePath, DRIVER_MIN_SIZE)
        val driverUrl = config.checkUrlBase!!

        // no url to download or no need to download... so we are done
        if (StringUtils.isBlank(driverUrl)) throw IOException("Url to download driver is empty.")

        if (!hasDriver) {
            // download driver to driver home (local)
            val wsClient = newIsolatedWsClient()

            //delete the directory before new download
            val driverDir = File(driverLocation)
            if (driverDir.isDirectory) FileUtils.deleteQuietly(driverDir)
            FileUtils.forceMkdir(driverDir)   //create folder .nexial/rdbms/xxdbType

            // download url might not be the actual driver, but zip or gzip
            val downloadTo = when {
                driverUrl.endsWith(".zip") -> "$driverLocation/driver.zip"
                driverUrl.endsWith(".jar") -> driverFilePath
                else                       -> StringUtils.appendIfMissing(driverLocation, separator) + "driver.zip"
            }
            val response = wsClient.download(driverUrl, null, downloadTo)
            if (response.returnCode >= 400) {
                throw IOException("Unable to download driver for $dbType from $driverUrl: ${response.statusText}")
            }


            if (StringUtils.endsWith(downloadTo, ".zip")) {
                unzip(downloadTo, driverLocation, dbType)
            }

            if (!isFileReadable(driverFilePath, DRIVER_MIN_SIZE)) {
                // download fail? disk out of space?
                throw IOException("Unable to download/save driver for $dbType from $driverUrl")
            }

            ConsoleUtils.log("[DBDriverHelper] for $dbType downloaded to $driverLocation")
        }
    }

    protected fun initConfig(): DBDriverConfig {
        val configs = context.dbdriverHelperConfig

        if (MapUtils.isEmpty(configs)) {
            val error = "No DBDriver configurations found"
            ConsoleUtils.log(error)
            throw IllegalArgumentException(error)
        }

        var configString = configs.get(dbType)
        if (StringUtils.isBlank(configString)) {
            val error = "Configuration not supported for dbType $dbType"
            ConsoleUtils.log(error)
            throw IllegalArgumentException(error)
        }

        configString = context.replaceTokens(configString)
        configString = StringUtils.replace(configString, "\\", "/")
        val config = GSON.fromJson(configString, DBDriverConfig::class.java)
        config.init()
        return config
    }

    protected fun newIsolatedWsClient() = WebServiceClient(context).configureAsQuiet().disableContextConfiguration()

    protected abstract fun resolveLocalDriverPath(): String

    companion object {
        const val DRIVER_MIN_SIZE: Long = 1024 * 50

        @JvmStatic
        @Throws(IOException::class)
        fun newInstance(dbType: String, context: ExecutionContext): DBDriverHelper {
            //create a customised helper class if this is not helpful
            val helper: DBDriverHelper = DBDriverHelperImpl(context)
            helper.dbType = dbType

            val config = helper.initConfig()
            helper.config = config

            helper.driverLocation = helper.resolveLocalDriverPath()

            return helper
        }

        @JvmStatic
        @Throws(IOException::class)
        protected fun unzip(zipFile: String, uncompressTo: String, dbType: String) {
            if (StringUtils.isBlank(zipFile)) return
            if (StringUtils.isBlank(uncompressTo)) return

            FileUtil.unzip(File(zipFile), File(uncompressTo))
            if (dbType == "mssql") extractMSSqlJarsDlls(uncompressTo)
            FileUtils.deleteQuietly(File(zipFile))
        }

        private fun extractMSSqlJarsDlls(uncompressTo: String) {
            val fileFilter: IOFileFilter = WildcardFileFilter(listOf("*.jar", "*.dll"))
            val expectedFiles: Collection<File> = FileUtils.listFiles(File(uncompressTo), fileFilter, TRUE)

            val dllFolder = "$USER_HOME${separator}.nexial${separator}dll"

            for (file in expectedFiles) {
                try {
                    when {
                        file.name.endsWith(".dll") -> FileUtils.moveFileToDirectory(file, File(dllFolder), true)
                        file.name.endsWith(".jar") -> FileUtils.moveFileToDirectory(file, File(uncompressTo), true)
                    }
                } catch (e: FileExistsException) {
                    //No Need to throw or log
                }
            }

            //delete uncompressed folder
            val uncompressedFiles = File(uncompressTo).listFiles()
            if (uncompressedFiles != null && uncompressedFiles.size > 0) {
                for (file in uncompressedFiles) if (file.isDirectory) FileUtils.deleteQuietly(file)
            }
        }
    }
}

/** db driver helper for postgresql/mariadb/mssql/hsqldb/mysql/oracle/db2 db */
class DBDriverHelperImpl(context: ExecutionContext) : DBDriverHelper(context) {

    override fun resolveLocalDriverPath(): String {
        return File(context.replaceTokens(config.home)).absolutePath
    }
}