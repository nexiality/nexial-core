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
import org.nexial.core.NexialConst.NAMESPACE

private const val NOTIFY_ON = NAMESPACE + "notifyOn"

/**
 * the definition and a list of known execution-level events
 * @property eventName String
 * @property variable String
 * @property description String
 * @constructor
 */
enum class ExecutionEvent(val eventName: String, val variable: String, val description: String) {

    ExecutionStart("onExecutionStart", "${NOTIFY_ON}ExecutionStart", "Execution started"),
    ExecutionComplete("onExecutionComplete", "${NOTIFY_ON}ExecutionComplete", "Execution completed"),

    ScriptStart("onScriptStart", "${NOTIFY_ON}ScriptStart", "Script execution started"),
    ScriptComplete("onScriptComplete", "${NOTIFY_ON}ScriptComplete", "Script execution completed"),

    IterationStart("onIterationStart", "${NOTIFY_ON}IterationStart", "Iteration execution started"),
    IterationComplete("onIterationComplete", "${NOTIFY_ON}IterationComplete", "Iteration execution completed"),

    ScenarioStart("onScenarioStart", "${NOTIFY_ON}ScenarioStart", "Scenario execution started"),
    ScenarioComplete("onScenarioComplete", "${NOTIFY_ON}ScenarioComplete", "Scenario execution completed"),

    ErrorOccurred("onError", "${NOTIFY_ON}Error", "Error occurred"),
    ExecutionPause("onPause", "${NOTIFY_ON}Pause", "Execution paused"),

    DesktopUseApp("onDesktopUseApp", "${NOTIFY_ON}DesktopUseApp", "Desktop app in use"),
    DesktopUseForm("onDesktopUseForm", "${NOTIFY_ON}DesktopUseForm", "Desktop form in use"),
    DesktopUseTable("onDesktopUseTable", "${NOTIFY_ON}DesktopUseTable", "Desktop table in use"),
    DesktopUseList("onDesktopUseList", "${NOTIFY_ON}DesktopUseList", "Desktop list in use"),

    WsStart("onWsStart", "${NOTIFY_ON}WsStart", "Web service invoked"),
    WsComplete("onWsComplete", "${NOTIFY_ON}WsComplete", "Web service completed"),

    RdbmsStart("onRdbmsStart", "${NOTIFY_ON}RdbmsStart", "SQL query started"),
    RdbmsComplete("onRdbmsComplete", "${NOTIFY_ON}RdbmsComplete", "SQL query completed"),

    WebOpen("onWebOpen", "${NOTIFY_ON}WebOpen", "URL invoked"),
    BrowserComplete("onBrowserComplete", "${NOTIFY_ON}BrowserComplete", "current Browser instance terminated");

    override fun toString(): String = eventName

    object Utils {
        val eventId: String = RandomStringUtils.randomAlphanumeric(10)
    }
}
