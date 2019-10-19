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

import com.google.gson.JsonParser
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.json.JSONArray
import org.json.JSONObject
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.Integration.OTC_PREFIX
import org.nexial.core.NexialConst.Project.SCRIPT_FILE_EXT
import org.nexial.core.integration.connection.ConnectionFactory
import org.nexial.core.integration.jira.JiraHelper
import org.nexial.core.integration.slack.SlackHelper
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionDefinition
import org.nexial.core.model.TestProject
import org.nexial.core.plugins.aws.S3Command
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils
import org.nexial.core.variable.Syspath
import java.io.File
import java.io.File.separator
import java.net.URL
import java.nio.charset.Charset

class IntegrationManager {

    companion object {
        var remoteUrl: String? = null

        @JvmStatic
        fun manageIntegration(remoteUrl: String) {
            val context = ExecutionContext(createExecDefinition())
            ExecutionThread.set(context)
            val localPath = Syspath().out("fullpath")
            if (!copyOutputDirTo(context, localPath, remoteUrl)) {
                throw IllegalArgumentException("Unable to download output files from given url: $remoteUrl")
            }

            val iterationOutputList = mutableListOf<IterationOutput>()
            val executionOutput = ExecutionOutput().readExecutionSummary(localPath)
            val outputDir = File(localPath)
            when {
//          outputDir.isFile      -> if (isExcelFile(outputDir)) iterationOutputList.add(ExcelOutput(outputDir).parse())
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

        private fun isExcelFile(file: File) =
                StringUtils.startsWith(file.name, "~").not() && StringUtils.endsWith(file.name, SCRIPT_FILE_EXT)

        private fun createExecDefinition(): ExecutionDefinition {
            val execDef = ExecutionDefinition()
            execDef.runId = ExecUtils.deriveRunId()
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

        private fun copyOutputDirTo(context: ExecutionContext, localPath: String, remotePath: String): Boolean {
            ConsoleUtils.log("Copying remote dir '$remotePath' to output dir '$localPath")

            if (UrlValidator.getInstance().isValid(remotePath)) {

                // todo: check for s3 in url for s3 implementation

                // use profile name, for s3Command to work
                val profile = "temp"
                val url = URL(remotePath).path.removePrefix("/")
                remoteUrl = url
                context.setData("$profile.aws.accessKey", System.getProperty("${OTC_PREFIX}accessKey"))
                context.setData("$profile.aws.secretKey", System.getProperty("${OTC_PREFIX}secretKey"))
                context.setData("$profile.aws.region", System.getProperty("${OTC_PREFIX}region"))
                val s3Command = S3Command()
                s3Command.init(context)
                val result = "~s3OutputDir"
                val stepResult = s3Command.copyFrom(result, profile,
                                                    "${StringUtils.appendIfMissing(url, "/")}*",
                                                    localPath)

                return stepResult.isSuccess
            }
            return false
        }

        /*private fun setDataToContext(data: Map<String, String>, context: ExecutionContext) {
            data.forEach { key, value -> context.setData(key, value) }
        }*/

        /**
         * check for Nexial supported integration servers
         * @param profile String
         * @return Boolean
         */
        @JvmStatic
        fun isValidServer(profile: String) = StringUtils.equalsAnyIgnoreCase(profile, "Jira", "Slack")

        private fun handle(executionOutput: ExecutionOutput, context: ExecutionContext) {
            val servers = mutableSetOf<String>()
            executionOutput.iterations.forEach { iteration ->
                for (scenario in iteration.scenarios) {
                    scenario.projects.forEach { project -> servers.add(project.server!!) }
                }
            }

            val metadata = JSONArray()
            servers.forEach { server ->
                if (!isValidServer(server)) throw IllegalArgumentException("Unsupported server $server specified.")

                when (server) {
                    "jira"  -> {
                        val httpClient = ConnectionFactory.getInstance(context).getAsyncWsClient(server)
                        val jiraHelper = JiraHelper(context, httpClient)
                        val integrationMeta = jiraHelper.process(server, executionOutput)

                        metadata.put(JSONObject().put(server, JSONObject(GSON.toJson(integrationMeta))))
                    }

                    "slack" -> {
                        val httpClient = ConnectionFactory.getInstance(context).getAsyncWsClient(server)
                        SlackHelper(context, httpClient).process(server, executionOutput)
                    }
                }
            }

            updateMeta(metadata, context)
        }

        private fun updateMeta(data: JSONArray, context: ExecutionContext) {
            val jsonFile = "${Syspath().out("fullpath")}${separator}integrationMeta.json"

            FileUtils.write(File(jsonFile),
                            GSON.toJson(JsonParser.parseString(data.toString())),
                            Charset.defaultCharset())
            val s3Command = S3Command()
            s3Command.init(context)
            val result = s3Command.copyTo("copyMeta", "temp", jsonFile, remoteUrl!!)
            if (!result.isSuccess) {
                throw IllegalArgumentException("Integration meta is not update to S3 output folder.")
            }
        }
    }
}