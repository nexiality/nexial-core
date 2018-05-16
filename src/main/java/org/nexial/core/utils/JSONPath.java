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

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.json.JSONObject.NULL;
import static org.nexial.core.utils.JSONPath.Option.*;
import static org.nexial.core.utils.JsonUtils.isSimpleType;

/**
 * Javascript-like support for extracting JSON fragment via a descriptive path.  For example, assume the following
 * JSON structure:
 * <pre>
 * {
 *     "name": "John Smith",
 *     "age": 17,
 *     "address": {
 *         "street1": "123 Elm Street",
 *         "street2": null,
 *         "city": "Gotham",
 *         "state": "Solid",
 *         "zip": "54321"
 *     }
 *     "data1": [
 *      "abc", "efg", "hij"
 *     ],
 *     "data2": [
 *      { "klm": "nop" },
 *      { "qrs": 999.01 }
 *     ]
 * }
 * </pre>
 * Here are some possible ways to access JSON fragments by "path":
 * <ul>
 * <li>{@code new JSONPath(json, "name").get() --> "John Smith"}</li>
 * <li>{@code new JSONPath(json, "age").get() --> "17"}</li>
 * <li>{@code new JSONPath(json, "address.street1").get() --> "123 Elm Street"}</li>
 * <li>{@code new JSONPath(json, "address.street2").get() --> null}</li>
 * <li>{@code new JSONPath(json, "data1[0]").get() --> "abc"}</li>
 * <li>{@code new JSONPath(json, "data1[\"abc\"]").get() --> "abc" // moot point, but useful to confirm
 * expectation}</li>
 * <li>{@code new JSONPath(json, "data1[3]").get() --> null}</li>
 * <li>{@code new JSONPath(json, "data1").get() --> "[\"abc\",\"efg\",\"hij\"]"}</li>
 * <li>{@code new JSONPath(json, "data2.klm").get() --> "nop"}</li>
 * <li>{@code new JSONPath(json, "data2[0]").get() --> "nop"}</li>
 * <li>{@code new JSONPath(json, "data2[qrt]").get() --> "999.01"}</li>
 * <li>{@code new JSONPath(json, "data2['qrt']").get() --> "999.01"}</li>
 * <li>{@code new JSONPath(json, "data2[\"qrt\"]").get() --> "999.01"}</li>
 * </ul>
 *
 * Class name kept as uppercase "JSON" to avoid confusion against jayway's JsonPath
 */
public class JSONPath {
    private static final Map<String, String> ESCAPED_CHARS_REPLACER = TextUtils.toMap("\\.=~~~>~~|" +
                                                                                      "\\]=~~}~~|" +
                                                                                      "\\[=~~{~~|",
                                                                                      "|", "=");
    private transient Logger logger = LoggerFactory.getLogger(getClass());
    private Object dataStruc;
    private String key;
    private JSONPath child;
    private JSONPath parent;
    private Object parsedVal;

    enum Option {
        PREPEND, APPEND, OVERWRITE, DELETE, OVERWRITE_OR_ADD;

        String handle(Object existingValue, Object newValue) {
            switch (this) {
                case PREPEND:
                    if (newValue == null) {
                        return existingValue == null ? null : Objects.toString(existingValue);
                    } else {
                        return existingValue == null ? Objects.toString(newValue) :
                               Objects.toString(newValue) + Objects.toString(existingValue);
                    }
                case APPEND:
                    if (newValue == null) {
                        return existingValue == null ? null : Objects.toString(existingValue);
                    } else {
                        return existingValue == null ? Objects.toString(newValue) :
                               Objects.toString(existingValue) + Objects.toString(newValue);
                    }
                case OVERWRITE:
                case OVERWRITE_OR_ADD:
                    return newValue == null ? null : Objects.toString(newValue);
                case DELETE:
                    return null;
                default:
                    throw new IllegalArgumentException("JSONPath.Option " + this.name() + " not supported");
            }
        }
    }

    private class JSONPathKey {
        boolean isIndexOrdinal;
        String nodeName;
        String nodeIndex;
        boolean isFilter;

        @Override
        public String toString() { return StringUtils.defaultString(nodeName, nodeIndex); }
    }

    public JSONPath(JSONObject dataStruc, String key) {
        this.dataStruc = dataStruc;
        this.key = key;
        init();
    }

    public JSONPath(JSONArray dataStruc, String key) {
        this.dataStruc = dataStruc;
        this.key = key;
        init();
    }

    private JSONPath(Object dataStruc, String key, JSONPath parent) {
        this.dataStruc = dataStruc;
        this.key = key;
        this.parent = parent;
        init();
    }

    public String getKey() { return key; }

    public void setKey(String key) { this.key = key; }

    public JSONPath getChild() { return child; }

    public void setChild(JSONPath child) { this.child = child; }

    public JSONPath getParent() { return parent; }

    public void setParent(JSONPath parent) { this.parent = parent; }

    public boolean isTopLevel() { return parent == null; }

    public boolean isLast() { return child == null; }

    public <T> T getAs(Class<T> type) {
        if (child != null) {
            Object value = child.getNative();
            if (type.isArray() && value instanceof JSONArray) { return toArray(type, (JSONArray) value); }
            return type.cast(value);
        }

        if (parsedVal == null) { return null; }
        if (parsedVal == NULL) { return null; }

        if (type.isArray() && parsedVal instanceof JSONArray) { return toArray(type, (JSONArray) parsedVal); }
        return type.cast(parsedVal);
    }

    public String get() {
        if (child != null) { return child.get(); }
        if (parsedVal == null) { return null; }
        if (parsedVal == NULL) { return null; }
        if (parsedVal instanceof JSONArray) {
            JSONArray array = (JSONArray) parsedVal;
            if (array.length() < 1) { return null; }
            return array.toString();
        }
        return parsedVal.toString();
    }

    public int count() {
        String matches = get();
        if (matches == null) { return 0; }

        // is this array?
        if (StringUtils.startsWith(matches, "[") && StringUtils.endsWith(matches, "]")) {
            JSONArray matchedArray = getAs(JSONArray.class);
            return matchedArray != null ? matchedArray.length() : 0;
        } else {
            return 1;
        }
    }

    public static String find(JSONObject json, String path) { return new JSONPath(json, path).get(); }

    public static String find(JSONArray json, String path) { return new JSONPath(json, path).get(); }

    public static <T> T find(JSONObject json, String path, Class<T> type) {
        JSONPath jsonPath = new JSONPath(json, path);
        return jsonPath.getAs(type);
    }

    public static JSONObject append(JSONObject json, String path, String appendWith) throws JSONException {
        return change(json, path, appendWith, APPEND);
    }

    public static JSONArray append(JSONArray json, String path, String appendWith) throws JSONException {
        return change(json, path, appendWith, APPEND);
    }

    public static JSONObject prepend(JSONObject json, String path, String prependWith) throws JSONException {
        return change(json, path, prependWith, PREPEND);
    }

    public static JSONArray prepend(JSONArray json, String path, String prependWith) throws JSONException {
        return change(json, path, prependWith, PREPEND);
    }

    public static JSONObject delete(JSONObject json, String path) throws JSONException {
        return change(json, path, "", DELETE);
    }

    public static JSONArray delete(JSONArray json, String path) throws JSONException {
        return change(json, path, "", DELETE);
    }

    public static JSONObject overwrite(JSONObject json, String path, String overwriteWith) throws JSONException {
        return overwrite(json, path, overwriteWith, false);
    }

    public static JSONObject overwrite(JSONObject json, String path, String overwriteWith, boolean blankOnly)
        throws JSONException {
        return overwrite(json, path, overwriteWith, OVERWRITE, blankOnly);
    }

    public static JSONArray overwrite(JSONArray json, String path, String overwriteWith) throws JSONException {
        return overwrite(json, path, overwriteWith, false);
    }

    public static JSONArray overwrite(JSONArray json, String path, String overwriteWith, boolean blankOnly)
        throws JSONException {
        return overwrite(json, path, overwriteWith, OVERWRITE, blankOnly);
    }

    public static JSONObject overwriteOrAdd(JSONObject json, String path, String overwriteWith, boolean blankOnly)
        throws JSONException {
        return overwrite(json, path, overwriteWith, OVERWRITE_OR_ADD, blankOnly);
    }

    public static JSONArray overwriteOrAdd(JSONArray json, String path, String overwriteWith, boolean blankOnly)
        throws JSONException {
        return overwrite(json, path, overwriteWith, OVERWRITE_OR_ADD, blankOnly);
    }

    protected void modify(String modifyWith, Option option) throws JSONException {
        if (child != null) {
            child.modify(modifyWith, option);
            return;
        }

        if (dataStruc == null) { return; }

        JsonPathFilters filters = null;
        if (TextUtils.isBetween(key, "[", "]")) {
            String enclosedFilter = cleanNodeName(StringUtils.removeEnd(StringUtils.removeStart(key, "["), "]"));
            if (!NumberUtils.isDigits(enclosedFilter)) { filters = new JsonPathFilters(enclosedFilter); }
        }

        if (option == OVERWRITE_OR_ADD && parsedVal == null) {
            if (filters != null) {
                logger.error("filters=" + filters + ", unable to overwrite-add to JSON document via filter");
                return;
            }

            // plain-jane keys
            if (dataStruc instanceof JSONObject) {
                ((JSONObject) dataStruc).put(key, modifyWith);
                return;
            }

            if (dataStruc instanceof JSONArray) {
                String source = "{\"" + key + "\":" + (modifyWith == null ? "null" : "\"" + modifyWith + "\"") + "}";
                ((JSONArray) dataStruc).put(new JSONObject(source));
                return;
            }

            if (logger.isDebugEnabled()) { logger.debug("key=" + key + ", no parsedVal, no updates done"); }
            return;
        }

        if (dataStruc instanceof JSONArray) {
            modify((JSONArray) dataStruc, modifyWith, option);
            return;
        }

        if (option == DELETE) {
            delete(filters);
            return;
        }

        if (!(parsedVal instanceof String)) {
            logger.warn("key=" + key + ", parsedVal is an " + parsedVal.getClass().getSimpleName() +
                        ", and not supported for update");
            return;
        }

        parsedVal = option.handle(parsedVal, modifyWith);

        if (dataStruc instanceof JSONObject) {
            JSONObject json = (JSONObject) dataStruc;
            if (StringUtils.isEmpty(key)) {
                // key must have been a dotted index, hence expect only 1 element and just update the value
                Iterator keys = json.keys();
                String key = (String) keys.next();
                json.put(key, parsedVal);
            } else {
                if (TextUtils.isBetween(key, "[", "]")) {
                    json.put(fromIndexToSimpleKey(key), parsedVal);
                } else {
                    json.put(key, parsedVal);
                }
            }
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("key=" + key + ", dataStruc is an " + dataStruc.getClass().getSimpleName() +
                         ", no String-based update support");
        }
    }

    protected Object getNative() {
        if (child != null) { return child.getNative(); }
        if (parsedVal == null) { return null; }
        if (parsedVal == NULL) { return null; }
        if (parsedVal instanceof JSONArray) {
            JSONArray array = (JSONArray) parsedVal;
            if (array.length() < 1) { return null; }
            return array;
        }
        return parsedVal;
    }

    protected void filterByNodeName(String nodeName, JSONArray array, JSONArray matches) {
        // array.forEach(item -> filterByNodeName(nodeName, item, matches));
        for (int i = 0; i < array.length(); i++) { filterByNodeName(nodeName, array.opt(i), matches); }
    }

    protected void filterByNodeName(String nodeName, Object obj, JSONArray matches) {
        if (isSimpleType(obj) && obj.toString().equals(nodeName)) {
            matches.put(obj);
            return;
        }

        if (obj instanceof JSONObject) {
            JSONObject json = (JSONObject) obj;
            Object nodeValue = json.opt(nodeName);
            if (nodeValue != null) { matches.put(nodeValue); }
            return;
        }

        if (obj instanceof JSONArray) {
            filterByNodeName(nodeName, (JSONArray) obj, matches);
            return;
        }
    }

    private void modify(JSONArray array, String modifyWith, Option option) throws JSONException {
        if (array == null || option == null) { return; }

        JsonPathFilters filters = null;
        String nameKey = null;
        int indexKey = -1;
        if (TextUtils.isBetween(key, "[", "]")) {
            String enclosedFilter = cleanNodeName(StringUtils.removeEnd(StringUtils.removeStart(key, "["), "]"));
            if (NumberUtils.isDigits(enclosedFilter)) {
                indexKey = NumberUtils.toInt(enclosedFilter);
            } else {
                // ordinal index will be handled separately later..
                filters = new JsonPathFilters(enclosedFilter);
            }
        } else {
            nameKey = cleanNodeName(key);
        }

        switch (option) {
            case DELETE: {
                if (indexKey != -1) {
                    array.remove(indexKey);
                    return;
                }

                for (int i = 0; i < array.length(); i++) {
                    Object o = array.get(i);
                    if (o == null) { continue; }

                    if (StringUtils.isNotEmpty(nameKey)) {
                        if (deleteMatch(array, o, i, nameKey)) { i--; }
                        continue;
                    }

                    if (filters != null) {
                        if (filters.find(o) != null) {
                            array.remove(i);
                            i--;
                        }
                        continue;
                    }
                }

                return;
            }

            case OVERWRITE_OR_ADD:
            case OVERWRITE:
            case APPEND:
            case PREPEND: {
                if (indexKey != -1) {
                    array.put(indexKey, option.handle(parsedVal, modifyWith));
                    return;
                }

                boolean matched = false;
                for (int i = 0; i < array.length(); i++) {
                    Object obj = array.get(i);
                    if (obj == null) { continue; }

                    if (nameKey != null) {
                        if (isSimpleType(obj)) {
                            if (StringUtils.equals(obj.toString(), nameKey)) {
                                array.put(i, option.handle(obj.toString(), modifyWith));
                                matched = true;
                            }
                            continue;
                        }

                        if (obj instanceof JSONObject) {
                            JSONObject json = (JSONObject) obj;
                            if (json.has(nameKey)) {
                                json.put(nameKey, option.handle(json.opt(nameKey), modifyWith));
                                matched = true;
                            }
                            continue;
                        }

                        if (obj instanceof JSONArray) {
                            modify(((JSONArray) obj), modifyWith, option);
                            continue;
                        }
                    } else if (filters != null) {
                        Object newValue = filters.modify(obj, modifyWith, option);
                        if (newValue != null) { array.put(i, newValue); }
                    }
                }

                // if no match for nameKey (not filter) and we are asking for "overwrite or add", then let's "add"
                if (!matched && StringUtils.isNotEmpty(nameKey) && option == OVERWRITE_OR_ADD) {
                    String source = "{\"" + nameKey + "\":" +
                                    (modifyWith == null ? "null" : "\"" + modifyWith + "\"") + "}";
                    array.put(new JSONObject(source));
                }

                return;
            }

            default: {
                logger.debug("key=" + key + " for a JSON Array, but action " + option + " is not supported");
            }
        }
    }

    private boolean deleteMatch(JSONArray array, Object matchTarget, int objectPosition, String nodeName) {
        if (JsonUtils.isSimpleType(matchTarget) && matchTarget.toString().equals(nodeName)) {
            array.remove(objectPosition);
            return true;
        }

        if (matchTarget instanceof JSONObject) {
            JSONObject json = (JSONObject) matchTarget;
            if (json.opt(nodeName) != null) {
                // array.remove(i);
                json.remove(nodeName);
                return true;
            }
        }

        if (matchTarget instanceof JSONArray) {
            JSONArray matchTargetArray = (JSONArray) matchTarget;
            for (int i = 0; i < matchTargetArray.length(); i++) {
                deleteMatch(matchTargetArray, matchTargetArray.get(i), i, nodeName);
                // no need to return true (which will delete this array), since we are already deleting the matched node
            }
        }

        return false;
    }

    private void delete(JsonPathFilters filters) throws JSONException {
        if (dataStruc instanceof JSONObject) {
            if (filters != null) {
                filters.removeMatches((JSONObject) dataStruc);
            } else {
                ((JSONObject) dataStruc).remove(key);
            }
        }
    }

    /**
     * parse json path into 2 components - current and next
     *
     * @return a pair of 'current' and 'next' path
     */
    private Pair<JSONPathKey, String> parseKey() {
        // fail-fast to avoid NPE
        if (StringUtils.isBlank(key)) { return null; }

        preParseSubstitution();

        String current;

        // special case of index key, where the key contains only [...]
        if (StringUtils.startsWith(key, "[") && StringUtils.contains(key, "]")) {
            current = postParseSubstitution(fromIndexToSimpleKey(key));
            // there could be multiple sets of [...], like [s][0]

            JSONPathKey currentKey = new JSONPathKey();
            currentKey.nodeName = current;
            currentKey.isIndexOrdinal = NumberUtils.isDigits(currentKey.nodeName);
            currentKey.isFilter = true;
            return new ImmutablePair<>(currentKey, cleanNextKey(StringUtils.substringAfter(key, "]")));
        }

        // key does not contain _just_ indexed key
        current = StringUtils.substringBefore(key, ".");

        if (StringUtils.contains(current, "[")) {
            // composite key found. e.g. a[b], a[2], a[c.d]
            if (StringUtils.endsWith(current, "]")) {
                // this composite key does not contains dot-based name
                current = StringUtils.substringBefore(current, "[");
            } else {
                // special case where array reference containing dot. ie. key1[key2.key3] or key4[5]
                current = StringUtils.substringBefore(key, "[");
            }

            current = postParseSubstitution(current);

            JSONPathKey currentKey = new JSONPathKey();
            currentKey.nodeName = current;
            currentKey.isIndexOrdinal = NumberUtils.isDigits(current);
            currentKey.isFilter = false;
            return new ImmutablePair<>(currentKey, cleanNextKey(StringUtils.substringAfter(key, current)));
        }

        // key does not contain indexed key
        current = postParseSubstitution(current);
        JSONPathKey currentKey = new JSONPathKey();
        currentKey.nodeName = current;
        currentKey.nodeIndex = null;
        currentKey.isFilter = false;

        if (StringUtils.equals(key, current)) { return new ImmutablePair<>(currentKey, null); }

        return new ImmutablePair<>(currentKey, cleanNextKey(StringUtils.substringAfter(key, current)));
    }

    private String postParseSubstitution(String data) {
        if (StringUtils.isEmpty(data)) { return data; }
        for (Map.Entry<String, String> subst : ESCAPED_CHARS_REPLACER.entrySet()) {
            data = StringUtils.replace(data, subst.getValue(), StringUtils.removeStart(subst.getKey(), "\\"));
        }
        return data;
    }

    private void preParseSubstitution() {
        for (Map.Entry<String, String> subst : ESCAPED_CHARS_REPLACER.entrySet()) {
            key = StringUtils.replace(key, subst.getKey(), subst.getValue());
        }
    }

    private static String cleanNextKey(String next) {
        if (StringUtils.startsWith(next, ".")) { next = next.substring(1); }
        return next;
    }

    private Object resolveArrayRef(JSONArray jsonArray, JSONPathKey jsonPathKey) {
        if (jsonArray.length() < 1) { return null; }

        String key = jsonPathKey.nodeName;

        // json array is not empty, is it an indexed key (numeric)?
        if (NumberUtils.isDigits(key)) { return jsonArray.opt(NumberUtils.toInt(key)); }

        JSONArray matches = new JSONArray();

        boolean isFilter = jsonPathKey.isFilter;
        if (isFilter) {
            JsonPathFilters filters = new JsonPathFilters(key);
            // jsonArray.forEach(item -> filters.filter(item, matches));
            for (int i = 0; i < jsonArray.length(); i++) { filters.filter(jsonArray.opt(i), matches);}
        } else {
            filterByNodeName(key, jsonArray, matches);
        }

        if (matches.length() == 0) { return null; }
        if (matches.length() == 1) { return matches.opt(0); }
        return matches;
    }

    private static String cleanNodeName(String nodeName) {
        if (TextUtils.isBetween(nodeName, "'", "'")) { return StringUtils.substringBetween(nodeName, "'"); }
        if (TextUtils.isBetween(nodeName, "\"", "\"")) { return StringUtils.substringBetween(nodeName, "\""); }
        return nodeName;
    }

    private void init() {
        // fail-fast to avoid NPE
        if (dataStruc == null) {
            parsedVal = null;
            return;
        }

        Pair<JSONPathKey, String> keyPair = parseKey();

        // put it back, now that we've figured out the JSONPathKey
        key = postParseSubstitution(key);

        // fail-fast to avoid NPE
        if (keyPair == null) { return; }

        JSONPathKey current = keyPair.getKey();
        String nextKey = keyPair.getValue();

        // key can be either (1) node name, (2) ordinal node index, and (3) named node index

        if (StringUtils.isNotBlank(current.nodeName)) { resolveValue(current); }

        if (StringUtils.isNotBlank(current.nodeIndex)) {
            // next key might be array index or simple key
            // if array index, then we just append to childKey,
            // if simple key, we need to add "." to childKey first
            String childKey = "[" + current.nodeIndex + "]" +
                              (StringUtils.isNotBlank(nextKey) && !StringUtils.startsWith(nextKey, "[") ? "." : "") +
                              StringUtils.defaultString(nextKey);
            this.child = new JSONPath(parsedVal, postParseSubstitution(childKey), this);
            return;
        }

        // now that we got the parsed value, it might be used by the child key (if any)
        if (StringUtils.isNotBlank(nextKey) && (parsedVal instanceof JSONArray || parsedVal instanceof JSONObject)) {
            this.child = new JSONPath(parsedVal, postParseSubstitution(nextKey), this);
        }
    }

    private void resolveValue(JSONPathKey jsonPathKey) {
        if (dataStruc instanceof JSONArray) {
            parsedVal = resolveArrayRef((JSONArray) dataStruc, jsonPathKey);
            return;
        }

        // not ordinal index, hence the node index can be
        //  (1) child structure,
        //  (2) index key of an array,
        //  (3) key of simple value
        if (dataStruc instanceof JSONObject) {
            JSONObject json = (JSONObject) dataStruc;
            parsedVal = jsonPathKey.isFilter ?
                        new JsonPathFilters(jsonPathKey.nodeName).find(json) : json.opt(jsonPathKey.nodeName);
            return;
        }

        parsedVal = null;
    }

    private String fromIndexToSimpleKey(String key) {
        return cleanNodeName(StringUtils.substringBetween(key, "[", "]"));
    }

    private <T> T toArray(Class<T> type, JSONArray jsonArray) {
        Object array = Array.newInstance(type.getComponentType(), jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) { Array.set(array, i, jsonArray.opt(i)); }
        return (T) array;
    }

    private static JSONObject change(JSONObject json, String path, String data, Option option) throws JSONException {
        JSONPath jsonPath = new JSONPath(json, path);
        jsonPath.modify(data, option);
        return (JSONObject) jsonPath.dataStruc;
    }

    private static JSONArray change(JSONArray json, String path, String data, Option option) throws JSONException {
        JSONPath jsonPath = new JSONPath(json, path);
        jsonPath.modify(data, option);
        return (JSONArray) jsonPath.dataStruc;
    }

    private static JSONObject overwrite(JSONObject json, String path, String data, Option option, boolean blankOnly)
        throws JSONException {
        return (JSONObject) _overwrite(json, path, data, option, blankOnly);
    }

    private static JSONArray overwrite(JSONArray json, String path, String data, Option option, boolean blankOnly)
        throws JSONException {
        return (JSONArray) _overwrite(json, path, data, option, blankOnly);
    }

    private static Object _overwrite(Object json, String path, String data, Option option, boolean blankOnly)
        throws JSONException {
        if (json == null) { return null; }

        JSONPath jsonPath = null;
        if (json instanceof JSONObject) { jsonPath = new JSONPath((JSONObject) json, path); }
        if (json instanceof JSONArray) { jsonPath = new JSONPath((JSONArray) json, path); }
        if (jsonPath == null) { throw new JSONException("Unable create JsonPath via " + json.getClass()); }

        String value = jsonPath.get();
        if (StringUtils.isNotEmpty(value) && blankOnly) { return json; }

        jsonPath.modify(data, option);
        return jsonPath.dataStruc;
    }

}
