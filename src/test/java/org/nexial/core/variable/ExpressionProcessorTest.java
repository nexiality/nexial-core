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

package org.nexial.core.variable;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.NexialTestUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.db.DataAccess;
import org.nexial.core.plugins.db.RdbmsCommand;

import static java.io.File.separator;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.nexial.core.NexialConst.FlowControls.ANY_FIELD;

public class ExpressionProcessorTest {
    private DataAccess da = initDataAccess();
    private ExecutionContext context = new MockExecutionContext() {

        @Override
        public NexialCommand findPlugin(String target) {
            if (StringUtils.equals(target, "rdbms")) {
                RdbmsCommand command = new RdbmsCommand();
                command.setDataAccess(da);
                command.init(this);
                return command;
            } else {
                System.err.println("Currently unsupported plugin in test: " + target);
                return null;
            }
        }
    };
    private String resourcePath;

    @Before
    public void setup() throws Exception {
        resourcePath = "/" + StringUtils.replace(this.getClass().getPackage().getName(), ".", "/") + "/";

        context.setData("testdb.type", "sqlite");
        context.setData("testdb.url", NexialTestUtils.resolveTestDbUrl());
        context.setData("testdb.autocommit", "true");
    }

    @After
    public void tearDown() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void processText() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "[TEXT(hello world) => upper distinct length]";
        String result = subject.process(fixture);
        Assert.assertEquals("8", result);

        fixture = "[TEXT(Johnny Be Goode) => upper]";
        result = subject.process(fixture);
        Assert.assertEquals("JOHNNY BE GOODE", result);

        fixture = "[TEXT(Event Driven Infrastructure) => substring(0, 12) pack]";
        result = subject.process(fixture);
        Assert.assertEquals("EventDriven", result);

        fixture = "[TEXT(If you're happy and you know it clap your hands) => remove(and) replace(yo,ma)]";
        result = subject.process(fixture);
        Assert.assertEquals("If mau're happy  mau know it clap maur hs", result);

        fixture = "[TEXT(everybody wants to rule the world) => before(the world) after(everybody) trim between(t,l) " +
                  "                                            pack]";
        result = subject.process(fixture);
        Assert.assertEquals("storu", result);

        fixture = "So this is an [TEXT(awesome) => upper] way to [TEXT(Chat) => distinct lower] with your friends!";
        result = subject.process(fixture);
        Assert.assertEquals("So this is an AWESOME way to chat with your friends!", result);

        fixture = "I love [TEXT(Jumbo) => replace(o,a) replace(u,a) append( Laya)], [TEXT(Chunga) => prepend(Chimi )]" +
                  " and [TEXT(lodi paratha) => title insert(Lodi,ee)], but I can't spell them!";
        result = subject.process(fixture);
        Assert.assertEquals("I love Jamba Laya, Chimi Chunga and Lodiee Paratha, but I can't spell them!", result);

        fixture = "There are [TEXT(Missisauga) => count(s)] 's's in Missisauga";
        result = subject.process(fixture);
        Assert.assertEquals("There are 3 's's in Missisauga", result);

        context.setData("fruit", "Apple");
        context.setData("more fruits", "Strawberry Banana");
        String replaced = context.replaceTokens("I love [TEXT(${fruit}) => append(\\,) append(${more fruits}) " +
                                                "                          replace( ,\\,) pack list(\\,)]" +
                                                " - and they are [TEXT(good for you) => upper]!");
        Assert.assertEquals("I love Apple,Strawberry,Banana - and they are GOOD FOR YOU!", replaced);
    }

    @Test
    public void processTest_regex() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "[TEXT(showcase/spiderman) => replaceRegex(\\(.+/.+\\),$1.)]";
        String result = subject.process(fixture);
        Assert.assertEquals("showcase/spiderman.", result);

        fixture = "[TEXT(showcase) => replaceRegex(\\(.+/.+\\),$1.) replaceRegex(\\(.*[^/].*\\),$1/) ]";
        result = subject.process(fixture);
        Assert.assertEquals("showcase/", result);
    }

    @Test
    public void processTest_IfMissing() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "[TEXT(spiderman) => appendIfMissing(sion) prependIfMissing(ex-)]";
        String result = subject.process(fixture);
        Assert.assertEquals("ex-spidermansion", result);
    }

    @Test
    public void processList() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "[LIST(1,2,3,4,5,6,7,8,9,10) => sum]";
        String result = subject.process(fixture);
        Assert.assertEquals("55.0", result);

        fixture = "[LIST(1,0,-1254,14,14.0,13.99999) => max]";
        result = subject.process(fixture);
        Assert.assertEquals("14", result);

        fixture = "[LIST(-20, -15.001, -14, -101, -13.99999) => max]";
        result = subject.process(fixture);
        Assert.assertEquals("-13.99999", result);

        fixture = "[LIST(-1.234, -0, 0, 901.24, -13) => min]";
        result = subject.process(fixture);
        Assert.assertEquals("-13", result);

        fixture = "[LIST(1, , 0, , -1) => min]";
        result = subject.process(fixture);
        Assert.assertEquals("-1", result);

        fixture = "[LIST(, , , , ) => average]";
        result = subject.process(fixture);
        Assert.assertEquals("0", result);

        fixture = "[LIST(, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1) => average]";
        result = subject.process(fixture);
        Assert.assertEquals("3.0", result);

        fixture = "[LIST(, 5, 4, 3, 2, 1, seven, nineteen, 5, 4, 3, 2, 1) => average]";
        result = subject.process(fixture);
        Assert.assertEquals("3.0", result);

        fixture = "[LIST(,,adam,ada,,,,,mad,ada,,,) => pack reverse]";
        result = subject.process(fixture);
        Assert.assertEquals("ada,mad,ada,adam", result);

        fixture = "[LIST(every,mountain,high,every,valley,low) => item(2)]";
        result = subject.process(fixture);
        Assert.assertEquals("high", result);

        fixture = "[LIST(every,mountain,high,every,valley,low) => length]";
        result = subject.process(fixture);
        Assert.assertEquals("6", result);

        fixture = "[LIST(every,mountain,high,every,valley,low) => index(every)]";
        result = subject.process(fixture);
        Assert.assertEquals("0", result);

        fixture = "[LIST(every,mountain,high,every,valley,low) => ascending distinct sublist(2,4)]";
        result = subject.process(fixture);
        Assert.assertEquals("low,mountain,valley", result);

        fixture = "[LIST(Tempe,Burbank,San Francisco,Las Vegas,Toronto) => descending remove(0) insert(3,Santa Fe)]";
        result = subject.process(fixture);
        Assert.assertEquals("Tempe,San Francisco,Las Vegas,Santa Fe,Burbank", result);

        fixture = "[LIST(1,5,4,3,7,8,90,55,443,6,5,2,5,11,2,3,1,7,6,6,0,0) => removeItems(1,6,0) replace(3,33)]";
        result = subject.process(fixture);
        Assert.assertEquals("5,4,33,7,8,90,55,4433,5,2,5,11,2,33,7", result);

        fixture = "[LIST(bah,bah,black,sheep) => join(have,you,any,wool) join(yes,sir,yes,sir,three,bags,full) " +
                  "                              combine( ) ]";
        result = subject.process(fixture);
        Assert.assertEquals("bah bah black sheep have you any wool yes sir yes sir three bags full", result);

        fixture = "[LIST(bah,bah,black,sheep) => join(have,you,any,wool) join(yes,sir,yes,sir,three,bags,full) " +
                  "                              combine(\"\")]";
        result = subject.process(fixture);
        Assert.assertEquals("bahbahblacksheephaveyouanywoolyessiryessirthreebagsfull", result);

        fixture = "[LIST(red,yellow,green,blue,black,white) => union(purple,pink,orange,black,white)]";
        result = subject.process(fixture);
        Assert.assertEquals("red,yellow,green,blue,black,white,purple,pink,orange", result);

        fixture = "[LIST(red,yellow,green,blue,black,white) => intersect(purple,pink,orange,black,white)]";
        result = subject.process(fixture);
        Assert.assertEquals("black,white", result);

        fixture = "[LIST(CDwindow-51c12978-15c6-4ea2-ab24-9aadefe370a8,CDwindow-adf7c26d-262e-4a6c-a7a3-1adb5d8406bb)" +
                  " => removeItems(CDwindow-51c12978-15c6-4ea2-ab24-9aadefe370a8) text]";
        result = subject.process(fixture);
        Assert.assertEquals("CDwindow-adf7c26d-262e-4a6c-a7a3-1adb5d8406bb", result);
    }

    @Test
    public void processList_empty() throws Exception {
        context.setData("new array", "START");
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = context.replaceTokens("[LIST(${new array}) => append(item1) " +
                                               "                       append(item2) " +
                                               "                       removeItems(START)]");
        String result = subject.process(fixture);
        Assert.assertEquals("item1,item2", result);
    }

    @Test
    public void processNumber() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "[NUMBER(101) => add(202)]";
        String result = subject.process(fixture);
        Assert.assertEquals("303", result);

        fixture = "[NUMBER(1) => add(2,3,4,5,6,7,8,9, 10)]";
        result = subject.process(fixture);
        Assert.assertEquals("55", result);

        fixture = "[NUMBER(55) => add(-1  ,-2, -3 ,-4,-5,-6,-7,-8,-9, -10)]";
        result = subject.process(fixture);
        Assert.assertEquals("0", result);

        fixture = "[NUMBER(55) => minus(1  ,2, 3 ,4,5,6,7,8,9, 10)]";
        result = subject.process(fixture);
        Assert.assertEquals("0", result);

        fixture = "[NUMBER(0) => multiply(1  ,2, 3 ,4,5,6,7,8,9, 10)]";
        result = subject.process(fixture);
        Assert.assertEquals("0", result);

        fixture = "[NUMBER(1) => multiply(,2, 3 ,4,5,6,7,8,9, 10)]";
        result = subject.process(fixture);
        Assert.assertEquals("3628800", result);

        fixture = "[NUMBER(3628800) => divide(,2, 3 ,4,5,6,7,8,9, 10)]";
        result = subject.process(fixture);
        Assert.assertEquals("1", result);

        fixture = "[NUMBER(5) => divide(3,4.0,11)]";
        result = subject.process(fixture);
        Assert.assertEquals("0.03787878787878788", result);

        fixture = "[NUMBER(0) => add(7,6) minus(2) multiply(11,51.2251) divide(17.04) round]";
        result = subject.process(fixture);
        Assert.assertEquals("364", result);

        fixture = "[NUMBER(173921.22) => roundTo(1)]";
        result = subject.process(fixture);
        Assert.assertEquals("173921", result);

        fixture = "[NUMBER(173921.22) => roundTo(10)]";
        result = subject.process(fixture);
        Assert.assertEquals("173920", result);

        fixture = "[NUMBER(173921.22) => roundTo(100)]";
        result = subject.process(fixture);
        Assert.assertEquals("173900", result);

        fixture = "[NUMBER(173921.22) => roundTo(1000)]";
        result = subject.process(fixture);
        Assert.assertEquals("174000", result);

        fixture = "[NUMBER(173921.22) => roundTo(10000)]";
        result = subject.process(fixture);
        Assert.assertEquals("170000", result);

        fixture = "[NUMBER(173921.22) => roundTo(100000)]";
        result = subject.process(fixture);
        Assert.assertEquals("200000", result);

        fixture = "[NUMBER(1201) => multiply(151,0.952) roundTo(1000)]";
        result = subject.process(fixture);
        Assert.assertEquals("173000", result);

        fixture = "[NUMBER(1201) => multiply(151,0.952) roundTo(100.00)]";
        result = subject.process(fixture);
        Assert.assertEquals("172600", result);

        fixture = "[NUMBER(1201) => multiply(151,0.952) roundTo(10.000)]";
        result = subject.process(fixture);
        Assert.assertEquals("172650", result);

        fixture = "[NUMBER(1201) => multiply(151,0.952) roundTo(0.001)]";
        result = subject.process(fixture);
        Assert.assertEquals("172646.152", result);

        fixture = "[NUMBER(1201) => multiply(151,0.952) roundTo(0.1)]";
        result = subject.process(fixture);
        Assert.assertEquals("172646.2", result);

        fixture = "[NUMBER(0) => randomDigits(5)]";
        result = subject.process(fixture);
        Assert.assertTrue(NumberUtils.isDigits(result));
        Assert.assertEquals(5, result.length());

        fixture = "[NUMBER(0) => randomDigits(0)]";
        result = subject.process(fixture);
        // Assert.assertTrue(NumberUtils.isDigits(result));
        Assert.assertEquals(0, result.length());

        fixture = "[NUMBER(0) => randomDecimal(5,2)]";
        result = subject.process(fixture);
        Assert.assertTrue(NumberUtils.isCreatable(result));
        Assert.assertEquals(8, result.length());

        fixture = "[NUMBER(0) => randomInteger(5,19)]";
        result = subject.process(fixture);
        Assert.assertTrue(NumberUtils.isCreatable(result));
        int resultInt = NumberUtils.toInt(result);
        Assert.assertTrue(resultInt >= 5 && resultInt <= 19);

        fixture = "[NUMBER(96.49341) => ceiling]";
        result = subject.process(fixture);
        Assert.assertEquals("97", result);

        fixture = "[NUMBER(96.49341) => floor]";
        result = subject.process(fixture);
        Assert.assertEquals("96", result);

        fixture = "[NUMBER(96.49341) => roundTo(0.00)]";
        result = subject.process(fixture);
        Assert.assertEquals("96.49", result);

        fixture = "[NUMBER(96.49341) => roundTo(000.00)]";
        result = subject.process(fixture);
        Assert.assertEquals("100", result);
    }

    @Test
    public void processNumber2() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String expected = "-1770.2978788893238";

        // tight argument list
        String fixture = "[NUMBER(92) =>" +
                         " add(44,92,71.23,801.23,-1092)" +
                         " minus(11,44.002)" +
                         " multiply(15.01,0.902)" +
                         " divide(5.0190,0.07092)]";
        String result = subject.process(fixture);
        Assert.assertEquals(expected, result);

        // space much!?
        fixture = "[NUMBER(92) =>" +
                         " add(44,   92,   71.23  ,   801.23,   -1092   )" +
                         " minus(11    ,44.002)" +
                         " multiply(15.01,      0.902)" +
                         " divide( 5.0190 , 0.07092  )]";
        result = subject.process(fixture);
        Assert.assertEquals(expected, result);
    }

    @Test
    public void processDate() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        long rightNow = new Date().getTime();
        String fixture = "[DATE(now) => text]";
        String result = subject.process(fixture);
        Assert.assertNotNull(result);
        Assert.assertNotEquals(result, fixture);

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        long resultTime = df.parse(result).getTime();
        Assert.assertTrue(resultTime - rightNow < 200);

        fixture = "[DATE(2017/10/19,yyyy/MM/dd) => text]";
        result = subject.process(fixture);
        Assert.assertEquals("2017/10/19", result);

        fixture = "[DATE(2017/05/01,yyyy/MM/dd) => addYear(2)]";
        result = subject.process(fixture);
        Assert.assertEquals("2019/05/01", result);

        fixture = "[DATE(2017/05/01,yyyy/MM/dd) => addYear(2) setMonth(8) setDOW(2) text]";
        result = subject.process(fixture);
        Assert.assertEquals("2019/07/29", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(0.00001)]";
        result = subject.process(fixture);
        Assert.assertEquals("180.71654", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(0.01)]";
        result = subject.process(fixture);
        Assert.assertEquals("180.72", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(.1)]";
        result = subject.process(fixture);
        Assert.assertEquals("180.7", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(1.1111111111)]";
        result = subject.process(fixture);
        Assert.assertEquals("180.7165354331", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(1.)]";
        result = subject.process(fixture);
        Assert.assertEquals("181", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(1.0)]";
        result = subject.process(fixture);
        Assert.assertEquals("180.7", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(10.)]";
        result = subject.process(fixture);
        Assert.assertEquals("180", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(100.9999)]";
        result = subject.process(fixture);
        Assert.assertEquals("200", result);

        fixture = "[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                  "  format(h m s) pack " +
                  "  number divide(127.0) roundTo(1000.53)]";
        result = subject.process(fixture);
        Assert.assertEquals("0", result);
    }

    @Test
    public void processJson() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String jsonFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "1.json");
        String fixture = "[JSON(" + jsonFile + ") => text pack]";
        String result = subject.process(fixture);
        Assert.assertTrue(result.contains("\"GlossTerm\":\"StandardGeneralizedMarkupLanguage\""));

        fixture = "[JSON(" + jsonFile + ") => extract(glossary.GlossDiv.GlossList.GlossEntry.GlossDef.GlossSeeAlso)]";
        result = subject.process(fixture);
        Assert.assertEquals("[\"GML\",\"XML\"]", result);

        fixture = "[JSON(" + jsonFile + ") => extract(glossary.GlossDiv.GlossList.GlossEntry.ID)]";
        result = subject.process(fixture);
        Assert.assertEquals("SGML", result);

        fixture = "[JSON(" + jsonFile + ") => extract(glossary.GlossDiv.GlossList.GlossEntry) " +
                  "                           extract(GlossDef.GlossSeeAlso.XML)]";
        result = subject.process(fixture);
        Assert.assertEquals("XML", result);

        jsonFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "2.json");

        result = subject.process("[JSON(" + jsonFile + ") => extract(gamt)]");
        Assert.assertEquals("3415.829994", result);
        String gamt = result;

        result = subject.process("[JSON(" + jsonFile + ") => extract(details.amt1) list sum roundTo(0.000001)]");
        Assert.assertEquals(gamt, result);

        result = subject.process("[JSON(" + jsonFile + ") => extract(details.id) list length]");
        Assert.assertEquals("7", result);

        result = subject.process("[JSON(" + jsonFile + ") => extract(details.code) list distinct ascending first " +
                                 "                           between(\",\")]");
        Assert.assertEquals("A1", result);

        result = subject.process("[JSON(" + jsonFile + ") => extract(details.minutes) list distinct descending last]");
        Assert.assertEquals("420", result);

        result = subject.process("[JSON(" + jsonFile + ") => extract(details[date1=REGEX:2016-05-0\\.+].minutes) " +
                                 "                           list average]");
        Assert.assertEquals("600.0", result);

        fixture = "[JSON(" + jsonFile + ") => replace(details[type1=A2].type1,7th)" +
                  "                           extract(details[type1=7th].type1)]";
        result = subject.process(fixture);
        Assert.assertEquals("7th", result);

        result = subject.process("[JSON(" + jsonFile + ") => extract(error)]");
        Assert.assertNull(result);

        result = subject.process("[JSON(" + jsonFile + ") => pack]");
        Assert.assertFalse(StringUtils.contains(result, "null"));

        String extractSeventh = "extract(details[type1=A2].type1)";
        result = subject.process("[JSON(" + jsonFile + ") => " + extractSeventh + "]");
        Assert.assertEquals("A2", result);

        result = subject.process("[JSON(" + jsonFile + ") => remove(details[type1=A2].type1) " + extractSeventh + "]");
        Assert.assertNull(result);

        // count the json elements
        result = subject.process("[JSON(" + jsonFile + ") => count(details[code=NP]) add(1) multiply(10)]");
        Assert.assertEquals("10", result);

    }

    @Test
    public void processJson_unicode() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String jsonDocument = "{" +
                              "   \"timestamp\":\"2017-09-25T19:23:24.419-07:00\"," +
                              "   \"status\":405," +
                              "   \"error\":\"Method Not Allowed\"," +
                              "   \"exception\":\"org.springframework.web.HttpRequestMethodNotSupportedException\"," +
                              "   \"message\":\"Request method \\u005BPATCH\\u005D not supported\\u003A just not my style\"," +
                              "   \"path\":\"/notification/client/rP58k9yEFflmwGGngGdigkPRxkmzFnlC/deletePhone\"" +
                              "}";
        String result = subject.process("[JSON(" + jsonDocument + ") => extract(message)]");
        Assert.assertEquals("Request method [PATCH] not supported: just not my style", result);
    }

    @Test
    public void processXml() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String xmlFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "3.xml");
        String fixture = "[XML(" + xmlFile + ") => text normalize]";
        String result = subject.process(fixture);
        Assert.assertTrue(result.contains("<description>Microsoft's .NET initiative is explored in detail in this " +
                                          "deep programmer's reference. </description>"));

        fixture = "Hey you know, [XML(" + xmlFile + ") => " +
                  "extract(//book[./price=5.95 and starts-with\\(./publish_date\\,'2001-09'\\)]/title/text\\(\\))]" +
                  " is a great book for the year at a great price of $5.95!";
        result = subject.process(fixture);
        Assert.assertEquals("Hey you know, The Sundered Grail is a great book for the year at a great price of $5.95!",
                            result);

        fixture = "[XML(" + xmlFile + ") => count(//catalog/book)]";
        result = subject.process(fixture);
        Assert.assertEquals("12", result);
    }

    @Test
    public void processXml_with_prolog() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String xmlFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "4.xml");
        String fixture = "[XML(" + xmlFile + ") => text normalize]";
        String result = subject.process(fixture);
        Assert.assertTrue(result.contains("<double xmlns=\"http://www.webserviceX.NET/\">212</double>"));

        fixture = "[XML(" + xmlFile + ") => extract(/*[local-name\\(\\)='double']/text\\(\\))]";
        result = subject.process(fixture);
        Assert.assertEquals("212", result);
    }

    @Test
    public void processExpression() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "this is [TEXT(the best opportunity (or chance maybe...)) => " +
                         "upper remove(\\)) replace( \\(, ) remove(...)], blah...";
        String result = subject.process(fixture);
        Assert.assertEquals("this is THE BEST OPPORTUNITY OR CHANCE MAYBE, blah...", result);

        fixture = "this is [TEXT(.)....) => replace([, ) replace(], )] blah...";
        result = subject.process(fixture);
        Assert.assertEquals("this is .).... blah...", result);

        fixture = "this is [XML(<a>b</a>) => extract(//a)], blah...";
        result = subject.process(fixture);
        System.out.println("result = " + result);

        // result = subject.process("this is [XML(<...>) => extract(//a[b(c)='d'])] " +
        //                          "and [TEXT(..)...) => replace([, ) replace(], )] blah...");
        // System.out.println(result);
    }

    @Test
    public void processConfig() throws Exception {
        String className = this.getClass().getSimpleName();
        String propertiesFile = ResourceUtils.getResourceFilePath(resourcePath + className + "4.txt");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "[CONFIG(" + propertiesFile + ") => value(FirstName)]";
        String result = subject.process(fixture);
        Assert.assertEquals("John", result);

        fixture = "[CONFIG(" + propertiesFile + ") => keys length]";
        result = subject.process(fixture);
        Assert.assertEquals("2", result);

        fixture = "[CONFIG(" + propertiesFile + ") => " +
                  " set(State,California)] " +
                  " save(" + propertiesFile + ") " +
                  " set(State,New York)] " +
                  " value(State)" +
                  "]";
        result = subject.process(fixture);
        Assert.assertEquals("New York", result);

        fixture = "[CONFIG(Title=Engineer) => value(Title) length]";
        result = subject.process(fixture);
        Assert.assertEquals("8", result);

        fixture = "[CONFIG(" + propertiesFile + ") => remove(State) save(" + propertiesFile + ") value(State)]";
        result = subject.process(fixture);
        Assert.assertNull(result);

        String prop2 = ResourceUtils.getResourceFilePath(resourcePath + className + "4.properties");

        result = subject.process("[CONFIG(" + prop2 + ") => ascending()]");
        Assert.assertNotNull(result);
        Assert.assertEquals("AL=128191\n" +
                            "CA=199182\n" +
                            "CO=230190\n" +
                            "CT=99320\n" +
                            "FL=352388\n" +
                            "NY=230283\n" +
                            "RI=2389811\n",
                            result);

        result = subject.process("[CONFIG(" + prop2 + ") => remove(CT) remove(RI) descending()]");
        Assert.assertNotNull(result);
        Assert.assertEquals("NY=230283\n" +
                            "FL=352388\n" +
                            "CO=230190\n" +
                            "CA=199182\n" +
                            "AL=128191\n",
                            result);
    }

    @Test
    public void processINI() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);
        String iniFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "5.ini");

        String fixture = "[INI(" + iniFile + ") =>" +
                         " set(COMPANY_NAME,SERVER_NAME,server94a)" +
                         " save(" + iniFile + ")" +
                         " value(COMPANY_NAME,SERVER_NAME)" +
                         "]";
        Assert.assertEquals("server94a", subject.process(fixture));

        Assert.assertEquals("mapi32.dll", subject.process("[INI(" + iniFile + ") => value(Mail,CMCDLLNAME32)]"));

        Assert.assertNotNull(subject.process("[INI(" + iniFile + ") => text]"));

        System.out.println(subject.process("[INI(" + iniFile + ") => values(COMPANY_NAME)]"));
        Assert.assertEquals("7", subject.process("[INI(" + iniFile + ") => values(COMPANY_NAME) length]"));

        fixture = "[INI(" + iniFile + ") => remove(PRODUCT_2345,*) save(" + iniFile + ")]";
        Assert.assertTrue(!subject.process(fixture).contains("PRODUCT_2345"));

        String newFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "6.ini");
        fixture = "[INI(" + iniFile + ") => merge(" + newFile + ") save(" + iniFile + ")]";
        Assert.assertTrue(subject.process(fixture).contains("PRODUCT_2345"));

        fixture = "[INI(" + iniFile + ") => merge([COMPANY_NAME]\n TEST=1234) save(" + iniFile + ")]";
        Assert.assertTrue(subject.process(fixture).contains("TEST"));

        fixture = "[INI(" + iniFile + ") => remove(PRODUCT_2345,*) save(" + iniFile + ")]";
        Assert.assertTrue(!subject.process(fixture).contains("PRODUCT_2345="));

        fixture = "[INI(" + iniFile + ") => remove(COMPANY_NAME,NAME)" +
                  "                         save(" + iniFile + ")" +
                  "                         value(COMPANY_NAME,PROJECT)]";
        Assert.assertEquals("PROJECT_1234", subject.process(fixture));

        fixture = "[INI(" + iniFile + ") => remove(COMPANY_NAME,TEST) save(" + iniFile + ")]";
        Assert.assertNotNull(subject.process(fixture));

        fixture = "[INI(" + iniFile + ") => remove(COMPANY_NAME,PROJECT) save(" + iniFile + ")]";
        Assert.assertNotNull(subject.process(fixture));

        fixture = "[INI(" + iniFile + ") => newComment(Adding new comment here) save (" + iniFile + ") comment]";
        Assert.assertTrue(subject.process(fixture).contains("Adding new comment here"));

        Assert.assertEquals("1234", subject.process("[INI([COMPANY_NAME]\n TEST=1234) => value(COMPANY_NAME,TEST)]"));
        Assert.assertEquals("1234", subject.process("[INI([NEW]\n TEST=1234) => value(NEW,TEST)]"));
    }

    @Test
    public void processCSV() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "7.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "[CSV(" + csvFile + ") => text]";
        String result = subject.process(fixture);
        Assert.assertNotNull(result);
        Assert.assertEquals(StringUtils.replace(FileUtils.readFileToString(new File(csvFile), "UTF-8"), "\r\n", "\n"),
                            StringUtils.replace(result, "\r\n", "\n"));

        fixture = "[CSV(" + csvFile + ") => parse(delim=\\,|header=true) text]";
        result = subject.process(fixture);
        Assert.assertNotNull(result);
        Assert.assertEquals(StringUtils.replace(FileUtils.readFileToString(new File(csvFile), "UTF-8"), "\r\n", "\n"),
                            StringUtils.replace(result, "\r\n", "\n"));

        fixture = "[CSV(" + csvFile + ") => " +
                  " parse(delim=\\,|header=true)" +
                  " row(2)" +
                  " text]";
        result = subject.process(fixture);
        Assert.assertNotNull(result);
        Assert.assertEquals("david@contoso.com,David,Longmuir,David Longmuir,IT Manager,Information Technology," +
                            "123453,123-555-1213,123-555-6643,123-555-9823,1 Microsoft way,Redmond,Wa,98052," +
                            "United States",
                            result);

        fixture = "[CSV(" + csvFile + ") => " +
                  " parse(delim=\\,|header=true)" +
                  " filter(Job Title = IT Manager)" +
                  " rowCount]";
        result = subject.process(fixture);
        Assert.assertNotNull(result);
        Assert.assertEquals("5", result);

        fixture = "[CSV(" + csvFile + ") => " +
                  " parse(delim=\\,|header=true)" +
                  " filter(Job Title = IT Manager)" +
                  " parse(header=false)" +
                  " rowCount" +
                  "]";
        result = subject.process(fixture);
        Assert.assertNotNull(result);
        Assert.assertEquals("6", result);

        fixture = "[CSV(" + csvFile + ") => " +
                  " parse(delim=\\,|header=true)" +
                  " filter(Job Title = IT Manager)" +
                  " removeColumns(Country or Region|Office Phone|Mobile Phone|Fax)" +
                  " filter(Last Name match \\w{2\\,5}) " +
                  " rowCount ]";
        result = subject.process(fixture);
        Assert.assertNotNull(result);
        Assert.assertEquals("2", result);
    }

    @Test
    public void processCSV2() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "8.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " removeRows(Department = Information Technology|Office Phone end with 1192)" +
                                   " filter(Department = Information Technology)" +
                                   " removeColumns(Job Title|Department|Office Number|Office Phone|Mobile Phone|Fax)" +
                                   " removeColumns(Address|City|State or Province|ZIP or Postal Code|Country or Region)" +
                                   " row(0)" +
                                   " text]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("ben@contoso.com,Ben,Andrews,Ben Andrews"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " removeRows(Department = Information Technology|Office Phone end with 1192)" +
                                   " removeColumns(Job Title|Department|Office Number|Office Phone|Mobile Phone|Fax)" +
                                   " removeColumns(Address|City|State or Province|ZIP or Postal Code|Country or Region)" +
                                   " json" +
                                   " extract([User Name=david@contoso.com].First Name)" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("David"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " removeRows(Department = Information Technology|Office Phone end with 1192)" +
                                   " removeColumns(First Name|Last Name|Office Number|Office Phone|Mobile Phone|Fax)" +
                                   " removeColumns(Address|City|State or Province|ZIP or Postal Code|Country or Region)" +
                                   " renameColumn(User Name, user)" +
                                   " renameColumn(Display Name, name)" +
                                   " renameColumn(Job Title, position)" +
                                   " renameColumn(Department, group)" +
                                   " json" +
                                   " extract([name=REGEX:(.*e.*\\){2\\,}].group)" +
                                   " list" +
                                   " descending " +
                                   " combine(\\,)" +
                                   " remove(\")" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("Information Technology,Human Resource,Finance"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " columnCount" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("15"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " headers" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("User Name,First Name,Last Name,Display Name,Job Title,Department,Office Number," +
                                    "Office Phone,Mobile Phone,Fax,Address,City,State or Province," +
                                    "ZIP or Postal Code,Country or Region"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " renameColumn(State or Province, state)" +
                                   " renameColumn(Display Name, name)" +
                                   " removeColumns(First Name|Last Name)" +
                                   " renameColumn(Country or Region, country)" +
                                   " renameColumn(User Name, user)" +
                                   " headers" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("user,name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax," +
                                    "Address,City,state,ZIP or Postal Code,country"))));

        String tmp = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator) +
                     "junk2.csv";
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\r\n|header=true)" +
                                   " removeColumns(First Name)" +
                                   " save(" + tmp + ")" +
                                   " headers" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("User Name,Last Name,Display Name,Job Title,Department,Office Number," +
                                    "Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code," +
                                    "Country or Region"))));

        System.out.println("tmp = " + tmp);
        String junk2Content = FileUtils.readFileToString(new File(tmp), "UTF-8");
        Assert.assertNotNull(junk2Content);
        Assert.assertEquals(
            "User Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\r\n" +
            "chris@contoso.com,Green,Chris Green,Manager,Finance,234232,312-490-4891,123-555-6641,123-555-9821,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
            "ben@contoso.com,Andrews,Ben Andrews,Director,Information Technology,362342,312-492-9910,123-555-6642,123-555-9822,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
            "david@contoso.com,Longmuir,David Longmuir,Vice President,Accounting,6795647,312-490-5565,312-490-5125,123-555-9823,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
            "cynthia@contoso.com,Carey,Cynthia Carey,Senior Director,Information Technology,112324,312-490-1192,123-555-6644,123-555-9824,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
            "melissa@contoso.com,MacBeth,Melissa MacBeth,Supervisor,Human Resource,345345,312-490-3892,123-555-6645,123-555-9825,1 Microsoft way,Redmond,WA,98052,United States",
            junk2Content);
    }

    @Test
    public void processCSV3() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "8.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " removeRows(Department = Information Technology|Office Phone end with 1192)" +
                                   " removeColumns(Job Title|Department|Office Number|Office Phone|Mobile Phone|Fax)" +
                                   " removeColumns(Address|City|State or Province|ZIP or Postal Code|Country or Region)" +
                                   " parse(delim=\\,|header=false)" +
                                   " json" +
                                   " pack" +
                                   " count([melissa@contoso.com])" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("1"))));
    }

    @Test
    public void processCSV4() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "8.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " removeRows(Department = Information Technology|Office Phone end with 1192)" +
                                   " removeColumns(Office Number|Office Phone|Mobile Phone|Fax)" +
                                   " xml(team,members,member)" +
                                   " extract( //member[@name = 'First Name' and text(\\) = 'Melissa']" +
                                   "/../member[@name = 'Last Name']/text(\\) )" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("MacBeth"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " removeRows(Department = Information Technology|Office Phone end with 1192)" +
                                   " removeColumns(Office Number|Office Phone|Mobile Phone|Fax|Country or Region)" +
                                   " removeColumns(ZIP or Postal Code|Display Name)" +
                                   " xml(team,members,member)" +
                                   " extract( //member[@name = 'First Name' and text(\\) = 'Melissa']" +
                                   "/../member[@name = 'Last Name']/text(\\) )" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("MacBeth"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " removeRows(Department = Information Technology|Office Phone end with 1192)" +
                                   " removeColumns(Office Number|Office Phone|Mobile Phone|Fax|Country or Region)" +
                                   " removeColumns(ZIP or Postal Code|Display Name)" +
                                   " parse(delim=\\,|header=false)" +
                                   " xml( , , )" +
                                   " extract( count(//cell[@index='4']\\) )" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("5.0")))
                  );
    }

    @Test
    public void processCSV5() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "8.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " transpose" +
                                   " parse(header=false)" +
                                   " row(3)" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("Chris Green,Ben Andrews,David Longmuir,Cynthia Carey,Melissa MacBeth"))));

        String tmp = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator) +
                     "junk.csv";
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=false)" +
                                   " transpose" +
                                   " save(" + tmp + ")" +
                                   " rowCount" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo("15"))));

        File tmpFile = new File(tmp);
        Assert.assertNotNull(tmpFile);
        Assert.assertTrue(tmpFile.length() > 1010);

        // transpose back
        String retranposed = subject.process("[CSV(" + tmp + ") => " +
                                             " parse(delim=\\,|header=false) " +
                                             " transpose " +
                                             " text" +
                                             "]");
        Assert.assertEquals(StringUtils.replace(FileUtils.readFileToString(new File(csvFile), "UTF-8"), "\r\n", "\n"),
                            retranposed);

        String output = subject.process("[CSV(" + csvFile + ") => " +
                                        " parse(delim=\\,|header=true)" +
                                        " ascii-table]");
        System.out.println("output = \n" + output);
    }

    @Test
    public void processCSV_with_blank_records() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "11.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\n|header=true)" +
                                   " rowCount" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo("7"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\n|header=true)" +
                                   " columnCount" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo("15"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\n|header=true)" +
                                   " pack" +
                                   " store(csv1)" +
                                   " rowCount" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo("5"))));

        assertThat(subject.process("[CSV(csv1) => " +
                                   " transpose" +
                                   " parse(header=false)" +
                                   " rowCount" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo("15"))));
    }

    @Test
    public void processCSV_with_quotes() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "12.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " row(2) item(2)" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("70 Bowman St. , South Windsor CT 06074 USA"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true|quote=\")" +
                                   " row(0) length" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("3"))));
    }

    @Test
    public void processCSV_renameColumns() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + "ExpressionProcessorTest16.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String expected = "number1,numbers4,name";
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " removeColumns(gross)" +
                                   " headers" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));
    }

    @Test
    public void processCSV_sorts() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + "ExpressionProcessorTest16.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String expected = "number1,numbers4,name,gross\n" +
                          "623132658,20130408,ANDERSON/CARTER,3222.50\n" +
                          "623132658,20130401,ANDERSON/CARTER,3402.50\n" +
                          "623132658,20130415,ANDERSON/CARTER,3665.00\n" +
                          "623132658,20130527,ANDERSON/CARTER,3957.50\n" +
                          "623132658,20130513,ANDERSON/CARTER,4377.50\n" +
                          "623132658,20130429,ANDERSON/CARTER,4475.00\n" +
                          "623132658,20130506,ANDERSON/CARTER,4665.00\n" +
                          "623132658,20130520,ANDERSON/CARTER,4745.00\n" +
                          "623132658,20130318,ANDERSON/CARTER,5270.00\n" +
                          "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
                          "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
                          "623132658,20130603,ANDERSON/CARTER,5675.00\n" +
                          "623132658,20140692,\"ANDERSON, CARTER\",5676.00";
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\n|header=true)" +
                                   " sortAscending(gross)" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));

        expected = "number1,numbers4,name,gross\n" +
                   "623132658,20140692,\"ANDERSON, CARTER\",5676.00\n" +
                   "623132658,20130603,ANDERSON/CARTER,5675.00\n" +
                   "623132658,20130527,ANDERSON/CARTER,3957.50\n" +
                   "623132658,20130520,ANDERSON/CARTER,4745.00\n" +
                   "623132658,20130513,ANDERSON/CARTER,4377.50\n" +
                   "623132658,20130506,ANDERSON/CARTER,4665.00\n" +
                   "623132658,20130429,ANDERSON/CARTER,4475.00\n" +
                   "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
                   "623132658,20130415,ANDERSON/CARTER,3665.00\n" +
                   "623132658,20130408,ANDERSON/CARTER,3222.50\n" +
                   "623132658,20130401,ANDERSON/CARTER,3402.50\n" +
                   "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
                   "623132658,20130318,ANDERSON/CARTER,5270.00";
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\n|header=true)" +
                                   " sortDescending(numbers4)" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));
    }

    @Test
    public void processCSV_removeMatchingLines() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "15.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\r\n|header=true)" +
                                   " removeRows(" + ANY_FIELD + " contain Technology)" +
                                   " pack" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(
                             "User Name,First Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\r\n" +
                             "chris@contoso.com,Chris,Green,Chris Green,Manager,Finance,234232,312-490-4891,123-555-6641,123-555-9821,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "david@contoso.com,David,Longmuir,David Longmuir,Vice President,Accounting,6795647,312-490-5565,312-490-5125,123-555-9823,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "melissa@contoso.com,Melissa,MacBeth,Melissa MacBeth,Supervisor,Human Resource,345345,312-490-3892,123-555-6645,123-555-9825,1 Microsoft way,Redmond,WA,98052,United States"))));

        subject = new ExpressionProcessor(context);
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\r\n|header=true)" +
                                   " remove-rows(" + ANY_FIELD + " contain Director | " + ANY_FIELD + " contain -664)" +
                                   " pack" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(
                             "User Name,First Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\r\n" +
                             "chris@contoso.com,Chris,Green,Chris Green,Manager,Finance,234232,312-490-4891,123-555-6641,123-555-9821,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "david@contoso.com,David,Longmuir,David Longmuir,Vice President,Accounting,6795647,312-490-5565,312-490-5125,123-555-9823,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "melissa@contoso.com,Melissa,MacBeth,Melissa MacBeth,Supervisor,Human Resource,345345,312-490-3892,123-555-6645,123-555-9825,1 Microsoft way,Redmond,WA,98052,United States"))));

        subject = new ExpressionProcessor(context);
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|header=true)" +
                                   " remove-rows( " + ANY_FIELD + " contain -555- )" +
                                   " pack" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(
                             "User Name,First Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region"))));

    }

    @Test
    public void processCSV_filterMatchingLines() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "15.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\r\n|header=true)" +
                                   " filter(" + ANY_FIELD + " contain Technology)" +
                                   " pack" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(
                             "User Name,First Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\r\n" +
                             "ben@contoso.com,Ben,Andrews,Ben Andrews,Director,Information Technology,362342,312-492-9910,123-555-6642,123-555-9822,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "cynthia@contoso.com,Cynthia,Carey,Cynthia Carey,Senior Director,Information Technology,112324,312-490-1192,123-555-6644,123-555-9824,1 Microsoft way,Redmond,WA,98052,United States"))));

        subject = new ExpressionProcessor(context);
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\r\n|header=true)" +
                                   " filter(" + ANY_FIELD + " contain Director | " + ANY_FIELD + " contain -664)" +
                                   " pack" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(
                             "User Name,First Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\r\n" +
                             "ben@contoso.com,Ben,Andrews,Ben Andrews,Director,Information Technology,362342,312-492-9910,123-555-6642,123-555-9822,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "cynthia@contoso.com,Cynthia,Carey,Cynthia Carey,Senior Director,Information Technology,112324,312-490-1192,123-555-6644,123-555-9824,1 Microsoft way,Redmond,WA,98052,United States"))));

        subject = new ExpressionProcessor(context);
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(delim=\\,|recordDelim=\r\n|header=true)" +
                                   " filter( " + ANY_FIELD + " contain -555- )" +
                                   " pack" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(
                             "User Name,First Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\r\n" +
                             "chris@contoso.com,Chris,Green,Chris Green,Manager,Finance,234232,312-490-4891,123-555-6641,123-555-9821,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "ben@contoso.com,Ben,Andrews,Ben Andrews,Director,Information Technology,362342,312-492-9910,123-555-6642,123-555-9822,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "david@contoso.com,David,Longmuir,David Longmuir,Vice President,Accounting,6795647,312-490-5565,312-490-5125,123-555-9823,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "cynthia@contoso.com,Cynthia,Carey,Cynthia Carey,Senior Director,Information Technology,112324,312-490-1192,123-555-6644,123-555-9824,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
                             "melissa@contoso.com,Melissa,MacBeth,Melissa MacBeth,Supervisor,Human Resource,345345,312-490-3892,123-555-6645,123-555-9825,1 Microsoft way,Redmond,WA,98052,United States"))));

    }

    @Test
    public void processCSV_merge_simple() throws Exception {
        context.setData("csv1", "SSN,First Name\n" +
                                "123456789,Jim\n" +
                                "234567890,John\n" +
                                "345678901,James\n" +
                                "456789012,Joe\n" +
                                "567890123,Jacob\n");
        context.setData("csv2", "SSN,Last Name\n" +
                                "345678901,Taylor\n" +
                                "234567890,Scott\n" +
                                "123456789,Hanson\n" +
                                "456789012,Smoe\n" +
                                "567890123,Ladder");

        ExpressionProcessor subject = new ExpressionProcessor(context);
        Assert.assertEquals("SSN,Last Name\n" +
                            "345678901,Taylor\n" +
                            "234567890,Scott\n" +
                            "123456789,Hanson\n" +
                            "456789012,Smoe\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name,Last Name\n" +
                            "123456789,Jim,Hanson\n" +
                            "234567890,John,Scott\n" +
                            "345678901,James,Taylor\n" +
                            "456789012,Joe,Smoe\n" +
                            "567890123,Jacob,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,SSN)]"));
    }

    @Test
    public void processCSV_merge_missing_in_target() throws Exception {
        // case 1: missing record found in `from`
        context.setData("csv1", "SSN,First Name\n" +
                                "123456789,Jim\n" +
                                "234567890,John\n" +
                                "345678901,James\n" +
                                "456789012,Joe\n" +
                                "567890123,Jacob\n");
        context.setData("csv2", "SSN,Last Name\n" +
                                "345678901,Taylor\n" +
                                "234567890,Scott\n" +
                                "123456789,Hanson\n" +
                                "456789012,Smoe\n" +
                                "123456792,Jaime\n" +
                                "567890123,Ladder");
        ExpressionProcessor subject = new ExpressionProcessor(context);
        Assert.assertEquals("SSN,Last Name\n" +
                            "345678901,Taylor\n" +
                            "234567890,Scott\n" +
                            "123456789,Hanson\n" +
                            "456789012,Smoe\n" +
                            "123456792,Jaime\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name,Last Name\n" +
                            "123456789,Jim,Hanson\n" +
                            "123456792,,Jaime\n" +
                            "234567890,John,Scott\n" +
                            "345678901,James,Taylor\n" +
                            "456789012,Joe,Smoe\n" +
                            "567890123,Jacob,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,SSN)]"));

        // case 2: `to` has no data
        context.setData("csv1", "SSN,First Name\n");
        context.setData("csv2", "SSN,Last Name\n" +
                                "345678901,Taylor\n" +
                                "123456792,Jaime\n" +
                                "567890123,Ladder");
        subject = new ExpressionProcessor(context);
        Assert.assertEquals("SSN,Last Name\n" +
                            "345678901,Taylor\n" +
                            "123456792,Jaime\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name,Last Name\n" +
                            "123456792,,Jaime\n" +
                            "345678901,,Taylor\n" +
                            "567890123,,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,SSN)]"));

        // case 3: `to` and `from` has no shared ref
        context.setData("csv1", "SSN,First Name\n" +
                                "098765432,Tim\n" +
                                "987654321,Tommy\n" +
                                "876543210,Thomas\n" +
                                "765432109,Tren\n" +
                                "654321098,Toby\n\n\n");
        context.setData("csv2", "SSN,Last Name\n" +
                                "345678901,Taylor\n" +
                                "675849302,Jaime\n" +
                                "123456792,Jaime\n" +
                                "567890123,Ladder");
        subject = new ExpressionProcessor(context);
        Assert.assertEquals("SSN,Last Name\n" +
                            "345678901,Taylor\n" +
                            "675849302,Jaime\n" +
                            "123456792,Jaime\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name,Last Name\n" +
                            "098765432,Tim\n" +
                            "123456792,,Jaime\n" +
                            "345678901,,Taylor\n" +
                            "567890123,,Ladder\n" +
                            "654321098,Toby\n" +
                            "675849302,,Jaime\n" +
                            "765432109,Tren\n" +
                            "876543210,Thomas\n" +
                            "987654321,Tommy",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,SSN)]"));
    }

    @Test
    public void processCSV_merge_missing_in_source() throws Exception {
        // case 1: missing record found in `to`
        context.setData("csv1", "SSN,First Name\n" +
                                "123456789,Jim\n" +
                                "234567890,John\n" +
                                "345678901,James\n" +
                                "456789012,Joe\n" +
                                "567890123,Jacob\n");
        context.setData("csv2", "SSN,Last Name\n" +
                                "345678901,Taylor\n" +
                                "123456789,Hanson\n" +
                                "456789012,Smoe\n" +
                                "123456792,Jaime\n" +
                                "567890123,Ladder");
        ExpressionProcessor subject = new ExpressionProcessor(context);
        Assert.assertEquals("SSN,Last Name\n" +
                            "345678901,Taylor\n" +
                            "123456789,Hanson\n" +
                            "456789012,Smoe\n" +
                            "123456792,Jaime\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name,Last Name\n" +
                            "123456789,Jim,Hanson\n" +
                            "123456792,,Jaime\n" +
                            "234567890,John\n" +
                            "345678901,James,Taylor\n" +
                            "456789012,Joe,Smoe\n" +
                            "567890123,Jacob,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,SSN)]"));

        // case 2: empty `to`
        context.setData("csv1", "SSN,First Name\n" +
                                "123456789,Jim\n" +
                                "234567890,John\n" +
                                "345678901,James\n" +
                                "456789012,Joe\n" +
                                "567890123,Jacob\n");
        context.setData("csv2", "SSN,Last Name\n");
        subject = new ExpressionProcessor(context);
        Assert.assertEquals("SSN,Last Name",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name\n" +
                            "123456789,Jim\n" +
                            "234567890,John\n" +
                            "345678901,James\n" +
                            "456789012,Joe\n" +
                            "567890123,Jacob",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,SSN)]"));

        // case 3: completely mismatched `from` and `to`
        context.setData("csv1", "SSN,First Name\n" +
                                "123456789,Jim\n" +
                                "234567890,John\n" +
                                "345678901,James\n" +
                                "456789012,Joe\n" +
                                "567890123,Jacob\n");
        context.setData("csv2", "SSN,Last Name\n" +
                                "098765432,Wallace\n" +
                                "987654321,Willet\n" +
                                "876543210,Wilma\n" +
                                "765432109,Wharton\n\n");
        subject = new ExpressionProcessor(context);
        Assert.assertEquals("SSN,Last Name\n" +
                            "098765432,Wallace\n" +
                            "987654321,Willet\n" +
                            "876543210,Wilma\n" +
                            "765432109,Wharton\n",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name,Last Name\n" +
                            "098765432,,Wallace\n" +
                            "123456789,Jim\n" +
                            "234567890,John\n" +
                            "345678901,James\n" +
                            "456789012,Joe\n" +
                            "567890123,Jacob\n" +
                            "765432109,,Wharton\n" +
                            "876543210,,Wilma\n" +
                            "987654321,,Willet",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,SSN)]"));
    }

    @Test
    public void processCSV_merge_simple_no_header() throws Exception {
        context.setData("csv1", "123456789,Jim\n" +
                                "234567890,John\n" +
                                "345678901,James\n" +
                                "456789012,Joe\n" +
                                "567890123,Jacob\n");
        context.setData("csv2", "345678901,Taylor\n" +
                                "234567890,Scott\n" +
                                "123456789,Hanson\n" +
                                "456789012,Smoe\n" +
                                "567890123,Ladder");
        ExpressionProcessor subject = new ExpressionProcessor(context);
        Assert.assertEquals("345678901,Taylor\n" +
                            "234567890,Scott\n" +
                            "123456789,Hanson\n" +
                            "456789012,Smoe\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=false) store(csv2)]"));
        Assert.assertEquals("123456789,Jim,345678901,Taylor\n" +
                            "234567890,John,234567890,Scott\n" +
                            "345678901,James,123456789,Hanson\n" +
                            "456789012,Joe,456789012,Smoe\n" +
                            "567890123,Jacob,567890123,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=false) merge(csv2,\\(empty\\))]"));

        context.setData("csv1", "123456789,Jim,37\n" +
                                "234567890,John,32\n" +
                                "345678901,James,45\n" +
                                "456789012,Joe,19\n" +
                                "567890123,Jacob,22\n");
        context.setData("csv2", "345678901,Taylor,Yellow\n" +
                                "234567890,Scott,Red\n" +
                                "123456789,Hanson,Green\n" +
                                "456789012,Smoe,Blue\n" +
                                "567890123,Ladder");
        subject = new ExpressionProcessor(context);
        Assert.assertEquals("345678901,Taylor,Yellow\n" +
                            "234567890,Scott,Red\n" +
                            "123456789,Hanson,Green\n" +
                            "456789012,Smoe,Blue\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=false) store(csv2)]"));
        Assert.assertEquals("123456789,Jim,37,345678901,Taylor,Yellow\n" +
                            "234567890,John,32,234567890,Scott,Red\n" +
                            "345678901,James,45,123456789,Hanson,Green\n" +
                            "456789012,Joe,19,456789012,Smoe,Blue\n" +
                            "567890123,Jacob,22,567890123,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=false) merge(csv2,\\(empty\\))]"));
    }

    @Test
    public void processCSV_merge_simple_no_ref_column() throws Exception {
        context.setData("csv1", "SSN,First Name\n" +
                                "123456789,Jim\n" +
                                "234567890,John\n" +
                                "345678901,James\n" +
                                "456789012,Joe\n" +
                                "567890123,Jacob\n");
        context.setData("csv2", "Phone,Last Name\n" +
                                "345678901,Taylor\n" +
                                "234567890,Scott\n" +
                                "123456789,Hanson\n" +
                                "456789012,Smoe\n" +
                                "567890123,Ladder");
        ExpressionProcessor subject = new ExpressionProcessor(context);
        Assert.assertEquals("Phone,Last Name\n" +
                            "345678901,Taylor\n" +
                            "234567890,Scott\n" +
                            "123456789,Hanson\n" +
                            "456789012,Smoe\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name,Phone,Last Name\n" +
                            "123456789,Jim,345678901,Taylor\n" +
                            "234567890,John,234567890,Scott\n" +
                            "345678901,James,123456789,Hanson\n" +
                            "456789012,Joe,456789012,Smoe\n" +
                            "567890123,Jacob,567890123,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,\\(empty\\))]"));

        context.setData("csv1", "SSN,First Name\n" +
                                "123456789,Jim,37\n" +
                                "234567890,John,32\n" +
                                "345678901,James,45\n" +
                                "456789012,Joe,19\n" +
                                "567890123,Jacob,22\n");
        context.setData("csv2", "Phone,Last Name,Color\n" +
                                "345678901,Taylor,Yellow\n" +
                                "234567890,Scott,Red\n" +
                                "123456789,Hanson,Green\n" +
                                "456789012,Smoe,Blue\n" +
                                "567890123,Ladder");
        subject = new ExpressionProcessor(context);
        Assert.assertEquals("Phone,Last Name,Color\n" +
                            "345678901,Taylor,Yellow\n" +
                            "234567890,Scott,Red\n" +
                            "123456789,Hanson,Green\n" +
                            "456789012,Smoe,Blue\n" +
                            "567890123,Ladder",
                            subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        Assert.assertEquals("SSN,First Name,Phone,Last Name,Color\n" +
                            "123456789,Jim,37,345678901,Taylor,Yellow\n" +
                            "234567890,John,32,234567890,Scott,Red\n" +
                            "345678901,James,45,123456789,Hanson,Green\n" +
                            "456789012,Joe,19,456789012,Smoe,Blue\n" +
                            "567890123,Jacob,22,567890123,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=true) merge(csv2,\\(empty\\))]"));
    }

    @Test
    public void processCSV_merge_multiple() throws Exception {
        context.setData("csv1", "SSN,First Name\n" +
                                "123456789,Jim\n" +
                                "234567890,John\n" +
                                "345678901,James\n" +
                                "456789012,Joe\n" +
                                "567890123,Jacob\n");
        context.setData("csv2", "SSN,Last Name\n" +
                                "345678901,Taylor\n" +
                                "123456789,Hanson\n" +
                                "456789012,Smoe\n" +
                                "123456792,Jaime\n" +
                                "567890123,Ladder");
        context.setData("csv3", "SSN,Color\n" +
                                "345678901,Yellow\n" +
                                "234567890,Red\n" +
                                "456789012,Blue\n" +
                                "123456792,Green");
        context.setData("csv4", "SSN,Snack\n" +
                                "045678901,Chocolate\n" +
                                "123456789,Nuts\n" +
                                "456789012,Chicken\n" +
                                "123456792,Jerky\n" +
                                "567890123,Everything!\n\n");
        ExpressionProcessor subject = new ExpressionProcessor(context);
        subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]");
        subject.process("[CSV(${csv3}) => parse(header=true) store(csv3)]");
        subject.process("[CSV(${csv4}) => parse(header=true) store(csv4)]");

        Assert.assertEquals("SSN,First Name,Last Name\n" +
                            "123456789,Jim,Hanson\n" +
                            "123456792,,Jaime\n" +
                            "234567890,John\n" +
                            "345678901,James,Taylor\n" +
                            "456789012,Joe,Smoe\n" +
                            "567890123,Jacob,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=true)" +
                                            "                 merge(csv2,SSN)]"));
        Assert.assertEquals("SSN,First Name,Last Name,Color\n" +
                            "123456789,Jim,Hanson\n" +
                            "123456792,,Jaime,Green\n" +
                            "234567890,John,Red\n" +
                            "345678901,James,Taylor,Yellow\n" +
                            "456789012,Joe,Smoe,Blue\n" +
                            "567890123,Jacob,Ladder",
                            subject.process("[CSV(${csv1}) => parse(header=true)" +
                                            "                 merge(csv2,SSN)" +
                                            "                 merge(csv3,SSN)]"));
        Assert.assertEquals("SSN,First Name,Last Name,Color,Snack\n" +
                            "045678901,,,,Chocolate\n" +
                            "123456789,Jim,Hanson,Nuts\n" +
                            "123456792,,Jaime,Green,Jerky\n" +
                            "234567890,John,Red\n" +
                            "345678901,James,Taylor,Yellow\n" +
                            "456789012,Joe,Smoe,Blue,Chicken\n" +
                            "567890123,Jacob,Ladder,Everything!",
                            subject.process("[CSV(${csv1}) => parse(header=true)" +
                                            "                 merge(csv2,SSN)" +
                                            "                 merge(csv3,SSN)" +
                                            "                 merge(csv4,SSN)]"));
    }

    @Test
    public void processExcel() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => text]"),
                   allOf(is(not(nullValue())), is(equalTo(file))));
        assertThat(subject.process("[EXCEL(" + file + ") => worksheets]"),
                   allOf(is(not(nullValue())), is(equalTo("sc1,gpos1,tc17,list45,rccTo_2355"))));

        String expectedCapturedData =
            "number1,numbers3,name,emp type,soc sec no,fso,union,class,addr 1,city,state,postal code,email,fed mar stat,allow/dep,calc type,adj amt,state mar stat,allow/dep,calc type,adj amt,i-9,last year w4,wfr NY,wfr CA,start date,union dues,document id,res state,work state,state of incorporation,employee status,vacation type,holiday type,start memo,gl code,tax waiver,from,to,state,CA,NC,SC,MI,MS,NM,GA,PA,MA,CO,LA\r\n" +
            "69898,1,ANDERSON/A,R,,,Q0484,2300001,A ,B,CA,91504,N,S,1,,,,,,,070717,17,070717,070717,070717,Y,0,CA  SF,INAD,,3,1,1,,01123456,,,,,,,,,,,,,,,\r\n" +
            "69898,2,BANSHE/A,R,,,STV00,1100056,A,B,CA,91504,N,M,1,,,,,,,070717,17,070717,070717,070717,,0,NM,PQ,,3,1,1,,01123456789,3,010117,123130,CN,,,,,,,,,,,\r\n" +
            "28520,3,\"COLDDER, INC\",C,652521225,COLDDER/A,DWEST,2901001,A,B,CA,91504,N,S,0,5,,,,,,070717,17,070717,070717,070717,,0,CA    ,NC,CA,3,2,3,,01,,,,, , , ,X,X,X,X,X,X,X,X\r\n" +
            "15970,1,ANDERSON/A,R,,,ZNUPA,9400001,A ,B,CA,91504,N,S,1,,,,,,,070717,17,070717,070717,070717, ,0,CA  SF,NYNYNY,,3,1,1,,01,,,,,,,,,,,,,,,";
        assertThat(subject.process("[EXCEL(" + file + ") => read(sc1,A2:AY6)]"),
                   allOf(is(not(nullValue())), is(equalTo(expectedCapturedData))));

        expectedCapturedData =
            "number1,69898,69898,28520,15970\r\n" +
            "numbers3,1,2,3,1\r\n" +
            "name,ANDERSON/A,BANSHE/A,\"COLDDER, INC\",ANDERSON/A\r\n" +
            "emp type,R,R,C,R\r\n" +
            "soc sec no,,,652521225,\r\n" +
            "fso,,,COLDDER/A,\r\n" +
            "union,Q0484,STV00,DWEST,ZNUPA\r\n" +
            "class,2300001,1100056,2901001,9400001\r\n" +
            "addr 1,A,A,A,A\r\n" +
            "city,B,B,B,B\r\n" +
            "state,CA,CA,CA,CA\r\n" +
            "postal code,91504,91504,91504,91504\r\n" +
            "email,N,N,N,N\r\n" +
            "fed mar stat,S,M,S,S\r\n" +
            "allow/dep,1,1,0,1\r\n" +
            "calc type,,,5,\r\n" +
            "adj amt,,,,\r\n" +
            "state mar stat,,,,\r\n" +
            "allow/dep,,,,\r\n" +
            "calc type,,,,\r\n" +
            "adj amt,,,,\r\n" +
            "i-9,070717,070717,070717,070717\r\n" +
            "last year w4,17,17,17,17\r\n" +
            "wfr NY,070717,070717,070717,070717\r\n" +
            "wfr CA,070717,070717,070717,070717\r\n" +
            "start date,070717,070717,070717,070717\r\n" +
            "union dues,Y,,,\r\n" +
            "document id,0,0,0,0\r\n" +
            "res state,CA  SF,NM,CA,CA  SF\r\n" +
            "work state,INAD,PQ,NC,NYNYNY\r\n" +
            "state of incorporation,,,CA,\r\n" +
            "employee status,3,3,3,3\r\n" +
            "vacation type,1,1,2,1\r\n" +
            "holiday type,1,1,3,1\r\n" +
            "start memo,,,,\r\n" +
            "gl code,01123456,01123456789,01,01\r\n" +
            "tax waiver,,3,,\r\n" +
            "from,,010117,,\r\n" +
            "to,,123130,,\r\n" +
            "state,,CN,,\r\n" +
            "CA,,,,\r\n" +
            "NC,,,,\r\n" +
            "SC,,,,\r\n" +
            "MI,,,X,\r\n" +
            "MS,,,X,\r\n" +
            "NM,,,X,\r\n" +
            "GA,,,X,\r\n" +
            "PA,,,X,\r\n" +
            "MA,,,X,\r\n" +
            "CO,,,X,\r\n" +
            "LA,,,X,";

        assertThat(subject.process("[EXCEL(" + file + ") => read(sc1,A2:AY6) pack transpose]"),
                   allOf(is(not(nullValue())), is(equalTo(expectedCapturedData))));

        assertThat(subject.process("[EXCEL(" + file + ") => read(sc1,A2:AY6) pack transpose csv row(6)]"),
                   allOf(is(not(nullValue())), is(equalTo("union,Q0484,STV00,DWEST,ZNUPA"))));

        // --------------------------------------------------------------------------------
        // test with existing workbook/worksheet
        // --------------------------------------------------------------------------------
        File targetSource = new File(ResourceUtils.getResourceFilePath(fixtureBase + "10.xlsx"));

        String tmpDir = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator);
        File targetFile = new File(tmpDir + "junk1.xlsx");

        FileUtils.copyFile(targetSource, targetFile);
        assertThat(subject.process("[EXCEL(" + file + ") =>" +
                                   " read(sc1,A2:AY6)" +
                                   " pack" +
                                   " transpose" +
                                   " save(" + targetFile.getAbsolutePath() + ",transposed,K9)" +
                                   "]"),
                   is(not(nullValue())));
        System.out.println("saved EXCEL to " + targetFile);

        // make sure we maintain more or less the file size as found with 'targetSource'
        Assert.assertTrue(targetFile.length() > 17 * 1024);

        // test worksheet content
        Excel tmpExcel = new Excel(targetFile);
        Worksheet worksheet = tmpExcel.worksheet("transposed");
        Assert.assertNotNull(worksheet);
        Assert.assertEquals("number1", worksheet.cell(new ExcelAddress("K9")).getStringCellValue());
        Assert.assertEquals("69898", worksheet.cell(new ExcelAddress("L9")).getStringCellValue());
        Assert.assertEquals("numbers3", worksheet.cell(new ExcelAddress("K10")).getStringCellValue());
        Assert.assertEquals("2", worksheet.cell(new ExcelAddress("M10")).getStringCellValue());

        // --------------------------------------------------------------------------------
        // test with [potentially] new worksheet
        // --------------------------------------------------------------------------------
        File tmpFile = new File(tmpDir + "junk2.xlsx");
        assertThat(subject.process("[EXCEL(" + file + ") =>" +
                                   " read(sc1,A2:AY6)" +
                                   " pack" +
                                   " transpose" +
                                   " save(" + tmpFile.getAbsolutePath() + ",transposed,C14)" +
                                   "]"),
                   is(not(nullValue())));
        System.out.println("saved EXCEL to " + tmpFile);
        Assert.assertNotNull(tmpFile);
        Assert.assertTrue(tmpFile.isFile());
        Assert.assertTrue(tmpFile.length() > 5 * 1024);

        tmpExcel = new Excel(tmpFile);
        worksheet = tmpExcel.worksheet("transposed");
        Assert.assertNotNull(worksheet);
        Assert.assertEquals("number1", worksheet.cell(new ExcelAddress("C14")).getStringCellValue());
        Assert.assertEquals("69898", worksheet.cell(new ExcelAddress("D14")).getStringCellValue());
        Assert.assertEquals("numbers3", worksheet.cell(new ExcelAddress("C15")).getStringCellValue());
        Assert.assertEquals("2", worksheet.cell(new ExcelAddress("E15")).getStringCellValue());
    }

    @Test
    public void processExcel_worksheets() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => text]"),
                   allOf(is(not(nullValue())), is(equalTo(file))));
        assertThat(subject.process("[EXCEL(" + file + ") => worksheets]"),
                   allOf(is(not(nullValue())), is(equalTo("sc1,gpos1,tc17,list45,rccTo_2355"))));
    }

    @Test
    public void processExcel_read() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        String expectedCapturedData =
            "number1,numbers3,name,emp type,soc sec no,fso,union,class,addr 1,city,state,postal code,email,fed mar stat,allow/dep,calc type,adj amt,state mar stat,allow/dep,calc type,adj amt,i-9,last year w4,wfr NY,wfr CA,start date,union dues,document id,res state,work state,state of incorporation,employee status,vacation type,holiday type,start memo,gl code,tax waiver,from,to,state,CA,NC,SC,MI,MS,NM,GA,PA,MA,CO,LA\r\n" +
            "69898,1,ANDERSON/A,R,,,Q0484,2300001,A ,B,CA,91504,N,S,1,,,,,,,070717,17,070717,070717,070717,Y,0,CA  SF,INAD,,3,1,1,,01123456,,,,,,,,,,,,,,,\r\n" +
            "69898,2,BANSHE/A,R,,,STV00,1100056,A,B,CA,91504,N,M,1,,,,,,,070717,17,070717,070717,070717,,0,NM,PQ,,3,1,1,,01123456789,3,010117,123130,CN,,,,,,,,,,,\r\n" +
            "28520,3,\"COLDDER, INC\",C,652521225,COLDDER/A,DWEST,2901001,A,B,CA,91504,N,S,0,5,,,,,,070717,17,070717,070717,070717,,0,CA    ,NC,CA,3,2,3,,01,,,,, , , ,X,X,X,X,X,X,X,X\r\n" +
            "15970,1,ANDERSON/A,R,,,ZNUPA,9400001,A ,B,CA,91504,N,S,1,,,,,,,070717,17,070717,070717,070717, ,0,CA  SF,NYNYNY,,3,1,1,,01,,,,,,,,,,,,,,,";

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => read(sc1,A2:AY6)]"),
                   allOf(is(not(nullValue())), is(equalTo(expectedCapturedData))));
    }

    @Test
    public void processExcel_transpose() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        String expectedCapturedData =
            "number1,69898,69898,28520,15970\r\n" +
            "numbers3,1,2,3,1\r\n" +
            "name,ANDERSON/A,BANSHE/A,\"COLDDER, INC\",ANDERSON/A\r\n" +
            "emp type,R,R,C,R\r\n" +
            "soc sec no,,,652521225,\r\n" +
            "fso,,,COLDDER/A,\r\n" +
            "union,Q0484,STV00,DWEST,ZNUPA\r\n" +
            "class,2300001,1100056,2901001,9400001\r\n" +
            "addr 1,A,A,A,A\r\n" +
            "city,B,B,B,B\r\n" +
            "state,CA,CA,CA,CA\r\n" +
            "postal code,91504,91504,91504,91504\r\n" +
            "email,N,N,N,N\r\n" +
            "fed mar stat,S,M,S,S\r\n" +
            "allow/dep,1,1,0,1\r\n" +
            "calc type,,,5,\r\n" +
            "adj amt,,,,\r\n" +
            "state mar stat,,,,\r\n" +
            "allow/dep,,,,\r\n" +
            "calc type,,,,\r\n" +
            "adj amt,,,,\r\n" +
            "i-9,070717,070717,070717,070717\r\n" +
            "last year w4,17,17,17,17\r\n" +
            "wfr NY,070717,070717,070717,070717\r\n" +
            "wfr CA,070717,070717,070717,070717\r\n" +
            "start date,070717,070717,070717,070717\r\n" +
            "union dues,Y,,,\r\n" +
            "document id,0,0,0,0\r\n" +
            "res state,CA  SF,NM,CA,CA  SF\r\n" +
            "work state,INAD,PQ,NC,NYNYNY\r\n" +
            "state of incorporation,,,CA,\r\n" +
            "employee status,3,3,3,3\r\n" +
            "vacation type,1,1,2,1\r\n" +
            "holiday type,1,1,3,1\r\n" +
            "start memo,,,,\r\n" +
            "gl code,01123456,01123456789,01,01\r\n" +
            "tax waiver,,3,,\r\n" +
            "from,,010117,,\r\n" +
            "to,,123130,,\r\n" +
            "state,,CN,,\r\n" +
            "CA,,,,\r\n" +
            "NC,,,,\r\n" +
            "SC,,,,\r\n" +
            "MI,,,X,\r\n" +
            "MS,,,X,\r\n" +
            "NM,,,X,\r\n" +
            "GA,,,X,\r\n" +
            "PA,,,X,\r\n" +
            "MA,,,X,\r\n" +
            "CO,,,X,\r\n" +
            "LA,,,X,";

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => read(sc1,A2:AY6) pack transpose]"),
                   allOf(is(not(nullValue())), is(equalTo(expectedCapturedData))));

        assertThat(subject.process("[EXCEL(" + file + ") => read(sc1,A2:AY6) pack transpose csv row(6)]"),
                   allOf(is(not(nullValue())), is(equalTo("union,Q0484,STV00,DWEST,ZNUPA"))));
    }

    @Test
    public void processExcel_save() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        // --------------------------------------------------------------------------------
        // test with existing workbook/worksheet
        // --------------------------------------------------------------------------------
        File targetSource = new File(ResourceUtils.getResourceFilePath(fixtureBase + "10.xlsx"));

        String tmpDir = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator);
        File targetFile = new File(tmpDir + "junk1.xlsx");

        FileUtils.copyFile(targetSource, targetFile);

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") =>" +
                                   " read(sc1,A2:AY6)" +
                                   " pack" +
                                   " transpose" +
                                   " save(" + targetFile.getAbsolutePath() + ",transposed,K9)" +
                                   "]"),
                   is(not(nullValue())));
        System.out.println("saved EXCEL to " + targetFile);

        // make sure we maintain more or less the file size as found with 'targetSource'
        Assert.assertTrue(targetFile.length() > 17 * 1024);

        // test worksheet content
        Excel tmpExcel = new Excel(targetFile);
        Worksheet worksheet = tmpExcel.worksheet("transposed");
        Assert.assertNotNull(worksheet);
        Assert.assertEquals("number1", worksheet.cell(new ExcelAddress("K9")).getStringCellValue());
        Assert.assertEquals("69898", worksheet.cell(new ExcelAddress("L9")).getStringCellValue());
        Assert.assertEquals("numbers3", worksheet.cell(new ExcelAddress("K10")).getStringCellValue());
        Assert.assertEquals("2", worksheet.cell(new ExcelAddress("M10")).getStringCellValue());

        // --------------------------------------------------------------------------------
        // test with [potentially] new worksheet
        // --------------------------------------------------------------------------------
        File tmpFile = new File(tmpDir + "junk2.xlsx");
        assertThat(subject.process("[EXCEL(" + file + ") =>" +
                                   " read(sc1,A2:AY6)" +
                                   " pack" +
                                   " transpose" +
                                   " save(" + tmpFile.getAbsolutePath() + ",transposed,C14)" +
                                   "]"),
                   is(not(nullValue())));
        System.out.println("saved EXCEL to " + tmpFile);
        Assert.assertNotNull(tmpFile);
        Assert.assertTrue(tmpFile.isFile());
        Assert.assertTrue(tmpFile.length() > 5 * 1024);

        tmpExcel = new Excel(tmpFile);
        worksheet = tmpExcel.worksheet("transposed");
        Assert.assertNotNull(worksheet);
        Assert.assertEquals("number1", worksheet.cell(new ExcelAddress("C14")).getStringCellValue());
        Assert.assertEquals("69898", worksheet.cell(new ExcelAddress("D14")).getStringCellValue());
        Assert.assertEquals("numbers3", worksheet.cell(new ExcelAddress("C15")).getStringCellValue());
        Assert.assertEquals("2", worksheet.cell(new ExcelAddress("E15")).getStringCellValue());
    }

    @Test
    public void processExcel_pack() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        String expectedCapturedData =
            "number1,numbers2,fla/flb/flc,numbers3,name,union,work date,document id,comment,wrk state,wk dy,tx dy,sep chk,,gl,pay code,hours,amount,class code\r\n" +
            "69898,1,B,1,ANDERSON/A,Q0484,070717,0,,,1`,5,,,,110,1.0,50.00,\r\n" +
            "69898,1,A,2,BANSHE/A,STV00,070717,0,,,1`,5,,,,201,1.0,50.00,\r\n" +
            "28520,1,A,3,\"COLDDER, INC\",DWEST,070717,0,,,1`,5,,,,220,1.0,50.00,\r\n" +
            "15971,1,C,1,ANDERSON/A,ZNUPA,070717,0,,,1`,5,,,,853,,50.00,\r\n" +
            "69898,2,B,1,ANDERSON/A,Q0484,070817,0,,,1`,5,,,,110,32.0,320.00,\r\n" +
            "69898,2,B,1,ANDERSON/A,Q0484,070817,0,,,,,,,,111,4.0,40.00,\r\n" +
            "69898,2,B,1,ANDERSON/A,Q0484,070817,0,,,,,,,,112,4.0,40.00,\r\n" +
            "69898,2,B,1,ANDERSON/A,Q0484,070817,0,,,,,,,,201,4.0,40.00,\r\n" +
            "69898,2,B,1,ANDERSON/A,Q0484,070817,0,,,,,,,,202,1.0,10.00,\r\n" +
            "69898,2,A,2,BANSHE/A,STV00,070817,0,,,1`,5,,,,110,,35000.00,\r\n" +
            "69898,2,A,2,BANSHE/A,STV00,070817,0,,,,,,,,104,,100.00,\r\n" +
            "69898,2,A,2,BANSHE/A,STV00,070817,0,,,,,,,,108,,555.00,\r\n" +
            "69898,2,A,2,BANSHE/A,STV00,070817,0,,,,,,,,135,,222.00,\r\n" +
            "69898,2,A,2,BANSHE/A,STV00,070817,0,,,,,,,,720,,25.00,\r\n" +
            "28520,2,A,3,\"COLDDER, INC\",DWEST,070817,0,,,1`,5,,,,110,,4845.00,\r\n" +
            "28520,2,A,3,\"COLDDER, INC\",DWEST,070817,0,,,,,,,,216,,538.00,\r\n" +
            "28520,2,A,3,\"COLDDER, INC\",DWEST,070817,0,,,,,,,,630,,424.00,\r\n" +
            "15971,2,C,1,ANDERSON/A,ZNUPA,070817,0,,NJ,1`,5,,,,852,,25.00,\r\n" +
            "15971,2,C,1,ANDERSON/A,ZNUPA,070817,0,,NJ,,,,,,861,,32.00,\r\n" +
            "15971,2,C,1,ANDERSON/A,ZNUPA,070817,0,,NJ,,,,,,110,40.0,500.00,\r\n" +
            "15971,2,C,1,ANDERSON/A,ZNUPA,070817,0,,NJ,,,,,,350,8.0,150.00,\r\n" +
            "15971,2,C,1,ANDERSON/A,ZNUPA,070817,0,,NJ,,,,,,360,8.0,200.00,\r\n" +
            "15971,2,C,1,ANDERSON/A,ZNUPA,070917,0,,NJ,,,,,,,,,";

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => read(tc17,A1:S27) pack]"),
                   allOf(is(not(nullValue())), is(equalTo(expectedCapturedData))));
    }

    @Test
    public void processExcel_rowCount() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => read(tc17,A1:S27) rowCount]"),
                   allOf(is(not(nullValue())), is(equalTo("27"))));
        assertThat(subject.process("[EXCEL(" + file + ") => read(tc17,A1:S27) pack rowCount]"),
                   allOf(is(not(nullValue())), is(equalTo("24"))));
        assertThat(subject.process("[EXCEL(" + file + ") => read(tc17,A1) rowCount]"),
                   allOf(is(not(nullValue())), is(equalTo("1"))));
    }

    @Test
    public void processExcel_columnCount() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => read(tc17,A1:S27) columnCount]"),
                   allOf(is(not(nullValue())), is(equalTo("19"))));
        assertThat(subject.process("[EXCEL(" + file + ") => read(tc17,A1:S27) pack columnCount]"),
                   allOf(is(not(nullValue())), is(equalTo("19"))));
        assertThat(subject.process("[EXCEL(" + file + ") => read(tc17,A1) columnCount]"),
                   allOf(is(not(nullValue())), is(equalTo("1"))));
    }

    @Test
    public void processExcel_store_restore() throws Exception {
        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "9.xlsx");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => read(rccTo_2355,A3:AD11) store(excel1) column-count]"),
                   allOf(is(not(nullValue())), is(equalTo("30"))));
        assertThat(subject.process("[EXCEL(excel1) => rowCount]"), allOf(is(not(nullValue())), is(equalTo("9"))));

        // multiple store and transformation
        assertThat(subject.process("[EXCEL(excel1) =>" +
                                   " pack                   store(excel2)" +
                                   " transpose              store(excel3)" +
                                   " clear(A3:I3) transpose store(excel4)" +
                                   " csv row(4)" +
                                   "]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(",ANDERSON/A,BANSHE/A,COLDDER\\, INC,ANDERSON/A,ANDERSON/A,BANSHE/A," +
                                    "COLDDER\\, INC,ANDERSON/A"))));

        assertThat(subject.process("[EXCEL(excel1) => rowCount]"), allOf(is(not(nullValue())), is(equalTo("9"))));
        assertThat(subject.process("[EXCEL(excel1) => pack rowCount]"), allOf(is(not(nullValue())), is(equalTo("9"))));

        assertThat(subject.process("[EXCEL(excel2) => rowCount]"), allOf(is(not(nullValue())), is(equalTo("9"))));
        assertThat(subject.process("[EXCEL(excel2) => columnCount]"), allOf(is(not(nullValue())), is(equalTo("30"))));
        assertThat(subject.process("[EXCEL(excel2) => csv row(5) ascending last]"),
                   allOf(is(not(nullValue())), is(equalTo("Q0484"))));

        assertThat(subject.process("[EXCEL(excel3) =>" +
                                   " pack" +
                                   " transpose" +
                                   " csv" +
                                   " parse(header=true)" +
                                   " renameColumn( ,name1)" +
                                   " filter(numbers3 = 1|name1 start with ANDERSON)" +
                                   " rowCount]"),
                   allOf(is(not(nullValue())), is(equalTo("4"))));

        assertThat(subject.process("[EXCEL(excel4) => pack transpose csv parse(header=true) rowCount]"),
                   allOf(is(not(nullValue())), is(equalTo("8"))));
    }

    @Test
    public void processExcel_writes() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + this.getClass().getSimpleName() + "9.xlsx");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => " +
                                   "    read(list45,A1:F13) " +
                                   "    writeDown(B2,2,3,4,5,6,7,8,9,10,11,12,13) " +
                                   "    store(excel1) " +
                                   "    read(list45,B2:B13) " +
                                   "    transpose " +
                                   "    csv " +
                                   "    row(0)" +
                                   "]"),
                   equalTo("2,3,4,5,6,7,8,9,10,11,12,13"));
    }

    @Test
    public void processCSV_render_template() throws Exception {
        String resourceBasePath = resourcePath + "ExpressionProcessorTest";
        String csvFile = ResourceUtils.getResourceFilePath(resourceBasePath + "12.csv");

        String template = "Here is ${description}, which is located in ${fullAddress}, we call ourselves ${code}.\n";
        context.setData("template", template);

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String expected =
            "Here is Kentucky AxeWye, which is located in 710 W. Cainey Road , Townizel KY 42991 USA, we call ourselves KXY.\n" +
            "Here is Ante Industry, which is located in 71 Pilgrim Avenue #105, Chevy Chase MD 20815 USA, we call ourselves AI.\n" +
            "Here is Metadeo Printing Ltd, which is located in 70 Bowman St. , South Windsor CT 06074 USA, we call ourselves MPL.\n" +
            "Here is Bovoid Serivce Inc., which is located in 8 Rockaway Ave. #105, Vernon Rockville CT 06066 USA, we call ourselves BSI.\n" +
            "Here is Skafix Toy Factory, which is located in 6 Van Dyke Street , Harrisburg PA 17091 USA, we call ourselves STF.\n";
        assertThat(subject.process("[CSV(" + csvFile + ") => parse(header=true) render(template) text]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));

        String sqlTemplate = ResourceUtils.getResourceFilePath(resourceBasePath + "12.sql");
        String sqlPath = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator) +
                         this.getClass().getSimpleName() + "-rendered.sql";
        expected = "-- nexial:KXY result\n" +
                   "SELECT OFFICELOCATIONDESC AS \"description\", ADDRESSLINE1 || ' ' || ADDRESSLINE2 || ', ' || CITY || ' ' || OFFICELOCATIONSTATE || ' ' || ZIP || ' ' || COUNTRY AS \"fullAddress\" FROM OFFICELOCATIONS WHERE OFFICELOCATIONCODE = 'KXY';\n" +
                   "\n" +
                   "-- nexial:AI result\n" +
                   "SELECT OFFICELOCATIONDESC AS \"description\", ADDRESSLINE1 || ' ' || ADDRESSLINE2 || ', ' || CITY || ' ' || OFFICELOCATIONSTATE || ' ' || ZIP || ' ' || COUNTRY AS \"fullAddress\" FROM OFFICELOCATIONS WHERE OFFICELOCATIONCODE = 'AI';\n" +
                   "\n" +
                   "-- nexial:MPL result\n" +
                   "SELECT OFFICELOCATIONDESC AS \"description\", ADDRESSLINE1 || ' ' || ADDRESSLINE2 || ', ' || CITY || ' ' || OFFICELOCATIONSTATE || ' ' || ZIP || ' ' || COUNTRY AS \"fullAddress\" FROM OFFICELOCATIONS WHERE OFFICELOCATIONCODE = 'MPL';\n" +
                   "\n" +
                   "-- nexial:BSI result\n" +
                   "SELECT OFFICELOCATIONDESC AS \"description\", ADDRESSLINE1 || ' ' || ADDRESSLINE2 || ', ' || CITY || ' ' || OFFICELOCATIONSTATE || ' ' || ZIP || ' ' || COUNTRY AS \"fullAddress\" FROM OFFICELOCATIONS WHERE OFFICELOCATIONCODE = 'BSI';\n" +
                   "\n" +
                   "-- nexial:STF result\n" +
                   "SELECT OFFICELOCATIONDESC AS \"description\", ADDRESSLINE1 || ' ' || ADDRESSLINE2 || ', ' || CITY || ' ' || OFFICELOCATIONSTATE || ' ' || ZIP || ' ' || COUNTRY AS \"fullAddress\" FROM OFFICELOCATIONS WHERE OFFICELOCATIONCODE = 'STF';\n" +
                   "\n";
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(header=true)" +
                                   " pack" +
                                   " render(" + sqlTemplate + ")" +
                                   " store(sql1)" +
                                   " save(" + sqlPath + ")" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));

        assertThat(subject.process("[TEXT(" + sqlPath + ") => text]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));
        assertThat(subject.process("[TEXT(sql1) => text]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));

        File sqlFile = new File(sqlPath);
        System.out.println("sqlFile.length() = " + sqlFile.length());
        Assert.assertTrue(sqlFile.isFile());
        Assert.assertTrue(sqlFile.canRead());
        Assert.assertTrue(sqlFile.length() > 1250);

        String jsonTemplate = ResourceUtils.getResourceFilePath(resourceBasePath + "12.json");
        expected =
            "{ \"corporation\": [ " +
            "{ \"office\": { \"code\": \"KXY\", \"description\": \"Kentucky AxeWye\", \"address\": \"710 W. Cainey Road , Townizel KY 42991 USA\" } },\n" +
            "{ \"office\": { \"code\": \"AI\", \"description\": \"Ante Industry\", \"address\": \"71 Pilgrim Avenue #105, Chevy Chase MD 20815 USA\" } },\n" +
            "{ \"office\": { \"code\": \"MPL\", \"description\": \"Metadeo Printing Ltd\", \"address\": \"70 Bowman St. , South Windsor CT 06074 USA\" } },\n" +
            "{ \"office\": { \"code\": \"BSI\", \"description\": \"Bovoid Serivce Inc.\", \"address\": \"8 Rockaway Ave. #105, Vernon Rockville CT 06066 USA\" } },\n" +
            "{ \"office\": { \"code\": \"STF\", \"description\": \"Skafix Toy Factory\", \"address\": \"6 Van Dyke Street , Harrisburg PA 17091 USA\" } } " +
            "] }";
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(header=true)" +
                                   " render(" + jsonTemplate + ")" +
                                   " trim" +
                                   " remove-end(\\,)" +
                                   " prepend({ \"corporation\": [ )" +
                                   " append( ] })" +
                                   " text" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));
    }

    @Test
    public void processSql_basic() throws Exception {
        // used in SQL file
        context.setData("code", "KXY");
        context.setData("min. Location ID", 2);

        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "13.sql");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String result = subject.process("[SQL(" + file + ") => execute(testdb) store(sql expression)]");
        System.out.println("result = " + result);

        result = subject.process("[SQL(sql expression) => resultCount]");
        Assert.assertNotNull(result);
        Assert.assertEquals("2", result);

        String expected = "description,fullAddress\n" +
                          "Kentucky AxeWye,\"710 W. Cainey Road , Townizel KY 42991 USA\"";
        Assert.assertEquals("1", subject.process("[SQL(sql expression) => rowCount(result1)]"));
        result = subject.process("[SQL(sql expression) => csv(result1) text]");
        Assert.assertNotNull(result);
        Assert.assertEquals(expected, result);

        expected = "description,fullAddress\n" +
                   "Metadeo Printing Ltd,\"70 Bowman St. , South Windsor CT 06074 USA\"\n" +
                   "Bovoid Serivce Inc.,\"8 Rockaway Ave. #105, Vernon Rockville CT 06066 USA\"\n" +
                   "Skafix Toy Factory,\"6 Van Dyke Street , Harrisburg PA 17091 USA\"";
        Assert.assertEquals("3", subject.process("[SQL(sql expression) => rowCount(result2)]"));
        Assert.assertEquals("2", subject.process("[SQL(sql expression) => columnCount(result2)]"));
        Assert.assertNull(subject.process("[SQL(sql expression) => error(result2)]"));
        Assert.assertEquals("description,fullAddress", subject.process("[SQL(sql expression) => columns(result2)]"));
        result = subject.process("[SQL(sql expression) => csv(result2) text]");
        Assert.assertNotNull(result);
        Assert.assertEquals(expected, result);
    }

    @Test
    public void processSql_extend_results() throws Exception {
        // used in SQL file
        context.setData("TAX Year", 2017);
        context.setData("Client ID", 73443);
        context.setData("Voucher ID", 541089);

        String fixtureBase = resourcePath + this.getClass().getSimpleName();
        String file = ResourceUtils.getResourceFilePath(fixtureBase + "14.sql");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String result = subject.process("[SQL(" + file + ") =>" +
                                        "   execute(testdb)" +
                                        "   store(sql expression)" +
                                        "   rowCount(processing log)" +
                                        "]");
        System.out.println("result = " + result);
        Assert.assertNotNull(result);

        int processingLogCount = NumberUtils.toInt(result);
        Assert.assertTrue(processingLogCount < 1);

        result = subject.process("[SQL(sql expression) => rowCount(vouchers)]");
        System.out.println("result = " + result);
        Assert.assertNotNull(result);

        int voucherCount = NumberUtils.toInt(result);
        Assert.assertTrue(voucherCount > 1);

        result = subject.process("[SQL(sql expression) => csv(vouchers)]");
        System.out.println("result = " + result);
        Assert.assertTrue(StringUtils.isNotBlank(result));
        Assert.assertTrue(StringUtils.countMatches(result, "\n") > 1);
    }

    private DataAccess initDataAccess() {
        Map<String, String> dbTypes = new HashMap<>();
        dbTypes.put("sqlite", "org.sqlite.JDBC");

        DataAccess da = new DataAccess();
        da.setDbTypes(dbTypes);
        return da;
    }
}
