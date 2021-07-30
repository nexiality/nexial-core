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

package org.nexial.core.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import javax.validation.constraints.NotNull;

import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.interactive.InteractiveConsole;
import org.nexial.core.interactive.InteractiveSession;
import org.nexial.core.model.ExecutionDefinition;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.TestProject;
import org.nexial.core.plugins.base.BaseCommand;

public class ConsoleUtilsTest {
    private MockExecutionContext context;

    @Before
    public void setUp() throws Exception {
        context = new MockExecutionContext();

        // add 'base' because we need it for InteractiveSession
        BaseCommand baseCommand = new BaseCommand();
        baseCommand.init(context);
        context.addPlugin("base", baseCommand);
    }

    @Test
    public void showInteractiveMenu() {
        String script = ResourceUtils.getResourceFilePath("/showcase/artifact/script/base-showcase.xlsx");
        ExecutionDefinition execDef = prepExecDef(context, script);

        InteractiveSession session = new InteractiveSession(context);
        session.setExecutionDefinition(execDef);
        session.assignActivities(
            Arrays.asList("progressively slowing down", "Assert Nested Data Ref.", "Assert Count"));
        session.setSteps(Arrays.asList("5", "17", "6", "15", "12", "14", "14", "13", "14"));
        session.setIteration(2);

        InteractiveConsole.Companion.showMenu(session);
    }

    @Test
    public void showInteractiveRun() {
        ExecutionSummary activity1 = new ExecutionSummary();
        activity1.setName("showcase for $(count)");
        activity1.setStartTime(1541293351459L);
        activity1.setEndTime(1541293351557L);
        activity1.setTotalSteps(10);
        activity1.setPassCount(9);
        activity1.setFailCount(1);
        activity1.setExecuted(10);

        ExecutionSummary activity2 = new ExecutionSummary();
        activity2.setName("showcase for $(array), but this is a very long activity name (against all good judgement)");
        activity2.setStartTime(1541293351557L);
        activity2.setEndTime(1541293351584L);
        activity2.setTotalSteps(18);
        activity2.setPassCount(7);
        activity2.setFailCount(10);
        activity2.setExecuted(17);

        ExecutionSummary scenarioSummary = new ExecutionSummary() {
            @Override
            public Map<String, String> getReferenceData() {
                return TextUtils.toMap("=", "version=1.5.23", "environment=QA", "client=Sony");
            }
        };
        scenarioSummary.setName("nexial_function_count");
        scenarioSummary.setStartTime(1541293351457L);
        scenarioSummary.setEndTime(1541293351584L);
        scenarioSummary.addNestSummary(activity1);
        scenarioSummary.addNestSummary(activity2);
        scenarioSummary.aggregatedNestedExecutions(context);

        String script = ResourceUtils.getResourceFilePath("/showcase/artifact/script/base-showcase.xlsx");
        ExecutionDefinition execDef = prepExecDef(context, script);

        InteractiveSession session = new InteractiveSession(context);
        session.setExecutionDefinition(execDef);
        session.setScript(ResourceUtils.getResourceFilePath("/showcase/artifact/script/io-showcase.xlsx"));
        session.setException(new Exception("error! error!"));

        InteractiveConsole.Companion.showRun(scenarioSummary, session);
    }

    @NotNull
    private static ExecutionDefinition prepExecDef(MockExecutionContext context, String script) {
        String runId = ExecUtils.deriveRunId();

        TestProject project = new TestProject();
        project.setScriptPath(script);

        ExecutionDefinition execDef = new ExecutionDefinition();
        execDef.setRunId(runId);
        execDef.setTestScript(script);
        execDef.setScenarios(Collections.singletonList("base_showcase"));
        execDef.setDataSheets(TextUtils.toList("base_showcase", ",", true));
        execDef.setProject(project);

        context.setExecDef(execDef);

        return execDef;
    }
}