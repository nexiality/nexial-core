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
package org.nexial.core.tms.model

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FilenameUtils
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFCell
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelConfig
import org.nexial.core.tms.spi.TmsMD5Cache.generateMD5

/**
 * Represents a single TMS test case, mapped to a scenario in Nexial Script
 */
class TmsTestCase(val worksheet: Worksheet) {
    val name: String = worksheet.name
    var tmsIdRef: String
    var testCaseName: String
    val scriptName: String
    val description: String
    var row = -1 // only to be used in case of plan import
    var testSteps: List<TmsTestStep>? = null
    var cache: String? = null

    init {
        testCaseName = worksheet.name
        description = Excel.getCellValue(worksheet.cell(ExcelConfig.ADDR_SCENARIO_DESCRIPTION))
        scriptName = FilenameUtils.removeExtension(worksheet.file.name)
        tmsIdRef = Excel.getCellValue(worksheet.cell(ExcelConfig.ADDR_TEST_ID))
    }

    fun setCache() {
        cache = generateMD5(this)
    }

    fun setPlanTestCaseName(row: Int) {
        this.row = row
        testCaseName = "$scriptName/$name/$row"
    }

    /**
     * Write the test case id into the Test id cell of the current worksheet
     */
    fun writeToFile() {
        val xssfCellStyle = worksheet.newCellStyle()
        xssfCellStyle.setAlignment(HorizontalAlignment.CENTER)
        xssfCellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
        worksheet.cell(ExcelConfig.ADDR_TEST_ID).cellStyle = xssfCellStyle
        worksheet.cell(ExcelConfig.ADDR_TEST_ID).setCellValue(tmsIdRef)
    }
}


/**
 * Represents a test step that resides inside a test case. Mapped to an activity in Nexial
 */
class TmsTestStep(val testCase: TmsTestCase, var name: String, val messageId: String) {
    constructor(testCase: TmsTestCase, name: String) :
            this(testCase, name, "[${testCase.scriptName}][${testCase.name}][$name]")

    val tmsCustomSteps: MutableList<TmsCustomStep> = ArrayList()
    fun addTmsCustomTestStep(testStep: TmsCustomStep) = tmsCustomSteps.add(testStep)
}

/**
 * Represents a single Nexial test step, to be imported into TMS as custom separated steps
 */
class TmsCustomStep(var row: List<XSSFCell?>, var testCase: TmsTestStep?) {
    val description: String
    val rowIndex: Int

    init {
        assert(testCase != null)
        assert(CollectionUtils.isNotEmpty(row))
        rowIndex = row[0]!!.rowIndex
        description = Excel.getCellValue(row[ExcelConfig.COL_IDX_DESCRIPTION])
    }
}
