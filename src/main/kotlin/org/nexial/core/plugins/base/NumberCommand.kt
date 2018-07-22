package org.nexial.core.plugins.base

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.model.StepResult
import org.nexial.core.utils.CheckUtils.*
import java.text.DecimalFormat
import kotlin.Double.Companion.MIN_VALUE

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
        val asExpected: Boolean = isInRange(toDouble(num, "num"), toDouble(minNum, "min"), toDouble(maxNum, "max"))
        return StepResult(asExpected, "$num is ${if (asExpected) "" else "NOT "}between $minNum and $maxNum", null)
    }

    fun average(Var: String, array: String?): StepResult {
        requiresValidVariableName(Var)
//        requiresNotBlank(array, "invalid array", array)

        val strings = TextUtils.toList(array, context.textDelim, true)
        val average = if (CollectionUtils.isNotEmpty(strings)) {
            strings.foldRight(0.0) { value: String?, curr: Double -> curr + NumberUtils.toDouble(value) } / strings.size
        } else {
            0.0
        }

        context.setData(Var, average)
        return StepResult.success("average saved to variable '$Var' as $average")
    }

    fun max(Var: String, array: String?): StepResult {
        requiresValidVariableName(Var)
        requiresNotBlank(array, "invalid array", array)

        val strings = TextUtils.toList(array, context.textDelim, true)
        return if (CollectionUtils.isEmpty(strings)) {
            StepResult.fail("max NOT saved to variable '$Var' since no valid numbers are given")
        } else {
            var max = MIN_VALUE
            for (string in strings) {
                val num = NumberUtils.toDouble(string)
                if (num > max) max = num
            }

            context.setData(Var, max)
            StepResult.success("max saved to variable '$Var' as $max")
        }
    }

    fun min(Var: String, array: String?): StepResult {
        requiresValidVariableName(Var)
        requiresNotBlank(array, "invalid array", array)

        val strings = TextUtils.toList(array, context.textDelim, true)
        return if (CollectionUtils.isEmpty(strings)) {
            StepResult.fail("min NOT saved to variable '$Var' since no valid numbers are given")
        } else {
            var min = Double.MAX_VALUE
            for (string in strings) {
                val num = NumberUtils.toDouble(string)
                if (num < min) min = num
            }

            context.setData(Var, min)
            StepResult.success("min saved to variable '$Var' as $min")
        }
    }

    fun ceiling(Var: String): StepResult {
        requiresValidVariableName(Var)

        val current = StringUtils.defaultString(context.getStringData(Var))

        requires(NumberUtils.isParsable(current), "not valid number", Var)

        val ceiling = Math.ceil(NumberUtils.toDouble(current)).toInt()

        context.setData(Var, ceiling)

        return StepResult.success("Variable '$Var' has been round up to $ceiling")
    }

    fun floor(Var: String): StepResult {
        requiresValidVariableName(Var)

        val current = StringUtils.defaultString(context.getStringData(Var))

        requires(NumberUtils.isParsable(current), "not valid number", Var)

        val floor = Math.floor(NumberUtils.toDouble(current)).toInt()

        context.setData(Var, floor)

        return StepResult.success("Variable '$Var' has been round down to $floor")
    }

    fun round(Var: String, closestDigit: String): StepResult {
        requiresValidVariableName(Var)

        val current = StringUtils.defaultString(context.getStringData(Var))

        requires(NumberUtils.isParsable(current), "Variable does not contain correct number format", Var)

        val num = NumberUtils.toDouble(current)
        var closest = NumberUtils.toDouble(closestDigit)
        val fractionDigitCount = StringUtils.length(StringUtils.substringAfter(closestDigit + "", "."))

        val rounded: String = if (fractionDigitCount == 0) {
            (Math.round(num / closest) * closest).toInt().toString() + ""
        } else {
            if (closest == 0.0) {
                closest = NumberUtils.toDouble("0." + (StringUtils.repeat("0", fractionDigitCount - 1) + "1"))
            }

            val df = DecimalFormat()
            df.isGroupingUsed = false
            df.maximumFractionDigits = fractionDigitCount
            df.minimumFractionDigits = fractionDigitCount
            df.format(Math.round(num / closest) * closest)
        }

        context.setData(Var, rounded)
        return StepResult.success("Variable '$Var' has been rounded down to $rounded")
    }

    fun increment(Var: String, amount: String): StepResult {
        val newAmt = numberFormatHelper(Var, amount).addValue(amount).originalFormat
        context.setData(Var, newAmt)
        return StepResult.success("incremented \${$Var} by $amount to $newAmt")
    }

    fun decrement(Var: String, amount: String): StepResult {
        val newAmt = numberFormatHelper(Var, amount).subtractValue(amount).originalFormat
        context.setData(Var, newAmt)
        return StepResult.success("decremented \${$Var} by $amount to $newAmt")
    }

    private fun numberFormatHelper(Var: String, amount: String): NumberFormatHelper {
        requiresValidVariableName(Var)

        val current = StringUtils.defaultString(context.getStringData(Var, "0"))
        val formatHelper = NumberFormatHelper.newInstance(current)

        requiresNotBlank(formatHelper.normalFormat, "variable '$Var' does not represent a number", current)
        requires(NumberUtils.isParsable(amount), "Variable does not contain correct number format", amount)

        return formatHelper
    }
}
