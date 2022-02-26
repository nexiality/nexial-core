/*
 * Copyright 2012-2022 the original author or authors.
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
package org.nexial.core.plugins.io

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FalseFileFilter
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.io.filefilter.RegexFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Compare.*
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.MockExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.image.ImageCommandTest
import org.nexial.core.utils.ExecUtils.newRuntimeTempDir
import org.nexial.core.variable.Random
import org.nexial.core.variable.Sysdate
import org.nexial.core.variable.Syspath
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.util.*

class IoCommandTest {
    private val baseLocation = TEMP
    private val tmpOutdir = baseLocation + this.javaClass.simpleName
    private val testFile1 = baseLocation + "dummy1"
    private val testFile2 = baseLocation + "dummy2"
    private val testFile3 = baseLocation + "dummy3"
    private val testDestination1 = baseLocation + "newloc"
    private val testDestination2 = baseLocation + "testMatch"
    private val testDestination3 = baseLocation + "newloc2"
    private val basePath = baseLocation + this.javaClass.simpleName + separator
    private val dummyPng = baseLocation + "dummy.png"
    private val context: ExecutionContext = object : MockExecutionContext() {
        override fun replaceTokens(text: String): String {
            builtinFunctions = mapOf("sysdate" to Sysdate(), "syspath" to Syspath())
            return super.replaceTokens(text)
        }
    }

    @Test
    @Throws(Exception::class)
    fun copy_file_to_dir() {
        val sourceFile = makeDummyContent(testFile3)

        // make sure the file is really written to disk
        val expectedFileSize = 100 * 10
        assertTrue(sourceFile.length() >= expectedFileSize)

        val io = newIO()
        val result = io.copyFiles(testFile3, testDestination1)
        println("result = $result")

        val destinationFile = File(testDestination1 + separator + "dummy3")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertTrue(destinationFile.length() >= expectedFileSize)
        assertEquals(FileUtils.readFileToString(sourceFile, DEF_CHARSET),
                     FileUtils.readFileToString(destinationFile, DEF_CHARSET))
    }

    @Test
    fun copy_jar_resource_to_dir() {
        // .../lib/selenium-java-2.39.0.jar!/org/openqa/selenium/firefox/webdriver.xpi
        val io = newIO()

        val result = io.copyFiles("jar:/org/openqa/selenium/firefox/webdriver.xpi", testDestination1)
        println("result = $result")
        val destinationFile = File(testDestination1 + separator + "webdriver.xpi")
        val expectedFileSize = (685 * 1024).toLong()
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertTrue(destinationFile.exists())
        assertTrue(destinationFile.length() > expectedFileSize)
        println("using IoCommand; copied to " + destinationFile.absolutePath)
    }

    @Test
    @Throws(Exception::class)
    fun copy_wildcard_files_to_dir() {
        val sourceDir = newRuntimeTempDir()
        makeDummyContent(sourceDir + "_E4_test1_step4_myLogs.txt")
        makeDummyContent(sourceDir + "_E4_test1_step4_hello.log")
        makeDummyContent(sourceDir + "_E4_test1_step4_190FFV.csv")

        val io = newIO()

        val sourcePattern = sourceDir + "_E4_test1_step4*.*"
        val result = io.copyFiles(sourcePattern, testDestination1)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val destinationFiles = FileUtils.listFiles(File(testDestination1), object : IOFileFilter {
            override fun accept(file: File) = file.canRead() && file.isFile
            override fun accept(dir: File, name: String) = name.startsWith("_E4_test1_step4_")
        }, null)
        assertNotNull(destinationFiles)
        assertEquals(3, destinationFiles.size.toLong())

        val destinationPaths = destinationFiles.map { it.absolutePath }
        assertTrue(destinationPaths.contains(testDestination1 + separator + "_E4_test1_step4_myLogs.txt"))
        assertTrue(destinationPaths.contains(testDestination1 + separator + "_E4_test1_step4_hello.log"))
        assertTrue(destinationPaths.contains(testDestination1 + separator + "_E4_test1_step4_190FFV.csv"))

        FileUtils.deleteDirectory(File(sourceDir))
    }

    @Test
    @Throws(Exception::class)
    fun move_wildcard_files_to_dir() {
        val sourceDir = newRuntimeTempDir()
        val file1 = makeDummyContent(sourceDir + "_E3_test1_step4_myLogs.txt")
        val file2 = makeDummyContent(sourceDir + "_E3_test1_step4_hello.log")
        val file3 = makeDummyContent(sourceDir + "_E3_test1_step4_190FFV.csv")

        val io = newIO()

        val sourcePattern = sourceDir + "_E3_test1_step4*.*"
        val destinationDir = testDestination3 + separator + RandomStringUtils.randomAlphanumeric(5)
        File(destinationDir).mkdirs()
        val result = io.moveFiles(sourcePattern, destinationDir)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        val destinationFiles = FileUtils.listFiles(File(destinationDir), object : IOFileFilter {
            override fun accept(file: File) = file.canRead() && file.isFile
            override fun accept(dir: File, name: String) = name.startsWith("_E3_test1_step4_")
        }, null)
        assertNotNull(destinationFiles)
        assertEquals(3, destinationFiles.size.toLong())

        val destinationPaths = destinationFiles.map { it.absolutePath }
        assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_myLogs.txt"))
        assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_hello.log"))
        assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_190FFV.csv"))
        assertFalse(file1.exists())
        assertFalse(file2.exists())
        assertFalse(file3.exists())

        FileUtils.deleteDirectory(File(sourceDir))
    }

    @Test
    @Throws(Exception::class)
    fun move_wildcard_files_to_single_file() {
        val sourceDir = newRuntimeTempDir()
        val file1 = makeDummyContent(sourceDir + "_E5_test1_step4_myLogs.txt")
        val file2 = makeDummyContent(sourceDir + "_E5_test1_step4_hello.log")
        val file3 = makeDummyContent(sourceDir + "_E5_test1_step4_190FFV.csv")

        val io = newIO()
        context.setData(OPT_IO_COPY_CONFIG, COPY_CONFIG_OVERRIDE)

        val sourcePathPattern = sourceDir + "_E5_test1_step4*.*"
        val destinationDir = testDestination1 + separator + RandomStringUtils.randomAlphanumeric(5)
        val result = io.moveFiles(sourcePathPattern, destinationDir + separator + "myFile.text")
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        val destinationFiles = FileUtils.listFiles(File(destinationDir), object : IOFileFilter {
            override fun accept(file: File) = file.canRead() && file.isFile
            override fun accept(dir: File, name: String) = name.startsWith("myFile.text")
        }, null)
        assertNotNull(destinationFiles)
        assertEquals(1, destinationFiles.size.toLong())

        val destinationPaths = destinationFiles.map { it.absolutePath }
        assertTrue(destinationPaths.contains(destinationDir + separator + "myFile.text"))
        assertFalse(file1.exists())
        assertFalse(file2.exists())
        assertFalse(file3.exists())

        FileUtils.deleteDirectory(File(sourceDir))
    }

    @Test
    fun testUnzip() {
        val io = newIO()

        // ...lib/selenium-java-2.39.0.jar!/org/openqa/selenium/firefox/webdriver.xpi
        val result = io.unzip("jar:/org/openqa/selenium/firefox/webdriver.xpi", testDestination1)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        val files = FileUtils.listFiles(File(testDestination1), null, true)
        assertTrue(CollectionUtils.isNotEmpty(files))
        println(files)
    }

    @Test
    fun testMakeDirectory() {
        val io = newIO()

        val newLoc = testDestination1 + separator + "newDir2"
        val result = io.makeDirectory(newLoc)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        val newDir = File(newLoc)
        assertTrue(newDir.exists())
        assertTrue(newDir.isDirectory)
        assertTrue(newDir.canRead())
    }

    @Test
    @Throws(Exception::class)
    fun testMoveFiles() {
        val sourceFile = makeDummyContent(testFile1)
        val expectedFileLength = sourceFile.length()
        val targetDir = testDestination1
        val targetFile = File(targetDir + separator + sourceFile.name)
        FileUtils.deleteQuietly(targetFile)
        val fullPath = targetFile.absolutePath
        println("checking existence for " + fullPath + "? " + FileUtil.isFileReadable(fullPath))

        val io = newIO()

        val result = io.moveFiles(testFile1, targetDir)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertTrue(targetFile.exists())
        assertTrue(targetFile.canRead())
        assertEquals(expectedFileLength, targetFile.length())
        assertFalse(sourceFile.exists())

        FileUtils.deleteQuietly(targetFile)
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFiles() {
        makeDummyContent(testDestination1 + separator + "testDeleteFiles1.txt")
        makeDummyContent(testDestination1 + separator + "testDeleteFiles2.txt")
        makeDummyContent(testDestination1 + separator + "testDeleteFiles3.txt")
        makeDummyContent(testDestination1 + separator + "sub" + separator + "testDeleteFiles4.txt")
        makeDummyContent(testDestination1 + separator + "sub" + separator + "testDeleteFiles5.txt")

        val io = newIO()

        val result = io.deleteFiles(testDestination1, "false")
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        var dir = File(testDestination1)
        var files = FileUtils.listFiles(dir, RegexFileFilter("testDeleteFile.+"), FalseFileFilter.INSTANCE)
        assertTrue(CollectionUtils.isEmpty(files))
        dir = File(testDestination1 + separator + "sub")
        files = FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, FalseFileFilter.INSTANCE)
        assertEquals(CollectionUtils.size(files).toLong(), 2)
    }

    @Test
    @Throws(Exception::class)
    fun testRenameFiles() {
        val fixture1 = testDestination1 + separator + "testRenameFiles1.txt"
        makeDummyContent(fixture1)

        val io = newIO()

        val result = io.rename(fixture1, "fingerLickingGood.doc")
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        val sourceFile = File(fixture1)
        assertFalse(sourceFile.exists())
        val targetFile = File(testDestination1 + separator + "fingerLickingGood.doc")
        assertTrue(targetFile.exists())
        assertTrue(targetFile.canRead())
    }

    @Test
    @Throws(Exception::class)
    fun testReadFile() {
        val myTestFile = "$testFile1.testReadFile"
        val file = makeDummyContent(myTestFile)
        Thread.sleep(3000)

        val expectedLength = file.length()
        println("expectedLength = $expectedLength")

        val io = newIO()

        val varName = "testFile1"
        val result = io.readFile(varName, myTestFile)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val fileContent = context.getStringData(varName)
        println("fileContent = $fileContent")
        assertNotNull(fileContent)
        assertEquals(expectedLength, fileContent.length.toLong())
        assertTrue(fileContent.length > 1000)
    }

    @Test
    @Throws(Exception::class)
    fun testReadFile_xml() {
        val resourceFile = "/org/nexial/core/plugins/io/IOCommandTest_1.xml"
        val resource = ResourceUtils.getResourceFilePath(resourceFile)
        System.setProperty(OPT_DATA_DIR, "~/tmp/nexial/artifact/data")
        context.setData("ConfigFile", "$(syspath|data|fullpath)/Configurations.csv")

        val io = newIO()

        val varName = "testFile1"
        val result = io.readFile(varName, resource)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val fileContent = context.getStringData(varName)
        println("fileContent = $fileContent")
        assertNotNull(fileContent)
        assertTrue(StringUtils.contains(fileContent, "\\"))
    }

    @Test
    @Throws(IOException::class)
    fun testSaveFileMeta() {
        val myTestFile = "$testFile1.txt"
        val enteredText = "623132658,20130318,ANDERSON/CARTER,5270.00"

        val io = newIO()

        io.writeFile(myTestFile, enteredText, "true")
        var result = io.saveFileMeta("myData", myTestFile)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val file = File(myTestFile)
        val filemeta = FileMeta(file)
        assertEquals(myTestFile, filemeta.fullpath)
        assertEquals(42, filemeta.size)
        assertEquals(enteredText, filemeta.text)
        assertEquals(filemeta.isReadable, file.canRead())
        assertEquals(filemeta.isWritable, file.canWrite())
        assertEquals(filemeta.isExecutable, file.canExecute())
        assertEquals(filemeta.lastmod, file.lastModified())

        //Update same file and validate the changes
        val updateText = "623132658,20130318,ANDERSON/UPDATED,5271.01"
        io.writeFile(myTestFile, updateText, "false")
        result = io.saveFileMeta("myData", myTestFile)
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val updatedFile = File(myTestFile)
        val updatedMeta = FileMeta(updatedFile)
        assertNotEquals(filemeta.text, updatedMeta.text)
        assertEquals(myTestFile, updatedMeta.fullpath)
        assertEquals(43, filemeta.size)
        assertEquals(updateText, updatedMeta.text)
        assertEquals(updatedMeta.isReadable, updatedFile.canRead())
        assertEquals(updatedMeta.isWritable, updatedFile.canWrite())
        assertEquals(updatedMeta.isExecutable, updatedFile.canExecute())
        assertEquals(updatedMeta.lastmod, updatedFile.lastModified())
    }

    @Test
    @Throws(Exception::class)
    fun testReadProperty() {
        val propContent = """
            prop1=a
            prop2=b
            prop3=${"$"}{prop1}-${"$"}{prop2}
            
            """.trimIndent()
        val propFile = File(testFile1)
        FileUtils.write(propFile, propContent, DEF_CHARSET)

        val io = newIO()

        var result = io.readProperty("prop1", testFile1, "prop1")
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertEquals("a", context.getStringData("prop1"))

        result = io.readProperty("prop2", testFile1, "prop2")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertEquals("b", context.getStringData("prop2"))

        result = io.readProperty("prop3", testFile1, "prop3")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertEquals("a-b", context.getStringData("prop3"))
    }

    @Test
    @Throws(Exception::class)
    fun testWriteFile() {
        val propContent = """
            prop1=a
            prop2=b
            prop3=${"$"}{prop1}-${"$"}{prop2}
            
            """.trimIndent()

        val io = newIO()

        val result = io.writeFile(testFile1, propContent, "false")
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val file1 = File(testFile1)
        val prop = ResourceUtils.loadProperties(file1)
        assertEquals("a", prop.getProperty("prop1"))
        assertEquals("b", prop.getProperty("prop2"))
        assertEquals("-", prop.getProperty("prop3"))
    }

    @Test
    @Throws(Exception::class)
    fun testEol_simple() {
        val propContent = """
            prop1=a
            prop2=b
            prop3=${"$"}{prop1}-${"$"}{prop2}
            
            """.trimIndent()

        val io = newIO()

        var result = io.writeFile(testFile1, propContent, "false")
        println("result = $result")
        assertTrue(result.isSuccess)

        val file1 = File(testFile1)

        // eol default (platform) test
        // default is platform-specific eol
        var eol = System.lineSeparator()
        // check testFile1 for eol
        assertEquals("prop1=a" + eol + "prop2=b" + eol + "prop3=-" + eol,
                     FileUtils.readFileToString(file1, DEF_FILE_ENCODING))

        // eol as is test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_AS_IS)
        result = io.writeFile(testFile1, propContent, "false")
        assertTrue(result.isSuccess)
        eol = "\n"
        assertEquals("prop1=a" + eol + "prop2=b" + eol + "prop3=-" + eol,
                     FileUtils.readFileToString(file1, DEF_FILE_ENCODING))

        // eol for windows test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_WINDOWS)
        result = io.writeFile(testFile1, propContent, "false")
        assertTrue(result.isSuccess)
        eol = "\r\n"
        assertEquals("prop1=a" + eol + "prop2=b" + eol + "prop3=-" + eol,
                     FileUtils.readFileToString(file1, DEF_FILE_ENCODING))

        // eol for linux test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_UNIX)
        result = io.writeFile(testFile1, propContent, "false")
        assertTrue(result.isSuccess)
        eol = "\n"
        assertEquals("prop1=a" + eol + "prop2=b" + eol + "prop3=-" + eol,
                     FileUtils.readFileToString(file1, DEF_FILE_ENCODING))
    }

    @Test
    @Throws(Exception::class)
    fun testEol_mixed() {
        val propContent = """
            Now is the time for all good men to come to the aid of his country
            The lazy brown fox jump over the dog, 
            or something like that.
            
            Now is the winter of your discontent
            
            Good bye
            
            """.trimIndent()
        val io = newIO()
        var result = io.writeFile(testFile1, propContent, "false")
        println("result = $result")
        assertTrue(result.isSuccess)
        val file1 = File(testFile1)

        // eol default (platform) test
        // default is platform-specific eol
        var eol = System.lineSeparator()
        // check testFile1 for eol
        assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
                     "The lazy brown fox jump over the dog, " + eol +
                     "or something like that." + eol + eol +
                     "Now is the winter of your discontent" + eol + eol +
                     "Good bye" + eol,
                     FileUtils.readFileToString(file1, DEF_FILE_ENCODING))

        // eol as is test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_AS_IS)
        result = io.writeFile(testFile1, propContent, "false")
        assertTrue(result.isSuccess)
        eol = "\n"
        assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
                     "The lazy brown fox jump over the dog, " + eol +
                     "or something like that." + eol + eol +
                     "Now is the winter of your discontent" + eol + eol +
                     "Good bye" + eol,
                     FileUtils.readFileToString(file1, DEF_FILE_ENCODING))

        // eol for windows test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_WINDOWS)
        result = io.writeFile(testFile1, propContent, "false")
        assertTrue(result.isSuccess)
        eol = "\r\n"
        assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
                     "The lazy brown fox jump over the dog, " + eol +
                     "or something like that." + eol + eol +
                     "Now is the winter of your discontent" + eol + eol +
                     "Good bye" + eol,
                     FileUtils.readFileToString(file1, DEF_FILE_ENCODING))

        // eol for linux test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_UNIX)
        result = io.writeFile(testFile1, propContent, "false")
        assertTrue(result.isSuccess)
        eol = "\n"
        assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
                     "The lazy brown fox jump over the dog, " + eol +
                     "or something like that." + eol + eol +
                     "Now is the winter of your discontent" + eol + eol +
                     "Good bye" + eol,
                     FileUtils.readFileToString(file1, DEF_FILE_ENCODING))
    }

    @Test
    @Throws(Exception::class)
    fun testWriteFileAsIs() {
        val propContent = """
            prop1=a
            prop2=b
            prop3=${"$"}{prop1}-${"$"}{prop2}
            
            """.trimIndent()

        val io = newIO()

        val result = io.writeFileAsIs(testFile1, propContent, "false")
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val prop = ResourceUtils.loadProperties(File(testFile1))
        assertEquals("a", prop.getProperty("prop1"))
        assertEquals("b", prop.getProperty("prop2"))
        assertEquals("\${prop1}-\${prop2}", prop.getProperty("prop3"))
    }

    @Test
    @Throws(Exception::class)
    fun testWriteProperty() {
        val propContent = """
            prop1=a
            prop2=b
            prop3=${"$"}{prop1}-${"$"}{prop2}
            
            """.trimIndent()
        val propFile = File(testFile1)
        FileUtils.write(propFile, propContent, DEF_CHARSET)

        val io = newIO()

        var result = io.writeProperty(testFile1, "prop4", "c")
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        result = io.writeProperty(testFile1, "prop5", "\${prop1}-\${prop2}-\${prop3}-\${prop4}")
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val prop = ResourceUtils.loadProperties(File(testFile1))
        assertEquals("a", prop.getProperty("prop1"))
        assertEquals("b", prop.getProperty("prop2"))
        assertEquals("\${prop1}-\${prop2}", prop.getProperty("prop3"))
        assertEquals("c", prop.getProperty("prop4"))
        assertEquals("\${prop1}-\${prop2}-\${prop3}-\${prop4}", prop.getProperty("prop5"))
    }

    @Test
    fun testAssertEqual() {
        val propContent = """
            prop1=a
            prop2=b
            prop3=${"$"}{prop1}-${"$"}{prop2}
            
            """.trimIndent()
        val propContent2 = """
            prop1=a
            prop2=b
            prop3=${"$"}{prop1}-${"$"}{prop2}
            prop4=${"$"}{prop3}+${"$"}{prop2}
            
            """.trimIndent()

        val io = newIO()

        val myTestFile1 = "$testFile1.txt"
        val myTestFile2 = "$testFile2.txt"

        io.writeFile(myTestFile1, propContent, "false")
        io.writeFile(myTestFile2, propContent, "false")
        var result = io.assertEqual(myTestFile1, myTestFile2)
        println("result = $result")
        assertTrue(result.isSuccess)

        io.writeFile(myTestFile1, propContent, "true")
        io.writeFile(myTestFile2, propContent, "true")
        result = io.assertEqual(myTestFile1, myTestFile2)
        assertTrue(result.isSuccess)
        io.writeFile(myTestFile1, propContent2, "true")

        result = io.assertEqual(myTestFile1, myTestFile2)
        assertFalse(result.isSuccess)
    }

    @Test
    fun testAssertNotEqual() {
        val propContent1 = """
            prop1=a
            prop1=b
            prop1=${"$"}{prop1}-${"$"}{prop1}
            
            """.trimIndent()
        val propContent2 = """
            prop1=c
            prop1=d
            prop1=${"$"}{prop1}-${"$"}{prop1}
            
            """.trimIndent()
        val myTestFile1 = "$testFile1.txt"
        val myTestFile2 = "$testFile2.txt"

        val io = newIO()

        io.writeFile(myTestFile1, propContent1, "false")
        io.writeFile(myTestFile2, propContent2, "false")
        var result = io.assertNotEqual(myTestFile1, myTestFile2)
        println("result = $result")
        assertTrue(result.isSuccess)

        io.writeFile(myTestFile1, propContent1, "true")
        io.writeFile(myTestFile2, propContent2, "true")
        result = io.assertNotEqual(myTestFile1, myTestFile2)
        assertTrue(result.isSuccess)
        io.deleteFiles(myTestFile2, "false")
        io.writeFile(myTestFile2, propContent1, "true")
        io.writeFile(myTestFile2, propContent1, "true")

        result = io.assertNotEqual(myTestFile1, myTestFile2)
        assertFalse(result.isSuccess)
    }

    @Test
    fun testCompare() {
        val myTestFile1 = "$testFile1.txt"
        val myTestFile2 = "$testFile2.txt"
        val file1Data: MutableList<String> = ArrayList()
        val file2Data: MutableList<String> = ArrayList()
        file1Data.add("""
    taxID,weekBeginDate,name,gross
    623132658,20130318,ANDERSON/CARTER,5270.00
    623132658,20130325,ANDERSON/CARTER,5622.50
    623132658,20130401,ANDERSON/CARTER,3402.50
    623132658,20130408,ANDERSON/CARTER,3222.50
    623132658,20130603,ANDERSON/CARTER,5675.00
    
    """.trimIndent())
        file2Data.add("""
    taxID,weekBeginDate,name,gross
    623132658,20130318,ANDERSON/CARTER,5270.00
    623132658,20130325,ANDERSON/CARTER,5622.50
    623132658,20130401,ANDERSON/CARTER,3402.50
    623132658,20130408,ANDERSON/CARTER,3222.50
    623132658,20130422,ANDERSON/CARTER,5285.00
    623132658,20130603,ANDERSON/CARTER,5675.00
    
    """.trimIndent())

        val io = newIO()

        io.writeFile(myTestFile1, file1Data.toString(), "true")
        io.writeFile(myTestFile2, file2Data.toString(), "true")
        var result = io.compare(myTestFile1, myTestFile2, "false")
        println("result = $result")
        assertFalse(result.isSuccess)

        io.deleteFiles(myTestFile2, "false")
        io.writeFile(myTestFile2, file1Data.toString(), "true")
        result = io.compare(myTestFile1, myTestFile2, "false")
        assertTrue(result.isSuccess)
    }

    @Test
    fun testFileReadable() {
        val propContent = """
            prop1=a
            prop2=b
            prop3=${"$"}{prop1}-${"$"}{prop2}
            
            """.trimIndent()
        val myTestFile = "$testFile1.txt"

        val io = newIO()

        io.writeFile(myTestFile, propContent, "false")
        var result = io.assertReadableFile(myTestFile, "-1")
        println("result = $result")
        assertTrue(result.isSuccess)

        result = io.assertReadableFile(myTestFile, "10")
        assertTrue(result.isSuccess)

        result = io.assertReadableFile(myTestFile, "")
        assertTrue(result.isSuccess)
    }

    @Test
    @Throws(Exception::class)
    fun testZip() {
        // setup
        val zipFilePath = basePath + "testZip1.zip"
        val unzipPath = basePath + "unzip"
        zip_create_test_fixture(basePath)

        val io = newIO()

        val result = io.zip("$basePath*.txt", zipFilePath)
        println("result = $result")
        zip_check_result(result)
        zip_check_zip_file(zipFilePath)
        assertTrue(io.unzip(zipFilePath, unzipPath).isSuccess)
        zip_check_unzipped_files(unzipPath, 10)

        zip_cleanup(basePath)
    }

    @Test
    @Throws(Exception::class)
    fun testZip1() {
        // setup
        val zipFilePath = basePath + "testZip1.zip"
        val unzipPath = basePath + "unzip"
        zip_create_test_fixture(basePath)

        val io = newIO()

        val result = io.zip(basePath, zipFilePath)
        println("result = $result")
        zip_check_result(result)
        zip_check_zip_file(zipFilePath)
        assertTrue(io.unzip(zipFilePath, unzipPath).isSuccess)
        zip_check_unzipped_files(unzipPath, 10)

        // tear down
        zip_cleanup(basePath)
    }

    @Test
    @Throws(Exception::class)
    fun testZip2() {
        // setup
        val zipFilePath = basePath + "testZip2.zip"
        val unzipPath = basePath + "unzip"
        zip_create_test_fixture(basePath)

        val io = newIO()

        val result = io.zip(StringUtils.removeEnd(basePath, separator), zipFilePath)
        println("result = $result")
        zip_check_result(result)
        zip_check_zip_file(zipFilePath)
        assertTrue(io.unzip(zipFilePath, unzipPath).isSuccess)
        zip_check_unzipped_files(unzipPath, 10)

        // tear down
        zip_cleanup(basePath)
    }

    @Test
    @Throws(Exception::class)
    fun testZip3() {
        // setup
        val zipFilePath = basePath + "testZip3.zip"
        val unzipPath = basePath + "unzip"
        zip_create_test_fixture(basePath)

        val io = newIO()

        val result = io.zip(basePath + "1*.*", zipFilePath)
        println("result = $result")
        zip_check_result(result)
        zip_check_zip_file(zipFilePath)
        assertTrue(io.unzip(zipFilePath, unzipPath).isSuccess)
        zip_check_unzipped_files(unzipPath, 1)

        // tear down
        zip_cleanup(basePath)
    }

    @Test
    @Throws(Exception::class)
    fun testCount() {
        val testDirectory = testDestination1 + separator
        val filePattern1 = ".*\\.txt"
        val filePattern2 = ".*\\.csv"
        makeDummyContent(testDirectory + "testCountFiles1.txt")
        makeDummyContent(testDirectory + "testCountFiles2.txt")
        makeDummyContent(testDirectory + "testCountFiles3.txt")
        makeDummyContent(testDirectory + "testCountFiles4.txt")

        val io = newIO()

        var result = io.count("fileCount", testDirectory, filePattern1)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertEquals("4", context.getStringData("fileCount"))
        makeDummyContent(testDirectory + "testAddFiles1.txt")
        makeDummyContent(testDirectory + "testAddFiles2.txt")

        result = io.count("fileCount", testDirectory, filePattern1)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertEquals("6", context.getStringData("fileCount"))

        val dir = File(testDirectory + "testCountFiles1.txt")
        dir.delete()
        makeDummyContent(testDirectory + "testCountFiles3.csv")
        makeDummyContent(testDirectory + "testCountFiles4.csv")
        result = io.count("fileCount", testDirectory, filePattern2)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertEquals("2", context.getStringData("fileCount"))

        result = io.count("fileCount", testDirectory, filePattern1)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertEquals("5", context.getStringData("fileCount"))
    }

    @Test
    fun saveDiff() {
        val expectedData: MutableList<String> = ArrayList()
        val actualData: MutableList<String> = ArrayList()
        val messages: MutableList<List<String>> = ArrayList()

        // happy path
        expectedData.add("""
    taxID,weekBeginDate,name,gross
    623132658,20130318,ANDERSON/CARTER,5270.00
    623132658,20130325,ANDERSON/CARTER,5622.50
    623132658,20130401,ANDERSON/CARTER,3402.50
    623132658,20130408,ANDERSON/CARTER,3222.50
    623132658,20130415,ANDERSON/CARTER,3665.00
    623132658,20130422,ANDERSON/CARTER,5285.00
    623132658,20130429,ANDERSON/CARTER,4475.00
    623132658,20130506,ANDERSON/CARTER,4665.00
    623132658,20130513,ANDERSON/CARTER,4377.50
    623132658,20130520,ANDERSON/CARTER,4745.00
    623132658,20130527,ANDERSON/CARTER,3957.50
    623132658,20130603,ANDERSON/CARTER,5675.00
    
    """.trimIndent())
        actualData.add("""
    taxID,weekBeginDate,name,gross
    623132658,20130318,ANDERSON/CARTER,5270.00
    623132658,20130325,ANDERSON/CARTER,5622.50
    623132658,20130401,ANDERSON/CARTER,3402.50
    623132658,20130408,ANDERSON/CARTER,3222.50
    623132658,20130415,ANDERSON/CARTER,3665.00
    623132658,20130422,ANDERSON/CARTER,5285.00
    623132658,20130429,ANDERSON/CARTER,4475.00
    623132658,20130506,ANDERSON/CARTER,4665.00
    623132658,20130513,ANDERSON/CARTER,4377.50
    623132658,20130520,ANDERSON/CARTER,4745.00
    623132658,20130527,ANDERSON/CARTER,3957.50
    623132658,20130603,ANDERSON/CARTER,5675.00
    
    """.trimIndent())
        messages.add(listOf("content matched exactly"))
        expectedData.add("""
    taxID,weekBeginDate,name,gross
    623132658,20130318,ANDERSON/CARTER,5270.00
    623132658,20130325,ANDERSON/CARTER,5622.50
    623132658,20130401,Anderson/CARTER,3402.50
    623132658,20130480,ANDERSON/CARTER,3222.50
    623132658,20130415,ANDERSON/CARTER,3665.00
    623132658,20130422,ANDERSON/CARTER,5285.00
    623132658,20130429,ANDERSON/CARTER,4475.00
    623132658,20130506,ANDERSON/CARTER,4665.00
    623132658,20130603,ANDERSON/CARTER,5675.00
    
    """.trimIndent())
        actualData.add("""
    taxID,weekBeginDate,name,gross
    623132658,20130318,ANDERSON/CARTER,5270.00
    623132658,20130325,ANDERSON/CARTER,5622.50
    623132658,20130401,ANDERSON/CARTER,3402.50
    623132658,20130408,ANDERSON/CARTER,3222
    623132658,20130415,ANDERSON/CARTER,3665.00 
    623132658,20130422,ANDERSON/CARTER,5285.00
    623132658,20130429,ANDERSON/CARTER,4475.00
    623132658,20130506,ANDERSON/CARTER,4665.00
    623132658,20130513,ANDERSON/CARTER,4377.50
    623132658,20130520,ANDERSON/CARTER,4745.00
    623132658,20130527,ANDERSON/CARTER,3957.50
    623132658,20130603,ANDERSON/CARTER,5675.00
    
    """.trimIndent())
        messages.add(listOf("1|   MATCHED|[taxID,weekBeginDate,name,gross]",
                            "2|   MATCHED|[623132658,20130318,ANDERSON/CARTER,5270.00]",
                            "3|   MATCHED|[623132658,20130325,ANDERSON/CARTER,5622.50]",
                            "4|  MISMATCH|[623132658,20130401,Anderson/CARTER,3402.50]",
                            "5|  MISMATCH|[623132658,20130480,ANDERSON/CARTER,3222.50]",
                            "6|  MISMATCH|[623132658,20130415,ANDERSON/CARTER,3665.00]",
                            "7|   MATCHED|[623132658,20130422,ANDERSON/CARTER,5285.00]",
                            "8|   MATCHED|[623132658,20130429,ANDERSON/CARTER,4475.00]",
                            "9|   MATCHED|[623132658,20130506,ANDERSON/CARTER,4665.00]",
                            "10|     ADDED|[623132658,20130513,ANDERSON/CARTER,4377.50]|missing in EXPECTED",
                            "11|     ADDED|[623132658,20130520,ANDERSON/CARTER,4745.00]|missing in EXPECTED",
                            "12|     ADDED|[623132658,20130527,ANDERSON/CARTER,3957.50]|missing in EXPECTED",
                            "10|   MATCHED|[623132658,20130603,ANDERSON/CARTER,5675.00]|ACTUAL moved to line 13"))
        val testCaseId = "simple_diff"
        val diffVar = "myDiff"
        // testCompare("compare_dangling_lines", expectedData, actualData, messages, false);

        val command = newIO()

        for (i in expectedData.indices) {
            println()
            println()
            println(StringUtils.repeat("*", 120))
            println("TEST CASE: $testCaseId #${i + 1}")

            val result = command.saveDiff(diffVar, expectedData[i], actualData[i])
            println("result = $result")
            assertNotNull(result)

            val diffs = context.getStringData(diffVar)
            println(diffs)
            val expectedErrors = messages[i]
            for (error in expectedErrors) {
                print("checking that logs contains '$error'... ")
                assertTrue(StringUtils.contains(diffs, error))
                println("PASSED!")
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testBase64() {
        val resourcePath = StringUtils.replace(ImageCommandTest::class.java.getPackage().name, ".", "/")
        val imageFile1 = ResourceUtils.getResourceFilePath("$resourcePath/overall.png")

        val io = newIO()

        val result = io.base64("myImageBase64", imageFile1)
        println("result = $result")
        assertNotNull(result)
        assertTrue(result.isSuccess)

        val base64 = context.getStringData("myImageBase64")
        println("base64 = $base64")
        assertNotNull(base64)

        val decoded = Base64.getDecoder().decode(base64)
        FileUtils.writeByteArrayToFile(File(dummyPng), decoded)
        val compareResult = io.compare(imageFile1, dummyPng, "true")
        println("compareResult = $compareResult")
        assertNotNull(compareResult)
        assertTrue(compareResult.isSuccess)
    }

    @Test
    @Throws(Throwable::class)
    fun testChecksum() {
        val basePath = javaClass.getPackage().name.replace(".", "/")
        assertEquals("c1d20cf59843cd209517f1b31186b1e73dcefdb96c7c20d09f6b49f2ef0ca91f",
                     IoCommand.checksum(ResourceUtils.getResourceFilePath("$basePath/checksum-target")))
        assertEquals("3e20709d538de2b4028b55884192617a1b0add3fda3540e93828f89e252e0bad",
                     IoCommand.checksum(ResourceUtils.getResourceFilePath("$basePath/checksum-target2")))
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {
        System.setProperty(OPT_OUT_DIR, tmpOutdir)
        context.setData(LOG_MATCH, true)
        FileUtils.deleteQuietly(File(testFile1))
        FileUtils.deleteQuietly(File(testDestination1))
        FileUtils.forceMkdir(File(testDestination1))
        FileUtils.deleteQuietly(File(testDestination2))
        FileUtils.forceMkdir(File(testDestination2))
        FileUtils.deleteQuietly(File(tmpOutdir))
    }

    @After
    fun tearDown() {
        FileUtils.deleteQuietly(File(testFile1))
        FileUtils.deleteQuietly(File(testFile2))
        FileUtils.deleteQuietly(File("$testFile1.testReadFile"))
        FileUtils.deleteQuietly(File("$testFile1.testRenameFiles"))
        FileUtils.deleteQuietly(File("$testFile1.txt"))
        FileUtils.deleteQuietly(File("$testFile2.txt"))
        FileUtils.deleteQuietly(File(testDestination1))
        FileUtils.deleteQuietly(File(testDestination2))
        FileUtils.deleteQuietly(File(tmpOutdir))
        FileUtils.deleteQuietly(File(dummyPng))
        (context as MockExecutionContext).cleanProject()
    }

    @Test
    @Throws(Throwable::class)
    fun testMatch() {
        makeDummyContent(testDestination2 + separator + "FileA.txt")
        makeDummyContent(testDestination2 + separator + "FileB.txt")
        makeDummyContent(testDestination2 + separator + "A.txt")
        makeDummyContent(testDestination2 + separator + "B.txt")
        makeDummyContent(testDestination2 + separator + "A1.txt")
        makeDummyContent(testDestination2 + separator + "B2.txt")

        context.setData(OPT_IO_MATCH_RECURSIVE, false)
        val io = newIO()

        run {
            context.setData(OPT_IO_MATCH_EXACT, true)
            val matches = io.listMatches(testDestination2, "[A-Z]\\.txt")
            assertNotNull(matches)
            assertEquals(2, matches.size.toLong())
            assertEquals("A.txt", matches[0].name)
            assertEquals("B.txt", matches[1].name)
        }

        run {
            context.setData(OPT_IO_MATCH_EXACT, false)
            val matches = io.listMatches(testDestination2, "[A-Z]\\.txt")
            assertNotNull(matches)
            assertEquals(4, matches.size.toLong())
            assertEquals("A.txt", matches[0].name)
            assertEquals("B.txt", matches[1].name)
            assertEquals("FileA.txt", matches[2].name)
            assertEquals("FileB.txt", matches[3].name)
        }

        run {
            context.setData(OPT_IO_MATCH_EXACT, false)
            val matches = io.listMatches(testDestination2, "[\\w][\\d]\\.txt")
            assertNotNull(matches)
            assertEquals(2, matches.size.toLong())
            assertEquals("A1.txt", matches[0].name)
            assertEquals("B2.txt", matches[1].name)
        }

        run {
            context.setData(OPT_IO_MATCH_EXACT, true)
            val matches = io.listMatches(testDestination2, "[\\w][\\d]?\\.txt")
            assertNotNull(matches)
            assertEquals(4, matches.size.toLong())
            assertEquals("A.txt", matches[0].name)
            assertEquals("A1.txt", matches[1].name)
            assertEquals("B.txt", matches[2].name)
            assertEquals("B2.txt", matches[3].name)
        }

        run {
            context.setData(OPT_IO_MATCH_EXACT, false)
            val matches = io.listMatches(testDestination2, "[\\w][\\d]?\\.txt")
            assertNotNull(matches)
            assertEquals(6, matches.size.toLong())
            assertEquals("A.txt", matches[0].name)
            assertEquals("A1.txt", matches[1].name)
            assertEquals("B.txt", matches[2].name)
            assertEquals("B2.txt", matches[3].name)
            assertEquals("FileA.txt", matches[4].name)
            assertEquals("FileB.txt", matches[5].name)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testMatchEmptyFile() {
        makeEmptyFile(testDestination2 + separator + "Data1.txt")
        makeEmptyFile(testDestination2 + separator + "Data2.txt")
        makeEmptyFile(testDestination2 + separator + "1.txt")
        makeEmptyFile(testDestination2 + separator + "2.txt")
        makeEmptyFile(testDestination2 + separator + "1a.txt")
        makeEmptyFile(testDestination2 + separator + "2b.txt")

        context.setData(OPT_IO_MATCH_RECURSIVE, false)
        val io = newIO()

        run {
            context.setData(OPT_IO_MATCH_EXACT, true)
            val matches = io.listMatches(testDestination2, "[0-9]\\.txt")
            assertNotNull(matches)
            assertEquals(2, matches.size.toLong())
            assertEquals("1.txt", matches[0].name)
            assertEquals("2.txt", matches[1].name)
        }

        run {
            context.setData(OPT_IO_MATCH_EXACT, false)
            val matches = io.listMatches(testDestination2, "[0-9]\\.txt")
            assertNotNull(matches)
            assertEquals(4, matches.size.toLong())
            assertEquals("1.txt", matches[0].name)
            assertEquals("2.txt", matches[1].name)
            assertEquals("Data1.txt", matches[2].name)
            assertEquals("Data2.txt", matches[3].name)
        }

        run {
            context.setData(OPT_IO_MATCH_EXACT, false)
            val matches = io.listMatches(testDestination2, "[\\d][a-z]\\.txt")
            assertNotNull(matches)
            assertEquals(2, matches.size.toLong())
            assertEquals("1a.txt", matches[0].name)
            assertEquals("2b.txt", matches[1].name)
        }

        run {
            context.setData(OPT_IO_MATCH_EXACT, true)
            val matches = io.listMatches(testDestination2, "[\\d][a-z]?\\.txt")
            assertNotNull(matches)
            assertEquals(4, matches.size.toLong())
            assertEquals("1.txt", matches[0].name)
            assertEquals("1a.txt", matches[1].name)
            assertEquals("2.txt", matches[2].name)
            assertEquals("2b.txt", matches[3].name)
        }

        run {
            context.setData(OPT_IO_MATCH_EXACT, false)
            val matches = io.listMatches(testDestination2, "[\\d][a-z]?\\.txt")
            assertNotNull(matches)
            assertEquals(6, matches.size.toLong())
            assertEquals("1.txt", matches[0].name)
            assertEquals("1a.txt", matches[1].name)
            assertEquals("2.txt", matches[2].name)
            assertEquals("2b.txt", matches[3].name)
            assertEquals("Data1.txt", matches[4].name)
            assertEquals("Data2.txt", matches[5].name)
        }
    }

    private fun newIO(): IoCommand {
        val command = IoCommand()
        command.init(context)
        return command
    }

    @Throws(IOException::class)
    private fun zip_create_test_fixture(basePath: String) {
        val numberOfFiles = 10
        for (i in 0 until numberOfFiles) {
            FileUtils.writeStringToFile(File("$basePath$i.txt"), RandomStringUtils.randomAlphabetic(100), "UTF-8")
        }
    }

    private fun zip_check_result(result: StepResult) {
        println("result = $result")
        assertTrue(result.isSuccess)
    }

    private fun zip_check_zip_file(zipFilePath: String) {
        val zipFile = File(zipFilePath)
        assertTrue(zipFile.isFile)
        assertTrue(zipFile.canRead())
        assertTrue(zipFile.length() > 1)

        // byte[] zipContent = FileUtils.readFileToByteArray(zipFile);
        // for (byte aZipContent : zipContent) { System.out.print(aZipContent); }
    }

    private fun zip_check_unzipped_files(dir: String, expectedCount: Int) {
        val unzipFiles = FileUtils.listFiles(File(dir), arrayOf("txt"), false)
        assertEquals(expectedCount.toLong(), unzipFiles.size.toLong())
        unzipFiles.forEach {
            println(it)
            assertEquals(100, it.length())
            assertTrue(NumberUtils.isDigits(it.name.substring(0, 1)))
            assertTrue(StringUtils.endsWith(it.name, ".txt"))
        }
    }

    private fun zip_cleanup(basePath: String) {
        // tear down
        try {
            FileUtils.deleteDirectory(File(basePath))
        } catch (e: IOException) {
            System.err.println("Can't delete directory $basePath")
            e.printStackTrace()
        }
    }

    companion object {
        @Throws(IOException::class)
        fun makeDummyContent(dummyFilePath: String): File {
            // dummy content
            val text = (0..9).map { Random().alphanumeric("100") }
            val file = File(dummyFilePath)
            file.delete()
            FileUtil.appendFile(file, text, "\n")
            return file
        }

        @Throws(IOException::class)
        fun makeEmptyFile(dummyFilePath: String): File {
            val file = File(dummyFilePath)
            file.delete()
            file.createNewFile()
            return file
        }
    }
}