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

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.tools.inspector.InspectorConst.PROJECT_ID
import org.nexial.core.tools.inspector.InspectorConst.UTF8
import org.nexial.core.tools.inspector.ProjectInspector.getMessage
import java.io.File
import java.io.File.separator
import java.io.IOException

class ProjectInfoGenerator(val options: InspectorOptions, val logger: InspectorLogger) {

    fun generate(): ProjectInfo {
        val projectHome = File(options.directory)
        val projectPath = projectHome.absolutePath
        val metaFile = File("$projectPath$separator$PROJECT_ID")
        val isMetaFileExists = FileUtil.isFileReadable(metaFile)

        // projectId is either the content of .meta/project.id or the name of the project directory
        val projectId: String = if (isMetaFileExists) {
            try {
                StringUtils.trim(FileUtils.readFileToString(metaFile, UTF8))
            } catch (e: IOException) {
                projectHome.name
            }
        } else {
            projectHome.name
        }

        val projectInfo = ProjectInfo(projectHome = projectPath, projectId = projectId)

        if (isMetaFileExists) {
            logger.log("meta file found", metaFile.absolutePath)
            projectInfo.metaFile = PROJECT_ID
        } else {
            // add advice to improve project identification
            logger.log("meta file not found")
            projectInfo.advices += getMessage("meta.file.missing")
        }

        logger.log("project id", projectId)
        return projectInfo
    }
}