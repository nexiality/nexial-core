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

package org.nexial.core.utils;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.nexial.core.utils.JsonUtils.DEFAULT_ERROR_CODE;

public class JsonUtilsTest {
    public static class Company {
        String name;
        String address;
        String industry;
        boolean publicallyTraded;

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String getAddress() { return address; }

        public void setAddress(String address) { this.address = address; }

        public String getIndustry() { return industry; }

        public void setIndustry(String industry) { this.industry = industry; }

        public boolean isPublicallyTraded() { return publicallyTraded; }

        public void setPublicallyTraded(boolean publicallyTraded) { this.publicallyTraded = publicallyTraded; }
    }

    public static class Person {
        String name;
        int age;
        Company company;
        double averageScore;
        Person[] friends;
        Date birthDate;

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public int getAge() { return age; }

        public void setAge(int age) { this.age = age; }

        public Company getCompany() { return company; }

        public void setCompany(Company company) { this.company = company; }

        public double getAverageScore() { return averageScore; }

        public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

        public Person[] getFriends() { return friends; }

        public void setFriends(Person[] friends) { this.friends = friends; }

        public Date getBirthDate() { return birthDate; }

        public void setBirthDate(Date birthDate) { this.birthDate = birthDate; }
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testToJSONObject() {
        String fixture = "{" +
                         "\"userId\":\"2773appuser1_ap\"," +
                         "\"requestTimestamp\":1339176623443," +
                         "\"jurisId\":\"\\\\[\\\\]\"," +
                         "\"taxTypes\":[\"SDI\",\"SUI\"]," +
                         "\"stateCode\":\"01\"" +
                         "}";

        JSONObject json = JsonUtils.toJSONObject(fixture);
        Assert.assertNotNull(json);
        Assert.assertEquals(json.opt("userId"), "2773appuser1_ap");
        Assert.assertNull(json.optJSONArray("jurisId"));
        JSONArray jsonArray = json.optJSONArray("taxTypes");
        Assert.assertNotNull(jsonArray);
        Assert.assertEquals(jsonArray.length(), 2);
        Assert.assertEquals(jsonArray.get(0), "SDI");
        Assert.assertEquals(jsonArray.get(1), "SUI");
        Assert.assertEquals(json.opt("stateCode"), "01");
        Assert.assertEquals(json.optString("requestTimestamp"), "1339176623443");
        System.out.println("json = " + json);
    }

    @Test
    public void testFilterToList() {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(0, 5);
        jsonArray.put(1, 6.1);
        jsonArray.put(2, false);
        jsonArray.put(3, "Hello");
        jsonArray.put(4, new JSONObject("{\"a\":\"b\"}"));
        jsonArray.put(5, "Mom");
        jsonArray.put(6, new JSONObject("{\"c\": [\"d\", \"e\"]}"));

        List<Integer> integers = JsonUtils.filterToList(jsonArray, Integer.class);
        Assert.assertNotNull(integers);
        Assert.assertEquals(integers.size(), 1);
        Assert.assertEquals(5, (int) integers.get(0));

        List<Double> doubles = JsonUtils.filterToList(jsonArray, Double.class);
        Assert.assertNotNull(doubles);
        Assert.assertEquals(doubles.size(), 1);
        Assert.assertEquals(6.1, doubles.get(0), 0.0);

        List<Number> numbers = JsonUtils.filterToList(jsonArray, Number.class);
        Assert.assertNotNull(numbers);
        Assert.assertEquals(numbers.size(), 2);
        Assert.assertEquals(5, numbers.get(0).intValue());
        Assert.assertEquals(6.1, numbers.get(1).doubleValue(), 0.0);

        List<Boolean> booleans = JsonUtils.filterToList(jsonArray, Boolean.class);
        Assert.assertNotNull(booleans);
        Assert.assertEquals(booleans.size(), 1);
        Assert.assertTrue(!booleans.get(0));

        List<String> strings = JsonUtils.filterToList(jsonArray, String.class);
        Assert.assertNotNull(strings);
        Assert.assertEquals(strings.size(), 2);
        Assert.assertEquals("Hello", strings.get(0));
        Assert.assertEquals("Mom", strings.get(1));

        List<JSONObject> jsonObjects = JsonUtils.filterToList(jsonArray, JSONObject.class);
        Assert.assertNotNull(jsonObjects);
        Assert.assertEquals(jsonObjects.size(), 2);
        Assert.assertEquals("b", jsonObjects.get(0).optString("a"));
        Assert.assertEquals(2, jsonObjects.get(1).optJSONArray("c").length());
        Assert.assertEquals("[\"d\",\"e\"]", jsonObjects.get(1).optJSONArray("c").toString());

    }

    @Test
    public void testToJSONObjectMap() {
        Map<Object, Object> map = new HashMap<>();
        map.put("text", "This is just test");
        map.put("number", 154);
        map.put("array", new Object[]{Boolean.TRUE, 15.324, "another test", (byte) 5});

        JSONObject json = JsonUtils.toJSONObject(map);
        Assert.assertNotNull(json);
        Assert.assertEquals(json.length(), 3);
        Assert.assertEquals("This is just test", json.get("text"));
        Assert.assertEquals("" + 154, json.get("number"));

        Assert.assertTrue(json.has("array"));
        Object obj = json.get("array");
        Assert.assertFalse(obj instanceof JSONArray);
        System.out.println("obj = " + obj);
    }

    @Test
    public void toDefaultSuccessResponse() {
        JSONObject json = JsonUtils.toDefaultSuccessResponse();
        Assert.assertNotNull(json);
        Assert.assertTrue(json.has("responseTimestamp"));
        Assert.assertTrue(json.has("errorCode") && StringUtils.isEmpty(json.getString("errorCode")));
        Assert.assertTrue(json.has("errorMessage") && StringUtils.isEmpty(json.getString("errorMessage")));
    }

    @Test
    public void addToJson() {
        Assert.assertNull(JsonUtils.addToJson(null, null, null, false));
        Assert.assertNull(JsonUtils.addToJson(null, null, "b", false));
        Assert.assertNull(JsonUtils.addToJson(null, "a", "b", false));

        String json = JsonUtils.addToJson("{}", "a", "b", false);
        Assert.assertEquals("{\"a\":\"b\"}", json);

        json = JsonUtils.addToJson(json, "a", "c", false);
        Assert.assertEquals("{\"a\":\"b\"}", json);

        json = JsonUtils.addToJson(json, "a", "c", true);
        Assert.assertEquals("{\"a\":\"c\"}", json);

        json = JsonUtils.addToJson("{\"a\":[\"1\",\"2\"]}", "a", 12345, true);
        Assert.assertEquals("{\"a\":12345}", json);

        json = JsonUtils.addToJson("{\"a\":[\"1\",\"2\"]}", "a", "c", true);
        Assert.assertEquals("{\"a\":\"c\"}", json);

        json = JsonUtils.addToJson("{\"a\":[\"1\",\"2\"]}", "a", Arrays.asList("3", "4", "5"), true);
        Assert.assertEquals("{\"a\":[\"3\",\"4\",\"5\"]}", json);

        json = JsonUtils.addToJson("{\"a\":[\"1\",\"2\"]}", "a", new String[]{"3", "4", "5"}, true);
        Assert.assertEquals("{\"a\":[\"3\",\"4\",\"5\"]}", json);

        Map<String, String> map = new HashMap<>();
        map.put("name", "Joe");
        map.put("age", "14");
        map.put("location", "Los Angeles");
        json = JsonUtils.addToJson("{\"a\":[\"1\",\"2\"]}", "a", map, true);
        Assert.assertEquals("{\"a\":{\"name\":\"Joe\",\"location\":\"Los Angeles\",\"age\":\"14\"}}", json);
    }

    @Test
    public void serialize() throws Exception {
        Assert.assertNull(JsonUtils.serialize(null));

        Object obj = new Object() {
            private String name = "James";
            private int age = 94;
            private String favoriteColor = "Brown";
            private String profession = "Musician";

            public String getName() { return name; }

            public void setName(String name) { this.name = name; }

            public int getAge() { return age; }

            public void setAge(int age) { this.age = age; }

            public String getFavoriteColor() { return favoriteColor; }

            public void setFavoriteColor(String favoriteColor) { this.favoriteColor = favoriteColor; }

            public String getProfession() { return profession; }

            public void setProfession(String profession) { this.profession = profession; }
        };

        JSONObject json = JsonUtils.serialize(obj);
        System.out.println("json = " + json);
        Assert.assertNotNull(json);
        Assert.assertEquals("James", json.getString("name"));
        Assert.assertEquals("Brown", json.getString("favoriteColor"));
        Assert.assertEquals(94, json.getInt("age"));
        Assert.assertEquals("Musician", json.getString("profession"));
    }

    @Test
    public void newExceptionJSONObject() {
        Assert.assertNull(JsonUtils.newExceptionJSONObject(null));

        Exception e1 = new Exception("This is a test.  Do not be alarmed");
        JSONObject json = JsonUtils.newExceptionJSONObject(e1);
        Assert.assertNotNull(json);
        Assert.assertEquals(DEFAULT_ERROR_CODE, json.getString("errorCode"));
        Assert.assertEquals("This is a test.  Do not be alarmed", json.getString("errorMessage"));
        Assert.assertTrue(json.has("responseTimestamp"));
    }

    @Test
    public void serializeQuotes() {
        Person fixture = new Person();
        fixture.name = "Dan \"The Man\" Watson";
        fixture.age = 17;
        fixture.averageScore = 98.292;
        fixture.company = new Company();
        fixture.company.name = "Shake 'n Bake";

        Gson gson = new GsonBuilder().disableHtmlEscaping()
                                     // .setPrettyPrinting()
                                     .create();

        String json = gson.toJson(fixture, Person.class);
        System.out.println("json = " + json);

        String serialized = "{" +
                            " \"name\":                 'Dan \"The Man\" Watson'," +
                            " \"age\":                  17," +
                            " \"company\": {" +
                            "  \"name\":                'Shake \\'n Bake'," +
                            "  \"publicallyTraded\":    false" +
                            "  }," +
                            " \"averageScore\":         98.292 }";
        Person subject = gson.fromJson(serialized, Person.class);
        Assert.assertEquals(subject.name, fixture.name);
        Assert.assertEquals(subject.age, fixture.age);
        Assert.assertEquals(subject.averageScore, fixture.averageScore, 0);
        Assert.assertEquals(subject.company.name, fixture.company.name);

    }
}
