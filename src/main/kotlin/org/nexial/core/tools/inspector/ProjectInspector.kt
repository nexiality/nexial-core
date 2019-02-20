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
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS
import org.nexial.core.tools.CliConst.OPT_VERBOSE
import org.nexial.core.tools.CliUtils.getCommandLine
import org.nexial.core.tools.CliUtils.newArgOption
import org.nexial.core.tools.inspector.InspectorConst.GSON
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.BAD_DIRECTORY
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.MISSING_DIRECTORY
import org.nexial.core.tools.inspector.InspectorConst.exit
import org.nexial.core.tools.inspector.InspectorViewMode.LOCAL

object ProjectInspector {

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

        InspectorOutput(logger).output(options, json)
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
            InspectorLogger.error("Unable to proceed, exiting...")
            exit(RC_BAD_CLI_ARGS)
        }

        return cmd
    }

    private fun deriveInspectorOptions(cmd: CommandLine): InspectorOptions {

        val projectHome = cmd.getOptionValue("t")
        if (StringUtils.isBlank(projectHome)) {
            InspectorLogger.error("Missing 'target' parameter")
            exit(MISSING_DIRECTORY)
        }

        if (!FileUtil.isDirectoryReadable(projectHome)) {
            InspectorLogger.error("Invalid 'target' directory: $projectHome")
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
