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

package org.nexial.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.OutputFileUtils;
import org.springframework.util.ResourceUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;

public class NexialTestUtils {
    public static final String RESULT = "NONE";
    public static final String BACKUP = "BACKUP";

    protected NexialTestUtils() { }

    public static void prep(final Class testClass, String classesRoot) throws IOException {
        String filepath = StringUtils.replace(testClass.getName(), ".", "/");
        File excel = ResourceUtils.getFile("classpath:" + filepath + ".xlsx");

        System.out.println("trying excel at " + excel.getAbsolutePath());

        if (!excel.exists()) {
            // possibly renamed due to previous test run, let's rename (copy) it back..
            File excelBackup = ResourceUtils.getFile("classpath:" + filepath + "." + BACKUP + ".xlsx");
            FileUtils.copyFile(excelBackup, excel);
        } else {
            // i have the excel file, but it might be renamed to the test result, let's back it up for future use
            File excelBackup = new File(StringUtils.substringBeforeLast(excel.getAbsolutePath(), ".") + "." +
                                        BACKUP + ".xlsx");
            FileUtils.copyFile(excel, excelBackup);
        }

        setupCommonProps(excel, classesRoot);
        cleanupPastRemant(testClass, classesRoot);
    }

    public static void setupCommonProps(File excel, String classesRoot) throws IOException {
        Assert.assertTrue(excel.canRead());
        //System.setProperty("log4j.configuration", "nexial-log4j.xml");
        System.setProperty(OPT_EXCEL_FILE, excel.getAbsolutePath());
        System.setProperty(OPT_INPUT_EXCEL_FILE, excel.getAbsolutePath());
        System.setProperty(OPT_OUT_DIR, classesRoot);

        FileUtils.forceMkdir(new File(classesRoot + "/summary"));
        FileWriter fw = new FileWriter(classesRoot + "/summary/mail.properties");
        fw.write("Hello World=Needed for test to pass");
        fw.close();
    }

    public static void cleanupPastRemant(final Class testClass, String classesRoot) {
        String subDir = StringUtils.replace(testClass.getPackage().getName(), ".", separator);
        File searchFrom = new File(classesRoot + separator + subDir);
        String searchFor = testClass.getSimpleName() + "." + RESULT + ".";

        Collection<File> pastResultSpreadsheets =
            FileUtils.listFiles(
                searchFrom,
                new IOFileFilter() {
                    @Override
                    public boolean accept(File file) { return StringUtils.contains(file.getName(), searchFor); }

                    @Override
                    public boolean accept(File dir, String name) { return StringUtils.contains(name, searchFor); }
                },
                new IOFileFilter() {
                    @Override
                    public boolean accept(File file) { return true; }

                    @Override
                    public boolean accept(File dir, String name) { return true; }
                });

        for (File oldSpreadsheets : pastResultSpreadsheets) {
            ConsoleUtils.log("deleting previous test result " + oldSpreadsheets);
            FileUtils.deleteQuietly(oldSpreadsheets);
        }

        File excel = new File(classesRoot + "/" + StringUtils.replace(testClass.getName(), ".", "/") + ".xlsx");
        String resultFileName = OutputFileUtils.add(excel.getAbsolutePath(), RESULT, 2);
        FileUtils.deleteQuietly(new File(resultFileName));

        // one last try just to make sure we really clean things up
        String resourceRelPath = toPackageResourcePath(testClass, searchFor + "xlsx");
        try {
            File resource = ResourceUtils.getFile(resourceRelPath);
            if (resource.exists()) {
                ConsoleUtils.log("delete classpath resource " + resource.getAbsolutePath());
                FileUtils.deleteQuietly(resource);
            } else {
                ConsoleUtils.log("classpath resource " + resourceRelPath + " not found");
            }
        } catch (FileNotFoundException e) {
            ConsoleUtils.log("ignoring the classpath resource (" + resourceRelPath + ") that cannot be found: " +
                             e.getMessage());
        }
    }

    public static String getResourcePath(Class baseClass, String filename) throws FileNotFoundException {
        return getResourceFile(baseClass, filename).getAbsolutePath();
    }

    public static File getResourceFile(Class baseClass, String filename) throws FileNotFoundException {
        return ResourceUtils.getFile("classpath:" + toPackageResourcePath(baseClass, filename));
    }

    public static String resolveTestDbUrl() throws FileNotFoundException {
        return "jdbc:sqlite:" + getResourceFile(NexialTestUtils.class, "nexial-test.db");
    }

    protected static String toPackageResourcePath(Class baseClass, String filename) {
        return StringUtils.replace(baseClass.getPackage().getName(), ".", "/") + "/" + filename;
    }
}