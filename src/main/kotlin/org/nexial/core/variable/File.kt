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

package org.nexial.core.variable

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.DEF_FILE_ENCODING
import org.nexial.core.NexialConst.Data.TEXT_DELIM
import org.nexial.core.SystemVariables.getDefault
import org.nexial.core.utils.ConsoleUtils
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

/**
 * Built-in function for file and file related operations. This would simply the need to saving the result of an IO
 * operation and then perform operation on that result, such as `io.readFile(var1...)`,
 * `json.assertWellformed(${var1})`.  Instead one should be able to use this function in place of where the
 * intermediary variable is (or would be) and save one or more intermediary steps.
 */
class File {

    @Throws(IOException::class)
    fun content(file: String): String {
        return FileUtils.readFileToString(toFile(file) ?: return "", DEF_FILE_ENCODING)
    }

    @Throws(IOException::class)
    fun asList(file: String): String {
        toFile(file) ?: return ""

        val context = ExecutionThread.get()
        val delim = if (context == null) getDefault(TEXT_DELIM) else context.textDelim

        return StringUtils.replace(StringUtils.remove(content(file), "\r"), "\n", delim)
    }

    fun lastmod(file: String): String {
        return (toFile(file) ?: return "-1").lastModified().toString()
    }

    fun size(file: String): String {
        return (toFile(file) ?: return "-1").length().toString()
    }

    @Throws(IOException::class)
    fun copy(file: String, target: String): String {
        val f = toFile(file)
        val t = toNewFile(target)
        return if (f != null && t != null) {
            FileUtils.copyFile(f, t)
            t.absolutePath
        } else {
            ""
        }
    }

    @Throws(IOException::class)
    fun move(file: String, target: String): String {
        val f = toFile(file)
        val t = toNewFile(target)
        return if (f != null && t != null) {
            FileUtils.moveFile(f, t)
            t.absolutePath
        } else {
            ""
        }
    }

    fun delete(file: String): String {
        val f = toFile(file)
        return if (f != null) {
            FileUtils.deleteQuietly(f)
            file
        } else {
            ""
        }
    }

    @Throws(IOException::class)
    fun overwrite(file: String, content: String): String {
        val f = toFile(file) ?: toNewFile(file)
        return if (f != null) {
            FileUtils.writeStringToFile(f, content, DEF_FILE_ENCODING)
            file
        } else {
            ""
        }
    }

    @Throws(IOException::class)
    fun append(file: String, content: String): String {
        val f = toFile(file)
        return if (f != null) {
            FileUtils.writeStringToFile(f, content, DEF_FILE_ENCODING, true)
            file
        } else {
            ""
        }
    }

    @Throws(IOException::class)
    fun prepend(file: String, content: String): String {
        val f = toFile(file)
        return if (f != null) {
            val targetFile = java.io.File("$file.new")

            FileWriter(targetFile).use { targetWriter ->
                BufferedReader(FileReader(f)).use { sourceReader ->

                    targetWriter.write(content)

                    var buffer = CharArray(2048)
                    var read = sourceReader.read(buffer)
                    while (read != -1) {
                        targetWriter.write(buffer, 0, read)
                        buffer = CharArray(2048)
                        read = sourceReader.read(buffer)
                    }
                }
            }

            FileUtils.deleteQuietly(f)
            FileUtils.moveFile(targetFile, f)
            file
        } else {
            ""
        }
    }

    fun name(file: String): String {
        return (toFile(file) ?: return "").name
    }

    fun dir(file: String): String {
        return (toFile(file) ?: return "").parentFile.absolutePath
    }

    protected fun init() {
    }

    companion object {
        private val HEADER = "[file]: "

        private fun toFile(file: String): java.io.File? {
            if (StringUtils.isBlank(file)) {
                ConsoleUtils.log(HEADER + "empty/blank file name")
                return null
            }
            if (!FileUtil.isFileReadable(file)) {
                ConsoleUtils.log(HEADER + "unreadable file - " + file)
                return null
            }
            return java.io.File(file)
        }

        private fun toNewFile(file: String): java.io.File? {
            if (StringUtils.isBlank(file)) {
                ConsoleUtils.log(HEADER + "empty/blank file name")
                return null
            }

            val f = java.io.File(file)
            if (!f.parentFile.mkdirs()) {
                ConsoleUtils.log(HEADER + "unable to create parent directory for " + file)
            }

            return f
        }
    }
}
