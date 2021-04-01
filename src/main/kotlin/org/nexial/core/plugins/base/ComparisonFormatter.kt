package org.nexial.core.plugins.base

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.NexialConst.NL
import org.nexial.core.model.ExecutionContext
import kotlin.math.max
import kotlin.math.min

object ComparisonFormatter {
    private const val missing = "<missing>"
    private const val yes = "yes"
    private const val no = "NO"
    private const val listDelim = ","
    private const val undefined = "<null/undefined>"
    private const val empty = "<empty>"
    private const val blank = "<blank>"

    @JvmStatic
    fun displayAssertionResult(context: ExecutionContext, expectsSame: Boolean, expected: String?, actual: String?):
            String {
        val nullValue = context.nullValueToken
        val expectedForDisplay = context.truncateForDisplay(StringUtils.defaultString(expected, nullValue))
        val actualForDisplay = context.truncateForDisplay(StringUtils.defaultString(actual, nullValue))

        return if (expectsSame)
            "validated EXPECTED = ACTUAL; '$expectedForDisplay' = '$actualForDisplay'"
        else
            "validated EXPECTED not equal to ACTUAL; '$expectedForDisplay' not equal to '$actualForDisplay'"
    }

    @JvmStatic
    fun displayForCompare(label1: String = "expected", data1: Any?, label2: String = "actual", data2: Any?): String {
        // 1. label treatment
        val labelLength = max(label1.length, label2.length)
        val label1Display = StringUtils.rightPad(label1, labelLength, " ")
        val label2Display = StringUtils.rightPad(label2, labelLength, " ")

        // 2. check for special types
        var data1List: List<String?>? = null
        var data2List: List<String?>? = null
        var data1Map: Map<String, String>? = null
        var data2Map: Map<String, String>? = null

        // 3. form display for data1 :: first pass
        var data1Display = label1Display
        when {
            data1 == null           -> data1Display += "=$undefined"
            data1.javaClass.isArray -> data1List = listOf(*TextUtils.toStringArray(data1))
            data1 is Collection<*>  -> data1List = TextUtils.toStringList(data1)
            data1 is Map<*, *>      -> data1Map = TextUtils.toStringMap(data1)
            data1 is String         -> data1Display += when {
                StringUtils.isEmpty(data1) -> "=$empty"
                StringUtils.isBlank(data1) -> "=$blank[$data1]"
                else                       -> "=$data1"
            }
            else                    -> data1Display += "=$data1"
        }

        // 3. form display for data2 :: first pass
        var data2Display = label2Display
        when {
            data2 == null           -> data2Display += "=$undefined"
            data2.javaClass.isArray -> data2List = listOf(*TextUtils.toStringArray(data2))
            data2 is Collection<*>  -> data2List = TextUtils.toStringList(data2)
            data2 is Map<*, *>      -> data2Map = TextUtils.toStringMap(data2)
            data2 is String         -> data2Display += when {
                StringUtils.isEmpty(data2) -> "=$empty"
                StringUtils.isBlank(data2) -> "=$blank[$data2]"
                else                       -> "=$data2"
            }
            else                    -> data2Display += "=$data2"
        }

        if (data1List != null) {
            if (data2List != null) {
                val (line1, line2) = displayAligned(data1List, data2List)
                data1Display += "=$line1"
                data2Display += "=$line2"
            } else {
                data1Display += "=${TextUtils.toString(data1List, listDelim)}"
            }
        } else {
            if (data1Map != null) {
                data1Display +=
                    if (data2Map != null)
                        displayAsMapDiff(label1Display, data1Map, label2Display, data2Map)
                    else "=$data1Map"
            } else {
                if (data2Map != null) data2Display += "=$data2Map"
                if (data2List != null) data2Display += "=${TextUtils.toString(data2List, listDelim)}"
            }
        }

        return data1Display + NL + data2Display
    }

    private fun displayAligned(list1: List<String?>, list2: List<String?>): AlignedListDisplay {
        if (CollectionUtils.isEmpty(list1)) return AlignedListDisplay("", TextUtils.toString(list2, listDelim))
        if (CollectionUtils.isEmpty(list2)) return AlignedListDisplay(TextUtils.toString(list1, listDelim), "")

        val list1Size = list1.size
        val list2Size = list2.size
        val commonSize = min(list1Size, list2Size)

        val buffer1 = StringBuilder()
        val buffer2 = StringBuilder()

        for (i in 0 until commonSize) {
            val item1 = list1[i] ?: missing
            val item2 = list2[i] ?: missing
            if (StringUtils.equals(item1, item2)) {
                buffer1.append(item1).append(listDelim)
                buffer2.append(item2).append(listDelim)
            } else {
                val item1Length = StringUtils.length(item1) + if (item1 != missing) 2 else 0
                val item2Length = StringUtils.length(item2) + if (item2 != missing) 2 else 0
                val maxWidth = max(item1Length, item2Length)
                buffer1.append(displayAlignedUnmatched(item1, maxWidth)).append(listDelim)
                buffer2.append(displayAlignedUnmatched(item2, maxWidth)).append(listDelim)
            }
        }

        if (list1Size > commonSize) {
            for (i in commonSize until list1Size) {
                val item1 = list1[i] ?: missing
                val item1Length = StringUtils.length(item1) + if (item1 == missing) 0 else 2
                val item2 = missing
                val maxWidth = max(item1Length, StringUtils.length(item2))
                buffer1.append(displayAlignedUnmatched(item1, maxWidth)).append(listDelim)
                buffer2.append(displayAlignedUnmatched(item2, maxWidth)).append(listDelim)
            }
        }

        if (list2Size > commonSize) {
            for (i in commonSize until list2Size) {
                val item1 = missing
                val item2 = list2[i] ?: missing
                val item2Length = StringUtils.length(item2) + if (item2 == missing) 0 else 2
                val maxWidth = max(StringUtils.length(item1), item2Length)
                buffer1.append(displayAlignedUnmatched(item1, maxWidth)).append(listDelim)
                buffer2.append(displayAlignedUnmatched(item2, maxWidth)).append(listDelim)
            }
        }

        return AlignedListDisplay(StringUtils.removeEnd(buffer1.toString(), listDelim),
                                  StringUtils.removeEnd(buffer2.toString(), listDelim))
    }

    private fun displayAlignedUnmatched(item: String, maxWidth: Int): String {
        val display = if (item == missing) missing else "[$item]"
        return display + StringUtils.repeat(" ", maxWidth - StringUtils.length(display))
    }

    private fun displayAsMapDiff(label1: String, map1: Map<String, String>, label2: String, map2: Map<String, String>):
            String {
        val headers = listOf("key", StringUtils.trim(label1) + " value", StringUtils.trim(label2) + " value", "matched")

        // collect map1's value and any diff against map2, THEN
        // collect map2's value (those unique within map2)
        val records = map1.map { (key, value) ->
            val value2 = map2[key] ?: missing
            listOf(key, value, value2, if (StringUtils.equals(value, value2)) yes else no)
        }.plus(map2.filterKeys { key -> !map1.containsKey(key) }
                       .map { (key, value) -> listOf(key, missing, value, no) })

        return TextUtils.createAsciiTable(headers, records) { obj, index -> obj[index] }
    }
}

data class AlignedListDisplay(val line1: String, val line2: String)