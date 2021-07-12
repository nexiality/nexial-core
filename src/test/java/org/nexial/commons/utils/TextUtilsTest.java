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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static java.io.File.separator;
import static org.junit.Assert.*;
import static org.nexial.commons.utils.TextUtils.CleanNumberStrategy.CSV;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;

public class TextUtilsTest {

    @Test
    public void testSubstringBetweenClosestPair() {
        assertEquals("a", TextUtils.substringBetweenFirstPair("((a))", "(", ")"));
        assertEquals("jolly good", TextUtils.substringBetweenFirstPair("((jolly good))", "(", ")"));
        assertEquals("bracket", TextUtils.substringBetweenFirstPair("[bracket]", "[", "]"));
        assertEquals("bracket", TextUtils.substringBetweenFirstPair("[bracket]]]", "[", "]"));
        assertEquals("bracket", TextUtils.substringBetweenFirstPair("[[[[bracket]]", "[", "]"));
        assertEquals("", TextUtils.substringBetweenFirstPair("[][[[bracket]]", "[", "]"));
        assertNull(TextUtils.substringBetweenFirstPair("bracket]bracket[[[bracket]]", "[", "]"));
    }

    @Test
    public void testSubstringBetweenClosestPair_includeSep() {
        assertEquals("(a)", TextUtils.substringBetweenFirstPair("((a))", "(", ")", true));
        assertEquals("(jolly good)", TextUtils.substringBetweenFirstPair("((jolly good))", "(", ")", true));
        assertEquals("[bracket]", TextUtils.substringBetweenFirstPair("[bracket]", "[", "]", true));
        assertEquals("[bracket]", TextUtils.substringBetweenFirstPair("[bracket]]]", "[", "]", true));
        assertEquals("[bracket]", TextUtils.substringBetweenFirstPair("[[[[bracket]]", "[", "]", true));
        assertEquals("[]", TextUtils.substringBetweenFirstPair("[][[[bracket]]", "[", "]", true));
        assertNull(TextUtils.substringBetweenFirstPair("bracket]bracket[[[bracket]]", "[", "]", true));
    }

    @Test
    public void testToString() {
        Map<String, String> fixture = new HashMap<>();
        fixture.put("one", "yee");
        fixture.put("two", "er");
        fixture.put("three", "san");
        assertEquals("one=yee|two=er|three=san", TextUtils.toString(fixture, "|", "="));

        fixture = new HashMap<>();
        fixture.put("a", "");
        fixture.put("b", "");
        fixture.put("c", "=");
        assertEquals("a=|b=|c==", TextUtils.toString(fixture, "|", "="));
        assertEquals("a= b= c==", TextUtils.toString(fixture, " ", "="));
    }

    @Test
    public void testToOneLine() {
        assertEquals("", TextUtils.toOneLine("", true));
        assertEquals("", TextUtils.toOneLine(" ", true));
        assertEquals("", TextUtils.toOneLine("\t", true));
        assertEquals("\n", TextUtils.toOneLine("\n", false));
        assertEquals("   ", TextUtils.toOneLine(" \n ", false));
        assertEquals("That's enough already Jimmy", TextUtils.toOneLine("That's \nenough\r already\n\rJimmy", true));
        assertEquals("Check out my really awesome hover text. I can include HTML tags and everything!!!",
                     TextUtils.toOneLine("Check out my really awesome\n" +
                                         " hover text. \n" +
                                         " I can include HTML tags and everything!!!", true));
    }

    @Test
    public void testToList() {
        String fixture = "a,,b,,c,d";

        List<String> actual = TextUtils.toList(fixture, ":", true);
        assertEquals(1, actual.size());
        assertEquals(fixture, actual.get(0));

        actual = TextUtils.toList(fixture, ",", true);
        assertEquals(actual.size(), 6);
        assertEquals(actual.get(0), "a");
        assertEquals(actual.get(1), "");
        assertEquals(actual.get(2), "b");
        assertEquals(actual.get(3), "");
        assertEquals(actual.get(4), "c");
        assertEquals(actual.get(5), "d");
    }

    @Test
    public void testToList_single_item() {
        String fixture = "this is one single item without delimiter";

        List<String> actual = TextUtils.toList(fixture, ",", true);
        assertEquals(1, actual.size());
        assertEquals(fixture, actual.get(0));
    }

    @Test
    public void toListPreserveTokens() {
        String fixture = "a,,b,,c,d";

        List<String> actual = TextUtils.toListPreserveTokens(fixture, ",", false);
        assertEquals(actual.size(), 6);
        assertEquals(actual.get(0), "a");
        assertEquals(actual.get(1), "");
        assertEquals(actual.get(2), "b");
        assertEquals(actual.get(3), "");
        assertEquals(actual.get(4), "c");
        assertEquals(actual.get(5), "d");
    }

    @Test
    public void toListPreserveTokens_long_delim() {
        String fixture = "Every~=~Time~=~You~=~Leave~=~Me";

        List<String> actual = TextUtils.toListPreserveTokens(fixture, "~=~", false);
        assertEquals(5, actual.size());
        assertEquals("Every", actual.get(0));
        assertEquals("Time", actual.get(1));
        assertEquals("You", actual.get(2));
        assertEquals("Leave", actual.get(3));
        assertEquals("Me", actual.get(4));
    }

    @Test
    public void testToListWith2Spaces() {
        String fixture = "Active     NUM            Name             Client  Type    FSO ID          FSO Name";
        List<String> actual = TextUtils.toList(fixture, "  ", true);
        assertEquals(actual.size(), 7);
        assertEquals(actual.get(0), "Active");
        assertEquals(actual.get(1), "NUM");
        assertEquals(actual.get(2), "Name");
        assertEquals(actual.get(3), "Client");
        assertEquals(actual.get(4), "Type");
        assertEquals(actual.get(5), "FSO ID");
        assertEquals(actual.get(6), "FSO Name");
    }

    @Test
    public void testToListWithMultipleTabs() {
        String fixture = "Active\tNUM\t\tName\t\t\tClient\tType\t\t\t\t\tFSO ID\t\tFSO Name";
        List<String> actual = TextUtils.toList(fixture, "\t", true);
        assertEquals(actual.size(), 7);
        assertEquals(actual.get(0), "Active");
        assertEquals(actual.get(1), "NUM");
        assertEquals(actual.get(2), "Name");
        assertEquals(actual.get(3), "Client");
        assertEquals(actual.get(4), "Type");
        assertEquals(actual.get(5), "FSO ID");
        assertEquals(actual.get(6), "FSO Name");
    }

    @Test
    public void testToListPreserveTokens() {
        assertNull(TextUtils.toListPreserveTokens("", ",", true));
        assertEquals("[a]", TextUtils.toListPreserveTokens("a", ",", true).toString());
        assertEquals("[a, b, c, d, e]", TextUtils.toListPreserveTokens("a,b,c,d,e", ",", true).toString());
        assertEquals("[a, b, , d, e]", TextUtils.toListPreserveTokens("a,b,,d,e", ",", true).toString());
        assertEquals("[, b, , d, e]", TextUtils.toListPreserveTokens(",b,,d,e", ",", true).toString());
        assertEquals("[, , , , e]", TextUtils.toListPreserveTokens(",,,,e", ",", true).toString());
        assertEquals("[, , , , e]", TextUtils.toListPreserveTokens(",    ,,  ,e", ",", true).toString());
        assertEquals("[, , , , e]", TextUtils.toListPreserveTokens("        ,   ,,  ,e", ",", true).toString());
        assertEquals("[, b, , , e]", TextUtils.toListPreserveTokens("      , b  ,,  ,e", ",", true).toString());
    }

    /**
     * proving that {@link StringUtils#split(String)} can handle multiple delimiters independently.
     */
    @Test
    public void testStringUtilsSplit() {
        String fixture = "C:\\dir1\\dir2\\dir3\\dir4";
        String[] actual = StringUtils.split(fixture, "\\/");
        assertNotNull(actual);
        assertEquals(actual.length, 5);
        assertEquals(actual[0], "C:");
        assertEquals(actual[1], "dir1");
        assertEquals(actual[2], "dir2");
        assertEquals(actual[3], "dir3");
        assertEquals(actual[4], "dir4");

        fixture = "/dir1/dir2/dir3/dir4";
        actual = StringUtils.split(fixture, "\\/");
        assertNotNull(actual);
        assertEquals(actual.length, 4);
        assertEquals(actual[0], "dir1");
        assertEquals(actual[1], "dir2");
        assertEquals(actual[2], "dir3");
        assertEquals(actual[3], "dir4");

        fixture = "/dir1\\dir2/dir3/dir4\\";
        actual = StringUtils.split(fixture, "/\\");
        assertNotNull(actual);
        assertEquals(actual.length, 4);
        assertEquals(actual[0], "dir1");
        assertEquals(actual[1], "dir2");
        assertEquals(actual[2], "dir3");
        assertEquals(actual[3], "dir4");
    }

    @Test
    public void testInsert() {
        assertEquals("", TextUtils.insert("", 0, ""));
        assertEquals("A", TextUtils.insert("A", 0, ""));
        assertEquals("A", TextUtils.insert("A", 5, ""));
        assertEquals("A", TextUtils.insert("A", -2, ""));
        assertEquals("ABC", TextUtils.insert("ABC", -2, "D"));
        assertEquals("ABC", TextUtils.insert("ABC", 9, "D"));
        assertEquals("ABDC", TextUtils.insert("ABC", 2, "D"));
        assertEquals("ABDDD", TextUtils.insert("ABDD", 2, "D"));
        assertEquals("ABDDD", TextUtils.insert("ABDD", 4, "D"));
        assertEquals("ABDD", TextUtils.insert("ABDD", 5, "D"));
    }

    @Test
    public void isBetween() {
        assertFalse(TextUtils.isBetween(null, null, null));
        assertFalse(TextUtils.isBetween(null, "", null));
        assertFalse(TextUtils.isBetween(null, "", ""));
        assertFalse(TextUtils.isBetween("", "", ""));
        assertFalse(TextUtils.isBetween("a", null, null));
        assertFalse(TextUtils.isBetween("a", "", null));
        assertFalse(TextUtils.isBetween("a", "", ""));
        assertFalse(TextUtils.isBetween("a", "a", null));
        assertFalse(TextUtils.isBetween("a", "a", "a"));
        assertFalse(TextUtils.isBetween("ab", "a", "b"));
        assertFalse(TextUtils.isBetween("ab", "a", "ab"));
        assertFalse(TextUtils.isBetween("ab", "ab", "a"));
        assertFalse(TextUtils.isBetween("aab", "a", "ab"));
        assertFalse(TextUtils.isBetween("aab", "aa", "ab"));

        assertTrue(TextUtils.isBetween("aab", "a", "b"));
        assertTrue(TextUtils.isBetween("aabb", "aa", "b"));
        assertTrue(TextUtils.isBetween("aacbb", "aa", "bb"));
        assertTrue(TextUtils.isBetween("${data1}", "${", "}"));
    }

    @Test
    public void toOneCharArray() {
        assertNull(TextUtils.toOneCharArray(null));
        assertNull(TextUtils.toOneCharArray(""));
        assertArrayEquals(new String[]{"a"}, TextUtils.toOneCharArray("a"));
        assertArrayEquals(new String[]{"a", "b", "c"}, TextUtils.toOneCharArray("abc"));
        assertArrayEquals(new String[]{" ", " "}, TextUtils.toOneCharArray("  "));
    }

    @Test
    public void removeFirst() {
        assertNull(TextUtils.removeFirst(null, null));
        assertNull(TextUtils.removeFirst(null, ""));
        assertNull(TextUtils.removeFirst(null, " "));
        assertEquals("", TextUtils.removeFirst("", null));
        assertEquals("", TextUtils.removeFirst("", ""));
        assertEquals("", TextUtils.removeFirst("", " "));
        assertEquals("", TextUtils.removeFirst(" ", " "));
        assertEquals("", TextUtils.removeFirst("a", "a"));
        assertEquals("a", TextUtils.removeFirst("a", "b"));
        assertEquals("a", TextUtils.removeFirst("ab", "b"));
        assertEquals("ab", TextUtils.removeFirst("abb", "b"));
        assertEquals("ac", TextUtils.removeFirst("abc", "b"));
        assertEquals("ac", TextUtils.removeFirst("acb", "b"));
        assertEquals("ab", TextUtils.removeFirst("bab", "b"));
        assertEquals("bab", TextUtils.removeFirst("babab", "ab"));
        assertEquals("baab", TextUtils.removeFirst("baabab", "ab"));
        assertEquals("This works", TextUtils.removeFirst("This{IS} works", "{IS}"));
    }

    @Test
    public void xpathFriendlyQuotes() {
        assertEquals("''", TextUtils.xpathFriendlyQuotes(null));
        assertEquals("''", TextUtils.xpathFriendlyQuotes(""));
        assertEquals("' '", TextUtils.xpathFriendlyQuotes(" "));
        assertEquals("'\t'", TextUtils.xpathFriendlyQuotes("\t"));
        assertEquals("'This is a test'", TextUtils.xpathFriendlyQuotes("This is a test"));
        assertEquals("\"Mike's computer\"", TextUtils.xpathFriendlyQuotes("Mike's computer"));
        assertEquals("'\"Over the hill\" cafe'", TextUtils.xpathFriendlyQuotes("\"Over the hill\" cafe"));
        assertEquals("concat(\"Jane'\",'s \"','words\"')", TextUtils.xpathFriendlyQuotes("Jane's \"words\""));
    }

    @Test
    public void countMatches() {
        assertEquals(1, TextUtils.countMatches(null, null));
        assertEquals(1, TextUtils.countMatches(null, ""));
        assertEquals(1, TextUtils.countMatches(new ArrayList<>(), ""));
        assertEquals(1, TextUtils.countMatches(Arrays.asList(""), ""));
        assertEquals(3, TextUtils.countMatches(Arrays.asList("", "", ""), ""));
        assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", " "), "  "));
        assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", "a", " "), " a "));
        assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", "a", "b", "c", " "), " a "));
        assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", "a", "b", "c", " "), " ac "));
        assertEquals(0, TextUtils.countMatches(Arrays.asList(" ", "", "a", "b", "c", " "), "as"));
        assertEquals(1, TextUtils.countMatches(Arrays.asList(" ", "", "a", "b", "c", " "), "a"));
        assertEquals(1, TextUtils.countMatches(Arrays.asList(" ", "", "a", "bc", " "), "bc"));
        assertEquals(3, TextUtils.countMatches(Arrays.asList(" ", "", "bc", "\t", "a", "bc", "a", "bc", " "), "bc"));
    }

    @Test
    public void xpathNormalize() {
        assertEquals("", TextUtils.xpathNormalize(null));
        assertEquals("", TextUtils.xpathNormalize(""));
        assertEquals("", TextUtils.xpathNormalize(" "));
        assertEquals("Hello World", TextUtils.xpathNormalize("Hello World"));
        assertEquals("Hello World", TextUtils.xpathNormalize("Hello   World"));
        assertEquals("Hello World", TextUtils.xpathNormalize("  Hello   World"));
        assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello   World\t\t\t"));
        assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello   World\t\n\t \t \n"));
        assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello   World\t\n\t\t\n\r\n\t "));
        assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello \r  World\t\n\t\t\n\r\n\t "));
        assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello \r\n  World\t\n\t\t\n\r\n\t "));
        assertEquals("Hello World", TextUtils.xpathNormalize("  \t  Hello \r\n\t \r  World\t\n\t\t\n\r\n\t "));
    }

    @Test
    public void to2dList() {
        String fixture = "taxID,weekBeginDate,name,gross\n" +
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
        assertNotNull(twoDlist);
        assertEquals("[taxID, weekBeginDate, name, gross]", twoDlist.get(0).toString());
        assertEquals("[623132658, 20130318, ANDERSON/CARTER, 5270.00]", twoDlist.get(1).toString());
        assertEquals("[623132658, 20130325, ANDERSON/CARTER, 5622.50]", twoDlist.get(2).toString());
        assertEquals("[623132658, 20130401, ANDERSON/CARTER, 3402.50]", twoDlist.get(3).toString());
        assertEquals("[623132658, 20130408, ANDERSON/CARTER, 3222.50]", twoDlist.get(4).toString());
        assertEquals("[623132658, 20130415, ANDERSON/CARTER, 3665.00]", twoDlist.get(5).toString());
        assertEquals("[623132658, 20130422, ANDERSON/CARTER, 5285.00]", twoDlist.get(6).toString());
        assertEquals("[623132658, 20130429, ANDERSON/CARTER, 4475.00]", twoDlist.get(7).toString());
        assertEquals("[623132658, 20130506, ANDERSON/CARTER, 4665.00]", twoDlist.get(8).toString());
        assertEquals("[623132658, 20130513, ANDERSON/CARTER, 4377.50]", twoDlist.get(9).toString());
        assertEquals("[623132658, 20130520, ANDERSON/CARTER, 4745.00]", twoDlist.get(10).toString());
        assertEquals("[623132658, 20130527, ANDERSON/CARTER, 3957.50]", twoDlist.get(11).toString());
        assertEquals("[623132658, 20130603, ANDERSON/CARTER, 5675.00]", twoDlist.get(12).toString());
    }

    @Test
    public void to2dList_long_delim() {
        String fixture = "taxID<DELIM>weekBeginDate<DELIM>name<DELIM>gross<END_OF_RECORD>\n" +
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
        assertNotNull(twoDlist);
        assertEquals("[taxID, weekBeginDate, name, gross]", twoDlist.get(0).toString());
        assertEquals("[623132658, 20130318, ANDERSON/CARTER, 5270.00]", twoDlist.get(1).toString());
        assertEquals("[623132658, 20130325, ANDERSON/CARTER, 5622.50]", twoDlist.get(2).toString());
        assertEquals("[623132658, 20130401, ANDERSON/CARTER, 3402.50]", twoDlist.get(3).toString());
        assertEquals("[623132658, 20130408, ANDERSON/CARTER, 3222.50]", twoDlist.get(4).toString());
        assertEquals("[623132658, 20130415, ANDERSON/CARTER, 3665.00]", twoDlist.get(5).toString());
        assertEquals("[623132658, 20130422, ANDERSON/CARTER, 5285.00]", twoDlist.get(6).toString());
        assertEquals("[623132658, 20130429, ANDERSON/CARTER, 4475.00]", twoDlist.get(7).toString());
        assertEquals("[623132658, 20130506, ANDERSON/CARTER, 4665.00]", twoDlist.get(8).toString());
        assertEquals("[623132658, 20130513, ANDERSON/CARTER, 4377.50]", twoDlist.get(9).toString());
        assertEquals("[623132658, 20130520, ANDERSON/CARTER, 4745.00]", twoDlist.get(10).toString());
        assertEquals("[623132658, 20130527, ANDERSON/CARTER, 3957.50]", twoDlist.get(11).toString());
        assertEquals("[623132658, 20130603, ANDERSON/CARTER, 5675.00]", twoDlist.get(12).toString());
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
        assertNotNull(twoDlist);
        assertEquals(
            "[VendorId, VendorCategory, VendorTypeCode, VendorStatus, VendorCode, VendorCompositeName, VendorPayee, VendorCompanyOrLastName, VendorFirstName, VendorAddressLine1, VendorAddressLine2, VendorCity, VendorState, VendorCountry, VendorPostalCode, VendorPhone1, VendorPhone2, VendorCell, VendorEmail, VendorSSN, VendorFEIN, VendorAddressLines1and2, VendorCityStateCountryZip, TinCompositeName, TinLastName, TinFirstName, TinAddressLine1, TinAddressLine2, TinCity, TinState, TinCountry, TinPostalCode, TinAddressLines1and2, TinCityStateCountryZip, TaxYear, TransMasterId, TransTypeCode, ProjectCode, GlProductionCode, TaxLocationCode, DocumentNumber, TransDetailDescription, EpisodeCode, LocationCode, AccountNumber, CostAccount, SetCode, FreeCode1, FreeCode2, FreeCode3, FreeCode4, Code1099, Code1099Desc, ConvertedTransDetailAmount]",
            twoDlist.get(0).toString());
        assertEquals(
            "[303, TIN Entity, LLCP, Complete For 1099 Processing, 303, 330 N. WABASH AVENUE LLC, 330 N. WABASH AVENUE LLC, 330 N. WABASH AVENUE LLC, , 330 N. WABASH STE 2325, , CHICAGO, IL, , 60611, 312-621-8553, , , , , 36-4243298, 330 N. WABASH STE 2325\\,, CHICAGO\\, IL  60611, 330 N WABASH AVENUE LLC, 330 N WABASH AVENUE LLC, , , , , , , , , , 2016, 1476, AP, OCP, , IL, CKREQ032916, 4.4 LOCATION FEE-DRESS PLAZA:STATE ST BRIDGE, , 02, 3836, 3836, 001, IE, , , , 01, Rents, 3\\,000.00]",
            twoDlist.get(1).toString());
        assertEquals(
            "[552, TIN Entity, LLCP, Complete For 1099 Processing, 552, A&M COLD STORAGE LLC, A&M COLD STORAGE LLC, A&M COLD STORAGE LLC, , PO BOX 1176, , SUWANEE, GA, , 30024, 404-276-2884, , , , , 20-8626653, PO BOX 1176\\,, SUWANEE\\, GA  30024, A&M COLD STORAGE LLC, A&M COLD STORAGE LLC, , , , , , , , , , 2016, 3348, AP, OCP, , GA, 6408, 0502-0531 FREEZER TRUCK RNTL, , 01, 2335, 2335, 145, GE, 12, , , 01, Rents, 1\\,579.00]",
            twoDlist.get(2).toString());
        assertEquals(
            "[407, TIN Entity, LLCSC, Complete For 1099 Processing, 407, ALL ABOUT PROPS LLC, ALL ABOUT PROPS LLC, ALL ABOUT PROPS LLC, , 4820 HAMMERMILL RD, SUITE H, TUCKER, GA, , 30084, , , , , , 06-1743580, 4820 HAMMERMILL RD\\, SUITE H, TUCKER\\, GA  30084, MORROW\\, TED, MORROW, TED, , , , , , , , , 2016, 4926, AP, OCP, , GA, 25851, 0411-0530 MAIL CART RNTL x2, , 01, 2335, 2335, 145, GE, 12, , , 01, Rents, 288.90]",
            twoDlist.get(3).toString());
        assertEquals(
            "[708, Individual, IND, Complete For 1099 Processing, 708, AMBARUS-HINSHAW\\, INGRID-AIDA, INGRID AIDA AMBARUS-HINSHAW, AMBARUS-HINSHAW, INGRID-AIDA, 1028 SANDERS AVE SE, , ATLANTA, GA, USA, 30316, 404-309-2166, , , , xxx-xx-1496, , 1028 SANDERS AVE SE\\,, ATLANTA\\, GA  30316  USA, AMBARUS-HINSHAW\\, INGRID AIDA, AMBARUS-HINSHAW, INGRID AIDA, , , , , , , , , 2016, 7238, AP, OCP, , GA, 1003-2016, 10/02 CLEANING OF THEATRE FOR PHOTO SHOOT, , 01, 0529, 0529, 002, , , , , 07, Nonemployee Compensation, 450.00]",
            twoDlist.get(4).toString());
        assertEquals(
            "[598, Individual, IND, Complete For 1099 Processing, 598, AMBURGEY\\, JILLIAN, JILLIAN AMBURGEY, AMBURGEY, JILLIAN, 765 ST. CHARLES AVE.\\, APT. #4, , ATLANTA, GA, , 30306, 407.701.8658, , , , xxx-xx-4950, , 765 ST. CHARLES AVE.\\, APT. #4\\,, ATLANTA\\, GA  30306, AMBURGEY\\, JILLIAN, AMBURGEY, JILLIAN, , , , , , , , , 2016, 5184, AP, OCP, , GA, 051816, 0510 SCRIPT TIMING SERVICES:YELLOW REVISION, , 01, 2013, 2013, , GE, 13, , , 07, Nonemployee Compensation, 750.00]",
            twoDlist.get(5).toString());
    }

    @Test
    public void to2dList_row_separator_in_content() {
        String fixture = "This is another way to write my code\\, albeit it sucks," +
                         "Although for some folks\\, this ain't bad, or wrong";
        String rowSeparator = ",";
        String delim = " ";

        List<List<String>> twoDlist = TextUtils.to2dList(fixture, rowSeparator, delim);
        System.out.println("twoDlist = " + twoDlist);
        assertNotNull(twoDlist);
        assertEquals("[This, is, another, way, to, write, my, code\\,, albeit, it, sucks]", twoDlist.get(0).toString());
        assertEquals("[Although, for, some, folks\\,, this, ain't, bad]", twoDlist.get(1).toString());
        assertEquals("[, or, wrong]", twoDlist.get(2).toString());

    }

    @Test
    public void testLoadProperties() throws Exception {
        String propContent = "nexial.lenientStringCompare   =${nexial.web.explicitWait}\n" +
                             "nexial.runID.prefix           =MyOneAndOnlyTest-Part2\n" +
                             "nexial.web.explicitWait         =true\n" +
                             "nexial.ws.digest.user         =User1\n" +
                             "\n" +
                             "nexial.browserstack.browser   =chrome\n" +
                             "nexial.browser                =${nexial.browserstack.browser}\n" +
                             "nexial.scope.fallbackToPrevious=${nexial.lenientStringCompare}\n" +
                             "\n" +
                             "mydata.type       =hsqldb\n" +
                             "mydata.url        =mem:\n" +
                             "mydata.treatNullAs==[NULL]\n" +
                             "\n" +
                             "myData                =yourData\n" +
                             "ourData               =Theirs\n" +
                             "all.the.kings.horse   =and king's men\n" +
                             "couldn't put          =Humpty Dumpty together again\n" +
                             "\n" +
                             "my.datasource.url=jdbc://myserver:1099/dbname;prop1=value1;prop2=#hash2;prop3=!what\n" +
                             "broken==\n" +
                             "rotten=\n" +
                             "gotten=";

        String tmpPropFile = StringUtils.appendIfMissing(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), separator) +
                             "testLoadProperties.properties";

        File tmpProp = new File(tmpPropFile);
        FileUtils.writeStringToFile(tmpProp, propContent, DEF_FILE_ENCODING);

        Map<String, String> propMap = TextUtils.loadProperties(tmpPropFile, true);
        assertEquals("Humpty Dumpty together again", propMap.get("couldn't put"));
        assertEquals("=[NULL]", propMap.get("mydata.treatNullAs"));
        assertEquals("=", propMap.get("broken"));
        assertEquals("", propMap.get("gotten"));
        assertEquals("${nexial.web.explicitWait}", propMap.get("nexial.lenientStringCompare"));
        assertEquals("jdbc://myserver:1099/dbname;prop1=value1;prop2=#hash2;prop3=!what",
                     propMap.get("my.datasource.url"));

        FileUtils.deleteQuietly(tmpProp);
    }

    @Test
    public void testLoadPropertiesFileWithSpacesInValues() {
        String testFixture = ResourceUtils.getResourceFilePath("/dummy-test.project.properties");

        Map<String, String> propMap = TextUtils.loadProperties(testFixture);
        assert propMap != null;
        assertEquals(propMap.size(), 7);
        assertEquals(propMap.get("environment"), "PROD");
        assertEquals(propMap.get("mySite.url"), "https://abcdefg.qwertyu.com/user21/logon.aspx?key1=Integon&key2=63befb2pdosk8e358adf39f95e63700e&partnerId=qpwoslkgRater&redirectUrl=~/rating/Default.aspx ");
        assertEquals(propMap.get("yoursite.url"), "http://1qazxswedfg.xvbghy.com/PolicyReview/ ");
        assertEquals(propMap.get("theirSite.url"), "https://rfgthyuj.mvncbxvs.com/09sidu7/logon.aspx?key1=Integon&key2=63befb211f2cfe358w23er455e63700e&partnerId=asdfrtyu7890jhg&redirectUrl=~//rating/search/quotesearch.aspx \t\t\t\t\t");
        assertEquals(propMap.get("username"), "tech21					");
        assertEquals(propMap.get("passwordClue"), "agency19	");
        assertEquals(propMap.get("mySuperDuperDB.url"), "jdbc:sqlserver://myserver09sdb04:1433;databaseName=dbase21;integratedSecurity=true;authenticationScheme=JavaKerberos");
    }

    @Test
    public void removeExcessWhitespaces() {
        assertEquals("", TextUtils.removeExcessWhitespaces(null));
        assertEquals("", TextUtils.removeExcessWhitespaces(""));
        assertEquals(" ", TextUtils.removeExcessWhitespaces(" "));
        assertEquals(" ", TextUtils.removeExcessWhitespaces("  "));
        assertEquals(" ", TextUtils.removeExcessWhitespaces(" \t "));
        assertEquals(" ", TextUtils.removeExcessWhitespaces(" \n \t  "));
        assertEquals(" ", TextUtils.removeExcessWhitespaces("    \n\n     \t      \r     \r \n \t\r\t  "));
        assertEquals(" This is a test ",
                     TextUtils.removeExcessWhitespaces(" This is  \n   \r \r    a \t\t\t test         "));
        assertEquals("This is a test ", TextUtils.removeExcessWhitespaces("This\nis\ra\ttest\n\n\n"));
    }

    @Test
    public void removeEndRepeatedly() {
        assertEquals("", TextUtils.removeEndRepeatedly(null, null));
        assertEquals("", TextUtils.removeEndRepeatedly(null, ""));
        assertEquals("", TextUtils.removeEndRepeatedly("", null));
        assertEquals("", TextUtils.removeEndRepeatedly("", " "));
        assertEquals("", TextUtils.removeEndRepeatedly("", "\t"));
        assertEquals("\t", TextUtils.removeEndRepeatedly("\t", " "));
        assertEquals("", TextUtils.removeEndRepeatedly("     ", " "));
        assertEquals("Username", TextUtils.removeEndRepeatedly("Username . . . . . .", " ."));
        assertEquals("Username . . . . . .:", TextUtils.removeEndRepeatedly("Username . . . . . .:", " ."));
    }

    @Test
    public void sanitizePhoneNumber() {
        try {
            TextUtils.sanitizePhoneNumber(null);
            fail("expects IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            TextUtils.sanitizePhoneNumber("");
            fail("expects IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            TextUtils.sanitizePhoneNumber("           ");
            fail("expects IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertEquals("+18189091234", TextUtils.sanitizePhoneNumber("(818)909-1234"));
        assertEquals("+18189091234", TextUtils.sanitizePhoneNumber("(818)909-1234"));
        assertEquals("+18463865323", TextUtils.sanitizePhoneNumber("TIME-TO-Lead"));
    }

    @Test
    public void keepOnly() {
        assertEquals("", TextUtils.keepOnly("", "abcde"));
        assertEquals("This is a test. Do not be alarmed.",
                     TextUtils.keepOnly("This is a test. Do not be alarmed.", ""));
        assertEquals("09392394", TextUtils.keepOnly("sd0g9w3ihps9g23unap9w4", "0123456789"));
        assertEquals("0123abbey", TextUtils.keepOnly("0123abbey", "0123abbey"));
        assertEquals("0123abbey", TextUtils.keepOnly("0@1#2%3^^&*aKLRURTYbZXCBZDFGbWEWRey", "0123abbey"));
        assertEquals("4567321.004", TextUtils.keepOnly("-$4,567,321.004", "0123456789."));
    }

    @Test
    public void removeOnly() {
        assertEquals("", TextUtils.removeOnly("", ""));
        assertEquals("", TextUtils.removeOnly("", "12345"));
        assertEquals("12345", TextUtils.removeOnly("12345", ""));
        assertEquals("12345", TextUtils.removeOnly("12345", " "));
        assertEquals("12345", TextUtils.removeOnly("12345", "abcdef"));
        assertEquals("Thatet", TextUtils.removeOnly("This is a test", " is"));
    }

    @Test
    public void base64() {
        assertEquals("WVBxVFd6ejlWbkZidGNsNE1hYmpOaDZRRW5nak5OQUg6UlNkRnRSYWdBZmtIZnNPbw==",
                     TextUtils.base64encode("YPqTWzz9VnFbtcl4MabjNh6QEngjNNAH:RSdFtRagAfkHfsOo"));
        assertEquals("UVJHZ2tCRDNZSHp2SGdBMGVhSWgyWEd4R2VBM0FHb1k6VVV1QUhOY0xlb00yZTBWZg==",
                     TextUtils.base64encode("QRGgkBD3YHzvHgA0eaIh2XGxGeA3AGoY:UUuAHNcLeoM2e0Vf"));
        assertEquals("YPqTWzz9VnFbtcl4MabjNh6QEngjNNAH:RSdFtRagAfkHfsOo",
                     TextUtils.base64decode("WVBxVFd6ejlWbkZidGNsNE1hYmpOaDZRRW5nak5OQUg6UlNkRnRSYWdBZmtIZnNPbw=="));
        assertEquals("QRGgkBD3YHzvHgA0eaIh2XGxGeA3AGoY:UUuAHNcLeoM2e0Vf",
                     TextUtils.base64decode("UVJHZ2tCRDNZSHp2SGdBMGVhSWgyWEd4R2VBM0FHb1k6VVV1QUhOY0xlb00yZTBWZg=="));
    }

    @Test
    public void breakLines() {
        // sanity checks
        assertEquals("This is a test. Do not be alarmed.",
                     TextUtils.demarcate("This is a test. Do not be alarmed.", 5, ""));
        assertEquals("This is a test. Do not be alarmed.",
                     TextUtils.demarcate("This is a test. Do not be alarmed.", 0, ","));
        assertEquals("This is a test. Do not be alarmed.",
                     TextUtils.demarcate("This is a test. Do not be alarmed.", 35, ","));

        assertEquals("This |is a |test.| Do n|ot be| alar|med.",
                     TextUtils.demarcate("This is a test. Do not be alarmed.", 5, "|"));
        assertEquals("This > <is a > <test.> < Do n> <ot be> < alar> <med.",
                     TextUtils.demarcate("This is a test. Do not be alarmed.", 5, "> <"));
        assertEquals("This <**>is a <**>test.<**> Do n<**>ot be<**> alar<**>med!!",
                     TextUtils.demarcate("This is a test. Do not be alarmed!!", 5, "<**>"));
        assertEquals("This is a test.... Do not be alar...med!!",
                     TextUtils.demarcate("This is a test. Do not be alarmed!!", 15, "..."));
    }

    @Test
    public void cleanNumber_simple() {
        assertEquals(7.7, NumberUtils.createDouble(TextUtils.cleanNumber("7.7", CSV)), 0);
        assertEquals(7.7, NumberUtils.createDouble(TextUtils.cleanNumber("7.70", CSV)), 0);
        assertEquals(7.7, NumberUtils.createDouble(TextUtils.cleanNumber("7.70000000", CSV)), 0);
        assertEquals(-7.7, NumberUtils.createDouble(TextUtils.cleanNumber("-07.7000", CSV)), 0);
        assertEquals(-7.700123, NumberUtils.createDouble(TextUtils.cleanNumber("-07.700123", CSV)), 0);
        assertEquals(0, NumberUtils.createDouble(TextUtils.cleanNumber("0.00", CSV)), 0);
        assertEquals(0, NumberUtils.createDouble(TextUtils.cleanNumber("000.0", CSV)), 0);
        assertEquals(0, NumberUtils.createDouble(TextUtils.cleanNumber("-0.00", CSV)), 0);
    }

    @Test
    public void insertAfter() {
        assertNull(TextUtils.insertAfter(null, "a", "b"));
        assertEquals("", TextUtils.insertAfter("", "a", "b"));
        assertEquals("abc", TextUtils.insertAfter("abc", "", "b"));
        assertEquals("abc", TextUtils.insertAfter("abc", "a", ""));
        assertEquals("abbc", TextUtils.insertAfter("abc", "a", "b"));
        assertEquals("abbc", TextUtils.insertAfter("abc", "ab", "b"));
        assertEquals("nexial.browser.MY_PROFILE.command",
                     TextUtils.insertAfter("nexial.browser.command", "nexial.browser", ".MY_PROFILE"));
    }

    @Test
    public void insertBefore() {
        assertNull(TextUtils.insertBefore(null, "a", "b"));
        assertEquals("", TextUtils.insertBefore("", "a", "b"));
        assertEquals("a", TextUtils.insertBefore("a", "", "b"));
        assertEquals("a", TextUtils.insertBefore("a", "c", ""));
        assertEquals("xenanadu", TextUtils.insertBefore("xenadu", "na", "na"));
        assertEquals("nabanana", TextUtils.insertBefore("banana", "ba", "na"));
        assertEquals("chrome.headless", TextUtils.insertBefore("chromeheadless", "headless", "."));
    }

    @Test
    public void polyMatcher_has_length() {
        assertTrue(TextUtils.polyMatch("12345", "LENGTH:5"));
        assertTrue(TextUtils.polyMatch("12345", "LENGTH:>=5"));
        assertTrue(TextUtils.polyMatch("12345", "LENGTH:<=5"));
        assertTrue(TextUtils.polyMatch("12345", "LENGTH:<=6"));
        assertTrue(TextUtils.polyMatch("12345", "LENGTH: >= 4"));
        assertTrue(TextUtils.polyMatch("12345", "LENGTH: != 17"));
        assertTrue(TextUtils.polyMatch("12345", "LENGTH:   =   5   "));
        assertTrue(TextUtils.polyMatch("", "LENGTH:0"));
        assertTrue(TextUtils.polyMatch("", "LENGTH: 0 "));
        assertTrue(TextUtils.polyMatch("", "LENGTH: 0 "));
        assertTrue(TextUtils.polyMatch(" ", "LENGTH: <2 "));
        assertTrue(TextUtils.polyMatch("\t", "LENGTH: <2 "));
        assertTrue(TextUtils.polyMatch("\r\n", "LENGTH: 2 "));
    }

    @Test
    public void polyMatcher_blank_empty() {
        assertTrue(TextUtils.polyMatch("", "EMPTY:yes"));
        assertTrue(TextUtils.polyMatch("", "EMPTY:y"));
        assertTrue(TextUtils.polyMatch("", "EMPTY:true"));
        assertTrue(TextUtils.polyMatch("", "EMPTY:TRUE"));
        assertTrue(TextUtils.polyMatch(" ", "EMPTY:no"));
        assertTrue(TextUtils.polyMatch(" ", "EMPTY:false"));

        assertTrue(TextUtils.polyMatch(" ", "BLANK:true"));
        assertTrue(TextUtils.polyMatch(" ", "BLANK:YES"));
        assertTrue(TextUtils.polyMatch("", "BLANK:Y"));
    }

    @Test
    public void polyMatcher_numeric_compare() {
        assertTrue(TextUtils.polyMatch("100", "NUMERIC:100"));
        assertTrue(TextUtils.polyMatch("100", "NUMERIC:100.0"));
        assertTrue(TextUtils.polyMatch("100", "NUMERIC:100.0000000"));
        assertTrue(TextUtils.polyMatch("100", "NUMERIC:+100.0000000"));
        assertTrue(TextUtils.polyMatch("100.0000", "NUMERIC:+100"));
        assertTrue(TextUtils.polyMatch("  -100.0001    ", "NUMERIC:  -100.000100000 "));

        assertTrue(TextUtils.polyMatch("  -131", "NUMERIC: < 130"));
        assertTrue(TextUtils.polyMatch("  -131", "NUMERIC: <= 130"));
        assertTrue(TextUtils.polyMatch("  -131.0000001", "NUMERIC: < 131"));

        try {
            assertTrue(TextUtils.polyMatch("15.0d", "NUMERIC:\t15 "));
            fail("Expected NumberFormatException not thrown");
        } catch (Exception e) {
            // expected
        }

        try {
            assertTrue(TextUtils.polyMatch("", "NUMERIC:\t15 "));
            fail("Expected NumberFormatException not thrown");
        } catch (Exception e) {
            // expected
        }

        try {
            assertTrue(TextUtils.polyMatch("15 .01", "NUMERIC:\t15 "));
            fail("Expected NumberFormatException not thrown");
        } catch (Exception e) {
            // expected
        }

    }

    @Test
    public void substringBeforeWhitespace() {
        assertNull(TextUtils.substringBeforeWhitespace(null));
        assertEquals("", TextUtils.substringBeforeWhitespace(""));
        assertEquals("", TextUtils.substringBeforeWhitespace(" "));
        assertEquals("", TextUtils.substringBeforeWhitespace(" \t\n  \t"));
        assertEquals("Johnny", TextUtils.substringBeforeWhitespace("Johnny was a rebel"));
        assertEquals("Johnny", TextUtils.substringBeforeWhitespace("Johnny\twas a rebel"));
        assertEquals("Johnny", TextUtils.substringBeforeWhitespace("Johnny\nwas a rebel"));
        assertEquals("Johnny", TextUtils.substringBeforeWhitespace("Johnny\n\rwas a rebel"));
        assertEquals("", TextUtils.substringBeforeWhitespace("Johnny"));
    }

    @Test
    public void substringAfterWhitespace() {
        assertNull(TextUtils.substringAfterWhitespace(null));
        assertEquals("", TextUtils.substringAfterWhitespace(""));
        assertEquals("", TextUtils.substringAfterWhitespace(" "));
        assertEquals("\t\n  \t", TextUtils.substringAfterWhitespace(" \t\n  \t"));
        assertEquals("was a rebel", TextUtils.substringAfterWhitespace("Johnny was a rebel"));
        assertEquals("was a rebel", TextUtils.substringAfterWhitespace("Johnny\twas a rebel"));
        assertEquals("was a rebel", TextUtils.substringAfterWhitespace("Johnny\nwas a rebel"));
        assertEquals("\rwas a rebel", TextUtils.substringAfterWhitespace("Johnny\n\rwas a rebel"));
        assertEquals("", TextUtils.substringAfterWhitespace("Johnny"));
        assertEquals("", TextUtils.substringAfterWhitespace("Johnny "));
    }
}