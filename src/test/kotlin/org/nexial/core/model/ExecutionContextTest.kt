/*
 * Copyright 2012-2022 the original author or authors.
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
package org.nexial.core.model

import org.apache.commons.lang3.StringUtils
import org.hamcrest.MatcherAssert
import org.hamcrest.core.AllOf
import org.hamcrest.core.IsNot
import org.hamcrest.core.StringContains
import org.hamcrest.core.StringEndsWith
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.nexial.core.NexialConst.*
import org.nexial.core.model.ExecutionContext.Function
import org.nexial.core.variable.*
import org.nexial.core.variable.Array
import java.io.File.separator

class ExecutionContextTest {
    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun replaceTokens() {
        val subject = initMockContext()

        subject.setData("a", arrayOf("Hello", "World", "Johnny boy"))

        assertEquals("Hello", subject.replaceTokens("\${a}[0]"))
        assertEquals("World", subject.replaceTokens("\${a}[1]"))
        assertEquals("Johnny boy", subject.replaceTokens("\${a}[2]"))
        assertEquals("Hello,World,Johnny boy", subject.replaceTokens("\${a}"))

        subject.setData("b", arrayOf(arrayOf("Otto", "Light", "Years Ahead"), arrayOf("Jixma", "Out", "Standing")))

        assertEquals("Years Ahead", subject.replaceTokens("\${b}[0][2]"))
        assertEquals("Otto,Light,Years Ahead,Jixma,Out,Standing", subject.replaceTokens("\${b}"))

        subject.setData("set", setOf("patty", "petty", "phatty", "fatie"))

        assertEquals("patty", subject.replaceTokens("\${set}[0]"))
        assertEquals("petty", subject.replaceTokens("\${set}[1]"))
        assertEquals("phatty", subject.replaceTokens("\${set}[2]"))
        assertEquals("fatie", subject.replaceTokens("\${set}[3]"))
        assertEquals("patty,petty,phatty,fatie", subject.replaceTokens("\${set}"))

        subject.setData("ages", mapOf("John" to 14, "Sam" to 19, "Johnny" to 41, "Sammy" to 91))

        assertEquals("14", subject.replaceTokens("\${ages}.John"))
        assertEquals("19", subject.replaceTokens("\${ages}.[Sam]"))
        assertEquals("19[my]", subject.replaceTokens("\${ages}.Sam[my]"))
        assertEquals("91", subject.replaceTokens("\${ages}.Sammy"))
        assertEquals("19 my", subject.replaceTokens("\${ages}.Sam my"))
        assertEquals("19.my", subject.replaceTokens("\${ages}.Sam.my"))
        assertEquals("19.Sammy", subject.replaceTokens("\${ages}.Sam.Sammy"))
        assertEquals("41", subject.replaceTokens("\${ages}.[Johnny]"))

        val stuff1 = mutableMapOf(
            "Los Angeles" to listOf<Number>(91, 55, 43, 20),
            "Chicago" to listOf<Number>(55, 45, 91, 11),
            "New York" to listOf<Number>(123, 11, 1, 0))
        val stuff2 = mutableMapOf(
            "Banana" to listOf<Number>(14.59, 15.01, 15.02),
            "Apple" to listOf<Number>(11.55, 12, 12.31),
            "Chocolate" to listOf<Number>(8.50))
        subject.setData("stuff", arrayListOf(stuff1, stuff2))

        assertEquals("55", subject.replaceTokens("\${stuff}[0].[Los Angeles][1]"))
        assertEquals("11", subject.replaceTokens("\${stuff}[0].Chicago[3]"))
        assertEquals("14.59,15.01,15.02", subject.replaceTokens("\${stuff}[1].Banana"))
        assertEquals("8.5", subject.replaceTokens("\${stuff}[1].Chocolate"))
        assertEquals("8.5", subject.replaceTokens("\${stuff}[1].Chocolate[0]"))
        // chocolate doesn't have 3 items, so we get empty string
        assertEquals("", subject.replaceTokens("\${stuff}[1].Chocolate[2]"))

        // since there's index 2 of stuff, but this object doesn't contain Cinnamon, we'll get print-out of index 2
        assertEquals("[2]", subject.replaceTokens("\${stuff}[1].Cinnamon[2]"))

        // since there's no index 15 of stuff, we'll get empty string
        assertEquals("", subject.replaceTokens("\${stuff}[14]"))

        subject.cleanProject()
    }

    @Test
    fun replaceTokens_array_as_string() {
        val subject = initMockContext()

        subject.setData("a", "Hello,World,Johnny boy")
        assertEquals("Hello", subject.replaceTokens("\${a}[0]"))
        assertEquals("World", subject.replaceTokens("\${a}[1]"))
        assertEquals("Johnny boy", subject.replaceTokens("\${a}[2]"))
        assertEquals("Hello,World,Johnny boy", subject.replaceTokens("\${a}"))
        assertEquals("[1]", subject.replaceTokens("\${b}[1]"))

        subject.setData("just a number", 571)
        assertEquals("571", subject.replaceTokens("\${just a number}"))
        assertEquals("571", subject.replaceTokens("\${just a number}[0]"))
        assertEquals("Hello World!", subject.replaceTokens("\${a}[0] \${a}[1]!"))
        assertEquals("Who's Johnny boy? Where in the World is he?",
                     subject.replaceTokens("Who's \${a}[2]? Where in the \${a}[1] is he?"))

        // invalid ref test
        assertEquals("", subject.replaceTokens("\${a}[22]"))
        assertEquals("571[2]", subject.replaceTokens("\${just a number}[2]"))
        assertEquals("Who's Johnny boy? Where in the World is he? How about ",
                     subject.replaceTokens("Who's \${a}[2]? Where in the \${a}[1] is he? How about \${a}[4]"))
        subject.setData(Data.OPT_VAR_DEFAULT_AS_IS, "true")
        assertEquals("Who's Johnny boy? Where in the World is he? How about Hello,World,Johnny boy[4]",
                     subject.replaceTokens("Who's \${a}[2]? Where in the \${a}[1] is he? How about \${a}[4]"))

        subject.cleanProject()
    }

    @Test
    fun replaceTokens_array() {
        val subject = initMockContext()
        subject.setData("a", "Hello,World,Johnny boy")
        subject.setData("b", "Hello World Johnny boy")
        assertEquals("Hello", subject.replaceTokens("$(array|item|\${a}|0)"))
        assertEquals("World", subject.replaceTokens("$(array|item|\${a}|1)"))
        assertEquals("Johnny boy", subject.replaceTokens("$(array|item|\${a}|2)"))
        assertEquals("Hello,World,Johnny boy", subject.replaceTokens("\${a}"))
        assertEquals("The World and Hello,World,Johnny boy",
                     subject.replaceTokens("The $(array|item|\${a}|1) and \${a}"))
        assertEquals("""
    This World ain't gotta
    hold on that Johnny boy!
    Ya, Hello and out!
    Hello,World,Johnny boy
    """.trimIndent(),
                     subject.replaceTokens("""
    This $(array|item|${"$"}{a}|1) ain't gotta
    hold on that $(array|item|${"$"}{a}|2)!
    Ya, $(array|item|${"$"}{a}|0) and out!
    ${"$"}{a}
    """.trimIndent()))

        // out of bounds
        assertEquals("", subject.replaceTokens("$(array|item|\${a}|3)"))
        assertEquals("", subject.replaceTokens("$(array|item|\${a}|-2)"))

        // not array
        assertEquals("Hello World Johnny boy", subject.replaceTokens("\${b}"))
        assertEquals("Hello World Johnny boy", subject.replaceTokens("$(array|item|\${b}|0)"))
        assertEquals("", subject.replaceTokens("$(array|item|\${b}|1)"))

        subject.cleanProject()
    }

    @Test
    fun replaceTokens_array_index() {
        val subject = initMockContext()
        subject.setData("a", "Hello,World,Johnny boy")
        subject.setData("b", "Hello World Johnny boy")

        subject.setData("index", 0)
        assertEquals("Hello", subject.replaceTokens("\${a}[\${index}]"))
        assertEquals("Hello World Johnny boy[0]", subject.replaceTokens("\${b}[\${index}]"))

        subject.setData("index", 1)
        assertEquals("World", subject.replaceTokens("\${a}[\${index}]"))
    }

    @Test
    fun replaceTokens_ignored() {
        val subject = initMockContext()
        subject.setData(Data.OPT_VAR_EXCLUDE_LIST, "username,applications")
        subject.setData("a1", "blah")
        subject.setData("a2", "yada")
        subject.setData("username", "Me and I and myself")
        subject.setData("a3", "\${a3}")

        assertEquals("The \${username} is blah and the \${applications} are yada yada",
                     subject.replaceTokens("The \${username} is \${a1} and the \${applications} are \${a2} \${a2}"))
        // 8 characters between there are 8 alphanumeric characters in '${username}'
        assertEquals("The \${username} has 8 characters",
                     subject.replaceTokens("The \${username} has $(count|alphanumeric|\${username}) characters"))
        // 11 characters between there are 11 characters in '${username}'
        assertEquals("The \${username} has 11 characters",
                     subject.replaceTokens("The \${username} has $(count|size|\${username}) characters"))
        assertEquals("The \${username} has 11 characters",
                     subject.replaceTokens("The \${username} has [TEXT(\${username}) => length] characters"))
        assertEquals("The \${username} is \${USERNAME}",
                     subject.replaceTokens("The \${username} is [TEXT(\${username}) => upper]"))

        subject.cleanProject()
    }

    @Test
    fun handleDateFunctions() {
        val subject = initMockContext()
        subject.setData("firstDOW", "04/30/2017")
        assertEquals("05/01/2017", subject.handleFunction("$(date|addDay|\${firstDOW}|1)"))
        assertEquals("04/30/2017", subject.handleFunction("$(date|addDay|$(date|addDay|\${firstDOW}|-1)|1)"))
        assertEquals("05/01/17",
                     subject.handleFunction("$(date|format|$(date|addDay|\${firstDOW}|1)|MM/dd/yyyy|MM/dd/yy)"))
        println(subject.handleFunction(
            "$(date|format|$(date|addDay|$(sysdate|firstDOW|MM/dd/yyyy)|1)|MM/dd/yyyy|MM/dd/yy)"))

        // diff
        subject.setData("date1", "04/30/2017 00:00:15")
        subject.setData("date2", "05/17/2017 21:49:22")
        assertEquals("17.91", subject.handleFunction("$(format|number|$(date|diff|\${date1}|\${date2}|DAY)|###.00)"))
        assertEquals("17.91", subject.handleFunction("$(format|number|$(date|diff|\${date1}|\${date2}|DAY)|###.##)"))
        assertEquals("2.6", subject.handleFunction("$(format|number|$(date|diff|\${date2}|\${date1}|WEEK)|0.#)"))
        assertEquals("1", subject.handleFunction("$(format|number|$(date|diff|\${date2}|\${date1}|MONTH)|#)"))
        assertEquals("0", subject.handleFunction("$(format|number|$(date|diff|\${date2}|\${date1}|YEAR)|0)"))
        assertEquals("0430", subject.handleFunction("$(format|number|$(date|diff|\${date1}|\${date2}|HOUR)|0000)"))
        assertEquals("25789.1167",
                     subject.handleFunction("$(format|number|$(date|diff|\${date1}|\${date2}|MINUTE)|#.####)"))
        assertEquals("1547347", subject.handleFunction("$(date|diff|\${date1}|\${date2}|SECOND)"))
        assertEquals("1547347000",
                     subject.handleFunction("$(format|number|$(date|diff|\${date1}|\${date2}|MILLISECOND)|0000)"))

        subject.cleanProject()
    }

    @Test
    fun handleFunction_syspath() {
        val subject = initMockContext()
        val theOtherSlash = if (StringUtils.equals(separator, "/")) "\\" else "/"
        val fixture = "$(syspath|script|fullpath)" + theOtherSlash + "MyScript.xlsx"
        val actual = subject.handleFunction(fixture)
        println("actual = $actual")
        MatcherAssert.assertThat(
            actual,
            AllOf.allOf(IsNot.not(StringContains.containsString(theOtherSlash)),
                        StringContains.containsString("artifact" + separator + "script" + separator + "MyScript.xlsx")))

        subject.setData("MyFile", fixture)
        val actual2 = subject.replaceTokens("\${MyFile}\\\\ 2 backslashes and // 2 forward slashes")
        println(actual2)
        MatcherAssert.assertThat(
            actual2,
            AllOf.allOf(StringContains.containsString(actual),
                        StringContains.containsString("\\\\ 2 backslashes and // 2 forward slashes")))

        subject.cleanProject()
    }

    @Test
    fun handleFunction_syspath_in_data() {
        val subject = initMockContext()
        val theOtherSlash = if (StringUtils.equals(separator, "/")) "\\" else "/"
        val fixture = "$(syspath|script|fullpath)" + theOtherSlash + "MyScript.xlsx"
        subject.setData("scriptPath", fixture)
        val actual = subject.replaceTokens("<MyWay>\${scriptPath}<\\MyWay><YourWay/>")
        println("actual = $actual")
        MatcherAssert.assertThat(
            actual,
            AllOf.allOf(StringEndsWith.endsWith("<\\MyWay><YourWay/>"),
                        StringContains.containsString("artifact" + separator + "script" + separator + "MyScript.xlsx")))

        subject.setData("MyFile", fixture)
        val actual2 = subject.replaceTokens("\${MyFile}\\\\ 2 backslashes and // 2 forward slashes")
        println(actual2)
        MatcherAssert.assertThat(
            actual2,
            AllOf.allOf(StringContains.containsString(subject.handleFunction(fixture)),
                        StringContains.containsString("\\\\ 2 backslashes and // 2 forward slashes")))

        subject.cleanProject()
    }

    @Test
    @Throws(Exception::class)
    fun isFunction() {
        val subject = initMockContext()
        assertFalse(subject.isFunction(null))
        assertFalse(subject.isFunction(""))
        assertFalse(subject.isFunction(" "))
        assertTrue(subject.isFunction("array|remove|a"))
        subject.cleanProject()
    }

    @Test
    @Throws(Exception::class)
    fun parseFunction() {
        val subject = initMockContext()

        var f = toFunction(subject, "array|length|a,b,c")
        assertEquals("array", f.functionName)
        assertEquals("length", f.operation)
        assertEquals("a,b,c", f.parameters[0])

        f = toFunction(subject, "array|distinct|a,b,c")
        assertEquals("array", f.functionName)
        assertEquals("distinct", f.operation)
        assertEquals("a,b,c", f.parameters[0])

        f = toFunction(subject, "array|index|a,b,c|2")
        assertEquals("array", f.functionName)
        assertEquals("index", f.operation)
        assertEquals("a,b,c", f.parameters[0])
        assertEquals("2", f.parameters[1])

        f = toFunction(subject, "array|item|a,b,c|0")
        assertEquals("array", f.functionName)
        assertEquals("item", f.operation)
        assertEquals("a,b,c", f.parameters[0])
        assertEquals("0", f.parameters[1])

        f = toFunction(subject, "array|subarray|a,b,c|0|2")
        assertEquals("array", f.functionName)
        assertEquals("subarray", f.operation)
        assertEquals("a,b,c", f.parameters[0])
        assertEquals("0", f.parameters[1])
        assertEquals("2", f.parameters[2])

        // change delim
        subject.setData(Data.TEXT_DELIM, "|")

        f = toFunction(subject, "array|length|a,b,c")
        assertEquals("array", f.functionName)
        assertEquals("length", f.operation)
        assertEquals("a,b,c", f.parameters[0])

        f = toFunction(subject, "array|distinct|a,b,c")
        assertEquals("array", f.functionName)
        assertEquals("distinct", f.operation)
        assertEquals("a,b,c", f.parameters[0])

        f = toFunction(subject, "array|index|a,b,c|2")
        assertEquals("array", f.functionName)
        assertEquals("index", f.operation)
        assertEquals("a,b,c", f.parameters[0])
        assertEquals("2", f.parameters[1])

        f = toFunction(subject, "array|item|a,b,c|0")
        assertEquals("array", f.functionName)
        assertEquals("item", f.operation)
        assertEquals("a,b,c", f.parameters[0])
        assertEquals("0", f.parameters[1])

        f = toFunction(subject, "array|subarray|a,b,c|0|2")
        assertEquals("array", f.functionName)
        assertEquals("subarray", f.operation)
        assertEquals("a,b,c", f.parameters[0])
        assertEquals("0", f.parameters[1])
        assertEquals("2", f.parameters[2])

        // use new delim
        f = toFunction(subject, "array|length|a\\|b\\|c")
        assertEquals("array", f.functionName)
        assertEquals("length", f.operation)
        assertEquals("a|b|c", f.parameters[0])

        f = toFunction(subject, "array|distinct|a\\|b\\|c")
        assertEquals("array", f.functionName)
        assertEquals("distinct", f.operation)
        assertEquals("a|b|c", f.parameters[0])

        f = toFunction(subject, "array|index|a\\|b\\|c|2")
        assertEquals("array", f.functionName)
        assertEquals("index", f.operation)
        assertEquals("a|b|c", f.parameters[0])
        assertEquals("2", f.parameters[1])

        f = toFunction(subject, "array|item|a\\|b\\|c|0")
        assertEquals("array", f.functionName)
        assertEquals("item", f.operation)
        assertEquals("a|b|c", f.parameters[0])
        assertEquals("0", f.parameters[1])

        f = toFunction(subject, "array|subarray|a\\|b\\|c|0|2")
        assertEquals("array", f.functionName)
        assertEquals("subarray", f.operation)
        assertEquals("a|b|c", f.parameters[0])
        assertEquals("0", f.parameters[1])
        assertEquals("2", f.parameters[2])

        subject.cleanProject()
    }

    @Test
    @Throws(Exception::class)
    fun invokeFunction() {
        val subject = initMockContext()

        // one param
        assertEquals("0", subject.replaceTokens("$(count|upper|there's no uppercase here)"))
        assertEquals("1", subject.replaceTokens("$(count|lower|THERE'S OnE LOWERCASE HERE)"))
        assertEquals("15", subject.replaceTokens("$(count|alphanumeric|1q2w3e4r 5t6y7u<>  [8])"))
        assertEquals("15", subject.replaceTokens("$(count|alphanumeric|this\\,is\\,a\\,long\\,text)"))

        // > 1 param
        assertEquals("5", subject.replaceTokens("$(array|length|this,is,a,long,text)"))
        assertEquals("a,is,long,text,this", subject.replaceTokens("$(array|ascending|this,is,a,long,text)"))
        assertEquals("this,is,text", subject.replaceTokens("$(array|pack|this,is,,,text,,,)"))
        assertEquals("had,a,little", subject.replaceTokens("$(array|subarray|mary,had,a,little,lamb|1|3)"))
        assertEquals("little,lamb", subject.replaceTokens("$(array|subarray|mary,had,a,little,lamb|3|-1)"))

        // nested functions
        assertEquals("HAD,A,LITTLE",
                     subject.replaceTokens("$(array|subarray|$(format|upper|mary,had,a,little,lamb)|1|3)"))
        assertEquals("had,a,very",
                     subject.replaceTokens("$(array|subarray|$(array|insert|mary,had,a,little,lamb|3|very)|1|3)"))

        // change textDelim
        subject.setData(Data.TEXT_DELIM, "|")
        assertEquals("mary,had,a,little,lamb",
                     subject.replaceTokens("$(array|subarray|mary,had,a,little,lamb|1|3)"))

        try {
            subject.replaceTokens("$(array|subarray|mary,had,a|little,lamb|1|3)")
            fail("Expected the above to fail")
        } catch (e: Exception) {
            // expected
        }

        assertEquals("had|a|little", subject.replaceTokens("$(array|subarray|mary\\|had\\|a\\|little\\|lamb|1|3)"))

        subject.setData(Data.TEXT_DELIM, ",")
        assertEquals("had,a,little,had,a,little,had,a,little,had,a,little,had,a,little",
                     subject.replaceTokens("$(array|replica|$(array|subarray|mary,had,a,little,lamb|1|3)|5)"))

        subject.cleanProject()
    }

    @Test
    @Throws(Exception::class)
    fun executionFunction() {
        try {
            println(Execution().meta("java"))
            println(Execution().meta("nexial"))
        } catch (e: Exception) {
            fail("Unexpected exception: $e")
        }
    }

    @Test
    @Throws(Exception::class)
    fun notFoundKeptAsIs() {
        val subject = initMockContext()
        subject.setData(Data.OPT_VAR_EXCLUDE_LIST, "applications")
        subject.setData("a1", "blah")
        subject.setData("a2", "yada")
        subject.setData("a3", "\${a3}")
        subject.setData(Data.OPT_VAR_DEFAULT_AS_IS, true)
        assertEquals("The \${username} is blah and the \${applications} are yada yada",
                     subject.replaceTokens("The \${username} is \${a1} and the \${applications} are \${a2} \${a2}"))

        // 8 characters between there are 8 alphanumeric characters in '${username}'
        assertEquals("The \${username} has 8 characters",
                     subject.replaceTokens("The \${username} has $(count|alphanumeric|\${username}) characters"))

        // 11 characters between there are 11 characters in '${username}'
        assertEquals("The \${username} has 11 characters",
                     subject.replaceTokens("The \${username} has $(count|size|\${username}) characters"))
        assertEquals("The \${username} has 11 characters",
                     subject.replaceTokens("The \${username} has [TEXT(\${username}) => length] characters"))
        assertEquals("The \${username} is \${USERNAME}",
                     subject.replaceTokens("The \${username} is [TEXT(\${username}) => upper]"))

        subject.cleanProject()
    }

    @Test
    @Throws(Exception::class)
    fun replaceTokensInXML() {
        val fixture = "<logger name=\"ch.qos.logback\" additivity=\"false\" level=\"WARN\">" +
                      "<appender-ref ref=\"\${path1}\"/>" +
                      "</logger>" +
                      "<logger name=\"ch.qos.logback.classic.LoggerContext\" additivity=\"false\" level=\"WARN\">" +
                      "<appender-ref ref=\"console\">\${path2}</appender>" +
                      "</logger>" +
                      "<logger name=\"ch.qos.logback.core\" additivity=\"false\" level=\"WARN\">\${path3}</logger>"

        // test 1: replace Windows-path variables
        val subject = initMockContext()
        subject.setData("path1", "C:\\temp\\junk1.txt")
        subject.setData("path2", "C:\\temp\\junk2.txt")
        subject.setData("path3", "C:\\temp\\junk3.txt")

        assertEquals("<logger name=\"ch.qos.logback\" additivity=\"false\" level=\"WARN\">" +
                     "<appender-ref ref=\"C:\\temp\\junk1.txt\"/>" +
                     "</logger>" +
                     "<logger name=\"ch.qos.logback.classic.LoggerContext\" additivity=\"false\" level=\"WARN\">" +
                     "<appender-ref ref=\"console\">C:\\temp\\junk2.txt</appender>" +
                     "</logger>" +
                     "<logger name=\"ch.qos.logback.core\" additivity=\"false\" level=\"WARN\">C:\\temp\\junk3.txt</logger>", 
                     subject.replaceTokens(fixture))

        // test 2: use syspath on script
        subject.setData("path1", "$(syspath|script|base)\\junk1.txt")
        subject.setData("path2", "$(syspath|script|base)\\junk2.txt")
        subject.setData("path3", "$(syspath|script|base)\\junk3.txt")
        val scriptPath = "${System.getProperty(OPT_PROJECT_BASE)}${separator}artifact${separator}script"

        assertEquals("<logger name=\"ch.qos.logback\" additivity=\"false\" level=\"WARN\">" +
                     "<appender-ref ref=\"$scriptPath\\junk1.txt\"/>" +
                     "</logger>" +
                     "<logger name=\"ch.qos.logback.classic.LoggerContext\" additivity=\"false\" level=\"WARN\">" +
                     "<appender-ref ref=\"console\">$scriptPath\\junk2.txt</appender>" +
                     "</logger>" +
                     "<logger name=\"ch.qos.logback.core\" additivity=\"false\" level=\"WARN\">$scriptPath\\junk3.txt</logger>",
                     subject.replaceTokens(fixture))

        // test 3: use syspath on fullpath
        subject.setData("path1", "$(syspath|data|fullpath)\\junk1.txt")
        subject.setData("path2", "$(syspath|data|fullpath)\\junk2.txt")
        subject.setData("path3", "$(syspath|data|fullpath)\\junk3.txt")
        val dataPath = "${System.getProperty(OPT_PROJECT_BASE)}${separator}artifact${separator}data${separator}"

        assertEquals("<logger name=\"ch.qos.logback\" additivity=\"false\" level=\"WARN\">" +
                     "<appender-ref ref=\"${dataPath}junk1.txt\"/>" +
                     "</logger>" +
                     "<logger name=\"ch.qos.logback.classic.LoggerContext\" additivity=\"false\" level=\"WARN\">" +
                     "<appender-ref ref=\"console\">${dataPath}junk2.txt</appender>" +
                     "</logger>" +
                     "<logger name=\"ch.qos.logback.core\" additivity=\"false\" level=\"WARN\">${dataPath}junk3.txt</logger>",
                     subject.replaceTokens(fixture))
    }

    @Test
    @Throws(Exception::class)
    fun handleExpression() {
        val subject = initMockContext()
        subject.setData("suggestions", "[\"croissant\",\"croissant\",\"croissant\"]")
        assertEquals(
            subject.replaceTokens("[TEXT(\${suggestions}) =>  removeStart([) removeEnd(]) remove(\") replace(\\,,~)]"),
            "croissant~croissant~croissant")
    }

    @Test
    @Throws(Exception::class)
    fun handleExpression_with_empty_data() {
        val subject = initMockContext()
        assertEquals("0", subject.replaceTokens("[LIST() => size]"))
        assertEquals("apple,orange", subject.replaceTokens("[LIST() => append(apple,orange)]"))
        assertEquals("0", subject.replaceTokens("[LIST() => append(apple,orange) removeItems(orange) remove(0) size]"))
        assertEquals("0", subject.replaceTokens("[LIST(\${no_such_data}) => size]"))
    }

    private fun toFunction(subject: ExecutionContext, token: String): Function {
        val f = subject.Function()
        f.parse(token)
        return f
    }

    private fun initMockContext(): MockExecutionContext {
        // for syspath
        val projectBase = TEMP + "dummy"
        val artifactBase = projectBase + separator + "artifact" + separator
        System.setProperty(OPT_PROJECT_BASE, projectBase)
        System.setProperty(OPT_OUT_DIR, projectBase + separator + "output")
        System.setProperty(OPT_DATA_DIR, artifactBase + "data")
        System.setProperty(OPT_INPUT_EXCEL_FILE, artifactBase + "script" + separator + "MyScript.xlsx")

        val subject = MockExecutionContext()
        subject.setData(Data.TEXT_DELIM, ",")
        subject.builtinFunctions = HashMap()
        subject.builtinFunctions["array"] = Array()
        subject.builtinFunctions["count"] = Count()
        subject.builtinFunctions["date"] = Date()
        subject.builtinFunctions["format"] = Format()
        subject.builtinFunctions["sysdate"] = Sysdate()
        subject.builtinFunctions["syspath"] = Syspath()
        return subject
    }
}