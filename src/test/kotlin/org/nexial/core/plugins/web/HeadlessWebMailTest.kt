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

import org.junit.Ignore
import org.junit.Test
import org.nexial.core.ExcelBasedTests

class HeadlessWebMailTest : ExcelBasedTests() {

    @Ignore("need to figure out the best way to sent SMTP mails on Jenkins...")
    @Test
    @Throws(Exception::class)
    fun webmails() {
        val executionSummary = testViaExcel("unitTest_webmail.xlsx", "mailinator", "mailinator_version")
        assertNoFail(executionSummary, "mailinator")
        assertPassFail(executionSummary, "mailinator_version", TestOutcomeStats.allPassed())
        // unable to run on Jenkins/AIX... chrome headless doesn't seem to be redirecting correctly and at times the network request is blocked by temp-mail
        // todo: need to run this locally
        // assertNoFail(executionSummary, "temporary-mail")
    }
}