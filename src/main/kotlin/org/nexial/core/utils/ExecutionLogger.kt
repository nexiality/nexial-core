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

package org.nexial.core.utils

import org.apache.commons.lang3.StringUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.TestCase
import org.nexial.core.model.TestScenario
import org.nexial.core.model.TestStep
import org.nexial.core.plugins.CanLogExternally
import org.nexial.core.plugins.NexialCommand
import org.slf4j.LoggerFactory
import java.io.File

class ExecutionLogger(private val context: ExecutionContext) {
    private val runId: String = context.runId
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val priorityLogger = LoggerFactory.getLogger(this.javaClass.name + "-priority")

    @JvmOverloads
    fun log(subject: NexialCommand, message: String, priority: Boolean = false) {
        val testStep = context.currentTestStep
        if (testStep != null) {
            // test step undefined could mean that we are in interactive mode, or we are running unit testing
            log(toHeader(testStep), message, priority)
            if (context.isVerbose) {
                testStep.addNestedMessage(message)
                if (subject is CanLogExternally) (subject as CanLogExternally).logExternally(testStep, message)
            }
        } else {
            log(runId, message, priority)
        }
    }

    @JvmOverloads
    fun log(testStep: TestStep, message: String, priority: Boolean = false) = log(toHeader(testStep), message, priority)

    // force logging when quiet mode is active
    fun log(subject: TestCase, message: String) = log(toHeader(subject), message, true)

    // force logging when quiet mode is active
    fun log(subject: TestScenario, message: String) = log(toHeader(subject), message, true)

    // force logging when quiet mode is active
    fun log(subject: ExecutionContext, message: String) = log(toHeader(subject), message, true)

    @JvmOverloads
    fun error(subject: NexialCommand, message: String, exception: Throwable? = null) {
        val testStep = ExecutionThread.get().currentTestStep
        error(toHeader(testStep), message, exception)
        if (subject is CanLogExternally) (subject as CanLogExternally).logExternally(testStep, message)
    }

    fun errorToOutput(subject: NexialCommand, message: String, exception: Throwable? = null) {
        val testStep = ExecutionThread.get().currentTestStep
        if (testStep != null) {
            error(toHeader(testStep), message, exception)
            testStep.addNestedMessage(message)
            if (subject is CanLogExternally) (subject as CanLogExternally).logExternally(testStep, message)
        } else {
            error(runId, message, exception)
        }
    }

    fun error(subject: TestStep, message: String) = error(toHeader(subject), message)

    fun error(subject: TestCase, message: String) = error(toHeader(subject), message)

    fun error(subject: TestScenario, message: String) = error(toHeader(subject), message)

    fun error(subject: ExecutionContext, message: String) = error(toHeader(subject), message)

    fun error(subject: ExecutionContext, message: String, e: Throwable) = error(toHeader(subject), message, e)

    private fun log(header: String, message: String, priority: Boolean) {
        if (priority) {
            priorityLogger.info("$header - $message")
        } else {
            logger.info("$header - $message")
        }
    }

    private fun error(header: String, message: String, e: Throwable? = null) {
        if (e == null) {
            priorityLogger.error("$header - $message")
        } else {
            priorityLogger.error("$header - $message", e)
        }
    }

    companion object {

        @JvmStatic
        fun toHeader(subject: TestStep?) = if (subject == null)
            "current  step"
        else
            toHeader(subject.testCase) +
            "|#" + StringUtils.leftPad((subject.rowIndex + 1).toString() + "", 3) +
            "|" + StringUtils.truncate(subject.commandFQN, 25)

        @JvmStatic
        fun toHeader(subject: TestCase?) =
                if (subject == null) "current activity" else toHeader(subject.testScenario) + "|" + subject.name

        @JvmStatic
        fun toHeader(subject: TestScenario?): String {
            return if (subject == null || subject.worksheet == null)
                "current scenario"
            else {
                val worksheet = subject.worksheet
                return justFileName(worksheet.file) + "|" + worksheet.name
            }
        }

        @JvmStatic
        fun toHeader(subject: ExecutionContext?): String = if (subject != null && subject.testScript != null)
            justFileName(subject.testScript.file)
        else
            "current script"

        @JvmStatic
        fun justFileName(file: File): String = StringUtils.substringBeforeLast(file.name, ".")
    }
}