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

import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst
import org.nexial.core.plugins.desktop.DesktopConst
import java.util.*

class SystemVarListing {
    private val targets = arrayOf(NexialConst::class.java, DesktopConst::class.java)
    private val newNs = "nexial"
    private val oldNs = "sentry"
    private val pairSep = ";"

    private fun collectVars(target: Class<*>): SortedMap<String, String> {
        val vars = TreeMap<String, String>()

        target.fields
            .filter { field -> field.type == String::class.java }
            .forEach { field ->
                try {
                    val fieldValue: String = field.get(null).toString()
                    if (StringUtils.startsWith(fieldValue, "$newNs.") && !StringUtils.endsWith(fieldValue, ".")) {
                        vars[StringUtils.replace(fieldValue, "$newNs.", "$oldNs.")] = fieldValue
                    }
                } catch (e: IllegalAccessException) {
                    // forget this one...
                }
            }

        target.classes.forEach { innerClass -> vars.putAll(collectVars(innerClass)) }

        return vars
    }

    fun collectVars(): String {
        val systemVars = addSpecialCases(TreeMap())

        targets.forEach { target -> systemVars.putAll(collectVars(target)) }

        val output = StringBuilder()
        systemVars.forEach { (oldVar, newVar) -> output.append("$oldVar=$newVar$pairSep") }

        return output.toString()
    }

    private fun addSpecialCases(vars: SortedMap<String, String>): SortedMap<String, String> {
        vars["sentry.dao.*"] = "nexial.dao.*"
        vars["sentry.pdfFormStrategy.*"] = "nexial.pdfFormStrategy.*"
        vars["sentry.scenarioRef.*"] = "nexial.scenarioRef.*"
        vars["sentry.script.*"] = "nexial.scriptRef.*"
        vars["sentry.ws.header.*"] = "nexial.ws.header.*"

        return vars
    }
}

fun main() {
    println(SystemVarListing().collectVars())
}
