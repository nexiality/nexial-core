package org.nexial.core.integration

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.ExecutionThread
import java.io.File

class TemplateEngine {

    companion object {
        val context = ExecutionThread.get()!!

        fun setSlackChatSummary(executionOutput: ExecutionOutput): String {
            var slackSummaryTemplate = StringUtils.replace(getTemplate("slack", COMMENT_ENDPOINT),
                    System.getProperty("line.separator"), "")

            val testEnvironmentDetails = "User: ${executionOutput.runUser} \\n System: " +
                    "${executionOutput.runHost} \\n Start Time: ${executionOutput.startTime}"
            context.setData("testEnvironmentDetails", testEnvironmentDetails)
            val color = if (executionOutput.failCount > 0) "#FF0000" else "#008000"
            context.setData("resultColor", color)
            context.setData("totalSteps", executionOutput.totalSteps)
            context.setData("totalExecuted", executionOutput.executed)
            context.setData("totalPassed", executionOutput.passCount)
            context.setData("totalFailed", executionOutput.failCount)
            slackSummaryTemplate = context.replaceTokens(slackSummaryTemplate)
            return slackSummaryTemplate
        }

        fun setJiraCommentSummary(scenario: ScenarioOutput): String {
            val iteration = scenario.iterationOutput!!
            var result = parseSummaryToMdTable(iteration.summary!!.execSummary!!)
            result = "| test script | ${iteration.summary!!.title} | \\n $result"
            return result
        }

        private fun parseSummaryToMdTable(map: Map<String, String>): String {
            val sb = StringBuilder()
            map.forEach { key, value ->
                var result = value
                if (value.contains("100.00%")) {
                    result = "{panel:bgColor=lightgreen} $value {panel}"
                }
                sb.append(" | $key | $result |\\n")
            }
            return sb.toString()
        }

        fun setJiraDefect(projectInfo: ProjectInfo, scenarioOutput: ScenarioOutput): MutableList<String> {
            val projectKey = projectInfo.projectName
            val sb = StringBuilder()
            val failedSteps = scenarioOutput.testSteps
            sb.append("h2. *Nexial Defect Summary*\\n----\\n")
            sb.append("h3. *Relates to Feature IDs:* ${projectInfo.features.joinToString(separator = ",")} \\n ")
            sb.append("h3. *Relates to Test IDs:* ${projectInfo.testIds.joinToString(separator = ",")} \\n ")
            sb.append("h3. *Test Script*: ${scenarioOutput.iterationOutput!!.fileName} \\n ")
            sb.append("h3. *Test Scenario*: ${scenarioOutput.scenarioName} \\n ")
            sb.append("h3. *Test Steps*:\\n")
            sb.append("|| Row || Activity || Description || Command || Result || \\n")

            val defectlist = mutableListOf<String>()
            failedSteps.forEach { stepIndex, value ->
                val activity = value[0]
                val description = value[1]
                val command = "${value.get(2)}&raquo;${value.get(3)}"
                var params = ""
                (4..8).forEach { i ->
                    val param = value.get(i)
                    if (param.isNotBlank()) {
                        params = "$params$param, "
                    }
                }
                params = "[${StringUtils.removeEnd(params, ", ")}]"
                val msg = RegexUtils.replace(value.get(13), "\"", "'")
                sb.append(
                        "| $stepIndex | $activity | $description | $command $params | {panel:bgColor=#ff4d4d} $msg {panel} | \\n ")

                // check for uniqueness of this hash value.. consider scripts with same values?
                val defecthash = "${scenarioOutput.scenarioName}_${activity}_$stepIndex"
                defectlist.add(RegexUtils.replace(defecthash, "\\s+", ""))
            }
            sb.append("\\n\\n")
            val summary = "A defect is created based on test script failures."
            val description = sb.toString()
            context.setData(JIRA_PROJECT_KEY, projectKey)
            context.setData(JIRA_DEFECT_SUMMARY, summary)
            context.setData(JIRA_DEFECT_DESCRIPTION, description)
            return defectlist
        }

        fun getTemplate(target: String, template: String): String {
            val resourceBasePath = "/${StringUtils.replace(this::class.java.getPackage().name, ".",
                    "/")}"
            val filePath = "$resourceBasePath/$target/$template.json"
            return FileUtils.readFileToString(File(this::class.java.getResource(filePath).toURI()), "UTF-8")!!
        }
    }
}