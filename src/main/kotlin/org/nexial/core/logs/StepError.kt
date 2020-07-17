/*
 * Copyright 2012-2018 the original author or authors.
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

package org.nexial.core.logs

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.NL
import org.nexial.core.utils.ConsoleUtils
import java.io.File

data class StepError(val dateTime: String,
                     val script: String,
                     val scenario: String,
                     val activity: String,
                     val step: String,
                     val commandType: String,
                     val commandName: String,
                     val screenshot: String,
                     val detailLog: String,
                     var message: String) {

    fun appendMessage(message: String) {
        this.message += (if (message.isNotBlank()) NL else "") + message
    }

    override fun toString(): String {
        return "StepError(dateTime='$dateTime'," +
               "\tscript='$script'," +
               "\tscenario='$scenario'," +
               "\tactivity='$activity'," +
               "\tstep='$step'," +
               "\tcommandType='$commandType'," +
               "\tcommandName='$commandName'," +
               "\tscreenshot='$screenshot'," +
               "\tdetailLog='$detailLog'," +
               "\tmessage='${message.replace("\n", "(eol)")}')"
    }
}

data class StepErrors(val logFile: File) {
    val errors = mutableListOf<StepError>()
    private val regexLogFormat = "^" +
                                 "\\[(.+?)\\]" +  // log date/time
                                 "\\[(.+?)\\]" +  // script
                                 "\\[(.+?)\\]" +  // scenario
                                 "\\[(.+?)\\]" +  // activity
                                 "\\[(.+?)\\]" +  // step
                                 "\\[(.+?)\\]" +  // command type
                                 "\\[(.+?)\\]" +  // command
                                 "\\[(.+?)\\]" +  // error screenshot
                                 "\\[(.+?)\\]" +  // detailed log
                                 "\\:?\\s*(.*)" + // log message
                                 "$"
    private val regexRuntimeFilePattern = "\\.\\d{8}_\\d{6}\\.[0-9~]+(\\.xlsx|\\.XLSX)$"
    val htmlTableClass = "execution-errors"
    val htmlTableColumns = listOf("date/time", "script", "scenario", "activity", "step", "command",
                                  "screenshot", "details", "message")

    init {
        if (FileUtil.isFileReadable(logFile, 32)) parse(FileUtils.readLines(logFile, DEF_FILE_ENCODING))
    }

    private fun parse(logs: List<String>) {
        var lastError: StepError? = null

        for (line in logs) {
            if (StringUtils.isBlank(line)) continue

            val logParts = RegexUtils.collectGroups(line, regexLogFormat)
            if (CollectionUtils.size(logParts) != 10) {
                // not fitting to the expected log format: we'd assume this line is the bleed-over from previous log
                if (lastError != null) {
                    lastError.appendMessage(line)
                } else {
                    // hmm... this means we have a dangling message as first line?!
                    ConsoleUtils.error("Unable to parse/handle error log: $line")
                }
            } else {
                lastError = StepError(dateTime = logParts[0],
                                      script = extractScriptName(logParts[1]),
                                      scenario = logParts[2],
                                      activity = logParts[3],
                                      step = logParts[4],
                                      commandType = logParts[5],
                                      commandName = logParts[6],
                                      screenshot = StringUtils.trim(logParts[7]),
                                      detailLog = StringUtils.trim(logParts[8]),
                                      message = StringUtils.removeStart(logParts[9], ": "))
                errors.add(lastError)
            }
        }
    }

    fun generateHTML(header: String, footer: String): String {
        val html = StringBuilder(header)
        html.append("<table width=\"100%\" class=\"$htmlTableClass\" cellspacing=\"0\">")
        html.append("<thead><tr>${htmlTableColumns.joinToString("") { "<th nowrap>$it</th>" }}</tr></thead>")
        html.append("<tbody>")
        html.append(errors.joinToString("") {
            "<tr>" +
            "<td nowrap>${it.dateTime}</td>" +
            "<td nowrap>${it.script}</td>" +
            "<td nowrap>${it.scenario}</td>" +
            "<td nowrap>${it.activity}</td>" +
            "<td nowrap>${it.step}</td>" +
            "<td nowrap>${it.commandType} &raquo; ${it.commandName}</td>" +
            "<td>${toHtmlAnchor("screenshot", it.screenshot)}</td>" +
            "<td>${toHtmlAnchor("details", it.detailLog)}</td>" +
            "<td><pre>${it.message}</pre></td>" +
            "</tr>"
        })
        html.append("</tbody></table>")
        html.append(footer)
        return html.toString()
    }

    private fun extractScriptName(scriptFileName: String) =
            StringUtils.trim(RegexUtils.replace(scriptFileName, regexRuntimeFilePattern, ""))

    private fun toHtmlAnchor(label: String, link: String) =
            if (link.isBlank() || link == "-") "&nbsp;" else "<a href=\"$link\">$label</a>"
}