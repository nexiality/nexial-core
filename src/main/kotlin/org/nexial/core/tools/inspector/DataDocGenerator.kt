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
import org.apache.commons.collections4.IterableUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.Data.SHEET_DEFAULT_DATA
import org.nexial.core.NexialConst.NAMESPACE
import org.nexial.core.NexialConst.Project.DEF_REL_LOC_BIN
import org.nexial.core.NexialConst.Project.DEF_REL_PROJECT_PROPS
import org.nexial.core.NexialConst.getDefault
import org.nexial.core.excel.Excel
import org.nexial.core.excel.Excel.Worksheet
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ext.CipherHelper.CRYPT_IND
import org.nexial.core.tools.inspector.DataVariableLocationType.Companion.DefaultDataSheet
import org.nexial.core.tools.inspector.DataVariableLocationType.Companion.ProjectProperties
import org.nexial.core.tools.inspector.DataVariableLocationType.Companion.ScenarioDataSheet
import org.nexial.core.tools.inspector.ProjectInspector.filterFiles
import org.nexial.core.tools.inspector.ProjectInspector.getMessage
import org.nexial.core.tools.inspector.ProjectInspector.resolveRelativePath
import org.nexial.core.utils.InputFileUtils
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.util.*

class DataDocGenerator(val options: InspectorOptions, val logger: InspectorLogger) {

    fun generate(): DataVariableEntity {
        val dataVariables = DataVariableEntity(File(options.directory))

        scanDataFiles(dataVariables)
        scanProjectProperties(dataVariables)
//        scanBatchFiles(dataVariables)
//        scanScriptFiles(dataVariables)
//        sortDataVariables(dataVariables)
        analyze(dataVariables)

        return dataVariables
    }

    private fun isDataFile(file: File): Boolean =
            !file.name.startsWith("~") && !file.absolutePath.contains("${separator}output$separator")

    private fun scanDataFiles(dataVariables: DataVariableEntity) {
        val projectHome = File(options.directory)

        // find all potential data variable files
        val dataFiles = filterFiles(projectHome, arrayOf("data.xlsx")) { file -> isDataFile(file) }
        logger.log("found ${dataFiles.size} data files")
        if (dataFiles.isEmpty()) return

        dataFiles.forEach { file ->
            try {
                val filePath = file.absolutePath
                if (InputFileUtils.isValidDataFile(filePath)) {
                    logger.log("parsing data file", filePath)

                    val dataFile = Excel(file)
                    val fileName = file.name
                    val fileRelativePath = resolveRelativePath(projectHome, file)

                    InputFileUtils.filterValidDataSheets(dataFile).forEach { sheet: Worksheet ->
                        val sheetName = sheet.name
                        val locationType =
                                if (StringUtils.equals(sheetName, SHEET_DEFAULT_DATA)) DefaultDataSheet
                                else ScenarioDataSheet

                        // find the last row
                        val addr = ExcelAddress("A1")
                        val endRowIndex = sheet.findLastDataRow(addr) + 1

                        // first pass: read all nexial scope settings
                        for (i in 1 until endRowIndex) {
                            val endColumnIndex = sheet.findLastDataColumn(ExcelAddress("A$i"))
                            val endColumn = ExcelAddress.toLetterCellRef(endColumnIndex)
                            val addrThisRow = ExcelAddress("A$i:$endColumn$i")
                            val row = sheet.cells(addrThisRow)

                            // capture only nexial scope data
                            if (CollectionUtils.isNotEmpty(row)) {
                                // column A must be defined with data name
                                val headerCell = row[0][0]
                                val name = Excel.getCellValue(headerCell)
                                if (StringUtils.isNotBlank(name)) {
                                    addToEntity(dataVariables,
                                                DataVariableAtom(name,
                                                                 if (row[0].size > 1) Excel.getCellValue(row[0][1]) else "",
                                                                 "$fileRelativePath/$fileName",
                                                                 sheetName,
                                                                 headerCell.reference,
                                                                 locationType))
                                }
                            }
                        }

                    }
                }
            } catch (e: IOException) {
                logger.error("Error parsing $file: ${e.message}")
            }
        }
    }

    private fun scanProjectProperties(dataVariables: DataVariableEntity) {
        val projectHome = File(options.directory)
        val projectProperties = File("${projectHome.absolutePath}$separator$DEF_REL_PROJECT_PROPS")
        val projectProps = ResourceUtils.loadProperties(projectProperties)
        if (projectProps == null || projectProps.isEmpty) return

        projectProps.forEach { prop ->
            val name = prop.key as String
            if (StringUtils.isNotEmpty(name)) {
                addToEntity(dataVariables, DataVariableAtom(name = name,
                                                            definedAs = prop.value as String,
                                                            location = DEF_REL_PROJECT_PROPS,
                                                            type = ProjectProperties))
            }
        }
    }

    private fun scanBatchFiles(dataVariables: DataVariableEntity) {
        val projectHome = File(options.directory)
        val bin = File("${projectHome.absolutePath}$separator$DEF_REL_LOC_BIN")

        // find all potential batch files
        val batchFiles = filterFiles(bin, arrayOf("bat", "sh", "cmd")) { file -> file.length() > 0 }
        logger.log("found ${batchFiles.size} batch files")
        if (batchFiles.isEmpty()) return

//            // look for pattern:
//            // 1. .+JAVA_OPT.*=.*-D...=... -D...=...
//            // 2. -override ...=...
//
//            try {
//                val content = FileUtils.readFileToString(file,UTF8)
//
//            } catch (e: IOException) {
//                logger.error("Error reading $file: ${e.message}")
//            }
//        }
    }

    private fun addToEntity(dataVariables: DataVariableEntity, dvAtom: DataVariableAtom) {
        if (!dataVariables.containsKey(dvAtom.name)) dataVariables[dvAtom.name] = TreeSet()
        dataVariables[dvAtom.name]!!.add(dvAtom)
    }

    private fun analyze(dataVariables: DataVariableEntity) {
        // scan Use of the default values.Recommends for in the elimination
        // scan for duplicate values in multiple sheets
        // scan for duplicate values in same sheet

        dataVariables.forEach { name, defs ->
            if (defs.isEmpty()) {
                options.advices += getMessage("dv.bad.definition")
            } else {
                val defCount = defs.size
                val firstInstance = IterableUtils.get(defs, 0)

                val uniqueDefinedValues = defs.flatMap { def -> listOf(def.definedAs) }.distinct()

                if (uniqueDefinedValues.isEmpty()) {
                    // Rule #3: missing definition
                    firstInstance.advices += getMessage("dv.values.missing")
                } else {
                    if (uniqueDefinedValues.size == 1) {
                        // Rule #1: all defined values are all the same
                        if (defCount > 1) firstInstance.advices += getMessage("dv.values.same")

                        // Rule #2: System variable defined with default value.
                        if (name.startsWith(NAMESPACE)) {
                            val defaultValue = getDefault(name) ?: ""
                            if (uniqueDefinedValues[0] == defaultValue) {
                                firstInstance.advices += getMessage("dv.values.same.as.default", Pair("name", name))
                            }
                        }
                    }

                    // Rule #4: missing 1 or more definition
                    if (uniqueDefinedValues.contains("")) firstInstance.advices += getMessage("dv.value.missing")

                    // Rule #5: sensitive data value
                    if (isSensitiveDataLikelyExposed(name, uniqueDefinedValues))
                        firstInstance.advices += getMessage("dv.value.encrypt.sensitive")

                    // Rule #4: already defined (and thus overwritten) in project.properties

                    // Rule #5: data variable defined in project.properties has same value as default
                }
            }

        }
    }

    private fun isSensitiveDataLikelyExposed(name: String, values: List<String>): Boolean {
        if (name.isEmpty() || values.isEmpty()) return false

        // test data variable name
        val lname = name.toLowerCase()
        return if (RegexUtils.match(lname, ".*password.*") ||
                   RegexUtils.match(lname, ".*secret.*(key|code).*") ||
                   RegexUtils.match(lname, ".*access.*(key|code).*"))
        // test defined values
            values.any { !it.startsWith(CRYPT_IND) && !it.startsWith("$" + "{") && !it.startsWith("$(") }
        else
            false
    }
}