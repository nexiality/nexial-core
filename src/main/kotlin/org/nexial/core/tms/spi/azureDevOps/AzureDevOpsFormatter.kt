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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.tms.spi.azureDevOps

import org.apache.commons.lang3.EnumUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.core.tms.TmsConst.COMMENT_COLOR
import org.nexial.core.tms.TmsConst.COMMENT_REGEX
import org.nexial.core.tms.TmsConst.DATA_VAR_BACKGROUND_COLOR
import org.nexial.core.tms.TmsConst.DATA_VAR_REGEX
import org.nexial.core.tms.model.BDDKeywords
import org.nexial.core.tms.spi.TmsFormatter
import org.nexial.core.tms.tools.TmsImporter
import org.thymeleaf.TemplateEngine

class AzureDevOpsFormatter(override var templateEngine: TemplateEngine? = null,
    override var executionTemplate: String? = null) : TmsFormatter {

    val paddingStyleDiv = "<div style='padding-left:10px'>"
    val endDiv = "</div>"

    override fun formatTeststepDescription(stepDescription: String): String {
        var step1 = stepDescription
        if (TmsImporter.formatBddKeywords && StringUtils.isNotBlank(step1)) {
            step1 = RegexUtils.replaceMultiLines(step1, DATA_VAR_REGEX,
                "\$1<code style='background-color: $DATA_VAR_BACKGROUND_COLOR;'><i>\$2</i></code>\$3")

            val firstWord = step1.trimStart().substringBefore(" ")

            if (RegexUtils.match(step1, COMMENT_REGEX, true)) {
                step1 =
                    "$paddingStyleDiv<code style='color:$COMMENT_COLOR;'><i>$step1</i></code>$endDiv"
            } else if (EnumUtils.isValidEnum(BDDKeywords::class.java, firstWord.uppercase())) {
                // bold BDD keywords
                step1 = step1.replaceFirst(firstWord, "<b>$firstWord</b>")
                if (firstWord.uppercase() == BDDKeywords.AND.keyword) {
                    step1 = "$paddingStyleDiv$step1$endDiv"
                }
            } else {
                step1 = "$paddingStyleDiv$step1$endDiv"
            }
            return step1.replace("\n", "<br/>")
        }
        return step1
    }
}
