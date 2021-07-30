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