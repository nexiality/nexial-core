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

import com.google.gson.JsonObject
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.tools.inspector.InspectorConst.GSON
import org.nexial.core.tools.inspector.InspectorConst.LOCAL_HTML_RESOURCE
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.WRITE_FILE
import org.nexial.core.tools.inspector.InspectorConst.UTF8
import org.nexial.core.tools.inspector.InspectorConst.exit
import org.nexial.core.tools.inspector.InspectorViewMode.LOCAL
import org.nexial.core.utils.ExecUtils
import java.io.File
import java.io.File.separator
import java.io.IOException

class InspectorOutput(val logger: InspectorLogger) {

    fun output(options: InspectorOptions, json: JsonObject) {

        // write JSON to JSON file
        try {
            var jsonData = GSON.toJson(json)
            if (options.viewMode == LOCAL) jsonData = "let projectJson = $jsonData;"

            FileUtils.writeStringToFile(File(options.outputJson), jsonData, UTF8)
            logger.log("generate output", "save macros as JSON: ${options.outputJson}")
        } catch (e: IOException) {
            logger.error("Error writing macro(s) to ${options.outputJson}: ${e.message}")
            exit(WRITE_FILE)
        }

        val outputFile = writeOutputHtml(File(options.directory), json)
        if (options.verbose) ExecUtils.openFile(outputFile.absolutePath)
    }

    private fun writeOutputHtml(projectHome: File, json: JsonObject): File {
        // resource could be bundled in JAR, so we can't assume file-copy will always work..
        val sourcePath = ResourceUtils.getResourceFilePath(LOCAL_HTML_RESOURCE) ?: return projectHome
        val source = File(sourcePath)
        val destination = File(projectHome.absolutePath + separator + source.name)
        val projectId = json.get("name").asString

        try {
            val html = StringUtils.replace(ResourceUtils.loadResource(LOCAL_HTML_RESOURCE),
                                           "<title></title>",
                                           "<title>Project Inspector for Project $projectId</title>")
            FileUtils.writeStringToFile(destination, html, UTF8)
            logger.log("generate output", "created output: $destination")
        } catch (e: IOException) {
            logger.error("Error updating HTML in $destination: ${e.message}")
            exit(WRITE_FILE)
        }

        return destination
    }
}