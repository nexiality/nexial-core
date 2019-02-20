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

package org.nexial.core.plugins.base

import org.apache.commons.lang3.StringUtils
import org.nexial.core.model.StepResult

class MacroCommand : BaseCommand() {
    override fun getTarget() = "macro"

    /**
     * no-op. Allow the capturing of documentation of a macro so that we can collect them for creating interactive
     * documentation (HTML).
     */
    fun description(): StepResult = StepResult.success()

    fun expects(`var`: String, default: String): StepResult = when {
        StringUtils.isNotEmpty(default) -> assertVarPresent(`var`)
        context.hasData(`var`)          -> StepResult.success()

        else                            -> {
            context.setData(`var`, default)
            StepResult.success("Data variable '$`var`' set to default value '$default'")
        }
    }

    fun produces(`var`: String, value: String): StepResult = save(`var`, value)
}
