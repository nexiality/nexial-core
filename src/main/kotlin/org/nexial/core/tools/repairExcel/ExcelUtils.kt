/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package org.nexial.core.tools.repairExcel

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.poi.ss.usermodel.CellType.*
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.nexial.core.NexialConst.DF_TIMESTAMP
import org.nexial.core.excel.Excel
import org.nexial.core.excel.ExcelConfig.StyleConfig.STRIKEOUT_COMMAND
import org.nexial.core.excel.ExcelConfig.StyleDecorator
import org.nexial.core.tools.ProjectToolUtils
import org.nexial.core.tools.repairExcel.RepairArtifact.RepairArtifactLog
import org.nexial.core.tools.repairExcel.RepairExcels.ArtifactType
import org.nexial.core.tools.repairExcel.RepairExcels.ArtifactType.*
import org.nexial.core.tools.repairExcel.RepairExcels.TEMP_SHEET_NAME
import org.nexial.core.tools.repairExcel.RepairExcels.retrieveValidSheet
import org.springframework.util.CollectionUtils
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.util.*

object ExcelUtils {
    private val runId = DF_TIMESTAMP.format(Date())
    var searchFrom: String? = null

    /**
     * @param file Excel file to be repaired
     * @param targetFile Excel template file to which file content to be copied
     * @param previewLocation Preview changes. It'll create file at preview location. No change with original file[file]
     * @param fileType [file] Artifact Type of file. e.g. [DATA], [MACRO], [SCRIPT].
     * @return [RepairArtifactLog]
     * */
    fun copyExcel(file: File, targetFile: File, previewLocation: String?, fileType: ArtifactType): RepairArtifactLog? {
        if (searchFrom == null) {
            throw IOException("Search directory is not readable or accessible: Unable to proceed")
        }

        ProjectToolUtils.log("processing", file)
        val srcExcel = Excel.asXlsxExcel(file.absolutePath, true, false)
        if (srcExcel == null) {
            ProjectToolUtils.log("processed (no valid source file)", file)
            return null
        }

        // retrieve all valid sheets from file.
        // empty sheets will be ignored
        val sourceSheets = retrieveValidSheet(srcExcel, fileType)
        if (CollectionUtils.isEmpty(sourceSheets)) {
            ProjectToolUtils.log("processed (no valid sheets)", file)
            return null
        }

        val targetExcel = Excel.asXlsxExcel(targetFile.absolutePath, false, false)
        val targetWorkbook = targetExcel.workbook
        val excelAddress = fileType.excelAddress
        try {
            for (sourceSheet in sourceSheets) {
                val lastDataRow = sourceSheet.findLastDataRow(excelAddress)
                copySheet(sourceSheet.sheet, targetWorkbook, lastDataRow, fileType)
            }
        } catch (e: Exception) {
            ProjectToolUtils.log("Unable to proceed repair file ${file.absolutePath} further due to ${e.message}")
        } finally {
            // delete temporary sheet
            targetWorkbook.removeSheetAt(targetWorkbook.getSheetIndex(TEMP_SHEET_NAME))
            val updateLog = saveFile(file, previewLocation, targetWorkbook)
            targetWorkbook.close()
            return updateLog
        }
    }

    /**
     * @param sourceSheet [XSSFSheet] sheet to be copied
     * @param targetWorkbook [XSSFWorkbook] workbook to which [sourceSheet] to be copied
     * @param lastDataRow number to rows in defaultSheet of target workbook
     * @param fileType [ArtifactType] type of excel being copied
     * @return sheet to which data is copied
     * */
    private fun copySheet(sourceSheet: XSSFSheet, targetWorkbook: XSSFWorkbook,
                          lastDataRow: Int, fileType: ArtifactType): XSSFSheet? {
        val sheetName = sourceSheet.sheetName
        val tempSheetIndex = targetWorkbook.getSheetIndex(TEMP_SHEET_NAME)
        val targetSheet = targetWorkbook.cloneSheet(tempSheetIndex, sheetName)

        var destRowIndex = 0
        for (rowIndex in 0 until lastDataRow) {
            if (!RepairExcels.ignoreRowToCopy(fileType, rowIndex)) {
                val sourceRow = getRow(sourceSheet, rowIndex)
                // remove rows from data sheets if system variable is read only.
                // Also update variable name if any
                if (fileType == DATA && RepairExcels.removeDataVars(sourceRow)) continue

                val destRow = getRow(targetSheet, destRowIndex)
                copyRow(sourceRow, destRow, fileType)
            }
            destRowIndex++
        }
        return targetSheet
    }

    /**
     * @param sourceRow [XSSFRow] to copy from source sheet
     * @param newRow [XSSFRow] new row in the target sheet where source row to be coopied
     * @param fileType [ArtifactType] type of file. By default it is [DATA]
     */
    fun copyRow(sourceRow: XSSFRow, newRow: XSSFRow, fileType: ArtifactType = DATA) {
        val lastDataColumn = RepairExcels.lastColumnIdx(sourceRow, fileType)

        for (columnIndex in 0 until lastDataColumn + 1) {
            val oldCell = sourceRow.getCell(columnIndex)
            var newCell = newRow.getCell(columnIndex)

            // If the old cell is null jump to next cell with new cell as empty
            if (oldCell == null) {
                newCell = null
                continue
            } else {
                newCell = newCell ?: newRow.createCell(columnIndex)
            }

            val oldCellType = oldCell.cellTypeEnum
            // set cell value
            when (oldCellType) {
                BLANK   -> newCell = null
                FORMULA -> newCell.cellFormula = oldCell.cellFormula
                NUMERIC -> {
                    oldCell.setCellType(STRING)
                    newCell.setCellValue(oldCell.stringCellValue)
                }
                BOOLEAN -> {
                    oldCell.setCellType(STRING)
                    newCell.setCellValue(oldCell.stringCellValue)
                }
                else    -> newCell.setCellValue(oldCell.stringCellValue)
            }

            // create styles for cell only if strikethrough is present
            if (RepairExcels.isStrikeOut(oldCell, fileType)) {
                newCell.cellStyle = StyleDecorator.generate(newRow.sheet.workbook, STRIKEOUT_COMMAND)
            }
        }
    }

    // rename sheet to some unique temporary sheet name
    fun renameSheet(targetWorkbook: XSSFWorkbook, defSheetName: String) {
        val sheetIndex = targetWorkbook.getSheetIndex(defSheetName)
        targetWorkbook.cloneSheet(sheetIndex, TEMP_SHEET_NAME)
        targetWorkbook.removeSheetAt(sheetIndex)
    }

    @Throws(IOException::class)
    private fun saveFile(file: File, preview: String?, targetWorkbook: XSSFWorkbook): RepairArtifactLog? {
        var action = "processed (not changed)"
        val destFile: File

        if (preview != null) {
            // create folder structure as it is from searchFrom
            var fileSuffix = file.name
            if (file.absolutePath != searchFrom) {
                fileSuffix = StringUtils.substringAfterLast(file.absolutePath, searchFrom).removePrefix(separator)
            }
            destFile = File(StringUtils.appendIfMissing(preview, separator) + fileSuffix)
            Excel.save(destFile, targetWorkbook)
        } else {
            destFile = File("${file.absolutePath}.$runId")
            FileUtils.moveFile(file, destFile)
            action = "processed (changed)"
            Excel.save(file, targetWorkbook)
        }
        ProjectToolUtils.log(action, file)
        return RepairArtifactLog(file, 0, destFile)
    }

    private fun getRow(sheet: XSSFSheet, rowNum: Int) = sheet.getRow(rowNum) ?: sheet.createRow(rowNum)
}