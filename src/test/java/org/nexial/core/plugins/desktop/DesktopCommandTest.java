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

package org.nexial.core.plugins.desktop;

import org.junit.Assert;
import org.junit.Test;
import org.nexial.core.utils.NativeInputParser;

public class DesktopCommandTest {
    @Test
    public void addShortcut() {
        String expected =
            "<[{Hello how's it going? Why don't we }]><[F2]><[CTRL-SPACE]><[CTRL-END]><[{just take this on right now!}]><[ENTER]>";

        String shortcuts = "";
        shortcuts = DesktopUtils.addShortcut(shortcuts, "Hello ");
        shortcuts = DesktopUtils.addShortcut(shortcuts, "how's it going? ");
        shortcuts = DesktopUtils.addShortcut(shortcuts, "Why don't we ");
        shortcuts = DesktopUtils.addShortcut(shortcuts, "[F2]]");
        shortcuts = DesktopUtils.addShortcut(shortcuts, "[CTRL-SPACE]");
        shortcuts = DesktopUtils.addShortcut(shortcuts, "just take this ");
        shortcuts = DesktopUtils.addShortcut(shortcuts, "on right no");
        shortcuts = DesktopUtils.addShortcut(shortcuts, "w!");
        shortcuts = DesktopUtils.addShortcut(shortcuts, "[ENTER]");
        Assert.assertEquals(expected, shortcuts);
    }

    @Test
    public void parseNativeInput() {

        String parsedKeys = NativeInputParser.handleKeys("[CTRL-ENd]Name[Alt-u-o]");
        Assert.assertEquals("{CTRL}{END}Name{ALT}uo", parsedKeys);

        String parsedKeys1 = NativeInputParser.handleKeys("[CTRL-ENd]Name\n[Alt-u-o]-uo");
        Assert.assertEquals("{CTRL}{END}Name\n{ALT}uo-uo", parsedKeys1);

        String parsedKeys2 = NativeInputParser.handleKeys("[CTRL-ENd]Name\n[Alt-u-o]-[uo]");
        Assert.assertEquals("{CTRL}{END}Name\n{ALT}uo-[uo]", parsedKeys2);

        String parsedKeys3 = NativeInputParser.handleKeys("[CTRL-ENd]Name\n[Alt1-u-o]-[uo]");
        Assert.assertEquals("{CTRL}{END}Name\n[Alt1-u-o]-[uo]", parsedKeys3);

        String parsedKeys4 = NativeInputParser.handleKeys("Hello how's it going? Why don't we [F2][CTRL-SPACE]" +
                                                          "[CTRL-END][just take - this on right now][ENTER]");
        Assert.assertEquals("Hello how's it going? Why don't we {F2}{CTRL}{SPACE}{CTRL}{END}" +
                            "[just take - this on right now]{ENTER}", parsedKeys4);

        String parsedKeys5 = NativeInputParser.handleKeys("[F1]hello to [Mr. Johnson][ENTER][F2]");
        Assert.assertEquals("{F1}hello to [Mr. Johnson]{ENTER}{F2}", parsedKeys5);

        String parsedKeys6 = NativeInputParser.handleKeys("[Alt-Ctrl-Shift-F-F1]");
        Assert.assertEquals("{ALT}{CTRL}{SHIFT}F{F1}", parsedKeys6);

        String parsedKeys7 = NativeInputParser.handleKeys("[Alt-u-o][just take -[ this on right]" +
                                                          " now][Alt-Ctrl-Shift-F-F1]");
        Assert.assertEquals("{ALT}uo[just take -[ this on right] now]{ALT}{CTRL}{SHIFT}F{F1}", parsedKeys7);
    }
}