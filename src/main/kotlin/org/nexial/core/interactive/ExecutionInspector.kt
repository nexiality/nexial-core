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

package org.nexial.core.interactive

import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.ConsoleUtils
import java.util.*

class ExecutionInspector(private val baseCommand: BaseCommand) {
    private val regexSaveVar = "^SAVE\\s*\\(([^\\)]+)\\)\\s*\\=\\s*(.+)$"
    private val regexClearVar = "^CLEAR\\s*\\(([^\\)]+)\\)\\s*$"
    // private val prompt = "> inspect (enter HELP for options): "
    private val prompt = "> inspect: "

    fun inspect() {
        val context = baseCommand.context
        print(prompt)

        val stdin = Scanner(System.`in`)
        var input = stdin.nextLine()

        while (StringUtils.isNotBlank(input)) {
            try {
                when {
                    // save var
                    RegexUtils.isExact(input, regexSaveVar)  -> {
                        val groups = RegexUtils.collectGroups(input, regexSaveVar)
                        saveVar(groups[0], context.replaceTokens(groups[1], true))
                    }

                    // clear var
                    RegexUtils.isExact(input, regexClearVar) -> {
                        val groups = RegexUtils.collectGroups(input, regexClearVar)
                        clearVar(TextUtils.toList(groups[0], ",", true))
                    }

                    // replace/update
                    else                                     -> {
                        showVar(input)
                    }
                }
            } catch (e: Throwable) {
                ConsoleUtils.error("ERROR on '$input' - ${e.message}")
            }

            println()
            print(prompt)
            input = stdin.nextLine()
        }
    }

    private fun saveVar(dataVariable: String, dataValue: String) {
        ConsoleUtils.log((if (baseCommand.context.hasData(dataVariable)) "updating" else "creating") +
                         " data variable [$dataVariable] to [$dataValue]")
        baseCommand.save(dataVariable, dataValue)
    }

    private fun clearVar(variables: List<String>) {
        ConsoleUtils.log("removing data variable $variables")
        val outcome = baseCommand.clearVariables(variables)

        // outcome.trim().split("\n").forEach { ConsoleUtils.log(it) }
        // forgo above simple print out for something more elaborate
        // not sure if this is a good idea.. the message itself doesn't have well-defined demarcation of
        // data variable list

        outcome.trim().split("\n").forEach {
            if (it.contains(": ")) {
                ConsoleUtils.log(it.substringBefore(": ") + ": ")
                it.substringAfter(": ").split(",").forEach { item -> ConsoleUtils.log("\t$item") }
            } else {
                ConsoleUtils.log(it)
            }
        }
    }

    private fun showVar(input: String) = println(baseCommand.context.replaceTokens(input, true))
}