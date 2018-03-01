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

import static org.nexial.core.NexialConst.Data.OPT_OPEN_RESULT;
import static org.nexial.core.NexialConst.OPT_RUN_ID_PREFIX;
import static org.nexial.core.NexialConst.OUTPUT_TO_CLOUD;

public class HeadlessJsonTests extends ExcelBasedTests {

    @Test
    public void jsonCommandTests() throws Exception {
        System.setProperty(OUTPUT_TO_CLOUD, "true");
        System.setProperty(OPT_RUN_ID_PREFIX, "unitTest_json");
        System.setProperty(OPT_OPEN_RESULT, "off");

        ExecutionSummary executionSummary = testViaExcel("unitTest_json.xlsx");
        System.out.println("executionSummary = " + executionSummary);
        assertPassFail(executionSummary, "jsonpath1", TestOutcomeStats.allPassed());
        assertPassFail(executionSummary, "bad_jsonpath", TestOutcomeStats.allPassed());
    }
}
