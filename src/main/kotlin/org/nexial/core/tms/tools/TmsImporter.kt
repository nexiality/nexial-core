/*
 * Copyright 2012-2021 the original author or authors.
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

package org.nexial.core.tms.tools

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.StopWatch
import org.nexial.core.tms.TmsConst.BDD_KEYWORDS
import org.nexial.core.tms.TmsConst.CLOSE_RUN
import org.nexial.core.tms.TmsConst.PLAN
import org.nexial.core.tms.TmsConst.SCRIPT
import org.nexial.core.tms.TmsConst.SUBPLAN
import org.nexial.core.tms.spi.TmsException

import org.nexial.core.tms.spi.TmsProcessor
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.InputFileUtils

/**
 * Imports the Nexial Test script or plan file as a suite to provided TMS tool e.g. Testrail, AzureDevOps.
 * If the script file or plan file is imported for the first time, this will create a new test suite, otherwise
 * update the existing test suite of the provided TMS tool.
 * Usage:-
 * 1. Test script import:- nexial-tms-importer.cmd -script "scriptPathToImport"
 * 2. Test plan import:- nexial-tms-importer.cmd -plan "planPathToImport" -subplan "subplanFromGivenPlan"
 */
object TmsImporter {
    var formatBddKeywords = false
    var closePreviousRun = false

    @Throws(ParseException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val watch = StopWatch()

        try {
            val cmd = DefaultParser().parse(initCmdOptions(), args)
            val cmdArgs = getArgs(cmd)
            val filepath = cmdArgs.first
            val subplan = cmdArgs.second

            watch.start()
            TmsProcessor.importToTms(filepath, subplan)
            watch.stop()
            ConsoleUtils.log("Total time taken to update/upload suite:: ${watch.time/1000} secs")
        } catch(e: Exception) {
            ConsoleUtils.error(e.message)
            e.printStackTrace()
            if(watch.isStarted) watch.stop()
        }

    }

    fun addCmdOptions(): Options {
        val cmdOptions = Options()
        cmdOptions.addOption(SCRIPT, true,
            "[REQUIRED] if -$PLAN is missing] The fully qualified path of the test script.")
        cmdOptions.addOption(PLAN, true, "[REQUIRED if -$SCRIPT is missing] The fully qualified path of a test plan.")
        cmdOptions.addOption(SUBPLAN, true, "[REQUIRED] if -" + PLAN + "is present.The name of the test plan")
        return cmdOptions
    }

    @JvmStatic
    fun getArgs(cmd: CommandLine): Pair<String, String> {
        val filepath: String
        var subplan = StringUtils.EMPTY

        if (cmd.hasOption(SCRIPT) && cmd.hasOption(PLAN)) {
            throw TmsException("Only one type of test file is allowed per input.")
        }
        if (cmd.hasOption(SCRIPT)) {
            filepath = cmd.getOptionValue(SCRIPT)
            if (!InputFileUtils.isValidScript(filepath)) {
                throw TmsException("Specified test script '$filepath' is not readable or does not contain valid format.")
            }
        } else if (cmd.hasOption(PLAN) && cmd.hasOption(SUBPLAN)) {
            filepath = cmd.getOptionValue(PLAN)
            subplan = cmd.getOptionValue(SUBPLAN)
            if (!InputFileUtils.isValidPlanFile(filepath)) {
                throw TmsException("Specified test plan '$filepath' is not readable or does not contain valid format.")
            }
        } else {
            throw TmsException("The option '-plan' or '-script' is required for the Nexial script/plan import. " +
                    "'subplan' option is Required for Plan file import, .")
        }
        formatBddKeywords = cmd.hasOption(BDD_KEYWORDS)
        closePreviousRun = cmd.hasOption(CLOSE_RUN)
        return Pair(filepath, subplan)
    }

    /**
     * Init Command line options for tms import command
     */
    private fun initCmdOptions(): Options {
        val cmdOptions = addCmdOptions()
        cmdOptions.addOption("b", BDD_KEYWORDS, false,
            "[OPTIONAL] Format step description if starting with BDDKeywords")
        cmdOptions.addOption("c", CLOSE_RUN, false,
            "[OPTIONAL] Close previous test runs for the respective test suite")
        return cmdOptions
    }
}