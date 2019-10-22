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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jdom2.Element;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.commons.utils.TextUtils.ListItemConverter;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.NexialFilter;
import org.nexial.core.model.NexialFilter.ListItemConverterImpl;
import org.nexial.core.plugins.io.ExcelHelper;
import org.nexial.core.utils.ConsoleUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.univocity.parsers.common.record.Record;

import static java.lang.System.lineSeparator;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Rdbms.*;
import static org.nexial.core.SystemVariables.getDefaultInt;
import static org.nexial.core.model.NexialFilterComparator.Equal;
import static org.nexial.core.variable.ExpressionUtils.fixControlChars;

public class CsvTransformer<T extends CsvDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM = discoverFunctions(CsvTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM, CsvTransformer.class, CsvDataType.class);

    private static final String PAIR_DELIM = "|";
    private static final String NAME_VALUE_DELIM = "=";
    private static final String ATTR_INDEX = "index";
    private static final String ATTR_NAME = "name";
    private static final String NODE_ROOT = "rows";
    private static final String NODE_ROW = "row";
    private static final String NODE_CELL = "cell";

    private static final Pattern COMPILED_FILTER_REGEX_PATTERN = Pattern.compile(FILTER_REGEX_PATTERN);

    public TextDataType text(T data) { return super.text(data); }

    public T parse(T data, String... configs) {
        if (ArrayUtils.isNotEmpty(configs)) {
            ExecutionContext context = ExecutionThread.get();

            String config = TextUtils.toString(configs, PAIR_DELIM, null, null);
            // escape pipe and comma
            config = StringUtils.replace(config, "\\" + PAIR_DELIM, FILTER_TEMP_DELIM1);
            config = StringUtils.replace(config, "\\,", FILTER_TEMP_DELIM2);

            Map<String, String> configMap = TextUtils.toMap(config, PAIR_DELIM, NAME_VALUE_DELIM);
            // unescape pipes and comma
            configMap.forEach((key, value) -> {
                value = StringUtils.replace(value, FILTER_TEMP_DELIM1, PAIR_DELIM);
                value = StringUtils.replace(value, FILTER_TEMP_DELIM2, ",");
                configMap.put(key, value);
            });

            data.setDelim(configMap.containsKey("delim") ? configMap.get("delim") : context.getTextDelim());
            if (configMap.containsKey("header")) { data.setHeader(BooleanUtils.toBoolean(configMap.get("header"))); }
            if (configMap.containsKey("quote")) { data.setQuote(configMap.get("quote")); }
            if (configMap.containsKey("keepQuote")) {
                data.setKeepQuote(BooleanUtils.toBoolean(configMap.get("keepQuote")));
            }
            if (configMap.containsKey("recordDelim")) {
                data.setRecordDelim(fixControlChars(configMap.get("recordDelim")));
            }
            if (configMap.containsKey("indexOn")) {
                data.addIndices(Array.toArray(fixControlChars(configMap.get("indexOn")), "\\,"));
            }

            // 512 is the default
            int maxColumns = context.getIntData(CSV_MAX_COLUMNS, getDefaultInt(CSV_MAX_COLUMNS));
            if (configMap.containsKey("maxColumns")) {
                data.setMaxColumns(NumberUtils.toInt(configMap.get("maxColumns"), maxColumns));
            } else {
                data.setMaxColumns(maxColumns);
            }

            // 4096 is the default width
            int maxColumnWidth = context.getIntData(CSV_MAX_COLUMN_WIDTH, getDefaultInt(CSV_MAX_COLUMN_WIDTH));
            if (configMap.containsKey("maxColumnWidth")) {
                data.setMaxColumnWidth(NumberUtils.toInt(configMap.get("maxColumnWidth"), maxColumnWidth));
            } else {
                data.setMaxColumnWidth(maxColumnWidth);
            }

            if (configMap.containsKey("trim")) { data.setTrimValue(BooleanUtils.toBoolean(configMap.get("trim"))); }
        }

        data.setReadyToParse(true);
        data.parse();
        return data;
    }

    public ListDataType row(T data, String index) {
        if (data == null || data.getValue() == null || !NumberUtils.isDigits(index)) { return null; }
        return recordToList(IterableUtils.get(data.getValue(), NumberUtils.toInt(index)));
    }

    public ListDataType column(T data, String column) {
        if (data == null || CollectionUtils.isEmpty(data.getValue()) || StringUtils.isBlank(column)) { return null; }

        int index;

        // if no header specified, then we can only use index (not column name)
        if (!data.isHeader()) {
            if (!NumberUtils.isDigits(column)) {
                ConsoleUtils.log("Invalid column " + column + ".  Columns of a CSV without header can only be " +
                                 "referenced via index");
                return null;
            }

            index = NumberUtils.toInt(column);
        } else {
            index = NumberUtils.isDigits(column) ? NumberUtils.toInt(column) : data.getHeaderPosition(column);
        }

        if (index < 0 || index >= data.getColumnCount()) {
            ConsoleUtils.log("Invalid column " + column);
            return null;
        }

        List<String> columnValues = new ArrayList<>();
        int pos = index;
        data.getValue().forEach(record -> columnValues.add(record != null ? record.getString(pos) : null));

        // converting to LIST object with the same delimiter used initially to parse `textValue`
        ExecutionContext context = ExecutionThread.get();
        String delim = StringUtils.defaultIfEmpty(data.getDelim(), context != null ? context.getTextDelim() : ",");
        return toListDataType(columnValues, String.valueOf(delim.charAt(0)));
    }

    public ListDataType headers(T data) {
        if (data == null || data.getValue() == null || !data.isHeader()) { return null; }
        return toListDataType(data.getHeaders(), data.getDelim());
    }

    public T filter(T data, String filter) throws TypeConversionException {
        if (data == null || data.getValue() == null || StringUtils.isBlank(filter)) { return data; }

        if (!data.isHeader()) {
            throw new TypeConversionException(data.getName(),
                                              filter,
                                              "Unable to filter() on CSV data that does not have header");
        }

        filter = getFormattedFilter(filter);
        ListItemConverter<NexialFilter> converter = new ListItemConverterImpl();
        List<NexialFilter> filters = TextUtils.toList(filter, PAIR_DELIM, converter);
        if (CollectionUtils.isEmpty(filters)) { return data; }

        List<Record> filtered = new ArrayList<>();
        List<Record> rows = data.getValue();

        rows.forEach(row -> {
            boolean matched = true;
            for (NexialFilter f : filters) {
                if (!matchFilter(row, f)) {
                    matched = false;
                    break;
                }
            }

            if (matched) { filtered.add(row); }
        });

        data.reset(filtered);
        return data;
    }

    public ListDataType fetch(T data, String filter) {
        if (data == null || data.getValue() == null || StringUtils.isBlank(filter)) { return null; }

        filter = getFormattedFilter(filter);
        ListItemConverter<NexialFilter> converter = new ListItemConverterImpl();
        List<NexialFilter> filters = TextUtils.toList(filter, PAIR_DELIM, converter);
        if (CollectionUtils.isEmpty(filters)) { return null; }

        // short-circuit via flyweight pattern: only if there's only 1 equal-filter
        if (CollectionUtils.size(filters) == 1) {
            NexialFilter onlyFilter = filters.get(0);
            if (onlyFilter.getComparator() == Equal && data.isIndexed(onlyFilter.getSubject())) {
                return recordToList(data.retrieveFromCache(onlyFilter.getSubject(), onlyFilter.getControls()));
            }
        }

        List<Record> rows = data.getValue();
        for (Record row : rows) {
            boolean matched = true;
            for (NexialFilter f : filters) {
                if (!matchFilter(row, f)) {
                    matched = false;
                    break;
                }
            }

            if (matched) { return recordToList(row); }
        }

        return null;
    }

    public T sortAscending(T data, String column) {
        if (data == null || data.getValue() == null || StringUtils.isBlank(column)) { return null; }
        if (!data.isHeader()) {
            ConsoleUtils.error("CSV parsed without header, hence unable to sort");
            return null;
        }

        data.sortAscending(column);
        return data;
    }

    public T sortDescending(T data, String column) {
        if (data == null || data.getValue() == null || StringUtils.isBlank(column)) { return null; }
        if (!data.isHeader()) {
            ConsoleUtils.error("CSV parsed without header, hence unable to sort");
            return null;
        }

        data.sortDescending(column);
        return data;
    }

    public T removeRows(T data, String... matches) {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(matches)) { return data; }

        // todo: support filter by column index (e.g. #2 != 02)

        // could be a list of row indices?
        if (Arrays.stream(matches).allMatch(NumberUtils::isDigits)) {
            // all args are integers, so this is a request to remove row by index
            int[] targetRows = Arrays.stream(matches).mapToInt(NumberUtils::toInt).toArray();
            data.removeRows(targetRows);
            return data;
        }

        // if not row indices, then they must be filters (no varargs here; only first value considered)
        String filter = getFormattedFilter(matches[0]);
        ListItemConverter<NexialFilter> converter = new ListItemConverterImpl();
        List<NexialFilter> filters = TextUtils.toList(filter, PAIR_DELIM, converter);
        if (CollectionUtils.isEmpty(filters)) { return data; }

        // short-circuit via flyweight pattern: only if there's only 1 equal-filter
        if (CollectionUtils.size(filters) == 1) {
            NexialFilter onlyFilter = filters.get(0);
            if (onlyFilter.getComparator() == Equal && data.isIndexed(onlyFilter.getSubject())) {
                data.remove(onlyFilter.getSubject(), onlyFilter.getControls());
                return data;
            }
        }

        List<Record> remained = new ArrayList<>();
        List<Record> rows = data.getValue();
        rows.forEach(row -> {
            // matched means such row must not be included in remained
            boolean matched = true;
            for (NexialFilter f : filters) {
                if (!matchFilter(row, f)) {
                    matched = false;
                    break;
                }
            }

            if (!matched) { remained.add(row); }
        });

        data.reset(remained);
        return data;
    }

    /**
     * columnNamesOrIndices can be vararg, where each can be a pipe-delimited list.
     */
    public T removeColumns(T data, String... columnNamesOrIndices) {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(columnNamesOrIndices)) { return data; }

        Set<Integer> indicesToRemove = toIndices(data, columnNamesOrIndices);
        if (CollectionUtils.isEmpty(indicesToRemove)) { return data; }

        String recordDelim = data.getRecordDelim();
        String delim = data.getDelim();
        StringBuilder csvModified = new StringBuilder();

        if (data.isHeader()) {
            List<String> modifiedHeaders = new ArrayList<>();
            for (int i = 0; i < data.getHeaders().size(); i++) {
                if (indicesToRemove.contains(i)) { continue; }
                modifiedHeaders.add(TextUtils.csvSafe(data.getHeaders().get(i), data.getDelim(), true));
            }

            csvModified.append(TextUtils.toString(modifiedHeaders, delim)).append(recordDelim);
        }

        List<Record> csvRecords = data.getValue();
        csvRecords.forEach(one -> {
            StringBuilder rowModified = new StringBuilder();
            String[] values = one.getValues();
            for (int i = 0; i < values.length; i++) {
                if (indicesToRemove.contains(i)) { continue; }
                rowModified.append(TextUtils.csvSafe(values[i], data.getDelim(), true)).append(delim);
            }

            csvModified.append(StringUtils.removeEnd(rowModified.toString(), delim)).append(recordDelim);
        });

        data.setTextValue(StringUtils.removeEnd(csvModified.toString(), recordDelim));
        data.parse();
        return data;
    }

    public T retainColumns(T data, String... columnNamesOrIndices) {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(columnNamesOrIndices)) { return data; }

        Set<Integer> indicesToRetain = toIndices(data, columnNamesOrIndices);
        if (CollectionUtils.isEmpty(indicesToRetain)) { return data; }

        String recordDelim = data.getRecordDelim();
        String delim = data.getDelim();
        StringBuilder csvModified = new StringBuilder();

        if (data.isHeader()) {
            List<String> modifiedHeaders = new ArrayList<>();
            for (int i = 0; i < data.getHeaders().size(); i++) {
                if (indicesToRetain.contains(i)) {
                    modifiedHeaders.add(TextUtils.csvSafe(data.getHeaders().get(i), data.getDelim(), true));
                }
            }

            csvModified.append(TextUtils.toString(modifiedHeaders, delim)).append(recordDelim);
        }

        List<Record> csvRecords = data.getValue();
        csvRecords.forEach(one -> {
            StringBuilder rowModified = new StringBuilder();
            String[] values = one.getValues();
            for (int i = 0; i < values.length; i++) {
                if (indicesToRetain.contains(i)) {
                    rowModified.append(TextUtils.csvSafe(values[i], data.getDelim(), true)).append(delim);
                }
            }

            csvModified.append(StringUtils.removeEnd(rowModified.toString(), delim)).append(recordDelim);
        });

        data.setTextValue(StringUtils.removeEnd(csvModified.toString(), recordDelim));
        data.parse();
        return data;
    }

    public T renameColumn(T data, String find, String replace) {
        if (data == null ||
            data.getValue() == null ||
            !data.isHeader() ||
            // StringUtils.isBlank(find) ||
            StringUtils.isBlank(replace)) { return data; }

        find = StringUtils.trim(find);
        replace = StringUtils.trim(replace);

        List<String> headers = data.getHeaders();
        if (headers.contains(find)) { headers.set(headers.indexOf(find), replace); }

        data.reset(data.getValue());
        data.parse();
        return data;
    }

    public T replaceColumnRegex(T data, String searchFor, String replaceWith, String columnNameOrIndices) {
        if (data == null || data.getValue() == null ||
            StringUtils.isBlank(searchFor) || StringUtils.isBlank(replaceWith) ||
            StringUtils.isBlank(columnNameOrIndices)) {
            return data;
        }

        Set<Integer> indicesToSearch = toIndices(data, columnNameOrIndices);
        if (CollectionUtils.isEmpty(indicesToSearch)) { return data; }

        String recordDelim = data.getRecordDelim();
        String delim = data.getDelim();
        StringBuilder csvModified = new StringBuilder();

        if (data.isHeader()) { csvModified.append(TextUtils.toString(data.getHeaders(), delim)).append(recordDelim); }

        List<Record> csvRecords = data.getValue();
        csvRecords.forEach(row -> {
            StringBuilder rowModified = new StringBuilder();
            String[] cells = row.getValues();
            for (int i = 0; i < cells.length; i++) {
                String cell = cells[i];
                cell = TextUtils.csvSafe(indicesToSearch.contains(i) ?
                                         RegexUtils.replace(cell, searchFor, replaceWith) : cell,
                                         delim,
                                         true);
                rowModified.append(cell).append(delim);
            }

            csvModified.append(StringUtils.removeEnd(rowModified.toString(), delim)).append(recordDelim);
        });

        data.setTextValue(StringUtils.removeEnd(csvModified.toString(), recordDelim));
        data.parse();
        return data;
    }

    public T distinct(T data) {
        if (data == null || data.getValue() == null) { return data; }

        String recordDelim = data.getRecordDelim();
        String delim = data.getDelim();

        // store the distinct rows
        StringBuilder csvModified = new StringBuilder();

        // track all the distinct rows
        List<String> parsed = new ArrayList<>();

        if (data.isHeader()) { csvModified.append(TextUtils.toString(data.getHeaders(), delim)).append(recordDelim); }

        List<Record> csvRecords = data.getValue();
        csvRecords.forEach(one -> {
            StringBuilder rowModified = new StringBuilder();
            String[] values = one.getValues();
            String combinedValues = TextUtils.toString(values, "|", "", "");
            if (parsed.contains(combinedValues)) {
                ConsoleUtils.log("[CSV] skipping duplicate row: " + combinedValues);
            } else {
                parsed.add(combinedValues);

                for (String value : values) { rowModified.append(TextUtils.csvSafe(value, delim, true)).append(delim); }
                csvModified.append(StringUtils.removeEnd(rowModified.toString(), delim)).append(recordDelim);
            }
        });

        data.setTextValue(StringUtils.removeEnd(csvModified.toString(), recordDelim));
        data.parse();
        return data;
    }

    public NumberDataType rowCount(T data) throws TypeConversionException {
        NumberDataType count = new NumberDataType("0");

        if (data == null || data.getValue() == null) { return count; }

        count.setValue(CollectionUtils.size(data.getValue()));
        count.setTextValue(count.getValue() + "");
        return count;
    }

    public NumberDataType size(T data) throws TypeConversionException { return rowCount(data); }

    public NumberDataType length(T data) throws TypeConversionException { return size(data); }

    public NumberDataType columnCount(T data) throws TypeConversionException {
        NumberDataType count = new NumberDataType("0");

        if (data == null || data.getValue() == null) { return count; }

        count.setValue(data.getColumnCount());
        count.setTextValue(count.getValue() + "");
        return count;
    }

    @NotNull
    public JsonDataType json(T data) throws TypeConversionException {
        JsonDataType jsonDataType = new JsonDataType("{}");
        if (data == null || CollectionUtils.isEmpty(data.getValue())) { return jsonDataType; }

        JsonArray jsonArray = new JsonArray();
        data.getValue().forEach(record -> {
            if (data.isHeader()) {
                JsonObject oneRow = new JsonObject();
                data.getHeaders().forEach(column -> oneRow.addProperty(column, record.getString(column)));
                jsonArray.add(oneRow);
            } else {
                JsonArray oneRow = new JsonArray();
                Arrays.stream(record.getValues()).forEach(oneRow::add);
                jsonArray.add(oneRow);
            }
        });

        jsonDataType.setTextValue(GSON.toJson(jsonArray));
        jsonDataType.init();
        return jsonDataType;
    }

    @NotNull
    public XmlDataType xml(T data, String root, String row, String cell) throws TypeConversionException {
        if (StringUtils.isBlank(root)) { root = NODE_ROOT; }
        if (StringUtils.isBlank(row)) { row = NODE_ROW; }
        if (StringUtils.isBlank(cell)) { cell = NODE_CELL; }

        XmlDataType xmlDataType = new XmlDataType("<" + root + "></" + root + ">");
        if (data == null || CollectionUtils.isEmpty(data.getValue())) { return xmlDataType; }

        String cellNodeName = cell;
        String rowNodeName = row;

        Element rootNode = xmlDataType.getValue();
        data.getValue().forEach(record -> {
            Element rowElement = new Element(rowNodeName);

            if (data.isHeader()) {
                List<String> headers = data.getHeaders();
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    rowElement.addContent(new Element(cellNodeName)
                                              .setAttribute(ATTR_INDEX, i + "")
                                              .setAttribute(ATTR_NAME, header + "")
                                              .setText(record.getString(header)));
                }
            } else {
                String[] values = record.getValues();
                for (int i = 0; i < values.length; i++) {
                    rowElement.addContent(new Element(cellNodeName).setAttribute(ATTR_INDEX, i + "")
                                                                   .setText(values[i]));
                }
            }

            rootNode.addContent(rowElement);
        });

        try {
            xmlDataType.reset(rootNode);
            return xmlDataType;
        } catch (IOException e) {
            throw new TypeConversionException("XML", rootNode.toString(), e.getMessage(), e);
        }
    }

    @NotNull
    public T transpose(T data) {
        if (data == null || data.getValue() == null) { return null; }

        List<List<String>> transposed = new ArrayList<>();
        for (Record row : data.getValue()) {
            if (row == null) { continue; }

            String[] values = row.getValues();
            int size = ArrayUtils.getLength(values);
            if (IterableUtils.isEmpty(transposed)) {
                // use first row (original) as guide.. but we'll allow for more rows if more found later in the array
                for (int j = 0; j < size; j++) { transposed.add(new ArrayList<>()); }
            }

            // adjust for wider rows (wider than the first row)
            int additionalRows = Math.max(size - transposed.size(), 0);
            for (int j = 0; j < additionalRows; j++) { transposed.add(new ArrayList<>()); }
            for (int j = 0; j < size; j++) { transposed.get(j).add(Objects.toString(values[j])); }
        }

        String delim = data.getDelim();
        String recordDelim = data.getRecordDelim();

        StringBuilder buffer = new StringBuilder();
        transposed.forEach(rows -> {
            StringBuilder oneRow = new StringBuilder();
            rows.forEach(cell -> oneRow.append(TextUtils.csvSafe(cell, delim, true)).append(delim));
            buffer.append(StringUtils.removeEnd(oneRow.toString(), delim)).append(recordDelim);
        });

        data.setTextValue(StringUtils.removeEnd(buffer.toString(), recordDelim));
        data.parse();
        return data;
    }

    public ExpressionDataType save(T data, String path, String append) { return super.save(data, path, append); }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    public T pack(T data) {
        if (data == null || data.getValue() == null) { return data; }

        String[] rows = StringUtils.splitByWholeSeparator(data.getTextValue(), data.getRecordDelim());
        if (ArrayUtils.isEmpty(rows)) { return data; }

        StringBuilder packed = new StringBuilder();

        Arrays.stream(rows).forEach(row -> {
            // only keep the rows that are not empty and contains non-whitespace characters
            if (StringUtils.isNotBlank(row) &&
                !StringUtils.containsOnly(StringUtils.deleteWhitespace(row), data.getDelim())) {
                packed.append(row).append(data.getRecordDelim());
            }
        });

        data.setTextValue(StringUtils.removeEnd(packed.toString(), data.getRecordDelim()));
        data.parse();
        return data;
    }

    public ExcelDataType excel(T data, String file, String sheet, String startCell)
        throws IOException, TypeConversionException {
        if (StringUtils.isEmpty(file)) { return null; }
        if (StringUtils.isEmpty(sheet)) { return null; }
        if (data.getRowCount() < 1 || data.getColumnCount() < 1) { return null; }

        List<List<String>> rowsAndColumns = new ArrayList<>();
        if (data.isHeader()) { rowsAndColumns.add(data.getHeaders()); }
        data.getValue().forEach(record -> rowsAndColumns.add(Arrays.asList(record.getValues())));

        ExcelHelper.csv2xlsx(file, sheet, startCell, rowsAndColumns);

        return new ExcelDataType(file);
    }

    public TextDataType render(T data, String template) throws TypeConversionException {
        TextDataType text = new TextDataType("");
        if (data == null || data.getValue() == null || CollectionUtils.isEmpty(data.getValue())) { return text; }
        if (StringUtils.isBlank(template)) { return text; }

        String templateValue = ExpressionUtils.resumeExpression(template, String.class);
        if (StringUtils.isEmpty(templateValue)) {
            templateValue = ExpressionUtils.handleExternal(data.getName(), template, false);
        }
        String templateText = templateValue;

        if (!data.isHeader()) {
            throw new TypeConversionException(data.getName(),
                                              data.getTextValue(),
                                              "Unable to invoke render() since current CSV expression is not " +
                                              "parsed with header=true");
        }

        ExecutionContext context = ExecutionThread.get();
        if (context == null) {
            ConsoleUtils.error("Unable to retrieve context");
            return text;
        }

        Set<String> headerNames = new HashSet<>(data.getHeaders());

        // KEEP old existing data with conflicting names
        Map<String, Object> oldData = new HashMap<>();
        headerNames.forEach(header -> {
            if (context.hasData(header)) { oldData.put(header, context.getObjectData(header)); }
        });

        StringBuilder buffer = new StringBuilder();
        data.getValue().forEach(record -> {
            headerNames.forEach(header -> context.setData(header, record.getString(header)));
            buffer.append(context.replaceTokens(templateText));
            headerNames.forEach(context::removeData);
        });

        // set old data back
        if (MapUtils.isNotEmpty(oldData)) { oldData.forEach(context::setData); }

        text.setValue(buffer.toString());
        return text;
    }

    /**
     * transform current CSV instance into a INI file format.  The first column is treated as the name, and all
     * remaining column as the value (separated by comma).
     */
    public ConfigDataType config(T data) throws TypeConversionException {
        ConfigDataType config = new ConfigDataType("");

        if (data == null || CollectionUtils.isEmpty(data.getValue())) { return config; }
        if (!data.isHeader()) {
            ConsoleUtils.log("Cannot convert to CONFIG since this CSV expression does not have header");
            return config;
        }

        ExecutionContext context = ExecutionThread.get();
        String delim = context == null ? "," : context.getTextDelim();

        Set<String> columnNames = new HashSet<>(data.getHeaders());

        StringBuilder props = new StringBuilder();
        columnNames.forEach(columnName -> {
            List<String> columns = new ArrayList<>();
            data.getValue().forEach(row -> columns.add(row.getString(columnName)));
            props.append(columnName).append("=").append(TextUtils.toString(columns, delim)).append(lineSeparator());
        });

        config.setTextValue(props.toString());
        config.init();
        return config;
    }

    public TextDataType asciiTable(T data) throws TypeConversionException {
        TextDataType ascii = new TextDataType("");

        if (data == null) { return ascii; }

        List<Record> records = data.getValue();
        if (CollectionUtils.isEmpty(records)) { return ascii; }

        ascii.setTextValue(TextUtils.createAsciiTable(data.getHeaders(), records, Record::getString));
        ascii.setValue(ascii.getTextValue());
        return ascii;
    }

    public TextDataType htmlTable(T data) throws TypeConversionException {
        TextDataType html = new TextDataType("");

        if (data == null) { return html; }

        List<Record> records = data.getValue();
        if (CollectionUtils.isEmpty(records)) { return html; }

        html.setTextValue(TextUtils.createHtmlTable(data.getHeaders(), records, Record::getString, null));
        html.setValue(html.getTextValue());
        return html;
    }

    /**
     * merge the CSV data as represented via {@code csvVariable} to current CSV instance.  The {@code refColumn}, if
     * specified, will be used as the basis of the merge so that rows with the same value (of the specified reference
     * column) will be merged together.  Consequently this is sort the current CSV instance as a side effect.  If
     * {@code refColumn} is not specified, then the merge will be done in the line-by-line basis.
     */
    public T merge(T data, String csvVariable, String refColumn) throws TypeConversionException {
        if (StringUtils.isBlank(csvVariable)) { return data; }

        CsvDataType mergeFrom = resolveExpressionTypeInContext(csvVariable);
        if (mergeFrom == null || mergeFrom.getValue().isEmpty()) { return data; }
        if (data == null || StringUtils.isEmpty(data.getTextValue())) { return (T) mergeFrom; }

        // both must have headers, or both must not
        if (data.isHeader() != mergeFrom.isHeader()) {
            throw new IllegalArgumentException("Cannot merge 2 set of CSV where only one of them has headers");
        }

        // both without header
        // can't merged by column names.. so we'll just slap new data to the end of each line
        if (!data.isHeader()) { return mergeWithoutHeaders(data, mergeFrom); }

        // both with header
        // check that we don't have conflicting header (except `refColumn`)
        List<String> toHeaders = data.getHeaders();
        List<String> fromHeaders = mergeFrom.getHeaders();
        for (String column : toHeaders) {
            if (StringUtils.equals(column, refColumn)) { continue; }
            if (fromHeaders.contains(column)) {
                throw new TypeConversionException("CSV", column,
                                                  "Unable to merge from variable '" + csvVariable +
                                                  "' because of conflicting column '" + column + "'");
            }
        }

        return mergeWithHeader(data, mergeFrom, refColumn);
    }

    public T groupCount(T data, String... columns) throws TypeConversionException {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(columns)) { return data; }

        assertValidColumns(data, columns);

        Map<String, Integer> counts = new TreeMap<>();
        data.getValue().forEach(record -> {
            String value = "";
            for (String column : columns) {
                value += (StringUtils.isNotEmpty(value) ? CSV_FIELD_DEIM : "") + record.getString(column);
                counts.put(value, counts.containsKey(value) ? counts.get(value) + 1 : 1);
            }
        });

        StringBuilder groupCsv = new StringBuilder(TextUtils.toString(columns, CSV_FIELD_DEIM, "", "") +
                                                   CSV_FIELD_DEIM + "Count" + CSV_ROW_SEP);
        counts.forEach((value, count) -> {
            int numMissingDelim = columns.length - StringUtils.countMatches(value, CSV_FIELD_DEIM) - 1;
            groupCsv.append(value).append(StringUtils.repeat(CSV_FIELD_DEIM, numMissingDelim)).append(CSV_FIELD_DEIM)
                    .append(count).append(CSV_ROW_SEP);
        });

        return (T) new CsvDataType(StringUtils.removeEnd(groupCsv.toString(), CSV_ROW_SEP));
    }

    public T groupSum(T data, String... columns) throws TypeConversionException {
        if (data == null || data.getValue() == null || ArrayUtils.isEmpty(columns)) { return data; }

        assertValidColumns(data, columns);
        if (columns.length < 2) {
            throw new TypeConversionException("CSV", ArrayUtils.toString(columns), "Too few columns specified");
        }

        String sumColumn = columns[columns.length - 1];
        String[] groupColumns = ArrayUtils.remove(columns, columns.length - 1);

        Map<String, Number> sums = new TreeMap<>();
        data.getValue().forEach(record -> {
            Number sumValue = NumberUtils.createNumber(
                StringUtils.trim(StringUtils.replaceChars(record.getString(sumColumn), "\"'$,", "")));

            String value = "";
            for (String column : groupColumns) {
                value += (StringUtils.isNotEmpty(value) ? CSV_FIELD_DEIM : "") + record.getString(column);
                if (sums.containsKey(value)) {
                    Number currentSum = sums.get(value);
                    if (currentSum instanceof Integer && sumValue instanceof Integer) {
                        sums.put(value, sumValue.intValue() + currentSum.intValue());
                    } else {
                        sums.put(value, sumValue.doubleValue() + currentSum.doubleValue());
                    }
                } else {
                    sums.put(value, sumValue);
                }
            }
        });

        StringBuilder groupCsv = new StringBuilder(TextUtils.toString(groupColumns, CSV_FIELD_DEIM, "", "") +
                                                   CSV_FIELD_DEIM + "Sum" + CSV_ROW_SEP);
        sums.forEach((value, sum) -> {
            int numMissingDelim = groupColumns.length - StringUtils.countMatches(value, CSV_FIELD_DEIM) - 1;
            String sumString = sum + "";
            groupCsv.append(value).append(StringUtils.repeat(CSV_FIELD_DEIM, numMissingDelim)).append(CSV_FIELD_DEIM)
                    .append(sumString).append(CSV_ROW_SEP);
        });

        return (T) new CsvDataType(StringUtils.removeEnd(groupCsv.toString(), CSV_ROW_SEP));
    }

    public T saveRowData(T data, String rowIndex) {
        if (data == null || data.getValue() == null) { return data; }
        if (!data.isHeader()) {
            ConsoleUtils.error("Unable to perform this operation since the target CSV data does not have header");
            return data;
        }

        ExecutionContext context = ExecutionThread.get();
        if (context == null) {
            ConsoleUtils.error("Unable to retrieve context");
            return null;
        }

        if (!NumberUtils.isDigits(rowIndex)) {
            ConsoleUtils.error("Unable to perform this operation since 'rowIndex' is not a valid number");
            return data;
        }

        int index = NumberUtils.toInt(rowIndex);
        if (index < 0) {
            ConsoleUtils.error("Unable to perform this operation since 'rowIndex' is less than zero");
            return data;
        }
        if (index >= data.getRowCount()) {
            ConsoleUtils.error("Unable to perform this operation since 'rowIndex' is greater than available rows: " +
                               data.getRowCount());
            return data;
        }

        Record row = data.getValue().get(index);
        data.getHeaders().forEach(header -> context.setData(header, row.getString(header)));
        return data;
    }

    /**
     * surround the data of specified columns with specified characters (first element of <code>parameters</code>).
     * Suppose both column index or column name (starting from second parameters of <code>parameters</code>). Use
     * <code>*</code> for <b>ALL</b> columns.
     */
    public TextDataType surround(T data, String... parameters) throws TypeConversionException {
        TextDataType text = new TextDataType("");

        if (data == null || data.getValue() == null) { return text; }

        if (ArrayUtils.getLength(parameters) < 2) {
            text.setValue(data.textValue);
        } else {
            String surroundWith = parameters[0];
            String[] onColumns = ArrayUtils.remove(parameters, 0);
            text.setValue(data.surround(surroundWith, toIndices(data, onColumns)));
        }
        return text;
    }

    /**
     * turn a data variable into an instance of CsvDataType, if possible
     */
    public static CsvDataType resolveExpressionTypeInContext(String var) {
        ExecutionContext context = ExecutionThread.get();
        if (context == null) {
            ConsoleUtils.error("Unable to retrieve context");
            return null;
        }

        Object mergeFromObj = context.getObjectData(var);
        if (mergeFromObj == null) { return null; }

        if (mergeFromObj instanceof CsvDataType) { return (CsvDataType) mergeFromObj; }

        String delim = context.getTextDelim();
        String recordDelim = "\n";
        String msgPrefix = "Cannot convert to CSV since variable '" + var + "' ";
        StringBuilder text = new StringBuilder();

        // List<List<String>> ?
        if (mergeFromObj instanceof List) {
            try {
                List<List<String>> listOfList = (List<List<String>>) mergeFromObj;
                if (CollectionUtils.isEmpty(listOfList)) { return null; }

                listOfList.forEach(innerList -> text.append(TextUtils.toString(innerList, delim)).append(recordDelim));
            } catch (ClassCastException e) {
                ConsoleUtils.log(msgPrefix + "is not of type List<List<String>>");
                // nope that's not it...
                return null;
            }
        }

        if (mergeFromObj instanceof String[][]) {
            String[][] arrayOfArray = (String[][]) mergeFromObj;
            if (ArrayUtils.isEmpty(arrayOfArray)) { return null; }

            Arrays.stream(arrayOfArray)
                  .forEach(innerArray -> text.append(TextUtils.toString(innerArray, delim, "", ""))
                                             .append(recordDelim));
        }

        if (text.length() > 0) {
            try {
                CsvDataType csv = new CsvDataType(StringUtils.trim(text.toString()));
                csv.setDelim(delim);
                csv.setRecordDelim(recordDelim);
                csv.setReadyToParse(true);
                csv.parse();
                return csv;
            } catch (TypeConversionException e) {
                ConsoleUtils.log(msgPrefix + "contains unparseable data: " + e.getMessage());
                return null;
            }
        }

        ConsoleUtils.log(msgPrefix + "is of unsupported type: " + mergeFromObj.getClass());
        return null;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    @Override
    protected void saveContentAsAppend(ExpressionDataType data, File target) throws IOException {
        if (FileUtil.isFileReadable(target, 1) && data instanceof CsvDataType) {
            String currentContent = FileUtils.readFileToString(target, DEF_FILE_ENCODING);
            if (!StringUtils.endsWith(currentContent, "\n")) {
                FileUtils.writeStringToFile(target, ((CsvDataType) data).getRecordDelim(), DEF_FILE_ENCODING, true);
            }
        }
        super.saveContentAsAppend(data, target);
    }

    @NotNull
    protected Set<Integer> toIndices(T data, String... columnNamesOrIndices) {
        Set<Integer> indices = new TreeSet<>();

        // treat varargs and pipe-delimited list evenly.
        String[] selected = StringUtils.split(TextUtils.toString(columnNamesOrIndices, PAIR_DELIM, "", ""), PAIR_DELIM);
        if (ArrayUtils.isEmpty(selected)) { return indices; }

        int maxColumnIndex = data.getColumnCount() - 1;

        // special case: * means _ALL_ columns
        if (selected.length == 1 && StringUtils.equals(selected[0], "*")) {
            return IntStream.range(0, maxColumnIndex + 1).boxed().collect(Collectors.toCollection(TreeSet::new));
        }

        Arrays.stream(selected).forEach(column -> {
            if (NumberUtils.isDigits(column)) {
                // expects numeric indices (zero-based)
                if (!NumberUtils.isDigits(column)) {
                    throw new IllegalArgumentException(column + " is not a valid column index (zero-based)");
                }

                int index = NumberUtils.createNumber(column).intValue();
                if (index > maxColumnIndex) {
                    throw new IllegalArgumentException("column index " + column + " is invalid");
                }

                indices.add(index);
            } else {
                // expects header name
                if (!data.isHeader()) {
                    throw new IllegalArgumentException("no header is configured; " + column + " is not valid");
                }

                int index = data.getHeaderPosition(column);
                if (index == -1) { throw new IllegalArgumentException(column + " is not a valid column"); }

                indices.add(index);
            }
        });

        return indices;
    }

    protected void assertValidColumns(T data, String[] columns) throws TypeConversionException {
        Object[] invalidColumns = Arrays.stream(columns).filter(column -> !data.hasHeader(column)).toArray();
        boolean columnNotFound = ArrayUtils.isNotEmpty(invalidColumns);
        if (columnNotFound) {
            throw new TypeConversionException("CSV", Arrays.toString(invalidColumns), "Invalid column(s) specified");
        }
    }

    protected T mergeWithoutHeaders(T data, CsvDataType mergeFrom) {
        StringBuilder toBuffer = new StringBuilder();

        String toDelim = data.getDelim();
        String toRecordDelim = data.getRecordDelim();
        List<Record> toRecords = data.getValue();
        int toColumnCount = data.getColumnCount();

        String fromDelim = mergeFrom.getDelim();
        List<Record> fromRecords = mergeFrom.getValue();

        // merge all `mergeFrom` records into `data`
        for (int i = 0; i < fromRecords.size(); i++) {
            Record fromRecord = fromRecords.get(i);

            if (toRecords.size() > i) {
                Record toRecord = toRecords.get(i);
                toBuffer.append(TextUtils.toString(toRecord.getValues(), toDelim, "", ""));
            } else {
                toBuffer.append(StringUtils.repeat(toDelim, toColumnCount - 1));
            }

            toBuffer.append(toDelim)
                    .append(TextUtils.toString(fromRecord.getValues(), fromDelim, "", ""))
                    .append(toRecordDelim);
        }

        // in case `data` has more rows, we'll add them verbatim
        if (toRecords.size() > fromRecords.size()) {
            for (int i = fromRecords.size(); i < toRecords.size(); i++) {
                toBuffer.append(TextUtils.toString(toRecords.get(i).getValues(), toDelim, "", ""))
                        .append(toRecordDelim);
            }
        }

        data.setTextValue(StringUtils.removeEnd(toBuffer.toString(), toRecordDelim));
        data.parse();
        return data;
    }

    protected T mergeWithHeader(T to, CsvDataType from, String refColumn) throws TypeConversionException {
        List<String> toHeaders = to.getHeaders();
        if (StringUtils.isNotEmpty(refColumn) && !toHeaders.contains(refColumn)) {
            throw new TypeConversionException("CSV", refColumn,
                                              "Unable to merge because the specified reference column does not " +
                                              "exists in the 'MERGED TO' CSV");
        }

        List<String> fromHeaders = from.getHeaders();
        if (StringUtils.isNotEmpty(refColumn) && !fromHeaders.contains(refColumn)) {
            throw new TypeConversionException("CSV", refColumn,
                                              "Unable to merge because the specified reference column does not " +
                                              "exists in the 'MERGED FROM' CSV");
        }

        // sort both `to` and `from` so we can merge them correctly
        int toRefColumnPos = -1;
        int fromRefColumnPos = -1;
        if (StringUtils.isNotEmpty(refColumn)) {
            to = sortAscending(to, refColumn);
            from = sortAscending((T) from, refColumn);
            toRefColumnPos = toHeaders.indexOf(refColumn);
            fromRefColumnPos = fromHeaders.indexOf(refColumn);
        }

        // merge headers
        for (int i = 0; i < fromHeaders.size(); i++) {
            if (i == fromRefColumnPos) { continue; }
            String fromHeader = fromHeaders.get(i);
            toHeaders.add(fromHeader);
        }

        String toDelim = to.getDelim();
        String toRecordDelim = to.getRecordDelim();
        List<Record> toRecords = to.getValue();
        int toColumnCount = to.getColumnCount();

        List<Record> fromRecords = from.getValue();

        // header should always be the first line
        StringBuilder toBuffer = new StringBuilder(TextUtils.toString(toHeaders, toDelim, "", "") + toRecordDelim);

        // slap the data from `mergedFrom` - except that of the `refColumn` - to data
        // merge all `mergeFrom` records into `data`
        int j = 0;
        for (int i = 0; i < fromRecords.size(); i++) {
            StringBuilder recordBuffer = new StringBuilder();

            Record fromRecord = fromRecords.get(i);
            String fromRef = fromRefColumnPos == -1 ? null : fromRecord.getString(refColumn);
            String[] fromValues = fromRecord.getValues();
            if (ArrayUtils.isEmpty(fromValues) || StringUtils.isEmpty(TextUtils.toString(fromValues, "", "", ""))) {
                continue;
            }

            if (toRecords.size() <= j) {
                // no more records in `to` but still some more in `from`
                recordBuffer.append(createBlankToPortion(toColumnCount, toRefColumnPos, fromRef, toDelim))
                            .append(prepareMergingValues(fromValues, fromRefColumnPos, toDelim));
                toBuffer.append(recordBuffer.toString()).append(toRecordDelim);
                continue;
            }

            Record toRecord = toRecords.get(j);
            if (toRecord == null ||
                ArrayUtils.isEmpty(toRecord.getValues()) ||
                StringUtils.isEmpty(TextUtils.toString(toRecord.getValues(), "", "", ""))) {
                // skip this line since there's no data here.
                j++;
                i--;
                continue;
            }

            String toRef = toRefColumnPos == -1 ? null : toRecord.getString(refColumn);
            if (StringUtils.equals(toRef, fromRef)) {
                // found matching reference data -- we match both rows from `to` and `from`
                recordBuffer.append(TextUtils.toString(toRecord.getValues(), toDelim, "", ""))
                            .append(toDelim)
                            .append(prepareMergingValues(fromValues, fromRefColumnPos, toDelim));
                toBuffer.append(recordBuffer.toString()).append(toRecordDelim);
                // we can proceed to next record of `to`
                j++;
                continue;
            }

            if (StringUtils.isEmpty(toRef) || StringUtils.compare(toRef, fromRef) > 0) {
                // ref in `to` is greater than that of `from`
                // we'll create a line with 'blank' portion of `to` and then the actual values of `from`
                recordBuffer.append(createBlankToPortion(toColumnCount, toRefColumnPos, fromRef, toDelim))
                            .append(prepareMergingValues(fromValues, fromRefColumnPos, toDelim));
                toBuffer.append(recordBuffer.toString()).append(toRecordDelim);

                if (i >= (fromRecords.size() - 1)) {
                    // last record of `from`.. we need to handle this otherwise dangling record of `to`
                    toBuffer.append(TextUtils.toString(toRecord.getValues(), toDelim, "", "")).append(toRecordDelim);
                }
                continue;
            }

            if (StringUtils.compare(toRef, fromRef) < 0) {
                // ref in `to` is less than that of `from`
                // add nothing from non-existent `from` record
                recordBuffer.append(TextUtils.toString(toRecord.getValues(), toDelim, "", ""));
                toBuffer.append(recordBuffer.toString()).append(toRecordDelim);
                // revisit the same `from` record again to compare with the next `to` record
                j++;
                i--;
                continue;
            }
        }

        // need to catch all the additional rows in `from` records
        if (toRecords.size() > fromRecords.size()) {
            for (int i = fromRecords.size() + 1; i < toRecords.size(); i++) {
                Record record = toRecords.get(i);
                toBuffer.append(TextUtils.toString(record.getValues(), toDelim, "", ""))
                        .append(toRecordDelim);
            }
        }

        to.setTextValue(StringUtils.removeEnd(toBuffer.toString(), toRecordDelim));
        to.parse();
        return to;
    }

    protected String createBlankToPortion(int expectedColumnCount, int refColumnPos, String refValue, String delim) {
        StringBuilder blank = new StringBuilder();
        for (int i = 0; i < expectedColumnCount; i++) { blank.append(i == refColumnPos ? refValue : "").append(delim); }
        return blank.toString();
    }

    protected String prepareMergingValues(String[] values, int ignoreColumnPosition, String delim) {
        StringBuilder recordBuffer = new StringBuilder();
        for (int k = 0; k < values.length; k++) {
            // skip refColumn in `from` records since we already have the same data in the `to` record
            if (k == ignoreColumnPosition) { continue; }
            recordBuffer.append(values[k]).append(delim);
        }

        return StringUtils.removeEnd(recordBuffer.toString(), delim);
    }

    protected static ListDataType recordToList(@NotNull Record record) {
        return toListDataType(Arrays.asList(record.getValues()), PAIR_DELIM);
    }

    private static ListDataType toListDataType(@NotNull List<String> values, String delim) {
        String[] array = values.toArray(new String[0]);
        return new ListDataType(TextUtils.toString(array, delim, "", ""), delim);
    }

    private boolean matchFilter(Record row, NexialFilter filter) {
        if (row == null) { return false; }
        if (filter == null) { return true; }

        if (filter.isAnySubject()) {
            // for (String next : row) { if (filter.isMatch(next)) { return true; } }
            String[] values = row.getValues();
            for (String value : values) { if (filter.isMatch(value)) { return true; } }
            return false;
        }

        return filter.isMatch(row.getString(filter.getSubject()));
    }

    private String getFormattedFilter(String filter) {
        filter = StringUtils.replace(filter, "\\" + PAIR_DELIM, FILTER_TEMP_DELIM1);

        StringBuilder filterText = new StringBuilder();
        while (true) {
            Matcher matcher = COMPILED_FILTER_REGEX_PATTERN.matcher(filter);
            boolean isMatched = false;
            while (matcher.find()) {
                isMatched = true;
                String condition = matcher.group(2);
                String newCondition = StringUtils.replace(condition, PAIR_DELIM, FILTER_TEMP_DELIM2);
                filterText.append(StringUtils.substringBefore(filter, condition)).append(newCondition);
                filter = StringUtils.substringAfter(filter, condition);
            }
            if (!isMatched) { break; }
        }

        return filterText.toString() + filter;
    }
}