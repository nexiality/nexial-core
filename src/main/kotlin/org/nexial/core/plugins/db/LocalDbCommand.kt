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

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.DAO_PREFIX
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.CheckUtils.requiresValidVariableName
import org.nexial.core.utils.OutputFileUtils
import java.io.File
import java.sql.SQLException

/**
 * The concept of "localdb" is to maintain a database within a Nexial installation, with its data stay persisted and
 * intact across executions. It is "local" - meaning, it does not share with a remote Nexial instance and it
 * cannot be connected via a remote Nexial instance. The purpose of a local-only database can be manifold:
 * 1. store and compare data over multiple executions
 * 2. tally and summary over multiple executions
 * 3. as an intermediate query engine to further manipulate data
 * 4. data collection over discrete automation
 */
class LocalDbCommand : BaseCommand() {

    lateinit var dbName: String
    lateinit var dbFile: String
    lateinit var rdbms: RdbmsCommand
    lateinit var connectionProps: MutableMap<String, String>
    lateinit var dao: SimpleExtractionDao

    override fun getTarget() = "localdb"

    override fun init(context: ExecutionContext) {
        super.init(context)

        dbFile = context.replaceTokens(dbFile)
        File(dbFile).parentFile.mkdirs()

        initConnection()
    }

    private fun initConnection() {
        connectionProps.forEach { (key, value) -> context.setData(key, value); }
        rdbms.init(context)
        dao = rdbms.dataAccess.resolveDao(dbName)
        context.setData(DAO_PREFIX + dbName, dao)
    }

    fun purge(`var`: String): StepResult {
        requiresValidVariableName(`var`)

        try {
            dao.dataSource?.connection?.close()
        } catch (e: SQLException) {
            // probably not yet connected.. ignore
        }

        val deleted = FileUtils.deleteQuietly(File(dbFile))
        context.setData(`var`, deleted)

        initConnection()

        return StepResult(deleted,
                          "localdb ${if (deleted) "purged" else "DID NOT purged; try manual delete of localdb file"}",
                          null)
    }

    fun runSQLs(`var`: String, sqls: String): StepResult = rdbms.runSQLs(`var`, dbName, sqls)

    fun dropTables(`var`: String, tables: String): StepResult {
        requiresValidVariableName(`var`)
        requiresNotBlank(tables, "invalid table(s)", tables)

        val sqls = StringUtils.split(tables, context.textDelim).map { table -> "DROP TABLE $table;\n" }
        return rdbms.runSQLs(`var`, dbName, TextUtils.toString(sqls, "\n", "") + "VACUUM;")
    }

    fun cloneTable(`var`: String, source: String, target: String): StepResult {
        requiresValidVariableName(`var`)
        requiresNotBlank(source, "invalid source table", source)
        requiresNotBlank(target, "invalid target table", target)

        // 1. find DDL SQL for `source`
        val sql = "SELECT sql FROM SQLITE_MASTER WHERE TYPE='table' AND lower(name)='${source.toLowerCase()}';"
        val result = rdbms.dataAccess.execute(sql, dao) ?: return StepResult.fail(
                "FAILED TO DETERMINE DDL SQL for $source: no result found")

        if (result.isEmpty) return StepResult.fail("Source table '$source' not found in localdb")
        if (result.hasError()) return StepResult.fail("Source table '$source' not found in localdb: ${result.error}")

        // 2. convert to DDL for `target`
        if (result.data[0]["sql"] == null) return StepResult.fail(
                "Unable to determine the CREATE SQL for source table '$source'")

        val ddl = RegexUtils.replace(result.data[0]["sql"], "(CREATE TABLE\\s+)([A-Za-z0-9_]+)(.+)", "\$1$target\$3")

        // 3. execute
        val createResult = rdbms.runSQL(`var`, dbName, ddl)
        if (createResult.isError) return createResult
        
        // 4. copy data
        return rdbms.runSQLs(`var`, dbName, "INSERT INTO $target SELECT * FROM $source;")
    }

    fun importRecords(`var`: String, sourceDb: String, sql: String, table: String): StepResult {
        requiresValidVariableName(`var`)
        requiresNotBlank(sourceDb, "invalid source database connection name", sourceDb)
        requiresNotBlank(sql, "invalid SQL", sql)
        requiresNotBlank(table, "invalid target table name", table)

        val query = SqlComponent(OutputFileUtils.resolveRawContent(sql, context))
        if (!query.type.hasResultset()) return StepResult.fail("SQL '$sql' MUST return resultset")

        val sourceDao = rdbms.dataAccess.resolveDao(sourceDb) ?: return StepResult.fail(
                "Unable to connection to source database '$sourceDb'")

        context.setData(`var`, sourceDao.importResults(query, dao, SqliteTableSqlGenerator(table)))
        return StepResult.success("Query result successfully imported from '$sourceDb' to localdb '$table'")
    }

    fun exportCSV(sql: String, output: String): StepResult = rdbms.saveResult(dbName, sql, output)

    /*
    importJSON(var,json,table)
    importXML(var,xml,table)
    importCSV(var,csv,table)

    exportXML(sql,output)
    exportJSON(sql,output)
    */
}
