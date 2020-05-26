package org.nexial.core.plugins.db


import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.SystemUtils.JAVA_VERSION
import org.junit.Assert
import org.junit.Test
import org.nexial.commons.utils.FileUtil
import org.nexial.core.model.MockExecutionContext
import java.io.File

class DBDriverHelperTest {

    private val driverHomeBase = "${SystemUtils.USER_HOME}${File.separator}.nexial${File.separator}rdbms${File.separator}"

    @Test
    @Throws(Exception::class)
    fun downloadDBDrivers() {
        val context = MockExecutionContext(true)
        for (dbType: String in context.dbdriverHelperConfig.keys) {
            val driverHome = File("${driverHomeBase}${dbType}")
            FileUtils.deleteDirectory(driverHome)
            assertDriverExists(DBDriverHelper.newInstance(dbType, context).resolveDriver())
        }
        context.cleanProject()
    }

    @Test
    @Throws(Exception::class)
    fun downloadDBDriver_mssql_with_diff_jre() {
        val dbType = "mssql"
        val context = MockExecutionContext(true)
        for (i in 9..14) {
            System.setProperty("java.version", i.toString() + ".x.xx")
            assertDriverExists(DBDriverHelper.newInstance(dbType, context).resolveDriver())
        }
        //verify dll
        val dllFolder = "${SystemUtils.USER_HOME}${File.separator}.nexial${File.separator}dll"
        val expectedFiles: Collection<File> = FileUtils.listFiles(File(dllFolder), TrueFileFilter.TRUE,
                                                                  TrueFileFilter.TRUE)
        Assert.assertTrue(expectedFiles.size >= 2)

        System.setProperty("java.version", "$JAVA_VERSION")
        context.cleanProject()
    }

    private fun assertDriverExists(driver: File): File {
        println("driver = $driver")
        Assert.assertNotNull(driver)
        Assert.assertTrue(driver.exists())
        Assert.assertTrue(driver.canRead())
        Assert.assertTrue(FileUtil.isFileReadable(driver, 500 * 1024))
        return driver
    }

}