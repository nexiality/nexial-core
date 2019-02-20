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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.charset.Charset
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object InspectorConst {
    val GSON: Gson = GsonBuilder().setLenient().setPrettyPrinting().create()
    val UTF8: Charset = Charset.forName("UTF-8")
    val LOG_DATE_FORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S")

    const val PROJECT_ID = ".meta/project.id"
    const val LOCAL_HTML_RESOURCE = "/org/nexial/core/reports/project-inspector-local.html"
    const val MACRO_DESCRIPTION = "macro.description()"
    const val MACRO_EXPECTS = "macro.expects(var,default)"
    const val MACRO_PRODUCES = "macro.produces(var,value)"
    val MACRO_CMDS: MutableList<String> = Arrays.asList(MACRO_DESCRIPTION, MACRO_EXPECTS, MACRO_PRODUCES)

    object ReturnCode {
        const val MISSING_DIRECTORY = -2
        const val MISSING_OUTPUT = -3
        const val BAD_DIRECTORY = -4
        const val BAD_OUTPUT = -5
        const val WRITE_FILE = -6
        const val READ_JSON = -7
    }

    object Advice {
        const val MISSING_META_FILE = "No <code>.meta/project.id</code> file found in the specified project " +
                                      "directory. While this file is not necessary, it helps to keep everyone " +
                                      "in sync in terms of project metadata. For more details, check out " +
                                      "<a href=\"http://nexiality.github.io/documentation/userguide/UnderstandingProjectStructure#project.id\">Project Structure - project.id</a>."
        const val MISSING_MACRO_DESC = "One or more macro is found without description. It is recommended for each " +
                                       "macro implementation to have some usage documentation, along with the data " +
                                       "variable(s) it expects prior to invocation and the data variable(s) it " +
                                       "produces after invocation. For more details, please check out " +
                                       "<a href=\"http://nexiality.github.io/documentation/commands/macro/\">Macro Commands</a>."
    }

    fun exit(returnCode: Int) = System.exit(returnCode)
}
