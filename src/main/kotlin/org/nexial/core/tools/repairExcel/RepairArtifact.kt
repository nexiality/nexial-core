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

package org.nexial.core.tools.repairExcel

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.apache.commons.lang3.time.StopWatch
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS
import org.nexial.core.NexialConst.Project.NEXIAL_HOME
import org.nexial.core.NexialConst.Project.SCRIPT_FILE_SUFFIX
import org.nexial.core.NexialConst.TEMP
import org.nexial.core.excel.Excel
import org.nexial.core.tools.CliConst.OPT_VERBOSE
import org.nexial.core.tools.CliUtils.getCommandLine
import org.nexial.core.tools.CliUtils.newArgOption
import org.nexial.core.tools.ProjectToolUtils.formatColumns
import org.nexial.core.tools.inspector.InspectorConst.exit
import org.nexial.core.tools.repairExcel.RepairExcels.ArtifactType
import org.nexial.core.tools.repairExcel.RepairExcels.getFileType
import org.nexial.core.tools.repairExcel.RepairExcels.lastColumnIdx
import org.nexial.core.utils.ConsoleUtils
import org.springframework.util.CollectionUtils
import java.io.File
import java.io.File.separator

object RepairArtifact {
    private var searchFrom: String? = null
    private var previewDirectory: String? = null
    private val repaired = mutableListOf<RepairArtifactLog>()
    private val newTemplateLoc = TEMP + RandomStringUtils.randomAlphabetic(5) + separator
    private val tempDir = TEMP + RandomStringUtils.randomAlphabetic(5) + separator

    @JvmStatic
    fun main(args: Array<String>) {
        deriveOptions(deriveCommandLine(args))
        repairArtifacts()
    }

    private fun deriveCommandLine(args: Array<String>): CommandLine {
        val cmdOptions = Options()
        cmdOptions.addOption(OPT_VERBOSE)

        cmdOptions.addOption(newArgOption("t", "target", "[REQUIRED] File or directory to repair", true))
        cmdOptions
            .addOption(newArgOption("d", "destination", "[OPTIONAL] Destination for preview files", false))

        val programExt = if (IS_OS_WINDOWS) ".cmd" else ".sh"
        val cmd = getCommandLine("nexial-artifact-repair$programExt", args, cmdOptions)
        if (cmd == null) {
            ConsoleUtils.error("unable to proceed... exiting")
            exit(RC_BAD_CLI_ARGS)
        }
        return cmd
    }

    private fun deriveOptions(cmd: CommandLine) {
        searchFrom = cmd.getOptionValue("t")
        if (StringUtils.isBlank(searchFrom)) {
            ConsoleUtils.error("Missing target parameter.")
            exit(RC_BAD_CLI_ARGS)
        }

        if (cmd.hasOption("d")) {
            previewDirectory = cmd.getOptionValue("d")
            if (StringUtils.isBlank(previewDirectory)) {
                ConsoleUtils.error("Destination for preview is empty.")
                exit(RC_BAD_CLI_ARGS)
            }
        }
    }

    private fun repairArtifacts() {
        val prompt = if (previewDirectory == null) "Artifact Repair Summary" else "Artifact Repair Preview"
        val column3Name = if (previewDirectory == null) "Backup" else "Preview Location"
        val banner = StringUtils.repeat('-', 120)

        println("/$banner\\")
        println("|" + ConsoleUtils.centerPrompt(prompt, 120) + "|")
        println("\\$banner/")
        repairArtifact()
        println()
        println("$banner--")

        if (repaired.isEmpty()) {
            println(ConsoleUtils.centerPrompt("There are no matching files.", 122))
            println("$banner--")
        } else {
            println(formatColumns("File", "Process Time", column3Name))
            println("$banner--")
            repaired.forEach { println(it) }
            println()
        }
    }

    private fun repairArtifact() {
        val nexialHome = System.getProperty(NEXIAL_HOME)
        if (StringUtils.isBlank(nexialHome)) {
            throw RuntimeException("System property $NEXIAL_HOME missing; unable to proceed")
        }

        if (previewDirectory != null) {
            if (File(previewDirectory).exists() && File(previewDirectory).isFile) {
                throw RuntimeException("Destination location for preview is a file: must be directory")
            }
        }

        val template = File(nexialHome + separator + "template")
        // list all template files and pre-process all of them
        FileUtils.listFiles(template, arrayOf(SCRIPT_FILE_SUFFIX), false).forEach { processTemplate(it) }

        // list all files with xlsx extension
        val listFiles = listFiles()
        if (CollectionUtils.isEmpty(listFiles)) return
        ExcelUtils.searchFrom = searchFrom

        listFiles.forEach { file ->
            val stopWatch = StopWatch()
            stopWatch.start()
            val fileType = getFileType(file) ?: return@forEach
            val targetFile = File(tempDir + fileType.fileName)

            val repairLog = ExcelUtils.copyExcel(file, targetFile, previewDirectory, fileType)
            stopWatch.stop()
            if (repairLog != null) {
                repairLog.processTime = stopWatch.time
                repaired.add(repairLog)
            }
        }
    }

    // remove unnecessary contents from template files and save to new temp location
    private fun processTemplate(templateFile: File) {
        // copy template file from `NEXIAL_HOME/template` to temp folder
        val file = File(newTemplateLoc + templateFile.name)
        FileUtils.copyFile(templateFile, file)

        // get file type from template file name
        val fileType: ArtifactType = getArtifactType(file) ?: return
        val defSheet = fileType.defSheet
        val excelAddress = fileType.excelAddress

        val excel = Excel.asXlsxExcel(file.absolutePath, false, false)
        val targetWorkbook = excel.workbook
        val targetSheet = excel.worksheet(defSheet)
        val lastDataRow = targetSheet.findLastDataRow(excelAddress)

        // clear unnecessary contents from default sheets
        for (i in excelAddress.rowStartIndex until lastDataRow) {
            val sheet = targetSheet.sheet
            val row = sheet.getRow(i)
            val lastColumnIdx = lastColumnIdx(row, fileType)
            for (cellIndex in 0 until lastColumnIdx + 1) {
                val cell = row.getCell(cellIndex) ?: continue
                cell.setCellValue("")
            }
        }
        // rename default sheet to temporary unique name
        ExcelUtils.renameSheet(targetWorkbook, defSheet)
        // save new template to another template location
        Excel.save(File(tempDir + file.name), targetWorkbook)
    }

    // get file type of template file
    private fun getArtifactType(file: File): ArtifactType? {
        RepairExcels.ArtifactType.values().forEach { fileType ->
            if (file.name == fileType.fileName) {
                return fileType
            }
        }
        return null
    }

    private fun listFiles(): MutableList<File> {
        return when {
            // recursively search for script file from provided path
            FileUtil.isDirectoryReadable(searchFrom) -> FileUtils.listFiles(File(searchFrom),
                                                                            arrayOf(SCRIPT_FILE_SUFFIX),
                                                                            true) as MutableList<File>
            FileUtil.isFileReadable(searchFrom)      -> mutableListOf(File(searchFrom))
            else                                     -> throw IllegalArgumentException(
                "'$searchFrom' is not a readable directory/file")
        }
    }

    data class RepairArtifactLog(var file: File, var processTime: Long, var newFile: File) {
        override fun toString(): String {
            return formatColumns(file.absolutePath, "$processTime", newFile.absolutePath)
        }
    }
}
