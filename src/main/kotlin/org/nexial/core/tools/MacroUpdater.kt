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
import javax.validation.constraints.NotNull

object MacroUpdater {
    private val updated = mutableListOf<UpdateLog>()
    private val project = TestProject()
    private var searchFrom = ""
    private var isDryRun = false

    data class MacroOptions(val macroFile: String, val macroSheet: String, val macroMap: MutableMap<String, String>)

    @JvmStatic
    fun main(args: Array<String>) {
        val options = deriveMacroOptions(deriveCommandLine(args))
        resolveProjectMeta()
        updateAll(options)
    }

    private fun resolveProjectMeta() {
        project.projectHome = StringUtils.substringBeforeLast(searchFrom, "artifact")
        project.scriptPath = StringUtils.appendIfMissing(project.projectHome, separator) + DEF_REL_LOC_TEST_SCRIPT
    }

    private fun updateAll(options: MacroOptions) {
        replaceMacro(options)
        replaceScript(options)

        val banner = StringUtils.repeat('-', 100)
        val prompt = if (isDryRun) " macro name update preview" else " macro name update summary"

        println()
        println()
        println("/$banner\\")
        println("|" + ConsoleUtils.centerPrompt(prompt, 100) + "|")
        println("\\$banner/")
        if (updated.size == 0) {
            println(ConsoleUtils.centerPrompt("There are no matching data variables in the files.", 102))
        } else {
            println(formatColumns("file", "worksheet", "position", "updatingMacros"))
            println("$banner--")
            updated.forEach { println(it) }
            println()
        }
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

    private fun deriveMacroOptions(cmd: CommandLine): MacroOptions {
        searchFrom = cmd.getOptionValue("t")
        if (StringUtils.isBlank(searchFrom)) {
            ConsoleUtils.error("Missing target parameter")
            exit(MISSING_DIRECTORY)
        }

        if (cmd.hasOption("p")) {
            isDryRun = true
        }

        var macroFileName = cmd.getOptionValue("f")
        if (StringUtils.isBlank(macroFileName)) {
            ConsoleUtils.error("Missing 'Macro File' parameter")
            exit(MISSING_OUTPUT)
        }
        macroFileName = StringUtils.appendIfMissing(macroFileName, ".$SCRIPT_FILE_SUFFIX")

        val macroSheet = cmd.getOptionValue("s")
        if (StringUtils.isBlank(macroSheet)) {
            ConsoleUtils.error("Missing 'Macro Sheet' parameter")
            exit(MISSING_OUTPUT)
        }

        val macroName = cmd.getOptionValue("m")
        if (StringUtils.isBlank(macroName)) {
            ConsoleUtils.error("Missing 'Macro Name' parameter")
            exit(MISSING_OUTPUT)
        }

        val toMap = TextUtils.toMap(macroName, getDefault(TEXT_DELIM), "=")

//        println("target macro file:  $macroFileName")
//        println("target macro sheet: $macroSheet")
//        println("target macro names: $toMap")

        return MacroOptions(macroFileName, macroSheet, toMap)
    }

    private fun replaceScript(options: MacroOptions) =
            listTestScripts(File(searchFrom)).forEach { handleScripts(it, options) }

    private fun handleScripts(file: File, options: MacroOptions) {
        val excel = Excel(file)
        var hasUpdate = false
        val worksheets = excel.getWorksheetsStartWith("")
        log("processing", file)

        if (CollectionUtils.isEmpty(worksheets)) {
            log("processed (no valid sheets)", file)
            return
        }

        worksheets.forEach {
            val filePath = StringUtils.substringAfter(file.absolutePath, DEF_REL_LOC_ARTIFACT)
            val updateLog = UpdateLog(filePath, it.name)
            if (updateScenario(it, options, updateLog)) hasUpdate = true
        }

        if (!hasUpdate) {
            log("processed (no change)", file)
            return
        }

        saveExcel(file, excel.workbook)
        log("processed", file)
    }

    private fun updateScenario(sheet: Worksheet, options: MacroOptions, updateLog: UpdateLog): Boolean {
        var hasUpdate = false
        if (sheet.name == SHEET_SYSTEM) return hasUpdate

        val expectedMacroFile = MacroMerger.resolveMacroFile(project, options.macroFile)

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

                try {
                    val macroFilePath = MacroMerger.resolveMacroFile(project, macroFile).absolutePath
                    if (StringUtils.equals(macroFilePath, expectedMacroFile.absolutePath) &&
                        macroSheet == options.macroSheet &&
                        options.macroMap.containsKey(macroName)) {
                        updateLog.position = macroCell.address.formatAsString()
                        updateLog.before = macroName
                        updateLog.after = "${options.macroMap[macroName]}"
                        updated.add(updateLog)

                        // don't set value to preview changes
                        if (isDryRun) continue

                        macroCell.setCellValue(options.macroMap[macroName])
                        hasUpdate = true
                    }
                } catch (e: IOException) {
                    log("processing ${sheet.name}", "Invalid reference for macro file - $macroFile")
                }
            }
        }

        return hasUpdate
    }

    private fun replaceMacro(options: MacroOptions) {
        val macroFile = MacroMerger.resolveMacroFile(project, options.macroFile)
        FileUtils.listFiles(File(searchFrom), Array(1) { SCRIPT_FILE_SUFFIX }, true).stream()
            .filter { file ->
                isTestScriptFile(file) &&
                file.absolutePath == macroFile.absolutePath &&
                InputFileUtils.isValidMacro(file.absolutePath)
            }.forEach { handleMacro(it, options) }
    }

    private fun handleMacro(file: File, options: MacroOptions) {
        log("processing macro", file)
        val excel = Excel(file)
        val worksheet = excel.worksheet(options.macroSheet) ?: return

        val filePath = StringUtils.substringAfter(file.absolutePath, DEF_REL_LOC_ARTIFACT)
        val updateLog = UpdateLog(filePath, worksheet.name)

        val hasUpdate = updateMacro(worksheet, options.macroMap, updateLog)
        if (!hasUpdate) {
            log("processed (no change)", file)
            return
        }

        saveExcel(file, excel.workbook)
        log("processed macro", file)
    }

    private fun updateMacro(sheet: Worksheet, toMap: MutableMap<String, String>, updateLog: UpdateLog): Boolean {
        var hasUpdate = false
        val lastCommandRow = sheet.findLastDataRow(ADDR_MACRO_COMMAND_START)
        val firstRow = "" + COL_TEST_CASE + (ADDR_MACRO_COMMAND_START.rowStartIndex + 1)

        val addr = ExcelAddress("$firstRow:$COL_TEST_CASE$lastCommandRow")
        val area = ExcelArea(sheet, addr, false)

        for (row in area.wholeArea) {
            val cell = row[COL_IDX_TESTCASE]
            val macro = Excel.getCellValue(cell)
            if (toMap.containsKey(macro)) {
                updateLog.position = cell.address.formatAsString()
                updateLog.before = macro
                updateLog.after = toMap[macro] ?: ""
                updated.add(updateLog)

                // don't set value to preview changes
                if (isDryRun) continue

                cell.setCellValue(toMap[macro])
                hasUpdate = true
            }
        }

        return hasUpdate
    }

    private fun saveExcel(@NotNull file: File, @NotNull workBook: XSSFWorkbook) {
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
}
