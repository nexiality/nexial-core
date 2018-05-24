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

package org.nexial.core;

import static org.nexial.core.NexialConst.Data.*;

/**
 * the definition and a list of known execution-level events
 */
public enum ExecutionEvent {
    ScriptStart("onScriptStart", NOTIFY_ON_START, "Script Execution Started"),
    ScriptComplete("onScriptComplete", NOTIFY_ON_COMPLETE, "Script Execution Completed"),
    ErrorOccurred("onError", NOTIFY_ON_ERROR, "Error Occurred"),
    ExecutionPause("onPause", NOTIFY_ON_PAUSE, "Execution Paused");

    private String name;
    private String variable;
    private String description;

    ExecutionEvent(String name, String variable, String description) {
        this.name = name;
        this.variable = variable;
        this.description = description;
    }

    public String getName() { return name; }

    public String getVariable() { return variable; }

    public String getDescription() { return description; }

    @Override
    public String toString() { return name; }
}
