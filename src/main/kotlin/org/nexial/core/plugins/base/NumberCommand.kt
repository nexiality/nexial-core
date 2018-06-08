package org.nexial.core.plugins.base

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.model.StepResult
import org.nexial.core.utils.CheckUtils.*
import java.text.DecimalFormat

class NumberCommand : BaseCommand() {

    override fun getTarget() = "number"

    override fun assertEqual(num1: String, num2: String): StepResult {
        val asExpected = toDouble(num1, "num1") == toDouble(num2, "num2")
        return StepResult(asExpected, "$num1 is ${if (asExpected) "" else "NOT "}equal to $num2", null)
    }

    fun assertGreater(num1: String, num2: String): StepResult {
        val asExpected = toDouble(num1, "num1") > toDouble(num2, "num2")
        return StepResult(asExpected, "$num1 is ${if (asExpected) "" else "NOT "}greater than $num2", null)
    }

    fun assertGreaterOrEqual(num1: String, num2: String): StepResult {
        val asExpected: Boolean = toDouble(num1, "num1") >= toDouble(num2, "num2")
        return StepResult(asExpected, "$num1 is ${if (asExpected) "" else "NOT"}greater than or equal to $num2", null)
    }

    fun assertLesser(num1: String, num2: String): StepResult {
        val asExpected: Boolean = toDouble(num1, "num1") < toDouble(num2, "num2")
        return StepResult(asExpected, "$num1 is ${if (asExpected) "" else "NOT "}less than $num2", null)
    }

    fun assertLesserOrEqual(num1: String, num2: String): StepResult {
        val asExpected: Boolean = toDouble(num1, "num1") <= toDouble(num2, "num2")
        return StepResult(asExpected, "$num1 is ${if (asExpected) "" else "NOT "}less than or equal to $num2", null)
    }

    fun assertBetween(num: String, minNum: String, maxNum: String): StepResult {
        val actual: Double = toDouble(num, "num")
        val lowerNum: Double = toDouble(minNum, "min")
        val upperNum: Double = toDouble(maxNum, "max")
        val asExpected: Boolean = isInRange(actual, lowerNum, upperNum)

        return StepResult(asExpected, "$num is ${if (asExpected) "" else "NOT "}between $minNum and $maxNum", null)
    }

    fun average(variableName: String, array: String?): StepResult {
        requiresValidVariableName(variableName)

        val strings = TextUtils.toList(array, context.textDelim, true)
        val average = if (CollectionUtils.isNotEmpty(strings)) {
            strings.foldRight(0.0 , { value: String?, curr: Double -> curr + NumberUtils.toDouble(value) })/ strings.size
        } else {
            0.0
        }

        context.setData(variableName, average)
        return StepResult.success("average saved to variable '$variableName' as $average")
    }

    fun max(variableName: String, array: String?): StepResult {
        requiresValidVariableName(variableName)
        requiresNotBlank(array, "invalid array", array)

        val strings = TextUtils.toList(array, context.textDelim, true)
        if (CollectionUtils.isEmpty(strings)) {
            return StepResult.fail("max NOT saved to variable '$variableName' since no valid numbers are given")
        }

        var max = Double.MIN_VALUE
        for (string in strings) {
            val num = NumberUtils.toDouble(string)
            if (num > max) {
                max = num
            }
        }

        context.setData(variableName, max)
        return StepResult.success("max saved to variable '$variableName' as $max")
    }

    fun min(variableName: String, array: String?): StepResult {
        requiresValidVariableName(variableName)
        requiresNotBlank(array, "invalid array", array)

        val strings = TextUtils.toList(array, context.textDelim, true)
        if (CollectionUtils.isEmpty(strings)) {
            return StepResult.fail("min NOT saved to variable '$variableName' since no valid numbers are given")
        }

        var min = Double.MAX_VALUE
        for (string in strings) {
            val num = NumberUtils.toDouble(string)
            if (num < min) {
                min = num
            }
        }

        context.setData(variableName, min)
        return StepResult.success("min saved to variable '$variableName' as $min")
    }

    fun ceiling(variableName: String): StepResult {
        requiresValidVariableName(variableName)

        val current = StringUtils.defaultString(context.getStringData(variableName))

        requires(NumberUtils.isParsable(current), "not valid number", variableName)

        val ceiling = Math.ceil(NumberUtils.toDouble(current)).toInt()

        context.setData(variableName, ceiling)

        return StepResult.success("Variable '$variableName' has been round up to $ceiling")
    }

    fun floor(variableName: String): StepResult {
        requiresValidVariableName(variableName)

        val current = StringUtils.defaultString(context.getStringData(variableName))

        requires(NumberUtils.isParsable(current), "not valid number", variableName)

        val floor = Math.floor(NumberUtils.toDouble(current)).toInt()

        context.setData(variableName, floor)

        return StepResult.success("Variable '$variableName' has been round down to $floor")
    }

    fun round(variableName: String, closestDigit: String): StepResult {
        requiresValidVariableName(variableName)

        val current = StringUtils.defaultString(context.getStringData(variableName))

        requires(NumberUtils.isParsable(current), "Variable does not contain correct number format", variableName)

        val num = NumberUtils.toDouble(current)
        var closest = NumberUtils.toDouble(closestDigit)
        val fractionDigitCount = StringUtils.length(StringUtils.substringAfter(closestDigit + "", "."))

        val rounded: String = if (fractionDigitCount == 0) {
            (Math.round(num / closest) * closest).toInt().toString() + ""
        } else {
            val df = DecimalFormat()
            df.isGroupingUsed = false
            df.maximumFractionDigits = fractionDigitCount
            df.minimumFractionDigits = fractionDigitCount

            if (closest == 0.0) {
                closest = NumberUtils.toDouble("0." + (StringUtils.repeat("0", fractionDigitCount - 1) + "1"))
            }

            df.format(Math.round(num / closest) * closest)
        }

        context.setData(variableName, rounded)
        return StepResult.success("Variable '$variableName' has been rounded down to $rounded")
    }

    private fun numberFormatHelper(variableName: String, amount: String): NumberFormatHelper {
        val current = StringUtils.defaultString(context.getStringData(variableName, "0"))
        val formatHelper = NumberFormatHelper.newInstance(current)

        requiresNotBlank(formatHelper.normalFormat, "variable '$variableName' does not represent a number", current)
        requires(NumberUtils.isParsable(amount), "Variable does not contain correct number format", amount)

        return formatHelper
    }

    fun increment(variableName: String, amount: String): StepResult {
        requiresValidVariableName(variableName)

        val formatHelper = numberFormatHelper(variableName, amount)

        val newAmt = formatHelper.addValue(amount).originalFormat
        context.setData(variableName, newAmt)

        return StepResult.success("incremented \${$variableName} by $amount to $newAmt")

    }

    fun decrement(variableName: String, amount: String): StepResult {
        requiresValidVariableName(variableName)

        val formatHelper = numberFormatHelper(variableName, amount)

        val newAmt = formatHelper.subtractValue(amount).originalFormat
        context.setData(variableName, newAmt)

        return StepResult.success("decremented \${$variableName} by $amount to $newAmt")

    }
}
