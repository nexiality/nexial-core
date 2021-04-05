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

package org.nexial.core.plugins.io;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.db.DaoUtils;
import org.nexial.core.utils.ConsoleUtils;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.lang.System.lineSeparator;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Project.SCRIPT_FILE_EXT;
import static org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE;

public class ExcelHelper {
    private static final DateFormat DEF_EXCEL_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
    private final ExecutionContext context;

    public ExcelHelper(ExecutionContext context) { this.context = context; }

    public StepResult saveCsvToFile(File excelFile, String worksheet, String csvFile) {
        String excel = excelFile.getAbsolutePath();
        boolean newerFormat = StringUtils.endsWith(excel, SCRIPT_FILE_EXT);
        try {
            StringBuilder csv = newerFormat ? xlsx2csv(excelFile, worksheet) : xls2csv(excelFile, worksheet);
            return saveCSVContentToFile(csvFile, csv);
        } catch (IOException e) {
            return StepResult.fail("Unable to read excel file '" + excel + "': " + e.getMessage());
        }
    }

    public static void csv2xlsx(String file, String sheet, String startCell, List<List<String>> rowsAndColumns)
        throws IOException {
        // either write access or write down would work.
        if (StringUtils.isBlank(startCell)) { startCell = "A1"; }

        File f = new File(file);
        Excel excel = FileUtil.isFileReadable(file, MIN_EXCEL_FILE_SIZE) ? new Excel(f) : Excel.newExcel(f);
        Worksheet worksheet = excel.worksheet(sheet, true);
        worksheet.writeAcross(new ExcelAddress(startCell), rowsAndColumns);
    }

    // currently not used; we might remove it after some time...
    // excel conversion is currently performed via external API (Easier and more reliable)
    public static void xlx2xlsx(File xls, File xlsx) throws IOException {
        if (xls == null || xlsx == null) { return; }

        Map<Short, XSSFCellStyle> cellStyleMap = new HashMap<>();
        Workbook fromWorkbook = null;
        Workbook toWorkbook = new XSSFWorkbook();
        OutputStream out = null;

        InputStream in = null;
        try {
            in = new FileInputStream(xls);
            fromWorkbook = new HSSFWorkbook(in);
            int sheetCnt = fromWorkbook.getNumberOfSheets();

            for (int i = 0; i < sheetCnt; i++) {
                Sheet fromSheet = fromWorkbook.getSheetAt(i);
                Sheet toSheet = toWorkbook.createSheet(fromSheet.getSheetName());
                Iterator<Row> iter = fromSheet.rowIterator();
                while (iter.hasNext()) {
                    Row fromRow = iter.next();
                    Row toRow = toSheet.createRow(fromRow.getRowNum());
                    copyRowProperties(fromRow, toRow, cellStyleMap);
                }
            }

            out = new BufferedOutputStream(new FileOutputStream(xlsx));
            toWorkbook.write(out);
        } finally {
            if (in != null) { in.close(); }
            toWorkbook.close();
            if (fromWorkbook != null) { fromWorkbook.close(); }
            if (out != null) { out.close(); }
        }
    }

    private static void copyRowProperties(Row from, Row to, Map<Short, XSSFCellStyle> styles) {
        to.setRowNum(from.getRowNum());
        to.setHeight(from.getHeight());
        to.setHeightInPoints(from.getHeightInPoints());
        to.setZeroHeight(from.getZeroHeight());

        Sheet fromSheet = from.getSheet();
        Sheet toSheet = to.getSheet();
        Iterator<Cell> iter = from.cellIterator();
        while (iter.hasNext()) {
            Cell fromCell = iter.next();
            int fromCellIndex = fromCell.getColumnIndex();

            Cell toCell = to.createCell(fromCellIndex, fromCell.getCellTypeEnum());
            int toCellIndex = toCell.getColumnIndex();

            toSheet.setColumnWidth(toCellIndex, fromSheet.getColumnWidth(fromCellIndex));
            copyCellProperties(fromCell, toCell, styles);
        }
    }

    private static void copyCellProperties(Cell from, Cell to, Map<Short, XSSFCellStyle> styles) {
        copyCellValue(from, to);

        HSSFCellStyle fromStyle = (HSSFCellStyle) from.getCellStyle();
        XSSFCellStyle styleOut;
        if (styles.get(fromStyle.getIndex()) != null) {
            styleOut = styles.get(fromStyle.getIndex());
        } else {
            Workbook wbOut = to.getSheet().getWorkbook();
            styleOut = createStyle(from, wbOut);
            styles.put(fromStyle.getIndex(), styleOut);
        }
        to.setCellStyle(styleOut);
        to.setCellComment(from.getCellComment());
    }

    @Nonnull
    private static XSSFCellStyle createStyle(Cell from, Workbook targetWorkbook) {
        HSSFCellStyle fromStyle = (HSSFCellStyle) from.getCellStyle();

        XSSFCellStyle toStyle = (XSSFCellStyle) targetWorkbook.createCellStyle();
        toStyle.setAlignment(fromStyle.getAlignmentEnum());
        toStyle.setDataFormat(targetWorkbook.createDataFormat().getFormat(fromStyle.getDataFormatString()));

        HSSFColor foregroundColor = fromStyle.getFillForegroundColorColor();
        if (foregroundColor != null) {
            toStyle.setFillForegroundColor(toXSSFColor(foregroundColor));
            toStyle.setFillPattern(fromStyle.getFillPatternEnum());
        }

        toStyle.setFillPattern(fromStyle.getFillPatternEnum());
        toStyle.setBorderBottom(fromStyle.getBorderBottomEnum());
        toStyle.setBorderLeft(fromStyle.getBorderLeftEnum());
        toStyle.setBorderRight(fromStyle.getBorderRightEnum());
        toStyle.setBorderTop(fromStyle.getBorderTopEnum());

        HSSFPalette palette = ((HSSFWorkbook) from.getSheet().getWorkbook()).getCustomPalette();
        HSSFColor bottom = palette.getColor(fromStyle.getBottomBorderColor());
        if (bottom != null) { toStyle.setBottomBorderColor(toXSSFColor(bottom)); }

        HSSFColor top = palette.getColor(fromStyle.getTopBorderColor());
        if (top != null) { toStyle.setTopBorderColor(toXSSFColor(top)); }

        HSSFColor left = palette.getColor(fromStyle.getLeftBorderColor());
        if (left != null) { toStyle.setLeftBorderColor(toXSSFColor(left)); }

        HSSFColor right = palette.getColor(fromStyle.getRightBorderColor());
        if (right != null) { toStyle.setRightBorderColor(toXSSFColor(right)); }

        toStyle.setVerticalAlignment(fromStyle.getVerticalAlignmentEnum());
        toStyle.setHidden(fromStyle.getHidden());
        toStyle.setIndention(fromStyle.getIndention());
        toStyle.setLocked(fromStyle.getLocked());
        toStyle.setRotation(fromStyle.getRotation());
        toStyle.setShrinkToFit(fromStyle.getShrinkToFit());
        toStyle.setVerticalAlignment(fromStyle.getVerticalAlignmentEnum());
        toStyle.setWrapText(fromStyle.getWrapText());
        return toStyle;
    }

    @Nonnull
    private static XSSFColor toXSSFColor(HSSFColor color) {
        short[] values = color.getTriplet();
        return new XSSFColor(new Color(values[0], values[1], values[2]));
    }

    private static void copyCellValue(Cell from, Cell to) {
        CellType cellTypeEnum = from.getCellTypeEnum();
        switch (cellTypeEnum) {
            case BLANK:
                break;
            case BOOLEAN:
                to.setCellValue(from.getBooleanCellValue());
                break;
            case ERROR:
                to.setCellValue(from.getErrorCellValue());
                break;
            case FORMULA:
                to.setCellFormula(from.getCellFormula());
                break;
            case NUMERIC:
                to.setCellValue(from.getNumericCellValue());
                break;
            case STRING:
                to.setCellValue(from.getStringCellValue());
                break;
        }
    }

    protected StringBuilder xlsx2csv(File excelFile, String worksheet) throws IOException {
        XSSFWorkbook workBook = new XSSFWorkbook(new FileInputStream(excelFile));
        XSSFSheet excelSheet = workBook.getSheet(worksheet);
        Iterator rowIterator = excelSheet.rowIterator();
        StringBuilder csv = new StringBuilder();
        String delim = context.getTextDelim();

        while (rowIterator.hasNext()) {
            XSSFRow row = (XSSFRow) rowIterator.next();

            String oneRow = "";
            String value;
            for (int i = 0; i < row.getLastCellNum(); i++) {
                value = returnCellValue(row.getCell(i));
                if (StringUtils.isEmpty(value)) { value = ""; }
                oneRow += value + delim;
            }

            oneRow = StringUtils.trim(StringUtils.removeEnd(oneRow, delim));
            if (!oneRow.isEmpty()) { csv.append(oneRow).append(lineSeparator()); }
        }

        return csv;
    }

    protected StringBuilder xls2csv(File excelFile, String worksheet) throws IOException {
        HSSFWorkbook workBook = new HSSFWorkbook(new POIFSFileSystem(new FileInputStream(excelFile)));
        HSSFSheet excelSheet = workBook.getSheet(worksheet);
        Iterator rowIterator = excelSheet.rowIterator();
        StringBuilder csv = new StringBuilder();
        String delim = context.getTextDelim();

        while (rowIterator.hasNext()) {
            HSSFRow row = (HSSFRow) rowIterator.next();

            String oneRow = "";
            String value;
            for (int i = 0; i < row.getLastCellNum(); i++) {
                HSSFCell cell = row.getCell(i);
                value = returnCellValue(cell);
                if (StringUtils.isEmpty(value)) { value = ""; }
                oneRow += value + delim;
            }

            oneRow = StringUtils.trim(StringUtils.removeEnd(oneRow, delim));
            if (!oneRow.isEmpty()) { csv.append(oneRow).append(lineSeparator()); }
        }

        return csv;
    }

    protected StepResult saveCSVContentToFile(String file, StringBuilder csv) {
        String content = csv.toString();
        if (context.isVerbose()) {
            context.getLogger().log(context,
                                    "writing " + StringUtils.countMatches(content, "\n") + " row(s) to '" + file + "'");
        }

        File target = new File(file);
        try {
            FileUtils.forceMkdir(target.getParentFile());
            FileUtils.write(target, content, DEF_CHARSET);
            return StepResult.success("File converted to CSV");
        } catch (IOException e) {
            String error = "Error writing CSV content to '" + file + "': " + e.getMessage();
            ConsoleUtils.log(error);
            FileUtils.deleteQuietly(target);
            return StepResult.fail(error);
        }
    }

    protected String returnCellValue(HSSFCell cell) { return returnCellValue(((Cell) cell)); }

    protected String returnCellValue(XSSFCell cell) { return returnCellValue(((Cell) cell)); }

    protected String returnCellValue(Cell cell) {
        return DaoUtils.csvFriendly(Excel.getCellValue(cell, false), context.getTextDelim(), true);
        // try {
        // String value = Excel.getCellValue(cell, false);
        //
        // switch (cell.getCellTypeEnum()) {
        //     case STRING:
        //     case BOOLEAN:
        //         value = cell.getRichStringCellValue().toString();
        //         break;
        //     case NUMERIC:
        //         value = formattedCellToString(cell);
        //         break;
        //     case FORMULA:
        //         CellType resultType = cell.getCachedFormulaResultTypeEnum();
        //         if (resultType == STRING) {
        //             value = cell.getRichStringCellValue().toString();
        //         } else if (resultType == NUMERIC) {
        //             value = formattedCellToString(cell);
        //         } else {
        //             value = cell.getStringCellValue();
        //         }
        //
        //         break;
        //     default:
        //         value = cell.getStringCellValue();
        // }

        // return DaoUtils.csvFriendly(value, context.getTextDelim(), true);
        // } catch (Exception e) {
        //     return null;
        // }
    }

    // protected String formattedCellToString(Cell cell) {
    //     if (HSSFDateUtil.isCellDateFormatted(cell)) {
    //         return DEF_EXCEL_DATE_FORMAT.format(cell.getDateCellValue());
    //     } else {
    //         return String.valueOf(cell.getNumericCellValue());
    //     }
    // }
}
