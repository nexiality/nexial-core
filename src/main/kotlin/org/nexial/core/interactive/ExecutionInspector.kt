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
import org.nexial.core.ExecutionThread
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.ConsoleUtils
import java.util.*

class ExecutionInspector(private val baseCommand: BaseCommand) {

    private val desktopInspector: DesktopInspector

    init {
        val context = baseCommand.context
        // just in case
        if (ExecutionThread.get() == null) ExecutionThread.set(context)

        desktopInspector = DesktopInspector(context)
    }

    fun inspect() {
        val context = baseCommand.context
        // just in case
        if (ExecutionThread.get() == null) ExecutionThread.set(context)

        print(inspectPrompt)
        print(prompt)

        val stdin = Scanner(System.`in`)
        var input = stdin.nextLine()

        while (StringUtils.isNotBlank(input)) {
            try {
                when {
                    // show help
                    input == help -> {
                        print(quickHelp)
                    }

                    // save var
                    RegexUtils.isExact(input, regexSaveVar)    -> {
                        val groups = RegexUtils.collectGroups(input, regexSaveVar)
                        saveVar(groups[0], context.replaceTokens(groups[1], true))
                    }

                    // clear var
                    RegexUtils.isExact(input, regexClearVar)   -> {
                        val groups = RegexUtils.collectGroups(input, regexClearVar)
                        clearVar(TextUtils.toList(groups[0], ",", true))
                    }

                    // desktop var
                    RegexUtils.isExact(input, regexDesktopVar) -> {
                        val groups = RegexUtils.collectGroups(input, regexDesktopVar)
                        val locator = context.replaceTokens(StringUtils.defaultString(groups[0]))
                        val action = StringUtils.defaultString(groups[2])
                        val actionInput = context.replaceTokens(StringUtils.defaultString(groups[4]))
                        desktopInspector.inspect(locator, action, context.replaceTokens(actionInput, true))
                    }

                    // web var
//                    RegexUtils.isExact(input, regexWebVar)     -> {
//                        val groups = RegexUtils.collectGroups(input, regexWebVar)
//                        saveVar(groups[0], context.replaceTokens(groups[1], true))
//                    }

                    // replace/update
                    else                                       -> {
                        showVar(input)
                    }
                }
            } catch (e: Throwable) {
                ConsoleUtils.error("ERROR on '$input' - ${e.message}")
            }

            println()
            print(inspectPrompt)
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

    companion object {
        private const val help = ":HELP"
        private const val inspectPrompt =
            "\nType $help for usage description\n"

        private const val quickHelp =
            "\nUse:\n" +
            "> SAVE(variable-name)=... - (re)create a data variable. Example: SAVE(a)=Hello\n" +
            "> CLEAR(variable-name)    - clear data variable. Example: CLEAR(a)\n" +
            "\n" +
            "> \${...}                  - inspect data variable. Example: \${var1}, \${nexial.textDelim}\n" +
            "> \$(...)                  - inspect built-in function. Example: $(random|integer|5)\n" +
            "> [EXPR(...) => ...]      - execute Nexial Expression. Example: [TEXT(Hello) => lower]\n" +
            "\n" +
            "DESKTOP ELEMENT INSPECTION:\n" +
            ":: xpath - refers to the XPATH of the target desktop element\n" +
            ":: name  - refers to the component name of the target desktop element, which exists in the active form\n" +
            "> DESKTOP(xpath|name)    - inspect a desktop element\n" +
            "> DESKTOP(xpath|name) => click\n" +
            "                          - click on a desktop element\n" +
            "> DESKTOP(xpath|name) => doubleClick\n" +
            "                          - double click on a desktop element\n" +
            "> DESKTOP(xpath|name) => type(input)\n" +
            "                          - type specified input on a desktop element. Shortcut keys accepted.\n" +
            "> DESKTOP(xpath|name) => context(label,label,...)\n" +
            "                          - invoke context menu of the a desktop element\n" +
            "> DESKTOP(app) => menu(label,label,...)\n" +
            "                          - invoke the application menu via respective labels on the current form\n" +
            "\n" +
            "> Press [Enter]           - quit Inspect and return back to Nexial Interactive\n" +
            "\n" +
            "Data variable, built-in function and Nexial Expression can be used in combination.\n"

        private const val prompt = "> inspect: "

        internal const val regexSaveVar = "^SAVE\\s*\\(([^\\)]+)\\)\\s*\\=\\s*(.+)$"

        internal const val regexClearVar = "^CLEAR\\s*\\(([^\\)]+)\\)\\s*$"

        // support various inspection and actions. e.g.:
        //  DESKTOP(xpath)                       -- inspect element based on xpath
        //  DESKTOP(label)                       -- inspect element based on label in current form
        //  DESKTOP(xpath|label) => click        -- click on element
        //  DESKTOP(xpath|label) => doubleClick  -- double click on element
        //  DESKTOP(xpath|label) => type(text)   -- type text, includes shortcuts and function keys, on element
        @JvmStatic
        internal val regexDesktopVar = "^DESKTOP\\s*\\((.+?)\\)(\\s*\\=\\>\\s*(\\w+)(\\((.+)?\\))?)*?\\s*$"

        @JvmStatic
        internal val regexWebVar = "^WEB\\s*\\(([^\\)]+)\\)\\s*\\=\\s*(.+)$"

    }
}