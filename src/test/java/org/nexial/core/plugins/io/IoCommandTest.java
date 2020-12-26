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
 *
 */

package org.nexial.core.plugins.io;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.image.ImageCommandTest;
import org.nexial.core.variable.Random;
import org.nexial.core.variable.Sysdate;
import org.nexial.core.variable.Syspath;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.junit.Assert.assertEquals;
import static org.nexial.core.NexialConst.Compare.*;
import static org.nexial.core.NexialConst.*;

public class IoCommandTest {

    private final String baseLocation = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(),
                                                                    separator);
    private final String tmpOutdir = baseLocation + IoCommandTest.class.getSimpleName();
    private final String testFile1 = baseLocation + "dummy1";
    private final String testFile2 = baseLocation + "dummy2";
    private final String testFile3 = baseLocation + "dummy3";
    private final String testDestination1 = baseLocation + "newloc";
    private final String testDestination2 = baseLocation + "testMatch";
    private final String testDestination3 = baseLocation + "newloc2";
    private final String basePath = baseLocation + this.getClass().getSimpleName() + separator;
    private final String dummyPng = baseLocation + "dummy.png";

    private final ExecutionContext context = new MockExecutionContext() {
        @Override
        public String replaceTokens(String text) {
            builtinFunctions = new HashedMap<>();
            builtinFunctions.put("sysdate", new Sysdate());
            builtinFunctions.put("syspath", new Syspath());
            return super.replaceTokens(text);
        }
    };

    protected static File makeDummyContent(String dummyFilePath) throws IOException {
        // dummy content
        List<String> text = new ArrayList<>();
        for (int i = 0; i < 10; i++) { text.add(new Random().alphanumeric("100")); }

        File file = new File(dummyFilePath);
        file.delete();
        FileUtil.appendFile(file, text, "\n");
        return file;
    }

    protected static File makeEmptyFile(String dummyFilePath) throws IOException {
        File file = new File(dummyFilePath);
        file.delete();
        file.createNewFile();
        return file;
    }

    @Test
    public void copy_file_to_dir() throws Exception {
        File sourceFile = makeDummyContent(testFile3);

        // make sure the file is really written to disk
        int expectedFileSize = 100 * 10;
        Assert.assertTrue(sourceFile.length() >= expectedFileSize);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.copyFiles(testFile3, testDestination1);

        File destinationFile = new File(testDestination1 + separator + "dummy3");

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(destinationFile.length() >= expectedFileSize);
        assertEquals(FileUtils.readFileToString(sourceFile, DEF_CHARSET),
                     FileUtils.readFileToString(destinationFile, DEF_CHARSET));
    }

    @Test
    public void copy_jar_resource_to_dir() {
        // .../lib/selenium-java-2.39.0.jar!/org/openqa/selenium/firefox/webdriver.xpi

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.copyFiles("jar:/org/openqa/selenium/firefox/webdriver.xpi", testDestination1);

        File destinationFile = new File(testDestination1 + separator + "webdriver.xpi");
        long expectedFileSize = 685 * 1024;

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(destinationFile.exists());
        Assert.assertTrue(destinationFile.length() > expectedFileSize);
        System.out.println("using IoCommand; copied to " + destinationFile.getAbsolutePath());
    }

    @Test
    public void copy_wildcard_files_to_dir() throws Exception {
        String sourceDir = baseLocation + RandomStringUtils.randomAlphanumeric(5) + separator;
        makeDummyContent(sourceDir + "_E4_test1_step4_myLogs.txt");
        makeDummyContent(sourceDir + "_E4_test1_step4_hello.log");
        makeDummyContent(sourceDir + "_E4_test1_step4_190FFV.csv");

        IoCommand io = new IoCommand();
        io.init(context);

        String sourcePattern = sourceDir + "_E4_test1_step4*.*";
        StepResult result = io.copyFiles(sourcePattern, testDestination1);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        Collection<File> destinationFiles = FileUtils.listFiles(new File(testDestination1), new IOFileFilter() {
            @Override
            public boolean accept(File file) { return file.canRead() && file.isFile(); }

            @Override
            public boolean accept(File dir, String name) { return name.startsWith("_E4_test1_step4_"); }
        }, null);

        Assert.assertNotNull(destinationFiles);
        assertEquals(3, destinationFiles.size());
        List<String> destinationPaths = new ArrayList<>();

        destinationFiles.forEach(f -> destinationPaths.add(f.getAbsolutePath()));
        Assert.assertTrue(destinationPaths.contains(testDestination1 + separator + "_E4_test1_step4_myLogs.txt"));
        Assert.assertTrue(destinationPaths.contains(testDestination1 + separator + "_E4_test1_step4_hello.log"));
        Assert.assertTrue(destinationPaths.contains(testDestination1 + separator + "_E4_test1_step4_190FFV.csv"));

        FileUtils.deleteDirectory(new File(sourceDir));
    }

    @Test
    public void move_wildcard_files_to_dir() throws Exception {
        String sourceDir = baseLocation + RandomStringUtils.randomAlphanumeric(5) + separator;
        File file1 = makeDummyContent(sourceDir + "_E3_test1_step4_myLogs.txt");
        File file2 = makeDummyContent(sourceDir + "_E3_test1_step4_hello.log");
        File file3 = makeDummyContent(sourceDir + "_E3_test1_step4_190FFV.csv");

        IoCommand io = new IoCommand();
        io.init(context);

        String sourcePattern = sourceDir + "_E3_test1_step4*.*";
        String destinationDir = testDestination3 + separator + RandomStringUtils.randomAlphanumeric(5);
        new File(destinationDir).mkdirs();
        StepResult result = io.moveFiles(sourcePattern, destinationDir);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        Collection<File> destinationFiles = FileUtils.listFiles(new File(destinationDir), new IOFileFilter() {
            @Override
            public boolean accept(File file) { return file.canRead() && file.isFile(); }

            @Override
            public boolean accept(File dir, String name) { return name.startsWith("_E3_test1_step4_"); }
        }, null);

        Assert.assertNotNull(destinationFiles);
        assertEquals(3, destinationFiles.size());
        List<String> destinationPaths = new ArrayList<>();

        destinationFiles.forEach(f -> destinationPaths.add(f.getAbsolutePath()));
        Assert.assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_myLogs.txt"));
        Assert.assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_hello.log"));
        Assert.assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_190FFV.csv"));
        Assert.assertFalse(file1.exists());
        Assert.assertFalse(file2.exists());
        Assert.assertFalse(file3.exists());

        FileUtils.deleteDirectory(new File(sourceDir));
    }

    @Test
    public void move_wildcard_files_to_single_file() throws Exception {
        String sourceDir = baseLocation + RandomStringUtils.randomAlphanumeric(5) + separator;
        File file1 = makeDummyContent(sourceDir + "_E5_test1_step4_myLogs.txt");
        File file2 = makeDummyContent(sourceDir + "_E5_test1_step4_hello.log");
        File file3 = makeDummyContent(sourceDir + "_E5_test1_step4_190FFV.csv");

        IoCommand io = new IoCommand();
        io.init(context);
        context.setData(OPT_IO_COPY_CONFIG, COPY_CONFIG_OVERRIDE);

        String sourcePathPattern = sourceDir + "_E5_test1_step4*.*";
        String destinationDir = testDestination1 + separator + RandomStringUtils.randomAlphanumeric(5);
        StepResult result = io.moveFiles(sourcePathPattern, destinationDir + separator + "myFile.text");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        Collection<File> destinationFiles = FileUtils.listFiles(new File(destinationDir), new IOFileFilter() {
            @Override
            public boolean accept(File file) { return file.canRead() && file.isFile(); }

            @Override
            public boolean accept(File dir, String name) { return name.startsWith("myFile.text"); }
        }, null);

        Assert.assertNotNull(destinationFiles);
        assertEquals(1, destinationFiles.size());
        List<String> destinationPaths = new ArrayList<>();

        destinationFiles.forEach(f -> destinationPaths.add(f.getAbsolutePath()));
        Assert.assertTrue(destinationPaths.contains(destinationDir + separator + "myFile.text"));
        Assert.assertFalse(file1.exists());
        Assert.assertFalse(file2.exists());
        Assert.assertFalse(file3.exists());

        FileUtils.deleteDirectory(new File(sourceDir));
    }

    @Test
    public void testUnzip() {
        // ...lib/selenium-java-2.39.0.jar!/org/openqa/selenium/firefox/webdriver.xpi

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.unzip("jar:/org/openqa/selenium/firefox/webdriver.xpi", testDestination1);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        Collection<File> files = FileUtils.listFiles(new File(testDestination1), null, true);
        Assert.assertTrue(CollectionUtils.isNotEmpty(files));
        System.out.println(files);
    }

    @Test
    public void testMakeDirectory() {
        String newLoc = testDestination1 + separator + "newDir2";

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.makeDirectory(newLoc);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        File newDir = new File(newLoc);
        Assert.assertTrue(newDir.exists());
        Assert.assertTrue(newDir.isDirectory());
        Assert.assertTrue(newDir.canRead());
    }

    @Test
    public void testMoveFiles() throws Exception {
        File sourceFile = makeDummyContent(testFile1);
        long expectedFileLength = sourceFile.length();

        String targetDir = testDestination1;
        File targetFile = new File(targetDir + separator + sourceFile.getName());
        FileUtils.deleteQuietly(targetFile);

        String fullPath = targetFile.getAbsolutePath();
        System.out.println("checking existence for " + fullPath + "? " + FileUtil.isFileReadable(fullPath));

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.moveFiles(testFile1, targetDir);
        System.out.println("result = " + result);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        Assert.assertTrue(targetFile.exists());
        Assert.assertTrue(targetFile.canRead());
        assertEquals(expectedFileLength, targetFile.length());

        Assert.assertFalse(sourceFile.exists());

        FileUtils.deleteQuietly(targetFile);
    }

    @Test
    public void testDeleteFiles() throws Exception {
        makeDummyContent(testDestination1 + separator + "testDeleteFiles1.txt");
        makeDummyContent(testDestination1 + separator + "testDeleteFiles2.txt");
        makeDummyContent(testDestination1 + separator + "testDeleteFiles3.txt");
        makeDummyContent(testDestination1 + separator + "sub" + separator + "testDeleteFiles4.txt");
        makeDummyContent(testDestination1 + separator + "sub" + separator + "testDeleteFiles5.txt");

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.deleteFiles(testDestination1, "false");
        System.out.println("result = " + result);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        File dir = new File(testDestination1);
        Collection<File> files =
            FileUtils.listFiles(dir, new RegexFileFilter("testDeleteFile.+"), FalseFileFilter.INSTANCE);
        Assert.assertTrue(CollectionUtils.isEmpty(files));

        dir = new File(testDestination1 + separator + "sub");
        files = FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, FalseFileFilter.INSTANCE);
        assertEquals(CollectionUtils.size(files), 2);
    }

    @Test
    public void testRenameFiles() throws Exception {
        String fixture1 = testDestination1 + separator + "testRenameFiles1.txt";
        makeDummyContent(fixture1);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.rename(fixture1, "fingerLickingGood.doc");
        System.out.println("result = " + result);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        File sourceFile = new File(fixture1);
        Assert.assertFalse(sourceFile.exists());

        File targetFile = new File(testDestination1 + separator + "fingerLickingGood.doc");
        Assert.assertTrue(targetFile.exists());
        Assert.assertTrue(targetFile.canRead());
    }

    @Test
    public void testReadFile() throws Exception {
        String myTestFile = testFile1 + ".testReadFile";
        File file = makeDummyContent(myTestFile);
        Thread.sleep(3000);

        long expectedLength = file.length();
        System.out.println("expectedLength = " + expectedLength);

        String varName = "testFile1";
        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.readFile(varName, myTestFile);
        System.out.println("result = " + result);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        String fileContent = context.getStringData(varName);
        System.out.println("fileContent = " + fileContent);
        Assert.assertNotNull(fileContent);
        assertEquals(expectedLength, fileContent.length());
        Assert.assertTrue(fileContent.length() > 1000);
    }

    @Test
    public void testReadFile_xml() throws Exception {
        String resourceFile = "/org/nexial/core/plugins/io/IOCommandTest_1.xml";
        String resource = ResourceUtils.getResourceFilePath(resourceFile);

        System.setProperty(OPT_DATA_DIR, "~/tmp/nexial/artifact/data");

        context.setData("ConfigFile", "$(syspath|data|fullpath)/Configurations.csv");
        String varName = "testFile1";
        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.readFile(varName, resource);
        System.out.println("result = " + result);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        String fileContent = context.getStringData(varName);
        System.out.println("fileContent = " + fileContent);
        Assert.assertNotNull(fileContent);
        Assert.assertTrue(StringUtils.contains(fileContent, "\\"));
    }

    @Test
    public void testSaveFileMeta() throws IOException {

        String myTestFile = testFile1 + ".txt";
        String enteredText = "623132658,20130318,ANDERSON/CARTER,5270.00";
        IoCommand io = new IoCommand();
        io.init(context);
        io.writeFile(myTestFile, enteredText, "true");
        StepResult result = io.saveFileMeta("myData", myTestFile);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        File file = new File(myTestFile);
        FileMeta filemeta = new FileMeta(file);

        assertEquals(myTestFile, filemeta.getFullpath());
        assertEquals(42, filemeta.getSize());
        assertEquals(enteredText, filemeta.getText());

        assertEquals(filemeta.isReadable(), file.canRead());
        assertEquals(filemeta.isWritable(), file.canWrite());
        assertEquals(filemeta.isExecutable(), file.canExecute());
        assertEquals(filemeta.getLastmod(), file.lastModified());

        //Update same file and validate the changes
        String updateText = "623132658,20130318,ANDERSON/UPDATED,5271.01";
        io.writeFile(myTestFile, updateText, "false");
        result = io.saveFileMeta("myData", myTestFile);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        File updatedFile = new File(myTestFile);
        FileMeta updatedMeta = new FileMeta(updatedFile);
        Assert.assertNotEquals(filemeta.getText(), updatedMeta.getText());

        assertEquals(myTestFile, updatedMeta.getFullpath());
        assertEquals(43, filemeta.getSize());
        assertEquals(updateText, updatedMeta.getText());

        assertEquals(updatedMeta.isReadable(), updatedFile.canRead());
        assertEquals(updatedMeta.isWritable(), updatedFile.canWrite());
        assertEquals(updatedMeta.isExecutable(), updatedFile.canExecute());
        assertEquals(updatedMeta.getLastmod(), updatedFile.lastModified());

    }

    @Test
    public void testReadProperty() throws Exception {
        String propContent = "prop1=a\n" +
                             "prop2=b\n" +
                             "prop3=${prop1}-${prop2}\n";
        File propFile = new File(testFile1);
        FileUtils.write(propFile, propContent, DEF_CHARSET);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.readProperty("prop1", testFile1, "prop1");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        assertEquals("a", context.getStringData("prop1"));

        result = io.readProperty("prop2", testFile1, "prop2");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        assertEquals("b", context.getStringData("prop2"));

        result = io.readProperty("prop3", testFile1, "prop3");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        assertEquals("a-b", context.getStringData("prop3"));
    }

    @Test
    public void testWriteFile() throws Exception {
        String propContent = "prop1=a\n" +
                             "prop2=b\n" +
                             "prop3=${prop1}-${prop2}\n";

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.writeFile(testFile1, propContent, "false");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        File file1 = new File(testFile1);
        Properties prop = ResourceUtils.loadProperties(file1);
        assertEquals("a", prop.getProperty("prop1"));
        assertEquals("b", prop.getProperty("prop2"));
        assertEquals("-", prop.getProperty("prop3"));
    }

    @Test
    public void testEol_simple() throws Exception {
        String propContent = "prop1=a\n" +
                             "prop2=b\n" +
                             "prop3=${prop1}-${prop2}\n";

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());

        File file1 = new File(testFile1);

        // check testFile1 for eol
        String content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);

        // eol default (platform) test
        // default is platform-specific eol
        String eol = lineSeparator();
        assertEquals("prop1=a" + eol +
                     "prop2=b" + eol +
                     "prop3=-" + eol,
                     content);

        // eol as is test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_AS_IS);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\n";
        assertEquals("prop1=a" + eol +
                     "prop2=b" + eol +
                     "prop3=-" + eol,
                     content);

        // eol for windows test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_WINDOWS);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\r\n";
        assertEquals("prop1=a" + eol +
                     "prop2=b" + eol +
                     "prop3=-" + eol,
                     content);

        // eol for linux test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_UNIX);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\n";
        assertEquals("prop1=a" + eol +
                     "prop2=b" + eol +
                     "prop3=-" + eol,
                     content);
    }

    @Test
    public void testEol_mixed() throws Exception {
        String propContent = "Now is the time for all good men to come to the aid of his country\n" +
                             "The lazy brown fox jump over the dog, \r\n" +
                             "or something like that.\n\n" +
                             "Now is the winter of your discontent\r\n\r\n" +
                             "Good bye\n";

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());

        File file1 = new File(testFile1);

        // check testFile1 for eol
        String content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);

        // eol default (platform) test
        // default is platform-specific eol
        String eol = lineSeparator();
        assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
                     "The lazy brown fox jump over the dog, " + eol +
                     "or something like that." + eol + eol +
                     "Now is the winter of your discontent" + eol + eol +
                     "Good bye" + eol,
                     content);

        // eol as is test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_AS_IS);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\n";
        assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
                     "The lazy brown fox jump over the dog, " + eol +
                     "or something like that." + eol + eol +
                     "Now is the winter of your discontent" + eol + eol +
                     "Good bye" + eol,
                     content);

        // eol for windows test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_WINDOWS);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\r\n";
        assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
                     "The lazy brown fox jump over the dog, " + eol +
                     "or something like that." + eol + eol +
                     "Now is the winter of your discontent" + eol + eol +
                     "Good bye" + eol,
                     content);

        // eol for linux test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_UNIX);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\n";
        assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
                     "The lazy brown fox jump over the dog, " + eol +
                     "or something like that." + eol + eol +
                     "Now is the winter of your discontent" + eol + eol +
                     "Good bye" + eol,
                     content);
    }

    @Test
    public void testWriteFileAsIs() throws Exception {
        String propContent = "prop1=a\n" +
                             "prop2=b\n" +
                             "prop3=${prop1}-${prop2}\n";

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.writeFileAsIs(testFile1, propContent, "false");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        Properties prop = ResourceUtils.loadProperties(new File(testFile1));
        assertEquals("a", prop.getProperty("prop1"));
        assertEquals("b", prop.getProperty("prop2"));
        assertEquals("${prop1}-${prop2}", prop.getProperty("prop3"));
    }

    @Test
    public void testWriteProperty() throws Exception {
        String propContent = "prop1=a\n" +
                             "prop2=b\n" +
                             "prop3=${prop1}-${prop2}\n";
        File propFile = new File(testFile1);
        FileUtils.write(propFile, propContent, DEF_CHARSET);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.writeProperty(testFile1, "prop4", "c");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        result = io.writeProperty(testFile1, "prop5", "${prop1}-${prop2}-${prop3}-${prop4}");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        Properties prop = ResourceUtils.loadProperties(new File(testFile1));
        assertEquals("a", prop.getProperty("prop1"));
        assertEquals("b", prop.getProperty("prop2"));
        assertEquals("${prop1}-${prop2}", prop.getProperty("prop3"));
        assertEquals("c", prop.getProperty("prop4"));
        assertEquals("${prop1}-${prop2}-${prop3}-${prop4}", prop.getProperty("prop5"));
    }

    @Test
    public void testAssertEqual() {

        String propContent = "prop1=a\n" +
                             "prop2=b\n" +
                             "prop3=${prop1}-${prop2}\n";

        String propContent2 = "prop1=a\n" +
                              "prop2=b\n" +
                              "prop3=${prop1}-${prop2}\n" +
                              "prop4=${prop3}+${prop2}\n";

        IoCommand io = new IoCommand();
        io.init(context);

        String myTestFile1 = testFile1 + ".txt";
        String myTestFile2 = testFile2 + ".txt";

        io.writeFile(myTestFile1, propContent, "false");
        io.writeFile(myTestFile2, propContent, "false");

        StepResult result = io.assertEqual(myTestFile1, myTestFile2);
        Assert.assertTrue(result.isSuccess());

        io.writeFile(myTestFile1, propContent, "true");
        io.writeFile(myTestFile2, propContent, "true");
        result = io.assertEqual(myTestFile1, myTestFile2);
        Assert.assertTrue(result.isSuccess());

        io.writeFile(myTestFile1, propContent2, "true");
        result = io.assertEqual(myTestFile1, myTestFile2);
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void testAssertNotEqual() {

        String propContent1 = "prop1=a\n" +
                              "prop1=b\n" +
                              "prop1=${prop1}-${prop1}\n";

        String propContent2 = "prop1=c\n" +
                              "prop1=d\n" +
                              "prop1=${prop1}-${prop1}\n";

        String myTestFile1 = testFile1 + ".txt";
        String myTestFile2 = testFile2 + ".txt";

        IoCommand io = new IoCommand();
        io.init(context);

        io.writeFile(myTestFile1, propContent1, "false");
        io.writeFile(myTestFile2, propContent2, "false");
        StepResult result = io.assertNotEqual(myTestFile1, myTestFile2);
        Assert.assertTrue(result.isSuccess());

        io.writeFile(myTestFile1, propContent1, "true");
        io.writeFile(myTestFile2, propContent2, "true");
        result = io.assertNotEqual(myTestFile1, myTestFile2);
        Assert.assertTrue(result.isSuccess());

        io.deleteFiles(myTestFile2, "false");
        io.writeFile(myTestFile2, propContent1, "true");
        io.writeFile(myTestFile2, propContent1, "true");

        result = io.assertNotEqual(myTestFile1, myTestFile2);
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void testCompare() {

        String myTestFile1 = testFile1 + ".txt";
        String myTestFile2 = testFile2 + ".txt";

        List<String> file1Data = new ArrayList<>();
        List<String> file2Data = new ArrayList<>();

        file1Data.add("taxID,weekBeginDate,name,gross\n" +
                      "623132658,20130318,ANDERSON/CARTER,5270.00\n" +
                      "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
                      "623132658,20130401,ANDERSON/CARTER,3402.50\n" +
                      "623132658,20130408,ANDERSON/CARTER,3222.50\n" +
                      "623132658,20130603,ANDERSON/CARTER,5675.00\n");

        file2Data.add("taxID,weekBeginDate,name,gross\n" +
                      "623132658,20130318,ANDERSON/CARTER,5270.00\n" +
                      "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
                      "623132658,20130401,ANDERSON/CARTER,3402.50\n" +
                      "623132658,20130408,ANDERSON/CARTER,3222.50\n" +
                      "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
                      "623132658,20130603,ANDERSON/CARTER,5675.00\n");

        IoCommand io = new IoCommand();
        io.init(context);
        io.writeFile(myTestFile1, String.valueOf(file1Data), "true");
        io.writeFile(myTestFile2, String.valueOf(file2Data), "true");

        StepResult result = io.compare(myTestFile1, myTestFile2, "false");
        Assert.assertFalse(result.isSuccess());

        io.deleteFiles(myTestFile2, "false");
        io.writeFile(myTestFile2, String.valueOf(file1Data), "true");

        result = io.compare(myTestFile1, myTestFile2, "false");
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void testFileReadable() {
        String propContent = "prop1=a\n" +
                             "prop2=b\n" +
                             "prop3=${prop1}-${prop2}\n";

        String myTestFile = testFile1 + ".txt";
        IoCommand io = new IoCommand();
        io.init(context);

        io.writeFile(myTestFile, propContent, "false");
        StepResult result = io.assertReadableFile(myTestFile, "-1");
        Assert.assertTrue(result.isSuccess());
        result = io.assertReadableFile(myTestFile, "10");
        Assert.assertTrue(result.isSuccess());
        result = io.assertReadableFile(myTestFile, "");
        Assert.assertTrue(result.isSuccess());

    }

    @Test
    public void testZip() throws Exception {
        // setup
        String zipFilePath = basePath + "testZip1.zip";
        String unzipPath = basePath + "unzip";

        zip_create_test_fixture(basePath);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.zip(basePath + "*.txt", zipFilePath);
        zip_check_result(result);
        zip_check_zip_file(zipFilePath);

        Assert.assertTrue(io.unzip(zipFilePath, unzipPath).isSuccess());
        zip_check_unzipped_files(unzipPath, 10);

        zip_cleanup(basePath);
    }

    @Test
    public void testZip1() throws Exception {
        // setup
        String zipFilePath = basePath + "testZip1.zip";
        String unzipPath = basePath + "unzip";

        zip_create_test_fixture(basePath);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.zip(basePath, zipFilePath);
        zip_check_result(result);
        zip_check_zip_file(zipFilePath);

        Assert.assertTrue(io.unzip(zipFilePath, unzipPath).isSuccess());
        zip_check_unzipped_files(unzipPath, 10);

        // tear down
        zip_cleanup(basePath);
    }

    @Test
    public void testZip2() throws Exception {
        // setup
        String zipFilePath = basePath + "testZip2.zip";
        String unzipPath = basePath + "unzip";

        zip_create_test_fixture(basePath);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.zip(StringUtils.removeEnd(basePath, separator), zipFilePath);
        zip_check_result(result);
        zip_check_zip_file(zipFilePath);

        Assert.assertTrue(io.unzip(zipFilePath, unzipPath).isSuccess());
        zip_check_unzipped_files(unzipPath, 10);

        // tear down
        zip_cleanup(basePath);
    }

    @Test
    public void testZip3() throws Exception {
        // setup
        String zipFilePath = basePath + "testZip3.zip";
        String unzipPath = basePath + "unzip";

        zip_create_test_fixture(basePath);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.zip(basePath + "1*.*", zipFilePath);
        zip_check_result(result);
        zip_check_zip_file(zipFilePath);

        Assert.assertTrue(io.unzip(zipFilePath, unzipPath).isSuccess());
        zip_check_unzipped_files(unzipPath, 1);

        // tear down
        zip_cleanup(basePath);
    }

    @Test
    public void testCount() throws Exception {

        String testDirectory = testDestination1 + separator;
        String filePattern1 = ".*\\.txt";
        String filePattern2 = ".*\\.csv";

        makeDummyContent(testDirectory + "testCountFiles1.txt");
        makeDummyContent(testDirectory + "testCountFiles2.txt");
        makeDummyContent(testDirectory + "testCountFiles3.txt");
        makeDummyContent(testDirectory + "testCountFiles4.txt");

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.count("fileCount", testDirectory, filePattern1);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        assertEquals("4", context.getStringData("fileCount"));

        makeDummyContent(testDirectory + "testAddFiles1.txt");
        makeDummyContent(testDirectory + "testAddFiles2.txt");
        result = io.count("fileCount", testDirectory, filePattern1);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        assertEquals("6", context.getStringData("fileCount"));

        File dir = new File(testDirectory + "testCountFiles1.txt");
        dir.delete();
        makeDummyContent(testDirectory + "testCountFiles3.csv");
        makeDummyContent(testDirectory + "testCountFiles4.csv");

        result = io.count("fileCount", testDirectory, filePattern2);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        assertEquals("2", context.getStringData("fileCount"));

        result = io.count("fileCount", testDirectory, filePattern1);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        assertEquals("5", context.getStringData("fileCount"));
    }

    @Test
    public void saveDiff() {
        List<String> expectedData = new ArrayList<>();
        List<String> actualData = new ArrayList<>();
        List<List<String>> messages = new ArrayList<>();

        // happy path
        expectedData.add("taxID,weekBeginDate,name,gross\n" +
                         "623132658,20130318,ANDERSON/CARTER,5270.00\n" +
                         "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
                         "623132658,20130401,ANDERSON/CARTER,3402.50\n" +
                         "623132658,20130408,ANDERSON/CARTER,3222.50\n" +
                         "623132658,20130415,ANDERSON/CARTER,3665.00\n" +
                         "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
                         "623132658,20130429,ANDERSON/CARTER,4475.00\n" +
                         "623132658,20130506,ANDERSON/CARTER,4665.00\n" +
                         "623132658,20130513,ANDERSON/CARTER,4377.50\n" +
                         "623132658,20130520,ANDERSON/CARTER,4745.00\n" +
                         "623132658,20130527,ANDERSON/CARTER,3957.50\n" +
                         "623132658,20130603,ANDERSON/CARTER,5675.00\n");
        actualData.add("taxID,weekBeginDate,name,gross\n" +
                       "623132658,20130318,ANDERSON/CARTER,5270.00\n" +
                       "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
                       "623132658,20130401,ANDERSON/CARTER,3402.50\n" +
                       "623132658,20130408,ANDERSON/CARTER,3222.50\n" +
                       "623132658,20130415,ANDERSON/CARTER,3665.00\n" +
                       "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
                       "623132658,20130429,ANDERSON/CARTER,4475.00\n" +
                       "623132658,20130506,ANDERSON/CARTER,4665.00\n" +
                       "623132658,20130513,ANDERSON/CARTER,4377.50\n" +
                       "623132658,20130520,ANDERSON/CARTER,4745.00\n" +
                       "623132658,20130527,ANDERSON/CARTER,3957.50\n" +
                       "623132658,20130603,ANDERSON/CARTER,5675.00\n");
        messages.add(Collections.singletonList("content matched exactly"));

        expectedData.add("taxID,weekBeginDate,name,gross\n" +
                         "623132658,20130318,ANDERSON/CARTER,5270.00\n" +
                         "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
                         "623132658,20130401,Anderson/CARTER,3402.50\n" +
                         "623132658,20130480,ANDERSON/CARTER,3222.50\n" +
                         "623132658,20130415,ANDERSON/CARTER,3665.00\n" +
                         "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
                         "623132658,20130429,ANDERSON/CARTER,4475.00\n" +
                         "623132658,20130506,ANDERSON/CARTER,4665.00\n" +
                         "623132658,20130603,ANDERSON/CARTER,5675.00\n");
        actualData.add("taxID,weekBeginDate,name,gross\n" +
                       "623132658,20130318,ANDERSON/CARTER,5270.00\n" +
                       "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
                       "623132658,20130401,ANDERSON/CARTER,3402.50\n" +
                       "623132658,20130408,ANDERSON/CARTER,3222\n" +
                       "623132658,20130415,ANDERSON/CARTER,3665.00 \n" +
                       "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
                       "623132658,20130429,ANDERSON/CARTER,4475.00\n" +
                       "623132658,20130506,ANDERSON/CARTER,4665.00\n" +
                       "623132658,20130513,ANDERSON/CARTER,4377.50\n" +
                       "623132658,20130520,ANDERSON/CARTER,4745.00\n" +
                       "623132658,20130527,ANDERSON/CARTER,3957.50\n" +
                       "623132658,20130603,ANDERSON/CARTER,5675.00\n");
        messages.add(Arrays.asList("1|   MATCHED|[taxID,weekBeginDate,name,gross]",
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
                                   "10|   MATCHED|[623132658,20130603,ANDERSON/CARTER,5675.00]|ACTUAL moved to line 13"));

        String testCaseId = "simple_diff";
        String diffVar = "myDiff";
        // testCompare("compare_dangling_lines", expectedData, actualData, messages, false);

        IoCommand command = new IoCommand();
        command.init(context);

        for (int i = 0; i < expectedData.size(); i++) {
            System.out.println();
            System.out.println();
            System.out.println(StringUtils.repeat("*", 120));
            System.out.println("TEST CASE: " + testCaseId + " #" + (i + 1) + "\n");

            StepResult result = command.saveDiff(diffVar, expectedData.get(i), actualData.get(i));
            Assert.assertNotNull(result);

            String diffs = context.getStringData(diffVar);
            System.out.println(diffs);

            List<String> expectedErrors = messages.get(i);
            for (String error : expectedErrors) {
                System.out.print("checking that logs contains '" + error + "'... ");
                Assert.assertTrue(StringUtils.contains(diffs, error));
                System.out.println("PASSED!");
            }
        }
    }

    @Test
    public void testBase64() throws IOException {
        String resourcePath = StringUtils.replace(ImageCommandTest.class.getPackage().getName(), ".", "/");
        String imageFile1 = ResourceUtils.getResourceFilePath(resourcePath + "/overall.png");

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.base64("myImageBase64", imageFile1);
        System.out.println("result = " + result);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        String base64 = context.getStringData("myImageBase64");
        System.out.println("base64 = " + base64);
        Assert.assertNotNull(base64);

        byte[] decoded = Base64.getDecoder().decode(base64);
        FileUtils.writeByteArrayToFile(new File(dummyPng), decoded);

        StepResult compareResult = io.compare(imageFile1, dummyPng, "true");
        System.out.println("compareResult = " + compareResult);
        Assert.assertNotNull(compareResult);
        Assert.assertTrue(compareResult.isSuccess());
    }

    @Test
    public void testChecksum() throws Throwable {
        String basePath = getClass().getPackage().getName().replace(".", "/");
        assertEquals("c1d20cf59843cd209517f1b31186b1e73dcefdb96c7c20d09f6b49f2ef0ca91f",
                     IoCommand.checksum(ResourceUtils.getResourceFilePath(basePath + "/checksum-target")));
        assertEquals("3e20709d538de2b4028b55884192617a1b0add3fda3540e93828f89e252e0bad",
                     IoCommand.checksum(ResourceUtils.getResourceFilePath(basePath + "/checksum-target2")));
    }

    @Before
    public void setUp() throws IOException {
        System.setProperty(OPT_OUT_DIR, tmpOutdir);
        context.setData(LOG_MATCH, true);

        FileUtils.deleteQuietly(new File(testFile1));

        FileUtils.deleteQuietly(new File(testDestination1));
        FileUtils.forceMkdir(new File(testDestination1));

        FileUtils.deleteQuietly(new File(testDestination2));
        FileUtils.forceMkdir(new File(testDestination2));

        FileUtils.deleteQuietly(new File(tmpOutdir));
    }

    @After
    public void tearDown() {
        FileUtils.deleteQuietly(new File(testFile1));
        FileUtils.deleteQuietly(new File(testFile2));

        FileUtils.deleteQuietly(new File(testFile1 + ".testReadFile"));
        FileUtils.deleteQuietly(new File(testFile1 + ".testRenameFiles"));
        FileUtils.deleteQuietly(new File(testFile1 + ".txt"));
        FileUtils.deleteQuietly(new File(testFile2 + ".txt"));

        FileUtils.deleteQuietly(new File(testDestination1));
        FileUtils.deleteQuietly(new File(testDestination2));

        FileUtils.deleteQuietly(new File(tmpOutdir));
        FileUtils.deleteQuietly(new File(dummyPng));

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void testMatch() throws Throwable {
        makeDummyContent(testDestination2 + separator + "FileA.txt");
        makeDummyContent(testDestination2 + separator + "FileB.txt");
        makeDummyContent(testDestination2 + separator + "A.txt");
        makeDummyContent(testDestination2 + separator + "B.txt");
        makeDummyContent(testDestination2 + separator + "A1.txt");
        makeDummyContent(testDestination2 + separator + "B2.txt");

        context.setData(OPT_IO_MATCH_RECURSIVE, false);

        IoCommand io = new IoCommand();
        io.init(context);

        {
            String filenameRegex = "[A-Z]\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, true);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(2, matches.size());
            Assert.assertEquals("A.txt", matches.get(0).getName());
            Assert.assertEquals("B.txt", matches.get(1).getName());
        }

        {
            String filenameRegex = "[A-Z]\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, false);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(4, matches.size());
            Assert.assertEquals("A.txt", matches.get(0).getName());
            Assert.assertEquals("B.txt", matches.get(1).getName());
            Assert.assertEquals("FileA.txt", matches.get(2).getName());
            Assert.assertEquals("FileB.txt", matches.get(3).getName());
        }

        {
            String filenameRegex = "[\\w][\\d]\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, false);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(2, matches.size());
            Assert.assertEquals("A1.txt", matches.get(0).getName());
            Assert.assertEquals("B2.txt", matches.get(1).getName());
        }

        {
            String filenameRegex = "[\\w][\\d]?\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, true);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(4, matches.size());
            Assert.assertEquals("A.txt", matches.get(0).getName());
            Assert.assertEquals("A1.txt", matches.get(1).getName());
            Assert.assertEquals("B.txt", matches.get(2).getName());
            Assert.assertEquals("B2.txt", matches.get(3).getName());
        }

        {
            String filenameRegex = "[\\w][\\d]?\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, false);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(6, matches.size());
            Assert.assertEquals("A.txt", matches.get(0).getName());
            Assert.assertEquals("A1.txt", matches.get(1).getName());
            Assert.assertEquals("B.txt", matches.get(2).getName());
            Assert.assertEquals("B2.txt", matches.get(3).getName());
            Assert.assertEquals("FileA.txt", matches.get(4).getName());
            Assert.assertEquals("FileB.txt", matches.get(5).getName());
        }

    }

    @Test
    public void testMatchEmptyFile() throws Throwable {
        makeEmptyFile(testDestination2 + separator + "Data1.txt");
        makeEmptyFile(testDestination2 + separator + "Data2.txt");
        makeEmptyFile(testDestination2 + separator + "1.txt");
        makeEmptyFile(testDestination2 + separator + "2.txt");
        makeEmptyFile(testDestination2 + separator + "1a.txt");
        makeEmptyFile(testDestination2 + separator + "2b.txt");

        context.setData(OPT_IO_MATCH_RECURSIVE, false);

        IoCommand io = new IoCommand();
        io.init(context);

        {
            String filenameRegex = "[0-9]\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, true);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(2, matches.size());
            Assert.assertEquals("1.txt", matches.get(0).getName());
            Assert.assertEquals("2.txt", matches.get(1).getName());
        }

        {
            String filenameRegex = "[0-9]\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, false);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(4, matches.size());
            Assert.assertEquals("1.txt", matches.get(0).getName());
            Assert.assertEquals("2.txt", matches.get(1).getName());
            Assert.assertEquals("Data1.txt", matches.get(2).getName());
            Assert.assertEquals("Data2.txt", matches.get(3).getName());
        }

        {
            String filenameRegex = "[\\d][a-z]\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, false);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(2, matches.size());
            Assert.assertEquals("1a.txt", matches.get(0).getName());
            Assert.assertEquals("2b.txt", matches.get(1).getName());
        }

        {
            String filenameRegex = "[\\d][a-z]?\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, true);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(4, matches.size());
            Assert.assertEquals("1.txt", matches.get(0).getName());
            Assert.assertEquals("1a.txt", matches.get(1).getName());
            Assert.assertEquals("2.txt", matches.get(2).getName());
            Assert.assertEquals("2b.txt", matches.get(3).getName());
        }

        {
            String filenameRegex = "[\\d][a-z]?\\.txt";
            context.setData(OPT_IO_MATCH_EXACT, false);
            List<File> matches = io.listMatches(testDestination2, filenameRegex);
            Assert.assertNotNull(matches);
            Assert.assertEquals(6, matches.size());
            Assert.assertEquals("1.txt", matches.get(0).getName());
            Assert.assertEquals("1a.txt", matches.get(1).getName());
            Assert.assertEquals("2.txt", matches.get(2).getName());
            Assert.assertEquals("2b.txt", matches.get(3).getName());
            Assert.assertEquals("Data1.txt", matches.get(4).getName());
            Assert.assertEquals("Data2.txt", matches.get(5).getName());
        }

    }

    private void zip_create_test_fixture(String basePath) throws IOException {
        int numberOfFiles = 10;
        for (int i = 0; i < numberOfFiles; i++) {
            File file = new File(basePath + i + ".txt");
            FileUtils.writeStringToFile(file, RandomStringUtils.randomAlphabetic(100), "UTF-8");
        }
    }

    private void zip_check_result(StepResult result) {
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());
    }

    private void zip_check_zip_file(String zipFilePath) {
        File zipFile = new File(zipFilePath);
        Assert.assertTrue(zipFile.isFile());
        Assert.assertTrue(zipFile.canRead());
        Assert.assertTrue(zipFile.length() > 1);

        // byte[] zipContent = FileUtils.readFileToByteArray(zipFile);
        // for (byte aZipContent : zipContent) { System.out.print(aZipContent); }
    }

    private void zip_check_unzipped_files(String dir, int expectedCount) {
        Collection<File> unzipFiles = FileUtils.listFiles(new File(dir), new String[]{"txt"}, false);
        assertEquals(expectedCount, unzipFiles.size());
        unzipFiles.forEach(file -> {
            System.out.println(file);
            assertEquals(100, file.length());
            Assert.assertTrue(NumberUtils.isDigits(file.getName().substring(0, 1)));
            Assert.assertTrue(StringUtils.endsWith(file.getName(), ".txt"));
        });
    }

    private void zip_cleanup(String basePath) {
        // tear down
        try {
            FileUtils.deleteDirectory(new File(basePath));
        } catch (IOException e) {
            System.err.println("Can't delete directory " + basePath);
            e.printStackTrace();
        }
    }
}