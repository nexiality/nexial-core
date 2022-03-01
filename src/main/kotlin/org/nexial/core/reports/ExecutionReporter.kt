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

package org.nexial.core.reports

import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Exec.*
import org.nexial.core.NexialConst.Web.WEB_METRICS_GENERATED
import org.nexial.core.NexialConst.Web.WEB_METRICS_HTML
import org.nexial.core.SystemVariables.getDefault
import org.nexial.core.excel.Excel
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.io.File
import java.io.IOException

class ExecutionReporter {
    private var templateEngine: TemplateEngine? = null
    private var executionTemplate: String? = null
    private var reportPath: String? = null
    private var htmlOutputFile: String? = null
    private var detailJsonFile: String? = null
    private var summaryJsonFile: String? = null
    private var junitFile: String? = null

    fun setTemplateEngine(templateEngine: TemplateEngine) {
        this.templateEngine = templateEngine
    }

    fun setExecutionTemplate(executionTemplate: String) {
        this.executionTemplate = executionTemplate
    }

    fun setReportPath(reportPath: String) {
        this.reportPath = reportPath
    }

    fun setHtmlOutputFile(htmlOutputFile: String) {
        this.htmlOutputFile = htmlOutputFile
    }

    fun setDetailJsonFile(detailJsonFile: String) {
        this.detailJsonFile = detailJsonFile
    }

    fun setSummaryJsonFile(summaryJsonFile: String) {
        this.summaryJsonFile = summaryJsonFile
    }

    fun setJunitFile(junitFile: String) {
        this.junitFile = junitFile
    }

    @Throws(IOException::class)
    fun generateHtml(summary: ExecutionSummary?): File? {
        if (summary == null) return null

        val output = File(reportPath!! + htmlOutputFile!!)
        ConsoleUtils.log("generating HTML output for this execution to " + output.absolutePath)

        val engineContext = Context()
        engineContext.setVariable("execution", ExecutionSummary.gatherExecutionData(summary))
        engineContext.setVariable("logs", summary.logs)
        engineContext.setVariable("summary", summary)

        ExecutionSummary.gatherSupplementProofs(summary)
        if (MapUtils.isNotEmpty(summary.screenRecordings)) {
            engineContext.setVariable("screenRecordings", summary.screenRecordings)
            engineContext.setVariable("hasSupplemental", true)
        }
        if (summary.wsLogs != null) {
            engineContext.setVariable("wsLogs", summary.wsLogs)
            engineContext.setVariable("hasSupplemental", true)
        }

        val sysProps = System.getProperties()
        if (sysProps.containsKey(WEB_METRICS_GENERATED) &&
            BooleanUtils.toBoolean(sysProps.getProperty(WEB_METRICS_GENERATED))) {
            engineContext.setVariable("browser_metrics_html", WEB_METRICS_HTML)
        }

        val content = templateEngine!!.process(executionTemplate!!, engineContext)
        return if (StringUtils.isNotBlank(content)) {
            FileUtils.writeStringToFile(output, content, DEF_FILE_ENCODING)
            val executionOutputJsContent = ResourceUtils.loadResource(Web.WEB_METRICS_HTML_LOC
                                                                         + Web.WEB_EXECUTION_OUTPUT_JS)
            FileUtils.writeStringToFile(File(reportPath!! + Web.WEB_EXECUTION_OUTPUT_JS),
                                        executionOutputJsContent, DEF_FILE_ENCODING)
            output
        } else {
            ConsoleUtils.error("No HTML content generated for this execution...")
            null
        }
    }

    @Throws(IOException::class)
    fun generateJson(summary: ExecutionSummary): List<File> {
        val jsons = ArrayList<File>()

        val detailJson = File(reportPath!! + detailJsonFile!!)
        FileUtils.writeStringToFile(detailJson, GSON_COMPRESSED.toJson(summary), DEF_CHARSET)
        jsons.add(detailJson)

        val report = summary.toSummary()
        if (report != null) {
            val summaryJson = File(reportPath!! + summaryJsonFile!!)
            FileUtils.writeStringToFile(summaryJson, GSON_COMPRESSED.toJson(report), DEF_CHARSET)
            jsons.add(summaryJson)
        }

        return jsons
    }

    @Throws(IOException::class)
    fun generateJUnitXml(summary: ExecutionSummary?): File? {
        if (summary == null) return null

        val output = File(reportPath!! + junitFile!!)
        ConsoleUtils.log("generating JUnit XML output for this execution to " + output.absolutePath)

        // convert to JUnit XML
        JUnitReportHelper.toJUnitXml(summary, output)

        return output
    }

    companion object {
        @JvmStatic
        fun openExecutionResult(context: ExecutionContext?, script: File?) {
            if (context == null) return
            if (script == null) return

            if (!isAutoOpenResult()) {
                if (ExecUtils.isRunningInZeroTouchEnv()) ConsoleUtils.log(RB.Commons.text("noAutoOpenResult"))
                return
            }

            val spreadsheetExe = context.getStringData(SPREADSHEET_PROGRAM, getDefault(SPREADSHEET_PROGRAM))
            System.setProperty(SPREADSHEET_PROGRAM, spreadsheetExe)

            if (StringUtils.equals(spreadsheetExe, SPREADSHEET_PROGRAM_WPS)) {
                if (!context.hasData(WPS_EXE_LOCATION)) {
                    // lightweight: resolve now to save time later
                    context.setData(WPS_EXE_LOCATION, Excel.resolveWpsExecutablePath())
                }
                if (context.hasData(WPS_EXE_LOCATION)) {
                    System.setProperty(WPS_EXE_LOCATION, context.getStringData(WPS_EXE_LOCATION)!!)
                }
            }

            Excel.openExcel(script)
        }

        @JvmStatic
        fun openExecutionSummaryReport(file: File?) {
            if (file != null) openExecutionSummaryReport(file.absolutePath)
        }

        @JvmStatic
        fun openExecutionSummaryReport(location: String) {
            if (!isAutoOpenExecResult()) {
                if (ExecUtils.isRunningInZeroTouchEnv()) ConsoleUtils.log(RB.Commons.text("noAutoOpenResult"))
            } else ExecUtils.openExecReportFile(location)
        }

    }
}