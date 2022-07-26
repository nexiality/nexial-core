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

package org.nexial.core.tms.spi.jira

import org.apache.commons.lang3.EnumUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.tms.BDDKeywords
import org.nexial.core.tms.TmsConst.COLOR_END_TEXT
import org.nexial.core.tms.TmsConst.COMMENT_REGEX
import org.nexial.core.tms.TmsConst.DATA_VAR_REGEX
import org.nexial.core.tms.TmsConst.JIRA_COMMENT_COLOR
import org.nexial.core.tms.spi.TmsFormatter
import org.nexial.core.tms.tools.TmsImporter
import org.thymeleaf.TemplateEngine

class JiraFormatter(override var templateEngine: TemplateEngine? = null,
                    override var executionTemplate: String? = null) : TmsFormatter {

    override fun formatTeststepDescription(stepDescription: String): String {
        var step1 = stepDescription
        if (TmsImporter.formatBddKeywords && StringUtils.isNotBlank(step1)) {
            val firstWord = step1.trimStart().substringBefore(" ")

            if (RegexUtils.match(step1, COMMENT_REGEX, true)) {
                step1 = "${JIRA_COMMENT_COLOR}_${step1}_$COLOR_END_TEXT"
            } else if (EnumUtils.isValidEnum(BDDKeywords::class.java, firstWord.uppercase())) {
                // bold BDD keywords
                step1 = step1.replaceFirst(firstWord, "*$firstWord*")
                if (BDDKeywords.AND.keyword == firstWord.uppercase()) step1 = "  $step1"
            } else {
                step1 = "  $step1"
            }
            step1 = RegexUtils.replaceMultiLines(step1, DATA_VAR_REGEX, "\$1{{\$2}}\$3")
        }
        return step1
    }
}
