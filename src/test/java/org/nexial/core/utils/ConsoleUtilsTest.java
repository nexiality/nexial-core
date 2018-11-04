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

package org.nexial.core.utils;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.interactive.InteractiveConsole;
import org.nexial.core.model.ExecutionSummary;
import org.nexial.core.model.InteractiveSession;
import org.nexial.core.model.MockExecutionContext;

public class ConsoleUtilsTest {

    public static void main(String[] args) {
        _showInteractiveMenu();
    }

    @Test
    public void showInteractiveMenu() {
        _showInteractiveMenu();
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

        ExecutionSummary activity2 = new ExecutionSummary() {
            @Override
            public Map<String, String> getReferenceData() {
                return TextUtils.toMap("=", "version=1.5.23", "environment=QA", "client=Sony");
            }
        };
        activity2.setName("showcase for $(array), but this is a very long activity name (against all good judgement)");
        activity2.setStartTime(1541293351557L);
        activity2.setEndTime(1541293351584L);
        activity2.setTotalSteps(18);
        activity2.setPassCount(8);
        activity2.setFailCount(10);

        ExecutionSummary scenarioSummary = new ExecutionSummary();
        scenarioSummary.setName("nexial_function_count");
        scenarioSummary.setSourceScript("/Users/SOMEBODY/projects/nexial/nexial-core/src/test/resource/showcase" +
                                        "/artifact/script/base-showcase.xlsx");
        scenarioSummary.setStartTime(1541293351457L);
        scenarioSummary.setEndTime(1541293351584L);
        scenarioSummary.addNestSummary(activity1);
        scenarioSummary.addNestSummary(activity2);

        InteractiveConsole.showInteractiveRun(scenarioSummary, new Exception("error! error!"));
    }

    protected static void _showInteractiveMenu() {
        MockExecutionContext context = new MockExecutionContext();
        InteractiveSession session = new InteractiveSession(context);
        session.setScript("/Users/SOMEBODY/projects/nexial/nexial-core/src/test/resource/showcase" +
                          "/artifact/script/base-showcase.xlsx");
        session.setScenario("Basic Login Tests");
        session.setActivities(Arrays.asList("known users",
                                            "admin users",
                                            "foreign users without proper registration but has validated emails",
                                            "gold members"));
        session.setSteps(Arrays.asList("3", "4", "5", "15", "12","14","1", "13", "14"));
        session.setIteration(2);

        InteractiveConsole.showInteractiveMenu(session);
    }
}