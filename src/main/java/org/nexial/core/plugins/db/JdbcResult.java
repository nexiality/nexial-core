/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.db;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.plugins.db.SqlComponent.Type;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.nexial.core.NexialConst.DATE_FORMAT_NOW;

public class JdbcResult implements Serializable {
    protected static final int TO_STRING_KEY_LENGTH = 14;

    protected String sql;
    protected long startTime;
    protected long elapsedTime;
    protected String error;
    protected int rowCount = 0;
    protected List<String> columns;
    protected List<Map<String, Object>> data;
    protected boolean rolledBack;
    protected Type sqlType;
    protected transient int nestedIndex = -1;
    protected List<JdbcResult> nested;
    protected transient JdbcResult previous;
    protected transient JdbcResult next;

    public JdbcResult(String sql) {
        this.sql = sql;
        this.sqlType = Type.resolveFromSql(sql);
    }

    public JdbcResult(String sql, int rowCount) {
        if (StringUtils.isBlank(sql)) { throw new IllegalArgumentException("sql is empty/blank"); }
        this.sql = sql;
        this.rowCount = rowCount;
    }

    protected JdbcResult() { }

    public Type getSqlType() { return sqlType; }

    public String getSql() { return sql; }

    public void setSql(String sql) { this.sql = sql; }

    public long getStartTime() { return startTime; }

    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getElapsedTime() { return elapsedTime; }

    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime; }

    public String getError() { return error; }

    public void setError(String error) { this.error = error; }

    public boolean hasError() { return StringUtils.isNotBlank(error); }

    public int getRowCount() { return rowCount; }

    public void setRowCount(int rowCount) { this.rowCount = rowCount; }

    public List<String> getColumns() { return columns; }

    public boolean hasData() { return CollectionUtils.isNotEmpty(data) && rowCount > 0; }

    public List<Map<String, Object>> getData() { return data; }

    protected void setData(List<Map<String, Object>> results) {
        if (CollectionUtils.isEmpty(results)) {
            rowCount = 0;
            return;
        }

        // harvest column name
        this.columns = CollectionUtil.toList(results.get(0).keySet());
        this.rowCount = results.size();
        data = results;
    }

    public boolean isEmpty() { return getRowCount() < 1; }

    public List<Object> cells(String column) {
        List<Object> cells = new ArrayList<>();

        if (StringUtils.isBlank(column)) { return cells; }
        if (CollectionUtils.isEmpty(data)) { return cells; }

        // test to see if this is a known column
        if (CollectionUtils.isEmpty(columns) || !columns.contains(column)) { return cells; }

        data.forEach(row -> cells.add(row.get(column)));

        return cells;
    }

    public int columnCount() { return CollectionUtils.size(columns); }

    public boolean isRolledBack() { return rolledBack; }

    public void setRolledBack(boolean rolledBack) { this.rolledBack = rolledBack; }

    public boolean hasNested() { return CollectionUtils.isNotEmpty(nested); }

    public int getNestedCount() { return CollectionUtils.size(nested); }

    public JdbcResult nested(String index) {
        if (!NumberUtils.isDigits(index)) { throw new IllegalArgumentException("Invalid index: " + index); }

        int idx = NumberUtils.toInt(index);
        int maxIndex = getNestedCount();
        if (idx < 0 || idx >= maxIndex) { throw new IndexOutOfBoundsException("Index out of range: " + index); }

        return IterableUtils.get(nested, idx);
    }

    public JdbcResult getNext() { return next; }

    public JdbcResult next() { return getNext(); }

    public JdbcResult getPrevious() { return previous; }

    public JdbcResult previous() { return getPrevious(); }

    @Override
    public String toString() {
        String nestedToString = "";
        if (CollectionUtils.isNotEmpty(nested)) {
            nestedToString = "nested=" +
                             nested.stream()
                                   .map(result ->
                                            TextUtils.toList(result.toString(), "\n", true)
                                                     .stream()
                                                     .map(line -> "\t" + line)
                                                     .collect(Collectors.joining("\n")))
                                   .collect(Collectors.joining());
        }
        return TextUtils.prettyToString("sql=" + sql,
                                        "startTime=" + formatStartTime(startTime), 
                                        "elapsedTime=" + elapsedTime + " ms",
                                        StringUtils.isNotBlank(error) ? "error=" + error : "",
                                        "rowCount=" + rowCount,
                                        CollectionUtils.isNotEmpty(columns) ? "columns=" + columns : "",
                                        CollectionUtils.isNotEmpty(data) ? "data=" + data : "",
                                        isRolledBack() ? "TRANSACTION ROLLED BACK=yes" : "",
                                        nestedToString);
    }

    protected JdbcResult addNewNested() {
        JdbcResult result = new JdbcResult();
        result.startTime = this.startTime;
        result.sql = this.sql;
        result.elapsedTime = this.elapsedTime;
        result.error = this.error;
        result.rolledBack = this.rolledBack;
        result.sqlType = this.sqlType;

        if (CollectionUtils.isEmpty(nested)) {
            nested = new ArrayList<>();
            this.next = result;
            result.previous = this;
        } else {
            JdbcResult lastResult = nested.get(nested.size() - 1);
            lastResult.next = result;
            result.previous = lastResult;
        }
        result.nestedIndex = nested.size();
        nested.add(result);

        return result;
    }

    protected <T extends JdbcResult> T setTiming(long startTime) {
        this.setStartTime(startTime);
        this.setElapsedTime(System.currentTimeMillis() - startTime);

        if (CollectionUtils.isNotEmpty(nested)) {
            nested.forEach(result -> {
                result.startTime = startTime;
                result.elapsedTime = elapsedTime;
            });
        }
        return (T) this;
    }

    protected static String formatStartTime(long startTime) { return DateUtility.format(startTime, DATE_FORMAT_NOW); }
}
