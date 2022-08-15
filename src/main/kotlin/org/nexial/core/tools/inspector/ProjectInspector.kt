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
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.ResourceUtils
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.Project.*
import org.nexial.core.tools.CliConst.OPT_VERBOSE
import org.nexial.core.tools.CliUtils.getCommandLine
import org.nexial.core.tools.CliUtils.newArgOption
import org.nexial.core.tools.inspector.InspectorConst.GSON
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.BAD_DIRECTORY
import org.nexial.core.tools.inspector.InspectorConst.ReturnCode.MISSING_DIRECTORY
import org.nexial.core.tools.inspector.InspectorConst.exit
import org.nexial.core.tools.inspector.InspectorViewMode.LOCAL
import org.nexial.core.tools.inspector.ProjectInspector.resolveRelativePath
import java.io.File
import java.io.File.separator
import java.security.MessageDigest
import java.util.*
import java.util.stream.Collectors

object ProjectInspector {
    private val RESOURCES: Properties =
            ResourceUtils.loadProperties("org/nexial/core/tools/inspector/resources.properties")

    @JvmStatic
    fun main(args: Array<String>) {
        val startTime = System.currentTimeMillis()

        val cmd = deriveCommandLine(args)
        val options = deriveInspectorOptions(cmd)
        val logger = InspectorLogger(options.verbose)
        val testProject = options.project

        val json = JsonObject()
        json.addProperty("nexialHome", testProject.nexialHome)
        json.addProperty("name", testProject.name)
        json.addProperty("scanProjectHome", testProject.projectHome)
        json.addProperty("scanTime", System.currentTimeMillis())
        if (options.advices.isNotEmpty()) json.add("advices", GSON.toJsonTree(options.advices).asJsonArray)
        json.add("macros", GSON.toJsonTree(MacroDocGenerator(options, logger).generate()))
        json.add("dataVariables", GSON.toJsonTree(DataDocGenerator(options, logger).generate()))

        logger.title("PROCESSING OUTPUT", "generate output")
        InspectorOutput(logger).output(options, json)
        logger.log("execution time", "${System.currentTimeMillis() - startTime} ms")
    }

    internal fun getMessage(key: String): String = RESOURCES.getProperty(key)

    internal fun getMessage(key: String, replacement: Pair<String, String>?): String {
        val message = RESOURCES.getProperty(key)
        return if (StringUtils.isBlank(message) || replacement == null)
            message
        else
            message.replace("{${replacement.first}}", replacement.second)
    }

    internal fun getMessage(key: String, replacements: List<Pair<String, String>>?): String {
        val message = RESOURCES.getProperty(key)
        return if (StringUtils.isBlank(message) || replacements.isNullOrEmpty())
            message
        else {
            var replaced = message
            replacements.forEach { replaced = replaced.replace("{${it.first}}", it.second) }
            return replaced
        }
    }

    internal fun filterFiles(directory: File, extensions: Array<String>, filter: (file: File) -> Boolean) =
            FileUtils.listFiles(directory, extensions, true).stream().filter { filter(it) }.collect(Collectors.toList())

    internal fun resolveRelativePath(project: File, file: File): String = StringUtils.removeStart(
            StringUtils.replace(StringUtils.remove(file.parentFile.absolutePath, project.absolutePath), "\\", "/"), "/")

    private fun deriveCommandLine(args: Array<String>): CommandLine {
        val cmdOptions = Options()
        cmdOptions.addOption(OPT_VERBOSE)
        cmdOptions.addOption(newArgOption("t", "target", "[REQUIRED] The project directory to scan", true))
        cmdOptions.addOption(newArgOption("m", "viewMode",
                                          "Specify how output will be viewed (local or remote). Default is local",
                                          false))
        return getCommandLine("nexial-project-inspector$BATCH_EXT", args, cmdOptions)
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
        val viewMode = if (StringUtils.isBlank(viewModeInput)) LOCAL
        else InspectorViewMode.valueOf(StringUtils.upperCase(viewModeInput))

        return InspectorOptions(directory = projectHome, viewMode = viewMode, verbose = cmd.hasOption("v"))
    }
}

class CacheHelper<T>(val options: InspectorOptions, val logger: InspectorLogger) where T : Any {

    internal fun resolveCacheFile(target: File) = if (!options.useCache) {
        null
    } else {
        val cachePath = options.cacheHome + resolveRelativePath(File(options.directory), target) + separator
        File(cachePath).mkdirs()
        File("$cachePath${target.name}.${generateMD5(target)}.json")
    }

    internal fun isUsableCacheFile(cacheFile: File?) = options.useCache && FileUtil.isFileReadable(cacheFile, 512)

    inline fun <reified T> readCache(cacheFile: File?) = if (cacheFile != null) {
        logger.log(deriveCacheName(cacheFile), "reading from cache...")
        GSON.fromJson(FileUtils.readFileToString(cacheFile, DEF_FILE_ENCODING), T::class.java)
    } else null

    internal fun saveCache(cacheObject: Any, cacheFile: File?) {
        if (cacheFile != null && options.useCache) {
            logger.log(deriveCacheName(cacheFile), "updating cache...")
            FileUtils.write(cacheFile, GSON.toJson(cacheObject), DEF_FILE_ENCODING)
        }
    }

    fun deriveCacheName(cacheFile: File): String {
        val name = cacheFile.absolutePath.substringAfter(options.project.name + separator + DEF_REL_LOC_ARTIFACT)
        return if (name.contains(SCRIPT_FILE_EXT)) name.substringBefore(SCRIPT_FILE_EXT) + SCRIPT_FILE_EXT else name
    }

    internal fun expireOutdatedCache(exclude: File?) {
        if (!options.useCache || exclude == null) return

        val cacheName = exclude.name.substringBefore(".")
        val ext = "." + exclude.name.substringAfterLast(".")
        fun accept(fileName: String) =
                fileName.startsWith(cacheName) && fileName.endsWith(ext) && fileName != exclude.name

        FileUtils.listFiles(exclude.parentFile,
                            object : IOFileFilter {
                                override fun accept(file: File?) = file != null && accept(file.name)
                                override fun accept(dir: File?, name: String?) = name != null && accept(name)
                            },
                            object : IOFileFilter {
                                override fun accept(file: File?) = true
                                override fun accept(dir: File?, name: String?) = true
                            }).parallelStream().forEach { FileUtils.deleteQuietly(it) }
    }

    companion object {
        private val md5 = MessageDigest.getInstance("MD5")

        /**
         * generate MD5 representation of a file based on the first 64 character of the file name, the file's last
         * modified epoch time and file size. Note that we can artificially inject "versioning" via altering the MD5
         * strategy. For example, we can change from<br/>
         * <code>file-name + last-mod + file-size</code><br/>
         * to<br/>
         * <code>file-name + last-mod + file-size + version</code><br/>
         *
         * As such we can expire cache files generated by older builds automatically.
         *
         * @param subject File
         * @return String
         */
        internal fun generateMD5(subject: File): String {
            // create digest base => filename + lastmod + size
            val base = StringUtils.leftPad(subject.name, 64, "\\") +
                       StringUtils.leftPad("${subject.lastModified()}", 16, "0") +
                       StringUtils.leftPad("${subject.length()}", 16, "0")

            // applying md5
            md5.reset()
            md5.update(base.toByteArray())
            return Hex.encodeHex(md5.digest())
        }
    }
}