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

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.Project.*
import org.nexial.core.utils.InputFileUtils
import java.io.File
import java.io.File.separator
import java.util.stream.Stream

/**
 * utilities common to the commandline tools, esp. those dealing with project artifacts
 */
object ProjectToolUtils {

    private const val column1Width = 50
    private const val column2Width = 20
    private const val column3Width = 11
    const val column4LeftMargin = column1Width + column2Width + column3Width
    const val column3LeftMargin = column1Width + column2Width

    @JvmField
    val beforeAfterArrow = "\n" + StringUtils.repeat(" ", column1Width + column2Width + column3Width - 3) + "=> "

    @JvmStatic
    fun isValidTestArtifact(file: File) = !file.name.startsWith("~") &&
                                          !file.absolutePath.contains("$separator$DEF_REL_LOC_OUTPUT")

    @JvmStatic
    fun isTestScriptFile(file: File) = isValidTestArtifact(file) && !file.name.endsWith(DEF_DATAFILE_SUFFIX)

    @JvmStatic
    fun isTestScript(file: File) = isValidTestArtifact(file) && InputFileUtils.isValidScript(file.absolutePath)

    @JvmStatic
    fun isMacroFile(file: File) = isTestScriptFile(file)

    @JvmStatic
    fun isMacro(file: File) = isTestScriptFile(file) && InputFileUtils.isValidMacro(file.absolutePath)

    @JvmStatic
    fun isDataFile(file: File) = isValidTestArtifact(file) && file.name.endsWith(DEF_DATAFILE_SUFFIX)

    @JvmStatic
    fun isData(file: File) = isDataFile(file) && InputFileUtils.isValidDataFile(file.absolutePath)

    @JvmStatic
    fun isPlan(file: File) = isTestScriptFile(file) && InputFileUtils.isValidPlanFile(file.absolutePath)

    @JvmStatic
    fun listMacroFiles(dir: File): Stream<File> = FileUtils.listFiles(dir, arrayOf(SCRIPT_FILE_SUFFIX), true)
        .stream().filter {
            isTestScriptFile(it) && InputFileUtils.isValidMacro(it.absolutePath)
        }

    @JvmStatic
    fun listTestScripts(dir: File): Stream<File> = FileUtils.listFiles(dir, arrayOf(SCRIPT_FILE_SUFFIX), true)
        .stream().filter {
            isTestScriptFile(it) && InputFileUtils.isValidScript(it.absolutePath)
        }

    @JvmStatic
    fun listDataFiles(dir: File): Collection<File> = FileUtils.listFiles(dir, arrayOf(DATA_FILE_SUFFIX), true)

    @JvmStatic
    fun log(action: String, subject: Any) = log(StringUtils.rightPad(action, 26) + " " + subject)

    @JvmStatic
    fun log(message: String) = println(" >> $message")

    @JvmStatic
    fun formatColumns(file: String, worksheet: String?, position: String, updatingVars: String) =
            "${StringUtils.abbreviate(StringUtils.rightPad(file, column1Width - 1),
                                      column1Width + 1,
                                      column1Width - 1)} " +
            "${StringUtils.abbreviate(StringUtils.rightPad(worksheet ?: "", column2Width - 1), column2Width - 1)} " +
            "[${StringUtils.rightPad(position, column3Width - 3)}] " +
            updatingVars

    @JvmStatic
    fun formatColumns(file: String, processTime: String, newFile: String) =
            "${StringUtils.rightPad(abbreviate(file, column1Width), column1Width)} " +
            "${StringUtils.leftPad(processTime, column2Width - 7)} " +
            abbreviate(newFile, column3LeftMargin)

    @JvmStatic
    fun abbreviate(text: String, maxWidth: Int): String {
        val length = text.length
        if (length <= maxWidth) {
            return text
        }
        val startIndex = length - maxWidth + 3
        return "...${StringUtils.substring(text, startIndex, length)}"
    }

    @JvmStatic
    fun reformatLines(before: String, after: String, leftMargin: Int): String {
        if (StringUtils.isBlank(before) && StringUtils.isBlank(after)) return before + beforeAfterArrow + after

        val lines = StringBuilder()

        if (StringUtils.contains(before, "\n")) {
            toLines(before, leftMargin, lines)
            lines.append(beforeAfterArrow).append("\n")
        } else {
            lines.append(before).append(beforeAfterArrow)
        }

        if (StringUtils.contains(after, "\n")) {
            toLines(after, leftMargin, lines)
        } else {
            lines.append(after)
        }

        return lines.toString()
    }

    @JvmStatic
    fun toLines(text: String, leftMargin: Int, newLines: StringBuilder) {
        val margin = StringUtils.repeat(" ", leftMargin)

        TextUtils.toList(StringUtils.remove(text, "\r"), "\n", false)
            .filter { StringUtils.isNotEmpty(it) }
            .forEach {
                if (newLines.isNotEmpty()) newLines.append(margin)
                newLines.append(it).append("\n")
            }

        newLines.deleteCharAt(newLines.length - 1)
    }
}