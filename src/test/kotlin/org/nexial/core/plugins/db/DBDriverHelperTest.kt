package org.nexial.core.plugins.db

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter.TRUE
import org.apache.commons.lang3.SystemUtils.JAVA_VERSION
import org.apache.commons.lang3.SystemUtils.USER_HOME
import org.junit.Assert
import org.junit.Test
import org.nexial.commons.utils.FileUtil
import org.nexial.core.model.MockExecutionContext
import java.io.File
import java.io.File.separator

class DBDriverHelperTest {

    private val driverHomeBase = "$USER_HOME${separator}.nexial${separator}rdbms${separator}"

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
            System.setProperty("java.version", "$i.x.xx")
            assertDriverExists(DBDriverHelper.newInstance(dbType, context).resolveDriver())
        }
        //verify dll
        val dllFolder = "$USER_HOME${separator}.nexial${separator}dll"
        val expectedFiles: Collection<File> = FileUtils.listFiles(File(dllFolder), TRUE, TRUE)
        Assert.assertTrue(expectedFiles.size >= 2)

        System.setProperty("java.version", JAVA_VERSION)
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