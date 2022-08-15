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

package org.nexial.core.tools.inspector

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.CommandConst.MACRO_COMMANDS
import org.nexial.core.CommandConst.MACRO_DESCRIPTION
import org.nexial.core.CommandConst.MACRO_EXPECTS
import org.nexial.core.CommandConst.MACRO_PRODUCES
import org.nexial.core.NexialConst.Data.SHEET_SYSTEM
import org.nexial.core.NexialConst.Project.SCRIPT_FILE_SUFFIX
import org.nexial.core.excel.Excel
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.tools.ProjectToolUtils.isMacroFile
import org.nexial.core.tools.inspector.ArtifactType.MACRO
import org.nexial.core.tools.inspector.ProjectInspector.filterFiles
import org.nexial.core.tools.inspector.ProjectInspector.resolveRelativePath
import org.nexial.core.utils.InputFileUtils
import java.io.File
import java.io.IOException

class MacroDocGenerator(val options: InspectorOptions, val logger: InspectorLogger) {

    fun generate(): List<MacroFile> {

        val scannedMacroFiles = ArrayList<MacroFile>()

        val projectHome = File(options.directory)

        // find all potential macro files
        val macroFiles = filterFiles(projectHome, arrayOf(SCRIPT_FILE_SUFFIX)) { file -> isMacroFile(file) }
        logger.title("PROCESSING MACROS", "found ${macroFiles.size} potential files")

        if (macroFiles.isEmpty()) return scannedMacroFiles

        val cacheHelper = CacheHelper<MacroCache>(options, logger)

        // scan each potential file for macro-specific header
        macroFiles.forEach { file ->

            val location = resolveRelativePath(projectHome, file)
            val macroFile = MacroFile(location, file.name)

            val cacheFile = cacheHelper.resolveCacheFile(file)
            val macroCache = if (cacheHelper.isUsableCacheFile(cacheFile)) {
                // use cache instead
                val macroCache = cacheHelper.readCache<MacroCache>(cacheFile)
                if (macroCache != null) {
                    // while this is not needed for Kotlin code, the loaded JSON might not have "macros", hence the null check
                    if (macroCache.macros.isNotEmpty()) {
                        macroFile.data += macroCache.macros
                        scannedMacroFiles += macroFile
                    } else macroCache.macros = arrayListOf()
                    macroCache
                } else newMacroCacheInstance(file, location)
            } else newMacroCacheInstance(file, location)

            // while this is not needed for Kotlin code, the loaded JSON might not have "macros", hence the null check
            if (macroCache.macros.isEmpty()) {
                try {
                    val excel = Excel(file)
                    if (InputFileUtils.isValidMacro(excel)) {
                        logger.log(file.name, "gathering macros")

                        // macro file found, scan every sheet
                        collectMacros(excel, macroCache)

                        val macros = macroCache.macros
                        macroFile.data.addAll(macros)

                        // scan for missing description
                        if (macros.any { StringUtils.isEmpty(it.description) })
                            macroFile.advices += ProjectInspector.getMessage("macro.description.missing")

                        // add to intermediary object
                        scannedMacroFiles.add(macroFile)

                        cacheHelper.saveCache(macroCache, cacheFile)
                    }
                } catch (e: IOException) {
                    logger.error("Error parsing $file: ${e.message}")
                }
            }

            cacheHelper.expireOutdatedCache(cacheFile)
        }

        return scannedMacroFiles
    }

    private fun newMacroCacheInstance(file: File, location: String): MacroCache {
        return MacroCache(options.project.name, location, ScriptCache(file.name, MACRO))
    }

    private fun collectMacros(excel: Excel, macroCache: MacroCache) {
        val sheets = excel.getWorksheetsStartWith("")
        if (CollectionUtils.isEmpty(sheets)) return

        val macros = ArrayList<MacroDef>()

        // sheet found, scan every macro def
        sheets.forEach { sheet ->
            val sheetName = sheet.name

            if (!StringUtils.equals(sheetName, SHEET_SYSTEM)) {
                logger.log(excel.file.name, "scanning sheet '$sheetName'")
                val lastRow = sheet.findLastDataRow(ExcelAddress("${COL_TARGET}2"))
                val cells = sheet.readRange(ExcelAddress("${COL_TEST_CASE}2:$COL_CAPTURE_SCREEN$lastRow"))

                var rowIndex = 1
                val scenarioCache = ScenarioCache(sheetName, MACRO)
                var macroDef: MacroDef? = null
                var sequenceCache: SequenceCache? = null

                for (row in cells) {
                    rowIndex++
                    val macroName = row[0].removeSurrounding("\"")

                    if (StringUtils.isNotBlank(macroName)) {
                        // new macro def
                        if (macroDef != null) macros.add(macroDef)
                        if (sequenceCache != null) scenarioCache.sequences += sequenceCache

                        // else brand new macros, first time
                        macroDef = MacroDef(sheetName, macroName)
                        sequenceCache = SequenceCache(macroName, MACRO, rowIndex)
                        logger.log(excel.file.name, "macro found -> $sheetName:$macroName")
                    }

                    // no macro def... probably source error
                    if (macroDef == null) continue

                    val description = row[1].removeSurrounding("\"")
                    val cmdType = row[2].removeSurrounding("\"")
                    val command = row[3].removeSurrounding("\"")
                    val commandFqn = "$cmdType.$command"
                    if (MACRO_COMMANDS.contains(commandFqn)) {
                        // macro found, scan for macro.definition(), macro.expects() and macro.produces()
                        when (commandFqn) {
                            MACRO_DESCRIPTION -> macroDef.addDescription(description)
                            MACRO_EXPECTS     -> macroDef.addExpects(Expects(description,
                                                                             row[4].removeSurrounding("\""),
                                                                             row[5].removeSurrounding("\"")))
                            MACRO_PRODUCES    -> macroDef.addProduces(Produces(description,
                                                                               row[4].removeSurrounding("\"")))
                        }
                    }

                    if (sequenceCache == null) sequenceCache = SequenceCache(macroName, MACRO, rowIndex)
                    sequenceCache.steps += StepCache(
                            row = rowIndex,
                            description = description,
                            cmdType = cmdType,
                            command = command,
                            param1 = row[4].removeSurrounding("\""),
                            param2 = row[5].removeSurrounding("\""),
                            param3 = row[6].removeSurrounding("\""),
                            param4 = row[7].removeSurrounding("\""),
                            param5 = row[8].removeSurrounding("\""),
                            flowControl = row[9].removeSurrounding("\""),
                            screenshot = StringUtils.equalsIgnoreCase(row[11].removeSurrounding("\"").trim(), "x"))
                }

                if (macroDef != null) macros.add(macroDef)
                if (sequenceCache != null) scenarioCache.sequences += sequenceCache
                macroCache.script.scenarios += scenarioCache
            }
        }

        macroCache.macros = macros
    }
}
