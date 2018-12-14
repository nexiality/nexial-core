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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StrTokenizer;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.base.BaseCommand;

import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import static org.apache.poi.poifs.filesystem.FileMagic.OLE2;
import static org.apache.poi.poifs.filesystem.FileMagic.OOXML;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.excel.Excel.clearExcelPassword;
import static org.nexial.core.excel.Excel.deriveFileFormat;
import static org.nexial.core.utils.CheckUtils.*;

public class ExcelCommand extends BaseCommand {
    @Override
    public String getTarget() { return "excel"; }

    public StepResult clear(String file, String worksheet, String range) throws IOException {
        Excel excel = deriveExcel(file);

        requires(StringUtils.isNotBlank(range) && StringUtils.contains(range, ":"), "invalid Excel range", range);
        ExcelAddress addr = new ExcelAddress(range);

        excel.requireWorksheet(worksheet, true).clearCells(addr);
        excel.save();

        return StepResult.success("Data at " + range + " cleared for " + file + "#" + worksheet);
    }

    public StepResult saveRange(String var, String file, String worksheet, String range) throws IOException {
        requiresValidVariableName(var);

        List<List<XSSFCell>> rows = fetchRows(file, worksheet, range);
        Map<String, String> data = new LinkedHashMap<>();
        for (List<XSSFCell> row : rows) {
            for (XSSFCell cell : row) { data.put(cell.getReference(), Excel.getCellValue(cell)); }
        }

        if (MapUtils.isEmpty(data)) {
            context.removeData(var);
            return StepResult.success("No data found");
        }

        context.setData(var, data);
        return StepResult.success(data.size() + " cells read and stored to '" + var + "'");
    }

    public StepResult saveData(String var, String file, String worksheet, String range) throws IOException {
        requiresValidVariableName(var);

        List<List<XSSFCell>> rows = fetchRows(file, worksheet, range);
        List<List<String>> data = new ArrayList<>();
        for (List<XSSFCell> row : rows) {
            List<String> rowData = new ArrayList<>();
            for (XSSFCell cell : row) { rowData.add(Excel.getCellValue(cell)); }
            data.add(rowData);
        }

        if (CollectionUtils.isEmpty(data)) {
            context.removeData(var);
            return StepResult.success("No data found");
        }

        context.setData(var, data.size() == 1 ? data.get(0) : data);
        return StepResult.success(data.size() + " cells read and stored to '" + var + "'");
    }

    public StepResult setPassword(String file, String password) {
        requiresNotBlank(password, "password can't be blank.");
        File excelFile = deriveReadableFile(file);
        FileMagic fileFormat = deriveFileFormat(excelFile);

        if (fileFormat == OOXML) {
            Excel.setExcelPassword(excelFile, password);
            return StepResult.success("Password set to " + file);
        }

        if (fileFormat == OLE2) { return StepResult.fail("A password is already set to " + file); }

        return StepResult.fail("Unable to set password: wrong file format " + fileFormat + " on " + file);
    }

    public StepResult clearPassword(String file, String password) {
        requiresNotBlank(password, "password can't be blank.");
        File excelFile = deriveReadableFile(file);
        if (deriveFileFormat(excelFile) == OLE2 && clearExcelPassword(excelFile, password)) {
            return StepResult.success("Password cleared for " + file);
        }
        return StepResult.fail("Incorrect or no password was set to " + file);
    }

    public StepResult assertPassword(String file) {
        File excelFile = deriveReadableFile(file);
        if (Excel.isPasswordSet(excelFile)) { return StepResult.success("Password set to " + file); }
        return StepResult.fail("Password NOT set to " + file);
    }

    public StepResult writeVar(String var, String file, String worksheet, String startCell) throws IOException {
        requiresValidVariableName(var);
        requiresNotBlank(startCell, "invalid cell address", startCell);

        Excel excel = deriveExcel(file);
        XSSFSheet sheet = excel.requireWorksheet(worksheet, true).getSheet();
        addData(sheet, new ExcelAddress(startCell), to2dStringList(var));
        excel.save();

        return StepResult.success("Data saved to " + file + "#" + worksheet);
    }

    public StepResult write(String file, String worksheet, String startCell, String data) throws IOException {
        requiresNotBlank(startCell, "invalid cell address", startCell);
        requiresNotNull(data, "Invalid data to write", data);

        String delim = context.getTextDelim();
        List<List<String>> data2d = TextUtils.to2dList(data, "\n", delim);
        // need to remove the `\,` with just `,` before writing to Excel
        for (List<String> row : data2d) {
            for (int i = 0; i < row.size(); i++) {
                row.set(i, StringUtils.replace(row.get(i), "\\" + delim, delim));
            }
        }

        Excel excel = deriveExcel(file);
        XSSFSheet sheet = excel.requireWorksheet(worksheet, true).getSheet();
        addData(sheet, new ExcelAddress(startCell), data2d);
        excel.save();

        return StepResult.success("Data saved to " + file + "#" + worksheet);
    }

    public StepResult writeAcross(String file, String worksheet, String startCell, String array) throws IOException {
        requiresNotBlank(startCell, "invalid cell address", startCell);
        requiresNotBlank(worksheet, "invalid worksheet name", worksheet);
        requiresNotBlank(array, "Invalid array to write", array);

        List<List<String>> rows = new ArrayList<>();
        rows.add(TextUtils.toList(array, context.getTextDelim(), false));
        deriveExcel(file).requireWorksheet(worksheet, true).writeAcross(new ExcelAddress(startCell), rows);

        return StepResult.success("Data (" + array + ") saved to " + file + "#" + worksheet);
    }

    public StepResult writeDown(String file, String worksheet, String startCell, String array) throws IOException {
        requiresNotBlank(startCell, "invalid cell address", startCell);
        requiresNotBlank(worksheet, "invalid worksheet name", worksheet);
        requiresNotBlank(array, "Invalid array to write", array);

        List<List<String>> columns = new ArrayList<>();
        columns.add(TextUtils.toList(array, context.getTextDelim(), false));
        deriveExcel(file).requireWorksheet(worksheet, true).writeDown(new ExcelAddress(startCell), columns);

        return StepResult.success("Data (" + array + ") saved to " + file + "#" + worksheet);
    }

    public StepResult csv(String file, String worksheet, String range, String output) {
        // requiresReadableFile(file);
        requiresNotBlank(file, "Invalid file", file);
        requiresNotBlank(worksheet, "Invalid worksheet", worksheet);
        requiresNotBlank(range, "Invalid range", range);
        requiresNotBlank(output, "Invalid CSV output", output);

        String[] ranges = StringUtils.split(range, context.getTextDelim());
        FileUtils.deleteQuietly(new File(output));
        Arrays.stream(ranges).forEach(r -> context.replaceTokens("[EXCEL(" + file + ") => " +
                                                                 " read(" + worksheet + "," + r + ")" +
                                                                 " csv" +
                                                                 " save(" + output + ",true)" +
                                                                 "]"));

        return StepResult.success("Excel content from " + worksheet + "," + range + " saved to " + output);
    }

    public StepResult columnarCsv(String file, String worksheet, String ranges, String output) throws IOException {
        requiresReadableFile(file);
        requiresNotBlank(worksheet, "Invalid worksheet", worksheet);
        requiresNotBlank(ranges, "Invalid range", ranges);
        requiresNotBlank(output, "Invalid CSV output", output);

        File outputFile = new File(output);
        FileUtils.deleteQuietly(outputFile);

        Excel excel = new Excel(new File(file), false, false);
        Worksheet ws = excel.requireWorksheet(worksheet, false);

        String delim = context.getTextDelim();

        List<List<String>> data = new ArrayList<>();
        final Integer[] maxColumns = {0};

        String[] cellRanges = StringUtils.split(ranges, delim);
        Arrays.stream(cellRanges).forEach(r -> {
            List<List<String>> rows = ws.readRange(new ExcelAddress(r));
            if (CollectionUtils.isNotEmpty(rows)) {
                // ensure proper allocation
                int columnCount = rows.get(0).size();
                while (data.size() < rows.size()) { data.add(new ArrayList<>(columnCount)); }

                for (int i = 0; i < rows.size(); i++) {
                    List<String> newRow = data.get(i);
                    while (newRow.size() < maxColumns[0]) { newRow.add(""); }
                    // add all cells to the end of `newRow`
                    newRow.addAll(rows.get(i));
                }

                maxColumns[0] += columnCount;
            }
        });

        String recordDelim = "\r\n";
        String csvContent = StringUtils.removeEnd(TextUtils.toCsvContent(data, delim, recordDelim), recordDelim);

        CsvParserSettings settings = CsvCommand.newCsvParserSettings(delim, recordDelim, false, 0);
        settings.setQuoteDetectionEnabled(true);
        settings.getFormat().setQuote('"');
        settings.setKeepQuotes(true);

        CsvParser parser = new CsvParser(settings);
        List<Record> value = parser.parseAllRecords(new StringReader(csvContent));

        csvContent = StringUtils.removeEnd(value.stream()
                                                .map(row -> TextUtils.toCsvLine(row.getValues(), delim, recordDelim))
                                                .collect(Collectors.joining()),
                                           recordDelim);

        outputFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(outputFile, csvContent, DEF_FILE_ENCODING);

        return StepResult.success("Excel content from " + worksheet + "," + ranges + " saved (columnar) to " + output);
    }

    public StepResult json(String file, String worksheet, String range, String header, String output) {
        requiresReadableFile(file);
        requiresNotBlank(worksheet, "Invalid worksheet", worksheet);
        requiresNotBlank(range, "Invalid range", range);
        requiresNotBlank(output, "Invalid CSV output", output);

        context.replaceTokens("[EXCEL(" + file + ") => " +
                              " read(" + worksheet + "," + range + ")" +
                              " json(" + BooleanUtils.toBoolean(header) + ")" +
                              " save(" + output + ")" +
                              "]");
        return StepResult.success("Excel content from " + worksheet + "," + range + " saved to " + output);
    }

    protected List<List<XSSFCell>> fetchRows(String file, String worksheet, String range) throws IOException {
        Excel excel = deriveExcel(file);
        Worksheet sheet = excel.worksheet(worksheet);
        requires(sheet != null && sheet.getSheet() != null, "invalid worksheet", worksheet);

        requires(StringUtils.isNotBlank(range), "invalid cell range", range);
        ExcelAddress addr = new ExcelAddress(range);
        return sheet.cells(addr);
    }

    protected Excel deriveExcel(String file) throws IOException { return new Excel(deriveReadableFile(file)); }

    protected static File deriveReadableFile(String file) {
        requiresReadableFile(file);
        return new File(file);
    }

    protected void addData(XSSFSheet sheet, ExcelAddress addr, List<List<String>> dataRows) {
        int startRowIndex = addr.getRowStartIndex();
        int endRowIndex = startRowIndex + dataRows.size();
        int startColIndex = addr.getColumnStartIndex();

        for (int i = startRowIndex; i < endRowIndex; i++) {
            XSSFRow row = sheet.getRow(i);
            if (row == null) { row = sheet.createRow(i); }

            List<String> data = dataRows.get(i - startRowIndex);

            int endColIndex = startColIndex + data.size();
            for (int j = startColIndex; j < endColIndex; j++) {
                XSSFCell cell = row.getCell(j, CREATE_NULL_AS_BLANK);
                cell.setCellValue(data.get(j - startColIndex));
            }
        }
    }

    protected List<List<String>> to2dStringList(String var) {
        List<List<String>> dataRows = new ArrayList<>();

        Object dataObject = context.getObjectData(var);
        Class<?> clazz = dataObject.getClass();

        if (clazz.isArray()) {
            int arraySize = ArrayUtils.getLength(dataObject);
            for (int i = 0; i < arraySize; i++) { dataRows.add(toStringList(Array.get(dataObject, i))); }
            return dataRows;
        }

        if (List.class.isAssignableFrom(clazz)) {
            List list = (List) dataObject;
            if (CollectionUtils.isEmpty(list)) { return dataRows; }

            Class listType = list.get(0).getClass();
            if (Map.class.isAssignableFrom(listType)) {
                // special treatment for List of Map; this is probably SQL result via db plugin
                Map firstItem = (Map) list.get(0);
                String[] headers = (String[]) firstItem.keySet().toArray(new String[firstItem.size()]);
                dataRows.add(Arrays.asList(headers));

                for (Object item : list) {
                    Map map = (Map) item;
                    Object[] values = new Object[map.size()];
                    for (int i = 0; i < headers.length; i++) {
                        String header = headers[i];
                        values[i] = map.get(header);
                    }
                    dataRows.add(toStringList(values));
                }

                return dataRows;
            }

            for (Object item : list) { dataRows.add(toStringList(item)); }
            return dataRows;
        }

        if (Iterable.class.isAssignableFrom(clazz)) {
            for (Object item : (Iterable) dataObject) { dataRows.add(toStringList(item)); }
            return dataRows;
        }

        if (dataObject instanceof String) {
            return TextUtils.to2dList(((String) dataObject), "\n", context.getTextDelim());
        }

        dataRows.add(Collections.singletonList(Objects.toString(dataObject)));
        return dataRows;
    }

    protected List<List<String>> stringTo2dList(String data) {
        List<List<String>> dataRows = new ArrayList<>();
        if (StringUtils.isEmpty(data)) { return dataRows; }

        List<String> list = TextUtils.toList(StringUtils.remove(data, '\r'), "\n", false);

        String delim = context.getTextDelim();

        list.forEach(item -> dataRows.add(new StrTokenizer(item, delim).setIgnoreEmptyTokens(false).getTokenList()));
        return dataRows;
    }

    protected List<String> toStringList(Object data) {
        List<String> list = new ArrayList<>();
        Class<?> clazz = data.getClass();

        if (clazz.isArray()) {
            int arraySize = ArrayUtils.getLength(data);
            for (int i = 0; i < arraySize; i++) { list.add(Objects.toString(Array.get(data, i))); }
            return list;
        }

        if (Iterable.class.isAssignableFrom(clazz)) {
            for (Object item : (Iterable) data) { list.add(Objects.toString(item)); }
            return list;
        }

        if (data instanceof String) {
            list.addAll(new StrTokenizer((String) data, context.getTextDelim()).setIgnoreEmptyTokens(false)
                                                                               .getTokenList());
            return list;
        }

        list.add(Objects.toString(data));
        return list;
    }

}
