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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

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

import static java.io.File.separator;
import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;

public class IoCommandTest {

    private String tmpOutdir = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator) +
                               IoCommandTest.class.getSimpleName();
    private String baseLocation = StringUtils.appendIfMissing(FileUtils.getTempDirectoryPath(), separator);
    private String testFile1 = baseLocation + "dummy1";
    private String testFile2 = baseLocation + "dummy2";
    private String testDestination1 = baseLocation + "newloc";
    private String basePath = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator) +
                              this.getClass().getSimpleName() + separator;
    private String dummyPng = StringUtils.appendIfMissing(JAVA_IO_TMPDIR, separator) + "dummy.png";

    private ExecutionContext context = new MockExecutionContext() {
        @Override
        public String replaceTokens(String text) {
            builtinFunctions = new HashedMap<>();
            builtinFunctions.put("sysdate", new Sysdate());
            builtinFunctions.put("syspath", new Syspath());
            return super.replaceTokens(text);
        }
    };

    @Before
    public void setUp() throws IOException {
        System.setProperty(OPT_OUT_DIR, tmpOutdir);
        context.setData(LOG_MATCH, true);
        FileUtils.deleteQuietly(new File(testFile1));
        FileUtils.deleteQuietly(new File(testDestination1));
        FileUtils.forceMkdir(new File(testDestination1));
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
        FileUtils.deleteQuietly(new File(tmpOutdir));
        FileUtils.deleteQuietly(new File(dummyPng));
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void copy_file_to_dir() throws Exception {
        File sourceFile = makeDummyContent(testFile1);

        // make sure the file is really written to disk
        int expectedFileSize = 100 * 10;
        Assert.assertTrue(sourceFile.length() >= expectedFileSize);

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.copyFiles(testFile1, testDestination1);

        File destinationFile = new File(testDestination1 + separator + "dummy1");

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(destinationFile.length() >= expectedFileSize);
        Assert.assertEquals(FileUtils.readFileToString(sourceFile, DEF_CHARSET),
                            FileUtils.readFileToString(destinationFile, DEF_CHARSET));
    }

    @Test
    public void copy_jar_resource_to_dir() {
        // .../lib/selenium-java-2.39.0.jar!/org/openqa/selenium/firefox/webdriver.xpi

        IoCommand io = new IoCommand();
        io.init(context);

        StepResult result = io.copyFiles("jar:/org/openqa/selenium/firefox/webdriver.xpi", testDestination1);

        File destinationFile = new File(testDestination1 + separator + "webdriver.xpi");
        long expectedFileSize = (long) (685 * 1024);

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
        Assert.assertEquals(3, destinationFiles.size());
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
        String destinationDir = testDestination1 + separator + RandomStringUtils.randomAlphanumeric(5);
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
        Assert.assertEquals(3, destinationFiles.size());
        List<String> destinationPaths = new ArrayList<>();

        destinationFiles.forEach(f -> destinationPaths.add(f.getAbsolutePath()));
        Assert.assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_myLogs.txt"));
        Assert.assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_hello.log"));
        Assert.assertTrue(destinationPaths.contains(destinationDir + separator + "_E3_test1_step4_190FFV.csv"));
        Assert.assertTrue(!file1.exists());
        Assert.assertTrue(!file2.exists());
        Assert.assertTrue(!file3.exists());

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
        Assert.assertEquals(1, destinationFiles.size());
        List<String> destinationPaths = new ArrayList<>();

        destinationFiles.forEach(f -> destinationPaths.add(f.getAbsolutePath()));
        Assert.assertTrue(destinationPaths.contains(destinationDir + separator + "myFile.text"));
        Assert.assertTrue(!file1.exists());
        Assert.assertTrue(!file2.exists());
        Assert.assertTrue(!file3.exists());

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
        Assert.assertEquals(expectedFileLength, targetFile.length());

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
        Assert.assertEquals(CollectionUtils.size(files), 2);
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
        Assert.assertEquals(expectedLength, fileContent.length());
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

        Assert.assertEquals(myTestFile, filemeta.getFullpath());
        Assert.assertEquals(42, filemeta.getSize());
        Assert.assertEquals(enteredText, filemeta.getText());

        Assert.assertEquals(filemeta.isReadable(), file.canRead());
        Assert.assertEquals(filemeta.isWritable(), file.canWrite());
        Assert.assertEquals(filemeta.isExecutable(), file.canExecute());
        Assert.assertEquals(filemeta.getLastmod(), file.lastModified());

        //Update same file and validate the changes
        String updateText = "623132658,20130318,ANDERSON/UPDATED,5271.01";
        io.writeFile(myTestFile, updateText, "false");
        result = io.saveFileMeta("myData", myTestFile);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());

        File updatedFile = new File(myTestFile);
        FileMeta updatedMeta = new FileMeta(updatedFile);
        Assert.assertNotEquals(filemeta.getText(), updatedMeta.getText());

        Assert.assertEquals(myTestFile, updatedMeta.getFullpath());
        Assert.assertEquals(43, filemeta.getSize());
        Assert.assertEquals(updateText, updatedMeta.getText());

        Assert.assertEquals(updatedMeta.isReadable(), updatedFile.canRead());
        Assert.assertEquals(updatedMeta.isWritable(), updatedFile.canWrite());
        Assert.assertEquals(updatedMeta.isExecutable(), updatedFile.canExecute());
        Assert.assertEquals(updatedMeta.getLastmod(), updatedFile.lastModified());

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
        Assert.assertEquals("a", context.getStringData("prop1"));

        result = io.readProperty("prop2", testFile1, "prop2");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("b", context.getStringData("prop2"));

        result = io.readProperty("prop3", testFile1, "prop3");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("a-b", context.getStringData("prop3"));
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
        Assert.assertEquals("a", prop.getProperty("prop1"));
        Assert.assertEquals("b", prop.getProperty("prop2"));
        Assert.assertEquals("-", prop.getProperty("prop3"));
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
        Assert.assertEquals("prop1=a" + eol +
                            "prop2=b" + eol +
                            "prop3=-" + eol,
                            content);

        // eol as is test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_AS_IS);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\n";
        Assert.assertEquals("prop1=a" + eol +
                            "prop2=b" + eol +
                            "prop3=-" + eol,
                            content);

        // eol for windows test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_WINDOWS);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\r\n";
        Assert.assertEquals("prop1=a" + eol +
                            "prop2=b" + eol +
                            "prop3=-" + eol,
                            content);

        // eol for linux test
        context.setData(OPT_IO_EOL_CONFIG, EOL_CONFIG_UNIX);
        result = io.writeFile(testFile1, propContent, "false");
        Assert.assertTrue(result.isSuccess());
        content = FileUtils.readFileToString(file1, DEF_FILE_ENCODING);
        eol = "\n";
        Assert.assertEquals("prop1=a" + eol +
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
        Assert.assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
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
        Assert.assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
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
        Assert.assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
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
        Assert.assertEquals("Now is the time for all good men to come to the aid of his country" + eol +
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
        Assert.assertEquals("a", prop.getProperty("prop1"));
        Assert.assertEquals("b", prop.getProperty("prop2"));
        Assert.assertEquals("${prop1}-${prop2}", prop.getProperty("prop3"));
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
        Assert.assertEquals("a", prop.getProperty("prop1"));
        Assert.assertEquals("b", prop.getProperty("prop2"));
        Assert.assertEquals("${prop1}-${prop2}", prop.getProperty("prop3"));
        Assert.assertEquals("c", prop.getProperty("prop4"));
        Assert.assertEquals("${prop1}-${prop2}-${prop3}-${prop4}", prop.getProperty("prop5"));
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
        Assert.assertTrue(!result.isSuccess());

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
        Assert.assertTrue(!result.isSuccess());

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
        Assert.assertTrue(!result.isSuccess());

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
        Assert.assertEquals("4", context.getStringData("fileCount"));

        makeDummyContent(testDirectory + "testAddFiles1.txt");
        makeDummyContent(testDirectory + "testAddFiles2.txt");
        result = io.count("fileCount", testDirectory, filePattern1);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("6", context.getStringData("fileCount"));

        File dir = new File(testDirectory + "testCountFiles1.txt");
        dir.delete();
        makeDummyContent(testDirectory + "testCountFiles3.csv");
        makeDummyContent(testDirectory + "testCountFiles4.csv");

        result = io.count("fileCount", testDirectory, filePattern2);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("2", context.getStringData("fileCount"));

        result = io.count("fileCount", testDirectory, filePattern1);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("5", context.getStringData("fileCount"));
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
    public void testWriteBinaryFile() throws IOException {
        StringBuilder encoded = new StringBuilder("0M8R4KGxGuEAAAAAAAAAAAAAAAAAAAAAPgADAP7/CQAGAAAAAAAAAAAAAAAB");
        encoded.append("AAAAAQAAAAAAAAAAEAAAAgAAAAEAAAD+////AAAAAAAAAAD/////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("///////9/////v////7///8EAAAABQAAAAYAAAAHAAAACAAAAAkAAAAKAAAAFAAAAAwAAAANAAAADgAAAA8AAAAQ");
        encoded.append("AAAAEQAAABIAAAATAAAAAwAAABUAAAAWAAAAFwAAABgAAAAZAAAAGgAAABsAAAAcAAAAHQAAAB4AAAAfAAAAIAAA");
        encoded.append("ACEAAAAiAAAAIwAAACQAAAAlAAAAJgAAACcAAAAoAAAAKQAAACoAAAArAAAALAAAAC0AAAAuAAAALwAAADAAAAAx");
        encoded.append("AAAAMgAAADMAAAA0AAAANQAAADYAAAA3AAAAOAAAADkAAAA6AAAAOwAAADwAAAA9AAAAPgAAAD8AAABAAAAAQQAA");
        encoded.append("AEIAAABDAAAARAAAAEUAAABGAAAARwAAAEgAAABJAAAASgAAAEsAAABMAAAATQAAAE4AAABPAAAAUAAAAFEAAABS");
        encoded.append("AAAAUwAAAFQAAABVAAAAVgAAAFcAAABYAAAAWQAAAFoAAABbAAAAXAAAAF0AAABeAAAAXwAAAGAAAABhAAAAYgAA");
        encoded.append("AGMAAABkAAAAZQAAAGYAAABnAAAAaAAAAGkAAABqAAAAawAAAGwAAABtAAAAbgAAAG8AAABwAAAAcQAAAHIAAABz");
        encoded.append("AAAAdAAAAP7////+/////////////////////////////////////////////////////////1IAbwBvAHQAIABF");
        encoded.append("AG4AdAByAHkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWAAUA//////////8B");
        encoded.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJC64kOtCtUBdQAAAAABAAAAAAAAVwBvAHIAawBiAG8AbwBr");
        encoded.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABIAAgH/////AgAAAP////8A");
        encoded.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAALAAAAq+MAAAAAAAAFAFMAdQBtAG0AYQByAHkASQBu");
        encoded.append("AGYAbwByAG0AYQB0AGkAbwBuAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKAACAP///////////////wAAAAAA");
        encoded.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD0AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        encoded.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA////////////////AAAAAAAAAAAA");
        encoded.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAIAAAADAAAA/v//////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("////////////////////////////////////////////////////////////////////////////////////////");
        encoded.append("//////////////////////////////8tAE8AVgBFAFIASABFAEEARAAGAAFRAEIAMAA1ADYANgAOAAFLAEEAWABY");
        encoded.append("AFgAIAAvACAAVABBAFkARgBVAE4ACQABNgAyADMANAA5ADgAOQA0ADkACwABKgAqACoALQAqACoALQA4ADkANAA5");
        encoded.append("AAMAAVIARQBHAAUAAVcAaABpAHQAZQAIAAFQAFIATwBEAFUAQwBFAFIACgABMAAxAC8AMAA4AC8AMgAwADEAMwAK");
        encoded.append("AAEwADMALwAyADYALwAyADAAMQAyAAoAATAAMwAvADAAMQAvADEAOQA3ADQABgABUQBCADIANgA4ADQABgABUQBC");
        encoded.append("ADQAMwA2ADIACgABMAAxAC8AMAAzAC8AMgAwADEAMwAGAAFRAEIAMQA1ADQAMgAKAAEwADEALwAyADIALwAyADAA");
        encoded.append("MQAzAAYAAVEAQwA1ADgANQA5AAYAAVEAQgA3ADgANwA2AAsAAUoARQBGAEYAIABEAEUAQwBLAEUAUgAMAAFUAEgA");
        encoded.append("WABYAFgAIAAvACAAQgBSAEEARAAJAAE1ADYANAA3ADUAMQA3ADUAMAALAAEqACoAKgAtACoAKgAtADEANwA1ADAA");
        encoded.append("AQABWgAEAAFOAFUAUABBAAoAAUQARQBWACAAQwBPAE8AUgBEAC4ABwABOQA0ADAAMAAwADUANgAKAAEwADEALwAx");
        encoded.append("ADkALwAyADAAMQAxAAoAATAAOAAvADEANQAvADEAOQA3ADgABgABUQBDADUANwA4ADMADAABRgBPAFgAWABYACAA");
        encoded.append("LwAgAEoATwBIAE4ACQABMAA2ADUAOAAwADcANAAxADcACwABKgAqACoALQAqACoALQA3ADQAMQA3AAQAAUEAUwBT");
        encoded.append("AFQABwABOQA0ADAAMAAwADAAMQAKAAEwADcALwAwADkALwAyADAAMQAyAAoAATAAOQAvADEANgAvADEAOQA4ADgA");
        encoded.append("BgABUQBDADUAOAA1ADQABgABUQBDADEAMAA1ADEABgABUQBCADQAMwA1ADcADQABTQBBAFgAWABYACAALwAgAE0A");
        encoded.append("RQBHAEEATgAGAAFGAEUATQBBAEwARQAJAAE2ADEANAAzADAAOAA3ADAANQALAAEqACoAKgAtACoAKgAtADgANwAw");
        encoded.append("ADUAAgABUABBAAoAATEAMgAvADIAMAAvADIAMAAxADIACgABMQAyAC8AMAA3AC8AMQA5ADgANwAKAAExADIALwAy");
        encoded.append("ADEALwAyADAAMQAyAAYAAVEAQgAxADUAOQAyAA0AAUMAQQBYAFgAWAAgAC8AIABHAFIAQQBDAEUACQABNgAyADMA");
        encoded.append("NgA0ADYAMgA1ADYACwABKgAqACoALQAqACoALQA2ADIANQA2AAoAATAANgAvADAANwAvADEAOQA5ADEACgABMQAy");
        encoded.append("AC8AMQA3AC8AMgAwADEAMgAGAAFRAEIAMAAxADYANQARAAFGAEkAWABYAFgAIAAvACAARQBMAEkAWgBBAEIARQBU");
        encoded.append("AEgACQABNQA3ADQANwA2ADQAMgAyADAACwABKgAqACoALQAqACoALQA0ADIAMgAwAAoAATEAMgAvADMAMQAvADEA");
        encoded.append("OQA4ADQAGwABRABFAFYARQBMAE8AUABNAEUATgBUACAAUABSAE8ASgBFAEMAVABTACAALQAgADIAMAAxADIADwAB");
        encoded.append("TQBBAFgAWABYACAALwAgAE0ARQBMAEEATgBJAEUACQABMQA5ADEANAAyADcAMAA5ADUACwABKgAqACoALQAqACoA");
        encoded.append("LQA3ADAAOQA1AAQAAUUARABJAFQABgABRQBEAEkAVABPAFIABwABNAAxADIAMQAwADUANgACAAFNAEQACgABMAA0");
        encoded.append("AC8AMgA3AC8AMQA5ADUAMAAGAAFRAEMANQA2ADMANQADAAFHAEIAVwACAAFOAFkADQABTABFAFgAWABYACAALwAg");
        encoded.append("AFMAQQBSAEEASAAJAAExADYAMAA2ADYANgA0ADIANAALAAEqACoAKgAtACoAKgAtADYANAAyADQAFAABRABFAFYA");
        encoded.append("RQBMAE8AUABNAEUATgBUACAAQQBTAEkAUwBUAEEATgBUAAcAATkANAAwADAAMAA0ADAAAgABRABDAAoAATAAOQAv");
        encoded.append("ADEAMAAvADIAMAAxADIACgABMQAyAC8AMQA3AC8AMQA5ADgANgAGAAFRAEMANQA2ADIAOAANAAFMAFUAWABYAFgA");
        encoded.append("IAAvACAASgBVAEwASQBPAAkAATUANwA3ADgANAAxADAAMgA5AAsAASoAKgAqAC0AKgAqAC0AMQAwADIAOQAEAAFD");
        encoded.append("AEEATQBSABEAAUQASQBSAEUAQwBUAE8AUgAgAE8ARgAgAFAASABPAFQATwAHAAExADkAMAAxADAANAAwAAYAAU4A");
        encoded.append("WQBOAFkATgBZAAoAATEAMgAvADIAMQAvADEAOQA3ADAABgABUQBCADcANgAwADIABgABUQBCADcANQA5ADQABgAB");
        encoded.append("UQBCADAAMwA2ADkABgABUQBCADIAMQA5ADUAEQABSABFAFgAWABYACAALwAgAEsARQBMAEwASQBFACAATQAuAAkA");
        encoded.append("ATYAMQAyADUAMgAwADAANQA2AAsAASoAKgAqAC0AKgAqAC0AMAAwADUANgARAAFTAEUATgBJAE8AUgAgAFMAVABB");
        encoded.append("AEYARgAgAEEAUwBTAFQACgABMAAxAC8AMQA3AC8AMgAwADEAMgAKAAEwADMALwAwADcALwAxADkAOQAwAAYAAVEA");
        encoded.append("QgAxADIANgA0AA0AAUYAUgBYAFgAWAAgAC8AIABMAFUAQwBBAFMACQABMAAyADQANgA4ADUANQA1ADYACwABKgAq");
        encoded.append("ACoALQAqACoALQA1ADUANQA2ABQAAUEAUwBTAFQAIABUAE8AIABTAEUATgBJAE8AUgAgAFMAVABBAEYARgAKAAEw");
        encoded.append("ADQALwAyADUALwAyADAAMQAyAAoAATAANAAvADIANwAvADEAOQA4ADgADAABQgBVAFgAWABYACAALwAgAFQATwBO");
        encoded.append("AEkACQABMQAwADMANwAyADgAMAA3ADEACwABKgAqACoALQAqACoALQA4ADAANwAxABMAAUIAbABhAGMAawAvAEEA");
        encoded.append("ZgByAGkAYwBhAG4AIABBAG0AZQByAC4ACQABQQBTAFMASQBTAFQAQQBOAFQACgABMAA1AC8AMAAyAC8AMgAwADEA");
        encoded.append("MgAKAAEwADMALwAyADAALwAxADkAOAA2ABEAAVQAUwBYAFgAWAAgAC8AIABDAEgAUgBJAFMAVABJAE4ARQAJAAEyADcAOAA4ADAANgA2ADMANwALAAEqACoAKgAtACoAKgAtADYANgAzADcABQABQQBzAGkAYQBuAAIAAU0AUwACAAFUAFgACgABMAA2AC8AMQAwAC8AMgAwADEAMwAKAAEwADYALwAwADgALwAxADkAOAAyAAoAATEAMgAvADIAMgAvADIAMAAxADIABgABUQBCADEANwA0ADEACgABMQAyAC8AMgA5AC8AMgAwADEAMgAVAAFXAEkAWABYAFgAIAAvACAATQBJAEMASABBAEUATAAgAEEATABMAEUATgAJAAE1ADYAOAAxADUANgAzADAAMAALAAEqACoAKgAt");
        encoded.append("ACoAKgAtADYAMwAwADAABAABQQBDAEMAVAAVAAFQAFIATwBEAFUAQwBUAEkATwBOACAAQQBDAEMATwBVAE4AVABBAE4AVAAHAAE3ADEAMAAwADAANQA2AAoAATAANQAvADIAMAAvADEAOQA3ADAABgABUQBCADIANwA5ADQABgABUQBCADAAMQA5ADIADgABUwBBAFgAWABYACAALwAgAFIAQQBDAEgARQBMAAkAATAAMgAzADcAMgA3ADQAOQA5AAsAASoAKgAqAC0AKgAqAC0ANwA0ADkAOQAPAAFIAGkAcwBwAGEAbgBpAGMALwBMAGEAdABpAG4AbwALAAFFAFgARQBDAC4AIABBAFMAUwBUAC4ACgABMAA1AC8AMAA0AC8AMgAwADEAMgAKAAEwADYALwAxADgALwAxADkA");
        encoded.append("OAA4AAYAAVEAQgAyADYAOQA4AAYAAVEAQgAyADYAOAAzABAAAUMASABYAFgAWAAgAC8AIABSAEEAQwBIAEUATAAgAFIACQABNgAyADEAMwAzADkAOAAyADMACwABKgAqACoALQAqACoALQA5ADgAMgAzAAIAAUEARQAHAAE0ADEANwAxADAAMAAxAAoAATAAOAAvADAANgAvADIAMAAxADIACgABMAA5AC8AMAAzAC8AMQA5ADgANQAGAAFRAEIANwA4ADcAMwAGAAFRAEIANwA4ADYANwAMAAFNAEMAWABYAFgAIAAvACAATQBBAFQAVAAJAAE2ADAAMQA1ADQANAAxADEANAALAAEqACoAKgAtACoAKgAtADQAMQAxADQAEAABKgBBAEwATAAgAFMASABJAFAAUwAsACAASQBO");
        encoded.append("AEMALgAJAAEyADcAMwAxADEAMwA4ADIANwALAAEqACoAKgAtACoAKgAtADMAOAAyADcABwABNAAxADIAMQAwADAAMQAKAAEwADUALwAyADEALwAyADAAMQAyAAYAAVEAQgA0ADEANAA5AA8AAUIAQQBYAFgAWAAgAC8AIABUAEkATQBPAFQASABZAAkAATMANQAzADcAOAAzADgANwAxAAsAASoAKgAqAC0AKgAqAC0AMwA4ADcAMQAHAAExADkAMAAxADAANQA2AAoAATEAMQAvADIAOAAvADIAMAAxADIACgABMAAzAC8AMAA0AC8AMQA5ADgANAAKAAExADIALwAwADEALwAyADAAMQAyAAYAAVEAQgAyADcAMQAwAAYAAVEAQwA1ADgAMgAyAAwAAUcATwBYAFgAWAAgAC8A");
        encoded.append("IABQAEEAVQBMAAkAATYAMAA2ADMAMAA0ADUANQAzAAsAASoAKgAqAC0AKgAqAC0ANAA1ADUAMwAEAAFTAE8AVQBOAAsAAVMATwBVAE4ARAAgAE0ASQBYAEUAUgAHAAE4ADEANwAxADAAMAAxAAoAATEAMgAvADMAMQAvADEAOQA4ADYABgABUQBCADcAOAA3ADkACwABVABSAEEATgBTAFAATABBAE4AVABTAP8AMgEIAMcGAAAMAAAAYQcAAKYAAAALCAAAUAEAAO8IAAA0AgAAnwkAAOQCAABTCgAAmAMAABULAABaBAAA8QsAADYFAADTDAAAGAYAADcNAAB8BgAA2Q0AAB4HAACpDgAA7gcAAIcPAADMCAAAaxAAALAJAAADEQAASAoAAIcRAADMCgAAVRIAAJoLAADrEgAA");
        encoded.append("MAwAAIkTAADODAAAHRQAAGINAAC/FAAABA4AAE0VAACSDgAAAxYAAEgPAADtFgAAMhAAAHMXAAC4EAAAERgAAFYRAADJGAAADhIAAGEZAACmEgAAIxoAAGgTAAAHGwAATBQAALEbAAD2FAAAZRwAAKoVAAAbHQAAYBYAAM8dAAAUFwAAdR4AALoXAAAvHwAAdBgAAOMfAAAoCQgQAAAGBQC7Dc0HAAAAAAAAAADAAQAAYQECAAAAPQECAAEAjQACAAAAtwECAAAAXABwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA9");
        encoded.append("ABIAAAAAAEA4wCE4/wAAAAAAAPQBMQAaAMgAAAAIAJABAAAAAAEABQFBAFIASQBBAEwAMQAaAMgAAAAIAJABAAAAAAEABQFBAFIASQBBAEwAMQAaAMgAAAAIAJABAAAAAAEABQFBAFIASQBBAEwAMQAaAMgAAAAIAJABAAAAAAEABQFBAFIASQBBAEwAMQAaAMgAAAAIAJABAAAAAAEABQFBAFIASQBBAEwAMQAaAKAAAAAIALwCAAAAEAEABQFBAHIAaQBhAGwAMQAaAKAAAAAIAJABAAAAEAEABQFBAFIASQBBAEwAHgQHAKQAAQABMAAeBA8ApQAFAAEjACwAIwAjADAAHgQnAKYAEQABWwAkACQALQAwADQAMAA5AF0AIwAsACMAIwAwAC4AMAAwAOAAFAAAAAAA9f8AAAAA");
        encoded.append("AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAA9f8AAAD0AAAIBAgE");
        encoded.append("AACZIOAAFAAAAAAA9f8AAAD0AAAIBAgEAACZIOAAFAAAAAAAAQAAAAAAAAAIBAgEAACZIOAAFAAAACsA9f8AAAD4AAAIBAgEAACZIOAAFAAAACkA9f8AAAD4AAAIBAgEAACZIOAAFAAAACwA9f8AAAD4AAAIBAgEAACZIOAAFAAAACoA9f8AAAD4AAAIBAgEAACZIOAAFAAAAAkA9f8AAAD4AAAIBAgEAACZIOAAFAAGAAAAAQAKAEAYAAAIBAgEAACZIOAAFAAGAAAAAQAJAEAYAAAIBAgEAACZIOAAFAAGAAAAAQALAEAYAAAIBAgEAACZIOAAFAAHAKQAAQACAAAcAAAIBAgEAACZIOAAFAAHAKUAAQACAAAcAAAIBAgEAACZIOAAFAAHAAAAAQABAAAYAAAIBAgEAACZIOAA");
        encoded.append("FAAHAAAAAQACAAAYAAAIBAgEAACZIOAAFAAHAKYAAQADAAAcAAAIBAgEAACZIOAAFAAAAAAAAQAAAABAAAAIBAgEAASWIOAAFAAHAKQAAQACAABcAAAIBAgEAASWIOAAFAAHAKUAAQACAABcAAAIBAgEAASWIOAAFAAHAAAAAQABAABYAAAIBAgEAASWIOAAFAAHAAAAAQACAABYAAAIBAgEAASWIOAAFAAHAKYAAQADAABcAAAIBAgEAASWIJMCBAACgAIAkwIEAASAAgGTAgQABoACApMCBAAIgAIDkwIEAAqAAgSTAgQADIACBZMCBAAOgAIGkwIEABCAA/+TAgQAEYAG/5MCBAASgAT/kwIEABOAB/+TAgQAAIAA/5MCBAAUgAX/kwIEAAGAAQCTAgQAA4ABAZMCBAAFgAEC");
        encoded.append("kwIEAAeAAQOTAgQACYABBJMCBAALgAEFkwIEAA2AAQaSAOIAOAAAAAAA////AP8AAAAA/wAAAAD/AP//AAD/AP8AAP//AIAAAAAAgAAAAACAAICAAACAAIAAAICAAMDAwACAgIAAmZn/AJkzZgD//8wAzP//AGYAZgD/gIAAAGbMAMzM/wAAAIAA/wD/AP//AAAA//8AgACAAIAAAAAAgIAAAAD/AADM/wDM//8AzP/MAP//mQCZzP8A/5nMAMyZ/wDj4+MAM8zMAJnMAAD/zAAA/5kAAP9mAABmZpkAlpaWAAAzZgAzmWYAADMAADMzAACZMwAAmTNmADMzmQAzMzMAmZmZAIUAFAAPIgAAAAAGAVMAaABlAGUAdAAxAPwAFhosAQAALAEAAAgAAVQAQQBYAF8AWQBFAEEAUgAJ");
        encoded.append("AAFQAFIAVABfAEMATwBfAEkARAAIAAFTAFUAQgBHAFIATwBVAFAABgABRgBJAEQAXwBJAEQACQABUgBFAEIAQQBUAEUAXwBJAEQACQABQwBMAEkARQBOAFQAXwBJAEQABwABQwBMACAAVABZAFAARQAJAAFQAFIATwBEAF8ATgBBAE0ARQAMAAFQAFIATwBEAF8AQwBPAF8ATgBBAE0ARQAJAAFQAFIATwBEAF8AVABZAFAARQAIAAFTAFUAQgBfAFQAWQBQAEUABgABRQBNAFAAXwBJAEQACAABRQBNAFAAXwBOAEEATQBFAAsAAUcARQBOAEQARQBSAF8AVABZAFAARQAHAAFFAE0AUABfAFMAUwBOAAwAAU0AUwBLAEQAXwBFAE0AUABfAFMAUwBOAAkAAUMATwBSAFAAXwBO");
        encoded.append("AEEATQBFAAsAAUMATwBSAFAAXwBUAEEAWABfAEkARAAQAAFNAFMASwBEAF8AQwBPAFIAUABfAFQAQQBYAF8ASQBEAAgAAUUATQBQAF8AVABZAFAARQALAAFFAFQASABOAEkAQwBfAFQAWQBQAEUADgABRgBSAEcATgBfAEEAVQBUAEgAXwBUAFkAUABFABIAAVQAQQBYAF8AVwBBAEkAVgBFAFIAXwBUAFkAUABFAF8ASQBEAA8AAVcASwBDAF8AQwBBAFQARQBHAE8AUgBZAF8ASQBEAAcAAUEARgBGAEwAXwBJAEQACAABTABPAEMAQQBMAF8ASQBEAAwAAUMATwBOAFQAXwBUAFkAUABFAF8ASQBEAAkAAUoATwBCAF8AVABJAFQATABFAAgAAVMAQwBfAEMATABBAFMAUwAJ");
        encoded.append("AAFSAEUAUwBfAFMAVABBAFQARQANAAFSAEUAUwBfAFQAWABfAFIARQBHAEkATwBOAAoAAVcATwBSAEsAXwBTAFQAQQBUAEUADAABVwBLAF8AVABYAF8AUgBFAEcASQBPAE4ACQABUwBVAEkAXwBTAFQAQQBUAEUADQABUwBVAEkAXwBUAFgAXwBSAEUARwBJAE8ATgAKAAFWAE8AVQBDAEgARQBSAF8ASQBEAAcAAUMASABLAF8ATgBVAE0ACAABQwBIAEsAXwBEAEEAVABFAAkAAUgASQBSAEUAXwBEAEEAVABFAAoAAUIASQBSAFQASABfAEQAQQBUAEUADgABTABBAFMAVABfAFcATwBSAEsAXwBEAEEAVABFAAwAAVAAUgBPAEMARQBTAFMAXwBEAEEAVABFAA4AAVcASwBf");
        encoded.append("AEUATgBEAEkATgBHAF8ARABBAFQARQAKAAFJAE4AVgBPAEkAQwBFAF8ASQBEAAsAAUkATgBWAE8ASQBDAEUAXwBOAFUATQAJAAFQAE0AXwBJAE4ASQBUAEwAUwAHAAFFAFAAXwBTAEkAVABFAAgAAUMATwBNAE0ARQBOAFQAUwAJAAFHAFIATwBTAFMAXwBBAE0AVAAHAAFOAEUAVABfAEEATQBUAAkAAVcATwBSAEsAXwBEAEEAWQBTAAgAAUQASQBTAEMATwBWAEUAUgACAAFQAFAAHQABRABJAFMAQwBPAFYARQBSAFkAIABTAFQAVQBEAEkATwBTAC0ARABFAFYARQBMAE8AUABNAEUATgBUABYAAUQASQBTAEMATwBWAEUAUgBZACAAUwBUAFUARABJAE8AUwAsACAATABM");
        encoded.append("AEMADAABQwBhAGIAbABlAC8AUABhAHkAIABUAFYACgABUAByAGUAIAAtACAAMQA5ADkANAALAAFIAEEAWABYAFgAIAAvACAAUgBPAEIABAABTQBBAEwARQAJAAEyADEANAA5ADQANQAyADYANAALAAEqACoAKgAtACoAKgAtADUAMgA2ADQAJAABKgBSAEkAQwBPAEMASABFAFQAIABSAE8AQQBEAFMASABPAFcAIABQAFIATwBEAFUAQwBUAEkATwBOAFMALAAgAEkATgBDAC4ACQABMgA3ADMANAAxADAAMAA3ADAACwABKgAqACoALQAqACoALQAwADAANwAwAAQAAUMATwBSAFAABQABTwB0AGgAZQByAAEAAU4AAQABWAAEAAFQAFIATwBEAA4AAUUAWABFAEMALgAgAFAA");
        encoded.append("UgBPAEQAVQBDAEUAUgAHAAE5ADMAMAAxADAANQA2AAIAAUMAQQAKAAEwADEALwAxADYALwAyADAAMQAzAAoAATAANwAvADAAMgAvADIAMAAxADIACgABMAAzAC8AMQAyAC8AMQA5ADYAOAAKAAEwADEALwAxADEALwAyADAAMQAzAAoAATAAMQAvADEAOQAvADIAMAAxADMABgABUQBDADEAMAA0ADYAAwABQQBNACoACgABMAAxAC8AMAAyAC8AMgAwADEAMwAKAAExADIALwAyADgALwAyADAAMQAyAAoAATAAMQAvADAANQAvADIAMAAxADMABgABUQBCADAAMQA3ADIADgABUwBPAFgAWABYACAALwAgAEQAQQBOAEkARQBMAAkAATQANwAwADcAMgAzADgAOAA0AAsAASoA");
        encoded.append("KgAqAC0AKgAqAC0AMwA4ADgANAAXAAEqAEQAUwBUAFYAIABQAFIATwBEAFUAQwBUAEkATwBOAFMALAAgAEkATgBDAC4ACQABMgAwADUANgA1ADYANgAwADIACwABKgAqACoALQAqACoALQA2ADYAMAAyAA0AAU4AbwB0ACAAUwBwAGUAYwBpAGYAaQBlAGQACgABMAA4AC8AMAAxAC8AMgAwADEAMQARAAFCAEEAWABYAFgAIAAvACAARQBEAFcAQQBSAEQAIABGAC4ACQABMAA5ADgANQAwADgAMQA3ADAACwABKgAqACoALQAqACoALQA4ADEANwAwABMAASoAQgBMAFUARQAgAEYAUgBPAEcAIABQAEkAQwBUAFUAUgBFAFMACQABOQA1ADQANwA4ADUANwA0ADQACwABKgAq");
        encoded.append("ACoALQAqACoALQA1ADcANAA0AAoAATAANwAvADIANQAvADIAMAAxADEADQABSgBFAFIAUwBFAFkAIABPAE4AIABJAEMARQAOAAFXAEkATABNAEEAIABUAFYALAAgAEkATgBDAC4ADQABQwBPAFgAWABYACAALwAgAEoAQQBTAE8ATgAJAAE2ADAAMgAwADUAMgA3ADQAMAALAAEqACoAKgAtACoAKgAtADIANwA0ADAAFQABKgBNAEEAVABDAEgAQgBPAFgAIABQAFIATwBEAFUAQwBUAEkATwBOAFMACQABNAA1ADAANQA3ADYANwAzADgACwABKgAqACoALQAqACoALQA2ADcAMwA4AAQAAUUAWABFAEMAAwABRQBJAEMABwABOQA5ADAAMAAwADUANgAKAAEwADEALwAyADMA");
        encoded.append("LwAyADAAMQAzAAoAATEAMgAvADMAMQAvADIAMAAxADIACgABMAA4AC8AMAAzAC8AMQA5ADcANgAKAAEwADEALwAxADgALwAyADAAMQAzAAoAATAAMQAvADIANgAvADIAMAAxADMABgABUQBDADYAOAAyADIAAwABQgBaAFMACgABMAAxAC8AMQA1AC8AMgAwADEAMwAGAAFRAEIANwA5ADIANwACAAFMAFAABwABOQAzADAAMAAwADUANgAKAAEwADEALwAwADkALwAyADAAMQAzAAoAATAAMQAvADAANAAvADIAMAAxADMACgABMAAxAC8AMQAyAC8AMgAwADEAMwAGAAFRAEIAMwA4ADQANAAaAAFEAEkAUwBDAE8AVgBFAFIAWQAgAFMAVABVAEQASQBPAFMAGQAAhSAAAMoZ");
        encoded.append("AAAKAAAACQgQAAAGEAC7Dc0HAAAAAAAAAAALAhgAAAAAAAAAAAA/AAAAAAAAACMiAAAjIgAAVQACAAcAJQIEAAEA/wBNAN4A3ABcAFwAYwBzAHAAcAByAHQAMAAyAFwAYwBwAGwAcwBmAHQAMAAyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQQABtwAPB5D/4AHAQAFAAEADwBYAgIAAQBYAgMAAAABAAAAAgAAAB0BAAD/////AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgQACAMAEoQAiAAUAZAABAAAAAAACAAAA");
        encoded.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAmAAgAVVVVVVVVxT8nAAgAVVVVVVVVxT8oAAgAVVVVVVVVxT8pAAgAVVVVVVVVxT8bAAIAAAB9AAwAAAAAANsIDwAAAAAAfQAMAAEAAQBtCQ8AAAAAAH0ADAACAAIAbQsPAAAAAAB9AAwAAwADACQHDwAAAAAAfQAMAAQABACSDQ8AAAAAAH0ADAAFAAUASQoPAAAAAAB9AAwABgAGAAAIDwAAAAAAfQAMAAcABwBtGw8AAAAAAH0ADAAIAAgAbRMPAAAAAAB9AAwACQAJAJIMDwAAAAAAfQAMAAoACgAAEQ8AAAAAAH0ADAALAAsAbQsPAAAAAAB9AAwADAAMAAASDwAAAAAAfQAMAA0ADQBtCw8AAAAAAH0ADAAOAA4AthIPAAAAAAB9AAwA");
        encoded.append("DwAPANsODwAAAAAAfQAMABAAEABtEg8AAAAAAH0ADAARABEAthIPAAAAAAB9AAwAEgASAAARDwAAAAAAfQAMABMAEwCSCQ8AAAAAAH0ADAAUABQA2woPAAAAAAB9AAwAFQAVAEkODwAAAAAAfQAMABYAFgAkEQ8AAAAAAH0ADAAXABcA2w8PAAAAAAB9AAwAGAAYAG0GDwAAAAAAfQAMABkAGQDbBw8AAAAAAH0ADAAaABoAAAwPAAAAAAB9AAwAGwAbAEkdDwAAAAAAfQAMABwAHAC2Cg8AAAAAAH0ADAAdAB0A2wkPAAAAAAB9AAwAHgAeAG0NDwAAAAAAfQAMAB8AHwAACw8AAAAAAH0ADAAgACAAAA0PAAAAAAB9AAwAIQAhAJIIDwAAAAAAfQAMACIAIgBtDA8AAAAAAH0A");
        encoded.append("DAAjACMAkgoPAAAAAAB9AAwAJAAkALYLDwAAAAAAfQAMACUAJQDbCQ8AAAAAAH0ADAAmACYAbQkPAAAAAAB9AAwAJwAnAG0KDwAAAAAAfQAMACgAKABJEQ8AAAAAAH0ADAApACkAbQ0PAAAAAAB9AAwAKgAqAG0ODwAAAAAAfQAMACsAKwC2CQ8AAAAAAH0ADAAsACwAkgwPAAAAAAB9AAwALQAtAG0KDwAAAAAAfQAMAC4ALgC2Bg8AAAAAAH0ADAAvAC8AthoPAAAAAAB9AAwAMAAwAAAMDwAAAAAAfQAMADEAMQCSCA8AAAAAAH0ADAAyADIAAAsPAAAAAAB9AAwAMwAzANsGDwAAAAAAfQAMADQANADbBg8AAAAAAH0ADAA1ADUA2wYPAAAAAAB9AAwANgA2ANsGDwAAAAAA");
        encoded.append("fQAMADcANwDbBg8AAAAAAH0ADAA4ADgA2wYPAAAAAAB9AAwAOQA5ANsGDwAAAAAAfQAMADoAOgDbBg8AAAAAAH0ADAA7ADsA2wYPAAAAAAB9AAwAPAA8ANsGDwAAAAAAfQAMAD0APQDbBg8AAAAAAH0ADAA+AD4A2wYPAAAAAAB9AAwAPwA/ANsGDwAAAAAAfQAMAEAAQADbBg8AAAAAAH0ADABBAEEA2wYPAAAAAAB9AAwAQgBCANsGDwAAAAAAfQAMAEMAQwDbBg8AAAAAAH0ADABEAEQA2wYPAAAAAAB9AAwARQBFANsGDwAAAAAAfQAMAEYARgDbBg8AAAAAAH0ADABHAEcA2wYPAAAAAAB9AAwASABIANsGDwAAAAAAfQAMAEkASQDbBg8AAAAAAH0ADABKAEoA2wYPAAAA");
        encoded.append("AAB9AAwASwBLANsGDwAAAAAAfQAMAEwATADbBg8AAAAAAH0ADABNAE0A2wYPAAAAAAB9AAwATgBOANsGDwAAAAAAfQAMAE8ATwDbBg8AAAAAAH0ADABQAFAA2wYPAAAAAAB9AAwAUQBRANsGDwAAAAAAfQAMAFIAUgDbBg8AAAAAAH0ADABTAFMA2wYPAAAAAAB9AAwAVABUANsGDwAAAAAAfQAMAFUAVQDbBg8AAAAAAH0ADABWAFYA2wYPAAAAAAB9AAwAVwBXANsGDwAAAAAAfQAMAFgAWADbBg8AAAAAAH0ADABZAFkA2wYPAAAAAAB9AAwAWgBaANsGDwAAAAAAfQAMAFsAWwDbBg8AAAAAAH0ADABcAFwA2wYPAAAAAAB9AAwAXQBdANsGDwAAAAAAfQAMAF4AXgDbBg8A");
        encoded.append("AAAAAH0ADABfAF8A2wYPAAAAAAB9AAwAYABgANsGDwAAAAAAfQAMAGEAYQDbBg8AAAAAAH0ADABiAGIA2wYPAAAAAAB9AAwAYwBjANsGDwAAAAAAfQAMAGQAZADbBg8AAAAAAH0ADABlAGUA2wYPAAAAAAB9AAwAZgBmANsGDwAAAAAAfQAMAGcAZwDbBg8AAAAAAH0ADABoAGgA2wYPAAAAAAB9AAwAaQBpANsGDwAAAAAAfQAMAGoAagDbBg8AAAAAAH0ADABrAGsA2wYPAAAAAAB9AAwAbABsANsGDwAAAAAAfQAMAG0AbQDbBg8AAAAAAH0ADABuAG4A2wYPAAAAAAB9AAwAbwBvANsGDwAAAAAAfQAMAHAAcADbBg8AAAAAAH0ADABxAHEA2wYPAAAAAAB9AAwAcgByANsG");
        encoded.append("DwAAAAAAfQAMAHMAcwDbBg8AAAAAAH0ADAB0AHQA2wYPAAAAAAB9AAwAdQB1ANsGDwAAAAAAfQAMAHYAdgDbBg8AAAAAAH0ADAB3AHcA2wYPAAAAAAB9AAwAeAB4ANsGDwAAAAAAfQAMAHkAeQDbBg8AAAAAAH0ADAB6AHoA2wYPAAAAAAB9AAwAewB7ANsGDwAAAAAAfQAMAHwAfADbBg8AAAAAAH0ADAB9AH0A2wYPAAAAAAB9AAwAfgB+ANsGDwAAAAAAfQAMAH8AfwDbBg8AAAAAAH0ADACAAIAA2wYPAAAAAAB9AAwAgQCBANsGDwAAAAAAfQAMAIIAggDbBg8AAAAAAH0ADACDAIMA2wYPAAAAAAB9AAwAhACEANsGDwAAAAAAfQAMAIUAhQDbBg8AAAAAAH0ADACGAIYA");
        encoded.append("2wYPAAAAAAB9AAwAhwCHANsGDwAAAAAAfQAMAIgAiADbBg8AAAAAAH0ADACJAIkA2wYPAAAAAAB9AAwAigCKANsGDwAAAAAAfQAMAIsAiwDbBg8AAAAAAH0ADACMAIwA2wYPAAAAAAB9AAwAjQCNANsGDwAAAAAAfQAMAI4AjgDbBg8AAAAAAH0ADACPAI8A2wYPAAAAAAB9AAwAkACQANsGDwAAAAAAfQAMAJEAkQDbBg8AAAAAAH0ADACSAJIA2wYPAAAAAAB9AAwAkwCTANsGDwAAAAAAfQAMAJQAlADbBg8AAAAAAH0ADACVAJUA2wYPAAAAAAB9AAwAlgCWANsGDwAAAAAAfQAMAJcAlwDbBg8AAAAAAH0ADACYAJgA2wYPAAAAAAB9AAwAmQCZANsGDwAAAAAAfQAMAJoA");
        encoded.append("mgDbBg8AAAAAAH0ADACbAJsA2wYPAAAAAAB9AAwAnACcANsGDwAAAAAAfQAMAJ0AnQDbBg8AAAAAAH0ADACeAJ4A2wYPAAAAAAB9AAwAnwCfANsGDwAAAAAAfQAMAKAAoADbBg8AAAAAAH0ADAChAKEA2wYPAAAAAAB9AAwAogCiANsGDwAAAAAAfQAMAKMAowDbBg8AAAAAAH0ADACkAKQA2wYPAAAAAAB9AAwApQClANsGDwAAAAAAfQAMAKYApgDbBg8AAAAAAH0ADACnAKcA2wYPAAAAAAB9AAwAqACoANsGDwAAAAAAfQAMAKkAqQDbBg8AAAAAAH0ADACqAKoA2wYPAAAAAAB9AAwAqwCrANsGDwAAAAAAfQAMAKwArADbBg8AAAAAAH0ADACtAK0A2wYPAAAAAAB9AAwA");
        encoded.append("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EQAWABoAQgAAAAMCDgARABcAGQAAAAAAAADwP/0ACgARABgAGwBDAAAA/QAKABEAGQAbAEQAAAADAg4AEQAaABkAAAAAAADAWED9AAoAEQAbABoARQAAAP0ACgARABwAGwBGAAAA/QAKABEAHQAbAEcAAAD9AAoAEQAeABsARwAAAP0ACgARAB8AGwBHAAAA/QAKABEAIAAbAEcAAAD9AAoAEQAhABsARwAAAP0ACgARACIAGwBHAAAAAwIOABEAIwAYAAAAAABIqopBAwIOABEAJAAYAAAAACBMUWNB/QAKABEAJQAbAIsAAAD9AAoAEQAmABsAWgAAAP0ACgARACgAGwBwAAAA/QAKABEAKQAaAIsAAAD9AAoAEQAqABoAcQAAAAMCDgARACsAGAAAAACA9gVcQf0ACgARACwAGwCMAAAA/QAKABEALQAbAE4AAAD9AAoAEQAuABsARwAAAAMCDgARADAAHADXo3A9iivMQAMCDgARADEAHADXo3A9iivMQAMCDgARADIAGQAAAAAAAAAUQAMCDgASAAAAHgAAAAAAAHSfQAMCDgASAAEAHwAAAAAAAADwPwMCDgASAAMAHwAAAAAAAAAsQP0ACgASAAQAIAAzAAAAAwIOABIABQAeAAAAAAAAZOtA/QAKABIABgAhADQAAAD9AAoAEgAHACAANQAAAP0ACgASAAgAIAA2AAAA/QAKABIACQAhADcAAAD9AAoAEgAKACAAOAAAAAMCDgASAAsAHgAAAAAAMCP1QP0ACgASAAwAIABbAAAA/QAKABIADQAgADoAAAD9AAoAEgAOACEAXAAAAP0ACgASAA8AIQBdAAAA/QAKABIAEAAgAF4AAAD9AAoAEgARACEAXwAAAP0ACgASABIAIQBgAAAA/QAKABIAEwAgAEAAAAD9AAoAEgAUACEAQQAAAP0ACgASABYAIABCAAAAAwIOABIAFwAfAAAAAAAAAPA//QAKABIAGAAhAEMAAAD9AAoAEgAZACEARAAAAAMCDgASABoAHwAAAAAAAMBYQP0ACgASABsAIABFAAAA/QAKABIAHAAhAEYAAAD9AAoAEgAdACEARwAAAP0ACgASAB4AIQBHAAAA/QAKABIAHwAhAEcAAAD9AAoAEgAgACEARwAAAP0ACgASACEAIQBHAAAA/QAKABIAIgAhAEcAAAADAg4A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");

        byte[] decoded = Base64.getDecoder().decode(encoded.toString());

        DataOutputStream dos = new DataOutputStream(new FileOutputStream("/Users/ml093043/tmp/nexial/junk.xls"));
        dos.write(decoded);
        dos.close();
    }

    protected static File makeDummyContent(String dummyFilePath) throws IOException {
        // dummy content
        List<String> text = new ArrayList<>();
        for (int i = 0; i < 10; i++) { text.add(new Random().alphanumeric("100")); }

        File sourceFile = new File(dummyFilePath);
        sourceFile.delete();
        FileUtil.appendFile(sourceFile, text, "\n");
        return sourceFile;
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
        Assert.assertEquals(expectedCount, unzipFiles.size());
        unzipFiles.forEach(file -> {
            System.out.println(file);
            Assert.assertEquals(100, file.length());
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