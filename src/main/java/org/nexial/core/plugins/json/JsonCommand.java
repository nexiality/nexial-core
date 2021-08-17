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
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.*;
import org.nexial.core.utils.JsonEditor.JsonEditorConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;

import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.utils.CheckUtils.*;

public class JsonCommand extends BaseCommand {
    private static final String DIFF_HIGHLIGHT_HTML_START = "<span class=\"diff-highlight\"" +
                                                            " style=\"background:#fee;color:red;font-weight:bold\">";
    private static final String DIFF_HIGHLIGHT_HTML_END = "</span>";
    private static final String DIFF_NULL_HTML_START = "<code class=\"diff-null\" style=\"color:#777\">";
    private static final String DIFF_NULL_HTML_END = "</code>";

    @Override
    public String getTarget() { return "json"; }

    public StepResult assertEqual(String expected, String actual) {
        if (expected == null && actual == null) { return StepResult.success("Both EXPECTED and ACTUAL are null"); }
        if (StringUtils.equals(expected, actual)) { return StepResult.success("Both EXPECTED and ACTUAL are the same");}

        String expectedJson;
        try {
            expectedJson = new OutputResolver(expected, context, false, true).getContent();
        } catch (Throwable e) {
            return StepResult.fail("EXPECTED json is invalid or not readable: " + e.getMessage());
        }

        String actualJson;
        try {
            actualJson = new OutputResolver(actual, context, false, true).getContent();
        } catch (Throwable e) {
            return StepResult.fail("ACTUAL json is invalid or not readable: " + e.getMessage());
        }

        // maybe expected or actual are NOT json doc or array...
        if ((TextUtils.isBetween(expectedJson, "{", "}") || TextUtils.isBetween(expectedJson, "[", "]")) &&
            (TextUtils.isBetween(actualJson, "{", "}") || TextUtils.isBetween(actualJson, "[", "]"))) {
            return assertEqual(JsonParser.parseString(expectedJson), JsonParser.parseString(actualJson));
        } else {
            // one or both of expected and actual is not json.. unlikely equal
            return super.assertEqual(expectedJson, actualJson);
        }
    }

    public StepResult assertElementPresent(String json, String jsonpath) {
        String match = find(json, jsonpath);
        boolean isMatched = match != null;
        String message = "JSON " + (isMatched ? "matches " : "DOES NOT match ") + jsonpath;
        return new StepResult(isMatched, message, null);
    }

    public StepResult assertElementNotPresent(String json, String jsonpath) {
        String match = find(json, jsonpath);
        boolean isMatched = match != null;
        String message = "JSON " + (isMatched ? "matches " : "DOES NOT match ") + jsonpath;
        return new StepResult(!isMatched, message, null);
    }

    public StepResult assertElementCount(String json, String jsonpath, String count) {
        int expected = toPositiveInt(count, "count");
        int actual = count(json, jsonpath);
        if (expected == actual) {
            return StepResult.success("EXPECTED element count found");
        } else {
            return StepResult.fail("element count (" + actual + ") DID NOT match expected (" + count + ")");
        }
    }

    public StepResult storeValue(String json, String jsonpath, String var) {
        return extractJsonValue(json, jsonpath, var);
    }

    public StepResult storeValues(String json, String jsonpath, String var) {
        // jsonpath resolves multiple matches to string anyways...
        return extractJsonValue(json, jsonpath, var);
    }

    public StepResult storeCount(String json, String jsonpath, String var) {
        requiresValidAndNotReadOnlyVariableName(var);
        context.setData(var, count(json, jsonpath));
        return StepResult.success("match count stored to ${" + var + "}");
    }

    public StepResult storeKeys(String json, String jsonpath, String var) {
        requiresValidAndNotReadOnlyVariableName(var);

        Object obj = toJSONObject(json);
        if (obj instanceof JSONArray) {
            context.removeData(var);
            return StepResult.success("No JSON keys can be retrieved from a JSON array");
        }

        if (obj instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) obj;

            Set<String> keys;
            if (StringUtils.isEmpty(jsonpath)) {
                keys = jsonObject.keySet();
            } else {
                keys = JSONPath.keys(jsonObject, jsonpath);
            }

            updateDataVariable(var, TextUtils.toString(keys, context.getTextDelim()));
            return StepResult.success("JSON keys from " + jsonpath + " stored to data variable '" + var + "'");
        }

        return StepResult.fail("Specified JSON did not resolve to a valid JSON document");
    }

    /**
     * add {@code input} to {@code json}, which should represents a JSON object, at a location specified via
     * {@code jsonpath}.  The final output is captured as {@code var}. Optionally, {@code jsonpath} can be empty to
     * represent top level.
     */
    public StepResult addOrReplace(String json, String jsonpath, String input, String var) {
        requiresValidAndNotReadOnlyVariableName(var);

        try {
            // json = OutputFileUtils.resolveContent(json, context, false, true);
            json = new OutputResolver(json, context).getContent();
        } catch (Throwable e) {
            ConsoleUtils.log("Unable to read JSON as a file; considering it as is...");
        }
        requiresNotBlank(json, "invalid/malformed json", json);

        try {
            // input = OutputFileUtils.resolveContent(input, context, false, true);
            input = new OutputResolver(input, context).getContent();
        } catch (Throwable e) {
            ConsoleUtils.log("Unable to read 'input' as a JSON file; considering it as is...");
        }
        requiresNotBlank(input, "Invalid input", input);

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

    public StepResult assertValue(String json, String jsonpath, String expected) {
        return assertEqual(expected, find(json, jsonpath));
    }

    public StepResult assertValues(String json, String jsonpath, String array, String exactOrder) {
        requiresNotBlank(json, "Invalid JSON", json);
        requiresNotBlank(jsonpath, "Invalid JsonPath", jsonpath);

        boolean isExactOrder = CheckUtils.toBoolean(exactOrder);

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
        if (jsonNode == null) { return StepResult.fail("invalid json: " + json); }

        requires(StringUtils.isNotBlank(schema) && !context.isNullValue(schema), "empty schema", schema);

        try {
            String jsonSchemaContent;
            if (OutputFileUtils.isContentReferencedAsClasspathResource(schema, context)) {
                jsonSchemaContent = ResourceUtils.loadResource(schema);
            } else {
                // support path-based content specification
                jsonSchemaContent = OutputFileUtils.resolveContent(schema, context, false);
            }

            if (StringUtils.isBlank(jsonSchemaContent)) { return StepResult.fail("invalid schema: " + schema); }
            Set<ValidationMessage> report = validateJsonWithSchema(jsonNode, jsonSchemaContent);
            return CollectionUtils.isEmpty(report) ?
                   StepResult.success("json validated successfully against schema") :
                   StepResult.fail(parseSchemaValidationError(report));
        } catch (IOException e) {
            ConsoleUtils.log("Error reading as file '" + schema + "': " + e.getMessage());
            return StepResult.fail("Unable to retrieve JSON schema: " + e.getMessage());
        }
    }

    public static Set<ValidationMessage> validateJsonWithSchema(JsonNode jsonNode,
                                                         String jsonSchemaContent)
        throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonSchemaNode = mapper.readTree(jsonSchemaContent);
        // if schema is not found, default to latest
        VersionFlag versionFlag = StringUtils.contains(jsonSchemaContent, "$schema") ?
                                  SpecVersionDetector.detect(jsonSchemaNode) :
                                  VersionFlag.V201909;
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(versionFlag);
        JsonSchema jsonSchema = factory.getSchema(jsonSchemaNode);

        return jsonSchema.validate(jsonNode);
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
                           CheckUtils.toBoolean(header),
                           new FileWriter(jsonFile));

        return StepResult.success("CSV '" + csv + "' converted to JSON '" + jsonFile + "'");
    }

    public StepResult minify(String json, String var) { return compact(var, json, "false"); }

    public StepResult beautify(String json, String var) {
        requiresValidAndNotReadOnlyVariableName(var);

        String jsonContent = retrieveJsonContent(json);
        if (jsonContent == null) { return StepResult.fail("Unable to parse JSON content: " + json); }

        String beautified = JsonUtils.beautify(jsonContent);
        if (StringUtils.isBlank(beautified)) { return StepResult.fail("Unable to beautify JSON content"); }

        updateDataVariable(var, beautified);
        return StepResult.success("JSON content beautified and saved to '" + var + "'");
    }

    public StepResult compact(String var, String json, String removeEmpty) {
        requiresValidAndNotReadOnlyVariableName(var);

        String jsonContent = retrieveJsonContent(json);
        if (jsonContent == null) { return StepResult.fail("Unable to parse JSON content: " + json); }

        String compressed = JsonUtils.compact(jsonContent, CheckUtils.toBoolean(removeEmpty));
        if (StringUtils.isBlank(compressed)) { return StepResult.fail("Unable to minify/compact JSON content"); }

        updateDataVariable(var, compressed);
        return StepResult.success("JSON content minify/compacted and saved to '" + var);
    }

    public static Object resolveToJSONObject(String json) {
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

    public static JsonElement removeEmpty(JsonElement json, boolean onlyNull) {
        if (json == null) { return null; }

        if (json.isJsonNull()) { return null; }

        if (json.isJsonPrimitive() && StringUtils.isEmpty(json.getAsString())) { return null; }

        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            List<String> keys = CollectionUtil.toList(jsonObject.keySet());
            if (CollectionUtils.isEmpty(keys)) { return null; }

            keys.forEach(childName -> {
                JsonElement childElement = jsonObject.get(childName);
                if (childElement.isJsonNull()) {
                    jsonObject.remove(childName);
                } else if (childElement.isJsonPrimitive()) {
                    if (!onlyNull && StringUtils.isEmpty(childElement.getAsString())) { jsonObject.remove(childName); }
                } else {
                    JsonElement compacted = removeEmpty(childElement, onlyNull);
                    if (compacted == null) {
                        jsonObject.remove(childName);
                    } else {
                        jsonObject.add(childName, compacted);
                    }
                }
            });

            if (jsonObject.isJsonNull() || jsonObject.size() == 0) { return null; }
        }

        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement elem = array.get(i);
                if (elem.isJsonNull()) {
                    array.remove(i--);
                } else if (elem.isJsonPrimitive()) {
                    if (!onlyNull && StringUtils.isEmpty(elem.getAsString())) { array.remove(i--); }
                } else {
                    JsonElement compacted = removeEmpty(elem, onlyNull);
                    if (compacted == null) {
                        array.remove(i--);
                    } else {
                        array.set(i, compacted);
                    }
                }
            }

            if (array.size() < 1) { return null; }
        }

        return json;
    }

    @NotNull
    protected StepResult extractJsonValue(String json, String jsonpath, String var) {
        requiresValidAndNotReadOnlyVariableName(var);

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
            handleComparisonResults(results);
            return StepResult.fail("EXPECTED json is NOT equivalent to the ACTUAL json");
        } else {
            context.removeDataForcefully(LAST_JSON_COMPARE_RESULT);
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

    protected Object toJSONObject(String json) { return resolveToJSONObject(retrieveJsonContent(json)); }

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

        JsonElement expectedJson;
        try {
            // case 1: expected is good/parsable JSON
            expectedJson = JsonParser.parseString(TextUtils.wrapIfMissing(array, "[", "]"));
        } catch (JsonSyntaxException e) {
            // nope, not valid json.. move on to next evaluation...
            // case 2: delimiter string with spaces (string with no spaces should be parsed in case 1)
            String fixed = TextUtils.toString(
                Arrays.asList(StringUtils.splitByWholeSeparator(array, delimiter)), ",", "\"", "\"");

            try {
                expectedJson = JsonParser.parseString(TextUtils.wrapIfMissing(fixed, "[", "]"));
            } catch (JsonSyntaxException e1) {
                // report the original `array` as error
                ConsoleUtils.error("Unable to parse array '" + array + "': " + e1.getMessage());
                return null;
            }
        }

        if (expectedJson == null || !expectedJson.isJsonArray()) { return null; }

        return expectedJson.getAsJsonArray();
    }

    private void handleComparisonResults(JsonComparisonResult results) {
        if (results == null) {
            context.removeData(LAST_JSON_COMPARE_RESULT);
            return;
        }

        String differences = GSON.toJson(results.toJson());
        context.setData(LAST_JSON_COMPARE_RESULT, differences);
        ConsoleUtils.log("JSON differences found:\n" + differences);

        boolean asJson = context.getBooleanData(COMPARE_RESULT_AS_JSON, getDefaultBool(COMPARE_RESULT_AS_JSON));
        boolean asCsv = context.getBooleanData(COMPARE_RESULT_AS_CSV, getDefaultBool(COMPARE_RESULT_AS_CSV));
        boolean asHtml = context.getBooleanData(COMPARE_RESULT_AS_HTML, getDefaultBool(COMPARE_RESULT_AS_HTML));

        String caption = "JSON comparison resulted in " + results.differenceCount() + " differences";

        if (asJson) { addOutputAsLink(caption, differences, "json"); }

        List<String> headers = Arrays.asList("node", "expected", "actual");
        List<List<String>> diffList = results.toList();
        if (asCsv) { addOutputAsLink(caption, TextUtils.createCsv(headers, diffList, "\r\n", ",", "\""), "csv"); }

        if (asHtml) {
            addOutputAsLink(caption,
                            TextUtils.createHtmlTable(
                                headers,
                                diffList,
                                (row, position) -> {
                                    String html = row.get(position);
                                    if (html == null || "null".equals(html) || "NOT FOUND".equals(html)) {
                                        return DIFF_NULL_HTML_START + html + DIFF_NULL_HTML_END;
                                    }

                                    html = TextUtils.decorateTextRange(
                                        html, "value ", " of", DIFF_HIGHLIGHT_HTML_START, DIFF_HIGHLIGHT_HTML_END);
                                    html = TextUtils.decorateTextRange(
                                        html, "node '", "' ", DIFF_HIGHLIGHT_HTML_START, DIFF_HIGHLIGHT_HTML_END);
                                    return "<code>" + html + "</code>";
                                },
                                "compare-result-table"),
                            "html");
        }
    }

    private JsonNode deriveWellformedJson(String json) {
        requiresNotBlank(json, "empty json", json);

        JsonNode jsonNode = null;
        try {
            // support path-based content specification
            json = OutputFileUtils.resolveContent(json, context, false);
            if (StringUtils.isNotBlank(json)) { jsonNode = new ObjectMapper().readTree(json); }
        } catch (IOException e) {
            String msg = "Error reading '" + json + "': " + e.getMessage();
            ConsoleUtils.log(msg);
            fail(msg);
            // won't return... but compiler is happy
            //return StepResult.fail("Unable to retrieve JSON data via known methods");
        }

        return jsonNode;
    }

    private String parseSchemaValidationError(Set<ValidationMessage> report) {
        if (report == null) { return "Unknown error - null report object"; }
        if (CollectionUtils.size(report) < 1) { return "No schema validation error found"; }
        return report.stream().map(ValidationMessage::toString).collect(Collectors.joining("\n")).trim();
    }

    // private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = JsonSchemaFactory.byDefault();
    // protected StepResult assertCorrectness_fge(String json, String schema) {
    //     JsonNode jsonNode = deriveWellformedJson(json);
    //     if (jsonNode == null) { return StepResult.fail("invalid json: " + json); }
    //
    //     requires(StringUtils.isNotBlank(schema) && !context.isNullValue(schema), "empty schema", schema);
    //
    //     JsonSchema jsonSchema;
    //     try {
    //         if (OutputFileUtils.isContentReferencedAsFile(schema, context)) {
    //             String schemaLocation = new File(schema).toURI().toURL().toString();
    //             jsonSchema = JSON_SCHEMA_FACTORY.getJsonSchema(schemaLocation);
    //         } else if (OutputFileUtils.isContentReferencedAsClasspathResource(schema, context)) {
    //             String schemaLocation = (StringUtils.startsWith(schema, "/") ? "" : "/") + schema;
    //             jsonSchema = JSON_SCHEMA_FACTORY.getJsonSchema("resource:" + schemaLocation);
    //         } else {
    //             // support path-based content specification
    //             schema = OutputFileUtils.resolveContent(schema, context, false);
    //             if (StringUtils.isBlank(schema)) { return StepResult.fail("invalid schema: " + schema); }
    //
    //             JsonNode schemaNode = JsonLoader.fromString(schema);
    //             if (schemaNode == null) { return StepResult.fail("invalid schema: " + schema); }
    //
    //             jsonSchema = JSON_SCHEMA_FACTORY.getJsonSchema(schemaNode);
    //         }
    //
    //         ProcessingReport report = jsonSchema.validate(jsonNode, true);
    //         return report.isSuccess() ?
    //                StepResult.success("json validated successfully against schema") :
    //                StepResult.fail(parseSchemaValidationError(report));
    //     } catch (IOException e) {
    //         ConsoleUtils.log("Error reading as file '" + schema + "': " + e.getMessage());
    //         return StepResult.fail("Unable to retrieve JSON schema: " + e.getMessage());
    //     } catch (ProcessingException e) {
    //         ConsoleUtils.log("Error processing schema '" + schema + "': " + e.getMessage());
    //         return StepResult.fail("Unable to process JSON schema: " + e.getMessage());
    //     }
    // }
    //
    // private String parseSchemaValidationError(ProcessingReport report) {
    //     if (report == null) { return "Unknown error - null report object"; }
    //
    //     String reportText = report.toString();
    //     reportText = StringUtils.substringAfter(reportText, "--- BEGIN MESSAGES ---");
    //     reportText = StringUtils.substringBefore(reportText, "---  END MESSAGES  ---");
    //     return StringUtils.trim(reportText);
    // }
}
