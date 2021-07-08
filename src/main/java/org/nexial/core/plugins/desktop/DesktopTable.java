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
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.NexialConst.PolyMatcher;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.desktop.TableData.TableRow;
import org.nexial.core.plugins.desktop.ig.IgExplorerBar;
import org.nexial.core.utils.ConsoleUtils;
import org.nexial.core.utils.JsonUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.plugins.desktop.DesktopConst.*;
import static org.nexial.core.plugins.desktop.DesktopUtils.*;
import static org.nexial.core.plugins.desktop.ElementType.*;
import static org.nexial.core.utils.CheckUtils.requiresNotNull;
import static org.openqa.selenium.Keys.ESCAPE;

/**
 * capture both behavior and data of a {@link ElementType#Table} element (the corresponding @ControlType is
 * {@link ElementType#TABLE}).
 * <p>
 * Instance of this element may be stored in session ({@link DesktopSession}) so that its content and state can be
 * retained for the span of an execution cycle.
 * <p>
 * As of v3.7, we will support HierTable (aka ControlType.Tree) as well
 */
public class DesktopTable extends DesktopElement {
    protected List<String> headers;
    protected Map<String, String> cellTypes;
    protected int columnCount = UNDEFINED;
    protected int headerHeight = TABLE_HEADER_HEIGHT;
    protected int clickOffsetX = TABLE_ROW_X_OFFSET;
    // in case some table column headers contains multiple/repeating spaces, setting this property to true means Nexial
    // would normalize the repeating spaces in column header during XPATH generation
    protected boolean normalizeSpace;
    protected boolean isTreeView;

    protected DesktopTable() { }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) as well */
    public static DesktopTable toInstance(DesktopElement component) {
        DesktopTable instance = new DesktopTable();
        copyTo(component, instance);
        if (component.getElementType() == HierTable) { instance.isTreeView = true; }

        if (MapUtils.isNotEmpty(instance.extra)) {
            // needed for multi-line headers
            if (instance.extra.containsKey("normalizeSpace")) {
                instance.normalizeSpace = BooleanUtils.toBoolean(instance.extra.get("normalizeSpace"));
            }

            // header list specified manually to avoid time needed to discover this dynamically
            if (instance.extra.containsKey("headers")) {
                instance.headers =
                    TextUtils.toList(instance.extra.get("headers"), ",", true).stream()
                             .map(header -> instance.normalizeSpace ? TextUtils.xpathNormalize(header) : header)
                             .collect(Collectors.toList());
                instance.columnCount = CollectionUtils.size(instance.headers);
            }

            if (instance.extra.containsKey("headerHeight")) {
                instance.headerHeight = NumberUtils.toInt(instance.extra.get("headerHeight"));
            }

            if (instance.extra.containsKey("clickOffsetX")) {
                instance.clickOffsetX = NumberUtils.toInt(instance.extra.get("clickOffsetX"));
            }

            // specify the cells that require different treatment (ie FormattedTextbox)
            if (instance.extra.containsKey("cellTypes")) {
                instance.cellTypes = TextUtils.toMap(StringUtils.trim(instance.extra.get("cellTypes")), ",", "=");
            }
        }
        return instance;
    }

    public List<String> getHeaders() { return headers; }

    public int getColumnCount() { return columnCount; }

    @Override
    public String toString() {
        return "columnCount='" + columnCount + "', \n" +
               "headers='" + headers + "', \n" +
               "cellTypes='" + cellTypes + "', \n" +
               "normalizeSpace='" + normalizeSpace + "'";
    }

    /**
     * scan table structure, optionally forcing the rescanning of row information so that we get the latest row count.
     * Consequently  any previously harvested rows will be cleared out as well.
     */
    public TableMetaData scanStructure() {
        sanityCheck();

        // short-circuit: if header information is provided via extra, then we don't need to do the scanning again
        if (CollectionUtils.isEmpty(headers) || CollectionUtils.size(headers) != columnCount) {
            // collect headers
            String xpath;
            if (isTreeView) {
                // first try with a data row since we'd get more accurate headers (correct order)
                xpath = "*[@ControlType='ControlType.DataItem'][1]/*";
                ConsoleUtils.log("scanning for table structure via " + xpath);
                List<WebElement> columns = element.findElements(By.xpath(xpath));
                if (CollectionUtils.isEmpty(columns)) {
                    ConsoleUtils.log("No data found; re-scanning for table structure via " + xpath);
                    xpath = LOCATOR_HIER_HEADER_COLUMNS;
                    columns = element.findElements(By.xpath(xpath));
                }

                headers = columns.stream().map(elem -> elem.getAttribute(ATTR_NAME)).collect(Collectors.toList());
                columnCount = headers.size();
            } else {
                String row = getFirstAvailableRowName();
                if (StringUtils.isNotEmpty(row)) {
                    xpath = "*[@Name='" + row + "']/*[@Name!='Column Headers']";
                    ConsoleUtils.log("scanning for table structure via " + xpath);
                    headers = element.findElements(By.xpath(xpath)).stream()
                                     .map(elem -> elem.getAttribute(ATTR_NAME))
                                     .collect(Collectors.toList());
                    columnCount = headers.size();
                }
            }
        }

        return new TableMetaData(headers, getTableRowCount(), cellTypes, normalizeSpace, columnCount);
    }

    @Nonnull
    private String getFirstAvailableRowName() {
        return element.findElements(By.xpath("*")).stream()
                      .filter(elem -> StringUtils.containsIgnoreCase(elem.getAttribute("Name"), "row"))
                      .findFirst()
                      .map(elem -> elem.getAttribute("Name")).orElse("");
    }

    @Nonnull
    private String getLastDataRowName() {
        return element.findElements(By.xpath("*")).stream()
                      .filter(elem -> StringUtils.contains(elem.getAttribute("Name"), "row "))
                      .map(elem -> elem.getAttribute("Name"))
                      .max(Comparator.naturalOrder()).orElse("");
    }

    @Nonnull
    private String getMatchingRowName(String endWith) {
        return element.findElements(By.xpath("*")).stream()
                      .filter(elem -> StringUtils.endsWith(elem.getAttribute("Name"), endWith))
                      .findFirst()
                      .map(elem -> elem.getAttribute("Name")).orElse("");
    }

    public List<List<String>> scanAllRows() { return fetchAll().getRows(); }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) and converting to CSV structure (text) */
    public void clickRow(int row) {
        sanityCheck();

        if (row < 0) { throw new IllegalArgumentException("row (zero-based) must be 0 or greater"); }

        int rowCount = getTableRowCount();
        if (rowCount < row - 1) {
            throw new IllegalArgumentException(String.format("Table '%s' has only %s; cannot click row %s",
                                                             name, rowCount, row));
        }

        clickOffset(getElement(), clickOffsetX, resolveClickOffsetY(row));
    }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) and converting to CSV structure (text) */
    public boolean clickCell(int row, String column) {
        sanityCheck(row, column);

        if (row > getTableRowCount()) {
            // perhaps user wants to click on a new row.. let's click new row by offset and then check if the new row is created
            clickOffset(element, clickOffsetX, (resolveClickOffsetY(row)));
            // todo: need to figure out XPATH for HierTable
            String xpath = StringUtils.replace(LOCATOR_NEW_ROW_CELL, "{column}", column);
            try {
                return element.findElement(By.xpath(xpath)) != null;
            } catch (NoSuchElementException e) {
                return false;
            }
        }

        String xpath = isTreeView ? LOCATOR_HIER_CELL : LOCATOR_CELL;
        xpath = StringUtils.replace(xpath, "{row}", (row + 1) + "");
        xpath = StringUtils.replace(xpath, "{column}", column);
        return clickElement(xpath);
    }

    public int findColumnIndex(String column) { return headers.indexOf(column); }

    public boolean containsColumnHeader(String header) {
        return headers != null && headers.contains(treatColumnHeader(header));
    }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) as well */
    public TableData fetch(int begin, int end) {
        sanityCheck();
        TableData tableData;
        Instant startTime = Instant.now();
        if (isTreeView) {
            String index = begin == end ?
                           "position()=" + (begin + 1) :
                           "position()>=" + (begin + 1) + " and position()<=" + (end + 1);
            String xpath = "*[@ControlType='ControlType.DataItem'][" + index + "]/*";
            List<WebElement> rowData = element.findElements(By.xpath(xpath));
            tableData = TableData.fromTreeViewRows(headers, rowData, Duration.between(startTime, Instant.now()));
        } else {
            String object = (String) driver.executeScript(SCRIPT_DATAGRID_FETCH, element, begin, end);
            tableData = new TableData(object, Duration.between(startTime, Instant.now()));
        }

        List<String> columnNames = tableData.getColumns();
        List<TableRow> data = tableData.getData();
        for (TableRow row : data) {
            for (int i = 0; i < columnNames.size(); i++) {
                if (row.size() <= i) { continue; }
                String columnName = columnNames.get(i);
                row.put(i + "", columnName, reformatCellData(columnName, row.get(columnName)));
            }
        }

        return tableData;
    }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) as well */
    public TableData fetchAll() {
        sanityCheck();
        Instant startTime = Instant.now();

        if (isTreeView) {
            int count = countChildren(element, DesktopUtils::isValidDataRow);
            List<WebElement> rowData = count < 1 ?
                                       new ArrayList<>() :
                                       element.findElements(By.xpath(LOCATOR_HIER_TABLE_ROWS + "/*"));
            return TableData.fromTreeViewRows(headers, rowData, Duration.between(startTime, Instant.now()));
        }

        return new TableData(driver.executeScript(SCRIPT_DATAGRID_FETCH_ALL, element),
                             Duration.between(startTime, Instant.now()));
    }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) as well */
    public int getTableRowCount() {
        if (isTreeView) {
            int count = countChildren(element, DesktopUtils::isValidDataRow);
            return count < 1 ? count : CollectionUtils.size(element.findElements(By.xpath(LOCATOR_HIER_TABLE_ROWS)));
        }

        Object object = driver.executeScript(SCRIPT_DATAGRID_ROW_COUNT, element);
        if (object == null) { return collectRowCountByXpath(); }

        JSONObject jsonObject = JsonUtils.toJSONObject(object.toString());
        try {
            if (jsonObject == null || jsonObject.has("error")) {
                ConsoleUtils.log("error fetching row count by driver. Try to get row count by xpath");
                // get the rowCount using xpath
                return collectRowCountByXpath();
            }
            return jsonObject.getInt("rows");
        } catch (JSONException e) {
            // get the rowCount using xpath
            ConsoleUtils.log("error fetching row count by driver. Try to get row count by xpath");
            return collectRowCountByXpath();
        }
    }

    /** As of v3.7, we will support HierTable (aka ControlType.Tree) as well */
    public DesktopTableRow fetchOrCreateRow(int row) {
        sanityCheck(row, null);
        ExecutionContext context = ExecutionThread.get();

        List<WebElement> dataElements = null;
        if (isTreeView) {
            // we don't know if there are any row in this data
            // so we use "*" to all children -- this is faster
            int count = countChildren(element, DesktopUtils::isValidDataRow);
            if (count == 0) {
                ConsoleUtils.log("No rows found for data grid " + element);
                dataElements = new ArrayList<>();
            } else {
                // now that we know there are data rows, we can fetch them confidently (and fast)
                dataElements = element.findElements(By.xpath(LOCATOR_HIER_TABLE_ROWS));
            }
        } else {
            dataElements = element.findElements(By.xpath(LOCATOR_TABLE_DATA));
            if (CollectionUtils.isEmpty(dataElements)) { ConsoleUtils.log("No rows found for data grid " + element); }
        }

        List<WebElement> rows = new ArrayList<>();
        //todo: short circuit this: fetch only requested row
        if (CollectionUtils.isNotEmpty(dataElements)) {
            dataElements.forEach(elem -> { if (elem != null) { rows.add(elem); }});
        }

        int rowCount = CollectionUtils.size(rows);
        boolean newRow = false;
        ConsoleUtils.log("current row count: " + rowCount);
        if (row >= rowCount) {
            row = rowCount;
            newRow = true;
        }

        String msgPrefix = "Table '" + label + "' Row '" + row + "' ";

        // find through existing column for the ones that would match specified criteria
        List<WebElement> columns;
        if (newRow) {
            List<WebElement> matches;
            if (!isTreeView) {
                // using offsets, only when to click on the first row and it is new row.
                if (row == 0) {
                    if (clickBeforeEdit()) {
                        clickOffset(element, clickOffsetX, resolveClickOffsetY(0));
                        try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    }
                    matches = element.findElements(By.xpath(LOCATOR_NEW_ROW));
                    if (CollectionUtils.isEmpty(matches)) {
                        // try clicking on first row
                        WebElement firstRow =
                            element.findElements(By.xpath("*")).stream()
                                   .filter(elem -> StringUtils.containsIgnoreCase(elem.getAttribute("Name"), "row"))
                                   .findFirst().orElse(null);
                        if (firstRow != null) {
                            firstRow.click();
                            matches = element.findElements(By.xpath(LOCATOR_NEW_ROW));
                        }
                        if (CollectionUtils.isEmpty(matches)) {
                            throw new IllegalArgumentException(
                                msgPrefix + "Unable to retrieve any row; no 'Add Row' found");
                        }
                    }
                } else {
                    // old table/datagrid acts weird some times... we need to click on it, it seems
                    // element.click();

                    // get the 'Add Row' element for new row and then get all the child elements with '*[@Name!='Column Headers']'
                    matches = element.findElements(By.xpath(LOCATOR_NEW_ROW));
                    if (CollectionUtils.isEmpty(matches)) {
                        try { Thread.sleep(5000);} catch (InterruptedException e) {}
                        matches = element.findElements(By.xpath(LOCATOR_NEW_ROW));
                    }

                    // in case the 'Add Row' not available, fetch the elements with previous row (row - 1)
                    if (CollectionUtils.isEmpty(matches)) {
                        String cellsXpath = StringUtils.replace(LOCATOR_CELLS, "{row}", (row - 1) + "");
                        ConsoleUtils.log("fetching columns for previous row" + msgPrefix + "via " + cellsXpath);
                        columns = element.findElements(By.xpath(cellsXpath));
                        for (WebElement column : columns) {
                            String editableColumnName = findAndSetEditableColumnName(column);
                            if (editableColumnName != null) {
                                new Actions(getDriver()).moveToElement(column, 0, 2).click().perform();
                                matches = element.findElements(By.xpath(LOCATOR_NEW_ROW));
                                if (CollectionUtils.isNotEmpty(matches)) { break; }
                            }
                        }
                    }
                }

                WebElement addRow = matches.get(0);
                String cellsXpath = "*[@Name!='Column Headers']";
                ConsoleUtils.log("fetching columns for " + msgPrefix + "via " + cellsXpath);
                columns = addRow.findElements(By.xpath(cellsXpath));
            } else {
                clickOffset(getElement(), clickOffsetX, resolveClickOffsetY(row));
                String cellsXpath = StringUtils.replace(LOCATOR_HIER_CELLS, "{row}", (row + 1) + "");
                ConsoleUtils.log("fetching columns for " + msgPrefix + "via " + cellsXpath);
                columns = element.findElements(By.xpath(cellsXpath));
            }
        } else {
            String cellsXpath;
            if (isTreeView) {
                cellsXpath = StringUtils.replace(LOCATOR_HIER_CELLS, "{row}", (row + 1) + "");
            } else {
                cellsXpath = "*[@Name='" + getMatchingRowName("row " + (row + 1)) + "']/*[@Name!='Column Headers']";
            }

            ConsoleUtils.log("fetching columns for " + msgPrefix + "via " + cellsXpath);
            columns = element.findElements(By.xpath(cellsXpath));
        }

        if (CollectionUtils.isEmpty(columns)) { throw new IllegalArgumentException(msgPrefix + "No columns found"); }

        // collect names so we can reuse them
        boolean editableColumnFound = context != null &&
                                      context.getBooleanData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_FOUND);

        Map<String, WebElement> columnMapping = new ListOrderedMap<>();
        for (WebElement column : columns) {
            String columnName = treatColumnHeader(column.getAttribute("Name"));
            if (!editableColumnFound) { findAndSetEditableColumnName(column); }
            columnMapping.put(columnName, column);
        }

        return new DesktopTableRow(row, newRow, columnMapping, this, null);
    }

    private boolean clickBeforeEdit() {
        ExecutionContext context = ExecutionThread.get();
        return context.getBooleanData(TABLE_CLICK_BEFORE_EDIT, getDefaultBool(TABLE_CLICK_BEFORE_EDIT));
    }

    public StepResult editCells(Integer row, Map<String, String> nameValues) {
        if (MapUtils.isEmpty(nameValues)) { throw new IllegalArgumentException("No name-value pairs provided"); }

        // check nameValues to make sure all referenced columns are valid
        nameValues.keySet().forEach(column -> {
            column = treatColumnHeader(column);
            if (StringUtils.isBlank(column)) { throw new IllegalArgumentException("Blank/empty column found"); }
            if (!headers.contains(column)) {
                throw new IllegalArgumentException("column '" + column + "' not found in Table object");
            }
        });

        DesktopTableRow tableRow = fetchOrCreateRow(row);
        if (tableRow == null || MapUtils.isEmpty(tableRow.getColumns())) {
            return StepResult.fail("Unable to fetch or create new '" + row + "'");
        }

        return editCells(tableRow, nameValues);
    }

    public StepResult editCells(DesktopTableRow tableRow, Map<String, String> nameValues) {
        if (tableRow == null || MapUtils.isEmpty(tableRow.getColumns())) {
            return StepResult.fail("Unable to edit cells since table row is not valid");
        }

        Map<String, WebElement> columnMapping = tableRow.getColumns();
        StringBuilder messageBuffer = new StringBuilder();

        ExecutionContext context = ExecutionThread.get();
        boolean tabAfterEdit = context.getBooleanData(TABLE_TAB_AFTER_EDIT, getDefaultBool(TABLE_TAB_AFTER_EDIT));

        // loop through each nameValues pairs
        WebElement cellElement;
        boolean focused = true;
        boolean setFocusOut = false;
        for (Map.Entry<String, String> nameValue : nameValues.entrySet()) {
            if (context.getBooleanData(DIALOG_LOOKUP, getDefaultBool(DIALOG_LOOKUP))) {
                // fail if any modal dialog present
                WebElement modal = DesktopCommand.getModalDialog(getCurrentSession().getApp().getElement());
                if (modal != null) {
                    return StepResult.fail("Table cannot be edited when the dialogue box '%s' is present",
                                           modal.getAttribute("Name"));
                }
            }

            String column = treatColumnHeader(nameValue.getKey());
            String value = nameValue.getValue();
            String msgPrefix2 = "Table '" + label + "' Row '" + tableRow.getRow() + "' Column '" + column + "' ";

            cellElement = columnMapping.get(column);
            if (cellElement == null) { return StepResult.fail("Unable to edit " + msgPrefix2 + ": NOT FOUND"); }

            //todo: support function keys along with [CLICK]? and [CHECK]

            if (StringUtils.equals(value, TABLE_CELL_CLICK)) {
                ConsoleUtils.log("clicked on " + msgPrefix2);
                focused = false;
                context.setData(CURRENT_DESKTOP_TABLE_ROW, tableRow);

                actionClick(cellElement);
                continue;
            }

            ConsoleUtils.log("editing " + msgPrefix2);
            if (isInvokePatternAvailable(cellElement) && !DesktopUtils.isCheckboxOrRadio(cellElement)) {
                new Actions(getDriver()).moveToElement(cellElement, 0, 2).click().perform();
                ConsoleUtils.log("clicked at (0,0) on " + msgPrefix2);
            }

            if (StringUtils.equals(value, TABLE_CELL_CHECK) || StringUtils.equals(value, TABLE_CELL_UNCHECK)) {
                WebElement checkboxElement = findCellCheckboxElement(cellElement, column);
                if (checkboxElement == null) {
                    messageBuffer.append(msgPrefix2).append("contains NO checkbox or radio element" + NL);
                    return StepResult.fail(messageBuffer.toString());
                }

                focused = false;
                boolean isChecked = DesktopUtils.isChecked(checkboxElement);
                ConsoleUtils.log("checkbox element is currently " + (isChecked ? "CHECKED" : "UNCHECKED"));
                boolean proceed = isChecked && StringUtils.equals(value, TABLE_CELL_UNCHECK) ||
                                  !isChecked && StringUtils.equals(value, TABLE_CELL_CHECK);

                if (proceed) {
                    if (!checkboxElement.isEnabled()) {
                        messageBuffer.append(msgPrefix2).append("is NOT ENABLED" + NL);
                        return StepResult.fail(messageBuffer.toString());
                    }

                    ConsoleUtils.log(msgPrefix2 + (isChecked ? "unchecking" : "checking") + " it...");

                    if (tabAfterEdit) {
                        // actions:
                        // 1. click (before keystroke), also toggle checkbox
                        // 2. enter TAB --> move focus to next cell element
                        driver.executeScript(toShortcuts("TAB"), checkboxElement);
                    } else {
                        // actions:
                        // 1. click (before keystroke), will also toggle checkbox
                        // 2. enter SPACE --> toggle checkbox back
                        // 3. enter SPACE --> toggle checkbox again
                        // note that focus remains on this element
                        driver.executeScript(toShortcuts("SPACE", "SPACE"), checkboxElement);
                    }

                    boolean isCheckedAfterAction = DesktopUtils.isChecked(checkboxElement);
                    if (isCheckedAfterAction == isChecked) {
                        // actions did nothing, try another route?
                        // this happens usually when the target element is the last one of the row
                        if (tabAfterEdit) {
                            // action:
                            // 1. click to focus
                            // 2. shift-tab to move focus back to current element
                            // 3. SPACE to toggle checkbox
                            // 4. TAB to move focus to next cell element
                            driver.executeScript(toShortcuts("SHIFT-TAB", "SPACE", "TAB"), checkboxElement);
                        } else {
                            // action:
                            // 1. click to focus
                            // 2. SPACE to toggle checkbox
                            // note that focus remains on this element
                            driver.executeScript(toShortcuts("SPACE"), checkboxElement);
                        }
                    }

                    if (context.getBooleanData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_FOUND) &&
                        context.getStringData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_NAME).contentEquals(column)) {
                        setFocusOut = true;
                    }

                    messageBuffer.append(msgPrefix2).append(isChecked ? "unchecked" : "checked").append(NL);
                }

            } else if (StringUtils.isEmpty(value) || StringUtils.equals(value, TABLE_CELL_CLEAR)) {
                clearCellContent(cellElement);
                messageBuffer.append(msgPrefix2).append("content cleared").append(NL);
                if (tabAfterEdit) { driver.executeScript(toShortcuts("TAB"), cellElement); }
            } else {
                // focused would be false if shortcut key is used
                focused = typeCellContent(tableRow, cellElement, column, value);
                messageBuffer.append(msgPrefix2).append("entered text '").append(value).append("'").append(NL);
                if (focused && tabAfterEdit) { driver.executeScript(toShortcuts("TAB"), cellElement); }
            }
        }

        if (focused) {
            // todo: need to handle first column = null problem
            if (setFocusOut) {
                looseCurrentFocus();
            } else if (context.getBooleanData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_FOUND) && !isTreeView) {
                // only applicable to pre- infragistics 4 components
                WebElement editColumn = tableRow.getColumns()
                                                .get(context.getStringData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_NAME));
                // using click action on editable column helps the driver find child elements
                try { editColumn.click(); } catch (Exception e) { }
            }
        }

        return StepResult.success(StringUtils.trim(messageBuffer.toString()));
    }

    public boolean clickFirstMatchedRow(Map<String, String> nameValues) {
        if (MapUtils.isEmpty(nameValues)) { return false; }

        // check names
        String xpath = "*[" +
                       nameValues.keySet().stream()
                                 .filter(name -> headers.contains(name))
                                 .map(name -> resolveMatchingColumnXpath(name, nameValues.get(name)))
                                 .collect(Collectors.joining(" and ")) +
                       "]";
        if (StringUtils.isBlank(xpath)) {
            throw new IllegalArgumentException(String.format("Column '%s'' is not valid for this Table",
                                                             nameValues.keySet().stream()
                                                                       .filter(name -> !headers.contains(name))
                                                                       .collect(Collectors.joining(", "))));
        }

        ConsoleUtils.log("finding/clicking on first matched row via " + xpath);
        List<WebElement> elements = element.findElements(By.xpath(xpath));
        if (CollectionUtils.isEmpty(elements)) { return false; }

        WebElement row = elements.get(0);
        clickOffset(row, clickOffsetX, TABLE_ROW_HEIGHT / 2);
        return true;
    }

    // todo: handle the duplicate code
    protected static DesktopSession getCurrentSession() {
        ExecutionContext context = ExecutionThread.get();
        if (!context.hasData(CURRENT_DESKTOP_SESSION)) { return null; }

        Object sessionObj = context.getObjectData(CURRENT_DESKTOP_SESSION);
        if (!(sessionObj instanceof DesktopSession)) {
            context.removeData(CURRENT_DESKTOP_SESSION);
            return null;
        }

        DesktopSession session = (DesktopSession) sessionObj;
        if (session.getApp() == null) {
            ConsoleUtils.error("desktop session found without 'app'; return null!");
            return null;
        }

        if (StringUtils.isBlank(session.getAppId())) {
            ConsoleUtils.error("desktop session found without 'App ID'; return null!");
            return null;
        }

        return session;
    }

    protected void actionClick(WebElement element) { new Actions(getDriver()).moveToElement(element).click().perform();}

    protected String findAndSetEditableColumnName(WebElement column) {
        int tableXoffset = 0;
        BoundingRectangle tableBoundary = BoundingRectangle.newInstance(element);
        if (tableBoundary != null) { tableXoffset = tableBoundary.getX(); }

        BoundingRectangle columnBoundary = BoundingRectangle.newInstance(column);
        int columnXoffset = 0;
        int columnWidth = 0;
        if (columnBoundary != null) {
            columnXoffset = columnBoundary.getX();
            columnWidth = columnBoundary.getWidth();
        }

        if (clickOffsetX > (columnXoffset - tableXoffset) &&
            clickOffsetX < (columnXoffset - tableXoffset + columnWidth)) {
            String editColumnName = treatColumnHeader(column.getAttribute("Name"));
            ExecutionContext context = ExecutionThread.get();
            if (context != null) {
                context.setData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_FOUND, true);
                context.setData(CURRENT_DESKTOP_TABLE_EDITABLE_COLUMN_NAME, editColumnName);
            }
            return editColumnName;
        }

        return null;
    }

    protected WebElement findCellCheckboxElement(WebElement cellElement, String column) {
        // 1. the cell element could just be a checkbox
        if (isCheckbox(cellElement)) {
            ConsoleUtils.log("Found element as CHECKBOX");
            return cellElement;
        }

        // search for the CHECKBOX child element
        String xpath = "*[@ControlType='" + CHECK_BOX + "']";

        // 2. sometimes the checkbox hides under another element by the same name (WEIRD .NET CRAP)
        if (countChildren(cellElement) > 0) {
            String columnXpath = resolveColumnXpath(column);
            List<WebElement> matches = cellElement.findElements(By.xpath(columnXpath));
            if (CollectionUtils.isNotEmpty(matches)) {
                ConsoleUtils.log("Found surrogate (inner) element; binding to first nested element");
                return matches.get(0);
            }

            // 3. could be under cell (esp. if the target cell is a TEXTBOX)
            matches = cellElement.findElements(By.xpath(xpath));
            if (CollectionUtils.isNotEmpty(matches)) {
                ConsoleUtils.log("Found CHECKBOX element via CELL");
                return matches.get(0);
            }
        }


        // 4. could be under TABLE (esp. if the target cell is a COMBO)
        List<WebElement> matches = element.findElements(By.xpath(xpath));
        if (!CollectionUtils.isEmpty(matches)) {
            ConsoleUtils.log("Found CHECKBOX element via TABLE");
            return matches.get(0);
        }

        // give up
        return null;
    }

    protected void sanityCheck(int row, String column) {
        sanityCheck();

        if (row < 0) { throw new IllegalArgumentException("row (zero-based) must be 0 or greater"); }
        if (CollectionUtils.isEmpty(headers)) { scanStructure(); }
        if (CollectionUtils.isEmpty(headers)) {
            throw new IllegalStateException("Unable to resolve metadata for Table '" + getLabel() + "'");
        }
        if (StringUtils.isNotEmpty(column) && !headers.contains(treatColumnHeader(column))) {
            throw new IllegalArgumentException("column '" + column + "' not found in Table object");
        }
    }

    protected void clickOffset(WebElement element, int x, int y) {
        ConsoleUtils.log("clicking on table via offset (" + x + "," + y + ")");
        new Actions(getDriver()).moveToElement(element, x, y).click().perform();
    }

    protected boolean clickElement(String locator) {
        try {
            WebElement cell = element.findElement(By.xpath(locator));
            if (cell == null) { return false; }
            actionClick(cell);
            return true;
        } catch (NoSuchElementException e) {
            ConsoleUtils.error("No cell retrieved via " + locator + " table '" + getLabel() + "'");
            return false;
        }
    }

    protected void sanityCheck() {
        String errorPrefix = "Failed to scan table: ";
        if (StringUtils.isBlank(getXpath())) {
            throw new IllegalStateException(errorPrefix + "No corresponding desktop element");
        }
        if (driver == null) { throw new IllegalStateException(errorPrefix + "No driver associated to this instance"); }
        if (element == null) { element = driver.findElement(By.xpath(getXpath())); }
    }

    /** support PolyMatcher, as of v3.7 */
    protected boolean containsAnywhere(List<List<String>> data, List<String> expected) {
        // if there's no data to check, and we aren't checking anything -- then that's a PASS (and weird)
        if (CollectionUtils.isEmpty(expected)) { return CollectionUtils.isEmpty(data); }
        return data.stream().anyMatch(row -> contains(row, expected));
    }

    /** support PolyMatcher, as of v3.7 */
    protected boolean containsColumnData(String column, String... text) {
        if (ArrayUtils.isEmpty(text)) { return true; }

        int pos = resolveColumnIndex(column);
        if (pos == UNDEFINED) { return false; }

        List<String> actual = fetchAll().getRows()
                                        .stream()
                                        .filter(Objects::nonNull)
                                        .map(row -> {
                                            String data = row.get(pos);
                                            return !PolyMatcher.isPolyMatcher(data) ?
                                                   matchCellFormat(column, data) : data;
                                        })
                                        .collect(Collectors.toList());
        return contains(actual, Arrays.asList(text));
    }

    /** support PolyMatcher, as of v3.7 */
    protected boolean containsRowData(int row, String... text) {
        List<String> data = fetch(row, row).getRows().get(0);
        return isValidRow(data, row) && contains(data, Arrays.asList(text));
    }

    /** support PolyMatcher, as of v3.7 */
    protected List<List<String>> findMatchedRows(List<String> criterion) {
        List<List<String>> tableRows = fetchAll().getRows();
        if (tableRows.isEmpty()) { throw new IllegalStateException("Table does not contain any data"); }
        return tableRows.stream().filter(row -> contains(row, criterion)).collect(Collectors.toList());
    }

    protected int resolveColumnIndex(String column) {
        if (StringUtils.isBlank(column)) {
            ConsoleUtils.log("Invalid column name specified: " + column);
            return UNDEFINED;
        }

        if (CollectionUtils.isEmpty(headers)) {
            ConsoleUtils.log("Empty headers or header information not yet collected");
            return UNDEFINED;
        }

        column = treatColumnHeader(column);
        int pos = headers.indexOf(column);
        if (pos < 0) {
            ConsoleUtils.log("Invalid column name specified: " + column);
            return UNDEFINED;
        }

        return pos;
    }

    protected boolean isValidRow(List<String> data, int row) {
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

    /** support PolyMatcher, as of v3.7 */
    protected boolean contains(List<String> actual, List<String> expected) {
        // the specified row is also empty or contains only empty string, then this is a match
        if (CollectionUtils.isEmpty(expected) || TextUtils.countMatches(expected, "") == expected.size()) {
            return CollectionUtils.isEmpty(actual) || actual.contains("");
        }

        for (String expectedValue : expected) {
            if (StringUtils.isEmpty(expectedValue)) { continue; }

            if (PolyMatcher.isPolyMatcher(expectedValue)) {
                if (!TextUtils.polyMatch(actual, expectedValue)) {
                    ConsoleUtils.log("EXPECTED value '" + expectedValue + "' is NOT poly-matched in " + actual);
                    return false;
                }
            } else if (!actual.contains(expectedValue)) {
                ConsoleUtils.log("EXPECTED value '" + expectedValue + "' is NOT found in " + actual);
                return false;
            }
        }

        return true;
    }

    protected String toString(List<List<String>> data) {
        if (CollectionUtils.isEmpty(data)) { return ""; }
        StringBuilder buffer = new StringBuilder();
        for (List<String> row : data) {
            for (String cell : row) { buffer.append("[").append(cell).append("]"); }
            buffer.append("\n");
        }
        return buffer.toString();
    }

    /**
     * reformat cell value to the specified format, which is configured as part of the "extra" section in the component
     * JSON.
     * <p>
     * Currently only date and number formats are supported.
     */
    protected String reformatCellData(String column, String value) { return reformatCellData(column, value, true); }

    /**
     * recalibrate {@literal value} to match the configured format of the specified column. This is needed to ensure
     * data format consistancy between user input and expected value format of the corresponding data grid.
     */
    protected String matchCellFormat(String column, String value) { return reformatCellData(column, value, false); }

    protected String reformatCellData(String column, String value, boolean fromTo) {
        String key = column + ".format";
        return !extra.containsKey(key) ? value : DesktopUtils.reformatValue(value, extra.get(key), fromTo);
    }

    private void looseCurrentFocus() {
        DesktopSession session = getCurrentSession();
        requiresNotNull(session, "No active desktop session found");

        IgExplorerBar explorerBar = session.findThirdPartyComponent(IgExplorerBar.class);
        if (explorerBar != null) {
            // todo: need to configure the element to click on per application
            explorerBar.clickFirstSectionHeader();
        } else {
            ConsoleUtils.log("no explorer bar component specified/loaded; try pressing escape key...");
            new Actions(driver).sendKeys(ESCAPE);
        }
    }

    private void clearCellContent(WebElement cellElement) { driver.executeScript(SCRIPT_SET_VALUE, cellElement, ""); }

    private int collectRowCountByXpath() {
        // collect row count using xpath
        List<WebElement> dataElements = element.findElements(By.xpath(LOCATOR_TABLE_DATA));
        if (CollectionUtils.isEmpty(dataElements)) {
            ConsoleUtils.log("No rows found for data grid " + element);
        }
        return CollectionUtils.size(dataElements);
    }

    /**
     * this method accounts for scenario where the calculated y-offset has gone beyond the bounds of the containing
     * grid component. In such case, this method will resolve to the last middle of the last row instead.
     */
    private int resolveClickOffsetY(int row) {
        int offsetY = headerHeight + (TABLE_ROW_HEIGHT * row) + (TABLE_ROW_HEIGHT / 2);

        String boundingRectangle = this.element.getAttribute("BoundingRectangle");
        if (StringUtils.isBlank(boundingRectangle)) { return offsetY; }

        String[] dimensions = StringUtils.split(boundingRectangle, ",");
        if (ArrayUtils.getLength(dimensions) != 4) { return offsetY; }

        int height = NumberUtils.toInt(dimensions[3], -1);
        if (height == -1) { return offsetY; }

        return (offsetY > height) ? height - (TABLE_ROW_HEIGHT / 2) : offsetY;
    }

    private String treatColumnHeader(String header) {return normalizeSpace ? TextUtils.xpathNormalize(header) : header;}

    protected String resolveMatchingColumnXpath(String column, String value) {
        String xpath = "./*[" + resolveColumnXpathCondition(column) + " and ";

        if (StringUtils.equals(value, TABLE_CELL_CHECK)) { value = "True"; }
        if (StringUtils.equals(value, TABLE_CELL_UNCHECK)) { value = "False"; }
        if (StringUtils.equals(value, TABLE_CELL_CLEAR)) { value = ""; }

        if (isInfragistic4Aware() && isTreeView) {
            xpath += "contains(@ItemStatus, '" + value + INFRAG4_ITEM_STATUS_POSTFIX + "')";
        } else {
            xpath += "@Value='" + matchCellFormat(column, value) + "'";
        }

        return xpath + "]";
    }

    private String resolveColumnXpath(String column) { return "*[" + resolveColumnXpathCondition(column) + "]"; }

    private String resolveColumnXpathCondition(String column) {
        return (normalizeSpace ? "normalize-space(@Name)='" : "@Name='") + column + "'";
    }

    private boolean typeCellContent(DesktopTableRow tableRow, WebElement cellElement, String column, String value) {
        if (StringUtils.equals(reformatCellData(column, infragistic4Text(cellElement), true), value)) {
            ConsoleUtils.log("Text '" + value + "' already entered into cell '" + column + "'");
            return true;
        }

        // special treatment for formatted textbox
        if (MapUtils.isNotEmpty(cellTypes) && cellTypes.containsKey(column)) {
            String cellType = cellTypes.get(column);
            try {
                ElementType elementTypeHint = ElementType.valueOf(cellType);
                if (elementTypeHint == FormattedTextbox) {
                    return typeValueWithFunctionKey(tableRow, cellElement, value);
                }
            } catch (IllegalArgumentException e) {
                // never mind...
                ConsoleUtils.error("Unknown overridden cell type: " + cellType + e.getMessage());
                return true;
            }
        }

        String controlType = cellElement.getAttribute("ControlType");
        if (StringUtils.equals(controlType, COMBO)) {
            // while combo usually contains edit element from which we can type in value, seeking for such element might
            // dramatically increase execution time esp. when the combo in question contains many selection items.
            // therefore for combo, we would directly enter text (and trust that) and then evaluate the combo's value
            // afterwards
            return typeValueWithFunctionKey(tableRow, cellElement, value);
        }

        if (isValuePatternAvailable(cellElement)) {
            // the code below is not stable for combo.. since some combo do not receive 'text changed' event until focus is lost.
            // ConsoleUtils.log("using setValue on '" + column + "' on '" + cellElement.getAttribute("ControlType") + "'");
            // driver.executeScript("automation: ValuePattern.SetValue", cellElement, value);
            ConsoleUtils.log("type '" + value + "' on '" + column + "'");
            return typeValueWithFunctionKey(tableRow, cellElement, value);
        } else if (isTextPatternAvailable(cellElement)) {
            ConsoleUtils.log("using shortcut on '" + column + "'");
            driver.executeScript(SCRIPT_PREFIX_SHORTCUT + joinShortcuts(value), cellElement);
            return true;
        } else {
            ConsoleUtils.log("using sendKeys on '" + column + "'");
            return typeValueWithFunctionKey(tableRow, cellElement, value);
        }
    }

    private boolean typeValueWithFunctionKey(DesktopTableRow tableRow, WebElement cellElement, String value) {
        ExecutionContext context = ExecutionThread.get();
        if (TextUtils.isBetween(value, "[", "]")) {
            ConsoleUtils.log("enter shortcut keys " + value + " on '" + label + "'");
            context.setData(CURRENT_DESKTOP_TABLE_ROW, tableRow);
            driver.executeScript(SCRIPT_PREFIX_SHORTCUT + joinShortcuts(value), cellElement);
            return false;
        }

        String currentValue = infragistic4Text(cellElement);
        String shortcutPrefix = SCRIPT_PREFIX_SHORTCUT +
                                (StringUtils.isNotEmpty(currentValue) ? "<[HOME]><[SHIFT-END]><[DEL]>" : "");

        Pattern p = Pattern.compile("^(.+)\\[(.+)]$");
        Matcher m = p.matcher(value);
        if (m.find()) {
            // extract combo selection
            value = m.group(1);
            // extract what's between [...]
            String postShortcut = m.group(2);

            if (context != null) { context.setData(CURRENT_DESKTOP_TABLE_ROW, tableRow); }
            String script = shortcutPrefix + joinShortcuts(value) +
                            (StringUtils.isNotBlank(postShortcut) ? toShortcuts(postShortcut) : "");
            driver.executeScript(script, cellElement);
            return false;
        } else {
            driver.executeScript(shortcutPrefix + joinShortcuts(value), cellElement);
            return true;
        }
    }
}