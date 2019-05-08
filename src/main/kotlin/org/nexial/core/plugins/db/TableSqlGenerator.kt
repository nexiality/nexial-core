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

package org.nexial.core.plugins.db

import org.apache.commons.lang3.StringUtils
import java.sql.ResultSetMetaData
import java.sql.Types.*

abstract class TableSqlGenerator(val table: String) {
    abstract fun generateSql(metadata: ResultSetMetaData): String
    abstract fun dbSpecificTypeName(type: Int): String
    abstract fun isTextColumnType(type: Int): Boolean
}

class SqliteTableSqlGenerator(table: String) : TableSqlGenerator(table = table) {
    override fun generateSql(metadata: ResultSetMetaData): String {
        val ddl = StringBuilder("CREATE TABLE IF NOT EXISTS ").append(table).append("(")

        val numOfColumns = metadata.columnCount
        for (i in 1..numOfColumns) {
            ddl.append("\"").append(metadata.getColumnLabel(i)).append("\" ")
                .append(dbSpecificTypeName(metadata.getColumnType(i))).append(",")
        }

        return StringUtils.removeEnd(ddl.toString(), ",") + ");"
    }

    override fun dbSpecificTypeName(type: Int) = when (type) {
        BINARY                  -> "BLOB"
        BLOB                    -> "BLOB"
        LONGVARBINARY           -> "BLOB"
        VARBINARY               -> "BLOB"

        BIGINT                  -> "INTEGER"
        BIT                     -> "INTEGER"
        INTEGER                 -> "INTEGER"
        ROWID                   -> "INTEGER"
        SMALLINT                -> "INTEGER"
        TINYINT                 -> "INTEGER"

        NULL                    -> "NULL"

        BOOLEAN                 -> "NUMERIC"
        DATE                    -> "NUMERIC"
        NUMERIC                 -> "NUMERIC"
        TIME                    -> "NUMERIC"
        TIME_WITH_TIMEZONE      -> "NUMERIC"
        TIMESTAMP               -> "NUMERIC"
        TIMESTAMP_WITH_TIMEZONE -> "NUMERIC"

        DECIMAL                 -> "REAL"
        DOUBLE                  -> "REAL"
        FLOAT                   -> "REAL"
        REAL                    -> "REAL"

        CHAR                    -> "TEXT"
        CLOB                    -> "TEXT"
        LONGNVARCHAR            -> "TEXT"
        LONGVARCHAR             -> "TEXT"
        NCHAR                   -> "TEXT"
        NCLOB                   -> "TEXT"
        NVARCHAR                -> "TEXT"
        VARCHAR                 -> "TEXT"

        else                    -> throw IllegalArgumentException("Unsupported data type $type for SQLite")
    }

    override fun isTextColumnType(type: Int) = when (type) {
        CHAR         -> true
        CLOB         -> true
        LONGNVARCHAR -> true
        LONGVARCHAR  -> true
        NCHAR        -> true
        NCLOB        -> true
        NVARCHAR     -> true
        VARCHAR      -> true
        else         -> false
    }
}