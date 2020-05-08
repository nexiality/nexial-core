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

package org.nexial.core.plugins.desktop;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.JsonUtils;

import static org.apache.commons.lang3.StringUtils.rightPad;
import static org.json.JSONObject.NULL;
import static org.nexial.core.NexialConst.NL;

public class TableData {
    private static final String REGEX_LOWER_UPPER = "[0-9a-z]\\s[A-Z]";
    private static final int TO_STRING_KEY_LENGTH = 12;

    private int rowCount;
    private int columnCount;
    private List<String> columns;
    private long elapsedTime;
    private List<TableRow> data;

    public class TableRow extends ListOrderedMap<String, String> {
        private Map<String, String> dataMap = new ListOrderedMap<>();

        public String get(Object key) {
            if (!(dataMap.containsKey(key)) && dataMap.containsKey("error")) { return dataMap.get("error"); }
            if (!(dataMap.containsKey(key))) {
                key = applyPattern(key.toString());
                if (dataMap.containsKey(key)) { return dataMap.get(key); }
            }
            return dataMap.get(key);
        }

        public void put(String index, String key, String value) {
            dataMap.put(index, value);
            dataMap.put(key, value);
            this.put(key, value);
        }
    }

    public TableData(Object tableDataObject, Duration duration) {
        elapsedTime = duration.toMillis() / 1000;
        data = readTableData(tableDataObject);
        rowCount = data.size();
        columnCount = columns.size();
    }

    public long getElapsedTime() { return elapsedTime; }

    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime; }

    public List<String> getColumns() { return columns; }

    public int getRowCount() { return rowCount; }

    public List<TableRow> getData() { return data; }

    public int getColumnCount() { return columnCount; }

    @Override
    public String toString() { return rightPad("data", TO_STRING_KEY_LENGTH) + "=" + data; }

    protected List<List<String>> getRows() {
        List<List<String>> rows = new ArrayList<>();
        data.forEach(map -> rows.add(map.valueList()));
        return rows;
    }

    protected String getCell(String column) {
        if (data.isEmpty()) { return null; }
        return data.get(0).get(column);
    }

    protected List<String> getRow(int row) {
        if (!isValidRow(row)) { return null; }
        return data.get(row).valueList();
    }

    private boolean isValidRow(int row) {
        String errPrefix = "Invalid row number specified: " + row + ". ";

        if (row < 0) {
            ConsoleUtils.log(errPrefix + "MUST START FROM 0 (0 is FIRST ROW).");
            return false;
        }

        if (CollectionUtils.isEmpty(data)) {
            ConsoleUtils.log(errPrefix + "NO DATA YET COLLECTED FROM CORRESPONDING TABLE");
            return false;
        }

        return true;
    }

    private List<TableRow> readTableData(Object tableDataObject) {
        if (tableDataObject == null) { return null; }

        JSONObject jsonObject = JsonUtils.toJSONObject(tableDataObject.toString());

        try {
            if (jsonObject.has("error")) { CheckUtils.fail(jsonObject.getString("error")); }
        } catch (JSONException e) {
            throw new IllegalStateException("ERROR: Unable to retrieve data from Table." + NL +
                                            "Exception: " + e.getMessage());
        }

        List<TableRow> rows = new ArrayList<>();
        try {
            JSONArray columns = jsonObject.getJSONArray("columns");
            List<String> columnsList = new ArrayList<>();
            JsonUtils.toList(columns).forEach(column -> columnsList.add(column.toString()));

            List<String> stringList = new ArrayList<>();
            stringList.addAll(columnsList);
            this.columns = stringList;

            TableRow columnValues;
            List<Object> rowData;
            Collection rowCollection;

            JSONArray data = jsonObject.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                rowData = new ArrayList<>();
                columnValues = new TableRow();
                if (data.get(i) instanceof JSONArray) {
                    rowCollection = JsonUtils.toList(data.getJSONArray(i));
                    rowData.addAll(rowCollection);
                } else if (data.get(i) instanceof JSONObject && ((JSONObject) data.get(i)).has("error")) {
                    columnValues.put(Integer.toString(i), "error", ((JSONObject) data.get(i)).getString("error"));
                }

                for (int j = 0; j < rowData.size(); j++) {
                    if (rowData.get(j) == null || rowData.get(j) == NULL) {
                        columnValues.put(Integer.toString(j), columns.getString(j), "");
                    } else if (rowData.get(j) instanceof Boolean) {
                        columnValues.put(Integer.toString(j),
                                         columns.getString(j),
                                         (Boolean.toString((boolean) rowData.get(j))));
                    } else {
                        columnValues.put(Integer.toString(j), columns.getString(j), rowData.get(j).toString());
                    }
                }
                rows.add(columnValues);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("ERROR: Unable to read Table data: " + e.getMessage());
        }

        return rows;
    }

    private String applyPattern(String column) {
        Pattern pattern = Pattern.compile(REGEX_LOWER_UPPER);
        Matcher matcher = pattern.matcher(column);
        while (matcher.find()) {
            String matchedString = matcher.group();
            column = column.replace(matchedString, matchedString.replace(" ", ""));
        }
        return column;
    }
}