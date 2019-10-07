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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.json.JsonCommand;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.JSONPath;
import org.nexial.core.utils.JsonEditor;
import org.nexial.core.utils.JsonEditor.JsonEditorConfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.MalformedJsonException;

import static org.json.JSONObject.NULL;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.NexialConst.Data.TREAT_JSON_AS_IS;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;

public class JsonTransformer<T extends JsonDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(JsonTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, JsonTransformer.class, JsonDataType.class);
    private static final String TEMP_TEXT_DELIM = "$~~TD~~$";

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

    public ExpressionDataType save(T data, String path, String append) { return super.save(data, path, append); }

    public ExpressionDataType addOrReplace(T data, String jsonpath, String input) {
        if (data == null || data.getValue() == null || StringUtils.isBlank(input)) { return null; }

        JsonElement value = data.getValue();
        String jsonString = value.toString();

        JsonEditorConfig config = new JsonEditorConfig();
        config.setRemoveNull(true);
        JsonEditor editor = JsonEditor.newInstance(config);

        Object modified = editor.add(jsonString, jsonpath, input);
        if (modified == null || modified == NULL) {
            data.setTextValue(null);
            data.setValue(JsonNull.INSTANCE);
            return data;
        }

        return handleJsonPathResult(data, modified.toString());
    }

    public T minify(T data) {
        if (data == null || data.getValue() == null) { return null; }

        String compressed = GSON_COMPRESSED.toJson(data.getValue());
        data.setValue(GSON_COMPRESSED.fromJson(compressed, JsonElement.class));
        data.setTextValue(compressed);
        return data;
    }

    public T beautify(T data) {
        if (data == null || data.getValue() == null) { return null; }

        String beautified = GSON.toJson(data.getValue());
        data.setValue(GSON.fromJson(beautified, JsonElement.class));
        data.setTextValue(beautified);
        return data;
    }

    public T compact(T data, String removeEmpty) {
        if (data == null || data.getValue() == null) { return null; }

        JsonElement jsonElement = GSON_COMPRESSED.fromJson(data.getValue(), JsonElement.class);
        requiresNotNull(jsonElement, "invalid json", data.getValue());

        jsonElement = JsonCommand.removeEmpty(jsonElement, !BooleanUtils.toBoolean(removeEmpty));

        String compressed = GSON_COMPRESSED.toJson(jsonElement);
        data.setValue(GSON_COMPRESSED.fromJson(compressed, JsonElement.class));
        data.setTextValue(compressed);
        return data;
    }

    public CsvDataType select(T data, String... jsonpaths) throws ExpressionException {
        if (data == null || data.getValue() == null) { return null; }
        if (ArrayUtils.isEmpty(jsonpaths)) { return null; }

        StringBuilder buffer = new StringBuilder();
        Arrays.stream(jsonpaths).forEach(jsonpath -> {
            try {
                jsonpath = ExpressionUtils.handleExternal("TEXT", jsonpath);
                if (StringUtils.isNotBlank(jsonpath)) { buffer.append("\n").append(jsonpath); }
            } catch (TypeConversionException e) {
                ConsoleUtils.error("Invalid jsonpath - " + jsonpath + ": " + e.getMessage());
            }
        });

        String compoundJsonPaths = StringUtils.trim(buffer.toString());
        if (StringUtils.isBlank(compoundJsonPaths)) { return null; }

        ExecutionContext context = ExecutionThread.get();
        String textDelim = context == null ? getDefault(TEXT_DELIM) : context.getTextDelim();

        compoundJsonPaths = StringUtils.replace(compoundJsonPaths, "\\" + textDelim, TEMP_TEXT_DELIM);
        compoundJsonPaths = StringUtils.replace(compoundJsonPaths, textDelim, "\n");
        List<String> jsonpathList = TextUtils.toList(compoundJsonPaths, "\n", true);
        StringBuilder output = new StringBuilder();

        /*
        [JSON(${json}) =>
            export(
                summary[payCode=OT].duration,
                COUNT::summary[payCode=OT],
                grossAmount,
                SUM::summary[payCode=OT].flatAmount
                summary[payCode=OT].flatAmount -> sum
            )

            // now a CSV doc in memory:
            // summary[payCode=OT].duration         [8.0,7.0]
            // COUNT::summary[payCode=OT]           2
            // grossAmount                          3415.829994
            // SUM::summary[payCode=OT].flatAmount  1180.053999379

            save(${external_csv_file})
        ]
        */
        boolean defaultAsIs = getDefaultBool(TREAT_JSON_AS_IS);
        boolean jsonAsIs = context == null ? defaultAsIs : context.getBooleanData(TREAT_JSON_AS_IS, defaultAsIs);
        JsonElement value = data.getValue();
        jsonpathList.forEach(jsonpath -> {
            jsonpath = StringUtils.trim(StringUtils.replace(jsonpath, TEMP_TEXT_DELIM, textDelim));
            output.append(jsonpath).append(textDelim);
            if (value instanceof JsonObject) { output.append(JSONPath.find(data.toJSONObject(), jsonpath, !jsonAsIs)); }
            if (value instanceof JsonArray) { output.append(JSONPath.find(data.toJSONArray(), jsonpath, !jsonAsIs)); }
            output.append("\r\n");
        });

        CsvDataType returnObj = new CsvDataType(output.toString());
        returnObj.setHeader(false);
        returnObj.setDelim(textDelim);
        returnObj.parse();
        return returnObj;
    }

    public ListDataType keys(T data, String jsonpath) throws ExpressionException {
        ListDataType empty = new ListDataType();

        if (data == null) { return empty; }

        try {
            if (data.getValue() != null && data.getValue() instanceof JsonObject && StringUtils.isBlank(jsonpath)) {
                return new ListDataType(JSONPath.keys(data.toJSONObject(), jsonpath).toArray(new String[0]));
            }

            return empty;
        } catch (JSONException e) {
            throw new TypeConversionException(data.getName(), data.getTextValue(),
                                              "Error converting to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    @Override
    protected void saveContentAsAppend(ExpressionDataType data, File target) throws IOException {
        if (!FileUtil.isFileReadable(target, 1) || !(data instanceof JsonDataType)) {
            super.saveContentAsAppend(data, target);
            return;
        }

        String content = StringUtils.trim(FileUtils.readFileToString(target, DEF_FILE_ENCODING));
        if (StringUtils.startsWith(content, "[") && StringUtils.endsWith(content, "]")) {
            String newContent = StringUtils.trim(data.getTextValue());

            if (TextUtils.isBetween(newContent, "[", "]")) {
                newContent = StringUtils.removeStart(StringUtils.removeEnd(newContent, "]"), "[");
                FileUtils.writeStringToFile(target,
                                            StringUtils.removeEnd(content, "]") + "," + newContent + "]",
                                            DEF_FILE_ENCODING);
                return;
            }

            if (TextUtils.isBetween(newContent, "{", "}")) {
                FileUtils.writeStringToFile(target,
                                            StringUtils.removeEnd(content, "]") + "," + newContent + "]",
                                            DEF_FILE_ENCODING);
                return;
            }

            throw new MalformedJsonException("Unknown JSON structure: " + newContent);
        }

        if (StringUtils.startsWith(content, "{") && StringUtils.endsWith(content, "}")) {
            String newContent = StringUtils.trim(data.getTextValue());

            if (TextUtils.isBetween(newContent, "{", "}")) {
                newContent = StringUtils.removeStart(StringUtils.removeEnd(newContent, "}"), "{");
                FileUtils.writeStringToFile(target,
                                            StringUtils.removeEnd(content, "}") + "," + newContent + "}",
                                            DEF_FILE_ENCODING);
                return;
            }

            if (TextUtils.isBetween(newContent, "[", "]")) {
                ConsoleUtils.log("can't merge JSON array into a JSON document file");
                throw new MalformedJsonException("JSON array cannot be merged into a JSON document");
            }

            throw new MalformedJsonException("Unknown JSON structure: " + newContent);
        }
    }

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
