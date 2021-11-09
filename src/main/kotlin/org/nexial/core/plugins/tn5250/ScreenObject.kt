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

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.CollectionUtil
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.SystemVariables.getDefaultBool
import org.nexial.core.model.ExecutionContext
import org.nexial.core.plugins.tn5250.KeyTranslator.translateKeyMnemonics
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.CONTENT_COLORS
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.cleanScreenLabel
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.cleanScreenText
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.filterCharsByColor
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.nextReadablePosition
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.waitForScreenToStabilize
import org.nexial.core.plugins.tn5250.Tn5250Command.Companion.resolveTitleLineConfig
import org.nexial.core.plugins.tn5250.Tn5250Helper.ATTR_GREEN_READ_ONLY
import org.nexial.core.plugins.tn5250.Tn5250Helper.DEF_TITLE_LINES
import org.nexial.core.plugins.tn5250.Tn5250Helper.DISMISS_BROADCAST
import org.nexial.core.plugins.tn5250.Tn5250Helper.REGEX_LABEL
import org.nexial.core.plugins.tn5250.Tn5250Helper.REGEX_LABEL2
import org.nexial.core.plugins.tn5250.Tn5250Helper.REGEX_LABEL3
import org.nexial.core.plugins.tn5250.Tn5250Helper.REGEX_LABEL_START
import org.nexial.core.plugins.tn5250.Tn5250Helper.SCAN_BROADCAST_TEXT
import org.nexial.core.plugins.tn5250.Tn5250Helper.SCAN_BROADCAST_TITLE
import org.nexial.core.plugins.tn5250.Tn5250Helper.USE_FIRST_TABLE
import org.nexial.core.utils.ConsoleUtils
import org.tn5250j.TN5250jConstants.*
import org.tn5250j.framework.tn5250.Screen5250
import org.tn5250j.framework.tn5250.ScreenField
import org.tn5250j.framework.tn5250.ScreenFields
import org.tn5250j.framework.tn5250.ScreenOIA
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/**
 * `text` is a var because we would probably need to change its content after `scan()`
 */
open class ScreenObject(var text: String,
                        private val rowRange: IntRange,
                        private val columnRange: IntRange,
                        private val titles: List<String>,
                        private val config: ScreenConfig) {

    protected val fieldMap = mutableMapOf<Int, MutableList<ScreenField>>()
    val fieldMetaMap = mutableMapOf<String, FieldMeta>()
    internal val roFields = mutableMapOf<String, String>()

    /** supports Nexial's data variable reference syntax, as in ${SIS.screen}.titleLine[1] */
    fun titleLine(line: String) = titleLine(line.toInt())
    fun titleLine(line: Int) = titles[line]
    fun titleLines() = titles
    fun title() = titles.joinToString("\n")

    var inputFieldCount = 0
        protected set

    var table: ScreenTable? = null
        protected set

    fun inputFieldExists(label: String) = fieldMetaMap.containsKey(label) && fieldMetaMap[label]?.readOnly == false
    fun field(label: String) = fieldMetaMap[label]?.field
    fun inputFields() = fieldMetaMap.keys.toList()
    private fun addField(label: String, field: ScreenField?, index: Int = 0) {
        if (field != null && !field.isBypassField)
            fieldMetaMap["$label${if (index == 0) "" else "@$index"}"] = FieldMeta(field)
    }

    fun content() = roFields
    fun contentFields() = roFields.keys.toList()

    // fun labelExists(label: String) = content.containsKey(label)

    fun fieldExists(label: String) = roFields.containsKey(label) || fieldMetaMap.containsKey(label)

    // preference given to r/o field (if exist)... use `scanScreen()` to get new values
    fun fieldValue(label: String) = when {
        roFields.containsKey(label)     -> roFields[label]
        fieldMetaMap.containsKey(label) -> fieldMetaMap[label]?.text
        else                            -> null
    }

    private fun isInContentRange(i: Int) = i >= config.titleLines && i <= (rowRange.last - 2)

    internal fun scan(screen: Screen5250) {
        // --------------------------------------------------------------------------------
        // PREP: clear read-only fields, create printable text, determine dual pane layout
        // --------------------------------------------------------------------------------
        // all read-only fields are removed since we aren't interactively modified them
        var remainingInputField = 0
        fieldMap.entries.removeIf {
            val fields = it.value
            fields.removeAll { field -> field.attr == ATTR_GREEN_READ_ONLY }
            remainingInputField += fields.size
            fields.isEmpty()
        }
        inputFieldCount = remainingInputField

        // contain the merged content (green text + title)
        val newContent = mutableListOf<String>()
        val textLines = ArrayList<String>(rowRange.last)
        var dualPaneLineCount = 0
        val tableBound = intArrayOf(0, 0)

        for (i in 0..rowRange.first) textLines += ""
        for (i in rowRange) {
            // scan the entire line but not BLACK "hidden" text
            newContent.add(filterCharsByColor(screen, i, columnRange, NON_HIDDEN_TEXT_COLORS)
                               .map { if (it == 0.toChar()) ' ' else it }
                               .joinToString(separator = "")
                               .removePrefix(" "))

            // we are scanning only the "green" text (ie. content)
            val textLine = String(filterCharsByColor(screen, i, columnRange, CONTENT_COLORS))
            textLines.add(i, textLine)

            // auto detecting dual pane layout
            dualPaneLineCount += if (isDualPane(textLine)) 1 else 0
        }

        // text regenerated based on merging of green text and title, and removal of "hidden" text
        text = newContent.joinToString(separator = "\n")

        // if 30% of the non-blank lines "looks" like dual-pane, then we'll say that the screen is dual-pane
        val isDualPaneScreen =
            (dualPaneLineCount * 1.0 / textLines.filter { StringUtils.trim(it) != "" }.size) * 100.0 >= 30

        // map fields onto screen: each row may have zero to many fields

        var inTable = false
        val favorFirstTable = config.favorFirstTable

        // evaluate through each row for read-only fields and input fields
        var currentContentLabel: String
        var unmatchedLabel = ""

        // table headers are processed separately and should be skipped here
        val skipLines = mutableListOf<Int>()

        for (i in rowRange) {
            if (skipLines.contains(i)) continue

            currentContentLabel = ""

            // we are scanning only the "green" text (ie. content)
            val textLine = textLines[i]
            val isEmptyTextLine = StringUtils.isBlank(StringUtils.trim(textLine))

            // could be table header? only consider after title section and not as the last screen row (message section)
            if (mightBeTableHeader(textLine, i)) {
                val headerLine = String(filterCharsByAttr(screen, i, columnRange, TABLE_HEADER_ATTRS))
                val headerContent = StringUtils.trim(headerLine)

                if (isIgnorableTableHeader(screen, headerContent)) {
                    if (table != null) {
                        tableBound[1] = i - 1
                        table!!.rowRange = IntRange(tableBound[0], tableBound[1])
                        table!!.columnRange = columnRange
                    }
                    inTable = false
                    continue
                }

                if (StringUtils.isNotBlank(headerContent)) {
                    // this means there are more than 1 table in current screen...
                    if (table != null) {
                        // throw RuntimeException("multiple tables in single TN5250 screen is not yet supported")
                        if (favorFirstTable) {
                            ConsoleUtils.log("Possibly multiple tables detected in current TN5250 screen; " +
                                             "multi-table currently not supported")
                            tableBound[1] = i - 1
                            table!!.rowRange = IntRange(tableBound[0], tableBound[1])
                            table!!.columnRange = columnRange
                            inTable = false
                        } else {
                            val table2 = newTable(screen, headerLine, i + 1 until rowRange.last, skipLines)

                            // wait... we might have scanned in a line that is really not a table header (no column scanned)
                            if (table2.columnCount() < 2) {
                                // 1 column is like no columns... thus this is not really a table header!
                                inTable = false
                            } else {
                                // enable scan-table mode; from this point we are looking for grid data until blank line is reached
                                table = table2
                                inTable = true
                            }
                        }
                    } else {
                        table = newTable(screen, headerLine, i + 1 until rowRange.last, skipLines)

                        // wait... we might have scanned in a line that is really not a table header (no column scanned)
                        if (table!!.columnCount() < 2) {
                            // 1 column is like no columns... thus this is not really a table header!
                            table = null
                            inTable = false
                        } else {
                            // enable scan-table mode; from this point we are looking for grid data until blank line is reached
                            inTable = true
                        }
                    }
                    continue
                } else {
                    // no table header nor text content... maybe it's a blank line (i.e. table section ended)
                    // scan-table mode ended
                    if (inTable) {
                        if (table != null) {
                            tableBound[1] = i - 1
                            table!!.rowRange = IntRange(tableBound[0], tableBound[1])
                            table!!.columnRange = columnRange
                        }
                        inTable = false
                    }
                }
            }

            if (inTable && table != null) {
                // handle edge condition
                if (!isEmptyTextLine && isInContentRange(i)) {
                    // first row of the data row in current table
                    if (table!!.data.isEmpty()) tableBound[0] = i
                    table!!.addData(textLine, fieldMap[i])
                    continue
                }

                // last row of the data row in current table
                tableBound[1] = i - 1
                table!!.rowRange = IntRange(tableBound[0], tableBound[1])
                table!!.columnRange = columnRange
                inTable = false
            }

            // out of scan-table mode
            if (isEmptyTextLine) continue

            extractReadOnlyFields(textLine, if (isDualPaneScreen) 2 else 1)?.forEach {
                this.roFields[resolveUniqueContentLabel(it.first, 0)] = cleanScreenText(it.second)
            }

            // at this point, we know that we have fields on this row
            val rowFields = fieldMap[i] ?: continue
            // no fields? then skip this line
            if (rowFields.isEmpty()) continue

            // try for input field(s)
            // analysis fields:
            //  if a field is the only one in current row, then scan for label
            //      if a field is less than 3 position from the screen left, then label is probably on the right
            //      if no text in current row, then this field is probably a continuation of previous field set
            //  if multiple fields found in current row, then scan for multiple labels
            //      detect pattern (label is at least 3 characters):
            //      1. [ label ] [ field ] [ label ] [ field ]
            //      2. [ label ] [ field ] [ field ] [ field ]...
            //      3. [ label ] [ subfield ] - [ subfield ] - [ subfield ] ...
            //      4. [ label ] [ subfield ] - [ subfield ] - [ subfield ] ... [ label ] [ subfield ] - [ subfield ] - [ subfield ] ...

            // 1. sort text segment and row fields by start pos
            val labelsAndFields = arrangeRowElementsByPosition(textLine, rowFields, if (isDualPaneScreen) 2 else 1)
            val positions = labelsAndFields.keys.toMutableList()

            // 2. recognize pattern:
            //    label(s) field1 field2 ...
            //    label1 field1 label2 field2 ...
            // [one label, one field]           Effective Date . . . . . . __________
            // [multiple label, one field]      Program/Procedure  . . . . _________________
            // [too many labels]                Co/Policy/Opt/As of date .  __ ____ ________ __ ________
            // [chained labels/fields in 1 row] IRS/Security number . . . ____________ I/S _ Birth date ________
            // [weird label format]             DBA/AKA . Person/Corp . . __________________ _
            // [separators between fields]      Telephone 1  . . . . . . . ___ ___ - ____
            //                                  Policy prefix/number . . . ____ / ________
            // [dangling labels]                Smoke detector . . . . . . _ - No
            //                                  Full sprinkler . . . . . . _ - Yes
            // [too many fields]                Policy number . . . . . .  ____ ___________ ______________
            //                                  Second Name Insured . . .  ___ ___________ _______________
            //                                                              _____________ ________________

            while (positions.isNotEmpty()) {
                val position = positions[0]

                // peek ahead...
                val nextFields = if (fieldMap.containsKey(i + 1)) fieldMap[i + 1] else null
                val nextLabel = if (CollectionUtils.size(nextFields) > 0) {
                    val newRowLabelEnds = nextFields?.first()?.startCol()!!
                    StringUtils.trim(String(filterCharsByColor(screen,
                                                               i + 1,
                                                               columnRange.first until newRowLabelEnds,
                                                               CONTENT_COLORS)))
                } else ""

                if (labelsAndFields[position] is String) {
                    val label = if (currentContentLabel == "") {
                        positions.remove(position)
                        labelsAndFields.remove(position) as String
                    } else
                        gatherLabelSet(labelsAndFields, positions)

                    // [dangling label]
                    if (positions.isEmpty()) break

                    // check for existence of current label... we might need to rename this one for uniqueness
                    currentContentLabel = uniqueFieldLabel(label)

                    val matchedFields = gatherFieldSet(labelsAndFields, positions)
                    unmatchedLabel = assignLabelsAndFields(label, matchedFields, nextLabel, nextFields)
                } else {
                    // [too many fields]
                    val matchedFields = gatherFieldSet(labelsAndFields, positions)
                    if (StringUtils.isNotBlank(unmatchedLabel))
                        unmatchedLabel = assignLabelsAndFields(unmatchedLabel, matchedFields, nextLabel, nextFields)
                    else {
                        ConsoleUtils.log("[WARNING] Unmapped field(s) found in line $i")
                        if (StringUtils.isNotBlank(currentContentLabel)) {
                            val currentContentLabels = currentContentLabel.split("/")
                            val lastContentLabel = currentContentLabels.last()
                            matchedFields.forEachIndexed { pos, field -> addField(lastContentLabel, field, pos) }
                        }
                    }
                }
            }
        }
    }

    private fun mightBeTableHeader(line: String, lineNo: Int) =
        if (StringUtils.isBlank(StringUtils.trim(line)) && isInContentRange(lineNo))
            true
        else {
            // if current line contains character less than the first 5 position, and the rest are all '0'
            val firstZero = StringUtils.indexOf(line, 0, line.indexOfFirst { it != 0.toChar() } + 1)
            firstZero < 5 && StringUtils.substring(line, firstZero, line.length).all { it == 0.toChar() }
        }

    private fun newTable(screen: Screen5250, headerLine: String, range: IntRange, skipLines: MutableList<Int>):
        ScreenTable {
        val headerLines = mutableListOf<String>()
        headerLines += headerLine
        headerLines += scanMoreTableHeaders(screen, range, skipLines)
        return ScreenTable(headerLines, columnRange, config)
    }

    private fun arrangeRowElementsByPosition(rowText: String, rowFields: MutableList<ScreenField>, numberOfPane: Int):
        TreeMap<Int, Any> {
        val labelsAndFields = TreeMap<Int, Any>()
        if (StringUtils.isEmpty(rowText)) return labelsAndFields
        if (rowFields.isEmpty()) return labelsAndFields

        val paneLength = rowText.length / numberOfPane
        val panes = mutableListOf<String>()
        (0 until numberOfPane).forEach { i ->
            panes += StringUtils.substring(rowText, paneLength * i, paneLength * (i + 1))
        }

        val fieldOffset = columnRange.first
        val firstRowFieldPos = rowFields[0].startCol()

        panes.forEachIndexed { index, paneText ->
            val maxPaneTextPos = fieldOffset + (index + 1) * paneLength
            if (firstRowFieldPos < maxPaneTextPos) {
                // 1. chop text into labels and add them by their individual parts
                val chars = paneText.toCharArray()
                var startPos = nextReadablePosition(chars, 0)
                while (startPos != -1 && startPos < chars.size) {
                    val endPos = ArrayUtils.indexOf(chars, 0.toChar(), startPos)
                    val label = cleanScreenLabel(
                        String(chars.copyOfRange(startPos, if (endPos == -1) chars.size - 1 else endPos))
                    )
                    // [separators between fields] ignore them
                    // we need to compensate for the columnRange that did not start from 0
                    // then we can accurately compare label against input fields to determine any overlaps
                    if (label.length > 1)
                        labelsAndFields[startPos + columnRange.first + (paneLength * index)] = label

                    if (endPos == -1) break
                    startPos = nextReadablePosition(chars, endPos)
                }

                // add each field, possibly overriding those previously added for label (by design)
                rowFields.forEach {
                    if (it.startCol() < maxPaneTextPos) labelsAndFields[it.startCol()] = it
                }

                // remove any instance of label: label patterns
                if (labelsAndFields.none { it.value is ScreenField }) labelsAndFields.clear()

                if (labelsAndFields.isNotEmpty()) {
                    var danglingLabelIndex = labelsAndFields.lastKey()
                    while (danglingLabelIndex != null) {
                        danglingLabelIndex = if (labelsAndFields[danglingLabelIndex] is String) {
                            labelsAndFields.remove(danglingLabelIndex)
                            if (labelsAndFields.isNotEmpty()) labelsAndFields.lastKey() else null
                        } else {
                            null
                        }
                    }
                }

                if (labelsAndFields.size == 1 && labelsAndFields[0] is String) labelsAndFields.clear()

                // any label found to overlap with input fields will be removed
                val labelPositions = labelsAndFields.keys.filter { labelsAndFields[it] is String }
                val inputFieldPositions = labelsAndFields.keys.filter { labelsAndFields[it] is ScreenField }
                labelPositions.forEach { labelStartsFrom ->
                    val labelLength = (labelsAndFields[labelStartsFrom] as String).length
                    val overlap = inputFieldPositions.firstOrNull {
                        val inputField = labelsAndFields[it] as ScreenField
                        it <= labelStartsFrom && (it + inputField.length) >= (labelStartsFrom + labelLength)
                    }
                    // overlap found -> remove overlapping label
                    if (overlap != null) labelsAndFields.remove(labelStartsFrom)
                }
            }
        }

        return labelsAndFields
    }

    /**
     * possible more header lines since some columns might wrap
     * eg
     *     ____Column 1__Column 2__Column 3______
     *     ______________line two__and last line_
     *
     *  Column 1  Column 2  Column 3
     *  line two  and last line
     */
    private fun scanMoreTableHeaders(screen: Screen5250, range: IntRange, skipLines: MutableList<Int>): List<String> {
        val headerLines = mutableListOf<String>()
        for (j in range) {
            val nextHeaderLine = String(filterCharsByAttr(screen, j, columnRange, TABLE_HEADER_ATTRS))
            if (StringUtils.isBlank(StringUtils.trim(nextHeaderLine))) break

            headerLines += nextHeaderLine
            skipLines += j
        }

        return headerLines
    }

    /**
     * ignore the "More..." and "More..." lines.
     * "More..." means the end of table (at least for this screen)
     */
    private fun isIgnorableTableHeader(screen: Screen5250, headerContent: String) =
        StringUtils.equals(headerContent, screen.hsMore) ||
        StringUtils.equals(headerContent, "More ...") ||
        StringUtils.equals(headerContent, screen.hsBottom)

    private fun gatherFieldSet(labelsAndFields: TreeMap<Int, Any>, positions: MutableList<Int>):
        MutableList<ScreenField> {

        // look for all fields after this position, until the next label
        val matchingFields = mutableListOf<ScreenField>()
        while (positions.isNotEmpty()) {
            val fieldPosition = positions[0]
            if (labelsAndFields[fieldPosition] is String) break

            val field = labelsAndFields.remove(fieldPosition)
            if (field != null) matchingFields.add(field as ScreenField)
            positions.removeAt(0)
        }

        return matchingFields
    }

    private fun gatherLabelSet(labelsAndFields: TreeMap<Int, Any>, positions: MutableList<Int>): String {
        // look for all fields after this position, until the next label
        val matchingLabels = mutableListOf<String>()
        while (positions.isNotEmpty()) {
            val fieldPosition = positions[0]
            if (labelsAndFields[fieldPosition] !is String) break

            val label = labelsAndFields.remove(fieldPosition)
            if (label != null) matchingLabels.add(label as String)
            positions.removeAt(0)
        }

        return StringUtils.trim(matchingLabels.joinToString(" "))
    }

    private fun assignLabelsAndFields(
        label: String,
        rowFields: MutableList<ScreenField>,
        nextLine: String,
        newRowFields: List<ScreenField>?,
    ): String {

        // is there more fields in the next row?
        val moreFields = CollectionUtils.isNotEmpty(newRowFields) && StringUtils.isBlank(nextLine)
        if (moreFields) newRowFields?.forEach { rowFields.add(it) }
        if (rowFields.isEmpty()) return label

        // whether we have different or same number of label vs field, we would create a one-to-one mapping between
        // the "unsplit" label to the first field so that we can automate the field input sequence based on label,
        // starting from the first field position
        val cleanedLabel = cleanScreenLabel(label)
        addField(uniqueFieldLabel(cleanedLabel), rowFields[0])

        // [one label, one field]
        // [multiple labels, one field]
        if (rowFields.size == 1) return ""

        val labels = StringUtils.split(label, "/")
        if (labels.size == 1) {
            // [too many fields]
            rowFields.forEachIndexed { index, field -> addField(cleanedLabel, field, index) }
            return ""
        }

        // [label count = field count], and possibly [too many labels]
        // add what we have now, and set up for the next line to scan
        val unmatchedLabels = mutableListOf<String>()
        labels.forEachIndexed { pos, it ->
            if (rowFields.size > pos) {
                // if there's an inline label-to-field mapped, then we should store such in the field-content mapping as well
                addField(uniqueFieldLabel(cleanScreenLabel(it)), rowFields[pos])
            } else {
                unmatchedLabels.add(it)
            }
        }

        if (rowFields.size > labels.size) {
            ConsoleUtils.log("[WARNING] Unmapped fields found for the line [$label]; mapped to last label")
            val lastLabel = cleanScreenLabel(labels.last())
            rowFields.subList(labels.size, rowFields.size).forEachIndexed { index, field ->
                addField(lastLabel, field, index + 1)
            }
        }

        return unmatchedLabels.joinToString("/")
    }

    internal fun arrangeScreenFieldsByRow(screenFields: ScreenFields): MutableMap<Int, MutableList<ScreenField>> {
        val fieldMap = mutableMapOf<Int, MutableList<ScreenField>>()
        val fieldCount = screenFields.fieldCount
        if (fieldCount > 0) {
            for (i in 0 until screenFields.fieldCount) {
                val field = screenFields.getField(i)
                val startRow = field.startRow()
                val rowFields = fieldMap[startRow] ?: mutableListOf()
                rowFields.add(field)
                fieldMap[startRow] = rowFields
            }
        }
        return fieldMap
    }

    private fun isDualPane(textLine: String) =
        if (!RegexUtils.isExact(
                StringUtils.substring(textLine, columnRange.first, columnRange.first + 4),
                REGEX_LABEL_START
            )
        )
            false
        else {
            val halfMark = columnRange.last / 2
            RegexUtils.isExact(StringUtils.substring(textLine, halfMark, halfMark + 4), REGEX_LABEL_START)
        }

    /**
     * test for read-only field. For example:<pre>
     * "   System: ABCDEFG  "
     * "SYSTEM_MENU                 TITLE               ITEM: 1234"
     * </pre>
     *
     * also need to deal with 2-column or multi-column:<pre>
     * Client ID . . .: 1234567890         Name  . . . .: John Smith
     * Client Type . .: Regular            Address . . .: 123 Elm Street
     * </pre>
     */
    private fun extractReadOnlyFields(textLine: String, numberOfPane: Int): List<Pair<String, String>>? {
        val fields = mutableListOf<Pair<String, String>>()
        if (StringUtils.isEmpty(textLine)) return fields

        val paneLength = textLine.length / numberOfPane
        val panes = mutableListOf<String>()
        (0 until numberOfPane).forEach { i ->
            panes += StringUtils.substring(textLine, paneLength * i, paneLength * (i + 1))
        }

        panes.forEach { paneText ->
            var line = paneText
            while (StringUtils.isNotEmpty(line)) {
                // 1. collect each label (could be composite labels)
                val label = RegexUtils.firstMatches(line, REGEX_LABEL) ?: RegexUtils.firstMatches(line, REGEX_LABEL2)
                            ?: RegexUtils.firstMatches(line, REGEX_LABEL3) ?: break
                // todo how to combine dangling text to previous field

                line = StringUtils.substringAfter(line, label)

                if (StringUtils.contains(label, "/")) {
                    // composite field
                    fields += Pair(cleanScreenLabel(label), cleanScreenText(line))

                    val labels = StringUtils.split(label, "/")

                    // perhaps the value are also delim by /
                    val values = StringUtils.split(line, "/")

                    // same number of /'s in labels and values, then we'll take the easy route
                    if (ArrayUtils.getLength(labels) == ArrayUtils.getLength(values)) {
                        labels.forEachIndexed { index, it ->
                            fields += Pair(cleanScreenLabel(it), cleanScreenText(values[index]))
                        }
                        line = ""
                    } else {
                        labels.forEach { lbl ->
                            val cleanLabel = cleanScreenLabel(lbl)

                            if (line.isEmpty()) {
                                fields += Pair(cleanLabel, "")
                            } else {
                                val chars = line.toCharArray()
                                val start = nextReadablePosition(chars, 0)
                                if (start != -1 && start < chars.size) {
                                    val end = ArrayUtils.indexOf(chars, 0.toChar(), start)
                                    val value = String(chars.copyOfRange(start, if (end == -1) chars.size - 1 else end))
                                    line = if (end == -1) "" else StringUtils.substringAfter(line, value)
                                    fields += Pair(cleanLabel, StringUtils.removeEnd(cleanScreenText(value), "/"))
                                } else {
                                    fields += Pair(cleanLabel, cleanScreenText(line))
                                    line = ""
                                }
                            }
                        }

                        // any leftover after parsing?
                        if (StringUtils.isNotBlank(line)) {
                            val lastIndex = fields.size - 1
                            val field = fields[lastIndex]
                            fields[lastIndex] = Pair(
                                field.first,
                                StringUtils.trim(field.second + " " + cleanScreenText(line))
                            )
                            line = ""
                        }
                    }
                } else {
                    // since we only have 1 field, then we should only have 1 value
                    fields += Pair(cleanScreenLabel(label), cleanScreenText(line))
                    line = ""
                }
            }
        }

        return fields
    }

    private fun toCompositeFieldValue(combinedValue: String, line: String) =
        "$combinedValue ${StringUtils.trim(StringUtils.replaceChars(line, 0.toChar(), ' '))}"

    private fun resolveUniqueContentLabel(label: String, dup: Int = 0): String {
        val newLabel = "$label${if (dup == 0) "" else "@$dup"}"
        return if (!roFields.containsKey(newLabel)) newLabel else resolveUniqueContentLabel(label, dup + 1)
    }

    private fun uniqueFieldLabel(label: String, dup: Int = 0): String {
        val newLabel = "$label${if (dup == 0) "" else "@$dup"}"
        return if (!fieldMetaMap.containsKey(newLabel)) newLabel else uniqueFieldLabel(label, dup + 1)
    }

    companion object {

        internal val TITLE_COLORS = arrayOf(COLOR_BG_WHITE)
        internal val CONTENT_COLORS = arrayOf(COLOR_BG_GREEN)
        internal val NON_HIDDEN_TEXT_COLORS = arrayOf(
            COLOR_BG_BLUE,
            COLOR_BG_CYAN,
            COLOR_BG_GREEN,
            COLOR_BG_MAGENTA,
            COLOR_BG_RED,
            COLOR_BG_WHITE,
            COLOR_BG_YELLOW
        )

        // 34 means `TN5250jConstants.ATTR_34`
        internal val TABLE_HEADER_ATTRS = arrayOf(34.toChar())

        private val KEEP_WAITING_MESSAGES = listOf(
            "X SYSTEM",
            "X-SYSTEM",
            "X - SYSTEM",
            "X CONNECT",
            "X - CONNECT",
            "X [ ]"
        )
        internal const val STABILIZE_MAX_WAIT = 15000
        internal const val MSG_TIMEOUT = "Unable to stabilize current TN5250 session; timed out"

        @JvmStatic
        fun renderNested(screen: Screen5250, config: ScreenConfig): ScreenObject? {
            if (!waitForScreenToStabilize(screen, config)) throw RuntimeException(MSG_TIMEOUT)

            var startRow = -1
            var startColumn = -1
            var endRow = -1
            var endColumn = -1
            val nestedScreenLines = mutableListOf<String>()
            val rows = screen.rows
            for (i in 0..rows) {
                val lineAttrs = screen.getData(i, 0, i, screen.columns, PLANE_EXTENDED_GRAPHIC)
                if (lineAttrs == null || lineAttrs.isEmpty()) continue

                val prefixUnmatchedBounds = "found unmatched nested window boundary on Row ${i + 1}"

                // still looking for the start of the box
                if (startRow < 0) {
                    val indexUpperLeft = lineAttrs.indexOfFirst { it == UPPER_LEFT.toChar() }
                    if (indexUpperLeft != -1) {
                        val indexUpperRight = lineAttrs.lastIndexOf(UPPER_RIGHT.toChar())
                        if (indexUpperRight != -1) {
                            // found it!
                            startRow = i + 1
                            startColumn = indexUpperLeft + 1
                            endColumn = indexUpperRight
                        } else {
                            ConsoleUtils.error(prefixUnmatchedBounds)
                        }
                    }
                    continue
                }

                // could be middle of the box or end of the box
                val indexLowerLeft = lineAttrs.indexOfFirst { it == LOWER_LEFT.toChar() }
                if (indexLowerLeft != -1) {
                    val indexLowerRight = lineAttrs.lastIndexOf(LOWER_RIGHT.toChar())
                    if (indexLowerRight != -1) {
                        // found the last line
                        endRow = i - 1

                        // just double check that the box dimension is maintained
                        if (indexLowerLeft != startColumn - 1 && indexLowerRight != endColumn + 1) {
                            ConsoleUtils.error("$prefixUnmatchedBounds; could possibly lead to scanning issues...")
                        }

                        break
                    } else {
                        ConsoleUtils.error(prefixUnmatchedBounds)
                    }

                    continue
                }

                val indexLeftBorder = lineAttrs.indexOfFirst { it == GUI_LEFT.toChar() }
                if (indexLeftBorder == -1) {
                    ConsoleUtils.error("$prefixUnmatchedBounds: expected left border not found")
                } else {
                    val indexRightBorder = lineAttrs.lastIndexOf(GUI_RIGHT.toChar())
                    if (indexRightBorder == -1) {
                        ConsoleUtils.error("$prefixUnmatchedBounds: expected right border not found")
                    }

                    if (indexLeftBorder != startColumn - 1 && indexRightBorder != endColumn + 1) {
                        ConsoleUtils.error("$prefixUnmatchedBounds; could possibly lead to scanning issues...")
                    }

                    nestedScreenLines.add(
                        screen.getData(i, indexLeftBorder + 1, i, indexRightBorder - 1, PLANE_TEXT)
                            .map { if (it == 0.toChar()) ' ' else it }
                            .joinToString(separator = "")
                            .removePrefix(" "))
                }
            }

            if (startRow == -1 || startColumn == -1 || endRow == -1 || endColumn == -1) {
                ConsoleUtils.error("No nested window found in current TN5250 session")
                return null
            }

            val rowRange = startRow until endRow
            val columnRange = startColumn..endColumn
            val nestedScreenText = nestedScreenLines.joinToString("\n")
            val titleLines = config.titleLines
            val titles = if (titleLines < 1)
                listOf<String>()
            else
                filterDataByColor(screen,
                                  startRow until minOf(titleLines + startRow, endRow),
                                  columnRange,
                                  TITLE_COLORS)
                    .map { StringUtils.trim(it) }

            val screenObject = ScreenObject(nestedScreenText, rowRange, columnRange, titles, config)
            screenObject.inputFieldCount = screen.screenFields.fieldCount
            screenObject.fieldMap += screenObject.arrangeScreenFieldsByRow(screen.screenFields)
            screenObject.scan(screen)
            return screenObject
        }

        @JvmStatic
        fun cleanScreenText(line: CharArray) = cleanScreenText(String(line))

        @JvmStatic
        fun cleanScreenText(line: String) = StringUtils.trim(StringUtils.replaceChars(line, 0.toChar(), ' '))!!

        /** make sure we have screen text before proceeding */
        @JvmStatic
        fun waitForInitialScreen(screen: Screen5250, config: ScreenConfig): Boolean {
            // 1. check that we are connected... we'll wait for `STABILIZE_MAX_WAIT` ms
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
            }

            if (!waitForScreenToStabilize(screen, config)) return false

            // 2. check that we are getting some screen text... we'll wait for `STABILIZE_MAX_WAIT` ms
            val startTime = System.currentTimeMillis()
            var stringText = screen.stringText
            while (StringUtils.isBlank(StringUtils.trim(stringText))) {
                try {
                    Thread.sleep(250)
                } catch (e: InterruptedException) {
                }

                if (System.currentTimeMillis() - startTime > STABILIZE_MAX_WAIT) {
                    ConsoleUtils.error(
                        "Unable to receive content in current TN5250 session after ${STABILIZE_MAX_WAIT / 1000} seconds")
                    return false
                }

                stringText = screen.stringText
            }

            return true
        }

        /** wait until system responded */
        internal fun waitForScreenToStabilize(screen: Screen5250, config: ScreenConfig): Boolean {
            val oia = screen.oia
            // val oiaText = oia.inhibitedText

            handleBroadcastMessage(screen, config)

            // hang a little longer... at times the server takes longer to warm up (which means oia text is missing)
            Thread.sleep(1500)

            val startTime = System.currentTimeMillis()
            while (keepWaiting(oia)) {
                try {
                    Thread.sleep(250)
                } catch (e: InterruptedException) {
                }

                handleBroadcastMessage(screen, config)

                if ((System.currentTimeMillis() - startTime) > STABILIZE_MAX_WAIT) {
                    ConsoleUtils.error(
                        "Unable to stabilize current TN5250 session after ${STABILIZE_MAX_WAIT / 1000} seconds")
                    return false
                }
            }

            return true
        }

        internal fun handleBroadcastMessage(screen: Screen5250, config: ScreenConfig) {
            val dismissalKeys = config.dismissBroadcastMessage
            if (StringUtils.isBlank(dismissalKeys)) return

            val broadcastTitle = config.detectBroadcastTitle
            val broadcastMessage = config.detectBroadcastText
            if (StringUtils.isBlank(broadcastMessage) && StringUtils.isBlank(broadcastTitle)) return

            var foundBroadcastMessage = if (StringUtils.isNotBlank(broadcastMessage))
                StringUtils.substring(screen.stringText, if (screen.characters[0] == 0.toChar()) 1 else 0)
                    .split("\n")
                    .indexOfFirst { line -> StringUtils.contains(line, broadcastMessage) } != -1
            else false

            if (!foundBroadcastMessage)
                foundBroadcastMessage = if (StringUtils.isNotBlank(broadcastTitle))
                    filterDataByColor(screen,
                                      0 until minOf(config.titleLines, screen.rows),
                                      config.columnRange ?: 0..screen.columns,
                                      TITLE_COLORS)
                        .map { StringUtils.trim(it) }
                        .indexOfFirst { title -> StringUtils.contains(title, broadcastTitle) } != -1
                else false

            if (foundBroadcastMessage) {
                screen.sendKeys(translateKeyMnemonics(dismissalKeys))
                screen.updateScreen()
            }
        }

        /** determine if the screen is ready for scan/mapping */
        private fun keepWaiting(oia: ScreenOIA): Boolean {
            val text = StringUtils.upperCase(StringUtils.trim(oia.inhibitedText))
            return KEEP_WAITING_MESSAGES.any { StringUtils.startsWith(text, it) }
        }

        /**
         * read one or more {@param rows} of data from {@param screen} and then filter out characters which do not have
         * color specified in {@param colorChars}.
         */
        internal fun filterDataByColor(
            screen: Screen5250,
            rows: IntRange,
            columnRange: IntRange,
            acceptedColors: Array<Char>,
        ) =
            rows.map { cleanScreenText(filterCharsByColor(screen, it, columnRange, acceptedColors)) }

        /**
         * read 1 {@param row} of characters from {@param screen}, but zero out the characters that do not have colors
         * specified in {@param colorChars}.
         */
        internal fun filterCharsByColor(
            screen: Screen5250,
            line: Int,
            columnRange: IntRange,
            acceptedColors: Array<Char>,
        ): CharArray {
            if (line < 0 || line >= screen.rows) return CharArray(0)
            val rowData = screen.getData(line, columnRange.first, line, columnRange.last, PLANE_TEXT)

            // seek out unwanted char based on unmatched color; set corresponding data to '0'
            screen.getData(line, columnRange.first, line, columnRange.last, PLANE_COLOR)
                .forEachIndexed { i, color -> if (acceptedColors.indexOf(color) == -1) rowData[i] = 0.toChar() }
            return rowData.copyOfRange(0, columnRange.last - columnRange.first)
        }

        /**
         * read 1 {@param row} of characters from {@param screen}, but zero out the characters that do not have colors
         * specified in {@param colorChars}.
         */
        internal fun filterCharsByAttr(
            screen: Screen5250,
            line: Int,
            columnRange: IntRange,
            acceptedAttrs: Array<Char>,
        ): CharArray {
            if (line < 0 || line >= screen.rows) return CharArray(0)
            val rowData = screen.getData(line, columnRange.first, line, columnRange.last, PLANE_TEXT)
            if (StringUtils.isBlank(String(rowData)))
                return rowData.copyOfRange(0, columnRange.last - columnRange.first)

            // seek out unwanted char based on unmatched color; set corresponding data to '0'
            screen.getData(line, columnRange.first, line, columnRange.last, PLANE_ATTR)
                .forEachIndexed { i, attr -> if (acceptedAttrs.indexOf(attr) == -1) rowData[i] = 0.toChar() }
            return rowData.copyOfRange(0, columnRange.last - columnRange.first)
        }

        /** clear through all the '\0' chars to get to the next section */
        internal fun nextReadablePosition(line: CharArray, position: Int): Int {
            if (position >= line.size) return position - 1

            var position1 = position
            var ch = line[position1]
            while (ch.code == 0) {
                position1 = position1.inc()
                if (position1 >= line.size) break
                ch = line[position1]
            }
            return position1
        }

        // /** clear through all the '\0' and space chars to get to the character */
        // internal fun nextTextPosition(line: CharArray, position: Int): Int {
        //     if (position >= line.size) return position - 1
        //
        //     var position1 = position
        //     var ch = line[position1]
        //     while (ch.toInt() == 0 || ch == ' ') {
        //         position1 = position1.inc()
        //         if (position1 >= line.size) break
        //         ch = line[position1]
        //     }
        //     return position1
        // }

        internal fun cleanScreenLabel(label: String): String {
            // remove trailing colon, then filler, then trailing colon again
            var cleaned = StringUtils.trim(label)
            cleaned = StringUtils.trim(StringUtils.removeEnd(cleaned, ":"))
            cleaned = RegexUtils.removeMatches(StringUtils.trim(cleaned), "\\s*([ |\\x00]\\.|\\.[ |\\x00])+\\s*$")
            cleaned = StringUtils.trim(StringUtils.removeEnd(cleaned, "."))
            cleaned = StringUtils.trim(StringUtils.removeEnd(cleaned, ":"))

            // if '0' found, then trim from last position of '0'
            val lastPos = StringUtils.lastIndexOf(cleaned, 0)
            return StringUtils.trim(if (lastPos != -1) StringUtils.substring(cleaned, lastPos) else cleaned)
        }
    }
}

class FullScreenObject(text: String,
                       rowRange: IntRange,
                       columnRange: IntRange,
                       title: List<String>,
                       val message: String,
                       private val config: ScreenConfig) : ScreenObject(text, rowRange, columnRange, title, config) {

    companion object {

        @JvmStatic
        fun render(screen: Screen5250, config: ScreenConfig): FullScreenObject {
            if (!waitForScreenToStabilize(screen, config)) throw RuntimeException(MSG_TIMEOUT)
            val screenObject = newInstance(screen, config)
            screenObject.scan(screen)
            return screenObject
        }

        private fun newInstance(screen: Screen5250, config: ScreenConfig): FullScreenObject {
            // remove initial null character... some screen has this weirdness
            val screenText = StringUtils.substring(screen.stringText, if (screen.characters[0] == 0.toChar()) 1 else 0)
            val lines = screenText.split("\n")

            // 1. capture screen title
            // 2. capture screen text
            // 3. capture message
            // 4. capture oia text (OIA = Operation Information Area)
            val rowRange = 0 until screen.rows
            val columnRange = 0..screen.columns
            val titles =
                filterDataByColor(screen, 0 until minOf(config.titleLines, screen.rows), columnRange, TITLE_COLORS)
                    .map { StringUtils.trim(it) }
            val screenObject = FullScreenObject(
                screenText,
                rowRange,
                columnRange,
                titles,
                StringUtils.trim(lines.takeLast(1).first()),
                config)
            screenObject.inputFieldCount = screen.screenFields.fieldCount
            screenObject.fieldMap += screenObject.arrangeScreenFieldsByRow(screen.screenFields)

            return screenObject
        }
    }
}

data class FieldMeta(val readOnly: Boolean, var text: String = "", var field: ScreenField? = null) {
    /** bypass field means read-only field. */
    constructor(field: ScreenField) :
        this(field.isBypassField, StringUtils.trim(field.string), if (field.isBypassField) null else field)
}

class ScreenTable(private val headerLines: MutableList<String>,
                  val columns: IntRange,
                  private val config: ScreenConfig) {

    lateinit var rowRange: IntRange
    lateinit var columnRange: IntRange

    internal val columnSpecs = mutableListOf<Pair<Int, Int>>()
    internal val headers = mutableListOf<String>()
    internal val data = mutableListOf<List<String>>()
    private val fields = mutableListOf<List<ScreenField?>>()

    private val favorSpaces = headerLines.map {
        val startsFrom = it.filterIndexed { index, _ -> index > 0 }.indexOfFirst { char -> char != 0.toChar() } + 1
        val lastPos = it.length - it.toCharArray().reversedArray().indexOfFirst { char -> char != 0.toChar() }
        StringUtils.contains(StringUtils.substring(it, startsFrom, lastPos), StringUtils.repeat(" ", 2))
    }.all { it }

    init {
        if (favorSpaces) {
            // contain spaces... need different strategy
            for (index in 0 until headerLines.count()) {
                var line = headerLines[index]
                var startFrom = StringUtils.indexOf(line, "  ")
                while (startFrom != -1) {
                    val endAt = startFrom + line.toCharArray()
                        .filterIndexed { i, _ -> i > startFrom }
                        .indexOfFirst { it != ' ' }
                    line = StringUtils.substring(line, 0, startFrom) +
                           StringUtils.repeat(0.toChar(), endAt - startFrom + 1) +
                           StringUtils.substring(line, endAt + 1)
                    startFrom = StringUtils.indexOf(line, "  ")
                }

                headerLines[index] = line
            }
        }

        parseHeaders(columns)
    }

    private fun parseHeaders(columns: IntRange) {
        // dissect header into fields
        var ending = false
        var startPos = -1

        val textRange = 0 until columns.count()
        for (columnIndex in textRange) {
            val columnChars = resolveColumnChars(columnIndex)
            if (allCharsEmpty(columnChars)) {
                if (startPos != -1) ending = true
            } else {
                if (startPos == -1)
                    startPos = columnIndex
                else if (ending) {
                    columnSpecs.add(Pair(startPos, columnIndex - 1))
                    startPos = columnIndex
                    ending = false
                }
            }
        }

        if (startPos != -1) columnSpecs.add(Pair(startPos, textRange.count() - 1))

        // init headers
        adjustHeaders()
    }

    private fun adjustHeaders() {
        headers.clear()
        repeat(columnSpecs.size) { headers.add("") }

        if (favorSpaces) {
            columnSpecs.forEachIndexed { index, spec ->
                headers[index] = headerLines.joinToString(separator = " ") {
                    StringUtils.trim(cleanScreenText(StringUtils.substring(it, spec.first, spec.second)))
                }.trim()
            }
        } else {
            headerLines.forEach {
                columnSpecs.forEachIndexed { index, spec ->
                    run {
                        val currentHeader = StringUtils.defaultString(headers.getOrNull(index))
                        val newHeader = StringUtils.trim(StringUtils.substring(it, spec.first, spec.second))
                        headers[index] = StringUtils.trim(StringUtils.appendIfMissing(currentHeader, " ") + newHeader)
                    }
                }
            }
        }
    }

    // internal fun debugInfo() =
    //     "Specs: ${columnSpecs}\n"
    //     columnSpecs.forEach { debug(it.first, it.second) }
    // println(headers.joinToString(separator = "\t") { "[$it]" })
    // data.forEach {
    //     print(it.joinToString(separator = "\t") { cell -> "[$cell]" })
    //     println()
    // }
    // println()
    // }

    private fun resolveColumnChars(columnIndex: Int) =
        headerLines.map { line -> if (line.length <= columnIndex) 0.toChar() else line[columnIndex] }

    private fun allCharsEmpty(columnChars: List<Char>) = columnChars.isEmpty() || columnChars.all { it == 0.toChar() }

    fun field(row: Int, field: String): ScreenField? {
        val index = headers.indexOf(field)
        return if (index == -1) null else getField(row, index)
    }

    fun getField(row: Int, field: Int): ScreenField? = if (fields.size <= row) null else fields[row].getOrNull(field)

    fun addData(dataLine: String, fields: List<ScreenField?>?) {
        val rowData = if (favorSpaces) parseDataLine2(dataLine) else parseDataLine(dataLine)

        // it is possible that the parsing logic above might get us more elements than the parsed header list
        if (rowData.size > columnSpecs.size) {
            val extra = StringUtils.trim(rowData.subList(columnSpecs.size, rowData.size).joinToString(separator = ""))
            if (StringUtils.isNotEmpty(extra)) rowData[columnSpecs.size - 1] += " $extra"
            for (i in (rowData.size - 1 downTo columnSpecs.size)) rowData.removeAt(i)
        }

        this.data.add(rowData)

        val rowFields = mutableListOf<ScreenField?>()
        columnSpecs.forEach { spec ->
            val field = fields?.find {
                it != null && it.startCol() >= spec.first && (it.startCol() + it.length) <= spec.second
            }
            if (field != null) rowFields.add(field)
        }

        if (rowFields.isEmpty() && fields != null) fields.forEach { rowFields.add(it) }

        this.fields.add(rowFields)
    }

    private fun parseDataLine(dataLine: String): MutableList<String> {
        val rowData = mutableListOf<String>()
        var posLastRead = 0
        var columnIndex = 0

        while (posLastRead < dataLine.length) {
            val startPos = nextReadablePosition(dataLine.toCharArray(), posLastRead)
            if (startPos >= dataLine.length) break

            val nextZero = StringUtils.indexOf(dataLine, 0, startPos + 1)
            if (nextZero != -1) {
                val cellData = cleanScreenLabel(StringUtils.substring(dataLine, startPos, nextZero))
                if (columnIndex < columnSpecs.size) {
                    // check if the harvest cell text coincide with the header (either from the left or right)
                    val columnSpec = columnSpecs[columnIndex]
                    var nextColumnIndex = columnIndex + 1
                    var nextColumnSpec = if (nextColumnIndex < columnSpecs.size) columnSpecs[nextColumnIndex] else null

                    // found data prior to the first header: this means that we have data (in first column) without header
                    // we need to enhance header and columnSpecs to account for this newfound 1st column
                    if (columnIndex == 0 && nextZero < columnSpec.first) {
                        columnSpecs.add(0, Pair(startPos, nextZero))
                        headers.add(0, "")
                        rowData.add(cellData)
                        columnIndex++
                        posLastRead = nextZero + 1
                        continue
                    }

                    // captured data is completely outside the bound of the column spec
                    if (startPos >= columnSpec.second) {
                        if (nextColumnSpec != null && nextZero > nextColumnSpec.first) {
                            while (nextColumnSpec != null) {
                                rowData.add(columnIndex, "")

                                columnIndex++
                                nextColumnIndex++
                                nextColumnSpec = if (nextColumnIndex < columnSpecs.size)
                                    columnSpecs[nextColumnIndex]
                                else null

                                if (nextColumnSpec == null) {
                                    // this means we've reached the last column
                                    rowData.add(
                                        nextColumnIndex - 1,
                                        if (rowData.size < nextColumnIndex - 1)
                                            StringUtils.trim("" + rowData.last() + " " + cellData)
                                        else cellData
                                    )
                                    break
                                } else {
                                    if (nextZero < nextColumnSpec.first) {
                                        rowData.add(nextColumnIndex - 1, cellData)
                                        break
                                    }
                                }
                            }
                        } else {
                            // either we are at the last column, or captured data is still "close" enough to current column
                            rowData.add(cellData)
                        }
                    } else {
                        // this means that `startPos` is within the boundary of columnSpec
                        when {
                            columnIndex == columnSpecs.size - 1                          -> {
                                // last column; all in
                                rowData.add(cellData)
                            }

                            nextZero > nextColumnSpec?.first ?: columnSpecs.last().first -> {
                                // this means that current `cellData` is cross between current column and next column
                                // so, we need to split and re-merge these 2 column spec to align with cell data
                                val newColumnSpec = Pair(columnSpec.first, startPos)
                                val newNextColumnSpec =
                                    Pair(startPos, max(nextZero, nextColumnSpec?.second ?: columnSpecs.last().second))
                                columnSpecs[columnIndex] = newColumnSpec
                                columnSpecs[nextColumnIndex] = newNextColumnSpec
                                rowData.add(cleanScreenLabel(StringUtils.substring(dataLine,
                                                                                   newColumnSpec.first,
                                                                                   newColumnSpec.second)))
                                rowData.add(cellData)
                                columnIndex++
                            }

                            nextZero < columnSpec.first                                  -> {
                                val targetIndex = max(columnIndex - 1, 0)
                                rowData[targetIndex] = StringUtils.trim(rowData[targetIndex] + " " + cellData)
                                columnIndex--
                            }

                            else                                                         -> {
                                // captured data within the bound of the column spec
                                rowData.add(cellData)
                            }
                        }
                    }
                } else {
                    // extra / dangling data; will be handled outside while() loop (below)
                    rowData.add(cellData)
                }

                columnIndex++
                posLastRead = nextZero + 1
            } else {
                // last column
                if (dataLine.length > startPos) rowData.add(cleanScreenLabel(StringUtils.substring(dataLine, startPos)))
                break
            }
        }

        return rowData
    }

    private fun parseDataLine2(dataLine: String): MutableList<String> {
        var positionChanged = false

        val rowData = mutableListOf<String>()
        for ((index, spec) in columnSpecs.withIndex()) {
            val data = StringUtils.substring(dataLine, spec.first, spec.second + 1)
            val posFirstChar = data.indexOfFirst { it != 0.toChar() }
            if (posFirstChar == -1) {
                // all `0` characters
                rowData.add(cleanScreenText(data))
                continue
            }

            val lastChar = data.last()
            if (lastChar == ' ' || lastChar == 0.toChar()) {
                // data ends with space or `0`
                rowData.add(cleanScreenText(data))
                continue
            }

            // at this point, we know that data does not end with space or `0` (normally should), so we probably need
            // to calibrate column spec to the right positions for current/next spec
            val posLastZero = data.indexOfLast { it == 0.toChar() || it == ' ' }
            rowData.add(cleanScreenText(StringUtils.substring(data, 0, posLastZero)))
            val lastPos = spec.first + posLastZero
            columnSpecs[index] = Pair(spec.first, lastPos)
            if (index < (columnSpecs.count() - 1)) {
                columnSpecs[index + 1] = Pair(lastPos + 1, columnSpecs[index + 1].second)
                positionChanged = true
            }
        }

        if (positionChanged) {
            ConsoleUtils.log("adjusting header due to dimension irregularity when parsing data")
            adjustHeaders()
        }

        return rowData
    }

    fun toCSV(screen: Screen5250, fieldSeparator: String = ",", rowSeparator: String = "\n", maxPages: Int): String {
        val buffer = StringBuilder(headers.joinToString(fieldSeparator) + rowSeparator)

        screen.sendKeys(translateKeyMnemonics("{RESET}"))
        screen.updateScreen()

        // assume we are currently on 1st page
        val endLoopPrefix = "ending table->csv:"
        val sameContentTolerance = 10
        var sameContentFound = 0
        var pageCount = 0

        val forward = maxPages > 0
        val pageLimit = abs(maxPages)

        while (pageCount < pageLimit) {
            // harvest table data for this page
            (0 until data.size).forEach { i -> buffer.append(toCSVRow(i, fieldSeparator)).append(rowSeparator) }

            // is at last page?
            if (pageCount == (pageLimit - 1)) break

            val screenText = screen.stringText

            if (StringUtils.contains(screenText, screen.hsBottom)) {
                // false alarm..?
                if (!RegexUtils.match(screenText, "[\\S]+Bottom")) {
                    // only applicable to forward-scanning
                    if (!forward) continue
                    ConsoleUtils.log("$endLoopPrefix Bottom of the table reached - '${screen.hsBottom}' text found")
                    break
                }
            }

            if (StringUtils.contains(screenText, screen.hsMore)) {
                // going backwards?
                if (!forward && !pageUp(screen)) {
                    ConsoleUtils.log("$endLoopPrefix Found '${screen.hsMore}; Unable to proceed to previous page; " +
                                     "possibly top page reached")
                    break
                }

                if (forward && !pageDown(screen)) {
                    ConsoleUtils.log("$endLoopPrefix Found '${screen.hsMore}; Unable to proceed to next page; " +
                                     "possibly bottom page reached")
                    break
                }

                data.clear()
                for (i in rowRange) addData(String(filterCharsByColor(screen, i, columnRange, CONTENT_COLORS)), null)
                pageCount++
            } else {
                // no indication that there's more data or not... we're gonna do it the hard way
                val currentData = data.toString()

                if (!forward && !pageUp(screen)) {
                    ConsoleUtils.log("$endLoopPrefix Unable to proceed to previous page; possibly top page reached")
                    break
                }

                if (forward && !pageDown(screen)) {
                    ConsoleUtils.log("$endLoopPrefix Unable to proceed to next page; possibly bottom page reached")
                    break
                }

                data.clear()
                for (i in rowRange) addData(String(filterCharsByColor(screen, i, columnRange, CONTENT_COLORS)), null)

                // same stuff? then maybe we've reached the end after all...
                if (StringUtils.equals(currentData, data.toString())) {
                    sameContentFound++
                    ConsoleUtils.log("Same content found... count $sameContentFound")
                    if (sameContentFound >= sameContentTolerance) {
                        ConsoleUtils.log("$endLoopPrefix Same content found too many times ($sameContentTolerance)")
                        break
                    }
                }

                pageCount++
            }
        }

        // scroll back
        for (i in pageCount downTo 1) {
            if (!forward && !pageDown(screen)) break
            if (forward && !pageUp(screen)) break
        }

        return buffer.toString()
    }

    private fun toCSVRow(row: Int, separator: String = ",") =
        data[row].mapIndexed { i, text ->
            TextUtils.csvSafe(
                if (StringUtils.isEmpty(text)) StringUtils.defaultString(getField(row, i)?.string) else text,
                separator,
                true
            )
        }.joinToString(separator)

    private fun pageDown(screen: Screen5250) = changePage(screen, false)
    private fun pageUp(screen: Screen5250) = changePage(screen, true)
    private fun changePage(screen: Screen5250, pageUp: Boolean): Boolean {
        screen.sendKeys(translateKeyMnemonics(if (pageUp) "{PAGEUP}" else "{PAGEDOWN}"))
        screen.updateScreen()
        val lastLineBeforeScanning = screen.stringText.split("\n").takeLast(1).first()

        if (!waitForScreenToStabilize(screen, config)) return false

        if (screen.oia.isKeyBoardLocked) {
            screen.sendKeys(translateKeyMnemonics("{RESET}"))
            screen.updateScreen()
            return false
        }

        val screenText = screen.stringText
        val lastLine = screenText.split("\n").takeLast(1).first()

        return when {
            StringUtils.contains(lastLine, screen.hsBottom) && !pageUp -> false

            // more data available... move along
            StringUtils.contains(lastLine, screen.hsMore)              -> true

            // some other text (changed since we last change the page)... not a good sign
            !StringUtils.equals(lastLineBeforeScanning, lastLine)      -> {
                ConsoleUtils.log("message found in current TN5250 session: $lastLine")
                false
            }

            else                                                       -> true
        }
    }

    fun filter(criteria: Map<String, String>?): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()

        // no criteria means all
        return if (MapUtils.isEmpty(criteria)) {
            data.forEach { rows.add(transformTableRowAsMap(it)) }
            rows
        } else {
            // convert criteria of "column -> search_for" into "column_index -> search_for"
            val criteriaMap = mutableMapOf<Int, String>()
            criteria?.forEach { (column, searchFor) ->
                val columnIndex = headers.indexOf(column)
                if (columnIndex != -1) criteriaMap[columnIndex] = searchFor
            }

            data.forEach { row ->
                var found = true
                val columnIndices = criteriaMap.keys
                for (i in columnIndices) {
                    if (row.size <= i || row[i] != criteriaMap[i]) {
                        found = false
                        break
                    }
                }

                if (found) rows.add(transformTableRowAsMap(row))
            }
            rows
        }
    }

    fun first(criteria: Map<String, String>?): Map<String, String> {
        // no criteria means all
        if (MapUtils.isEmpty(criteria))
            return transformTableRowAsMap(data[0])
        else {
            // convert criteria of "column -> search_for" into "column_index -> search_for"
            val criteriaMap = mutableMapOf<Int, String>()
            criteria?.forEach { (column, searchFor) ->
                val columnIndex = headers.indexOf(column)
                if (columnIndex != -1) criteriaMap[columnIndex] = searchFor
            }

            for (row in data) {
                var found = true
                val columnIndices = criteriaMap.keys
                for (i in columnIndices) {
                    if (row.size <= i || row[i] != criteriaMap[i]) {
                        found = false
                        break
                    }
                }

                if (found) return transformTableRowAsMap(row)
            }

            return mapOf()
        }
    }

    private fun transformTableRowAsMap(row: List<String>): Map<String, String> {
        val rowMap = mutableMapOf<String, String>()
        headers.forEachIndexed { columnIndex, header -> rowMap[header] = row[columnIndex] }
        return rowMap
    }

    fun option(screen: Screen5250, row: Int, vararg options: String) {
        val rowFields = fields.getOrNull(row)?.filterNotNull()?.toMutableList()
        if (rowFields != null) {
            options.forEach { option ->
                if (rowFields.isNotEmpty()) {
                    screen.gotoField(rowFields.removeAt(0))
                    screen.sendKeys(option)
                }
            }
        }
    }

    fun getData(field: String): List<String> {
        val columnIndex = headers.indexOf(field)
        return if (columnIndex == -1)
            mutableListOf()
        else {
            data.mapIndexed { rowNum: Int, row: List<String> ->
                StringUtils.defaultIfBlank(
                    CollectionUtil.getOrDefault(row, columnIndex, ""),
                    StringUtils.defaultString(CollectionUtil.getOrDefault(fields[rowNum],
                                                                          columnIndex,
                                                                          null)?.string)
                )
            }
        }
    }

    fun match(field: String, data: String) =
        if (field == "*")
            rowContains { TextUtils.polyMatch(it, data) }
        else
            getData(field).firstOrNull { TextUtils.polyMatch(it, data) } != null

    fun findRow(field: String, data: String) =
        if (field == "*")
            findRow { TextUtils.polyMatch(it, data) }
        else
            getData(field).indexOfFirst { TextUtils.polyMatch(it, data) }

    fun findRow(check: (String) -> Boolean) = data.indexOfFirst { row -> row.firstOrNull { check(it) } != null }

    fun rowContains(check: (String) -> Boolean) =
        data.firstOrNull { row -> row.firstOrNull { check(it) } != null } != null

    fun rowCount(field: String, data: String) =
        if (field == "*")
            rowCount { TextUtils.polyMatch(it, data) }
        else
            getData(field).filter { TextUtils.polyMatch(it, data) }.size

    fun rowCount(check: (String) -> Boolean) = data.filter { row -> row.firstOrNull { check(it) } != null }.size

    fun rowCount() = data.size

    fun columnCount() = headers.size
}

class ScreenConfig(val titleLines: Int = 0,
                   val favorFirstTable: Boolean = true,
                   val detectBroadcastTitle: String,
                   val detectBroadcastText: String,
                   val dismissBroadcastMessage: String) {

    var rowRange: IntRange? = null
        set(range) {
            if (range != null) field = range
        }

    var columnRange: IntRange? = null
        set(range) {
            if (range != null) field = range
        }

    companion object {
        fun newInstance(profile: String, context: ExecutionContext): ScreenConfig {
            return ScreenConfig(context.getIntData(resolveTitleLineConfig(profile), DEF_TITLE_LINES),
                                context.getBooleanData(USE_FIRST_TABLE, getDefaultBool(USE_FIRST_TABLE)),
                                context.getStringData(SCAN_BROADCAST_TITLE),
                                context.getStringData(SCAN_BROADCAST_TEXT),
                                context.getStringData(DISMISS_BROADCAST))
        }
    }
}
