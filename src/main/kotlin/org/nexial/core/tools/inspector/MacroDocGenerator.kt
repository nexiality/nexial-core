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

package org.nexial.core.tools.inspector

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Data.SHEET_SYSTEM
import org.nexial.core.excel.Excel
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.tools.inspector.InspectorConst.Advice.MISSING_MACRO_DESC
import org.nexial.core.tools.inspector.InspectorConst.MACRO_CMDS
import org.nexial.core.tools.inspector.InspectorConst.MACRO_DESCRIPTION
import org.nexial.core.tools.inspector.InspectorConst.MACRO_EXPECTS
import org.nexial.core.tools.inspector.InspectorConst.MACRO_PRODUCES
import org.nexial.core.utils.InputFileUtils
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.util.*
import java.util.stream.Collectors

class MacroDocGenerator(val options: InspectorOptions, val logger: InspectorLogger) {

    fun generate(): List<MacroFile> {

        val scannedMacroFiles = ArrayList<MacroFile>()

        val projectHome = File(options.directory)

        // find all potential macro files
        val macroFiles = filterFiles(projectHome, arrayOf("xlsx")) { file -> isMacroFile(file) }
        if (options.verbose) logger.log("found ${macroFiles.size} Excel files")
        if (macroFiles.isEmpty()) return scannedMacroFiles

        // scan each potential file for macro-specific header
        macroFiles.forEach { file ->
            try {
                val excel = Excel(file)
                val filePath = excel.file.absolutePath

                if (!InputFileUtils.isValidMacro(excel)) {
                    logger.log("ignoring file", "$filePath")
                } else {
                    logger.log("parsing macro", "$filePath")

                    var relativePath = StringUtils.remove(file.parentFile.absolutePath, projectHome.absolutePath)
                    relativePath = StringUtils.replace(relativePath, "\\", "/")
                    relativePath = StringUtils.removeStart(relativePath, "/")

                    val macroFile = MacroFile(relativePath, file.name)

                    // macro file found, scan every sheet
                    val macros = collectMacros(excel)
                    macroFile.data.addAll(macros)

                    // scan for missing description
                    if (macros.any { StringUtils.isEmpty(it.description) }) macroFile.advices.add(MISSING_MACRO_DESC)

                    // add to intermediary object
                    scannedMacroFiles.add(macroFile)
                }
            } catch (e: IOException) {
                logger.error("Error parsing $file: ${e.message}")
            }

        }

        return scannedMacroFiles
    }

    internal fun filterFiles(directory: File, extensions: Array<String>, filter: (file: File) -> Boolean): List<File> =
            FileUtils.listFiles(directory, extensions, true)
                .stream()
                .filter { filter(it) }
                .collect(Collectors.toList())

    internal fun isMacroFile(file: File): Boolean = !file.name.startsWith("~") &&
                                                    !file.absolutePath.contains("${separator}output$separator") &&
                                                    !file.name.contains(".data.xlsx")

    private fun collectMacros(excel: Excel): List<MacroDef> {
        val macros = ArrayList<MacroDef>()

        val sheets = excel.getWorksheetsStartWith("")
        if (CollectionUtils.isEmpty(sheets)) return macros

        // sheet found, scan every macro def

        sheets.forEach { sheet ->
            val sheetName = sheet.name
            if (!StringUtils.equals(sheetName, SHEET_SYSTEM)) {

                val lastRow = sheet.findLastDataRow(ExcelAddress("C2"))
                val cells = sheet.readRange(ExcelAddress("A2:F$lastRow"))

                var macroDef: MacroDef? = null
                for (row in cells) {
                    val macroName = StringUtils.unwrap(row[0], "\"")

                    if (StringUtils.isNotBlank(macroName)) {
                        // new macro def
                        if (macroDef != null) macros.add(macroDef)

                        // else brand new macros, first time
                        macroDef = MacroDef(sheetName, macroName)
                        logger.log("macro found", "$sheetName:$macroName")
                    }

                    // no macro def... probably source error
                    if (macroDef == null) continue

                    val description = StringUtils.unwrap(row[1], "\"")
                    val command = StringUtils.unwrap(row[2], "\"") + "." + StringUtils.unwrap(row[3], "\"")
                    if (MACRO_CMDS.contains(command)) {
                        when (command) {
                            MACRO_DESCRIPTION -> macroDef.addDescription(description)
                            MACRO_EXPECTS     -> macroDef.addExpects(Expects(description,
                                                                             StringUtils.unwrap(row[4], "\""),
                                                                             StringUtils.unwrap(row[5], "\"")))
                            MACRO_PRODUCES    -> macroDef.addProduces(Produces(description,
                                                                               StringUtils.unwrap(row[4], "\"")))
                        }
                    }
                }

                if (macroDef != null) macros.add(macroDef)
            }
        }

        // macro found, scan for macro.definition(), macro.expects() and macro.produces()
        return macros
    }
}
