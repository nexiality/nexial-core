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

package org.nexial.core.model

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.plugins.io.CsvParserBuilder
import java.io.File

class WebServiceLogs(val summary: WebServiceSummaryLog?, val details: List<WebServiceDetailLog>)

class WebServiceSummaryLog(val file: File) {
    private val rowIdPosition = 3
    private val ttfbPosition = 9
    private val elapsedTimePosition = 10
    private val responseLengthPosition = 11

    val filename: String = file.name
    val records: List<Array<String>>
    val total: Int
    val ttfbMax: Long
    val ttfbMin: Long
    val ttfbAverage: Long
    val elapsedMax: Long
    val elapsedMin: Long
    val elapsedAverage: Long
    val responseLengthMax: Long
    val responseLengthMin: Long
    val responseLengthAverage: Long

    init {
        val parser = CsvParserBuilder()
            .setHasHeader(true)
            .setDelim(",")
            .setTrimValue(true)
            .setLineSeparator("\n")
            .build()
        records = parser.parseAll(file)
        records.forEach { row -> row[rowIdPosition] = StringUtils.removeStart(row[rowIdPosition], "Row ") }

        total = records.size

        val ttfbList = records.map { row -> row[ttfbPosition] }.map { it.toLong() }
        ttfbMax = ttfbList.maxOrNull() ?: 0L
        ttfbMin = ttfbList.minOrNull() ?: 0L
        ttfbAverage = ttfbList.sum() / total

        val elapsedList = records.map { row -> row[elapsedTimePosition] }.map { it.toLong() }
        elapsedMax = elapsedList.maxOrNull() ?: 0L
        elapsedMin = elapsedList.minOrNull() ?: 0L
        elapsedAverage = elapsedList.sum() / total

        val responseLengths = records.map { row -> row[responseLengthPosition] }.map { it.toLong() }
        responseLengthMax = responseLengths.maxOrNull() ?: 0L
        responseLengthMin = responseLengths.minOrNull() ?: 0L
        responseLengthAverage = responseLengths.sum() / total
    }
}

class WebServiceDetailLog(
    val invokeFrom: String, // which script invoke this WS call
    val script: String,  // just script name, no runID or extension
    val scenario: String,
    val iteration: String,
    val row: String,
    val repeatUntil: String,
    val file: File, // the log file itself
) {
    val content: String = FileUtils.readFileToString(file, DEF_FILE_ENCODING)
    val url: String
    val method: String
    val returnCode: Int

    init {
        val lines = content.split("\n")
        this.url = extractValue(lines, "Request URL")
        this.method = extractValue(lines, "Request Method")
        this.returnCode = extractValue(lines, "Return Code").toInt()
    }

    private fun extractValue(lines: List<String>, startWith: String) =
        lines.find { it.startsWith(startWith) }
            .orEmpty()
            .substringAfter(startWith).trim()
            .substringAfter(":").trim()
}
