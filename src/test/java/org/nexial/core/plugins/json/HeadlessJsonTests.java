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

package org.nexial.core.plugins.json;

import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;

public class HeadlessJsonTests extends ExcelBasedTests {
    @Test
    public void jsonCommandTests() throws Exception {
        // ExecutionSummary executionSummary = testViaExcel("unitTest_json.xlsx", "jsonpath1");
        ExecutionSummary executionSummary = testViaExcel("unitTest_json.xlsx");
        System.out.println("executionSummary = " + executionSummary);
        assertPassFail(executionSummary, "validation", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "bad_jsonpath", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "json_compare", new TestOutcomeStats(2, 26));
        assertPassFail(executionSummary, "read_json", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "beautification", TestOutcomeStats.allPassed());
    }
}
