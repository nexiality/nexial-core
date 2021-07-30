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

package org.nexial.core

import org.junit.*
import org.nexial.core.NexialConst.Data.ENV_NAME

class ProjectPropertiesTests : ExcelBasedTests() {

    @Before
    fun setUp() = VOLATILE_PROPS.forEach { System.clearProperty(it) }

    @After
    fun tearDown() = VOLATILE_PROPS.forEach { System.clearProperty(it) }

    @Test
    fun test_UNITTEST_env() {
        System.setProperty(ENV_NAME, "UNITTEST")
        val executionSummary = testViaExcel("unitTest_project_properties.xlsx")
        assertPassFail(executionSummary, "env", TestOutcomeStats.allPassed())
        Assert.assertEquals(0, executionSummary.failCount.toLong())
    }

    @Test
    fun test_UnitTesting_env() {
        System.setProperty(ENV_NAME, "UnitTesting")
        val executionSummary = testViaExcel("unitTest_project_properties.xlsx")
        assertPassFail(executionSummary, "env", TestOutcomeStats.allPassed())
        Assert.assertEquals(0, executionSummary.failCount.toLong())
    }

    companion object {
        val VOLATILE_PROPS = listOf(
            ENV_NAME, "nexial.delayBetweenStepsMs", "TabularMenu.Name", "Limit",
            "Default.Name", "Subject.Name"
        )

        @BeforeClass
        fun init() = VOLATILE_PROPS.forEach { System.clearProperty(it) }

        @AfterClass
        fun destroy() = VOLATILE_PROPS.forEach { System.clearProperty(it) }
    }
}