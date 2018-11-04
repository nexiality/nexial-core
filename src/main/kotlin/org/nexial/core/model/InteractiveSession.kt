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

package org.nexial.core.model

import org.apache.commons.lang3.SystemUtils
import org.nexial.commons.utils.EnvUtils

data class InteractiveSession(val context: ExecutionContext) {

    // system
    val startTime: Long = System.currentTimeMillis()
    val hostname: String = EnvUtils.getHostName()
    val user: String = SystemUtils.USER_NAME

    // user input
    var script: String? = null
    var dataFile: String? = null
    //    var scenario: String? = "nexial_function_count"
    var scenario: String? = null
    //    var activities: List<String>? = mutableListOf("showcase for $(count)", "showcase for $(array)")
    var activities: List<String>? = mutableListOf()

    var steps: List<String>? = mutableListOf()
    var iteration: Int = 0

    // execution output
    var results: List<StepResult>? = mutableListOf()
    var exception: Throwable? = null

    fun clearSteps() {
        steps = mutableListOf()
    }

    fun clearActivities() {
        activities = mutableListOf()
    }
}
