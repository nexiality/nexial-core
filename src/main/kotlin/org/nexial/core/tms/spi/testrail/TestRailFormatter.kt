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


package org.nexial.core.tms.spi.testrail

import org.apache.commons.lang3.EnumUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.tms.BDDKeywords

import org.nexial.core.tms.spi.TmsFormatter
import org.nexial.core.tms.tools.TmsImporter
import org.thymeleaf.TemplateEngine

class TestRailFormatter(override var templateEngine: TemplateEngine? = null,
                        override var executionTemplate: String? = null) : TmsFormatter {

    /**
    * Format the custom step description on the basis of the [BDDKeywords]
    *
    * @param stepDescription a single custom test step
    * @return a formatted custom step
    */

    override fun formatTeststepDescription(stepDescription: String): String {
        if (TmsImporter.formatBddKeywords && StringUtils.isNotBlank(stepDescription)) {
            val firstWord = stepDescription.trim().substringBefore(" ").uppercase()
            if (EnumUtils.isValidEnum(BDDKeywords::class.java, firstWord) && firstWord == BDDKeywords.AND.keyword) {
               return " $stepDescription"
            }
        }
        return stepDescription
    }
}
