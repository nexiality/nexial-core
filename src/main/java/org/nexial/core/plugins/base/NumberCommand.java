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
 *
 */

package org.nexial.core.plugins.base;

import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;

import static org.nexial.core.utils.CheckUtils.*;

public class NumberCommand extends BaseCommand {

    @Override
    public void init(ExecutionContext context) {
        super.init(context);
    }

    @Override
    public String getTarget() { return "number"; }

    public StepResult assertEqual(String expected, String actual) {
        String nullValue = context.getNullValueToken();

        expected = StringUtils.equals(expected, nullValue) ?
                   null : NumberFormatHelper.newInstance(expected).getNormalFormat();
        actual = StringUtils.equals(actual, nullValue) ?
                 null : NumberFormatHelper.newInstance(actual).getNormalFormat();
        assertEquals(expected, actual);

        return StepResult.success("validated " + StringUtils.defaultString(expected, nullValue) +
                                  "=" + StringUtils.defaultString(actual, nullValue));
    }

    public StepResult assertGreater(String num1, String num2) {
        boolean asExpected = toDouble(num1, "num1") > toDouble(num2, "num2");
        return new StepResult(asExpected, num1 + " is " + (asExpected ? "" : "NOT") + " greater than " + num2, null);
    }

    public StepResult assertGreaterOrEqual(String num1, String num2) {
        boolean asExpected = toDouble(num1, "num1") >= toDouble(num2, "num2");
        return new StepResult(asExpected,
                              num1 + " is " + (asExpected ? "" : "NOT") + " greater or equal to " + num2,
                              null);
    }

    public StepResult assertLesser(String num1, String num2) {
        boolean asExpected = toDouble(num1, "num1") < toDouble(num2, "num2");
        return new StepResult(asExpected, num1 + " is " + (asExpected ? "" : "NOT") + " less than " + num2, null);
    }

    public StepResult assertLesserOrEqual(String num1, String num2) {
        boolean asExpected = toDouble(num1, "num1") <= toDouble(num2, "num2");
        return new StepResult(asExpected,
                              num1 + " is" + (asExpected ? "" : " NOT ") + "less or equal to " + num2,
                              null);
    }

    public StepResult assertBetween(String num, String min, String max) {
        double actual = toDouble(num, "num");
        double lowerNum = toDouble(min, "min");
        double upperNum = toDouble(max, "max");
        boolean asExpected = isInRange(actual, lowerNum, upperNum);
        return new StepResult(asExpected,
                              num + " is" + (asExpected ? "" : " NOT") + " between " +
                              min + " and " + max, null);
    }

    public StepResult average(String var, String array) {
        requiresValidVariableName(var);

        double average = 0;
        List<String> strings = TextUtils.toList(array, context.getTextDelim(), true);
        if (CollectionUtils.isNotEmpty(strings)) {
            double total = 0;
            for (String string : strings) { total += NumberUtils.toDouble(string); }
            average = total / strings.size();
        }

        context.setData(var, average);
        return StepResult.success("average saved to variable '" + var + "' as " + average);
    }

    public StepResult max(String var, String array) {
        requiresValidVariableName(var);
        requiresNotBlank(array, "invalid array", array);

        List<String> strings = TextUtils.toList(array, context.getTextDelim(), true);
        if (CollectionUtils.isEmpty(strings)) {
            return StepResult.fail("max NOT saved to variable '" + var + "' since no valid numbers are given");
        }

        double max = Integer.MIN_VALUE;
        for (String string : strings) {
            double num = NumberUtils.toDouble(string);
            if (num > max) { max = num; }
        }

        context.setData(var, max);
        return StepResult.success("max saved to variable '" + var + "' as " + max);
    }

    public StepResult min(String var, String array) {
        requiresValidVariableName(var);
        requiresNotBlank(array, "invalid array", var);

        List<String> strings = TextUtils.toList(array, context.getTextDelim(), true);
        if (CollectionUtils.isEmpty(strings)) {
            return StepResult.fail("min NOT saved to variable '" + var + "' since no valid numbers are given");
        }

        double min = Integer.MAX_VALUE;
        for (String string : strings) {
            double num = NumberUtils.toDouble(string);
            if (num < min) { min = num; }
        }

        context.setData(var, min);
        return StepResult.success("min saved to variable '" + var + "' as " + min);
    }

    public StepResult ceiling(String var) {
        requiresValidVariableName(var);

        String current = StringUtils.defaultString(context.getStringData(var));
        requires(NumberUtils.isParsable(current), "Variable does not contain correct number format", var);

        int ceiling = (int) Math.ceil(NumberUtils.toDouble(current));
        context.setData(var, ceiling);

        return StepResult.success("Variable '" + var + "' has been round up to " + ceiling);
    }

    public StepResult floor(String var) {
        requiresValidVariableName(var);

        String current = StringUtils.defaultString(context.getStringData(var));
        requires(NumberUtils.isParsable(current), "Variable does not contain correct number format", var);

        int floor = (int) Math.floor(NumberUtils.toDouble(current));
        context.setData(var, floor);

        return StepResult.success("Variable '" + var + "' has been round down to " + floor);
    }

    public StepResult round(String var, String closestDigit) {
        requiresValidVariableName(var);

        String current = StringUtils.defaultString(context.getStringData(var));
        requires(NumberUtils.isParsable(current), "Variable does not contain correct number format", var);

        double num = NumberUtils.toDouble(current);
        double closest = NumberUtils.toDouble(closestDigit);
        int fractionDigitCount = StringUtils.length(StringUtils.substringAfter(closestDigit + "", "."));

        String rounded;
        if (fractionDigitCount == 0) {
            rounded = ((int) (Math.round(num / closest) * closest)) + "";
        } else {
            DecimalFormat df = new DecimalFormat();
            df.setGroupingUsed(false);
            df.setMaximumFractionDigits(fractionDigitCount);
            df.setMinimumFractionDigits(fractionDigitCount);

            if (closest == 0) {
                closest = NumberUtils.toDouble("0." + (StringUtils.repeat("0", fractionDigitCount - 1) + "1"));
            }

            rounded = df.format(Math.round(num / closest) * closest);
        }

        context.setData(var, rounded);
        return StepResult.success("Variable '" + var + "' has been rounded down to " + rounded);
    }

    public StepResult increment(String var, String amount) {
        requiresValidVariableName(var);

        String current = context.getStringData(var, "0");
        NumberFormatHelper formatHelper = NumberFormatHelper.newInstance(current);
        requires(StringUtils.isNotBlank(formatHelper.getNormalFormat()),
                 "variable '" + var + "' does not represent a number",
                 current);

        String newAmt = formatHelper.addValue(amount).getOriginalFormat();
        context.setData(var, newAmt);

        return StepResult.success("incremented ${" + var + "} by " + amount + " to " + newAmt);
    }

}
