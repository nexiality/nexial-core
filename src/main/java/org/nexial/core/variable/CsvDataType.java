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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.list.TreeList;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.plugins.io.CsvCommand;
import org.nexial.core.utils.ConsoleUtils;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;

import static java.lang.System.lineSeparator;

public class CsvDataType extends ExpressionDataType<List<Record>> {
    private CsvTransformer transformer = new CsvTransformer();
    private String delim;
    private String quote;
    private String recordDelim;
    private boolean header = true;
    private CsvParser parser;
    private List<String> headers;
    private int columnCount;
    private int rowCount;
    private List<String> indices = new TreeList<>();
    private Map<String, Map<String, Record>> flyweight;
    private boolean readyToParse;

    public CsvDataType(String textValue) throws TypeConversionException { super(textValue); }

    private CsvDataType() { super(); }

    @Override
    public String getName() { return "CSV"; }

    @Override
    public String toString() { return getName() + "(" + lineSeparator() + getTextValue() + lineSeparator() + ")"; }

    @Override
    Transformer getTransformer() { return transformer; }

    @Override
    CsvDataType snapshot() {
        CsvDataType snapshot = new CsvDataType();
        snapshot.transformer = transformer;
        snapshot.delim = delim;
        snapshot.quote = quote;
        snapshot.recordDelim = recordDelim;
        snapshot.header = header;
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

    @Override
    protected void init() { parse(); }

    public List<String> getIndices() { return indices; }

    public void setIndices(List<String> indices) { this.indices = indices; }

    public boolean isIndexed(String column) { return MapUtils.isNotEmpty(flyweight) && flyweight.containsKey(column); }

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

    public void setReadyToParse(boolean readyToParse) { this.readyToParse = readyToParse; }

    public void reset(List<Record> records) {
        this.value = records;
        this.rowCount = CollectionUtils.size(this.value);
        if (!header) { columnCount = rowCount == 0 ? 0 : ArrayUtils.getLength(value.get(0).getValues()); }
        resetTextValue();
    }

    public void sortAscending(String column) {
        if (!headers.contains(column)) {
            ConsoleUtils.error("Invalid column " + column + "; sorting not performed");
            return;
        }

        if (CollectionUtils.isEmpty(value)) {
            ConsoleUtils.log("No data to sort");
            return;
        }

        value.sort(Comparator.comparing(o -> o == null ? "" : StringUtils.defaultString(o.getString(column))));
        resetTextValue();
    }

    public void sortDescending(String column) {
        if (!headers.contains(column)) {
            ConsoleUtils.error("Invalid column " + column + "; sorting not performed");
            return;
        }
        value.sort((o1, o2) -> o2.getString(column).compareTo(o1.getString(column)));
        resetTextValue();
    }

    protected void resetTextValue() {
        StringBuilder output = new StringBuilder();

        if (CollectionUtils.isNotEmpty(headers)) {
            output.append(TextUtils.toString(headers, delim)).append(recordDelim);
        }

        for (Record oneRow : value) { output.append(TextUtils.toCsvLine(oneRow.getValues(), delim, recordDelim)); }
        textValue = StringUtils.removeEnd(output.toString(), recordDelim);
    }

    protected List<String> getHeaders() { return headers; }

    protected void parse() {
        if (!readyToParse) { return; }

        if (StringUtils.isBlank(textValue)) {
            // csvFormat = null;
            headers = null;
            value = null;
        }

        parser = CsvCommand.newCsvParser(quote, delim, recordDelim, header);
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
