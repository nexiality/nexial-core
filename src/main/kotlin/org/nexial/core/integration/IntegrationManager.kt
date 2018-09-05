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

package org.nexial.core.integration

import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.nexial.core.ExecutionThread
import org.nexial.core.integration.connection.ConnectionFactory
import org.nexial.core.integration.jira.JiraHelper
import org.nexial.core.integration.slack.SlackHelper
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionDefinition
import org.nexial.core.model.TestProject
import org.nexial.core.plugins.aws.S3Command
import org.nexial.core.utils.ExecUtil
import org.nexial.core.variable.Syspath
import java.io.File
import java.io.File.separator
import java.net.URL

class IntegrationManager {

    companion object {

        fun manageIntegration(outputDirPath: String) {

            val context = ExecutionContext(createExecDefinition())
            ExecutionThread.set(context)
            val outputDir = resolveOutputDir(context, outputDirPath)
            val iterationOutputList = mutableListOf<IterationOutput>()
            val executionOutput = ExecutionOutput().readExecutionSummary(outputDir.absolutePath)

            when {
//                outputDir.isFile      -> if (isExcelFile(outputDir)) iterationOutputList.add(ExcelOutput(outputDir).parse())
                outputDir.isDirectory -> outputDir.listFiles().forEach { file ->
                    if (isExcelFile(file)) {
                        val iterationOutput = ExcelOutput(file).parse()
                        iterationOutput.executionOutput = executionOutput
                        iterationOutputList.add(iterationOutput)
                    }
                }
            }
            executionOutput.iterations = iterationOutputList
            handle(executionOutput, context)
        }

        private fun isExcelFile(file: File) = (StringUtils.startsWith(file.name, "~").not()
                && StringUtils.endsWith(file.name, ".xlsx"))

        private fun createExecDefinition(): ExecutionDefinition {
            val execDef = ExecutionDefinition()
            execDef.runId = ExecUtil.deriveRunId()
            val project = TestProject()
            project.name = "integration"
            project.projectHome = "${System.getProperty("user.home")}$separator.nexial${separator}native"
            project.outPath = File("${project.projectHome}$separator${project.name}${separator}output").absolutePath
            val artifact = File("${project.projectHome}$separator${project.name}${separator}artifact").absolutePath
            project.artifactPath = File(artifact).absolutePath
            project.scriptPath = File("$artifact${separator}script$separator").absolutePath
            project.dataPath = File("$artifact${separator}data$separator").absolutePath
            project.planPath = File("$artifact${separator}plan$separator").absolutePath
            execDef.project = project
            return execDef
        }

        private fun resolveOutputDir(context: ExecutionContext, outputDirUrl: String): File {
            if (UrlValidator.getInstance().isValid(outputDirUrl)) {

                // todo: check for s3 in url for s3 implementation

                // use profile name, for s3Command to work
                val profile = "temp"
                val url = URL(outputDirUrl)
                context.setData("$profile.aws.accessKey", System.getProperty("otc.accessKey"))
                context.setData("$profile.aws.secretKey", System.getProperty("otc.secretKey"))
                context.setData("$profile.aws.region", System.getProperty("otc.region"))
                val s3Command = S3Command()
                s3Command.init(context)
                val result = "~s3OutputDir"

                val remotePath = url.path.removePrefix("/")
                val stepResult = s3Command.copyFrom(result, profile,
                        "${StringUtils.appendIfMissing(remotePath, "/")}*",
                        Syspath().out("fullpath"))

                if (stepResult.isSuccess) {
                    return File(Syspath().out("fullpath"))
                } else {
                    throw IllegalArgumentException("Unable to download output files from given url: $outputDirUrl")
                }
            }

            return File(outputDirUrl)
        }

        /*private fun setDataToContext(data: Map<String, String>, context: ExecutionContext) {
            data.forEach { key, value -> context.setData(key, value) }
        }*/

        @JvmStatic
        fun isValidServer(profile: String): Boolean {
            // check for Nexial supported integration servers
            return StringUtils.equalsAnyIgnoreCase(profile, "Jira", "Slack")
        }

        private fun handle(executionOutput: ExecutionOutput, context: ExecutionContext) {
            val servers = mutableSetOf<String>()
            executionOutput.iterations.forEach { iteration ->
                for (scenario in iteration.scenarios) {

                    scenario.projects.forEach { project -> servers.add(project.server!!) }

                }
            }
            servers.forEach { server ->
                if (!isValidServer(server)) {
                    throw IllegalArgumentException("Unsupported server $server specified.")
                }
                when (server) {
                    "jira" -> {
                        val httpClient = ConnectionFactory.getInstance(context).getAsyncWsClient(server)
                        JiraHelper(context, httpClient).process(server, executionOutput)
                    }
                    "slack" -> {
                        val httpClient = ConnectionFactory.getInstance(context).getAsyncWsClient(server)
                        SlackHelper(context, httpClient).process(server, executionOutput)
                    }
                }
            }
        }
    }
}