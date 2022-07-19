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

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.ParseException
import org.apache.commons.collections4.CollectionUtils
import org.nexial.core.tms.spi.TMSOperation
import org.nexial.core.tms.spi.TmsException
import org.nexial.core.tms.spi.TmsFactory
import org.nexial.core.tms.spi.TmsMetaJson
import org.nexial.core.tms.spi.testrail.TestRailOperations
import org.nexial.core.tms.tools.TmsImporter.addCmdOptions
import org.nexial.core.tms.tools.TmsImporter.getArgs
import org.nexial.core.utils.ConsoleUtils

/**
 * Closes all test runs for the suite associated with the Nexial test file passed in as the argument
 */
class CloseTestRun {
    /**
     * Close any active test runs associated with the provided suite id
     *
     * @param suiteId the suite id associated with the passed in file
     * @param projectId the project id of the current project
     */
    fun closeActiveRuns(tms: TMSOperation, activeRunIds: MutableList<String>) {
        activeRunIds.forEach {
            try {
                tms.closeRun(it)
            } catch (e: Exception) {
                ConsoleUtils.error(e.message)
            }
        }
    }

    /**
     * Retrieve the ids of the active test runs associated with the provided suite id.
     *
     * @param suiteId the suite id associated with the passed in file
     * @param tms object of [TestRailOperations] class containing the API client
     * @return List of ids of active test runs for the current suite
     */
    private fun getActiveTestRuns(suiteId: String, tms: TMSOperation): MutableList<String> {
        val activeRuns = try {
             tms.getExistingActiveRuns(suiteId)
        } catch (e: Exception) {
            throw TmsException("Unable to retrieve active test runs due to ${e.message}")
        }

        if (CollectionUtils.size(activeRuns) == 0) {
            throw TmsException("No active test runs found.")
        }
        ConsoleUtils.log("Active test runs found.")
        val activeRunIds = activeRuns.map { it.id.toString() }.toMutableList()
        activeRunIds.forEach {  ConsoleUtils.log("Test Run id: $it") }

        return activeRunIds
    }

    companion object {
        @Throws(ParseException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val cmdArgs = getArgs(DefaultParser().parse(addCmdOptions(), args))

            // retrieve the project.tms meta file
            val tmsTestFile = TmsMetaJson.getJsonEntryForFile(cmdArgs.first, cmdArgs.second)
            val suiteId = tmsTestFile.file!!.suiteId!!
            val tms = TmsFactory.getTmsInstance(tmsTestFile.projectId)
            val closeTestRun = CloseTestRun()
            val activeRunIds = closeTestRun.getActiveTestRuns(suiteId, tms)
            closeTestRun.closeActiveRuns(tms, activeRunIds)
        }
    }
}