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

package org.nexial.core.plugins.bai2

import org.apache.commons.lang3.StringUtils
import org.nexial.core.plugins.bai2.BaiFile.Companion.isNumeric

object Validations {

    val validateNumeric: (Map<String, String>, String) -> String = { record, field ->
        if (!isNumeric(record.getValue(field))) {
            "Transaction: $field:  ${record[field]} is not Numeric"
        } else {
            ""
        }
    }

    val validateAlphanumeric: (Map<String, String>, String) -> String = { record, field ->
        if (!StringUtils.isAlphanumeric(record.getValue(field))) {
            "Transaction: $field:  ${record[field]} is not Alphanumeric"
        } else {
            ""
        }
    }

    val validateAsciiPrintable: (Map<String, String>, String) -> String = { record, field ->
        if (!StringUtils.isAsciiPrintable(record.getValue(field))) {
            "Transaction: $field:  ${record[field]} is not Alphanumeric"
        } else {
            ""
        }
    }

    fun validate(records: Map<String, String>,
                 vararg validations: Pair<String, (Map<String, String>, String) -> String>): MutableList<String> {
        val errors: MutableList<String> = mutableListOf()
        validations.forEach { pair ->
            run {
                val field = pair.first
                val validation = pair.second
                val errorMessage = validation(records, field)
                if (StringUtils.isNotBlank(errorMessage)) errors.add(errorMessage)
            }
        }
        return errors
    }
}