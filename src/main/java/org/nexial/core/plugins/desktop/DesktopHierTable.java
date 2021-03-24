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
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.utils.CheckUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.JsonUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

import javax.annotation.Nonnull;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.builder.ToStringStyle.NO_CLASS_NAME_STYLE;
import static org.nexial.commons.utils.TextUtils.CleanNumberStrategy.REAL;
import static org.nexial.core.NexialConst.Desktop.AUTOSCAN_INFRAGISTICS4_AWARE;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.desktop.DesktopConst.*;
import static org.nexial.core.plugins.web.WebDriverExceptionHelper.resolveErrorMessage;

public class DesktopHierTable extends DesktopElement {

    private static final String ROW_TYPE_COLUMN = "rowTypeColumn";
    private static final String ROW_TYPE_HIERARCHY = "rowTypeHierarchy";
    private static final String MATCH_COLUMN = "matchColumn";
    private static final String FETCH_COLUMN = "fetchColumn";
    private static final String MATCH_HIERARCHY = "matchHierarchy";
    private static final String ALREADY_COLLAPSED = "alreadyCollapsed";
    private static final DecimalFormat CELL_NUMBER_FORMAT = new DecimalFormat("###,##0.00");

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

        boolean infragistics4 = supportInfragistics4();

        // if header not defined or not yet scanned... scan for existing headers
        if (CollectionUtils.isEmpty(headers)) {
            String headersXpath = resolveHierHeaderRowXpath(infragistics4);
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
            throw new NoSuchElementException("Unable to retrieve first row of Hierarchical Table '" + getLabel() + "'");
        }
    }

    public boolean containsHeader(String header) {
        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }
        return headers != null && headers.contains(header);
    }

    public StepResult collapseAll() {
        boolean infragistics4 = supportInfragistics4();
        if (!infragistics4) {
            driver.executeScript(SCRIPT_TREE_COLLAPSE_ALL, element);
        } else {
            // do it by hand!
            String shortcuts = DesktopUtils.toShortcuts("CTRL-SPACE", "LEFT");
            element.findElements(By.xpath(LOCATOR_HIER_TABLE_ROWS))
                   .forEach(row -> driver.executeScript(shortcuts, row));
        }

        // alreadyCollapsed has no effect if hierarchyColumn and hierarchyList values are provided
        alreadyCollapsed = true;
        return StepResult.success("collapsed all rows in Hierarchical Table");
    }

    protected Map<String, String> getHierRow(List<String> matchBy) {
        if (StringUtils.isBlank(categoryColumn)) { CheckUtils.fail("No categoryColumn found"); }

        if (CollectionUtils.isEmpty(matchBy)) { return null; }
        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }
        if (!containsHeader(categoryColumn)) { return null; }

        boolean infragistics4 = supportInfragistics4();

        if (!infragistics4) {
            JsonObject jsonInput = newJsonInput(matchBy);
            jsonInput.addProperty(ALREADY_COLLAPSED, alreadyCollapsed);

            Object result = driver.executeScript(SCRIPT_TREE_GETROW, element, jsonInput.toString());
            alreadyCollapsed = false;
            return getResultData(result);
        } else {
            WebElement parentRow = expandToMatchedHierarchy(matchBy);
            if (parentRow == null) { return null; }

            // if last match is found, use `nameValues` to construct a series of setValue()'s.
            Map<String, String> outcome = new ListOrderedMap<>();
            parentRow.findElements(By.xpath(LOCATOR_EDITOR))
                     .forEach(cell -> outcome.put(cell.getAttribute("Name"), formatHierCellData(cell)));
            return outcome;
        }
    }

    @Nonnull
    private JsonObject newJsonInput(List<String> matchBy) {
        JsonObject jsonInput = new JsonObject();
        if (StringUtils.isNotBlank(hierarchyColumn)) { jsonInput.addProperty(ROW_TYPE_COLUMN, hierarchyColumn); }
        if (StringUtils.isNotBlank(hierarchyList)) {
            hierarchyList = formatHierarchy(TextUtils.toList(hierarchyList, ",", true));
            jsonInput.addProperty(ROW_TYPE_HIERARCHY, hierarchyList);
        }

        jsonInput.addProperty(MATCH_COLUMN, categoryColumn);
        jsonInput.addProperty(MATCH_HIERARCHY, formatHierarchy(matchBy));
        return jsonInput;
    }

    protected Map<String, String> editHierCell(List<String> matchBy, Map<String, String> nameValues) {
        if (MapUtils.isEmpty(nameValues)) { CheckUtils.fail("No name-value pairs found"); }
        if (StringUtils.isBlank(categoryColumn)) { CheckUtils.fail("No categoryColumn found"); }

        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }

        String invalidColumns = nameValues.keySet().stream()
                                          .filter(name -> !containsHeader(name))
                                          .collect(Collectors.joining(", "));
        if (StringUtils.isNotBlank(invalidColumns)) { CheckUtils.fail("Invalid columns: " + invalidColumns); }

        boolean infragistics4 = supportInfragistics4();
        if (!infragistics4) {
            JsonObject jsonInput = newJsonInput(matchBy);
            jsonInput.addProperty(ALREADY_COLLAPSED, alreadyCollapsed);
            JsonArray edits = new JsonArray();
            nameValues.forEach((key, value) -> {
                JsonObject edit = new JsonObject();
                edit.addProperty("column", key);
                edit.addProperty("value", value);
                edits.add(edit);
            });
            jsonInput.add("edits", edits);

            Object result = driver.executeScript(SCRIPT_TREE_EDIT_CELLS, element, jsonInput.toString());
            alreadyCollapsed = false;
            return getResultData(result);
        } else {
            WebElement parentRow = expandToMatchedHierarchy(matchBy);
            if (parentRow == null) { return null; }

            // if last match is found, use `nameValues` to construct a series of setValue()'s.
            Map<String, String> outcome = new ListOrderedMap<>();
            nameValues.forEach((name, value) -> {
                WebElement cell = findFirstElement(parentRow, StringUtils.replace(XPATH_CELL_INFRAG4, "{name}", name));
                if (cell != null) {
                    try {
                        cell.clear();
                        driver.executeScript(DesktopUtils.toShortcutText(value), cell);
                        outcome.put(name, formatHierCellData(cell));
                    } catch (WebDriverException e) {
                        String message = resolveErrorMessage(e);
                        ConsoleUtils.error("Error when executing shortcut '%s' on '%s': %s", value, name, message);
                        outcome.put(name, "ERROR: " + message);
                    }
                } else {
                    outcome.put(name, null);
                }
            });

            return outcome;
        }
    }

    protected String formatHierCellData(WebElement cell) {
        if (cell == null) { return null; }
        String data = null;
        try {
            data = cell.getText();
        } catch (WebDriverException e) {
            // try @Name instead
        }

        if (data == null) { data = cell.getAttribute("Name"); }

        data = StringUtils.trim(data);

        boolean infragistics4 = supportInfragistics4();

        // number treatment
        return infragistics4 && RegexUtils.match(data, "^\\-?[\\d\\,]+\\.[\\d]{1,}$") ?
               CELL_NUMBER_FORMAT.format(NumberUtils.createDouble(TextUtils.cleanNumber(data, REAL))) :
               data;
    }

    protected WebElement expandToMatchedHierarchy(List<String> matchBy) {
        if (CollectionUtils.isEmpty(matchBy)) { return null; }

        String shortcutExpand = DesktopUtils.toShortcuts("CTRL-SPACE", "RIGHT");
        String xpathMatching = StringUtils.replace(XPATH_ROW_BY_CATEGORY_INFRAG4, "{name}", categoryColumn);
        WebElement parentRow = element;

        for (String matchValue : matchBy) {
            // 1. use `categoryColumn` and `matchBy` to construct XPATH for each level. assume we start from Level 1
            WebElement matched = findFirstElement(parentRow, StringUtils.replace(xpathMatching, "{value}", matchValue));
            if (matched == null) {
                // if no element matched to xpath, then we have not reached the target level/row
                parentRow = null;
                break;
            }

            // 2. if level is found, expand it (unless it's the last one to match)
            driver.executeScript(shortcutExpand, matched);

            // 3. use the matched row to find the next level.
            parentRow = matched;
        }

        alreadyCollapsed = false;

        // 4. if we did not reach the last matching level, then return with nothing
        return parentRow;
    }

    protected List<String> getHierCellChildData(List<String> matchBy, String fetchColumn) {
        if (StringUtils.isBlank(categoryColumn)) { CheckUtils.fail(" No categoryColumn found"); }

        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }
        if (!containsHeader(categoryColumn)) { return null; }

        boolean infragistics4 = supportInfragistics4();

        if (!infragistics4) {
            JsonObject jsonInput = newJsonInput(matchBy);
            jsonInput.addProperty(FETCH_COLUMN, fetchColumn);
            jsonInput.addProperty(ALREADY_COLLAPSED, alreadyCollapsed);

            Object result = driver.executeScript(SCRIPT_TREE_GET_CHILD, element, jsonInput.toString());
            if (result == null) { CheckUtils.fail("Unable to fetch data with matchBy " + matchBy); }

            JSONArray jsonArray = JsonUtils.toJSONArray(result.toString());
            List<String> data = new ArrayList<>();

            if (jsonArray != null  && jsonArray.length() > 0) {
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
                        // ignored...
                    }
                }
            }

            alreadyCollapsed = false;
            return data;
        } else {
            WebElement parentRow = expandToMatchedHierarchy(matchBy);
            if (parentRow == null) { return null; }

            // if last match is found, use `nameValues` to construct a series of setValue()'s.
            return parentRow.findElements(By.xpath(LOCATOR_HIER_TABLE_ROWS + "/*[@Name='" + fetchColumn + "']"))
                            .stream().map(this::formatHierCellData).collect(Collectors.toList());
        }
    }

    // protected List<String> collectData(WebElement row) {
    //     List<String> empty = new ArrayList<>();
    //     if (row == null) {
    //         return empty;
    //     } else {
    //         row.click();
    //         List<WebElement> cells = row.findElements(By.xpath("*"));
    //         return CollectionUtils.isEmpty(cells) ?
    //                empty :
    //                cells.stream().map(cell -> StringUtils.trim(cell.getText())).collect(Collectors.toList());
    //     }
    // }

    protected WebElement getFirstHierRow() {
        boolean infragistics4 = supportInfragistics4();
        List<WebElement> matches = element.findElements(By.xpath(resolveFirstHierRowXpath(infragistics4)));
        return CollectionUtils.isNotEmpty(matches) ? matches.get(0) : null;
    }

    private Map<String, String> getResultData(Object result) {
        if (result == null) { CheckUtils.fail("Unable to fetch data from Hierarchical Table"); }

        JSONArray jsonArray = JsonUtils.toJSONArray(result.toString());
        Map<String, String> data = new ListOrderedMap<>();
        if (jsonArray != null && jsonArray.length() > 0) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    if (jsonArray.get(i) instanceof JSONObject) {
                        JSONObject json = (JSONObject) jsonArray.get(i);
                        if (json.length() == 0) {
                            CheckUtils.fail("Unable to fetch data with specified matchBy criterion..");
                        }
                        if (json.has("name") && json.has("value")) {
                            data.put((String) json.get("name"), String.valueOf(json.get("value")).trim());
                        }
                    }
                } catch (JSONException e) {
                    CheckUtils.fail("Unable to read data from Hierarchical Table");
                }
            }
        }
        return data;
    }

    private String formatHierarchy(List<String> matchBy) { return String.join("/", matchBy); }

    private boolean supportInfragistics4() {
        ExecutionContext context = ExecutionThread.get();
        return context != null &&
               context.getBooleanData(AUTOSCAN_INFRAGISTICS4_AWARE, getDefaultBool(AUTOSCAN_INFRAGISTICS4_AWARE));
    }
}
