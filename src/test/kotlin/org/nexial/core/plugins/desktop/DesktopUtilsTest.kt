package org.nexial.core.plugins.desktop

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class DesktopUtilsTest {

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun joinShortcuts() {
        assertEquals("", DesktopUtils.joinShortcuts(""))
        assertEquals("<[{a}]>", DesktopUtils.joinShortcuts("a"))
        assertEquals("<[{abc d}]>", DesktopUtils.joinShortcuts("abc d"))
        assertEquals("<[{abc d}]>", DesktopUtils.joinShortcuts("abc", " ", "d"))
        assertEquals("<[{abc }]><[ENTER]><[{d}]>", DesktopUtils.joinShortcuts("abc", " ", "[ENTER]", "d"))
        assertEquals("<[{abc }]><[ENTER]><[{d}]><[TAB]><[ESCAPE]>",
                     DesktopUtils.joinShortcuts("abc", " ", "[ENTER]", "d", "[TAB][ESCAPE]"))
        assertEquals("<[{abc }]><[ENTER]><[{d}]><[TAB]><[ESCAPE]><[{.}]>",
                     DesktopUtils.joinShortcuts("abc", " ", "[ENTER]", "d", "[TAB][ESCAPE]", "."))
        assertEquals("<[ENTER]>", DesktopUtils.joinShortcuts("[ENTER]"))
        assertEquals("<[ENTER]><[{a}]><[TAB]><[{ }]><[TAB]><[ENTER]><[{ppa}]>",
                     DesktopUtils.joinShortcuts("[ENTER]a[TAB] [TAB][ENTER]ppa"))

        // test newline characters
        assertEquals("<[{This is}]><[ENTER]>" +
                     "<[{a Test}]><[ENTER]>" +
                     "<[{A test, I said!!}]><[ENTER]>" +
                     "<[ENTER]>" +
                     "<[{Bye}]>",
                     DesktopUtils.joinShortcuts("This is\na Test\nA test, I said!!\n\nBye"))
        assertEquals("<[{When I say}]><[ENTER]>" +
                     "<[{ENTER}]><[ENTER]>" +
                     "<[{You press it, like this:}]><[ENTER]>" +
                     "<[ENTER]>",
                     DesktopUtils.joinShortcuts("When I say\nENTER\nYou press it, like this:\n\n"))
    }
}