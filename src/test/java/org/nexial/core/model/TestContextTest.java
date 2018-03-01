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

import java.util.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.plugins.ws.Response;

import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.model.ExecutionContext.KEY_COMPLEX;

public class TestContextTest {

    public static class Dummy {
        private String name;
        private int age;
        private boolean bigShot;
        private long[] numbers = new long[]{123456789L, 987654321L};

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public int getAge() { return age; }

        public void setAge(int age) { this.age = age; }

        public boolean isBigShot() { return bigShot; }

        public void setBigShot(boolean bigShot) { this.bigShot = bigShot; }

        public long[] getNumbers() { return numbers; }

        public void setNumbers(long[] numbers) { this.numbers = numbers; }
    }

    public class Person {
        private String name;
        private int age;
        private String favorite;
        private List<Dummy> dummy;

        Person(String name, int age, String favorite) {
            this.name = name;
            this.age = age;
            this.favorite = favorite;
        }

        public String getName() { return name; }

        public int getAge() { return age; }

        public String getFavorite() { return favorite; }

        public List<Dummy> getDummy() { return dummy; }

        public void setDummy(List<Dummy> dummy) { this.dummy = dummy; }
    }

    @Before
    public void init() {
        // System.setProperty("selenium.host", "localhost");
        // System.setProperty("testsuite.name", "ManualTest");
        // System.setProperty("testsuite.startTs", new SimpleDateFormat("yyyyMMddHHmmssS").format(new Date()));
    }

    @Test
    public void testReplaceTokens() {
        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");
        context.setData("a", "Hello");
        context.setData("b", "World");
        context.setData("c", "Johnny boy");
        context.setData("d", 42);
        context.setData("e", "my name is ${c}");

        String fixture = "Well, ${a} ${b} there, ${c}";
        Assert.assertEquals(context.replaceTokens(fixture), "Well, Hello World there, Johnny boy");

        fixture = "And I suppose ${e}?";
        Assert.assertEquals(context.replaceTokens(fixture), "And I suppose my name is Johnny boy?");

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void testReplaceArrayTokens() {
        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");
        context.setData(TEXT_DELIM, "|");
        context.setData("a", new String[]{"Hello", "World", "Johnny boy"});

        String fixture = "Well, ${a}";
        Assert.assertEquals(context.replaceTokens(fixture), "Well, Hello|World|Johnny boy");

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }

    }

    @Test
    public void testReplaceStringListTokens() {
        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");
        context.setData(TEXT_DELIM, "|");
        context.setData("a", Arrays.asList("Hello", "World", "Johnny boy"));

        String fixture = "Well, ${a}";
        Assert.assertEquals(context.replaceTokens(fixture), "Well, Hello|World|Johnny boy");

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void testTokenFinder() {
        String fixture = "My friend is ${data}.name.  He is ${data}.age years old.  While living " +
                         "on ${data}.address, he would say '${data}.quote'.";

        Set<String> tokens = ExecutionContext.findTokens(fixture);
        Assert.assertNotNull(tokens);
        Assert.assertEquals(tokens.size(), 1);
        Iterator<String> iterator = tokens.iterator();
        Assert.assertEquals(iterator.next(), "data");

        fixture = "a ${b}, c ${c}, d ${d} ${a} ${b}";
        tokens = ExecutionContext.findTokens(fixture);
        Assert.assertNotNull(tokens);
        Assert.assertEquals(tokens.size(), 4);
        Assert.assertTrue(tokens.contains("a"));
        Assert.assertTrue(tokens.contains("b"));
        Assert.assertTrue(tokens.contains("c"));
        Assert.assertTrue(tokens.contains("d"));
    }

    @Test
    public void testReplaceTokens2() {
        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");

        List<String> items = new ArrayList<>();
        items.add("one");
        items.add("two");
        items.add("three");
        context.setData("items", items);

        context.setData("array", new String[]{"apple", "orange", "banana"});

        Map<String, String> data = new HashMap<>();
        data.put("name", "Johnny Cash");
        data.put("age", "52");
        data.put("address", "444 Silverlake Drive, Kansas City, KS");
        data.put("quote", "Don't die young");
        context.setData("data", data);

        String fixture = "And a ${items}[0], and a ${items}[1], and a ${items}[0], ${items}[1], ${items}[2]!";
        Assert.assertEquals("And a one, and a two, and a one, two, three!", context.replaceTokens(fixture));

        fixture = "I like ${array}[0], ${array}[1], and ${array}[2].  But not '${array}[4]'.";
        Assert.assertEquals("I like apple, orange, and banana.  But not ''.", context.replaceTokens(fixture));

        fixture = "My friend is ${data}.name.  He is ${data}.age years old.  While living " +
                  "on ${data}.address, he would say '${data}.quote'.";
        Assert.assertEquals("My friend is Johnny Cash.  He is 52 years old.  While " +
                            "living on 444 Silverlake Drive, Kansas City, KS, he " +
                            "would say 'Don't die young'.", context.replaceTokens(fixture));

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void testReplaceTokens3() {
        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");

        List<Map<String, String>> spreadsheet = new ArrayList<>();
        Map<String, String> row1 = new HashMap<>();
        row1.put("name", "Johnny Cash");
        row1.put("age", "52");
        row1.put("address", "444 Silverlake Drive, Kansas City, KS");
        row1.put("quote", "Don't die young");
        spreadsheet.add(row1);

        Map<String, String> row2 = new HashMap<>();
        row2.put("name", "Jimmy Hendrix");
        row2.put("age", "94");
        row2.put("address", "000 Smokey Pipe Hills");
        row2.put("quote", "Are you stoned?");
        spreadsheet.add(row2);

        Map<String, String> row3 = new HashMap<>();
        row3.put("name", "Jimmy John Jones");
        row3.put("age", "2");
        row3.put("address", "99 Storybook Lane");
        row3.put("quote", "Waaaa waaa!");
        spreadsheet.add(row3);

        context.setData("spreadsheet", spreadsheet);

        String fixture = "My friend is ${spreadsheet}[0].name.  He is ${spreadsheet}[0].age years old.  While living " +
                         "on ${spreadsheet}[0].address, he would say '${spreadsheet}[0].quote'.";
        Assert.assertEquals("My friend is Johnny Cash.  He is 52 years old.  While " +
                            "living on 444 Silverlake Drive, Kansas City, KS, he " +
                            "would say 'Don't die young'.", context.replaceTokens(fixture));

        fixture = "My friend is ${spreadsheet}[1].name.  He is ${spreadsheet}[1].age years old.  While living " +
                  "on ${spreadsheet}[1].address, he would say '${spreadsheet}[1].quote'.";
        Assert.assertEquals("My friend is Jimmy Hendrix.  He is 94 years old.  While " +
                            "living on 000 Smokey Pipe Hills, he " +
                            "would say 'Are you stoned?'.", context.replaceTokens(fixture));

        context.setData("index", 2);
        fixture = "My friend is ${spreadsheet}[${index}].name.  He is ${spreadsheet}[${index}].age years old.  " +
                  "While living on ${spreadsheet}[${index}].address, he would say '${spreadsheet}[${index}].quote'.";
        Assert.assertEquals("My friend is Jimmy John Jones.  He is 2 years old.  " +
                            "While living on 99 Storybook Lane, he " +
                            "would say 'Waaaa waaa!'.", context.replaceTokens(fixture));

        // todo compare these..
        //fixture = "My friend is ${spreadsheet}";
        //fixture = "My friend is ${spreadsheet}[0]";
        //fixture = "My friend is ${spreadsheet}.name";

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    /**
     * real example using WsResponse
     */
    @Test
    public void testReplaceTokens4() {
        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");

        List<Map<String, String>> spreadsheet = new ArrayList<>();
        Map<String, String> row1 = new HashMap<>();
        row1.put("name", "Johnny Cash");
        row1.put("age", "52");
        row1.put("address", "444 Silverlake Drive, Kansas City, KS");
        row1.put("quote", "Don't die young");
        spreadsheet.add(row1);

        Map<String, String> row2 = new HashMap<>();
        row2.put("name", "Jimmy Hendrix");
        row2.put("age", "94");
        row2.put("address", "000 Smokey Pipe Hills");
        row2.put("quote", "Are you stoned?");
        spreadsheet.add(row2);

        Map<String, String> row3 = new HashMap<>();
        row3.put("name", "Jimmy John Jones");
        row3.put("age", "2");
        row3.put("address", "99 Storybook Lane");
        row3.put("quote", "Waaaa waaa!");
        spreadsheet.add(row3);

        context.setData("spreadsheet", spreadsheet);

        Response response = new Response();
        response.setContentLength(2052L);
        response.setElapsedTime(59302L);

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("User-Agent",
                      "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)");
        headerMap.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headerMap.put("Accept-Language", "en-us,en;q=0.5");
        headerMap.put("Accept-Encoding", "gzip,deflate");
        headerMap.put("Accept- Charset ", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        headerMap.put("Keep-Alive", "300");
        headerMap.put("Connection", "keep-alive");
        headerMap.put("Cookie", "PHPSESSID=r2t5uvjq435r4q7ib3vtdjq120");
        headerMap.put("Pragma", "no-cache");
        headerMap.put("Cache-Control", "no-cache");
        headerMap.put("Dummy Data", "Would you like fries with that?");
        response.setHeaders(headerMap);

        response.setReturnCode(200);
        response.setStatusText("OK");
        response.setRawBody("This is a test".getBytes());

        context.setData("response", response);

        Assert.assertEquals("2052", context.replaceTokens("${response}.contentLength"));
        Assert.assertEquals("2052", context.replaceTokens("${response}.[contentLength]"));
        Assert.assertEquals("2052 is the content length",
                            context.replaceTokens("${response}.[contentLength] is the content length"));
        Assert.assertEquals("200", context.replaceTokens("${response}.returnCode"));
        Assert.assertEquals("I think 200 is the return code",
                            context.replaceTokens("I think ${response}.returnCode is the return code"));
        Assert.assertEquals("I think the return code is 200.",
                            context.replaceTokens("I think the return code is ${response}.returnCode."));
        Assert.assertEquals("OK", context.replaceTokens("${response}.statusText"));
        Assert.assertEquals("no-cache", context.replaceTokens("${response}.headers.Pragma"));
        Assert.assertEquals("Would you like fries with that?",
                            context.replaceTokens("${response}.headers.[Dummy Data]"));

        // ${response}.headers.Pragma
        // ${response}.headers[Pragma]
        // ${response}.headers\.Pragma

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    /**
     * map of list of objects
     */
    @Test
    public void testReplaceTokens5() {
        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");

        Map<String, List<Person>> fixture = new HashMap<>();

        fixture.put("Cartoon", Arrays.asList(new Person("Mickey", 72, "Cheese"), new Person("Popeye", 92, "Spinash")));
        fixture.put("Pop Stars", Arrays.asList(new Person("Sting", 62, "Jazz"), new Person("Bono", 64, "Punk Rock")));

        context.setData("facts", fixture);
        Assert.assertEquals("You know that Mickey loves Cheese even at 72?!",
                            context.replaceTokens("You know that ${facts}.Cartoon[0].name loves " +
                                                  "${facts}.Cartoon[0].favorite even at ${facts}.Cartoon[0].age?!"));
        Assert.assertEquals("So what if Popeye loves Spinash!? He's 92!",
                            context.replaceTokens("So what if ${facts}.Cartoon[1].name loves " +
                                                  "${facts}.Cartoon[1].favorite!? He's ${facts}.Cartoon[1].age!"));

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    /**
     * object of objects of list
     */
    @Test
    public void testReplaceTokens6() {
        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");

        Person fixture = new Person("Me and Myself", 29, "Watermelon");
        Dummy dummy1 = new Dummy();
        dummy1.setAge(15);
        dummy1.setName("Ameria");
        dummy1.setBigShot(false);
        dummy1.setNumbers(new long[]{54321L, 67890L});

        Dummy dummy2 = new Dummy();
        dummy2.setAge(39);
        dummy2.setName("Korey Glostiana");
        dummy2.setBigShot(true);
        dummy2.setNumbers(new long[]{293848756L, 1029384576L});

        fixture.setDummy(Arrays.asList(dummy1, dummy2));

        context.setData("me", fixture);

        Assert.assertEquals(
            "Me and Myself, though only 29, knows of Ameria who has 54321 and 67890 and of Korey Glostiana who is 39",
            context.replaceTokens("${me}.name, though only ${me}.age, knows of ${me}.dummy[0].name who has " +
                                  "${me}.dummy[0].numbers[0] and ${me}.dummy[0].numbers[1] and of " +
                                  "${me}.dummy[1].name who is ${me}.dummy[1].age")
                           );

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    @Test
    public void testReplaceListTokens() {
        // test 1 pattern ${x}.y
        List<Map<String, String>> stateList = new ArrayList<>();
        stateList.add(newSinglePairMap("state", "AL"));
        stateList.add(newSinglePairMap("state", "AK"));
        stateList.add(newSinglePairMap("state", "AR"));
        stateList.add(newSinglePairMap("state", "CA"));
        stateList.add(newSinglePairMap("state", "CO"));
        stateList.add(newSinglePairMap("state", "CT"));

        Map<String, Object> listValues = new HashMap<>();
        listValues.put("stateList", stateList);

        Map<String, Object> complexValues = new HashMap<>();

        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");

        List<Map<String, String>> spreadsheet = new ArrayList<>();
        Map<String, String> row1 = new HashMap<>();
        row1.put("name", "Johnny Cash");
        row1.put("age", "52");
        row1.put("address", "444 Silverlake Drive, Kansas City, KS");
        row1.put("quote", "Don't die young");
        spreadsheet.add(row1);

        Map<String, String> row2 = new HashMap<>();
        row2.put("name", "Jimmy Hendrix");
        row2.put("age", "94");
        row2.put("address", "000 Smokey Pipe Hills");
        row2.put("quote", "Are you stoned?");
        spreadsheet.add(row2);

        Map<String, String> row3 = new HashMap<>();
        row3.put("name", "Jimmy John Jones");
        row3.put("age", "2");
        row3.put("address", "99 Storybook Lane");
        row3.put("quote", "Waaaa waaa!");
        spreadsheet.add(row3);

        context.setData("spreadsheet", spreadsheet);

        String testSubject = context.replaceCollectionTokens("${stateList}.state", listValues, complexValues);
        System.out.println("testSubject = " + testSubject);
        Assert.assertEquals("AL,AK,AR,CA,CO,CT", testSubject);

        // test 2 pattern ${x}[i].y - observe complexValues
        testSubject = context.replaceCollectionTokens("${stateList}[0].state", listValues, complexValues);
        System.out.println("testSubject = " + testSubject);
        Assert.assertEquals("${__lAIxEn__stateList[0]}.state", testSubject);
        Assert.assertTrue(complexValues.size() == 1);

        testSubject = context.replaceCollectionTokens("${stateList}[5].state", listValues, complexValues);
        System.out.println("testSubject = " + testSubject);
        Assert.assertEquals("${__lAIxEn__stateList[5]}.state", testSubject);
        Assert.assertTrue(complexValues.size() == 2);

        testSubject = context.replaceComplexTokens("${__lAIxEn__stateList[0]}.state", complexValues);
        Assert.assertEquals("AL", testSubject);

        testSubject = context.replaceComplexTokens("${__lAIxEn__stateList[5]}.state", complexValues);
        Assert.assertEquals("CT", testSubject);

        ((MockExecutionContext) context).cleanProject();
    }

    @Test
    public void testReplaceArrayTokens2() {
        Dummy[] dummies = new Dummy[5];
        dummies[0] = new Dummy();
        dummies[0].setName("Grumpy");
        dummies[0].setAge(91);
        dummies[0].setBigShot(false);

        dummies[1] = new Dummy();
        dummies[1].setName("Clumsy");
        dummies[1].setAge(44);
        dummies[1].setBigShot(false);

        dummies[2] = new Dummy();
        dummies[2].setName("Sneezy");
        dummies[2].setAge(53);
        dummies[2].setBigShot(false);

        dummies[3] = new Dummy();
        dummies[3].setName("Sleepy");
        dummies[3].setAge(39);
        dummies[3].setBigShot(false);

        dummies[4] = new Dummy();
        dummies[4].setName("Snow White");
        dummies[4].setAge(103);
        dummies[4].setBigShot(true);

        Map<String, Object> listValues = new HashMap<>();
        listValues.put("dummies", dummies);
        Map<String, Object> complexValues = new HashMap<>();

        ExecutionContext context = new MockExecutionContext();
        context.setData("dummy", "that's me");

        List<Map<String, String>> spreadsheet = new ArrayList<>();
        Map<String, String> row1 = new HashMap<>();
        row1.put("name", "Johnny Cash");
        row1.put("age", "52");
        row1.put("address", "444 Silverlake Drive, Kansas City, KS");
        row1.put("quote", "Don't die young");
        spreadsheet.add(row1);

        Map<String, String> row2 = new HashMap<>();
        row2.put("name", "Jimmy Hendrix");
        row2.put("age", "94");
        row2.put("address", "000 Smokey Pipe Hills");
        row2.put("quote", "Are you stoned?");
        spreadsheet.add(row2);

        Map<String, String> row3 = new HashMap<>();
        row3.put("name", "Jimmy John Jones");
        row3.put("age", "2");
        row3.put("address", "99 Storybook Lane");
        row3.put("quote", "Waaaa waaa!");
        spreadsheet.add(row3);

        context.setData("spreadsheet", spreadsheet);

        // test 1 pattern ${x}.y
        String testSubject = context.replaceCollectionTokens("${dummies}.name", listValues, complexValues);
        System.out.println("testSubject = " + testSubject);
        Assert.assertEquals("Grumpy,Clumsy,Sneezy,Sleepy,Snow White", testSubject);

        // test 2 pattern ${x}[i].y - observe complexValues
        testSubject = context.replaceCollectionTokens("${dummies}[0].age", listValues, complexValues);
        System.out.println("testSubject = " + testSubject);
        Assert.assertEquals("${" + KEY_COMPLEX + "dummies[0]}.age", testSubject);
        Assert.assertTrue(complexValues.size() == 1);

        testSubject = context.replaceCollectionTokens("${dummies}[4].state", listValues, complexValues);
        System.out.println("testSubject = " + testSubject);
        Assert.assertEquals("${" + KEY_COMPLEX + "dummies[4]}.state", testSubject);
        Assert.assertTrue(complexValues.size() == 2);

        testSubject = context.replaceComplexTokens("${" + KEY_COMPLEX + "dummies[0]}.age", complexValues);
        Assert.assertEquals("91", testSubject);

        // test for when property (state) is not found
        testSubject = context.replaceComplexTokens("${" + KEY_COMPLEX + "dummies[4]}.state", complexValues);
        System.out.println("testSubject = " + testSubject);
        Assert.assertTrue(RegexUtils.isExact(testSubject,
                                             "org\\.nexial\\.core\\.model\\.TestContextTest\\$Dummy.+\\.state"));

        complexValues.put("dummies", dummies);
        testSubject = context.replaceComplexTokens("${dummies}.bigShot", complexValues);
        System.out.println("testSubject = " + testSubject);
        Assert.assertEquals("false,false,false,false,true", testSubject);

        if (context != null) { ((MockExecutionContext) context).cleanProject(); }
    }

    private Map<String, String> newSinglePairMap(String name, String value) {
        Map<String, String> stateAL = new HashMap<>();
        stateAL.put(name, value);
        return stateAL;
    }
}