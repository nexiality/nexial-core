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

package org.nexial.core.tms.spi

import org.nexial.core.tms.model.TmsTestCase
import org.nexial.core.tms.model.TmsTestStep
import org.nexial.core.tms.tools.TmsImporter
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

interface TmsFormatter {
    var templateEngine: TemplateEngine?
    var executionTemplate: String?

    /**
     * Returns formatted text/object depending on TMS tools from tmsTestSteps [TmsTestStep] by formatting
     * BDDKeywords if [TmsImporter.formatBddKeywords] is true to update testcase.
     *
     * @param tmsTestSteps [List] of [TmsTestStep] to format
     * @return formatted [Any] object of testcase to update testcases
     */
    fun formatTestSteps(testCase: TmsTestCase): String {
        val engineContext = Context()
        engineContext.setVariable("formatter", this)
        engineContext.setVariable("testCase", testCase)
        return templateEngine!!.process(executionTemplate!!, engineContext)
    }

    fun formatNewLine(rowDes: String?) = rowDes?.replace("\r\n", "\\n")?.replace("\n", "\\n")


    /**
     * Returns test step description well formatted with BDDKeywords. For HTML supporting tms
     * Test step description is formatted with BDDKeywords if [TmsImporter.formatBddKeywords] is true
     *
     * @param stepDescription [String] description of the test step from script file
     * @return [String] formatted test step description
     */
    fun formatTeststepDescription(stepDescription: String): String
}