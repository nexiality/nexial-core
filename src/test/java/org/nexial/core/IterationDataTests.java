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

import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.model.ExecutionSummary;

public class IterationDataTests extends ExcelBasedTests {
    @Test
    public void fallback_true_contiguous_iterations() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_IterationDataTests.xlsx",
                                                         "fallback_true_multi_iter");
        assertNoFail(executionSummary, "fallback_true_multi_iter");
        Assert.assertEquals(0, executionSummary.getFailCount());

    }

    @Test
    public void fallback_false_contiguous_iterations() throws Exception {
        ExecutionSummary executionSummary = testViaExcel("unitTest_IterationDataTests.xlsx",
                                                         "fallback_false_multi_iter");
        assertNoFail(executionSummary, "fallback_false_multi_iter");
        Assert.assertEquals(0, executionSummary.getFailCount());
    }

    @Test
    public void fallback_false_disparate_iterations() throws Exception {
        System.setProperty("Var5", "Still the one");
        ExecutionSummary executionSummary = testViaExcel("unitTest_IterationDataTests.xlsx",
                                                         "fallback_false_split_iter");
        assertNoFail(executionSummary, "fallback_false_split_iter");
        Assert.assertEquals(0, executionSummary.getFailCount());
    }

    @Test
    public void dynamic_datasheet() throws Exception {
        ExecutionSummary executionSummary = testPlanViaExcel("unitTest_Iteration_Dynamic_DataSheet.xlsx");
        Assert.assertEquals(0, executionSummary.getFailCount());
    }
}
