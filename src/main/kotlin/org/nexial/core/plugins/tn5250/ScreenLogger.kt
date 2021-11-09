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

package org.nexial.core.plugins.tn5250

import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.DateUtility.getCurrentTimestampForLogging
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ServiceProfile
import org.nexial.core.utils.ConsoleUtils
import java.io.File

class ScreenLogger(val context: ExecutionContext, private val currentProfile: String) {
    private val lineWidth = 80
    private val labelWidth = 38

    private val sectionBreak = "\n"
    private val topBorder = StringUtils.repeat('.', lineWidth) + "\n"
    private val bottomBorder = topBorder
    private val logBottomBorder = "\n\\" + StringUtils.repeat('-', lineWidth - 2) + "/\n"

    private val headerText = newSectionHeader("~~~ SCREEN ~~~")
    private val headerTitle = newSectionHeader("~~~ TITLE ~~~")
    private val headerDisplayFields = newSectionHeader("~~~ DISPLAY FIELDS ~~~")
    private val headerInputFields = newSectionHeader("~~~ INPUT FIELDS ~~~")
    private val headerTable = newSectionHeader("~~~ TABLE ~~~")
    private val headerMessage = newSectionHeader("~~~ MESSAGE ~~~")

    fun log(screenObject: ScreenObject) {
        val profile = currentProfile
        val profiler = ServiceProfile.resolve(context, profile, Tn5250ServiceProfile::class.java)
        if (!profiler.logInspection) return

        val buffer = StringBuilder(newLogHeader(context.currentTestStep.messageId)).append(sectionBreak)

        val screenText = screenObject.text
        if (StringUtils.isNotBlank(screenText)) buffer.append(headerText).append(screenText).append(sectionBreak)

        val title = screenObject.title()
        if (StringUtils.isNotEmpty(title))
            buffer.append(headerTitle).append(StringUtils.trim(title)).append(sectionBreak)

        val readOnlyFields = screenObject.roFields
        if (MapUtils.isNotEmpty(readOnlyFields)) {
            buffer.append(headerDisplayFields)
                .append(readOnlyFields.map { (label, display) -> createLogLabel(label) + display }.joinToString("\n"))
                .append(sectionBreak)
        }

        val inputMeta = screenObject.fieldMetaMap
        if (MapUtils.isNotEmpty(inputMeta)) {
            buffer.append(headerInputFields)
                .append(inputMeta.filterValues { it.field != null }.map { (label, meta) ->
                    val field = meta.field!!
                    createLogLabel(label) +
                    "row=" + field.startRow() +
                    ", column=" + field.startCol() +
                    ", length=" + field.length +
                    ", text=" + field.string
                }.joinToString("\n"))
                .append(sectionBreak)
        }

        val table = screenObject.table
        if (table != null) {
            val columnWidths = table.columnSpecs.map { spec -> spec.second - spec.first }
            val rowBreak = StringUtils.repeat("-", columnWidths.foldRight(0) { width, total -> total + width } +
                                                   columnWidths.size + 1)

            buffer.append(headerTable)
                .append(createLogLabel("Columns")).append(table.columnSpecs.joinToString(separator = ",")).append("\n")
                .append("|")
                .append(table.headers.mapIndexed { i, header -> StringUtils.rightPad(header, columnWidths[i]) }
                            .joinToString("|"))
                .append("|\n")
                .append(rowBreak).append("\n")
                .append(table.data.joinToString("\n") { row ->
                    "|" +
                    row.mapIndexed { i, cell -> StringUtils.rightPad(cell, columnWidths[i]) }.joinToString("|") +
                    "|\n" +
                    rowBreak
                })
                .append(sectionBreak)
        }

        if (screenObject is FullScreenObject) {
            val message = screenObject.message
            if (StringUtils.isNotBlank(message)) buffer.append(headerMessage).append(message).append(sectionBreak)
        }

        buffer.append(sectionBreak).append(sectionBreak).append(sectionBreak)

        val logFile = File(profiler.inspectionLogFile
                           ?: context.replaceTokens("$(syspath|log|fullpath)/${currentProfile}.inspection.log"))
        FileUtils.write(logFile, buffer.toString(), DEF_FILE_ENCODING, true)
    }

    private fun newSectionHeader(header: String) =
        topBorder + "::" + ConsoleUtils.centerPrompt(header, lineWidth - 4) + "::\n" + bottomBorder

    private fun newLogHeader(header: String) =
        "/-" + StringUtils.rightPad("[" + getCurrentTimestampForLogging() + "]", lineWidth - 3, '-') + "\\\n" +
        "| " + StringUtils.rightPad(header, lineWidth - 4) + " |" +
        logBottomBorder

    private fun createLogLabel(label: String) = " > " + StringUtils.rightPad(label, labelWidth - 5) + ": "
}