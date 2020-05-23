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

package org.nexial.core.plugins.step

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.core.NexialConst.Data.STEP_RESPONSE
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class InTimeCommand : BaseCommand() {

    override fun getTarget() = "step.inTime"

    fun perform(instructions: String, waitMs: String): StepResult {
        requiresNotBlank(instructions, "Invalid instruction(s)", instructions)
        val header = "PERFORM ACTION (Timeout in ${waitMs}ms)"
        val executor = Executors.newSingleThreadExecutor()
        val task = executor.submit(Callable { ConsoleUtils.pauseForStep(context, instructions, header) })

        val comment = try {
            task[NumberUtils.toLong(waitMs), TimeUnit.MILLISECONDS]
        } catch (exception: TimeoutException) {
            task.cancel(true);
            "FAIL - Step execution timeout."
        } finally {
            executor.shutdownNow()
        }

        // supports keyword FAIL
        return when {
            StringUtils.isBlank(comment)             -> StepResult.success("Step(s) performed")
            StringUtils.startsWith(comment, "FAIL ") -> StepResult.fail(comment)
            else                                     -> StepResult.success("Response received as '$comment'")
        }
    }

    fun validate(prompt: String, responses: String, passResponses: String, waitMs: String): StepResult {
        requiresNotBlank(prompt, "Invalid prompt(s)", prompt)
        val header = "VALIDATION (Timeout in ${waitMs}ms)"
        val executor = Executors.newSingleThreadExecutor();
        val task = executor.submit(Callable { ConsoleUtils.pauseToValidate(context, prompt, responses, header) })

        val validationResponses = try {
            task[NumberUtils.toLong(waitMs), TimeUnit.MILLISECONDS]
        } catch (exception: TimeoutException) {
            task.cancel(true);
            listOf("FAIL", "Timeout Occurred");
        } finally {
            executor.shutdownNow()
        }

        val response = validationResponses[0]
        val comment = validationResponses[1]

        context.setData(STEP_RESPONSE, response)

        return if (StringUtils.isBlank(response)) {
            StepResult.success("Empty response " +
                               "${if (StringUtils.isBlank(passResponses)) "accepted as PASS" else "found"}. " +
                               "Comment: $comment")
        } else {
            val pass = passResponses.split(context.textDelim).contains(response)
            StepResult(pass,
                       "Response is '$response'. ${"Comment: $comment"}",
                       null)
        }
    }


    fun observe(prompt: String, waitMs: String): StepResult {
        requiresNotBlank(prompt, "Invalid prompt(s)", prompt)
        val header = "OBSERVATION (Timeout in ${waitMs}ms)"
        val executor = Executors.newSingleThreadExecutor()
        val task = executor.submit(Callable { ConsoleUtils.pauseForInput(context, prompt, header) })

        val response = try {
            val tmp = task[NumberUtils.toLong(waitMs), TimeUnit.MILLISECONDS]
            if (StringUtils.isEmpty(tmp)) "FAIL - No response received." else tmp
        } catch (exception: TimeoutException) {
            task.cancel(true);
            "FAIL - Timeout Occurred"
        } finally {
            executor.shutdownNow()
        }

        context.setData(STEP_RESPONSE, response)

        // supports keyword FAIL | PASS
        val result = if (StringUtils.startsWith(response, "FAIL ")) StepResult.fail(response) else
            StepResult.success("Response received as '$response'")
        result.paramValues = arrayOf<Any>(prompt, response)
        return result
    }

}
