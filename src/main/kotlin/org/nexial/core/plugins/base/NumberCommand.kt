package org.nexial.core.plugins.base

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.nexial.commons.utils.TextUtils
import org.nexial.core.model.StepResult
import org.nexial.core.utils.CheckUtils.*
import java.text.DecimalFormat
import kotlin.Double.Companion.MAX_VALUE
import kotlin.Double.Companion.MIN_VALUE
import kotlin.math.ceil
import kotlin.math.roundToLong

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
        return StepResult(asExpected, "$num1 is ${if (asExpected) "" else "NOT "}greater than or equal to $num2", null)
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
        requiresValidAndNotReadOnlyVariableName(Var)

        val strings = TextUtils.toList(array, context.textDelim, true)
        val average = if (CollectionUtils.isNotEmpty(strings)) {
            val noBlanks = strings.filter { StringUtils.isNotBlank(it) }
            noBlanks.foldRight(0.0) { value, curr -> curr + NumberUtils.toDouble(value) } / noBlanks.size
        } else {
            0.0
        }

        context.setData(Var, average)
        return StepResult.success("average saved to variable '$Var' as $average")
    }

    fun max(Var: String, array: String?): StepResult {
        requiresValidAndNotReadOnlyVariableName(Var)
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
        requiresValidAndNotReadOnlyVariableName(Var)
        requiresNotBlank(array, "invalid array", array)

        val strings = TextUtils.toList(array, context.textDelim, true)
        return if (CollectionUtils.isEmpty(strings)) {
            StepResult.fail("min NOT saved to variable '$Var' since no valid numbers are given")
        } else {
            var min = MAX_VALUE
            for (string in strings) {
                val num = NumberUtils.toDouble(string)
                if (num < min) min = num
            }

            context.setData(Var, min)
            StepResult.success("min saved to variable '$Var' as $min")
        }
    }

    fun ceiling(Var: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(Var)

        val current = StringUtils.defaultString(context.getStringData(Var))

        requires(NumberUtils.isParsable(current), "not valid number", Var)

        val ceiling = ceil(NumberUtils.toDouble(current)).toLong()

        context.setData(Var, ceiling)

        return StepResult.success("Variable '$Var' has been round up to $ceiling")
    }

    fun floor(Var: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(Var)

        val current = StringUtils.defaultString(context.getStringData(Var))

        requires(NumberUtils.isParsable(current), "not valid number", Var)

        val floor = kotlin.math.floor(NumberUtils.toDouble(current)).toLong()

        context.setData(Var, floor)

        return StepResult.success("Variable '$Var' has been round down to $floor")
    }

    fun whole(Var: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(Var)

        val current = StringUtils.defaultString(context.getStringData(Var))
        requires(NumberUtils.isParsable(current), "not valid number", Var)

        val floor = NumberUtils.toDouble(current).toLong()
        context.setData(Var, floor)

        return StepResult.success("Variable '$Var' has been round down to $floor")
    }

    fun roundTo(Var: String, closestDigit: String): StepResult {
        requiresValidAndNotReadOnlyVariableName(Var)

        val current = StringUtils.defaultString(context.getStringData(Var))

        requires(NumberUtils.isParsable(current), "Variable does not contain correct number format", Var)

        val rounded = roundTo(NumberUtils.toDouble(current), closestDigit)
        updateDataVariable(Var, rounded)
        return StepResult.success("Variable '$Var' has been rounded down to $rounded")
    }

    fun increment(Var: String, amount: String): StepResult {
        val newAmt = numberFormatHelper(Var, amount).addValue(amount).originalFormat
        updateDataVariable(Var, newAmt)
        return StepResult.success("incremented \${$Var} by $amount to $newAmt")
    }

    fun decrement(Var: String, amount: String): StepResult {
        val newAmt = numberFormatHelper(Var, amount).subtractValue(amount).originalFormat
        updateDataVariable(Var, newAmt)
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

    companion object {
        @JvmStatic
        fun roundTo(num: Double, closestDigit: String): String {
            // figure out the specified number of whole numbers and fractional digits
            val wholeNumberCount = StringUtils.length(StringUtils.substringBefore(closestDigit, "."))
            var fractionDigitCount = StringUtils.length(StringUtils.substringAfter(closestDigit, "."))

            // we will only use this divisor if there's a whole number or if closestDigit looks like 0.xxx
            // this means that if user specifies
            //  - closestDigit = 0.00, then we will round number to closest 2-digit decimal
            //  - closestDigit = 1.00, then we will round number to closest 2-digit decimal
            //  - closestDigit =  .00, then we will round number to closest 2-digit decimal
            //  - closestDigit = 1.0,  then we will round number to closest 1-digit decimal
            //  - closestDigit = 9.0,  then we will round number to closest 1-digit decimal
            //  - closestDigit = 5,    then we will round number to closest 1's whole number
            //  - closestDigit = 10.00, then we will round number to closest 10's whole number
            //  - closestDigit = 10.99, then we will round number to closest 10's whole number
            //  - closestDigit = 10.0,  then we will round number to closest 10's whole number
            var closestWhole = 0.0
            if (wholeNumberCount > 1) {
                closestWhole = NumberUtils.toDouble("1" + StringUtils.repeat("0", wholeNumberCount - 1))
                // since we are rounding to closest 10's or higher position, decimal places have no meaning here
                fractionDigitCount = 0
            } else if (closestWhole == 0.0) {
                closestWhole = NumberUtils.toDouble("0." + (StringUtils.repeat("0", fractionDigitCount - 1) + "1"))
            }

            val df = DecimalFormat()
            df.isGroupingUsed = false
            df.maximumFractionDigits = fractionDigitCount
            df.minimumFractionDigits = fractionDigitCount
            return df.format((num / closestWhole).roundToLong() * closestWhole)
        }
    }
}
