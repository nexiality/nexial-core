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

package org.nexial.core.plugins.web

import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.MockExecutionContext

class LocatorHelperTest {
    internal val mockContext = MockExecutionContext(true)
    internal val delegator:WebCommand
    internal val subject: LocatorHelper

    init {
        delegator= object : WebCommand() {
            override fun getContext(): ExecutionContext = mockContext
        }

        subject = LocatorHelper(delegator)
    }

    @Before
    fun init() {
    }

    @After
    fun tearDown() {
    }

    @Test
    @Throws(Exception::class)
    fun testResolveFilteringXPath() {
        assertEquals("//*[ends-with(@class,'nxl2') and text()='Save' and contains(@id,'save')]",
                            subject.resolveFilteringXPath("class=nxl2*\ntext()=Save\nid=*save*"))

        assertEquals("//*[ends-with(@class,'nxl2') and text()='Save' and contains(@id,'save')]",
                            subject.resolveFilteringXPath("@class=nxl2*|text()=Save\n" + "@id=*save*"))

        assertEquals("//*[ends-with(@class,'nxl2') and text()='Save' and contains(@id,'save')]",
                            subject.resolveFilteringXPath("@class=nxl2*|text()=Save\r\n@id=*save*"))

        assertEquals("//*[starts-with(@class,'cntri') and contains(text(),'Save') and @enabled='true']",
                            subject.resolveFilteringXPath("@class=*cntri|text()=*Save*\r\n@enabled=true"))

        assertEquals("//*[@id='IF1']", subject.resolveFilteringXPath("id=IF1"))

        assertEquals("//*[@id='IF1' and @class='entry-title post-title' and @name='TestName']",
                            subject.resolveFilteringXPath("id=IF1\nclass=entry-title post-title\nname=TestName"))
    }

    @Test
    @Throws(Exception::class)
    fun testNormalizeXpathText() {
        // basic
        assertEquals(LocatorHelper.normalizeXpathText(""), "''")
        assertEquals(LocatorHelper.normalizeXpathText(null), "''")
        assertEquals(LocatorHelper.normalizeXpathText("Hello"), "'Hello'")
        assertEquals(LocatorHelper.normalizeXpathText("Hello Jimmy Johnson"), "'Hello Jimmy Johnson'")

        // quotes
        assertEquals(LocatorHelper.normalizeXpathText("Bob's Pizza"), "concat('Bob',\"'\",'s Pizza')")
        assertEquals(LocatorHelper.normalizeXpathText("Review \"Cardholder\"'s Enrollments"),
                            "concat('Review ','\"','Cardholder','\"',\"'\",'s Enrollments')")
        assertEquals(LocatorHelper.normalizeXpathText("'Special K'"), "concat(\"'\",'Special K',\"'\")")
        assertEquals(LocatorHelper.normalizeXpathText("New's \"flash\""),
                            "concat('New',\"'\",'s ','\"','flash','\"')")
        assertEquals(LocatorHelper.normalizeXpathText(" 'Final' \"Space\" "),
                            "concat(' ',\"'\",'Final',\"'\",' ','\"','Space','\"',' ')")
    }

    @Test
    @Throws(Exception::class)
    fun testFixBadXpath() {
        Assert.assertNull(LocatorHelper.fixBadXpath(null))
        assertEquals("", LocatorHelper.fixBadXpath(""))
        assertEquals("/", LocatorHelper.fixBadXpath("/"))
        assertEquals("//", LocatorHelper.fixBadXpath("//"))
        assertEquals("//", LocatorHelper.fixBadXpath(".//"))
        assertEquals("//", LocatorHelper.fixBadXpath(" .//"))
        assertEquals("//", LocatorHelper.fixBadXpath("    .//"))
        assertEquals("(//", LocatorHelper.fixBadXpath("(//"))
        assertEquals("(//", LocatorHelper.fixBadXpath("(.//"))
        assertEquals("(//", LocatorHelper.fixBadXpath("( .//"))
        assertEquals("(//", LocatorHelper.fixBadXpath("( .//"))
    }
}
