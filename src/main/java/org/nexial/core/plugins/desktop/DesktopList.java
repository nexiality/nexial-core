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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.apache.commons.lang3.builder.ToStringStyle.NO_CLASS_NAME_STYLE;
import static org.nexial.core.plugins.desktop.DesktopConst.UNDEFINED;
import static org.nexial.core.plugins.desktop.ElementType.EDIT;
import static org.nexial.core.plugins.desktop.ElementType.LIST_ITEM;

public class DesktopList extends DesktopElement {
    public static final String CELL_SEP = "  ";
    public static final String CELL_SEP2 = "\t";

    protected List<String> headers;
    protected int columnCount = UNDEFINED;
    protected transient int rowCount = UNDEFINED;
    protected String headerXpath;
    protected String dataRegex;
    protected List<Integer> dataWidths;
    protected transient ArrayList<List<String>> data = new ArrayList<>();
    protected transient List<WebElement> dataElements = new ArrayList<>();

    public static class ListMetaData {
        private List<String> headers;
        private int columnCount;
        private int rowCount;

        public List<String> getHeaders() { return headers; }

        public void setHeaders(List<String> headers) { this.headers = headers; }

        public int getColumnCount() { return columnCount; }

        public void setColumnCount(int columnCount) { this.columnCount = columnCount; }

        public int getRowCount() { return rowCount; }

        public void setRowCount(int rowCount) { this.rowCount = rowCount; }

        @Override
        public String toString() {
            return new ToStringBuilder(this, NO_CLASS_NAME_STYLE)
                       .append("headers", headers)
                       .append("columnCount", columnCount)
                       .append("rowCount", rowCount)
                       .toString();
        }
    }

    protected DesktopList() { }

    public static DesktopList toInstance(DesktopElement component) {
        DesktopList instance = new DesktopList();
        copyTo(component, instance);

        String configuredHeaders = MapUtils.getString(instance.extra, "headers");
        if (StringUtils.isNotBlank(configuredHeaders)) {
            instance.headers = TextUtils.toList(configuredHeaders, ",", true);
        }
        instance.headerXpath = MapUtils.getString(instance.extra, "headerXpath");
        instance.dataRegex = MapUtils.getString(instance.extra, "dataRegex");
        String dataWidths = MapUtils.getString(instance.extra, "dataWidths");
        if (StringUtils.isNotBlank(dataWidths)) {
            List<String> widthStrings = TextUtils.toList(dataWidths, ",", true);
            if (CollectionUtils.isNotEmpty(widthStrings)) {
                List<Integer> widths = new ArrayList<>();
                widthStrings.forEach(string -> widths.add(NumberUtils.toInt(string)));
                instance.dataWidths = widths;
            }
        }
        return instance;
    }

    public ListMetaData toMetaData() {
        ListMetaData metadata = new ListMetaData();
        metadata.setHeaders(headers);
        metadata.setColumnCount(columnCount);
        metadata.setRowCount(rowCount);
        return metadata;
    }

    public List<String> getHeaders() { return headers; }

    public ArrayList<List<String>> getData() { return data; }

    public int getRowCount() { return rowCount; }

    public int getColumnCount() { return columnCount; }

    public void inspect() {
        if (CollectionUtils.isEmpty(headers)) {
            inspectHeader();
            if (CollectionUtils.isEmpty(headers)) { return; }
        } else {
            columnCount = headers.size();
        }

        rowCount = UNDEFINED;
        inspectData();
    }

    public List<String> getData(int rowNumber) {
        if (rowNumber < 0) { return null; }
        if (rowNumber >= data.size()) { return null; }
        return data.get(rowNumber);
    }

    public WebElement getDataElement(int rowNumber) {
        if (rowNumber < 0) { return null; }
        if (rowCount == UNDEFINED) { inspectData(); }
        if (rowNumber >= dataElements.size()) { return null; }
        return dataElements.get(rowNumber);
    }

    public WebElement findFirstDataElementContaining(String contains) {
        if (StringUtils.isBlank(contains)) { return dataElements.get(0); }

        WebElement matched = null;
        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            boolean found = false;
            for (String cell : row) {
                if (StringUtils.contains(cell, contains)) {
                    found = true;
                    break;
                }
            }

            if (found) {
                matched = dataElements.get(i);
                break;
            }
        }

        return matched;
    }

    public List<List<String>> getDataContaining(String... contains) {
        if (ArrayUtils.isEmpty(contains)) { return data; }

        Set<Integer> matchedRows = new HashSet<>();

        for (int i = 0; i < data.size(); i++) {
            if (matchedRows.contains(i)) { continue; }

            List<String> row = data.get(i);
            boolean found = false;
            for (String containing : contains) {
                for (String cell : row) {
                    if (StringUtils.contains(cell, containing)) {
                        found = true;
                        matchedRows.add(i);
                        break;
                    }
                }

                if (found) { break; }
            }
        }

        List<List<String>> matched = new ArrayList<>();
        for (int row : matchedRows) { matched.add(new ArrayList<>(data.get(row))); }
        return matched;
    }

    public List<List<String>> getMatchingData(String header, String value) {
        if (StringUtils.isBlank(header)) { return null; }
        if (CollectionUtils.isEmpty(headers)) { return null; }
        if (!headers.contains(header)) { return null; }
        if (StringUtils.isBlank(value)) { value = ""; }

        int cellIndex = headers.indexOf(header);
        List<List<String>> matched = new ArrayList<>();

        for (List<String> row : data) {
            if (StringUtils.equals(IterableUtils.get(row, cellIndex), value)) { matched.add(new ArrayList<>(row)); }
        }

        return matched;
    }

    public int findFirstMatchedRow(String contains) {
        if (CollectionUtils.isEmpty(data)) { return UNDEFINED; }

        // every row would match empty string, hence returning first row
        if (StringUtils.isEmpty(contains)) { return 0; }

        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            for (String cell : row) { if (StringUtils.contains(cell, contains)) { return i; } }
        }

        return UNDEFINED;
    }

    public int findFirstMatchedRow(String... contains) {
        if (CollectionUtils.isEmpty(data)) { return UNDEFINED; }

        // every row would match empty string, hence returning first row
        if (ArrayUtils.isEmpty(contains)) { return 0; }

        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            boolean matchedAll = false;

            for (String contain : contains) {
                matchedAll = false;
                for (String cell : row) {
                    if (StringUtils.contains(cell, contain)) {
                        matchedAll = true;
                        break;
                    }
                }

                if (!matchedAll) { break; }
            }

            if (matchedAll) { return i; }
        }

        return UNDEFINED;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, NO_CLASS_NAME_STYLE)
                   .append("headers", headers)
                   .append("columnCount", columnCount)
                   .append("rowCount", rowCount)
                   .append("data", data)
                   .toString();
    }

    protected void inspectHeader() {
        if (StringUtils.isBlank(headerXpath)) {
            ConsoleUtils.error("No XPATH for header found.  Unable to inspect this " + elementType);
            return;
        }

        // fetch header
        List<WebElement> headerElements = driver.findElements(By.xpath(headerXpath));
        if (CollectionUtils.isEmpty(headerElements)) {
            ConsoleUtils.error("XPATH for header returned no matches.  Unable to inspect element: " + headerXpath);
            return;
        }

        // parse header into header list (headers)
        if (headerElements.size() == 1) {
            // there's only 1 element, but generally "headers" means a list of string.  So we'll investigate if we
            // can parse the @Name of this element into headers
            WebElement headerElement = headerElements.get(0);
            String headerControlType = headerElement.getAttribute("ControlType");
            String headerName = StringUtils.equals(headerControlType, EDIT) ?
                                headerElement.getText() : StringUtils.trim(headerElement.getAttribute("Name"));

            String sep = null;
            if (StringUtils.contains(headerName, CELL_SEP)) {
                // assuming each header text is separated by space(s)
                // e.g. "Active     SSN            Name             Client  Type    FSO ID          FSO Name"
                // Active     SSN            Name             Client  Type    FSO ID          FSO Name
                sep = CELL_SEP;
            } else if (StringUtils.contains(headerName, CELL_SEP2)) {
                sep = CELL_SEP2;
            }

            if (sep == null && CollectionUtils.size(dataWidths) < 1) {
                ConsoleUtils.error("Unable to determine header delimiter.  Unable to inspect element: " + headerName);
                return;
            }

            if (sep == null) {
                headers = new ArrayList<>();
                headers.add(headerName);
            } else {
                headers = TextUtils.toList(headerName, sep, true);
            }
        } else {
            // multiple elements means each element represent 1 header
            headers = new ArrayList<>();
            headerElements.forEach(headerElement -> headers.add(StringUtils.trim(headerElement.getAttribute("Name"))));
        }

        if (CollectionUtils.isEmpty(headers)) {
            ConsoleUtils.error("Unable to parse headers - Unable to inspect element: " + headerXpath);
            return;
        }

        columnCount = headers.size();
    }

    protected void inspectData() {
        if (StringUtils.isBlank(getXpath())) {
            ConsoleUtils.error("Invalid list element.  Unable to inspect element");
            return;
        }

        // short-circuit
        if (rowCount != UNDEFINED && CollectionUtils.isNotEmpty(data)) { return; }

        if (columnCount == UNDEFINED) { inspectHeader(); }

        // fetch data
        data.clear();
        dataElements.clear();
        String xpath = StringUtils.appendIfMissing(getXpath(), "/") + "*[@ControlType='" + LIST_ITEM + "']";
        List<WebElement> dataElements = driver.findElements(By.xpath(xpath));
        if (CollectionUtils.isEmpty(dataElements)) {
            rowCount = UNDEFINED;
            ConsoleUtils.error("No data rows found.  Unable to inspect element: " + xpath);
            return;
        }

        // parse data into cells
        rowCount = CollectionUtils.size(dataElements);
        dataElements.forEach(dataElement -> data.add(collectRowText(dataElement.getAttribute("Name"))));
        this.dataElements = dataElements;

        List<WebElement> scrollbars = element.findElements(By.xpath(DesktopConst.XPATH_SCROLLBARS));
        if (CollectionUtils.isNotEmpty(scrollbars)) { scrollbars.forEach(this::handleScrollbar); }
    }

    protected List<String> collectRowText(String dataRow) {
        if (StringUtils.isBlank(dataRow)) {
            // no need to parse.. just create an empty row
            String[] emptyRow = new String[headers.size()];
            Arrays.fill(emptyRow, "");
            return Arrays.asList(emptyRow);
        }

        if (StringUtils.isNotBlank(dataRegex)) { return RegexUtils.collectGroups(dataRow, dataRow); }

        if (CollectionUtils.isNotEmpty(dataWidths)) {
            List<String> row = new ArrayList<>();
            final String[] dataRowText = new String[]{dataRow};
            dataWidths.forEach(width -> {
                if (width < 1) {
                    row.add(StringUtils.trim(dataRowText[0]));
                    dataRowText[0] = null;
                } else {
                    String rowText = StringUtils.substring(dataRowText[0], 0, width);
                    dataRowText[0] = StringUtils.substringAfter(dataRowText[0], rowText);
                    row.add(StringUtils.trim(rowText));
                }
            });

            return row;
        }

        String sep = null;
        if (StringUtils.contains(dataRow, CELL_SEP)) {
            sep = CELL_SEP;
        } else if (StringUtils.contains(dataRow, CELL_SEP2)) {
            sep = CELL_SEP2;
        }

        if (sep == null && CollectionUtils.size(dataWidths) < 1) {
            ConsoleUtils.error("Unable to determine row delimiter.  Skipping over this row: " + dataRow);
            return null;
        }

        if (sep == null) {
            List<String> row = new ArrayList<>();
            row.add(dataRow);
            return row;
        } else {
            return TextUtils.toList(dataRow, sep, true);
        }
    }
}
