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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.JsonUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.rightPad;
import static org.json.JSONObject.NULL;
import static org.nexial.core.NexialConst.Desktop.AUTOSCAN_INFRAGISTICS4_AWARE;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.desktop.DesktopConst.INFRAG4_ITEM_STATUS_POSTFIX;
import static org.nexial.core.plugins.desktop.DesktopConst.INFRAG4_ITEM_STATUS_PREFIX;
import static org.nexial.core.plugins.desktop.DesktopUtils.getElementText;
import static org.nexial.core.plugins.desktop.ElementType.*;

public class TableData {
    private static final String REGEX_LOWER_UPPER = "[0-9a-z]\\s[A-Z]";
    private static final int TO_STRING_KEY_LENGTH = 12;

    private int rowCount;
    private int columnCount;
    private List<String> columns;
    private long elapsedTime;
    private List<TableRow> data;

    public class TableRow extends ListOrderedMap<String, String> {
        private final Map<String, String> dataMap = new ListOrderedMap<>();

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

    private TableData() {}

    public TableData(Object tableDataObject, Duration duration) {
        elapsedTime = duration.toMillis() / 1000;
        data        = readTableData(tableDataObject);
        rowCount    = data.size();
        columnCount = columns.size();
    }

    public static TableData fromTreeViewRows(List<String> headers, List<WebElement> rowData, Duration duration) {
        TableData tableData = new TableData();
        tableData.elapsedTime = duration.toMillis() / 1000;
        tableData.data        = tableData.readTreeViewData(headers, rowData);
        tableData.columns     = headers;
        tableData.columnCount = CollectionUtils.size(headers);
        tableData.rowCount    = tableData.data == null ? 0 : tableData.data.size();
        return tableData;
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
                rowData      = new ArrayList<>();
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

    private List<TableRow> readTreeViewData(List<String> headers, List<WebElement> rowData) {
        if (CollectionUtils.isEmpty(headers) || CollectionUtils.isEmpty(rowData)) { return null; }

        List<TableRow> rows = new ArrayList<>();

        int headerIndex = 0;
        TableRow columnValues = new TableRow();
        for (WebElement rowDatum : rowData) {
            String header = headers.get(headerIndex++);
            columnValues.put(Integer.toString(headerIndex), header, resolveCellValue(rowDatum));
            if (headerIndex >= headers.size()) {
                headerIndex = 0;
                rows.add(columnValues);
                columnValues = new TableRow();
            }
        }

        if (!columnValues.isEmpty()) { rows.add(columnValues); }
        return rows;
    }

    private String resolveCellValue(WebElement cell) {
        if (cell == null) { return ""; }

        String controlType = cell.getAttribute("ControlType");

        // todo: need to support tri-state checkboxes
        if (StringUtils.equals(controlType, CHECK_BOX) || StringUtils.equals(controlType, RADIO)) {
            return cell.isSelected() ? "True" : getElementText(cell, "False");
        }

        String cellText = getElementText(cell, "");

        if (StringUtils.equals(controlType, COMBO) || StringUtils.equals(controlType, LIST)) {
            // anything selected?
            if (StringUtils.isEmpty(cellText)) { return cellText; }

            cellText = deriveInfragistic4CellText(cell);
            if (StringUtils.isNotEmpty(cellText)) { return cellText; }

            List<WebElement> children = cell.findElements(By.xpath("*[@ControlType='ControlType.ListItem']"));
            if (CollectionUtils.isEmpty(children)) { return cellText; }

            String xpathSelected = "*[@ControlType='ControlType.ListItem' and @IsSelected='True']";
            List<WebElement> selectedElements = cell.findElements(By.xpath(xpathSelected));

            // if none selected or no list item found
            if (CollectionUtils.isEmpty(selectedElements)) { return cellText; }
            return StringUtils.trim(StringUtils.defaultString(selectedElements.get(0).getAttribute("Name")));
        }

        if (StringUtils.equals(controlType, BUTTON)) { return cell.getAttribute("Name"); }

        return cellText;
    }

    private String deriveInfragistic4CellText(WebElement cell) {
        ExecutionContext context = ExecutionThread.get();
        if (context == null) { return null; }
        boolean supportInfragistics4 =
                context.getBooleanData(AUTOSCAN_INFRAGISTICS4_AWARE, getDefaultBool(AUTOSCAN_INFRAGISTICS4_AWARE));
        if (!supportInfragistics4) { return null; }

        String itemStatus = cell.getAttribute("ItemStatus");
        if (StringUtils.isBlank(itemStatus)) { return null; }

        return StringUtils.substringBetween(itemStatus, INFRAG4_ITEM_STATUS_PREFIX, INFRAG4_ITEM_STATUS_POSTFIX);
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