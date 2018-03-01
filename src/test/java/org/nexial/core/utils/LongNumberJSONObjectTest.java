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

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class LongNumberJSONObjectTest {

    @Test
    public void testToString() {
        String a = "{"
                   + "\"tracerNo\":\"3425843V1\","
                   + "\"userId\":\"kumaraa\","
                   + "\"tracerInfo\":{"
                   + "\"name\": {"
                   + "\"data\":\"arup\", \"num\":987654321.09 "
                   + "}"
                   + "}, "
                   + "\"number\": 123456789.09, "
                   + "\"array\": [\"abc\", \"efg\"]"
                   + "}";

        JSONObject json = new JSONObject(a);
        System.out.println("json =  " + json);
        Assert.assertTrue(!json.toString().contains("E"));
        Assert.assertEquals(987654321.09, json.optJSONObject("tracerInfo").optJSONObject("name").getDouble("num"), 0);
        Assert.assertEquals(123456789.09, json.getDouble("number"), 0);

        String b = "{"
                   + "\"tracerNo\":\"3425843V1\","
                   + "\"userId\":\"kumaraa\"," +
                   "\"tracerReindexInfo\": {"
                   + "\"raf\":\" \","
                   + "\"branch\":\"ST\","
                   + "\"cpNumber\":\"\","
                   + "\"taxType\":\"3\","
                   + "\"company\":\"NH01\","
                   + "\"filingType\":\"4\","
                   + "\"noticeNo\" :\"1\","
                   + "\"id\":\"135123390\","
                   + "\"amount\":-854545225.25,"
                   + "\"localCode\":\"0000\","
                   + "\"yrQtrs\":[ \"2008/1\" ],"
                   + "\"stateCode\":\"00\","
                   + "\"noticeDate\":\"10/28/2009\""
                   + "}"
                   + "}";

        json = new JSONObject(b);
        System.out.println("json =  " + json);
        Assert.assertTrue(!json.toString().contains("E"));
        Assert.assertEquals(-854545225.25, json.optJSONObject("tracerReindexInfo").optDouble("amount"), 0);

        String c = "{\"timestamp\": 12345678901234567890, \"magicNum\": -1234567890.0987654321}";
        json = new JSONObject(c);
        System.out.println("json =  " + json);
        Assert.assertTrue(!json.toString().contains("E"));
        Assert.assertEquals("12345678901234567890", json.optString("timestamp"));
        Assert.assertNotSame("-1234567890.0987654321", json.optString("magicNum"));
        // 7 DECIMAL PLACES WILL WORK...
        Assert.assertEquals("-1234567890.0987654321", json.optString("magicNum"));
        Assert.assertEquals(-1234567890.0987654321, json.optDouble("magicNum"), 0);

    }

}
