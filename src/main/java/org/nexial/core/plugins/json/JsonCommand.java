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
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.JSONPath;
import org.nexial.core.utils.JsonUtils;
import org.nexial.core.utils.OutputFileUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.utils.CheckUtils.*;

/**
 *
 */
public class JsonCommand extends BaseCommand {
	private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = JsonSchemaFactory.byDefault();

	@Override
	public String getTarget() { return "json"; }

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
		return assertEqual(countInt + "", count(json, jsonpath) + "");
	}

	public StepResult storeValue(String json, String jsonpath, String var) {
		requiresNotBlank(var, "invalid variable", var);
		String match = find(json, jsonpath);
		if (match == null) { return StepResult.fail("EXPECTED match against '" + jsonpath + "' was not found"); }

		context.setData(var, match);
		return StepResult.success("EXPECTED match against '" + jsonpath + "' stored to ${" + var + "}");
	}

	public StepResult storeValues(String json, String jsonpath, String var) {
		// jsonpath resolves multiple matches to string anyways...
		return storeValue(json, jsonpath, var);
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
		String actual = find(json, jsonpath);
		// accomodate for [, ] and double quotes
		actual = StringUtils.removeStart(actual, "[");
		actual = StringUtils.removeEnd(actual, "]");
		actual = StringUtils.remove(actual, "\"");
		return assertArrayEqual(array, actual, exactOrder);
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

	protected Object toJSONObject(ExecutionContext context, String json) {
		requires(StringUtils.isNotBlank(json), "invalid json", json);

		try {
			// support path-based content specificaton
			if (context != null) { json = OutputFileUtils.resolveContent(json, context, false); }

			requiresNotBlank(json, "invalid/malformed json", json);

			json = new String(StringUtils.trim(json).getBytes(DEF_CHARSET), DEF_CHARSET);
			if (TextUtils.isBetween(json, "[", "]")) {
				JSONArray jsonArray = JsonUtils.toJSONArray(json);
				requires(jsonArray != null, "invalid/malformed json", json);
				return jsonArray;
			}

			JSONObject jsonObject = JsonUtils.toJSONObject(json);
			requires(jsonObject != null, "invalid/malformed json", json);
			return jsonObject;
		} catch (IOException e) {
			ConsoleUtils.log("Error reading as file '" + json + "': " + e.getMessage());
			fail("Error reading as file '" + json + "': " + e.getMessage());
			// won't return... but compiler is happy
			return null;
		}
	}

	protected Object sanityCheck(String json, String jsonpath) {
		requires(StringUtils.isNotBlank(jsonpath), "invalid jsonpath", jsonpath);
		return toJSONObject(context, json);
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

		if (jp == null) {
			throw new IllegalArgumentException("Unsupported data type " + obj.getClass().getSimpleName());
		}

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

	private JsonNode deriveWellformedJson(String json) {
		requires(StringUtils.isNotBlank(json), "empty json", json);

		JsonNode jsonNode = null;
		try {
			// support path-based content specificaton
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
