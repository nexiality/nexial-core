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
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.ResourceUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.SHEET_DEFAULT_DATA
import org.nexial.core.NexialConst.Data.SHEET_SYSTEM
import org.nexial.core.NexialConst.Project.*
import org.nexial.core.SystemVariables.*
import org.nexial.core.excel.Excel
import org.nexial.core.excel.ExcelAddress
import org.nexial.core.excel.ExcelConfig.*
import org.nexial.core.excel.ext.CipherHelper.CRYPT_IND
import org.nexial.core.tools.ProjectToolUtils.isDataFile
import org.nexial.core.tools.ProjectToolUtils.isTestScript
import org.nexial.core.tools.VarCommandGenerator.retriveVarCmds
import org.nexial.core.tools.inspector.ArtifactType.ACTIVITY
import org.nexial.core.tools.inspector.ArtifactType.SCRIPT
import org.nexial.core.tools.inspector.DataVariableLocationType.Companion.CommandLineOverride
import org.nexial.core.tools.inspector.DataVariableLocationType.Companion.DefaultDataSheet
import org.nexial.core.tools.inspector.DataVariableLocationType.Companion.ProjectProperties
import org.nexial.core.tools.inspector.DataVariableLocationType.Companion.ScenarioDataSheet
import org.nexial.core.tools.inspector.DataVariableLocationType.Companion.StepOverride
import org.nexial.core.tools.inspector.InspectorConst.MULTI_VARS_CMDS
import org.nexial.core.tools.inspector.ProjectInspector.filterFiles
import org.nexial.core.tools.inspector.ProjectInspector.getMessage
import org.nexial.core.tools.inspector.ProjectInspector.resolveRelativePath
import org.nexial.core.utils.InputFileUtils
import java.io.File
import java.io.File.separator
import java.util.*

class DataDocGenerator(val options: InspectorOptions, val logger: InspectorLogger) {

    fun generate(): DataVariableEntity {
        val dataVariables = DataVariableEntity(File(options.directory))
        scanDataFiles(dataVariables)
        scanProjectProperties(dataVariables)
        scanBatchFiles(dataVariables)
        scanScriptFiles(dataVariables)
        analyze(dataVariables)
        return dataVariables
    }

    private fun scanDataFiles(dataVariables: DataVariableEntity) {
        val projectHome = File(options.directory)

        // find all potential data variable files
        val dataFiles = filterFiles(projectHome, arrayOf(DATA_FILE_SUFFIX)) { file -> isDataFile(file) }
        logger.title("PROCESSING DATA FILES", "found ${dataFiles.size} data files")

        if (dataFiles.isEmpty()) return

        val cacheHelper = CacheHelper<DataVariableCache>(options, logger)

        dataFiles.forEach { file ->
            val filePath = file.absolutePath

            val cacheFile = cacheHelper.resolveCacheFile(file)
            val cacheUsed = if (cacheHelper.isUsableCacheFile(cacheFile)) {
                // use cache instead
                val dataVariableCache = cacheHelper.readCache<DataVariableCache>(cacheFile)
                if (dataVariableCache != null && dataVariableCache.dataVariables.isNotEmpty()) {
                    dataVariableCache.dataVariables.forEach { addToEntity(dataVariables, it) }
                    true
                } else false
            } else false

            if (!cacheUsed) {
                if (InputFileUtils.isValidDataFile(filePath)) {
                    val fileName = file.name
                    logger.log(fileName, "parsing data file")

                    val dataFile = Excel(file)
                    val fileRelativePath = resolveRelativePath(projectHome, file)

                    val dataFileCache = DataFileCache(fileName)
                    val dataVariableCache = DataVariableCache(options.project.name, fileRelativePath, dataFileCache)

                    InputFileUtils.filterValidDataSheets(dataFile).forEach { sheet ->
                        val sheetName = sheet.name
                        val locationType = if (sheetName == SHEET_DEFAULT_DATA) DefaultDataSheet else ScenarioDataSheet

                        val dataSheetCache = DataSheetCache(sheetName)

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
                                    val definedAs = if (row[0].size > 1) Excel.getCellValue(row[0][1]) else ""

                                    dataSheetCache.data += DataCache(position = headerCell.reference,
                                                                     name = name,
                                                                     value = definedAs)
                                    val dvAtom = DataVariableAtom(name = name,
                                                                  definedAs = definedAs,
                                                                  location = "$fileRelativePath/$fileName",
                                                                  dataSheet = sheetName,
                                                                  position = headerCell.reference,
                                                                  type = locationType)
                                    addToEntity(dataVariables, dvAtom)
                                    dataVariableCache.dataVariables += dvAtom
                                }
                            }
                        }

                        dataFileCache.dataSheets += dataSheetCache
                    }

                    cacheHelper.saveCache(dataVariableCache, cacheFile)
                }
            }

            cacheHelper.expireOutdatedCache(cacheFile)
        }
    }

    private fun scanProjectProperties(dataVariables: DataVariableEntity) {
        val projectHome = File(options.directory)
        val projectProperties = File("${projectHome.absolutePath}$separator$DEF_REL_PROJECT_PROPS")
        if (FileUtil.isFileReadable(projectProperties, 5)) {
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
    }

    private fun scanBatchFiles(dataVariables: DataVariableEntity) {
        val projectHome = File(options.directory)
        val bin = File("${projectHome.absolutePath}$separator$DEF_REL_LOC_BIN")
        if (!FileUtil.isDirectoryReadable(bin)) return

        // find all potential batch files
        val batchFiles = filterFiles(bin, arrayOf("bat", "sh", "cmd")) { file -> file.length() > 0 }
        logger.title("PROCESSING BATCH FILES", "found ${batchFiles.size} batch files")
        if (batchFiles.isEmpty()) return

        batchFiles.forEach { batch ->
            val location = "${resolveRelativePath(projectHome, batch)}/${batch.name}"
            logger.log(location, "parsing...")

            val batchContent = FileUtils.readLines(batch, DEF_FILE_ENCODING)
            for ((index, line) in batchContent.withIndex()) {
                if (StringUtils.isBlank(line)) continue
                if (StringUtils.startsWithIgnoreCase(line.trim(), "rem ")) continue
                if (StringUtils.startsWithIgnoreCase(line.trim(), "# ")) continue

                // compensate for multi-line commands (windows and *nix)
                val line1 = StringUtils.removeEnd(StringUtils.removeEnd(line, "^"), "\\")

                val position = "line ${(index + 1)}"

                if (RegexUtils.match(line1, ".*\\-D(.+)=(.+).*")) {
                    val overrides = if (line1.contains("=-D")) line1.substringAfter("=-D") else line1.substringAfter("-D")
                    handleCommandlineOverrides(dataVariables, overrides, " -D", location, position)
                    continue
                }

                if (RegexUtils.match(line1, ".*\\-override\\s+(.+).*")) {
                    val overrides = line1.substringAfter("-override ")
                    handleCommandlineOverrides(dataVariables, overrides, " -override ", location, position)
                }
            }
        }
    }

    private fun scanScriptFiles(dataVariables: DataVariableEntity) {
        val projectHome = File(options.directory)

        val varCmds = retriveVarCmds()

        if (varCmds == null) {
            System.err.println("Unable to retrieve var commands: $MSG_CHECK_SUPPORT")
            return
        }

        // find all potential data variable files
        val scriptFiles = filterFiles(projectHome, arrayOf(SCRIPT_FILE_SUFFIX)) { file -> isTestScript(file) }
        logger.title("PROCESSING DATA VARIABLES", "found ${scriptFiles.size} test scripts")
        if (scriptFiles.isEmpty()) return

        val cacheHelper = CacheHelper<ScriptSuiteCache>(options, logger)

        scriptFiles.forEach { file ->

            val cacheFile = cacheHelper.resolveCacheFile(file)
            val cacheUsed = if (cacheHelper.isUsableCacheFile(cacheFile)) {
                // use cache instead
                val scriptSuiteCache = cacheHelper.readCache<ScriptSuiteCache>(cacheFile)
                if (scriptSuiteCache != null && scriptSuiteCache.dataVariables.isNotEmpty()) {
                    scriptSuiteCache.dataVariables.forEach { addToEntity(dataVariables, it) }
                    true
                } else false
            } else false

            if (!cacheUsed) {
                logger.log(file.name, "parsing script file for data variable reference")

                val location = "${resolveRelativePath(projectHome, file)}/${file.name}"
                val scriptCache = ScriptCache(file.name, SCRIPT)
                val scriptSuiteCache = ScriptSuiteCache(options.project.name, location, scriptCache)
                val excel = Excel(file)

                InputFileUtils.retrieveValidTestScenarios(excel).forEach { sheet ->
                    val sheetName = sheet.name
                    if (sheetName != SHEET_SYSTEM) {
                        val scenarioCache = ScenarioCache(sheetName, SCRIPT)
                        var activityCache: SequenceCache? = null

                        // find the last row
                        val step = excel.worksheet(sheetName)
                            .cells(ExcelAddress("${COL_TEST_CASE}5:$COL_CAPTURE_SCREEN" +
                                                "${sheet.findLastDataRow(ExcelAddress("${COL_TARGET}5")) + 1}"))

                        step.forEach { row ->
                            val activity = Excel.getCellValue(row[0])
                            if (activity.isNotBlank()) {
                                if (activityCache != null) scenarioCache.sequences += activityCache!!
                                activityCache = SequenceCache(activity, ACTIVITY, row[0].rowIndex)
                            }

                            val rowIndex = row[0].rowIndex
                            val cmdType = Excel.getCellValue(row[2])
                            val command = Excel.getCellValue(row[3])

                            if (activityCache != null) {
                                activityCache!!.steps += StepCache(
                                    row = rowIndex,
                                    description = Excel.getCellValue(row[1]),
                                    cmdType = cmdType,
                                    command = command,
                                    param1 = Excel.getCellValue(row[4]),
                                    param2 = Excel.getCellValue(row[5]),
                                    param3 = Excel.getCellValue(row[6]),
                                    param4 = Excel.getCellValue(row[7]),
                                    param5 = Excel.getCellValue(row[8]),
                                    flowControl = Excel.getCellValue(row[9]),
                                    screenshot = BooleanUtils.toBoolean(Excel.getCellValue(row[11])))
                            }

                            val commandFqn = "$cmdType.$command"
                            if (varCmds.containsKey(commandFqn)) {
                                val varIndices = varCmds.getValue(commandFqn)
                                varIndices.forEach {
                                    val dv = DataVariableAtom(name = Excel.getCellValue(row[4 + it]),
                                                              definedAs = commandFqn,
                                                              location = location,
                                                              dataSheet = sheetName,
                                                              position = "Row $rowIndex",
                                                              type = StepOverride)
                                    addToEntity(dataVariables, dv)
                                    scriptSuiteCache.dataVariables += dv
                                }
                            }

                            if (MULTI_VARS_CMDS.containsKey(commandFqn)) {
                                val vars = Excel.getCellValue(row[4 + MULTI_VARS_CMDS.getValue(commandFqn)])
                                if (vars.isNotBlank()) {
                                    TextUtils.toList(vars, ",", true).forEach {
                                        val dv = DataVariableAtom(name = it,
                                                                  definedAs = commandFqn,
                                                                  location = location,
                                                                  dataSheet = sheetName,
                                                                  position = "Row $rowIndex",
                                                                  type = StepOverride)
                                        addToEntity(dataVariables, dv)
                                        scriptSuiteCache.dataVariables += dv
                                    }
                                }
                            }
                        }

                        if (activityCache != null) scenarioCache.sequences += activityCache!!
                        scriptCache.scenarios += scenarioCache
                    }
                }

                cacheHelper.saveCache(scriptSuiteCache, cacheFile)
            }

            cacheHelper.expireOutdatedCache(cacheFile)
        }
    }

    private fun analyze(dataVariables: DataVariableEntity) {

        dataVariables.forEach { name, defs ->
            if (defs.isEmpty()) {
                options.advices += getMessage("dv.bad.definition")
            } else {
                val count = defs.size
                val firstInstance = IterableUtils.get(defs, 0)

                val locationTypes = defs.flatMap { listOf(it.type) }.distinct().sortedBy { it.order }
                val definedInDataSheet = isDefinedInDataSheet(locationTypes)

                val uniqueDefinitions = defs.flatMap { listOf(it.definedAs) }.distinct()

                if (uniqueDefinitions.isEmpty()) {
                    // Rule #3: missing definition
                    firstInstance.advices += getMessage("dv.values.missing")
                } else {
                    if (uniqueDefinitions.size == 1) {
                        var definedSameAsDefault = false

                        // Rule #2: System variable defined with default value.
                        if (name.startsWith(NAMESPACE)) {
                            val defaultValue = getDefault(name) ?: ""
                            if (uniqueDefinitions[0] == defaultValue && defaultValue.isNotEmpty()) {
                                firstInstance.advices += getMessage("dv.values.same.as.default", Pair("name", name))
                                definedSameAsDefault = true
                            }
                        }

                        // Rule #1: all defined values are all the same
                        if (count > 1 && !definedSameAsDefault && definedInDataSheet)
                            firstInstance.advices += getMessage("dv.values.same")
                    }

                    // Rule #4: missing 1 or more definition
                    if (uniqueDefinitions.isEmpty() || uniqueDefinitions.contains(""))
                        firstInstance.advices += getMessage("dv.value.missing")

                    // Rule #7: sentry no more!
                    if (name.toLowerCase().contains("sentry"))
                        firstInstance.advices += getMessage("dv.name.sentry")

                    // Rule #5: sensitive data value
                    if (isSensitiveDataLikelyExposed(name, uniqueDefinitions))
                        firstInstance.advices += getMessage("dv.value.encrypt.sensitive")

                    // Rule #6: already defined (and thus overwritten) in project.properties
                    if (uniqueDefinitions.size > 1 && locationTypes.contains(ProjectProperties) && definedInDataSheet)
                        firstInstance.advices += getMessage("dv.value.overridden")

                    // Rule #8: prefer updated System variables
                    if (name.startsWith(NAMESPACE)) {
                        val preferredName = getPreferredSystemVariableName(name)
                        if (preferredName != name)
                            firstInstance.advices += getMessage("dv.name.outdated", Pair("name", preferredName))

                        // Rule #9: unknown system variable
                        if (!isRegisteredSystemVariable(name))
                            firstInstance.advices += getMessage("dv.name.unknown", Pair("name", name))
                    }
                }
            }
        }
    }

    private fun isDefinedInDataSheet(locationTypes: List<DataVariableLocationType>) =
            locationTypes.contains(DefaultDataSheet) || locationTypes.contains(ScenarioDataSheet)

    private fun handleCommandlineOverrides(dataVariables: DataVariableEntity,
                                           overrides: String,
                                           overridePrefix: String,
                                           location: String,
                                           position: String) {
        var cmdline = overrides
        while (cmdline.isNotBlank()) {
            val moreData = cmdline.contains(overridePrefix)
            val dvPair = StringUtils.split(if (moreData) cmdline.substringBefore(overridePrefix)
                                           else cmdline, "=")
            if (dvPair.isNotEmpty()) {
                val name = dvPair[0]
                if (StringUtils.isNotBlank(name)) {
                    val definedAs = if (dvPair.size > 1) dvPair[1] else ""
                    addToEntity(dataVariables,
                                DataVariableAtom(name = name,
                                                 definedAs = definedAs,
                                                 location = location,
                                                 position = position,
                                                 type = CommandLineOverride))
                }
            }

            if (moreData) cmdline = cmdline.substringAfter(overridePrefix)
            else break
        }
    }

    private fun addToEntity(dataVariables: DataVariableEntity, dvAtom: DataVariableAtom) {
        if (!dataVariables.containsKey(dvAtom.name)) dataVariables[dvAtom.name] = TreeSet()
        dataVariables[dvAtom.name]!!.add(dvAtom)
    }

    private fun isSensitiveDataLikelyExposed(name: String, values: List<String>): Boolean {
        if (name.isEmpty() || values.isEmpty()) return false

        // test data variable name
        val varName = name.toLowerCase()
        return if (RegexUtils.match(varName, ".*password.*") ||
                   RegexUtils.match(varName, ".*secret.*(key|code).*") ||
                   RegexUtils.match(varName, ".*access.*(key|code).*"))
        // test defined values
            values.any { !it.startsWith(CRYPT_IND) && !it.startsWith("$" + "{") && !it.startsWith("$(") }
        else
            false
    }
}