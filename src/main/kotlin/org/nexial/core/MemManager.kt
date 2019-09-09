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

package org.nexial.core

import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.proc.RuntimeUtils
import org.nexial.commons.utils.DateUtility
import org.nexial.core.NexialConst.OPT_MANAGE_MEM
import org.nexial.core.SystemVariables.getDefault
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionDefinition
import org.nexial.core.model.TestCase
import org.nexial.core.utils.ConsoleUtils
import java.text.DecimalFormat
import java.util.*

object MemManager {
    private val memManagementEnabled =
            BooleanUtils.toBoolean(System.getProperty(OPT_MANAGE_MEM, getDefault(OPT_MANAGE_MEM)))
    private val memFormat = DecimalFormat("###,###")
    private const val memLength = 15
    private const val logLength = 90
    private val memChanges = LinkedHashMap<String, String>()
    private val gcScopes = listOf(Nexial::class.java,
                                  ExecutionContext::class.java,
                                  ExecutionThread::class.java,
                                  ExecutionDefinition::class.java,
                                  TestCase::class.java)

    private var lastMemUsed: Long = -1

    @JvmStatic
    fun recordMemoryChanges(title: String) {
        if (!memManagementEnabled) return

        val memUsed = RuntimeUtils.memUsed()
        var shortenTitle = StringUtils.rightPad(title, logLength)
        val descriptive = "mem use: " + StringUtils.leftPad(memFormat.format(memUsed), memLength) +
                          if (lastMemUsed != -1L)
                              ", changes: " + StringUtils.leftPad(memFormat.format(memUsed - lastMemUsed), memLength)
                          else
                              ""

        ConsoleUtils.log("[MEM] $shortenTitle $descriptive")
        lastMemUsed = memUsed
        memChanges[DateUtility.getCurrentTimestampForLogging() + " " + shortenTitle] = descriptive
    }

    @JvmStatic
    fun gc(requestor: Any?) {
        if (memManagementEnabled && requestor != null) gc(requestor.javaClass)
    }

    @JvmStatic
    fun gc(requestorClass: Class<*>) {
        if (memManagementEnabled && gcScopes.contains(requestorClass)) {
            RuntimeUtils.gc()
            recordMemoryChanges(requestorClass.simpleName + ", after gc")
        }
    }

    @JvmOverloads
    @JvmStatic
    fun showUsage(logPrefix: String = ""): String? {
        if (!memManagementEnabled) return null

        val buffer = StringBuilder()
        memChanges.forEach { (title, log) ->
            buffer.append(logPrefix).append(title).append(" ").append(log).append("\n")
        }
        return buffer.toString()
    }
}
