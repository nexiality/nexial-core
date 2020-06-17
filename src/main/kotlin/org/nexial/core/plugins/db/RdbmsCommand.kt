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

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Rdbms.CSV_ROW_SEP
import org.nexial.core.NexialConst.Rdbms.DAO_PREFIX
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.OutputFileUtils
import java.io.File
import java.io.File.separator
import java.io.IOException

class RdbmsCommand : BaseCommand() {

    private val maxSqlDisplayLength = 200

    lateinit var dataAccess: DataAccess

    override fun init(context: ExecutionContext) {
        super.init(context)
        dataAccess.setContext(context)
    }

    override fun getTarget() = "rdbms"

    @Throws(IOException::class)
    fun runSQL(`var`: String, db: String, sql: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(db, "invalid db", db)
        requiresNotBlank(sql, "invalid sql", sql)

        // remove any trailing semi-colon, since single query should not have it
        val query = context.replaceTokens(OutputFileUtils.resolveRawContent(sql, context).trim()).removeSuffix(";")
        // we want to support ALL types of SQL, including those vendor-specific
        // so for that reason, we are no longer insisting on the use of standard ANSI sql
        // requires(dataAccess.validSQL(query), "invalid sql", sql);

        try {
            val result = dataAccess.execute(query, resolveDao(db)) ?:
                         return StepResult.fail("FAILED TO EXECUTE SQL '$sql': no result found")
            context.setData(`var`, result)

            log("executed query in ${result.elapsedTime} ms with " +
                if (result.hasError()) "ERROR ${result.error}" else "${result.rowCount} row(s)")
        } finally {
            // done with connection... probably good idea to remove mongo-specific trust store to avoid SSL issue elsewhere
            System.clearProperty("javax.net.ssl.trustStore")
            System.clearProperty("javax.net.ssl.trustStorePassword")
        }

        return StepResult.success("executed SQL '$sql'; stored result as \${$`var`}")
    }

    /**
     * execution one or more queries, which may be specified either as a series of SQL or as a file
     * (containing a series of SQL).
     */
    fun runSQLs(`var`: String, db: String, sqls: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(db, "invalid db", db)
        requiresNotBlank(sqls, "invalid sql statement(s)", sqls)

        var msgPrefix = "executing SQLs"

        try {
            val qualifiedSqlList = SqlComponent.toList(OutputFileUtils.resolveRawContent(sqls, context))
            requires(CollectionUtils.isNotEmpty(qualifiedSqlList), "No valid SQL statements found", sqls)

            val qualifiedSqlCount = qualifiedSqlList.size
            if (!db.startsWith(NAMESPACE)) log("found $qualifiedSqlCount qualified query(s) to execute")

            msgPrefix = "executed $qualifiedSqlCount SQL(s);"

            val outcome = executeSQLs(db, qualifiedSqlList)

            if (CollectionUtils.isNotEmpty(outcome)) {
                context.setData(`var`, outcome)
                if (MapUtils.isNotEmpty(outcome.namedOutcome)) {
                    outcome.namedOutcome.forEach { (name, result) -> context.setData(name, result) }
                    return StepResult.success("$msgPrefix result saved as ${outcome.namedOutcome.keys}")
                }

                if (outcome.size == 1) {
                    context.setData(`var`, outcome[0])
                    return StepResult.success("$msgPrefix single result stored as \${$`var`}")
                }

                return StepResult.success("$msgPrefix ${outcome.size} results stored as \${$`var`}")
            }

            return StepResult.success("${msgPrefix}no result saved")
        } catch (e: Exception) {
            return StepResult.fail("FAIL $msgPrefix: ${e.message}")
        } finally {
            // done with connection... probably good idea to remove mongo-specific trust store to avoid SSL issue elsewhere
            System.clearProperty("javax.net.ssl.trustStore")
            System.clearProperty("javax.net.ssl.trustStorePassword")
        }
    }

    /**
     * todo: need to deprecate since runSQLs does the same thing
     */
    fun runFile(`var`: String, db: String, file: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(db, "invalid db", db)
        requiresReadableFile(file)

        return runSQLs(`var`, db, file)
    }

    @Throws(IOException::class)
    fun resultToCSV(`var`: String, csvFile: String, delim: String, showHeader: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        requiresNotBlank(csvFile, "invalid target CSV file", csvFile)

        val delimiter = if (StringUtils.isBlank(delim)) context.textDelim else delim
        val printHeader = BooleanUtils.toBoolean(showHeader)

        val resultObj = context.getObjectData(`var`)
        requiresNotNull(resultObj, "specified variable not found", `var`)

        // variable could be:
        // (1) exception text           --> error occurred when running stored procedure
        // (2) number                   --> rows affected by previous SQL
        // (3) List<Map<String,String>> --> "rows of map of name and value" by previous SELECT sql

        val csv = File(csvFile)

        if (resultObj is String) {
            if (StringUtils.isEmpty(resultObj as String?)) {
                return StepResult.success("No result found from previous command; no CSV file generated")
            }

            FileUtils.write(csv, resultObj as String?, DEF_CHARSET, false)
            return StepResult.success("error from previous command written to '$csvFile'")
        }

        if (resultObj is JdbcResult) {

            val result = resultObj as JdbcResult?
            if (CollectionUtils.isNotEmpty(result!!.getData())) {
                if (CollectionUtils.isEmpty(result.columns)) {
                    // no column names means no result (empty list)
                    return StepResult.success("No result found from previous command; no CSV file generated")
                }

                val csvContent = toCSV(result, delimiter, printHeader)
                FileUtils.writeStringToFile(csv, csvContent, DEF_CHARSET, false)
                return StepResult.success("result found in previous db.run*() command saved to '$csvFile'")
            }

            if (result.rowCount > 0) {
                FileUtils.write(csv, result.rowCount.toString() + "", DEF_CHARSET, false)
                return StepResult.success("affected row count from previous command written to '$csvFile'")
            }

            if (result.hasError()) {
                FileUtils.write(csv, "ERROR: " + result.error, DEF_CHARSET, false)
                return StepResult.success("errors from previous command written to '$csvFile'")
            }
        }

        return StepResult.fail("Unknown type found for variable '$`var`': ${resultObj!!.javaClass.name}")
    }

    /**
     * execute multiple SQL statements and save the corresponding output (as CSV) to the specific `outputDir`
     * directory. Note that only SQL with matching Nexial variable will result in its associated output saved to the
     * specific `outputDir` directory - the associate variable name will be used as the output CSV file name.
     */
    fun saveResults(db: String, sqls: String, outputDir: String): StepResult {
        requiresNotBlank(db, "invalid db", db)
        requiresNotBlank(sqls, "invalid sql", sqls)

        val outDir = StringUtils.trim(outputDir)
        requiresReadableDirectory(outDir, "invalid output directory", outDir)

        val dao = resolveDao(db)
        var msgPrefix = "executing SQLs"

        try {
            val qualifiedSqlList = SqlComponent.toList(OutputFileUtils.resolveRawContent(sqls, context))
            requires(CollectionUtils.isNotEmpty(qualifiedSqlList), "No valid SQL statements found", sqls)

            val qualifiedSqlCount = qualifiedSqlList.size
            if (!db.startsWith(NAMESPACE)) log("found $qualifiedSqlCount qualified query(s) to execute")

            msgPrefix = "executed $qualifiedSqlCount SQL(s);"
            // val testStep = context.currentTestStep

            for (sqlComponent in qualifiedSqlList) {
                val sql = context.replaceTokens(StringUtils.trim(sqlComponent.sql))
                val printableSql = if (StringUtils.length(sql) > maxSqlDisplayLength)
                    StringUtils.right(sql, maxSqlDisplayLength) + "..."
                else
                    sql

                if (StringUtils.isNotBlank(sqlComponent.varName)) {
                    val varName = context.replaceTokens(sqlComponent.varName)
                    val outFile = StringUtils.appendIfMissing(OutputFileUtils.webFriendly(varName), ".csv")
                    val targetFile = File(StringUtils.appendIfMissing(File(outDir).absolutePath, separator) + outFile)
                    if (!handleResult(dao, sql, targetFile))
                        return StepResult.fail("FAILED TO EXECUTE SQL '$printableSql': no result")
                } else {
                    // not saving result anywhere since this SQL is not mapped to any variable
                    log("executing $printableSql without saving its result")
                    dao.executeSql(sql, null)
                }
            }

            return StepResult.success("$msgPrefix output saved to $outDir")
        } catch (e: Exception) {
            return StepResult.fail("FAIL $msgPrefix: ${e.message}")
        } finally {
            // done with connection... probably good idea to remove mongo-specific trust store to avoid SSL issue elsewhere
            System.clearProperty("javax.net.ssl.trustStore")
            System.clearProperty("javax.net.ssl.trustStorePassword")
        }
    }

    fun saveResult(db: String, sql: String, output: String): StepResult {
        requiresNotBlank(db, "invalid db", db)
        requiresNotBlank(sql, "invalid sql", sql)
        requiresNotBlank(output, "invalid output", output)

        val outputFile = if (!StringUtils.endsWithIgnoreCase(output, ".csv")) "$output.csv" else output
        val targetFile = File(outputFile)
        val dao = resolveDao(db)

        try {
            val query = context.replaceTokens(StringUtils.trim(OutputFileUtils.resolveRawContent(sql, context)))
            // we want to support ALL types of SQL, including those vendor-specific
            // so for that reason, we are no longer insisting on the use of standard ANSI sql
            // requires(dataAccess.validSQL(query), "invalid sql", sql);

            if (!handleResult(dao, query, targetFile)) return StepResult.fail("FAILED TO EXECUTE SQL '$sql': no result")
            return StepResult.success("executed SQL '$sql'; stored result to '$outputFile'")
        } catch (e: IOException) {
            return StepResult.fail("Error when executing '$sql': ${e.message}")
        } finally {
            // done with connection... probably good idea to remove mongo-specific trust store to avoid SSL issue elsewhere
            System.clearProperty("javax.net.ssl.trustStore")
            System.clearProperty("javax.net.ssl.trustStorePassword")
        }
    }

    private fun handleResult(dao: SimpleExtractionDao, query: String?, saveTo: File): Boolean {
        val result = dataAccess.execute(query, dao, saveTo) ?: return false

        val displaySql = if (StringUtils.length(query) > maxSqlDisplayLength)
            StringUtils.right(query, maxSqlDisplayLength) + "..."
        else
            query

        log("executed $displaySql in ${result.elapsedTime} ms with " +
            if (result.hasError()) "ERROR ${result.error}" else "${result.rowCount} row(s)")

        val jsonOutput = resultToJson(result, StringUtils.substringBeforeLast(saveTo.absolutePath, ".") + ".json")
        if (FileUtil.isFileReadable(jsonOutput, 3)) {
            addLinkRef("result metadata saved as ${jsonOutput.name}",
                       jsonOutput.name,
                       jsonOutput.absolutePath)
        }

        // csv file will get the `nexial.lastOutputLink` ref, not json file
        if (FileUtil.isFileReadable(saveTo, 3)) {
            addLinkRef("result saved as ${saveTo.name}", saveTo.name, saveTo.absolutePath)
            return true
        }

        return false
    }

    fun executeSQLs(db: String, sqls: List<SqlComponent>): JdbcOutcome = resolveDao(db).executeSqls(sqls)

    fun toCSV(result: JdbcResult, delim: String, printHeader: Boolean): String {
        // test along way... good luck
        val sb = StringBuilder()

        // save header
        if (printHeader) sb.append(TextUtils.toString(result.columns, delim)).append(CSV_ROW_SEP)

        for (row in result.getData()) sb.append(rowToString(row, result.columns, delim)).append(CSV_ROW_SEP)

        return sb.toString()
    }

    private fun resolveDao(db: String): SimpleExtractionDao {
        val dbKey = DAO_PREFIX + db
        if (context.hasData(dbKey)) {
            val daoObject = context.getObjectData(dbKey)
            if (daoObject is SimpleExtractionDao) {
                if (!db.startsWith(NAMESPACE)) log("reusing established connection '$db'")
                return daoObject
            }

            // nope.. wrong type - toss it away
            context.removeData(dbKey)
        }

        val dao = dataAccess.resolveDao(db)
        context.setData(dbKey, dao)
        if (!db.startsWith(NAMESPACE)) log("new connection established for '$db'")

        return dao
    }

    fun rowToString(row: Map<String, String>, columnNames: List<String>, delim: String): String {
        val sb = StringBuilder()
        for (columnName in columnNames) {
            var value = row[columnName]
            if (StringUtils.contains(value, "\"")) value = StringUtils.replace(value, "\"", "\"\"")
            if (StringUtils.contains(value, delim)) value = TextUtils.wrapIfMissing(value, "\"", "\"")
            sb.append(value).append(delim)
        }

        return StringUtils.removeEnd(sb.toString(), delim)
    }

    @Throws(IOException::class)
    private fun resultToJson(result: JdbcResult, output: String): File {
        val file = File(output)
        FileUtils.writeStringToFile(file, GSON.toJson(result), DEF_FILE_ENCODING)
        return file
    }
}
