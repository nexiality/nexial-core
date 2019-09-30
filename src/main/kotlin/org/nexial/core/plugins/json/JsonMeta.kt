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

/** json elemental structure, either array or name/value pair, and metadata */
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

        // first level type checking
        if (actual == null) return result.addDifference(Difference(node, display(this), "null"))
        if (isArray && !actual.isArray) return result.addDifference(Difference(node, display(this), display(actual)))
        if (isObject && !actual.isObject) return result.addDifference(Difference(node, "type object", display(actual)))
        if (isLeaf && !actual.isLeaf) return result.addDifference(Difference(node, display(this), display(actual)))

        // at this point, EXPECTED and ACTUAL are of same type
        if (isArray || isObject) return compareAsObjectOrArray(actual, result)

        // at this point, both are leaves
        return if (isLeaf) compareAsLeaf(actual, result) else result
    }

    private fun display(meta: JsonMeta?): String {
        if (meta?.value == null) return "null"
        if (meta.isLeaf) return "value ${meta.value} of type ${displayType(meta)}"
        if (meta.isArray) {
            val size = (meta.value as List<*>).size
            return when (size) {
                0    -> "[]"
                1    -> "[${meta.value}]"
                else -> "$size elements of type array"
            }
        }
        if (meta.isObject) return "type object"
        return "unknown"
    }

    private fun displayType(meta: JsonMeta) =
            when {
                meta?.value == null -> "null"
                meta.isObject       -> "object"
                meta.isArray        -> "array"
                meta.isLeaf         -> JsonUtils.getPrimitiveType(meta.value as JsonPrimitive)
                else                -> "unknown"
            }

    private fun compareAsLeaf(actual: JsonMeta, result: JsonComparisonResult): JsonComparisonResult {

        val actualValue = actual.value

        if (value == null) return if (actualValue == null) result
        else result.addDifference(Difference(actual.node, "null", display(actual)))

        if (actualValue == null) return result.addDifference(Difference(node, display(this), "null"))

        if (value is JsonPrimitive && actualValue is JsonPrimitive) {
            return if (this.value != actualValue)
                result.addDifference(Difference(node, display(this), display(actual)))
            else result
        }

        return if (value != actualValue)
            result.addDifference(
                    Difference(node, "value ${revealLeafValue(value as Any)}", "value ${revealLeafValue(actualValue)}"))
        else result
    }

    private fun compareAsObjectOrArray(actual: JsonMeta,
                                       result: JsonComparisonResult): JsonComparisonResult {
        val expectedSize = children.size
        val actualSize = actual.children.size

        if (expectedSize != actualSize) {
            val expectedValue = "$expectedSize element${if (expectedSize > 1) "s" else ""}" +
                                if (isObject && children.isNotEmpty())
                                    " (${children.map { it.name }.reduce { left, right -> "$left, $right" }})"
                                else ""
            val actualValue = "$actualSize element${if (actualSize > 1) "s" else ""}" +
                              if (isObject && actual.children.isNotEmpty())
                                  " (${actual.children.map { it.name }.reduce { left, right -> "$left, $right" }})"
                              else ""
            result.addDifference(Difference(node, expectedValue, actualValue))
        }

        val scanned = mutableListOf<String>()

        for (i in 0 until expectedSize) {
            val expectedMeta = children[i]
            val expectedDisplay = displayMetaLabel(expectedMeta)

            if (isObject) {
                // all leaf that belongs to a object has name
                val nodeName = expectedMeta.name!!

                // remember this node so that we don't double report on this issue
                scanned.add(nodeName)

                val actualMatchedMeta = actual.findChildNode(nodeName)
                if (actualMatchedMeta == null) {
                    result.addDifference(Difference(expectedMeta.node, expectedDisplay, "NOT FOUND"))
                } else {
                    result.addDifferences(expectedMeta.compare(actualMatchedMeta))
                }
            } else {
                // could be leaf or array
                if (i >= actualSize) {
                    result.addDifference(Difference(expectedMeta.node, expectedDisplay, "NOT FOUND"))
                } else {
                    result.addDifferences(expectedMeta.compare(actual.children[i]))
                }
            }

            continue
        }

        actual.children.forEach {
            if (it.name != null && !scanned.contains(it.name!!))
                result.addDifference(Difference(it.node, "NOT FOUND", display(it)))
        }

        return result
    }

    private fun displayMetaLabel(meta: JsonMeta): String =
            when {
                meta.value == null -> "null"
                meta.isArray       -> "[" + TextUtils.toString(meta.value as List<*>, ", ") + "]"
                meta.isLeaf        -> "value ${meta.value.toString()} of type ${JsonUtils.getPrimitiveType(meta.value as JsonPrimitive)}"
                else               -> "type object"
            }

    private fun findChildNode(nodeName: String) = children.firstOrNull { it.name == nodeName }

    fun getValueString() =
            when (value) {
                null             -> null
                is JsonPrimitive -> (value as JsonPrimitive).asString
                else             -> value.toString()
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
                val name = value.name
                return if (StringUtils.isNotBlank(name)) "{$name:${value.value}}"
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

        if (json.isJsonNull) return JsonMeta(type = PRIMITIVE, name = name, node = node)

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