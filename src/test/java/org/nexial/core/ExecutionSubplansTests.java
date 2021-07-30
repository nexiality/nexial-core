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

package org.nexial.core;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.core.model.ExecutionSummary;

import static org.nexial.core.NexialConst.Data.OPT_RUN_ID;
import static org.nexial.core.NexialConst.SUBPLANS_INCLUDED;
import static org.nexial.core.NexialConst.SUBPLANS_OMITTED;

public class ExecutionSubplansTests extends ExcelBasedTests {

    @Override
    @Before
    public void init() throws Exception {
        super.init();
    }

    @Test
    public void executeSubplans() throws Exception {
        ExecutionSummary executionSummary =
            new ExcelBasedTestBuilder()
                .setPlan("unitTest_Subplan.xlsx", "Test Plan3", "Test Plan2").execute();

        List<ExecutionSummary> planExecutions = executionSummary.getNestedExecutions();
        Assert.assertEquals(2, planExecutions.size());
        Assert.assertEquals("Test Plan3", planExecutions.get(0).getPlanName());
        Assert.assertEquals("Test Plan2", planExecutions.get(1).getPlanName());
        Assert.assertEquals("Test Plan3,Test Plan2", System.getProperty(SUBPLANS_INCLUDED));
    }

    @Test
    public void executeInvalidSubplans() {
        try {
            ExecutionSummary executionSummary =
                new ExcelBasedTestBuilder()
                    .setPlan("unitTest_Subplan.xlsx", "Test Plan2", "Test Plan4").execute();
        } catch (Exception e) {
            Assert.assertEquals("Test Plan4", System.getProperty(SUBPLANS_OMITTED));
        }
    }

    static {
        System.clearProperty(OPT_RUN_ID);
    }
}
