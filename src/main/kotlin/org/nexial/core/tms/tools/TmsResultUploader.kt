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

import com.google.gson.Gson
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.NexialConst.DEF_CHARSET
import org.nexial.core.NexialConst.OPT_OUT_DIR
import org.nexial.core.model.ExecutionSummary
import org.nexial.core.tms.TmsConst.EXECUTION_SUMMARY
import org.nexial.core.tms.TmsConst.LATEST_FROM
import org.nexial.core.tms.TmsConst.OUTPUT
import org.nexial.core.tms.TmsConst.REGEX_PATTERN

import org.nexial.core.tms.spi.TmsProcessor
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator
import java.io.FileFilter
import kotlin.system.exitProcess

/**
 * Upload the Nexial Test result of the already imported test script/plan by providing output in the argument.
 * This accepts two arguments -output and -latestFrom
 * Usage:-
 * 1. To upload specific result - <br>nexial-tms-result-uploader.cmd|sh -output "C:/projects/demo/output/20221212_124316</br>
 * 2. To upload latest result of the project :- <br>nexial-tms-result-uploader.cmd|sh -output "C:/projects/demo</br>
 */
object TmsResultUploader {
    @Throws(ParseException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val cmd = DefaultParser().parse(initCmdOptions(), args)
        if (cmd.hasOption(OUTPUT)) {
            val output = cmd.getOptionValue(OUTPUT)
            importResultsToTms(output)
        } else if (cmd.hasOption(LATEST_FROM)) {
            val project = cmd.getOptionValue(LATEST_FROM)
            uploadLatestResult(project)
        } else {
            ConsoleUtils.error("Options are unsupported for uploading execution result.")
            exitProcess(-1)
        }
    }

    /**
     * Import test execution result to already imported testcases from the output folder provided by user.
     *
     * @param output fully qualified output folder path containing execution-summary.json.
     */
    private fun importResultsToTms(output: String) {
        if (!FileUtils.isDirectory(File(output))) {
            ConsoleUtils.error("$output is not a output directory.")
            exitProcess(-1)
        }

        System.setProperty(OPT_OUT_DIR, output)
        val executionSummary = "$output/$EXECUTION_SUMMARY"
        val executionSummaryFile = File(executionSummary)
        if (!FileUtil.isFileReadable(executionSummaryFile)) {
            ConsoleUtils.error("File '$executionSummary' doesn't exist or is not readable.")
            exitProcess(-1)
        }

        val summary = Gson().fromJson(
            FileUtils.readFileToString(executionSummaryFile, DEF_CHARSET), ExecutionSummary::class.java)
        try {
            TmsProcessor().importResultsToTms(summary)
        } catch(e: Exception) {
            ConsoleUtils.error(e.message)
            e.printStackTrace()
        }
    }

    /**
     * Upload latest execution result from specified project.
     *
     * @param project fully qualified project path to upload the latest results from
     */
    private fun uploadLatestResult(project: String) {
        if (System.getenv("NEXIAL_OUTPUT") != null) {
            ConsoleUtils.error("Upload is NOT supported if 'NEXIAL_OUTPUT' is set up.")
            exitProcess(-1)
        }

        val nexialOutput = StringUtils.appendIfMissing(project, separator) + OUTPUT
        if (!FileUtils.isDirectory(File(nexialOutput))) {
            ConsoleUtils.error("Nexial output '$nexialOutput' is not a directory.")
            exitProcess(-1)
        }

        val directories = File(nexialOutput).listFiles(
            FileFilter { it.isDirectory && RegexUtils.match(it.name, REGEX_PATTERN) })

        if (ArrayUtils.isEmpty(directories)) {
            ConsoleUtils.error("There is no output folder available to upload.")
            exitProcess(-1)
        }

        val outputFolder = directories.sortedDescending()[0]
        importResultsToTms(outputFolder.absolutePath)
    }

    /**
     * Command line options
     */
    private fun initCmdOptions(): Options {
        val cmdOptions = Options()
        cmdOptions.addOption(LATEST_FROM, true,
            "[REQUIRED] if -$OUTPUT is missing] The fully qualified path of the project.")

        cmdOptions.addOption(OUTPUT, true,
            "[REQUIRED] if -$LATEST_FROM is missing] The fully qualified path of the test output.")
        return cmdOptions
    }
}
