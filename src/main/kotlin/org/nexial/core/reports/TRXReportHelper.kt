package org.nexial.core.reports

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.jdom2.Document
import org.jdom2.Element
import org.nexial.core.NexialConst.*

import org.nexial.core.NexialConst.Project.NEXIAL_EXECUTION_TYPE
import org.nexial.core.NexialConst.Project.NEXIAL_EXECUTION_TYPE_SCRIPT
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.tms.TmsConst.COMPUTER_NAME
import org.nexial.core.tms.TmsConst.CREATION
import org.nexial.core.tms.TmsConst.DATE_FORMAT1
import org.nexial.core.tms.TmsConst.DURATION
import org.nexial.core.tms.TmsConst.ERROR_INFO
import org.nexial.core.tms.TmsConst.EXECUTION_ID
import org.nexial.core.tms.TmsConst.FAILED
import org.nexial.core.tms.TmsConst.FINISH
import org.nexial.core.tms.TmsConst.ID
import org.nexial.core.tms.TmsConst.MESSAGE
import org.nexial.core.tms.TmsConst.NAME
import org.nexial.core.tms.TmsConst.OUTCOME
import org.nexial.core.tms.TmsConst.OUTPUT1
import org.nexial.core.tms.TmsConst.PASSED
import org.nexial.core.tms.TmsConst.PATH
import org.nexial.core.tms.TmsConst.RESULTS
import org.nexial.core.tms.TmsConst.RESULT_FILE
import org.nexial.core.tms.TmsConst.RESULT_FILES
import org.nexial.core.tms.TmsConst.RESULT_SUMMARY
import org.nexial.core.tms.TmsConst.RUN_USER
import org.nexial.core.tms.TmsConst.START
import org.nexial.core.tms.TmsConst.START_TIME
import org.nexial.core.tms.TmsConst.STDOUT
import org.nexial.core.tms.TmsConst.STORAGE
import org.nexial.core.tms.TmsConst.TEST_DEFINITIONS
import org.nexial.core.tms.TmsConst.TEST_ID
import org.nexial.core.tms.TmsConst.TEST_NAME_TMS
import org.nexial.core.tms.TmsConst.TEST_RUN
import org.nexial.core.tms.TmsConst.TIMES
import org.nexial.core.tms.TmsConst.UNIT_TEST
import org.nexial.core.tms.TmsConst.UNIT_TEST_RESULT
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.OutputFileUtils
import java.io.File
import java.io.FileOutputStream
import java.util.*

// Create trx report uploading to AzureDevops pipeline for execution run
class TRXReportHelper(val summaryTextOutput: String, val executionSummaryHTML: String,
                      val summary: ExecutionSummary, val output: File) {
    var testId = 1

    @Throws(Exception::class)
    fun toTrxXml() {
        val document = Document()
        val testRuns = toTestRuns(summary)
        document.rootElement = testRuns

        val unitTestResults = mutableListOf<Element>()
        val testDefinitions = mutableListOf<Element>()

        // script
        summary.nestedExecutions.forEach { scriptExec ->
            val results = toUnitTestResults(scriptExec)
            unitTestResults.addAll(results)
            testDefinitions.addAll(toTestDefinitions(scriptExec, results))
        }
        val result = Element(RESULTS)
        val testDefinition = Element(TEST_DEFINITIONS)
        testDefinition.addContent(testDefinitions)

        testRuns.addContent(toTestTimes(summary))
        testRuns.addContent(toResultSummary(summary))
        result.addContent(unitTestResults)
        testRuns.addContent(testDefinition)
        testRuns.addContent(result)

        FileOutputStream(output).use { out -> PRETTY_XML_OUTPUTTER.output(document, out) }
    }

    private fun toTestRuns(summary: ExecutionSummary): Element {
        val testRuns = Element(TEST_RUN)
        testRuns.setAttribute(ID, summary.name)
        testRuns.setAttribute(NAME, summary.name)
        testRuns.setAttribute(RUN_USER, summary.runUser)
        return testRuns
    }

    private fun toTestTimes(scriptExec: ExecutionSummary): Element {
        val times = Element(TIMES)
        times.setAttribute(START, DATE_FORMAT1.format(Date(scriptExec.startTime)))
        times.setAttribute(FINISH, DATE_FORMAT1.format(Date(scriptExec.endTime)))
        times.setAttribute(CREATION, DATE_FORMAT1.format(Date()))
        return times
    }

    private fun toTestDefinitions(scriptExec: ExecutionSummary, results: MutableList<Element>): MutableList<Element> {
        val scriptName = scriptExec.name.substringBeforeLast(" (")
        val storage =
            (if (System.getProperty(NEXIAL_EXECUTION_TYPE) == NEXIAL_EXECUTION_TYPE_SCRIPT) ""
            else "${scriptExec.planName}[${scriptExec.planSequence}] - ") + scriptName
        val unitTests = mutableListOf<Element>()

        results.forEach { result ->
            val unitTest = Element(UNIT_TEST)
            if(result.getAttributeValue(TEST_ID) == null) return@forEach
            unitTest.setAttribute(ID, result.getAttributeValue(TEST_ID))
            unitTest.setAttribute(NAME, result.getAttributeValue(TEST_NAME_TMS))
            unitTest.setAttribute(STORAGE, "${storage}[${result.getAttributeValue(STORAGE)}]")
            result.removeAttribute(STORAGE)
            unitTests.add(unitTest)
        }
        return unitTests
    }

    private fun toResultSummary(summary: ExecutionSummary): Element {
        val resultSummary = Element(RESULT_SUMMARY)
        val outcome = if (summary.failCount > 0) FAILED else PASSED
        resultSummary.setAttribute(OUTCOME, outcome)
        addResultFiles(summary, resultSummary)
        return resultSummary
    }

    private fun addResultFiles(summary: ExecutionSummary, element: Element) {
        val filterFiles = mutableListOf<String>()
        summary.nestedExecutions.forEach { filterFiles.addAll(it.resultSummary.attachments) }

        val file1 = File(executionSummaryHTML)
        val file2 = File(summaryTextOutput)
        if (file1.exists()) filterFiles.add(file1.absolutePath)
        if (file2.exists()) filterFiles.add(file2.absolutePath)
        toResultFiles(filterFiles, element)
    }

    private fun toUnitTestResults(scriptExec: ExecutionSummary): MutableList<Element> {
        // all script has at least 1 iteration (Default)
        val unitTestResults = mutableListOf<Element>()
        if (CollectionUtils.isNotEmpty(scriptExec.nestedExecutions)) {
            scriptExec.nestedExecutions.forEach { iteration ->
                iteration.nestedExecutions.forEach { scenario ->
                    unitTestResults.addAll(toUnitTestResult(scenario, iteration))
                }
            }
        }
        return unitTestResults
    }

    private fun toUnitTestResult(scenario: ExecutionSummary, iteration: ExecutionSummary): List<Element> {
        val outputLink = iteration.testScriptLink
        val executionLog = iteration.executionLog
        val unitTestResults: MutableList<Element> = ArrayList()
        val storage = "${iteration.iterationIndex}"
        val unitTestResult = toUnitTestResult(scenario, storage)
        val toOutputLog = toOutputLog(scenario)
        val files = scenario.resultSummary.attachments
        if (toOutputLog != null) {
            files.add(toOutputLog)
        }

        toResultFiles(files, unitTestResult)
        toErrorOutput(unitTestResult, scenario, outputLink, executionLog)
        unitTestResults.add(unitTestResult)
        testId++

        return unitTestResults
    }

    private fun toOutputLog(scenario: ExecutionSummary): String? {
        try {
            var content = ""
            scenario.nestedExecutions.forEach { activity ->
                content += """
            - Activity   : ${activity.name}
              Total Steps : ${activity.totalSteps}
              Passed Steps: ${activity.passCount}
              Failed Steps: ${activity.failCount}
            $NL$NL""".trimIndent()

            }
            val file = OutputFileUtils.prependRandomizedTempDirectory("$testId/scenario-summary.log")
            FileUtils.writeStringToFile(file, content, DEF_FILE_ENCODING)
            return if (file.exists()) file.absolutePath else null
        } catch (e: Exception) {
            ConsoleUtils.log("Unable to create output log for ${scenario.name}")
            return null
        }
    }

    private fun toErrorOutput(unitTestResult: Element, scenario: ExecutionSummary,
                              outputLink: String, executionLog: String) {
        val output = Element(OUTPUT1)
        if (scenario.failCount > 0) {
            toStdOutput(scenario, outputLink, executionLog, output)
            val message = Element(MESSAGE)
            message.addContent("${scenario.failCount} of ${scenario.totalSteps} FAILED. Check " +
                    "$outputLink for execution result, $executionLog for execution logs")
            val errorInfo = Element(ERROR_INFO)
            errorInfo.addContent(message)
            output.addContent(errorInfo)
        }
        unitTestResult.addContent(output)
    }

    private fun toStdOutput(scenario: ExecutionSummary, outputLink: String, executionLog: String, output: Element) {
        val stdOutput = Element(STDOUT)

        stdOutput.addContent("""
            Total : ${scenario.totalSteps}
            Passed: ${scenario.passCount}
            Failed: ${scenario.failCount}
            Output: $outputLink
            Logs  : $executionLog
            """.trimIndent())
        output.addContent(stdOutput)
    }

    private fun toUnitTestResult(scenario: ExecutionSummary, storage: String): Element {
        val unitTestResult = Element(UNIT_TEST_RESULT)
        if(scenario.name == null) return unitTestResult
        unitTestResult.setAttribute(EXECUTION_ID, scenario.name)
        unitTestResult.setAttribute(TEST_ID, testId.toString())
        unitTestResult.setAttribute(TEST_NAME_TMS, scenario.name)
        unitTestResult.setAttribute(STORAGE, storage)
        unitTestResult.setAttribute(COMPUTER_NAME, scenario.runHost)
        unitTestResult.setAttribute(OUTCOME, if (scenario.failCount > 0) FAILED else PASSED)
        unitTestResult.setAttribute(DURATION, milliToTimeFormat(scenario.endTime - scenario.startTime))
        unitTestResult.setAttribute(START_TIME, DATE_FORMAT1.format(scenario.startTime))
        return unitTestResult
    }

    private fun toResultFiles(files: List<String>, unitTests: Element) {
        if (CollectionUtils.isEmpty(files)) return
        val resultFiles = Element(RESULT_FILES)

        files.forEach {
            val resultFile = Element(RESULT_FILE)
            resultFile.setAttribute(PATH, it)
            resultFiles.addContent(resultFile)
        }
        unitTests.addContent(resultFiles)
    }

    private fun milliToTimeFormat(duration: Long): String {
        val seconds1 = duration / 1000
        val seconds = seconds1 % 60
        val minutes = (seconds1 / 60) % 60
        val hours = (seconds1 / (60 * 60)) % 24
        return String.format("%02d:%02d:%02d.%d", hours, minutes, seconds, duration % 1000)
    }
}
