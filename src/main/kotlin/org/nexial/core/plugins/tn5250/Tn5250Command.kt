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
package org.nexial.core.plugins.tn5250

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.TextUtils.*
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.NAMESPACE
import org.nexial.core.ShutdownAdvisor
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ServiceProfile.Companion.requiresActiveProfile
import org.nexial.core.model.ServiceProfile.Companion.resolve
import org.nexial.core.model.StepResult
import org.nexial.core.model.TestStep
import org.nexial.core.plugins.CanTakeScreenshot
import org.nexial.core.plugins.ForcefulTerminate
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.plugins.tn5250.FullScreenObject.Companion.render
import org.nexial.core.plugins.tn5250.KeyTranslator.KeyMeta
import org.nexial.core.plugins.tn5250.KeyTranslator.translateKeyMnemonics
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.STABILIZE_MAX_WAIT
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.renderNested
import org.nexial.core.plugins.tn5250.ScreenObject.Companion.waitForInitialScreen
import org.nexial.core.plugins.tn5250.Tn5250Helper.KEY_PRESSED
import org.nexial.core.plugins.tn5250.Tn5250Helper.KEY_RELEASED
import org.nexial.core.plugins.tn5250.Tn5250Helper.KEY_TYPED
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.OutputFileUtils
import org.tn5250j.My5250
import org.tn5250j.SessionPanel
import org.tn5250j.framework.tn5250.Screen5250
import org.tn5250j.framework.tn5250.ScreenField
import org.tn5250j.keyboard.KeyMnemonic
import org.tn5250j.keyboard.KeyboardHandler
import org.tn5250j.tools.encoder.EncodeComponent
import org.tn5250j.tools.encoder.EncodeComponent.PNG
import java.awt.event.KeyEvent
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.lang.Thread.sleep
import java.util.*
import java.util.function.Consumer
import javax.swing.KeyStroke
import javax.validation.constraints.NotNull

class Tn5250Command : BaseCommand(), CanTakeScreenshot, ForcefulTerminate {

    /** need to use dot (".") here because Excel is mistaking this (i.e. TN5250) as a cell reference  */
    override fun getTarget() = "tn.5250"

    override fun init(context: ExecutionContext) {
        super.init(context)
        ShutdownAdvisor.addAdvisor(this)
    }

    override fun takeScreenshot(testStep: TestStep): String {
        var filename = generateScreenshotFilename(testStep)
        if (StringUtils.isBlank(filename)) {
            error("Unable to generate screen capture filename!")
            return ""
        }

        filename = context.project.screenCaptureDir + separator + filename
        return try {
            val sessionPanel = resolveActiveSessionPanel()
            if (sessionPanel == null) {
                error("Unable to generate screen capture due to missing TN5250 session")
                return ""
            }

            val screenshotFile = File(filename)
            EncodeComponent.encode(PNG, sessionPanel, screenshotFile)
            postScreenshot(testStep, screenshotFile)
        } catch (e: Exception) {
            error("Unable to generate screen capture: " + ExceptionUtils.getRootCauseMessage(e))
            ""
        }
    }

    override fun readyToTakeScreenshot() = resolveActiveSessionPanel() != null

    override fun generateScreenshotFilename(testStep: TestStep): String =
        OutputFileUtils.generateScreenCaptureFilename(testStep)

    override fun mustForcefullyTerminate(): Boolean {
        for ((_, value) in context.getObjectByPrefix("$NAMESPACE$target.")) if (value is My5250) return true
        return false
    }

    override fun forcefulTerminate() {
        context.getObjectByPrefix("$NAMESPACE$target.").forEach { (key, obj) -> if (obj is My5250) close(key) }
    }

    fun open(profile: String): StepResult {
        requiresNotBlank(profile, "invalid profile", profile)
        currentProfile = profile
        ensureActiveSession()
        val screenConfig = ScreenConfig.newInstance(profile, context)
        if (!waitForInitialScreen(resolveActiveScreen(), screenConfig))
            return StepResult.fail("Unable to stabilize current TN5250 session; timed out")

        saveSessionFields()
        return StepResult.success("TN5250 session '%s' started", profile)
    }

    fun close(profile: String): StepResult {
        requiresNotBlank(profile, "invalid profile", profile)
        val m = context.getObjectData(resolveProfileKey(profile), My5250::class.java)
        if (m != null) {
            try {
                val sessionPanel = resolveActiveSessionPanel()
                sessionPanel?.confirmCloseSession(true) ?: ConsoleUtils.log("No TN5250 session found for '$profile'")
            } catch (e: IllegalArgumentException) {
                // probably we've lost the session... moving on
                ConsoleUtils.log("Target TN5250 session likely closed.")
            }
        }
        removeCurrentProfile()
        return StepResult.success("TN5250 session '%s' closed", profile)
    }

    /**
     * scan and decipher the entire screen. This would include title, screen text, message, fields, tables, oia.
     *
     * The screen object is stored to a [profile].screen data variable, effectively overwriting any previous version
     * of the same.
     */
    fun inspectScreen(): StepResult {
        val profile = currentProfile
        val screenObject = saveSessionFields()
        return StepResult.success("Inspected TN5250 session '$profile'; ${screenObject.inputFieldCount} input fields")
    }

    /**
     * scan for the first nested screen by examine the screen attribute for UPPER_LEFT, UPPER_RIGHT, BOTTOM_LEFT,
     * BOTTOM_RIGHT to form a rectangle of screen text. Captured screen text will be deciphered for screen text, fields
     * and table information.
     *
     * The captured screen object will be stored to a [profile].nested data variable, effectively overwriting any
     * previous version of the same.
     */
    fun inspectNestedScreen(titles: String): StepResult {
        val profile = currentProfile
        val screenConfig = ScreenConfig.newInstance(profile, context)
        val screen = resolveActiveScreen()

        var screenObject = renderNested(screen, screenConfig)
        if (screenObject == null) {
            val startTime = System.currentTimeMillis()
            do screenObject = renderNested(screen, screenConfig)
            while ((System.currentTimeMillis() - startTime) < STABILIZE_MAX_WAIT)
        }

        if (screenObject == null) return StepResult.fail("Unable to detect nested window in current TN5250 session")

        ScreenLogger(context, currentProfile).log(screenObject)
        context.setData(resolveScreenKey(profile), screenObject)
        return StepResult.success(
            "Inspected the first nested window in TN5250 session '%s'; %s input fields found",
            profile, screenObject.inputFieldCount
        )
    }

    fun typeKeys(keystrokes: String): StepResult {
        requiresNotEmpty(keystrokes, "Invalid keystrokes", keystrokes)
        val profile = currentProfile
        ensureActiveSession()
        // handleKeystrokes(resolveActiveSessionPanel()!!, keystrokes)
        val screen = resolveActiveScreen()
        screen.sendKeys(mapToKeyMnemonics(keystrokes))
        screen.updateScreen()

        return StepResult.success("TN5250 session '%s' processed key event", profile)
    }

    fun type(label: String, text: String): StepResult {
        requiresNotBlank(label, "Invalid field label", label)
        requiresNotEmpty(text, "Invalid text", text)

        val screen = resolveActiveScreen()
        screen.gotoField(findFieldByLabel(label))
        screen.sendKeys(mapToKeyMnemonics(text))
        screen.updateScreen()

        // cheap way to hide password... might need something "stronger"
        return StepResult.success(
            "text '${
                if (StringUtils.containsIgnoreCase(label, "Password")) "....." else text
            }' entered to field '$label'"
        )
    }

    fun clearField(label: String): StepResult {
        requiresNotBlank(label, "Invalid field label", label)

        val field = findFieldByLabel(label)
        val screen = resolveActiveScreen()
        screen.gotoField(field)
        screen.sendKeys(StringUtils.repeat("[delete]", field.length))
        screen.updateScreen()
        return StepResult.success("field '$label' cleared")
    }

    fun saveDisplayFields(`var`: String): StepResult {
        requiresValidVariableName(`var`)
        context.setData(`var`, toString(resolveScreenObject().contentFields(), "\n"))
        return StepResult.success(
            "Display field names of current TN5250 session '%s' saved to \${${`var`}}",
            currentProfile, `var`
        )
    }

    fun saveInputFields(`var`: String): StepResult {
        requiresValidVariableName(`var`)
        context.setData(`var`, toString(resolveScreenObject().inputFields(), "\n"))
        return StepResult.success(
            "Input field names of current TN5250 session '%s' saved to \${${`var`}}",
            currentProfile, `var`
        )
    }

    /**
     * save the value of the specified `field` to data variable denoted as `var`
     */
    fun saveDisplay(`var`: String, field: String): StepResult {
        requiresValidVariableName(`var`)
        requiresNotBlank(field, "Invalid field name", field)

        val profile = currentProfile
        val value = resolveScreenObject().fieldValue(field)
        return if (value == null) {
            context.removeData(`var`)
            StepResult.fail("Field '$field' doesn't exist in current TN5250 session '$profile'")
        } else {
            context.setData(`var`, value)
            StepResult.success("Field value of '$field' in the current TN5250 session '$profile' saved to \${${`var`}}")
        }
    }

    /**
     * check if current TN5250 is locked such that no more keystrokes can be sent to host. This happens when incorrect
     * input or keystroke was entered - either in the wrong location or with the wrong content.
     *
     * We can use this to assert the expected behavior during automation. Possibly useful for negative tests
     */
    fun assertKeyboardLocked() = assertKeyboardLocked(true)

    /**
     * assert that current TN5250 is NOT locked such that keystrokes can be sent to host.
     *
     * We can use this to assert the expected behavior during automation. Possibly useful for negative tests.
     */
    fun assertKeyboardNotLocked() = assertKeyboardLocked(false)

    fun unlockKeyboard(): StepResult {
        val screen = resolveActiveScreen()
        screen.sendKeys(mapToKeyMnemonics("{RESET}"))
        screen.updateScreen()
        return StepResult.success("RESET key sent to host for TN5250 session '$currentProfile'")
    }

    /**
     * assert the current screen title is the same as `expects`.
     */
    fun assertTitle(expects: String?) =
        compare(StringUtils.trim(StringUtils.remove(expects, '\r')), resolveScreenObject().title(), true, "Title")

    /** assert the current screen title (could be multi-line) contains `expects`.  */
    fun assertTitleContain(expects: String?): StepResult =
        assertContains(resolveScreenObject().title(), StringUtils.trim(expects))

    fun focus(label: String): StepResult {
        requiresNotBlank(label, "Invalid field label", label)

        val screen = resolveActiveScreen()
        screen.gotoField(findFieldByLabel(label))
        screen.updateScreen()

        return StepResult.success("Focused on field '$label'")
    }

    fun saveScreenText(`var`: String?): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)
        context.setData(`var`, resolveActiveScreen().stringText)
        return StepResult.success()
    }

    /**
     * assert that current TN5250 session contains `text`.
     * support straight-up text or regex with REGEX: prefix
     */
    fun assertScreenMatch(text: String?) = checkScreenText(text, true)

    /**
     * assert that current TN5250 session DOES NOT contain `text`.
     * support straight-up text or regex with REGEX: prefix
     */
    fun assertScreenNotMatch(text: String?) = checkScreenText(text, false)

    fun assertScreenContain(list: String?, ordered: String?): StepResult {
        requiresNotEmpty(list, "Invalid list", list)
        requiresNotBlank(ordered, "Invalid ordered value", ordered)
        return checkScreenTextContain(
            toList(list, context.textDelim, true),
            BooleanUtils.toBoolean(ordered),
            true
        )
    }

    fun assertScreenNotContain(list: String?, ordered: String?): StepResult {
        requiresNotEmpty(list, "Invalid list", list)
        requiresNotBlank(ordered, "Invalid ordered value", ordered)
        return checkScreenTextContain(
            toList(list, context.textDelim, true),
            BooleanUtils.toBoolean(ordered),
            false
        )
    }

    fun assertTablePresent(): StepResult {
        val table = resolveScreenTable()
        val tableExist = table != null && table.rowCount() > 0
        return StepResult(tableExist, "Current TN5250 table${if (tableExist) " exists" else " DOES NOT exist"}", null)
    }

    /**
     * {@param maxPage} represents the number of pages Nexial should scan for a given table; default to 1
     * {@param csv} represents the target CSV file to save the table data
     */
    fun saveTableAsCSV(csv: String, maxPage: String): StepResult {
        requiresNotBlank(csv, "Invalid csv target", csv)
        val output = File(csv)
        val dir = output.parentFile ?: return StepResult.fail("Invalid path for csv: $csv")

        val maxPages = if (StringUtils.isBlank(maxPage)) 1 else NumberUtils.toInt(maxPage, 1)

        val screen = resolveActiveScreen()
        val table = resolveScreenTable() ?: return StepResult.fail("No table found in current TN5250 screen")

        val currentScreen = resolveScreenObject()
        if (currentScreen !is FullScreenObject) screen.sendKeys(translateKeyMnemonics("{TAB}"))

        val csvContent = table.toCSV(screen, ",", "\n", maxPages)

        if (currentScreen is FullScreenObject)
            inspectScreen()
        else
            inspectNestedScreen("${CollectionUtils.size(currentScreen.titleLines())}")

        dir.mkdirs()

        return try {
            FileUtils.writeStringToFile(output, csvContent, DEF_FILE_ENCODING)
            StepResult.success("Table content in current TN5250 screen written to '$csv'")
        } catch (e: IOException) {
            StepResult.success("Unable to write to '" + csv + "': " + e.message)
        }
    }

    /**
     * save first matched row to `var` based on `criteria` (name/value pairs)
     * @param `var` to store matched rows as List<Map<String,String>>
     * @param criteria one or more name/value pairs to filter against current table
     */
    fun saveTableRow(`var`: String, criteria: String?): StepResult {
        requiresValidAndNotReadOnlyVariableName(`var`)

        context.removeData(`var`)
        val table = resolveScreenTable() ?: return StepResult.fail("No table found in current TN5250 screen")
        val rows = table.first(if (StringUtils.isBlank(criteria)) null else toMap(criteria!!, "\n", "="))
        return if (MapUtils.isEmpty(rows))
            StepResult.fail("Unable to retrieve matching rows from current table")
        else {
            context.setData(`var`, rows)
            StepResult.success("Matched found; ${rows.size} column(s) saved as '${`var`}'")
        }
    }

    fun assertTableMatch(column: String, text: String) = checkTableContains(column, text, true)

    fun assertTableNotMatch(column: String, text: String) = checkTableContains(column, text, false)

    fun saveTableMatchCount(`var`: String, column: String, text: String): StepResult {
        requiresNotBlank(column, "Invalid column", column)
        val table = resolveScreenTable() ?: return StepResult.fail("No table found in current TN5250 screen")

        context.setData(`var`, table.rowCount(column, text))
        return StepResult.success("match count of '$text' on column '$column' saved to '${`var`}'")
    }

    fun typeOnMatchedRow(column: String, match: String, keystrokes: String): StepResult {
        requiresNotBlank(column, "Invalid column", column)
        requiresNotBlank(match, "Invalid match criteria", match)
        requiresNotBlank(keystrokes, "Invalid keystrokes", keystrokes)

        val table = resolveScreenTable() ?: return StepResult.fail("No table found in current TN5250 screen")
        val row = table.findRow(column, match)
        return typeOnRow(table, row, keystrokes)
    }

    fun typeOnMatchedColumns(matches: String, keystrokes: String): StepResult {
        requiresNotBlank(matches, "Invalid match criterion", matches)
        requiresNotBlank(keystrokes, "Invalid keystrokes", keystrokes)

        val table = resolveScreenTable() ?: return StepResult.fail("No table found in current TN5250 screen")
        val headers = table.headers
        val criterion = toMap(matches, "\n", "=")
        val row: Int = table.data.indexOfFirst { row ->
            criterion.map { (field, criteria) -> polyMatch(row[headers.indexOf(field)], criteria) }.all { it }
        }

        return typeOnRow(table, row, keystrokes)
    }

    private fun typeOnRow(table: ScreenTable, row: Int, keystrokes: String) = if (row != -1) {
        ConsoleUtils.log("performing keystroke automation on Row $row of current TN5250 table")
        table.option(
            resolveActiveScreen(), row, *toList(keystrokes, "\n", false)
            .map { mapToKeyMnemonics(it) }.toTypedArray()
        )
        StepResult.success("keystrokes entered to row ${row + 1}")
    } else
        StepResult.fail("Unable to find any matching row in current TN5250 table")

    fun assertColumnPresent(column: String) = assertColumnPresent(column, true)

    fun assertColumnNotPresent(column: String) = assertColumnPresent(column, false)

    fun assertFieldPresent(label: String): StepResult =
        if (!isFieldExists(label))
            StepResult.fail("Screen field '$label' DOES NOT exist")
        else
            StepResult.success("Screen field '$label' found")

    fun assertFieldNotPresent(label: String): StepResult =
        if (isFieldExists(label))
            StepResult.fail("Unexpected screen field '$label' exists")
        else
            StepResult.success("Screen field '$label' does not exist, as expected")

    fun assertFieldMatch(label: String, expects: String?): StepResult =
        if (!isFieldExists(label))
            StepResult.fail("Screen field '$label' DOES NOT exist")
        else
            compare(expects, resolveScreenObject().fieldValue(label), true, "Screen field '$label'")

    fun assertFieldNotMatch(label: String, expects: String?): StepResult =
        if (!isFieldExists(label))
            StepResult.fail("Screen field '$label' DOES NOT exist")
        else
            compare(expects, resolveScreenObject().fieldValue(label), false, "Screen field '$label'")

    fun saveMessage(`var`: String): StepResult {
        requiresValidVariableName(`var`)
        val msg = screenMessage
        if (StringUtils.isEmpty(msg)) {
            context.removeData(`var`)
        } else {
            context.setData(`var`, msg)
        }
        return StepResult.success("message of current TN5250 screen saved to data variable '$`var`'")
    }

    fun assertMessageMatch(expects: String?) = compare(expects, screenMessage, true, "Screen message")

    fun assertMessageNotMatch(expects: String?) = compare(expects, screenMessage, false, "Screen message")

    fun waitUntilTextPresent(text: String?, maxWaitMs: String?) =
        waitUntilConditionMet("text", text, maxWaitMs) { screen -> screen.text }

    fun waitUntilTitlePresent(title: String?, maxWaitMs: String?) =
        waitUntilConditionMet("title", title, maxWaitMs) { screen -> screen.title() }

    fun waitUntilMessagePresent(message: String?, maxWaitMs: String?) =
        waitUntilConditionMet("message", message, maxWaitMs) { screen -> screen.message }

    fun waitUntilProcessed(maxWaitMs: String?) = waitUntilConditionMet("status", "PROCESSED", maxWaitMs) {
        val oia = resolveActiveScreen().oia
        if (oia.inputInhibited == 0 && !oia.isKeyBoardLocked)
            "PROCESSED"
        else
            StringUtils.defaultIfEmpty(oia.inhibitedText, "")
    }

    private fun waitUntilConditionMet(name: String,
                                      matchBy: String?,
                                      maxWaitMs: String?,
                                      inspect: (FullScreenObject) -> String): StepResult {
        requiresNotBlank(matchBy, "Invalid $name", matchBy)
        requiresInteger(maxWaitMs, "Invalid wait value", maxWaitMs)

        val timesUp = System.currentTimeMillis() + maxWaitMs!!.toLong()
        do {
            if (polyMatch(inspect(saveSessionFields()), matchBy, true))
                return StepResult.success("Expected $matchBy met with screen $name within allocated time $maxWaitMs")
            else
                sleep(200)
        } while (System.currentTimeMillis() < timesUp)

        return StepResult.fail("Expected $matchBy with screen $name NOT met within allocated time $maxWaitMs")
    }

    protected val screenMessage: @NotNull String
        get() = resolveFullScreenObject().message

    protected fun isFieldExists(label: String): Boolean {
        requiresNotBlank(label, "Invalid field label", label)
        return resolveScreenObject().fieldExists(label)
    }

    internal fun compare(expects: String?, actual: String?, expectMatch: Boolean, messagePrefix: String): StepResult {
        val matched = polyMatch(actual, expects, true)

        if (StringUtils.startsWith(expects, REGEX)) {
            val regex = StringUtils.substringAfter(expects, REGEX)
            return StepResult(
                matched == expectMatch,
                "$messagePrefix ${if (matched) "matches" else "DOES NOT match"} the regular expression '$regex'",
                null
            )
        }

        if (StringUtils.startsWith(expects, CONTAIN)) {
            val search = StringUtils.substringAfter(expects, CONTAIN)
            return StepResult(
                matched == expectMatch,
                "$messagePrefix ${if (matched) "contains" else "DOES NOT contain"} '$search'",
                null
            )
        }

        if (StringUtils.startsWith(expects, CONTAIN_ANY_CASE)) {
            val search = StringUtils.substringAfter(expects, CONTAIN_ANY_CASE)
            return StepResult(
                matched == expectMatch,
                "$messagePrefix ${if (matched) "contains" else "DOES NOT contain"} '$search'",
                null
            )
        }

        if (StringUtils.startsWith(expects, START)) {
            val search = StringUtils.substringAfter(expects,
                                                    START)
            return StepResult(
                matched == expectMatch,
                "$messagePrefix ${if (matched) "starts with" else "DOES NOT start with"} '$search'",
                null
            )
        }

        if (StringUtils.startsWith(expects, START_ANY_CASE)) {
            val search = StringUtils.substringAfter(expects,
                                                    START_ANY_CASE)
            return StepResult(
                matched == expectMatch,
                "$messagePrefix ${if (matched) "starts with" else "DOES NOT start with"} '$search'",
                null
            )
        }

        if (StringUtils.startsWith(expects, END)) {
            val search = StringUtils.substringAfter(expects, END)
            return StepResult(
                matched == expectMatch,
                "$messagePrefix ${if (matched) "ends with" else "DOES NOT end with"} '$search'",
                null
            )
        }

        if (StringUtils.startsWith(expects, END_ANY_CASE)) {
            val search = StringUtils.substringAfter(expects,
                                                    END_ANY_CASE)
            return StepResult(
                matched == expectMatch,
                "$messagePrefix ${if (matched) "ends with" else "DOES NOT end with"} '$search'",
                null
            )
        }

        return StepResult(matched == expectMatch,
                          "$messagePrefix ${if (matched) "equals" else "DOES NOT equal"} to '$expects'",
                          null)
    }

    private fun checkTableContains(column: String, text: String, expectMatch: Boolean): StepResult {
        requiresNotBlank(column, "Invalid column", column)
        val table = resolveScreenTable() ?: return StepResult.fail("No table found in current TN5250 screen")

        val prefix = if (column == "*") "Table" else "column '$column'"
        val equal = table.match(column, text)
        return StepResult(equal == expectMatch, "$prefix ${if (equal) "has" else "DOES NOT have"} $text", null)
    }

    protected fun assertColumnPresent(column: String, expectsPresent: Boolean): StepResult {
        requiresNotBlank(column, "Invalid column", column)
        val table = resolveScreenTable() ?: return StepResult.fail("No table found in current TN5250 screen")
        val found = table.headers.contains(column)
        return StepResult(
            found == expectsPresent,
            "Table of current screen ${if (found) "contains" else "DOES NOT contain"} column '$column'",
            null
        )
    }

    protected fun checkScreenText(expected: String?, expectMatch: Boolean): StepResult {
        requiresNotBlank(expected, "Invalid text to assert", expected)
        return compare(expected, resolveScreenObject().text, expectMatch, "Text in current TN5250 screen")
    }

    protected fun checkScreenTextContain(phrases: List<String?>, ordered: Boolean, expectsMatch: Boolean): StepResult {
        requiresNotEmpty(phrases, "invalid list of text to assert", phrases)
        val screen = resolveActiveScreen()
        val screenText = screen.stringText
        val phraseText = toString(phrases, ",")
        val failure = "${if (expectsMatch) "EXPECTED" else "UNEXPECTED"} " +
                      "${if (ordered) "ordered" else ""} text [$phraseText] " +
                      "${if (expectsMatch) "NOT FOUND" else "FOUND"} in current TN5250 screen"
        val success = "${if (expectsMatch) "EXPECTED" else "UNEXPECTED"} " +
                      "${if (ordered) "ordered" else ""} text [$phraseText] " +
                      "${if (expectsMatch) "FOUND" else "NOT FOUND"} in current TN5250 screen"

        if (ordered) {
            var lastPos = 0
            for (text in phrases) {
                lastPos = StringUtils.indexOf(screenText, text, lastPos)
                if (lastPos == -1) {
                    if (expectsMatch) return StepResult.fail(failure)
                } else {
                    if (!expectsMatch) return StepResult.fail(failure)
                }
            }
            return StepResult.success(success)
        }

        for (text in phrases) {
            if (StringUtils.contains(screenText, text)) {
                if (!expectsMatch) return StepResult.fail(failure)
            } else {
                if (expectsMatch) return StepResult.fail(failure)
            }
        }
        return StepResult.success(success)
    }

    protected fun resolveActiveSessionPanel() =
        context.getObjectData(resolveSessionKey(currentProfile), SessionPanel::class.java)

    protected fun assertKeyboardLocked(expectLocked: Boolean): @NotNull StepResult {
        val keyBoardLocked = resolveActiveScreen().oia.isKeyBoardLocked
        return StepResult(
            keyBoardLocked == expectLocked,
            "Keyboard is${if (keyBoardLocked) "" else " NOT"} currently locked for TN5250 session '$currentProfile'",
            null
        )
    }

    protected fun resolveActiveScreen(): Screen5250 {
        val profile = currentProfile
        requiresNotBlank(profile, "Unable to reference current profile for TN5250 session")
        requiresActiveProfile(context, profile, target, My5250::class.java)
        val sessionPanel = resolveActiveSessionPanel()
                           ?: throw IllegalArgumentException("Unable to reference TN5250 session")
        return sessionPanel.screen ?: throw IllegalArgumentException("Unable to reference TN5250 screen")
    }

    private fun mapToKeyMnemonics(input: String) =
        if (StringUtils.isBlank(input)) input else translateKeyMnemonics(input)

    private fun saveSessionFields(): FullScreenObject {
        val profile = currentProfile
        val screenConfig = ScreenConfig.newInstance(profile, context)
        val screenObject = render(resolveActiveScreen(), screenConfig)
        context.setData(resolveScreenKey(profile), screenObject)
        ScreenLogger(context, currentProfile).log(screenObject)
        return screenObject
    }

    private fun findFieldByLabel(label: String): ScreenField {
        return resolveScreenObject().field(label)
               ?: throw AssertionError("Current TN5250 session does not contain any field labeled as '$label'")
    }

    private fun resolveScreenObject(): ScreenObject {
        val profile = currentProfile
        ensureActiveSession()
        return context.getObjectData(resolveScreenKey(profile), ScreenObject::class.java)
               ?: throw AssertionError("Unable to resolve to current screen")
    }

    private fun resolveFullScreenObject(): FullScreenObject {
        val profile = currentProfile
        ensureActiveSession()
        return context.getObjectData(resolveScreenKey(profile), FullScreenObject::class.java)
               ?: throw AssertionError("Unable to resolve to current screen")
    }

    private fun resolveScreenTable() = resolveScreenObject().table

    private fun handleKeystrokes(sessionPanel: SessionPanel, keys: String) {
        val screen = sessionPanel.screen
        screen.sendKeys(mapToKeyMnemonics(keys))
        screen.updateScreen()
    }

    private fun pushModifier(keyHandler: KeyboardHandler, sessionPanel: SessionPanel?, modifier: KeyMeta?) {
        handleKeyMeta(keyHandler, sessionPanel, KEY_PRESSED, modifier)
    }

    private fun pushSpecialKey(keyHandler: KeyboardHandler, sessionPanel: SessionPanel?, key: KeyMeta?) {
        handleKeyMeta(keyHandler, sessionPanel, KEY_PRESSED, key)
        handleKeyMeta(keyHandler, sessionPanel, KEY_RELEASED, key)
    }

    private fun pushSystemKey(keyHandler: KeyboardHandler, sessionPanel: SessionPanel, key: KeyMnemonic?) {
        sessionPanel.session.screen.sendKeys(key)
    }

    private fun releaseModifiers(keyHandler: KeyboardHandler, sessionPanel: SessionPanel?, modifiers: Stack<KeyMeta>) {
        modifiers.forEach(Consumer { m: KeyMeta? -> handleKeyMeta(keyHandler, sessionPanel, KEY_RELEASED, m) })
    }

    private fun handleKeyMeta(keyHandler: KeyboardHandler, sessionPanel: SessionPanel?, action: Int, key: KeyMeta?) {
        keyHandler.processKeyEvent(KeyEvent(sessionPanel,
                                            action,
                                            System.currentTimeMillis(),
                                            key!!.modifier,
                                            key.keyCode,
                                            key.keyChar,
                                            key.keyLocation))
    }

    private fun typeKeystrokes(keyHandler: KeyboardHandler, sessionPanel: SessionPanel?, keystrokes: String) {
        keystrokes.chars().forEach { c: Int ->
            val ch = c.toChar()
            val keyCode = KeyStroke.getKeyStroke(ch).keyCode
            val now = System.currentTimeMillis()
            keyHandler.processKeyEvent(KeyEvent(sessionPanel, KEY_PRESSED, now, 0, keyCode, ch))
            keyHandler.processKeyEvent(KeyEvent(sessionPanel, KEY_TYPED, now, 0, keyCode, ch))
            keyHandler.processKeyEvent(KeyEvent(sessionPanel, KEY_RELEASED, now, 0, keyCode, ch))
        }
    }

    private var currentProfile: String
        get() {
            require(hasCurrentProfile()) { "No current TN5250 profile" }
            return context.getStringData(resolveCurrentProfileKey())
        }
        private set(profile) {
            ConsoleUtils.log("switching current TN5250 profile to '$profile'")
            if (context.getStringData(resolveCurrentProfileKey()) != profile)
                context.setData(resolveCurrentProfileKey(), profile)
        }

    private fun hasCurrentProfile() = context.hasData(resolveCurrentProfileKey())

    private fun removeCurrentProfile() {
        try {
            val profile = currentProfile
            context.removeData(resolveProfileKey(profile))
            context.removeData("nexial.profile.$profile")
            context.removeData(resolveSessionKey(profile))
            context.removeData(resolveScreenKey(profile))
            context.removeData(resolveTitleLineConfig(profile))
        } catch (e: IllegalArgumentException) {
            ConsoleUtils.log("Unable to resolve current TN5250 profile; likely already removed")
        } finally {
            context.removeData(resolveCurrentProfileKey())
        }
    }

    private fun ensureActiveSession() {
        val profile = currentProfile
        val terminalKey = resolveProfileKey(profile)
        var m = context.getObjectData(terminalKey, My5250::class.java)
        if (m == null) {
            val profiler = resolve(context, profile, Tn5250ServiceProfile::class.java)
            m = My5250()
            m.start(profiler.name)
            context.setData(terminalKey, m)
            context.setData(resolveSessionKey(profile), m.sessionPanel)
            context.setData(resolveTitleLineConfig(profile), profiler.titleLines)
        }
        m.frame.isVisible = true
    }

    private fun resolveCurrentProfileKey() = "$NAMESPACE$target.CURRENT_PROFILE"

    private fun resolveProfileKey(profile: String) = "$NAMESPACE$target.$profile"

    private fun resolveSessionKey(profile: String) = "$profile.session"

    private fun resolveScreenKey(profile: String) = "$profile.screen"

    companion object {
        fun resolveTitleLineConfig(profile: String) = "$profile.titleLines"
    }
}