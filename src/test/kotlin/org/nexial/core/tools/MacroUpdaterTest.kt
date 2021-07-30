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

package org.nexial.core.tools

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MacroUpdaterTest {
    val options = Options()
        .addOption(CliConst.OPT_VERBOSE)
        .addOption(CliConst.OPT_PREVIEW)
        .addOption(CliUtils.newArgOption("t", "target", "[REQUIRED] The project home to scan", true))
        .addOption(CliUtils.newArgOption("f", "file", "[REQUIRED] The macro file name to scan", true))
        .addOption(CliUtils.newArgOption("s", "sheet", "[REQUIRED] The macro sheet to search in", false))
        .addOption(CliUtils.newArgOption("m", "macroName", "[REQUIRED] The macro name to be refactored", false))

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
    }

    @Test
    fun deriveMacroOptions_simple() {
        val args = arrayOf("-v", "-p", "-t", "myproject", "-f", "myfile.xlsx", "-s", "mysheet", "-m", "oldname=newname")
        val fixture = DefaultParser().parse(options, args)

        val subject = MacroUpdater.deriveMacroOptions(fixture)
        Assert.assertNotNull(subject)
        Assert.assertTrue(subject.preview)
        Assert.assertEquals("myproject", subject.searchFrom)

        val changes = subject.changes
        Assert.assertEquals(1, changes.size)
        Assert.assertEquals("myfile.xlsx", changes[0].fromFile)
        Assert.assertEquals("myfile.xlsx", changes[0].toFile)
        Assert.assertEquals("mysheet", changes[0].fromSheet)
        Assert.assertEquals("mysheet", changes[0].toSheet)
        Assert.assertEquals("oldname", changes[0].fromName)
        Assert.assertEquals("newname", changes[0].toName)
    }

    @Test
    fun deriveMacroOptions_no_name() {

        val args = arrayOf("-v", "-p", "-t", "myproject", "-f", "myfile.xlsx", "-s", "mysheet", "-m", "=")
        val subject = MacroUpdater.deriveMacroOptions(DefaultParser().parse(options, args))
        Assert.assertNotNull(subject)
        Assert.assertTrue(subject.preview)
        Assert.assertEquals("myproject", subject.searchFrom)
        Assert.assertEquals(0, subject.changes.size)

        val args1 = arrayOf("-v", "-p", "-t", "myproject", "-f", "myfile.xlsx", "-s", "mysheet", "-m", "=,=")
        val subject1 = MacroUpdater.deriveMacroOptions(DefaultParser().parse(options, args1))
        Assert.assertNotNull(subject1)
        Assert.assertTrue(subject1.preview)
        Assert.assertEquals("myproject", subject1.searchFrom)
        Assert.assertEquals(0, subject1.changes.size)
    }

    @Test
    fun deriveMacroOptions_two_names() {
        val args = arrayOf("-t", "myproject", "-f", "myfile.xlsx", "-s", "mysheet", "-m", "oldname=newname,another=one")
        assert_two_names(DefaultParser().parse(options, args))
    }

    @Test
    fun deriveMacroOptions_bad_names() {
        val args = arrayOf("-t", "myproject",
                           "-f", "myfile.xlsx",
                           "-s", "mysheet",
                           "-m", "oldname=newname,another=one,,,")
        assert_two_names(DefaultParser().parse(options, args))
    }

    @Test
    fun deriveMacroOptions_dup_names() {
        val args = arrayOf("-t", "myproject",
                           "-f", "myfile.xlsx",
                           "-s", "mysheet",
                           "-m", "oldname=newname,another=one,oldname=newname")
        assert_two_names(DefaultParser().parse(options, args))
    }

    private fun assert_two_names(fixture: CommandLine) {
        val subject = MacroUpdater.deriveMacroOptions(fixture)
        Assert.assertNotNull(subject)
        Assert.assertFalse(subject.preview)
        Assert.assertEquals("myproject", subject.searchFrom)

        val changes = subject.changes
        Assert.assertEquals(2, changes.size)

        Assert.assertEquals("myfile.xlsx", changes[0].fromFile)
        Assert.assertEquals("myfile.xlsx", changes[0].toFile)
        Assert.assertEquals("mysheet", changes[0].fromSheet)
        Assert.assertEquals("mysheet", changes[0].toSheet)
        Assert.assertEquals("oldname", changes[0].fromName)
        Assert.assertEquals("newname", changes[0].toName)

        Assert.assertEquals("myfile.xlsx", changes[1].fromFile)
        Assert.assertEquals("myfile.xlsx", changes[1].toFile)
        Assert.assertEquals("mysheet", changes[1].fromSheet)
        Assert.assertEquals("mysheet", changes[1].toSheet)
        Assert.assertEquals("another", changes[1].fromName)
        Assert.assertEquals("one", changes[1].toName)
    }
}