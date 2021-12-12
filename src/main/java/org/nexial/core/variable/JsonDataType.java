/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.variable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.json.JsonSanitizer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.core.utils.ConsoleUtils;

import java.io.UnsupportedEncodingException;

import static java.lang.System.lineSeparator;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.GSON;

public class JsonDataType extends ExpressionDataType<JsonElement> {
    private Transformer transformer = new JsonTransformer();

    public JsonDataType(String textValue) throws TypeConversionException { super(textValue); }

    private JsonDataType() { super(); }

    @Override
    public String getName() { return "JSON"; }

    @Override
    public String toString() { return getName() + "(" + lineSeparator() + getTextValue() + lineSeparator() + ")"; }

    public JSONObject toJSONObject() throws JSONException {
        if (value instanceof JsonObject) { return new JSONObject(value.toString()); }
        throw new ClassCastException("Mismatched data type found: Unable to convert " +
                                     value.getClass().getSimpleName() + " to JSON document");
    }

    public JSONArray toJSONArray() throws JSONException {
        if (value instanceof JsonArray) { return new JSONArray(value.toString()); }
        throw new ClassCastException("Mismatched data type found: Unable to convert " +
                                     value.getClass().getSimpleName() + " to JSON Array");
    }

    @Override
    Transformer getTransformer() { return transformer; }

    @Override
    JsonDataType snapshot() {
        JsonDataType snapshot = new JsonDataType();
        snapshot.transformer = transformer;
        snapshot.value = value;
        snapshot.textValue = textValue;
        return snapshot;
    }

    @Override
    protected void init() throws TypeConversionException {
        TypeConversionException badJsonException =
            new TypeConversionException(getName(), textValue, "Cannot convert to JSON: " + textValue);

        if (StringUtils.isBlank(textValue)) { throw badJsonException; }

        try {
            textValue = escapeUnicode(textValue);
            this.value = GSON.fromJson(textValue, JsonElement.class);
            if (value == null) { throw badJsonException; }
        } catch (JsonSyntaxException e) {
            ConsoleUtils.error("Unable to parse as JSON - " + textValue + ": " + ExceptionUtils.getRootCauseMessage(e));
            throw badJsonException;
        }
    }

    protected static String escapeUnicode(String textValue) {
        if (StringUtils.isBlank(textValue)) { return textValue; }
        try {
            return new String(StringUtils.trim(textValue).getBytes(DEF_CHARSET), DEF_CHARSET);
        } catch (UnsupportedEncodingException e) {
            ConsoleUtils.error(JsonDataType.class.getSimpleName(),
                               "Unable to convert unicode sequence to ASCII equivalent",
                               e);
            return textValue;
        }
    }

    public static String sanitize(String textValue) {
        if (StringUtils.isBlank(textValue)) { return textValue; }
        return JsonSanitizer.sanitize(escapeUnicode(textValue));
    }
}
