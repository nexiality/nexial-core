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
    public void testReplace_partial() {
        String regex = "(.*)\\%([0-9]{2})(.*)";
        String replacement = "$1!!-$2-!!$3";

        String fixture = "/A%20B/C%25C/D";
        while (RegexUtils.match(fixture, regex)) { fixture = RegexUtils.replace(fixture, regex, replacement); }
        Assert.assertEquals("/A!!-20-!!B/C!!-25-!!C/D", fixture);

        fixture = "/%%20%B/20%2544/%";
        while (RegexUtils.match(fixture, regex)) { fixture = RegexUtils.replace(fixture, regex, replacement); }
        Assert.assertEquals("/%!!-20-!!%B/20!!-25-!!44/%", fixture);
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
        List<String> splits = RegexUtils.collectGroups(
            key,
            "\\@include\\(\\s*(.+)\\s*\\,\\s*(.+)\\s*\\,\\s*(LANDSCAPE|PORTRAIT)\\)");
        Assert.assertEquals(3, splits.size());
        Assert.assertEquals("dataDriver.xlsx", splits.get(0));
        Assert.assertEquals("#data", splits.get(1));
        Assert.assertEquals("LANDSCAPE", splits.get(2));
    }

    @Test
    public void testReplaceRetainNewLines() {
        Assert.assertEquals("this is a test. \n\n\nDo not be alarmed.",
                            RegexUtils.replace("this is a test. \n\n\nDo not be alarmed.", "[0-9]", " "));

        String sqls = "-- sentry:insert_new_support_rep\n" +
                      "INSERT INTO EMPLOYEES (LASTNAME, FIRSTNAME, TITLE, REPORTSTO, BIRTHDATE, HIREDATE, ADDRESS, CITY, STATE, COUNTRY, POSTALCODE, PHONE, FAX, EMAIL)\n" +
                      "VALUES ('Brown', 'James', 'Funk Master', 'Nobody', '1963-09-30', NULL, '101 Beat Street', 'Funky Town', 'MI', 'USA', '20931', NULL, NULL, 'funky@tac.com');\n" +
                      "\n" +
                      "-- sentry:support_rep\n" +
                      "SELECT EMPLOYEEID AS \"EmployeeId\"\n" +
                      "FROM EMPLOYEES WHERE LASTNAME = 'Brown' AND FIRSTNAME = 'James';\n" +
                      "\n" +
                      "-- sentry:insert_new_customer\n" +
                      "INSERT INTO CUSTOMERS (FIRSTNAME, LASTNAME, COMPANY, ADDRESS, CITY, STATE, COUNTRY, POSTALCODE, PHONE, FAX, EMAIL, SUPPORTREPID)\n" +
                      "VALUES ('John', 'Smith', 'Acme', '123 Elm Street', 'Fullerton', 'PA', 'USA', '10491', '702-541-2213', NULL, 'john.smith@acme.com', ${support_rep}.data[0].EmployeeId);\n" +
                      "\n" +
                      "\n";
        Assert.assertEquals("-- nexial:insert_new_support_rep\n" +
                            "INSERT INTO EMPLOYEES (LASTNAME, FIRSTNAME, TITLE, REPORTSTO, BIRTHDATE, HIREDATE, ADDRESS, CITY, STATE, COUNTRY, POSTALCODE, PHONE, FAX, EMAIL)\n" +
                            "VALUES ('Brown', 'James', 'Funk Master', 'Nobody', '1963-09-30', NULL, '101 Beat Street', 'Funky Town', 'MI', 'USA', '20931', NULL, NULL, 'funky@tac.com');\n" +
                            "\n" +
                            "-- nexial:support_rep\n" +
                            "SELECT EMPLOYEEID AS \"EmployeeId\"\n" +
                            "FROM EMPLOYEES WHERE LASTNAME = 'Brown' AND FIRSTNAME = 'James';\n" +
                            "\n" +
                            "-- nexial:insert_new_customer\n" +
                            "INSERT INTO CUSTOMERS (FIRSTNAME, LASTNAME, COMPANY, ADDRESS, CITY, STATE, COUNTRY, POSTALCODE, PHONE, FAX, EMAIL, SUPPORTREPID)\n" +
                            "VALUES ('John', 'Smith', 'Acme', '123 Elm Street', 'Fullerton', 'PA', 'USA', '10491', '702-541-2213', NULL, 'john.smith@acme.com', ${support_rep}.data[0].EmployeeId);\n" +
                            "\n" +
                            "\n",
                            RegexUtils.replace(sqls, "-- sentry:", "-- nexial:"));

    }

    @Test
    public void removeMatches() {
        Assert.assertNull(RegexUtils.removeMatches(null, "[A-Za-z]"));
        Assert.assertEquals("", RegexUtils.removeMatches("", "[A-Za-z]"));
        Assert.assertEquals(" ", RegexUtils.removeMatches(" ", "[A-Za-z]"));
        Assert.assertEquals("12345", RegexUtils.removeMatches("a1s2g3b4w5pps", "[A-Za-z]"));
        Assert.assertEquals("9449", RegexUtils.removeMatches("a;sdgihap9w4havliegrp49u", "[A-Za-z;]"));
        Assert.assertEquals("82 23980 \n020",
                            RegexUtils.removeMatches("a;asd8goj2;g 23980sl adlf\n02e0ijdkf", "[A-Za-z;]"));
        Assert.assertEquals(";;  \n", RegexUtils.removeMatches("a;asd8goj2;g 23980sl adlf\n02e0ijdkf", "[\\d\\w]"));

        Assert.assertEquals("GSLEWPvnse9otq09wbanw4k5ql4k4qjfh",
                            RegexUtils.removeMatches("@#)GSLEWP)vnse9otq09wbanw4k5  ql4k4;qjfh",
                                                     "[\\p{Punct}\\p{Space}]"));
    }

    @Test
    public void retainMatches() {
        Assert.assertNull(RegexUtils.retainMatches(null, "[A-Za-z]"));
        Assert.assertEquals("", RegexUtils.retainMatches("", "[A-Za-z]"));
        Assert.assertEquals("", RegexUtils.retainMatches(" ", "[A-Za-z]"));
        Assert.assertEquals("12345", RegexUtils.retainMatches("a1s2g3b4w5pps", "[0-9]"));
        Assert.assertEquals("12345432100991",
                            RegexUtils.retainMatches("  askdjgh1asdjlkjslksj2345gggn ;;s;4321s.df--00991", "[0-9]"));
    }

    @Test
    public void firstMatch() {
        String match = RegexUtils.firstMatches("Can{TAB}ada{ESCAPE}", "(\\{.+?\\})");
        System.out.println("groups = " + match);
        Assert.assertNotNull(match);
        // Assert.assertEquals(groups.size(), 4);
    }
}
