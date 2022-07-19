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

package org.nexial.core.tms.model

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.USER_NAME
import org.nexial.core.NexialConst.Project.DEF_REL_LOC_ARTIFACT
import org.nexial.core.tms.spi.TmsSource
import org.nexial.core.tms.spi.TmsSource.*
import org.nexial.core.tms.spi.jira.Id
import org.nexial.core.utils.ExecUtils.NEXIAL_MANIFEST
import java.io.File
import java.util.*

/**
 * Represents a TMS Test Suite
 */
class TmsSuite(val id: String, val name: String, var suiteUrl: String?, var testCases: Map<String, String>?) {
    constructor(name: String, id: String, testCases: Map<String, String>?) : this(id, name, null, testCases)
}

/**
 * Represents a TMS Test Suite Description
 */
class TmsSuiteDesc(val filePath: String) {
    val project: String = File(StringUtils.substringBefore(filePath, DEF_REL_LOC_ARTIFACT)).name
    val file: String = File(filePath).name
    val suiteDescAsMap = linkedMapOf(
        "Project" to project,
        "File Name" to file,
        "User" to USER_NAME,
        "Nexial Version" to NEXIAL_MANIFEST,
        "Modified Time" to Date().toString()
    )

    /**
     * Returns suite description well formatted to be updated on the provided TMS tool.
     * This description includes project name, script/plan name, username, nexial version and modified time
     *
     * @param tmsType [TmsSource] enum e.g. JIRA, TestRail
     * @return formatted [String] of suite description
     */
    fun format(tmsType: TmsSource): String {
        return when (tmsType) {
            JIRA -> format("|*", "*| ", "|\n")
            TESTRAIL -> format("||", "|", "\n")
            AZURE -> "<table>" + format("<tr><td><b>", "</b></td><td>=  ", "</td></tr>") + "</table>"
        }
    }

    fun format(beforeHeader: String, afterHeader: String, afterValue: String): String {
        var prefix = "Test suite corresponds to :: \n"
        suiteDescAsMap.forEach { prefix += "$beforeHeader${it.key}$afterHeader${it.value}$afterValue" }
        return prefix
    }
}

// might need to add or remove add some fields for azure
data class TestRun(var id: Int = 0, var name: String? = null,
                   var isAutomated: Boolean = true,
                   var plan: Plan? = null,
                   var startDate: String? = System.currentTimeMillis().toString(),
                   var completeDate: String = System.currentTimeMillis().toString(),
                   var state: String = "InProgress",
                   var type: String = "NoConfigRun",
                   val build: Build? = Build("1", NEXIAL_MANIFEST),
                   val comment: String = "",
                   var runSummary: List<RunSummary>? = mutableListOf()) {
    constructor(id: Int, name: String) : this(id, name, true)
}

data class RunSummary(val duration: Long, val resultCount: Int, val testOutcome: String)

data class ResultSummary(var attachments: MutableList<String> = mutableListOf(),
                         var activities: Map<String, List<String>> = HashMap(), var storage: String? = null)

data class AzureTestResult(var id: Int, val testCaseTitle: String, val state: String, val automatedTestName: String,
                           val automatedTestStorage: String, val automatedTestType: String, var startedDate: String,
                           var completedDate: String,var testPoint: Id? = null, val outcome: String,
                           val failureType: String)

//data class TestRun(val id: Int, val name: String)
data class TestRailTestRun(val suite_id: String?, val name: String,
                           val include_all: Boolean, val case_ids: MutableSet<Int>?)

data class TestResultAttachment(var attachmentType: String, val comment: String,
                                val fileName: String, val encodedData: String)

data class Build(val id: String, val name: String)
data class Plan(val id: String?)
data class Pipeline(val pipelineId: Int)
