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

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.TextUtils
import org.nexial.core.excel.Excel
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.model.ExecutionContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.sql.ResultSet
import java.sql.Types
import java.util.*

/**
 * general contract to export SQL query result to a structured file format
 */
interface QueryResultExporter {

    fun export(rs: ResultSet, result: JdbcResult, output: File): JdbcResult

    fun prepOutput(output: File) = output.parentFile?.mkdirs()
}

class CsvExporter(val context: ExecutionContext, val nullValue: String, val header: Boolean = true) :
        QueryResultExporter {

    override fun export(rs: ResultSet, result: JdbcResult, output: File): JdbcResult {
        if (!rs.next()) return result

        val metaData = rs.metaData
        val columnCount = metaData.columnCount
        val recordDelim = "\n"
        val delim = context.textDelim
        var rowCount = 0

        val columns = ArrayList<String>()
        for (i in 1..columnCount) columns.add(DaoUtils.csvFriendly(metaData.getColumnLabel(i), delim, true))
        result.columns = columns

        prepOutput(output)

        try {
            BufferedOutputStream(FileOutputStream(output)).use { out ->

                // construct header
                if (header) out.write((TextUtils.toString(columns, delim) + recordDelim).toByteArray())

                // recycle through all rows
                do {
                    val row = StringBuilder()
                    for (i in 1..columnCount) {
                        row.append(DaoUtils.csvFriendly(rs.getString(i), delim, true, nullValue)).append(delim)
                    }

                    rowCount++
                    out.write((StringUtils.removeEnd(row.toString(), delim) + recordDelim).toByteArray())
                } while (rs.next())

                result.setRowCount(rowCount)
                out.flush()
                out.close()
            }
        } catch (e: Throwable) {
            result.setError(e.message)
        }

        return result
    }
}

internal val GSON_SERIALIZE_NULL = GsonBuilder().setPrettyPrinting()
    .disableHtmlEscaping()
    .disableInnerClassSerialization()
    .setLenient()
    .serializeNulls()
    .create()

class JsonExporter(private val context: ExecutionContext,
                   private val nullValue: String,
                   private val header: Boolean = true) : QueryResultExporter {

    override fun export(rs: ResultSet, result: JdbcResult, output: File): JdbcResult {
        if (!rs.next()) return result

        val delim = context.textDelim

        val columns = mutableListOf<String>()
        val columnsAsString = mutableListOf<Boolean>()
        val metaData = rs.metaData
        val columnCount = metaData.columnCount
        for (i in 1..columnCount) {
            columns.add(DaoUtils.csvFriendly(metaData.getColumnLabel(i), delim, true))
            columnsAsString.add(isCharacterSQLType(metaData.getColumnType(i)))
        }
        result.columns = columns

        // recycle through all rows
        var rowCount = 0
        val jsonArray = JsonArray()
        do {
            if (header) {
                val oneRow = JsonObject()
                for (i in 1..columnCount) {
                    if (columnsAsString[i - 1]) {
                        oneRow.addProperty(columns[i - 1], rs.getString(i) ?: nullValue)
                    } else {
                        oneRow.addProperty(columns[i - 1], rs.getBigDecimal(i) ?: null)
                    }
                }
                jsonArray.add(oneRow)
            } else {
                val oneRow = JsonArray()
                for (i in 1..columnCount) {
                    if (columnsAsString[i - 1]) {
                        oneRow.add(rs.getString(i) ?: nullValue)
                    } else {
                        oneRow.add(rs.getBigDecimal(i) ?: null)
                    }
                }
                jsonArray.add(oneRow)
            }
            rowCount++
        } while (rs.next())

        result.setRowCount(rowCount)

        prepOutput(output)

        try {
            BufferedOutputStream(FileOutputStream(output)).use {
                it.write(GSON_SERIALIZE_NULL.toJson(jsonArray).toByteArray())
            }
        } catch (e: Throwable) {
            result.setError(e.message)
        }

        return result
    }

    private fun isCharacterSQLType(type: Int): Boolean {
        return type == Types.VARCHAR || type == Types.CHAR || type == Types.LONGNVARCHAR ||
               type == Types.CLOB || type == Types.NCLOB ||
               type == Types.NCHAR || type == Types.NVARCHAR || type == Types.LONGVARCHAR ||
               type == Types.DATE || type == Types.TIME || type == Types.TIME_WITH_TIMEZONE ||
               type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE
    }
}

class XmlExporter(private val context: ExecutionContext,
                  private val nullValue: String,
                  private val root: String = "rows",
                  private val row: String = "row",
                  private val cell: String = "cell") : QueryResultExporter {

    override fun export(rs: ResultSet, result: JdbcResult, output: File): JdbcResult {
        if (!rs.next()) return result

        val metaData = rs.metaData
        val columnCount = metaData.columnCount
        val delim = context.textDelim
        var rowCount = 0

        val headers = ArrayList<String>()
        for (i in 1..columnCount) headers.add(DaoUtils.csvFriendly(metaData.getColumnLabel(i), delim, true))
        result.columns = headers

        prepOutput(output)

        try {
            BufferedOutputStream(FileOutputStream(output)).use { out ->

                // root
                out.write("<$root>\n".toByteArray())

                // recycle through all rows
                do {
                    out.write("\t<$row>\n".toByteArray())

                    for (i in 1..columnCount) {
                        val data = rs.getString(i) ?: nullValue
                        out.write(("\t\t<$cell index=\"$i\" name=\"${headers[i - 1]}\">$data</$cell>\n").toByteArray())
                    }

                    out.write("\t</$row>\n".toByteArray())
                    rowCount++
                } while (rs.next())

                result.setRowCount(rowCount)

                out.write("</$root>\n".toByteArray())
                // out.flush()
                // out.close()
            }
        } catch (e: Throwable) {
            result.setError(e.message)
        }

        return result
    }
}

class ExcelExporter(private val nullValue: String,
                    private val header: Boolean = true,
                    private val sheet: String = "Sheet1",
                    private val startAddress: ExcelAddress = ExcelAddress("A1")) : QueryResultExporter {

    override fun export(rs: ResultSet, result: JdbcResult, output: File): JdbcResult {
        if (!rs.next()) return result

        val metaData = rs.metaData
        val columnCount = metaData.columnCount
        var rowCount = 0

        val columns = ArrayList<String>()
        for (i in 1..columnCount) columns.add(metaData.getColumnLabel(i))
        result.columns = columns

        prepOutput(output)

        val excel = if (FileUtil.isFileReadable(output)) Excel(output, false, false) else Excel.newExcel(output)
        val worksheet = excel.worksheet(sheet, true)
        val addr = startAddress

        // construct header
        if (header) {
            worksheet.writeAcross(addr, listOf<List<String>>(columns))
            addr.advanceRow()
        }

        // recycle through all rows
        do {
            val row = (1..columnCount).map { rs.getString(it) ?: nullValue }
            // val row = mutableListOf<String>()
            // for (i in 1..columnCount) row += rs.getString(i) ?: nullValue
            // worksheet.writeAcross(addr, listOf<List<String>>(row))

            worksheet.writeAcross(addr, listOf(row))
            addr.advanceRow()
            rowCount++
        } while (rs.next())

        // worksheet.save()
        excel.save()
        excel.close()

        result.setRowCount(rowCount)

        return result
    }
}

