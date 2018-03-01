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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.NexialCommand;
import org.nexial.core.plugins.db.RdbmsCommand;
import org.nexial.core.plugins.db.JdbcOutcome;
import org.nexial.core.plugins.db.JdbcResult;

import static org.nexial.core.NexialConst.CSV_FIELD_DEIM;
import static org.nexial.core.NexialConst.CSV_ROW_SEP;

public class SqlTransformer<T extends SqlDataType> extends Transformer {
    private static final Map<String, Integer> FUNCTION_TO_PARAM = discoverFunctions(SqlTransformer.class);
    private static final Map<String, Method> FUNCTIONS =
        toFunctionMap(FUNCTION_TO_PARAM, SqlTransformer.class, SqlDataType.class);

    public TextDataType text(T data) { return super.text(data); }

    public T execute(T data, String db) {
        if (data == null || StringUtils.isBlank(db)) { return data; }

        ExecutionContext context = ExecutionThread.get();
        if (context == null) { throw new IllegalStateException("Unable to execute statements - context is missing"); }

        RdbmsCommand rdbms = resolveRdbmsCommand(context);
        JdbcOutcome outcome = rdbms.executeSQLs(db, data.getSqlStatements());
        if (outcome == null || CollectionUtils.isEmpty(outcome)) {
            throw new IllegalStateException("Failed to execute statements - no outcome after execution");
        }

        data.setExecutionOutcome(outcome);
        return data;
    }

    public NumberDataType resultCount(T data) throws TypeConversionException {
        NumberDataType number = new NumberDataType("0");
        if (isEmptyDataType(data)) { return number; }

        number.setValue(data.getExecutionOutcome().size());
        number.setTextValue(number.getValue() + "");
        return number;
    }

    public TextDataType sql(T data, String resultName) throws TypeConversionException {
        TextDataType sql = new TextDataType("");
        if (isEmptyDataType(data)) { return sql; }

        JdbcResult result = data.getResult(resultName);
        if (result == null) { return sql; }

        sql.setValue(result.getSql());
        sql.setTextValue(sql.getValue());
        return sql;
    }

    public DateDataType startTime(T data, String resultName) throws TypeConversionException {
        DateDataType date = new DateDataType("");
        if (isEmptyDataType(data)) { return date; }

        JdbcResult result = data.getResult(resultName);
        if (result == null) { return date; }

        date.setTextValue(date.getValue() + "");
        date.parse();
        return date;
    }

    public NumberDataType elapsedTime(T data, String resultName) throws TypeConversionException {
        NumberDataType number = new NumberDataType("0");
        if (isEmptyDataType(data)) { return number; }

        JdbcResult result = data.getResult(resultName);
        if (result == null) { return number; }

        number.setValue(result.getElapsedTime());
        number.setTextValue(number.getValue() + "");
        return number;
    }

    public NumberDataType rowCount(T data, String resultName) throws TypeConversionException {
        NumberDataType number = new NumberDataType("0");
        if (isEmptyDataType(data)) { return number; }

        JdbcResult result = data.getResult(resultName);
        if (result == null) { return number; }

        number.setValue(result.getRowCount());
        number.setTextValue(number.getValue() + "");
        return number;
    }

    public TextDataType error(T data, String resultName) throws TypeConversionException {
        TextDataType error = new TextDataType("");
        if (isEmptyDataType(data)) { return error; }

        JdbcResult result = data.getResult(resultName);
        if (result == null) { return error; }

        error.setValue(result.getError());
        error.setTextValue(error.getValue());
        return error;
    }

    public NumberDataType columnCount(T data, String resultName) throws TypeConversionException {
        NumberDataType number = new NumberDataType("0");
        if (isEmptyDataType(data)) { return number; }

        JdbcResult result = data.getResult(resultName);
        if (result == null) { return number; }

        number.setValue(CollectionUtils.size(result.getColumns()));
        number.setTextValue(number.getValue() + "");
        return number;
    }

    public ListDataType row(T data, String resultName, String rowIndex) throws TypeConversionException {
        if (StringUtils.isBlank(resultName)) { throw new IllegalArgumentException("resultName is empty/blank"); }
        if (!NumberUtils.isDigits(rowIndex)) { throw new IllegalArgumentException("rowIndex is invalid: " + rowIndex); }

        ListDataType list = new ListDataType("");
        if (isEmptyDataType(data)) { return list;}

        JdbcResult result = data.getResult(resultName);
        if (result == null || !result.hasData()) { return list; }

        int row = NumberUtils.createInteger(rowIndex);
        if (result.getRowCount() <= row) { return list; }

        Map<String, String> rowData = result.getData().get(row);
        if (MapUtils.isEmpty(rowData)) { return list; }

        List<String> oneRow = new ArrayList<>();
        rowData.forEach((column, value) -> oneRow.add(value));
        if (CollectionUtils.isEmpty(oneRow)) { return list; }

        list.setValue(oneRow.toArray(new String[0]));
        list.setTextValue(TextUtils.toString(oneRow, list.getDelim()));
        return list;
    }

    public ListDataType cells(T data, String resultName, String column) throws TypeConversionException {
        if (StringUtils.isBlank(resultName)) { throw new IllegalArgumentException("resultName is empty/blank"); }
        if (StringUtils.isBlank(column)) { throw new IllegalArgumentException("column is invalid: " + column); }

        ListDataType list = new ListDataType("");
        if (isEmptyDataType(data)) { return list;}

        JdbcResult result = data.getResult(resultName);
        if (result == null || !result.hasData()) { return list; }

        List<String> cells = result.cells(column);
        if (CollectionUtils.isEmpty(cells)) { return list; }

        list.setValue(cells.toArray(new String[0]));
        list.setTextValue(TextUtils.toString(cells, list.getDelim()));
        return list;
    }

    public CsvDataType csv(T data, String resultName) throws TypeConversionException {
        CsvDataType csv = new CsvDataType("");
        if (isEmptyDataType(data)) { return csv; }

        JdbcResult result = data.getResult(resultName);
        if (result == null || result.isEmpty()) { return csv; }

        ExecutionContext context = ExecutionThread.get();
        if (context == null) { throw new IllegalStateException("Unable to create CSV - context is missing"); }

        RdbmsCommand rdbms = resolveRdbmsCommand(context);
        String csvContent = rdbms.toCSV(result, CSV_FIELD_DEIM, true);
        csv.setDelim(CSV_FIELD_DEIM);
        csv.setRecordDelim(CSV_ROW_SEP);
        csv.setQuote("\"");
        csv.setTextValue(csvContent);
        csv.setReadyToParse(true);
        csv.parse();
        return csv;
    }

    public ListDataType columns(T data, String resultName) throws TypeConversionException {
        ListDataType list = new ListDataType("");
        if (isEmptyDataType(data)) { return list;}

        JdbcResult result = data.getResult(resultName);
        if (result == null) { return list; }

        list.setValue(result.getColumns().toArray(new String[0]));
        list.setTextValue(TextUtils.toString(list.getValue(), list.getDelim(), "", ""));
        return list;
    }

    public TextDataType rolledBack(T data, String resultName) throws TypeConversionException {
        TextDataType status = new TextDataType("");
        if (isEmptyDataType(data)) { return status; }

        JdbcResult result = data.getResult(resultName);
        if (result == null) { return status; }

        status.setValue(BooleanUtils.toStringTrueFalse(result.isRolledBack()));
        status.setTextValue(status.getValue());
        return status;
    }

    public T store(T data, String var) {
        snapshot(var, data);
        return data;
    }

    @Override
    Map<String, Integer> listSupportedFunctions() { return FUNCTION_TO_PARAM; }

    @Override
    Map<String, Method> listSupportedMethods() { return FUNCTIONS; }

    protected boolean isEmptyDataType(T data) {
        return data == null || data.getExecutionOutcome() == null || data.getExecutionOutcome().size() < 1;
    }

    @NotNull
    protected RdbmsCommand resolveRdbmsCommand(ExecutionContext context) {
        NexialCommand command = context.findPlugin("rdbms");
        if (command instanceof RdbmsCommand) { return (RdbmsCommand) command; }
        throw new IllegalStateException("Unable to execute statements - rdbms command not available");
    }
}
