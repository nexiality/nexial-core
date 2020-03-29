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

import java.io.StringReader;
import java.util.*;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.list.TreeList;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.io.CsvParserBuilder;
import org.nexial.core.utils.ConsoleUtils;

import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;

import static java.lang.System.lineSeparator;
import static org.apache.commons.lang3.BooleanUtils.toBoolean;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.SystemVariables.getDefaultInt;
import static org.nexial.core.variable.CsvTransformer.NAME_VALUE_DELIM;
import static org.nexial.core.variable.CsvTransformer.PAIR_DELIM;
import static org.nexial.core.variable.ExpressionUtils.fixControlChars;

public class CsvDataType extends ExpressionDataType<List<Record>> {
    private CsvTransformer<CsvDataType> transformer = new CsvTransformer<>();
    private String delim;
    private String quote;
    private String recordDelim;
    private boolean header = true;
    private boolean trimValue = true;
    private int maxColumns;
    private int maxColumnWidth;
    private CsvParser parser;
    private List<String> headers;
    private int columnCount;
    private int rowCount;
    private List<String> indices = new TreeList<>();
    private Map<String, Map<String, Record>> flyweight;
    private boolean readyToParse;
    private boolean keepQuote;

    public CsvDataType(String textValue) throws TypeConversionException { super(textValue); }

    private CsvDataType() { super(); }

    @Override
    public String getName() { return "CSV"; }

    @Override
    public String toString() { return getName() + "(" + lineSeparator() + getTextValue() + lineSeparator() + ")"; }

    public List<String> getIndices() { return indices; }

    public void setIndices(List<String> indices) { this.indices = indices; }

    public boolean isIndexed(String column) { return MapUtils.isNotEmpty(flyweight) && flyweight.containsKey(column); }

    public void addIndices(String... newIndices) {
        if (ArrayUtils.isEmpty(newIndices)) { return; }
        indices.addAll(Arrays.asList(newIndices));
    }

    public int getColumnCount() { return columnCount; }

    public int getRowCount() { return rowCount; }

    public String getDelim() { return delim; }

    public void setDelim(String delim) { this.delim = delim; }

    public boolean isHeader() { return header; }

    public void setHeader(boolean header) { this.header = header; }

    public boolean hasHeader(String header) { return CollectionUtils.isEmpty(headers) || headers.contains(header); }

    public int getHeaderPosition(String header) {
        return CollectionUtils.isEmpty(headers) ? -1 : headers.indexOf(header);
    }

    public String getQuote() { return quote; }

    public void setQuote(String quote) { this.quote = quote; }

    public String getRecordDelim() { return recordDelim; }

    public void setRecordDelim(String recordDelim) { this.recordDelim = recordDelim; }

    public boolean isTrimValue() { return trimValue; }

    public void setTrimValue(boolean trimValue) { this.trimValue = trimValue; }

    public int getMaxColumns() { return maxColumns; }

    public void setMaxColumns(int maxColumns) { this.maxColumns = maxColumns; }

    public int getMaxColumnWidth() { return maxColumnWidth;}

    public void setMaxColumnWidth(int maxColumnWidth) { this.maxColumnWidth = maxColumnWidth;}

    public void setReadyToParse(boolean readyToParse) { this.readyToParse = readyToParse; }

    public void sortAscending(String column) { sort(column, true); }

    public void sortDescending(String column) { sort(column, false); }

    public Record retrieveFromCache(String column, String cacheKey) {
        if (StringUtils.isBlank(column)) { return null; }
        if (StringUtils.isEmpty(cacheKey)) { return null; }
        if (CollectionUtils.isEmpty(headers)) { return null; }
        if (MapUtils.isEmpty(flyweight)) { return null; }

        Map<String, Record> cache = flyweight.get(column);
        return MapUtils.isNotEmpty(cache) ? cache.get(cacheKey) : null;
    }

    public Record remove(String column, String value) {
        Record matched = retrieveFromCache(column, value);
        if (matched == null) { return null; }

        ConsoleUtils.log("removing matched record");
        if (this.value.remove(matched)) { this.rowCount--; }

        ConsoleUtils.log("updating textValue due to record removal");
        resetTextValue();

        return matched;
    }

    public void removeRows(int... rowIndices) {
        Arrays.sort(rowIndices);
        ArrayUtils.reverse(rowIndices);
        List<String> rows = TextUtils.toList(textValue, recordDelim, false);
        Arrays.stream(rowIndices).forEach(index -> {
            if (rows.size() > index) { rows.remove(isHeader() ? index + 1 : index); }
        });

        textValue = TextUtils.toString(rows, recordDelim);
        parse();
    }

    public void reset(List<Record> records) {
        this.value = records;
        this.rowCount = CollectionUtils.size(this.value);
        if (!header) { columnCount = rowCount == 0 ? 0 : ArrayUtils.getLength(value.get(0).getValues()); }
        resetTextValue();
    }

    public void setKeepQuote(boolean keepQuote) { this.keepQuote = keepQuote; }

    @NotNull
    @Override
    CsvTransformer<CsvDataType> getTransformer() { return transformer; }

    @NotNull
    @Override
    CsvDataType snapshot() {
        CsvDataType snapshot = new CsvDataType();
        snapshot.transformer = transformer;
        snapshot.delim = delim;
        snapshot.quote = quote;
        snapshot.keepQuote = keepQuote;
        snapshot.recordDelim = recordDelim;
        snapshot.header = header;
        snapshot.maxColumns = maxColumns;
        snapshot.maxColumnWidth = maxColumnWidth;
        snapshot.parser = parser;
        if (CollectionUtils.isNotEmpty(headers)) { snapshot.headers = new ArrayList<>(headers); }
        snapshot.rowCount = rowCount;
        snapshot.columnCount = columnCount;
        snapshot.indices = indices;
        snapshot.flyweight = flyweight;
        snapshot.readyToParse = readyToParse;
        snapshot.textValue = textValue;
        snapshot.value = value;
        return snapshot;
    }

    protected List<String> getHeaders() { return headers; }

    @Override
    protected void init() { parse(); }

    protected void sort(String column, boolean ascending) {
        if (!headers.contains(column)) {
            ConsoleUtils.error("Invalid column " + column + "; sorting not performed");
            return;
        }

        if (CollectionUtils.isEmpty(value)) {
            ConsoleUtils.log("No data to sort");
            return;
        }

        value.sort((o1, o2) -> ascending ? compare(o1, o2, column) : compare(o2, o1, column));
        resetTextValue();
    }

    protected int compare(Record first, Record second, String columnName) {
        String value1 = first == null ? "" : StringUtils.defaultString(first.getString(columnName));
        String value2 = second == null ? "" : StringUtils.defaultString(second.getString(columnName));

        return Array.compare(value1, value2);
    }

    protected void resetTextValue() {
        StringBuilder output = new StringBuilder();

        if (CollectionUtils.isNotEmpty(headers)) {
            output.append(TextUtils.toString(headers, delim)).append(recordDelim);
        }

        for (Record oneRow : value) { output.append(TextUtils.toCsvLine(oneRow.getValues(), delim, recordDelim)); }
        textValue = StringUtils.removeEnd(output.toString(), recordDelim);
    }

    protected String surround(String surroundWith, Set<Integer> onColumns) {
        StringBuilder output = new StringBuilder();
        if (CollectionUtils.isNotEmpty(headers)) {
            output.append(TextUtils.toString(headers, delim)).append(recordDelim);
        }

        for (Record row : value) {
            StringBuilder oneRow = new StringBuilder();
            String[] columns = row.getValues();
            for (int i = 0; i < columns.length; i++) {
                String data = columns[i];
                if (onColumns.contains(i)) {
                    oneRow.append(TextUtils.wrapIfMissing(data, surroundWith, surroundWith)).append(delim);
                } else {
                    oneRow.append(data).append(delim);
                }
            }
            output.append(StringUtils.removeEnd(oneRow.toString(), delim)).append(recordDelim);
        }

        return StringUtils.removeEnd(output.toString(), recordDelim);
    }

    protected void configAndParse(String... configs) {
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

            this.delim = configMap.containsKey("delim") ? configMap.get("delim") : context.getTextDelim();
            if (configMap.containsKey("header")) { this.header = toBoolean(configMap.get("header")); }
            if (configMap.containsKey("quote")) { this.quote = configMap.get("quote"); }
            if (configMap.containsKey("keepQuote")) { this.keepQuote = toBoolean(configMap.get("keepQuote")); }
            if (configMap.containsKey("recordDelim")) {
                this.recordDelim = fixControlChars(configMap.get("recordDelim"));
            }
            if (configMap.containsKey("indexOn")) {
                addIndices(Array.toArray(fixControlChars(configMap.get("indexOn")), "\\,"));
            }

            // 512 is the default
            int maxColumns = context.getIntData(CSV_MAX_COLUMNS, getDefaultInt(CSV_MAX_COLUMNS));
            this.maxColumns = configMap.containsKey("maxColumns") ?
                              NumberUtils.toInt(configMap.get("maxColumns"), maxColumns) : maxColumns;

            // 4096 is the default width
            int maxColumnWidth = context.getIntData(CSV_MAX_COLUMN_WIDTH, getDefaultInt(CSV_MAX_COLUMN_WIDTH));
            this.maxColumnWidth = configMap.containsKey("maxColumnWidth") ?
                                  NumberUtils.toInt(configMap.get("maxColumnWidth"), maxColumnWidth) : maxColumnWidth;

            if (configMap.containsKey("trim")) { this.trimValue = toBoolean(configMap.get("trim")); }
        }

        this.readyToParse = true;
        parse();
    }

    protected void parse() {
        if (!readyToParse) { return; }

        if (StringUtils.isBlank(textValue)) {
            ConsoleUtils.log("Unable to generate CSV content from empty/blank text...");
            headers = null;
            value = null;
        }

        ExecutionContext context = ExecutionThread.get();
        if (context != null) {
            if (maxColumns == 0) { maxColumns = context.getIntData(CSV_MAX_COLUMNS, getDefaultInt(CSV_MAX_COLUMNS)); }
            if (maxColumnWidth == 0) {
                maxColumnWidth = context.getIntData(CSV_MAX_COLUMN_WIDTH, getDefaultInt(CSV_MAX_COLUMN_WIDTH));
            }
        } else {
            maxColumns = getDefaultInt(CSV_MAX_COLUMNS);
            maxColumnWidth = getDefaultInt(CSV_MAX_COLUMN_WIDTH);
        }

        // CsvParserSettings settings = CsvCommand.newCsvParserSettings(delim, recordDelim, header, maxColumns);
        // settings.setMaxCharsPerColumn(maxColumnWidth);
        // settings.setKeepQuotes(keepQuote);
        // if (StringUtils.isNotEmpty(quote)) { settings.getFormat().setQuote(quote.charAt(0)); }
        //
        // if (!trimValue) {
        //     settings.setIgnoreLeadingWhitespaces(false);
        //     settings.setIgnoreLeadingWhitespacesInQuotes(false);
        //     settings.setIgnoreTrailingWhitespaces(false);
        //     settings.setIgnoreTrailingWhitespacesInQuotes(false);
        // }
        //
        // parser = new CsvParser(settings);

        parser = new CsvParserBuilder().setDelim(delim)
                                       .setLineSeparator(recordDelim)
                                       .setHasHeader(header)
                                       .setMaxColumns(maxColumns)
                                       .setMaxColumnWidth(maxColumnWidth)
                                       .setQuote(quote)
                                       .setKeepQuote(keepQuote)
                                       .setTrimValue(trimValue)
                                       .build();

        value = parser.parseAllRecords(new StringReader(textValue));
        rowCount = CollectionUtils.size(value);
        if (header) {
            headers = new ArrayList<>(Arrays.asList(parser.getRecordMetadata().headers()));
            columnCount = CollectionUtils.size(headers);
        } else {
            headers = null;
            columnCount = rowCount == 0 ? 0 : ArrayUtils.getLength(value.get(0).getValues());
        }

        CsvFormat detectedFormat = parser.getDetectedFormat();
        if (StringUtils.isEmpty(quote)) { quote = detectedFormat.getQuote() + ""; }
        if (StringUtils.isEmpty(delim)) { delim = detectedFormat.getDelimiter() + ""; }
        if (StringUtils.isEmpty(recordDelim)) { recordDelim = detectedFormat.getLineSeparatorString(); }

        resetTextValue();

        if (CollectionUtils.isNotEmpty(indices) && CollectionUtils.isNotEmpty(headers)) {
            flyweight = new HashMap<>();
            indices.forEach(index -> flyweight.put(index, new HashMap<>()));
            value.forEach(record -> indices.forEach(column ->
                                                        flyweight.get(column).put(record.getString(column), record)));
        }
    }
}
