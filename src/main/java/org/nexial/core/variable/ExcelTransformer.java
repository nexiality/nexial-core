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

package org.nexial.core.variable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

public class ExcelTransformer<T extends ExcelDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM = discoverFunctions(ExcelTransformer.class);
    private static final Map<String, Method> FUNCTIONS = toFunctionMap(FUNCTION_TO_PARAM,
                                                                       ExcelTransformer.class,
                                                                       ExcelDataType.class);

    public TextDataType text(T data) { return super.text(data); }

    public ListDataType worksheets(T data) {
        ExecutionContext context = ExecutionThread.get();
        String delim = context == null ? "," : context.getTextDelim();

        ListDataType list = new ListDataType("", delim);
        if (data == null || CollectionUtils.isEmpty(data.getWorksheetNames())) { return list; }

        list.setTextValue(TextUtils.toString(data.getWorksheetNames(), delim));
        list.init();
        return list;
    }

    public T read(T data, String sheet, String range) {
        if (data == null) { throw new IllegalArgumentException("data is null"); }
        if (StringUtils.isBlank(sheet)) { throw new IllegalArgumentException("Invalid sheet: " + sheet); }
        if (StringUtils.isBlank(range)) { throw new IllegalArgumentException("Invalid cell range: " + range); }

        data.read(data.getValue().worksheet(sheet, true), new ExcelAddress(range));
        return data;
    }

    public T pack(T data) {
        requireAfterRead(data, "pack()");

        List<List<String>> packed = new ArrayList<>();

        List<List<String>> range = data.getCapturedValues();
        for (List<String> row : range) {
            if (row == null) { continue; }

            List<String> packedRow = new ArrayList<>();
            for (String aRow : row) { packedRow.add(StringUtils.trim(aRow)); }

            if (CollectionUtils.isEmpty(packedRow)) { continue; }
            if (CollectionUtils.isEmpty(CollectionUtils.removeAll(packedRow, Arrays.asList(null, "")))) { continue; }

            packed.add(packedRow);
        }

        data.setCapturedValues(packed);
        return data;
    }

    @NotNull
    public T transpose(T data) {
        requireAfterRead(data, "transpose()");

        data.setCapturedValues(CollectionUtil.transpose(data.getCapturedValues()));
        return data;
    }

    @NotNull
    public CsvDataType csv(T data) throws TypeConversionException { return toCsv(data, false); }

    @NotNull
    public CsvDataType csvWithHeader(T data) throws TypeConversionException { return toCsv(data, true); }

    @NotNull
    public JsonDataType json(T data, String firstRowAsHeader) throws TypeConversionException {
        return new CsvTransformer<>().json(toCsv(data, BooleanUtils.toBoolean(firstRowAsHeader)));
    }

    @NotNull
    public T save(T data, String file, String sheet, String start) throws IOException {
        requireAfterRead(data, "save()");

        if (StringUtils.isBlank(file)) { throw new IllegalArgumentException("file is empty/blank"); }
        file = StringUtils.trim(file);

        if (StringUtils.isBlank(sheet)) { throw new IllegalArgumentException("sheet is empty/blank"); }
        sheet = StringUtils.trim(sheet);

        ExcelAddress addr = toExcelAddress(start);

        if (FileUtil.isFileReadable(file)) {
            ConsoleUtils.log("Overwriting '" + file + "' with current EXCEL content");
        } else {
            FileUtils.forceMkdirParent(new File(file));
        }

        Excel targetExcel = Excel.asXlsxExcel(file, false, false);
        if (targetExcel == null) {
            // file doesn't exists, can't be opened, or not compatible with Excel 2007
            // if file not exists or not a XLSX, creating a new file will effectively overwrite the existing.
            targetExcel = Excel.createExcel(new File(file), sheet);
        }

        Worksheet currentWorksheet = targetExcel.worksheet(sheet, true);
        data.getCapturedValues().forEach(row -> {
            currentWorksheet.setColumnValues(addr, row);
            addr.advanceRow();
        });

        targetExcel.save();

        return data;
    }

    public T clear(T data, String range) {
        requireAfterRead(data, "clear()");

        data.getCurrentSheet().clearCells(new ExcelAddress(range));
        data.read(data.getCurrentSheet(), data.getCurrentRange());
        return data;
    }

    public NumberDataType rowCount(T data) throws TypeConversionException {
        requireAfterRead(data, "rowCount()");
        return new NumberDataType(CollectionUtils.size(data.getCapturedValues()) + "");
    }

    public NumberDataType columnCount(T data) throws TypeConversionException {
        requireAfterRead(data, "columnCount()");

        final int[] maxColumn = new int[]{0};
        data.getCapturedValues().forEach(row -> maxColumn[0] = Math.max(maxColumn[0], CollectionUtils.size(row)));
        return new NumberDataType(maxColumn[0] + "");
    }

    public TextDataType firstCell(T data, String column, String regex, String maxRows) throws TypeConversionException {
        requireAfterRead(data, "firstCell()");

        if (StringUtils.isBlank(regex)) { throw new IllegalArgumentException("Invalid regex: " + regex); }
        if (!NumberUtils.isDigits(maxRows)) { throw new IllegalArgumentException("Invalid max-rows: " + maxRows); }

        ExcelAddress cellAddress = data.getCurrentSheet()
                                       .findFirstMatchingCell(column, regex, NumberUtils.toInt(maxRows));

        TextDataType returnType = new TextDataType("");
        if (cellAddress != null) { returnType.setValue(cellAddress.getAddr()); }
        return returnType;
    }

    public T replace(T data, String search, String replace) {
        requireAfterRead(data, "writeAcross()");

        if (StringUtils.isEmpty(search)) { throw new IllegalArgumentException("search is empty/null"); }
        String replaceWith = replace == null ? "" : replace;

        data.setCapturedValues(data.getCapturedValues()
                                   .parallelStream()
                                   .map(row -> row.parallelStream()
                                                  .map(cell -> cell = StringUtils.replace(cell, search, replaceWith))
                                                  .collect(Collectors.toList()))
                                   .collect(Collectors.toList()));
        return data;
    }

    public T writeAcross(T data, String... startAndContent) throws TypeConversionException {
        requireAfterRead(data, "writeAcross()");

        if (ArrayUtils.getLength(startAndContent) < 2) {
            throw new IllegalArgumentException("No starting address or content specified");
        }

        String start = startAndContent[0];

        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList(ArrayUtils.remove(startAndContent, 0)));

        try {
            data.getCurrentSheet().writeAcross(toExcelAddress(start), rows);
            // data.writeAcross(start, rows);
            return data;
        } catch (IOException e) {
            throw new TypeConversionException(data.getName(), rows.get(0).toString(),
                                              "Unable to write across " + start + ": " + e.getMessage(), e);
        }
    }

    public T writeDown(T data, String... startAndContent) throws TypeConversionException {
        requireAfterRead(data, "writeAcross()");

        if (ArrayUtils.getLength(startAndContent) < 2) {
            throw new IllegalArgumentException("No starting address or content specified");
        }

        String start = startAndContent[0];
        String[] content = ArrayUtils.remove(startAndContent, 0);

        List<List<String>> columns = new ArrayList<>();
        columns.add(Arrays.asList(content));

        try {
            data.getCurrentSheet().writeDown(toExcelAddress(start), columns);
            // data.writeDown(start, columns);
            return data;
        } catch (IOException e) {
            throw new TypeConversionException(data.getName(), columns.get(0).toString(),
                                              "Unable to write downwards from " + start + ": " + e.getMessage(), e);
        }
    }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    @NotNull
    protected CsvDataType toCsv(T data, boolean withHeader) throws TypeConversionException {
        requireAfterRead(data, withHeader ? "csvWithHeader()" : "csv()");
        List<List<String>> capturedValues = data.getCapturedValues();

        ExecutionContext context = ExecutionThread.get();
        String delim = context == null ? "," : context.getTextDelim();
        String recordDelim = "\r\n";

        String csvBuffer = TextUtils.toCsvContent(capturedValues, delim, recordDelim);

        CsvDataType csv = new CsvDataType(StringUtils.removeEnd(csvBuffer, recordDelim));
        csv.setRecordDelim(recordDelim);
        csv.setDelim(delim);
        csv.setHeader(withHeader);
        csv.setReadyToParse(true);
        csv.parse();

        return csv;
    }

    // todo?
    // public ExcelDataType deleteRow(ExcelDataType data, String row) { }
    // public ExcelDataType deleteColumn(ExcelDataType data, String column) { }
    // public ExcelDataType deleteRange(ExcelDataType data, String range) { }

    protected static ExcelAddress toExcelAddress(String range) {
        if (StringUtils.isBlank(range)) { throw new IllegalArgumentException("Excel address/range is empty/blank"); }
        try {
            return new ExcelAddress(StringUtils.trim(range));
        } catch (Exception e) {
            throw new IllegalArgumentException("not a valid Excel address '" + range + "'");
        }
    }

    protected void requireAfterRead(T data, String op) {
        if (data == null) { throw new IllegalArgumentException("data is null"); }
        if (CollectionUtils.isEmpty(data.getCapturedValues()) && data.getCurrentSheet() == null) {
            // if (CollectionUtils.isEmpty(data.getCapturedValues()) && data.getCurrentSheetName() == null) {
            throw new IllegalArgumentException(op + " can only be performed after a valid read() operation");
        }
    }
}