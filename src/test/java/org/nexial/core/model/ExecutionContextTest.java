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
import org.nexial.core.variable.*;
import org.nexial.core.variable.Date;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.NexialConst.*;

public class ExecutionContextTest {
    @Before
    public void setUp() { }

    @After
    public void tearDown() { }

    @Test
    public void replaceTokens() {
        ExecutionContext subject = initMockContext();
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
    }

    @Test
    public void replaceTokens_array() {
        ExecutionContext subject = initMockContext();
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
    }

    @Test
    public void handleFunction() {
        ExecutionContext subject = initMockContext();
        subject.setData("firstDOW", "04/30/2017");

        Assert.assertEquals("05/01/2017", subject.handleFunction("$(date|addDay|${firstDOW}|1)"));
        Assert.assertEquals("04/30/2017", subject.handleFunction("$(date|addDay|$(date|addDay|${firstDOW}|-1)|1)"));
        Assert.assertEquals("05/01/17",
                            subject.handleFunction("$(date|format|$(date|addDay|${firstDOW}|1)|MM/dd/yyyy|MM/dd/yy)"));

        System.out.println(subject.handleFunction(
            "$(date|format|$(date|addDay|$(sysdate|firstDOW|MM/dd/yyyy)|1)|MM/dd/yyyy|MM/dd/yy)"));
    }

    @Test
    public void handleFunction_syspath() {
        ExecutionContext subject = initMockContext();

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
    }

    @Test
    public void handleFunction_syspath_in_data() {
        ExecutionContext subject = initMockContext();

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
    }

    @Test
    public void isFunction() throws Exception {
        ExecutionContext subject = initMockContext();

        Assert.assertFalse(subject.isFunction(null));
        Assert.assertFalse(subject.isFunction(""));
        Assert.assertFalse(subject.isFunction(" "));
        Assert.assertTrue(subject.isFunction("array|remove|a"));
    }

    @Test
    public void parseFunction() throws Exception {
        ExecutionContext subject = initMockContext();

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
    }

    @Test
    public void invokeFunction() throws Exception {
        ExecutionContext subject = initMockContext();

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
    }

    @NotNull
    private Function toFunction(ExecutionContext subject, String token) {
        Function f = subject.new Function();
        f.parse(token);
        return f;
    }

    @NotNull
    private ExecutionContext initMockContext() {
        // for syspath
        String projectBase = JAVA_IO_TMPDIR + "dummy";
        System.setProperty(OPT_PROJECT_BASE, projectBase);
        System.setProperty(OPT_OUT_DIR, projectBase + separator + "output");
        System.setProperty(OPT_INPUT_EXCEL_FILE, projectBase + separator +
                                                 "artifact" + separator +
                                                 "script" + separator +
                                                 "MyScript.xlsx");

        ExecutionContext subject = new MockExecutionContext();
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