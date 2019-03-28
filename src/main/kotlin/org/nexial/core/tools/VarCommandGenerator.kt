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
 *
 */

package org.nexial.core.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.FileUtil.addToJar
import org.nexial.commons.utils.ResourceUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.*
import org.nexial.core.NexialConst.Data.TEXT_DELIM
import org.nexial.core.NexialConst.ExitStatus.RC_FILE_GEN_FAILED
import org.nexial.core.NexialConst.Project.COMMAND_JSON_FILE
import org.nexial.core.NexialConst.Project.JSON_FOLDER
import org.nexial.core.SystemVariables.getDefault
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

object VarCommandGenerator {
    private const val VAR_CMD_JSON = "nexial.var.command.json"
    private const val JAR_NAME = "nexial-json.jar"
    private val jarDir = TEMP + "nexial-json-Jar/"
    private val sourceJar = File(jarDir + JAR_NAME);
    private val variables = mutableListOf("var", "saveVar", "profile", "db", "config")

    @JvmStatic
    fun generateVarCommands() {
        val varCommands = deriveVarCommands()

        val varCommandJson = File(JSON_FOLDER + VAR_CMD_JSON)
        FileUtils.write(varCommandJson, GSON.toJson(varCommands), DEF_FILE_ENCODING)
        if (!FileUtil.isFileReadable(varCommandJson.absolutePath, 1024)) {
            System.err.println("Generated var command file is not readable or is invalid: $varCommandJson")
            System.exit(RC_FILE_GEN_FAILED)
        }

        buildJar()
    }

    private fun deriveVarCommands(): MutableMap<String, MutableList<Int>> {
        if (!FileUtil.isFileReadable(COMMAND_JSON_FILE.absolutePath, 1024)) {
            System.err.println("Generated command meta file is not readable or is invalid: $COMMAND_JSON_FILE")
            System.exit(RC_FILE_GEN_FAILED)
        }

        val commandJson = FileUtils.readFileToString(COMMAND_JSON_FILE, DEF_FILE_ENCODING)
        println("Scanning command json file: ${COMMAND_JSON_FILE.absolutePath}")

        val varCommands = mutableMapOf<String, MutableList<Int>>()
        val commands = GSON.fromJson(commandJson, JsonObject::class.java).getAsJsonArray("commands")

        commands.forEach { command ->
            val target = command.asJsonObject["name"].asString

            command.asJsonObject["commands"].asJsonArray.forEach { commandName ->
                val cmd = commandName.asString
                val commandFqn = "$target.$cmd"
                val varIndices = mutableListOf<Int>()
                val params = TextUtils.toList(StringUtils.substringBetween(cmd, "(", ")"), getDefault(TEXT_DELIM), true)

                variables.forEach { if (params.contains(it)) varIndices.add(params.indexOf(it)) }
                varIndices.sort()
                if (varIndices.isNotEmpty()) varCommands.putIfAbsent(commandFqn, varIndices)
            }
        }
        return varCommands
    }

    private fun buildJar() {
        // create temp folder for jar
        val dir = File(jarDir)
        FileUtils.deleteQuietly(dir)
        dir.mkdirs()

        if (sourceJar.exists()) sourceJar.delete()

        //create jar file
        val manifest = Manifest()
        manifest.mainAttributes[MANIFEST_VERSION] = "1.0"
        val target = JarOutputStream(FileOutputStream(sourceJar), manifest)
        addToJar(File(JSON_FOLDER), target, JSON_FOLDER)
        target.close()

        // check for NEXIAL_LIB
        var libDir = System.getenv(ENV_NEXIAL_LIB)
        if (StringUtils.isEmpty(libDir)) {
            val nexialHome = System.getenv(ENV_NEXIAL_HOME)
            if (StringUtils.isEmpty(nexialHome)) {
                ConsoleUtils.log("NEXIAL_LIB and NEXIAL_HOME is missing: $MSG_CHECK_SUPPORT")
                System.exit(-1)
            } else {
                libDir = nexialHome + separator + "lib"
            }
        }

        // move jar file to lib folder
        val targetJar = File("$libDir/$JAR_NAME")
        FileUtils.moveFile(sourceJar, targetJar)

        // delete json file and jar file
        FileUtils.deleteQuietly(File(JSON_FOLDER))
        FileUtils.deleteQuietly(sourceJar)
    }

    fun retriveVarCmds(): MutableMap<String, IntArray>? {
        val varCmdFile = ResourceUtils.loadResource("/$VAR_CMD_JSON") ?: return null
        val varCmdJson = GSON.fromJson(varCmdFile, JsonObject::class.java)
        val varCmds = mutableMapOf<String, IntArray>()
        varCmdJson.keySet().forEach { command ->
            val array = varCmdJson[command] as JsonArray
            val intArray = IntArray(array.count())
            for (i in 0 until array.count()) {
                intArray[i] = array[i].asInt
            }
            varCmds.putIfAbsent(command, intArray)
        }
        return varCmds;
    }
}


