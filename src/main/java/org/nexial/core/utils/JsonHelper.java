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
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.utils.ResourceUtils;
import org.springframework.util.ClassUtils;

import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import static org.nexial.core.NexialConst.DEF_CHARSET;

public final class JsonHelper {
    private JsonHelper() { }

    public static JSONObject toJsonObject(Object bean) {
        String jsonString = toJsonString(bean, true);
        return StringUtils.isNotBlank(jsonString) ? toJsonObject(jsonString) : null;
    }

    public static JSONObject toJsonObject(Object bean, boolean generateNullProperties) {
        if (bean instanceof List) { return new JSONObject(toJsonArray(bean, generateNullProperties)); }
        if (bean.getClass().isArray()) { return new JSONObject(toJsonArray(bean, generateNullProperties)); }

        String jsonString = toJsonString(bean, generateNullProperties);
        return StringUtils.isNotBlank(jsonString) ? toJsonObject(jsonString) : null;
    }

    public static JSONObject retrieveJsonObject(String filePath) throws IOException, JSONException {
        return new JSONObject(FileUtils.readFileToString(new File(filePath), DEF_CHARSET));
    }

    /**
     * retrieve {@link JSONObject} structure based on a file found in classpath.  Classpath is derived by the
     * {@code clazz} parameter, and the file is based on {@code path} being the relative path calculated from the
     * base of the classpath.
     */
    public static JSONObject retrieveJsonObject(Class clazz, String path) throws IOException, JSONException {
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

    public static String toJsonString(Object bean) { return toJsonString(bean, true); }

    public static String toJsonString(Object bean, boolean generateNullProperties) {
        if (bean == null) { return generateNullProperties ? "{}" : ""; }

        GsonBuilder builder = new GsonBuilder().setPrettyPrinting().setLenient().disableHtmlEscaping();
        if (generateNullProperties) { builder.serializeNulls(); }

        return builder.create().toJson(bean);
    }

    public static String getJsonString(JSONObject node, String name) {
        return node == null ? "" : node.optString(name, "");
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

    /**
     * provide js-like convenience to retrieve value from {@code json}.  {@code json} is expected to use dot (.) to
     * denote node hierarchy and square brackets ({@code [...]}) to denote array.  If a node name contains ., then
     * the standard Javascript array notation should be used.  For example,
     * <pre>{ "a": { "b.c": "hello" } }</pre>
     * can be referenced as:
     * <pre>JsonHelper.fetchString(json, "a[\"b.c\"]");</pre>
     * or simply
     * <pre>JsonHelper.fetchString(json, "a[b.c]");</pre>
     */
    public static String fetchString(JSONObject json, String path) { return JSONPath.find(json, path); }

    public static JSONArray toJsonArray(Object bean, boolean generateNullProperties) {
        return new JSONArray(toJsonString(bean, generateNullProperties));
    }

    public static JSONArray getJsonArray(JSONObject node, String name) {
        return node == null ? null : node.optJSONArray(name);
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

    public static List<String> sortJsonArray(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array == null || array.length() < 1) { return list; }
        for (int i = 0; i < array.length(); i++) { list.add(array.optString(i)); }
        Collections.sort(list);

        return list;
    }

    public static boolean hasError(JSONObject json, String errorProperty) {
        return StringUtils.isNotBlank(getJsonString(json, errorProperty));
    }

    public static boolean hasError(JSONObject json) { return hasError(json, "errorCode"); }

    /**
     * convert {@code records}, which presumably from CSV file, to a JSON array streamed to the designated
     * {@code destination}. Optionally, one may use {@code header} to indicate if {@code records} contains header
     * (as first row) or not.
     *
     * <b>NOTE</b>: {@code destination} is NOT closed at the end of this method. Caller will need to flush/close after
     * the method completes.
     */
    public static void fromCsv(List<List<String>> records, boolean header, Writer destination) throws IOException {
        if (CollectionUtils.isEmpty(records)) { throw new IOException("No valid content"); }

        // only 1 line and it is the header... this will not do
        if (header && records.size() == 1) { throw new IOException("Record contains only header; conversion aborted"); }

        try (JsonWriter writer = new JsonWriter(destination)) {
            writer.beginArray();

            if (header) {
                List<String> headers = records.get(0);
                for (int i = 1; i < records.size(); i++) {
                    writer.beginObject();
                    List<String> record = records.get(i);
                    for (int j = 0; j < record.size(); j++) { writer.name(headers.get(j)).value(record.get(j)); }
                    writer.endObject();
                }
            } else {
                for (List<String> record : records) {
                    writer.beginArray();
                    for (String field : record) { writer.value(field); }
                    writer.endArray();
                }
            }

            writer.endArray();
        }
    }
}
