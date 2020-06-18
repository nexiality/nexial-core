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
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

class InTimeCommand : StepCommand() {

    override fun getTarget() = "step.inTime"

    fun perform(instructions: String, waitMs: String): StepResult {
        return executeInTime(Supplier {
            performHelper(instructions, "PERFORM ACTION (Timeout in ${waitMs}ms)")
        }, waitMs)
    }

    fun validate(prompt: String, responses: String, passResponses: String, waitMs: String): StepResult {
        return executeInTime(
            Supplier { validateHelper(prompt, responses, passResponses, "VALIDATION (Timeout in ${waitMs}ms)") },
            waitMs)
    }

    fun observe(prompt: String, waitMs: String): StepResult {
        return executeInTime(
            Supplier { observeHelper(prompt, "OBSERVATION (Timeout in ${waitMs}ms)") },
            waitMs)
    }

    private fun executeInTime(f: Supplier<StepResult>, waitMs: String): StepResult {
        val executor = Executors.newSingleThreadExecutor();
        val task = executor.submit(Callable { f.get() })

        return try {
            task[NumberUtils.toLong(waitMs), TimeUnit.MILLISECONDS]
        } catch (exception: TimeoutException) {
            task.cancel(true);
            computeStepResult("", "", false, "Step execution timeout.")
        } finally {
            executor.shutdownNow()
        }
    }

}