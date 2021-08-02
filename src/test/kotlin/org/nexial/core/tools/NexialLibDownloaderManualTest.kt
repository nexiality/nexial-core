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