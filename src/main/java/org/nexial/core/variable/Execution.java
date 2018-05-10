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

package org.nexial.core.variable;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.NexialConst.Data;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.TestCase;
import org.nexial.core.model.TestScenario;
import org.nexial.core.model.TestStep;
import org.nexial.core.utils.ConsoleUtils;

import static org.nexial.core.NexialConst.OPT_INPUT_EXCEL_FILE;

/**
 * built-in function to expose execution level meta data.  This function expose the "current" or runtime
 * meta data, but static or design-level information.
 *
 * For example,
 * <ol>
 * <li>$(execution|script|name) - display the file name (without extension) of the script currently in execution</li>
 * <li>$(execution|script|fullpath) - display the full path of the script currently in execution</li>
 * <li>$(execution|scenario|name) - display the scenario currently in execution</li>
 * <li>$(execution|activity|name) - display the activity currently in execution</li>
 * <li>$(execution|step|name) - display the step currently in execution, in the form of [script_name][scenario][activity][row number]</li>
 * <li>$(execution|step|index) - display the step currently in execution, in the form of row number</li>
 * <li>$(execution|iteration|index) - display the iteration currently in execution, zero-based</li>
 * </ol>
 */
public class Execution {
    enum Metadata {name, fullpath, index}

    enum Artifact {script, scenario, activity, step, command, iteration}

    public String script(String scope) { return evaluateExecutionData(Artifact.script, Metadata.valueOf(scope)); }

    public String scenario(String scope) { return evaluateExecutionData(Artifact.scenario, Metadata.valueOf(scope)); }

    public String activity(String scope) { return evaluateExecutionData(Artifact.activity, Metadata.valueOf(scope)); }

    public String step(String scope) { return evaluateExecutionData(Artifact.step, Metadata.valueOf(scope)); }

    public String iteration(String scope) { return evaluateExecutionData(Artifact.iteration, Metadata.valueOf(scope)); }

    protected void init() { }

    // public String plan(String scope) { return evaluateExecutionData(Artifact.script, scope); }

    private String evaluateExecutionData(Artifact scope, Metadata metadata) {
        ExecutionContext context = ExecutionThread.get();
        if (context == null) { return null; }

        String testScriptName =
            StringUtils.removeEndIgnoreCase(new File(context.getExecDef().getTestScript()).getName(), ".xlsx");

        File testScript = new File(context.getStringData(OPT_INPUT_EXCEL_FILE));

        TestStep currentStep = context.getCurrentTestStep();
        TestCase activity = currentStep == null ? null : currentStep.getTestCase();
        TestScenario scenario = activity == null ? null : activity.getTestScenario();

        String error = "Invalid function: $(execution|" + scope + "|" + metadata + ")";

        switch (scope) {
            case command: {
                if (currentStep == null) {
                    ConsoleUtils.error(error + " current step cannot be determined");
                    return "";
                }

                switch (metadata) {
                    case name:
                        return currentStep.getCommandFQN();
                    case index:
                    case fullpath:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case step: {
                if (currentStep == null) {
                    ConsoleUtils.error(error + " current step cannot be determined");
                    return "";
                }

                switch (metadata) {
                    case index:
                        return currentStep.getRowIndex() + "";
                    case name:
                        return "[" + testScriptName + "]" +
                               "[" + scenario.getName() + "]" +
                               "[" + activity.getName() + "]" +
                               "[ROW " + currentStep.getRowIndex() + "]";
                    case fullpath:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case activity: {
                if (activity == null) {
                    ConsoleUtils.error(error + " current activity cannot be determined");
                    return "";
                }

                switch (metadata) {
                    case name:
                        return activity.getName();
                    case fullpath:
                    case index:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case scenario: {
                if (scenario == null) {
                    ConsoleUtils.error(error + " current scenario cannot be determined");
                    return "";
                }

                switch (metadata) {
                    case name:
                        return scenario.getName();
                    case fullpath:
                    case index:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case iteration: {
                switch (metadata) {
                    case index:
                        return context.getIntData(Data.CURR_ITERATION) + "";
                    case name:
                    case fullpath:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case script: {
                switch (metadata) {
                    case name:
                        return testScriptName;
                    case fullpath:
                        return testScript.getAbsolutePath();
                    case index:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }
        }

        return null;
    }
}
