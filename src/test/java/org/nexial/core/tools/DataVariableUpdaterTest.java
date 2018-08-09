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

package org.nexial.core.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.tools.DataVariableUpdater.UpdateLog;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.Project.*;

public class DataVariableUpdaterTest {
    private String searchFrom = ResourceUtils.getResourceFilePath("/DataVariableUpdaterTest/");
    private String searchReplace = "nexial.browser=CENTREE.browser;" +
                                   "nexial.browserstack.browser=CENTREE.browserstack.browser;" +
                                   "nexial.failFast=CENTREE.failFast;" +
                                   "nexial.lenientStringCompare=CENTREE.compare.lenient;" +
                                   "nexial.pollWaitMs=CENTREE.pollWaitMs;" +
                                   "nexial.runID.prefix=CENTREE.runID.prefix;" +
                                   "nexial.scope.fallbackToPrevious=CENTREE.iteration.fallbackToPrevious;" +
                                   "nexial.scope.iteration=CENTREE.iteration;" +
                                   "nexial.textDelim=CENTREE.textDelim;" +
                                   "nexial.verbose=CENTREE.verbose;" +
                                   "nexial.web.alwaysWait=CENTREE.web.alwaysWait;" +
                                   "nexial.ws.digest.user=CENTREE.ws.digest.user;" +

                                   "mydata=mifdb;" +
                                   "mydata.treatNullAs=mifdb.treatNullAs;" +
                                   "mydata.type=mifdb.type;" +
                                   "mydata.url=mifdb.url;" +

                                   "all.the.kings.horse=riddle;" +
                                   "broken=break;" +
                                   "couldn't put=rhyme2;" +
                                   "dunn=hunt;" +
                                   "gotten=taken;" +
                                   "kingsman=King's Men;" +
                                   "myData=His Data;" +
                                   "ourData=Her Data;" +
                                   "rotten=gone;" +
                                   "actress=actor;" +
                                   "agent=secret agent;" +
                                   "resultset=My Result;" +
                                   "mySQL=statement-one;" +
                                   "a bunch of stuff=nothing to see;" +
                                   "more stuff=a bit of everything;" +

                                   "jimmy.johnson.*=john.williams.*;" +
                                   "sandy.*=mandy's *;" +
                                   "variable1=result1";

    private DataVariableUpdater updater;

    @Before
    public void setUp() {
        updater = new DataVariableUpdater();
        updater.setSearchFrom(searchFrom);
        updater.setVariableMap(TextUtils.toMap(searchReplace, ";", "="));
    }

    @After
    public void tearDown() {
    }

    @Test
    public void replaceDataFile() throws Exception {
        updater.replaceDataFiles();

        File projectProps = new File(StringUtils.appendIfMissing(searchFrom, separator) + DEF_REL_LOC_TEST_DATA +
                                     "MyTestScript.data.xlsx");

        Excel dataExcel = new Excel(projectProps, false, false);
        Worksheet worksheetDefault = dataExcel.worksheet("#default");
        int lastRow = worksheetDefault.findLastDataRow(new ExcelAddress("A1"));
        List<List<XSSFCell>> cells = worksheetDefault.cells(new ExcelAddress("A1:B" + lastRow));
        Map<String, String> cellData = new LinkedHashMap<>();
        cells.forEach(row -> cellData.put(row.get(0).getStringCellValue(), row.get(1).getStringCellValue()));

        Assert.assertEquals("true", cellData.get("CENTREE.iteration.fallbackToPrevious"));
        Assert.assertEquals("1", cellData.get("CENTREE.iteration"));
        Assert.assertEquals("800", cellData.get("CENTREE.pollWaitMs"));
        Assert.assertEquals("false", cellData.get("CENTREE.failFast"));
        Assert.assertEquals(",", cellData.get("CENTREE.textDelim"));
        Assert.assertEquals("false", cellData.get("CENTREE.verbose"));
        Assert.assertEquals("ethan", cellData.get("hunt"));
        Assert.assertEquals("secret service", cellData.get("King's Men"));
        Assert.assertEquals("justin ${hunt}", cellData.get("actor"));
        Assert.assertEquals(
            "[LIST(${King's Men},${actor},${hunt}) => text replace(\\,,:) store(a bit of everything) lower]",
            cellData.get("nothing to see"));
        Assert.assertEquals("DATA_TWO", cellData.get("data1"));
        Assert.assertEquals("$(format|upper|${a bit of everything})", cellData.get("even more stuff"));

        Assert.assertEquals("on the dock", cellData.get("mandy's beach"));
        Assert.assertEquals("that's a mock", cellData.get("mandy's smith"));
        Assert.assertEquals("not, or so I thought", cellData.get("mandy's wich.which is"));
    }

    @Test
    public void replaceScript() throws Exception {
        updater.replaceScripts();

        File scriptFile = new File(StringUtils.appendIfMissing(searchFrom, separator) + DEF_REL_LOC_TEST_SCRIPT +
                                   "MyTestScript.xlsx");

        Excel scriptExcel = new Excel(scriptFile, false, false);
        Worksheet worksheetDefault = scriptExcel.worksheet("Test Scenario");
        int lastRow = worksheetDefault.findLastDataRow(new ExcelAddress("D5"));
        List<List<XSSFCell>> cells = worksheetDefault.cells(new ExcelAddress("A5:J" + lastRow));
        List<List<String>> cellData = new ArrayList<>();
        cells.forEach(row -> {
            // List<String> rowData = new ArrayList<>();
            // row.forEach(cell -> rowData.add(cell.getStringCellValue()));
            // cellData.add(rowData);
            cellData.add(row.stream().map(Excel::getCellValue).collect(Collectors.toList()));
        });

        Assert.assertEquals("${CENTREE.web.alwaysWait}", cellData.get(0).get(4));
        Assert.assertEquals("${His Data}", cellData.get(1).get(4));
        Assert.assertEquals("All the king's horses ${riddle}", cellData.get(2).get(1));
        Assert.assertEquals(", couldn't put ${rhyme2}… unless, ... ", cellData.get(2).get(4));
        Assert.assertEquals("Your data is ${His Data}, and our data is ${Her Data}.", cellData.get(3).get(4));
        Assert.assertEquals("${break}", cellData.get(4).get(4));
        Assert.assertEquals("${gone}", cellData.get(5).get(5));
        Assert.assertEquals("[TEXT(${CENTREE.ws.digest.user}) => length]", cellData.get(6).get(5));
        Assert.assertEquals("5", cellData.get(6).get(4));
        Assert.assertEquals("[TEXT(${CENTREE.runID.prefix}) => split(-) list]", cellData.get(7).get(5));
        Assert.assertEquals("[TEXT(${hunt} hunt) => \n" +
                            " append(is part of ${King's Men}) \n" +
                            " title-case\n" +
                            "]", cellData.get(8).get(4));
        Assert.assertEquals("secret agent", cellData.get(8).get(6));
        // assert no change
        Assert.assertEquals("${secret agent}", cellData.get(9).get(5));
        Assert.assertEquals("${actress name length}", cellData.get(10).get(5));
        Assert.assertEquals("[TEXT(${actor}) => length]", cellData.get(11).get(4));
        Assert.assertEquals("My Result", cellData.get(12).get(4));
        Assert.assertEquals("mifdb", cellData.get(12).get(5));
        Assert.assertEquals("[TEXT(select * from ${secret agent}) => upper store(statement-one)];",
                            cellData.get(12).get(6));
        Assert.assertEquals("${My Result}.data", cellData.get(13).get(4));
        Assert.assertEquals("[TEXT(statement-one) => title-case replace( ,:)]", cellData.get(14).get(4));
    }

    @Test
    public void replaceProperties() throws Exception {
        updater.replaceProperties();

        String projectProps = StringUtils.appendIfMissing(searchFrom, separator) + DEF_REL_PROJECT_PROPS;
        String projectPropContent = FileUtils.readFileToString(new File(projectProps), DEF_FILE_ENCODING);
        System.out.println("projectPropContent = \n" + projectPropContent);
        String sep = StringUtils.contains(projectPropContent, "\r\n") ? "\r\n" : "\n";

        Assert.assertEquals("CENTREE.compare.lenient=${CENTREE.web.alwaysWait}" + sep +
                            "CENTREE.runID.prefix=MyOneAndOnlyTest-Part2" + sep +
                            "CENTREE.web.alwaysWait=true" + sep +
                            "CENTREE.ws.digest.user=User1" + sep +
                            sep +
                            "CENTREE.browserstack.browser=chrome" + sep +
                            "CENTREE.browser=${CENTREE.browserstack.browser}" + sep +
                            "CENTREE.iteration.fallbackToPrevious=${CENTREE.compare.lenient}" + sep +
                            sep +
                            "mifdb.type=hsqldb" + sep +
                            "mifdb.url=mem:" + sep +
                            "mifdb.treatNullAs=[NULL]" + sep +
                            sep +
                            "His Data=yourData" + sep +
                            "Her Data=Theirs" + sep +
                            "riddle=and king's men" + sep +
                            "" + sep +
                            "" + sep +
                            "rhyme2=Humpty Dumpty together again" + sep +
                            "" + sep +
                            "" + sep +
                            "break=" + sep +
                            "gone=" + sep +
                            "taken=" + sep +
                            "" + sep +
                            "john.williams.color=black" + sep +
                            "john.williams.fruit=apple" + sep +
                            "john.williams.phone=Apple" + sep +
                            sep +
                            sep,
                            projectPropContent);

        Map<String, String> props = TextUtils.loadProperties(projectProps);

        Assert.assertNotNull(props);
        Assert.assertEquals("${CENTREE.web.alwaysWait}", props.get("CENTREE.compare.lenient"));
        Assert.assertEquals("MyOneAndOnlyTest-Part2", props.get("CENTREE.runID.prefix"));
        Assert.assertEquals("true", props.get("CENTREE.web.alwaysWait"));
        Assert.assertEquals("User1", props.get("CENTREE.ws.digest.user"));
        Assert.assertEquals("chrome", props.get("CENTREE.browserstack.browser"));
        Assert.assertEquals("${CENTREE.browserstack.browser}", props.get("CENTREE.browser"));
        Assert.assertEquals("${CENTREE.compare.lenient}", props.get("CENTREE.iteration.fallbackToPrevious"));
        Assert.assertEquals("hsqldb", props.get("mifdb.type"));
        Assert.assertEquals("mem:", props.get("mifdb.url"));
        Assert.assertEquals("[NULL]", props.get("mifdb.treatNullAs"));
        Assert.assertEquals("yourData", props.get("His Data"));
        Assert.assertEquals("Theirs", props.get("Her Data"));
        Assert.assertEquals("and king's men", props.get("riddle"));
        Assert.assertEquals("Humpty Dumpty together again", props.get("rhyme2"));
        Assert.assertEquals("", props.get("break"));
        Assert.assertEquals("", props.get("gone"));
        Assert.assertEquals("", props.get("taken"));
        Assert.assertEquals("black", props.get("john.williams.color"));
        Assert.assertEquals("apple", props.get("john.williams.fruit"));
        Assert.assertEquals("Apple", props.get("john.williams.phone"));
    }

    @Test
    public void replaceTextFiles() throws Exception {
        updater.replaceTextFiles();

        // validate sql file
        String sqlFile = StringUtils.appendIfMissing(searchFrom, separator) +
                         "artifact" + separator + "data" + separator + "test.sql";
        String sqlContent = FileUtils.readFileToString(new File(sqlFile), DEF_FILE_ENCODING);
        String sep = StringUtils.contains(sqlContent, "\r\n") ? "\r\n" : "\n";

        Assert.assertEquals(
            "-- this is a test fixture to validate the search/replace functionality of DataVariableUpdater" + sep +
            "-- here's a comment" + sep +
            "-- here's another" + sep +
            sep +
            "-- nexial:result1" + sep +
            "SELECT * FROM WHATEVER_TABLE WHERE NOBODY_CARE = \"that's right\";" + sep +
            sep +
            "-- nexial:variable2" + sep +
            "UPDATE TABLE1 SET COL1 = 2, COL2 = 3;" + sep +
            sep +
            "-- good bye",
            sqlContent);

        // validate xml file
        String xmlFile = StringUtils.appendIfMissing(searchFrom, separator) +
                         "artifact" + separator + "data" + separator + "test.xml";
        String xmlContent = FileUtils.readFileToString(new File(xmlFile), DEF_FILE_ENCODING);
        sep = StringUtils.contains(xmlContent, "\r\n") ? "\r\n" : "\n";

        Assert.assertEquals(
            "<a>" + sep +
            "\t<b>" + sep +
            "\t\t<c>${CENTREE.browser}</c>" + sep +
            "\t</b>" + sep +
            "\t<d e=\"${CENTREE.failFast}\">${CENTREE.textDelim}</d>" + sep +
            "</a>",
            xmlContent);

        // validate xml file
        String jsonFile = StringUtils.appendIfMissing(searchFrom, separator) +
                          "artifact" + separator + "data" + separator + "test.json";
        String jsonContent = FileUtils.readFileToString(new File(jsonFile), DEF_FILE_ENCODING);
        sep = StringUtils.contains(jsonContent, "\r\n") ? "\r\n" : "\n";

        Assert.assertEquals(
            "{" + sep +
            "    \"a\": {" + sep +
            "        \"b\": \"${CENTREE.failFast}\"," + sep +
            "\"${CENTREE.textDelim}\": [ \"${CENTREE.browser}\", \"${CENTREE.browser}\", \"${CENTREE.verbose}\"]" +
            sep +
            "    }" + sep +
            "}",
            jsonContent);
    }

    @Test
    public void replaceVarsInKeywordWrapper() throws Exception {
        UpdateLog updateLog = updater.new UpdateLog("file1").setPosition("line1");

        // unmatched cases
        Assert.assertNull(updater.replaceVarsInKeywordWrapper(""));
        Assert.assertNull(updater.replaceVarsInKeywordWrapper("No variable here"));
        Assert.assertNull(updater.replaceVarsInKeywordWrapper("CENTREE.browser"));
        Assert.assertNull(updater.replaceVarsInKeywordWrapper("store(CENTREE.browser"));
        Assert.assertNull(updater.replaceVarsInKeywordWrapper("store CENTREE.browser)"));
        Assert.assertNull(updater.replaceVarsInKeywordWrapper("store(CENTREE.browser)"));

        // matched cases
        Assert.assertEquals("store(CENTREE.browser)",
                            updater.replaceVarsInKeywordWrapper("store(nexial.browser)"));

        Assert.assertEquals("I'm gonna store(CENTREE.browser) as ${nexial.browser}",
                            updater
                                .replaceVarsInKeywordWrapper("I'm gonna store(nexial.browser) as ${nexial.browser}"));

        Assert.assertEquals("I'll \n store(CENTREE.browser)\n as ${nexial.browser}",
                            updater
                                .replaceVarsInKeywordWrapper("I'll \n store(nexial.browser)\n as ${nexial.browser}"));

        Assert.assertEquals(
            "I'll \n store(CENTREE.browser)\n as ${nexial.browser}, and then again store(CENTREE.browser)",
            updater.replaceVarsInKeywordWrapper(
                "I'll \n store(nexial.browser)\n as ${nexial.browser}, and then again store(nexial.browser)"));
    }

    @Test
    public void replaceVarTokens() throws Exception {
        UpdateLog updateLog = updater.new UpdateLog("file1").setPosition("line1");

        // unmatched cases
        Assert.assertNull(updater.replaceVarTokens(""));
        Assert.assertNull(updater.replaceVarTokens("No variable here"));
        Assert.assertNull(updater.replaceVarTokens("CENTREE.browser"));
        Assert.assertNull(updater.replaceVarTokens("${CENTREE.browser"));
        Assert.assertNull(updater.replaceVarTokens("$ CENTREE.browser}"));
        Assert.assertNull(updater.replaceVarTokens("${CENTREE.browser}"));

        // matched cases
        Assert.assertEquals("${CENTREE.browser}", updater.replaceVarTokens("${nexial.browser}"));

        Assert.assertEquals("I'm gonna store(nexial.browser) as ${CENTREE.browser}",
                            updater.replaceVarTokens("I'm gonna store(nexial.browser) as ${nexial.browser}"));

        Assert.assertEquals("I'll \n store(nexial.browser)\n as ${CENTREE.browser}",
                            updater.replaceVarTokens("I'll \n store(nexial.browser)\n as ${nexial.browser}"));

        Assert.assertEquals(
            "I'll \n store(nexial.browser)\n as ${CENTREE.browser}, and then use it as ${CENTREE.browser}",
            updater.replaceVarTokens(
                "I'll \n store(nexial.browser)\n as ${nexial.browser}, and then use it as ${nexial.browser}"));
    }
}