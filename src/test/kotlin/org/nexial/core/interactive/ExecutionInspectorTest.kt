package org.nexial.core.interactive

import org.apache.commons.lang3.StringUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.interactive.ExecutionInspector.Companion.regexDesktopVar

class ExecutionInspectorTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun testDesktopRegex() {

        // inspection only
        val fixtures = listOf(
                "/*[@ClassName='MyApp']",
                "/*[@ClassName='WindowClass32' and contains(@Name,'PTE System Menu')]"
        )
        fixtures.forEach {
            val fixture = "DESKTOP($it)"
            println("testing $fixture")

            assertTrue(RegexUtils.isExact(fixture, regexDesktopVar))
            val groups = RegexUtils.collectGroups(fixture, regexDesktopVar)
            assertNotNull(groups)
            assertEquals(5, groups.size)
            assertEquals(it, groups[0])
        }

        // action with no input
        val fixtures2 = mapOf(
                Pair("/*[@ClassName='WindowClass32' and contains(@Name,'PTE System Menu')]/*[@ControlType='ControlType.TitleBar' and @AutomationId='TitleBar'])",
                     "doubleClick"),
                Pair("/*[@ClassName='WindowClass32' and @Name='Application)])", "click")
        )
        fixtures2.forEach { (key, value) ->
            val fixture = "DESKTOP($key) => $value"
            println("testing $fixture")

            assertTrue(RegexUtils.isExact(fixture, regexDesktopVar))
            val groups = RegexUtils.collectGroups(fixture, regexDesktopVar)
            assertNotNull(groups)
            assertEquals("expects 5 groups captured", 5, groups.size)
            assertEquals(key, groups[0])
            assertEquals(value, groups[2])
        }

        // action with input(s)
        val fixtures3 = mapOf(
                Pair("/*[@ClassName='WindowClass32']/*[@AutomationId='TitleBar'])", "click"),
                Pair("/*[@ClassName='WindowClass32']/*[@ControlType='ControlType.TitleBar' and @AutomationId='TitleBar'])",
                     "type,Hello"),
                Pair("/*[@ClassName='WindowClass32' and @Name='Application)])", "type,Hi{TAB}there!,What's up?!")
        )
        fixtures3.forEach { (key, value) ->
            val action = StringUtils.substringBefore(value, ",")
            val fixture = "DESKTOP($key) => $action(${StringUtils.substringAfter(value, ",")})"
            println("testing $fixture")

            assertTrue(RegexUtils.isExact(fixture, regexDesktopVar))
            val groups = RegexUtils.collectGroups(fixture, regexDesktopVar)
            assertNotNull(groups)
            assertEquals("expects 5 groups captured", 5, groups.size)
            assertEquals(key, groups[0])
            assertEquals(action, groups[2])
            assertEquals(StringUtils.substringAfter(value, ","), StringUtils.defaultString(groups[4]))
        }


    }
}