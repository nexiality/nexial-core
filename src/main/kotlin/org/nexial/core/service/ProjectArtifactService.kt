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

package org.nexial.core.service

import org.nexial.commons.utils.FileUtil
import org.nexial.core.tools.DataVariableUpdater
import org.nexial.core.tools.MacroUpdater
import org.nexial.core.tools.MacroUpdater.MacroChange
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller
import java.io.File

@Controller("project-artifact")
class ProjectArtifactService {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @MessageMapping("/data-variable-update")
    @SendTo("/reply/data-variable-update")
    @Throws(Exception::class)
    fun updateDataVariables(request: DataVariableUpdateRequest): DataVariableUpdateResponse {
        logger.debug("received request: $request")

        // check project
        val project = request.project
        if (!FileUtil.isDirectoryReadWritable(File(project))) {
            throw IllegalArgumentException("project '$project' is not a valid or accessible directory.")
        }

        val changes = request.changes
        if (changes.isEmpty()) {
            throw IllegalArgumentException("No data variable changes proposed via 'changes' property.")
        }

        val updater = DataVariableUpdater()
        updater.searchFrom = project
        updater.variableMap = changes
        updater.isPreview = request.preview

        val updateLogs = updater.updateAll()

        return DataVariableUpdateResponse(project, updateLogs, updater.isPreview)
    }

    @MessageMapping("/macro-update")
    @SendTo("/reply/macro-update")
    @Throws(Exception::class)
    fun updateMacros(request: MacroUpdateRequest): MacroUpdateResponse {

        // check project
        val project = request.project
        if (!FileUtil.isDirectoryReadWritable(File(project))) {
            throw IllegalArgumentException("project '$project' is not a valid or accessible directory.")
        }

        val preview = request.preview

        val changes = mutableListOf<MacroChange>()
        request.options.forEach {
            changes += MacroChange(fromFile = it.fromFile, toFile = it.fromFile,
                                   fromSheet = it.fromSheet, toSheet = it.fromSheet,
                                   fromName = it.fromName, toName = it.toName)
        }
        val option = MacroUpdater.MacroUpdaterOptions(searchFrom = project, changes = changes, preview = preview)

        val updater = MacroUpdater
        updater.resolveProjectMeta(option)
        val updateLogs = updater.updateAll(option)
        logger.debug("completed request: $updateLogs")

        return MacroUpdateResponse(updateLogs)
    }
}