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

package org.nexial.core.model;

import java.util.*;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.ExecutionContext.Function;
import org.nexial.core.variable.Date;
import org.nexial.core.variable.*;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.*;

public class ExecutionContextTest {
    @Before
    public void setUp() { }

    @After
    public void tearDown() { }

    @Test
    public void replaceTokens() {
        MockExecutionContext subject = initMockContext();
        subject.setData("a", new String[]{"Hello", "World", "Johnny boy"});

        Assert.assertEquals("Hello", subject.replaceTokens("${a}[0]"));
        Assert.assertEquals("World", subject.replaceTokens("${a}[1]"));
        Assert.assertEquals("Johnny boy", subject.replaceTokens("${a}[2]"));
        Assert.assertEquals("Hello,World,Johnny boy", subject.replaceTokens("${a}"));

        subject.setData("b", new String[][]{new String[]{"Otto", "Light", "Years Ahead"},
                                            new String[]{"Jixma", "Out", "Standing"}});
        Assert.assertEquals("Years Ahead", subject.replaceTokens("${b}[0][2]"));
        Assert.assertEquals("Otto,Light,Years Ahead,Jixma,Out,Standing", subject.replaceTokens("${b}"));

        HashSet<String> set = new LinkedHashSet<>();
        set.add("patty");
        set.add("petty");
        set.add("phatty");
        set.add("fatie");
        subject.setData("set", set);
        Assert.assertEquals("patty", subject.replaceTokens("${set}[0]"));
        Assert.assertEquals("petty", subject.replaceTokens("${set}[1]"));
        Assert.assertEquals("phatty", subject.replaceTokens("${set}[2]"));
        Assert.assertEquals("fatie", subject.replaceTokens("${set}[3]"));
        Assert.assertEquals("patty,petty,phatty,fatie", subject.replaceTokens("${set}"));

        Map<String, Integer> ages = new LinkedHashMap<>();
        ages.put("John", 14);
        ages.put("Sam", 19);
        ages.put("Johnny", 41);
        ages.put("Sammy", 91);
        subject.setData("ages", ages);
        Assert.assertEquals("14", subject.replaceTokens("${ages}.John"));
        Assert.assertEquals("19", subject.replaceTokens("${ages}.[Sam]"));
        Assert.assertEquals("19[my]", subject.replaceTokens("${ages}.Sam[my]"));
        Assert.assertEquals("91", subject.replaceTokens("${ages}.Sammy"));
        Assert.assertEquals("19 my", subject.replaceTokens("${ages}.Sam my"));
        Assert.assertEquals("19.my", subject.replaceTokens("${ages}.Sam.my"));
        Assert.assertEquals("19.Sammy", subject.replaceTokens("${ages}.Sam.Sammy"));
        Assert.assertEquals("41", subject.replaceTokens("${ages}.[Johnny]"));

        List<Map<String, List<Number>>> stuff = new ArrayList<>();
        Map<String, List<Number>> stuff1 = new LinkedHashMap<>();
        stuff1.put("Los Angeles", Arrays.asList(91, 55, 43, 20));
        stuff1.put("Chicago", Arrays.asList(55, 45, 91, 11));
        stuff1.put("New York", Arrays.asList(123, 11, 1, 0));
        stuff.add(stuff1);
        Map<String, List<Number>> stuff2 = new LinkedHashMap<>();
        stuff2.put("Banana", Arrays.asList(14.59, 15.01, 15.02));
        stuff2.put("Apple", Arrays.asList(11.55, 12, 12.31));
        stuff2.put("Chocolate", Collections.singletonList(8.50));
        stuff.add(stuff2);
        subject.setData("stuff", stuff);

        Assert.assertEquals("55", subject.replaceTokens("${stuff}[0].[Los Angeles][1]"));
        Assert.assertEquals("11", subject.replaceTokens("${stuff}[0].Chicago[3]"));
        Assert.assertEquals("14.59,15.01,15.02", subject.replaceTokens("${stuff}[1].Banana"));
        Assert.assertEquals("8.5", subject.replaceTokens("${stuff}[1].Chocolate"));
        Assert.assertEquals("8.5", subject.replaceTokens("${stuff}[1].Chocolate[0]"));
        // chocolate doesn't have 3 items, so we get empty string
        Assert.assertEquals("", subject.replaceTokens("${stuff}[1].Chocolate[2]"));

        // since there's index 2 of stuff, but this object doesn't contain Cinnamon, we'll get print-out of index 2
        Assert.assertEquals("[2]", subject.replaceTokens("${stuff}[1].Cinnamon[2]"));

        // since there's not index 15 of stuff, we'll get empty string
        Assert.assertEquals("", subject.replaceTokens("${stuff}[14]"));

        subject.cleanProject();
    }

    @Test
    public void replaceTokens_array_as_string() {
        MockExecutionContext subject = initMockContext();
        subject.setData("a", "Hello,World,Johnny boy");

        Assert.assertEquals("Hello", subject.replaceTokens("${a}[0]"));
        Assert.assertEquals("World", subject.replaceTokens("${a}[1]"));
        Assert.assertEquals("Johnny boy", subject.replaceTokens("${a}[2]"));
        Assert.assertEquals("Hello,World,Johnny boy", subject.replaceTokens("${a}"));

        Assert.assertEquals("[1]", subject.replaceTokens("${b}[1]"));

        subject.setData("just a number", 571);

        Assert.assertEquals("571", subject.replaceTokens("${just a number}"));
        Assert.assertEquals("571", subject.replaceTokens("${just a number}[0]"));

        Assert.assertEquals("Hello World!", subject.replaceTokens("${a}[0] ${a}[1]!"));
        Assert.assertEquals("Who's Johnny boy? Where in the World is he?",
                            subject.replaceTokens("Who's ${a}[2]? Where in the ${a}[1] is he?"));

        // invalid ref test
        Assert.assertEquals("", subject.replaceTokens("${a}[22]"));
        Assert.assertEquals("571[2]", subject.replaceTokens("${just a number}[2]"));
        Assert.assertEquals("Who's Johnny boy? Where in the World is he? How about ",
                            subject.replaceTokens("Who's ${a}[2]? Where in the ${a}[1] is he? How about ${a}[4]"));

        subject.setData(OPT_VAR_DEFAULT_AS_IS, "true");
        Assert.assertEquals("Who's Johnny boy? Where in the World is he? How about Hello,World,Johnny boy[4]",
                            subject.replaceTokens("Who's ${a}[2]? Where in the ${a}[1] is he? How about ${a}[4]"));

        subject.cleanProject();
    }

    @Test
    public void replaceTokens_array() {
        MockExecutionContext subject = initMockContext();
        subject.setData("a", "Hello,World,Johnny boy");
        subject.setData("b", "Hello World Johnny boy");

        Assert.assertEquals("Hello", subject.replaceTokens("$(array|item|${a}|0)"));
        Assert.assertEquals("World", subject.replaceTokens("$(array|item|${a}|1)"));
        Assert.assertEquals("Johnny boy", subject.replaceTokens("$(array|item|${a}|2)"));
        Assert.assertEquals("Hello,World,Johnny boy", subject.replaceTokens("${a}"));
        Assert.assertEquals("The World and Hello,World,Johnny boy",
                            subject.replaceTokens("The $(array|item|${a}|1) and ${a}"));
        Assert.assertEquals("This World ain't gotta\n" +
                            "hold on that Johnny boy!\n" +
                            "Ya, Hello and out!\n" +
                            "Hello,World,Johnny boy",
                            subject.replaceTokens("This $(array|item|${a}|1) ain't gotta\n" +
                                                  "hold on that $(array|item|${a}|2)!\n" +
                                                  "Ya, $(array|item|${a}|0) and out!\n" +
                                                  "${a}"));

        // out of bounds
        Assert.assertEquals("", subject.replaceTokens("$(array|item|${a}|3)"));
        Assert.assertEquals("", subject.replaceTokens("$(array|item|${a}|-2)"));

        // not array
        Assert.assertEquals("Hello World Johnny boy", subject.replaceTokens("${b}"));
        Assert.assertEquals("Hello World Johnny boy", subject.replaceTokens("$(array|item|${b}|0)"));
        Assert.assertEquals("", subject.replaceTokens("$(array|item|${b}|1)"));

        subject.cleanProject();
    }

    @Test
    public void replaceTokens_array_index() {
        MockExecutionContext subject = initMockContext();
        subject.setData("a", "Hello,World,Johnny boy");
        subject.setData("b", "Hello World Johnny boy");

        subject.setData("index", 0);
        Assert.assertEquals("Hello", subject.replaceTokens("${a}[${index}]"));
        Assert.assertEquals("Hello World Johnny boy[0]", subject.replaceTokens("${b}[${index}]"));

        subject.setData("index", 1);
        Assert.assertEquals("World", subject.replaceTokens("${a}[${index}]"));
    }

    @Test
    public void replaceTokens_ignored() {
        MockExecutionContext subject = initMockContext();
        subject.setData(OPT_VAR_EXCLUDE_LIST, "username,applications");
        subject.setData("a1", "blah");
        subject.setData("a2", "yada");
        subject.setData("username", "Me and I and myself");
        subject.setData("a3", "${a3}");

        Assert.assertEquals("The ${username} is blah and the ${applications} are yada yada",
                            subject.replaceTokens("The ${username} is ${a1} and the ${applications} are ${a2} ${a2}"));
        // 8 characters between there are 8 alphanumeric characters in '${username}'
        Assert.assertEquals("The ${username} has 8 characters",
                            subject.replaceTokens("The ${username} has $(count|alphanumeric|${username}) characters"));
        // 11 characters between there are 11 characters in '${username}'
        Assert.assertEquals("The ${username} has 11 characters",
                            subject.replaceTokens("The ${username} has $(count|size|${username}) characters"));
        Assert.assertEquals("The ${username} has 11 characters",
                            subject.replaceTokens("The ${username} has [TEXT(${username}) => length] characters"));
        Assert.assertEquals("The ${username} is ${USERNAME}",
                            subject.replaceTokens("The ${username} is [TEXT(${username}) => upper]"));

        subject.cleanProject();
    }

    @Test
    public void handleFunction() {
        MockExecutionContext subject = initMockContext();
        subject.setData("firstDOW", "04/30/2017");

        Assert.assertEquals("05/01/2017", subject.handleFunction("$(date|addDay|${firstDOW}|1)"));
        Assert.assertEquals("04/30/2017", subject.handleFunction("$(date|addDay|$(date|addDay|${firstDOW}|-1)|1)"));
        Assert.assertEquals("05/01/17",
                            subject.handleFunction("$(date|format|$(date|addDay|${firstDOW}|1)|MM/dd/yyyy|MM/dd/yy)"));

        System.out.println(subject.handleFunction(
            "$(date|format|$(date|addDay|$(sysdate|firstDOW|MM/dd/yyyy)|1)|MM/dd/yyyy|MM/dd/yy)"));

        subject.cleanProject();
    }

    @Test
    public void handleFunction_syspath() {
        MockExecutionContext subject = initMockContext();

        String theOtherSlash = StringUtils.equals(separator, "/") ? "\\" : "/";
        String fixture = "$(syspath|script|fullpath)" + theOtherSlash + "MyScript.xlsx";
        String actual = subject.handleFunction(fixture);
        System.out.println("actual = " + actual);
        Assert.assertThat(actual,
                          allOf(not(containsString(theOtherSlash)),
                                containsString("artifact" + separator + "script" + separator + "MyScript.xlsx")));

        subject.setData("MyFile", fixture);
        String actual2 = subject.replaceTokens("${MyFile}\\\\ 2 backslashes and // 2 forward slashes");
        System.out.println(actual2);
        Assert.assertThat(actual2, allOf(containsString(actual),
                                         containsString("\\\\ 2 backslashes and // 2 forward slashes")));

        subject.cleanProject();
    }

    @Test
    public void handleFunction_syspath_in_data() {
        MockExecutionContext subject = initMockContext();

        String theOtherSlash = StringUtils.equals(separator, "/") ? "\\" : "/";
        String fixture = "$(syspath|script|fullpath)" + theOtherSlash + "MyScript.xlsx";
        subject.setData("scriptPath", fixture);

        String actual = subject.replaceTokens("<MyWay>${scriptPath}<\\MyWay><YourWay/>");
        System.out.println("actual = " + actual);
        Assert.assertThat(actual,
                          allOf(endsWith("<\\MyWay><YourWay/>"),
                                containsString("artifact" + separator + "script" + separator + "MyScript.xlsx")));

        subject.setData("MyFile", fixture);
        String actual2 = subject.replaceTokens("${MyFile}\\\\ 2 backslashes and // 2 forward slashes");
        System.out.println(actual2);
        Assert.assertThat(actual2, allOf(containsString(subject.handleFunction(fixture)),
                                         containsString("\\\\ 2 backslashes and // 2 forward slashes")));

        subject.cleanProject();
    }

    @Test
    public void isFunction() throws Exception {
        MockExecutionContext subject = initMockContext();

        Assert.assertFalse(subject.isFunction(null));
        Assert.assertFalse(subject.isFunction(""));
        Assert.assertFalse(subject.isFunction(" "));
        Assert.assertTrue(subject.isFunction("array|remove|a"));

        subject.cleanProject();
    }

    @Test
    public void parseFunction() throws Exception {
        MockExecutionContext subject = initMockContext();

        Function f;

        f = toFunction(subject, "array|length|a,b,c");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("length", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);

        f = toFunction(subject, "array|distinct|a,b,c");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("distinct", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);

        f = toFunction(subject, "array|index|a,b,c|2");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("index", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);
        Assert.assertEquals("2", f.parameters[1]);

        f = toFunction(subject, "array|item|a,b,c|0");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("item", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);
        Assert.assertEquals("0", f.parameters[1]);

        f = toFunction(subject, "array|subarray|a,b,c|0|2");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("subarray", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);
        Assert.assertEquals("0", f.parameters[1]);
        Assert.assertEquals("2", f.parameters[2]);

        // change delim
        subject.setData(TEXT_DELIM, "|");

        f = toFunction(subject, "array|length|a,b,c");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("length", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);

        f = toFunction(subject, "array|distinct|a,b,c");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("distinct", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);

        f = toFunction(subject, "array|index|a,b,c|2");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("index", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);
        Assert.assertEquals("2", f.parameters[1]);

        f = toFunction(subject, "array|item|a,b,c|0");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("item", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);
        Assert.assertEquals("0", f.parameters[1]);

        f = toFunction(subject, "array|subarray|a,b,c|0|2");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("subarray", f.operation);
        Assert.assertEquals("a,b,c", f.parameters[0]);
        Assert.assertEquals("0", f.parameters[1]);
        Assert.assertEquals("2", f.parameters[2]);

        // use new delim
        f = toFunction(subject, "array|length|a\\|b\\|c");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("length", f.operation);
        Assert.assertEquals("a|b|c", f.parameters[0]);

        f = toFunction(subject, "array|distinct|a\\|b\\|c");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("distinct", f.operation);
        Assert.assertEquals("a|b|c", f.parameters[0]);

        f = toFunction(subject, "array|index|a\\|b\\|c|2");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("index", f.operation);
        Assert.assertEquals("a|b|c", f.parameters[0]);
        Assert.assertEquals("2", f.parameters[1]);

        f = toFunction(subject, "array|item|a\\|b\\|c|0");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("item", f.operation);
        Assert.assertEquals("a|b|c", f.parameters[0]);
        Assert.assertEquals("0", f.parameters[1]);

        f = toFunction(subject, "array|subarray|a\\|b\\|c|0|2");
        Assert.assertEquals("array", f.functionName);
        Assert.assertEquals("subarray", f.operation);
        Assert.assertEquals("a|b|c", f.parameters[0]);
        Assert.assertEquals("0", f.parameters[1]);
        Assert.assertEquals("2", f.parameters[2]);

        subject.cleanProject();
    }

    @Test
    public void invokeFunction() throws Exception {
        MockExecutionContext subject = initMockContext();

        // one param
        Assert.assertEquals("0", subject.replaceTokens("$(count|upper|there's no uppercase here)"));
        Assert.assertEquals("1", subject.replaceTokens("$(count|lower|THERE'S OnE LOWERCASE HERE)"));
        Assert.assertEquals("15", subject.replaceTokens("$(count|alphanumeric|1q2w3e4r 5t6y7u<>  [8])"));
        Assert.assertEquals("15", subject.replaceTokens("$(count|alphanumeric|this\\,is\\,a\\,long\\,text)"));

        // > 1 param
        Assert.assertEquals("5", subject.replaceTokens("$(array|length|this,is,a,long,text)"));
        Assert.assertEquals("a,is,long,text,this", subject.replaceTokens("$(array|ascending|this,is,a,long,text)"));
        Assert.assertEquals("this,is,text", subject.replaceTokens("$(array|pack|this,is,,,text,,,)"));
        Assert.assertEquals("had,a,little", subject.replaceTokens("$(array|subarray|mary,had,a,little,lamb|1|3)"));

        // nested functions
        Assert.assertEquals("HAD,A,LITTLE",
                            subject.replaceTokens("$(array|subarray|$(format|upper|mary,had,a,little,lamb)|1|3)"));
        Assert.assertEquals("had,a,very",
                            subject
                                .replaceTokens("$(array|subarray|$(array|insert|mary,had,a,little,lamb|3|very)|1|3)"));

        // change textDelim
        subject.setData(TEXT_DELIM, "|");
        Assert.assertEquals("mary,had,a,little,lamb",
                            subject.replaceTokens("$(array|subarray|mary,had,a,little,lamb|1|3)"));

        try {
            subject.replaceTokens("$(array|subarray|mary,had,a|little,lamb|1|3)");
            Assert.fail("Expected the above to fail");
        } catch (Exception e) {
            // expected
        }

        Assert.assertEquals("had|a|little",
                            subject.replaceTokens("$(array|subarray|mary\\|had\\|a\\|little\\|lamb|1|3)"));

        subject.setData(TEXT_DELIM, ",");
        Assert.assertEquals("had,a,little,had,a,little,had,a,little,had,a,little,had,a,little",
                            subject.replaceTokens("$(array|replica|$(array|subarray|mary,had,a,little,lamb|1|3)|5)"));

        subject.cleanProject();
    }

    @Test
    public void executionFunction() throws Exception {
        try {
            System.out.println(new Execution().meta("java"));
            System.out.println(new Execution().meta("nexial"));
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void notFoundKeptAsIs() throws Exception {
        MockExecutionContext subject = initMockContext();
        subject.setData(OPT_VAR_EXCLUDE_LIST, "applications");
        subject.setData("a1", "blah");
        subject.setData("a2", "yada");
        subject.setData("a3", "${a3}");
        subject.setData(OPT_VAR_DEFAULT_AS_IS, true);

        Assert.assertEquals("The ${username} is blah and the ${applications} are yada yada",
                            subject.replaceTokens("The ${username} is ${a1} and the ${applications} are ${a2} ${a2}"));
        // 8 characters between there are 8 alphanumeric characters in '${username}'
        Assert.assertEquals("The ${username} has 8 characters",
                            subject.replaceTokens("The ${username} has $(count|alphanumeric|${username}) characters"));
        // 11 characters between there are 11 characters in '${username}'
        Assert.assertEquals("The ${username} has 11 characters",
                            subject.replaceTokens("The ${username} has $(count|size|${username}) characters"));
        Assert.assertEquals("The ${username} has 11 characters",
                            subject.replaceTokens("The ${username} has [TEXT(${username}) => length] characters"));
        Assert.assertEquals("The ${username} is ${USERNAME}",
                            subject.replaceTokens("The ${username} is [TEXT(${username}) => upper]"));

        subject.cleanProject();
    }

    @Test
    public void replaceTokensInXML() throws Exception {
        String fixture =
            "<logger name=\"ch.qos.logback\" additivity=\"false\" level=\"WARN\"><appender-ref ref=\"${path1}\"/></logger>" +
            "<logger name=\"ch.qos.logback.classic.LoggerContext\" additivity=\"false\" level=\"WARN\"><appender-ref ref=\"console\">${path2}</appender></logger>" +
            "<logger name=\"ch.qos.logback.core\" additivity=\"false\" level=\"WARN\">${path3}</logger>";

        // test 1: replace Windows-path variables
        MockExecutionContext subject = initMockContext();
        subject.setData("path1", "C:\\temp\\junk1.txt");
        subject.setData("path2", "C:\\temp\\junk2.txt");
        subject.setData("path3", "C:\\temp\\junk3.txt");

        String expected =
            "<logger name=\"ch.qos.logback\" additivity=\"false\" level=\"WARN\"><appender-ref ref=\"C:\\temp\\junk1.txt\"/></logger>" +
            "<logger name=\"ch.qos.logback.classic.LoggerContext\" additivity=\"false\" level=\"WARN\"><appender-ref ref=\"console\">C:\\temp\\junk2.txt</appender></logger>" +
            "<logger name=\"ch.qos.logback.core\" additivity=\"false\" level=\"WARN\">C:\\temp\\junk3.txt</logger>";

        String actual = subject.replaceTokens(fixture);
        Assert.assertEquals(expected, actual);

        // test 2: use syspath on script
        subject.setData("path1", "$(syspath|script|base)\\junk1.txt");
        subject.setData("path2", "$(syspath|script|base)\\junk2.txt");
        subject.setData("path3", "$(syspath|script|base)\\junk3.txt");

        String scriptPath = System.getProperty(OPT_PROJECT_BASE) + separator + "artifact" + separator + "script";
        expected = "<logger name=\"ch.qos.logback\" additivity=\"false\" level=\"WARN\">" +
                   "<appender-ref ref=\"" + scriptPath + "\\junk1.txt\"/>" +
                   "</logger>" +
                   "<logger name=\"ch.qos.logback.classic.LoggerContext\" additivity=\"false\" level=\"WARN\">" +
                   "<appender-ref ref=\"console\">" + scriptPath + "\\junk2.txt</appender>" +
                   "</logger>" +
                   "<logger name=\"ch.qos.logback.core\" additivity=\"false\" level=\"WARN\">" +
                   scriptPath + "\\junk3.txt" +
                   "</logger>";

        actual = subject.replaceTokens(fixture);
        Assert.assertEquals(expected, actual);

        // test 3: use syspath on fullpath
        subject.setData("path1", "$(syspath|data|fullpath)\\junk1.txt");
        subject.setData("path2", "$(syspath|data|fullpath)\\junk2.txt");
        subject.setData("path3", "$(syspath|data|fullpath)\\junk3.txt");

        String dataPath = System.getProperty(OPT_PROJECT_BASE) + separator + "artifact" + separator + "data";
        expected = "<logger name=\"ch.qos.logback\" additivity=\"false\" level=\"WARN\">" +
                   "<appender-ref ref=\"" + dataPath + "/junk1.txt\"/>" +
                   "</logger>" +
                   "<logger name=\"ch.qos.logback.classic.LoggerContext\" additivity=\"false\" level=\"WARN\">" +
                   "<appender-ref ref=\"console\">" + dataPath + "/junk2.txt</appender>" +
                   "</logger>" +
                   "<logger name=\"ch.qos.logback.core\" additivity=\"false\" level=\"WARN\">" +
                   dataPath + "/junk3.txt" +
                   "</logger>";

        actual = subject.replaceTokens(fixture);
        Assert.assertEquals(expected, actual);

    }

    @NotNull
    private Function toFunction(ExecutionContext subject, String token) {
        Function f = subject.new Function();
        f.parse(token);
        return f;
    }

    @NotNull
    private MockExecutionContext initMockContext() {
        // for syspath
        String projectBase = JAVA_IO_TMPDIR + "dummy";
        String artifactBase = projectBase + separator + "artifact" + separator;
        System.setProperty(OPT_PROJECT_BASE, projectBase);
        System.setProperty(OPT_OUT_DIR, projectBase + separator + "output");
        System.setProperty(OPT_DATA_DIR, artifactBase + "data");
        System.setProperty(OPT_INPUT_EXCEL_FILE, artifactBase + "script" + separator + "MyScript.xlsx");

        MockExecutionContext subject = new MockExecutionContext();
        subject.setData(TEXT_DELIM, ",");
        subject.builtinFunctions = new HashMap<>();
        subject.builtinFunctions.put("array", new Array());
        subject.builtinFunctions.put("count", new Count());
        subject.builtinFunctions.put("date", new Date());
        subject.builtinFunctions.put("format", new Format());
        subject.builtinFunctions.put("sysdate", new Sysdate());
        subject.builtinFunctions.put("syspath", new Syspath());
        return subject;
    }
}