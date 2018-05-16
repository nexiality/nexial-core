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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.utils.ResourceUtils;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_NULL_MAP_VALUES;
import static org.nexial.core.NexialConst.DEF_CHARSET;

public final class JsonHelper {
    private JsonHelper() { }

    public static <T> T extractTypedValue(JSONObject json, Class<T> type) {
        return extractTypedValue(json.toString(), type);
    }

    public static <T> T extractTypedValue(JSONObject json, Class<T> type, DateFormat df) {
        return extractTypedValue(json.toString(), type, df);
    }

    public static <T> T extractTypedValue(String json, Class<T> type) {
        return extractTypedValue(json, type, null);
    }

    public static <T> T extractTypedValue(String json, Class<T> type, DateFormat df) {
        if (StringUtils.isBlank(json)) { return null; }
        if (type == null) { return null; }

        T obj = extractKnownType(json, type);
        if (obj != null) { return obj; }

        ObjectMapper mapper = new ObjectMapper();
        if (df != null) {
            mapper.setDateFormat(df);
            mapper.getDeserializationConfig().with(df);
        }

        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Error instantiating type " + type.getName() + ": " + e.getMessage(), e);
        }
    }

    public static <T> T lenientExtractTypedValue(JSONObject json, Class<T> type) {
        if (json == null) { return null; }
        return lenientExtractTypedValue(json.toString(), type);
    }

    public static <T> T lenientExtractTypedValue(JSONObject json, Class<T> type, DateFormat df) {
        if (json == null) { return null; }
        return lenientExtractTypedValue(json.toString(), type, df);
    }

    public static <T> T lenientExtractTypedValue(String json, Class<T> type) {
        return lenientExtractTypedValue(json, type, null);
    }

    public static <T> T lenientExtractTypedValue(String json, Class<T> type, DateFormat df) {
        return extractTypedValue(json, type, df);
    }

    public static String toJSONString(Object bean) throws Exception { return toJSONString(bean, true); }

    public static JSONObject toJSONObject(Object bean) throws Exception {
        String jsonString = toJSONString(bean, true);
        return StringUtils.isNotBlank(jsonString) ? toJSONObject(jsonString) : null;
    }

    public static String toJSONString(Object bean, boolean generateNullProperties) throws Exception {
        if (bean == null) { return generateNullProperties ? "{}" : ""; }

        ObjectMapper mapper = new ObjectMapper();
        if (!generateNullProperties) {
            mapper.setConfig(mapper.getSerializationConfig().without(WRITE_NULL_MAP_VALUES));
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bean);
    }

    public static JSONObject toJSONObject(Object bean, boolean generateNullProperties) throws Exception {
        if (bean instanceof List) { return new JSONObject(toJSONArray(bean, generateNullProperties)); }
        if (bean.getClass().isArray()) { return new JSONObject(toJSONArray(bean, generateNullProperties)); }

        String jsonString = toJSONString(bean, generateNullProperties);
        return StringUtils.isNotBlank(jsonString) ? toJSONObject(jsonString) : null;
    }

    public static JSONArray toJSONArray(Object bean, boolean generateNullProperties) throws Exception {
        return new JSONArray(toJSONString(bean, generateNullProperties));
    }

    public static String getJSONString(JSONObject node, String name) {
        return node == null ? "" : node.optString(name, "");
    }

    public static JSONArray getJSONArray(JSONObject node, String name) {
        return node == null ? null : node.optJSONArray(name);
    }

    public static JSONObject retrieveJSONObject(String filePath) throws IOException, JSONException {
        return new JSONObject(FileUtils.readFileToString(new File(filePath), DEF_CHARSET));
    }

    /**
     * retrieve {@link JSONObject} structure based on a file found in classpath.  Classpath is derived by the
     * {@code clazz} parameter, and the file is based on {@code path} being the relative path calculated from the
     * base of the claspath.
     */
    public static JSONObject retrieveJSONObject(Class clazz, String path) throws IOException, JSONException {
        if (clazz == null) { return null; }
        if (StringUtils.isBlank(path)) { return null; }

        // special path handling for hierarchical classloader where current class is loaded in a higher hierarchy than
        // the classloader context where the resource is located
        // oh yeah... THANKS SPRING!!!
        URL resource = ClassUtils.getDefaultClassLoader().getResource(path);
        if (resource == null && StringUtils.startsWith(path, "/")) {
            resource = ClassUtils.getDefaultClassLoader().getResource(StringUtils.removeStart(path, "/"));
        }
        return new JSONObject(ResourceUtils.loadResource(resource));
    }

    /**
     * converts a {@link JSONArray} object into a String[] array.  If {@code jsonArray} is null or empty, then null
     * will be returned.
     */
    public static String[] toStringArray(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.length() < 1) { return null; }

        String[] array = new String[jsonArray.length()];
        for (int i = 0; i < array.length; i++) { array[i] = jsonArray.optString(i); }
        return array;
    }

    public static List<String> toStringList(JSONArray stringArray) throws JSONException {
        List<String> list = new ArrayList<>();
        if (stringArray == null || stringArray.length() < 1) { return list; }
        for (int i = 0; i < stringArray.length(); i++) { list.add(stringArray.optString(i)); }
        return list;
    }

    /**
     * converts a {@link JSONArray} object into a String object.  If {@code jsonArray} is null or empty, then null
     * will be returned.  Otherwise, the resulting string will be concatenated with {@code delim}.
     */
    public static String toString(JSONArray jsonArray, String delim) {
        if (jsonArray == null || jsonArray.length() <= 0) { return null; }

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < jsonArray.length(); i++) {
            buffer.append(jsonArray.optString(i)).append(delim);
        }

        // we don't want the last 'delim' character
        return buffer.deleteCharAt(buffer.length() - 1).toString();
    }

    public static List<String> sortJSONArray(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array == null || array.length() < 1) { return list; }
        for (int i = 0; i < array.length(); i++) { list.add(array.optString(i)); }
        Collections.sort(list);
        return list;
    }

    /**
     * create compact deserialized string for {@code json}, suitable for network transmission.
     * <p/>
     * the {@code removeNullNameValuePair} parameter allows for removal of name/value pair where the value is null.
     * <b>Note that null array indices will be kept untouched since removing of such indices will shift the
     * position of subsequent indices in the same array</b>.  Also, string literal "null" will be ignored as well to
     * maintain intent.
     */
    public static String toCompactString(JSONObject json, boolean removeNullNameValuePair) throws JSONException {
        if (json == null) { return null; }

        String string = StringUtils.replaceChars(json.toString(0), "\n\r", "");
        if (removeNullNameValuePair && StringUtils.isNotBlank(string)) {
            string = string.replaceAll("(?:\"[0-9A-Za-z_-]+\"\\:null,)", "");
            string = string.replaceAll("(?:\"[0-9A-Za-z_-]+\"\\:null)", "");
            // assure well-formed via second conversion
            string = new JSONObject(string).toString(0);
        }
        return string;
    }

    /**
     * provide js-like convenience to retrieve value from {@code json}.  {@code json} is expected to use dot (.) to
     * denote node hierarchy and square brackets ({@code [...]}) to denote array.  If a node name contains ., then
     * the standard Javascript array denotion should be used.  For example,
     * <pre>{ "a": { "b.c": "hello" } }</pre>
     * can be referenced as:
     * <pre>JsonHelper.fetchString(json, "a[\"b.c\"]");</pre>
     * or simply
     * <pre>JsonHelper.fetchString(json, "a[b.c]");</pre>
     */
    public static String fetchString(JSONObject json, String path) { return JSONPath.find(json, path); }

    public static boolean hasError(JSONObject json, String errorProperty) {
        return StringUtils.isNotBlank(JsonHelper.getJSONString(json, errorProperty));
    }

    public static boolean hasError(JSONObject json) { return hasError(json, "errorCode"); }

    private static <T> T extractKnownType(String json, Class<T> type) {
        String typeName = type.getName();
        if (StringUtils.equals(typeName, "boolean") || StringUtils.equals(typeName, Boolean.class.getName())) {
            return (T) Boolean.valueOf(StringUtils.containsOnly(json, "Yy") ||
                                       StringUtils.equalsIgnoreCase(json, "true"));
        }
        if ("java.lang.String".equals(typeName)) { return (T) json; }

        return null;
    }
}
