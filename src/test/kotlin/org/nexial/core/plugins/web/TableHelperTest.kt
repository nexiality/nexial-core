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

import org.junit.Assert
import org.junit.Test
import org.nexial.core.NexialConst.Data.WEB_CSV_TRIM
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.MockExecutionContext

class TableHelperTest {

    @Test
    fun csvSafe() {
        val context = MockExecutionContext(true)
        context.setData(WEB_CSV_TRIM, true)

        val webCommand = object : WebCommand() {
            override fun getContext(): ExecutionContext = context
        }

        val subject = TableHelper(webCommand)

        Assert.assertEquals("", subject.csvSafe(""))
        Assert.assertEquals("", subject.csvSafe(" "))
        Assert.assertEquals("", subject.csvSafe("\t"))
        Assert.assertEquals("", subject.csvSafe("\n"))
        Assert.assertEquals("", subject.csvSafe("\r\n"))
        Assert.assertEquals("", subject.csvSafe("\r\n "))
        Assert.assertEquals("", subject.csvSafe(" \r\n"))
        Assert.assertEquals("", subject.csvSafe(" \r\n "))
        Assert.assertEquals("", subject.csvSafe(" \t\n "))

        Assert.assertEquals("Net (Take-Home)", subject.csvSafe("Net\n(Take-Home) "))
        Assert.assertEquals("Net (Take-Home)", subject.csvSafe("Net\n (Take-Home) "))
        Assert.assertEquals("Net (Take-Home)", subject.csvSafe("Net \n(Take-Home) "))
        Assert.assertEquals("Net (Take-Home)", subject.csvSafe("Net \n (Take-Home) "))
        Assert.assertEquals("Net (Take-Home)", subject.csvSafe("\tNet \r\n (Take-Home) "))
        Assert.assertEquals("Net (Take-Home)", subject.csvSafe("\tNet \r (Take-Home)\t\t"))

        Assert.assertEquals("3,21.56", subject.csvSafe("\t3,21.56\t\t"))
        Assert.assertEquals("a,b,c, d, e", subject.csvSafe("\ta,b,c,\td,\ne\t\t"))
        Assert.assertEquals("Tom, the \"tom-tom\" guide", subject.csvSafe("\tTom, the \"tom-tom\" guide\t\t"))

        context.setData(WEB_CSV_TRIM, false)
        Assert.assertEquals(" Tom, the \"tom-tom\" guide  ",
                            subject.csvSafe("\tTom, the \"tom-tom\" guide\t\t"))
        Assert.assertEquals("15,22.1", subject.csvSafe("15,22.1"))
        Assert.assertEquals("15,22.1  ", subject.csvSafe("15,22.1  \t"))
    }
}