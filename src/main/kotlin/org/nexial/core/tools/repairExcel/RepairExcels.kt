package org.nexial.core.tools.repairExcel

import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFRow
import org.nexial.core.CommandConst.DEPRECATED_VARS
import org.nexial.core.CommandConst.READ_ONLY_VARS
import org.nexial.core.CommandConst.UPDATED_VARS
import org.nexial.core.ExecutionInputPrep
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.tools.ProjectToolUtils.isData
import org.nexial.core.tools.ProjectToolUtils.isMacro
import org.nexial.core.tools.ProjectToolUtils.isPlan
import org.nexial.core.tools.ProjectToolUtils.isTestScript
import org.nexial.core.tools.repairExcel.RepairExcels.ArtifactType.*
import org.nexial.core.utils.InputFileUtils
import java.io.File

object RepairExcels {
    const val TEMP_SHEET_NAME = "TempSheet$12345"
    private val rowsToIgnore = arrayListOf(0, 2, 3)

    enum class ArtifactType(val fileName: String, val defSheet: String, val excelAddress: ExcelAddress) {
        PLAN("nexial-testplan.xlsx", "Test Plan", ADDR_PLAN_EXECUTION_START),
        SCRIPT("nexial-script.xlsx", "Scenario", ADDR_COMMAND_START),
        MACRO("nexial-macro.xlsx", "MacroLibrary", ADDR_MACRO_COMMAND_START),
        DATA("nexial-data.xlsx", "#default", ADDR_FIRST_DATA_COL)
    }

    fun getFileType(file: File): ArtifactType? {
        return when {
            isData(file)       -> DATA
            isTestScript(file) -> SCRIPT
            isMacro(file)      -> MACRO
            isPlan(file)       -> PLAN
            else               -> null
        }
    }

    // check rows which has headers i.e. row to be ignored or not
    fun ignoreRowToCopy(fileType: ArtifactType, rowIndex: Int): Boolean {
        return when (fileType) {
            SCRIPT -> rowsToIgnore.contains(rowIndex)
            PLAN   -> rowsToIgnore.contains(rowIndex)
            MACRO  -> rowIndex == 0
            DATA   -> false
        }
    }

    // remove row if variable is deprecated.
    // update variable name from first row if its updated
    fun removeDataVars(row: XSSFRow): Boolean {
        val cell = row.getCell(0) ?: return false
        val cellValue = cell.stringCellValue
        if (DEPRECATED_VARS.contains(cellValue) || READ_ONLY_VARS.contains(cellValue)) return true
        if (UPDATED_VARS.containsKey(cellValue)) {
            cell.setCellValue(UPDATED_VARS[cellValue])
        }
        return false
    }

    /**
     * Find last column index of the row. All files except data files have predefined last column index
     * @param row XSSFRow to find last column index(Required only in case of data)
     * @param fileType [ArtifactType] file type to evaluate last column column
     * @return [Int] last column index for the row
     * */
    fun lastColumnIdx(row: XSSFRow?, fileType: ArtifactType): Int {
        return when (fileType) {
            SCRIPT -> COL_IDX_CAPTURE_SCREEN
            MACRO  -> COL_IDX_CAPTURE_SCREEN
            PLAN   -> COL_IDX_PLAN_LOAD_TEST_SPEC
            DATA   -> row!!.lastCellNum.toInt() - 1
        }
    }

    /**
     * Check if cell has strike out or not.
     * @param oldCell [XSSFCell] cell to check strikeout in.
     * @param fileType [ArtifactType] artifact type of the file
     * @return [Boolean] true if oldCell has strike out else return false
     * */
    fun isStrikeOut(oldCell: XSSFCell?, fileType: ArtifactType): Boolean {
        return when (fileType) {
            SCRIPT -> ExecutionInputPrep.isTestStepDisabled(oldCell)
            MACRO  -> ExecutionInputPrep.isMacroStepDisabled(oldCell)
            PLAN   -> ExecutionInputPrep.isPlanStepDisabled(oldCell)
            DATA   -> false
        }
    }

    //List out all the valid worksheets from provided [excel] depending on artifact type
    fun retrieveValidSheet(excel: Excel, fileType: ArtifactType): List<Worksheet> {
        return when (fileType) {
            SCRIPT -> InputFileUtils.retrieveValidTestScenarios(excel)
            MACRO  -> InputFileUtils.retrieveValidMacros(excel)
            PLAN   -> InputFileUtils.retrieveValidPlanSequence(excel)
            DATA   -> InputFileUtils.filterValidDataSheets(excel)
        }
    }
}