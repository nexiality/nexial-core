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

package org.nexial.core.utils

import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.utils.KeystrokeParser.FUNCTION_KEYS
import org.nexial.core.utils.KeystrokeParser.MODIFIERS

object NativeInputParser {
    @JvmStatic
    fun handleKeys(keys: String) = toKeystrokes(keys)

    private fun isKey(key: String) = MODIFIERS.containsKey(key) || FUNCTION_KEYS.containsKey(key)

    private fun toKeystrokes(keys: String): String {
        var keystrokes = ""
        var allKeys = keys

        while (allKeys != "") {
            allKeys = if (allKeys.contains(Regex("\\[.+]"))) {
                // get first matching {.+}
                val substring = StringUtils.substringBetween(allKeys, "[", "]")

                // get all keys before [
                keystrokes += StringUtils.substringBefore(allKeys, "[$substring]")

                val keyStroke = "{${substring.uppercase()}}"
                keystrokes += if (isKey(keyStroke)) keyStroke else parseKeys(substring)

                StringUtils.substringAfter(allKeys, "[$substring]")
            } else {
                keystrokes += allKeys
                ""
            }
        }
        return keystrokes
    }

    // parse combined keys e.g. {Alt-u-o}
    private fun parseKeys(keys: String): String {
        var containsKey = false
        var keystrokes = ""

        TextUtils.toList(keys, "-", true).forEach { key ->
            val keystroke = "{${key.uppercase()}}"
            keystrokes += if (isKey(keystroke)) {
                containsKey = true
                keystroke
            } else {
                key
            }
        }

        // return keys as it is if it is normal text i.e. without any keys
        return if (containsKey) keystrokes else "[$keys]"
    }
}
