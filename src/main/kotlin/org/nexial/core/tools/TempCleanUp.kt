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

package org.nexial.core.tools

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.nexial.core.NexialConst.Project.BATCH_EXT
import org.nexial.core.NexialConst.TEMP
import org.nexial.core.tools.CliConst.OPT_VERBOSE
import org.nexial.core.tools.CliUtils.getCommandLine
import org.nexial.core.tools.ProjectToolUtils.log
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.utils.ExecUtils.findTempDirectories
import java.util.*
import java.util.Calendar.HOUR

object TempCleanUp {

    @JvmStatic
    fun main(args: Array<String>) {
        val cmd = deriveCommandLine(args)
        val verbose = cmd.hasOption(OPT_VERBOSE.opt)
        cleanTempFiles(verbose)
    }

    private fun deriveCommandLine(args: Array<String>): CommandLine {
        val cmdOptions = Options()
        cmdOptions.addOption(OPT_VERBOSE)
        return getCommandLine("nexial-clean$BATCH_EXT", args, cmdOptions)
    }

    fun cleanTempFiles(verbose: Boolean) = cleanTempFiles(verbose, 24)

    fun cleanTempFiles(verbose: Boolean, keepSinceHours: Int) {
        val cal = Calendar.getInstance()
        cal.add(HOUR, -1 * keepSinceHours)
        val oneDayAgo = cal.time

        try {
            findTempDirectories().filter { isBefore(Date(it.lastModified()), oneDayAgo) }.forEach {
                if (verbose) log("delete", it)
                if (!FileUtils.deleteQuietly(it)) ConsoleUtils.error("unable to delete directory: $it")
            }
        } catch (e: Throwable) {
            ConsoleUtils.error("Unable to read or delete temp files from $TEMP, will try again later: " +
                               ExceptionUtils.getRootCauseMessage(e))
        }
    }

    private fun isBefore(modifiedDate: Date, beforeDate: Date) = modifiedDate.before(beforeDate)
}
