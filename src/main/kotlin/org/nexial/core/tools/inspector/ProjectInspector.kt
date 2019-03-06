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

import com.coremedia.iso.Hex
import com.google.gson.JsonObject
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS
import org.nexial.core.tools.CliConst.OPT_VERBOSE
import org.nexial.core.tools.CliUtils.getCommandLine
import org.nexial.core.tools.CliUtils.newArgOption
import org.nexial.core.tools.inspector.InspectorConst.GSON
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.BAD_DIRECTORY
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.MISSING_DIRECTORY
import org.nexial.core.tools.inspector.InspectorConst.exit
import org.nexial.core.tools.inspector.InspectorViewMode.LOCAL
import java.io.File
import java.security.MessageDigest
import java.util.*
import java.util.stream.Collectors

object ProjectInspector {
    private val md5 = MessageDigest.getInstance("MD5")
    private val RESOURCES: Properties =
            ResourceUtils.loadProperties("org/nexial/core/tools/inspector/resources.properties")

    @JvmStatic
    fun main(args: Array<String>) {

        val cmd = deriveCommandLine(args)
        val options = deriveInspectorOptions(cmd)

        val logger = InspectorLogger(options.verbose)

        val json = JsonObject()
        val projectInfo = ProjectInfoGenerator(options, logger).generate()
        json.addProperty("name", projectInfo.projectId)
        json.addProperty("scanTime", System.currentTimeMillis())
        json.add("macros", GSON.toJsonTree(MacroDocGenerator(options, logger).generate()))
        json.add("dataVariables", GSON.toJsonTree(DataDocGenerator(options, logger).generate()))

        if (options.advices.isNotEmpty()) projectInfo.advices.addAll(options.advices)

        if (projectInfo.advices.isNotEmpty()) json.add("advices", GSON.toJsonTree(projectInfo.advices).asJsonArray)

        InspectorOutput(logger).output(options, json)
    }

    internal fun getMessage(key: String): String = RESOURCES.getProperty(key)

    internal fun getMessage(key: String, replacement: Pair<String, String>?): String {
        val message = RESOURCES.getProperty(key)
        return if (StringUtils.isBlank(message) || replacement == null) message
        else
            message.replace("{${replacement.first}}", replacement.second)
    }

    internal fun getMessage(key: String, replacements: List<Pair<String, String>>?): String {
        val message = RESOURCES.getProperty(key)
        return if (StringUtils.isBlank(message) || replacements == null || replacements.isEmpty()) message
        else {
            var replaced = message
            replacements.forEach { replaced = replaced.replace("{${it.first}}", it.second) }
            return replaced
        }
    }

    internal fun filterFiles(directory: File, extensions: Array<String>, filter: (file: File) -> Boolean): List<File> =
            FileUtils.listFiles(directory, extensions, true)
                .stream()
                .filter { filter(it) }
                .collect(Collectors.toList())

    internal fun resolveRelativePath(project: File, file: File): String =
            StringUtils.removeStart(
                    StringUtils.replace(
                            StringUtils.remove(file.parentFile.absolutePath, project.absolutePath),
                            "\\", "/"),
                    "/")

    internal fun generateMD5(subject: File): String {
        // create digest base => filename + lastmod + size
        val base = StringUtils.leftPad(subject.name, 50, "\\") +
                   StringUtils.leftPad("${subject.lastModified()}", 16, "0") +
                   StringUtils.leftPad("${subject.length()}", 16, "0")

        // applying md5
        md5.reset()
        md5.update(base.toByteArray())
        return Hex.encodeHex(md5.digest())
    }

    private fun deriveCommandLine(args: Array<String>): CommandLine {
        val cmdOptions = Options()
        cmdOptions.addOption(OPT_VERBOSE)
        cmdOptions.addOption(newArgOption("t", "target", "[REQUIRED] The project directory to scan", true))
        cmdOptions.addOption(newArgOption("m", "viewMode",
                                          "Specify how output will be viewed (local or remote). Default is local",
                                          false))

        val programExt = if (IS_OS_WINDOWS) ".cmd" else ".sh"
        val cmd = getCommandLine("nexial-project-inspector$programExt", args, cmdOptions)
        if (cmd == null) {
            println()
            InspectorLogger.error("Unable to proceed, exiting...")
            println()
            exit(RC_BAD_CLI_ARGS)
        }

        return cmd
    }

    private fun deriveInspectorOptions(cmd: CommandLine): InspectorOptions {

        val projectHome = cmd.getOptionValue("t")
        if (StringUtils.isBlank(projectHome)) {
            println()
            InspectorLogger.error("Missing 'target' parameter")
            println()
            exit(MISSING_DIRECTORY)
        }

        if (!FileUtil.isDirectoryReadable(projectHome)) {
            println()
            InspectorLogger.error("Invalid 'target' directory: $projectHome")
            println()
            exit(BAD_DIRECTORY)
        }

        val viewModeInput = cmd.getOptionValue("m")
        val viewMode =
                if (StringUtils.isBlank(viewModeInput)) LOCAL
                else InspectorViewMode.valueOf(StringUtils.upperCase(viewModeInput))

        return InspectorOptions(directory = projectHome,
                                viewMode = viewMode,
                                verbose = cmd.hasOption("v"))
    }
}
