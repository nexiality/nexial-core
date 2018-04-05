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

import org.junit.Assert;
import org.junit.Test;
import org.nexial.commons.utils.RegexUtils;

public class NexialFilterTest {
    @Test
    public void regexParsing() {
        String regex = NexialFilterComparator.getRegexFilter();
        System.out.println("regex = " + regex);

        Assert.assertEquals("[a, =, \"a\"]", RegexUtils.collectGroups("a = \"a\"", regex) + "");
        Assert.assertEquals("[a, =, \"a\"]", RegexUtils.collectGroups("a = \"a\"", regex) + "");
        Assert.assertEquals("[a, =, \"a\"]", RegexUtils.collectGroups("a   = \t\"a\"", regex) + "");
        Assert.assertEquals("[yadf, =, \"a\"]", RegexUtils.collectGroups("yadf   = \t\"a\"", regex) + "");
        Assert.assertEquals("[${my data}, =, \"a\"]",
                            RegexUtils.collectGroups("${my data}   = \t\"a\"", regex) + "");

        Assert.assertEquals("[a, !=, \"a\"]", RegexUtils.collectGroups("a != \"a\"", regex) + "");
        Assert.assertEquals("[a, !=, \"a\"]", RegexUtils.collectGroups("a !=  \"a\"", regex) + "");

        Assert.assertEquals("[a, >, \"a\"]", RegexUtils.collectGroups("a > \"a\"", regex) + "");
        Assert.assertEquals("[abc, >, \"a\"]", RegexUtils.collectGroups("abc > \"a\"", regex) + "");

        Assert.assertEquals("[a, >=, \"a\"]", RegexUtils.collectGroups("a >= \"a\"", regex) + "");
        Assert.assertEquals("[a, <, \"a\"]", RegexUtils.collectGroups("a < \"a\"", regex) + "");

        Assert.assertEquals("[a, <=, \"a\"]", RegexUtils.collectGroups("a <= \"a\"", regex) + "");
        // input a < = "a" is wrong since "< =" is not "<="
        Assert.assertEquals("[a, <, = \"a\"]", RegexUtils.collectGroups("a < = \"a\"", regex) + ""); // wrong input

        Assert.assertEquals("[a, is, \"a\"]", RegexUtils.collectGroups("a is \"a\"", regex) + "");
        Assert.assertEquals("[a, is, \"a\"]", RegexUtils.collectGroups("a is \"a\"", regex) + "");
        Assert.assertEquals("[is, is, \"a\"]", RegexUtils.collectGroups("is is \"a\"", regex) + "");
        Assert.assertEquals("[is not, is, \"a\"]", RegexUtils.collectGroups("is not is \"a\"", regex) + "");
        Assert.assertEquals("[contain, is, match]", RegexUtils.collectGroups("contain is match", regex) + "");

        Assert.assertEquals("[he, is not, me]", RegexUtils.collectGroups("he is not me", regex) + "");
        Assert.assertEquals("[x, is not, [a,b,c]]", RegexUtils.collectGroups("x is not [a,b,c]", regex) + "");

        Assert.assertEquals("[<abc>, in, [a,b]]", RegexUtils.collectGroups("<abc> in [a,b]", regex) + "");

        Assert.assertEquals("[c, not in, [a,b]]", RegexUtils.collectGroups("c not in [a,b]", regex) + "");
        Assert.assertEquals("[in or, not in, [a,b]]", RegexUtils.collectGroups("in or not in [a,b]", regex) + "");

        // input: a in or not in [a,b] is wrong/confusing. parser can't figure out which of "in" or "not in" to use
        // workaround is to wrap "subject" with double quotes: "a in or" not in [a,b]
        // eg: can't be done
        Assert.assertEquals("[a, in, or not in [a,b]]", RegexUtils.collectGroups("a in or not in [a,b]", regex) + "");
        // eg: alternate
        Assert.assertEquals("[\"a in or\", not in, [a,b]]",
                            RegexUtils.collectGroups("\"a in or\" not in [a,b]", regex) + "");

        Assert.assertEquals("[8, between, [5,15]]", RegexUtils.collectGroups("8 between [5,15]", regex) + "");
        // wrong controls, but Filter class will handle it after parser
        Assert.assertEquals("[a, between, [5,15,20]]", RegexUtils.collectGroups("a between [5,15,20]", regex) + "");

        Assert.assertEquals("[does my list, contain, data?]",
                            RegexUtils.collectGroups("does my list contain data?", regex) + "");
        Assert.assertEquals("[${does my list}, contain, data?]",
                            RegexUtils.collectGroups("${does my list} contain data?", regex) + "");

        Assert.assertEquals("[I can, start with, this task]",
                            RegexUtils.collectGroups("I can start with this task", regex) + "");
        Assert.assertEquals("[abc, start with, ab]", RegexUtils.collectGroups("abc start with ab", regex) + "");

        // between|contain|start with|end with|match)
        Assert.assertEquals("[a, end with, a]", RegexUtils.collectGroups("a end with a", regex) + "");
        Assert.assertEquals("[chopper, end with, per]", RegexUtils.collectGroups("chopper end with per", regex) + "");

        Assert.assertEquals("[chopper, match, .+]", RegexUtils.collectGroups("chopper match .+", regex) + "");
        Assert.assertEquals("[match maker, match, .*match.+r]",
                            RegexUtils.collectGroups("match maker match .*match.+r", regex) + "");
    }

    @Test
    public void normalizeCondition() {
        Assert.assertEquals(null, NexialFilter.normalizeCondition(null));
        Assert.assertEquals("", NexialFilter.normalizeCondition(""));
        Assert.assertEquals("", NexialFilter.normalizeCondition(" "));
        Assert.assertEquals("hello", NexialFilter.normalizeCondition("hello"));
        Assert.assertEquals("hel\"lo", NexialFilter.normalizeCondition("hel\"lo"));
        Assert.assertEquals("'hel\"lo'", NexialFilter.normalizeCondition("'hel\"lo'"));
        Assert.assertEquals("'hel\"lo", NexialFilter.normalizeCondition("\"'hel\"lo\""));
        Assert.assertEquals(" hel\"lo .. [ yada ]  ", NexialFilter.normalizeCondition("\" hel\"lo .. [ yada ]  \""));
    }

    @Test
    public void isMatch() {
        // Equal("="),
        Assert.assertTrue(NexialFilter.newInstance("x = \"a\"").isMatch("a"));
        Assert.assertTrue(NexialFilter.newInstance("x = \"a\"").isMatch(" a "));
        Assert.assertTrue(NexialFilter.newInstance("x = \"a\"").isMatch(" a\t  "));

        // NotEqual("!="),
        Assert.assertTrue(NexialFilter.newInstance("x != \"a\"").isMatch("A"));
        Assert.assertTrue(NexialFilter.newInstance("x != \"a\"").isMatch("A "));
        Assert.assertTrue(NexialFilter.newInstance("x != a").isMatch(" "));
        Assert.assertTrue(NexialFilter.newInstance("x != 'a'").isMatch(" "));

        // todo: deal with dates for greater/lesser comparison

        // Greater(">"),
        Assert.assertTrue(NexialFilter.newInstance("x > 19").isMatch("20 "));
        Assert.assertTrue(NexialFilter.newInstance("x > 19.9999").isMatch("20 "));
        // octal power!
        Assert.assertTrue(NexialFilter.newInstance("x > 013").isMatch("20 "));
        Assert.assertTrue(NexialFilter.newInstance("x > \"+15.09123\"").isMatch("20 "));
        Assert.assertTrue(NexialFilter.newInstance("x > \"-15.09123\"").isMatch(" -14.2093 "));

        // GreaterOrEqual(">="),
        Assert.assertTrue(NexialFilter.newInstance("x >= 19").isMatch("19 "));
        Assert.assertTrue(NexialFilter.newInstance("x >= 19").isMatch("19.0000 "));
        Assert.assertTrue(NexialFilter.newInstance("x >= 19").isMatch("+19.0000 "));
        Assert.assertTrue(NexialFilter.newInstance("x >= 19").isMatch(" \"+19.0000\" "));
        Assert.assertTrue(NexialFilter.newInstance("x >= 19").isMatch(" \"+  19.0000\" "));
        Assert.assertTrue(NexialFilter.newInstance("x >= $18.0293").isMatch(" \"+  19.0000\" "));

        // Lesser("<"),
        Assert.assertTrue(NexialFilter.newInstance("x < 15.01").isMatch(" \"-15.01\" "));
        Assert.assertTrue(NexialFilter.newInstance("x < -15.01").isMatch(" \"-15.02\" "));
        Assert.assertTrue(NexialFilter.newInstance("x < \"$ -15.01 \"").isMatch(" \"-15.02\" "));

        // LesserOrEqual("<="),
        Assert.assertTrue(NexialFilter.newInstance("x <= \"$ -15.01 \"").isMatch(" \"-15.01\" "));
        Assert.assertTrue(NexialFilter.newInstance("x <= \"$ -15.01 \"").isMatch(" -15.01 "));
        Assert.assertTrue(NexialFilter.newInstance("x <= \"$ -15.01 \"").isMatch(" $  -15.01 "));
        Assert.assertTrue(NexialFilter.newInstance("x <= \"$ -15.01 \"").isMatch(" $    -15.  01 "));

        // Is("is"),
        Assert.assertTrue(NexialFilter.newInstance("x is [x|y|z]").isMatch(" y "));
        Assert.assertTrue(NexialFilter.newInstance("x is [x|y|z]").isMatch(" z "));
        Assert.assertFalse(NexialFilter.newInstance("x is [x|y|z]").isMatch(" | "));
        Assert.assertTrue(NexialFilter.newInstance("x is [  x  |  y |  z  ]   ").isMatch(" x "));
        Assert.assertTrue(NexialFilter.newInstance("x is [  \"x\"  |  \"y\" |  z  ]   ").isMatch(" z"));
        Assert.assertTrue(NexialFilter.newInstance("x is [  \"x\"  |  \"y\" |  z  ]   ").isMatch(" z"));
        // nothing matches EVERY situation
        Assert.assertTrue(NexialFilter.newInstance("x in [  ]   ").isMatch(""));

        // IsNot("is not"),
        Assert.assertTrue(NexialFilter.newInstance("x is not [  \"x\"  |  \"y\" |  z  ]   ").isMatch(" k "));
        Assert.assertTrue(NexialFilter.newInstance("x is not [  \"x\" |  \"y\" |  z  ]   ").isMatch("\"x\" |  \"y\""));

        // In("in"),
        Assert.assertTrue(NexialFilter.newInstance("x in [  \"x\"  |  \"y\" |  z  ]   ").isMatch(" z"));
        Assert.assertTrue(NexialFilter.newInstance("x in [  \" \" ]   ").isMatch(" "));
        Assert.assertTrue(NexialFilter.newInstance("x in [  \" \"| \"  \"| \"   \" ]   ").isMatch(" "));

        // NotIn("not in"),
        Assert.assertTrue(NexialFilter.newInstance("x not in [  \"x\"  |  \"y\" |z  ]   ").isMatch("\"x\"  |  \"y\""));
        // nothing matches nothing, unless empty string is in the control group
        Assert.assertTrue(NexialFilter.newInstance("x not in [  \"x\"  |  \"y\" |z  ]   ").isMatch(""));
        Assert.assertTrue(NexialFilter.newInstance("x not in [  \"x\"  |  \"y\" |z  ]   ").isMatch("\"\""));
        Assert.assertTrue(NexialFilter.newInstance("x not in [  \"x\"  |  \"y\" |z  ]   ").isMatch(" "));
        Assert.assertTrue(NexialFilter.newInstance("x not in [  \"x\"  |  \"y\" |z  ]   ").isMatch("\" \""));
        Assert.assertFalse(NexialFilter.newInstance("x not in [  \"x\"  |  \"\" |z  ]   ").isMatch("\"\""));
        Assert.assertFalse(NexialFilter.newInstance("x not in [  \"x\"  |  \"\" |z  ]   ").isMatch(""));
        Assert.assertTrue(NexialFilter.newInstance("x not in [  ]   ").isMatch("x"));
        // nothing matches nothing
        Assert.assertTrue(NexialFilter.newInstance("x not in [  ]   ").isMatch(""));
        Assert.assertTrue(NexialFilter.newInstance("x not in [  ]   ").isMatch("\"\""));
        // anything DOES NOT matches nothing - hence always true4
        Assert.assertTrue(NexialFilter.newInstance("x not in [  ]   ").isMatch(" a "));
        Assert.assertTrue(NexialFilter.newInstance("x not in [  ]   ").isMatch(" "));
        Assert.assertTrue(NexialFilter.newInstance("x not in [  ]   ").isMatch("\" \""));

        // Between("between"),
        Assert.assertTrue(NexialFilter.newInstance("x between [ 5| 10  ]   ").isMatch("\"8\""));
        Assert.assertTrue(NexialFilter.newInstance("x between [ 5| 10  ]   ").isMatch("\"5.0001\""));
        Assert.assertTrue(NexialFilter.newInstance("x between [ 5| 6 ]   ").isMatch("$+5.0001"));
        Assert.assertTrue(NexialFilter.newInstance("x between [ 6| 5 ]   ").isMatch("$+5.0001"));
        Assert.assertTrue(NexialFilter.newInstance("x between [ +7| $4.80042 ]   ").isMatch("$+5.0001"));
        Assert.assertTrue(NexialFilter.newInstance("x between    [ 5.001| 5 ]   ").isMatch("$+5.0001"));

        try {
            NexialFilter.newInstance("x between [ 5.0000001| 5| 5.01 ]   ");
            Assert.fail("expects exception since controls for 'between' must be exactly 2");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Contains("contain")
        Assert.assertTrue(NexialFilter.newInstance("x contain  the time").isMatch("now is the time"));
        Assert.assertTrue(NexialFilter.newInstance("x contain the time").isMatch("now is the time"));
        Assert.assertTrue(NexialFilter.newInstance("x contain the time     ").isMatch("now is the time"));
        Assert.assertTrue(NexialFilter.newInstance("a contain  calif").isMatch("supercalifragilistic"));
        Assert.assertTrue(NexialFilter.newInstance("a contain [calif|lifrag]").isMatch("supercalifragilistic"));
        Assert.assertTrue(NexialFilter.newInstance("a contain [california|supercal]").isMatch("supercalifragilistic"));

        // StartsWith("start with"),
        Assert.assertTrue(NexialFilter.newInstance("a start with matt").isMatch("matthew"));
        Assert.assertTrue(NexialFilter.newInstance("a start with  ma").isMatch("matthew"));
        Assert.assertTrue(NexialFilter.newInstance("a start with  \"ma\"").isMatch("matthew"));
        Assert.assertTrue(NexialFilter.newInstance("a start with   [ \"ma\"| tth  ]  ").isMatch("matthew"));

        // EndsWith("end with"),
        Assert.assertTrue(NexialFilter.newInstance("a end with   [ \"ma\"| hew  ]  ").isMatch("matthew"));
        Assert.assertTrue(NexialFilter.newInstance("a end with   [ \"ma\"| \n hew  ]  ").isMatch("matthew"));
        Assert.assertTrue(NexialFilter.newInstance("a end with   [ \"ma\"| \r\n \t hew| att\n\n  ]  ")
                                      .isMatch("matthew"));

        // Match("match"),
        Assert.assertTrue(NexialFilter.newInstance("a match mat+.* ").isMatch("matthew"));
        Assert.assertTrue(NexialFilter.newInstance("a match   ca.*o+.*rao.* ").isMatch("carpool karaoke"));
    }

}