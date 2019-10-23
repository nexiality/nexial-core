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

package org.nexial.core.tools

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.nexial.commons.utils.TextUtils
import org.nexial.core.CommandConst.CMD_MACRO
import org.nexial.core.NexialConst.Data.SHEET_SYSTEM
import org.nexial.core.NexialConst.Data.TEXT_DELIM
import org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS
import org.nexial.core.NexialConst.ExitStatus.RC_EXCEL_IN_USE
import org.nexial.core.NexialConst.Project.*
import org.nexial.core.SystemVariables.getDefault
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelArea
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.model.MacroMerger
import org.nexial.core.model.TestProject
import org.nexial.core.tools.CliConst.OPT_PREVIEW
import org.nexial.core.tools.CliConst.OPT_VERBOSE
import org.nexial.core.tools.CliUtils.getCommandLine
import org.nexial.core.tools.CliUtils.newArgOption
import org.nexial.core.tools.ProjectToolUtils.formatColumns
import org.nexial.core.tools.ProjectToolUtils.isTestScriptFile
import org.nexial.core.tools.ProjectToolUtils.listTestScripts
import org.nexial.core.tools.ProjectToolUtils.log
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.MISSING_DIRECTORY
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.MISSING_OUTPUT
import org.nexial.core.tools.inspector.InspectorConst.exit
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.InputFileUtils
import org.springframework.util.CollectionUtils
import java.io.File
import java.io.File.separator
import java.io.IOException

object MacroUpdater {
    private val updated = mutableListOf<UpdateLog>()
    private val project = TestProject()
    private var verbose = false

    data class MacroChange(val fromFile: String, val toFile: String,
                           val fromSheet: String, val toSheet: String,
                           val fromName: String, val toName: String)

    data class MacroUpdaterOptions(val searchFrom: String,
                                   val changes: List<MacroChange>,
                                   val preview: Boolean = false,
                                   val verbose: Boolean = false)

    @JvmStatic
    fun main(args: Array<String>) {
        val options = deriveMacroOptions(deriveCommandLine(args))
        verbose = options.verbose
        resolveProjectMeta(options)
        updateAll(options)

        val banner = StringUtils.repeat('-', 100)
        val prompt = if (options.preview) "macro update preview" else "macro update summary"

        println()
        println()
        println("/$banner\\")
        println("|" + ConsoleUtils.centerPrompt(prompt, 100) + "|")
        println("\\$banner/")
        if (updated.size == 0) {
            println(ConsoleUtils.centerPrompt("There are no matching data variables in the files.", 102))
        } else {
            println(formatColumns("File", "Worksheet", "Position", "Updating Macros"))
            println("$banner--")
            updated.forEach { println(it) }
            println()
        }
    }

    fun resolveProjectMeta(option: MacroUpdaterOptions) {
        project.projectHome = StringUtils.substringBeforeLast(option.searchFrom, DEF_LOC_ARTIFACT)
        project.scriptPath = StringUtils.appendIfMissing(project.projectHome, separator) + DEF_REL_LOC_TEST_SCRIPT
    }

    fun updateAll(options: MacroUpdaterOptions): List<UpdateLog> {
        replaceMacro(options)
        replaceScript(options)
        return updated
    }

    private fun deriveCommandLine(args: Array<String>): CommandLine {
        val cmdOptions = Options()
        cmdOptions.addOption(OPT_VERBOSE)
        cmdOptions.addOption(OPT_PREVIEW)
        cmdOptions.addOption(newArgOption("t", "target", "[REQUIRED] The project home to scan", true))
        cmdOptions.addOption(newArgOption("f", "file", "[REQUIRED] The macro file name to scan", true))
        cmdOptions.addOption(newArgOption("s", "sheet", "[REQUIRED] The macro sheet to search in", false))
        cmdOptions.addOption(newArgOption("m", "macroName", "[REQUIRED] The macro name to be refactored", false))

        val programExt = if (IS_OS_WINDOWS) ".cmd" else ".sh"
        val cmd = getCommandLine("nexial-macro-refactor$programExt", args, cmdOptions)
        if (cmd == null) {
            ConsoleUtils.error("unable to proceed... exiting")
            exit(RC_BAD_CLI_ARGS)
        }

        return cmd
    }

    internal fun deriveMacroOptions(cmd: CommandLine): MacroUpdaterOptions {
        val searchFrom = cmd.getOptionValue("t")
        if (StringUtils.isBlank(searchFrom)) {
            ConsoleUtils.error("Missing target parameter")
            exit(MISSING_DIRECTORY)
        }

        var macroFileName = StringUtils.trim(cmd.getOptionValue("f"))
        if (StringUtils.isBlank(macroFileName)) {
            ConsoleUtils.error("Missing 'Macro File' parameter")
            exit(MISSING_OUTPUT)
        }
        macroFileName = StringUtils.appendIfMissing(macroFileName, ".$SCRIPT_FILE_SUFFIX")

        val macroSheet = StringUtils.trim(cmd.getOptionValue("s"))
        if (StringUtils.isBlank(macroSheet)) {
            ConsoleUtils.error("Missing 'Macro Sheet' parameter")
            exit(MISSING_OUTPUT)
        }

        val macroName = StringUtils.trim(cmd.getOptionValue("m"))
        if (StringUtils.isBlank(macroName)) {
            ConsoleUtils.error("Missing 'Macro Name' parameter")
            exit(MISSING_OUTPUT)
        }

        val map = TextUtils.toMap(macroName, getDefault(TEXT_DELIM), "=")
        val options: List<MacroChange> = if (map.isEmpty()) {
            listOf()
        } else {
            map.filter { it.key.isNotBlank() }.map {
                MacroChange(fromFile = macroFileName, toFile = macroFileName,
                            fromSheet = macroSheet, toSheet = macroSheet,
                            fromName = it.key.trim(), toName = it.value.trim())
            }
        }

        return MacroUpdaterOptions(searchFrom = searchFrom,
                                   changes = options,
                                   preview = cmd.hasOption(OPT_PREVIEW.opt),
                                   verbose = cmd.hasOption(OPT_VERBOSE.opt))
    }

    private fun replaceScript(options: MacroUpdaterOptions) =
            listTestScripts(File(options.searchFrom)).forEach { handleScripts(it, options) }

    private fun handleScripts(file: File, options: MacroUpdaterOptions) {
        val excel = Excel(file)
        var hasUpdate = false
        val worksheets = excel.getWorksheetsStartWith("")
        verbose("processing", file)

        if (CollectionUtils.isEmpty(worksheets)) {
            verbose("processed (no valid sheets)", file)
            return
        }

        val filePath = StringUtils.substringAfter(file.absolutePath, DEF_REL_LOC_ARTIFACT)
        worksheets.forEach { if (updateScenario(it, options, UpdateLog(filePath, it.name))) hasUpdate = true }

        if (!hasUpdate) {
            verbose("processed (no change)", file)
            return
        }

        saveExcel(file, excel.workbook)
        verbose("processed", file)
    }

    private fun updateScenario(sheet: Worksheet, options: MacroUpdaterOptions, updateLog: UpdateLog): Boolean {
        var hasUpdate = false
        if (sheet.name == SHEET_SYSTEM) return hasUpdate

        val lastCommandRow = sheet.findLastDataRow(ADDR_COMMAND_START)
        val firstRow = "" + COL_TEST_CASE + (ADDR_COMMAND_START.rowStartIndex + 1)
        val area = ExcelArea(sheet, ExcelAddress("$firstRow:${COL_COMMAND + 3}$lastCommandRow"), false)

        // parse entire area
        for (row in area.wholeArea) {
            val command = Excel.getCellValue(row[COL_IDX_TARGET]) + "." + Excel.getCellValue(row[COL_IDX_COMMAND])
            if (command == CMD_MACRO) {
                val macroCell = row[COL_IDX_PARAMS_START + 2]
                val macroFile = Excel.getCellValue(row[COL_IDX_PARAMS_START])
                val macroSheet = Excel.getCellValue(row[COL_IDX_PARAMS_START + 1])
                val macroName = Excel.getCellValue(macroCell)

                val matched = options.changes.find { change ->
                    return MacroMerger.resolveMacroFile(project, change.fromFile).absolutePath ==
                           MacroMerger.resolveMacroFile(project, macroFile).absolutePath &&
                           macroSheet == change.fromSheet &&
                           macroName == change.fromName
                }

                if (matched != null) {
                    val log = updateLog.copy()
                    log.position = macroCell.address.formatAsString()
                    log.before = macroName
                    log.after = matched.toName
                    updated.add(log)

                    verbose("macro reference matched", "${sheet.file} / ${sheet.name} / ${log.position}")

                    // don't set value to preview changes
                    if (options.preview) continue

                    // we only deal with macro name for now
                    macroCell.setCellValue(log.after)
                    hasUpdate = true
                }
            }
        }

        return hasUpdate
    }

    private fun replaceMacro(options: MacroUpdaterOptions) {
        FileUtils.listFiles(File(options.searchFrom), Array(1) { SCRIPT_FILE_SUFFIX }, true)
            .stream()
            .filter { isTestScriptFile(it) && InputFileUtils.isValidMacro(it.absolutePath) }
            .forEach { handleMacro(it, options) }
    }

    private fun handleMacro(file: File, options: MacroUpdaterOptions) {

        val macroFilePath = file.absolutePath
        val excel = Excel(file)
        val filePath = StringUtils.substringAfter(file.absolutePath, DEF_REL_LOC_ARTIFACT)
        var updated = false

        options.changes.forEach { change ->
            if (MacroMerger.resolveMacroFile(project, change.fromFile).absolutePath == macroFilePath) {
                val worksheet = excel.worksheet(change.fromSheet)
                if (worksheet != null) {
                    verbose("processing macro", file)

                    val hasUpdate = updateMacro(worksheet, change, UpdateLog(filePath, worksheet.name), options.preview)
                    if (!hasUpdate) {
                        verbose("processed (no change)", file)
                    } else {
                        verbose("updated macro", file)
                        updated = true
                    }
                }
            }
        }

        if (updated) saveExcel(file, excel.workbook)
        verbose("processed macro", file)
    }

    private fun updateMacro(sheet: Worksheet, change: MacroChange, updateLog: UpdateLog, preview: Boolean): Boolean {
        var hasUpdate = false
        val lastCommandRow = sheet.findLastDataRow(ADDR_MACRO_COMMAND_START)
        val firstRow = "" + COL_TEST_CASE + (ADDR_MACRO_COMMAND_START.rowStartIndex + 1)

        val addr = ExcelAddress("$firstRow:$COL_TEST_CASE$lastCommandRow")
        val area = ExcelArea(sheet, addr, false)

        for (row in area.wholeArea) {
            val cell = row[COL_IDX_TESTCASE]
            val macro = Excel.getCellValue(cell)
            if (change.fromName == macro) {
                val log = updateLog.copy()
                log.position = cell.address.formatAsString()
                log.before = change.fromName
                log.after = change.toName
                updated.add(log)

                verbose("macro definition matched", "${sheet.file} / ${sheet.name} / ${log.position}")

                if (preview) continue

                cell.setCellValue(change.toName)
                hasUpdate = true
            }
        }

        return hasUpdate
    }

    private fun saveExcel(file: File, workBook: XSSFWorkbook) {
        try {
            Excel.save(file, workBook)
        } catch (e: IOException) {
            System.err.println("\n\nFile $file is either is in use by other process or got deleted. " +
                               "Please close the file if it is open and re run the program\n\n")
            System.exit(RC_EXCEL_IN_USE)
        } finally {
            try {
                workBook.close()
            } catch (e: IOException) {
                System.err.println("Unable to properly close Excel file $file: ${e.message}")
            }
        }
    }

    private fun verbose(action: String, subject: Any) {
        if (verbose) log(action, subject)
    }
}
