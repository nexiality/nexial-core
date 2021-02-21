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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.JsonUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.builder.ToStringStyle.NO_CLASS_NAME_STYLE;
import static org.nexial.core.plugins.desktop.DesktopConst.*;

public class DesktopHierTable extends DesktopElement {

    private static final String ROW_TYPE_COLUMN = "rowTypeColumn";
    private static final String ROW_TYPE_HIERARCHY = "rowTypeHierarchy";
    private static final String MATCH_COLUMN = "matchColumn";
    private static final String FETCH_COLUMN = "fetchColumn";
    private static final String MATCH_HIERARCHY = "matchHierarchy";
    private static final String ALREADY_COLLAPSED = "alreadyCollapsed";

    protected List<String> headers;
    protected int columnCount = UNDEFINED;
    protected String hierarchyColumn;
    protected String hierarchyList;
    protected String categoryColumn;
    private boolean alreadyCollapsed = true;

    public static class TreeMetaData {
        private List<String> headers;
        private int columnCount;

        public List<String> getHeaders() { return headers; }

        public void setHeaders(List<String> headers) { this.headers = headers; }

        public int getColumnCount() { return columnCount; }

        public void setColumnCount(int columnCount) { this.columnCount = columnCount; }

        @Override
        public String toString() {
            return new ToStringBuilder(this, NO_CLASS_NAME_STYLE)
                       .append("headers", headers)
                       .append("columnCount", columnCount)
                       .toString();
        }
    }

    protected DesktopHierTable() { }

    public static DesktopHierTable toInstance(DesktopElement component) {
        DesktopHierTable instance = new DesktopHierTable();
        copyTo(component, instance);
        if (MapUtils.isNotEmpty(instance.extra)) {
            if (instance.extra.containsKey("headers")) {
                instance.headers = TextUtils.toList(instance.extra.get("headers"), ",", true);
                instance.columnCount = CollectionUtils.size(instance.headers);
            }
            if (instance.extra.containsKey("hierarchyColumn")) {
                instance.hierarchyColumn = instance.extra.get("hierarchyColumn");
            }
            if (instance.extra.containsKey("hierarchyList")) {
                instance.hierarchyList = instance.extra.get("hierarchyList");
            }
            if (instance.extra.containsKey("categoryColumn")) {
                instance.categoryColumn = instance.extra.get("categoryColumn");
            }
        }
        return instance;
    }

    public TreeMetaData toMetaData() {
        TreeMetaData metaData = new TreeMetaData();
        if (CollectionUtils.isNotEmpty(headers)) {
            metaData.setHeaders(new ArrayList<>(headers));
            metaData.setColumnCount(columnCount);
        }

        return metaData;
    }

    public List<String> getHeaders() { return headers; }

    public int getColumnCount() { return columnCount; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, NO_CLASS_NAME_STYLE)
                   .append("columnCount", columnCount)
                   .append("headers", headers)
                   .toString();
    }

    public void scanStructure() {

        // if header not defined or not yet scanned... scan for existing headers
        if (CollectionUtils.isEmpty(headers)) {
            String headersXpath = XPATH_FIRST_HIER_ROW + "/*";
            ConsoleUtils.log("scanning for hierarchical table structure via " + headersXpath);
            List<WebElement> elements = element.findElements(By.xpath(headersXpath));
            if (CollectionUtils.isEmpty(elements)) {
                columnCount = 0;
                return;
                // todo: should we throw up here?
            }

            headers = new ArrayList<>();
            elements.forEach(elem -> headers.add(TextUtils.xpathNormalize(elem.getAttribute(ATTR_NAME))));
            columnCount = headers.size();
        }

        // find first row to make sure we have something in this hier table
        WebElement firstRow = getFirstHierRow();
        if (firstRow == null) {
            throw new NoSuchElementException("Unable to retrieve first row of HierTable '" + getLabel() + "'");
        }
    }

    public boolean containsHeader(String header) {
        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }
        return headers != null && headers.contains(header);
    }

    public Map<String, String> editHierCell(List<String> matchBy, Map<String, String> nameValues) {
        if (MapUtils.isEmpty(nameValues)) { CheckUtils.fail("No name-value pairs found"); }
        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }
        for (String name : nameValues.keySet()) {
            if (!containsHeader(name)) { CheckUtils.fail("Invalid column name:" + name);}
        }

        JsonObject jsonInput = new JsonObject();
        if (hierarchyColumn != null && StringUtils.isNotBlank(hierarchyColumn)) {
            jsonInput.addProperty(ROW_TYPE_COLUMN, hierarchyColumn);
        }
        if (hierarchyList != null && StringUtils.isNotBlank(hierarchyList)) {
            hierarchyList = formatHierarchy(TextUtils.toList(hierarchyList, ",", true));
            jsonInput.addProperty(ROW_TYPE_HIERARCHY, hierarchyList);
        }
        if (categoryColumn == null || StringUtils.isBlank(categoryColumn)) {
            CheckUtils.fail(" No categoryColumn found");
        }

        jsonInput.addProperty(MATCH_COLUMN, categoryColumn);
        jsonInput.addProperty(MATCH_HIERARCHY, formatHierarchy(matchBy));
        jsonInput.addProperty(ALREADY_COLLAPSED, alreadyCollapsed);

        JsonArray edits = new JsonArray();

        nameValues.forEach((key, value) -> {
            JsonObject edit = new JsonObject();
            edit.addProperty("column", key);
            edit.addProperty("value", value);
            edits.add(edit);
        });
        jsonInput.add("edits", edits);
        Object result = driver.executeScript("editCells", element, jsonInput.toString());
        alreadyCollapsed = false;
        return getResultData(result);
    }

    public StepResult collapseAll() {
        driver.executeScript("collapseAll", element);
        // alreadyCollapsed has no effect if hierarchyColumn and hierarchyList values are provided
        alreadyCollapsed = true;
        return StepResult.success("collapsed all hierTable rows");

    }

    protected Map<String, String> getHierRow(List<String> matchBy) {
        if (categoryColumn == null || StringUtils.isBlank(categoryColumn)) {
            CheckUtils.fail(" No categoryColumn found");
        }
        if (CollectionUtils.isEmpty(matchBy)) { return null; }
        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }
        if (!containsHeader(categoryColumn)) { return null; }

        JsonObject jsonInput = new JsonObject();

        if (hierarchyColumn != null && StringUtils.isNotBlank(hierarchyColumn)) {
            jsonInput.addProperty(ROW_TYPE_COLUMN, hierarchyColumn);
        }
        if (hierarchyList != null && StringUtils.isNotBlank(hierarchyList)) {
            hierarchyList = formatHierarchy(TextUtils.toList(hierarchyList, ",", true));
            jsonInput.addProperty(ROW_TYPE_HIERARCHY, hierarchyList);
        }

        jsonInput.addProperty(MATCH_COLUMN, categoryColumn);
        jsonInput.addProperty(MATCH_HIERARCHY, formatHierarchy(matchBy));
        jsonInput.addProperty(ALREADY_COLLAPSED, alreadyCollapsed);
        Object result = driver.executeScript("getRowData", element, jsonInput.toString());
        alreadyCollapsed = false;
        return getResultData(result);
    }

    protected List<String> getHierCellChildData(List<String> matchBy, String fetchColumn) {
        if (categoryColumn == null || StringUtils.isBlank(categoryColumn)) {
            CheckUtils.fail(" No categoryColumn found");
        }
        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }
        if (!containsHeader(categoryColumn)) { return null; }

        JsonObject jsonInput = new JsonObject();
        if (hierarchyColumn != null && StringUtils.isNotBlank(hierarchyColumn)) {
            jsonInput.addProperty(ROW_TYPE_COLUMN, hierarchyColumn);
        }
        if (hierarchyList != null && StringUtils.isNotBlank(hierarchyList)) {
            hierarchyList = formatHierarchy(TextUtils.toList(hierarchyList, ",", true));
            jsonInput.addProperty(ROW_TYPE_HIERARCHY, hierarchyList);
        }

        jsonInput.addProperty(MATCH_COLUMN, categoryColumn);
        jsonInput.addProperty(MATCH_HIERARCHY, formatHierarchy(matchBy));
        jsonInput.addProperty(FETCH_COLUMN, fetchColumn);
        jsonInput.addProperty(ALREADY_COLLAPSED, alreadyCollapsed);
        Object result = driver.executeScript("getChildData", element, jsonInput.toString());
        if (result == null) { CheckUtils.fail("Unable to fetch data with matchBy " + matchBy); }
        JSONArray jsonArray = JsonUtils.toJSONArray(result.toString());
        List<String> data = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {

            try {
                if (jsonArray.get(i) instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject) jsonArray.get(i);
                    if (jsonObject.length() == 0) {
                        CheckUtils.fail("Unable to fetch data with specified matchBy criterion..");
                    }
                    if (jsonObject.has("name") && jsonObject.getString("name").equals(fetchColumn)) {
                        data.add(String.valueOf(jsonObject.get("value")).trim());
                    }
                }
            } catch (JSONException e) {
            }
        }
        alreadyCollapsed = false;
        return data;
    }

    protected List<String> collectData(WebElement row) {
        List<String> data = new ArrayList<>();
        if (row == null) { return data; }

        row.click();
        List<WebElement> cells = row.findElements(By.xpath("*"));
        if (CollectionUtils.isEmpty(cells)) { return data; }

        cells.forEach(cell -> data.add(StringUtils.trim(cell.getText())));
        return data;
    }

    protected WebElement getFirstHierRow() {
        List<WebElement> matches = element.findElements(By.xpath(XPATH_FIRST_HIER_ROW));
        if (CollectionUtils.isNotEmpty(matches)) { return matches.get(0);}
        return null;
    }

    private Map<String, String> getResultData(Object result) {

        if (result == null) {
            CheckUtils.fail("Unable to fetch data from hiertable");
        }

        JSONArray jsonArray = JsonUtils.toJSONArray(result.toString());
        Map<String, String> data = new ListOrderedMap<>();

        for (int i = 0; i < jsonArray.length(); i++) {

            try {
                if (jsonArray.get(i) instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject) jsonArray.get(i);
                    if (jsonObject.length() == 0) {
                        CheckUtils.fail("Unable to fetch data with specified matchBy criterion..");
                    }
                    if (jsonObject.has("name") && jsonObject.has("value")) {
                        data.put((String) jsonObject.get("name"), String.valueOf(jsonObject.get("value")).trim());
                    }
                }
            } catch (JSONException e) {
                CheckUtils.fail("Unable to read data from hiertable");
            }
        }
        return data;
    }

    private String formatHierarchy(List<String> matchBy) {

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < matchBy.size(); i++) {
            builder.append(matchBy.get(i) + "/");
        }
        return StringUtils.removeEnd(builder.toString(), "/");

    }


}
