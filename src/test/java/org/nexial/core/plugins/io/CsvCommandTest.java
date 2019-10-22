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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.text.similarity.LevenshteinDetailedDistance;
import org.apache.commons.text.similarity.LevenshteinResults;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.NexialConst.Compare;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.model.TestStep;

import com.google.gson.JsonObject;
import com.univocity.parsers.csv.CsvParser;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.Compare.*;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.OPT_OUT_DIR;
import static org.nexial.core.plugins.io.ComparisonResult.*;
import static org.nexial.core.plugins.io.FileComparisonReport.GSON;
import static org.nexial.core.plugins.io.IoCommand.levenshtein;

public class CsvCommandTest {
    private static final String CLASSNAME = CsvCommandTest.class.getSimpleName();
    private static final String TMP_PATH = SystemUtils.getJavaIoTmpDir().getAbsolutePath();
    private static final String LOG_FILE = StringUtils.appendIfMissing(TMP_PATH, separator) + CLASSNAME + ".log";

    private static ExecutionContext context = new MockExecutionContext(true) {
        @Override
        public TestStep getCurrentTestStep() {
            return new TestStep() {
                @Override
                public String generateFilename(String ext) {
                    return CLASSNAME + StringUtils.prependIfMissing(StringUtils.trim(ext), ".");
                }
            };
        }
    };
    private final String resourceBasePath = "/" + StringUtils.replace(this.getClass().getPackage().getName(), ".", "/");

    @BeforeClass
    public static void init() {
        System.setProperty(OPT_OUT_DIR, TMP_PATH);
    }

    @AfterClass
    public static void afterClass() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
        FileUtils.deleteQuietly(new File(LOG_FILE));
        FileUtils.deleteQuietly(new File(StringUtils.substringBeforeLast(LOG_FILE, ".log") + ".json"));
    }

    @Before
    public void beforeTest() {
        context.setData(GEN_COMPARE_LOG, true);
        context.setData(GEN_COMPARE_JSON, true);
        context.setData(LOG_MATCH, true);
    }

    @Test
    public void parseAsCSV() {
        CsvCommand command = new CsvCommand();
        command.init(context);

        String fixtureFile = resourceBasePath + "/" + this.getClass().getSimpleName() + ".csv";
        File fixture = new File(getClass().getResource(fixtureFile).getFile());
        List<String[]> lines = command.parseAsCSV(fixture);

        Assert.assertNotNull(lines);
        Assert.assertEquals(1197, lines.size());
        Assert.assertEquals("permalink,company,numEmps,category,city,state,fundedDate,raisedAmt,raisedCurrency,round",
                            formatSample(lines.get(0)));
        Assert.assertEquals("rebelmonkey,Rebel Monkey,15,web,New York,NY,5-Feb-08,1000000,USD,a",
                            formatSample(lines.get(1196)));
        Assert.assertEquals("zivity,Zivity,16,web,San Francisco,CA,1-Aug-07,1000000,USD,seed",
                            formatSample(lines.get(297)));
        Assert.assertEquals("matchmine,MatchMine,,web,Needham,MA,1-Sep-07,10000000,USD,a",
                            formatSample(lines.get(1035)));
    }

    @Test
    public void logComparisonReport() throws Exception {
        String basePath = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator + this.getClass().getSimpleName();
        File logFile = new File(basePath + ".log");
        File jsonFile = new File(basePath + ".json");
        FileUtils.deleteQuietly(logFile);
        FileUtils.deleteQuietly(jsonFile);

        CsvCommand command = new CsvCommand();
        command.init(context);

        FileComparisonReport fixture = new FileComparisonReport();
        fixture.addFileMismatch(file("file comparison starts", "/asg/asd/fs/mytxt.csv", "/asdg/asd.csv"));
        fixture.addFileMismatch(file("file size differences found", "50952 bytes", "50112 bytes"));
        fixture.addFileMismatch(file("line count differences found", "19 lines", "17 lines"));
        fixture.addLineMismatch(line(0, "line difference found", "This is a test.  Do not be alarmed", "huh"));
        command.logComparisonReport("LOG FILE", fixture);

        String logActual = FileUtils.readFileToString(logFile, DEF_FILE_ENCODING);
        logActual = StringUtils.trim(StringUtils.substringBefore(logActual, "*****"));
        System.out.println("logActual = \n" + logActual);
        Assert.assertEquals("MATCH                                    EA LINE# CONTENT\r\n" +
                            "======================================== == ===== ================================================== \r\n" +
                            "file comparison starts                   E        /asg/asd/fs/mytxt.csv                              \r\n" +
                            "                                         A        /asdg/asd.csv                                      \r\n" +
                            "---------------------------------------------------------------------------------------------------- \r\n" +
                            "file size differences found              E        50952 bytes                                        \r\n" +
                            "                                         A        50112 bytes                                        \r\n" +
                            "---------------------------------------------------------------------------------------------------- \r\n" +
                            "line count differences found             E        19 lines                                           \r\n" +
                            "                                         A        17 lines                                           \r\n" +
                            "---------------------------------------------------------------------------------------------------- \r\n" +
                            "line difference found                    E      0 [This is a test.  Do not be alarmed]               \r\n" +
                            "                                         A      0 [huh]                                              \r\n" +
                            "---------------------------------------------------------------------------------------------------- \r\n" +
                            "\r\n" +
                            "Legend:   E=EXPECTED, A=ACTUAL\r\n" +
                            "Summary:  Lines matched:    0\r\n" +
                            "          Lines mismatched: 1\r\n" +
                            "          Match percentage: 0.00%",
                            logActual);

        String jsonActual = FileUtils.readFileToString(jsonFile, DEF_FILE_ENCODING);
        JsonObject json = GSON.fromJson(jsonActual, JsonObject.class);
        json.remove("generated-on");
        json.remove("powered-by");
        jsonActual = GSON.toJson(json);
        System.out.println("jsonActual = " + jsonActual);

        Assert.assertEquals("{\n" +
                            "  \"summary\": [\n" +
                            "    {\n" +
                            "      \"message\": \"file comparison starts\",\n" +
                            "      \"actual\": \"/asdg/asd.csv\",\n" +
                            "      \"expected\": \"/asg/asd/fs/mytxt.csv\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "      \"message\": \"file size differences found\",\n" +
                            "      \"actual\": \"50112 bytes\",\n" +
                            "      \"expected\": \"50952 bytes\"\n" +
                            "    },\n" +
                            "    {\n" +
                            "      \"message\": \"line count differences found\",\n" +
                            "      \"actual\": \"17 lines\",\n" +
                            "      \"expected\": \"19 lines\"\n" +
                            "    }\n" +
                            "  ],\n" +
                            "  \"details\": [\n" +
                            "    {\n" +
                            "      \"expected-line\": 0,\n" +
                            "      \"expected\": \"This is a test.  Do not be alarmed\",\n" +
                            "      \"actual-line\": 0,\n" +
                            "      \"actual\": \"huh\",\n" +
                            "      \"message\": \"line difference found\",\n" +
                            "      \"additionalMessages\": []\n" +
                            "    }\n" +
                            "  ],\n" +
                            "  \"statistics\": \"Lines matched: 0, Lines mismatched: 1. Match percentage: 0.00%\"\n" +
                            "}",
                            jsonActual);
    }

    @Test
    public void logComparisonReport_new() throws Exception {
        String basePath = SystemUtils.getJavaIoTmpDir().getAbsolutePath() + separator + this.getClass().getSimpleName();
        File logFile = new File(basePath + ".log");
        File jsonFile = new File(basePath + ".json");
        FileUtils.deleteQuietly(logFile);
        FileUtils.deleteQuietly(jsonFile);

        CsvCommand command = new CsvCommand();
        command.init(context);

        FileComparisonReport fixture = new FileComparisonReport();
        fixture.addFileMismatch(file("file comparison starts", "/asg/asd/fs/mytxt.csv", "/asdg/asd.csv"));
        fixture.addFileMismatch(file("file size differences found", "50952 bytes", "50112 bytes"));
        fixture.addFileMismatch(file("line count differences found", "19 lines", "17 lines"));
        fixture.addLineMismatch(line(0, "line difference found", "This is a test.  Do not be alarmed", "huh"));
        fixture.addLineMatch(lineMatched(1, null, "taxID,weekBeginDate,name,gross"));
        fixture.addLineMismatch(lineMissing(6, "623132658,20130415,ANDERSON/CARTER,3665.00", null));
        fixture.addLineMatch(lineMatched(7, null, "623132658,20130422,ANDERSON/CARTER,5285.00").maligned(7, 6));
        fixture.addLineMatch(line(8, "mismatch due to trailing spaces",
                                  "623132658,20130422,ANDERSON/CARTER,5285.00  ",
                                  "623132658,20130422,ANDERSON/CARTER,5285.00").maligned(8, 7));
        fixture.addLineMismatch(lineMissing(8, "623132658,20130429,ANDERSON/CARTER,4475.00", null));

        String expected = "623132658,20130527,ANDERSON/CARTER,3957.50";
        String actual = "623132658,20130527,ANDERSON/CARTER,3958.51";
        LevenshteinResults lsr = levenshtein.apply(expected, actual);
        fixture.addLineMismatch(lineDiff(12, lsr, expected, actual));

        command.logComparisonReport("LOG FILE", fixture);
        // command.logComparisonReport("JSON FILE", fixture, "json");

        String logActual = FileUtils.readFileToString(logFile, DEF_FILE_ENCODING);
        // logActual = StringUtils.trim(StringUtils.substringBefore(logActual, "*****"));
        System.out.println("logActual = \n" + logActual);
    }

    @Test
    public void levenshteinDistance() {
        String string1 = "Monkey see, moneky do";
        String string2 = "Money see, monei doo";

        LevenshteinDetailedDistance levenshtein = LevenshteinDetailedDistance.getDefaultInstance();

        // missing and mismatch :: distance = insert + delete + substituted
        LevenshteinResults lsResult = levenshtein.apply(string1, string2);
        System.out.println("lsResult = " + lsResult);
        System.out.println("lsResult.getDistance() = " + lsResult.getDistance());
        System.out.println("lsResult.getDeleteCount() = " + lsResult.getDeleteCount());
        System.out.println("lsResult.getInsertCount() = " + lsResult.getInsertCount());
        System.out.println("lsResult.getSubstituteCount() = " + lsResult.getSubstituteCount());
        System.out.println();

        // mismatch values :: distance = substituted
        string1 = "623132658,20130318,ANDERSON/CARTER,5270.00";
        string2 = "623132658,20130325,ANDERSON/CARTER,5622.50";
        lsResult = levenshtein.apply(string1, string2);
        System.out.println("lsResult = " + lsResult);
        System.out.println("lsResult.getDistance() = " + lsResult.getDistance());
        System.out.println("lsResult.getDeleteCount() = " + lsResult.getDeleteCount());
        System.out.println("lsResult.getInsertCount() = " + lsResult.getInsertCount());
        System.out.println("lsResult.getSubstituteCount() = " + lsResult.getSubstituteCount());
        System.out.println();

        // missing field :: distance = delete
        string1 = "623132658,20130318,ANDERSON/CARTER,5270.00";
        string2 = "623132658,20130318,ANDERSON/CARTER";
        lsResult = levenshtein.apply(string1, string2);
        System.out.println("lsResult = " + lsResult);
        System.out.println("lsResult.getDistance() = " + lsResult.getDistance());
        System.out.println("lsResult.getDeleteCount() = " + lsResult.getDeleteCount());
        System.out.println("lsResult.getInsertCount() = " + lsResult.getInsertCount());
        System.out.println("lsResult.getSubstituteCount() = " + lsResult.getSubstituteCount());
        System.out.println();

        string1 = "623132658,20130318,ANDERSON/CARTER,5270.00";
        string2 = "    623132658, 20130318,    ANDER SON/CARTER   ";
        lsResult = levenshtein.apply(string1, string2);
        System.out.println("lsResult = " + lsResult);
        System.out.println("lsResult.getDistance() = " + lsResult.getDistance());
        System.out.println("lsResult.getDeleteCount() = " + lsResult.getDeleteCount());
        System.out.println("lsResult.getInsertCount() = " + lsResult.getInsertCount());
        System.out.println("lsResult.getSubstituteCount() = " + lsResult.getSubstituteCount());
        System.out.println();

        lsResult = levenshtein.apply(StringUtils.deleteWhitespace(string1), StringUtils.deleteWhitespace(string2));
        System.out.println("lsResult = " + lsResult);
        System.out.println("lsResult.getDistance() = " + lsResult.getDistance());
        System.out.println("lsResult.getDeleteCount() = " + lsResult.getDeleteCount());
        System.out.println("lsResult.getInsertCount() = " + lsResult.getInsertCount());
        System.out.println("lsResult.getSubstituteCount() = " + lsResult.getSubstituteCount());
        System.out.println();

        // field swapped :: distance = delete + insert; delete = insert
        string1 = "623132658,20130318,ANDERSON/CARTER,5270.00";
        string2 = "623132658,20130318,5270.00,ANDERSON/CARTER";
        lsResult = levenshtein.apply(string1, string2);
        System.out.println("lsResult = " + lsResult);
        System.out.println("lsResult.getDistance() = " + lsResult.getDistance());
        System.out.println("lsResult.getDeleteCount() = " + lsResult.getDeleteCount());
        System.out.println("lsResult.getInsertCount() = " + lsResult.getInsertCount());
        System.out.println("lsResult.getSubstituteCount() = " + lsResult.getSubstituteCount());
        System.out.println();

        // utterly mismatched: distance greater than string length
        string1 = "623132658,20130318,ANDERSON/CARTER,5270.00";
        string2 = "Life's like a box of chocolate, you never know what you gonna find";
        lsResult = levenshtein.apply(string1, string2);
        System.out.println("lsResult = " + lsResult);
        System.out.println("lsResult.getDistance() = " + lsResult.getDistance());
        System.out.println("lsResult.getDeleteCount() = " + lsResult.getDeleteCount());
        System.out.println("lsResult.getInsertCount() = " + lsResult.getInsertCount());
        System.out.println("lsResult.getSubstituteCount() = " + lsResult.getSubstituteCount());
        System.out.println();
    }

    @Test
    public void compare_failFast() throws Exception {
        CsvCommand command = new CsvCommand();
        command.init(context);

        // test null/empty/blank
        try {
            System.out.println(command.compare("", "", "true"));
            Assert.fail("EXPECTS AssertionError not thrown");
        } catch (AssertionError e) {
            // expected
        }

        // test null/empty/blank
        try {
            command.compare(" ", " ", "true");
            Assert.fail("EXPECTS AssertionError not thrown");
        } catch (AssertionError e) {
            // expected
        }

        // test for fail fast
        List<String> expectedData = new ArrayList<>();
        List<String> actualData = new ArrayList<>();
        List<List<String>> messages = new ArrayList<>();

        expectedData.add("This is a test.");
        actualData.add("Nope");
        messages.add(Collections.singletonList("EXPECTED and ACTUAL sizes are different"));

        expectedData.add("This is a test.");
        actualData.add("This is a test too.");
        messages.add(Collections.singletonList("EXPECTED and ACTUAL sizes are different"));

        expectedData.add("This is a test.");
        actualData.add("This is a TEST.");
        messages.add(Collections.singletonList("mismatch due to letter case"));

        expectedData.add("This is a test.");
        actualData.add("This is  atest.");
        messages.add(Collections.singletonList("mismatch due to extra spaces"));

        expectedData.add("   This is a test.");
        actualData.add("This is at est.   ");
        messages.add(Collections.singletonList("mismatch due to extra spaces"));

        expectedData.add("   This \nis a test.");
        actualData.add("This is at lest.   ");
        messages.add(Collections.singletonList("number of lines are different"));

        testCompare("compare_failFast", expectedData, actualData, messages, true);
    }

    @Test
    public void compare_missing_lines() throws Exception {
        CsvCommand command = new CsvCommand();
        command.init(context);

        List<String> expectedData = new ArrayList<>();
        List<String> actualData = new ArrayList<>();
        List<List<String>> messages = new ArrayList<>();

        // test #1
        expectedData.add("This is a test.\n" +
                         "And this is not.");
        actualData.add("Nope");
        messages.add(Arrays.asList("EXPECTED and ACTUAL sizes are different",
                                   "number of lines are different            E        2 lines",
                                   "11 mismatch(s) found in this line:       E      1 [This is a test.]",
                                   "  8 removed                              A      1 [Nope]",
                                   "missing line in ACTUAL                   E      2 [And this is not.]"));

        // test #2
        expectedData.add("This is a test.\n" +
                         "And this is not.\n\n");
        actualData.add("This is a test.\n" +
                       "And this is not.");
        messages.add(Arrays.asList("EXPECTED and ACTUAL sizes are different",
                                   "number of lines are different            E        3 lines",
                                   "missing line in ACTUAL                   E      3 []",
                                   "A      3 ***** MISSING"));

        // test #3
        expectedData.add("This is a test.\n" +
                         "This is the second line.\n" +
                         "This is the 3rd and last line.\n");
        actualData.add("This is a test.\n" +
                       "This is the 2nd and last line.");
        messages.add(Arrays.asList("EXPECTED and ACTUAL sizes are different",
                                   "number of lines are different            E        3 lines",
                                   "8 mismatch(s) found in this line:        E      2 [This is the second line.]",
                                   "  4 added                                A      2 [This is the 2nd and last line.]",
                                   "missing line in ACTUAL                   E      3 [This is the 3rd and last line.]"
                                  ));

        // test #4
        expectedData.add("This is a test.\n" +
                         "This is the second line.\n" +
                         "Every day is the start of the another was-tomorrow.\n");
        actualData.add("This is a test.\n" +
                       "Every day is the end of the night before");
        messages.add(Arrays.asList(
            "EXPECTED and ACTUAL sizes are different",
            "number of lines are different            E        3 lines",
            "24 mismatch(s) found in this line:       E      2 [This is the second line.]",
            "  12 added                               A      2 [Every day is the end of the night before]",
            "missing line in ACTUAL                   E      3 [Every day is the start of the another was-tomorrow.]",
            "A      3 ***** MISSING"));

        testCompare("compare_missing_lines", expectedData, actualData, messages, false);
    }

    @Test
    public void compare_dangling_lines() throws Exception {
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
        messages.add(null);
        // messages.add(Arrays.asList("content matched exactly"));

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
        messages.add(Arrays.asList(
            "EXPECTED and ACTUAL sizes are different",
            "number of lines are different            E        10 lines",
            "mismatch due to letter case              E      4 [623132658,20130401,Anderson/CARTER,3402.50]",
            "5 mismatch(s) found in this line:        E      5 [623132658,20130480,ANDERSON/CARTER,3222.50]",
            "  3 removed                              A      5 [623132658,20130408,ANDERSON/CARTER,3222]",
            "mismatch due to leading/trailing spaces  E      6 [623132658,20130415,ANDERSON/CARTER,3665.00]",
            "extra line found in ACTUAL               E     10 ***** MISSING",
            "extra line found in ACTUAL               E     11 ***** MISSING",
            "extra line found in ACTUAL               E     12 ***** MISSING",
            "  (EXPECTED line 10, ACTUAL line 13)     A     13 [623132658,20130603,ANDERSON/CARTER,5675.00]"));

        testCompare("compare_dangling_lines", expectedData, actualData, messages, false);
    }

    @Test
    public void compare_missing_text() throws Exception {
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
                       "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
                       "623132658,20130429,ANDERSON/CARTER,4475.00\n" +
                       "623132658,20130513,ANDERSON/CARTER,4377.50\n" +
                       "623132658,20130520,ANDERSON/CARTER,4745.00\n" +
                       "623132658,20130527,ANDERSON/CARTER,3958.51\n" +
                       "623132658,20130603,ANDERSON/CARTER,5675.00\n");
        messages.add(Arrays.asList(
            "EXPECTED and ACTUAL sizes are different",
            "number of lines are different            E        13 lines",
            "missing line in ACTUAL                   E      6 [623132658,20130415,ANDERSON/CARTER,3665.00]",
            "  (EXPECTED line 7, ACTUAL line 6)       A      6 [623132658,20130422,ANDERSON/CARTER,5285.00]",
            "  (EXPECTED line 8, ACTUAL line 7)       A      7 [623132658,20130429,ANDERSON/CARTER,4475.00]",
            "missing line in ACTUAL                   E      8 [623132658,20130429,ANDERSON/CARTER,4475.00]",
            "missing line in ACTUAL                   E      9 [623132658,20130506,ANDERSON/CARTER,4665.00]",
            "  (EXPECTED line 10, ACTUAL line 8)      A      8 [623132658,20130513,ANDERSON/CARTER,4377.50]",
            "  (EXPECTED line 11, ACTUAL line 9)      A      9 [623132658,20130520,ANDERSON/CARTER,4745.00]",
            "2 mismatch(s) found in this line:        E     12 [623132658,20130527,ANDERSON/CARTER,3957.50]",
            "  2 replaced                             A     10 [623132658,20130527,ANDERSON/CARTER,3958.51]"));

        testCompare("compare_missing_text", expectedData, actualData, messages, false);
    }

    @Test
    public void compare_extra_trailing_lines() throws Exception {
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
                       "623132658,20130603,ANDERSON/CARTER,5675.00\n" +
                       "623132658,20130603,ANDERSON/CARTER,5675.00\n" +
                       "623132658,20130603,ANDERSON/CARTER,5675.00\n" +
                       "623132658,20130603,ANDERSON/CARTER,5675.00\n");
        messages.add(Arrays.asList("number of lines are different            E        13 lines",
                                   "extra line found in ACTUAL               E     14 ***** MISSING",
                                   "extra line found in ACTUAL               E     15 ***** MISSING",
                                   "extra line found in ACTUAL               E     16 ***** MISSING"));

        testCompare("compare_extra_trailing_lines", expectedData, actualData, messages, false);
    }

    @Test
    public void parseCsv_comma_newline() {
        CsvParser parser = CsvCommand.newCsvParser("\"", ",", "\n", false, -1);

        // test 1: last row has fewer columns
        String fixture = "col1,col2,col3,col3a\n" +
                         "col4,col5,col6,col6a\n" +
                         "col7,col8";
        List<String[]> csv = parser.parseAll(new StringReader(fixture));
        Assert.assertNotNull(csv);
        Assert.assertEquals(3, csv.size());
        Assert.assertEquals(4, csv.get(0).length);
        Assert.assertArrayEquals(new String[]{"col1", "col2", "col3", "col3a"}, csv.get(0));
        Assert.assertArrayEquals(new String[]{"col4", "col5", "col6", "col6a"}, csv.get(1));
        Assert.assertArrayEquals(new String[]{"col7", "col8"}, csv.get(2));

        // test 2: first row has fewer columns - ASSERT FIRST COLUMN IS THE BASE
        fixture = "col1,col2,col3\n" +
                  "col4,col5,col6,col6a\n" +
                  "col7,col8";
        csv = parser.parseAll(new StringReader(fixture));
        Assert.assertNotNull(csv);
        Assert.assertEquals(3, csv.size());
        Assert.assertEquals(3, csv.get(0).length);
        Assert.assertArrayEquals(new String[]{"col1", "col2", "col3"}, csv.get(0));
        Assert.assertArrayEquals(new String[]{"col4", "col5", "col6", "col6a"}, csv.get(1));
        Assert.assertArrayEquals(new String[]{"col7", "col8"}, csv.get(2));
    }

    @Test
    public void parseCsv_comma_newline_quote() {
        CsvParser parser = CsvCommand.newCsvParser("\"", ",", "\n", false, -1);

        // test 1: last row has fewer columns AND first row has quoted value
        String fixture = "col1,col2,\"col 3\",col3a\n" +
                         "\"col 4\",col5,col6,col6a\n" +
                         "col7,col8,\"\"";
        List<String[]> csv = parser.parseAll(new StringReader(fixture));
        Assert.assertNotNull(csv);
        Assert.assertEquals(3, csv.size());
        Assert.assertEquals(4, csv.get(0).length);
        Assert.assertArrayEquals(new String[]{"col1", "col2", "\"col 3\"", "col3a"}, csv.get(0));
        Assert.assertArrayEquals(new String[]{"\"col 4\"", "col5", "col6", "col6a"}, csv.get(1));
        Assert.assertArrayEquals(new String[]{"col7", "col8", "\"\""}, csv.get(2));

        // test 2: single quote not recognized
        fixture = "col1,col2,\" \",col3\n" +
                  "col 4,,col5,col6,col6a\n" +
                  "col7,col8,' '";
        csv = parser.parseAll(new StringReader(fixture));
        Assert.assertNotNull(csv);
        Assert.assertEquals(3, csv.size());
        Assert.assertEquals(4, csv.get(0).length);
        Assert.assertArrayEquals(new String[]{"col1", "col2", "\" \"", "col3"}, csv.get(0));
        Assert.assertArrayEquals(new String[]{"col 4", "", "col5", "col6", "col6a"}, csv.get(1));
        Assert.assertArrayEquals(new String[]{"col7", "col8", "' '"}, csv.get(2));

        // test 3: first row has quote value, and 2nd row has unbalanced quote
        fixture = "col1,col2,\" \",col3\n" +
                  "col4,\",col5,col6,col6a\n" +
                  "col7,col8,' '";
        csv = parser.parseAll(new StringReader(fixture));
        Assert.assertNotNull(csv);
        Assert.assertEquals(2, csv.size());
        Assert.assertEquals(4, csv.get(0).length);
        Assert.assertArrayEquals(new String[]{"col1", "col2", "\" \"", "col3"}, csv.get(0));
        Assert.assertArrayEquals("unbalanced quote in 2nd row should cause all subsequent lines to co-join as one",
                                 new String[]{"col4", "\",col5,col6,col6a\ncol7,col8,' '"}, csv.get(1));
    }

    @Test
    public void parseCsv_comma_newline_quote_auto() {
        CsvParser parser = CsvCommand.newCsvParser(null, null, null, false, -1);

        // test 1: last row has fewer columns AND first row has quoted value
        String fixture = "col1,col2,\"col 3\",col3a\n" +
                         "\"col 4\",col5,col6,col6a\n" +
                         "col7,col8,\"\"";
        List<String[]> csv = parser.parseAll(new StringReader(fixture));
        Assert.assertNotNull(csv);
        Assert.assertEquals(3, csv.size());
        Assert.assertEquals(4, csv.get(0).length);
        Assert.assertArrayEquals(new String[]{"col1", "col2", "col 3", "col3a"}, csv.get(0));
        Assert.assertArrayEquals(new String[]{"col 4", "col5", "col6", "col6a"}, csv.get(1));
        Assert.assertArrayEquals(new String[]{"col7", "col8", ""}, csv.get(2));

        // test 2: single quote not recognized
        fixture = "col1,col2,\" \",col3\n" +
                  "col 4,,col5,col6,col6a\n" +
                  "col7,col8,' '";
        csv = parser.parseAll(new StringReader(fixture));
        Assert.assertNotNull(csv);
        Assert.assertEquals(3, csv.size());
        Assert.assertEquals(4, csv.get(0).length);
        Assert.assertArrayEquals(new String[]{"col1", "col2", " ", "col3"}, csv.get(0));
        Assert.assertArrayEquals(new String[]{"col 4", "", "col5", "col6", "col6a"}, csv.get(1));
        Assert.assertArrayEquals(new String[]{"col7", "col8", "' '"}, csv.get(2));

        // test 3: first row has quote value, and 2nd row has unbalanced quote
        fixture = "col1,col2,\" \",col3\n" +
                  "col4,\",col5,col6,col6a\n" +
                  "col7,col8,' '";
        csv = parser.parseAll(new StringReader(fixture));
        Assert.assertNotNull(csv);
        Assert.assertEquals(2, csv.size());
        Assert.assertEquals(4, csv.get(0).length);
        Assert.assertArrayEquals(new String[]{"col1", "col2", " ", "col3"}, csv.get(0));
        Assert.assertArrayEquals("unbalanced quote in 2nd row should cause all subsequent lines to co-join as one",
                                 new String[]{"col4", ",col5,col6,col6a\ncol7,col8,' '"}, csv.get(1));
    }

    @Test
    public void parseAsCSV_strings() {
        CsvCommand command = new CsvCommand();
        command.init(context);

        String fixture = "Year,Make,Model,Description,Price\r\n" +
                         "1997,Ford,E350,\"ac, abs, moon\",3000.00\r\n" +
                         "1999,Chevy,\"Venture \"\"Extended Edition\"\"\",\"\",4900.00";

        // test with header=false
        List<String[]> actualResult = command.parseAsCSV(fixture, false);
        StringBuilder actual = new StringBuilder();
        actualResult.forEach(record -> actual.append(TextUtils.toString(record, ";", "", "")).append("|"));
        Assert.assertEquals("Year;Make;Model;Description;Price|" +
                            "1997;Ford;E350;ac, abs, moon;3000.00|" +
                            "1999;Chevy;Venture \"Extended Edition\";;4900.00",
                            StringUtils.removeEnd(actual.toString(), "|"));

        // test with header=true
        actual.setLength(0);
        actualResult = command.parseAsCSV(fixture, true);
        actualResult.forEach(record -> actual.append(TextUtils.toString(record, ",", "", "")).append(";"));
        Assert.assertEquals("1997,Ford,E350,ac, abs, moon,3000.00;" +
                            "1999,Chevy,Venture \"Extended Edition\",,4900.00",
                            StringUtils.removeEnd(actual.toString(), ";"));

        // test with delim=|, lineSep=\n
        fixture = "Year|Make|Model|Description|Price\n" +
                  "1997|Ford|E350|\"ac, abs, moon\"|3000.00\n" +
                  "1999|Chevy|\"Venture \"\"Extended Edition\"\"\"|\"\"|4900.00\n";
        actual.setLength(0);
        actualResult = command.parseAsCSV(fixture, false);
        actualResult.forEach(record -> actual.append(TextUtils.toString(record, ";", "", "")).append("|"));
        Assert.assertEquals("Year;Make;Model;Description;Price|" +
                            "1997;Ford;E350;ac, abs, moon;3000.00|" +
                            "1999;Chevy;Venture \"Extended Edition\";;4900.00",
                            StringUtils.removeEnd(actual.toString(), "|"));

        fixture = "permalink,company,numEmps,category,city,state,fundedDate,raisedAmt,raisedCurrency,round\n" +
                  "lifelock,LifeLock,,web,Tempe,AZ,1-May-07,6850000,USD,b\n" +
                  "mycityfaces,MyCityFaces,7,web,Scottsdale,AZ,1-Jan-08,50000,USD,seed\n" +
                  "flypaper,Flypaper,,web,Phoenix,AZ,1-Feb-08,3000000,USD,a\n" +
                  "infusionsoft,Infusionsoft,105,software,Gilbert,AZ,1-Oct-07,9000000,USD,a\n" +
                  "gauto,gAuto,4,web,Scottsdale,AZ,1-Jan-08,250000,USD,seed\n" +
                  "chosenlist-com,ChosenList.com,5,web,Scottsdale,AZ,1-Oct-06,140000,USD,seed\n" +
                  "digg,Digg,60,web,San Francisco,CA,1-Dec-06,8500000,USD,b\n" +
                  "mediamachines,Media Machines,,web,San Francisco,CA,1-Aug-07,9400000,USD,a\n" +
                  "mig33,Ciihui,,web,Burlingame,CA,1-May-07,10000000,USD,a\n" +
                  "aliph,Aliph,,web,San Francisco,CA,1-Jul-07,5000000,USD,a\n" +
                  "mego,mEgo,15,web,Los Angeles,CA,1-May-06,1100000,USD,angel\n" +
                  "mego,mEgo,15,web,Los Angeles,CA,1-Jul-07,2000000,USD,angel\n" +
                  "echosign,EchoSign,,web,Palo Alto,CA,1-Oct-07,6000000,USD,a\n" +
                  "rollbase,Rollbase,0,web,Mountain View,CA,1-Aug-07,400000,USD,angel\n" +
                  "predictify,Predictify,6,web,Redwood City,CA,25-Mar-08,4300000,USD,a\n" +
                  "trialpay,TrialPay,50,web,San Francisco,CA,1-Feb-08,12700000,USD,b\n" +
                  "socializr,Socializr,,web,San Francisco,CA,1-Sep-07,1500000,USD,a\n" +
                  "eurekster,Eurekster,0,web,,CA,1-Dec-04,1350000,USD,a\n" +
                  "akimbo,Akimbo,,web,San Mateo,CA,29-Feb-08,8000000,USD,d\n" +
                  "laszlosystems,Laszlo Systems,60,software,San Mateo,CA,6-Mar-08,14600000,USD,c\n" +
                  "brightroll,BrightRoll,,web,San Francisco,CA,1-Oct-07,5000000,USD,b\n" +
                  "merchantcircle,MerchantCircle,10,web,Los Altos,CA,1-Sep-07,10000000,USD,b\n" +
                  "minekey,Minekey,,web,Sunnyvale,CA,1-Aug-07,3000000,USD,a\n" +
                  "bookingangel,Booking Angel,,web,Hollywood,CA,1-Aug-07,100000,USD,angel\n" +
                  "loopt,Loopt,,web,Mountain View,CA,1-Jun-05,6000,USD,seed\n" +
                  "fix8,Fix8,,web,Sherman Oaks,CA,1-Oct-07,3000000,USD,a\n" +
                  "mybuys,MyBuys,,web,Redwood Shores,CA,21-Feb-07,2800000,USD,a\n" +
                  "mybuys,MyBuys,,web,Redwood Shores,CA,8-Oct-07,10000000,USD,b\n";
        actualResult = command.parseAsCSV(fixture, true);
        List<String> actualLines = new ArrayList<>();
        actualResult.forEach(record -> actualLines.add(TextUtils.toString(record, ";", "", "")));
        // check column count
        Assert.assertEquals(10, actualResult.get(0).length);
        // check row count
        Assert.assertEquals(28, actualLines.size());
        // spot check
        Assert.assertTrue(
            actualLines.contains("trialpay;TrialPay;50;web;San Francisco;CA;1-Feb-08;12700000;USD;b"));
        Assert.assertTrue(
            actualLines.contains("merchantcircle;MerchantCircle;10;web;Los Altos;CA;1-Sep-07;10000000;USD;b"));
        Assert.assertTrue(
            actualLines.contains("chosenlist-com;ChosenList.com;5;web;Scottsdale;AZ;1-Oct-06;140000;USD;seed"));
    }

    protected void testCompare(String testCaseId,
                               List<String> expected,
                               List<String> actual,
                               List<List<String>> errors,
                               boolean failfast)
        throws IOException {

        CsvCommand command = new CsvCommand();
        command.init(context);

        File log = new File(LOG_FILE);

        for (int i = 0; i < expected.size(); i++) {
            System.out.println();
            System.out.println();
            System.out.println(StringUtils.repeat("*", 120));
            System.out.println("TEST CASE: " + testCaseId + " #" + (i + 1) + "\n");

            // reset log file
            System.out.println("deleting old log " + log + "\n");
            FileUtils.deleteQuietly(log);

            StepResult result = command.compare(expected.get(i), actual.get(i), failfast + "");
            Assert.assertNotNull(result);

            String logs = FileUtils.readFileToString(log, DEF_FILE_ENCODING);
            System.out.println(logs);
            System.out.println();

            List<String> expectedErrors = errors.get(i);
            // expects failure only if there are error messages to validate
            if (CollectionUtils.isEmpty(expectedErrors)) {
                Assert.assertTrue(result.isSuccess());
                continue;
            }

            Assert.assertTrue(result.failed());

            for (String error : expectedErrors) {
                System.out.print("checking that logs contains '" + error + "'... ");
                Assert.assertTrue(StringUtils.contains(logs, error));
                System.out.println("PASSED!");
            }
        }
    }

    protected String formatSample(String[] testSample) {
        return StringUtils.substringBetween(StringUtils.trim(ArrayUtils.toString(testSample)), "{", "}");
    }

}