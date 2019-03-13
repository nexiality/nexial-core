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
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.ExecutionThread;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.TestCase;
import org.nexial.core.model.TestStep;
import org.nexial.core.utils.ConsoleUtils;

import static org.apache.commons.lang3.SystemUtils.JAVA_VERSION;
import static org.apache.commons.lang3.SystemUtils.USER_NAME;
import static org.nexial.core.NexialConst.Data.CURR_ITERATION;
import static org.nexial.core.NexialConst.Data.ITERATION_ENDED;
import static org.nexial.core.NexialConst.OPT_INPUT_EXCEL_FILE;
import static org.nexial.core.NexialConst.Project.SCRIPT_FILE_EXT;
import static org.nexial.core.utils.ExecUtils.NEXIAL_MANIFEST;

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
    enum Metadata {
        name, fullpath, index, script, iteration, scenario, activity, description, command,
        // on-the-fly calculation of elapsed time, pass count, fail count, skip count and success rate at every scope
        // EXCEPT step
        // elapsed, passed, failed, skipped, success
    }

    enum Artifact {script, iteration, scenario, activity, step }

    public String script(String scope) { return evaluateExecutionData(Artifact.script, Metadata.valueOf(scope)); }

    public String scenario(String scope) { return evaluateExecutionData(Artifact.scenario, Metadata.valueOf(scope)); }

    public String activity(String scope) { return evaluateExecutionData(Artifact.activity, Metadata.valueOf(scope)); }

    public String step(String scope) { return evaluateExecutionData(Artifact.step, Metadata.valueOf(scope)); }

    public String iteration(String scope) { return evaluateExecutionData(Artifact.iteration, Metadata.valueOf(scope)); }

    public String meta(String type) {
        if (StringUtils.equalsIgnoreCase(type, "nexial")) { return NEXIAL_MANIFEST; }
        if (StringUtils.equalsIgnoreCase(type, "java")) { return "Java " + JAVA_VERSION; }
        if (StringUtils.equalsIgnoreCase(type, "user")) { return USER_NAME; }
        ConsoleUtils.log("Unknown 'type': " + type);
        return null;
    }

    protected void init() { }

    private String evaluateExecutionData(Artifact scope, Metadata metadata) {
        ExecutionContext context = ExecutionThread.get();
        if (context == null) { return null; }

        String metaRequest = "$(execution|" + scope + "|" + metadata + ")";
        boolean iterationEnded = context.getTestScript() == null || context.getBooleanData(ITERATION_ENDED, false);
        TestStep currentStep = context.getCurrentTestStep();

        if (iterationEnded && currentStep == null &&
            (Artifact.step == scope || Artifact.activity == scope || Artifact.iteration == scope)) {
            ConsoleUtils.log("Iteration ended; unable to determine " + metaRequest);
            return "N/A";
        }

        String error = "Built-in function: " + metaRequest;

        switch (scope) {
            case step: {
                if (currentStep == null) {
                    ConsoleUtils.error(error + " current step cannot be determined");
                    return "";
                }

                switch (metadata) {
                    case index:
                        return (currentStep.getRowIndex() + 1) + "";
                    case name:
                        return "[" + resolveScriptName(context) + "]" +
                               "[" + resolveScenario(context) + "]" +
                               "[" + resolveActivity(currentStep) + "]" +
                               "[ROW " + (currentStep.getRowIndex() + 1) + "]";
                    case script:
                        return resolveScriptName(context);
                    case iteration:
                        return context.getIntData(CURR_ITERATION) + "";
                    case scenario:
                        return resolveScenario(context);
                    case activity:
                        return resolveActivity(currentStep);
                    case description:
                        return currentStep.getDescription();
                    case command:
                        return currentStep.getCommandFQN();
                    case fullpath:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case activity: {
                String activityName = resolveActivity(currentStep);
                if (StringUtils.isBlank(activityName)) {
                    ConsoleUtils.error(error + " current activity cannot be determined");
                    return "";
                }

                switch (metadata) {
                    case name:
                        return activityName;
                    case script:
                        return resolveScriptName(context);
                    case iteration:
                        return context.getIntData(CURR_ITERATION) + "";
                    case scenario:
                        return resolveScenario(context);
                    case fullpath:
                    case index:
                    case activity:
                    case description:
                    case command:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case scenario: {
                String scenarioName = resolveScenario(context);
                if (StringUtils.isBlank(scenarioName)) {
                    ConsoleUtils.error(error + " current scenario cannot be determined");
                    return "";
                }

                switch (metadata) {
                    case name:
                        return resolveScenario(context);
                    case script:
                        return resolveScriptName(context);
                    case iteration:
                        return context.getIntData(CURR_ITERATION) + "";
                    case fullpath:
                    case index:
                    case scenario:
                    case activity:
                    case description:
                    case command:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case iteration: {
                switch (metadata) {
                    case index:
                        return context.getIntData(CURR_ITERATION) + "";
                    case script:
                        return resolveScriptName(context);
                    case name:
                    case fullpath:
                    case iteration:
                    case scenario:
                    case activity:
                    case description:
                    case command:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }

            case script: {
                switch (metadata) {
                    case name:
                        return resolveScriptName(context);
                    case fullpath: {
                        String excelFile = context.getStringData(OPT_INPUT_EXCEL_FILE);
                        if (StringUtils.isNotEmpty(excelFile)) { return new File(excelFile).getAbsolutePath(); }
                    }
                    case index:
                    case script:
                    case iteration:
                    case scenario:
                    case activity:
                    case description:
                    case command:
                    default:
                        ConsoleUtils.error(error);
                        return "";
                }
            }
        }

        return null;
    }

    @NotNull
    private String resolveScriptName(ExecutionContext context) {
        ExecutionDefinition execDef = context.getExecDef();
        if (execDef == null) { return ""; }

        String testScript = execDef.getTestScript();
        if (testScript == null) { return ""; }

        return StringUtils.removeEndIgnoreCase(new File(testScript).getName(), SCRIPT_FILE_EXT);
    }

    @NotNull
    private String resolveScenario(ExecutionContext context) { return context.getCurrentScenario(); }

    @NotNull
    private String resolveActivity(TestStep step) {
        TestCase activity = resolveEnclosedActivity(step);
        return activity == null ? "" : activity.getName();
    }

    private TestCase resolveEnclosedActivity(TestStep step) { return step == null ? null : step.getTestCase(); }
}
