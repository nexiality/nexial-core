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

import java.io.BufferedOutputStream;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.util.FileUtil;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.plugins.db.SqlComponent.Type;
import org.nexial.core.utils.ConsoleUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.jdbc.core.support.JdbcDaoSupport;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Rdbms.*;
import static org.nexial.core.SystemVariables.getDefaultBool;
import static org.nexial.core.SystemVariables.getDefaultInt;

/**
 * a <b>VERY</b> basic and stripped down version of data extraction via SQL statements or stored procedure.  This class
 * only provides rudimentary extraction and JDBC handling logic.  Subclassing is probably a good idea to ensure proper
 * fulfillment of requirement.
 */
public class SimpleExtractionDao extends JdbcDaoSupport {
    private static final String MSG_NULL_JDBC = "Unable to resolve data access; contain Nexial Support team";

    protected String treatNullAs = DEF_TREAT_NULL_AS;
    protected Connection transactedConnection;
    protected Boolean autoCommit;
    protected ExecutionContext context;

    protected class JdbcResultExtractor
        implements ResultSetExtractor<JdbcResult>, StatementCallback<JdbcResult> {
        private JdbcResult result;
        private File file;
        private QueryResultExporter exporter;

        public JdbcResultExtractor(JdbcResult result) { this.result = result; }

        public JdbcResultExtractor(JdbcResult result, File file) {
            this.result = result;
            this.file = file;
            // this.exporter = new CsvExporter(context, treatNullAs, true)
        }

        public JdbcResultExtractor(JdbcResult result, File file, QueryResultExporter exporter) {
            this.result = result;
            this.file = file;
            this.exporter = exporter;
        }

        public JdbcResult getResult() { return result; }

        public File getFile() { return file; }

        @Override
        public JdbcResult extractData(ResultSet rs) throws SQLException, DataAccessException {
            if (result == null) { throw new IllegalArgumentException("null result found"); }

            if (rs == null) {
                result.setError("Unable to retrieve query result; Query execution possibly did not complete");
                return result;
            }

            int rowsAffected = rs.getStatement().getUpdateCount();
            if (rowsAffected != -1) { result.setRowCount(rowsAffected); }

            if (file == null) { return resultToListOfMap(rs, result); }
            if (exporter == null) { return resultToCSV(rs, result, file); }
            return exporter.export(rs, result, file);
        }

        @Override
        public JdbcResult doInStatement(Statement stmt) throws SQLException, DataAccessException {
            if (stmt == null) {
                result.setError("Unable to retrieve query result.  Statement is null");
                return result;
            }
            if (!StringUtils.equals(stmt.getClass().getSimpleName(), "TCJdbcStatement") && stmt.isClosed()) {
                result.setError("Unable to retrieve query result since Statement is closed");
                return result;
            }

            String sql = result.getSql();
            try {
                if (stmt.execute(sql)) {
                    ResultSet rs = stmt.getResultSet();
                    if (file == null) { return packData(resultToListOfMap(rs, result)); }
                    if (exporter == null) { return resultToCSV(rs, result, file); }
                    return exporter.export(rs, result, file);
                } else {
                    int rowsAffected = stmt.getUpdateCount();
                    if (rowsAffected != -1) { result.setRowCount(rowsAffected); }
                    return result;
                }
            } catch (SQLException | DataAccessException e) {
                result.setError("Error occurred when executing '" + sql + "': " + e.getMessage());
                return result;
            }
        }
    }

    public void setTreatNullAs(String treatNullAs) { this.treatNullAs = treatNullAs; }

    public void setContext(ExecutionContext context) { this.context = context; }

    public JdbcResult executeSql(String sql, File saveTo) {
        long startTime = System.currentTimeMillis();
        JdbcResult result = new JdbcResult(sql);
        return executeAndExtract(sql, result, new JdbcResultExtractor(result, saveTo)).setTiming(startTime);
    }

    protected JdbcResult executeStoredProcedure(Connection connection, JdbcResultExtractor extractor) {
        JdbcResult result = extractor.getResult();
        String sql = result.getSql();

        try (CallableStatement callStmt = connection.prepareCall(sql)) {
            //callStmt.setString(1, "...");
            //callStmt.registerOutParameter(2, OracleTypes.CURSOR);

            boolean hasResult = callStmt.execute();
            if (hasResult) {
                return packData(extractor.extractData(callStmt.getResultSet()));
            } else {
                result.setRowCount(callStmt.getUpdateCount());
                return result;
            }
        } catch (SQLException e) {
            result.setError("Error executing stored procedure '" + sql + "': " + e.getMessage());
            return result;
        }
    }

    protected JdbcOutcome executeSqls(List<SqlComponent> sqls) {
        long startTime = System.currentTimeMillis();
        final JdbcOutcome outcome = new JdbcOutcome();
        outcome.setStartTime(startTime);

        ExecutionContext context = ExecutionThread.get();

        AtomicReference<Boolean> explicitCommit = new AtomicReference<>(false);
        AtomicReference<Boolean> explicitRollback = new AtomicReference<>(false);

        sqls.forEach(sql -> {
            String query = sql.getSql();
            String varName = sql.getVarName();
            if (StringUtils.isNotBlank(query)) {

                if (context != null) {
                    query = context.replaceTokens(query);
                    varName = context.replaceTokens(varName);
                }

                ConsoleUtils.log("Executing" + (StringUtils.isNotEmpty(varName) ? " '" + varName + "'" : "") + " - " +
                                 query);

                JdbcResult result = executeSql(query, null);

                // set result to context now so we can use it right away
                if (result != null) {
                    if (context != null) { context.setData(varName, result); }
                    outcome.addOutcome(varName, result);

                    Type sqlType = result.getSqlType();
                    if (sqlType != null) {
                        if (sqlType.isCommit()) { explicitCommit.set(true); }
                        if (sqlType.isRollback()) { explicitRollback.set(true); }
                        if (sqlType.isUpdate()) {
                            explicitCommit.set(false);
                            explicitRollback.set(false);
                        }
                    }
                }
            }
        });

        if (!isAutoCommit()) {
            initTransactedConnection();

            try {
                transactedConnection.commit();
            } catch (SQLException e) {
                // this might fail since explicit commit/rollback was performed earlier
                if (explicitCommit.get() || explicitRollback.get()) {
                    ConsoleUtils.log("Possibly benign error found when committing current transaction - " +
                                     e.getErrorCode() + " " + e.getMessage() + "\n" +
                                     "An explicit COMMIT or ROLLBACK was PREVIOUSLY EXECUTED IN THIS TRANSACTION");
                } else {
                    outcome.setError(e.getErrorCode() + " " + e.getMessage());
                }
            } finally {
                try { transactedConnection.close(); } catch (SQLException e) { }
                transactedConnection = null;
            }
        }

        outcome.setStartTime(startTime);
        outcome.setElapsedTime(System.currentTimeMillis() - startTime);
        return outcome;
    }

    protected JdbcResult packData(JdbcResult result) {
        if (result == null || CollectionUtils.isEmpty(result.getData())) { return result; }

        List<Map<String, String>> results = pack(result.getData());
        result.setData(results);
        return result;
    }

    protected List<Map<String, String>> pack(List<Map<String, String>> results) {
        if (context == null ||
            !context.getBooleanData(OPT_INCLUDE_PACK_SINGLE_ROW, getDefaultBool(OPT_INCLUDE_PACK_SINGLE_ROW))) {
            return results;
        }

        if (CollectionUtils.size(results) == 1) {
            Map<String, String> newRow = new LinkedHashMap<>();
            results.get(0).forEach((column, value) -> {
                if (value != null && !StringUtils.equals(value, treatNullAs)) { newRow.put(column, value); }
            });
            results.remove(0);
            results.add(newRow);
        }

        return results;
    }

    protected JdbcResult importResults(SqlComponent sql, SimpleExtractionDao dao, TableSqlGenerator tableGenerator) {
        long startTime = System.currentTimeMillis();

        String query = sql.getSql();
        JdbcResult result = new JdbcResult(query);

        JdbcTemplate jdbc = getJdbcTemplate();
        if (jdbc == null) { throw new RuntimeException(MSG_NULL_JDBC); }

        Integer rowsImported = jdbc.query(query, rs -> {
            if (!rs.next()) {
                result.setError("Unable to retrieve query result; Query execution possibly did not complete");
                return -1;
            }

            // 1. generate DDL for target table
            ResultSetMetaData metaData = rs.getMetaData();
            String ddl = tableGenerator.generateSql(metaData);
            JdbcResult ddlResult = dao.executeSql(ddl, null);
            if (ddlResult.hasError()) {
                result.setError("Failed to create table via '" + ddl + "': " + ddlResult.getError());
                return -1;
            }

            // 2. generate DML to import data to target
            int rowCount = 0;
            int rowsAffected = 0;
            int importBufferSize = context.getIntData(IMPORT_BUFFER_SIZE, getDefaultInt(IMPORT_BUFFER_SIZE));
            StringBuilder error = new StringBuilder();
            StringBuilder inserts = new StringBuilder();
            int numOfColumn = metaData.getColumnCount();

            do {
                inserts.append("INSERT INTO ").append(tableGenerator.getTable()).append(" VALUES (");
                for (int i = 1; i <= numOfColumn; i++) {
                    String data = rs.getString(i);
                    if (rs.wasNull()) {
                        data = "NULL";
                    } else if (tableGenerator.isTextColumnType(metaData.getColumnType(i))) {
                        data = "'" + StringUtils.replace(data, "'", "''") + "'";
                    }
                    inserts.append(data);
                    if (i < numOfColumn) { inserts.append(","); }
                }

                inserts.append(");\n");

                rowCount++;

                if (rowCount % importBufferSize == 0) {
                    JdbcOutcome insertResults = dao.executeSqls(SqlComponent.toList(inserts.toString()));
                    rowsAffected += insertResults.getRowsAffected();
                    if (result.hasError()) { error.append(result.getError()).append("\n"); }
                    inserts = new StringBuilder();
                }
            } while (rs.next());

            JdbcOutcome insertResults = dao.executeSqls(SqlComponent.toList(inserts.toString()));
            rowsAffected += insertResults.getRowsAffected();
            if (result.hasError()) { error.append(result.getError()).append("\n"); }

            if (error.length() > 0) { result.setError(error.toString()); }

            return rowsAffected;
        });

        result.setRowCount(rowsImported == null ? -1 : rowsImported);
        result.setTiming(startTime);

        return result;
    }

    protected void setAutoCommit(Boolean autoCommit) { this.autoCommit = autoCommit; }

    protected Boolean isAutoCommit() {
        return autoCommit != null ? autoCommit : ((BasicDataSource) getDataSource()).getDefaultAutoCommit();
    }

    /** @deprecated use {@link CsvExporter} instead */
    @Deprecated
    protected <T extends JdbcResult> T resultToCSV(ResultSet rs, T result, File file) throws SQLException {
        if (rs == null || !rs.next()) { return result; }

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        String recordDelim = "\n";
        String delim = ",";

        int rowCount = 0;
        try (BufferedOutputStream outputStream = FileUtil.makeOutputStream(file)) {
            // construct header
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(DaoUtils.csvFriendly(metaData.getColumnLabel(i), delim, true));
            }

            outputStream.write((TextUtils.toString(columns, delim) + recordDelim).getBytes());
            result.columns = columns;

            // recycle through all rows
            do {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    if (value == null && treatNullAs != null) {
                        value = treatNullAs;
                    } else {
                        value = DaoUtils.csvFriendly(value, delim, true);
                    }

                    row.append(value).append(delim);
                }

                rowCount++;
                String line = StringUtils.removeEnd(row.toString(), delim) + recordDelim;
                outputStream.write(line.getBytes());
            } while (rs.next());

            result.setRowCount(rowCount);
            outputStream.flush();
        } catch (Throwable e) {
            result.setError(e.getMessage());
        }

        return result;
    }

    protected <T extends JdbcResult> T resultToListOfMap(ResultSet rs, T result) throws SQLException {
        if (rs == null || !rs.next()) { return result; }

        List<Map<String, String>> rows = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();

        // cycle through all rows
        do {
            Map<String, String> row = new LinkedHashMap<>();

            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                // rs.getString(): String representation of column value, or null if the column is SQL NULL.
                String value = rs.getString(i);
                if (value == null && treatNullAs != null) { value = treatNullAs; }
                row.put(StringUtils.trim(metaData.getColumnLabel(i)), value);
            }

            rows.add(row);
        } while (rs.next());

        result.setData(rows);
        return result;
    }

    @NotNull
    protected JdbcResult saveAsJSON(@NotNull String sql, @NotNull File output, boolean header) {
        long startTime = System.currentTimeMillis();
        JdbcResult result = new JdbcResult(sql);
        JsonExporter exporter = new JsonExporter(context, treatNullAs, header);
        return executeAndExtract(sql, result, new JdbcResultExtractor(result, output, exporter)).setTiming(startTime);
    }

    @NotNull
    protected JdbcResult saveAsXML(@NotNull String sql, @NotNull File output, String root, String row, String cell) {
        long startTime = System.currentTimeMillis();
        JdbcResult result = new JdbcResult(sql);
        XmlExporter exporter = new XmlExporter(context, treatNullAs, root, row, cell);
        return executeAndExtract(sql, result, new JdbcResultExtractor(result, output, exporter)).setTiming(startTime);
    }

    @NotNull
    protected JdbcResult saveAsEXCEL(@NotNull String sql, @NotNull File output, String sheet, String startAddress) {
        long startTime = System.currentTimeMillis();
        JdbcResult result = new JdbcResult(sql);
        ExcelExporter exporter = new ExcelExporter(treatNullAs, true, sheet, new ExcelAddress(startAddress));
        return executeAndExtract(sql, result, new JdbcResultExtractor(result, output, exporter)).setTiming(startTime);
    }

    protected void initTransactedConnection() {
        try {
            if (transactedConnection == null || transactedConnection.isClosed()) {
                JdbcTemplate jdbc = getJdbcTemplate();
                if (jdbc == null) { throw new RuntimeException(MSG_NULL_JDBC); }

                transactedConnection = jdbc.getDataSource().getConnection();
                if (transactedConnection == null) {
                    throw new InvalidDataAccessResourceUsageException(
                        "Unable to obtain database connection for transaction: null");
                }
            }

            if (transactedConnection.isClosed()) {
                throw new InvalidDataAccessResourceUsageException(
                    "Underlying connection is closed. Unable to proceed.");
            }
        } catch (SQLException e) {
            throw new InvalidDataAccessResourceUsageException("Unable to obtain database connection for transaction: " +
                                                              e.getMessage(), e);
        }
    }

    @NotNull
    private JdbcResult executeAndExtract(@NotNull String sql, JdbcResult result, JdbcResultExtractor extractor) {
        JdbcTemplate jdbc = getJdbcTemplate();
        if (jdbc == null) { throw new RuntimeException(MSG_NULL_JDBC); }

        if (isAutoCommit()) { return jdbc.execute(extractor); }

        initTransactedConnection();

        // stored procedure?
        Type sqlType = result.getSqlType();
        if (sqlType != null && sqlType.isStoredProcedure()) {
            return executeStoredProcedure(transactedConnection, extractor);
        }

        try (Statement statement = transactedConnection.createStatement()) {
            return extractor.doInStatement(statement);
        } catch (SQLException e) {
            result.setError("Error executing " + sql + ": " + e.getMessage());
            return result;
        }
    }
}
