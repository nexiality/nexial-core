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

package org.nexial.core.plugins.json

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE
import java.io.Serializable
import java.util.*

class JsonComparisonResult : Serializable {
    val differences: MutableList<Difference> = LinkedList()

    data class Difference(var node: String?, val expected: String, val actual: String) : Serializable {

        override fun toString(): String {
            return ToStringBuilder(this, MULTI_LINE_STYLE)
                .append("node", node)
                .append("expected", expected)
                .append("actual", actual)
                .toString()
        }

        fun toJson(): JsonObject {
            val json = JsonObject()
            json.addProperty("node", node)
            json.addProperty("expected", expected)
            json.addProperty("actual", actual)
            return json
        }

        fun asList(): List<String?> = listOf(node, expected, actual)
    }

    fun differenceCount() = differences.size

    fun hasDifferences() = differences.size > 0

    fun addDifference(diff: Difference): JsonComparisonResult {
        this.differences.add(diff)
        return this
    }

    fun addDifferences(results: JsonComparisonResult?): JsonComparisonResult {
        if (results != null && CollectionUtils.isNotEmpty(results.differences)) differences.addAll(results.differences)
        return this
    }

    override fun toString() = differences.toString()

    fun toJson(): JsonArray {
        val json = JsonArray()
        differences.forEach { difference -> json.add(difference.toJson()) }
        return json
    }

    fun toList() = differences.map { it.asList() }
}
