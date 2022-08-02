/*
 * Copyright 2012-2018 the original author or authors.
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

package org.nexial.core.variable

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.core.ExecutionThread
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.OutputResolver
import java.io.IOException
import java.lang.reflect.Method
import java.util.*

class BinaryTransformer : Transformer<BinaryDataType>() {

    private val functionToParam: MutableMap<String, Int> = discoverFunctions(BinaryTransformer::class.java)
    private val functions = toFunctionMap(functionToParam, BinaryTransformer::class.java, BinaryDataType::class.java)

    override fun listSupportedFunctions(): MutableMap<String, Int> = functionToParam

    override fun listSupportedMethods(): MutableMap<String, Method> = functions

    fun save(data: BinaryDataType, path: String): BinaryDataType {
        if (data.getValue() == null) return data

        require(!StringUtils.isBlank(path)) { "path is empty/blank" }

        val target = FileUtil.makeParentDir(path)
        return try {
            FileUtils.writeByteArrayToFile(target, data.getValue(), false)
            ConsoleUtils.log("content saved to '$path'")
            data
        } catch (e: IOException) {
            throw IllegalArgumentException("Unable to write to " + path + ": " + e.message, e)
        }
    }

    fun saveEncoded(data: BinaryDataType, path: String, append: String): BinaryDataType {
        if (data.getValue() == null) return data
        require(!StringUtils.isBlank(path)) { "path is empty/blank" }

        FileUtil.writeBinaryFile(FileUtil.makeParentDir(path).absolutePath,
                                 BooleanUtils.toBoolean(append),
                                 data.getValue())
        return data
    }

    fun base64encode(data: BinaryDataType) = TextDataType(Base64.getEncoder().encodeToString(data.getValue()))

    fun size(data: BinaryDataType) = NumberDataType(if (data.value == null) "0" else data.value.size.toString())

    fun loadBase64(data: BinaryDataType, file: String) = loadExternal(data, file, false)

    fun loadBinary(data: BinaryDataType, file: String) = loadExternal(data, file, true)

    override fun text(data: ExpressionDataType<*>?) = TextDataType(data?.textValue)

    fun text(data: BinaryDataType) = TextDataType(data.textValue)

    private fun loadExternal(data: BinaryDataType, file: String, binary: Boolean): BinaryDataType {
        if (!FileUtil.isFileReadable(file)) {
            ConsoleUtils.error("Unreadable/invalid file: $file")
            return data
        }

        val resolver = OutputResolver(data = file,
                                      context = ExecutionThread.get(),
                                      resolveAsFile = true,
                                      resolveUrl = true,
                                      replaceTokens = false,
                                      asBinary = binary,
                                      compact = false)
        data.value = if (binary) resolver.bytes else Base64.getDecoder().decode(resolver.content)
        data.textValue = String(data.value)
        return data
    }
}
