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
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle.JSON_STYLE
import org.nexial.commons.utils.TextUtils
import org.nexial.core.plugins.json.JsonComparisonResult.Difference
import org.nexial.core.plugins.json.JsonMeta.Type.*
import org.nexial.core.utils.JsonUtils
import java.util.*

/**
 * json elemental structure, either array or name/value pair, and metadata
 */
data class JsonMeta(val type: Type, var name: String?, var node: String?) {

    enum class Type {
        ARRAY, OBJECT, PRIMITIVE
    }

    // applicable to all types, for array and object this would be reference to `children`
    var value: Any? = null
    var parent: JsonMeta? = null
    val children: MutableList<JsonMeta> = mutableListOf()
    val isArray = type == ARRAY
    val isLeaf = type == PRIMITIVE
    val isObject = type == OBJECT

    fun compare(actual: JsonMeta?): JsonComparisonResult {
        val result = JsonComparisonResult()

        if (actual == null) {
            return result.addDifference(Difference(node, null, "ACTUAL is null/empty but EXPECTED is not"))
        }

        if (isArray && !actual.isArray) {
            return result.addDifference(Difference(node, actual.node, "EXPECTED is an array but ACTUAL is not"))
        }

        if (isObject && !actual.isObject) {
            return result.addDifference(Difference(node, actual.node, "EXPECTED is an object but ACTUAL is not"))
        }

        if (isLeaf && !actual.isLeaf) {
            return result.addDifference(Difference(node, actual.node,
                                                   "EXPECTED is a name/value pair but ACTUAL is not"))
        }

        // at this point, EXPECTED and ACTUAL are of same type
        if (isArray || isObject) {
            val expectedSize = children.size
            val actualSize = actual.children.size
            if (expectedSize != actualSize) {
                val message: String
                message = if (isObject) {
                    val expectedNodeNames = mutableListOf<String>()
                    children.forEach { child ->
                        if (StringUtils.isNotBlank(child.name)) expectedNodeNames.add(child.name!!)
                    }

                    val actualNodeNames = mutableListOf<String>()
                    actual.children.forEach { child ->
                        if (StringUtils.isNotBlank(child.name)) actualNodeNames.add(child.name!!)
                    }

                    "EXPECTED has $expectedSize node${if (expectedSize > 1) "s" else ""}" +
                    " (${TextUtils.toString(expectedNodeNames, ",")}) but " +
                    "ACTUAL has $actualSize node${if (actualSize > 1) "s" else ""}" +
                    " (${TextUtils.toString(actualNodeNames, ",")})"
                } else {
                    "EXPECTED has $expectedSize node${if (expectedSize > 1) "s" else ""} but " +
                    "ACTUAL has $actualSize node${if (actualSize > 1) "s" else ""}"
                }

                result.addDifference(Difference(node, actual.node, message))
            }

            for (i in 0 until expectedSize) {
                val expectedMeta = children[i]
                if (i >= actualSize) {
                    result.addDifference(Difference(expectedMeta.node, null, "No matching node on ACTUAL"))
                } else {
                    result.addDifferences(expectedMeta.compare(actual.children[i]))
                }
            }

            if (actualSize > expectedSize) {
                for (i in expectedSize until actualSize) {
                    result.addDifference(Difference(null, actual.children[i].node, "No matching node on ACTUAL"))
                }
            }

            return result
        }

        if (isLeaf) {
            val actualValue = actual.value
            if (value == null) {
                return if (actualValue == null)
                    result
                else
                    result.addDifference(Difference(node, actual.node,
                                                    "EXPECTED contains null but ACTUAL contains $actualValue"))
            }

            if (actualValue == null) {
                return result.addDifference(Difference(node, actual.node,
                                                       "EXPECTED contains $value but ACTUAL contains null"))
            }

            if (value is JsonPrimitive && actualValue is JsonPrimitive) {
                if (this.value != actualValue) {
                    val expectedType = JsonUtils.getPrimitiveType(value as JsonPrimitive)
                    val actualType = JsonUtils.getPrimitiveType(actualValue)
                    result.addDifference(Difference(node, actual.node,
                                                    "EXPECTED contains $value of type $expectedType but " +
                                                    "ACTUAL contains $actualValue of type $actualType"))
                }

                return result
            }

            if (value != actualValue) {
                return result.addDifference(Difference(node, actual.node,
                                                       "EXPECTED contains ${revealLeafValue(value as Any)} but " +
                                                       "ACTUAL contains ${revealLeafValue(actualValue)}"))
            }
        }

        return result
    }

    fun getValueString() = if (value == null) {
        null
    } else {
        if (value is JsonPrimitive) {
            (value as JsonPrimitive).asString
        } else {
            value.toString()
        }
    }

    override fun toString(): String {
        return ToStringBuilder(this, JSON_STYLE)
            .append("type", type)
            .append("node", node)
            .append("name", name)
            .append("value", value)
            .append("children", children)
            .toString()
    }

    companion object {
        fun revealLeafValue(value: Any): String {
            if (value is JsonMeta) {
                val actualChildName = value.name
                return if (StringUtils.isNotBlank(actualChildName)) "{$actualChildName:${value.value}}"
                else "${value.value}"
            }

            return "$value"
        }
    }
}

class JsonMetaParser {
    fun parse(json: JsonElement): JsonMeta = parseJson(json, null, "$")

    private fun parseJson(json: JsonElement, name: String?, node: String): JsonMeta {
        if (json.isJsonArray) {
            val jsonMeta = JsonMeta(type = ARRAY, name = name, node = node)
            val children = parseArray(json.asJsonArray, jsonMeta)
            jsonMeta.value = children
            jsonMeta.children += children
            return jsonMeta
        }

        if (json.isJsonObject) {
            val jsonMeta = JsonMeta(type = OBJECT, name = name, node = node)
            val children = parseObject(json.asJsonObject, jsonMeta)
            jsonMeta.value = children
            jsonMeta.children += children
            return jsonMeta
        }

        if (json.isJsonPrimitive) {
            val jsonMeta = JsonMeta(type = PRIMITIVE, name = name, node = node)
            jsonMeta.value = json.asJsonPrimitive
            return jsonMeta
        }

        if (json.isJsonNull) {
            return JsonMeta(type = PRIMITIVE, name = name, node = node)
        }

        throw IllegalArgumentException("Unknown/Unsupported JSON structure encountered")
    }

    private fun parseArray(array: JsonArray, parent: JsonMeta): List<JsonMeta> {
        // since this is object, we need to sort key for meaningful comparison
        val children = LinkedList<JsonMeta>()
        for (i in 0 until array.size()) {
            val childMeta = parseJson(array.get(i), null, "${parent.node}[$i]")
            childMeta.parent = parent
            children.add(childMeta)
        }
        return children
    }

    fun parsePrimitive(primitive: JsonPrimitive, parent: JsonMeta): JsonMeta {
        val meta = JsonMeta(type = PRIMITIVE, name = null, node = null)
        meta.parent = parent
        meta.value = primitive
        return meta
    }

    private fun parseObject(obj: JsonObject, parent: JsonMeta): List<JsonMeta> {
        // since this is object, we need to sort key for meaningful comparison
        val children = LinkedList<JsonMeta>()
        TreeSet(obj.keySet()).forEach { key ->
            val meta = parseJson(obj.get(key), key, "${StringUtils.defaultIfEmpty(parent.node, "$")}.$key")
            meta.parent = parent
            children.add(meta)
        }
        return children
    }
}