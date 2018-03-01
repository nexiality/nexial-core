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

package org.nexial.commons.utils;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class RegexUtilsTest {

    @Test
    public void testReplace() {
        String fixture = "Invalid input for Federal EIN '0000051801'. [2005]";
        String actual = RegexUtils.replace(fixture, "(.+)\\[([\\d]{1,4})\\]", "$1|$2");
        Assert.assertEquals("Invalid input for Federal EIN '0000051801'. |2005", actual);

        String fixture1 = "Invalid input for Federal EIN '0000051801'.";
        String actual1 = RegexUtils.replace(fixture1, "(.+)\\[([\\d]{1,4})\\]", "$1|$2");
        Assert.assertEquals("Invalid input for Federal EIN '0000051801'.", actual1);

        String fixture2 = "Invalid input for Federal EIN '0000051801'. [2005";
        String actual2 = RegexUtils.replace(fixture2, "(.+)\\[([\\d]{1,4})\\]", "$1|$2");
        Assert.assertEquals("Invalid input for Federal EIN '0000051801'. [2005", actual2);
    }

    @Test
    public void testReplaceVarName() {
        Map<String, String> oldAndNew = TextUtils.toMap("John=walker,Sam=ash", ",", "=");

        String fixture = "John=Johnson\n" +
                         "Sam=Sammy\n" +
                         "John==DoubleEquals\n" +
                         "John NoEqual\n" +
                         "Sam:Samson";

        String actual = fixture;
        for (String oldVal : oldAndNew.keySet()) {
            String newVal = oldAndNew.get(oldVal);
            actual = RegexUtils.replace(actual, "^(" + oldVal + ")([:=].*)", newVal + "$2");
        }

        Assert.assertEquals("walker=Johnson\n" +
                            "ash=Sammy\n" +
                            "walker==DoubleEquals\n" +
                            "John NoEqual\n" +
                            "ash:Samson", actual);
    }

    @Test
    public void testReplaceVarNameWithCR() {
        Map<String, String> oldAndNew = TextUtils.toMap("John=walker,Sam=ash", ",", "=");

        String fixture = "John=Johnson\r\n" +
                         "Sam=Sammy\r\n" +
                         "John==DoubleEquals";

        String actual = fixture;
        for (String oldVal : oldAndNew.keySet()) {
            String newVal = oldAndNew.get(oldVal);
            actual = RegexUtils.replace(actual, "^(" + oldVal + ")(=.*)", newVal + "$2");
        }

        Assert.assertEquals("walker=Johnson\r\n" +
                            "ash=Sammy\r\n" +
                            "walker==DoubleEquals", actual);
    }

    @Test
    public void testSplits() {
        String key = "@include(dataDriver.xlsx, #data, LANDSCAPE)";
        List<String> splits = RegexUtils.collectGroups(key,
                                                       "\\@include\\(\\s*(.+)\\s*\\,\\s*(.+)\\s*\\,\\s*(LANDSCAPE|PORTRAIT)\\)");
        Assert.assertEquals(3, splits.size());
        Assert.assertEquals("dataDriver.xlsx", splits.get(0));
        Assert.assertEquals("#data", splits.get(1));
        Assert.assertEquals("LANDSCAPE", splits.get(2));

    }
}
