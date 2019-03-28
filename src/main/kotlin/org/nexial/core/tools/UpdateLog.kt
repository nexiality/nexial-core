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

import org.apache.commons.lang3.StringUtils
import org.nexial.core.tools.ProjectToolUtils.column4LeftMargin
import org.nexial.core.tools.ProjectToolUtils.formatColumns
import org.nexial.core.tools.ProjectToolUtils.reformatLines
import java.io.File

class UpdateLog(val file: String,
                val worksheet: String,
                var position: String,
                var before: String?,
                var after: String?) {

    constructor(file: String, worksheet: String) : this(file, worksheet, "", "", "")

    constructor(file: String, worksheet: String, position: String) :
            this(file = file, worksheet = worksheet, position = position, before = null, after = null)

    constructor(file: File, worksheet: String, position: String) :
            this(file = file.absolutePath, worksheet = worksheet, position = position, before = null, after = null)

    fun copy(): UpdateLog {
        return UpdateLog(file = file, worksheet = worksheet, position = position, before = before, after = after)
    }

    fun setChange(before: String, after: String): UpdateLog {
        this.before = before
        this.after = after
        return this
    }

    override fun toString(): String {
        return formatColumns(
                file,
                worksheet,
                position,
                reformatLines(StringUtils.defaultString(before), StringUtils.defaultString(after), column4LeftMargin))
    }
}
