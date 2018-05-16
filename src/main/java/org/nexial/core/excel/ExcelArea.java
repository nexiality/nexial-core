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

package org.nexial.core.excel;

import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.core.excel.Excel.Worksheet;

/**
 * represents a contiguous excel area; basically a 2-D area
 */
public class ExcelArea {
    private Worksheet worksheet;
    private ExcelAddress addr;
    private boolean hasHeader;

    private List<List<XSSFCell>> wholeArea;
    private List<List<XSSFCell>> area;
    private List<XSSFCell> headerRow;

    public ExcelArea(Worksheet worksheet, ExcelAddress addr, boolean hasHeader) {
        assert worksheet != null && worksheet.getSheet() != null;
        assert addr != null && addr.getStart() != null;

        this.worksheet = worksheet;
        this.addr = addr;
        this.hasHeader = hasHeader;

        wholeArea = worksheet.cells(addr);
        if (hasHeader) {
            area = wholeArea;
            headerRow = area.remove(0);
        } else {
            area = wholeArea;
            headerRow = null;
        }
    }

    public Worksheet getWorksheet() { return worksheet; }

    public ExcelAddress getAddr() { return addr; }

    public boolean isHasHeader() { return hasHeader; }

    /** the entire area of specified cells, including headers (if <code>hasHeader</code> is true) */
    public List<List<XSSFCell>> getWholeArea() { return wholeArea; }

    /** the entire area of specified cells, excluding headers (if <code>hasHeader</code> is true) */
    public List<List<XSSFCell>> getArea() { return area; }

    public List<XSSFCell> getHeaderRow() { return headerRow; }

    public XSSFCell getTopLeft() { return area.get(0).get(0); }

    public XSSFCell getBottomRight() {
        List<XSSFCell> lastRow = area.get(area.size() - 1);
        return lastRow.get(lastRow.size() - 1);
    }

    List<XSSFCell> row(int i) {
        assert i > -1;
        return area.get(i);
    }

    List<XSSFCell> column(int i) {
        assert i > -1;
        return area.get(i);
    }

    List<XSSFCell> getRow(int i) { return row(i); }

    List<XSSFCell> getColumn(int i) { return column(i); }
}
