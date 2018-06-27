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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.*;
import org.nexial.commons.proc.ProcessInvoker;
import org.nexial.commons.proc.ProcessOutcome;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.excel.ExcelConfig.*;
import org.nexial.core.excel.ext.CellTextReader;
import org.nexial.core.utils.ConsoleUtils;

import static java.io.File.separator;
import static org.apache.commons.lang3.SystemUtils.*;
import static org.apache.poi.ss.SpreadsheetVersion.EXCEL2007;
import static org.apache.poi.ss.usermodel.Cell.CELL_TYPE_BLANK;
import static org.apache.poi.ss.usermodel.CellType.ERROR;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.core.excel.ExcelConfig.StyleConfig.*;

/**
 * Wrapper for managing Excel documents.
 */
public class Excel {
    public static final int MIN_EXCEL_FILE_SIZE = 5 * 1024;

    // var to support spring-injected value
    float _cellSpacing = 5.2f;

    private File file;
    private XSSFWorkbook workbook;
    /** all worksheets in the excel represented by the file parameter */
    private List<XSSFSheet> allsheets;
    /** list all existing cell styles in the excel file */
    private Set<XSSFCellStyle> workbookStyles;
    private Map<String, XSSFCellStyle> commonStyles;

    public class Worksheet {
        private XSSFSheet sheet;
        private String name;

        Worksheet(XSSFSheet sheet) {
            assert sheet != null;
            this.sheet = sheet;
            name = sheet.getSheetName();
        }

        Worksheet(XSSFWorkbook workbook, int index) { this(workbook.getSheetAt(index)); }

        Worksheet(XSSFWorkbook workbook, String name) { this(workbook.getSheet(name)); }

        public XSSFSheet getSheet() { return sheet; }

        public String getName() { return name; }

        public boolean isHidden() {
            XSSFWorkbook workbook = getWorkbook();
            return workbook.isSheetHidden(workbook.getSheetIndex(sheet));
        }

        public void hide() { hideOrShow(true); }

        public void show() { hideOrShow(false); }

        /** get a top-left cell of a range based on {@code addr} */
        public XSSFCell cell(ExcelAddress addr) {
            assert addr != null && addr.getStart() != null;

            XSSFRow row = sheet.getRow(addr.getStart().getLeft());
            if (row == null) { return null; }
            return row.getCell(addr.getStart().getRight());
        }

        /**
         * alias to {@link #cell(ExcelAddress)}.
         *
         * @see #cell(ExcelAddress)
         */
        public XSSFCell firstCell(ExcelAddress addr) { return cell(addr); }

        /**
         * get the range of cells based on {@code addr}, where the outer {@link List} represents rows the the
         * inner {@link List} represents columns of the associated row.
         */
        public List<List<XSSFCell>> cells(ExcelAddress addr) { return cells(addr, true); }

        public List<List<XSSFCell>> cells(ExcelAddress addr, boolean skipNullRows) {
            assert addr != null;
            Pair<Integer, Integer> startPos = addr.getStart();
            Pair<Integer, Integer> endPos = addr.getEnd();
            assert startPos != null && endPos != null;

            List<List<XSSFCell>> availableRows = new ArrayList<>(addr.getRowCount());
            int startRow = startPos.getLeft();
            int startCol = startPos.getRight();
            int endRow = endPos.getLeft();
            int endCol = endPos.getRight();

            for (int i = startRow; i <= endRow; i++) {
                XSSFRow row = sheet.getRow(i);
                if (skipNullRows && row == null) { continue; }

                List<XSSFCell> availableCells = new ArrayList<>(addr.getColumnCount());
                if (row != null) {
                    for (int j = startCol; j <= endCol; j++) {
                        availableCells.add(row.getCell(j, CREATE_NULL_AS_BLANK));
                    }
                }
                availableRows.add(availableCells);
            }
            return availableRows;
        }

        public ExcelArea area(ExcelAddress addr) { return new ExcelArea(this, addr, false); }

        public ExcelArea areaWithHeader(ExcelAddress addr) { return new ExcelArea(this, addr, true); }

        public XSSFCell appendToCell(ExcelAddress addr, String appendWith) {
            return appendToCell(addr, appendWith, null);
        }

        public XSSFCell appendToCell(ExcelAddress addr, String appendWith, Style style) {
            assert addr != null;
            assert StringUtils.isNotBlank(appendWith);
            assert style != null;

            XSSFCell cell = firstCell(addr);
            int cellType = cell.getCellType();
            if (cellType == CELL_TYPE_BLANK) {
                setValue(cell, appendWith, style);
                return cell;
            }

            XSSFRichTextString cellValue = cell.getRichStringCellValue();
            int currValueLen = cellValue.length();
            cellValue.append(appendWith);
            cellValue.applyFont(currValueLen, cellValue.length(), style.font);
            cell.setCellValue(cellValue);
            return cell;
        }

        public Worksheet writeAcross(ExcelAddress startCell, List<String> data) throws IOException {
            int startRowIndex = startCell.getRowStartIndex();
            int startColIndex = startCell.getColumnStartIndex();
            int endColIndex = startColIndex + CollectionUtils.size(data);

            XSSFRow row = sheet.getRow(startRowIndex);
            if (row == null) { row = sheet.createRow(startRowIndex); }

            for (int i = startColIndex; i < endColIndex; i++) {
                row.getCell(i, CREATE_NULL_AS_BLANK).setCellValue(IterableUtils.get(data, i - startColIndex));
            }

            save();
            return this;
        }

        public Worksheet writeDown(ExcelAddress startCell, List<String> data) throws IOException {
            int startRowIndex = startCell.getRowStartIndex();
            int endRowIndex = startRowIndex + CollectionUtils.size(data);
            int startColIndex = startCell.getColumnStartIndex();

            for (int i = startRowIndex; i < endRowIndex; i++) {
                XSSFRow row = sheet.getRow(i);
                if (row == null) { row = sheet.createRow(i); }

                row.getCell(startColIndex, CREATE_NULL_AS_BLANK)
                   .setCellValue(IterableUtils.get(data, i - startRowIndex));
            }

            save();
            return this;
        }

        public Style style(ExcelAddress addr) {
            assert addr != null;
            return style(firstCell(addr));
        }

        public Style style(XSSFCell cell) {
            return new Style(copyFont(cell != null ?
                                      cell.getCellStyle().getFont() :
                                      workbook.getFontAt((short) 0)));
        }

        public XSSFRichTextString setValue(ExcelAddress addr, RichTextString value) {
            assert addr != null;
            assert value != null;

            XSSFCell cell = firstCell(addr);
            if (cell != null) {
                cell.setCellValue(value);
                return cell.getRichStringCellValue();
            }

            return null;
        }

        public void setRowValues(ExcelAddress addr, List<String> values) {
            assert addr != null;
            assert CollectionUtils.isNotEmpty(values);

            workbook.setMissingCellPolicy(CREATE_NULL_AS_BLANK);

            int columnIndex = addr.getColumnStartIndex();
            int startRow = addr.getRowStartIndex();
            int endRow = values.size() + startRow;
            for (int i = startRow; i < endRow; i++) {
                if (sheet.getRow(i) == null) { sheet.createRow(i); }
                sheet.getRow(i).getCell(columnIndex).setCellValue(values.get(i - startRow));
            }
        }

        public void setColumnValues(ExcelAddress addr, List<String> values) {
            assert addr != null;
            assert CollectionUtils.isNotEmpty(values);

            workbook.setMissingCellPolicy(CREATE_NULL_AS_BLANK);

            int rowIndex = addr.getRowStartIndex();
            final int[] columnIndex = {addr.getColumnStartIndex()};
            XSSFRow row = sheet.getRow(rowIndex);
            if (row == null) { row = sheet.createRow(rowIndex); }

            XSSFRow targetRow = row;
            values.forEach(value -> targetRow.getCell(columnIndex[0]++).setCellValue(value));
        }

        public XSSFCell setValue(XSSFCell cell, String text, Style style) {
            assert cell != null;
            assert StringUtils.isNotBlank(text);
            assert style != null;

            XSSFRichTextString richText = new XSSFRichTextString(text);
            richText.applyFont(style.font);
            cell.setCellValue(richText);
            return cell;
        }

        /**
         * ensure the parent row of {@code cell} has enough height to display the number of lines as denoted by
         * {@code linesToShow}.  If the row in question already does, then its height will not be modified.
         */
        public XSSFCell setMinHeight(XSSFCell cell, int linesToShow) {
            if (cell != null) {
                float newHeight = linesToShow == 1 ?
                                  21 :
                                  (cell.getCellStyle().getFont().getFontHeightInPoints() + _cellSpacing) * linesToShow;

                XSSFRow row = cell.getRow();
                float actualHeight = row.getHeightInPoints();
                if (actualHeight < newHeight) { row.setHeightInPoints(newHeight); }
            }
            return cell;
        }

        public XSSFCell setHeight(XSSFCell cell, int linesToShow) {
            if (cell != null) {
                float newHeight = (cell.getCellStyle().getFont().getFontHeightInPoints() + _cellSpacing) * linesToShow;
                cell.getRow().setHeightInPoints(newHeight);
            }
            return cell;
        }

        public XSSFCell setHeight(ExcelAddress addr, int linesToShow) {
            assert addr != null;
            assert linesToShow > 0;

            XSSFCell cell = firstCell(addr);
            return setHeight(cell, linesToShow);
        }

        public XSSFCell setHyperlink(XSSFCell cell, String link, String label) {
            return Excel.setHyperlink(cell, link, label);
        }

        public XSSFCell setScreenCaptureStyle(XSSFCell cell, String url) {
            return setHyperlink(cell, url, MSG_SCREENCAPTURE);
        }

        public XSSFCell setWrapText(ExcelAddress addr, boolean onOff) {
            XSSFCell cell = firstCell(addr);
            if (cell != null) { cell.getCellStyle().setWrapText(onOff); }
            return cell;
        }

        public XSSFCell wrapText(ExcelAddress addr) { return setWrapText(addr, true); }

        public XSSFCell unwrapText(ExcelAddress addr) { return setWrapText(addr, false); }

        public File getFile() { return file; }

        /** find the last row in a contiguous column block */
        public int findLastDataRow(ExcelAddress startCellAddr) {
            assert startCellAddr != null;

            int startRow = startCellAddr.getRowStartIndex();
            int startCol = startCellAddr.getColumnStartIndex();
            for (int i = startRow; i < sheet.getLastRowNum() + 1; i++) {
                XSSFRow row = sheet.getRow(i);
                if (row == null) { return i; }
                XSSFCell cell = row.getCell(startCol, RETURN_BLANK_AS_NULL);
                if (cell == null) { return i; }
                if (cell.getCellTypeEnum() == STRING) {
                    if (StringUtils.isBlank(cell.getStringCellValue())) { return i; }
                } else {
                    if (StringUtils.isBlank(cell.getRawValue())) { return i; }
                }
            }

            return sheet.getLastRowNum() + 1;
        }

        /** find the last column in a contiguous row */
        public int findLastDataColumn(ExcelAddress startCellAddr) {
            assert startCellAddr != null;

            int startRow = startCellAddr.getRowStartIndex();
            int startCol = startCellAddr.getColumnStartIndex();
            XSSFRow row = sheet.getRow(startRow);
            if (row == null) { return -1; }
            for (int i = startCol; i < row.getLastCellNum() + 1; i++) {
                if (row.getCell(i) == null || row.getCell(i, RETURN_BLANK_AS_NULL) == null) { return i; }
            }

            return row.getLastCellNum() + 1;
        }

        /**
         * find the next empty row immediately after the specified {@code startCellAddr}.  The scope of a row is defined by
         * the {@code startCellAddr}.
         */
        public int findNextEntirelyEmptyRow(ExcelAddress startCellAddr) {
            assert startCellAddr != null;

            int startRow = startCellAddr.getRowStartIndex();
            int startCol = startCellAddr.getColumnStartIndex();
            int endCol = startCellAddr.getColumnEndIndex();

            for (int i = startRow; i < sheet.getLastRowNum() + 1; i++) {
                XSSFRow row = sheet.getRow(i);
                if (row == null) { return i; }

                boolean entirelyEmpty = true;
                for (int j = startCol; j < endCol; j++) {
                    if (row.getCell(j, RETURN_BLANK_AS_NULL) != null) {
                        entirelyEmpty = false;
                        break;
                    }
                }

                if (entirelyEmpty) { return i; }
            }

            // this means every row in this worksheet has data
            return sheet.getLastRowNum() + 1;
        }

        public XSSFCellStyle getStyle(String styleName) { return commonStyles.get(styleName); }

        public XSSFCellStyle newCellStyleInstance() { return Excel.newCellStyleInstance(sheet); }

        public void clearCells(ExcelAddress addr) {
            // clean up data from previous generation
            int startRowIndex = addr.getRowStartIndex();
            int endRowIndex = addr.getRowEndIndex();
            int startColIndex = addr.getColumnStartIndex();
            int endColIndex = addr.getColumnEndIndex();

            for (int i = startRowIndex; i <= endRowIndex; i++) {
                XSSFRow row = sheet.getRow(i);
                if (row == null) { continue; }

                for (int j = startColIndex; j <= endColIndex; j++) {
                    XSSFCell cell = row.getCell(j, RETURN_BLANK_AS_NULL);
                    if (cell != null) { row.removeCell(cell); }
                }
            }
        }

        public void clearAllContent() {
            int lastRow = sheet.getLastRowNum();
            for (int i = 0; i <= lastRow; i++) {
                XSSFRow row = sheet.getRow(i);
                if (row != null) { sheet.removeRow(row); }
            }
        }

        public void createName(String name, String reference) {
            assert StringUtils.isNotBlank(name);
            assert StringUtils.isNotBlank(reference);

            XSSFName namedRange = workbook.getName(name);
            if (namedRange == null) {
                namedRange = workbook.createName();
                namedRange.setNameName(name);
            }
            namedRange.setRefersToFormula(reference);
        }

        public XSSFFont createFont() { return workbook.createFont(); }

        public XSSFCellStyle newCellStyle() {
            XSSFWorkbook workbook = sheet.getWorkbook();
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(StyleDecorator.newDefaultFont(workbook));
            return style;
        }

        public void setSheetName(String name) {
            XSSFWorkbook workbook = sheet.getWorkbook();
            workbook.setSheetName(workbook.getSheetIndex(sheet), name);
        }

        public void shiftRows(int startRow, int endRow, int shiftBy) {
            if (startRow < 0 || endRow < 0 || shiftBy == 0) { return; }

            // due to https://bz.apache.org/bugzilla/show_bug.cgi?id=57423
            // we currently CANNOT directly shift rows via 1 method call.  POI 3.17 can only
            // shift rows with increment equal or less than the number of rows being shifted.

            // sheet.shiftRows(startRow, endRow, shiftBy);

            int shiftInterval = endRow - startRow + 1;
            int rowsToShift = shiftBy;
            int currentStartRow = startRow;
            int currentEndRow = endRow;

            if (shiftBy > 0) {
                while (rowsToShift > 0) {
                    int shifts = rowsToShift < shiftInterval ? rowsToShift : shiftInterval;
                    rowsToShift -= shiftInterval;

                    sheet.shiftRows(currentStartRow, currentEndRow, shifts);

                    currentStartRow += rowsToShift;
                    currentEndRow += rowsToShift;
                }

                List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
                if (CollectionUtils.isNotEmpty(mergedRegions)) {
                    for (CellRangeAddress merged : mergedRegions) {
                        int initialMergedRow = merged.getFirstRow();
                        if (initialMergedRow >= startRow) {
                            int newMergedRow = initialMergedRow + shiftBy;
                            sheet.addMergedRegion(
                                new CellRangeAddress(newMergedRow, newMergedRow,
                                                     COL_IDX_MERGE_RESULT_START, COL_IDX_PARAMS_END));
                        }
                    }
                }
            }

            if (shiftBy < 0) {
                while (rowsToShift < 0) {
                    int shifts = rowsToShift > shiftInterval ? rowsToShift : shiftInterval;
                    rowsToShift += shiftInterval;

                    sheet.shiftRows(currentStartRow, currentEndRow, shifts);

                    currentStartRow -= rowsToShift;
                    currentEndRow -= rowsToShift;
                }

                List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
                if (CollectionUtils.isNotEmpty(mergedRegions)) {
                    for (CellRangeAddress merged : mergedRegions) {
                        int initialMergedRow = merged.getFirstRow();
                        if (initialMergedRow < startRow) {
                            int newMergedRow = initialMergedRow - shiftBy;
                            sheet.addMergedRegion(
                                new CellRangeAddress(newMergedRow, newMergedRow,
                                                     COL_IDX_MERGE_RESULT_START, COL_IDX_PARAMS_END));
                        }
                    }
                }
            }
        }

        public void save() throws IOException { Excel.save(getFile(), sheet.getWorkbook()); }

        /** set a worksheet as readonly -- mainly so that user won't accidentally modify test result */
        protected void setAsReadOnly() {
            sheet.lockDeleteColumns(true);
            sheet.lockDeleteRows(true);
            sheet.lockInsertColumns(true);
            sheet.lockInsertHyperlinks(true);
            sheet.lockInsertRows(true);
            sheet.enableLocking();
        }

        private void hideOrShow(boolean hide) {
            XSSFWorkbook workbook = getWorkbook();
            workbook.setSheetHidden(workbook.getSheetIndex(sheet), hide);
        }

        private XSSFCellStyle newCellStyle(XSSFCell cell) { return cell.getSheet().getWorkbook().createCellStyle(); }
    }

    public class Style {
        private XSSFFont font;

        public Style(XSSFFont font) { this.font = font; }

        /** @param hexColor HTML color, in the format of #rrggbb */
        public Style setColor(String hexColor) {
            assert StringUtils.length(hexColor) == 7 &&
                   StringUtils.containsOnly(hexColor.substring(1).toUpperCase(), "0123456789ABCDEF");

            byte[] colorBytes = new byte[3];
            for (int i = 0; i < colorBytes.length; i++) {
                colorBytes[i] =
                    new BigInteger(StringUtils.substring(hexColor, 1 + i * 2, 2).toUpperCase(), 16).byteValue();
            }

            font.setColor(new XSSFColor(new java.awt.Color(colorBytes[0], colorBytes[1], colorBytes[2])));
            return this;
        }

        public Style fontName(String name) {
            assert StringUtils.isNotBlank(name);

            font.setFontName(name);
            return this;
        }

        public XSSFFont getFont() { return font; }

        public Style bold() {
            font.setBold(true);
            return this;
        }

        public Style italic() {
            font.setItalic(true);
            return this;
        }

        public Style underline() {
            font.setUnderline((byte) 1);
            return this;
        }

        public Style black() {
            font.setColor(BLACK);
            return this;
        }

        public Style white() {
            font.setColor(WHITE);
            return this;
        }

        public Style red() {
            font.setColor(RED);
            return this;
        }

        public Style orange() {
            font.setColor(ORANGE);
            return this;
        }

        public Style yellow() {
            font.setColor(YELLOW);
            return this;
        }

        public Style green() {
            font.setColor(GREEN);
            return this;
        }

        public Style blue() {
            font.setColor(BLUE);
            return this;
        }

        public Style indigo() {
            font.setColor(INDIGO);
            return this;
        }

        public Style purple() {
            font.setColor(PURPLE);
            return this;
        }

        public Style setSize(int points) {
            assert points > 0;

            font.setFontHeight(points);
            return this;
        }

        public Style plain() {
            font.setItalic(false);
            font.setBold(false);
            font.setUnderline((byte) 0);
            return this;
        }
    }

    public Excel(File file) throws IOException { this(file, false); }

    /**
     * since excel file can't be opened via POI while being opened in Excel (Windows only), it might be helpful to copy
     * {@code file} as duplicate and open the duplicate instead.
     */
    public Excel(File file, boolean dupThenOpen) throws IOException { this(file, dupThenOpen, true); }

    public Excel(File file, boolean dupThenOpen, boolean initCommonStyles) throws IOException {
        assert file != null && file.canRead();
        if (dupThenOpen) { file = duplicateInTemp(file); }

        this.file = file;
        // DO NOT USE - open excel via File object will cause JVM crash during save!
        // try {
        // workbook = new XSSFWorkbook(file);
        // } catch (InvalidFormatException e) {
        //     throw new IOException(e.getMessage(), e);
        // }
        workbook = new XSSFWorkbook(new FileInputStream(file));
        allsheets = gatherWorksheets();
        workbookStyles = gatherCellStyles();

        if (initCommonStyles) { initCommonStyles(); }
    }

    public Worksheet worksheet(String name) { return worksheet(name, false); }

    public Worksheet worksheet(String name, boolean create) {
        assert StringUtils.isNotBlank(name);
        for (XSSFSheet sheet : allsheets) { if (sheet.getSheetName().equals(name)) { return worksheet(sheet); } }
        if (!create) { return null; }

        XSSFSheet newSheet = this.workbook.createSheet(WorkbookUtil.createSafeSheetName(name));
        allsheets.add(newSheet);
        return worksheet(newSheet);
    }

    public Worksheet requireWorksheet(String name, boolean createIfMissing) {
        if (StringUtils.isBlank(name)) { throw new IllegalArgumentException("worksheet name cannot be empty/null"); }

        Worksheet worksheet = worksheet(name, createIfMissing);
        if (worksheet == null || worksheet.getSheet() == null) {
            throw new RuntimeException("Unable to retrieve/create worksheet '" + name + "'; check worksheet name");
        }

        return worksheet;
    }

    public XSSFCellStyle getStyle(String styleName) { return commonStyles.get(styleName); }

    public File getFile() { return file; }

    public XSSFWorkbook getWorkbook() { return workbook; }

    public Set<XSSFCellStyle> getWorkbookStyles() { return workbookStyles; }

    public void setCellSpacing(float _cellSpacing) { this._cellSpacing = _cellSpacing; }

    // same as worksheetsStartWith; to get along with java
    public List<Worksheet> getWorksheetsStartWith(String startsWith) {
        return new ArrayList<>(worksheetsStartWith(startsWith));
    }

    public static String getCellValue(XSSFCell cell) {
        if (cell == null) { return null; }

        XSSFWorkbook workbook = cell.getRow().getSheet().getWorkbook();
        FormulaEvaluator evaluator = workbook != null ? workbook.getCreationHelper().createFormulaEvaluator() : null;

        CellType cellType = cell.getCellTypeEnum();
        switch (cellType) {
            case BLANK:
            case NUMERIC:
            case BOOLEAN:
                return new DataFormatter().formatCellValue(cell, evaluator);
            case FORMULA:
                if (evaluator == null) { return new DataFormatter().formatCellValue(cell); }

                CellValue cellValue = evaluator.evaluate(cell);

                // special handling for error after formula is evaluated
                if (cellValue == null) { return ""; }
                if (cellValue.getCellTypeEnum() == ERROR) { return cellValue.formatAsString(); }

                return new DataFormatter().formatCellValue(cell, evaluator);
            case ERROR:
                return cell.getErrorCellString();
            default:
                return CellTextReader.getText(cell.getStringCellValue());
        }
    }

    public static XSSFCell setHyperlink(XSSFCell cell, String link, String label) {
        if (cell == null) { return cell; }
        if (StringUtils.isBlank(link)) { return cell; }
        if (StringUtils.isBlank(label)) { return cell; }

        cell.setCellValue(label);

        // if the hyperlink is not HTTP-enabled, then we need to determine OS-specific paths
        if (StringUtils.startsWithIgnoreCase(link, "http")) {
            // simply case: link is a http... something
            cell.setCellFormula("HYPERLINK(\"" + link + "\", \"" + label + "\")");
        } else {
            String unixLink;
            String winLink;

            // link not expressed as URL, hence it's likely some sort of path
            int projectHomeIndex = StringUtils.indexOfIgnoreCase(link, "projects");
            if (projectHomeIndex <= 0) {
                // non-standard directory structure.. just make up something. good luck!
                unixLink = "file:" + StringUtils.replace(link, "\\", "/");
                winLink = StringUtils.replace(link, "/", "\\");
            } else {
                unixLink = "file:" + USER_HOME + "/"
                           + StringUtils.replace(StringUtils.substring(link, projectHomeIndex), "\\", "/");
                winLink = "C:\\" + StringUtils.replace(StringUtils.substring(link, projectHomeIndex), "/", "\\");
            }

            // =if(info("system")="mac",hyperlink("/Users/lium/tmp/", "Click here"),hyperlink("C:\temp\","Click there"))
            cell.setCellFormula("HYPERLINK(IF(ISERROR(FIND(\"dos\",INFO(\"system\"))),\"" +
                                unixLink + "\",\"" + winLink + "\"),\"" + label + "\")");
        }

        XSSFSheet sheet = cell.getSheet();
        //sheet.setForceFormulaRecalculation(true);
        cell.setCellStyle(StyleDecorator.generate(sheet.getWorkbook(), LINK));
        return cell;
    }

    public static Excel createExcel(File file, String... worksheets) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        if (ArrayUtils.isNotEmpty(worksheets)) {
            Arrays.stream(worksheets).forEach(worksheet -> {
                XSSFSheet sheet = workbook.createSheet(worksheet);
                sheet.createRow(0).createCell(0).setCellValue("");
            });
        }

        try { save(file, workbook); } finally { workbook.close(); }

        return new Excel(file);
    }

    public void save() throws IOException { save(file, workbook); }

    public static void save(File excelFile, XSSFWorkbook excelWorkbook) throws IOException {
        OutputStream out = null;
        try {
            out = FileUtils.openOutputStream(excelFile);
            excelWorkbook.write(out);
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    public static Excel asXlsxExcel(String file, boolean dupThenOpen) throws IOException {
        if (StringUtils.isBlank(file) || !FileUtil.isFileReadable(file, MIN_EXCEL_FILE_SIZE)) { return null; }

        Workbook workbook = null;
        try {
            File excelFile = new File(file);
            Excel excel = new Excel(new File(file), dupThenOpen);
            if (excel.workbook.getSpreadsheetVersion() == EXCEL2007) {
                return excel;
            }

            workbook = WorkbookFactory.create(excelFile);
            if (workbook != null && workbook.getSpreadsheetVersion() == EXCEL2007) {
                return new Excel(new File(file), dupThenOpen);
            }

            // not excel-2007 or above
            ConsoleUtils.error("\n\n\n" +
                               StringUtils.repeat("!", 80) + "\n" +
                               "File (" + excelFile + ")\n" +
                               "is either unreadable, not of version Excel 2007 or above, or is currently open.\n" +
                               "If this file is currently open, please close it before retrying again.\n" +
                               StringUtils.repeat("!", 80) + "\n" +
                               "\n\n");
            return null;
        } catch (InvalidFormatException | OLE2NotOfficeXmlFileException e) {
            ConsoleUtils.error("Unable to open workbook (" + file + "): " + e.getMessage());
            return null;
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Unable to close workbook (" + file + "): " + e.getMessage());
                }
            }
        }
    }

    public static boolean isXlsxVersion(String file) {
        if (StringUtils.isBlank(file)) { return false; }

        if (!FileUtil.isFileReadable(file, MIN_EXCEL_FILE_SIZE)) { return false; }

        File excelFile = new File(file);
        Workbook workbook = null;

        try {
            if (DEF_OPEN_EXCEL_AS_DUP) { excelFile = duplicateInTemp(excelFile); }
            if (excelFile == null) { return false; }

            workbook = WorkbookFactory.create(excelFile);
            return workbook.getSpreadsheetVersion() == EXCEL2007;
        } catch (IOException | InvalidFormatException e) {
            ConsoleUtils.error("Unable to open workbook (" + file + "): " + e.getMessage());
            return false;
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    ConsoleUtils.error("Unable to close workbook (" + file + "): " + e.getMessage());
                }
            }

            if (DEF_OPEN_EXCEL_AS_DUP && excelFile != null) { FileUtils.deleteQuietly(excelFile.getParentFile()); }
        }
    }

    public static XSSFCellStyle newCellStyleInstance(XSSFSheet sheet) {
        XSSFWorkbook workbook = sheet.getWorkbook();
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(StyleDecorator.newDefaultFont(workbook));
        return style;
    }

    public static Comment createComment(XSSFCell cell, String comment, String author) {
        XSSFSheet sheet = cell.getSheet();
        XSSFWorkbook workbook = sheet.getWorkbook();
        CreationHelper factory = workbook.getCreationHelper();
        XSSFRow row = cell.getRow();

        Drawing drawing = sheet.createDrawingPatriarch();

        // When the comment box is visible, have it show in a 1x3 space
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 1);
        anchor.setRow1(row.getRowNum());
        anchor.setRow2(row.getRowNum() + 3);

        // Create the comment and set the text+author
        Comment commentCell = drawing.createCellComment(anchor);
        RichTextString str = factory.createRichTextString(comment);
        commentCell.setString(str);
        commentCell.setAuthor(author);

        return commentCell;
    }

    public static Excel newNexialExcel(File file) throws IOException {
        createWorkbook(file);
        return new Excel(file, false, true);
    }

    public static Excel newExcel(File file) throws IOException {
        createWorkbook(file);
        return new Excel(file, false, false);
    }

    /**
     * assert that the data found in {@code addrs} of {@code sheet} is the same (as string) as that of {@code expectedText}.
     */
    public static boolean isRowTextFound(Worksheet sheet, List<String> expectRowText, ExcelAddress... addrs) {
        if (CollectionUtils.isEmpty(expectRowText)) { return false; }
        if (sheet == null) { return false; }
        if (ArrayUtils.isEmpty(addrs)) { return false; }

        // for as many addresses given, we are merging them into 1 contiguous list
        List<XSSFCell> cells = new ArrayList<>();
        for (ExcelAddress addr : addrs) {
            List<List<XSSFCell>> range = sheet.cells(addr);
            if (CollectionUtils.isEmpty(range)) { continue; }
            cells.addAll(range.get(0));
        }
        if (CollectionUtils.isEmpty(cells)) { return false; }

        // now compare the gathered cell values against the expected values
        List<String> headers = new ArrayList<>();
        cells.forEach(cell -> headers.add(cell.getStringCellValue()));
        return CollectionUtils.isEqualCollection(expectRowText, headers);
    }

    public static void openExcel(File testScript) {
        try {
            String file = testScript.getAbsolutePath();
            if (IS_OS_MAC) { ProcessInvoker.invoke("open", Collections.singletonList(file), null); }
            if (IS_OS_WINDOWS) {
                String spreadsheetProgram = System.getProperty(SPREADSHEET_PROGRAM, DEF_SPREADSHEET);
                ProcessInvoker.invoke(WIN32_CMD, Arrays.asList("/C", "start", "\"\"", spreadsheetProgram, file), null);
            }
        } catch (IOException | InterruptedException e) {
            ConsoleUtils.error("ERROR!!! Can't open " + testScript + ": " + e.getMessage());
        }
    }

    public static String resolveWpsExecutablePath() {
        if (IS_OS_WINDOWS) {
            try {
                ProcessOutcome outcome = ProcessInvoker.invoke(WIN32_CMD,
                                                               Arrays.asList("/C",
                                                                             "%windir%\\System32\\where.exe",
                                                                             "/R",
                                                                             "\"%LOCALAPPDATA%\\Kingsoft\\WPS Office\"",
                                                                             "et.exe"), null);
                int exitStatus = outcome.getExitStatus();
                if (exitStatus != 0) {
                    ConsoleUtils.error("ERROR!!! Unable to determine WPS spreadsheet program location: " + exitStatus);
                    return null;
                }

                String[] output = StringUtils.split(outcome.getStdout(), "\r\n");
                if (ArrayUtils.isEmpty(output)) {
                    ConsoleUtils.error("ERROR!!! Unable to determine WPS spreadsheet program location: NO OUTPUT");
                    return null;
                }

                Arrays.sort(output);
                ArrayUtils.reverse(output);
                return output[0];
            } catch (IOException | InterruptedException e) {
                ConsoleUtils.error("ERROR!!! Unable to determine WPS spreadsheet program location: " + e.getMessage());
                return null;
            }
        }

        ConsoleUtils.error("WPS is not supported on " + OS_NAME);
        return null;
    }

    /**
     * if {@code startsWith} is an empty string, then all worksheets will be returned
     */
    List<Worksheet> worksheetsStartWith(String startsWith) {
        return allsheets.stream()
                        .filter(sheet -> sheet.getSheetName().startsWith(startsWith))
                        .map(Worksheet::new)
                        .collect(Collectors.toList());
    }

    Worksheet worksheet(XSSFSheet sheet) { return new Worksheet(sheet); }

    int getWorksheetCount() {return workbook.getNumberOfSheets();}

    XSSFFont copyFont(XSSFFont cellFont) {
        assert cellFont != null;

        XSSFFont newFont = workbook.createFont();
        newFont.setBold(cellFont.getBold());
        newFont.setBold(cellFont.getBold());
        newFont.setColor(cellFont.getColor());
        newFont.setFontHeight(cellFont.getFontHeight());
        newFont.setFontName(cellFont.getFontName());
        newFont.setItalic(cellFont.getItalic());
        newFont.setStrikeout(cellFont.getStrikeout());
        newFont.setTypeOffset(cellFont.getTypeOffset());
        newFont.setUnderline(cellFont.getUnderline());
        return newFont;
    }

    private static void createWorkbook(File file) throws IOException {
        Workbook wb = new XSSFWorkbook();
        try (FileOutputStream fileOut = FileUtils.openOutputStream(file)) { wb.write(fileOut); }
    }

    private static File duplicateInTemp(File file) throws IOException {
        if (file == null || !file.exists() || !file.canRead()) { return null; }

        File tmpDir = SystemUtils.getJavaIoTmpDir();
        // use random alphanum to avoid collision in parallel processing
        File tmpFile = new File((tmpDir.getAbsolutePath() + separator +
                                 RandomStringUtils.randomAlphabetic(5)) + separator +
                                file.getName());

        if (StringUtils.equals(file.getAbsolutePath(), tmpFile.getAbsolutePath())) { return file; }

        FileUtils.copyFile(file, tmpFile);
        return tmpFile;
    }

    private void initCommonStyles() {
        commonStyles = new HashMap<>();
        //commonStyles.put(STYLE_JENKINS_REF_LABEL, StyleDecorator.generate(workbook, JENKINS_REF_LABEL));
        //commonStyles.put(STYLE_JENKINS_REF_LINK, StyleDecorator.generate(workbook, JENKINS_REF_LINK));
        //commonStyles.put(STYLE_JENKINS_REF_PARAM, StyleDecorator.generate(workbook, JENKINS_REF_PARAM));
        commonStyles.put(STYLE_TEST_CASE, StyleDecorator.generate(workbook, TESTCASE));
        commonStyles.put(STYLE_DESCRIPTION, StyleDecorator.generate(workbook, DESCRIPTION));
        commonStyles.put(STYLE_SECTION_DESCRIPTION, StyleDecorator.generate(workbook, SECTION_DESCRIPTION));
        commonStyles.put(STYLE_REPEAT_UNTIL_DESCRIPTION, StyleDecorator.generate(workbook, REPEAT_UNTIL_DESCRIPTION));
        commonStyles.put(STYLE_TARGET, StyleDecorator.generate(workbook, TARGET));
        commonStyles.put(STYLE_MESSAGE, StyleDecorator.generate(workbook, MSG));
        commonStyles.put(STYLE_COMMAND, StyleDecorator.generate(workbook, COMMAND));
        commonStyles.put(STYLE_PARAM, StyleDecorator.generate(workbook, PARAM));
        commonStyles.put(STYLE_TAINTED_PARAM, StyleDecorator.generate(workbook, TAINTED_PARAM));
        commonStyles.put(STYLE_SCREENSHOT, StyleDecorator.generate(workbook, SCREENSHOT));
        // commonStyles.put(STYLE_LINK, StyleDecorator.generate(workbook, LINK));
        commonStyles.put(STYLE_ELAPSED_MS, StyleDecorator.generate(workbook, ELAPSED_MS));
        commonStyles.put(STYLE_ELAPSED_MS_BAD_SLA, StyleDecorator.generate(workbook, ELAPSED_MS_BAD_SLA));
        commonStyles.put(STYLE_SUCCESS_RESULT, StyleDecorator.generate(workbook, SUCCESS));
        commonStyles.put(STYLE_FAILED_RESULT, StyleDecorator.generate(workbook, FAILED));
        commonStyles.put(STYLE_SKIPPED_RESULT, StyleDecorator.generate(workbook, SKIPPED));
    }

    private List<XSSFSheet> gatherWorksheets() {
        int sheetCount = workbook.getNumberOfSheets();
        List<XSSFSheet> sheets = new ArrayList<>();
        for (int i = 0; i < sheetCount; i++) { sheets.add(workbook.getSheetAt(i)); }
        return sheets;
    }

    private Set<XSSFCellStyle> gatherCellStyles() {
        int styleCount = workbook.getNumCellStyles();
        Set<XSSFCellStyle> styles = new HashSet<>();
        for (int i = 0; i < styleCount; i++) { styles.add(workbook.getStylesSource().getStyleAt(i)); }
        return styles;
    }

}
