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

package org.nexial.core

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.RegexUtils
import org.nexial.commons.utils.ResourceUtils
import java.util.*

/**
 * Akin to Java's "MessageBundle" where common text string are externalized, potentially with i18n support. This
 * implementation adds capabilities for token replacements and message hierarchy (subset of messages based on similar
 * key prefix).
 */
class ResourceBundle(private val resources: Properties, private val prefix: String = "") {
    constructor(name: String) : this(ResourceUtils.loadProperties(StringUtils.appendIfMissing(name, ".properties")), "")

    constructor(resources: ResourceBundle, childPrefix: String = "") :
        this(resources.resources, if (resources.prefix != "") "${resources.prefix}.$childPrefix" else childPrefix)

    fun subset(name: String) = ResourceBundle(this, name)

    fun text(key: String, vararg values: Any?): String {
        var msg = resolveText(key)
        if (values.isNotEmpty())
            for (index in 0 until (values.size)) msg = StringUtils.replace(msg, "{${index}}", "" + values[index])
        return msg
    }

    private fun resolveText(key: String): String {
        // if `key` starts with `$`, then use it as is (THIS IS ABSOLUTE KEY REFERENCE)
        // if prefix is defined, then add it to the beginning of `key`
        var msg = resources.getProperty(
            if (StringUtils.startsWith(key, "$")) StringUtils.substringAfter(key, "$")
            else if (prefix == "") key else "$prefix.$key") ?: ""
        val groups = RegexUtils.eagerCollectGroups(msg, "\\{.+?\\}", false, true)
        if (groups.isNotEmpty())
            groups.forEach { group ->
                val token = StringUtils.substringBetween(group, "{", "}")
                if (StringUtils.isNotBlank(token) && token != key && !NumberUtils.isDigits(token))
                    msg = StringUtils.replace(msg, group, resolveText(token))
            }
        return msg
    }
}