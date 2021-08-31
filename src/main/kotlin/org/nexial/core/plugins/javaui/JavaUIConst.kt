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

package org.nexial.core.plugins.javaui

import org.nexial.core.SystemVariables.registerSysVar

internal object JavaUIConst {

    val requiredConfigs = listOf("name", "agent", "location", "app", "type")
    const val mappingConfigurationPrefix = "mapping."
    const val embedded = "embedded"
    const val screenshotExtension = "png"
    const val defaultAutAgentPort = 60000
    const val postAutAgentDelayMs = 3000
    const val postStopAutAgentDelayMs = 4000
    const val minAppStartupWaitMs = 1500

    const val envJubulaHome = "JUBULA_HOME"

    internal object SystemVariable {
        val agent = registerSysVar("nexial.javaui.agent", embedded)
        val appStartupWaitMs = registerSysVar("nexial.javaui.appStartupWaitMs", 5000)
        val delayBetweenStepsMs = registerSysVar("nexial.javaui.delayBetweenStepsMs", 850)
        const val profileVarPrefix = "nexial.javaui.profile."
    }

    internal object Message {
        const val startAppNotExecuted = "was the startApp(profile) command executed?"
        const val noJubulaHome = "Environment variable '$envJubulaHome' is required but missing!"
        const val tooLowAppStartupWaitMs = "Profile configuration 'appStartupWaitMs' must be greater " +
                                           "than $minAppStartupWaitMs (i.e. 1.5 seconds)"
    }

}