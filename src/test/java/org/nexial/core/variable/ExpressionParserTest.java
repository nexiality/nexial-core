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

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.variable.Expression.ExpressionFunction;

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
        Assert.assertNull(subject.parse(null));
        Assert.assertNull(subject.parse(""));
        Assert.assertNull(subject.parse(" "));
        Assert.assertNull(subject.parse(" \t \t \n  \t \r     \t\t"));
        Assert.assertNull(subject.parse("this is a test, and there's nothing to do here"));
        Assert.assertNull(subject.parse("you know, JSON's my favorite son!"));
        Assert.assertNull(subject.parse("Everyone's list include ['JSON', 'TEXT', 'CSV']"));
        Assert.assertNull(subject.parse("Where the [JSON of a ...] is that?"));
        Assert.assertNull(subject.parse("blah blah blah [TEXT(yada yada)] not valid"));

        // simple test
        String fixture = "[TEXT(hello world) => upper unique length]";
        Expression expr = subject.parse(fixture);
        Assert.assertNotNull(expr);
        Assert.assertEquals("TEXT", expr.getDataType().getName());
        Assert.assertEquals("hello world", expr.getDataType().getTextValue());

        List<ExpressionFunction> functions = expr.getFunctions();
        Assert.assertEquals(3, CollectionUtils.size(functions));
        Assert.assertEquals("upper", functions.get(0).getFunctionName());
        Assert.assertEquals("unique", functions.get(1).getFunctionName());
        Assert.assertEquals("length", functions.get(2).getFunctionName());

        Assert.assertEquals(fixture, expr.getOriginalExpression());
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

    private static void assertSimpleExpression(Expression expr) {
        List<ExpressionFunction> functions;
        functions = expr.getFunctions();
        Assert.assertNotNull(expr);
        Assert.assertEquals("TEXT", expr.getDataType().getName());
        Assert.assertEquals("hello world", expr.getDataType().getTextValue());
        Assert.assertEquals(3, functions.size());
        Assert.assertEquals("upper", functions.get(0).getFunctionName());
        Assert.assertTrue(functions.get(0).getParams().isEmpty());
        Assert.assertEquals("unique", functions.get(1).getFunctionName());
        Assert.assertTrue(functions.get(1).getParams().isEmpty());
        Assert.assertEquals("length", functions.get(2).getFunctionName());
        Assert.assertTrue(functions.get(2).getParams().isEmpty());
    }

    private static void assertSimpleExpression2(Expression expr) {
        List<ExpressionFunction> functions;
        functions = expr.getFunctions();
        Assert.assertNotNull(expr);
        Assert.assertEquals("LIST", expr.getDataType().getName());
        Assert.assertEquals("hello,world,what's,up?!", expr.getDataType().getTextValue());
        Assert.assertEquals(2, functions.size());
        Assert.assertEquals("insert", functions.get(0).getFunctionName());
        Assert.assertEquals("[2, and]", functions.get(0).getParams().toString());
        Assert.assertEquals("join", functions.get(1).getFunctionName());
        Assert.assertEquals("[good, to, meet, you]", functions.get(1).getParams().toString());
    }
}