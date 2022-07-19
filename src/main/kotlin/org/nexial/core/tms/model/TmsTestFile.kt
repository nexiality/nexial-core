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


/**
 * Represent the TMS Meta json for the project. Each project will have a single TMS project id and can have multiple file
 * entries associated with different suites
 */
class TmsMeta(val projectId: String, var files: MutableList<TestFile>?) {
    fun addFile(file: TestFile) = if (files == null) files = mutableListOf(file) else files!!.add(file)

    fun removeFile(file: TestFile) { if (files != null) files!!.removeIf { f -> f.path == file.path  } }
}

/**
 * Contains the project id of the current TMS project and and a file entry from the json file
 */
class TmsTestFile(val projectId: String, val file: TestFile?)

/**
 * Represents a single file entry inside the file [List] inside TMS Meta json for the project
 */
class TestFile {
    var path: String? = null
    var fileType: String? = null
    var suiteId: String? = null
    var subplan: String? = null
    var planSteps: List<TestFile>? = null
    var scenarios: List<Scenario>? = null
    var suiteUrl: String? = null
    var cache: MutableMap<String, String>? = null
    var stepId: String? = null

    @Transient
    val projectId: String? = null

    constructor(path: String?, fileType: String?, suiteId: String?, subplan: String?, planSteps: List<TestFile>?,
                scenarios: List<Scenario>?, suiteUrl: String?, cache: MutableMap<String, String>?) {
        this.path = path
        this.fileType = fileType
        this.suiteId = suiteId
        this.subplan = subplan
        this.planSteps = planSteps
        this.scenarios = scenarios
        this.suiteUrl = suiteUrl
        this.cache = cache
    }

    constructor()
}

/**
 * Represents a single test case entry in the TMS Meta Json file. Each script can have multiple scenarios
 */
class Scenario(val testCase: String, val name: String, val testCaseId: String)
