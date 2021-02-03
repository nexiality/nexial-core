package org.nexial.core.plugins.desktop;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WiniumUtilsTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void toWiniumShortcuts() {
        assertEquals("", WiniumUtils.toWiniumShortcuts(null));
        assertEquals("", WiniumUtils.toWiniumShortcuts(""));
        assertEquals("<[{abc d}]>", WiniumUtils.toWiniumShortcuts("abc d"));
        assertEquals("<[CTRL-a]>", WiniumUtils.toWiniumShortcuts("{CONTROL}a"));
        assertEquals("<[CTRL-a]><[CTRL-X]>", WiniumUtils.toWiniumShortcuts("{CONTROL}a{CTRL}X"));
        assertEquals("<[CTRL-SHIFT-END]><[BACKSPACE]>",
                     WiniumUtils.toWiniumShortcuts("{CONTROL}{SHIFT}{END}{BACKSPACE}"));
        assertEquals("<[ALT-SPACE]><[UP]><[ENTER]><[{\tY}]><[ALT-TAB]><[{ }]>",
                     WiniumUtils.toWiniumShortcuts("{ALT} {UP}{ENTER}\tY{ALT}\t "));
        assertEquals("<[CTRL-ALT-A]><[{Hello}]><[LEFT]><[CTRL-RIGHT]><[{ no!!!}]><[CTRL-a]><[DEL]>",
                     WiniumUtils.toWiniumShortcuts("{CONTROL}{ALT}AHello{LEFT}{CONTROL}{RIGHT} no!!!{CONTROL}a{DELETE}"));

        assertEquals("<[{{}}]><[ALT-A]><[{\t{}]><[LEFT]><[CTRL-RIGHT]><[{}}]><[{{ABC}}]>",
                     WiniumUtils.toWiniumShortcuts("{}{ALT}A\t{{LEFT}{CONTROL}{RIGHT}}{ABC}"));
    }
}