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
import org.junit.FixMethodOrder;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.runners.MethodSorters.NAME_ASCENDING;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.FlowControls.ANY_FIELD;

@FixMethodOrder(value = NAME_ASCENDING)
public class ExpressionProcessorTest {
    private DataAccess da = initDataAccess();
    private ExecutionContext context = new MockExecutionContext(true) {

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
    private String className = this.getClass().getSimpleName();

    @Before
    public void setup() throws Exception {
        resourcePath = "/" + StringUtils.replace(this.getClass().getPackage().getName(), ".", "/") + "/";

        context.setData("testdb.type", "sqlite");
        context.setData("testdb.url", NexialTestUtils.resolveTestDbUrl());
        context.setData("testdb.autocommit", "true");
        context.setData("nexial.textDelim", ",");
    }

    @After
    public void tearDown() {
        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void processText() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("8", subject.process("[TEXT(hello world) => upper distinct length]"));
        assertEquals("JOHNNY BE GOODE", subject.process("[TEXT(Johnny Be Goode) => upper]"));
        assertEquals("EventDriven", subject.process("[TEXT(Event Driven Infrastructure) => substring(0, 12) pack]"));
        assertEquals("If mau're happy  mau know it clap maur hs",
                     subject.process("[TEXT(If you're happy and you know it clap your hands) =>" +
                                     " remove(and) replace(yo,ma)]"));
        assertEquals("123456.78", subject.process("[TEXT(-123,45 6.78) => retain(0123456789.)]"));
        assertEquals("storu", subject.process("[TEXT(everybody wants to rule the world) =>" +
                                              " before(the world) after(everybody) trim between(t,l) pack]"));
        assertEquals("So this is an AWESOME way to chat with your friends!",
                     subject.process("So this is an " +
                                     "[TEXT(awesome) => upper] way to " +
                                     "[TEXT(Chat) => distinct lower] with your friends!"));
        assertEquals("I love Jamba Laya, Chimi Chunga and Lodiee Paratha, but I can't spell them!",
                     subject.process("I love " +
                                     "[TEXT(Jumbo) => replace(o,a) replace(u,a) append( Laya)], " +
                                     "[TEXT(Chunga) => prepend(Chimi )] and " +
                                     "[TEXT(lodi paratha) => title insert(Lodi,ee)]" +
                                     ", but I can't spell them!"));
        assertEquals("There are 3 's's in Missisauga",
                     subject.process("There are [TEXT(Missisauga) => count(s)] 's's in Missisauga"));

        context.setData("fruit", "Apple");
        context.setData("more fruits", "Strawberry Banana");
        assertEquals("I love Apple,Strawberry,Banana - and they are GOOD FOR YOU!",
                     context.replaceTokens("I love [TEXT(${fruit}) => " +
                                           " append(\\,) append(${more fruits}) replace( ,\\,) pack list(\\,)]" +
                                           " - and they are [TEXT(good for you) => upper]!"));

        assertEquals("Rainer", context.replaceTokens("[TEXT(Ra.iner) => replace(.,)]"));
    }

    @Test
    public void processText2() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("eoo", subject.process("[TEXT(hello world) => retain(aeiou)]"));
        assertEquals("i i a e", subject.process("[TEXT(This is a test) => retain(aeiou )]"));
        assertEquals("Thsstst", subject.process("[TEXT(This is a test) => removeRegex([aeiou ])]"));
        assertEquals("hll wrld", subject.process("[TEXT(hello world) => removeRegex([aeiou])]"));
        assertEquals("12.450.903419", subject.process("[TEXT(12.450.90-b3419) => retainRegex([0-9\\.])]"));
        assertEquals("f ce ad ee ea ag",
                     subject.process("[TEXT(four scores and seven years ago) => retainRegex([a-j ])]"));
    }

    @Test
    public void processText_invalid_data() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        // invalid expression since there's no data assign as data type
        assertEquals("[TEXT() => remove(a)]", subject.process("[TEXT() => remove(a)]"));

        // invalid data variable
        assertEquals("", subject.process("[TEXT(${no_such_data}) => remove(a)]"));

        // null data variable test
        context.setData("num1", "10.95");
        context.setData("null_data", (String) null);

        // empty data begets empty data
        assertEquals("", subject.process("[TEXT(${null_data}) => remove(\\,)]"));

        // empty data cannot be used in numeric processing
        // Assert.assertEquals("10.95", subject.process("[NUMBER(${num1}) => multiply([TEXT(${null_data}) => remove(\\,)])]"));
    }

    @Test
    public void processTest_csv() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixturePath = ResourceUtils.getResourceFilePath(resourcePath + className + "18.txt");

        assertEquals("12345,Johnny Walker,Alcohol\n" +
                     "4321,\"Jim Carey\",Actor\n" +
                     "153.1,\"Johnson, K.\",Imposter\n" +
                     "--102,Adam Scham,Crook",
                     subject.process("[TEXT(" + fixturePath + ") => csv(0,6,19) text]"));
    }

    @Test
    public void processTest_regex() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("showcase/spiderman.", subject.process("[TEXT(showcase/spiderman) =>" +
                                                            " replaceRegex(\\(.+/.+\\),$1.)]"));
        assertEquals("showcase/", subject.process("[TEXT(showcase) =>" +
                                                  " replaceRegex(\\(.+/.+\\),$1.)" +
                                                  " replaceRegex(\\(.*[^/].*\\),$1/) ]"));
        assertEquals("\"hello\",23.44,\"My name\",124",
                     subject.process("[TEXT(\"hello\",\"23.44\",\"My name\",124) =>" +
                                     " replaceRegex(\"([0-9.]+\\)\",$1)]"));
    }

    @Test
    public void processTest_IfMissing() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("ex-spidermansion",
                     subject.process("[TEXT(spiderman) => appendIfMissing(sion) prependIfMissing(ex-)]"));
    }

    @Test
    public void processText_change() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "this is [TEXT(the best opportunity (or chance maybe...)) => " +
                         "upper remove(\\)) replace( \\(, ) remove(...)], blah...";
        assertEquals("this is THE BEST OPPORTUNITY OR CHANCE MAYBE, blah...", subject.process(fixture));

        assertEquals("this is .).... blah...", subject.process("this is [TEXT(.)....) =>" +
                                                               " replace([, ) replace(], )] blah..."));

        System.out.println("result = " + subject.process("this is [XML(<a>b</a>) => extract(//a)], blah..."));

        // result = subject.process("this is [XML(<...>) => extract(//a[b(c)='d'])] " +
        //                          "and [TEXT(..)...) => replace([, ) replace(], )] blah...");
        // System.out.println(result);
    }

    @Test
    public void processText_different_delim() throws Exception {

        context.setData("nexial.textDelim", "|");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        // replace character same as delim
        assertEquals("this line\n" +
                     "that line\n" +
                     "another line\n" +
                     "last line",
                     subject.process("[TEXT(this line|that line|another line|last line) => replace(\\||\n) ]"));

        // regex-replace character same as delim... no need to double-escape pipe because it's wrapped in [..] as simple class
        assertEquals("this line\n" +
                     "that line\n" +
                     "another line\n" +
                     "last line",
                     subject.process("[TEXT(this line|that line|another line|last line) =>" +
                                     " replaceRegex([\\|\\]|\n) ]"));

        // regex-replace character same as delim... need to double-escape pipe for grouping
        assertEquals("this line\n" +
                     "that line\n" +
                     "another line\n" +
                     "last line",
                     subject.process("[TEXT(this line|that line|another line|last line) =>" +
                                     " replaceRegex(\\(\\\\|\\)|\n) ]"));

        // regex-replace character same as delim... need to double-escape pipe
        assertEquals("this line\n" +
                     "that line\n" +
                     "another line\n" +
                     "last line",
                     subject.process("[TEXT(this line|that line|another line|last line) =>" +
                                     " replaceRegex(\\\\||\n) ]"));
    }

    @Test
    public void processText_multi_char_delim() throws Exception {

        context.setData("nexial.textDelim", ",|");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        // replace character same as delim
        assertEquals("this line\n" +
                     "that line\n" +
                     "another line\n" +
                     "last line",
                     subject.process("[TEXT(this line,|that line,|another line,|last line) => replace(\\,|,|\n) ]"));

        // regex-replace character same as delim... need to double-escape pipe for grouping
        assertEquals("this line\n" +
                     "that line\n" +
                     "another line\n" +
                     "last line",
                     subject.process("[TEXT(this line,|that line,|another line,|last line) =>" +
                                     " replaceRegex(\\(\\,\\|\\),|\n) ]"));

        // regex-replace character same as delim... need to double-escape pipe
        assertEquals("this line\n" +
                     "that line\n" +
                     "another line\n" +
                     "last line",
                     subject.process("[TEXT(this line,|that line,|another line,|last line) =>" +
                                     " replaceRegex(\\,\\|,|\n) ]"));
    }

    @Test
    public void processTest_html() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "\n" +
                         "<html>\n" +
                         "<head><title>Index of /clients/</title></head>\n" +
                         "<body bgcolor=\"white\">\n" +
                         "<h1>Index of /clients/</h1><hr><pre><a href=\"../\">../</a>\n" +
                         "<a href=\"MovieMagicBudgeting-dev-682.exe\">MovieMagicBudgeting-dev-682.exe</a>                    09-Sep-2019 18:16            45637168\n" +
                         "<a href=\"MovieMagicBudgeting-dev-682.zip\">MovieMagicBudgeting-dev-682.zip</a>                    09-Sep-2019 18:16            65351582\n" +
                         "</pre><hr></body>\n" +
                         "</html>\n";
        System.out.println("result = " + subject.process("[TEXT(" + fixture + ") => html ]"));
    }

    @Test
    public void processTest_resolve_as_url() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "https://api.ipify.org?format=json";
        assertEquals(fixture, subject.process("[TEXT(" + fixture + ") => text ]"));

        context.setData("nexial.resolveTextAsURL", "true");
        assertTrue(subject.process("[TEXT(" + fixture + ") => text ]").contains("{"));
    }

    @Test
    public void processTest_extract() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixturePath = ResourceUtils.getResourceFilePath(resourcePath + className + "19.txt");
        String expected = "Nexial Automation | Test Automation Platform for everyone!\n" +
                          "Welcome! | Tutorials for Nexial - Nexial Automation\n" +
                          "nexiality/nexial-core: test automation for everyone ... - GitHub\n" +
                          "nexiality/documentation: documentation for Nexial ... - GitHub\n" +
                          "Nexial in Action - YouTube\n" +
                          "#nexial hashtag on Twitter\n" +
                          "Pooja Shejawale - Principal Software Engineer - Accion Labs ...";

        assertEquals("7", subject.process("[TEXT(" + fixturePath + ") =>" +
                                          " extract(<h3 class=\"LC20lb\">,</h3>,false)" +
                                          " store(extract_list) size" +
                                          "]"));
        String extracted = subject.process("[LIST(extract_list) => combine(\n)]");
        System.out.println();
        System.out.println(extracted);
        assertEquals(expected, extracted);

        extracted = subject.process("[TEXT(" + fixturePath + ") =>" +
                                    " extract(<h3 class=\"[0-9A-Za-z_\\-]{2\\,}?\">,</h3>,false)" +
                                    " combine(\n)" +
                                    "]");
        System.out.println();
        System.out.println(extracted);
        assertEquals(expected, extracted);
    }

    @Test
    public void processList() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("55", subject.process("[LIST(1,2,3,4,5,6,7,8,9,10) => sum]"));

        String fixture = "[LIST(2441117.97,4750496.50,890528.83,679465.00,2441117.97,6320490.33,8761608.30) => sum]";
        assertEquals("26284824.90", subject.process(fixture));

        fixture = "[LIST(2441117.97,4750496.50,890528.83,679465.0005,2441117.97,6320490.33,8761608.30) => sum]";
        assertEquals("26284824.9005", subject.process(fixture));

        fixture = "[LIST(2441117.97,4750496.50,890528.83,679465.0005,2441117.97,6320490.3305,8761608.30) => sum]";
        assertEquals("26284824.9010", subject.process(fixture));

        assertEquals("14", subject.process("[LIST(1,0,-1254,14,14.0,13.99999) => max]"));
        assertEquals("-13.99999", subject.process("[LIST(-20, -15.001, -14, -101, -13.99999) => max]"));
        assertEquals("-13", subject.process("[LIST(-1.234, -0, 0, 901.24, -13) => min]"));
        assertEquals("-1", subject.process("[LIST(1, , 0, , -1) => min]"));
        assertEquals("0", subject.process("[LIST(, , , , ) => average]"));
        assertEquals("3.0", subject.process("[LIST(, 5, 4, 3, 2, 1, 5, 4, 3, 2, 1) => average]"));
        assertEquals("3.0", subject.process("[LIST(, 5, 4, 3, 2, 1, seven, nineteen, 5, 4, 3, 2, 1) => average]"));
        assertEquals("30,99,102", subject.process("[LIST(102,20,30,30,9,99) => ascending distinct sublist(2,4)]"));
        assertEquals("30,20,9", subject.process("[LIST(102,20,30,30,9,99) => descending distinct sublist(2,4)]"));
        assertEquals("ada,mad,ada,adam", subject.process("[LIST(,,adam,ada,,,,,mad,ada,,,) => pack reverse]"));
        assertEquals("high", subject.process("[LIST(every,mountain,high,every,valley,low) => item(2)]"));
        assertEquals("6", subject.process("[LIST(every,mountain,high,every,valley,low) => length]"));
        assertEquals("0", subject.process("[LIST(every,mountain,high,every,valley,low) => index(every)]"));
        assertEquals("low,mountain,valley", subject.process("[LIST(every,mountain,high,every,valley,low) =>" +
                                                            " ascending distinct sublist(2,4)]"));
        assertEquals("Tempe,San Francisco,Las Vegas,Santa Fe,Burbank",
                     subject.process("[LIST(Tempe,Burbank,San Francisco,Las Vegas,Toronto) =>" +
                                     " descending remove(0) insert(3,Santa Fe)]"));
        assertEquals("5,4,33,7,8,90,55,4433,5,2,5,11,2,33,7",
                     subject.process("[LIST(1,5,4,3,7,8,90,55,443,6,5,2,5,11,2,3,1,7,6,6,0,0) =>" +
                                     " removeItems(1,6,0) replace(3,33)]"));
        assertEquals("bah bah black sheep have you any wool yes sir yes sir three bags full",
                     subject.process("[LIST(bah,bah,black,sheep) =>" +
                                     " join(have,you,any,wool) join(yes,sir,yes,sir,three,bags,full) combine( ) ]"));
        assertEquals("bahbahblacksheephaveyouanywoolyessiryessirthreebagsfull",
                     subject.process("[LIST(bah,bah,black,sheep) =>" +
                                     " join(have,you,any,wool)" +
                                     " join(yes,sir,yes,sir,three,bags,full) " +
                                     " combine(\"\")]"));
        assertEquals("red,yellow,green,blue,black,white,purple,pink,orange",
                     subject.process("[LIST(red,yellow,green,blue,black,white) =>" +
                                     " union(purple,pink,orange,black,white)]"));
        assertEquals("black,white", subject.process("[LIST(red,yellow,green,blue,black,white) =>" +
                                                    " intersect(purple,pink,orange,black,white)]"));

        fixture = "[LIST(CDwindow-51c12978-15c6-4ea2-ab24-9aadefe370a8,CDwindow-adf7c26d-262e-4a6c-a7a3-1adb5d8406bb)" +
                  " => removeItems(CDwindow-51c12978-15c6-4ea2-ab24-9aadefe370a8) text]";
        assertEquals("CDwindow-adf7c26d-262e-4a6c-a7a3-1adb5d8406bb", subject.process(fixture));

        // support empty item replacement
        assertEquals("red,yellow,green,HELLO,HELLO,white",
                     subject.process("[LIST(red,yellow,green,,,white) => replaceItem(\\(empty\\),HELLO) text]"));
        assertEquals("red,yellow,green,white,HELLO,HELLO",
                     subject.process("[LIST(red,yellow,green,white,,) => replaceItem(\\(empty\\),HELLO) text]"));
        assertEquals("HELLO,red,yellow,green,white,HELLO,HELLO",
                     subject.process("[LIST(,red,yellow,green,white,,) => replaceItem(\\(empty\\),HELLO) text]"));
    }

    @Test
    public void processList_replica() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("{TAB}{TAB}", subject.process("[LIST({TAB}) => replica(2) text remove(\\,)]"));
        assertEquals("data [block] data [block] data [block]", subject.process("[LIST(data [block] ) =>" +
                                                                               " replica(3) text remove(\\,) trim]"));
        assertEquals("{[.]}{[.]}{[.]}{[.]}{[.]}", subject.process("[LIST({[.]}) => replica(5) text remove(\\,)]"));
    }

    @Test
    public void processList_empty() throws Exception {
        context.setData("new array", "START");
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("item1,item2", subject.process(context.replaceTokens("[LIST(${new array}) =>" +
                                                                          " append(item1) " +
                                                                          " append(item2) " +
                                                                          " removeItems(START)]")));
    }

    @Test
    public void processList_different_delim() throws Exception {

        context.setData("nexial.textDelim", "|");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("Bohn|Bames|Bim|Bane|Boby|Bacab|Borem",
                     subject.process("[LIST(John|James|Jim|Jane|Joby|Jacab|Jorem) => replace(J|B)]"));

        // using delim as parameter does not increase
        assertEquals("Smith\\|John|Bron\\|James|Bloc\\|Jim|Norris\\|Jane|Chen\\|Joby|Li\\|Jacab|Jorem", subject.process(
            "[LIST(Smith, John|Bron, James|Bloc, Jim|Norris, Jane|Chen, Joby|Li, Jacab|Jorem) => replace(, |\\|)]"));
        assertEquals("7", subject.process(
            "[LIST(Smith, John|Bron, James|Bloc, Jim|Norris, Jane|Chen, Joby|Li, Jacab|Jorem) =>" +
            " replace(, |\\|) count]"));
    }

    @Test
    public void processNumber() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("303", subject.process("[NUMBER(101) => add(202)]"));
        assertEquals("55", subject.process("[NUMBER(1) => add(2,3,4,5,6,7,8,9, 10)]"));
        assertEquals("0", subject.process("[NUMBER(55) => add(-1  ,-2, -3 ,-4,-5,-6,-7,-8,-9, -10)]"));
        assertEquals("-579972.74",
                     subject.process("[NUMBER(0) =>" +
                                     " add(-109264.45,-25553.78,-10573.98,-109264.45,-34541.66,10.00,-290784.42)]"));
        assertEquals("0", subject.process("[NUMBER(55) => minus(1  ,2, 3 ,4,5,6,7,8,9, 10)]"));
        assertEquals("0", subject.process("[NUMBER(0) => multiply(1  ,2, 3 ,4,5,6,7,8,9, 10)]"));
        assertEquals("3628800", subject.process("[NUMBER(1) => multiply(,2, 3 ,4,5,6,7,8,9, 10)]"));
        assertEquals("1", subject.process("[NUMBER(3628800) => divide(,2, 3 ,4,5,6,7,8,9, 10)]"));
        assertEquals("36", subject.process("[NUMBER(3628800) => divide(,2, 3 ,blah,5,6,7,8,this is not number, 10)]"));
        assertEquals("5.5", subject.process("[NUMBER(1) => average(2,3,4,5,6,7,8,9,10)]"));
        assertEquals("362884.1", subject.process("[NUMBER(3628800) =>" +
                                                 " average(,2, 3 ,blah,5,6,7,8,this is not number, 10)]"));
        assertEquals("0.03787878787878788", subject.process("[NUMBER(5) => divide(3,4.0,11)]"));
        assertEquals("363", subject.process("[NUMBER(0) =>" +
                                            " add(7,6) minus(2) multiply(11,51.2251) divide(17.04) whole]"));
        assertEquals("173921", subject.process("[NUMBER(173921.22) => roundTo(1)]"));
        assertEquals("173920", subject.process("[NUMBER(173921.22) => roundTo(10)]"));
        assertEquals("173900", subject.process("[NUMBER(173921.22) => roundTo(100)]"));
        assertEquals("174000", subject.process("[NUMBER(173921.22) => roundTo(1000)]"));
        assertEquals("170000", subject.process("[NUMBER(173921.22) => roundTo(10000)]"));
        assertEquals("200000", subject.process("[NUMBER(173921.22) => roundTo(100000)]"));
        assertEquals("173000", subject.process("[NUMBER(1201) => multiply(151,0.952) roundTo(1000)]"));
        assertEquals("172600", subject.process("[NUMBER(1201) => multiply(151,0.952) roundTo(100.00)]"));
        assertEquals("172650", subject.process("[NUMBER(1201) => multiply(151,0.952) roundTo(10.000)]"));
        assertEquals("172646.152", subject.process("[NUMBER(1201) => multiply(151,0.952) roundTo(0.001)]"));
        assertEquals("172646.2", subject.process("[NUMBER(1201) => multiply(151,0.952) roundTo(0.1)]"));
        assertEquals("173921.2", subject.process("[NUMBER(173921.22) => roundTo(0.0)]"));

        String result = subject.process("[NUMBER(0) => randomDigits(5)]");
        assertTrue(NumberUtils.isDigits(result));
        assertEquals(5, result.length());

        result = subject.process("[NUMBER(0) => randomDigits(0)]");
        assertEquals(0, result.length());

        result = subject.process("[NUMBER(0) => randomDecimal(5,2)]");
        assertTrue(NumberUtils.isCreatable(result));
        assertEquals(8, result.length());

        result = subject.process("[NUMBER(0) => randomInteger(5,19)]");
        assertTrue(NumberUtils.isCreatable(result));
        int resultInt = NumberUtils.toInt(result);
        assertTrue(resultInt >= 5 && resultInt <= 19);

        assertEquals("97", subject.process("[NUMBER(96.49341) => ceiling]"));
        assertEquals("96", subject.process("[NUMBER(96.49341) => floor]"));
        assertEquals("96.49", subject.process("[NUMBER(96.49341) => roundTo(0.00)]"));
        assertEquals("100", subject.process("[NUMBER(96.49341) => roundTo(000.00)]"));
    }

    @Test
    public void processNumber2() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String expected = "-1770.2978788893238";

        // tight argument list
        assertEquals(expected, subject.process("[NUMBER(92) =>" +
                                               " add(44,92,71.23,801.23,-1092)" +
                                               " minus(11,44.002)" +
                                               " multiply(15.01,0.902)" +
                                               " divide(5.0190,0.07092)]"));

        // space much!?
        assertEquals(expected, subject.process("[NUMBER(92) =>" +
                                               " add(44,   92,   71.23  ,   801.23,   -1092   )" +
                                               " minus(11    ,44.002)" +
                                               " multiply(15.01,      0.902)" +
                                               " divide( 5.0190 , 0.07092  )]"));
    }

    @Test
    public void processDate() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        long rightNow = new Date().getTime();
        String result = subject.process("[DATE(now) => text]");
        assertNotNull(result);
        Assert.assertNotEquals("[DATE(now) => text]", result);

        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        long resultTime = df.parse(result).getTime();
        assertTrue(resultTime - rightNow < 200);

        assertEquals("2017/10/19", subject.process("[DATE(2017/10/19,yyyy/MM/dd) => text]"));
        assertEquals("2019/05/01", subject.process("[DATE(2017/05/01,yyyy/MM/dd) => addYear(2)]"));
        assertEquals("2019/07/29", subject.process("[DATE(2017/05/01,yyyy/MM/dd) =>" +
                                                   " addYear(2) setMonth(8) setDOW(2) text]"));
        assertEquals("180.71654", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                                  "  format(h m s) pack " +
                                                  "  number divide(127.0) roundTo(0.00001)]"));
        assertEquals("180.72", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                               "  format(h m s) pack " +
                                               "  number divide(127.0) roundTo(0.01)]"));
        assertEquals("180.7", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                              "  format(h m s) pack " +
                                              "  number divide(127.0) roundTo(.1)]"));
        assertEquals("180.7165354331", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                                       "  format(h m s) pack " +
                                                       "  number divide(127.0) roundTo(1.1111111111)]"));
        assertEquals("181", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                            "  format(h m s) pack " +
                                            "  number divide(127.0) roundTo(1.)]"));
        assertEquals("180.7", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                              "  format(h m s) pack " +
                                              "  number divide(127.0) roundTo(1.0)]"));
        assertEquals("180", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                            "  format(h m s) pack " +
                                            "  number divide(127.0) roundTo(10.)]"));
        assertEquals("200", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                            "  format(h m s) pack " +
                                            "  number divide(127.0) roundTo(100.9999)]"));
        assertEquals("0", subject.process("[DATE(2016/06/02 14:29:51.133,yyyy/MM/dd HH:mm:ss.S) => " +
                                          "  format(h m s) pack " +
                                          "  number divide(127.0) roundTo(1000.53)]"));
    }

    @Test
    public void processDateMath() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("2018/04/01", subject.process("[DATE(2018/04/01,yyyy/MM/dd) => text]"));

        // days
        assertEquals("2018/04/06", subject.process("[DATE(2018/04/01,yyyy/MM/dd) => addDay(5) text]"));
        assertEquals("2018/03/27", subject.process("[DATE(2018/04/01,yyyy/MM/dd) => addDay(-5) text]"));
        assertEquals("2018/03/31", subject.process("[DATE(2018/04/01,yyyy/MM/dd) => addDay(-1) text]"));
        assertEquals("2018/04/01", subject.process("[DATE(2018/04/01,yyyy/MM/dd) =>" +
                                                   " addDay(-10) addDay(11) addDay(-1) text]"));
        assertEquals("2017/12/01", subject.process("[DATE(2018/04/01,yyyy/MM/dd) => addMonth(-4) text]"));
        assertEquals("2017/04/01", subject.process("[DATE(2018/04/01,yyyy/MM/dd) => addMonth(-12) text]"));
        assertEquals("2018/04/30", subject.process("[DATE(2018/04/01,yyyy/MM/dd) =>" +
                                                   " addMonth(-12) addMonth(13) addDay(-1) text]"));

        // DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        // long resultTime = df.parse(result).getTime();
        // Assert.assertTrue(resultTime - rightNow < 200);
        //
        // fixture = "[DATE(2017/10/19,yyyy/MM/dd) => text]";
        // result = subject.process(fixture);
        // Assert.assertEquals("2017/10/19", result);
        //
        // fixture = "[DATE(2017/05/01,yyyy/MM/dd) => addYear(2)]";
        // result = subject.process(fixture);
        // Assert.assertEquals("2019/05/01", result);
        //
        // fixture = "[DATE(2017/05/01,yyyy/MM/dd) => addYear(2) setMonth(8) setDOW(2) text]";
        // result = subject.process(fixture);
        // Assert.assertEquals("2019/07/29", result);
    }

    @Test
    public void processJson() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String jsonFile = ResourceUtils.getResourceFilePath(resourcePath + className + "1.json");
        String result = subject.process("[JSON(" + jsonFile + ") => text pack]");
        assertTrue(result.contains("\"GlossTerm\":\"StandardGeneralizedMarkupLanguage\""));
        assertEquals("[\"GML\",\"XML\"]", subject.process(
            "[JSON(" + jsonFile + ") => extract(glossary.GlossDiv.GlossList.GlossEntry.GlossDef.GlossSeeAlso)]"));
        assertEquals("SGML", subject.process("[JSON(" + jsonFile + ") =>" +
                                             " extract(glossary.GlossDiv.GlossList.GlossEntry.ID)]"));
        assertEquals("XML", subject.process("[JSON(" + jsonFile + ") =>" +
                                            " extract(glossary.GlossDiv.GlossList.GlossEntry) " +
                                            " extract(GlossDef.GlossSeeAlso.XML)]"));

        jsonFile = ResourceUtils.getResourceFilePath(resourcePath + className + "2.json");

        result = subject.process("[JSON(" + jsonFile + ") => extract(gamt)]");
        assertEquals("3415.829994", result);
        String gamt = result;

        assertEquals(gamt, subject.process("[JSON(" + jsonFile + ") =>" +
                                           " extract(details.amt1) list sum roundTo(0.000001)]"));
        assertEquals("7", subject.process("[JSON(" + jsonFile + ") => extract(details.id) list length]"));
        assertEquals("A1", subject.process("[JSON(" + jsonFile + ") =>" +
                                           " extract(details.code) list distinct ascending first between(\",\")]"));
        assertEquals("420", subject.process("[JSON(" + jsonFile + ") =>" +
                                            " extract(details.minutes) list distinct descending last]"));
        assertEquals("600.0", subject.process("[JSON(" + jsonFile + ") =>" +
                                              " extract(details[date1=REGEX:2016-05-0\\.+].minutes) list average]"));
        assertEquals("2", subject.process("[JSON(" + jsonFile + ") => " +
                                          "extract(details[date1=REGEX:2016-05-0\\.+].minutes => count)]"));
        assertEquals("3300", subject.process("[JSON(" + jsonFile + ") => " +
                                             "extract(details[date1=REGEX:2016-05-1\\.+].minutes => sum)]"));
        assertEquals("7th", subject.process("[JSON(" + jsonFile + ") => replace(details[type1=A2].type1,7th)" +
                                            "                           extract(details[type1=7th].type1)]"));
        assertNull(subject.process("[JSON(" + jsonFile + ") => extract(error)]"));
        assertFalse(StringUtils.contains(subject.process("[JSON(" + jsonFile + ") => pack]"), "null"));

        String extractSeventh = "extract(details[type1=A2].type1)";
        assertEquals("A2", subject.process("[JSON(" + jsonFile + ") => " + extractSeventh + "]"));
        assertNull(subject.process("[JSON(" + jsonFile + ") =>" +
                                   " remove(details[type1=A2].type1) " + extractSeventh + "]"));

        // count the json elements
        assertEquals("10", subject.process("[JSON(" + jsonFile + ") => count(details[code=NP]) add(1) multiply(10)]"));

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
        assertEquals("Request method [PATCH] not supported: just not my style", result);
    }

    @Test
    public void process_expression_with_square_bracket() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = "sum = " +
                         "[[LIST(2441117.97,4750496.50,890528.83,679465.00,2441117.97,6320490.33,8761608.30) => sum]]";
        assertEquals("sum = [26284824.90]", subject.process(fixture));

        fixture = "{\"product\" : [{" +
                  "   \"name\" : \"Mobiles\"," +
                  "   \"quantity\" : [LIST(247,475,89,67,17,49, 8) => sum]" +
                  "}," +
                  "   \"name\" : \"Laptops\"," +
                  "   \"quantity\" : [LIST(24,500,78,65,88,100,10) => sum]" +
                  "}" +
                  "]}";
        assertEquals("{\"product\" : [{" +
                     "   \"name\" : \"Mobiles\"," +
                     "   \"quantity\" : 952" +
                     "}," +
                     "   \"name\" : \"Laptops\"," +
                     "   \"quantity\" : 865" +
                     "}" +
                     "]}",
                     subject.process(fixture));
    }

    @Test
    public void processJsonAdd() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String jsonFile = ResourceUtils.getResourceFilePath(resourcePath + className + "13.json");
        String fixture = "[JSON(" + jsonFile + ") =>" +
                         " addOrReplace(office.address,\"932b 32nd Street\\, Big City\\, State of Confusion\")" +
                         " text]";

        // replace string with string
        String result = subject.process(fixture);
        assertEquals("{\"office\":{" +
                     "\"code\":\"AEF\"," +
                     "\"address\":\"932b 32nd Street, Big City, State of Confusion\"," +
                     "\"description\":\"Advanced External Partnership\"" +
                     "}}", result);

        // replace string with array
        fixture = "[JSON(" + jsonFile + ") =>" +
                  " addOrReplace(office.address,932b 32nd Street\\, Big City\\, State of Confusion)" +
                  " text]";
        result = subject.process(fixture);
        assertEquals("{\"office\":{" +
                     "\"code\":\"AEF\"," +
                     "\"address\":[\"932b 32nd Street\",\"Big City\",\"State of Confusion\"]," +
                     "\"description\":\"Advanced External Partnership\"" +
                     "}}", result);

        // replace string with null
        fixture = "[JSON(" + jsonFile + ") =>" +
                  " addOrReplace(office.address,null)" +
                  " text]";
        result = subject.process(fixture);
        assertEquals("{\"office\":{" +
                     "\"code\":\"AEF\"," +
                     "\"address\":null," +
                     "\"description\":\"Advanced External Partnership\"" +
                     "}}", result);

        // replace object
        fixture = "[JSON(" + jsonFile + ") =>" +
                  " addOrReplace(office,{\"address\":{\"optional\":true}})" +
                  " text]";
        result = subject.process(fixture);
        assertEquals("{\"office\":{" +
                     "\"code\":\"AEF\"," +
                     "\"address\":{\"optional\":true}," +
                     "\"description\":\"Advanced External Partnership\"" +
                     "}}", result);
    }

    @Test
    public void processJsonAdd_1() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String jsonFile = ResourceUtils.getResourceFilePath(resourcePath + className + "13.json");
        String fixture = "[JSON(" + jsonFile + ") =>" +
                         " addOrReplace( ,author: me and myself)" +
                         " text]";

        // replace string with string
        String result = subject.process(fixture);
        assertEquals("{" +
                     "\"author\":\"me and myself\"," +
                     "\"office\":{" +
                     "\"code\":\"AEF\"," +
                     "\"address\":\"123 Outthere Ave, SeeTee\"," +
                     "\"description\":\"Advanced External Partnership\"" +
                     "}}",
                     result);
    }

    @Test
    public void processJson_save() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String jsonFile = ResourceUtils.getResourceFilePath(resourcePath + className + "13.json");
        String output = StringUtils.replace(jsonFile, ".json", "-new.json");
        assertNotNull(output);
        FileUtils.deleteQuietly(new File(output));

        String fixture = "[JSON(" + jsonFile + ") =>" +
                         " addOrReplace(office.address,\"932b 32nd Street\\, Big City\\, State of Confusion\")" +
                         " save(" + output + ",true) ]";

        // replace string with string
        subject.process(fixture);
        assertEquals("{\"office\":{" +
                     "\"code\":\"AEF\"," +
                     "\"address\":\"932b 32nd Street, Big City, State of Confusion\"," +
                     "\"description\":\"Advanced External Partnership\"" +
                     "}}", FileUtils.readFileToString(new File(output), DEF_FILE_ENCODING));

        // replace string with array
        fixture = "[JSON(" + jsonFile + ") =>" +
                  " addOrReplace(office.address,932b 32nd Street\\, Big City\\, State of Confusion)" +
                  " save(" + output + ",true) ]";
        subject.process(fixture);
        assertEquals("{\"office\":{" +
                     "\"code\":\"AEF\"," +
                     "\"address\":\"932b 32nd Street, Big City, State of Confusion\"," +
                     "\"description\":\"Advanced External Partnership\"" +
                     "}," +
                     "\"office\":{" +
                     "\"code\":\"AEF\"," +
                     "\"address\":[\"932b 32nd Street\",\"Big City\",\"State of Confusion\"]," +
                     "\"description\":\"Advanced External Partnership\"" +
                     "}}", FileUtils.readFileToString(new File(output), DEF_FILE_ENCODING));

    }

    @Test
    public void processJson_save2() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String jsonFile = ResourceUtils.getResourceFilePath(resourcePath + className + "14.json");
        String output = StringUtils.replace(jsonFile, ".json", "-new.json");
        assertNotNull(output);
        FileUtils.deleteQuietly(new File(output));

        String fixture = "[JSON(" + jsonFile + ") => replace(age,year) save(" + output + ",true) ]";

        // replace string with string
        subject.process(fixture);
        assertEquals("[{\"Name\":\"Jim\"},{\"Name\":\"Natalie\"},{\"Name\":\"Sam\"}]",
                     FileUtils.readFileToString(new File(output), DEF_FILE_ENCODING));

        fixture = "[JSON(" + jsonFile + ") => replace(age,year) save(" + output + ",true) ]";
        subject.process(fixture);
        assertEquals("[" +
                     "{\"Name\":\"Jim\"},{\"Name\":\"Natalie\"},{\"Name\":\"Sam\"}," +
                     "{\"Name\":\"Jim\"},{\"Name\":\"Natalie\"},{\"Name\":\"Sam\"}" +
                     "]",
                     FileUtils.readFileToString(new File(output), DEF_FILE_ENCODING));

        fixture = "[JSON([59,47,13,1]) => replace(age,year) save(" + output + ",true) ]";
        subject.process(fixture);
        assertEquals("[" +
                     "{\"Name\":\"Jim\"},{\"Name\":\"Natalie\"},{\"Name\":\"Sam\"}," +
                     "{\"Name\":\"Jim\"},{\"Name\":\"Natalie\"},{\"Name\":\"Sam\"}," +
                     "59,47,13,1" +
                     "]",
                     FileUtils.readFileToString(new File(output), DEF_FILE_ENCODING));
    }

    @Test
    public void processJson_select() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String jsonFile = ResourceUtils.getResourceFilePath(resourcePath + className + "2.json");

        assertEquals("details.minutes => sum,4500\r\n" +
                     "details.amt1 => sum,3415.8299937894\r\n" +
                     "details[code=A1].key0019,[8.0,12.0,7.0]\r\n",
                     subject.process("[JSON(" + jsonFile + ") => select(" +
                                     "details.minutes => sum," +
                                     "details.amt1 => sum," +
                                     "details[code=A1].key0019" +
                                     ") text]"));

        assertEquals("details.code              => distinct,[\"A1\",\"B1\"]\r\n" +
                     "details.key0019           => average,10.714285714285714\r\n" +
                     "details[code=B1].amt1     => max,372.636\r\n" +
                     "details[key0029=false].id => count,2\r\n",
                     subject.process("[JSON(" + jsonFile + ")      => select(" +
                                     "   details.code              => distinct \n" +
                                     "   details.key0019           => average  \n" +
                                     "   details[code=B1].amt1     => max      \n" +
                                     "   details[key0029=false].id => count\n" +
                                     ") text]"));

    }

    @Test
    public void processJson_keys() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String json = "{\n" +
                      "  \"shipping_address\": [\n" +
                      "    {\n" +
                      "      \"street_address\": \"1600 Pennsylvania Avenue NW\",\n" +
                      "      \"city\": \"Washington\",\n" +
                      "      \"state\": \"DC\",\n" +
                      "      \"labels\": [\n" +
                      "        \"east coast\",\n" +
                      "        {\n" +
                      "          \"residents\": [\n" +
                      "            { \"name\": \"Mr. President\", \"gender\": \"M\" },\n" +
                      "            { \"name\": \"Mrs. President\", \"gender\": \"F\" },\n" +
                      "            { \"name\": \"First Dog\", \"gender\": \"F\" },\n" +
                      "            null\n" +
                      "          ]\n" +
                      "        },\n" +
                      "        null,\n" +
                      "        \"gated\"\n" +
                      "      ]\n" +
                      "    },\n" +
                      "    {\n" +
                      "      \"country\": \"USA\",\n" +
                      "      \"street_address\": \"1733 Washington Avenue\",\n" +
                      "      \"city\": \"Glenoak\",\n" +
                      "      \"state\": \"CA\"\n" +
                      "    }\n" +
                      "  ],\n" +
                      "  \"billing_address\": {\n" +
                      "    \"address1\": \"1st Street SE\",\n" +
                      "    \"address2\": null,\n" +
                      "    \"city\": \"Seeti\",\n" +
                      "    \"county\": \"Abrehiban\",\n" +
                      "    \"state\": \"DC\",\n" +
                      "    \"zipcode\": \"10541\",\n" +
                      "    \"country\": \"USA\"\n" +
                      "  },\n" +
                      "  \"tags\": [ \"electronics\", \"hi-tech\", \"android\" ]\n" +
                      "}";

        assertEquals("gender name",
                     subject.process("[JSON(" + json + ") => " +
                                     "   keys(shipping_address.labels.residents[2]) " +
                                     "   ascending " +
                                     "   combine( )" +
                                     "]"));
        assertEquals("0",
                     subject.process("[JSON(" + json + ") => keys(shipping_address.labels.residents[3]) size]"));
        assertEquals("residents", subject.process("[JSON(" + json + ") => keys(shipping_address.labels[1])]"));
        assertEquals("city,country,state,street_address",
                     subject.process("[JSON(" + json + ") => keys(shipping_address[1]) ascending]"));
        assertEquals("address1,address2,city,country,county,state,zipcode",
                     subject.process("[JSON(" + json + ") => keys(billing_address) ascending]"));
    }

    @Test
    public void processJson_pack_vs_compact() throws Exception {
        // test: first-level null
        String jsonString = "{ \"name\": \"Me and Myself\", \"data1\": \"\", \"data2\": null }";
        System.out.println("jsonString   = " + jsonString);

        String expPack = "[JSON(" + jsonString + ") => pack]";
        String expCompactKeepEmpty = "[JSON(" + jsonString + ") => compact(false)]";
        String expCompactRemoveEmpty = "[JSON(" + jsonString + ") => compact(true)]";

        ExpressionProcessor subject = new ExpressionProcessor(context);
        assertEquals("{\n" +
                     "  \"name\": \"Me and Myself\",\n" +
                     "  \"data1\": \"\"\n" +
                     "}", subject.process(expPack));
        assertEquals("{\"name\":\"Me and Myself\",\"data1\":\"\"}", subject.process(expCompactKeepEmpty));
        assertEquals("{\"name\":\"Me and Myself\"}", subject.process(expCompactRemoveEmpty));

        // test: first-level empty json object
        jsonString = "{ \"name\": \"Me and Myself\", \"data1\": \"\", \"data2\": { } }";
        System.out.println("jsonString   = " + jsonString);

        expPack = "[JSON(" + jsonString + ") => pack]";
        expCompactKeepEmpty = "[JSON(" + jsonString + ") => compact(false)]";
        expCompactRemoveEmpty = "[JSON(" + jsonString + ") => compact(true)]";

        subject = new ExpressionProcessor(context);
        assertEquals("{\n" +
                     "  \"name\": \"Me and Myself\",\n" +
                     "  \"data1\": \"\",\n" +
                     "  \"data2\": {}\n" +
                     "}", subject.process(expPack));
        assertEquals("{\"name\":\"Me and Myself\",\"data1\":\"\"}", subject.process(expCompactKeepEmpty));
        assertEquals("{\"name\":\"Me and Myself\"}", subject.process(expCompactRemoveEmpty));

        // test: nest-level null
        jsonString = "{ \"name\": \"Me and Myself\", \"data1\": \"\", \"data2\": { \"data3\": null } }";
        System.out.println("jsonString   = " + jsonString);

        expPack = "[JSON(" + jsonString + ") => pack]";
        expCompactKeepEmpty = "[JSON(" + jsonString + ") => compact(false)]";
        expCompactRemoveEmpty = "[JSON(" + jsonString + ") => compact(true)]";

        subject = new ExpressionProcessor(context);
        assertEquals("{\n" +
                     "  \"name\": \"Me and Myself\",\n" +
                     "  \"data1\": \"\",\n" +
                     "  \"data2\": {}\n" +
                     "}", subject.process(expPack));
        assertEquals("{\"name\":\"Me and Myself\",\"data1\":\"\"}", subject.process(expCompactKeepEmpty));
        assertEquals("{\"name\":\"Me and Myself\"}", subject.process(expCompactRemoveEmpty));

        // test: nest-level empty json object
        jsonString = "{ \"name\": \"Me and Myself\", \"data1\": \"\", \"data2\": { \"data3\": { \"data4\": { } } } }";
        System.out.println("jsonString   = " + jsonString);

        expPack = "[JSON(" + jsonString + ") => pack]";
        expCompactKeepEmpty = "[JSON(" + jsonString + ") => compact(false)]";
        expCompactRemoveEmpty = "[JSON(" + jsonString + ") => compact(true)]";

        subject = new ExpressionProcessor(context);
        assertEquals("{\n" +
                     "  \"name\": \"Me and Myself\",\n" +
                     "  \"data1\": \"\",\n" +
                     "  \"data2\": {\n" +
                     "    \"data3\": {\n" +
                     "      \"data4\": {}\n" +
                     "    }\n" +
                     "  }\n" +
                     "}",
                     subject.process(expPack));
        assertEquals("{\"name\":\"Me and Myself\",\"data1\":\"\"}", subject.process(expCompactKeepEmpty));
        assertEquals("{\"name\":\"Me and Myself\"}", subject.process(expCompactRemoveEmpty));

        // test: empty array
        jsonString = "{ \"name\": \"Me and Myself\", \"data1\": \"\", \"data2\": [] }";
        System.out.println("jsonString   = " + jsonString);

        expPack = "[JSON(" + jsonString + ") => pack]";
        expCompactKeepEmpty = "[JSON(" + jsonString + ") => compact(false)]";
        expCompactRemoveEmpty = "[JSON(" + jsonString + ") => compact(true)]";

        subject = new ExpressionProcessor(context);
        assertEquals("{\n" +
                     "  \"name\": \"Me and Myself\",\n" +
                     "  \"data1\": \"\",\n" +
                     "  \"data2\": []\n" +
                     "}", subject.process(expPack));
        assertEquals("{\"name\":\"Me and Myself\",\"data1\":\"\"}", subject.process(expCompactKeepEmpty));
        assertEquals("{\"name\":\"Me and Myself\"}", subject.process(expCompactRemoveEmpty));

        // test: array with null
        jsonString = "{ \"name\": \"Me and Myself\", \"data1\": \"\", \"data2\": [null,null] }";
        System.out.println("jsonString   = " + jsonString);

        expPack = "[JSON(" + jsonString + ") => pack]";
        expCompactKeepEmpty = "[JSON(" + jsonString + ") => compact(false)]";
        expCompactRemoveEmpty = "[JSON(" + jsonString + ") => compact(true)]";

        subject = new ExpressionProcessor(context);
        assertEquals("{\n" +
                     "  \"name\": \"Me and Myself\",\n" +
                     "  \"data1\": \"\",\n" +
                     "  \"data2\": [\n" +
                     "    null,\n" +
                     "    null\n" +
                     "  ]\n" +
                     "}", subject.process(expPack));
        assertEquals("{\"name\":\"Me and Myself\",\"data1\":\"\"}", subject.process(expCompactKeepEmpty));
        assertEquals("{\"name\":\"Me and Myself\"}", subject.process(expCompactRemoveEmpty));

        // test: array with null and empty
        jsonString = "{ \"name\": \"Me and Myself\", \"data1\": \"\", \"data2\": [null,\"\",null] }";
        System.out.println("jsonString   = " + jsonString);

        expPack = "[JSON(" + jsonString + ") => pack]";
        expCompactKeepEmpty = "[JSON(" + jsonString + ") => compact(false)]";
        expCompactRemoveEmpty = "[JSON(" + jsonString + ") => compact(true)]";

        subject = new ExpressionProcessor(context);
        assertEquals("{\n" +
                     "  \"name\": \"Me and Myself\",\n" +
                     "  \"data1\": \"\",\n" +
                     "  \"data2\": [\n" +
                     "    null,\n" +
                     "    \"\",\n" +
                     "    null\n" +
                     "  ]\n" +
                     "}",
                     subject.process(expPack));
        assertEquals("{\"name\":\"Me and Myself\",\"data1\":\"\",\"data2\":[\"\"]}",
                     subject.process(expCompactKeepEmpty));
        assertEquals("{\"name\":\"Me and Myself\"}", subject.process(expCompactRemoveEmpty));
    }

    @Test
    public void processJson_pack2() throws Exception {
        String jsonString = "{\n" +
                            "  \"config\": {\n" +
                            "    \"location1\": [\n" +
                            "      {\n" +
                            "        \"code\": \"CA\",\n" +
                            "        \"state\": \"California\"\n" +
                            "      }\n" +
                            "    ],\n" +
                            "    \"location2\": null,\n" +
                            "    \"config1\": {\n" +
                            "      \"client\": 12345,\n" +
                            "      \"active\": true\n" +
                            "    },\n" +
                            "    \"config2\": [ ],\n" +
                            "    \"dataListing\": [\n" +
                            "      \"\",\n" +
                            "      \"\",\n" +
                            "      null,\n" +
                            "      { }\n" +
                            "    ],\n" +
                            "    \"config3\": null\n" +
                            "  }\n" +
                            "}";
        System.out.println("before pack = " + jsonString);

        ExpressionProcessor subject = new ExpressionProcessor(context);
        String afterPack = subject.process("[JSON(" + jsonString + ") => pack]");
        System.out.println("after pack  = " + afterPack);

        assertFalse(afterPack.contains("\"location2\""));
        assertTrue(afterPack.contains("\n"));
    }

    @Test
    public void processXml() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String xmlFile = ResourceUtils.getResourceFilePath(resourcePath + className + "3.xml");
        String result = subject.process("[XML(" + xmlFile + ") => text normalize]");
        assertTrue(result.contains("<description>Microsoft's .NET initiative is explored in detail in this " +
                                   "deep programmer's reference. </description>"));

        String fixture = "Hey you know, [XML(" +
                         xmlFile +
                         ") => " +
                         "extract(//book[./price=5.95 and starts-with\\(./publish_date\\,'2001-09'\\)]/title/text\\(\\))]" +
                         " is a great book for the year at a great price of $5.95!";
        assertEquals("Hey you know, The Sundered Grail is a great book for the year at a great price of $5.95!",
                     subject.process(fixture));

        assertEquals("12", subject.process("[XML(" + xmlFile + ") => count(//catalog/book)]"));

        result = subject.process("[XML(" + xmlFile + ") => clear(/catalog/book[./genre=\"Fantasy\"]) ]");
        assertTrue(result.contains("<book id=\"bk102\" />"));
        assertTrue(result.contains("<book id=\"bk103\" />"));
        assertTrue(result.contains("<book id=\"bk104\" />"));
        assertTrue(result.contains("<book id=\"bk105\" />"));

        result = subject.process("[XML(" + xmlFile + ") => remove(/catalog/book[./genre=\"Fantasy\"]) ]");
        assertFalse(result.contains("id=\"bk102\""));
        assertFalse(result.contains("id=\"bk103\""));
        assertFalse(result.contains("id=\"bk104\""));
        assertFalse(result.contains("id=\"bk105\""));

        assertEquals(result, subject.process("[XML(" + xmlFile + ") => delete(/catalog/book[./genre=\"Fantasy\"]) ]"));
    }

    @Test
    public void processXml_with_prolog() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        String xmlFile = ResourceUtils.getResourceFilePath(resourcePath + className + "4.xml");
        String result = subject.process("[XML(" + xmlFile + ") => text normalize]");
        assertTrue(result.contains("<double xmlns=\"http://www.webserviceX.NET/\">212</double>"));

        assertEquals("212", subject.process("[XML(" + xmlFile + ") =>" +
                                            " extract(/*[local-name\\(\\)='double']/text\\(\\))]"));
    }

    @Test
    public void processConfig() throws Exception {
        String propertiesFile = ResourceUtils.getResourceFilePath(resourcePath + className + "4.txt");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("John", subject.process("[CONFIG(" + propertiesFile + ") => value(FirstName)]"));
        assertEquals("2", subject.process("[CONFIG(" + propertiesFile + ") => keys length]"));
        assertEquals("New York", subject.process("[CONFIG(" + propertiesFile + ") => " +
                                                 " set(State,California) " +
                                                 " save(" + propertiesFile + ") " +
                                                 " set(State,New York) " +
                                                 " value(State)" +
                                                 "]"));
        assertEquals("8", subject.process("[CONFIG(Title=Engineer) => value(Title) length]"));
        assertNull(subject.process("[CONFIG(" + propertiesFile + ") =>" +
                                   " remove(State) save(" + propertiesFile + ") value(State)]"));

        String prop2 = ResourceUtils.getResourceFilePath(resourcePath + className + "4.properties");
        assertEquals("AL=128191\n" +
                     "CA=199182\n" +
                     "CO=230190\n" +
                     "CT=99320\n" +
                     "FL=352388\n" +
                     "NY=230283\n" +
                     "RI=2389811\n",
                     subject.process("[CONFIG(" + prop2 + ") => ascending()]"));
        assertEquals("NY=230283\n" +
                     "FL=352388\n" +
                     "CO=230190\n" +
                     "CA=199182\n" +
                     "AL=128191\n",
                     subject.process("[CONFIG(" + prop2 + ") => remove(CT) remove(RI) descending()]"));
    }

    @Test
    public void processConfig_keys() throws Exception {
        String propertiesFile = ResourceUtils.getResourceFilePath(resourcePath + className + "4.txt");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("FirstName,LastName", subject.process("[CONFIG(" + propertiesFile + ") => keys]"));
    }

    @Test
    public void processINI() throws Exception {
        ExpressionProcessor subject = new ExpressionProcessor(context);

        // to avoid forceful recompile between tests, let's copy `iniFile` to another as test subject
        String iniFile = ResourceUtils.getResourceFilePath(resourcePath + className + "5.ini");
        assertNotNull(iniFile);
        String testFile = iniFile + "COPY";
        FileUtils.copyFile(new File(iniFile), new File(testFile));

        assertEquals("server94a", subject.process("[INI(" + testFile + ") =>" +
                                                  " set(COMPANY_NAME,SERVER_NAME,server94a)" +
                                                  " save(" + testFile + ")" +
                                                  " value(COMPANY_NAME,SERVER_NAME)" +
                                                  "]"));
        assertEquals("mapi32.dll", subject.process("[INI(" + testFile + ") => value(Mail,CMCDLLNAME32)]"));
        assertNotNull(subject.process("[INI(" + testFile + ") => text]"));

        System.out.println(subject.process("[INI(" + testFile + ") => values(COMPANY_NAME)]"));
        assertEquals("7", subject.process("[INI(" + testFile + ") => values(COMPANY_NAME) length]"));
        assertFalse(subject.process("[INI(" + testFile + ") => remove(PRODUCT_2345,*) save(" + testFile + ")]")
                           .contains("PRODUCT_2345"));

        String newFile = ResourceUtils.getResourceFilePath(resourcePath + className + "6.ini");
        assertTrue(subject.process("[INI(" + testFile + ") =>" +
                                   " merge(" + newFile + ") save(" + testFile + ")]")
                          .contains("PRODUCT_2345"));

        assertTrue(subject.process("[INI(" + testFile + ") =>" +
                                   " merge([COMPANY_NAME]\n TEST=1234) save(" + testFile + ")]")
                          .contains("TEST"));
        assertFalse(subject.process("[INI(" + testFile + ") =>" +
                                    " remove(PRODUCT_2345,*) save(" + testFile + ")]")
                           .contains("PRODUCT_2345="));
        assertEquals("PROJECT_1234", subject.process("[INI(" + testFile + ") => remove(COMPANY_NAME,NAME)" +
                                                     "                          save(" + testFile + ")" +
                                                     "                          value(COMPANY_NAME,PROJECT)]"));
        assertNotNull(subject.process("[INI(" + testFile + ") =>" +
                                      " remove(COMPANY_NAME,TEST) save(" + testFile + ")]"));
        assertNotNull(subject.process("[INI(" + testFile + ") =>" +
                                      " remove(COMPANY_NAME,PROJECT) save(" + testFile + ")]"));
        assertTrue(subject.process("[INI(" + testFile + ") =>" +
                                   " newComment(Adding new comment here) save (" + testFile + ") comment]")
                          .contains("Adding new comment here"));
        assertEquals("1234", subject.process("[INI([COMPANY_NAME]\n TEST=1234) => value(COMPANY_NAME,TEST)]"));
        assertEquals("1234", subject.process("[INI([NEW]\n TEST=1234) => value(NEW,TEST)]"));
    }

    @Test
    public void processCSV() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "7.csv");
        assertNotNull(csvFile);

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String result = subject.process("[CSV(" + csvFile + ") => text]");
        assertEquals(StringUtils.replace(FileUtils.readFileToString(new File(csvFile), "UTF-8"), "\r\n", "\n"),
                     StringUtils.replace(result, "\r\n", "\n"));

        result = subject.process("[CSV(" + csvFile + ") => parse(delim=\\,|header=true) text]");
        assertEquals(StringUtils.replace(FileUtils.readFileToString(new File(csvFile), "UTF-8"), "\r\n", "\n"),
                     StringUtils.replace(result, "\r\n", "\n"));

        assertEquals("david@contoso.com,David,Longmuir,David Longmuir,IT Manager,Information Technology," +
                     "123453,123-555-1213,123-555-6643,123-555-9823,1 Microsoft way,Redmond,Wa,98052," +
                     "United States",
                     subject.process("[CSV(" + csvFile + ") => " +
                                     " parse(delim=\\,|header=true)" +
                                     " row(2)" +
                                     " text]"));
        assertEquals("5", subject.process("[CSV(" + csvFile + ") => " +
                                          " parse(delim=\\,|header=true)" +
                                          " filter(Job Title = IT Manager)" +
                                          " rowCount]"));
        assertEquals("6", subject.process("[CSV(" + csvFile + ") => " +
                                          " parse(delim=\\,|header=true)" +
                                          " filter(Job Title = IT Manager)" +
                                          " parse(header=false)" +
                                          " rowCount" +
                                          "]"));
        assertEquals("2", subject.process("[CSV(" + csvFile + ") => " +
                                          " parse(delim=\\,|header=true)" +
                                          " filter(Job Title = IT Manager)" +
                                          " removeColumns(Country or Region|Office Phone|Mobile Phone|Fax)" +
                                          " filter(Last Name match \\w{2\\,5}) " +
                                          " rowCount ]"));
        assertEquals("1", subject.process("[CSV(" + csvFile + ") => " +
                                          " parse(delim=\\,|header=true)" +
                                          " filter(First Name in [David|Cynthia])" +
                                          " removeColumns(Country or Region|Office Phone|Mobile Phone|Fax)" +
                                          " filter(Last Name match \\w{2\\,5})" +
                                          " store(myData)" +
                                          " rowCount ]"));
        assertEquals("Cynthia", subject.process("[CSV(myData) => column(First Name)]"));
    }

    @Test
    public void processCSV2() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "8.csv");

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
        assertNotNull(junk2Content);
        assertEquals(
            "User Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\r\n" +
            "chris@contoso.com,Green,Chris Green,Manager,Finance,234232,312-490-4891,123-555-6641,123-555-9821,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
            "ben@contoso.com,Andrews,Ben Andrews,Director,Information Technology,362342,312-492-9910,123-555-6642,123-555-9822,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
            "david@contoso.com,Longmuir,David Longmuir,Vice President,Accounting,6795647,312-490-5565,312-490-5125,123-555-9823,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
            "cynthia@contoso.com,Carey,Cynthia Carey,Senior Director,Information Technology,112324,312-490-1192,123-555-6644,123-555-9824,1 Microsoft way,Redmond,WA,98052,United States\r\n" +
            "melissa@contoso.com,MacBeth,Melissa MacBeth,Supervisor,Human Resource,345345,312-490-3892,123-555-6645,123-555-9825,1 Microsoft way,Redmond,WA,98052,United States",
            junk2Content);

        FileUtils.deleteQuietly(new File(tmp));
    }

    @Test
    public void processCSV3() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "8.csv");

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
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "8.csv");

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
                         is(equalTo("5.0"))));
    }

    @Test
    public void processCSV5() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "8.csv");
        assertNotNull(csvFile);

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
        assertNotNull(tmpFile);
        assertTrue(tmpFile.length() > 1010);

        // transpose back
        String reTransposed = subject.process("[CSV(" + tmp + ") => " +
                                              " parse(delim=\\,|header=false) " +
                                              " transpose " +
                                              " text" +
                                              "]");
        assertEquals(StringUtils.replace(FileUtils.readFileToString(new File(csvFile), "UTF-8"), "\r\n", "\n"),
                     reTransposed);

        String output = subject.process("[CSV(" + csvFile + ") => " +
                                        " parse(delim=\\,|header=true)" +
                                        " ascii-table]");
        System.out.println("output = \n" + output);

        FileUtils.deleteQuietly(tmpFile);
    }

    @Test
    public void processCSV6() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "9a.csv");
        assertNotNull(csvFile);

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String fixture = subject.process("[CSV(" + csvFile + ") => " +
                                         " parse(header=true)" +
                                         " remove-columns(User Name,Department)" +
                                         " text" +
                                         "]");
        System.out.println("fixture = " + fixture);

        // check that double quotes are retained.
        assertEquals(
            "First Name,Last Name,Display Name,Job Title,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\n" +
            "Chris,Green,Chris Green,Manager,234232,312-490-4891,123-555-6641,123-555-9821,1 Microsoft way,Redmond,WA,98052,United States\n" +
            "Ben,Andrews,Ben Andrews,Director,362342,312-492-9910,123-555-6642,123-555-9822,1 Microsoft way,Redmond,WA,98052,United States\n" +
            "David,Longmuir,David Longmuir,\"Vice President, Quality\",6795647,312-490-5565,312-490-5125,123-555-9823,1 Microsoft way,Redmond,WA,98052,United States\n" +
            "Cynthia,Carey,Cynthia Carey,Senior Director,112324,312-490-1192,123-555-6644,123-555-9824,1 Microsoft way,Redmond,WA,98052,United States\n" +
            "Melissa,MacBeth,Melissa MacBeth,Supervisor,345345,312-490-3892,123-555-6645,123-555-9825,1 Microsoft way,Redmond,WA,98052,United States",
            fixture);

        csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "10.csv");
        assertNotNull(csvFile);

        String outputFile = StringUtils.substringBeforeLast(csvFile, separator) + separator + "output-10.csv";
        context.setData("output", outputFile);
        subject = new ExpressionProcessor(context);

        fixture = subject.process("[CSV(" + csvFile + ") =>" +
                                  " parse(header=true,keepQuote=true) save(" + outputFile + ")]");
        System.out.println(fixture);
        assertNotNull(fixture);

        String saved = FileUtils.readFileToString(new File(outputFile), DEF_FILE_ENCODING);
        assertEquals("1107-000,WRITERS ASSTS & P.A.'S\n" +
                     "1204-000,PRODUCER ROYALTY / BONUSES\n" +
                     "1304-000,DIRECTOR'S ROYALTIES / BONUSES\n" +
                     "1542-000,CAST HOTEL\n" +
                     "2129-000,\"BUMPS - WARDROBE, AUTO, SMOKE, ETC\"\n" +
                     "2195-000,OTHER COSTS\n" +
                     "2195-000,\"OTHER COSTS \"\n" +
                     "2195-000,OTHER \"COSTS\"\n" +
                     "2195-000,OTHER 'COSTS'",
                     saved);
        assertEquals(fixture, saved);
    }

    @Test
    public void processCSV_with_blank_records() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "11.csv");

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
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "12.csv");

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

        String actual = "name,position\n" +
                        "Joe,13\n" +
                        "Sammy,9\n" +
                        "Alice,4\n" +
                        "Mary,02\n" +
                        "Bohert,0\n" +
                        "Igor,14\n" +
                        "Tihita,10\n" +
                        "Ragu,10\n" +
                        "Karthima,8\n" +
                        "John,4";

        // numeric sort ascending
        expected = "name,position\n" +
                   "Bohert,0\n" +
                   "Mary,02\n" +
                   "Alice,4\n" +
                   "John,4\n" +
                   "Karthima,8\n" +
                   "Sammy,9\n" +
                   "Tihita,10\n" +
                   "Ragu,10\n" +
                   "Joe,13\n" +
                   "Igor,14";
        assertThat(subject.process("[CSV(" + actual + ") => " +
                                   " parse(delim=\\,|recordDelim=\n|header=true)" +
                                   " sortAscending(position)" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));

        // numeric sort descending
        expected = "name,position\n" +
                   "Igor,14\n" +
                   "Joe,13\n" +
                   "Tihita,10\n" +
                   "Ragu,10\n" +
                   "Sammy,9\n" +
                   "Karthima,8\n" +
                   "Alice,4\n" +
                   "John,4\n" +
                   "Mary,02\n" +
                   "Bohert,0";
        assertThat(subject.process("[CSV(" + actual + ") => " +
                                   " parse(delim=\\,|recordDelim=\n|header=true)" +
                                   " sortDescending(position)" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));

        // text sort descending
        expected = "name,position\n" +
                   "Tihita,10\n" +
                   "Sammy,9\n" +
                   "Ragu,10\n" +
                   "Mary,02\n" +
                   "Karthima,8\n" +
                   "John,4\n" +
                   "Joe,13\n" +
                   "Igor,14\n" +
                   "Bohert,0\n" +
                   "Alice,4";
        assertThat(subject.process("[CSV(" + actual + ") => " +
                                   " parse(delim=\\,|recordDelim=\n|header=true)" +
                                   " sortDescending(name)" +
                                   "]"),
                   allOf(is(not(nullValue())), is(equalTo(expected))));
    }

    @Test
    public void processCSV_removeMatchingLines() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "15.csv");

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
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "15.csv");

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
        assertEquals("SSN,Last Name\n" +
                     "345678901,Taylor\n" +
                     "234567890,Scott\n" +
                     "123456789,Hanson\n" +
                     "456789012,Smoe\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        assertEquals("SSN,First Name,Last Name\n" +
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
        assertEquals("SSN,Last Name\n" +
                     "345678901,Taylor\n" +
                     "234567890,Scott\n" +
                     "123456789,Hanson\n" +
                     "456789012,Smoe\n" +
                     "123456792,Jaime\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        assertEquals("SSN,First Name,Last Name\n" +
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
        assertEquals("SSN,Last Name\n" +
                     "345678901,Taylor\n" +
                     "123456792,Jaime\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));

        assertEquals("SSN,First Name,Last Name\n" +
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
        assertEquals("SSN,Last Name\n" +
                     "345678901,Taylor\n" +
                     "675849302,Jaime\n" +
                     "123456792,Jaime\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        assertEquals("SSN,First Name,Last Name\n" +
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
        assertEquals("SSN,Last Name\n" +
                     "345678901,Taylor\n" +
                     "123456789,Hanson\n" +
                     "456789012,Smoe\n" +
                     "123456792,Jaime\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        assertEquals("SSN,First Name,Last Name\n" +
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
        assertEquals("SSN,Last Name",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        assertEquals("SSN,First Name\n" +
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
        assertEquals("SSN,Last Name\n" +
                     "098765432,Wallace\n" +
                     "987654321,Willet\n" +
                     "876543210,Wilma\n" +
                     "765432109,Wharton\n",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        assertEquals("SSN,First Name,Last Name\n" +
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
        assertEquals("345678901,Taylor\n" +
                     "234567890,Scott\n" +
                     "123456789,Hanson\n" +
                     "456789012,Smoe\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=false) store(csv2)]"));
        assertEquals("123456789,Jim,345678901,Taylor\n" +
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
        assertEquals("345678901,Taylor,Yellow\n" +
                     "234567890,Scott,Red\n" +
                     "123456789,Hanson,Green\n" +
                     "456789012,Smoe,Blue\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=false) store(csv2)]"));
        assertEquals("123456789,Jim,37,345678901,Taylor,Yellow\n" +
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
        assertEquals("Phone,Last Name\n" +
                     "345678901,Taylor\n" +
                     "234567890,Scott\n" +
                     "123456789,Hanson\n" +
                     "456789012,Smoe\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        assertEquals("SSN,First Name,Phone,Last Name\n" +
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
        assertEquals("Phone,Last Name,Color\n" +
                     "345678901,Taylor,Yellow\n" +
                     "234567890,Scott,Red\n" +
                     "123456789,Hanson,Green\n" +
                     "456789012,Smoe,Blue\n" +
                     "567890123,Ladder",
                     subject.process("[CSV(${csv2}) => parse(header=true) store(csv2)]"));
        assertEquals("SSN,First Name,Phone,Last Name,Color\n" +
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

        assertEquals("SSN,First Name,Last Name\n" +
                     "123456789,Jim,Hanson\n" +
                     "123456792,,Jaime\n" +
                     "234567890,John\n" +
                     "345678901,James,Taylor\n" +
                     "456789012,Joe,Smoe\n" +
                     "567890123,Jacob,Ladder",
                     subject.process("[CSV(${csv1}) => parse(header=true)" +
                                     "                 merge(csv2,SSN)]"));
        assertEquals("SSN,First Name,Last Name,Color\n" +
                     "123456789,Jim,Hanson\n" +
                     "123456792,,Jaime,Green\n" +
                     "234567890,John,Red\n" +
                     "345678901,James,Taylor,Yellow\n" +
                     "456789012,Joe,Smoe,Blue\n" +
                     "567890123,Jacob,Ladder",
                     subject.process("[CSV(${csv1}) => parse(header=true)" +
                                     "                 merge(csv2,SSN)" +
                                     "                 merge(csv3,SSN)]"));
        assertEquals("SSN,First Name,Last Name,Color,Snack\n" +
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
    public void processCSV_group() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "17.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("Country,Count\n" +
                     "Argentina,1\n" +
                     "Australia,38\n" +
                     "Austria,7\n" +
                     "Bahrain,1\n" +
                     "Belgium,8\n" +
                     "Bermuda,1\n" +
                     "Brazil,5\n" +
                     "Bulgaria,1\n" +
                     "Canada,76\n" +
                     "Cayman Isls,1\n" +
                     "China,1\n" +
                     "Costa Rica,1\n" +
                     "Czech Republic,3\n" +
                     "Denmark,15\n" +
                     "Dominican Republic,1\n" +
                     "Finland,2\n" +
                     "France,27\n" +
                     "Germany,25\n" +
                     "Greece,1\n" +
                     "Guatemala,1\n" +
                     "Hong Kong,1\n" +
                     "Hungary,3\n" +
                     "Iceland,1\n" +
                     "India,2\n" +
                     "Ireland,49\n" +
                     "Israel,1\n" +
                     "Italy,15\n" +
                     "Japan,2\n" +
                     "Jersey,1\n" +
                     "Kuwait,1\n" +
                     "Latvia,1\n" +
                     "Luxembourg,1\n" +
                     "Malaysia,1\n" +
                     "Malta,2\n" +
                     "Mauritius,1\n" +
                     "Moldova,1\n" +
                     "Monaco,2\n" +
                     "Netherlands,22\n" +
                     "New Zealand,6\n" +
                     "Norway,16\n" +
                     "Philippines,2\n" +
                     "Poland,2\n" +
                     "Romania,1\n" +
                     "Russia,1\n" +
                     "South Africa,5\n" +
                     "South Korea,1\n" +
                     "Spain,12\n" +
                     "Sweden,13\n" +
                     "Switzerland,36\n" +
                     "Thailand,2\n" +
                     "The Bahamas,2\n" +
                     "Turkey,6\n" +
                     "Ukraine,1\n" +
                     "United Arab Emirates,6\n" +
                     "United Kingdom,100\n" +
                     "United States,463",
                     subject.process("[CSV(" + file + ") => parse(header=true) group-count(Country) text]")
                    );

        assertEquals("Country,State,Count\n" +
                     "Argentina,,1\n" +
                     "Argentina,Buenos Aires,1\n" +
                     "Australia,,38\n" +
                     "Australia,New South Wales,11\n" +
                     "Australia,Queensland,10\n" +
                     "Australia,South Australia,1\n" +
                     "Australia,Tasmania,1\n" +
                     "Australia,Victoria,9\n" +
                     "Australia,Western Australia,6\n" +
                     "Austria,,7\n" +
                     "Austria,Lower Austria,2\n" +
                     "Austria,Tyrol,1\n" +
                     "Austria,Vienna,4\n" +
                     "Bahrain,,1\n" +
                     "Bahrain,Al Manamah,1\n" +
                     "Belgium,,8\n" +
                     "Belgium,Antwerpen,1\n" +
                     "Belgium,Brussels (Bruxelles),6\n" +
                     "Belgium,Hainaut,1\n" +
                     "Bermuda,,1\n" +
                     "Bermuda,Hamilton,1\n" +
                     "Brazil,,5\n" +
                     "Brazil,Ceara,1\n" +
                     "Brazil,Minas Gerais,1\n" +
                     "Brazil,Rio Grande do Sul,1\n" +
                     "Brazil,Santa Catarina,1\n" +
                     "Brazil,Sao Paulo,1\n" +
                     "Bulgaria,,1\n" +
                     "Bulgaria,Stara Zagora,1\n" +
                     "Canada,,76\n" +
                     "Canada,Alberta,18\n" +
                     "Canada,British Columbia,18\n" +
                     "Canada,Manitoba,1\n" +
                     "Canada,Northwest Territories,1\n" +
                     "Canada,Ontario,27\n" +
                     "Canada,Quebec,8\n" +
                     "Canada,Saskatchewan,2\n" +
                     "Canada,Yukon Territory,1\n" +
                     "Cayman Isls,,1\n" +
                     "Cayman Isls,,1\n" +
                     "China,,1\n" +
                     "China,Guangdong,1\n" +
                     "Costa Rica,,1\n" +
                     "Costa Rica,Heredia,1\n" +
                     "Czech Republic,,3\n" +
                     "Czech Republic,Central Bohemia,1\n" +
                     "Czech Republic,East Bohemia,1\n" +
                     "Czech Republic,Prague,1\n" +
                     "Denmark,,15\n" +
                     "Denmark,Arhus,1\n" +
                     "Denmark,Frederiksborg,3\n" +
                     "Denmark,Kobenhavn,7\n" +
                     "Denmark,Staden Kobenhavn,1\n" +
                     "Denmark,Storstrom,2\n" +
                     "Denmark,Vestsjalland,1\n" +
                     "Dominican Republic,,1\n" +
                     "Dominican Republic,Distrito Nacional,1\n" +
                     "Finland,,2\n" +
                     "Finland,Etela-Suomen Laani,1\n" +
                     "Finland,Ita-Suomen Laani,1\n" +
                     "France,,27\n" +
                     "France,Alsace,1\n" +
                     "France,Brittany,2\n" +
                     "France,Franche-Comte,2\n" +
                     "France,Ile-de-France,8\n" +
                     "France,Lorraine,1\n" +
                     "France,Pays de la Loire,1\n" +
                     "France,Provence-Alpes-Cote d'Azur,4\n" +
                     "France,Rhone-Alpes,7\n" +
                     "France,Upper Normandy,1\n" +
                     "Germany,,25\n" +
                     "Germany,Baden-Wurttemberg,5\n" +
                     "Germany,Bayern,4\n" +
                     "Germany,Berlin,1\n" +
                     "Germany,Hamburg,1\n" +
                     "Germany,Hessen,2\n" +
                     "Germany,Lower Saxony,3\n" +
                     "Germany,Nordrhein-Westfalen,2\n" +
                     "Germany,Rhineland-Palatinate,3\n" +
                     "Germany,Saarland,1\n" +
                     "Germany,Saxony,1\n" +
                     "Germany,Thuringia,2\n" +
                     "Greece,,1\n" +
                     "Greece,Attiki,1\n" +
                     "Guatemala,,1\n" +
                     "Guatemala,Guatemala,1\n" +
                     "Hong Kong,,1\n" +
                     "Hong Kong,,1\n" +
                     "Hungary,,3\n" +
                     "Hungary,Bacs-Kiskun,1\n" +
                     "Hungary,Budapest,1\n" +
                     "Hungary,Pest,1\n" +
                     "Iceland,,1\n" +
                     "Iceland,,1\n" +
                     "India,,2\n" +
                     "India,Andhra Pradesh,1\n" +
                     "India,Haryana,1\n" +
                     "Ireland,,49\n" +
                     "Ireland,Cork,10\n" +
                     "Ireland,Dublin,22\n" +
                     "Ireland,Kerry,1\n" +
                     "Ireland,Kilkenny,1\n" +
                     "Ireland,Limerick,2\n" +
                     "Ireland,Louth,1\n" +
                     "Ireland,Mayo,1\n" +
                     "Ireland,Meath,4\n" +
                     "Ireland,Tipperary,2\n" +
                     "Ireland,Waterford,2\n" +
                     "Ireland,Westmeath,1\n" +
                     "Ireland,Wicklow,2\n" +
                     "Israel,,1\n" +
                     "Israel,Tel Aviv,1\n" +
                     "Italy,,15\n" +
                     "Italy,Lazio,1\n" +
                     "Italy,Lombardy,4\n" +
                     "Italy,Sardinia,1\n" +
                     "Italy,Sicilia,3\n" +
                     "Italy,Tuscany,4\n" +
                     "Italy,Veneto,2\n" +
                     "Japan,,2\n" +
                     "Japan,Tokyo,2\n" +
                     "Jersey,,1\n" +
                     "Jersey,,1\n" +
                     "Kuwait,,1\n" +
                     "Kuwait,,1\n" +
                     "Latvia,,1\n" +
                     "Latvia,Riga,1\n" +
                     "Luxembourg,,1\n" +
                     "Luxembourg,Luxembourg,1\n" +
                     "Malaysia,,1\n" +
                     "Malaysia,Kuala Lumpur,1\n" +
                     "Malta,,2\n" +
                     "Malta,,2\n" +
                     "Mauritius,,1\n" +
                     "Mauritius,Black River,1\n" +
                     "Moldova,,1\n" +
                     "Moldova,Chisinau,1\n" +
                     "Monaco,,2\n" +
                     "Monaco,,2\n" +
                     "Netherlands,,22\n" +
                     "Netherlands,Gelderland,1\n" +
                     "Netherlands,Groningen,2\n" +
                     "Netherlands,Noord-Brabant,1\n" +
                     "Netherlands,Noord-Holland,7\n" +
                     "Netherlands,Utrecht,1\n" +
                     "Netherlands,Zuid-Holland,10\n" +
                     "New Zealand,,6\n" +
                     "New Zealand,Auckland,4\n" +
                     "New Zealand,Taranaki,2\n" +
                     "Norway,,16\n" +
                     "Norway,Buskerud,1\n" +
                     "Norway,Hordaland,2\n" +
                     "Norway,Oslo,6\n" +
                     "Norway,Rogaland,4\n" +
                     "Norway,Sogn og Fjordane,1\n" +
                     "Norway,Vestfold,2\n" +
                     "Philippines,,2\n" +
                     "Philippines,Bohol,1\n" +
                     "Philippines,General Santos,1\n" +
                     "Poland,,2\n" +
                     "Poland,Mazowieckie,1\n" +
                     "Poland,Pomorskie,1\n" +
                     "Romania,,1\n" +
                     "Romania,Bucuresti,1\n" +
                     "Russia,,1\n" +
                     "Russia,Moscow City,1\n" +
                     "South Africa,,5\n" +
                     "South Africa,Gauteng,3\n" +
                     "South Africa,KwaZulu-Natal,2\n" +
                     "South Korea,,1\n" +
                     "South Korea,Soul-t'ukpyolsi,1\n" +
                     "Spain,,12\n" +
                     "Spain,Andalucia,1\n" +
                     "Spain,Catalonia,3\n" +
                     "Spain,Madrid,5\n" +
                     "Spain,Murcia,2\n" +
                     "Spain,Pais Vasco,1\n" +
                     "Sweden,,13\n" +
                     "Sweden,Ostergotland,1\n" +
                     "Sweden,Skane,1\n" +
                     "Sweden,Stockholm,9\n" +
                     "Sweden,Uppsala,1\n" +
                     "Sweden,Vasterbotten,1\n" +
                     "Switzerland,,36\n" +
                     "Switzerland,Aargau,2\n" +
                     "Switzerland,Basel-Country,2\n" +
                     "Switzerland,Basel-Town,1\n" +
                     "Switzerland,Bern,1\n" +
                     "Switzerland,Geneve,7\n" +
                     "Switzerland,Neuchatel,1\n" +
                     "Switzerland,Schwyz,1\n" +
                     "Switzerland,St Gallen,1\n" +
                     "Switzerland,Ticino,2\n" +
                     "Switzerland,Vaud,8\n" +
                     "Switzerland,Zug,2\n" +
                     "Switzerland,Zurich,8\n" +
                     "Thailand,,2\n" +
                     "Thailand,Krung Thep,1\n" +
                     "Thailand,Phuket,1\n" +
                     "The Bahamas,,2\n" +
                     "The Bahamas,Freeport,1\n" +
                     "The Bahamas,New Providence,1\n" +
                     "Turkey,,6\n" +
                     "Turkey,Istanbul,5\n" +
                     "Turkey,Izmir,1\n" +
                     "Ukraine,,1\n" +
                     "Ukraine,Kiev,1\n" +
                     "United Arab Emirates,,6\n" +
                     "United Arab Emirates,Abu Zaby,1\n" +
                     "United Arab Emirates,Dubayy,5\n" +
                     "United Kingdom,,100\n" +
                     "United Kingdom,England,86\n" +
                     "United Kingdom,Northern Ireland,3\n" +
                     "United Kingdom,Scotland,9\n" +
                     "United Kingdom,Wales,2\n" +
                     "United States,,463\n" +
                     "United States,AK,5\n" +
                     "United States,AL,1\n" +
                     "United States,AR,3\n" +
                     "United States,AZ,10\n" +
                     "United States,CA,66\n" +
                     "United States,CO,11\n" +
                     "United States,CT,9\n" +
                     "United States,DC,3\n" +
                     "United States,DE,1\n" +
                     "United States,FL,29\n" +
                     "United States,GA,21\n" +
                     "United States,Georgia,1\n" +
                     "United States,HI,9\n" +
                     "United States,IA,3\n" +
                     "United States,ID,2\n" +
                     "United States,IL,16\n" +
                     "United States,IN,2\n" +
                     "United States,KS,2\n" +
                     "United States,KY,2\n" +
                     "United States,LA,2\n" +
                     "United States,MA,13\n" +
                     "United States,MD,15\n" +
                     "United States,ME,2\n" +
                     "United States,MI,11\n" +
                     "United States,MN,10\n" +
                     "United States,MO,1\n" +
                     "United States,MS,2\n" +
                     "United States,MT,2\n" +
                     "United States,Michigan,1\n" +
                     "United States,NC,7\n" +
                     "United States,NE,1\n" +
                     "United States,NH,1\n" +
                     "United States,NJ,19\n" +
                     "United States,NM,1\n" +
                     "United States,NV,5\n" +
                     "United States,NY,41\n" +
                     "United States,OH,7\n" +
                     "United States,OR,2\n" +
                     "United States,PA,11\n" +
                     "United States,RI,3\n" +
                     "United States,SC,4\n" +
                     "United States,TN,11\n" +
                     "United States,TX,37\n" +
                     "United States,UT,5\n" +
                     "United States,VA,30\n" +
                     "United States,VI,1\n" +
                     "United States,VT,2\n" +
                     "United States,Virginia,1\n" +
                     "United States,WA,14\n" +
                     "United States,WI,5",
                     subject.process("[CSV(" + file + ") => parse(header=true) group-count(Country,State) text]")
                    );
    }

    @Test
    public void processCSV_sum() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "17.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("Country,Sum\n" +
                     "Argentina,1200\n" +
                     "Australia,64800\n" +
                     "Austria,10800\n" +
                     "Bahrain,1200\n" +
                     "Belgium,12000\n" +
                     "Bermuda,1200\n" +
                     "Brazil,12300\n" +
                     "Bulgaria,1200\n" +
                     "Canada,124800\n" +
                     "Cayman Isls,1200\n" +
                     "China,1200\n" +
                     "Costa Rica,1200\n" +
                     "Czech Republic,6000\n" +
                     "Denmark,18000\n" +
                     "Dominican Republic,1200\n" +
                     "Finland,2400\n" +
                     "France,53100\n" +
                     "Germany,42000\n" +
                     "Greece,1200\n" +
                     "Guatemala,1200\n" +
                     "Hong Kong,1200\n" +
                     "Hungary,3600\n" +
                     "Iceland,1200\n" +
                     "India,2400\n" +
                     "Ireland,69900\n" +
                     "Israel,1200\n" +
                     "Italy,37800\n" +
                     "Japan,2400\n" +
                     "Jersey,1200\n" +
                     "Kuwait,1200\n" +
                     "Latvia,1200\n" +
                     "Luxembourg,1200\n" +
                     "Malaysia,1200\n" +
                     "Malta,4800\n" +
                     "Mauritius,3600\n" +
                     "Moldova,1200\n" +
                     "Monaco,2400\n" +
                     "Netherlands,44700\n" +
                     "New Zealand,7200\n" +
                     "Norway,21600\n" +
                     "Philippines,2400\n" +
                     "Poland,2400\n" +
                     "Romania,1200\n" +
                     "Russia,3600\n" +
                     "South Africa,12300\n" +
                     "South Korea,1200\n" +
                     "Spain,16800\n" +
                     "Sweden,22800\n" +
                     "Switzerland,76800\n" +
                     "Thailand,4800\n" +
                     "The Bahamas,2400\n" +
                     "Turkey,7200\n" +
                     "Ukraine,1200\n" +
                     "United Arab Emirates,12000\n" +
                     "United Kingdom,144000\n" +
                     "United States,750000",
                     subject.process("[CSV(" + file + ") => parse(header=true) groupSum(Country,Price) text]")
                    );
    }

    @Test
    public void processCSV_retainColumns() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "_retainColumns.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("Product,Price,Name,Country\n" +
                     "Product1,1200,carolina,United Kingdom\n" +
                     "Product1,1200,Betina,United States\n" +
                     "Product1,1200,Federica e Andrea,United States\n" +
                     "Product1,1200,Gouya,Australia\n" +
                     "Product2,3600,Gerd W ,United States\n" +
                     "Product1,1200,LAURENCE,United States\n" +
                     "Product1,1200,Fleur,United States\n" +
                     "Product1,1200,adam,United States\n" +
                     "Product1,1200,Renee Elisabeth,Israel\n" +
                     "Product1,1200,Aidan,France\n" +
                     "Product1,1200,Stacy,United States\n" +
                     "Product1,1200,Heidi,Netherlands\n" +
                     "Product1,1200,Sean ,United States\n" +
                     "Product1,1200,Georgia,United States\n" +
                     "Product1,1200,Richard,United States\n" +
                     "Product1,1200,Leanne,Ireland\n" +
                     "Product1,1200,Janet,Canada\n" +
                     "Product1,1200,barbara,India\n" +
                     "Product2,3600,Sabine,United Kingdom\n" +
                     "Product1,1200,Hani,United States\n" +
                     "Product1,1200,Jeremy,United Kingdom\n" +
                     "Product1,1200,Janis,Ireland\n" +
                     "Product1,1200,Nicola,South Africa\n" +
                     "Product1,1200,asuman,United States\n" +
                     "Product1,1200,Lena,Finland\n" +
                     "Product1,1200,Lisa,United States\n" +
                     "Product1,1200,Bryan Kerrene,United States\n" +
                     "Product1,1200,chris,United Kingdom\n" +
                     "Product1,1200,Maxine,United States\n" +
                     "Product1,1200,Family,United States\n" +
                     "Product1,1200,Katherine,United States\n" +
                     "Product1,1200,Linda,United States\n" +
                     "Product1,1200,SYLVIA,Switzerland\n" +
                     "Product1,1200,Sheila,United States\n" +
                     "Product1,1200,Stephanie,Netherlands\n" +
                     "Product1,1200,Kelly,United States\n" +
                     "Product2,3600,James,Australia\n" +
                     "Product1,1200,jennifer,United States\n" +
                     "Product1,1200,Anneli,United States\n" +
                     "Product2,3600,Ritz,United States\n" +
                     "Product2,3600,Sylvia,United States\n" +
                     "Product1,1200,Marie,United States\n" +
                     "Product1,1200,Mehmet Fatih,Denmark\n" +
                     "Product2,3600,Anabela,United States\n" +
                     "Product1,1200,Nicole,United States\n" +
                     "Product2,3600,Christiane ,United States\n" +
                     "Product1,1200,Sari,United Kingdom\n" +
                     "Product1,1200,simone,Denmark\n" +
                     "Product1,1200,Vanessa,United States\n" +
                     "Product1,1200,Anupam,Ireland\n" +
                     "Product1,1200,Karina,United States\n" +
                     "Product1,1200,Frank,Australia\n" +
                     "Product1,1200,Angela,United States\n" +
                     "Product1,1200,Darren,United States\n" +
                     "Product1,1200,Nikki,United States\n" +
                     "Product1,1200,chris,Australia\n" +
                     "Product1,1200,Stephanie,Belgium\n" +
                     "Product1,1200,Anushka,Canada\n" +
                     "Product1,1200,June ,United States\n" +
                     "Product2,3600,Baybars,Canada\n" +
                     "Product1,1200,Bonnie,Sweden\n" +
                     "Product1,1200,Cindy ,United Kingdom\n" +
                     "Product1,1200,chrissy,United States\n" +
                     "Product1,1200,Tamar,United Kingdom\n" +
                     "Product2,3600,Deirdre,Switzerland\n" +
                     "Product1,1200,Bernadett,United Kingdom\n" +
                     "Product1,1200,Dottie,United States\n" +
                     "Product1,1200,Stefan,Norway\n" +
                     "Product1,1200,Gina,Canada\n" +
                     "Product1,1200,Lynne,United States\n" +
                     "Product1,1200,Tammy,Switzerland\n" +
                     "Product1,1200,Kim,Canada\n" +
                     "Product1,1200,Bruce,Canada\n" +
                     "Product1,1200,Rosa Maria,United States\n" +
                     "Product1,1200,Lydia,Canada\n" +
                     "Product1,1200,Eric,Luxembourg\n" +
                     "Product1,1200,AnaPaula,United Kingdom\n" +
                     "Product1,1200,Robin,Italy\n" +
                     "Product1,1200,Gitte,United States\n" +
                     "Product1,1200,Dr. Claudia,Norway\n" +
                     "Product1,1200,Crystal,United States\n" +
                     "Product1,1200,Delphine,United States\n" +
                     "Product1,1200,nathalie,Canada\n" +
                     "Product1,1200,Lindi,Canada\n" +
                     "Product2,3600,Valda,United States\n" +
                     "Product2,3600,Marcia,Germany\n" +
                     "Product1,1200,Kevin,United Kingdom\n" +
                     "Product1,1200,Clare,United States\n" +
                     "Product1,1200,Alice,Denmark\n" +
                     "Product1,1200,ZENA,United States\n" +
                     "Product1,1200,Andrea,Moldova\n" +
                     "Product1,1200,Rennae,United States\n" +
                     "Product1,1200,Gerhard,Canada\n" +
                     "Product1,1200,Megan,Spain\n" +
                     "Product1,1200,Danielle,Ireland\n" +
                     "Product1,1200,Tod,United States\n" +
                     "Product1,1200,Janaina,United States\n" +
                     "Product1,1200,Kofi,Canada\n" +
                     "Product1,1200,Jennifer,United Arab Emirates",
                     subject.process("[CSV(" + file + ") =>" +
                                     " parse(header=true,trim=false)" +
                                     " retainColumns(1,2,4,7)" +
                                     " text]")
                    );
    }

    @Test
    public void processCSV_replaceColumnsRegex() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "_replaceColumnsRegex.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("Price,Payment_Type,Country,Latitude,Longitude\n" +
                     "12.00,Mastercard,United Kingdom,51.5,-1.11\n" +
                     "12.00,Visa,United States,39.19,-94.68\n" +
                     "12.00,Mastercard,United States,46.18,-123.83\n" +
                     "12.00,Visa,Australia,-36.13,144.75\n" +
                     "36.00,Visa,United States,33.52,-86.80\n" +
                     "12.00,Visa,United States,39.79,-75.23\n" +
                     "12.00,Mastercard,United States,40.69,-89.58\n" +
                     "12.00,Mastercard,United States,36.34,-88.85\n" +
                     "12.00,Mastercard,Israel,32.06,34.76\n" +
                     "12.00,Visa,France,48.88,2.15\n" +
                     "12.00,Diners,United States,40.71,-74.00\n" +
                     "12.00,Amex,Netherlands,51.45,5.46\n" +
                     "12.00,Mastercard,United States,29.42,-98.49\n" +
                     "12.00,Visa,United States,43.69,-116.35\n" +
                     "12.00,Visa,United States,40.03,-74.95\n" +
                     "12.00,Diners,Ireland,53.67,-6.31\n" +
                     "12.00,Visa,Canada,45.41,-75.7\n" +
                     "12.00,Diners,India,17.38,78.46\n" +
                     "36.00,Visa,United Kingdom,51.52,0.14\n" +
                     "12.00,Diners,United States,40.76,-111.89\n" +
                     "12.00,Visa,United Kingdom,53.5,-2.21\n" +
                     "12.00,Diners,Ireland,51.86,-8.58\n" +
                     "12.00,Mastercard,South Africa,-26.16,27.86\n" +
                     "12.00,Visa,United States,32.64,-117.08\n" +
                     "12.00,Mastercard,Finland,62.9,27.68\n" +
                     "12.00,Visa,United States,29.61,-95.63\n" +
                     "12.00,Diners,United States,40.71,-74.00\n" +
                     "12.00,Visa,United Kingdom,51.52,0.14\n" +
                     "12.00,Visa,United States,40.61,-89.45\n" +
                     "12.00,Visa,United States,37.22,-121.97\n" +
                     "12.00,Mastercard,United States,40.71,-74.00\n" +
                     "12.00,Mastercard,United States,25.77,-80.19\n" +
                     "12.00,Mastercard,Switzerland,46.23,6.2\n" +
                     "12.00,Diners,United States,40.65,-73.95\n" +
                     "12.00,Mastercard,Netherlands,52.33,4.78\n" +
                     "12.00,Amex,United States,38.96,-77.34\n" +
                     "36.00,Visa,Australia,-27.16,152.95\n" +
                     "12.00,Visa,United States,33.44,-112.07\n" +
                     "12.00,Mastercard,United States,29.76,-95.36\n" +
                     "36.00,Amex,United States,43.77,-72.81\n" +
                     "36.00,Amex,United States,43.77,-72.81\n" +
                     "12.00,Mastercard,United States,34.33,-84.37\n" +
                     "12.00,Visa,Denmark,56.03,12.61\n" +
                     "36.00,Visa,United States,41.54,-87.68\n" +
                     "12.00,Amex,United States,29.76,-95.36\n" +
                     "36.00,Visa,United States,26.46,-80.07\n" +
                     "12.00,Mastercard,United Kingdom,51.4,-1.31\n" +
                     "12.00,Mastercard,Denmark,55.66,12.58\n" +
                     "12.00,Amex,United States,33.92,-84.37\n" +
                     "12.00,Diners,Ireland,53.42,-6.17\n" +
                     "12.00,Visa,United States,26.12,-80.14\n" +
                     "12.00,Mastercard,Australia,-37.81,144.96\n" +
                     "12.00,Visa,United States,41.72,-93.60\n" +
                     "12.00,Visa,United States,35.72,-79.17\n" +
                     "12.00,Mastercard,United States,40.91,-73.78\n" +
                     "12.00,Visa,Australia,-28,153.43\n" +
                     "12.00,Visa,Belgium,50.83,4.33\n" +
                     "12.00,Visa,Canada,49.25,-122.5\n" +
                     "12.00,Mastercard,United States,41.46,-81.50\n" +
                     "36.00,Diners,Canada,53.2,-105.75\n" +
                     "12.00,Mastercard,Sweden,59.28,18.3\n" +
                     "12.00,Visa,United Kingdom,51.67,-2.01\n" +
                     "12.00,Mastercard,United States,43.64,-72.31\n" +
                     "12.00,Mastercard,United Kingdom,51.11,-0.81\n" +
                     "36.00,Mastercard,Switzerland,46.53,6.66\n" +
                     "12.00,Mastercard,United Kingdom,50.9,-1.4\n" +
                     "12.00,Visa,United States,39.53,-77.31\n" +
                     "12.00,Visa,Norway,58.96,5.75\n" +
                     "12.00,Visa,Canada,52.26,-113.8\n" +
                     "12.00,Diners,United States,35.14,-90.04\n" +
                     "12.00,Mastercard,Switzerland,46.51,6.5\n" +
                     "12.00,Visa,Canada,51.08,-114.08\n" +
                     "12.00,Visa,Canada,44.16,-77.38\n" +
                     "12.00,Visa,United States,39.16,-84.45\n" +
                     "12.00,Visa,Canada,49.68,-124.93\n" +
                     "12.00,Visa,Luxembourg,49.58,6.12\n" +
                     "12.00,Mastercard,United Kingdom,54.65,-5.73\n" +
                     "12.00,Visa,Italy,45.46,9.2\n" +
                     "12.00,Visa,United States,40.63,-74.15\n" +
                     "12.00,Visa,Norway,59.91,10.75\n" +
                     "12.00,Visa,United States,42.46,-83.37\n" +
                     "12.00,Diners,United States,34.01,-118.49\n" +
                     "12.00,Visa,Canada,51.08,-114.08\n" +
                     "12.00,Mastercard,Canada,49.25,-123.13\n" +
                     "36.00,Mastercard,United States,33.66,-117.82\n" +
                     "36.00,Mastercard,Germany,52.33,7.9\n" +
                     "12.00,Mastercard,United Kingdom,51.9,-2.08\n" +
                     "12.00,Visa,United States,37.61,-75.76\n" +
                     "12.00,Visa,Denmark,54.83,11.15\n" +
                     "12.00,Mastercard,United States,21.30,-157.85\n" +
                     "12.00,Visa,Moldova,46.98,28.94\n" +
                     "12.00,Visa,United States,30.66,-81.46\n" +
                     "12.00,Visa,Canada,44.15,-79.86\n" +
                     "12.00,Amex,Spain,37.93,-1.13\n" +
                     "12.00,Mastercard,Ireland,53.31,-6.26\n" +
                     "12.00,Mastercard,United States,25.72,-80.26\n" +
                     "12.00,Visa,United States,25.77,-80.19\n" +
                     "12.00,Visa,Canada,49.25,-123.13\n" +
                     "12.00,Visa,United Arab Emirates,25.20,55.24",
                     subject.process("[CSV(" + file + ") =>" +
                                     " parse(header=true,trim=false)" +
                                     " retainColumns(2,3,7,10,11)" +
                                     " replaceColumnRegex((\\d+\\)(\\d\\d\\),$1.$2,0)" +
                                     " replaceColumnRegex((-?\\d+\\.\\d\\d?\\)(.*\\),$1,3)" +
                                     " replaceColumnRegex((-?\\d+\\.\\d\\d?\\)(.*\\),$1,4)" +
                                     " text]")
                    );
    }

    @Test
    public void processCSV_distinct() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "_replaceColumnsRegex.csv");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("Product,Price,Country\n" +
                     "Product1,1200,United Kingdom\n" +
                     "Product1,1200,United States\n" +
                     "Product1,1200,Australia\n" +
                     "Product2,3600,United States\n" +
                     "Product1,1200,Israel\n" +
                     "Product1,1200,France\n" +
                     "Product1,1200,Netherlands\n" +
                     "Product1,1200,Ireland\n" +
                     "Product1,1200,Canada\n" +
                     "Product1,1200,India\n" +
                     "Product2,3600,United Kingdom\n" +
                     "Product1,1200,South Africa\n" +
                     "Product1,1200,Finland\n" +
                     "Product1,1200,Switzerland\n" +
                     "Product2,3600,Australia\n" +
                     "Product1,1200,Denmark\n" +
                     "Product1,1200,Belgium\n" +
                     "Product2,3600,Canada\n" +
                     "Product1,1200,Sweden\n" +
                     "Product2,3600,Switzerland\n" +
                     "Product1,1200,Norway\n" +
                     "Product1,1200,Luxembourg\n" +
                     "Product1,1200,Italy\n" +
                     "Product2,3600,Germany\n" +
                     "Product1,1200,Moldova\n" +
                     "Product1,1200,Spain\n" +
                     "Product1,1200,United Arab Emirates",
                     subject.process("[CSV(" + file + ") =>" +
                                     " parse(header=true,trim=false)" +
                                     " retainColumns(1,2,7)" +
                                     " distinct" +
                                     " text]")
                    );
    }

    @Test
    public void processCSV_removeRows_by_index() throws Exception {
        String csvFile = ResourceUtils.getResourceFilePath(resourcePath + className + "8.csv");
        ExpressionProcessor subject = new ExpressionProcessor(context);

        // CSV with header
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(header=true)" +
                                   " removeRows(2,3,0)" +
                                   " row-count]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("2"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(header=true)" +
                                   " removeRows(2,3,0)" +
                                   " text]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(
                             "User Name,First Name,Last Name,Display Name,Job Title,Department,Office Number,Office Phone,Mobile Phone,Fax,Address,City,State or Province,ZIP or Postal Code,Country or Region\n" +
                             "ben@contoso.com,Ben,Andrews,Ben Andrews,Director,Information Technology,362342,312-492-9910,123-555-6642,123-555-9822,1 Microsoft way,Redmond,WA,98052,United States\n" +
                             "melissa@contoso.com,Melissa,MacBeth,Melissa MacBeth,Supervisor,Human Resource,345345,312-490-3892,123-555-6645,123-555-9825,1 Microsoft way,Redmond,WA,98052,United States"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(header=true)" +
                                   " removeRows(2,3,0)" +
                                   " filter(Department = Information Technology)" +
                                   " removeColumns(Job Title|Department|Office Number|Office Phone|Mobile Phone|Fax)" +
                                   " removeColumns(Address|City|State or Province|ZIP or Postal Code|Country or Region)" +
                                   " text]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("User Name,First Name,Last Name,Display Name\n" +
                                    "ben@contoso.com,Ben,Andrews,Ben Andrews"))));

        // CSV with no header
        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(header=false)" +
                                   " removeRows(2,3,0)" +
                                   " row-count]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("3"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(header=false)" +
                                   " removeRows(2,3,0)" +
                                   " text]"),
                   allOf(is(not(nullValue())),
                         is(equalTo(
                             "chris@contoso.com,Chris,Green,Chris Green,Manager,Finance,234232,312-490-4891,123-555-6641,123-555-9821,1 Microsoft way,Redmond,WA,98052,United States\n" +
                             "cynthia@contoso.com,Cynthia,Carey,Cynthia Carey,Senior Director,Information Technology,112324,312-490-1192,123-555-6644,123-555-9824,1 Microsoft way,Redmond,WA,98052,United States\n" +
                             "melissa@contoso.com,Melissa,MacBeth,Melissa MacBeth,Supervisor,Human Resource,345345,312-490-3892,123-555-6645,123-555-9825,1 Microsoft way,Redmond,WA,98052,United States"))));

        assertThat(subject.process("[CSV(" + csvFile + ") => " +
                                   " parse(header=false)" +
                                   " removeRows(2,3,0)" +
                                   " removeColumns(4,5,6,7,8,9,10,11,12,13,14)" +
                                   " text]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("chris@contoso.com,Chris,Green,Chris Green\n" +
                                    "cynthia@contoso.com,Cynthia,Carey,Cynthia Carey\n" +
                                    "melissa@contoso.com,Melissa,MacBeth,Melissa MacBeth"))));

    }

    @Test
    public void processCSV_force_quote_on_text() throws Exception {
        context.setData("csv1", "SSN,First Name,Job,Age\n" +
                                "123456789,Jim,Teacher,39\n" +
                                "234567890,John,Manager,33\n" +
                                "345678901,\"James P.\",Educator,34\n" +
                                "456789012,Joe,\"Production Inspector\",41\n" +
                                "567890123,Jacob,\"Software Developer at \\\"Cool Vibe\\\"\",44\n");
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertEquals("SSN,First Name,Job,Age\n" +
                     "123456789,\"Jim\",\"Teacher\",39\n" +
                     "234567890,\"John\",\"Manager\",33\n" +
                     "345678901,\"James P.\",\"Educator\",34\n" +
                     "456789012,\"Joe\",\"Production Inspector\",41\n" +
                     "567890123,\"Jacob\",\"Software Developer at \\\"Cool Vibe\\\"\",44",
                     subject.process("[CSV(${csv1}) => parse(header=true) surround(\",1,2) text]"));

        assertEquals("SSN,First Name,Job,Age\n" +
                     "\"123456789\",\"Jim\",\"Teacher\",\"39\"\n" +
                     "\"234567890\",\"John\",\"Manager\",\"33\"\n" +
                     "\"345678901\",\"James P.\",\"Educator\",\"34\"\n" +
                     "\"456789012\",\"Joe\",\"Production Inspector\",\"41\"\n" +
                     "\"567890123\",\"Jacob\",\"Software Developer at \\\"Cool Vibe\\\"\",\"44\"",
                     subject.process("[CSV(${csv1}) => parse(header=true) surround(\",*) text]"));

        assertEquals("SSN,First Name,Job,Age\n" +
                     "\"123456789\",Jim,Teacher,39\n" +
                     "\"234567890\",John,Manager,33\n" +
                     "\"345678901\",James P.,Educator,34\n" +
                     "\"456789012\",Joe,Production Inspector,41\n" +
                     "\"567890123\",Jacob,\"Software Developer at \\\"Cool Vibe\\\"\",44",
                     subject.process("[CSV(${csv1}) => parse(header=true) surround(\",SSN) text]"));

        assertEquals("SSN,First Name,Job,Age\n" +
                     "\"123456789\",Jim,\"Teacher\",39\n" +
                     "\"234567890\",John,\"Manager\",33\n" +
                     "\"345678901\",\"James P.\",\"Educator\",34\n" +
                     "\"456789012\",Joe,\"Production Inspector\",41\n" +
                     "\"567890123\",Jacob,\"Software Developer at \\\"Cool Vibe\\\"\",44",
                     subject
                         .process("[CSV(${csv1}) => parse(header=true,keepQuote=true) surround(\",Job,SSN) text]"));

        assertEquals("SSN,First Name,Job,Age\n" +
                     " 123456789 ,Jim, Teacher ,39\n" +
                     " 234567890 ,John, Manager ,33\n" +
                     " 345678901 ,\"James P.\", Educator ,34\n" +
                     " 456789012 ,Joe, \"Production Inspector\" ,41\n" +
                     " 567890123 ,Jacob, \"Software Developer at \\\"Cool Vibe\\\"\" ,44",
                     subject
                         .process("[CSV(${csv1}) => parse(header=true,keepQuote=true) surround( ,Job,SSN) text]"));
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
                         className + "-rendered.sql";
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
        assertTrue(sqlFile.isFile());
        assertTrue(sqlFile.canRead());
        assertTrue(sqlFile.length() > 1250);

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

        FileUtils.deleteQuietly(new File(sqlPath));
    }

    @Test
    public void processExcel() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => text]"),
                   allOf(is(not(nullValue())), is(equalTo(file))));
        assertThat(subject.process("[EXCEL(" + file + ") => worksheets]"),
                   allOf(is(not(nullValue())), is(equalTo("sc1,gpos1,tc17,list45,list46,rccTo_2355"))));

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
        String xlsxFile = ResourceUtils.getResourceFilePath(resourcePath + className + "10.xlsx");
        assertNotNull(xlsxFile);
        File targetSource = new File(xlsxFile);

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
        assertTrue(targetFile.length() > 17 * 1024);

        // test worksheet content
        Excel tmpExcel = new Excel(targetFile);
        Worksheet worksheet = tmpExcel.worksheet("transposed");
        assertNotNull(worksheet);
        assertEquals("number1", worksheet.cell(new ExcelAddress("K9")).getStringCellValue());
        assertEquals("69898", worksheet.cell(new ExcelAddress("L9")).getStringCellValue());
        assertEquals("numbers3", worksheet.cell(new ExcelAddress("K10")).getStringCellValue());
        assertEquals("2", worksheet.cell(new ExcelAddress("M10")).getStringCellValue());

        FileUtils.deleteQuietly(targetFile);

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
        assertNotNull(tmpFile);
        assertTrue(tmpFile.isFile());
        assertTrue(tmpFile.length() > 5 * 1024);

        tmpExcel = new Excel(tmpFile);
        worksheet = tmpExcel.worksheet("transposed");
        assertNotNull(worksheet);
        assertEquals("number1", worksheet.cell(new ExcelAddress("C14")).getStringCellValue());
        assertEquals("69898", worksheet.cell(new ExcelAddress("D14")).getStringCellValue());
        assertEquals("numbers3", worksheet.cell(new ExcelAddress("C15")).getStringCellValue());
        assertEquals("2", worksheet.cell(new ExcelAddress("E15")).getStringCellValue());

        FileUtils.deleteQuietly(tmpFile);
    }

    @Test
    public void processExcel_worksheets() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => text]"),
                   allOf(is(not(nullValue())), is(equalTo(file))));
        assertThat(subject.process("[EXCEL(" + file + ") => worksheets]"),
                   allOf(is(not(nullValue())), is(equalTo("sc1,gpos1,tc17,list45,list46,rccTo_2355"))));
    }

    @Test
    public void processExcel_read() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

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
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

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
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

        // --------------------------------------------------------------------------------
        // test with existing workbook/worksheet
        // --------------------------------------------------------------------------------
        String xlsxFile = ResourceUtils.getResourceFilePath(resourcePath + className + "10.xlsx");
        assertNotNull(xlsxFile);
        File targetSource = new File(xlsxFile);

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
        assertTrue(targetFile.length() > 17 * 1024);

        // test worksheet content
        Excel tmpExcel = new Excel(targetFile);
        Worksheet worksheet = tmpExcel.worksheet("transposed");
        assertNotNull(worksheet);
        assertEquals("number1", worksheet.cell(new ExcelAddress("K9")).getStringCellValue());
        assertEquals("69898", worksheet.cell(new ExcelAddress("L9")).getStringCellValue());
        assertEquals("numbers3", worksheet.cell(new ExcelAddress("K10")).getStringCellValue());
        assertEquals("2", worksheet.cell(new ExcelAddress("M10")).getStringCellValue());

        FileUtils.deleteQuietly(targetFile);

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
        assertNotNull(tmpFile);
        assertTrue(tmpFile.isFile());
        assertTrue(tmpFile.length() > 5 * 1024);

        tmpExcel = new Excel(tmpFile);
        worksheet = tmpExcel.worksheet("transposed");
        assertNotNull(worksheet);
        assertEquals("number1", worksheet.cell(new ExcelAddress("C14")).getStringCellValue());
        assertEquals("69898", worksheet.cell(new ExcelAddress("D14")).getStringCellValue());
        assertEquals("numbers3", worksheet.cell(new ExcelAddress("C15")).getStringCellValue());
        assertEquals("2", worksheet.cell(new ExcelAddress("E15")).getStringCellValue());

        FileUtils.deleteQuietly(tmpFile);
    }

    @Test
    public void processExcel_pack() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

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
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

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
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

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
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

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
    public void processExcel_csv() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => read(list46,A1:F13) csv]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("number1,group,bus type,prod type,fid,misc\r\n" +
                                    "69898,1,PP,30 min series,,\r\n" +
                                    "28520,1,PP,theat,,\r\n" +
                                    "15970,1,PP,mow,,\r\n" +
                                    "1,1,RS,residuals,,\r\n" +
                                    "76990,1,CS,casting,27,postage\r\n" +
                                    "78277,1,CS,casting,47,\r\n" +
                                    "78294,1,MS,music,,\r\n" +
                                    "16041,1,CM,comm,,\r\n" +
                                    "74546,6,PP,group 6,,\r\n" +
                                    "39503,2000,PP,agency,2,\r\n" +
                                    "16042,1,CM,comm,,\r\n" +
                                    "75341,1,PP,1 hr series,,accrue w/c"))));
        assertThat(subject.process("[EXCEL(" + file + ") => read(list46,A1:F13) csvWithHeader()]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("number1,group,bus type,prod type,fid,misc\r\n" +
                                    "69898,1,PP,30 min series,,\r\n" +
                                    "28520,1,PP,theat,,\r\n" +
                                    "15970,1,PP,mow,,\r\n" +
                                    "1,1,RS,residuals,,\r\n" +
                                    "76990,1,CS,casting,27,postage\r\n" +
                                    "78277,1,CS,casting,47,\r\n" +
                                    "78294,1,MS,music,,\r\n" +
                                    "16041,1,CM,comm,,\r\n" +
                                    "74546,6,PP,group 6,,\r\n" +
                                    "39503,2000,PP,agency,2,\r\n" +
                                    "16042,1,CM,comm,,\r\n" +
                                    "75341,1,PP,1 hr series,,accrue w/c"))));
    }

    @Test
    public void processExcel_csv_append() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");
        String output = StringUtils.replace(file, ".xlsx", ".csv");
        assertNotNull(output);

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String outcome1 = subject.process("[EXCEL(" + file + ") => read(list46,A1:F6) csv save(" + output + ")]");
        System.out.println("outcome1 = " + outcome1);

        String outcome2 = subject.process("[EXCEL(" + file + ") => read(list46,A7:F13) csv save(" + output + ",true)]");
        System.out.println("outcome2 = " + outcome2);

        String csvContent = FileUtils.readFileToString(new File(output), DEF_FILE_ENCODING);

        assertThat(csvContent,
                   allOf(is(not(nullValue())),
                         is(equalTo("number1,group,bus type,prod type,fid,misc\r\n" +
                                    "69898,1,PP,30 min series,,\r\n" +
                                    "28520,1,PP,theat,,\r\n" +
                                    "15970,1,PP,mow,,\r\n" +
                                    "1,1,RS,residuals,,\r\n" +
                                    "76990,1,CS,casting,27,postage\r\n" +
                                    "78277,1,CS,casting,47,\r\n" +
                                    "78294,1,MS,music,,\r\n" +
                                    "16041,1,CM,comm,,\r\n" +
                                    "74546,6,PP,group 6,,\r\n" +
                                    "39503,2000,PP,agency,2,\r\n" +
                                    "16042,1,CM,comm,,\r\n" +
                                    "75341,1,PP,1 hr series,,accrue w/c"))));
    }

    @Test
    public void processExcel_json() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");
        ExpressionProcessor subject = new ExpressionProcessor(context);

        assertThat(subject.process("[EXCEL(" + file + ") => read(list46,A1:F13) json(false)]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("[\n" +
                                    "  [\n" +
                                    "    \"number1\",\n" +
                                    "    \"group\",\n" +
                                    "    \"bus type\",\n" +
                                    "    \"prod type\",\n" +
                                    "    \"fid\",\n" +
                                    "    \"misc\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"69898\",\n" +
                                    "    \"1\",\n" +
                                    "    \"PP\",\n" +
                                    "    \"30 min series\",\n" +
                                    "    \"\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"28520\",\n" +
                                    "    \"1\",\n" +
                                    "    \"PP\",\n" +
                                    "    \"theat\",\n" +
                                    "    \"\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"15970\",\n" +
                                    "    \"1\",\n" +
                                    "    \"PP\",\n" +
                                    "    \"mow\",\n" +
                                    "    \"\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"1\",\n" +
                                    "    \"1\",\n" +
                                    "    \"RS\",\n" +
                                    "    \"residuals\",\n" +
                                    "    \"\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"76990\",\n" +
                                    "    \"1\",\n" +
                                    "    \"CS\",\n" +
                                    "    \"casting\",\n" +
                                    "    \"27\",\n" +
                                    "    \"postage\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"78277\",\n" +
                                    "    \"1\",\n" +
                                    "    \"CS\",\n" +
                                    "    \"casting\",\n" +
                                    "    \"47\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"78294\",\n" +
                                    "    \"1\",\n" +
                                    "    \"MS\",\n" +
                                    "    \"music\",\n" +
                                    "    \"\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"16041\",\n" +
                                    "    \"1\",\n" +
                                    "    \"CM\",\n" +
                                    "    \"comm\",\n" +
                                    "    \"\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"74546\",\n" +
                                    "    \"6\",\n" +
                                    "    \"PP\",\n" +
                                    "    \"group 6\",\n" +
                                    "    \"\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"39503\",\n" +
                                    "    \"2000\",\n" +
                                    "    \"PP\",\n" +
                                    "    \"agency\",\n" +
                                    "    \"2\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"16042\",\n" +
                                    "    \"1\",\n" +
                                    "    \"CM\",\n" +
                                    "    \"comm\",\n" +
                                    "    \"\",\n" +
                                    "    \"\"\n" +
                                    "  ],\n" +
                                    "  [\n" +
                                    "    \"75341\",\n" +
                                    "    \"1\",\n" +
                                    "    \"PP\",\n" +
                                    "    \"1 hr series\",\n" +
                                    "    \"\",\n" +
                                    "    \"accrue w/c\"\n" +
                                    "  ]\n" +
                                    "]"))));

        assertThat(subject.process("[EXCEL(" + file + ") => read(list46,A1:F13) json(true)]"),
                   allOf(is(not(nullValue())),
                         is(equalTo("[\n" +
                                    "  {\n" +
                                    "    \"number1\": \"69898\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"PP\",\n" +
                                    "    \"prod type\": \"30 min series\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"28520\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"PP\",\n" +
                                    "    \"prod type\": \"theat\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"15970\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"PP\",\n" +
                                    "    \"prod type\": \"mow\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"1\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"RS\",\n" +
                                    "    \"prod type\": \"residuals\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"76990\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"CS\",\n" +
                                    "    \"prod type\": \"casting\",\n" +
                                    "    \"fid\": \"27\",\n" +
                                    "    \"misc\": \"postage\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"78277\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"CS\",\n" +
                                    "    \"prod type\": \"casting\",\n" +
                                    "    \"fid\": \"47\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"78294\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"MS\",\n" +
                                    "    \"prod type\": \"music\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"16041\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"CM\",\n" +
                                    "    \"prod type\": \"comm\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"74546\",\n" +
                                    "    \"group\": \"6\",\n" +
                                    "    \"bus type\": \"PP\",\n" +
                                    "    \"prod type\": \"group 6\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"39503\",\n" +
                                    "    \"group\": \"2000\",\n" +
                                    "    \"bus type\": \"PP\",\n" +
                                    "    \"prod type\": \"agency\",\n" +
                                    "    \"fid\": \"2\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"16042\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"CM\",\n" +
                                    "    \"prod type\": \"comm\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"\"\n" +
                                    "  },\n" +
                                    "  {\n" +
                                    "    \"number1\": \"75341\",\n" +
                                    "    \"group\": \"1\",\n" +
                                    "    \"bus type\": \"PP\",\n" +
                                    "    \"prod type\": \"1 hr series\",\n" +
                                    "    \"fid\": \"\",\n" +
                                    "    \"misc\": \"accrue w/c\"\n" +
                                    "  }\n" +
                                    "]"))));
    }

    @Test
    public void processExcel_writes() throws Exception {
        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "9.xlsx");

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
    public void processSql_basic() throws Exception {
        // used in SQL file
        context.setData("code", "KXY");
        context.setData("min. Location ID", 2);

        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "13.sql");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String result = subject.process("[SQL(" + file + ") => execute(testdb) store(sql expression)]");
        System.out.println("result = " + result);

        result = subject.process("[SQL(sql expression) => resultCount]");
        assertNotNull(result);
        assertEquals("2", result);

        String expected = "description,fullAddress\n" +
                          "Kentucky AxeWye,\"710 W. Cainey Road , Townizel KY 42991 USA\"";
        assertEquals("1", subject.process("[SQL(sql expression) => rowCount(result1)]"));
        result = subject.process("[SQL(sql expression) => csv(result1) text]");
        assertNotNull(result);
        assertEquals(expected, result);

        expected = "description,fullAddress\n" +
                   "Metadeo Printing Ltd,\"70 Bowman St. , South Windsor CT 06074 USA\"\n" +
                   "Bovoid Serivce Inc.,\"8 Rockaway Ave. #105, Vernon Rockville CT 06066 USA\"\n" +
                   "Skafix Toy Factory,\"6 Van Dyke Street , Harrisburg PA 17091 USA\"";
        assertEquals("3", subject.process("[SQL(sql expression) => rowCount(result2)]"));
        assertEquals("2", subject.process("[SQL(sql expression) => columnCount(result2)]"));
        assertNull(subject.process("[SQL(sql expression) => error(result2)]"));
        assertEquals("description,fullAddress", subject.process("[SQL(sql expression) => columns(result2)]"));
        result = subject.process("[SQL(sql expression) => csv(result2) text]");
        assertNotNull(result);
        assertEquals(expected, result);
    }

    @Test
    public void processSql_extend_results() throws Exception {
        // used in SQL file
        context.setData("TAX Year", 2017);
        context.setData("Client ID", 73443);
        context.setData("Voucher ID", 541089);

        String file = ResourceUtils.getResourceFilePath(resourcePath + className + "14.sql");

        ExpressionProcessor subject = new ExpressionProcessor(context);

        String result = subject.process("[SQL(" + file + ") =>" +
                                        "   execute(testdb)" +
                                        "   store(sql expression)" +
                                        "   rowCount(processing log)" +
                                        "]");
        System.out.println("result = " + result);
        assertNotNull(result);

        int processingLogCount = NumberUtils.toInt(result);
        assertTrue(processingLogCount < 1);

        result = subject.process("[SQL(sql expression) => rowCount(vouchers)]");
        System.out.println("result = " + result);
        assertNotNull(result);

        int voucherCount = NumberUtils.toInt(result);
        assertTrue(voucherCount > 1);

        result = subject.process("[SQL(sql expression) => csv(vouchers)]");
        System.out.println("result = " + result);
        assertTrue(StringUtils.isNotBlank(result));
        assertTrue(StringUtils.countMatches(result, "\n") > 1);
    }

    private DataAccess initDataAccess() {
        Map<String, String> dbTypes = new HashMap<>();
        dbTypes.put("sqlite", "org.sqlite.JDBC");

        DataAccess da = new DataAccess();
        da.setDbTypes(dbTypes);
        return da;
    }
}
