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

package org.nexial.core.utils;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.AppException;
import org.nexial.commons.BusinessException;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.plugins.json.JsonCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Configuration.Defaults;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

/**
 * @author Mike Liu
 */
public final class JsonUtils {
    public static final String DEFAULT_ERROR_CODE = "0999";
    public static final String SOCKET_ERROR_CODE = "0081";
    //public static final String SAVE_RESPONSE_ERROR_CODE = "0113";
    //public static final String BAD_JSON_ERROR_CODE = "0197";
    //public static final String BLANK_FILE_ERROR_CODE = "0119";

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

    private JsonUtils() { }

    /**
     * convert <code >jsonString</code> into a {@link JSONObject} object.  This method will convert exception
     * condition into a {@link JSONObject} using {@link #newExceptionJSONObject(Exception)}.
     */
    public static JSONObject toJSONObject(String jsonString) {
        try {
            return new JSONObject(jsonString);
        } catch (JSONException e) {
            LOGGER.error("Error converting string to JSON object: " + e.getMessage(), e);
            LOGGER.error("JSON string in error is as follows:" + NL + jsonString);
            return newExceptionJSONObject(e);
        }
    }

    public static JSONArray toJSONArray(String jsonString) {
        try {
            return new JSONArray(jsonString);
        } catch (JSONException e) {
            LOGGER.error("Error converting string to JSON object: " + e.getMessage(), e);
            LOGGER.error("JSON string in error is as follows:" + NL + jsonString);
            return null;
        }
    }

    public static JSONObject toJSONObject(Map links) {
        StringBuilder sb = new StringBuilder("{");

        if (MapUtils.isNotEmpty(links)) {
            for (Object key : links.keySet()) {
                sb.append("\"").append(TextUtils.escapeDoubleQuotes(key)).append("\": ");
                Object value = links.get(key);
                sb.append("\"").append(TextUtils.escapeDoubleQuotes(value)).append("\",");
            }
        }

        // remove last comma
        sb.deleteCharAt(sb.length() - 1);
        sb.append("}");

        return toJSONObject(sb.toString());
    }

    public static JSONObject toDefaultSuccessResponse() {
        return JsonUtils.toJSONObject("{\"responseTimestamp\": " + System.currentTimeMillis() +
                                      ",\"errorCode\":\"\",\"errorMessage\":\"\"}");
    }

    /**
     * This method adds additional element to JSON text. if <code>overwrite</code> parameter is true
     * and an <code>element</code> already exists in JSON text, the value of that element is replaced
     * with  <code>value</code> parameter. If <code>element</code> does not exist then <code>element</code>
     * is added with <code>value</code>
     *
     * @param jsonText  <code>String</code> - JSON text to be enhanced
     * @param element   <code>String</code> - Element to be added or its value to be replaced
     * @param value     <code>Object</code> - Value to be added or replaced
     * @param overwrite <code>boolean</code> - If true replace value
     * @return <code>String</code> - Enhanced json text
     */
    public static String addToJson(String jsonText, String element, Object value, boolean overwrite)
        throws JSONException {
        if (StringUtils.isBlank(jsonText)) { return jsonText; }
        if (StringUtils.isBlank(element)) { return jsonText; }

        JSONObject jsonobj = new JSONObject(jsonText);
        if (jsonobj.has(element)) {
            if (overwrite) { jsonobj.put(element, value); }
        } else {
            jsonobj.put(element, value);
        }
        return jsonobj.toString();
    }

    public static JSONObject serialize(Object object) throws IOException {
        String json = serializeToString(object);
        if (json == null) { return null; }
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("Invalid/malformed JSON: " + e.getMessage());
        }
    }

    public static String serializeToString(Object object) {
        return object == null ? null : JsonWriter.objectToJson(object);
    }

    public static <T> T deserialize(JSONObject json, Class<T> type) {
        return json == null ? null : deserialize(json.toString(), type);
    }

    public static <T> T deserialize(String jsonString, Class<T> type) {
        return jsonString == null ? null : (T) JsonReader.jsonToJava(jsonString);
    }

    public static List<Object> toList(JSONArray jsonArray) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) { list.add(jsonArray.opt(i)); }
        return list;
    }

    public static <T> List<T> filterToList(JSONArray jsonArray, Class<T> filterClass) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object item = jsonArray.opt(i);
            if (filterClass.isAssignableFrom(item.getClass())) { list.add((T) item); }
        }
        return list;
    }

    /** standardize the creation of a json object to capture exception */
    public static JSONObject newExceptionJSONObject(Exception e) {
        if (e == null) { return null; }
        if (e instanceof BusinessException) { return newExceptionJSONObject((BusinessException) e); }

        Pair<String, String> textAndCode = splitCodeAndMessage(e);

        try {
            JSONObject json = new JSONObject();
            json.put("errorCode", textAndCode.getRight());
            json.put("errorMessage", textAndCode.getLeft());
            json.put("responseTimestamp", System.currentTimeMillis());
            return json;
        } catch (JSONException e1) {
            // very unlikely since we control all the JSON properties here.
            return null;
        }
    }

    /** standardize the creation of a json object to capture exception */
    public static JSONObject newExceptionJSONObject(BusinessException e) {
        if (e == null) { return null; }
        Pair<String, String> textAndCode = splitCodeAndMessage(e);

        try {
            JSONObject json = new JSONObject();
            json.put("errorCode", StringUtils.isBlank(e.getErrorCode()) ? textAndCode.getRight() : e.getErrorCode());
            json.put("errorMessage", textAndCode.getLeft());
            json.put("responseTimestamp", System.currentTimeMillis());
            return json;
        } catch (JSONException e1) {
            // very unlikely since we control all the JSON properties here.
            return null;
        }
    }

    /** standardize the creation of a json object to capture exception */
    public static JSONObject newExceptionJSONObject(String errorCode, String errorMessage) {
        try {
            JSONObject json = new JSONObject();
            json.put("errorCode", errorCode);
            json.put("errorMessage", errorMessage);
            json.put("responseTimestamp", System.currentTimeMillis());
            return json;
        } catch (JSONException e1) {
            // very unlikely since we control all the JSON properties here.
            return null;
        }
    }

    public static String findByJsonPath(String json, String jsonPath) {
        return JsonPath.using(Configuration.defaultConfiguration()).parse(json).read(jsonPath);
    }

    public static String getPrimitiveType(JsonPrimitive value) {
        if (value.isBoolean()) { return "boolean"; }
        if (value.isNumber()) { return "number"; }
        if (value.isString()) { return "text"; }
        if (value.isJsonNull()) { return "null"; }
        if (value.isJsonArray()) { return "array"; }
        if (value.isJsonObject()) { return "object"; }
        return "unknown";
    }

    public static String beautify(String content) {
        if (StringUtils.isBlank(content)) { return StringUtils.trim(content); }

        JsonElement jsonElement = GSON.fromJson(content, JsonElement.class);
        requiresNotNull(jsonElement, "invalid json", content);
        return GSON.toJson(jsonElement);
    }

    public static String compact(String content, boolean removeEmpty) {
        if (StringUtils.isBlank(content)) { return StringUtils.trim(content); }

        JsonElement jsonElement = GSON_COMPRESSED.fromJson(content, JsonElement.class);
        requiresNotNull(jsonElement, "invalid json", content);

        jsonElement = JsonCommand.removeEmpty(jsonElement, !removeEmpty);
        return GSON_COMPRESSED.toJson(jsonElement);
    }

    protected static boolean isSimpleType(Object struct) {
        return struct != null && (
            struct instanceof String || ClassUtils.isPrimitiveOrWrapper(struct.getClass()) || struct instanceof Number
        );
    }

    private static Pair<String, String> splitCodeAndMessage(Exception e) {
        String msg = e.getMessage();
        String defaultCode = DEFAULT_ERROR_CODE;
        if (e instanceof AppException) {
            msg = ((AppException) e).getErrorMsg();
            defaultCode = ((AppException) e).getErrorCode();
        } else if (e instanceof SocketException) {
            defaultCode = SOCKET_ERROR_CODE;
        }
        msg = RegexUtils.replace(StringUtils.trim(msg), "(.+)\\[([\\d]{1,4})\\]", "$1|$2");
        String text = StringUtils.defaultIfEmpty(StringUtils.substringBeforeLast(msg, "|"), e.toString());
        String code = StringUtils.defaultIfBlank(StringUtils.substringAfterLast(msg, "|"), defaultCode);
        return new ImmutablePair<>(text, code);
    }

    static {
        Configuration.setDefaults(new Defaults() {
            private final JsonProvider jsonProvider = new GsonJsonProvider();
            private final MappingProvider mappingProvider = new GsonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }
        });
    }
}