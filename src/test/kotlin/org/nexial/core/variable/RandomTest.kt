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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Assert;
import org.junit.Test;

public class RandomTest {

    @Test
    public void testInteger() throws Exception {
        Random fixture = new Random();
        fixture.init();

        String data = fixture.integer("a");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.integer(null);
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.integer("");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.integer("16");
        Assert.assertNotNull(data);
        Assert.assertEquals(16, data.length());
        Assert.assertTrue(NumberUtils.isDigits(data));
    }

    @Test
    public void testDecimal() throws Exception {
        Random fixture = new Random();
        fixture.init();

        String data = fixture.decimal("a");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.decimal(null);
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.decimal("");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.decimal("16");
        Assert.assertNotNull(data);
        Assert.assertTrue(data.length() <= 16);
        Assert.assertTrue(NumberUtils.isDigits(data));

        data = fixture.decimal("16,");
        Assert.assertNotNull(data);
        Assert.assertTrue(data.length() <= 16);
        Assert.assertTrue(NumberUtils.isDigits(data));

        data = fixture.decimal("16,0");
        Assert.assertNotNull(data);
        Assert.assertTrue(data.length() <= 16);
        Assert.assertTrue(NumberUtils.isCreatable(data));

        data = fixture.decimal("16,a");
        Assert.assertNotNull(data);
        Assert.assertTrue(data.length() <= 16);
        Assert.assertTrue(NumberUtils.isCreatable(data));

        data = fixture.decimal("16,3");
        Assert.assertNotNull(data);
        Assert.assertTrue(data.length() <= 20);
        Assert.assertTrue(NumberUtils.isCreatable(data));

        // test that there's no preceding zero
        for (int i = 0; i < 100; i++) {
            data = fixture.decimal("30,3");
            Assert.assertNotNull(data);
            Assert.assertTrue(data.length() <= 34);
            Assert.assertTrue(NumberUtils.isCreatable(data));
        }
    }

    @Test
    public void testLetter() throws Exception {
        Random fixture = new Random();
        fixture.init();

        String data = fixture.letter("a");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.letter(null);
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.letter("");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.letter("16");
        Assert.assertNotNull(data);
        Assert.assertEquals(16, data.length());
        Assert.assertTrue(StringUtils.isAlpha(data));
    }

    @Test
    public void testAlphanumeric() throws Exception {
        Random fixture = new Random();
        fixture.init();

        String data = fixture.alphanumeric("a");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.alphanumeric(null);
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.alphanumeric("");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.alphanumeric("16");
        Assert.assertNotNull(data);
        Assert.assertEquals(16, data.length());
        Assert.assertTrue(StringUtils.isAlphanumeric(data));

        data = fixture.alphanumeric("2");
        Assert.assertNotNull(data);
        Assert.assertEquals(2, data.length());
        Assert.assertTrue(StringUtils.isAlphanumeric(data));
    }

    @Test
    public void testAny() throws Exception {
        Random fixture = new Random();
        fixture.init();

        String data = fixture.any("a");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.any(null);
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.any("");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.any("16");
        System.out.println("data = " + data);
        Assert.assertNotNull(data);
        Assert.assertEquals(16, data.length());
        Assert.assertTrue(StringUtils.isAsciiPrintable(data));

        data = fixture.any("2");
        System.out.println("data = " + data);
        Assert.assertNotNull(data);
        Assert.assertEquals(2, data.length());
        Assert.assertTrue(StringUtils.isAsciiPrintable(data));
    }

    @Test
    public void testCharacters() throws Exception {
        Random fixture = new Random();
        fixture.init();

        String data = fixture.characters(null, "a");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.characters("abcde", null);
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.characters("12345", "");
        Assert.assertNotNull(data);
        Assert.assertEquals("", data);

        data = fixture.characters("abcde", "16");
        System.out.println("data = " + data);
        Assert.assertNotNull(data);
        Assert.assertEquals(16, data.length());
        Assert.assertTrue(StringUtils.containsOnly(data, "abcde".toCharArray()));

        data = fixture.characters("4", "2");
        System.out.println("data = " + data);
        Assert.assertNotNull(data);
        Assert.assertEquals(2, data.length());
        Assert.assertTrue(StringUtils.containsOnly(data, "4".toCharArray()));

        data = fixture.characters("abcdefg", "5");
        System.out.println("data = " + data);
        Assert.assertNotNull(data);
        Assert.assertEquals(5, data.length());
        Assert.assertTrue(StringUtils.containsAny(data, "abcdefg".toCharArray()));
    }
}