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

package org.nexial.core.model

import org.apache.commons.lang3.RandomStringUtils
import org.nexial.core.NexialConst.Notification.*

/**
 * the definition and a list of known execution-level events
 * @property eventName String
 * @property variable String
 * @property description String
 * @constructor
 */
enum class ExecutionEvent(val eventName: String,
                          val variable: String,
                          val conditionalVar: String = variable + "If",
                          val description: String) {

    ExecutionStart("onExecutionStart", ON_EXEC_START, description = "Execution started"),
    ExecutionComplete("onExecutionComplete", ON_EXEC_COMPLETE, description = "Execution completed"),

    ScriptStart("onScriptStart", ON_SCRIPT_START, description = "Script execution started"),
    ScriptComplete("onScriptComplete", ON_SCRIPT_COMPLETE, description = "Script execution completed"),

    IterationStart("onIterationStart", ON_ITER_START, description = "Iteration execution started"),
    IterationComplete("onIterationComplete", ON_ITER_COMPLETE, description = "Iteration execution completed"),

    ScenarioStart("onScenarioStart", ON_SCN_START, description = "Scenario execution started"),
    ScenarioComplete("onScenarioComplete", ON_SCN_COMPLETE, description = "Scenario execution completed"),

    ErrorOccurred("onError", ON_ERROR, description = "Error occurred"),
    ExecutionPause("onPause", ON_PAUSE, description = "Execution paused"),

    DesktopUseApp("onDesktopUseApp", ON_USE_APP, description = "Desktop app in use"),
    DesktopUseForm("onDesktopUseForm", ON_USE_FORM, description = "Desktop form in use"),
    DesktopUseTable("onDesktopUseTable", ON_USE_TABLE, description = "Desktop table in use"),
    DesktopUseList("onDesktopUseList", ON_USE_LIST, description = "Desktop list in use"),

    WsStart("onWsStart", ON_WS_START, description = "Web service invoked"),
    WsComplete("onWsComplete", ON_WS_COMPLETE, description = "Web service completed"),

    RdbmsStart("onRdbmsStart", ON_RDBMS_START, description = "SQL query started"),
    RdbmsComplete("onRdbmsComplete", ON_RDBMS_COMPLETE, description = "SQL query completed"),

    WebOpen("onWebOpen", ON_WEB_OPEN, description = "URL invoked"),
    BrowserComplete("onBrowserComplete", ON_BROWSER_COMPLETE, description = "current Browser instance terminated");

    override fun toString(): String = eventName

    object Utils {
        val eventId: String = RandomStringUtils.randomAlphanumeric(10)
    }
}
