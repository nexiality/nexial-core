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
    private val SYS_VARS = mutableMapOf<String, Any?>()
    private val SYS_VAR_GROUPS = mutableListOf<String>()

    @JvmStatic
    fun registerSysVar(name: String): String {
        if (StringUtils.isNotBlank(name)) SYS_VARS[name] = null
        return name
    }

    @JvmStatic
    fun <T> registerSysVar(name: String, value: T): String {
        if (StringUtils.isNotBlank(name)) SYS_VARS[name] = value
        return name
    }

    @JvmStatic
    fun registerSysVarGroup(group: String): String {
        if (StringUtils.isNotBlank(group) && !SYS_VAR_GROUPS.contains(group)) SYS_VAR_GROUPS.add(group)
        return group
    }

    @JvmStatic
    fun getDefault(name: String): String? =
            if (SYS_VARS.containsKey(name)) if (SYS_VARS[name] == null) null else SYS_VARS[name].toString() else null

    @JvmStatic
    fun getDefaultBool(name: String): Boolean {
        require(SYS_VARS.containsKey(name)) { "No default configured for '$name'" }
        return BooleanUtils.toBoolean(SYS_VARS[name].toString())
    }

    @JvmStatic
    fun getDefaultInt(name: String): Int {
        require(SYS_VARS.containsKey(name)) { "No default value configured for '$name'" }
        return NumberUtils.toInt(SYS_VARS[name].toString())
    }

    @JvmStatic
    fun getDefaultLong(name: String): Long {
        require(SYS_VARS.containsKey(name)) { "No default configured for '$name'" }
        return NumberUtils.toLong(SYS_VARS[name].toString())
    }

    @JvmStatic
    fun getDefaultFloat(name: String): Double {
        require(SYS_VARS.containsKey(name)) { "No default configured for '$name'" }
        return NumberUtils.toFloat(SYS_VARS[name].toString()).toDouble()
    }

    @JvmStatic
    fun getDefaultDouble(name: String): Double {
        require(SYS_VARS.containsKey(name)) { "No default configured for '$name'" }
        return NumberUtils.toDouble(SYS_VARS[name].toString())
    }

    @JvmStatic
    fun isRegisteredSysVar(name: String): Boolean {
        if (SYS_VARS.containsKey(name)) return true
        for (group in SYS_VAR_GROUPS) {
            if (StringUtils.startsWith(name, group)) return true
        }
        return false
    }

    @JvmStatic
    fun listSysVars(): List<String> = SYS_VARS.keys.stream().sorted().toList()
}
