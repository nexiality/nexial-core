package org.nexial.core.plugins.base

import org.apache.commons.lang3.StringUtils
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.nexial.core.model.MockExecutionContext

class ClipboardCommandManualTest {
    private val context = MockExecutionContext(true)

    @After
    fun tearDown() {
        context.cleanProject()
    }

    @Test
    fun clipboardCopyTest() {
        val subject = BaseCommand()
        subject.init(context)

        //verify clipboard copy paste
        val clipboardData = "assign this to clipboard"
        subject.copyIntoClipboard(clipboardData)
        subject.copyFromClipboard("c-data")
        val cData = context.getStringData("c-data")
        Assert.assertEquals(clipboardData, cData)
    }

    @Test
    fun clipboardClearTest() {
        val subject = BaseCommand()
        subject.init(context)

        subject.clearClipboard()
        subject.copyFromClipboard("var1")
        val var1 = context.getStringData("var1")
        Assert.assertTrue(StringUtils.isEmpty(var1))
    }
}