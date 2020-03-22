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
 *
 */

package org.nexial.core

import org.nexial.core.NexialConst.Data.SCOPE
import org.nexial.core.NexialConst.Exec.*
import org.nexial.core.NexialConst.OPT_MANAGE_MEM
import org.nexial.core.NexialConst.OUTPUT_TO_CLOUD
import org.nexial.core.NexialConst.Web.*

object CommandConst {

    // repair artifact constants
    val DEPRECATED_VARS = listOf("nexial.scope.executionMode", "nexial.safari.cleanSession")

    val UPDATED_VARS = mapOf(
            OPT_DEBUG_HIGHLIGHT_OLD to OPT_DEBUG_HIGHLIGHT,
            HIGHLIGHT_WAIT_MS_OLD to HIGHLIGHT_WAIT_MS,
            ASSISTANT_MODE to OPT_OPEN_RESULT,
            POST_EXEC_MAIL_TO_OLD to POST_EXEC_MAIL_TO)

    private val NON_ITERABLE_VARS = listOf(
            ENABLE_EMAIL,
            POST_EXEC_MAIL_TO,
            POST_EXEC_EMAIL_SUBJECT,
            POST_EXEC_EMAIL_HEADER,
            POST_EXEC_EMAIL_FOOTER,
            OPT_MANAGE_MEM,
            WPS_EXE_LOCATION,
            OUTPUT_TO_CLOUD,
            GENERATE_EXEC_REPORT,
            OPT_OPEN_RESULT,
            OPT_OPEN_EXEC_REPORT)

    @JvmStatic
    fun isNonIterableVariable(name: String) = name.startsWith(SCOPE) || NON_ITERABLE_VARS.contains(name)

    @JvmStatic
    fun getPreferredSystemVariableName(name: String) = UPDATED_VARS.getOrDefault(name, name)!!

    val READ_ONLY_VARS = listOf(
            "nexial.runID", "nexial.iterationEnded", "nexial.scope.currentIteration",
            "nexial.scope.lastIteration", "nexial.scope.isLastIteration",
            "nexial.scope.isFirstIteration", "nexial.scope.currentIterationId",
            "nexial.lastScreenshot", "nexial.lastOutcome", "file.separator",
            "java.home", "java.io.tmpdir", "java.version", "line.separator",
            "os.arch", "os.name", "os.version", "user.country", "user.dir",
            "user.home", "user.language", "user.name", "user.timezone")

    // common commands
    const val CMD_VERBOSE = "base.verbose(text)"
    private const val CMD_RUN_PROGRAM = "external.runProgram(programPathAndParams)"
    private const val CMD_RUN_PROGRAM_NO_WAIT = "external.runProgramNoWait(programPathAndParams)"
    private val MERGE_READY = listOf(CMD_VERBOSE, CMD_RUN_PROGRAM, CMD_RUN_PROGRAM_NO_WAIT)

    @JvmStatic
    fun shouldMergeCommandParams(command: String) = MERGE_READY.contains(command)

    const val CMD_MACRO = "base.macro(file,sheet,name)"
    const val CMD_REPEAT_UNTIL = "base.repeatUntil(steps,maxWaitMs)"
    const val CMD_SECTION = "base.section(steps)"

    // macro command for inspector
    const val MACRO_DESCRIPTION = "macro.description()"
    const val MACRO_EXPECTS = "macro.expects(var,default)"
    const val MACRO_PRODUCES = "macro.produces(var,value)"
    val MACRO_COMMANDS = listOf(MACRO_DESCRIPTION, MACRO_EXPECTS, MACRO_PRODUCES)

    val MULTI_VARS_COMMANDS = mapOf("base.clear(vars)" to 0)

    // test script updater commands
    @JvmStatic
    val nonMacroCommands = listOf("macro(file,sheet,name)")

    @JvmStatic
    val replacedCommands = mapOf(
            "number.assertBetween(num,lower,upper)" to "number.assertBetween(num,min,max)",
            "number.assertBetween(num,min,max)" to "number.assertBetween(num,minNum,maxNum)",
            "number.round(var,closestDigit)" to "number.roundTo(var,closestDigit)",
            "desktop.scanTable(var,name)" to "desktop.useTable(var,name)",
            "desktop.getRowCount(var)" to "desktop.saveRowCount(var)",
            "io.saveMatches(var,path,filePattern)" to "io.saveMatches(var,path,fileFilter,textFilter)",
            "base.assertNotContains(text,substring)" to "base.assertNotContain(text,substring)",
            "web.assertAttributeNotContains(locator,attrName,contains)" to "web.assertAttributeNotContain(locator,attrName,contains)",
            "web.assertAttributeContains(locator,attrName,contains)" to "web.assertAttributeContain(locator,attrName,contains)",
            "web.screenshot(file,locator,ignoreLocators)" to "web.screenshot(file,locator)",
            "web.selectMultiOptions(locator)" to "web.selectAllOptions(locator)"
    )

    private const val SUGGESTION_PREFIX = "This command is deprecated and will soon be removed. Consider using"

    @JvmStatic
    val commandSuggestions = mapOf(
            "desktop.useTable(var,name)" to "$SUGGESTION_PREFIX desktop » editTableCells(row,nameValues) instead",
            "desktop.editCurrentRow(nameValues)" to "$SUGGESTION_PREFIX desktop » editTableCells(row,nameValues) instead",
            "web.scrollLeft(locator,pixel)" to "$SUGGESTION_PREFIX web » scrollElement(locator,xOffset,yOffset) instead",
            "web.scrollRight(locator,pixel)" to "$SUGGESTION_PREFIX web » scrollElement(locator,xOffset,yOffset) instead")

    @JvmField
    val PARAM_AUTO_FILL_COMMANDS = listOf("desktop.sendKeysToTextBox",
                                          "desktop.typeAppendTextArea",
                                          "desktop.typeAppendTextBox",
                                          "desktop.typeTextArea",
                                          "desktop.typeTextBox",
                                          "localdb.exportEXCEL",
                                          "localdb.exportXML",
                                          "io.saveMatches",
                                          "macro.expects",
                                          "webcookie.saveAllAsText")

    @JvmField
    val CRYPT_RESTRICTED_COMMANDS = listOf("base.verbose",
                                           "base.prependText",
                                           "base.appendText",
                                           "base.save")

    // "self-derived" means that the command will figure out the appropriate param values for display
    @JvmField
    val PARAM_DERIVED_COMMANDS = listOf("step.observe")
}
