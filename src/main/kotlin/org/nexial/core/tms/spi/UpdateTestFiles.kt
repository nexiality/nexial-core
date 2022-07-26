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
package org.nexial.core.tms.spi

import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst
import org.nexial.core.NexialConst.NL
import org.nexial.core.excel.Excel
import org.nexial.core.tms.model.TmsSuite
import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.InputFileUtils
import java.io.File
import java.io.IOException
import java.util.*
import java.util.stream.Collectors

/**
 * Update the nexial plan and nexial script file with test and suite ids after suite operation
 */
object UpdateTestFiles {
    /**
     * Update the plan file with the suite id and the scenarios in the script file mentioned in the subplan
     * with the corresponding test case ids.
     *
     * @param testPath plan file path to update
     * @param suite    suite details
     * @param subplan  the subplan name
     */
    @Throws(TmsException::class)
    internal fun updatePlanFile(testPath: String, suite: TmsSuite, subplan: String) {
        deduplicateValues(ReadPlan.scriptToStep!!).forEach { updateScriptFile(it.value, suite, false) }
        writeToPlanFile(testPath, subplan, suite)
    }

    /**
     * Update the scenarios in the script file with the suite id and test case ids.
     *
     * @param scriptFile the path of the script file
     * @param suite      suite details
     * @param isScript   Boolean, if true file type of suite import/update is script otherwise plan
     */
    internal fun updateScriptFile(scriptFile: String, suite: TmsSuite, isScript: Boolean) {
        val updatedTestIds: MutableMap<String?, String?> = LinkedHashMap()
        var script: Excel? = null
        try {
            ConsoleUtils.log("Updating test file: $scriptFile")
            script = InputFileUtils.resolveValidScript(scriptFile)
            if (script == null) {
                ConsoleUtils.error("Unable to read script file: $scriptFile")
                if (MapUtils.isNotEmpty(updatedTestIds)) { logChanges(scriptFile, updatedTestIds) }
                return
            }
            val scriptName1 = FilenameUtils.removeExtension(script.file.name)
            suite.testCases?.forEach { (scenario, newTestId) ->
                val scriptName = StringUtils.substringBefore(scenario, "/")
                if (!isScript && !StringUtils.equals(scriptName1, scriptName)) { return@forEach }
                val worksheetName = if (isScript) scenario else StringUtils.substringBetween(scenario, "/")
                val worksheet = script.worksheet(worksheetName)
                val tmsTestScenario = TmsTestCase(worksheet)
                val oldId = tmsTestScenario.tmsIdRef
                val updatedTestId = getUpdatedTestId(suite.id, oldId, newTestId, isScript)
                tmsTestScenario.tmsIdRef = updatedTestId
                tmsTestScenario.writeToFile()
                updatedTestIds[worksheetName] = updatedTestId
            }

            script.save()
            FileUtils.copyFile(script.file, File(scriptFile))
            ConsoleUtils.log("Updated test file: $scriptFile")
            script.close()
        } catch (e: IOException) {
            ConsoleUtils.log("")
            ConsoleUtils.error("Unable to update to excel file: " + e.message)
            if (MapUtils.isNotEmpty(updatedTestIds)) { logChanges(scriptFile, updatedTestIds) }
        } finally {
            if (script != null) {
                try {
                    script.close()
                } catch (ex: IOException) {
                    ConsoleUtils.log("Unable to close script excel")
                }
            }
        }
    }

    /**
     * Update the current suite id and test id in the scenario with the new suite id and test id and
     * return the updated ids.
     *
     * @param suiteId  suite id
     * @param oldId    the current contents of the "test id" cell in the scenario
     * @param newId    the test case corresponding to the scenario
     * @param isScript boolean true if type of file is "script" otherwise "plan"
     * @return the updated test id
     */
    private fun getUpdatedTestId(suiteId: String, oldId: String, newId: String, isScript: Boolean): String {
        var oldId1 = oldId
        if (StringUtils.contains(oldId1, suiteId)) {
            if (isScript) {
                oldId1 = StringUtils.replace(oldId1, StringUtils.substringBetween(suiteId, NexialConst.NL),
                    StringUtils.join(suiteId, "/", newId, NexialConst.NL))
            }
        } else {
            oldId1 += StringUtils.join(suiteId, "/", newId, NexialConst.NL)
        }
        return oldId1
    }

    /**
     * Write the suite id into the plan file in the subplan specified.
     *
     * @param subplan the subplan name
     * @param suite   the suite details
     */
    @Throws(TmsException::class)
    private fun writeToPlanFile(testPath: String, subplan: String, suite: TmsSuite) {
        val testPlanFile = File(testPath)
        var excel: Excel? = null
        try {
            excel = Excel(testPlanFile, NexialConst.Data.DEF_OPEN_EXCEL_AS_DUP, false)
            val subplans = InputFileUtils.retrieveValidPlanSequence(excel)
            val first = subplans.stream().filter { p: Excel.Worksheet -> p.name == subplan }.findFirst()
            if (!first.isPresent) {
                throw TmsException("Unable to find subplan '$subplan' inside the plan file '$testPath'.")
            }
            val sheet = first.get()
            val tmsTestScenario = TmsTestCase(sheet)
            tmsTestScenario.tmsIdRef = suite.id
            tmsTestScenario.writeToFile()
            excel.save()
            FileUtils.copyFile(sheet.file, testPlanFile)
        } catch (e: IOException) {
            ConsoleUtils.error("Unable to read/write to the plan file: ${e.message}")
            ConsoleUtils.log("")
            ConsoleUtils.error(
                "Please update the suite id in (Column H) of $NL" +
                        " Subplan   :: '" + subplan + "'$NL" +
                        " Plan File ::'" + testPath + "'$NL" +
                        " Suite ID  :: " + suite.id
            )
            ConsoleUtils.log("")
        } finally {
            if (excel != null) {
                try {
                    excel.close()
                } catch (e: IOException) {
                    ConsoleUtils.error("Unable to close the plan excel")
                }
            }
        }
    }

    /**
     * Remove duplicate entries of script file in case of plan.
     *
     * @param map Map of duplicated script values
     * @return map of unique scripts values
     */
    private fun deduplicateValues(map: Map<Int, String>): MutableMap<Int, String> {
        val inverse = map.keys.stream().collect(
            Collectors.toMap({ key -> map[key] }, { key -> key }, { a, b -> a.coerceAtLeast(b) })
        ) // take the highest key on duplicate values
        return inverse.keys.stream().collect(Collectors.toMap({ key -> inverse[key] }, { key -> key }))
    }


    private fun logChanges(scriptFile: String, updatedTestIds: Map<String?, String?>) {
        ConsoleUtils.error("")
        ConsoleUtils.error("Please update 'test id column(H) of $scriptFile' with following tms ref:")
        ConsoleUtils.error(StringUtils.rightPad("-", 90, "-"))
        ConsoleUtils.error(StringUtils.rightPad("Worksheet", 35, " ") + "| TMS Reference ")
        ConsoleUtils.error(StringUtils.rightPad("-", 90, "-"))
        updatedTestIds.forEach { (sheetName: String?, testId: String?) ->
            ConsoleUtils.error(
                StringUtils.rightPad(
                    sheetName,
                    35,
                    " "
                ) + "| " +
                        StringUtils.replace(
                            testId!!.trim { it <= ' ' },
                            NexialConst.NL,
                            "\\n"
                        )
            )
            ConsoleUtils.error(StringUtils.rightPad("-", 90, "-"))
        }
        ConsoleUtils.error("")
    }
}