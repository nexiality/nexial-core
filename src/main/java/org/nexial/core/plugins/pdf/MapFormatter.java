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

package org.nexial.core.plugins.pdf;

import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.plugins.pdf.CommonKeyValueIdentStrategies.STRATEGY;
import org.nexial.core.plugins.pdf.Table.Cell;
import org.nexial.core.plugins.pdf.Table.Row;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.Data.PDFFORM_UNMATCHED_TEXT;
import static org.nexial.core.plugins.pdf.CommonKeyValueIdentStrategies.STRATEGY.ALTERNATING_CELL;
import static org.nexial.core.plugins.pdf.CommonKeyValueIdentStrategies.STRATEGY.ALTERNATING_ROW;

class MapFormatter extends TableFormatter<Map<String, Object>> {
    private KeyValueIdentStrategy idStrategy;
    private Map<String, Object> existingFormValues;

    public KeyValueIdentStrategy getIdStrategy() { return idStrategy; }

    public void setIdStrategy(KeyValueIdentStrategy idStrategy) {
        this.idStrategy = idStrategy;
    }

    public Map<String, Object> format(Table table) {
        if (table == null || CollectionUtils.isEmpty(table.getRows()) || table.getColumnsCount() < 1) {
            return new LinkedHashMap<>();
        }

        if (idStrategy.isKeyInHeaderRowOnly()) { return formatKeyInHeaderRowOnly(table); }
        if (idStrategy.isKeyValueAlternatingRow()) { return formatKeyValueAlternatingRow(table); }
        if (idStrategy.isKeyValueAlternatingCell()) { return formatKeyValueAlternatingCell(table); }
        if (idStrategy.isKeyValueShareCell()) { return formatKeyValueSharedCell(table); }

        throw new IllegalArgumentException("Invalid key-value identification strategy; unable to format table to fileHeaderMap");
    }

    public MapFormatter setExistingFormValues(Map<String, Object> existingFormValues) {
        this.existingFormValues = existingFormValues;
        return this;
    }

    public boolean shouldSkipKey(String key) {
        String delim = idStrategy.getKeyValueDelimiter();

        // scenario: key doesn't contain delimiter and is configured to be skipped (ignored)
        if (StringUtils.isNotEmpty(delim) && idStrategy.isSkipKeyWithoutDelim()) {
            return !StringUtils.contains(key, delim);
        }

        return false;
    }

    protected Map<String, Object> formatKeyInHeaderRowOnly(Table table) {
        int columnsCount = table.getColumnsCount();
        List<Row> rows = table.getRows();
        int rowCount = rows.size();
        int valueRowCount = rowCount - 1;

        // only first row is key
        List<String> keyList = harvestKeyRow(rows.get(0));

        Map<String, Object> harvestedKeyValues = new LinkedHashMap<>();

        // beyond 1st row, all other rows are value
        for (int i = 0; i < columnsCount; i++) {
            String key = keyList.get(i);
            List<String> values = new ArrayList<>(valueRowCount);
            for (int j = 1; j < rowCount; j++) { values.add(formatCell(rows.get(j), i)); }
            if (StringUtils.isNotEmpty(key) && CollectionUtils.isNotEmpty(values)) {
                harvestedKeyValues.put(key, values);
            }
        }

        if (MapUtils.isEmpty(existingFormValues)) { return harvestedKeyValues; }

        // if (idStrategy.isMultiPageHeaderRowTable() &&
        //     harvestedKeyValues.keySet().size() == existingFormValues.keySet().size()) {
        //
        // 	List<String> harvestedKeys = CollectionUtil.toList(harvestedKeyValues.keySet());
        // 	List<String> existingKeys = CollectionUtil.toList(existingFormValues.keySet());
        // 	for (int i = 0; i < existingKeys.size(); i++) {
        // 		Object existingValues = existingFormValues.get(existingKeys.get(i));
        //
        // 		Collection values;
        // 		if (existingValues instanceof Collection) {
        // 			values = (Collection) existingValues;
        // 		} else {
        // 			values = new ArrayList<String>();
        // 			values.add(existingValues);
        // 		}
        //
        // 		String harvestedKey = harvestedKeys.get(i);
        // 		values.add(harvestedKey);
        // 		values.addAll((List) harvestedKeyValues.get(harvestedKey));
        // 	}
        //
        // 	return existingFormValues;
        // }

        harvestedKeyValues.forEach((key, values) -> {
            if (existingFormValues.containsKey(key)) {
                List<String> harvestedValues = (List<String>) values;

                Object existingValues = existingFormValues.get(key);
                if (existingValues instanceof Collection) {
                    ((Collection) existingValues).addAll(harvestedValues);
                } else if (existingValues instanceof String) {
                    harvestedValues.add((String) existingValues);
                    existingFormValues.put(key, harvestedKeyValues);
                } else {
                    ConsoleUtils.error("Unknown value(s) found with key '" + key + "'");
                }
            } else {
                existingFormValues.put(key, values);
            }
        });

        return existingFormValues;
    }

    protected Map<String, Object> formatKeyValueAlternatingRow(Table table) {
        int columnsCount = table.getColumnsCount();
        List<Row> rows = table.getRows();
        int rowCount = rows.size();

        Map<String, Object> map = new LinkedHashMap<>();

        // one row is key, one row is value, etc.
        for (int i = 0; i < rowCount; i++) {
            List<String> keyList;
            Row valueRow;
            if (idStrategy.isKeyThenValue()) {
                keyList = harvestKeyRow(rows.get(i));
                valueRow = (i + 1) < rowCount ? rows.get(i + 1) : null;
            } else {
                valueRow = rows.get(i);
                keyList = (i + 1) < rowCount ? harvestKeyRow(rows.get(i + 1)) : null;
            }

            i++;

            if (CollectionUtils.isEmpty(keyList)) {
                // no key, no play
                ConsoleUtils.error("Orphaned values found at row " + (i - 1) + "; ignore...");
                continue;
            }

            if (valueRow == null) {
                // no value
                ConsoleUtils.error("Orphaned keys found at row " + (i - 1) + "; corresponding values set to null");
                for (int j = 0; j < columnsCount; j++) {
                    if (CollectionUtils.size(keyList) > j) {
                        if (StringUtils.isNotBlank(keyList.get(j))) { map.put(keyList.get(j), null); }
                    }
                }
                continue;
            }

            // has key and key value - yay!
            for (int j = 0; j < columnsCount; j++) {
                if (CollectionUtils.size(keyList) > j) { addToMap(map, keyList.get(j), formatCell(valueRow, j)); }
            }
        }

        return map;
    }

    protected Map<String, Object> formatKeyValueAlternatingCell(Table table) {
        int columnsCount = table.getColumnsCount();
        List<Row> rows = table.getRows();

        Map<String, Object> map = new LinkedHashMap<>();

        // each row contains key cell, value cell, etc.
        for (Row row : rows) {
            List<Cell> cells = row.getCells();
            for (int j = 0; j < columnsCount; j++) {
                if (CollectionUtils.size(cells) <= j) { continue; }

                String delim = idStrategy.getKeyValueDelimiter();

                String key;
                String value;
                if (idStrategy.isKeyThenValue()) {
                    String cellContent = cells.get(j).getContent();
                    if (shouldSkipKey(cellContent)) { continue; }

                    key = formatKey(cellContent);

                    // if this is last column and key looks like a combination of key+value,
                    // then apply aggressive key/value harvesting
                    if (j + 1 >= columnsCount && StringUtils.isNotEmpty(delim) && StringUtils.contains(key, delim)) {
                        value = formatValue(StringUtils.substringAfter(key, delim));
                        key = formatKey(StringUtils.substringBefore(key, delim));
                    } else {
                        value = formatCell(row, (j + 1));
                    }
                } else {
                    value = formatCell(row, j);
                    if (shouldSkipKey(value)) { continue; }

                    // if this is last column and key looks like a combination of key+value,
                    // then apply aggressive key/value harvesting
                    if (j + 1 >= columnsCount && StringUtils.isNotEmpty(delim) && StringUtils.contains(value, delim)) {
                        key = formatKey(StringUtils.substringAfter(value, delim));
                        value = formatValue(StringUtils.substringBefore(value, delim));
                    } else {
                        key = formatKey(cells.get(j + 1).getContent());
                    }

                }

                j++;

                if (StringUtils.isBlank(key)) {
                    ConsoleUtils.error("Orphaned value found at row " + (j - 1) + "; ignore...");
                } else {
                    // handle dup or repeating key
                    if (map.containsKey(key)) {
                        // there should DEFINITELY be much less than 1000 repeated keys... we should all hope so..
                        for (int i = 1; i < 1000; i++) {
                            String newKey = key + "." + i;
                            if (!map.containsKey(newKey)) {
                                map.put(newKey, value);
                                break;
                            }
                        }
                    } else {
                        map.put(key, value);
                    }
                }
            }
        }

        return map;
    }

    protected Map<String, Object> formatKeyValueSharedCell(Table table) {
        Map<String, Object> map = new LinkedHashMap<>();

        String delim = idStrategy.getKeyValueDelimiter();
        if (StringUtils.isEmpty(delim)) {
            ConsoleUtils.error("Cannot parse PDF Form since no key/value delimiter is specified");
            return map;
        }

        STRATEGY fallbackStrategy = idStrategy.getFallback();
        boolean keyThenValue = idStrategy.isKeyThenValue();

        // each cell contains key and value.
        List<Row> rows = table.getRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            List<Cell> cells = row.getCells();
            if (CollectionUtils.isEmpty(cells)) { continue; }

            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                Cell cell = cells.get(cellIndex);
                String cellContent = cell.getContent();
                boolean delimFound = StringUtils.contains(cellContent, delim);
                String key = formatKey(cellContent);
                String value = formatValue(cellContent);

                if (delimFound && StringUtils.isNotEmpty(key) && StringUtils.isNotEmpty(value)) {
                    // scenario: found key and value within one cell
                    addToMap(map, key, value);
                    continue;
                }

                // from this point, we established that either
                // 1) cellContent doesn't contain delim
                // 2) cellContent doesn't contain key
                // 3) cellContent doesn't contain value

                // if cell doesn't contain delim, then we might need to try something else
                if (fallbackStrategy == null) {
                    skipOrPartialAdd(map, rowIndex, cellIndex, cellContent);
                    continue;
                }

                if (StringUtils.isEmpty(key) && StringUtils.isEmpty(value)) {
                    skipOrPartialAdd(map, rowIndex, cellIndex, cellContent);
                    continue;
                }

                // try with fallback strategy
                String nextCellContent = null;
                if (fallbackStrategy == ALTERNATING_CELL) {
                    if (cellIndex >= cells.size() - 1) {
                        // already last column, so we can't fetch value from next column
                        skipOrPartialAdd(map, rowIndex, cellIndex, cellContent);
                        continue;
                    }

                    nextCellContent = cells.get(cellIndex + 1).getContent();
                }

                if (fallbackStrategy == ALTERNATING_ROW) {
                    if (rowIndex >= rows.size() - 1) {
                        // already last row, so we can't fetch value from next row
                        skipOrPartialAdd(map, rowIndex, cellIndex, cellContent);
                        continue;
                    }

                    nextCellContent = rows.get(rowIndex + 1).getCells().get(cellIndex).getContent();
                }

                // scenario: unsupported fallback strategy
                if (StringUtils.isEmpty(nextCellContent)) {
                    skipOrPartialAdd(map, rowIndex, cellIndex, cellContent);
                    continue;
                }

                // we only want to consider the next cell if it ALSO doesn't contain delim
                if (delimFound) {
                    // scenario: key or value was empty... check if nextCellContent is any good
                    if (StringUtils.contains(nextCellContent, delim)) {
                        // next cell contains delim, so we can't use it (or it might topple all subsequent parsing!)
                        skipOrPartialAdd(map, rowIndex, cellIndex, cellContent);
                    } else {
                        // next cell doesn't have delim... let's match current and next cell as key/value pair
                        if (keyThenValue) {
                            addToMap(map, key, postFormatValue(nextCellContent));
                        } else {
                            addToMap(map, formatKey(nextCellContent), value);
                        }
                    }
                } else {
                    // no delim so far... so the entire cellContent is either key or value
                    if (StringUtils.contains(nextCellContent, delim)) {
                        // scenario: next cell has delim, so we'll only consider using it if it
                        // resolves to a "key and empty value" or "empty key and value"
                        String nextKey = formatKey(nextCellContent);
                        String nextValue = formatValue(nextCellContent);

                        if (keyThenValue) {
                            // expects "empty key then value"
                            if (StringUtils.isNotEmpty(nextKey)) {
                                // nope.. it's off, can't use
                                skipOrPartialAdd(map, rowIndex, cellIndex, cellContent);
                                continue;
                            } else {
                                addToMap(map, key, nextValue);
                            }
                        } else {
                            // expects "key then empty value"
                            if (StringUtils.isNotEmpty(nextValue)) {
                                // nope.. it's off, can't use
                                skipOrPartialAdd(map, rowIndex, cellIndex, cellContent);
                                continue;
                            } else {
                                addToMap(map, nextKey, value);
                            }
                        }
                    }
                }

                if (fallbackStrategy == ALTERNATING_CELL) { cellIndex++; }
                if (fallbackStrategy == ALTERNATING_ROW) { rowIndex++; }
            }
        }

        return map;
    }

    protected void addToMap(Map<String, Object> map, String key, String value) {
        if (StringUtils.isNotBlank(key) && value != null) { map.put(key, value); }
    }

    protected void addToMap(Map<String, Object> map, String key, Collection<String> values) {
        if (StringUtils.isNotBlank(key) && CollectionUtils.isNotEmpty(values)) { map.put(key, values); }
    }

    protected String formatCell(Row row, int cellIndex) {
        if (row == null || CollectionUtils.size(row.getCells()) <= cellIndex) { return null; }
        return formatValue(row.getCells().get(cellIndex).getContent());
    }

    protected String formatValue(String content) {
        if (StringUtils.isNotBlank(content)) {
            String delim = idStrategy.getKeyValueDelimiter();
            if (idStrategy.isKeyValueShareCell() && StringUtils.isNotEmpty(delim)) {
                if (idStrategy.isKeyThenValue()) {
                    // scenario: key then value
                    content = StringUtils.substringAfter(content, delim);
                } else {
                    // scenario: value then key
                    content = StringUtils.substringBefore(content, delim);
                }
            }
        }

        return postFormatValue(content);
    }

    protected String postFormatValue(String content) {
        if (idStrategy.isValueAsOneLine()) {
            content = StringUtils.replace(content, "\r\n", " ");
            content = StringUtils.replace(content, "\r", " ");
            content = StringUtils.replace(content, "\n", " ");
        }
        if (idStrategy.isTrimValue()) { content = StringUtils.trim(content); }
        if (idStrategy.isNormalizeValue()) { content = StringUtils.normalizeSpace(content); }

        return content;
    }

    protected List<String> harvestKeyRow(Row row) {
        if (row == null || CollectionUtils.isEmpty(row.getCells())) { return new ArrayList<>(); }

        List<Cell> keyCells = row.getCells();
        List<String> keys = new ArrayList<>();
        for (Cell cell : keyCells) {
            String cellContent = cell.getContent();
            if (!shouldSkipKey(cellContent)) { keys.add(formatKey(cellContent)); }
        }
        return keys;
    }

    protected String formatKey(String key) {
        String delim = idStrategy.getKeyValueDelimiter();

        if (StringUtils.isNotBlank(key)) {
            // scenario: key and value are in the same cell
            if (idStrategy.isKeyValueShareCell() && StringUtils.isNotEmpty(delim)) {
                if (idStrategy.isKeyThenValue()) {
                    // scenario: key then value
                    key = StringUtils.substringBefore(key, delim);
                } else {
                    // scenario: value then key
                    key = StringUtils.substringAfter(key, delim);
                }
            }

            String regexKey = idStrategy.getExtractKeyPattern();
            if (StringUtils.isNotEmpty(regexKey)) {
                List<String> keyParts = RegexUtils.collectGroups(key, regexKey);
                if (CollectionUtils.size(keyParts) > 0) { key = keyParts.get(0); }
            }
        }

        if (idStrategy.isTrimKey()) { key = StringUtils.trim(key); }
        if (idStrategy.isNormalizeKey()) { key = StringUtils.normalizeSpace(key); }

        return key;
    }

    protected void skipOrPartialAdd(Map<String, Object> map, int rowIndex, int cellIndex, String cellContent) {
        boolean skipKeyWithoutDelim = idStrategy.isSkipKeyWithoutDelim();
        if (skipKeyWithoutDelim) {
            ConsoleUtils.log("Unable to extract PDF Form data (" + rowIndex + "," + cellIndex + "): '" +
                             cellContent + "'");
        } else {
            String matched = idStrategy.isKeyThenValue() ? formatKey(cellContent) : formatValue(cellContent);
            if (StringUtils.isNotBlank(matched)) { resolveUmatchedList(map).add(matched); }
        }
    }

    protected static Collection resolveUmatchedList(Map<String, Object> map) {
        if (!map.containsKey(PDFFORM_UNMATCHED_TEXT)) {
            Collection unmatched = new HashSet();
            map.put(PDFFORM_UNMATCHED_TEXT, unmatched);
            return unmatched;
        } else {
            return (Collection) map.get(PDFFORM_UNMATCHED_TEXT);
        }
    }
}
