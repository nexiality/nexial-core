package org.nexial.core.tools

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.Project.USER_NEXIAL_HOME
import org.nexial.core.plugins.ws.WebServiceClient
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator
import java.io.IOException
import kotlin.system.exitProcess


object NexialLibDownloader {

    internal const val DOWNLOAD_TO_DIR_NAME = "lib"
    internal const val NEXIAL_LIB_BASE_URL = "https://github.com/nexiality/fixes/releases/download/"
    internal const val ZIP_FILE_NAME_PREFIX = "nexial-lib-"
    internal const val LIB_VERSION_FILENAME = "nexial-lib-version.txt"

    internal const val MSG_DOWNLOADING = "Your Nexial support library is outdated. Nexial is downloading the latest " +
                                         "support library now. It will take a few minutes..."
    internal const val MSG_UPDATED = "Your Nexial support library is updated to version"
    internal const val MSG_INSTALLING = "The latest support library has been downloaded. Installing it now..."

    internal const val EC_UPDATE_FAILED = 1
    internal const val EC_MISSING_INPUT = 2

    internal val nexialLibLoc = File("$USER_NEXIAL_HOME$DOWNLOAD_TO_DIR_NAME$separator")
    internal var nexialHome = ""

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isNotEmpty()) {
            nexialHome = StringUtils.appendIfMissing(
                StringUtils.substringBefore(args[0], "bin").replace("\\", separator), separator)
            try {
                updateLibrary()
                exitProcess(0)
            } catch (e: Exception) {
                ConsoleUtils.error("Error occurred while updating Nexial support library: ${e.message}")
                ConsoleUtils.error("Please retry.")
                exitProcess(EC_UPDATE_FAILED)
            }
        } else
            exitProcess(EC_MISSING_INPUT)
    }

    internal fun updateLibrary() {
        val requiredVer = getRequiredNexialLibVersion()
        if (StringUtils.isNotBlank(requiredVer)) {
            val tagName = "$ZIP_FILE_NAME_PREFIX$requiredVer"
            val downloadTo = "$USER_NEXIAL_HOME$DOWNLOAD_TO_DIR_NAME.zip"

            ConsoleUtils.log(MSG_DOWNLOADING)
            downloadFile("$NEXIAL_LIB_BASE_URL$tagName/$tagName.zip", downloadTo)

            ConsoleUtils.log(MSG_INSTALLING)
            FileUtils.deleteQuietly(nexialLibLoc) //del old lib dir
            FileUtil.unzip(File(downloadTo), nexialLibLoc, false)
            FileUtils.deleteQuietly(File(downloadTo))

            ConsoleUtils.log("$MSG_UPDATED $requiredVer")
        }
    }

    internal fun getRequiredNexialLibVersion(): String {
        val versionFilePath = "${nexialHome}lib$separator$LIB_VERSION_FILENAME"
        return if (FileUtil.isFileReadable(File(versionFilePath)))
            FileUtils.readFileToString(File(versionFilePath), DEF_CHARSET).trim()
        else ""
    }

    @Throws(IOException::class)
    internal fun downloadFile(downloadUrl: String, downloadTo: String) {
        val libFile = File(downloadTo)
        if (libFile.exists()) FileUtils.deleteQuietly(libFile)

        val response = WebServiceClient(null)
            .configureAsQuiet()
            .disableContextConfiguration()
            .download(downloadUrl, null, downloadTo)
        if (response.returnCode >= 400)
            throw IOException("Error occurred while downloading Nexial support library from $downloadUrl: " +
                              response.statusText)
    }
}
