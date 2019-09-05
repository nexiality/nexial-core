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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.utils.JSONPath.Option;

import static org.nexial.core.NexialConst.PREFIX_REGEX;
import static org.nexial.core.utils.JsonUtils.isSimpleType;

class JsonPathFilters {
    private static final String CONDITION_AND = " AND ";
    private static final String EQUAL = "=";

    private String original;
    private List<FilterKey> filters = new ArrayList<>();

    private static class FilterKey {
        private String original;
        private String key;
        private String value;
        private boolean regexOnKey;
        private boolean regexOnValue;

        public FilterKey(String original) {
            if (StringUtils.isBlank(original)) { throw new IllegalArgumentException("filter key is empty/blank"); }

            this.original = original;
            if (StringUtils.contains(original, EQUAL)) {
                parseValue(StringUtils.substringAfter(original, EQUAL));
                parseKey(StringUtils.substringBefore(original, EQUAL));
            } else {
                regexOnValue = false;
                parseKey(original);
            }
        }

        public String getOriginal() { return original; }

        public String getKey() { return key; }

        public String getValue() { return value; }

        public boolean isRegexOnKey() { return regexOnKey; }

        public boolean isRegexOnValue() { return regexOnValue; }

        @Override
        public String toString() { return original; }

        protected void parseValue(String value) {
            regexOnValue = StringUtils.startsWith(value, PREFIX_REGEX);
            this.value = regexOnValue ? StringUtils.substringAfter(value, PREFIX_REGEX) : value;
        }

        protected void parseKey(String key) {
            regexOnKey = StringUtils.startsWith(key, PREFIX_REGEX);
            this.key = regexOnKey ? StringUtils.substringAfter(key, PREFIX_REGEX) : key;
        }

        protected boolean accept(String jsonValue) {
            return !StringUtils.isEmpty(jsonValue) &&
                   (regexOnKey ? RegexUtils.isExact(jsonValue, key) : StringUtils.equals(jsonValue, key));
        }

        protected boolean accept(JSONObject json) {
            if (json == null) { return false; }

            Iterator childKeys = json.keys();
            while (childKeys.hasNext()) {
                String childKey = (String) childKeys.next();

                if (regexOnKey && RegexUtils.isExact(childKey, key) ||
                    !regexOnKey && StringUtils.equals(childKey, key)) {

                    // at this point, key matched.
                    // if value also match, then we found our guy...

                    if (StringUtils.isEmpty(value)) {
                        // empty value means *any* value would be fine
                        return true;
                    }

                    Object childValue = json.opt(childKey);
                    if (childValue == null) {
                        break;
                    }

                    if (regexOnValue && RegexUtils.isExact(childValue.toString(), value) ||
                        (!regexOnValue && StringUtils.equals(childValue.toString(), value))) {
                        return true;
                    }
                }
            }

            return false;
        }

        protected boolean accept(JSONArray jsonArray) {
            if (jsonArray == null || jsonArray.length() < 1) { return false; }

            for (int i = 0; i < jsonArray.length(); i++) {
                Object childObject = jsonArray.opt(i);
                if (childObject == null) { continue; }
                if (isSimpleType(childObject)) { if (accept((String) childObject)) { return true; } }
                if (childObject instanceof JSONObject) { if (accept((JSONObject) childObject)) { return true; } }
                if (childObject instanceof JSONArray) { return accept((JSONArray) childObject); }
                ConsoleUtils.log("JSONPath currently does not support filter on nested array");
            }

            return false;
        }
    }

    public JsonPathFilters(String original) {
        if (StringUtils.isBlank(original)) { throw new IllegalArgumentException("filter key is empty/blank"); }

        this.original = original;
        Arrays.stream(StringUtils.splitByWholeSeparator(original, CONDITION_AND))
              .forEach(filterKey -> filters.add(new FilterKey(filterKey)));
    }

    public void filter(Object candidate, JSONArray matched) {
        if (candidate == null) { return; }

        // no filters means all in!
        if (CollectionUtils.isEmpty(filters)) { matched.put(candidate); }

        if (isSimpleType(candidate)) {
            filterSimpleType(candidate, matched);
            return;
        }

        if (candidate instanceof JSONObject) {
            filter((JSONObject) candidate, matched);
            return;
        }

        if (candidate instanceof JSONArray) {
            JSONArray array = (JSONArray) candidate;
            if (array.length() < 1) { return; }
            for (Object child : array) { filter(child, matched); }
            // filter(array, matched);
            return;
        }

        throw new IllegalArgumentException("Unknown/unsupported type: " + candidate.getClass().getSimpleName());
    }

    public Object find(Object candidate) {
        if (candidate == null) { return null; }

        // no filters means all in!
        if (CollectionUtils.isEmpty(filters)) { return candidate; }

        if (isSimpleType(candidate)) { return StringUtils.isNotEmpty(find(candidate.toString())) ? candidate : null; }

        JSONArray matched = new JSONArray();

        if (candidate instanceof JSONObject) { return find((JSONObject) candidate, matched); }

        if (candidate instanceof JSONArray) {
            find((JSONArray) candidate, matched);
            if (matched.length() == 0) { return null; }
            if (matched.length() == 1) { return matched.opt(0); }
            return matched;
        }

        throw new IllegalArgumentException("Unknown/unsupported type: " + candidate.getClass().getSimpleName());
    }

    @Override
    public String toString() { return original; }

    protected void find(JSONArray array, JSONArray matched) {
        if (array.length() < 1) { return; }

        for (int i = 0; i < array.length(); i++) {
            Object childObject = array.opt(i);
            if (childObject == null) { continue; }

            // if (childObject instanceof String) {
            if (isSimpleType(childObject)) {
                if (StringUtils.isNotEmpty(find(((String) childObject)))) { matched.put(childObject); }
                continue;
            }

            if (childObject instanceof JSONObject) {
                find(((JSONObject) childObject), matched);
                continue;
            }

            if (childObject instanceof JSONArray) {
                find(((JSONArray) childObject), matched);
                // continue;
            }
        }
    }

    protected Object find(JSONObject json, JSONArray matched) {
        Iterator childKeys = json.keys();
        while (childKeys.hasNext()) {
            String childKey = (String) childKeys.next();
            Object childValue = json.opt(childKey);
            if (isMatched(childKey, childValue)) { matched.put(childValue); }
        }

        if (matched.length() == 0) { return null; }
        if (matched.length() == 1) { return matched.opt(0); }
        return matched;
    }

    protected String find(String value) {
        boolean accepted = true;
        for (FilterKey key : filters) {
            if (!key.accept(value)) {
                accepted = false;
                break;
            }
        }
        return accepted ? value : null;
    }

    protected void filterSimpleType(Object simpleObj, JSONArray matched) {
        if (simpleObj == null) { return; }

        if (CollectionUtils.isEmpty(filters)) {
            matched.put(simpleObj);
            return;
        }

        String value = simpleObj.toString();

        boolean accepted = true;
        for (FilterKey key : filters) {
            if (!key.accept(value)) {
                accepted = false;
                break;
            }
        }

        if (accepted) { matched.put(simpleObj); }
    }

    protected void filter(JSONObject json, JSONArray matched) {
        if (json.length() < 1) { return; }

        if (CollectionUtils.isEmpty(filters)) {
            matched.put(json);
            return;
        }

        if (CollectionUtils.size(filters) == 1) {
            // special case: reference to node name (ONLY) means "include that node as is, as part of filter"
            FilterKey filterKey = filters.get(0);
            if (!filterKey.regexOnKey && !filterKey.regexOnValue && StringUtils.isEmpty(filterKey.value)) {
                Object childJson = json.opt(filterKey.getKey());
                if (childJson != null) {
                    matched.put(childJson);
                    return;
                }
            }
        }

        boolean isMatched = true;
        for (FilterKey filter : filters) {
            if (!filter.accept(json)) {
                isMatched = false;
                break;
            }
        }

        if (isMatched) { matched.put(json); }
    }

    protected JSONObject removeMatches(JSONObject json) {
        if (json == null) { return json; }

        Iterator childKeys = json.keys();
        while (childKeys.hasNext()) {
            String childKey = (String) childKeys.next();
            Object childValue = json.opt(childKey);
            if (isMatched(childKey, childValue)) { json.remove(childKey);}
        }

        return json;
    }

    protected Object modify(Object obj, String modifyWith, Option option) {
        if (obj == null) { return null; }

        if (isSimpleType(obj)) {
            String value = obj.toString();
            boolean matched = true;
            for (FilterKey key : filters) {
                if (!key.accept(value)) {
                    matched = false;
                    break;
                }
            }

            return matched ? option.handle(obj.toString(), modifyWith) : null;
        }

        if (obj instanceof JSONObject) {
            JSONObject json = (JSONObject) obj;

            if (CollectionUtils.size(filters) == 1) {
                // special case: reference to node name (ONLY) means "include that node as is, as part of filter"
                FilterKey filterKey = filters.get(0);
                if (!filterKey.regexOnKey && !filterKey.regexOnValue && StringUtils.isEmpty(filterKey.value)) {
                    String nameKey = filterKey.getKey();
                    Object value = json.opt(nameKey);
                    if (value != null) {
                        json.put(nameKey, option.handle(value, modifyWith));
                        return null;
                    }
                }
            }

            Iterator<String> childKeys = json.keys();
            while (childKeys.hasNext()) {
                String nameKey = childKeys.next();
                Object value = json.opt(nameKey);
                if (isMatched(nameKey, value)) {
                    json.put(nameKey, option.handle(value, modifyWith));
                }
            }

            return null;
        }

        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                Object newValue = modify(item, modifyWith, option);
                if (newValue != null) { array.put(i, newValue); }
            }
            return null;
        }

        return null;
    }

    protected boolean isMatched(String childKey, Object childValue) {
        if (CollectionUtils.isEmpty(filters)) { return true; }

        boolean foundMatchingProp = true;
        for (FilterKey key : filters) {
            String filterKey = key.key;
            boolean regexOnKey = key.regexOnKey;
            String filterValue = key.value;
            boolean regexOnValue = key.regexOnValue;

            if (regexOnKey && !RegexUtils.isExact(childKey, filterKey)) {
                foundMatchingProp = false;
                break;
            }

            if (!regexOnKey && !StringUtils.equals(childKey, filterKey)) {
                foundMatchingProp = false;
                break;
            }

            if (StringUtils.isEmpty(filterValue)) { break; }

            if (childValue == null) {
                foundMatchingProp = false;
                break;
            }

            if (regexOnValue && !RegexUtils.isExact(childValue.toString(), filterValue)) {
                foundMatchingProp = false;
                break;
            }

            if (!regexOnValue && !StringUtils.equals(childValue.toString(), filterValue)) {
                foundMatchingProp = false;
                break;
            }
        }

        return foundMatchingProp;
    }
}
