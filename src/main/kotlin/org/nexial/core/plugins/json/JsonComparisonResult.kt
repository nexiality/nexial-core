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
    var expected: String? = null
    var actual: String? = null

    val differences: MutableList<Difference> = LinkedList()

    data class Difference(var expectedNode: String?, var actualNode: String?, val message: String) : Serializable {
        override fun toString(): String {
            return ToStringBuilder(this, MULTI_LINE_STYLE)
                .append("expectedNode", expectedNode)
                .append("actualNode", actualNode)
                .append("message", message)
                .toString()
        }

        fun toJson(): JsonObject {
            val json = JsonObject()
            json.addProperty("expectedNode", expectedNode)
            json.addProperty("actualNode", actualNode)
            json.addProperty("message", message)
            return json
        }
    }

    fun differenceCount() = differences.size

    fun hasDifferences() = differences.size > 0

    fun addDifference(diff: Difference): JsonComparisonResult {
        this.differences.add(diff)
        return this
    }

    fun addDifferences(diffs: List<Difference>): JsonComparisonResult {
        if (CollectionUtils.isNotEmpty(diffs)) this.differences.addAll(diffs)
        return this
    }

    fun addDifferences(results: JsonComparisonResult?): JsonComparisonResult {
        return if (results != null) addDifferences(results.differences) else this
    }

    override fun toString() = differences.toString()

    fun toJson(): JsonArray {
        val json = JsonArray()
        differences.forEach { difference -> json.add(difference.toJson()) }
        return json
    }
}
