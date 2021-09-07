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
package org.nexial.commons.utils

import org.junit.Assert.*
import org.junit.Test

class RegexUtilsTest {
    @Test
    fun testReplace() {
        val fixture = "Invalid input for Federal EIN '0000051801'. [2005]"
        val actual = RegexUtils.replace(fixture, "(.+)\\[([\\d]{1,4})\\]", "$1|$2")
        assertEquals("Invalid input for Federal EIN '0000051801'. |2005", actual)

        val fixture1 = "Invalid input for Federal EIN '0000051801'."
        val actual1 = RegexUtils.replace(fixture1, "(.+)\\[([\\d]{1,4})\\]", "$1|$2")
        assertEquals("Invalid input for Federal EIN '0000051801'.", actual1)

        val fixture2 = "Invalid input for Federal EIN '0000051801'. [2005"
        val actual2 = RegexUtils.replace(fixture2, "(.+)\\[([\\d]{1,4})\\]", "$1|$2")
        assertEquals("Invalid input for Federal EIN '0000051801'. [2005", actual2)
    }

    @Test
    fun testReplace_partial() {
        val regex = "(.*)\\%([0-9]{2})(.*)"
        val replacement = "$1!!-$2-!!$3"

        var fixture = "/A%20B/C%25C/D"
        while (RegexUtils.match(fixture, regex)) fixture = RegexUtils.replace(fixture, regex, replacement)
        assertEquals("/A!!-20-!!B/C!!-25-!!C/D", fixture)

        fixture = "/%%20%B/20%2544/%"
        while (RegexUtils.match(fixture, regex)) fixture = RegexUtils.replace(fixture, regex, replacement)
        assertEquals("/%!!-20-!!%B/20!!-25-!!44/%", fixture)
    }

    @Test
    fun testReplaceVarName() {
        val oldAndNew = TextUtils.toMap("John=walker,Sam=ash", ",", "=")
        val fixture = """
            John=Johnson
            Sam=Sammy
            John==DoubleEquals
            John NoEqual
            Sam:Samson
            """.trimIndent()
        var actual: String? = fixture
        for (oldVal in oldAndNew.keys)
            actual = RegexUtils.replace(actual, "^($oldVal)([:=].*)", "${oldAndNew[oldVal]}$2")
        assertEquals("""
    walker=Johnson
    ash=Sammy
    walker==DoubleEquals
    John NoEqual
    ash:Samson
    """.trimIndent(), actual)
    }

    @Test
    fun testReplaceVarNameWithCR() {
        val oldAndNew = TextUtils.toMap("John=walker,Sam=ash", ",", "=")
        val fixture = """
            John=Johnson
            Sam=Sammy
            John==DoubleEquals
            """.trimIndent()
        var actual: String? = fixture
        for (oldVal in oldAndNew.keys) actual = RegexUtils.replace(actual, "^($oldVal)(=.*)", "${oldAndNew[oldVal]}$2")
        assertEquals("""
    walker=Johnson
    ash=Sammy
    walker==DoubleEquals
    """.trimIndent(), actual)
    }

    @Test
    fun testSplits() {
        val key = "@include(dataDriver.xlsx, #data, LANDSCAPE)"
        val splits = RegexUtils.collectGroups(
            key,
            "\\@include\\(\\s*(.+)\\s*\\,\\s*(.+)\\s*\\,\\s*(LANDSCAPE|PORTRAIT)\\)")
        assertEquals(3, splits.size.toLong())
        assertEquals("dataDriver.xlsx", splits[0])
        assertEquals("#data", splits[1])
        assertEquals("LANDSCAPE", splits[2])
    }

    @Test
    fun testReplaceRetainNewLines() {
        assertEquals("this is a test. \n\n\nDo not be alarmed.",
                     RegexUtils.replace("this is a test. \n\n\nDo not be alarmed.", "[0-9]", " "))
        val sqls = """
            -- sentry:insert_new_support_rep
            INSERT INTO EMPLOYEES (LASTNAME, FIRSTNAME, TITLE, REPORTSTO, BIRTHDATE, HIREDATE, ADDRESS, CITY, STATE, COUNTRY, POSTALCODE, PHONE, FAX, EMAIL)
            VALUES ('Brown', 'James', 'Funk Master', 'Nobody', '1963-09-30', NULL, '101 Beat Street', 'Funky Town', 'MI', 'USA', '20931', NULL, NULL, 'funky@tac.com');
            
            -- sentry:support_rep
            SELECT EMPLOYEEID AS "EmployeeId"
            FROM EMPLOYEES WHERE LASTNAME = 'Brown' AND FIRSTNAME = 'James';
            
            -- sentry:insert_new_customer
            INSERT INTO CUSTOMERS (FIRSTNAME, LASTNAME, COMPANY, ADDRESS, CITY, STATE, COUNTRY, POSTALCODE, PHONE, FAX, EMAIL, SUPPORTREPID)
            VALUES ('John', 'Smith', 'Acme', '123 Elm Street', 'Fullerton', 'PA', 'USA', '10491', '702-541-2213', NULL, 'john.smith@acme.com', ${"$"}{support_rep}.data[0].EmployeeId);
            
            
            
            """.trimIndent()
        assertEquals("""
    -- nexial:insert_new_support_rep
    INSERT INTO EMPLOYEES (LASTNAME, FIRSTNAME, TITLE, REPORTSTO, BIRTHDATE, HIREDATE, ADDRESS, CITY, STATE, COUNTRY, POSTALCODE, PHONE, FAX, EMAIL)
    VALUES ('Brown', 'James', 'Funk Master', 'Nobody', '1963-09-30', NULL, '101 Beat Street', 'Funky Town', 'MI', 'USA', '20931', NULL, NULL, 'funky@tac.com');
    
    -- nexial:support_rep
    SELECT EMPLOYEEID AS "EmployeeId"
    FROM EMPLOYEES WHERE LASTNAME = 'Brown' AND FIRSTNAME = 'James';
    
    -- nexial:insert_new_customer
    INSERT INTO CUSTOMERS (FIRSTNAME, LASTNAME, COMPANY, ADDRESS, CITY, STATE, COUNTRY, POSTALCODE, PHONE, FAX, EMAIL, SUPPORTREPID)
    VALUES ('John', 'Smith', 'Acme', '123 Elm Street', 'Fullerton', 'PA', 'USA', '10491', '702-541-2213', NULL, 'john.smith@acme.com', ${"$"}{support_rep}.data[0].EmployeeId);
    
    
    
    """.trimIndent(), RegexUtils.replace(sqls, "-- sentry:", "-- nexial:"))
    }

    @Test
    fun removeMatches() {
        assertNull(RegexUtils.removeMatches(null, "[A-Za-z]"))
        assertEquals("", RegexUtils.removeMatches("", "[A-Za-z]"))
        assertEquals(" ", RegexUtils.removeMatches(" ", "[A-Za-z]"))
        assertEquals("12345", RegexUtils.removeMatches("a1s2g3b4w5pps", "[A-Za-z]"))
        assertEquals("9449", RegexUtils.removeMatches("a;sdgihap9w4havliegrp49u", "[A-Za-z;]"))
        assertEquals("82 23980 \n020", RegexUtils.removeMatches("a;asd8goj2;g 23980sl adlf\n02e0ijdkf", "[A-Za-z;]"))
        assertEquals(";;  \n", RegexUtils.removeMatches("a;asd8goj2;g 23980sl adlf\n02e0ijdkf", "[\\d\\w]"))
        assertEquals("GSLEWPvnse9otq09wbanw4k5ql4k4qjfh",
                     RegexUtils.removeMatches("@#)GSLEWP)vnse9otq09wbanw4k5  ql4k4;qjfh", "[\\p{Punct}\\p{Space}]"))
    }

    @Test
    fun removeMatches_noMatches() {
        assertEquals("//a/b/c", RegexUtils.removeMatches("By.xpath: //a/b/c", "^By\\..+\\:\\s*"))
        assertEquals("//a/b/c", RegexUtils.removeMatches("//a/b/c", "^By\\..+\\:\\s*"))
        assertEquals("//a/b/c", RegexUtils.removeMatches("By.cssSelector://a/b/c", "^By\\..+\\:\\s*"))
        assertEquals("By.link=//a/b/c", RegexUtils.removeMatches("By.link=//a/b/c", "^By\\..+\\:\\s*"))
    }

    @Test
    fun removeMatchedLines() {
        val fixture =
            """3036 Propofol 20 mLs Ampule     37.67 -1     11.462     -11.46     11.240     11.240 F< 11 02-02-09
ANESTHESIA INJECTABLE     848.37
ASDAS
123 423423 (none)       0.00 0      0.000       0.00      0.000      0.000 F 11
ASDAS       0.00
BANDAGING/CASTING
1000 Adaptic Dressing 3" x 8" Each       2.58 4      1.311       5.24      1.298      1.298 F< 11 03-17-05
1134 AluSpray Aerosol Bandage Bottle      19.50 1      9.750       9.75      9.750      9.750 F< 11
"""
        assertEquals(
            """3036 Propofol 20 mLs Ampule     37.67 -1     11.462     -11.46     11.240     11.240 F< 11 02-02-09
123 423423 (none)       0.00 0      0.000       0.00      0.000      0.000 F 11
1000 Adaptic Dressing 3" x 8" Each       2.58 4      1.311       5.24      1.298      1.298 F< 11 03-17-05
1134 AluSpray Aerosol Bandage Bottle      19.50 1      9.750       9.75      9.750      9.750 F< 11
""",
            RegexUtils.removeMatches(fixture, "^[A-Z ,/\\\\]+\\s*([0-9\\.]+\\s*)?\n", true, true))
    }

    @Test
    fun retainMatches() {
        assertNull(RegexUtils.retainMatches(null, "[A-Za-z]"))
        assertEquals("", RegexUtils.retainMatches("", "[A-Za-z]"))
        assertEquals("", RegexUtils.retainMatches(" ", "[A-Za-z]"))
        assertEquals("12345", RegexUtils.retainMatches("a1s2g3b4w5pps", "[0-9]"))
        assertEquals("12345432100991",
                     RegexUtils.retainMatches("  askdjgh1asdjlkjslksj2345gggn ;;s;4321s.df--00991", "[0-9]"))
    }

    @Test
    fun firstMatch() {
        val match = RegexUtils.firstMatches("Can{TAB}ada{ESCAPE}", "(\\{.+?\\})")
        println("groups = $match")
        assertNotNull(match)
        // Assert.assertEquals(groups.size(), 4);
    }

    @Test
    fun collectGroup() {
        val fixture = "{right-of=Yes}{clickable,class=android.widget.GroupView}"
        val regex = "\\{(.*?)\\}\\{(.*?)\\}"
        val groups = RegexUtils.collectGroups(fixture, regex, false, true)
        println("groups = $groups")
        assertNotNull(groups)
        assertEquals(2, groups.size)
        assertEquals("right-of=Yes", groups[0])
        assertEquals("clickable,class=android.widget.GroupView", groups[1])
    }

    @Test
    fun test_isMatch() {
        val regex = "^(left-of|right-of|above|below):.+$"
        assertTrue(RegexUtils.match("left-of:Cellphone", regex))
        assertTrue(RegexUtils.match("below:Your Password", regex))
        assertTrue(RegexUtils.match("right-of:Male", regex))
    }
}