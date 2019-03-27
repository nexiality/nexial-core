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

package org.nexial.core.plugins.json;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.*;
import org.nexial.core.utils.JsonEditor.JsonEditorConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.utils.CheckUtils.*;

/**
 *
 */
public class JsonCommand extends BaseCommand {
    private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = JsonSchemaFactory.byDefault();

    @Override
    public String getTarget() { return "json"; }

    public StepResult assertEqual(String expected, String actual) {
        if (expected == null && actual == null) { return StepResult.success("Both EXPECTED and ACTUAL are null"); }
        if (StringUtils.equals(expected, actual)) { return StepResult.success("Both EXPECTED and ACTUAL are the same");}

        String expectedJson;
        try {
            expectedJson = OutputFileUtils.resolveContent(expected, context, true, true);
        } catch (IOException e) {
            return StepResult.fail("EXPECTED json is invalid or not readable: " + e.getMessage());
        }

        String actualJson;
        try {
            actualJson = OutputFileUtils.resolveContent(actual, context, true, true);
        } catch (IOException e) {
            return StepResult.fail("ACTUAL json is invalid or not readable: " + e.getMessage());
        }

        // maybe expected or actual are NOT json doc or array...
        if ((TextUtils.isBetween(expectedJson, "{", "}") || TextUtils.isBetween(expectedJson, "[", "]")) &&
            (TextUtils.isBetween(actualJson, "{", "}") || TextUtils.isBetween(actualJson, "[", "]"))) {
            JsonParser jsonParser = new JsonParser();
            return assertEqual(jsonParser.parse(expectedJson), jsonParser.parse(actualJson));
        } else {
            // one or both of expected and actual is not json.. unlikely equal
            return super.assertEqual(expectedJson, actualJson);
        }
    }

    public StepResult assertElementPresent(String json, String jsonpath) {
        String match = find(json, jsonpath);
        boolean isMatched = match != null;
        String message = "JSON " + (isMatched ? "matches " : " DOES NOT match ") + jsonpath;
        return new StepResult(isMatched, message, null);
    }

    public StepResult assertElementNotPresent(String json, String jsonpath) {
        String match = find(json, jsonpath);
        boolean isMatched = match != null;
        String message = "JSON " + (isMatched ? "matches " : " DOES NOT matches ") + jsonpath;
        return new StepResult(!isMatched, message, null);
    }

    public StepResult assertElementCount(String json, String jsonpath, String count) {
        int countInt = toPositiveInt(count, "count");
        return super.assertEqual(countInt + "", count(json, jsonpath) + "");
    }

    public StepResult storeValue(String json, String jsonpath, String var) {
        return extractJsonValue(json, jsonpath, var);
    }

    public StepResult storeValues(String json, String jsonpath, String var) {
        // jsonpath resolves multiple matches to string anyways...
        return extractJsonValue(json, jsonpath, var);
    }

    /**
     * add {@code data} to {@code var}, which should represents a JSON object, at a location specified via {@code jsonpath}.
     *
     * Optionally, {@code jsonpath} can be empty to represent top level.
     */
    public StepResult addOrReplace(String json, String jsonpath, String input, String var) {
        try {
            json = OutputFileUtils.resolveContent(json, context, false, true);
        } catch (IOException e) {
            ConsoleUtils.log("Unable to read JSON as a file; considering it as is...");
        }
        requiresNotBlank(json, "invalid/malformed json", json);

        requiresNotBlank(input, "Invalid input", json);
        requiresValidVariableName(var);

        JsonEditorConfig config = new JsonEditorConfig();
        config.setRemoveNull(true);
        JsonEditor editor = JsonEditor.newInstance(config);

        if (context.isNullValue(jsonpath)) { jsonpath = ""; }
        Object modified = editor.add(json, jsonpath, input);
        if (modified == null) {
            ConsoleUtils.log("JSON modified to null");
            context.removeData(var);
        } else {
            updateDataVariable(var, modified.toString());
        }

        return StepResult.success("JSON modified and stored to ${" + var + "}");
    }

    public StepResult storeCount(String json, String jsonpath, String var) {
        requiresNotBlank(var, "invalid variable", var);
        context.setData(var, count(json, jsonpath));
        return StepResult.success("match count stored to ${" + var + "}");
    }

    public StepResult assertValue(String json, String jsonpath, String expected) {
        return assertEqual(expected, find(json, jsonpath));
    }

    public StepResult assertValues(String json, String jsonpath, String array, String exactOrder) {
        requiresNotBlank(json, "Invalid JSON", json);
        requiresNotBlank(jsonpath, "Invalid JsonPath", jsonpath);

        boolean isExactOrder = BooleanUtils.toBoolean(exactOrder);

        // could be an array of 1 item
        JsonArray expectedArray = toJsonArray(array, context.getTextDelim());
        if (expectedArray == null) { return StepResult.fail("Unable to parse array '" + array + "'"); }

        String actual = find(json, jsonpath);
        JsonArray actualArray = toJsonArray(actual, ",");
        if (actualArray == null) {
            return StepResult.fail("JsonPath '" + jsonpath + "' derived invalid JSON: '" + actual + "'");
        }

        // by this point both `expected` and `actual` are array
        if (expectedArray.size() == 0 && actualArray.size() == 0) { return StepResult.success("Both array are empty"); }

        if (!isExactOrder) {
            List<JsonElement> expectedList = IterableUtils.toList(expectedArray);
            expectedList.sort(Comparator.comparing(elem -> (elem == null ? "" : elem.toString())));

            List<JsonElement> actualList = IterableUtils.toList(actualArray);
            actualList.sort(Comparator.comparing(elem -> (elem == null ? "" : elem.toString())));

            for (int i = 0; i < expectedList.size(); i++) {
                JsonElement expected = expectedList.get(i);
                StepResult itemCompare = assertEqual(expected, actualList.get(i));
                if (itemCompare.failed()) { return itemCompare; }
            }
        } else {
            StepResult valueCompare = assertEqual(expectedArray, actualArray);
            if (valueCompare.failed()) { return valueCompare; }
        }

        return StepResult.success("EXPECTED array is equivalent to the ACTUAL array");
    }

    public StepResult assertCorrectness(String json, String schema) {
        JsonNode jsonNode = deriveWellformedJson(json);
        if (jsonNode == null) { StepResult.fail("invalid json: " + json); }

        requires(StringUtils.isNotBlank(schema) && !context.isNullValue(schema), "empty schema", schema);

        JsonSchema jsonSchema;
        try {
            if (OutputFileUtils.isContentReferencedAsFile(schema, context)) {
                String schemaLocation = new File(schema).toURI().toURL().toString();
                jsonSchema = JSON_SCHEMA_FACTORY.getJsonSchema(schemaLocation);
            } else if (OutputFileUtils.isContentReferencedAsClasspathResource(schema, context)) {
                String schemaLocation = (StringUtils.startsWith(schema, "/") ? "" : "/") + schema;
                jsonSchema = JSON_SCHEMA_FACTORY.getJsonSchema("resource:" + schemaLocation);
            } else {
                // support path-based content specificaton
                schema = OutputFileUtils.resolveContent(schema, context, false);
                if (StringUtils.isBlank(schema)) { return StepResult.fail("invalid schema: " + schema); }

                JsonNode schemaNode = JsonLoader.fromString(schema);
                if (schemaNode == null) { return StepResult.fail("invalid schema: " + schema); }

                jsonSchema = JSON_SCHEMA_FACTORY.getJsonSchema(schemaNode);
            }

            ProcessingReport report = jsonSchema.validate(jsonNode);
            return report.isSuccess() ?
                   StepResult.success("json validated successfully against schema") :
                   StepResult.fail(parseSchemaValidationError(report));
        } catch (IOException e) {
            ConsoleUtils.log("Error reading as file '" + schema + "': " + e.getMessage());
            return StepResult.fail("Unable to retrieve JSON schema: " + e.getMessage());
        } catch (ProcessingException e) {
            ConsoleUtils.log("Error processing schema '" + schema + "': " + e.getMessage());
            return StepResult.fail("Unable to process JSON schema: " + e.getMessage());
        }
    }

    public StepResult assertWellformed(String json) {
        JsonNode jsonNode = deriveWellformedJson(json);
        return jsonNode == null ?
               StepResult.fail("invalid json: " + json) :
               StepResult.success("json validated as well-formed");
    }

    public StepResult fromCsv(String csv, String header, String jsonFile) throws IOException {
        requiresNotBlank(csv, "Invalid csv", csv);
        requiresNotBlank(jsonFile, "Invalid destination JSON file", jsonFile);

        String csvContent = OutputFileUtils.resolveContent(csv, context, false, true);
        if (StringUtils.isBlank(csvContent)) { return StepResult.fail("CSV '" + csv + "' has no valid content"); }

        File destination = new File(jsonFile);
        if (!destination.getParentFile().mkdirs()) {
            ConsoleUtils.log("Unable to create parent directory for '" + jsonFile + "'; more failure could ensue");
        }

        // these lines might have \r still; we need to remove them before field-level parsing
        JsonHelper.fromCsv(TextUtils.to2dList(StringUtils.remove(csvContent, "\r"), "\n", context.getTextDelim()),
                           BooleanUtils.toBoolean(header),
                           new FileWriter(jsonFile));

        return StepResult.success("CSV '" + csv + "' converted to JSON '" + jsonFile + "'");
    }

    public StepResult minify(String json, String var) {
        requiresValidVariableName(var);

        String jsonContent = retrieveJsonContent(json);
        if (jsonContent == null) { return StepResult.fail("Unable to parse JSON content: " + json); }

        JsonElement jsonElement = GSON_COMPRESSED.fromJson(jsonContent, JsonElement.class);
        requiresNotNull(jsonElement, "invalid json", json);

        String compressed = GSON_COMPRESSED.toJson(jsonElement);
        if (StringUtils.isBlank(compressed)) { return StepResult.fail("Unable to minify JSON content"); }

        updateDataVariable(var, compressed);
        return StepResult.success("JSON content compressed and saved to '" + var);
    }

    public StepResult beautify(String json, String var) {
        requiresValidVariableName(var);

        String jsonContent = retrieveJsonContent(json);
        if (jsonContent == null) { return StepResult.fail("Unable to parse JSON content: " + json); }

        JsonElement jsonElement = GSON.fromJson(jsonContent, JsonElement.class);
        requiresNotNull(jsonElement, "invalid json", json);

        String beautified = GSON.toJson(jsonElement);
        if (StringUtils.isBlank(beautified)) { return StepResult.fail("Unable to beautify JSON content"); }

        updateDataVariable(var, beautified);
        return StepResult.success("JSON content beautified and saved to '" + var);
    }

    @NotNull
    protected StepResult extractJsonValue(String json, String jsonpath, String var) {
        requiresNotBlank(var, "invalid variable", var);
        String match = find(json, jsonpath);
        if (match == null) { return StepResult.fail("EXPECTED match against '" + jsonpath + "' was not found"); }

        boolean asIs = context.getBooleanData(TREAT_JSON_AS_IS, getDefaultBool(TREAT_JSON_AS_IS));
        if (asIs || !TextUtils.isBetween(match, "[", "]")) {
            updateDataVariable(var, match);
        } else {
            JSONArray array = new JSONArray(match);
            if (array.length() < 1) {
                updateDataVariable(var, "[]");
            } else {
                List<String> matches = new ArrayList<>();
                array.forEach(elem -> matches.add(elem.toString()));
                context.setData(var, matches);
            }
        }

        return StepResult.success("EXPECTED match against '" + jsonpath + "' stored to ${" + var + "}");
    }

    @NotNull
    protected StepResult assertEqual(JsonElement expectedJson, JsonElement actualJson) {
        JsonMetaParser jsonMetaParser = new JsonMetaParser();
        JsonMeta expectedMeta = jsonMetaParser.parse(expectedJson);
        JsonMeta actualMeta = jsonMetaParser.parse(actualJson);
        JsonComparisonResult results = expectedMeta.compare(actualMeta);

        if (results.hasDifferences()) {
            String differences = GSON.toJson(results.toJson());
            context.setData(LAST_JSON_COMPARE_RESULT, differences);
            ConsoleUtils.log("JSON differences found:\n" + differences);
            addOutputAsLink("JSON comparison resulted in " + results.differenceCount() + " differences",
                            differences, "json");
            return StepResult.fail("EXPECTED json is NOT equivalent to the ACTUAL json");
        } else {
            context.removeData(LAST_JSON_COMPARE_RESULT);
            return StepResult.success("EXPECTED json is equivalent to the ACTUAL json");
        }
    }

    protected String retrieveJsonContent(String json) {
        requires(StringUtils.isNotBlank(json), "invalid json", json);

        try {
            // support path-based content specification
            json = OutputFileUtils.resolveContent(json, context, false, true);
            requiresNotBlank(json, "invalid/malformed json", json);

            return new String(StringUtils.trim(json).getBytes(DEF_CHARSET), DEF_CHARSET);
        } catch (IOException e) {
            ConsoleUtils.log("Error reading as file '" + json + "': " + e.getMessage());
            fail("Error reading as file '" + json + "': " + e.getMessage());
            // won't return... but compiler is happy
            return null;
        }
    }

    protected Object toJSONObject(String json) {
        json = retrieveJsonContent(json);
        if (json == null) { return null; }

        if (TextUtils.isBetween(json, "[", "]")) {
            JSONArray jsonArray = JsonUtils.toJSONArray(json);
            requiresNotNull(jsonArray, "invalid/malformed json", json);
            return jsonArray;
        }

        JSONObject jsonObject = JsonUtils.toJSONObject(json);
        requiresNotNull(jsonObject, "invalid/malformed json", json);
        return jsonObject;
    }

    protected Object sanityCheck(String json, String jsonpath) {
        requiresNotBlank(jsonpath, "invalid jsonpath", jsonpath);
        return toJSONObject(json);
    }

    protected String find(String json, String jsonpath) {
        Object obj = sanityCheck(json, jsonpath);
        if (obj instanceof JSONArray) { return JSONPath.find((JSONArray) obj, jsonpath); }
        if (obj instanceof JSONObject) { return JSONPath.find((JSONObject) obj, jsonpath); }
        throw new IllegalArgumentException("Unsupported data type " + obj.getClass().getSimpleName());
    }

    protected int count(String json, String jsonpath) {
        Object obj = sanityCheck(json, jsonpath);

        JSONPath jp = null;
        if (obj instanceof JSONArray) { jp = new JSONPath((JSONArray) obj, jsonpath); }
        if (obj instanceof JSONObject) { jp = new JSONPath((JSONObject) obj, jsonpath); }

        if (jp == null) {throw new IllegalArgumentException("Unsupported data type " + obj.getClass().getSimpleName());}

        String matches = jp.get();

        int count = 0;
        if (matches != null) {
            // is this array?
            if (StringUtils.startsWith(matches, "[") && StringUtils.endsWith(matches, "]")) {
                JSONArray matchedArray = jp.getAs(JSONArray.class);
                if (matchedArray != null) { count = matchedArray.length(); }
            } else {
                count = 1;
            }
        }

        return count;
    }

    protected static JsonArray toJsonArray(String array, String delimiter) {
        if (StringUtils.isEmpty(array)) { return new JsonArray(); }

        JsonParser jsonParser = new JsonParser();
        JsonElement expectedJson;

        try {
            // case 1: expected is good/parsable JSON
            expectedJson = jsonParser.parse(TextUtils.wrapIfMissing(array, "[", "]"));
        } catch (JsonSyntaxException e) {
            // nope, not valid json.. move on to next evaluation...
            // case 2: delimiter string with spaces (string with no spaces should be parsed in case 1)
            String fixed = TextUtils.toString(
                Arrays.asList(StringUtils.splitByWholeSeparator(array, delimiter)), ",", "\"", "\"");

            try {
                expectedJson = jsonParser.parse(TextUtils.wrapIfMissing(fixed, "[", "]"));
            } catch (JsonSyntaxException e1) {
                // report the original `array` as error
                ConsoleUtils.error("Unable to parse array '" + array + "': " + e1.getMessage());
                return null;
            }
        }

        if (expectedJson == null || !expectedJson.isJsonArray()) { return null; }

        return expectedJson.getAsJsonArray();
    }

    private JsonNode deriveWellformedJson(String json) {
        requiresNotBlank(json, "empty json", json);

        JsonNode jsonNode = null;
        try {
            // support path-based content specification
            json = OutputFileUtils.resolveContent(json, context, false);
            if (StringUtils.isNotBlank(json)) { jsonNode = JsonLoader.fromString(json); }
        } catch (IOException e) {
            String msg = "Error reading '" + json + "': " + e.getMessage();
            ConsoleUtils.log(msg);
            fail(msg);
            // won't return... but compiler is happy
            //return StepResult.fail("Unable to retrieve JSON data via known methods");
        }

        return jsonNode;
    }

    private String parseSchemaValidationError(ProcessingReport report) {
        if (report == null) { return "Unknown error - null report object"; }

        String reportText = report.toString();
        reportText = StringUtils.substringAfter(reportText, "--- BEGIN MESSAGES ---");
        reportText = StringUtils.substringBefore(reportText, "---  END MESSAGES  ---");
        return StringUtils.trim(reportText);
    }
}
