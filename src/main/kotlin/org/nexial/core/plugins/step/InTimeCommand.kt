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

import org.apache.commons.lang3.math.NumberUtils
import org.nexial.core.model.StepResult
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

class InTimeCommand : StepCommand() {

    override fun getTarget() = "step.inTime"

    fun perform(instructions: String, waitMs: String) =
            executeInTime({ performHelper(instructions, "PERFORM ACTION (timeout in ${waitMs}ms)", true) }, waitMs)

    fun validate(prompt: String, responses: String, passResponses: String, waitMs: String): StepResult {
        return executeInTime(
            { validateHelper(prompt, responses, passResponses, "VALIDATION (timeout in ${waitMs}ms)", true) },
            waitMs)
    }

    fun observe(prompt: String, waitMs: String) =
        executeInTime({ observeHelper(prompt, "OBSERVATION (timeout in ${waitMs}ms)", true) }, waitMs)

    private fun executeInTime(f: Supplier<StepResult>, waitMs: String): StepResult {
        val executor = Executors.newSingleThreadExecutor { r: Runnable? ->
            val thread = Thread(r)
            thread.isDaemon = true
            thread
        }
        val task = executor.submit(Callable { f.get() })

        return try {
            task[NumberUtils.toLong(waitMs), MILLISECONDS]
        } catch (exception: TimeoutException) {
            task.cancel(true)
            computeStepResult("", "", false, "Step execution timeout.")
        } finally {
            executor.shutdown()
        }
    }
}