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

package org.nexial.core.tools

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.proc.RuntimeUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS
import org.nexial.core.plugins.javaui.JavaUIConst.SystemVariable
import org.nexial.core.plugins.javaui.JubulaHelper
import org.nexial.core.plugins.javaui.JubulaUtils
import org.nexial.core.tools.CliUtils.newArgOption
import org.nexial.core.utils.ConsoleUtils
import java.io.File.separator
import kotlin.system.exitProcess

/**
 * CLI utility to start AUT for component mapping purpose
 */
class JavaUIMapping {

    companion object {
        private const val scriptName = "nexial-javaui-mapping"
        private const val DEF_AGENT = "embedded"
        private val localhostAliases = listOf("localhost", "127.0.0.1", "0.0.0.0")

        private const val OPT_NAME = "[REQUIRED] 1. The name of the target executable (AUT)"
        private const val OPT_TYPE = "[REQUIRED] 2. The application type: swing, swt, rcp, javafx, gef"
        private const val OPT_LOCATION = "[REQUIRED] 3. The location of the executable (AUT)"
        private const val OPT_EXEC = "[REQUIRED] 4. The name of the executable (can be batch file or shell script"
        private const val OPT_PARAMS = "[optional] 5. Additional parameters to launch the target executable (AUT)"
        private const val OPT_AGENT = "[optional] 6. The AUT agent to use. Format: host:port or embedded"
        private const val OPT_JUBULA_HOME = "[optional] 7. Jubula installation directory (AUT Agent)"

        private const val MSG_MISSING_JUBULA_HOME = "unable to resolve Jubula installation directory either via " +
                                                    "environment variable JUBULA_HOME nor via commandline option (-h)"
        private const val MSG_INVALID_JUBULA_HOME = "specified JUBULA_HOME is not a valid directory"
        private const val MSG_INVALID_AGENT_CONFIG = "invalid AUT agent (-a) configured specified: "
        private const val MSG_INVALID_AGENT_PORT = "invalid AUT agent port (must be higher than 1024): "
        private const val MSG_REMOTE_AGENT_WARNING = "Be sure that the AUT agent has been started on "
        private const val MSG_USE_EMBEDDED_AGENT = "Using embedded AUT agent..."
        private const val MSG_INVALID_APP_TYPE = "Application type is not yet supported: "
        private const val MSG_INVALID_APP_LOCATION = "Invalid application directory specified: "
        private const val MSG_INVALID_APP = "Invalid application location specified: "
        private const val MSG_START_MAPPING = "Right-click on the AUT Agent icon on the taskbar, then select " +
                                              "Object Mapping | Start | AUT to start mapping."

        fun deriveCommandLine(args: Array<String>): CommandLine {
            val cliOptions = Options()
            cliOptions.addOption(newArgOption("n", "name", OPT_NAME, true))
            cliOptions.addOption(newArgOption("t", "type", OPT_TYPE, true))
            cliOptions.addOption(newArgOption("l", "location", OPT_LOCATION, true))
            cliOptions.addOption(newArgOption("e", "executable", OPT_EXEC, true))
            cliOptions.addOption(newArgOption("p", "parameters", OPT_PARAMS, false))
            cliOptions.addOption(newArgOption("a", "agent", OPT_AGENT, false))
            cliOptions.addOption(newArgOption("h", "jubulaHome", OPT_JUBULA_HOME, false))

            val programExt = if (IS_OS_WINDOWS) ".cmd" else ".sh"
            val formatter = HelpFormatter()
            formatter.width = 115
            formatter.optionComparator = compareBy { it -> it.description }
            val cmd = CliUtils.getCommandLine("$scriptName$programExt", args, cliOptions, formatter)
            if (cmd == null) {
                ConsoleUtils.error("unable to proceed... exiting")
                exitProcess(RC_BAD_CLI_ARGS)
            }

            return cmd
        }

        @JvmStatic
        fun main(args: Array<String>) {
            // 1. read cli options
            val cmd = deriveCommandLine(args)
            val name = cmd.getOptionValue("n")

            val location = cmd.getOptionValue("l")
            if (!FileUtil.isDirectoryReadable(location)) {
                ConsoleUtils.error(MSG_INVALID_APP_LOCATION + location)
                exitProcess(RC_BAD_CLI_ARGS)
            }

            val exe = cmd.getOptionValue("e")
            val exeFqn = StringUtils.appendIfMissing(location, separator) + exe
            if (!FileUtil.isFileExecutable(exeFqn)) {
                ConsoleUtils.error(MSG_INVALID_APP + exeFqn)
                exitProcess(RC_BAD_CLI_ARGS)
            }

            val exeArgs = RuntimeUtils.formatCommandLine(cmd.getOptionValue("p"))

            // 2. resolve optional cli options
            val jubulaHome = cmd.getOptionValue("h", System.getenv("JUBULA_HOME"))
            if (StringUtils.isBlank(jubulaHome)) {
                ConsoleUtils.error(MSG_MISSING_JUBULA_HOME)
                exitProcess(RC_BAD_CLI_ARGS)
            }
            if (!FileUtil.isDirectoryReadable(jubulaHome)) {
                ConsoleUtils.error(MSG_INVALID_JUBULA_HOME)
                exitProcess(RC_BAD_CLI_ARGS)
            }

            // 3 start agent (if needed)
            handleAutAgent(cmd.getOptionValue("a", DEF_AGENT))

            // 6. exit /w 0
            val jubula = when (val appType = cmd.getOptionValue("t")) {
                "swing" -> JubulaHelper.swing()
                "swt"   -> JubulaHelper.swt()
                "rcp"   -> JubulaHelper.rcp()
                else    -> throw IllegalArgumentException(MSG_INVALID_APP_TYPE + appType)
            }

            jubula.registerAut(name, location, exe, *exeArgs)
            jubula.startAut(name)
            println("AUT ($exeFqn) started. " + MSG_START_MAPPING)
        }

        private fun handleAutAgent(agentOption: String) {
            if (agentOption == DEF_AGENT) {
                ConsoleUtils.log(MSG_USE_EMBEDDED_AGENT)
                return
            }

            val agentConfigs = agentOption.split(":")
            if (agentConfigs.size != 2) {
                ConsoleUtils.error(MSG_INVALID_AGENT_CONFIG + agentOption)
                exitProcess(RC_BAD_CLI_ARGS)
            }

            val agentHost = agentConfigs[0]
            val agentPort = agentConfigs[1]
            if (!NumberUtils.isDigits(agentPort)) {
                ConsoleUtils.error(MSG_INVALID_AGENT_PORT + agentPort)
                exitProcess(RC_BAD_CLI_ARGS)
            }

            val port = NumberUtils.toInt(agentPort)
            if (port < 1024) {
                ConsoleUtils.error(MSG_INVALID_AGENT_PORT + agentPort)
                exitProcess(RC_BAD_CLI_ARGS)
            }

            if (localhostAliases.contains(StringUtils.lowerCase(agentHost)))
                JubulaUtils.startLocalAgent(port)
            else
                ConsoleUtils.log("$MSG_REMOTE_AGENT_WARNING$agentPort")

            System.setProperty(SystemVariable.agent, agentOption)
        }
    }
}