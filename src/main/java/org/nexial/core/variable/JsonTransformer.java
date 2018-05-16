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

package org.nexial.core.variable;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.JSONPath;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import static org.json.JSONObject.NULL;
import static org.nexial.core.variable.JsonDataType.GSON;

public class JsonTransformer<T extends JsonDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(JsonTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, JsonTransformer.class, JsonDataType.class);

    public TextDataType text(T data) { return super.text(data); }

    public ListDataType list(T data) {
        if (data == null || data.getValue() == null) { return null; }

        try {
            if (data.getValue() instanceof JsonArray) {
                JsonArray array = (JsonArray) data.getValue();
                return new ListDataType(StringUtils.substringBetween(array.toString(), "[", "]"));
            }

            if (data.getValue() instanceof JsonPrimitive) {
                String value = data.getValue().getAsString();
                if (TextUtils.isBetween(value, "[", "]")) {
                    return new ListDataType(StringUtils.substringBetween(value, "[", "]"));
                }
                if (TextUtils.isBetween(value, "(", ")")) {
                    return new ListDataType(StringUtils.substringBetween(value, "(", ")"));
                }
                if (TextUtils.isBetween(value, "{", "}")) {
                    return new ListDataType(StringUtils.substringBetween(value, "{", "}"));
                }

                return new ListDataType(value);
            }
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Error converting to LIST from " + data.getTextValue(), e);
        }

        ConsoleUtils.log("Unable to convert to LIST via " + data.getValue().getClass());
        return null;
    }

    public ExpressionDataType extract(T data, String jsonpath) throws ExpressionException {
        if (data == null || data.getValue() == null || StringUtils.isBlank(jsonpath)) { return null; }

        JsonElement value = data.getValue();

        try {
            if (value instanceof JsonObject) {
                return handleJsonPathResult(data, JSONPath.find(data.toJSONObject(), jsonpath));
            }
            if (value instanceof JsonArray) {
                return handleJsonPathResult(data, JSONPath.find(data.toJSONArray(), jsonpath));
            }

            throw new ExpressionException("Unable to transform " + value.getClass().getSimpleName() + " instance");
        } catch (JSONException e) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to JSON: " + e.getMessage(), e);
        }
    }

    public ExpressionDataType replace(T data, String jsonpath, String replace) throws ExpressionException {
        if (data == null || data.getValue() == null || StringUtils.isBlank(jsonpath)) { return null; }

        JsonElement value = data.getValue();

        if (value instanceof JsonNull) { return data; }

        if (value instanceof JsonPrimitive) {
            return new TextDataType(StringUtils.replace(value.getAsString(), jsonpath, replace));
        }

        try {
            if (value instanceof JsonObject) {
                return handleJsonPathResult(data, JSONPath.overwrite(data.toJSONObject(), jsonpath, replace, false));
            }

            if (value instanceof JsonArray) {
                return handleJsonPathResult(data, JSONPath.overwrite(data.toJSONArray(), jsonpath, replace, false));
            }

            throw new ExpressionException("Unable to transform " + value.getClass().getSimpleName() + " instance");
        } catch (JSONException e) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to JSON: " + e.getMessage(), e);
        }
    }

    public ExpressionDataType remove(T data, String jsonpath) throws ExpressionException {
        if (data == null || data.getValue() == null || StringUtils.isBlank(jsonpath)) { return null; }

        JsonElement value = data.getValue();

        if (value instanceof JsonNull) { return data; }

        if (value instanceof JsonPrimitive) {
            return new TextDataType(StringUtils.remove(value.getAsString(), jsonpath));
        }

        try {
            if (value instanceof JsonObject) {
                return handleJsonPathResult(data, JSONPath.delete(data.toJSONObject(), jsonpath));
            }

            if (value instanceof JsonArray) {
                return handleJsonPathResult(data, JSONPath.delete(data.toJSONArray(), jsonpath));
            }

            throw new ExpressionException("Unable to transform " + value.getClass().getSimpleName() + " instance");
        } catch (JSONException e) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to JSON: " + e.getMessage(), e);
        }
    }

    public NumberDataType count(T data, String jsonPath) throws ExpressionException {
        NumberDataType count = new NumberDataType("0");
        if (data == null || data.getValue() == null || StringUtils.isBlank(jsonPath)) { return count; }

        JsonElement value = data.getValue();

        try {
            if (value instanceof JsonArray) {
                JSONArray jsonArray = new JSONArray(value.toString());
                JSONPath jp = new JSONPath(jsonArray, jsonPath);
                count.setValue(jp.count());
                count.setTextValue(count.getValue().toString());
                return count;
            }

            if (value instanceof JsonObject) {
                JSONObject jsonObject = new JSONObject(value.toString());
                JSONPath jp = new JSONPath(jsonObject, jsonPath);
                count.setValue(jp.count());
                count.setTextValue(count.getValue().toString());
                return count;
            }

            throw new ExpressionException("Unable to transform " + value.getClass().getSimpleName() + " instance");
        } catch (JSONException jsonException) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to JSON: " + jsonException.getMessage(), jsonException);
        }
    }

    public T pack(T data) {
        if (data == null || data.getValue() == null) { return null; }

        String jsonString = GSON.toJson(data.getValue());
        data.setValue(GSON.fromJson(jsonString, JsonElement.class));
        data.setTextValue(data.getValue().toString());
        return data;
    }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    public ExpressionDataType save(T data, String path) { return super.save(data, path); }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    // todo: merge

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected ExpressionDataType handleJsonPathResult(T data, JSONObject json) {
        if (json == null || json == NULL) { return TextDataType.newEmptyInstance(); }
        return handleJsonPathResult(data, StringUtils.trim(json.toString()));
    }

    protected ExpressionDataType handleJsonPathResult(T data, JSONArray json) {
        if (json == null || json == NULL) { return TextDataType.newEmptyInstance(); }
        return handleJsonPathResult(data, StringUtils.trim(json.toString()));
    }

    protected ExpressionDataType handleJsonPathResult(T data, String extracted) {
        TextDataType returnType = TextDataType.newEmptyInstance();

        if (StringUtils.isBlank(extracted)) {
            returnType.setValue(extracted);
            return returnType;
        }

        if (TextUtils.isBetween(extracted, "[", "]")) {
            // possibly json array
            data.setValue(GSON.fromJson(extracted, JsonArray.class));
            data.setTextValue(extracted);
            return data;
        }

        if (TextUtils.isBetween(extracted, "{", "}")) {
            // possibly json object
            data.setValue(GSON.fromJson(extracted, JsonObject.class));
            data.setTextValue(extracted);
            return data;
        }

        returnType.setValue(extracted);
        return returnType;
    }
}
