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

open class IterationOutput {

    var fileName: String? = null
    var executionOutput: ExecutionOutput? = null
    var iteration: String? = null
    var summary: SummaryOutput? = null
    var scenarios: MutableList<ScenarioOutput> = mutableListOf()
    var data: Map<String, String>? = null

    fun getScenarioOutput(scenarioName: String): ScenarioOutput {
        return scenarios.find { scenarioOutput -> scenarioOutput.scenarioName == scenarioName }!!
    }

    fun getIterationFromTitle(): String {
        return StringUtils.substringAfterLast(summary!!.title, ".")
    }
}

open class SummaryOutput {
    var title: String? = null
    var execSummary: Map<String, String>? = null
}

open class ScenarioOutput {
    var iterationOutput: IterationOutput? = null
    var scenarioName: String? = null
    var projects = mutableListOf<ProjectInfo>()
    var scenarioSummaryMap = mutableMapOf<String, String>()
    var testSteps = mutableMapOf<Int, List<String>>()
    var isFailed = false
}