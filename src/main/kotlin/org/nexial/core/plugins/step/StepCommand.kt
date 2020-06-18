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

import org.apache.commons.lang3.StringUtils.*
import org.nexial.core.NexialConst.Data.STEP_COMMENT
import org.nexial.core.NexialConst.Data.STEP_RESPONSE
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.requiresNotBlank
import org.nexial.core.utils.ConsoleUtils
import java.util.function.Function

open class StepCommand : BaseCommand() {

    override fun getTarget() = "step"

    fun perform(instructions: String): StepResult {
        return performHelper(instructions, "PERFORM ACTION")
    }

    fun validate(prompt: String, responses: String, passResponses: String): StepResult {
        return validateHelper(prompt, responses, passResponses, "VALIDATION")
    }

    fun observe(prompt: String): StepResult {
        return observeHelper(prompt, "OBSERVATION")
    }

    protected fun performHelper(instructions: String, header: String): StepResult {
        clearContextData()
        requiresNotBlank(instructions, "Invalid instruction(s)", instructions)

        val comment = ConsoleUtils.pauseForStep(context, instructions, header)

        if (isBlank(comment)) log("Step(s) performed")
        return computeStepResult("", comment, isBlank(comment) || !startsWith(comment, "FAIL "), "")
    }

    protected fun validateHelper(prompt: String, responses: String, passResponses: String, header: String): StepResult {
        clearContextData()
        requiresNotBlank(prompt, "Invalid prompt(s)", prompt)

        val validationResponses = ConsoleUtils.pauseToValidate(context, prompt, responses, header)
        val response = validationResponses?.get(0)
        val comment = validationResponses?.get(1)

        if (isBlank(response)) log("Empty response accepted as PASS.")
        return computeStepResult(response + "", comment + "",
                                 isBlank(response) || passResponses.split(context.textDelim).contains(response), "")
    }

    protected fun observeHelper(prompt: String, header: String): StepResult {
        clearContextData()
        requiresNotBlank(prompt, "Invalid prompt(s)", prompt)

        val response = ConsoleUtils.pauseForInput(context, prompt, header)

        if (isBlank(response)) log("Empty response accepted as PASS.")
        val result = computeStepResult(response, "", !startsWith(response, "FAIL "), "")
        result.paramValues = arrayOf<Any>(prompt, response)
        return result
    }

    private val commentFormat = Function { c: String -> if (isNotEmpty(c)) "[comment]: $c" else "" }
    private val responseFormat = Function { r: String -> if (isNotEmpty(r)) "[response]: $r " else "" }

    protected fun computeStepResult(response: String, comment: String, pass: Boolean, defaultMsg: String): StepResult {

        setContextData(response, comment)
        var msg = "";
        if (isNotEmpty(defaultMsg)) msg += "($defaultMsg) ";
        msg += responseFormat.apply(response) + commentFormat.apply(comment)

        return if (pass) StepResult.success(msg) else StepResult.fail(msg);
    }

    private fun setContextData(response: String, comment: String) {
        if (isNotBlank(response)) context.setData(STEP_RESPONSE, response)
        if (isNotBlank(comment)) context.setData(STEP_COMMENT, comment)
    }

    private fun clearContextData() {
        context.removeData(STEP_RESPONSE)
        context.removeData(STEP_COMMENT)
    }

}