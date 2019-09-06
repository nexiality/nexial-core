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
        if (actual == null)
            return result.addDifference(Difference(node, null, "ACTUAL is null/empty but EXPECTED is not"))

        if (isArray && !actual.isArray)
            return result.addDifference(Difference(node, actual.node, "EXPECTED is an array but ACTUAL is not"))

        if (isObject && !actual.isObject)
            return result.addDifference(Difference(node, actual.node, "EXPECTED is an object but ACTUAL is not"))

        if (isLeaf && !actual.isLeaf)
            return result.addDifference(Difference(node, actual.node, "EXPECTED is name/value pair but ACTUAL is not"))

        // at this point, EXPECTED and ACTUAL are of same type
        if (isArray || isObject) return compareAsObjectOrArray(actual, result)

        // at this point, both are leaves
        if (isLeaf) return compareAsLeaf(actual, result)

        return result
    }

    private fun compareAsLeaf(actual: JsonMeta, result: JsonComparisonResult): JsonComparisonResult {
        val actualValue = actual.value

        if (value == null)
            return if (actualValue == null)
                result
            else {
                result.addDifference(
                        Difference(node, actual.node,
                                   "EXPECTED is null but ACTUAL contains $actualValue of " +
                                   "type ${JsonUtils.getPrimitiveType(actualValue as JsonPrimitive)}"))
                return result
            }

        if (actualValue == null)
            return result.addDifference(
                    Difference(node, actual.node,
                               "EXPECTED contains $value of type ${JsonUtils.getPrimitiveType(value as JsonPrimitive)} " +
                               "but ACTUAL is null"))


        if (value is JsonPrimitive && actualValue is JsonPrimitive) {
            if (this.value != actualValue) {
                val expectedType = JsonUtils.getPrimitiveType(value as JsonPrimitive)
                val actualType = JsonUtils.getPrimitiveType(actualValue)
                result.addDifference(Difference(node, actual.node,
                                                "EXPECTED contains $value of type $expectedType " +
                                                "but ACTUAL contains $actualValue of type $actualType"))
            }

            return result
        }

        if (value != actualValue)
            return result.addDifference(
                    Difference(node, actual.node, "EXPECTED contains ${revealLeafValue(value as Any)} " +
                                                  "but ACTUAL contains ${revealLeafValue(actualValue)}"))

        return result
    }

    private fun compareAsObjectOrArray(actual: JsonMeta,
                                       result: JsonComparisonResult): JsonComparisonResult {
        val expectedSize = children.size
        val actualSize = actual.children.size
        if (expectedSize != actualSize) {
            val message: String
            message = if (isObject) {
                val expectedNodeNames = mutableListOf<String>()
                children.forEach { if (StringUtils.isNotBlank(it.name)) expectedNodeNames.add(it.name!!) }

                val actualNodeNames = mutableListOf<String>()
                actual.children.forEach { if (StringUtils.isNotBlank(it.name)) actualNodeNames.add(it.name!!) }

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

        // at this point, EXPECTED and ACTUAL has the same number of "children"
        for (i in 0 until expectedSize) {
            val expectedMeta = children[i]

            if (i >= actualSize) {
                result.addDifference(Difference(expectedMeta.node,
                                                null,
                                                "EXPECTED node '${displayMetaLabel(expectedMeta, i)}' " +
                                                "NOT FOUND in ACTUAL"))
            } else {
                val actualMeta = actual.children[i]

                if (isObject) {
                    // as JSON objects, we compare each node by name.. first by matching up the node name
                    val actualMatchedMeta = actual.findChildNode(expectedMeta.name!!)
                    if (actualMatchedMeta == null) {
                        // this means that there's a child node in EXPECTED not found in ACTUAL's
                        result.addDifference(Difference(expectedMeta.node, null,
                                                        "EXPECTED node '${expectedMeta.name}' NOT FOUND in ACTUAL"))

                        // what about ACTUAL's current node?
                        val expectedMatchedNode = findChildNode(actualMeta.name!!)
                        if (expectedMatchedNode == null) {
                            // this means that there's a child node in ACTUAL not found in EXPECTED's
                            // this means that there's a child node in EXPECTED not found in ACTUAL's
                            result.addDifference(Difference(null, actualMeta.node,
                                                            "ACTUAL node '${actualMeta.name}' NOT FOUND in EXPECTED"))
                        }
                    } else {
                        // at this point, we found matching node name in EXPECTED and ACTUAL's child node
                        result.addDifferences(expectedMeta.compare(actualMatchedMeta))
                    }
                } else {
                    if (i >= actualSize) {
                        result.addDifference(Difference(expectedMeta.node, null, "No matching node on ACTUAL"))
                    } else {
                        result.addDifferences(expectedMeta.compare(actualMeta))
                    }
                }
            }
        }

        if (actualSize > expectedSize) {
            for (i in expectedSize until actualSize) {
                val actualMeta = actual.children[i]
                result.addDifference(
                        Difference(null, actualMeta.node,
                                   "ACTUAL node '${displayMetaLabel(actualMeta, i)}' NOT FOUND in EXPECTED"))
            }
        }

        return result
    }

    private fun displayMetaLabel(meta: JsonMeta, i: Int) =
            if (meta.isArray)
                "item $i"
            else if (meta.isLeaf)
                if (StringUtils.isEmpty(meta.name)) "${meta.parent!!.name}[$i]" else "${meta.name}[$i]"
            else
                meta.name ?: meta.node

    private fun findChildNode(nodeName: String) = children.firstOrNull { it.name == nodeName }

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