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
 */

package org.nexial.core.utils;

import java.util.Map;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;

import static org.json.JSONObject.NULL;

/**
 * Helper class to add or replace an existing JSON Document (JSONObject or JSONArray) via JSON Path.
 */
public final class JsonEditor {
    private boolean removeNull;

    public static class JsonEditorConfig {
        private boolean removeNull;

        public void setRemoveNull(boolean removeNull) { this.removeNull = removeNull;}
    }

    private JsonEditor() { }

    public static JsonEditor newInstance(JsonEditorConfig config) {
        JsonEditor editor = new JsonEditor();
        if (config != null) {
            editor.removeNull = config.removeNull;
        }

        return editor;
    }

    /**
     * using {@code jsonPath}, find the appropriate position in {@code json} to add {@code data} into it.
     *
     * In some cases, the "add" operation would end up like an
     */
    public Object add(String json, String jsonPath, String data) {
        if (StringUtils.isBlank(jsonPath)) { return addData(json, data); }

        // 1. extract current hierarchy (nodeName)

        // 1.1 `jsonPath` could be in the form of node[filter].sub-node
        jsonPath = cleanJsonPath(jsonPath);
        String nodeName = extractNodeName(jsonPath);
        jsonPath = StringUtils.substringAfter(jsonPath, nodeName);
        if (StringUtils.startsWith(jsonPath, ".")) { jsonPath = StringUtils.removeStart(jsonPath, "."); }

        // 2. check if we have filter
        // at this point, `nodeName` does not have filter
        if (!RegexUtils.isExact(nodeName, ".+\\[.+\\]")) { return addToNode(json, nodeName, jsonPath, data); }

        // 3. we have filter; current nodeName might be filter (e.g. node[key=value]) or ordinal position (e.g. node[2])
        // at this point, we have either filter or ordinal position
        String filter = TextUtils.substringBetweenFirstPair(nodeName, "[", "]");
        nodeName = StringUtils.substringBefore(nodeName, "[");

        // 3.1 check that current filter is referencing single node name or ordinal position
        String ordinal = StringUtils.trim(filter);
        if (NumberUtils.isDigits(ordinal)) {
            // as in { `nodeName`: [ ..., ..., ...], ... ... }
            return addToIndex(json, nodeName, jsonPath, data, NumberUtils.toInt(ordinal));
        }

        // 4 filter is not ordinal or single node; therefore it could be equality filter or REGEX filter
        boolean regexFilter = StringUtils.startsWith(filter, "REGEX:");
        if (regexFilter) {
            // regex is too complicated for now.. maybe later
            // todo: future -> we can enhance this method to support regex filter
            String regex = StringUtils.removeStart(filter, "REGEX:");
            throw new RuntimeException("NOT YET IMPLEMENTED for REGEX filter\n" +
                                       "\tjson=" + json + "\n" +
                                       "\tnodeName=" + nodeName + "\n" +
                                       "\tfilter=" + filter + "\n" +
                                       "\tregex-filter=" + regex + "\n" +
                                       "\tjsonPath=" + jsonPath + "\n" +
                                       "\tdata=" + data);
        }

        // 5 filter is not ordinal, not single node and not REGEX
        // it must be equality filter
        throw new RuntimeException("NOT YET IMPLEMENTED for REGEX filter\n" +
                                   "\tjson=" + json + "\n" +
                                   "\tnodeName=" + nodeName + "\n" +
                                   "\tfilter=" + filter + "\n" +
                                   "\tjsonPath=" + jsonPath + "\n" +
                                   "\tdata=" + data);
    }

    /**
     * add {@code data} to the {@code nodeName} of {@code json}.  Optionally, {@code jsonPath} will further the
     * traversal further down the hierarchy of the node value corresponding to {@code nodeName}.
     *
     * Note that {@code nodeName} does not contain filter
     */
    protected Object addToNode(String json, String nodeName, String jsonPath, String data) {
        // objectify `json`
        boolean isJsonArray = false;
        JSONObject jsonObject = null;
        JSONArray jsonArray = null;
        if (TextUtils.isBetween(json, "[", "]")) {
            isJsonArray = true;
            jsonArray = new JSONArray(json);
        } else {
            jsonObject = new JSONObject(json);
        }

        if (isJsonArray) {
            // scanning array to find an item of json array matching to `nodeName`
            for (int i = 0; i < jsonArray.length(); i++) {
                Object arrayItem = jsonArray.get(i);

                // as in: `nodeName`: [ ..., null, ...]
                if (arrayItem == null || arrayItem == NULL) { continue; }

                // as in: `nodeName`: [ ..., [..., ..., ...] , ...]
                // todo: it'd be nice to continue digging further down.. but it gets complicated...
                if (arrayItem instanceof JSONArray) { continue; }

                // as in: `nodeName`: [ ..., { ... }, ...]
                if (arrayItem instanceof JSONObject) {
                    // traverse down the json object (item of the array) and add `data` to it
                    addToNode((JSONObject) arrayItem, nodeName, jsonPath, data);
                    break;
                }
            }

            return jsonArray;
        }

        // current json is object, as in { ... }

        // 2.1 if `nodeName` is not currently known, then we'll stop here because we don't know what to do in this case
        if (!jsonObject.has(nodeName)) { return jsonObject; }

        return jsonObject.put(nodeName, addToObject(jsonObject.get(nodeName), jsonPath, data));
    }

    /**
     * add {@code data} to {@code node} as the value of its {@code nodeName}.  Optionally, {@code jsonPath} will
     * further traverse down the hierarchy of current node value to add {@code data}, which will be converted to its
     * JSON structure via {@link #toJsonValue(String)}
     */
    protected void addToNode(JSONObject node, String nodeName, String jsonPath, String data) {
        if (!node.has(nodeName)) { return; }

        // found it.. `data` should be added to this item
        Object nodeValue = node.get(nodeName);

        if (nodeValue == null || nodeValue == NULL) {
            // overwrite null with `data`
            node.put(nodeName, addToObject(nodeValue, jsonPath, data));
            return;
        }

        if (nodeValue instanceof JSONArray) {
            addToArray(((JSONArray) nodeValue), jsonPath, data, -1);
            return;
        }

        if (nodeValue instanceof JSONObject) {
            addToObject(nodeValue, jsonPath, data);
            return;
        }

        // last resort
        addToObject(node, jsonPath, data);
    }

    /**
     * add `data` to the node named as `nodeName` in `json`. Optionally, traverse further down to satisfy the path
     * specified in `jsonPath.
     */
    protected Object addToIndex(String json, String nodeName, String jsonPath, String data, int indexToAdd) {
        if (TextUtils.isBetween(json, "[", "]")) {
            // as in [ ..., ..., ...]
            throw new RuntimeException("Unable to add '" + data + "' to '" + json + "' under node '" + nodeName + "'");
        }

        JSONObject jsonObject = new JSONObject(json);

        // current node/hierarchy
        Object nodeValue = jsonObject.opt(nodeName);

        // 3.2 if `nodeName` is not currently known, then we'll assume it's array (since filter is ordinal)
        if (nodeValue == null) { return jsonObject.put(nodeName, addToArray(null, jsonPath, data, indexToAdd)); }

        // 3.4 found ordinal position filter, current `nodeValue` is array
        if (nodeValue instanceof JSONArray) {
            return jsonObject.put(nodeName, addToArray((JSONArray) nodeValue, jsonPath, data, indexToAdd));
        }

        // 3.5 found ordinal filter, current `nodeValue` is object
        // treat filter as sub node name
        if (nodeValue instanceof JSONObject) {
            return jsonObject.put(nodeName, addToObject(((JSONObject) nodeValue).opt(indexToAdd + ""), jsonPath, data));
        }

        // 3.6 current `nodeValue` is not null, array or object => therefore we are replacing the entire primitive!
        return jsonObject.put(nodeName, addToObject(null, jsonPath, data));
    }

    protected JSONArray addToArray(JSONArray array, String jsonPath, String data, int indexToAdd) {
        if (array == null) { array = new JSONArray(); }

        Object currentNode = array.opt(indexToAdd);

        if (StringUtils.isNotBlank(jsonPath)) {
            Object jsonValue = addToObject(currentNode, jsonPath, data);
            return indexToAdd < 0 ? array.put(jsonValue) : array.put(indexToAdd, jsonValue);
        }

        Object jsonValue = toJsonValue(data);

        if (indexToAdd < 0) { return addToArray(array, jsonValue); }

        if (currentNode == null || currentNode == NULL) { return array.put(jsonValue); }

        // if current node is array, then we would add to this array
        if (currentNode instanceof JSONArray) {
            return array.put(indexToAdd, addToArray((JSONArray) currentNode, jsonValue));
        }

        if (currentNode instanceof JSONObject) {
            if (jsonValue instanceof JSONArray) {
                // can't merge array to object
                throw new RuntimeException("Unable to add JSON Array '" + data + "' to JSON Object");
            }

            JSONObject currentObject = (JSONObject) currentNode;
            if (jsonValue instanceof JSONObject) {
                // append the entire `data` to current object
                JSONObject jsonObject = (JSONObject) jsonValue;
                Map<String, Object> newJsonMap = jsonObject.toMap();
                for (String key : newJsonMap.keySet()) { currentObject.put(key, jsonObject.get(key)); }
                return array.put(indexToAdd, currentObject);
            } else {
                // if not array or object, `data` must be primitive => simply replace it
                return array.put(indexToAdd, jsonValue);
            }
        }

        // now we know that current node resolves to primitive => override it with `jsonValue`
        return array.put(indexToAdd, jsonValue);
    }

    protected JSONArray addToArray(JSONArray array, Object jsonValue) {
        if (jsonValue instanceof JSONArray) {
            // if `data` resolves to array, then append its items to current array
            JSONArray newItems = ((JSONArray) jsonValue);
            for (Object item : newItems) { array.put(item); }
            return array;
        } else {
            // else, append the entire `data` to current array
            return array.put(jsonValue);
        }
    }

    protected Object addToObject(Object node, String jsonPath, String data) {
        // current node is null or doesn't exists
        // since node is null, we can't traverse further down any json path.
        // so if there are more path to traverse, then we'll stop. otherwise `data` is what we will end up using
        if (node == null || node == NULL) { return StringUtils.isBlank(jsonPath) ? toJsonValue(data) : node; }

        // 2.2 current `nodeValue` is object (means enhance)
        // as in { ..., `nodeName`: { ... }, ... }
        // OR    { ..., `nodeName`: `nodeValue`, ... }
        if (node instanceof JSONObject) { return add(node.toString(), jsonPath, data); }

        // 2.3 no filter but current `nodeValue` is array
        // as in `nodeName`: [ ..., ..., ... ]
        // if there are more jsonpath, we need to stop
        // nothing we can do here since traversing JSON path over array is too complicated (for now
        // todo: future - we could possible implement the `add` operation across all items of this array
        if (node instanceof JSONArray) { return addToArray((JSONArray) node, jsonPath, data, -1); }

        // 2.4 no filter and current `nodeValue` is primitive (means overwrite)
        // node can be primitive => replace existing value with
        // we can only add `data` if there aren't any more json path to traverse
        return StringUtils.isBlank(jsonPath) ? toJsonValue(data) : node;
    }

    /**
     * add data to JSON at its current hierarchy. {@code data} will be evaluated into a name/value pair, where
     * {@code name} is preceded by {@code :}, and value could be:
     * <ul>
     * <li>array - if it's wrapped in [...]</li>
     * <li>object - if it's wrapped in {...}</li>
     * <li>primitive - {@code boolean} if it's {@code true|false}, {@code number} if it can convert as a number,
     * and {@code string} otherwise.</li>
     * </ul>
     *
     * If {@code data} does not conform to the above expected, {@link RuntimeException} will be thrown.
     */
    protected Object addData(String jsonString, String data) {
        if (data == null || StringUtils.equals(StringUtils.trim(data), "null")) { return NULL; }

        boolean isJsonArray = false;
        JSONObject jsonObject = null;
        JSONArray jsonArray = null;
        if (TextUtils.isBetween(jsonString, "[", "]")) {
            isJsonArray = true;
            jsonArray = new JSONArray(jsonString);
        } else {
            jsonObject = new JSONObject(jsonString);
        }

        if (StringUtils.isBlank(data)) { return isJsonArray ? jsonArray : jsonObject; }

        // NOTE: we cannot add array or object directly into a json object.  Such must be associated to a node name.

        String dataTrimmed = StringUtils.trim(data);
        if (TextUtils.isBetween(dataTrimmed, "[", "]")) {
            JSONArray array = new JSONArray(dataTrimmed);
            ConsoleUtils.log("resolved to array: " + array);
            if (isJsonArray) {
                for (int i = 0; i < array.length(); i++) { jsonArray.put(array.opt(i)); }
                return jsonArray;
            } else {
                throw new RuntimeException("array cannot be added directly to JSON: " + data);
            }
        }

        if (TextUtils.isBetween(dataTrimmed, "{", "}")) {
            JSONObject newJson = new JSONObject(dataTrimmed);
            ConsoleUtils.log("resolved to JSON: " + newJson);

            if (isJsonArray) { return jsonArray.put(newJson); }

            // SPECIAL CASE: When current `json` is completely empty, we can replace it with new `json`
            // if existing json is null/empty, then new replace old
            if (jsonObject.length() < 1) { return newJson; }

            Map<String, Object> newJsonMap = newJson.toMap();
            for (String key : newJsonMap.keySet()) { jsonObject.put(key, newJson.get(key)); }
            return jsonObject;
        }

        if (StringUtils.contains(data, ":")) {
            if (isJsonArray) { return jsonArray.put("{" + data + "}"); }

            // maybe it's in the form of "key": ...
            String nodeName = StringUtils.trim(StringUtils.substringBefore(data, ":"));
            if (TextUtils.isBetween(nodeName, "\"", "\"")) { nodeName = StringUtils.unwrap(nodeName, "\""); }

            String nodeValue = StringUtils.trim(StringUtils.substringAfter(data, ":"));

            if (StringUtils.equals(nodeValue, "null")) {
                jsonObject.put(nodeName, NULL);
                return jsonObject;
            }

            if (TextUtils.isBetween(nodeValue, "[", "]")) {
                // detected array as node value
                JSONArray array = new JSONArray(nodeValue);
                jsonObject.put(nodeName, array);
                return jsonObject;
            }

            if (TextUtils.isBetween(nodeValue, "{", "}")) {
                // detected json object as node value
                JSONObject jsonNew = new JSONObject(nodeValue);
                jsonObject.put(nodeName, jsonNew);
                return jsonObject;
            }

            if (TextUtils.isBetween(nodeValue, "\"", "\"")) {
                jsonObject.put(nodeName, StringUtils.unwrap(nodeValue, "\""));
                return jsonObject;
            }

            if (RegexUtils.isExact(nodeValue, "(true|false)")) {
                jsonObject.put(nodeName, BooleanUtils.toBoolean(nodeValue));
                return jsonObject;
            }

            if (NumberUtils.isCreatable(nodeValue)) {
                jsonObject.put(nodeName, NumberUtils.createNumber(nodeValue));
                return jsonObject;
            }

            // last resort...
            jsonObject.put(nodeName, nodeValue);
            return jsonObject;
        }

        if (isJsonArray) {
            if (TextUtils.isBetween(data, "\"", "\"")) { return jsonArray.put(StringUtils.unwrap(data, "\"")); }
            if (RegexUtils.isExact(data, "(true|false)")) { return jsonArray.put(BooleanUtils.toBoolean(data)); }
            if (NumberUtils.isCreatable(data)) { return jsonArray.put(NumberUtils.createNumber(data)); }
            return jsonArray.put(data);
        }

        throw new RuntimeException("Unable to add data to JSON: " + data);
    }

    /**
     * best-effort attempt to convert {@code data} into its JSON equivalent: {@link JSONObject#NULL}, {@link JSONArray},
     * {@link JSONObject} or JSON primitive.
     */
    @NotNull
    protected Object toJsonValue(String data) {
        // null begets NULL
        if (data == null) { return NULL; }

        String dataTrimmed = StringUtils.trim(data);

        // "null" begets NULL
        if (StringUtils.equals(dataTrimmed, "null")) { return NULL; }

        // empty means string
        if (StringUtils.isEmpty(dataTrimmed)) { return data; }

        // [...] means array
        if (TextUtils.isBetween(dataTrimmed, "[", "]")) { return new JSONArray(dataTrimmed); }

        // {...} means object
        if (TextUtils.isBetween(dataTrimmed, "{", "}")) { return new JSONObject(dataTrimmed); }

        // true or false means boolean
        if (RegexUtils.isExact(data, "(true|false)")) { return BooleanUtils.toBoolean(data); }

        // if it looks like a number, then it's a number
        if (NumberUtils.isCreatable(data)) { return NumberUtils.createNumber(data); }

        // "..." means string
        if (TextUtils.isBetween(data, "\"", "\"")) { return StringUtils.unwrap(data, "\""); }

        // "...": .... means object, wrap it with { and }
        String dataOneLine = TextUtils.toOneLine(dataTrimmed, false);
        if (RegexUtils.isExact(dataOneLine, "\"?([^\".]+)\"?\\s*\\:\\s*(.+)", true)) {
            return new JSONObject("{" + data + "}");
        }

        // ...,...,... means array, wrap it with [ and ]
        if (StringUtils.contains(data, ",")) { return new JSONArray("[" + data + "]"); }

        // last resort... just treat it as string
        return data;
    }

    /**
     * extract the most immediate (left-most) node name from {@code jsonPath}. If exists, extracted node name will
     * include filter (i.e. {@code node[filter]} filter).
     */
    @NotNull
    protected String extractNodeName(String jsonPath) {
        if (!StringUtils.contains(jsonPath, "[")) {
            // found pattern node or node.sub-node...
            return StringUtils.contains(jsonPath, ".") ? StringUtils.substringBefore(jsonPath, ".") : jsonPath;
        }

        // `jsonPath` could be in the form of node[filter]....
        String nodeName = StringUtils.substringBefore(jsonPath, "[");

        // nope, `jsonPath` is in the form of node.sub-node[filter]...
        if (StringUtils.contains(nodeName, ".")) { return StringUtils.substringBefore(jsonPath, "."); }

        // nodeName should contain filter as well
        // found open bracket without close bracket? This will likely fail...
        if (!StringUtils.contains(jsonPath, "]")) {
            throw new MalformedJsonPathException("Invalid JSON Path; unbalanced brackets [] found", jsonPath);
        }

        // yes, we found the pattern node[filter]....
        nodeName = StringUtils.substringBefore(jsonPath, "]") + "]";
        String remaining = StringUtils.substringAfter(jsonPath, nodeName);

        // expects '.more filter' after 'node[...]' pattern
        if (StringUtils.isNotBlank(remaining) && !StringUtils.startsWith(remaining, ".")) {
            throw new MalformedJsonPathException("Invalid JSON Path; filter not followed by dot (.)", jsonPath);
        }

        return nodeName;
    }

    @NotNull
    private String cleanJsonPath(String jsonPath) {
        if (StringUtils.isBlank(jsonPath)) { return ""; }

        if (StringUtils.endsWith(jsonPath, ".")) {
            throw new MalformedJsonPathException("JSON Path must not end with dot (.)", jsonPath);
        }

        int openBracketCount = StringUtils.countMatches(jsonPath, '[');
        int closeBracketCount = StringUtils.countMatches(jsonPath, ']');
        if (openBracketCount != closeBracketCount) {
            throw new MalformedJsonPathException("JSON Path found with unbalanced brackets []", jsonPath);
        }

        if (StringUtils.contains(jsonPath, "[]")) {
            throw new MalformedJsonPathException("JSON Path must not contain empty filter []", jsonPath);
        }

        if (StringUtils.startsWith(jsonPath, ".")) { jsonPath = StringUtils.substringAfter(jsonPath, "."); }
        return jsonPath;
    }
}
