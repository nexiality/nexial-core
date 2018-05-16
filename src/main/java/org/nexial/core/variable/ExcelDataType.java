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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;

import static java.lang.System.lineSeparator;
import static org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK;
import static org.nexial.core.excel.Excel.MIN_EXCEL_FILE_SIZE;

public class ExcelDataType extends ExpressionDataType<Excel> {
    private ExcelTransformer transformer = new ExcelTransformer();
    private String filePath;
    private List<String> worksheetNames;
    private List<List<String>> capturedValues;
    private Worksheet currentSheet;
    private ExcelAddress currentRange;

    public ExcelDataType(String textValue) throws TypeConversionException { super(textValue); }

    private ExcelDataType() { super(); }

    public List<String> getWorksheetNames() { return worksheetNames; }

    @Override
    public String getName() { return "EXCEL"; }

    @Override
    public String toString() {
        return getName() + "(" + lineSeparator() + getTextValue() + lineSeparator() + ")";
    }

    public String getFilePath() { return filePath; }

    public List<List<String>> getCapturedValues() { return capturedValues; }

    public void setCapturedValues(List<List<String>> capturedValues) {
        this.capturedValues = capturedValues;
        textValue = "";
        if (!CollectionUtils.isNotEmpty(capturedValues)) { return; }

        char delim = ',';
        String recordDelim = "\r\n";
        capturedValues.forEach(row -> textValue += TextUtils.toString(row, delim + "") + recordDelim);
        textValue = StringUtils.removeEnd(textValue, recordDelim);
        if (StringUtils.isBlank(textValue)) { textValue = ""; }
    }

    public Worksheet getCurrentSheet() { return currentSheet; }

    public void setCurrentSheet(Worksheet currentSheet) { this.currentSheet = currentSheet; }

    public ExcelAddress getCurrentRange() { return currentRange; }

    public void setCurrentRange(ExcelAddress currentRange) { this.currentRange = currentRange; }

    @Override
    Transformer getTransformer() { return transformer; }

    @Override
    ExcelDataType snapshot() {
        ExcelDataType snapshot = new ExcelDataType();
        snapshot.transformer = transformer;
        snapshot.value = value;
        snapshot.textValue = textValue;
        snapshot.filePath = filePath;
        snapshot.worksheetNames = worksheetNames;
        snapshot.capturedValues = new ArrayList<>(capturedValues);
        snapshot.currentSheet = currentSheet;
        snapshot.currentRange = currentRange;
        return snapshot;
    }

    @Override
    protected void init() throws TypeConversionException {
        if (StringUtils.isBlank(textValue)) {
            throw new TypeConversionException(getName(), textValue, "Not a valid spreadsheet: '" + textValue + "'");
        }

        try {
            if (!FileUtil.isFileReadable(textValue, MIN_EXCEL_FILE_SIZE)) {
                value = Excel.newExcel(new File(textValue));
            } else {
                value = new Excel(new File(textValue), false);
            }

            filePath = textValue;
            List<Worksheet> worksheets = value.getWorksheetsStartWith("");
            if (CollectionUtils.isNotEmpty(worksheets)) {
                worksheetNames = new ArrayList<>();
                worksheets.forEach(worksheet -> worksheetNames.add(worksheet.getName()));
            }
        } catch (IOException e) {
            throw new TypeConversionException(getName(),
                                              textValue,
                                              "Error opening " + textValue + ": " + e.getMessage(),
                                              e);
        }
    }

    protected void read(Worksheet worksheet, ExcelAddress range) {
        worksheet.getSheet().getWorkbook().setMissingCellPolicy(CREATE_NULL_AS_BLANK);

        List<List<String>> values = new ArrayList<>();
        List<List<XSSFCell>> wholeArea = worksheet.cells(range, false);
        if (CollectionUtils.isNotEmpty(wholeArea)) {
            wholeArea.forEach(row -> {
                List<String> rowValues = new ArrayList<>();
                row.forEach(cell -> rowValues.add(prepCellData(Excel.getCellValue(cell))));
                values.add(rowValues);
            });
        }

        currentRange = range;
        currentSheet = worksheet;
        setCapturedValues(values);
    }

    protected String prepCellData(String cellValue) {
        if (StringUtils.isEmpty(cellValue)) { return cellValue; }

        cellValue = StringUtils.replace(cellValue, "\"", "\"\"");

        if (StringUtils.containsAny(cellValue, ",", "\r", "\n")) {
            return TextUtils.wrapIfMissing(cellValue, "\"", "\"");
        }

        return cellValue;
    }

}