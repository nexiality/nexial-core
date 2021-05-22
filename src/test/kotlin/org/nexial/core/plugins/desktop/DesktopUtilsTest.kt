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
    }
}