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

package org.nexial.core.plugins.web

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.GSON
import org.nexial.core.NexialConst.Web.BROWSER_META
import org.nexial.core.NexialConst.Web.NS_WEB_METRICS
import org.nexial.core.model.BrowserMeta
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.TestStep
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.Execution
import org.openqa.selenium.WebDriverException
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class ClientPerformanceCollector(val command: WebCommand, private val output: String) {
    private val resourceBase = "/org/nexial/core/plugins/web/metrics/"
    private val initScript = fetchScript("_init") + "\n"
    private val endScript = fetchScript("_return")
    private val commandRefBaseUrl = "http://nexiality.github.io/documentation/commands/"

    init {
        ConsoleUtils.log("web metrics collection enabled and will be saved to $output")
    }

    @Throws(IOException::class)
    fun collect() {
        val context = command.context
        val metrics = context.getDataByPrefix(NS_WEB_METRICS)
        if (metrics.isEmpty()) return

        val js = initScript +
                 metrics.filter { config -> config.key != "enabled" && BooleanUtils.toBoolean(config.value) }
                     .map { config -> fetchScript(config.key) }
                     .reduce { acc, s -> acc + "\n" + s } +
                 "\n" +
                 endScript

        try {
            val result = command.jsExecutor.executeScript(js)
            val stepJson = newStep(context.currentTestStep, GSON.fromJson(result.toString(), JsonObject::class.java))

            val jsonFile = File(output)
            val execution = if (FileUtil.isFileReadable(output, 5)) {
                GSON.fromJson(FileReader(jsonFile), JsonObject::class.java)
            } else {
                newExecution(context)
            }

            val script = findOrCreateScript(execution)
            val scenario = findOrCreateChild(script, "scenarios", context.currentScenario, "activities")
            val activity = findOrCreateChild(scenario, "activities", context.currentActivity, "steps")
            activity.getAsJsonArray("steps").add(stepJson)

            FileWriter(jsonFile).use { GSON.toJson(execution, it) }
        } catch (e: WebDriverException) {
            ConsoleUtils.error("Error occurred while collecting browser performance metrics - " +
                               "${WebDriverExceptionHelper.analyzeError(context, context.currentTestStep, e)}; " +
                               "ignoring...")
        }
    }

    private fun findOrCreateScript(node: JsonObject): JsonObject {
        val execution = Execution()
        val planName = execution.plan("name")
        val planStep = execution.plan("index")
        val iteration = execution.iteration("index")
        val scriptName =
                (if (planName.isNotBlank()) "$planName.$planStep." else "") + execution.script("name") +
                (if (iteration == "" || iteration == "0") "" else ".${StringUtils.leftPad(iteration + "", 3, "0")}")
        return findOrCreateChild(node, "scripts", scriptName, "scenarios")
    }

    private fun findOrCreateChild(node: JsonObject, childNode: String, childName: String, childArray: String):
            JsonObject {
        val children = node.getAsJsonArray(childNode)
        var child = children.firstOrNull { it.asJsonObject.get("name").asString == childName }?.asJsonObject
        if (child == null) {
            child = JsonObject()
            child.addProperty("name", childName)
            child.add(childArray, JsonArray())
            children.add(child)
        }
        return child
    }

    private fun newStep(step: TestStep, metrics: JsonObject): JsonObject {
        metrics.addProperty("row", (step.rowIndex + 1).toString())
        metrics.addProperty("description", step.description)
        metrics.addProperty("command", step.target + " » " + step.command)
        metrics.addProperty("command-ref", "$commandRefBaseUrl${step.target}/${step.command}")
        val parameters = JsonArray()
        step.params.forEach { parameters.add(command.context.replaceTokens(it, true)) }
        metrics.add("parameters", parameters)
        return metrics
    }

    private fun newExecution(context: ExecutionContext): JsonObject {
        val execution = JsonObject()
        execution.addProperty("runID", context.runId)
        execution.addProperty("project", context.project.name)
        execution.add("scripts", JsonArray())

        if (context.hasData(BROWSER_META)) {
            val browserMeta = context.getObjectData(BROWSER_META) as BrowserMeta
            execution.addProperty("browser", "${browserMeta.name} ${browserMeta.version}")
            execution.addProperty("os", browserMeta.os.name)
        }

        return execution
    }

    private fun fetchScript(key: String) = ResourceUtils.loadResource("$resourceBase$key.js")
                                           ?: throw IOException("Unable to fetch content from $resourceBase$key.js")
}