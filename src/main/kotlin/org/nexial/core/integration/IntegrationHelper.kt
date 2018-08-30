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

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.model.ExecutionContext
import java.io.File
import java.util.*
import java.util.stream.Collectors

abstract class IntegrationHelper {
    abstract fun createDefect(profile: String): String?
    abstract fun addLink(url: String, linkBody: String)
    abstract fun addComment(url: String, commentBody: String)
    abstract fun addLabels(url: String, labelsBody: String)

    fun getActions(context: ExecutionContext, profile: String): List<String> {
        var actions = mutableListOf<String>()
        if (!StringUtils.isEmpty(profile)) {
            val data = context.getStringData("$INTEGRATION.$profile.actions")
            actions = Arrays.stream(StringUtils.split(data, ",")).collect(Collectors.toList())
        }
        return actions
    }

    fun parseResultToMdFormat(scenario: ScenarioOutput): String {
        val iteration = scenario.iterationOutput!!
        var result = iteration.parseToMDTable(iteration.summary!!.execSummary!!)
        result = "| test script | ${iteration.summary!!.title} | \\n $result"
        return result
    }

    fun readFileToString(filePath: String) =
        FileUtils.readFileToString(File(javaClass.getResource(filePath).toURI()), "UTF-8")!!

    fun getTemplate(target: String): String {
        val resourceBasePath = "/${StringUtils.replace(this.javaClass.getPackage().name, ".", "/")}"
        return readFileToString("$resourceBasePath/$target.json")
    }
}