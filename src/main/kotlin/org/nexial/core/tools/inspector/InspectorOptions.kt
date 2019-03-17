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

package org.nexial.core.tools.inspector

import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Project.PROJECT_CACHE_LOCATION
import org.nexial.core.NexialConst.Project.resolveStandardPaths
import org.nexial.core.model.TestProject
import java.io.File
import java.io.File.separator

class InspectorOptions(val directory: String,
                       val viewMode: InspectorViewMode,
                       val verbose: Boolean,
                       val useCache: Boolean = true) {

    val advices = mutableListOf<String>()
    val outputJson = "$directory$separator${viewMode.outputFileName}"
    val project = TestProject()
    lateinit var cacheHome: String

    init {
        project.projectHome = File(directory).absolutePath
        project.isStandardStructure = true
        resolveStandardPaths(project)

        if (StringUtils.isBlank(project.boundProjectId)) {
            // add advice to improve project identification
            advices += ProjectInspector.getMessage("meta.file.missing")
        }

        if (useCache) cacheHome = "$PROJECT_CACHE_LOCATION${project.name}$separator"
    }
}
