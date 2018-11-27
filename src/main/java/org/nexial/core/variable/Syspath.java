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
 *
 */

package org.nexial.core.variable;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.Project.appendCapture;
import static org.nexial.core.NexialConst.Project.appendLog;

public class Syspath {
    enum Scope {name, fullpath, base}

    public String out(String scope) { return evaluateExecutionScope(scope, OPT_OUT_DIR); }

    public String script(String scope) {
        ExecutionContext context = ExecutionThread.get();

        String executionData = null;
        if (context != null && context.getExecDef() != null) {
            executionData = context.getExecDef().getTestScript();
        }

        if (executionData == null) { executionData = getExecutionData(OPT_INPUT_EXCEL_FILE); }

        ConsoleUtils.log(TOKEN_FUNCTION_START + "syspath|script" + TOKEN_FUNCTION_END + ": value=" + executionData);

        return evaluateScope(scope, executionData);
    }

    public String data(String scope) { return evaluateScope(scope, getExecutionData(OPT_DATA_DIR)); }

    public String screenshot(String scope) { return evaluateScope(scope, appendCapture(getExecutionData(OPT_OUT_DIR)));}

    public String log(String scope) { return evaluateScope(scope, appendLog(getExecutionData(OPT_OUT_DIR))); }

    // todo: still needed?
    public String suite(String scope) { return evaluateExecutionScope(scope, OPT_SUITE_PROP); }

    public String temp(String scope) { return evaluateScope(scope, SystemUtils.getJavaIoTmpDir().getAbsolutePath()); }

    public String project(String scope) { return evaluateScope(scope, getExecutionData(OPT_PROJECT_BASE)); }

    static String getExecutionData(String varName) {
        if (StringUtils.isBlank(varName)) { return null; }

        String value = System.getProperty(varName);
        if (StringUtils.isNotBlank(value)) { return value; }

        ExecutionContext context = ExecutionThread.get();
        if (context == null) { return null; }

        return context.getStringData(varName);
    }

    protected void init() { }

    private String evaluateExecutionScope(String scope, String varName) {
        return evaluateScope(scope, getExecutionData(varName));
    }

    private String evaluateScope(String scope, String fullpath) {
        if (StringUtils.isBlank(fullpath)) { return null; }

        try {
            Scope s = Scope.valueOf(scope);
            File path = new File(fullpath);
            switch (s) {
                case name:
                    return path.getName();
                case base:
                    return path.getParent();
                default:
                    return path.getAbsolutePath();
            }
        } catch (IllegalArgumentException e) {
            ConsoleUtils.error("'" + scope + "' is not valid or support for " +
                               TOKEN_FUNCTION_START + "syspath" + TOKEN_FUNCTION_END);
            return evaluateScope(Scope.fullpath.name(), fullpath);
        }
    }
}
