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
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Rdbms.DAO_PREFIX
import org.nexial.core.excel.ExcelConfig.MSG_SCREENCAPTURE
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.plugins.io.CsvParserBuilder
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.CheckUtils.requiresReadableFile
import org.nexial.core.utils.OutputFileUtils
import java.io.File
import java.io.StringReader
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
        requiresValidAndNotReadOnlyVariableName(`var`)

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
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(tables, "invalid table(s)", tables)

        val sqls = StringUtils.split(tables, context.textDelim).map { table -> "DROP TABLE $table;\n" }
        return rdbms.runSQLs(`var`, dbName, TextUtils.toString(sqls, "\n", "") + "VACUUM;")
    }

    fun cloneTable(`var`: String, source: String, target: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
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
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(sourceDb, "invalid source database connection name", sourceDb)
        requiresNotBlank(sql, "invalid SQL", sql)
        requiresNotBlank(table, "invalid target table name", table)

        val query = SqlComponent(OutputFileUtils.resolveRawContent(sql, context))
        if (!query.type.hasResultset()) return StepResult.fail("SQL '$sql' MUST return query result")

        val sourceDao = rdbms.dataAccess.resolveDao(sourceDb) ?: return StepResult.fail(
            "Unable to connection to source database '$sourceDb'")

        context.setData(`var`, sourceDao.importResults(query, dao, SqliteTableSqlGenerator(table)))
        return StepResult.success("Query result successfully imported from '$sourceDb' to localdb '$table'")
    }

    fun importEXCEL(`var`: String, excel: String, sheet: String, ranges: String, table: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresReadableFile(excel)
        requiresNotBlank(sheet, "invalid sheet", sheet)
        requiresNotBlank(ranges, "invalid ranges", ranges)
        requiresNotBlank(table, "invalid target table name", table)

        val buffer = StringBuilder()
        StringUtils.split(ranges, context.textDelim).forEach {
            buffer.append(context.replaceTokens("[EXCEL($excel) => read($sheet,$it) csv text]\n"))
        }

        return importCSV(`var`, buffer.toString(), table)
    }

    /**
     * import CSV content or file to {@param table}. Assumes that the first line of the {@param csv} is header.
     * @param `var` String
     * @param csv String
     * @param table String
     */
    fun importCSV(`var`: String, csv: String, table: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(csv, "invalid csv", csv)
        requiresNotBlank(table, "invalid target table name", table)

        // 1. resolve csv content
        val csvContent = OutputFileUtils.resolveContent(csv, context, false, true)
        requiresNotBlank(csvContent, "invalid csv content", csv)

        // 2. set up csv parser
        val parser = CsvParserBuilder()
            .setDelim(context.textDelim)
            .setHasHeader(true)
            .setMaxColumns(context.getIntData(CSV_MAX_COLUMNS, -1))
            .setMaxColumnWidth(context.getIntData(CSV_MAX_COLUMN_WIDTH, -1))
            .build()

        // 3. parse csv and resolve csv metadata
        val csvRecords = parser.parseAllRecords(StringReader(csvContent))
        val headers = parser.recordMetadata.headers().asList()

        val queries = StringBuilder()

        // 4. if target table not exist, create it
        val tableInfo = dao.executeSqls(SqlComponent.toList(
            "SELECT name, \"notnull\" AS 'not_null', dflt_value FROM pragma_table_info('$table') ORDER BY cid;" +
            "SELECT upper(name) || '=' || dflt_value AS 'defaults' FROM pragma_table_info('$table') WHERE dflt_value IS NOT NULL"))

        val columns = if (tableInfo.rowCount < 1) {
            // target table does not exist, let's create it
            val tableGenerator = SqliteTableSqlGenerator(table)
            queries.append(tableGenerator.generateSql(headers)).append("\n")
            TextUtils.toString(headers.map { treatColumnName(it) }, ",")
        } else {
            // target table exist, let's map out its columns
            val definedColumns = tableInfo[0].cells("name")
            if (definedColumns.size < headers.size) {
                // there are more columns in CSV than the existing table.. FAIL this
                throw IllegalArgumentException("Existing table $table has ${definedColumns.size} columns but the specified CSV has ${headers.size} columns")
            }

            val normalizedDefinedColumns = definedColumns.map { it.toLowerCase() }.sorted()
            val normalizedCsvHeaders = headers.map { it.toLowerCase() }.sorted()

            TextUtils.toString(
                if (normalizedDefinedColumns.containsAll(normalizedCsvHeaders)) {
                    // all CSV headers are found as column name in the existing table. We'll use name-matching mapping
                    headers
                } else {
                    // not all CSV headers are found in existing table as column. We'll use left-to-right mapping
                    definedColumns.subList(0, headers.size)
                }.map { treatColumnName(it) }
                , ",")
        }

        val defaultValues = if (tableInfo.rowCount < 1)
            emptyMap()
        else {
            if (tableInfo[1].rowCount < 1)
                emptyMap()
            else
                TextUtils.toMap(TextUtils.toString(tableInfo[1].cells("defaults"), ","), ",", "=")
        }

        // 5. import data via INSERT generator
        val insertPrefix = "INSERT INTO $table ($columns) VALUES ("
        csvRecords.forEach { record ->
            queries.append(insertPrefix)
            record.values.forEachIndexed { index, value ->
                val insertValue =
                        if (StringUtils.isEmpty(value)) defaultValues[headers[index].toUpperCase()] ?: value else value
                queries.append("\"$insertValue\",")
            }
            queries.delete(queries.length - 1, queries.length)
            queries.append(");\n")
        }

        // 6. execute generated queries
        val result = dao.executeSqls(SqlComponent.toList(queries.toString()))
        context.setData(`var`, result)
        return if (StringUtils.isNotBlank(result.error)) {
            if (result[0].hasError()) {
                StepResult.fail("Error occurred while creating new table '$table': ${result[0].error}")
            } else {
                StepResult.fail("Error occurred while importing CSV to '$table': ${result.error}")
            }
        } else {
            val rowsInserted = result.rowsAffected - if (result.size > csvRecords.size) result[0].rowCount else 0
            return StepResult.success("Successfully imported $rowsInserted rows from CSV to '$table'")
        }
    }

    // handle column names with spaces or commas
    private fun treatColumnName(column: String) = when {
        StringUtils.isEmpty(column)             -> "\"\""
        StringUtils.containsAny(column, " ,()") -> TextUtils.wrapIfMissing(column, "\"", "\"")
        else                                    -> column
    }

    fun exportCSV(sql: String, output: String): StepResult = rdbms.saveResult(dbName, sql, output)

    fun queryAsCSV(`var`: String, sql: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(sql, "invalid sql", sql)

        // prevent previous `var` content shown as current
        context.removeData(`var`)

        // export to CSV file
        val output = OutputFileUtils.prependRandomizedTempDirectory("$target.toCSV.csv")
        val result = rdbms.saveResult(dbName, sql, output.absolutePath)
        if (result.failed()) {
            return StepResult.fail("Unable to transform query result to CSV: " + result.message)
        }

        if (!FileUtil.isFileReadable(output, 5)) {
            return StepResult.fail("Unable to transform query result to CSV")
        }

        // then read it back as var
        context.setData(`var`, FileUtils.readFileToString(output, DEF_CHARSET))

        // before return, remove CSV file (cover our tracks)
        FileUtils.deleteDirectory(output.parentFile)

        return StepResult.success("Query result saved to $`var`")
    }

    fun exportJSON(sql: String, output: String, header: String): StepResult {
        requiresNotBlank(sql, "invalid sql", sql)
        requiresNotBlank(output, "invalid output", output)
        return postExport(dao.saveAsJSON(sql, File(output), header.toBoolean()), output)
    }

    fun exportXML(sql: String, output: String, root: String = "root", row: String = "row", cell: String = "cell"):
            StepResult {
        requiresNotBlank(sql, "invalid sql", sql)
        requiresNotBlank(output, "invalid output", output)
        return postExport(dao.saveAsXML(sql, File(output), root, row, cell), output)
    }

    fun exportEXCEL(sql: String, output: String, sheet: String = "Sheet1", start: String = "A1"): StepResult {
        requiresNotBlank(sql, "invalid sql", sql)
        requiresNotBlank(output, "invalid output", output)
        return postExport(dao.saveAsEXCEL(sql, File(output), sheet, start), output)
    }

    private fun postExport(result: JdbcResult, output: String): StepResult {
        log("exported query result in ${result.elapsedTime} ms with " +
            if (result.hasError()) "ERROR ${result.error}" else "${result.rowCount} row(s) to $output")

        return if (result.hasError()) {
            StepResult.fail("Error occurred while exporting query result to '$output'")
        } else {
            addLinkRef("${result.rowCount} row(s) exported to '$output'", MSG_SCREENCAPTURE, File(output).absolutePath)
            StepResult.success("query result exported to '$output'")
        }
    }

    /*
    importJSON(var,json,table)
    importXML(var,xml,table)
    */

    // NOT READY; NEED BETTER USECASE AND WE'LL NEED MORE TESTING ON THIS
    /*
    fun importJSON(`var`: String, json: String, jsonpath: String, table: String): StepResult {
        requiresValidVariableName(`var`)
        requiresNotBlank(jsonpath, "invalid ranges", jsonpath)
        requiresNotBlank(table, "invalid target table name", table)

        // 1. resolve json content
        val jsonContent = OutputFileUtils.resolveContent(json, context, false, true)
        requiresNotBlank(jsonContent, "invalid sheet", json)

        // 2. filter json content by `jsonpath`
        val matchContent =
                when (val jsonObject = JsonCommand.resolveToJSONObject(jsonContent)) {
                    is JSONArray  -> JSONPath.find(jsonObject, jsonpath)
                    is JSONObject -> JSONPath.find(jsonObject, jsonpath)
                    else          -> throw IllegalArgumentException("Unsupported data type ${jsonObject.javaClass.simpleName}")
                } ?: return StepResult.fail("No valid content matched against '$jsonpath' was not found")

            */
/*
         * `json` could be:
         * 1. array of arrays
         *    [
         *      [ "header1", "header2", "header3" ],
         *      [ "col1-1", "col1-2", "col1-3" ],
         *      [ "col2-1", "col2-2", "col2-3" ]
         *    ]
         * 2. array of objects
         *    [
         *      { "header1": "col1-1", "header2": "col1-2", "header3": "col1-3" },
         *      { "header1": "col2-1", "header2": "col2-2", "header3": "col2-3" },
         *      { "header1": "col3-1", "header2": "col3-2", "header3": "col3-3" }
         *    ]
         * 3. objects of array
         *    {
         *      "header": [ "header1", "header2", "header3" ],
         *      "row1": [ "col1-1", "col1-2", "col1-3" ],
         *      "row2" :[ "col2-1", "col2-2", "col2-3" ]
         *    }
         */    /*


        // 3. generate INSERT statements
        // val inserts = generateInserts(match, table)
        val inserts = when (val match = JsonCommand.resolveToJSONObject(matchContent)) {
            is JsonArray  -> generateInserts(match, table)
            is JsonObject -> generateInserts(match, table)
            else          -> throw IllegalArgumentException("JSON of type ${json.javaClass.simpleName} NOT SUPPORTED")
        }

        // 4. execute generated queries
        val result = dao.executeSqls(SqlComponent.toList(inserts))
        context.setData(`var`, result)
        return if (result.error.isNotBlank()) {
            if (result[0].hasError()) {
                StepResult.fail("Error occurred while creating new table '$table': ${result[0].error}")
            } else {
                StepResult.fail("Error occurred while importing CSV to '$table': ${result.error}")
            }
        } else {
            val rowsInserted = result.rowsAffected - result[0].rowCount
            return StepResult.success("Successfully imported $rowsInserted rows from CSV to '$table'")
        }
    }

    private fun generateInserts(json: JsonArray, table: String): String {
        if (json.size() < 1) throw IllegalArgumentException("Cannot import empty JSON array")

        val statements = StringBuilder()

        // use first object as guide: either this is array of arrays or array of objects
        return if (json[0].isJsonArray) {
            // array of arrays
            // first index is an array of headers
            val headers = json[0].asJsonArray
                          ?: throw IllegalArgumentException("No JSON object to represent the column headers")
            statements.append(SqliteTableSqlGenerator(table).generateSql(headers.map { it.asString })).append("\n")

            val columnCount = headers.size()
            json.forEachIndexed { index, array ->
                if (index != 0) statements.append(generateInsertSQL(table, array.asJsonArray, columnCount))
            }

            statements.toString()
        } else {
            // array of objects
            // first index is an object
            val firstElement = json[0].asJsonObject ?: throw IllegalArgumentException("No JSON object found")
            val headers = firstElement.keySet().map { it.toString() }
            statements.append(SqliteTableSqlGenerator(table).generateSql(headers))

            json.forEach {
                if (!it.isJsonObject) {
                    ConsoleUtils.error("Expects each node as JSON Object; unexpected: ${it.javaClass.simpleName}")
                } else {
                    val jsonObject = it.asJsonObject
                    val rowHeaders = jsonObject.keySet().map { key -> key.toString() }
                    if (headers != rowHeaders) {
                        ConsoleUtils.error("Expects same definition for all nodes; " +
                                           "current node ($rowHeaders) NOT compatible with initial node ($headers)")
                    } else {
                        val sql = StringBuilder("INSERT INTO $table VALUES (")
                        rowHeaders.forEach { header ->
                            val value = jsonObject[header]
                            if (value.isJsonPrimitive) {
                                if (value.asJsonPrimitive.isString) {
                                    sql.append("\"${value.asString}\",")
                                } else {
                                    sql.append("${value.asString},")
                                }
                            } else {
                                ConsoleUtils.error("Only simple type is accepted for data import; " +
                                                   "UNSUPPORTED TYPE: ${it.javaClass.simpleName}")
                            }
                        }

                        if (rowHeaders.size < headers.size)
                            sql.append(StringUtils.repeat(",", headers.size - rowHeaders.size))

                        statements.append(StringUtils.removeEnd(sql.toString(), ",") + ");\n")
                    }
                }
            }

            statements.toString()
        }
    }

    private fun generateInserts(json: JsonObject, table: String): String {
        if (json.size() < 1) throw IllegalArgumentException("Cannot import empty JSON")

        val statements = StringBuilder()

        // first object must be headers
        val headerKey = json.keySet().first()
        val headers = json.get(headerKey)?.asJsonArray
                      ?: throw IllegalArgumentException("No JSON object to represent the column headers")
        statements.append(SqliteTableSqlGenerator(table).generateSql(headers.map { it.asString })).append("\n")

        // the rest are expected to be array of the same length as the first
        val columnCount = headers.size()
        json.keySet().forEachIndexed { index, key ->
            if (index != 0) {
                val row = json.get(key)?.asJsonArray
                          ?: throw IllegalArgumentException("Invalid/incompatible JSON structure found in \"$key\"")
                statements.append(generateInsertSQL(table, row, columnCount))
            }
        }

        return statements.toString()
    }

    private fun generateInsertSQL(table: String, row: JsonArray, columnCount: Int): String {
        val sql = StringBuilder("INSERT INTO $table VALUES (")
        row.forEach {
            if (it.isJsonPrimitive) {
                if (it.asJsonPrimitive.isString) {
                    sql.append("\"${it.asJsonPrimitive.asString}\",")
                } else {
                    sql.append("${it.asJsonPrimitive.asString},")
                }
            } else {
                ConsoleUtils.error("Only simple type is accepted for data import; " +
                                   "UNSUPPORTED TYPE: ${it.javaClass.simpleName}")
            }
        }

        if (row.size() < columnCount) sql.append(StringUtils.repeat(",", columnCount - row.size()))

        return StringUtils.removeEnd(sql.toString(), ",") + ");\n"
    }
    */
}
