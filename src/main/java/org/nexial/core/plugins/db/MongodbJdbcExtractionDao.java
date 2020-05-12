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
 */

package org.nexial.core.plugins.db;

import java.io.File;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.nexial.core.utils.JsonUtils;

import com.dbschema.resultSet.ObjectAsResultSet;
import com.dbschema.resultSet.OkResultSet;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import static org.nexial.core.plugins.db.SqlComponent.Type.*;

public class MongodbJdbcExtractionDao extends SimpleExtractionDao {
    private boolean expandDoc;

    public void setExpandDoc(boolean expandDoc) { this.expandDoc = expandDoc; }

    @Override
    public JdbcResult executeSql(String sql, File saveTo) {
        long startTime = System.currentTimeMillis();
        MongodbJdbcResult result = new MongodbJdbcResult(sql);
        return executeAndExtract(sql, result, new JdbcResultExtractor(result, saveTo)).setTiming(startTime);
    }

    @Override
    protected <T extends JdbcResult> T resultToListOfMap(ResultSet rs, T result) throws SQLException {
        if (rs == null || !rs.next()) { return result; }

        if (rs instanceof ObjectAsResultSet) { return handleObjectResultSet((ObjectAsResultSet) rs, result); }

        if (rs instanceof OkResultSet) {
            result.setRowCount(0);
            return result;
        }

        List<Map<String, String>> rows = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();

        // cycle through all rows
        do {
            Map<String, String> row = new LinkedHashMap<>();

            ((MongodbJdbcResult) result).setExpanded(expandDoc);
            if (!expandDoc) {
                row.put(metaData.getColumnLabel(1), stringify(rs.getObject(1)));
            } else {
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    // rs.getString(): String representation of column value, or null if the column is SQL NULL.
                    String value = rs.getString(i);
                    if (value == null && treatNullAs != null) { value = treatNullAs; }
                    row.put(StringUtils.trim(metaData.getColumnLabel(i)), value);
                }
            }

            rows.add(row);
        } while (rs.next());

        result.setData(rows);
        return result;
    }

    protected <T extends JdbcResult> T handleObjectResultSet(ObjectAsResultSet rs, T result) throws SQLException {
        MongodbJdbcResult mongoResult = (MongodbJdbcResult) result;
        Object firstRecord = rs.getObject(1);

        if (firstRecord instanceof DeleteResult) {
            mongoResult.updateSqlType(DELETE);
            DeleteResult deleteRs = (DeleteResult) firstRecord;
            mongoResult.setDeletedCount(deleteRs.getDeletedCount());
            mongoResult.setAcknowledged(deleteRs.wasAcknowledged());
            return (T) mongoResult;
        }

        if (firstRecord instanceof UpdateResult) {
            UpdateResult updateRs = (UpdateResult) firstRecord;
            mongoResult.updateSqlType(UPDATE);
            mongoResult.setModifiedCount(updateRs.getModifiedCount());
            mongoResult.setMatchedCount(updateRs.getMatchedCount());
            mongoResult.setAcknowledged(updateRs.wasAcknowledged());
            return (T) mongoResult;
        }

        if (firstRecord instanceof Document) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("document", JsonUtils.compact(((Document) firstRecord).toJson(), false));
            List<Map<String, String>> rows = new ArrayList<>();
            rows.add(row);
            mongoResult.setData(rows);
            mongoResult.updateSqlType(SELECT);
            return (T) mongoResult;
        }

        // ConsoleUtils.error("Unknown/unsupported result type: " + rs.getClass());
        // return null;
        List<Map<String, String>> rows = new ArrayList<>();
        do {
            ResultSetMetaData rsMetaData = rs.getMetaData();
            for (int i = 1; i <= rsMetaData.getColumnCount(); i++) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put(rsMetaData.getColumnLabel(i), String.valueOf(rs.getObject(i)));
                rows.add(row);
            }
        } while (rs.next());

        mongoResult.setData(rows);
        return (T) mongoResult;
    }

    protected static String stringify(Object value) {
        if (value == null) { return null; }
        if (value instanceof Document) { return JsonUtils.compact(((Document) value).toJson(), false); }
        if (value instanceof DeleteResult) { return ((DeleteResult) value).getDeletedCount() + ""; }
        if (value instanceof UpdateResult) { return ((UpdateResult) value).getModifiedCount() + ""; }
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            if (list.isEmpty()) { return "[]"; }
            return JsonUtils.compact(
                "[" + list.stream().reduce((initial, curr) -> stringify(initial) + "," + stringify(curr)).get() + "]",
                false);
        }

        return value.toString();
    }
}