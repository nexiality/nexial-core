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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NumberDataTypeTest {

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testCreate() throws Exception {
        Assert.assertEquals("0", new NumberDataType("").getValue() + "");
        Assert.assertEquals("0", new NumberDataType(" ").getValue() + "");
        Assert.assertEquals("0", new NumberDataType("\t").getValue() + "");
        Assert.assertEquals("0", new NumberDataType("\n\n\t  \t\n").getValue() + "");
        Assert.assertEquals("0", new NumberDataType("0").getValue() + "");
        Assert.assertEquals("0", new NumberDataType("00").getValue() + "");
        Assert.assertEquals("0", new NumberDataType("000000").getValue() + "");
        Assert.assertEquals("23405", new NumberDataType("00000023405").getValue() + "");

        Assert.assertEquals("2943500.1", new NumberDataType("00002943500.1").getValue() + "");
        Assert.assertEquals("125.22", new NumberDataType("+125.22").getValue() + "");
        Assert.assertEquals("125.22", new NumberDataType("+000125.2200").getValue() + "");
        Assert.assertEquals("0.0", new NumberDataType("+0000.000").getValue() + "");
        Assert.assertEquals("0", new NumberDataType("+0000").getValue() + "");
        Assert.assertEquals("0", new NumberDataType("-0000").getValue() + "");
        Assert.assertEquals("-0.0", new NumberDataType("-0000.00").getValue() + "");
        Assert.assertEquals("-0.01", new NumberDataType("-0000.01").getValue() + "");
        Assert.assertEquals("-0.01", new NumberDataType("-0000.01000").getValue() + "");
        Assert.assertEquals("-102040.01", new NumberDataType("-0000102040.01000").getValue() + "");
        Assert.assertEquals("-0.01", new NumberDataType("-.01000").getValue() + "");
        Assert.assertEquals("3.17261524E8", new NumberDataType("3.17261524E8").getValue() + "");
        Assert.assertEquals("317261524", new NumberDataType("3.17261524E8").getTextValue());

        try {
            System.out.println(new NumberDataType("3.123.567"));
            Assert.fail("EXPECTED TypeConversionException not thrown");
        } catch (TypeConversionException e) {
            // expected
        }

        try {
            System.out.println(new NumberDataType("EEE098E765#,11.2.1"));
            Assert.fail("EXPECTED TypeConversionException not thrown");
        } catch (TypeConversionException e) {
            // expected
        }

        try {
            System.out.println(new NumberDataType("EEE"));
            Assert.fail("EXPECTED TypeConversionException not thrown");
        } catch (TypeConversionException e) {
            // expected
        }

        try {
            System.out.println(new NumberDataType("12E34E56E"));
            Assert.fail("EXPECTED TypeConversionException not thrown");
        } catch (TypeConversionException e) {
            // expected
        }
    }
}