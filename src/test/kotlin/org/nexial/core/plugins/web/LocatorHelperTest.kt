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

package org.nexial.core.plugins.web

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class LocatorHelperTest {
    internal val subject: LocatorHelper = LocatorHelper(WebCommand())

    @Before
    fun init() {
    }

    @After
    fun tearDown() {
    }

    @Test
    @Throws(Exception::class)
    fun testResolveFilteringXPath() {
        Assert.assertEquals("//*[ends-with(@class,'nxl2') and text()='Save' and contains(@id,'save')]",
                            subject.resolveFilteringXPath("class=nxl2*\ntext()=Save\nid=*save*"))

        Assert.assertEquals("//*[ends-with(@class,'nxl2') and text()='Save' and contains(@id,'save')]",
                            subject.resolveFilteringXPath("@class=nxl2*|text()=Save\n" + "@id=*save*"))

        Assert.assertEquals("//*[ends-with(@class,'nxl2') and text()='Save' and contains(@id,'save')]",
                            subject.resolveFilteringXPath("@class=nxl2*|text()=Save\r\n@id=*save*"))

        Assert.assertEquals("//*[starts-with(@class,'cntri') and contains(text(),'Save') and @enabled='true']",
                            subject.resolveFilteringXPath("@class=*cntri|text()=*Save*\r\n@enabled=true"))

        Assert.assertEquals("//*[@id='IF1']", subject.resolveFilteringXPath("id=IF1"))

        Assert.assertEquals("//*[@id='IF1' and @class='entry-title post-title' and @name='TestName']",
                            subject.resolveFilteringXPath("id=IF1\nclass=entry-title post-title\nname=TestName"))
    }

    @Test
    @Throws(Exception::class)
    fun testNormalizeXpathText() {
        // basic
        Assert.assertEquals(subject.normalizeXpathText(""), "''")
        Assert.assertEquals(subject.normalizeXpathText(null), "''")
        Assert.assertEquals(subject.normalizeXpathText("Hello"), "'Hello'")
        Assert.assertEquals(subject.normalizeXpathText("Hello Jimmy Johnson"), "'Hello Jimmy Johnson'")

        // quotes
        Assert.assertEquals(subject.normalizeXpathText("Bob's Pizza"), "concat('Bob',\"'\",'s Pizza')")
        Assert.assertEquals(subject.normalizeXpathText("Review \"Cardholder\"'s Enrollments"),
                            "concat('Review ','\"','Cardholder','\"',\"'\",'s Enrollments')")
        Assert.assertEquals(subject.normalizeXpathText("'Special K'"), "concat(\"'\",'Special K',\"'\")")
        Assert.assertEquals(subject.normalizeXpathText("New's \"flash\""),
                            "concat('New',\"'\",'s ','\"','flash','\"')")
        Assert.assertEquals(subject.normalizeXpathText(" 'Final' \"Space\" "),
                            "concat(' ',\"'\",'Final',\"'\",' ','\"','Space','\"',' ')")
    }

    @Test
    @Throws(Exception::class)
    fun testFixBadXpath() {
        Assert.assertNull(subject.fixBadXpath(null))
        Assert.assertEquals("", subject.fixBadXpath(""))
        Assert.assertEquals("/", subject.fixBadXpath("/"))
        Assert.assertEquals("//", subject.fixBadXpath("//"))
        Assert.assertEquals("//", subject.fixBadXpath(".//"))
        Assert.assertEquals("//", subject.fixBadXpath(" .//"))
        Assert.assertEquals("//", subject.fixBadXpath("    .//"))
        Assert.assertEquals("(//", subject.fixBadXpath("(//"))
        Assert.assertEquals("(//", subject.fixBadXpath("(.//"))
        Assert.assertEquals("(//", subject.fixBadXpath("( .//"))
        Assert.assertEquals("(//", subject.fixBadXpath("( .//"))
    }
}
