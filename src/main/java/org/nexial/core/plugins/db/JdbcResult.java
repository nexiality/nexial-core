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

package org.nexial.core.plugins.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.DateUtility;
import org.nexial.core.plugins.db.SqlComponent.Type;
import org.nexial.core.utils.ConsoleUtils;

import static org.apache.commons.lang3.StringUtils.rightPad;
import static org.nexial.core.NexialConst.DATE_FORMAT_NOW;
import static org.nexial.core.NexialConst.NL;
import static org.nexial.core.plugins.db.SqlComponent.Type.UNKNOWN;

public class JdbcResult implements Serializable {
    protected static final int TO_STRING_KEY_LENGTH = 14;

    protected String sql;
    protected long startTime;
    protected long elapsedTime;
    protected String error;
    protected int rowCount = 0;
    protected List<String> columns;
    protected List<Map<String, String>> data;
    protected boolean rolledBack;
    protected Type sqlType;

    public JdbcResult(String sql) {
        this.sql = sql;
        String sqlStart = StringUtils.upperCase(StringUtils.substringBefore(sql, " "));
        if (StringUtils.isNotBlank(sqlStart)) {
            try {
                sqlType = Type.toType(sqlStart);
            } catch (IllegalArgumentException e) {
                sqlType = UNKNOWN;
                ConsoleUtils.log("Unknown SQL type from SQL: '" + sql + "'");
            }
        }
    }

    public JdbcResult(String sql, int rowCount) {
        if (StringUtils.isBlank(sql)) { throw new IllegalArgumentException("sql is empty/blank"); }
        this.sql = sql;
        this.rowCount = rowCount;
    }

    protected JdbcResult() { }

    public Type getSqlType() { return sqlType; }

    public String getSql() { return sql; }

    public void setSql(String sql) { this.sql = sql;}

    public long getStartTime() { return startTime; }

    public void setStartTime(long startTime) { this.startTime = startTime;}

    public long getElapsedTime() { return elapsedTime; }

    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime;}

    public String getError() { return error; }

    public void setError(String error) { this.error = error;}

    public boolean hasError() { return StringUtils.isNotBlank(error); }

    public int getRowCount() { return rowCount; }

    public void setRowCount(int rowCount) { this.rowCount = rowCount; }

    public List<String> getColumns() { return columns; }

    public boolean hasData() { return CollectionUtils.isNotEmpty(data) && rowCount > 0; }

    public List<Map<String, String>> getData() { return data; }

    protected void setData(List<Map<String, String>> results) {
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

    public List<String> cells(String column) {
        List<String> cells = new ArrayList<>();

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

    @Override
    public String toString() {
        return NL +
               rightPad("sql", TO_STRING_KEY_LENGTH) + "=" + sql + NL +
               rightPad("startTime", TO_STRING_KEY_LENGTH) + "=" + formatStartTime(startTime) + NL +
               rightPad("elapsedTime", TO_STRING_KEY_LENGTH) + "=" + elapsedTime + " ms" + NL +
               (StringUtils.isNotBlank(error) ? rightPad("error", TO_STRING_KEY_LENGTH) + "=" + error + NL : "") +
               rightPad("rowCount", TO_STRING_KEY_LENGTH) + "=" + rowCount + NL +
               (CollectionUtils.isNotEmpty(data) ? rightPad("data", TO_STRING_KEY_LENGTH) + "=" + data + NL : "") +
               (isRolledBack() ? "TRANSACTION ROLLBACKED" + NL : "");
    }

    protected <T extends JdbcResult> T setTiming(long startTime) {
        this.setStartTime(startTime);
        this.setElapsedTime(System.currentTimeMillis() - startTime);
        return (T) this;
    }

    protected static String formatStartTime(long startTime) { return DateUtility.format(startTime, DATE_FORMAT_NOW); }

}
