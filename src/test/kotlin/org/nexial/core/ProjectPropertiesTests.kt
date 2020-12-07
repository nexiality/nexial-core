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