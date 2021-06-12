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
 */

package org.nexial.core;

import org.junit.Assert;
import org.junit.Test;

import static org.nexial.core.NexialConst.handleWindowsChar;

public class NexialConstTest {

    @Test
    public void treatCommonValueShorthand() {

        Assert.assertNull(NexialConst.treatCommonValueShorthand(null));
        Assert.assertEquals("", NexialConst.treatCommonValueShorthand(""));

        Assert.assertEquals(" ", NexialConst.treatCommonValueShorthand("(blank)"));
        Assert.assertEquals("\t", NexialConst.treatCommonValueShorthand("(tab)"));
        Assert.assertEquals("\n", NexialConst.treatCommonValueShorthand("(eol)"));
        Assert.assertEquals("", NexialConst.treatCommonValueShorthand("(empty)"));

        Assert.assertEquals("   ", NexialConst.treatCommonValueShorthand("(blank)(blank)(blank)"));
        Assert.assertEquals(" \t ", NexialConst.treatCommonValueShorthand("(blank)(tab)(blank)"));
        Assert.assertEquals("\t\t\n ", NexialConst.treatCommonValueShorthand("(tab)(tab)(eol)(blank)"));

        Assert.assertEquals("A", NexialConst.treatCommonValueShorthand("(empty)A"));
        Assert.assertEquals("A(\t)", NexialConst.treatCommonValueShorthand("A((tab))"));
        Assert.assertEquals("  A\n  ", NexialConst.treatCommonValueShorthand("  A\n  "));
    }

    @Test
    public void replaceWindowsChars() {
        Assert.assertEquals(
            "'Nexial' - reflects our desire and intention to perfect the \"art\" and \"science\" of Testing...",
            handleWindowsChar("‘Nexial’ – reflects our desire and intention to perfect the “art” and “science” of Testing…"));
    }
}