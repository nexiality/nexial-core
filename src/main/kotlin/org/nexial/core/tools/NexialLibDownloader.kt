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


object NexialLibDownloader {

    private const val DOWNLOAD_TO_DIR_NAME = "lib"
    private const val NEXIAL_LIB_BASE_URL = "https://github.com/nexiality/fixes/releases/download/"
    private const val ZIP_FILE_NAME_PREFIX = "nexial-lib-"
    const val LIB_VERSION_FILENAME = "nexial-lib-version.txt"

    val nexialLibLoc = "$USER_NEXIAL_HOME$DOWNLOAD_TO_DIR_NAME$separator"
    var nexialHome = ""


    @JvmStatic
    fun main(args: Array<String>) {
        try {
            if (args.isNotEmpty()) {
                nexialHome = StringUtils.substringBefore(args[0], "bin")
                nexialHome = nexialHome.replace("\\", "$separator")
            }
            updateLibrary()
        } catch (e: Exception) {
            ConsoleUtils.error("error while downloading libs from $NEXIAL_LIB_BASE_URL")
            ConsoleUtils.error("error is ${e.printStackTrace()}")
        }
    }

    fun updateLibrary() {
        val requiredVer = getRequiredNexialLibVersion()
        if (StringUtils.isNotBlank(requiredVer)) {
            val tagName = "$ZIP_FILE_NAME_PREFIX$requiredVer"
            val fileName = "$tagName.zip"
            ConsoleUtils.log("Your Nexial support library is outdated. Nexial is downloading the latest support library now. It will take a few minutes...")
            val downloadFileName = "$DOWNLOAD_TO_DIR_NAME.zip"
            val downloadTo = "$USER_NEXIAL_HOME$downloadFileName"
            downloadFile("$NEXIAL_LIB_BASE_URL$tagName/$fileName", downloadTo)
            FileUtils.deleteQuietly(File("$nexialLibLoc")) //del old lib dir
            FileUtil.unzip(File(downloadTo), File("$nexialLibLoc"), false)
            FileUtils.deleteQuietly(File(downloadTo))
            ConsoleUtils.log("Your Nexial support library is updated to version $requiredVer")
        }
    }

    fun getRequiredNexialLibVersion(): String {
        nexialHome = StringUtils.appendIfMissing(nexialHome, separator)
        val versionFilePath = "$nexialHome$DOWNLOAD_TO_DIR_NAME$separator$LIB_VERSION_FILENAME"
        if (FileUtil.isFileReadable(File(versionFilePath))) {
            return FileUtils.readFileToString(File(versionFilePath), DEF_CHARSET).trim()
        }
        return ""
    }

    @Throws(IOException::class)
    private fun downloadFile(downloadUrl: String, downloadTo: String) {
        if (StringUtils.isNotBlank(downloadUrl)) {
            val libFile = File(downloadTo)
            if (libFile.exists()) {
                FileUtils.deleteQuietly(libFile)
            }
            val wsClient = WebServiceClient(null).configureAsQuiet().disableContextConfiguration()
            val response = wsClient.download(downloadUrl, null, downloadTo)
            if (response.returnCode >= 400) {
                ConsoleUtils.error("There was an error while downloading Nexial support library. Url: $downloadUrl")
                throw IOException("There was an error while downloading Nexial support library. Url: $downloadUrl")
            }
            ConsoleUtils.log("The latest support library has been downloaded. Installing it now...")
        }
    }

}
