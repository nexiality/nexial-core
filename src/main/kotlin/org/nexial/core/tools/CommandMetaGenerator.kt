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

package org.nexial.core.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.COMMAND_DISCOVERY_MODE
import org.nexial.core.NexialConst.ExitStatus.RC_BAD_BATCH_FILE
import org.nexial.core.NexialConst.ExitStatus.RC_FILE_GEN_FAILED
import org.nexial.core.NexialConst.Project.*
import org.nexial.core.SystemVariables
import org.nexial.core.spi.NexialExecutionEvent
import org.nexial.core.spi.NexialListenerFactory
import org.nexial.core.tools.CliConst.OPT_PREVIEW
import org.nexial.core.tools.CliConst.OPT_VERBOSE
import org.nexial.core.tools.ProjectToolUtils.log
import org.springframework.context.support.ClassPathXmlApplicationContext
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

object CommandMetaGenerator {
    private const val MIN_JSON_FILE_SIZE = 1024L
    private val variables = listOf("var", "saveVar", "profile", "db", "config")

    private val cmdlineOptions = initCmdlineOptions()
    var verbose = false
    var preview = false

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val generator = newInstance(args)
        generator.generateVarMeta(generator.generateCommandMeta())
        if (!preview) {
            generator.buildJar()
            generator.installJar()
        }
    }

    private fun initCmdlineOptions(): Options {
        val options = Options()
        options.addOption(OPT_VERBOSE)
        options.addOption(OPT_PREVIEW)
        return options
    }

    private fun newInstance(args: Array<String>): CommandMetaGenerator {
        try {
            val cmd: CommandLine = DefaultParser().parse(cmdlineOptions, args)
            val generator = CommandMetaGenerator
            generator.verbose = cmd.hasOption(OPT_VERBOSE.opt)
            generator.preview = cmd.hasOption(OPT_PREVIEW.opt)
            return generator
        } catch (e: ParseException) {
            throw RuntimeException("Unable to parse commandline options: " + e.message)
        }
    }

    /**
     * generate JSON that contains:<ol>
     * <li>command types</li>
     * <li>command listing per command type</li>
     * <li>Excel "name" for each command type, which represent the cell range for the commands of each type</li>
     * </ol>
     *
     * @throws Exception
     */
    @Throws(Exception::class)
    fun generateCommandMeta(): JsonArray {
        // turn on discovery mode so that command discovery can be enabled during spring init
        System.setProperty(COMMAND_DISCOVERY_MODE, "true")

        // load spring context in order to discover all plugins (aka commands)
        val springContext = ClassPathXmlApplicationContext(
            "classpath:" + System.getProperty(OPT_SPRING_XML, DEF_SPRING_XML))
        if (!springContext.containsBean("plugins")) {
            error("Unable to generate command meta file since no 'plugins' are defined")
            System.exit(RC_FILE_GEN_FAILED)
        }

        val plugins = springContext.getBean("plugins", Map::class.java)
        if (MapUtils.isEmpty(plugins)) {
            error("No plugins configured. No command list generated")
            System.exit(RC_FILE_GEN_FAILED)
        }

        verbose("Command types found", plugins.size)

        // allow for extension
        NexialListenerFactory.fireEvent(NexialExecutionEvent.newCommandListingEvent(springContext))

        val discovery = CommandDiscovery.getInstance()

        if (preview) {
            println("\nPREVIEW: printing out discovered commands")
            val commandsJson = discovery.printDiscoveredCommands()
            println(commandsJson)
            return GSON.fromJson(commandsJson, JsonObject::class.java)?.getAsJsonArray("commands")!!
        }

        val commandJson = discovery.persistDiscoveredCommands()
        if (commandJson == null) {
            error("Unable to generate command meta file!")
            System.exit(RC_FILE_GEN_FAILED)
        }

        if (!FileUtil.isFileReadable(commandJson, MIN_JSON_FILE_SIZE)) {
            error("Generated command meta file is not readable or is invalid: $commandJson")
            System.exit(RC_FILE_GEN_FAILED)
        }

        val commands = GSON.fromJson(FileUtils.readFileToString(commandJson, DEF_FILE_ENCODING),
                                     JsonObject::class.java)?.getAsJsonArray("commands")
        if (commands == null || commands.size() < 1) {
            error("Unable to retrieve any command metadata from $commandJson")
            System.exit(RC_FILE_GEN_FAILED)
        }

        verbose("Command metadata created", commandJson)
        return commands!!
    }

    /**
     * generate JSON that contains a list of commands which contains "variable" parameters, and their respective
     * positions thereof.
     */
    fun generateVarMeta(commands: JsonArray): String {
        val varCommands = mutableMapOf<String, MutableList<Int>>()
        commands.forEach { command ->
            val target = command.asJsonObject["name"].asString
            command.asJsonObject["commands"].asJsonArray.forEach { commandName ->
                val cmd = commandName.asString
                val commandFqn = "$target.$cmd"
                val varIndices = mutableListOf<Int>()
                val params = TextUtils.toList(StringUtils.substringBetween(cmd, "(", ")"),
                                              SystemVariables.getDefault(Data.TEXT_DELIM),
                                              true)

                variables.forEach { if (params.contains(it)) varIndices.add(params.indexOf(it)) }
                varIndices.sort()
                if (varIndices.isNotEmpty()) varCommands.putIfAbsent(commandFqn, varIndices)
            }
        }

        val varCommandsJson = GSON.toJson(varCommands)

        if (preview) {
            println("\nPREVIEW: printing out command variable metadata")
            println(varCommandsJson)
            return varCommandsJson
        }

        val varCommandJson = File(JSON_FOLDER + VAR_CMD_JSON)
        FileUtils.write(varCommandJson, varCommandsJson, DEF_FILE_ENCODING)
        if (!FileUtil.isFileReadable(varCommandJson, MIN_JSON_FILE_SIZE)) {
            error("Generated command metadata file is not readable or is invalid: $varCommandJson")
            System.exit(RC_FILE_GEN_FAILED)
        }

        verbose("Variable metadata created", varCommandJson)
        return varCommandsJson
    }

    private fun buildJar() {
        // create temp folder for jar
        val dir = TEMP_JSON_JAR.parentFile
        FileUtils.deleteQuietly(dir)
        dir.mkdirs()

        if (TEMP_JSON_JAR.exists()) TEMP_JSON_JAR.delete()

        //create jar file
        val manifest = Manifest()
        manifest.mainAttributes[MANIFEST_VERSION] = "1.0"
        val target = JarOutputStream(FileOutputStream(TEMP_JSON_JAR), manifest)
        FileUtil.addToJar(File(JSON_FOLDER), target, JSON_FOLDER)
        target.close()
    }

    private fun installJar() {
        // check for NEXIAL_LIB
        var libDir = System.getenv(ENV_NEXIAL_LIB)
        if (StringUtils.isEmpty(libDir)) {
            val nexialHome = System.getenv(ENV_NEXIAL_HOME)
            if (StringUtils.isEmpty(nexialHome)) {
                error("NEXIAL_LIB and NEXIAL_HOME environment variables are missing. $MSG_CHECK_SUPPORT")
                System.exit(RC_BAD_BATCH_FILE)
                return
            }

            libDir = nexialHome + separator + "lib"
        }

        // move jar file to lib folder
        val targetJar = File("$libDir/${TEMP_JSON_JAR.name}")
        FileUtils.deleteQuietly(targetJar)
        FileUtils.moveFile(TEMP_JSON_JAR, targetJar)
        verbose("Jar file created", targetJar)

        // delete json file and jar file
        FileUtils.deleteQuietly(File(JSON_FOLDER))
        FileUtils.deleteQuietly(TEMP_JSON_JAR.parentFile)
    }

    private fun verbose(label: String, data: Any) {
        if (verbose) log(label, data)
    }

    private fun error(message: String) = System.err?.println(message)
}