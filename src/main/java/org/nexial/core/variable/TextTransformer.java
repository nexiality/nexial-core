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

import com.google.gson.JsonElement;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.WordUtils;
import org.jdom2.JDOMException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.nexial.commons.utils.*;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.ws.WsCommand;
import org.nexial.core.utils.ConsoleUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static org.nexial.core.NexialConst.Data.EXPRESSION_RESOLVE_URL;
import static org.nexial.core.NexialConst.Data.TEXT_DELIM;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.SystemVariables.getDefault;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.utils.CheckUtils.requiresPositiveNumber;
import static org.nexial.core.variable.ExpressionUtils.fixControlChars;

public class TextTransformer<T extends TextDataType> extends Transformer<T> {
    private static final Map<String, Integer> FUNCTION_TO_PARAM_LIST = discoverFunctions(TextTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM_LIST, TextTransformer.class, TextDataType.class);

    public T text(T data) { return data; }

    public T upper(T data) {
        if (data == null || StringUtils.isBlank(data.getValue())) { return data; }
        data.setValue(StringUtils.upperCase(data.getValue()));
        return data;
    }

    public T lower(T data) {
        if (data == null || StringUtils.isBlank(data.getValue())) { return data; }
        data.setValue(StringUtils.lowerCase(data.getValue()));
        return data;
    }

    public T title(T data) {
        if (data == null || StringUtils.isBlank(data.getValue())) { return data; }
        data.setValue(WordUtils.capitalizeFully(data.getValue()));
        return data;
    }

    /**
     * switch all lowercase to uppercase, and vice versa
     */
    public T swapCases(T data) {
        if (data == null || StringUtils.isBlank(data.getValue())) { return data; }
        data.setValue(StringUtils.swapCase(data.getValue()));
        return data;
    }

    public T after(T data, String text) {
        if (data == null || StringUtils.isEmpty(data.getValue()) || StringUtils.isEmpty(text)) { return data; }
        data.setValue(StringUtils.substringAfter(data.getValue(), fixControlChars(text)));
        return data;
    }

    public T before(T data, String text) {
        if (data == null || StringUtils.isEmpty(data.getValue()) || StringUtils.isEmpty(text)) { return data; }
        data.setValue(StringUtils.substringBefore(data.getValue(), fixControlChars(text)));
        return data;
    }

    public T between(T data, String before, String after) {
        if (data == null || StringUtils.isEmpty(data.getValue()) ||
            StringUtils.isEmpty(before) || StringUtils.isEmpty(after)) { return data; }
        data.setValue(StringUtils.substringBetween(data.getValue(), fixControlChars(before), fixControlChars(after)));
        return data;
    }

    public T substring(T data, String start, String end) {
        if (data == null || StringUtils.isEmpty(data.getValue())) { return data; }
        start = StringUtils.trim(start);
        end = StringUtils.trim(end);
        requiresPositiveNumber(start, "start must be a positive number (zero-based)", start);
        requiresPositiveNumber(end, "end must be a positive number (zero-based)", end);
        data.setValue(StringUtils.substring(data.getValue(), NumberUtils.toInt(start), NumberUtils.toInt(end)));
        return data;
    }

    public T trim(T data) {
        if (data == null) { return null; }
        data.setValue(StringUtils.trim(data.getValue()));
        return data;
    }

    public T distinct(T data) {
        if (data == null || StringUtils.isEmpty(data.getValue())) { return data; }

        String value = data.getValue();
        if (StringUtils.isEmpty(value)) {
            data.setValue("");
            return data;
        }

        char[] chars = value.toCharArray();
        Set<Character> distinctChars = new ListOrderedSet<>();
        for (char c : chars) { distinctChars.add(c); }

        Character[] characters = distinctChars.toArray(new Character[0]);
        chars = new char[characters.length];
        for (int i = 0; i < characters.length; i++) { chars[i] = characters[i]; }
        String newValue = new String(chars);
        data.setValue(newValue);
        return data;
    }

    public NumberDataType count(T data, String searchFor) {
        try {
            NumberDataType number = new NumberDataType("0");
            if (data == null || StringUtils.isEmpty(data.getValue()) || StringUtils.isEmpty(searchFor)) {
                return number;
            }

            int count = StringUtils.countMatches(data.getValue(), fixControlChars(searchFor));
            number.setValue(count);
            number.setTextValue(count + "");
            return number;
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }
    }

    public NumberDataType length(T data) {
        try {
            NumberDataType number = new NumberDataType("0");
            if (data == null || StringUtils.isEmpty(data.getValue())) { return number; }

            int length = StringUtils.length(data.getValue());
            number.setValue(length);
            number.setTextValue(length + "");
            return number;
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }
    }

    public NumberDataType number(T data) {
        if (data == null || StringUtils.isBlank(data.getValue())) { return null; }

        String text = data.getValue();
        if (StringUtils.isBlank(text)) {
            ConsoleUtils.log("Unable to convert '" + text + "' into number");
            return null;
        }

        try {
            return new NumberDataType(text);
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to create number: " + e.getMessage(), e);
        }
    }

    public ListDataType list(T data, String delim) {
        if (data == null || StringUtils.isEmpty(data.getValue()) || StringUtils.isEmpty(delim)) { return null; }
        return new ListDataType(data.getValue(), fixControlChars(delim));
    }

    public T pack(T data) {
        if (data == null || StringUtils.isEmpty(data.getValue())) { return data; }
        data.setValue(StringUtils.deleteWhitespace(data.getValue()));
        return data;
    }

    public T normalize(T data) {
        if (data == null || StringUtils.isEmpty(data.getValue())) { return data; }
        data.setValue(StringUtils.normalizeSpace(data.getValue()));
        return data;
    }

    public T remove(T data, String text) {
        if (data == null || StringUtils.isEmpty(data.getTextValue()) || StringUtils.isEmpty(text)) { return data; }
        data.setValue(StringUtils.remove(data.getTextValue(), fixControlChars(text)));
        return data;
    }

    public T removeLines(T data, String match) {
        if (data == null || StringUtils.isEmpty(data.getTextValue()) || StringUtils.isEmpty(match)) { return data; }

        String searchFor = fixControlChars(match);

        String text = data.getTextValue();
        List<String> lines = Arrays.asList(StringUtils.splitByWholeSeparatorPreserveAllTokens(text, "\n"));
        if (CollectionUtils.isEmpty(lines)) {
            if (TextUtils.polyMatch(text, searchFor)) { data.setValue(""); }
        } else {
            data.setValue(lines.stream()
                               .filter(line -> !TextUtils.polyMatch(line, searchFor))
                               .collect(Collectors.joining("\n")));
        }
        return data;
    }

    public T removeRegex(T data, String regex, String... options) {
        if (data == null || StringUtils.isEmpty(data.getTextValue())) { return data; }

        // first param of option is multi line, default is false
        boolean multiLine = options.length > 0 && BooleanUtils.toBoolean(options[0]);
        // second param of option is case-sensitive, default is true
        boolean caseSensitive = options.length <= 1 || BooleanUtils.toBoolean(options[1]);

        data.setValue(RegexUtils.removeMatches(data.getTextValue(), fixControlChars(regex), multiLine, caseSensitive));
        return data;
    }

    public T retain(T data, String keep) {
        if (data == null || StringUtils.isEmpty(data.getTextValue()) || StringUtils.isEmpty(keep)) { return data; }

        data.setValue(TextUtils.keepOnly(data.getTextValue(), fixControlChars(keep)));
        return data;
    }

    public T retainRegex(T data, String regex) {
        if (data == null || StringUtils.isEmpty(data.getTextValue()) || StringUtils.isEmpty(regex)) { return data; }
        data.setValue(RegexUtils.retainMatches(data.getTextValue(), fixControlChars(regex)));
        return data;
    }

    public T replace(T data, String searchFor, String replaceWith) {
        if (data == null) { return data; }

        String textValue = fixControlChars(data.getTextValue());
        String searchBy = fixControlChars(searchFor);
        String replaceBy = StringUtils.defaultString(fixControlChars(replaceWith));
        data.setValue(StringUtils.equals(textValue, searchFor) ?
                      replaceBy : StringUtils.replace(textValue, searchBy, replaceBy));
        return data;
    }

    public T replaceRegex(T data, String regexSearch, String replaceWith) {
        if (data == null || StringUtils.isEmpty(data.getTextValue()) || StringUtils.isEmpty(regexSearch)) {
            return data;
        }
        data.setValue(RegexUtils.replace(fixControlChars(data.getTextValue()),
                                         fixControlChars(regexSearch), fixControlChars(replaceWith)));
        return data;
    }

    public T leftMost(T data, String length) {
        requiresPositiveNumber(length, "invalid length", length);
        if (data != null) { data.setValue(StringUtils.left(data.getTextValue(), NumberUtils.toInt(length))); }
        return data;
    }

    public T rightMost(T data, String length) {
        requiresPositiveNumber(length, "invalid length", length);
        if (data != null) { data.setValue(StringUtils.right(data.getTextValue(), NumberUtils.toInt(length))); }
        return data;
    }

    public T removeLeft(T data, String length) {
        requiresPositiveNumber(length, "invalid length", length);
        if (data != null) { data.setValue(StringUtils.substring(data.getTextValue(), NumberUtils.toInt(length))); }
        return data;
    }

    public T removeRight(T data, String length) {
        requiresPositiveNumber(length, "invalid length", length);
        if (data != null) {
            String text = data.getTextValue();
            data.setValue(StringUtils.substring(text, 0, Math.max(text.length() - NumberUtils.toInt(length), 0)));
        }
        return data;
    }

    public T padLeft(T data, String padWith, String maxLength) {
        requiresPositiveNumber(maxLength, "invalid maxLength", maxLength);
        if (data == null) { return data; }

        int max = NumberUtils.toInt(maxLength);
        String textValue = data.getTextValue();

        // if text is already too long, then we'll return truncated version
        if (StringUtils.isEmpty(padWith) || StringUtils.length(textValue) > max) {
            data.setValue(StringUtils.truncate(textValue, max));
            return data;
        }

        data.setValue(StringUtils.leftPad(textValue, max, padWith));
        return data;
    }

    public T padRight(T data, String padWith, String maxLength) {
        requiresPositiveNumber(maxLength, "invalid maxLength", maxLength);
        if (data == null) { return data; }

        int max = NumberUtils.toInt(maxLength);
        String textValue = data.getTextValue();

        // if text is already too long, then we'll return truncated version
        if (StringUtils.isEmpty(padWith) || StringUtils.length(textValue) > max) {
            data.setValue(StringUtils.truncate(textValue, max));
            return data;
        }

        data.setValue(StringUtils.rightPad(textValue, max, padWith));
        return data;
    }

    public T prepend(T data, String... text) {
        if (data == null || ArrayUtils.isEmpty(text)) { return data; }
        data.setValue(fixControlChars(TextUtils.toString(text, null, null, null)) +
                      StringUtils.defaultString(data.getValue()));
        return data;
    }

    public T append(T data, String... text) {
        if (data == null || ArrayUtils.isEmpty(text)) { return data; }
        data.setValue(StringUtils.defaultString(data.getValue()) +
                      fixControlChars(TextUtils.toString(text, null, null, null)));
        return data;
    }

    public T appendIfMissing(T data, String text) {
        if (data == null || StringUtils.isEmpty(text)) { return data; }
        data.setValue(StringUtils.appendIfMissing(data.getValue(), text));
        return data;
    }

    public T prependIfMissing(T data, String text) {
        if (data == null || StringUtils.isEmpty(text)) { return data; }
        data.setValue(StringUtils.prependIfMissing(data.getValue(), text));
        return data;
    }

    public T insert(T data, String after, String text) {
        if (data == null || StringUtils.isEmpty(after) || StringUtils.isEmpty(text)) { return data; }

        String value = data.getValue();
        after = fixControlChars(after);
        if (StringUtils.isEmpty(value) || !StringUtils.contains(value, after)) { return data; }

        data.setValue(StringUtils.substringBefore(value, after) +
                      after + fixControlChars(text) +
                      StringUtils.substringAfter(value, after));
        return data;
    }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    public ExpressionDataType save(T data, String path, String append) { return super.save(data, path, append); }

    public T removeEnd(T data, String ending) {
        if (data == null || StringUtils.isEmpty(ending)) { return data; }
        data.setValue(StringUtils.removeEnd(data.getValue(), fixControlChars(ending)));
        return data;
    }

    public T removeStart(T data, String start) {
        if (data == null || StringUtils.isEmpty(start)) { return data; }
        data.setValue(StringUtils.removeStart(data.getValue(), fixControlChars(start)));
        return data;
    }

    /**
     * convert text ({@code data}) into a CSV instance by separating each line in {@code data} via the specified
     * {@code positions}.  This method assumes NO COLUMN HEADER to the target CSV instance.  It will use system line
     * separator ({@link System#lineSeparator()}) to split multiple lines (into multiple CSV records). If {@code data}
     * is without any line separator ({@link System#lineSeparator()}, then it would be treated as a single CSV record.
     */
    public CsvDataType csv(T data, String... positions) throws TypeConversionException {
        CsvDataType csv = new CsvDataType("");
        if (data == null || StringUtils.isEmpty(data.getValue()) || ArrayUtils.isEmpty(positions)) { return csv; }

        String text = StringUtils.replace(data.getValue(), "\r\n", "\n");

        // in case user pass a delimiter, not positions
        if (positions.length == 1 && !NumberUtils.isCreatable(positions[0])) {
            csv = new CsvDataType(text);
            csv.setHeader(false);
            csv.setDelim(positions[0]);
            csv.setRecordDelim("\n");
            csv.setReadyToParse(true);
            csv.parse();
            return csv;
        }

        // in case user pass positions
        List<Integer> positionNums = new ArrayList<>();
        for (String position : positions) {
            if (NumberUtils.isCreatable(position)) {
                int num = NumberUtils.toInt(position);
                // make sure positions are ascending numbers
                if (positionNums.isEmpty() && num == 0) { continue; }
                if (!positionNums.isEmpty() && positionNums.get(positionNums.size() - 1) >= num) {
                    throw new TypeConversionException("CSV",
                                                      ArrayUtils.toString(positions),
                                                      "Positions must specify is ascending order");
                }

                positionNums.add(num);
            } else {
                throw new TypeConversionException("CSV", ArrayUtils.toString(positions), "Invalid positions specified");
            }
        }

        ExecutionContext context = ExecutionThread.get();
        String delim = context == null ? getDefault(TEXT_DELIM) : context.getTextDelim();

        StringBuilder csvContent = new StringBuilder();

        String[] lines = StringUtils.splitByWholeSeparator(text, "\n");
        Arrays.stream(lines).forEach(line -> {
            StringBuilder record = new StringBuilder();
            int lastPos = 0;
            for (int pos : positionNums) {
                String extracted = StringUtils.substring(line, lastPos, pos - 1);
                if (StringUtils.contains(extracted, delim)) {
                    extracted = TextUtils.wrapIfMissing(StringUtils.trim(extracted), "\"", "\"");
                }
                record.append(extracted).append(delim);
                lastPos = pos - 1;
            }
            record.append(StringUtils.substring(line, lastPos));
            csvContent.append(record.toString()).append("\n");
        });

        csv = new CsvDataType(StringUtils.removeEnd(csvContent.toString(), "\n"));
        csv.setHeader(false);
        csv.setDelim(delim);
        csv.setRecordDelim("\n");
        csv.setKeepQuote(true);
        csv.setReadyToParse(true);
        csv.parse();
        return csv;
    }

    public T base64encode(T data) {
        if (data == null || StringUtils.isEmpty(data.getValue())) { return data; }
        data.setValue(TextUtils.base64encode(data.getValue()));
        return data;
    }

    public T base64decode(T data) {
        if (data == null || StringUtils.isEmpty(data.getValue())) { return data; }
        data.setValue(TextUtils.base64decode(data.getValue()));
        return data;
    }

    public T base64decodeThenSave(T data, String path, String append) {
        if (data == null || data.getValue() == null) { return data; }
        if (StringUtils.isBlank(path)) { throw new IllegalArgumentException("path is empty/blank"); }

        byte[] decoded = TextUtils.base64decodeAsBytes(data.getValue());
        FileUtil.writeBinaryFile(path, BooleanUtils.toBoolean(append), decoded);
        data.setValue(new String(decoded));
        return data;
    }

    public XmlDataType xml(T data) throws TypeConversionException {
        String text = data.value;

        ExecutionContext context = ExecutionThread.get();
        boolean resolveUrl = context.getBooleanData(EXPRESSION_RESOLVE_URL, getDefaultBool(EXPRESSION_RESOLVE_URL));

        // could be a proper XML already
        org.jdom2.Document xmlDoc;
        try {
            xmlDoc = XmlUtils.parse(text);
            if (xmlDoc != null) { return new XmlDataType(text); }
        } catch (JDOMException | IOException e) {
            // nope.. maybe it's a malformed HTML or a URL reference
            try {
                Document document = resolveUrl && ResourceUtils.isWebResource(text) ?
                                    Jsoup.connect(text).get() : Jsoup.parse(text);
                if (document == null) {
                    throw new TypeConversionException("XML", text, "No valid XML content found via '" + text + "'");
                }

                document.outputSettings().prettyPrint(true).syntax(Syntax.xml);
                String html = StringUtils.trim(document.html());
                // remove doc declaration since XML parser might not like that
                // i.e. <!DOCTYPE html PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
                if (StringUtils.startsWith(html, "<!")) {
                    html = StringUtils.trim(StringUtils.substringAfter(html, ">"));
                }

                xmlDoc = XmlUtils.parse(html);
                if (xmlDoc != null) { return new XmlDataType(html); }
            } catch (IOException ex) {
                // can't get content via URL
                throw new TypeConversionException("XML", text, "Error downloading '" + text + "': " + e.getMessage());
            } catch (JDOMException ex) {
                throw new TypeConversionException("XML", text, "Error parsing '" + text + "': " + e.getMessage());
            }
        }

        throw new TypeConversionException("XML", text, "Unable to convert TEXT to XML via '" + text + "'");
    }

    public JsonDataType json(T data) throws TypeConversionException {
        String text = data.value;

        ExecutionContext context = ExecutionThread.get();
        boolean resolveUrl = context.getBooleanData(EXPRESSION_RESOLVE_URL, getDefaultBool(EXPRESSION_RESOLVE_URL));

        // could be a proper JSON already
        JsonElement json;
        try {
            if (resolveUrl && ResourceUtils.isWebResource(text)) { text = WsCommand.resolveWebContent(text); }

            text = JsonDataType.escapeUnicode(text);
            json = GSON.fromJson(text, JsonElement.class);
            if (json != null) { return new JsonDataType(text); }
        } catch (IOException e) {
            // nope.. maybe it's a malformed HTML or a URL reference
            throw new TypeConversionException("JSON",
                                              data.value,
                                              "Error downloading '" + data.value + "': " + e.getMessage());
        }

        throw new TypeConversionException("JSON", text, "Cannot convert TEXT to JSON: " + data.value);
    }

    public BinaryDataType binary(T data) {
        return data == null || StringUtils.isEmpty(data.getValue()) ?
               new BinaryDataType() : new BinaryDataType(data.getValue().getBytes());
    }

    /**
     * parse current `TEXT` content as a `CSV` expression
     */
    public CsvDataType parseAsCsv(T data, String... configs) throws TypeConversionException {
        if (data == null || StringUtils.isEmpty(data.getValue())) {
            throw new TypeConversionException("TEXT", "Invalid TEXT expression: null");
        }

        CsvDataType csv = new CsvDataType(data.value);
        csv.configAndParse(configs);
        return csv;
    }

    public ListDataType extract(T data, String beginRegex, String endRegex, String inclusive) {
        ListDataType list = new ListDataType();
        if (data == null || data.getValue() == null) { return list; }

        if (StringUtils.isEmpty(beginRegex)) { throw new IllegalArgumentException("beginRegex is empty/blank"); }
        if (StringUtils.isEmpty(endRegex)) { throw new IllegalArgumentException("endRegex is empty/blank"); }

        boolean includeBeginEnd = BooleanUtils.toBoolean(inclusive);
        List<String> matches = RegexUtils.extract(data.getValue(), beginRegex + ".*?" + endRegex);
        if (CollectionUtils.isEmpty(matches)) { return list; }

        String[] extracted = matches.stream().map(
                                        match -> includeBeginEnd ?
                                                 match :
                                                 RegexUtils.removeMatches(RegexUtils.removeMatches(match, "^" + beginRegex), endRegex + "$"))
                                    .toArray(String[]::new);
        list.setValue(extracted);
        list.setTextValue(TextUtils.toString(extracted, list.getDelim(), "", ""));
        return list;
    }

    public T ifEqual(T data, String test, String match, String notMatch) {
        if (data == null) { return data; }
        data.setValue(StringUtils.equals(data.getValue(), test) ? match : notMatch);
        return data;
    }

    public T ifContain(T data, String test, String match, String notMatch) {
        if (data == null || StringUtils.isEmpty(test)) { return data; }
        data.setValue(StringUtils.contains(data.getValue(), test) ? match : notMatch);
        return data;
    }

    public T ifMatch(T data, String regex, String match, String notMatch) {
        if (data == null || StringUtils.isEmpty(regex)) { return data; }
        data.setValue(RegexUtils.isExact(data.getValue(), regex) ? match : notMatch);
        return data;
    }

    public T repeat(T data, String times) {
        if (data == null || !NumberUtils.isDigits(times)) { return data; }
        data.setValue(StringUtils.repeat(data.getValue(), NumberUtils.toInt(times)));
        return data;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM_LIST; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }
}
