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

package org.nexial.core

import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import kotlin.streams.toList

/**
 * constants repo. to manage/track system variables
 */
object SystemVariables {

    // global defaults, to be registered from the definition of each default values
    private val SYSVARS = mutableMapOf<String, Any?>()
    private val SYSVARGROUPS = mutableListOf<String>()

    @JvmStatic
    fun registerSysVar(name: String): String {
        if (StringUtils.isNotBlank(name)) SYSVARS[name] = null
        return name
    }

    @JvmStatic
    fun <T> registerSysVar(name: String, value: T): String {
        if (StringUtils.isNotBlank(name)) SYSVARS[name] = value
        return name
    }

    @JvmStatic
    fun registerSysVarGroup(group: String): String {
        if (StringUtils.isNotBlank(group) && !SYSVARGROUPS.contains(group)) SYSVARGROUPS.add(group)
        return group
    }

    @JvmStatic
    fun getDefault(name: String): String? =
            if (SYSVARS.containsKey(name)) if (SYSVARS[name] == null) null else SYSVARS[name].toString() else null

    @JvmStatic
    fun getDefaultBool(name: String): Boolean {
        require(SYSVARS.containsKey(name)) { "No default configured for '$name'" }
        return BooleanUtils.toBoolean(SYSVARS[name].toString())
    }

    @JvmStatic
    fun getDefaultInt(name: String): Int {
        require(SYSVARS.containsKey(name)) { "No default value configured for '$name'" }
        return NumberUtils.toInt(SYSVARS[name].toString())
    }

    @JvmStatic
    fun getDefaultLong(name: String): Long {
        require(SYSVARS.containsKey(name)) { "No default configured for '$name'" }
        return NumberUtils.toLong(SYSVARS[name].toString())
    }

    @JvmStatic
    fun getDefaultFloat(name: String): Double {
        require(SYSVARS.containsKey(name)) { "No default configured for '$name'" }
        return NumberUtils.toFloat(SYSVARS[name].toString()).toDouble()
    }

    @JvmStatic
    fun getDefaultDouble(name: String): Double {
        require(SYSVARS.containsKey(name)) { "No default configured for '$name'" }
        return NumberUtils.toDouble(SYSVARS[name].toString())
    }

    @JvmStatic
    fun isRegisteredSysVar(name: String): Boolean {
        if (SYSVARS.containsKey(name)) return true
        for (group in SYSVARGROUPS) {
            if (StringUtils.startsWith(name, group)) return true
        }
        return false
    }

    @JvmStatic
    fun listSysVars(): List<String> = SYSVARS.keys.stream().sorted().toList()
}
