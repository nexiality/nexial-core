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
package org.nexial.core.reports

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.junit.Assert
import org.junit.Test
import org.nexial.core.ExcelBasedTests

class ExecutionSummaryTests : ExcelBasedTests() {

    @Test
    @Throws(Exception::class)
    fun testExecutionSummaryStepDetails() {
        val executionSummary = testViaExcel("unitTest_execution_summary.xlsx", "Compare")!!
        assertPassFail(executionSummary, "Compare", TestOutcomeStats(3, 1))
        val nestedExecutions =
            executionSummary.nestedExecutions[0].nestedExecutions[0].nestedExecutions[0].nestedExecutions[0]

        val stepDetails = nestedExecutions.stepDetails
        Assert.assertTrue(CollectionUtils.isNotEmpty(stepDetails))
        Assert.assertEquals(3, stepDetails.size)

        Assert.assertEquals(1, stepDetails[0].nestedMessages.size)
        Assert.assertFalse(stepDetails[0].isPass)

        val stepDetails1 = stepDetails[1]
        Assert.assertNotNull(stepDetails1)
        val nestedMessages = stepDetails1.nestedMessages
        Assert.assertNotNull(nestedMessages)
        Assert.assertEquals(3, nestedMessages.size)
        Assert.assertEquals("alt", nestedMessages[0].fileType)
        Assert.assertEquals("alt", nestedMessages[1].fileType)
        Assert.assertTrue(StringUtils.isEmpty(nestedMessages[2].file))
        Assert.assertFalse(stepDetails1.isPass)

        val stepDetails2 = stepDetails[2]
        Assert.assertNotNull(stepDetails2)
        val nestedMessages1 = stepDetails2.nestedMessages
        Assert.assertNotNull(nestedMessages1)
        Assert.assertEquals("csv", stepDetails2.nestedMessages[1].fileType)
        Assert.assertFalse(stepDetails2.isPass)
    }
}