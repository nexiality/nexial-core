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
import org.nexial.core.NexialConst.Data.STEP_RESPONSE
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils

class StepCommand : BaseCommand() {

    override fun getTarget() = "step"

    fun perform(instructions: String): StepResult {
        requiresNotBlank(instructions, "Invalid instruction(s)", instructions)

        val comment = ConsoleUtils.pauseForStep(context, instructions, "PERFORM ACTION")
        if (StringUtils.isNotBlank(comment)) log(comment)

        // supports keyword FAIL
        return when {
            StringUtils.isBlank(comment)             -> StepResult.success("Step(s) performed")
            StringUtils.startsWith(comment, "FAIL ") -> StepResult.fail(comment)
            else                                     -> StepResult.success("Response received as '$comment'")
        }
    }

    fun validate(prompt: String, responses: String, passResponses: String): StepResult {
        requiresNotBlank(prompt, "Invalid prompt(s)", prompt)

        val validationResponses = ConsoleUtils.pauseToValidate(context, prompt, responses, "VALIDATION")
        val response = validationResponses?.get(0)
        val comment = validationResponses?.get(1)

        context.setData(STEP_RESPONSE, response)
        if (StringUtils.isNotBlank(comment)) log(comment)

        return if (StringUtils.isBlank(response)) {
            StepResult.success("Empty response " +
                               "${if (StringUtils.isBlank(passResponses)) "accepted as PASS" else "found"}. " +
                               if (comment != null) "Comment: $comment" else "")
        } else {
            val pass = passResponses.split(context.textDelim).contains(response)
            StepResult(pass,
                       "Response is '$response'. ${if (comment != null) "Comment: $comment" else ""}",
                       null)
        }
    }

    fun observe(prompt: String): StepResult {
        requiresNotBlank(prompt, "Invalid prompt(s)", prompt)

        val response = ConsoleUtils.pauseForInput(context, prompt, "OBSERVATION")
        context.setData(STEP_RESPONSE, response)

        // supports keyword FAIL | PASS
        val result = if (StringUtils.startsWith(response, "FAIL ")) StepResult.fail(response) else
            StepResult.success("Response received as '$response'")
        result.paramValues = arrayOf<Any>(prompt, response)
        return result
    }
}
