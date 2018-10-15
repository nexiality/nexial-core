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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
import org.junit.Test;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

public class TextUtilsTest {

    @Test
    public void testSubstringBetweenClosestPair() {
        Assert.assertEquals("a", TextUtils.substringBetweenFirstPair("((a))", "(", ")"));
        Assert.assertEquals("jolly good", TextUtils.substringBetweenFirstPair("((jolly good))", "(", ")"));

        Assert.assertEquals("bracket", TextUtils.substringBetweenFirstPair("[bracket]", "[", "]"));
        Assert.assertEquals("bracket", TextUtils.substringBetweenFirstPair("[bracket]]]", "[", "]"));
        Assert.assertEquals("bracket", TextUtils.substringBetweenFirstPair("[[[[bracket]]", "[", "]"));
        Assert.assertEquals("", TextUtils.substringBetweenFirstPair("[][[[bracket]]", "[", "]"));
        Assert.assertEquals(null, TextUtils.substringBetweenFirstPair("bracket]bracket[[[bracket]]", "[", "]"));
    }

    @Test
    public void testToString() {
        Map<String, String> fixture = new HashMap<>();
        fixture.put("one", "yee");
        fixture.put("two", "er");
        fixture.put("three", "san");
        Assert.assertEquals("one=yee|two=er|three=san", TextUtils.toString(fixture, "|", "="));

        fixture = new HashMap<>();
        fixture.put("a", "");
        fixture.put("b", "");
        fixture.put("c", "=");
        Assert.assertEquals("a=|b=|c==", TextUtils.toString(fixture, "|", "="));
        Assert.assertEquals("a= b= c==", TextUtils.toString(fixture, " ", "="));
    }

    @Test
    public void testToOneLine() {
        Assert.assertEquals("", TextUtils.toOneLine("", true));
        Assert.assertEquals("", TextUtils.toOneLine(" ", true));
        Assert.assertEquals("", TextUtils.toOneLine("\t", true));
        Assert.assertEquals("\n", TextUtils.toOneLine("\n", false));
        Assert.assertEquals("   ", TextUtils.toOneLine(" \n ", false));
        Assert.assertEquals("That's enough already Jimmy",
                            TextUtils.toOneLine("That's \nenough\r already\n\rJimmy", true));
        Assert.assertEquals("Check out my really awsome hover text. I can include HTML tags and everything!!!",
                            TextUtils.toOneLine("Check out my really awsome\n" +
                                                " hover text. \n" +
                                                " I can include HTML tags and everything!!!", true));
    }

    @Test
    public void testToList() {
        String fixture = "a,,b,,c,d";

        List<String> actual = TextUtils.toList(fixture, ":", true);
        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(fixture, actual.get(0));

        actual = TextUtils.toList(fixture, ",", true);
        Assert.assertEquals(actual.size(), 4);
        Assert.assertEquals(actual.get(0), "a");
        Assert.assertEquals(actual.get(1), "b");
        Assert.assertEquals(actual.get(2), "c");
        Assert.assertEquals(actual.get(3), "d");
    }

    @Test
    public void toListPreserveTokens() {
        String fixture = "a,,b,,c,d";

        List<String> actual = TextUtils.toListPreserveTokens(fixture, ",", false);
        Assert.assertEquals(actual.size(), 6);
        Assert.assertEquals(actual.get(0), "a");
        Assert.assertEquals(actual.get(1), "");
        Assert.assertEquals(actual.get(2), "b");
        Assert.assertEquals(actual.get(3), "");
        Assert.assertEquals(actual.get(4), "c");
        Assert.assertEquals(actual.get(5), "d");
    }

    @Test
    public void toListPreserveTokens_long_delim() {
        String fixture = "Every~=~Time~=~You~=~Leave~=~Me";

        List<String> actual = TextUtils.toListPreserveTokens(fixture, "~=~", false);
        Assert.assertEquals(5, actual.size());
        Assert.assertEquals("Every", actual.get(0));
        Assert.assertEquals("Time", actual.get(1));
        Assert.assertEquals("You", actual.get(2));
        Assert.assertEquals("Leave", actual.get(3));
        Assert.assertEquals("Me", actual.get(4));
    }

    @Test
    public void testToListWith2Spaces() {
        String fixture = "Active     SSN            Name             Client  Type    FSO ID          FSO Name";
        List<String> actual = TextUtils.toList(fixture, "  ", true);
        Assert.assertEquals(actual.size(), 7);
        Assert.assertEquals(actual.get(0), "Active");
        Assert.assertEquals(actual.get(1), "SSN");
        Assert.assertEquals(actual.get(2), "Name");
        Assert.assertEquals(actual.get(3), "Client");
        Assert.assertEquals(actual.get(4), "Type");
        Assert.assertEquals(actual.get(5), "FSO ID");
        Assert.assertEquals(actual.get(6), "FSO Name");
    }

    @Test
    public void testToListWithMultipleTabs() {
        String fixture = "Active\tSSN\t\tName\t\t\tClient\tType\t\t\t\t\tFSO ID\t\tFSO Name";
        List<String> actual = TextUtils.toList(fixture, "\t", true);
        Assert.assertEquals(actual.size(), 7);
        Assert.assertEquals(actual.get(0), "Active");
        Assert.assertEquals(actual.get(1), "SSN");
        Assert.assertEquals(actual.get(2), "Name");
        Assert.assertEquals(actual.get(3), "Client");
        Assert.assertEquals(actual.get(4), "Type");
        Assert.assertEquals(actual.get(5), "FSO ID");
        Assert.assertEquals(actual.get(6), "FSO Name");
    }

    @Test
    public void testToListPreserveTokens() {
        Assert.assertEquals(null, TextUtils.toListPreserveTokens("", ",", true));
        Assert.assertEquals("[a]", TextUtils.toListPreserveTokens("a", ",", true).toString());
        Assert.assertEquals("[a, b, c, d, e]", TextUtils.toListPreserveTokens("a,b,c,d,e", ",", true).toString());
        Assert.assertEquals("[a, b, , d, e]", TextUtils.toListPreserveTokens("a,b,,d,e", ",", true).toString());
        Assert.assertEquals("[, b, , d, e]", TextUtils.toListPreserveTokens(",b,,d,e", ",", true).toString());
        Assert.assertEquals("[, , , , e]", TextUtils.toListPreserveTokens(",,,,e", ",", true).toString());
        Assert.assertEquals("[, , , , e]", TextUtils.toListPreserveTokens(",    ,,  ,e", ",", true).toString());
        Assert.assertEquals("[, , , , e]", TextUtils.toListPreserveTokens("        ,   ,,  ,e", ",", true).toString());
        Assert.assertEquals("[, b, , , e]", TextUtils.toListPreserveTokens("      , b  ,,  ,e", ",", true).toString());
    }

    /**
     * proving that {@link StringUtils#split(String)} can handle multiple delimiters independently.
     */
    @Test
    public void testStringUtilsSplit() {
        String fixture = "C:\\dir1\\dir2\\dir3\\dir4";
        String[] actual = StringUtils.split(fixture, "\\/");
        Assert.assertNotNull(actual);
        Assert.assertEquals(actual.length, 5);
        Assert.assertEquals(actual[0], "C:");
        Assert.assertEquals(actual[1], "dir1");
        Assert.assertEquals(actual[2], "dir2");
        Assert.assertEquals(actual[3], "dir3");
        Assert.assertEquals(actual[4], "dir4");

        fixture = "/dir1/dir2/dir3/dir4";
        actual = StringUtils.split(fixture, "\\/");
        Assert.assertNotNull(actual);
        Assert.assertEquals(actual.length, 4);
        Assert.assertEquals(actual[0], "dir1");
        Assert.assertEquals(actual[1], "dir2");
        Assert.assertEquals(actual[2], "dir3");
        Assert.assertEquals(actual[3], "dir4");

        fixture = "/dir1\\dir2/dir3/dir4\\";
        actual = StringUtils.split(fixture, "/\\");
        Assert.assertNotNull(actual);
        Assert.assertEquals(actual.length, 4);
        Assert.assertEquals(actual[0], "dir1");
        Assert.assertEquals(actual[1], "dir2");
        Assert.assertEquals(actual[2], "dir3");
        Assert.assertEquals(actual[3], "dir4");

    }

    @Test
    public void testInsert() {
        Assert.assertEquals("", TextUtils.insert("", 0, ""));
        Assert.assertEquals("A", TextUtils.insert("A", 0, ""));
        Assert.assertEquals("A", TextUtils.insert("A", 5, ""));
        Assert.assertEquals("A", TextUtils.insert("A", -2, ""));
        Assert.assertEquals("ABC", TextUtils.insert("ABC", -2, "D"));
        Assert.assertEquals("ABC", TextUtils.insert("ABC", 9, "D"));
        Assert.assertEquals("ABDC", TextUtils.insert("ABC", 2, "D"));
        Assert.assertEquals("ABDDD", TextUtils.insert("ABDD", 2, "D"));
        Assert.assertEquals("ABDDD", TextUtils.insert("ABDD", 4, "D"));
        Assert.assertEquals("ABDD", TextUtils.insert("ABDD", 5, "D"));
    }

    @Test
    public void isBetween() {
        Assert.assertFalse(TextUtils.isBetween(null, null, null));
        Assert.assertFalse(TextUtils.isBetween(null, "", null));
        Assert.assertFalse(TextUtils.isBetween(null, "", ""));
        Assert.assertFalse(TextUtils.isBetween("", "", ""));
        Assert.assertFalse(TextUtils.isBetween("a", null, null));
        Assert.assertFalse(TextUtils.isBetween("a", "", null));
        Assert.assertFalse(TextUtils.isBetween("a", "", ""));
        Assert.assertFalse(TextUtils.isBetween("a", "a", null));
        Assert.assertFalse(TextUtils.isBetween("a", "a", "a"));
        Assert.assertFalse(TextUtils.isBetween("ab", "a", "b"));
        Assert.assertFalse(TextUtils.isBetween("ab", "a", "ab"));
        Assert.assertFalse(TextUtils.isBetween("ab", "ab", "a"));
        Assert.assertFalse(TextUtils.isBetween("aab", "a", "ab"));
        Assert.assertFalse(TextUtils.isBetween("aab", "aa", "ab"));

        Assert.assertTrue(TextUtils.isBetween("aab", "a", "b"));
        Assert.assertTrue(TextUtils.isBetween("aabb", "aa", "b"));
        Assert.assertTrue(TextUtils.isBetween("aacbb", "aa", "bb"));
        Assert.assertTrue(TextUtils.isBetween("${data1}", "${", "}"));
    }

    @Test
    public void toOneCharArray() {
        Assert.assertNull(TextUtils.toOneCharArray(null));
        Assert.assertNull(TextUtils.toOneCharArray(""));
        Assert.assertArrayEquals(new String[]{"a"}, TextUtils.toOneCharArray("a"));
        Assert.assertArrayEquals(new String[]{"a", "b", "c"}, TextUtils.toOneCharArray("abc"));
        Assert.assertArrayEquals(new String[]{" ", " "}, TextUtils.toOneCharArray("  "));
    }

    @Test
    public void removeFirst() {
        Assert.assertNull(TextUtils.removeFirst(null, null));
        Assert.assertNull(TextUtils.removeFirst(null, ""));
        Assert.assertNull(TextUtils.removeFirst(null, " "));
        Assert.assertEquals("", TextUtils.removeFirst("", null));
        Assert.assertEquals("", TextUtils.removeFirst("", ""));
        Assert.assertEquals("", TextUtils.removeFirst("", " "));
        Assert.assertEquals("", TextUtils.removeFirst(" ", " "));
        Assert.assertEquals("", TextUtils.removeFirst("a", "a"));
        Assert.assertEquals("a", TextUtils.removeFirst("a", "b"));
        Assert.assertEquals("a", TextUtils.removeFirst("ab", "b"));
        Assert.assertEquals("ab", TextUtils.removeFirst("abb", "b"));
        Assert.assertEquals("ac", TextUtils.removeFirst("abc", "b"));
        Assert.assertEquals("ac", TextUtils.removeFirst("acb", "b"));
        Assert.assertEquals("ab", TextUtils.removeFirst("bab", "b"));
        Assert.assertEquals("bab", TextUtils.removeFirst("babab", "ab"));
        Assert.assertEquals("baab", TextUtils.removeFirst("baabab", "ab"));
        Assert.assertEquals("This works", TextUtils.removeFirst("This{IS} works", "{IS}"));
    }

    @Test
    public void xpathFriendlyQuotes() {
        Assert.assertEquals("''", TextUtils.xpathFriendlyQuotes(null));
        Assert.assertEquals("''", TextUtils.xpathFriendlyQuotes(""));
        Assert.assertEquals("' '", TextUtils.xpathFriendlyQuotes(" "));
        Assert.assertEquals("'\t'", TextUtils.xpathFriendlyQuotes("\t"));
        Assert.assertEquals("'This is a test'", TextUtils.xpathFriendlyQuotes("This is a test"));
        Assert.assertEquals("\"Mike's computer\"", TextUtils.xpathFriendlyQuotes("Mike's computer"));
        Assert.assertEquals("'\"Over the hill\" cafe'", TextUtils.xpathFriendlyQuotes("\"Over the hill\" cafe"));
        Assert.assertEquals("concat(\"Jane'\",'s \"','words\"')", TextUtils.xpathFriendlyQuotes("Jane's \"words\""));
    }

    @Test
    public void countMatches() {
        Assert.assertEquals(1, TextUtils.countMatches(null, null));
        Assert.assertEquals(1, TextUtils.countMatches(null, ""));
        Assert.assertEquals(1, TextUtils.countMatches(new ArrayList<>(), ""));
        Assert.assertEquals(1, TextUtils.countMatches(Arrays.asList(""), ""));
        Assert.assertEquals(3, TextUtils.countMatches(Arrays.asList("", "", ""), ""));
        Assert.assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", " "), "  "));
        Assert.assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", "a", " "), " a "));
        Assert.assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", "a", "b", "c", " "), " a "));
        Assert.assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", "a", "b", "c", " "), " ac "));
        Assert.assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", "a", "b", "c", " "), "as"));
        Assert.assertEquals(1, TextUtils.countMatches(Arrays.asList(" ", "", "a", "b", "c", " "), "a"));
        Assert.assertEquals(1, TextUtils.countMatches(Arrays.asList(" ", "", "a", "bc", " "), "bc"));
        Assert.assertEquals(3,
                            TextUtils
                                .countMatches(Arrays.asList(" ", "", "bc", "\t", "a", "bc", "a", "bc", " "), "bc"));
    }

    @Test
    public void xpathNormlize() {
        Assert.assertEquals("", TextUtils.xpathNormalize(null));
        Assert.assertEquals("", TextUtils.xpathNormalize(""));
        Assert.assertEquals("", TextUtils.xpathNormalize(" "));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("Hello World"));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("Hello   World"));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("  Hello   World"));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello   World\t\t\t"));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello   World\t\n\t \t \n"));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello   World\t\n\t\t\n\r\n\t "));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello \r  World\t\n\t\t\n\r\n\t "));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello \r\n  World\t\n\t\t\n\r\n\t "));
        Assert.assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello \r\n\t \r  World\t\n\t\t\n\r\n\t "));
    }

    @Test
    public void to2dList() {
        String fixture =
            "taxID,weekBeginDate,name,gross\n" +
            "623132658,20130318,ANDERSON/CARTER,5270.00\n" +
            "623132658,20130325,ANDERSON/CARTER,5622.50\n" +
            "623132658,20130401,ANDERSON/CARTER,3402.50\n" +
            "623132658,20130408,ANDERSON/CARTER,3222.50\n" +
            "623132658,20130415,ANDERSON/CARTER,3665.00\n" +
            "623132658,20130422,ANDERSON/CARTER,5285.00\n" +
            "623132658,20130429,ANDERSON/CARTER,4475.00\n" +
            "623132658,20130506,ANDERSON/CARTER,4665.00\n" +
            "623132658,20130513,ANDERSON/CARTER,4377.50\n" +
            "623132658,20130520,ANDERSON/CARTER,4745.00\n" +
            "623132658,20130527,ANDERSON/CARTER,3957.50\n" +
            "623132658,20130603,ANDERSON/CARTER,5675.00\n";
        String rowSeparator = "\n";
        String delim = ",";

        List<List<String>> twoDlist = TextUtils.to2dList(fixture, rowSeparator, delim);
        System.out.println("twoDlist = " + twoDlist);
        Assert.assertNotNull(twoDlist);
        Assert.assertEquals("[taxID, weekBeginDate, name, gross]", twoDlist.get(0).toString());
        Assert.assertEquals("[623132658, 20130318, ANDERSON/CARTER, 5270.00]", twoDlist.get(1).toString());
        Assert.assertEquals("[623132658, 20130325, ANDERSON/CARTER, 5622.50]", twoDlist.get(2).toString());
        Assert.assertEquals("[623132658, 20130401, ANDERSON/CARTER, 3402.50]", twoDlist.get(3).toString());
        Assert.assertEquals("[623132658, 20130408, ANDERSON/CARTER, 3222.50]", twoDlist.get(4).toString());
        Assert.assertEquals("[623132658, 20130415, ANDERSON/CARTER, 3665.00]", twoDlist.get(5).toString());
        Assert.assertEquals("[623132658, 20130422, ANDERSON/CARTER, 5285.00]", twoDlist.get(6).toString());
        Assert.assertEquals("[623132658, 20130429, ANDERSON/CARTER, 4475.00]", twoDlist.get(7).toString());
        Assert.assertEquals("[623132658, 20130506, ANDERSON/CARTER, 4665.00]", twoDlist.get(8).toString());
        Assert.assertEquals("[623132658, 20130513, ANDERSON/CARTER, 4377.50]", twoDlist.get(9).toString());
        Assert.assertEquals("[623132658, 20130520, ANDERSON/CARTER, 4745.00]", twoDlist.get(10).toString());
        Assert.assertEquals("[623132658, 20130527, ANDERSON/CARTER, 3957.50]", twoDlist.get(11).toString());
        Assert.assertEquals("[623132658, 20130603, ANDERSON/CARTER, 5675.00]", twoDlist.get(12).toString());
    }

    @Test
    public void to2dList_long_delim() {
        String fixture =
            "taxID<DELIM>weekBeginDate<DELIM>name<DELIM>gross<END_OF_RECORD>\n" +
            "623132658<DELIM>20130318<DELIM>ANDERSON/CARTER<DELIM>5270.00<END_OF_RECORD>\n" +
            "623132658<DELIM>20130325<DELIM>ANDERSON/CARTER<DELIM>5622.50<END_OF_RECORD>\n" +
            "623132658<DELIM>20130401<DELIM>ANDERSON/CARTER<DELIM>3402.50<END_OF_RECORD>\n" +
            "623132658<DELIM>20130408<DELIM>ANDERSON/CARTER<DELIM>3222.50<END_OF_RECORD>\n" +
            "623132658<DELIM>20130415<DELIM>ANDERSON/CARTER<DELIM>3665.00<END_OF_RECORD>\n" +
            "623132658<DELIM>20130422<DELIM>ANDERSON/CARTER<DELIM>5285.00<END_OF_RECORD>\n" +
            "623132658<DELIM>20130429<DELIM>ANDERSON/CARTER<DELIM>4475.00<END_OF_RECORD>\n" +
            "623132658<DELIM>20130506<DELIM>ANDERSON/CARTER<DELIM>4665.00<END_OF_RECORD>\n" +
            "623132658<DELIM>20130513<DELIM>ANDERSON/CARTER<DELIM>4377.50<END_OF_RECORD>\n" +
            "623132658<DELIM>20130520<DELIM>ANDERSON/CARTER<DELIM>4745.00<END_OF_RECORD>\n" +
            "623132658<DELIM>20130527<DELIM>ANDERSON/CARTER<DELIM>3957.50<END_OF_RECORD>\n" +
            "623132658<DELIM>20130603<DELIM>ANDERSON/CARTER<DELIM>5675.00<END_OF_RECORD>\n";
        String rowSeparator = "<END_OF_RECORD>\n";
        String delim = "<DELIM>";

        List<List<String>> twoDlist = TextUtils.to2dList(fixture, rowSeparator, delim);
        System.out.println("twoDlist = " + twoDlist);
        Assert.assertNotNull(twoDlist);
        Assert.assertEquals("[taxID, weekBeginDate, name, gross]", twoDlist.get(0).toString());
        Assert.assertEquals("[623132658, 20130318, ANDERSON/CARTER, 5270.00]", twoDlist.get(1).toString());
        Assert.assertEquals("[623132658, 20130325, ANDERSON/CARTER, 5622.50]", twoDlist.get(2).toString());
        Assert.assertEquals("[623132658, 20130401, ANDERSON/CARTER, 3402.50]", twoDlist.get(3).toString());
        Assert.assertEquals("[623132658, 20130408, ANDERSON/CARTER, 3222.50]", twoDlist.get(4).toString());
        Assert.assertEquals("[623132658, 20130415, ANDERSON/CARTER, 3665.00]", twoDlist.get(5).toString());
        Assert.assertEquals("[623132658, 20130422, ANDERSON/CARTER, 5285.00]", twoDlist.get(6).toString());
        Assert.assertEquals("[623132658, 20130429, ANDERSON/CARTER, 4475.00]", twoDlist.get(7).toString());
        Assert.assertEquals("[623132658, 20130506, ANDERSON/CARTER, 4665.00]", twoDlist.get(8).toString());
        Assert.assertEquals("[623132658, 20130513, ANDERSON/CARTER, 4377.50]", twoDlist.get(9).toString());
        Assert.assertEquals("[623132658, 20130520, ANDERSON/CARTER, 4745.00]", twoDlist.get(10).toString());
        Assert.assertEquals("[623132658, 20130527, ANDERSON/CARTER, 3957.50]", twoDlist.get(11).toString());
        Assert.assertEquals("[623132658, 20130603, ANDERSON/CARTER, 5675.00]", twoDlist.get(12).toString());
    }

    @Test
    public void to2dList_delim_in_content() {
        String fixture =
            "VendorId,VendorCategory,VendorTypeCode,VendorStatus,VendorCode,VendorCompositeName,VendorPayee,VendorCompanyOrLastName,VendorFirstName,VendorAddressLine1,VendorAddressLine2,VendorCity,VendorState,VendorCountry,VendorPostalCode,VendorPhone1,VendorPhone2,VendorCell,VendorEmail,VendorSSN,VendorFEIN,VendorAddressLines1and2,VendorCityStateCountryZip,TinCompositeName,TinLastName,TinFirstName,TinAddressLine1,TinAddressLine2,TinCity,TinState,TinCountry,TinPostalCode,TinAddressLines1and2,TinCityStateCountryZip,TaxYear,TransMasterId,TransTypeCode,ProjectCode,GlProductionCode,TaxLocationCode,DocumentNumber,TransDetailDescription,EpisodeCode,LocationCode,AccountNumber,CostAccount,SetCode,FreeCode1,FreeCode2,FreeCode3,FreeCode4,Code1099,Code1099Desc,ConvertedTransDetailAmount\n" +
            "303,TIN Entity,LLCP,Complete For 1099 Processing,303,330 N. WABASH AVENUE LLC,330 N. WABASH AVENUE LLC,330 N. WABASH AVENUE LLC,,330 N. WABASH STE 2325,,CHICAGO,IL,,60611,312-621-8553,,,,,36-4243298,330 N. WABASH STE 2325\\,,CHICAGO\\, IL  60611,330 N WABASH AVENUE LLC,330 N WABASH AVENUE LLC,,,,,,,,,,2016,1476,AP,OCP,,IL,CKREQ032916,4.4 LOCATION FEE-DRESS PLAZA:STATE ST BRIDGE,,02,3836,3836,001,IE,,,,01,Rents,3\\,000.00\n" +
            "552,TIN Entity,LLCP,Complete For 1099 Processing,552,A&M COLD STORAGE LLC,A&M COLD STORAGE LLC,A&M COLD STORAGE LLC,,PO BOX 1176,,SUWANEE,GA,,30024,404-276-2884,,,,,20-8626653,PO BOX 1176\\,,SUWANEE\\, GA  30024,A&M COLD STORAGE LLC,A&M COLD STORAGE LLC,,,,,,,,,,2016,3348,AP,OCP,,GA,6408,0502-0531 FREEZER TRUCK RNTL,,01,2335,2335,145,GE,12,,,01,Rents,1\\,579.00\n" +
            "407,TIN Entity,LLCSC,Complete For 1099 Processing,407,ALL ABOUT PROPS LLC,ALL ABOUT PROPS LLC,ALL ABOUT PROPS LLC,,4820 HAMMERMILL RD,SUITE H,TUCKER,GA,,30084,,,,,,06-1743580,4820 HAMMERMILL RD\\, SUITE H,TUCKER\\, GA  30084,MORROW\\, TED,MORROW,TED,,,,,,,,,2016,4926,AP,OCP,,GA,25851,0411-0530 MAIL CART RNTL x2,,01,2335,2335,145,GE,12,,,01,Rents,288.90\n" +
            "708,Individual,IND,Complete For 1099 Processing,708,AMBARUS-HINSHAW\\, INGRID-AIDA,INGRID AIDA AMBARUS-HINSHAW,AMBARUS-HINSHAW,INGRID-AIDA,1028 SANDERS AVE SE,,ATLANTA,GA,USA,30316,404-309-2166,,,,xxx-xx-1496,,1028 SANDERS AVE SE\\,,ATLANTA\\, GA  30316  USA,AMBARUS-HINSHAW\\, INGRID AIDA,AMBARUS-HINSHAW,INGRID AIDA,,,,,,,,,2016,7238,AP,OCP,,GA,1003-2016,10/02 CLEANING OF THEATRE FOR PHOTO SHOOT,,01,0529,0529,002,,,,,07,Nonemployee Compensation,450.00\n" +
            "598,Individual,IND,Complete For 1099 Processing,598,AMBURGEY\\, JILLIAN,JILLIAN AMBURGEY,AMBURGEY,JILLIAN,765 ST. CHARLES AVE.\\, APT. #4,,ATLANTA,GA,,30306,407.701.8658,,,,xxx-xx-4950,,765 ST. CHARLES AVE.\\, APT. #4\\,,ATLANTA\\, GA  30306,AMBURGEY\\, JILLIAN,AMBURGEY,JILLIAN,,,,,,,,,2016,5184,AP,OCP,,GA,051816,0510 SCRIPT TIMING SERVICES:YELLOW REVISION,,01,2013,2013,,GE,13,,,07,Nonemployee Compensation,750.00\n";
        String rowSeparator = "\n";
        String delim = ",";

        List<List<String>> twoDlist = TextUtils.to2dList(fixture, rowSeparator, delim);
        System.out.println("twoDlist = " + twoDlist);
        Assert.assertNotNull(twoDlist);
        Assert.assertEquals(
            "[VendorId, VendorCategory, VendorTypeCode, VendorStatus, VendorCode, VendorCompositeName, VendorPayee, VendorCompanyOrLastName, VendorFirstName, VendorAddressLine1, VendorAddressLine2, VendorCity, VendorState, VendorCountry, VendorPostalCode, VendorPhone1, VendorPhone2, VendorCell, VendorEmail, VendorSSN, VendorFEIN, VendorAddressLines1and2, VendorCityStateCountryZip, TinCompositeName, TinLastName, TinFirstName, TinAddressLine1, TinAddressLine2, TinCity, TinState, TinCountry, TinPostalCode, TinAddressLines1and2, TinCityStateCountryZip, TaxYear, TransMasterId, TransTypeCode, ProjectCode, GlProductionCode, TaxLocationCode, DocumentNumber, TransDetailDescription, EpisodeCode, LocationCode, AccountNumber, CostAccount, SetCode, FreeCode1, FreeCode2, FreeCode3, FreeCode4, Code1099, Code1099Desc, ConvertedTransDetailAmount]",
            twoDlist.get(0).toString());
        Assert.assertEquals(
            "[303, TIN Entity, LLCP, Complete For 1099 Processing, 303, 330 N. WABASH AVENUE LLC, 330 N. WABASH AVENUE LLC, 330 N. WABASH AVENUE LLC, , 330 N. WABASH STE 2325, , CHICAGO, IL, , 60611, 312-621-8553, , , , , 36-4243298, 330 N. WABASH STE 2325\\,, CHICAGO\\, IL  60611, 330 N WABASH AVENUE LLC, 330 N WABASH AVENUE LLC, , , , , , , , , , 2016, 1476, AP, OCP, , IL, CKREQ032916, 4.4 LOCATION FEE-DRESS PLAZA:STATE ST BRIDGE, , 02, 3836, 3836, 001, IE, , , , 01, Rents, 3\\,000.00]",
            twoDlist.get(1).toString());
        Assert.assertEquals(
            "[552, TIN Entity, LLCP, Complete For 1099 Processing, 552, A&M COLD STORAGE LLC, A&M COLD STORAGE LLC, A&M COLD STORAGE LLC, , PO BOX 1176, , SUWANEE, GA, , 30024, 404-276-2884, , , , , 20-8626653, PO BOX 1176\\,, SUWANEE\\, GA  30024, A&M COLD STORAGE LLC, A&M COLD STORAGE LLC, , , , , , , , , , 2016, 3348, AP, OCP, , GA, 6408, 0502-0531 FREEZER TRUCK RNTL, , 01, 2335, 2335, 145, GE, 12, , , 01, Rents, 1\\,579.00]",
            twoDlist.get(2).toString());
        Assert.assertEquals(
            "[407, TIN Entity, LLCSC, Complete For 1099 Processing, 407, ALL ABOUT PROPS LLC, ALL ABOUT PROPS LLC, ALL ABOUT PROPS LLC, , 4820 HAMMERMILL RD, SUITE H, TUCKER, GA, , 30084, , , , , , 06-1743580, 4820 HAMMERMILL RD\\, SUITE H, TUCKER\\, GA  30084, MORROW\\, TED, MORROW, TED, , , , , , , , , 2016, 4926, AP, OCP, , GA, 25851, 0411-0530 MAIL CART RNTL x2, , 01, 2335, 2335, 145, GE, 12, , , 01, Rents, 288.90]",
            twoDlist.get(3).toString());
        Assert.assertEquals(
            "[708, Individual, IND, Complete For 1099 Processing, 708, AMBARUS-HINSHAW\\, INGRID-AIDA, INGRID AIDA AMBARUS-HINSHAW, AMBARUS-HINSHAW, INGRID-AIDA, 1028 SANDERS AVE SE, , ATLANTA, GA, USA, 30316, 404-309-2166, , , , xxx-xx-1496, , 1028 SANDERS AVE SE\\,, ATLANTA\\, GA  30316  USA, AMBARUS-HINSHAW\\, INGRID AIDA, AMBARUS-HINSHAW, INGRID AIDA, , , , , , , , , 2016, 7238, AP, OCP, , GA, 1003-2016, 10/02 CLEANING OF THEATRE FOR PHOTO SHOOT, , 01, 0529, 0529, 002, , , , , 07, Nonemployee Compensation, 450.00]",
            twoDlist.get(4).toString());
        Assert.assertEquals(
            "[598, Individual, IND, Complete For 1099 Processing, 598, AMBURGEY\\, JILLIAN, JILLIAN AMBURGEY, AMBURGEY, JILLIAN, 765 ST. CHARLES AVE.\\, APT. #4, , ATLANTA, GA, , 30306, 407.701.8658, , , , xxx-xx-4950, , 765 ST. CHARLES AVE.\\, APT. #4\\,, ATLANTA\\, GA  30306, AMBURGEY\\, JILLIAN, AMBURGEY, JILLIAN, , , , , , , , , 2016, 5184, AP, OCP, , GA, 051816, 0510 SCRIPT TIMING SERVICES:YELLOW REVISION, , 01, 2013, 2013, , GE, 13, , , 07, Nonemployee Compensation, 750.00]",
            twoDlist.get(5).toString());
    }

    @Test
    public void to2dList_row_separator_in_content() {
        String fixture =
            "This is another way to write my code\\, albeit it sucks," +
            "Although for some folks\\, this ain't bad, or wrong";
        String rowSeparator = ",";
        String delim = " ";

        List<List<String>> twoDlist = TextUtils.to2dList(fixture, rowSeparator, delim);
        System.out.println("twoDlist = " + twoDlist);
        Assert.assertNotNull(twoDlist);
        Assert.assertEquals("[This, is, another, way, to, write, my, code\\,, albeit, it, sucks]",
                            twoDlist.get(0).toString());
        Assert.assertEquals("[Although, for, some, folks\\,, this, ain't, bad]", twoDlist.get(1).toString());
        Assert.assertEquals("[, or, wrong]", twoDlist.get(2).toString());

    }

    @Test
    public void testLoadProperties() throws Exception {
        String propContent = "nexial.lenientStringCompare=${nexial.web.alwaysWait}\n" +
                             "nexial.runID.prefix=MyOneAndOnlyTest-Part2\n" +
                             "nexial.web.alwaysWait=true\n" +
                             "nexial.ws.digest.user=User1\n" +
                             "\n" +
                             "nexial.browserstack.browser=chrome\n" +
                             "nexial.browser=${nexial.browserstack.browser}\n" +
                             "nexial.scope.fallbackToPrevious=${nexial.lenientStringCompare}\n" +
                             "\n" +
                             "mydata.type=hsqldb\n" +
                             "mydata.url=mem:\n" +
                             "mydata.treatNullAs==[NULL]\n" +
                             "\n" +
                             "myData=yourData\n" +
                             "ourData=Theirs\n" +
                             "all.the.kings.horse=and king's men\n" +
                             "couldn't put=Humpty Dumpty together again\n" +
                             "\n" +
                             "broken==\n" +
                             "rotten=\n" +
                             "gotten=";

        String tmpPropFile = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator) +
                             "testLoadProperties.properties";

        File tmpProp = new File(tmpPropFile);
        FileUtils.writeStringToFile(tmpProp, propContent, DEF_FILE_ENCODING);

        Map<String, String> propMap = TextUtils.loadProperties(tmpPropFile);
        Assert.assertEquals("Humpty Dumpty together again", propMap.get("couldn't put"));
        Assert.assertEquals("=[NULL]", propMap.get("mydata.treatNullAs"));
        Assert.assertEquals("=", propMap.get("broken"));
        Assert.assertEquals("", propMap.get("gotten"));
        Assert.assertEquals("${nexial.web.alwaysWait}", propMap.get("nexial.lenientStringCompare"));

        FileUtils.deleteQuietly(tmpProp);
    }

    @Test
    public void removeExcessWhitespaces() throws Exception {
        Assert.assertEquals("", TextUtils.removeExcessWhitespaces(null));
        Assert.assertEquals("", TextUtils.removeExcessWhitespaces(""));
        Assert.assertEquals(" ", TextUtils.removeExcessWhitespaces(" "));
        Assert.assertEquals(" ", TextUtils.removeExcessWhitespaces("  "));
        Assert.assertEquals(" ", TextUtils.removeExcessWhitespaces(" \t "));
        Assert.assertEquals(" ", TextUtils.removeExcessWhitespaces(" \n \t  "));
        Assert.assertEquals(" ", TextUtils.removeExcessWhitespaces("    \n\n     \t      \r     \r \n \t\r\t  "));
        Assert.assertEquals(" This is a test ",
                            TextUtils.removeExcessWhitespaces(" This is  \n   \r \r    a \t\t\t test         "));
        Assert.assertEquals("This is a test ",
                            TextUtils.removeExcessWhitespaces("This\nis\ra\ttest\n\n\n"));
    }

    @Test
    public void sanitizePhoneNumber() throws Exception {
        try {
            TextUtils.sanitizePhoneNumber(null);
            Assert.fail("expects IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            TextUtils.sanitizePhoneNumber("");
            Assert.fail("expects IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            TextUtils.sanitizePhoneNumber("           ");
            Assert.fail("expects IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        Assert.assertEquals("+18189091234", TextUtils.sanitizePhoneNumber("(818)909-1234"));
        Assert.assertEquals("+18189091234", TextUtils.sanitizePhoneNumber("(818)909-1234"));
        Assert.assertEquals("+18463865323", TextUtils.sanitizePhoneNumber("TIME-TO-Lead"));
    }

    @Test
    public void keepOnly() {
        Assert.assertEquals("", TextUtils.keepOnly("", "abcde"));
        Assert.assertEquals("This is a test. Do not be alarmed.",
                            TextUtils.keepOnly("This is a test. Do not be alarmed.", ""));
        Assert.assertEquals("09392394", TextUtils.keepOnly("sd0g9w3ihps9g23unap9w4", "0123456789"));
        Assert.assertEquals("0123abbey", TextUtils.keepOnly("0123abbey", "0123abbey"));
        Assert.assertEquals("0123abbey", TextUtils.keepOnly("0@1#2%3^^&*aKLRURTYbZXCBZDFGbWEWRey", "0123abbey"));

    }

    @Test
    public void base64() {
        Assert.assertEquals("WVBxVFd6ejlWbkZidGNsNE1hYmpOaDZRRW5nak5OQUg6UlNkRnRSYWdBZmtIZnNPbw==",
                            TextUtils.base64encoding("YPqTWzz9VnFbtcl4MabjNh6QEngjNNAH:RSdFtRagAfkHfsOo"));
        Assert.assertEquals("UVJHZ2tCRDNZSHp2SGdBMGVhSWgyWEd4R2VBM0FHb1k6VVV1QUhOY0xlb00yZTBWZg==",
                            TextUtils.base64encoding("QRGgkBD3YHzvHgA0eaIh2XGxGeA3AGoY:UUuAHNcLeoM2e0Vf"));

        Assert.assertEquals("YPqTWzz9VnFbtcl4MabjNh6QEngjNNAH:RSdFtRagAfkHfsOo",
                            TextUtils.base64decoding("WVBxVFd6ejlWbkZidGNsNE1hYmpOaDZRRW5nak5OQUg6UlNkRnRSYWdBZmtIZnNPbw=="));
        Assert.assertEquals("QRGgkBD3YHzvHgA0eaIh2XGxGeA3AGoY:UUuAHNcLeoM2e0Vf",
                            TextUtils.base64decoding("UVJHZ2tCRDNZSHp2SGdBMGVhSWgyWEd4R2VBM0FHb1k6VVV1QUhOY0xlb00yZTBWZg=="));
    }
}