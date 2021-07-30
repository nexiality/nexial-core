/*
 * Copyright 2012-2021 the original author or authors.
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

package org.nexial.core.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.excel.Excel;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import static org.nexial.core.NexialConst.Project.DEF_DATAFILE_SUFFIX;

public class TestDataTest {
    @Test
    public void collectAllDataSet() throws Exception {

        String excelFile = getPath("TestScenarioTest1" + DEF_DATAFILE_SUFFIX);
        System.out.println("excelFile = " + excelFile);
        Excel excel = new Excel(new File(excelFile));
        TestData testData = new TestData(excel, Collections.singletonList("new_test1"));

        Assert.assertTrue(testData.isFallbackToPrevious());
        Assert.assertEquals(testData.getIteration(), "3");
        Assert.assertEquals(testData.getMailTo(), "nobody@nowhere.com");

        Assert.assertEquals(testData.getIntValue(1, "nexial.delayBetweenStepsMs"), 1200);
        Assert.assertTrue(testData.getBooleanValue(1, "nexial.failFast"));
        Assert.assertTrue(testData.getBooleanValue(1, "nexial.verbose"));
        Assert.assertEquals(testData.getIntValue(1, "nexial.pollWaitMs"), 800);
        Assert.assertArrayEquals(testData.getAllValue("nexial.textDelim").toArray(new String[3]),
                                 new String[]{",", "", "|"});
        Assert.assertArrayEquals(testData.getAllValue("username").toArray(new String[3]),
                                 new String[]{"jsmith", "ymansoor", "bjoghereus"});
        Assert.assertArrayEquals(testData.getAllValue("password").toArray(new String[3]),
                                 new String[]{"1ymmiJ", "", ""});
        Assert.assertArrayEquals(testData.getAllValue("rememberMe").toArray(new String[3]),
                                 new String[]{"true", "", "false"});
        Assert.assertArrayEquals(testData.getAllValue("userType").toArray(new String[3]),
                                 new String[]{"", "admin", ""});
        Assert.assertArrayEquals(testData.getAllValue("fullScreen").toArray(new String[3]),
                                 new String[]{"true", "", ""});
        Assert.assertArrayEquals(testData.getAllValue("firstName").toArray(new String[3]),
                                 new String[]{"John", "Yegear", "Boris"});
        Assert.assertArrayEquals(testData.getAllValue("lastName").toArray(new String[3]),
                                 new String[]{"Smith", "Mansoor", "Joghereus"});
        Assert.assertArrayEquals(testData.getAllValue("remberMe").toArray(new String[3]),
                                 new String[]{"false", "", "true"});
        Assert.assertArrayEquals(testData.getAllValue("years.of.service").toArray(new String[3]),
                                 new String[]{"4", "4.2", "5"});
        Assert.assertEquals(testData.getValue(1, "db.myDB.conn"), "jdbc:odbc:myDB");
        Assert.assertEquals(testData.getValue(1, "db.myDB.user"), "user1");
        Assert.assertEquals(testData.getValue(1, "db.myDB.password"), "password");

        Assert.assertEquals(testData.getIntValue(3, "nexial.delayBetweenStepsMs"), 1200);
        Assert.assertEquals(testData.getValue(1, "nexial.textDelim"), ",");
        Assert.assertEquals(testData.getValue(2, "nexial.textDelim"), ",");
        Assert.assertEquals(testData.getValue(3, "nexial.textDelim"), "|");
        Assert.assertTrue(testData.getBooleanValue(1, "rememberMe"));
        Assert.assertTrue(testData.getBooleanValue(2, "rememberMe"));
        Assert.assertFalse(testData.getBooleanValue(3, "rememberMe"));
        Assert.assertEquals(testData.getValue(1, "userType"), "");
        Assert.assertEquals(testData.getValue(2, "userType"), "admin");
        Assert.assertEquals(testData.getValue(3, "userType"), "admin");
    }

    @Test
    public void collectAllDataSet_2() throws Exception {

        String excelFile = getPath("TestScenarioTest1" + DEF_DATAFILE_SUFFIX);
        System.out.println("excelFile = " + excelFile);

        Excel excel = new Excel(new File(excelFile));
        TestData testData = new TestData(excel, Arrays.asList("new_test1", "new_test2"));

        // inherit from new_test1
        Assert.assertTrue(testData.isFallbackToPrevious());
        Assert.assertEquals(testData.getIteration(), "3");

        // overridden from new_test2
        Assert.assertEquals(testData.getMailTo(), "johnny@bgood.com");

        Assert.assertEquals(testData.getIntValue(1, "nexial.delayBetweenStepsMs"), 1201);
        Assert.assertTrue(testData.getBooleanValue(1, "nexial.failFast"));
        Assert.assertTrue(testData.getBooleanValue(1, "nexial.verbose"));
        Assert.assertEquals(testData.getIntValue(1, "nexial.pollWaitMs"), 800);
        Assert.assertArrayEquals(testData.getAllValue("nexial.delayBetweenStepsMs").toArray(new String[3]),
                                 new String[]{"1201", "", "1202"});
        Assert.assertArrayEquals(testData.getAllValue("nexial.textDelim").toArray(new String[3]),
                                 new String[]{",", "", "|"});
        Assert.assertArrayEquals(testData.getAllValue("username").toArray(new String[3]),
                                 new String[]{"", "janson", ""});
        Assert.assertArrayEquals(testData.getAllValue("password").toArray(new String[3]),
                                 new String[]{"1ymmiJ", "", ""});
        Assert.assertArrayEquals(testData.getAllValue("rememberMe").toArray(new String[3]),
                                 new String[]{"false", "false", ""});
        Assert.assertArrayEquals(testData.getAllValue("userType").toArray(new String[3]),
                                 new String[]{"", "admin", ""});
        Assert.assertArrayEquals(testData.getAllValue("fullScreen").toArray(new String[3]),
                                 new String[]{"true", "", ""});
        Assert.assertArrayEquals(testData.getAllValue("firstName").toArray(new String[3]),
                                 new String[]{"John", "Yegear", "Boris"});
        Assert.assertArrayEquals(testData.getAllValue("lastName").toArray(new String[3]),
                                 new String[]{"Smith", "Mansoor", "Joghereus"});
        Assert.assertArrayEquals(testData.getAllValue("years.of.service").toArray(new String[3]),
                                 new String[]{"4", "4.2", "5"});
        Assert.assertArrayEquals(testData.getAllValue("newData").toArray(new String[3]),
                                 new String[]{"12345", "", "abc"});
        Assert.assertEquals(testData.getValue(1, "db.myDB.conn"), "jdbc:odbc:myDB");
        Assert.assertEquals(testData.getValue(1, "db.myDB.user"), "user1");
        Assert.assertEquals(testData.getValue(1, "db.myDB.password"), "password");

        // new data defined in new_test2
        Assert.assertEquals(testData.getValue(1, "newData"), "12345");
        Assert.assertEquals(testData.getValue(2, "newData"), "12345");
        Assert.assertEquals(testData.getValue(3, "newData"), "abc");

        Assert.assertEquals(testData.getIntValue(1, "nexial.delayBetweenStepsMs"), 1201);
        Assert.assertEquals(testData.getIntValue(2, "nexial.delayBetweenStepsMs"), 1201);
        Assert.assertEquals(testData.getIntValue(3, "nexial.delayBetweenStepsMs"), 1202);
        Assert.assertEquals(testData.getValue(1, "nexial.textDelim"), ",");
        Assert.assertEquals(testData.getValue(2, "nexial.textDelim"), ",");
        Assert.assertEquals(testData.getValue(3, "nexial.textDelim"), "|");
        Assert.assertEquals(testData.getValue(1, "username"), "");
        Assert.assertEquals(testData.getValue(2, "username"), "janson");
        Assert.assertEquals(testData.getValue(3, "username"), "janson");
        Assert.assertFalse(testData.getBooleanValue(1, "rememberMe"));
        Assert.assertFalse(testData.getBooleanValue(2, "rememberMe"));
        Assert.assertFalse(testData.getBooleanValue(3, "rememberMe"));
        Assert.assertEquals(testData.getValue(1, "userType"), "");
        Assert.assertEquals(testData.getValue(2, "userType"), "admin");
        Assert.assertEquals(testData.getValue(3, "userType"), "admin");
    }

    //Strikethrough data test
    @Test
    public void collectStrikthroughDataSet() throws Exception {

        String excelFile = getPath("TestScenarioTest2" + DEF_DATAFILE_SUFFIX);
        System.out.println("excelFile = " + excelFile);

        Excel excel = new Excel(new File(excelFile));
        TestData testData = new TestData(excel, Arrays.asList("skippedDataTest"));

        Assert.assertArrayEquals(testData.getAllValue("username").toArray(new String[3]),
                                 new String[]{"", "janson", ""});
        Assert.assertArrayEquals(testData.getAllValue("rememberMe").toArray(new String[3]),
                                 new String[]{"false", "false", ""});
        Assert.assertArrayEquals(testData.getAllValue("name").toArray(new String[3]),
                                 new String[]{"abc", "", "xyz"});

        // 'email' Defined in default sheet but has strikethrough in scenario sheet
        Assert.assertArrayEquals(testData.getAllValue("email").toArray(new String[3]),
                                 new String[]{"abc@gmail.com", "", ""});

        // only described in scenario sheets
        Assert.assertEquals(testData.getAllValue("password").size(), 0);
    }

    public String getPath(String filename) throws FileNotFoundException {
        return ResourceUtils.getFile("classpath:" +
                                     StringUtils.replace(this.getClass().getPackage().getName(), ".", "/") + "/" +
                                     filename).getAbsolutePath();
    }
}