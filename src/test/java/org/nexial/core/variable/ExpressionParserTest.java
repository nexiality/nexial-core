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

package org.nexial.core.variable;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.variable.Expression.ExpressionFunction;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ExpressionParserTest {
    private MockExecutionContext context;

    @Before
    public void init() { context = new MockExecutionContext(); }

    @After
    public void tearDown() {
        if (context != null) { context.cleanProject(); }
    }

    @Test
    public void parse() throws Exception {

        ExpressionParser subject = new ExpressionParser(context);

        // null test
        assertNull(subject.parse(null));
        assertNull(subject.parse(""));
        assertNull(subject.parse(" "));
        assertNull(subject.parse(" \t \t \n  \t \r     \t\t"));
        assertNull(subject.parse("this is a test, and there's nothing to do here"));
        assertNull(subject.parse("you know, JSON's my favorite son!"));
        assertNull(subject.parse("Everyone's list include ['JSON', 'TEXT', 'CSV']"));
        assertNull(subject.parse("Where the [JSON of a ...] is that?"));
        assertNull(subject.parse("blah blah blah [TEXT(yada yada)] not valid"));

        // simple test
        String fixture = "[TEXT(hello world) => upper unique length]";
        Expression expr = subject.parse(fixture);
        assertNotNull(expr);
        assertEquals("TEXT", expr.getDataType().getName());
        assertEquals("hello world", expr.getDataType().getTextValue());

        List<ExpressionFunction> functions = expr.getFunctions();
        assertEquals(3, CollectionUtils.size(functions));
        assertEquals("upper", functions.get(0).getFunctionName());
        assertEquals("unique", functions.get(1).getFunctionName());
        assertEquals("length", functions.get(2).getFunctionName());

        assertEquals(fixture, expr.getOriginalExpression());
    }

    @Test
    public void parse_tight_space() throws Exception {
        ExpressionParser subject = new ExpressionParser(context);
        assertSimpleExpression(subject.parse("[TEXT(hello world)=>upper unique length]"));
        assertSimpleExpression(subject.parse("[TEXT(hello world)=>upper()unique()length]"));
        assertSimpleExpression(subject.parse("[TEXT(hello world)\t=>\tupper()\nunique()      \n \t   \n\n\r\nlength]"));
        assertSimpleExpression(subject.parse("[TEXT(hello world)\t=>\n\n\nupper()\r\n\t\tunique()   \n\n\r\nlength]"));
    }

    @Test
    public void parse_tight_space_with_params() throws Exception {
        ExpressionParser subject = new ExpressionParser(context);
        assertSimpleExpression2(subject.parse("[LIST(hello,world,what's,up?!)=>" +
                                              " insert(2,and) join(good,to,meet,you)]      "));
        assertSimpleExpression2(subject.parse("[LIST(hello,world,what's,up?!)=>insert(2,and)join(good,to,meet,you)]"));
        assertSimpleExpression2(subject.parse("[LIST(hello,world,what's,up?!)=>insert(2,and)join(good,to,meet,you) ]"));
    }

    @Test
    public void collectFunctionGroups() throws Exception {
        ExpressionParser subject = new ExpressionParser(context);

        List<String> functionGroups = new ArrayList<>();
        subject.collectFunctionGroups("", functionGroups);
        assertTrue(functionGroups.isEmpty());

        subject.collectFunctionGroups("  \t\n\n\t  \n", functionGroups);
        assertTrue(functionGroups.isEmpty());

        subject.collectFunctionGroups("operation", functionGroups);
        assertEquals(1, functionGroups.size());
        assertEquals("operation", functionGroups.get(0));

        functionGroups.clear();
        subject.collectFunctionGroups("op1\n op2 \n\tallPass", functionGroups);
        assertEquals(3, functionGroups.size());
        assertEquals("op1", functionGroups.get(0));
        assertEquals("op2", functionGroups.get(1));
        assertEquals("allPass", functionGroups.get(2));

        functionGroups.clear();
        subject.collectFunctionGroups("ascii-table()\n wait(1000) \n\t", functionGroups);
        assertEquals(2, functionGroups.size());
        assertEquals("ascii-table()", functionGroups.get(0));
        assertEquals("wait(1000)", functionGroups.get(1));

        functionGroups.clear();
        subject.collectFunctionGroups("click(css=#id1)\n select(//*[@id='id2'],California)\n\t", functionGroups);
        assertEquals(2, functionGroups.size());
        assertEquals("click(css=#id1)", functionGroups.get(0));
        assertEquals("select(//*[@id='id2'],California)", functionGroups.get(1));

        System.out.println();
        functionGroups.clear();
        subject.collectFunctionGroups(" type(//input[contains(@name,'email')],no1home@yahoo.com) " +
                                      "type(//input[contains(@name, 'password') and (@name=\"password\")],passmeNOT!) " +
                                      "  wait   " +
                                      "click(//input[contains(@name,  'password') and contains(@name,\"password\")]) " +
                                      "allPass",
                                      functionGroups);
        assertEquals(5, functionGroups.size());
        assertEquals("type(//input[contains(@name,'email')],no1home@yahoo.com)", functionGroups.get(0));
        assertEquals("type(//input[contains(@name, 'password') and (@name=\"password\")],passmeNOT!)", functionGroups.get(1));
        assertEquals("wait", functionGroups.get(2));
        assertEquals("click(//input[contains(@name,  'password') and contains(@name,\"password\")])", functionGroups.get(3));
        assertEquals("allPass", functionGroups.get(4));

        System.out.println();
        functionGroups.clear();
        subject.collectFunctionGroups(
            " click(//fieldset[ ./legend[normalize-space(string(.))='Login Information'] ]//input[@value='Save']) \n" +
            "\tclick (//fieldset[ ./legend[normalize-space(string(.))='Address Information'] ]//input[@value='Save'])\n" +
            " wait(1000)  " +
            "type(//table[@id='tblUsers']//tr[ ./td[normalize-space(string(.))='${NewTestUser.firstName} ${NewTestUser.lastName}'] ]//a[@title='Edit User'],crypt:234810235978139847376) \n " +
            "allPass",
            functionGroups);
        System.out.println(TextUtils.toString(functionGroups,"\n"));
        assertEquals(5, functionGroups.size());
        assertEquals("click(//fieldset[ ./legend[normalize-space(string(.))='Login Information'] ]//input[@value='Save'])",
                     functionGroups.get(0));
        assertEquals("click(//fieldset[ ./legend[normalize-space(string(.))='Address Information'] ]//input[@value='Save'])",
                     functionGroups.get(1));
        assertEquals("wait(1000)", functionGroups.get(2));
        assertEquals("type(//table[@id='tblUsers']//tr[ ./td[normalize-space(string(.))='${NewTestUser.firstName} ${NewTestUser.lastName}'] ]//a[@title='Edit User'],crypt:234810235978139847376)",
                     functionGroups.get(3));
        assertEquals("allPass", functionGroups.get(4));

        System.out.println();
        functionGroups.clear();
        subject.collectFunctionGroups(" click(//fieldset[contains (@name, 'sizable' ) ] )\n" +
                                      " wait(1000)  ",
                                      functionGroups);
        System.out.println(TextUtils.toString(functionGroups,"\n"));
        assertEquals(2, functionGroups.size());
        assertEquals("click(//fieldset[contains(@name, 'sizable' ) ] )", functionGroups.get(0));
        assertEquals("wait(1000)", functionGroups.get(1));
    }

    @Test
    public void collectFunctionGroups2() throws Exception {
        ExpressionParser subject = new ExpressionParser(context);

        List<String> functionGroups = new ArrayList<>();

        System.out.println();
        functionGroups.clear();
        subject.collectFunctionGroups("merge(apple,orange,chicken) size multiply(16.44)", functionGroups);
        System.out.println(TextUtils.toString(functionGroups,"\n"));
        assertEquals(3, functionGroups.size());
        assertEquals("merge(apple,orange,chicken)", functionGroups.get(0));
        assertEquals("size", functionGroups.get(1));
        assertEquals("multiply(16.44)", functionGroups.get(2));

        System.out.println();
        functionGroups.clear();
        subject.collectFunctionGroups("extract([name=REGEX:(.*e.*){2,}].group) list descending  combine(,) remove(\") text",
                                      functionGroups);
        System.out.println(TextUtils.toString(functionGroups,"\n"));
        assertEquals(6, functionGroups.size());
        assertEquals("extract([name=REGEX:(.*e.*){2,}].group)", functionGroups.get(0));
        assertEquals("list", functionGroups.get(1));
        assertEquals("descending", functionGroups.get(2));
        assertEquals("combine(,)", functionGroups.get(3));
        assertEquals("remove(\")", functionGroups.get(4));
        assertEquals("text", functionGroups.get(5));

        System.out.println();
        functionGroups.clear();
        subject.collectFunctionGroups("extract([name=REGEX:(.*e.*\\){2,}].group) list combine(\\,) remove(\") text",
                                      functionGroups);
        System.out.println(TextUtils.toString(functionGroups,"\n"));
        assertEquals(5, functionGroups.size());
        assertEquals("extract([name=REGEX:(.*e.*\\){2,}].group)", functionGroups.get(0));
        assertEquals("list", functionGroups.get(1));
        assertEquals("combine(\\,)", functionGroups.get(2));
        assertEquals("remove(\")", functionGroups.get(3));
        assertEquals("text", functionGroups.get(4));
    }

    private static void assertSimpleExpression(Expression expr) {
        List<ExpressionFunction> functions;
        functions = expr.getFunctions();
        assertNotNull(expr);
        assertEquals("TEXT", expr.getDataType().getName());
        assertEquals("hello world", expr.getDataType().getTextValue());
        assertEquals(3, functions.size());
        assertEquals("upper", functions.get(0).getFunctionName());
        assertTrue(functions.get(0).getParams().isEmpty());
        assertEquals("unique", functions.get(1).getFunctionName());
        assertTrue(functions.get(1).getParams().isEmpty());
        assertEquals("length", functions.get(2).getFunctionName());
        assertTrue(functions.get(2).getParams().isEmpty());
    }

    private static void assertSimpleExpression2(Expression expr) {
        List<ExpressionFunction> functions;
        functions = expr.getFunctions();
        assertNotNull(expr);
        assertEquals("LIST", expr.getDataType().getName());
        assertEquals("hello,world,what's,up?!", expr.getDataType().getTextValue());
        assertEquals(2, functions.size());
        assertEquals("insert", functions.get(0).getFunctionName());
        assertEquals("[2, and]", functions.get(0).getParams().toString());
        assertEquals("join", functions.get(1).getFunctionName());
        assertEquals("[good, to, meet, you]", functions.get(1).getParams().toString());
    }
}