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

package org.nexial.core.plugins.io;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.ExcelBasedTests;
import org.nexial.core.model.ExecutionSummary;

public class HeadlessIOTests extends ExcelBasedTests {
    @Test
    public void alltests() throws Exception {
        // System.setProperty(OUTPUT_TO_CLOUD, "false");
        // System.setProperty(OPT_RUN_ID_PREFIX, "unitTest_io");
        // System.setProperty(OPT_OPEN_RESULT, "off");

        ExecutionSummary executionSummary = testViaExcel("unitTest_io.xlsx",
                                                         "filter",
                                                         "saveMatches",
                                                         "saveMatches_2",
                                                         "compareExtended",
                                                         "checksum");
        Assert.assertEquals(0, executionSummary.getFailCount());
    }
}
