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

package org.nexial.core.plugins.xml

import org.apache.commons.lang3.StringUtils
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.Serializable
import java.util.*
import kotlin.math.max

class SchemaError(val severity: String, val line: Int, val column: Int, var message: String?) : Serializable {
    var schemaMismatched = false

    init {
        schemaMismatched = StringUtils.contains(message, "Cannot find the declaration of element")
    }

    companion object {

        @JvmStatic
        fun toSchemaError(severity: String, exception: SAXParseException, lineOffset: Int) =
            SchemaError(severity = severity,
                        line = max(exception.lineNumber + lineOffset, 0),
                        column = exception.columnNumber,
                        message = exception.message)
    }
}

class SchemaErrorCollector(private var lineOffset: Int = 0) : ErrorHandler {
    internal val errors: MutableList<SchemaError> = ArrayList()
    private var hasErrors: Boolean = false

    override fun warning(exception: SAXParseException) {
        errors.add(SchemaError.toSchemaError("warning", exception, lineOffset))
    }

    override fun error(exception: SAXParseException) {
        hasErrors = true
        errors.add(SchemaError.toSchemaError("ERROR", exception, lineOffset))
    }

    override fun fatalError(exception: SAXParseException) {
        hasErrors = true
        errors.add(SchemaError.toSchemaError("FATAL", exception, lineOffset))
    }

    fun hasSchemaMismatched() = errors.count { it.schemaMismatched } > 0

    fun getErrors() = errors

    fun hasErrors() = hasErrors
}

