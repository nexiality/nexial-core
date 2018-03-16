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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CountTest {
    private Count count;

    @Before
    public void setup() {
        count = new Count();
    }

    @Test
    public void testUpper() {
        Assert.assertEquals(count.upper(null), "0");
        Assert.assertEquals(count.upper(""), "0");
        Assert.assertEquals(count.upper(" T H I S "), "4");
        Assert.assertEquals(count.upper(" TtHhIiSs "), "4");
    }

    @Test
    public void testLower() {
        Assert.assertEquals(count.lower(null), "0");
        Assert.assertEquals(count.lower(""), "0");
        Assert.assertEquals(count.lower(" T H I S "), "0");
        Assert.assertEquals(count.lower(" TtHhIiSs "), "4");
        Assert.assertEquals(count.lower("Equivalent to java.lang.Character.isLowerCase()"), "36");
    }

    @Test
    public void testNumber() {
        Assert.assertEquals(count.number(null), "0");
        Assert.assertEquals(count.number(""), "0");
        Assert.assertEquals(count.number(" T H I S "), "0");
        Assert.assertEquals(count.number("A decimal digit: [0-9]"), "2");
        Assert.assertEquals(count.number("-143.25f"), "5");
    }

    @Test
    public void testAlphanumeric() {
        Assert.assertEquals(count.alphanumeric(null), "0");
        Assert.assertEquals(count.alphanumeric(""), "0");
        Assert.assertEquals(count.alphanumeric(" T H I S "), "4");
        Assert.assertEquals(count.alphanumeric("A decimal digit: [0-9]"), "15");
        Assert.assertEquals(count.alphanumeric("-143.25f"), "6");
    }

    @Test
    public void testWhitespace() {
        Assert.assertEquals(count.whitespace(null), "0");
        Assert.assertEquals(count.whitespace(""), "0");
        Assert.assertEquals(count.whitespace(" T H I S "), "5");
        Assert.assertEquals(count.whitespace(" \t\n \r "), "6");
        Assert.assertEquals(count.whitespace("\t"), "1");
    }

    @Test
    public void testPunctuation() {
        Assert.assertEquals(count.punctuation(null), "0");
        Assert.assertEquals(count.punctuation(""), "0");
        Assert.assertEquals(count.punctuation(" T H I S "), "0");
        Assert.assertEquals(count.punctuation(" \t\n \r "), "0");
        Assert.assertEquals(count.punctuation("A matches method is defined by this class as a convenience for " +
                                              "when a regular expression is used just once. This method compiles " +
                                              "an expression and matches an input sequence against it in a " +
                                              "single invocation. "), "2");
        Assert.assertEquals(count.punctuation("Don't forget to accept some of your own answers, Mister R2, as you " +
                                              "now have an accept rate of 33% (and I can clearly see that that is " +
                                              "not deliberate in any way). –  owlstead Jul 28 '12 at 23:06\n" +
                                              "  \t  \t\nOh! Gosh I didn't even notice that. No it wasn't " +
                                              "intentional at all -- Thank you for bringing it up!"), "16");
    }

    @Test
    public void testAny() {
        Assert.assertEquals(count.any(null, "'cie!"), "0");
        Assert.assertEquals(count.any("", "'cie!"), "0");
        Assert.assertEquals(count.any(" T H I S ", "'cIe!"), "1");
        Assert.assertEquals(count.any(" \t\n \r ", "'c ie!"), "3");
        Assert.assertEquals(count.any("A matches method is defined by this class as a convenience for " +
                                      "when a regular expression is used just once. This method compiles " +
                                      "an expression and matches an input sequence against it in a " +
                                      "single invocation. ", "'Tie!"), "39");
        Assert.assertEquals(count.any("Don't forget to accept some of your own answers, Mister R2, as you " +
                                      "now have an accept rate of 33% (and I can clearly see that that is " +
                                      "not deliberate in any way). –  owlstead Jul 28 '12 at 23:06\n" +
                                      "  \t  \t\nOh! Gosh I didn't even notice that. No it wasn't " +
                                      "intentional at all -- Thank you for bringing it up!", "'cie!"), "44");
        Assert.assertEquals(count.any("This is a test.  Do not be alarmed", "e.i"), "6");
    }

    @Test
    public void testSize() {
        Assert.assertEquals(count.size(null), "0");
        Assert.assertEquals(count.size(""), "0");
        Assert.assertEquals(count.size(" "), "1");
        Assert.assertEquals(count.size(" T H I S "), "9");
        Assert.assertEquals(count.size(" \t\n \r "), "6");
        Assert.assertEquals(count.size("Add whatever characters you wish to match inside the []s. Be " +
                                       "careful to escape any characters that might have a special meaning " +
                                       "to the regex parser."), "148");
    }

    @Test
    public void testOmit() {
        Assert.assertEquals(count.omit(null, "aeiou"), "0");
        Assert.assertEquals(count.omit("", "aeiou"), "0");
        Assert.assertEquals(count.omit(" ", "aeiou"), "1");
        Assert.assertEquals(count.omit(" T H I S ", "aeiou"), "9");
        Assert.assertEquals(count.omit(" T H I S ", "aeiouAEIOU"), "8");
        Assert.assertEquals(count.omit(" \t\n \r ", "aeiou"), "6");
        Assert.assertEquals(count.omit("Add whatever characters you wish to match inside the []s. Be " +
                                       "careful to escape any characters that might have a special meaning " +
                                       "to the regex parser.", "aeiou"), "104");
    }

    @Test
    public void testSequence() {
        Assert.assertEquals(count.sequence(null, "aeiou"), "0");
        Assert.assertEquals(count.sequence("", "aeiou"), "0");
        Assert.assertEquals(count.sequence(" ", "aeiou"), "0");
        Assert.assertEquals(count.sequence(" T H I S ", "aeiou"), "0");
        Assert.assertEquals(count.sequence(" T H I S ", "aeiouAEIOU"), "0");
        Assert.assertEquals(count.sequence(" \t\n \r ", "aeiou"), "0");
        Assert.assertEquals(count.sequence("So I'm completely new to Regular Expressions, and I'm trying to use " +
                                           "Java's java.util.regex to find punctuation in input Strings. I won't " +
                                           "know what kind of punctuation I might get ahead of time, except that " +
                                           "(1) !, ?, ., ... are all valid puncutation, and (2) \"<\" and \">\" " +
                                           "mean something special, and don't count as punctuation. The program " +
                                           "itself builds phrases pseudo-randomly, and I want to strip off the " +
                                           "punctuation at the end of a sentence before it goes through the " +
                                           "random process.", "the "), "3");
    }
}